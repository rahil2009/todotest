package app.privatetodo;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Pick a page background and its colour. From Settings it sets the look of all notes; inside a note it can set
 * that note alone ("This note") or all notes. Changes show at once through {@code onChange}.
 */
final class PaperPicker {
    private PaperPicker() {
    }

    interface Change {
        void changed(int style, int color);
    }

    static View build(final Activity a, final long noteId, final Change onChange) {
        final float d = a.getResources().getDisplayMetrics().density;
        final LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        final boolean[] own = {noteId > 0 && (Paper.ownStyle(a, noteId) >= 0 || Paper.ownColor(a, noteId) >= 0)};
        final int[] style = {noteId > 0 ? Paper.styleFor(a, noteId) : Paper.chosen(a)};
        final int[] color = {noteId > 0 ? Paper.colorFor(a, noteId) : Paper.chosenColor(a)};
        final View[] tiles = new View[Paper.NAMES.length];
        final TextView[] names = new TextView[Paper.NAMES.length];
        final View[] swatches = new View[Paper.COLOR_NAMES.length];
        final TextView[] scope = new TextView[2];

        final Runnable save = () -> {
            if (noteId > 0 && own[0]) {
                Paper.setOwn(a, noteId, style[0], color[0]);
            } else {
                if (noteId > 0) Paper.setOwn(a, noteId, -1, -1);
                Paper.setChosen(a, style[0]);
                Paper.setChosenColor(a, color[0]);
            }
            if (onChange != null) onChange.changed(style[0], color[0]);
        };
        final Runnable paint = () -> {
            for (int i = 0; i < tiles.length; i++) {
                GradientDrawable g = new GradientDrawable();
                int t = Paper.tint(color[0]);
                g.setColor(t != 0 ? t : Pal.bg);
                g.setCornerRadius(12 * d);
                g.setStroke(Math.round((i == style[0] ? 2 : 1) * d), i == style[0] ? Pal.accent : Pal.chip);
                tiles[i].setBackground(g);
                tiles[i].invalidate();
                names[i].setTextColor(i == style[0] ? Pal.accent : Pal.muted);
            }
            for (int i = 0; i < swatches.length; i++) {
                GradientDrawable g = new GradientDrawable();
                g.setShape(GradientDrawable.OVAL);
                int t = Paper.tint(i);
                g.setColor(t != 0 ? t : Pal.bg);
                g.setStroke(Math.round((i == color[0] ? 3 : 1) * d), i == color[0] ? Pal.accent : Pal.muted);
                swatches[i].setBackground(g);
            }
            for (int i = 0; i < 2 && scope[0] != null; i++) {
                boolean on = (i == 0) == own[0];
                GradientDrawable g = new GradientDrawable();
                g.setCornerRadius(100 * d);
                g.setColor(on ? Pal.sel : Pal.chip);
                scope[i].setBackground(g);
                scope[i].setTextColor(on ? Pal.onSel : Pal.muted);
            }
        };

        if (noteId > 0) { // inside a note: this note only, or every note
            LinearLayout row = new LinearLayout(a);
            String[] lab = {"This note", "All notes"};
            for (int i = 0; i < 2; i++) {
                final boolean mine = i == 0;
                TextView t = label(a, lab[i], 14, true);
                t.setGravity(Gravity.CENTER);
                t.setPadding(Math.round(16 * d), 0, Math.round(16 * d), 0);
                t.setOnClickListener(v -> {
                    Hap.tick(v);
                    own[0] = mine;
                    save.run();
                    paint.run();
                });
                scope[i] = t;
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, Math.round(38 * d));
                lp.rightMargin = Math.round(8 * d);
                row.addView(t, lp);
            }
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
            rl.bottomMargin = Math.round(12 * d);
            box.addView(row, rl);
        }

        TextView h1 = label(a, "STYLE", 12, true);
        h1.setLetterSpacing(0.08f);
        h1.setTextColor(Pal.muted);
        h1.setPadding(0, 0, 0, Math.round(6 * d));
        box.addView(h1);
        HorizontalScrollView hs = new HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout tilesRow = new LinearLayout(a);
        for (int i = 0; i < Paper.NAMES.length; i++) {
            final int k = i;
            LinearLayout cell = new LinearLayout(a);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            View tile = new View(a) {
                private final Rect rr = new Rect();

                @Override
                protected void onDraw(Canvas cv) {
                    rr.set(0, 0, getWidth(), getHeight());
                    Paper.draw(cv, k, rr, 14 * d, 0, 10 * d, getWidth() - 4 * d, d);
                }
            };
            tile.setClipToOutline(true);
            tiles[i] = tile;
            cell.addView(tile, new LinearLayout.LayoutParams(Math.round(64 * d), Math.round(80 * d)));
            TextView nm = label(a, Paper.NAMES[i], 11, true);
            nm.setPadding(0, Math.round(4 * d), 0, 0);
            names[i] = nm;
            cell.addView(nm);
            cell.setOnClickListener(v -> {
                Hap.tick(v);
                style[0] = k;
                save.run();
                paint.run();
            });
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(Math.round(72 * d), -2);
            clp.rightMargin = Math.round(4 * d);
            tilesRow.addView(cell, clp);
        }
        hs.addView(tilesRow);
        box.addView(hs);

        TextView h2 = label(a, "COLOUR", 12, true);
        h2.setLetterSpacing(0.08f);
        h2.setTextColor(Pal.muted);
        h2.setPadding(0, Math.round(16 * d), 0, Math.round(6 * d));
        box.addView(h2);
        HorizontalScrollView cs = new HorizontalScrollView(a);
        cs.setHorizontalScrollBarEnabled(false);
        LinearLayout colRow = new LinearLayout(a);
        for (int i = 0; i < Paper.COLOR_NAMES.length; i++) {
            final int k = i;
            View sw = new View(a);
            sw.setContentDescription(Paper.COLOR_NAMES[i]);
            sw.setOnClickListener(v -> {
                Hap.tick(v);
                color[0] = k;
                save.run();
                paint.run();
            });
            swatches[i] = sw;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Math.round(38 * d), Math.round(38 * d));
            lp.rightMargin = Math.round(8 * d);
            colRow.addView(sw, lp);
        }
        cs.addView(colRow);
        box.addView(cs);
        paint.run();
        return box;
    }

    private static TextView label(Activity a, String s, int sp, boolean bold) {
        TextView t = new TextView(a);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(Pal.ink);
        if (bold) t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return t;
    }
}
