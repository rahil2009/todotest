package app.privatetodo;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * Invisible stop for taps on widget rows and buttons: it does the action (tick a task, log a habit) or opens
 * the right screen, then closes itself at once. Works on every Android version, unlike a broadcast that
 * would have to start a screen from the background.
 */
public class WidgetActionActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        try {
            handle(getIntent());
        } catch (RuntimeException ignored) {
            // a widget tap must never crash
        }
        finish();
        overridePendingTransition(0, 0);
    }

    private void open(Class<?> cls, Intent extra) {
        Intent i = new Intent(this, cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (extra != null) i.putExtras(extra);
        startActivity(i);
    }

    private void handle(Intent in) {
        String act = in.getStringExtra("act");
        long id = in.getLongExtra("id", -1);
        if (act == null) return;
        switch (act) {
            case "done":
                if (id >= 0) Reminders.complete(this, id);
                Widgets.refreshAll(this);
                break;
            case "snooze":
                if (id >= 0) Reminders.snooze(this, id, 60);
                Widgets.refreshAll(this);
                break;
            case "task":
                startActivity(new Intent(this, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                break;
            case "note":
                open(NoteEditorActivity.class, new Intent().putExtra(NoteEditorActivity.EXTRA_ID, id));
                break;
            case "plan":
                open(PlanActivity.class, null);
                break;
            case "open_habits":
                open(HabitActivity.class, null);
                break;
            case "habit": {
                HabitDb db = new HabitDb(this);
                try {
                    Habit h = db.get(id);
                    if (h != null) {
                        long day = HabitDb.today();
                        int amt = in.getIntExtra("amt", h.step);
                        if (h.goal <= 0) db.setAmount(id, day, db.amount(id, day) > 0 ? 0 : 1);
                        else db.add(id, day, amt > 0 ? amt : h.step);
                    }
                } finally {
                    db.close();
                }
                Widgets.refreshAll(this);
                break;
            }
            case "count":
                open(CountdownActivity.class, null);
                break;
            case "lent":
                open(LentPersonActivity.class, new Intent().putExtra("person", in.getStringExtra("person"))
                        .putExtra("dir", in.getIntExtra("dir", 0)));
                break;
            default:
                break;
        }
    }
}
