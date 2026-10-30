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

/** Reminders for countdowns: one alarm per event, always armed for the next reminder that is still ahead. */
final class CountdownRemind {
    private CountdownRemind() { }

    static final String ACTION_FIRE = "app.privatetodo.COUNTDOWN_FIRE";
    private static final int NOTIF_BASE = 830000;

    private static long at(long day, int minutes) {
        LocalDate d = LocalDate.ofEpochDay(day);
        return LocalDateTime.of(d, LocalTime.of(Math.max(0, Math.min(23, minutes / 60)), Math.max(0, Math.min(59, minutes % 60))))
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    /** The next moment this event should remind, or 0 when nothing is left to remind about. */
    static long next(CountdownDb.Item i) {
        if (i == null || i.mask == 0) return 0;
        long now = System.currentTimeMillis() + 2000;
        long best = 0;
        for (int k = 0; k < CountdownDb.BITS.length; k++) {
            if ((i.mask & CountdownDb.BITS[k]) == 0) continue;
            long t = at(i.day - CountdownDb.OFFSETS[k], i.remindMin);
            if (t > now && (best == 0 || t < best)) best = t;
        }
        return best;
    }

    private static PendingIntent pending(Context ctx, long id) {
        Intent i = new Intent(ctx, CountdownReceiver.class)
                .setAction(ACTION_FIRE)
                .setData(Uri.parse("todo://countdown/" + id))
                .putExtra("id", id);
        return PendingIntent.getBroadcast(ctx, 0, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    static void schedule(Context ctx, CountdownDb.Item i) {
        if (i == null) return;
        AlarmManager am = ctx.getSystemService(AlarmManager.class);
        PendingIntent fire = pending(ctx, i.id);
        am.cancel(fire);
        long t = next(i);
        if (t == 0) return;
        try {
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, fire);
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, fire);
            }
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, fire);
        }
    }

    static void cancel(Context ctx, long id) {
        ctx.getSystemService(AlarmManager.class).cancel(pending(ctx, id));
        ctx.getSystemService(NotificationManager.class).cancel(NOTIF_BASE + (int) (id % 100000));
    }

    static void scheduleAll(Context ctx) {
        CountdownDb db = new CountdownDb(ctx);
        try {
            List<CountdownDb.Item> all = db.all();
            for (CountdownDb.Item i : all) schedule(ctx, i);
        } catch (RuntimeException ignored) {
            // no countdown database yet: nothing to arm
        } finally {
            db.close();
        }
    }

    /** "Exam is today", "Trip in 3 days". */
    static String headline(CountdownDb.Item i) {
        long n = i.daysLeft();
        String name = (i.emoji.isEmpty() ? "" : i.emoji + " ") + i.title;
        if (n <= 0) return name + " is today";
        if (n == 1) return name + " is tomorrow";
        return name + " in " + n + " days";
    }

    static void fire(Context ctx, long id, Runnable done) {
        CountdownDb db = new CountdownDb(ctx);
        try {
            CountdownDb.Item i = db.get(id);
            if (i == null) {
                if (done != null) done.run();
                return;
            }
            Reminders.ensureChannels(ctx);
            Intent open = new Intent(ctx, CountdownActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent tap = PendingIntent.getActivity(ctx, (int) (id % 100000), open,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            String when = LentUi.date(i.day) + (i.minutes >= 0 ? "  ·  " + LentUi.clock(ctx, i.minutes) : "");
            Notification n = new Notification.Builder(ctx, Reminders.CH_REMIND)
                    .setSmallIcon(R.drawable.ic_notify)
                    .setContentTitle(headline(i))
                    .setContentText(when + (i.note.isEmpty() ? "" : "  ·  " + i.note))
                    .setContentIntent(tap)
                    .setAutoCancel(true)
                    .setCategory(Notification.CATEGORY_REMINDER)
                    .setVisibility(Notification.VISIBILITY_PUBLIC) // readable on the lock screen
                    .build();
            ctx.getSystemService(NotificationManager.class).notify(NOTIF_BASE + (int) (id % 100000), n);
            Sound.play(ctx, Prefs.NOTIF, false, 6_000L, done);
            schedule(ctx, i); // arm the next one
        } finally {
            db.close();
        }
    }
}
