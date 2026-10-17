package app.privatetodo;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * A small pill that says "Undo". The whole pill is filled like the volume slider and drains smoothly
 * to empty over a few seconds: white fill in dark mode, near-black in light mode.
 */
final class UndoPill extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final RectF rect = new RectF();
    private final float d;
    private float progress; // 1 = full, 0 = empty
    private ValueAnimator anim;

    UndoPill(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        setClickable(true);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setTextSize(16 * d);
        paint.setTextAlign(Paint.Align.CENTER);
    }

    /** Fills the pill and drains it linearly over {@code ms}. */
    void start(long ms) {
        stop();
        progress = 1f;
        anim = ValueAnimator.ofFloat(1f, 0f);
        anim.setDuration(ms);
        anim.setInterpolator(new LinearInterpolator());
        anim.addUpdateListener(a -> {
            progress = (float) a.getAnimatedValue();
            invalidate();
        });
        anim.start();
        invalidate();
    }

    void stop() {
        if (anim != null) anim.cancel();
        anim = null;
    }

    @Override
    protected void onMeasure(int w, int h) {
        setMeasuredDimension(Math.round(116 * d), Math.round(48 * d));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        float r = h / 2f;
        rect.set(0, 0, w, h);
        clip.reset();
        clip.addRoundRect(rect, r, r, Path.Direction.CW);
        int fill = Pal.dark ? 0xFFFFFFFF : 0xFF0E1513;
        int onFill = Pal.dark ? 0xFF111513 : 0xFFFFFFFF;
        float split = w * progress;
        float ty = h / 2f - (paint.descent() + paint.ascent()) / 2f;

        canvas.save();
        canvas.clipPath(clip);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Pal.raised);
        canvas.drawRect(0, 0, w, h, paint);
        paint.setColor(fill);
        canvas.drawRect(0, 0, split, h, paint);

        canvas.save();
        canvas.clipRect(0, 0, split, h);
        paint.setColor(onFill);
        canvas.drawText("Undo", w / 2f, ty, paint);
        canvas.restore();

        canvas.save();
        canvas.clipRect(split, 0, w, h);
        paint.setColor(Pal.ink);
        canvas.drawText("Undo", w / 2f, ty, paint);
        canvas.restore();
        canvas.restore();
    }
}
