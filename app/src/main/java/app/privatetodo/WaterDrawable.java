package app.privatetodo;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;

import java.util.HashMap;
import java.util.Map;

/**
 * Live water for the "Drink water" habit: three layers of waves that keep rolling, a lighter surface line,
 * little bubbles drifting up, and a level that rises (with a slosh) as the day's total climbs to the goal.
 * Everything is vector drawing, so it stays crisp at any size. It keeps itself moving by asking for the next
 * frame from draw(), so it runs exactly while it is on screen and stops by itself when it is not.
 */
final class WaterDrawable extends Drawable {
    private static final Map<Long, Float> SHOWN = new HashMap<>();
    private static final int BUBBLES = 9;
    /** The water is always blue, whatever colour the habit itself has. */
    static final int BLUE = 0xFF2F9BFF;

    private final float d;
    private final float radius;
    private final int rgb;
    private final float from;
    private final float to;
    private final long t0 = SystemClock.uptimeMillis();
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final Path wave = new Path();
    private final Path line = new Path();
    private final RectF r = new RectF();
    private Shader fill;

    /** @param level today's amount divided by the goal (0 to 1) */
    WaterDrawable(float density, float radiusDp, int color, long habitId, float level) {
        this.d = density;
        this.radius = radiusDp * density;
        this.rgb = BLUE & 0xFFFFFF; // the habit's own colour is ignored: water is blue
        this.to = Math.max(0f, Math.min(1f, level));
        Float prev = SHOWN.get(habitId);
        this.from = prev == null ? 0f : prev;
        SHOWN.put(habitId, this.to);
    }

    /** 0..1 progress of the rise, eased out. */
    private float rise() {
        float k = Math.min(1f, (SystemClock.uptimeMillis() - t0) / 1300f);
        return 1f - (1f - k) * (1f - k) * (1f - k);
    }

    private int col(int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | rgb;
    }

    @Override
    protected void onBoundsChange(android.graphics.Rect b) {
        r.set(b);
        clip.reset();
        clip.addRoundRect(r, radius, radius, Path.Direction.CW);
        fill = new LinearGradient(0, r.top, 0, r.bottom, col(0x58), col(0xA6), Shader.TileMode.CLAMP);
    }

    @Override
    public void draw(Canvas c) {
        if (r.isEmpty()) return;
        float k = rise();
        float lv = from + (to - from) * k;
        float t = (SystemClock.uptimeMillis() - t0) / 1000f;
        // While the level is moving the water sloshes harder, then settles into a calm roll.
        float slosh = 1f + 0.9f * (1f - k) * Math.min(1f, Math.abs(to - from) * 6f);
        float amp = (5.5f * d) * Math.min(1f, lv * 10f) * slosh;
        float h = r.height();
        float y0 = r.bottom + amp - lv * (h + 2 * amp);
        if (lv > 0.001f) {
            int save = c.save();
            c.clipPath(clip);
            p.setStyle(Paint.Style.FILL);
            p.setShader(fill);
            // back, middle, front layers: different length, speed, direction and height
            float[] len = {70f, 52f, 38f};
            float[] spd = {0.9f, -1.35f, 1.9f};
            float[] mul = {0.55f, 0.75f, 1f};
            float[] off = {-3.5f, -1.5f, 0f};
            float[] ph = {0f, 2.1f, 4.3f};
            float frontY0 = y0;
            for (int i = 0; i < 3; i++) {
                float base = y0 + off[i] * d;
                float a = amp * (i == 1 ? 0.8f : i == 2 ? 0.6f : 1f);
                wave.reset();
                wave.moveTo(r.left - 2 * d, r.bottom + 2 * d);
                float step = 3f * d;
                for (float x = -2 * d; x <= r.width() + 2 * d + step; x += step) {
                    float y = base + (float) Math.sin(x / (len[i] * d) * 2f + t * spd[i] + ph[i]) * a
                            + (float) Math.sin(x / (len[i] * d * 0.45f) - t * spd[i] * 0.6f + ph[i]) * a * 0.22f;
                    wave.lineTo(r.left + x, y);
                }
                wave.lineTo(r.right + 2 * d, r.bottom + 2 * d);
                wave.close();
                p.setAlpha(Math.round(255 * mul[i]));
                c.drawPath(wave, p);
            }
            p.setShader(null);

            // bright surface line along the front wave
            if (lv > 0.01f) {
                Path line = this.line;
                line.reset();
                float step = 3f * d;
                boolean first = true;
                for (float x = -2 * d; x <= r.width() + 2 * d + step; x += step) {
                    float y = frontY0 + (float) Math.sin(x / (len[2] * d) * 2f + t * spd[2] + ph[2]) * amp * 0.6f
                            + (float) Math.sin(x / (len[2] * d * 0.45f) - t * spd[2] * 0.6f + ph[2]) * amp * 0.6f * 0.22f;
                    if (first) {
                        line.moveTo(r.left + x, y);
                        first = false;
                    } else {
                        line.lineTo(r.left + x, y);
                    }
                }
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(1.6f * d);
                p.setStrokeCap(Paint.Cap.ROUND);
                p.setColor(0x66FFFFFF);
                c.drawPath(line, p);
            }

            // bubbles rising through the water
            if (lv > 0.06f) {
                p.setStyle(Paint.Style.STROKE);
                float depth = r.bottom - (y0 + amp);
                for (int i = 0; i < BUBBLES; i++) {
                    float seed = (i * 0.6180339f) % 1f;
                    float period = 4.2f + 3.4f * ((i * 0.37f) % 1f);
                    float q = ((t / period) + seed) % 1f;
                    float x = r.left + r.width() * (0.06f + 0.88f * ((i * 0.2718f + 0.13f) % 1f))
                            + (float) Math.sin(t * 1.3f + i * 1.7f) * 5f * d;
                    float y = r.bottom - 6 * d - q * (depth - 6 * d);
                    float rad = (2f + 3.2f * ((i * 0.53f) % 1f)) * d * (0.7f + 0.5f * q);
                    float fade = q < 0.12f ? q / 0.12f : q > 0.85f ? (1f - q) / 0.15f : 1f;
                    if (y < y0 + amp + rad) continue;
                    p.setStrokeWidth(1.2f * d);
                    p.setColor(((int) (0x88 * fade) << 24) | 0xFFFFFF);
                    c.drawCircle(x, y, rad, p);
                    p.setColor(((int) (0xAA * fade) << 24) | 0xFFFFFF);
                    c.drawArc(x - rad * 0.6f, y - rad * 0.6f, x + rad * 0.6f, y + rad * 0.6f, 200, 55, false, p);
                }
            }
            c.restoreToCount(save);
        }
        if (lv > 0.001f || k < 1f) invalidateSelf(); // the next frame: this is what keeps the water moving
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
