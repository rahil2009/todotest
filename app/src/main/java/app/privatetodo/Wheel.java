package app.privatetodo;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;

/**
 * Swipe-up/down dial. Replaces the platform NumberPicker so the rule is simple and the same for every
 * dial: whichever value sits in the centre band is drawn bold and full-colour, the rest are lighter.
 */
final class Wheel extends View {
    interface Listener {
        void onChange(Wheel w, int oldValue, int newValue);
    }

    private static final int ROWS = 5;

    private final float density;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint band = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final int slop;
    private final float rowH;

    private int min;
    private int max = 1;
    private String[] labels;
    private boolean wrap = true;
    private float pos; // continuous index into 0..count-1
    private int lastIdx;
    private Listener listener;
    private ValueAnimator anim;
    private VelocityTracker vt;
    private float downY;
    private float lastY;
    private boolean dragging;
    private final Typeface regular;
    private final Typeface bold;

    Wheel(Context c) {
        super(c);
        density = c.getResources().getDisplayMetrics().density;
        rowH = 36 * density;
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
        Fonts.init(c);
        regular = Fonts.face(false);
        bold = Fonts.face(true);
        band.setColor(Pal.accent);
        setFocusable(false);
    }

    // ------------------------------------------------------------ API like NumberPicker

    void setMinValue(int v) {
        min = v;
    }

    void setMaxValue(int v) {
        max = v;
    }

    void setDisplayedValues(String[] l) {
        labels = l;
    }

    void setWrapSelectorWheel(boolean w) {
        wrap = w;
    }

    void setOnValueChangedListener(Listener l) {
        listener = l;
    }

    int getValue() {
        return min + lastIdx;
    }

    /** Sets the value without calling the listener. */
    void setValue(int v) {
        stop();
        int idx = Math.max(0, Math.min(count() - 1, v - min));
        pos = idx;
        lastIdx = idx;
        invalidate();
    }

    private int count() {
        return Math.max(1, max - min + 1);
    }

    private String label(int idx) {
        int v = min + idx;
        if (labels != null) {
            int k = idx;
            return k >= 0 && k < labels.length ? labels[k] : "";
        }
        return String.valueOf(v);
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void onMeasure(int w, int h) {
        setMeasuredDimension(resolveSize(Math.round(80 * density), w), Math.round(rowH * ROWS));
    }

    @Override
    protected void onDraw(Canvas c) {
        float cy = getHeight() / 2f;
        float cx = getWidth() / 2f;
        // two accent lines frame the centre value, as in the design
        float lx0 = 16 * density;
        float lx1 = getWidth() - 16 * density;
        float lh = 2 * density;
        c.drawRect(lx0, cy - rowH / 2f - lh / 2f, lx1, cy - rowH / 2f + lh / 2f, band);
        c.drawRect(lx0, cy + rowH / 2f - lh / 2f, lx1, cy + rowH / 2f + lh / 2f, band);

        int n = count();
        int base = Math.round(pos);
        paint.setTextAlign(Paint.Align.CENTER);
        for (int k = -ROWS / 2 - 1; k <= ROWS / 2 + 1; k++) {
            int idx = base + k;
            if (!wrap && (idx < 0 || idx >= n)) continue;
            int real = ((idx % n) + n) % n;
            float d = idx - pos; // distance from the centre, in rows
            float ad = Math.abs(d);
            boolean centre = ad < 0.5f;
            paint.setTypeface(centre ? bold : regular);
            paint.setTextSize((centre ? 22 : 18) * density);
            paint.setColor(centre ? Pal.ink : Pal.muted);
            paint.setAlpha(centre ? 255 : (int) Math.max(40, 200 - ad * 60));
            float y = cy + d * rowH - (paint.ascent() + paint.descent()) / 2f;
            c.drawText(label(real), cx, y, paint);
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                stop();
                getParent().requestDisallowInterceptTouchEvent(true);
                downY = lastY = e.getY();
                dragging = false;
                vt = VelocityTracker.obtain();
                vt.addMovement(e);
                return true;
            case MotionEvent.ACTION_MOVE:
                vt.addMovement(e);
                if (!dragging && Math.abs(e.getY() - downY) > slop) dragging = true;
                if (dragging) {
                    setPos(pos - (e.getY() - lastY) / rowH);
                    lastY = e.getY();
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                vt.addMovement(e);
                vt.computeCurrentVelocity(1000);
                float v = -vt.getYVelocity() / rowH; // rows per second
                vt.recycle();
                vt = null;
                if (!dragging && e.getActionMasked() == MotionEvent.ACTION_UP) {
                    int delta = Math.round((e.getY() - getHeight() / 2f) / rowH);
                    settle(Math.round(pos) + delta);
                } else {
                    settle(Math.round(pos + v * 0.18f));
                }
                return true;
            default:
                return true;
        }
    }

    private void setPos(float p) {
        if (!wrap) p = Math.max(0, Math.min(count() - 1, p));
        pos = p;
        int n = count();
        int idx = ((Math.round(pos) % n) + n) % n;
        if (idx != lastIdx) {
            int old = lastIdx;
            lastIdx = idx;
            if (listener != null) listener.onChange(this, min + old, min + idx);
        }
        invalidate();
    }

    private void settle(int target) {
        if (!wrap) target = Math.max(0, Math.min(count() - 1, target));
        final float from = pos;
        final float to = target;
        stop();
        anim = ValueAnimator.ofFloat(from, to);
        anim.setDuration(Math.min(450, 140 + (long) (Math.abs(to - from) * 40)));
        anim.setInterpolator(new DecelerateInterpolator());
        anim.addUpdateListener(a -> setPos((Float) a.getAnimatedValue()));
        anim.start();
    }

    private void stop() {
        if (anim != null) {
            anim.cancel();
            anim = null;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }
}
