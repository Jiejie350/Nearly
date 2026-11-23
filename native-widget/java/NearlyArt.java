package com.benjamin.nearly; // CHANGE to your app's package

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

/** Canvas drawing: cropped photos, photo frames, captions, date stamps, candle. */
final class NearlyArt {

    /** Centre-crop bmp into dst as a rounded rect. */
    static void drawCrop(Canvas cv, Bitmap bmp, RectF dst, float rx, int alpha) {
        Paint p = shaderPaint(bmp, dst, alpha);
        cv.drawRoundRect(dst, rx, rx, p);
    }

    static void drawCropOval(Canvas cv, Bitmap bmp, RectF dst) {
        cv.drawOval(dst, shaderPaint(bmp, dst, 255));
    }

    private static Paint shaderPaint(Bitmap bmp, RectF dst, int alpha) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        BitmapShader sh = new BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        float bw = bmp.getWidth(), bh = bmp.getHeight();
        float s = Math.max(dst.width() / bw, dst.height() / bh);
        Matrix m = new Matrix();
        m.setScale(s, s);
        m.postTranslate(dst.left + (dst.width() - bw * s) / 2f, dst.top + (dst.height() - bh * s) / 2f);
        sh.setLocalMatrix(m);
        p.setShader(sh);
        p.setAlpha(alpha);
        return p;
    }

    private static Paint text(int color, float size, Typeface tf, Paint.Align a) {
        Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
        t.setColor(color);
        t.setTextSize(size);
        t.setTypeface(tf);
        t.setTextAlign(a);
        return t;
    }

    private static String fit(String s, Paint p, float maxW) {
        if (s == null) return "";
        if (p.measureText(s) <= maxW) return s;
        int n = s.length();
        while (n > 1 && p.measureText(s.substring(0, n) + "…") > maxW) n--;
        return s.substring(0, n) + "…";
    }

    /**
     * Whole photo widget as one bitmap. frame: none | rounded | polaroid | circle | gold | pink | film
     * photo == null draws a friendly placeholder.
     */
    static Bitmap photoCard(NearlyStyle st, Bitmap photo, String caption, String date, String frame,
                            boolean showCap, boolean showDate, int w, int h) {
        float u = st.unit;
        Bitmap out = st.bg(w, h);
        Canvas cv = new Canvas(out);
        float rad = Math.min(st.radiusDp * u, Math.min(w, h) / 2f);
        boolean hasCap = showCap && caption != null && !caption.isEmpty();

        if (photo == null) {
            Paint t = text(st.ink, 34 * u, Typeface.DEFAULT, Paint.Align.CENTER);
            cv.drawText("📷", w / 2f, h / 2f - 4 * u, t);
            Paint t2 = text(st.mu, 12 * u, st.typeface(false), Paint.Align.CENTER);
            cv.drawText(fit("Add photos in the Nearly app", t2, w - 24 * u), w / 2f, h / 2f + 22 * u, t2);
            return out;
        }

        RectF area = new RectF(0, 0, w, h);          // where caption/date overlays go
        switch (frame) {
            case "polaroid": {
                float m = 8 * u;
                Paint card = new Paint(Paint.ANTI_ALIAS_FLAG);
                card.setColor(0xFFFFFFFF);
                card.setShadowLayer(4 * u, 0, 1.5f * u, 0x55000000);
                RectF c = new RectF(m, m, w - m, h - m);
                cv.drawRoundRect(c, 6 * u, 6 * u, card);
                float strip = (hasCap || showDate) ? 34 * u : 16 * u;
                RectF ph = new RectF(c.left + 8 * u, c.top + 8 * u, c.right - 8 * u, c.bottom - strip);
                drawCrop(cv, photo, ph, 3 * u, 255);
                Typeface hand = Typeface.create("cursive", Typeface.NORMAL);
                float cy = ph.bottom + 19 * u;
                if (hasCap) {
                    Paint t = text(0xFF3A3A3A, 15 * u, hand, Paint.Align.CENTER);
                    cv.drawText(fit(caption, t, ph.width() - 8 * u), w / 2f, cy, t);
                }
                if (showDate && date != null) {
                    Paint t = text(0xFF9A9A9A, 9 * u, Typeface.MONOSPACE, hasCap ? Paint.Align.RIGHT : Paint.Align.CENTER);
                    cv.drawText(date, hasCap ? ph.right : w / 2f, hasCap ? cy + 12 * u : cy, t);
                }
                return out;
            }
            case "circle": {
                float reserve = hasCap || showDate ? 26 * u : 0;
                float d = Math.min(w, h - reserve) - 16 * u;
                float cx = w / 2f, cy = (h - reserve) / 2f;
                RectF c = new RectF(cx - d / 2, cy - d / 2, cx + d / 2, cy + d / 2);
                drawCropOval(cv, photo, c);
                Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
                ring.setStyle(Paint.Style.STROKE);
                ring.setStrokeWidth(3 * u);
                ring.setColor(st.acc);
                cv.drawOval(c, ring);
                Paint t = text(st.ink, 13 * u, st.typeface(true), Paint.Align.CENTER);
                float ty = h - 9 * u;
                if (hasCap) cv.drawText(fit(caption, t, w - 20 * u), w / 2f, ty - (showDate ? 12 * u : 0), t);
                if (showDate && date != null) {
                    Paint t2 = text(st.mu, 9.5f * u, Typeface.MONOSPACE, Paint.Align.CENTER);
                    cv.drawText(date, w / 2f, ty, t2);
                }
                return out;
            }
            case "gold":
            case "pink": {
                float b = 6 * u;
                RectF o = new RectF(0, 0, w, h);
                Paint fp = new Paint(Paint.ANTI_ALIAS_FLAG);
                if (frame.equals("gold")) {
                    fp.setShader(new LinearGradient(0, 0, w, h, new int[]{0xFFF5D76E, 0xFFB8860B, 0xFFF5D76E, 0xFF8B6508},
                            null, Shader.TileMode.CLAMP));
                } else {
                    fp.setColor(st.acc);
                }
                cv.drawRoundRect(o, rad, rad, fp);
                area = new RectF(b, b, w - b, h - b);
                drawCrop(cv, photo, area, Math.max(0, rad - b), 255);
                break;
            }
            case "film": {
                Paint bk = new Paint(Paint.ANTI_ALIAS_FLAG);
                bk.setColor(0xFF161616);
                cv.drawRoundRect(new RectF(0, 0, w, h), Math.min(rad, 14 * u), Math.min(rad, 14 * u), bk);
                float side = 14 * u;
                Paint hole = new Paint(Paint.ANTI_ALIAS_FLAG);
                hole.setColor(0xFFF4F0E6);
                float step = 15 * u;
                for (float y = 8 * u; y < h - 8 * u; y += step) {
                    cv.drawRoundRect(new RectF(4 * u, y, 4 * u + 6 * u, y + 8 * u), 2 * u, 2 * u, hole);
                    cv.drawRoundRect(new RectF(w - 10 * u, y, w - 4 * u, y + 8 * u), 2 * u, 2 * u, hole);
                }
                area = new RectF(side + 4 * u, 6 * u, w - side - 4 * u, h - 6 * u);
                drawCrop(cv, photo, area, 3 * u, 255);
                break;
            }
            case "rounded": {
                float m = 6 * u;
                area = new RectF(m, m, w - m, h - m);
                drawCrop(cv, photo, area, Math.max(0, rad - m), 255);
                break;
            }
            default: { // none
                drawCrop(cv, photo, area, rad, 255);
                break;
            }
        }

        // caption scrim + text, date stamp (non-polaroid / non-circle frames)
        if (hasCap) {
            float sh = 34 * u;
            Paint scrim = new Paint(Paint.ANTI_ALIAS_FLAG);
            scrim.setShader(new LinearGradient(0, area.bottom - sh, 0, area.bottom, 0x00000000, 0xAA000000, Shader.TileMode.CLAMP));
            cv.save();
            cv.clipRect(area.left, area.bottom - sh, area.right, area.bottom);
            cv.drawRoundRect(new RectF(area.left, area.top, area.right, area.bottom), rad, rad, scrim);
            cv.restore();
            Paint t = text(0xFFFFFFFF, 14 * u, st.typeface(true), Paint.Align.LEFT);
            cv.drawText(fit(caption, t, area.width() - (showDate ? 100 * u : 24 * u)), area.left + 12 * u, area.bottom - 11 * u, t);
        }
        if (showDate && date != null) {
            Paint t = text(0xFFFFA726, 10 * u, Typeface.MONOSPACE, Paint.Align.RIGHT);
            t.setShadowLayer(2 * u, 0, 0, 0xCC000000);
            cv.drawText(date, area.right - 12 * u, area.bottom - 11 * u, t);
        }
        return out;
    }

    /** Handwritten note: centred, aspect-fit over the widget background. */
    static Bitmap handwritten(NearlyStyle st, Bitmap hw, int w, int h) {
        Bitmap out = st.bg(w, h);
        if (hw == null) {
            Canvas cv = new Canvas(out);
            Paint t = text(st.mu, 13 * st.unit, st.typeface(false), Paint.Align.CENTER);
            cv.drawText("✍️ No handwritten note yet", w / 2f, h / 2f, t);
            return out;
        }
        Canvas cv = new Canvas(out);
        float m = 10 * st.unit;
        float s = Math.min((w - 2 * m) / hw.getWidth(), (h - 2 * m) / hw.getHeight());
        float dw = hw.getWidth() * s, dh = hw.getHeight() * s;
        RectF dst = new RectF((w - dw) / 2f, (h - dh) / 2f, (w + dw) / 2f, (h + dh) / 2f);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        cv.drawBitmap(hw, null, dst, p);
        return out;
    }

    /**
     * Candle: flame size + glow follow `strength` (0..1). Two dots underneath are the connection
     * indicator (you / partner).
     */
    static Bitmap candle(NearlyStyle st, float mine, float theirs, int w, int h) {
        float u = st.unit;
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        float strength = Math.max(mine, theirs) * (mine > 0 && theirs > 0 ? 1f : 0.6f);
        float cx = w / 2f;
        float bodyW = Math.min(w * 0.36f, 56 * u), bodyH = h * 0.46f;
        float bodyTop = h * 0.52f;
        RectF body = new RectF(cx - bodyW / 2, bodyTop, cx + bodyW / 2, bodyTop + bodyH);
        float wickTop = bodyTop - 9 * u;
        float fy = wickTop - 14 * u * (0.5f + strength * 0.5f);

        if (strength > 0) {   // glow
            float gr = (46 + 56 * strength) * u;
            p.setShader(new RadialGradient(cx, fy, gr, NearlyUtil.withAlpha(0xFFFFC857, (int) (140 * strength)), 0x00FFC857, Shader.TileMode.CLAMP));
            cv.drawCircle(cx, fy, gr, p);
            p.setShader(null);
        }

        p.setColor(0xFFFFF3E0);                                     // wax
        cv.drawRoundRect(body, 8 * u, 8 * u, p);
        p.setColor(0x22000000);
        cv.drawRoundRect(new RectF(cx + bodyW * 0.18f, bodyTop, cx + bodyW / 2, bodyTop + bodyH), 8 * u, 8 * u, p);
        p.setColor(0xFFFFE0B2);                                     // melt pool
        cv.drawOval(new RectF(body.left + 2 * u, bodyTop - 4 * u, body.right - 2 * u, bodyTop + 6 * u), p);
        Paint wick = new Paint(Paint.ANTI_ALIAS_FLAG);
        wick.setColor(0xFF3B2A20);
        wick.setStrokeWidth(2.2f * u);
        cv.drawLine(cx, bodyTop, cx, wickTop, wick);

        if (strength > 0) {   // flame
            float fh = (16 + 18 * strength) * u, fw = (7 + 6 * strength) * u;
            Path f = flamePath(cx, fy, fw, fh);
            p.setColor(0xFFFF8A3D);
            cv.drawPath(f, p);
            p.setColor(0xFFFFD36B);
            cv.drawPath(flamePath(cx, fy + fh * 0.12f, fw * 0.55f, fh * 0.6f), p);
        } else {              // wisp of smoke
            Paint s = new Paint(Paint.ANTI_ALIAS_FLAG);
            s.setStyle(Paint.Style.STROKE);
            s.setStrokeWidth(2 * u);
            s.setColor(NearlyUtil.withAlpha(st.mu, 160));
            Path sm = new Path();
            sm.moveTo(cx, wickTop);
            sm.cubicTo(cx + 8 * u, wickTop - 8 * u, cx - 8 * u, wickTop - 16 * u, cx, wickTop - 26 * u);
            cv.drawPath(sm, s);
        }

        // connection dots
        float dy = h - 8 * u, gap = 18 * u, r = 4.5f * u;
        p.setColor(mine > 0 ? 0xFFFFA726 : NearlyUtil.withAlpha(st.mu, 120));
        cv.drawCircle(cx - gap, dy, r, p);
        p.setColor(theirs > 0 ? 0xFFFFA726 : NearlyUtil.withAlpha(st.mu, 120));
        cv.drawCircle(cx + gap, dy, r, p);
        if (mine > 0 && theirs > 0) {
            Paint l = new Paint(Paint.ANTI_ALIAS_FLAG);
            l.setColor(0xFFFF7AA8);
            l.setStrokeWidth(2 * u);
            cv.drawLine(cx - gap + r, dy, cx + gap - r, dy, l);
        }
        return b;
    }

    private static Path flamePath(float cx, float cy, float fw, float fh) {
        Path f = new Path();
        f.moveTo(cx, cy - fh);
        f.cubicTo(cx + fw * 0.9f, cy - fh * 0.3f, cx + fw * 0.8f, cy + fh * 0.5f, cx, cy + fh * 0.5f);
        f.cubicTo(cx - fw * 0.8f, cy + fh * 0.5f, cx - fw * 0.9f, cy - fh * 0.3f, cx, cy - fh);
        f.close();
        return f;
    }
}
