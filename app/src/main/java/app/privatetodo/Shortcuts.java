package app.privatetodo;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;

import java.util.ArrayList;
import java.util.List;

/** The quick actions shown when you hold the app icon: new task, new note, and the main screens. Local intents only. */
final class Shortcuts {
    private Shortcuts() {
    }

    static void publish(Context c) {
        try {
            ShortcutManager sm = c.getSystemService(ShortcutManager.class);
            if (sm == null) return;
            List<ShortcutInfo> l = new ArrayList<>();
            l.add(one(c, "new_task", "New task", "New task",
                    new Intent(Intent.ACTION_VIEW).setClass(c, MainActivity.class).putExtra(TaskWidget.EXTRA_ADD, true)));
            l.add(one(c, "new_note", "New note", "New note",
                    new Intent(Intent.ACTION_VIEW).setClass(c, NoteEditorActivity.class)
                            .putExtra(NoteEditorActivity.EXTRA_ID, 0L)));
            l.add(one(c, "notes", "Notes", "Open notes",
                    new Intent(Intent.ACTION_VIEW).setClass(c, NotesActivity.class)));
            l.add(one(c, "habits", "Habits", "Open habits",
                    new Intent(Intent.ACTION_VIEW).setClass(c, HabitActivity.class)));
            l.add(one(c, "pomodoro", "Focus timer", "Start a focus timer",
                    new Intent(Intent.ACTION_VIEW).setClass(c, PomodoroActivity.class)));
            l.add(one(c, "plan", "Plan", "Open the day plan",
                    new Intent(Intent.ACTION_VIEW).setClass(c, PlanActivity.class)));
            int max = Math.max(1, sm.getMaxShortcutCountPerActivity());
            sm.setDynamicShortcuts(l.subList(0, Math.min(l.size(), max)));
        } catch (RuntimeException ignored) {
            // shortcuts are a convenience; never block the app
        }
    }

    private static ShortcutInfo one(Context c, String id, String shortLabel, String longLabel, Intent i) {
        int res = id.equals("new_task") ? R.drawable.sc_task : id.equals("new_note") ? R.drawable.sc_note
                : id.equals("notes") ? R.drawable.sc_notes : id.equals("habits") ? R.drawable.sc_habits
                : id.equals("pomodoro") ? R.drawable.sc_focus : R.drawable.sc_plan;
        return new ShortcutInfo.Builder(c, id)
                .setShortLabel(shortLabel)
                .setLongLabel(longLabel)
                .setIcon(Icon.createWithResource(c, res))
                .setIntent(i)
                .build();
    }
}
