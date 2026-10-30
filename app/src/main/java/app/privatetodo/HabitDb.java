package app.privatetodo;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Habits and their daily log, in their own local database. A day is the number of days since 1970
 * (local time). Logged amount: >0 progress, 0 nothing, -1 skipped (neither breaks nor adds to a streak).
 */
final class HabitDb extends SQLiteOpenHelper {
    static final int[] COLORS = {0xFF4ADEB0, 0xFF60A5FA, 0xFFA78BFA, 0xFFF472B6,
            0xFFFBBF24, 0xFFFB923C, 0xFF34D399, 0xFFF87171};

    private final Context appCtx;

    HabitDb(Context c) {
        super(c.getApplicationContext(), "habits.db", null, 2);
        appCtx = c.getApplicationContext();
    }

    /** Every write goes through here, so the home-screen widgets are told shortly after (debounced, read-only refresh). */
    @Override
    public SQLiteDatabase getWritableDatabase() {
        TaskWidget.refreshSoon(appCtx);
        return super.getWritableDatabase();
    }

    static long today() {
        return LocalDate.now().toEpochDay();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE habits (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL,"
                + "emoji TEXT NOT NULL DEFAULT '', color INTEGER NOT NULL, goal INTEGER NOT NULL DEFAULT 0,"
                + "unit TEXT NOT NULL DEFAULT '', step INTEGER NOT NULL DEFAULT 1, created INTEGER NOT NULL,"
                + "sort INTEGER NOT NULL DEFAULT 0, extras TEXT NOT NULL DEFAULT '')");
        db.execSQL("CREATE TABLE log (habit INTEGER NOT NULL, day INTEGER NOT NULL, amount INTEGER NOT NULL,"
                + "PRIMARY KEY (habit, day))");
        // a starting set: water first, reading as 30 minutes
        seed(db, "Drink Water", "🥤", COLORS[0], 1000, "ml", 250, 0, "100,500");
        seed(db, "Read", "📘", COLORS[1], 30, "min", 10, 1, "5,15");
        seed(db, "Exercise", "🏋️", COLORS[2], 0, "", 1, 2, "");
    }

    private void seed(SQLiteDatabase db, String name, String emoji, int color, int goal, String unit,
                      int step, int sort, String extras) {
        ContentValues v = new ContentValues();
        v.put("name", name);
        v.put("emoji", emoji);
        v.put("color", color);
        v.put("goal", goal);
        v.put("unit", unit);
        v.put("step", step);
        v.put("created", System.currentTimeMillis());
        v.put("sort", sort);
        v.put("extras", extras);
        db.insert("habits", null, v);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int o, int n) {
        if (o < 2) db.execSQL("ALTER TABLE habits ADD COLUMN extras TEXT NOT NULL DEFAULT ''");
    }

    private static Habit read(Cursor c) {
        return new Habit(c.getLong(0), c.getString(1), c.getString(2), c.getInt(3), c.getInt(4),
                c.getString(5), c.getInt(6), c.getLong(7), c.getString(8));
    }

    private static final String COLS = "id,name,emoji,color,goal,unit,step,created,extras";

    List<Habit> list() {
        List<Habit> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT " + COLS + " FROM habits ORDER BY sort, id", null)) {
            while (c.moveToNext()) out.add(read(c));
        }
        return out;
    }

    Habit get(long id) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT " + COLS + " FROM habits WHERE id=?",
                new String[]{String.valueOf(id)})) {
            return c.moveToFirst() ? read(c) : null;
        }
    }

    long add(String name, String emoji, int color, int goal, String unit, int step, String extras) {
        ContentValues v = new ContentValues();
        v.put("name", name);
        v.put("emoji", emoji);
        v.put("color", color);
        v.put("goal", goal);
        v.put("unit", unit);
        v.put("step", Math.max(1, step));
        v.put("extras", extras == null ? "" : extras);
        v.put("created", System.currentTimeMillis());
        v.put("sort", list().size());
        return getWritableDatabase().insert("habits", null, v);
    }

    void update(long id, String name, String emoji, int color, int goal, String unit, int step, String extras) {
        ContentValues v = new ContentValues();
        v.put("name", name);
        v.put("emoji", emoji);
        v.put("color", color);
        v.put("goal", goal);
        v.put("unit", unit);
        v.put("step", Math.max(1, step));
        v.put("extras", extras == null ? "" : extras);
        getWritableDatabase().update("habits", v, "id=?", new String[]{String.valueOf(id)});
    }

    void delete(long id) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("habits", "id=?", new String[]{String.valueOf(id)});
        db.delete("log", "habit=?", new String[]{String.valueOf(id)});
    }

    int amount(long habit, long day) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT amount FROM log WHERE habit=? AND day=?",
                new String[]{String.valueOf(habit), String.valueOf(day)})) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    void setAmount(long habit, long day, int amount) {
        SQLiteDatabase db = getWritableDatabase();
        if (amount == 0) {
            db.delete("log", "habit=? AND day=?", new String[]{String.valueOf(habit), String.valueOf(day)});
            return;
        }
        ContentValues v = new ContentValues();
        v.put("habit", habit);
        v.put("day", day);
        v.put("amount", amount);
        db.insertWithOnConflict("log", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    /** Adds to a day's progress (a skipped day becomes a normal day first). */
    int add(long habit, long day, int delta) {
        int a = Math.max(0, amount(habit, day)) + delta;
        a = Math.max(0, a);
        setAmount(habit, day, a);
        return a;
    }

    Map<Long, Integer> all(long habit) {
        Map<Long, Integer> m = new HashMap<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT day, amount FROM log WHERE habit=?",
                new String[]{String.valueOf(habit)})) {
            while (c.moveToNext()) m.put(c.getLong(0), c.getInt(1));
        }
        return m;
    }

    /** Streak (now), best streak, number of completed days and the last completed day (-1 = never). */
    static final class Stats {
        int streak;
        int best;
        int total;
        long last = -1;
    }

    static Stats stats(Habit h, Map<Long, Integer> m) {
        Stats s = new Stats();
        long today = today();
        long min = Long.MAX_VALUE;
        for (Map.Entry<Long, Integer> e : m.entrySet()) {
            if (h.doneAt(e.getValue())) {
                s.total++;
                s.last = Math.max(s.last, e.getKey());
                min = Math.min(min, e.getKey());
            }
        }
        if (s.total == 0) return s;
        int cur = 0;
        for (long d = min; d <= today; d++) {
            int a = m.containsKey(d) ? m.get(d) : 0;
            if (a < 0) continue;
            if (h.doneAt(a)) {
                cur++;
                s.best = Math.max(s.best, cur);
            } else {
                cur = 0;
            }
        }
        long d = today;
        int guard = 0;
        while (guard++ < 5000) {
            int a = m.containsKey(d) ? m.get(d) : 0;
            if (a < 0) {
                d--;
            } else if (h.doneAt(a)) {
                s.streak++;
                d--;
            } else if (d == today) {
                d--; // today is still open: it does not break the streak yet
            } else {
                break;
            }
        }
        return s;
    }

    // ------------------------------------------------------------------ backup

    static JSONObject export(Context ctx) {
        HabitDb h = new HabitDb(ctx);
        JSONObject o = new JSONObject();
        try {
            JSONArray hs = new JSONArray();
            for (Habit x : h.list()) {
                JSONObject j = new JSONObject();
                j.put("id", x.id);
                j.put("name", x.name);
                j.put("emoji", x.emoji);
                j.put("color", x.color);
                j.put("goal", x.goal);
                j.put("unit", x.unit);
                j.put("step", x.step);
                j.put("created", x.created);
                j.put("extras", x.extras);
                hs.put(j);
            }
            JSONArray log = new JSONArray();
            try (Cursor c = h.getReadableDatabase().rawQuery("SELECT habit, day, amount FROM log", null)) {
                while (c.moveToNext()) {
                    JSONObject j = new JSONObject();
                    j.put("h", c.getLong(0));
                    j.put("d", c.getLong(1));
                    j.put("a", c.getInt(2));
                    log.put(j);
                }
            }
            o.put("habits", hs);
            o.put("log", log);
        } catch (Exception ignored) {
            // an export without habits is still a valid backup
        } finally {
            h.close();
        }
        return o;
    }

    static void restore(Context ctx, JSONObject o) {
        if (o == null) return;
        HabitDb h = new HabitDb(ctx);
        try {
            SQLiteDatabase db = h.getWritableDatabase();
            db.beginTransaction();
            try {
                db.delete("habits", null, null);
                db.delete("log", null, null);
                JSONArray hs = o.optJSONArray("habits");
                for (int i = 0; hs != null && i < hs.length(); i++) {
                    JSONObject j = hs.getJSONObject(i);
                    ContentValues v = new ContentValues();
                    v.put("id", j.getLong("id"));
                    v.put("name", j.getString("name"));
                    v.put("emoji", j.optString("emoji", ""));
                    v.put("color", j.optInt("color", COLORS[0]));
                    v.put("goal", j.optInt("goal", 0));
                    v.put("unit", j.optString("unit", ""));
                    v.put("step", j.optInt("step", 1));
                    v.put("created", j.optLong("created", System.currentTimeMillis()));
                    v.put("sort", i);
                    v.put("extras", j.optString("extras", ""));
                    db.insert("habits", null, v);
                }
                JSONArray log = o.optJSONArray("log");
                for (int i = 0; log != null && i < log.length(); i++) {
                    JSONObject j = log.getJSONObject(i);
                    ContentValues v = new ContentValues();
                    v.put("habit", j.getLong("h"));
                    v.put("day", j.getLong("d"));
                    v.put("amount", j.getInt("a"));
                    db.insertWithOnConflict("log", null, v, SQLiteDatabase.CONFLICT_REPLACE);
                }
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        } catch (Exception ignored) {
            // leave habits as they were
        } finally {
            h.close();
        }
    }
}
