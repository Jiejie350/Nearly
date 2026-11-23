package com.benjamin.nearly; // CHANGE to your app's package

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

/**
 * Photo cache. Metadata lives in the couple node (photoIndex), the (small, base64) image
 * bytes live under  <url>/p/<code>/<id>.json  so they never bloat the normal sync.
 * Files are cached in filesDir/nearly_photos so widgets work offline.
 */
final class NearlyPhotos {
    static final int MAX_PHOTOS = 40;

    static File dir(Context c) {
        File d = new File(c.getFilesDir(), "nearly_photos");
        d.mkdirs();
        return d;
    }

    static File file(Context c, String id) { return new File(dir(c), id.replaceAll("[^A-Za-z0-9_-]", "_") + ".img"); }

    static Bitmap decodeFile(File f, int maxW, int maxH) {
        try {
            if (f == null || !f.exists()) return null;
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getAbsolutePath(), o);
            int s = 1;
            while (o.outWidth / (s * 2) >= maxW && o.outHeight / (s * 2) >= maxH) s *= 2;
            o.inJustDecodeBounds = false;
            o.inSampleSize = s;
            return BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        } catch (Throwable t) {
            return null;
        }
    }

    static Bitmap load(Context c, String id, int maxW, int maxH) { return decodeFile(file(c, id), maxW, maxH); }

    static byte[] decodeB64(String b64) {
        String s = b64 == null ? "" : b64;
        int comma = s.indexOf("base64,");
        if (comma >= 0) s = s.substring(comma + 7);
        return Base64.decode(s, Base64.DEFAULT);
    }

    static void saveBytes(Context c, String id, byte[] data) throws Exception {
        FileOutputStream o = new FileOutputStream(file(c, id));
        o.write(data);
        o.close();
    }

    /** Entries for a photo widget mode, newest first. Each object gets an "id" field. */
    static List<JSONObject> pick(NearlyData d, String mode, Context c, String selId) {
        ArrayList<JSONObject> all = new ArrayList<>();
        JSONObject idx = d.o("photoIndex");
        Iterator<String> it = idx.keys();
        while (it.hasNext()) {
            String id = it.next();
            JSONObject j = idx.optJSONObject(id);
            if (j == null || !file(c, id).exists()) continue;
            NearlyUtil.put(j, "id", id);
            all.add(j);
        }
        Collections.sort(all, new Comparator<JSONObject>() {
            public int compare(JSONObject a, JSONObject b) { return Long.compare(b.optLong("ts"), a.optLong("ts")); }
        });

        ArrayList<JSONObject> out = new ArrayList<>();
        for (JSONObject j : all) {
            String kind = j.optString("kind", "gallery");
            boolean take;
            switch (mode) {
                case "partner": take = kind.equals("partner") && j.optString("by").equals(d.pslot); break;
                case "couple": take = kind.equals("couple"); break;
                case "favorites": take = j.optBoolean("fav", false) && !kind.equals("partner") && !kind.equals("hw"); break;
                case "recent": take = !kind.equals("partner") && !kind.equals("hw"); break;
                case "pick": take = j.optString("id").equals(selId); break;
                case "hw": take = kind.equals("hw"); break;
                default: take = kind.equals("gallery") || kind.equals("couple"); break; // gallery
            }
            if (take) out.add(j);
        }
        if (mode.equals("recent") && out.size() > 1) return out.subList(0, 1);
        if ((mode.equals("partner") || mode.equals("couple")) && out.size() > 1) return out.subList(0, 1);
        return out;
    }

    /** Download missing / changed photos, prune the rest. */
    static void sync(Context c, JSONObject root) {
        JSONObject idx = root.optJSONObject("photoIndex");
        File dir = dir(c);
        ArrayList<String> ids = new ArrayList<>();
        final JSONObject fidx = idx;
        if (idx != null) {
            Iterator<String> it = idx.keys();
            while (it.hasNext()) ids.add(it.next());
            Collections.sort(ids, new Comparator<String>() {
                public int compare(String a, String b) {
                    JSONObject ja = fidx.optJSONObject(a), jb = fidx.optJSONObject(b);
                    return Long.compare(jb == null ? 0 : jb.optLong("ts"), ja == null ? 0 : ja.optLong("ts"));
                }
            });
        }
        HashSet<String> keep = new HashSet<>();
        int n = 0;
        for (String id : ids) {
            JSONObject j = idx.optJSONObject(id);
            if (j == null) continue;
            boolean special = j.optString("kind", "").equals("partner") || j.optString("kind", "").equals("couple") || j.optString("kind", "").equals("hw");
            if (!special && ++n > MAX_PHOTOS) continue;
            keep.add(file(c, id).getName());
            long ts = j.optLong("ts", 0);
            File f = file(c, id);
            if (f.exists() && NearlyStore.sp(c).getLong("ph." + id, -1) == ts) continue;
            try {
                String u = NearlyStore.url(c) + "/p/" + NearlyStore.code(c) + "/" + id + ".json";
                Object v = new JSONTokener(NearlyApi.get(u)).nextValue();
                if (v instanceof String) {
                    saveBytes(c, id, decodeB64((String) v));
                    NearlyStore.sp(c).edit().putLong("ph." + id, ts).apply();
                }
            } catch (Exception ignored) { }
        }
        File[] files = dir.listFiles();
        if (files != null) for (File f : files) if (!keep.contains(f.getName())) f.delete();
    }

    static Bitmap bitmapForId(Context c, String id, int w, int h) { return load(c, id, w, h); }
}
