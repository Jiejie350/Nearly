package com.benjamin.nearly; // CHANGE to your app's package

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Base64;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;

/**
 * Chat messages (text or handwriting) shared by the widget composer and the app's Chat tab.
 *   notes/<id>      { kind:"chat", type:"text"|"draw", text?, img?, by, ts, edited? }
 *   photoIndex/<id> { kind:"msg", ... }   handwriting image (bytes under /p/<code>/<id>)
 * Call from a background thread (network).
 */
final class NearlyMsgs {

    static String sendText(Context c, String text, String editId) {
        if (!NearlyStore.paired(c)) { NearlyStore.setFeedback(c, "Pair in the app first"); NearlyWidgets.refreshAll(c); return null; }
        String slot = NearlyStore.slot(c);
        long ts = NearlyUtil.now();
        String id = editId == null || editId.isEmpty() ? Long.toString(ts, 36) : editId;
        JSONObject n = new JSONObject();
        NearlyUtil.put(n, "kind", "chat");
        NearlyUtil.put(n, "type", "text");
        NearlyUtil.put(n, "text", text);
        NearlyUtil.put(n, "by", slot);
        NearlyUtil.put(n, "ts", ts);
        if (editId != null && !editId.isEmpty()) NearlyUtil.put(n, "edited", true);
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        m.put("notes/" + id, n);
        NearlyStore.setFeedback(c, "💌 Sent");
        NearlySync.writeAll(c, m);
        return id;
    }

    static String sendDrawing(Context c, byte[] png) {
        if (!NearlyStore.paired(c)) { NearlyStore.setFeedback(c, "Pair in the app first"); NearlyWidgets.refreshAll(c); return null; }
        try {
            String slot = NearlyStore.slot(c);
            long ts = NearlyUtil.now();
            String key = Long.toString(ts, 36) + "d";
            String img = "m" + key;   // key already unique per drawing
            NearlyPhotos.saveBytes(c, img, png);
            NearlyStore.sp(c).edit().putLong("ph." + img, ts).apply();
            JSONObject idx = new JSONObject();
            NearlyUtil.put(idx, "ts", ts);
            NearlyUtil.put(idx, "by", slot);
            NearlyUtil.put(idx, "kind", "msg");
            NearlySync.push(c, "~p/" + img, Base64.encodeToString(png, Base64.NO_WRAP));   // bytes first
            JSONObject n = new JSONObject();
            NearlyUtil.put(n, "kind", "chat");
            NearlyUtil.put(n, "type", "draw");
            NearlyUtil.put(n, "img", img);
            NearlyUtil.put(n, "by", slot);
            NearlyUtil.put(n, "ts", ts);
            LinkedHashMap<String, Object> m = new LinkedHashMap<>();
            m.put("photoIndex/" + img, idx);
            m.put("notes/" + key, n);
            NearlyStore.setFeedback(c, "✍️ Sent");
            NearlySync.writeAll(c, m);
            return key;
        } catch (Exception e) {
            NearlyStore.setFeedback(c, "Couldn't send — try again");
            NearlyWidgets.refreshAll(c);
            return null;
        }
    }

    static byte[] png(Bitmap b) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        b.compress(Bitmap.CompressFormat.PNG, 90, o);
        return o.toByteArray();
    }
}
