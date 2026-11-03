package app.privatetodo;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Where a note opens: Smart (long notes at the last spot, short ones at the top), the last spot, the top or the bottom.
 * One setting for all notes in Settings > Notebook; any note can have its own (or follow that setting).
 */
final class NoteOpen {
    static final int FOLLOW = -1;
    static final int SMART = 0;
    static final int LAST = 1;
    static final int TOP = 2;
    static final int BOTTOM = 3;
    static final String[] NAMES = {"Smart", "Where I left off", "Top", "Bottom"};
    static final String[] DESC = {
            "Long notes open where you left off, short ones at the top.",
            "Every note opens at the spot you last read.",
            "Always start at the beginning.",
            "Always jump to the end (good for logs and journals)."};

    private NoteOpen() {
    }

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("note_open", Context.MODE_PRIVATE);
    }

    static int universal(Context c) {
        return sp(c).getInt("all", SMART);
    }

    static void setUniversal(Context c, int m) {
        sp(c).edit().putInt("all", m).apply();
    }

    /** This note's own choice, or FOLLOW. */
    static int own(Context c, long id) {
        return sp(c).getInt("m" + id, FOLLOW);
    }

    static void setOwn(Context c, long id, int m) {
        if (m == FOLLOW) sp(c).edit().remove("m" + id).apply();
        else sp(c).edit().putInt("m" + id, m).apply();
    }

    static int effective(Context c, long id) {
        int m = own(c, id);
        return m == FOLLOW ? universal(c) : m;
    }

    /** Character offset of the first line on screen when the note was left. */
    static int lastOffset(Context c, long id) {
        return sp(c).getInt("o" + id, 0);
    }

    static void setLastOffset(Context c, long id, int offset) {
        if (id > 0) sp(c).edit().putInt("o" + id, Math.max(0, offset)).apply();
    }

    static boolean jumpButton(Context c) {
        return sp(c).getBoolean("jump", true);
    }

    static void setJumpButton(Context c, boolean on) {
        sp(c).edit().putBoolean("jump", on).apply();
    }

    static String label(Context c, long id) {
        int m = own(c, id);
        return m == FOLLOW ? "Follow Settings (" + NAMES[universal(c)] + ")" : NAMES[m];
    }
}
