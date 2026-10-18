package app.privatetodo;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

/** Small bell glyph shown before "Alarm" on tasks that ring as an alarm. */
final class BellDrawable extends Drawable {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final int size;

    BellDrawable(int color, int sizePx) {
        size = sizePx;
        p.setColor(color);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        setBounds(0, 0, sizePx, sizePx);
    }

    @Override public int getIntrinsicWidth() { return size; }

    @Override public int getIntrinsicHeight() { return size; }

    @Override
    public void draw(Canvas c) {
        float s = size / 24f;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2.2f * s);
        path.reset();
        path.moveTo(6 * s, 16 * s);
        path.lineTo(6 * s, 11 * s);
        path.cubicTo(6 * s, 7.5f * s, 8.7f * s, 5 * s, 12 * s, 5 * s);
        path.cubicTo(15.3f * s, 5 * s, 18 * s, 7.5f * s, 18 * s, 11 * s);
        path.lineTo(18 * s, 16 * s);
        path.moveTo(4 * s, 16 * s);
        path.lineTo(20 * s, 16 * s);
        c.drawPath(path, p);
        p.setStyle(Paint.Style.FILL);
        c.drawCircle(12 * s, 19.8f * s, 1.5f * s, p);
    }

    @Override public void setAlpha(int a) { p.setAlpha(a); }

    @Override public void setColorFilter(ColorFilter cf) { p.setColorFilter(cf); }

    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
