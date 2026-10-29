package app.privatetodo;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;

import java.util.Random;

/**
 * Design lab only: a test task you slide to the left, with one of ten delete animations.
 * Nothing is deleted. A moment after the animation the task comes back so you can try again.
 */
final class LabSwipe extends View {
    static final String[] NAMES = {
            "Trash lid", "Shred", "Into the bin", "Poof", "Elastic",
            "Circle wipe", "Dust", "Paper drop", "Strike out", "Motion trail",
            "Black hole", "Explode", "Fire", "Glitch", "Shatter", "Zip", "Rocket", "Slice", "Flip away", "Genie",
            "Melt", "Bubbles", "Lightning", "Confetti", "Stamp", "Eraser", "Spin out", "Dominoes", "Pixelate",
            "Portal"};
    static final String[] NOTES = {
            "Red slab, the bin lid opens as you pull; the row zips off and the gap closes.",
            "On release the task is cut into strips that fall away.",
            "The task shrinks and spins into the bin, which gives a little bounce.",
            "The task puffs away in a cloud of smoke.",
            "The task stretches like rubber, snaps back, then shoots off.",
            "A red circle grows from the bin and swallows the task.",
            "The task crumbles into pixels that blow away.",
            "The task tips over like a card and falls off the screen.",
            "The colour warms from amber to red, the title is struck out, then it slides away.",
            "The task streaks off with a ghost trail behind it.",
            "The task spirals into a black hole, stars get pulled in with it.",
            "Boom: the task blows apart into chunks with a flash.",
            "The task burns away from the edge, leaving embers and ash.",
            "Screen glitch: slices tear sideways, colours split, then static.",
            "The task cracks like glass and the shards fall.",
            "A zipper closes the task into a line that shoots away.",
            "The task shakes, then blasts off upward with a flame.",
            "A sword slash cuts it in two and the halves fall apart.",
            "The task flips over like a page and is gone.",
            "The task is sucked into the bin like the Mac genie effect.",
            "The task melts and drips into a puddle.",
            "The task turns into soap bubbles that float up and pop.",
            "Lightning strikes the task, it flashes white and vanishes.",
            "The task bursts into confetti.",
            "A big red DELETED stamp slams on, then the task slides away.",
            "An eraser wipes the task away and leaves crumbs.",
            "The task spins away like a thrown disc.",
            "The task is cut into blocks that topple like dominoes.",
            "The task turns into big pixels, then dissolves.",
            "The task is swallowed by a glowing portal."};
    private static final int[] DURATION = {800, 1000, 900, 900, 900, 900, 1100, 900, 1000, 900,
            1200, 1100, 1300, 1100, 1200, 1000, 1200, 1100, 1000, 1000, 1200, 1300, 1200, 1100, 1300, 1100, 1100,
            1200, 1200, 1200};
    private static final int[] BG = {0xFF12082B, 0xFFB71C1C, 0xFF4E1B0B, 0xFF0B1B2B, 0xFF37474F, 0xFF455A64,
            0xFF0D1B4C, 0xFF212121, 0xFF6A1B9A, 0xFF004D40, 0xFF5D4037, 0xFF0277BD, 0xFF1A1A2E, 0xFFAD1457,
            0xFFD64545, 0xFFE57373, 0xFF283593, 0xFF37474F, 0xFF263238, 0xFF311B92};

    private static final int RED = 0xFFD64545;
    private static final int DEEP = 0xFFB3261E;
    private static final int AMBER = 0xFFF5A623;
    private static final int SLATE = 0xFF546E7A;
    private static final int ORANGE = 0xFFEF6C00;
    private static final int PURPLE = 0xFF5E35B1;

    private static final int IDLE = 0;
    private static final int COMMIT = 1;
    private static final int GONE = 2;
    private static final int RESTORE = 3;

    private final int style;
    final float d;
    private final int slop;
    final float rh; // row height
    private final float pad;
    final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bmpPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    final RectF rf = new RectF();
    private final Rect src = new Rect();
    private final Path path = new Path();
    private final Typeface regular;
    private final Typeface bold;
    final float[] rnd = new float[96];

    private int phase = IDLE;
    private float dx; // drag, 0 or negative
    float cdx; // where the drag was when the animation started
    private float t; // commit progress 0..1
    private float rt; // restore progress 0..1
    private float downX;
    private float downY;
    private boolean swiping;
    private boolean armed;
    Bitmap snap;
    Bitmap[] mos;
    private ValueAnimator anim;
    private Runnable pending;
    float rw;
    private int deleted;
    private Runnable onDeleted;

    LabSwipe(Context c, int style) {
        super(c);
        this.style = style;
        d = c.getResources().getDisplayMetrics().density;
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
        rh = 68 * d;
        pad = 12 * d;
        Fonts.init(c);
        regular = Fonts.face(false);
        bold = Fonts.face(true);
        Random r = new Random(11 + style);
        for (int i = 0; i < rnd.length; i++) rnd[i] = r.nextFloat();
    }

    void setOnDeleted(Runnable r) {
        onDeleted = r;
    }

    int count() {
        return deleted;
    }

    @Override
    protected void onMeasure(int w, int h) {
        setMeasuredDimension(resolveSize(Math.round(300 * d), w), Math.round(rh + 36 * d));
    }

    private float limit() {
        return Math.max(96 * d, rw * 0.3f);
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static float ease(float v) {
        v = clamp01(v);
        return v * v * (3 - 2 * v);
    }

    private static float lerp(float a, float b, float v) {
        return a + (b - a) * v;
    }

    private static int blend(int a, int b, float v) {
        int r = Math.round(((a >> 16) & 255) * (1 - v) + ((b >> 16) & 255) * v);
        int g = Math.round(((a >> 8) & 255) * (1 - v) + ((b >> 8) & 255) * v);
        int bl = Math.round((a & 255) * (1 - v) + (b & 255) * v);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    // ------------------------------------------------------------------ the test task row

    void drawRow(Canvas c, int alpha, float strike) {
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
        // emoji badge
        p.setColor((Pal.accent & 0x00FFFFFF) | 0x24000000);
        p.setAlpha((int) (alpha * 0.14f));
        c.drawCircle(35 * d, rh / 2f, 21 * d, p);
        p.setColor(Pal.ink);
        p.setAlpha(alpha);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(regular);
        p.setTextSize(20 * d);
        c.drawText("🛒", 35 * d, rh / 2f - (p.ascent() + p.descent()) / 2f, p);
        // title
        p.setTextAlign(Paint.Align.LEFT);
        p.setTypeface(bold);
        p.setTextSize(16 * d);
        p.setColor(Pal.ink);
        p.setAlpha(alpha);
        float ty = rh * 0.40f;
        c.drawText("Buy groceries", 68 * d, ty, p);
        float tw = p.measureText("Buy groceries");
        if (strike > 0) {
            p.setColor(Pal.overdue);
            p.setAlpha(alpha);
            p.setStrokeWidth(2.4f * d);
            p.setStyle(Paint.Style.STROKE);
            c.drawLine(66 * d, ty - 5 * d, 66 * d + (tw + 6 * d) * strike, ty - 5 * d, p);
            p.setStyle(Paint.Style.FILL);
        }
        // "when" tile
        p.setTypeface(bold);
        p.setTextSize(12 * d);
        float sw = p.measureText("Today, 6:00 PM") + 18 * d;
        p.setColor(Pal.accent);
        p.setAlpha(alpha);
        rf.set(68 * d, rh * 0.52f, 68 * d + sw, rh * 0.52f + 22 * d);
        c.drawRoundRect(rf, 8 * d, 8 * d, p);
        p.setColor(Pal.onAccent);
        p.setAlpha(alpha);
        p.setTextAlign(Paint.Align.LEFT);
        c.drawText("Today, 6:00 PM", 68 * d + 9 * d, rh * 0.52f + 15.5f * d, p);
        // priority stripe
        p.setColor(0xFFE67E22);
        p.setAlpha(alpha);
        rf.set(rw - 16 * d, rh / 2f - 16 * d, rw - 12 * d, rh / 2f + 16 * d);
        c.drawRoundRect(rf, 2 * d, 2 * d, p);
        p.setAlpha(255);
    }

    void drawTrash(Canvas c, float cx, float cy, float s, float lid, int color) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setStrokeWidth(2.2f * d);
        p.setColor(color);
        path.reset();
        path.moveTo(cx - 0.30f * s, cy - 0.14f * s);
        path.lineTo(cx - 0.24f * s, cy + 0.42f * s);
        path.lineTo(cx + 0.24f * s, cy + 0.42f * s);
        path.lineTo(cx + 0.30f * s, cy - 0.14f * s);
        c.drawPath(path, p);
        c.drawLine(cx - 0.09f * s, cy, cx - 0.08f * s, cy + 0.30f * s, p);
        c.drawLine(cx + 0.09f * s, cy, cx + 0.08f * s, cy + 0.30f * s, p);
        c.save();
        c.rotate(-lid * 42f, cx - 0.40f * s, cy - 0.24f * s);
        c.drawLine(cx - 0.40f * s, cy - 0.24f * s, cx + 0.40f * s, cy - 0.24f * s, p);
        c.drawLine(cx - 0.12f * s, cy - 0.24f * s, cx - 0.12f * s, cy - 0.38f * s, p);
        c.drawLine(cx + 0.12f * s, cy - 0.24f * s, cx + 0.12f * s, cy - 0.38f * s, p);
        c.drawLine(cx - 0.12f * s, cy - 0.38f * s, cx + 0.12f * s, cy - 0.38f * s, p);
        c.restore();
        p.setStyle(Paint.Style.FILL);
    }

    private void emoji(Canvas c, String s, float cx, float cy, float sp) {
        p.setTypeface(regular);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(sp * d);
        p.setColor(0xFFFFFFFF);
        p.setAlpha(255);
        c.drawText(s, cx, cy - (p.ascent() + p.descent()) / 2f, p);
    }

    // ------------------------------------------------------------------ the area revealed behind the row

    /** p: how far the row is pulled (0..1). lid / isc: bin lid opening and icon scale. */
    void drawReveal(Canvas c, float pr, float lid, float isc) {
        float ix = rw - 38 * d;
        float iy = rh / 2f;
        int bg;
        switch (style) {
            case 1:
                bg = DEEP;
                break;
            case 2:
                bg = 0xFF263238;
                break;
            case 3:
                bg = SLATE;
                break;
            case 4:
            case 5:
                bg = blend(Pal.bg, RED, 0.16f);
                break;
            case 6:
                bg = PURPLE;
                break;
            case 7:
                bg = ORANGE;
                break;
            case 8:
                bg = blend(AMBER, RED, ease(pr));
                break;
            default:
                bg = style >= 10 ? BG[style - 10] : RED;
                break;
        }
        p.setColor(bg);
        p.setStyle(Paint.Style.FILL);
        p.setAlpha(255);
        rf.set(0, 0, rw, rh);
        c.drawRoundRect(rf, 20 * d, 20 * d, p);

        if (style == 4) { // a red disc grows from the right edge
            c.save();
            c.clipRect(0, 0, rw, rh);
            p.setColor(RED);
            c.drawCircle(rw, iy, Math.min(1f, pr) * rw * 1.15f, p);
            c.restore();
        }
        if (style == 5) { // red disc behind the icon
            p.setColor(RED);
            c.drawCircle(ix, iy, (16 + 10 * Math.min(1f, pr)) * d * isc, p);
        }
        switch (style) {
            case 1:
                emoji(c, "✂️", ix, iy, 24 * isc);
                break;
            case 3:
                emoji(c, "💨", ix, iy, 26 * isc);
                break;
            case 6:
                emoji(c, "✨", ix, iy, 24 * isc);
                break;
            case 8: {
                drawTrash(c, ix, iy, 30 * d * (0.7f + 0.5f * Math.min(1f, pr)) * isc, 0, 0xFFFFFFFF);
                p.setTypeface(bold);
                p.setTextAlign(Paint.Align.RIGHT);
                p.setTextSize(12.5f * d);
                p.setColor(0xFFFFFFFF);
                p.setAlpha(255);
                String msg = t > 0 ? "Deleted" : pr >= 1f ? "Release to delete" : "Slide to delete";
                c.drawText(msg, ix - 26 * d, iy - (p.ascent() + p.descent()) / 2f, p);
                break;
            }
            default:
                drawTrash(c, ix, iy, 30 * d * isc, lid, 0xFFFFFFFF);
                break;
        }
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void onDraw(Canvas c) {
        rw = getWidth() - 2 * pad;
        c.save();
        c.translate(pad, (getHeight() - rh) / 2f);
        if (phase == IDLE) {
            float pr = clamp01(-dx / limit());
            drawReveal(c, pr, pr, 1f);
            c.save();
            c.translate(dx, 0);
            if (style == 4) { // stretch like rubber, pinned at the right edge
                c.translate(rw, 0);
                c.scale(1f + 0.14f * pr, 1f - 0.04f * pr);
                c.translate(-rw, 0);
            } else if (style == 2) {
                c.translate(rw / 2f, rh / 2f);
                c.scale(1f - 0.06f * pr, 1f - 0.06f * pr);
                c.translate(-rw / 2f, -rh / 2f);
            }
            drawRow(c, 255, 0);
            c.restore();
        } else if (phase == COMMIT) {
            drawCommit(c);
        } else if (phase == GONE) {
            p.setTypeface(regular);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(13 * d);
            p.setColor(Pal.muted);
            p.setAlpha(255);
            c.drawText("Gone. (Nothing was really deleted.) It comes back in a moment.", rw / 2f,
                    rh / 2f - (p.ascent() + p.descent()) / 2f, p);
        } else {
            float v = ease(rt);
            c.translate(0, (1 - v) * 10 * d);
            c.translate(rw / 2f, rh / 2f);
            c.scale(0.94f + 0.06f * v, 0.94f + 0.06f * v);
            c.translate(-rw / 2f, -rh / 2f);
            drawRow(c, (int) (255 * v), 0);
        }
        c.restore();
    }

    void collapse(Canvas c, float hs) {
        c.translate(0, rh / 2f);
        c.scale(1f, Math.max(0.001f, hs));
        c.translate(0, -rh / 2f);
    }

    private void drawCommit(Canvas c) {
        c.save();
        switch (style) {
            case 0: { // trash lid
                float u = clamp01(t / 0.5f);
                float x = cdx + (-rw - 30 * d - cdx) * u * u;
                collapse(c, 1f - ease((t - 0.5f) / 0.5f));
                drawReveal(c, 1f, 1f, 1f);
                c.translate(x, 0);
                drawRow(c, 255, 0);
                break;
            }
            case 1: { // shred
                collapse(c, 1f - ease((t - 0.72f) / 0.28f));
                drawReveal(c, 1f, 1f, 1f);
                int n = 16;
                float sw = rw / n;
                for (int i = 0; i < n; i++) {
                    float delay = i / (float) n * 0.32f + rnd[i] * 0.1f;
                    float lt = clamp01((t - delay) / 0.55f);
                    float fall = lt * lt * 240 * d;
                    float rot = (rnd[i + 20] - 0.5f) * 50f * lt;
                    int al = (int) (255 * (1f - lt * lt));
                    if (snap == null || al <= 0) continue;
                    c.save();
                    c.translate(cdx + i * sw + sw / 2f, fall + rh / 2f);
                    c.rotate(rot);
                    src.set(Math.round(i * sw), 0, Math.round((i + 1) * sw), snap.getHeight());
                    rf.set(-sw / 2f, -rh / 2f, sw / 2f, rh / 2f);
                    bmpPaint.setAlpha(al);
                    c.drawBitmap(snap, src, rf, bmpPaint);
                    c.restore();
                }
                break;
            }
            case 2: { // into the bin
                float u = ease(clamp01(t / 0.6f));
                float ix = rw - 38 * d;
                float sx = cdx + rw / 2f;
                float cx = lerp(sx, ix, u);
                float cy = lerp(rh / 2f, rh / 2f, u);
                float sc = lerp(1f, 0.06f, (float) Math.pow(u, 1.2));
                float pulse = 1f + 0.3f * (float) Math.sin(Math.PI * clamp01((t - 0.55f) / 0.25f));
                float lid = 1f - clamp01((t - 0.7f) / 0.15f);
                collapse(c, 1f - ease((t - 0.86f) / 0.14f));
                drawReveal(c, 1f, lid, pulse);
                int al = (int) (255 * (1f - clamp01((u - 0.75f) / 0.25f)));
                if (al > 0) {
                    c.translate(cx, cy);
                    c.rotate(-28f * u);
                    c.scale(sc, sc);
                    c.translate(-rw / 2f, -rh / 2f);
                    drawRow(c, al, 0);
                }
                break;
            }
            case 3: { // poof
                collapse(c, 1f - ease((t - 0.62f) / 0.38f));
                float u = clamp01(t / 0.38f);
                drawReveal(c, 1f, 1f, 1f);
                if (u < 1f) {
                    c.save();
                    c.translate(cdx + rw / 2f, rh / 2f);
                    float s = 1f - 0.14f * u;
                    c.scale(s, s);
                    c.translate(-rw / 2f, -rh / 2f);
                    drawRow(c, (int) (255 * (1f - u)), 0);
                    c.restore();
                }
                float lt = clamp01((t - 0.04f) / 0.72f);
                p.setStyle(Paint.Style.FILL);
                for (int i = 0; i < 26; i++) {
                    float ox = cdx + rnd[i] * rw;
                    float oy = rnd[i + 26] * rh;
                    float vx = (rnd[i + 52] - 0.5f) * 90 * d;
                    float vy = -(10 + rnd[i + 70] * 60) * d;
                    float rad = (7 + rnd[i + 10] * 12) * d * (0.5f + lt);
                    p.setColor(i % 3 == 0 ? 0xFFFFFFFF : 0xFFCFD8DC);
                    p.setAlpha((int) (200 * (1f - lt) * Math.min(1f, lt * 8f)));
                    c.drawCircle(ox + vx * lt, oy + vy * lt, rad, p);
                }
                p.setAlpha(255);
                break;
            }
            case 4: { // elastic
                float x;
                float stretch;
                if (t < 0.18f) {
                    float a = (float) Math.sin(Math.PI / 2 * (t / 0.18f));
                    x = cdx + 24 * d * a;
                    stretch = 1f + 0.14f - 0.08f * a;
                } else {
                    float u = clamp01((t - 0.18f) / 0.44f);
                    x = cdx + 24 * d + (-rw - 60 * d - cdx - 24 * d) * (float) Math.pow(u, 2.2);
                    stretch = 1.06f + 0.4f * u;
                }
                collapse(c, 1f - ease((t - 0.64f) / 0.36f));
                drawReveal(c, 1f, 1f, 1f);
                c.translate(x + rw, 0);
                c.scale(stretch, 1f - 0.05f * (stretch - 1f));
                c.translate(-rw, 0);
                drawRow(c, 255, 0);
                break;
            }
            case 5: { // circle wipe
                collapse(c, 1f - ease((t - 0.66f) / 0.34f));
                drawReveal(c, 1f, 1f, 1f);
                c.save();
                c.translate(cdx, 0);
                drawRow(c, 255, 0);
                c.restore();
                float ix = rw - 38 * d;
                float maxR = (float) Math.hypot(rw, rh);
                float rad = lerp(26 * d, maxR, ease(t / 0.5f));
                path.reset();
                rf.set(0, 0, rw, rh);
                path.addRoundRect(rf, 20 * d, 20 * d, Path.Direction.CW);
                c.save();
                c.clipPath(path);
                p.setColor(RED);
                p.setAlpha(255);
                c.drawCircle(ix, rh / 2f, rad, p);
                c.restore();
                drawTrash(c, ix, rh / 2f, 30 * d, 1f, 0xFFFFFFFF);
                float ta = clamp01((t - 0.25f) / 0.2f);
                if (ta > 0) {
                    p.setTypeface(bold);
                    p.setTextAlign(Paint.Align.LEFT);
                    p.setTextSize(16 * d);
                    p.setColor(0xFFFFFFFF);
                    p.setAlpha((int) (255 * ta));
                    c.drawText("Deleted", 24 * d, rh / 2f - (p.ascent() + p.descent()) / 2f, p);
                    p.setAlpha(255);
                }
                break;
            }
            case 6: { // dust
                collapse(c, 1f - ease((t - 0.8f) / 0.2f));
                drawReveal(c, 1f, 1f, 1f);
                int cols = 14;
                int rows = 5;
                float bw = rw / cols;
                float bh = rh / rows;
                for (int cI = 0; cI < cols; cI++) {
                    for (int rI = 0; rI < rows; rI++) {
                        int id = (cI * rows + rI) % 70;
                        float delay = cI / (float) cols * 0.32f + rnd[id] * 0.14f;
                        float lt = clamp01((t - delay) / 0.5f);
                        if (snap == null || lt >= 1f) continue;
                        float ox = cdx + cI * bw + bw / 2f - lt * (30 + 80 * rnd[id + 5]) * d;
                        float oy = rI * bh + bh / 2f - lt * (8 + 50 * rnd[id + 9]) * d;
                        float s = 1f - 0.55f * lt;
                        src.set(Math.round(cI * bw), Math.round(rI * bh), Math.round((cI + 1) * bw),
                                Math.round((rI + 1) * bh));
                        rf.set(ox - bw * s / 2f, oy - bh * s / 2f, ox + bw * s / 2f, oy + bh * s / 2f);
                        bmpPaint.setAlpha((int) (255 * (1f - lt)));
                        c.drawBitmap(snap, src, rf, bmpPaint);
                    }
                }
                break;
            }
            case 7: { // paper drop
                float u = clamp01(t / 0.78f);
                collapse(c, 1f - ease((t - 0.74f) / 0.26f));
                drawReveal(c, 1f, 1f, 1f);
                c.translate(cdx - 36 * d * u, 560 * d * u * u);
                c.rotate(46f * u * u, 0, rh);
                drawRow(c, (int) (255 * (1f - clamp01((u - 0.55f) / 0.45f))), 0);
                break;
            }
            case 8: { // strike out
                float s = ease(t / 0.32f);
                float u = clamp01((t - 0.36f) / 0.4f);
                collapse(c, 1f - ease((t - 0.76f) / 0.24f));
                drawReveal(c, 1f, 1f, 1f);
                float x = cdx + (-rw - 30 * d - cdx) * u * u;
                c.translate(x, 0);
                drawRow(c, (int) (255 * (1f - 0.42f * s)), s);
                break;
            }
            case 9: { // motion trail
                float u = clamp01(t / 0.55f);
                float v = clamp01((t - 0.55f) / 0.45f);
                float hs = 1f - ease(v) + 0.07f * (float) Math.sin(v * Math.PI * 3) * (1f - v);
                collapse(c, Math.min(1.08f, hs));
                drawReveal(c, 1f, 1f, 1f);
                float span = -rw - 50 * d - cdx;
                for (int k = 5; k >= 0; k--) {
                    float uk = clamp01(u - k * 0.05f);
                    float xk = cdx + span * (float) Math.pow(uk, 2.2);
                    c.save();
                    c.translate(xk, 0);
                    drawRow(c, k == 0 ? 255 : (int) (95 - k * 14), 0);
                    c.restore();
                }
                break;
            }
            default:
                LabBurn.draw(this, c, style - 10, t);
                break;
        }
        c.restore();
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
                    dx = Math.min(0f, mx);
                    boolean past = -dx >= limit();
                    if (past && !armed) Hap.tick(this);
                    armed = past;
                    invalidate();
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (swiping) {
                    if (a == MotionEvent.ACTION_UP && -dx >= limit()) commit();
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
            dx = Math.min(0f, from * (1f - (Float) a.getAnimatedValue()));
            invalidate();
        });
        anim.start();
    }

    private void commit() {
        Hap.confirm(this);
        cdx = dx;
        t = 0;
        phase = COMMIT;
        deleted++;
        if (onDeleted != null) onDeleted.run();
        if (style == 1 || style == 6 || style >= 10) { // these cut the row up, so draw it once into a bitmap
            if (snap != null) snap.recycle();
            mos = null;
            snap = Bitmap.createBitmap(Math.max(1, Math.round(rw)), Math.max(1, Math.round(rh)),
                    Bitmap.Config.ARGB_8888);
            drawRow(new Canvas(snap), 255, 0);
        }
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(DURATION[style]);
        anim.setInterpolator(new LinearInterpolator());
        anim.addUpdateListener(a -> {
            t = (Float) a.getAnimatedValue();
            invalidate();
        });
        anim.addListener(new android.animation.AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationCancel(android.animation.Animator an) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(android.animation.Animator an) {
                if (cancelled) return;
                phase = GONE;
                t = 0;
                if (snap != null) {
                    snap.recycle();
                    snap = null;
                }
                invalidate();
                pending = () -> restore();
                postDelayed(pending, 750);
            }
        });
        anim.start();
    }

    private void restore() {
        pending = null;
        dx = 0;
        rt = 0;
        phase = RESTORE;
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(380);
        anim.addUpdateListener(a -> {
            rt = (Float) a.getAnimatedValue();
            invalidate();
        });
        anim.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator an) {
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
        if (snap != null) {
            snap.recycle();
            snap = null;
        }
        super.onDetachedFromWindow();
    }
}
