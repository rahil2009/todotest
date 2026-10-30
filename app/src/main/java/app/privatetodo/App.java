package app.privatetodo;

import android.app.Application;

/** Only here to watch when the app goes to the background, for the app lock. */
public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(new Lock.Watch());
    }
}
