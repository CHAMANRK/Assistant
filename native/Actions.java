package com.chaman.assistant;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.telephony.SmsManager;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Native actions used by the floating overlay. */
final class Actions {
    private Actions() {}

    static boolean has(Context c, String p) {
        return c.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED;
    }

    static void need(Context c, String p, String what) throws Exception {
        if (!has(c, p)) throw new Exception(what + " permission nahi hai. Assistant app kholo aur allow karo.");
    }

    static String last10(String s) {
        String d = s == null ? "" : s.replaceAll("[^0-9]", "");
        return d.length() > 10 ? d.substring(d.length() - 10) : d;
    }

    // ---- open app ----
    static String open(Context c, String name) throws Exception {
        String q = name.toLowerCase(Locale.ROOT).trim();
        PackageManager pm = c.getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN, null);
        main.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = pm.queryIntentActivities(main, 0);
        ResolveInfo best = null;
        int bestScore = 0;
        for (ResolveInfo ri : apps) {
            String label = ri.loadLabel(pm).toString().toLowerCase(Locale.ROOT);
            String pkg = ri.activityInfo.packageName.toLowerCase(Locale.ROOT);
            int score = 0;
            if (label.equals(q)) score = 100;
            else if (label.startsWith(q)) score = 80;
            else if (label.contains(q)) score = 60;
            else if (label.length() > 2 && q.contains(label)) score = 50;
            else if (pkg.contains(q.replace(" ", ""))) score = 40;
            if (score > bestScore) { bestScore = score; best = ri; }
        }
        if (best == null) throw new Exception("App nahi mili: " + name);
        Intent launch = pm.getLaunchIntentForPackage(best.activityInfo.packageName);
        if (launch == null) throw new Exception("App nahi mili: " + name);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        c.startActivity(launch);
        return "ho gaya";
    }

    // ---- phone controls ----
    static String phone(Context c, String arg) throws Exception {
        String[] parts = arg.trim().toLowerCase(Locale.ROOT).split("\\s+", 2);
        String a = parts[0];
        String v = parts.length > 1 ? parts[1].trim() : "";
        switch (a) {
            case "torch": torch(c, !v.equals("off")); return "ho gaya";
            case "volume": volume(c, v); return "ho gaya";
            case "brightness": brightness(c, v); return "ho gaya";
            case "wifi":
            case "internet":
            case "bluetooth": panel(c, a); return "panel khol diya, toggle kar lo";
            default: throw new Exception("Ye command samajh nahi aayi");
        }
    }

    private static void torch(Context c, boolean on) throws Exception {
        CameraManager cm = (CameraManager) c.getSystemService(Context.CAMERA_SERVICE);
        for (String id : cm.getCameraIdList()) {
            CameraCharacteristics ch = cm.getCameraCharacteristics(id);
            Boolean flash = ch.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
            Integer facing = ch.get(CameraCharacteristics.LENS_FACING);
            if (Boolean.TRUE.equals(flash) && facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                cm.setTorchMode(id, on);
                return;
            }
        }
        throw new Exception("Flash nahi mila");
    }

    private static void volume(Context c, String v) throws Exception {
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        int s = AudioManager.STREAM_MUSIC;
        if (v.matches("\\d+")) {
            int lvl = Math.max(0, Math.min(100, Integer.parseInt(v)));
            int max = am.getStreamMaxVolume(s);
            am.setStreamVolume(s, Math.round(max * lvl / 100f), AudioManager.FLAG_SHOW_UI);
            return;
        }
        switch (v) {
            case "up": am.adjustStreamVolume(s, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI); break;
            case "down": am.adjustStreamVolume(s, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI); break;
            case "mute": am.adjustStreamVolume(s, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI); break;
            case "unmute": am.adjustStreamVolume(s, AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI); break;
            default: throw new Exception("Volume command samajh nahi aayi");
        }
    }

    private static void brightness(Context c, String v) throws Exception {
        if (!v.matches("\\d+")) throw new Exception("Brightness 0-100 me batao");
        if (!Settings.System.canWrite(c)) {
            Intent i = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + c.getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
            throw new Exception("Modify system settings ki permission do, phir dobara bolo");
        }
        int lvl = Math.max(0, Math.min(100, Integer.parseInt(v)));
        int val = Math.max(1, Math.round(255 * lvl / 100f));
        Settings.System.putInt(c.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
        Settings.System.putInt(c.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, val);
    }

    private static void panel(Context c, String t) throws Exception {
        String action;
        if (t.equals("bluetooth")) action = Settings.ACTION_BLUETOOTH_SETTINGS;
        else if (Build.VERSION.SDK_INT >= 29) action = t.equals("internet") ? Settings.Panel.ACTION_INTERNET_CONNECTIVITY : Settings.Panel.ACTION_WIFI;
        else action = t.equals("internet") ? Settings.ACTION_WIRELESS_SETTINGS : Settings.ACTION_WIFI_SETTINGS;
        Intent i = new Intent(action);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        c.startActivity(i);
    }

    // ---- contacts ----
    static List<String[]> contacts(Context c, String q) throws Exception {
        need(c, Manifest.permission.READ_CONTACTS, "Contacts");
        List<String[]> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Cursor cur = c.getContentResolver().query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER},
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?",
            new String[]{"%" + q.trim() + "%"},
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC");
        if (cur != null) {
            try {
                while (cur.moveToNext() && out.size() < 5) {
                    String name = cur.getString(0);
                    String num = cur.getString(1);
                    if (num == null || !seen.add(last10(num))) continue;
                    out.add(new String[]{name, num});
                }
            } finally {
                cur.close();
            }
        }
        return out;
    }

    // ---- sms ----
    static void sendSms(Context c, String number, String text) throws Exception {
        need(c, Manifest.permission.SEND_SMS, "SMS bhejne ki");
        SmsManager sm = c.getSystemService(SmsManager.class);
        ArrayList<String> parts = sm.divideMessage(text);
        sm.sendMultipartTextMessage(number, null, parts, null, null);
    }

    private static String nameFor(Context c, String addr) {
        if (addr == null || !has(c, Manifest.permission.READ_CONTACTS)) return null;
        try {
            Uri u = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(addr));
            Cursor cur = c.getContentResolver().query(u, new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME}, null, null, null);
            if (cur != null) {
                try {
                    if (cur.moveToFirst()) return cur.getString(0);
                } finally {
                    cur.close();
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    static String readSms(Context c, List<String> numbers, int limit) throws Exception {
        need(c, Manifest.permission.READ_SMS, "SMS padhne ki");
        Set<String> want = new HashSet<>();
        for (String n : numbers) want.add(last10(n));
        StringBuilder sb = new StringBuilder();
        int count = 0;
        Cursor cur = c.getContentResolver().query(Uri.parse("content://sms"),
            new String[]{"address", "body", "type"}, null, null, "date DESC");
        if (cur != null) {
            try {
                while (cur.moveToNext() && count < limit) {
                    String addr = cur.getString(0);
                    if (!want.isEmpty() && (addr == null || !want.contains(last10(addr)))) continue;
                    String who = nameFor(c, addr);
                    if (who == null) who = addr;
                    if (count > 0) sb.append("\n\n");
                    if (cur.getInt(2) == 2) sb.append("Tumne ").append(who).append(" ko: ");
                    else sb.append(who).append(": ");
                    sb.append(cur.getString(1));
                    count++;
                }
            } finally {
                cur.close();
            }
        }
        return count == 0 ? "Koi msg nahi mila" : sb.toString();
    }

    // ---- call ----
    static void call(Context c, String number) throws Exception {
        need(c, Manifest.permission.CALL_PHONE, "Call karne ki");
        Intent i = new Intent(Intent.ACTION_CALL, Uri.fromParts("tel", number, null));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        c.startActivity(i);
    }
}
