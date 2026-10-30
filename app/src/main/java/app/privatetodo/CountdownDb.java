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
import java.util.List;

/** Countdowns: events with a date (exams, trips, launches) and how many days are left. Local only. */
final class CountdownDb extends SQLiteOpenHelper {
    /** Reminder choices, as bits of {@link Item#mask}. */
    static final int ON_DAY = 1;
    static final int DAY_BEFORE = 2;
    static final int THREE_DAYS = 4;
    static final int WEEK = 8;
    static final int[] BITS = {ON_DAY, DAY_BEFORE, THREE_DAYS, WEEK};
    static final int[] OFFSETS = {0, 1, 3, 7};
    static final String[] BIT_NAMES = {"On the day", "1 day before", "3 days before", "1 week before"};

    static final class Item {
        long id;
        String title = "";
        String emoji = "";
        long day;
        int minutes = -1; // time of the event, -1 = all day
        String note = "";
        int mask = ON_DAY | DAY_BEFORE;
        int remindMin = 540;
        long created;

        long daysLeft() {
            return day - today();
        }
    }

    private final Context appCtx;

    CountdownDb(Context c) {
        super(c.getApplicationContext(), "countdown.db", null, 1);
        appCtx = c.getApplicationContext();
    }

    /** Every write goes through here, so the home-screen widgets are told shortly after. */
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
        db.execSQL("CREATE TABLE cd (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL,"
                + "emoji TEXT NOT NULL DEFAULT '', day INTEGER NOT NULL, minutes INTEGER NOT NULL DEFAULT -1,"
                + "note TEXT NOT NULL DEFAULT '', mask INTEGER NOT NULL DEFAULT 3,"
                + "remind_min INTEGER NOT NULL DEFAULT 540, created INTEGER NOT NULL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int o, int n) {
    }

    private static final String COLS = "id,title,emoji,day,minutes,note,mask,remind_min,created";

    private static Item read(Cursor c) {
        Item i = new Item();
        i.id = c.getLong(0);
        i.title = c.getString(1);
        i.emoji = c.getString(2);
        i.day = c.getLong(3);
        i.minutes = c.getInt(4);
        i.note = c.getString(5);
        i.mask = c.getInt(6);
        i.remindMin = c.getInt(7);
        i.created = c.getLong(8);
        return i;
    }

    /** Coming up first (soonest on top), then the ones that have passed (most recent first). */
    List<Item> all() {
        List<Item> up = new ArrayList<>();
        List<Item> past = new ArrayList<>();
        long t = today();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT " + COLS + " FROM cd ORDER BY day ASC, id ASC", null)) {
            while (c.moveToNext()) {
                Item i = read(c);
                if (i.day >= t) up.add(i);
                else past.add(0, i);
            }
        }
        up.addAll(past);
        return up;
    }

    Item get(long id) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT " + COLS + " FROM cd WHERE id=?",
                new String[]{String.valueOf(id)})) {
            return c.moveToFirst() ? read(c) : null;
        }
    }

    private static ContentValues values(Item i) {
        ContentValues v = new ContentValues();
        v.put("title", i.title);
        v.put("emoji", i.emoji);
        v.put("day", i.day);
        v.put("minutes", i.minutes);
        v.put("note", i.note);
        v.put("mask", i.mask);
        v.put("remind_min", i.remindMin);
        v.put("created", i.created > 0 ? i.created : System.currentTimeMillis());
        return v;
    }

    long save(Item i) {
        if (i.id > 0) {
            getWritableDatabase().update("cd", values(i), "id=?", new String[]{String.valueOf(i.id)});
            return i.id;
        }
        i.id = getWritableDatabase().insert("cd", null, values(i));
        return i.id;
    }

    void delete(long id) {
        getWritableDatabase().delete("cd", "id=?", new String[]{String.valueOf(id)});
    }

    // ------------------------------------------------------------------ backup

    static JSONObject export(Context ctx) {
        CountdownDb h = new CountdownDb(ctx);
        JSONObject o = new JSONObject();
        try {
            JSONArray a = new JSONArray();
            for (Item i : h.all()) {
                JSONObject j = new JSONObject();
                j.put("id", i.id);
                j.put("title", i.title);
                j.put("emoji", i.emoji);
                j.put("day", i.day);
                j.put("min", i.minutes);
                j.put("note", i.note);
                j.put("mask", i.mask);
                j.put("rm", i.remindMin);
                j.put("created", i.created);
                a.put(j);
            }
            o.put("items", a);
        } catch (Exception ignored) {
            // an export without countdowns is still a valid backup
        } finally {
            h.close();
        }
        return o;
    }

    static void restore(Context ctx, JSONObject o) {
        if (o == null) return;
        CountdownDb h = new CountdownDb(ctx);
        try {
            SQLiteDatabase db = h.getWritableDatabase();
            db.beginTransaction();
            try {
                db.delete("cd", null, null);
                JSONArray a = o.optJSONArray("items");
                for (int k = 0; a != null && k < a.length(); k++) {
                    JSONObject j = a.getJSONObject(k);
                    Item i = new Item();
                    i.title = j.optString("title", "");
                    i.emoji = j.optString("emoji", "");
                    i.day = j.optLong("day", today());
                    i.minutes = j.optInt("min", -1);
                    i.note = j.optString("note", "");
                    i.mask = j.optInt("mask", ON_DAY);
                    i.remindMin = j.optInt("rm", 540);
                    i.created = j.optLong("created", System.currentTimeMillis());
                    ContentValues v = values(i);
                    v.put("id", j.getLong("id"));
                    db.insert("cd", null, v);
                }
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        } catch (Exception ignored) {
            // a backup from before Countdowns has nothing to restore here
        } finally {
            h.close();
        }
        CountdownRemind.scheduleAll(ctx);
    }
}
