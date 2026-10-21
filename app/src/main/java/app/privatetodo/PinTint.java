package app.privatetodo;

import android.content.Context;

/** Fill colour for pinned notes' previews (Settings > Notebook). Style 0 is the faint default tint. */
final class PinTint {
    static final String[] NAMES = {"Soft", "Green", "Amber", "Rose", "Sky", "Violet", "Sticky", "Ink", "Mixed", "None",
            "Sunset", "Ocean", "Aurora", "Berry", "Dusk",
            "Peach", "Mint", "Lagoon", "Candy", "Citrus", "Forest", "Midnight", "Ember", "Lilac", "Slate"};
    static final int COUNT = NAMES.length;
    private static final int[] MIX = {1, 2, 3, 4, 5, 6};

    private PinTint() {
    }

    static int style(Context c) {
        return Math.max(0, Math.min(COUNT - 1, Prefs.pinColor(c)));
    }

    /** True when the whole preview is filled with a solid colour (so text needs the matching foreground). */
    /** The style a note uses: its own pinned colour if it has one, otherwise the Settings choice. */
    static int of(Context c, Note n) {
        return n.pinColor >= 0 && n.pinColor < COUNT ? n.pinColor : style(c);
    }

    static boolean solid(int style) {
        return (style >= 1 && style <= 8) || isGradient(style);
    }

    /** Styles 10 to 24 fill the preview with a diagonal two-colour gradient. */
    private static final int[][] GRAD = {
            {0xFFFFB347, 0xFFF0647A}, // Sunset
            {0xFF1B8FB0, 0xFF3F5BD8}, // Ocean
            {0xFF0B6B5D, 0xFF2F8FC4}, // Aurora
            {0xFF8E7CE0, 0xFFE9677B}, // Berry
            {0xFF1F2A44, 0xFF7A4FA0}, // Dusk
            {0xFFFFD1A9, 0xFFFF8FA3}, // Peach
            {0xFFA8E6CF, 0xFF3FB8AF}, // Mint
            {0xFF2DD4BF, 0xFF3B82F6}, // Lagoon
            {0xFFFF9A9E, 0xFFA18CD1}, // Candy
            {0xFFF9D423, 0xFFFF4E50}, // Citrus
            {0xFF134E5E, 0xFF71B280}, // Forest
            {0xFF0F2027, 0xFF2C5364}, // Midnight
            {0xFF7F1D1D, 0xFFF97316}, // Ember
            {0xFFC4B5FD, 0xFFF9A8D4}, // Lilac
            {0xFF334155, 0xFF94A3B8}, // Slate
    };

    static boolean isGradient(int style) {
        return style >= 10 && style < 10 + GRAD.length;
    }

    /** Fills a drawable for this style: a gradient for 10 to 14, otherwise the solid colour. */
    static void paint(android.graphics.drawable.GradientDrawable g, int style, long id) {
        if (isGradient(style)) {
            g.setColors(GRAD[style - 10]);
            g.setOrientation(android.graphics.drawable.GradientDrawable.Orientation.TL_BR);
        } else if (style == 8) { // swatch of the mix
            g.setColors(new int[]{swatch(1), swatch(2), swatch(3), swatch(4)});
            g.setOrientation(android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT);
        } else {
            g.setColor(bg(style, id));
        }
    }

    private static int real(int style, long id) {
        return style == 8 ? MIX[(int) (Math.abs(id) % MIX.length)] : style;
    }

    static int bg(int style, long id) {
        if (isGradient(style)) return GRAD[style - 10][0];
        switch (real(style, id)) {
            case 1: return Pal.accent;
            case 2: return 0xFFF2A93B;
            case 3: return 0xFFE9677B;
            case 4: return 0xFF5AA9E6;
            case 5: return 0xFF8E7CE0;
            case 6: return 0xFFF6DF6B;
            case 7: return Pal.dark ? Pal.sel : 0xFF0E1513;
            case 9: return Pal.card;
            default: return HabitUi.blend(Pal.accent, Pal.card, 0.12f);
        }
    }

    static int fg(int style, long id) {
        if (isGradient(style)) { // the light gradients need dark text
            switch (style) {
                case 10: case 15: case 16: case 17: case 18: case 19: case 23:
                    return 0xFF0E1513;
                default:
                    return 0xFFFFFFFF;
            }
        }
        switch (real(style, id)) {
            case 1: return Pal.onAccent;
            case 2: case 4: case 6: return 0xFF0E1513;
            case 3: case 5: return 0xFFFFFFFF;
            case 7: return Pal.dark ? Pal.onSel : 0xFFFFFFFF;
            default: return Pal.ink;
        }
    }

    /** Background for a swatch preview in Settings (style 8 shows the first of the mix). */
    static int swatch(int style) {
        return bg(style, 0);
    }
}
