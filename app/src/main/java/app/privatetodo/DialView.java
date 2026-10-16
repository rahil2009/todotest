package app.privatetodo;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * The app's one time-input control: swipe-up/down dials.
 * "In" mode = amount dial + unit dial (min / hr / day / week).
 * "At" mode = day dial + hour dial + minute dial (+ AM/PM on 12-hour phones).
 * Used for custom snooze on the lock screen and for the reminder time in the task editor.
 */
final class DialView extends LinearLayout {
    // Read from the palette when the control is created, so dark mode applies.
    private final int ACCENT = Pal.accent;
    private final int INK = Pal.ink;
    private final int MUTED = Pal.muted;
    private final int CARD = Pal.chip;
    private final int SEL = Pal.sel;
    private final int ON_SEL = Pal.onSel;
    private static final int[] UNIT_MINUTES = {1, 60, 1440, 10080};
    private static final int DAYS_AHEAD = 365;

    private final float density;
    private final boolean allowAbsolute;
    private final boolean withMonths;
    private final boolean is24;
    private final long dayBase; // midnight today when the control was created

    private final NumberPicker amount;
    private final NumberPicker unit;
    private final NumberPicker day;
    private final NumberPicker hour;
    private final NumberPicker minute;
    private final NumberPicker ampm;
    private final LinearLayout relRow;
    private final LinearLayout absRow;
    private final TextView tabIn;
    private final TextView tabAt;

    private boolean absolute;
    private boolean quiet;
    private Runnable onChange;

    DialView(Context c, boolean allowAbsolute) {
        this(c, allowAbsolute, false);
    }

    /** {@code withMonths} adds a "month" unit; used for repeat intervals like "every 2 weeks". */
    DialView(Context c, boolean allowAbsolute, boolean withMonths) {
        super(c);
        this.withMonths = withMonths;
        this.allowAbsolute = allowAbsolute;
        this.density = c.getResources().getDisplayMetrics().density;
        this.is24 = DateFormat.is24HourFormat(c);
        setOrientation(VERTICAL);

        Calendar mid = Calendar.getInstance();
        mid.set(Calendar.HOUR_OF_DAY, 0);
        mid.set(Calendar.MINUTE, 0);
        mid.set(Calendar.SECOND, 0);
        mid.set(Calendar.MILLISECOND, 0);
        dayBase = mid.getTimeInMillis();

        // ---- tabs (In / At)
        tabIn = tab(c, "In");
        tabAt = tab(c, "At");
        if (allowAbsolute) {
            LinearLayout tabs = new LinearLayout(c);
            tabs.setOrientation(HORIZONTAL);
            LayoutParams lp = new LayoutParams(-2, -2);
            lp.rightMargin = dp(8);
            tabs.addView(tabIn, lp);
            tabs.addView(tabAt, new LayoutParams(-2, -2));
            LayoutParams tlp = new LayoutParams(-1, -2);
            tlp.bottomMargin = dp(8);
            addView(tabs, tlp);
            tabIn.setOnClickListener(v -> setAbsolute(false, true));
            tabAt.setOnClickListener(v -> setAbsolute(true, true));
        }

        // ---- relative dials
        amount = picker(c, 1, 99, null, true);
        amount.setValue(15);
        unit = withMonths
                ? picker(c, 0, 4, new String[]{"min", "hr", "day", "week", "month"}, true)
                : picker(c, 0, 3, new String[]{"min", "hr", "day", "week"}, true);
        relRow = row(c);
        relRow.addView(amount, new LayoutParams(0, -2, 1f));
        relRow.addView(unit, new LayoutParams(0, -2, 1f));

        // ---- absolute dials
        String[] dayNames = new String[DAYS_AHEAD + 1];
        SimpleDateFormat fmt = new SimpleDateFormat("EEE d MMM", Locale.getDefault());
        Calendar cal = Calendar.getInstance();
        for (int i = 0; i <= DAYS_AHEAD; i++) {
            cal.setTimeInMillis(dayBase);
            cal.add(Calendar.DAY_OF_YEAR, i);
            dayNames[i] = i == 0 ? "Today" : i == 1 ? "Tomorrow" : fmt.format(new Date(cal.getTimeInMillis()));
        }
        day = picker(c, 0, DAYS_AHEAD, dayNames, false);

        String[] hours;
        if (is24) {
            hours = new String[24];
            for (int i = 0; i < 24; i++) hours[i] = two(i);
            hour = picker(c, 0, 23, hours, true);
        } else {
            hours = new String[12];
            for (int i = 0; i < 12; i++) hours[i] = String.valueOf(i + 1);
            hour = picker(c, 1, 12, hours, true);
        }
        String[] mins = new String[60];
        for (int i = 0; i < 60; i++) mins[i] = two(i);
        minute = picker(c, 0, 59, mins, true);
        ampm = picker(c, 0, 1, new String[]{"AM", "PM"}, true);

        absRow = row(c);
        absRow.addView(day, new LayoutParams(0, -2, 2.2f));
        absRow.addView(hour, new LayoutParams(0, -2, 1f));
        absRow.addView(minute, new LayoutParams(0, -2, 1f));
        if (!is24) absRow.addView(ampm, new LayoutParams(0, -2, 1f));

        addView(relRow, new LayoutParams(-1, -2));
        addView(absRow, new LayoutParams(-1, -2));
        setAbsolute(false, false);
    }

    // ---------------------------------------------------------- public API

    void setOnChange(Runnable r) {
        this.onChange = r;
    }

    /** Preset the "In" dials, e.g. 15 and unit index 0 = 15 min. Does not fire onChange. */
    void setRelative(int amountValue, int unitIndex) {
        quiet = true;
        amount.setValue(amountValue);
        unit.setValue(unitIndex);
        quiet = false;
        setAbsolute(false, false);
    }

    /** Preset the "At" dials to a moment and switch to "At". Does not fire onChange. */
    void setTime(long millis) {
        if (!allowAbsolute) return;
        quiet = true;
        Calendar t = Calendar.getInstance();
        t.setTimeInMillis(millis);
        Calendar mid = (Calendar) t.clone();
        mid.set(Calendar.HOUR_OF_DAY, 0);
        mid.set(Calendar.MINUTE, 0);
        mid.set(Calendar.SECOND, 0);
        mid.set(Calendar.MILLISECOND, 0);
        long days = Math.round((mid.getTimeInMillis() - dayBase) / 86_400_000.0);
        day.setValue((int) Math.max(0, Math.min(DAYS_AHEAD, days)));
        int h = t.get(Calendar.HOUR_OF_DAY);
        if (is24) {
            hour.setValue(h);
        } else {
            hour.setValue(h % 12 == 0 ? 12 : h % 12);
            ampm.setValue(h >= 12 ? 1 : 0);
        }
        minute.setValue(t.get(Calendar.MINUTE));
        quiet = false;
        setAbsolute(true, false);
    }

    int selectedMinutes() {
        return amount.getValue() * UNIT_MINUTES[Math.min(unit.getValue(), 3)];
    }

    /** For repeat dials: the N in "every N units" (1 to 99). */
    int everyAmount() {
        return amount.getValue();
    }

    /** For repeat dials: 0 min, 1 hour, 2 day, 3 week, 4 month. */
    int everyUnit() {
        return unit.getValue();
    }

    /** The moment the dials currently describe. "In" is measured from now. */
    long selectedTime() {
        if (!absolute) return System.currentTimeMillis() + selectedMinutes() * 60_000L;
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(dayBase);
        c.add(Calendar.DAY_OF_YEAR, day.getValue());
        int h = is24 ? hour.getValue() : (hour.getValue() % 12) + (ampm.getValue() == 1 ? 12 : 0);
        c.set(Calendar.HOUR_OF_DAY, h);
        c.set(Calendar.MINUTE, minute.getValue());
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    /** "15 min", "1 hour", "3 days"... for the "In" dials. */
    String relativeLabel() {
        int a = amount.getValue();
        String[] one = {"min", "hour", "day", "week", "month"};
        String[] many = {"min", "hours", "days", "weeks", "months"};
        return a + " " + (a == 1 ? one[unit.getValue()] : many[unit.getValue()]);
    }

    // ------------------------------------------------------------- internals

    private void setAbsolute(boolean abs, boolean fire) {
        absolute = abs && allowAbsolute;
        relRow.setVisibility(absolute ? GONE : VISIBLE);
        absRow.setVisibility(absolute ? VISIBLE : GONE);
        styleTab(tabIn, !absolute);
        styleTab(tabAt, absolute);
        if (fire) changed();
    }

    private void changed() {
        if (!quiet && onChange != null) onChange.run();
    }

    private NumberPicker picker(Context c, int min, int max, String[] labels, boolean wrap) {
        NumberPicker p = new NumberPicker(c);
        p.setMinValue(min);
        p.setMaxValue(max);
        if (labels != null) p.setDisplayedValues(labels);
        p.setWrapSelectorWheel(wrap);
        // Stops the soft keyboard popping up when a dial is tapped.
        p.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
        if (Build.VERSION.SDK_INT >= 29) {
            p.setTextColor(INK);
            p.setTextSize(20 * density);
            p.setSelectionDivider(new ColorDrawable(ACCENT));
            p.setSelectionDividerHeight(dp(2));
        }
        p.setOnValueChangedListener((v, o, n) -> changed());
        return p;
    }

    private LinearLayout row(Context c) {
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(HORIZONTAL);
        r.setGravity(Gravity.CENTER);
        r.setPadding(dp(8), dp(8), dp(8), dp(8));
        GradientDrawable g = new GradientDrawable();
        g.setColor(CARD);
        g.setCornerRadius(dp(24));
        r.setBackground(g);
        return r;
    }

    private TextView tab(Context c, String s) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(14);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(dp(40));
        t.setMinWidth(dp(64));
        t.setPadding(dp(14), 0, dp(14), 0);
        return t;
    }

    private void styleTab(TextView t, boolean on) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(20));
        g.setColor(on ? SEL : CARD);
        t.setBackground(g);
        t.setTextColor(on ? ON_SEL : MUTED);
    }

    private static String two(int v) {
        return v < 10 ? "0" + v : String.valueOf(v);
    }

    private int dp(int v) {
        return Math.round(v * density);
    }
}
