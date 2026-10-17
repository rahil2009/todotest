package app.privatetodo;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.os.Build;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Pomodoro state and logic. Everything lives in SharedPreferences, so the timer keeps going when the
 * screen is closed: the end of a period is an AlarmManager alarm, and the running countdown is an ongoing
 * notification with a chronometer. Nothing leaves the phone.
 */
final class Pomo {
    private Pomo() { }

    static final int FOCUS = 0;
    static final int SHORT = 1;
    static final int LONG = 2;

    private static final String CH_RUN = "pomodoro_run_v1";
    private static final String CH_DONE = "pomodoro_done_v1";
    private static final int NOTIF_RUN = 91001;
    private static final int NOTIF_DONE = 91002;
    private static final String ACTION_END = "app.privatetodo.POMODORO_END";

    static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("pomodoro", Context.MODE_PRIVATE);
    }

    // ------------------------------------------------------------------ settings

    static int minutes(Context c, int phase) {
        int[] def = {25, 5, 15};
        return sp(c).getInt("min" + phase, def[phase]);
    }

    static void setMinutes(Context c, int phase, int v) {
        sp(c).edit().putInt("min" + phase, Math.max(1, Math.min(180, v))).apply();
        if (!running(c) && phase(c) == phase) sp(c).edit().putLong("left", minutes(c, phase) * 60_000L).apply();
    }

    // ------------------------------------------------------------------ state

    static int phase(Context c) {
        return sp(c).getInt("phase", FOCUS);
    }

    static boolean running(Context c) {
        return sp(c).getBoolean("running", false);
    }

    /** Sessions finished in the current round of four (0..3). */
    static int round(Context c) {
        return sp(c).getInt("round", 0);
    }

    static long total(Context c) {
        return minutes(c, phase(c)) * 60_000L;
    }

    /** Milliseconds left in the current period. */
    static long left(Context c) {
        SharedPreferences s = sp(c);
        if (s.getBoolean("running", false)) {
            return Math.max(0, s.getLong("end", 0) - System.currentTimeMillis());
        }
        return s.getLong("left", minutes(c, s.getInt("phase", FOCUS)) * 60_000L);
    }

    static long taskId(Context c) {
        return sp(c).getLong("task", -1);
    }

    static void setTask(Context c, long id) {
        sp(c).edit().putLong("task", id).apply();
    }

    static void start(Context c) {
        long ms = left(c);
        if (ms <= 0) ms = total(c);
        long end = System.currentTimeMillis() + ms;
        sp(c).edit().putBoolean("running", true).putLong("end", end).putLong("began",
                sp(c).getLong("began", 0) > 0 ? sp(c).getLong("began", 0) : System.currentTimeMillis()).apply();
        setAlarm(c, end);
        showRunning(c, end);
    }

    static void pause(Context c) {
        long ms = left(c);
        sp(c).edit().putBoolean("running", false).putLong("left", ms).apply();
        cancelAlarm(c);
        notifications(c).cancel(NOTIF_RUN);
    }

    static void reset(Context c) {
        sp(c).edit().putBoolean("running", false).putLong("left", total(c)).remove("began").apply();
        cancelAlarm(c);
        notifications(c).cancel(NOTIF_RUN);
    }

    static void select(Context c, int phase) {
        cancelAlarm(c);
        notifications(c).cancel(NOTIF_RUN);
        sp(c).edit().putInt("phase", phase).putBoolean("running", false)
                .putLong("left", minutes(c, phase) * 60_000L).remove("began").apply();
    }

    /** Moves to the next period; a finished focus counts towards today's stats. */
    static void skip(Context c) {
        advance(c, false);
    }

    /** Called when the clock reaches zero (alarm or the open screen). Does nothing if not running. */
    static void finishPhase(Context c) {
        if (!running(c)) return;
        advance(c, true);
    }

    private static void advance(Context c, boolean completed) {
        int p = phase(c);
        int round = round(c);
        int next;
        if (p == FOCUS) {
            if (completed) addStat(c, minutes(c, FOCUS));
            round++;
            if (round >= 4) {
                next = LONG;
                round = 0;
            } else {
                next = SHORT;
            }
        } else {
            next = FOCUS;
        }
        cancelAlarm(c);
        notifications(c).cancel(NOTIF_RUN);
        sp(c).edit().putInt("phase", next).putInt("round", round).putBoolean("running", false)
                .putLong("left", minutes(c, next) * 60_000L).remove("began").apply();
        if (completed) announce(c, p, next);
    }

    // ------------------------------------------------------------------ stats

    private static String day(long t) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(t));
    }

    private static void addStat(Context c, int mins) {
        SharedPreferences s = sp(c);
        String key = "d_" + day(System.currentTimeMillis());
        String[] v = s.getString(key, "0:0").split(":");
        int n = Integer.parseInt(v[0]) + 1;
        int m = Integer.parseInt(v[1]) + mins;
        s.edit().putString(key, n + ":" + m).apply();
    }

    /** {sessions, minutes} for the day that is {@code ago} days back. */
    static int[] stat(Context c, int ago) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -ago);
        String[] v = sp(c).getString("d_" + day(cal.getTimeInMillis()), "0:0").split(":");
        return new int[]{Integer.parseInt(v[0]), Integer.parseInt(v[1])};
    }

    // ------------------------------------------------------------------ alarm + notifications

    private static NotificationManager notifications(Context c) {
        return c.getSystemService(NotificationManager.class);
    }

    private static PendingIntent endIntent(Context c) {
        return PendingIntent.getBroadcast(c, 91003,
                new Intent(c, PomodoroReceiver.class).setAction(ACTION_END),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static void setAlarm(Context c, long end) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, end, endIntent(c));
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, end, endIntent(c));
        }
    }

    private static void cancelAlarm(Context c) {
        c.getSystemService(AlarmManager.class).cancel(endIntent(c));
    }

    private static void channels(Context c) {
        NotificationManager nm = notifications(c);
        NotificationChannel run = new NotificationChannel(CH_RUN, "Pomodoro timer",
                NotificationManager.IMPORTANCE_LOW);
        run.setDescription("Shows the running countdown");
        run.setShowBadge(false);
        nm.createNotificationChannel(run);
        NotificationChannel done = new NotificationChannel(CH_DONE, "Pomodoro finished",
                NotificationManager.IMPORTANCE_HIGH);
        done.setDescription("Tells you when a focus or break period ends");
        done.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build());
        done.enableVibration(true);
        done.setVibrationPattern(new long[]{0, 250, 120, 250, 120, 500});
        nm.createNotificationChannel(done);
    }

    static String phaseName(int p) {
        return p == FOCUS ? "Focus" : p == SHORT ? "Short break" : "Long break";
    }

    private static PendingIntent open(Context c) {
        return PendingIntent.getActivity(c, 91004,
                new Intent(c, PomodoroActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    static void showRunning(Context c, long end) {
        channels(c);
        Notification.Builder b = new Notification.Builder(c, CH_RUN)
                .setSmallIcon(R.drawable.ic_notify)
                .setContentTitle(phaseName(phase(c)))
                .setContentText(phase(c) == FOCUS ? "Stay with one thing" : "Rest your eyes")
                .setWhen(end)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setShowWhen(true)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(open(c));
        notifications(c).notify(NOTIF_RUN, b.build());
    }

    private static void announce(Context c, int finished, int next) {
        channels(c);
        String title = finished == FOCUS ? "Focus session done" : "Break over";
        String text = finished == FOCUS
                ? (next == LONG ? "Four in a row. Take a long break of " + minutes(c, LONG) + " min."
                        : "Take a " + minutes(c, SHORT) + " min break.")
                : "Ready for the next focus session?";
        Notification.Builder b = new Notification.Builder(c, CH_DONE)
                .setSmallIcon(R.drawable.ic_notify)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true)
                .setContentIntent(open(c));
        notifications(c).notify(NOTIF_DONE, b.build());
    }

    static void clearDone(Context c) {
        notifications(c).cancel(NOTIF_DONE);
    }
}
