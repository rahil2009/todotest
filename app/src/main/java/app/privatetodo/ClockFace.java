package app.privatetodo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.format.DateFormat;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * The clock on the full-screen alert. Faces are drawn after the lock-screen clocks of current phone systems
 * (layout, weight, stacking, colour of the digits only), using the phone's own fonts. Updates every minute.
 */
final class ClockFace extends View {
    static final String[] NAMES = {"Classic",
            "iOS · Tall", "iOS · Bold", "One UI · Stacked", "One UI · Thin",
            "ColorOS · Two-tone", "ColorOS · Dial", "OxygenOS · Dot", "OxygenOS · Vertical",
            "HyperOS · Depth", "HyperOS · Magazine"};
    private static final int[] HEIGHT_DP = {104, 196, 150, 232, 146, 236, 196, 128, 300, 250, 190};

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float d;
    private final RectF r = new RectF();
    private int face;
    private float scale = 1f; // smaller in the Settings preview
    private float fit = 1f;   // shrinks the face to the screen: narrow phones, short screens
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            invalidate();
            long now = System.currentTimeMillis();
            postDelayed(this, 60_000L - now % 60_000L + 30);
        }
    };

    ClockFace(Context c, int face) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        this.face = Math.max(0, Math.min(NAMES.length - 1, face));
    }

    static int chosen(Context c) {
        return c.getApplicationContext().getSharedPreferences("settings", Context.MODE_PRIVATE).getInt("clock_face", 0);
    }

    static void setChosen(Context c, int f) {
        c.getApplicationContext().getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putInt("clock_face", f).apply();
    }

    void setFace(int f) {
        face = Math.max(0, Math.min(NAMES.length - 1, f));
        requestLayout();
        invalidate();
    }

    void setScale(float s) {
        scale = s;
        requestLayout();
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        post(tick);
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(tick);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int width = MeasureSpec.getSize(wSpec);
        // drawn for a 304dp-wide column; never wider than the column, never taller than 36% of the screen
        fit = Math.min(1f, width / (304f * d * scale));
        float cap = getResources().getDisplayMetrics().heightPixels * 0.36f;
        float h = HEIGHT_DP[face] * d * scale * fit;
        if (scale == 1f && h > cap) {
            fit *= cap / h;
            h = cap;
        }
        setMeasuredDimension(width, Math.round(h));
    }

    private float sp(float v) {
        return v * d * scale * fit;
    }

    private Typeface tf(String family, int style) {
        return Typeface.create(family, style);
    }

    private void text(Canvas c, String s, float x, float y, float size, Typeface t, int color, Paint.Align a) {
        p.setTextSize(size);
        p.setTypeface(t);
        p.setColor(color);
        p.setTextAlign(a);
        c.drawText(s, x, y, p);
    }

    private static String fmt(String pattern, Date t) {
        return new SimpleDateFormat(pattern, Locale.getDefault()).format(t);
    }

    @Override
    protected void onDraw(Canvas c) {
        Calendar now = Calendar.getInstance();
        Date t = now.getTime();
        boolean h24 = DateFormat.is24HourFormat(getContext());
        int hour = now.get(Calendar.HOUR_OF_DAY);
        String hh = h24 ? (hour < 10 ? "0" + hour : String.valueOf(hour)) : String.valueOf(hour % 12 == 0 ? 12 : hour % 12);
        String hh2 = hh.length() == 1 ? "0" + hh : hh;
        int min = now.get(Calendar.MINUTE);
        String mm = min < 10 ? "0" + min : String.valueOf(min);
        float w = getWidth();
        float cx = w / 2f;
        float hgt = getHeight();
        int white = Color.WHITE;
        int soft = 0xCCFFFFFF;
        p.setLetterSpacing(0f);
        p.setShadowLayer(0, 0, 0, 0);
        p.setStyle(Paint.Style.FILL);
        p.setTextScaleX(1f);
        switch (face) {
            case 1: { // tall glass numerals under a bold date line
                text(c, fmt("EEEE d MMMM", t), cx, sp(26), sp(17), tf("sans-serif-medium", Typeface.BOLD), soft, Paint.Align.CENTER);
                c.save();
                c.scale(1f, 1.38f, cx, hgt);
                p.setLetterSpacing(-0.04f);
                p.setShadowLayer(sp(10), 0, 0, 0x55FFFFFF);
                text(c, hh + ":" + mm, cx, hgt - sp(10) / 1.38f, sp(104), tf("sans-serif-black", Typeface.NORMAL),
                        0xF2FFFFFF, Paint.Align.CENTER);
                c.restore();
                break;
            }
            case 2: { // heavy rounded clock, date above
                text(c, fmt("EEE d MMM", t), cx, sp(24), sp(18), tf("sans-serif-medium", Typeface.BOLD), soft, Paint.Align.CENTER);
                p.setLetterSpacing(-0.03f);
                text(c, hh + ":" + mm, cx, hgt - sp(16), sp(96), tf("sans-serif", Typeface.BOLD), white, Paint.Align.CENTER);
                break;
            }
            case 3: { // hours over minutes, left, minutes lighter
                float x = sp(6);
                p.setLetterSpacing(-0.02f);
                text(c, hh2, x, sp(102), sp(112), tf("sans-serif", Typeface.BOLD), white, Paint.Align.LEFT);
                text(c, mm, x, sp(200), sp(112), tf("sans-serif", Typeface.BOLD), 0x99FFFFFF, Paint.Align.LEFT);
                p.setLetterSpacing(0f);
                text(c, fmt("EEE, d MMMM", t), x + sp(4), sp(226), sp(16), tf("sans-serif-medium", Typeface.NORMAL), soft, Paint.Align.LEFT);
                break;
            }
            case 4: { // large thin clock, date under it
                p.setLetterSpacing(0.01f);
                text(c, hh + ":" + mm, cx, sp(100), sp(104), tf("sans-serif-thin", Typeface.NORMAL), white, Paint.Align.CENTER);
                text(c, fmt("EEE, d MMMM", t), cx, sp(134), sp(17), tf("sans-serif", Typeface.NORMAL), soft, Paint.Align.CENTER);
                break;
            }
            case 5: { // two-tone stacked, centred
                p.setLetterSpacing(-0.03f);
                text(c, hh2, cx, sp(104), sp(118), tf("sans-serif-black", Typeface.NORMAL), white, Paint.Align.CENTER);
                text(c, mm, cx, sp(206), sp(118), tf("sans-serif-black", Typeface.NORMAL), 0xFFFFC56B, Paint.Align.CENTER);
                p.setLetterSpacing(0.05f);
                text(c, fmt("EEEE  d/M", t), cx, sp(230), sp(15), tf("sans-serif-medium", Typeface.NORMAL), soft, Paint.Align.CENTER);
                break;
            }
            case 6: { // analogue dial with a small date
                float rad = Math.min(cx, hgt / 2f) - sp(8);
                float cy = hgt / 2f;
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeCap(Paint.Cap.ROUND);
                for (int i = 0; i < 60; i++) {
                    boolean big = i % 5 == 0;
                    p.setStrokeWidth(big ? sp(3) : sp(1.2f));
                    p.setColor(big ? white : 0x66FFFFFF);
                    double a = Math.PI * 2 * i / 60;
                    float r1 = rad - (big ? sp(12) : sp(6));
                    c.drawLine(cx + (float) Math.sin(a) * r1, cy - (float) Math.cos(a) * r1,
                            cx + (float) Math.sin(a) * rad, cy - (float) Math.cos(a) * rad, p);
                }
                p.setStyle(Paint.Style.FILL);
                text(c, fmt("EEE d", t).toUpperCase(Locale.getDefault()), cx, cy + rad * 0.5f, sp(13),
                        tf("sans-serif-medium", Typeface.BOLD), 0xFFFFC56B, Paint.Align.CENTER);
                p.setStyle(Paint.Style.STROKE);
                float hA = (float) (Math.PI * 2 * ((hour % 12) + min / 60f) / 12);
                float mA = (float) (Math.PI * 2 * min / 60f);
                p.setColor(white);
                p.setStrokeWidth(sp(6));
                c.drawLine(cx, cy, cx + (float) Math.sin(hA) * rad * 0.5f, cy - (float) Math.cos(hA) * rad * 0.5f, p);
                p.setStrokeWidth(sp(4));
                c.drawLine(cx, cy, cx + (float) Math.sin(mA) * rad * 0.78f, cy - (float) Math.cos(mA) * rad * 0.78f, p);
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFFFFC56B);
                c.drawCircle(cx, cy, sp(5), p);
                break;
            }
            case 7: { // bold condensed digits, a red dot for the colon
                Typeface cond = tf("sans-serif-condensed", Typeface.BOLD);
                p.setTextSize(sp(96));
                p.setTypeface(cond);
                float hw = p.measureText(hh);
                float mw = p.measureText(mm);
                float gap = sp(26);
                float x0 = cx - (hw + gap + mw) / 2f;
                float base = sp(96);
                text(c, hh, x0, base, sp(96), cond, white, Paint.Align.LEFT);
                text(c, mm, x0 + hw + gap, base, sp(96), cond, white, Paint.Align.LEFT);
                p.setColor(0xFFEB0028);
                c.drawCircle(x0 + hw + gap / 2f, base - sp(36), sp(6), p);
                text(c, fmt("EEE  d MMM", t).toUpperCase(Locale.getDefault()), cx, sp(122), sp(14),
                        tf("sans-serif-medium", Typeface.NORMAL), soft, Paint.Align.CENTER);
                break;
            }
            case 8: { // vertical: one digit per line, the first one red
                Typeface cond = tf("sans-serif-condensed", Typeface.BOLD);
                String all = hh2 + mm;
                float x = sp(8);
                float step = sp(70);
                for (int i = 0; i < 4; i++) {
                    text(c, String.valueOf(all.charAt(i)), x, sp(64) + i * step, sp(78), cond,
                            i == 0 ? 0xFFEB0028 : white, Paint.Align.LEFT);
                }
                float dx = x + sp(62);
                text(c, fmt("EEEE", t), dx, sp(52), sp(18), tf("sans-serif-medium", Typeface.BOLD), white, Paint.Align.LEFT);
                text(c, fmt("d MMMM", t), dx, sp(76), sp(15), tf("sans-serif", Typeface.NORMAL), soft, Paint.Align.LEFT);
                break;
            }
            case 9: { // big thin stacked digits, minutes see-through, small date on top
                text(c, fmt("EEE, d MMM", t), sp(8), sp(20), sp(15), tf("sans-serif-medium", Typeface.NORMAL), soft, Paint.Align.LEFT);
                p.setLetterSpacing(-0.05f);
                text(c, hh2, sp(0), sp(128), sp(130), tf("sans-serif-light", Typeface.NORMAL), white, Paint.Align.LEFT);
                text(c, mm, sp(0), sp(240), sp(130), tf("sans-serif-light", Typeface.NORMAL), 0x80FFFFFF, Paint.Align.LEFT);
                break;
            }
            case 10: { // magazine: weekday in large type, time bold on the left
                text(c, fmt("EEEE", t).toLowerCase(Locale.getDefault()), sp(6), sp(52), sp(46),
                        tf("serif", Typeface.ITALIC), soft, Paint.Align.LEFT);
                p.setLetterSpacing(-0.03f);
                text(c, hh + ":" + mm, sp(2), sp(146), sp(98), tf("sans-serif-medium", Typeface.BOLD), white, Paint.Align.LEFT);
                p.setLetterSpacing(0.12f);
                text(c, fmt("d MMMM yyyy", t).toUpperCase(Locale.getDefault()), sp(8), sp(176), sp(12),
                        tf("sans-serif-medium", Typeface.NORMAL), soft, Paint.Align.LEFT);
                break;
            }
            default: { // classic
                text(c, hh + ":" + mm, cx, sp(80), sp(72), tf("sans-serif", Typeface.NORMAL), white, Paint.Align.CENTER);
                break;
            }
        }
        p.setLetterSpacing(0f);
        p.setShadowLayer(0, 0, 0, 0);
    }
}
