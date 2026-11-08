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
        setContentView(build(title == null ? "Reminder" : title, text == null ? "" : text));

        content.setAlpha(0f);
        content.animate().alpha(1f).setDuration(450).start();
        final int secs = Math.max(2, Peek.seconds(this));
        h.postDelayed(() -> { // fade to black, keep the screen from being held awake
            content.animate().alpha(0f).setDuration(900).start();
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }, secs * 1000L);
        h.postDelayed(this::finish, (secs + 25) * 1000L); // never lingers if the screen never turns off

        off = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) {
                finish();
            }
        };
        registerReceiver(off, new IntentFilter(Intent.ACTION_SCREEN_OFF));
    }

    /** The clock font chosen in Settings (any note font), else the thin system face. */
    private Typeface face(boolean bold) {
        int idx = ClockFace.fontIndex(this);
        if (idx >= 0) {
            NoteFonts.init(this);
            Typeface f = NoteFonts.face(idx);
            if (f != null) return Typeface.create(f, bold ? Typeface.BOLD : Typeface.NORMAL);
        }
        return bold ? Typeface.DEFAULT_BOLD : Typeface.create("sans-serif-thin", Typeface.NORMAL);
    }

    private View build(String title, String text) {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        root.setOnClickListener(v -> finish());
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
        time.setTypeface(face(false));
        time.setTextColor(0xE6FFFFFF);
        time.setIncludeFontPadding(false);
        time.setGravity(Gravity.CENTER);
        col.addView(time, new LinearLayout.LayoutParams(-2, -2));

        TextView date = new TextView(this);
        date.setText(new SimpleDateFormat("EEEE d MMMM", Locale.getDefault()).format(now));
        date.setTextSize(14 * Math.max(0.85f, scale));
        date.setTypeface(face(false));
        date.setTextColor(0x99FFFFFF);
        date.setGravity(Gravity.CENTER);
        date.setPadding(0, Math.round(4 * d), 0, Math.round(30 * d));
        col.addView(date, new LinearLayout.LayoutParams(-2, -2));

        // the reminder: plain centred lines, nothing boxed
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(19);
        t.setTypeface(face(true));
        t.setTextColor(0xF0FFFFFF);
        t.setGravity(Gravity.CENTER);
        t.setMaxLines(3);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
        tl.leftMargin = Math.round(36 * d);
        tl.rightMargin = Math.round(36 * d);
        col.addView(t, tl);
        if (!text.isEmpty()) {
            TextView s2 = new TextView(this);
            s2.setText(text);
            s2.setTextSize(13);
            s2.setTypeface(face(false));
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
