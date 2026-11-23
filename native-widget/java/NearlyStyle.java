package com.benjamin.nearly; // CHANGE to your app's package

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import java.io.File;

/** Resolved look of one widget instance (theme + user customisation). */
final class NearlyStyle {
    boolean dark;
    int c1, c2, ink, mu, acc;
    String bgMode, font, align, size;
    float scale, radiusDp, unit = 1f;
    int alpha, dim;
    boolean photoBg, showLabel, showActions, showExtra;
    int solidOverride = 0;        // e.g. sticky-note colour
    Bitmap photoBmp;

    static NearlyStyle load(Context c, int id) {
        NearlyStyle s = new NearlyStyle();
        String theme = NearlyStore.w(c, id, "theme", "auto");
        boolean night = (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        s.dark = theme.equals("dark") || (theme.equals("auto") && night);

        s.c1 = NearlyUtil.color(NearlyStore.w(c, id, "bg1", ""), s.dark ? 0xFF3A2132 : 0xFFFFE1EC);
        s.c2 = NearlyUtil.color(NearlyStore.w(c, id, "bg2", ""), s.dark ? 0xFF2C2442 : 0xFFEADFFF);
        String txt = NearlyStore.w(c, id, "txt", "");
        s.ink = NearlyUtil.color(txt, s.dark ? 0xFFFFE9F3 : 0xFF4A2A3C);
        s.mu = txt.isEmpty() ? (s.dark ? 0xFFCBA3B8 : 0xFF9A7C8C) : NearlyUtil.withAlpha(s.ink, 0xB8);
        s.acc = NearlyUtil.color(NearlyStore.w(c, id, "acc", ""), 0xFFFF7AA8);

        s.bgMode = NearlyStore.w(c, id, "bg", "gradient");
        s.font = NearlyStore.w(c, id, "font", "sans-serif");
        s.align = NearlyStore.w(c, id, "align", "left");
        s.size = NearlyStore.w(c, id, "size", "auto");
        s.scale = NearlyStore.wi(c, id, "scale", 100) / 100f;
        s.radiusDp = NearlyStore.wi(c, id, "radius", 28);
        s.alpha = Math.round(255 * Math.max(0, Math.min(100, NearlyStore.wi(c, id, "opacity", 100))) / 100f);
        s.dim = NearlyStore.wi(c, id, "dim", 30);
        s.showLabel = NearlyStore.wb(c, id, "showLabel", true);
        s.showActions = NearlyStore.wb(c, id, "showActions", true);
        s.showExtra = NearlyStore.wb(c, id, "showExtra", true);

        File f = NearlyStore.bgFile(c, id);
        if (s.bgMode.equals("photo") && f.exists()) {
            s.photoBmp = NearlyPhotos.decodeFile(f, 720, 720);
            s.photoBg = s.photoBmp != null;
        }
        if (s.photoBg && txt.isEmpty()) { s.ink = 0xFFFFFFFF; s.mu = 0xFFE6E6E6; }
        return s;
    }

    /** Sticky-note style override: solid pastel with dark ink. */
    void applySticky(int color) {
        solidOverride = color;
        ink = 0xFF3B3320;
        mu = 0xFF6B6248;
    }

    String sizeClass(int wdp, int hdp) {
        if (!size.equals("auto")) return size;
        if (wdp < 170 || hdp < 95) return "small";
        if (hdp >= 200 && wdp >= 220) return "large";
        return "medium";
    }

    /** Applies the chosen font family (+ optional bold) as spans so it survives RemoteViews. */
    CharSequence span(String t, boolean bold) {
        if (t == null || t.isEmpty()) return "";
        SpannableString s = new SpannableString(t);
        int n = t.length();
        if (!font.equals("sans-serif")) s.setSpan(new TypefaceSpan(font), 0, n, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (bold) s.setSpan(new StyleSpan(Typeface.BOLD), 0, n, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return s;
    }

    Typeface typeface(boolean bold) {
        return Typeface.create(font, bold ? Typeface.BOLD : Typeface.NORMAL);
    }

    /** Rounded background bitmap honouring mode, colours, radius, transparency, photo + dim. */
    Bitmap bg(int w, int h) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        if (bgMode.equals("transparent")) return b;
        Canvas cv = new Canvas(b);
        RectF r = new RectF(0, 0, w, h);
        float rad = Math.min(radiusDp * unit, Math.min(w, h) / 2f);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

        if (solidOverride != 0 || bgMode.equals("solid")) {
            p.setColor(solidOverride != 0 ? solidOverride : c1);
            p.setAlpha(alpha);
            cv.drawRoundRect(r, rad, rad, p);
        } else if (photoBg && photoBmp != null) {
            NearlyArt.drawCrop(cv, photoBmp, r, rad, alpha);
            Paint d = new Paint(Paint.ANTI_ALIAS_FLAG);
            d.setColor(0xFF000000);
            d.setAlpha(Math.round(alpha * Math.max(0, Math.min(100, dim)) / 100f));
            cv.drawRoundRect(r, rad, rad, d);
        } else {
            p.setShader(new LinearGradient(0, 0, w, h, c1, c2, Shader.TileMode.CLAMP));
            p.setAlpha(alpha);
            cv.drawRoundRect(r, rad, rad, p);
        }

        Paint s = new Paint(Paint.ANTI_ALIAS_FLAG);
        s.setStyle(Paint.Style.STROKE);
        s.setStrokeWidth(1.5f * unit);
        s.setColor(NearlyUtil.withAlpha(acc, Math.round(alpha * 0.35f)));
        RectF in = new RectF(r);
        in.inset(0.75f * unit, 0.75f * unit);
        cv.drawRoundRect(in, rad, rad, s);
        return b;
    }
}
