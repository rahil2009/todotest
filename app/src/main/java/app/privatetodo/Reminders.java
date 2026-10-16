package app.privatetodo;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.text.format.DateFormat;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Everything about scheduling and showing reminders. Fully offline: AlarmManager + notifications. */
final class Reminders {
    // v2: the channels are silent now. The app plays your chosen sound itself (see Sound), which is
    // what makes custom sounds and a volume slider possible. v1 channels are deleted.
    static final String CH_REMIND = "reminders_v2";
    static final String CH_ALARM = "alarms_v2";

    static final String ACTION_FIRE = "app.privatetodo.FIRE";
    static final String ACTION_DONE = "app.privatetodo.DONE";
    static final String ACTION_SNOOZE = "app.privatetodo.SNOOZE";
    static final String EXTRA_ID = "id";
    static final String EXTRA_MIN = "min";

    static final int DEFAULT_SNOOZE_MIN = 10;
    private static final long GRACE = 3000;

    private Reminders() {
    }

    // ------------------------------------------------------------ timing

    /** Next moment this task should ring, or 0 for none. */
    static long trigger(Task t, long now) {
        if (t.done) return 0;
        if (t.snooze > 0) return Math.max(t.snooze, now + GRACE);
        if (t.due > 0 && !t.fired) return Math.max(t.due, now + GRACE);
        return 0;
    }

    /**
     * First occurrence of "every n units" after {@code now}, always counted from the original
     * time (so "monthly on the 31st" does not drift). unit: 0 min, 1 hour, 2 day, 3 week, 4 month.
     */
    static long advance(long due, int n, int unit, long now) {
        if (n < 1) return due;
        if (unit <= 1) { // minutes and hours are fixed-length
            long step = n * (unit == 0 ? 60_000L : 3_600_000L);
            long k = Math.max(1, (now - due) / step + 1);
            return due + k * step;
        }
        Calendar base = Calendar.getInstance();
        base.setTimeInMillis(due);
        for (int k = 1; k < 100_000; k++) {
            Calendar c = (Calendar) base.clone();
            if (unit == 2) c.add(Calendar.DAY_OF_YEAR, k * n);
            else if (unit == 3) c.add(Calendar.DAY_OF_YEAR, k * n * 7);
            else c.add(Calendar.MONTH, k * n);
            if (c.getTimeInMillis() > now) return c.getTimeInMillis();
        }
        return due + 86_400_000L;
    }

    /** "Every minute", "Every 30 min", "Every 2 days", "Every month"... */
    static String repeatLabel(int n, int unit) {
        String[] one = {"minute", "hour", "day", "week", "month"};
        String[] many = {"min", "hours", "days", "weeks", "months"};
        int u = Math.max(0, Math.min(4, unit));
        return n <= 1 ? "Every " + one[u] : "Every " + n + " " + many[u];
    }

    // --------------------------------------------------------- scheduling

    static void schedule(Context ctx, Task t) {
        if (t == null) return;
        AlarmManager am = ctx.getSystemService(AlarmManager.class);
        PendingIntent fire = broadcast(ctx, ReminderReceiver.class, ACTION_FIRE, t.id, 0);
        am.cancel(fire);
        long at = trigger(t, System.currentTimeMillis());
        if (at == 0) return;
        try {
            if (t.alarm) {
                // Alarm-clock alarms are exact, survive Doze and need no extra permission.
                PendingIntent show = PendingIntent.getActivity(ctx, (int) t.id,
                        new Intent(ctx, MainActivity.class),
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
                am.setAlarmClock(new AlarmManager.AlarmClockInfo(at, show), fire);
            } else if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, fire);
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, fire);
            }
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, fire);
        }
    }

    static void cancel(Context ctx, long id) {
        AlarmManager am = ctx.getSystemService(AlarmManager.class);
        am.cancel(broadcast(ctx, ReminderReceiver.class, ACTION_FIRE, id, 0));
        cancelNotification(ctx, id);
    }

    static void rescheduleAll(Context ctx) {
        TaskDb db = new TaskDb(ctx);
        try {
            List<Task> list = db.withReminders();
            for (Task t : list) schedule(ctx, t);
        } finally {
            db.close();
        }
    }

    // -------------------------------------------------- user actions

    static void snooze(Context ctx, long id, int minutes) {
        TaskDb db = new TaskDb(ctx);
        try {
            if (db.get(id) == null) return;
            db.setSnooze(id, System.currentTimeMillis() + minutes * 60_000L);
            cancelNotification(ctx, id);
            schedule(ctx, db.get(id));
        } finally {
            db.close();
        }
    }

    /**
     * One-off reminders are marked done. Repeating ones stay active and simply wait for the
     * next occurrence, so "Done" never kills a daily/weekly series.
     */
    static void complete(Context ctx, long id) {
        TaskDb db = new TaskDb(ctx);
        try {
            Task t = db.get(id);
            cancelNotification(ctx, id);
            if (t == null) return;
            if (t.repeat == 0) {
                db.setDone(id, true);
                cancel(ctx, id);
            } else {
                db.setSnooze(id, 0);
                schedule(ctx, db.get(id));
            }
        } finally {
            db.close();
        }
    }

    static void cancelNotification(Context ctx, long id) {
        RingService.stopFor(ctx, id); // also ends the looping alarm sound
        ctx.getSystemService(NotificationManager.class).cancel((int) id);
    }

    // ------------------------------------------------------ notification

    static void ensureChannels(Context ctx) {
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        nm.deleteNotificationChannel("reminders_v1");
        nm.deleteNotificationChannel("alarms_v1");

        NotificationChannel remind = new NotificationChannel(
                CH_REMIND, "Reminders", NotificationManager.IMPORTANCE_HIGH);
        remind.setDescription("Task reminders. The sound is chosen in the app's Settings.");
        remind.setSound(null, null);
        remind.enableVibration(true);
        remind.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);

        NotificationChannel alarm = new NotificationChannel(
                CH_ALARM, "Alarm reminders", NotificationManager.IMPORTANCE_HIGH);
        alarm.setDescription("Full-screen alarm-style reminders that ring until dismissed");
        alarm.setSound(null, null);
        alarm.enableVibration(true);
        alarm.setVibrationPattern(new long[]{0, 600, 400, 600, 400, 600});
        alarm.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);

        nm.createNotificationChannels(Arrays.asList(remind, alarm));
    }

    /**
     * Shows the reminder and plays its sound. Normal reminders play once; alarm-style ones start
     * {@link RingService}, which loops the sound until you handle it. {@code done} runs exactly once
     * when nothing more will happen on this call's behalf (it keeps a broadcast alive while a
     * normal reminder's sound plays).
     */
    static void notifyTask(Context ctx, Task t, List<Sub> subs, Runnable done) {
        ensureChannels(ctx);
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        if (t.alarm) {
            try {
                RingService.start(ctx, t.id); // posts the notification itself
            } catch (RuntimeException e) {
                // The system refused a foreground service: still show it, and ring for a minute.
                nm.notify((int) t.id, build(ctx, t, subs));
                Sound.play(ctx, Prefs.ALARM, true, 60_000L, null, t.sound, t.volume);
            }
            if (done != null) done.run();
        } else {
            nm.notify((int) t.id, build(ctx, t, subs));
            Sound.play(ctx, Prefs.NOTIF, false, 9_000L, done, t.sound, t.volume);
        }
    }

    static Notification build(Context ctx, Task t, List<Sub> subs) {
        ensureChannels(ctx);

        PendingIntent open = PendingIntent.getActivity(ctx, (int) t.id,
                new Intent(ctx, MainActivity.class).setFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.Builder b = new Notification.Builder(ctx, t.alarm ? CH_ALARM : CH_REMIND)
                .setSmallIcon(R.drawable.ic_notify)
                .setContentTitle(t.title)
                .setContentText(when(ctx, t.snooze > 0 ? t.snooze : t.due))
                .setContentIntent(open)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setShowWhen(true)
                .setWhen(System.currentTimeMillis());

        Icon icon = Icon.createWithResource(ctx, R.drawable.ic_notify);
        b.addAction(new Notification.Action.Builder(icon, "Done",
                broadcast(ctx, ActionReceiver.class, ACTION_DONE, t.id, 0)).build());
        b.addAction(new Notification.Action.Builder(icon, "Snooze " + DEFAULT_SNOOZE_MIN + " min",
                broadcast(ctx, ActionReceiver.class, ACTION_SNOOZE, t.id, DEFAULT_SNOOZE_MIN)).build());

        // Expanded text: time + checklist. Links can't be inline in a notification, so the first one
        // gets an "Open link" button; every link is tappable inside the app.
        String[] texts = new String[subs.size() + 1];
        texts[0] = t.title;
        StringBuilder body = new StringBuilder(when(ctx, t.snooze > 0 ? t.snooze : t.due));
        for (int i = 0; i < subs.size(); i++) {
            Sub s = subs.get(i);
            texts[i + 1] = s.title;
            body.append('\n').append(s.done ? "✓ " : "• ").append(s.title);
        }
        b.setStyle(new Notification.BigTextStyle().bigText(body));
        List<String> links = Links.find(texts);
        if (!links.isEmpty()) {
            PendingIntent openLink = PendingIntent.getActivity(ctx, (int) t.id + 2_000_000,
                    new Intent(Intent.ACTION_VIEW, Uri.parse(Links.normalize(links.get(0))))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            b.addAction(new Notification.Action.Builder(icon,
                    links.size() > 1 ? "Open link (1/" + links.size() + ")" : "Open link",
                    openLink).build());
        }

        if (t.alarm) {
            PendingIntent full = PendingIntent.getActivity(ctx, (int) t.id + 1_000_000,
                    new Intent(ctx, AlarmActivity.class)
                            .putExtra(EXTRA_ID, t.id)
                            .setData(Uri.parse("todo://alarm/" + t.id))
                            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                    | Intent.FLAG_ACTIVITY_NO_USER_ACTION),
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            b.setCategory(Notification.CATEGORY_ALARM)
                    .setFullScreenIntent(full, true)
                    .setAutoCancel(false)
                    .setTimeoutAfter(10 * 60 * 1000L);
        } else {
            b.setCategory(Notification.CATEGORY_REMINDER).setAutoCancel(true);
        }

        Notification n = b.build();
        // The sound is played by the app (Sound / RingService); INSISTENT keeps the vibration repeating.
        if (t.alarm) n.flags |= Notification.FLAG_INSISTENT;
        return n;
    }

    // ----------------------------------------------------------- helpers

    static PendingIntent broadcast(Context ctx, Class<?> target, String action, long id, int min) {
        Intent i = new Intent(ctx, target)
                .setAction(action)
                .setData(Uri.parse("todo://task/" + id + "/" + action + "/" + min))
                .putExtra(EXTRA_ID, id)
                .putExtra(EXTRA_MIN, min);
        return PendingIntent.getBroadcast(ctx, 0, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /** "Today, 18:30" / "Tomorrow, 09:00" / "Mon 20 Oct, 09:00". */
    static String when(Context ctx, long t) {
        if (t <= 0) return "";
        Calendar now = Calendar.getInstance();
        Calendar x = Calendar.getInstance();
        x.setTimeInMillis(t);
        Calendar tomorrow = Calendar.getInstance();
        tomorrow.add(Calendar.DAY_OF_YEAR, 1);
        String time = new SimpleDateFormat(
                DateFormat.is24HourFormat(ctx) ? "HH:mm" : "h:mm a", Locale.getDefault())
                .format(new Date(t));
        String day;
        if (sameDay(x, now)) day = "Today";
        else if (sameDay(x, tomorrow)) day = "Tomorrow";
        else day = new SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(new Date(t));
        return day + ", " + time;
    }

    private static boolean sameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }
}
