package com.benjamin.nearly; // CHANGE to your app's package

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * All on-device state. Same SharedPreferences file as the original widget ("nearly_widget"),
 * so existing pairing data (url / code / slot) keeps working.
 *
 *  - pairing:   url, code, slot, pslot, myName, pName
 *  - cache:     last JSON snapshot of the couple node (offline data)
 *  - queue:     writes that failed while offline, replayed on next sync
 *  - priv.*:    sharing / privacy switches
 *  - w<id>.*:   per-widget-instance settings
 */
final class NearlyStore {
    static final String FILE = "nearly_widget";
    private static final String[] RESERVED = {"meta", "events", "notes", "tasks", "candle", "game", "photoIndex", "notify", "seen", "typing", "call"};

    static SharedPreferences sp(Context c) { return c.getSharedPreferences(FILE, Context.MODE_PRIVATE); }

    // ---------------- pairing ----------------
    static String url(Context c) { return sp(c).getString("url", "").replaceAll("/+$", ""); }
    static String code(Context c) { return sp(c).getString("code", ""); }
    static String slot(Context c) { return sp(c).getString("slot", ""); }

    static boolean paired(Context c) {
        return !url(c).isEmpty() && !code(c).isEmpty() && !slot(c).isEmpty();
    }

    /** Partner's slot: explicit if the app sent one, else inferred. */
    static String pslot(Context c) {
        String ps = sp(c).getString("pslot", "");
        if (!ps.isEmpty()) return ps;
        String s = slot(c);
        switch (s) {
            case "a": return "b";
            case "b": return "a";
            case "A": return "B";
            case "B": return "A";
            case "1": return "2";
            case "2": return "1";
            default: break;
        }
        JSONObject root = cache(c);
        Iterator<String> it = root.keys();
        while (it.hasNext()) {
            String k = it.next();
            if (k.equals(s) || isReserved(k)) continue;
            if (root.optJSONObject(k) != null) return k;
        }
        return "partner";
    }

    static boolean isReserved(String k) {
        for (String r : RESERVED) if (r.equals(k)) return true;
        return false;
    }

    // ---------------- cache ----------------
    static synchronized JSONObject cache(Context c) {
        try { return new JSONObject(sp(c).getString("cache", "{}")); }
        catch (Exception e) { return new JSONObject(); }
    }

    /** Replace the whole snapshot (a successful sync). */
    static synchronized void setCache(Context c, JSONObject o) {
        sp(c).edit().putString("cache", o.toString()).putLong("cacheTs", NearlyUtil.now()).apply();
    }

    static long cacheTs(Context c) { return sp(c).getLong("cacheTs", 0); }

    /** Optimistic local edit of one path ("a/hearts"). value == null removes it. */
    static synchronized void cacheSet(Context c, String path, Object value) {
        JSONObject root = cache(c);
        String[] parts = path.split("/");
        JSONObject cur = root;
        try {
            for (int i = 0; i < parts.length - 1; i++) {
                JSONObject next = cur.optJSONObject(parts[i]);
                if (next == null) { next = new JSONObject(); cur.put(parts[i], next); }
                cur = next;
            }
            String last = parts[parts.length - 1];
            if (value == null) cur.remove(last); else cur.put(last, value);
        } catch (Exception e) {
            return;
        }
        sp(c).edit().putString("cache", root.toString()).apply();
    }

    // ---------------- offline write queue ----------------
    static synchronized JSONArray queue(Context c) {
        try { return new JSONArray(sp(c).getString("queue", "[]")); }
        catch (Exception e) { return new JSONArray(); }
    }

    static synchronized void setQueue(Context c, JSONArray q) {
        sp(c).edit().putString("queue", q.toString()).apply();
    }

    static synchronized void enqueue(Context c, String method, String path, String jsonBody) {
        JSONArray old = queue(c), q = new JSONArray();
        // last write to a path wins
        for (int i = 0; i < old.length(); i++) {
            JSONObject e = old.optJSONObject(i);
            if (e != null && !path.equals(e.optString("p"))) q.put(e);
        }
        JSONObject n = new JSONObject();
        NearlyUtil.put(n, "m", method);
        NearlyUtil.put(n, "p", path);
        NearlyUtil.put(n, "v", jsonBody == null ? "" : jsonBody);
        q.put(n);
        if (q.length() > 120) {
            JSONArray cut = new JSONArray();
            for (int i = q.length() - 120; i < q.length(); i++) cut.put(q.opt(i));
            q = cut;
        }
        setQueue(c, q);
    }

    // ---------------- privacy / sharing switches ----------------
    static boolean priv(Context c, String k, boolean def) { return sp(c).getBoolean("priv." + k, def); }
    static void setPriv(Context c, String k, boolean v) { sp(c).edit().putBoolean("priv." + k, v).apply(); }
    static String privStr(Context c, String k, String def) { return sp(c).getString("priv." + k, def); }
    static void setPrivStr(Context c, String k, String v) { sp(c).edit().putString("priv." + k, v).apply(); }

    // ---------------- per-widget settings ----------------
    static String w(Context c, int id, String k, String def) { return sp(c).getString("w" + id + "." + k, def); }

    static int wi(Context c, int id, String k, int def) {
        try { return Integer.parseInt(sp(c).getString("w" + id + "." + k, "")); }
        catch (Exception e) { return def; }
    }

    static boolean wb(Context c, int id, String k, boolean def) {
        String v = sp(c).getString("w" + id + "." + k, "");
        if (v.isEmpty()) return def;
        return v.equals("1") || v.equals("true");
    }

    static void setW(Context c, int id, String k, String v) {
        if (v == null) sp(c).edit().remove("w" + id + "." + k).apply();
        else sp(c).edit().putString("w" + id + "." + k, v).apply();
    }

    static void clearW(Context c, int id) {
        SharedPreferences p = sp(c);
        SharedPreferences.Editor e = p.edit();
        String prefix = "w" + id + ".";
        for (String key : new ArrayList<>(p.getAll().keySet())) if (key.startsWith(prefix)) e.remove(key);
        e.apply();
        File f = bgFile(c, id);
        if (f.exists()) f.delete();
    }

    /** Clears settings but keeps the instance alive (Reset button). */
    static void resetW(Context c, int id) { clearW(c, id); }

    static File bgFile(Context c, int id) {
        File d = new File(c.getFilesDir(), "nearly_bg");
        d.mkdirs();
        return new File(d, id + ".jpg");
    }

    // ---------------- last action feedback ----------------
    static void setFeedback(Context c, String text) {
        sp(c).edit().putString("fb", text).putLong("fbTs", NearlyUtil.now()).apply();
    }
}
