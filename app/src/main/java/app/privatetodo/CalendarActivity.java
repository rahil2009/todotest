package app.privatetodo;

import android.app.Activity;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Month calendar. Days with tasks carry dots (repeating tasks show on every day they land on); tapping a day
 * lists its tasks underneath, and the bar at the bottom adds a new task to the selected day.
 */
public class CalendarActivity extends Activity {
    private TaskDb db;
    private float density;
    private int builtDark;
    private long month;  // first day of the shown month, start of day
    private long day;    // selected day, start of day
    private List<Task> all = new ArrayList<>();

    private TextView monthLabel;
    private LinearLayout grid;
    private TextView dayLabel;
    private LinearLayout list;

    @Override
    protected void onCreate(Bundle b) {
        Pal.init(this);
        builtDark = Pal.themeRes();
        setTheme(Pal.themeRes());
        super.onCreate(b);
        density = getResources().getDisplayMetrics().density;
        db = new TaskDb(this);
        day = startOfDay(System.currentTimeMillis());
        month = firstOfMonth(day);
        setContentView(build());
    }

    @Override
    protected void onResume() {
        super.onResume();
        Pal.init(this);
        if (Pal.themeRes() != builtDark) {
            recreate();
            return;
        }
        render();
    }

    @Override
    protected void onDestroy() {
        db.close();
        super.onDestroy();
    }

    // ------------------------------------------------------------------ build

    private View build() {
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(Pal.bg);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        frame.addView(root, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(12), dp(12), dp(16), 0);
        IconView back = new IconView(this, IconView.BACK);
        back.setContentDescription("Back");
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView title = text("Calendar", 30, Pal.ink, true);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView today = pill("Today", false);
        today.setOnClickListener(v -> {
            Hap.tick(v);
            day = startOfDay(System.currentTimeMillis());
            month = firstOfMonth(day);
            render();
        });
        header.addView(today);
        root.addView(header);

        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        nav.setPadding(dp(16), dp(10), dp(16), 0);
        TextView prev = text("‹", 28, Pal.ink, true);
        prev.setGravity(Gravity.CENTER);
        prev.setContentDescription("Previous month");
        prev.setOnClickListener(v -> shiftMonth(-1, v));
        nav.addView(prev, new LinearLayout.LayoutParams(dp(48), dp(40)));
        monthLabel = text("", 18, Pal.ink, true);
        monthLabel.setGravity(Gravity.CENTER);
        nav.addView(monthLabel, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView next = text("›", 28, Pal.ink, true);
        next.setGravity(Gravity.CENTER);
        next.setContentDescription("Next month");
        next.setOnClickListener(v -> shiftMonth(1, v));
        nav.addView(next, new LinearLayout.LayoutParams(dp(48), dp(40)));
        root.addView(nav);

        // weekday letters, Monday first
        LinearLayout names = new LinearLayout(this);
        names.setPadding(dp(12), dp(4), dp(12), dp(2));
        String[] dn = {"M", "T", "W", "T", "F", "S", "S"};
        for (String n : dn) {
            TextView t = text(n, 12, Pal.muted, true);
            t.setGravity(Gravity.CENTER);
            names.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        root.addView(names);

        grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(dp(12), 0, dp(12), dp(6));
        root.addView(grid, new LinearLayout.LayoutParams(-1, -2));

        dayLabel = text("", 16, Pal.ink, true);
        dayLabel.setPadding(dp(24), dp(10), dp(24), dp(4));
        root.addView(dayLabel);

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(16), dp(2), dp(16), dp(96));
        scroll.addView(list, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        // add bar
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(20), 0, dp(6), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Pal.raised);
        bg.setCornerRadius(dp(26));
        bar.setBackground(bg);
        bar.setElevation(dp(3));
        TextView hint = text("Add a task on this day…", 16, Pal.muted, false);
        bar.addView(hint, new LinearLayout.LayoutParams(0, -2, 1f));
        IconView plus = new IconView(this, IconView.PLUS);
        plus.setTint(Pal.onAccent);
        GradientDrawable pg = new GradientDrawable();
        pg.setShape(GradientDrawable.OVAL);
        pg.setColor(Pal.accent);
        plus.setBackground(pg);
        plus.setContentDescription("Add a task");
        bar.addView(plus, new LinearLayout.LayoutParams(dp(42), dp(42)));
        bar.setOnClickListener(v -> {
            Hap.tick(v);
            newTask();
        });
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(-1, dp(52));
        blp.gravity = Gravity.BOTTOM;
        blp.setMargins(dp(16), 0, dp(16), dp(20));
        frame.addView(bar, blp);
        return frame;
    }

    private void shiftMonth(int dir, View v) {
        Hap.tick(v);
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(month + 12 * 3_600_000L);
        c.add(Calendar.MONTH, dir);
        month = firstOfMonth(c.getTimeInMillis());
        // keep the selection inside the month shown
        Calendar sel = Calendar.getInstance();
        sel.setTimeInMillis(day);
        Calendar m = Calendar.getInstance();
        m.setTimeInMillis(month + 12 * 3_600_000L);
        if (sel.get(Calendar.MONTH) != m.get(Calendar.MONTH) || sel.get(Calendar.YEAR) != m.get(Calendar.YEAR)) {
            day = month;
        }
        render();
    }

    // ----------------------------------------------------------------- render

    private void render() {
        all = db.list(TaskDb.ALL);
        long todayStart = startOfDay(System.currentTimeMillis());
        monthLabel.setText(new SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(new Date(month + 12 * 3_600_000L)));

        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(month + 12 * 3_600_000L);
        int lead = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7;       // blank cells before the 1st (Monday first)
        int dim = c.getActualMaximum(Calendar.DAY_OF_MONTH);
        int rows = (lead + dim + 6) / 7;
        grid.removeAllViews();
        for (int r = 0; r < rows; r++) {
            LinearLayout line = new LinearLayout(this);
            for (int col = 0; col < 7; col++) {
                int n = r * 7 + col - lead + 1;
                if (n < 1 || n > dim) {
                    line.addView(new View(this), new LinearLayout.LayoutParams(0, dp(52), 1f));
                    continue;
                }
                final long d0 = dayAt(month, n);
                line.addView(cell(d0, n, d0 == day, d0 == todayStart), new LinearLayout.LayoutParams(0, dp(52), 1f));
            }
            grid.addView(line);
        }

        dayLabel.setText(new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(new Date(day + 12 * 3_600_000L)));
        list.removeAllViews();
        List<Task> today = tasksOn(day);
        if (today.isEmpty()) {
            TextView empty = text("Nothing on this day. Use the bar below to add something.", 15, Pal.muted, false);
            empty.setPadding(dp(8), dp(12), dp(8), 0);
            list.addView(empty);
        }
        for (Task t : today) list.addView(row(t));
        Fonts.apply(getWindow().getDecorView());
    }

    private List<Task> tasksOn(long d0) {
        List<Task> out = new ArrayList<>();
        for (Task t : all) {
            Task o = Reminders.onDay(t, d0);
            if (o != null) out.add(o);
        }
        Collections.sort(out, (a, b) -> Long.compare(a.due, b.due));
        return out;
    }

    private View cell(final long d0, int n, boolean sel, boolean isToday) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView num = text(String.valueOf(n), 16, sel ? Pal.onSel : Pal.ink, true);
        num.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(sel ? Pal.sel : 0);
        if (!sel && isToday) g.setStroke(dp(2), Pal.accent);
        num.setBackground(g);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(dp(38), dp(38));
        nlp.topMargin = dp(2);
        cell.addView(num, nlp);
        int count = 0;
        int open = 0;
        for (Task t : all) {
            Task o = Reminders.onDay(t, d0);
            if (o == null) continue;
            count++;
            if (!o.done) open++;
        }
        LinearLayout dots = new LinearLayout(this);
        dots.setGravity(Gravity.CENTER);
        for (int k = 0; k < Math.min(3, count); k++) {
            View dot = new View(this);
            GradientDrawable dg = new GradientDrawable();
            dg.setShape(GradientDrawable.OVAL);
            dg.setColor(open > 0 ? Pal.accent : Pal.muted);
            dot.setBackground(dg);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(5), dp(5));
            dlp.setMargins(dp(1), 0, dp(1), 0);
            dots.addView(dot, dlp);
        }
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-2, dp(8));
        dl.topMargin = dp(2);
        dl.gravity = Gravity.CENTER_HORIZONTAL;
        cell.addView(dots, dl);
        cell.setOnClickListener(v -> {
            Hap.tick(v);
            day = d0;
            render();
        });
        return cell;
    }

    private View row(final Task t) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4), 0, dp(14), 0);
        GradientDrawable g = new GradientDrawable();
        g.setColor(Pal.card);
        g.setCornerRadius(dp(16));
        row.setBackground(g);
        row.setMinimumHeight(dp(60));
        IconView check = new IconView(this, IconView.CHECK);
        check.setOn(t.done);
        check.setContentDescription(t.done ? "Mark not done" : "Mark done");
        check.setOnClickListener(v -> {
            if (t.ghost) {
                android.widget.Toast.makeText(this, "It repeats: you can tick it off on the day",
                        android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            Hap.confirm(v);
            db.setDone(t.id, !t.done);
            if (t.done) Reminders.schedule(this, db.get(t.id));
            else Reminders.cancel(this, t.id);
            render();
        });
        row.addView(check, new LinearLayout.LayoutParams(dp(44), dp(44)));
        TextView title = text(t.emoji.isEmpty() ? t.title : t.emoji + "  " + t.title, 16,
                t.done ? Pal.muted : Pal.ink, false);
        if (t.done) title.setPaintFlags(title.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        title.setMaxLines(2);
        title.setPadding(dp(2), dp(8), dp(4), dp(8));
        if (t.repeat > 0) {
            title.setCompoundDrawablesWithIntrinsicBounds(null, null,
                    new RepeatDrawable(t.done ? Pal.muted : Pal.accent, dp(15)), null);
            title.setCompoundDrawablePadding(dp(6));
        }
        row.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t.due);
        TextView time = text(String.format(Locale.US, "%02d:%02d", c.get(Calendar.HOUR_OF_DAY),
                c.get(Calendar.MINUTE)), 14, Pal.muted, true);
        row.addView(time);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(8);
        row.setLayoutParams(lp);
        return row;
    }

    // ------------------------------------------------------------------- add

    private void newTask() {
        final EditText in = new EditText(this);
        in.setHint("What needs doing?");
        in.setSingleLine(true);
        in.setTextColor(Pal.ink);
        in.setHintTextColor(Pal.muted);
        Calendar now = Calendar.getInstance();
        int startHour = day == startOfDay(System.currentTimeMillis()) ? Math.min(23, now.get(Calendar.HOUR_OF_DAY) + 1) : 9;
        final String[] hours = new String[24];
        for (int i = 0; i < 24; i++) hours[i] = String.format(Locale.US, "%02d", i);
        final String[] mins = new String[12];
        for (int i = 0; i < 12; i++) mins[i] = String.format(Locale.US, "%02d", i * 5);
        final Wheel hw = new Wheel(this);
        hw.setMinValue(0);
        hw.setMaxValue(23);
        hw.setDisplayedValues(hours);
        hw.setWrapSelectorWheel(true);
        hw.setValue(startHour);
        final Wheel mw = new Wheel(this);
        mw.setMinValue(0);
        mw.setMaxValue(11);
        mw.setDisplayedValues(mins);
        mw.setWrapSelectorWheel(true);
        mw.setValue(0);
        LinearLayout wheels = new LinearLayout(this);
        wheels.setGravity(Gravity.CENTER);
        wheels.setPadding(dp(24), dp(8), dp(24), 0);
        GradientDrawable wg = new GradientDrawable();
        wg.setColor(Pal.chip);
        wg.setCornerRadius(dp(24));
        wheels.setBackground(wg);
        wheels.addView(hw, new LinearLayout.LayoutParams(0, -2, 1f));
        wheels.addView(text(":", 22, Pal.ink, true));
        wheels.addView(mw, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        box.addView(in, new LinearLayout.LayoutParams(-1, -2));
        TextView lab = text("At what time?", 13, Pal.muted, true);
        lab.setPadding(dp(4), dp(14), 0, dp(6));
        box.addView(lab);
        box.addView(wheels, new LinearLayout.LayoutParams(-1, -2));
        new Dlg.Builder(this)
                .setTitle("New task · " + new SimpleDateFormat("EEE d MMM", Locale.getDefault())
                        .format(new Date(day + 12 * 3_600_000L)))
                .setView(box)
                .setPositiveButton("Add", (d, w) -> {
                    String text = in.getText().toString().trim();
                    if (text.isEmpty()) return;
                    long at = dayAtTime(day, hw.getValue(), mw.getValue() * 5);
                    int repeat = 0;
                    int unit = 0;
                    SmartDate.Result r = SmartDate.parse(this, text, System.currentTimeMillis());
                    if (r.found) { // "@fri 6pm" typed here wins over the wheels
                        text = r.title;
                        at = r.due;
                        repeat = r.repeat;
                        unit = r.unit;
                    }
                    if (text.isEmpty()) return;
                    long id = db.add(text, at, repeat, unit, false);
                    Reminders.schedule(this, db.get(id));
                    Hap.confirm(list);
                    render();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ---------------------------------------------------------------- helpers

    private static long startOfDay(long t) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private static long firstOfMonth(long t) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t);
        c.set(Calendar.DAY_OF_MONTH, 1);
        return startOfDay(c.getTimeInMillis());
    }

    /** Start of day number {@code n} of the month that starts at {@code first}. */
    private static long dayAt(long first, int n) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(first + 12 * 3_600_000L);
        c.set(Calendar.DAY_OF_MONTH, n);
        return startOfDay(c.getTimeInMillis());
    }

    private static long dayAtTime(long dayStart, int h, int m) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(dayStart + 12 * 3_600_000L);
        c.set(Calendar.HOUR_OF_DAY, h);
        c.set(Calendar.MINUTE, m);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private int dp(int v) {
        return Math.round(v * density);
    }

    private TextView pill(String s, boolean on) {
        TextView t = text(s, 14, on ? Pal.onSel : Pal.muted, true);
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(dp(40));
        t.setPadding(dp(14), 0, dp(14), 0);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(20));
        g.setColor(on ? Pal.sel : Pal.chip);
        t.setBackground(g);
        return t;
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }
}
