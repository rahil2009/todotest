package app.privatetodo;

import android.graphics.Canvas;
import android.graphics.CornerPathEffect;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.PriorityQueue;

/**
 * A diagram that lives in its own block of a note, the way eraser.io / Excalidraw / tldraw work: shapes are free
 * objects on a canvas, each carrying its own words, and arrows are BOUND to a shape and one of its four anchor
 * points. An arrow's ends are never stored: they are worked out from the shapes every time it is drawn, so arrows
 * follow their shapes wherever they are moved.
 * <p>
 * Elbow arrows are routed like Excalidraw's: a sparse grid is built from the shapes' padded edges, and an A* search
 * with a penalty for every bend finds the shortest right-angled path that never enters a shape (including the two it
 * joins). Units are dp on a canvas {@link #W} wide.
 */
final class Diagram {
    static final float W = 360f;      // canvas width in dp
    static final float PAD = 10f;     // clearance arrows keep round every shape
    static final float GAP = 16f;     // shapes never come closer than this to each other
    static final float GRID = 8f;     // moves snap to this

    static final int BOX = 0, SQUIRCLE = 1, ELLIPSE = 2, DIAMOND = 3, TEXT = 4, STICKY = 5;
    static final String[] KIND_NAMES = {"Box", "Squircle", "Ellipse", "Diamond", "Text", "Sticky"};
    static final int TOP = 0, RIGHT = 1, BOTTOM = 2, LEFT = 3;
    static final int[] COLORS = {0xFF5B6270, 0xFF2E7DE0, 0xFF2FA866, 0xFFD9A400, 0xFFF08A24, 0xFFD2452B, 0xFF8B5CD6, 0xFF1F9E9E};
    private static final int[][] DIR = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};

    static final class Node {
        int id, kind, color = COLORS[1];
        float x, y, w = 120, h = 56;
        String text = "";
        // cached text layout
        StaticLayout lay;
        String layFor;
        float layW, layS;

        RectF rect() {
            return new RectF(x, y, x + w, y + h);
        }
    }

    static final class Edge {
        int id, from, to;
        int fs = -1, ts = -1; // chosen anchor sides, -1 = pick the best
        int color = COLORS[0];
        boolean dashed;
        float wx = Float.NaN, wy = Float.NaN; // a point the arrow is dragged through (NaN = none)
        float[] path; // cached route, dp
        int pathVer = -1;
        int usedFs, usedTs;
    }

    final List<Node> nodes = new ArrayList<>();
    final List<Edge> edges = new ArrayList<>();
    int ver; // bumped on every change: routes are recomputed when it moves

    // ------------------------------------------------------------ model

    int nextId() {
        int m = 0;
        for (Node n : nodes) m = Math.max(m, n.id);
        for (Edge e : edges) m = Math.max(m, e.id);
        return m + 1;
    }

    Node node(int id) {
        for (Node n : nodes) if (n.id == id) return n;
        return null;
    }

    void removeNode(Node n) {
        nodes.remove(n);
        for (int i = edges.size() - 1; i >= 0; i--) {
            Edge e = edges.get(i);
            if (e.from == n.id || e.to == n.id) edges.remove(i);
        }
        ver++;
    }

    /** The bottom of everything drawn, in dp. */
    float bottom() {
        float b = 0;
        for (Node n : nodes) b = Math.max(b, n.y + n.h);
        for (Edge e : edges) {
            float[] p = route(e);
            if (p != null) for (int i = 1; i < p.length; i += 2) b = Math.max(b, p[i]);
        }
        return b;
    }

    float top() {
        if (nodes.isEmpty()) return 0;
        float t = Float.MAX_VALUE;
        for (Node n : nodes) t = Math.min(t, n.y);
        for (Edge e : edges) {
            float[] p = route(e);
            if (p != null) for (int i = 1; i < p.length; i += 2) t = Math.min(t, p[i]);
        }
        return Math.max(0, t);
    }

    /** True when r (grown by the gap) would touch any shape other than skip. */
    boolean collides(RectF r, Node skip) {
        for (Node n : nodes) {
            if (n == skip) continue;
            RectF o = n.rect();
            o.inset(-GAP, -GAP);
            if (RectF.intersects(o, r)) return true;
        }
        return false;
    }

    /** A free spot for a new w x h shape near (x, y): tries round the wanted spot, then further down. */
    float[] freeSpot(float x, float y, float w, float h) {
        for (int ring = 0; ring < 60; ring++) {
            float step = ring * GRID * 2;
            float[][] tries = {{x, y + step}, {x + step, y}, {x - step, y}, {x, y - step}};
            for (float[] t : tries) {
                float cx = clampX(snap(t[0]), w), cy = Math.max(0, snap(t[1]));
                if (!collides(new RectF(cx, cy, cx + w, cy + h), null)) return new float[]{cx, cy};
            }
        }
        float b = 0;
        for (Node n : nodes) b = Math.max(b, n.y + n.h);
        return new float[]{clampX(snap(x), w), snap(b + GAP * 2)};
    }

    static float snap(float v) {
        return Math.round(v / GRID) * GRID;
    }

    static float clampX(float x, float w) {
        return Math.max(4, Math.min(W - w - 4, x));
    }

    // ------------------------------------------------------------ persistence

    String toJson() {
        try {
            JSONObject o = new JSONObject();
            o.put("v", 1);
            JSONArray na = new JSONArray();
            for (Node n : nodes) {
                na.put(new JSONObject().put("i", n.id).put("k", n.kind).put("x", (double) n.x).put("y", (double) n.y)
                        .put("w", (double) n.w).put("h", (double) n.h).put("c", n.color).put("t", n.text));
            }
            o.put("n", na);
            JSONArray ea = new JSONArray();
            for (Edge e : edges) {
                JSONObject j = new JSONObject().put("i", e.id).put("f", e.from).put("fs", e.fs).put("t", e.to)
                        .put("ts", e.ts).put("c", e.color).put("d", e.dashed);
                if (!Float.isNaN(e.wx)) j.put("wx", (double) e.wx).put("wy", (double) e.wy);
                ea.put(j);
            }
            o.put("e", ea);
            return o.toString();
        } catch (JSONException ex) {
            return "{}";
        }
    }

    static Diagram fromJson(String s) {
        Diagram d = new Diagram();
        if (s == null || s.isEmpty()) return d;
        try {
            JSONObject o = new JSONObject(s);
            JSONArray na = o.optJSONArray("n");
            if (na != null) {
                for (int i = 0; i < na.length(); i++) {
                    JSONObject j = na.optJSONObject(i);
                    if (j == null) continue;
                    Node n = new Node();
                    n.id = j.optInt("i");
                    n.kind = Math.max(0, Math.min(STICKY, j.optInt("k")));
                    n.x = (float) j.optDouble("x", 0);
                    n.y = (float) j.optDouble("y", 0);
                    n.w = Math.max(24, (float) j.optDouble("w", 120));
                    n.h = Math.max(24, (float) j.optDouble("h", 56));
                    n.color = j.optInt("c", COLORS[1]);
                    n.text = j.optString("t", "");
                    d.nodes.add(n);
                }
            }
            JSONArray ea = o.optJSONArray("e");
            if (ea != null) {
                for (int i = 0; i < ea.length(); i++) {
                    JSONObject j = ea.optJSONObject(i);
                    if (j == null) continue;
                    Edge e = new Edge();
                    e.id = j.optInt("i");
                    e.from = j.optInt("f");
                    e.to = j.optInt("t");
                    e.fs = j.optInt("fs", -1);
                    e.ts = j.optInt("ts", -1);
                    e.color = j.optInt("c", COLORS[0]);
                    e.dashed = j.optBoolean("d", false);
                    if (j.has("wx")) {
                        e.wx = (float) j.optDouble("wx");
                        e.wy = (float) j.optDouble("wy");
                    }
                    if (d.node(e.from) != null && d.node(e.to) != null && e.from != e.to) d.edges.add(e);
                }
            }
        } catch (JSONException ignored) {
            // an unreadable diagram opens empty rather than crashing the note
        }
        return d;
    }

    Diagram copy() {
        return fromJson(toJson());
    }

    // ------------------------------------------------------------ geometry

    static float[] anchor(RectF r, int side) {
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

    static int nearestSide(RectF r, float x, float y) {
        int best = 0;
        float bd = Float.MAX_VALUE;
        for (int s = 0; s < 4; s++) {
            float[] p = anchor(r, s);
            float dd = (p[0] - x) * (p[0] - x) + (p[1] - y) * (p[1] - y);
            if (dd < bd) {
                bd = dd;
                best = s;
            }
        }
        return best;
    }

    /** The edge's route in dp (x0,y0,x1,y1,...), computed when the diagram changed. */
    float[] route(Edge e) {
        if (e.path != null && e.pathVer == ver) return e.path;
        Node a = node(e.from), b = node(e.to);
        if (a == null || b == null) return null;
        RectF ra = a.rect(), rb = b.rect();
        int[] fsList = e.fs >= 0 ? new int[]{e.fs} : facing(ra, rb);
        int[] tsList = e.ts >= 0 ? new int[]{e.ts} : facing(rb, ra);
        float[] best = null;
        double bestCost = Double.MAX_VALUE;
        for (int fs : fsList) {
            for (int ts : tsList) {
                double[] cost = new double[1];
                float[] p = solve(ra, fs, rb, ts, e, cost);
                if (p != null && cost[0] < bestCost) {
                    bestCost = cost[0];
                    best = p;
                    e.usedFs = fs;
                    e.usedTs = ts;
                }
            }
        }
        if (best == null) { // no clean route (shapes packed in): a plain elbow, still from point to point
            int fs = fsList[0], ts = tsList[0];
            float[] p = anchor(ra, fs), q = anchor(rb, ts);
            best = new float[]{p[0], p[1], q[0], p[1], q[0], q[1]};
            e.usedFs = fs;
            e.usedTs = ts;
        }
        e.path = best;
        e.pathVer = ver;
        return best;
    }

    /** The two (or three) sides of r that face toward o: the only sensible places for an automatic arrow to leave. */
    private static int[] facing(RectF r, RectF o) {
        float dx = o.centerX() - r.centerX(), dy = o.centerY() - r.centerY();
        int h = dx >= 0 ? RIGHT : LEFT, v = dy >= 0 ? BOTTOM : TOP;
        boolean apartX = o.left > r.right || o.right < r.left, apartY = o.top > r.bottom || o.bottom < r.top;
        if (apartX && !apartY) return new int[]{h, v, v == TOP ? BOTTOM : TOP};
        if (apartY && !apartX) return new int[]{v, h, h == LEFT ? RIGHT : LEFT};
        return new int[]{h, v};
    }

    /** A* over the sparse grid. Returns the polyline or null; cost[0] = length plus bend penalties. */
    private float[] solve(RectF ra, int fs, RectF rb, int ts, Edge e, double[] cost) {
        float[] a = anchor(ra, fs), b = anchor(rb, ts);
        float[] a1 = {a[0] + DIR[fs][0] * PAD, a[1] + DIR[fs][1] * PAD};
        float[] b1 = {b[0] + DIR[ts][0] * PAD, b[1] + DIR[ts][1] * PAD};
        List<RectF> obs = new ArrayList<>();
        for (Node n : nodes) {
            RectF r = n.rect();
            r.inset(-PAD + 0.5f, -PAD + 0.5f); // a hair inside the padded edge, so the edge itself is walkable
            obs.add(r);
        }
        boolean via = !Float.isNaN(e.wx);
        if (!via) return leg(a, a1, fs, b1, (ts + 2) % 4, b, obs, cost, true);
        double[] c1 = new double[1], c2 = new double[1];
        float[] w = {e.wx, e.wy};
        int[] endDir = new int[1];
        float[] p1 = legTo(a1, fs, w, -1, obs, c1, endDir);
        float[] p2 = p1 == null ? null : legTo(w, endDir[0], b1, (ts + 2) % 4, obs, c2, null);
        if (p1 == null || p2 == null) return leg(a, a1, fs, b1, (ts + 2) % 4, b, obs, cost, true); // point unusable
        cost[0] = c1[0] + c2[0];
        float[] all = new float[2 + p1.length + p2.length - 2 + 2];
        int k = 0;
        all[k++] = a[0];
        all[k++] = a[1];
        for (float v : p1) all[k++] = v;
        for (int i = 2; i < p2.length; i++) all[k++] = p2[i];
        all[k++] = b[0];
        all[k] = b[1];
        return simplify(all);
    }

    private float[] leg(float[] a, float[] a1, int dir0, float[] b1, int dirEnd, float[] b, List<RectF> obs,
                        double[] cost, boolean wrap) {
        float[] mid = legTo(a1, dir0, b1, dirEnd, obs, cost, null);
        if (mid == null) return null;
        float[] all = new float[mid.length + 4];
        all[0] = a[0];
        all[1] = a[1];
        System.arraycopy(mid, 0, all, 2, mid.length);
        all[all.length - 2] = b[0];
        all[all.length - 1] = b[1];
        return simplify(all);
    }

    /**
     * Shortest right-angled path from s (heading dir0) to t (arriving heading dirEnd, or any when -1).
     * Grid lines: every padded shape edge, the two ends, and the midlines between neighbouring lines.
     */
    private float[] legTo(float[] s, int dir0, float[] t, int dirEnd, List<RectF> obs, double[] cost, int[] endDirOut) {
        float[] xs = lines(obs, s[0], t[0], true);
        float[] ys = lines(obs, s[1], t[1], false);
        int nx = xs.length, ny = ys.length;
        int si = idx(xs, s[0]), sj = idx(ys, s[1]), ti = idx(xs, t[0]), tj = idx(ys, t[1]);
        if (si < 0 || sj < 0 || ti < 0 || tj < 0) return null;
        boolean[] free = new boolean[nx * ny];
        for (int i = 0; i < nx; i++) for (int j = 0; j < ny; j++) free[i * ny + j] = !inside(obs, xs[i], ys[j]);
        free[si * ny + sj] = true;
        free[ti * ny + tj] = true;
        int states = nx * ny * 4;
        double[] g = new double[states];
        Arrays.fill(g, Double.MAX_VALUE);
        int[] from = new int[states];
        Arrays.fill(from, -1);
        final double bend = 24;
        PriorityQueue<double[]> open = new PriorityQueue<>((p, q) -> Double.compare(p[0], q[0]));
        int st = ((si * ny + sj) << 2) | dir0;
        g[st] = 0;
        open.add(new double[]{h(xs[si], ys[sj], t), st});
        int goal = -1;
        while (!open.isEmpty()) {
            double[] top = open.poll();
            int cur = (int) top[1];
            int cell = cur >> 2, dir = cur & 3;
            int i = cell / ny, j = cell % ny;
            if (top[0] - h(xs[i], ys[j], t) > g[cur] + 1e-6) continue; // stale entry
            if (i == ti && j == tj && (dirEnd < 0 || dir == dirEnd)) {
                goal = cur;
                break;
            }
            for (int nd = 0; nd < 4; nd++) {
                if (nd == (dir + 2) % 4) continue; // never doubling back on itself
                int ni = i + DIR[nd][0], nj = j + DIR[nd][1];
                if (ni < 0 || nj < 0 || ni >= nx || nj >= ny) continue;
                if (!free[ni * ny + nj]) continue;
                float mx = (xs[i] + xs[ni]) / 2f, my = (ys[j] + ys[nj]) / 2f;
                if (inside(obs, mx, my)) continue;
                double step = Math.abs(xs[ni] - xs[i]) + Math.abs(ys[nj] - ys[j]);
                double ng = g[cur] + step + (nd != dir ? bend : 0);
                int ns = ((ni * ny + nj) << 2) | nd;
                if (ng < g[ns]) {
                    g[ns] = ng;
                    from[ns] = cur;
                    open.add(new double[]{ng + h(xs[ni], ys[nj], t), ns});
                }
            }
        }
        if (goal < 0) return null;
        cost[0] = g[goal];
        if (endDirOut != null) endDirOut[0] = goal & 3;
        List<float[]> pts = new ArrayList<>();
        for (int c = goal; c >= 0; c = from[c]) {
            int cell = c >> 2;
            pts.add(0, new float[]{xs[cell / ny], ys[cell % ny]});
            if (c == st) break;
        }
        float[] out = new float[pts.size() * 2];
        for (int k = 0; k < pts.size(); k++) {
            out[k * 2] = pts.get(k)[0];
            out[k * 2 + 1] = pts.get(k)[1];
        }
        return out;
    }

    private static double h(float x, float y, float[] t) {
        return Math.abs(x - t[0]) + Math.abs(y - t[1]);
    }

    private static boolean inside(List<RectF> obs, float x, float y) {
        for (RectF r : obs) if (x > r.left && x < r.right && y > r.top && y < r.bottom) return true;
        return false;
    }

    private static int idx(float[] a, float v) {
        for (int i = 0; i < a.length; i++) if (Math.abs(a[i] - v) < 0.01f) return i;
        return -1;
    }

    private static float[] lines(List<RectF> obs, float s, float t, boolean x) {
        List<Float> l = new ArrayList<>();
        l.add(s);
        l.add(t);
        float lo = Math.min(s, t), hi = Math.max(s, t);
        for (RectF r : obs) {
            float a = x ? r.left - 0.5f : r.top - 0.5f, b = x ? r.right + 0.5f : r.bottom + 0.5f; // the padded edges
            l.add(a);
            l.add(b);
            lo = Math.min(lo, a);
            hi = Math.max(hi, b);
        }
        l.add(lo - PAD * 2);
        l.add(hi + PAD * 2);
        java.util.Collections.sort(l);
        List<Float> u = new ArrayList<>();
        for (float v : l) if (u.isEmpty() || Math.abs(u.get(u.size() - 1) - v) > 0.01f) u.add(v);
        List<Float> withMid = new ArrayList<>();
        for (int i = 0; i < u.size(); i++) {
            withMid.add(u.get(i));
            if (i + 1 < u.size() && u.get(i + 1) - u.get(i) > 2) withMid.add((u.get(i) + u.get(i + 1)) / 2f);
        }
        float[] out = new float[withMid.size()];
        for (int i = 0; i < out.length; i++) out[i] = withMid.get(i);
        return out;
    }

    private static float[] simplify(float[] poly) {
        List<float[]> pts = new ArrayList<>();
        for (int i = 0; i + 1 < poly.length; i += 2) {
            float[] q = {poly[i], poly[i + 1]};
            if (!pts.isEmpty()) {
                float[] l = pts.get(pts.size() - 1);
                if (Math.abs(l[0] - q[0]) < 0.01f && Math.abs(l[1] - q[1]) < 0.01f) continue;
                if (pts.size() >= 2) {
                    float[] k = pts.get(pts.size() - 2);
                    boolean sameX = Math.abs(k[0] - l[0]) < 0.01f && Math.abs(l[0] - q[0]) < 0.01f;
                    boolean sameY = Math.abs(k[1] - l[1]) < 0.01f && Math.abs(l[1] - q[1]) < 0.01f;
                    if (sameX || sameY) pts.remove(pts.size() - 1);
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

    /** The middle of the route's longest segment: where its drag handle sits. */
    float[] handle(Edge e) {
        float[] p = route(e);
        if (p == null || p.length < 4) return null;
        float best = -1, hx = p[0], hy = p[1];
        for (int i = 0; i + 3 < p.length; i += 2) {
            float len = Math.abs(p[i + 2] - p[i]) + Math.abs(p[i + 3] - p[i + 1]);
            if (len > best) {
                best = len;
                hx = (p[i] + p[i + 2]) / 2f;
                hy = (p[i + 1] + p[i + 3]) / 2f;
            }
        }
        if (!Float.isNaN(e.wx)) return new float[]{e.wx, e.wy};
        return new float[]{hx, hy};
    }

    /** Distance (dp) from (x, y) to the edge's route. */
    float distTo(Edge e, float x, float y) {
        float[] p = route(e);
        if (p == null) return Float.MAX_VALUE;
        float best = Float.MAX_VALUE;
        for (int i = 0; i + 3 < p.length; i += 2) {
            float x1 = p[i], y1 = p[i + 1], x2 = p[i + 2], y2 = p[i + 3];
            float cx = Math.max(Math.min(x1, x2), Math.min(Math.max(x1, x2), x));
            float cy = Math.max(Math.min(y1, y2), Math.min(Math.max(y1, y2), y));
            best = Math.min(best, (float) Math.hypot(x - cx, y - cy));
        }
        return best;
    }

    // ------------------------------------------------------------ drawing

    /**
     * Draws the diagram with dp -> px factor s, shifted by (ox, oy) px. ink = text colour; dark = dark page.
     * selNode / selEdge are only drawn as selected in the editor.
     */
    void draw(Canvas c, float s, float ox, float oy, int ink, boolean dark, Node selNode, Edge selEdge, boolean editing) {
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeJoin(Paint.Join.ROUND);
        line.setStrokeCap(Paint.Cap.ROUND);
        c.save();
        c.translate(ox, oy);
        // arrows under the shapes, so a shape always reads cleanly
        for (Edge e : edges) {
            float[] p = route(e);
            if (p == null || p.length < 4) continue;
            Path path = new Path();
            path.moveTo(p[0] * s, p[1] * s);
            for (int i = 2; i + 1 < p.length; i += 2) path.lineTo(p[i] * s, p[i + 1] * s);
            line.setColor(e.color);
            line.setStrokeWidth((e == selEdge ? 3f : 2f) * s);
            CornerPathEffect round = new CornerPathEffect(7 * s);
            line.setPathEffect(e.dashed ? new android.graphics.ComposePathEffect(new DashPathEffect(new float[]{7 * s, 5 * s}, 0), round) : round);
            c.drawPath(path, line);
            line.setPathEffect(null);
            // the head, along the last segment
            int n = p.length;
            float ex = p[n - 2] * s, ey = p[n - 1] * s, px = p[n - 4] * s, py = p[n - 3] * s;
            float len = (float) Math.hypot(ex - px, ey - py);
            if (len > 0.1f) {
                float ux = (ex - px) / len, uy = (ey - py) / len, hs = 9 * s;
                Path hd = new Path();
                hd.moveTo(ex, ey);
                hd.lineTo(ex - ux * hs - uy * hs * 0.55f, ey - uy * hs + ux * hs * 0.55f);
                hd.lineTo(ex - ux * hs + uy * hs * 0.55f, ey - uy * hs - ux * hs * 0.55f);
                hd.close();
                fill.setStyle(Paint.Style.FILL);
                fill.setColor(e.color);
                c.drawPath(hd, fill);
            }
        }
        TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        for (Node nd : nodes) {
            RectF r = new RectF(nd.x * s, nd.y * s, (nd.x + nd.w) * s, (nd.y + nd.h) * s);
            Path shape = shapePath(nd.kind, r, s);
            if (nd.kind != TEXT) {
                fill.setStyle(Paint.Style.FILL);
                int a = nd.kind == STICKY ? (dark ? 0x66 : 0x55) : (dark ? 0x30 : 0x1E);
                fill.setColor((nd.color & 0x00FFFFFF) | (a << 24));
                c.drawPath(shape, fill);
                line.setColor(nd.color);
                line.setStrokeWidth((nd == selNode ? 2.6f : 1.8f) * s);
                c.drawPath(shape, line);
            } else if (editing) { // a text shape shows a faint frame only while editing
                line.setColor((ink & 0x00FFFFFF) | 0x33000000);
                line.setStrokeWidth(1f * s);
                line.setPathEffect(new DashPathEffect(new float[]{4 * s, 4 * s}, 0));
                c.drawRect(r, line);
                line.setPathEffect(null);
            }
            if (nd == selNode && nd.kind == TEXT) {
                line.setColor(nd.color);
                line.setStrokeWidth(2f * s);
                c.drawRect(r, line);
            }
            if (!nd.text.isEmpty()) {
                float tw = Math.max(10, (nd.w - (nd.kind == DIAMOND ? 28 : 12)) * s);
                if (nd.lay == null || !nd.text.equals(nd.layFor) || nd.layW != tw || nd.layS != s) {
                    tp.setTextSize(14 * s);
                    tp.setColor(ink);
                    nd.lay = StaticLayout.Builder.obtain(nd.text, 0, nd.text.length(), tp, (int) tw)
                            .setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(false).build();
                    nd.layFor = nd.text;
                    nd.layW = tw;
                    nd.layS = s;
                }
                nd.lay.getPaint().setColor(ink);
                c.save();
                c.clipRect(r);
                c.translate(r.centerX() - tw / 2f, r.centerY() - nd.lay.getHeight() / 2f);
                nd.lay.draw(c);
                c.restore();
            }
        }
        c.restore();
    }

    static Path shapePath(int kind, RectF r, float s) {
        Path p = new Path();
        switch (kind) {
            case SQUIRCLE:
                return PencilFx.squircle(r);
            case ELLIPSE:
                p.addOval(r, Path.Direction.CW);
                return p;
            case DIAMOND:
                p.moveTo(r.centerX(), r.top);
                p.lineTo(r.right, r.centerY());
                p.lineTo(r.centerX(), r.bottom);
                p.lineTo(r.left, r.centerY());
                p.close();
                return p;
            case STICKY: {
                float f = 12 * s; // a folded corner
                p.moveTo(r.left, r.top);
                p.lineTo(r.right, r.top);
                p.lineTo(r.right, r.bottom - f);
                p.lineTo(r.right - f, r.bottom);
                p.lineTo(r.left, r.bottom);
                p.close();
                return p;
            }
            default:
                p.addRoundRect(r, 8 * s, 8 * s, Path.Direction.CW);
                return p;
        }
    }

    /** The shape's height that fits its words (never smaller than it is unless shrink). */
    float fitHeight(Node n, float density) {
        if (n.text.isEmpty()) return n.h;
        TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        tp.setTextSize(14 * density);
        int tw = (int) Math.max(10, (n.w - (n.kind == DIAMOND ? 28 : 12)) * density);
        StaticLayout l = StaticLayout.Builder.obtain(n.text, 0, n.text.length(), tp, tw).setIncludePad(false).build();
        float need = l.getHeight() / density + (n.kind == DIAMOND ? 34 : n.kind == ELLIPSE ? 22 : 16);
        return snap(Math.max(n.h, need) + GRID / 2);
    }
}
