package app.privatetodo;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Design lab: ten swipe-time dial looks and ten slide-to-delete animations to try by hand.
 * Nothing here is wired into the app. Mark the ones you like with "Keep"; the list at the top
 * shows what you picked.
 */
public class LabActivity extends Activity {
    private float density;
    private int builtTheme;
    private int tab;
    private LinearLayout list;
    private TextView summary;
    private TextView tabDial;
    private TextView tabSwipe;
    private TextView tabTick;
    private SharedPreferences sp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Pal.init(this);
        builtTheme = Pal.themeRes();
        setTheme(Pal.themeRes());
        super.onCreate(savedInstanceState);
        density = getResources().getDisplayMetrics().density;
        sp = getSharedPreferences("lab", Context.MODE_PRIVATE);
        tab = savedInstanceState != null ? savedInstanceState.getInt("tab") : 0;
        setContentView(build());
        Fonts.apply(getWindow().getDecorView());
        fill();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("tab", tab);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Pal.init(this);
        if (Pal.themeRes() != builtTheme) recreate();
    }

    private View build() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Pal.bg);
        scroll.setFillViewport(true);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(12), dp(16), dp(40));
        scroll.addView(col, new ScrollView.LayoutParams(-1, -2));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        IconView back = new IconView(this, IconView.BACK);
        back.setContentDescription("Back");
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        header.addView(text("Design lab", 30, Pal.ink, true));
        col.addView(header);

        TextView intro = text("Try each one with your finger, then tap Keep on the ones you like. "
                + "None of this is connected to the app yet, nothing is saved or deleted.", 14, Pal.muted, false);
        intro.setPadding(dp(8), dp(4), dp(8), dp(12));
        col.addView(intro);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabDial = tab("Time dials");
        tabSwipe = tab("Delete");
        tabTick = tab("Done");
        tabDial.setOnClickListener(v -> pick(0));
        tabSwipe.setOnClickListener(v -> pick(1));
        tabTick.setOnClickListener(v -> pick(2));
        LinearLayout.LayoutParams a = new LinearLayout.LayoutParams(0, dp(44), 1f);
        a.rightMargin = dp(4);
        LinearLayout.LayoutParams m = new LinearLayout.LayoutParams(0, dp(44), 1f);
        m.leftMargin = dp(4);
        m.rightMargin = dp(4);
        LinearLayout.LayoutParams b = new LinearLayout.LayoutParams(0, dp(44), 1f);
        b.leftMargin = dp(4);
        tabs.addView(tabDial, a);
        tabs.addView(tabSwipe, m);
        tabs.addView(tabTick, b);
        col.addView(tabs);

        summary = text("", 14, Pal.accent, true);
        summary.setPadding(dp(8), dp(14), dp(8), dp(4));
        col.addView(summary);

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        col.addView(list);
        return scroll;
    }

    private void pick(int t) {
        if (t == tab) return;
        Hap.tick(list);
        tab = t;
        fill();
    }

    private TextView tab(String s) {
        TextView t = text(s, 15, Pal.ink, true);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private void styleTab(TextView t, boolean on) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(22));
        g.setColor(on ? Pal.sel : Pal.card);
        t.setBackground(g);
        t.setTextColor(on ? Pal.onSel : Pal.ink);
    }

    private void fill() {
        styleTab(tabDial, tab == 0);
        styleTab(tabSwipe, tab == 1);
        styleTab(tabTick, tab == 2);
        list.removeAllViews();
        String[] names = names();
        String[] notes = tab == 0 ? LabDial.NOTES : tab == 1 ? LabSwipe.NOTES : LabTick.NOTES;
        String prefix = prefix();
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(16), dp(14), dp(16), dp(14));
            GradientDrawable g = new GradientDrawable();
            g.setColor(Pal.card);
            g.setCornerRadius(dp(22));
            card.setBackground(g);

            LinearLayout top = new LinearLayout(this);
            top.setOrientation(LinearLayout.HORIZONTAL);
            top.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout names2 = new LinearLayout(this);
            names2.setOrientation(LinearLayout.VERTICAL);
            names2.addView(text((tab == 0 ? "Dial " : tab == 1 ? "Delete " : "Done ") + (i + 1) + " · " + names[i], 17, Pal.ink, true));
            TextView note = text(notes[i], 13, Pal.muted, false);
            note.setPadding(0, dp(2), dp(8), 0);
            names2.addView(note);
            top.addView(names2, new LinearLayout.LayoutParams(0, -2, 1f));
            final TextView keep = text("", 14, Pal.ink, true);
            keep.setPadding(dp(14), dp(8), dp(14), dp(8));
            styleKeep(keep, sp.getBoolean(prefix + i, false));
            keep.setOnClickListener(v -> {
                boolean now = !sp.getBoolean(prefix + idx, false);
                sp.edit().putBoolean(prefix + idx, now).apply();
                Hap.tick(v);
                styleKeep(keep, now);
                updateSummary();
            });
            top.addView(keep);
            card.addView(top);

            View demo;
            if (tab == 0) {
                final LabDial dial = new LabDial(this, i);
                final TextView live = text(dial.time(), 15, Pal.accent, true);
                live.setPadding(0, dp(6), 0, 0);
                dial.setOnChange(() -> live.setText(dial.time()));
                LinearLayout box = new LinearLayout(this);
                box.setOrientation(LinearLayout.VERTICAL);
                box.addView(dial, new LinearLayout.LayoutParams(-1, -2));
                box.addView(live);
                demo = box;
            } else if (tab == 2) {
                final LabTick tk = new LabTick(this, i);
                GradientDrawable pg = new GradientDrawable();
                pg.setColor(Pal.bg);
                pg.setCornerRadius(dp(18));
                tk.setBackground(pg);
                demo = tk;
            } else {
                final LabSwipe sw = new LabSwipe(this, i);
                GradientDrawable pg = new GradientDrawable();
                pg.setColor(Pal.bg);
                pg.setCornerRadius(dp(18));
                sw.setBackground(pg);
                demo = sw;
            }
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-1, -2);
            dl.topMargin = dp(10);
            card.addView(demo, dl);

            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-1, -2);
            cl.setMargins(0, dp(6), 0, dp(6));
            list.addView(card, cl);
        }
        updateSummary();
        Fonts.apply(list);
    }

    private void styleKeep(TextView k, boolean on) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(20));
        if (on) {
            g.setColor(Pal.accent);
        } else {
            g.setColor(Pal.chip);
        }
        k.setBackground(g);
        k.setText(on ? "Kept ✓" : "Keep");
        k.setTextColor(on ? Pal.onAccent : Pal.ink);
    }

    private String[] names() {
        return tab == 0 ? LabDial.NAMES : tab == 1 ? LabSwipe.NAMES : LabTick.NAMES;
    }

    private String prefix() {
        return tab == 0 ? "dial_" : tab == 1 ? "swipe_" : "tick_";
    }

    private void updateSummary() {
        String prefix = prefix();
        StringBuilder sb = new StringBuilder();
        int n = names().length;
        for (int i = 0; i < n; i++) {
            if (sp.getBoolean(prefix + i, false)) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(i + 1);
            }
        }
        String what = tab == 0 ? "dials" : tab == 1 ? "delete animations" : "done animations";
        summary.setText(sb.length() == 0 ? "Kept: none yet" : "Kept " + what + ": " + sb);
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
