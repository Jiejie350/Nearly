package com.benjamin.nearly; // CHANGE to your app's package

import android.content.Context;
import android.graphics.Color;
import org.json.JSONException;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

/** Small helpers: dates (DST-safe day maths), formatting, geo, colours. */
final class NearlyUtil {
    static final long DAY = 86400000L;
    static final TimeZone UTC = TimeZone.getTimeZone("UTC");

    static void put(JSONObject o, String k, Object v) {
        try { o.put(k, v); } catch (JSONException ignored) { }
    }

    static long now() { return System.currentTimeMillis(); }

    static float dp(Context c, float v) { return v * c.getResources().getDisplayMetrics().density; }

    static String ago(long ts) {
        if (ts <= 0) return "never";
        long d = now() - ts;
        if (d < 0) d = 0;
        long m = d / 60000L;
        if (m < 1) return "just now";
        if (m < 60) return m + " min ago";
        long h = m / 60;
        if (h < 24) return h + " h ago";
        long dd = h / 24;
        if (dd < 30) return dd + (dd == 1 ? " day ago" : " days ago");
        return (dd / 30) + " mo ago";
    }

    // ---- days (epoch-day numbers in UTC so DST never shifts a count) ----
    static long epochDay(int y, int m0, int d) {
        GregorianCalendar g = new GregorianCalendar(UTC);
        g.clear();
        g.set(y, m0, d); // lenient: Feb 29 in a common year becomes Mar 1
        return g.getTimeInMillis() / DAY;
    }

    static long today() {
        Calendar c = Calendar.getInstance();
        return epochDay(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
    }

    static int[] ymd(long ed) {
        GregorianCalendar g = new GregorianCalendar(UTC);
        g.setTimeInMillis(ed * DAY);
        return new int[]{g.get(Calendar.YEAR), g.get(Calendar.MONTH), g.get(Calendar.DAY_OF_MONTH)};
    }

    /** "YYYY-MM-DD" -> epoch day, or -1. */
    static long parseDay(String s) {
        if (s == null) return -1;
        try {
            String[] p = s.trim().split("-");
            if (p.length < 3) return -1;
            String dd = p[2].length() > 2 ? p[2].substring(0, 2) : p[2];
            return epochDay(Integer.parseInt(p[0]), Integer.parseInt(p[1]) - 1, Integer.parseInt(dd));
        } catch (Exception e) {
            return -1;
        }
    }

    /** Next occurrence (today included) of month/day. */
    static long nextMD(int m0, int d, long today) {
        int y = ymd(today)[0];
        long c1 = epochDay(y, m0, d);
        return c1 >= today ? c1 : epochDay(y + 1, m0, d);
    }

    static long addMonths(long from, int n) {
        int[] a = ymd(from);
        GregorianCalendar g = new GregorianCalendar(UTC);
        g.clear();
        g.set(a[0], a[1] + n, 1);
        int max = g.getActualMaximum(Calendar.DAY_OF_MONTH);
        g.set(Calendar.DAY_OF_MONTH, Math.min(a[2], max));
        return g.getTimeInMillis() / DAY;
    }

    /** {years, months, days} between two epoch days. */
    static int[] ymdDiff(long from, long to) {
        int[] a = ymd(from), b = ymd(to);
        int y = b[0] - a[0], m = b[1] - a[1], d = b[2] - a[2];
        if (d < 0) {
            m--;
            GregorianCalendar g = new GregorianCalendar(UTC);
            g.clear();
            g.set(b[0], b[1], 1);
            g.add(Calendar.DAY_OF_MONTH, -1);
            d += g.getActualMaximum(Calendar.DAY_OF_MONTH);
        }
        if (m < 0) { y--; m += 12; }
        return new int[]{y, m, d};
    }

    static String fmt(long ed, String pattern) {
        SimpleDateFormat f = new SimpleDateFormat(pattern, Locale.getDefault());
        f.setTimeZone(UTC);
        return f.format(new Date(ed * DAY));
    }

    static String fmtTs(long ts, String pattern) {
        return new SimpleDateFormat(pattern, Locale.getDefault()).format(new Date(ts));
    }

    static String todayKey() { return fmtTs(now(), "yyyy-MM-dd"); }

    static String ordinal(int n) {
        int m100 = n % 100;
        if (m100 >= 11 && m100 <= 13) return n + "th";
        switch (n % 10) {
            case 1: return n + "st";
            case 2: return n + "nd";
            case 3: return n + "rd";
            default: return n + "th";
        }
    }

    static String countdown(long days) {
        if (days <= 0) return "Today! 🎉";
        if (days == 1) return "Tomorrow";
        return days + " days";
    }

    // ---- geo ----
    static double km(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371.0088;
        double dLat = Math.toRadians(lat2 - lat1), dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * r * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    static String dist(double km, boolean mi) {
        double v = mi ? km * 0.621371 : km;
        if (v < 1) {
            int small = (int) Math.round(v * (mi ? 5280 : 1000));
            return small + (mi ? " ft" : " m");
        }
        if (v < 10) return String.format(Locale.US, "%.1f", v) + (mi ? " mi" : " km");
        return Math.round(v) + (mi ? " mi" : " km");
    }

    static String duration(double hours) {
        int mins = (int) Math.round(hours * 60);
        if (mins < 1) return "<1 min";
        if (mins < 60) return mins + " min";
        int h = mins / 60, m = mins % 60;
        return m == 0 ? h + " h" : h + " h " + m + " min";
    }

    // ---- colours / text ----
    static int color(String s, int def) {
        if (s == null || s.isEmpty()) return def;
        try {
            String t = s.trim();
            if (!t.startsWith("#")) t = "#" + t;
            return Color.parseColor(t);
        } catch (Exception e) {
            return def;
        }
    }

    static int mix(int a, int b, float t) {
        return Color.argb(
                (int) (Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * t),
                (int) (Color.red(a) + (Color.red(b) - Color.red(a)) * t),
                (int) (Color.green(a) + (Color.green(b) - Color.green(a)) * t),
                (int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
    }

    static int withAlpha(int color, int a) {
        return (color & 0x00FFFFFF) | (Math.max(0, Math.min(255, a)) << 24);
    }

    /** First n lines of s. */
    static String lines(String s, int n) {
        if (s == null) return "";
        String[] p = s.split("\n");
        if (p.length <= n) return s;
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < n; i++) { if (i > 0) b.append('\n'); b.append(p[i]); }
        return b.toString();
    }

    static String clip(String s, int n) {
        if (s == null) return "";
        return s.length() <= n ? s : s.substring(0, Math.max(0, n - 1)) + "…";
    }
}
