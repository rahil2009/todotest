package app.privatetodo;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

/** One habit: streak, best, total, today's log and a month-by-month history. */
public class HabitDetailActivity extends Activity {
    private float density;
    private int builtTheme;
    private HabitDb db;
    private long id;
    private HabitUi.Cal cal;
    private int calShift; // months away from the current one

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Pal.init(this);
        builtTheme = Pal.themeRes();
        setTheme(Pal.themeRes());
        super.onCreate(savedInstanceState);
        density = getResources().getDisplayMetrics().density;
        db = new HabitDb(this);
        id = getIntent().getLongExtra("id", -1);
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
        final Habit h = db.get(id);
        if (h == null) {
            finish();
            return;
        }
        Map<Long, Integer> m = db.all(id);
        HabitDb.Stats st = HabitDb.stats(h, m);
        long today = HabitDb.today();
        int tint = HabitUi.blend(h.color, Pal.bg, Pal.dark ? 0.30f : 0.55f);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Pal.card);
        scroll.setFillViewport(true);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(page, new ScrollView.LayoutParams(-1, -2));

        // ---- coloured header
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        if (HabitUi.isWater(h)) { // the header fills with water up to today's goal
            int today0 = m.containsKey(today) ? m.get(today) : 0;
            head.setBackground(new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{
                    new android.graphics.drawable.ColorDrawable(tint),
                    new WaterDrawable(density, 0, h.color, h.id + 1000000L, today0 / (float) h.goal)}));
        } else {
            head.setBackgroundColor(tint);
        }
        head.setPadding(dp(18), dp(14), dp(18), dp(36));
        page.addView(head, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        IconView back = new IconView(this, IconView.BACK);
        back.setContentDescription("Back");
        back.setOnClickListener(v -> finish());
        bar.addView(circle(back), new LinearLayout.LayoutParams(dp(44), dp(44)));
        bar.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        IconView trash = new IconView(this, IconView.TRASH);
        trash.setContentDescription("Delete habit");
        trash.setOnClickListener(v -> new Dlg.Builder(this)
                .setTitle("Delete “" + h.name + "”?")
                .setMessage("Its history is deleted too.")
                .setPositiveButton("Delete", (d, w) -> {
                    db.delete(id);
                    finish();
                })
                .setNegativeButton("Keep", null)
                .show());
        bar.addView(circle(trash), new LinearLayout.LayoutParams(dp(44), dp(44)));
        TextView edit = text("✎", 20, Pal.ink, true);
        edit.setGravity(Gravity.CENTER);
        edit.setContentDescription("Edit habit");
        edit.setOnClickListener(v -> HabitEditor.show(this, h, this::render));
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(dp(44), dp(44));
        elp.leftMargin = dp(10);
        bar.addView(circle(edit), elp);
        head.addView(bar);

        TextView title = text(h.name + (h.emoji.isEmpty() ? "" : " " + h.emoji), 34, Pal.ink, true);
        title.setPadding(0, dp(16), 0, 0);
        head.addView(title);
        TextView last = text("Last Recorded: " + lastText(st.last, today), 14, Pal.ink, false);
        last.setPadding(0, dp(6), 0, dp(14));
        head.addView(last);

        LinearLayout stats = new LinearLayout(this);
        LinearLayout streakCard = whiteCard();
        streakCard.setGravity(Gravity.CENTER);
        LinearLayout sr = new LinearLayout(this);
        sr.setGravity(Gravity.CENTER);
        sr.addView(new HabitUi.Flame(this), new LinearLayout.LayoutParams(dp(24), dp(30)));
        TextView sn = text(String.valueOf(st.streak), 36, Pal.ink, true);
        sn.setPadding(dp(8), 0, 0, 0);
        sr.addView(sn);
        streakCard.addView(sr);
        TextView streakWord = text("Streak", 16, Pal.ink, false);
        streakWord.setGravity(Gravity.CENTER);
        streakCard.addView(streakWord, new LinearLayout.LayoutParams(-1, -2));
        stats.addView(streakCard, new LinearLayout.LayoutParams(0, dp(124), 1.15f));
        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.addView(statRow("Best", st.best), new LinearLayout.LayoutParams(-1, dp(58)));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, dp(58));
        tlp.topMargin = dp(8);
        right.addView(statRow("Total", st.total), tlp);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(0, dp(124), 1f);
        rlp.leftMargin = dp(10);
        stats.addView(right, rlp);
        head.addView(stats);

        // today's log
        head.addView(todayCard(h, m.containsKey(today) ? m.get(today) : 0));

        // ---- history, as a white sheet that overlaps the header
        LinearLayout hist = new LinearLayout(this);
        hist.setOrientation(LinearLayout.VERTICAL);
        hist.setPadding(dp(20), dp(22), dp(20), dp(32));
        GradientDrawable hb = new GradientDrawable();
        hb.setColor(Pal.card);
        hb.setCornerRadii(new float[]{dp(32), dp(32), dp(32), dp(32), 0, 0, 0, 0});
        hist.setBackground(hb);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(-1, -2);
        hlp.topMargin = -dp(24);
        page.addView(hist, hlp);

        hist.addView(text("History", 20, Pal.ink, true));
        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        nav.setPadding(0, dp(10), 0, dp(4));
        cal = new HabitUi.Cal(this, h);
        cal.setData(m);
        if (calShift != 0) cal.shift(calShift);
        final TextView month = text(cal.title(), 16, Pal.ink, false);
        month.setGravity(Gravity.CENTER);
        TextView prev = text("‹", 30, Pal.ink, false);
        prev.setGravity(Gravity.CENTER);
        prev.setOnClickListener(v -> {
            calShift--;
            cal.shift(-1);
            month.setText(cal.title());
        });
        TextView next = text("›", 30, Pal.ink, false);
        next.setGravity(Gravity.CENTER);
        next.setOnClickListener(v -> {
            calShift++;
            cal.shift(1);
            month.setText(cal.title());
        });
        nav.addView(prev, new LinearLayout.LayoutParams(dp(48), dp(44)));
        nav.addView(month, new LinearLayout.LayoutParams(0, dp(44), 1f));
        nav.addView(next, new LinearLayout.LayoutParams(dp(48), dp(44)));
        hist.addView(nav);
        cal.setListener(day -> {
            int cur = db.amount(id, day);
            db.setAmount(id, day, h.doneAt(cur) ? 0 : (h.goal > 0 ? h.goal : 1));
            Hap.tick(cal);
            render();
        });
        hist.addView(cal, new LinearLayout.LayoutParams(-1, -2));
        TextView tip = text("Tap a day to mark it done or undo it.", 12, Pal.muted, false);
        tip.setPadding(dp(4), dp(8), 0, 0);
        hist.addView(tip);

        setContentView(scroll);
        getWindow().setStatusBarColor(tint);
        getWindow().setNavigationBarColor(Pal.card);
        Fonts.apply(scroll);
    }

    private View todayCard(final Habit h, int amount) {
        LinearLayout card = whiteCard();
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(18), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = h.goal > 0 ? 0 : dp(10);
        card.setLayoutParams(lp);
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.addView(text("Today", 13, Pal.muted, false));
        left.addView(text(h.progress(amount), 20, Pal.ink, true));
        card.addView(left, new LinearLayout.LayoutParams(0, -2, 1f));
        final boolean done = h.doneAt(amount);
        String label = h.goal > 0 ? "+" + h.step + (h.unit.isEmpty() ? "" : " " + h.unit)
                : done ? "Undo" : "Mark done";
        TextView btn = text(label, 15, HabitUi.onDeep(h.color), true);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(dp(18), 0, dp(18), 0);
        btn.setMinHeight(dp(44));
        GradientDrawable g = new GradientDrawable();
        g.setColor(HabitUi.deep(h.color));
        g.setCornerRadius(dp(22));
        btn.setBackground(g);
        btn.setOnClickListener(v -> {
            Hap.confirm(v);
            if (h.goal > 0) db.add(id, HabitDb.today(), h.step);
            else db.setAmount(id, HabitDb.today(), done ? 0 : 1);
            render();
        });
        card.addView(btn);
        if (h.goal <= 0) return card;

        // the habit's other quick amounts, a custom amount, and undo
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(0, 0, 0, 0);
        wrap.addView(card, new LinearLayout.LayoutParams(-1, -2));
        FlowLayout chips = new FlowLayout(this, dp(8), dp(8));
        chips.setPadding(dp(4), dp(10), 0, 0);
        boolean first = true;
        for (final int q : h.quickAmounts()) {
            if (first) { // the main step is already the big button
                first = false;
                continue;
            }
            chips.addView(amountChip("+" + q, v -> {
                db.add(id, HabitDb.today(), q);
                render();
            }), new android.view.ViewGroup.LayoutParams(-2, dp(38)));
        }
        chips.addView(amountChip("Custom\u2026", v -> HabitEditor.askAmount(this, h, val -> {
            db.add(id, HabitDb.today(), val);
            render();
        })), new android.view.ViewGroup.LayoutParams(-2, dp(38)));
        chips.addView(amountChip("\u2212" + h.step, v -> {
            db.add(id, HabitDb.today(), -h.step);
            render();
        }), new android.view.ViewGroup.LayoutParams(-2, dp(38)));
        wrap.addView(chips);
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(-1, -2);
        wlp.topMargin = dp(10);
        wrap.setLayoutParams(wlp);
        return wrap;
    }

    private TextView amountChip(String label, View.OnClickListener l) {
        TextView t = text(label, 14, Pal.ink, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(16), 0, dp(16), 0);
        GradientDrawable g = new GradientDrawable();
        g.setColor(HabitUi.alpha(Pal.card, 0xE0));
        g.setCornerRadius(dp(19));
        t.setBackground(g);
        t.setOnClickListener(v -> {
            Hap.tick(v);
            l.onClick(v);
        });
        return t;
    }

    private LinearLayout statRow(String label, int value) {
        LinearLayout row = whiteCard();
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(18), 0, dp(18), 0);
        row.addView(text(label, 17, Pal.ink, false), new LinearLayout.LayoutParams(0, -2, 1f));
        row.addView(text(String.valueOf(value), 18, Pal.ink, false));
        return row;
    }

    private LinearLayout whiteCard() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setColor(Pal.card);
        g.setCornerRadius(dp(22));
        c.setBackground(g);
        return c;
    }

    private View circle(View inner) {
        FrameLayoutHolder f = new FrameLayoutHolder();
        return f.wrap(inner);
    }

    /** Round translucent button background around an icon. */
    private final class FrameLayoutHolder {
        View wrap(View inner) {
            android.widget.FrameLayout f = new android.widget.FrameLayout(HabitDetailActivity.this);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(HabitUi.alpha(Pal.card, 0xB0));
            f.setBackground(g);
            View click = inner;
            f.addView(inner, new android.widget.FrameLayout.LayoutParams(-1, -1));
            f.setOnClickListener(v -> click.performClick());
            return f;
        }
    }

    private String lastText(long last, long today) {
        if (last < 0) return "Never";
        if (last == today) return "Today";
        if (last == today - 1) return "Yesterday";
        return LocalDate.ofEpochDay(last).format(DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.getDefault()));
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
