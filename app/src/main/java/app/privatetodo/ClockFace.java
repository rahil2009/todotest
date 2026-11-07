package app.privatetodo;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.format.DateFormat;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * The clock on the full-screen alert: 37 faces, drawn with the phone's own fonts and updated every minute.
 * Size, main colour, accent colour and the date line can be changed in Settings > Full-screen alert.
 */
final class ClockFace extends View {
    static final String[] NAMES = {"Classic", "Tall", "Two-tone", "Magazine", "Glow", "Dots", "Heavy",
            "Frames", "Ring", "Hour arc", "Swiss", "Editorial", "Hairline", "Flip", "Capsule", "Minute bars",
            "Words", "Offset", "Echo", "Gradient", "Block", "Orbit", "Minimal dial", "Segments", "Matrix", "Ruler",
            "Big hour", "Liquid", "Tower", "Spaced", "Calendar", "Stencil", "Underline", "Brackets", "Neon",
            "Week rail", "Day progress"};
    private static final int[] HEIGHT_DP = {104, 196, 236, 190, 150, 130, 130,
            130, 220, 200, 170, 170, 140, 150, 140, 150, 200, 230, 140, 140, 140, 220, 220, 140, 130, 150,
            220, 150, 220, 120, 170, 140, 140, 160, 150, 160, 160};
    /** The accent each face uses until you pick your own. */
    private static final int[] ACCENT = {0xFF8FD6C8, 0xFF8FD6C8, 0xFFFFC56B, 0xFFFFC56B, 0xFF8FD6C8, 0xFFFFC56B, 0xFFEB0028,
            0xFF8FD6C8, 0xFF8FD6C8, 0xFFFFC56B, 0xFFFF4D3D, 0xFFE8C9A0, 0xFF8FD6C8, 0xFFFFC56B, 0xFF8FD6C8, 0xFF8FD6C8,
            0xFFFFC56B, 0xFFFF8A65, 0xFF7FB2FF, 0xFFB57CFF, 0xFF8FD6C8, 0xFFFFC56B, 0xFFFF6B5E, 0xFFFF5E5E, 0xFF7CF0A8,
            0xFFFFC56B, 0xFFFFC56B, 0xFF6FC3FF, 0xFF8FD6C8, 0xFF8FD6C8, 0xFFFF6B5E, 0xFF8FD6C8, 0xFFFFC56B, 0xFF8FD6C8,
            0xFFFF5FD2, 0xFF8FD6C8, 0xFFFFC56B};
    static final int[] COLORS = {0xFFFFFFFF, 0xFFF4EBDD, 0xFFCFE8FF, 0xFFD6F5E6, 0xFFFFD6E0, 0xFFE6DCFF,
            0xFF8FD6C8, 0xFFFFC56B, 0xFFFF6B5E, 0xFF7FB2FF, 0xFFB57CFF, 0xFF7CF0A8};

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
        setLayerType(LAYER_TYPE_SOFTWARE, null); // glows and the stencil cut need it
    }

    // ------------------------------------------------------------------ settings

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    static int chosen(Context c) {
        SharedPreferences s = sp(c);
        if (s.contains("clock_face2")) return s.getInt("clock_face2", 0);
        int old = s.getInt("clock_face", 0); // the earlier list: keep the faces that stayed
        int[][] map = {{1, 1}, {5, 2}, {10, 3}, {21, 4}, {22, 5}, {26, 6}};
        for (int[] m : map) if (m[0] == old) return m[1];
        return 0;
    }

    static void setChosen(Context c, int f) {
        sp(c).edit().putInt("clock_face2", f).apply();
    }

    /** 60 to 140 percent. */
    static int sizePct(Context c) {
        return sp(c).getInt("clock_size", 100);
    }

    static void setSizePct(Context c, int v) {
        sp(c).edit().putInt("clock_size", Math.max(60, Math.min(140, v))).apply();
    }

    static int mainColor(Context c) {
        return sp(c).getInt("clock_main", 0xFFFFFFFF);
    }

    static void setMainColor(Context c, int v) {
        sp(c).edit().putInt("clock_main", v).apply();
    }

    /** 0 = the face's own accent. */
    static int accentColor(Context c) {
        return sp(c).getInt("clock_accent", 0);
    }

    static void setAccentColor(Context c, int v) {
        sp(c).edit().putInt("clock_accent", v).apply();
    }

    /** -1 = each face's own lettering; otherwise a note font (NoteFonts slot) used for every clock text. */
    static int fontIndex(Context c) {
        return sp(c).getInt("clock_font", -1);
    }

    static void setFontIndex(Context c, int v) {
        sp(c).edit().putInt("clock_font", v).apply();
    }

    private Typeface own(Typeface t) {
        int idx = fontIndex(getContext());
        if (idx < 0) return t;
        NoteFonts.init(getContext());
        Typeface f = NoteFonts.face(idx);
        if (f == null) return t;
        boolean bold = t != null && t.isBold();
        boolean ital = t != null && t.isItalic();
        return Typeface.create(f, bold ? (ital ? Typeface.BOLD_ITALIC : Typeface.BOLD) : (ital ? Typeface.ITALIC : Typeface.NORMAL));
    }

    static boolean showDate(Context c) {
        return sp(c).getBoolean("clock_date", true);
    }

    static void setShowDate(Context c, boolean v) {
        sp(c).edit().putBoolean("clock_date", v).apply();
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

    /** Re-reads size and colours (the Settings preview calls this as you change them). */
    void refresh() {
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
        float user = sizePct(getContext()) / 100f;
        // drawn for a 304dp-wide column; never wider than the column, never taller than 40% of the screen
        fit = Math.min(user, width / (304f * d * scale));
        float cap = getResources().getDisplayMetrics().heightPixels * 0.40f;
        float h = HEIGHT_DP[face] * d * scale * fit;
        if (scale == 1f && h > cap) {
            fit *= cap / h;
            h = cap;
        }
        setMeasuredDimension(width, Math.round(h));
    }

    // ------------------------------------------------------------------ helpers

    private float sp(float v) {
        return v * d * scale * fit;
    }

    private Typeface tf(String family, int style) {
        return Typeface.create(family, style);
    }

    private void text(Canvas c, String s, float x, float y, float size, Typeface t, int color, Paint.Align a) {
        p.setTextSize(size);
        p.setTypeface(own(t));
        p.setColor(color);
        p.setTextAlign(a);
        c.drawText(s, x, y, p);
    }

    private float width(String s, float size, Typeface t) {
        p.setTextSize(size);
        p.setTypeface(own(t));
        return p.measureText(s);
    }

    private void outline(Canvas c, String s, float x, float y, float size, Typeface t, int color, float w, Paint.Align a) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(w);
        p.setStrokeJoin(Paint.Join.ROUND);
        text(c, s, x, y, size, t, color, a);
        p.setStyle(Paint.Style.FILL);
    }

    private static String fmt(String pattern, Date t) {
        return new SimpleDateFormat(pattern, Locale.getDefault()).format(t);
    }

    private static int alpha(int color, int a) {
        return (color & 0x00FFFFFF) | (a << 24);
    }

    private void line(Canvas c, float x0, float y0, float x1, float y1, float w, int color) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(w);
        p.setColor(color);
        c.drawLine(x0, y0, x1, y1, p);
        p.setStyle(Paint.Style.FILL);
    }

    private static final String[] DOTS = {"111101101101111", "010110010010111", "111001111100111", "111001111001111",
            "101101111001001", "111100111001111", "111100111101111", "111001001001001", "111101111101111",
            "111101111001111"};

    private void dotDigit(Canvas c, char ch, float x, float y, float cell, int color) {
        if (ch < '0' || ch > '9') return;
        String g = DOTS[ch - '0'];
        p.setColor(color);
        for (int i = 0; i < 15; i++) {
            if (g.charAt(i) != '1') continue;
            c.drawCircle(x + (i % 3) * cell + cell / 2f, y + (i / 3) * cell + cell / 2f, cell * 0.42f, p);
        }
    }

    private static final String[][] MATRIX = {
            {"01110", "10001", "10011", "10101", "11001", "10001", "01110"},
            {"00100", "01100", "00100", "00100", "00100", "00100", "01110"},
            {"01110", "10001", "00001", "00010", "00100", "01000", "11111"},
            {"11111", "00010", "00100", "00010", "00001", "10001", "01110"},
            {"00010", "00110", "01010", "10010", "11111", "00010", "00010"},
            {"11111", "10000", "11110", "00001", "00001", "10001", "01110"},
            {"00110", "01000", "10000", "11110", "10001", "10001", "01110"},
            {"11111", "00001", "00010", "00100", "01000", "01000", "01000"},
            {"01110", "10001", "10001", "01110", "10001", "10001", "01110"},
            {"01110", "10001", "10001", "01111", "00001", "00010", "01100"}};

    private void matrixDigit(Canvas c, char ch, float x, float y, float cell, int on, int off) {
        if (ch < '0' || ch > '9') return;
        String[] g = MATRIX[ch - '0'];
        for (int row = 0; row < 7; row++) {
            for (int col = 0; col < 5; col++) {
                p.setColor(g[row].charAt(col) == '1' ? on : off);
                c.drawCircle(x + col * cell + cell / 2f, y + row * cell + cell / 2f, cell * 0.38f, p);
            }
        }
    }

    // a b c d e f g
    private static final int[] SEG = {0b1111110, 0b0110000, 0b1101101, 0b1111001, 0b0110011, 0b1011011, 0b1011111,
            0b1110000, 0b1111111, 0b1111011};

    private void segDigit(Canvas c, char ch, float x, float y, float w, float h, float t, int on, int off) {
        if (ch < '0' || ch > '9') return;
        int m = SEG[ch - '0'];
        float half = h / 2f;
        float g = t * 0.25f;
        float[][] rects = {
                {x + t / 2 + g, y, x + w - t / 2 - g, y + t},                    // a
                {x + w - t, y + t / 2 + g, x + w, y + half - g},                  // b
                {x + w - t, y + half + g, x + w, y + h - t / 2 - g},              // c
                {x + t / 2 + g, y + h - t, x + w - t / 2 - g, y + h},              // d
                {x, y + half + g, x + t, y + h - t / 2 - g},                      // e
                {x, y + t / 2 + g, x + t, y + half - g},                          // f
                {x + t / 2 + g, y + half - t / 2, x + w - t / 2 - g, y + half + t / 2}}; // g
        for (int i = 0; i < 7; i++) {
            boolean lit = (m & (1 << (6 - i))) != 0;
            p.setColor(lit ? on : off);
            r.set(rects[i][0], rects[i][1], rects[i][2], rects[i][3]);
            c.drawRoundRect(r, t / 2f, t / 2f, p);
        }
    }

    private static final String[] ONES = {"twelve", "one", "two", "three", "four", "five", "six", "seven", "eight",
            "nine", "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen",
            "nineteen"};
    private static final String[] TENS = {"", "", "twenty", "thirty", "forty", "fifty"};

    private static String words(int n) {
        if (n < 20) return n == 0 ? "o'clock" : ONES[n];
        return TENS[n / 10] + (n % 10 == 0 ? "" : "-" + ONES[n % 10]);
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void onDraw(Canvas c) {
        Context ctx = getContext();
        Calendar now = Calendar.getInstance();
        Date t = now.getTime();
        boolean h24 = DateFormat.is24HourFormat(ctx);
        int hour = now.get(Calendar.HOUR_OF_DAY);
        int h12 = hour % 12 == 0 ? 12 : hour % 12;
        String hh = h24 ? (hour < 10 ? "0" + hour : String.valueOf(hour)) : String.valueOf(h12);
        String hh2 = hh.length() == 1 ? "0" + hh : hh;
        int min = now.get(Calendar.MINUTE);
        String mm = min < 10 ? "0" + min : String.valueOf(min);
        String time = hh + ":" + mm;
        float w = getWidth();
        float cx = w / 2f;
        float hgt = getHeight();
        int M = mainColor(ctx);
        int soft = alpha(M, 0xCC);
        int dim = alpha(M, 0x59);
        int A = accentColor(ctx) != 0 ? accentColor(ctx) : ACCENT[face];
        boolean date = showDate(ctx);
        Typeface reg = tf("sans-serif", Typeface.NORMAL);
        Typeface bold = tf("sans-serif", Typeface.BOLD);
        Typeface med = tf("sans-serif-medium", Typeface.NORMAL);
        Typeface black = tf("sans-serif-black", Typeface.NORMAL);
        Typeface thin = tf("sans-serif-thin", Typeface.NORMAL);
        Typeface light = tf("sans-serif-light", Typeface.NORMAL);
        Typeface cond = tf("sans-serif-condensed", Typeface.BOLD);
        Typeface serif = tf("serif", Typeface.NORMAL);
        p.reset();
        p.setAntiAlias(true);
        switch (face) {
            case 1: { // Tall: stretched heavy digits, date above
                if (date) text(c, fmt("EEEE d MMMM", t), cx, sp(26), sp(17), tf("sans-serif-medium", Typeface.BOLD), soft, Paint.Align.CENTER);
                c.save();
                c.scale(1f, 1.38f, cx, hgt);
                p.setLetterSpacing(-0.04f);
                p.setShadowLayer(sp(10), 0, 0, alpha(M, 0x55));
                text(c, time, cx, hgt - sp(10) / 1.38f, sp(104), black, alpha(M, 0xF2), Paint.Align.CENTER);
                c.restore();
                break;
            }
            case 2: { // Two-tone: stacked, minutes in the accent
                p.setLetterSpacing(-0.03f);
                text(c, hh2, cx, sp(104), sp(118), black, M, Paint.Align.CENTER);
                text(c, mm, cx, sp(206), sp(118), black, A, Paint.Align.CENTER);
                p.setLetterSpacing(0.05f);
                if (date) text(c, fmt("EEEE  d/M", t), cx, sp(230), sp(15), med, soft, Paint.Align.CENTER);
                break;
            }
            case 3: { // Magazine: weekday in italic serif, bold time
                if (date) text(c, fmt("EEEE", t).toLowerCase(Locale.getDefault()), sp(6), sp(52), sp(46), tf("serif", Typeface.ITALIC), A, Paint.Align.LEFT);
                p.setLetterSpacing(-0.03f);
                text(c, time, sp(2), sp(146), sp(98), tf("sans-serif-medium", Typeface.BOLD), M, Paint.Align.LEFT);
                p.setLetterSpacing(0.12f);
                if (date) text(c, fmt("d MMMM yyyy", t).toUpperCase(Locale.getDefault()), sp(8), sp(176), sp(12), med, soft, Paint.Align.LEFT);
                break;
            }
            case 4: { // Glow
                Typeface b = tf("sans-serif-medium", Typeface.BOLD);
                p.setShadowLayer(sp(22), 0, 0, alpha(A, 0xCC));
                text(c, time, cx, sp(106), sp(100), b, alpha(M, 0x40), Paint.Align.CENTER);
                p.setShadowLayer(sp(6), 0, 0, alpha(A, 0x88));
                text(c, time, cx, sp(106), sp(100), b, alpha(M, 0xEE), Paint.Align.CENTER);
                p.setShadowLayer(0, 0, 0, 0);
                if (date) text(c, fmt("EEEE d MMMM", t), cx, sp(138), sp(15), reg, soft, Paint.Align.CENTER);
                break;
            }
            case 5: { // Dots
                String all = hh2 + mm;
                float cell = sp(11);
                float dw = cell * 3;
                float colon = cell * 1.6f;
                float x = cx - (dw * 4 + cell * 2 + colon) / 2f;
                float y = sp(14);
                for (int i = 0; i < 4; i++) {
                    dotDigit(c, all.charAt(i), x, y, cell, i < 2 ? M : A);
                    x += dw + (i == 1 ? 0 : cell);
                    if (i == 1) {
                        p.setColor(soft);
                        c.drawCircle(x + colon / 2f, y + cell * 1.5f, cell * 0.42f, p);
                        c.drawCircle(x + colon / 2f, y + cell * 3.5f, cell * 0.42f, p);
                        x += colon;
                    }
                }
                if (date) text(c, fmt("EEE d MMM", t).toUpperCase(Locale.getDefault()), cx, sp(114), sp(14), med, soft, Paint.Align.CENTER);
                break;
            }
            case 6: { // Heavy: minutes in the accent
                p.setLetterSpacing(-0.04f);
                float hw = width(hh, sp(100), black);
                float mw = width(mm, sp(100), black);
                float x0 = cx - (hw + sp(10) + mw) / 2f;
                text(c, hh, x0, sp(100), sp(100), black, M, Paint.Align.LEFT);
                text(c, mm, x0 + hw + sp(10), sp(100), sp(100), black, A, Paint.Align.LEFT);
                p.setLetterSpacing(0f);
                if (date) text(c, fmt("EEEE d MMMM", t), cx, sp(124), sp(14), med, soft, Paint.Align.CENTER);
                break;
            }
            case 7: { // Frames: hours and minutes in two outlined tiles
                float bw = sp(128);
                float bh = sp(96);
                float gap = sp(12);
                float x0 = cx - bw - gap / 2f;
                for (int i = 0; i < 2; i++) {
                    float x = x0 + i * (bw + gap);
                    r.set(x, sp(4), x + bw, sp(4) + bh);
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(sp(1.5f));
                    p.setColor(i == 0 ? dim : alpha(A, 0x99));
                    c.drawRoundRect(r, sp(22), sp(22), p);
                    p.setStyle(Paint.Style.FILL);
                    text(c, i == 0 ? hh2 : mm, x + bw / 2f, sp(4) + bh / 2f + sp(26), sp(72), tf("monospace", Typeface.BOLD), i == 0 ? M : A, Paint.Align.CENTER);
                }
                if (date) text(c, fmt("EEE · d MMM", t), cx, sp(124), sp(14), med, soft, Paint.Align.CENTER);
                break;
            }
            case 8: { // Ring: the minute as an arc around the time
                float rad = sp(96);
                float cy = sp(108);
                r.set(cx - rad, cy - rad, cx + rad, cy + rad);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(sp(7));
                p.setStrokeCap(Paint.Cap.ROUND);
                p.setColor(alpha(M, 0x22));
                c.drawArc(r, 0, 360, false, p);
                p.setColor(A);
                c.drawArc(r, -90, Math.max(2f, 360f * min / 60f), false, p);
                p.setStyle(Paint.Style.FILL);
                text(c, time, cx, cy + sp(18), sp(54), bold, M, Paint.Align.CENTER);
                if (date) text(c, fmt("EEE d", t).toUpperCase(Locale.getDefault()), cx, cy + sp(46), sp(13), med, soft, Paint.Align.CENTER);
                break;
            }
            case 9: { // Hour arc: twelve marks along a half circle, this hour lit
                float rad = sp(130);
                float cy = sp(160);
                for (int i = 0; i < 12; i++) {
                    double a = Math.PI + Math.PI * i / 11.0;
                    float x = cx + (float) Math.cos(a) * rad;
                    float y = cy + (float) Math.sin(a) * rad;
                    boolean on = i == (hour % 12);
                    p.setColor(on ? A : alpha(M, 0x40));
                    c.drawCircle(x, y, on ? sp(7) : sp(3.5f), p);
                }
                text(c, time, cx, cy - sp(20), sp(64), bold, M, Paint.Align.CENTER);
                if (date) text(c, fmt("EEEE d MMMM", t), cx, cy + sp(14), sp(14), reg, soft, Paint.Align.CENTER);
                break;
            }
            case 10: { // Swiss: huge bold hour, small minutes on top right, a red square
                p.setLetterSpacing(-0.05f);
                text(c, hh, sp(0), sp(150), sp(170), bold, M, Paint.Align.LEFT);
                float hw = width(hh, sp(170), bold);
                p.setLetterSpacing(0f);
                text(c, mm, hw + sp(10), sp(60), sp(54), bold, M, Paint.Align.LEFT);
                p.setColor(A);
                c.drawRect(hw + sp(12), sp(76), hw + sp(30), sp(94), p);
                if (date) text(c, fmt("EEE d.M.", t), hw + sp(10), sp(150), sp(15), med, soft, Paint.Align.LEFT);
                break;
            }
            case 11: { // Editorial: serif time between two hairlines, small caps date
                line(c, sp(20), sp(14), w - sp(20), sp(14), sp(1), dim);
                if (date) {
                    p.setLetterSpacing(0.3f);
                    text(c, fmt("EEEE", t).toUpperCase(Locale.getDefault()), cx, sp(36), sp(11), med, A, Paint.Align.CENTER);
                    p.setLetterSpacing(0f);
                }
                text(c, time, cx, sp(128), sp(96), serif, M, Paint.Align.CENTER);
                line(c, sp(20), sp(148), w - sp(20), sp(148), sp(1), dim);
                if (date) {
                    p.setLetterSpacing(0.2f);
                    text(c, fmt("d MMMM yyyy", t).toUpperCase(Locale.getDefault()), cx, sp(166), sp(10), med, soft, Paint.Align.CENTER);
                }
                break;
            }
            case 12: { // Hairline: thin time, a long rule, the date on the right
                text(c, time, sp(2), sp(94), sp(96), thin, M, Paint.Align.LEFT);
                line(c, sp(4), sp(112), w - sp(4), sp(112), sp(1), dim);
                p.setColor(A);
                c.drawCircle(sp(4) + (w - sp(8)) * (min / 60f), sp(112), sp(3.5f), p);
                if (date) text(c, fmt("EEE d MMM", t), w - sp(4), sp(134), sp(14), reg, soft, Paint.Align.RIGHT);
                break;
            }
            case 13: { // Flip: two cards split across the middle
                float bw = sp(126);
                float bh = sp(110);
                float gap = sp(10);
                float x0 = cx - bw - gap / 2f;
                for (int i = 0; i < 2; i++) {
                    float x = x0 + i * (bw + gap);
                    r.set(x, sp(4), x + bw, sp(4) + bh);
                    p.setColor(alpha(M, 0x1C));
                    c.drawRoundRect(r, sp(14), sp(14), p);
                    text(c, i == 0 ? hh2 : mm, x + bw / 2f, sp(4) + bh / 2f + sp(28), sp(80), bold, i == 0 ? M : A, Paint.Align.CENTER);
                    line(c, x, sp(4) + bh / 2f, x + bw, sp(4) + bh / 2f, sp(2.5f), 0xFF000000);
                }
                if (date) text(c, fmt("EEEE d MMMM", t), cx, sp(140), sp(14), reg, soft, Paint.Align.CENTER);
                break;
            }
            case 14: { // Capsule: the time inside an outlined pill
                float tw = width(time, sp(72), med);
                r.set(cx - tw / 2f - sp(26), sp(6), cx + tw / 2f + sp(26), sp(100));
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(sp(2));
                p.setColor(A);
                c.drawRoundRect(r, sp(50), sp(50), p);
                p.setStyle(Paint.Style.FILL);
                text(c, time, cx, sp(78), sp(72), med, M, Paint.Align.CENTER);
                if (date) text(c, fmt("EEE d MMM", t), cx, sp(128), sp(14), reg, soft, Paint.Align.CENTER);
                break;
            }
            case 15: { // Minute bars: sixty bars, filled up to this minute
                text(c, time, cx, sp(76), sp(72), bold, M, Paint.Align.CENTER);
                float bw = (w - sp(8)) / 60f;
                for (int i = 0; i < 60; i++) {
                    boolean on = i <= min;
                    float bh = i % 5 == 0 ? sp(26) : sp(16);
                    p.setColor(on ? A : alpha(M, 0x26));
                    r.set(sp(4) + i * bw + bw * 0.2f, sp(118) - bh, sp(4) + (i + 1) * bw - bw * 0.2f, sp(118));
                    c.drawRoundRect(r, bw * 0.3f, bw * 0.3f, p);
                }
                if (date) text(c, fmt("EEEE d MMMM", t), cx, sp(142), sp(13), reg, soft, Paint.Align.CENTER);
                break;
            }
            case 16: { // Words
                String hw = ONES[h12 % 20 == 0 ? 12 : h12];
                text(c, hw, sp(4), sp(76), sp(76), black, M, Paint.Align.LEFT);
                text(c, words(min), sp(6), sp(140), sp(52), light, A, Paint.Align.LEFT);
                if (date) text(c, fmt("EEEE d MMMM", t).toLowerCase(Locale.getDefault()), sp(8), sp(182), sp(15), reg, soft, Paint.Align.LEFT);
                break;
            }
            case 17: { // Offset: minutes shifted right and overlapping the hours
                p.setLetterSpacing(-0.04f);
                text(c, hh2, sp(0), sp(112), sp(130), black, M, Paint.Align.LEFT);
                text(c, mm, w - sp(0), sp(210), sp(130), black, alpha(A, 0xE6), Paint.Align.RIGHT);
                p.setLetterSpacing(0f);
                if (date) text(c, fmt("EEE d MMM", t), sp(6), sp(200), sp(15), med, soft, Paint.Align.LEFT);
                break;
            }
            case 18: { // Echo: the time with two fading copies behind it
                for (int i = 2; i >= 0; i--) {
                    text(c, time, cx + sp(8) * i, sp(92) - sp(8) * i, sp(86), black, i == 0 ? M : alpha(A, i == 1 ? 0x66 : 0x2E), Paint.Align.CENTER);
                }
                if (date) text(c, fmt("EEEE d MMMM", t), cx, sp(126), sp(14), reg, soft, Paint.Align.CENTER);
                break;
            }
            case 19: { // Gradient
                float tw = width(time, sp(100), black);
                p.setShader(new LinearGradient(cx - tw / 2f, 0, cx + tw / 2f, 0, M, A, Shader.TileMode.CLAMP));
                p.setLetterSpacing(-0.03f);
                text(c, time, cx, sp(100), sp(100), black, M, Paint.Align.CENTER);
                p.setShader(null);
                p.setLetterSpacing(0f);
                if (date) text(c, fmt("EEEE d MMMM", t), cx, sp(128), sp(14), reg, soft, Paint.Align.CENTER);
                break;
            }
            case 20: { // Block: hours on an accent tile, minutes beside it
                float hw = width(hh2, sp(84), black);
                float x0 = cx - (hw + sp(36) + width(mm, sp(84), black)) / 2f;
                r.set(x0 - sp(10), sp(8), x0 + hw + sp(10), sp(104));
                p.setColor(A);
                c.drawRoundRect(r, sp(20), sp(20), p);
                text(c, hh2, x0, sp(88), sp(84), black, 0xFF111513, Paint.Align.LEFT);
                text(c, mm, x0 + hw + sp(26), sp(88), sp(84), black, M, Paint.Align.LEFT);
                if (date) text(c, fmt("EEE d MMM", t), cx, sp(130), sp(14), med, soft, Paint.Align.CENTER);
                break;
            }
            case 21: { // Orbit: an hour dot and a minute dot on two rings
                float cy = sp(110);
                float r1 = sp(100);
                float r2 = sp(70);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(sp(1.2f));
                p.setColor(alpha(M, 0x30));
                c.drawCircle(cx, cy, r1, p);
                c.drawCircle(cx, cy, r2, p);
                p.setStyle(Paint.Style.FILL);
                double ma = Math.PI * 2 * min / 60.0 - Math.PI / 2;
                double ha = Math.PI * 2 * ((hour % 12) + min / 60.0) / 12.0 - Math.PI / 2;
                p.setColor(A);
                c.drawCircle(cx + (float) Math.cos(ma) * r1, cy + (float) Math.sin(ma) * r1, sp(8), p);
                p.setColor(M);
                c.drawCircle(cx + (float) Math.cos(ha) * r2, cy + (float) Math.sin(ha) * r2, sp(10), p);
                text(c, time, cx, cy + sp(12), sp(34), med, M, Paint.Align.CENTER);
                if (date) text(c, fmt("EEE d", t).toUpperCase(Locale.getDefault()), cx, cy + sp(34), sp(11), med, soft, Paint.Align.CENTER);
                break;
            }
            case 22: { // Minimal dial: four marks and slim hands
                float cy = hgt / 2f;
                float rad = Math.min(cx, cy) - sp(8);
                for (int i = 0; i < 4; i++) {
                    double a = Math.PI / 2 * i;
                    line(c, cx + (float) Math.sin(a) * (rad - sp(14)), cy - (float) Math.cos(a) * (rad - sp(14)),
                            cx + (float) Math.sin(a) * rad, cy - (float) Math.cos(a) * rad, sp(3), soft);
                }
                double ha = Math.PI * 2 * ((hour % 12) + min / 60.0) / 12.0;
                double ma = Math.PI * 2 * min / 60.0;
                line(c, cx, cy, cx + (float) Math.sin(ha) * rad * 0.5f, cy - (float) Math.cos(ha) * rad * 0.5f, sp(5), M);
                line(c, cx, cy, cx + (float) Math.sin(ma) * rad * 0.82f, cy - (float) Math.cos(ma) * rad * 0.82f, sp(2.5f), A);
                p.setColor(A);
                c.drawCircle(cx, cy, sp(5), p);
                if (date) text(c, fmt("d", t), cx + rad * 0.55f, cy + sp(6), sp(16), med, soft, Paint.Align.CENTER);
                break;
            }
            case 23: { // Segments: a seven-segment display with unlit segments showing faintly
                String all = hh2 + mm;
                float dw = sp(44);
                float dh = sp(84);
                float th = sp(8);
                float gap = sp(12);
                float colon = sp(18);
                float x = cx - (dw * 4 + gap * 2 + colon) / 2f;
                int off = alpha(M, 0x14);
                for (int i = 0; i < 4; i++) {
                    segDigit(c, all.charAt(i), x, sp(8), dw, dh, th, i < 2 ? M : A, off);
                    x += dw + (i == 1 ? 0 : gap);
                    if (i == 1) {
                        p.setColor(soft);
                        c.drawCircle(x + colon / 2f, sp(8) + dh * 0.32f, sp(4), p);
                        c.drawCircle(x + colon / 2f, sp(8) + dh * 0.68f, sp(4), p);
                        x += colon;
                    }
                }
                if (date) text(c, fmt("EEE d MMM", t).toUpperCase(Locale.getDefault()), cx, sp(124), sp(13), tf("monospace", Typeface.NORMAL), soft, Paint.Align.CENTER);
                break;
            }
            case 24: { // Matrix: 5 x 7 dot grid, unlit dots faint
                String all = hh2 + mm;
                float cell = sp(10);
                float dw = cell * 5;
                float gap = cell;
                float colon = cell * 2;
                float x = cx - (dw * 4 + gap * 2 + colon) / 2f;
                int off = alpha(M, 0x12);
                for (int i = 0; i < 4; i++) {
                    matrixDigit(c, all.charAt(i), x, sp(4), cell, i < 2 ? M : A, off);
                    x += dw + (i == 1 ? 0 : gap);
                    if (i == 1) {
                        p.setColor(soft);
                        c.drawCircle(x + colon / 2f, sp(4) + cell * 2.5f, cell * 0.38f, p);
                        c.drawCircle(x + colon / 2f, sp(4) + cell * 4.5f, cell * 0.38f, p);
                        x += colon;
                    }
                }
                if (date) text(c, fmt("EEE d MMM", t).toUpperCase(Locale.getDefault()), cx, sp(112), sp(13), tf("monospace", Typeface.NORMAL), soft, Paint.Align.CENTER);
                break;
            }
            case 25: { // Ruler: a tape of minutes sliding under a fixed marker
                text(c, time, cx, sp(64), sp(60), bold, M, Paint.Align.CENTER);
                float base = sp(118);
                float per = sp(5); // px per minute
                int total = hour * 60 + min;
                int from = total - (int) (cx / per) - 1;
                int to = total + (int) (cx / per) + 1;
                for (int m2 = from; m2 <= to; m2++) {
                    float x = cx + (m2 - total) * per;
                    int mm2 = ((m2 % 60) + 60) % 60;
                    float len = mm2 == 0 ? sp(26) : mm2 % 15 == 0 ? sp(18) : mm2 % 5 == 0 ? sp(12) : sp(6);
                    line(c, x, base, x, base - len, sp(1.4f), alpha(M, mm2 == 0 ? 0xCC : 0x55));
                    if (mm2 == 0) {
                        int hl = ((m2 / 60) % 24 + 24) % 24;
                        text(c, String.valueOf(h24 ? hl : (hl % 12 == 0 ? 12 : hl % 12)), x, base + sp(18), sp(12), med, soft, Paint.Align.CENTER);
                    }
                }
                line(c, cx, base + sp(4), cx, base - sp(34), sp(3), A);
                if (date) text(c, fmt("EEEE d MMMM", t), cx, sp(146), sp(13), reg, soft, Paint.Align.CENTER);
                break;
            }
            case 26: { // Big hour: a giant hour, minutes in a round badge
                p.setLetterSpacing(-0.05f);
                text(c, hh, cx - sp(18), sp(196), sp(220), black, M, Paint.Align.CENTER);
                p.setLetterSpacing(0f);
                float bx = cx + width(hh, sp(220), black) / 2f - sp(10);
                p.setColor(A);
                c.drawCircle(bx, sp(54), sp(36), p);
                text(c, mm, bx, sp(66), sp(32), bold, 0xFF111513, Paint.Align.CENTER);
                if (date) text(c, fmt("EEE d MMM", t), cx, sp(216), sp(14), med, soft, Paint.Align.CENTER);
                break;
            }
            case 27: { // Liquid: outline digits that fill up as the hour goes by
                float base = sp(110);
                float size = sp(110);
                outline(c, time, cx, base, size, black, dim, sp(2), Paint.Align.CENTER);
                float top = base - size * 0.72f;
                float level = base - (base - top) * (min / 60f);
                c.save();
                c.clipRect(0, level, w, base + sp(6));
                text(c, time, cx, base, size, black, A, Paint.Align.CENTER);
                c.restore();
                if (date) text(c, fmt("EEEE d MMMM", t), cx, sp(138), sp(14), reg, soft, Paint.Align.CENTER);
                break;
            }
            case 28: { // Tower: tall condensed digits, widely spaced
                c.save();
                c.scale(1f, 1.9f, cx, hgt - sp(24));
                p.setLetterSpacing(0.12f);
                text(c, time, cx, hgt - sp(24), sp(84), cond, M, Paint.Align.CENTER);
                c.restore();
                p.setLetterSpacing(0.35f);
                if (date) text(c, fmt("EEE d MMM", t).toUpperCase(Locale.getDefault()), cx, hgt - sp(4), sp(11), med, A, Paint.Align.CENTER);
                break;
            }
            case 29: { // Spaced: light digits with wide tracking, dotted date
                p.setLetterSpacing(0.22f);
                text(c, hh + " " + mm, cx, sp(72), sp(70), light, M, Paint.Align.CENTER);
                p.setLetterSpacing(0.25f);
                if (date) text(c, fmt("EEE · d · MMM", t).toUpperCase(Locale.getDefault()), cx, sp(104), sp(11), med, A, Paint.Align.CENTER);
                break;
            }
            case 30: { // Calendar: today's date large on the left, the time on the right
                float lx = sp(8);
                p.setColor(A);
                r.set(lx, sp(10), lx + sp(110), sp(150));
                c.drawRoundRect(r, sp(22), sp(22), p);
                p.setLetterSpacing(0.15f);
                text(c, fmt("MMM", t).toUpperCase(Locale.getDefault()), lx + sp(55), sp(40), sp(14), med, 0xFF111513, Paint.Align.CENTER);
                p.setLetterSpacing(-0.02f);
                text(c, fmt("d", t), lx + sp(55), sp(124), sp(76), black, 0xFF111513, Paint.Align.CENTER);
                p.setLetterSpacing(0f);
                text(c, time, sp(134), sp(102), sp(58), bold, M, Paint.Align.LEFT);
                if (date) text(c, fmt("EEEE", t), sp(136), sp(132), sp(16), med, soft, Paint.Align.LEFT);
                break;
            }
            case 31: { // Stencil: a slit cut through the middle of the digits
                int save = c.saveLayer(0, 0, w, hgt, null);
                p.setLetterSpacing(-0.02f);
                text(c, time, cx, sp(100), sp(100), black, M, Paint.Align.CENTER);
                p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
                c.drawRect(0, sp(60), w, sp(66), p);
                p.setXfermode(null);
                c.restoreToCount(save);
                p.setLetterSpacing(0f);
                if (date) text(c, fmt("EEEE d MMMM", t), cx, sp(128), sp(14), med, A, Paint.Align.CENTER);
                break;
            }
            case 32: { // Underline: bold time over a bar that grows with the minutes
                float tw = width(time, sp(92), bold);
                text(c, time, cx, sp(92), sp(92), bold, M, Paint.Align.CENTER);
                float x0 = cx - tw / 2f;
                r.set(x0, sp(106), x0 + tw, sp(114));
                p.setColor(alpha(M, 0x22));
                c.drawRoundRect(r, sp(4), sp(4), p);
                r.set(x0, sp(106), x0 + Math.max(sp(8), tw * min / 60f), sp(114));
                p.setColor(A);
                c.drawRoundRect(r, sp(4), sp(4), p);
                if (date) text(c, fmt("EEE d MMM", t), cx, sp(136), sp(14), reg, soft, Paint.Align.CENTER);
                break;
            }
            case 33: { // Brackets: the time framed by four corners
                float x0 = sp(16);
                float x1 = w - sp(16);
                float y0 = sp(8);
                float y1 = sp(128);
                float k = sp(22);
                float sw = sp(3);
                line(c, x0, y0, x0 + k, y0, sw, A);
                line(c, x0, y0, x0, y0 + k, sw, A);
                line(c, x1, y0, x1 - k, y0, sw, A);
                line(c, x1, y0, x1, y0 + k, sw, A);
                line(c, x0, y1, x0 + k, y1, sw, A);
                line(c, x0, y1, x0, y1 - k, sw, A);
                line(c, x1, y1, x1 - k, y1, sw, A);
                line(c, x1, y1, x1, y1 - k, sw, A);
                text(c, time, cx, sp(96), sp(80), med, M, Paint.Align.CENTER);
                if (date) text(c, fmt("EEEE d MMMM", t), cx, sp(152), sp(14), reg, soft, Paint.Align.CENTER);
                break;
            }
            case 34: { // Neon: glowing tube letters in the accent
                p.setShadowLayer(sp(16), 0, 0, A);
                outline(c, time, cx, sp(104), sp(100), bold, A, sp(3.5f), Paint.Align.CENTER);
                p.setShadowLayer(sp(4), 0, 0, A);
                outline(c, time, cx, sp(104), sp(100), bold, alpha(M, 0xF2), sp(1.4f), Paint.Align.CENTER);
                p.setShadowLayer(0, 0, 0, 0);
                if (date) text(c, fmt("EEE d MMM", t), cx, sp(138), sp(14), med, alpha(A, 0xCC), Paint.Align.CENTER);
                break;
            }
            case 35: { // Week rail: the time over the seven days, today lit
                text(c, time, cx, sp(88), sp(86), bold, M, Paint.Align.CENTER);
                String[] days = {"M", "T", "W", "T", "F", "S", "S"};
                int today = (now.get(Calendar.DAY_OF_WEEK) + 5) % 7;
                float step = (w - sp(40)) / 6f;
                for (int i = 0; i < 7; i++) {
                    float x = sp(20) + i * step;
                    if (i == today) {
                        p.setColor(A);
                        c.drawCircle(x, sp(128), sp(15), p);
                    }
                    text(c, days[i], x, sp(134), sp(15), med, i == today ? 0xFF111513 : soft, Paint.Align.CENTER);
                }
                if (date) text(c, fmt("d MMMM", t), cx, sp(158), sp(12), reg, soft, Paint.Align.CENTER);
                break;
            }
            case 36: { // Day progress: how much of today has gone
                text(c, time, sp(4), sp(86), sp(86), bold, M, Paint.Align.LEFT);
                float pct = (hour * 60 + min) / 1440f;
                r.set(sp(6), sp(108), w - sp(6), sp(120));
                p.setColor(alpha(M, 0x22));
                c.drawRoundRect(r, sp(6), sp(6), p);
                r.set(sp(6), sp(108), sp(6) + (w - sp(12)) * pct, sp(120));
                p.setColor(A);
                c.drawRoundRect(r, sp(6), sp(6), p);
                text(c, Math.round(pct * 100) + "% of today", sp(6), sp(146), sp(13), med, soft, Paint.Align.LEFT);
                if (date) text(c, fmt("EEE d MMM", t), w - sp(6), sp(146), sp(13), reg, soft, Paint.Align.RIGHT);
                break;
            }
            default: { // Classic
                text(c, time, cx, sp(80), sp(72), reg, M, Paint.Align.CENTER);
                if (date && HEIGHT_DP[0] > 100) text(c, fmt("EEE d MMM", t), cx, sp(100), sp(13), reg, soft, Paint.Align.CENTER);
                break;
            }
        }
        p.setLetterSpacing(0f);
        p.setShadowLayer(0, 0, 0, 0);
        p.setShader(null);
    }
}
