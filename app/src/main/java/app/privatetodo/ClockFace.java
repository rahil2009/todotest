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
            "HyperOS · Depth", "HyperOS · Magazine",
            "iOS · Glass", "iOS · Compact", "iOS · Stretch thin", "iOS · Tinted glass",
            "One UI · Horizontal", "One UI · Adaptive", "One UI · Outline", "One UI · Corner",
            "ColorOS · Long", "ColorOS · Word art", "ColorOS · Blur", "ColorOS · Geometry",
            "OxygenOS · Side", "OxygenOS · Top left", "OxygenOS · Span", "OxygenOS · Heavy",
            "HyperOS · Outline", "HyperOS · Split", "HyperOS · Neon", "HyperOS · Playful"};
    private static final int[] HEIGHT_DP = {104, 196, 150, 232, 146, 236, 196, 128, 300, 250, 190,
            170, 70, 210, 196, 120, 140, 232, 120, 110, 190, 150, 130, 230, 130, 96, 140, 236, 270, 150, 160};

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

    /** The text size that makes {@code s} exactly {@code room} px wide in this typeface. */
    private float fitSize(String s, float room, Typeface t, float spacing) {
        p.setTypeface(t);
        p.setLetterSpacing(spacing);
        p.setTextSize(100f);
        float w = p.measureText(s);
        return w <= 0 ? 100f : 100f * room / w;
    }

    private void outline(Canvas c, String s, float x, float y, float size, Typeface t, int color, float width,
                         Paint.Align a) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(width);
        p.setStrokeJoin(Paint.Join.ROUND);
        text(c, s, x, y, size, t, color, a);
        p.setStyle(Paint.Style.FILL);
    }

    /** Frosted glass digits: a soft fill, a light rim and a shine across the top. */
    private void glass(Canvas c, String s, float x, float y, float size, Typeface t, int tint) {
        p.setShadowLayer(sp(14), 0, sp(4), 0x40000000);
        text(c, s, x, y, size, t, (tint & 0x00FFFFFF) | 0x59000000, Paint.Align.CENTER);
        p.setShadowLayer(0, 0, 0, 0);
        android.graphics.Shader old = p.getShader();
        p.setShader(new android.graphics.LinearGradient(0, y - size * 0.8f, 0, y,
                new int[]{0x8CFFFFFF, 0x14FFFFFF, 0x33FFFFFF}, new float[]{0f, 0.55f, 1f},
                android.graphics.Shader.TileMode.CLAMP));
        text(c, s, x, y, size, t, Color.WHITE, Paint.Align.CENTER);
        p.setShader(old);
        outline(c, s, x, y, size, t, 0x99FFFFFF, Math.max(1f, sp(1.2f)), Paint.Align.CENTER);
    }

    /** One digit drawn from round dots on a 3 x 5 grid. */
    private static final String[] DOTS = {"111101101101111", "010110010010111", "111001111100111", "111001111001111",
            "101101111001001", "111100111001111", "111100111101111", "111001001001001", "111101111101111",
            "111101111001111"};

    private void dotDigit(Canvas c, char ch, float x, float y, float cell, int color) {
        if (ch < '0' || ch > '9') return;
        String g = DOTS[ch - '0'];
        p.setColor(color);
        for (int i = 0; i < 15; i++) {
            if (g.charAt(i) != '1') continue;
            float cx = x + (i % 3) * cell + cell / 2f;
            float cy = y + (i / 3) * cell + cell / 2f;
            c.drawCircle(cx, cy, cell * 0.42f, p);
        }
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
            case 11: { // glass digits, date above
                text(c, fmt("EEEE d MMMM", t), cx, sp(24), sp(17), tf("sans-serif-medium", Typeface.BOLD), soft, Paint.Align.CENTER);
                Typeface b = tf("sans-serif", Typeface.BOLD);
                p.setLetterSpacing(-0.03f);
                glass(c, hh + ":" + mm, cx, hgt - sp(18), sp(104), b, Color.WHITE);
                break;
            }
            case 12: { // compact: the time as small as the date, side by side
                String line = fmt("EEE d MMM", t) + "   " + hh + ":" + mm;
                text(c, line, cx, sp(44), sp(24), tf("sans-serif-medium", Typeface.BOLD), white, Paint.Align.CENTER);
                break;
            }
            case 13: { // stretched tall in a thin weight
                text(c, fmt("EEEE d MMMM", t), cx, sp(22), sp(16), tf("sans-serif-medium", Typeface.NORMAL), soft, Paint.Align.CENTER);
                c.save();
                c.scale(1f, 1.75f, cx, hgt);
                p.setLetterSpacing(0.02f);
                text(c, hh + ":" + mm, cx, hgt - sp(6) / 1.75f, sp(100), tf("sans-serif-light", Typeface.NORMAL), 0xF2FFFFFF, Paint.Align.CENTER);
                c.restore();
                break;
            }
            case 14: { // tinted glass, stretched
                text(c, fmt("EEEE d MMMM", t), cx, sp(24), sp(17), tf("sans-serif-medium", Typeface.BOLD), soft, Paint.Align.CENTER);
                c.save();
                c.scale(1f, 1.3f, cx, hgt);
                p.setLetterSpacing(-0.04f);
                glass(c, hh + ":" + mm, cx, hgt - sp(14) / 1.3f, sp(104), tf("sans-serif-black", Typeface.NORMAL), 0xFF6FA8FF);
                c.restore();
                break;
            }
            case 15: { // horizontal: bold hours beside thin minutes
                Typeface hb = tf("sans-serif-black", Typeface.NORMAL);
                Typeface ml = tf("sans-serif-thin", Typeface.NORMAL);
                float size = sp(92);
                p.setTypeface(hb);
                p.setTextSize(size);
                float hw = p.measureText(hh2);
                p.setTypeface(ml);
                float mw = p.measureText(mm);
                float x0 = cx - (hw + sp(8) + mw) / 2f;
                text(c, hh2, x0, sp(90), size, hb, white, Paint.Align.LEFT);
                text(c, mm, x0 + hw + sp(8), sp(90), size, ml, white, Paint.Align.LEFT);
                text(c, fmt("EEE, d MMMM", t), cx, sp(114), sp(15), tf("sans-serif", Typeface.NORMAL), soft, Paint.Align.CENTER);
                break;
            }
            case 16: { // adaptive: the two middle characters in a second typeface
                String full = hh2 + ":" + mm;
                Typeface a = tf("sans-serif", Typeface.BOLD);
                Typeface b = tf("serif", Typeface.BOLD_ITALIC);
                float size = sp(96);
                float[] ws = new float[full.length()];
                float total = 0;
                for (int i = 0; i < full.length(); i++) {
                    boolean mid = i == 1 || i == 3;
                    p.setTypeface(mid ? b : a);
                    p.setTextSize(size);
                    ws[i] = p.measureText(String.valueOf(full.charAt(i)));
                    total += ws[i];
                }
                float x = cx - total / 2f;
                for (int i = 0; i < full.length(); i++) {
                    boolean mid = i == 1 || i == 3;
                    text(c, String.valueOf(full.charAt(i)), x, sp(100), size, mid ? b : a, mid ? 0xFFFFD27A : white, Paint.Align.LEFT);
                    x += ws[i];
                }
                text(c, fmt("EEE d MMM", t), cx, sp(128), sp(15), tf("sans-serif-medium", Typeface.NORMAL), soft, Paint.Align.CENTER);
                break;
            }
            case 17: { // outline digits stacked
                Typeface b = tf("sans-serif", Typeface.BOLD);
                outline(c, hh2, sp(4), sp(102), sp(112), b, white, sp(2.4f), Paint.Align.LEFT);
                outline(c, mm, sp(4), sp(200), sp(112), b, 0xB3FFFFFF, sp(2.4f), Paint.Align.LEFT);
                text(c, fmt("EEE, d MMMM", t), sp(8), sp(226), sp(16), tf("sans-serif-medium", Typeface.NORMAL), soft, Paint.Align.LEFT);
                break;
            }
            case 18: { // corner: small and bold at the top left, date under it
                text(c, hh + ":" + mm, sp(6), sp(62), sp(64), tf("sans-serif", Typeface.BOLD), white, Paint.Align.LEFT);
                text(c, fmt("EEEE, d MMMM", t), sp(8), sp(94), sp(16), tf("sans-serif-medium", Typeface.NORMAL), soft, Paint.Align.LEFT);
                break;
            }
            case 19: { // long: condensed digits stretched across the whole width
                Typeface cond = tf("sans-serif-condensed", Typeface.BOLD);
                String full = hh + ":" + mm;
                float size = fitSize(full, w - sp(4), cond, -0.02f);
                text(c, full, cx, Math.min(hgt - sp(4), size * 0.76f + sp(4)), size, cond, white, Paint.Align.CENTER);
                break;
            }
            case 20: { // word art line over a left-aligned bold clock
                String[] art = {"Slow Sunday", "Hello Monday", "Keep going", "Midweek", "Almost there", "Hello Friday", "Weekend mode"};
                text(c, art[now.get(Calendar.DAY_OF_WEEK) - 1], sp(6), sp(40), sp(34), tf("serif", Typeface.BOLD_ITALIC), 0xFFFFC56B, Paint.Align.LEFT);
                p.setLetterSpacing(-0.03f);
                text(c, hh + ":" + mm, sp(2), sp(136), sp(96), tf("sans-serif-black", Typeface.NORMAL), white, Paint.Align.LEFT);
                p.setLetterSpacing(0f);
                text(c, fmt("EEE d MMM", t), sp(8), sp(166), sp(14), tf("sans-serif-medium", Typeface.NORMAL), soft, Paint.Align.LEFT);
                break;
            }
            case 21: { // blur: a soft glow behind light digits
                Typeface b = tf("sans-serif-medium", Typeface.BOLD);
                p.setShadowLayer(sp(22), 0, 0, 0xCCFFFFFF);
                text(c, hh + ":" + mm, cx, sp(106), sp(100), b, 0x40FFFFFF, Paint.Align.CENTER);
                p.setShadowLayer(sp(6), 0, 0, 0x66FFFFFF);
                text(c, hh + ":" + mm, cx, sp(106), sp(100), b, 0xEEFFFFFF, Paint.Align.CENTER);
                p.setShadowLayer(0, 0, 0, 0);
                text(c, fmt("EEEE d MMMM", t), cx, sp(138), sp(15), tf("sans-serif", Typeface.NORMAL), soft, Paint.Align.CENTER);
                break;
            }
            case 22: { // geometry: digits built from dots
                String all = hh2 + mm;
                float cell = sp(11);
                float digitW = cell * 3;
                float gap = cell;
                float colon = cell * 1.6f;
                float total = digitW * 4 + gap * 2 + colon;
                float x = cx - total / 2f;
                float y = sp(18);
                int[] col = {white, white, 0xFFFFC56B, 0xFFFFC56B};
                for (int i = 0; i < 4; i++) {
                    dotDigit(c, all.charAt(i), x, y, cell, col[i]);
                    x += digitW + (i == 1 ? 0 : gap);
                    if (i == 1) {
                        p.setColor(soft);
                        c.drawCircle(x + colon / 2f, y + cell * 1.5f, cell * 0.42f, p);
                        c.drawCircle(x + colon / 2f, y + cell * 3.5f, cell * 0.42f, p);
                        x += colon;
                    }
                }
                text(c, fmt("EEE d MMM", t).toUpperCase(Locale.getDefault()), cx, sp(118), sp(14),
                        tf("sans-serif-medium", Typeface.NORMAL), soft, Paint.Align.CENTER);
                break;
            }
            case 23: { // side: the time runs up the left edge, date beside it
                c.save();
                c.rotate(-90, sp(70), hgt - sp(4));
                text(c, hh + ":" + mm, sp(70), hgt - sp(4), sp(84), tf("sans-serif-condensed", Typeface.BOLD), white, Paint.Align.LEFT);
                c.restore();
                text(c, fmt("EEEE", t), sp(92), hgt - sp(40), sp(20), tf("sans-serif-medium", Typeface.BOLD), white, Paint.Align.LEFT);
                text(c, fmt("d MMMM", t), sp(92), hgt - sp(14), sp(15), tf("sans-serif", Typeface.NORMAL), soft, Paint.Align.LEFT);
                break;
            }
            case 24: { // top left, bold with a red colon
                Typeface b = tf("sans-serif", Typeface.BOLD);
                p.setTextSize(sp(72));
                p.setTypeface(b);
                float hw = p.measureText(hh);
                float cw = p.measureText(":");
                text(c, hh, sp(4), sp(72), sp(72), b, white, Paint.Align.LEFT);
                text(c, ":", sp(4) + hw, sp(68), sp(72), b, 0xFFEB0028, Paint.Align.LEFT);
                text(c, mm, sp(4) + hw + cw, sp(72), sp(72), b, white, Paint.Align.LEFT);
                text(c, fmt("EEE, d MMM", t), sp(8), sp(104), sp(16), tf("sans-serif-medium", Typeface.NORMAL), soft, Paint.Align.LEFT);
                break;
            }
            case 25: { // span: a thin clock across the width with the date at both ends
                Typeface th = tf("sans-serif-thin", Typeface.NORMAL);
                String full = hh + ":" + mm;
                float size = fitSize(full, w * 0.86f, th, 0.06f);
                text(c, full, cx, size * 0.74f, size, th, white, Paint.Align.CENTER);
                p.setLetterSpacing(0.2f);
                text(c, fmt("EEE", t).toUpperCase(Locale.getDefault()), sp(4), hgt - sp(2), sp(11), tf("sans-serif-medium", Typeface.NORMAL), soft, Paint.Align.LEFT);
                text(c, fmt("d MMM", t).toUpperCase(Locale.getDefault()), w - sp(4), hgt - sp(2), sp(11), tf("sans-serif-medium", Typeface.NORMAL), soft, Paint.Align.RIGHT);
                break;
            }
            case 26: { // heavy digits, minutes in red
                Typeface b = tf("sans-serif-black", Typeface.NORMAL);
                p.setTextSize(sp(100));
                p.setTypeface(b);
                p.setLetterSpacing(-0.04f);
                float hw = p.measureText(hh);
                float mw = p.measureText(mm);
                float x0 = cx - (hw + sp(10) + mw) / 2f;
                text(c, hh, x0, sp(100), sp(100), b, white, Paint.Align.LEFT);
                text(c, mm, x0 + hw + sp(10), sp(100), sp(100), b, 0xFFEB0028, Paint.Align.LEFT);
                p.setLetterSpacing(0f);
                text(c, fmt("EEEE d MMMM", t), cx, sp(128), sp(14), tf("sans-serif-medium", Typeface.NORMAL), soft, Paint.Align.CENTER);
                break;
            }
            case 27: { // outline digits, large and centred
                Typeface b = tf("sans-serif-black", Typeface.NORMAL);
                text(c, fmt("EEE, d MMM", t), cx, sp(20), sp(15), tf("sans-serif-medium", Typeface.NORMAL), soft, Paint.Align.CENTER);
                outline(c, hh2, cx, sp(122), sp(118), b, white, sp(2f), Paint.Align.CENTER);
                outline(c, mm, cx, sp(226), sp(118), b, white, sp(2f), Paint.Align.CENTER);
                break;
            }
            case 28: { // split: hours at the top, minutes at the bottom, the middle left open
                Typeface b = tf("sans-serif", Typeface.BOLD);
                text(c, hh2, sp(2), sp(96), sp(110), b, white, Paint.Align.LEFT);
                text(c, mm, w - sp(2), hgt - sp(8), sp(110), b, white, Paint.Align.RIGHT);
                text(c, fmt("EEE", t), w - sp(6), sp(36), sp(18), tf("sans-serif-medium", Typeface.BOLD), soft, Paint.Align.RIGHT);
                text(c, fmt("d MMM", t), w - sp(6), sp(60), sp(15), tf("sans-serif", Typeface.NORMAL), soft, Paint.Align.RIGHT);
                break;
            }
            case 29: { // neon: purple gradient digits with a glow
                Typeface b = tf("sans-serif-black", Typeface.NORMAL);
                String full = hh + ":" + mm;
                p.setShadowLayer(sp(18), 0, 0, 0xCC9B5CFF);
                android.graphics.Shader old = p.getShader();
                p.setShader(new android.graphics.LinearGradient(0, sp(20), 0, sp(110),
                        new int[]{0xFFF3D9FF, 0xFFB57CFF, 0xFF7A4DFF}, null, android.graphics.Shader.TileMode.CLAMP));
                p.setLetterSpacing(-0.03f);
                text(c, full, cx, sp(104), sp(100), b, white, Paint.Align.CENTER);
                p.setShader(old);
                p.setShadowLayer(0, 0, 0, 0);
                p.setLetterSpacing(0f);
                text(c, fmt("EEEE d MMMM", t), cx, sp(136), sp(15), tf("sans-serif-medium", Typeface.NORMAL), 0xFFD9C2FF, Paint.Align.CENTER);
                break;
            }
            case 30: { // playful: round heavy digits with a soft coloured outline
                Typeface b = tf("sans-serif-black", Typeface.NORMAL);
                String full = hh + ":" + mm;
                outline(c, full, cx, sp(112), sp(100), b, 0xFFFF7A90, sp(10), Paint.Align.CENTER);
                text(c, full, cx, sp(112), sp(100), b, white, Paint.Align.CENTER);
                text(c, fmt("EEE d MMM", t), cx, sp(146), sp(16), tf("sans-serif-medium", Typeface.BOLD), 0xFFFFC2CC, Paint.Align.CENTER);
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
