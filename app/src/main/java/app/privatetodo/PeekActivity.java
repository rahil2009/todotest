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

    private View build(String title, String text) {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        root.setOnClickListener(v -> finish());
        float d = getResources().getDisplayMetrics().density;

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        content = col;

        boolean h24 = android.text.format.DateFormat.is24HourFormat(this);
        Date now = new Date();
        TextView time = new TextView(this);
        time.setText(new SimpleDateFormat(h24 ? "HH:mm" : "h:mm", Locale.getDefault()).format(now));
        time.setTextSize(72);
        time.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        time.setTextColor(0xE6FFFFFF);
        time.setIncludeFontPadding(false);
        col.addView(time);

        TextView date = new TextView(this);
        date.setText(new SimpleDateFormat("EEEE d MMMM", Locale.getDefault()).format(now));
        date.setTextSize(14);
        date.setTextColor(0x99FFFFFF);
        date.setPadding(0, Math.round(2 * d), 0, Math.round(34 * d));
        col.addView(date);

        // the reminder: a soft outlined card
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        int p = Math.round(16 * d);
        card.setPadding(p, p, p, p);
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xFF0B0D0C);
        g.setCornerRadius(24 * d);
        g.setStroke(Math.max(1, Math.round(d)), 0xFF2A302D);
        card.setBackground(g);

        ImageView ic = new ImageView(this);
        ic.setImageResource(R.drawable.ic_notify);
        ic.setColorFilter(0xFF6FCDBD);
        GradientDrawable ig = new GradientDrawable();
        ig.setShape(GradientDrawable.OVAL);
        ig.setColor(0xFF16201D);
        ic.setBackground(ig);
        int ip = Math.round(9 * d);
        ic.setPadding(ip, ip, ip, ip);
        card.addView(ic, new LinearLayout.LayoutParams(Math.round(42 * d), Math.round(42 * d)));

        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.setPadding(Math.round(14 * d), 0, 0, 0);
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(17);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(0xF0FFFFFF);
        t.setMaxLines(3);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        words.addView(t);
        if (!text.isEmpty()) {
            TextView s = new TextView(this);
            s.setText(text);
            s.setTextSize(13);
            s.setTextColor(0x99FFFFFF);
            s.setMaxLines(2);
            s.setEllipsize(android.text.TextUtils.TruncateAt.END);
            s.setPadding(0, Math.round(2 * d), 0, 0);
            words.addView(s);
        }
        card.addView(words, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, -2);
        clp.leftMargin = Math.round(28 * d);
        clp.rightMargin = Math.round(28 * d);
        col.addView(card, clp);

        TextView hint = new TextView(this);
        hint.setText("Tap to close");
        hint.setTextSize(11);
        hint.setTextColor(0x55FFFFFF);
        hint.setPadding(0, Math.round(22 * d), 0, 0);
        col.addView(hint);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER);
        lp.bottomMargin = Math.round(60 * d); // a little above the middle
        root.addView(col, lp);
        Fonts.apply(root);
        time.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        return root;
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
