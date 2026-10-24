package app.privatetodo;

import android.text.Editable;
import android.text.Spanned;
import android.text.SpannableStringBuilder;

import java.util.ArrayList;

/** Notes <-> Markdown: copy / export a note as Markdown, and paste Markdown in as formatted text. */
final class Markdown {
    private Markdown() { }

    // ------------------------------------------------------------------ export

    static String export(Spanned e, String title) {
        StringBuilder out = new StringBuilder();
        if (title != null && !title.trim().isEmpty()) out.append("# ").append(title.trim()).append("\n\n");
        int n = e.length();
        int p = 0;
        boolean inCode = false;
        while (p <= n) {
            int le = RichText.lineEnd(e, p);
            boolean code = le > p && RichText.active(e, RichText.BLOCK, p, le);
            if (code && !inCode) {
                out.append("```\n");
                inCode = true;
            } else if (!code && inCode) {
                out.append("```\n");
                inCode = false;
            }
            if (code) {
                out.append(e.subSequence(p, le)).append('\n');
            } else {
                out.append(line(e, p, le)).append('\n');
            }
            if (le >= n) break;
            p = le + 1;
        }
        if (inCode) out.append("```\n");
        return out.toString().replaceAll("\\s+$", "") + "\n";
    }

    private static String line(Spanned e, int p, int le) {
        if (le > p && e.charAt(p) == RichText.TABLE_CHAR) {
            RichText.Tbl t = RichText.tableOnLine(e, p);
            if (t != null) return table(t);
        }
        if (le > p && e.charAt(p) == RichText.RULE_CHAR) return "---";
        int lvl = RichText.indentOf(e, p);
        StringBuilder padB = new StringBuilder();
        for (int z = 0; z < lvl; z++) padB.append("  ");
        String pad = padB.toString();
        int mk = RichText.prefixAt(e, p);
        int num = mk == RichText.P_NONE ? RichText.numLen(e, p) : 0;
        int from = p + (mk != RichText.P_NONE ? 2 : num);
        String body = inline(e, from, le);
        String prefix = "";
        if (mk == RichText.P_BULLET || RichText.isToggle(mk)) prefix = pad + "- ";
        else if (mk == RichText.P_CHECK_OFF) prefix = pad + "- [ ] ";
        else if (mk == RichText.P_CHECK_ON) prefix = pad + "- [x] ";
        else if (num > 0) prefix = pad + e.subSequence(p, p + num);
        else prefix = pad;
        if (le > p && RichText.active(e, RichText.HEAD, p, le)) prefix = "# ";
        else if (le > p && RichText.active(e, RichText.QUOTE, p, le)) prefix = "> ";
        else if (le > p && RichText.active(e, RichText.CALLOUT, p, le)) prefix = "> \uD83D\uDCA1 ";
        if (RichText.isToggle(mk)) body = "**" + body.replace("**", "") + "**";
        return prefix + body;
    }

    private static boolean on(Spanned e, int type, int i) {
        return RichText.active(e, type, i, i + 1);
    }

    private static String inline(Spanned e, int a, int b) {
        StringBuilder sb = new StringBuilder();
        int i = a;
        while (i < b) {
            boolean bo = on(e, RichText.BOLD, i);
            boolean it = on(e, RichText.ITALIC, i);
            boolean st = on(e, RichText.STRIKE, i);
            boolean hl = on(e, RichText.HILITE, i);
            boolean co = on(e, RichText.CODE, i);
            int j = i + 1;
            while (j < b && on(e, RichText.BOLD, j) == bo && on(e, RichText.ITALIC, j) == it
                    && on(e, RichText.STRIKE, j) == st && on(e, RichText.HILITE, j) == hl
                    && on(e, RichText.CODE, j) == co) j++;
            String run = e.subSequence(i, j).toString();
            String lead = run.substring(0, run.length() - run.replaceAll("^\\s+", "").length());
            String trail = run.substring(run.replaceAll("\\s+$", "").length());
            String core = run.trim();
            if (core.isEmpty() || (!bo && !it && !st && !hl && !co)) {
                sb.append(run);
            } else {
                String o = "";
                String c = "";
                if (co) { o = "`" + o; c += "`"; }
                if (hl) { o = "==" + o; c += "=="; }
                if (st) { o = "~~" + o; c += "~~"; }
                if (it) { o = "*" + o; c += "*"; }
                if (bo) { o = "**" + o; c += "**"; }
                sb.append(lead).append(o).append(core).append(reverse(c)).append(trail);
            }
            i = j;
        }
        return sb.toString();
    }

    /** closing markers mirror the opening ones; each marker is symmetric so reversing the order is enough */
    private static String reverse(String c) {
        return c;
    }

    private static String table(RichText.Tbl t) {
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < t.rows(); r++) {
            sb.append('|');
            for (int k = 0; k < t.cols(); k++) {
                String cell = t.cells[r][k] == null ? "" : t.cells[r][k];
                int tk = RichText.Tbl.tick(cell);
                String lab = RichText.Tbl.label(cell).replace("|", "\\|").replace('\n', ' ');
                sb.append(' ');
                if (tk == 1) sb.append("[ ] ");
                if (tk == 2) sb.append("[x] ");
                sb.append(lab).append(" |");
            }
            sb.append('\n');
            if (r == 0) {
                sb.append('|');
                for (int k = 0; k < t.cols(); k++) sb.append(" --- |");
                sb.append('\n');
            }
        }
        return sb.toString().replaceAll("\\n$", "");
    }

    // ------------------------------------------------------------------ import

    /** One styled range found while parsing; applied once the whole text exists. */
    private static final class Op {
        final int type;
        final int a;
        final int b;
        final int arg;

        Op(int type, int a, int b, int arg) {
            this.type = type;
            this.a = a;
            this.b = b;
            this.arg = arg;
        }
    }

    /** Parses Markdown into rich text (not yet in any note). */
    static SpannableStringBuilder parse(String md) {
        StringBuilder tb = new StringBuilder();
        ArrayList<Op> ops = new ArrayList<>();
        ArrayList<Object[]> blocks = new ArrayList<>(); // {Integer pos, span}
        ArrayList<int[]> indents = new ArrayList<>();
        String[] lines = md.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        boolean code = false;
        for (int i = 0; i < lines.length; i++) {
            String raw = lines[i];
            String tr = raw.trim();
            if (tr.startsWith("```")) {
                code = !code;
                continue;
            }
            int ls = tb.length();
            if (code) {
                tb.append(raw);
                if (tb.length() > ls) ops.add(new Op(RichText.BLOCK, ls, tb.length(), 0));
                tb.append('\n');
                continue;
            }
            if (tr.matches("^(-{3,}|\\*{3,}|_{3,})$")) {
                tb.append(RichText.RULE_CHAR);
                blocks.add(new Object[]{ls, new RichText.Rule()});
                tb.append('\n');
                continue;
            }
            if (tr.startsWith("|") && i + 1 < lines.length && lines[i + 1].trim().matches("^\\|?[\\s:|-]+\\|?$")
                    && lines[i + 1].contains("-")) {
                ArrayList<String[]> rows = new ArrayList<>();
                rows.add(cells(tr));
                int j = i + 2;
                while (j < lines.length && lines[j].trim().startsWith("|")) {
                    rows.add(cells(lines[j].trim()));
                    j++;
                }
                int cols = 0;
                for (String[] r : rows) cols = Math.max(cols, r.length);
                cols = Math.max(2, Math.min(8, cols));
                String[][] cell = new String[Math.max(2, rows.size())][cols];
                for (int r = 0; r < cell.length; r++) {
                    for (int k = 0; k < cols; k++) {
                        String v = r < rows.size() && k < rows.get(r).length ? rows.get(r)[k] : "";
                        cell[r][k] = r == 0 ? v : tickCell(v);
                    }
                }
                tb.append(RichText.TABLE_CHAR);
                blocks.add(new Object[]{ls, new RichText.Tbl(cell)});
                tb.append('\n');
                i = j - 1;
                continue;
            }
            int lvl = 0;
            int q = 0;
            int spaces = 0;
            while (q < raw.length() && (raw.charAt(q) == ' ' || raw.charAt(q) == '\t')) {
                if (raw.charAt(q) == '\t') lvl++;
                else spaces++;
                q++;
            }
            lvl = Math.min(RichText.MAX_INDENT, lvl + spaces / 2);
            String rest = raw.substring(q);
            String marker = "";
            int lineType = -1;
            if (rest.matches("^[-*+] \\[[ xX]\\]( .*)?$")) {
                marker = rest.charAt(3) != ' ' ? RichText.CHECK_ON : RichText.CHECK_OFF;
                rest = rest.length() > 6 ? rest.substring(6) : "";
            } else if (rest.matches("^[-*+] .*")) {
                marker = RichText.BULLET;
                rest = rest.substring(2);
            } else if (rest.matches("^\\d{1,3}[.)] .*")) {
                int d = rest.indexOf(' ');
                marker = rest.substring(0, d - 1) + ". ";
                rest = rest.substring(d + 1);
            } else if (rest.matches("^#{1,6} .*")) {
                rest = rest.replaceFirst("^#{1,6} ", "");
                lineType = RichText.HEAD;
            } else if (rest.startsWith("> ") || rest.equals(">")) {
                rest = rest.length() > 2 ? rest.substring(2) : "";
                lineType = RichText.QUOTE;
                if (rest.startsWith("\uD83D\uDCA1 ")) {
                    rest = rest.substring(3);
                    lineType = RichText.CALLOUT;
                }
            }
            tb.append(marker);
            int textStart = tb.length();
            inlineInto(tb, ops, rest);
            int le = tb.length();
            if (lineType >= 0 && le > ls) ops.add(new Op(lineType, ls, le, 0));
            if (lvl > 0) indents.add(new int[]{ls, lvl});
            if (marker.equals(RichText.CHECK_ON) && le > textStart) ops.add(new Op(RichText.STRIKE, textStart, le, 0));
            tb.append('\n');
        }
        if (tb.length() > 0 && tb.charAt(tb.length() - 1) == '\n') tb.setLength(tb.length() - 1);
        SpannableStringBuilder out = new SpannableStringBuilder(tb);
        for (Op o : ops) {
            int a = Math.min(o.a, out.length());
            int b = Math.min(o.b, out.length());
            if (a < b) RichText.apply(out, o.type, a, b, o.arg, true);
        }
        for (Object[] bl : blocks) {
            int at = (Integer) bl[0];
            if (at < out.length()) out.setSpan(bl[1], at, at + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        for (int[] in : indents) {
            if (in[0] <= out.length()) RichText.setIndent(out, in[0], in[1]);
        }
        RichText.normalize(out);
        return out;
    }

    private static String tickCell(String v) {
        v = v.trim();
        if (v.startsWith("[ ] ") || v.equals("[ ]")) return RichText.Tbl.compose(1, v.length() > 4 ? v.substring(4) : "");
        if (v.startsWith("[x] ") || v.startsWith("[X] ") || v.equals("[x]")) {
            return RichText.Tbl.compose(2, v.length() > 4 ? v.substring(4) : "");
        }
        return v;
    }

    private static String[] cells(String row) {
        String r = row.trim();
        if (r.startsWith("|")) r = r.substring(1);
        if (r.endsWith("|") && !r.endsWith("\\|")) r = r.substring(0, r.length() - 1);
        String[] parts = r.split("(?<!\\\\)\\|", -1);
        for (int i = 0; i < parts.length; i++) parts[i] = parts[i].replace("\\|", "|").trim();
        return parts;
    }

    /** Appends one line of inline Markdown (bold, italic, strike, code, ==highlight==); styles go to {@code ops}. */
    private static void inlineInto(StringBuilder out, ArrayList<Op> ops, String s) {
        String[] marks = {"**", "~~", "==", "`", "*", "_"};
        int[] types = {RichText.BOLD, RichText.STRIKE, RichText.HILITE, RichText.CODE, RichText.ITALIC, RichText.ITALIC};
        int[] open = {-1, -1, -1, -1, -1, -1};
        int i = 0;
        while (i < s.length()) {
            boolean hit = false;
            for (int m = 0; m < marks.length; m++) {
                String mk = marks[m];
                if (!s.startsWith(mk, i)) continue;
                if (open[m] >= 0) {
                    int a = open[m];
                    open[m] = -1;
                    if (out.length() > a) {
                        ops.add(new Op(types[m], a, out.length(), types[m] == RichText.HILITE ? RichText.HL_COLORS[0] : 0));
                    }
                    i += mk.length();
                    hit = true;
                    break;
                }
                if (mk.equals("_") && i > 0 && Character.isLetterOrDigit(s.charAt(i - 1))) continue;
                if (s.indexOf(mk, i + mk.length()) > i + mk.length()) { // only if it closes later on the line
                    open[m] = out.length();
                    i += mk.length();
                    hit = true;
                    break;
                }
            }
            if (hit) continue;
            if (s.charAt(i) == '\\' && i + 1 < s.length()) i++;
            out.append(s.charAt(i));
            i++;
        }
    }

    /**
     * Puts Markdown into the note at {@code pos} (on a line of its own) and returns the cursor offset
     * after it.
     */
    static int insert(Editable e, int pos, String md) {
        SpannableStringBuilder rich = parse(md);
        if (rich.length() == 0) return pos;
        int ls = RichText.lineStart(e, pos);
        int le = RichText.lineEnd(e, pos);
        int at;
        if (le > ls) { // the line has text: go below it
            e.insert(le, "\n");
            at = le + 1;
        } else {
            at = ls;
        }
        e.insert(at, rich);
        int end = at + rich.length();
        if (end >= e.length() || e.charAt(end) != '\n') {
            e.insert(end, "\n");
        }
        RichText.renumber(e, at, -1);
        RichText.normalize(e);
        RichText.refold(e);
        return Math.min(e.length(), end + 1);
    }

    /** The Markdown version of the text in [s, en) of a note, as formatted text replacing it. */
    static int convertSelection(Editable e, int s, int en) {
        String md = e.subSequence(s, en).toString();
        SpannableStringBuilder rich = parse(md);
        e.replace(s, en, rich);
        RichText.renumber(e, s, -1);
        RichText.normalize(e);
        return s + rich.length();
    }
}
