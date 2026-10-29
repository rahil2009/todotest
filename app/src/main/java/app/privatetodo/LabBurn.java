package app.privatetodo;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LightingColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

/**
 * Design lab only: the twenty extra delete animations (styles 10 to 29 of {@link LabSwipe}).
 * Drawn in the row's own coordinates: the row is rw wide and rh tall, and cdx is where the finger left it.
 */
final class LabBurn {
    private static final Paint bp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private static final Paint nf = new Paint(Paint.ANTI_ALIAS_FLAG); // no bitmap filtering: hard pixels
    private static final Paint q = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final RectF r1 = new RectF();
    private static final Rect s1 = new Rect();
    private static final Path path = new Path();
    private static final int[] PAL = {0xFFFF5252, 0xFFFFD740, 0xFF69F0AE, 0xFF40C4FF, 0xFFB388FF, 0xFFFF80AB, 0xFFFFAB40};

    static {
        nf.setFilterBitmap(false);
    }

    private LabBurn() {
    }

    private static float cl(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static float ease(float v) {
        v = cl(v);
        return v * v * (3 - 2 * v);
    }

    private static float lerp(float a, float b, float v) {
        return a + (b - a) * v;
    }

    private static void piece(Canvas c, Bitmap b, int x0, int y0, int x1, int y1, RectF dst, int alpha, Paint paint) {
        if (alpha <= 0 || dst.width() <= 0 || dst.height() <= 0) return;
        s1.set(x0, y0, x1, y1);
        paint.setAlpha(Math.min(255, alpha));
        c.drawBitmap(b, s1, dst, paint);
        paint.setAlpha(255);
    }

    static void draw(LabSwipe v, Canvas c, int k, float t) {
        switch (k) {
            case 0: hole(v, c, t); break;
            case 1: explode(v, c, t); break;
            case 2: fire(v, c, t); break;
            case 3: glitch(v, c, t); break;
            case 4: shatter(v, c, t); break;
            case 5: zip(v, c, t); break;
            case 6: rocket(v, c, t); break;
            case 7: slice(v, c, t); break;
            case 8: flip(v, c, t); break;
            case 9: genie(v, c, t); break;
            case 10: melt(v, c, t); break;
            case 11: bubbles(v, c, t); break;
            case 12: lightning(v, c, t); break;
            case 13: confetti(v, c, t); break;
            case 14: stamp(v, c, t); break;
            case 15: eraser(v, c, t); break;
            case 16: spin(v, c, t); break;
            case 17: domino(v, c, t); break;
            case 18: pixelate(v, c, t); break;
            default: portal(v, c, t); break;
        }
    }

    /** Shrinks the whole area toward its middle line near the end, so the list closes the gap. */
    private static void close(LabSwipe v, Canvas c, float t, float from) {
        v.collapse(c, 1f - ease((t - from) / (1f - from)));
    }

    // ------------------------------------------------------------------ 0 black hole

    private static void hole(LabSwipe v, Canvas c, float t) {
        float W = v.rw;
        float H = v.rh;
        float d = v.d;
        close(v, c, t, 0.82f);
        v.drawReveal(c, 1f, 1f, 1f);
        float u = ease(cl(t / 0.78f));
        float px = W * 0.5f;
        float py = H / 2f;
        // stars spiral inward
        q.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 30; i++) {
            double a0 = v.rnd[i] * Math.PI * 2;
            float r0 = (0.25f + v.rnd[i + 30] * 0.75f) * W * 0.5f;
            double a = a0 + u * 5.5 * (1.2 - v.rnd[i + 60] * 0.5);
            float r = r0 * (1f - u * 0.97f);
            q.setColor(0xFFFFFFFF);
            q.setAlpha((int) (230 * (1f - u * 0.6f)));
            c.drawCircle(px + (float) Math.cos(a) * r, py + (float) Math.sin(a) * r * 0.5f, 1.3f * d, q);
        }
        q.setAlpha(255);
        // accretion glow
        q.setShader(new RadialGradient(px, py, 36 * d, 0xFF000000, 0x00B388FF, Shader.TileMode.CLAMP));
        c.drawCircle(px, py, 36 * d, q);
        q.setShader(null);
        q.setStyle(Paint.Style.STROKE);
        q.setStrokeWidth(2 * d);
        q.setColor(0xFFB388FF);
        q.setAlpha(170);
        rf(px - 22 * d, py - 8 * d, px + 22 * d, py + 8 * d);
        c.drawOval(r1, q);
        q.setAlpha(255);
        q.setStyle(Paint.Style.FILL);
        // the row
        float cx = lerp(v.cdx + W / 2f, px, u);
        float s = Math.max(0.02f, 1f - u);
        int al = (int) (255 * (1f - cl((u - 0.82f) / 0.18f)));
        if (al > 0) {
            c.save();
            c.translate(cx, py);
            c.rotate(560f * u);
            c.scale(s, s);
            c.translate(-W / 2f, -H / 2f);
            v.drawRow(c, al, 0);
            c.restore();
        }
        // final flash
        float f = cl((t - 0.74f) / 0.12f);
        if (f > 0 && f < 1) {
            q.setColor(0xFFFFFFFF);
            q.setAlpha((int) (255 * (1f - f)));
            c.drawCircle(px, py, 4 * d + f * 26 * d, q);
            q.setAlpha(255);
        }
    }

    private static void rf(float l, float t, float r, float b) {
        r1.set(l, t, r, b);
    }

    // ------------------------------------------------------------------ 1 explode

    private static void explode(LabSwipe v, Canvas c, float t) {
        float W = v.rw;
        float H = v.rh;
        float d = v.d;
        close(v, c, t, 0.8f);
        v.drawReveal(c, 1f, 1f, 1f);
        if (v.snap == null) return;
        int cols = 7;
        int rows = 3;
        float bw = W / cols;
        float bh = H / rows;
        float ox = v.cdx + W / 2f;
        float oy = H / 2f;
        float lt = cl((t - 0.1f) / 0.7f);
        for (int i = 0; i < cols; i++) {
            for (int j = 0; j < rows; j++) {
                int id = i * rows + j;
                float bx = v.cdx + i * bw + bw / 2f;
                float by = j * bh + bh / 2f;
                float dx = bx - ox;
                float dy = by - oy;
                float len = (float) Math.hypot(dx, dy) + 1f;
                float sp = (70 + v.rnd[id] * 120) * d;
                float px = bx + dx / len * sp * lt + (v.rnd[id + 30] - 0.5f) * 30 * d * lt;
                float py = by + dy / len * sp * lt * 0.6f - 40 * d * lt * (1f - lt) + lt * lt * 160 * d;
                float rot = (v.rnd[id + 50] - 0.5f) * 720f * lt;
                int al = (int) (255 * (1f - lt * lt));
                c.save();
                c.translate(px, py);
                c.rotate(rot);
                rf(-bw / 2f, -bh / 2f, bw / 2f, bh / 2f);
                piece(c, v.snap, Math.round(i * bw), Math.round(j * bh), Math.round((i + 1) * bw),
                        Math.round((j + 1) * bh), r1, al, bp);
                c.restore();
            }
        }
        // flash and shock ring
        float f = cl((t - 0.06f) / 0.3f);
        if (f > 0 && f < 1) {
            q.setStyle(Paint.Style.FILL);
            q.setShader(new RadialGradient(ox, oy, 10 * d + f * W * 0.55f, 0xCCFFFFFF, 0x00FFAB40,
                    Shader.TileMode.CLAMP));
            c.drawCircle(ox, oy, 10 * d + f * W * 0.55f, q);
            q.setShader(null);
            q.setStyle(Paint.Style.STROKE);
            q.setStrokeWidth(3 * d);
            q.setColor(0xFFFFD740);
            q.setAlpha((int) (255 * (1f - f)));
            c.drawCircle(ox, oy, f * W * 0.6f, q);
            q.setAlpha(255);
            q.setStyle(Paint.Style.FILL);
        }
    }

    // ------------------------------------------------------------------ 2 fire

    private static void fire(LabSwipe v, Canvas c, float t) {
        float W = v.rw;
        float H = v.rh;
        float d = v.d;
        close(v, c, t, 0.82f);
        v.drawReveal(c, 1f, 1f, 1f);
        float u = cl(t / 0.78f);
        float fx = lerp(v.cdx + W, v.cdx, u);
        c.save();
        c.clipRect(0, 0, Math.max(0f, fx - 6 * d), H);
        c.save();
        c.translate(v.cdx, 0);
        v.drawRow(c, 255, 0);
        c.restore();
        c.restore();
        // charred edge
        q.setStyle(Paint.Style.FILL);
        float cl0 = Math.max(0f, fx - 6 * d);
        q.setShader(new LinearGradient(cl0, 0, fx + 4 * d, 0, 0x00000000, 0xFF140A05, Shader.TileMode.CLAMP));
        c.drawRect(cl0, 0, fx + 4 * d, H, q);
        q.setShader(null);
        if (u < 1f) {
            for (int i = 0; i < 16; i++) {
                float y = (i + v.rnd[i]) / 16f * H;
                float flick = (float) Math.sin(t * 40 + i * 1.7f);
                float rad = (6 + 5 * v.rnd[i + 20] + 2 * flick) * d;
                q.setColor(0xFFFF6D00);
                q.setAlpha(220);
                c.drawCircle(fx + (v.rnd[i + 40] - 0.5f) * 6 * d, y - 3 * d * flick, rad, q);
                q.setColor(0xFFFFD740);
                q.setAlpha(230);
                c.drawCircle(fx + (v.rnd[i + 40] - 0.5f) * 6 * d, y - 2 * d * flick, rad * 0.55f, q);
            }
            q.setAlpha(255);
        }
        // embers and ash rise from the front, whatever was already burnt
        for (int i = 0; i < 26; i++) {
            float born = v.rnd[i] * 0.7f;
            float lt = cl((t - born) / 0.35f);
            if (lt <= 0 || lt >= 1) continue;
            float bx = lerp(v.cdx + W, v.cdx, cl(born / 0.78f));
            float x = bx + (v.rnd[i + 26] - 0.5f) * 14 * d + (float) Math.sin(lt * 7 + i) * 5 * d;
            float y = v.rnd[i + 52] * H - lt * 38 * d;
            q.setColor(i % 3 == 0 ? 0xFFFFAB40 : 0xFF4A4A4A);
            q.setAlpha((int) (230 * (1f - lt)));
            c.drawCircle(x, y, (1.6f + 1.4f * v.rnd[i + 10]) * d, q);
        }
        q.setAlpha(255);
    }

    // ------------------------------------------------------------------ 3 glitch

    private static void glitch(LabSwipe v, Canvas c, float t) {
        float W = v.rw;
        float H = v.rh;
        float d = v.d;
        close(v, c, t, 0.85f);
        v.drawReveal(c, 1f, 1f, 1f);
        if (v.snap == null) return;
        int n = 9;
        float sh = H / n;
        int frame = (int) (t * 26);
        float intensity = (float) Math.sin(Math.PI * cl(t / 0.8f));
        float vanish = cl((t - 0.45f) / 0.35f);
        LightingColorFilter red = new LightingColorFilter(0xFFFF0000, 0);
        LightingColorFilter cyan = new LightingColorFilter(0xFF00FFFF, 0);
        for (int i = 0; i < n; i++) {
            float rr = v.rnd[(frame * 7 + i * 5) % 90];
            if (rr < vanish) continue; // slices drop out one by one
            float off = (v.rnd[(frame * 11 + i * 3) % 90] - 0.5f) * 70 * d * intensity;
            rf(v.cdx + off, i * sh, v.cdx + off + W, (i + 1) * sh);
            int y0 = Math.round(i * sh);
            int y1 = Math.round((i + 1) * sh);
            if (intensity > 0.25f) {
                bp.setColorFilter(red);
                RectF a = new RectF(r1);
                a.offset(-6 * d * intensity, 0);
                piece(c, v.snap, 0, y0, v.snap.getWidth(), y1, a, 160, bp);
                bp.setColorFilter(cyan);
                a.offset(12 * d * intensity, 0);
                piece(c, v.snap, 0, y0, v.snap.getWidth(), y1, a, 160, bp);
                bp.setColorFilter(null);
            }
            piece(c, v.snap, 0, y0, v.snap.getWidth(), y1, r1, 255, bp);
        }
        // static bars
        q.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 10; i++) {
            float y = v.rnd[(frame * 3 + i) % 90] * H;
            float x = v.rnd[(frame * 5 + i + 30) % 90] * W;
            q.setColor(i % 2 == 0 ? 0xFFFFFFFF : 0xFF00E5FF);
            q.setAlpha((int) (200 * intensity));
            c.drawRect(x, y, x + (20 + 80 * v.rnd[(i + frame) % 90]) * d, y + 1.6f * d, q);
        }
        q.setAlpha(255);
    }

    // ------------------------------------------------------------------ 4 shatter

    private static void shatter(LabSwipe v, Canvas c, float t) {
        float W = v.rw;
        float H = v.rh;
        float d = v.d;
        close(v, c, t, 0.86f);
        v.drawReveal(c, 1f, 1f, 1f);
        if (v.snap == null) return;
        float ix = v.cdx + W * 0.62f;
        float iy = H * 0.5f;
        int n = 14;
        float R = W * 1.2f;
        float crack = cl(t / 0.14f);
        for (int i = 0; i < n; i++) {
            double a0 = (i + v.rnd[i] * 0.4f) / n * Math.PI * 2;
            double a1 = (i + 1 + v.rnd[(i + 1) % n] * 0.4f) / n * Math.PI * 2;
            float delay = 0.12f + v.rnd[i + 20] * 0.12f;
            float lt = cl((t - delay) / 0.6f);
            c.save();
            float fall = lt * lt * 230 * d;
            double mid = (a0 + a1) / 2;
            c.translate((float) Math.cos(mid) * 36 * d * lt, fall);
            c.rotate((v.rnd[i + 40] - 0.5f) * 70f * lt, ix, iy);
            path.reset();
            path.moveTo(ix, iy);
            path.lineTo(ix + (float) Math.cos(a0) * R, iy + (float) Math.sin(a0) * R);
            path.lineTo(ix + (float) Math.cos(a1) * R, iy + (float) Math.sin(a1) * R);
            path.close();
            c.clipPath(path);
            rf(v.cdx, 0, v.cdx + W, H);
            piece(c, v.snap, 0, 0, v.snap.getWidth(), v.snap.getHeight(), r1, (int) (255 * (1f - lt * lt)), bp);
            c.restore();
        }
        // crack lines
        q.setStyle(Paint.Style.STROKE);
        q.setStrokeWidth(1.4f * d);
        q.setColor(0xFFFFFFFF);
        q.setAlpha((int) (230 * (1f - cl((t - 0.16f) / 0.1f))));
        if (q.getAlpha() > 0) {
            for (int i = 0; i < n; i++) {
                double a0 = (i + v.rnd[i] * 0.4f) / n * Math.PI * 2;
                float len = crack * (30 + 90 * v.rnd[i + 60]) * d;
                c.drawLine(ix, iy, ix + (float) Math.cos(a0) * len, iy + (float) Math.sin(a0) * len, q);
            }
        }
        q.setAlpha(255);
        q.setStyle(Paint.Style.FILL);
    }

    // ------------------------------------------------------------------ 5 zip

    private static void zip(LabSwipe v, Canvas c, float t) {
        float W = v.rw;
        float H = v.rh;
        float d = v.d;
        close(v, c, t, 0.88f);
        v.drawReveal(c, 1f, 1f, 1f);
        float u = ease(cl(t / 0.48f));
        float cx = v.cdx + W / 2f;
        float sx = Math.max(0.012f, 1f - u * 0.99f);
        float fly = cl((t - 0.52f) / 0.4f);
        c.save();
        c.translate(cx, H / 2f - fly * fly * (H + 90 * d));
        c.scale(sx, 1f);
        c.translate(-W / 2f, -H / 2f);
        v.drawRow(c, (int) (255 * (1f - cl((fly - 0.6f) / 0.4f))), 0);
        c.restore();
        // teeth along the closing seam, slider travelling up
        float seamY = H - u * H;
        q.setStyle(Paint.Style.FILL);
        float fa = 1f - cl((fly - 0.6f) / 0.4f);
        for (float y = 0; y < H; y += 5 * d) {
            if (y < seamY) continue;
            boolean left = ((int) (y / (5 * d))) % 2 == 0;
            q.setColor(0xFFCFD8DC);
            q.setAlpha((int) (255 * fa));
            float yy = y - fly * fly * (H + 90 * d);
            c.drawRect(cx + (left ? -3 * d : 0), yy, cx + (left ? 0 : 3 * d), yy + 2.4f * d, q);
        }
        q.setColor(0xFFFFD740);
        q.setAlpha((int) (255 * fa));
        float sy = seamY - fly * fly * (H + 90 * d);
        rf(cx - 6 * d, sy - 7 * d, cx + 6 * d, sy + 7 * d);
        c.drawRoundRect(r1, 3 * d, 3 * d, q);
        q.setAlpha(255);
    }

    // ------------------------------------------------------------------ 6 rocket

    private static void rocket(LabSwipe v, Canvas c, float t) {
        float W = v.rw;
        float H = v.rh;
        float d = v.d;
        close(v, c, t, 0.84f);
        v.drawReveal(c, 1f, 1f, 1f);
        float shake = t < 0.22f ? 1f : 0f;
        float lift = cl((t - 0.2f) / 0.55f);
        float dy = -lift * lift * (H + 150 * d);
        float dxs = shake * (float) Math.sin(t * 160) * 2.4f * d;
        float cx = v.cdx + W / 2f;
        // smoke puffs left behind
        q.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 14; i++) {
            float born = 0.2f + i * 0.035f;
            float lt = cl((t - born) / 0.4f);
            if (lt <= 0 || lt >= 1) continue;
            float bl = cl((born - 0.2f) / 0.55f);
            float by = H - bl * bl * (H + 150 * d) + 6 * d;
            q.setColor(0xFFECEFF1);
            q.setAlpha((int) (150 * (1f - lt)));
            c.drawCircle(cx + (v.rnd[i] - 0.5f) * W * 0.5f, by + lt * 10 * d, (8 + 16 * lt) * d, q);
        }
        q.setAlpha(255);
        // flame under the row
        float fl = shake > 0 ? 0.25f + 0.2f * (float) Math.sin(t * 90) : 1f;
        float flameLen = (shake > 0 ? 22 : 18 + 80 * lift) * d * (0.8f + 0.2f * fl);
        float fy = H + dy;
        path.reset();
        path.moveTo(cx - 24 * d, fy);
        path.quadTo(cx, fy + flameLen * 1.6f, cx + 24 * d, fy);
        path.close();
        q.setShader(new LinearGradient(0, fy, 0, fy + flameLen, new int[]{0xFFFFFFFF, 0xFFFFD740, 0xFFFF6D00, 0x00FF6D00},
                null, Shader.TileMode.CLAMP));
        c.drawPath(path, q);
        q.setShader(null);
        c.save();
        c.translate(dxs, dy);
        c.translate(v.cdx, 0);
        v.drawRow(c, 255, 0);
        c.restore();
    }

    // ------------------------------------------------------------------ 7 slice

    private static void slice(LabSwipe v, Canvas c, float t) {
        float W = v.rw;
        float H = v.rh;
        float d = v.d;
        close(v, c, t, 0.86f);
        v.drawReveal(c, 1f, 1f, 1f);
        if (v.snap == null) return;
        float cx = v.cdx + W / 2f;
        float cyy = H / 2f;
        double th = Math.toRadians(-18);
        float dxl = (float) Math.cos(th);
        float dyl = (float) Math.sin(th);
        float nx = -dyl;
        float ny = dxl; // normal of the cut
        float slash = cl(t / 0.14f);
        float split = ease(cl((t - 0.16f) / 0.7f));
        float L = W;
        for (int side = -1; side <= 1; side += 2) {
            c.save();
            float fall = split * split * 90 * d;
            c.translate(nx * side * split * 26 * d, ny * side * split * 26 * d + (side > 0 ? fall : fall * 0.4f));
            c.rotate(side * split * 14f, cx, cyy);
            path.reset();
            path.moveTo(cx - dxl * L, cyy - dyl * L);
            path.lineTo(cx + dxl * L, cyy + dyl * L);
            path.lineTo(cx + dxl * L + nx * side * L, cyy + dyl * L + ny * side * L);
            path.lineTo(cx - dxl * L + nx * side * L, cyy - dyl * L + ny * side * L);
            path.close();
            c.clipPath(path);
            rf(v.cdx, 0, v.cdx + W, H);
            piece(c, v.snap, 0, 0, v.snap.getWidth(), v.snap.getHeight(), r1, (int) (255 * (1f - cl((split - 0.7f) / 0.3f))), bp);
            c.restore();
        }
        // the slash itself
        float a = cl(t / 0.14f);
        float fade = 1f - cl((t - 0.14f) / 0.25f);
        if (fade > 0) {
            q.setStyle(Paint.Style.STROKE);
            q.setStrokeCap(Paint.Cap.ROUND);
            float x0 = cx - dxl * W * 0.55f;
            float y0 = cyy - dyl * W * 0.55f;
            float x1 = lerp(x0, cx + dxl * W * 0.55f, slash);
            float y1 = lerp(y0, cyy + dyl * W * 0.55f, slash);
            q.setStrokeWidth(8 * d);
            q.setColor(0xFFFFFFFF);
            q.setAlpha((int) (70 * fade));
            c.drawLine(x0, y0, x1, y1, q);
            q.setStrokeWidth(2.4f * d);
            q.setAlpha((int) (255 * fade));
            c.drawLine(x0, y0, x1, y1, q);
            q.setAlpha(255);
            q.setStyle(Paint.Style.FILL);
        }
    }

    // ------------------------------------------------------------------ 8 flip away

    private static void flip(LabSwipe v, Canvas c, float t) {
        float W = v.rw;
        float H = v.rh;
        float d = v.d;
        close(v, c, t, 0.82f);
        v.drawReveal(c, 1f, 1f, 1f);
        float u = ease(cl(t / 0.62f));
        float th = u * (float) Math.PI * 0.5f;
        float sy = Math.max(0.005f, (float) Math.cos(th));
        c.save();
        c.translate(v.cdx + W / 2f, 0); // hinge on the top edge
        c.scale(1f - 0.2f * (float) Math.sin(th), sy);
        c.translate(-W / 2f, 0);
        v.drawRow(c, 255, 0);
        q.setStyle(Paint.Style.FILL);
        q.setColor(0xFF000000);
        q.setAlpha((int) (150 * u));
        rf(0, 0, W, H);
        c.drawRoundRect(r1, 20 * d, 20 * d, q);
        q.setAlpha(255);
        c.restore();
        // a soft shadow on the ground where the page was
        q.setColor(0xFF000000);
        q.setAlpha((int) (60 * (1f - u)));
        rf(v.cdx + 10 * d, H - 6 * d, v.cdx + W - 10 * d, H);
        c.drawOval(r1, q);
        q.setAlpha(255);
    }

    // ------------------------------------------------------------------ 9 genie

    private static void genie(LabSwipe v, Canvas c, float t) {
        float W = v.rw;
        float H = v.rh;
        float d = v.d;
        close(v, c, t, 0.88f);
        float tx = W - 38 * d;
        float ty = H / 2f;
        float pulse = 1f + 0.3f * (float) Math.sin(Math.PI * cl((t - 0.62f) / 0.22f));
        v.drawReveal(c, 1f, 1f - cl((t - 0.7f) / 0.15f), pulse);
        if (v.snap == null) return;
        int n = 18;
        float sh = H / n;
        for (int i = 0; i < n; i++) {
            float delay = i / (float) n * 0.22f;
            float g = ease(cl((t - delay) / 0.55f));
            float yi = i * sh;
            float x0 = lerp(v.cdx, tx - 2 * d, g);
            float wd = lerp(W, 4 * d, g);
            float y0 = lerp(yi, ty, g * g);
            float hh = lerp(sh, 0.6f * d, g);
            rf(x0, y0, x0 + wd, y0 + hh + 0.6f);
            piece(c, v.snap, 0, Math.round(yi), v.snap.getWidth(), Math.round(yi + sh), r1,
                    (int) (255 * (1f - cl((g - 0.8f) / 0.2f))), bp);
        }
    }

    // ------------------------------------------------------------------ 10 melt

    private static void melt(LabSwipe v, Canvas c, float t) {
        float W = v.rw;
        float H = v.rh;
        float d = v.d;
        close(v, c, t, 0.86f);
        v.drawReveal(c, 1f, 1f, 1f);
        if (v.snap == null) return;
        int n = 26;
        float sw = W / n;
        for (int i = 0; i < n; i++) {
            float delay = v.rnd[i] * 0.3f;
            float lt = cl((t - delay) / 0.62f);
            float top = lt * lt * H * (0.25f + 0.5f * v.rnd[i + 30]);
            float bot = H + lt * H * (0.3f + 1.2f * v.rnd[i + 60]);
            rf(v.cdx + i * sw, top, v.cdx + (i + 1) * sw + 0.6f, bot);
            piece(c, v.snap, Math.round(i * sw), 0, Math.round((i + 1) * sw), v.snap.getHeight(), r1,
                    (int) (255 * (1f - lt * lt)), bp);
        }
        float pu = cl((t - 0.2f) / 0.6f);
        q.setStyle(Paint.Style.FILL);
        q.setColor(0xFF000000);
        q.setAlpha((int) (70 * pu * (1f - cl((t - 0.82f) / 0.18f))));
        rf(v.cdx + W * 0.5f - W * 0.45f * pu, H - 5 * d, v.cdx + W * 0.5f + W * 0.45f * pu, H + 6 * d);
        c.drawOval(r1, q);
        q.setAlpha(255);
    }

    // ------------------------------------------------------------------ 11 bubbles

    private static void bubbles(LabSwipe v, Canvas c, float t) {
        float W = v.rw;
        float H = v.rh;
        float d = v.d;
        close(v, c, t, 0.88f);
        v.drawReveal(c, 1f, 1f, 1f);
        float fade = cl(t / 0.45f);
        if (fade < 1f) {
            c.save();
            c.translate(v.cdx, 0);
            v.drawRow(c, (int) (255 * (1f - fade)), 0);
            c.restore();
        }
        q.setStyle(Paint.Style.STROKE);
        for (int i = 0; i < 20; i++) {
            float born = v.rnd[i] * 0.3f;
            float lt = cl((t - born) / 0.6f);
            if (lt <= 0) continue;
            float x0 = v.cdx + (0.05f + 0.9f * v.rnd[i + 20]) * W;
            float y0 = H * (0.2f + 0.6f * v.rnd[i + 40]);
            float x = x0 + (float) Math.sin(lt * 6 + i) * 6 * d;
            float y = y0 - lt * (14 + 16 * v.rnd[i + 60]) * d;
            float rad = (6 + 10 * v.rnd[i + 70]) * d;
            float hue = (i * 47) % 360;
            int col = Color.HSVToColor(new float[]{hue, 0.35f, 1f});
            if (lt < 0.78f) {
                q.setStrokeWidth(1.6f * d);
                q.setColor(col);
                q.setAlpha(220);
                c.drawCircle(x, y, rad, q);
                q.setStyle(Paint.Style.FILL);
                q.setColor(0xFFFFFFFF);
                q.setAlpha(120);
                c.drawCircle(x - rad * 0.35f, y - rad * 0.35f, rad * 0.22f, q);
                q.setStyle(Paint.Style.STROKE);
            } else { // pop: a thin ring and droplets
                float pp = (lt - 0.78f) / 0.22f;
                q.setStrokeWidth(1.2f * d);
                q.setColor(col);
                q.setAlpha((int) (200 * (1f - pp)));
                c.drawCircle(x, y, rad * (1f + pp * 0.7f), q);
                q.setStyle(Paint.Style.FILL);
                for (int j = 0; j < 6; j++) {
                    double a = j * Math.PI / 3;
                    c.drawCircle(x + (float) Math.cos(a) * rad * (1f + pp * 1.2f),
                            y + (float) Math.sin(a) * rad * (1f + pp * 1.2f), 1.3f * d, q);
                }
                q.setStyle(Paint.Style.STROKE);
            }
        }
        q.setAlpha(255);
        q.setStyle(Paint.Style.FILL);
    }

    // ------------------------------------------------------------------ 12 lightning

    private static void lightning(LabSwipe v, Canvas c, float t) {
        float W = v.rw;
        float H = v.rh;
        float d = v.d;
        close(v, c, t, 0.88f);
        v.drawReveal(c, 1f, 1f, 1f);
        int frame = (int) (t * 16);
        boolean flick = (frame % 2 == 0) && t < 0.55f;
        float fadeOut = cl((t - 0.55f) / 0.3f);
        if (fadeOut < 1f) {
            c.save();
            c.translate(v.cdx, 0);
            v.drawRow(c, (int) (255 * (flick ? 0.55f : 1f) * (1f - fadeOut)), 0);
            c.restore();
            // white-hot overlay
            if (t > 0.3f) {
                q.setStyle(Paint.Style.FILL);
                q.setColor(0xFFFFFFFF);
                q.setAlpha((int) (200 * cl((t - 0.3f) / 0.25f) * (1f - fadeOut)));
                rf(v.cdx, 0, v.cdx + W, H);
                c.drawRoundRect(r1, 20 * d, 20 * d, q);
            }
        }
        if (t < 0.6f) {
            int seed = frame * 13;
            for (int b = 0; b < 2; b++) {
                float x = v.cdx + (0.15f + 0.7f * v.rnd[(seed + b * 7) % 90]) * W;
                path.reset();
                path.moveTo(x, -10 * d);
                float y = -10 * d;
                int seg = 0;
                while (y < H + 10 * d) {
                    y += (12 + 9 * v.rnd[(seed + seg + b * 20) % 90]) * d;
                    x += (v.rnd[(seed + seg * 3 + b * 11) % 90] - 0.5f) * 30 * d;
                    path.lineTo(x, y);
                    seg++;
                }
                q.setStyle(Paint.Style.STROKE);
                q.setStrokeJoin(Paint.Join.ROUND);
                q.setStrokeWidth(7 * d);
                q.setColor(0xFF7C4DFF);
                q.setAlpha(110);
                c.drawPath(path, q);
                q.setStrokeWidth(2.4f * d);
                q.setColor(0xFFFFFFFF);
                q.setAlpha(255);
                c.drawPath(path, q);
            }
            q.setStyle(Paint.Style.FILL);
        }
        // sparks when it vanishes
        float sp = cl((t - 0.55f) / 0.4f);
        for (int i = 0; i < 16 && sp > 0 && sp < 1; i++) {
            double a = v.rnd[i] * Math.PI * 2;
            float r = sp * (30 + 60 * v.rnd[i + 16]) * d;
            q.setColor(0xFFFFFFFF);
            q.setAlpha((int) (255 * (1f - sp)));
            c.drawCircle(v.cdx + W / 2f + (float) Math.cos(a) * r * 2f, H / 2f + (float) Math.sin(a) * r, 1.8f * d, q);
        }
        q.setAlpha(255);
    }

    // ------------------------------------------------------------------ 13 confetti

    private static void confetti(LabSwipe v, Canvas c, float t) {
        float W = v.rw;
        float H = v.rh;
        float d = v.d;
        close(v, c, t, 0.88f);
        v.drawReveal(c, 1f, 1f, 1f);
        float rowA = 1f - ease(cl(t / 0.25f));
        if (rowA > 0) {
            c.save();
            c.translate(v.cdx + W / 2f, H / 2f);
            float s = 1f + 0.06f * (1f - rowA);
            c.scale(s, s);
            c.translate(-W / 2f, -H / 2f);
            v.drawRow(c, (int) (255 * rowA), 0);
            c.restore();
        }
        float lt = cl((t - 0.04f) / 0.8f);
        q.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 48; i++) {
            float ang = (v.rnd[i] - 0.5f) * 2.6f - 1.57f;
            float sp = (90 + 190 * v.rnd[(i + 48) % 96]) * d;
            float x = v.cdx + W / 2f + (float) Math.cos(ang) * sp * lt * 1.4f;
            float y = H / 2f + (float) Math.sin(ang) * sp * lt * 0.9f + lt * lt * 170 * d;
            float flutter = (float) Math.cos(lt * 18 + i);
            q.setColor(PAL[i % PAL.length]);
            q.setAlpha((int) (255 * (1f - lt * lt)));
            c.save();
            c.translate(x, y);
            c.rotate(lt * 540 * (v.rnd[(i + 5) % 90] - 0.5f));
            rf(-3.5f * d, -2f * d * flutter, 3.5f * d, 2f * d * flutter);
            c.drawRect(r1, q);
            c.restore();
        }
        q.setAlpha(255);
    }

    // ------------------------------------------------------------------ 14 stamp

    private static void stamp(LabSwipe v, Canvas c, float t) {
        float W = v.rw;
        float H = v.rh;
        float d = v.d;
        close(v, c, t, 0.9f);
        v.drawReveal(c, 1f, 1f, 1f);
        float away = cl((t - 0.58f) / 0.3f);
        float shiftX = v.cdx - away * away * (W + 40 * d + v.cdx);
        float hit = cl((t - 0.12f) / 0.16f); // stamp comes down 3.4x -> 1x
        float scale = hit < 1f ? lerp(3.4f, 1f, hit * hit) : 1f + 0.08f * (float) Math.sin(Math.PI * cl((t - 0.28f) / 0.12f));
        float jolt = t > 0.28f && t < 0.4f ? (float) Math.sin((t - 0.28f) / 0.12f * Math.PI * 3) * 2.5f * d : 0f;
        c.save();
        c.translate(shiftX, jolt);
        v.drawRow(c, 255, 0);
        if (hit > 0.02f) {
            c.save();
            c.translate(W / 2f, H / 2f);
            c.rotate(-12f);
            c.scale(scale, scale);
            q.setStyle(Paint.Style.STROKE);
            q.setStrokeWidth(3.2f * d);
            q.setColor(0xFFD32F2F);
            q.setAlpha((int) (235 * Math.min(1f, hit * 2f)));
            rf(-W * 0.3f, -H * 0.3f, W * 0.3f, H * 0.3f);
            c.drawRoundRect(r1, 6 * d, 6 * d, q);
            q.setStyle(Paint.Style.FILL);
            q.setTypeface(Typeface.create(Fonts.face(true), Typeface.BOLD));
            q.setTextAlign(Paint.Align.CENTER);
            q.setTextSize(26 * d);
            c.drawText("DELETED", 0, -(q.ascent() + q.descent()) / 2f, q);
            q.setAlpha(255);
            c.restore();
        }
        c.restore();
        // impact ring
        float ring = cl((t - 0.26f) / 0.25f);
        if (ring > 0 && ring < 1) {
            q.setStyle(Paint.Style.STROKE);
            q.setStrokeWidth(2.4f * d);
            q.setColor(0xFFFFFFFF);
            q.setAlpha((int) (200 * (1f - ring)));
            c.drawCircle(W / 2f, H / 2f, ring * W * 0.5f, q);
            q.setAlpha(255);
            q.setStyle(Paint.Style.FILL);
        }
    }

    // ------------------------------------------------------------------ 15 eraser

    private static void eraser(LabSwipe v, Canvas c, float t) {
        float W = v.rw;
        float H = v.rh;
        float d = v.d;
        close(v, c, t, 0.88f);
        v.drawReveal(c, 1f, 1f, 1f);
        float u = ease(cl(t / 0.74f));
        float ew = 46 * d;
        float ex = lerp(v.cdx - ew, v.cdx + W, u);
        c.save();
        c.clipRect(Math.max(0f, ex + ew * 0.35f), 0, W + Math.max(0f, v.cdx), H);
        c.save();
        c.translate(v.cdx, 0);
        v.drawRow(c, 255, 0);
        c.restore();
        c.restore();
        if (u < 1f) { // the eraser: pink body, blue sleeve
            q.setStyle(Paint.Style.FILL);
            q.setColor(0xFFFF8A9B);
            rf(ex, 5 * d, ex + ew * 0.7f, H - 5 * d);
            c.drawRoundRect(r1, 8 * d, 8 * d, q);
            q.setColor(0xFF4F7CFF);
            rf(ex + ew * 0.45f, 5 * d, ex + ew, H - 5 * d);
            c.drawRoundRect(r1, 8 * d, 8 * d, q);
            q.setColor(0x55FFFFFF);
            rf(ex + 4 * d, 8 * d, ex + ew * 0.5f, 14 * d);
            c.drawRoundRect(r1, 3 * d, 3 * d, q);
        }
        // crumbs
        q.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 26; i++) {
            float born = v.rnd[i] * 0.7f;
            float lt = cl((t - born) / 0.3f);
            if (lt <= 0 || lt >= 1) continue;
            float bx = lerp(v.cdx - ew, v.cdx + W, ease(cl(born / 0.74f)));
            q.setColor(i % 2 == 0 ? 0xFFFF8A9B : 0xFFB0BEC5);
            q.setAlpha((int) (230 * (1f - lt)));
            rf(bx + (v.rnd[i + 30] - 0.3f) * 14 * d, v.rnd[i + 60] * H + lt * lt * 40 * d,
                    bx + (v.rnd[i + 30] - 0.3f) * 14 * d + 3.4f * d, v.rnd[i + 60] * H + lt * lt * 40 * d + 2.2f * d);
            c.drawRect(r1, q);
        }
        q.setAlpha(255);
    }

    // ------------------------------------------------------------------ 16 spin out

    private static void spin(LabSwipe v, Canvas c, float t) {
        float W = v.rw;
        float H = v.rh;
        close(v, c, t, 0.88f);
        v.drawReveal(c, 1f, 1f, 1f);
        float u = cl(t / 0.78f);
        for (int g = 4; g >= 0; g--) { // faint copies behind show the path
            float uu = cl(u - g * 0.04f);
            float e = uu * uu;
            float cx = lerp(v.cdx + W / 2f, -W * 0.25f, e);
            float cy = lerp(H / 2f, -H * 1.1f, e) + (float) Math.sin(uu * Math.PI) * H * 0.9f;
            float s = lerp(1f, 0.2f, e);
            c.save();
            c.translate(cx, cy);
            c.rotate(-720f * e);
            c.scale(s, s);
            c.translate(-W / 2f, -H / 2f);
            v.drawRow(c, g == 0 ? (int) (255 * (1f - cl((u - 0.8f) / 0.2f))) : 70 - g * 12, 0);
            c.restore();
        }
    }

    // ------------------------------------------------------------------ 17 dominoes

    private static void domino(LabSwipe v, Canvas c, float t) {
        float W = v.rw;
        float H = v.rh;
        float d = v.d;
        close(v, c, t, 0.88f);
        v.drawReveal(c, 1f, 1f, 1f);
        if (v.snap == null) return;
        int n = 10;
        float bw = W / n;
        for (int i = 0; i < n; i++) {
            float st = 0.04f + i * 0.065f;
            float lt = cl((t - st) / 0.26f);
            float ang = (lt * lt) * 92f;
            float x1 = v.cdx + (i + 1) * bw;
            c.save();
            c.rotate(ang, x1, H);
            rf(v.cdx + i * bw, 0, x1, H);
            piece(c, v.snap, Math.round(i * bw), 0, Math.round((i + 1) * bw), v.snap.getHeight(), r1,
                    (int) (255 * (1f - cl((lt - 0.85f) / 0.15f))), bp);
            c.restore();
        }
    }

    // ------------------------------------------------------------------ 18 pixelate

    private static void pixelate(LabSwipe v, Canvas c, float t) {
        float W = v.rw;
        float H = v.rh;
        float d = v.d;
        close(v, c, t, 0.9f);
        v.drawReveal(c, 1f, 1f, 1f);
        if (v.snap == null) return;
        if (v.mos == null) {
            int[] blocks = {3, 6, 10, 16, 26};
            v.mos = new Bitmap[blocks.length];
            for (int i = 0; i < blocks.length; i++) {
                int bw = Math.max(1, Math.round(v.snap.getWidth() / (blocks[i] * d)));
                int bh = Math.max(1, Math.round(v.snap.getHeight() / (blocks[i] * d)));
                v.mos[i] = Bitmap.createScaledBitmap(v.snap, bw, bh, true);
            }
        }
        float u = cl(t / 0.6f);
        int level = Math.min(v.mos.length - 1, (int) (u * v.mos.length));
        float fade = 1f - cl((t - 0.62f) / 0.28f);
        rf(v.cdx, 0, v.cdx + W, H);
        if (level == 0 && t < 0.08f) {
            piece(c, v.snap, 0, 0, v.snap.getWidth(), v.snap.getHeight(), r1, 255, bp);
        } else {
            Bitmap m = v.mos[level];
            // shrink a little and drift while it degrades
            c.save();
            c.translate(v.cdx + W / 2f, H / 2f);
            float s = 1f - 0.08f * ease(cl((t - 0.5f) / 0.4f));
            c.scale(s, s);
            c.translate(-W / 2f - v.cdx, -H / 2f);
            rf(v.cdx, 0, v.cdx + W, H);
            piece(c, m, 0, 0, m.getWidth(), m.getHeight(), r1, (int) (255 * fade), nf);
            c.restore();
        }
        // pixels that break away at the end
        float dl = cl((t - 0.6f) / 0.3f);
        q.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 30 && dl > 0; i++) {
            float x = v.cdx + v.rnd[i] * W;
            float y = v.rnd[i + 30] * H;
            q.setColor(Pal.card);
            q.setAlpha((int) (255 * (1f - dl)));
            float sz = 5 * d;
            rf(x + dl * 40 * d * (v.rnd[i + 60] - 0.5f), y - dl * 30 * d, x + sz + dl * 40 * d * (v.rnd[i + 60] - 0.5f),
                    y + sz - dl * 30 * d);
            c.drawRect(r1, q);
        }
        q.setAlpha(255);
    }

    // ------------------------------------------------------------------ 19 portal

    private static void portal(LabSwipe v, Canvas c, float t) {
        float W = v.rw;
        float H = v.rh;
        float d = v.d;
        close(v, c, t, 0.9f);
        v.drawReveal(c, 1f, 1f, 1f);
        if (v.snap == null) return;
        float open = ease(cl(t / 0.15f)) * (1f - ease(cl((t - 0.78f) / 0.18f)));
        float px = 12 * d;
        float pw = 16 * d * open;
        float ph = H * 0.46f * open;
        float u = ease(cl((t - 0.1f) / 0.7f));
        float shift = u * (v.cdx + W - px + 10 * d);
        int n = 26;
        float sw = W / n;
        for (int i = 0; i < n; i++) {
            float x = v.cdx + i * sw - shift;
            if (x + sw < px) continue;
            float f = cl((x - px) / (60 * d));
            float hh = H * (0.08f + 0.92f * f);
            rf(x, H / 2f - hh / 2f, x + sw + 0.6f, H / 2f + hh / 2f);
            piece(c, v.snap, Math.round(i * sw), 0, Math.round((i + 1) * sw), v.snap.getHeight(), r1,
                    (int) (255 * (0.25f + 0.75f * f)), bp);
        }
        if (open > 0.02f) {
            q.setStyle(Paint.Style.FILL);
            q.setShader(new RadialGradient(px, H / 2f, ph + 18 * d, 0x887C4DFF, 0x007C4DFF, Shader.TileMode.CLAMP));
            c.drawCircle(px, H / 2f, ph + 18 * d, q);
            q.setShader(null);
            q.setStyle(Paint.Style.STROKE);
            q.setStrokeWidth(3.4f * d);
            q.setColor(0xFFB388FF);
            rf(px - pw, H / 2f - ph, px + pw, H / 2f + ph);
            c.drawOval(r1, q);
            q.setStrokeWidth(1.6f * d);
            q.setColor(0xFF40C4FF);
            q.setAlpha(220);
            rf(px - pw * 0.6f, H / 2f - ph * 0.82f, px + pw * 0.6f, H / 2f + ph * 0.82f);
            c.drawArc(r1, t * 720f, 120f, false, q);
            c.drawArc(r1, t * 720f + 180f, 120f, false, q);
            q.setAlpha(255);
            q.setStyle(Paint.Style.FILL);
        }
        float sp = cl((t - 0.78f) / 0.2f);
        for (int i = 0; i < 14 && sp > 0 && sp < 1; i++) {
            double a = v.rnd[i] * Math.PI * 2;
            q.setColor(i % 2 == 0 ? 0xFFB388FF : 0xFF40C4FF);
            q.setAlpha((int) (255 * (1f - sp)));
            c.drawCircle(px + (float) Math.cos(a) * sp * 34 * d, H / 2f + (float) Math.sin(a) * sp * 34 * d, 2f * d, q);
        }
        q.setAlpha(255);
    }
}
