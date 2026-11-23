package com.benjamin.nearly; // CHANGE to your app's package

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.RemoteViews;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class NearlyWidgetProvider extends AppWidgetProvider {
    static final String ACT_HEART = "com.benjamin.nearly.WIDGET_HEART"; // keep in sync with your package

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        for (int id : ids) m.updateAppWidget(id, build(c));
    }

    static RemoteViews build(Context c) {
        SharedPreferences p = c.getSharedPreferences("nearly_widget", Context.MODE_PRIVATE);
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.nearly_widget);
        v.setTextViewText(R.id.w_l1, p.getString("l1", "Nearly"));
        v.setTextViewText(R.id.w_l2, p.getString("l2", "Open the app to set up"));
        v.setTextViewText(R.id.w_l3, p.getString("l3", ""));
        Intent beat = new Intent(c, NearlyWidgetProvider.class).setAction(ACT_HEART);
        v.setOnClickPendingIntent(R.id.w_heart,
            PendingIntent.getBroadcast(c, 0, beat, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        Intent open = c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());
        if (open != null) v.setOnClickPendingIntent(R.id.w_root,
            PendingIntent.getActivity(c, 1, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        return v;
    }

    static void refresh(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        int[] ids = m.getAppWidgetIds(new ComponentName(c, NearlyWidgetProvider.class));
        if (ids.length > 0) m.updateAppWidget(ids, build(c));
    }

    @Override
    public void onReceive(final Context c, Intent i) {
        super.onReceive(c, i);
        if (!ACT_HEART.equals(i.getAction())) return;
        final PendingResult pr = goAsync();
        new Thread(new Runnable() {
            public void run() {
                SharedPreferences p = c.getSharedPreferences("nearly_widget", Context.MODE_PRIVATE);
                try {
                    int n = p.getInt("hearts", 0) + 1;
                    long ts = System.currentTimeMillis();
                    String url = p.getString("url", ""), code = p.getString("code", ""), slot = p.getString("slot", "");
                    p.edit().putInt("hearts", n).putLong("hl", ts).apply();
                    if (!url.isEmpty() && !code.isEmpty() && !slot.isEmpty()) {
                        String base = url.replaceAll("/+$", "") + "/c/" + code + "/" + slot;
                        put(base + "/hearts.json", String.valueOf(n));
                        put(base + "/hl.json", String.valueOf(ts));
                        p.edit().putString("l3", "💗 Heart sent!").apply();
                    } else {
                        p.edit().putString("l3", "Pair in the app to send hearts").apply();
                    }
                } catch (Exception e) {
                    p.edit().putString("l3", "Couldn’t send. Check your connection.").apply();
                } finally {
                    refresh(c);
                    pr.finish();
                }
            }
        }).start();
    }

    static void put(String u, String body) throws Exception {
        HttpURLConnection h = (HttpURLConnection) new URL(u).openConnection();
        h.setRequestMethod("PUT");
        h.setConnectTimeout(8000);
        h.setReadTimeout(8000);
        h.setDoOutput(true);
        OutputStream o = h.getOutputStream();
        o.write(body.getBytes("UTF-8"));
        o.close();
        if (h.getResponseCode() >= 300) throw new Exception("http " + h.getResponseCode());
        h.disconnect();
    }
}
