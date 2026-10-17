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

    /** Full-width shaded background with an accent bar, for code blocks. */
    static final class Block implements LineBackgroundSpan {
        @Override
        public void drawBackground(Canvas c, Paint p, int left, int right, int top, int baseline,
                                   int bottom, CharSequence text, int start, int end, int lnum) {
            int old = p.getColor();
            p.setColor(codeBg());
            c.drawRect(left, top, right, bottom, p);
            p.setColor(Pal.accent);
            c.drawRect(left, top, left + 2 * density, bottom, p);
            p.setColor(old);
        }
    }

    static Object make(int type, int arg) {
        switch (type) {
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
        return -1;
    }

    private static int argOf(Object o) {
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
        if (type == BLOCK) apply(e, CODE, s, en, 0, on); // a code block is monospace too
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
