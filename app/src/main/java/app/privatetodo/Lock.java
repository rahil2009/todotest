package app.privatetodo;

import android.app.Activity;
import android.app.Application;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * App lock, using the phone's own fingerprint / face / screen lock (nothing of ours to remember or leak).
 * Screens call {@link #guard} from onResume; while locked they are covered and {@link UnlockActivity} asks the phone.
 * One unlock covers everything until the app has been in the background for the chosen time.
 */
final class Lock {
    private Lock() { }

    static final int NOTES = 1;
    static final int LENT = 2;
    static final int ALL = 4;
    /** Used by screens that must be locked whenever the lock is on at all (Settings, so it cannot be switched off). */
    static final int ANY = 8;

    static final int[] AFTER_MS = {0, 60_000, 300_000, 900_000};
    static final String[] AFTER_NAMES = {"Right away", "1 minute", "5 minutes", "15 minutes"};

    static boolean unlocked;   // this process, since the last successful unlock
    private static long leftAt; // when the last screen went to the background
    private static int started;
    private static boolean configChange;

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("applock", Context.MODE_PRIVATE);
    }

    static boolean enabled(Context c) {
        return sp(c).getBoolean("on", false);
    }

    static void setEnabled(Context c, boolean on) {
        sp(c).edit().putBoolean("on", on).apply();
        if (on) unlocked = true; // they are in Settings, so they just proved who they are
    }

    static int scope(Context c) {
        return sp(c).getInt("scope", NOTES | LENT);
    }

    static void setScope(Context c, int s) {
        sp(c).edit().putInt("scope", s).apply();
    }

    static int afterIndex(Context c) {
        return Math.max(0, Math.min(AFTER_MS.length - 1, sp(c).getInt("after", 0)));
    }

    static void setAfter(Context c, int i) {
        sp(c).edit().putInt("after", i).apply();
    }

    static boolean hideInRecents(Context c) {
        return sp(c).getBoolean("secure", true);
    }

    static void setHideInRecents(Context c, boolean v) {
        sp(c).edit().putBoolean("secure", v).apply();
    }

    static boolean deviceSecure(Context c) {
        KeyguardManager km = c.getSystemService(KeyguardManager.class);
        return km != null && km.isDeviceSecure();
    }

    private static boolean covers(Context c, int area) {
        if (!enabled(c)) return false;
        if (area == ANY) return true;
        int s = scope(c);
        return (s & ALL) != 0 || (s & area) != 0;
    }

    // ------------------------------------------------------------------ process-wide foreground tracking

    /** Registered once from {@link App}: relocks after the app has been away for longer than the chosen time. */
    static final class Watch implements Application.ActivityLifecycleCallbacks {
        @Override
        public void onActivityStarted(Activity a) {
            if (started++ == 0) {
                if (configChange) {
                    configChange = false; // a rotation or theme change is not "leaving the app"
                } else if (unlocked && System.currentTimeMillis() - leftAt >= AFTER_MS[afterIndex(a)]) {
                    unlocked = false;
                }
            }
        }

        @Override
        public void onActivityStopped(Activity a) {
            if (--started == 0) {
                if (a.isChangingConfigurations()) configChange = true;
                else leftAt = System.currentTimeMillis();
            }
        }

        @Override
        public void onActivityCreated(Activity a, Bundle b) { }

        @Override
        public void onActivityResumed(Activity a) { }

        @Override
        public void onActivityPaused(Activity a) { }

        @Override
        public void onActivitySaveInstanceState(Activity a, Bundle b) { }

        @Override
        public void onActivityDestroyed(Activity a) { }
    }

    // ------------------------------------------------------------------ the guard

    private static final int OVERLAY_TAG = 0x4C4F434B;

    /** Call from onResume of a screen that belongs to {@code area}. Covers it while the app is locked. */
    static void guard(final Activity a, int area) {
        boolean cover = covers(a, area);
        if (cover && hideInRecents(a)) {
            a.getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        } else {
            a.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }
        ViewGroup decor = (ViewGroup) a.getWindow().getDecorView();
        View old = decor.findViewWithTag(OVERLAY_TAG);
        if (!cover || unlocked || !deviceSecure(a)) {
            if (old != null) decor.removeView(old);
            return;
        }
        if (old == null) decor.addView(overlay(a), new ViewGroup.LayoutParams(-1, -1));
        UnlockActivity.show(a);
    }

    private static View overlay(final Activity a) {
        float d = a.getResources().getDisplayMetrics().density;
        LinearLayout box = new LinearLayout(a);
        box.setTag(OVERLAY_TAG);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setBackgroundColor(Pal.bg);
        box.setClickable(true); // swallow touches
        TextView ic = new TextView(a);
        ic.setText("🔒");
        ic.setTextSize(44);
        ic.setGravity(Gravity.CENTER);
        box.addView(ic);
        TextView t = new TextView(a);
        t.setText("Locked");
        t.setTextSize(24);
        t.setTextColor(Pal.ink);
        t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, Math.round(8 * d), 0, Math.round(18 * d));
        box.addView(t);
        TextView b = new TextView(a);
        b.setText("Unlock");
        b.setTextSize(16);
        b.setTextColor(Pal.onAccent);
        b.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        b.setGravity(Gravity.CENTER);
        b.setPadding(Math.round(28 * d), Math.round(13 * d), Math.round(28 * d), Math.round(13 * d));
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setColor(Pal.accent);
        g.setCornerRadius(100 * d);
        b.setBackground(g);
        b.setOnClickListener(v -> UnlockActivity.show(a));
        box.addView(b);
        return box;
    }
}
