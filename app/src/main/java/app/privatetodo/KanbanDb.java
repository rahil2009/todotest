package app.privatetodo;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Kanban boards: boards hold columns, columns hold cards; a card can point at a task and at a note. Local only. */
final class KanbanDb extends SQLiteOpenHelper {
    static final class Board {
        long id;
        String name = "";
        String emoji = "";
        long created;
        int cards;
        int done; // cards in the last column
    }

    static final class Col {
        long id;
        long board;
        String name = "";
        int sort;
    }

    static final class Card {
        long id;
        long col;
        String title = "";
        String note = "";
        int sort;
        long taskId;  // linked task, 0 = none
        long noteId;  // linked note, 0 = none
        long created;
    }

    private final Context appCtx;

    KanbanDb(Context c) {
        super(c.getApplicationContext(), "kanban.db", null, 1);
        appCtx = c.getApplicationContext();
    }

    @Override
    public SQLiteDatabase getWritableDatabase() {
        TaskWidget.refreshSoon(appCtx);
        return super.getWritableDatabase();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE boards (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL,"
                + "emoji TEXT NOT NULL DEFAULT '', created INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE cols (id INTEGER PRIMARY KEY AUTOINCREMENT, board INTEGER NOT NULL,"
                + "name TEXT NOT NULL, sort INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE cards (id INTEGER PRIMARY KEY AUTOINCREMENT, col INTEGER NOT NULL,"
                + "title TEXT NOT NULL, note TEXT NOT NULL DEFAULT '', sort INTEGER NOT NULL DEFAULT 0,"
                + "task_id INTEGER NOT NULL DEFAULT 0, note_id INTEGER NOT NULL DEFAULT 0, created INTEGER NOT NULL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int o, int n) {
    }

    // ------------------------------------------------------------------ boards

    List<Board> boards() {
        List<Board> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT id,name,emoji,created FROM boards ORDER BY id", null)) {
            while (c.moveToNext()) {
                Board b = new Board();
                b.id = c.getLong(0);
                b.name = c.getString(1);
                b.emoji = c.getString(2);
                b.created = c.getLong(3);
                out.add(b);
            }
        }
        for (Board b : out) {
            List<Col> cols = columns(b.id);
            for (int i = 0; i < cols.size(); i++) {
                int n = count(cols.get(i).id);
                b.cards += n;
                if (i == cols.size() - 1 && cols.size() > 1) b.done = n;
            }
        }
        return out;
    }

    Board board(long id) {
        for (Board b : boards()) if (b.id == id) return b;
        return null;
    }

    /** A new board with the usual three columns. */
    long addBoard(String name, String emoji) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put("name", name);
        v.put("emoji", emoji);
        v.put("created", System.currentTimeMillis());
        long id = db.insert("boards", null, v);
        String[] names = {"To do", "Doing", "Done"};
        for (int i = 0; i < names.length; i++) addColumn(id, names[i]);
        return id;
    }

    void renameBoard(long id, String name, String emoji) {
        ContentValues v = new ContentValues();
        v.put("name", name);
        v.put("emoji", emoji);
        getWritableDatabase().update("boards", v, "id=?", new String[]{String.valueOf(id)});
    }

    void deleteBoard(long id) {
        SQLiteDatabase db = getWritableDatabase();
        for (Col c : columns(id)) db.delete("cards", "col=?", new String[]{String.valueOf(c.id)});
        db.delete("cols", "board=?", new String[]{String.valueOf(id)});
        db.delete("boards", "id=?", new String[]{String.valueOf(id)});
    }

    // ------------------------------------------------------------------ columns

    List<Col> columns(long board) {
        List<Col> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT id,board,name,sort FROM cols WHERE board=? ORDER BY sort,id",
                new String[]{String.valueOf(board)})) {
            while (c.moveToNext()) {
                Col k = new Col();
                k.id = c.getLong(0);
                k.board = c.getLong(1);
                k.name = c.getString(2);
                k.sort = c.getInt(3);
                out.add(k);
            }
        }
        return out;
    }

    long addColumn(long board, String name) {
        int sort = 0;
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COALESCE(MAX(sort),-1)+1 FROM cols WHERE board=?",
                new String[]{String.valueOf(board)})) {
            if (c.moveToFirst()) sort = c.getInt(0);
        }
        ContentValues v = new ContentValues();
        v.put("board", board);
        v.put("name", name);
        v.put("sort", sort);
        return getWritableDatabase().insert("cols", null, v);
    }

    void renameColumn(long id, String name) {
        ContentValues v = new ContentValues();
        v.put("name", name);
        getWritableDatabase().update("cols", v, "id=?", new String[]{String.valueOf(id)});
    }

    /** Removes a column; its cards move to {@code into} (or are deleted when into <= 0). */
    void deleteColumn(long id, long into) {
        SQLiteDatabase db = getWritableDatabase();
        if (into > 0) {
            int sort = count(into);
            for (Card c : cards(id)) {
                ContentValues v = new ContentValues();
                v.put("col", into);
                v.put("sort", sort++);
                db.update("cards", v, "id=?", new String[]{String.valueOf(c.id)});
            }
        } else {
            db.delete("cards", "col=?", new String[]{String.valueOf(id)});
        }
        db.delete("cols", "id=?", new String[]{String.valueOf(id)});
    }

    // ------------------------------------------------------------------ cards

    int count(long col) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM cards WHERE col=?",
                new String[]{String.valueOf(col)})) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    List<Card> cards(long col) {
        List<Card> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT id,col,title,note,sort,task_id,note_id,created FROM cards WHERE col=? ORDER BY sort,id",
                new String[]{String.valueOf(col)})) {
            while (c.moveToNext()) out.add(read(c));
        }
        return out;
    }

    private static Card read(Cursor c) {
        Card k = new Card();
        k.id = c.getLong(0);
        k.col = c.getLong(1);
        k.title = c.getString(2);
        k.note = c.getString(3);
        k.sort = c.getInt(4);
        k.taskId = c.getLong(5);
        k.noteId = c.getLong(6);
        k.created = c.getLong(7);
        return k;
    }

    Card card(long id) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT id,col,title,note,sort,task_id,note_id,created FROM cards WHERE id=?", new String[]{String.valueOf(id)})) {
            return c.moveToFirst() ? read(c) : null;
        }
    }

    /** Saves a card; a new one goes to the bottom of its column. */
    long save(Card k) {
        ContentValues v = new ContentValues();
        v.put("col", k.col);
        v.put("title", k.title);
        v.put("note", k.note);
        v.put("task_id", k.taskId);
        v.put("note_id", k.noteId);
        if (k.id > 0) {
            getWritableDatabase().update("cards", v, "id=?", new String[]{String.valueOf(k.id)});
            return k.id;
        }
        v.put("sort", count(k.col));
        v.put("created", System.currentTimeMillis());
        k.id = getWritableDatabase().insert("cards", null, v);
        return k.id;
    }

    void delete(long id) {
        getWritableDatabase().delete("cards", "id=?", new String[]{String.valueOf(id)});
    }

    /** Moves a card to column {@code col}, to position {@code pos} (0 = top), and renumbers both columns. */
    void move(long cardId, long col, int pos) {
        Card k = card(cardId);
        if (k == null) return;
        long from = k.col;
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            List<Card> target = cards(col);
            for (int i = target.size() - 1; i >= 0; i--) if (target.get(i).id == cardId) target.remove(i);
            pos = Math.max(0, Math.min(target.size(), pos));
            target.add(pos, k);
            for (int i = 0; i < target.size(); i++) {
                ContentValues v = new ContentValues();
                v.put("col", col);
                v.put("sort", i);
                db.update("cards", v, "id=?", new String[]{String.valueOf(target.get(i).id)});
            }
            if (from != col) {
                List<Card> rest = cards(from);
                for (int i = 0; i < rest.size(); i++) {
                    ContentValues v = new ContentValues();
                    v.put("sort", i);
                    db.update("cards", v, "id=?", new String[]{String.valueOf(rest.get(i).id)});
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    // ------------------------------------------------------------------ backup

    static JSONObject export(Context ctx) {
        KanbanDb h = new KanbanDb(ctx);
        JSONObject o = new JSONObject();
        try {
            SQLiteDatabase db = h.getReadableDatabase();
            JSONArray bs = new JSONArray();
            try (Cursor c = db.rawQuery("SELECT id,name,emoji,created FROM boards", null)) {
                while (c.moveToNext()) {
                    JSONObject j = new JSONObject();
                    j.put("id", c.getLong(0));
                    j.put("name", c.getString(1));
                    j.put("emoji", c.getString(2));
                    j.put("created", c.getLong(3));
                    bs.put(j);
                }
            }
            JSONArray cs = new JSONArray();
            try (Cursor c = db.rawQuery("SELECT id,board,name,sort FROM cols", null)) {
                while (c.moveToNext()) {
                    JSONObject j = new JSONObject();
                    j.put("id", c.getLong(0));
                    j.put("board", c.getLong(1));
                    j.put("name", c.getString(2));
                    j.put("sort", c.getInt(3));
                    cs.put(j);
                }
            }
            JSONArray ks = new JSONArray();
            try (Cursor c = db.rawQuery("SELECT id,col,title,note,sort,task_id,note_id,created FROM cards", null)) {
                while (c.moveToNext()) {
                    JSONObject j = new JSONObject();
                    j.put("id", c.getLong(0));
                    j.put("col", c.getLong(1));
                    j.put("title", c.getString(2));
                    j.put("note", c.getString(3));
                    j.put("sort", c.getInt(4));
                    j.put("task", c.getLong(5));
                    j.put("nt", c.getLong(6));
                    j.put("created", c.getLong(7));
                    ks.put(j);
                }
            }
            o.put("boards", bs);
            o.put("cols", cs);
            o.put("cards", ks);
        } catch (Exception ignored) {
            // an export without boards is still a valid backup
        } finally {
            h.close();
        }
        return o;
    }

    static void restore(Context ctx, JSONObject o) {
        if (o == null) return;
        KanbanDb h = new KanbanDb(ctx);
        try {
            SQLiteDatabase db = h.getWritableDatabase();
            db.beginTransaction();
            try {
                db.delete("boards", null, null);
                db.delete("cols", null, null);
                db.delete("cards", null, null);
                JSONArray bs = o.optJSONArray("boards");
                for (int i = 0; bs != null && i < bs.length(); i++) {
                    JSONObject j = bs.getJSONObject(i);
                    ContentValues v = new ContentValues();
                    v.put("id", j.getLong("id"));
                    v.put("name", j.optString("name", ""));
                    v.put("emoji", j.optString("emoji", ""));
                    v.put("created", j.optLong("created", System.currentTimeMillis()));
                    db.insert("boards", null, v);
                }
                JSONArray cs = o.optJSONArray("cols");
                for (int i = 0; cs != null && i < cs.length(); i++) {
                    JSONObject j = cs.getJSONObject(i);
                    ContentValues v = new ContentValues();
                    v.put("id", j.getLong("id"));
                    v.put("board", j.getLong("board"));
                    v.put("name", j.optString("name", ""));
                    v.put("sort", j.optInt("sort", 0));
                    db.insert("cols", null, v);
                }
                JSONArray ks = o.optJSONArray("cards");
                for (int i = 0; ks != null && i < ks.length(); i++) {
                    JSONObject j = ks.getJSONObject(i);
                    ContentValues v = new ContentValues();
                    v.put("id", j.getLong("id"));
                    v.put("col", j.getLong("col"));
                    v.put("title", j.optString("title", ""));
                    v.put("note", j.optString("note", ""));
                    v.put("sort", j.optInt("sort", 0));
                    v.put("task_id", j.optLong("task", 0));
                    v.put("note_id", j.optLong("nt", 0));
                    v.put("created", j.optLong("created", System.currentTimeMillis()));
                    db.insert("cards", null, v);
                }
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        } catch (Exception ignored) {
            // a backup from before Kanban has nothing to restore here
        } finally {
            h.close();
        }
    }
}
