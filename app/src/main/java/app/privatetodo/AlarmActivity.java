package app.privatetodo;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** Full-screen, lock-screen-capable alarm shown when an alarm-style reminder rings. */
public class AlarmActivity extends Activity {
    private static final int BG = 0xFF111513;
    private static final int ACCENT = 0xFF0B6B5D;

    private long id;
    private float density;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Pal.init(this);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        density = getResources().getDisplayMetrics().density;
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        id = getIntent().getLongExtra(Reminders.EXTRA_ID, -1);
        show();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        id = intent.getLongExtra(Reminders.EXTRA_ID, -1);
        show();
    }

    private void show() {
        TaskDb db = new TaskDb(this);
        Task t;
        List<Sub> subs = new ArrayList<>();
        try {
            t = db.get(id);
            if (t != null) subs = db.subs(id);
        } finally {
            db.close();
        }
        if (t == null || t.done) {
            finish();
            return;
        }
        android.widget.FrameLayout frame = new android.widget.FrameLayout(this);
        frame.setBackgroundColor(BG);
        int mode = Prefs.alertAnim(this);
        View fx = AlertFx.create(this, mode);
        View content = build(t, subs);
        if (fx != null && mode != AlertFx.EDGE) { // backdrop styles sit behind the content
            frame.addView(fx, new android.widget.FrameLayout.LayoutParams(-1, -1));
            content.setBackgroundColor(Color.TRANSPARENT);
        }
        frame.addView(content, new android.widget.FrameLayout.LayoutParams(-1, -1));
        if (fx != null && mode == AlertFx.EDGE) { // the edge glow frames everything, on top
            frame.addView(fx, new android.widget.FrameLayout.LayoutParams(-1, -1));
        }
        setContentView(frame);
        Fonts.apply(getWindow().getDecorView());
    }

    private View build(Task t, List<Sub> subs) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(28), dp(72), dp(28), dp(40));

        TextView label = label("REMINDER", 13, 0xFF8FD6C8, true);
        label.setLetterSpacing(0.18f);
        root.addView(label);

        TextClock clock = new TextClock(this);
        clock.setFormat12Hour("h:mm");
        clock.setFormat24Hour("HH:mm");
        clock.setTextSize(72);
        clock.setTextColor(Color.WHITE);
        clock.setGravity(Gravity.CENTER);
        clock.setPadding(0, dp(8), 0, dp(24));
        root.addView(clock);

        TextView title = label(t.title, 28, Color.WHITE, true);
        title.setGravity(Gravity.CENTER);
        Links.linkify(title, 0xFF8FD6C8, this::linkOpened); // opening a link finishes the reminder
        root.addView(title);

        long when = t.snooze > 0 ? t.snooze : t.due;
        TextView sub = label(Reminders.when(this, when), 15, 0xFFB7C2BF, false);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, dp(8), 0, 0);
        root.addView(sub);

        if (!subs.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (Sub x : subs) {
                if (sb.length() > 0) sb.append('\n');
                sb.append(x.done ? "\u2713 " : "\u2022 ").append(x.title);
            }
            TextView list = label(sb.toString(), 16, 0xFFE3EAE8, false);
            list.setGravity(Gravity.START);
            list.setPadding(0, dp(16), 0, 0);
            Links.linkify(list, 0xFF8FD6C8, this::linkOpened);
            root.addView(list);
        }

        View spacer = new View(this);
        root.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1f));

        TextView snoozeLabel = label("Snooze for", 13, 0xFFB7C2BF, true);
        snoozeLabel.setPadding(0, 0, 0, dp(8));
        root.addView(snoozeLabel);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        int[] mins = {5, 10, 30, 60};
        String[] names = {"5 min", "10 min", "30 min", "1 hour"};
        for (int i = 0; i < mins.length; i++) {
            final int m = mins[i];
            TextView b = pill(names[i], 0xFF22302D);
            b.setOnClickListener(v -> {
                Reminders.snooze(this, id, m);
                Toast.makeText(this, "Snoozed", Toast.LENGTH_SHORT).show();
                finish();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(52), 1f);
            lp.leftMargin = dp(4);
            lp.rightMargin = dp(4);
            row.addView(b, lp);
        }
        root.addView(row, new LinearLayout.LayoutParams(-1, -2));

        // Custom snooze: swipe up/down on the left dial for the amount, on the right dial for the unit.
        final LinearLayout panel = customPanel();
        panel.setVisibility(View.GONE);
        final TextView customBtn = pill("Custom time…", 0xFF22302D);
        customBtn.setOnClickListener(v -> {
            boolean open = panel.getVisibility() != View.VISIBLE;
            panel.setVisibility(open ? View.VISIBLE : View.GONE);
            clock.setVisibility(open ? View.GONE : View.VISIBLE); // make room on small screens
        });
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, dp(48));
        clp.topMargin = dp(8);
        clp.leftMargin = dp(4);
        clp.rightMargin = dp(4);
        root.addView(customBtn, clp);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-1, -2);
        plp.topMargin = dp(8);
        plp.leftMargin = dp(4);
        plp.rightMargin = dp(4);
        root.addView(panel, plp);

        TextView done = pill("Done", ACCENT);
        done.setTextSize(18);
        done.setOnClickListener(v -> {
            Reminders.complete(this, id);
            finish();
        });
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-1, dp(60));
        dlp.topMargin = dp(16);
        dlp.leftMargin = dp(4);
        dlp.rightMargin = dp(4);
        root.addView(done, dlp);

        TextView dismiss = label("Dismiss", 16, 0xFFB7C2BF, true);
        dismiss.setGravity(Gravity.CENTER);
        dismiss.setMinHeight(dp(56));
        dismiss.setOnClickListener(v -> dismiss());
        root.addView(dismiss, new LinearLayout.LayoutParams(-1, -2));
        return root;
    }

    /** Shared swipe dials (see DialView) plus a confirm button. */
    private LinearLayout customPanel() {
        final DialView dials = new DialView(this, false);
        dials.setRelative(15, 0);

        final TextView go = pill("", ACCENT);
        go.setTextSize(16);
        final Runnable relabel = () -> go.setText("Snooze for " + dials.relativeLabel());
        relabel.run();
        dials.setOnChange(relabel);
        go.setOnClickListener(v -> {
            int minutes = dials.selectedMinutes();
            Reminders.snooze(this, id, minutes);
            Toast.makeText(this, "Snoozed until "
                    + Reminders.when(this, System.currentTimeMillis() + minutes * 60_000L),
                    Toast.LENGTH_LONG).show();
            finish();
        });

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.addView(dials, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(-1, dp(52));
        glp.topMargin = dp(8);
        panel.addView(go, glp);
        return panel;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // the Settings test button makes a throw-away task; remove it once its alert is closed
        if (isFinishing() && id >= 0 && id == Prefs.testId(this)) {
            Prefs.setTestId(this, -1);
            Reminders.cancel(this, id);
            Reminders.cancelNotification(this, id);
            RingService.stopFor(this, id);
            TaskDb db = new TaskDb(this);
            try {
                db.delete(id);
            } finally {
                db.close();
            }
        }
    }

    /** A link was opened from the alarm: the reminder has done its job, so silence it and mark it done. */
    private void linkOpened() {
        Reminders.complete(this, id);
        finish();
    }

    /** Stops the ringing but leaves the task as it is. */
    private void dismiss() {
        Reminders.cancelNotification(this, id);
        finish();
    }

    @Override
    public void onBackPressed() {
        dismiss();
    }

    /** The upper volume button silences the ringing (sound and vibration); the screen stays for Done / Snooze. */
    @Override
    public boolean onKeyDown(int keyCode, android.view.KeyEvent event) {
        if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP && RingService.isRinging()) {
            if (event.getRepeatCount() == 0) {
                RingService.stopFor(this, id);
                android.widget.Toast.makeText(this, "Silenced", android.widget.Toast.LENGTH_SHORT).show();
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, android.view.KeyEvent event) {
        if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP) return true;
        return super.onKeyUp(keyCode, event);
    }

    private TextView label(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private TextView pill(String s, int color) {
        TextView t = label(s, 15, Color.WHITE, true);
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(30));
        t.setBackground(g);
        t.setClickable(true);
        return t;
    }

    private int dp(int v) {
        return Math.round(v * density);
    }
}
