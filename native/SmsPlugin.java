package com.chaman.assistant;

import android.Manifest;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.telephony.SmsManager;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

@CapacitorPlugin(
    name = "Sms",
    permissions = {
        @Permission(strings = {Manifest.permission.READ_SMS}, alias = "readSms"),
        @Permission(strings = {Manifest.permission.SEND_SMS}, alias = "sendSms"),
        @Permission(strings = {Manifest.permission.READ_CONTACTS}, alias = "contacts")
    }
)
public class SmsPlugin extends Plugin {

    private static String last10(String s) {
        String d = s == null ? "" : s.replaceAll("[^0-9]", "");
        return d.length() > 10 ? d.substring(d.length() - 10) : d;
    }

    // ---- contacts ----
    @PluginMethod
    public void findContact(PluginCall call) {
        if (getPermissionState("contacts") != PermissionState.GRANTED) {
            requestPermissionForAlias("contacts", call, "contactsCb");
            return;
        }
        doFind(call);
    }

    @PermissionCallback
    private void contactsCb(PluginCall call) {
        if (getPermissionState("contacts") == PermissionState.GRANTED) doFind(call);
        else call.reject("Contacts permission nahi mili");
    }

    private void doFind(PluginCall call) {
        String q = call.getString("name", "").trim();
        JSArray arr = new JSArray();
        Set<String> seen = new HashSet<>();
        Cursor c = getContext().getContentResolver().query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER},
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?",
            new String[]{"%" + q + "%"},
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC");
        if (c != null) {
            try {
                while (c.moveToNext() && arr.length() < 5) {
                    String name = c.getString(0);
                    String num = c.getString(1);
                    if (num == null || !seen.add(last10(num))) continue;
                    JSObject o = new JSObject();
                    o.put("name", name);
                    o.put("number", num);
                    arr.put(o);
                }
            } finally {
                c.close();
            }
        }
        JSObject r = new JSObject();
        r.put("matches", arr);
        call.resolve(r);
    }

    // ---- send ----
    @PluginMethod
    public void send(PluginCall call) {
        if (getPermissionState("sendSms") != PermissionState.GRANTED) {
            requestPermissionForAlias("sendSms", call, "sendCb");
            return;
        }
        doSend(call);
    }

    @PermissionCallback
    private void sendCb(PluginCall call) {
        if (getPermissionState("sendSms") == PermissionState.GRANTED) doSend(call);
        else call.reject("SMS bhejne ki permission nahi mili");
    }

    private void doSend(PluginCall call) {
        String number = call.getString("number");
        String text = call.getString("text");
        if (number == null || text == null || text.isEmpty()) {
            call.reject("Number ya text khali hai");
            return;
        }
        try {
            SmsManager sm = getContext().getSystemService(SmsManager.class);
            ArrayList<String> parts = sm.divideMessage(text);
            sm.sendMultipartTextMessage(number, null, parts, null, null);
            call.resolve();
        } catch (Exception e) {
            call.reject(e.getMessage() == null ? "SMS fail" : e.getMessage());
        }
    }

    // ---- read ----
    @PluginMethod
    public void read(PluginCall call) {
        if (getPermissionState("readSms") != PermissionState.GRANTED) {
            requestPermissionForAliases(new String[]{"readSms", "contacts"}, call, "readCb");
            return;
        }
        doRead(call);
    }

    @PermissionCallback
    private void readCb(PluginCall call) {
        if (getPermissionState("readSms") == PermissionState.GRANTED) doRead(call);
        else call.reject("SMS padhne ki permission nahi mili");
    }

    private String nameFor(String addr) {
        if (addr == null || getContext().checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null;
        try {
            Uri u = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(addr));
            Cursor c = getContext().getContentResolver().query(u, new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME}, null, null, null);
            if (c != null) {
                try {
                    if (c.moveToFirst()) return c.getString(0);
                } finally {
                    c.close();
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void doRead(PluginCall call) {
        int limit = call.getInt("limit", 5);
        JSArray nums = call.getArray("numbers", new JSArray());
        Set<String> want = new HashSet<>();
        for (int i = 0; i < nums.length(); i++) {
            try { want.add(last10(nums.getString(i))); } catch (Exception ignored) {}
        }
        JSArray out = new JSArray();
        Cursor c = getContext().getContentResolver().query(
            Uri.parse("content://sms"),
            new String[]{"address", "body", "date", "type"},
            null, null, "date DESC");
        if (c != null) {
            try {
                while (c.moveToNext() && out.length() < limit) {
                    String addr = c.getString(0);
                    if (!want.isEmpty() && (addr == null || !want.contains(last10(addr)))) continue;
                    JSObject o = new JSObject();
                    o.put("address", addr);
                    o.put("name", nameFor(addr));
                    o.put("body", c.getString(1));
                    o.put("date", c.getLong(2));
                    o.put("type", c.getInt(3));
                    out.put(o);
                }
            } finally {
                c.close();
            }
        }
        JSObject r = new JSObject();
        r.put("messages", out);
        call.resolve(r);
    }
}
