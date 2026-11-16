package app.privatetodo;

import android.graphics.Canvas;
import android.graphics.CornerPathEffect;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

import java.util.Random;

/**
 * The hand-drawn looks: thirty pencil circles and thirty scribbles, drawn from a rectangle and a seed (so a shape looks the
 * same every time it is redrawn). Also the squircle outline. Used by the note and by the Settings previews.
 */
final class PencilFx {
    private PencilFx() {
    }

    static final String[] CIRCLE_NAMES = {"Soft graphite", "Bold pencil", "Sketchy double", "Neat ring", "Loose swoop",
            "Red pen", "Blue ink", "Dotted pencil", "Triple loop", "Highlighter ring",
            "2H fine", "HB loose", "Scratchy x4", "B double", "Shaky wild", "6B soft thick", "Spiral twice", "Sepia egg",
            "Faint sketch", "2B bold", "Triple pass", "Long tail", "Nervous hand", "Charcoal", "Fine many",
            "Dashed lead", "Brown pencil", "Soft double", "Lopsided egg", "Rushed loop"};
    static final String[] SCRIBBLE_NAMES = {"Tight zigzag", "Loose zigzag", "Cross-hatch", "Wavy underline",
            "Strike-through", "Loops", "Highlighter", "Graphite fill", "Double underline", "Crown",
            "Squiggle line", "Scribble out", "Black-out", "Cross out X", "Stars above", "Sunburst", "Check mark",
            "Brackets", "Scallops", "Stitches", "Dots above", "Margin bar", "Hearts", "Exclamation", "Zigzag line",
            "Arrow underline", "Corner marks", "Asterisk", "Rough marker", "Lightning"};
    static final int COUNT = 30;

    private static final int GRAPHITE = 0xFF3A3A3A, RED = 0xFFD2452B, BLUE = 0xFF2E6FD9, GREEN = 0xFF2FA866,
            YELLOW = 0xFFFFD23F;

    // width dp, passes, wobble dp, overshoot (turns), alpha, dash (0 none), grow dp per pass
    private static final float[][] CIRCLE = {
            {1.5f, 2, 1.6f, 0.12f, 200, 0, 1.4f, 0, 0},
            {3.2f, 1, 1.2f, 0.08f, 230, 0, 0, 0, 0},
            {1.3f, 3, 2.6f, 0.15f, 190, 0, 2.2f, 0, 0},
            {1.8f, 1, 0.5f, 0.02f, 230, 0, 0, 0, 0},
            {1.8f, 1, 3.4f, 0.32f, 220, 0, 0, 0, 0},
            {2.0f, 2, 1.8f, 0.14f, 235, 0, 1.6f, 0, 0},
            {1.8f, 1, 1.4f, 0.1f, 235, 0, 0, 0, 0},
            {2.0f, 1, 1.2f, 0.06f, 235, 1, 0, 0, 0},
            {1.3f, 3, 1.6f, 0.45f, 200, 0, 1.8f, 0, 0},
            {9.0f, 1, 1.4f, 0.06f, 110, 0, 0, 0, 0},
            // 10..29: pencil only, drawn like a hand did it (tilted, lopsided, never closing, uneven pressure)
            {1.2f, 1, 2.0f, 0.18f, 170, 0, 0, 0.05f, 0.04f},
            {2.4f, 1, 2.8f, 0.22f, 210, 0, 0, -0.06f, 0.06f},
            {1.0f, 4, 2.2f, 0.20f, 150, 0, 1.6f, 0.04f, 0.03f},
            {2.8f, 2, 3.0f, 0.28f, 200, 0, 2.4f, 0.08f, -0.05f},
            {1.6f, 1, 4.2f, 0.40f, 200, 0, 0, -0.10f, 0.10f},
            {4.0f, 1, 2.0f, 0.10f, 190, 0, 0, 0, 0.03f},
            {1.4f, 2, 1.0f, 0.50f, 180, 0, 2.6f, 0.12f, 0},
            {2.0f, 1, 2.4f, 0.05f, 225, 0, 0, 0.07f, 0.08f},
            {1.1f, 2, 3.4f, 0.30f, 160, 0, 3.0f, -0.08f, 0.05f},
            {3.4f, 1, 1.6f, 0.16f, 215, 0, 0, 0.04f, -0.07f},
            {1.8f, 3, 2.8f, 0.10f, 185, 0, 1.8f, 0, 0.06f},
            {2.2f, 1, 1.8f, 0.36f, 235, 0, 0, -0.12f, 0.05f},
            {1.5f, 1, 5.0f, 0.20f, 190, 0, 0, 0.05f, 0},
            {5.0f, 1, 1.8f, 0.08f, 120, 0, 0, 0, 0.04f},
            {1.2f, 5, 1.8f, 0.10f, 130, 0, 1.2f, 0.03f, 0.02f},
            {2.6f, 1, 3.2f, 0.26f, 220, 1, 0, 0.06f, 0.04f},
            {1.7f, 2, 1.4f, 0.60f, 210, 0, 3.4f, -0.05f, 0.07f},
            {3.0f, 2, 2.2f, 0.18f, 170, 0, 1.2f, 0.09f, 0},
            {1.3f, 1, 2.6f, 0.14f, 230, 0, 0, -0.04f, 0.12f},
            {2.0f, 2, 3.8f, 0.34f, 200, 0, 2.0f, 0.10f, -0.08f}};

    private static final int[] CIRCLE_COL = {GRAPHITE, GRAPHITE, GRAPHITE, GRAPHITE, GRAPHITE, RED, BLUE, GRAPHITE, GREEN, YELLOW,
            0xFF555555, 0xFF2B2B2B, 0xFF444444, 0xFF1F1F1F, 0xFF3A3A3A, 0xFF202020, 0xFF4A4A4A, 0xFF5A4636, 0xFF777777,
            0xFF2E2E2E, 0xFF3B4558, 0xFF3A3A3A, 0xFF4A4A4A, 0xFF111111, 0xFF5C5C5C, 0xFF353535, 0xFF6A5040, 0xFF2A2A2A,
            0xFF3F3F3F, 0xFF4D4D4D};

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
        float[] s = CIRCLE[Math.max(0, Math.min(COUNT - 1, style))];
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
                double egg = 1 + s[8] * Math.cos(t - 0.7);
                double px = (a * drift * egg + wob) * Math.cos(t), py = (b * drift * egg + wob * 0.8) * Math.sin(t);
                double ct = Math.cos(s[7]), st = Math.sin(s[7]); // the whole ring leans a little, as a hand's does
                float x = (float) (cx + px * ct - py * st);
                float y = (float) (cy + px * st + py * ct);
                if (i == 0) p.moveTo(x, y);
                else p.lineTo(x, y);
            }
            Paint main = pen(s[0], CIRCLE_COL[Math.max(0, Math.min(COUNT - 1, style))], (int) s[4], d);
            if (s[5] > 0) main.setPathEffect(new DashPathEffect(new float[]{2 * d, 5 * d}, 0));
            c.drawPath(p, main);
            if (s[0] < 4 && s[5] == 0) { // the pencil's grain: a thinner, fainter second pass beside it
                Paint grain = pen(s[0] * 0.5f, CIRCLE_COL[Math.max(0, Math.min(COUNT - 1, style))], (int) (s[4] * 0.5f), d);
                c.save();
                c.translate(0.6f * d, 0.5f * d);
                c.drawPath(p, grain);
                c.restore();
            }
        }
    }

    /** A scribble over (or under) the text in the rectangle. */
    static void scribble(Canvas c, RectF r, int style, long seed, float d) {
        if (style >= 10) {
            scribble2(c, r, Math.min(style, COUNT - 1), seed, d);
            return;
        }
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

    private static void star(Path p, float cx, float cy, float rad) {
        for (int i = 0; i < 10; i++) {
            double a = -Math.PI / 2 + i * Math.PI / 5;
            float rr = i % 2 == 0 ? rad : rad * 0.42f;
            float x = (float) (cx + rr * Math.cos(a)), y = (float) (cy + rr * Math.sin(a));
            if (i == 0) p.moveTo(x, y);
            else p.lineTo(x, y);
        }
        p.close();
    }

    /** The extra scribbles, 10 to 29: the marks people actually make on notes (underlines, cross-outs, stars, brackets...). */
    private static void scribble2(Canvas c, RectF r, int style, long seed, float d) {
        Random rnd = new Random(seed * 1299709L + style);
        float l = r.left - 2 * d, rr = r.right + 2 * d, t = r.top + 2 * d, b = r.bottom - 1 * d;
        float h = b - t, mid = (t + b) / 2f, w = rr - l;
        Path p = new Path();
        Paint pen;
        switch (style) {
            case 10: { // squiggle underline
                pen = pen(1.8f, GRAPHITE, 225, d);
                p.moveTo(l, b + 3 * d);
                for (float x = l; x <= rr; x += 2.5f * d) p.lineTo(x, b + 3 * d + (float) Math.sin((x - l) / (2.2 * d)) * 2.2f * d);
                c.drawPath(p, pen);
                break;
            }
            case 11: { // scribbled out: fast back and forth
                pen = pen(1.4f, GRAPHITE, 210, d);
                p.moveTo(l - 2 * d, mid);
                boolean down = true;
                for (float x = l; x < rr + 2 * d; x += 5 * d + rnd.nextFloat() * 3 * d) {
                    p.lineTo(x, down ? b - rnd.nextFloat() * 3 * d : t + rnd.nextFloat() * 3 * d);
                    down = !down;
                }
                c.drawPath(p, pen);
                break;
            }
            case 12: { // black-out: so many passes that the words are gone
                pen = pen(2.6f, 0xFF222222, 235, d);
                for (int pass = 0; pass < 3; pass++) {
                    Path q = new Path();
                    q.moveTo(l - 3 * d, t + pass * h / 3f);
                    boolean right = true;
                    for (int k = 0; k < 4; k++) {
                        q.lineTo(right ? rr + 3 * d : l - 3 * d, t + (pass + (k + 1) / 5f) * h / 3f + rnd.nextFloat() * d);
                        right = !right;
                    }
                    c.drawPath(q, pen);
                }
                break;
            }
            case 13: { // a hand-drawn X through it
                pen = pen(2.0f, GRAPHITE, 230, d);
                p.moveTo(l, t - 1 * d);
                p.lineTo(rr, b + 1 * d);
                p.moveTo(rr, t + rnd.nextFloat() * 2 * d);
                p.lineTo(l + rnd.nextFloat() * 3 * d, b);
                c.drawPath(p, pen);
                break;
            }
            case 14: { // little stars over the text
                pen = pen(1.3f, RED, 235, d);
                int n = Math.max(1, (int) (w / (26 * d)));
                for (int i = 0; i < n; i++) {
                    Path q = new Path();
                    star(q, l + (i + 0.5f) * w / n, t - 6 * d - rnd.nextFloat() * 2 * d, (3.6f + rnd.nextFloat()) * d);
                    c.drawPath(q, pen);
                }
                break;
            }
            case 15: { // sunburst rays over the text
                pen = pen(1.6f, 0xFFE0A100, 235, d);
                float cx = (l + rr) / 2f;
                for (int i = -3; i <= 3; i++) {
                    double ang = -Math.PI / 2 + i * 0.36;
                    float r0 = 5 * d + h / 2f, r1 = r0 + (5 + rnd.nextFloat() * 3) * d;
                    p.moveTo((float) (cx + r0 * Math.cos(ang) * 1.6), (float) (mid + r0 * Math.sin(ang)));
                    p.lineTo((float) (cx + r1 * Math.cos(ang) * 1.6), (float) (mid + r1 * Math.sin(ang)));
                }
                c.drawPath(p, pen);
                break;
            }
            case 16: { // a tick after the text
                pen = pen(2.6f, 0xFF2FA866, 240, d);
                p.moveTo(rr + 4 * d, mid);
                p.lineTo(rr + 9 * d, b + d);
                p.lineTo(rr + 19 * d, t - 5 * d);
                c.drawPath(p, pen);
                break;
            }
            case 17: { // hand brackets either side
                pen = pen(1.8f, GRAPHITE, 230, d);
                p.moveTo(l + 1 * d, t - 2 * d);
                p.cubicTo(l - 5 * d, t, l - 5 * d, b, l + 1 * d, b + 2 * d);
                p.moveTo(rr - 1 * d, t - 2 * d);
                p.cubicTo(rr + 5 * d, t, rr + 5 * d, b, rr - 1 * d, b + 2 * d);
                c.drawPath(p, pen);
                break;
            }
            case 18: { // scalloped underline
                pen = pen(1.6f, BLUE, 225, d);
                p.moveTo(l, b + 2 * d);
                for (float x = l; x < rr; x += 8 * d) p.cubicTo(x + 1 * d, b + 7 * d, x + 7 * d, b + 7 * d, x + 8 * d, b + 2 * d);
                c.drawPath(p, pen);
                break;
            }
            case 19: { // stitched underline
                pen = pen(1.8f, RED, 230, d);
                for (float x = l; x < rr; x += 7 * d) {
                    p.moveTo(x, b + 3 * d + rnd.nextFloat() * d);
                    p.lineTo(Math.min(rr, x + 4 * d), b + 3 * d + rnd.nextFloat() * d);
                }
                c.drawPath(p, pen);
                break;
            }
            case 20: { // dots above for emphasis
                pen = pen(3.0f, GRAPHITE, 235, d);
                int n = Math.max(1, (int) (w / (14 * d)));
                for (int i = 0; i < n; i++) {
                    float x = l + (i + 0.5f) * w / n;
                    p.moveTo(x, t - 4 * d);
                    p.lineTo(x + 0.1f, t - 4 * d);
                }
                c.drawPath(p, pen);
                break;
            }
            case 21: { // a bar in the margin with ticks
                pen = pen(2.2f, 0xFFE0A100, 235, d);
                float x0 = l - 8 * d;
                p.moveTo(x0, t - 2 * d);
                p.lineTo(x0 + 0.5f * d, b + 2 * d);
                p.moveTo(x0 - 3 * d, t + 1 * d);
                p.lineTo(x0 + 3 * d, t + 1 * d);
                p.moveTo(x0 - 3 * d, b - 1 * d);
                p.lineTo(x0 + 3 * d, b - 1 * d);
                c.drawPath(p, pen);
                break;
            }
            case 22: { // hearts at the ends
                pen = pen(1.6f, RED, 235, d);
                for (int side = 0; side < 2; side++) {
                    float cx = side == 0 ? l - 8 * d : rr + 8 * d, cy = mid - 2 * d, k = 4.5f * d;
                    Path q = new Path();
                    q.moveTo(cx, cy + k);
                    q.cubicTo(cx - 2 * k, cy - 0.2f * k, cx - 0.6f * k, cy - 1.6f * k, cx, cy - 0.4f * k);
                    q.cubicTo(cx + 0.6f * k, cy - 1.6f * k, cx + 2 * k, cy - 0.2f * k, cx, cy + k);
                    c.drawPath(q, pen);
                }
                break;
            }
            case 23: { // !! after the text
                pen = pen(2.4f, RED, 240, d);
                for (int i = 0; i < 2; i++) {
                    float x = rr + (6 + i * 6) * d;
                    p.moveTo(x, t - 3 * d);
                    p.lineTo(x + 0.4f * d, b - 5 * d);
                    p.moveTo(x + 0.4f * d, b);
                    p.lineTo(x + 0.5f * d, b);
                }
                c.drawPath(p, pen);
                break;
            }
            case 24: { // zigzag underline
                pen = pen(1.7f, GRAPHITE, 225, d);
                p.moveTo(l, b + 2 * d);
                boolean up = false;
                for (float x = l; x < rr; x += 4 * d) {
                    p.lineTo(x + 2 * d, b + (up ? 1 : 5) * d + rnd.nextFloat() * d);
                    up = !up;
                }
                c.drawPath(p, pen);
                break;
            }
            case 25: { // underline that ends in an arrow
                pen = pen(1.9f, BLUE, 235, d);
                p.moveTo(l, b + 3 * d);
                for (float x = l; x < rr; x += 8 * d) p.lineTo(x, b + 3 * d + (rnd.nextFloat() - 0.5f) * 1.4f * d);
                p.lineTo(rr + 6 * d, b + 3 * d);
                p.moveTo(rr, b - 1 * d);
                p.lineTo(rr + 6 * d, b + 3 * d);
                p.lineTo(rr, b + 7 * d);
                c.drawPath(p, pen);
                break;
            }
            case 26: { // four corner marks
                pen = pen(1.8f, GRAPHITE, 230, d);
                float k = 5 * d;
                float[][] cn = {{l - 3 * d, t - 3 * d, 1, 1}, {rr + 3 * d, t - 3 * d, -1, 1},
                        {l - 3 * d, b + 3 * d, 1, -1}, {rr + 3 * d, b + 3 * d, -1, -1}};
                for (float[] q : cn) {
                    p.moveTo(q[0] + q[2] * k, q[1]);
                    p.lineTo(q[0], q[1]);
                    p.lineTo(q[0], q[1] + q[3] * k);
                }
                c.drawPath(p, pen);
                break;
            }
            case 27: { // an asterisk in front
                pen = pen(1.8f, RED, 240, d);
                float cx = l - 8 * d, cy = t - 1 * d, k = 4.5f * d;
                for (int i = 0; i < 3; i++) {
                    double a = i * Math.PI / 3 + 0.2;
                    p.moveTo((float) (cx - k * Math.cos(a)), (float) (cy - k * Math.sin(a)));
                    p.lineTo((float) (cx + k * Math.cos(a)), (float) (cy + k * Math.sin(a)));
                }
                c.drawPath(p, pen);
                break;
            }
            case 28: { // a rough pencil marker, slanted and streaky
                pen = pen(Math.max(7f, h / d * 0.8f), 0xFF8A8A8A, 90, d);
                pen.setStrokeCap(Paint.Cap.SQUARE);
                for (int i = 0; i < 3; i++) {
                    Path q = new Path();
                    q.moveTo(l - 2 * d, mid + (1 - i) * 2.2f * d + rnd.nextFloat() * d);
                    q.lineTo(rr + 2 * d, mid + (1 - i) * 2.2f * d - 2 * d + rnd.nextFloat() * d);
                    c.drawPath(q, pen);
                }
                break;
            }
            default: { // lightning underline
                pen = pen(1.8f, 0xFFE0A100, 240, d);
                p.moveTo(l, b + 1 * d);
                boolean up = false;
                for (float x = l; x < rr; x += 6 * d) {
                    p.lineTo(x + 3 * d, up ? b + 1 * d : b + 7 * d);
                    up = !up;
                }
                c.drawPath(p, pen);
            }
        }
    }

    // ------------------------------------------------------------ freestyle pencils (drawing on a note)

    static final String[] PENCIL_NAMES = {"9H", "6H", "4H", "2H", "H", "F", "HB", "B", "2B", "3B", "4B", "6B", "8B",
            "Charcoal", "Red", "Blue", "Green", "Orange", "Purple", "Sepia"};
    static final String[] PENCIL_NOTES = {"hardest, faintest", "very hard", "hard", "firm, light", "crisp", "fine, between H and HB",
            "the everyday pencil", "soft", "soft and dark", "darker", "dark", "very dark", "softest, blackest",
            "smudgy and broad", "coloured pencil", "coloured pencil", "coloured pencil", "coloured pencil",
            "coloured pencil", "brown drawing pencil"};
    // width dp, alpha, grain (0 smooth .. 1 rough)
    private static final float[][] PENCIL = {
            {0.9f, 140, 0.2f}, {1.0f, 150, 0.2f}, {1.1f, 160, 0.25f}, {1.2f, 175, 0.25f}, {1.3f, 190, 0.3f},
            {1.5f, 200, 0.3f}, {1.7f, 215, 0.35f}, {1.9f, 225, 0.4f}, {2.2f, 235, 0.45f}, {2.5f, 240, 0.5f},
            {2.9f, 245, 0.55f}, {3.4f, 250, 0.6f}, {4.0f, 250, 0.65f}, {5.0f, 190, 1f},
            {2.0f, 225, 0.5f}, {2.0f, 225, 0.5f}, {2.0f, 225, 0.5f}, {2.0f, 225, 0.5f}, {2.0f, 225, 0.5f}, {2.1f, 225, 0.55f}};
    private static final int[] PENCIL_COL = {0xFF8C8C8C, 0xFF7A7A7A, 0xFF6A6A6A, 0xFF5A5A5A, 0xFF4C4C4C, 0xFF404040,
            0xFF363636, 0xFF2C2C2C, 0xFF242424, 0xFF1C1C1C, 0xFF161616, 0xFF101010, 0xFF080808, 0xFF1A1A1A,
            0xFFC8402C, 0xFF2E6FD9, 0xFF2F9A5E, 0xFFE8821E, 0xFF7B4FC4, 0xFF6B4A32};
    static final int PENCILS = 20;

    /** One freehand stroke in pencil. xy = x0,y0,x1,y1... in pixels; drawn as a soft body, a darker core and a grainy edge. */
    static void ink(Canvas c, float[] xy, int pencil, float d, long seed) {
        if (xy == null || xy.length < 4) return;
        int pi = Math.max(0, Math.min(PENCILS - 1, pencil));
        float[] s = PENCIL[pi];
        int col = PENCIL_COL[pi];
        Path p = new Path();
        p.moveTo(xy[0], xy[1]);
        int n = xy.length / 2;
        for (int i = 1; i < n - 1; i++) {
            float mx = (xy[i * 2] + xy[i * 2 + 2]) / 2f, my = (xy[i * 2 + 1] + xy[i * 2 + 3]) / 2f;
            p.quadTo(xy[i * 2], xy[i * 2 + 1], mx, my);
        }
        p.lineTo(xy[n * 2 - 2], xy[n * 2 - 1]);
        Paint pen = new Paint(Paint.ANTI_ALIAS_FLAG);
        pen.setStyle(Paint.Style.STROKE);
        pen.setStrokeCap(Paint.Cap.ROUND);
        pen.setStrokeJoin(Paint.Join.ROUND);
        if (pi == 13) { // charcoal: a broad smudge under the line
            pen.setStrokeWidth(s[0] * 1.8f * d);
            pen.setColor((col & 0x00FFFFFF) | (50 << 24));
            c.drawPath(p, pen);
        }
        pen.setStrokeWidth(s[0] * d);
        pen.setColor((col & 0x00FFFFFF) | (((int) (s[1] * 0.62f)) << 24));
        c.drawPath(p, pen); // the body
        pen.setStrokeWidth(s[0] * 0.5f * d);
        pen.setColor((col & 0x00FFFFFF) | (((int) s[1]) << 24));
        c.drawPath(p, pen); // the darker core where the lead pressed hardest
        Random rnd = new Random(seed * 31L + pi);
        float jx = (0.4f + rnd.nextFloat() * 0.5f) * d * s[2], jy = (0.3f + rnd.nextFloat() * 0.5f) * d * s[2];
        pen.setStrokeWidth(Math.max(0.5f, s[0] * 0.4f) * d);
        pen.setColor((col & 0x00FFFFFF) | (((int) (s[1] * 0.4f * (0.5f + s[2] * 0.5f))) << 24));
        pen.setPathEffect(new DashPathEffect(new float[]{(3 + 5 * s[2]) * d, (1.2f + 2 * s[2]) * d}, rnd.nextFloat() * 6 * d));
        c.save();
        c.translate(jx, jy);
        c.drawPath(p, pen); // the paper's grain breaking the edge
        c.restore();
    }
}
