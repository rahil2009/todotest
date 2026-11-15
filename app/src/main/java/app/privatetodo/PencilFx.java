package app.privatetodo;

import android.graphics.Canvas;
import android.graphics.CornerPathEffect;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

import java.util.Random;

/**
 * The hand-drawn looks: ten pencil circles and ten scribbles, drawn from a rectangle and a seed (so a shape looks the
 * same every time it is redrawn). Also the squircle outline. Used by the note and by the Settings previews.
 */
final class PencilFx {
    private PencilFx() {
    }

    static final String[] CIRCLE_NAMES = {"Soft graphite", "Bold pencil", "Sketchy double", "Neat ring", "Loose swoop",
            "Red pen", "Blue ink", "Dotted pencil", "Triple loop", "Highlighter ring"};
    static final String[] SCRIBBLE_NAMES = {"Tight zigzag", "Loose zigzag", "Cross-hatch", "Wavy underline",
            "Strike-through", "Loops", "Highlighter", "Graphite fill", "Double underline", "Crown"};

    private static final int GRAPHITE = 0xFF3A3A3A, RED = 0xFFD2452B, BLUE = 0xFF2E6FD9, GREEN = 0xFF2FA866,
            YELLOW = 0xFFFFD23F;

    // width dp, passes, wobble dp, overshoot (turns), alpha, dash (0 none), grow dp per pass
    private static final float[][] CIRCLE = {
            {1.5f, 2, 1.6f, 0.12f, 200, 0, 1.4f},
            {3.2f, 1, 1.2f, 0.08f, 230, 0, 0},
            {1.3f, 3, 2.6f, 0.15f, 190, 0, 2.2f},
            {1.8f, 1, 0.5f, 0.02f, 230, 0, 0},
            {1.8f, 1, 3.4f, 0.32f, 220, 0, 0},
            {2.0f, 2, 1.8f, 0.14f, 235, 0, 1.6f},
            {1.8f, 1, 1.4f, 0.1f, 235, 0, 0},
            {2.0f, 1, 1.2f, 0.06f, 235, 1, 0},
            {1.3f, 3, 1.6f, 0.45f, 200, 0, 1.8f},
            {9.0f, 1, 1.4f, 0.06f, 110, 0, 0}};

    private static final int[] CIRCLE_COL = {GRAPHITE, GRAPHITE, GRAPHITE, GRAPHITE, GRAPHITE, RED, BLUE, GRAPHITE, GREEN, YELLOW};

    static Path squircle(RectF r) {
        Path p = new Path();
        float w = r.width(), h = r.height();
        float s = Math.min(Math.min(w, h) / 2f, Math.min(w, h) * 0.46f);
        float k = 0.37f;
        p.moveTo(r.left + s, r.top);
        p.lineTo(r.right - s, r.top);
        p.cubicTo(r.right - k * s, r.top, r.right, r.top + k * s, r.right, r.top + s);
        p.lineTo(r.right, r.bottom - s);
        p.cubicTo(r.right, r.bottom - k * s, r.right - k * s, r.bottom, r.right - s, r.bottom);
        p.lineTo(r.left + s, r.bottom);
        p.cubicTo(r.left + k * s, r.bottom, r.left, r.bottom - k * s, r.left, r.bottom - s);
        p.lineTo(r.left, r.top + s);
        p.cubicTo(r.left, r.top + k * s, r.left + k * s, r.top, r.left + s, r.top);
        p.close();
        return p;
    }

    /** A superellipse (n = 4): the original "box" outline. */
    static Path superellipse(RectF r) {
        Path p = new Path();
        int steps = 64;
        float cx = r.centerX(), cy = r.centerY(), a = r.width() / 2f, b = r.height() / 2f;
        for (int i = 0; i <= steps; i++) {
            double th = 2 * Math.PI * i / steps;
            double ct = Math.cos(th), st = Math.sin(th);
            float x = cx + (float) (a * Math.signum(ct) * Math.pow(Math.abs(ct), 0.5));
            float y = cy + (float) (b * Math.signum(st) * Math.pow(Math.abs(st), 0.5));
            if (i == 0) p.moveTo(x, y);
            else p.lineTo(x, y);
        }
        p.close();
        return p;
    }

    private static Paint pen(float w, int color, int alpha, float d) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setStrokeWidth(w * d);
        p.setColor((color & 0x00FFFFFF) | (alpha << 24));
        p.setPathEffect(new CornerPathEffect(10 * d));
        return p;
    }

    /** A pencil circle round the rectangle. */
    static void circle(Canvas c, RectF r, int style, long seed, float d) {
        float[] s = CIRCLE[Math.max(0, Math.min(9, style))];
        Random rnd = new Random(seed * 7919L + style);
        float cx = r.centerX(), cy = r.centerY();
        float a0 = r.width() / 2f + 4 * d, b0 = r.height() / 2f + 3 * d;
        int passes = (int) s[1];
        for (int pass = 0; pass < passes; pass++) {
            float grow = s[6] * d * pass;
            float a = a0 + grow, b = b0 + grow * 0.7f;
            double start = rnd.nextDouble() * 0.9 + pass * 0.5;
            double turns = 1.0 + s[3] + rnd.nextDouble() * 0.04;
            double ph1 = rnd.nextDouble() * 6.28, ph2 = rnd.nextDouble() * 6.28, ph3 = rnd.nextDouble() * 6.28;
            Path p = new Path();
            int n = Math.max(24, (int) (turns * 72));
            for (int i = 0; i <= n; i++) {
                double t = start + 2 * Math.PI * turns * i / n;
                double drift = 1 + 0.035 * (i / (double) n); // a pencil circle never quite closes: it spirals out
                double wob = s[2] * d * (0.55 * Math.sin(3 * t + ph1) + 0.3 * Math.sin(5 * t + ph2) + 0.2 * Math.sin(9 * t + ph3));
                float x = (float) (cx + (a * drift + wob) * Math.cos(t));
                float y = (float) (cy + (b * drift + wob * 0.8) * Math.sin(t));
                if (i == 0) p.moveTo(x, y);
                else p.lineTo(x, y);
            }
            Paint main = pen(s[0], CIRCLE_COL[Math.max(0, Math.min(9, style))], (int) s[4], d);
            if (s[5] > 0) main.setPathEffect(new DashPathEffect(new float[]{2 * d, 5 * d}, 0));
            c.drawPath(p, main);
            if (s[0] < 4 && s[5] == 0) { // the pencil's grain: a thinner, fainter second pass beside it
                Paint grain = pen(s[0] * 0.5f, CIRCLE_COL[Math.max(0, Math.min(9, style))], (int) (s[4] * 0.5f), d);
                c.save();
                c.translate(0.6f * d, 0.5f * d);
                c.drawPath(p, grain);
                c.restore();
            }
        }
    }

    /** A scribble over (or under) the text in the rectangle. */
    static void scribble(Canvas c, RectF r, int style, long seed, float d) {
        Random rnd = new Random(seed * 104729L + style);
        float l = r.left - 2 * d, rr = r.right + 2 * d, t = r.top + 2 * d, b = r.bottom - 1 * d;
        float h = b - t, mid = (t + b) / 2f;
        Path p = new Path();
        Paint pen;
        switch (style) {
            case 0: { // tight zigzag over the whole text
                pen = pen(1.2f, GRAPHITE, 150, d);
                float x = l;
                p.moveTo(x, t);
                boolean up = false;
                while (x < rr) {
                    x += 4 * d + rnd.nextFloat() * 2 * d;
                    p.lineTo(Math.min(x, rr), up ? t + rnd.nextFloat() * 2 * d : b - rnd.nextFloat() * 2 * d);
                    up = !up;
                }
                c.drawPath(p, pen);
                break;
            }
            case 1: { // loose zigzag
                pen = pen(1.8f, GRAPHITE, 190, d);
                float x = l - 4 * d;
                p.moveTo(x, mid);
                boolean up = false;
                while (x < rr + 4 * d) {
                    x += 13 * d + rnd.nextFloat() * 5 * d;
                    p.lineTo(x, (up ? t - 2 * d : b + 2 * d) + rnd.nextFloat() * 3 * d);
                    up = !up;
                }
                c.drawPath(p, pen);
                break;
            }
            case 2: { // cross-hatch
                pen = pen(1.0f, GRAPHITE, 130, d);
                for (float x = l - h; x < rr; x += 7 * d) {
                    p.moveTo(x, b);
                    p.lineTo(x + h, t);
                }
                for (float x = l; x < rr + h; x += 7 * d) {
                    p.moveTo(x, b);
                    p.lineTo(x - h, t);
                }
                c.save();
                c.clipRect(l, t - 2 * d, rr, b + 2 * d);
                c.drawPath(p, pen);
                c.restore();
                break;
            }
            case 3: { // wavy underline
                pen = pen(2.0f, RED, 230, d);
                p.moveTo(l, b + 1 * d);
                for (float x = l; x <= rr; x += 3 * d) {
                    p.lineTo(x, b + 2 * d + (float) Math.sin((x - l) / (4.5 * d)) * 2.4f * d + rnd.nextFloat() * 0.6f * d);
                }
                c.drawPath(p, pen);
                break;
            }
            case 4: { // a scribbled strike-through
                pen = pen(1.6f, GRAPHITE, 215, d);
                for (int pass = 0; pass < 2; pass++) {
                    Path q = new Path();
                    q.moveTo(l - 3 * d, mid + (pass == 0 ? 2 : -2) * d);
                    for (float x = l; x <= rr + 3 * d; x += 6 * d) {
                        q.lineTo(x, mid + (float) Math.sin((x - l) / (3.2 * d) + pass * 2) * (h * 0.28f) + rnd.nextFloat() * 1.5f * d);
                    }
                    c.drawPath(q, pen);
                }
                break;
            }
            case 5: { // loops, like cursive e's, along the bottom
                pen = pen(1.5f, BLUE, 220, d);
                p.moveTo(l, b);
                for (float x = l; x < rr; x += 9 * d) {
                    p.cubicTo(x + 4 * d, b - h * 0.9f, x + 12 * d, b - h * 0.9f, x + 8 * d, b + 1 * d);
                    p.cubicTo(x + 6 * d, b + 3 * d, x + 6 * d, b - 2 * d, x + 9 * d, b);
                }
                c.drawPath(p, pen);
                break;
            }
            case 6: { // highlighter swipes
                pen = pen(Math.max(8f, h / d * 0.85f), YELLOW, 105, d);
                pen.setStrokeCap(Paint.Cap.SQUARE);
                p.moveTo(l - 2 * d, mid + 1 * d);
                p.lineTo(rr + 2 * d, mid - 1 * d);
                c.drawPath(p, pen);
                Paint thin = pen(Math.max(5f, h / d * 0.5f), YELLOW, 70, d);
                Path q = new Path();
                q.moveTo(l, mid - 2 * d);
                q.lineTo(rr, mid + 2 * d);
                c.drawPath(q, thin);
                break;
            }
            case 7: { // dense graphite fill: many short diagonals
                pen = pen(1.1f, GRAPHITE, 120, d);
                for (float x = l; x < rr; x += 2.6f * d) {
                    p.moveTo(x, b + rnd.nextFloat() * d);
                    p.lineTo(x + h * 0.35f, t - rnd.nextFloat() * d);
                }
                c.save();
                c.clipRect(l, t - 2 * d, rr, b + 2 * d);
                c.drawPath(p, pen);
                c.restore();
                break;
            }
            case 8: { // double underline, sketchy
                pen = pen(1.5f, GRAPHITE, 210, d);
                for (int i = 0; i < 2; i++) {
                    Path q = new Path();
                    float y0 = b + (2 + i * 3.5f) * d;
                    q.moveTo(l, y0);
                    for (float x = l; x <= rr; x += 8 * d) q.lineTo(x, y0 + (rnd.nextFloat() - 0.5f) * 1.6f * d);
                    c.drawPath(q, pen);
                }
                break;
            }
            default: { // a little crown of spikes above the text
                pen = pen(1.5f, RED, 230, d);
                p.moveTo(l, t - 1 * d);
                boolean up = true;
                for (float x = l; x < rr; x += 6 * d) {
                    p.lineTo(x + 3 * d, up ? t - 7 * d - rnd.nextFloat() * 2 * d : t - 1 * d);
                    up = !up;
                }
                c.drawPath(p, pen);
            }
        }
    }
}
