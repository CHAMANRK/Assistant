package com.chaman.assistant;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Native AI call with key rotation. Config is synced from the web UI. */
final class Ai {
    private Ai() {}

    private static final String[][] PROVIDERS = {
        {"groq", "https://api.groq.com/openai/v1/chat/completions", "llama-3.3-70b-versatile"},
        {"openrouter", "https://openrouter.ai/api/v1/chat/completions", "meta-llama/llama-3.3-70b-instruct:free"},
        {"nvidia", "https://integrate.api.nvidia.com/v1/chat/completions", "meta/llama-3.3-70b-instruct"},
        {"cerebras", "https://api.cerebras.ai/v1/chat/completions", "llama-3.3-70b"}
    };

    static String ask(Context c, String userText) throws Exception {
        SharedPreferences sp = c.getSharedPreferences("assistant", Context.MODE_PRIVATE);
        JSONObject cfg = new JSONObject(sp.getString("cfg", "{}"));
        JSONObject idx = new JSONObject(sp.getString("idx", "{}"));
        String system = sp.getString("system", "Tu ek phone assistant hai. Jawab sabse chhota rakho.");
        JSONArray msgs = new JSONArray();
        msgs.put(new JSONObject().put("role", "system").put("content", system));
        msgs.put(new JSONObject().put("role", "user").put("content", userText));

        for (String[] p : PROVIDERS) {
            JSONObject pc = cfg.optJSONObject(p[0]);
            if (pc == null) continue;
            List<String> keys = new ArrayList<>();
            for (String k : pc.optString("keys", "").split(",")) {
                k = k.trim();
                if (!k.isEmpty()) keys.add(k);
            }
            if (keys.isEmpty()) continue;
            String model = pc.optString("model", "");
            if (model.isEmpty()) model = p[2];
            int start = idx.optInt(p[0], 0) % keys.size();
            for (int i = 0; i < keys.size(); i++) {
                int n = (start + i) % keys.size();
                try {
                    String out = post(p[1], keys.get(n), model, msgs);
                    idx.put(p[0], n);
                    sp.edit().putString("idx", idx.toString()).apply();
                    return out;
                } catch (Exception ignored) {
                    // next key / next provider
                }
            }
        }
        throw new Exception("Sab keys fail. App ki settings me keys check karo.");
    }

    private static String post(String url, String key, String model, JSONArray msgs) throws Exception {
        HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
        try {
            h.setRequestMethod("POST");
            h.setConnectTimeout(10000);
            h.setReadTimeout(25000);
            h.setDoOutput(true);
            h.setRequestProperty("Content-Type", "application/json");
            h.setRequestProperty("Authorization", "Bearer " + key);
            JSONObject body = new JSONObject()
                .put("model", model)
                .put("messages", msgs)
                .put("max_tokens", 200)
                .put("temperature", 0.2);
            try (OutputStream os = h.getOutputStream()) {
                os.write(body.toString().getBytes("UTF-8"));
            }
            if (h.getResponseCode() != 200) throw new Exception("HTTP " + h.getResponseCode());
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(h.getInputStream(), "UTF-8"))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
            }
            JSONObject j = new JSONObject(sb.toString());
            return j.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim();
        } finally {
            h.disconnect();
        }
    }
}
