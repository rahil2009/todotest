package app.privatetodo;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.RemoteViews;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Home-screen widgets. All of them are drawn from the local database only; nothing leaves the phone.
 *  - "Tasks": any of the app's filters (Today, Tomorrow, Upcoming, Active, Priority, All). Tap the title to
 *    switch filter, tick a circle to finish a task, "+" to add one.
 *  - "Quick add": one tap opens the app with the keyboard ready.
 *  - Notebook and Plan widgets live in {@link NotesWidget} and {@link PlanWidget}.
 */
public class TaskWidget extends AppWidgetProvider {
    static final String ACTION_DONE = "app.privatetodo.WIDGET_DONE";
    static final String ACTION_CYCLE = "app.privatetodo.WIDGET_CYCLE";
    static final String EXTRA_ID = "id";
    static final String EXTRA_WIDGET = "widget";
    static final String EXTRA_ADD = "add";
    static final int ROWS = 6;

    static final int[] FILTERS = {TaskDb.TODAY, TaskDb.TOMORROW, TaskDb.UPCOMING, TaskDb.ACTIVE,
            TaskDb.PRIORITY, TaskDb.ALL};
    static final String[] NAMES = {"Today", "Tomorrow", "Upcoming", "Active", "Priority", "All"};

    private static final Handler H = new Handler(Looper.getMainLooper());

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("widgets", Context.MODE_PRIVATE);
    }

    static int filterIndex(Context c, int widgetId) {
        return Math.max(0, Math.min(FILTERS.length - 1, prefs(c).getInt("f" + widgetId, 0)));
    }

    static void setFilterIndex(Context c, int widgetId, int idx) {
        prefs(c).edit().putInt("f" + widgetId, idx).apply();
    }

    /** Redraws every widget shortly after the data changed. Cheap and safe to call often. */
    static void refreshSoon(final Context ctx) {
        final Context app = ctx.getApplicationContext();
        H.removeCallbacksAndMessages(null);
        H.postDelayed(() -> refresh(app), 300);
    }

    static void refresh(Context ctx) {
        try {
            AppWidgetManager m = AppWidgetManager.getInstance(ctx);
            for (int id : m.getAppWidgetIds(new ComponentName(ctx, TaskWidget.class))) {
                m.updateAppWidget(id, build(ctx, id));
            }
            for (int id : m.getAppWidgetIds(new ComponentName(ctx, QuickWidget.class))) {
                m.updateAppWidget(id, QuickWidget.build(ctx));
            }
            for (int id : m.getAppWidgetIds(new ComponentName(ctx, NotesWidget.class))) {
                m.updateAppWidget(id, NotesWidget.build(ctx));
            }
            for (int id : m.getAppWidgetIds(new ComponentName(ctx, PlanWidget.class))) {
                m.updateAppWidget(id, PlanWidget.build(ctx));
            }
        } catch (RuntimeException ignored) {
            // a widget must never crash the app
        }
    }

    @Override
    public void onUpdate(Context ctx, AppWidgetManager m, int[] ids) {
        for (int id : ids) m.updateAppWidget(id, build(ctx, id));
    }

    @Override
    public void onDeleted(Context ctx, int[] ids) {
        SharedPreferences.Editor e = prefs(ctx).edit();
        for (int id : ids) e.remove("f" + id);
        e.apply();
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        String a = intent.getAction();
        if (ACTION_DONE.equals(a)) {
            long id = intent.getLongExtra(EXTRA_ID, -1);
            if (id >= 0) {
                Reminders.complete(ctx, id);
                refresh(ctx);
            }
            return;
        }
        if (ACTION_CYCLE.equals(a)) {
            int w = intent.getIntExtra(EXTRA_WIDGET, -1);
            if (w >= 0) {
                setFilterIndex(ctx, w, (filterIndex(ctx, w) + 1) % FILTERS.length);
                AppWidgetManager.getInstance(ctx).updateAppWidget(w, build(ctx, w));
            }
            return;
        }
        super.onReceive(ctx, intent);
    }

    static RemoteViews build(Context ctx, int widgetId) {
        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget_tasks);
        int fi = filterIndex(ctx, widgetId);
        v.setTextViewText(R.id.w_title, NAMES[fi] + " \u25BE");
        Intent cycle = new Intent(ctx, TaskWidget.class).setAction(ACTION_CYCLE)
                .putExtra(EXTRA_WIDGET, widgetId).setData(Uri.parse("widget://cycle/" + widgetId));
        v.setOnClickPendingIntent(R.id.w_head, PendingIntent.getBroadcast(ctx, widgetId, cycle,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        v.setOnClickPendingIntent(R.id.w_add, open(ctx, true, 2));
        v.removeAllViews(R.id.w_rows);

        TaskDb db = new TaskDb(ctx);
        List<Task> list;
        try {
            list = db.list(FILTERS[fi]);
        } finally {
            db.close();
        }
        int shown = 0;
        int total = 0;
        SimpleDateFormat f = new SimpleDateFormat("HH:mm", Locale.getDefault());
        for (Task t : list) {
            if (t.done) continue;
            total++;
            if (shown >= ROWS) continue;
            v.addView(R.id.w_rows, taskRow(ctx, t, f));
            shown++;
        }
        v.setTextViewText(R.id.w_sub, total == 0 ? "" : total + (total == 1 ? " task" : " tasks")
                + (total > shown ? " \u00b7 showing " + shown : ""));
        v.setTextViewText(R.id.w_empty, "Nothing in " + NAMES[fi]);
        v.setViewVisibility(R.id.w_empty, shown == 0 ? View.VISIBLE : View.GONE);
        v.setViewVisibility(R.id.w_rows, shown == 0 ? View.GONE : View.VISIBLE);
        return v;
    }

    static RemoteViews taskRow(Context ctx, Task t, SimpleDateFormat f) {
        RemoteViews r = new RemoteViews(ctx.getPackageName(), R.layout.widget_row);
        r.setTextViewText(R.id.r_title, t.title);
        r.setTextViewText(R.id.r_time, t.due > 0 ? f.format(new Date(t.due)) : "");
        Intent done = new Intent(ctx, TaskWidget.class).setAction(ACTION_DONE).putExtra(EXTRA_ID, t.id)
                .setData(Uri.parse("widget://done/" + t.id));
        r.setOnClickPendingIntent(R.id.r_check, PendingIntent.getBroadcast(ctx, (int) t.id, done,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        r.setOnClickPendingIntent(R.id.r_title, open(ctx, false, 3));
        return r;
    }

    static PendingIntent open(Context ctx, boolean add, int code) {
        Intent i = new Intent(ctx, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_ADD, add).setData(Uri.parse("widget://open/" + code));
        return PendingIntent.getActivity(ctx, code, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static PendingIntent openActivity(Context ctx, Class<?> cls, int code, long noteId) {
        Intent i = new Intent(ctx, cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .setData(Uri.parse("widget://" + cls.getSimpleName() + "/" + code + "/" + noteId));
        if (noteId > 0) i.putExtra(NoteEditorActivity.EXTRA_ID, noteId);
        return PendingIntent.getActivity(ctx, code, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** The one-tap "Add a task" strip. */
    public static class QuickWidget extends AppWidgetProvider {
        static RemoteViews build(Context ctx) {
            RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget_quick);
            v.setOnClickPendingIntent(R.id.q_root, open(ctx, true, 4));
            return v;
        }

        @Override
        public void onUpdate(Context ctx, AppWidgetManager m, int[] ids) {
            for (int id : ids) m.updateAppWidget(id, build(ctx));
        }
    }
}
