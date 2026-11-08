package app.privatetodo;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

/** Watches when the app goes to the background (app lock), and keeps the phone from re-colouring our screens. */
public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(new Lock.Watch());
        // A crash is written to a small file on this phone only (Settings shows it, you can copy it and send it to me).
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try {
                java.io.StringWriter sw = new java.io.StringWriter();
                e.printStackTrace(new java.io.PrintWriter(sw));
                String txt = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(new java.util.Date())
                        + "  Android " + android.os.Build.VERSION.RELEASE + " (API " + android.os.Build.VERSION.SDK_INT + ")\n"
                        + "Thread " + t.getName() + "\n" + sw;
                java.io.FileOutputStream fo = openFileOutput("last_crash.txt", MODE_PRIVATE);
                fo.write(txt.getBytes("UTF-8"));
                fo.close();
            } catch (Throwable ignored) {
                // never make a crash worse
            }
            if (prev != null) prev.uncaughtException(t, e);
        });
        Pal.applyNight(this); // keep the system's idea of our light / dark mode in step with Settings
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity a, Bundle b) {
                noForceDark(a);
                fastScreen(a);
            }

            @Override
            public void onActivityStarted(Activity a) {
                noForceDark(a);
            }

            @Override
            public void onActivityResumed(Activity a) {
            }

            @Override
            public void onActivityPaused(Activity a) {
            }

            @Override
            public void onActivityStopped(Activity a) {
            }

            @Override
            public void onActivitySaveInstanceState(Activity a, Bundle b) {
            }

            @Override
            public void onActivityDestroyed(Activity a) {
            }
        });
    }

    /**
     * The app paints its own light and dark themes. When the phone is in dark mode, Android's "force dark"
     * (and the newer "make more apps dark" option) can darken a light screen on its own; this switches that off
     * for every window of the app, so Settings > Appearance > Light really stays light.
     */
    /**
     * Asks for the screen's fastest refresh rate (120 Hz where the phone has it) while the app is on screen, so
     * scrolling is not held to 60. The phone still drops the rate when nothing moves.
     */
    static void fastScreen(Activity a) {
        try {
            if (a instanceof AlarmActivity || a instanceof PeekActivity) return;
            android.view.Display disp = a.getWindowManager().getDefaultDisplay();
            float best = 0f;
            for (android.view.Display.Mode m : disp.getSupportedModes()) best = Math.max(best, m.getRefreshRate());
            if (best > 61f) {
                android.view.WindowManager.LayoutParams lp = a.getWindow().getAttributes();
                lp.preferredRefreshRate = best;
                a.getWindow().setAttributes(lp);
            }
        } catch (RuntimeException ignored) {
            // some phones refuse; scrolling just stays at the default rate
        }
    }

    static void noForceDark(Activity a) {
        if (android.os.Build.VERSION.SDK_INT >= 29 && a.getWindow() != null) {
            a.getWindow().getDecorView().setForceDarkAllowed(false);
        }
    }
}
