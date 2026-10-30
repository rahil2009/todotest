package app.privatetodo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.style.ReplacementSpan;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.EditText;

/** EditText that reports cursor / selection moves, so the toolbar can show what is active. */
final class RichEditText extends EditText {
    interface SelectionListener {
        void onSelection(int start, int end);
    }

    interface CheckTapListener {
        void onCheckTap(int lineStart);
    }

    interface ZoomListener {
        void onZoom(float factor, boolean done);
    }

    private ZoomListener zoomListener;
    private final android.view.ScaleGestureDetector scaler;
    private boolean zooming;
    private SelectionListener listener;
    private CheckTapListener checkListener;
    private float downX;
    private float downY;

    /** Draws the to-do box as a circle: empty ring when open, filled with a tick when done. */
    private static final class CircleSpan extends ReplacementSpan {
        final boolean on;

        CircleSpan(boolean on) {
            this.on = on;
        }

        @Override
        public int getSize(Paint p, CharSequence t, int s, int e, Paint.FontMetricsInt fm) {
            return Math.round(p.getTextSize() * 1.1f);
        }

        @Override
        public void draw(Canvas c, CharSequence t, int s, int e, float x, int top, int y,
                         int bottom, Paint p) {
            if (RichText.hiddenAt(t, s, e)) return;
            float size = p.getTextSize();
            float r = size * 0.42f;
            float cx = x + size * 0.5f;
            float cy = (top + bottom) / 2f;
            int old = p.getColor();
            Paint.Style oldStyle = p.getStyle();
            float oldW = p.getStrokeWidth();
            p.setAntiAlias(true);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            if (on) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(Pal.accent);
                c.drawCircle(cx, cy, r, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(size * 0.11f);
                p.setColor(Pal.onAccent);
                Path k = new Path();
                k.moveTo(cx - r * 0.45f, cy + r * 0.02f);
                k.lineTo(cx - r * 0.1f, cy + r * 0.38f);
                k.lineTo(cx + r * 0.48f, cy - r * 0.34f);
                c.drawPath(k, p);
            } else {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(size * 0.09f);
                p.setColor(Pal.muted);
                c.drawCircle(cx, cy, r, p);
            }
            p.setColor(old);
            p.setStyle(oldStyle);
            p.setStrokeWidth(oldW);
        }
    }

    RichEditText(Context c) {
        super(c);
        scaler = new android.view.ScaleGestureDetector(c,
                new android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(android.view.ScaleGestureDetector d) {
                        if (zoomListener != null) zoomListener.onZoom(d.getScaleFactor(), false);
                        return true;
                    }

                    @Override
                    public void onScaleEnd(android.view.ScaleGestureDetector d) {
                        if (zoomListener != null) zoomListener.onZoom(1f, true);
                    }
                });
        addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int d) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int d) { }
            @Override public void afterTextChanged(Editable e) {
                syncCircles(e);
                RichText.healCounters(e);
                RichText.highlight(e);
            }
        });
        initCaret();
    }

    /** The text cursor: normal in the text, but trimmed to the text's own height inside a code block. */
    private static final class Caret extends android.graphics.drawable.Drawable {
        private final Paint paint = new Paint();
        private final int width;
        int top;
        int bottom;

        Caret(int width) {
            this.width = width;
        }

        void set(int color, int top, int bottom) {
            paint.setColor(color);
            this.top = top;
            this.bottom = bottom;
        }

        @Override
        public void draw(Canvas c) {
            android.graphics.Rect b = getBounds();
            float t = b.top + top;
            float bt = b.bottom - bottom;
            if (bt - t < 2) {
                t = b.top;
                bt = b.bottom;
            }
            c.drawRect(b.left, t, b.left + width, bt, paint);
        }

        @Override
        public int getIntrinsicWidth() {
            return width;
        }

        @Override
        public void setAlpha(int a) {
            paint.setAlpha(a);
        }

        @Override
        public void setColorFilter(android.graphics.ColorFilter cf) {
            paint.setColorFilter(cf);
        }

        @Override
        public int getOpacity() {
            return android.graphics.PixelFormat.TRANSLUCENT;
        }
    }

    private Caret caret;

    private void initCaret() {
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            caret = new Caret(Math.round(2 * getResources().getDisplayMetrics().density));
            caret.set(Pal.accent, 0, 0);
            setTextCursorDrawable(caret);
        }
    }

    /** Before drawing: in a code block the cursor must skip the header room above the first line and the room below the last. */
    private void updateCaret() {
        if (caret == null) return;
        android.text.Layout lay = getLayout();
        CharSequence t = getText();
        int top = 0;
        int bot = 0;
        int color = Pal.accent;
        if (lay != null && t instanceof android.text.Spanned && t.length() > 0) {
            int sel = Math.max(0, Math.min(t.length(), getSelectionStart()));
            int line = lay.getLineForOffset(sel);
            int ls = lay.getLineStart(line);
            int le = lay.getLineEnd(line);
            android.text.Spanned sp = (android.text.Spanned) t;
            for (RichText.Block b : sp.getSpans(ls, Math.max(ls, Math.min(t.length(), ls + 1)), RichText.Block.class)) {
                int a = sp.getSpanStart(b);
                int z = sp.getSpanEnd(b);
                if (a < 0 || ls < a || ls >= Math.max(z, a + 1)) continue;
                color = 0xFFE6E6E6;
                if (ls == a) top = RichText.Block.topPad();
                if (z <= le) bot = RichText.Block.botPad();
            }
            for (RichText.Callout b : sp.getSpans(ls, Math.max(ls, Math.min(t.length(), ls + 1)), RichText.Callout.class)) {
                int a2 = sp.getSpanStart(b);
                int z2 = sp.getSpanEnd(b);
                if (a2 < 0 || ls < a2 || ls >= Math.max(z2, a2 + 1)) continue;
                if (ls == a2) top = RichText.Callout.pad();
                if (z2 <= le) bot = RichText.Callout.pad();
            }
        }
        caret.set(color, top, bot);
    }

    /** Puts a CircleSpan over the box character of every to-do line (and removes stale ones). */
    private static void syncCircles(Editable e) {
        CircleSpan[] old = e.getSpans(0, e.length(), CircleSpan.class);
        int n = e.length();
        int i = 0;
        while (i < n) {
            int k = RichText.prefixAt(e, i);
            if (RichText.isCheck(k)) {
                boolean on = k == RichText.P_CHECK_ON;
                CircleSpan have = null;
                for (CircleSpan o : old) {
                    if (e.getSpanStart(o) == i && e.getSpanEnd(o) == i + 1 && o.on == on) have = o;
                }
                if (have == null) e.setSpan(new CircleSpan(on), i, i + 1, Editable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            int j = i;
            while (j < n && e.charAt(j) != '\n') j++;
            i = j + 1;
        }
        // drop spans that no longer sit on a matching box
        for (CircleSpan o : old) {
            int st = e.getSpanStart(o);
            if (st < 0) continue;
            int k = RichText.prefixAt(e, st);
            boolean ok = RichText.isCheck(k) && e.getSpanEnd(o) == st + 1 && o.on == (k == RichText.P_CHECK_ON);
            if (!ok) e.removeSpan(o);
        }
    }

    /** A tap on a table cell. left / top are the table's top-left corner in this view's own coordinates. */
    interface TableTapListener {
        void onTableTap(RichText.Tbl t, int row, int col, boolean onTick, float left, float top);
    }

    private TableTapListener tableListener;
    private RichText.Tbl tblDown;
    private int tblR;
    private int tblC;
    private boolean tblTick;
    private boolean tblMoved;
    private int tblZone = -1;

    /** For the table tap being delivered: -1 not a counter cell, else 0 minus, 1 the number, 2 plus. */
    int lastTableZone() {
        return tblZone;
    }
    private float tblLeft;
    private float tblTop;

    void setTableTapListener(TableTapListener l) {
        tableListener = l;
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        RichText.tableWidth = Math.max(0, w - getPaddingLeft() - getPaddingRight());
    }

    /** True when a finger that just went down at (x, y) is on a table cell; remembers which. */
    private boolean tableDown(float x, float y) {
        android.text.Layout l = getLayout();
        if (l == null || tableListener == null) return false;
        int line = l.getLineForVertical((int) (y - getTotalPaddingTop() + getScrollY()));
        int ls = l.getLineStart(line);
        RichText.Tbl t = RichText.tableOnLine(getText(), ls);
        if (t == null || RichText.hiddenAt(getText(), ls, ls + 1)) return false;
        float xl = x - getTotalPaddingLeft() + getScrollX();
        float lineTop = l.getLineTop(line);
        float yl = y - (lineTop + getTotalPaddingTop() - getScrollY());
        int[] rc = t.cellAt(xl, yl, getPaint());
        if (rc == null) return false;
        tblDown = t;
        tblR = rc[0];
        tblC = rc[1];
        tblTick = t.onTick(xl, yl, getPaint());
        tblZone = t.counterZone(xl, yl, getPaint());
        tblLeft = getTotalPaddingLeft() - getScrollX();
        tblTop = lineTop + getTotalPaddingTop() - getScrollY();
        tblMoved = false;
        return true;
    }

    void setZoomListener(ZoomListener l) {
        zoomListener = l;
    }

    void setSelectionListener(SelectionListener l) {
        listener = l;
    }

    void setCheckTapListener(CheckTapListener l) {
        checkListener = l;
    }

    interface LinkTapListener {
        void onLinkTap(int noteId);
    }

    private LinkTapListener linkListener;

    void setLinkTapListener(LinkTapListener l) {
        linkListener = l;
    }

    private RichText.Block hitBlock;
    private RichText.Counter hitCounter;
    private int hitButton; // header: 0 = Copy, 1 = Paste; counter: 0 = minus, 1 = the number, 2 = plus
    private boolean hitMoved;

    interface CounterListener {
        /** zone: 0 = minus, 1 = the number, 2 = plus. mult: 1 for a tap, more while the button is held. */
        void onCounter(RichText.Counter c, int zone, int mult);

        /** The same for a counter inside a table cell (zone 0 or 2 only while held). */
        void onTableCounter(RichText.Tbl t, int row, int col, int zone, int mult);
    }

    // ---- holding - or + : repeats, slow at first and then faster and faster
    private boolean holdFired;
    private int holdCount;
    private int holdStart = -1;
    private int holdZone;
    private boolean holdTable;
    private long holdHaptic;
    private final Runnable holdRun = new Runnable() {
        @Override
        public void run() {
            holdStep();
        }
    };

    private void holdBegin(int start, int zone, boolean table) {
        removeCallbacks(holdRun);
        holdFired = false;
        holdCount = 0;
        holdStart = start;
        holdZone = zone;
        holdTable = table;
        postDelayed(holdRun, 380);
    }

    private void holdEnd() {
        removeCallbacks(holdRun);
        holdStart = -1;
        holdTable = false;
    }

    private void holdStep() {
        if (counterL == null) return;
        int mult = holdCount < 40 ? 1 : holdCount < 80 ? 5 : 25;
        boolean ok = false;
        if (holdTable) {
            if (tblDown != null) {
                counterL.onTableCounter(tblDown, tblR, tblC, holdZone, mult);
                ok = true;
            }
        } else if (holdStart >= 0 && getText() instanceof android.text.Spanned) {
            android.text.Spanned sp = (android.text.Spanned) getText();
            int e = Math.min(sp.length(), holdStart + 1);
            for (RichText.Counter k : sp.getSpans(holdStart, e, RichText.Counter.class)) {
                if (sp.getSpanStart(k) == holdStart) {
                    counterL.onCounter(k, holdZone, mult);
                    ok = true;
                    break;
                }
            }
        }
        if (!ok) {
            holdEnd();
            return;
        }
        holdFired = true;
        holdCount++;
        long now = android.os.SystemClock.uptimeMillis();
        if (now - holdHaptic > 40) {
            holdHaptic = now;
            performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
        }
        long delay = Math.max(28L, Math.round(300 * Math.pow(0.9, holdCount)));
        postDelayed(holdRun, delay);
    }

    private CounterListener counterL;

    void setCounterListener(CounterListener l) {
        counterL = l;
    }

    /** The code block whose header Copy / Paste button is under this point, or null (sets hitButton). */
    private RichText.Block headButtonAt(float x, float y) {
        android.text.Layout l = getLayout();
        CharSequence t = getText();
        if (l == null || !(t instanceof android.text.Spanned) || t.length() == 0) return null;
        float yl = y - getTotalPaddingTop() + getScrollY();
        if (yl < 0 || yl > l.getHeight()) return null;
        int line = l.getLineForVertical((int) yl);
        int ls = l.getLineStart(line);
        if (yl > l.getLineTop(line) + RichText.Block.headPx()) return null;
        float right = getWidth() - getCompoundPaddingRight();
        float copyL = right - RichText.Block.copyW();
        float pasteR = copyL - RichText.Block.btnGap();
        float pasteL = pasteR - RichText.Block.pasteW();
        int which;
        if (x >= copyL - 4 && x <= right + 4) which = 0;
        else if (x >= pasteL - 4 && x <= pasteR + 4) which = 1;
        else return null;
        android.text.Spanned sp = (android.text.Spanned) t;
        for (RichText.Block b : sp.getSpans(ls, Math.min(t.length(), ls + 1), RichText.Block.class)) {
            if (sp.getSpanStart(b) == ls) {
                hitButton = which;
                return b;
            }
        }
        return null;
    }

    /** The counter under this point (sets hitButton to the part: minus, number or plus), or null. */
    private RichText.Counter counterAt(float x, float y) {
        android.text.Layout l = getLayout();
        CharSequence t = getText();
        if (l == null || !(t instanceof android.text.Spanned) || t.length() == 0) return null;
        float yl = y - getTotalPaddingTop() + getScrollY();
        if (yl < 0 || yl > l.getHeight()) return null;
        int line = l.getLineForVertical((int) yl);
        int ls = l.getLineStart(line);
        int le = l.getLineEnd(line);
        android.text.Spanned sp = (android.text.Spanned) t;
        for (RichText.Counter k : sp.getSpans(ls, le, RichText.Counter.class)) {
            int s = sp.getSpanStart(k);
            if (s < ls || l.getLineForOffset(s) != line || RichText.hiddenAt(t, s, s + 1)) continue;
            float sx = getTotalPaddingLeft() + l.getPrimaryHorizontal(s) - getScrollX();
            if (x < sx - 4 || x > sx + k.width + 4) continue;
            float zone = Math.min(k.width / 3f, getTextSize() * 1.9f);
            hitButton = x - sx < zone ? 0 : x - sx > k.width - zone ? 2 : 1;
            return k;
        }
        return null;
    }

    private void copyCode(RichText.Block b) {
        CharSequence t = getText();
        int a = ((android.text.Spanned) t).getSpanStart(b);
        int z = ((android.text.Spanned) t).getSpanEnd(b);
        if (a < 0 || z <= a) return;
        String code = t.subSequence(a, z).toString();
        while (code.endsWith("\n")) code = code.substring(0, code.length() - 1);
        android.content.ClipboardManager cm = (android.content.ClipboardManager)
                getContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(android.content.ClipData.newPlainText("code", code));
        RichText.copiedSpan = b;
        RichText.copiedUntil = android.os.SystemClock.uptimeMillis() + 1600;
        invalidate();
        postDelayed(this::invalidate, 1700);
    }

    /** Pastes the clipboard (as plain text) into the block: over the selection if it is inside, else at the end. */
    private void pasteCode(RichText.Block b) {
        android.content.ClipboardManager cm = (android.content.ClipboardManager)
                getContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
        CharSequence clip = null;
        if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip() != null && cm.getPrimaryClip().getItemCount() > 0) {
            clip = cm.getPrimaryClip().getItemAt(0).coerceToText(getContext());
        }
        if (clip == null || clip.length() == 0) {
            android.widget.Toast.makeText(getContext(), "Nothing to paste", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        Editable e = getText();
        int a = e.getSpanStart(b);
        int z = e.getSpanEnd(b);
        if (a < 0 || z <= a) return;
        int selS = getSelectionStart();
        int selE = getSelectionEnd();
        int s;
        int en;
        if (hasFocus() && selS >= a && selE <= z && selS <= selE) {
            s = selS;
            en = selE;
        } else {
            s = z;
            if (s > a && e.charAt(s - 1) == '\n') s--;
            en = s;
            boolean blank = e.subSequence(a, z).toString().trim().isEmpty();
            if (blank) {
                s = a;
                en = z;
                if (en > s && e.charAt(en - 1) == '\n') en--;
            }
        }
        String text = clip.toString().replace("\r\n", "\n");
        while (text.endsWith("\n")) text = text.substring(0, text.length() - 1);
        int flags = e.getSpanFlags(b);
        e.replace(s, en, text);
        int newEnd = Math.max(s + text.length(), e.getSpanEnd(b));
        e.setSpan(b, a, Math.max(newEnd, a + 1), flags);
        setSelection(Math.min(e.length(), s + text.length()));
        android.widget.Toast.makeText(getContext(), "Pasted", android.widget.Toast.LENGTH_SHORT).show();
    }

    /** The note id of the link under this point, or -1. */
    private int linkAt(float x, float y) {
        android.text.Layout l = getLayout();
        if (l == null) return -1;
        int off = getOffsetForPosition(x, y);
        int line = l.getLineForVertical((int) (y - getTotalPaddingTop() + getScrollY()));
        float hx = getTotalPaddingLeft() + l.getPrimaryHorizontal(off) - getScrollX();
        int ch = x < hx ? off - 1 : off;
        CharSequence t = getText();
        if (ch < 0 || ch >= t.length() || l.getLineForOffset(ch) != line) return -1;
        for (RichText.NoteLink k : getText().getSpans(ch, ch + 1, RichText.NoteLink.class)) return k.noteId;
        return -1;
    }

    // ---- finger (paint) mode: swipe over text to apply the chosen style

    interface PaintListener {
        /** A swipe began at this text offset. */
        void onStart(int offset);

        /** The swipe has covered [lo, hi) so far (it only ever grows). */
        void onStroke(int lo, int hi);

        void onEnd();

        /** A plain tap (no swipe) at this offset. */
        void onTap(int offset);
    }

    private PaintListener paintL;
    private int pState; // 0 idle, 1 finger down, 2 painting, 3 scrolling the page
    private int pStart;
    private int pLo;
    private int pHi;

    /** Non-null turns the field into a paint surface: touches paint instead of placing the cursor. */
    void setPaintListener(PaintListener l) {
        paintL = l;
        pState = 0;
        setCursorVisible(l == null);
    }

    private int offsetAt(float x, float y) {
        if (getLayout() == null) return 0;
        return Math.max(0, Math.min(length(), getOffsetForPosition(x, y)));
    }

    private boolean paintTouch(MotionEvent e) {
        int slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                pState = 1;
                downX = e.getX();
                downY = e.getY();
                pStart = offsetAt(e.getX(), e.getY());
                pLo = pStart;
                pHi = pStart;
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (pState == 1) {
                    float dx = Math.abs(e.getX() - downX);
                    float dy = Math.abs(e.getY() - downY);
                    if (dx < slop && dy < slop) return true;
                    if (dx >= dy) { // sideways: paint
                        pState = 2;
                        paintL.onStart(pStart);
                    } else { // up / down: let the page scroll
                        pState = 3;
                        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
                        return true;
                    }
                }
                if (pState == 2) {
                    int off = offsetAt(e.getX(), e.getY());
                    int lo = Math.min(pLo, off);
                    int hi = Math.max(pHi, off);
                    if (lo != pLo || hi != pHi) {
                        pLo = lo;
                        pHi = hi;
                        paintL.onStroke(pLo, pHi);
                    }
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (pState == 1) paintL.onTap(pStart);
                if (pState == 2) paintL.onEnd();
                pState = 0;
                return true;
            case MotionEvent.ACTION_CANCEL:
                if (pState == 2) paintL.onEnd();
                pState = 0;
                return true;
            default:
                return true;
        }
    }

    private int checkTouch = -1; // line start of the to-do circle the finger went down on, else -1

    /** Line start of the to-do circle under (x, y), or -1. Only the circle plus a thumb's margin counts. */
    private int checkLineAt(float x, float y) {
        if (getLayout() == null) return -1;
        int off = getOffsetForPosition(x, y);
        CharSequence t = getText();
        int ls = RichText.lineStart(t, off);
        int mk = RichText.prefixAt(t, ls);
        if (off > ls + 1 || !(RichText.isCheck(mk) || RichText.isToggle(mk))) return -1;
        float edge = getTotalPaddingLeft() + getLayout().getPrimaryHorizontal(ls + 1)
                + 10 * getResources().getDisplayMetrics().density;
        float from = getTotalPaddingLeft() + getLayout().getPrimaryHorizontal(ls) - 12 * getResources().getDisplayMetrics().density;
        return x <= edge && x >= from ? ls : -1;
    }

    /** A tap on a to-do box (the first characters of a "\u2610 " / "\u2611 " line) ticks it. */
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        scaler.onTouchEvent(e);
        if (scaler.isInProgress() || e.getPointerCount() > 1) {
            zooming = true;
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
            return true; // two fingers: pinch only, never moves the cursor or ticks a box
        }
        if (zooming) {
            if (e.getActionMasked() == MotionEvent.ACTION_UP
                    || e.getActionMasked() == MotionEvent.ACTION_CANCEL) zooming = false;
            return true;
        }
        if (paintL != null) return paintTouch(e);
        // The buttons in a code block's header (Paste, Copy) and the - / + of a counter are ours too:
        // a tap does the job with no cursor move and no keyboard.
        int actC = e.getActionMasked();
        if (actC == MotionEvent.ACTION_DOWN) {
            downX = e.getX();
            downY = e.getY();
            hitMoved = false;
            hitBlock = null;
            hitCounter = null;
            holdEnd();
            holdFired = false;
            hitBlock = headButtonAt(e.getX(), e.getY());
            if (hitBlock == null) hitCounter = counterAt(e.getX(), e.getY());
            if (hitCounter != null && hitButton != 1 && counterL != null) {
                holdBegin(((android.text.Spanned) getText()).getSpanStart(hitCounter), hitButton, false);
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
            }
            if (hitBlock != null || hitCounter != null) return true;
        } else if (hitBlock != null || hitCounter != null) {
            int slopC = ViewConfiguration.get(getContext()).getScaledTouchSlop();
            if (Math.abs(e.getX() - downX) >= slopC || Math.abs(e.getY() - downY) >= slopC) {
                hitMoved = true;
                if (!holdFired) holdEnd(); // a drag is a scroll, not a hold
            }
            if (actC == MotionEvent.ACTION_UP) {
                RichText.Block b = hitBlock;
                RichText.Counter k = hitCounter;
                hitBlock = null;
                hitCounter = null;
                boolean held = holdFired;
                holdEnd();
                if (!hitMoved && !held) {
                    performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                    if (b != null) {
                        if (hitButton == 0) copyCode(b);
                        else pasteCode(b);
                    } else if (counterL != null) {
                        counterL.onCounter(k, hitButton, 1);
                    }
                }
            } else if (actC == MotionEvent.ACTION_CANCEL) {
                hitBlock = null;
                hitCounter = null;
                holdEnd();
            }
            return true;
        }
        // A touch that starts on a table cell is ours too: no cursor, no keyboard from the note itself;
        // a tap hands the cell to the editor, which opens it for typing right there.
        if (tableListener != null) {
            int act0 = e.getActionMasked();
            if (act0 == MotionEvent.ACTION_DOWN) {
                downX = e.getX();
                downY = e.getY();
                tblDown = null;
                if (tableDown(e.getX(), e.getY())) {
                    if ((tblZone == 0 || tblZone == 2) && tblR > 0 && counterL != null
                            && RichText.Tbl.counterOf(tblDown.cells[tblR][tblC]) != null) {
                        holdBegin(-1, tblZone, true);
                        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    return true;
                }
            } else if (tblDown != null) {
                int slop0 = ViewConfiguration.get(getContext()).getScaledTouchSlop();
                if (Math.abs(e.getX() - downX) >= slop0 || Math.abs(e.getY() - downY) >= slop0) {
                    tblMoved = true;
                    if (!holdFired) holdEnd();
                }
                if (act0 == MotionEvent.ACTION_UP) {
                    RichText.Tbl t = tblDown;
                    boolean held = holdFired;
                    holdEnd();
                    tblDown = null;
                    if (!tblMoved && !held) tableListener.onTableTap(t, tblR, tblC, tblTick, tblLeft, tblTop);
                } else if (act0 == MotionEvent.ACTION_CANCEL) {
                    holdEnd();
                    tblDown = null;
                }
                return true;
            }
        }
        // A touch that starts on a to-do circle is ours alone: it ticks the box without focusing the
        // field, moving the cursor or raising the keyboard.
        if (checkListener != null) {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                downX = e.getX();
                downY = e.getY();
                checkTouch = checkLineAt(e.getX(), e.getY());
                if (checkTouch >= 0) return true;
            } else if (checkTouch != -1) { // -2 = the finger moved off: a scroll, not a tap
                int act = e.getActionMasked();
                int slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
                if (Math.abs(e.getX() - downX) >= slop || Math.abs(e.getY() - downY) >= slop) {
                    checkTouch = -2;
                }
                if (act == MotionEvent.ACTION_UP) {
                    int ls = checkTouch;
                    checkTouch = -1;
                    if (ls >= 0) checkListener.onCheckTap(ls);
                } else if (act == MotionEvent.ACTION_CANCEL) {
                    checkTouch = -1;
                }
                return true;
            }
        }
        boolean handled = super.onTouchEvent(e);
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
            downX = e.getX();
            downY = e.getY();
        } else if (e.getActionMasked() == MotionEvent.ACTION_UP && checkListener != null) {
            int slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
            if (Math.abs(e.getX() - downX) < slop && Math.abs(e.getY() - downY) < slop
                    && getLayout() != null) {
                int off = getOffsetForPosition(e.getX(), e.getY());
                CharSequence t = getText();
                int ls = RichText.lineStart(t, off);
                int mk = RichText.prefixAt(t, ls);
                if (off <= ls + 1 && (RichText.isCheck(mk) || RichText.isToggle(mk))) {
                    // only the circle (plus a thumb's margin) ticks; the text after it just edits
                    float edge = getTotalPaddingLeft() + getLayout().getPrimaryHorizontal(ls + 1)
                            + 10 * getResources().getDisplayMetrics().density;
                    if (e.getX() <= edge) checkListener.onCheckTap(ls);
                }
            }
        }
        if (e.getActionMasked() == MotionEvent.ACTION_UP && linkListener != null) { // a quick tap on a note link opens it
            int slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
            if (Math.abs(e.getX() - downX) < slop && Math.abs(e.getY() - downY) < slop
                    && e.getEventTime() - e.getDownTime() < ViewConfiguration.getLongPressTimeout()) {
                int id = linkAt(e.getX(), e.getY());
                if (id >= 0) linkListener.onLinkTap(id);
            }
        }
        return handled;
    }

    private float baseStroke;

    /** Outline (fraction of the text size) for the note's default font when it is a pen / marker font. */
    void setBaseStroke(float k) {
        baseStroke = k;
        invalidate();
    }

    @Override
    protected void onDraw(android.graphics.Canvas canvas) {
        android.graphics.Paint p = getPaint();
        if (baseStroke > 0) {
            p.setStyle(android.graphics.Paint.Style.FILL_AND_STROKE);
            p.setStrokeJoin(android.graphics.Paint.Join.ROUND);
            p.setStrokeWidth(getTextSize() * baseStroke);
        } else if (p.getStyle() != android.graphics.Paint.Style.FILL) {
            p.setStyle(android.graphics.Paint.Style.FILL);
        }
        if (RichText.layoutDirty) { // any span change that moved line heights: start the layout over (see rebuildLayout)
            RichText.layoutDirty = false;
            rebuildLayout();
        }
        drawPanels(canvas);
        updateCaret();
        super.onDraw(canvas);
    }

    private boolean rebuildQueued;

    /**
     * Throws the text layout away so it is measured and drawn again from scratch. Used after a style
     * changes the height of lines without any text changing (a table got a row, a paragraph became a
     * callout or code block, a toggle folded): Android keeps cached drawing of the lines below such a
     * change, so they stayed where they were, under the grown table, or blank.
     */
    void rebuildLayout() {
        if (rebuildQueued) return;
        rebuildQueued = true;
        postOnAnimation(() -> {
            rebuildQueued = false;
            float add = getLineSpacingExtra();
            float mult = getLineSpacingMultiplier();
            setLineSpacing(add + 0.01f, mult); // a changed value makes TextView drop its layout...
            setLineSpacing(add, mult);          // ...and the next measure builds a new one with the real values
            invalidate();
        });
    }

    private final android.graphics.Paint panelPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
    private final android.graphics.RectF panelRect = new android.graphics.RectF();

    /**
     * Callout and code panels are drawn here, before TextView clips to its text column and draws the text.
     * They reach PANEL_EXT past the column on each side, which a span's own drawBackground cannot do.
     */
    private void drawPanels(android.graphics.Canvas c) {
        android.text.Layout lay = getLayout();
        CharSequence t = getText();
        if (lay == null || !(t instanceof android.text.Spanned) || t.length() == 0) return;
        android.text.Spanned sp = (android.text.Spanned) t;
        android.graphics.Rect clip = c.getClipBounds();
        int pt = getTotalPaddingTop();
        int vTop = Math.max(0, clip.top - pt);
        int vBot = clip.bottom - pt;
        if (vBot < 0) return;
        int from = lay.getLineStart(lay.getLineForVertical(vTop));
        int to = lay.getLineEnd(lay.getLineForVertical(Math.max(vTop, vBot)));
        int ext = RichText.panelExt();
        float left = getCompoundPaddingLeft() - ext;
        float right = getWidth() - getCompoundPaddingRight() + ext;
        Object[][] groups = {sp.getSpans(from, to, RichText.Callout.class), sp.getSpans(from, to, RichText.Block.class)};
        for (Object[] group : groups) {
            for (Object span : group) {
                int s = sp.getSpanStart(span);
                int e = sp.getSpanEnd(span);
                if (s < 0 || e <= s) continue;
                int l0 = lay.getLineForOffset(s);
                int l1 = lay.getLineForOffset(Math.max(s, e - 1));
                panelRect.set(left, pt + lay.getLineTop(l0), right, pt + lay.getLineBottom(l1));
                RichText.paintPanel(c, panelPaint, span, panelRect);
            }
        }
    }

    @Override
    protected void onSelectionChanged(int selStart, int selEnd) {
        super.onSelectionChanged(selStart, selEnd);
        if (listener != null) listener.onSelection(selStart, selEnd);
    }
}
