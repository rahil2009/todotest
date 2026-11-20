package app.privatetodo;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * The diagram editor: an eraser.io-style canvas for one diagram block of a note.
 * <ul>
 * <li>Pick a shape and tap the canvas to place it (it opens for its words straight away).</li>
 * <li>Drag a shape to move it: it snaps to an 8 dp grid, stays inside the canvas and never overlaps another shape
 * (it slides along instead). Drag the corner square to resize.</li>
 * <li>Arrows: pick Arrow and drag from one shape to another, or drag from one of a selected shape's four rings.
 * Start or end on a ring to fix that point; anywhere else on the shape and the best point is chosen and kept up to
 * date as shapes move. Tap an arrow to select it: drag its ends onto another ring or shape to reconnect, drag its
 * middle to route it through that spot.</li>
 * <li>One finger on empty canvas pans; two fingers pinch to zoom.</li>
 * </ul>
 */
public class DiagramActivity extends Activity {
    static final String EXTRA_JSON = "json";
    static final String EXTRA_AT = "at";
    static final String EXTRA_DELETE = "delete";

    private Diagram dg;
    private CanvasView cv;
    private float density;
    private final List<String> undo = new ArrayList<>();
    private int tool = -1; // -1 select, 0..5 a shape kind, 99 arrow
    private static final int ARROW = 99;
    private LinearLayout tools, context;
    private final List<TextView> toolChips = new ArrayList<>();

    @Override
    protected void onCreate(Bundle b) {
        Pal.init(this);
        PaperTheme.apply(this);
        setTheme(Pal.themeRes());
        super.onCreate(b);
        density = getResources().getDisplayMetrics().density;
        String json = b != null ? b.getString(EXTRA_JSON) : getIntent().getStringExtra(EXTRA_JSON);
        dg = Diagram.fromJson(json);
        setContentView(build());
        getWindow().setStatusBarColor(Pal.bg);
        getWindow().setNavigationBarColor(Pal.raised | 0xFF000000);
        Fonts.apply(getWindow().getDecorView());
        refreshTools();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Lock.guard(this, Lock.NOTES);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putString(EXTRA_JSON, dg.toJson());
    }

    private int dp(float v) {
        return Math.round(v * density);
    }

    // ------------------------------------------------------------ layout

    private View build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Pal.bg);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(8), dp(6), dp(8), dp(6));
        top.addView(button("✕", v -> {
            setResult(RESULT_CANCELED);
            finish();
        }), new LinearLayout.LayoutParams(dp(44), dp(44)));
        TextView ttl = new TextView(this);
        ttl.setText("Diagram");
        ttl.setTextColor(Pal.ink);
        ttl.setTextSize(18);
        ttl.setTypeface(Typeface.DEFAULT_BOLD);
        ttl.setPadding(dp(6), 0, 0, 0);
        top.addView(ttl, new LinearLayout.LayoutParams(0, -2, 1f));
        top.addView(button("↶", v -> undo()), new LinearLayout.LayoutParams(dp(44), dp(44)));
        top.addView(button("🗑", v -> new Dlg.Builder(this).setTitle("Delete this diagram?")
                .setPositiveButton("Delete", (d, w) -> {
                    setResult(RESULT_OK, new Intent().putExtra(EXTRA_DELETE, true)
                            .putExtra(EXTRA_AT, getIntent().getIntExtra(EXTRA_AT, -1)));
                    finish();
                }).setNegativeButton("Cancel", null).show()), new LinearLayout.LayoutParams(dp(44), dp(44)));
        TextView done = button("Done", v -> save());
        done.setTextColor(Pal.onAccent);
        GradientDrawable dgd = new GradientDrawable();
        dgd.setCornerRadius(dp(20));
        dgd.setColor(Pal.accent);
        done.setBackground(dgd);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-2, dp(40));
        dl.leftMargin = dp(6);
        done.setPadding(dp(18), 0, dp(18), 0);
        top.addView(done, dl);
        root.addView(top, new LinearLayout.LayoutParams(-1, -2));

        cv = new CanvasView(this);
        root.addView(cv, new LinearLayout.LayoutParams(-1, 0, 1f));

        context = new LinearLayout(this); // what can be done to the selected shape or arrow
        context.setGravity(Gravity.CENTER_VERTICAL);
        context.setPadding(dp(8), dp(4), dp(8), dp(4));
        HorizontalScrollView cs = new HorizontalScrollView(this);
        cs.setHorizontalScrollBarEnabled(false);
        cs.addView(context);
        cs.setBackgroundColor(Pal.raised | 0xFF000000);
        root.addView(cs, new LinearLayout.LayoutParams(-1, -2));

        tools = new LinearLayout(this);
        tools.setGravity(Gravity.CENTER_VERTICAL);
        tools.setPadding(dp(8), dp(6), dp(8), dp(8));
        String[] names = {"☝ Select", "▭ Box", "▢ Squircle", "◯ Ellipse", "◇ Diamond",
                "T Text", "▤ Sticky", "→ Arrow"};
        int[] ids = {-1, Diagram.BOX, Diagram.SQUIRCLE, Diagram.ELLIPSE, Diagram.DIAMOND, Diagram.TEXT, Diagram.STICKY, ARROW};
        for (int i = 0; i < names.length; i++) {
            final int id = ids[i];
            TextView chip = chip(names[i]);
            chip.setTag(id);
            chip.setOnClickListener(v -> {
                Hap.tick(v);
                tool = tool == id ? -1 : id;
                refreshTools();
            });
            toolChips.add(chip);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(40));
            lp.rightMargin = dp(6);
            tools.addView(chip, lp);
        }
        HorizontalScrollView ts = new HorizontalScrollView(this);
        ts.setHorizontalScrollBarEnabled(false);
        ts.addView(tools);
        ts.setBackgroundColor(Pal.raised | 0xFF000000);
        root.addView(ts, new LinearLayout.LayoutParams(-1, -2));
        return root;
    }

    private TextView button(String s, View.OnClickListener l) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(Pal.ink);
        t.setTextSize(18);
        t.setGravity(Gravity.CENTER);
        t.setOnClickListener(v -> {
            Hap.tick(v);
            l.onClick(v);
        });
        return t;
    }

    private TextView chip(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(14);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(14), 0, dp(14), 0);
        return t;
    }

    private void paintChip(TextView t, boolean on) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(20));
        g.setColor(on ? Pal.sel : Pal.chip);
        t.setBackground(g);
        t.setTextColor(on ? Pal.onSel : Pal.ink);
    }

    /** The tool row shows the active tool; the row above it offers what fits the selection. */
    private void refreshTools() {
        for (TextView t : toolChips) paintChip(t, (Integer) t.getTag() == tool);
        context.removeAllViews();
        if (cv.selNode == null && cv.selEdge == null) {
            TextView hint = new TextView(this);
            hint.setTextColor(Pal.muted);
            hint.setTextSize(13);
            hint.setText(tool == ARROW ? "Drag from one shape to another. Start or end on a ring to pick that point."
                    : tool >= 0 ? "Tap the canvas to place a " + Diagram.KIND_NAMES[tool].toLowerCase() + "."
                    : "Tap a shape or arrow to change it. Empty canvas: drag to pan, pinch to zoom.");
            hint.setPadding(dp(6), dp(10), dp(6), dp(10));
            context.addView(hint);
            return;
        }
        for (int i = 0; i < Diagram.COLORS.length; i++) {
            final int col = Diagram.COLORS[i];
            View sw = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(col);
            int cur = cv.selNode != null ? cv.selNode.color : cv.selEdge.color;
            if (cur == col) g.setStroke(dp(3), Pal.ink);
            sw.setBackground(g);
            sw.setOnClickListener(v -> {
                Hap.tick(v);
                push();
                if (cv.selNode != null) cv.selNode.color = col;
                else if (cv.selEdge != null) cv.selEdge.color = col;
                changed();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(30), dp(30));
            lp.rightMargin = dp(8);
            context.addView(sw, lp);
        }
        if (cv.selNode != null) {
            addAction("Text", v -> editText(cv.selNode));
            addAction("Shape", v -> {
                push();
                cv.selNode.kind = (cv.selNode.kind + 1) % (Diagram.STICKY + 1);
                changed();
            });
        } else {
            addAction(cv.selEdge.dashed ? "Solid" : "Dashed", v -> {
                push();
                cv.selEdge.dashed = !cv.selEdge.dashed;
                changed();
            });
            addAction("Reverse", v -> {
                push();
                Diagram.Edge e = cv.selEdge;
                int f = e.from, fs = e.fs;
                e.from = e.to;
                e.fs = e.ts;
                e.to = f;
                e.ts = fs;
                changed();
            });
            addAction("Auto route", v -> {
                push();
                cv.selEdge.fs = -1;
                cv.selEdge.ts = -1;
                cv.selEdge.wx = Float.NaN;
                cv.selEdge.wy = Float.NaN;
                changed();
            });
        }
        addAction("Delete", v -> {
            push();
            if (cv.selNode != null) dg.removeNode(cv.selNode);
            else dg.edges.remove(cv.selEdge);
            cv.selNode = null;
            cv.selEdge = null;
            changed();
        });
    }

    private void addAction(String s, View.OnClickListener l) {
        TextView t = chip(s);
        paintChip(t, false);
        t.setOnClickListener(v -> {
            Hap.tick(v);
            l.onClick(v);
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(36));
        lp.rightMargin = dp(6);
        context.addView(t, lp);
    }

    // ------------------------------------------------------------ model edits

    private void push() {
        undo.add(dg.toJson());
        if (undo.size() > 60) undo.remove(0);
    }

    private void undo() {
        if (undo.isEmpty()) return;
        dg = Diagram.fromJson(undo.remove(undo.size() - 1));
        cv.selNode = null;
        cv.selEdge = null;
        changed();
    }

    private void changed() {
        dg.ver++;
        refreshTools();
        cv.invalidate();
    }

    private void editText(final Diagram.Node n) {
        final EditText ed = new EditText(this);
        ed.setText(n.text);
        ed.setSelection(ed.getText().length());
        ed.setTextColor(Pal.ink);
        ed.setMinLines(2);
        ed.setMaxLines(8);
        LinearLayout box = new LinearLayout(this);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        box.addView(ed, new LinearLayout.LayoutParams(-1, -2));
        android.app.AlertDialog d = new Dlg.Builder(this).setTitle(Diagram.KIND_NAMES[n.kind] + " text").setView(box)
                .setPositiveButton("OK", (x, w) -> {
                    push();
                    n.text = ed.getText().toString();
                    float nh = dg.fitHeight(n, density); // the shape grows to fit its words
                    if (nh > n.h) {
                        float old = n.h;
                        n.h = nh;
                        if (dg.collides(n.rect(), n)) { // no room to grow here: find a free spot for it
                            float[] sp = dg.freeSpot(n.x, n.y, n.w, n.h);
                            dg.nodes.remove(n);
                            boolean ok = !dg.collides(new RectF(sp[0], sp[1], sp[0] + n.w, sp[1] + n.h), null);
                            dg.nodes.add(n);
                            if (ok) {
                                n.x = sp[0];
                                n.y = sp[1];
                            } else {
                                n.h = old;
                            }
                        }
                    }
                    changed();
                })
                .setNegativeButton("Cancel", null).create();
        if (d.getWindow() != null) d.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        d.show();
        Dlg.style(d);
        ed.requestFocus();
    }

    private void save() {
        setResult(RESULT_OK, new Intent().putExtra(EXTRA_JSON, dg.toJson())
                .putExtra(EXTRA_AT, getIntent().getIntExtra(EXTRA_AT, -1)));
        finish();
    }

    @Override
    public void onBackPressed() {
        if (cv.selNode != null || cv.selEdge != null) {
            cv.selNode = null;
            cv.selEdge = null;
            refreshTools();
            cv.invalidate();
            return;
        }
        save(); // Back keeps the work
    }

    // ------------------------------------------------------------ canvas

    private final class CanvasView extends View {
        Diagram.Node selNode;
        Diagram.Edge selEdge;
        private float zoom = 1f, tx, ty;
        private final ScaleGestureDetector scaler;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        // gesture state
        private static final int NONE = 0, PAN = 1, MOVE = 2, RESIZE = 3, BEND = 4, REBIND_FROM = 5, REBIND_TO = 6,
                NEW_ARROW = 7, TAP_SHAPE = 8;
        private int op = NONE;
        private float downX, downY, lastX, lastY, fingerX, fingerY; // screen px
        private float startX, startY, startW, startH; // dp
        private boolean moved, pushed, multi;
        private Diagram.Node arrowFrom;
        private int arrowSide = -1;
        private long lastTapAt;
        private Diagram.Node lastTapNode;

        CanvasView(Context c) {
            super(c);
            scaler = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override
                public boolean onScale(ScaleGestureDetector d) {
                    float nz = Math.max(0.5f, Math.min(3f, zoom * d.getScaleFactor()));
                    float f = nz / zoom;
                    tx = d.getFocusX() - (d.getFocusX() - tx) * f;
                    ty = d.getFocusY() - (d.getFocusY() - ty) * f;
                    zoom = nz;
                    clampPan();
                    invalidate();
                    return true;
                }
            });
        }

        /** dp -> px. At zoom 1 the canvas width fills the view. */
        float s() {
            return getWidth() / Diagram.W * zoom;
        }

        float cx(float sx) {
            return (sx - tx) / s();
        }

        float cy(float sy) {
            return (sy - ty) / s();
        }

        private void clampPan() {
            float s = s();
            float cw = Diagram.W * s;
            if (cw <= getWidth()) tx = (getWidth() - cw) / 2f;
            else tx = Math.max(getWidth() - cw, Math.min(0, tx));
            float ch = Math.max(dg.bottom() + 400, 900) * s;
            ty = Math.max(getHeight() - ch, Math.min(dp(24), ty));
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            super.onSizeChanged(w, h, ow, oh);
            clampPan();
        }

        @Override
        protected void onDraw(Canvas c) {
            float s = s();
            c.drawColor(Pal.bg);
            // the canvas sheet, its walls and a dot grid
            p.setStyle(Paint.Style.FILL);
            p.setColor(Pal.dark ? 0x0DFFFFFF : 0x08000000);
            float left = tx, right = tx + Diagram.W * s;
            c.drawRect(left, 0, right, getHeight(), p);
            p.setColor(Pal.dark ? 0x33FFFFFF : 0x22000000);
            float g = 16 * s;
            float y0 = ty + (float) Math.floor((0 - ty) / g) * g;
            for (float y = Math.max(ty, y0); y < getHeight(); y += g) {
                for (float x = left; x <= right + 0.5f; x += g) c.drawCircle(x, y, Math.max(1f, 0.9f * s), p);
            }
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(1));
            p.setColor(Pal.chip);
            c.drawLine(left, 0, left, getHeight(), p);
            c.drawLine(right, 0, right, getHeight(), p);
            p.setStyle(Paint.Style.FILL);

            dg.draw(c, s, tx, ty, Pal.ink, Pal.dark, selNode, selEdge, true);

            // selection furniture
            if (selNode != null) {
                RectF r = sr(selNode.rect());
                for (int side = 0; side < 4; side++) ring(c, Diagram.anchor(r, side), Pal.accent, false);
                p.setStyle(Paint.Style.FILL);
                p.setColor(Pal.accent);
                c.drawRect(r.right - dp(7), r.bottom - dp(7), r.right + dp(7), r.bottom + dp(7), p);
            }
            if (op == NEW_ARROW || op == REBIND_FROM || op == REBIND_TO || tool == ARROW) { // every shape shows its rings
                for (Diagram.Node n : dg.nodes) {
                    RectF r = sr(n.rect());
                    for (int side = 0; side < 4; side++) ring(c, Diagram.anchor(r, side), Pal.accent, false);
                }
            }
            if (selEdge != null && op != REBIND_FROM && op != REBIND_TO) {
                float[] pth = dg.route(selEdge);
                if (pth != null && pth.length >= 4) {
                    ring(c, new float[]{tx + pth[0] * s, ty + pth[1] * s}, selEdge.color, true);
                    ring(c, new float[]{tx + pth[pth.length - 2] * s, ty + pth[pth.length - 1] * s}, selEdge.color, true);
                    float[] h = dg.handle(selEdge);
                    if (h != null) {
                        p.setStyle(Paint.Style.FILL);
                        p.setColor(selEdge.color);
                        c.drawCircle(tx + h[0] * s, ty + h[1] * s, dp(8), p);
                        p.setColor(0xFFFFFFFF);
                        c.drawCircle(tx + h[0] * s, ty + h[1] * s, dp(3), p);
                    }
                }
            }
            if (op == NEW_ARROW || op == REBIND_FROM || op == REBIND_TO) { // the line following the finger
                float[] from = null;
                if (op == NEW_ARROW && arrowFrom != null) {
                    RectF r = sr(arrowFrom.rect());
                    from = arrowSide >= 0 ? Diagram.anchor(r, arrowSide) : new float[]{r.centerX(), r.centerY()};
                } else if (selEdge != null) {
                    Diagram.Node other = dg.node(op == REBIND_FROM ? selEdge.to : selEdge.from);
                    if (other != null) {
                        RectF r = sr(other.rect());
                        from = new float[]{r.centerX(), r.centerY()};
                    }
                }
                if (from != null) {
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(dp(2));
                    p.setColor(Pal.accent);
                    p.setPathEffect(new DashPathEffect(new float[]{dp(8), dp(6)}, 0));
                    c.drawLine(from[0], from[1], fingerX, fingerY, p);
                    p.setPathEffect(null);
                    ring(c, new float[]{fingerX, fingerY}, Pal.accent, true);
                }
            }
        }

        private RectF sr(RectF r) {
            float s = s();
            return new RectF(tx + r.left * s, ty + r.top * s, tx + r.right * s, ty + r.bottom * s);
        }

        private void ring(Canvas c, float[] pt, int color, boolean big) {
            float r = dp(big ? 8 : 5.5f);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFFFFFFFF);
            c.drawCircle(pt[0], pt[1], r, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(2));
            p.setColor(color);
            c.drawCircle(pt[0], pt[1], r, p);
            p.setStyle(Paint.Style.FILL);
        }

        private Diagram.Node nodeAt(float x, float y) { // dp; the top-most shape wins
            for (int i = dg.nodes.size() - 1; i >= 0; i--) {
                Diagram.Node n = dg.nodes.get(i);
                RectF r = n.rect();
                r.inset(-4, -4);
                if (r.contains(x, y)) return n;
            }
            return null;
        }

        /** The anchor ring of n within reach of the finger, or -1. */
        private int ringAt(Diagram.Node n, float x, float y) {
            float reach = 20 / zoom;
            RectF r = n.rect();
            for (int side = 0; side < 4; side++) {
                float[] a = Diagram.anchor(r, side);
                if (Math.hypot(a[0] - x, a[1] - y) < reach) return side;
            }
            return -1;
        }

        private void begin() {
            if (!pushed) {
                push();
                pushed = true;
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            scaler.onTouchEvent(e);
            int act = e.getActionMasked();
            if (act == MotionEvent.ACTION_POINTER_DOWN || e.getPointerCount() > 1) { // two fingers: zoom / pan only
                if (!multi && (op == MOVE || op == RESIZE || op == BEND) && pushed) { // put back what one finger began
                    undo();
                    pushed = false;
                }
                multi = true;
                op = PAN;
                if (act == MotionEvent.ACTION_MOVE) {
                    float fx = 0, fy = 0;
                    for (int i = 0; i < e.getPointerCount(); i++) {
                        fx += e.getX(i);
                        fy += e.getY(i);
                    }
                    fx /= e.getPointerCount();
                    fy /= e.getPointerCount();
                    if (lastX != 0 || lastY != 0) {
                        tx += fx - lastX;
                        ty += fy - lastY;
                        clampPan();
                    }
                    lastX = fx;
                    lastY = fy;
                    invalidate();
                } else {
                    lastX = 0;
                    lastY = 0;
                }
                return true;
            }
            float x = e.getX(), y = e.getY();
            float px = cx(x), py = cy(y);
            fingerX = x;
            fingerY = y;
            switch (act) {
                case MotionEvent.ACTION_DOWN:
                    getParent().requestDisallowInterceptTouchEvent(true);
                    multi = false;
                    downX = lastX = x;
                    downY = lastY = y;
                    moved = false;
                    pushed = false;
                    op = NONE;
                    down(px, py);
                    invalidate();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (multi) return true;
                    if (!moved && Math.hypot(x - downX, y - downY) > dp(6)) moved = true;
                    if (moved) drag(px, py, x - lastX, y - lastY);
                    lastX = x;
                    lastY = y;
                    invalidate();
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!multi) up(px, py);
                    op = NONE;
                    lastX = 0;
                    lastY = 0;
                    invalidate();
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    op = NONE;
                    invalidate();
                    return true;
            }
            return true;
        }

        private void down(float px, float py) {
            float s = s();
            if (tool == ARROW) {
                Diagram.Node n = nodeAt(px, py);
                if (n != null) {
                    op = NEW_ARROW;
                    arrowFrom = n;
                    arrowSide = ringAt(n, px, py);
                } else {
                    op = PAN;
                }
                return;
            }
            if (tool >= 0) {
                op = PAN; // a shape is placed on lift (a tap); a drag pans
                return;
            }
            if (selEdge != null) {
                float[] pth = dg.route(selEdge);
                float reach = 22 / zoom;
                if (pth != null && pth.length >= 4) {
                    if (Math.hypot(pth[0] - px, pth[1] - py) < reach) {
                        op = REBIND_FROM;
                        return;
                    }
                    if (Math.hypot(pth[pth.length - 2] - px, pth[pth.length - 1] - py) < reach) {
                        op = REBIND_TO;
                        return;
                    }
                }
                float[] h = dg.handle(selEdge);
                if (h != null && Math.hypot(h[0] - px, h[1] - py) < reach) {
                    op = BEND;
                    return;
                }
            }
            if (selNode != null) {
                RectF r = selNode.rect();
                if (Math.hypot(r.right - px, r.bottom - py) < 22 / zoom) {
                    op = RESIZE;
                    startW = selNode.w;
                    startH = selNode.h;
                    return;
                }
                int ring = ringAt(selNode, px, py);
                if (ring >= 0) { // drag from a ring: a new arrow from exactly that point
                    op = NEW_ARROW;
                    arrowFrom = selNode;
                    arrowSide = ring;
                    return;
                }
            }
            Diagram.Node n = nodeAt(px, py);
            if (n != null) {
                op = TAP_SHAPE;
                selNode = n;
                selEdge = null;
                startX = n.x;
                startY = n.y;
                refreshTools();
                return;
            }
            Diagram.Edge best = null;
            float bd = 14 / zoom;
            for (Diagram.Edge ed : dg.edges) {
                float d = dg.distTo(ed, px, py);
                if (d < bd) {
                    bd = d;
                    best = ed;
                }
            }
            if (best != null) {
                selEdge = best;
                selNode = null;
                op = BEND; // a drag along an arrow bends it through that spot
                refreshTools();
                return;
            }
            if (selNode != null || selEdge != null) {
                selNode = null;
                selEdge = null;
                refreshTools();
            }
            op = PAN;
        }

        private void drag(float px, float py, float dxPx, float dyPx) {
            switch (op) {
                case PAN:
                    tx += dxPx;
                    ty += dyPx;
                    clampPan();
                    break;
                case TAP_SHAPE:
                    op = MOVE;
                    // fall through
                case MOVE: {
                    Diagram.Node n = selNode;
                    if (n == null) break;
                    float ddx = (fingerX - downX) / s(), ddy = (fingerY - downY) / s();
                    float nx = Diagram.clampX(Diagram.snap(startX + ddx), n.w);
                    float ny = Math.max(4, Diagram.snap(startY + ddy));
                    if (nx == n.x && ny == n.y) break;
                    begin();
                    if (fits(n, nx, ny)) {
                        n.x = nx;
                        n.y = ny;
                    } else if (fits(n, nx, n.y)) { // blocked: slide along the other shape instead
                        n.x = nx;
                    } else if (fits(n, n.x, ny)) {
                        n.y = ny;
                    }
                    dg.ver++;
                    break;
                }
                case RESIZE: {
                    Diagram.Node n = selNode;
                    if (n == null) break;
                    float ddx = (fingerX - downX) / s(), ddy = (fingerY - downY) / s();
                    float nw = Math.max(40, Diagram.snap(startW + ddx));
                    float nh = Math.max(28, Diagram.snap(startH + ddy));
                    nw = Math.min(nw, Diagram.W - n.x - 4);
                    begin();
                    float ow = n.w, oh = n.h;
                    n.w = nw;
                    n.h = nh;
                    if (dg.collides(n.rect(), n)) {
                        n.w = ow;
                        n.h = oh;
                    }
                    dg.ver++;
                    break;
                }
                case BEND:
                    if (selEdge == null) break;
                    begin();
                    selEdge.wx = Math.max(0, Math.min(Diagram.W, Diagram.snap(px)));
                    selEdge.wy = Math.max(0, Diagram.snap(py));
                    dg.ver++;
                    break;
                default:
                    break; // NEW_ARROW / REBIND: only the rubber line moves
            }
        }

        private boolean fits(Diagram.Node n, float x, float y) {
            return !dg.collides(new RectF(x, y, x + n.w, y + n.h), n);
        }

        private void up(float px, float py) {
            if (tool >= 0 && tool != ARROW && !moved) { // place a new shape where tapped
                push();
                Diagram.Node n = new Diagram.Node();
                n.id = dg.nextId();
                n.kind = tool;
                if (tool == Diagram.TEXT) {
                    n.w = 120;
                    n.h = 40;
                } else if (tool == Diagram.DIAMOND || tool == Diagram.ELLIPSE) {
                    n.w = 128;
                    n.h = 72;
                } else if (tool == Diagram.STICKY) {
                    n.w = 112;
                    n.h = 96;
                    n.color = Diagram.COLORS[3];
                }
                float[] sp = dg.freeSpot(px - n.w / 2f, py - n.h / 2f, n.w, n.h);
                n.x = sp[0];
                n.y = sp[1];
                dg.nodes.add(n);
                selNode = n;
                selEdge = null;
                tool = -1;
                changed();
                editText(n);
                return;
            }
            if (op == NEW_ARROW) {
                Diagram.Node to = nodeAt(px, py);
                if (to != null && arrowFrom != null && to != arrowFrom) {
                    push();
                    Diagram.Edge ed = new Diagram.Edge();
                    ed.id = dg.nextId();
                    ed.from = arrowFrom.id;
                    ed.to = to.id;
                    ed.fs = arrowSide;
                    ed.ts = ringAt(to, px, py);
                    dg.edges.add(ed);
                    selEdge = ed;
                    selNode = null;
                    changed();
                    Hap.tick(this);
                }
                arrowFrom = null;
                return;
            }
            if (op == REBIND_FROM || op == REBIND_TO) {
                Diagram.Node to = nodeAt(px, py);
                Diagram.Edge ed = selEdge;
                if (to != null && ed != null) {
                    int other = op == REBIND_FROM ? ed.to : ed.from;
                    if (to.id != other) {
                        push();
                        int ring = ringAt(to, px, py);
                        int side = ring >= 0 ? ring : -1;
                        if (op == REBIND_FROM) {
                            boolean same = to.id == ed.from;
                            ed.from = to.id;
                            ed.fs = same && side < 0 ? Diagram.nearestSide(to.rect(), px, py) : side;
                        } else {
                            boolean same = to.id == ed.to;
                            ed.to = to.id;
                            ed.ts = same && side < 0 ? Diagram.nearestSide(to.rect(), px, py) : side;
                        }
                        ed.wx = Float.NaN; // a reconnected arrow routes itself afresh
                        ed.wy = Float.NaN;
                        changed();
                        Hap.tick(this);
                    }
                }
                return;
            }
            if (op == TAP_SHAPE && !moved) { // a second tap (or a double tap) on a shape opens its words
                long now = android.os.SystemClock.uptimeMillis();
                if (lastTapNode == selNode && now - lastTapAt < 450) editText(selNode);
                lastTapNode = selNode;
                lastTapAt = now;
            }
            if (pushed) changed();
        }
    }
}
