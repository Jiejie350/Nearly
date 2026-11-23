package com.benjamin.nearly; // CHANGE to your app's package

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import org.json.JSONObject;
import java.util.List;

/** Notification-based updates: partner hearts/kisses/hugs, direct pings, notes, game invites. */
final class NearlyNotifier {
    static final String CHANNEL = "nearly_partner";

    /** Compare the previous cache with the fresh snapshot and notify about what the partner did. */
    static void check(Context c, JSONObject prev, JSONObject now) {
        if (prev == null || prev.length() == 0) return;                // first sync: just seed, don't spam
        if (!NearlyStore.priv(c, "notifications", true)) return;
        String me = NearlyStore.slot(c), ps = NearlyStore.pslot(c);
        JSONObject p0 = prev.optJSONObject(ps), p1 = now.optJSONObject(ps);
        if (p1 == null) return;
        if (p0 == null) p0 = new JSONObject();
        NearlyData d = new NearlyData(c);
        String pn = d.pName();

        if (p1.optInt("hearts", 0) > p0.optInt("hearts", 0)) show(c, 1, "💗 " + pn, "sent you a heart");
        if (p1.optInt("kisses", 0) > p0.optInt("kisses", 0)) show(c, 2, "💋 " + pn, "sent you a kiss");
        if (p1.optInt("hugs", 0) > p0.optInt("hugs", 0)) show(c, 3, "🤗 " + pn, "sent you a hug");

        JSONObject n = now.optJSONObject("notify");
        JSONObject mine = n == null ? null : n.optJSONObject(me);
        if (mine != null && mine.optLong("ts", 0) > NearlyStore.sp(c).getLong("seenNotify", 0)) {
            NearlyStore.sp(c).edit().putLong("seenNotify", mine.optLong("ts")).apply();
            show(c, 4, mine.optString("title", "💌 " + pn), mine.optString("body", ""));
        }

        JSONObject g = now.optJSONObject("game");
        JSONObject inv = g == null ? null : g.optJSONObject("invite");
        if (inv != null && inv.optString("to").equals(me) && inv.optLong("ts", 0) > NearlyStore.sp(c).getLong("seenInvite", 0)) {
            NearlyStore.sp(c).edit().putLong("seenInvite", inv.optLong("ts")).apply();
            show(c, 5, "🎮 " + pn, "invited you to play " + inv.optString("game", "a game"));
        }

        // newest note from partner
        JSONObject notes = now.optJSONObject("notes");
        if (notes != null) {
            long seen = NearlyStore.sp(c).getLong("seenNote", 0), top = seen;
            JSONObject best = null;
            java.util.Iterator<String> it = notes.keys();
            while (it.hasNext()) {
                JSONObject j = notes.optJSONObject(it.next());
                if (j == null || !j.optString("by").equals(ps)) continue;
                long ts = j.optLong("ts", 0);
                String to = j.optString("to", "");
                if (!to.isEmpty() && !to.equals(me)) continue;
                if (ts > top) { top = ts; best = j; }
            }
            if (top > seen) {
                NearlyStore.sp(c).edit().putLong("seenNote", top).apply();
                if (best != null) {
                    boolean priv = best.optString("kind").equals("private");
                    show(c, 6, "💌 " + pn, priv ? "sent you a private message" : best.optString("type").equals("draw") ? "✍️ sent you a handwritten message" : NearlyUtil.clip(best.optString("text"), 120));
                }
            }
        }
    }

    static void show(Context c, int id, String title, String text) {
        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            if (Build.VERSION.SDK_INT >= 26) {
                nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Partner updates", NotificationManager.IMPORTANCE_DEFAULT));
            }
            Intent open = c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());
            PendingIntent pi = open == null ? null
                    : PendingIntent.getActivity(c, id, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, CHANNEL) : new Notification.Builder(c);
            b.setSmallIcon(R.drawable.ic_nearly_heart).setContentTitle(title).setContentText(text).setAutoCancel(true);
            if (pi != null) b.setContentIntent(pi);
            nm.notify(7300 + id, b.build());
        } catch (Throwable ignored) { }
    }
}
