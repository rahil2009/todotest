package app.privatetodo;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/** Local-only SQLite storage. The file lives in the app's private data folder. */
final class TaskDb extends SQLiteOpenHelper {
    static final int ACTIVE = 0;
    static final int DONE = 1;
    static final int ALL = 2;
    static final int TODAY = 3;
    static final int TOMORROW = 4;
    static final int UPCOMING = 5;
    static final int PRIORITY = 6;
    static final int RAW = 8; // everything exactly as stored (Plan, Calendar, widgets project repeats from it)
    static final int REPEAT = 7; // repeating tasks ticked for today, waiting for their next round

    private static final String[] COLS = {
            "id", "title", "done", "due_at", "repeat_mode", "repeat_unit", "is_alarm", "snooze_until", "fired",
            "sound", "volume", "priority", "emoji"};

    private final Context appCtx;

    TaskDb(Context context) {
        super(context, "tasks.db", null, 11);
        appCtx = context.getApplicationContext();
    }

    private void changed() {
        TaskWidget.refreshSoon(appCtx);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE tasks ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "title TEXT NOT NULL,"
                + "done INTEGER NOT NULL DEFAULT 0,"
                + "created INTEGER NOT NULL,"
                + "due_at INTEGER NOT NULL DEFAULT 0,"
                + "repeat_mode INTEGER NOT NULL DEFAULT 0,"
                + "repeat_unit INTEGER NOT NULL DEFAULT 2,"
                + "is_alarm INTEGER NOT NULL DEFAULT 0,"
                + "snooze_until INTEGER NOT NULL DEFAULT 0,"
                + "fired INTEGER NOT NULL DEFAULT 0,"
                + "sound TEXT NOT NULL DEFAULT '',"
                + "volume INTEGER NOT NULL DEFAULT -1,"
                + "priority INTEGER NOT NULL DEFAULT 0,"
                + "emoji TEXT NOT NULL DEFAULT '')");
        createSubtasks(db);
        createNotes(db);
        createOcc(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE tasks ADD COLUMN due_at INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE tasks ADD COLUMN repeat_mode INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE tasks ADD COLUMN is_alarm INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE tasks ADD COLUMN snooze_until INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE tasks ADD COLUMN fired INTEGER NOT NULL DEFAULT 0");
        }
        if (oldVersion < 3) createSubtasks(db);
        if (oldVersion < 4) {
            // repeat_mode used to be 1 = daily, 2 = weekly. It now means "every N <repeat_unit>".
            db.execSQL("ALTER TABLE tasks ADD COLUMN repeat_unit INTEGER NOT NULL DEFAULT 2");
            db.execSQL("UPDATE tasks SET repeat_mode = 1, repeat_unit = 3 WHERE repeat_mode = 2");
        }
        if (oldVersion < 6) createNotes(db);
        if (oldVersion < 5) { // per-task sound and volume; "" / -1 mean "use Settings"
            db.execSQL("ALTER TABLE tasks ADD COLUMN sound TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE tasks ADD COLUMN volume INTEGER NOT NULL DEFAULT -1");
        }
        if (oldVersion < 7) { // priority: 0 none, 1 low, 2 medium, 3 high
            db.execSQL("ALTER TABLE tasks ADD COLUMN priority INTEGER NOT NULL DEFAULT 0");
        }
        if (oldVersion < 8) { // optional emoji per task
            db.execSQL("ALTER TABLE tasks ADD COLUMN emoji TEXT NOT NULL DEFAULT ''");
        }
        if (oldVersion < 9) { // per-day ticks for repeating tasks; a repeating series is never "done"
            createOcc(db);
            db.execSQL("UPDATE tasks SET done = 0 WHERE repeat_mode > 0 AND done = 1");
        }
        if (oldVersion < 10) { // pinned notes: the time it was pinned, 0 = not pinned
            db.execSQL("ALTER TABLE notes ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0");
        }
        if (oldVersion < 11) { // a pinned note's own colour, -1 = follow Settings
            db.execSQL("ALTER TABLE notes ADD COLUMN pincolor INTEGER NOT NULL DEFAULT -1");
        }
    }

    private static void createOcc(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS occ_done ("
                + "task_id INTEGER NOT NULL,"
                + "day INTEGER NOT NULL,"
                + "PRIMARY KEY (task_id, day))");
    }

    /** Loads the ticked days of every repeating task in the list. */
    private void attachOcc(List<Task> tasks) {
        java.util.Map<Long, Task> rep = new java.util.HashMap<>();
        for (Task t : tasks) if (t.repeat > 0) rep.put(t.id, t);
        if (rep.isEmpty()) return;
        Cursor c = getReadableDatabase().rawQuery("SELECT task_id, day FROM occ_done", null);
        try {
            java.util.Map<Long, java.util.Set<Long>> m = new java.util.HashMap<>();
            while (c.moveToNext()) {
                java.util.Set<Long> set = m.get(c.getLong(0));
                if (set == null) {
                    set = new java.util.HashSet<>();
                    m.put(c.getLong(0), set);
                }
                set.add(c.getLong(1));
            }
            for (java.util.Map.Entry<Long, Task> e : rep.entrySet()) {
                java.util.Set<Long> set = m.get(e.getKey());
                if (set != null) e.getValue().occDone = set;
            }
        } finally {
            c.close();
        }
    }

    /** Ticks or un-ticks one day of a repeating task. */
    void occSet(long id, long day, boolean done) {
        changed();
        SQLiteDatabase w = getWritableDatabase();
        if (done) {
            ContentValues v = new ContentValues();
            v.put("task_id", id);
            v.put("day", day);
            w.insertWithOnConflict("occ_done", null, v, SQLiteDatabase.CONFLICT_REPLACE);
        } else {
            w.delete("occ_done", "task_id = ? AND day = ?", new String[]{String.valueOf(id), String.valueOf(day)});
        }
    }

    private static void createNotes(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS notes ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "title TEXT NOT NULL DEFAULT '',"
                + "body TEXT NOT NULL DEFAULT '',"
                + "plain TEXT NOT NULL DEFAULT '',"
                + "updated INTEGER NOT NULL DEFAULT 0,"
                + "pinned INTEGER NOT NULL DEFAULT 0,"
                + "pincolor INTEGER NOT NULL DEFAULT -1)");
    }

    private static void createSubtasks(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS subtasks ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "task_id INTEGER NOT NULL,"
                + "title TEXT NOT NULL,"
                + "done INTEGER NOT NULL DEFAULT 0,"
                + "pos INTEGER NOT NULL DEFAULT 0)");
    }

    private static Task map(Cursor c) {
        return new Task(c.getLong(0), c.getString(1), c.getInt(2) == 1, c.getLong(3),
                c.getInt(4), c.getInt(5), c.getInt(6) != 0, c.getLong(7), c.getInt(8) == 1,
                c.getString(9), c.getInt(10), c.getInt(11), c.getInt(6) == 2, c.getString(12));
    }

    List<Task> list(int filter) {
        String where = null;
        String[] args = null;
        if (filter == ACTIVE) {
            where = "done = ?";
            args = new String[]{"0"};
        } else if (filter == DONE) {
            where = "done = ?";
            args = new String[]{"1"};
        } else if (filter == REPEAT) {
            where = "done = 0 AND repeat_mode > 0";
        }
        String order = "done ASC, priority DESC, created DESC, id DESC";
        if (filter >= TODAY && filter != REPEAT && filter != RAW) {
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
            cal.set(java.util.Calendar.MINUTE, 0);
            cal.set(java.util.Calendar.SECOND, 0);
            cal.set(java.util.Calendar.MILLISECOND, 0);
            cal.add(java.util.Calendar.DAY_OF_YEAR, 1);
            long tomorrow = cal.getTimeInMillis();
            cal.add(java.util.Calendar.DAY_OF_YEAR, 1);
            long after = cal.getTimeInMillis();
            if (filter == TODAY) { // today, plus anything overdue
                where = "done = 0 AND due_at > 0 AND due_at < ?";
                args = new String[]{String.valueOf(tomorrow)};
            } else if (filter == TOMORROW) {
                where = "done = 0 AND due_at >= ? AND due_at < ?";
                args = new String[]{String.valueOf(tomorrow), String.valueOf(after)};
            } else if (filter == UPCOMING) {
                where = "done = 0 AND due_at >= ?";
                args = new String[]{String.valueOf(after)};
            } else {
                where = "done = 0 AND priority > 0";
                args = null;
            }
            if (filter != PRIORITY) order = "due_at ASC, priority DESC, id DESC";
        }
        List<Task> out = new ArrayList<>();
        Cursor c = getReadableDatabase().query("tasks", COLS, where, args, null, null, order);
        try {
            while (c.moveToNext()) out.add(map(c));
        } finally {
            c.close();
        }
        attachOcc(out);
        // A repeating task that is ticked for today lives in the Repeat tab until midnight.
        if (filter == RAW) return out;
        long today = Reminders.startOf(System.currentTimeMillis());
        List<Task> res = new ArrayList<>();
        for (Task t : out) {
            boolean waiting = Reminders.inRepeatTab(t, today);
            if (filter == REPEAT) {
                if (waiting) res.add(t.copyAt(t.due, true));
            } else if (!waiting || filter == DONE) {
                res.add(t);
            } else if (filter != ACTIVE) {
                res.add(t.copyAt(t.due, true)); // shown as ticked in All / Today / Tomorrow / ...
            }
        }
        return res;
    }

    /** Active tasks that carry a reminder (used to re-arm alarms after reboot). */
    List<Task> withReminders() {
        List<Task> out = new ArrayList<>();
        Cursor c = getReadableDatabase().query("tasks", COLS,
                "done = 0 AND (due_at > 0 OR snooze_until > 0)", null, null, null, null);
        try {
            while (c.moveToNext()) out.add(map(c));
        } finally {
            c.close();
        }
        attachOcc(out);
        return out;
    }

    Task get(long id) {
        Cursor c = getReadableDatabase().query("tasks", COLS, "id = ?",
                new String[]{String.valueOf(id)}, null, null, null);
        try {
            if (!c.moveToFirst()) return null;
            Task t = map(c);
            if (t.repeat > 0) {
                List<Task> one = new ArrayList<>();
                one.add(t);
                attachOcc(one);
            }
            return t;
        } finally {
            c.close();
        }
    }

    int count(boolean done) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM tasks WHERE done = ?", new String[]{done ? "1" : "0"});
        try {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally {
            c.close();
        }
    }

    /** 0 none, 1 low, 2 medium, 3 high. */
    /** is_alarm: 0 = normal, 1 = alarm style, 2 = alarm style that also shows full screen when unlocked. */
    void setAlarmMode(long id, boolean alarm, boolean always) {
        ContentValues v = new ContentValues();
        v.put("is_alarm", alarm ? (always ? 2 : 1) : 0);
        getWritableDatabase().update("tasks", v, "id = ? AND due_at > 0", new String[]{String.valueOf(id)});
    }

    void setEmoji(long id, String emoji) {
        changed();
        ContentValues v = new ContentValues();
        v.put("emoji", emoji == null ? "" : emoji);
        getWritableDatabase().update("tasks", v, "id = ?", new String[]{String.valueOf(id)});
    }

    void setPriority(long id, int priority) {
        changed();
        ContentValues v = new ContentValues();
        v.put("priority", Math.max(0, Math.min(3, priority)));
        getWritableDatabase().update("tasks", v, "id = ?", new String[]{String.valueOf(id)});
    }

    /** A task's own sound ("" = Settings) and volume (-1 = Settings). */
    void setSound(long id, String sound, int volume) {
        ContentValues v = new ContentValues();
        v.put("sound", sound == null ? "" : sound);
        v.put("volume", volume);
        getWritableDatabase().update("tasks", v, "id = ?", new String[]{String.valueOf(id)});
    }

    long add(String title, long due, int repeat, int repeatUnit, boolean alarm) {
        changed();
        ContentValues v = new ContentValues();
        v.put("title", title);
        v.put("done", 0);
        v.put("created", System.currentTimeMillis());
        v.put("due_at", due);
        v.put("repeat_mode", due > 0 ? repeat : 0);
        v.put("repeat_unit", repeatUnit);
        v.put("is_alarm", due > 0 && alarm ? 1 : 0);
        return getWritableDatabase().insert("tasks", null, v);
    }

    void update(long id, String title, long due, int repeat, int repeatUnit, boolean alarm) {
        changed();
        Task old = get(id);
        ContentValues v = new ContentValues();
        v.put("title", title);
        v.put("due_at", due);
        v.put("repeat_mode", due > 0 ? repeat : 0);
        v.put("repeat_unit", repeatUnit);
        v.put("is_alarm", due > 0 && alarm ? 1 : 0);
        boolean unchanged = old != null && old.due == due && old.repeat == repeat
                && old.repeatUnit == repeatUnit;
        if (!unchanged) {
            v.put("fired", 0);
            v.put("snooze_until", 0);
        }
        getWritableDatabase().update("tasks", v, "id = ?", new String[]{String.valueOf(id)});
    }

    void setDone(long id, boolean done) {
        changed();
        ContentValues v = new ContentValues();
        v.put("done", done ? 1 : 0);
        if (done) {
            v.put("snooze_until", 0);
        } else {
            // Un-checking a task must not instantly ring for a time that already passed.
            Task t = get(id);
            if (t != null && t.due > 0 && t.due < System.currentTimeMillis()
                    && t.repeat == 0) {
                v.put("fired", 1);
            }
        }
        getWritableDatabase().update("tasks", v, "id = ?", new String[]{String.valueOf(id)});
    }

    void setState(long id, long due, boolean fired, long snooze) {
        changed();
        ContentValues v = new ContentValues();
        v.put("due_at", due);
        v.put("fired", fired ? 1 : 0);
        v.put("snooze_until", snooze);
        getWritableDatabase().update("tasks", v, "id = ?", new String[]{String.valueOf(id)});
    }

    void setSnooze(long id, long until) {
        ContentValues v = new ContentValues();
        v.put("snooze_until", until);
        getWritableDatabase().update("tasks", v, "id = ?", new String[]{String.valueOf(id)});
    }

    void delete(long id) {
        changed();
        SQLiteDatabase w = getWritableDatabase();
        w.delete("subtasks", "task_id = ?", new String[]{String.valueOf(id)});
        w.delete("occ_done", "task_id = ?", new String[]{String.valueOf(id)});
        w.delete("tasks", "id = ?", new String[]{String.valueOf(id)});
    }

    /** Raw copy of some tasks and their subtasks, so a delete can be undone. */
    static final class Snap {
        final List<ContentValues> tasks = new ArrayList<>();
        final List<ContentValues> subs = new ArrayList<>();
    }

    Snap snapshot(String where, String[] args) {
        Snap s = new Snap();
        SQLiteDatabase r = getReadableDatabase();
        Cursor c = r.query("tasks", null, where, args, null, null, null);
        try {
            while (c.moveToNext()) {
                ContentValues v = new ContentValues();
                android.database.DatabaseUtils.cursorRowToContentValues(c, v);
                s.tasks.add(v);
            }
        } finally {
            c.close();
        }
        for (ContentValues t : s.tasks) {
            Cursor sc = r.query("subtasks", null, "task_id = ?",
                    new String[]{String.valueOf(t.getAsLong("id"))}, null, null, null);
            try {
                while (sc.moveToNext()) {
                    ContentValues v = new ContentValues();
                    android.database.DatabaseUtils.cursorRowToContentValues(sc, v);
                    s.subs.add(v);
                }
            } finally {
                sc.close();
            }
        }
        return s;
    }

    void restore(Snap s) {
        changed();
        SQLiteDatabase w = getWritableDatabase();
        w.beginTransaction();
        try {
            for (ContentValues v : s.tasks) w.insertWithOnConflict("tasks", null, v, SQLiteDatabase.CONFLICT_REPLACE);
            for (ContentValues v : s.subs) w.insertWithOnConflict("subtasks", null, v, SQLiteDatabase.CONFLICT_REPLACE);
            w.setTransactionSuccessful();
        } finally {
            w.endTransaction();
        }
    }

    /** A fresh, not-done copy of a task with its subtasks (un-ticked). A reminder in the past is dropped. */
    long duplicate(long id) {
        Snap s = snapshot("id = ?", new String[]{String.valueOf(id)});
        if (s.tasks.isEmpty()) return -1;
        ContentValues t = s.tasks.get(0);
        t.remove("id");
        t.put("created", System.currentTimeMillis());
        t.put("done", 0);
        t.put("fired", 0);
        t.put("snooze_until", 0);
        Long due = t.getAsLong("due_at");
        if (due == null || due <= System.currentTimeMillis()) {
            t.put("due_at", 0);
            t.put("repeat_mode", 0);
            t.put("is_alarm", 0);
        }
        SQLiteDatabase w = getWritableDatabase();
        long nid = w.insert("tasks", null, t);
        for (ContentValues v : s.subs) {
            v.remove("id");
            v.put("task_id", nid);
            v.put("done", 0);
            w.insert("subtasks", null, v);
        }
        return nid;
    }

    void resetSubs(long taskId) {
        getWritableDatabase().execSQL("UPDATE subtasks SET done = 0 WHERE task_id = ?",
                new Object[]{taskId});
    }

    void clearDone() {
        changed();
        SQLiteDatabase w = getWritableDatabase();
        w.execSQL("DELETE FROM subtasks WHERE task_id IN (SELECT id FROM tasks WHERE done = 1)");
        w.delete("tasks", "done = 1", null);
    }

    // ------------------------------------------------------------ subtasks

    List<Sub> subs(long taskId) {
        List<Sub> out = new ArrayList<>();
        Cursor c = getReadableDatabase().query("subtasks",
                new String[]{"id", "task_id", "title", "done"}, "task_id = ?",
                new String[]{String.valueOf(taskId)}, null, null, "pos ASC, id ASC");
        try {
            while (c.moveToNext()) {
                out.add(new Sub(c.getLong(0), c.getLong(1), c.getString(2), c.getInt(3) == 1));
            }
        } finally {
            c.close();
        }
        return out;
    }

    /** Saves the editor's subtask list in one go (keeps order and done flags). */
    void replaceSubs(long taskId, List<Sub> subs) {
        SQLiteDatabase w = getWritableDatabase();
        w.beginTransaction();
        try {
            w.delete("subtasks", "task_id = ?", new String[]{String.valueOf(taskId)});
            for (int i = 0; i < subs.size(); i++) {
                ContentValues v = new ContentValues();
                v.put("task_id", taskId);
                v.put("title", subs.get(i).title);
                v.put("done", subs.get(i).done ? 1 : 0);
                v.put("pos", i);
                w.insert("subtasks", null, v);
            }
            w.setTransactionSuccessful();
        } finally {
            w.endTransaction();
        }
    }

    void setSubDone(long subId, boolean done) {
        ContentValues v = new ContentValues();
        v.put("done", done ? 1 : 0);
        getWritableDatabase().update("subtasks", v, "id = ?", new String[]{String.valueOf(subId)});
    }

    // ------------------------------------------------------------ notebook

    private static final String[] NOTE_COLS = {"id", "title", "body", "plain", "updated", "pinned", "pincolor"};

    private static Note mapNote(Cursor c) {
        return new Note(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getLong(4), c.getLong(5), c.getInt(6));
    }

    /** All notes, newest edit first; {@code query} filters title and text (empty = all). */
    List<Note> notes(String query) {
        return notes(query, 0);
    }

    /** Pinned notes first, then by {@code sort}: 0 edited newest, 1 edited oldest, 2 created newest, 3 title A-Z, 4 title Z-A. */
    List<Note> notes(String query, int sort) {
        String order;
        switch (sort) {
            case 1:
                order = "updated ASC, id ASC";
                break;
            case 2:
                order = "id DESC";
                break;
            case 3:
                order = "title COLLATE NOCASE ASC, id ASC";
                break;
            case 4:
                order = "title COLLATE NOCASE DESC, id DESC";
                break;
            default:
                order = "updated DESC, id DESC";
        }
        String where = null;
        String[] args = null;
        if (query != null && !query.isEmpty()) {
            where = "title LIKE ? OR plain LIKE ?";
            String like = "%" + query + "%";
            args = new String[]{like, like};
        }
        List<Note> out = new ArrayList<>();
        Cursor c = getReadableDatabase().query("notes", NOTE_COLS, where, args, null, null,
                "(pinned > 0) DESC, pinned DESC, " + order);
        try {
            while (c.moveToNext()) out.add(mapNote(c));
        } finally {
            c.close();
        }
        return out;
    }

    Note note(long id) {
        Cursor c = getReadableDatabase().query("notes", NOTE_COLS, "id = ?",
                new String[]{String.valueOf(id)}, null, null, null);
        try {
            return c.moveToFirst() ? mapNote(c) : null;
        } finally {
            c.close();
        }
    }

    /** Inserts (id <= 0) or updates a note; returns its id. */
    long saveNote(long id, String title, String body, String plain) {
        changed();
        ContentValues v = new ContentValues();
        v.put("title", title);
        v.put("body", body);
        v.put("plain", plain);
        v.put("updated", System.currentTimeMillis());
        if (id <= 0) return getWritableDatabase().insert("notes", null, v);
        getWritableDatabase().update("notes", v, "id = ?", new String[]{String.valueOf(id)});
        return id;
    }

    int pinnedCount() {
        Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM notes WHERE pinned > 0", null);
        try {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally {
            c.close();
        }
    }

    /** Pins or unpins a note; the edit time is left alone. */
    void setPinned(long id, boolean pin) {
        changed();
        ContentValues v = new ContentValues();
        v.put("pinned", pin ? System.currentTimeMillis() : 0);
        getWritableDatabase().update("notes", v, "id = ?", new String[]{String.valueOf(id)});
    }

    /** Sets one note's own pinned colour; -1 goes back to the Settings choice. */
    void setPinColor(long id, int style) {
        changed();
        ContentValues v = new ContentValues();
        v.put("pincolor", style);
        getWritableDatabase().update("notes", v, "id = ?", new String[]{String.valueOf(id)});
    }

    void deleteNote(long id) {
        changed();
        getWritableDatabase().delete("notes", "id = ?", new String[]{String.valueOf(id)});
    }
}
