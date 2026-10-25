package app.privatetodo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.style.ReplacementSpan;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.EditText;

/** EditText that reports cursor / selection moves, so the toolbar can show what is active. */
final class RichEditText extends EditText {
    interface SelectionListener {
        void onSelection(int start, int end);
    }

    interface CheckTapListener {
        void onCheckTap(int lineStart);
    }

    interface ZoomListener {
        void onZoom(float factor, boolean done);
    }

    private ZoomListener zoomListener;
    private final android.view.ScaleGestureDetector scaler;
    private boolean zooming;
    private SelectionListener listener;
    private CheckTapListener checkListener;
    private float downX;
    private float downY;

    /** Draws the to-do box as a circle: empty ring when open, filled with a tick when done. */
    private static final class CircleSpan extends ReplacementSpan {
        final boolean on;

        CircleSpan(boolean on) {
            this.on = on;
        }

        @Override
        public int getSize(Paint p, CharSequence t, int s, int e, Paint.FontMetricsInt fm) {
            return Math.round(p.getTextSize() * 1.1f);
        }

        @Override
        public void draw(Canvas c, CharSequence t, int s, int e, float x, int top, int y,
                         int bottom, Paint p) {
            if (RichText.hiddenAt(t, s, e)) return;
            float size = p.getTextSize();
            float r = size * 0.42f;
            float cx = x + size * 0.5f;
            float cy = (top + bottom) / 2f;
            int old = p.getColor();
            Paint.Style oldStyle = p.getStyle();
            float oldW = p.getStrokeWidth();
            p.setAntiAlias(true);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            if (on) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(Pal.accent);
                c.drawCircle(cx, cy, r, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(size * 0.11f);
                p.setColor(Pal.onAccent);
                Path k = new Path();
                k.moveTo(cx - r * 0.45f, cy + r * 0.02f);
                k.lineTo(cx - r * 0.1f, cy + r * 0.38f);
                k.lineTo(cx + r * 0.48f, cy - r * 0.34f);
                c.drawPath(k, p);
            } else {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(size * 0.09f);
                p.setColor(Pal.muted);
                c.drawCircle(cx, cy, r, p);
            }
            p.setColor(old);
            p.setStyle(oldStyle);
            p.setStrokeWidth(oldW);
        }
    }

    RichEditText(Context c) {
        super(c);
        scaler = new android.view.ScaleGestureDetector(c,
                new android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(android.view.ScaleGestureDetector d) {
                        if (zoomListener != null) zoomListener.onZoom(d.getScaleFactor(), false);
                        return true;
                    }

                    @Override
                    public void onScaleEnd(android.view.ScaleGestureDetector d) {
                        if (zoomListener != null) zoomListener.onZoom(1f, true);
                    }
                });
        addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int d) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int d) { }
            @Override public void afterTextChanged(Editable e) {
                syncCircles(e);
            }
        });
    }

    /** Puts a CircleSpan over the box character of every to-do line (and removes stale ones). */
    private static void syncCircles(Editable e) {
        CircleSpan[] old = e.getSpans(0, e.length(), CircleSpan.class);
        int n = e.length();
        int i = 0;
        while (i < n) {
            int k = RichText.prefixAt(e, i);
            if (RichText.isCheck(k)) {
                boolean on = k == RichText.P_CHECK_ON;
                CircleSpan have = null;
                for (CircleSpan o : old) {
                    if (e.getSpanStart(o) == i && e.getSpanEnd(o) == i + 1 && o.on == on) have = o;
                }
                if (have == null) e.setSpan(new CircleSpan(on), i, i + 1, Editable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            int j = i;
            while (j < n && e.charAt(j) != '\n') j++;
            i = j + 1;
        }
        // drop spans that no longer sit on a matching box
        for (CircleSpan o : old) {
            int st = e.getSpanStart(o);
            if (st < 0) continue;
            int k = RichText.prefixAt(e, st);
            boolean ok = RichText.isCheck(k) && e.getSpanEnd(o) == st + 1 && o.on == (k == RichText.P_CHECK_ON);
            if (!ok) e.removeSpan(o);
        }
    }

    /** A tap on a table cell. left / top are the table's top-left corner in this view's own coordinates. */
    interface TableTapListener {
        void onTableTap(RichText.Tbl t, int row, int col, boolean onTick, float left, float top);
    }

    private TableTapListener tableListener;
    private RichText.Tbl tblDown;
    private int tblR;
    private int tblC;
    private boolean tblTick;
    private boolean tblMoved;
    private float tblLeft;
    private float tblTop;

    void setTableTapListener(TableTapListener l) {
        tableListener = l;
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        RichText.tableWidth = Math.max(0, w - getPaddingLeft() - getPaddingRight());
    }

    /** True when a finger that just went down at (x, y) is on a table cell; remembers which. */
    private boolean tableDown(float x, float y) {
        android.text.Layout l = getLayout();
        if (l == null || tableListener == null) return false;
        int line = l.getLineForVertical((int) (y - getTotalPaddingTop() + getScrollY()));
        int ls = l.getLineStart(line);
        RichText.Tbl t = RichText.tableOnLine(getText(), ls);
        if (t == null || RichText.hiddenAt(getText(), ls, ls + 1)) return false;
        float xl = x - getTotalPaddingLeft() + getScrollX();
        float lineTop = l.getLineTop(line);
        float yl = y - (lineTop + getTotalPaddingTop() - getScrollY());
        int[] rc = t.cellAt(xl, yl, getPaint());
        if (rc == null) return false;
        tblDown = t;
        tblR = rc[0];
        tblC = rc[1];
        tblTick = t.onTick(xl, yl, getPaint());
        tblLeft = getTotalPaddingLeft() - getScrollX();
        tblTop = lineTop + getTotalPaddingTop() - getScrollY();
        tblMoved = false;
        return true;
    }

    void setZoomListener(ZoomListener l) {
        zoomListener = l;
    }

    void setSelectionListener(SelectionListener l) {
        listener = l;
    }

    void setCheckTapListener(CheckTapListener l) {
        checkListener = l;
    }

    interface LinkTapListener {
        void onLinkTap(int noteId);
    }

    private LinkTapListener linkListener;

    void setLinkTapListener(LinkTapListener l) {
        linkListener = l;
    }

    /** The note id of the link under this point, or -1. */
    private int linkAt(float x, float y) {
        android.text.Layout l = getLayout();
        if (l == null) return -1;
        int off = getOffsetForPosition(x, y);
        int line = l.getLineForVertical((int) (y - getTotalPaddingTop() + getScrollY()));
        float hx = getTotalPaddingLeft() + l.getPrimaryHorizontal(off) - getScrollX();
        int ch = x < hx ? off - 1 : off;
        CharSequence t = getText();
        if (ch < 0 || ch >= t.length() || l.getLineForOffset(ch) != line) return -1;
        for (RichText.NoteLink k : getText().getSpans(ch, ch + 1, RichText.NoteLink.class)) return k.noteId;
        return -1;
    }

    // ---- finger (paint) mode: swipe over text to apply the chosen style

    interface PaintListener {
        /** A swipe began at this text offset. */
        void onStart(int offset);

        /** The swipe has covered [lo, hi) so far (it only ever grows). */
        void onStroke(int lo, int hi);

        void onEnd();

        /** A plain tap (no swipe) at this offset. */
        void onTap(int offset);
    }

    private PaintListener paintL;
    private int pState; // 0 idle, 1 finger down, 2 painting, 3 scrolling the page
    private int pStart;
    private int pLo;
    private int pHi;

    /** Non-null turns the field into a paint surface: touches paint instead of placing the cursor. */
    void setPaintListener(PaintListener l) {
        paintL = l;
        pState = 0;
        setCursorVisible(l == null);
    }

    private int offsetAt(float x, float y) {
        if (getLayout() == null) return 0;
        return Math.max(0, Math.min(length(), getOffsetForPosition(x, y)));
    }

    private boolean paintTouch(MotionEvent e) {
        int slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                pState = 1;
                downX = e.getX();
                downY = e.getY();
                pStart = offsetAt(e.getX(), e.getY());
                pLo = pStart;
                pHi = pStart;
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (pState == 1) {
                    float dx = Math.abs(e.getX() - downX);
                    float dy = Math.abs(e.getY() - downY);
                    if (dx < slop && dy < slop) return true;
                    if (dx >= dy) { // sideways: paint
                        pState = 2;
                        paintL.onStart(pStart);
                    } else { // up / down: let the page scroll
                        pState = 3;
                        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
                        return true;
                    }
                }
                if (pState == 2) {
                    int off = offsetAt(e.getX(), e.getY());
                    int lo = Math.min(pLo, off);
                    int hi = Math.max(pHi, off);
                    if (lo != pLo || hi != pHi) {
                        pLo = lo;
                        pHi = hi;
                        paintL.onStroke(pLo, pHi);
                    }
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (pState == 1) paintL.onTap(pStart);
                if (pState == 2) paintL.onEnd();
                pState = 0;
                return true;
            case MotionEvent.ACTION_CANCEL:
                if (pState == 2) paintL.onEnd();
                pState = 0;
                return true;
            default:
                return true;
        }
    }

    private int checkTouch = -1; // line start of the to-do circle the finger went down on, else -1

    /** Line start of the to-do circle under (x, y), or -1. Only the circle plus a thumb's margin counts. */
    private int checkLineAt(float x, float y) {
        if (getLayout() == null) return -1;
        int off = getOffsetForPosition(x, y);
        CharSequence t = getText();
        int ls = RichText.lineStart(t, off);
        int mk = RichText.prefixAt(t, ls);
        if (off > ls + 1 || !(RichText.isCheck(mk) || RichText.isToggle(mk))) return -1;
        float edge = getTotalPaddingLeft() + getLayout().getPrimaryHorizontal(ls + 1)
                + 10 * getResources().getDisplayMetrics().density;
        float from = getTotalPaddingLeft() + getLayout().getPrimaryHorizontal(ls) - 12 * getResources().getDisplayMetrics().density;
        return x <= edge && x >= from ? ls : -1;
    }

    /** A tap on a to-do box (the first characters of a "\u2610 " / "\u2611 " line) ticks it. */
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        scaler.onTouchEvent(e);
        if (scaler.isInProgress() || e.getPointerCount() > 1) {
            zooming = true;
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
            return true; // two fingers: pinch only, never moves the cursor or ticks a box
        }
        if (zooming) {
            if (e.getActionMasked() == MotionEvent.ACTION_UP
                    || e.getActionMasked() == MotionEvent.ACTION_CANCEL) zooming = false;
            return true;
        }
        if (paintL != null) return paintTouch(e);
        // A touch that starts on a table cell is ours too: no cursor, no keyboard from the note itself;
        // a tap hands the cell to the editor, which opens it for typing right there.
        if (tableListener != null) {
            int act0 = e.getActionMasked();
            if (act0 == MotionEvent.ACTION_DOWN) {
                downX = e.getX();
                downY = e.getY();
                tblDown = null;
                if (tableDown(e.getX(), e.getY())) return true;
            } else if (tblDown != null) {
                int slop0 = ViewConfiguration.get(getContext()).getScaledTouchSlop();
                if (Math.abs(e.getX() - downX) >= slop0 || Math.abs(e.getY() - downY) >= slop0) tblMoved = true;
                if (act0 == MotionEvent.ACTION_UP) {
                    RichText.Tbl t = tblDown;
                    tblDown = null;
                    if (!tblMoved) tableListener.onTableTap(t, tblR, tblC, tblTick, tblLeft, tblTop);
                } else if (act0 == MotionEvent.ACTION_CANCEL) {
                    tblDown = null;
                }
                return true;
            }
        }
        // A touch that starts on a to-do circle is ours alone: it ticks the box without focusing the
        // field, moving the cursor or raising the keyboard.
        if (checkListener != null) {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                downX = e.getX();
                downY = e.getY();
                checkTouch = checkLineAt(e.getX(), e.getY());
                if (checkTouch >= 0) return true;
            } else if (checkTouch != -1) { // -2 = the finger moved off: a scroll, not a tap
                int act = e.getActionMasked();
                int slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
                if (Math.abs(e.getX() - downX) >= slop || Math.abs(e.getY() - downY) >= slop) {
                    checkTouch = -2;
                }
                if (act == MotionEvent.ACTION_UP) {
                    int ls = checkTouch;
                    checkTouch = -1;
                    if (ls >= 0) checkListener.onCheckTap(ls);
                } else if (act == MotionEvent.ACTION_CANCEL) {
                    checkTouch = -1;
                }
                return true;
            }
        }
        boolean handled = super.onTouchEvent(e);
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
            downX = e.getX();
            downY = e.getY();
        } else if (e.getActionMasked() == MotionEvent.ACTION_UP && checkListener != null) {
            int slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
            if (Math.abs(e.getX() - downX) < slop && Math.abs(e.getY() - downY) < slop
                    && getLayout() != null) {
                int off = getOffsetForPosition(e.getX(), e.getY());
                CharSequence t = getText();
                int ls = RichText.lineStart(t, off);
                int mk = RichText.prefixAt(t, ls);
                if (off <= ls + 1 && (RichText.isCheck(mk) || RichText.isToggle(mk))) {
                    // only the circle (plus a thumb's margin) ticks; the text after it just edits
                    float edge = getTotalPaddingLeft() + getLayout().getPrimaryHorizontal(ls + 1)
                            + 10 * getResources().getDisplayMetrics().density;
                    if (e.getX() <= edge) checkListener.onCheckTap(ls);
                }
            }
        }
        if (e.getActionMasked() == MotionEvent.ACTION_UP && linkListener != null) { // a quick tap on a note link opens it
            int slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
            if (Math.abs(e.getX() - downX) < slop && Math.abs(e.getY() - downY) < slop
                    && e.getEventTime() - e.getDownTime() < ViewConfiguration.getLongPressTimeout()) {
                int id = linkAt(e.getX(), e.getY());
                if (id >= 0) linkListener.onLinkTap(id);
            }
        }
        return handled;
    }

    private float baseStroke;

    /** Outline (fraction of the text size) for the note's default font when it is a pen / marker font. */
    void setBaseStroke(float k) {
        baseStroke = k;
        invalidate();
    }

    @Override
    protected void onDraw(android.graphics.Canvas canvas) {
        android.graphics.Paint p = getPaint();
        if (baseStroke > 0) {
            p.setStyle(android.graphics.Paint.Style.FILL_AND_STROKE);
            p.setStrokeJoin(android.graphics.Paint.Join.ROUND);
            p.setStrokeWidth(getTextSize() * baseStroke);
        } else if (p.getStyle() != android.graphics.Paint.Style.FILL) {
            p.setStyle(android.graphics.Paint.Style.FILL);
        }
        drawPanels(canvas);
        super.onDraw(canvas);
    }

    private final android.graphics.Paint panelPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
    private final android.graphics.RectF panelRect = new android.graphics.RectF();

    /**
     * Callout and code panels are drawn here, before TextView clips to its text column and draws the text.
     * They reach PANEL_EXT past the column on each side, which a span's own drawBackground cannot do.
     */
    private void drawPanels(android.graphics.Canvas c) {
        android.text.Layout lay = getLayout();
        CharSequence t = getText();
        if (lay == null || !(t instanceof android.text.Spanned) || t.length() == 0) return;
        android.text.Spanned sp = (android.text.Spanned) t;
        android.graphics.Rect clip = c.getClipBounds();
        int pt = getTotalPaddingTop();
        int vTop = Math.max(0, clip.top - pt);
        int vBot = clip.bottom - pt;
        if (vBot < 0) return;
        int from = lay.getLineStart(lay.getLineForVertical(vTop));
        int to = lay.getLineEnd(lay.getLineForVertical(Math.max(vTop, vBot)));
        int ext = RichText.panelExt();
        float left = getCompoundPaddingLeft() - ext;
        float right = getWidth() - getCompoundPaddingRight() + ext;
        Object[][] groups = {sp.getSpans(from, to, RichText.Callout.class), sp.getSpans(from, to, RichText.Block.class)};
        for (Object[] group : groups) {
            for (Object span : group) {
                int s = sp.getSpanStart(span);
                int e = sp.getSpanEnd(span);
                if (s < 0 || e <= s) continue;
                int l0 = lay.getLineForOffset(s);
                int l1 = lay.getLineForOffset(Math.max(s, e - 1));
                panelRect.set(left, pt + lay.getLineTop(l0), right, pt + lay.getLineBottom(l1));
                RichText.paintPanel(c, panelPaint, span, panelRect);
            }
        }
    }

    @Override
    protected void onSelectionChanged(int selStart, int selEnd) {
        super.onSelectionChanged(selStart, selEnd);
        if (listener != null) listener.onSelection(selStart, selEnd);
    }
}
