package app.privatetodo;

import android.content.Context;
import android.content.res.Configuration;

/**
 * The app's colours, light or dark. Call {@link #init} in every activity before building views.
 * Dark follows the usual dark-theme rules: a dark grey (not black) base, surfaces that get lighter as
 * they rise, text a little under pure white, and a softer (less saturated) accent.
 */
final class Pal {
    static boolean dark;
    static boolean amoled;
    static int bg = 0xFFEEF1EF;      // page
    static int card = 0xFFFFFFFF;    // task cards, settings cards
    static int raised = 0xFFFFFFFF;  // floating bar: gear, pill, search (one step above cards)
    static int chip = 0xFFEEF1EF;    // chips and dial wheels inside cards and dialogs
    static int ink = 0xFF0E1513;     // main text
    static int muted = 0xFF5B6764;   // secondary text and outlines
    static int accent = 0xFF0B6B5D;
    static int onAccent = 0xFFFFFFFF;
    static int sel = 0xFF0E1513;     // selected chip fill
    static int onSel = 0xFFFFFFFF;   // text on a selected chip
    static int overdue = 0xFFB3261E;

    private Pal() {
    }

    static void init(Context c) {
        int mode = Prefs.theme(c);
        boolean system = (c.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        amoled = mode == Prefs.THEME_AMOLED;
        dark = mode == Prefs.THEME_DARK || amoled || (mode == Prefs.THEME_SYSTEM && system);
        if (amoled) { // true black so AMOLED pixels switch off
            bg = 0xFF000000;
            card = 0xFF111413;
            raised = 0xFF1A1E1C;
            chip = 0xFF232826;
            ink = 0xFFE3E8E6;
            muted = 0xFF9FA9A5;
            accent = 0xFF6FCDBD;
            onAccent = 0xFF04201B;
            sel = 0xFF21403A;
            onSel = 0xFFD2F1EA;
            overdue = 0xFFF2B8B5;
        } else if (dark) {
            bg = 0xFF111513;
            card = 0xFF1A201E;
            raised = 0xFF252C2A;
            chip = 0xFF2E3633;
            ink = 0xFFE3E8E6;       // about 87% white
            muted = 0xFF9FA9A5;     // about 60% white
            accent = 0xFF6FCDBD;
            onAccent = 0xFF04201B;
            sel = 0xFF2B4B45;       // tonal selection instead of a glaring white chip
            onSel = 0xFFD2F1EA;
            overdue = 0xFFF2B8B5;
        } else {
            bg = 0xFFEEF1EF;
            card = 0xFFFFFFFF;
            raised = 0xFFFFFFFF;
            chip = 0xFFEEF1EF;
            ink = 0xFF0E1513;
            muted = 0xFF5B6764;
            accent = 0xFF0B6B5D;
            onAccent = 0xFFFFFFFF;
            sel = 0xFF0E1513;
            onSel = 0xFFFFFFFF;
            overdue = 0xFFB3261E;
        }
    }

    /** The window theme matching the current palette. */
    static int themeRes() {
        return amoled ? R.style.AppThemeAmoled : dark ? R.style.AppThemeDark : R.style.AppTheme;
    }

    /**
     * Tells Android which mode the app is in, the way apps with their own Light / Dark / Auto setting do
     * (AndroidX calls this setDefaultNightMode). Android 12+: the system itself treats the app as light or dark,
     * so force-dark, "make more apps dark", system dialogs, pickers and the text bar all follow the app's choice.
     */
    static void applyNight(Context c) {
        if (android.os.Build.VERSION.SDK_INT < 31) return;
        int mode = Prefs.theme(c);
        int m = mode == Prefs.THEME_LIGHT ? android.app.UiModeManager.MODE_NIGHT_NO
                : mode == Prefs.THEME_SYSTEM ? android.app.UiModeManager.MODE_NIGHT_AUTO
                : android.app.UiModeManager.MODE_NIGHT_YES;
        try {
            android.app.UiModeManager um = c.getSystemService(android.app.UiModeManager.class);
            if (um != null) um.setApplicationNightMode(m);
        } catch (RuntimeException ignored) {
            // older or unusual system: the per-screen override below still applies
        }
    }

    /**
     * Every screen is created with the night flag set to the app's choice (Android 8 to 11, and as a second
     * guard on newer ones). Called from each activity's attachBaseContext, before anything is drawn.
     */
    static void override(android.app.Activity a, Context base) {
        int mode = Prefs.theme(base);
        if (mode == Prefs.THEME_SYSTEM) return;
        Configuration cur = base.getResources().getConfiguration();
        Configuration o = new Configuration();
        o.fontScale = 0f; // 0 = "not overridden": a fresh Configuration says 1.0 and would undo the phone's font size
        int night = mode == Prefs.THEME_LIGHT ? Configuration.UI_MODE_NIGHT_NO : Configuration.UI_MODE_NIGHT_YES;
        o.uiMode = (cur.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | night;
        try {
            a.applyOverrideConfiguration(o);
        } catch (IllegalStateException ignored) {
            // resources were already created: the palette alone still paints the right colours
        }
    }
}
