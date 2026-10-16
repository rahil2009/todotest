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

    void setSelectionListener(SelectionListener l) {
        listener = l;
    }

    void setCheckTapListener(CheckTapListener l) {
        checkListener = l;
    }

    /** A tap on a to-do box (the first characters of a "\u2610 " / "\u2611 " line) ticks it. */
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        boolean handled = super.onTouchEvent(e);
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
            downX = e.getX();
            downY = e.getY();
        } else if (e.getActionMasked() == MotionEvent.ACTION_UP && checkListener != null) {
            int slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
            float boxWidth = 44 * getResources().getDisplayMetrics().density;
            if (Math.abs(e.getX() - downX) < slop && Math.abs(e.getY() - downY) < slop
                    && e.getX() < getTotalPaddingLeft() + boxWidth) {
                int off = getOffsetForPosition(e.getX(), e.getY());
                CharSequence t = getText();
                int ls = RichText.lineStart(t, off);
                if (off <= ls + 1 && RichText.prefixAt(t, ls) >= RichText.P_CHECK_OFF) {
                    checkListener.onCheckTap(ls);
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
