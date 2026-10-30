package app.privatetodo;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Design lab: time-dial looks, slide-left and slide-right animations and main-screen title animations to try
 * by hand. "Keep" marks a shortlist; "Use in the app" (or Default / Random) picks what the app really uses.
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
    private TextView tabTitle;
    private TextView useLine;
    private LinearLayout useChips;
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

        TextView intro = text("Try each one with your finger. Keep is just your shortlist. \"Use in the app\" "
                + "makes the app use it for real: Default is how the app always was, Random picks a different "
                + "one every time. The test rows here never delete or complete anything.", 14, Pal.muted, false);
        intro.setPadding(dp(8), dp(4), dp(8), dp(12));
        col.addView(intro);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabDial = tab("Time dials");
        tabSwipe = tab("Delete");
        tabTick = tab("Done");
        tabTitle = tab("Title");
        tabDial.setOnClickListener(v -> pick(0));
        tabSwipe.setOnClickListener(v -> pick(1));
        tabTick.setOnClickListener(v -> pick(2));
        tabTitle.setOnClickListener(v -> pick(3));
        TextView[] all = {tabDial, tabSwipe, tabTick, tabTitle};
        for (int i = 0; i < all.length; i++) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(44), 1f);
            lp.leftMargin = i == 0 ? 0 : dp(3);
            lp.rightMargin = i == all.length - 1 ? 0 : dp(3);
            all[i].setTextSize(13);
            tabs.addView(all[i], lp);
        }
        col.addView(tabs);

        useLine = text("", 14, Pal.ink, true);
        useLine.setPadding(dp(8), dp(12), dp(8), 0);
        col.addView(useLine);
        useChips = new LinearLayout(this);
        useChips.setOrientation(LinearLayout.HORIZONTAL);
        useChips.setPadding(0, dp(6), 0, dp(2));
        useChips.addView(useChip("Default", AnimPrefs.DEFAULT), chipLp(false));
        useChips.addView(useChip("Random", AnimPrefs.RANDOM), chipLp(true));
        col.addView(useChips);

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
        styleTab(tabTitle, tab == 3);
        list.removeAllViews();
        String[] names = names();
        String[] notes = tab == 0 ? LabDial.NOTES : tab == 1 ? LabSwipe.NOTES : tab == 2 ? LabTick.NOTES : TitleFx.NOTES;
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
            names2.addView(text((tab == 0 ? "Dial " : tab == 1 ? "Delete " : tab == 2 ? "Done " : "Title ") + (i + 1) + " · " + names[i], 17, Pal.ink, true));
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

            LinearLayout useRow2 = new LinearLayout(this);
            useRow2.setOrientation(LinearLayout.HORIZONTAL);
            useRow2.setGravity(Gravity.END);
            final TextView use = text("", 14, Pal.ink, true);
            use.setPadding(dp(14), dp(8), dp(14), dp(8));
            styleUse(use, AnimPrefs.mode(this, kind()) == idx);
            use.setOnClickListener(v -> {
                AnimPrefs.setMode(this, kind(), idx);
                Hap.confirm(v);
                fill();
            });
            useRow2.addView(use);
            LinearLayout.LayoutParams ul = new LinearLayout.LayoutParams(-1, -2);
            ul.topMargin = dp(6);
            card.addView(useRow2, ul);

            View demo;
            if (tab == 3) {
                FrameLayout box = new FrameLayout(this);
                GradientDrawable pg = new GradientDrawable();
                pg.setColor(Pal.bg);
                pg.setCornerRadius(dp(18));
                box.setBackground(pg);
                box.setPadding(dp(18), dp(14), dp(18), dp(14));
                box.addView(new TitleFx(this, "Tasks", 34, i, 1400), new FrameLayout.LayoutParams(-2, -2));
                demo = box;
            } else if (tab == 0) {
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
        refreshUse();
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
        return tab == 0 ? LabDial.NAMES : tab == 1 ? LabSwipe.NAMES : tab == 2 ? LabTick.NAMES : TitleFx.NAMES;
    }

    private String prefix() {
        return tab == 0 ? "dial_" : tab == 1 ? "swipe_" : tab == 2 ? "tick_" : "title_";
    }

    private int kind() {
        return tab == 0 ? AnimPrefs.DIAL : tab == 1 ? AnimPrefs.SWIPE : tab == 2 ? AnimPrefs.TICK : AnimPrefs.TITLE;
    }

    private TextView useChip(String label, final int mode) {
        TextView t = text(label, 14, Pal.ink, true);
        t.setGravity(Gravity.CENTER);
        t.setTag(mode);
        t.setOnClickListener(v -> {
            AnimPrefs.setMode(this, kind(), mode);
            Hap.confirm(v);
            fill();
        });
        return t;
    }

    private LinearLayout.LayoutParams chipLp(boolean second) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(44), 1f);
        if (second) lp.leftMargin = dp(6);
        else lp.rightMargin = dp(6);
        return lp;
    }

    private void styleUse(TextView t, boolean on) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(20));
        g.setColor(on ? Pal.sel : Pal.chip);
        t.setBackground(g);
        t.setText(on ? "In the app ✓" : "Use in the app");
        t.setTextColor(on ? Pal.onSel : Pal.ink);
    }

    /** Colours the Default / Random chips and says what the app uses for this tab right now. */
    private void refreshUse() {
        int m = AnimPrefs.mode(this, kind());
        String[] nm = names();
        String what = tab == 0 ? "time dials" : tab == 1 ? "slide left (delete)" : tab == 2 ? "slide right (done)" : "main-screen title";
        String now = m == AnimPrefs.DEFAULT ? "Default" : m == AnimPrefs.RANDOM ? "Random, a different one every time"
                : (m + 1) + " · " + nm[m];
        useLine.setText("In the app for " + what + ": " + now);
        for (int i = 0; i < useChips.getChildCount(); i++) {
            TextView c = (TextView) useChips.getChildAt(i);
            boolean on = ((Integer) c.getTag()) == m;
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(dp(22));
            g.setColor(on ? Pal.sel : Pal.card);
            c.setBackground(g);
            c.setTextColor(on ? Pal.onSel : Pal.ink);
        }
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
        String what = tab == 0 ? "dials" : tab == 1 ? "delete animations" : tab == 2 ? "done animations" : "titles";
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
