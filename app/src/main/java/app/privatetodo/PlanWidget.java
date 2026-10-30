package app.privatetodo;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.widget.RemoteViews;

/** Plan widget: what is left today in time order, with the free stretches in between. Scrolls. */
public class PlanWidget extends AppWidgetProvider {
    @Override
    public void onUpdate(Context ctx, AppWidgetManager m, int[] ids) {
        for (int id : ids) m.updateAppWidget(id, build(ctx, id));
    }

    static RemoteViews build(Context ctx, int wid) {
        return Widgets.list(ctx, wid, Widgets.K_PLAN, TaskWidget.openActivity(ctx, PlanActivity.class, 30, 0),
                TaskWidget.open(ctx, true, 2));
    }
}
