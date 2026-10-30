package app.privatetodo;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One person: what is still out, how much came back, the return-by dates and the history. */
public class LentPersonActivity extends Activity {
    private int builtTheme;
    private LentDb db;
    private String person = "";
    private int dir;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Pal.init(this);
        builtTheme = Pal.themeRes();
        setTheme(Pal.themeRes());
        super.onCreate(savedInstanceState);
        db = new LentDb(this);
        person = getIntent().getStringExtra("person");
        if (person == null) person = "";
        dir = getIntent().getIntExtra("dir", 0);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String p = intent.getStringExtra("person");
        if (p != null) person = p;
        dir = intent.getIntExtra("dir", dir);
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

    /** One line of the history. */
    private static final class Event {
        long day;
        long order;
        String text;
        String amount = "";
        int color;
        long payId = -1;
    }

    private void render() {
        List<LentDb.Loan> loans = db.of(person, dir);
        if (loans.isEmpty()) {
            finish();
            return;
        }
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Pal.bg);
        scroll.setFillViewport(true);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(18), dp(16), dp(18), dp(32));
        scroll.addView(col, new ScrollView.LayoutParams(-1, -2));

        // ---- header
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        IconView back = new IconView(this, IconView.BACK);
        back.setContentDescription("Back");
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView title = LentUi.text(this, person, 26, Pal.ink, true);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        IconView more = new IconView(this, IconView.MORE);
        more.setContentDescription("More");
        more.setOnClickListener(v -> menu());
        header.addView(more, new LinearLayout.LayoutParams(dp(48), dp(48)));
        col.addView(header);

        List<LentDb.Loan> open = new ArrayList<>();
        long owed = 0;
        long total = 0;
        long back2 = 0;
        int things = 0;
        for (LentDb.Loan l : loans) {
            if (l.closed) continue;
            open.add(l);
            if (l.money()) {
                owed += l.left();
                total += l.amount;
                back2 += Math.min(l.paid, l.amount);
            } else {
                things++;
            }
        }

        col.addView(hero(open, owed, total, back2, things));
        for (LentDb.Loan l : open) col.addView(loanCard(l, open.size() > 1));

        // ---- history
        col.addView(history(loans));

        if (!open.isEmpty()) {
            TextView all = LentUi.pill(this, "Mark everything returned", Pal.chip, Pal.ink, 15);
            all.setPadding(dp(18), dp(14), dp(18), dp(14));
            all.setOnClickListener(v -> {
                Hap.confirm(v);
                new Dlg.Builder(this).setTitle("Mark everything returned?")
                        .setMessage(dir == 0 ? person + " has given everything back." : "You gave everything back to " + person + ".")
                        .setPositiveButton("Yes, all returned", (d, w) -> {
                            for (LentDb.Loan l : db.of(person, dir)) {
                                db.markReturned(l.id, LentDb.today());
                                LentRemind.cancel(this, l.id);
                            }
                            render();
                        }).setNegativeButton("Cancel", null).show();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = dp(8);
            col.addView(all, lp);
        }

        setContentView(scroll);
        getWindow().setStatusBarColor(Pal.bg);
        getWindow().setNavigationBarColor(Pal.bg);
        Fonts.apply(scroll);
    }

    // ------------------------------------------------------------------ hero

    private View hero(List<LentDb.Loan> open, long owed, long total, long back, int things) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(22), dp(20), dp(22), dp(20));
        box.setBackground(LentUi.round(this, Pal.accent, 26));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(10);
        lp.bottomMargin = dp(12);
        box.setLayoutParams(lp);
        int soft = (Pal.onAccent & 0x00FFFFFF) | 0xB3000000;

        if (open.isEmpty()) {
            box.addView(LentUi.text(this, "All returned ✓", 26, Pal.onAccent, true));
            box.addView(LentUi.text(this, dir == 0 ? person + " has given everything back" : "You returned everything to " + person,
                    14, soft, false));
            return box;
        }
        String line;
        if (total > 0) line = dir == 0 ? person + " still owes you" : "You still owe " + person;
        else line = dir == 0 ? person + " still has" : "You still have";
        box.addView(LentUi.text(this, line, 14, soft, true));
        if (total > 0) {
            box.addView(LentUi.text(this, LentUi.money(this, owed), 38, Pal.onAccent, true));
            if (things > 0) {
                box.addView(LentUi.text(this, "and " + things + (things == 1 ? " thing" : " things"), 14, soft, false));
            }
            if (back > 0) {
                LinearLayout pr = new LinearLayout(this);
                pr.setPadding(0, dp(12), 0, dp(6));
                pr.addView(LentUi.text(this, LentUi.money(this, back) + " of " + LentUi.money(this, total) + " back",
                        13, soft, false), new LinearLayout.LayoutParams(0, -2, 1f));
                pr.addView(LentUi.text(this, Math.round(100f * back / total) + "%", 13, Pal.onAccent, true));
                box.addView(pr);
                LentUi.Bar bar = new LentUi.Bar(this, back / (float) total, Pal.onAccent);
                box.addView(bar, new LinearLayout.LayoutParams(-1, dp(6)));
            }
        } else {
            box.addView(LentUi.text(this, things + (things == 1 ? " thing" : " things"), 34, Pal.onAccent, true));
        }
        return box;
    }

    // ------------------------------------------------------------------ one open loan

    private View loanCard(final LentDb.Loan l, boolean many) {
        LinearLayout card = LentUi.card(this);
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        if (!l.money()) {
            TextView em = LentUi.text(this, l.emoji.isEmpty() ? "📦" : l.emoji, 26, Pal.ink, false);
            em.setPadding(0, 0, dp(12), 0);
            top.addView(em);
        }
        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        String head = l.money() ? LentUi.money(this, l.amount) : (l.title.isEmpty() ? "A thing" : l.title);
        mid.addView(LentUi.text(this, head, 20, Pal.ink, true));
        String sub = (dir == 0 ? "Lent " : "Borrowed ") + LentUi.date(l.day) + (l.note.isEmpty() ? "" : "  ·  " + l.note);
        TextView st = LentUi.text(this, sub, 13, Pal.muted, false);
        mid.addView(st);
        top.addView(mid, new LinearLayout.LayoutParams(0, -2, 1f));
        if (l.money() && l.paid > 0) {
            top.addView(LentUi.text(this, LentUi.money(this, l.left()) + " left", 14, Pal.muted, true));
        }
        card.addView(top);

        if (l.money() && l.paid > 0) {
            card.addView(new LentUi.Bar(this, l.paid / (float) l.amount, Pal.accent), barParams());
        }

        // return-by row
        LinearLayout due = new LinearLayout(this);
        due.setGravity(Gravity.CENTER_VERTICAL);
        due.setPadding(0, dp(14), 0, 0);
        due.addView(LentUi.text(this, "Return by", 14, Pal.muted, true), new LinearLayout.LayoutParams(0, -2, 1f));
        boolean late = LentUi.overdue(l);
        String dueText = l.due > 0 ? LentUi.date(l.due) : "No date";
        TextView when = LentUi.pill(this, dueText + (l.due > 0 ? "  ·  " + LentUi.dueLabel(l.due) : ""),
                late ? (Pal.dark ? 0x40F2B8B5 : 0x1FB3261E) : Pal.chip, late ? Pal.overdue : Pal.ink, 13);
        when.setOnClickListener(v -> {
            Hap.tick(v);
            pickDue(l);
        });
        due.addView(when);
        card.addView(due);

        if (l.due > 0) {
            LinearLayout rem = new LinearLayout(this);
            rem.setGravity(Gravity.CENTER_VERTICAL);
            rem.setPadding(0, dp(10), 0, 0);
            LinearLayout rt = new LinearLayout(this);
            rt.setOrientation(LinearLayout.VERTICAL);
            rt.addView(LentUi.text(this, "Remind me to ask", 15, Pal.ink, true));
            rt.addView(LentUi.text(this, l.remind ? LentUi.before(l.remindDays) + ", " + LentUi.clock(this, l.remindMin)
                    : "Off", 12, Pal.muted, false));
            rem.addView(rt, new LinearLayout.LayoutParams(0, -2, 1f));
            ToggleView tg = new ToggleView(this);
            tg.set(l.remind, false);
            tg.setListener(on -> {
                l.remind = on;
                db.save(l);
                LentRemind.schedule(this, l);
                render();
            });
            rem.addView(tg, new LinearLayout.LayoutParams(dp(52), dp(32)));
            card.addView(rem);
        }

        // actions
        LinearLayout acts = new LinearLayout(this);
        acts.setPadding(0, dp(16), 0, 0);
        if (l.money()) {
            TextView got = LentUi.pill(this, dir == 0 ? "+ Got money back" : "+ I paid some back", Pal.accent, Pal.onAccent, 14);
            got.setOnClickListener(v -> {
                Hap.tick(v);
                payDialog(l);
            });
            acts.addView(got, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        TextView ret = LentUi.pill(this, "Mark returned", Pal.chip, Pal.ink, 14);
        ret.setOnClickListener(v -> {
            Hap.confirm(v);
            db.markReturned(l.id, LentDb.today());
            LentRemind.cancel(this, l.id);
            render();
        });
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(l.money() ? -2 : 0, -2, l.money() ? 0f : 1f);
        rlp.leftMargin = l.money() ? dp(8) : 0;
        acts.addView(ret, rlp);
        TextView edit = LentUi.pill(this, "Edit", Pal.chip, Pal.ink, 14);
        edit.setOnClickListener(v -> {
            Hap.tick(v);
            startActivity(new Intent(this, LentEditActivity.class).putExtra("id", l.id));
        });
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(-2, -2);
        elp.leftMargin = dp(8);
        acts.addView(edit, elp);
        card.addView(acts);
        return card;
    }

    private LinearLayout.LayoutParams barParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(6));
        lp.topMargin = dp(12);
        return lp;
    }

    private void pickDue(final LentDb.Loan l) {
        LocalDate d = l.due > 0 ? LocalDate.ofEpochDay(l.due) : LocalDate.now().plusDays(7);
        DatePickerDialog dlg = new DatePickerDialog(this, (view, y, m, day) -> {
            l.due = LocalDate.of(y, m + 1, day).toEpochDay();
            db.save(l);
            LentRemind.schedule(this, l);
            render();
        }, d.getYear(), d.getMonthValue() - 1, d.getDayOfMonth());
        dlg.setButton(DatePickerDialog.BUTTON_NEUTRAL, "No date", (di, w) -> {
            l.due = 0;
            l.remind = false;
            db.save(l);
            LentRemind.cancel(this, l.id);
            render();
        });
        dlg.show();
    }

    /** Asks how much came back (or was paid back) and records it. */
    private void payDialog(final LentDb.Loan l) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(8), dp(24), 0);
        final EditText amount = new EditText(this);
        amount.setHint(LentUi.money(this, l.left()));
        amount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        amount.setSingleLine(true);
        amount.setTextSize(24);
        amount.setTextColor(Pal.ink);
        amount.setHintTextColor(Pal.muted);
        amount.setBackground(LentUi.round(this, Pal.chip, 16));
        amount.setPadding(dp(16), dp(12), dp(16), dp(12));
        box.addView(amount);
        TextView rest = LentUi.pill(this, "All of it (" + LentUi.money(this, l.left()) + ")", Pal.chip, Pal.ink, 13);
        rest.setOnClickListener(v -> amount.setText(trimCents(l.left())));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-2, -2);
        rlp.topMargin = dp(10);
        box.addView(rest, rlp);
        new Dlg.Builder(this).setTitle(dir == 0 ? "Got money back" : "Paid back").setView(box)
                .setPositiveButton("Save", (d, w) -> {
                    long c = LentUi.parse(amount.getText().toString());
                    if (c <= 0) {
                        Toast.makeText(this, "Type an amount", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    db.addPay(l.id, LentDb.today(), c, "");
                    LentDb.Loan now = db.get(l.id);
                    if (now != null && now.closed) LentRemind.cancel(this, l.id);
                    render();
                }).setNegativeButton("Cancel", null).show();
    }

    private static String trimCents(long cents) {
        return cents % 100 == 0 ? String.valueOf(cents / 100) : String.format(java.util.Locale.ROOT, "%.2f", cents / 100.0);
    }

    // ------------------------------------------------------------------ history

    private View history(List<LentDb.Loan> loans) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        TextView h = LentUi.text(this, "HISTORY", 12, Pal.muted, true);
        h.setLetterSpacing(0.1f);
        h.setPadding(dp(4), dp(12), 0, dp(8));
        wrap.addView(h);

        List<Event> ev = new ArrayList<>();
        long n = 0;
        for (LentDb.Loan l : loans) {
            Event e = new Event();
            e.day = l.day;
            e.order = n++;
            e.text = (dir == 0 ? "Lent" : "Borrowed") + (l.money() ? "" : " " + (l.title.isEmpty() ? "a thing" : l.title));
            if (l.money()) {
                e.amount = "+" + LentUi.money(this, l.amount);
                e.color = LentUi.AMBER;
            } else {
                e.color = LentUi.AMBER;
            }
            ev.add(e);
            for (LentDb.Pay p : db.pays(l.id)) {
                Event x = new Event();
                x.day = p.day;
                x.order = n++;
                x.text = dir == 0 ? "Got money back" : "Paid back";
                if (!p.note.isEmpty()) x.text += "  ·  " + p.note;
                x.amount = "−" + LentUi.money(this, p.amount);
                x.color = LentUi.GREEN;
                x.payId = p.id;
                ev.add(x);
            }
            if (l.closed && !l.money()) {
                Event x = new Event();
                x.day = l.closedOn;
                x.order = n++;
                x.text = "Returned " + (l.title.isEmpty() ? "the thing" : l.title);
                x.color = LentUi.GREEN;
                ev.add(x);
            }
        }
        Collections.sort(ev, (a, b) -> a.day != b.day ? Long.compare(b.day, a.day) : Long.compare(b.order, a.order));

        LinearLayout card = LentUi.card(this);
        for (int i = 0; i < ev.size(); i++) {
            final Event e = ev.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(8), 0, dp(8));
            View dot = new View(this);
            dot.setBackground(LentUi.round(this, e.color, 100));
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(10), dp(10));
            dlp.rightMargin = dp(12);
            row.addView(dot, dlp);
            LinearLayout mid = new LinearLayout(this);
            mid.setOrientation(LinearLayout.VERTICAL);
            mid.addView(LentUi.text(this, e.text, 15, Pal.ink, true));
            String d = LentUi.date(e.day);
            mid.addView(LentUi.text(this, d.substring(0, 1).toUpperCase() + d.substring(1), 12, Pal.muted, false));
            row.addView(mid, new LinearLayout.LayoutParams(0, -2, 1f));
            if (!e.amount.isEmpty()) row.addView(LentUi.text(this, e.amount, 15, e.color, true));
            if (e.payId >= 0) {
                row.setOnLongClickListener(v -> {
                    Hap.confirm(v);
                    new Dlg.Builder(this).setTitle("Remove this payment?")
                            .setMessage(e.text + "  " + e.amount)
                            .setPositiveButton("Remove", (dd, w) -> {
                                db.delPay(e.payId);
                                render();
                            }).setNegativeButton("Keep", null).show();
                    return true;
                });
            }
            card.addView(row);
        }
        if (ev.size() > 0) {
            TextView tip = LentUi.text(this, "Press and hold a payment to remove it.", 12, Pal.muted, false);
            tip.setPadding(0, dp(6), 0, 0);
            boolean anyPay = false;
            for (Event e : ev) if (e.payId >= 0) anyPay = true;
            if (anyPay) card.addView(tip);
        }
        wrap.addView(card);
        return wrap;
    }

    // ------------------------------------------------------------------ more

    private void menu() {
        new Dlg.Builder(this).setItems(new String[]{"Rename " + person, "Add another", "Delete all of " + person + "'s entries"},
                (d, i) -> {
                    if (i == 0) rename();
                    else if (i == 1) startActivity(new Intent(this, LentEditActivity.class)
                            .putExtra("dir", dir).putExtra("person", person));
                    else confirmDelete();
                }).show();
    }

    private void rename() {
        final EditText name = new EditText(this);
        name.setText(person);
        name.setSingleLine(true);
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        name.setTextColor(Pal.ink);
        name.setBackground(LentUi.round(this, Pal.chip, 16));
        name.setPadding(dp(16), dp(12), dp(16), dp(12));
        LinearLayout box = new LinearLayout(this);
        box.setPadding(dp(24), dp(8), dp(24), 0);
        box.addView(name, new LinearLayout.LayoutParams(-1, -2));
        new Dlg.Builder(this).setTitle("Rename").setView(box).setPositiveButton("Save", (d, w) -> {
            String to = name.getText().toString().trim();
            if (to.isEmpty()) return;
            db.rename(person, dir, to);
            person = to;
            render();
        }).setNegativeButton("Cancel", null).show();
    }

    private void confirmDelete() {
        new Dlg.Builder(this).setTitle("Delete everything for " + person + "?")
                .setMessage("This removes every entry and payment for " + person + " from this phone.")
                .setPositiveButton("Delete", (d, w) -> {
                    for (LentDb.Loan l : db.of(person, dir)) {
                        LentRemind.cancel(this, l.id);
                        db.delete(l.id);
                    }
                    finish();
                }).setNegativeButton("Keep", null).show();
    }
}
