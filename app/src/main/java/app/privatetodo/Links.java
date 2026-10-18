package app.privatetodo;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.method.MovementMethod;
import android.text.style.ReplacementSpan;
import android.util.Patterns;
import android.view.MotionEvent;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;

/**
 * Finds web links in task text and shows them as small tappable pills.
 * Opening one hands off to your browser. Taps anywhere else on the text pass through to the row.
 */
final class Links {
    private Links() {
    }

    /** All distinct web links in the given texts, in order of appearance. */
    static List<String> find(String... texts) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String text : texts) {
            if (text == null) continue;
            Matcher m = Patterns.WEB_URL.matcher(text);
            while (m.find()) out.add(m.group());
        }
        return new ArrayList<>(out);
    }

    /** "example.com/x" becomes "https://example.com/x". */
    static String normalize(String url) {
        return url.matches("(?i)^[a-z][a-z0-9+.-]*://.*") ? url : "https://" + url;
    }

    static void linkify(TextView t) {
        linkify(t, Pal.accent);
    }

    /** Keeps links inside an editable field drawn as pills while typing (display only; text is unchanged). */
    static void pillWatch(final android.widget.EditText et, final int color) {
        final boolean[] busy = {false};
        final Runnable apply = () -> {
            if (busy[0]) return;
            busy[0] = true;
            android.text.Editable ed = et.getText();
            for (Pill o : ed.getSpans(0, ed.length(), Pill.class)) ed.removeSpan(o);
            Matcher m = Patterns.WEB_URL.matcher(ed);
            while (m.find()) {
                ed.setSpan(new Pill(et.getContext(), m.group(), color), m.start(), m.end(),
                        android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            busy[0] = false;
        };
        et.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence c, int a, int b, int d) { }
            @Override public void onTextChanged(CharSequence c, int a, int b, int d) { }
            @Override public void afterTextChanged(android.text.Editable e) { apply.run(); }
        });
        apply.run();
    }

    /** A link drawn as a rounded pill; long ones are shortened with an ellipsis. */
    static final class Pill extends ReplacementSpan {
        final String url;
        private final int color;
        private final float maxPx;
        private final float d;

        Pill(Context c, String url, int color) {
            this.url = url;
            this.color = color;
            this.d = c.getResources().getDisplayMetrics().density;
            this.maxPx = c.getResources().getDisplayMetrics().widthPixels * 0.62f;
        }

        private CharSequence shown(Paint p, CharSequence text, int s, int e) {
            TextPaint tp = p instanceof TextPaint ? (TextPaint) p : new TextPaint(p);
            // plain String: a Spanned here would re-enter this span via the measuring code (stack overflow)
            String raw = text.toString().substring(s, e);
            return TextUtils.ellipsize(raw, tp, maxPx - 20 * d, TextUtils.TruncateAt.END);
        }

        @Override
        public int getSize(Paint p, CharSequence text, int s, int e, Paint.FontMetricsInt fm) {
            return Math.round(p.measureText(shown(p, text, s, e), 0, shown(p, text, s, e).length())
                    + 20 * d + 4 * d);
        }

        @Override
        public void draw(Canvas c, CharSequence text, int s, int e, float x, int top, int y,
                         int bottom, Paint p) {
            CharSequence str = shown(p, text, s, e);
            float w = p.measureText(str, 0, str.length());
            Paint.FontMetrics fm = p.getFontMetrics();
            RectF r = new RectF(x + 2 * d, y + fm.ascent - 1 * d, x + 2 * d + w + 20 * d, y + fm.descent + 1 * d);
            int old = p.getColor();
            Paint.Style os = p.getStyle();
            p.setStyle(Paint.Style.FILL);
            p.setColor((color & 0x00FFFFFF) | 0x26000000); // ~15% tint of the link colour
            c.drawRoundRect(r, r.height() / 2f, r.height() / 2f, p);
            p.setColor(color);
            c.drawText(str, 0, str.length(), x + 12 * d, y, p);
            p.setColor(old);
            p.setStyle(os);
        }
    }

    /**
     * Only claims a touch when it lands on a link pill. Everything else returns false, so the row
     * under the text still opens the editor (the old movement method swallowed those taps).
     */
    private static final class PillTouch implements MovementMethod {
        static final PillTouch INSTANCE = new PillTouch(null);
        private final Runnable after;

        PillTouch(Runnable after) {
            this.after = after;
        }

        private Pill hit(TextView tv, Spannable sp, MotionEvent ev) {
            Layout layout = tv.getLayout();
            if (layout == null) return null;
            int x = (int) ev.getX() - tv.getTotalPaddingLeft() + tv.getScrollX();
            int y = (int) ev.getY() - tv.getTotalPaddingTop() + tv.getScrollY();
            if (y < 0 || y >= layout.getHeight()) return null;
            int line = layout.getLineForVertical(y);
            if (x < layout.getLineLeft(line) || x > layout.getLineRight(line)) return null;
            int off = layout.getOffsetForHorizontal(line, x);
            Pill[] ps = sp.getSpans(off, off, Pill.class);
            if (ps.length == 0 && off > 0) ps = sp.getSpans(off - 1, off, Pill.class);
            return ps.length > 0 ? ps[0] : null;
        }

        @Override
        public boolean onTouchEvent(TextView tv, Spannable sp, MotionEvent ev) {
            int a = ev.getActionMasked();
            if (a != MotionEvent.ACTION_DOWN && a != MotionEvent.ACTION_UP) return false;
            Pill p = hit(tv, sp, ev);
            if (p == null) return false;
            if (a == MotionEvent.ACTION_UP) {
                open(tv.getContext(), p.url);
                if (after != null) after.run();
            }
            return true;
        }

        @Override public void initialize(TextView widget, Spannable text) { }
        @Override public boolean onKeyDown(TextView w, Spannable t, int k, android.view.KeyEvent e) { return false; }
        @Override public boolean onKeyUp(TextView w, Spannable t, int k, android.view.KeyEvent e) { return false; }
        @Override public boolean onKeyOther(TextView w, Spannable t, android.view.KeyEvent e) { return false; }
        @Override public void onTakeFocus(TextView w, Spannable t, int d) { }
        @Override public boolean onTrackballEvent(TextView w, Spannable t, MotionEvent e) { return false; }
        @Override public boolean onGenericMotionEvent(TextView w, Spannable t, MotionEvent e) { return false; }
        @Override public boolean canSelectArbitrarily() { return false; }
    }

    static void open(Context c, String url) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url.contains("@") && !url.contains("/")
                    && !url.startsWith("http") ? "mailto:" + url : normalize(url)));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
        } catch (ActivityNotFoundException ex) {
            Toast.makeText(c, "No app to open this link", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Replaces links in the text with tappable pills. The TextView stays non-clickable, and a
     * tap that misses every pill falls through to the parent (which opens the editor).
     */
    static void linkify(TextView t, int color) {
        linkify(t, color, null);
    }

    /** Like linkify, and {@code after} runs once a link has been opened (the alarm screen marks the task done). */
    static void linkify(TextView t, int color, Runnable after) {
        CharSequence src = t.getText();
        SpannableString out = new SpannableString(src);
        Matcher m = Patterns.WEB_URL.matcher(src);
        while (m.find()) {
            out.setSpan(new Pill(t.getContext(), m.group(), color), m.start(), m.end(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        // Always set (also when there are no links): recycled list rows must not keep an old handler.
        t.setMovementMethod(after == null ? PillTouch.INSTANCE : new PillTouch(after));
        t.setText(out, TextView.BufferType.SPANNABLE);
        t.setClickable(false);
        t.setFocusable(false);
        t.setLongClickable(false);
        t.setFocusableInTouchMode(false);
    }
}
