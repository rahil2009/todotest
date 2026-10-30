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
    /** Paragraph alignment; the arg is 1 (centre) or 2 (right). */
    static final int ALIGN = 17;
    /** A link to another note; the arg is that note's id. */
    static final int LINK = 18;
    /** A number with - and + buttons; the span covers the digits themselves, the arg is the step. */
    static final int COUNTER = 19;
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
            0xFF3E8BFF, 0xFF1E40AF, 0xFF8E4EC6, 0xFFE93D82, 0xFF8B8D98};

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
        private static void mono(TextPaint p) {
            Typeface cur = p.getTypeface();
            int st = cur == null ? Typeface.NORMAL : cur.getStyle();
            // keep bold / italic that were applied before this span, whichever order the spans sit in
            p.setTypeface(st == Typeface.NORMAL ? Typeface.MONOSPACE : Typeface.create(Typeface.MONOSPACE, st));
        }

        @Override
        public void updateMeasureState(TextPaint p) {
            mono(p);
        }

        @Override
        public void updateDrawState(TextPaint p) {
            mono(p);
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
            float k = NoteFonts.stroke(idx);
            if (k > 0) { // pen / marker fonts: an outline around each glyph makes the stroke thicker
                p.setStyle(Paint.Style.FILL_AND_STROKE);
                p.setStrokeJoin(Paint.Join.ROUND);
                p.setStrokeWidth(p.getTextSize() * k);
            }
        }
    }

    // ---- shared drawing helpers for the panel-like paragraph styles

    private static final android.graphics.Path PANEL_PATH = new android.graphics.Path();
    private static final android.graphics.RectF PANEL_RECT = new android.graphics.RectF();

    /**
     * One line's slice of a rounded panel: the first line has rounded top corners, the last line rounded
     * bottom corners, the lines between are plain, so a multi-line panel reads as one piece.
     */
    /** How far a callout / code panel reaches out past the text column on each side (px). */
    static int panelExt() {
        return Math.round(14 * density);
    }

    static boolean isPanel(Object o) {
        return o instanceof Callout || o instanceof Block;
    }

    /** True when the line starting at {@code start} is the first line of {@code span}. */
    static boolean firstLine(CharSequence text, Object span, int start) {
        if (!(text instanceof android.text.Spanned)) return true;
        return ((android.text.Spanned) text).getSpanStart(span) >= start;
    }

    /** Adds {@code px} of room above the first line and below the last line of a panel span. */
    static void panelPadding(CharSequence text, Object span, int start, int end, int px,
                             Paint.FontMetricsInt fm) {
        panelPadding(text, span, start, end, px, px, fm);
    }

    /** Same, with separate room above (top) and below (bottom). */
    static void panelPadding(CharSequence text, Object span, int start, int end, int top, int bottom,
                             Paint.FontMetricsInt fm) {
        if (!(text instanceof android.text.Spanned)) return;
        android.text.Spanned sp = (android.text.Spanned) text;
        if (sp.getSpanStart(span) >= start) { // first line
            fm.ascent -= top;
            fm.top -= top;
        }
        if (sp.getSpanEnd(span) <= end) { // last line
            fm.descent += bottom;
            fm.bottom += bottom;
        }
    }

    /**
     * Draws the rounded panel behind a callout or code block into {@code r} (view coordinates). The editor
     * calls this before the text is drawn: TextView clips everything drawn by spans to its text column, which
     * is what used to chop the panels at the left and right.
     */
    static void paintPanel(Canvas c, Paint p, Object span, android.graphics.RectF r) {
        p.setStyle(Paint.Style.FILL);
        if (span instanceof Callout) {
            p.setColor(Pal.dark ? 0xFF1E1B30 : 0xFFEEEBF9);
            c.drawRoundRect(r, 18 * density, 18 * density, p);
        } else {
            float rad = 12 * density;
            p.setColor(Block.EDGE);
            c.drawRoundRect(r, rad, rad, p);
            float bw = Math.max(1f, density);
            PANEL_RECT.set(r.left + bw, r.top + bw, r.right - bw, r.bottom - bw);
            p.setColor(Block.BG);
            c.drawRoundRect(PANEL_RECT, rad - bw, rad - bw, p);
            paintBlockHead(c, p, (Block) span, r, bw);
        }
    }

    /** Set when a block's Copy button was pressed: the button shows "Copied" until this time. */
    static Object copiedSpan;
    static long copiedUntil;

    /** The code panel's header: the language on the left, Paste and Copy buttons on the right. */
    private static void paintBlockHead(Canvas c, Paint p, Block b, android.graphics.RectF r, float bw) {
        float head = Block.headPx();
        float pad = panelExt();
        float rad = 12 * density - bw;
        p.setStyle(Paint.Style.FILL);
        p.setColor(Block.HEAD_BG);
        c.save();
        PANEL_RECT.set(r.left + bw, r.top + bw, r.right - bw, r.top + head);
        c.clipRect(PANEL_RECT);
        PANEL_RECT.set(r.left + bw, r.top + bw, r.right - bw, r.top + head + rad * 2);
        c.drawRoundRect(PANEL_RECT, rad, rad, p);
        c.restore();
        float cy = r.top + head / 2f;
        p.setTypeface(NoteFonts.face(0));
        p.setFakeBoldText(false);
        p.setTextAlign(Paint.Align.LEFT);
        p.setTextSize(12.5f * density);
        Paint.FontMetrics fm = p.getFontMetrics();
        p.setColor(0xFFB4B4B4);
        c.drawText(b.lang, r.left + pad, cy - (fm.ascent + fm.descent) / 2f, p);

        float bh = 28 * density;
        float right = r.right - pad;
        boolean done = copiedSpan == b && android.os.SystemClock.uptimeMillis() < copiedUntil;
        pill(c, p, right - Block.copyW(), cy - bh / 2, right, cy + bh / 2, done ? 2 : 0, done ? "Copied" : "Copy");
        float pr = right - Block.copyW() - Block.btnGap();
        pill(c, p, pr - Block.pasteW(), cy - bh / 2, pr, cy + bh / 2, 1, "Paste");
        p.setTextAlign(Paint.Align.LEFT);
    }

    /** One outlined header button: an icon (0 copy, 1 paste, 2 tick) and a word, centred. */
    private static void pill(Canvas c, Paint p, float l, float t, float r, float bt, int icon, String label) {
        float h = bt - t;
        float sw = Math.max(1f, density);
        int ink = icon == 2 ? 0xFF8FE3A0 : 0xFFE6E6E6;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(sw);
        p.setColor(icon == 2 ? 0xFF4F8F5E : 0xFF555555);
        PANEL_RECT.set(l + sw / 2, t + sw / 2, r - sw / 2, bt - sw / 2);
        c.drawRoundRect(PANEL_RECT, h / 2, h / 2, p);
        p.setTextSize(12.5f * density);
        float tw = p.measureText(label);
        float s = 13 * density;
        float gap = 6 * density;
        float x0 = l + ((r - l) - (s + gap + tw)) / 2f;
        float cx = x0 + s / 2;
        float cy = t + h / 2;
        p.setColor(ink);
        p.setStrokeWidth(Math.max(1.4f, 1.4f * density));
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        if (icon == 0) { // two overlapping sheets
            PANEL_RECT.set(cx - s * 0.5f, cy - s * 0.5f, cx + s * 0.2f, cy + s * 0.2f);
            c.drawRoundRect(PANEL_RECT, 2 * density, 2 * density, p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(Block.HEAD_BG);
            PANEL_RECT.set(cx - s * 0.2f, cy - s * 0.2f, cx + s * 0.5f, cy + s * 0.5f);
            c.drawRoundRect(PANEL_RECT, 2 * density, 2 * density, p);
            p.setStyle(Paint.Style.STROKE);
            p.setColor(ink);
            c.drawRoundRect(PANEL_RECT, 2 * density, 2 * density, p);
        } else if (icon == 1) { // a clipboard
            PANEL_RECT.set(cx - s * 0.42f, cy - s * 0.38f, cx + s * 0.42f, cy + s * 0.5f);
            c.drawRoundRect(PANEL_RECT, 2 * density, 2 * density, p);
            p.setStyle(Paint.Style.FILL);
            PANEL_RECT.set(cx - s * 0.2f, cy - s * 0.52f, cx + s * 0.2f, cy - s * 0.26f);
            c.drawRoundRect(PANEL_RECT, 1.5f * density, 1.5f * density, p);
            p.setStyle(Paint.Style.STROKE);
            c.drawLine(cx - s * 0.2f, cy + s * 0.02f, cx + s * 0.2f, cy + s * 0.02f, p);
            c.drawLine(cx - s * 0.2f, cy + s * 0.27f, cx + s * 0.1f, cy + s * 0.27f, p);
        } else { // tick
            c.drawLine(cx - s * 0.42f, cy + s * 0.02f, cx - s * 0.1f, cy + s * 0.34f, p);
            c.drawLine(cx - s * 0.1f, cy + s * 0.34f, cx + s * 0.46f, cy - s * 0.3f, p);
        }
        p.setStyle(Paint.Style.FILL);
        p.setColor(ink);
        Paint.FontMetrics fm = p.getFontMetrics();
        c.drawText(label, x0 + s + gap, cy - (fm.ascent + fm.descent) / 2f, p);
    }

    /**
     * A quote: accent bar on the left, serif italic text, and a big faint quotation mark behind the first
     * line. One span covers a whole run of quote paragraphs ({@link #normalize} keeps it that way).
     */
    static final class Quote extends MetricAffectingSpan implements LineBackgroundSpan,
            android.text.style.LeadingMarginSpan {
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
            return Math.round(22 * density);
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
            PANEL_RECT.set(dir > 0 ? x : x - w, top, dir > 0 ? x + w : x, bottom);
            c.drawRoundRect(PANEL_RECT, w / 2, w / 2, p);
            p.setColor(old);
            p.setStyle(os);
        }

        @Override
        public void drawBackground(Canvas c, Paint p, int left, int right, int top, int baseline,
                                   int bottom, CharSequence text, int start, int end, int lnum) {
            if (!firstLine(text, this, start)) return;
            // "Quote" in italic is a placeholder: it shows while the quote is still empty and goes away with
            // the first character typed, so it never sits behind (or leaks into) the words.
            for (int i = start; i < end && i < text.length(); i++) {
                if (!Character.isWhitespace(text.charAt(i))) return;
            }
            int oldColor = p.getColor();
            Paint.Style oldStyle = p.getStyle();
            Typeface oldFace = p.getTypeface();
            boolean oldFake = p.isFakeBoldText();
            p.setStyle(Paint.Style.FILL);
            p.setFakeBoldText(false);
            p.setTypeface(Typeface.create(NoteFonts.face(2), Typeface.ITALIC));
            p.setColor((Pal.accent & 0x00FFFFFF) | (Pal.dark ? 0x99000000 : 0x8C000000));
            c.drawText("Quote", left + 22 * density, baseline, p);
            p.setTypeface(oldFace);
            p.setFakeBoldText(oldFake);
            p.setColor(oldColor);
            p.setStyle(oldStyle);
        }
    }

    /**
     * A callout: a dark rounded panel with a purple icon tile and a light bulb at the left, then the
     * text. One span covers a whole run of callout paragraphs.
     */
    static final class Callout extends MetricAffectingSpan implements LineBackgroundSpan,
            android.text.style.LeadingMarginSpan, android.text.style.LineHeightSpan {
        static int pad() {
            return Math.round(14 * density);
        }

        static final int TILE = 0xFFB9A8FF;
        static final int TILE_INK = 0xFF0E0F12;

        @Override
        public void updateMeasureState(TextPaint p) {
        }

        @Override
        public void updateDrawState(TextPaint p) {
        }

        /** The icon tile (36dp) and the gap after it (12dp); the panel itself is drawn by the editor, see {@link #paintPanel}. */
        @Override
        public int getLeadingMargin(boolean first) {
            return Math.round(48 * density);
        }

        @Override
        public void drawLeadingMargin(Canvas c, Paint p, int x, int dir, int top, int baseline,
                                      int bottom, CharSequence text, int start, int end, boolean first,
                                      android.text.Layout layout) {
        }

        @Override
        public void chooseHeight(CharSequence text, int start, int end, int spanstartv, int lineHeight,
                                 Paint.FontMetricsInt fm) {
            panelPadding(text, this, start, end, Math.round(14 * density), fm);
        }

        /** Only the icon tile is drawn here (the panel behind it is drawn unclipped by the editor). */
        @Override
        public void drawBackground(Canvas c, Paint p, int left, int right, int top, int baseline,
                                   int bottom, CharSequence text, int start, int end, int lnum) {
            if (!firstLine(text, this, start)) return;
            int oldColor = p.getColor();
            Paint.Style oldStyle = p.getStyle();
            float oldW = p.getStrokeWidth();
            p.setStyle(Paint.Style.FILL);
            float size = 36 * density;
            float tx = left;
            float ty = top + 14 * density;
            PANEL_RECT.set(tx, ty, tx + size, ty + size);
            p.setColor(TILE);
            c.drawRoundRect(PANEL_RECT, 12 * density, 12 * density, p);
            bulb(c, p, tx + size / 2f, ty + size / 2f, density * 1.12f);
            p.setStrokeWidth(oldW);
            p.setColor(oldColor);
            p.setStyle(oldStyle);
        }

        /** A small light bulb drawn with strokes, centred on (cx, cy). */
        private static void bulb(Canvas c, Paint p, float cx, float cy, float d) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setStrokeWidth(1.7f * d);
            p.setColor(TILE_INK);
            c.drawCircle(cx, cy - 2.2f * d, 5.2f * d, p);
            c.drawLine(cx - 2.6f * d, cy + 3.2f * d, cx - 2.6f * d, cy + 5.4f * d, p);
            c.drawLine(cx + 2.6f * d, cy + 3.2f * d, cx + 2.6f * d, cy + 5.4f * d, p);
            c.drawLine(cx - 2.6f * d, cy + 5.4f * d, cx + 2.6f * d, cy + 5.4f * d, p);
            c.drawLine(cx - 1.4f * d, cy + 7.6f * d, cx + 1.4f * d, cy + 7.6f * d, p);
            p.setStyle(Paint.Style.FILL);
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

    /** Paragraph alignment: arg 1 = centred, 2 = right (0 = left, no span). */
    static final class Align extends android.text.style.AlignmentSpan.Standard {
        final int mode;

        Align(int mode) {
            super(mode == 1 ? android.text.Layout.Alignment.ALIGN_CENTER
                    : mode == 2 ? android.text.Layout.Alignment.ALIGN_OPPOSITE
                    : android.text.Layout.Alignment.ALIGN_NORMAL);
            this.mode = mode;
        }
    }

    /** A link to another note: the text is accent coloured and underlined; the arg is the note's id. */
    static final class NoteLink extends CharacterStyle implements UpdateAppearance {
        final int noteId;

        NoteLink(int noteId) {
            this.noteId = noteId;
        }

        @Override
        public void updateDrawState(TextPaint tp) {
            tp.setUnderlineText(true);
            if (tp.bgColor == 0) tp.setColor(Pal.dark ? 0xFFA9B4FF : 0xFF3B4BD6);
        }
    }

    /**
     * Code block: near-black rounded panel with a thin border and light JetBrains Mono text. One span covers
     * the whole block ({@link #normalize} keeps it that way), so it reads as one piece.
     */
    static final class Block extends MetricAffectingSpan implements LineBackgroundSpan,
            android.text.style.LeadingMarginSpan, android.text.style.LineHeightSpan {
        static final int BG = 0xFF0D0D0D;
        static final int HEAD_BG = 0xFF2F2F2F;
        static final int EDGE = 0xFF2F2F2F;
        static final int FG = 0xFFE6E6E6;
        /** Shown in the panel's header; guessed from the code by {@link #highlight}. */
        String lang = "code";

        /** Height of the header strip (language label, Paste and Copy buttons) at the top of the panel. */
        static int headPx() {
            return Math.round(40 * density);
        }

        /** Room above the first line (the header and a gap) and below the last line of a block. */
        static int topPad() {
            return headPx() + Math.round(10 * density);
        }

        static int botPad() {
            return Math.round(14 * density);
        }

        static float copyW() {
            return 78 * density;
        }

        static float pasteW() {
            return 84 * density;
        }

        static float btnGap() {
            return 8 * density;
        }

        @Override
        public void updateMeasureState(TextPaint p) {
            p.setTypeface(NoteFonts.face(11));
        }

        @Override
        public void updateDrawState(TextPaint p) {
            p.setTypeface(NoteFonts.face(11));
            p.setColor(FG);
        }

        @Override
        public void chooseHeight(CharSequence text, int start, int end, int spanstartv, int lineHeight,
                                 Paint.FontMetricsInt fm) {
            panelPadding(text, this, start, end, topPad(), botPad(), fm);
        }

        @Override
        public int getLeadingMargin(boolean first) {
            return 0; // the panel pokes out PANEL_EXT on each side, so the text already sits inside it
        }

        @Override
        public void drawLeadingMargin(Canvas c, Paint p, int x, int dir, int top, int baseline,
                                      int bottom, CharSequence text, int start, int end, boolean first,
                                      android.text.Layout layout) {
        }

        /** The panel is drawn unclipped by the editor, see {@link #paintPanel}. */
        @Override
        public void drawBackground(Canvas c, Paint p, int left, int right, int top, int baseline,
                                   int bottom, CharSequence text, int start, int end, int lnum) {
        }
    }


    // ------------------------------------------------------------- code colouring

    /** A syntax colour inside a code block. Drawn only: never saved, rebuilt by {@link #highlight}. */
    static final class Syn extends CharacterStyle implements UpdateAppearance {
        final int color;

        Syn(int color) {
            this.color = color;
        }

        @Override
        public void updateDrawState(TextPaint tp) {
            tp.setColor(color);
        }
    }

    private static final int SYN_KEYWORD = 0xFFFF7B72;
    private static final int SYN_STRING = 0xFFA5D6A7;
    private static final int SYN_NUMBER = 0xFFFFB86C;
    private static final int SYN_COMMENT = 0xFF8B949E;
    private static final int SYN_FUNC = 0xFF79B8FF;
    private static final int SYN_TYPE = 0xFFD2A8FF;

    private static final java.util.HashSet<String> KEYWORDS = new java.util.HashSet<>(java.util.Arrays.asList(
            "if", "else", "elif", "for", "while", "do", "switch", "case", "break", "continue", "return", "function",
            "def", "class", "interface", "enum", "struct", "fun", "val", "var", "let", "const", "static", "final",
            "public", "private", "protected", "void", "int", "long", "float", "double", "boolean", "bool", "char",
            "string", "new", "import", "from", "package", "export", "default", "try", "catch", "finally", "throw",
            "throws", "extends", "implements", "in", "is", "as", "not", "and", "or", "with", "lambda", "async",
            "await", "yield", "null", "none", "nil", "true", "false", "this", "self", "super", "select", "where",
            "insert", "into", "update", "delete", "create", "table", "join", "left", "right", "inner", "outer", "on",
            "group", "by", "order", "limit", "values", "set", "distinct", "having", "union", "like", "between",
            "func", "fn", "mut", "pub", "use", "mod", "type", "echo", "then", "fi", "done", "elseif", "end"));

    /** A best guess at the language, shown in the header of a code block. */
    static String guessLang(String t) {
        String lo = t.toLowerCase(java.util.Locale.ROOT);
        String tr = lo.trim();
        if ((tr.startsWith("{") || tr.startsWith("[")) && tr.contains("\":")) return "json";
        if (tr.startsWith("<?php")) return "php";
        if (tr.startsWith("#!") || tr.startsWith("$ ") || tr.startsWith("sudo ") || tr.startsWith("git ")
                || tr.startsWith("cd ") || tr.startsWith("npm ") || tr.startsWith("pip ") || tr.startsWith("echo ")) {
            return "shell";
        }
        if (lo.contains("select ") && lo.contains(" from ")) return "sql";
        if (lo.contains("#include")) return "c++";
        if (lo.contains("def ") || lo.contains("elif ") || lo.contains("print(")
                || (lo.contains("import ") && !lo.contains(";") && !lo.contains("{"))) return "python";
        if (lo.contains("fun ") && (lo.contains("val ") || lo.contains("var "))) return "kotlin";
        if (lo.contains("system.out") || lo.contains("public class") || lo.contains("void main")) return "java";
        if (lo.contains("func ") && lo.contains(":=")) return "go";
        if (lo.contains("let mut") || lo.contains("fn ")) return "rust";
        if (tr.startsWith("<") && tr.contains(">")) return "html";
        if (lo.contains("function") || lo.contains("=>") || lo.contains("const ") || lo.contains("console.")
                || lo.contains("let ")) return "javascript";
        return "code";
    }

    /** Colours keywords, strings, numbers, comments and calls inside every code block (drawing only). */
    static void highlight(Editable e) {
        for (Syn old : e.getSpans(0, e.length(), Syn.class)) e.removeSpan(old);
        for (Block b : e.getSpans(0, e.length(), Block.class)) {
            int a = e.getSpanStart(b);
            int z = e.getSpanEnd(b);
            if (a < 0 || z <= a) continue;
            String t = e.subSequence(a, z).toString();
            b.lang = guessLang(t);
            int n = t.length();
            int i = 0;
            while (i < n) {
                char ch = t.charAt(i);
                char nx = i + 1 < n ? t.charAt(i + 1) : 0;
                if (ch == '/' && nx == '/' || ch == '#' && (i == 0 || Character.isWhitespace(t.charAt(i - 1)))
                        || ch == '-' && nx == '-' && i + 2 < n && t.charAt(i + 2) == ' ') {
                    int j = t.indexOf('\n', i);
                    if (j < 0) j = n;
                    syn(e, a + i, a + j, SYN_COMMENT);
                    i = j;
                } else if (ch == '/' && nx == '*') {
                    int j = t.indexOf("*/", i + 2);
                    j = j < 0 ? n : j + 2;
                    syn(e, a + i, a + j, SYN_COMMENT);
                    i = j;
                } else if (ch == '"' || ch == '\'' || ch == '`') {
                    int j = i + 1;
                    while (j < n && t.charAt(j) != ch && t.charAt(j) != '\n') {
                        if (t.charAt(j) == '\\') j++;
                        j++;
                    }
                    j = Math.min(n, j + (j < n && t.charAt(j) == ch ? 1 : 0));
                    syn(e, a + i, a + j, SYN_STRING);
                    i = j;
                } else if (Character.isDigit(ch) && (i == 0 || !Character.isLetterOrDigit(t.charAt(i - 1))
                        && t.charAt(i - 1) != '_')) {
                    int j = i + 1;
                    while (j < n && (Character.isLetterOrDigit(t.charAt(j)) || t.charAt(j) == '.')) j++;
                    syn(e, a + i, a + j, SYN_NUMBER);
                    i = j;
                } else if (Character.isLetter(ch) || ch == '_') {
                    int j = i + 1;
                    while (j < n && (Character.isLetterOrDigit(t.charAt(j)) || t.charAt(j) == '_')) j++;
                    String w = t.substring(i, j);
                    int k = j;
                    while (k < n && t.charAt(k) == ' ') k++;
                    if (KEYWORDS.contains(w.toLowerCase(java.util.Locale.ROOT))) {
                        syn(e, a + i, a + j, SYN_KEYWORD);
                    } else if (k < n && t.charAt(k) == '(') {
                        syn(e, a + i, a + j, SYN_FUNC);
                    } else if (k < n && t.charAt(k) == '=' && (k + 1 >= n || t.charAt(k + 1) != '=')) {
                        syn(e, a + i, a + j, SYN_FUNC); // a name being assigned, like records = ...
                    } else if (Character.isUpperCase(ch) && j - i > 1) {
                        syn(e, a + i, a + j, SYN_TYPE);
                    }
                    i = j;
                } else {
                    i++;
                }
            }
        }
    }

    private static void syn(Editable e, int a, int b, int color) {
        if (b > a) e.setSpan(new Syn(color), a, b, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    // ------------------------------------------------------------- counters

    /**
     * A number with - and + buttons, usable anywhere in a note ("Tables moved: [-] 20 [+]"). The span covers the
     * digits themselves, so the value is ordinary text (it shows in search, exports and previews) and the span only
     * draws it as buttons. The arg is the step each tap moves it by.
     */
    static final class Counter extends android.text.style.ReplacementSpan {
        final int step;
        /** Width as last measured, for finding which part a tap landed on. */
        float width;

        Counter(int step) {
            this.step = Math.max(1, Math.min(9999, step));
        }

        static float btn(Paint p) {
            return p.getTextSize() * 1.5f;
        }

        @Override
        public int getSize(Paint p, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
            width = btn(p) * 2 + p.measureText(text, start, end) + p.getTextSize() * 0.7f;
            return Math.round(width);
        }

        @Override
        public void draw(Canvas c, CharSequence text, int start, int end, float x, int top, int y,
                         int bottom, Paint p) {
            if (hiddenAt(text, start, end)) return;
            int oldColor = p.getColor();
            Paint.Style oldStyle = p.getStyle();
            float oldW = p.getStrokeWidth();
            Paint.Align oldAlign = p.getTextAlign();
            float ts = p.getTextSize();
            Paint.FontMetrics fm = p.getFontMetrics();
            float cy = y + (fm.ascent + fm.descent) / 2f;
            float h = ts * 1.25f;
            float btn = btn(p);
            float w = btn * 2 + p.measureText(text, start, end) + ts * 0.7f;
            width = w;
            p.setStyle(Paint.Style.FILL);
            p.setColor((Pal.accent & 0x00FFFFFF) | (Pal.dark ? 0x2E000000 : 0x24000000));
            PANEL_RECT.set(x, cy - h / 2, x + w, cy + h / 2);
            c.drawRoundRect(PANEL_RECT, h / 2, h / 2, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1.6f, ts * 0.1f));
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(Pal.accent);
            float g = btn * 0.2f;
            c.drawLine(x + btn / 2 - g, cy, x + btn / 2 + g, cy, p); // minus
            float px = x + w - btn / 2;
            c.drawLine(px - g, cy, px + g, cy, p); // plus
            c.drawLine(px, cy - g, px, cy + g, p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(Pal.ink);
            p.setTextAlign(Paint.Align.CENTER);
            c.drawText(text, start, end, x + w / 2, y, p);
            p.setTextAlign(oldAlign);
            p.setColor(oldColor);
            p.setStyle(oldStyle);
            p.setStrokeWidth(oldW);
        }
    }

    private static boolean validNumber(CharSequence t) {
        int n = t.length();
        if (n == 0 || n > 8) return false;
        int i = t.charAt(0) == '-' ? 1 : 0;
        if (i >= n) return false;
        for (; i < n; i++) if (t.charAt(i) < '0' || t.charAt(i) > '9') return false;
        return true;
    }

    /** The number a counter shows (0 if its text is not a number). */
    static long counterValue(Spanned text, Counter c) {
        int a = text.getSpanStart(c);
        int b = text.getSpanEnd(c);
        if (a < 0 || b <= a) return 0;
        CharSequence t = text.subSequence(a, b);
        if (!validNumber(t)) return 0;
        try {
            return Long.parseLong(t.toString());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    /** A counter whose digits were edited into something that is not a number goes back to plain text. */
    static void healCounters(Editable e) {
        for (Counter k : e.getSpans(0, e.length(), Counter.class)) {
            int a = e.getSpanStart(k);
            int b = e.getSpanEnd(k);
            if (a < 0 || b <= a || !validNumber(e.subSequence(a, b))) e.removeSpan(k);
        }
    }

    /** Writes a new value (and step) into a counter. */
    static void setCounter(Editable e, Counter old, long value, int step) {
        int a = e.getSpanStart(old);
        int b = e.getSpanEnd(old);
        if (a < 0 || b <= a) return;
        value = Math.max(-9_999_999L, Math.min(9_999_999L, value));
        String num = Long.toString(value);
        e.removeSpan(old);
        e.replace(a, b, num);
        e.setSpan(new Counter(step), a, a + num.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    /** Inserts "Name: 0" (the number being a counter) at the selection; returns where the number starts. */
    static int insertCounter(Editable e, int selStart, int selEnd, String name, long value, int step) {
        String label = name == null || name.trim().isEmpty() ? ""
                : name.trim() + (name.trim().endsWith(":") ? " " : ": ");
        value = Math.max(-9_999_999L, Math.min(9_999_999L, value));
        String num = Long.toString(value);
        boolean spaceAfter = selEnd < e.length() && !Character.isWhitespace(e.charAt(selEnd));
        e.replace(selStart, selEnd, label + num + (spaceAfter ? " " : ""));
        int at = selStart + label.length();
        e.setSpan(new Counter(step), at, at + num.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return at;
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

        /** A cell that is a counter starts with this; then the number, and " x5" when the step is not 1. */
        static final String COUNT = "\u00B1 ";

        /** {value, step} of a counter cell, or null for any other cell. */
        static long[] counterOf(String cell) {
            if (cell == null || !cell.startsWith(COUNT)) return null;
            String rest = cell.substring(COUNT.length()).trim();
            long step = 1;
            int x = rest.indexOf('\u00D7');
            if (x >= 0) {
                try {
                    step = Math.max(1, Math.min(9999, Long.parseLong(rest.substring(x + 1).trim())));
                } catch (NumberFormatException ex) {
                    return null;
                }
                rest = rest.substring(0, x).trim();
            }
            if (!validNumber(rest)) return null;
            try {
                return new long[]{Long.parseLong(rest), step};
            } catch (NumberFormatException ex) {
                return null;
            }
        }

        static String counterCell(long value, long step) {
            value = Math.max(-9_999_999L, Math.min(9_999_999L, value));
            return COUNT + value + (step > 1 ? " \u00D7" + step : "");
        }

        /** Where the counter pill sits inside a cell: {left from the cell's left edge, width, button width}. */
        float[] pill(Paint p, String cell) {
            long[] cv = counterOf(cell);
            if (cv == null) return null;
            float ts = p.getTextSize();
            float room = colW() - 2 * padX(p);
            float num = p.measureText(Long.toString(cv[0]));
            float btn = Math.min(ts * 1.5f, Math.max(ts * 0.9f, (room - num - ts * 0.5f) / 2f));
            float w = Math.min(room, btn * 2 + num + ts * 0.5f);
            return new float[]{padX(p), w, btn};
        }

        /** For a tap at (xl, yl) from the table's top-left: -1 not a counter cell, else 0 minus, 1 number, 2 plus. */
        int counterZone(float xl, float yl, Paint p) {
            int[] rc = cellAt(xl, yl, p);
            if (rc == null) return -1;
            float[] g = pill(p, cells[rc[0]][rc[1]]);
            if (g == null) return -1;
            float x = xl - rc[1] * colW() - g[0];
            if (x >= -6 * density && x < g[2]) return 0;
            if (x > g[1] - g[2] && x <= g[1] + 6 * density) return 2;
            return 1;
        }

        private void drawPill(Canvas c, TextPaint tp, float left, float cy, float w, float btn, long value) {
            float ts = tp.getTextSize();
            float h = ts * 1.25f;
            Paint.Style oldStyle = tp.getStyle();
            float oldW = tp.getStrokeWidth();
            Paint.Align oldAlign = tp.getTextAlign();
            boolean oldFake = tp.isFakeBoldText();
            tp.setFakeBoldText(false);
            tp.setStyle(Paint.Style.FILL);
            tp.setColor((Pal.accent & 0x00FFFFFF) | (Pal.dark ? 0x2E000000 : 0x24000000));
            PANEL_RECT.set(left, cy - h / 2, left + w, cy + h / 2);
            c.drawRoundRect(PANEL_RECT, h / 2, h / 2, tp);
            tp.setStyle(Paint.Style.STROKE);
            tp.setStrokeWidth(Math.max(1.6f, ts * 0.1f));
            tp.setStrokeCap(Paint.Cap.ROUND);
            tp.setColor(Pal.accent);
            float g = btn * 0.2f;
            c.drawLine(left + btn / 2 - g, cy, left + btn / 2 + g, cy, tp);
            float px = left + w - btn / 2;
            c.drawLine(px - g, cy, px + g, cy, tp);
            c.drawLine(px, cy - g, px, cy + g, tp);
            tp.setStyle(Paint.Style.FILL);
            tp.setColor(Pal.ink);
            tp.setTextAlign(Paint.Align.CENTER);
            Paint.FontMetrics fm = tp.getFontMetrics();
            c.drawText(Long.toString(value), left + w / 2, cy - (fm.ascent + fm.descent) / 2f, tp);
            tp.setTextAlign(oldAlign);
            tp.setStyle(oldStyle);
            tp.setStrokeWidth(oldW);
            tp.setFakeBoldText(oldFake);
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
            final int n = cols(); // fixed first: cols() reads row 0, which is rebuilt in the first pass
            at = Math.max(0, Math.min(n, at));
            for (int r = 0; r < rows(); r++) {
                String[] row = new String[n + 1];
                for (int k = 0, o = 0; k < row.length; k++) {
                    row[k] = k == at ? (r == 0 ? "Column " + (n + 1) : "") : cells[r][o++];
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
            final int n = cols();
            if (n <= 2 || at < 0 || at >= n) return false;
            for (int r = 0; r < rows(); r++) {
                String[] row = new String[n - 1];
                for (int k = 0, o = 0; k < n; k++) if (k != at) row[o++] = cells[r][k];
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
                    float[] pg = r == 0 ? null : pill(tp, raw);
                    if (pg != null) { // a counter cell: [-] 20 [+]
                        drawPill(c, tp, x + k * colW + pg[0], t0 + r * rh + rh / 2f, pg[1], pg[2],
                                counterOf(raw)[0]);
                        continue;
                    }
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
        java.util.ArrayList<int[]> want = new java.util.ArrayList<>();
        int n = e.length();
        if (e.toString().indexOf('\u25B8') >= 0) {
            int p = 0;
            while (p <= n) {
                if (prefixAt(e, p) == P_TOGGLE_CLOSED) {
                    int a = lineEnd(e, p) + 1;
                    int b = childEnd(e, p);
                    if (a < b) {
                        want.add(new int[]{a, b});
                        p = b;
                        continue;
                    }
                }
                int le = lineEnd(e, p);
                if (le >= n) break;
                p = le + 1;
            }
        }
        Hide[] have = e.getSpans(0, n, Hide.class);
        boolean same = have.length == want.size();
        if (same) {
            java.util.Arrays.sort(have, (x, y) -> e.getSpanStart(x) - e.getSpanStart(y));
            for (int k = 0; k < have.length && same; k++) {
                if (e.getSpanStart(have[k]) != want.get(k)[0] || e.getSpanEnd(have[k]) != want.get(k)[1]) same = false;
            }
        }
        if (same) return;
        layoutDirty = true;
        for (Hide h : have) e.removeSpan(h);
        for (int[] r : want) e.setSpan(new Hide(), r[0], r[1], Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
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
        layoutDirty = true;
    }

    /**
     * Set whenever a span changed the HEIGHT of lines without any text changing (a table gained a row, a
     * paragraph became a callout / code block, a toggle folded). Android's text layout keeps cached drawing of
     * the lines below such a change, which is what drew a grown table over the text under it and left the
     * text of a callout blank until the next key press. The editor rebuilds its layout when it sees this.
     */
    static boolean layoutDirty;

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
            case ALIGN:
                return new Align(arg);
            case LINK:
                return new NoteLink(arg);
            case COUNTER:
                return new Counter(arg);
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
        if (o instanceof Align) return ALIGN;
        if (o instanceof NoteLink) return LINK;
        if (o instanceof Counter) return COUNTER;
        return -1;
    }

    private static int argOf(Object o) {
        if (o instanceof Tbl) return ((Tbl) o).cols();
        if (o instanceof Sz) return ((Sz) o).sp;
        if (o instanceof Fg) return ((Fg) o).color;
        if (o instanceof Indent) return ((Indent) o).level;
        if (o instanceof Fnt) return ((Fnt) o).idx;
        if (o instanceof Align) return ((Align) o).mode;
        if (o instanceof NoteLink) return ((NoteLink) o).noteId;
        if (o instanceof Counter) return ((Counter) o).step;
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
        // a code block is monospace already: inline code inside one is never wanted (it broke the block)
        if (type == BLOCK) apply(e, CODE, s, en, 0, false);
        if (type == BLOCK || type == CALLOUT || type == QUOTE) layoutDirty = true;
    }

    /** True when any part of [s, en) (or the line holding s when s == en) sits inside a code block. */
    static boolean inBlock(Spanned e, int s, int en) {
        int n = e.length();
        int a = Math.max(0, Math.min(s, n));
        int b = Math.max(a, Math.min(en, n));
        for (Block bl : e.getSpans(a, b, Block.class)) {
            int bs = e.getSpanStart(bl);
            int be = e.getSpanEnd(bl);
            if (a == b ? (a >= bs && a <= be) : (bs < b && be > a)) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ paragraph styles
    //
    // Quote, callout, code block, alignment and nesting style whole paragraphs. Android adds up the margins
    // and backgrounds of every such span on a line, so a paragraph must carry exactly ONE span of each kind.
    // Typing and deleting split and grow spans in many small ways; normalize() puts them back into the one
    // canonical shape after every edit: a style is read from the first character of each paragraph, and one
    // span is written per run of neighbouring paragraphs with the same value.

    private static final int[] CHAR_TYPES = {BOLD, ITALIC, UNDER, STRIKE, HILITE, CODE, HEAD, SIZE, FG, FONT};
    private static final int[] PARA_TYPES = {BLOCK, CALLOUT, QUOTE, ALIGN, INDENT};

    private static boolean isMargin(int type) {
        return type == ALIGN || type == INDENT;
    }

    /** Smallest paragraph index whose start is at or after {@code pos}. */
    private static int firstParaAtOrAfter(int[] st, int count, int pos) {
        int lo = 0;
        int hi = count;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (st[mid] < pos) lo = mid + 1;
            else hi = mid;
        }
        return lo;
    }

    /** Puts every style span into its canonical shape (see above). Text is never touched. */
    @SuppressWarnings("unchecked")
    static void normalize(Editable e) {
        normalizeSpans(e);
        highlight(e);
    }

    /**
     * Android applies overlapping text spans in the order they were added, so a code span added after a
     * font span used to turn that text back into the monospace font. A chosen font always wins: when a
     * font span sits before a code span it overlaps, the font spans are added again, after everything else.
     */
    private static void fontsLast(Editable e) {
        int n = e.length();
        Object[] all = e.getSpans(0, n, Object.class);
        java.util.ArrayList<Object> fonts = new java.util.ArrayList<>();
        boolean need = false;
        for (Object o : all) {
            if (o instanceof Fnt) {
                fonts.add(o);
            } else if (o instanceof Code && !need && !fonts.isEmpty()) {
                int a = e.getSpanStart(o);
                int b = e.getSpanEnd(o);
                for (Object f : fonts) {
                    if (e.getSpanStart(f) < b && e.getSpanEnd(f) > a) {
                        need = true;
                        break;
                    }
                }
            }
        }
        if (!need) return;
        for (Object f : fonts) {
            int a = e.getSpanStart(f);
            int b = e.getSpanEnd(f);
            int fl = e.getSpanFlags(f);
            e.removeSpan(f);
            if (a >= 0 && b > a) e.setSpan(f, a, b, fl);
        }
    }

    private static void normalizeSpans(Editable e) {
        int n = e.length();
        Object[] all = e.getSpans(0, n, Object.class);
        java.util.ArrayList<Object>[] by = new java.util.ArrayList[LINK + 1];
        for (Object o : all) {
            int t = typeOf(o);
            if (t < 0 || t > LINK) continue;
            if (by[t] == null) by[t] = new java.util.ArrayList<>();
            by[t].add(o);
        }
        for (int t : CHAR_TYPES) mergeChars(e, t, by[t]);
        fontsLast(e);

        int count = 1;
        for (int i = 0; i < n; i++) if (e.charAt(i) == '\n') count++;
        int[] st = new int[count];
        int[] en = new int[count];
        int k = 0;
        int from = 0;
        for (int i = 0; i <= n; i++) {
            if (i == n || e.charAt(i) == '\n') {
                st[k] = from;
                en[k] = i;
                k++;
                from = i + 1;
            }
        }
        int[][] val = new int[PARA_TYPES.length][];
        for (int j = 0; j < PARA_TYPES.length; j++) val[j] = paraValues(e, PARA_TYPES[j], by[PARA_TYPES[j]], st, count, n);
        int[] blk = val[0];
        int[] cal = val[1];
        int[] quo = val[2];
        for (int p = 0; p < count; p++) { // a paragraph is a code block, or a callout, or a quote
            if (blk[p] != 0) {
                cal[p] = 0;
                quo[p] = 0;
            } else if (cal[p] != 0) {
                quo[p] = 0;
            }
        }
        for (int j = 0; j < PARA_TYPES.length; j++) rebuildParas(e, PARA_TYPES[j], by[PARA_TYPES[j]], st, en, val[j], n);
        if (by[CODE] != null) { // inline code and a code block never mix
            for (Block bl : e.getSpans(0, e.length(), Block.class)) {
                int a = e.getSpanStart(bl);
                int z = e.getSpanEnd(bl);
                if (a >= 0 && z > a) apply(e, CODE, a, z, 0, false);
            }
        }
    }

    /** Merges touching spans of one character style with the same value into single spans. */
    private static void mergeChars(Editable e, int type, java.util.List<Object> spans) {
        if (spans == null || spans.size() < 2) return;
        java.util.ArrayList<int[]> iv = new java.util.ArrayList<>();
        for (Object o : spans) {
            int a = e.getSpanStart(o);
            int b = e.getSpanEnd(o);
            if (a < 0 || a >= b) continue;
            iv.add(new int[]{a, b, argOf(o)});
        }
        iv.sort((x, y) -> x[0] != y[0] ? x[0] - y[0] : x[1] - y[1]);
        java.util.ArrayList<int[]> out = new java.util.ArrayList<>();
        for (int[] r : iv) {
            if (!out.isEmpty()) {
                int[] last = out.get(out.size() - 1);
                if (r[0] <= last[1] && r[2] == last[2]) {
                    last[1] = Math.max(last[1], r[1]);
                    continue;
                }
            }
            out.add(new int[]{r[0], r[1], r[2]});
        }
        if (out.size() == spans.size()) return;
        for (Object o : spans) e.removeSpan(o);
        for (int[] r : out) e.setSpan(make(type, r[2]), r[0], r[1], FLAGS);
    }

    /** The value of one paragraph style for every paragraph (0 = none), read at each paragraph's first character. */
    private static int[] paraValues(Editable e, int type, java.util.List<Object> spans, int[] st, int count, int n) {
        int[] val = new int[count];
        if (spans == null || spans.isEmpty()) return val;
        boolean margin = isMargin(type);
        java.util.ArrayList<int[]> iv = new java.util.ArrayList<>();
        for (Object o : spans) {
            int a = e.getSpanStart(o);
            int b = e.getSpanEnd(o);
            if (a < 0 || a >= b) continue;
            int arg = margin ? argOf(o) : 1;
            if (arg > 0) iv.add(new int[]{a, b, arg});
        }
        iv.sort((x, y) -> x[0] != y[0] ? x[0] - y[0] : x[2] - y[2]); // the span that starts last wins
        for (int[] r : iv) {
            int p = firstParaAtOrAfter(st, count, r[0]);
            // typing at the very start of a styled line (or on a styled empty line) pushes the span's
            // start forward, into the middle of its own paragraph: that paragraph still has the style
            if (p > 0 && r[0] > st[p - 1] && r[0] <= (p < count ? st[p] - 1 : n)) p = p - 1;
            for (; p < count && st[p] < r[1] && st[p] < n; p++) val[p] = r[2];
            // a margin span that reaches the end of the text carries over to an empty last line
            if (margin && n > 0 && r[1] == n && r[0] < n && st[count - 1] == n) val[count - 1] = r[2];
        }
        return val;
    }

    /** Rewrites the spans of one paragraph style as one span per run of paragraphs with the same value. */
    private static void rebuildParas(Editable e, int type, java.util.List<Object> spans, int[] st, int[] en,
                                     int[] val, int n) {
        boolean margin = isMargin(type);
        int count = st.length;
        java.util.ArrayList<int[]> runs = new java.util.ArrayList<>();
        for (int p = 0; p < count; p++) {
            if (val[p] == 0) continue;
            int q = p;
            while (q + 1 < count && val[q + 1] == val[p]) q++;
            int s0 = st[p];
            int e0;
            if (st[q] >= n) e0 = n; // empty last paragraph: the span reaches the end of the text
            else if (en[q] == st[q]) e0 = en[q] + 1; // empty paragraph: its own line break carries the style
            else e0 = en[q];
            if (e0 <= s0) { // only an empty last paragraph: cover the line break before it
                if (n == 0) {
                    p = q;
                    continue;
                }
                s0 = n - 1;
                e0 = n;
            }
            runs.add(new int[]{s0, e0, val[p]});
            p = q;
        }
        java.util.ArrayList<int[]> have = new java.util.ArrayList<>();
        if (spans != null) {
            for (Object o : spans) {
                int a = e.getSpanStart(o);
                int b = e.getSpanEnd(o);
                have.add(new int[]{a, b, margin ? argOf(o) : 1});
            }
        }
        boolean same = have.size() == runs.size();
        if (same) {
            have.sort((x, y) -> x[0] - y[0]);
            for (int i = 0; i < runs.size() && same; i++) {
                int[] x = have.get(i);
                int[] y = runs.get(i);
                if (x[0] != y[0] || x[1] != y[1] || x[2] != y[2]) same = false;
            }
        }
        if (same) return;
        layoutDirty = true;
        if (spans != null) for (Object o : spans) e.removeSpan(o);
        for (int[] r : runs) e.setSpan(make(type, margin ? r[2] : 0), r[0], r[1], FLAGS);
    }

    /**
     * The value of a paragraph style for the paragraph starting at {@code ps}: -1 = not set, otherwise 0
     * (quote, callout, code block) or the level / mode (nesting, alignment).
     */
    static int paraArg(Spanned e, int type, int ps) {
        int n = e.length();
        int best = -1;
        int bestStart = -1;
        if (ps >= n) { // the empty last line: only margin styles carry over to it
            if (!isMargin(type) || n == 0) return -1;
            for (Object o : e.getSpans(n, n, Object.class)) {
                if (typeOf(o) != type) continue;
                int a = e.getSpanStart(o);
                if (e.getSpanEnd(o) == n && a < n && a >= bestStart) {
                    bestStart = a;
                    best = argOf(o);
                }
            }
            return best;
        }
        for (Object o : e.getSpans(ps, ps + 1, Object.class)) {
            if (typeOf(o) != type) continue;
            int a = e.getSpanStart(o);
            if (a <= ps && e.getSpanEnd(o) > ps && a >= bestStart) {
                bestStart = a;
                best = isMargin(type) ? argOf(o) : 0;
            }
        }
        return best;
    }

    /** Sets (arg &gt; 0) or clears (arg 0) a margin style on the one paragraph starting at {@code ps}. */
    private static void setParaSpan(Editable e, int type, int ps, int arg) {
        int n = e.length();
        int pe = lineEnd(e, ps);
        int stop = pe < n ? pe + 1 : pe;
        if (stop <= ps) { // the empty last paragraph: use the line break before it so the span touches the end
            if (ps == 0) return;
            for (Object o : e.getSpans(ps, ps, Object.class)) {
                if (typeOf(o) != type) continue;
                int a = e.getSpanStart(o);
                int b = e.getSpanEnd(o);
                if (b != ps) continue;
                int keep = argOf(o);
                e.removeSpan(o);
                if (a < ps - 1) e.setSpan(make(type, keep), a, ps - 1, FLAGS);
            }
            if (arg > 0) e.setSpan(make(type, arg), ps - 1, ps, FLAGS);
            return;
        }
        for (Object o : e.getSpans(ps, stop, Object.class)) {
            if (typeOf(o) != type) continue;
            int a = e.getSpanStart(o);
            int b = e.getSpanEnd(o);
            int keep = argOf(o);
            e.removeSpan(o);
            if (a < ps) e.setSpan(make(type, keep), a, ps, FLAGS); // the parts on other paragraphs stay
            if (b > stop) e.setSpan(make(type, keep), stop, b, FLAGS);
        }
        if (arg > 0) e.setSpan(make(type, arg), ps, stop, FLAGS);
    }

    /** Paragraph alignment (0 left, 1 centre, 2 right) of the paragraph at {@code pos}. */
    static int alignAt(Spanned e, int pos) {
        return Math.max(0, paraArg(e, ALIGN, lineStart(e, pos)));
    }

    /** Aligns every paragraph touched by [s, en). */
    static void setAlign(Editable e, int s, int en, int mode) {
        int a = lineStart(e, s);
        int hi = en > s && en > 0 && en <= e.length() && e.charAt(en - 1) == '\n' ? en - 1 : en;
        int lastStart = lineStart(e, hi);
        int p = a;
        while (true) {
            setParaSpan(e, ALIGN, p, mode);
            if (p >= lastStart) break;
            p = lineEnd(e, p) + 1;
            if (p > e.length()) break;
        }
        normalize(e);
    }

    /** True when every paragraph touched by [s, en) has the style (quote, callout, code block). */
    static boolean paragraphsHave(Spanned e, int type, int s, int en) {
        int a = lineStart(e, s);
        int hi = en > s && en > 0 && en <= e.length() && e.charAt(en - 1) == '\n' ? en - 1 : en;
        int lastStart = lineStart(e, hi);
        int p = a;
        while (true) {
            if (paraArg(e, type, p) < 0) return false;
            if (p >= lastStart) return true;
            p = lineEnd(e, p) + 1;
            if (p > e.length()) return false;
        }
    }

    /**
     * Turns quote / callout / code block on or off for the paragraphs touched by [s, en). An empty last
     * paragraph gets a line break of its own to carry the style; true is returned when that was added.
     */
    static boolean setParagraph(Editable e, int type, int s, int en, boolean on) {
        boolean added = false;
        int n = e.length();
        int a = lineStart(e, s);
        int hi = en > s && en > 0 && en <= n && e.charAt(en - 1) == '\n' ? en - 1 : en;
        int lastStart = lineStart(e, hi);
        if (on && lastStart >= n) {
            e.insert(n, "\n");
            n = e.length();
            added = true;
        }
        int b = lineEnd(e, lastStart);
        int end = Math.min(n, b == lastStart ? b + 1 : b);
        if (end > a) {
            if (on) {
                for (int other : new int[]{QUOTE, CALLOUT, BLOCK}) {
                    if (other != type) apply(e, other, a, end, 0, false);
                }
            }
            apply(e, type, a, end, 0, on);
        }
        normalize(e);
        return added;
    }

    /**
     * Enter pressed on an empty quote / callout / code-block line: take the line break back and make the
     * line plain, so a second Enter leaves the block. Returns true when it did that.
     */
    static boolean leaveStyle(Editable e, int ps, int nl) {
        if (nl != ps) return false;
        int q = nl + 1;
        boolean styled = false;
        for (int ty : new int[]{BLOCK, CALLOUT, QUOTE}) {
            if (paraArg(e, ty, ps) >= 0 || paraArg(e, ty, q) >= 0) styled = true;
        }
        if (!styled) return false;
        e.delete(nl, nl + 1);
        int end = Math.min(e.length(), ps + 1);
        if (end > ps) {
            for (int ty : new int[]{BLOCK, CALLOUT, QUOTE}) apply(e, ty, ps, end, 0, false);
        }
        normalize(e);
        return true;
    }

    /**
     * Enter pressed at the end of a styled paragraph: the new paragraph keeps the quote / callout / code
     * block / alignment. True is returned when a line break was added at the end of the text to hold it.
     */
    static boolean inheritStyles(Editable e, int ps, int nl) {
        if (nl <= ps) return false;
        int q = nl + 1;
        boolean added = false;
        for (int ty : new int[]{BLOCK, CALLOUT, QUOTE}) {
            if (paraArg(e, ty, ps) < 0 || paraArg(e, ty, q) >= 0) continue;
            if (q >= e.length()) {
                e.insert(q, "\n");
                added = true;
            }
            int end = lineEnd(e, q);
            if (end == q) end = q + 1;
            apply(e, ty, q, Math.min(e.length(), end), 0, true);
        }
        int m = paraArg(e, ALIGN, ps);
        if (m > 0 && q < e.length() && paraArg(e, ALIGN, q) < 0) {
            int end = lineEnd(e, q);
            if (end == q) end = q + 1;
            apply(e, ALIGN, q, Math.min(e.length(), end), m, true);
        }
        normalize(e);
        return added;
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
        return Math.max(0, paraArg(e, INDENT, ls));
    }

    /** Sets the nesting level of the line starting at {@code ls}. */
    static void setIndent(Editable e, int ls, int level) {
        setParaSpan(e, INDENT, ls, Math.max(0, Math.min(MAX_INDENT, level)));
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
            String all = text.toString();
            int end = all.length(); // blank lines left at the very end (made while styling) are not saved
            while (end >= 2 && all.charAt(end - 1) == '\n' && all.charAt(end - 2) == '\n') end--;
            o.put("t", all.substring(0, end));
            JSONArray arr = new JSONArray();
            if (text instanceof Spanned) {
                Spanned sp = (Spanned) text;
                for (Object s : sp.getSpans(0, sp.length(), Object.class)) {
                    int type = typeOf(s);
                    int a = sp.getSpanStart(s);
                    int b = Math.min(end, sp.getSpanEnd(s));
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
                        r.getInt(0) == HIDE || r.getInt(0) == RULE || r.getInt(0) == LINK || r.getInt(0) == COUNTER
                                ? Spanned.SPAN_EXCLUSIVE_EXCLUSIVE : FLAGS);
            }
            for (Object blk : out.getSpans(0, out.length(), Block.class)) { // old notes: drop code-in-block
                apply(out, CODE, out.getSpanStart(blk), out.getSpanEnd(blk), 0, false);
            }
            healCounters(out);
            normalize(out); // heals notes saved with stacked / split paragraph spans
        } catch (JSONException ex) {
            out.clear();
            out.append(json);
        }
    }

    /** True when the stored note {@code json} has a link span pointing at note {@code id}. */
    static boolean linksTo(String json, int id) {
        try {
            JSONArray arr = new JSONObject(json).optJSONArray("s");
            if (arr == null) return false;
            for (int i = 0; i < arr.length(); i++) {
                JSONArray r = arr.getJSONArray(i);
                if (r.getInt(0) == LINK && r.optInt(3) == id) return true;
            }
        } catch (JSONException ignored) {
            // not our JSON: no links
        }
        return false;
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
