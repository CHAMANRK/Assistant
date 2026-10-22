package com.chaman.assistant;

import android.content.Context;
import android.content.Intent;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "Phone")
public class PhonePlugin extends Plugin {

    @PluginMethod
    public void torch(PluginCall call) {
        boolean on = call.getBoolean("on", true);
        try {
            CameraManager cm = (CameraManager) getContext().getSystemService(Context.CAMERA_SERVICE);
            for (String id : cm.getCameraIdList()) {
                CameraCharacteristics c = cm.getCameraCharacteristics(id);
                Boolean flash = c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                Integer facing = c.get(CameraCharacteristics.LENS_FACING);
                if (Boolean.TRUE.equals(flash) && facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    cm.setTorchMode(id, on);
                    call.resolve();
                    return;
                }
            }
            call.reject("Flash nahi mila");
        } catch (Exception e) {
            call.reject(e.getMessage() == null ? "Torch error" : e.getMessage());
        }
    }

    @PluginMethod
    public void volume(PluginCall call) {
        AudioManager am = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
        String a = call.getString("action", "set");
        int s = AudioManager.STREAM_MUSIC;
        switch (a) {
            case "up": am.adjustStreamVolume(s, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI); break;
            case "down": am.adjustStreamVolume(s, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI); break;
            case "mute": am.adjustStreamVolume(s, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI); break;
            case "unmute": am.adjustStreamVolume(s, AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI); break;
            case "set": {
                int lvl = Math.max(0, Math.min(100, call.getInt("level", 50)));
                int max = am.getStreamMaxVolume(s);
                am.setStreamVolume(s, Math.round(max * lvl / 100f), AudioManager.FLAG_SHOW_UI);
                break;
            }
            default: call.reject("Volume command samajh nahi aayi"); return;
        }
        call.resolve();
    }

    @PluginMethod
    public void brightness(PluginCall call) {
        Context c = getContext();
        if (!Settings.System.canWrite(c)) {
            Intent i = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + c.getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
            call.reject("Modify system settings ki permission do, phir dobara bolo");
            return;
        }
        int lvl = Math.max(0, Math.min(100, call.getInt("level", 50)));
        int val = Math.max(1, Math.round(255 * lvl / 100f));
        Settings.System.putInt(c.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
        Settings.System.putInt(c.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, val);
        call.resolve();
    }

    @PluginMethod
    public void panel(PluginCall call) {
        String t = call.getString("type", "wifi");
        String action;
        if (t.equals("bluetooth")) action = Settings.ACTION_BLUETOOTH_SETTINGS;
        else if (Build.VERSION.SDK_INT >= 29) action = t.equals("internet") ? Settings.Panel.ACTION_INTERNET_CONNECTIVITY : Settings.Panel.ACTION_WIFI;
        else action = t.equals("internet") ? Settings.ACTION_WIRELESS_SETTINGS : Settings.ACTION_WIFI_SETTINGS;
        try {
            Intent i = new Intent(action);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(i);
            call.resolve();
        } catch (Exception e) {
            call.reject("Panel nahi khula");
        }
    }
}
