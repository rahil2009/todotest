package app.privatetodo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;

import java.util.Random;

/**
 * Task-card animations. Each task slot (normal, low, medium, high) can have one of 20 styles, picked and tried out in
 * Settings. A style is a Drawable laid over the card as its foreground, so it moves with swipes, is clipped to the
 * card's rounded corners and only runs while the card is on screen. Plain canvas calls, about 30 frames a second.
 */
final class Fx {
    private Fx() {
    }

    static final String[] NAMES = {"None", "Soft pulse", "Breathing border", "Bar pulse", "Shimmer", "Marching ants",
            "Running light", "Neon glow", "Heartbeat", "Gradient flow", "Aurora", "Rainbow edge", "Sparkles",
            "Waves", "Scanline", "Ripple", "Comet", "Corner glow", "Equalizer", "Glitch", "Embers"};

    static final String[] DESCS = {"No animation.",
            "The whole card gently brightens and fades.",
            "The outline slowly breathes in and out.",
            "The edge bar glows and the colour leaks into the card.",
            "A soft band of light sweeps across.",
            "A dashed outline marches around the card.",
            "A bright streak runs around the edge.",
            "A glowing neon outline with a faint flicker.",
            "Two quick beats, then a rest.",
            "Colour drifts slowly across the card.",
            "Soft northern-lights blobs float around.",
            "A rainbow outline spins around the card.",
            "Little stars twinkle on the card.",
            "Gentle waves rise from the bottom.",
            "A scanner line sweeps down and back.",
            "Rings spread out from the tick circle.",
            "A comet with a tail circles the card.",
            "Corners glow in turn.",
            "Little bars bounce at the right.",
            "A quick digital glitch now and then.",
            "Glowing embers float up."};

    /** Pref slots: 0 normal, 1 low, 2 medium, 3 high. */
    static int slotFor(int priority) {
        return priority >= 1 && priority <= 3 ? priority : 0;
    }

    static int style(Context c, int slot) {
        return Math.max(0, Math.min(NAMES.length - 1, c.getApplicationContext()
                .getSharedPreferences("settings", Context.MODE_PRIVATE).getInt("fx_" + slot, 0)));
    }

    static void setStyle(Context c, int slot, int style) {
        c.getApplicationContext().getSharedPreferences("settings", Context.MODE_PRIVATE)
                .edit().putInt("fx_" + slot, style).apply();
    }

    static int colorFor(int slot) {
        return slot == 0 ? Pal.accent : MainActivity.priorityColor(slot);
    }

    /** The animation drawable, or null for None. */
    static Drawable make(Context c, int style, int color) {
        if (style <= 0 || style >= NAMES.length) return null;
        return new FxDrawable(c.getResources().getDisplayMetrics().density, style, color);
    }

    static final class FxDrawable extends Drawable implements Runnable {
        private final float d;
        private final int style;
        private final int rgb;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path clip = new Path();
        private final Path edge = new Path();
        private final Path tmp = new Path();
        private final RectF r = new RectF();
        private final Matrix m = new Matrix();
        private final PathMeasure pm = new PathMeasure();
        private final float[] pos = new float[2];
        private float perimeter = 1f;
        private boolean running;
        private final long t0 = SystemClock.uptimeMillis();
        private Shader shader;
        private final float[] rx = new float[24];
        private final float[] ry = new float[24];
        private final float[] rs = new float[24];

        FxDrawable(float density, int style, int color) {
            this.d = density;
            this.style = style;
            this.rgb = color & 0xFFFFFF;
            Random rnd = new Random(style * 31L + 7);
            for (int i = 0; i < rx.length; i++) {
                rx[i] = rnd.nextFloat();
                ry[i] = rnd.nextFloat();
                rs[i] = rnd.nextFloat();
            }
        }

        private int col(int alpha) {
            return (Math.max(0, Math.min(255, alpha)) << 24) | rgb;
        }

        private int white(int alpha) {
            return (Math.max(0, Math.min(255, alpha)) << 24) | 0xFFFFFF;
        }

        @Override
        protected void onBoundsChange(android.graphics.Rect b) {
            r.set(b);
            float rad = 16 * d;
            clip.reset();
            clip.addRoundRect(r, rad, rad, Path.Direction.CW);
            RectF in = new RectF(r);
            in.inset(1.5f * d, 1.5f * d);
            edge.reset();
            edge.addRoundRect(in, rad - 1.5f * d, rad - 1.5f * d, Path.Direction.CW);
            pm.setPath(edge, false);
            perimeter = Math.max(1f, pm.getLength());
            shader = null;
        }

        @Override
        public boolean setVisible(boolean visible, boolean restart) {
            boolean changed = super.setVisible(visible, restart);
            if (visible) start(); else stop();
            return changed;
        }

        private void start() {
            if (running) return;
            running = true;
            scheduleSelf(this, SystemClock.uptimeMillis() + 33);
        }

        private void stop() {
            running = false;
            unscheduleSelf(this);
        }

        @Override
        public void run() {
            if (!running) return;
            invalidateSelf();
            scheduleSelf(this, SystemClock.uptimeMillis() + 33);
        }

        @Override
        public void draw(Canvas c) {
            if (r.isEmpty()) return;
            float t = (SystemClock.uptimeMillis() - t0) / 1000f;
            int save = c.save();
            c.clipPath(clip);
            p.reset();
            p.setAntiAlias(true);
            switch (style) {
                case 1: softPulse(c, t); break;
                case 2: breathing(c, t); break;
                case 3: barPulse(c, t); break;
                case 4: shimmer(c, t); break;
                case 5: ants(c, t); break;
                case 6: runningLight(c, t); break;
                case 7: neon(c, t); break;
                case 8: heartbeat(c, t); break;
                case 9: flow(c, t); break;
                case 10: aurora(c, t); break;
                case 11: rainbow(c, t); break;
                case 12: sparkles(c, t); break;
                case 13: waves(c, t); break;
                case 14: scan(c, t); break;
                case 15: ripple(c, t); break;
                case 16: comet(c, t); break;
                case 17: corners(c, t); break;
                case 18: equalizer(c, t); break;
                case 19: glitch(c, t); break;
                case 20: embers(c, t); break;
                default: break;
            }
            c.restoreToCount(save);
        }

        private float wave01(float t, float period) {
            return 0.5f + 0.5f * (float) Math.sin(t * 2 * Math.PI / period);
        }

        private void stroke(Canvas c, float w, int color) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(w);
            p.setColor(color);
            c.drawPath(edge, p);
        }

        // 1
        private void softPulse(Canvas c, float t) {
            p.setColor(col((int) (14 + 40 * wave01(t, 2.4f))));
            c.drawRect(r, p);
        }

        // 2
        private void breathing(Canvas c, float t) {
            float k = wave01(t, 2.8f);
            stroke(c, (1.5f + 1.5f * k) * d, col((int) (70 + 185 * k)));
        }

        // 3
        private void barPulse(Canvas c, float t) {
            float k = wave01(t, 1.6f);
            float w = (60 + 50 * k) * d;
            p.setShader(new LinearGradient(r.left, 0, r.left + w, 0, col((int) (60 + 70 * k)), col(0), Shader.TileMode.CLAMP));
            c.drawRect(r.left, r.top, r.left + w, r.bottom, p);
            p.setShader(null);
            p.setColor(col((int) (130 + 125 * k)));
            c.drawRect(r.left, r.top, r.left + 6 * d, r.bottom, p);
        }

        // 4
        private void shimmer(Canvas c, float t) {
            float w = r.width();
            float band = w * 0.35f;
            if (shader == null) {
                shader = new LinearGradient(0, 0, band, 0, new int[]{white(0), white(80), white(0)},
                        new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
            }
            float cycle = (t % 2.6f) / 2.6f;
            float x = -band + cycle * (w + band * 2);
            m.setSkew(-0.3f, 0, 0, 0);
            m.postTranslate(x, 0);
            shader.setLocalMatrix(m);
            p.setShader(shader);
            c.drawRect(r, p);
            p.setShader(null);
        }

        // 5
        private void ants(Canvas c, float t) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2 * d);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(col(220));
            p.setPathEffect(new DashPathEffect(new float[]{8 * d, 6 * d}, -t * 22 * d));
            c.drawPath(edge, p);
            p.setPathEffect(null);
        }

        // 6
        private void runningLight(Canvas c, float t) {
            float len = perimeter * 0.18f;
            float phase = -((t % 2.2f) / 2.2f) * perimeter;
            stroke(c, 1.5f * d, col(60));
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(3 * d);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(col(255));
            p.setPathEffect(new DashPathEffect(new float[]{len, perimeter - len}, phase));
            c.drawPath(edge, p);
            p.setPathEffect(null);
        }

        // 7
        private void neon(Canvas c, float t) {
            float flick = 0.85f + 0.15f * (float) Math.sin(t * 17) * (float) Math.sin(t * 3.1);
            float k = (0.7f + 0.3f * wave01(t, 2f)) * flick;
            stroke(c, 9 * d, col((int) (26 * k)));
            stroke(c, 5.5f * d, col((int) (54 * k)));
            stroke(c, 2.2f * d, col((int) (235 * k)));
        }

        // 8
        private void heartbeat(Canvas c, float t) {
            float x = (t % 1.5f) / 1.5f;
            float a = bump(x, 0.08f, 0.07f) + 0.7f * bump(x, 0.26f, 0.08f);
            p.setColor(col((int) (12 + 90 * a)));
            c.drawRect(r, p);
            stroke(c, (1.5f + 1.5f * a) * d, col((int) (60 + 195 * a)));
        }

        private float bump(float x, float center, float width) {
            float v = (x - center) / width;
            return (float) Math.exp(-v * v);
        }

        // 9
        private void flow(Canvas c, float t) {
            float w = r.width();
            if (shader == null) {
                shader = new LinearGradient(0, 0, w * 0.8f, 0, col(0), col(70), Shader.TileMode.MIRROR);
            }
            m.setTranslate(((t % 5f) / 5f) * w * 1.6f, 0);
            shader.setLocalMatrix(m);
            p.setShader(shader);
            c.drawRect(r, p);
            p.setShader(null);
        }

        // 10
        private void aurora(Canvas c, float t) {
            float w = r.width();
            float h = r.height();
            int[] cols = {rgb, shift(rgb, 60), shift(rgb, -70)};
            for (int i = 0; i < 3; i++) {
                float cx = r.left + w * (0.5f + 0.38f * (float) Math.sin(t * (0.35 + 0.15 * i) + i * 2.1));
                float cy = r.top + h * (0.5f + 0.3f * (float) Math.cos(t * (0.5 + 0.1 * i) + i));
                float rad = Math.max(w * 0.28f, h * 0.9f);
                p.setShader(new RadialGradient(cx, cy, rad, (60 << 24) | (cols[i] & 0xFFFFFF), 0, Shader.TileMode.CLAMP));
                c.drawRect(r, p);
            }
            p.setShader(null);
        }

        private int shift(int rgb, int deg) {
            float[] hsv = new float[3];
            Color.colorToHSV(rgb | 0xFF000000, hsv);
            hsv[0] = (hsv[0] + deg + 360) % 360;
            return Color.HSVToColor(hsv) & 0xFFFFFF;
        }

        // 11
        private void rainbow(Canvas c, float t) {
            if (shader == null) {
                shader = new SweepGradient(r.centerX(), r.centerY(),
                        new int[]{0xFFFF4D4D, 0xFFFFC94D, 0xFF5CE65C, 0xFF4DD2FF, 0xFF7B61FF, 0xFFFF4DD2, 0xFFFF4D4D}, null);
            }
            m.setRotate((t * 120) % 360, r.centerX(), r.centerY());
            shader.setLocalMatrix(m);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2.5f * d);
            p.setShader(shader);
            c.drawPath(edge, p);
            p.setShader(null);
        }

        // 12
        private void sparkles(Canvas c, float t) {
            for (int i = 0; i < 12; i++) {
                float ph = (t * (0.5f + rs[i] * 0.6f) + rs[i] * 5f) % 1f;
                float a = (float) Math.sin(ph * Math.PI);
                if (a < 0.05f) continue;
                float cx = r.left + (0.05f + 0.9f * rx[i]) * r.width();
                float cy = r.top + (0.12f + 0.76f * ry[i]) * r.height();
                float s = (2f + 3.5f * rs[i]) * d * a;
                p.setStyle(Paint.Style.FILL);
                p.setColor(i % 3 == 0 ? col((int) (230 * a)) : white((int) (230 * a)));
                tmp.reset();
                tmp.moveTo(cx, cy - s * 1.6f);
                tmp.quadTo(cx, cy, cx + s * 1.6f, cy);
                tmp.quadTo(cx, cy, cx, cy + s * 1.6f);
                tmp.quadTo(cx, cy, cx - s * 1.6f, cy);
                tmp.quadTo(cx, cy, cx, cy - s * 1.6f);
                c.drawPath(tmp, p);
            }
        }

        // 13
        private void waves(Canvas c, float t) {
            float h = r.height();
            for (int k = 0; k < 2; k++) {
                tmp.reset();
                float base = r.bottom - h * (0.28f + 0.06f * k);
                tmp.moveTo(r.left, r.bottom);
                for (float x = 0; x <= r.width() + 6 * d; x += 6 * d) {
                    float y = base + (float) Math.sin(x / (34 * d) + t * (1.4f + 0.5f * k) * (k == 0 ? 1 : -1)) * 4 * d;
                    tmp.lineTo(r.left + x, y);
                }
                tmp.lineTo(r.right, r.bottom);
                tmp.close();
                p.setStyle(Paint.Style.FILL);
                p.setColor(col(k == 0 ? 50 : 32));
                c.drawPath(tmp, p);
            }
        }

        // 14
        private void scan(Canvas c, float t) {
            float h = r.height();
            float band = 22 * d;
            float ph = (t % 2.4f) / 2.4f;
            float k = ph < 0.5f ? ph * 2 : 2 - ph * 2;
            float y = r.top - band + (h + band) * k;
            if (shader == null) {
                shader = new LinearGradient(0, 0, 0, band, col(0), col(110), Shader.TileMode.CLAMP);
            }
            m.setTranslate(0, y);
            shader.setLocalMatrix(m);
            p.setShader(shader);
            c.drawRect(r.left, y, r.right, y + band, p);
            p.setShader(null);
            p.setColor(col(230));
            c.drawRect(r.left, y + band - d, r.right, y + band, p);
        }

        // 15
        private void ripple(Canvas c, float t) {
            float cx = r.left + 30 * d;
            float cy = r.centerY();
            float max = r.width() * 0.75f;
            p.setStyle(Paint.Style.STROKE);
            for (int i = 0; i < 3; i++) {
                float ph = ((t / 2.4f) + i / 3f) % 1f;
                p.setStrokeWidth((2.5f - 1.5f * ph) * d);
                p.setColor(col((int) (200 * (1 - ph))));
                c.drawCircle(cx, cy, 8 * d + ph * max, p);
            }
        }

        // 16
        private void comet(Canvas c, float t) {
            float head = ((t % 2.6f) / 2.6f) * perimeter;
            p.setStyle(Paint.Style.FILL);
            for (int i = 0; i < 16; i++) {
                float at = head - i * 5 * d;
                if (at < 0) at += perimeter;
                pm.getPosTan(at, pos, null);
                float k = 1f - i / 16f;
                p.setColor(col((int) (255 * k * k)));
                c.drawCircle(pos[0], pos[1], (1f + 2.2f * k) * d, p);
            }
            p.setColor(white(255));
            pm.getPosTan(head, pos, null);
            c.drawCircle(pos[0], pos[1], 1.6f * d, p);
        }

        // 17
        private void corners(Canvas c, float t) {
            float rad = Math.max(r.width() * 0.3f, r.height() * 1.2f);
            float[][] cs = {{r.left, r.top}, {r.right, r.bottom}, {r.right, r.top}, {r.left, r.bottom}};
            for (int i = 0; i < 4; i++) {
                float a = (float) Math.max(0, Math.sin(t * 1.9 + (i < 2 ? 0 : Math.PI) + (i % 2) * 0.6)) ;
                if (a <= 0.02f) continue;
                p.setShader(new RadialGradient(cs[i][0], cs[i][1], rad, col((int) (110 * a)), col(0), Shader.TileMode.CLAMP));
                c.drawRect(r, p);
            }
            p.setShader(null);
        }

        // 18
        private void equalizer(Canvas c, float t) {
            int n = 6;
            float bw = 3 * d;
            float gap = 3 * d;
            float x0 = r.right - 14 * d - n * (bw + gap);
            float maxH = r.height() * 0.5f;
            p.setStyle(Paint.Style.FILL);
            for (int i = 0; i < n; i++) {
                float k = 0.25f + 0.75f * wave01(t + i * 0.37f, 0.7f + 0.11f * i);
                float x = x0 + i * (bw + gap);
                p.setColor(col(150));
                c.drawRoundRect(x, r.bottom - 8 * d - maxH * k, x + bw, r.bottom - 8 * d, d, d, p);
            }
        }

        // 19
        private void glitch(Canvas c, float t) {
            float ph = t % 2.4f;
            if (ph > 0.22f) {
                stroke(c, 1.2f * d, col(70));
                return;
            }
            int seed = (int) (t * 30);
            Random rnd = new Random(seed);
            int[] cols = {0xFF00E5FF, 0xFFFF2BD6, rgb | 0xFF000000};
            for (int i = 0; i < 4; i++) {
                float y = r.top + rnd.nextFloat() * r.height();
                float hh = (2 + rnd.nextFloat() * 8) * d;
                float dx = (rnd.nextFloat() - 0.5f) * 18 * d;
                p.setStyle(Paint.Style.FILL);
                p.setColor((70 << 24) | (cols[i % 3] & 0xFFFFFF));
                c.drawRect(r.left + dx, y, r.right + dx, y + hh, p);
            }
        }

        // 20
        private void embers(Canvas c, float t) {
            p.setStyle(Paint.Style.FILL);
            for (int i = 0; i < 16; i++) {
                float life = ((t * (0.22f + 0.2f * rs[i])) + rx[i]) % 1f;
                float x = r.left + (0.04f + 0.92f * ry[i]) * r.width() + (float) Math.sin(t * 2 + i) * 4 * d;
                float y = r.bottom - life * (r.height() + 6 * d) + 3 * d;
                float a = (float) Math.sin(life * Math.PI);
                p.setColor(i % 2 == 0 ? (((int) (230 * a)) << 24) | 0xFFB14D : col((int) (230 * a)));
                c.drawCircle(x, y, (1.1f + 1.6f * rs[i]) * d * (1 - life * 0.4f), p);
            }
        }

        @Override
        public void setAlpha(int alpha) {
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
