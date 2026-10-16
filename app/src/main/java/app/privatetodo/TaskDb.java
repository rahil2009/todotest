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

    private static final String[] COLS = {
            "id", "title", "done", "due_at", "repeat_mode", "repeat_unit", "is_alarm", "snooze_until", "fired",
            "sound", "volume"};

    TaskDb(Context context) {
        super(context, "tasks.db", null, 6);
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
                + "volume INTEGER NOT NULL DEFAULT -1)");
        createSubtasks(db);
        createNotes(db);
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
    }

    private static void createNotes(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS notes ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "title TEXT NOT NULL DEFAULT '',"
                + "body TEXT NOT NULL DEFAULT '',"
                + "plain TEXT NOT NULL DEFAULT '',"
                + "updated INTEGER NOT NULL DEFAULT 0)");
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
                c.getInt(4), c.getInt(5), c.getInt(6) == 1, c.getLong(7), c.getInt(8) == 1,
                c.getString(9), c.getInt(10));
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
        }
        List<Task> out = new ArrayList<>();
        Cursor c = getReadableDatabase().query("tasks", COLS, where, args, null, null,
                "done ASC, created DESC, id DESC");
        try {
            while (c.moveToNext()) out.add(map(c));
        } finally {
            c.close();
        }
        return out;
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
        return out;
    }

    Task get(long id) {
        Cursor c = getReadableDatabase().query("tasks", COLS, "id = ?",
                new String[]{String.valueOf(id)}, null, null, null);
        try {
            return c.moveToFirst() ? map(c) : null;
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

    /** A task's own sound ("" = Settings) and volume (-1 = Settings). */
    void setSound(long id, String sound, int volume) {
        ContentValues v = new ContentValues();
        v.put("sound", sound == null ? "" : sound);
        v.put("volume", volume);
        getWritableDatabase().update("tasks", v, "id = ?", new String[]{String.valueOf(id)});
    }

    long add(String title, long due, int repeat, int repeatUnit, boolean alarm) {
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
        SQLiteDatabase w = getWritableDatabase();
        w.delete("subtasks", "task_id = ?", new String[]{String.valueOf(id)});
        w.delete("tasks", "id = ?", new String[]{String.valueOf(id)});
    }

    void clearDone() {
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

    private static final String[] NOTE_COLS = {"id", "title", "body", "plain", "updated"};

    private static Note mapNote(Cursor c) {
        return new Note(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getLong(4));
    }

    /** All notes, newest edit first; {@code query} filters title and text (empty = all). */
    List<Note> notes(String query) {
        String where = null;
        String[] args = null;
        if (query != null && !query.isEmpty()) {
            where = "title LIKE ? OR plain LIKE ?";
            String like = "%" + query + "%";
            args = new String[]{like, like};
        }
        List<Note> out = new ArrayList<>();
        Cursor c = getReadableDatabase().query("notes", NOTE_COLS, where, args, null, null,
                "updated DESC, id DESC");
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
        ContentValues v = new ContentValues();
        v.put("title", title);
        v.put("body", body);
        v.put("plain", plain);
        v.put("updated", System.currentTimeMillis());
        if (id <= 0) return getWritableDatabase().insert("notes", null, v);
        getWritableDatabase().update("notes", v, "id = ?", new String[]{String.valueOf(id)});
        return id;
    }

    void deleteNote(long id) {
        getWritableDatabase().delete("notes", "id = ?", new String[]{String.valueOf(id)});
    }
}
