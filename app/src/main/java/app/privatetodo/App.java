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
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity a, Bundle b) {
                noForceDark(a);
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
    static void noForceDark(Activity a) {
        if (android.os.Build.VERSION.SDK_INT >= 29 && a.getWindow() != null) {
            a.getWindow().getDecorView().setForceDarkAllowed(false);
        }
    }
}
