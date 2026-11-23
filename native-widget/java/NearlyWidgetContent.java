package com.benjamin.nearly; // CHANGE to your app's package

import android.graphics.Bitmap;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;


/** What each widget type shows, per mode. Pure data -> NearlyRenderer.Ui; no Android views here. */
final class NearlyWidgetContent {

    private static NearlyRenderer.Act a(String label, String action, String arg) { return new NearlyRenderer.Act(label, action, arg); }
    private static String w(NearlyRenderer.Env e, String k, String d) { return NearlyStore.w(e.c, e.id, k, d); }
    private static int wi(NearlyRenderer.Env e, String k, int d) { return NearlyStore.wi(e.c, e.id, k, d); }
    private static boolean wb(NearlyRenderer.Env e, String k, boolean d) { return NearlyStore.wb(e.c, e.id, k, d); }

    static NearlyRenderer.Ui build(NearlyRenderer.Env e) {
        switch (e.type) {
            case "photo": return photo(e);
            case "love": return love(e);
            case "relationship": return relationship(e);
            case "status": return status(e);
            case "location": return location(e);
            case "mood": return mood(e);
            case "countdown": return countdown(e);
            case "msg": return msg(e);
            case "interactive": return interactive(e);
            case "game": return game(e);
            case "candle": return candle(e);
            default: return love(e);
        }
    }

    // ================================================================= 1. PHOTOS
    private static NearlyRenderer.Ui photo(NearlyRenderer.Env e) {
        NearlyRenderer.Ui u = new NearlyRenderer.Ui();
        u.layout = "photo";
        u.page = "photos";
        String mode = w(e, "mode", "gallery");
        List<JSONObject> list = NearlyPhotos.pick(e.d, mode, e.c, w(e, "photoSel", ""));
        JSONObject sel = null;
        if (!list.isEmpty()) {
            int n = list.size();
            int rot = wi(e, "rot", 60);
            int off = wi(e, "photoOff", 0);
            long tick = rot <= 0 ? 0 : NearlyUtil.now() / (rot * 60000L);
            int idx = (int) (((tick + off) % n + n) % n);
            sel = list.get(idx);
            u.canNext = n > 1 && (mode.equals("gallery") || mode.equals("favorites"));
        }
        Bitmap bmp = sel == null ? null : NearlyPhotos.load(e.c, sel.optString("id"), e.bw, e.bh);
        String cap = sel == null ? "" : sel.optString("cap", "");
        String date = "";
        if (sel != null) {
            long d = NearlyUtil.parseDay(sel.optString("d", ""));
            date = d >= 0 ? NearlyUtil.fmt(d, "d MMM yyyy") : NearlyUtil.fmtTs(sel.optLong("ts"), "d MMM yyyy");
        }
        u.art = NearlyArt.photoCard(e.st, bmp, cap, date, w(e, "frame", "rounded"),
                wb(e, "showCap", true), wb(e, "showDate", false), e.bw, e.bh);
        return u;
    }

    // ================================================================= 2. LOVE
    private static NearlyRenderer.Ui love(NearlyRenderer.Env e) {
        NearlyRenderer.Ui u = new NearlyRenderer.Ui();
        NearlyData d = e.d;
        JSONObject me = d.me(), p = d.partner();
        String pn = d.pName();
        String mode = w(e, "mode", "iloveyou");
        int mh = me.optInt("hearts", 0), ph = p.optInt("hearts", 0);
        int mk = me.optInt("kisses", 0), pk = p.optInt("kisses", 0);
        int mg = me.optInt("hugs", 0), pg = p.optInt("hugs", 0);

        switch (mode) {
            case "random": {
                int r = wi(e, "rnd", -1);
                if (r < 0) r = (int) ((e.id * 31L + NearlyUtil.today()) % 100000);
                u.label = "Love note";
                u.big = NearlyContent.pick(NearlyContent.LOVE, r);
                u.bigLines = 4;
                u.acts.add(a("🔀", "rnd", ""));
                u.acts.add(a("💗", "heart", ""));
                break;
            }
            case "daily":
                u.label = "Today's message";
                u.big = NearlyContent.pick(NearlyContent.LOVE, NearlyUtil.today());
                u.bigLines = 4;
                u.acts.add(a("💗", "heart", ""));
                u.acts.add(a("💋", "kiss", ""));
                break;
            case "partner": {
                String msg = p.optString("msg", "");
                u.label = "From " + pn;
                u.big = msg.isEmpty() ? "No message yet — ask " + pn + " to write one 💌" : msg;
                u.bigLines = 4;
                u.sub = msg.isEmpty() ? "" : NearlyUtil.ago(p.optLong("msgTs", 0));
                u.acts.add(a("✏️", "note", "love"));
                u.acts.add(a("💗", "heart", ""));
                u.page = "messages";
                break;
            }
            case "kisses":
                u.label = "Kisses";
                u.big = "💋 " + (mk + pk);
                u.sub = "You " + mk + " · " + pn + " " + pk;
                u.extra = pk > 0 ? pn + " last kissed you " + NearlyUtil.ago(p.optLong("kl", 0)) : "Send the first one!";
                u.acts.add(a("💋", "kiss", ""));
                break;
            case "hugs":
                u.label = "Hugs";
                u.big = "🤗 " + (mg + pg);
                u.sub = "You " + mg + " · " + pn + " " + pg;
                u.extra = pg > 0 ? pn + " last hugged you " + NearlyUtil.ago(p.optLong("gl", 0)) : "Send the first one!";
                u.acts.add(a("🤗", "hug", ""));
                break;
            case "affection":
                u.label = "Send love";
                u.big = "💗 " + mh + "  💋 " + mk + "  🤗 " + mg;
                u.sub = "You've sent · " + pn + ": 💗 " + ph + " 💋 " + pk + " 🤗 " + pg;
                u.extra = "One tap sends it to " + pn + " instantly.";
                u.acts.add(a("💗", "heart", ""));
                u.acts.add(a("💋", "kiss", ""));
                u.acts.add(a("🤗", "hug", ""));
                u.acts.add(a("✏️", "note", "love"));
                break;
            default: // iloveyou
                u.label = "I love you";
                u.big = "I ❤ you";
                u.sub = ph > 0 ? pn + " sent " + ph + " heart" + (ph == 1 ? "" : "s") : "Tap to send a heart";
                u.extra = p.optLong("hl", 0) > 0 ? pn + "'s last heart: " + NearlyUtil.ago(p.optLong("hl", 0)) : "";
                u.acts.add(a("💗", "heart", ""));
                break;
        }
        return u;
    }

    // ================================================================= 3. RELATIONSHIP
    private static final int[] MILESTONES = {100, 200, 300, 365, 500, 730, 1000, 1500, 2000, 2500, 3000, 3650, 5000, 7300, 10000};

    private static final class TL { long day; String text; }

    private static NearlyRenderer.Ui relationship(NearlyRenderer.Env e) {
        NearlyRenderer.Ui u = new NearlyRenderer.Ui();
        u.page = "timeline";
        NearlyData d = e.d;
        String mode = w(e, "mode", "days");
        long st = d.start(), t = NearlyUtil.today();

        if (mode.equals("reminders")) return reminders(e, u);

        if (st < 0) {
            u.label = "Together";
            u.big = "—";
            u.sub = "Set your start date in the app";
            return u;
        }
        long days = t - st;
        if (days < 0) {
            u.label = "Together";
            u.big = NearlyUtil.countdown(-days);
            u.sub = "until your start date";
            return u;
        }
        int[] diff = NearlyUtil.ymdDiff(st, t);

        switch (mode) {
            case "total": {
                u.label = "Total time together";
                u.big = days + " days";
                StringBuilder b = new StringBuilder();
                if (diff[0] > 0) b.append(diff[0]).append(diff[0] == 1 ? " year" : " years");
                if (diff[1] > 0) b.append(b.length() > 0 ? ", " : "").append(diff[1]).append(diff[1] == 1 ? " month" : " months");
                b.append(b.length() > 0 ? ", " : "").append(diff[2]).append(diff[2] == 1 ? " day" : " days");
                u.sub = b.toString();
                u.extra = (days / 7) + " weeks · " + (days * 24) + " hours\nSince " + NearlyUtil.fmt(st, "MMM d, yyyy");
                break;
            }
            case "anniv": {
                List<NearlyData.Ev> l = d.upcoming("anniversary");
                u.label = "Next anniversary";
                if (l.isEmpty()) {
                    u.big = (365 - days) + " days";
                    u.sub = "until your 1st anniversary";
                } else {
                    NearlyData.Ev ev = l.get(0);
                    u.big = NearlyUtil.countdown(ev.days);
                    u.sub = "💞 " + ev.title;
                    u.extra = ev.date + "\nTogether " + days + " days";
                }
                break;
            }
            case "monthly": {
                int months = diff[0] * 12 + diff[1];
                long nextM = NearlyUtil.addMonths(st, months + 1);
                u.label = "Milestones";
                if (months > 0 && NearlyUtil.addMonths(st, months) == t) {
                    u.big = "🎉 Month " + months;
                    u.sub = "Happy monthsary!";
                } else {
                    u.big = NearlyUtil.countdown(nextM - t);
                    u.sub = "Month " + (months + 1) + " · " + NearlyUtil.fmt(nextM, "MMM d");
                }
                StringBuilder x = new StringBuilder();
                for (int ms : MILESTONES) {
                    if (ms == days) { x.append("🎉 Day ").append(ms).append(" is today!"); break; }
                    if (ms > days) {
                        x.append("Day ").append(ms).append(" in ").append(ms - days).append(" days\n")
                                .append(NearlyUtil.fmt(st + ms, "MMM d, yyyy"));
                        break;
                    }
                }
                u.extra = x.toString();
                break;
            }
            case "timeline": {
                u.label = "Our timeline";
                u.big = days + " days";
                u.sub = "💗 Started " + NearlyUtil.fmt(st, "MMM d, yyyy");
                ArrayList<TL> all = new ArrayList<>();
                for (int ms : MILESTONES) {
                    TL x = new TL();
                    x.day = st + ms;
                    x.text = "Day " + ms;
                    all.add(x);
                }
                for (NearlyData.Ev ev : d.upcoming(null)) {
                    TL x = new TL();
                    x.day = ev.day;
                    x.text = ev.emoji + " " + ev.title;
                    all.add(x);
                }
                Collections.sort(all, new Comparator<TL>() { public int compare(TL p, TL q) { return Long.compare(p.day, q.day); } });
                ArrayList<TL> past = new ArrayList<>(), fut = new ArrayList<>();
                for (TL x : all) { if (x.day <= t) past.add(x); else fut.add(x); }
                StringBuilder b = new StringBuilder();
                for (int i = Math.max(0, past.size() - 2); i < past.size(); i++)
                    b.append("✓ ").append(NearlyUtil.fmt(past.get(i).day, "MMM d, yyyy")).append(" · ").append(past.get(i).text).append("\n");
                for (int i = 0; i < Math.min(3, fut.size()); i++)
                    b.append("→ ").append(NearlyUtil.fmt(fut.get(i).day, "MMM d, yyyy")).append(" · ").append(fut.get(i).text)
                            .append(" (").append(fut.get(i).day - t).append(" d)\n");
                u.extra = b.toString().trim();
                break;
            }
            default: // days
                u.label = "Days together";
                u.big = String.valueOf(days);
                u.sub = "days 💗";
                u.extra = "Since " + NearlyUtil.fmt(st, "MMM d, yyyy");
                break;
        }
        return u;
    }

    private static NearlyRenderer.Ui reminders(NearlyRenderer.Env e, NearlyRenderer.Ui u) {
        List<NearlyData.Ev> l = e.d.upcoming(null);
        StringBuilder b = new StringBuilder();
        int n = 0;
        for (NearlyData.Ev ev : l) {
            if (ev.days > 45) break;
            if (n++ >= 6) break;
            b.append("🔔 ").append(ev.title).append(" — ").append(NearlyUtil.countdown(ev.days)).append("\n");
        }
        u.label = "Special dates";
        u.big = n == 0 ? "Nothing soon" : n + " coming up";
        u.sub = n == 0 ? "No dates in the next 45 days" : "in the next 45 days";
        u.extra = b.toString().trim();
        u.page = "events";
        return u;
    }

    // ================================================================= 4. PARTNER STATUS
    private static NearlyRenderer.Ui status(NearlyRenderer.Env e) {
        NearlyRenderer.Ui u = new NearlyRenderer.Ui();
        u.page = "home";
        NearlyData d = e.d;
        JSONObject p = d.partner();
        String pn = d.pName();
        String mode = w(e, "mode", "full");
        String show = "," + w(e, "show", "online,last,mood,activity,battery,msg") + ",";

        boolean online = d.partnerOnline();
        String onlineS = online ? "🟢 Online" : "⚪ Offline";
        String lastS = online ? "Active now" : "Last active " + NearlyUtil.ago(p.optLong("active", 0));
        String mood = p.optString("mood", "");
        String moodS = mood.isEmpty() ? "" : NearlyContent.moodEmoji(mood) + " " + NearlyContent.moodLabel(mood);
        String activity = p.optString("activity", "");
        String msg = p.optString("status", "");
        int batt = p.has("batt") ? p.optInt("batt", -1) : -1;
        String battS = batt >= 0 ? "🔋 " + batt + "%" + (p.optBoolean("charging", false) ? " ⚡" : "") : "🔋 not shared";

        u.label = pn;
        switch (mode) {
            case "online": u.big = onlineS; u.sub = lastS; break;
            case "last": u.big = online ? "Now" : NearlyUtil.ago(p.optLong("active", 0)); u.sub = "last active"; break;
            case "mood": u.big = mood.isEmpty() ? "😶" : NearlyContent.moodEmoji(mood); u.sub = NearlyContent.moodLabel(mood); u.extra = p.optString("moodMsg", ""); break;
            case "activity": u.big = activity.isEmpty() ? "—" : activity; u.bigLines = 3; u.sub = "right now"; break;
            case "battery": u.big = battS; u.sub = batt >= 0 && p.optBoolean("charging", false) ? "charging" : ""; break;
            case "message": u.big = msg.isEmpty() ? "No status message" : msg; u.bigLines = 4; u.sub = "status"; break;
            default: {
                u.big = show.contains(",online,") ? onlineS : (show.contains(",mood,") && !moodS.isEmpty() ? moodS : pn);
                StringBuilder s = new StringBuilder();
                if (show.contains(",mood,") && !moodS.isEmpty() && !u.big.equals(moodS)) s.append(moodS);
                if (show.contains(",activity,") && !activity.isEmpty()) s.append(s.length() > 0 ? " · " : "").append(activity);
                u.sub = s.toString();
                StringBuilder x = new StringBuilder();
                if (show.contains(",last,")) x.append(lastS).append("\n");
                if (show.contains(",battery,")) x.append(battS).append("\n");
                if (show.contains(",msg,") && !msg.isEmpty()) x.append("“").append(msg).append("”");
                u.extra = x.toString().trim();
                break;
            }
        }
        u.acts.add(a("💗", "heart", ""));
        u.acts.add(a("🔄", "refresh", ""));
        return u;
    }

    // ================================================================= 5. LOCATION
    private static NearlyRenderer.Ui location(NearlyRenderer.Env e) {
        NearlyRenderer.Ui u = new NearlyRenderer.Ui();
        u.page = "location";
        NearlyData d = e.d;
        JSONObject p = d.partner();
        String pn = d.pName();
        String mode = w(e, "mode", "partner");
        boolean mi = w(e, "units", "km").equals("mi");

        JSONObject pl = p.optJSONObject("loc");
        boolean pShare = p.optBoolean("locShare", false) && pl != null && pl.has("lat");
        boolean iShare = NearlyStore.priv(e.c, "shareLocation", false);
        double[] my = d.myLoc();
        double km = -1;
        if (pShare && my != null) km = NearlyUtil.km(my[0], my[1], pl.optDouble("lat"), pl.optDouble("lng"));
        long ts = pShare ? pl.optLong("ts", 0) : 0;
        String updated = pShare ? "Updated " + NearlyUtil.ago(ts) : "";
        String place = "";
        if (pShare) {
            place = pl.optString("name", "");
            if (place.isEmpty()) place = String.format(java.util.Locale.US, "%.2f, %.2f", pl.optDouble("lat"), pl.optDouble("lng"));
        }

        u.label = mode.equals("sharing") ? "Location sharing" : pn + "'s location";
        switch (mode) {
            case "distance":
                u.label = "Distance";
                if (!pShare) { u.big = "—"; u.sub = pn + " isn't sharing location"; }
                else if (km < 0) { u.big = "—"; u.sub = "Enable your location to measure"; }
                else { u.big = NearlyUtil.dist(km, mi); u.sub = "between you and " + pn; u.extra = updated; }
                break;
            case "travel":
                u.label = "Travel estimate";
                if (!pShare || km < 0) { u.big = "—"; u.sub = pShare ? "Enable your location to estimate" : pn + " isn't sharing location"; }
                else {
                    double road = km * 1.3;     // rough road factor, no routing service involved
                    u.big = "≈ " + NearlyUtil.dist(road, mi);
                    u.sub = "~" + NearlyUtil.duration(road / 60.0) + " by car";
                    u.extra = "Straight line " + NearlyUtil.dist(km, mi) + "\nEstimate only (1.3× road factor, 60 km/h)";
                }
                break;
            case "updated":
                u.label = "Last update";
                u.big = pShare ? NearlyUtil.ago(ts) : "—";
                u.sub = pShare ? place : pn + " isn't sharing location";
                break;
            case "sharing":
                u.big = "You: " + (iShare ? "🟢 sharing" : "⚪ paused");
                u.sub = pn + ": " + (pShare ? "🟢 sharing" : "⚪ not sharing");
                u.extra = "Precision: " + precisionLabel(NearlyStore.privStr(e.c, "locationPrecision", "approx"));
                break;
            default: // partner
                if (!pShare) { u.big = "—"; u.sub = pn + " isn't sharing location"; u.extra = "Location sharing is off or hasn't updated yet."; }
                else {
                    u.big = place;
                    u.sub = km >= 0 ? NearlyUtil.dist(km, mi) + " away" : "";
                    u.extra = updated;
                }
                break;
        }
        u.acts.add(a(iShare ? "📍 On" : "📍 Off", "loc", ""));
        u.acts.add(a("🔄", "refresh", ""));
        return u;
    }

    private static String precisionLabel(String p) {
        switch (p) {
            case "exact": return "exact";
            case "city": return "city-level (~10 km)";
            default: return "approximate (~1 km)";
        }
    }

    // ================================================================= 6. MOOD
    private static String hist(JSONObject o, int days) {
        JSONObject h = o.optJSONObject("moodHist");
        StringBuilder b = new StringBuilder();
        long t = NearlyUtil.today();
        for (int i = days - 1; i >= 0; i--) {
            String m = h == null ? "" : h.optString(NearlyUtil.fmt(t - i, "yyyy-MM-dd"), "");
            b.append(m.isEmpty() ? "⚪" : NearlyContent.moodEmoji(m));
        }
        return b.toString();
    }

    private static NearlyRenderer.Ui mood(NearlyRenderer.Env e) {
        NearlyRenderer.Ui u = new NearlyRenderer.Ui();
        u.page = "mood";
        NearlyData d = e.d;
        JSONObject me = d.me(), p = d.partner();
        String pn = d.pName();
        String mode = w(e, "mode", "current");
        String mm = me.optString("mood", ""), pm = p.optString("mood", "");
        String mEmoji = mm.isEmpty() ? "😶" : NearlyContent.moodEmoji(mm);
        String pEmoji = pm.isEmpty() ? "😶" : NearlyContent.moodEmoji(pm);

        switch (mode) {
            case "select":
                u.label = "How are you today?";
                u.big = mEmoji + " " + NearlyContent.moodLabel(mm);
                u.sub = pn + ": " + pEmoji + " " + NearlyContent.moodLabel(pm);
                u.acts.add(a("😊", "setmood", "happy"));
                u.acts.add(a("🥰", "setmood", "loved"));
                u.acts.add(a("😴", "setmood", "tired"));
                u.acts.add(a("⋯", "moodpick", ""));
                break;
            case "history":
                u.label = "Mood history";
                u.big = hist(me, 7);
                u.sub = "You · last 7 days";
                u.extra = pn + "\n" + hist(p, 7);
                u.fixedBigSp = Math.min(22f, (e.wd - 30) / 8.6f);
                u.acts.add(a("⋯", "moodpick", ""));
                break;
            case "shared":
                u.label = "Moods";
                u.big = mEmoji + "  " + pEmoji;
                u.sub = "You · " + pn;
                StringBuilder x = new StringBuilder();
                if (!me.optString("moodMsg", "").isEmpty()) x.append("You: ").append(me.optString("moodMsg")).append("\n");
                if (!p.optString("moodMsg", "").isEmpty()) x.append(pn).append(": ").append(p.optString("moodMsg"));
                u.extra = x.toString().trim();
                u.acts.add(a("⋯", "moodpick", ""));
                break;
            case "calendar":
                return moodCalendar(e, u, me, p, pn);
            case "message":
                u.label = "Mood message";
                u.big = p.optString("moodMsg", "").isEmpty() ? "No message yet" : p.optString("moodMsg");
                u.bigLines = 4;
                u.sub = pEmoji + " " + NearlyContent.moodLabel(pm) + " · " + pn;
                u.acts.add(a("✏️", "moodpick", ""));
                break;
            default: // current
                u.label = pn + "'s mood";
                u.big = pEmoji;
                u.sub = NearlyContent.moodLabel(pm);
                StringBuilder y = new StringBuilder();
                if (!p.optString("moodMsg", "").isEmpty()) y.append("“").append(p.optString("moodMsg")).append("”\n");
                if (p.optLong("moodTs", 0) > 0) y.append("Updated ").append(NearlyUtil.ago(p.optLong("moodTs")));
                u.extra = y.toString().trim();
                u.acts.add(a("💗", "heart", ""));
                u.acts.add(a("⋯", "moodpick", ""));
                break;
        }
        return u;
    }

    private static NearlyRenderer.Ui moodCalendar(NearlyRenderer.Env e, NearlyRenderer.Ui u, JSONObject me, JSONObject p, String pn) {
        boolean mine = w(e, "calWho", "me").equals("me");
        JSONObject who = mine ? me : p;
        JSONObject h = who.optJSONObject("moodHist");
        long t = NearlyUtil.today();
        int[] ymd = NearlyUtil.ymd(t);
        long first = NearlyUtil.epochDay(ymd[0], ymd[1], 1);
        int dim = (int) (NearlyUtil.epochDay(ymd[0], ymd[1] + 1, 1) - first);
        int lead = (int) ((first + 3) % 7);                 // Monday-first
        int rows = (lead + dim + 6) / 7;
        int curRow = (lead + ymd[2] - 1) / 7;
        int show = e.sz.equals("large") ? rows : e.sz.equals("medium") ? Math.min(rows, 3) : Math.min(rows, 2);
        int startRow = Math.max(0, Math.min(curRow - (show - 1), rows - show));
        StringBuilder b = new StringBuilder();
        for (int r = startRow; r < startRow + show; r++) {
            if (r > startRow) b.append('\n');
            for (int c = 0; c < 7; c++) {
                int day = r * 7 + c - lead + 1;
                if (day < 1 || day > dim) { b.append("▫️"); continue; }
                String m = h == null ? "" : h.optString(NearlyUtil.fmt(first + day - 1, "yyyy-MM-dd"), "");
                b.append(m.isEmpty() ? "⚪" : NearlyContent.moodEmoji(m));
            }
        }
        u.label = (mine ? "Your" : pn + "'s") + " moods · " + NearlyUtil.fmt(t, "MMM yyyy");
        u.big = b.toString();
        u.fixedBigSp = Math.max(10f, Math.min(20f, (e.wd - 30) / 8.8f));
        u.acts.add(a("⋯", "moodpick", ""));
        return u;
    }

    // ================================================================= 7. COUNTDOWNS
    private static NearlyRenderer.Ui countdown(NearlyRenderer.Env e) {
        NearlyRenderer.Ui u = new NearlyRenderer.Ui();
        u.page = "events";
        NearlyData d = e.d;
        String mode = w(e, "mode", "next");
        String filter;
        switch (mode) {
            case "anniv": filter = "anniversary"; break;
            case "birthday": filter = "birthday"; break;
            case "meetup": filter = "meetup"; break;
            case "vacation": filter = "vacation"; break;
            case "special": filter = "special"; break;
            case "custom": filter = "custom"; break;
            default: filter = null; break;
        }
        String[] names = {"Next date", "Anniversary", "Birthday", "Meetup", "Vacation", "Special occasion", "Custom event"};
        String[] keys = {"next", "anniv", "birthday", "meetup", "vacation", "special", "custom"};
        u.label = "Countdown";
        for (int i = 0; i < keys.length; i++) if (keys[i].equals(mode)) u.label = names[i];

        NearlyData.Ev ev = null;
        List<NearlyData.Ev> l = d.upcoming(filter);
        if (mode.equals("custom")) {
            String sel = w(e, "evSel", "");
            if (!sel.isEmpty()) ev = d.eventById(sel);
        }
        if (ev == null && !l.isEmpty()) ev = l.get(0);

        if (ev == null) {
            u.big = "—";
            u.sub = "No upcoming " + (filter == null ? "dates" : u.label.toLowerCase());
            u.extra = "Add dates in the Nearly app";
            return u;
        }
        u.big = NearlyUtil.countdown(ev.days);
        u.sub = ev.emoji + " " + ev.title;
        StringBuilder b = new StringBuilder(ev.date);
        int n = 0;
        for (NearlyData.Ev o : d.upcoming(null)) {
            if (o.day == ev.day && o.title.equals(ev.title)) continue;
            if (n++ >= 4) break;
            b.append("\n• ").append(o.title).append(" — ").append(NearlyUtil.countdown(o.days));
        }
        u.extra = b.toString();
        return u;
    }

    // ================================================================= 8. MESSAGES (text or handwriting, no modes)
    private static NearlyRenderer.Ui msg(NearlyRenderer.Env e) {
        NearlyRenderer.Ui u = new NearlyRenderer.Ui();
        u.layout = "msg";
        u.page = "chat";
        NearlyData d = e.d;
        String pn = d.pName();
        List<JSONObject> l = d.chat();
        boolean queued = NearlyStore.queue(e.c).length() > 0;
        if (l.isEmpty()) {
            u.label = "Messages";
            u.big = NearlyStore.paired(e.c) ? "Say hi to " + pn + " 💌" : "Pair in the Nearly app to start chatting 💌";
            u.sub = "Tap ✏️ to write or draw";
        } else {
            JSONObject n = l.get(0);
            boolean mine = n.optString("by").equals(d.slot);
            u.label = mine ? "You · to " + pn : pn;
            String when = NearlyUtil.ago(n.optLong("ts")) + (mine && queued ? " · ⏳ sending" : mine ? " · sent ✓" : "");
            if (n.optString("type").equals("draw")) {
                u.art = NearlyPhotos.load(e.c, n.optString("img"), e.bw, e.bh);
                if (u.art == null) u.big = "✍️ Handwritten message";
            } else {
                u.big = n.optString("text") + (n.optBoolean("edited", false) ? "  (edited)" : "");
                u.bigLines = 4;
            }
            u.sub = when;
        }
        u.acts.add(a("✏️ Write", "compose", ""));
        u.acts.add(a("💬", "open", "chat"));
        u.acts.add(a("⚙", "config", ""));
        return u;
    }

    // ================================================================= 9. INTERACTIVE
    private static NearlyRenderer.Ui interactive(NearlyRenderer.Env e) {
        NearlyRenderer.Ui u = new NearlyRenderer.Ui();
        NearlyData d = e.d;
        JSONObject me = d.me();
        String pn = d.pName();
        String[] chosen = w(e, "acts", "heart,kiss,hug,mood").split(",");
        int n = 0;
        for (String k : chosen) {
            if (n >= 4) break;
            switch (k.trim()) {
                case "heart": u.acts.add(a("💗", "heart", "")); n++; break;
                case "kiss": u.acts.add(a("💋", "kiss", "")); n++; break;
                case "hug": u.acts.add(a("🤗", "hug", "")); n++; break;
                case "mood": u.acts.add(a("😊", "moodpick", "")); n++; break;
                case "note": u.acts.add(a("✏️", "note", "love")); n++; break;
                case "task": u.acts.add(a("✅", "task", "")); n++; break;
                case "notify": u.acts.add(a("🔔", "notify", "")); n++; break;
                case "game": u.acts.add(a("🎮", "game", "games")); n++; break;
                default: break;
            }
        }
        u.label = "Quick actions";
        u.big = "Tap to send love";
        String fb = NearlyStore.sp(e.c).getString("fb", "");
        long fbTs = NearlyStore.sp(e.c).getLong("fbTs", 0);
        u.sub = !fb.isEmpty() && NearlyUtil.now() - fbTs < 6 * 3600000L ? fb + " · " + NearlyUtil.ago(fbTs) : "to " + pn;
        StringBuilder b = new StringBuilder();
        b.append("💗 ").append(me.optInt("hearts", 0)).append("  💋 ").append(me.optInt("kisses", 0)).append("  🤗 ").append(me.optInt("hugs", 0));
        List<JSONObject> open = d.openTasks();
        if (!open.isEmpty()) b.append("\n✅ Next: ").append(NearlyUtil.clip(open.get(0).optString("title"), 40)).append(" (").append(open.size()).append(" open)");
        u.extra = b.toString();
        return u;
    }

    // ================================================================= 10. GAMES
    static String tttWinner(String b) {
        int[][] L = {{0, 1, 2}, {3, 4, 5}, {6, 7, 8}, {0, 3, 6}, {1, 4, 7}, {2, 5, 8}, {0, 4, 8}, {2, 4, 6}};
        for (int[] l : L) {
            char c = b.charAt(l[0]);
            if (c != '-' && c == b.charAt(l[1]) && c == b.charAt(l[2])) return String.valueOf(c);
        }
        return b.indexOf('-') < 0 ? "d" : "";
    }

    static char mySym(NearlyData d) { return d.slot.compareTo(d.pslot) < 0 ? 'x' : 'o'; }

    private static NearlyRenderer.Ui game(NearlyRenderer.Env e) {
        NearlyRenderer.Ui u = new NearlyRenderer.Ui();
        u.page = "games";
        NearlyData d = e.d;
        JSONObject g = d.o("game");
        String pn = d.pName();
        String mode = w(e, "mode", "ttt");
        String day = NearlyUtil.todayKey();
        long t = NearlyUtil.today();

        switch (mode) {
            case "daily": {
                String q = NearlyContent.pick(NearlyContent.QUESTIONS, t);
                JSONObject ans = g.optJSONObject("answers") == null ? null : g.optJSONObject("answers").optJSONObject(day);
                String mine = ans == null ? "" : ans.optString(d.slot, ""), theirs = ans == null ? "" : ans.optString(d.pslot, "");
                u.label = "Daily question";
                u.big = q;
                u.bigLines = 4;
                if (mine.isEmpty()) u.sub = "Tap 💬 to answer";
                else if (theirs.isEmpty()) u.sub = "Waiting for " + pn + "…";
                else u.sub = "Both answered 💞";
                if (!mine.isEmpty() && !theirs.isEmpty()) u.extra = "You: " + mine + "\n" + pn + ": " + theirs;
                u.acts.add(a("💬", "answer", q));
                break;
            }
            case "nhie": {
                JSONObject nh = g.optJSONObject("nhie");
                int n = NearlyContent.NHIE.length;
                int idx = nh != null && day.equals(nh.optString("day")) ? nh.optInt("idx", 0) : (int) (t % n);
                idx = ((idx % n) + n) % n;
                JSONObject ans = nh == null || nh.optJSONObject("ans") == null ? null : nh.optJSONObject("ans").optJSONObject(String.valueOf(idx));
                String mine = ans == null ? "" : ans.optString(d.slot, ""), theirs = ans == null ? "" : ans.optString(d.pslot, "");
                u.label = "Never have I ever";
                u.big = NearlyContent.NHIE[idx];
                u.bigLines = 4;
                if (mine.isEmpty()) u.sub = "Have you?";
                else if (theirs.isEmpty()) u.sub = "You said: " + (mine.equals("have") ? "I have 🙋" : "Never 🙅") + " · waiting for " + pn;
                else u.sub = "You: " + (mine.equals("have") ? "I have 🙋" : "Never 🙅") + " · " + pn + ": " + (theirs.equals("have") ? "I have 🙋" : "Never 🙅");
                u.acts.add(a("🙋", "nhie", "have"));
                u.acts.add(a("🙅", "nhie", "never"));
                u.acts.add(a("🔀", "nhienext", ""));
                break;
            }
            case "random": {
                int r = wi(e, "rnd", -1);
                if (r < 0) r = (int) ((e.id * 17L + t) % 100000);
                u.label = "Random question";
                u.big = NearlyContent.pick(NearlyContent.QUESTIONS, r);
                u.bigLines = 4;
                u.acts.add(a("🔀", "rnd", ""));
                break;
            }
            case "challenge": {
                String c = NearlyContent.pick(NearlyContent.CHALLENGES, t);
                JSONObject done = g.optJSONObject("challenge") == null ? null : g.optJSONObject("challenge").optJSONObject(day);
                boolean mine = done != null && done.optBoolean(d.slot, false), theirs = done != null && done.optBoolean(d.pslot, false);
                u.label = "Daily challenge";
                u.big = c;
                u.bigLines = 4;
                u.sub = (mine ? "You ✅" : "You ⬜") + "  ·  " + pn + (theirs ? " ✅" : " ⬜");
                if (!mine) u.acts.add(a("✅", "chdone", ""));
                break;
            }
            case "invite": {
                JSONObject inv = g.optJSONObject("invite");
                boolean fresh = inv != null && NearlyUtil.now() - inv.optLong("ts", 0) < 24 * 3600000L;
                u.label = "Game invitations";
                if (fresh && inv.optString("to").equals(d.slot) && inv.optString("st", "").isEmpty()) {
                    u.big = "🎮 " + pn + " wants to play";
                    u.bigLines = 3;
                    u.sub = inv.optString("game", "a game");
                    u.acts.add(a("✅", "inviteyes", ""));
                    u.acts.add(a("❌", "inviteno", ""));
                } else {
                    u.big = "Invite " + pn;
                    u.bigLines = 2;
                    if (fresh && inv.optString("from").equals(d.slot))
                        u.sub = "Invited to " + inv.optString("game") + (inv.optString("st", "").isEmpty() ? " · waiting" : " · " + inv.optString("st"));
                    else u.sub = "Pick a game";
                    u.acts.add(a("⭕", "invite", "Tic-Tac-Toe"));
                    u.acts.add(a("❓", "invite", "Daily Question"));
                    u.acts.add(a("🎯", "invite", "Couple Challenge"));
                    u.acts.add(a("🙋", "invite", "Never Have I Ever"));
                }
                break;
            }
            default: { // ttt
                u.layout = "ttt";
                JSONObject b = g.optJSONObject("ttt");
                String board = b == null ? "---------" : b.optString("b", "---------");
                if (board.length() != 9) board = "---------";
                String turn = b == null ? "x" : b.optString("t", "x");
                String win = b == null ? "" : b.optString("w", "");
                char me = mySym(d);
                u.cells = new String[9];
                for (int i = 0; i < 9; i++) {
                    char c = board.charAt(i);
                    u.cells[i] = c == 'x' ? "✕" : c == 'o' ? "◯" : "";
                }
                u.label = "Tic-Tac-Toe";
                if (!win.isEmpty()) u.sub = win.equals("d") ? "🤝 It's a draw!" : win.charAt(0) == me ? "🎉 You won!" : pn + " won";
                else if (turn.length() > 0 && turn.charAt(0) == me) u.sub = "Your turn (" + (me == 'x' ? "✕" : "◯") + ")";
                else u.sub = pn + "'s turn…";
                u.canPlay = win.isEmpty() && turn.length() > 0 && turn.charAt(0) == me;
                u.acts.add(a("🔄 New game", "tttnew", ""));
                break;
            }
        }
        return u;
    }

    // ================================================================= 11. CANDLE
    private static NearlyRenderer.Ui candle(NearlyRenderer.Env e) {
        NearlyRenderer.Ui u = new NearlyRenderer.Ui();
        u.layout = "candle";
        u.page = "candle";
        NearlyData d = e.d;
        String pn = d.pName();
        float mine = d.myFlame(), theirs = d.partnerFlame();
        u.label = "Shared candle";
        u.art = NearlyArt.candle(e.st, mine, theirs, e.bw, e.bh);
        if (mine > 0 && theirs > 0) u.sub = "💞 Both lit — you're connected";
        else if (mine > 0) u.sub = "Waiting for " + pn + " to light theirs";
        else if (theirs > 0) u.sub = pn + " lit the candle 🕯️";
        else u.sub = "Light it to connect with " + pn;
        u.acts.add(a(mine > 0 ? "💨 Blow out" : "🕯️ Light", "candle", ""));
        return u;
    }
}
