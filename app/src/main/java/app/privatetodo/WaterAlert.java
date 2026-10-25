package app.privatetodo;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

/**
 * Water reminders: full-screen, alarm-style alerts at the times you choose (Settings > Water reminders).
 * Everything is local: AlarmManager wakes {@link WaterReceiver}, {@link WaterRingService} rings the alarm sound
 * a few times, and {@link WaterAlarmActivity} is the screen. Skipped once today's goal is met.
 */
final class WaterAlert {
    private WaterAlert() { }

    static final String ACTION_FIRE = "app.privatetodo.WATER_FIRE";
    static final String ACTION_ADD = "app.privatetodo.WATER_ADD";
    static final String ACTION_STOP = "app.privatetodo.WATER_STOP";
    static final int MAX_TIMES = 8;
    /** The slot used by "Snooze": a one-off alarm that does not touch the daily times. */
    static final int SNOOZE_SLOT = 99;
    static final int NOTIF_ID = 777001;
    /** Defaults: 8:00, 11:00, 14:00, 17:00, 19:00 (minutes after midnight). */
    private static final String DEFAULT_TIMES = "480,660,840,1020,1140";

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    static boolean enabled(Context c) {
        return sp(c).getBoolean("water_alert", false);
    }

    static void setEnabled(Context c, boolean on) {
        sp(c).edit().putBoolean("water_alert", on).apply();
        scheduleAll(c);
    }

    /** How many times the alarm sound plays at each reminder; the screen then stays until you answer. */
    static final int PLAYS = 3;

    /** Reminders per day (1 to 8) = how many times are set. */
    static int perDay(Context c) {
        return Math.max(1, Math.min(MAX_TIMES, times(c).size()));
    }

    /** Evenly spread day plans (minutes after midnight) for 1 to 8 reminders a day. */
    private static final int[][] PLANS = {
            {720},
            {540, 1080},
            {540, 840, 1140},
            {480, 720, 960, 1140},
            {480, 660, 840, 1020, 1140},
            {480, 600, 750, 900, 1020, 1140},
            {480, 600, 720, 840, 960, 1080, 1200},
            {480, 600, 720, 840, 960, 1080, 1170, 1260},
    };

    /** Sets the number of reminders a day and spreads them over the day (replaces custom times). */
    static void setPerDay(Context c, int n) {
        n = Math.max(1, Math.min(MAX_TIMES, n));
        java.util.ArrayList<Integer> list = new java.util.ArrayList<>();
        for (int v : PLANS[n - 1]) list.add(v);
        setTimes(c, list);
    }

    /** The daily times as minutes after midnight, earliest first. */
    static List<Integer> times(Context c) {
        TreeSet<Integer> set = new TreeSet<>();
        for (String s : sp(c).getString("water_times", DEFAULT_TIMES).split(",")) {
            try {
                int v = Integer.parseInt(s.trim());
                if (v >= 0 && v < 1440) set.add(v);
            } catch (NumberFormatException ignored) {
                // skip junk
            }
        }
        return new ArrayList<>(set);
    }

    static void setTimes(Context c, List<Integer> list) {
        TreeSet<Integer> set = new TreeSet<>(list);
        StringBuilder sb = new StringBuilder();
        for (int v : set) {
            if (sb.length() > 0) sb.append(',');
            sb.append(v);
        }
        sp(c).edit().putString("water_times", sb.toString()).apply();
        scheduleAll(c);
    }

    static String clock(Context c, int minutes) {
        Calendar x = Calendar.getInstance();
        x.set(Calendar.HOUR_OF_DAY, minutes / 60);
        x.set(Calendar.MINUTE, minutes % 60);
        return new java.text.SimpleDateFormat(android.text.format.DateFormat.is24HourFormat(c) ? "HH:mm" : "h:mm a",
                java.util.Locale.getDefault()).format(x.getTime());
    }

    private static PendingIntent slotIntent(Context c, int slot) {
        Intent i = new Intent(c, WaterReceiver.class).setAction(ACTION_FIRE)
                .setData(Uri.parse("todo://water/" + slot)).putExtra("slot", slot);
        return PendingIntent.getBroadcast(c, 7000 + slot, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /** The next moment (today, or tomorrow if it has passed) of a time of day. */
    static long next(int minutes) {
        Calendar x = Calendar.getInstance();
        x.set(Calendar.HOUR_OF_DAY, minutes / 60);
        x.set(Calendar.MINUTE, minutes % 60);
        x.set(Calendar.SECOND, 0);
        x.set(Calendar.MILLISECOND, 0);
        if (x.getTimeInMillis() <= System.currentTimeMillis() + 1000) x.add(Calendar.DAY_OF_YEAR, 1);
        return x.getTimeInMillis();
    }

    private static void arm(Context c, int slot, long at) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        PendingIntent fire = slotIntent(c, slot);
        try {
            PendingIntent show = PendingIntent.getActivity(c, 7100 + slot, new Intent(c, MainActivity.class),
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            am.setAlarmClock(new AlarmManager.AlarmClockInfo(at, show), fire); // exact, survives Doze
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, fire);
        }
    }

    /** (Re)arms the daily times, or cancels them all when water reminders are off. */
    static void scheduleAll(Context c) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        for (int slot = 0; slot < MAX_TIMES; slot++) am.cancel(slotIntent(c, slot));
        if (!enabled(c)) {
            am.cancel(slotIntent(c, SNOOZE_SLOT));
            return;
        }
        List<Integer> t = times(c);
        for (int i = 0; i < t.size() && i < MAX_TIMES; i++) arm(c, i, next(t.get(i)));
    }

    /** "Remind me again in N minutes" from the alert screen. */
    static void snooze(Context c, int minutes) {
        arm(c, SNOOZE_SLOT, System.currentTimeMillis() + minutes * 60_000L);
    }

    /** The water habit alerts add to: the first habit that counts as water. */
    static Habit habit(Context c) {
        HabitDb db = new HabitDb(c);
        try {
            for (Habit h : db.list()) if (HabitUi.isWater(h)) return h;
        } finally {
            db.close();
        }
        return null;
    }

    static int todayAmount(Context c, Habit h) {
        HabitDb db = new HabitDb(c);
        try {
            return Math.max(0, db.amount(h.id, HabitDb.today()));
        } finally {
            db.close();
        }
    }

    /** True when the day's water goal is already met, so there is no need to ring. */
    static boolean goalMet(Context c) {
        Habit h = habit(c);
        return h != null && h.goal > 0 && todayAmount(c, h) >= h.goal;
    }

    /** Logs one more drink on the water habit. Returns the new total, or -1 if there is no water habit. */
    static int add(Context c, int amount) {
        Habit h = habit(c);
        if (h == null) return -1;
        HabitDb db = new HabitDb(c);
        try {
            int now = Math.max(0, db.amount(h.id, HabitDb.today())) + amount;
            db.setAmount(h.id, HabitDb.today(), now);
            return now;
        } finally {
            db.close();
        }
    }

    /** Rings now: a scheduled time, or the Test button. */
    static void fire(Context c, boolean test) {
        if (!test && (!enabled(c) || goalMet(c))) return;
        try {
            c.startForegroundService(new Intent(c, WaterRingService.class));
        } catch (RuntimeException e) {
            // the system refused a foreground service: at least open the screen
            try {
                c.startActivity(new Intent(c, WaterAlarmActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (RuntimeException ignored) {
                // nothing more can be done from here
            }
        }
    }
}
