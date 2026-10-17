package app.privatetodo;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Plan mode: a week strip, the day's agenda split into Morning / Afternoon / Evening, a progress ring,
 * one-tap scheduling for tasks that have no time yet, and "Plan my day" which spreads them out by priority.
 * Everything is computed and stored on this device.
 */
public class PlanActivity extends Activity {
    private static final long DAY = 86_400_000L;

    private TaskDb db;
    private float density;
    private int builtDark;
    private long day; // start of the selected day
    private List<Task> all = new ArrayList<>();

    private TextView subtitle;
    private TextView monthLabel;
    private LinearLayout strip;
    private LinearLayout content;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Pal.init(this);
        builtDark = Pal.themeRes();
        setTheme(Pal.themeRes());
        super.onCreate(savedInstanceState);
        density = getResources().getDisplayMetrics().density;
        db = new TaskDb(this);
        day = startOfDay(System.currentTimeMillis());
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
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Pal.bg);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(12), dp(12), dp(16), 0);
        IconView back = new IconView(this, IconView.BACK);
        back.setContentDescription("Back");
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(text("Plan", 30, Pal.ink, true));
        subtitle = text("", 14, Pal.muted, false);
        titles.addView(subtitle);
        header.addView(titles, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView today = chip("Today", false);
        today.setOnClickListener(v -> {
            Hap.tick(v);
            day = startOfDay(System.currentTimeMillis());
            render();
        });
        header.addView(today);
        root.addView(header);

        // month + week arrows
        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        nav.setPadding(dp(16), dp(12), dp(16), 0);
        TextView prev = text("‹", 28, Pal.ink, true);
        prev.setGravity(Gravity.CENTER);
        prev.setContentDescription("Previous week");
        prev.setOnClickListener(v -> shiftWeek(-1, v));
        nav.addView(prev, new LinearLayout.LayoutParams(dp(48), dp(40)));
        monthLabel = text("", 16, Pal.ink, true);
        monthLabel.setGravity(Gravity.CENTER);
        nav.addView(monthLabel, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView next = text("›", 28, Pal.ink, true);
        next.setGravity(Gravity.CENTER);
        next.setContentDescription("Next week");
        next.setOnClickListener(v -> shiftWeek(1, v));
        nav.addView(next, new LinearLayout.LayoutParams(dp(48), dp(40)));
        root.addView(nav);

        strip = new LinearLayout(this);
        strip.setPadding(dp(12), dp(4), dp(12), dp(8));
        root.addView(strip, new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(4), dp(16), dp(32));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        return root;
    }

    private void shiftWeek(int dir, View v) {
        Hap.tick(v);
        day = startOfDay(day + dir * 7 * DAY + 12 * 3_600_000L);
        render();
    }

    // ----------------------------------------------------------------- render

    private void render() {
        all = db.list(TaskDb.ALL);
        long todayStart = startOfDay(System.currentTimeMillis());

        subtitle.setText(new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(new Date(day)));
        monthLabel.setText(new SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(new Date(day)));

        // week strip, Monday first
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(day);
        int back = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        long weekStart = startOfDay(day - back * DAY + 12 * 3_600_000L);
        strip.removeAllViews();
        SimpleDateFormat letter = new SimpleDateFormat("EEE", Locale.getDefault());
        for (int i = 0; i < 7; i++) {
            final long d0 = startOfDay(weekStart + i * DAY + 12 * 3_600_000L);
            boolean sel = d0 == day;
            boolean isToday = d0 == todayStart;
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            TextView dow = text(letter.format(new Date(d0)).toUpperCase(Locale.getDefault()), 11,
                    Pal.muted, true);
            dow.setGravity(Gravity.CENTER);
            cell.addView(dow, new LinearLayout.LayoutParams(-1, -2));
            Calendar dc = Calendar.getInstance();
            dc.setTimeInMillis(d0);
            TextView num = text(String.valueOf(dc.get(Calendar.DAY_OF_MONTH)), 16,
                    sel ? Pal.onSel : Pal.ink, true);
            num.setGravity(Gravity.CENTER);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(sel ? Pal.sel : 0);
            if (!sel && isToday) g.setStroke(dp(2), Pal.accent);
            num.setBackground(g);
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(dp(40), dp(40));
            nlp.topMargin = dp(4);
            nlp.gravity = Gravity.CENTER_HORIZONTAL;
            cell.addView(num, nlp);
            // up to three dots for what is due that day
            int pending = 0;
            for (Task t : all) {
                if (t.due >= d0 && t.due < d0 + DAY + 3_600_000L && startOfDay(t.due) == d0 && !t.done) {
                    pending++;
                }
            }
            LinearLayout dots = new LinearLayout(this);
            dots.setGravity(Gravity.CENTER);
            for (int k = 0; k < Math.min(3, pending); k++) {
                View dot = new View(this);
                GradientDrawable dg = new GradientDrawable();
                dg.setShape(GradientDrawable.OVAL);
                dg.setColor(Pal.accent);
                dot.setBackground(dg);
                LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(5), dp(5));
                dlp.setMargins(dp(1), 0, dp(1), 0);
                dots.addView(dot, dlp);
            }
            LinearLayout.LayoutParams dotsLp = new LinearLayout.LayoutParams(-2, dp(8));
            dotsLp.gravity = Gravity.CENTER_HORIZONTAL;
            cell.addView(dots, dotsLp);
            cell.setOnClickListener(v -> {
                Hap.tick(v);
                day = d0;
                render();
            });
            strip.addView(cell, new LinearLayout.LayoutParams(0, -2, 1f));
        }

        // sort the data for the selected day
        List<Task> overdue = new ArrayList<>();
        List<Task> morning = new ArrayList<>();
        List<Task> afternoon = new ArrayList<>();
        List<Task> evening = new ArrayList<>();
        List<Task> loose = new ArrayList<>();
        int total = 0;
        int done = 0;
        for (Task t : all) {
            if (t.due <= 0) {
                if (!t.done) loose.add(t);
                continue;
            }
            long td = startOfDay(t.due);
            if (td < day && day == todayStart && !t.done && td < todayStart) {
                overdue.add(t);
                continue;
            }
            if (td != day) continue;
            total++;
            if (t.done) done++;
            Calendar tc = Calendar.getInstance();
            tc.setTimeInMillis(t.due);
            int h = tc.get(Calendar.HOUR_OF_DAY);
            (h < 12 ? morning : h < 17 ? afternoon : evening).add(t);
        }
        sortByDue(morning);
        sortByDue(afternoon);
        sortByDue(evening);

        content.removeAllViews();
        content.addView(summaryCard(done, total, overdue.size(), loose.size()));

        addSection("Overdue", overdue, true);
        boolean pastDay = day < todayStart;
        addTimeline("Morning", morning, 6, 12, pastDay, loose);
        addTimeline("Afternoon", afternoon, 12, 17, pastDay, loose);
        addTimeline("Evening", evening, 17, 22, pastDay, loose);
        if (total == 0 && overdue.isEmpty() && pastDay) {
            TextView empty = text("Nothing planned for this day.", 15, Pal.muted, false);
            empty.setPadding(dp(8), dp(16), dp(8), 0);
            content.addView(empty);
        }

        if (!loose.isEmpty()) {
            content.addView(sectionLabel("No time yet · " + loose.size()));
            TextView auto = text("Plan my day", 16, Pal.onAccent, true);
            auto.setGravity(Gravity.CENTER);
            GradientDrawable ag = new GradientDrawable();
            ag.setColor(Pal.accent);
            ag.setCornerRadius(dp(26));
            auto.setBackground(ag);
            auto.setOnClickListener(v -> confirmAutoPlan(loose));
            LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(-1, dp(52));
            alp.bottomMargin = dp(8);
            content.addView(auto, alp);
            for (Task t : loose) content.addView(looseCard(t));
        }
        Fonts.apply(getWindow().getDecorView());
    }

    private View summaryCard(int done, int total, int overdue, int loose) {
        LinearLayout card = new LinearLayout(this);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable g = new GradientDrawable();
        g.setColor(Pal.card);
        g.setCornerRadius(dp(20));
        card.setBackground(g);
        RingView ring = new RingView(this);
        ring.set(done, total);
        card.addView(ring, new LinearLayout.LayoutParams(dp(64), dp(64)));
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setPadding(dp(16), 0, 0, 0);
        String head;
        if (total == 0) head = "A clear day";
        else if (done == total) head = "All done. Well played";
        else if (total <= 3) head = "A light day";
        else if (total <= 6) head = "A full day";
        else head = "A busy day";
        t.addView(text(head, 18, Pal.ink, true));
        StringBuilder sb = new StringBuilder();
        sb.append(total).append(total == 1 ? " task" : " tasks").append(" · ").append(done).append(" done");
        if (overdue > 0) sb.append(" · ").append(overdue).append(" overdue");
        TextView sub = text(sb.toString(), 14, Pal.muted, false);
        sub.setPadding(0, dp(2), 0, 0);
        t.addView(sub);
        if (loose > 0) {
            TextView hint = text(loose + " waiting for a time", 13, Pal.accent, true);
            hint.setPadding(0, dp(4), 0, 0);
            t.addView(hint);
        }
        card.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(8);
        card.setLayoutParams(lp);
        return card;
    }

    private void addSection(String name, List<Task> list, boolean overdue) {
        if (list.isEmpty()) return;
        content.addView(sectionLabel(name));
        for (Task t : list) content.addView(agendaRow(t, overdue));
    }

    /** A part of the day: tasks in time order, with hatched "free" blocks where nothing is planned. */
    private void addTimeline(String name, List<Task> list, int fromH, int toH, boolean pastDay,
                             List<Task> loose) {
        long now = System.currentTimeMillis();
        long winEnd = dayAt(day, toH, 0);
        long cursor = dayAt(day, fromH, 0);
        if (!pastDay && day == startOfDay(now) && now > cursor) {
            cursor = now - now % (15 * 60_000L) + 15 * 60_000L; // next quarter hour
        }
        List<View> views = new ArrayList<>();
        final long hour = 3_600_000L;
        for (Task t : list) {
            if (!pastDay && t.due - cursor >= hour && !t.done) views.add(freeBlock(cursor, t.due, loose));
            views.add(agendaRow(t, false));
            cursor = Math.max(cursor, t.due + 30 * 60_000L);
        }
        if (!pastDay && winEnd - cursor >= hour) views.add(freeBlock(cursor, winEnd, loose));
        if (views.isEmpty()) return;
        content.addView(sectionLabel(name));
        for (View v : views) content.addView(v);
    }

    /** Same shape as an agenda row: time on the left, a card-sized hatched block on the right. */
    private View freeBlock(final long from, final long to, final List<Task> loose) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
        rlp.bottomMargin = dp(8);
        row.setLayoutParams(rlp);
        row.addView(text(clock(from), 13, Pal.muted, true), new LinearLayout.LayoutParams(dp(52), -2));

        FreeBlock b = new FreeBlock(this);
        long mins = (to - from) / 60_000L;
        String len = mins >= 60 ? (mins / 60) + (mins % 60 == 0 ? " h" : " h " + (mins % 60) + " min") : mins + " min";
        b.setText("Free for " + len + "  \u00b7  tap to fill");
        b.setMinHeight(dp(60));
        row.addView(b, new LinearLayout.LayoutParams(0, dp(60), 1f));
        row.setContentDescription("Free from " + clock(from) + " to " + clock(to) + ". Tap to fill.");
        View.OnClickListener l = v -> {
            Hap.tick(v);
            fillSlot(from, to, loose);
        };
        row.setOnClickListener(l);
        b.setOnClickListener(l);
        return row;
    }

    private String clock(long t) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t);
        return String.format(Locale.US, "%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));
    }

    private void fillSlot(final long from, final long to, final List<Task> loose) {
        final String[] names = new String[loose.size() + 1];
        names[0] = "+ New task";
        for (int i = 0; i < loose.size(); i++) {
            Task t = loose.get(i);
            names[i + 1] = t.emoji.isEmpty() ? t.title : t.emoji + "  " + t.title;
        }
        new Dlg.Builder(this)
                .setTitle("Fill " + clock(from) + " \u2013 " + clock(to))
                .setItems(names, (d, i) -> {
                    final int hFrom = hourOf(from);
                    final int hTo = Math.min(23, hourOf(to - 60_000L));
                    if (i == 0) {
                        pickTime("When in this gap?", from, hFrom, hTo, at -> newTaskAt(at));
                    } else {
                        final Task t = loose.get(i - 1);
                        pickTime("Time for \u201C" + t.title + "\u201D", from, hFrom, hTo, at -> {
                            assign(t, at);
                            render();
                        });
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static int minuteOf(long t) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t);
        return c.get(Calendar.MINUTE);
    }

    private static int hourOf(long t) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t);
        return c.get(Calendar.HOUR_OF_DAY);
    }

    /** Hour + minute wheels (5 minute steps), starting at {@code start}; any time in [minH, maxH] is allowed. */
    private void pickTime(String title, long start, int minH, int maxH, final java.util.function.LongConsumer done) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(start);
        int h = Math.max(minH, Math.min(maxH, c.get(Calendar.HOUR_OF_DAY)));
        int m = c.get(Calendar.MINUTE) / 5;
        final String[] hours = new String[maxH - minH + 1];
        for (int i = 0; i < hours.length; i++) hours[i] = String.format(Locale.US, "%02d", minH + i);
        final String[] mins = new String[12];
        for (int i = 0; i < 12; i++) mins[i] = String.format(Locale.US, "%02d", i * 5);
        final Wheel hw = new Wheel(this);
        hw.setMinValue(0);
        hw.setMaxValue(hours.length - 1);
        hw.setDisplayedValues(hours);
        hw.setWrapSelectorWheel(hours.length > 5);
        hw.setValue(h - minH);
        final Wheel mw = new Wheel(this);
        mw.setMinValue(0);
        mw.setMaxValue(11);
        mw.setDisplayedValues(mins);
        mw.setWrapSelectorWheel(true);
        mw.setValue(m);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        row.setPadding(dp(24), dp(8), dp(24), 0);
        GradientDrawable g = new GradientDrawable();
        g.setColor(Pal.chip);
        g.setCornerRadius(dp(24));
        row.setBackground(g);
        row.addView(hw, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView colon = text(":", 22, Pal.ink, true);
        row.addView(colon);
        row.addView(mw, new LinearLayout.LayoutParams(0, -2, 1f));
        FrameLayout box = new FrameLayout(this);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        box.addView(row);
        final int baseH = minH;
        new Dlg.Builder(this)
                .setTitle(title)
                .setView(box)
                .setPositiveButton("Set", (d, w) -> done.accept(dayAt(day, baseH + hw.getValue(), mw.getValue() * 5)))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void newTaskAt(final long at) {
        final android.widget.EditText in = new android.widget.EditText(this);
        in.setHint("What needs doing?");
        in.setSingleLine(true);
        in.setTextColor(Pal.ink);
        in.setHintTextColor(Pal.muted);
        FrameLayout box = new FrameLayout(this);
        box.setPadding(dp(24), dp(8), dp(24), 0);
        box.addView(in, new FrameLayout.LayoutParams(-1, -2));
        new Dlg.Builder(this)
                .setTitle("New task at " + clock(at))
                .setView(box)
                .setPositiveButton("Add", (d, w) -> {
                    String title = in.getText().toString().trim();
                    if (title.isEmpty()) return;
                    long id = db.add(title, at, 0, 0, false);
                    Reminders.schedule(this, db.get(id));
                    Hap.confirm(content);
                    render();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Diagonal-hatched empty time block. */
    private static final class FreeBlock extends TextView {
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Path clip = new android.graphics.Path();
        private final float d;

        FreeBlock(Context c) {
            super(c);
            d = c.getResources().getDisplayMetrics().density;
            setGravity(Gravity.CENTER);
            setTextSize(13);
            setTypeface(Typeface.DEFAULT_BOLD);
            setTextColor(Pal.accent);
            setPadding(Math.round(12 * d), 0, Math.round(12 * d), 0);
            line.setColor(Pal.accent);
            line.setAlpha(Pal.dark ? 60 : 50);
            line.setStrokeWidth(1.5f * d);
            edge.setStyle(Paint.Style.STROKE);
            edge.setStrokeWidth(1.5f * d);
            edge.setColor(Pal.accent);
            edge.setAlpha(140);
            edge.setPathEffect(new android.graphics.DashPathEffect(new float[]{6 * d, 5 * d}, 0));
        }

        @Override
        protected void onDraw(Canvas c) {
            float r = 16 * d;
            float inset = edge.getStrokeWidth() / 2f;
            RectF rf = new RectF(inset, inset, getWidth() - inset, getHeight() - inset);
            clip.reset();
            clip.addRoundRect(rf, r, r, android.graphics.Path.Direction.CW);
            c.save();
            c.clipPath(clip);
            float step = 12 * d;
            for (float x = -getHeight(); x < getWidth(); x += step) {
                c.drawLine(x, getHeight(), x + getHeight(), 0, line); // slanted lines
            }
            c.restore();
            c.drawRoundRect(rf, r, r, edge);
            super.onDraw(c);
        }
    }

    private View agendaRow(final Task t, boolean overdue) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
        rlp.bottomMargin = dp(8);
        row.setLayoutParams(rlp);

        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t.due);
        String when = overdue
                ? new SimpleDateFormat("d MMM", Locale.getDefault()).format(new Date(t.due))
                : String.format(Locale.US, "%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));
        TextView time = text(when, 13, overdue ? Pal.overdue : Pal.muted, true);
        if (!t.done) {
            time.setOnClickListener(v -> {
                Hap.tick(v);
                final Task tt = t;
                pickTime("Move \u201C" + t.title + "\u201D", t.due, 0, 23, at -> {
                    assign(tt, dayAt(day, hourOf(at), minuteOf(at)));
                    render();
                });
            });
            time.setMinHeight(dp(44));
            time.setGravity(Gravity.CENTER_VERTICAL);
        }
        row.addView(time, new LinearLayout.LayoutParams(dp(52), -2));

        LinearLayout card = new LinearLayout(this);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(4), 0, dp(4), 0);
        GradientDrawable g = new GradientDrawable();
        g.setColor(Pal.card);
        g.setCornerRadius(dp(16));
        card.setBackground(g);
        card.setMinimumHeight(dp(60));

        if (t.priority > 0 && !t.done) {
            View bar = new View(this);
            GradientDrawable pg = new GradientDrawable();
            pg.setCornerRadius(dp(2));
            pg.setColor(MainActivity.priorityColor(t.priority));
            bar.setBackground(pg);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(4), dp(28));
            blp.leftMargin = dp(6);
            card.addView(bar, blp);
        }
        IconView check = new IconView(this, IconView.CHECK);
        check.setOn(t.done);
        check.setContentDescription(t.done ? "Mark not done" : "Mark done");
        check.setOnClickListener(v -> {
            Hap.confirm(v);
            db.setDone(t.id, !t.done);
            if (t.done) Reminders.schedule(this, db.get(t.id));
            else Reminders.cancel(this, t.id);
            render();
        });
        card.addView(check, new LinearLayout.LayoutParams(dp(44), dp(44)));

        TextView title = text(t.emoji.isEmpty() ? t.title : t.emoji + "  " + t.title, 16, t.done ? Pal.muted : Pal.ink, false);
        if (t.done) title.setPaintFlags(title.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        title.setMaxLines(2);
        title.setPadding(dp(2), dp(8), dp(4), dp(8));
        if (t.repeat > 0) {
            title.setCompoundDrawablesWithIntrinsicBounds(null, null,
                    new RepeatDrawable(t.done ? Pal.muted : Pal.accent, dp(15)), null);
            title.setCompoundDrawablePadding(dp(6));
        }
        card.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));

        if (!t.done) {
            TextView move = text(overdue ? "Today" : "+1 day", 13, Pal.accent, true);
            move.setGravity(Gravity.CENTER);
            move.setMinHeight(dp(44));
            move.setPadding(dp(10), 0, dp(10), 0);
            move.setContentDescription(overdue ? "Move to today" : "Move to the next day");
            move.setOnClickListener(v -> {
                Hap.tick(v);
                long target;
                if (overdue) { // same clock time, today (or the next hour if that has passed)
                    Calendar tc = Calendar.getInstance();
                    Calendar src = Calendar.getInstance();
                    src.setTimeInMillis(t.due);
                    tc.set(Calendar.HOUR_OF_DAY, src.get(Calendar.HOUR_OF_DAY));
                    tc.set(Calendar.MINUTE, src.get(Calendar.MINUTE));
                    tc.set(Calendar.SECOND, 0);
                    target = tc.getTimeInMillis();
                    if (target <= System.currentTimeMillis()) target = System.currentTimeMillis() + 3_600_000L;
                } else {
                    Calendar tc = Calendar.getInstance();
                    tc.setTimeInMillis(t.due);
                    tc.add(Calendar.DAY_OF_YEAR, 1);
                    target = tc.getTimeInMillis();
                }
                assign(t, target);
                render();
            });
            card.addView(move);
        }
        row.addView(card, new LinearLayout.LayoutParams(0, -2, 1f));
        return row;
    }

    private View looseCard(final Task t) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(12), dp(12), dp(12));
        GradientDrawable g = new GradientDrawable();
        g.setColor(Pal.card);
        g.setCornerRadius(dp(16));
        card.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(8);
        card.setLayoutParams(lp);

        TextView title = text(t.emoji.isEmpty() ? t.title : t.emoji + "  " + t.title, 16, Pal.ink, false);
        title.setMaxLines(2);
        card.addView(title);

        FlowLayout slots = new FlowLayout(this, dp(8), dp(8));
        slots.setPadding(0, dp(10), 0, 0);
        int[][] opts = {{9, 0}, {14, 0}, {18, 0}};
        String[] names = {"Morning", "Afternoon", "Evening"};
        boolean any = false;
        for (int i = 0; i < opts.length; i++) {
            final long at = dayAt(day, opts[i][0], opts[i][1]);
            if (at <= System.currentTimeMillis()) continue; // already past on today
            any = true;
            TextView ch = chip(names[i] + " " + String.format(Locale.US, "%02d:%02d", opts[i][0], opts[i][1]), false);
            ch.setOnClickListener(v -> {
                Hap.confirm(v);
                assign(t, at);
                render();
            });
            slots.addView(ch);
        }
        TextView pick = chip("Pick time\u2026", true);
        pick.setOnClickListener(v -> {
            Hap.tick(v);
            pickTime("Time for \u201C" + t.title + "\u201D", dayAt(day, 9, 0), 0, 23, at -> {
                assign(t, at);
                render();
            });
        });
        slots.addView(pick);
        if (!any) {
            TextView ch = chip("In 1 hour", false);
            ch.setOnClickListener(v -> {
                Hap.confirm(v);
                assign(t, System.currentTimeMillis() + 3_600_000L);
                render();
            });
            slots.addView(ch);
        }
        card.addView(slots);
        return card;
    }

    // ---------------------------------------------------------------- actions

    private void assign(Task t, long due) {
        db.update(t.id, t.title, due, t.repeat, t.repeatUnit, t.alarm);
        if (t.always) db.setAlarmMode(t.id, t.alarm, true);
        Reminders.schedule(this, db.get(t.id));
    }

    private void confirmAutoPlan(final List<Task> loose) {
        new Dlg.Builder(this)
                .setTitle("Plan my day?")
                .setMessage("Gives " + loose.size() + (loose.size() == 1 ? " task" : " tasks")
                        + " a time, most important first, in 30 minute steps from "
                        + (day == startOfDay(System.currentTimeMillis()) ? "the next free slot" : "09:00")
                        + ", skipping lunch. Each gets a reminder at its time.")
                .setPositiveButton("Plan", (d, w) -> autoPlan(loose))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void autoPlan(List<Task> loose) {
        long now = System.currentTimeMillis();
        Calendar cur = Calendar.getInstance();
        if (day == startOfDay(now)) {
            cur.setTimeInMillis(now + 15 * 60_000L);
            int m = cur.get(Calendar.MINUTE);
            cur.set(Calendar.MINUTE, m <= 30 ? 30 : 0);
            if (m > 30) cur.add(Calendar.HOUR_OF_DAY, 1);
            if (cur.get(Calendar.HOUR_OF_DAY) < 8) cur.set(Calendar.HOUR_OF_DAY, 9);
        } else {
            cur.setTimeInMillis(dayAt(day, 9, 0));
        }
        cur.set(Calendar.SECOND, 0);
        cur.set(Calendar.MILLISECOND, 0);
        // already sorted: db.list puts higher priority first
        List<Task> ordered = new ArrayList<>(loose);
        java.util.Collections.sort(ordered, (a, b) -> b.priority - a.priority);
        for (Task t : ordered) {
            int h = cur.get(Calendar.HOUR_OF_DAY);
            int min = cur.get(Calendar.MINUTE);
            if (h >= 21) { // day is full: carry on at 09:00 the next day
                cur.add(Calendar.DAY_OF_YEAR, 1);
                cur.set(Calendar.HOUR_OF_DAY, 9);
                cur.set(Calendar.MINUTE, 0);
            } else if (h == 12 && min >= 30 || h == 13 && min < 30) { // lunch
                cur.set(Calendar.HOUR_OF_DAY, 13);
                cur.set(Calendar.MINUTE, 30);
            }
            assign(t, cur.getTimeInMillis());
            cur.add(Calendar.MINUTE, 30);
        }
        Hap.confirm(content);
        Toast.makeText(this, "Planned " + ordered.size() + (ordered.size() == 1 ? " task" : " tasks"),
                Toast.LENGTH_SHORT).show();
        render();
    }

    // ---------------------------------------------------------------- helpers

    private static void sortByDue(List<Task> l) {
        java.util.Collections.sort(l, (a, b) -> Long.compare(a.due, b.due));
    }

    private static long startOfDay(long t) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private static long dayAt(long dayStart, int h, int m) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(dayStart);
        c.set(Calendar.HOUR_OF_DAY, h);
        c.set(Calendar.MINUTE, m);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private TextView sectionLabel(String s) {
        TextView t = text(s, 13, Pal.muted, true);
        t.setPadding(dp(8), dp(18), 0, dp(8));
        return t;
    }

    private TextView chip(String s, boolean on) {
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

    private int dp(int v) {
        return Math.round(v * density);
    }

    /** Progress ring with "done/total" in the middle. */
    private static final class RingView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final float d;
        private int done;
        private int total;

        RingView(Context c) {
            super(c);
            d = c.getResources().getDisplayMetrics().density;
        }

        void set(int done, int total) {
            this.done = done;
            this.total = total;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            float sw = 7 * d;
            rect.set(sw / 2, sw / 2, w - sw / 2, h - sw / 2);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(sw);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(Pal.chip);
            canvas.drawArc(rect, 0, 360, false, paint);
            if (total > 0 && done > 0) {
                paint.setColor(Pal.accent);
                canvas.drawArc(rect, -90, 360f * done / total, false, paint);
            }
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Pal.ink);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            paint.setTextSize(16 * d);
            String s = total == 0 ? "–" : done + "/" + total;
            canvas.drawText(s, w / 2, h / 2 - (paint.descent() + paint.ascent()) / 2, paint);
        }
    }
}
