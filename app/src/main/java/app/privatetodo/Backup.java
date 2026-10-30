package app.privatetodo;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Export / import of everything: tasks, subtasks, repeat ticks, notes, habits, Pomodoro stats and every setting, as one JSON file you choose where to keep. */
final class Backup {
    private static final String[] TABLES = {"tasks", "subtasks", "notes", "occ_done", "folders", "shared",
            "note_versions"};

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
            root.put("habitData", HabitDb.export(ctx));
            root.put("lentData", LentDb.export(ctx));
            root.put("countdownData", CountdownDb.export(ctx));
            root.put("kanbanData", KanbanDb.export(ctx));
            JSONObject prefs = new JSONObject();
            prefs.put("settings", exportPrefs(ctx.getApplicationContext().getSharedPreferences("settings", Context.MODE_PRIVATE), null));
            prefs.put("pomodoro", exportPrefs(ctx.getApplicationContext().getSharedPreferences("pomodoro", Context.MODE_PRIVATE), POMO_LIVE));
            root.put("prefs", prefs);
            return root.toString();
        } finally {
            helper.close();
        }
    }

    /** Pomodoro keys that describe a timer running right now; they are not worth restoring. */
    private static final java.util.Set<String> POMO_LIVE = new java.util.HashSet<>(
            java.util.Arrays.asList("running", "began", "end", "left", "phase", "round", "task"));

    private static JSONObject exportPrefs(android.content.SharedPreferences sp, java.util.Set<String> skip)
            throws JSONException {
        JSONObject o = new JSONObject();
        for (java.util.Map.Entry<String, ?> e : sp.getAll().entrySet()) {
            if (skip != null && skip.contains(e.getKey())) continue;
            Object v = e.getValue();
            JSONObject item = new JSONObject();
            if (v instanceof Boolean) {
                item.put("t", "b");
                item.put("v", (Boolean) v);
            } else if (v instanceof Integer) {
                item.put("t", "i");
                item.put("v", (Integer) v);
            } else if (v instanceof Long) {
                item.put("t", "l");
                item.put("v", (Long) v);
            } else if (v instanceof Float) {
                item.put("t", "f");
                item.put("v", ((Float) v).doubleValue());
            } else if (v instanceof String) {
                item.put("t", "s");
                item.put("v", (String) v);
            } else {
                continue;
            }
            o.put(e.getKey(), item);
        }
        return o;
    }

    private static void restorePrefs(android.content.SharedPreferences sp, JSONObject o, java.util.Set<String> keep) {
        if (o == null) return;
        android.content.SharedPreferences.Editor ed = sp.edit();
        for (String k : sp.getAll().keySet()) {
            if (keep == null || !keep.contains(k)) ed.remove(k);
        }
        java.util.Iterator<String> it = o.keys();
        while (it.hasNext()) {
            String k = it.next();
            JSONObject item = o.optJSONObject(k);
            if (item == null) continue;
            switch (item.optString("t")) {
                case "b": ed.putBoolean(k, item.optBoolean("v")); break;
                case "i": ed.putInt(k, item.optInt("v")); break;
                case "l": ed.putLong(k, item.optLong("v")); break;
                case "f": ed.putFloat(k, (float) item.optDouble("v")); break;
                case "s": ed.putString(k, item.optString("v")); break;
                default: break;
            }
        }
        ed.apply();
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
                    JSONArray rows = root.optJSONArray(table);
                    // an older backup has no note history: keep the history this phone already has
                    if (rows == null && table.equals("note_versions")) continue;
                    db.delete(table, null, null);
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
            if (root.optJSONObject("habitData") != null) HabitDb.restore(ctx, root.optJSONObject("habitData"));
            if (root.optJSONObject("lentData") != null) LentDb.restore(ctx, root.optJSONObject("lentData"));
            if (root.optJSONObject("countdownData") != null) CountdownDb.restore(ctx, root.optJSONObject("countdownData"));
            if (root.optJSONObject("kanbanData") != null) KanbanDb.restore(ctx, root.optJSONObject("kanbanData"));
            JSONObject prefs = root.optJSONObject("prefs");
            if (prefs != null) {
                restorePrefs(ctx.getApplicationContext().getSharedPreferences("settings", Context.MODE_PRIVATE),
                        prefs.optJSONObject("settings"), null);
                restorePrefs(ctx.getApplicationContext().getSharedPreferences("pomodoro", Context.MODE_PRIVATE),
                        prefs.optJSONObject("pomodoro"), POMO_LIVE);
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
