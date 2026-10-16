package app.privatetodo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Re-arms every pending reminder after a reboot, app update, or clock/timezone change. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        Reminders.rescheduleAll(ctx);
    }
}
