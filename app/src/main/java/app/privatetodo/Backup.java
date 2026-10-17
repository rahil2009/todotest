package app.privatetodo;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Export / import of every task, subtask and note as one JSON file you choose where to keep. */
final class Backup {
    private static final String[] TABLES = {"tasks", "subtasks", "notes"};

    private Backup() {
    }

    static String export(Context ctx) throws JSONException {
        TaskDb helper = new TaskDb(ctx);
        try {
            SQLiteDatabase db = helper.getReadableDatabase();
            JSONObject root = new JSONObject();
            root.put("app", "privatetodo");
            root.put("format", 1);
            root.put("exported", System.currentTimeMillis());
            for (String table : TABLES) {
                JSONArray rows = new JSONArray();
                Cursor c = db.rawQuery("SELECT * FROM " + table, null);
                try {
                    String[] names = c.getColumnNames();
                    while (c.moveToNext()) {
                        JSONObject o = new JSONObject();
                        for (int i = 0; i < names.length; i++) {
                            switch (c.getType(i)) {
                                case Cursor.FIELD_TYPE_INTEGER:
                                    o.put(names[i], c.getLong(i));
                                    break;
                                case Cursor.FIELD_TYPE_FLOAT:
                                    o.put(names[i], c.getDouble(i));
                                    break;
                                case Cursor.FIELD_TYPE_STRING:
                                    o.put(names[i], c.getString(i));
                                    break;
                                default:
                                    break;
                            }
                        }
                        rows.put(o);
                    }
                } finally {
                    c.close();
                }
                root.put(table, rows);
            }
            return root.toString();
        } finally {
            helper.close();
        }
    }

    /** Replaces everything in the app with the backup's content. Returns the number of tasks, or -1 if the file is not a backup. */
    static int restore(Context ctx, String json) {
        JSONObject root;
        try {
            root = new JSONObject(json);
        } catch (JSONException e) {
            return -1;
        }
        if (!"privatetodo".equals(root.optString("app")) || root.optJSONArray("tasks") == null) return -1;

        TaskDb helper = new TaskDb(ctx);
        try {
            SQLiteDatabase db = helper.getWritableDatabase();
            db.beginTransaction();
            try {
                for (String table : TABLES) {
                    db.delete(table, null, null);
                    JSONArray rows = root.optJSONArray(table);
                    if (rows == null) continue;
                    String[] known;
                    Cursor c = db.rawQuery("SELECT * FROM " + table + " LIMIT 0", null);
                    try {
                        known = c.getColumnNames();
                    } finally {
                        c.close();
                    }
                    for (int i = 0; i < rows.length(); i++) {
                        JSONObject o = rows.optJSONObject(i);
                        if (o == null) continue;
                        ContentValues v = new ContentValues();
                        for (String col : known) {
                            if (!o.has(col) || o.isNull(col)) continue;
                            Object val = o.get(col);
                            if (val instanceof Number) {
                                if (val instanceof Double) v.put(col, ((Number) val).doubleValue());
                                else v.put(col, ((Number) val).longValue());
                            } else {
                                v.put(col, val.toString());
                            }
                        }
                        db.insert(table, null, v);
                    }
                }
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
            // Arm the reminders that were in the backup.
            for (Task t : helper.withReminders()) Reminders.schedule(ctx, t);
            return root.getJSONArray("tasks").length();
        } catch (Exception e) {
            return -1;
        } finally {
            helper.close();
        }
    }
}
