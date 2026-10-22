package com.chaman.assistant;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.util.List;
import java.util.Locale;

@CapacitorPlugin(name = "AppOpener")
public class AppOpenerPlugin extends Plugin {
    @PluginMethod
    public void open(PluginCall call) {
        String q = call.getString("name", "").toLowerCase(Locale.ROOT).trim();
        if (q.isEmpty()) { call.reject("empty"); return; }
        PackageManager pm = getContext().getPackageManager();
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
        if (best == null) { call.reject("not found"); return; }
        Intent launch = pm.getLaunchIntentForPackage(best.activityInfo.packageName);
        if (launch == null) { call.reject("cannot launch"); return; }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(launch);
        JSObject r = new JSObject();
        r.put("label", best.loadLabel(pm).toString());
        call.resolve(r);
    }
}
