package app.privatetodo;

import android.app.Activity;
import android.app.NotificationManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/** The full-screen water alert: the same look as the task alarm, with a progress bar and one-tap "I drank" buttons. */
public class WaterAlarmActivity extends Activity {
    private static final int BG = 0xFF0E1620;
    private static final int BLUE = 0xFF2F80ED;
    private static final int BLUE_LIGHT = 0xFF9CC9FF;

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
        show();
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        show();
    }

    private void show() {
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(BG);
        int mode = Prefs.alertAnim(this);
        Habit wh = WaterAlert.animation(this) ? WaterAlert.habit(this) : null;
        if (wh != null) {
            // the live water from the Drink water habit, rising to today's level behind everything
            View water = new View(this);
            float level = 0.12f + 0.88f * (wh.goal > 0
                    ? Math.min(1f, WaterAlert.todayAmount(this, wh) / (float) wh.goal) : 0f); // always some water to see
            water.setBackground(new WaterDrawable(density, 0, WaterDrawable.BLUE, wh.id + 2000000L, level));
            frame.addView(water, new FrameLayout.LayoutParams(-1, -1));
            if (mode != AlertFx.EDGE) mode = AlertFx.OFF;
        }
        View fx = AlertFx.create(this, mode);
        View content = build();
        if (wh != null) content.setBackgroundColor(Color.TRANSPARENT);
        if (fx != null && mode != AlertFx.EDGE) {
            frame.addView(fx, new FrameLayout.LayoutParams(-1, -1));
            content.setBackgroundColor(Color.TRANSPARENT);
        }
        frame.addView(content, new FrameLayout.LayoutParams(-1, -1));
        if (fx != null && mode == AlertFx.EDGE) frame.addView(fx, new FrameLayout.LayoutParams(-1, -1));
        setContentView(frame);
        Fonts.apply(getWindow().getDecorView());
    }

    /** A rounded bar that fills with blue as today's total climbs to the goal. */
    private final class Bar extends View {
        private final float frac;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();

        Bar(float frac) {
            super(WaterAlarmActivity.this);
            this.frac = Math.max(0f, Math.min(1f, frac));
        }

        @Override
        protected void onDraw(Canvas c) {
            float h = getHeight();
            r.set(0, 0, getWidth(), h);
            p.setColor(0xFF22313F);
            c.drawRoundRect(r, h / 2, h / 2, p);
            if (frac > 0) {
                r.set(0, 0, Math.max(h, getWidth() * frac), h);
                p.setColor(BLUE);
                c.drawRoundRect(r, h / 2, h / 2, p);
            }
        }
    }

    private View build() {
        final Habit h = WaterAlert.habit(this);
        final int amt = h == null ? 0 : WaterAlert.todayAmount(this, h);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(28), dp(72), dp(28), dp(40));

        TextView label = label("WATER", 13, BLUE_LIGHT, true);
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

        TextView title = label("Time to drink water 💧", 28, Color.WHITE, true);
        root.addView(title);

        if (h != null) {
            TextView sub = label(h.progress(amt) + " today", 15, 0xFFB7C2CF, false);
            sub.setPadding(0, dp(8), 0, dp(14));
            root.addView(sub);
            Bar bar = new Bar(h.goal > 0 ? amt / (float) h.goal : 0f);
            root.addView(bar, new LinearLayout.LayoutParams(-1, dp(12)));
        } else {
            TextView sub = label("Add a “Drink water” habit to keep count", 15, 0xFFB7C2CF, false);
            sub.setPadding(0, dp(8), 0, 0);
            root.addView(sub);
        }

        root.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1f));

        if (h != null) {
            List<Integer> q = h.quickAmounts();
            int main = q.isEmpty() ? h.step : q.get(0);
            TextView done = pill("I drank " + main + (h.unit.isEmpty() ? "" : " " + h.unit), BLUE);
            done.setTextSize(18);
            done.setOnClickListener(v -> drank(h, main));
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-1, dp(60));
            dlp.leftMargin = dp(4);
            dlp.rightMargin = dp(4);
            root.addView(done, dlp);
            LinearLayout row = new LinearLayout(this);
            for (int i = 1; i < q.size() && i < 3; i++) {
                final int a = q.get(i);
                TextView b = pill("+" + a + (h.unit.isEmpty() ? "" : " " + h.unit), 0xFF1D2C3B);
                b.setOnClickListener(v -> drank(h, a));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(48), 1f);
                lp.leftMargin = dp(4);
                lp.rightMargin = dp(4);
                row.addView(b, lp);
            }
            TextView custom = pill("Custom\u2026", 0xFF1D2C3B);
            custom.setOnClickListener(v -> askAmount(h));
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, dp(48), 1f);
            clp.leftMargin = dp(4);
            clp.rightMargin = dp(4);
            row.addView(custom, clp);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
            rlp.topMargin = dp(8);
            root.addView(row, rlp);
        }

        TextView snoozeLabel = label("Remind me again in", 13, 0xFFB7C2CF, true);
        snoozeLabel.setPadding(0, dp(20), 0, dp(8));
        root.addView(snoozeLabel);
        LinearLayout srow = new LinearLayout(this);
        int[] mins = {10, 20, 30, 60};
        String[] names = {"10 min", "20 min", "30 min", "1 hour"};
        for (int i = 0; i < mins.length; i++) {
            final int m = mins[i];
            final String nm = names[i];
            TextView b = pill(names[i], 0xFF1D2C3B);
            b.setOnClickListener(v -> {
                WaterAlert.snooze(this, m);
                Toast.makeText(this, "I'll remind you in " + nm, Toast.LENGTH_SHORT).show();
                close();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(52), 1f);
            lp.leftMargin = dp(4);
            lp.rightMargin = dp(4);
            srow.addView(b, lp);
        }
        root.addView(srow, new LinearLayout.LayoutParams(-1, -2));

        TextView dismiss = label("Dismiss", 16, 0xFFB7C2CF, true);
        dismiss.setMinHeight(dp(56));
        dismiss.setOnClickListener(v -> close());
        LinearLayout.LayoutParams xlp = new LinearLayout.LayoutParams(-1, -2);
        xlp.topMargin = dp(8);
        root.addView(dismiss, xlp);
        return root;
    }

    /** Any amount: a small box for the number, in the habit's own unit. */
    private void askAmount(final Habit h) {
        final android.widget.EditText et = new android.widget.EditText(this);
        et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        et.setSingleLine(true);
        et.setHint(h.unit.isEmpty() ? "Amount" : "Amount in " + h.unit);
        et.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(6)});
        LinearLayout box = new LinearLayout(this);
        box.setPadding(dp(24), dp(8), dp(24), 0);
        box.addView(et, new LinearLayout.LayoutParams(-1, -2));
        final android.app.AlertDialog dlg = new android.app.AlertDialog.Builder(this)
                .setTitle("How much did you drink?")
                .setView(box)
                .setPositiveButton("Add", null)
                .setNegativeButton("Cancel", null)
                .create();
        dlg.setOnShowListener(d -> {
            et.requestFocus();
            dlg.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
            dlg.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                int a;
                try {
                    a = Integer.parseInt(et.getText().toString().trim());
                } catch (NumberFormatException ex) {
                    a = 0;
                }
                if (a <= 0) {
                    et.setError("Enter a number");
                    return;
                }
                dlg.dismiss();
                drank(h, a);
            });
        });
        dlg.show();
    }

    private void drank(Habit h, int amount) {
        int total = WaterAlert.add(this, amount);
        if (h.goal > 0 && total >= h.goal) {
            Toast.makeText(this, "Goal reached 🎉", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "Logged +" + amount, Toast.LENGTH_SHORT).show();
        }
        close();
    }

    /** Stops the ringing, clears the notification and closes the screen. */
    private void close() {
        WaterRingService.stopRinging(this);
        getSystemService(NotificationManager.class).cancel(WaterAlert.NOTIF_ID);
        finish();
    }

    @Override
    public void onBackPressed() {
        close();
    }

    /** The upper volume button silences the ringing; the screen stays for the buttons. */
    @Override
    public boolean onKeyDown(int keyCode, android.view.KeyEvent event) {
        if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP && WaterRingService.isRinging()) {
            if (event.getRepeatCount() == 0) {
                stopService(new android.content.Intent(this, WaterRingService.class));
                Toast.makeText(this, "Silenced", Toast.LENGTH_SHORT).show();
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
