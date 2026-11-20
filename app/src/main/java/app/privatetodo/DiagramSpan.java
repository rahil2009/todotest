package app.privatetodo;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.Editable;
import android.text.Spanned;

/**
 * A diagram block in a note: one line, like a table, that shows the whole diagram scaled to the note's width.
 * Tapping it opens the canvas editor (DiagramActivity). The diagram itself is kept in {@link #d}.
 */
final class DiagramSpan extends android.text.style.ReplacementSpan {
    Diagram d;
    float drawX = -1, drawTop = -1, drawW, drawH;
    private float effMult = -1f;
    private boolean wasLast;
    private int laidH = -1;
    private long healAt;
    private int heals;

    DiagramSpan(Diagram d) {
        this.d = d;
    }

    private static float density() {
        return android.content.res.Resources.getSystem().getDisplayMetrics().density;
    }

    private int width() {
        return RichText.tableWidth > 0 ? RichText.tableWidth : Math.round(300 * density());
    }

    /** dp -> px factor for the preview: the whole canvas width fits the note. */
    private float scale() {
        return width() / Diagram.W;
    }

    /** The content's top and bottom (dp) shown in the note, with a margin; an empty diagram gets a short placeholder. */
    private float[] band() {
        if (d.nodes.isEmpty()) return new float[]{0, 72};
        float t = Math.max(0, d.top() - 14), b = d.bottom() + 14;
        return new float[]{t, Math.max(t + 48, b)};
    }

    private int totalH() {
        float[] bd = band();
        return Math.round((bd[1] - bd[0]) * scale() + 12 * density());
    }

    @Override
    public int getSize(Paint p, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
        if (fm != null) {
            boolean lastLine = end >= text.length();
            if (effMult < 0 || lastLine != wasLast) {
                wasLast = lastLine;
                effMult = lastLine ? 1f : Math.max(1f, RichText.lineMult);
            }
            int h = (int) Math.ceil(totalH() / effMult);
            laidH = h;
            fm.ascent = -h;
            fm.top = -h;
            fm.descent = 0;
            fm.bottom = 0;
        }
        return width();
    }

    @Override
    public void draw(Canvas c, CharSequence text, int start, int end, float x, int top, int y, int bottom, Paint p) {
        float dens = density();
        float w = width(), h = totalH();
        float lineH = bottom - top;
        if (laidH > 0 && Math.abs(lineH - h) > 1.5f && text instanceof Editable) { // same self-heal as tables
            long now = android.os.SystemClock.uptimeMillis();
            if (now - healAt > 400) heals = 0;
            if (heals < 3 && now - healAt > 60) {
                healAt = now;
                heals++;
                float em = lineH / laidH;
                if (em >= 1f && em < 2f && Math.abs(em - effMult) > 0.005f) effMult = em;
                final Editable ed = (Editable) text;
                final DiagramSpan self = this;
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> refresh(ed, self));
            }
        }
        drawX = x;
        drawTop = top;
        drawW = w;
        drawH = h;
        RectF box = new RectF(x + dens, top + 4 * dens, x + w - dens, top + h - 4 * dens);
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setColor(Pal.dark ? 0x14FFFFFF : 0x0A000000);
        c.drawRoundRect(box, 12 * dens, 12 * dens, bg);
        bg.setStyle(Paint.Style.STROKE);
        bg.setStrokeWidth(dens);
        bg.setColor(Pal.chip);
        c.drawRoundRect(box, 12 * dens, 12 * dens, bg);
        if (d.nodes.isEmpty()) {
            android.text.TextPaint tp = new android.text.TextPaint(Paint.ANTI_ALIAS_FLAG);
            tp.setColor(Pal.muted);
            tp.setTextSize(14 * dens);
            String s = "Empty diagram · tap to draw";
            c.drawText(s, box.centerX() - tp.measureText(s) / 2f, box.centerY() + 5 * dens, tp);
            return;
        }
        float[] bd = band();
        float s = scale();
        c.save();
        c.clipRect(box);
        d.draw(c, s, x, top + 6 * dens - bd[0] * s, Pal.ink, Pal.dark, null, null, false);
        c.restore();
    }

    static void refresh(Editable e, DiagramSpan t) {
        int a = e.getSpanStart(t), b = e.getSpanEnd(t);
        if (a < 0) return;
        e.removeSpan(t);
        e.setSpan(t, a, b, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        RichText.layoutDirty = true;
    }

    /** Puts a new diagram on its own line at pos; returns where its character is. */
    static int insert(Editable e, int pos, DiagramSpan span) {
        int at = RichText.lineStart(e, pos);
        boolean lineHasText = RichText.lineEnd(e, pos) > at;
        int ins = lineHasText ? RichText.lineEnd(e, pos) : at;
        String sb = (lineHasText ? "\n" : "") + RichText.TABLE_CHAR + "\n";
        e.insert(ins, sb);
        int s0 = ins + (lineHasText ? 1 : 0);
        e.setSpan(span, s0, s0 + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return s0;
    }
}
