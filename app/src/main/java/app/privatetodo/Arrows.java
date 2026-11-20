package app.privatetodo;

import android.graphics.Canvas;
import android.graphics.CornerPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.Editable;
import android.text.Layout;
import android.text.Spanned;
import android.view.MotionEvent;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Drawing on a note: boxes, squircles, pencil circles and scribbles round or over text, and arrows between them (or
 * between any two pieces of text). Shapes can be held and moved. An arrow leaves and enters at one of four points
 * (top, right, bottom, left) and its middle segment can be dragged without ever crossing the text it joins.
 * Everything hangs on inert spans, so it follows the words when the note is edited. Saved in the note's JSON.
 */
final class Arrows {
    static final int K_WORD = -1, K_BOX = 0, K_SQUIRCLE = 1, K_CIRCLE = 2, K_SCRIBBLE = 3, K_INK = 4;
    static final int MODE_OFF = 0, MODE_BOX = 1, MODE_ARROW = 2, MODE_SQUIRCLE = 3, MODE_CIRCLE = 4, MODE_SCRIBBLE = 5, MODE_INK = 6;
    static final int TOP = 0, RIGHT = 1, BOTTOM = 2, LEFT = 3;

    /** A piece of text that can carry a shape and/or an arrow end. ox / oy: how far the shape was moved, in dp. */
    static final class Anchor {
        int id;
        int kind = K_WORD;
        int style;
        float ox, oy;
        float[] pts; // K_INK: the stroke, x,y pairs in dp from the anchor character's top-left
        String ftext; // a shape that was moved: it floats freely and carries its own words (null = it hugs the text)
        android.text.Spannable frich; // those words with their own fonts, sizes, colours and styles
        android.text.StaticLayout fl; // cached layout of ftext
        String flFor;
        float flSize;
        final List<Out> out = new ArrayList<>();
    }

    /** An arrow leaving an anchor. fs / ts: the point it leaves from and arrives at. mid: where the middle segment sits. */
    static final class Out {
        int to;
        float mid = 0.5f;
        int color = 0xFFD2452B;
        int fs = -1, ts = -1;
        float wx = Float.NaN, wy = Float.NaN; // a point the line is dragged through, dp from the source shape's top-left
    }

    private static final int[] PALETTE = {0xFFD2452B, 0xFF2E7DE0, 0xFF2FA866, 0xFFE9B800, 0xFF8B5CD6, 0xFF444B55};

    private final RichEditText v;
    private final float d;
    int mode = MODE_OFF;
    int inkPencil = 6; // HB
    boolean inkErase;
    private Out selected;
    private final List<float[]> stroke = new ArrayList<>();
    private Anchor dragFrom;
    private int[] dragWord;
    private float dragX, dragY, downX, downY;
    private Out moving;
    private Anchor movingFrom;
    private boolean tapCandidate;
    private Anchor holdShape;
    private Anchor movingShape;
    private float shapeOx0, shapeOy0;
    private Runnable holdTimer;
    private int endDrag; // 3 = carrying an arrow's end, 4 = its start
    private Out endOut;
    private Anchor endFrom;
    private Anchor tapFloat;
    private FloatTap floatTap;

    /** Tapping a floating shape (one that was moved) asks the editor to edit its words. */
    interface FloatTap {
        void onFloatTap(Anchor a);
    }

    void setFloatTap(FloatTap f) {
        floatTap = f;
    }
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);

    Arrows(RichEditText v) {
        this.v = v;
        d = v.getResources().getDisplayMetrics().density;
    }

    // ------------------------------------------------------------ model

    private Editable text() {
        return v.getText();
    }

    List<Anchor> anchors() {
        Editable e = text();
        List<Anchor> l = new ArrayList<>();
        for (Anchor a : e.getSpans(0, e.length(), Anchor.class)) {
            if (e.getSpanEnd(a) > e.getSpanStart(a) || pinned(a)) l.add(a);
        }
        return l;
    }

    /** Pencil strokes and floating shapes hang on a zero-width mark, so deleting the text near them never deletes them. */
    static boolean pinned(Anchor a) {
        return a.kind == K_INK || a.ftext != null;
    }

    private int nextId() {
        int m = 0;
        for (Anchor a : text().getSpans(0, text().length(), Anchor.class)) m = Math.max(m, a.id);
        return m + 1;
    }

    private Anchor byId(int id) {
        for (Anchor a : anchors()) if (a.id == id) return a;
        return null;
    }

    private Anchor make(int a, int b, int kind, int style) {
        Anchor n = new Anchor();
        n.id = nextId();
        n.kind = kind;
        n.style = style;
        text().setSpan(n, a, b, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return n;
    }

    void clearAll() {
        Editable e = text();
        for (Anchor a : e.getSpans(0, e.length(), Anchor.class)) e.removeSpan(a);
        selected = null;
        v.invalidate();
    }

    // ------------------------------------------------------------ geometry (content coordinates)

    private RectF textRect(int a, int b) {
        Layout l = v.getLayout();
        if (l == null || a >= b) return null;
        int len = text().length();
        a = Math.max(0, Math.min(a, len));
        b = Math.max(a, Math.min(b, len));
        int la = l.getLineForOffset(a), lb = l.getLineForOffset(Math.max(a, b - 1));
        float left = Float.MAX_VALUE, right = 0;
        for (int ln = la; ln <= lb; ln++) {
            int s = Math.max(a, l.getLineStart(ln)), e = Math.min(b, l.getLineEnd(ln));
            if (e <= s) continue;
            left = Math.min(left, l.getPrimaryHorizontal(s));
            right = Math.max(right, l.getPrimaryHorizontal(e));
        }
        if (left == Float.MAX_VALUE) return null;
        if (right < left) {
            float t = left;
            left = right;
            right = t;
        }
        RectF r = new RectF(left, l.getLineTop(la), Math.max(right, left + 4 * d), l.getLineBottom(lb));
        r.offset(v.getTotalPaddingLeft(), v.getTotalPaddingTop());
        return r;
    }

    /** Where the thing is drawn: the text's rectangle, grown a little for shapes, then moved by the user's offset. */
    private RectF rectOf(Anchor a) {
        Editable e = text();
        if (a.kind == K_INK) return inkBounds(a);
        RectF r = a.ftext != null ? floatRect(a) : textRect(e.getSpanStart(a), e.getSpanEnd(a));
        if (r == null) return null;
        switch (a.kind) {
            case K_BOX:
            case K_SQUIRCLE:
                r.inset(-6 * d, -2 * d);
                break;
            case K_CIRCLE:
                r.inset(-10 * d, -4 * d);
                break;
            default:
                break;
        }
        r.offset(a.ox * d, a.oy * d);
        return r;
    }

    /** The words a floating shape carries, laid out once per change. */
    private android.text.StaticLayout floatLayout(Anchor a) {
        float sz = v.getTextSize();
        if (a.fl == null || !a.ftext.equals(a.flFor) || a.flSize != sz) {
            android.text.TextPaint tp = new android.text.TextPaint(v.getPaint());
            tp.setColor(Pal.ink);
            CharSequence src = a.frich != null && a.frich.toString().equals(a.ftext) ? a.frich : a.ftext;
            int maxW = Math.max((int) (80 * d), v.getWidth() - v.getTotalPaddingLeft() - v.getTotalPaddingRight() - (int) (24 * d));
            float want = Layout.getDesiredWidth(src, tp); // with its own fonts and sizes, so the box fits exactly
            int w = (int) Math.min(maxW, Math.max(12 * d, Math.ceil(want) + 2));
            a.fl = android.text.StaticLayout.Builder.obtain(src, 0, src.length(), tp, w)
                    .setLineSpacing(v.getLineSpacingExtra(), v.getLineSpacingMultiplier()).build();
            a.flFor = a.ftext;
            a.flSize = sz;
        }
        return a.fl;
    }

    /** A floating shape's words: placed from its anchor character plus its own offset. */
    private RectF floatRect(Anchor a) {
        float[] o = inkOrigin(a);
        if (o == null) return null;
        android.text.StaticLayout sl = floatLayout(a);
        float x = o[0] + a.ox * d, y = o[1] + a.oy * d;
        return new RectF(x, y, x + sl.getWidth(), y + sl.getHeight());
    }

    /** Where an ink stroke's anchor character sits (content coordinates), or null. */
    private float[] inkOrigin(Anchor a) {
        Layout l = v.getLayout();
        int s0 = text().getSpanStart(a);
        if (l == null || s0 < 0 || s0 > text().length()) return null;
        int line = l.getLineForOffset(s0);
        return new float[]{l.getPrimaryHorizontal(s0) + v.getTotalPaddingLeft(), l.getLineTop(line) + v.getTotalPaddingTop()};
    }

    private float[] inkPx(Anchor a) {
        float[] o = inkOrigin(a);
        if (o == null || a.pts == null) return null;
        float[] xy = new float[a.pts.length];
        for (int i = 0; i + 1 < xy.length; i += 2) {
            xy[i] = o[0] + (a.pts[i] + a.ox) * d;
            xy[i + 1] = o[1] + (a.pts[i + 1] + a.oy) * d;
        }
        return xy;
    }

    private RectF inkBounds(Anchor a) {
        float[] xy = inkPx(a);
        if (xy == null || xy.length < 2) return null;
        RectF r = new RectF(xy[0], xy[1], xy[0], xy[1]);
        for (int i = 0; i + 1 < xy.length; i += 2) r.union(xy[i], xy[i + 1]);
        return r;
    }

    private static float[] pt(RectF r, int side) {
        switch (side) {
            case TOP:
                return new float[]{r.centerX(), r.top};
            case RIGHT:
                return new float[]{r.right, r.centerY()};
            case BOTTOM:
                return new float[]{r.centerX(), r.bottom};
            default:
                return new float[]{r.left, r.centerY()};
        }
    }

    private static int nearestSide(RectF r, float x, float y) {
        int best = 0;
        float bd = Float.MAX_VALUE;
        for (int s = 0; s < 4; s++) {
            float[] p = pt(r, s);
            float dd = (p[0] - x) * (p[0] - x) + (p[1] - y) * (p[1] - y);
            if (dd < bd) {
                bd = dd;
                best = s;
            }
        }
        return best;
    }

    /** Which side an arrow leaves from when none was chosen: along the axis where the two pieces are furthest apart. */
    private static int autoSide(RectF s, RectF t) {
        float dx = t.centerX() - s.centerX(), dy = t.centerY() - s.centerY();
        float gx = Math.abs(dx) - (s.width() + t.width()) / 2f;
        float gy = Math.abs(dy) - (s.height() + t.height()) / 2f;
        boolean horiz;
        if (gx > 0 && gy > 0) horiz = gx >= gy;
        else if (gx > 0) horiz = true;
        else if (gy > 0) horiz = false;
        else horiz = Math.abs(dx) / (s.width() + t.width()) >= Math.abs(dy) / (s.height() + t.height());
        if (horiz) return dx >= 0 ? RIGHT : LEFT;
        return dy >= 0 ? BOTTOM : TOP;
    }

    /** The connection point within reach of the touch, or -1 (then the side is picked from where the two pieces sit). */
    private int snapSide(RectF r, float x, float y) {
        int s = nearestSide(r, x, y);
        float[] p = pt(r, s);
        return dist(x, y, p[0], p[1]) <= 26 * d ? s : -1;
    }

    private static boolean vertical(int side) {
        return side == TOP || side == BOTTOM;
    }

    private static final float[][] DIRS = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}}; // out of TOP, RIGHT, BOTTOM, LEFT
    private int rFs, rTs; // the sides the last route() used
    private float[] rA1, rB1;

    /**
     * Fills p with the arrow's path; returns {handleX, handleY, endX, endY, startX, startY}.
     * Like a diagram tool: every allowed pair of connection points (all four of each, or the ones the person chose)
     * is tried with a handful of right-angled routes (an L, a Z, or a detour round both pieces), and the shortest one
     * that never runs through either piece wins. So an arrow cannot cut through the words it joins.
     */
    private float[] route(RectF s, RectF t, Out o, Path p) {
        float g = 14 * d;
        float[] best = null;
        double bestCost = Double.MAX_VALUE;
        int bf = 0, bt = 0;
        for (int fs = 0; fs < 4; fs++) {
            if (o.fs >= 0 && fs != o.fs) continue;
            for (int ts = 0; ts < 4; ts++) {
                if (o.ts >= 0 && ts != o.ts) continue;
                float[] a = pt(s, fs), b = pt(t, ts);
                float[] a1 = {a[0] + DIRS[fs][0] * g, a[1] + DIRS[fs][1] * g};
                float[] b1 = {b[0] + DIRS[ts][0] * g, b[1] + DIRS[ts][1] * g};
                float mx = a1[0] + (b1[0] - a1[0]) * o.mid, my = a1[1] + (b1[1] - a1[1]) * o.mid;
                float top = Math.min(s.top, t.top) - g, bot = Math.max(s.bottom, t.bottom) + g;
                float lef = Math.min(s.left, t.left) - g, rig = Math.max(s.right, t.right) + g;
                boolean via = !Float.isNaN(o.wx);
                float wx = s.left + (via ? o.wx : 0) * d, wy = s.top + (via ? o.wy : 0) * d;
                float[][] cands = via ? new float[][]{ // dragged through a point: every right-angled way through it
                        {wx, a1[1], wx, wy, b1[0], wy},
                        {wx, a1[1], wx, wy, wx, b1[1]},
                        {a1[0], wy, wx, wy, b1[0], wy},
                        {a1[0], wy, wx, wy, wx, b1[1]}} : new float[][]{
                        {b1[0], a1[1]},
                        {a1[0], b1[1]},
                        {mx, a1[1], mx, b1[1]},
                        {a1[0], my, b1[0], my},
                        {a1[0], top, b1[0], top},
                        {a1[0], bot, b1[0], bot},
                        {lef, a1[1], lef, b1[1]},
                        {rig, a1[1], rig, b1[1]}};
                for (float[] mid : cands) {
                    float[] poly = new float[8 + mid.length];
                    poly[0] = a[0];
                    poly[1] = a[1];
                    poly[2] = a1[0];
                    poly[3] = a1[1];
                    System.arraycopy(mid, 0, poly, 4, mid.length);
                    poly[4 + mid.length] = b1[0];
                    poly[5 + mid.length] = b1[1];
                    poly[6 + mid.length] = b[0];
                    poly[7 + mid.length] = b[1];
                    double cost = cost(poly, s, t, g);
                    if (cost < bestCost) {
                        bestCost = cost;
                        best = poly;
                        bf = fs;
                        bt = ts;
                    }
                }
            }
        }
        rFs = bf;
        rTs = bt;
        float[] a = pt(s, bf), b = pt(t, bt);
        rA1 = new float[]{a[0] + DIRS[bf][0] * g, a[1] + DIRS[bf][1] * g};
        rB1 = new float[]{b[0] + DIRS[bt][0] * g, b[1] + DIRS[bt][1] * g};
        best = simplify(best);
        p.reset();
        p.moveTo(best[0], best[1]);
        for (int i = 2; i + 1 < best.length; i += 2) p.lineTo(best[i], best[i + 1]);
        // the handle sits on the middle of the longest inner segment
        float hx = (best[0] + best[best.length - 2]) / 2f, hy = (best[1] + best[best.length - 1]) / 2f;
        float longest = -1;
        int n = best.length / 2;
        for (int i = (n > 2 ? 1 : 0); i < (n > 2 ? n - 2 : n - 1); i++) {
            float x1 = best[i * 2], y1 = best[i * 2 + 1], x2 = best[i * 2 + 2], y2 = best[i * 2 + 3];
            float len = Math.abs(x2 - x1) + Math.abs(y2 - y1);
            if (len > longest) {
                longest = len;
                hx = (x1 + x2) / 2f;
                hy = (y1 + y2) / 2f;
            }
        }
        if (!Float.isNaN(o.wx)) {
            hx = s.left + o.wx * d;
            hy = s.top + o.wy * d;
        }
        return new float[]{hx, hy, b[0], b[1], a[0], a[1]};
    }

    /** Length plus a little per bend; a route that runs through either piece is all but ruled out. */
    private double cost(float[] poly, RectF s, RectF t, float g) {
        RectF si = new RectF(s), ti = new RectF(t);
        si.inset(-g * 0.45f, -g * 0.45f);
        ti.inset(-g * 0.45f, -g * 0.45f);
        double len = 0;
        int bends = 0, bad = 0;
        int n = poly.length / 2;
        float wl = 3 * d, wr = v.getWidth() - 3 * d;
        for (int i = 0; i < n; i++) { // never past the page's edges
            float px = poly[i * 2], py = poly[i * 2 + 1];
            if (px < wl || px > wr || py < 0) bad++;
        }
        for (int i = 0; i < n - 1; i++) {
            float x1 = poly[i * 2], y1 = poly[i * 2 + 1], x2 = poly[i * 2 + 2], y2 = poly[i * 2 + 3];
            len += Math.abs(x2 - x1) + Math.abs(y2 - y1);
            if (i > 0 && i < n - 2) { // the first and last pieces touch their shape by design
                if (hits(si, x1, y1, x2, y2)) bad++;
                if (hits(ti, x1, y1, x2, y2)) bad++;
            }
            if (i > 0) {
                float px = poly[i * 2] - poly[i * 2 - 2], py = poly[i * 2 + 1] - poly[i * 2 - 1];
                float qx = x2 - x1, qy = y2 - y1;
                if (Math.abs(px * qy - py * qx) > 0.5f) bends++;
                if (px * qx + py * qy < -0.5f) bad++; // doubling straight back on itself
            }
        }
        return len + bends * 10 * d + bad * 1e6;
    }

    private static boolean hits(RectF r, float x1, float y1, float x2, float y2) {
        if (Math.abs(y1 - y2) < 0.5f) { // horizontal
            return y1 > r.top && y1 < r.bottom && Math.max(x1, x2) > r.left && Math.min(x1, x2) < r.right;
        }
        if (Math.abs(x1 - x2) < 0.5f) { // vertical
            return x1 > r.left && x1 < r.right && Math.max(y1, y2) > r.top && Math.min(y1, y2) < r.bottom;
        }
        return r.intersects(Math.min(x1, x2), Math.min(y1, y2), Math.max(x1, x2), Math.max(y1, y2));
    }

    /** Drops repeated points and points in the middle of a straight run. */
    private static float[] simplify(float[] poly) {
        List<float[]> pts = new ArrayList<>();
        for (int i = 0; i + 1 < poly.length; i += 2) {
            float[] q = {poly[i], poly[i + 1]};
            if (!pts.isEmpty()) {
                float[] l = pts.get(pts.size() - 1);
                if (Math.abs(l[0] - q[0]) < 0.5f && Math.abs(l[1] - q[1]) < 0.5f) continue;
                if (pts.size() >= 2) {
                    float[] k = pts.get(pts.size() - 2);
                    boolean straight = (Math.abs(k[0] - l[0]) < 0.5f && Math.abs(l[0] - q[0]) < 0.5f)
                            || (Math.abs(k[1] - l[1]) < 0.5f && Math.abs(l[1] - q[1]) < 0.5f);
                    float dx1 = l[0] - k[0], dy1 = l[1] - k[1], dx2 = q[0] - l[0], dy2 = q[1] - l[1];
                    if (straight && dx1 * dx2 + dy1 * dy2 > 0) pts.remove(pts.size() - 1);
                }
            }
            pts.add(q);
        }
        float[] out = new float[pts.size() * 2];
        for (int i = 0; i < pts.size(); i++) {
            out[i * 2] = pts.get(i)[0];
            out[i * 2 + 1] = pts.get(i)[1];
        }
        return out;
    }

    // ------------------------------------------------------------ drawing

    /** While a shape is carried its words come with it: drawn again where the shape is, over a patch of paper. */
    private void ghost(Canvas c, Anchor a, RectF r) {
        Editable e = text();
        int s0 = e.getSpanStart(a), e0 = e.getSpanEnd(a);
        if (s0 < 0 || e0 <= s0) return;
        fill.setStyle(Paint.Style.FILL);
        fill.setColor((Pal.card & 0x00FFFFFF) | 0xF2000000);
        c.drawRoundRect(r, 8 * d, 8 * d, fill);
        CharSequence seg = e.subSequence(s0, e0);
        int w = (int) Math.max(40 * d, r.width() + 40 * d);
        android.text.StaticLayout sl = android.text.StaticLayout.Builder
                .obtain(seg, 0, seg.length(), v.getPaint(), w).build();
        RectF base = textRect(s0, e0);
        if (base == null) return;
        c.save();
        c.translate(r.centerX() - (base.width() / 2f), r.centerY() - sl.getHeight() / 2f);
        sl.draw(c);
        c.restore();
    }

    void draw(Canvas c) {
        if (v.getLayout() == null) return;
        int accent = Pal.accent;
        List<Anchor> all = anchors();
        for (Anchor a : all) { // pencil strokes sit under everything else
            if (a.kind != K_INK) continue;
            PencilFx.ink(c, inkPx(a), a.style, d, a.id);
        }
        for (Anchor a : all) { // shapes next
            if (a.kind == K_INK) continue;
            RectF r = rectOf(a);
            if (r == null) continue;
            line.setPathEffect(null);
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeCap(Paint.Cap.ROUND);
            line.setStrokeWidth(2f * d);
            line.setColor(accent);
            if (a == movingShape && a.ftext == null) ghost(c, a, r);
            if (a.ftext != null) { // a floating shape carries its own words
                RectF fr = floatRect(a);
                if (fr != null) {
                    c.save();
                    c.translate(fr.left, fr.top);
                    floatLayout(a).draw(c);
                    c.restore();
                }
            }
            switch (a.kind) {
                case K_BOX:
                    c.drawPath(PencilFx.superellipse(r), line);
                    break;
                case K_SQUIRCLE:
                    c.drawPath(PencilFx.squircle(r), line);
                    break;
                case K_CIRCLE: {
                    RectF tight = new RectF(r);
                    tight.inset(10 * d, 4 * d); // the pencil circle adds its own margin
                    PencilFx.circle(c, tight, a.style, a.id, d);
                    break;
                }
                case K_SCRIBBLE:
                    PencilFx.scribble(c, r, a.style, a.id, d);
                    break;
                default:
                    break;
            }
            if (mode == MODE_ARROW || selected != null || endDrag != 0) dots(c, r, accent);
        }
        if (mode == MODE_ARROW || selected != null || endDrag != 0) { // words that carry an arrow end show their points too
            for (Anchor a : all) {
                if (a.kind != K_WORD) continue;
                RectF r = rectOf(a);
                if (r != null) dots(c, r, accent);
            }
        }
        for (Anchor a : all) {
            RectF r = rectOf(a);
            if (r == null) continue;
            for (Out o : a.out) {
                Anchor t = byId(o.to);
                RectF tr = t == null ? null : rectOf(t);
                if (tr == null) continue;
                Path p = new Path();
                float[] k = route(r, tr, o, p);
                line.setStyle(Paint.Style.STROKE);
                line.setStrokeWidth(2f * d);
                line.setColor(o.color);
                line.setStrokeCap(Paint.Cap.ROUND);
                line.setPathEffect(new CornerPathEffect(8 * d));
                c.drawPath(p, line);
                line.setPathEffect(null);
                head(c, p, o.color);
                boolean sel = o == selected;
                fill.setStyle(Paint.Style.FILL);
                fill.setColor(sel ? o.color : (o.color & 0x00FFFFFF) | 0xB0000000);
                c.drawCircle(k[0], k[1], (sel ? 8 : 5) * d, fill);
                if (sel) {
                    fill.setColor(0xFFFFFFFF);
                    c.drawCircle(k[0], k[1], 3 * d, fill);
                    drawX(c, k[0], k[1] - 26 * d, o.color);
                    drawColorDot(c, k[0] + 26 * d, k[1], o.color);
                    if (endDrag == 0) { // the two ends: drag one to another point, or onto another shape or word
                        endHandle(c, k[4], k[5], o.color);
                        endHandle(c, k[2], k[3], o.color);
                    }
                }
            }
        }
        if (endDrag != 0 && endOut != null) { // an arrow end being carried to a new point
            Anchor t = byId(endOut.to);
            RectF sr = rectOf(endFrom), tr = t == null ? null : rectOf(t);
            if (sr != null && tr != null) {
                float[] k = route(sr, tr, endOut, new Path());
                float fx = endDrag == 3 ? k[2] : k[4], fy = endDrag == 3 ? k[3] : k[5];
                line.setStyle(Paint.Style.STROKE);
                line.setStrokeWidth(2f * d);
                line.setColor(endOut.color);
                line.setPathEffect(new android.graphics.DashPathEffect(new float[]{8 * d, 6 * d}, 0));
                c.drawLine(fx, fy, dragX, dragY, line);
                line.setPathEffect(null);
                endHandle(c, dragX, dragY, endOut.color);
            }
        }
        if (mode == MODE_INK && !inkErase && stroke.size() > 1) {
            float[] xy = new float[stroke.size() * 2];
            for (int i = 0; i < stroke.size(); i++) {
                xy[i * 2] = stroke.get(i)[0];
                xy[i * 2 + 1] = stroke.get(i)[1];
            }
            PencilFx.ink(c, xy, inkPencil, d, 1);
        } else if ((mode == MODE_BOX || mode == MODE_SQUIRCLE || mode == MODE_CIRCLE || mode == MODE_SCRIBBLE) && stroke.size() > 1) {
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeWidth(2f * d);
            line.setColor(accent);
            line.setPathEffect(null);
            Path p = new Path();
            p.moveTo(stroke.get(0)[0], stroke.get(0)[1]);
            for (int i = 1; i < stroke.size(); i++) p.lineTo(stroke.get(i)[0], stroke.get(i)[1]);
            c.drawPath(p, line);
        } else if (mode == MODE_ARROW && (dragFrom != null || dragWord != null)) {
            RectF s = dragFrom != null ? rectOf(dragFrom) : textRect(dragWord[0], dragWord[1]);
            if (s != null) {
                line.setStyle(Paint.Style.STROKE);
                line.setStrokeWidth(2f * d);
                line.setColor(accent);
                line.setPathEffect(new android.graphics.DashPathEffect(new float[]{8 * d, 6 * d}, 0));
                float[] from = pt(s, nearestSide(s, downX, downY));
                c.drawLine(from[0], from[1], dragX, dragY, line);
                line.setPathEffect(null);
            }
        }
        if (mode != MODE_OFF) { // a faint frame: a drawing tool is on
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeWidth(3f * d);
            line.setColor((accent & 0x00FFFFFF) | 0x66000000);
            c.drawRect(2 * d, v.getScrollY() + 2 * d, v.getWidth() - 2 * d, v.getScrollY() + v.getHeight() - 2 * d, line);
        }
    }

    private void head(Canvas c, Path p, int color) {
        android.graphics.PathMeasure pm = new android.graphics.PathMeasure(p, false);
        float len = pm.getLength();
        if (len < 6 * d) return;
        float[] pos = new float[2], tan = new float[2];
        pm.getPosTan(len, pos, tan);
        float ux = tan[0], uy = tan[1], sz = 9 * d;
        Path h = new Path();
        h.moveTo(pos[0], pos[1]);
        h.lineTo(pos[0] - ux * sz - uy * sz * 0.5f, pos[1] - uy * sz + ux * sz * 0.5f);
        h.lineTo(pos[0] - ux * sz + uy * sz * 0.5f, pos[1] - uy * sz - ux * sz * 0.5f);
        h.close();
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(color);
        c.drawPath(h, fill);
    }

    /** The four points an arrow can join, as small hollow rings. */
    private void dots(Canvas c, RectF r, int accent) {
        for (int sd = 0; sd < 4; sd++) {
            float[] p = pt(r, sd);
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(0xFFFFFFFF);
            c.drawCircle(p[0], p[1], 4.5f * d, fill);
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeWidth(1.6f * d);
            line.setColor(accent);
            c.drawCircle(p[0], p[1], 4.5f * d, line);
        }
    }

    private void endHandle(Canvas c, float x, float y, int color) {
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(0xFFFFFFFF);
        c.drawCircle(x, y, 8 * d, fill);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(2.4f * d);
        line.setColor(color);
        c.drawCircle(x, y, 8 * d, line);
        fill.setColor(color);
        c.drawCircle(x, y, 3 * d, fill);
    }

    private void drawX(Canvas c, float x, float y, int color) {
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(0xFFFFFFFF);
        c.drawCircle(x, y, 11 * d, fill);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(2f * d);
        line.setColor(color);
        c.drawCircle(x, y, 11 * d, line);
        c.drawLine(x - 4 * d, y - 4 * d, x + 4 * d, y + 4 * d, line);
        c.drawLine(x - 4 * d, y + 4 * d, x + 4 * d, y - 4 * d, line);
    }

    private void drawColorDot(Canvas c, float x, float y, int color) {
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(0xFFFFFFFF);
        c.drawCircle(x, y, 11 * d, fill);
        fill.setColor(color);
        c.drawCircle(x, y, 7 * d, fill);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(1.5f * d);
        line.setColor(0x33000000);
        c.drawCircle(x, y, 11 * d, line);
    }

    // ------------------------------------------------------------ touch

    /** True when the touch belongs to the drawing layer (the editor then skips its own handling). */
    boolean touch(MotionEvent e) {
        float x = e.getX() + v.getScrollX(), y = e.getY() + v.getScrollY();
        int act = e.getActionMasked();
        if (movingShape != null) { // a shape that was held is following the finger
            if (act == MotionEvent.ACTION_MOVE) {
                moveShapeTo(movingShape, shapeOx0 + (x - downX) / d, shapeOy0 + (y - downY) / d);
                v.invalidate();
            } else if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) {
                Anchor dropped = movingShape;
                movingShape = null;
                if (act == MotionEvent.ACTION_UP) dropShape(dropped);
                else {
                    dropped.ox = shapeOx0;
                    dropped.oy = shapeOy0;
                }
                v.invalidate();
                notifyChanged();
            }
            return true;
        }
        if (act == MotionEvent.ACTION_DOWN) {
            downX = x;
            downY = y;
            moving = null;
            tapCandidate = false;
            cancelHold();
            Object[] hit = handleAt(x, y);
            if (hit != null) {
                Out o = (Out) hit[0];
                int part = (Integer) hit[2];
                if (part == 1) {
                    removeOut((Anchor) hit[1], o);
                    return true;
                }
                if (part == 2) {
                    o.color = PALETTE[(indexOf(o.color) + 1) % PALETTE.length];
                    v.invalidate();
                    notifyChanged();
                    return true;
                }
                if (part == 3 || part == 4) {
                    endDrag = part;
                    endOut = o;
                    endFrom = (Anchor) hit[1];
                    dragX = x;
                    dragY = y;
                    lock();
                    v.invalidate();
                    return true;
                }
                moving = o;
                movingFrom = (Anchor) hit[1];
                tapCandidate = true;
                lock();
                return true;
            }
            if (mode == MODE_OFF) {
                if (selected != null) {
                    selected = null;
                    v.invalidate();
                }
                armHold(x, y);
                tapFloat = floatAt(x, y);
                return tapFloat != null; // a floating shape is ours: a tap edits it, a hold carries it
            }
            lock();
            if (mode == MODE_ARROW) {
                dragFrom = anchorAt(x, y);
                dragWord = dragFrom == null ? wordAt(x, y) : null;
                dragX = x;
                dragY = y;
            } else if (mode == MODE_INK && inkErase) {
                eraseAt(x, y);
            } else {
                stroke.clear();
                stroke.add(new float[]{x, y});
            }
            v.invalidate();
            return true;
        }
        if (endDrag != 0) {
            if (act == MotionEvent.ACTION_MOVE) {
                dragX = x;
                dragY = y;
                v.invalidate();
            } else if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) {
                if (act == MotionEvent.ACTION_UP) dropEnd(x, y);
                endDrag = 0;
                endOut = null;
                endFrom = null;
                v.invalidate();
                notifyChanged();
            }
            return true;
        }
        if (tapFloat != null && mode == MODE_OFF) {
            if (Math.abs(x - downX) > 10 * d || Math.abs(y - downY) > 10 * d) {
                cancelHold();
                tapFloat = null;
                return false;
            }
            if (act == MotionEvent.ACTION_UP) {
                Anchor f = tapFloat;
                tapFloat = null;
                cancelHold();
                if (floatTap != null) floatTap.onFloatTap(f);
            } else if (act == MotionEvent.ACTION_CANCEL) {
                tapFloat = null;
                cancelHold();
            }
            return true;
        }
        if (moving != null) {
            if (act == MotionEvent.ACTION_MOVE) {
                if (Math.abs(x - downX) > 6 * d || Math.abs(y - downY) > 6 * d) tapCandidate = false;
                if (!tapCandidate) setMid(movingFrom, moving, x, y);
                v.invalidate();
            } else if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) {
                if (tapCandidate && act == MotionEvent.ACTION_UP) selected = selected == moving ? null : moving;
                else selected = moving;
                moving = null;
                v.invalidate();
                notifyChanged();
            }
            return true;
        }
        if (mode == MODE_OFF) {
            if (holdShape != null && (Math.abs(x - downX) > 8 * d || Math.abs(y - downY) > 8 * d)) cancelHold();
            if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) cancelHold();
            return false;
        }
        if (mode == MODE_ARROW) {
            if (act == MotionEvent.ACTION_MOVE) {
                dragX = x;
                dragY = y;
                v.invalidate();
            } else if (act == MotionEvent.ACTION_UP) {
                finishArrow(x, y);
                dragFrom = null;
                dragWord = null;
                v.invalidate();
            } else if (act == MotionEvent.ACTION_CANCEL) {
                dragFrom = null;
                dragWord = null;
                v.invalidate();
            }
            return true;
        }
        if (mode == MODE_INK && inkErase) {
            if (act == MotionEvent.ACTION_DOWN || act == MotionEvent.ACTION_MOVE || act == MotionEvent.ACTION_UP) eraseAt(x, y);
            return true;
        }
        if (act == MotionEvent.ACTION_MOVE) {
            if (mode == MODE_INK) {
                float[] last = stroke.get(stroke.size() - 1);
                if (Math.hypot(x - last[0], y - last[1]) < 1.5f * d) return true;
            }
            stroke.add(new float[]{x, y});
            v.invalidate();
        } else if (act == MotionEvent.ACTION_UP) {
            if (mode == MODE_INK) finishInk();
            else finishShape();
            stroke.clear();
            v.invalidate();
        } else if (act == MotionEvent.ACTION_CANCEL) {
            stroke.clear();
            v.invalidate();
        }
        return true;
    }

    /**
     * The shape was dropped. The first time a shape that hugs text is moved, its words leave the text and go with it:
     * from then on it floats freely, anywhere on the note (left, right, between lines), carrying its own words.
     * It is pinned to the character under where it was dropped, so it scrolls with the note. Nothing around it is
     * touched: neighbouring shapes and styles keep exactly what they had.
     */
    private void dropShape(Anchor a) {
        if (a.kind == K_INK) return; // a stroke just stays where it was put down (its offset already moved it)
        if (Math.abs(a.ox - shapeOx0) < 2 && Math.abs(a.oy - shapeOy0) < 2) {
            a.ox = shapeOx0;
            a.oy = shapeOy0;
            return;
        }
        Editable e = text();
        Layout l = v.getLayout();
        RectF r = a.ftext != null ? floatRect(a) : textRect(e.getSpanStart(a), e.getSpanEnd(a));
        if (l == null || r == null) return;
        r.offset(a.ftext != null ? 0 : a.ox * d, a.ftext != null ? 0 : a.oy * d); // where its words now sit
        if (a.ftext == null) {
            int s0 = e.getSpanStart(a), e0 = e.getSpanEnd(a);
            if (s0 < 0) return;
            for (int i = s0; i < e0; i++) {
                if (e.charAt(i) == RichText.TABLE_CHAR) { // a table never travels: put the shape back
                    a.ox = shapeOx0;
                    a.oy = shapeOy0;
                    return;
                }
            }
            android.text.SpannableStringBuilder seg = new android.text.SpannableStringBuilder(e.subSequence(s0, e0));
            for (Object o : seg.getSpans(0, seg.length(), Object.class)) { // shapes, ticks, blocks stay out of it
                if (o instanceof Anchor || o instanceof RichText.Tbl || o instanceof DiagramSpan
                        || o.getClass().getSimpleName().equals("CircleSpan")) seg.removeSpan(o);
            }
            a.frich = seg; // the words keep their own fonts and styles
            a.ftext = seg.toString();
            a.fl = null;
            int ds = s0, de = e0;
            boolean lineStart = ds == 0 || e.charAt(ds - 1) == '\n';
            boolean lineEnd = de >= e.length() || e.charAt(de) == '\n';
            if (lineStart && lineEnd && de < e.length()) de++; // it was the whole line: the empty line goes too
            else if (de < e.length() && e.charAt(de) == ' ') de++;
            else if (ds > 0 && e.charAt(ds - 1) == ' ') ds--;
            e.removeSpan(a);
            e.delete(ds, de);
        } else {
            e.removeSpan(a);
        }
        if (e.length() == 0) e.insert(0, "\n");
        l = v.getLayout();
        int line = l.getLineForVertical((int) Math.max(0, r.top - v.getTotalPaddingTop()));
        int off = l.getLineStart(line); // pinned to the start of the line it sits on
        off = Math.max(0, Math.min(off, e.length() - 1));
        if (e.charAt(off) == RichText.TABLE_CHAR && off + 1 < e.length()) off++;
        e.setSpan(a, off, off, Spanned.SPAN_MARK_MARK);
        float[] o = inkOrigin(a);
        if (o == null) return;
        a.ox = (r.left - o[0]) / d;
        a.oy = (r.top - o[1]) / d;
    }

    /**
     * Moves a carried shape toward (ox, oy) dp: never past the page's edges, and never into another shape (it keeps
     * a 12 dp gap and slides along the other one instead).
     */
    private void moveShapeTo(Anchor a, float ox, float oy) {
        float px = a.ox, py = a.oy;
        a.ox = ox;
        a.oy = oy;
        RectF r = rectOf(a);
        if (r == null) {
            a.ox = px;
            a.oy = py;
            return;
        }
        float lo = 4 * d, hi = v.getWidth() - 4 * d, top = 4 * d;
        if (r.width() >= hi - lo) a.ox += (lo - r.left) / d;
        else if (r.left < lo) a.ox += (lo - r.left) / d;
        else if (r.right > hi) a.ox -= (r.right - hi) / d;
        if (r.top < top) a.oy += (top - r.top) / d;
        if (a.kind == K_INK) return;
        float cx = a.ox, cy = a.oy;
        if (!overlaps(a)) return;
        a.ox = cx;
        a.oy = py; // slide sideways only
        if (!overlaps(a)) return;
        a.ox = px;
        a.oy = cy; // or up / down only
        if (!overlaps(a)) return;
        a.ox = px;
        a.oy = py; // blocked: stay
    }

    private boolean overlaps(Anchor a) {
        RectF r = rectOf(a);
        if (r == null) return false;
        r.inset(-12 * d, -12 * d);
        for (Anchor o : anchors()) {
            if (o == a || o.kind == K_WORD || o.kind == K_INK || o.kind == K_SCRIBBLE) continue;
            RectF q = rectOf(o);
            if (q != null && RectF.intersects(r, q)) return true;
        }
        return false;
    }

    /** A floating shape under the finger, or null. */
    private Anchor floatAt(float x, float y) {
        for (Anchor a : anchors()) {
            if (a.ftext == null) continue;
            RectF r = rectOf(a);
            if (r != null && r.contains(x, y)) return a;
        }
        return null;
    }

    /** Edits made from the editor's dialog. text null = delete the shape; back = put the words back into the text. */
    void editFloat(Anchor a, String text, boolean back) {
        Editable e = text();
        if (e.getSpanStart(a) < 0) return;
        if (text == null) {
            e.removeSpan(a);
        } else if (back) {
            int at = e.getSpanStart(a);
            CharSequence w = a.frich != null && text.equals(a.ftext) ? a.frich : (text.isEmpty() ? " " : text);
            e.removeSpan(a);
            e.insert(at, "\n");
            e.insert(at, w); // styled words go back styled
            int len = w.length();
            a.ftext = null;
            a.frich = null;
            a.fl = null;
            a.ox = 0;
            a.oy = 0;
            e.setSpan(a, at, at + len, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else {
            if (!text.equals(a.ftext)) a.frich = null; // retyped: plain words now
            a.ftext = text.isEmpty() ? " " : text;
            a.fl = null;
        }
        v.invalidate();
        notifyChanged();
    }

    /** An arrow end was let go: on its own shape it takes that point; on another shape or word it moves there. */
    private void dropEnd(float x, float y) {
        Out o = endOut;
        Anchor from = endFrom;
        if (o == null || from == null) return;
        Anchor to = byId(o.to);
        Anchor hit = anchorAt(x, y);
        if (hit == null) {
            int[] w = wordAt(x, y);
            if (w != null) {
                for (Anchor ex : text().getSpans(w[0], w[1], Anchor.class)) {
                    if (ex.kind == K_WORD && text().getSpanStart(ex) == w[0] && text().getSpanEnd(ex) == w[1]) hit = ex;
                }
                if (hit == null) hit = make(w[0], w[1], K_WORD, 0);
            }
        }
        if (hit == null) return;
        RectF hr = rectOf(hit);
        int side = hr == null ? -1 : nearestSide(hr, x, y);
        o.wx = Float.NaN; // a reconnected arrow finds its own way again
        o.wy = Float.NaN;
        if (endDrag == 3) { // the arrow's head
            if (hit == from) return;
            if (hit != to) o.to = hit.id;
            o.ts = side;
        } else { // its tail
            if (hit == to) return;
            if (hit != from) {
                from.out.remove(o);
                hit.out.add(o);
                selected = o;
            }
            o.fs = side;
        }
    }

    /** Hold a shape for a moment and it picks up: drag to move it anywhere. */
    private void armHold(final float x, final float y) {
        Anchor best = null;
        float bestA = Float.MAX_VALUE;
        for (Anchor a : anchors()) {
            if (a.kind == K_WORD) continue;
            RectF r = rectOf(a);
            if (r == null) continue;
            r.inset(-6 * d, -6 * d);
            if (r.contains(x, y) && r.width() * r.height() < bestA) {
                best = a;
                bestA = r.width() * r.height();
            }
        }
        if (best == null) return;
        holdShape = best;
        holdTimer = () -> {
            if (holdShape == null) return;
            movingShape = holdShape;
            holdShape = null;
            tapFloat = null;
            shapeOx0 = movingShape.ox;
            shapeOy0 = movingShape.oy;
            Hap.confirm(v);
            lock();
            v.abortPress();
            v.invalidate();
        };
        v.postDelayed(holdTimer, 380);
    }

    private void cancelHold() {
        if (holdTimer != null) v.removeCallbacks(holdTimer);
        holdTimer = null;
        holdShape = null;
    }

    private void lock() {
        if (v.getParent() != null) v.getParent().requestDisallowInterceptTouchEvent(true);
    }

    private int indexOf(int c) {
        for (int i = 0; i < PALETTE.length; i++) if (PALETTE[i] == c) return i;
        return -1;
    }

    private Object[] handleAt(float x, float y) {
        for (Anchor a : anchors()) {
            RectF r = rectOf(a);
            if (r == null) continue;
            for (Out o : a.out) {
                Anchor t = byId(o.to);
                RectF tr = t == null ? null : rectOf(t);
                if (tr == null) continue;
                float[] k = route(r, tr, o, new Path());
                if (o == selected) {
                    if (dist(x, y, k[2], k[3]) < 18 * d) return new Object[]{o, a, 3};
                    if (dist(x, y, k[4], k[5]) < 18 * d) return new Object[]{o, a, 4};
                    if (dist(x, y, k[0], k[1] - 26 * d) < 18 * d) return new Object[]{o, a, 1};
                    if (dist(x, y, k[0] + 26 * d, k[1]) < 18 * d) return new Object[]{o, a, 2};
                }
                if (dist(x, y, k[0], k[1]) < 20 * d) return new Object[]{o, a, 0};
            }
        }
        return null;
    }

    private static float dist(float x1, float y1, float x2, float y2) {
        return (float) Math.hypot(x1 - x2, y1 - y2);
    }

    /** Dragging the middle handle: the line is routed through the finger, any way it likes (kept within the page). */
    private void setMid(Anchor from, Out o, float x, float y) {
        Anchor t = byId(o.to);
        RectF s = rectOf(from), tr = t == null ? null : rectOf(t);
        if (s == null || tr == null) return;
        if (Float.isNaN(o.wx)) { // first drag: keep the points it uses now, so only the middle moves
            route(s, tr, o, new Path());
            o.fs = rFs;
            o.ts = rTs;
        }
        float cx = Math.max(4 * d, Math.min(v.getWidth() - 4 * d, x));
        float cy = Math.max(4 * d, y);
        o.wx = (cx - s.left) / d;
        o.wy = (cy - s.top) / d;
    }

    private void removeOut(Anchor a, Out o) {
        a.out.remove(o);
        selected = null;
        v.invalidate();
        notifyChanged();
    }

    private Anchor anchorAt(float x, float y) {
        Anchor best = null;
        float bestA = Float.MAX_VALUE;
        for (Anchor a : anchors()) {
            if (a.kind == K_INK) continue;
            RectF r = rectOf(a);
            if (r == null) continue;
            r.inset(-8 * d, -6 * d);
            if (r.contains(x, y) && r.width() * r.height() < bestA) {
                best = a;
                bestA = r.width() * r.height();
            }
        }
        return best;
    }

    private int[] wordAt(float x, float y) {
        Layout l = v.getLayout();
        if (l == null) return null;
        int line = l.getLineForVertical((int) (y - v.getTotalPaddingTop()));
        int off = l.getOffsetForHorizontal(line, x - v.getTotalPaddingLeft());
        Editable e = text();
        int a = off, b = off;
        while (a > 0 && !isGap(e.charAt(a - 1))) a--;
        while (b < e.length() && !isGap(e.charAt(b))) b++;
        if (b <= a) return null;
        return new int[]{a, b};
    }

    private static boolean isGap(char c) {
        return Character.isWhitespace(c) || c == RichText.TABLE_CHAR;
    }

    /** The finger drew round some text: put the chosen shape on the words it covers. */
    private void finishShape() {
        if (stroke.size() < 6) return;
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = 0, maxY = 0;
        for (float[] p : stroke) {
            minX = Math.min(minX, p[0]);
            minY = Math.min(minY, p[1]);
            maxX = Math.max(maxX, p[0]);
            maxY = Math.max(maxY, p[1]);
        }
        if (maxX - minX < 24 * d || maxY - minY < 8 * d) return;
        Layout l = v.getLayout();
        if (l == null) return;
        float oy = v.getTotalPaddingTop(), ox = v.getTotalPaddingLeft();
        int l1 = l.getLineForVertical((int) (minY - oy)), l2 = l.getLineForVertical((int) (maxY - oy));
        while (l1 < l2 && (l.getLineBottom(l1) + oy - minY) < (l.getLineBottom(l1) - l.getLineTop(l1)) / 3f) l1++;
        while (l2 > l1 && (maxY - (l.getLineTop(l2) + oy)) < (l.getLineBottom(l2) - l.getLineTop(l2)) / 3f) l2--;
        int a = l.getOffsetForHorizontal(l1, minX - ox);
        int b = l.getOffsetForHorizontal(l2, maxX - ox);
        Editable e = text();
        while (a < b && isGap(e.charAt(a))) a++;
        while (b > a && isGap(e.charAt(b - 1))) b--;
        if (b <= a) return;
        int kind = mode == MODE_BOX ? K_BOX : mode == MODE_SQUIRCLE ? K_SQUIRCLE : mode == MODE_CIRCLE ? K_CIRCLE : K_SCRIBBLE;
        int style = mode == MODE_CIRCLE ? Prefs.circleStyle(v.getContext())
                : mode == MODE_SCRIBBLE ? Prefs.scribbleStyle(v.getContext()) : 0;
        make(a, b, kind, style);
        Hap.tick(v);
        notifyChanged();
    }

    /** A finished freehand stroke becomes an anchor on the character it started over, so it follows the text. */
    private void finishInk() {
        if (stroke.isEmpty()) return;
        Layout l = v.getLayout();
        if (l == null) return;
        Editable e = text();
        if (e.length() == 0) e.insert(0, "\n");
        float[] f = stroke.get(0);
        int line = l.getLineForVertical((int) Math.max(0, f[1] - v.getTotalPaddingTop()));
        int off = l.getOffsetForHorizontal(line, f[0] - v.getTotalPaddingLeft());
        off = Math.max(0, Math.min(off, e.length() - 1));
        if (e.charAt(off) == RichText.TABLE_CHAR) off = off > 0 ? off - 1 : Math.min(e.length() - 1, off + 1);
        if (e.charAt(off) == RichText.TABLE_CHAR) return;
        Anchor tmp = new Anchor();
        e.setSpan(tmp, off, off + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        float[] o = inkOrigin(tmp);
        e.removeSpan(tmp);
        if (o == null) return;
        List<Float> keep = new ArrayList<>();
        float lx = Float.MAX_VALUE, ly = 0;
        for (float[] q : stroke) {
            if (Math.hypot(q[0] - lx, q[1] - ly) < 1.4f * d && lx != Float.MAX_VALUE) continue;
            keep.add(Math.round((q[0] - o[0]) / d * 10f) / 10f);
            keep.add(Math.round((q[1] - o[1]) / d * 10f) / 10f);
            lx = q[0];
            ly = q[1];
        }
        if (keep.size() < 4) { // a tap: a dot
            keep.add(keep.get(0) + 0.2f);
            keep.add(keep.get(1) + 0.2f);
        }
        float[] pts = new float[keep.size()];
        for (int i = 0; i < pts.length; i++) pts[i] = keep.get(i);
        Anchor n = new Anchor();
        n.id = nextId();
        n.kind = K_INK;
        n.style = inkPencil;
        n.pts = pts;
        e.setSpan(n, off, off, Spanned.SPAN_MARK_MARK); // a mark at that spot: the stroke survives edits round it
        Hap.tick(v);
        notifyChanged();
    }

    /** Rubs out every stroke the finger passes over. */
    private void eraseAt(float x, float y) {
        boolean any = false;
        for (Anchor a : anchors()) {
            if (a.kind != K_INK) continue;
            float[] xy = inkPx(a);
            if (xy == null) continue;
            for (int i = 0; i + 1 < xy.length; i += 2) {
                if (Math.hypot(xy[i] - x, xy[i + 1] - y) < 12 * d) {
                    text().removeSpan(a);
                    any = true;
                    break;
                }
            }
        }
        if (any) {
            Hap.tick(v);
            v.invalidate();
            notifyChanged();
        }
    }

    private void finishArrow(float x, float y) {
        Anchor to = anchorAt(x, y);
        int[] tw = to == null ? wordAt(x, y) : null;
        if (dragFrom == null && dragWord == null) return;
        if (to == null && tw == null) return;
        Editable e = text();
        int sA = dragFrom != null ? e.getSpanStart(dragFrom) : dragWord[0];
        int sB = dragFrom != null ? e.getSpanEnd(dragFrom) : dragWord[1];
        int tA = to != null ? e.getSpanStart(to) : tw[0];
        int tB = to != null ? e.getSpanEnd(to) : tw[1];
        if (sA == tA && sB == tB) return;
        Anchor from = dragFrom != null ? dragFrom : make(sA, sB, K_WORD, 0);
        Anchor target = to != null ? to : make(tA, tB, K_WORD, 0);
        Out o = new Out();
        o.to = target.id;
        RectF fr = rectOf(from), tr = rectOf(target);
        if (fr != null && tr != null) {
            o.fs = snapSide(fr, downX, downY);
            o.ts = snapSide(tr, x, y);
        }
        from.out.add(o);
        selected = o;
        Hap.tick(v);
        notifyChanged();
        v.invalidate();
    }

    private Runnable changed;

    void onChanged(Runnable r) {
        changed = r;
    }

    private void notifyChanged() {
        if (changed != null) changed.run();
    }

    // ------------------------------------------------------------ persistence (kept inside the note's JSON)

    static JSONArray toJson(Spanned sp, int end) throws JSONException {
        JSONArray arr = new JSONArray();
        for (Anchor a : sp.getSpans(0, sp.length(), Anchor.class)) {
            int s = sp.getSpanStart(a), e = Math.min(end, sp.getSpanEnd(a));
            if (e < s || s > end || (e == s && !pinned(a))) continue;
            JSONObject o = new JSONObject();
            o.put("i", a.id).put("s", s).put("e", e).put("k", a.kind).put("t", a.style);
            if (a.ox != 0 || a.oy != 0) o.put("x", (double) a.ox).put("y", (double) a.oy);
            if (a.ftext != null) o.put("f", a.ftext);
            if (a.frich != null && a.frich.toString().equals(a.ftext)) o.put("fr", RichText.toJson(a.frich));
            if (a.kind == K_INK && a.pts != null) {
                JSONArray pa = new JSONArray();
                for (float q : a.pts) pa.put((double) q);
                o.put("p", pa);
            }
            JSONArray outs = new JSONArray();
            for (Out t : a.out) {
                JSONArray oa = new JSONArray().put(t.to).put((double) t.mid).put(t.color).put(t.fs).put(t.ts);
                if (!Float.isNaN(t.wx)) oa.put((double) t.wx).put((double) t.wy);
                outs.put(oa);
            }
            if (outs.length() > 0) o.put("o", outs);
            arr.put(o);
        }
        return arr;
    }

    static void fromJson(JSONArray arr, Editable out, int length) {
        if (arr == null) return;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            int s = Math.max(0, Math.min(length, o.optInt("s"))), e = Math.min(length, o.optInt("e"));
            boolean pin = o.optInt("k") == K_INK || o.has("f");
            if (e < s || (e == s && !pin)) continue;
            Anchor a = new Anchor();
            a.id = o.optInt("i");
            a.kind = o.has("k") ? o.optInt("k") : (o.optInt("b") == 1 ? K_BOX : K_WORD); // older notes: "b" = box
            a.style = o.optInt("t");
            a.ox = (float) o.optDouble("x", 0);
            a.oy = (float) o.optDouble("y", 0);
            if (o.has("f")) a.ftext = o.optString("f");
            if (o.has("fr")) {
                android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder();
                RichText.fromJson(o.optString("fr"), sb);
                if (sb.toString().equals(a.ftext)) a.frich = sb;
            }
            JSONArray pa = o.optJSONArray("p");
            if (pa != null && pa.length() >= 4) {
                a.pts = new float[pa.length()];
                for (int k = 0; k < a.pts.length; k++) a.pts[k] = (float) pa.optDouble(k, 0);
            }
            JSONArray outs = o.optJSONArray("o");
            if (outs != null) {
                for (int k = 0; k < outs.length(); k++) {
                    JSONArray t = outs.optJSONArray(k);
                    if (t == null) continue;
                    Out x = new Out();
                    x.to = t.optInt(0);
                    x.mid = (float) t.optDouble(1, 0.5);
                    x.color = t.optInt(2, 0xFFD2452B);
                    x.fs = t.optInt(3, -1);
                    x.ts = t.optInt(4, -1);
                    if (t.length() > 6) {
                        x.wx = (float) t.optDouble(5, Double.NaN);
                        x.wy = (float) t.optDouble(6, Double.NaN);
                    }
                    a.out.add(x);
                }
            }
            if (e == s) out.setSpan(a, s, s, Spanned.SPAN_MARK_MARK);
            else out.setSpan(a, s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    /** Gives pasted anchors fresh ids so they cannot clash with the ones already in the note. */
    static void remap(Editable pasted, Editable into) {
        int base = 0;
        for (Anchor a : into.getSpans(0, into.length(), Anchor.class)) base = Math.max(base, a.id);
        Anchor[] ps = pasted.getSpans(0, pasted.length(), Anchor.class);
        java.util.HashMap<Integer, Integer> map = new java.util.HashMap<>();
        for (Anchor a : ps) map.put(a.id, ++base);
        for (Anchor a : ps) {
            a.id = map.get(a.id);
            for (java.util.Iterator<Out> it = a.out.iterator(); it.hasNext(); ) {
                Out o = it.next();
                Integer n = map.get(o.to);
                if (n == null) it.remove();
                else o.to = n;
            }
        }
    }
}
