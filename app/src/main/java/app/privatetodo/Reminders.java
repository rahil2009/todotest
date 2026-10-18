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
        if (unit == 5) { // n is a Mon..Sun bit mask
            Calendar base = Calendar.getInstance();
            base.setTimeInMillis(due);
            for (int k = 1; k < 100_000; k++) {
                Calendar c = (Calendar) base.clone();
                c.add(Calendar.DAY_OF_YEAR, k);
                if ((n & (1 << ((c.get(Calendar.DAY_OF_WEEK) + 5) % 7))) != 0
                        && c.getTimeInMillis() > now) {
                    return c.getTimeInMillis();
                }
            }
            return due + 86_400_000L;
        }
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

    /**
     * Where this task lands on the day starting at {@code d0}: its own time if it is due that day, a projected
     * time if it repeats onto that day, or 0 if it does not occur then. Repeats only project forward.
     */
    static long occurrence(Task t, long d0) {
        if (t.due <= 0) return 0;
        Calendar start = Calendar.getInstance();
        start.setTimeInMillis(t.due);
        Calendar day = Calendar.getInstance();
        day.setTimeInMillis(d0);
        Calendar dueDay = (Calendar) start.clone();
        dueDay.set(Calendar.HOUR_OF_DAY, 0);
        dueDay.set(Calendar.MINUTE, 0);
        dueDay.set(Calendar.SECOND, 0);
        dueDay.set(Calendar.MILLISECOND, 0);
        long dueStart = dueDay.getTimeInMillis();
        if (d0 == dueStart) return t.due;
        if (t.repeat <= 0 || t.done) return 0;
        // A daily/weekly series also shows on the days from today up to its next stored time.
        boolean back = d0 < dueStart;
        if (back && d0 < startOf(System.currentTimeMillis())) {
            // history: a past day only appears if that occurrence was ticked off
            return t.occDone.contains(d0) ? clockOn(d0, start) : 0;
        }
        if (back && t.repeatUnit <= 1) return 0;
        int days = (int) Math.round((d0 - dueStart) / 86_400_000.0);
        Calendar at = (Calendar) day.clone();
        at.set(Calendar.HOUR_OF_DAY, start.get(Calendar.HOUR_OF_DAY));
        at.set(Calendar.MINUTE, start.get(Calendar.MINUTE));
        at.set(Calendar.SECOND, 0);
        at.set(Calendar.MILLISECOND, 0);
        switch (t.repeatUnit) {
            case 5:
                return (t.repeat & (1 << ((day.get(Calendar.DAY_OF_WEEK) + 5) % 7))) != 0 ? at.getTimeInMillis() : 0;
            case 2:
                return Math.floorMod(days, t.repeat) == 0 ? at.getTimeInMillis() : 0;
            case 3:
                return Math.floorMod(days, 7 * t.repeat) == 0 ? at.getTimeInMillis() : 0;
            case 4: {
                int months = (day.get(Calendar.YEAR) - start.get(Calendar.YEAR)) * 12
                        + day.get(Calendar.MONTH) - start.get(Calendar.MONTH);
                if (months == 0 || Math.floorMod(months, t.repeat) != 0) return 0;
                int want = Math.min(start.get(Calendar.DAY_OF_MONTH), day.getActualMaximum(Calendar.DAY_OF_MONTH));
                return day.get(Calendar.DAY_OF_MONTH) == want ? at.getTimeInMillis() : 0;
            }
            default: { // minutes and hours: the first run of that day
                long step = t.repeat * (t.repeatUnit == 0 ? 60_000L : 3_600_000L);
                long k = Math.max(0, (d0 - t.due + step - 1) / step);
                long first = t.due + k * step;
                return first >= d0 && first < d0 + 86_400_000L + 3_600_000L && startOf(first) == d0 ? first : 0;
            }
        }
    }


    /**
     * A repeating task whose stored time is days ahead of its pattern (e.g. a daily task saved as
     * "Wed 7:00" on a Sunday) is pulled back to its first occurrence after now, so the alarm, the list
     * and the Plan all agree that it happens every day.
     */
    static long earliestFuture(Task t, long now) {
        if (t.repeat <= 0 || t.done || t.due <= now || t.fired || t.snooze > 0) return t.due;
        if (t.repeatUnit == 2 || t.repeatUnit == 3) {
            int stepDays = t.repeat * (t.repeatUnit == 3 ? 7 : 1);
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(t.due);
            long best = t.due;
            for (int i = 0; i < 2000; i++) {
                c.add(Calendar.DAY_OF_YEAR, -stepDays);
                if (c.getTimeInMillis() <= now) break;
                if (!t.occDone.contains(startOf(c.getTimeInMillis()))) best = c.getTimeInMillis();
            }
            return best;
        }
        if (t.repeatUnit == 5) {
            Calendar base = Calendar.getInstance();
            base.setTimeInMillis(t.due);
            Calendar c = Calendar.getInstance();
            c.set(Calendar.HOUR_OF_DAY, base.get(Calendar.HOUR_OF_DAY));
            c.set(Calendar.MINUTE, base.get(Calendar.MINUTE));
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
            for (int k = 0; k < 8; k++) {
                if ((t.repeat & (1 << ((c.get(Calendar.DAY_OF_WEEK) + 5) % 7))) != 0
                        && c.getTimeInMillis() > now && c.getTimeInMillis() < t.due
                        && !t.occDone.contains(startOf(c.getTimeInMillis()))) {
                    return c.getTimeInMillis();
                }
                c.add(Calendar.DAY_OF_YEAR, 1);
            }
        }
        return t.due;
    }

    /** Applies {@link #earliestFuture} to every task and re-arms the ones that moved. */
    static void pullBack(Context ctx) {
        TaskDb db = new TaskDb(ctx);
        try {
            long now = System.currentTimeMillis();
            for (Task t : db.withReminders()) {
                long nd = earliestFuture(t, now);
                if (nd != t.due) {
                    db.setState(t.id, nd, false, 0);
                    schedule(ctx, db.get(t.id));
                }
            }
        } finally {
            db.close();
        }
    }

    private static long clockOn(long d0, Calendar clock) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(d0);
        c.set(Calendar.HOUR_OF_DAY, clock.get(Calendar.HOUR_OF_DAY));
        c.set(Calendar.MINUTE, clock.get(Calendar.MINUTE));
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private static long startOf(long t) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    /** The task as it shows on the day starting at {@code d0}, or null when it does not occur. */
    static Task onDay(Task t, long d0) {
        long at = occurrence(t, d0);
        if (at == 0) return null;
        if (t.repeat > 0) {
            boolean dn = t.occDone.contains(d0);
            return at == t.due && !dn ? t : t.copyAt(at, dn);
        }
        return at == t.due ? t : t.ghostAt(at);
    }

    /** Next stored time for a repeating task, skipping days that were already ticked off. */
    static long skipDone(Task t, long due) {
        for (int i = 0; i < 400 && t.occDone.contains(startOf(due)); i++) {
            due = advance(due, t.repeat, t.repeatUnit, due);
        }
        return due;
    }

    /**
     * Ticks (or un-ticks) one day of a task. A one-off task is simply done/not done. A repeating task only gets that
     * day ticked: the series itself is never "done", so it stays in the list and on every other day. Returns the
     * new state of that day.
     */
    static boolean toggleDay(Context ctx, TaskDb db, Task t, long day) {
        if (t.repeat <= 0) {
            boolean nd = !t.done;
            db.setDone(t.id, nd);
            if (nd) cancel(ctx, t.id); else schedule(ctx, db.get(t.id));
            return nd;
        }
        boolean was = t.occDone.contains(day);
        if (!was) {
            db.occSet(t.id, day, true);
            Task fresh = db.get(t.id);
            cancelNotification(ctx, t.id);
            if (fresh != null && startOf(fresh.due) == day) { // the stored next time is the one just ticked
                long nd = skipDone(fresh, advance(fresh.due, fresh.repeat, fresh.repeatUnit, fresh.due));
                db.setState(t.id, nd, false, 0);
                db.resetSubs(t.id); // the next round starts with fresh subtasks
                schedule(ctx, db.get(t.id));
            }
            return true;
        }
        db.occSet(t.id, day, false);
        Task fresh = db.get(t.id);
        if (fresh != null) {
            Calendar clock = Calendar.getInstance();
            clock.setTimeInMillis(fresh.due);
            long inst = clockOn(day, clock);
            if (inst < fresh.due && inst > System.currentTimeMillis()) { // un-ticking an upcoming one: bring it back
                db.setState(t.id, inst, false, 0);
                schedule(ctx, db.get(t.id));
            }
        }
        return false;
    }

    /** Moves {@code due} forward to the first day (same clock time) allowed by the Mon..Sun mask. */
    static long nextAllowedDay(long due, int mask) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(due);
        for (int i = 0; i < 7; i++) {
            if ((mask & (1 << ((c.get(Calendar.DAY_OF_WEEK) + 5) % 7))) != 0) return c.getTimeInMillis();
            c.add(Calendar.DAY_OF_YEAR, 1);
        }
        return due;
    }

    /** "Every minute", "Every 30 min", "Every 2 days", "Every month"... */
    static String repeatLabel(int n, int unit) {
        if (unit == 5) {
            if (n == 31) return "Mon\u2013Fri";
            if (n == 127) return "Every day";
            String[] d = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 7; i++) {
                if ((n & (1 << i)) != 0) sb.append(sb.length() > 0 ? ", " : "").append(d[i]);
            }
            return sb.toString();
        }
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
                db.occSet(id, startOf(System.currentTimeMillis()), true); // "Done" ticks today's round
                db.setSnooze(id, 0);
                schedule(ctx, db.get(id));
            }
        } finally {
            db.close();
        }
    }

    static void cancelNotification(Context ctx, long id) {
        RingService.stopFor(ctx, id); // also ends the looping alarm sound
        if (!RingService.isRinging()) Sound.stop(); // a normal reminder's one-off sound
        ctx.getSystemService(NotificationManager.class).cancel((int) id);
    }

    // ------------------------------------------------------ notification

    static final String CH_ALARM_QUIET = "alarms_quiet_v1";

    /**
     * True when this alarm should simply take over the screen (phone in use, unlocked) instead of
     * also sliding in as a notification from the top.
     */
    static boolean takeOver(Context ctx, Task t) {
        if (!t.always || !android.provider.Settings.canDrawOverlays(ctx)) return false;
        android.os.PowerManager pm = ctx.getSystemService(android.os.PowerManager.class);
        android.app.KeyguardManager km = ctx.getSystemService(android.app.KeyguardManager.class);
        return pm != null && pm.isInteractive() && km != null && !km.isKeyguardLocked();
    }

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

        NotificationChannel quiet = new NotificationChannel(
                CH_ALARM_QUIET, "Alarm (screen already open)", NotificationManager.IMPORTANCE_LOW);
        quiet.setDescription("Quiet entry in the shade while the alarm screen is already showing");
        quiet.setSound(null, null);
        quiet.enableVibration(false);
        quiet.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);

        nm.createNotificationChannels(Arrays.asList(remind, alarm, quiet));
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
            final boolean quiet = takeOver(ctx, t);
            try {
                RingService.start(ctx, t.id, quiet); // posts the notification itself
            } catch (RuntimeException e) {
                // The system refused a foreground service: still show it, and ring for a minute.
                nm.notify((int) t.id, build(ctx, t, subs, quiet));
                Sound.play(ctx, Prefs.ALARM, true, 60_000L, null, t.sound, t.volume);
            }
            if (quiet) {
                try { // the overlay permission lets the alarm screen open even while the phone is in use
                    ctx.startActivity(new Intent(ctx, AlarmActivity.class)
                            .putExtra(EXTRA_ID, t.id)
                            .setData(Uri.parse("todo://alarm/" + t.id))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_USER_ACTION));
                } catch (RuntimeException ignored) {
                    // the notification's full-screen intent still covers the locked case
                }
            }
            if (done != null) done.run();
        } else {
            nm.notify((int) t.id, build(ctx, t, subs));
            Sound.play(ctx, Prefs.NOTIF, false, 9_000L, done, t.sound, t.volume);
        }
    }

    static Notification build(Context ctx, Task t, List<Sub> subs) {
        return build(ctx, t, subs, false);
    }

    /** {@code quiet}: the alarm screen is already open, so no heads-up pop-in and no full-screen intent. */
    static Notification build(Context ctx, Task t, List<Sub> subs, boolean quiet) {
        ensureChannels(ctx);

        PendingIntent open = PendingIntent.getActivity(ctx, (int) t.id,
                new Intent(ctx, MainActivity.class).setFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.Builder b = new Notification.Builder(ctx, t.alarm ? (quiet ? CH_ALARM_QUIET : CH_ALARM) : CH_REMIND)
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
            // Goes through LinkActivity so the ringing and vibration stop as the link opens.
            PendingIntent openLink = PendingIntent.getActivity(ctx, (int) t.id + 2_000_000,
                    new Intent(ctx, LinkActivity.class)
                            .putExtra(EXTRA_ID, t.id)
                            .putExtra("url", Links.normalize(links.get(0)))
                            .setData(Uri.parse("todo://link/" + t.id))
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
                    .setAutoCancel(false)
                    .setTimeoutAfter(10 * 60 * 1000L);
            if (!quiet) b.setFullScreenIntent(full, true);
        } else {
            b.setCategory(Notification.CATEGORY_REMINDER).setAutoCancel(true);
        }

        Notification n = b.build();
        // The sound is played by the app (Sound / RingService); INSISTENT keeps the vibration repeating.
        if (t.alarm && !quiet) n.flags |= Notification.FLAG_INSISTENT;
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
