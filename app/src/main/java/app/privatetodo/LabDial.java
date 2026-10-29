package app.privatetodo;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;

/**
 * Design lab only: a swipe-up/down time dial (hour, minute, AM/PM) drawn in one of ten looks.
 * It is not connected to any reminder or task; it only shows the time you swipe to.
 */
final class LabDial extends View {
    static final String[] NAMES = {
            "Solid pill", "Barrel", "Neon", "Big number", "Ruler",
            "Cards", "Gradient", "Flip tiles", "Curve", "Lens",
            "Aurora", "Orbit", "Hologram", "Retro LCD", "Odometer", "Bubbles", "Spotlight", "Glass", "Gauge",
            "Keycaps", "Wave", "Film strip", "Neon tube", "Candy", "Terminal", "Blueprint", "Sunset",
            "Card fan", "Sonar", "Liquid"};
    static final String[] NOTES = {
            "A solid accent bar holds the chosen time. Strongest contrast.",
            "A rounded drum: rows squash and fade as they turn away.",
            "Dark panel, glowing numbers and light lines.",
            "The nearer a number is, the bigger and brighter it gets.",
            "A ruler scale with ticks that slide past a pointer.",
            "Raised cards; the chosen one stands out with an accent edge.",
            "The chosen time is painted with an accent gradient.",
            "Split-flap tiles like an old departure board.",
            "Numbers bend along an arc, a dot marks the chosen one.",
            "A round lens circles the chosen number.",
            "Dark sky with drifting aurora light and a glowing time.",
            "Rings and a tiny satellite orbit the chosen number.",
            "Cyan hologram with scan lines and colour fringing.",
            "Green LCD with ghost segments, like an old watch.",
            "Mechanical odometer: tens and units roll on separate reels.",
            "Number bubbles; the chosen one swells and fills with colour.",
            "A spotlight: only the chosen time is lit.",
            "Frosted glass over a colour gradient.",
            "A ring fills up as the value goes up.",
            "Real keycaps; the chosen one is pressed down.",
            "The column ripples like a wave, the chosen one rides the crest.",
            "Film strip with sprocket holes that roll with the dial.",
            "Glowing neon tube lettering on a dark wall.",
            "Soft candy pills, each a different colour.",
            "Green phosphor terminal with a block cursor.",
            "Blueprint paper with dimension brackets.",
            "Sunset gradient with a low sun.",
            "Cards fanned like a hand, the chosen one on top.",
            "Sonar rings ripple out as you turn.",
            "A tube of liquid that sloshes and fills with the value."};

    private static final int ROWS = 5;
    private static final float FRICTION = 1.9f;

    private final int style;
    private final float d;
    private final float rowH;
    private final int slop;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rf = new RectF();
    private final Path path = new Path();
    private final Typeface regular;
    private final Typeface bold;
    private final Col[] cols = new Col[3];
    private Col active;
    private Runnable onChange;

    private final class Col {
        final int count;
        final String[] labels;
        final boolean wrap;
        float pos;
        int lastIdx;
        float x0;
        float x1;
        VelocityTracker vt;
        float lastY;
        float downY;
        boolean drag;
        ValueAnimator anim;
        Runnable fling;
        long flingTime;
        float flingV;
        long lastHaptic;

        Col(String[] l, boolean w, int start) {
            labels = l;
            count = l.length;
            wrap = w;
            pos = start;
            lastIdx = start;
        }
    }

    LabDial(Context c, int style) {
        super(c);
        this.style = style;
        d = c.getResources().getDisplayMetrics().density;
        rowH = 38 * d;
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
        Fonts.init(c);
        regular = Fonts.face(false);
        bold = Fonts.face(true);
        String[] hours = new String[12];
        for (int i = 0; i < 12; i++) hours[i] = String.valueOf(i + 1);
        String[] mins = new String[60];
        for (int i = 0; i < 60; i++) mins[i] = (i < 10 ? "0" : "") + i;
        cols[0] = new Col(hours, true, 6);
        cols[1] = new Col(mins, true, 30);
        cols[2] = new Col(new String[]{"AM", "PM"}, false, 1);
        setFocusable(false);
    }

    // ---- read access for LabLooks
    float dens() {
        return d;
    }

    float rowH() {
        return rowH;
    }

    float cx0(int i) {
        return cols[i].x0;
    }

    float cx1(int i) {
        return cols[i].x1;
    }

    float cpos(int i) {
        return cols[i].pos;
    }

    int cn(int i) {
        return cols[i].count;
    }

    boolean cwrap(int i) {
        return cols[i].wrap;
    }

    String clabel(int i, int idx) {
        return cols[i].labels[idx];
    }

    void setOnChange(Runnable r) {
        onChange = r;
    }

    String time() {
        return cols[0].labels[cols[0].lastIdx] + ":" + cols[1].labels[cols[1].lastIdx] + " "
                + cols[2].labels[cols[2].lastIdx];
    }

    @Override
    protected void onMeasure(int w, int h) {
        setMeasuredDimension(resolveSize(Math.round(280 * d), w), Math.round(rowH * ROWS));
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth();
        float h = getHeight();
        float cy = h / 2f;
        float pad = 14 * d;
        float avail = w - 2 * pad;
        float[] fr = {0.36f, 0.36f, 0.28f};
        float x = pad;
        for (int i = 0; i < 3; i++) {
            cols[i].x0 = x;
            x += avail * fr[i];
            cols[i].x1 = x;
        }

        if (style >= 10) {
            LabLooks.draw(this, c, style);
            return;
        }

        // panel behind everything
        if (style == 2) {
            p.setColor(0xFF0B1412);
            rf.set(0, 0, w, h);
            c.drawRoundRect(rf, 22 * d, 22 * d, p);
        } else if (style == 7) {
            p.setColor(0xFF1C1F1E);
            rf.set(0, 0, w, h);
            c.drawRoundRect(rf, 22 * d, 22 * d, p);
        } else if (style == 1 || style == 4 || style == 5 || style == 6 || style == 8 || style == 9) {
            p.setColor(Pal.chip);
            rf.set(0, 0, w, h);
            c.drawRoundRect(rf, 22 * d, 22 * d, p);
        }

        // full-width bands behind the chosen row
        switch (style) {
            case 0:
                p.setColor(Pal.accent);
                rf.set(8 * d, cy - rowH / 2f + 2 * d, w - 8 * d, cy + rowH / 2f - 2 * d);
                c.drawRoundRect(rf, 15 * d, 15 * d, p);
                break;
            case 1:
                p.setColor((Pal.accent & 0x00FFFFFF) | 0x2A000000);
                c.drawRect(0, cy - rowH / 2f, w, cy + rowH / 2f, p);
                p.setColor(Pal.accent);
                c.drawRect(0, cy - rowH / 2f - d, w, cy - rowH / 2f + d, p);
                c.drawRect(0, cy + rowH / 2f - d, w, cy + rowH / 2f + d, p);
                break;
            case 2:
                int[] gw = {10, 6, 3};
                int[] ga = {0x18, 0x30, 0xA0};
                p.setStyle(Paint.Style.STROKE);
                for (int i = 0; i < 3; i++) {
                    p.setStrokeWidth(gw[i] * d);
                    p.setColor((0x5CF2D6) | (ga[i] << 24));
                    c.drawLine(18 * d, cy - rowH / 2f, w - 18 * d, cy - rowH / 2f, p);
                    c.drawLine(18 * d, cy + rowH / 2f, w - 18 * d, cy + rowH / 2f, p);
                }
                p.setStyle(Paint.Style.FILL);
                break;
            case 4:
                p.setColor((Pal.accent & 0x00FFFFFF) | 0x1E000000);
                rf.set(8 * d, cy - rowH / 2f, w - 8 * d, cy + rowH / 2f);
                c.drawRoundRect(rf, 12 * d, 12 * d, p);
                break;
            case 6:
                p.setShader(new LinearGradient(0, 0, w, 0, Pal.accent, 0xFF7C4DFF, Shader.TileMode.CLAMP));
                c.drawRect(18 * d, cy - rowH / 2f - d, w - 18 * d, cy - rowH / 2f + d, p);
                c.drawRect(18 * d, cy + rowH / 2f - d, w - 18 * d, cy + rowH / 2f + d, p);
                p.setShader(null);
                break;
            default:
                break;
        }

        for (int i = 0; i < 3; i++) drawCol(c, cols[i], i);

        // colon between hour and minute
        float colonX = (cols[0].x1 + cols[1].x0) / 2f;
        int ink = Pal.ink;
        float size = 24;
        if (style == 0) ink = Pal.onAccent;
        else if (style == 2) ink = 0xFF5CF2D6;
        else if (style == 3) {
            ink = Pal.accent;
            size = 34;
        } else if (style == 6) ink = 0xFF7C4DFF;
        else if (style == 7) ink = 0xFFE6E6E6;
        else if (style == 4 || style == 8 || style == 9) ink = Pal.accent;
        p.setTypeface(bold);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(size * d);
        p.setColor(ink);
        c.drawText(":", colonX, cy - (p.ascent() + p.descent()) / 2f - 2 * d, p);

        // soft fade at the top and bottom edge for the flat looks
        if (style == 0 || style == 3 || style == 5) {
            int bg = style == 5 ? Pal.chip : Pal.card;
            p.setShader(new LinearGradient(0, 0, 0, rowH * 1.3f, bg, bg & 0x00FFFFFF, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, rowH * 1.3f, p);
            p.setShader(new LinearGradient(0, h - rowH * 1.3f, 0, h, bg & 0x00FFFFFF, bg, Shader.TileMode.CLAMP));
            c.drawRect(0, h - rowH * 1.3f, w, h, p);
            p.setShader(null);
        }
    }

    private void drawCol(Canvas c, Col k, int colIndex) {
        float cy = getHeight() / 2f;
        float cx = (k.x0 + k.x1) / 2f;
        float cw = k.x1 - k.x0;
        int n = k.count;
        int base = Math.round(k.pos);

        if (style == 4) { // ruler ticks run down the left of the column
            float tx = k.x0 + 2 * d;
            int g0 = (int) Math.floor(k.pos) * 4;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            for (int s = -14; s <= 14; s++) {
                int g = g0 + s;
                float y = cy + (g / 4f - k.pos) * rowH;
                if (y < 4 * d || y > getHeight() - 4 * d) continue;
                boolean major = ((g % 4) + 4) % 4 == 0;
                if (!k.wrap && (g < 0 || g > (n - 1) * 4)) continue;
                float near = Math.max(0f, 1f - Math.abs(y - cy) / (rowH * 1.6f));
                p.setStrokeWidth((major ? 2f : 1.2f) * d);
                p.setColor(near > 0.55f ? Pal.accent : Pal.muted);
                p.setAlpha((int) (60 + 195 * near));
                c.drawLine(tx, y, tx + (major ? 14 : 7) * d, y, p);
            }
            p.setStyle(Paint.Style.FILL);
            if (colIndex == 0) { // pointer for the whole row, once
                path.reset();
                path.moveTo(2 * d, cy - 6 * d);
                path.lineTo(2 * d, cy + 6 * d);
                path.lineTo(10 * d, cy);
                path.close();
                p.setColor(Pal.accent);
                p.setAlpha(255);
                c.drawPath(path, p);
            }
            cx += 8 * d;
        }

        for (int r = -ROWS / 2 - 1; r <= ROWS / 2 + 1; r++) {
            int idx = base + r;
            if (!k.wrap && (idx < 0 || idx >= n)) continue;
            int real = ((idx % n) + n) % n;
            float dist = idx - k.pos;
            float ad = Math.abs(dist);
            if (ad > 2.7f) continue;
            String s = k.labels[real];
            float near = Math.max(0f, 1f - ad); // 1 at the centre, 0 one row away
            boolean centre = ad < 0.5f;
            float y = cy + dist * rowH;
            switch (style) {
                case 0:
                    text(c, s, cx, y, centre ? 24 : 18, centre ? Pal.onAccent : Pal.ink,
                            centre ? 255 : (int) Math.max(50, 210 - ad * 70), centre);
                    break;
                case 1: {
                    float ang = Math.min(1.35f, ad * 0.5f);
                    float sgn = dist < 0 ? -1 : 1;
                    float R = rowH / 0.5f;
                    float yy = cy + sgn * (float) Math.sin(ang) * R;
                    float sc = (float) Math.cos(ang);
                    c.save();
                    c.translate(cx, yy);
                    c.scale(1f, Math.max(0.25f, sc));
                    text(c, s, 0, 0, centre ? 24 : 20, centre ? Pal.ink : Pal.muted,
                            (int) Math.max(30, 255 * sc * sc), centre);
                    c.restore();
                    break;
                }
                case 2:
                    if (centre) {
                        p.setShadowLayer(14 * d, 0, 0, 0xFF5CF2D6);
                        text(c, s, cx, y, 28, 0xFF5CF2D6, 255, true);
                        p.clearShadowLayer();
                    } else {
                        text(c, s, cx, y, 18, 0xFF5E8F86, (int) Math.max(40, 190 - ad * 70), false);
                    }
                    break;
                case 3: {
                    float sz = 16 + 22 * (float) Math.pow(near, 1.4);
                    int col = blend(Pal.muted, Pal.accent, near);
                    text(c, s, cx, y, sz, col, (int) (90 + 165 * near), near > 0.5f);
                    if (centre) {
                        p.setColor(Pal.accent);
                        rf.set(cx - 14 * d, y + 22 * d, cx + 14 * d, y + 25 * d);
                        c.drawRoundRect(rf, 2 * d, 2 * d, p);
                    }
                    break;
                }
                case 4:
                    text(c, s, cx, y, centre ? 24 : 18, centre ? Pal.accent : Pal.muted,
                            (int) Math.max(60, 255 - ad * 80), centre);
                    break;
                case 5: {
                    float sc = 1f - 0.11f * Math.min(ad, 2f);
                    float hw = (cw - 10 * d) * sc / 2f;
                    float hh = (rowH - 7 * d) * sc / 2f;
                    float yy = cy + dist * rowH * (0.97f);
                    int al = (int) Math.max(40, 255 - ad * 85);
                    if (centre) { // soft layered shadow, then the card
                        for (int q = 3; q >= 1; q--) {
                            p.setColor(0x14000000);
                            rf.set(cx - hw - q * d, yy - hh + q * d, cx + hw + q * d, yy + hh + 3 * q * d);
                            c.drawRoundRect(rf, 14 * d, 14 * d, p);
                        }
                    }
                    p.setColor(Pal.card);
                    p.setAlpha(al);
                    rf.set(cx - hw, yy - hh, cx + hw, yy + hh);
                    c.drawRoundRect(rf, 13 * d, 13 * d, p);
                    if (centre) {
                        p.setStyle(Paint.Style.STROKE);
                        p.setStrokeWidth(2 * d);
                        p.setColor(Pal.accent);
                        c.drawRoundRect(rf, 13 * d, 13 * d, p);
                        p.setStyle(Paint.Style.FILL);
                    }
                    text(c, s, cx, yy, centre ? 24 : 18, centre ? Pal.accent : Pal.muted, al, centre);
                    break;
                }
                case 6:
                    if (centre) {
                        p.setTypeface(bold);
                        p.setTextSize(30 * d);
                        float tw = p.measureText(s);
                        p.setShader(new LinearGradient(cx - tw / 2f, y - 14 * d, cx + tw / 2f, y + 14 * d,
                                Pal.accent, 0xFF7C4DFF, Shader.TileMode.CLAMP));
                        p.setTextAlign(Paint.Align.CENTER);
                        p.setColor(0xFFFFFFFF);
                        c.drawText(s, cx, y - (p.ascent() + p.descent()) / 2f, p);
                        p.setShader(null);
                    } else {
                        text(c, s, cx, y, 18, Pal.muted, (int) Math.max(40, 200 - ad * 70), false);
                    }
                    break;
                case 7: {
                    float ang = Math.min(1.3f, ad * 0.42f);
                    float sgn = dist < 0 ? -1 : 1;
                    float R = rowH / 0.42f;
                    float yy = cy + sgn * (float) Math.sin(ang) * R;
                    float sc = Math.max(0.25f, (float) Math.cos(ang));
                    float hw = (cw - 8 * d) / 2f;
                    float hh = (rowH - 6 * d) / 2f * sc;
                    p.setColor(centre ? Pal.accent : 0xFF2C312F);
                    p.setAlpha((int) Math.max(60, 255 * sc));
                    rf.set(cx - hw, yy - hh, cx + hw, yy + hh);
                    c.drawRoundRect(rf, 9 * d, 9 * d, p);
                    p.setColor(centre ? 0x55000000 : 0xFF141716); // the split line across the middle
                    c.drawRect(cx - hw, yy - d * 0.6f, cx + hw, yy + d * 0.6f, p);
                    c.save();
                    c.translate(cx, yy);
                    c.scale(1f, sc);
                    text(c, s, 0, 0, centre ? 24 : 19, centre ? Pal.onAccent : 0xFFD7DCDA,
                            (int) Math.max(60, 255 * sc), true);
                    c.restore();
                    break;
                }
                case 8: {
                    float dx = dist * dist * 5 * d;
                    c.save();
                    c.translate(cx + dx, y);
                    c.rotate(dist * 7f);
                    text(c, s, 0, 0, centre ? 25 : 19, centre ? Pal.accent : Pal.muted,
                            (int) Math.max(40, 230 - ad * 70), centre);
                    c.restore();
                    if (centre) {
                        p.setColor(Pal.accent);
                        c.drawCircle(k.x0 + 5 * d, y, 4 * d, p);
                    }
                    break;
                }
                default: { // 9: lens
                    float sc = centre ? 1f : 0.9f - 0.05f * Math.min(ad, 2f);
                    text(c, s, cx, y, (centre ? 24 : 19) * sc, centre ? Pal.ink : Pal.muted,
                            (int) Math.max(40, 220 - ad * 70), centre);
                    break;
                }
            }
        }
        if (style == 9) { // the lens itself sits on top
            float rad = rowH * 0.62f;
            p.setColor((Pal.accent & 0x00FFFFFF) | 0x22000000);
            c.drawCircle(cx, cy, rad, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2.2f * d);
            p.setColor(Pal.accent);
            c.drawCircle(cx, cy, rad, p);
            p.setStyle(Paint.Style.FILL);
        }
    }

    private void text(Canvas c, String s, float x, float y, float sp, int color, int alpha, boolean b) {
        p.setTypeface(b ? bold : regular);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(sp * d);
        p.setColor(color);
        p.setAlpha(Math.max(0, Math.min(255, alpha)));
        c.drawText(s, x, y - (p.ascent() + p.descent()) / 2f, p);
        p.setAlpha(255);
    }

    private static int blend(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    // ------------------------------------------------------------------ input

    private Col colAt(float x) {
        for (Col k : cols) if (x < k.x1) return k;
        return cols[2];
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                active = colAt(e.getX());
                stop(active);
                getParent().requestDisallowInterceptTouchEvent(true);
                active.downY = active.lastY = e.getY();
                active.drag = false;
                active.vt = VelocityTracker.obtain();
                active.vt.addMovement(e);
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                Col k = active;
                if (k == null) return true;
                k.vt.addMovement(e);
                if (!k.drag && Math.abs(e.getY() - k.downY) > slop) k.drag = true;
                if (k.drag) {
                    setPos(k, k.pos - (e.getY() - k.lastY) / rowH);
                    k.lastY = e.getY();
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                Col k = active;
                active = null;
                if (k == null) return true;
                k.vt.addMovement(e);
                k.vt.computeCurrentVelocity(1000);
                float v = -k.vt.getYVelocity() / rowH;
                v = Math.max(-110f, Math.min(110f, v));
                k.vt.recycle();
                k.vt = null;
                if (!k.drag && e.getActionMasked() == MotionEvent.ACTION_UP) {
                    float off = e.getY() - getHeight() / 2f;
                    settle(k, Math.round(k.pos) + Math.round(off / rowH));
                } else if (Math.abs(v) > 3f) {
                    fling(k, v);
                } else {
                    settle(k, Math.round(k.pos));
                }
                return true;
            }
            default:
                return true;
        }
    }

    private void fling(final Col k, float v) {
        stop(k);
        k.flingV = v;
        k.flingTime = System.nanoTime();
        k.fling = new Runnable() {
            @Override
            public void run() {
                long now = System.nanoTime();
                float dt = Math.min(0.04f, (now - k.flingTime) / 1e9f);
                k.flingTime = now;
                setPos(k, k.pos + k.flingV * dt);
                k.flingV *= (float) Math.exp(-FRICTION * dt);
                boolean edge = !k.wrap && (k.pos <= 0f || k.pos >= k.count - 1f);
                if (Math.abs(k.flingV) < 1.6f || edge) {
                    k.fling = null;
                    settle(k, Math.round(k.pos));
                } else {
                    postOnAnimation(this);
                }
            }
        };
        postOnAnimation(k.fling);
    }

    private void setPos(Col k, float v) {
        if (!k.wrap) v = Math.max(0, Math.min(k.count - 1, v));
        k.pos = v;
        int n = k.count;
        int idx = ((Math.round(v) % n) + n) % n;
        if (idx != k.lastIdx) {
            k.lastIdx = idx;
            long t = SystemClock.uptimeMillis();
            if (t - k.lastHaptic > 45) {
                k.lastHaptic = t;
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            }
            if (onChange != null) onChange.run();
        }
        invalidate();
    }

    private void settle(final Col k, int target) {
        if (!k.wrap) target = Math.max(0, Math.min(k.count - 1, target));
        float from = k.pos;
        float to = target;
        stop(k);
        k.anim = ValueAnimator.ofFloat(from, to);
        k.anim.setDuration(Math.min(520, 170 + (long) (Math.abs(to - from) * 260)));
        k.anim.setInterpolator(new DecelerateInterpolator());
        k.anim.addUpdateListener(a -> setPos(k, (Float) a.getAnimatedValue()));
        k.anim.start();
    }

    private void stop(Col k) {
        if (k.fling != null) {
            removeCallbacks(k.fling);
            k.fling = null;
        }
        if (k.anim != null) {
            k.anim.cancel();
            k.anim = null;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        for (Col k : cols) stop(k);
        super.onDetachedFromWindow();
    }
}
