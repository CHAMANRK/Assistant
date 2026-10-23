package com.chaman.assistant;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OverlayService extends Service {
    private static final Object CANCEL = new Object();
    private static final String NUM = "^\\+?[\\d\\s\\-]{7,}$";

    private WindowManager wm;
    private Handler ui;
    private TextView icon;
    private LinearLayout panel;
    private ScrollView outScroll;
    private TextView out;
    private LinearLayout cardBox;
    private TextView cardText;
    private EditText cardInput;
    private LinearLayout cardRow;
    private EditText input;
    private WindowManager.LayoutParams iconLp;
    private WindowManager.LayoutParams panelLp;
    private boolean panelShown = false;
    private boolean busy = false;
    private final BlockingQueue<Object> answer = new LinkedBlockingQueue<>();

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startFg();
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return START_NOT_STICKY; }
        if (icon == null) {
            try { buildViews(); } catch (Exception e) { stopSelf(); }
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        try { if (panelShown) wm.removeView(panel); } catch (Exception ignored) {}
        try { if (icon != null) wm.removeView(icon); } catch (Exception ignored) {}
        icon = null;
        super.onDestroy();
    }

    private void startFg() {
        String ch = "assistant_overlay";
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(ch, "Assistant", NotificationManager.IMPORTANCE_MIN));
            b = new Notification.Builder(this, ch);
        } else {
            b = new Notification.Builder(this);
        }
        b.setContentTitle("Assistant chalu hai").setSmallIcon(android.R.drawable.ic_dialog_info).setOngoing(true);
        Notification n = b.build();
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, 0x40000000);
        else startForeground(1, n);
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private int overlayType() {
        return Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
    }

    private GradientDrawable round(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private Button mkBtn(String label, final Runnable r, boolean primary) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(primary ? 0xFF1A1405 : 0xFFE9F1F5);
        b.setBackground(round(primary ? 0xFFF0C24B : 0xFF2D4455, 10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(6);
        b.setLayoutParams(lp);
        b.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { r.run(); }
        });
        return b;
    }

    private void buildViews() {
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        ui = new Handler(Looper.getMainLooper());

        // ---- floating icon ----
        icon = new TextView(this);
        icon.setText("AI");
        icon.setTextColor(0xFF1A1405);
        icon.setTypeface(Typeface.DEFAULT_BOLD);
        icon.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(0xFFF0C24B);
        icon.setBackground(g);
        iconLp = new WindowManager.LayoutParams(dp(52), dp(52), overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT);
        iconLp.gravity = Gravity.TOP | Gravity.START;
        iconLp.x = dp(8);
        iconLp.y = dp(220);
        icon.setOnTouchListener(new View.OnTouchListener() {
            int sx, sy;
            float tx, ty;
            boolean moved;
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                int a = e.getAction();
                if (a == MotionEvent.ACTION_DOWN) {
                    sx = iconLp.x; sy = iconLp.y; tx = e.getRawX(); ty = e.getRawY(); moved = false;
                    return true;
                }
                if (a == MotionEvent.ACTION_MOVE) {
                    float dx = e.getRawX() - tx, dy = e.getRawY() - ty;
                    if (Math.abs(dx) > dp(6) || Math.abs(dy) > dp(6)) moved = true;
                    if (moved) {
                        iconLp.x = sx + (int) dx;
                        iconLp.y = sy + (int) dy;
                        try { wm.updateViewLayout(icon, iconLp); } catch (Exception ignored) {}
                    }
                    return true;
                }
                if (a == MotionEvent.ACTION_UP) {
                    if (!moved) showPanel();
                    return true;
                }
                return false;
            }
        });
        wm.addView(icon, iconLp);

        // ---- panel ----
        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable pg = round(0xFF162531, 16);
        pg.setStroke(dp(1), 0xFF2D4455);
        panel.setBackground(pg);
        panel.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_OUTSIDE) hidePanel();
                return false;
            }
        });

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText("Assistant");
        title.setTextColor(0xFFF0C24B);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView close = new TextView(this);
        close.setText("\u2715");
        close.setTextColor(0xFF8AA0AE);
        close.setTextSize(18);
        close.setPadding(dp(12), dp(4), dp(4), dp(4));
        close.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { hidePanel(); }
        });
        head.addView(title);
        head.addView(close);
        panel.addView(head);

        out = new TextView(this);
        out.setTextColor(0xFFE9F1F5);
        out.setTextSize(15);
        out.setPadding(0, dp(8), 0, dp(8));
        outScroll = new ScrollView(this);
        outScroll.addView(out);
        outScroll.setVisibility(View.GONE);
        panel.addView(outScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(150)));

        cardBox = new LinearLayout(this);
        cardBox.setOrientation(LinearLayout.VERTICAL);
        cardBox.setPadding(0, dp(6), 0, dp(6));
        cardBox.setVisibility(View.GONE);
        cardText = new TextView(this);
        cardText.setTextColor(0xFFE9F1F5);
        cardText.setTextSize(15);
        cardInput = new EditText(this);
        cardInput.setInputType(InputType.TYPE_CLASS_PHONE);
        cardInput.setTextColor(0xFFE9F1F5);
        cardInput.setHintTextColor(0xFF8AA0AE);
        cardInput.setHint("Number");
        cardInput.setBackground(round(0xFF0F1A22, 8));
        cardInput.setPadding(dp(10), dp(8), dp(10), dp(8));
        cardInput.setVisibility(View.GONE);
        cardRow = new LinearLayout(this);
        cardRow.setOrientation(LinearLayout.VERTICAL);
        cardBox.addView(cardText);
        cardBox.addView(cardInput);
        cardBox.addView(cardRow);
        panel.addView(cardBox);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, 0);
        input = new EditText(this);
        input.setHint("Command likho");
        input.setHintTextColor(0xFF8AA0AE);
        input.setTextColor(0xFFE9F1F5);
        input.setSingleLine(true);
        input.setImeOptions(EditorInfo.IME_ACTION_SEND);
        input.setBackground(round(0xFF0F1A22, 10));
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        input.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        input.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, android.view.KeyEvent ev) {
                submit();
                return true;
            }
        });
        Button send = new Button(this);
        send.setText("Bhejo");
        send.setAllCaps(false);
        send.setTextColor(0xFF1A1405);
        send.setBackground(round(0xFFF0C24B, 10));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.leftMargin = dp(8);
        send.setLayoutParams(slp);
        send.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { submit(); }
        });
        row.addView(input);
        row.addView(send);
        panel.addView(row);

        int w = getResources().getDisplayMetrics().widthPixels - dp(24);
        panelLp = new WindowManager.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT, overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT);
        panelLp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        panelLp.y = dp(48);
        panelLp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN;
    }

    private void showPanel() {
        if (panelShown) return;
        try { wm.addView(panel, panelLp); } catch (Exception e) { return; }
        panelShown = true;
        input.requestFocus();
        ui.postDelayed(new Runnable() {
            @Override public void run() {
                InputMethodManager im = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (im != null) im.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
            }
        }, 200);
    }

    private void hidePanel() {
        if (!panelShown) return;
        try {
            InputMethodManager im = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (im != null) im.hideSoftInputFromWindow(input.getWindowToken(), 0);
            wm.removeView(panel);
        } catch (Exception ignored) {}
        panelShown = false;
        cardBox.setVisibility(View.GONE);
        answer.offer(CANCEL);
    }

    private void setOut(String s) {
        out.setText(s);
        outScroll.setVisibility(View.VISIBLE);
        outScroll.post(new Runnable() {
            @Override public void run() { outScroll.fullScroll(View.FOCUS_UP); }
        });
    }

    private void submit() {
        final String t = input.getText().toString().trim();
        if (t.isEmpty() || busy) return;
        input.setText("");
        busy = true;
        setOut("> " + t + "\n...");
        new Thread(new Runnable() {
            @Override public void run() {
                String res;
                try { res = process(t); }
                catch (Exception e) { res = e.getMessage() == null ? "Error" : e.getMessage(); }
                final String r = res;
                ui.post(new Runnable() {
                    @Override public void run() { setOut(r); busy = false; }
                });
            }
        }).start();
    }

    // ---- command handling (background thread) ----
    private String process(String t) throws Exception {
        String reply = Ai.ask(this, t);
        Matcher m = Pattern.compile("(?im)^\\s*(OPEN|DO|SMS|READ|CALL):\\s*(.+)$").matcher(reply);
        if (!m.find()) return reply;
        String kind = m.group(1).toUpperCase(Locale.ROOT);
        String arg = m.group(2).trim();
        switch (kind) {
            case "OPEN": return Actions.open(this, arg);
            case "DO": return Actions.phone(this, arg);
            case "SMS": return doSms(arg);
            case "READ": return doRead(arg);
            case "CALL": return doCall(arg);
            default: return "Command samajh nahi aayi";
        }
    }

    private String[] resolve(String target) throws Exception {
        if (target.matches(NUM)) {
            String n = target.replaceAll("[\\s\\-]", "");
            return new String[]{n, n};
        }
        List<String[]> m = Actions.contacts(this, target);
        if (m.size() == 1) return m.get(0);
        if (m.size() > 1) {
            String[] labels = new String[m.size()];
            for (int k = 0; k < labels.length; k++) labels[k] = m.get(k)[0] + " " + m.get(k)[1];
            int p = choose("Kaunsa " + target + "?", labels, false);
            return p < 0 ? null : m.get(p);
        }
        String n = askText(target + " contacts me nahi mila. Number likho:");
        if (n == null) return null;
        n = n.trim();
        if (!n.matches(NUM)) throw new Exception("Number sahi nahi hai");
        n = n.replaceAll("[\\s\\-]", "");
        return new String[]{n, n};
    }

    private String doSms(String arg) throws Exception {
        int i = arg.indexOf('|');
        if (i < 0) return "SMS ka format galat";
        String target = arg.substring(0, i).trim();
        String text = arg.substring(i + 1).trim();
        if (target.isEmpty() || text.isEmpty()) return "Naam ya message khali hai";
        String[] who = resolve(target);
        if (who == null) return "Cancel kiya";
        int ok = choose(who[0] + " (" + who[1] + ")\n\"" + text + "\"", new String[]{"Bhejo"}, true);
        if (ok != 0) return "Cancel kiya";
        Actions.sendSms(this, who[1], text);
        return "ho gaya";
    }

    private String doCall(String arg) throws Exception {
        String target = arg.trim();
        if (target.isEmpty()) return "Kisko call karna hai?";
        String[] who = resolve(target);
        if (who == null) return "Cancel kiya";
        int ok = choose("Call karo: " + who[0] + " (" + who[1] + ")", new String[]{"Call"}, true);
        if (ok != 0) return "Cancel kiya";
        Actions.call(this, who[1]);
        return "ho gaya";
    }

    private String doRead(String arg) throws Exception {
        String t = arg.trim();
        List<String> numbers = new ArrayList<>();
        if (!t.isEmpty() && !t.matches("(?i)^(last|latest|all|inbox)$")) {
            if (t.matches(NUM)) {
                numbers.add(t);
            } else {
                for (String[] c : Actions.contacts(this, t)) numbers.add(c[1]);
                if (numbers.isEmpty()) return "Contact nahi mila: " + t;
            }
        }
        return Actions.readSms(this, numbers, 5);
    }

    // ---- in-overlay cards (blocking helpers, call from background thread) ----
    private int choose(final String text, final String[] labels, final boolean firstPrimary) {
        answer.clear();
        ui.post(new Runnable() {
            @Override public void run() {
                cardText.setText(text);
                cardInput.setVisibility(View.GONE);
                cardRow.removeAllViews();
                for (int i = 0; i < labels.length; i++) {
                    final int idx = i;
                    cardRow.addView(mkBtn(labels[i], new Runnable() {
                        @Override public void run() { cardBox.setVisibility(View.GONE); answer.offer(Integer.valueOf(idx)); }
                    }, firstPrimary && i == 0));
                }
                cardRow.addView(mkBtn("Cancel", new Runnable() {
                    @Override public void run() { cardBox.setVisibility(View.GONE); answer.offer(CANCEL); }
                }, false));
                cardBox.setVisibility(View.VISIBLE);
            }
        });
        try {
            Object o = answer.take();
            return o instanceof Integer ? ((Integer) o).intValue() : -1;
        } catch (InterruptedException e) {
            return -1;
        }
    }

    private String askText(final String text) {
        answer.clear();
        ui.post(new Runnable() {
            @Override public void run() {
                cardText.setText(text);
                cardInput.setText("");
                cardInput.setVisibility(View.VISIBLE);
                cardRow.removeAllViews();
                cardRow.addView(mkBtn("OK", new Runnable() {
                    @Override public void run() {
                        String v = cardInput.getText().toString();
                        cardBox.setVisibility(View.GONE);
                        answer.offer(v);
                    }
                }, true));
                cardRow.addView(mkBtn("Cancel", new Runnable() {
                    @Override public void run() { cardBox.setVisibility(View.GONE); answer.offer(CANCEL); }
                }, false));
                cardBox.setVisibility(View.VISIBLE);
                cardInput.requestFocus();
            }
        });
        try {
            Object o = answer.take();
            return o instanceof String ? (String) o : null;
        } catch (InterruptedException e) {
            return null;
        }
    }
}
