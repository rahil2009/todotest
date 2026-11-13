package app.privatetodo;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * A dim, black page over the lock screen: time, then the reminder. Fades in, stays for the chosen seconds, fades to
 * pure black (pixels off on an OLED), and closes when the screen turns off. Tap to close.
 */
public class PeekActivity extends Activity {
    private final Handler h = new Handler(Looper.getMainLooper());
    private View content;
    private BroadcastReceiver off;
    static final String EXTRA_TEST = "test";
    static final String EXTRA_PREVIEW = "preview_titles";
    static final String EXTRA_MODE = "preview_mode";

    private int collideMode() {
        return getIntent().hasExtra(EXTRA_MODE) ? getIntent().getIntExtra(EXTRA_MODE, 0) : Prefs.collide(this);
    }

    private FrameLayout outer;
    private TestSheet sheet;
    private String pTitle = "";
    private String pText = "";
    private final java.util.ArrayList<String[]> items = new java.util.ArrayList<>();
    private int shown;
    private boolean test;

    /** Shows the current reminder for the chosen seconds, then the next waiting one, then fades to black. */
    private void scheduleRound() {
        h.removeCallbacksAndMessages(null);
        final int secs = Math.max(2, Peek.seconds(this));
        final boolean together = collideMode() == 1;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        h.postDelayed(() -> {
            if (!together && shown + 1 < items.size()) { // one at a time: the next one comes straight after
                content.animate().alpha(0f).setDuration(350).withEndAction(() -> {
                    shown++;
                    pTitle = items.get(shown)[0];
                    pText = items.get(shown)[1];
                    rebuild();
                    content.setAlpha(0f);
                    content.animate().alpha(1f).setDuration(350).start();
                    scheduleRound();
                }).start();
                return;
            }
            content.animate().alpha(0f).setDuration(900).start(); // fade to black, stop holding the screen awake
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            h.postDelayed(this::finish, 25_000L); // never lingers if the screen never turns off
        }, secs * 1000L);
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        if (test) return;
        String t = intent.getStringExtra(Peek.EXTRA_TITLE);
        String x = intent.getStringExtra(Peek.EXTRA_TEXT);
        String[] it = {t == null ? "Reminder" : t, x == null ? "" : x};
        for (String[] o : items) if (o[0].equals(it[0]) && o[1].equals(it[1])) return;
        items.add(it);
        boolean together = collideMode() == 1;
        boolean faded = content.getAlpha() < 0.5f;
        if (together || faded) { // all together, or it had already faded out: bring it up now
            shown = items.size() - 1;
            pTitle = it[0];
            pText = it[1];
            rebuild();
            content.animate().cancel();
            content.setAlpha(1f);
            scheduleRound();
        }
    }

    private void rebuild() {
        outer.removeViewAt(0);
        outer.addView(build(pTitle, pText), 0);
        content.setAlpha(1f);
    }

    private void fillSheet() {
        sheet.heading("Clock size on the peek");
        sheet.slider("Size", Peek.sizePct(this), v -> {
            Peek.setSizePct(this, v);
            rebuild();
        });
        sheet.heading("Font");
        sheet.fontChips(ClockFace.fontIndex(this), v -> {
            ClockFace.setFontIndex(this, v);
            rebuild();
        });
        sheet.heading("Which parts use it");
        sheet.toggle("Time", (ClockFace.fontParts(this) & ClockFace.PART_TIME) != 0, on -> part(ClockFace.PART_TIME, on));
        sheet.toggle("Date", (ClockFace.fontParts(this) & ClockFace.PART_DATE) != 0, on -> part(ClockFace.PART_DATE, on));
        sheet.toggle("Reminder text", (ClockFace.fontParts(this) & ClockFace.PART_TEXT) != 0, on -> part(ClockFace.PART_TEXT, on));
        sheet.heading("Date");
        sheet.toggle("Show the date", ClockFace.showDate(this), on -> {
            ClockFace.setShowDate(this, on);
            rebuild();
        });
        sheet.heading("Stays lit for");
        final int[] secOpts = {3, 5, 8, 12};
        int cur = 0;
        for (int i = 0; i < secOpts.length; i++) if (secOpts[i] == Peek.seconds(this)) cur = i;
        sheet.chips(new String[]{"3 s", "5 s", "8 s", "12 s"}, cur, k -> Peek.setSeconds(this, secOpts[k]));
    }

    private void part(int bit, boolean on) {
        int m = ClockFace.fontParts(this);
        ClockFace.setFontParts(this, on ? m | bit : m & ~bit);
        rebuild();
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (android.os.Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.BLACK));
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = 0.18f; // dim, like an always-on display
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        getWindow().setAttributes(lp);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);

        String title = getIntent().getStringExtra(Peek.EXTRA_TITLE);
        String text = getIntent().getStringExtra(Peek.EXTRA_TEXT);
        pTitle = title == null ? "Reminder" : title;
        pText = text == null ? "" : text;
        test = getIntent().getBooleanExtra(EXTRA_TEST, false);
        items.add(new String[]{pTitle, pText});
        String[] more = getIntent().getStringArrayExtra(EXTRA_PREVIEW);
        if (more != null) for (String m : more) items.add(new String[]{m, "Today, " + android.text.format.DateFormat.format("HH:mm", new java.util.Date())});
        outer = new FrameLayout(this);
        outer.setBackgroundColor(Color.BLACK);
        outer.addView(build(pTitle, pText));
        setContentView(outer);
        if (test) { // test mode: stays lit; the dot at the bottom opens the options, every change shows live
            sheet = TestSheet.attach(outer);
            fillSheet();
        }

        content.setAlpha(0f);
        content.animate().alpha(1f).setDuration(450).start();
        if (!test) scheduleRound();

        off = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) {
                finish();
            }
        };
        registerReceiver(off, new IntentFilter(Intent.ACTION_SCREEN_OFF));
    }

    /** The clock font chosen in Settings (any note font), else the thin system face. */
    private Typeface face(boolean bold, int part) {
        int idx = ClockFace.fontIndex(this);
        if (idx >= 0 && (ClockFace.fontParts(this) & part) != 0) {
            NoteFonts.init(this);
            Typeface f = NoteFonts.face(idx);
            if (f != null) return Typeface.create(f, bold ? Typeface.BOLD : Typeface.NORMAL);
        }
        return bold ? Typeface.DEFAULT_BOLD : Typeface.create("sans-serif-thin", Typeface.NORMAL);
    }

    private View build(String title, String text) {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        root.setOnClickListener(v -> {
            if (sheet != null && sheet.isOpen()) sheet.close(); else finish();
        });
        float d = getResources().getDisplayMetrics().density;
        float scale = Peek.sizePct(this) / 100f;

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        content = col;

        boolean h24 = android.text.format.DateFormat.is24HourFormat(this);
        Date now = new Date();
        TextView time = new TextView(this);
        time.setText(new SimpleDateFormat(h24 ? "HH:mm" : "h:mm", Locale.getDefault()).format(now));
        time.setTextSize(76 * scale);
        time.setTypeface(face(false, ClockFace.PART_TIME));
        time.setTextColor(0xE6FFFFFF);
        time.setIncludeFontPadding(false);
        time.setGravity(Gravity.CENTER);
        col.addView(time, new LinearLayout.LayoutParams(-2, -2));

        TextView date = new TextView(this);
        date.setText(new SimpleDateFormat("EEEE d MMMM", Locale.getDefault()).format(now));
        date.setTextSize(14 * Math.max(0.85f, scale));
        date.setTypeface(face(false, ClockFace.PART_DATE));
        date.setTextColor(0x99FFFFFF);
        date.setGravity(Gravity.CENTER);
        date.setPadding(0, Math.round(4 * d), 0, Math.round(30 * d));
        date.setVisibility(ClockFace.showDate(this) ? View.VISIBLE : View.GONE);
        col.addView(date, new LinearLayout.LayoutParams(-2, -2));

        // the reminder: plain centred lines, nothing boxed (all of them stacked when "all together" is chosen)
        java.util.List<String[]> view = new java.util.ArrayList<>();
        if (collideMode() == 1 && items.size() > 1 && !test) view.addAll(items); else view.add(new String[]{title, text});
        for (String[] one : view) {
            String title1 = one[0];
            String text1 = one[1];
        TextView t = new TextView(this);
        t.setText(title1);
        t.setTextSize(19);
        t.setTypeface(face(true, ClockFace.PART_TEXT));
        t.setTextColor(0xF0FFFFFF);
        t.setGravity(Gravity.CENTER);
        t.setMaxLines(3);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
        tl.leftMargin = Math.round(36 * d);
        tl.rightMargin = Math.round(36 * d);
        if (one != view.get(0)) tl.topMargin = Math.round(16 * d);
        col.addView(t, tl);
        if (!text1.isEmpty()) {
            TextView s2 = new TextView(this);
            s2.setText(text1);
            s2.setTextSize(13);
            s2.setTypeface(face(false, ClockFace.PART_TEXT));
            s2.setTextColor(0x8CFFFFFF);
            s2.setGravity(Gravity.CENTER);
            s2.setMaxLines(2);
            s2.setEllipsize(android.text.TextUtils.TruncateAt.END);
            s2.setPadding(0, Math.round(6 * d), 0, 0);
            LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-2, -2);
            sl.leftMargin = Math.round(36 * d);
            sl.rightMargin = Math.round(36 * d);
            col.addView(s2, sl);
        }

        }

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER);
        lp.bottomMargin = Math.round(40 * d); // a little above the middle
        root.addView(col, lp);
        return root; // no Fonts.apply: the clock font above is the only font here
    }

    @Override
    protected void onDestroy() {
        h.removeCallbacksAndMessages(null);
        if (off != null) {
            try {
                unregisterReceiver(off);
            } catch (RuntimeException ignored) {
                // already gone
            }
        }
        super.onDestroy();
    }
}
