package app.privatetodo;

import android.content.Context;
import android.graphics.Typeface;

/** The fonts a note's text can use, bundled with the app (no downloads). Index 0 is the app font. */
final class NoteFonts {
    private NoteFonts() { }

    static final String[] NAMES = {"Sans", "Bricolage", "Serif (Lora)", "Crimson", "Young Serif", "Gloock",
            "Instrument", "Outfit", "Poiret", "Handwriting", "Chancery", "Mono", "(removed)", "(removed)", "Marker"};

    /** A short line shown under the font's name in Settings. */
    static final String[] NOTES = {"The app font (Figtree)", "Bricolage Grotesque, the design's headline font",
            "Lora, a warm Georgia-style serif", "Crimson Pro, a book serif", "Young Serif, a chunky serif",
            "Gloock, a high-contrast display serif", "Instrument Serif, tall and elegant",
            "Outfit, clean and geometric", "Poiret One, thin art deco", "Nothing You Could Do, hand-written",
            "TeX Gyre Chorus, calligraphy", "JetBrains Mono, for code",
            "", "",
            "A heavy felt-tip marker hand"};

    /** Extra outline (fraction of the text size) drawn around the glyphs: 0 = plain, bigger = bolder pen. */
    private static final float[] STROKE = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0.075f};

    private static final int[] RES = {R.font.figtree, R.font.bricolage, R.font.lora, R.font.crimson,
            R.font.youngserif, R.font.gloock, R.font.instrumentserif, R.font.outfit, R.font.poiret,
            R.font.handwrite, R.font.chancery, R.font.jetbrains, R.font.figtree, R.font.figtree,
            R.font.handwrite};

    private static final Typeface[] CACHE = new Typeface[RES.length];
    private static Context app;

    static void init(Context c) {
        app = c.getApplicationContext();
    }

    static int count() {
        return NAMES.length;
    }

    /**
     * Slots 12 and 13 once held Calligra and Brush pen. They are gone, but the slots stay so that notes which
     * already store later fonts (Marker is 14) keep their numbers; text that used them shows in the app font.
     */
    static boolean shown(int idx) {
        return idx != 12 && idx != 13;
    }

    /** The regular face of font {@code idx} (0 = app font). */
    static synchronized Typeface face(int idx) {
        if (idx < 0 || idx >= RES.length) idx = 0;
        if (CACHE[idx] == null && app != null) {
            try {
                CACHE[idx] = app.getResources().getFont(RES[idx]);
            } catch (Exception e) {
                CACHE[idx] = null;
            }
        }
        return CACHE[idx] != null ? CACHE[idx] : Typeface.DEFAULT;
    }

    /** Outline width factor of font {@code idx} (0 for ordinary fonts). */
    static float stroke(int idx) {
        return idx < 0 || idx >= STROKE.length ? 0f : STROKE[idx];
    }

    /** Default font for a whole note (Settings > Notebook). */
    static int noteDefault(Context c) {
        int i = Math.max(0, Math.min(RES.length - 1, c.getApplicationContext()
                .getSharedPreferences("settings", Context.MODE_PRIVATE).getInt("note_font", 0)));
        return shown(i) ? i : 0;
    }

    static void setNoteDefault(Context c, int idx) {
        c.getApplicationContext().getSharedPreferences("settings", Context.MODE_PRIVATE)
                .edit().putInt("note_font", idx).apply();
    }
}
