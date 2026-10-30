package app.privatetodo;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.RemoteViews;

/**
 * Home-screen widgets. All of them are drawn from the local database only; nothing leaves the phone.
 *  - "Tasks": a scrolling list of any of the app's filters. Tap the title to switch filter, tick a circle
 *    to finish a task, "+" to add one.
 *  - "Shortcuts": one tap each to a new task, a new note, habits, lent and focus.
 *  - Notes, Plan, Today, Habits, Water and Lent are in {@link NotesWidget}, {@link PlanWidget} and {@link Widgets}.
 */
public class TaskWidget extends AppWidgetProvider {
    static final String ACTION_CYCLE = "app.privatetodo.WIDGET_CYCLE";
    static final String EXTRA_WIDGET = "widget";
    static final String EXTRA_ADD = "add";

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
        Widgets.refreshAll(ctx);
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
        if (ACTION_CYCLE.equals(intent.getAction())) {
            int w = intent.getIntExtra(EXTRA_WIDGET, -1);
            if (w >= 0) {
                setFilterIndex(ctx, w, (filterIndex(ctx, w) + 1) % FILTERS.length);
                AppWidgetManager m = AppWidgetManager.getInstance(ctx);
                m.updateAppWidget(w, build(ctx, w));
                m.notifyAppWidgetViewDataChanged(w, R.id.w_list);
            }
            return;
        }
        super.onReceive(ctx, intent);
    }

    static RemoteViews build(Context ctx, int widgetId) {
        Intent cycle = new Intent(ctx, TaskWidget.class).setAction(ACTION_CYCLE)
                .putExtra(EXTRA_WIDGET, widgetId).setData(Uri.parse("widget://cycle/" + widgetId));
        PendingIntent head = PendingIntent.getBroadcast(ctx, widgetId, cycle,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return Widgets.list(ctx, widgetId, Widgets.K_TASKS, head, open(ctx, true, 1));
    }

    static PendingIntent open(Context ctx, boolean add, int code) {
        Intent i = new Intent(ctx, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_ADD, add).setData(Uri.parse("widget://open/" + code + "/" + add));
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

    /** The shortcuts strip: new task, new note, habits, lent, focus. */
    public static class QuickWidget extends AppWidgetProvider {
        static RemoteViews build(Context ctx) {
            RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.w_quick);
            v.setOnClickPendingIntent(R.id.q_task, open(ctx, true, 4));
            v.setOnClickPendingIntent(R.id.q_note, openActivity(ctx, NoteEditorActivity.class, 45, 0));
            v.setOnClickPendingIntent(R.id.q_habit, openActivity(ctx, HabitActivity.class, 46, 0));
            v.setOnClickPendingIntent(R.id.q_lent, openActivity(ctx, LentActivity.class, 47, 0));
            v.setOnClickPendingIntent(R.id.q_focus, openActivity(ctx, PomodoroActivity.class, 48, 0));
            return v;
        }

        @Override
        public void onUpdate(Context ctx, AppWidgetManager m, int[] ids) {
            for (int id : ids) m.updateAppWidget(id, build(ctx));
        }
    }
}
