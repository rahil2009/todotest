package app.privatetodo;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.widget.RemoteViews;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Home-screen widgets. Both are drawn from the local database only; nothing leaves the phone.
 *  - "Today": the next tasks (due today or overdue, then untimed ones), tick the circle to finish one.
 *  - "Quick add": one tap opens the app with the keyboard ready.
 */
public class TaskWidget extends AppWidgetProvider {
    static final String ACTION_DONE = "app.privatetodo.WIDGET_DONE";
    static final String EXTRA_ID = "id";
    static final String EXTRA_ADD = "add";
    private static final int ROWS = 6;
    private static final Handler H = new Handler(Looper.getMainLooper());

    /** Redraws every widget shortly after the data changed. Cheap and safe to call often. */
    static void refreshSoon(final Context ctx) {
        final Context app = ctx.getApplicationContext();
        H.removeCallbacksAndMessages(null);
        H.postDelayed(() -> refresh(app), 300);
    }

    static void refresh(Context ctx) {
        try {
            AppWidgetManager m = AppWidgetManager.getInstance(ctx);
            int[] ids = m.getAppWidgetIds(new ComponentName(ctx, TaskWidget.class));
            for (int id : ids) m.updateAppWidget(id, build(ctx));
            int[] qs = m.getAppWidgetIds(new ComponentName(ctx, QuickWidget.class));
            for (int id : qs) m.updateAppWidget(id, QuickWidget.build(ctx));
        } catch (RuntimeException ignored) {
            // a widget must never crash the app
        }
    }

    @Override
    public void onUpdate(Context ctx, AppWidgetManager m, int[] ids) {
        for (int id : ids) m.updateAppWidget(id, build(ctx));
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (ACTION_DONE.equals(intent.getAction())) {
            long id = intent.getLongExtra(EXTRA_ID, -1);
            if (id >= 0) {
                Reminders.complete(ctx, id);
                refresh(ctx);
            }
            return;
        }
        super.onReceive(ctx, intent);
    }

    static RemoteViews build(Context ctx) {
        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget_tasks);
        v.setOnClickPendingIntent(R.id.w_title, open(ctx, false, 1));
        v.setOnClickPendingIntent(R.id.w_add, open(ctx, true, 2));
        v.removeAllViews(R.id.w_rows);

        TaskDb db = new TaskDb(ctx);
        List<Task> list;
        try {
            list = db.list(TaskDb.TODAY);
            if (list.isEmpty()) list = db.list(TaskDb.ACTIVE);
        } finally {
            db.close();
        }
        int shown = 0;
        SimpleDateFormat f = new SimpleDateFormat("HH:mm", Locale.getDefault());
        for (Task t : list) {
            if (t.done || shown >= ROWS) continue;
            RemoteViews r = new RemoteViews(ctx.getPackageName(), R.layout.widget_row);
            r.setTextViewText(R.id.r_title, t.title);
            r.setTextViewText(R.id.r_time, t.due > 0 ? f.format(new Date(t.due)) : "");
            Intent done = new Intent(ctx, TaskWidget.class).setAction(ACTION_DONE).putExtra(EXTRA_ID, t.id);
            r.setOnClickPendingIntent(R.id.r_check, PendingIntent.getBroadcast(ctx, (int) t.id, done,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
            r.setOnClickPendingIntent(R.id.r_title, open(ctx, false, 3));
            v.addView(R.id.w_rows, r);
            shown++;
        }
        v.setViewVisibility(R.id.w_empty, shown == 0 ? android.view.View.VISIBLE : android.view.View.GONE);
        v.setViewVisibility(R.id.w_rows, shown == 0 ? android.view.View.GONE : android.view.View.VISIBLE);
        return v;
    }

    static PendingIntent open(Context ctx, boolean add, int code) {
        Intent i = new Intent(ctx, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_ADD, add);
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
