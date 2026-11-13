package app.privatetodo;

import android.content.Context;
import android.content.SharedPreferences;

/** Three-finger gestures in a note: five triggers, each doing one action chosen in Settings > Notebook. */
final class Gestures {
    static final String[] TRIGGERS = {"Swipe down", "Swipe up", "Swipe left", "Swipe right", "Tap"};
    static final int DOWN = 0, UP = 1, LEFT = 2, RIGHT = 3, TAP = 4;

    static final int NONE = 0, FIND = 1, UNDO = 2, REDO = 3, TOP = 4, BOTTOM = 5, SELECT_ALL = 6, KEYBOARD = 7, SHARE = 8, FIND_REPLACE = 9, TOP_BOTTOM = 10;
    static final String[] ACTIONS = {"Nothing", "Find", "Undo", "Redo", "Jump to the top", "Jump to the bottom",
            "Select all", "Show / hide keyboard", "Share", "Find & replace", "Top, then back to where I was"};
    private static final int[] DEFAULTS = {FIND, TOP_BOTTOM, UNDO, REDO, NONE};

    private Gestures() {
    }

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    static boolean enabled(Context c) {
        return sp(c).getBoolean("gestures_on", true);
    }

    static void setEnabled(Context c, boolean v) {
        sp(c).edit().putBoolean("gestures_on", v).apply();
    }

    static int action(Context c, int trigger) {
        return sp(c).getInt("gesture_" + trigger, DEFAULTS[trigger]);
    }

    static void setAction(Context c, int trigger, int action) {
        sp(c).edit().putInt("gesture_" + trigger, action).apply();
    }
}
