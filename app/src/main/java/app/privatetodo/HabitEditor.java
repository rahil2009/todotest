package app.privatetodo;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;

/** The add / edit dialog for habits, with unit chips (managed in Settings), number dials and quick amounts. */
final class HabitEditor {
    private HabitEditor() { }

    // ------------------------------------------------------------------ units (saved in Settings)

    static List<String[]> units(Activity a) {
        List<String[]> out = new ArrayList<>();
        for (String line : Prefs.habitUnits(a).split("\n")) {
            int eq = line.indexOf('=');
            String name = (eq > 0 ? line.substring(0, eq) : line).trim();
            if (name.isEmpty()) continue;
            out.add(new String[]{name, eq > 0 ? line.substring(eq + 1).trim() : ""});
        }
        return out;
    }

    static void saveUnits(Activity a, List<String[]> units) {
        StringBuilder sb = new StringBuilder();
        for (String[] u : units) sb.append(sb.length() > 0 ? "\n" : "").append(u[0]).append('=').append(u[1]);
        Prefs.setHabitUnits(a, sb.toString());
    }

    /** Dialog to add (idx < 0) or edit a unit and its quick amounts, saved in Settings. */
    static void editUnit(final Activity a, final int idx, final Runnable done) {
        final float d = a.getResources().getDisplayMetrics().density;
        final List<String[]> list = units(a);
        final EditText name = field(a, "Unit, e.g. ml, cups, laps", InputType.TYPE_CLASS_TEXT);
        final EditText amounts = field(a, "Quick amounts, e.g. 100, 250, 500", InputType.TYPE_CLASS_TEXT);
        if (idx >= 0) {
            name.setText(list.get(idx)[0]);
            amounts.setText(list.get(idx)[1]);
        }
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Math.round(22 * d), Math.round(6 * d), Math.round(22 * d), 0);
        box.addView(name, new LinearLayout.LayoutParams(-1, Math.round(50 * d)));
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(-1, Math.round(50 * d));
        alp.topMargin = Math.round(8 * d);
        box.addView(amounts, alp);
        Fonts.apply(box);
        Dlg.Builder b = new Dlg.Builder(a);
        b.setTitle(idx >= 0 ? "Edit unit" : "New unit")
                .setMessage("The quick amounts show as one-tap buttons on habits that use this unit.")
                .setView(box)
                .setPositiveButton("Save", (dlg, w) -> {
                    String n = name.getText().toString().trim().replace("=", "").replace("\n", " ");
                    if (n.isEmpty()) return;
                    String am = amounts.getText().toString().trim().replace("\n", " ");
                    String[] row = {n, am};
                    if (idx >= 0) list.set(idx, row);
                    else list.add(row);
                    saveUnits(a, list);
                    if (done != null) done.run();
                })
                .setNegativeButton("Cancel", null);
        if (idx >= 0) {
            b.setNeutralButton("Delete", (dlg, w) -> {
                list.remove(idx);
                saveUnits(a, list);
                if (done != null) done.run();
            });
        }
        b.show();
    }

    // ------------------------------------------------------------------ number dial with a custom list

    /** A dial over sensible steps (1s, then 5s, 10s, 50s, 100s...) that also takes any number typed with a double-tap. */
    static final class NumDial {
        final Wheel wheel;
        private final List<Integer> vals = new ArrayList<>();

        NumDial(Activity a, int initial, boolean allowZero) {
            wheel = new Wheel(a);
            wheel.setWrapSelectorWheel(false);
            for (int v = allowZero ? 0 : 1; v <= 20; v++) vals.add(v);
            for (int v = 25; v <= 100; v += 5) vals.add(v);
            for (int v = 110; v <= 500; v += 10) vals.add(v);
            for (int v = 550; v <= 2000; v += 50) vals.add(v);
            for (int v = 2100; v <= 5000; v += 100) vals.add(v);
            for (int v = 6000; v <= 30000; v += 1000) vals.add(v);
            rebuild(initial);
            wheel.setTypedHandler(v -> {
                int val = Math.max(allowZero ? 0 : 1, Math.min(100000, v));
                if (!vals.contains(val)) {
                    int at = 0;
                    while (at < vals.size() && vals.get(at) < val) at++;
                    vals.add(at, val);
                }
                rebuild(val);
            });
        }

        private void rebuild(int select) {
            if (!vals.contains(select)) {
                int at = 0;
                while (at < vals.size() && vals.get(at) < select) at++;
                vals.add(at, select);
            }
            String[] labels = new String[vals.size()];
            for (int i = 0; i < labels.length; i++) labels[i] = String.valueOf(vals.get(i));
            wheel.setMinValue(0);
            wheel.setMaxValue(vals.size() - 1);
            wheel.setDisplayedValues(labels);
            wheel.setValue(vals.indexOf(select));
            wheel.invalidate();
        }

        int value() {
            return vals.get(Math.max(0, Math.min(vals.size() - 1, wheel.getValue())));
        }

        void set(int v) {
            rebuild(v);
        }
    }

    /** "Custom amount" dialog for a habit: a dial (double-tap to type), then the amount is handed back. */
    static void askAmount(final Activity a, final Habit h, final IntConsumer onPick) {
        final float d = a.getResources().getDisplayMetrics().density;
        final NumDial dial = new NumDial(a, h.step, false);
        LinearLayout box = new LinearLayout(a);
        box.setGravity(Gravity.CENTER);
        box.addView(dial.wheel, new LinearLayout.LayoutParams(Math.round(140 * d), -2));
        if (!h.unit.isEmpty()) {
            TextView u = new TextView(a);
            u.setText(h.unit);
            u.setTextSize(20);
            u.setTypeface(Typeface.DEFAULT_BOLD);
            u.setTextColor(Pal.muted);
            u.setPadding(Math.round(10 * d), 0, 0, 0);
            box.addView(u);
        }
        Fonts.apply(box);
        new Dlg.Builder(a)
                .setTitle("Add how much?")
                .setMessage("Double-tap the number to type it")
                .setView(box)
                .setPositiveButton("Add", (dlg, w) -> onPick.accept(dial.value()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ------------------------------------------------------------------ the editor

    static void show(final Activity a, final Habit existing, final Runnable saved) {
        final float d = a.getResources().getDisplayMetrics().density;
        final android.app.Dialog dlg = new android.app.Dialog(a, android.R.style.Theme_DeviceDefault_NoActionBar);
        LinearLayout page = new LinearLayout(a);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Pal.bg);

        // ---- header: back, title, Save pill
        LinearLayout head = new LinearLayout(a);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(Math.round(8 * d), Math.round(10 * d), Math.round(16 * d), Math.round(6 * d));
        IconView close = new IconView(a, IconView.BACK);
        close.setContentDescription("Close");
        close.setOnClickListener(v -> dlg.dismiss());
        head.addView(close, new LinearLayout.LayoutParams(Math.round(48 * d), Math.round(48 * d)));
        TextView title = new TextView(a);
        title.setText(existing == null ? "New habit" : "Edit habit");
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Pal.ink);
        head.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        final TextView save = new TextView(a);
        save.setText("Save");
        save.setTextSize(16);
        save.setTypeface(Typeface.DEFAULT_BOLD);
        save.setTextColor(Pal.onAccent);
        save.setGravity(Gravity.CENTER);
        save.setPadding(Math.round(26 * d), 0, Math.round(26 * d), 0);
        GradientDrawable sg = new GradientDrawable();
        sg.setColor(Pal.accent);
        sg.setCornerRadius(100 * d);
        save.setBackground(sg);
        head.addView(save, new LinearLayout.LayoutParams(-2, Math.round(46 * d)));
        page.addView(head);

        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Math.round(16 * d), Math.round(6 * d), Math.round(16 * d), Math.round(40 * d));

        // ---- name card
        LinearLayout nameCard = section(a);
        LinearLayout top = new LinearLayout(a);
        top.setGravity(Gravity.CENTER_VERTICAL);
        final EditText emoji = field(a, "\uD83D\uDE00", InputType.TYPE_CLASS_TEXT);
        emoji.setGravity(Gravity.CENTER);
        emoji.setTextSize(26);
        final EditText name = field(a, "Habit name", InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        name.setTextSize(18);
        top.addView(emoji, new LinearLayout.LayoutParams(Math.round(68 * d), Math.round(60 * d)));
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(0, Math.round(60 * d), 1f);
        nlp.leftMargin = Math.round(10 * d);
        top.addView(name, nlp);
        nameCard.addView(top);
        box.addView(nameCard);

        // ---- unit card
        LinearLayout unitCard = section(a);
        unitCard.addView(label(a, "UNIT"));
        final String[] unit = {existing == null ? "" : existing.unit};
        final EditText extras = field(a, "e.g. 100, 250, 500", InputType.TYPE_CLASS_TEXT);
        final String[] lastTemplate = {""};
        final FlowLayout chips = new FlowLayout(a, Math.round(8 * d), Math.round(8 * d));
        final NumDial goal = new NumDial(a, existing == null ? 0 : existing.goal, true);
        final NumDial step = new NumDial(a, existing == null ? 1 : existing.step, false);
        final Runnable[] drawChips = new Runnable[1];
        drawChips[0] = () -> {
            chips.removeAllViews();
            chips.addView(chip(a, "Tick only", unit[0].isEmpty(), v -> {
                unit[0] = "";
                goal.set(0);
                drawChips[0].run();
            }), new ViewGroup.LayoutParams(-2, Math.round(44 * d)));
            for (final String[] u : units(a)) {
                chips.addView(chip(a, u[0], u[0].equals(unit[0]), v -> {
                    String cur = extras.getText().toString().trim();
                    unit[0] = u[0];
                    if (cur.isEmpty() || cur.equals(lastTemplate[0])) extras.setText(u[1]);
                    lastTemplate[0] = u[1];
                    drawChips[0].run();
                }), new ViewGroup.LayoutParams(-2, Math.round(44 * d)));
            }
            chips.addView(chip(a, "+ New", false, v -> editUnit(a, -1, drawChips[0])),
                    new ViewGroup.LayoutParams(-2, Math.round(44 * d)));
            Fonts.apply(chips);
        };
        unitCard.addView(chips);
        box.addView(unitCard);

        // ---- dials card: each dial sits in its own soft panel
        LinearLayout dialCard = section(a);
        LinearLayout dials = new LinearLayout(a);
        dials.addView(dialPanel(a, "DAILY GOAL", goal.wheel), new LinearLayout.LayoutParams(0, -2, 1f));
        View gap = new View(a);
        dials.addView(gap, new LinearLayout.LayoutParams(Math.round(12 * d), 1));
        dials.addView(dialPanel(a, "ONE TAP ADDS", step.wheel), new LinearLayout.LayoutParams(0, -2, 1f));
        dialCard.addView(dials);
        TextView hint = new TextView(a);
        hint.setText("Goal 0 makes it a simple tick. Swipe a dial, or double-tap its number to type any value.");
        hint.setTextSize(13);
        hint.setLineSpacing(0, 1.1f);
        hint.setTextColor(Pal.muted);
        hint.setPadding(Math.round(4 * d), Math.round(12 * d), Math.round(4 * d), 0);
        dialCard.addView(hint);
        box.addView(dialCard);

        // ---- quick amounts card
        LinearLayout extraCard = section(a);
        extraCard.addView(label(a, "MORE QUICK AMOUNTS"));
        extraCard.addView(extras, new LinearLayout.LayoutParams(-1, Math.round(54 * d)));
        TextView eh = new TextView(a);
        eh.setText("Separate with commas. They appear as one-tap buttons on the habit's page.");
        eh.setTextSize(13);
        eh.setTextColor(Pal.muted);
        eh.setPadding(Math.round(4 * d), Math.round(8 * d), Math.round(4 * d), 0);
        extraCard.addView(eh);
        box.addView(extraCard);

        // ---- colour card
        LinearLayout colorCard = section(a);
        colorCard.addView(label(a, "COLOUR"));
        final int[] color = {existing != null ? existing.color : HabitDb.COLORS[0]};
        final LinearLayout colors = new LinearLayout(a);
        final View[] dots = new View[HabitDb.COLORS.length];
        for (int i = 0; i < dots.length; i++) {
            final int idx = i;
            dots[i] = new View(a);
            dots[i].setOnClickListener(v -> {
                color[0] = HabitDb.COLORS[idx];
                paint(dots, color[0], d);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Math.round(38 * d), Math.round(38 * d));
            lp.rightMargin = Math.round(12 * d);
            colors.addView(dots[i], lp);
        }
        paint(dots, color[0], d);
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(colors);
        hs.setPadding(Math.round(4 * d), Math.round(4 * d), 0, Math.round(4 * d));
        colorCard.addView(hs);
        box.addView(colorCard);

        if (existing != null) {
            emoji.setText(existing.emoji);
            name.setText(existing.name);
            extras.setText(existing.extras);
        }
        drawChips[0].run();

        save.setOnClickListener(v -> {
            String n = name.getText().toString().trim();
            if (n.isEmpty()) {
                name.setError("Give it a name");
                return;
            }
            String em = emoji.getText().toString().trim();
            if (em.length() > 8) em = em.substring(0, 8);
            int g = goal.value();
            int s = Math.max(1, step.value());
            String ex = extras.getText().toString().trim();
            HabitDb db = new HabitDb(a);
            try {
                if (existing == null) db.add(n, em, color[0], g, unit[0], s, ex);
                else db.update(existing.id, n, em, color[0], g, unit[0], s, ex);
            } finally {
                db.close();
            }
            dlg.dismiss();
            if (saved != null) saved.run();
        });

        ScrollView sv = new ScrollView(a);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(box);
        page.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        Fonts.apply(page);
        dlg.setContentView(page);
        android.view.Window w = dlg.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Pal.bg));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            w.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        dlg.show();
    }

    /** A rounded white card holding one group of settings. */
    private static LinearLayout section(Activity a) {
        float d = a.getResources().getDisplayMetrics().density;
        LinearLayout c = new LinearLayout(a);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(Math.round(18 * d), Math.round(8 * d), Math.round(18 * d), Math.round(18 * d));
        GradientDrawable g = new GradientDrawable();
        g.setColor(Pal.card);
        g.setCornerRadius(22 * d);
        c.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = Math.round(12 * d);
        c.setLayoutParams(lp);
        return c;
    }

    /** A dial with its caption inside a soft rounded panel. */
    private static View dialPanel(Activity a, String caption, Wheel wheel) {
        float d = a.getResources().getDisplayMetrics().density;
        LinearLayout p = new LinearLayout(a);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setGravity(Gravity.CENTER_HORIZONTAL);
        p.setPadding(Math.round(6 * d), Math.round(2 * d), Math.round(6 * d), Math.round(8 * d));
        GradientDrawable g = new GradientDrawable();
        g.setColor(Pal.chip);
        g.setCornerRadius(20 * d);
        p.setBackground(g);
        TextView t = label(a, caption);
        t.setPadding(0, Math.round(12 * d), 0, Math.round(2 * d));
        t.setGravity(Gravity.CENTER);
        p.addView(t, new LinearLayout.LayoutParams(-1, -2));
        p.addView(wheel, new LinearLayout.LayoutParams(-1, -2));
        return p;
    }

    private static TextView label(Activity a, String s) {
        float d = a.getResources().getDisplayMetrics().density;
        TextView t = new TextView(a);
        t.setText(s);
        t.setTextSize(11);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setLetterSpacing(0.12f);
        t.setTextColor(Pal.muted);
        t.setPadding(Math.round(4 * d), Math.round(12 * d), 0, Math.round(6 * d));
        return t;
    }

    private static TextView chip(Activity a, String s, boolean on, View.OnClickListener l) {
        float d = a.getResources().getDisplayMetrics().density;
        TextView t = new TextView(a);
        t.setText(s);
        t.setTextSize(14);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER);
        t.setPadding(Math.round(14 * d), 0, Math.round(14 * d), 0);
        t.setTextColor(on ? Pal.onSel : Pal.muted);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(22 * d);
        g.setColor(on ? Pal.sel : Pal.chip);
        t.setBackground(g);
        t.setOnClickListener(l);
        return t;
    }

    private static void paint(View[] dots, int selected, float d) {
        for (int i = 0; i < dots.length; i++) {
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(HabitDb.COLORS[i]);
            if (HabitDb.COLORS[i] == selected) g.setStroke(Math.round(3 * d), Pal.ink);
            dots[i].setBackground(g);
        }
    }

    private static EditText field(Activity a, String hint, int type) {
        float d = a.getResources().getDisplayMetrics().density;
        EditText e = new EditText(a);
        e.setHint(hint);
        e.setInputType(type);
        e.setSingleLine(true);
        e.setTextSize(15);
        e.setTextColor(Pal.ink);
        e.setHintTextColor(Pal.muted);
        GradientDrawable g = new GradientDrawable();
        g.setColor(Pal.chip);
        g.setCornerRadius(14 * d);
        e.setBackground(g);
        e.setPadding(Math.round(12 * d), 0, Math.round(12 * d), 0);
        return e;
    }
}
