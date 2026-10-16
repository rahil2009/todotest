package app.privatetodo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Handles the Done / Snooze buttons on the notification. */
public class ActionReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        long id = intent.getLongExtra(Reminders.EXTRA_ID, -1);
        if (id < 0 || intent.getAction() == null) return;
        switch (intent.getAction()) {
            case Reminders.ACTION_DONE:
                Reminders.complete(ctx, id);
                break;
            case Reminders.ACTION_SNOOZE:
                Reminders.snooze(ctx, id,
                        intent.getIntExtra(Reminders.EXTRA_MIN, Reminders.DEFAULT_SNOOZE_MIN));
                break;
            default:
                break;
        }
    }
}
