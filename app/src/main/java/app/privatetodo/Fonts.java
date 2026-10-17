package app.privatetodo;

import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/** Applies the bundled Figtree font (same as the design) to every text in a view tree. */
final class Fonts {
    private static Typeface regular;
    private static Typeface bold;

    private Fonts() { }

    static synchronized void init(Context c) {
        if (regular != null) return;
        try {
            regular = c.getResources().getFont(R.font.figtree);
            bold = Typeface.create(regular, Typeface.BOLD);
        } catch (Exception e) {
            regular = null; // falls back to the system font
        }
    }

    /** Walks the tree and swaps the typeface, keeping bold / italic. Safe to call repeatedly. */
    static void apply(View v) {
        init(v.getContext());
        if (regular == null) return;
        walk(v);
    }

    private static void walk(View v) {
        if (v instanceof TextView) {
            TextView t = (TextView) v;
            Typeface cur = t.getTypeface();
            if (cur != null && (cur.equals(Typeface.MONOSPACE) || cur.equals(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)))) {
                return; // code stays monospace
            }
            boolean b = cur != null && cur.isBold();
            boolean i = cur != null && cur.isItalic();
            Typeface base = b ? bold : regular;
            if (i) base = Typeface.create(base, b ? Typeface.BOLD_ITALIC : Typeface.ITALIC);
            if (cur != base) t.setTypeface(base);
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int k = 0; k < g.getChildCount(); k++) walk(g.getChildAt(k));
        }
    }
}
