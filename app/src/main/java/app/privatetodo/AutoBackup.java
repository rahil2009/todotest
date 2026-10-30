package app.privatetodo;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Daily backup to a folder the person chose (Android's own folder picker: no storage permission, no network).
 * One file per day, the last few kept. An alarm runs it around 3 am; opening the app also catches up when a day was missed.
 */
final class AutoBackup {
    private AutoBackup() { }

    static final String ACTION = "app.privatetodo.AUTO_BACKUP";
    private static final String PREFIX = "private-todo-auto-";
    private static final AtomicBoolean RUNNING = new AtomicBoolean();

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("autobackup", Context.MODE_PRIVATE);
    }

    static boolean on(Context c) {
        return sp(c).getBoolean("on", false) && tree(c) != null;
    }

    static void setOn(Context c, boolean v) {
        sp(c).edit().putBoolean("on", v).apply();
        schedule(c);
    }

    static Uri tree(Context c) {
        String s = sp(c).getString("tree", null);
        return s == null ? null : Uri.parse(s);
    }

    static void setTree(Context c, Uri u) {
        sp(c).edit().putString("tree", u.toString()).apply();
    }

    static int keep(Context c) {
        return sp(c).getInt("keep", 7);
    }

    static void setKeep(Context c, int n) {
        sp(c).edit().putInt("keep", n).apply();
    }

    static long last(Context c) {
        return sp(c).getLong("last", 0);
    }

    static String status(Context c) {
        return sp(c).getString("status", "");
    }

    /** "Download/Backups" for a folder picked in the file app. */
    static String folderName(Context c) {
        Uri t = tree(c);
        if (t == null) return "Not chosen";
        try {
            String id = DocumentsContract.getTreeDocumentId(t); // "primary:Download/Backups"
            int i = id.indexOf(':');
            String name = i >= 0 ? id.substring(i + 1) : id;
            return name.isEmpty() ? "Internal storage" : name;
        } catch (RuntimeException e) {
            return "Chosen folder";
        }
    }

    private static PendingIntent pending(Context c) {
        Intent i = new Intent(c, AutoBackupReceiver.class).setAction(ACTION).setData(Uri.parse("todo://autobackup"));
        return PendingIntent.getBroadcast(c, 0, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /** Arms (or cancels) the next run: the coming 3 am, give or take what Android allows. */
    static void schedule(Context c) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        PendingIntent p = pending(c);
        am.cancel(p);
        if (!on(c)) return;
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.set(java.util.Calendar.HOUR_OF_DAY, 3);
        cal.set(java.util.Calendar.MINUTE, 10);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() <= System.currentTimeMillis() + 60_000L) cal.add(java.util.Calendar.DAY_OF_YEAR, 1);
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), p);
    }

    /** Opening the app after a missed night (phone off, alarm killed): back up now, in the background. */
    static void catchUp(final Context c) {
        if (!on(c)) return;
        if (System.currentTimeMillis() - last(c) < 22L * 3_600_000L) return;
        final Context app = c.getApplicationContext();
        new Thread(() -> run(app), "auto-backup").start();
    }

    /** Writes today's file and trims the old ones. Call off the main thread. */
    static boolean run(Context c) {
        if (!RUNNING.compareAndSet(false, true)) return false;
        SharedPreferences.Editor e = sp(c).edit();
        try {
            Uri tree = tree(c);
            if (tree == null) throw new IllegalStateException("No folder chosen");
            android.content.ContentResolver cr = c.getContentResolver();
            String rootId = DocumentsContract.getTreeDocumentId(tree);
            Uri parent = DocumentsContract.buildDocumentUriUsingTree(tree, rootId);
            String name = PREFIX + new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()) + ".json";

            // today's file may exist already (a second run the same day): replace it
            List<String[]> mine = list(c, tree, rootId); // {docId, name}
            for (String[] f : mine) {
                if (f[1].equals(name)) DocumentsContract.deleteDocument(cr, DocumentsContract.buildDocumentUriUsingTree(tree, f[0]));
            }
            Uri out = DocumentsContract.createDocument(cr, parent, "application/json", name);
            if (out == null) throw new IllegalStateException("Could not create the file");
            try (OutputStream os = cr.openOutputStream(out, "wt")) {
                os.write(Backup.export(c).getBytes(StandardCharsets.UTF_8));
            }

            // keep the newest few (names sort by date)
            mine = list(c, tree, rootId);
            Collections.sort(mine, (a, b) -> a[1].compareTo(b[1]));
            int extra = mine.size() - Math.max(1, keep(c));
            for (int i = 0; i < extra; i++) {
                DocumentsContract.deleteDocument(cr, DocumentsContract.buildDocumentUriUsingTree(tree, mine.get(i)[0]));
            }
            e.putLong("last", System.currentTimeMillis()).putString("status", "OK").apply();
            return true;
        } catch (SecurityException ex) {
            e.putString("status", "The folder is no longer available. Choose it again.").apply();
            return false;
        } catch (Exception ex) {
            e.putString("status", "Failed: " + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage())).apply();
            return false;
        } finally {
            RUNNING.set(false);
        }
    }

    private static List<String[]> list(Context c, Uri tree, String rootId) {
        List<String[]> out = new ArrayList<>();
        Uri kids = DocumentsContract.buildChildDocumentsUriUsingTree(tree, rootId);
        try (Cursor q = c.getContentResolver().query(kids, new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null)) {
            while (q != null && q.moveToNext()) {
                String n = q.getString(1);
                if (n != null && n.startsWith(PREFIX) && n.endsWith(".json")) out.add(new String[]{q.getString(0), n});
            }
        }
        return out;
    }
}
