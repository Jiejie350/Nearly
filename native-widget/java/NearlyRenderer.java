package com.benjamin.nearly; // CHANGE to your app's package

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;
import java.util.ArrayList;

/**
 * Turns a widget instance (type + settings + cached data) into RemoteViews.
 * Content comes from NearlyWidgetContent; look comes from NearlyStyle.
 */
final class NearlyRenderer {

    static final class Act {
        final String label, action, arg;
        Act(String label, String action, String arg) { this.label = label; this.action = action; this.arg = arg == null ? "" : arg; }
    }

    static final class Ui {
        String layout = "card";            // card | photo | ttt | candle
        String label = "", big = "", sub = "", extra = "", page = "home";
        int bigLines = 1;                  // >1 = message style text
        float fixedBigSp = 0;              // >0 = use this size (calendar etc.)
        ArrayList<Act> acts = new ArrayList<>();
        Bitmap art;                        // photo / candle composite
        boolean canNext, canPlay;
        String[] cells;                    // tic-tac-toe
    }

    static final class Env {
        Context c; int id; String type, sz;
        NearlyStyle st; NearlyData d;
        int wd, hd, bw, bh; float u;
    }

    // ---------------------------------------------------------------- entry points
    static void update(Context c, AppWidgetManager m, int id, String type) {
        try {
            m.updateAppWidget(id, build(c, m, id, type, null));
        } catch (Throwable t) {
            try { m.updateAppWidget(id, fallback(c)); } catch (Throwable ignored) { }
        }
    }

    /** previewSz: null = real size from the launcher, otherwise small|medium|large (settings preview). */
    static RemoteViews build(Context c, AppWidgetManager m, int id, String type, String previewSz) {
        Env e = new Env();
        e.c = c; e.id = id; e.type = type;
        e.st = NearlyStyle.load(c, id);
        e.d = new NearlyData(c);

        int wd = 250, hd = 110;
        if (previewSz != null) {
            if (previewSz.equals("small")) { wd = 140; hd = 140; }
            else if (previewSz.equals("large")) { wd = 290; hd = 300; }
            else { wd = 270; hd = 130; }
        } else {
            Bundle o = m.getAppWidgetOptions(id);
            int w0 = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0);
            int h0 = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0);
            if (h0 <= 0) h0 = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0);
            if (w0 > 0) wd = w0;
            if (h0 > 0) hd = h0;
        }
        e.wd = wd; e.hd = hd;
        e.sz = previewSz != null && !previewSz.equals("auto") ? previewSz : e.st.sizeClass(wd, hd);

        float dens = c.getResources().getDisplayMetrics().density;
        float wpx = wd * dens, hpx = hd * dens;
        float k = Math.min(1f, 520f / Math.max(wpx, hpx));
        e.bw = Math.max(48, Math.round(wpx * k));
        e.bh = Math.max(48, Math.round(hpx * k));
        e.u = dens * k;
        e.st.unit = e.u;

        Ui ui = NearlyWidgetContent.build(e);   // may adjust style (sticky colour)
        RemoteViews rv;
        switch (ui.layout) {
            case "photo": rv = photo(e, ui); break;
            case "ttt": rv = ttt(e, ui); break;
            case "candle": rv = candle(e, ui); break;
            default: rv = card(e, ui); break;
        }
        rv.setOnClickPendingIntent(R.id.w_root, openPi(c, id, ui.page));
        return rv;
    }

    private static RemoteViews fallback(Context c) {
        RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.nly_w_card);
        rv.setTextViewText(R.id.w_big, "💗 Nearly");
        rv.setViewVisibility(R.id.w_actions, View.GONE);
        rv.setOnClickPendingIntent(R.id.w_root, openPi(c, 0, "home"));
        return rv;
    }

    // ---------------------------------------------------------------- layouts
    private static float[] sizes(String sz) {            // label, big, msg, sub, extra, button (sp)
        switch (sz) {
            case "small": return new float[]{10, 22, 13, 12, 11, 16};
            case "large": return new float[]{12, 40, 22, 17, 14, 22};
            default: return new float[]{11, 28, 16, 14, 12, 18};
        }
    }

    private static int estLines(String text, float sp, int widthDp, int maxLines) {
        if (text == null || text.isEmpty()) return 0;
        int total = 0;
        float perLine = Math.max(4, widthDp / (sp * 0.52f));
        for (String ln : text.split("\n")) total += Math.max(1, (int) Math.ceil(ln.length() / perLine));
        return Math.min(total, maxLines);
    }

    private static RemoteViews card(Env e, Ui ui) {
        Context c = e.c;
        NearlyStyle st = e.st;
        boolean centered = st.align.equals("center");
        RemoteViews rv = new RemoteViews(c.getPackageName(), centered ? R.layout.nly_w_card_c : R.layout.nly_w_card);
        rv.setImageViewBitmap(R.id.w_bg, st.bg(e.bw, e.bh));

        float[] s = sizes(e.sz);
        float sc = st.scale;
        int innerW = e.wd - 30;
        boolean hasActs = st.showActions && !ui.acts.isEmpty();

        // label
        boolean showLabel = st.showLabel && !ui.label.isEmpty();
        if (showLabel) {
            rv.setViewVisibility(R.id.w_label, View.VISIBLE);
            rv.setTextViewText(R.id.w_label, st.span(ui.label.toUpperCase(), true));
            rv.setTextColor(R.id.w_label, st.acc);
            rv.setTextViewTextSize(R.id.w_label, TypedValue.COMPLEX_UNIT_SP, s[0] * sc);
        } else rv.setViewVisibility(R.id.w_label, View.GONE);

        // big
        float bigSp;
        if (ui.fixedBigSp > 0) bigSp = ui.fixedBigSp;
        else if (ui.bigLines > 1) {
            int len = ui.big.length();
            float f = len > 90 ? 0.72f : len > 60 ? 0.82f : len > 36 ? 0.92f : 1f;
            bigSp = s[2] * f;
        } else {
            bigSp = s[1];
            int len = ui.big.length();
            if (len > 9) bigSp = Math.max(s[1] * 0.5f, s[1] * 9f / len);
        }
        bigSp *= sc;
        int bigLines = ui.big.isEmpty() ? 0 : (ui.fixedBigSp > 0 ? ui.big.split("\n").length : estLines(ui.big, bigSp, innerW, ui.bigLines > 1 ? 5 : 1));
        if (!ui.big.isEmpty()) {
            rv.setViewVisibility(R.id.w_big, View.VISIBLE);
            rv.setTextViewText(R.id.w_big, st.span(ui.big, ui.fixedBigSp <= 0));
            rv.setTextColor(R.id.w_big, st.ink);
            rv.setTextViewTextSize(R.id.w_big, TypedValue.COMPLEX_UNIT_SP, bigSp);
        } else rv.setViewVisibility(R.id.w_big, View.GONE);

        // sub
        float subSp = s[3] * sc;
        int subLines = estLines(ui.sub, subSp, innerW, 3);
        if (!ui.sub.isEmpty()) {
            rv.setViewVisibility(R.id.w_sub, View.VISIBLE);
            rv.setTextViewText(R.id.w_sub, st.span(ui.sub, false));
            rv.setTextColor(R.id.w_sub, st.ink);
            rv.setTextViewTextSize(R.id.w_sub, TypedValue.COMPLEX_UNIT_SP, subSp);
        } else rv.setViewVisibility(R.id.w_sub, View.GONE);

        // extra: as many lines as the remaining height allows
        float extraSp = s[4] * sc;
        float used = 28 + (showLabel ? s[0] * sc * 1.5f : 0) + bigLines * bigSp * 1.3f + subLines * subSp * 1.35f + (hasActs ? 46 : 0);
        int room = (int) Math.floor((e.hd - used) / (extraSp * 1.4f));
        int cap = e.sz.equals("large") ? 9 : e.sz.equals("medium") ? 3 : 0;
        int lines = Math.max(0, Math.min(room, cap));
        if (st.showExtra && !ui.extra.isEmpty() && lines > 0) {
            rv.setViewVisibility(R.id.w_extra, View.VISIBLE);
            rv.setTextViewText(R.id.w_extra, st.span(NearlyUtil.lines(ui.extra, lines), false));
            rv.setTextColor(R.id.w_extra, st.mu);
            rv.setTextViewTextSize(R.id.w_extra, TypedValue.COMPLEX_UNIT_SP, extraSp);
        } else rv.setViewVisibility(R.id.w_extra, View.GONE);

        // buttons
        int[] btn = {R.id.w_b1, R.id.w_b2, R.id.w_b3, R.id.w_b4};
        rv.setViewVisibility(R.id.w_actions, hasActs ? View.VISIBLE : View.GONE);
        for (int i = 0; i < 4; i++) {
            if (hasActs && i < ui.acts.size()) {
                Act a = ui.acts.get(i);
                rv.setViewVisibility(btn[i], View.VISIBLE);
                rv.setTextViewText(btn[i], st.span(a.label, false));
                rv.setTextColor(btn[i], st.ink);
                rv.setTextViewTextSize(btn[i], TypedValue.COMPLEX_UNIT_SP, s[5] * Math.min(1.2f, sc));
                rv.setOnClickPendingIntent(btn[i], actPi(e.c, e.id, i, a));
            } else rv.setViewVisibility(btn[i], View.GONE);
        }
        return rv;
    }

    private static RemoteViews photo(Env e, Ui ui) {
        RemoteViews rv = new RemoteViews(e.c.getPackageName(), R.layout.nly_w_photo);
        rv.setImageViewBitmap(R.id.w_photo, ui.art);
        if (ui.canNext) {
            rv.setViewVisibility(R.id.w_next, View.VISIBLE);
            rv.setTextColor(R.id.w_next, e.st.ink);
            rv.setOnClickPendingIntent(R.id.w_next, actPi(e.c, e.id, 0, new Act("›", "next", "")));
        } else rv.setViewVisibility(R.id.w_next, View.GONE);
        return rv;
    }

    private static RemoteViews candle(Env e, Ui ui) {
        NearlyStyle st = e.st;
        RemoteViews rv = new RemoteViews(e.c.getPackageName(), R.layout.nly_w_candle);
        rv.setImageViewBitmap(R.id.w_bg, st.bg(e.bw, e.bh));
        rv.setImageViewBitmap(R.id.w_art, ui.art);
        rv.setOnClickPendingIntent(R.id.w_art, actPi(e.c, e.id, 0, new Act("", "candle", "")));
        float[] s = sizes(e.sz);
        if (st.showLabel && !ui.label.isEmpty()) {
            rv.setViewVisibility(R.id.w_label, View.VISIBLE);
            rv.setTextViewText(R.id.w_label, st.span(ui.label.toUpperCase(), true));
            rv.setTextColor(R.id.w_label, st.acc);
            rv.setTextViewTextSize(R.id.w_label, TypedValue.COMPLEX_UNIT_SP, s[0] * st.scale);
        } else rv.setViewVisibility(R.id.w_label, View.GONE);
        rv.setTextViewText(R.id.w_sub, st.span(ui.sub, false));
        rv.setTextColor(R.id.w_sub, st.ink);
        rv.setTextViewTextSize(R.id.w_sub, TypedValue.COMPLEX_UNIT_SP, s[4] * st.scale);
        if (st.showActions && !ui.acts.isEmpty()) {
            Act a = ui.acts.get(0);
            rv.setViewVisibility(R.id.w_b1, View.VISIBLE);
            rv.setTextViewText(R.id.w_b1, st.span(a.label, false));
            rv.setTextColor(R.id.w_b1, st.ink);
            rv.setTextViewTextSize(R.id.w_b1, TypedValue.COMPLEX_UNIT_SP, s[4] * st.scale);
            rv.setOnClickPendingIntent(R.id.w_b1, actPi(e.c, e.id, 1, a));
        } else rv.setViewVisibility(R.id.w_b1, View.GONE);
        return rv;
    }

    private static RemoteViews ttt(Env e, Ui ui) {
        NearlyStyle st = e.st;
        RemoteViews rv = new RemoteViews(e.c.getPackageName(), R.layout.nly_w_ttt);
        rv.setImageViewBitmap(R.id.w_bg, st.bg(e.bw, e.bh));
        float[] s = sizes(e.sz);
        if (st.showLabel) {
            rv.setViewVisibility(R.id.w_label, View.VISIBLE);
            rv.setTextViewText(R.id.w_label, st.span(ui.label.toUpperCase(), true));
            rv.setTextColor(R.id.w_label, st.acc);
            rv.setTextViewTextSize(R.id.w_label, TypedValue.COMPLEX_UNIT_SP, s[0] * st.scale);
        } else rv.setViewVisibility(R.id.w_label, View.GONE);
        rv.setTextViewText(R.id.w_sub, st.span(ui.sub, false));
        rv.setTextColor(R.id.w_sub, st.ink);
        rv.setTextViewTextSize(R.id.w_sub, TypedValue.COMPLEX_UNIT_SP, s[4] * st.scale);
        int[] cell = {R.id.w_t0, R.id.w_t1, R.id.w_t2, R.id.w_t3, R.id.w_t4, R.id.w_t5, R.id.w_t6, R.id.w_t7, R.id.w_t8};
        for (int i = 0; i < 9; i++) {
            String t = ui.cells[i];
            rv.setTextViewText(cell[i], t);
            rv.setTextColor(cell[i], t.equals("✕") ? st.acc : st.ink);
            rv.setTextViewTextSize(cell[i], TypedValue.COMPLEX_UNIT_SP, (e.sz.equals("large") ? 30 : 22) * st.scale);
            if (ui.canPlay && t.isEmpty()) rv.setOnClickPendingIntent(cell[i], actPi(e.c, e.id, i, new Act("", "ttt", String.valueOf(i))));
            else rv.setOnClickPendingIntent(cell[i], openPi(e.c, e.id, "games"));
        }
        if (st.showActions && !ui.acts.isEmpty()) {
            Act a = ui.acts.get(0);
            rv.setViewVisibility(R.id.w_b1, View.VISIBLE);
            rv.setTextViewText(R.id.w_b1, st.span(a.label, false));
            rv.setTextColor(R.id.w_b1, st.ink);
            rv.setTextViewTextSize(R.id.w_b1, TypedValue.COMPLEX_UNIT_SP, s[4] * st.scale);
            rv.setOnClickPendingIntent(R.id.w_b1, actPi(e.c, e.id, 9, a));
        } else rv.setViewVisibility(R.id.w_b1, View.GONE);
        return rv;
    }

    // ---------------------------------------------------------------- pending intents
    static PendingIntent openPi(Context c, int id, String page) {
        Intent i = c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());
        if (i == null) i = new Intent();
        i.putExtra("nearly_page", page == null ? "home" : page);
        i.setData(Uri.parse("nearly://open/" + id + "/" + Uri.encode(page == null ? "home" : page)));
        return PendingIntent.getActivity(c, id * 40 + 39, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static PendingIntent actPi(Context c, int id, int idx, Act a) {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        int rc = id * 40 + idx;
        Uri data = Uri.parse("nearly://act/" + id + "/" + idx + "/" + a.action + "/" + Uri.encode(a.arg));
        switch (a.action) {
            case "note":
            case "answer": {
                Intent i = new Intent(c, QuickNoteActivity.class);
                i.setData(data);
                i.putExtra("mode", a.action.equals("answer") ? "answer" : "note");
                i.putExtra(a.action.equals("answer") ? "q" : "kind", a.arg);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                return PendingIntent.getActivity(c, rc, i, flags);
            }
            case "moodpick": {
                Intent i = new Intent(c, MoodPickerActivity.class);
                i.setData(data);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                return PendingIntent.getActivity(c, rc, i, flags);
            }
            case "game":
            case "open": {
                Intent i = c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());
                if (i == null) i = new Intent();
                i.putExtra("nearly_page", a.arg.isEmpty() ? (a.action.equals("game") ? "games" : "home") : a.arg);
                i.setData(data);
                return PendingIntent.getActivity(c, rc, i, flags);
            }
            default: {
                Intent i = new Intent(c, NearlyActionReceiver.class);
                i.setData(data);
                i.putExtra("id", id).putExtra("act", a.action).putExtra("arg", a.arg);
                return PendingIntent.getBroadcast(c, rc, i, flags);
            }
        }
    }
}
