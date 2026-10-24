package app.privatetodo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.Spannable;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.CharacterStyle;
import android.text.style.LineBackgroundSpan;
import android.text.style.MetricAffectingSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.text.style.UpdateAppearance;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Notebook formatting: bold, italic, underline, strikethrough, highlight colours, inline code, code
 * blocks and headings, stored as plain text plus a list of [type, start, end, arg] ranges (JSON in the
 * local database). Bullets are ordinary "• " characters at the start of a line.
 */
final class RichText {
    static final int BOLD = 0;
    static final int ITALIC = 1;
    static final int UNDER = 2;
    static final int STRIKE = 3;
    static final int HILITE = 4;
    static final int CODE = 5;
    static final int HEAD = 6;
    static final int BLOCK = 7;
    static final int TABLE = 8;
    /** A chosen text size in sp (at normal zoom); the arg of the range is the size. */
    static final int SIZE = 9;
    /** Text colour; the arg of the range is the ARGB colour. */
    static final int FG = 10;
    /** Nesting level of a list line (paragraph indent); the arg is the level. */
    static final int INDENT = 11;
    /** A font from {@link NoteFonts}; the arg is its index. */
    static final int FONT = 12;
    /** Quote paragraph (accent bar, serif italic). */
    static final int QUOTE = 13;
    /** Text folded away under a collapsed toggle. */
    static final int HIDE = 14;
    /** Horizontal divider drawn in place of one placeholder character. */
    static final int RULE = 15;
    /** Tinted callout paragraph. */
    static final int CALLOUT = 16;
    static final char RULE_CHAR = '\u2500';
    static final int MAX_INDENT = 5;
    static final int MIN_SIZE = 8;
    static final int MAX_SIZE = 72;
    static final int BASE_SIZE = 17;

    static final String BULLET = "• ";
    /** Highlight colours (same in light and dark; highlighted text is always drawn dark). */
    static final int[] HL_COLORS = {0xFFFFE066, 0xFFA8E6A1, 0xFF9FD3FF, 0xFFFFB3C7, 0xFFFFC48A};
    /** Text colours that read on both the light and the dark page. */
    static final int[] FG_COLORS = {0xFFE5484D, 0xFFF76B15, 0xFFD9A400, 0xFF30A46C, 0xFF12A5A0,
            0xFF3E8BFF, 0xFF8E4EC6, 0xFFE93D82, 0xFF8B8D98};

    /** Typing continues a span that ends right where the cursor is, like any text editor. */
    private static final int FLAGS = Spanned.SPAN_EXCLUSIVE_INCLUSIVE;
    private static float density = 2f;
    private static android.util.DisplayMetrics metrics;
    /** The note-wide pinch-zoom factor; sized text follows it so zooming keeps proportions. */
    static float zoom = 1f;

    private RichText() {
    }

    static void init(Context c) {
        density = c.getResources().getDisplayMetrics().density;
        metrics = c.getResources().getDisplayMetrics();
    }

    private static int codeBg() {
        return Pal.dark ? 0xFF2E3633 : 0xFFDDE3E0;
    }

    // ------------------------------------------------------------- spans

    static final class Hl extends CharacterStyle implements UpdateAppearance {
        final int color;

        Hl(int color) {
            this.color = color;
        }

        @Override
        public void updateDrawState(TextPaint tp) {
            tp.bgColor = color;
            tp.setColor(0xFF1A1A1A);
        }
    }

    /** Coloured text. Highlighted and code text keep their own look. */
    static final class Fg extends CharacterStyle implements UpdateAppearance {
        final int color;

        Fg(int color) {
            this.color = color;
        }

        @Override
        public void updateDrawState(TextPaint tp) {
            if (tp.bgColor == 0) tp.setColor(color);
        }
    }

    /** A list line nested {@code level} steps in; wrapped lines keep the same margin. */
    static final class Indent implements android.text.style.LeadingMarginSpan {
        final int level;

        Indent(int level) {
            this.level = Math.max(0, Math.min(MAX_INDENT, level));
        }

        @Override
        public int getLeadingMargin(boolean first) {
            return Math.round(level * 24 * density);
        }

        @Override
        public void drawLeadingMargin(Canvas c, Paint p, int x, int dir, int top, int baseline,
                                      int bottom, CharSequence text, int start, int end, boolean first,
                                      android.text.Layout layout) {
        }
    }

    /** Text at a size picked by the user (sp at normal zoom). */
    static final class Sz extends MetricAffectingSpan {
        final int sp;

        Sz(int sp) {
            this.sp = Math.max(MIN_SIZE, Math.min(MAX_SIZE, sp));
        }

        @Override
        public void updateMeasureState(TextPaint p) {
            float px = metrics != null
                    ? android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, sp * zoom, metrics)
                    : sp * zoom * density;
            p.setTextSize(px);
        }

        @Override
        public void updateDrawState(TextPaint p) {
            updateMeasureState(p);
        }
    }

    static final class Code extends MetricAffectingSpan {
        @Override
        public void updateMeasureState(TextPaint p) {
            p.setTypeface(Typeface.MONOSPACE);
        }

        @Override
        public void updateDrawState(TextPaint p) {
            p.setTypeface(Typeface.MONOSPACE);
            p.bgColor = codeBg();
        }
    }

    static final class Head extends MetricAffectingSpan {
        @Override
        public void updateMeasureState(TextPaint p) {
            p.setTextSize(p.getTextSize() * 1.4f);
            p.setFakeBoldText(true);
        }

        @Override
        public void updateDrawState(TextPaint p) {
            updateMeasureState(p);
        }
    }

    /** Text in one of the bundled fonts (bold / italic follow the surrounding styles). */
    static final class Fnt extends MetricAffectingSpan {
        final int idx;

        Fnt(int idx) {
            this.idx = Math.max(0, Math.min(NoteFonts.count() - 1, idx));
        }

        private void set(TextPaint p) {
            Typeface cur = p.getTypeface();
            int st = cur == null ? Typeface.NORMAL : cur.getStyle();
            Typeface f = NoteFonts.face(idx);
            p.setTypeface(st == Typeface.NORMAL ? f : Typeface.create(f, st));
        }

        @Override
        public void updateMeasureState(TextPaint p) {
            set(p);
        }

        @Override
        public void updateDrawState(TextPaint p) {
            set(p);
        }
    }

    /** A quote: accent bar on the left, serif italic text. */
    static final class Quote extends MetricAffectingSpan implements android.text.style.LeadingMarginSpan {
        private void set(TextPaint p) {
            p.setTypeface(Typeface.create(NoteFonts.face(2), Typeface.ITALIC));
        }

        @Override
        public void updateMeasureState(TextPaint p) {
            set(p);
        }

        @Override
        public void updateDrawState(TextPaint p) {
            set(p);
        }

        @Override
        public int getLeadingMargin(boolean first) {
            return Math.round(16 * density);
        }

        @Override
        public void drawLeadingMargin(Canvas c, Paint p, int x, int dir, int top, int baseline,
                                      int bottom, CharSequence text, int start, int end, boolean first,
                                      android.text.Layout layout) {
            int old = p.getColor();
            Paint.Style os = p.getStyle();
            p.setStyle(Paint.Style.FILL);
            p.setColor(Pal.accent);
            float w = 3 * density;
            c.drawRect(dir > 0 ? x : x - w, top, dir > 0 ? x + w : x, bottom, p);
            p.setColor(old);
            p.setStyle(os);
        }
    }

    /** A callout: tinted panel with an accent edge. */
    static final class Callout extends MetricAffectingSpan
            implements LineBackgroundSpan, android.text.style.LeadingMarginSpan {
        @Override
        public void updateMeasureState(TextPaint p) {
        }

        @Override
        public void updateDrawState(TextPaint p) {
        }

        @Override
        public int getLeadingMargin(boolean first) {
            return Math.round(16 * density);
        }

        @Override
        public void drawLeadingMargin(Canvas c, Paint p, int x, int dir, int top, int baseline,
                                      int bottom, CharSequence text, int start, int end, boolean first,
                                      android.text.Layout layout) {
        }

        @Override
        public void drawBackground(Canvas c, Paint p, int left, int right, int top, int baseline,
                                   int bottom, CharSequence text, int start, int end, int lnum) {
            int old = p.getColor();
            Paint.Style os = p.getStyle();
            p.setStyle(Paint.Style.FILL);
            p.setColor((Pal.accent & 0x00FFFFFF) | (Pal.dark ? 0x2E000000 : 0x26000000));
            c.drawRect(left, top, right, bottom, p);
            p.setColor(Pal.accent);
            c.drawRect(left, top, left + 4 * density, bottom, p);
            p.setColor(old);
            p.setStyle(os);
        }
    }

    /** Whole paragraphs folded away under a collapsed toggle: invisible and zero height. */
    static final class Hide extends MetricAffectingSpan implements android.text.style.LineHeightSpan {
        @Override
        public void updateMeasureState(TextPaint p) {
            p.setTextSize(1f);
        }

        @Override
        public void updateDrawState(TextPaint p) {
            p.setTextSize(1f);
            p.setColor(0);
            p.bgColor = 0;
        }

        @Override
        public void chooseHeight(CharSequence text, int start, int end, int spanstartv, int lineHeight,
                                 Paint.FontMetricsInt fm) {
            fm.ascent = 0;
            fm.descent = 0;
            fm.top = 0;
            fm.bottom = 0;
        }
    }

    static boolean hiddenAt(CharSequence text, int start, int end) {
        return text instanceof Spanned && ((Spanned) text).getSpans(start, Math.max(end, start + 1), Hide.class).length > 0;
    }

    /** A thin horizontal rule standing in for one placeholder character. */
    static final class Rule extends android.text.style.ReplacementSpan {
        @Override
        public int getSize(Paint p, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
            if (fm != null) {
                int h = Math.round(p.getTextSize() * 0.9f);
                fm.ascent = -h;
                fm.top = -h;
                fm.descent = 0;
                fm.bottom = 0;
            }
            return tableWidth > 0 ? tableWidth : Math.round(300 * density);
        }

        @Override
        public void draw(Canvas c, CharSequence text, int start, int end, float x, int top, int y,
                         int bottom, Paint p) {
            if (hiddenAt(text, start, end)) return;
            int old = p.getColor();
            Paint.Style os = p.getStyle();
            float sw = p.getStrokeWidth();
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1.5f, 1.5f * density));
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(Pal.dark ? 0xFF4A5753 : 0xFF9AA5A1);
            float w = tableWidth > 0 ? tableWidth : 300 * density;
            float cy = y - p.getTextSize() * 0.4f;
            c.drawLine(x + 2 * density, cy, x + w - 2 * density, cy, p);
            p.setColor(old);
            p.setStyle(os);
            p.setStrokeWidth(sw);
        }
    }

    /** Terminal-style code block: black panel with a border, indented light monospace text, accent bar. */
    static final class Block extends MetricAffectingSpan implements LineBackgroundSpan,
            android.text.style.LeadingMarginSpan, android.text.style.LineHeightSpan {
        static final int BG = 0xFF000000;
        static final int FG = 0xFFE6EDF3;

        @Override
        public void updateMeasureState(TextPaint p) {
            p.setTypeface(Typeface.MONOSPACE);
        }

        @Override
        public void updateDrawState(TextPaint p) {
            p.setTypeface(Typeface.MONOSPACE);
            p.setColor(FG);
        }

        /** Breathing room above the first line and below the last line of the block. */
        @Override
        public void chooseHeight(CharSequence text, int start, int end, int spanstartv, int lineHeight,
                                 Paint.FontMetricsInt fm) {
            if (!(text instanceof Spanned)) return;
            Spanned sp = (Spanned) text;
            int pad = Math.round(8 * density);
            int a = sp.getSpanStart(this);
            int b = sp.getSpanEnd(this);
            // only lines that really belong to this block; lines before/after never match
            if (a < 0 || start < a || start >= Math.max(b, a + 1)) return;
            if (start == a) {
                fm.ascent -= pad;
                fm.top -= pad;
            }
            if (end >= b) {
                fm.descent += pad;
                fm.bottom += pad;
            }
        }

        @Override
        public int getLeadingMargin(boolean first) {
            return Math.round(14 * density);
        }

        @Override
        public void drawLeadingMargin(Canvas c, Paint p, int x, int dir, int top, int baseline,
                                      int bottom, CharSequence text, int start, int end, boolean first,
                                      android.text.Layout layout) {
        }

        @Override
        public void drawBackground(Canvas c, Paint p, int left, int right, int top, int baseline,
                                   int bottom, CharSequence text, int start, int end, int lnum) {
            int old = p.getColor();
            Paint.Style os = p.getStyle();
            float sw = p.getStrokeWidth();
            p.setStyle(Paint.Style.FILL);
            p.setColor(BG);
            c.drawRect(left, top, right, bottom, p);
            // border so the panel stands out in dark mode too
            p.setColor(Pal.dark ? 0xFF4A5753 : 0xFF000000);
            float t = 1.5f * density;
            c.drawRect(right - t, top, right, bottom, p);
            if (text instanceof Spanned) {
                Spanned sp = (Spanned) text;
                if (sp.getSpanStart(this) >= start) c.drawRect(left, top, right, top + t, p);
                if (sp.getSpanEnd(this) <= end + 1) c.drawRect(left, bottom - t, right, bottom, p);
            }
            p.setColor(Pal.accent);
            c.drawRect(left, top, left + 4 * density, bottom, p);
            p.setColor(old);
            p.setStyle(os);
            p.setStrokeWidth(sw);
        }
    }


    // ------------------------------------------------------------- tables

    /** Width (px) tables are drawn at; set by the editor from its own width. */
    static int tableWidth = 0;

    /** Object-replacement character that stands in for a table in the text. */
    static final char TABLE_CHAR = '\uFFFC';

    /**
     * A table drawn as one block in place of a single placeholder character. The cells live in the span
     * (and in the saved note), and are edited in a dialog with real text fields, so typing always works.
     * First row = column headers (bold, tinted).
     */
    static final class Tbl extends android.text.style.ReplacementSpan {
        String[][] cells;

        Tbl(String[][] cells) {
            this.cells = cells;
        }

        int rows() {
            return cells.length;
        }

        int cols() {
            return cells.length == 0 ? 0 : cells[0].length;
        }

        static String[][] blank(int rows, int cols) {
            String[][] c = new String[rows][cols];
            for (int r = 0; r < rows; r++) {
                for (int k = 0; k < cols; k++) c[r][k] = r == 0 ? "Column " + (k + 1) : "";
            }
            return c;
        }

        private int width() {
            return tableWidth > 0 ? tableWidth : Math.round(300 * density);
        }

        /** 0 = no tick box in this cell, 1 = empty box, 2 = ticked. */
        static int tick(String cell) {
            if (cell == null) return 0;
            if (cell.startsWith(CHECK_OFF) || cell.equals("\u2610")) return 1;
            if (cell.startsWith(CHECK_ON) || cell.equals("\u2611")) return 2;
            return 0;
        }

        static String label(String cell) {
            if (tick(cell) == 0) return cell == null ? "" : cell;
            return cell.length() <= 2 ? "" : cell.substring(2);
        }

        static String compose(int state, String label) {
            if (state == 0) return label;
            return (state == 2 ? CHECK_ON : CHECK_OFF) + label;
        }

        /** Row height in px for the paint the table is drawn with. */
        int rowH(Paint p) {
            return Math.round(p.getTextSize() * 1.25f + p.getTextSize() * 1.0f);
        }

        float colW() {
            return width() / (float) Math.max(1, cols());
        }

        /** Left padding inside a cell. */
        float padX(Paint p) {
            return p.getTextSize() * 0.5f;
        }

        /** Where a cell's text starts, measured from the cell's left edge (after the tick circle, if any). */
        float textInset(Paint p, int r, int k) {
            float in = padX(p);
            if (tick(cells[r][k]) > 0) in += p.getTextSize() * 1.3f;
            return in;
        }

        /** The {row, col} under (xl, yl), measured from the table's top-left; null when outside. */
        int[] cellAt(float xl, float yl, Paint p) {
            int rows = rows();
            int cols = cols();
            if (rows == 0 || cols == 0) return null;
            int r = (int) (yl / rowH(p));
            int k = (int) (xl / colW());
            if (yl < 0 || xl < 0 || r >= rows || k >= cols) return null;
            return new int[]{r, k};
        }

        /** True if (xl, yl) is on the tick circle of a cell that has one. */
        boolean onTick(float xl, float yl, Paint p) {
            int[] rc = cellAt(xl, yl, p);
            if (rc == null || tick(cells[rc[0]][rc[1]]) == 0) return false;
            float into = xl - rc[1] * colW();
            return into <= padX(p) + p.getTextSize() * 1.3f + 8 * density;
        }

        /** Flips a tick box at (xl, yl) measured from the table's top-left; true if one was hit. */
        boolean toggleAt(float xl, float yl, Paint p) {
            if (!onTick(xl, yl, p)) return false;
            int[] rc = cellAt(xl, yl, p);
            int st = tick(cells[rc[0]][rc[1]]);
            cells[rc[0]][rc[1]] = compose(st == 1 ? 2 : 1, label(cells[rc[0]][rc[1]]));
            return true;
        }

        /** none -> empty box -> ticked -> none */
        void cycleTick(int r, int k) {
            int st = (tick(cells[r][k]) + 1) % 3;
            cells[r][k] = compose(st, label(cells[r][k]));
        }

        /** Ticks / unticks a cell that already has a box. */
        void flipTick(int r, int k) {
            int st = tick(cells[r][k]);
            if (st == 0) return;
            cells[r][k] = compose(st == 1 ? 2 : 1, label(cells[r][k]));
        }

        static String[][] kanban() {
            return new String[][]{{"To do", "Doing", "Done"}, {"", "", ""}, {"", "", ""}, {"", "", ""}};
        }

        void addRow(int at) {
            at = Math.max(1, Math.min(rows(), at));
            String[][] n = new String[rows() + 1][];
            for (int r = 0, o = 0; r < n.length; r++) {
                if (r == at) {
                    n[r] = new String[cols()];
                    java.util.Arrays.fill(n[r], "");
                } else {
                    n[r] = cells[o++];
                }
            }
            cells = n;
        }

        void addCol(int at) {
            at = Math.max(0, Math.min(cols(), at));
            for (int r = 0; r < rows(); r++) {
                String[] row = new String[cols() + 1];
                for (int k = 0, o = 0; k < row.length; k++) {
                    row[k] = k == at ? (r == 0 ? "Column " + (cols() + 1) : "") : cells[r][o++];
                }
                cells[r] = row;
            }
        }

        boolean delRow(int at) {
            if (rows() <= 2 || at < 1 || at >= rows()) return false; // header + one row stay
            String[][] n = new String[rows() - 1][];
            for (int r = 0, o = 0; r < rows(); r++) if (r != at) n[o++] = cells[r];
            cells = n;
            return true;
        }

        boolean delCol(int at) {
            if (cols() <= 2 || at < 0 || at >= cols()) return false;
            for (int r = 0; r < rows(); r++) {
                String[] row = new String[cols() - 1];
                for (int k = 0, o = 0; k < cols(); k++) if (k != at) row[o++] = cells[r][k];
                cells[r] = row;
            }
            return true;
        }

        /** Swaps two cells (used to move a Kanban card between columns). */
        void swap(int r1, int k1, int r2, int k2) {
            String t = cells[r1][k1];
            cells[r1][k1] = cells[r2][k2];
            cells[r2][k2] = t;
        }

        @Override
        public int getSize(Paint p, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
            if (fm != null) {
                int h = rowH(p) * rows();
                fm.ascent = -h;
                fm.top = -h;
                fm.descent = 0;
                fm.bottom = 0;
            }
            return width();
        }

        @Override
        public void draw(Canvas c, CharSequence text, int start, int end, float x, int top, int y,
                         int bottom, Paint p) {
            int rows = rows();
            int cols = cols();
            if (rows == 0 || cols == 0 || hiddenAt(text, start, end)) return;
            float w = width();
            float rh = rowH(p);
            float colW = w / cols;
            float t0 = y - rh * rows;
            float pad = p.getTextSize() * 0.5f;
            float rad = 10 * density;
            int oldColor = p.getColor();
            Paint.Style oldStyle = p.getStyle();
            Typeface oldFace = p.getTypeface();
            boolean oldFake = p.isFakeBoldText();

            Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
            Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
            line.setColor(Pal.dark ? 0xFF4A5753 : 0xFF9AA5A1);
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeWidth(Math.max(1f, density));
            Paint sep = new Paint(Paint.ANTI_ALIAS_FLAG);
            sep.setColor((line.getColor() & 0x00FFFFFF) | 0x80000000);
            sep.setStrokeWidth(Math.max(1f, density));

            android.graphics.RectF box = new android.graphics.RectF(x + 1, t0 + 1, x + w - 1, y - 1);
            c.save();
            android.graphics.Path clip = new android.graphics.Path();
            clip.addRoundRect(box, rad, rad, android.graphics.Path.Direction.CW);
            c.clipPath(clip);
            // header row: clearly different from the body rows
            fill.setColor((Pal.accent & 0x00FFFFFF) | (Pal.dark ? 0x38000000 : 0x2E000000));
            c.drawRect(x, t0, x + w, t0 + rh, fill);
            // body rows sit on a slightly raised card colour
            fill.setColor(Pal.card);
            c.drawRect(x, t0 + rh, x + w, y, fill);
            c.restore();

            for (int r = 1; r < rows; r++) c.drawLine(x, t0 + r * rh, x + w, t0 + r * rh, r == 1 ? line : sep);
            for (int k = 1; k < cols; k++) c.drawLine(x + k * colW, t0, x + k * colW, y, sep);
            c.drawRoundRect(box, rad, rad, line);

            android.text.TextPaint tp = new android.text.TextPaint(p);
            tp.setStyle(Paint.Style.FILL);
            tp.setColor(Pal.ink);
            for (int r = 0; r < rows; r++) {
                tp.setFakeBoldText(r == 0);
                tp.setTypeface(r == 0 ? Typeface.create(oldFace, Typeface.BOLD) : oldFace);
                Paint.FontMetrics fm = tp.getFontMetrics();
                float base = t0 + r * rh + rh / 2f - (fm.ascent + fm.descent) / 2f;
                for (int k = 0; k < cols; k++) {
                    String raw = cells[r][k] == null ? "" : cells[r][k].replace('\n', ' ');
                    int st = tick(raw);
                    String cell = label(raw);
                    float tx = x + k * colW + pad;
                    if (st > 0) { // tick box that fits inside the cell
                        float size = tp.getTextSize();
                        float rad2 = size * 0.42f;
                        float cx2 = tx + rad2;
                        float cy2 = t0 + r * rh + rh / 2f;
                        Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
                        ring.setStrokeCap(Paint.Cap.ROUND);
                        ring.setStrokeJoin(Paint.Join.ROUND);
                        if (st == 2) {
                            ring.setColor(Pal.accent);
                            ring.setStyle(Paint.Style.FILL);
                            c.drawCircle(cx2, cy2, rad2, ring);
                            ring.setStyle(Paint.Style.STROKE);
                            ring.setStrokeWidth(size * 0.11f);
                            ring.setColor(Pal.onAccent);
                            android.graphics.Path kk = new android.graphics.Path();
                            kk.moveTo(cx2 - rad2 * 0.45f, cy2 + rad2 * 0.02f);
                            kk.lineTo(cx2 - rad2 * 0.1f, cy2 + rad2 * 0.38f);
                            kk.lineTo(cx2 + rad2 * 0.48f, cy2 - rad2 * 0.34f);
                            c.drawPath(kk, ring);
                        } else {
                            ring.setStyle(Paint.Style.STROKE);
                            ring.setStrokeWidth(size * 0.09f);
                            ring.setColor(Pal.muted);
                            c.drawCircle(cx2, cy2, rad2, ring);
                        }
                        tx += size * 1.1f + size * 0.2f;
                    }
                    if (cell.isEmpty()) continue;
                    tp.setStrikeThruText(st == 2);
                    tp.setColor(st == 2 ? Pal.muted : Pal.ink);
                    float room = x + (k + 1) * colW - pad - tx;
                    CharSequence cut = android.text.TextUtils.ellipsize(cell, tp, Math.max(10, room),
                            android.text.TextUtils.TruncateAt.END);
                    c.drawText(cut, 0, cut.length(), tx, base, tp);
                    tp.setStrikeThruText(false);
                    tp.setColor(Pal.ink);
                }
            }
            p.setColor(oldColor);
            p.setStyle(oldStyle);
            p.setTypeface(oldFace);
            p.setFakeBoldText(oldFake);
        }
    }

    /** The table whose placeholder is on the line containing {@code pos}, or null. */
    static Tbl tableOnLine(Spanned e, int pos) {
        int ls = lineStart(e, pos);
        int le = lineEnd(e, pos);
        Tbl[] found = e.getSpans(ls, Math.min(e.length(), le + 0), Tbl.class);
        for (Tbl t : found) {
            int a = e.getSpanStart(t);
            if (a >= ls && a <= le) return t;
        }
        return null;
    }

    /** Puts a new table on its own line at {@code pos}; returns the placeholder's start. */
    static int insertTable(Editable e, int pos, int cols, int rows) {
        return insertTable(e, pos, new Tbl(Tbl.blank(rows, cols)));
    }

    static int insertTable(Editable e, int pos, Tbl table) {
        int at = lineStart(e, pos);
        boolean lineHasText = lineEnd(e, pos) > at;
        int ins = lineHasText ? lineEnd(e, pos) : at;
        StringBuilder sb = new StringBuilder();
        if (lineHasText) sb.append('\n');
        int off = sb.length();
        sb.append(TABLE_CHAR);
        sb.append('\n'); // a line below to keep typing on
        e.insert(ins, sb.toString());
        int s = ins + off;
        e.setSpan(table, s, s + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return s;
    }

    /** Puts a divider line on its own line at {@code pos}. */
    static int insertRule(Editable e, int pos) {
        int at = lineStart(e, pos);
        boolean lineHasText = lineEnd(e, pos) > at;
        int ins = lineHasText ? lineEnd(e, pos) : at;
        String sb = (lineHasText ? "\n" : "") + RULE_CHAR + "\n";
        e.insert(ins, sb);
        int s0 = ins + (lineHasText ? 1 : 0);
        e.setSpan(new Rule(), s0, s0 + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return s0;
    }

    // ---- toggles: a "▸ " / "▾ " line whose deeper-indented lines below it fold away

    /** End (exclusive) of the lines folded under the toggle at {@code ls}: the first line that is not deeper. */
    static int childEnd(Spanned e, int ls) {
        int lvl = indentOf(e, ls);
        int le = lineEnd(e, ls);
        if (le >= e.length()) return e.length();
        int p = le + 1;
        while (p <= e.length()) {
            if (p >= e.length() || indentOf(e, p) <= lvl) return p;
            int en = lineEnd(e, p);
            if (en >= e.length()) return e.length();
            p = en + 1;
        }
        return e.length();
    }

    /** Rebuilds every fold from the toggle markers (closed ▸ hides its children). */
    static void refold(Editable e) {
        for (Hide h : e.getSpans(0, e.length(), Hide.class)) e.removeSpan(h);
        String all = e.toString();
        if (all.indexOf('\u25B8') < 0) return;
        int n = e.length();
        int p = 0;
        while (p <= n) {
            if (prefixAt(e, p) == P_TOGGLE_CLOSED) {
                int a = lineEnd(e, p) + 1;
                int b = childEnd(e, p);
                if (a < b) {
                    e.setSpan(new Hide(), a, b, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    p = b;
                    continue;
                }
            }
            int le = lineEnd(e, p);
            if (le >= n) break;
            p = le + 1;
        }
    }

    /** Opens / closes the toggle at line start {@code ls}. */
    static void flipToggle(Editable e, int ls) {
        int k = prefixAt(e, ls);
        if (!isToggle(k)) return;
        e.replace(ls, ls + 1, k == P_TOGGLE_CLOSED ? "\u25BE" : "\u25B8");
        refold(e);
    }

    /** Turns the line at {@code pos} into a toggle (or back into plain text). Returns the cursor shift. */
    static int toggleToggle(Editable e, int pos) {
        int ls = lineStart(e, pos);
        int k = prefixAt(e, ls);
        int lvl = indentOf(e, ls);
        int delta;
        if (isToggle(k)) {
            e.delete(ls, ls + 2);
            delta = -2;
        } else if (k != P_NONE) {
            if (k == P_CHECK_ON) apply(e, STRIKE, ls + 2, lineEnd(e, ls), 0, false);
            e.replace(ls, ls + 2, TOGGLE_OPEN);
            delta = 0;
        } else {
            int nl = numLen(e, ls);
            if (nl > 0) e.delete(ls, ls + nl);
            e.insert(ls, TOGGLE_OPEN);
            delta = 2 - nl;
        }
        if (lvl > 0) setIndent(e, ls, lvl);
        refold(e);
        return delta;
    }

    /** Re-sets the span so the layout re-measures it after its cells changed. */
    static void refresh(Editable e, Tbl t) {
        int a = e.getSpanStart(t);
        int b = e.getSpanEnd(t);
        if (a < 0) return;
        e.removeSpan(t);
        e.setSpan(t, a, b, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    /** Text of every table's cells, for search. */
    static String tablesPlain(CharSequence text) {
        if (!(text instanceof Spanned)) return tidy(text.toString());
        Spanned sp = (Spanned) text;
        StringBuilder out = new StringBuilder(text);
        Tbl[] tables = sp.getSpans(0, sp.length(), Tbl.class);
        java.util.Arrays.sort(tables, (x, y) -> sp.getSpanStart(x) - sp.getSpanStart(y));
        for (Tbl t : tables) {
            StringBuilder cellText = new StringBuilder();
            for (String[] row : t.cells) {
                for (String cell : row) cellText.append(cell).append(' ');
            }
            int i = out.indexOf(String.valueOf(TABLE_CHAR));
            if (i >= 0) out.replace(i, i + 1, cellText.toString().trim());
        }
        return tidy(out.toString());
    }

    /** Plain text for previews and search: no divider glyphs or toggle arrows. */
    static String tidy(String t) {
        return t.replace(String.valueOf(RULE_CHAR), " ").replace(TOGGLE_CLOSED, "").replace(TOGGLE_OPEN, "");
    }

    static Object make(int type, int arg) {
        switch (type) {
            case TABLE:
                return new Tbl(Tbl.blank(2, Math.max(2, arg)));
            case BOLD:
                return new StyleSpan(Typeface.BOLD);
            case ITALIC:
                return new StyleSpan(Typeface.ITALIC);
            case UNDER:
                return new UnderlineSpan();
            case STRIKE:
                return new StrikethroughSpan();
            case HILITE:
                return new Hl(arg);
            case CODE:
                return new Code();
            case HEAD:
                return new Head();
            case SIZE:
                return new Sz(arg);
            case FG:
                return new Fg(arg);
            case INDENT:
                return new Indent(arg);
            case FONT:
                return new Fnt(arg);
            case QUOTE:
                return new Quote();
            case HIDE:
                return new Hide();
            case RULE:
                return new Rule();
            case CALLOUT:
                return new Callout();
            default:
                return new Block();
        }
    }

    static int typeOf(Object o) {
        if (o instanceof StyleSpan) {
            return ((StyleSpan) o).getStyle() == Typeface.ITALIC ? ITALIC : BOLD;
        }
        if (o instanceof UnderlineSpan) return UNDER;
        if (o instanceof StrikethroughSpan) return STRIKE;
        if (o instanceof Hl) return HILITE;
        if (o instanceof Code) return CODE;
        if (o instanceof Head) return HEAD;
        if (o instanceof Block) return BLOCK;
        if (o instanceof Tbl) return TABLE;
        if (o instanceof Sz) return SIZE;
        if (o instanceof Fg) return FG;
        if (o instanceof Indent) return INDENT;
        if (o instanceof Fnt) return FONT;
        if (o instanceof Quote) return QUOTE;
        if (o instanceof Hide) return HIDE;
        if (o instanceof Rule) return RULE;
        if (o instanceof Callout) return CALLOUT;
        return -1;
    }

    private static int argOf(Object o) {
        if (o instanceof Tbl) return ((Tbl) o).cols();
        if (o instanceof Sz) return ((Sz) o).sp;
        if (o instanceof Fg) return ((Fg) o).color;
        if (o instanceof Indent) return ((Indent) o).level;
        if (o instanceof Fnt) return ((Fnt) o).idx;
        return o instanceof Hl ? ((Hl) o).color : 0;
    }

    // ------------------------------------------------------- editing ops

    /** Adds ({@code on}) or removes a style over [s, en), splitting spans that stick out. */
    static void apply(Editable e, int type, int s, int en, int arg, boolean on) {
        if (s > en) {
            int t = s;
            s = en;
            en = t;
        }
        if (s < en) {
            for (Object o : e.getSpans(s, en, Object.class)) {
                if (typeOf(o) != type) continue;
                int a = e.getSpanStart(o);
                int b = e.getSpanEnd(o);
                if (b <= s || a >= en) continue;
                int keep = argOf(o);
                e.removeSpan(o);
                if (a < s) e.setSpan(make(type, keep), a, s, FLAGS);
                if (b > en) e.setSpan(make(type, keep), en, b, FLAGS);
            }
            if (on) e.setSpan(make(type, arg), s, en, FLAGS);
        }
        if (type == BLOCK && !on) apply(e, CODE, s, en, 0, false); // notes saved earlier had inline code inside blocks
    }

    /** The custom size (sp) of the text in [s, en), or the character before the cursor if s == en; 0 = none, -1 = mixed. */
    static int sizeAt(Spanned e, int s, int en) {
        if (s == en) {
            if (s == 0) return 0;
            s = s - 1;
            en = s + 1;
        }
        int found = 0;
        for (int i = s; i < en; i++) {
            int here = 0;
            for (Sz z : e.getSpans(i, i + 1, Sz.class)) here = z.sp;
            if (i == s) found = here;
            else if (here != found) return -1;
        }
        return found;
    }

    /** The font index of the text in [s, en) (or before the cursor); 0 = default, -1 = mixed. */
    static int fontAt(Spanned e, int s, int en) {
        if (s == en) {
            if (s == 0) return 0;
            s = s - 1;
            en = s + 1;
        }
        int found = 0;
        for (int i = s; i < en; i++) {
            int here = 0;
            for (Fnt z : e.getSpans(i, i + 1, Fnt.class)) here = z.idx;
            if (i == s) found = here;
            else if (here != found) return -1;
        }
        return found;
    }

    /** True when every character in [s, en) has the style (or the one before the cursor if s == en). */
    static boolean active(Spanned e, int type, int s, int en) {
        if (s == en) {
            if (s == 0) return false;
            s = s - 1;
            en = s + 1;
        }
        boolean[] covered = new boolean[en - s];
        for (Object o : e.getSpans(s, en, Object.class)) {
            if (typeOf(o) != type) continue;
            int a = Math.max(s, e.getSpanStart(o));
            int b = Math.min(en, e.getSpanEnd(o));
            for (int i = a; i < b; i++) covered[i - s] = true;
        }
        for (boolean c : covered) {
            if (!c) return false;
        }
        return true;
    }

    static int lineStart(CharSequence t, int pos) {
        int i = Math.max(0, Math.min(pos, t.length()));
        while (i > 0 && t.charAt(i - 1) != '\n') i--;
        return i;
    }

    static int lineEnd(CharSequence t, int pos) {
        int i = Math.max(0, Math.min(pos, t.length()));
        while (i < t.length() && t.charAt(i) != '\n') i++;
        return i;
    }

    static final String CHECK_OFF = "\u2610 ";
    static final String CHECK_ON = "\u2611 ";
    static final int P_NONE = 0;
    static final int P_BULLET = 1;
    static final int P_CHECK_OFF = 2;
    static final int P_CHECK_ON = 3;
    static final int P_TOGGLE_CLOSED = 4;
    static final int P_TOGGLE_OPEN = 5;
    static final String TOGGLE_CLOSED = "\u25B8 ";
    static final String TOGGLE_OPEN = "\u25BE ";

    static boolean isCheck(int kind) {
        return kind == P_CHECK_OFF || kind == P_CHECK_ON;
    }

    static boolean isToggle(int kind) {
        return kind == P_TOGGLE_CLOSED || kind == P_TOGGLE_OPEN;
    }

    /** Which list marker the line starting at {@code pos} has. */
    static int prefixAt(CharSequence t, int pos) {
        if (startsWith(t, pos, BULLET)) return P_BULLET;
        if (startsWith(t, pos, CHECK_OFF)) return P_CHECK_OFF;
        if (startsWith(t, pos, CHECK_ON)) return P_CHECK_ON;
        if (startsWith(t, pos, TOGGLE_CLOSED)) return P_TOGGLE_CLOSED;
        if (startsWith(t, pos, TOGGLE_OPEN)) return P_TOGGLE_OPEN;
        return P_NONE;
    }

    /**
     * Makes every line touched by [s, en) a bullet ({@code check} false) or a to-do item ({@code check}
     * true); lines that are already that kind lose the marker instead. Returns the new cursor end.
     */
    static int toggleList(Editable e, int s, int en, boolean check) {
        java.util.ArrayList<Integer> starts = new java.util.ArrayList<>();
        int p = lineStart(e, s);
        int stop = lineStart(e, Math.max(s, en));
        while (true) {
            starts.add(p);
            int nl = lineEnd(e, p);
            if (p >= stop || nl >= e.length()) break;
            p = nl + 1;
        }
        boolean allSame = true;
        for (int st : starts) {
            int k = prefixAt(e, st);
            boolean same = check ? (k == P_CHECK_OFF || k == P_CHECK_ON) : k == P_BULLET;
            if (!same) allSame = false;
        }
        int delta = 0;
        for (int i = starts.size() - 1; i >= 0; i--) { // bottom up keeps earlier offsets valid
            int st = starts.get(i);
            int k = prefixAt(e, st);
            boolean leavingTicked = k == P_CHECK_ON && !(check && !allSame);
            if (leavingTicked) apply(e, STRIKE, st + 2, lineEnd(e, st), 0, false); // un-strike its text
            if (allSame) {
                e.delete(st, st + 2);
                delta -= 2;
                continue;
            }
            boolean same = check ? (k == P_CHECK_OFF || k == P_CHECK_ON) : k == P_BULLET;
            if (same) continue;
            if (k != P_NONE) { // switch between bullet and to-do
                e.replace(st, st + 2, check ? CHECK_OFF : BULLET);
            } else {
                e.insert(st, check ? CHECK_OFF : BULLET);
                delta += 2;
            }
        }
        return Math.max(0, en + delta);
    }

    /** Ticks / unticks the to-do item at line start {@code ls}; ticked items are struck through. */
    static void flipCheck(Editable e, int ls) {
        int k = prefixAt(e, ls);
        if (k != P_CHECK_OFF && k != P_CHECK_ON) return;
        boolean nowOn = k == P_CHECK_OFF;
        int lvl = indentOf(e, ls);
        e.replace(ls, ls + 1, nowOn ? "\u2611" : "\u2610");
        if (lvl > 0) setIndent(e, ls, lvl);
        apply(e, STRIKE, ls + 2, lineEnd(e, ls), 0, nowOn);
    }

    static boolean startsWith(CharSequence t, int pos, String prefix) {
        if (pos + prefix.length() > t.length()) return false;
        for (int i = 0; i < prefix.length(); i++) {
            if (t.charAt(pos + i) != prefix.charAt(i)) return false;
        }
        return true;
    }


    // ------------------------------------------------- colours, clearing, nesting, numbers

    /** The colour arg of a HILITE / FG range covering the character at {@code pos}, or 0. */
    static int colorAt(Spanned e, int type, int pos) {
        if (pos < 0 || pos >= e.length()) return 0;
        int found = 0;
        for (Object o : e.getSpans(pos, pos + 1, Object.class)) {
            if (typeOf(o) == type) found = argOf(o);
        }
        return found;
    }

    /** Removes every character style (bold .. size, colours, highlight, code) from [s, en). */
    static void clearStyles(Editable e, int s, int en) {
        int[] types = {BOLD, ITALIC, UNDER, HILITE, CODE, SIZE, FG, FONT};
        for (int t : types) apply(e, t, s, en, 0, false);
        // ticked to-do lines stay struck through; everything else loses it
        int p = lineStart(e, s);
        while (p < en && p <= e.length()) {
            int le = lineEnd(e, p);
            int a = Math.max(s, p);
            int b = Math.min(en, le);
            if (a < b && prefixAt(e, p) != P_CHECK_ON) apply(e, STRIKE, a, b, 0, false);
            else if (a < b) apply(e, STRIKE, Math.max(a, p + 2), b, 0, true);
            if (le >= e.length()) break;
            p = le + 1;
        }
    }

    /** Length of the list marker ("•, ", "[ ] ", "[x] ") at a line start, or 0. */
    static int markerLen(CharSequence t, int ls) {
        return prefixAt(t, ls) != P_NONE ? 2 : numLen(t, ls);
    }

    // ---- nesting

    static int indentOf(Spanned e, int ls) {
        int lvl = 0;
        int le = lineEnd(e, ls);
        int qEnd = Math.min(e.length(), Math.max(le, ls + 1));
        for (Indent o : e.getSpans(ls, qEnd, Indent.class)) {
            if (e.getSpanEnd(o) > ls) lvl = Math.max(lvl, o.level);
        }
        return lvl;
    }

    /** Sets the nesting level of the line starting at {@code ls}. */
    static void setIndent(Editable e, int ls, int level) {
        int le = lineEnd(e, ls);
        int stop = Math.min(e.length(), le + 1);
        for (Indent o : e.getSpans(ls, Math.max(stop, ls), Indent.class)) {
            int a = e.getSpanStart(o);
            int b = e.getSpanEnd(o);
            e.removeSpan(o);
            // keep the parts of a multi-line span that lie on other lines
            if (a < ls) e.setSpan(new Indent(o.level), a, ls, FLAGS);
            if (b > stop) e.setSpan(new Indent(o.level), stop, b, FLAGS);
        }
        level = Math.max(0, Math.min(MAX_INDENT, level));
        if (level > 0 && stop > ls) e.setSpan(new Indent(level), ls, stop, FLAGS);
    }

    // ---- numbered lists ("1. ", "2. " ...)

    /** Length of a "12. " marker at the start of a line, or 0. */
    static int numLen(CharSequence t, int ls) {
        int i = ls;
        while (i < t.length() && i - ls < 3 && Character.isDigit(t.charAt(i))) i++;
        if (i == ls || i + 1 >= t.length()) return 0;
        return t.charAt(i) == '.' && t.charAt(i + 1) == ' ' ? i + 2 - ls : 0;
    }

    static int numValue(CharSequence t, int ls) {
        int n = numLen(t, ls);
        if (n == 0) return 0;
        try {
            return Integer.parseInt(t.subSequence(ls, ls + n - 2).toString());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    /**
     * Re-counts the run of numbered lines around {@code pos}: each nesting level counts on its own and
     * restarts under a shallower item. The line at {@code skip} (the cursor line) is left alone.
     */
    static void renumber(Editable e, int pos, int skip) {
        int p = lineStart(e, pos);
        while (p > 0) { // walk up to the first numbered line of the run
            int prev = lineStart(e, p - 1);
            if (numLen(e, prev) == 0) break;
            p = prev;
        }
        int[] count = new int[MAX_INDENT + 2];
        boolean first = true;
        while (p <= e.length() && numLen(e, p) > 0) {
            int lvl = indentOf(e, p);
            if (first) {
                count[lvl] = Math.max(0, numValue(e, p) - 1);
                first = false;
            }
            count[lvl]++;
            for (int k = lvl + 1; k < count.length; k++) count[k] = 0;
            if (p != skip) {
                int len = numLen(e, p);
                String want = count[lvl] + ". ";
                if (!e.subSequence(p, p + len).toString().equals(want)) {
                    int keep = indentOf(e, p);
                    e.replace(p, p + len, want);
                    if (keep > 0) setIndent(e, p, keep);
                }
            } else {
                count[lvl] = numValue(e, p);
            }
            int le = lineEnd(e, p);
            if (le >= e.length()) break;
            p = le + 1;
        }
    }

    /** Numbers every touched line (1. 2. 3.), or removes the numbers when all of them already have one. */
    static int toggleNumbered(Editable e, int s, int en) {
        java.util.ArrayList<Integer> starts = new java.util.ArrayList<>();
        int p = lineStart(e, s);
        int stop = lineStart(e, Math.max(s, en));
        while (true) {
            starts.add(p);
            int nl = lineEnd(e, p);
            if (p >= stop || nl >= e.length()) break;
            p = nl + 1;
        }
        boolean all = true;
        for (int st : starts) if (numLen(e, st) == 0) all = false;
        int delta = 0;
        for (int i = starts.size() - 1; i >= 0; i--) {
            int st = starts.get(i);
            int n = numLen(e, st);
            if (all) {
                e.delete(st, st + n);
                delta -= n;
                continue;
            }
            if (n > 0) continue;
            int k = prefixAt(e, st);
            if (k == P_CHECK_ON) apply(e, STRIKE, st + 2, lineEnd(e, st), 0, false);
            if (k != P_NONE) { // swap a bullet / to-do box for the number
                e.replace(st, st + 2, "1. ");
                delta += 1;
            } else {
                e.insert(st, "1. ");
                delta += 3;
            }
        }
        if (!all) renumber(e, lineStart(e, s), -1);
        else renumber(e, lineStart(e, Math.min(s, e.length())), -1);
        return Math.max(0, Math.min(e.length(), en + delta));
    }

    // ------------------------------------------------------- persistence

    static String toJson(CharSequence text) {
        try {
            JSONObject o = new JSONObject();
            o.put("t", text.toString());
            JSONArray arr = new JSONArray();
            if (text instanceof Spanned) {
                Spanned sp = (Spanned) text;
                for (Object s : sp.getSpans(0, sp.length(), Object.class)) {
                    int type = typeOf(s);
                    int a = sp.getSpanStart(s);
                    int b = sp.getSpanEnd(s);
                    if (type < 0 || a >= b) continue;
                    arr.put(new JSONArray().put(type).put(a).put(b).put(argOf(s)));
                }
            }
            o.put("s", arr);
            if (text instanceof Spanned) {
                JSONObject tb = new JSONObject();
                Spanned sp = (Spanned) text;
                for (Tbl t : sp.getSpans(0, sp.length(), Tbl.class)) {
                    JSONArray rows = new JSONArray();
                    for (String[] row : t.cells) {
                        JSONArray r = new JSONArray();
                        for (String cell : row) r.put(cell == null ? "" : cell);
                        rows.put(r);
                    }
                    tb.put(String.valueOf(sp.getSpanStart(t)), rows);
                }
                if (tb.length() > 0) o.put("tb", tb);
            }
            return o.toString();
        } catch (JSONException ex) {
            return text.toString();
        }
    }

    /** Loads stored text into {@code out}. Anything that is not our JSON is treated as plain text. */
    static void fromJson(String json, Editable out) {
        try {
            JSONObject o = new JSONObject(json);
            String t = o.getString("t");
            out.append(t);
            JSONArray arr = o.optJSONArray("s");
            if (arr == null) return;
            for (int i = 0; i < arr.length(); i++) {
                JSONArray r = arr.getJSONArray(i);
                int a = Math.max(0, r.getInt(1));
                int b = Math.min(t.length(), r.getInt(2));
                if (r.getInt(0) == TABLE) { // tables carry their cells in "tb"; older tab-based ones are dropped
                    JSONObject tb = o.optJSONObject("tb");
                    JSONArray rows = tb == null ? null : tb.optJSONArray(String.valueOf(a));
                    if (rows == null || rows.length() == 0 || b - a != 1) continue;
                    String[][] cells = new String[rows.length()][];
                    for (int x = 0; x < rows.length(); x++) {
                        JSONArray rr = rows.getJSONArray(x);
                        cells[x] = new String[rr.length()];
                        for (int y = 0; y < rr.length(); y++) cells[x][y] = rr.getString(y);
                    }
                    out.setSpan(new Tbl(cells), a, b, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    continue;
                }
                if (a < b) out.setSpan(make(r.getInt(0), r.optInt(3)), a, b,
                        r.getInt(0) == HIDE || r.getInt(0) == RULE ? Spanned.SPAN_EXCLUSIVE_EXCLUSIVE : FLAGS);
            }
            for (Object blk : out.getSpans(0, out.length(), Block.class)) { // old notes: drop code-in-block
                apply(out, CODE, out.getSpanStart(blk), out.getSpanEnd(blk), 0, false);
            }
        } catch (JSONException ex) {
            out.clear();
            out.append(json);
        }
    }

    static String plain(String json) {
        try {
            return new JSONObject(json).getString("t");
        } catch (JSONException ex) {
            return json;
        }
    }

    static Spannable empty() {
        return Editable.Factory.getInstance().newEditable("");
    }
}
