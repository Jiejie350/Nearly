package com.benjamin.nearly; // CHANGE to your app's package

import android.Manifest;
import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import org.json.JSONObject;
import java.util.Iterator;
import java.util.LinkedHashMap;

/**
 * JS bridge. `update` / `getHearts` are the original methods (unchanged behaviour).
 * Everything else is new: see nearly-widgets.js for friendly wrappers.
 */
@CapacitorPlugin(name = "NearlyWidget")
public class NearlyWidgetPlugin extends Plugin {
    private SharedPreferences prefs() {
        return getContext().getSharedPreferences("nearly_widget", Context.MODE_PRIVATE);
    }

    // ---------------------------------------------------------------- original API
    @PluginMethod
    public void update(PluginCall call) {
        SharedPreferences p = prefs();
        SharedPreferences.Editor e = p.edit();
        // only overwrite what the app actually sent, so a names-only call can't wipe the pairing
        if (call.hasOption("l1")) e.putString("l1", call.getString("l1", "Nearly"));
        if (call.hasOption("l2")) e.putString("l2", call.getString("l2", ""));
        if (call.hasOption("l3")) e.putString("l3", call.getString("l3", ""));
        if (call.hasOption("url")) e.putString("url", call.getString("url", ""));
        if (call.hasOption("code")) e.putString("code", call.getString("code", ""));
        if (call.hasOption("slot")) e.putString("slot", call.getString("slot", ""));
        // new, optional
        if (call.hasOption("pslot")) e.putString("pslot", call.getString("pslot", ""));
        if (call.hasOption("name")) e.putString("myName", call.getString("name", ""));
        if (call.hasOption("partnerName")) e.putString("pName", call.getString("partnerName", ""));
        Integer h = call.getInt("hearts", 0);
        if (h != null && h > p.getInt("hearts", 0)) e.putInt("hearts", h);
        e.apply();
        NearlyWidgetProvider.refresh(getContext());
        NearlyWidgets.refreshAll(getContext());
        NearlySync.schedule(getContext());
        call.resolve();
    }

    @PluginMethod
    public void getHearts(PluginCall call) {
        SharedPreferences p = prefs();
        JSObject o = new JSObject();
        o.put("hearts", p.getInt("hearts", 0));
        o.put("hl", p.getLong("hl", 0));
        call.resolve(o);
    }

    // ---------------------------------------------------------------- sync
    @PluginMethod
    public void syncNow(PluginCall call) {
        boolean ok = NearlySync.run(getContext());
        JSObject o = new JSObject();
        o.put("ok", ok);
        call.resolve(o);
    }

    @PluginMethod
    public void refreshWidgets(PluginCall call) {
        NearlyWidgets.refreshAll(getContext());
        call.resolve();
    }

    /** Whole cached couple node (works offline). */
    @PluginMethod
    public void getCache(PluginCall call) {
        try {
            JSObject o = new JSObject();
            o.put("data", new JSObject(NearlyStore.cache(getContext()).toString()));
            o.put("cacheTs", NearlyStore.cacheTs(getContext()));
            o.put("queued", NearlyStore.queue(getContext()).length());
            call.resolve(o);
        } catch (Exception e) {
            call.reject(e.getMessage());
        }
    }

    /** Generic synced write: {path:"events/abc", value:{...}} ; value null deletes. */
    @PluginMethod
    public void write(PluginCall call) {
        String path = call.getString("path");
        if (path == null || path.isEmpty()) { call.reject("path required"); return; }
        Object v = call.getData().opt("value");
        if (v == JSONObject.NULL) v = null;
        NearlySync.write(getContext(), path, v);
        call.resolve();
    }

    // ---------------------------------------------------------------- status
    @PluginMethod
    public void setStatus(PluginCall call) {
        NearlySync.setMood(getContext(), call.getString("mood"), call.getString("moodMsg"),
                call.getString("activity"), call.getString("status"));
        call.resolve();
    }

    @PluginMethod
    public void sendAffection(PluginCall call) {   // kind: heart | kiss | hug
        String kind = call.getString("kind", "heart");
        NearlyActionReceiver.handle(getContext(), 0, kind, "");
        call.resolve();
    }

    @PluginMethod
    public void notifyPartner(PluginCall call) {
        NearlySync.notifyPartner(getContext(), call.getString("title", "💌 Nearly"), call.getString("body", ""));
        call.resolve();
    }

    @PluginMethod
    public void setOnline(PluginCall call) {      // heartbeat from JS if you prefer it over MainActivity hooks
        NearlySync.presence(getContext(), Boolean.TRUE.equals(call.getBoolean("online", true)));
        call.resolve();
    }

    // ---------------------------------------------------------------- privacy + location
    @PluginMethod
    public void setPrivacy(PluginCall call) {
        Context c = getContext();
        String[] flags = {"sharePresence", "shareStatus", "shareBattery", "shareLocation", "maskPrivate", "notifications"};
        for (String f : flags) if (call.hasOption(f)) NearlyStore.setPriv(c, f, Boolean.TRUE.equals(call.getBoolean(f, false)));
        if (call.hasOption("locationPrecision")) NearlyStore.setPrivStr(c, "locationPrecision", call.getString("locationPrecision", "approx"));
        NearlySync.pushDevice(c);
        NearlyWidgets.refreshAll(c);
        call.resolve(getPrivacyObj());
    }

    @PluginMethod
    public void getPrivacy(PluginCall call) { call.resolve(getPrivacyObj()); }

    private JSObject getPrivacyObj() {
        Context c = getContext();
        JSObject o = new JSObject();
        o.put("sharePresence", NearlyStore.priv(c, "sharePresence", true));
        o.put("shareStatus", NearlyStore.priv(c, "shareStatus", true));
        o.put("shareBattery", NearlyStore.priv(c, "shareBattery", false));
        o.put("shareLocation", NearlyStore.priv(c, "shareLocation", false));
        o.put("locationPrecision", NearlyStore.privStr(c, "locationPrecision", "approx"));
        o.put("maskPrivate", NearlyStore.priv(c, "maskPrivate", true));
        o.put("notifications", NearlyStore.priv(c, "notifications", true));
        return o;
    }

    /** Push a position you got from JS (e.g. Capacitor Geolocation). Blurred per the precision setting. */
    @PluginMethod
    public void setLocation(PluginCall call) {
        Double lat = call.getDouble("lat"), lng = call.getDouble("lng");
        if (lat == null || lng == null) { call.reject("lat and lng required"); return; }
        Context c = getContext();
        NearlySync.storeMyLoc(c, lat, lng, call.getString("name"));
        try {
            if (NearlyStore.priv(c, "shareLocation", false)) NearlySync.publishLocation(c);
        } catch (Exception ignored) { }
        NearlyWidgets.refreshAll(c);
        call.resolve();
    }

    /** what: "notifications" | "location" | "locationAlways". Result arrives asynchronously: re-check with permissionState. */
    @PluginMethod
    public void askPermission(PluginCall call) {
        String what = call.getString("what", "notifications");
        Activity a = getActivity();
        if (a == null) { call.reject("no activity"); return; }
        if (what.equals("notifications") && Build.VERSION.SDK_INT >= 33) {
            a.requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 7301);
        } else if (what.equals("location")) {
            a.requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}, 7302);
        } else if (what.equals("locationAlways") && Build.VERSION.SDK_INT >= 29) {
            a.requestPermissions(new String[]{"android.permission.ACCESS_BACKGROUND_LOCATION"}, 7303);
        }
        call.resolve();
    }

    @PluginMethod
    public void permissionState(PluginCall call) {
        Context c = getContext();
        JSObject o = new JSObject();
        o.put("notifications", Build.VERSION.SDK_INT < 33 || c.checkSelfPermission("android.permission.POST_NOTIFICATIONS") == PackageManager.PERMISSION_GRANTED);
        o.put("location", c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED);
        call.resolve(o);
    }

    // ---------------------------------------------------------------- photos
    /**
     * {id?, base64, kind:"gallery"|"partner"|"couple"|"hw", caption, fav, date:"YYYY-MM-DD"}.
     * Keep the image small (≈640 px JPEG). partner/couple/hw use fixed ids so they replace the old one.
     */
    @PluginMethod
    public void addPhoto(PluginCall call) {
        Context c = getContext();
        String b64 = call.getString("base64");
        if (b64 == null || b64.isEmpty()) { call.reject("base64 required"); return; }
        String kind = call.getString("kind", "gallery");
        String slot = NearlyStore.slot(c);
        String id = call.getString("id");
        if (id == null || id.isEmpty()) {
            if (kind.equals("partner")) id = "profile_" + slot;
            else if (kind.equals("couple")) id = "couple";
            else if (kind.equals("hw")) id = "hw";
            else id = "p" + Long.toString(NearlyUtil.now(), 36);
        }
        try {
            long ts = NearlyUtil.now();
            byte[] bytes = NearlyPhotos.decodeB64(b64);
            NearlyPhotos.saveBytes(c, id, bytes);
            c.getSharedPreferences("nearly_widget", Context.MODE_PRIVATE).edit().putLong("ph." + id, ts).apply();
            JSONObject idx = new JSONObject();
            NearlyUtil.put(idx, "ts", ts);
            NearlyUtil.put(idx, "by", slot);
            NearlyUtil.put(idx, "kind", kind);
            NearlyUtil.put(idx, "cap", call.getString("caption", ""));
            NearlyUtil.put(idx, "fav", Boolean.TRUE.equals(call.getBoolean("fav", false)));
            if (call.hasOption("date")) NearlyUtil.put(idx, "d", call.getString("date", ""));
            String clean = b64.contains("base64,") ? b64.substring(b64.indexOf("base64,") + 7) : b64;
            NearlySync.push(c, "~p/" + id, clean);                 // bytes first, then the index that references them
            NearlySync.write(c, "photoIndex/" + id, idx);
            JSObject o = new JSObject();
            o.put("id", id);
            call.resolve(o);
        } catch (Exception e) {
            call.reject(e.getMessage());
        }
    }

    @PluginMethod
    public void setPhotoMeta(PluginCall call) {   // {id, caption?, fav?}
        String id = call.getString("id");
        if (id == null) { call.reject("id required"); return; }
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        if (call.hasOption("caption")) m.put("photoIndex/" + id + "/cap", call.getString("caption", ""));
        if (call.hasOption("fav")) m.put("photoIndex/" + id + "/fav", Boolean.TRUE.equals(call.getBoolean("fav", false)));
        NearlySync.writeAll(getContext(), m);
        call.resolve();
    }

    @PluginMethod
    public void removePhoto(PluginCall call) {
        String id = call.getString("id");
        if (id == null) { call.reject("id required"); return; }
        NearlySync.write(getContext(), "photoIndex/" + id, null);
        NearlySync.push(getContext(), "~p/" + id, null);
        NearlyPhotos.file(getContext(), id).delete();
        NearlyWidgets.refreshAll(getContext());
        call.resolve();
    }

    // ---------------------------------------------------------------- live chat / call helpers
    /** Light sync for the open Chat screen: pull the couple node, refresh cache, repaint widgets only if chat changed. */
    @PluginMethod
    public void pull(final PluginCall call) {
        final Context c = getContext();
        new Thread(new Runnable() {
            public void run() {
                JSObject o = new JSObject();
                try {
                    if (!NearlyStore.paired(c)) { o.put("ok", false); call.resolve(o); return; }
                    try { NearlySync.flushQueue(c); } catch (Exception ignored) { }
                    JSONObject now = NearlySync.parseObj(NearlyApi.get(NearlySync.base(c) + ".json"));
                    JSONObject prev = NearlyStore.cache(c);
                    boolean changed = !String.valueOf(prev.optJSONObject("notes")).equals(String.valueOf(now.optJSONObject("notes")));
                    NearlyStore.setCache(c, now);
                    try { NearlyPhotos.sync(c, now); } catch (Throwable ignored) { }
                    if (changed) NearlyWidgets.refreshAll(c);
                    o.put("ok", true);
                } catch (Exception e) { o.put("ok", false); }
                call.resolve(o);
            }
        }).start();
    }

    /** {path} -> {data} straight from the database (used for call signalling). */
    @PluginMethod
    public void fetchPath(final PluginCall call) {
        final Context c = getContext();
        final String path = call.getString("path", "");
        new Thread(new Runnable() {
            public void run() {
                JSObject o = new JSObject();
                try {
                    Object v = new org.json.JSONTokener(NearlyApi.get(NearlySync.urlFor(c, path))).nextValue();
                    o.put("data", v == JSONObject.NULL ? null : v);
                } catch (Exception e) { o.put("error", String.valueOf(e.getMessage())); }
                call.resolve(o);
            }
        }).start();
    }

    /** {path, value} - quick write with no widget repaint (typing, read receipts, call signalling). */
    @PluginMethod
    public void signal(final PluginCall call) {
        final Context c = getContext();
        final String path = call.getString("path", "");
        Object v0 = call.getData().opt("value");
        final Object v = v0 == JSONObject.NULL ? null : v0;
        new Thread(new Runnable() {
            public void run() {
                try { NearlySync.writeLocal(c, path, v); NearlySync.push(c, path, v); } catch (Throwable ignored) { }
                call.resolve();
            }
        }).start();
    }

    // ---------------------------------------------------------------- chat (text + handwriting)
    /** {text, editId?} - sends a text message (editId replaces your earlier one). */
    @PluginMethod
    public void sendMessage(final PluginCall call) {
        final String text = call.getString("text", "").trim();
        final String editId = call.getString("editId", "");
        if (text.isEmpty()) { call.reject("text required"); return; }
        final Context c = getContext();
        new Thread(new Runnable() {
            public void run() {
                String id = NearlyMsgs.sendText(c, text, editId);
                JSObject o = new JSObject();
                o.put("id", id == null ? "" : id);
                o.put("ok", id != null);
                call.resolve(o);
            }
        }).start();
    }

    /** {base64} - PNG or JPEG of a handwritten message (data-URL prefix allowed). */
    @PluginMethod
    public void sendDrawing(final PluginCall call) {
        final String b64 = call.getString("base64");
        if (b64 == null || b64.isEmpty()) { call.reject("base64 required"); return; }
        final Context c = getContext();
        new Thread(new Runnable() {
            public void run() {
                String id = NearlyMsgs.sendDrawing(c, NearlyPhotos.decodeB64(b64));
                JSObject o = new JSObject();
                o.put("id", id == null ? "" : id);
                o.put("ok", id != null);
                call.resolve(o);
            }
        }).start();
    }

    /** {id} -> {dataUrl} for a cached handwriting image (empty if not downloaded yet). */
    @PluginMethod
    public void getImage(PluginCall call) {
        JSObject o = new JSObject();
        try {
            java.io.File f = NearlyPhotos.file(getContext(), call.getString("id", ""));
            if (f.exists()) {
                java.io.FileInputStream in = new java.io.FileInputStream(f);
                java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                in.close();
                o.put("dataUrl", "data:image/png;base64," + android.util.Base64.encodeToString(bo.toByteArray(), android.util.Base64.NO_WRAP));
            } else o.put("dataUrl", "");
        } catch (Exception e) { o.put("dataUrl", ""); }
        call.resolve(o);
    }

    // ---------------------------------------------------------------- widget management
    /** {id} - opens that widget's settings screen (same one the launcher's "reconfigure" uses). */
    @PluginMethod
    public void editWidget(PluginCall call) {
        Integer id = call.getInt("id");
        if (id == null) { call.reject("id required"); return; }
        android.content.Intent i = new android.content.Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE);
        i.setClass(getContext(), NearlyConfigActivity.class);
        i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, (int) id);
        getActivity().startActivity(i);
        call.resolve();
    }

    @PluginMethod
    public void listWidgets(PluginCall call) {
        Context c = getContext();
        JSArray arr = new JSArray();
        for (String t : NearlyWidgets.TYPES) {
            for (int id : NearlyWidgets.ids(c, t)) {
                JSObject o = new JSObject();
                o.put("id", id);
                o.put("type", t);
                o.put("mode", NearlyStore.w(c, id, "mode", ""));
                arr.put(o);
            }
        }
        JSObject r = new JSObject();
        r.put("widgets", arr);
        call.resolve(r);
    }

    /** Ask the launcher to pin a widget: {type:"love"} (Android 8+; user confirms). */
    @PluginMethod
    public void pinWidget(PluginCall call) {
        Context c = getContext();
        Class<?> k = NearlyWidgets.cls(call.getString("type", "love"));
        if (k == null) { call.reject("unknown type"); return; }
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        if (Build.VERSION.SDK_INT >= 26 && m.isRequestPinAppWidgetSupported()) {
            m.requestPinAppWidget(new ComponentName(c, k), null, null);
            call.resolve();
        } else {
            call.reject("This launcher can't pin widgets from apps. Long-press the home screen > Widgets > Nearly.");
        }
    }

    @PluginMethod
    public void getWidgetSettings(PluginCall call) {
        Integer id = call.getInt("id");
        if (id == null) { call.reject("id required"); return; }
        JSObject o = new JSObject();
        String prefix = "w" + id + ".";
        for (java.util.Map.Entry<String, ?> e : prefs().getAll().entrySet())
            if (e.getKey().startsWith(prefix)) o.put(e.getKey().substring(prefix.length()), String.valueOf(e.getValue()));
        call.resolve(o);
    }

    /** {id, settings:{mode:"daily", theme:"dark", ...}} - same keys the settings screen uses. */
    @PluginMethod
    public void setWidgetSettings(PluginCall call) {
        Integer id = call.getInt("id");
        JSObject s = call.getObject("settings");
        if (id == null || s == null) { call.reject("id and settings required"); return; }
        Iterator<String> it = s.keys();
        while (it.hasNext()) {
            String k = it.next();
            NearlyStore.setW(getContext(), id, k, s.isNull(k) ? null : s.optString(k));
        }
        NearlyWidgets.refreshAll(getContext());
        call.resolve();
    }

    @PluginMethod
    public void resetWidget(PluginCall call) {
        Integer id = call.getInt("id");
        if (id == null) { call.reject("id required"); return; }
        NearlyStore.resetW(getContext(), id);
        NearlyWidgets.refreshAll(getContext());
        call.resolve();
    }

    // ---------------------------------------------------------------- deep link from a widget tap
    @PluginMethod
    public void getPendingPage(PluginCall call) {
        SharedPreferences p = prefs();
        JSObject o = new JSObject();
        o.put("page", p.getString("pendingPage", ""));
        p.edit().remove("pendingPage").apply();
        call.resolve(o);
    }
}
