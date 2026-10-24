package app.privatetodo;

import android.text.Editable;

import java.util.ArrayList;
import java.util.Locale;

/** Letter-case conversions for the notes editor (Case tab). */
final class CaseTools {
    private CaseTools() { }

    static final String[] NAMES = {"UPPER", "lower", "Title Case", "Sentence case", "camelCase",
            "PascalCase", "snake_case", "kebab-case", "CONSTANT", "dot.case", "aLtErNaTe", "iNVERT"};
    /** What each one does to "hello big world", shown under the name. */
    static final String[] SAMPLES = {"HELLO BIG WORLD", "hello big world", "Hello Big World",
            "Hello big world", "helloBigWorld", "HelloBigWorld", "hello_big_world", "hello-big-world",
            "HELLO_BIG_WORLD", "hello.big.world", "hElLo BiG wOrLd", "HELLO BIG WORLD"};

    static String convert(int mode, String s) {
        switch (mode) {
            case 0:
                return s.toUpperCase(Locale.getDefault());
            case 1:
                return s.toLowerCase(Locale.getDefault());
            case 2:
                return title(s);
            case 3:
                return sentence(s);
            case 4:
                return join(words(s), "", 1);
            case 5:
                return join(words(s), "", 2);
            case 6:
                return join(words(s), "_", 0);
            case 7:
                return join(words(s), "-", 0);
            case 8:
                return join(words(s), "_", 3);
            case 9:
                return join(words(s), ".", 0);
            case 10:
                return alternate(s);
            default:
                return invert(s);
        }
    }

    private static boolean wordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '\'' || c == '’';
    }

    static String title(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        boolean start = true;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (wordChar(c)) {
                sb.append(start ? Character.toUpperCase(c) : Character.toLowerCase(c));
                start = false;
            } else {
                sb.append(c);
                start = true;
            }
        }
        return sb.toString();
    }

    static String sentence(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        boolean cap = true;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetter(c)) {
                sb.append(cap ? Character.toUpperCase(c) : Character.toLowerCase(c));
                cap = false;
            } else {
                sb.append(c);
                if (c == '.' || c == '!' || c == '?') cap = true;
            }
        }
        return sb.toString();
    }

    static String alternate(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        boolean up = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetter(c)) {
                sb.append(up ? Character.toUpperCase(c) : Character.toLowerCase(c));
                up = !up;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    static String invert(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isUpperCase(c)) sb.append(Character.toLowerCase(c));
            else if (Character.isLowerCase(c)) sb.append(Character.toUpperCase(c));
            else sb.append(c);
        }
        return sb.toString();
    }

    /** Splits on anything that is not a letter or digit, and at lower-to-UPPER humps (helloWorld). */
    static ArrayList<String> words(String s) {
        ArrayList<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!Character.isLetterOrDigit(c)) {
                if (cur.length() > 0) out.add(cur.toString());
                cur.setLength(0);
                continue;
            }
            if (cur.length() > 0 && Character.isUpperCase(c)
                    && Character.isLowerCase(cur.charAt(cur.length() - 1))) {
                out.add(cur.toString());
                cur.setLength(0);
            }
            cur.append(c);
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    /** style: 0 = all lower, 1 = camel (first word lower), 2 = Pascal, 3 = UPPER. */
    private static String join(ArrayList<String> w, String sep, int style) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < w.size(); i++) {
            String x = w.get(i).toLowerCase(Locale.getDefault());
            if (style == 3) x = x.toUpperCase(Locale.getDefault());
            else if (style == 2 || (style == 1 && i > 0)) x = Character.toUpperCase(x.charAt(0)) + x.substring(1);
            if (i > 0) sb.append(sep);
            sb.append(x);
        }
        return sb.toString();
    }

    /**
     * Replaces [a, b) with {@code repl} but keeps the note's styles on it: spans that overlap the
     * range are put back on the new text (clamped when its length changed).
     */
    static void replaceKeep(Editable e, int a, int b, String repl) {
        if (e.subSequence(a, b).toString().equals(repl)) return;
        Object[] all = e.getSpans(a, b, Object.class);
        ArrayList<Object> keep = new ArrayList<>();
        for (Object o : all) if (RichText.typeOf(o) >= 0 && !(o instanceof RichText.Tbl)) keep.add(o);
        int[] st = new int[keep.size()];
        int[] en = new int[keep.size()];
        int[] fl = new int[keep.size()];
        for (int i = 0; i < keep.size(); i++) {
            st[i] = e.getSpanStart(keep.get(i));
            en[i] = e.getSpanEnd(keep.get(i));
            fl[i] = e.getSpanFlags(keep.get(i));
        }
        int delta = repl.length() - (b - a);
        e.replace(a, b, repl);
        int end = a + repl.length();
        for (int i = 0; i < keep.size(); i++) {
            int ns = st[i] <= a ? st[i] : st[i] >= b ? st[i] + delta : Math.min(end, st[i]);
            int ne = en[i] <= a ? en[i] : en[i] >= b ? en[i] + delta : Math.min(end, en[i]);
            if (ne > ns) e.setSpan(keep.get(i), ns, ne, fl[i]);
        }
    }

    /**
     * Applies a case mode to [s, en), one line at a time and without touching list markers.
     * With just a cursor (s == en) the word under it is converted. Returns the new end offset.
     */
    static int apply(Editable e, int mode, int s, int en) {
        if (s == en) {
            int n = e.length();
            int a = s;
            int b = s;
            while (a > 0 && Character.isLetterOrDigit(e.charAt(a - 1))) a--;
            while (b < n && Character.isLetterOrDigit(e.charAt(b))) b++;
            if (a == b) return en;
            int before = e.length();
            replaceKeep(e, a, b, convert(mode, e.subSequence(a, b).toString()));
            return Math.max(0, en + e.length() - before);
        }
        int p = RichText.lineStart(e, s);
        int lastStart = RichText.lineStart(e, en);
        int delta = 0;
        // bottom-up so earlier offsets stay valid
        ArrayList<int[]> lines = new ArrayList<>();
        while (true) {
            int le = RichText.lineEnd(e, p);
            int a = Math.max(s, p + RichText.markerLen(e, p));
            int b = Math.min(en, le);
            if (a < b) lines.add(new int[]{a, b});
            if (p >= lastStart || le >= e.length()) break;
            p = le + 1;
        }
        for (int i = lines.size() - 1; i >= 0; i--) {
            int a = lines.get(i)[0];
            int b = lines.get(i)[1];
            String seg = e.subSequence(a, b).toString();
            if (seg.indexOf(RichText.TABLE_CHAR) >= 0 || seg.indexOf(RichText.RULE_CHAR) >= 0) continue;
            String out = convert(mode, seg);
            delta += out.length() - seg.length();
            replaceKeep(e, a, b, out);
        }
        return Math.max(s, en + delta);
    }
}
