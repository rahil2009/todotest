package app.privatetodo;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Lent: who owes you, what you lent out, and what you borrowed. Everything stays on the phone. */
public class LentActivity extends Activity {
    private static final int F_LENT = 0;
    private static final int F_BORROWED = 1;
    private static final int F_RETURNED = 2;
    private static final int F_ALL = 3;

    private int builtTheme;
    private int filter = F_LENT;
    private LentDb db;

    /** Everything with one person in one direction. */
    private static final class Group {
        String name;
        int dir;
        final List<LentDb.Loan> loans = new ArrayList<>();
        long owed;      // money still to come back
        long total;     // the money of the open loans
        long back;      // already paid back on those
        int things;     // open things
        long nextDue;   // earliest return-by date of an open loan, 0 = none
        boolean open;   // anything still out
        String emoji = "";
        LentDb.Loan lead; // the newest open loan (the newest loan when all are returned)
        long lastReturned;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Pal.init(this);
        builtTheme = Pal.themeRes();
        setTheme(Pal.themeRes());
        super.onCreate(savedInstanceState);
        db = new LentDb(this);
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

    private List<Group> groups(List<LentDb.Loan> all) {
        Map<String, Group> map = new LinkedHashMap<>();
        for (LentDb.Loan l : all) {
            String key = l.dir + "|" + l.person.toLowerCase(Locale.ROOT);
            Group g = map.get(key);
            if (g == null) {
                g = new Group();
                g.name = l.person;
                g.dir = l.dir;
                map.put(key, g);
            }
            g.loans.add(l);
            if (l.closed) {
                g.lastReturned = Math.max(g.lastReturned, l.closedOn);
                continue;
            }
            g.open = true;
            if (g.lead == null || g.lead.closed) g.lead = l;
            if (l.money()) {
                g.owed += l.left();
                g.total += l.amount;
                g.back += Math.min(l.paid, l.amount);
            } else {
                g.things++;
                if (g.emoji.isEmpty()) g.emoji = l.emoji;
            }
            if (l.due > 0 && (g.nextDue == 0 || l.due < g.nextDue)) g.nextDue = l.due;
        }
        for (Group g : map.values()) if (g.lead == null) g.lead = g.loans.get(0);
        return new ArrayList<>(map.values());
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

        List<LentDb.Loan> all = db.all();
        List<Group> groups = groups(all);

        // ---- header
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        IconView back = new IconView(this, IconView.BACK);
        back.setContentDescription("Back");
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        header.addView(LentUi.text(this, "Lent", 30, Pal.ink, true), new LinearLayout.LayoutParams(0, -2, 1f));
        String sym = LentUi.symbol(this);
        TextView cur = LentUi.pill(this, sym.isEmpty() ? "123" : sym, Pal.chip, Pal.ink, 15);
        cur.setMinWidth(dp(44));
        cur.setContentDescription("Currency sign");
        cur.setOnClickListener(v -> {
            Hap.tick(v);
            chooseCurrency();
        });
        header.addView(cur, new LinearLayout.LayoutParams(-2, dp(38)));
        col.addView(header);

        // ---- summary
        col.addView(summary(all));

        // ---- filters
        String[] names = {"Lent out", "Borrowed", "Returned", "All"};
        LinearLayout chips = new LinearLayout(this);
        chips.setPadding(0, dp(14), 0, dp(10));
        for (int i = 0; i < names.length; i++) {
            final int f = i;
            TextView c = LentUi.chip(this, names[i], filter == i);
            c.setOnClickListener(v -> {
                Hap.tick(v);
                filter = f;
                render();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = dp(8);
            chips.addView(c, lp);
        }
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(chips);
        col.addView(hs);

        // ---- people
        int shown = 0;
        for (Group g : groups) {
            boolean show;
            if (filter == F_LENT) show = g.dir == 0 && g.open;
            else if (filter == F_BORROWED) show = g.dir == 1 && g.open;
            else if (filter == F_RETURNED) show = !g.open;
            else show = true;
            if (!show) continue;
            col.addView(personCard(g));
            shown++;
        }
        if (shown == 0) {
            String msg = filter == F_RETURNED ? "Nothing has been returned yet."
                    : filter == F_BORROWED ? "You have not borrowed anything." : "Nothing lent out right now.";
            TextView hint = LentUi.text(this, msg + "\nTap the button below to add one.", 16, Pal.muted, false);
            hint.setPadding(dp(4), dp(12), 0, 0);
            col.addView(hint);
        }

        // ---- the add button
        final int addDir = filter == F_BORROWED ? 1 : 0;
        TextView fab = LentUi.pill(this, addDir == 1 ? "+  Borrowed something" : "+  Lent something",
                Pal.accent, Pal.onAccent, 16);
        fab.setPadding(dp(22), dp(14), dp(22), dp(14));
        fab.setOnClickListener(v -> {
            Hap.tick(v);
            startActivity(new Intent(this, LentEditActivity.class).putExtra("dir", addDir));
        });
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END);
        flp.setMargins(0, 0, dp(20), dp(24));
        root.addView(fab, flp);

        setContentView(root);
        getWindow().setStatusBarColor(Pal.bg);
        getWindow().setNavigationBarColor(Pal.bg);
        Fonts.apply(root);
    }

    // ------------------------------------------------------------------ summary

    private View summary(List<LentDb.Loan> all) {
        long owed = 0;
        long owe = 0;
        int things = 0;
        int late = 0;
        java.util.HashSet<String> people = new java.util.HashSet<>();
        for (LentDb.Loan l : all) {
            if (l.closed) continue;
            if (l.dir == 0) {
                people.add(l.person.toLowerCase(Locale.ROOT));
                if (l.money()) owed += l.left();
                else things++;
                if (LentUi.overdue(l)) late++;
            } else if (l.money()) {
                owe += l.left();
            }
        }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(22), dp(20), dp(22), dp(20));
        box.setBackground(LentUi.round(this, Pal.accent, 26));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(10);
        box.setLayoutParams(lp);
        int soft = (Pal.onAccent & 0x00FFFFFF) | 0xB3000000;
        box.addView(LentUi.text(this, owed == 0 && things == 0 ? "Nobody owes you anything" : "You are owed", 14, soft, true));
        if (owed > 0 || things == 0) {
            TextView big = LentUi.text(this, LentUi.money(this, owed), 36, Pal.onAccent, true);
            box.addView(big);
        }
        if (owe > 0) {
            TextView o = LentUi.text(this, "You owe " + LentUi.money(this, owe), 15, Pal.onAccent, true);
            o.setPadding(0, dp(2), 0, 0);
            box.addView(o);
        }
        LinearLayout row = new LinearLayout(this);
        row.setPadding(0, dp(12), 0, 0);
        row.addView(summaryChip(people.size() + (people.size() == 1 ? " person" : " people"), false));
        if (things > 0) row.addView(summaryChip(things + (things == 1 ? " thing out" : " things out"), false));
        if (late > 0) row.addView(summaryChip(late + " overdue", true));
        box.addView(row);
        return box;
    }

    private View summaryChip(String s, boolean alert) {
        TextView t = LentUi.text(this, s, 13, alert ? 0xFFFFFFFF : Pal.onAccent, true);
        t.setPadding(dp(12), dp(5), dp(12), dp(5));
        t.setBackground(LentUi.round(this, alert ? 0xFFB3261E : ((Pal.onAccent & 0x00FFFFFF) | 0x26000000), 100));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = dp(8);
        t.setLayoutParams(lp);
        return t;
    }

    // ------------------------------------------------------------------ one person

    private View personCard(final Group g) {
        LinearLayout card = LentUi.card(this);
        card.setOnClickListener(v -> {
            Hap.tick(v);
            startActivity(new Intent(this, LentPersonActivity.class)
                    .putExtra("person", g.name).putExtra("dir", g.dir));
        });

        boolean onlyThings = g.open && g.total == 0 && g.things > 0;
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        LentUi.Avatar av = new LentUi.Avatar(this, g.name, onlyThings && g.things == 1 ? g.emoji : "");
        top.addView(av, new LinearLayout.LayoutParams(dp(46), dp(46)));
        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        mid.setPadding(dp(14), 0, dp(8), 0);
        TextView nm = LentUi.text(this, g.name, 18, Pal.ink, true);
        nm.setSingleLine(true);
        nm.setEllipsize(android.text.TextUtils.TruncateAt.END);
        mid.addView(nm);
        mid.addView(LentUi.text(this, subtitle(g), 13, Pal.muted, false));
        top.addView(mid, new LinearLayout.LayoutParams(0, -2, 1f));

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.END);
        if (!g.open) {
            long sum = 0;
            for (LentDb.Loan l : g.loans) if (l.money()) sum += l.amount;
            right.addView(LentUi.text(this, sum > 0 ? LentUi.money(this, sum) : "✓", 20, Pal.muted, true));
            right.addView(LentUi.text(this, "returned", 12, Pal.muted, false));
        } else if (g.total > 0) {
            right.addView(LentUi.text(this, LentUi.money(this, g.owed), 20, g.dir == 0 ? Pal.ink : LentUi.AMBER, true));
            right.addView(LentUi.text(this, g.dir == 0 ? "still owed" : "you owe", 12, Pal.muted, false));
        } else {
            right.addView(LentUi.text(this, g.things + (g.things == 1 ? " thing" : " things"), 17, Pal.ink, true));
            right.addView(LentUi.text(this, g.dir == 0 ? "out" : "borrowed", 12, Pal.muted, false));
        }
        top.addView(right, new LinearLayout.LayoutParams(-2, -2));
        card.addView(top);

        if (g.open && g.total > 0 && g.back > 0) {
            LinearLayout pr = new LinearLayout(this);
            pr.setPadding(0, dp(14), 0, dp(6));
            pr.addView(LentUi.text(this, LentUi.money(this, g.back) + " of " + LentUi.money(this, g.total)
                    + " back", 13, Pal.muted, false), new LinearLayout.LayoutParams(0, -2, 1f));
            pr.addView(LentUi.text(this, Math.round(100f * g.back / g.total) + "%", 13, Pal.ink, true));
            card.addView(pr);
            card.addView(new LentUi.Bar(this, g.back / (float) g.total, Pal.accent), new LinearLayout.LayoutParams(-1, dp(6)));
        }

        if (g.open && g.nextDue > 0) {
            boolean late = g.nextDue < LentDb.today();
            LinearLayout foot = new LinearLayout(this);
            foot.setGravity(Gravity.CENTER_VERTICAL);
            foot.setPadding(0, dp(12), 0, 0);
            TextView due = LentUi.pill(this, LentUi.dueLabel(g.nextDue),
                    late ? (Pal.dark ? 0x40F2B8B5 : 0x1FB3261E) : Pal.chip, late ? Pal.overdue : Pal.muted, 13);
            foot.addView(due, new LinearLayout.LayoutParams(-2, -2));
            foot.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
            if (late && g.dir == 0) {
                TextView remind = LentUi.pill(this, "Remind", Pal.sel, Pal.onSel, 13);
                remind.setOnClickListener(v -> {
                    Hap.tick(v);
                    shareReminder(g);
                });
                foot.addView(remind, new LinearLayout.LayoutParams(-2, -2));
            }
            card.addView(foot);
        }
        return card;
    }

    private String subtitle(Group g) {
        if (!g.open) return "All returned" + (g.lastReturned > 0 ? " · " + LentUi.date(g.lastReturned) : "");
        int n = 0;
        for (LentDb.Loan l : g.loans) if (!l.closed) n++;
        String verb = g.dir == 0 ? "lent " : "borrowed ";
        if (n > 1) return n + " items · since " + LentUi.date(oldestOpen(g));
        LentDb.Loan l = g.lead;
        String what = l.money() ? "Money" : (l.title.isEmpty() ? "A thing" : l.title);
        return what + " · " + verb + LentUi.date(l.day);
    }

    private long oldestOpen(Group g) {
        long d = Long.MAX_VALUE;
        for (LentDb.Loan l : g.loans) if (!l.closed) d = Math.min(d, l.day);
        return d == Long.MAX_VALUE ? LentDb.today() : d;
    }

    /** Opens the share sheet with a polite message; nothing is sent from the app itself. */
    private void shareReminder(Group g) {
        String msg;
        if (g.total > 0) {
            msg = "Hi " + g.name + ", a gentle reminder about the " + LentUi.money(this, g.owed) + " I lent you. "
                    + "Could you send it back when you get a chance? Thanks!";
        } else {
            String what = g.lead != null && !g.lead.title.isEmpty() ? g.lead.title : "the thing I lent you";
            msg = "Hi " + g.name + ", a gentle reminder about " + what + ". Could you return it when you get a chance? Thanks!";
        }
        Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, msg);
        startActivity(Intent.createChooser(send, "Remind " + g.name));
    }

    private void chooseCurrency() {
        final String[] labels = new String[LentUi.SYMBOLS.length];
        for (int i = 0; i < labels.length; i++) labels[i] = LentUi.SYMBOLS[i].isEmpty() ? "No sign" : LentUi.SYMBOLS[i];
        new Dlg.Builder(this).setTitle("Currency sign").setItems(labels, (d, i) -> {
            LentUi.setSymbol(this, LentUi.SYMBOLS[i]);
            render();
        }).show();
    }
}
