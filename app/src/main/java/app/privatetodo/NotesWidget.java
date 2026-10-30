package app.privatetodo;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.widget.RemoteViews;

/** Notebook widget: a scrolling list of notes, pinned first. Tap one to open it, "+" for a new note. */
public class NotesWidget extends AppWidgetProvider {
    @Override
    public void onUpdate(Context ctx, AppWidgetManager m, int[] ids) {
        for (int id : ids) m.updateAppWidget(id, build(ctx, id));
    }

    static RemoteViews build(Context ctx, int wid) {
        return Widgets.list(ctx, wid, Widgets.K_NOTES, TaskWidget.openActivity(ctx, NotesActivity.class, 20, 0),
                TaskWidget.openActivity(ctx, NoteEditorActivity.class, 21, 0));
    }
}
