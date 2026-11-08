package app.privatetodo;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.pdf.PdfDocument;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.regex.Pattern;

/**
 * Sharing a note to other apps. The share sheet only ever carries plain text or a file, so how much of the look
 * survives depends on the format picked here: Text (none), Markdown (for apps that read it), WhatsApp (its own
 * *bold* _italic_ ~strike~ marks, which it does show), or PDF (everything, as a picture of the page).
 */
final class ShareKit {
    static final int TEXT = 0;
    static final int MARKDOWN = 1;
    static final int WHATSAPP = 2;
    static final int PDF = 3;
    static final String[] NAMES = {"Text", "Markdown", "WhatsApp", "PDF"};
    static final String[] NOTES = {
            "Plain words only. Works everywhere; every style is dropped.",
            "# headings, **bold**, lists and code as Markdown. Good for apps that understand it (Obsidian, GitHub, Notion).",
            "Bold, italic, strike, code, lists and quotes use WhatsApp's own marks, so they show as styled text. Colours, sizes, fonts and tables cannot be sent as text.",
            "A PDF with the note's look: colours, sizes, fonts, tables. Shared as a file."};

    private ShareKit() {
    }

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    /** -1 = nothing remembered. */
    static int lastFormat(Context c) {
        return sp(c).getInt("share_fmt", -1);
    }

    static boolean always(Context c) {
        return sp(c).getBoolean("share_always", false) && lastFormat(c) >= 0;
    }

    static void remember(Context c, int fmt, boolean always) {
        sp(c).edit().putInt("share_fmt", fmt).putBoolean("share_always", always).apply();
    }

    // ------------------------------------------------------------------ converting

    static String plain(Spanned body, String title) {
        String md = Markdown.export(body, null);
        md = md.replaceAll("(?m)^```.*$\\n?", "");
        md = md.replaceAll("(?m)^#{1,6}\\s+", "");
        md = md.replaceAll("\\*\\*(.+?)\\*\\*", "$1").replaceAll("~~(.+?)~~", "$1").replaceAll("==(.+?)==", "$1");
        md = md.replaceAll("(?<![*\\w])\\*(?!\\s)(.+?)(?<!\\s)\\*(?![*\\w])", "$1");
        md = md.replaceAll("`([^`\\n]+)`", "$1");
        md = md.replaceAll("\\[([^\\]]+)\\]\\(([^)]+)\\)", "$1");
        return (title == null || title.trim().isEmpty() ? "" : title.trim() + "\n\n") + md.trim();
    }

    static String markdown(Spanned body, String title) {
        return Markdown.export(body, title).trim();
    }

    /** WhatsApp marks: *bold* _italic_ ~strike~ ```code``` and "- " lists; headings become bold lines. */
    static String whatsapp(Spanned body, String title) {
        String md = Markdown.export(body, null);
        StringBuilder out = new StringBuilder();
        boolean code = false;
        for (String line : md.split("\n", -1)) {
            if (line.startsWith("```")) {
                code = !code;
                out.append("```\n");
                continue;
            }
            if (code) {
                out.append(line).append('\n');
                continue;
            }
            String l = line;
            java.util.regex.Matcher h = Pattern.compile("^#{1,6}\\s+(.*)$").matcher(l);
            if (h.matches()) {
                out.append('*').append(h.group(1).replace("*", "")).append("*\n");
                continue;
            }
            l = l.replaceAll("^(\\s*)[*+] ", "$1- ");
            l = l.replace("**", "\u0001");           // keep bold apart from italic
            l = l.replaceAll("(?<![\\w*])\\*(?!\\s)(.+?)(?<!\\s)\\*(?![\\w*])", "_$1_");
            l = l.replace("\u0001", "*");
            l = l.replaceAll("\\*\\*(.+?)\\*\\*", "*$1*").replaceAll("~~(.+?)~~", "~$1~").replaceAll("==(.+?)==", "$1");
            l = l.replaceAll("\\[([^\\]]+)\\]\\(([^)]+)\\)", "$1 ($2)");
            out.append(l).append('\n');
        }
        String head = title == null || title.trim().isEmpty() ? "" : "*" + title.trim().replace("*", "") + "*\n\n";
        return head + out.toString().trim();
    }

    /** The note as a PDF in the share cache, or null if it could not be made. */
    static File pdf(Context c, Spanned body, String title, float screenTextPx) {
        File dir = SharedFiles.dir(c);
        File[] old = dir.listFiles();
        if (old != null) for (File f : old) f.delete(); // only the file being shared lives here
        String name = (title == null || title.trim().isEmpty() ? "Note" : title.trim().replaceAll("[^\\p{L}\\p{N} _-]", "").trim());
        if (name.isEmpty()) name = "Note";
        File out = new File(dir, name + ".pdf");
        try {
            writePdf(out, new SpannableStringBuilder(body), title, screenTextPx);
        } catch (RuntimeException | IOException e) {
            try { // a style that would not draw: fall back to the words alone
                writePdf(out, new SpannableStringBuilder(body.toString()), title, screenTextPx);
            } catch (RuntimeException | IOException e2) {
                return null;
            }
        }
        return out;
    }

    private static void writePdf(File out, SpannableStringBuilder text, String title, float screenTextPx) throws IOException {
        final int pw = 595;
        final int ph = 842;
        final int margin = 44;
        final float k = 11f / Math.max(1f, screenTextPx); // screen pixels -> PDF points: body text lands near 11 pt
        TextPaint tp = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
        tp.setColor(Color.BLACK);
        tp.setTextSize(screenTextPx);
        int width = Math.round((pw - 2 * margin) / k);
        StaticLayout lay = StaticLayout.Builder.obtain(text, 0, text.length(), tp, width)
                .setLineSpacing(0f, 1.15f).setIncludePad(false).build();
        TextPaint hp = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
        hp.setColor(Color.BLACK);
        hp.setTypeface(Typeface.DEFAULT_BOLD);
        hp.setTextSize(screenTextPx * 1.7f);
        StaticLayout head = title == null || title.trim().isEmpty() ? null
                : StaticLayout.Builder.obtain(title.trim(), 0, title.trim().length(), hp, width).setIncludePad(false).build();

        PdfDocument doc = new PdfDocument();
        try {
            int pageNo = 1;
            int line = 0;
            int lines = lay.getLineCount();
            float contentH = (ph - 2 * margin) / k;
            float used = 0f;
            boolean first = true;
            while (first || line < lines) {
                PdfDocument.Page page = doc.startPage(new PdfDocument.PageInfo.Builder(pw, ph, pageNo++).create());
                Canvas c = page.getCanvas();
                c.save();
                c.translate(margin, margin);
                c.scale(k, k);
                float y = 0f;
                if (first && head != null) {
                    head.draw(c);
                    y = head.getHeight() + screenTextPx * 0.8f;
                }
                first = false;
                if (line < lines) {
                    int top = lay.getLineTop(line);
                    float room = contentH - y;
                    int end = line;
                    while (end < lines && lay.getLineBottom(end) - top <= room) end++;
                    if (end == line) end = line + 1;
                    c.save();
                    c.translate(0, y - top);
                    c.clipRect(0, top, width, lay.getLineBottom(end - 1));
                    lay.draw(c);
                    c.restore();
                    line = end;
                }
                c.restore();
                doc.finishPage(page);
                if (lines == 0) break;
            }
            try (FileOutputStream fo = new FileOutputStream(out)) {
                doc.writeTo(fo);
            }
        } finally {
            doc.close();
        }
    }

    // ------------------------------------------------------------------ sending

    static void send(Context c, int fmt, Spanned body, String title, float screenTextPx) {
        String subject = title == null || title.trim().isEmpty() ? "Note" : title.trim();
        Intent i = new Intent(Intent.ACTION_SEND);
        if (fmt == PDF) {
            File f = pdf(c, body, title, screenTextPx);
            if (f == null) {
                Toast.makeText(c, "Could not make the PDF", Toast.LENGTH_LONG).show();
                return;
            }
            i.setType("application/pdf").putExtra(Intent.EXTRA_STREAM, SharedFiles.uriFor(f))
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } else {
            String s = fmt == MARKDOWN ? markdown(body, title) : fmt == WHATSAPP ? whatsapp(body, title) : plain(body, title);
            i.setType("text/plain").putExtra(Intent.EXTRA_TEXT, s);
        }
        i.putExtra(Intent.EXTRA_SUBJECT, subject);
        Intent chooser = Intent.createChooser(i, "Share note");
        chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        c.startActivity(chooser);
    }

    // ------------------------------------------------------------------ the panel

    /** A card that sits just above the note's tools: pick the format, optionally keep the choice, share. */
    static final class Panel extends LinearLayout {
        interface Go {
            void share(int fmt);
        }

        private int fmt;
        private boolean keep;
        private final TextView[] chips = new TextView[NAMES.length];
        private final TextView note;
        private final TextView keepBox;

        Panel(Context c, Go go) {
            super(c);
            final float d = c.getResources().getDisplayMetrics().density;
            setOrientation(VERTICAL);
            int p = Math.round(12 * d);
            setPadding(p, p, p, p);
            GradientDrawable g = new GradientDrawable();
            g.setColor(Pal.raised | 0xFF000000);
            g.setCornerRadius(20 * d);
            g.setStroke(Math.max(1, Math.round(d)), Pal.chip);
            setBackground(g);
            setElevation(8 * d);
            setClickable(true);
            fmt = Math.max(0, lastFormat(c));
            keep = sp(c).getBoolean("share_always", false);

            LinearLayout head = new LinearLayout(c);
            head.setGravity(Gravity.CENTER_VERTICAL);
            TextView t = new TextView(c);
            t.setText("Share as");
            t.setTextSize(15);
            t.setTypeface(Typeface.DEFAULT_BOLD);
            t.setTextColor(Pal.ink);
            head.addView(t, new LayoutParams(0, -2, 1f));
            TextView x = new TextView(c);
            x.setText("✕");
            x.setTextSize(15);
            x.setTextColor(Pal.muted);
            x.setGravity(Gravity.CENTER);
            x.setOnClickListener(v -> close());
            head.addView(x, new LayoutParams(Math.round(36 * d), Math.round(32 * d)));
            addView(head);

            LinearLayout row = new LinearLayout(c);
            for (int i = 0; i < NAMES.length; i++) {
                final int idx = i;
                TextView chip = new TextView(c);
                chip.setText(NAMES[i]);
                chip.setTextSize(13);
                chip.setTypeface(Typeface.DEFAULT_BOLD);
                chip.setGravity(Gravity.CENTER);
                chip.setOnClickListener(v -> {
                    fmt = idx;
                    paint();
                });
                chips[i] = chip;
                LayoutParams lp = new LayoutParams(0, Math.round(38 * d), 1f);
                lp.rightMargin = Math.round((i < NAMES.length - 1 ? 6 : 0) * d);
                row.addView(chip, lp);
            }
            LayoutParams rl = new LayoutParams(-1, -2);
            rl.topMargin = Math.round(8 * d);
            addView(row, rl);

            note = new TextView(c);
            note.setTextSize(12);
            note.setTextColor(Pal.muted);
            note.setPadding(0, Math.round(8 * d), 0, Math.round(6 * d));
            addView(note);

            LinearLayout foot = new LinearLayout(c);
            foot.setGravity(Gravity.CENTER_VERTICAL);
            keepBox = new TextView(c);
            keepBox.setTextSize(13);
            keepBox.setTypeface(Typeface.DEFAULT_BOLD);
            keepBox.setPadding(Math.round(12 * d), 0, Math.round(12 * d), 0);
            keepBox.setGravity(Gravity.CENTER_VERTICAL);
            keepBox.setOnClickListener(v -> {
                keep = !keep;
                paint();
            });
            foot.addView(keepBox, new LayoutParams(0, Math.round(38 * d), 1f));
            TextView go1 = new TextView(c);
            go1.setText("Share");
            go1.setTextSize(14);
            go1.setTypeface(Typeface.DEFAULT_BOLD);
            go1.setTextColor(Pal.onAccent);
            go1.setGravity(Gravity.CENTER);
            go1.setPadding(Math.round(22 * d), 0, Math.round(22 * d), 0);
            GradientDrawable gb = new GradientDrawable();
            gb.setColor(Pal.accent);
            gb.setCornerRadius(19 * d);
            go1.setBackground(gb);
            go1.setOnClickListener(v -> {
                remember(c, fmt, keep);
                close();
                go.share(fmt);
            });
            foot.addView(go1, new LayoutParams(-2, Math.round(38 * d)));
            addView(foot);
            paint();
            Fonts.apply(this);
        }

        private void paint() {
            float d = getResources().getDisplayMetrics().density;
            for (int i = 0; i < chips.length; i++) {
                boolean on = i == fmt;
                chips[i].setTextColor(on ? Pal.onSel : Pal.muted);
                GradientDrawable g = new GradientDrawable();
                g.setCornerRadius(19 * d);
                g.setColor(on ? Pal.sel : Pal.chip);
                chips[i].setBackground(g);
            }
            note.setText(NOTES[fmt]);
            keepBox.setText((keep ? "☑  " : "☐  ") + "Always use this");
            keepBox.setTextColor(keep ? Pal.accent : Pal.muted);
        }

        void open(FrameLayout area) {
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
            float d = getResources().getDisplayMetrics().density;
            lp.setMargins(Math.round(10 * d), 0, Math.round(10 * d), Math.round(8 * d));
            area.addView(this, lp);
        }

        void close() {
            if (getParent() != null) ((android.view.ViewGroup) getParent()).removeView(this);
        }

        boolean isOpen() {
            return getParent() != null;
        }
    }
}
