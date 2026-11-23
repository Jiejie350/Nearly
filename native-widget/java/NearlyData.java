package com.benjamin.nearly; // CHANGE to your app's package

import android.content.Context;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * Read-only view of the cached couple node.
 *
 *  meta:   { start:"YYYY-MM-DD", bday:{<slot>:"YYYY-MM-DD"|"MM-DD"} }
 *  <slot>: { name, hearts, hl, kisses, kl, hugs, gl, mood, moodTs, moodMsg, moodHist:{date:mood},
 *            activity, status, msg, msgTs, batt, charging, online, active, locShare, loc:{lat,lng,ts,name} }
 *  events: { id:{ title, date, type, yearly, emoji } }
 *  notes:  { id:{ text, by, ts, kind, to, color, done } }
 *  tasks:  { id:{ title, done, by, ts } }
 *  candle: { lit:{<slot>:ts|0} }
 *  game:   { ttt, answers, nhie, challenge, invite }
 *  notify: { <slot>:{ title, body, ts } }
 *  photoIndex: { id:{ ts, by, kind, cap, fav, d } }
 */
final class NearlyData {
    static final class Ev {
        long day, days;
        String id = "", title = "", emoji = "📅", type = "custom", date = "";
        int n;
    }

    final Context c;
    final JSONObject root;
    final String slot, pslot;

    NearlyData(Context c) {
        this.c = c;
        this.root = NearlyStore.cache(c);
        this.slot = NearlyStore.slot(c);
        this.pslot = NearlyStore.pslot(c);
    }

    JSONObject o(String k) {
        JSONObject j = root.optJSONObject(k);
        return j == null ? new JSONObject() : j;
    }

    JSONObject me() { return o(slot); }
    JSONObject partner() { return o(pslot); }
    JSONObject meta() { return o("meta"); }

    String myName() {
        String n = me().optString("name", "");
        if (n.isEmpty()) n = NearlyStore.sp(c).getString("myName", "");
        return n.isEmpty() ? "You" : n;
    }

    String pName() {
        String n = partner().optString("name", "");
        if (n.isEmpty()) n = NearlyStore.sp(c).getString("pName", "");
        return n.isEmpty() ? "Partner" : n;
    }

    String nameOf(String s) { return s.equals(slot) ? myName() : pName(); }

    /** Relationship start (epoch day) or -1. */
    long start() {
        JSONObject m = meta();
        long s = NearlyUtil.parseDay(m.optString("start", ""));
        if (s < 0) s = NearlyUtil.parseDay(m.optString("anniversary", ""));
        return s;
    }

    /** Partner / own coordinates {lat,lng} or null. Own falls back to the last local fix. */
    double[] myLoc() {
        JSONObject l = me().optJSONObject("loc");
        if (l != null && l.has("lat")) return new double[]{l.optDouble("lat"), l.optDouble("lng")};
        android.content.SharedPreferences p = NearlyStore.sp(c);
        if (p.contains("myloc_lat")) {
            return new double[]{Double.longBitsToDouble(p.getLong("myloc_lat", 0)), Double.longBitsToDouble(p.getLong("myloc_lng", 0))};
        }
        return null;
    }

    boolean partnerOnline() {
        long o = partner().optLong("online", 0);
        return o > 0 && NearlyUtil.now() - o < 180000L;
    }

    // ---------------- events / countdowns ----------------
    private Ev ev(String type, String emoji, String title, long day, long today) {
        Ev e = new Ev();
        e.type = type;
        e.emoji = emoji;
        e.title = title;
        e.day = day;
        e.days = day - today;
        e.date = NearlyUtil.fmt(day, "MMM d, yyyy");
        return e;
    }

    /** Upcoming occurrences sorted by date. filter = event type or null for all. */
    List<Ev> upcoming(String filter) {
        long t = NearlyUtil.today();
        ArrayList<Ev> l = new ArrayList<>();

        long st = start();
        if (st >= 0 && (filter == null || filter.equals("anniversary"))) {
            int[] a = NearlyUtil.ymd(st);
            long nx = NearlyUtil.nextMD(a[1], a[2], t);
            Ev e = ev("anniversary", "💞", "Anniversary", nx, t);
            e.n = NearlyUtil.ymd(nx)[0] - a[0];
            if (e.n > 0) { e.title = NearlyUtil.ordinal(e.n) + " anniversary"; l.add(e); }
        }

        if (filter == null || filter.equals("birthday")) {
            JSONObject b = meta().optJSONObject("bday");
            if (b != null) {
                Iterator<String> it = b.keys();
                while (it.hasNext()) {
                    String s = it.next();
                    String v = b.optString(s, "");
                    String[] p = v.split("-");
                    try {
                        int m0, d, year = 0;
                        if (p.length >= 3) { year = Integer.parseInt(p[0]); m0 = Integer.parseInt(p[1]) - 1; d = Integer.parseInt(p[2]); }
                        else if (p.length == 2) { m0 = Integer.parseInt(p[0]) - 1; d = Integer.parseInt(p[1]); }
                        else continue;
                        long nx = NearlyUtil.nextMD(m0, d, t);
                        Ev e = ev("birthday", "🎂", nameOf(s) + "'s birthday", nx, t);
                        if (year > 0) e.n = NearlyUtil.ymd(nx)[0] - year;
                        l.add(e);
                    } catch (Exception ignored) { }
                }
            }
        }

        JSONObject evs = o("events");
        Iterator<String> it = evs.keys();
        while (it.hasNext()) {
            String id = it.next();
            JSONObject j = evs.optJSONObject(id);
            if (j == null) continue;
            Ev e = fromEvent(id, j, t);
            if (e == null) continue;
            if (filter == null || filter.equals(e.type)) l.add(e);
        }

        Collections.sort(l, new Comparator<Ev>() {
            public int compare(Ev a, Ev b) { return Long.compare(a.day, b.day); }
        });
        return l;
    }

    Ev eventById(String id) {
        JSONObject j = o("events").optJSONObject(id);
        return j == null ? null : fromEvent(id, j, NearlyUtil.today());
    }

    private Ev fromEvent(String id, JSONObject j, long t) {
        long d = NearlyUtil.parseDay(j.optString("date", ""));
        if (d < 0) return null;
        boolean yearly = j.optBoolean("yearly", false);
        if (yearly) {
            int[] a = NearlyUtil.ymd(d);
            d = NearlyUtil.nextMD(a[1], a[2], t);
        } else if (d < t) {
            return null;
        }
        String type = j.optString("type", "custom");
        Ev e = ev(type, j.optString("emoji", defaultEmoji(type)), j.optString("title", "Event"), d, t);
        e.id = id;
        return e;
    }

    static String defaultEmoji(String type) {
        switch (type) {
            case "meetup": return "🤝";
            case "vacation": return "🏝️";
            case "birthday": return "🎂";
            case "anniversary": return "💞";
            case "special": return "✨";
            case "milestone": return "🏁";
            default: return "📅";
        }
    }

    // ---------------- notes / tasks ----------------
    /** Notes newest-first; kind == null for all. Each object gets an "id" field. */
    List<JSONObject> notes(String kind) {
        ArrayList<JSONObject> l = new ArrayList<>();
        JSONObject n = o("notes");
        Iterator<String> it = n.keys();
        while (it.hasNext()) {
            String id = it.next();
            JSONObject j = n.optJSONObject(id);
            if (j == null) continue;
            if (kind != null && !kind.equals(j.optString("kind", "love"))) continue;
            NearlyUtil.put(j, "id", id);
            l.add(j);
        }
        Collections.sort(l, new Comparator<JSONObject>() {
            public int compare(JSONObject a, JSONObject b) { return Long.compare(b.optLong("ts"), a.optLong("ts")); }
        });
        return l;
    }

    /** Chat messages (text + handwriting) newest-first. Each object gets an "id" field. */
    List<JSONObject> chat() { return notes("chat"); }

    /** Open tasks oldest-first. */
    List<JSONObject> openTasks() {
        ArrayList<JSONObject> l = new ArrayList<>();
        JSONObject n = o("tasks");
        Iterator<String> it = n.keys();
        while (it.hasNext()) {
            String id = it.next();
            JSONObject j = n.optJSONObject(id);
            if (j == null || j.optBoolean("done", false)) continue;
            NearlyUtil.put(j, "id", id);
            l.add(j);
        }
        Collections.sort(l, new Comparator<JSONObject>() {
            public int compare(JSONObject a, JSONObject b) { return Long.compare(a.optLong("ts"), b.optLong("ts")); }
        });
        return l;
    }

    // ---------------- candle ----------------
    /** 0..1 flame strength for a lit-timestamp: full for 1 h, fades to 0 over the next 2 h. */
    static float flame(long litTs) {
        if (litTs <= 0) return 0f;
        float ageMin = (NearlyUtil.now() - litTs) / 60000f;
        if (ageMin <= 60f) return 1f;
        if (ageMin >= 180f) return 0f;
        return 1f - 0.85f * ((ageMin - 60f) / 120f);
    }

    float myFlame() { return flame(o("candle").optJSONObject("lit") == null ? 0 : o("candle").optJSONObject("lit").optLong(slot, 0)); }
    float partnerFlame() { return flame(o("candle").optJSONObject("lit") == null ? 0 : o("candle").optJSONObject("lit").optLong(pslot, 0)); }
}
