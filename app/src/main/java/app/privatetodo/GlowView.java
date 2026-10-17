package app.privatetodo;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * Edge-lighting glow like the one on Samsung's reminder screen: a soft multi-colour ring hugging the
 * screen edge that slowly travels around while it breathes. Several strokes of growing width and falling
 * alpha stand in for a blur, so it stays cheap to draw. It never takes touches.
 */
final class GlowView extends View {
    static final int[] COLORS = {
            0xFF3DDCC6, 0xFF4F8CFF, 0xFFB06BFF, 0xFFFF6BB5, 0xFFFFB86B, 0xFF3DDCC6};

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Matrix turn = new Matrix();
    private final float d;
    private SweepGradient sweep;
    private float angle;   // 0..360
    private float breath;  // 0..1
    private ValueAnimator spin;
    private ValueAnimator pulse;

    GlowView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        setClickable(false);
        setFocusable(false);
        paint.setStyle(Paint.Style.STROKE);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        spin = ValueAnimator.ofFloat(0f, 360f);
        spin.setDuration(7000);
        spin.setInterpolator(new LinearInterpolator());
        spin.setRepeatCount(ValueAnimator.INFINITE);
        spin.addUpdateListener(a -> {
            angle = (Float) a.getAnimatedValue();
            invalidate();
        });
        spin.start();
        pulse = ValueAnimator.ofFloat(0f, 1f);
        pulse.setDuration(2200);
        pulse.setRepeatMode(ValueAnimator.REVERSE);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.addUpdateListener(a -> breath = (Float) a.getAnimatedValue());
        pulse.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (spin != null) spin.cancel();
        if (pulse != null) pulse.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        sweep = new SweepGradient(w / 2f, h / 2f, COLORS, null);
    }

    @Override
    protected void onDraw(Canvas c) {
        if (sweep == null) return;
        turn.setRotate(angle, getWidth() / 2f, getHeight() / 2f);
        sweep.setLocalMatrix(turn);
        paint.setShader(sweep);
        float corner = 40 * d;
        float strength = 0.65f + 0.35f * breath;
        // outer halo first, crisp bright line last
        int layers = 9;
        for (int i = 0; i < layers; i++) {
            float t = i / (float) (layers - 1);            // 0 = widest/faintest, 1 = thinnest/brightest
            float width = (2f + (1f - t) * 26f) * d;
            paint.setStrokeWidth(width);
            paint.setAlpha(Math.round(255 * strength * (0.05f + 0.30f * t * t)));
            float inset = width / 2f;
            rect.set(inset, inset, getWidth() - inset, getHeight() - inset);
            c.drawRoundRect(rect, corner, corner, paint);
        }
        paint.setStrokeWidth(2.5f * d);
        paint.setAlpha(Math.round(255 * (0.75f + 0.25f * breath)));
        rect.set(1.25f * d, 1.25f * d, getWidth() - 1.25f * d, getHeight() - 1.25f * d);
        c.drawRoundRect(rect, corner, corner, paint);
        paint.setShader(null);
    }
}
