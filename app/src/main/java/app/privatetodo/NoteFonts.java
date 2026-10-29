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

    /** The fonts that come with the app; imported fonts take the slots after them. */
    static final int BUILTIN = 15;
    /** At most this many slots in all, which is also the range the editor's Font tab can address. */
    static final int MAX_SLOTS = 60;

    private static final java.util.Map<Integer, Typeface> CUSTOM_CACHE = new java.util.HashMap<>();

    private static android.content.SharedPreferences sp() {
        return app.getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    /** Imported fonts as saved: one "slot|name" per line. */
    private static String[] customLines() {
        if (app == null) return new String[0];
        String raw = sp().getString("custom_fonts", "");
        return raw.isEmpty() ? new String[0] : raw.split("\n");
    }

    private static java.io.File fontFile(int slot) {
        return new java.io.File(new java.io.File(app.getFilesDir(), "fonts"), slot + ".font");
    }

    static int count() {
        int max = BUILTIN;
        for (String l : customLines()) {
            try {
                max = Math.max(max, Integer.parseInt(l.substring(0, l.indexOf('|'))) + 1);
            } catch (RuntimeException ignored) {
                // skip a damaged line
            }
        }
        return max;
    }

    /** The name shown for font {@code idx}. */
    static String name(int idx) {
        if (idx < BUILTIN) return NAMES[idx];
        for (String l : customLines()) {
            int bar = l.indexOf('|');
            if (bar > 0 && l.substring(0, bar).equals(String.valueOf(idx))) return l.substring(bar + 1);
        }
        return "(removed)";
    }

    /** The line under the name in Settings and the editor. */
    static String note(int idx) {
        return idx < BUILTIN ? NOTES[idx] : "A font you imported";
    }

    /**
     * Copies a font file (.ttf or .otf) the user picked into the app and gives it the next free slot.
     * Returns its name, or null when the file is not a usable font.
     */
    static synchronized String importFont(Context c, android.net.Uri uri) {
        init(c);
        java.io.File dir = new java.io.File(app.getFilesDir(), "fonts");
        if (!dir.exists() && !dir.mkdirs()) return null;
        int slot = Math.max(BUILTIN, count());
        if (slot >= MAX_SLOTS) return null;
        java.io.File out = fontFile(slot);
        try (java.io.InputStream in = app.getContentResolver().openInputStream(uri);
             java.io.FileOutputStream os = new java.io.FileOutputStream(out)) {
            if (in == null) return null;
            byte[] buf = new byte[16384];
            long total = 0;
            int n;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > 30L * 1024 * 1024) throw new java.io.IOException("too big");
                os.write(buf, 0, n);
            }
        } catch (java.io.IOException | RuntimeException e) {
            out.delete();
            return null;
        }
        Typeface tf;
        try {
            tf = new Typeface.Builder(out).build();
        } catch (RuntimeException e) {
            tf = null;
        }
        if (tf == null) { // not a font after all
            out.delete();
            return null;
        }
        String name = "Imported font";
        android.database.Cursor cur = null;
        try {
            cur = app.getContentResolver().query(uri, new String[]{android.provider.OpenableColumns.DISPLAY_NAME},
                    null, null, null);
            if (cur != null && cur.moveToFirst() && cur.getString(0) != null) name = cur.getString(0);
        } catch (RuntimeException ignored) {
            // keep the generic name
        } finally {
            if (cur != null) cur.close();
        }
        int dot = name.lastIndexOf('.');
        if (dot > 0) name = name.substring(0, dot);
        name = name.replace('|', ' ').replace('\n', ' ').replace('_', ' ').replace('-', ' ').trim();
        if (name.length() > 24) name = name.substring(0, 24).trim();
        if (name.isEmpty()) name = "Imported font";
        CUSTOM_CACHE.put(slot, tf);
        String raw = sp().getString("custom_fonts", "");
        sp().edit().putString("custom_fonts", (raw.isEmpty() ? "" : raw + "\n") + slot + "|" + name).apply();
        return name;
    }

    /** Removes an imported font; text that used it falls back to the app font. */
    static synchronized void removeFont(Context c, int slot) {
        init(c);
        if (slot < BUILTIN) return;
        StringBuilder keep = new StringBuilder();
        for (String l : customLines()) {
            int bar = l.indexOf('|');
            if (bar > 0 && l.substring(0, bar).equals(String.valueOf(slot))) continue;
            if (keep.length() > 0) keep.append('\n');
            keep.append(l);
        }
        sp().edit().putString("custom_fonts", keep.toString()).apply();
        fontFile(slot).delete();
        CUSTOM_CACHE.remove(slot);
        if (noteDefault(c) == slot) setNoteDefault(c, 0);
    }

    /**
     * Slots 12 and 13 once held Calligra and Brush pen. They are gone, but the slots stay so that notes which
     * already store later fonts (Marker is 14) keep their numbers; text that used them shows in the app font.
     */
    static boolean shown(int idx) {
        if (idx >= BUILTIN) return !name(idx).equals("(removed)");
        return idx != 12 && idx != 13;
    }

    /** The regular face of font {@code idx} (0 = app font). */
    static synchronized Typeface face(int idx) {
        if (idx >= BUILTIN) {
            Typeface t = CUSTOM_CACHE.get(idx);
            if (t == null && app != null && shown(idx)) {
                try {
                    t = new Typeface.Builder(fontFile(idx)).build();
                } catch (RuntimeException e) {
                    t = null;
                }
                if (t != null) CUSTOM_CACHE.put(idx, t);
            }
            return t != null ? t : face(0);
        }
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
        int i = Math.max(0, Math.min(count() - 1, c.getApplicationContext()
                .getSharedPreferences("settings", Context.MODE_PRIVATE).getInt("note_font", 0)));
        return shown(i) ? i : 0;
    }

    static void setNoteDefault(Context c, int idx) {
        c.getApplicationContext().getSharedPreferences("settings", Context.MODE_PRIVATE)
                .edit().putInt("note_font", idx).apply();
    }
}
