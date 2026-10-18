package io.github.chrisoo.expy;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Set;

/** Detected payments waiting for the user, kept on the phone only (SharedPreferences, at most 50). */
final class PayStore {
    private static final String FILE = "expy_pay", KEY = "pending", ON = "on";
    private static final long DUPLICATE_MS = 3 * 60 * 1000; // same app, same amount within 3 min = same payment

    private PayStore() {}

    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences(FILE, Context.MODE_PRIVATE); }

    static boolean isOn(Context c) { return prefs(c).getBoolean(ON, true); }

    static void setOn(Context c, boolean on) { prefs(c).edit().putBoolean(ON, on).apply(); }

    static synchronized JSONArray list(Context c) {
        try { return new JSONArray(prefs(c).getString(KEY, "[]")); } catch (JSONException e) { return new JSONArray(); }
    }

    /** Adds a payment; returns its id, or null when it duplicates a recent one. */
    static synchronized String add(Context c, String pkg, String app, String title, String text, PayParser.Result r, long when) {
        JSONArray cur = list(c), next = new JSONArray();
        try {
            for (int i = 0; i < cur.length(); i++) {
                JSONObject o = cur.getJSONObject(i);
                if (pkg.equals(o.optString("pkg")) && o.optLong("cents") == r.cents && Math.abs(o.optLong("ts") - when) < DUPLICATE_MS) return null;
            }
            int from = Math.max(0, cur.length() - 49);
            for (int i = from; i < cur.length(); i++) next.put(cur.get(i));
            String id = "w" + when + "-" + r.cents;
            JSONObject o = new JSONObject();
            o.put("id", id); o.put("pkg", pkg); o.put("app", app); o.put("title", title); o.put("text", text);
            o.put("cents", r.cents); o.put("merchant", r.merchant == null ? JSONObject.NULL : r.merchant); o.put("ts", when);
            next.put(o);
            prefs(c).edit().putString(KEY, next.toString()).apply();
            return id;
        } catch (JSONException e) { return null; }
    }

    static synchronized void remove(Context c, Set<String> ids) {
        JSONArray cur = list(c), next = new JSONArray();
        for (int i = 0; i < cur.length(); i++) {
            JSONObject o = cur.optJSONObject(i);
            if (o != null && !ids.contains(o.optString("id"))) next.put(o);
        }
        prefs(c).edit().putString(KEY, next.toString()).apply();
    }
}
