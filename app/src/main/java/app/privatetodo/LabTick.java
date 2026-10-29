package app.privatetodo;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;

import java.util.Random;

/**
 * Design lab only: a test task you slide to the RIGHT to complete it, with one of thirty "done" animations.
 * Nothing is saved. The task goes back to undone after a moment so you can try again.
 */
final class LabTick extends View {
    static final String[] NAMES = {
            "Draw the tick", "Confetti pop", "Ripples", "Green wipe", "Fireworks", "Coins", "Stars", "Balloons",
            "Shine sweep", "Hop", "Pen strike", "Flip card", "Big tick cover", "Hearts", "Emoji rain", "Medal",
            "Spring tick", "Rainbow bar", "Laser scan", "DONE stamp", "Shockwave", "Float up", "Golden glow",
            "Progress fill", "Tick chain", "Sunburst", "Paper plane", "Jelly", "Iris", "Fountain"};
    static final String[] NOTES = {
            "A big tick draws itself, then flies into the task's circle.",
            "Confetti pops out of the circle.",
            "Rings ripple out from the circle across the task.",
            "A green wave sweeps across and leaves the task ticked.",
            "Three little fireworks go off over the task.",
            "Gold coins jump out and spin.",
            "Stars burst out and twinkle.",
            "Balloons float up past the task.",
            "A shine glides across the task, with sparkles.",
            "The task hops and lands with a puff of dust.",
            "A pen writes the strike-through, word by word.",
            "The task flips over to a green Done side and back.",
            "A green panel slides in with a giant tick, then away.",
            "Hearts float up and drift.",
            "Party emoji rain down.",
            "A gold medal drops in and bounces.",
            "The circle springs big, ticks, and settles.",
            "A rainbow bar races along the bottom edge.",
            "A laser line scans across, ticking as it goes.",
            "A green DONE stamp slams on.",
            "A shockwave rolls out and the task jolts.",
            "The task lifts, leaves a ghost behind, and settles.",
            "The border glows gold twice.",
            "A progress bar fills to 100% and bursts into ticks.",
            "A chain of small circles ticks off one after another.",
            "Rays spin out behind the circle.",
            "A paper plane carries the task away, dotted trail and all.",
            "The task wobbles like jelly and flashes green.",
            "The screen irises in on the task, then opens.",
            "Green droplets fountain out of the circle."};
    private static final int[] DUR = {1100, 1200, 1200, 1000, 1500, 1300, 1300, 1600, 1100, 1100, 1200, 1100,
            1300, 1400, 1500, 1500, 1100, 1100, 1200, 1200, 1100, 1200, 1500, 1400, 1300, 1300, 1400, 1100, 1300, 1400};
    /** When the task starts to look ticked (0..1 of the animation). */
    private static final float[] DONE_AT = {0.55f, 0.15f, 0.2f, 0.45f, 0.1f, 0.1f, 0.1f, 0.1f, 0.45f, 0.5f, 0.55f,
            0.62f, 0.5f, 0.1f, 0.1f, 0.4f, 0.35f, 0.3f, 0.0f, 0.35f, 0.15f, 0.4f, 0.1f, 0.6f, 0.5f, 0.1f, 0.3f,
            0.3f, 0.7f, 0.15f};

    private static final int IDLE = 0;
    private static final int COMMIT = 1;
    private static final int HOLD = 2;
    private static final int RESTORE = 3;
    private static final int GREEN = 0xFF2E9E5B;
    private static final int[] PAL = {0xFFFF5252, 0xFFFFD740, 0xFF69F0AE, 0xFF40C4FF, 0xFFB388FF, 0xFFFF80AB, 0xFFFFAB40};

    private final int style;
    private final float d;
    private final int slop;
    private final float rh;
    private final float pad;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint q = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rf = new RectF();
    private final Path path = new Path();
    private final Typeface regular;
    private final Typeface bold;
    private final float[] rnd = new float[96];

    private int phase = IDLE;
    private float dx;
    private float t;
    private float rt;
    private float downX;
    private float downY;
    private boolean swiping;
    private boolean armed;
    private ValueAnimator anim;
    private Runnable pending;
    private float rw;
    private int count;
    private Runnable onDone;

    LabTick(Context c, int style) {
        super(c);
        this.style = style;
        d = c.getResources().getDisplayMetrics().density;
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
        rh = 68 * d;
        pad = 12 * d;
        Fonts.init(c);
        regular = Fonts.face(false);
        bold = Fonts.face(true);
        Random r = new Random(101 + style);
        for (int i = 0; i < rnd.length; i++) rnd[i] = r.nextFloat();
    }

    void setOnDone(Runnable r) {
        onDone = r;
    }

    @Override
    protected void onMeasure(int w, int h) {
        setMeasuredDimension(resolveSize(Math.round(300 * d), w), Math.round(rh + 60 * d));
    }

    private float limit() {
        return Math.max(96 * d, rw * 0.3f);
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

    /** 0 = open task, 1 = ticked task. */
    private float done() {
        if (phase == HOLD) return 1f;
        if (phase == RESTORE) return 1f - ease(rt);
        if (phase == COMMIT) return ease((t - DONE_AT[style]) / 0.25f);
        return 0f;
    }

    // ------------------------------------------------------------------ the task row

    private void drawCheck(Canvas c, float cx, float cy, float s, float prog, int color, float width) {
        if (prog <= 0) return;
        float ax = cx - 0.5f * s;
        float ay = cy + 0.03f * s;
        float bx = cx - 0.12f * s;
        float by = cy + 0.38f * s;
        float ex = cx + 0.55f * s;
        float ey = cy - 0.36f * s;
        float l1 = (float) Math.hypot(bx - ax, by - ay);
        float l2 = (float) Math.hypot(ex - bx, ey - by);
        float at = prog * (l1 + l2);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setStrokeWidth(width);
        p.setColor(color);
        path.reset();
        path.moveTo(ax, ay);
        if (at <= l1) {
            path.lineTo(lerp(ax, bx, at / l1), lerp(ay, by, at / l1));
        } else {
            path.lineTo(bx, by);
            float f = (at - l1) / l2;
            path.lineTo(lerp(bx, ex, f), lerp(by, ey, f));
        }
        c.drawPath(path, p);
        p.setStyle(Paint.Style.FILL);
    }

    /** strike < 0: follow the done amount. */
    private void drawRow(Canvas c, int alpha, float dn, float strike) {
        if (strike < 0) strike = dn;
        p.setStyle(Paint.Style.FILL);
        p.setColor(Pal.card);
        p.setAlpha(alpha);
        rf.set(0, 0, rw, rh);
        c.drawRoundRect(rf, 20 * d, 20 * d, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(d);
        p.setColor(Pal.chip);
        p.setAlpha(alpha);
        c.drawRoundRect(rf, 20 * d, 20 * d, p);
        p.setStyle(Paint.Style.FILL);
        float dim = 1f - 0.5f * dn;
        // circle: open -> ticked
        p.setColor(mix((Pal.accent & 0x00FFFFFF) | 0xFF000000, Pal.accent, dn));
        p.setAlpha((int) (alpha * (0.14f + 0.86f * dn)));
        c.drawCircle(35 * d, rh / 2f, 21 * d, p);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(regular);
        p.setTextSize(20 * d);
        p.setColor(Pal.ink);
        p.setAlpha((int) (alpha * (1f - dn)));
        c.drawText("🛒", 35 * d, rh / 2f - (p.ascent() + p.descent()) / 2f, p);
        p.setAlpha(alpha);
        drawCheck(c, 35 * d, rh / 2f, 17 * d, ease(dn * 1.3f), Pal.onAccent, 2.6f * d);
        // title
        p.setTextAlign(Paint.Align.LEFT);
        p.setTypeface(bold);
        p.setTextSize(16 * d);
        p.setColor(Pal.ink);
        p.setAlpha((int) (alpha * dim));
        float ty = rh * 0.40f;
        c.drawText("Buy groceries", 68 * d, ty, p);
        float tw = p.measureText("Buy groceries");
        if (strike > 0) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2.2f * d);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(Pal.ink);
            p.setAlpha((int) (alpha * dim));
            c.drawLine(66 * d, ty - 5 * d, 66 * d + (tw + 6 * d) * Math.min(1f, strike), ty - 5 * d, p);
            p.setStyle(Paint.Style.FILL);
        }
        // when tile
        p.setTypeface(bold);
        p.setTextSize(12 * d);
        float sw = p.measureText("Today, 6:00 PM") + 18 * d;
        p.setColor(mix(Pal.accent, Pal.chip, dn));
        p.setAlpha(alpha);
        rf.set(68 * d, rh * 0.52f, 68 * d + sw, rh * 0.52f + 22 * d);
        c.drawRoundRect(rf, 8 * d, 8 * d, p);
        p.setColor(mix(Pal.onAccent, Pal.muted, dn));
        p.setAlpha(alpha);
        c.drawText("Today, 6:00 PM", 77 * d, rh * 0.52f + 15.5f * d, p);
        p.setColor(0xFFE67E22);
        p.setAlpha((int) (alpha * (1f - dn)));
        rf.set(rw - 16 * d, rh / 2f - 16 * d, rw - 12 * d, rh / 2f + 16 * d);
        c.drawRoundRect(rf, 2 * d, 2 * d, p);
        p.setAlpha(255);
    }

    private static int mix(int a, int b, float t) {
        t = cl(t);
        int r = Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void onDraw(Canvas c) {
        rw = getWidth() - 2 * pad;
        c.save();
        c.translate(pad, (getHeight() - rh) / 2f);
        if (phase == IDLE) {
            float pr = cl(dx / limit());
            // green panel behind, tick circle at the left that draws itself as you pull
            p.setStyle(Paint.Style.FILL);
            p.setColor(Pal.accent);
            p.setAlpha(255);
            rf.set(0, 0, rw, rh);
            c.drawRoundRect(rf, 20 * d, 20 * d, p);
            float cx = 38 * d;
            p.setColor(0x33FFFFFF);
            c.drawCircle(cx, rh / 2f, (16 + 8 * pr) * d, p);
            drawCheck(c, cx, rh / 2f, (20 + 6 * pr) * d, pr, Pal.onAccent, 3 * d);
            p.setTypeface(bold);
            p.setTextAlign(Paint.Align.LEFT);
            p.setTextSize(12.5f * d);
            p.setColor(Pal.onAccent);
            c.drawText(pr >= 1f ? "Release to complete" : "Slide to complete", cx + 32 * d,
                    rh / 2f - (p.ascent() + p.descent()) / 2f, p);
            c.save();
            c.translate(dx, 0);
            drawRow(c, 255, 0, 0);
            c.restore();
        } else if (phase == COMMIT) {
            effect(c);
        } else {
            drawRow(c, 255, done(), -1);
        }
        c.restore();
    }

    private void dust(Canvas c, float cx, float cy, float lt, int n, int color, float spread) {
        q.setStyle(Paint.Style.FILL);
        for (int i = 0; i < n; i++) {
            float a = (rnd[i] - 0.5f) * 3.14f - 1.57f;
            float sp = (30 + 80 * rnd[i + 20]) * d * spread;
            q.setColor(color);
            q.setAlpha((int) (240 * (1f - lt)));
            c.drawCircle(cx + (float) Math.cos(a) * sp * lt, cy + (float) Math.sin(a) * sp * lt + lt * lt * 70 * d, 2.2f * d, q);
        }
        q.setAlpha(255);
    }

    private void star(Canvas c, float cx, float cy, float r, float rot, Paint pp) {
        path.reset();
        for (int i = 0; i < 10; i++) {
            double a = rot + i * Math.PI / 5 - Math.PI / 2;
            float rr = i % 2 == 0 ? r : r * 0.45f;
            float x = cx + (float) Math.cos(a) * rr;
            float y = cy + (float) Math.sin(a) * rr;
            if (i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        }
        path.close();
        c.drawPath(path, pp);
    }

    private void heart(Canvas c, float cx, float cy, float s, Paint pp) {
        path.reset();
        path.moveTo(cx, cy + s * 0.9f);
        path.cubicTo(cx - s * 1.6f, cy - s * 0.1f, cx - s * 0.7f, cy - s * 1.1f, cx, cy - s * 0.35f);
        path.cubicTo(cx + s * 0.7f, cy - s * 1.1f, cx + s * 1.6f, cy - s * 0.1f, cx, cy + s * 0.9f);
        path.close();
        c.drawPath(path, pp);
    }

    private void effect(Canvas c) {
        float W = rw;
        float H = rh;
        float dn = done();
        float bx = 35 * d;
        float by = H / 2f;
        q.setStyle(Paint.Style.FILL);
        switch (style) {
            case 0: { // draw the tick
                drawRow(c, 255, dn, -1);
                float grow = cl(t / 0.2f);
                float over = (float) (1 + 0.15 * Math.sin(grow * Math.PI));
                float fly = ease(cl((t - 0.62f) / 0.25f));
                float cx = lerp(W / 2f, bx, fly);
                float cy = lerp(H / 2f, by, fly);
                float sc = lerp(1f, 0.38f, fly) * grow * over;
                float al = 1f - cl((fly - 0.8f) / 0.2f);
                if (sc > 0.01f && al > 0) {
                    q.setColor(Pal.accent);
                    q.setAlpha((int) (255 * al));
                    c.drawCircle(cx, cy, 34 * d * sc, q);
                    p.setAlpha((int) (255 * al));
                    drawCheck(c, cx, cy, 50 * d * sc, cl((t - 0.2f) / 0.32f), Pal.onAccent, 5 * d * sc);
                    q.setAlpha(255);
                }
                break;
            }
            case 1: { // confetti
                drawRow(c, 255, dn, -1);
                float lt = cl(t / 0.85f);
                for (int i = 0; i < 40; i++) {
                    float a = -1.57f + (rnd[i] - 0.5f) * 2.9f;
                    float sp = (60 + 170 * rnd[i + 40]) * d;
                    float x = bx + (float) Math.cos(a) * sp * lt;
                    float y = by + (float) Math.sin(a) * sp * lt + lt * lt * 120 * d;
                    q.setColor(PAL[i % PAL.length]);
                    q.setAlpha((int) (255 * (1f - lt * lt)));
                    c.save();
                    c.translate(x, y);
                    c.rotate(lt * 500 * (rnd[(i + 7) % 90] - 0.5f));
                    rf.set(-3.5f * d, -2f * d * (float) Math.cos(lt * 16 + i), 3.5f * d, 2f * d * (float) Math.cos(lt * 16 + i));
                    c.drawRect(rf, q);
                    c.restore();
                }
                q.setAlpha(255);
                break;
            }
            case 2: { // ripples
                drawRow(c, 255, dn, -1);
                c.save();
                path.reset();
                rf.set(0, 0, W, H);
                path.addRoundRect(rf, 20 * d, 20 * d, Path.Direction.CW);
                c.clipPath(path);
                q.setStyle(Paint.Style.STROKE);
                for (int k = 0; k < 3; k++) {
                    float lt = cl((t - k * 0.12f) / 0.6f);
                    if (lt <= 0 || lt >= 1) continue;
                    q.setStrokeWidth((5 - 3 * lt) * d);
                    q.setColor(Pal.accent);
                    q.setAlpha((int) (200 * (1f - lt)));
                    c.drawCircle(bx, by, lt * W * 1.05f, q);
                }
                q.setAlpha(255);
                q.setStyle(Paint.Style.FILL);
                c.restore();
                break;
            }
            case 3: { // green wipe
                drawRow(c, 255, dn, -1);
                float front = ease(cl(t / 0.5f)) * (W + 40 * d);
                float back = ease(cl((t - 0.45f) / 0.45f)) * (W + 40 * d);
                c.save();
                path.reset();
                rf.set(0, 0, W, H);
                path.addRoundRect(rf, 20 * d, 20 * d, Path.Direction.CW);
                c.clipPath(path);
                q.setShader(new LinearGradient(back, 0, front, 0, new int[]{0x00000000, 0xAA2E9E5B, 0xCC2E9E5B, 0x00000000},
                        new float[]{0f, 0.1f, 0.9f, 1f}, Shader.TileMode.CLAMP));
                if (front > back) c.drawRect(back, 0, front, H, q);
                q.setShader(null);
                c.restore();
                break;
            }
            case 4: { // fireworks
                drawRow(c, 255, dn, -1);
                float[][] at = {{0.3f, 0.35f, 0.08f}, {0.65f, 0.6f, 0.28f}, {0.86f, 0.3f, 0.48f}};
                for (int k = 0; k < 3; k++) {
                    float lt = cl((t - at[k][2]) / 0.45f);
                    if (lt <= 0 || lt >= 1) continue;
                    float ox = at[k][0] * W;
                    float oy = at[k][1] * H;
                    for (int i = 0; i < 18; i++) {
                        double a = i * Math.PI * 2 / 18 + k;
                        float r = (14 + 26 * rnd[i + k * 10]) * d * (1f - (1f - lt) * (1f - lt));
                        float x = ox + (float) Math.cos(a) * r;
                        float y = oy + (float) Math.sin(a) * r + lt * lt * 12 * d;
                        q.setColor(PAL[(i + k) % PAL.length]);
                        q.setAlpha((int) (255 * (1f - lt)));
                        c.drawCircle(x, y, 2f * d, q);
                        q.setAlpha((int) (90 * (1f - lt)));
                        c.drawCircle(ox + (float) Math.cos(a) * r * 0.7f, oy + (float) Math.sin(a) * r * 0.7f, 1.4f * d, q);
                    }
                }
                q.setAlpha(255);
                break;
            }
            case 5: { // coins
                drawRow(c, 255, dn, -1);
                for (int i = 0; i < 12; i++) {
                    float lt = cl((t - rnd[i] * 0.25f) / 0.65f);
                    if (lt <= 0 || lt >= 1) continue;
                    float x = bx + (rnd[i + 12] - 0.2f) * W * 0.7f * lt;
                    float y = by - (float) Math.sin(lt * Math.PI) * (36 + 30 * rnd[i + 24]) * d + lt * lt * 20 * d;
                    float sx = Math.abs((float) Math.cos(lt * 14 + i));
                    q.setColor(0xFFFFC107);
                    q.setAlpha((int) (255 * (1f - cl((lt - 0.8f) / 0.2f))));
                    rf.set(x - 7 * d * sx, y - 7 * d, x + 7 * d * sx, y + 7 * d);
                    c.drawOval(rf, q);
                    q.setColor(0xFFFFE082);
                    rf.set(x - 4 * d * sx, y - 4 * d, x + 4 * d * sx, y + 4 * d);
                    c.drawOval(rf, q);
                }
                q.setAlpha(255);
                break;
            }
            case 6: { // stars
                drawRow(c, 255, dn, -1);
                float lt = cl(t / 0.85f);
                for (int i = 0; i < 16; i++) {
                    double a = rnd[i] * Math.PI * 2;
                    float r = (20 + 120 * rnd[i + 16]) * d * (1f - (1f - lt) * (1f - lt));
                    float x = bx + (float) Math.cos(a) * r * 1.5f;
                    float y = by + (float) Math.sin(a) * r * 0.55f;
                    q.setColor(i % 3 == 0 ? 0xFFFFFFFF : 0xFFFFD740);
                    q.setAlpha((int) (255 * (1f - lt * lt) * (0.6f + 0.4f * Math.sin(lt * 20 + i))));
                    star(c, x, y, (3 + 5 * rnd[i + 32]) * d * (1f - lt * 0.4f), lt * 4f + i, q);
                }
                q.setAlpha(255);
                break;
            }
            case 7: { // balloons
                drawRow(c, 255, dn, -1);
                for (int i = 0; i < 7; i++) {
                    float lt = cl((t - rnd[i] * 0.3f) / 0.65f);
                    if (lt <= 0) continue;
                    float x = (0.1f + 0.8f * (i + rnd[i + 10] * 0.5f) / 7f) * W + (float) Math.sin(lt * 7 + i) * 7 * d;
                    float y = H + 30 * d - lt * (H + 120 * d);
                    q.setColor(PAL[i % PAL.length]);
                    q.setAlpha((int) (255 * (1f - cl((lt - 0.85f) / 0.15f))));
                    rf.set(x - 9 * d, y - 12 * d, x + 9 * d, y + 12 * d);
                    c.drawOval(rf, q);
                    q.setColor(0x66FFFFFF);
                    c.drawCircle(x - 3 * d, y - 5 * d, 2.4f * d, q);
                    q.setStyle(Paint.Style.STROKE);
                    q.setStrokeWidth(1f * d);
                    q.setColor(0x99FFFFFF);
                    c.drawLine(x, y + 12 * d, x + (float) Math.sin(lt * 9 + i) * 3 * d, y + 28 * d, q);
                    q.setStyle(Paint.Style.FILL);
                }
                q.setAlpha(255);
                break;
            }
            case 8: { // shine sweep
                drawRow(c, 255, dn, -1);
                c.save();
                path.reset();
                rf.set(0, 0, W, H);
                path.addRoundRect(rf, 20 * d, 20 * d, Path.Direction.CW);
                c.clipPath(path);
                float x = lerp(-60 * d, W + 60 * d, ease(cl(t / 0.7f)));
                path.reset();
                path.moveTo(x - 24 * d, 0);
                path.lineTo(x + 8 * d, 0);
                path.lineTo(x - 8 * d - 20 * d, H);
                path.lineTo(x - 40 * d - 20 * d, H);
                path.close();
                q.setShader(new LinearGradient(x - 40 * d, 0, x + 10 * d, 0, 0x00FFFFFF, 0xAAFFFFFF, Shader.TileMode.CLAMP));
                c.drawPath(path, q);
                q.setShader(null);
                c.restore();
                for (int i = 0; i < 8; i++) {
                    float lt = cl((t - 0.1f - rnd[i] * 0.5f) / 0.25f);
                    if (lt <= 0 || lt >= 1) continue;
                    float sx = rnd[i + 8] * W;
                    float sy = rnd[i + 16] * H;
                    float sz = (3 + 5 * (float) Math.sin(lt * Math.PI)) * d;
                    q.setColor(0xFFFFFFFF);
                    c.drawRect(sx - sz, sy - 0.7f * d, sx + sz, sy + 0.7f * d, q);
                    c.drawRect(sx - 0.7f * d, sy - sz, sx + 0.7f * d, sy + sz, q);
                }
                break;
            }
            case 9: { // hop
                float lt = cl(t / 0.62f);
                float h = (float) Math.abs(Math.sin(lt * Math.PI * 2.0)) * (1f - lt) * 26 * d;
                float squash = lt > 0 && h < 2 * d ? 0.06f * (1f - lt) : 0f;
                c.save();
                c.translate(W / 2f, H);
                c.scale(1f + squash, 1f - squash);
                c.translate(-W / 2f, -H - h);
                drawRow(c, 255, dn, -1);
                c.restore();
                if (lt > 0.48f) dust(c, W * 0.5f, H, cl((lt - 0.48f) / 0.5f), 10, 0xFFB0BEC5, 1.2f);
                break;
            }
            case 10: { // pen strike
                float sp = ease(cl((t - 0.1f) / 0.45f));
                drawRow(c, 255, dn, sp);
                p.setTypeface(bold);
                p.setTextSize(16 * d);
                float tw = p.measureText("Buy groceries");
                float px = 66 * d + (tw + 6 * d) * sp;
                float py = H * 0.40f - 5 * d;
                if (sp > 0 && sp < 1) {
                    q.setColor(0xFF37474F);
                    path.reset();
                    path.moveTo(px, py);
                    path.lineTo(px + 14 * d, py - 14 * d);
                    path.lineTo(px + 18 * d, py - 10 * d);
                    path.lineTo(px + 4 * d, py + 4 * d);
                    path.close();
                    c.drawPath(path, q);
                    q.setColor(Pal.accent);
                    c.drawCircle(px, py, 1.6f * d, q);
                }
                break;
            }
            case 11: { // flip card
                float a = cl(t / 0.75f);
                float phi = a * 2f * (float) Math.PI;
                float sy = Math.abs((float) Math.cos(phi));
                c.save();
                c.translate(0, H / 2f);
                c.scale(1f, Math.max(0.01f, sy));
                c.translate(0, -H / 2f);
                if (phi > Math.PI / 2 && phi < 3 * Math.PI / 2) {
                    q.setColor(GREEN);
                    rf.set(0, 0, W, H);
                    c.drawRoundRect(rf, 20 * d, 20 * d, q);
                    p.setTypeface(bold);
                    p.setTextAlign(Paint.Align.CENTER);
                    p.setTextSize(22 * d);
                    p.setColor(0xFFFFFFFF);
                    c.drawText("Done", W / 2f + 14 * d, H / 2f - (p.ascent() + p.descent()) / 2f, p);
                    drawCheck(c, W / 2f - 36 * d, H / 2f, 22 * d, 1f, 0xFFFFFFFF, 3.4f * d);
                } else {
                    drawRow(c, 255, phi >= 3 * Math.PI / 2 ? 1f : 0f, -1);
                }
                c.restore();
                break;
            }
            case 12: { // big tick cover
                float in = ease(cl(t / 0.3f));
                float out = ease(cl((t - 0.58f) / 0.32f));
                drawRow(c, 255, dn, -1);
                float x0 = out * (W + 10 * d);
                float x1 = in * (W + 10 * d);
                if (x1 > x0) {
                    c.save();
                    path.reset();
                    rf.set(0, 0, W, H);
                    path.addRoundRect(rf, 20 * d, 20 * d, Path.Direction.CW);
                    c.clipPath(path);
                    q.setColor(GREEN);
                    c.drawRect(x0, 0, x1, H, q);
                    float cx = (x0 + x1) / 2f;
                    drawCheck(c, Math.min(cx, W / 2f), H / 2f, 44 * d, cl((t - 0.2f) / 0.28f) , 0xFFFFFFFF, 6 * d);
                    c.restore();
                }
                break;
            }
            case 13: { // hearts
                drawRow(c, 255, dn, -1);
                for (int i = 0; i < 12; i++) {
                    float lt = cl((t - rnd[i] * 0.4f) / 0.55f);
                    if (lt <= 0) continue;
                    float x = bx + (rnd[i + 12] - 0.2f) * W * 0.8f + (float) Math.sin(lt * 6 + i) * 8 * d;
                    float y = by + 10 * d - lt * (H * 0.9f + 20 * d);
                    q.setColor(i % 2 == 0 ? 0xFFFF4D6D : 0xFFFF8FA3);
                    q.setAlpha((int) (255 * (1f - lt * lt)));
                    heart(c, x, y, (4 + 4 * rnd[i + 24]) * d, q);
                }
                q.setAlpha(255);
                break;
            }
            case 14: { // emoji rain
                drawRow(c, 255, dn, -1);
                String[] em = {"🎉", "✨", "⭐", "🎊"};
                p.setTextAlign(Paint.Align.CENTER);
                p.setTypeface(regular);
                for (int i = 0; i < 16; i++) {
                    float lt = cl((t - rnd[i] * 0.4f) / 0.55f);
                    if (lt <= 0) continue;
                    p.setTextSize((13 + 8 * rnd[i + 16]) * d);
                    p.setAlpha((int) (255 * (1f - cl((lt - 0.8f) / 0.2f))));
                    c.drawText(em[i % em.length], rnd[i + 32] * W, -20 * d + lt * (H + 50 * d), p);
                }
                p.setAlpha(255);
                break;
            }
            case 15: { // medal
                drawRow(c, 255, dn, -1);
                float drop = cl((t - 0.1f) / 0.4f);
                float y = lerp(-40 * d, H / 2f, drop * drop);
                float bounce = drop >= 1f ? (float) Math.abs(Math.sin(cl((t - 0.5f) / 0.3f) * Math.PI * 2)) * (1f - cl((t - 0.5f) / 0.3f)) * 14 * d : 0;
                float al = 1f - cl((t - 0.85f) / 0.15f);
                float mx = W - 52 * d;
                q.setAlpha((int) (255 * al));
                q.setColor(0xFFE53935);
                path.reset();
                path.moveTo(mx - 10 * d, y - 36 * d);
                path.lineTo(mx - 2 * d, y - 6 * d);
                path.lineTo(mx - 14 * d, y - 6 * d);
                path.close();
                c.drawPath(path, q);
                path.reset();
                path.moveTo(mx + 10 * d, y - 36 * d);
                path.lineTo(mx + 2 * d, y - 6 * d);
                path.lineTo(mx + 14 * d, y - 6 * d);
                path.close();
                c.drawPath(path, q);
                q.setColor(0xFFFFC107);
                c.drawCircle(mx, y - bounce, 17 * d, q);
                q.setColor(0xFFFFE082);
                c.drawCircle(mx, y - bounce, 12 * d, q);
                drawCheck(c, mx, y - bounce, 15 * d, 1f, 0xFFB26A00, 2.6f * d);
                q.setAlpha(255);
                break;
            }
            case 16: { // spring tick
                float s = 1f + 1.1f * (float) Math.sin(cl(t / 0.45f) * Math.PI) * (1f - cl((t - 0.3f) / 0.4f));
                s += 0.08f * (float) Math.sin(cl((t - 0.45f) / 0.4f) * Math.PI * 3) * (1f - cl((t - 0.45f) / 0.4f));
                drawRow(c, 255, dn, -1);
                if (t < 0.85f) {
                    q.setColor(Pal.accent);
                    q.setAlpha((int) (255 * (1f - cl((t - 0.7f) / 0.15f))));
                    c.drawCircle(bx, by, 21 * d * s, q);
                    p.setAlpha(q.getAlpha());
                    drawCheck(c, bx, by, 17 * d * s, cl((t - 0.05f) / 0.3f), Pal.onAccent, 2.8f * d * s);
                    q.setAlpha(255);
                }
                q.setStyle(Paint.Style.STROKE);
                float rp = cl(t / 0.5f);
                q.setStrokeWidth(2.6f * d);
                q.setColor(Pal.accent);
                q.setAlpha((int) (200 * (1f - rp)));
                c.drawCircle(bx, by, 21 * d + rp * 34 * d, q);
                q.setAlpha(255);
                q.setStyle(Paint.Style.FILL);
                break;
            }
            case 17: { // rainbow bar
                drawRow(c, 255, dn, -1);
                float head = ease(cl(t / 0.6f)) * W;
                float tail = ease(cl((t - 0.4f) / 0.5f)) * W;
                if (head > tail) {
                    q.setShader(new LinearGradient(0, 0, W, 0, new int[]{0xFFFF5252, 0xFFFFD740, 0xFF69F0AE, 0xFF40C4FF, 0xFFB388FF},
                            null, Shader.TileMode.CLAMP));
                    rf.set(tail, H - 6 * d, head, H);
                    c.drawRoundRect(rf, 3 * d, 3 * d, q);
                    q.setAlpha(80);
                    rf.set(tail, H - 12 * d, head, H);
                    c.drawRoundRect(rf, 6 * d, 6 * d, q);
                    q.setShader(null);
                    q.setAlpha(255);
                }
                break;
            }
            case 18: { // laser scan
                float x = ease(cl(t / 0.7f)) * (W + 20 * d);
                c.save();
                c.clipRect(0, 0, Math.min(W, x), H);
                drawRow(c, 255, 1f, -1);
                c.restore();
                c.save();
                c.clipRect(Math.min(W, x), 0, W, H);
                drawRow(c, 255, 0f, 0f);
                c.restore();
                if (x < W + 10 * d) {
                    q.setShader(new LinearGradient(x - 24 * d, 0, x + 2 * d, 0, 0x0040C4FF, 0x8840C4FF, Shader.TileMode.CLAMP));
                    c.drawRect(x - 24 * d, 0, x + 2 * d, H, q);
                    q.setShader(null);
                    q.setColor(0xFFFFFFFF);
                    c.drawRect(x - 1 * d, 0, x + 1 * d, H, q);
                }
                break;
            }
            case 19: { // DONE stamp
                drawRow(c, 255, dn, -1);
                float hit = cl((t - 0.1f) / 0.16f);
                float scale = hit < 1f ? lerp(3.4f, 1f, hit * hit) : 1f + 0.07f * (float) Math.sin(Math.PI * cl((t - 0.26f) / 0.12f));
                float al = 1f - cl((t - 0.82f) / 0.18f);
                if (hit > 0.02f && al > 0) {
                    c.save();
                    c.translate(W * 0.72f, H / 2f);
                    c.rotate(-10f);
                    c.scale(scale, scale);
                    q.setStyle(Paint.Style.STROKE);
                    q.setStrokeWidth(3f * d);
                    q.setColor(GREEN);
                    q.setAlpha((int) (235 * al * Math.min(1f, hit * 2f)));
                    rf.set(-46 * d, -19 * d, 46 * d, 19 * d);
                    c.drawRoundRect(rf, 6 * d, 6 * d, q);
                    q.setStyle(Paint.Style.FILL);
                    q.setTypeface(bold);
                    q.setTextAlign(Paint.Align.CENTER);
                    q.setTextSize(24 * d);
                    c.drawText("DONE", 0, -(q.ascent() + q.descent()) / 2f, q);
                    q.setAlpha(255);
                    c.restore();
                }
                float ring = cl((t - 0.24f) / 0.25f);
                if (ring > 0 && ring < 1) {
                    q.setStyle(Paint.Style.STROKE);
                    q.setStrokeWidth(2.4f * d);
                    q.setColor(GREEN);
                    q.setAlpha((int) (200 * (1f - ring)));
                    c.drawCircle(W * 0.72f, H / 2f, ring * W * 0.4f, q);
                    q.setAlpha(255);
                    q.setStyle(Paint.Style.FILL);
                }
                break;
            }
            case 20: { // shockwave
                float lt = cl(t / 0.7f);
                float jolt = (float) Math.sin(lt * 40) * 2.4f * d * (1f - lt);
                c.save();
                c.translate(jolt, 0);
                drawRow(c, 255, dn, -1);
                c.restore();
                c.save();
                path.reset();
                rf.set(0, 0, W, H);
                path.addRoundRect(rf, 20 * d, 20 * d, Path.Direction.CW);
                c.clipPath(path);
                q.setStyle(Paint.Style.STROKE);
                for (int k = 0; k < 2; k++) {
                    float r = cl(lt - k * 0.12f);
                    q.setStrokeWidth((10 - 7 * r) * d);
                    q.setColor(k == 0 ? 0xFFFFFFFF : Pal.accent);
                    q.setAlpha((int) (210 * (1f - r)));
                    c.drawCircle(bx, by, r * W * 1.1f, q);
                }
                q.setAlpha(255);
                q.setStyle(Paint.Style.FILL);
                c.restore();
                break;
            }
            case 21: { // float up, ghost left behind
                float lt = cl(t / 0.7f);
                float lift = (float) Math.sin(lt * Math.PI) * 10 * d;
                if (lt < 1f) {
                    c.save();
                    c.translate(0, -lt * 22 * d);
                    drawRow(c, (int) (80 * (1f - lt)), 0f, 0f);
                    c.restore();
                }
                c.save();
                c.translate(W / 2f, H / 2f - lift);
                float s = 1f + 0.03f * (float) Math.sin(lt * Math.PI);
                c.scale(s, s);
                c.translate(-W / 2f, -H / 2f);
                drawRow(c, 255, dn, -1);
                c.restore();
                break;
            }
            case 22: { // golden glow
                drawRow(c, 255, dn, -1);
                float g = (float) Math.abs(Math.sin(cl(t / 0.8f) * Math.PI * 2)) * (1f - cl((t - 0.7f) / 0.3f));
                q.setStyle(Paint.Style.STROKE);
                for (int k = 3; k >= 0; k--) {
                    q.setStrokeWidth((2 + k * 3) * d);
                    q.setColor(0xFFFFC107);
                    q.setAlpha((int) (g * (k == 0 ? 255 : 55)));
                    rf.set(0, 0, W, H);
                    c.drawRoundRect(rf, 20 * d, 20 * d, q);
                }
                q.setAlpha(255);
                q.setStyle(Paint.Style.FILL);
                break;
            }
            case 23: { // progress fill
                drawRow(c, 255, dn, -1);
                float pg = ease(cl((t - 0.08f) / 0.5f));
                float al = 1f - cl((t - 0.8f) / 0.2f);
                q.setColor(0x33000000);
                q.setAlpha((int) (60 * al));
                rf.set(14 * d, H - 12 * d, W - 14 * d, H - 6 * d);
                c.drawRoundRect(rf, 3 * d, 3 * d, q);
                q.setColor(GREEN);
                q.setAlpha((int) (255 * al));
                rf.set(14 * d, H - 12 * d, 14 * d + (W - 28 * d) * pg, H - 6 * d);
                c.drawRoundRect(rf, 3 * d, 3 * d, q);
                p.setTypeface(bold);
                p.setTextAlign(Paint.Align.RIGHT);
                p.setTextSize(11 * d);
                p.setColor(GREEN);
                p.setAlpha((int) (255 * al));
                c.drawText(Math.round(pg * 100) + "%", W - 14 * d, H - 15 * d, p);
                p.setAlpha(255);
                float lt = cl((t - 0.58f) / 0.35f);
                for (int i = 0; i < 12 && lt > 0 && lt < 1; i++) {
                    double a = -Math.PI * (0.1 + 0.8 * i / 11.0);
                    float r = lt * (30 + 40 * rnd[i]) * d;
                    q.setColor(GREEN);
                    q.setAlpha((int) (255 * (1f - lt)));
                    c.drawCircle(14 * d + (W - 28 * d) * (i / 11f) + (float) Math.cos(a) * r * 0.2f,
                            H - 9 * d + (float) Math.sin(a) * r, 3f * d, q);
                    p.setAlpha(q.getAlpha());
                    drawCheck(c, 14 * d + (W - 28 * d) * (i / 11f), H - 9 * d + (float) Math.sin(a) * r, 5 * d, 1f, 0xFFFFFFFF, 1.1f * d);
                }
                q.setAlpha(255);
                break;
            }
            case 24: { // tick chain
                drawRow(c, 255, dn, -1);
                int n = 8;
                float al = 1f - cl((t - 0.82f) / 0.18f);
                for (int i = 0; i < n; i++) {
                    float lt = cl((t - 0.05f - i * 0.07f) / 0.18f);
                    if (lt <= 0) continue;
                    float over = lt < 1f ? 1f + 0.4f * (float) Math.sin(lt * Math.PI) : 1f;
                    float x = 22 * d + i * (W - 44 * d) / (n - 1);
                    q.setColor(GREEN);
                    q.setAlpha((int) (255 * al));
                    c.drawCircle(x, H - 10 * d, 6.5f * d * over * lt, q);
                    p.setAlpha(q.getAlpha());
                    drawCheck(c, x, H - 10 * d, 8.5f * d, cl(lt * 1.3f), 0xFFFFFFFF, 1.6f * d);
                }
                q.setAlpha(255);
                break;
            }
            case 25: { // sunburst
                drawRow(c, 255, dn, -1);
                c.save();
                path.reset();
                rf.set(0, 0, W, H);
                path.addRoundRect(rf, 20 * d, 20 * d, Path.Direction.CW);
                c.clipPath(path);
                float al = (float) Math.sin(cl(t / 0.8f) * Math.PI);
                c.translate(bx, by);
                c.rotate(t * 160f);
                q.setColor(0xFFFFD740);
                q.setAlpha((int) (140 * al));
                for (int i = 0; i < 12; i++) {
                    path.reset();
                    path.moveTo(0, 0);
                    path.lineTo((float) Math.cos(i * Math.PI / 6) * W * 1.2f, (float) Math.sin(i * Math.PI / 6) * W * 1.2f);
                    path.lineTo((float) Math.cos((i + 0.5) * Math.PI / 6) * W * 1.2f, (float) Math.sin((i + 0.5) * Math.PI / 6) * W * 1.2f);
                    path.close();
                    c.drawPath(path, q);
                }
                q.setAlpha(255);
                c.restore();
                break;
            }
            case 26: { // paper plane
                drawRow(c, 255, dn, -1);
                float u = ease(cl((t - 0.1f) / 0.7f));
                float x = lerp(bx, W + 30 * d, u);
                float y = by - (float) Math.sin(u * Math.PI * 0.9) * H * 0.9f;
                q.setStyle(Paint.Style.STROKE);
                q.setStrokeWidth(1.4f * d);
                q.setColor(Pal.accent);
                q.setPathEffect(new android.graphics.DashPathEffect(new float[]{4 * d, 4 * d}, 0));
                path.reset();
                path.moveTo(bx, by);
                for (float k = 0; k <= u; k += 0.03f) {
                    path.lineTo(lerp(bx, W + 30 * d, k), by - (float) Math.sin(k * Math.PI * 0.9) * H * 0.9f);
                }
                c.drawPath(path, q);
                q.setPathEffect(null);
                q.setStyle(Paint.Style.FILL);
                if (u < 1f) {
                    c.save();
                    c.translate(x, y);
                    c.rotate(-20f + 50f * u);
                    q.setColor(Pal.accent);
                    path.reset();
                    path.moveTo(-12 * d, -6 * d);
                    path.lineTo(14 * d, 0);
                    path.lineTo(-12 * d, 6 * d);
                    path.lineTo(-7 * d, 0);
                    path.close();
                    c.drawPath(path, q);
                    q.setColor(0x66FFFFFF);
                    path.reset();
                    path.moveTo(-7 * d, 0);
                    path.lineTo(14 * d, 0);
                    path.lineTo(-12 * d, 6 * d);
                    path.close();
                    c.drawPath(path, q);
                    c.restore();
                }
                break;
            }
            case 27: { // jelly
                float lt = cl(t / 0.75f);
                float w = (float) Math.sin(lt * 34) * (1f - lt) * (1f - lt);
                c.save();
                c.translate(W / 2f, H / 2f);
                c.scale(1f + 0.06f * w, 1f - 0.07f * w);
                c.skew(0.12f * w, 0f);
                c.translate(-W / 2f, -H / 2f);
                drawRow(c, 255, dn, -1);
                c.restore();
                float fl = (1f - cl(t / 0.5f));
                if (fl > 0) {
                    q.setColor(GREEN);
                    q.setAlpha((int) (110 * fl));
                    rf.set(0, 0, W, H);
                    c.drawRoundRect(rf, 20 * d, 20 * d, q);
                    q.setAlpha(255);
                }
                break;
            }
            case 28: { // iris
                drawRow(c, 255, dn, -1);
                float close = ease(cl(t / 0.4f));
                float open = ease(cl((t - 0.55f) / 0.35f));
                float r = lerp(W, 26 * d, close);
                r = lerp(r, W * 1.1f, open);
                c.save();
                path.reset();
                rf.set(0, 0, W, H);
                path.addRoundRect(rf, 20 * d, 20 * d, Path.Direction.CW);
                c.clipPath(path);
                Path hole = new Path();
                hole.addRect(0, 0, W, H, Path.Direction.CW);
                hole.addCircle(W * 0.5f - (1f - open) * (W * 0.5f - bx) * close, by, r, Path.Direction.CCW);
                hole.setFillType(Path.FillType.EVEN_ODD);
                q.setColor(0xDD000000);
                c.drawPath(hole, q);
                c.restore();
                float fl = cl(1f - Math.abs(t - 0.5f) / 0.07f);
                if (fl > 0) {
                    q.setColor(0xFFFFFFFF);
                    q.setAlpha((int) (200 * fl));
                    c.drawCircle(bx, by, 30 * d, q);
                    q.setAlpha(255);
                }
                break;
            }
            default: { // 29 fountain
                drawRow(c, 255, dn, -1);
                for (int i = 0; i < 34; i++) {
                    float lt = cl((t - rnd[i] * 0.25f) / 0.6f);
                    if (lt <= 0 || lt >= 1) continue;
                    float vx = (rnd[i + 34] - 0.25f) * 150 * d;
                    float vy = -(110 + 90 * rnd[i + 60]) * d;
                    float x = bx + vx * lt;
                    float y = by + vy * lt + 230 * d * lt * lt;
                    q.setColor(i % 3 == 0 ? 0xFFFFFFFF : i % 3 == 1 ? GREEN : 0xFF69F0AE);
                    q.setAlpha((int) (255 * (1f - lt * lt)));
                    c.drawCircle(x, y, (2f + 1.6f * rnd[i + 10]) * d, q);
                    q.setAlpha((int) (70 * (1f - lt)));
                    c.drawCircle(bx + vx * lt * 0.9f, by + vy * lt * 0.9f + 230 * d * lt * lt * 0.8f, 1.3f * d, q);
                }
                q.setAlpha(255);
                break;
            }
        }
        q.setStyle(Paint.Style.FILL);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (phase != IDLE) return true;
        int a = e.getActionMasked();
        switch (a) {
            case MotionEvent.ACTION_DOWN:
                if (anim != null) anim.cancel();
                downX = e.getX();
                downY = e.getY();
                swiping = false;
                armed = false;
                return true;
            case MotionEvent.ACTION_MOVE: {
                float mx = e.getX() - downX;
                float my = e.getY() - downY;
                if (!swiping && Math.abs(mx) > slop * 1.5f && Math.abs(mx) > Math.abs(my) * 1.5f) {
                    swiping = true;
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                }
                if (swiping) {
                    dx = Math.max(0f, mx);
                    boolean past = dx >= limit();
                    if (past && !armed) Hap.tick(this);
                    armed = past;
                    invalidate();
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (swiping) {
                    if (a == MotionEvent.ACTION_UP && dx >= limit()) commit();
                    else springBack();
                }
                swiping = false;
                return true;
            default:
                return true;
        }
    }

    private void springBack() {
        final float from = dx;
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(260);
        anim.setInterpolator(new OvershootInterpolator(1.6f));
        anim.addUpdateListener(a -> {
            dx = Math.max(0f, from * (1f - (Float) a.getAnimatedValue()));
            invalidate();
        });
        anim.start();
    }

    private void commit() {
        Hap.confirm(this);
        dx = 0;
        t = 0;
        phase = COMMIT;
        count++;
        if (onDone != null) onDone.run();
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(DUR[style]);
        anim.setInterpolator(new LinearInterpolator());
        anim.addUpdateListener(a -> {
            t = (Float) a.getAnimatedValue();
            invalidate();
        });
        anim.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationCancel(Animator an) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator an) {
                if (cancelled) return;
                phase = HOLD;
                invalidate();
                pending = () -> restore();
                postDelayed(pending, 1100);
            }
        });
        anim.start();
    }

    private void restore() {
        pending = null;
        rt = 0;
        phase = RESTORE;
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(450);
        anim.addUpdateListener(a -> {
            rt = (Float) a.getAnimatedValue();
            invalidate();
        });
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator an) {
                phase = IDLE;
                invalidate();
            }
        });
        anim.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (pending != null) removeCallbacks(pending);
        if (anim != null) anim.cancel();
        super.onDetachedFromWindow();
    }
}
