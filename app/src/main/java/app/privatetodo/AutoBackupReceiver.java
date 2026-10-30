package app.privatetodo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Fired by AlarmManager around 3 am when daily backup is on. */
public class AutoBackupReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(final Context ctx, Intent intent) {
        final PendingResult pending = goAsync();
        new Thread(() -> {
            try {
                AutoBackup.run(ctx.getApplicationContext());
            } finally {
                AutoBackup.schedule(ctx.getApplicationContext()); // tomorrow night
                pending.finish();
            }
        }, "auto-backup").start();
    }
}
