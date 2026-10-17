package app.privatetodo;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** The add / edit dialog shared by the habit screens. */
final class HabitEditor {
    private HabitEditor() { }

    static void show(final Activity a, final Habit existing, final Runnable saved) {
        final float d = a.getResources().getDisplayMetrics().density;
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Math.round(22 * d), Math.round(6 * d), Math.round(22 * d), 0);

        LinearLayout top = new LinearLayout(a);
        top.setGravity(Gravity.CENTER_VERTICAL);
        final EditText emoji = field(a, "😀", InputType.TYPE_CLASS_TEXT);
        emoji.setGravity(Gravity.CENTER);
        emoji.setTextSize(24);
        final EditText name = field(a, "Habit name", InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        top.addView(emoji, new LinearLayout.LayoutParams(Math.round(64 * d), Math.round(52 * d)));
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(0, Math.round(52 * d), 1f);
        nlp.leftMargin = Math.round(8 * d);
        top.addView(name, nlp);
        box.addView(top);

        LinearLayout goals = new LinearLayout(a);
        final EditText goal = field(a, "Goal / day", InputType.TYPE_CLASS_NUMBER);
        final EditText unit = field(a, "Unit (ml, min...)", InputType.TYPE_CLASS_TEXT);
        final EditText step = field(a, "Per tap", InputType.TYPE_CLASS_NUMBER);
        goals.addView(goal, new LinearLayout.LayoutParams(0, Math.round(48 * d), 1f));
        LinearLayout.LayoutParams ulp = new LinearLayout.LayoutParams(0, Math.round(48 * d), 1.2f);
        ulp.leftMargin = Math.round(6 * d);
        ulp.rightMargin = Math.round(6 * d);
        goals.addView(unit, ulp);
        goals.addView(step, new LinearLayout.LayoutParams(0, Math.round(48 * d), 1f));
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(-1, -2);
        glp.topMargin = Math.round(8 * d);
        box.addView(goals, glp);
        TextView hint = new TextView(a);
        hint.setText("Goal 0 = a simple tick. “Per tap” is how much one tap on the badge adds.");
        hint.setTextSize(12);
        hint.setTextColor(Pal.muted);
        hint.setPadding(Math.round(4 * d), Math.round(6 * d), 0, Math.round(8 * d));
        box.addView(hint);

        final int[] color = {existing != null ? existing.color : HabitDb.COLORS[0]};
        final LinearLayout chips = new LinearLayout(a);
        final View[] dots = new View[HabitDb.COLORS.length];
        for (int i = 0; i < dots.length; i++) {
            final int idx = i;
            dots[i] = new View(a);
            dots[i].setOnClickListener(v -> {
                color[0] = HabitDb.COLORS[idx];
                paint(dots, color[0], d);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Math.round(30 * d), Math.round(30 * d));
            lp.rightMargin = Math.round(8 * d);
            chips.addView(dots[i], lp);
        }
        paint(dots, color[0], d);
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(chips);
        box.addView(hs);

        if (existing != null) {
            emoji.setText(existing.emoji);
            name.setText(existing.name);
            goal.setText(String.valueOf(existing.goal));
            unit.setText(existing.unit);
            step.setText(String.valueOf(existing.step));
        } else {
            goal.setText("0");
            step.setText("1");
        }
        Fonts.apply(box);

        new Dlg.Builder(a)
                .setTitle(existing == null ? "New habit" : "Edit habit")
                .setView(box)
                .setPositiveButton("Save", (dlg, w) -> {
                    String n = name.getText().toString().trim();
                    if (n.isEmpty()) return;
                    String em = emoji.getText().toString().trim();
                    if (em.length() > 8) em = em.substring(0, 8);
                    int g = num(goal.getText().toString(), 0);
                    int s = Math.max(1, num(step.getText().toString(), 1));
                    String u = unit.getText().toString().trim();
                    HabitDb db = new HabitDb(a);
                    try {
                        if (existing == null) db.add(n, em, color[0], g, u, s);
                        else db.update(existing.id, n, em, color[0], g, u, s);
                    } finally {
                        db.close();
                    }
                    if (saved != null) saved.run();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static int num(String s, int def) {
        try {
            return Math.max(0, Integer.parseInt(s.trim()));
        } catch (NumberFormatException e) {
            return def;
        }
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
        EditText e = new EditText(a);
        e.setHint(hint);
        e.setInputType(type);
        e.setSingleLine(true);
        e.setTextSize(15);
        e.setTextColor(Pal.ink);
        e.setHintTextColor(Pal.muted);
        GradientDrawable g = new GradientDrawable();
        g.setColor(Pal.chip);
        g.setCornerRadius(14 * a.getResources().getDisplayMetrics().density);
        e.setBackground(g);
        e.setPadding(Math.round(12 * a.getResources().getDisplayMetrics().density), 0,
                Math.round(12 * a.getResources().getDisplayMetrics().density), 0);
        return e;
    }
}
