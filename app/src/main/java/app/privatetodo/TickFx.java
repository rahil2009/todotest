package app.privatetodo;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;

import java.util.Random;

/**
 * The little celebration when you tick a task off on the main screen. Ten styles (Settings > Tick animation),
 * all drawn with plain graphics on a short-lived overlay that removes itself, so nothing keeps running.
 */
final class TickFx {
    private TickFx() { }

    static final String[] NAMES = {"Off", "Pop", "Ripple", "Confetti", "Sparkles", "Check draw", "Ring burst",
            "Fireworks", "Row sweep", "Confetti rain", "Bubbles"};

    static final String[] DESCS = {"No animation when you tick a task.",
            "A round badge pops out of the tick.",
            "Three rings spread out from the tick.",
            "A burst of confetti from the tick.",
            "Little stars twinkle around the tick.",
            "A big tick is drawn with a soft glow.",
            "One ring and a ring of sparks.",
            "Three small fireworks above the tick.",
            "A bright band sweeps across the row.",
            "Confetti falls down the whole screen.",
            "Bubbles float up from the tick."};

    private static final int[] PARTY = {0xFFFFC107, 0xFFEF5350, 0xFF42A5F5, 0xFFAB47BC, 0xFF66BB6A, 0xFFFF7043};
    private static final int[] MS = {0, 650, 900, 1100, 950, 850, 800, 1250, 750, 1700, 1250};

    static int style(Context c) {
        return Math.max(0, Math.min(NAMES.length - 1, c.getApplicationContext()
                .getSharedPreferences("settings", Context.MODE_PRIVATE).getInt("tick_fx", 1)));
    }

    static void setStyle(Context c, int s) {
        c.getApplicationContext().getSharedPreferences("settings", Context.MODE_PRIVATE)
                .edit().putInt("tick_fx", s).apply();
    }

    /** Plays the chosen style on {@code anchor} (the tick circle); {@code row} is the whole task row, if known. */
    static void play(Activity a, View anchor, View row) {
        play(a, anchor, row, style(a));
    }

    static void play(Activity a, View anchor, View row, int style) {
        if (style <= 0 || style >= NAMES.length || a == null || a.isFinishing() || anchor == null
                || !anchor.isAttachedToWindow()) {
            return;
        }
        ViewGroup decor = (ViewGroup) a.getWindow().getDecorView();
        int[] l = new int[2];
        anchor.getLocationInWindow(l);
        float cx = l[0] + anchor.getWidth() / 2f;
        float cy = l[1] + anchor.getHeight() / 2f;
        RectF rr = new RectF(l[0], l[1], l[0] + anchor.getWidth(), l[1] + anchor.getHeight());
        if (row != null && row.isAttachedToWindow()) {
            row.getLocationInWindow(l);
            rr = new RectF(l[0], l[1], l[0] + row.getWidth(), l[1] + row.getHeight());
        }
        FxView v = new FxView(a, style, cx, cy, rr, decor.getWidth(), decor.getHeight());
        v.setClickable(false);
        v.setFocusable(false);
        decor.addView(v, new ViewGroup.LayoutParams(-1, -1));
    }

    private static float ease(float p) {
        float q = 1 - Math.max(0, Math.min(1, p));
        return 1 - q * q * q;
    }

    private static final class FxView extends View {
        private final int style;
        private final float cx;
        private final float cy;
        private final RectF row;
        private final int sw;
        private final int sh;
        private final float d;
        private final long t0 = SystemClock.uptimeMillis();
        private final int dur;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final Random rnd = new Random();
        private static final int N = 44;
        private final float[] ang = new float[N];
        private final float[] spd = new float[N];
        private final float[] siz = new float[N];
        private final float[] dly = new float[N];
        private final float[] rot = new float[N];
        private final int[] col = new int[N];
        private boolean gone;

        FxView(Activity a, int style, float cx, float cy, RectF row, int sw, int sh) {
            super(a);
            this.style = style;
            this.cx = cx;
            this.cy = cy;
            this.row = row;
            this.sw = sw;
            this.sh = sh;
            this.d = a.getResources().getDisplayMetrics().density;
            this.dur = MS[style];
            for (int i = 0; i < N; i++) {
                ang[i] = rnd.nextFloat();
                spd[i] = rnd.nextFloat();
                siz[i] = rnd.nextFloat();
                dly[i] = rnd.nextFloat();
                rot[i] = rnd.nextFloat();
                col[i] = PARTY[rnd.nextInt(PARTY.length)];
            }
            setWillNotDraw(false);
        }

        @Override
        protected void onDraw(Canvas c) {
            float el = SystemClock.uptimeMillis() - t0;
            float pr = el / dur;
            if (pr >= 1f) {
                if (!gone) {
                    gone = true;
                    post(() -> {
                        if (getParent() instanceof ViewGroup) ((ViewGroup) getParent()).removeView(this);
                    });
                }
                return;
            }
            float t = el / 1000f;
            p.reset();
            p.setAntiAlias(true);
            switch (style) {
                case 1: pop(c, pr); break;
                case 2: ripple(c, pr); break;
                case 3: confetti(c, t); break;
                case 4: sparkles(c, pr); break;
                case 5: checkDraw(c, pr); break;
                case 6: ringBurst(c, pr); break;
                case 7: fireworks(c, pr); break;
                case 8: sweep(c, pr); break;
                case 9: rain(c, t); break;
                default: bubbles(c, pr); break;
            }
            postInvalidateOnAnimation();
        }

        private int a(int rgb, float alpha) {
            int al = Math.max(0, Math.min(255, Math.round(alpha * 255)));
            return (al << 24) | (rgb & 0xFFFFFF);
        }

        private void tickPath(float x, float y, float r) {
            path.reset();
            path.moveTo(x - r * 0.45f, y + r * 0.02f);
            path.lineTo(x - r * 0.1f, y + r * 0.38f);
            path.lineTo(x + r * 0.48f, y - r * 0.34f);
        }

        // 1
        private void pop(Canvas c, float pr) {
            float r = 14 * d + 26 * d * ease(pr);
            p.setStyle(Paint.Style.FILL);
            p.setColor(a(Pal.accent, 0.9f * (1 - pr)));
            c.drawCircle(cx, cy, r, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setStrokeWidth(3.5f * d);
            p.setColor(a(Pal.onAccent, 1 - pr * pr));
            tickPath(cx, cy, r * (0.9f + 0.4f * (float) Math.sin(Math.PI * Math.min(1f, pr * 1.4f))));
            c.drawPath(path, p);
        }

        // 2
        private void ripple(Canvas c, float pr) {
            p.setStyle(Paint.Style.STROKE);
            for (int k = 0; k < 3; k++) {
                float q = Math.max(0, Math.min(1, (pr - k * 0.18f) / 0.64f));
                if (q <= 0) continue;
                p.setStrokeWidth((1 + 3 * (1 - q)) * d);
                p.setColor(a(Pal.accent, 0.8f * (1 - q)));
                c.drawCircle(cx, cy, 10 * d + 74 * d * ease(q), p);
            }
        }

        // 3
        private void confetti(Canvas c, float t) {
            float life = dur / 1000f;
            p.setStyle(Paint.Style.FILL);
            for (int i = 0; i < 30; i++) {
                float th = (float) (-Math.PI * (0.05 + 0.9 * ang[i]));
                float v = (150 + 190 * spd[i]) * d;
                float x = cx + (float) Math.cos(th) * v * t;
                float y = cy + (float) Math.sin(th) * v * t + 0.5f * 620 * d * t * t;
                float fade = Math.min(1f, (life - t) / (life * 0.35f));
                p.setColor(a(col[i], fade));
                float s = (4 + 5 * siz[i]) * d;
                c.save();
                c.rotate(rot[i] * 360 + t * 420 * (spd[i] - 0.5f), x, y);
                c.drawRect(x - s, y - s * 0.55f, x + s, y + s * 0.55f, p);
                c.restore();
            }
        }

        // 4
        private void star(Canvas c, float x, float y, float r) {
            path.reset();
            path.moveTo(x, y - r);
            path.quadTo(x, y, x + r, y);
            path.quadTo(x, y, x, y + r);
            path.quadTo(x, y, x - r, y);
            path.quadTo(x, y, x, y - r);
            path.close();
            c.drawPath(path, p);
        }

        private void sparkles(Canvas c, float pr) {
            p.setStyle(Paint.Style.FILL);
            for (int i = 0; i < 10; i++) {
                float start = dly[i] * 0.5f;
                float q = Math.max(0, Math.min(1, (pr - start) / 0.5f));
                if (q <= 0 || q >= 1) continue;
                float th = (float) (ang[i] * Math.PI * 2);
                float rad = (22 + 42 * spd[i]) * d;
                float s = (5 + 9 * siz[i]) * d * (float) Math.sin(Math.PI * q);
                p.setColor(a(i % 3 == 0 ? Pal.accent : i % 3 == 1 ? 0xFFFFC107 : 0xFFFFFFFF, 1f));
                star(c, cx + (float) Math.cos(th) * rad, cy + (float) Math.sin(th) * rad, s);
            }
        }

        // 5
        private void checkDraw(Canvas c, float pr) {
            float fade = pr < 0.75f ? 1f : 1 - (pr - 0.75f) / 0.25f;
            p.setStyle(Paint.Style.FILL);
            p.setColor(a(Pal.accent, 0.22f * fade));
            c.drawCircle(cx, cy, 52 * d * (0.7f + 0.3f * ease(pr * 1.5f)), p);
            tickPath(cx, cy, 46 * d);
            PathMeasure pm = new PathMeasure(path, false);
            Path seg = new Path();
            pm.getSegment(0, pm.getLength() * ease(Math.min(1f, pr / 0.55f)), seg, true);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setStrokeWidth(9 * d);
            p.setColor(a(Pal.accent, fade));
            c.drawPath(seg, p);
        }

        // 6
        private void ringBurst(Canvas c, float pr) {
            float e = ease(pr);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(3 * d * (1 - pr) + d);
            p.setColor(a(Pal.accent, 1 - pr));
            c.drawCircle(cx, cy, 12 * d + 48 * d * e, p);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeWidth(3 * d);
            for (int i = 0; i < 14; i++) {
                float th = (float) (i * Math.PI * 2 / 14);
                float r0 = 18 * d + 52 * d * e;
                float r1 = r0 + 12 * d * (1 - pr);
                p.setColor(a(PARTY[i % PARTY.length], 1 - pr));
                c.drawLine(cx + (float) Math.cos(th) * r0, cy + (float) Math.sin(th) * r0,
                        cx + (float) Math.cos(th) * r1, cy + (float) Math.sin(th) * r1, p);
            }
        }

        // 7
        private void fireworks(Canvas c, float pr) {
            float[][] spots = {{-64, -62}, {60, -92}, {-6, -130}};
            float[] begin = {0f, 0.2f, 0.4f};
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeWidth(2.5f * d);
            for (int k = 0; k < 3; k++) {
                float q = Math.max(0, Math.min(1, (pr - begin[k]) / 0.6f));
                if (q <= 0) continue;
                float x = cx + spots[k][0] * d;
                float y = cy + spots[k][1] * d;
                float e = ease(q);
                for (int i = 0; i < 14; i++) {
                    float th = (float) (i * Math.PI * 2 / 14);
                    float r0 = 4 * d + 30 * d * e;
                    float r1 = r0 + 9 * d * (1 - q);
                    p.setColor(a(PARTY[(i + k) % PARTY.length], 1 - q));
                    c.drawLine(x + (float) Math.cos(th) * r0, y + (float) Math.sin(th) * r0 + 14 * d * q * q,
                            x + (float) Math.cos(th) * r1, y + (float) Math.sin(th) * r1 + 14 * d * q * q, p);
                }
            }
        }

        // 8
        private void sweep(Canvas c, float pr) {
            if (row.isEmpty()) return;
            float band = Math.max(80 * d, row.width() * 0.35f);
            float e = pr < 0.5f ? 2 * pr * pr : 1 - 2 * (1 - pr) * (1 - pr);
            float x = row.left - band + (row.width() + band) * e;
            c.save();
            Path clip = new Path();
            clip.addRoundRect(row, 16 * d, 16 * d, Path.Direction.CW);
            c.clipPath(clip);
            p.setStyle(Paint.Style.FILL);
            p.setColor(a(Pal.accent, 0.20f * (1 - pr)));
            c.drawRect(row, p);
            p.setShader(new LinearGradient(x, 0, x + band, 0,
                    new int[]{0x00FFFFFF, a(Pal.dark ? 0xFFFFFF : Pal.accent, 0.55f), 0x00FFFFFF}, null,
                    Shader.TileMode.CLAMP));
            c.drawRect(x, row.top, x + band, row.bottom, p);
            p.setShader(null);
            c.restore();
        }

        // 9
        private void rain(Canvas c, float t) {
            p.setStyle(Paint.Style.FILL);
            for (int i = 0; i < N; i++) {
                float tt = t - dly[i] * 0.45f;
                if (tt <= 0) continue;
                float x = sw * ang[i] + (float) Math.sin(tt * 4 + i) * 16 * d;
                float y = -20 * d + (320 + 380 * spd[i]) * d * tt;
                if (y > sh + 20 * d) continue;
                float s = (4 + 4 * siz[i]) * d;
                p.setColor(a(col[i], 1f));
                c.save();
                c.rotate(rot[i] * 360 + tt * 300 * (spd[i] + 0.3f), x, y);
                c.drawRect(x - s, y - s * 0.55f, x + s, y + s * 0.55f, p);
                c.restore();
            }
        }

        // 10
        private void bubbles(Canvas c, float pr) {
            p.setStyle(Paint.Style.STROKE);
            for (int i = 0; i < 12; i++) {
                float start = dly[i] * 0.4f;
                float q = Math.max(0, Math.min(1, (pr - start) / 0.6f));
                if (q <= 0) continue;
                float x = cx + (ang[i] - 0.5f) * 56 * d + (float) Math.sin(q * 6 + i) * 6 * d;
                float y = cy - (40 + 110 * spd[i]) * d * ease(q);
                float r = (3 + 6 * siz[i]) * d * (0.6f + 0.6f * q);
                p.setStrokeWidth(1.8f * d);
                p.setColor(a(Pal.accent, 0.9f * (1 - q)));
                c.drawCircle(x, y, r, p);
                p.setStrokeWidth(1.4f * d);
                p.setColor(a(0xFFFFFF, 0.8f * (1 - q)));
                c.drawArc(x - r * 0.6f, y - r * 0.6f, x + r * 0.6f, y + r * 0.6f, 200, 60, false, p);
            }
        }
    }
}
