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

import java.util.Locale;

/**
 * The smart-dates guide: everything you can type after the trigger symbol. Each example shows what it means
 * right now (worked out by the same parser the app uses), so the guide can never drift from the behaviour.
 */
public class SmartGuideActivity extends Activity {
    private float density;
    private int builtDark;
    private String trig;

    @Override
    protected void onCreate(Bundle b) {
        Pal.init(this);
        builtDark = Pal.themeRes();
        setTheme(Pal.themeRes());
        super.onCreate(b);
        density = getResources().getDisplayMetrics().density;
        trig = Prefs.smartTrigger(this);
        setContentView(build());
    }

    @Override
    protected void onResume() {
        super.onResume();
        Pal.init(this);
        if (Pal.themeRes() != builtDark) recreate();
    }

    private View build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Pal.bg);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(12), dp(12), dp(16), 0);
        IconView back = new IconView(this, IconView.BACK);
        back.setContentDescription("Back");
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        header.addView(text("Smart dates guide", 26, Pal.ink, true));
        root.addView(header);

        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(4), dp(16), dp(40));
        sv.addView(col, new ScrollView.LayoutParams(-1, -2));
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));

        // ---- the idea
        LinearLayout idea = card();
        idea.addView(text("How it works", 18, Pal.ink, true));
        TextView p = text("Type your task as usual, then " + trig + " and say when. The app reads only what comes after "
                + trig + ", sets the reminder, and removes that part from the title, so the list stays clean.\n\n"
                + "Typing  Call mum " + trig + "sat 2pm  creates the task “Call mum” due Saturday at 14:00. "
                + "Capital letters do not matter. If nothing after " + trig + " is understood, the task is saved exactly as typed.",
                15, Pal.muted, false);
        p.setLineSpacing(0, 1.12f);
        p.setPadding(0, dp(6), 0, 0);
        idea.addView(p);
        col.addView(idea);

        section(col, "Days", "Say a day, a weekday, or how far away it is.", new String[]{
                "today", "tomorrow", "day after tomorrow", "friday", "next monday", "this sunday", "next week", "next month"});
        section(col, "Exact dates", "Month names, numbers, ISO style and years all work. Without a year it picks the next one that is still ahead.",
                new String[]{"12 may", "may 12", "12 may 2027", "25th december", "12/05/2027", "2027-05-12",
                        "the 15th", "1 jan 2028 9am"});
        section(col, "Times", "Add a time to any day. With no time, your default time from Settings is used.", new String[]{
                "2pm", "2:30pm", "14:30", "at 5pm", "noon", "midnight", "tonight", "fri 6:15pm"});
        section(col, "From now", "Count forward from this moment.", new String[]{
                "in 20 min", "in 2 hours", "in 3 days", "in 2 weeks", "in a month", "in 1 hour"});
        section(col, "Repeats", "Start with every, or use a one-word repeat. The first reminder lands on the next matching moment.",
                new String[]{"daily 8am", "weekly", "monthly", "every day 7am", "every 3 days", "every 2 weeks",
                        "every monday 2pm", "every mon and fri 6pm", "every mon wed fri 6am", "every weekday 9am",
                        "every weekend 10am", "every 30 min", "every month on the 15th"});
        section(col, "Mix and match", "Everything above combines in one phrase.", new String[]{
                "next friday 5pm", "12 may 2027 10:30am", "every tuesday 3pm", "tomorrow noon", "in 2 days 6pm"});

        LinearLayout sc = card();
        sc.addView(text("Your own shortcuts", 18, Pal.ink, true));
        TextView sp = text("In Settings > Smart dates > My shortcuts you can teach it words. A shortcut is any word that "
                + "stands for a whole phrase, for example:\n\n"
                + "   gym = every mon wed fri 6pm\n   rent = every month on the 1st\n"
                + "   lunch = 1pm\n\nThen " + trig + "gym creates a repeating gym task. Built in: morning, noon, afternoon, "
                + "evening, night, eod, weekend and nextweek. You can change or delete any of them.",
                15, Pal.muted, false);
        sp.setLineSpacing(0, 1.12f);
        sp.setPadding(0, dp(6), 0, 0);
        sc.addView(sp);
        col.addView(sc);

        LinearLayout tips = card();
        tips.addView(text("Good to know", 18, Pal.ink, true));
        TextView tp = text("•  Numbers like 12/05/2027 follow your region: day first almost everywhere, month first in the US. "
                + "If one number is above 12 the order is clear either way.\n"
                + "•  A lone number is not a time. Write 5pm, 17:00 or at 5.\n"
                + "•  Turn on Ring as alarm in Settings to make these tasks use the full-screen alarm.\n"
                + "•  Change the trigger symbol to anything you like if " + trig + " gets in your way.\n"
                + "•  Everything is worked out on your phone. Nothing is sent anywhere.",
                15, Pal.muted, false);
        tp.setLineSpacing(0, 1.12f);
        tp.setPadding(0, dp(6), 0, 0);
        tips.addView(tp);
        col.addView(tips);
        Fonts.apply(root);
        return root;
    }

    private void section(LinearLayout col, String title, String intro, String[] phrases) {
        LinearLayout c = card();
        c.addView(text(title, 18, Pal.ink, true));
        TextView t = text(intro, 14, Pal.muted, false);
        t.setPadding(0, dp(4), 0, dp(6));
        c.addView(t);
        long now = System.currentTimeMillis();
        for (String ph : phrases) {
            SmartDate.Result r = SmartDate.parse(this, "task " + trig + ph, now);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(0, dp(7), 0, dp(7));
            TextView ex = text(trig + ph, 15, Pal.accent, true);
            ex.setTypeface(Typeface.create("monospace", Typeface.BOLD));
            row.addView(ex);
            String res;
            if (!r.found) {
                res = "(not understood yet)";
            } else {
                res = Reminders.when(this, r.due);
                if (r.repeat > 0) res += "  ·  " + Reminders.repeatLabel(r.repeat, r.unit).toLowerCase(Locale.ROOT);
            }
            TextView out = text("→  " + res, 14, Pal.ink, false);
            out.setPadding(0, dp(1), 0, 0);
            row.addView(out);
            c.addView(row);
        }
        col.addView(c);
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(18), dp(16), dp(18), dp(12));
        GradientDrawable g = new GradientDrawable();
        g.setColor(Pal.card);
        g.setCornerRadius(dp(22));
        c.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(12);
        c.setLayoutParams(lp);
        return c;
    }

    private int dp(int v) {
        return Math.round(v * density);
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }
}
