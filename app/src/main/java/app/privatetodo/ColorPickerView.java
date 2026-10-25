package app.privatetodo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.ComposeShader;
import android.graphics.PorterDuff;
import android.view.MotionEvent;
import android.view.View;

/** A small colour picker: a saturation / brightness square over a hue bar. Drawn with plain graphics, no library. */
final class ColorPickerView extends View {
    interface Listener {
        void onColor(int color);
    }

    private final float d;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF sv = new RectF();
    private final RectF hue = new RectF();
    private final float[] hsv = {210f, 0.8f, 0.8f};
    private Listener listener;
    private int drag; // 1 = square, 2 = hue bar
    private Shader hueShader;
    private Shader svShader;
    private float shaderHue = -1f;

    ColorPickerView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
    }

    void setListener(Listener l) {
        listener = l;
    }

    void setColor(int color) {
        Color.colorToHSV(color, hsv);
        invalidate();
    }

    int getColor() {
        return Color.HSVToColor(hsv);
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        if (MeasureSpec.getMode(wSpec) == MeasureSpec.UNSPECIFIED) w = Math.round(280 * d);
        setMeasuredDimension(w, Math.round(w * 0.72f) + Math.round(46 * d));
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        float pad = 12 * d;
        float barH = 26 * d;
        sv.set(pad, pad, w - pad, h - pad * 2 - barH);
        hue.set(pad, h - pad - barH, w - pad, h - pad);
        int[] cols = new int[7];
        for (int i = 0; i < 7; i++) cols[i] = Color.HSVToColor(new float[]{i * 60f, 1f, 1f});
        hueShader = new LinearGradient(hue.left, 0, hue.right, 0, cols, null, Shader.TileMode.CLAMP);
        shaderHue = -1f;
    }

    @Override
    protected void onDraw(Canvas c) {
        if (hueShader == null) return;
        if (shaderHue != hsv[0]) {
            shaderHue = hsv[0];
            Shader sat = new LinearGradient(sv.left, 0, sv.right, 0, Color.WHITE,
                    Color.HSVToColor(new float[]{hsv[0], 1f, 1f}), Shader.TileMode.CLAMP);
            Shader val = new LinearGradient(0, sv.top, 0, sv.bottom, Color.WHITE, Color.BLACK, Shader.TileMode.CLAMP);
            svShader = new ComposeShader(sat, val, PorterDuff.Mode.MULTIPLY);
        }
        p.setStyle(Paint.Style.FILL);
        p.setShader(svShader);
        c.drawRoundRect(sv, 10 * d, 10 * d, p);
        p.setShader(hueShader);
        c.drawRoundRect(hue, hue.height() / 2, hue.height() / 2, p);
        p.setShader(null);

        float x = sv.left + hsv[1] * sv.width();
        float y = sv.top + (1f - hsv[2]) * sv.height();
        marker(c, x, y, getColor());
        float hx = hue.left + hsv[0] / 360f * hue.width();
        marker(c, hx, hue.centerY(), Color.HSVToColor(new float[]{hsv[0], 1f, 1f}));
    }

    private void marker(Canvas c, float x, float y, int fill) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(fill);
        c.drawCircle(x, y, 11 * d, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(3 * d);
        p.setColor(Color.WHITE);
        c.drawCircle(x, y, 11 * d, p);
        p.setStrokeWidth(1 * d);
        p.setColor(0x66000000);
        c.drawCircle(x, y, 13 * d, p);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX();
        float y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                drag = sv.contains(x, y) ? 1 : (y > sv.bottom ? 2 : 0);
                if (drag == 0) return false;
                getParent().requestDisallowInterceptTouchEvent(true);
                break;
            case MotionEvent.ACTION_MOVE:
                break;
            default:
                drag = 0;
                return true;
        }
        if (drag == 1) {
            hsv[1] = Math.max(0f, Math.min(1f, (x - sv.left) / sv.width()));
            hsv[2] = 1f - Math.max(0f, Math.min(1f, (y - sv.top) / sv.height()));
        } else if (drag == 2) {
            hsv[0] = Math.max(0f, Math.min(359.9f, (x - hue.left) / hue.width() * 360f));
        }
        invalidate();
        if (listener != null) listener.onColor(getColor());
        return true;
    }
}
