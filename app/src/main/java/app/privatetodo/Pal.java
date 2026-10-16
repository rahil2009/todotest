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
        dark = mode == Prefs.THEME_DARK || (mode == Prefs.THEME_SYSTEM && system);
        if (dark) {
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
}
