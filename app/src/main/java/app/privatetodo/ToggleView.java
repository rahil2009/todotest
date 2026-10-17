package app.privatetodo;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

/** The design's switch: 52x32 pill track with a 24dp knob. */
final class ToggleView extends View {
    interface Listener {
        void onChange(boolean on);
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float d;
    private boolean on;
    private float pos; // 0 = off, 1 = on
    private Listener listener;

    ToggleView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        setClickable(true);
        setOnClickListener(v -> {
            Hap.tick(this);
            set(!on, true);
            if (listener != null) listener.onChange(on);
        });
    }

    void setListener(Listener l) {
        listener = l;
    }

    boolean isOn() {
        return on;
    }

    void set(boolean value, boolean animate) {
        on = value;
        if (animate && isAttachedToWindow()) {
            ValueAnimator a = ValueAnimator.ofFloat(pos, on ? 1f : 0f);
            a.setDuration(140);
            a.addUpdateListener(an -> {
                pos = (float) an.getAnimatedValue();
                invalidate();
            });
            a.start();
        } else {
            pos = on ? 1f : 0f;
            invalidate();
        }
    }

    @Override
    protected void onMeasure(int w, int h) {
        setMeasuredDimension(Math.round(52 * d), Math.round(32 * d));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int off = Pal.chip;
        int onC = Pal.accent;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(blend(off, onC, pos));
        rect.set(0, 0, 52 * d, 32 * d);
        canvas.drawRoundRect(rect, 16 * d, 16 * d, paint);
        if (pos < 0.99f) { // thin outline while off so the track reads on light cards
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.5f * d);
            paint.setColor(blend(Pal.muted, onC, pos));
            rect.set(0.75f * d, 0.75f * d, 51.25f * d, 31.25f * d);
            canvas.drawRoundRect(rect, 15.25f * d, 15.25f * d, paint);
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(blend(Pal.muted, Pal.onAccent, pos));
        float r = (10 + 2 * pos) * d; // knob grows a little when on, like the design
        float cx = (16 + 20 * pos) * d;
        canvas.drawCircle(cx, 16 * d, r, paint);
    }

    private static int blend(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setCheckable(true);
        info.setChecked(on);
        info.setClassName("android.widget.Switch");
    }
}
