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

/**
 * Lent and borrowed things, in their own local database. A day is the number of days since 1970 (local time).
 * Money is kept in hundredths (cents) so nothing is ever rounded.
 */
final class LentDb extends SQLiteOpenHelper {
    /** dir: 0 = you lent it out, 1 = you borrowed it. kind: 0 = money, 1 = a thing. */
    static final class Loan {
        long id;
        String person = "";
        int dir;
        int kind;
        String title = "";
        String emoji = "";
        long amount;
        String note = "";
        long day;
        long due;
        boolean remind;
        int remindDays;
        int remindMin = 540;
        boolean closed;
        long closedOn;
        long created;
        /** Filled in by the queries: the sum of the payments so far. */
        long paid;

        boolean money() {
            return kind == 0;
        }

        long left() {
            return Math.max(0, amount - paid);
        }
    }

    static final class Pay {
        long id;
        long loan;
        long day;
        long amount;
        String note = "";
    }

    private final Context appCtx;

    LentDb(Context c) {
        super(c.getApplicationContext(), "lent.db", null, 1);
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
        db.execSQL("CREATE TABLE loans (id INTEGER PRIMARY KEY AUTOINCREMENT, person TEXT NOT NULL,"
                + "dir INTEGER NOT NULL DEFAULT 0, kind INTEGER NOT NULL DEFAULT 0, title TEXT NOT NULL DEFAULT '',"
                + "emoji TEXT NOT NULL DEFAULT '', amount INTEGER NOT NULL DEFAULT 0, note TEXT NOT NULL DEFAULT '',"
                + "day INTEGER NOT NULL, due INTEGER NOT NULL DEFAULT 0, remind INTEGER NOT NULL DEFAULT 0,"
                + "remind_days INTEGER NOT NULL DEFAULT 1, remind_min INTEGER NOT NULL DEFAULT 540,"
                + "closed INTEGER NOT NULL DEFAULT 0, closed_on INTEGER NOT NULL DEFAULT 0,"
                + "created INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE pays (id INTEGER PRIMARY KEY AUTOINCREMENT, loan INTEGER NOT NULL,"
                + "day INTEGER NOT NULL, amount INTEGER NOT NULL, note TEXT NOT NULL DEFAULT '')");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int o, int n) {
    }

    private static final String COLS = "id,person,dir,kind,title,emoji,amount,note,day,due,remind,remind_days,"
            + "remind_min,closed,closed_on,created,"
            + "COALESCE((SELECT SUM(amount) FROM pays WHERE pays.loan = loans.id),0)";

    private static Loan read(Cursor c) {
        Loan l = new Loan();
        l.id = c.getLong(0);
        l.person = c.getString(1);
        l.dir = c.getInt(2);
        l.kind = c.getInt(3);
        l.title = c.getString(4);
        l.emoji = c.getString(5);
        l.amount = c.getLong(6);
        l.note = c.getString(7);
        l.day = c.getLong(8);
        l.due = c.getLong(9);
        l.remind = c.getInt(10) != 0;
        l.remindDays = c.getInt(11);
        l.remindMin = c.getInt(12);
        l.closed = c.getInt(13) != 0;
        l.closedOn = c.getLong(14);
        l.created = c.getLong(15);
        l.paid = c.getLong(16);
        return l;
    }

    /** Every loan, newest first. */
    List<Loan> all() {
        List<Loan> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT " + COLS + " FROM loans ORDER BY day DESC, id DESC", null)) {
            while (c.moveToNext()) out.add(read(c));
        }
        return out;
    }

    Loan get(long id) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT " + COLS + " FROM loans WHERE id = ?",
                new String[]{String.valueOf(id)})) {
            return c.moveToFirst() ? read(c) : null;
        }
    }

    /** Everything lent to / borrowed from one person (names match ignoring case). */
    List<Loan> of(String person, int dir) {
        List<Loan> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT " + COLS
                        + " FROM loans WHERE LOWER(person) = LOWER(?) AND dir = ? ORDER BY day DESC, id DESC",
                new String[]{person, String.valueOf(dir)})) {
            while (c.moveToNext()) out.add(read(c));
        }
        return out;
    }

    private static ContentValues values(Loan l) {
        ContentValues v = new ContentValues();
        v.put("person", l.person);
        v.put("dir", l.dir);
        v.put("kind", l.kind);
        v.put("title", l.title);
        v.put("emoji", l.emoji);
        v.put("amount", l.amount);
        v.put("note", l.note);
        v.put("day", l.day);
        v.put("due", l.due);
        v.put("remind", l.remind ? 1 : 0);
        v.put("remind_days", l.remindDays);
        v.put("remind_min", l.remindMin);
        v.put("closed", l.closed ? 1 : 0);
        v.put("closed_on", l.closedOn);
        v.put("created", l.created == 0 ? System.currentTimeMillis() : l.created);
        return v;
    }

    /** Inserts (id 0) or updates; returns the loan's id. A money loan is closed once it has been paid back. */
    long save(Loan l) {
        long id = l.id;
        if (id > 0) {
            getWritableDatabase().update("loans", values(l), "id = ?", new String[]{String.valueOf(id)});
        } else {
            id = getWritableDatabase().insert("loans", null, values(l));
            l.id = id;
        }
        if (l.money()) settle(id, today());
        return id;
    }

    void delete(long id) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("pays", "loan = ?", new String[]{String.valueOf(id)});
        db.delete("loans", "id = ?", new String[]{String.valueOf(id)});
    }

    List<Pay> pays(long loan) {
        List<Pay> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT id,loan,day,amount,note FROM pays WHERE loan = ? ORDER BY day DESC, id DESC",
                new String[]{String.valueOf(loan)})) {
            while (c.moveToNext()) {
                Pay p = new Pay();
                p.id = c.getLong(0);
                p.loan = c.getLong(1);
                p.day = c.getLong(2);
                p.amount = c.getLong(3);
                p.note = c.getString(4);
                out.add(p);
            }
        }
        return out;
    }

    long addPay(long loan, long day, long amount, String note) {
        ContentValues v = new ContentValues();
        v.put("loan", loan);
        v.put("day", day);
        v.put("amount", amount);
        v.put("note", note == null ? "" : note);
        long id = getWritableDatabase().insert("pays", null, v);
        settle(loan, day);
        return id;
    }

    void delPay(long payId) {
        long loan = -1;
        try (Cursor c = getReadableDatabase().rawQuery("SELECT loan FROM pays WHERE id = ?",
                new String[]{String.valueOf(payId)})) {
            if (c.moveToFirst()) loan = c.getLong(0);
        }
        getWritableDatabase().delete("pays", "id = ?", new String[]{String.valueOf(payId)});
        if (loan >= 0) settle(loan, today());
    }

    /** A money loan is closed exactly when what came back covers it. */
    private void settle(long id, long day) {
        Loan l = get(id);
        if (l == null || !l.money() || l.amount <= 0) return;
        boolean full = l.paid >= l.amount;
        if (full == l.closed) return;
        ContentValues v = new ContentValues();
        v.put("closed", full ? 1 : 0);
        v.put("closed_on", full ? day : 0);
        getWritableDatabase().update("loans", v, "id = ?", new String[]{String.valueOf(id)});
    }

    /** Marks a loan returned: money gets a payment for what was left, a thing is simply closed. */
    void markReturned(long id, long day) {
        Loan l = get(id);
        if (l == null || l.closed) return;
        if (l.money() && l.left() > 0) {
            addPay(id, day, l.left(), "Returned in full");
            return;
        }
        ContentValues v = new ContentValues();
        v.put("closed", 1);
        v.put("closed_on", day);
        getWritableDatabase().update("loans", v, "id = ?", new String[]{String.valueOf(id)});
    }

    void reopen(long id) {
        ContentValues v = new ContentValues();
        v.put("closed", 0);
        v.put("closed_on", 0);
        getWritableDatabase().update("loans", v, "id = ?", new String[]{String.valueOf(id)});
    }

    void rename(String from, int dir, String to) {
        ContentValues v = new ContentValues();
        v.put("person", to);
        getWritableDatabase().update("loans", v, "LOWER(person) = LOWER(?) AND dir = ?",
                new String[]{from, String.valueOf(dir)});
    }

    /** Names used before, most recent first, for the quick chips in the form. */
    List<String> people(int dir) {
        List<String> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT person, MAX(created) m FROM loans WHERE dir = ? GROUP BY LOWER(person) ORDER BY m DESC LIMIT 8",
                new String[]{String.valueOf(dir)})) {
            while (c.moveToNext()) out.add(c.getString(0));
        }
        return out;
    }

    // ------------------------------------------------------------------ backup

    static JSONObject export(Context ctx) {
        LentDb h = new LentDb(ctx);
        JSONObject o = new JSONObject();
        try {
            JSONArray ls = new JSONArray();
            for (Loan l : h.all()) {
                JSONObject j = new JSONObject();
                j.put("id", l.id);
                j.put("person", l.person);
                j.put("dir", l.dir);
                j.put("kind", l.kind);
                j.put("title", l.title);
                j.put("emoji", l.emoji);
                j.put("amount", l.amount);
                j.put("note", l.note);
                j.put("day", l.day);
                j.put("due", l.due);
                j.put("remind", l.remind);
                j.put("rd", l.remindDays);
                j.put("rm", l.remindMin);
                j.put("closed", l.closed);
                j.put("closedOn", l.closedOn);
                j.put("created", l.created);
                ls.put(j);
            }
            JSONArray ps = new JSONArray();
            try (Cursor c = h.getReadableDatabase().rawQuery("SELECT id,loan,day,amount,note FROM pays", null)) {
                while (c.moveToNext()) {
                    JSONObject j = new JSONObject();
                    j.put("id", c.getLong(0));
                    j.put("loan", c.getLong(1));
                    j.put("day", c.getLong(2));
                    j.put("amount", c.getLong(3));
                    j.put("note", c.getString(4));
                    ps.put(j);
                }
            }
            o.put("loans", ls);
            o.put("pays", ps);
        } catch (Exception ignored) {
            // an export without lent items is still a valid backup
        } finally {
            h.close();
        }
        return o;
    }

    static void restore(Context ctx, JSONObject o) {
        if (o == null) return;
        LentDb h = new LentDb(ctx);
        try {
            SQLiteDatabase db = h.getWritableDatabase();
            db.beginTransaction();
            try {
                db.delete("loans", null, null);
                db.delete("pays", null, null);
                JSONArray ls = o.optJSONArray("loans");
                for (int i = 0; ls != null && i < ls.length(); i++) {
                    JSONObject j = ls.getJSONObject(i);
                    Loan l = new Loan();
                    l.person = j.optString("person", "");
                    l.dir = j.optInt("dir", 0);
                    l.kind = j.optInt("kind", 0);
                    l.title = j.optString("title", "");
                    l.emoji = j.optString("emoji", "");
                    l.amount = j.optLong("amount", 0);
                    l.note = j.optString("note", "");
                    l.day = j.optLong("day", today());
                    l.due = j.optLong("due", 0);
                    l.remind = j.optBoolean("remind", false);
                    l.remindDays = j.optInt("rd", 1);
                    l.remindMin = j.optInt("rm", 540);
                    l.closed = j.optBoolean("closed", false);
                    l.closedOn = j.optLong("closedOn", 0);
                    l.created = j.optLong("created", System.currentTimeMillis());
                    ContentValues v = values(l);
                    v.put("id", j.getLong("id"));
                    db.insert("loans", null, v);
                }
                JSONArray ps = o.optJSONArray("pays");
                for (int i = 0; ps != null && i < ps.length(); i++) {
                    JSONObject j = ps.getJSONObject(i);
                    ContentValues v = new ContentValues();
                    v.put("id", j.getLong("id"));
                    v.put("loan", j.getLong("loan"));
                    v.put("day", j.getLong("day"));
                    v.put("amount", j.getLong("amount"));
                    v.put("note", j.optString("note", ""));
                    db.insert("pays", null, v);
                }
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        } catch (Exception ignored) {
            // a backup from before Lent has nothing to restore here
        } finally {
            h.close();
        }
    }
}
