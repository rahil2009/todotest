package app.privatetodo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Fires when a focus or break period ends while the app is in the background. */
public class PomodoroReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        Pomo.finishPhase(ctx);
    }
}
