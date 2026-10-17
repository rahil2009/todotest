package app.privatetodo;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/** Lays children out left to right and wraps to a new line when a row is full. */
final class FlowLayout extends ViewGroup {
    private final int hGap;
    private final int vGap;

    FlowLayout(Context c, int hGapPx, int vGapPx) {
        super(c);
        hGap = hGapPx;
        vGap = vGapPx;
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int maxW = MeasureSpec.getSize(wSpec) - getPaddingLeft() - getPaddingRight();
        int x = 0;
        int y = 0;
        int lineH = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View ch = getChildAt(i);
            if (ch.getVisibility() == GONE) continue;
            ch.measure(MeasureSpec.makeMeasureSpec(maxW, MeasureSpec.AT_MOST),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            int w = ch.getMeasuredWidth();
            int h = ch.getMeasuredHeight();
            if (x > 0 && x + w > maxW) {
                x = 0;
                y += lineH + vGap;
                lineH = 0;
            }
            x += w + hGap;
            lineH = Math.max(lineH, h);
        }
        int total = y + lineH + getPaddingTop() + getPaddingBottom();
        setMeasuredDimension(MeasureSpec.getSize(wSpec), resolveSize(total, hSpec));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int maxW = r - l - getPaddingLeft() - getPaddingRight();
        int x = 0;
        int y = 0;
        int lineH = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View ch = getChildAt(i);
            if (ch.getVisibility() == GONE) continue;
            int w = ch.getMeasuredWidth();
            int h = ch.getMeasuredHeight();
            if (x > 0 && x + w > maxW) {
                x = 0;
                y += lineH + vGap;
                lineH = 0;
            }
            int left = getPaddingLeft() + x;
            int top = getPaddingTop() + y;
            ch.layout(left, top, left + w, top + h);
            x += w + hGap;
            lineH = Math.max(lineH, h);
        }
    }
}
