package app.privatetodo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

/**
 * Thick, fully rounded volume bar in the style of Samsung One UI: a wide pill track, filled with the
 * accent colour up to the current level, with a speaker icon inside. Drag anywhere on it. 0 to 100.
 */
final class PillSlider extends View {
    interface Listener {
        void onChange(int value);

        void onStart();

        void onStop();
    }

    private final float d;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final RectF rect = new RectF();
    private int value = 100;
    private Listener listener;

    PillSlider(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        setFocusable(true);
        paint.setStrokeCap(Paint.Cap.ROUND);
    }

    void setListener(Listener l) {
        listener = l;
    }

    int getValue() {
        return value;
    }

    void setValue(int v) {
        value = Math.max(0, Math.min(100, v));
        invalidate();
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        setMeasuredDimension(w, Math.round(48 * d)); // 48 dp tall: a chunky, easy-to-grab bar
    }

    /** White fill in dark mode, near-black in light mode. */
    private static int fillColor() {
        return Pal.dark ? 0xFFFFFFFF : 0xFF0E1513;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        float r = h / 2f;
        rect.set(0, 0, w, h);
        clip.reset();
        clip.addRoundRect(rect, r, r, Path.Direction.CW);

        canvas.save();
        canvas.clipPath(clip);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Pal.chip);
        canvas.drawRect(0, 0, w, h, paint);
        paint.setColor(fillColor());
        canvas.drawRect(0, 0, w * value / 100f, h, paint);
        canvas.restore();

        // Speaker icon, dark on the filled part and muted on the empty part.
        float x = 18 * d;
        float cy = h / 2f;
        boolean onFill = w * value / 100f > x + 10 * d;
        int ic = onFill ? (Pal.dark ? 0xFF111513 : 0xFFFFFFFF) : Pal.muted;
        paint.setColor(ic);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawRect(x, cy - 3.2f * d, x + 4 * d, cy + 3.2f * d, paint);
        clip.reset();
        clip.moveTo(x + 4 * d, cy - 3.2f * d);
        clip.lineTo(x + 9.5f * d, cy - 7 * d);
        clip.lineTo(x + 9.5f * d, cy + 7 * d);
        clip.lineTo(x + 4 * d, cy + 3.2f * d);
        clip.close();
        canvas.drawPath(clip, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.8f * d);
        if (value > 0) {
            rect.set(x + 7 * d, cy - 5 * d, x + 17 * d, cy + 5 * d);
            canvas.drawArc(rect, -50, 100, false, paint);
        }
        if (value > 50) {
            rect.set(x + 5 * d, cy - 9 * d, x + 21 * d, cy + 9 * d);
            canvas.drawArc(rect, -50, 100, false, paint);
        }
        if (value == 0) { // muted: a slash
            canvas.drawLine(x + 13 * d, cy - 5 * d, x + 20 * d, cy + 5 * d, paint);
            canvas.drawLine(x + 20 * d, cy - 5 * d, x + 13 * d, cy + 5 * d, paint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                if (listener != null) listener.onStart();
                moveTo(e.getX());
                return true;
            case MotionEvent.ACTION_MOVE:
                moveTo(e.getX());
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (listener != null) listener.onStop();
                performClick();
                return true;
            default:
                return super.onTouchEvent(e);
        }
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private void moveTo(float x) {
        int v = Math.round(Math.max(0f, Math.min(1f, x / Math.max(1, getWidth()))) * 100f);
        if (v != value) {
            value = v;
            invalidate();
            if (listener != null) listener.onChange(v);
        }
    }

    // TalkBack: announce as a range and allow +/- steps.
    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setRangeInfo(AccessibilityNodeInfo.RangeInfo.obtain(
                AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT, 0, 100, value));
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
    }

    @Override
    public boolean performAccessibilityAction(int action, Bundle args) {
        if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                || action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) {
            int v = Math.max(0, Math.min(100,
                    value + (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD ? 5 : -5)));
            if (v != value) {
                value = v;
                invalidate();
                if (listener != null) {
                    listener.onChange(v);
                    listener.onStop();
                }
            }
            return true;
        }
        return super.performAccessibilityAction(action, args);
    }
}
