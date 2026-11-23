package com.benjamin.nearly; // CHANGE to your app's package

import android.Manifest;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.BatteryManager;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cloud sync, offline queue, presence/battery/location publishing and job scheduling.
 *
 * Remote layout (same convention as the original widget):
 *     <url>/c/<code>/<slot>/<field>.json      per-person data
 *     <url>/c/<code>/<shared>/...             events, notes, tasks, candle, game, photoIndex, meta
 *     <url>/p/<code>/<photoId>.json           photo bytes (base64 string)
 */
final class NearlySync {
    static final int JOB_ID = 7301;
    private static volatile long lastRun = 0;

    // ---------------- URLs ----------------
    static String base(Context c) { return NearlyStore.url(c) + "/c/" + NearlyStore.code(c); }

    /** "~p/<id>" addresses the photo store, everything else the couple node. */
    static String urlFor(Context c, String path) {
        if (path.startsWith("~p/")) return NearlyStore.url(c) + "/p/" + NearlyStore.code(c) + "/" + path.substring(3) + ".json";
        return base(c) + "/" + path + ".json";
    }

    static String encode(Object v) {
        if (v == null) return "null";
        if (v instanceof String) return JSONObject.quote((String) v);
        if (v instanceof JSONObject || v instanceof JSONArray) return v.toString();
        return String.valueOf(v);
    }

    // ---------------- writes ----------------
    /** Local-only optimistic update. */
    static void writeLocal(Context c, String path, Object value) {
        if (!path.startsWith("~p/")) NearlyStore.cacheSet(c, path, value);
    }

    /** Network PUT/DELETE; queued for later if it fails. */
    static void push(Context c, String path, Object value) {
        if (!NearlyStore.paired(c)) return;
        String m = value == null ? "DELETE" : "PUT";
        String body = value == null ? "" : encode(value);
        try {
            NearlyApi.send(m, urlFor(c, path), body);
        } catch (Exception e) {
            NearlyStore.enqueue(c, m, path, body);
        }
    }

    /** Local update, repaint widgets, then push every path (keeps widgets snappy). */
    static void writeAll(Context c, Map<String, Object> kv) {
        for (Map.Entry<String, Object> e : kv.entrySet()) writeLocal(c, e.getKey(), e.getValue());
        NearlyWidgets.refreshAll(c);
        for (Map.Entry<String, Object> e : kv.entrySet()) push(c, e.getKey(), e.getValue());
        NearlyWidgets.refreshAll(c);   // reflects "queued/offline" state if a push failed
    }

    static void write(Context c, String path, Object value) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        m.put(path, value);
        writeAll(c, m);
    }

    static void flushQueue(Context c) throws Exception {
        JSONArray q = NearlyStore.queue(c);
        if (q.length() == 0) return;
        JSONArray rest = new JSONArray();
        boolean failed = false;
        for (int i = 0; i < q.length(); i++) {
            JSONObject e = q.optJSONObject(i);
            if (e == null) continue;
            if (failed) { rest.put(e); continue; }
            try {
                NearlyApi.send(e.optString("m", "PUT"), urlFor(c, e.optString("p")), e.optString("v"));
            } catch (Exception ex) {
                failed = true;
                rest.put(e);
            }
        }
        NearlyStore.setQueue(c, rest);
        if (failed) throw new Exception("offline");
    }

    // ---------------- high-level actions ----------------
    static void setMood(Context c, String mood, String msg, String activity, String status) {
        long ts = NearlyUtil.now();
        String slot = NearlyStore.slot(c);
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        if (mood != null && !mood.isEmpty()) {
            m.put(slot + "/mood", mood);
            m.put(slot + "/moodTs", ts);
            m.put(slot + "/moodHist/" + NearlyUtil.todayKey(), mood);
        }
        if (msg != null) m.put(slot + "/moodMsg", msg);
        if (activity != null) m.put(slot + "/activity", activity);
        if (status != null) m.put(slot + "/status", status);
        if (!NearlyStore.priv(c, "shareStatus", true)) {   // privacy: keep it on this phone only
            for (Map.Entry<String, Object> e : m.entrySet()) writeLocal(c, e.getKey(), e.getValue());
            NearlyWidgets.refreshAll(c);
            return;
        }
        writeAll(c, m);
    }

    static void notifyPartner(Context c, String title, String body) {
        JSONObject n = new JSONObject();
        NearlyUtil.put(n, "title", title);
        NearlyUtil.put(n, "body", body == null ? "" : body);
        NearlyUtil.put(n, "ts", NearlyUtil.now());
        NearlyUtil.put(n, "from", NearlyStore.slot(c));
        write(c, "notify/" + NearlyStore.pslot(c), n);
    }

    // ---------------- sync ----------------
    static synchronized boolean run(Context c) {
        lastRun = NearlyUtil.now();
        if (!NearlyStore.paired(c)) {
            NearlyWidgets.refreshAll(c);
            return false;
        }
        boolean ok = false;
        try {
            flushQueue(c);
            pushDevice(c);
            JSONObject now = parseObj(NearlyApi.get(base(c) + ".json"));
            JSONObject prev = NearlyStore.cache(c);
            try { NearlyNotifier.check(c, prev, now); } catch (Throwable ignored) { }
            NearlyStore.setCache(c, now);
            NearlyStore.sp(c).edit().putLong("syncOk", NearlyUtil.now()).apply();
            ok = true;
            try { NearlyPhotos.sync(c, now); } catch (Throwable ignored) { }
        } catch (Exception e) {
            NearlyStore.sp(c).edit().putLong("syncFail", NearlyUtil.now()).apply();
        }
        NearlyWidgets.refreshAll(c);
        return ok;
    }

    static JSONObject parseObj(String body) {
        try {
            Object v = new JSONTokener(body).nextValue();
            if (v instanceof JSONObject) return (JSONObject) v;
        } catch (Exception ignored) { }
        return new JSONObject();
    }

    /** Fire-and-forget sync on a background thread. */
    static void syncAsync(final Context ctx) {
        final Context c = ctx.getApplicationContext();
        new Thread(new Runnable() { public void run() { NearlySync.run(c); } }).start();
    }

    /** Sync only if the last attempt was longer ago than maxAgeMs. */
    static void syncIfStale(Context ctx, long maxAgeMs) {
        if (NearlyUtil.now() - lastRun > maxAgeMs) syncAsync(ctx);
    }

    // ---------------- presence / device state ----------------
    static void presenceAsync(final Context ctx, final boolean online) {
        final Context c = ctx.getApplicationContext();
        new Thread(new Runnable() { public void run() { presence(c, online); } }).start();
    }

    static void presence(Context c, boolean online) {
        if (!NearlyStore.paired(c)) return;
        String s = NearlyStore.slot(c);
        long ts = NearlyUtil.now();
        boolean share = NearlyStore.priv(c, "sharePresence", true);
        try {
            NearlyApi.send("PUT", urlFor(c, s + "/online"), share && online ? String.valueOf(ts) : "0");
            if (share) NearlyApi.send("PUT", urlFor(c, s + "/active"), String.valueOf(ts));
        } catch (Exception ignored) { }
    }

    /** Battery + location (respecting the privacy switches). Failures are ignored; data is perishable. */
    static void pushDevice(Context c) {
        String s = NearlyStore.slot(c);
        try {
            if (NearlyStore.priv(c, "shareBattery", false)) {
                Intent b = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                if (b != null) {
                    int lvl = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1), scale = b.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                    int st = b.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                    if (lvl >= 0 && scale > 0) {
                        NearlyApi.send("PUT", urlFor(c, s + "/batt"), String.valueOf(Math.round(100f * lvl / scale)));
                        boolean ch = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL;
                        NearlyApi.send("PUT", urlFor(c, s + "/charging"), String.valueOf(ch));
                    }
                    NearlyStore.sp(c).edit().putBoolean("battWasOn", true).apply();
                }
            } else if (NearlyStore.sp(c).getBoolean("battWasOn", false)) {
                NearlyApi.send("DELETE", urlFor(c, s + "/batt"), "");
                NearlyApi.send("DELETE", urlFor(c, s + "/charging"), "");
                NearlyStore.sp(c).edit().putBoolean("battWasOn", false).apply();
            }
        } catch (Exception ignored) { }

        try {
            boolean share = NearlyStore.priv(c, "shareLocation", false);
            NearlyApi.send("PUT", urlFor(c, s + "/locShare"), String.valueOf(share));
            if (share) {
                Location l = lastKnown(c);
                if (l != null) storeMyLoc(c, l.getLatitude(), l.getLongitude(), null);
                publishLocation(c);
            } else if (NearlyStore.sp(c).getBoolean("locWasOn", false)) {
                NearlyApi.send("DELETE", urlFor(c, s + "/loc"), "");
                NearlyStore.sp(c).edit().putBoolean("locWasOn", false).apply();
            }
        } catch (Exception ignored) { }
    }

    static void storeMyLoc(Context c, double lat, double lng, String name) {
        android.content.SharedPreferences.Editor e = NearlyStore.sp(c).edit();
        e.putLong("myloc_lat", Double.doubleToLongBits(lat));
        e.putLong("myloc_lng", Double.doubleToLongBits(lng));
        e.putLong("myloc_ts", NearlyUtil.now());
        if (name != null) e.putString("myloc_name", name);
        e.apply();
    }

    /** Publishes the latest local fix, blurred to the chosen precision. */
    static void publishLocation(Context c) throws Exception {
        android.content.SharedPreferences p = NearlyStore.sp(c);
        if (!p.contains("myloc_lat")) return;
        double lat = Double.longBitsToDouble(p.getLong("myloc_lat", 0));
        double lng = Double.longBitsToDouble(p.getLong("myloc_lng", 0));
        String prec = NearlyStore.privStr(c, "locationPrecision", "approx");
        int dec = prec.equals("exact") ? 5 : prec.equals("city") ? 1 : 2;
        double f = Math.pow(10, dec);
        JSONObject o = new JSONObject();
        NearlyUtil.put(o, "lat", Math.round(lat * f) / f);
        NearlyUtil.put(o, "lng", Math.round(lng * f) / f);
        NearlyUtil.put(o, "ts", p.getLong("myloc_ts", NearlyUtil.now()));
        String name = p.getString("myloc_name", "");
        if (!name.isEmpty()) NearlyUtil.put(o, "name", name);
        NearlyUtil.put(o, "prec", prec);
        String s = NearlyStore.slot(c);
        NearlyApi.send("PUT", urlFor(c, s + "/loc"), o.toString());
        p.edit().putBoolean("locWasOn", true).apply();
        NearlyStore.cacheSet(c, s + "/loc", o);
    }

    static Location lastKnown(Context c) {
        try {
            if (c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
                    && c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null;
            LocationManager lm = (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
            Location best = null;
            for (String prov : lm.getProviders(true)) {
                Location l = lm.getLastKnownLocation(prov);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            }
            return best;
        } catch (Throwable t) {
            return null;
        }
    }

    // ---------------- background schedule ----------------
    static void schedule(Context c) {
        NearlyMsgAlarm.schedule(c, false);          // Messages widget: fixed 10-minute check
        try {
            JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (js == null) return;
            int min = NearlyWidgets.minRefreshMin(c);
            if (min <= 0) { js.cancel(JOB_ID); return; }
            long ms = Math.max(15, min) * 60000L;
            for (JobInfo ji : js.getAllPendingJobs()) if (ji.getId() == JOB_ID && ji.getIntervalMillis() == ms) return;
            ComponentName cn = new ComponentName(c, NearlySyncService.class);
            try {
                js.schedule(new JobInfo.Builder(JOB_ID, cn).setPeriodic(ms)
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPersisted(true).build());
            } catch (SecurityException se) {   // RECEIVE_BOOT_COMPLETED missing
                js.schedule(new JobInfo.Builder(JOB_ID, cn).setPeriodic(ms)
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).build());
            }
        } catch (Throwable ignored) { }
    }
}
