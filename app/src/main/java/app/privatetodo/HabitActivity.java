package app.privatetodo;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Habit list: a week strip, and one card per habit with its streak, today's progress and the week at a glance. */
public class HabitActivity extends Activity {
    private float density;
    private int builtTheme;
    private HabitDb db;
    private long selected = HabitDb.today();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Pal.init(this);
        builtTheme = Pal.themeRes();
        setTheme(Pal.themeRes());
        super.onCreate(savedInstanceState);
        density = getResources().getDisplayMetrics().density;
        db = new HabitDb(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Pal.init(this);
        if (Pal.themeRes() != builtTheme) {
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

    private void render() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Pal.bg);
        scroll.setFillViewport(true);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(18), dp(16), dp(18), dp(32));
        scroll.addView(col, new ScrollView.LayoutParams(-1, -2));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        IconView back = new IconView(this, IconView.BACK);
        back.setContentDescription("Back");
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        header.addView(text("Habit", 30, Pal.ink, true), new LinearLayout.LayoutParams(0, -2, 1f));
        IconView add = new IconView(this, IconView.PLUS);
        add.setContentDescription("New habit");
        add.setOnClickListener(v -> HabitEditor.show(this, null, this::render));
        header.addView(add, new LinearLayout.LayoutParams(dp(48), dp(48)));
        col.addView(header);

        col.addView(dateStrip());

        List<Habit> habits = db.list();
        long today = HabitDb.today();
        int doneToday = 0;
        for (Habit h : habits) if (h.doneAt(db.amount(h.id, selected))) doneToday++;
        String when = selected == today ? "Today" : LocalDate.ofEpochDay(selected).getDayOfWeek()
                .getDisplayName(TextStyle.FULL, Locale.getDefault());
        TextView sum = text(habits.isEmpty() ? "No habits yet" : when + "  ·  " + doneToday + " of "
                + habits.size() + " done", 14, Pal.muted, true);
        sum.setPadding(dp(4), dp(18), 0, dp(10));
        col.addView(sum);

        if (habits.isEmpty()) {
            TextView hint = text("Tap + to add your first habit.", 16, Pal.muted, false);
            hint.setPadding(dp(4), dp(8), 0, 0);
            col.addView(hint);
        }
        for (Habit h : habits) col.addView(card(h));

        setContentView(scroll);
        getWindow().setStatusBarColor(Pal.bg);
        getWindow().setNavigationBarColor(Pal.bg);
        Fonts.apply(scroll);
    }

    // ------------------------------------------------------------------ strip

    private View dateStrip() {
        LinearLayout strip = new LinearLayout(this);
        strip.setPadding(0, dp(10), 0, 0);
        long today = HabitDb.today();
        for (int i = 6; i >= 0; i--) {
            final long day = today - i;
            LocalDate ld = LocalDate.ofEpochDay(day);
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            cell.addView(text(ld.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                    12, Pal.muted, false));
            boolean on = day == selected;
            TextView num = text(String.valueOf(ld.getDayOfMonth()), 16, on ? Pal.onAccent : Pal.ink, true);
            num.setGravity(Gravity.CENTER);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(on ? Pal.accent : 0);
            num.setBackground(g);
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(dp(40), dp(40));
            nlp.topMargin = dp(6);
            cell.addView(num, nlp);
            cell.setOnClickListener(v -> {
                Hap.tick(v);
                selected = day;
                render();
            });
            strip.addView(cell, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        return strip;
    }

    // ------------------------------------------------------------------ card

    private View card(final Habit h) {
        final Map<Long, Integer> m = db.all(h.id);
        HabitDb.Stats st = HabitDb.stats(h, m);
        int amt = m.containsKey(selected) ? m.get(selected) : 0;

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(16), dp(16), dp(14));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Pal.card);
        bg.setCornerRadius(dp(28));
        card.setBackground(bg);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, -2);
        clp.bottomMargin = dp(12);
        card.setLayoutParams(clp);
        card.setOnClickListener(v -> startActivity(new Intent(this, HabitDetailActivity.class)
                .putExtra("id", h.id)));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        TextView streak = text(st.streak > 0 ? st.streak + (st.streak == 1 ? " Day" : " Days") : "Start today",
                14, HabitUi.deep(h.color), true);
        left.addView(streak);
        TextView name = text(h.name, 22, Pal.ink, true);
        left.addView(name);
        left.addView(text(h.progress(amt), 14, Pal.muted, false));
        top.addView(left, new LinearLayout.LayoutParams(0, -2, 1f));

        HabitUi.Badge badge = new HabitUi.Badge(this, h.color, h.emoji);
        badge.setLevel(h.goal > 0 ? amt / (float) h.goal : h.doneAt(amt) ? 1f : 0f);
        badge.setContentDescription(h.goal > 0 ? "Add " + h.step + " " + h.unit : "Tick off");
        badge.setOnClickListener(v -> logTap(h, v));
        badge.setOnLongClickListener(v -> {
            Hap.confirm(v);
            db.setAmount(h.id, selected, 0);
            render();
            return true;
        });
        top.addView(badge, new LinearLayout.LayoutParams(dp(68), dp(68)));
        card.addView(top);

        // week at a glance, Monday first
        LinearLayout week = new LinearLayout(this);
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(-1, -2);
        wlp.topMargin = dp(14);
        week.setLayoutParams(wlp);
        long today = HabitDb.today();
        long monday = today - (LocalDate.ofEpochDay(today).getDayOfWeek().getValue() - 1);
        long created = Instant.ofEpochMilli(h.created).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay();
        String[] letters = {"M", "T", "W", "T", "F", "S", "S"};
        for (int i = 0; i < 7; i++) {
            final long day = monday + i;
            int a = m.containsKey(day) ? m.get(day) : 0;
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            TextView l = text(letters[i], 13, day == selected ? HabitUi.deep(h.color) : Pal.muted, day == selected);
            cell.addView(l);
            HabitUi.DayDot dot = new HabitUi.DayDot(this, h.color);
            float frac = h.goal > 0 ? Math.max(0, a) / (float) h.goal : 0f;
            int state;
            if (day > today) state = HabitUi.FUTURE;
            else if (a < 0) state = HabitUi.SKIP;
            else if (h.doneAt(a)) state = HabitUi.DONE;
            else if (day == today) state = HabitUi.TODAY;
            else if (a > 0) state = HabitUi.PARTIAL;
            else state = day < created ? HabitUi.FUTURE : HabitUi.MISSED;
            dot.set(state, frac);
            dot.setOnClickListener(v -> {
                if (day > HabitDb.today()) return;
                Hap.tick(v);
                int cur = db.amount(h.id, day);
                db.setAmount(h.id, day, h.doneAt(cur) ? 0 : (h.goal > 0 ? h.goal : 1));
                render();
            });
            dot.setOnLongClickListener(v -> {
                if (day > HabitDb.today()) return true;
                Hap.confirm(v);
                db.setAmount(h.id, day, db.amount(h.id, day) < 0 ? 0 : -1);
                render();
                return true;
            });
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(40), dp(44));
            dlp.topMargin = dp(2);
            cell.addView(dot, dlp);
            week.addView(cell, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        card.addView(week);
        return card;
    }

    /** One tap on the badge adds a step to the selected day (or ticks a simple habit). */
    private void logTap(Habit h, View v) {
        Hap.confirm(v);
        int cur = db.amount(h.id, selected);
        if (h.goal <= 0) {
            db.setAmount(h.id, selected, cur > 0 ? 0 : 1);
        } else {
            int now = db.add(h.id, selected, h.step);
            Toast.makeText(this, "+" + h.step + (h.unit.isEmpty() ? "" : " " + h.unit) + "  ·  " + now
                    + "/" + h.goal, Toast.LENGTH_SHORT).show();
        }
        render();
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
}
