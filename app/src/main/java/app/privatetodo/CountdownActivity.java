package app.privatetodo;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.time.LocalDate;
import java.util.List;

/** Countdowns: events with the days left. Tap one to edit it. */
public class CountdownActivity extends Activity {
    private int builtTheme;
    private CountdownDb db;

    @Override
    protected void onCreate(Bundle b) {
        Pal.init(this);
        builtTheme = Pal.themeRes();
        setTheme(Pal.themeRes());
        super.onCreate(b);
        db = new CountdownDb(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Lock.guard(this, Lock.ALL);
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

    private int dp(float v) {
        return LentUi.dp(this, v);
    }

    private void render() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Pal.bg);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(18), dp(16), dp(18), dp(110));
        scroll.addView(col, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        IconView back = new IconView(this, IconView.BACK);
        back.setContentDescription("Back");
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        header.addView(LentUi.text(this, "Countdowns", 30, Pal.ink, true), new LinearLayout.LayoutParams(0, -2, 1f));
        col.addView(header);

        List<CountdownDb.Item> all = db.all();
        CountdownDb.Item next = null;
        for (CountdownDb.Item i : all) {
            if (i.daysLeft() >= 0) {
                next = i;
                break;
            }
        }
        if (next != null) col.addView(hero(next));

        if (all.isEmpty()) {
            TextView hint = LentUi.text(this, "No countdowns yet.\nAdd an exam, a trip or a launch date.", 16, Pal.muted, false);
            hint.setPadding(dp(4), dp(18), 0, 0);
            col.addView(hint);
        }
        boolean pastHead = false;
        for (CountdownDb.Item i : all) {
            if (i.daysLeft() < 0 && !pastHead) {
                pastHead = true;
                TextView h = LentUi.text(this, "PAST", 12, Pal.muted, true);
                h.setLetterSpacing(0.08f);
                h.setPadding(dp(4), dp(16), 0, dp(8));
                col.addView(h);
            }
            col.addView(card(i));
        }

        TextView fab = LentUi.pill(this, "+  New countdown", Pal.accent, Pal.onAccent, 16);
        fab.setPadding(dp(22), dp(14), dp(22), dp(14));
        fab.setOnClickListener(v -> {
            Hap.tick(v);
            CountdownDb.Item n = new CountdownDb.Item();
            n.day = CountdownDb.today() + 7;
            edit(n);
        });
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END);
        flp.setMargins(0, 0, dp(20), dp(24));
        root.addView(fab, flp);

        setContentView(root);
        getWindow().setStatusBarColor(Pal.bg);
        getWindow().setNavigationBarColor(Pal.bg);
        Fonts.apply(root);
    }

    private static String left(long n) {
        if (n == 0) return "Today";
        if (n == 1) return "Tomorrow";
        if (n > 1) return n + " days";
        return n == -1 ? "Yesterday" : (-n) + " days ago";
    }

    private View hero(CountdownDb.Item i) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(22), dp(20), dp(22), dp(20));
        box.setBackground(LentUi.round(this, Pal.accent, 26));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(10);
        lp.bottomMargin = dp(14);
        box.setLayoutParams(lp);
        int soft = (Pal.onAccent & 0x00FFFFFF) | 0xB3000000;
        box.addView(LentUi.text(this, "NEXT UP", 12, soft, true));
        long n = i.daysLeft();
        box.addView(LentUi.text(this, n == 0 ? "Today" : n == 1 ? "Tomorrow" : String.valueOf(n), 46, Pal.onAccent, true));
        if (n > 1) box.addView(LentUi.text(this, "days to go", 14, soft, true));
        TextView t = LentUi.text(this, (i.emoji.isEmpty() ? "" : i.emoji + "  ") + i.title, 19, Pal.onAccent, true);
        t.setPadding(0, dp(6), 0, 0);
        box.addView(t);
        box.setOnClickListener(v -> edit(i));
        return box;
    }

    private View card(final CountdownDb.Item i) {
        LinearLayout c = LentUi.card(this);
        c.setOrientation(LinearLayout.HORIZONTAL);
        c.setGravity(Gravity.CENTER_VERTICAL);
        long n = i.daysLeft();
        boolean past = n < 0;

        LinearLayout num = new LinearLayout(this);
        num.setOrientation(LinearLayout.VERTICAL);
        num.setGravity(Gravity.CENTER);
        num.setMinimumWidth(dp(64));
        TextView big = LentUi.text(this, n > 1 ? String.valueOf(n) : n < -1 ? String.valueOf(-n) : n == 0 ? "★" : "1",
                28, past ? Pal.muted : Pal.ink, true);
        big.setGravity(Gravity.CENTER);
        num.addView(big);
        TextView unit = LentUi.text(this, n == 0 ? "today" : Math.abs(n) == 1 ? (past ? "day ago" : "day") : (past ? "days ago" : "days"),
                12, Pal.muted, true);
        unit.setGravity(Gravity.CENTER);
        num.addView(unit);
        c.addView(num, new LinearLayout.LayoutParams(dp(70), -2));

        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        mid.setPadding(dp(8), 0, 0, 0);
        TextView title = LentUi.text(this, (i.emoji.isEmpty() ? "" : i.emoji + "  ") + i.title, 18, past ? Pal.muted : Pal.ink, true);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        mid.addView(title);
        String when = LentUi.date(i.day) + (i.minutes >= 0 ? "  ·  " + LentUi.clock(this, i.minutes) : "");
        TextView sub = LentUi.text(this, when, 14, Pal.muted, false);
        mid.addView(sub);
        if (!past) {
            long span = Math.max(1, i.day - java.time.Instant.ofEpochMilli(i.created).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay());
            float frac = 1f - Math.min(1f, n / (float) span);
            LentUi.Bar bar = new LentUi.Bar(this, frac, Pal.accent);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, dp(5));
            blp.topMargin = dp(8);
            mid.addView(bar, blp);
        }
        c.addView(mid, new LinearLayout.LayoutParams(0, -2, 1f));
        c.setOnClickListener(v -> {
            Hap.tick(v);
            edit(i);
        });
        return c;
    }

    // ------------------------------------------------------------------ editor

    private EditText field(String hint, int type, String value) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setInputType(type);
        e.setSingleLine(true);
        e.setTextSize(17);
        e.setTextColor(Pal.ink);
        e.setHintTextColor(Pal.muted);
        e.setText(value);
        e.setBackground(LentUi.round(this, Pal.card, 16));
        e.setPadding(dp(14), dp(12), dp(14), dp(12));
        return e;
    }

    private static void paint(TextView t, boolean on) {
        t.setTextColor(on ? Pal.onSel : Pal.muted);
        t.setBackground(LentUi.round(t.getContext(), on ? Pal.sel : Pal.chip, 100));
    }

    private void edit(final CountdownDb.Item it) {
        final boolean isNew = it.id == 0;
        final long[] day = {it.day};
        final int[] minutes = {it.minutes};
        final int[] mask = {it.mask};
        final int[] rmin = {it.remindMin};

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(4), dp(20), dp(4));
        LinearLayout top = new LinearLayout(this);
        final EditText emoji = field("🎯", InputType.TYPE_CLASS_TEXT, it.emoji);
        emoji.setGravity(Gravity.CENTER);
        emoji.setTextSize(22);
        top.addView(emoji, new LinearLayout.LayoutParams(dp(64), -2));
        final EditText title = field("What is it? e.g. Final exam", InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, it.title);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, -2, 1f);
        tl.leftMargin = dp(8);
        top.addView(title, tl);
        box.addView(top);

        final TextView dateBtn = LentUi.pill(this, "", Pal.chip, Pal.ink, 15);
        final TextView timeBtn = LentUi.pill(this, "", Pal.chip, Pal.ink, 15);
        final Runnable labels = () -> {
            dateBtn.setText("📅  " + LentUi.date(day[0]));
            timeBtn.setText(minutes[0] < 0 ? "All day" : "⏰  " + LentUi.clock(this, minutes[0]));
        };
        labels.run();
        dateBtn.setOnClickListener(v -> {
            LocalDate d = LocalDate.ofEpochDay(day[0]);
            new DatePickerDialog(this, (view, y, m, dd) -> {
                day[0] = LocalDate.of(y, m + 1, dd).toEpochDay();
                labels.run();
            }, d.getYear(), d.getMonthValue() - 1, d.getDayOfMonth()).show();
        });
        timeBtn.setOnClickListener(v -> {
            int base = minutes[0] < 0 ? 540 : minutes[0];
            new TimePickerDialog(this, (view, h, m) -> {
                minutes[0] = h * 60 + m;
                labels.run();
            }, base / 60, base % 60, android.text.format.DateFormat.is24HourFormat(this)).show();
        });
        timeBtn.setOnLongClickListener(v -> {
            minutes[0] = -1;
            labels.run();
            return true;
        });
        LinearLayout when = new LinearLayout(this);
        when.setPadding(0, dp(12), 0, 0);
        when.addView(dateBtn, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, -2, 1f);
        tp.leftMargin = dp(8);
        when.addView(timeBtn, tp);
        box.addView(when);
        box.addView(LentUi.text(this, "Long-press the time for all day", 12, Pal.muted, false));

        TextView rh = LentUi.text(this, "REMIND ME", 12, Pal.muted, true);
        rh.setLetterSpacing(0.08f);
        rh.setPadding(dp(2), dp(14), 0, dp(6));
        box.addView(rh);
        box.addView(chipsRow(mask));

        final TextView rt = LentUi.pill(this, "", Pal.chip, Pal.ink, 15);
        final Runnable rl = () -> rt.setText("🔔  at " + LentUi.clock(this, rmin[0]));
        rl.run();
        rt.setOnClickListener(v -> new TimePickerDialog(this, (view, h, m) -> {
            rmin[0] = h * 60 + m;
            rl.run();
        }, rmin[0] / 60, rmin[0] % 60, android.text.format.DateFormat.is24HourFormat(this)).show());
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-2, -2);
        rlp.topMargin = dp(8);
        box.addView(rt, rlp);

        final EditText note = field("Note (optional)", InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, it.note);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(-1, -2);
        nlp.topMargin = dp(12);
        box.addView(note, nlp);

        ScrollView sv = new ScrollView(this);
        sv.addView(box);
        Fonts.apply(box);
        Dlg.Builder b = new Dlg.Builder(this);
        b.setTitle(isNew ? "New countdown" : "Edit countdown").setView(sv);
        b.setPositiveButton("Save", (d, w) -> {
            String name = title.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(this, "Give it a name", Toast.LENGTH_SHORT).show();
                return;
            }
            it.title = name;
            it.emoji = emoji.getText().toString().trim();
            it.day = day[0];
            it.minutes = minutes[0];
            it.mask = mask[0];
            it.remindMin = rmin[0];
            it.note = note.getText().toString().trim();
            if (isNew) it.created = System.currentTimeMillis();
            db.save(it);
            CountdownRemind.schedule(this, it);
            Hap.confirm(sv);
            render();
        }).setNegativeButton("Cancel", null);
        if (!isNew) {
            b.setNeutralButton("Delete", (d, w) -> {
                CountdownRemind.cancel(this, it.id);
                db.delete(it.id);
                render();
            });
        }
        AlertDialog dlg = b.show();
    }

    private View chipsRow(final int[] mask) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        for (int k = 0; k < CountdownDb.BITS.length; k++) {
            final int bit = CountdownDb.BITS[k];
            final TextView c = LentUi.chip(this, CountdownDb.BIT_NAMES[k], (mask[0] & bit) != 0);
            c.setOnClickListener(v -> {
                mask[0] ^= bit;
                paint(c, (mask[0] & bit) != 0);
                Hap.tick(v);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = dp(8);
            row.addView(c, lp);
        }
        hs.addView(row);
        return hs;
    }
}
