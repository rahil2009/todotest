package app.privatetodo;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

/** The "remind me to ask" alarms of the Lent section. Local only: AlarmManager and one notification. */
final class LentRemind {
    private LentRemind() { }

    static final String ACTION_FIRE = "app.privatetodo.LENT_FIRE";
    private static final int NOTIF_BASE = 810000;

    /** When this loan should remind, or 0 for never (no reminder set, returned, or the moment has passed). */
    static long triggerAt(LentDb.Loan l) {
        if (l == null || l.closed || !l.remind || l.due <= 0) return 0;
        LocalDate day = LocalDate.ofEpochDay(l.due - Math.max(0, l.remindDays));
        long t = LocalDateTime.of(day, LocalTime.of(Math.max(0, Math.min(23, l.remindMin / 60)),
                Math.max(0, Math.min(59, l.remindMin % 60)))).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        return t > System.currentTimeMillis() + 2000 ? t : 0;
    }

    private static PendingIntent pending(Context ctx, long id) {
        Intent i = new Intent(ctx, LentReceiver.class)
                .setAction(ACTION_FIRE)
                .setData(Uri.parse("todo://lent/" + id))
                .putExtra("id", id);
        return PendingIntent.getBroadcast(ctx, 0, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    static void schedule(Context ctx, LentDb.Loan l) {
        if (l == null) return;
        AlarmManager am = ctx.getSystemService(AlarmManager.class);
        PendingIntent fire = pending(ctx, l.id);
        am.cancel(fire);
        long at = triggerAt(l);
        if (at == 0) return;
        try {
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, fire);
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, fire);
            }
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, fire);
        }
    }

    static void cancel(Context ctx, long id) {
        ctx.getSystemService(AlarmManager.class).cancel(pending(ctx, id));
        ctx.getSystemService(NotificationManager.class).cancel(NOTIF_BASE + (int) (id % 100000));
    }

    static void scheduleAll(Context ctx) {
        LentDb db = new LentDb(ctx);
        try {
            List<LentDb.Loan> all = db.all();
            for (LentDb.Loan l : all) schedule(ctx, l);
        } catch (RuntimeException ignored) {
            // no Lent database yet: nothing to arm
        } finally {
            db.close();
        }
    }

    /** What the reminder says: "Rahul owes you 8,000". */
    static String headline(Context ctx, LentDb.Loan l) {
        String what = l.money() ? LentUi.money(ctx, l.left()) : (l.title.isEmpty() ? "your thing" : l.title);
        if (l.dir == 0) {
            return l.money() ? l.person + " owes you " + what : l.person + " has your " + what;
        }
        return l.money() ? "You owe " + l.person + " " + what : "Return " + what + " to " + l.person;
    }

    static void fire(Context ctx, long id, Runnable done) {
        LentDb db = new LentDb(ctx);
        try {
            LentDb.Loan l = db.get(id);
            if (l == null || l.closed) {
                if (done != null) done.run();
                return;
            }
            Reminders.ensureChannels(ctx);
            Intent open = new Intent(ctx, LentPersonActivity.class)
                    .putExtra("person", l.person).putExtra("dir", l.dir)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent tap = PendingIntent.getActivity(ctx, (int) (id % 100000), open,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            String when = l.due == LentDb.today() ? "Due today" : LentUi.dueLabel(l.due);
            Notification n = new Notification.Builder(ctx, Reminders.CH_REMIND)
                    .setSmallIcon(R.drawable.ic_notify)
                    .setContentTitle(headline(ctx, l))
                    .setContentText(when + (l.note.isEmpty() ? "" : "  ·  " + l.note))
                    .setContentIntent(tap)
                    .setAutoCancel(true)
                    .setVisibility(Notification.VISIBILITY_PUBLIC)
                    .build();
            ctx.getSystemService(NotificationManager.class).notify(NOTIF_BASE + (int) (id % 100000), n);
            Sound.play(ctx, Prefs.NOTIF, false, 6_000L, done);
        } finally {
            db.close();
        }
    }
}
