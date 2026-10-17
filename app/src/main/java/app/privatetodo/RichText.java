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

    static final String BULLET = "• ";
    /** Highlight colours (same in light and dark; highlighted text is always drawn dark). */
    static final int[] HL_COLORS = {0xFFFFE066, 0xFFA8E6A1, 0xFF9FD3FF, 0xFFFFB3C7, 0xFFFFC48A};

    /** Typing continues a span that ends right where the cursor is, like any text editor. */
    private static final int FLAGS = Spanned.SPAN_EXCLUSIVE_INCLUSIVE;
    private static float density = 2f;

    private RichText() {
    }

    static void init(Context c) {
        density = c.getResources().getDisplayMetrics().density;
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

    /** Column stop for a table row (recreated by {@link #syncTables}). */
    static final class Stop extends android.text.style.TabStopSpan.Standard {
        final int x;

        Stop(int x) {
            super(x);
            this.x = x;
        }
    }

    /** Bold header row (not saved; re-applied to the first row of every table). */
    static final class TblHead extends MetricAffectingSpan {
        @Override
        public void updateMeasureState(TextPaint p) {
            p.setFakeBoldText(true);
        }

        @Override
        public void updateDrawState(TextPaint p) {
            p.setFakeBoldText(true);
        }
    }

    /**
     * A table. Rows are lines, cells are separated by tab characters. Column widths follow the longest
     * cell; the grid, a tinted header row and cell padding are drawn behind the text.
     */
    static final class Tbl implements LineBackgroundSpan, android.text.style.LeadingMarginSpan,
            android.text.style.LineHeightSpan {
        final int cols;
        int[] widths = new int[0]; // per column, px
        int padX = 10;
        int padY = 6;

        Tbl(int cols) {
            this.cols = cols;
        }

        int edge(int k) {
            int x = 0;
            for (int i = 0; i < k && i < widths.length; i++) x += widths[i];
            return x;
        }

        @Override
        public int getLeadingMargin(boolean first) {
            return padX;
        }

        @Override
        public void drawLeadingMargin(Canvas c, Paint p, int x, int dir, int top, int baseline,
                                      int bottom, CharSequence text, int start, int end, boolean first,
                                      android.text.Layout layout) {
        }

        @Override
        public void chooseHeight(CharSequence text, int start, int end, int spanstartv, int lineHeight,
                                 Paint.FontMetricsInt fm) {
            fm.ascent -= padY;
            fm.top -= padY;
            fm.descent += padY;
            fm.bottom += padY;
        }

        @Override
        public void drawBackground(Canvas c, Paint p, int left, int right, int top, int baseline,
                                   int bottom, CharSequence text, int start, int end, int lnum) {
            int old = p.getColor();
            Paint.Style os = p.getStyle();
            boolean first = text instanceof Spanned && ((Spanned) text).getSpanStart(this) >= start;
            boolean last = text instanceof Spanned && ((Spanned) text).getSpanEnd(this) <= end + 1;
            int w = edge(widths.length);
            if (w <= 0) return;
            p.setStyle(Paint.Style.FILL);
            if (first) {
                p.setColor(Pal.chip);
                c.drawRect(left, top, left + w, bottom, p);
            }
            float t = Math.max(1f, density);
            int line = Pal.dark ? 0xFF4A5753 : 0xFF9AA5A1;
            p.setColor(line);
            if (first) c.drawRect(left, top, left + w, top + t, p);
            if (last) c.drawRect(left, bottom - t, left + w, bottom, p);
            if (!last) c.drawRect(left, bottom - t, left + w, bottom, p);
            for (int k = 0; k <= widths.length; k++) {
                float x = left + edge(k);
                if (k == widths.length) x -= t;
                c.drawRect(x, top, x + t, bottom, p);
            }
            p.setColor(old);
            p.setStyle(os);
        }
    }

    static Tbl tableAt(Spanned e, int pos) {
        for (Tbl t : e.getSpans(Math.max(0, pos - 1), Math.min(e.length(), pos + 1), Tbl.class)) {
            if (e.getSpanStart(t) <= pos && pos <= e.getSpanEnd(t)) return t;
        }
        return null;
    }

    /** Start / end (exclusive) of the cell index {@code cell} in the line starting at {@code ls}. */
    static int[] cellRange(CharSequence e, int ls, int cell) {
        int le = lineEnd(e, ls);
        int cs = ls;
        for (int k = 0; k < cell; k++) {
            int i = cs;
            while (i < le && e.charAt(i) != '\t') i++;
            if (i >= le) return new int[]{le, le};
            cs = i + 1;
        }
        int ce = cs;
        while (ce < le && e.charAt(ce) != '\t') ce++;
        return new int[]{cs, ce};
    }

    /** Keeps column widths, tab stops and the header style right (call after every text change). */
    static void syncTables(Editable e, TextPaint paint, int availPx) {
        Tbl[] tables = e.getSpans(0, e.length(), Tbl.class);
        Stop[] stops = e.getSpans(0, e.length(), Stop.class);
        boolean[] keep = new boolean[stops.length];
        for (TblHead h : e.getSpans(0, e.length(), TblHead.class)) e.removeSpan(h);
        float size = paint.getTextSize();
        for (Tbl t : tables) {
            int a = e.getSpanStart(t);
            int b = e.getSpanEnd(t);
            t.padX = Math.round(Math.max(8f, size * 0.5f));
            t.padY = Math.round(Math.max(4f, size * 0.3f));
            int[] w = new int[t.cols];
            int minW = Math.round(size * 3.2f);
            int p = a;
            int row = 0;
            while (p <= b) {
                int le = Math.min(lineEnd(e, p), b);
                for (int k = 0; k < t.cols; k++) {
                    int[] r = cellRange(e, p, k);
                    if (r[0] > le) break;
                    float mw = paint.measureText(e, r[0], Math.min(r[1], le)) * (row == 0 ? 1.12f : 1f);
                    w[k] = Math.max(w[k], Math.round(mw));
                }
                if (le >= b) break;
                p = le + 1;
                row++;
            }
            int total = 0;
            for (int k = 0; k < t.cols; k++) {
                w[k] = Math.max(minW, w[k]) + 2 * t.padX;
                total += w[k];
            }
            if (total > availPx && availPx > 0) { // squeeze, but keep every column usable
                float f = availPx / (float) total;
                for (int k = 0; k < t.cols; k++) w[k] = Math.max(minW, Math.round(w[k] * f));
            }
            t.widths = w;
            // tab stops sit at the column edges; the leading margin gives the first cell its padding
            int[] want = new int[Math.max(0, t.cols - 1)];
            for (int k = 1; k < t.cols; k++) want[k - 1] = t.edge(k);
            boolean[] have = new boolean[want.length];
            boolean ok = true;
            for (int i = 0; i < stops.length; i++) {
                if (e.getSpanStart(stops[i]) != a || e.getSpanEnd(stops[i]) != b) continue;
                int idx = -1;
                for (int k = 0; k < want.length; k++) {
                    if (want[k] == stops[i].x && !have[k]) {
                        idx = k;
                        break;
                    }
                }
                if (idx >= 0) {
                    have[idx] = true;
                    keep[i] = true;
                }
            }
            for (int k = 0; k < want.length; k++) {
                if (!have[k]) {
                    ok = false;
                    break;
                }
            }
            if (!ok) {
                for (int k = 0; k < want.length; k++) {
                    if (!have[k]) e.setSpan(new Stop(want[k]), a, b, Spanned.SPAN_EXCLUSIVE_INCLUSIVE);
                }
            }
            // header = first row
            int he = Math.min(lineEnd(e, a), b);
            if (he > a) e.setSpan(new TblHead(), a, he, Spanned.SPAN_EXCLUSIVE_INCLUSIVE);
        }
        for (int i = 0; i < stops.length; i++) {
            if (!keep[i]) e.removeSpan(stops[i]);
        }
    }

    /** Inserts a table on its own lines at {@code pos}; returns the start of the table (header row). */
    static int insertTable(Editable e, int pos, int cols, int rows) {
        StringBuilder sb = new StringBuilder();
        int at = lineStart(e, pos);
        boolean lineHasText = lineEnd(e, pos) > at;
        int ins = lineHasText ? lineEnd(e, pos) : at;
        if (lineHasText) sb.append('\n');
        int startOfTable = ins + sb.length();
        for (int r = 0; r < rows; r++) {
            if (r > 0) sb.append('\n');
            for (int c = 0; c < cols; c++) {
                if (c > 0) sb.append('\t');
                if (r == 0) sb.append("Column ").append(c + 1);
            }
        }
        int tableLen = sb.length() - (lineHasText ? 1 : 0);
        if (ins >= e.length()) sb.append('\n'); // leave a line below to keep typing on
        e.insert(ins, sb.toString());
        e.setSpan(new Tbl(cols), startOfTable, startOfTable + tableLen, Spanned.SPAN_EXCLUSIVE_INCLUSIVE);
        return startOfTable;
    }

    static int addRow(Editable e, int pos) {
        Tbl t = tableAt(e, pos);
        if (t == null) return pos;
        int end = e.getSpanEnd(t);
        StringBuilder sb = new StringBuilder("\n");
        for (int c = 1; c < t.cols; c++) sb.append('\t');
        e.insert(end, sb.toString());
        e.setSpan(t, e.getSpanStart(t), end + sb.length(), Spanned.SPAN_EXCLUSIVE_INCLUSIVE);
        return end + 1;
    }

    static void addColumn(Editable e, int pos) {
        Tbl t = tableAt(e, pos);
        if (t == null) return;
        int a = e.getSpanStart(t);
        int b = e.getSpanEnd(t);
        java.util.ArrayList<Integer> ends = new java.util.ArrayList<>();
        int p = a;
        while (true) {
            int le = lineEnd(e, p);
            if (le > b) le = b;
            ends.add(le);
            if (le >= b) break;
            p = le + 1;
        }
        e.removeSpan(t);
        for (int i = ends.size() - 1; i >= 0; i--) e.insert(ends.get(i), i == 0 ? "\tColumn " + (t.cols + 1) : "\t");
        int extra = ends.size() + ("Column " + (t.cols + 1)).length();
        e.setSpan(new Tbl(t.cols + 1), a, b + extra, Spanned.SPAN_EXCLUSIVE_INCLUSIVE);
    }

    /** Turns the table back into plain lines (cells separated by two spaces). */
    static void removeTable(Editable e, int pos) {
        Tbl t = tableAt(e, pos);
        if (t == null) return;
        int a = e.getSpanStart(t);
        int b = e.getSpanEnd(t);
        e.removeSpan(t);
        for (int i = b - 1; i >= a; i--) {
            if (e.charAt(i) == '\t') e.replace(i, i + 1, "  ");
        }
    }

    /** Cursor position at the end of the next cell (adds a row after the last cell). */
    static int nextCell(Editable e, int pos) {
        Tbl t = tableAt(e, pos);
        if (t == null) return pos;
        int le = lineEnd(e, pos);
        for (int i = pos; i < le; i++) {
            if (e.charAt(i) == '\t') return cellEnd(e, i + 1);
        }
        if (le < e.getSpanEnd(t)) return cellEnd(e, le + 1);
        return addRow(e, pos);
    }

    private static int cellEnd(CharSequence e, int from) {
        int le = lineEnd(e, from);
        int i = from;
        while (i < le && e.charAt(i) != '\t') i++;
        return i;
    }

    static Object make(int type, int arg) {
        switch (type) {
            case TABLE:
                return new Tbl(Math.max(2, arg));
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
        return -1;
    }

    private static int argOf(Object o) {
        if (o instanceof Tbl) return ((Tbl) o).cols;
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

    /** Which list marker the line starting at {@code pos} has. */
    static int prefixAt(CharSequence t, int pos) {
        if (startsWith(t, pos, BULLET)) return P_BULLET;
        if (startsWith(t, pos, CHECK_OFF)) return P_CHECK_OFF;
        if (startsWith(t, pos, CHECK_ON)) return P_CHECK_ON;
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
        e.replace(ls, ls + 1, nowOn ? "\u2611" : "\u2610");
        apply(e, STRIKE, ls + 2, lineEnd(e, ls), 0, nowOn);
    }

    static boolean startsWith(CharSequence t, int pos, String prefix) {
        if (pos + prefix.length() > t.length()) return false;
        for (int i = 0; i < prefix.length(); i++) {
            if (t.charAt(pos + i) != prefix.charAt(i)) return false;
        }
        return true;
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
                if (a < b) out.setSpan(make(r.getInt(0), r.optInt(3)), a, b, FLAGS);
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
