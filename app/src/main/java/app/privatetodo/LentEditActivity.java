package app.privatetodo;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.time.LocalDate;
import java.util.List;

/** Adds or edits one lent / borrowed item: money or a thing, who with, the dates and the reminder. */
public class LentEditActivity extends Activity {
    private static final String[] EMOJI = {"📚", "🔧", "💻", "🎮", "👕",
            "🚲", "📦"};
    private static final long[] QUICK = {100, 500, 1000, 5000};

    private int builtTheme;
    private LentDb db;
    private LentDb.Loan editing;

    private int dir;
    private int kind;
    private String emoji = "";
    private long day = LentDb.today();
    private long due;
    private boolean remind;
    private int remindDays = 1;
    private int remindMin = 540;

    private EditText who;
    private EditText amount;
    private EditText what;
    private EditText note;
    private LinearLayout col;
    private ScrollView scroll;
    private boolean ready;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Pal.init(this);
        builtTheme = Pal.themeRes();
        setTheme(Pal.themeRes());
        super.onCreate(savedInstanceState);
        db = new LentDb(this);
        long id = getIntent().getLongExtra("id", 0);
        dir = getIntent().getIntExtra("dir", 0);
        String preset = getIntent().getStringExtra("person");

        who = field("Name", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        amount = field("0", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        amount.setTextSize(30);
        amount.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        what = field("What is it? e.g. Drill machine", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        note = field("Optional", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);

        if (id > 0) {
            editing = db.get(id);
        }
        if (editing != null) {
            LentDb.Loan l = editing;
            dir = l.dir;
            kind = l.kind;
            emoji = l.emoji;
            day = l.day;
            due = l.due;
            remind = l.remind;
            remindDays = l.remindDays;
            remindMin = l.remindMin;
            who.setText(l.person);
            if (l.money()) amount.setText(l.amount % 100 == 0 ? String.valueOf(l.amount / 100)
                    : String.format(java.util.Locale.ROOT, "%.2f", l.amount / 100.0));
            what.setText(l.title);
            note.setText(l.note);
        } else if (preset != null) {
            who.setText(preset);
        }
        ready = true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        Lock.guard(this, Lock.LENT);
        Pal.init(this);
        if (Pal.themeRes() != builtTheme) {
            recreate();
            return;
        }
        if (scroll == null) {
            scroll = new ScrollView(this);
            scroll.setBackgroundColor(Pal.bg);
            scroll.setFillViewport(true);
            col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setPadding(dp(18), dp(16), dp(18), dp(40));
            scroll.addView(col, new ScrollView.LayoutParams(-1, -2));
            setContentView(scroll);
            getWindow().setStatusBarColor(Pal.bg);
            getWindow().setNavigationBarColor(Pal.bg);
            build();
        }
    }

    @Override
    protected void onDestroy() {
        db.close();
        super.onDestroy();
    }

    private int dp(float v) {
        return LentUi.dp(this, v);
    }

    private EditText field(String hint, int type) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setInputType(type);
        e.setSingleLine(true);
        e.setTextSize(17);
        e.setTextColor(Pal.ink);
        e.setHintTextColor(Pal.muted);
        e.setBackground(LentUi.round(this, Pal.card, 18));
        e.setPadding(dp(16), dp(14), dp(16), dp(14));
        return e;
    }

    private static void detach(View v) {
        if (v.getParent() instanceof ViewGroup) ((ViewGroup) v.getParent()).removeView(v);
    }

    private void add(View v) {
        detach(v);
        col.addView(v);
    }

    private TextView label(String s) {
        TextView t = LentUi.text(this, s, 12, Pal.muted, true);
        t.setLetterSpacing(0.1f);
        t.setPadding(dp(4), dp(18), 0, dp(8));
        return t;
    }

    /** A row of chips that scrolls sideways when it must. */
    private HorizontalScrollView chips(View... views) {
        LinearLayout row = new LinearLayout(this);
        for (View v : views) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = dp(8);
            row.addView(v, lp);
        }
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(row);
        return hs;
    }

    private TextView choice(String s, boolean on, final Runnable r) {
        TextView c = LentUi.chip(this, s, on);
        c.setOnClickListener(v -> {
            Hap.tick(v);
            r.run();
        });
        return c;
    }

    /** Rebuilds the form; the text fields are kept, so nothing typed is lost. */
    private void build() {
        if (!ready || col == null) return;
        col.removeAllViews();

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        IconView back = new IconView(this, IconView.BACK);
        back.setContentDescription("Back");
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        String title = editing != null ? "Edit" : dir == 0 ? "Lent something" : "Borrowed something";
        header.addView(LentUi.text(this, title, 26, Pal.ink, true), new LinearLayout.LayoutParams(0, -2, 1f));
        col.addView(header);

        // who the money / thing went to
        col.addView(chips(
                choice("I lent", dir == 0, () -> {
                    dir = 0;
                    build();
                }),
                choice("I borrowed", dir == 1, () -> {
                    dir = 1;
                    build();
                })));
        View sp = new View(this);
        col.addView(sp, new LinearLayout.LayoutParams(-1, dp(10)));
        col.addView(chips(
                choice("Money", kind == 0, () -> {
                    kind = 0;
                    build();
                }),
                choice("A thing", kind == 1, () -> {
                    kind = 1;
                    build();
                })));

        col.addView(label(dir == 0 ? "TO WHOM" : "FROM WHOM"));
        add(who);
        List<String> known = db.people(dir);
        if (!known.isEmpty()) {
            View[] cs = new View[known.size()];
            for (int i = 0; i < cs.length; i++) {
                final String n = known.get(i);
                TextView c = LentUi.chip(this, n, false);
                c.setOnClickListener(v -> {
                    Hap.tick(v);
                    who.setText(n);
                    who.setSelection(n.length());
                });
                cs[i] = c;
            }
            HorizontalScrollView hs = chips(cs);
            LentUi.margins(hs, 0, 10, 0, 0);
            col.addView(hs);
        }

        if (kind == 0) {
            col.addView(label("AMOUNT"));
            add(amount);
            View[] cs = new View[QUICK.length + 1];
            for (int i = 0; i < QUICK.length; i++) {
                final long q = QUICK[i];
                TextView c = LentUi.chip(this, "+" + java.text.NumberFormat.getInstance().format(q), false);
                c.setOnClickListener(v -> {
                    Hap.tick(v);
                    long cur = Math.max(0, LentUi.parse(amount.getText().toString()));
                    long next = cur + q * 100;
                    amount.setText(next % 100 == 0 ? String.valueOf(next / 100) : String.format(java.util.Locale.ROOT, "%.2f", next / 100.0));
                    amount.setSelection(amount.getText().length());
                });
                cs[i] = c;
            }
            TextView clear = LentUi.chip(this, "Clear", false);
            clear.setOnClickListener(v -> {
                Hap.tick(v);
                amount.setText("");
            });
            cs[QUICK.length] = clear;
            HorizontalScrollView hs = chips(cs);
            LentUi.margins(hs, 0, 10, 0, 0);
            col.addView(hs);
        } else {
            col.addView(label("WHAT"));
            add(what);
            View[] cs = new View[EMOJI.length];
            for (int i = 0; i < EMOJI.length; i++) {
                final String em = EMOJI[i];
                TextView c = LentUi.chip(this, em, em.equals(emoji));
                c.setTextSize(20);
                c.setOnClickListener(v -> {
                    Hap.tick(v);
                    emoji = em.equals(emoji) ? "" : em;
                    build();
                });
                cs[i] = c;
            }
            HorizontalScrollView hs = chips(cs);
            LentUi.margins(hs, 0, 10, 0, 0);
            col.addView(hs);
        }

        col.addView(label("NOTE"));
        add(note);

        // dates
        col.addView(label(dir == 0 ? "LENT ON" : "BORROWED ON"));
        long t = LentDb.today();
        col.addView(chips(
                choice("Today", day == t, () -> {
                    day = LentDb.today();
                    build();
                }),
                choice("Yesterday", day == t - 1, () -> {
                    day = LentDb.today() - 1;
                    build();
                }),
                choice(day == t || day == t - 1 ? "Pick date…" : LentUi.date(day), day != t && day != t - 1,
                        () -> pick(day, d -> {
                            day = d;
                            build();
                        }))));

        col.addView(label("RETURN BY"));
        boolean w1 = due == t + 7;
        boolean w2 = due == t + 14;
        boolean m1 = due == LocalDate.now().plusMonths(1).toEpochDay();
        boolean custom = due > 0 && !w1 && !w2 && !m1;
        col.addView(chips(
                choice("No date", due == 0, () -> {
                    due = 0;
                    remind = false;
                    build();
                }),
                choice("1 week", w1, () -> {
                    due = LentDb.today() + 7;
                    build();
                }),
                choice("2 weeks", w2, () -> {
                    due = LentDb.today() + 14;
                    build();
                }),
                choice("1 month", m1, () -> {
                    due = LocalDate.now().plusMonths(1).toEpochDay();
                    build();
                }),
                choice(custom ? LentUi.date(due) : "Pick…", custom,
                        () -> pick(due > 0 ? due : LentDb.today() + 7, d -> {
                            due = d;
                            build();
                        }))));

        if (due > 0) {
            LinearLayout card = LentUi.card(this);
            LentUi.margins(card, 0, 16, 0, 0);
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout mid = new LinearLayout(this);
            mid.setOrientation(LinearLayout.VERTICAL);
            mid.addView(LentUi.text(this, dir == 0 ? "Remind me to ask" : "Remind me to return it", 16, Pal.ink, true));
            mid.addView(LentUi.text(this, remind ? LentUi.before(remindDays) + ", " + LentUi.clock(this, remindMin)
                    : "Off", 13, Pal.muted, false));
            row.addView(mid, new LinearLayout.LayoutParams(0, -2, 1f));
            ToggleView tg = new ToggleView(this);
            tg.set(remind, false);
            tg.setListener(on -> {
                remind = on;
                build();
            });
            row.addView(tg, new LinearLayout.LayoutParams(dp(52), dp(32)));
            card.addView(row);
            if (remind) {
                HorizontalScrollView hs = chips(
                        choice("Same day", remindDays == 0, () -> {
                            remindDays = 0;
                            build();
                        }),
                        choice("1 day before", remindDays == 1, () -> {
                            remindDays = 1;
                            build();
                        }),
                        choice("3 days before", remindDays == 3, () -> {
                            remindDays = 3;
                            build();
                        }),
                        choice(LentUi.clock(this, remindMin), false, this::pickTime));
                LentUi.margins(hs, 0, 14, 0, 0);
                card.addView(hs);
            }
            col.addView(card);
        }

        TextView save = LentUi.pill(this, editing != null ? "Save changes" : "Save", Pal.accent, Pal.onAccent, 17);
        save.setPadding(dp(20), dp(16), dp(20), dp(16));
        save.setOnClickListener(v -> {
            Hap.confirm(v);
            save();
        });
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, -2);
        slp.topMargin = dp(24);
        col.addView(save, slp);

        if (editing != null) {
            TextView del = LentUi.pill(this, "Delete this entry", Pal.chip, Pal.overdue, 15);
            del.setPadding(dp(20), dp(14), dp(20), dp(14));
            del.setOnClickListener(v -> new Dlg.Builder(this).setTitle("Delete this entry?")
                    .setMessage("Its payments go with it.")
                    .setPositiveButton("Delete", (d, w) -> {
                        LentRemind.cancel(this, editing.id);
                        db.delete(editing.id);
                        finish();
                    }).setNegativeButton("Keep", null).show());
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-1, -2);
            dlp.topMargin = dp(10);
            col.addView(del, dlp);
        }
        Fonts.apply(col);
    }

    private interface DayListener {
        void got(long day);
    }

    private void pick(long start, final DayListener l) {
        LocalDate d = LocalDate.ofEpochDay(start);
        new DatePickerDialog(this, (view, y, m, dd) -> l.got(LocalDate.of(y, m + 1, dd).toEpochDay()),
                d.getYear(), d.getMonthValue() - 1, d.getDayOfMonth()).show();
    }

    private void pickTime() {
        new TimePickerDialog(this, (view, h, m) -> {
            remindMin = h * 60 + m;
            build();
        }, remindMin / 60, remindMin % 60, android.text.format.DateFormat.is24HourFormat(this)).show();
    }

    private void save() {
        String name = who.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, dir == 0 ? "Who did you lend it to?" : "Who did you borrow it from?", Toast.LENGTH_SHORT).show();
            return;
        }
        LentDb.Loan l = editing != null ? editing : new LentDb.Loan();
        l.person = name;
        l.dir = dir;
        l.kind = kind;
        if (kind == 0) {
            long cents = LentUi.parse(amount.getText().toString());
            if (cents <= 0) {
                Toast.makeText(this, "Type the amount", Toast.LENGTH_SHORT).show();
                return;
            }
            l.amount = cents;
            l.title = "";
            l.emoji = "";
        } else {
            String t = what.getText().toString().trim();
            if (t.isEmpty()) {
                Toast.makeText(this, "What did you lend?", Toast.LENGTH_SHORT).show();
                return;
            }
            l.title = t;
            l.emoji = emoji;
            l.amount = 0;
        }
        l.note = note.getText().toString().trim();
        l.day = day;
        l.due = due;
        l.remind = remind && due > 0;
        l.remindDays = remindDays;
        l.remindMin = remindMin;
        if (editing != null && kind == 1) { // a thing changed back to open keeps its returned state as it was
            l.closed = editing.closed;
        }
        db.save(l);
        LentDb.Loan saved = db.get(l.id);
        LentRemind.schedule(this, saved);
        finish();
    }
}
