package app.privatetodo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.concurrent.atomic.AtomicBoolean;

/** Fired by AlarmManager when a "remind me to ask" time of the Lent section comes. */
public class LentReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        long id = intent.getLongExtra("id", -1);
        if (id < 0) return;
        final PendingResult pending = goAsync(); // stay alive while the sound plays
        final AtomicBoolean finished = new AtomicBoolean();
        final Runnable finish = () -> {
            if (finished.compareAndSet(false, true)) pending.finish();
        };
        try {
            LentRemind.fire(ctx, id, finish);
        } catch (RuntimeException e) {
            finish.run();
            throw e;
        }
    }
}
