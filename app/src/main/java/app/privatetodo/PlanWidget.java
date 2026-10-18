package app.privatetodo;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.view.View;
import android.widget.RemoteViews;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Plan widget: what is left today, in time order, with the free stretches in between. */
public class PlanWidget extends AppWidgetProvider {
    @Override
    public void onUpdate(Context ctx, AppWidgetManager m, int[] ids) {
        for (int id : ids) m.updateAppWidget(id, build(ctx));
    }

    private static String clock(long t) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t);
        return String.format(Locale.US, "%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));
    }

    static RemoteViews build(Context ctx) {
        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget_tasks);
        v.setTextViewText(R.id.w_title, "Plan");
        v.setOnClickPendingIntent(R.id.w_head, TaskWidget.openActivity(ctx, PlanActivity.class, 30, 0));
        v.setOnClickPendingIntent(R.id.w_add, TaskWidget.open(ctx, true, 2));
        v.removeAllViews(R.id.w_rows);

        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        long start = c.getTimeInMillis();
        long end = start + 86_400_000L;
        long now = System.currentTimeMillis();
        long dayEnd = start + 22 * 3_600_000L;

        TaskDb db = new TaskDb(ctx);
        List<Task> all;
        try {
            all = db.list(TaskDb.RAW);
        } finally {
            db.close();
        }
        java.util.ArrayList<Task> today = new java.util.ArrayList<>();
        int done = 0;
        int total = 0;
        for (Task t : all) {
            if (t.due < start || t.due >= end) continue;
            total++;
            if (t.done) done++;
            else today.add(t);
        }
        Collections.sort(today, (a, b) -> Long.compare(a.due, b.due));

        SimpleDateFormat f = new SimpleDateFormat("HH:mm", Locale.getDefault());
        long cursor = now - now % (15 * 60_000L) + 15 * 60_000L;
        int rows = 0;
        for (Task t : today) {
            if (rows >= TaskWidget.ROWS) break;
            if (t.due - cursor >= 3_600_000L) {
                v.addView(R.id.w_rows, free(ctx, cursor, t.due));
                rows++;
                if (rows >= TaskWidget.ROWS) break;
            }
            v.addView(R.id.w_rows, TaskWidget.taskRow(ctx, t, f));
            rows++;
            cursor = Math.max(cursor, t.due + 30 * 60_000L);
        }
        if (rows < TaskWidget.ROWS && dayEnd - cursor >= 3_600_000L) {
            v.addView(R.id.w_rows, free(ctx, cursor, dayEnd));
            rows++;
        }
        v.setTextViewText(R.id.w_sub, new SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(new Date(now))
                + " \u00b7 " + done + "/" + total + " done");
        v.setTextViewText(R.id.w_empty, "A clear day");
        v.setViewVisibility(R.id.w_empty, rows == 0 ? View.VISIBLE : View.GONE);
        v.setViewVisibility(R.id.w_rows, rows == 0 ? View.GONE : View.VISIBLE);
        return v;
    }

    private static RemoteViews free(Context ctx, long from, long to) {
        RemoteViews r = new RemoteViews(ctx.getPackageName(), R.layout.widget_free_row);
        r.setTextViewText(R.id.f_text, "Free " + clock(from) + " \u2013 " + clock(to));
        r.setOnClickPendingIntent(R.id.f_text, TaskWidget.openActivity(ctx, PlanActivity.class, 30, 0));
        return r;
    }
}
