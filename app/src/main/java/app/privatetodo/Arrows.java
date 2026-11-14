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
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Connectors for notes: a freehand squircle box drawn round some text, and arrows from one piece of text (or box) to
 * another, with a middle segment you can drag. Everything is anchored to the text with inert spans, so it follows the
 * words when the note is edited. Stored in the note's own JSON ("an" anchors, nothing leaves the phone).
 */
final class Arrows {
    /** A piece of text an arrow can start or end on; box = drawn with a squircle round it. */
    static final class Anchor {
        int id;
        boolean box;
        final List<Out> out = new ArrayList<>();
    }

    /** An arrow leaving an anchor. mid is where the middle segment sits (0..1 between the two ends). */
    static final class Out {
        int to;
        float mid = 0.5f;
        int color = 0xFFD2452B;
    }

    static final int MODE_OFF = 0, MODE_BOX = 1, MODE_ARROW = 2;

    private final TextView v;
    private final float d;
    int mode = MODE_OFF;
    private Out selected;
    private Anchor selectedFrom;
    private final List<float[]> stroke = new ArrayList<>();
    private Anchor dragFrom;
    private int[] dragWord;
    private float dragX, dragY, downX, downY;
    private Out moving;
    private Anchor movingFrom;
    private boolean tapCandidate;
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);

    Arrows(TextView v) {
        this.v = v;
        d = v.getResources().getDisplayMetrics().density;
    }

    // ------------------------------------------------------------ model

    private Editable text() {
        return (Editable) v.getText();
    }

    List<Anchor> anchors() {
        Editable e = text();
        List<Anchor> l = new ArrayList<>();
        for (Anchor a : e.getSpans(0, e.length(), Anchor.class)) {
            if (e.getSpanEnd(a) > e.getSpanStart(a)) l.add(a);
        }
        return l;
    }

    boolean any() {
        return mode != MODE_OFF || text().getSpans(0, text().length(), Anchor.class).length > 0;
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

    private Anchor make(int a, int b, boolean box) {
        Anchor n = new Anchor();
        n.id = nextId();
        n.box = box;
        text().setSpan(n, a, b, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return n;
    }

    void clearAll() {
        Editable e = text();
        for (Anchor a : e.getSpans(0, e.length(), Anchor.class)) e.removeSpan(a);
        selected = null;
        v.invalidate();
    }

    // ------------------------------------------------------------ geometry (view coordinates, scroll not applied)

    private RectF rectOf(int a, int b) {
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

    private RectF rectOf(Anchor a) {
        Editable e = text();
        return rectOf(e.getSpanStart(a), e.getSpanEnd(a));
    }

    private float[] route(RectF s, RectF t, float mid, Path out) {
        // returns {midX, midY} (the handle) and fills out with the polyline
        float sx, sy, tx, ty, hx, hy;
        float dx = t.centerX() - s.centerX(), dy = t.centerY() - s.centerY();
        out.reset();
        if (Math.abs(dx) >= Math.abs(dy) * 0.8f && (t.left > s.right || t.right < s.left)) {
            boolean right = dx > 0;
            sx = right ? s.right : s.left;
            sy = s.centerY();
            tx = right ? t.left : t.right;
            ty = t.centerY();
            float mx = sx + (tx - sx) * mid;
            out.moveTo(sx, sy);
            out.lineTo(mx, sy);
            out.lineTo(mx, ty);
            out.lineTo(tx, ty);
            hx = mx;
            hy = (sy + ty) / 2f;
        } else {
            boolean down = dy >= 0;
            sx = s.centerX();
            sy = down ? s.bottom : s.top;
            tx = t.centerX();
            ty = down ? t.top : t.bottom;
            float my = sy + (ty - sy) * mid;
            out.moveTo(sx, sy);
            out.lineTo(sx, my);
            out.lineTo(tx, my);
            out.lineTo(tx, ty);
            hx = (sx + tx) / 2f;
            hy = my;
        }
        return new float[]{hx, hy, tx, ty, sx, sy};
    }

    // ------------------------------------------------------------ drawing

    void draw(Canvas c) {
        if (v.getLayout() == null) return;
        int accent = Pal.accent;
        for (Anchor a : anchors()) {
            RectF r = rectOf(a);
            if (r == null) continue;
            if (a.box) {
                r.inset(-5 * d, -2 * d);
                line.setStyle(Paint.Style.STROKE);
                line.setStrokeWidth(2f * d);
                line.setColor(accent);
                line.setPathEffect(null);
                c.drawPath(squircle(r), line);
            }
            for (Out o : a.out) {
                Anchor t = byId(o.to);
                RectF tr = t == null ? null : rectOf(t);
                if (tr == null) continue;
                RectF s2 = new RectF(r), t2 = new RectF(tr);
                if (a.box) s2.inset(0, 0);
                if (t.box) t2.inset(-5 * d, -2 * d);
                Path p = new Path();
                float[] k = route(s2, t2, o.mid, p);
                line.setStyle(Paint.Style.STROKE);
                line.setStrokeWidth(2f * d);
                line.setColor(o.color);
                line.setStrokeCap(Paint.Cap.ROUND);
                line.setPathEffect(new CornerPathEffect(8 * d));
                c.drawPath(p, line);
                line.setPathEffect(null);
                head(c, p, k[2], k[3], o.color);
                boolean sel = o == selected;
                fill.setStyle(Paint.Style.FILL);
                fill.setColor(sel ? o.color : (o.color & 0x00FFFFFF) | 0xB0000000);
                c.drawCircle(k[0], k[1], (sel ? 8 : 5) * d, fill);
                if (sel) {
                    fill.setColor(0xFFFFFFFF);
                    c.drawCircle(k[0], k[1], 3 * d, fill);
                    drawX(c, k[0], k[1] - 26 * d, o.color);
                    drawPaletteDot(c, k[0] + 26 * d, k[1], o.color);
                }
            }
        }
        // rubber band while drawing
        if (mode == MODE_BOX && stroke.size() > 1) {
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeWidth(2f * d);
            line.setColor(accent);
            line.setStrokeJoin(Paint.Join.ROUND);
            Path p = new Path();
            p.moveTo(stroke.get(0)[0], stroke.get(0)[1]);
            for (int i = 1; i < stroke.size(); i++) p.lineTo(stroke.get(i)[0], stroke.get(i)[1]);
            c.drawPath(p, line);
        } else if (mode == MODE_ARROW && (dragFrom != null || dragWord != null)) {
            RectF s = dragFrom != null ? rectOf(dragFrom) : rectOf(dragWord[0], dragWord[1]);
            if (s != null) {
                line.setStyle(Paint.Style.STROKE);
                line.setStrokeWidth(2f * d);
                line.setColor(accent);
                line.setPathEffect(new android.graphics.DashPathEffect(new float[]{8 * d, 6 * d}, 0));
                c.drawLine(s.centerX(), s.centerY(), dragX, dragY, line);
                line.setPathEffect(null);
            }
        }
        if (mode != MODE_OFF) { // a faint frame so it is clear the tool is on
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeWidth(3f * d);
            line.setColor((accent & 0x00FFFFFF) | 0x66000000);
            c.drawRect(2 * d, v.getScrollY() + 2 * d, v.getWidth() - 2 * d, v.getScrollY() + v.getHeight() - 2 * d, line);
        }
    }

    private void head(Canvas c, Path p, float tx, float ty, int color) {
        android.graphics.PathMeasure pm = new android.graphics.PathMeasure(p, false);
        float len = pm.getLength();
        if (len < 6 * d) return;
        float[] pos = new float[2], tan = new float[2];
        pm.getPosTan(len, pos, tan);
        float ux = tan[0], uy = tan[1];
        float sz = 9 * d;
        Path h = new Path();
        h.moveTo(pos[0], pos[1]);
        h.lineTo(pos[0] - ux * sz - uy * sz * 0.5f, pos[1] - uy * sz + ux * sz * 0.5f);
        h.lineTo(pos[0] - ux * sz + uy * sz * 0.5f, pos[1] - uy * sz - ux * sz * 0.5f);
        h.close();
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(color);
        c.drawPath(h, fill);
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

    private void drawPaletteDot(Canvas c, float x, float y, int color) {
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

    /** A squircle (superellipse, n = 4): rounder than a rounded rectangle, squarer than an oval. */
    static Path squircle(RectF r) {
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

    // ------------------------------------------------------------ touch

    private static final int[] PALETTE = {0xFFD2452B, 0xFF2E7DE0, 0xFF2FA866, 0xFFE9B800, 0xFF8B5CD6, 0xFF444B55};

    /** Returns true when the touch belongs to the connectors (the caller then skips the normal editor handling). */
    boolean touch(MotionEvent e) {
        float x = e.getX() + v.getScrollX(), y = e.getY() + v.getScrollY();
        int act = e.getActionMasked();
        if (act == MotionEvent.ACTION_DOWN) {
            downX = x;
            downY = y;
            moving = null;
            tapCandidate = false;
            // handles of an arrow work with or without a tool on
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
                return false;
            }
            lock();
            if (mode == MODE_BOX) {
                stroke.clear();
                stroke.add(new float[]{x, y});
                v.invalidate();
                return true;
            }
            dragFrom = anchorAt(x, y);
            dragWord = dragFrom == null ? wordAt(x, y) : null;
            dragX = x;
            dragY = y;
            v.invalidate();
            return true;
        }
        if (moving != null) {
            if (act == MotionEvent.ACTION_MOVE) {
                if (Math.abs(x - downX) > 6 * d || Math.abs(y - downY) > 6 * d) tapCandidate = false;
                setMid(movingFrom, moving, x, y);
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
        if (mode == MODE_OFF) return false;
        if (mode == MODE_BOX) {
            if (act == MotionEvent.ACTION_MOVE) {
                stroke.add(new float[]{x, y});
                v.invalidate();
            } else if (act == MotionEvent.ACTION_UP) {
                finishBox();
                stroke.clear();
                v.invalidate();
            } else if (act == MotionEvent.ACTION_CANCEL) {
                stroke.clear();
                v.invalidate();
            }
            return true;
        }
        // arrow tool
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
                RectF s2 = new RectF(r), t2 = new RectF(tr);
                if (a.box) s2.inset(-5 * d, -2 * d);
                if (t.box) t2.inset(-5 * d, -2 * d);
                float[] k = route(s2, t2, o.mid, new Path());
                if (o == selected) {
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

    private void setMid(Anchor from, Out o, float x, float y) {
        Anchor t = byId(o.to);
        RectF s = rectOf(from), tr = t == null ? null : rectOf(t);
        if (s == null || tr == null) return;
        float dx = tr.centerX() - s.centerX(), dy = tr.centerY() - s.centerY();
        boolean horiz = Math.abs(dx) >= Math.abs(dy) * 0.8f && (tr.left > s.right || tr.right < s.left);
        float a = horiz ? (dx > 0 ? s.right : s.left) : (dy >= 0 ? s.bottom : s.top);
        float b = horiz ? (dx > 0 ? tr.left : tr.right) : (dy >= 0 ? tr.top : tr.bottom);
        float p = horiz ? x : y;
        if (Math.abs(b - a) < 1f) return;
        o.mid = Math.max(-0.5f, Math.min(1.5f, (p - a) / (b - a)));
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

    private void finishBox() {
        if (stroke.size() < 6) return;
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = 0, maxY = 0;
        for (float[] p : stroke) {
            minX = Math.min(minX, p[0]);
            minY = Math.min(minY, p[1]);
            maxX = Math.max(maxX, p[0]);
            maxY = Math.max(maxY, p[1]);
        }
        if (maxX - minX < 24 * d || maxY - minY < 12 * d) return;
        Layout l = v.getLayout();
        if (l == null) return;
        float oy = v.getTotalPaddingTop(), ox = v.getTotalPaddingLeft();
        int l1 = l.getLineForVertical((int) (minY - oy)), l2 = l.getLineForVertical((int) (maxY - oy));
        // keep lines that the stroke covers by at least a third
        while (l1 < l2 && (l.getLineBottom(l1) + oy - minY) < (l.getLineBottom(l1) - l.getLineTop(l1)) / 3f) l1++;
        while (l2 > l1 && (maxY - (l.getLineTop(l2) + oy)) < (l.getLineBottom(l2) - l.getLineTop(l2)) / 3f) l2--;
        int a = l.getOffsetForHorizontal(l1, minX - ox);
        int b = l.getOffsetForHorizontal(l2, maxX - ox);
        Editable e = text();
        while (a < b && isGap(e.charAt(a))) a++;
        while (b > a && isGap(e.charAt(b - 1))) b--;
        if (b <= a) return;
        make(a, b, true);
        Hap.tick(v);
        notifyChanged();
    }

    private void finishArrow(float x, float y) {
        Anchor to = anchorAt(x, y);
        int[] tw = to == null ? wordAt(x, y) : null;
        if (dragFrom == null && dragWord == null) return;
        if (to == null && tw == null) return;
        int sA = dragFrom != null ? text().getSpanStart(dragFrom) : dragWord[0];
        int sB = dragFrom != null ? text().getSpanEnd(dragFrom) : dragWord[1];
        int tA = to != null ? text().getSpanStart(to) : tw[0];
        int tB = to != null ? text().getSpanEnd(to) : tw[1];
        if (sA == tA && sB == tB) return;
        Anchor from = dragFrom != null ? dragFrom : make(sA, sB, false);
        Anchor target = to != null ? to : make(tA, tB, false);
        Out o = new Out();
        o.to = target.id;
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
            if (e <= s) continue;
            JSONObject o = new JSONObject();
            o.put("i", a.id).put("s", s).put("e", e).put("b", a.box ? 1 : 0);
            JSONArray outs = new JSONArray();
            for (Out t : a.out) outs.put(new JSONArray().put(t.to).put((double) t.mid).put(t.color));
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
            int s = Math.max(0, o.optInt("s")), e = Math.min(length, o.optInt("e"));
            if (e <= s) continue;
            Anchor a = new Anchor();
            a.id = o.optInt("i");
            a.box = o.optInt("b") == 1;
            JSONArray outs = o.optJSONArray("o");
            if (outs != null) {
                for (int k = 0; k < outs.length(); k++) {
                    JSONArray t = outs.optJSONArray(k);
                    if (t == null) continue;
                    Out x = new Out();
                    x.to = t.optInt(0);
                    x.mid = (float) t.optDouble(1, 0.5);
                    x.color = t.optInt(2, 0xFFD2452B);
                    a.out.add(x);
                }
            }
            out.setSpan(a, s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
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
