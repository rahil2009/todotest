package app.privatetodo;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.text.Editable;
import android.text.Spanned;
import android.text.SpannableStringBuilder;

/**
 * Copy / cut / paste that keeps formatting, tables, counters and ticks, between notes. The rich copy lives only in
 * memory (never written anywhere); the system clipboard gets plain text, and a paste uses the rich copy only while the
 * clipboard still holds exactly that text.
 */
final class RichClip {
    private static String json;
    private static String plain;

    private RichClip() {
    }

    /** Copies [a, b) of the text. Returns false when nothing was copied. */
    static boolean copy(Context c, Editable e, int a, int b) {
        if (a < 0 || b > e.length() || a >= b) return false;
        CharSequence sub = e.subSequence(a, b);
        plain = sub.toString().replace(RichText.TABLE_CHAR, ' ');
        json = RichText.toJson(sub);
        ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("note", plain));
        return true;
    }

    /** Copies one table. */
    static boolean copyTable(Context c, Editable e, RichText.Tbl t) {
        int a = e.getSpanStart(t);
        return a >= 0 && copy(c, e, a, Math.min(e.length(), a + 1));
    }

    /** Pastes over [a, b); returns the caret position after it, or -1 when the clipboard is empty. */
    static int paste(Context c, Editable e, int a, int b) {
        ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null || !cm.hasPrimaryClip() || cm.getPrimaryClip().getItemCount() == 0) return -1;
        CharSequence s = cm.getPrimaryClip().getItemAt(0).coerceToText(c);
        if (s == null) return -1;
        a = Math.max(0, Math.min(a, e.length()));
        b = Math.max(a, Math.min(b, e.length()));
        if (json != null && plain != null && plain.contentEquals(s)) {
            SpannableStringBuilder tmp = new SpannableStringBuilder();
            RichText.fromJson(json, tmp);
            Arrows.remap(tmp, e);
            boolean table = tmp.toString().indexOf(RichText.TABLE_CHAR) >= 0;
            if (table) { // a table owns its line
                if (a > 0 && e.charAt(a - 1) != '\n') tmp.insert(0, "\n");
                if (b >= e.length() || e.charAt(b) != '\n') tmp.append("\n");
            }
            e.replace(a, b, tmp);
            return a + tmp.length();
        }
        e.replace(a, b, s.toString());
        return a + s.length();
    }
}
