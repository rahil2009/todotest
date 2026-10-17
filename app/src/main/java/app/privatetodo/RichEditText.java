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
            if (k >= RichText.P_CHECK_OFF) {
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
            boolean ok = k >= RichText.P_CHECK_OFF && e.getSpanEnd(o) == st + 1 && o.on == (k == RichText.P_CHECK_ON);
            if (!ok) e.removeSpan(o);
        }
    }

    interface TableTapListener {
        void onTableTap(RichText.Tbl t);
    }

    private TableTapListener tableListener;
    private Runnable changedListener;

    void setTableChangedListener(Runnable r) {
        changedListener = r;
    }

    void setTableTapListener(TableTapListener l) {
        tableListener = l;
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        RichText.tableWidth = Math.max(0, w - getPaddingLeft() - getPaddingRight());
    }

    /** A tap on a table block opens its editor. */
    private boolean tapOnTable(float x, float y) {
        android.text.Layout l = getLayout();
        if (l == null || tableListener == null) return false;
        int line = l.getLineForVertical((int) (y - getTotalPaddingTop() + getScrollY()));
        RichText.Tbl t = RichText.tableOnLine(getText(), l.getLineStart(line));
        if (t == null) return false;
        // a tap on a tick box inside the table flips it right here
        float xl = x - getTotalPaddingLeft() + getScrollX();
        float yl = y - (l.getLineTop(line) + getTotalPaddingTop() - getScrollY());
        if (t.toggleAt(xl, yl, getPaint())) {
            RichText.refresh(getText(), t);
            invalidate();
            if (changedListener != null) changedListener.run();
            return true;
        }
        tableListener.onTableTap(t);
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
        boolean handled = super.onTouchEvent(e);
        if (e.getActionMasked() == MotionEvent.ACTION_UP && getLayout() != null
                && Math.abs(e.getX() - downX) < ViewConfiguration.get(getContext()).getScaledTouchSlop()
                && Math.abs(e.getY() - downY) < ViewConfiguration.get(getContext()).getScaledTouchSlop()) {
            if (tapOnTable(e.getX(), e.getY())) return true;
        }
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
                if (off <= ls + 1 && RichText.prefixAt(t, ls) >= RichText.P_CHECK_OFF) {
                    // only the circle (plus a thumb's margin) ticks; the text after it just edits
                    float edge = getTotalPaddingLeft() + getLayout().getPrimaryHorizontal(ls + 1)
                            + 10 * getResources().getDisplayMetrics().density;
                    if (e.getX() <= edge) checkListener.onCheckTap(ls);
                }
            }
        }
        return handled;
    }

    @Override
    protected void onSelectionChanged(int selStart, int selEnd) {
        super.onSelectionChanged(selStart, selEnd);
        if (listener != null) listener.onSelection(selStart, selEnd);
    }
}
