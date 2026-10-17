package app.privatetodo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

import java.util.Random;

/**
 * Animated backdrops for the full-screen alert. Five styles (pick one in Settings): edge glow, pulse rings,
 * aurora, sparkles and waves. All time-based and drawn with plain canvas calls, so they run smoothly and
 * stop the moment the screen closes. They never take touches.
 */
final class AlertFx extends View {
    static final int OFF = 0;
    static final int EDGE = 1;
    static final int PULSE = 2;
    static final int AURORA = 3;
    static final int SPARKLES = 4;
    static final int WAVES = 5;

    static final String[] NAMES = {"Off", "Edge glow", "Pulse", "Aurora", "Sparkles", "Waves"};

    /** The view for a style, or null for Off. */
    static View create(Context c, int mode) {
        if (mode == EDGE) return new GlowView(c);
        if (mode >= PULSE && mode <= WAVES) return new AlertFx(c, mode);
        return null;
    }

    private final int mode;
    private final float d;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Matrix m = new Matrix();
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
            case PULSE:
                pulse(c, t);
                break;
            case AURORA:
                aurora(c, t);
                break;
            case SPARKLES:
                sparkles(c, t);
                break;
            default:
                waves(c, t);
                break;
        }
        postInvalidateOnAnimation();
    }

    private void pulse(Canvas c, float t) {
        float w = getWidth();
        float h = getHeight();
        float cx = w / 2f;
        float cy = h * 0.30f;
        float reach = Math.max(w, h) * 0.8f;
        // a soft breathing core behind the clock
        float breath = 0.85f + 0.15f * (float) Math.sin(t * 2.1f);
        m.setTranslate(cx, cy);
        m.postScale(breath, breath, cx, cy);
        core.setLocalMatrix(m);
        p.setStyle(Paint.Style.FILL);
        p.setShader(core);
        p.setColor(0xFFFFFFFF);
        p.setAlpha(0);
        p.setShader(new RadialGradient(cx, cy, w * 0.55f * breath,
                new int[]{HabitUi.alpha(pal(t * 0.4f), 0x55), 0x00000000}, null, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        for (int i = 0; i < 4; i++) {
            float ph = ((t / 3.4f) + i / 4f) % 1f;
            float rad = ph * reach;
            float a = (float) Math.pow(1f - ph, 1.7f);
            p.setStrokeWidth((2f + ph * 12f) * d);
            p.setColor(HabitUi.alpha(pal(t * 0.35f + i * 1.1f), Math.round(150 * a)));
            c.drawCircle(cx, cy, rad, p);
        }
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

    private void sparkles(Canvas c, float t) {
        float w = getWidth();
        float h = getHeight();
        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < sx.length; i++) {
            float rise = (sy[i] + t * ss[i]) % 1f;
            float y = h * (1f - rise);
            float x = sx[i] * w + (float) Math.sin(t * 0.7f + sp[i]) * 14 * d;
            float tw = (float) Math.sin(t * (1.4f + ss[i] * 8f) + sp[i]);
            float a = 0.25f + 0.75f * tw * tw;
            float edge = Math.min(1f, Math.min(rise, 1f - rise) * 6f); // fade in at the bottom, out at the top
            float r = (1.6f + ss[i] * 48f) * d;
            int col = sc[i];
            p.setColor(HabitUi.alpha(col, Math.round(40 * a * edge)));
            c.drawCircle(x, y, r * 3.2f, p);
            p.setColor(HabitUi.alpha(0xFFFFFFFF, Math.round(230 * a * edge)));
            if (r > 3.2f * d) { // the bigger ones are four-point stars
                float len = r * 2.1f;
                float th = r * 0.32f;
                c.drawRect(x - len, y - th / 2f, x + len, y + th / 2f, p);
                c.drawRect(x - th / 2f, y - len, x + th / 2f, y + len, p);
            }
            c.drawCircle(x, y, r * 0.6f, p);
        }
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
}
