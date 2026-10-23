package com.chaman.assistant;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

@CapacitorPlugin(
    name = "Overlay",
    permissions = {
        @Permission(strings = {Manifest.permission.POST_NOTIFICATIONS}, alias = "notif"),
        @Permission(strings = {Manifest.permission.SEND_SMS}, alias = "sendSms"),
        @Permission(strings = {Manifest.permission.READ_SMS}, alias = "readSms"),
        @Permission(strings = {Manifest.permission.READ_CONTACTS}, alias = "contacts"),
        @Permission(strings = {Manifest.permission.CALL_PHONE}, alias = "call")
    }
)
public class OverlayPlugin extends Plugin {

    @PluginMethod
    public void setup(PluginCall call) {
        if (call.getBoolean("prompt", true)) {
            requestPermissionForAliases(new String[]{"notif", "sendSms", "readSms", "contacts", "call"}, call, "permsDone");
        } else {
            finish(call, false);
        }
    }

    @PermissionCallback
    private void permsDone(PluginCall call) {
        finish(call, true);
    }

    private void finish(PluginCall call, boolean openSettings) {
        Context c = getContext();
        JSObject r = new JSObject();
        if (Settings.canDrawOverlays(c)) {
            Intent si = new Intent(c, OverlayService.class);
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(si);
            else c.startService(si);
            r.put("overlay", true);
        } else {
            if (openSettings) {
                Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + c.getPackageName()));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(i);
            }
            r.put("overlay", false);
        }
        call.resolve(r);
    }

    @PluginMethod
    public void saveConfig(PluginCall call) {
        SharedPreferences.Editor e = getContext().getSharedPreferences("assistant", Context.MODE_PRIVATE).edit();
        String cfg = call.getString("cfg");
        String system = call.getString("system");
        if (cfg != null) e.putString("cfg", cfg);
        if (system != null) e.putString("system", system);
        e.apply();
        call.resolve();
    }
}
