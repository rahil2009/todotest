package app.privatetodo;

final class Task {
    final long id;
    final String title;
    final boolean done;
    /** Reminder time in epoch millis, 0 = no reminder. */
    final long due;
    /** Repeat "every N units" (see repeatUnit); 0 = once. */
    final int repeat;
    /** Unit of the repeat interval: 0 min, 1 hour, 2 day, 3 week, 4 month. */
    final int repeatUnit;
    /** true = full-screen, ringing alarm style. */
    final boolean alarm;
    /** Snoozed-until time in epoch millis, 0 = not snoozed. */
    final long snooze;
    /** true once the current {@link #due} time has already rung. */
    final boolean fired;
    /** This task's own sound: "" = use Settings, Prefs.SILENT, or a URI string. */
    final String sound;
    /** This task's own volume 0..100, -1 = use Settings. */
    final int volume;
    /** 0 none, 1 low, 2 medium, 3 high. */
    final int priority;
    /** Alarm style that also takes over the screen while the phone is unlocked. */
    final boolean always;
    /** Optional emoji shown before the title ("" = none). */
    final String emoji;

    /** Days (local midnight, millis) on which this repeating task's occurrence was ticked off. */
    java.util.Set<Long> occDone = java.util.Collections.emptySet();

    /** This task as it appears at {@code at}: a ticked/unticked copy, marked as projected when it is not the stored time. */
    Task copyAt(long at, boolean isDone) {
        Task g = new Task(id, title, isDone, at, repeat, repeatUnit, alarm, 0, false, sound, volume, priority,
                always, emoji);
        g.ghost = at != due;
        g.occDone = occDone;
        return g;
    }

    /** true for a projected future occurrence of a repeating task (shown in Plan and Calendar, not stored). */
    boolean ghost;

    /** A copy of this task placed at another time, marked as a projected occurrence. */
    Task ghostAt(long at) {
        Task g = new Task(id, title, false, at, repeat, repeatUnit, alarm, 0, false, sound, volume, priority,
                always, emoji);
        g.ghost = at != due;
        return g;
    }

    Task(long id, String title, boolean done, long due, int repeat, int repeatUnit,
         boolean alarm, long snooze, boolean fired, String sound, int volume, int priority,
         boolean always, String emoji) {
        this.id = id;
        this.title = title;
        this.done = done;
        this.due = due;
        this.repeat = repeat;
        this.repeatUnit = repeatUnit;
        this.alarm = alarm;
        this.snooze = snooze;
        this.fired = fired;
        this.sound = sound == null ? "" : sound;
        this.volume = volume;
        this.priority = priority;
        this.always = alarm && always;
        this.emoji = emoji == null ? "" : emoji;
    }
}
