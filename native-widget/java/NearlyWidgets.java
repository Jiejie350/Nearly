package com.benjamin.nearly; // CHANGE to your app's package

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;

/** Registry of the widget types + helpers that act on every instance. */
final class NearlyWidgets {
    static final String[] TYPES = {"photo", "love", "relationship", "status", "location", "mood",
            "countdown", "notes", "interactive", "game", "candle"};

    static Class<?> cls(String type) {
        switch (type) {
            case "photo": return NearlyPhotoWidget.class;
            case "love": return NearlyLoveWidget.class;
            case "relationship": return NearlyRelationshipWidget.class;
            case "status": return NearlyStatusWidget.class;
            case "location": return NearlyLocationWidget.class;
            case "mood": return NearlyMoodWidget.class;
            case "countdown": return NearlyCountdownWidget.class;
            case "notes": return NearlyNotesWidget.class;
            case "interactive": return NearlyInteractiveWidget.class;
            case "game": return NearlyGameWidget.class;
            case "candle": return NearlyCandleWidget.class;
            default: return null;
        }
    }

    static int[] ids(Context c, String type) {
        Class<?> k = cls(type);
        if (k == null) return new int[0];
        return AppWidgetManager.getInstance(c).getAppWidgetIds(new ComponentName(c, k));
    }

    static String typeOf(Context c, int id) {
        AppWidgetProviderInfo i = AppWidgetManager.getInstance(c).getAppWidgetInfo(id);
        if (i == null) return null;
        String n = i.provider.getClassName();
        for (String t : TYPES) if (cls(t).getName().equals(n)) return t;
        return null;
    }

    static int count(Context c) {
        int n = 0;
        for (String t : TYPES) n += ids(c, t).length;
        return n;
    }

    /** Repaint every Nearly widget on the home screen (cheap, local data only). */
    static void refreshAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        for (String t : TYPES) {
            for (int id : ids(c, t)) NearlyRenderer.update(c, m, id, t);
        }
        try { NearlyWidgetProvider.refresh(c); } catch (Throwable ignored) { }   // classic widget
    }

    /** Smallest per-widget refresh interval in minutes; 0 when every widget is manual-only. */
    static int minRefreshMin(Context c) {
        int min = Integer.MAX_VALUE;
        for (String t : TYPES) {
            for (int id : ids(c, t)) {
                int v = NearlyStore.wi(c, id, "refresh", 30);
                if (v > 0 && v < min) min = v;
            }
        }
        return min == Integer.MAX_VALUE ? 0 : min;
    }
}
