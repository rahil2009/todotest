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

        /** Flips a tick box at (xl, yl) measured from the table's top-left; true if one was hit. */
        boolean toggleAt(float xl, float yl, Paint p) {
            int rows = rows();
            int cols = cols();
            if (rows == 0 || cols == 0) return false;
            float rh = rowH(p);
            float colW = width() / (float) cols;
            int r = (int) (yl / rh);
            int k = (int) (xl / colW);
            if (r < 0 || r >= rows || k < 0 || k >= cols) return false;
            int st = tick(cells[r][k]);
            if (st == 0) return false;
            float into = xl - k * colW;
            if (into > p.getTextSize() * 0.5f + p.getTextSize() * 1.3f + 10 * density) return false;
            cells[r][k] = compose(st == 1 ? 2 : 1, label(cells[r][k]));
            return true;
        }

        private int rowH(Paint p) {
            return Math.round(p.getTextSize() * 1.25f + p.getTextSize() * 0.9f);
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
            if (rows == 0 || cols == 0) return;
            float w = width();
            float rh = rowH(p);
            float colW = w / cols;
            float t0 = y - rh * rows;
            float pad = p.getTextSize() * 0.5f;
            float rad = 8 * density;
            int oldColor = p.getColor();
            Paint.Style oldStyle = p.getStyle();
            Typeface oldFace = p.getTypeface();
            boolean oldFake = p.isFakeBoldText();

            Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
            Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
            line.setColor(Pal.dark ? 0xFF4A5753 : 0xFF9AA5A1);
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeWidth(Math.max(1f, density));

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

            for (int r = 1; r < rows; r++) c.drawLine(x, t0 + r * rh, x + w, t0 + r * rh, line);
            for (int k = 1; k < cols; k++) c.drawLine(x + k * colW, t0, x + k * colW, y, line);
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
        e.setSpan(new Tbl(Tbl.blank(rows, cols)), s, s + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return s;
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
        if (!(text instanceof Spanned)) return text.toString();
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
        return out.toString();
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
        if (o instanceof Tbl) return ((Tbl) o).cols();
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
