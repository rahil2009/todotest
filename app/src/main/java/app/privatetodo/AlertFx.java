package app.privatetodo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

import java.util.Random;

/**
 * Animated backdrops for the full-screen alert. Ten styles (pick one in Settings): edge glow, aurora, waves,
 * mesh, liquid, beams, warp, grid, ribbons and orbit. All time-based and drawn with plain canvas calls, so they run smoothly and
 * stop the moment the screen closes. They never take touches.
 */
final class AlertFx extends View {
    static final int OFF = 0;
    static final int EDGE = 1;
    static final int AURORA = 2;
    static final int WAVES = 3;
    static final int MESH = 4;
    static final int LIQUID = 5;
    static final int BEAMS = 6;
    static final int WARP = 7;
    static final int GRID = 8;
    static final int RIBBONS = 9;
    static final int ORBIT = 10;

    static final String[] NAMES = {"Off", "Edge glow", "Aurora", "Waves", "Mesh", "Liquid", "Beams", "Warp",
            "Grid", "Ribbons", "Orbit"};

    /** The view for a style, or null for Off. */
    static View create(Context c, int mode) {
        if (mode == EDGE) return new GlowView(c);
        if (mode >= AURORA && mode <= ORBIT) return new AlertFx(c, mode);
        return null;
    }

    private final int mode;
    private final float d;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Matrix m = new Matrix();
    private final RectF rect = new RectF();
    private long t0;
    private RadialGradient blob;
    private RadialGradient core;
    private final float[] sx = new float[44];
    private final float[] sy = new float[44];
    private final float[] ss = new float[44];
    private final float[] sp = new float[44];
    private final int[] sc = new int[44];

    private AlertFx(Context c, int mode) {
        super(c);
        this.mode = mode;
        d = c.getResources().getDisplayMetrics().density;
        setClickable(false);
        setFocusable(false);
        Random r = new Random(7);
        for (int i = 0; i < sx.length; i++) {
            sx[i] = r.nextFloat();
            sy[i] = r.nextFloat();
            ss[i] = 0.025f + r.nextFloat() * 0.055f; // rise speed, screens per second
            sp[i] = r.nextFloat() * 6.28f;
            sc[i] = GlowView.COLORS[r.nextInt(GlowView.COLORS.length - 1)];
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        t0 = System.nanoTime();
        postInvalidateOnAnimation();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        float r = Math.max(w, h) * 0.55f;
        blob = new RadialGradient(0, 0, r, new int[]{0xFFFFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP);
        core = new RadialGradient(0, 0, w * 0.55f, new int[]{0xFFFFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP);
    }

    private static int mix(int a, int b, float t) {
        return HabitUi.blend(b, a, t);
    }

    /** Colour along the palette for a position that keeps cycling. */
    private static int pal(float x) {
        int n = GlowView.COLORS.length - 1;
        x = ((x % n) + n) % n;
        int i = (int) x;
        return mix(GlowView.COLORS[i], GlowView.COLORS[i + 1], x - i);
    }

    @Override
    protected void onDraw(Canvas c) {
        if (getWidth() == 0) return;
        float t = (System.nanoTime() - t0) / 1e9f;
        switch (mode) {
            case AURORA:
                aurora(c, t);
                break;
            case MESH:
                mesh(c, t);
                break;
            case LIQUID:
                liquid(c, t);
                break;
            case BEAMS:
                beams(c, t);
                break;
            case WARP:
                warp(c, t);
                break;
            case GRID:
                grid(c, t);
                break;
            case RIBBONS:
                ribbons(c, t);
                break;
            case ORBIT:
                orbit(c, t);
                break;
            default:
                waves(c, t);
                break;
        }
        postInvalidateOnAnimation();
    }

    private void aurora(Canvas c, float t) {
        float w = getWidth();
        float h = getHeight();
        Paint fill = p;
        fill.setStyle(Paint.Style.FILL);
        fill.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SCREEN));
        for (int i = 0; i < 4; i++) {
            float x = w * (0.5f + 0.40f * (float) Math.sin(t * (0.19f + i * 0.045f) + i * 1.7f));
            float y = h * (0.5f + 0.38f * (float) Math.cos(t * (0.15f + i * 0.05f) + i * 2.3f));
            float s = 0.75f + 0.25f * (float) Math.sin(t * 0.6f + i);
            m.setScale(s, s);
            m.postTranslate(x, y);
            blob.setLocalMatrix(m);
            fill.setShader(blob);
            fill.setColorFilter(new android.graphics.PorterDuffColorFilter(
                    HabitUi.alpha(pal(i * 1.25f + t * 0.12f), 0x80), PorterDuff.Mode.SRC_IN));
            c.drawCircle(x, y, Math.max(w, h) * 0.55f * s, fill);
        }
        fill.setColorFilter(null);
        fill.setShader(null);
        fill.setXfermode(null);
    }

    private void waves(Canvas c, float t) {
        float w = getWidth();
        float h = getHeight();
        p.setStyle(Paint.Style.FILL);
        for (int layer = 0; layer < 3; layer++) {
            float base = h * (0.80f + layer * 0.045f);
            float amp = (14 + layer * 6) * d * (0.8f + 0.2f * (float) Math.sin(t * 0.8f + layer));
            float k = (2f + layer * 0.7f) * (float) Math.PI / w;
            float sp = t * (0.9f + layer * 0.35f) * (layer % 2 == 0 ? 1 : -1);
            path.reset();
            path.moveTo(0, h);
            for (float x = 0; x <= w + 12 * d; x += 12 * d) {
                path.lineTo(x, base + amp * (float) Math.sin(x * k + sp));
            }
            path.lineTo(w, h);
            path.close();
            int c1 = pal(layer * 1.4f + t * 0.15f);
            int c2 = pal(layer * 1.4f + 1.6f + t * 0.15f);
            p.setShader(new LinearGradient(0, base - amp, 0, h,
                    HabitUi.alpha(c1, 0x66 - layer * 0x14), HabitUi.alpha(c2, 0x10), Shader.TileMode.CLAMP));
            c.drawPath(path, p);
        }
        p.setShader(null);
    }

    // ---- Mesh: big saturated blobs drifting over each other, with a fine film grain on top
    private void mesh(Canvas c, float t) {
        float w = getWidth();
        float h = getHeight();
        p.setStyle(Paint.Style.FILL);
        float r = Math.max(w, h) * 0.62f;
        for (int i = 0; i < 5; i++) {
            float x = w * (0.5f + 0.46f * (float) Math.sin(t * (0.11f + i * 0.037f) + i * 2.1f));
            float y = h * (0.5f + 0.46f * (float) Math.cos(t * (0.09f + i * 0.043f) + i * 1.3f));
            int col = pal(i * 1.0f + t * 0.06f);
            p.setShader(new RadialGradient(x, y, r, new int[]{HabitUi.alpha(col, 0xB0), HabitUi.alpha(col, 0x00)},
                    null, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, p);
        }
        // a soft vignette keeps the clock readable
        p.setShader(new RadialGradient(w / 2f, h * 0.35f, Math.max(w, h) * 0.75f,
                new int[]{0x00000000, 0x99000000}, null, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
        for (int i = 0; i < sx.length; i++) { // grain: twinkling pin-pricks that never move
            float a = 0.5f + 0.5f * (float) Math.sin(t * (0.8f + ss[i] * 10f) + sp[i]);
            p.setColor(HabitUi.alpha(0xFFFFFFFF, Math.round(60 * a)));
            c.drawCircle(sx[i] * w, sy[i] * h, 1.2f * d, p);
        }
    }

    // ---- Liquid: metaballs that melt into each other
    private Bitmap goo;
    private Canvas gooCanvas;
    private final Paint gooPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint gooBlob = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect gooSrc = new Rect();
    private final Rect gooDst = new Rect();

    private void liquid(Canvas c, float t) {
        float w = getWidth();
        float h = getHeight();
        int gw = Math.max(24, Math.round(w / 7f));
        int gh = Math.max(24, Math.round(h / 7f));
        if (goo == null || goo.getWidth() != gw || goo.getHeight() != gh) {
            if (goo != null) goo.recycle();
            goo = Bitmap.createBitmap(gw, gh, Bitmap.Config.ARGB_8888);
            gooCanvas = new Canvas(goo);
            gooPaint.setColorFilter(new ColorMatrixColorFilter(new float[]{
                    1, 0, 0, 0, 0,
                    0, 1, 0, 0, 0,
                    0, 0, 1, 0, 0,
                    0, 0, 0, 16, -16 * 110}));
        }
        goo.eraseColor(0);
        gooBlob.setStyle(Paint.Style.FILL);
        int n = 6;
        for (int i = 0; i < n; i++) {
            float fx = 0.5f + 0.34f * (float) Math.sin(t * (0.23f + i * 0.051f) + i * 1.9f);
            float fy = 0.5f + 0.42f * (float) Math.cos(t * (0.17f + i * 0.047f) + i * 2.7f);
            float rad = gw * (0.22f + 0.05f * (float) Math.sin(t * 0.5f + i));
            int col = pal(i * 0.9f + t * 0.1f);
            gooBlob.setShader(new RadialGradient(fx * gw, fy * gh, rad,
                    new int[]{col, HabitUi.alpha(col, 0)}, null, Shader.TileMode.CLAMP));
            gooCanvas.drawCircle(fx * gw, fy * gh, rad, gooBlob);
        }
        gooSrc.set(0, 0, gw, gh);
        gooDst.set(0, 0, (int) w, (int) h);
        gooPaint.setAlpha(150);
        c.drawBitmap(goo, gooSrc, gooDst, gooPaint);
        gooPaint.setAlpha(255);
    }

    // ---- Beams: stage-light shafts fanning from above, swaying slowly
    private void beams(Canvas c, float t) {
        float w = getWidth();
        float h = getHeight();
        p.setStyle(Paint.Style.FILL);
        p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SCREEN));
        float ox = w / 2f;
        float oy = -h * 0.08f;
        float len = h * 1.25f;
        for (int i = 0; i < 7; i++) {
            float base = -62f + i * (124f / 6f);
            float ang = base + 9f * (float) Math.sin(t * (0.35f + i * 0.06f) + i * 1.4f);
            float spread = 5.5f + 2.5f * (float) Math.sin(t * 0.5f + i * 0.9f);
            double a1 = Math.toRadians(90 + ang - spread);
            double a2 = Math.toRadians(90 + ang + spread);
            path.reset();
            path.moveTo(ox, oy);
            path.lineTo(ox + (float) Math.cos(a1) * len, oy + (float) Math.sin(a1) * len);
            path.lineTo(ox + (float) Math.cos(a2) * len, oy + (float) Math.sin(a2) * len);
            path.close();
            int col = pal(i * 0.85f + t * 0.08f);
            float flick = 0.65f + 0.35f * (float) Math.sin(t * 0.9f + i * 2f);
            p.setShader(new LinearGradient(ox, oy, ox, oy + len, HabitUi.alpha(col, Math.round(130 * flick)),
                    HabitUi.alpha(col, 0), Shader.TileMode.CLAMP));
            c.drawPath(path, p);
        }
        p.setShader(new RadialGradient(ox, 0, w * 0.6f, new int[]{0x66FFFFFF, 0x00FFFFFF}, null,
                Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h * 0.5f, p);
        p.setShader(null);
        p.setXfermode(null);
    }

    // ---- Warp: stars streaking out of the clock like a jump to hyperspace
    private void warp(Canvas c, float t) {
        float w = getWidth();
        float h = getHeight();
        float cx = w / 2f;
        float cy = h * 0.30f;
        float reach = (float) Math.hypot(Math.max(cx, w - cx), Math.max(cy, h - cy));
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < sx.length; i++) {
            for (int k = 0; k < 2; k++) {
                float ang = (sx[i] + k * 0.5f) * 6.2832f * 1.0f + sp[i];
                float prog = (sy[i] + t * (0.10f + ss[i] * 2.2f) + k * 0.37f) % 1f;
                float e = prog * prog;
                float r1 = e * reach;
                float r0 = Math.max(0f, (e - (0.02f + 0.16f * e)) * reach);
                float a = Math.min(1f, prog * 4f) * (1f - prog * 0.35f);
                p.setStrokeWidth((0.8f + e * 2.6f) * d);
                p.setColor(HabitUi.alpha(k == 0 ? 0xFFFFFFFF : sc[i], Math.round(230 * a)));
                float co = (float) Math.cos(ang);
                float si = (float) Math.sin(ang);
                c.drawLine(cx + co * r0, cy + si * r0, cx + co * r1, cy + si * r1, p);
            }
        }
        p.setStrokeCap(Paint.Cap.BUTT);
        p.setStyle(Paint.Style.FILL);
        p.setShader(new RadialGradient(cx, cy, w * 0.55f, new int[]{HabitUi.alpha(pal(t * 0.2f), 0x70), HabitUi.alpha(pal(t * 0.2f), 0)},
                null, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
    }

    // ---- Grid: a lattice of dots that swell and glow as two light waves roll across it
    private void grid(Canvas c, float t) {
        float w = getWidth();
        float h = getHeight();
        float gap = 20 * d;
        p.setStyle(Paint.Style.FILL);
        float c1x = w * (0.5f + 0.35f * (float) Math.sin(t * 0.21f));
        float c1y = h * (0.35f + 0.2f * (float) Math.cos(t * 0.17f));
        float c2x = w * (0.5f + 0.4f * (float) Math.cos(t * 0.15f + 2f));
        float c2y = h * (0.7f + 0.2f * (float) Math.sin(t * 0.19f));
        int cols = (int) (w / gap) + 2;
        int rows = (int) (h / gap) + 2;
        float ox = (w - (cols - 1) * gap) / 2f;
        float oy = (h - (rows - 1) * gap) / 2f;
        for (int j = 0; j < rows; j++) {
            for (int i = 0; i < cols; i++) {
                float x = ox + i * gap;
                float y = oy + j * gap;
                float d1 = (float) Math.hypot(x - c1x, y - c1y);
                float d2 = (float) Math.hypot(x - c2x, y - c2y);
                float w1 = (float) Math.sin(d1 / (38f * d) - t * 2.2f);
                float w2 = (float) Math.sin(d2 / (46f * d) - t * 1.7f);
                float e = Math.max(0f, 0.5f * (w1 + w2) * 0.5f + 0.5f);
                e = e * e * e;
                float fall = 1f / (1f + (Math.min(d1, d2) / (w * 0.7f)));
                float lvl = Math.min(1f, e * 1.4f * fall + 0.04f);
                p.setColor(HabitUi.alpha(pal(d1 / (160f * d) + t * 0.2f), Math.round(30 + 225 * lvl)));
                c.drawCircle(x, y, (1.1f + 2.4f * lvl) * d, p);
            }
        }
    }

    // ---- Ribbons: glossy silk strands weaving across the screen
    private void ribbons(Canvas c, float t) {
        float w = getWidth();
        float h = getHeight();
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SCREEN));
        for (int r = 0; r < 6; r++) {
            float yMid = h * (0.18f + r * 0.13f);
            float amp = h * (0.07f + 0.02f * (float) Math.sin(t * 0.3f + r));
            float k = (1.3f + r * 0.18f) * 6.2832f / w;
            float sp2 = t * (0.5f + r * 0.11f) * (r % 2 == 0 ? 1 : -1);
            path.reset();
            for (float x = -10 * d; x <= w + 10 * d; x += 10 * d) {
                float y = yMid + amp * (float) Math.sin(x * k + sp2 + r) + amp * 0.5f * (float) Math.sin(x * k * 2.3f - sp2 * 0.6f);
                if (x < 0) path.moveTo(x, y);
                else path.lineTo(x, y);
            }
            int col = pal(r * 0.9f + t * 0.1f);
            p.setShader(new LinearGradient(0, 0, w, 0, new int[]{HabitUi.alpha(col, 0),
                    HabitUi.alpha(col, 0xC0), HabitUi.alpha(pal(r * 0.9f + 1.5f + t * 0.1f), 0xC0),
                    HabitUi.alpha(col, 0)}, null, Shader.TileMode.CLAMP));
            p.setStrokeWidth(16 * d);
            p.setAlpha(40);
            c.drawPath(path, p);
            p.setStrokeWidth(7 * d);
            p.setAlpha(110);
            c.drawPath(path, p);
            p.setStrokeWidth(1.6f * d);
            p.setAlpha(255);
            c.drawPath(path, p);
        }
        p.setShader(null);
        p.setXfermode(null);
        p.setStrokeCap(Paint.Cap.BUTT);
    }

    // ---- Orbit: tilted rings with comets circling a glowing core
    private void orbit(Canvas c, float t) {
        float w = getWidth();
        float h = getHeight();
        float cx = w / 2f;
        float cy = h * 0.32f;
        p.setStyle(Paint.Style.FILL);
        float breath = 0.9f + 0.1f * (float) Math.sin(t * 1.6f);
        p.setShader(new RadialGradient(cx, cy, w * 0.62f * breath,
                new int[]{HabitUi.alpha(pal(t * 0.25f), 0x66), HabitUi.alpha(pal(t * 0.25f), 0)}, null, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
        for (int i = 0; i < 4; i++) {
            float rx = w * (0.30f + i * 0.085f);
            float ry = rx * (0.30f + 0.06f * i);
            float tilt = 24f * i - 30f + 10f * (float) Math.sin(t * 0.25f + i);
            c.save();
            c.rotate(tilt + t * (3f + i * 1.5f) * (i % 2 == 0 ? 1 : -1), cx, cy);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.2f * d);
            p.setColor(HabitUi.alpha(pal(i * 0.8f + t * 0.1f), 90));
            rect.set(cx - rx, cy - ry, cx + rx, cy + ry);
            c.drawOval(rect, p);
            p.setStyle(Paint.Style.FILL);
            float ang = t * (0.9f - i * 0.15f) * (i % 2 == 0 ? 1 : -1) + i * 1.7f;
            for (int tail = 0; tail < 9; tail++) { // comet: a fading trail behind the head
                float a2 = ang - tail * 0.07f * (i % 2 == 0 ? 1 : -1);
                float px = cx + rx * (float) Math.cos(a2);
                float py = cy + ry * (float) Math.sin(a2);
                p.setColor(HabitUi.alpha(0xFFFFFFFF, Math.round(255 * (1f - tail / 9f))));
                c.drawCircle(px, py, (4.2f - tail * 0.35f) * d, p);
            }
            float hx = cx + rx * (float) Math.cos(ang);
            float hy = cy + ry * (float) Math.sin(ang);
            p.setColor(HabitUi.alpha(pal(i * 0.8f + t * 0.1f), 70));
            c.drawCircle(hx, hy, 14 * d, p);
            c.restore();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (goo != null) {
            goo.recycle();
            goo = null;
        }
        super.onDetachedFromWindow();
    }
}
