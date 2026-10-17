package app.privatetodo;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/** Small "repeat" glyph (two chasing arrows) shown on recurring tasks. */
final class RepeatDrawable extends Drawable {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path head = new Path();
    private final RectF r = new RectF();
    private final int size;

    RepeatDrawable(int color, int sizePx) {
        size = sizePx;
        p.setColor(color);
        p.setStrokeCap(Paint.Cap.ROUND);
        setBounds(0, 0, sizePx, sizePx);
    }

    @Override
    public int getIntrinsicWidth() {
        return size;
    }

    @Override
    public int getIntrinsicHeight() {
        return size;
    }

    @Override
    public void draw(Canvas c) {
        float cx = size / 2f;
        float cy = size / 2f;
        float rad = size * 0.30f;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(size * 0.11f);
        r.set(cx - rad, cy - rad, cx + rad, cy + rad);
        c.drawArc(r, 205, 130, false, p);
        c.drawArc(r, 25, 130, false, p);
        p.setStyle(Paint.Style.FILL);
        arrow(c, cx, cy, rad, 335);
        arrow(c, cx, cy, rad, 155);
    }

    private void arrow(Canvas c, float cx, float cy, float rad, float deg) {
        double a = Math.toRadians(deg);
        float px = cx + (float) Math.cos(a) * rad;
        float py = cy + (float) Math.sin(a) * rad;
        float tx = (float) -Math.sin(a); // clockwise tangent
        float ty = (float) Math.cos(a);
        float nx = (float) Math.cos(a);
        float ny = (float) Math.sin(a);
        float h = size * 0.20f;
        float w = size * 0.17f;
        head.reset();
        head.moveTo(px + tx * h, py + ty * h);
        head.lineTo(px + nx * w, py + ny * w);
        head.lineTo(px - nx * w, py - ny * w);
        head.close();
        c.drawPath(head, p);
    }

    @Override
    public void setAlpha(int alpha) {
        p.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        p.setColorFilter(cf);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
