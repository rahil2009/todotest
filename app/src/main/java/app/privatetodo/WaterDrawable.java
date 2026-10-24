package app.privatetodo;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;

import java.util.HashMap;
import java.util.Map;

/**
 * Water for the "Drink water" habit: a card background that fills up with gently moving waves as the day's
 * total climbs to the goal. It remembers the last level it showed per habit, so each tap makes it rise.
 */
final class WaterDrawable extends Drawable implements Runnable {
    private static final Map<Long, Float> SHOWN = new HashMap<>();

    private final float d;
    private final float radius;
    private final int rgb;
    private final float from;
    private final float to;
    private final long t0 = SystemClock.uptimeMillis();
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final Path wave = new Path();
    private final RectF r = new RectF();
    private boolean running;

    /** @param level today's amount divided by the goal (0 to 1) */
    WaterDrawable(float density, float radiusDp, int color, long habitId, float level) {
        this.d = density;
        this.radius = radiusDp * density;
        this.rgb = color & 0xFFFFFF;
        this.to = Math.max(0f, Math.min(1f, level));
        Float prev = SHOWN.get(habitId);
        this.from = prev == null ? 0f : prev;
        SHOWN.put(habitId, this.to);
    }

    private float level() {
        float k = Math.min(1f, (SystemClock.uptimeMillis() - t0) / 1100f);
        float e = 1f - (1f - k) * (1f - k) * (1f - k);
        return from + (to - from) * e;
    }

    private int col(int alpha) {
        return (alpha << 24) | rgb;
    }

    @Override
    protected void onBoundsChange(android.graphics.Rect b) {
        r.set(b);
        clip.reset();
        clip.addRoundRect(r, radius, radius, Path.Direction.CW);
    }

    @Override
    public void draw(Canvas c) {
        if (r.isEmpty()) return;
        float lv = level();
        if (lv <= 0.002f) return;
        float t = (SystemClock.uptimeMillis() - t0) / 1000f;
        float h = r.height();
        float amp = 4.5f * d * Math.min(1f, lv * 8f) * (lv > 0.995f ? 0.5f : 1f);
        int save = c.save();
        c.clipPath(clip);
        p.setStyle(Paint.Style.FILL);
        for (int k = 0; k < 2; k++) {
            float top = r.bottom - h * lv - (k == 0 ? 0 : 2 * d);
            wave.reset();
            wave.moveTo(r.left, r.bottom);
            for (float x = 0; x <= r.width() + 6 * d; x += 6 * d) {
                float y = top + (float) Math.sin(x / (46 * d) + t * (1.5f + 0.5f * k) * (k == 0 ? 1 : -1)) * amp;
                wave.lineTo(r.left + x, y);
            }
            wave.lineTo(r.right, r.bottom);
            wave.close();
            p.setColor(col(k == 0 ? 0x4D : 0x2E));
            c.drawPath(wave, p);
        }
        c.restoreToCount(save);
    }

    @Override
    public boolean setVisible(boolean visible, boolean restart) {
        boolean changed = super.setVisible(visible, restart);
        if (visible) {
            if (!running) {
                running = true;
                scheduleSelf(this, SystemClock.uptimeMillis() + 33);
            }
        } else {
            running = false;
            unscheduleSelf(this);
        }
        return changed;
    }

    @Override
    public void run() {
        if (!running) return;
        invalidateSelf();
        scheduleSelf(this, SystemClock.uptimeMillis() + 33);
    }

    @Override
    public void setAlpha(int alpha) {
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
