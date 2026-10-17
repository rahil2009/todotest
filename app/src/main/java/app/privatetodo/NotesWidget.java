package app.privatetodo;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.view.View;
import android.widget.RemoteViews;

import java.util.List;

/** Notebook widget: the latest notes, tap one to open it, "+" starts a new note. */
public class NotesWidget extends AppWidgetProvider {
    @Override
    public void onUpdate(Context ctx, AppWidgetManager m, int[] ids) {
        for (int id : ids) m.updateAppWidget(id, build(ctx));
    }

    static RemoteViews build(Context ctx) {
        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget_tasks);
        v.setTextViewText(R.id.w_title, "Notebook");
        v.setOnClickPendingIntent(R.id.w_head, TaskWidget.openActivity(ctx, NotesActivity.class, 20, 0));
        v.setOnClickPendingIntent(R.id.w_add, TaskWidget.openActivity(ctx, NoteEditorActivity.class, 21, 0));
        v.removeAllViews(R.id.w_rows);
        TaskDb db = new TaskDb(ctx);
        List<Note> notes;
        try {
            notes = db.notes("");
        } finally {
            db.close();
        }
        int shown = 0;
        for (Note n : notes) {
            if (shown >= TaskWidget.ROWS - 1) break;
            RemoteViews r = new RemoteViews(ctx.getPackageName(), R.layout.widget_note_row);
            r.setTextViewText(R.id.n_title, n.title.isEmpty() ? "Untitled" : n.title);
            String p = n.plain.replace('\n', ' ').replace('\t', ' ').trim();
            r.setTextViewText(R.id.n_preview, p);
            r.setViewVisibility(R.id.n_preview, p.isEmpty() ? View.GONE : View.VISIBLE);
            r.setOnClickPendingIntent(R.id.n_title, TaskWidget.openActivity(ctx, NoteEditorActivity.class,
                    (int) (100 + n.id), n.id));
            r.setOnClickPendingIntent(R.id.n_preview, TaskWidget.openActivity(ctx, NoteEditorActivity.class,
                    (int) (100 + n.id), n.id));
            v.addView(R.id.w_rows, r);
            shown++;
        }
        v.setTextViewText(R.id.w_sub, notes.size() + (notes.size() == 1 ? " note" : " notes"));
        v.setTextViewText(R.id.w_empty, "No notes yet");
        v.setViewVisibility(R.id.w_empty, shown == 0 ? View.VISIBLE : View.GONE);
        v.setViewVisibility(R.id.w_rows, shown == 0 ? View.GONE : View.VISIBLE);
        return v;
    }
}
