package app.privatetodo;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.view.View;
import android.view.animation.LinearInterpolator;

import java.util.Random;

/**
 * The main-screen title ("Tasks") with a short animation that plays when the screen opens and now and then
 * after that. Twenty styles; Settings > Design lab shows them all. Between bursts it is plain text and
 * nothing runs, so it costs no battery.
 */
final class TitleFx extends View {
    static final String[] NAMES = {
            "Typewriter", "Wave", "Shimmer", "Drop bounce", "Rainbow", "Neon pulse", "Glitch", "Card flip",
            "Underline draw", "Breathing", "Spotlight", "Domino", "Sparkle", "Ink fill", "Tick draw", "Slide in",
            "Zoom in", "Strike and rewrite", "Rubber", "Scramble"};
    static final String[] NOTES = {
            "Letters are typed out one by one with a blinking cursor.",
            "The letters ride a wave that slowly calms down.",
            "A bright band of light sweeps across the word.",
            "Each letter drops in and bounces to a stop.",
            "Rainbow colours flow through the letters.",
            "The word glows like a neon sign, pulsing.",
            "A quick digital glitch with colour split.",
            "Letters turn over one after another like cards.",
            "An accent line draws itself under the word.",
            "The word gently grows and shrinks, like breathing.",
            "A spotlight moves over the word and lights it up.",
            "Letters tip over like dominoes, then stand up again.",
            "Little stars twinkle around the word.",
            "The word fills with colour like a glass of liquid.",
            "A tick draws itself after the word.",
            "Letters slide in from the left, one by one.",
            "The word zooms in from big to normal.",
            "The word is struck out, then rewritten.",
            "Letters squash and stretch like rubber.",
            "Letters scramble into random symbols, then settle."};

    private static final int[] BURST = {3200, 3000, 2600, 2600, 3000, 3000, 2400, 2800, 2600, 3200, 2800, 3000,
            3000, 3000, 2400, 2600, 1600, 3000, 2800, 2600};
    private static final String SCRAMBLE = "#@%&$*?!ABCDEFGHXYZ0123";

    private final int style;
    private final String text;
    private final float d;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint q = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final float[] xs;
    private final float textW;
    private final float base; // baseline
    private final int boxH;
    private final long rest; // pause between plays (ms)
    private final Random rnd = new Random(7);
    private final float[] sx = new float[8];
    private final float[] sy = new float[8];
    private final float[] sp = new float[8];

    private float u = 1f; // 0..1 through the burst, 1 = at rest
    private ValueAnimator anim;
    private Runnable again;
    private boolean attached;

    TitleFx(Context c, String text, float textSp, int style, long restMs) {
        super(c);
        this.text = text;
        this.style = Math.max(0, Math.min(NAMES.length - 1, style));
        this.rest = restMs;
        d = c.getResources().getDisplayMetrics().density;
        Fonts.init(c);
        Typeface bold = Fonts.face(true);
        p.setTypeface(bold);
        p.setTextSize(textSp * c.getResources().getDisplayMetrics().scaledDensity);
        p.setTextAlign(Paint.Align.LEFT);
        Paint.FontMetrics fm = p.getFontMetrics();
        base = 6 * d - fm.top;
        boxH = Math.round(base + fm.bottom + 6 * d);
        textW = p.measureText(text);
        xs = new float[text.length() + 1];
        for (int i = 0; i <= text.length(); i++) xs[i] = p.measureText(text, 0, i);
        for (int i = 0; i < sx.length; i++) {
            sx[i] = rnd.nextFloat();
            sy[i] = rnd.nextFloat();
            sp[i] = rnd.nextFloat();
        }
    }

    @Override
    protected void onMeasure(int w, int h) {
        setMeasuredDimension(resolveSize(Math.round(textW + 44 * d), w), boxH);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        schedule(450);
    }

    @Override
    protected void onDetachedFromWindow() {
        attached = false;
        if (again != null) removeCallbacks(again);
        again = null;
        if (anim != null) anim.cancel();
        u = 1f;
        super.onDetachedFromWindow();
    }

    private void schedule(long delay) {
        if (again != null) removeCallbacks(again);
        again = () -> play();
        postDelayed(again, delay);
    }

    private void play() {
        if (!attached) return;
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(BURST[style]);
        anim.setInterpolator(new LinearInterpolator());
        anim.addUpdateListener(a -> {
            u = (Float) a.getAnimatedValue();
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
                u = 1f;
                invalidate();
                if (attached && rest > 0) schedule(rest);
            }
        });
        anim.start();
    }

    // ------------------------------------------------------------------ helpers

    private static float cl(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static float ease(float v) {
        v = cl(v);
        return v * v * (3 - 2 * v);
    }

    private void ink() {
        p.setShader(null);
        p.setShadowLayer(0, 0, 0, 0);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Pal.ink);
        p.setAlpha(255);
    }

    private void plain(Canvas c) {
        ink();
        c.drawText(text, 0, base, p);
    }

    private void letter(Canvas c, int i) {
        c.drawText(text, i, i + 1, xs[i], base, p);
    }

    private float letterW(int i) {
        return xs[i + 1] - xs[i];
    }

    @Override
    protected void onDraw(Canvas c) {
        if (u >= 1f) {
            plain(c);
            return;
        }
        ink();
        int n = text.length();
        switch (style) {
            case 0: { // typewriter
                int shown = Math.min(n, (int) (u / 0.62f * n));
                c.drawText(text, 0, shown, 0, base, p);
                boolean blink = ((int) (u * 9)) % 2 == 0;
                if (u < 0.92f && blink) {
                    q.setColor(Pal.accent);
                    q.setStyle(Paint.Style.FILL);
                    float cx = xs[shown] + 3 * d;
                    c.drawRect(cx, base + p.getFontMetrics().ascent * 0.92f, cx + 3 * d, base + 2 * d, q);
                }
                break;
            }
            case 1: { // wave
                float env = 1f - ease(u);
                for (int i = 0; i < n; i++) {
                    float y = (float) Math.sin(u * 14f - i * 0.8f) * 9 * d * env;
                    c.save();
                    c.translate(0, y);
                    letter(c, i);
                    c.restore();
                }
                break;
            }
            case 2: { // shimmer
                float cx = -textW * 0.3f + u * textW * 1.6f;
                p.setShader(new LinearGradient(cx - textW * 0.22f, 0, cx + textW * 0.22f, 0,
                        new int[]{Pal.ink, mix(Pal.ink, Pal.accent, 0.35f), 0xFFFFFFFF, mix(Pal.ink, Pal.accent, 0.35f), Pal.ink},
                        new float[]{0f, 0.3f, 0.5f, 0.7f, 1f}, android.graphics.Shader.TileMode.CLAMP));
                c.drawText(text, 0, base, p);
                p.setShader(null);
                break;
            }
            case 3: { // drop bounce
                for (int i = 0; i < n; i++) {
                    float lt = cl((u * 1.5f - i * 0.1f) / 0.55f);
                    if (lt <= 0f) continue;
                    float bounce = lt >= 1f ? 0f : Math.abs((float) Math.cos(lt * Math.PI * 2.5)) * (1f - lt);
                    c.save();
                    c.translate(0, -bounce * 38 * d);
                    p.setAlpha((int) (255 * cl(lt * 4f)));
                    letter(c, i);
                    c.restore();
                }
                p.setAlpha(255);
                break;
            }
            case 4: { // rainbow
                plain(c);
                float a = (float) Math.sin(Math.PI * u);
                int[] cols = new int[7];
                float[] pos = new float[7];
                for (int k = 0; k < 7; k++) {
                    cols[k] = Color.HSVToColor(new float[]{(k * 55f + u * 540f) % 360f, 0.7f, 0.95f});
                    pos[k] = k / 6f;
                }
                p.setShader(new LinearGradient(0, 0, Math.max(1f, textW), 0, cols, pos, android.graphics.Shader.TileMode.CLAMP));
                p.setAlpha((int) (255 * a));
                c.drawText(text, 0, base, p);
                p.setShader(null);
                p.setAlpha(255);
                break;
            }
            case 5: { // neon pulse
                float a = (float) Math.sin(Math.PI * u);
                float glow = (0.5f + 0.5f * (float) Math.sin(u * 22f)) * 16 * d * a + 2 * d;
                p.setColor(mix(Pal.ink, Pal.accent, a));
                p.setShadowLayer(glow, 0, 0, Pal.accent);
                c.drawText(text, 0, base, p);
                p.setShadowLayer(0, 0, 0, 0);
                break;
            }
            case 6: { // glitch
                int frame = (int) (u * 60);
                boolean hit = (u > 0.18f && u < 0.3f) || (u > 0.5f && u < 0.62f) || (u > 0.8f && u < 0.9f);
                if (!hit) {
                    plain(c);
                    break;
                }
                Random r = new Random(frame * 31L + 5);
                float j = (2 + r.nextFloat() * 5) * d;
                p.setColor(0xFFFF2D55);
                p.setAlpha(190);
                c.drawText(text, -j, base, p);
                p.setColor(0xFF2DE2FF);
                c.drawText(text, j, base, p);
                ink();
                c.save();
                c.clipRect(0, 0, textW + 40 * d, base - p.getFontMetrics().ascent * 0.35f);
                c.translate((r.nextFloat() - 0.5f) * 10 * d, 0);
                c.drawText(text, 0, base, p);
                c.restore();
                c.save();
                c.clipRect(0, base - p.getFontMetrics().ascent * 0.35f, textW + 40 * d, boxH);
                c.drawText(text, 0, base, p);
                c.restore();
                break;
            }
            case 7: { // card flip
                for (int i = 0; i < n; i++) {
                    float lt = cl((u * 1.5f - i * 0.12f) / 0.6f);
                    float ang = lt * (float) Math.PI * 2f;
                    float sc = (float) Math.cos(ang);
                    float cx = xs[i] + letterW(i) / 2f;
                    float cy = base + p.getFontMetrics().ascent * 0.35f;
                    c.save();
                    c.translate(cx, cy);
                    c.scale(1f, Math.abs(sc) < 0.04f ? 0.04f : sc);
                    c.translate(-cx, -cy);
                    letter(c, i);
                    c.restore();
                }
                break;
            }
            case 8: { // underline draw
                plain(c);
                float grow = ease(u / 0.45f);
                float shrink = ease((u - 0.78f) / 0.22f);
                float x0 = textW * shrink;
                float x1 = textW * grow;
                if (x1 > x0) {
                    q.setStyle(Paint.Style.STROKE);
                    q.setStrokeCap(Paint.Cap.ROUND);
                    q.setStrokeWidth(3.2f * d);
                    q.setColor(Pal.accent);
                    c.drawLine(x0, base + 4 * d, x1, base + 4 * d, q);
                }
                break;
            }
            case 9: { // breathing
                float s = 1f + 0.08f * (float) Math.sin(u * Math.PI * 4f) * (1f - ease((u - 0.6f) / 0.4f));
                c.save();
                c.scale(s, s, 0, boxH / 2f);
                c.drawText(text, 0, base, p);
                c.restore();
                break;
            }
            case 10: { // spotlight
                p.setAlpha(70);
                c.drawText(text, 0, base, p);
                p.setAlpha(255);
                float cx = -textW * 0.15f + u * textW * 1.3f;
                float rad = boxH * 0.85f;
                c.save();
                path.reset();
                path.addCircle(cx, boxH / 2f, rad, Path.Direction.CW);
                c.clipPath(path);
                p.setColor(Pal.accent);
                c.drawText(text, 0, base, p);
                c.restore();
                q.setStyle(Paint.Style.FILL);
                q.setColor((Pal.accent & 0x00FFFFFF) | 0x26000000);
                c.drawCircle(cx, boxH / 2f, rad, q);
                if (u > 0.85f) {
                    ink();
                    p.setAlpha((int) (255 * ease((u - 0.85f) / 0.15f)));
                    c.drawText(text, 0, base, p);
                    p.setAlpha(255);
                }
                break;
            }
            case 11: { // domino
                for (int i = 0; i < n; i++) {
                    float lt = cl((u * 1.6f - i * 0.12f) / 0.6f);
                    float ang = -75f * (float) Math.sin(lt * Math.PI);
                    c.save();
                    c.rotate(ang, xs[i] + letterW(i), base);
                    letter(c, i);
                    c.restore();
                }
                break;
            }
            case 12: { // sparkle
                plain(c);
                q.setStyle(Paint.Style.FILL);
                for (int i = 0; i < sx.length; i++) {
                    float lt = cl((u * 1.6f - sp[i] * 0.8f) / 0.5f);
                    if (lt <= 0f || lt >= 1f) continue;
                    float sc = (float) Math.sin(lt * Math.PI);
                    float x = sx[i] * (textW + 12 * d) - 4 * d;
                    float y = 4 * d + sy[i] * (boxH - 8 * d);
                    float r = (3 + 6 * sp[i]) * d * sc;
                    q.setColor(i % 2 == 0 ? Pal.accent : 0xFFFFC107);
                    star(c, x, y, r);
                }
                break;
            }
            case 13: { // ink fill
                p.setAlpha(60);
                c.drawText(text, 0, base, p);
                p.setAlpha(255);
                float level = 1f - ease(u / 0.55f);
                float top = (boxH + 6 * d) * level - 3 * d;
                c.save();
                path.reset();
                path.moveTo(0, boxH + 2 * d);
                path.lineTo(0, top);
                int steps = 12;
                for (int k = 0; k <= steps; k++) {
                    float x = (textW + 20 * d) * k / steps;
                    path.lineTo(x, top + (float) Math.sin(u * 16f + k * 0.9f) * 3 * d * (1f - level * 0.3f));
                }
                path.lineTo(textW + 20 * d, boxH + 2 * d);
                path.close();
                c.clipPath(path);
                p.setColor(Pal.accent);
                c.drawText(text, 0, base, p);
                c.restore();
                if (u > 0.7f) {
                    ink();
                    p.setAlpha((int) (255 * ease((u - 0.7f) / 0.3f)));
                    c.drawText(text, 0, base, p);
                    p.setAlpha(255);
                }
                break;
            }
            case 14: { // tick draw
                plain(c);
                float prog = ease((u - 0.1f) / 0.45f);
                float fade = 1f - ease((u - 0.8f) / 0.2f);
                float s = 20 * d;
                float ax = textW + 12 * d;
                float ay = base - s * 0.45f;
                float l1 = (float) Math.hypot(0.36f * s, 0.36f * s);
                float l2 = (float) Math.hypot(0.67f * s, 0.74f * s);
                float at = prog * (l1 + l2);
                path.reset();
                path.moveTo(ax, ay);
                if (at <= l1) {
                    path.lineTo(ax + 0.36f * s * (at / l1), ay + 0.36f * s * (at / l1));
                } else {
                    path.lineTo(ax + 0.36f * s, ay + 0.36f * s);
                    float f = (at - l1) / l2;
                    path.lineTo(ax + 0.36f * s + 0.67f * s * f, ay + 0.36f * s - 0.74f * s * f);
                }
                q.setStyle(Paint.Style.STROKE);
                q.setStrokeCap(Paint.Cap.ROUND);
                q.setStrokeJoin(Paint.Join.ROUND);
                q.setStrokeWidth(4 * d);
                q.setColor(Pal.accent);
                q.setAlpha((int) (255 * fade));
                if (prog > 0f) c.drawPath(path, q);
                q.setAlpha(255);
                break;
            }
            case 15: { // slide in
                for (int i = 0; i < n; i++) {
                    float lt = ease((u * 1.5f - i * 0.1f) / 0.5f);
                    if (lt <= 0f) continue;
                    c.save();
                    c.translate(-(1f - lt) * 70 * d, 0);
                    p.setAlpha((int) (255 * lt));
                    letter(c, i);
                    c.restore();
                }
                p.setAlpha(255);
                break;
            }
            case 16: { // zoom in
                float lt = ease(u / 0.9f);
                float s = 2.4f - 1.4f * lt;
                c.save();
                c.scale(s, s, 0, boxH / 2f);
                p.setAlpha((int) (255 * cl(u / 0.3f)));
                c.drawText(text, 0, base, p);
                c.restore();
                p.setAlpha(255);
                break;
            }
            case 17: { // strike and rewrite
                float strike = ease(u / 0.3f);
                float gone = ease((u - 0.45f) / 0.2f);
                float back = ease((u - 0.7f) / 0.3f);
                float a = u < 0.45f ? 1f : (u < 0.7f ? 1f - gone : back);
                p.setAlpha((int) (255 * a));
                c.drawText(text, 0, base, p);
                p.setAlpha(255);
                if (u < 0.7f) {
                    q.setStyle(Paint.Style.STROKE);
                    q.setStrokeCap(Paint.Cap.ROUND);
                    q.setStrokeWidth(3.4f * d);
                    q.setColor(Pal.overdue);
                    q.setAlpha((int) (255 * (1f - gone)));
                    float y = base + p.getFontMetrics().ascent * 0.32f;
                    c.drawLine(-2 * d, y, -2 * d + (textW + 4 * d) * strike, y, q);
                    q.setAlpha(255);
                }
                break;
            }
            case 18: { // rubber
                for (int i = 0; i < n; i++) {
                    float lt = cl((u * 1.6f - i * 0.1f) / 0.55f);
                    float k = (float) Math.sin(lt * Math.PI) * (lt < 1f ? 1f : 0f);
                    float wob = (float) Math.cos(lt * Math.PI * 3f) * (1f - lt);
                    float cx = xs[i] + letterW(i) / 2f;
                    c.save();
                    c.scale(1f + 0.3f * k * Math.abs(wob + 0.6f), 1f - 0.35f * k, cx, base);
                    letter(c, i);
                    c.restore();
                }
                break;
            }
            default: { // 19: scramble
                Random r = new Random((long) (u * 28) * 977L + 3);
                for (int i = 0; i < n; i++) {
                    boolean settled = u * 1.35f > i * 0.13f + 0.35f;
                    if (settled) {
                        letter(c, i);
                    } else {
                        String g = String.valueOf(SCRAMBLE.charAt(r.nextInt(SCRAMBLE.length())));
                        p.setColor(mix(Pal.ink, Pal.accent, 0.7f));
                        p.setAlpha(190);
                        c.drawText(g, xs[i], base, p);
                        ink();
                    }
                }
                break;
            }
        }
        ink();
    }

    private void star(Canvas c, float x, float y, float r) {
        path.reset();
        path.moveTo(x, y - r);
        path.quadTo(x, y, x + r * 0.9f, y);
        path.quadTo(x, y, x, y + r);
        path.quadTo(x, y, x - r * 0.9f, y);
        path.quadTo(x, y, x, y - r);
        path.close();
        c.drawPath(path, q);
    }

    private static int mix(int a, int b, float t) {
        t = cl(t);
        int r = Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }
}
