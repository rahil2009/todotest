package app.privatetodo;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Currency;
import java.util.Locale;

/** Small shared pieces of the Lent screens: formatting, and the cards, chips and bars they are built from. */
final class LentUi {
    private LentUi() { }

    static final int AMBER = 0xFFF2A93B;
    static final int GREEN = 0xFF2E9E6B;
    static final String[] SYMBOLS = {"₹", "$", "€", "£", "¥", "₦", "₨", "₱", ""};

    static float dn(Context c) {
        return c.getResources().getDisplayMetrics().density;
    }

    static int dp(Context c, float v) {
        return Math.round(v * dn(c));
    }

    // ------------------------------------------------------------------ money and dates

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("lent", Context.MODE_PRIVATE);
    }

    /** The currency sign in front of amounts: the one chosen on the Lent screen, else the phone's own. */
    static String symbol(Context c) {
        SharedPreferences p = sp(c);
        if (p.contains("cur")) return p.getString("cur", "");
        try {
            return Currency.getInstance(Locale.getDefault()).getSymbol(Locale.getDefault());
        } catch (RuntimeException e) {
            return "";
        }
    }

    static void setSymbol(Context c, String s) {
        sp(c).edit().putString("cur", s).apply();
    }

    /** 24500 cents -> "245", 1250 -> "12.50", with the currency sign. */
    static String money(Context c, long cents) {
        NumberFormat nf = NumberFormat.getInstance(Locale.getDefault());
        nf.setMaximumFractionDigits(2);
        nf.setMinimumFractionDigits(cents % 100 == 0 ? 0 : 2);
        return symbol(c) + nf.format(cents / 100.0);
    }

    /** What the person typed ("1,250.50", "800") as cents; -1 when it is not a number. */
    static long parse(String s) {
        if (s == null) return -1;
        String t = s.replace(",", "").replace(" ", "").trim();
        if (t.isEmpty()) return -1;
        try {
            return new java.math.BigDecimal(t).multiply(new java.math.BigDecimal(100)).longValue();
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    static String date(long day) {
        long t = LentDb.today();
        if (day == t) return "today";
        if (day == t - 1) return "yesterday";
        LocalDate d = LocalDate.ofEpochDay(day);
        String f = d.getYear() == LocalDate.now().getYear() ? "d MMM" : "d MMM yyyy";
        return d.format(DateTimeFormatter.ofPattern(f, Locale.getDefault()));
    }

    /** "Due today", "in 8 days", "Overdue 3 days". */
    static String dueLabel(long due) {
        long n = due - LentDb.today();
        if (n == 0) return "Due today";
        if (n == 1) return "Due tomorrow";
        if (n > 1) return n <= 60 ? "in " + n + " days" : "by " + date(due);
        return n == -1 ? "Overdue 1 day" : "Overdue " + (-n) + " days";
    }

    static boolean overdue(LentDb.Loan l) {
        return !l.closed && l.due > 0 && l.due < LentDb.today();
    }

    static String clock(Context c, int minutes) {
        int h = minutes / 60;
        int m = minutes % 60;
        if (android.text.format.DateFormat.is24HourFormat(c)) return String.format(Locale.ROOT, "%02d:%02d", h, m);
        return String.format(Locale.ROOT, "%d:%02d %s", h % 12 == 0 ? 12 : h % 12, m, h < 12 ? "AM" : "PM");
    }

    static String before(int days) {
        return days == 0 ? "the same day" : days == 1 ? "1 day before" : days + " days before";
    }

    // ------------------------------------------------------------------ building blocks

    static TextView text(Context c, String s, int sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    static GradientDrawable round(Context c, int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radiusDp * dn(c));
        return g;
    }

    /** A white (palette card) rounded box. */
    static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(c, 18), dp(c, 16), dp(c, 18), dp(c, 16));
        l.setBackground(round(c, Pal.card, 22));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(c, 12);
        l.setLayoutParams(lp);
        return l;
    }

    /** A pill: filled with {@code fill} and {@code ink} text. */
    static TextView pill(Context c, String s, int fill, int ink, int sp) {
        TextView t = text(c, s, sp, ink, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(c, 14), dp(c, 7), dp(c, 14), dp(c, 7));
        t.setBackground(round(c, fill, 100));
        return t;
    }

    /** A filter / choice chip: selected = inverted colours. */
    static TextView chip(Context c, String s, boolean on) {
        TextView t = text(c, s, 14, on ? Pal.onSel : Pal.muted, true);
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(dp(c, 38));
        t.setPadding(dp(c, 16), 0, dp(c, 16), 0);
        t.setBackground(round(c, on ? Pal.sel : Pal.chip, 100));
        return t;
    }

    static void margins(View v, int l, int t, int r, int b) {
        Context c = v.getContext();
        LinearLayout.LayoutParams lp = v.getLayoutParams() instanceof LinearLayout.LayoutParams
                ? (LinearLayout.LayoutParams) v.getLayoutParams() : new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(dp(c, l), dp(c, t), dp(c, r), dp(c, b));
        v.setLayoutParams(lp);
    }

    static int tone(String name) {
        int h = 0;
        for (int i = 0; i < name.length(); i++) h = h * 31 + Character.toLowerCase(name.charAt(i));
        return HabitDb.COLORS[Math.abs(h) % HabitDb.COLORS.length];
    }

    /** A round tile with the person's first letter (or an emoji) in the colour taken from their name. */
    static final class Avatar extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final String label;
        private final int color;
        private final boolean emoji;

        Avatar(Context c, String name, String emoji) {
            super(c);
            boolean e = emoji != null && !emoji.isEmpty();
            this.emoji = e;
            this.label = e ? emoji : (name.trim().isEmpty() ? "?" : name.trim().substring(0, 1).toUpperCase(Locale.getDefault()));
            this.color = tone(name);
        }

        @Override
        protected void onDraw(Canvas c) {
            float r = Math.min(getWidth(), getHeight()) / 2f;
            p.setStyle(Paint.Style.FILL);
            p.setColor((color & 0x00FFFFFF) | (Pal.dark ? 0x40000000 : 0x38000000));
            c.drawCircle(getWidth() / 2f, getHeight() / 2f, r, p);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setTextSize(r * (emoji ? 0.95f : 0.9f));
            p.setColor(emoji ? Pal.ink : darker(color));
            Paint.FontMetrics fm = p.getFontMetrics();
            c.drawText(label, getWidth() / 2f, getHeight() / 2f - (fm.ascent + fm.descent) / 2f, p);
        }

        private static int darker(int c) {
            if (Pal.dark) return c;
            float[] hsv = new float[3];
            android.graphics.Color.colorToHSV(c, hsv);
            hsv[2] *= 0.55f;
            return android.graphics.Color.HSVToColor(hsv);
        }
    }

    /** A thin rounded progress bar. */
    static final class Bar extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private final float frac;
        private final int color;

        Bar(Context c, float frac, int color) {
            super(c);
            this.frac = Math.max(0f, Math.min(1f, frac));
            this.color = color;
        }

        @Override
        protected void onDraw(Canvas c) {
            float h = getHeight();
            p.setColor(Pal.chip);
            r.set(0, 0, getWidth(), h);
            c.drawRoundRect(r, h / 2f, h / 2f, p);
            if (frac <= 0f) return;
            p.setColor(color);
            r.set(0, 0, Math.max(h, getWidth() * frac), h);
            c.drawRoundRect(r, h / 2f, h / 2f, p);
        }
    }
}
