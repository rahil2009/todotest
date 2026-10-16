package app.privatetodo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.concurrent.atomic.AtomicBoolean;

/** Fired by AlarmManager at the reminder time. */
public class ReminderReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        long id = intent.getLongExtra(Reminders.EXTRA_ID, -1);
        if (id < 0) return;

        // Stay alive while a normal reminder's sound plays (it is capped at a few seconds).
        final PendingResult pending = goAsync();
        final AtomicBoolean finished = new AtomicBoolean();
        final Runnable finish = () -> {
            if (finished.compareAndSet(false, true)) pending.finish();
        };

        TaskDb db = new TaskDb(ctx);
        try {
            Task t = db.get(id);
            if (t == null || t.done) {
                finish.run();
                return;
            }

            Reminders.notifyTask(ctx, t, db.subs(id), finish);

            boolean fromSnooze = t.snooze > 0;
            long due = t.due;
            boolean fired = t.fired;
            if (!fromSnooze) {
                if (t.repeat != 0) {
                    due = Reminders.advance(t.due, t.repeat, t.repeatUnit, System.currentTimeMillis());
                } else {
                    fired = true;
                }
            }
            db.setState(id, due, fired, 0);
            Reminders.schedule(ctx, db.get(id)); // arm the next occurrence, if any
        } catch (RuntimeException e) {
            finish.run();
            throw e;
        } finally {
            db.close();
        }
    }
}
