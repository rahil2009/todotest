package app.privatetodo;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.text.Editable;
import android.text.Layout;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * The note's own text selection, built without Android's: long-press or double-tap selects a word, the two
 * handles drag the ends, a finger resting near the top or bottom of what is visible scrolls the page (slowly at
 * first, faster the longer it stays), and a small bar offers Cut, Copy, Paste and Select all.
 *
 * Android's version could not do this in a note: its handles live in separate windows (so the note never saw the
 * finger), they only scroll the text field itself, and every selection change made the text field scroll the page
 * on its own, which fought the drag and made it jump. Here Android's long-press and double-tap selection are
 * switched off, the page only scrolls when this class scrolls it, and the selection is just text highlighted.
 */
final class SelectKit {
    private final RichEditText v;
    private final float d;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Rect vis = new Rect();
    private final int[] loc = new int[2];
    private final int slop;
    private final long longPress;
    private final long doubleTap;

    // ---- gesture state
    private static final int NONE = 0;
    private static final int HANDLE = 1;  // dragging a handle
    private static final int WORDS = 2;   // finger still down after a long-press: extending from the word
    private int mode = NONE;
    private boolean dragEnd;
    private int anchorA;
    private int anchorB;
    private float downX;
    private float downY;
    private long downAt;
    private long lastTapUp;
    private float lastTapX;
    private float lastTapY;
    private boolean swallow; // this gesture is ours to the end (double-tap)
    private float rawX;
    private float rawY;
    private float grabDy;
    private Runnable pendingLong;
    private boolean pageScroll;
    private float lastRawY;
    private android.view.VelocityTracker vt;

    // ---- edge scroll
    private int dir;
    private float depth;
    private long since;
    private long lastFrame;
    private float carry;

    // ---- the bar
    private LinearLayout bar;
    private boolean barMoved; // the user dragged the bar: it stays where they put it
    private long quietUntil; // our own edits must not make the text field scroll the page

    SelectKit(RichEditText v) {
        this.v = v;
        this.d = v.getResources().getDisplayMetrics().density;
        ViewConfiguration vc = ViewConfiguration.get(v.getContext());
        slop = vc.getScaledTouchSlop();
        longPress = ViewConfiguration.getLongPressTimeout();
        doubleTap = ViewConfiguration.getDoubleTapTimeout();
        v.setLongClickable(false); // Android's long-press selection is off: ours does it
        v.setCustomSelectionActionModeCallback(new android.view.ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(android.view.ActionMode m, android.view.Menu menu) {
                return false; // never Android's selection bar
            }

            @Override
            public boolean onPrepareActionMode(android.view.ActionMode m, android.view.Menu menu) {
                return false;
            }

            @Override
            public boolean onActionItemClicked(android.view.ActionMode m, android.view.MenuItem item) {
                return false;
            }

            @Override
            public void onDestroyActionMode(android.view.ActionMode m) {
            }
        });
        if (android.os.Build.VERSION.SDK_INT >= 29) { // in case Android still makes handles: invisible ones
            GradientDrawable none = new GradientDrawable();
            none.setColor(0);
            none.setSize(1, 1);
            v.setTextSelectHandleLeft(none);
            v.setTextSelectHandleRight(none.getConstantState().newDrawable().mutate());
        }
        v.getViewTreeObserver().addOnScrollChangedListener(new ViewTreeObserver.OnScrollChangedListener() {
            @Override
            public void onScrollChanged() {
                if (bar != null && bar.getVisibility() == View.VISIBLE && mode == NONE) placeBar();
            }
        });
    }

    /** True when this touch belongs to the selection (a handle, or a drag already under way): checked before anything else. */
    boolean owns(MotionEvent e) {
        if (mode != NONE || swallow) return true;
        return e.getActionMasked() == MotionEvent.ACTION_DOWN && v.hasSelection() && grabbable(e.getX(), e.getY());
    }

    private boolean grabbable(float x, float y) {
        int a = Math.min(v.getSelectionStart(), v.getSelectionEnd());
        int b = Math.max(v.getSelectionStart(), v.getSelectionEnd());
        float[] pa = point(a);
        float[] pb = point(b);
        if (pa == null || pb == null) return false;
        float r = r();
        return Math.min(Math.hypot(x - (pa[0] - r), y - (pa[1] + r)), Math.hypot(x - (pb[0] + r), y - (pb[1] + r))) <= 42 * d;
    }

    /** True while a selection made here is showing. */
    boolean active() {
        return v.hasSelection();
    }

    /** The text field asks this before it scrolls itself to the cursor; while selecting, only we scroll. */
    boolean holdScroll() {
        return mode != NONE || pageScroll || SystemClock.uptimeMillis() < quietUntil;
    }

    private void quiet() {
        quietUntil = SystemClock.uptimeMillis() + 500;
    }

    // ------------------------------------------------------------------ geometry

    private float r() {
        return 10 * d;
    }

    /** x of a selection edge and the bottom of its line's text (view coordinates). */
    private float[] point(int offset) {
        Layout l = v.getLayout();
        if (l == null) return null;
        int line = l.getLineForOffset(Math.max(0, Math.min(v.length(), offset)));
        float x = v.getTotalPaddingLeft() + l.getPrimaryHorizontal(offset);
        float y = v.getTotalPaddingTop() + l.getLineBaseline(line) + v.getPaint().descent();
        return new float[]{x, y};
    }

    private float lineTopY(int offset) {
        Layout l = v.getLayout();
        if (l == null) return 0;
        int line = l.getLineForOffset(Math.max(0, Math.min(v.length(), offset)));
        return v.getTotalPaddingTop() + l.getLineBaseline(line) + v.getPaint().ascent();
    }

    private int offsetAt(float viewX, float viewY) {
        return v.getOffsetForPosition(viewX, Math.max(0, viewY));
    }

    // ------------------------------------------------------------------ drawing

    void draw(Canvas c) {
        if (!v.hasSelection()) return;
        int a = Math.min(v.getSelectionStart(), v.getSelectionEnd());
        int b = Math.max(v.getSelectionStart(), v.getSelectionEnd());
        float[] pa = point(a);
        float[] pb = point(b);
        if (pa == null || pb == null) return;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Pal.accent);
        drop(c, pa[0], pa[1], true);
        drop(c, pb[0], pb[1], false);
    }

    private void drop(Canvas c, float x, float y, boolean left) {
        float r = r();
        float cx = left ? x - r : x + r;
        float cy = y + r;
        path.reset();
        path.addCircle(cx, cy, r, Path.Direction.CW);
        if (left) path.addRect(cx, y, x, cy, Path.Direction.CW);
        else path.addRect(x, y, cx, cy, Path.Direction.CW);
        c.drawPath(path, paint);
    }

    // ------------------------------------------------------------------ touch

    /**
     * First look at every touch of the text field. Returns true when the touch is ours (handles, long-press
     * drag, double-tap); false lets the field handle it (typing position, to-dos, tables, links ...).
     */
    boolean onTouch(MotionEvent e) {
        int act = e.getActionMasked();
        if (act == MotionEvent.ACTION_DOWN) {
            cancelLong();
            swallow = false;
            downX = e.getX();
            downY = e.getY();
            downAt = e.getEventTime();
            rawX = e.getRawX();
            rawY = e.getRawY();
            mode = NONE;
            if (v.hasSelection() && grabHandle(e.getX(), e.getY())) {
                mode = HANDLE;
                hideBar();
                disallow();
                v.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
                return true;
            }
            // a second quick tap close to the first: select the word (and keep Android from doing it)
            if (e.getEventTime() - lastTapUp < doubleTap && Math.hypot(e.getX() - lastTapX, e.getY() - lastTapY) < 48 * d) {
                swallow = true;
                lastTapUp = 0;
                selectWordAt(e.getX(), e.getY());
                return true;
            }
            return false;
        }
        if (swallow) {
            if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) swallow = false;
            return true;
        }
        if (mode == HANDLE || mode == WORDS) {
            if (act == MotionEvent.ACTION_MOVE) {
                rawX = e.getRawX();
                rawY = e.getRawY();
                follow();
                edge();
            } else if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) {
                endDrag();
            }
            return true;
        }
        if (pageScroll) { // we are scrolling the page ourselves (a selection is showing)
            if (act == MotionEvent.ACTION_MOVE) {
                if (vt != null) vt.addMovement(e);
                ScrollView sv = page();
                float dy = lastRawY - e.getRawY();
                lastRawY = e.getRawY();
                if (sv != null) sv.scrollBy(0, Math.round(dy));
            } else {
                if (vt != null) {
                    vt.addMovement(e);
                    vt.computeCurrentVelocity(1000, ViewConfiguration.get(v.getContext()).getScaledMaximumFlingVelocity());
                    ScrollView sv = page();
                    if (act == MotionEvent.ACTION_UP && sv != null) sv.fling(-(int) vt.getYVelocity());
                    vt.recycle();
                    vt = null;
                }
                pageScroll = false;
                if (bar != null && bar.getVisibility() == View.VISIBLE) placeBar();
            }
            return true;
        }
        if (act == MotionEvent.ACTION_MOVE) {
            float mdx = Math.abs(e.getX() - downX);
            float mdy = Math.abs(e.getY() - downY);
            if (Math.hypot(mdx, mdy) > slop) cancelLong();
            // With a selection showing, a vertical drag must scroll the page. If the page has not taken it over
            // by now, do it here.
            if (v.hasSelection() && mdy > slop * 1.5f && mdy > mdx && page() != null) {
                cancelLong();
                MotionEvent cancel = MotionEvent.obtain(e);
                cancel.setAction(MotionEvent.ACTION_CANCEL);
                v.superTouch(cancel);
                cancel.recycle();
                pageScroll = true;
                lastRawY = e.getRawY();
                vt = android.view.VelocityTracker.obtain();
                vt.addMovement(e);
                disallow();
                return true;
            }
        } else if (act == MotionEvent.ACTION_UP) {
            cancelLong();
            if (e.getEventTime() - downAt < longPress && Math.hypot(e.getX() - downX, e.getY() - downY) < slop) {
                lastTapUp = e.getEventTime();
                lastTapX = e.getX();
                lastTapY = e.getY();
                if (v.hasSelection()) hideBar(); // a tap ends the selection: the field puts the cursor at the tap
            }
        } else if (act == MotionEvent.ACTION_CANCEL) {
            cancelLong();
        }
        return false;
    }

    /** Called by the text field for a touch that reached its plain-text path: arm the long-press there. */
    void armLongPress(MotionEvent e) {
        if (e.getActionMasked() != MotionEvent.ACTION_DOWN) return;
        cancelLong();
        final float x = e.getX();
        final float y = e.getY();
        pendingLong = () -> {
            pendingLong = null;
            if (!selectWordAt(x, y)) {
                showBar(true); // nothing to select here: offer Paste / Select all
                return;
            }
            mode = WORDS;
            anchorA = Math.min(v.getSelectionStart(), v.getSelectionEnd());
            anchorB = Math.max(v.getSelectionStart(), v.getSelectionEnd());
            grabDy = 0;
            disallow();
            // the field saw a DOWN: tell it the gesture is gone, so it does not also move the cursor on UP
            MotionEvent cancel = MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(),
                    MotionEvent.ACTION_CANCEL, x, y, 0);
            v.superTouch(cancel);
            cancel.recycle();
        };
        v.postDelayed(pendingLong, longPress);
    }

    private void cancelLong() {
        if (pendingLong != null) {
            v.removeCallbacks(pendingLong);
            pendingLong = null;
        }
    }

    private void disallow() {
        ViewParent p = v.getParent();
        if (p != null) p.requestDisallowInterceptTouchEvent(true);
    }

    private boolean grabHandle(float x, float y) {
        int a = Math.min(v.getSelectionStart(), v.getSelectionEnd());
        int b = Math.max(v.getSelectionStart(), v.getSelectionEnd());
        float[] pa = point(a);
        float[] pb = point(b);
        if (pa == null || pb == null) return false;
        float r = r();
        float da = (float) Math.hypot(x - (pa[0] - r), y - (pa[1] + r));
        float db = (float) Math.hypot(x - (pb[0] + r), y - (pb[1] + r));
        if (Math.min(da, db) > 42 * d) return false;
        dragEnd = db <= da;
        // keep the gap between finger and text point, so the selection edge does not leap to the finger
        grabDy = y - ((dragEnd ? pb[1] : pa[1]) - 2 * d);
        return true;
    }

    /** Selects the word under (x, y). False when there is no word there (empty line, spaces). */
    private boolean selectWordAt(float x, float y) {
        if (v.getLayout() == null || v.length() == 0) return false;
        int off = offsetAt(x, y);
        CharSequence t = v.getText();
        int n = t.length();
        int a = Math.max(0, Math.min(n, off));
        int b = a;
        while (a > 0 && word(t.charAt(a - 1))) a--;
        while (b < n && word(t.charAt(b))) b++;
        if (a == b) { // between words: take the character under the finger if it is visible text
            if (off < n && !Character.isWhitespace(t.charAt(off)) && t.charAt(off) != '￼') b = off + 1;
            else return false;
            a = off;
        }
        if (!v.isFocused()) {
            v.clearTapGuard();
            v.requestFocus();
        }
        quiet();
        v.setSelection(a, b);
        v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
        v.invalidate();
        showBar(false);
        return true;
    }

    private static boolean word(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '\'' || c == '’';
    }

    /** Moves the dragged edge to the text under the finger. */
    private void follow() {
        if (v.getLayout() == null) return;
        v.getLocationOnScreen(loc);
        float x = rawX - loc[0];
        float y = rawY - loc[1] - grabDy;
        int off = offsetAt(x, y);
        if (off < 0) return;
        int n = v.length();
        int a = Math.min(v.getSelectionStart(), v.getSelectionEnd());
        int b = Math.max(v.getSelectionStart(), v.getSelectionEnd());
        int na = a;
        int nb = b;
        if (mode == WORDS) { // grow from the long-pressed word in either direction
            if (off >= anchorB) {
                na = anchorA;
                nb = off;
            } else if (off <= anchorA) {
                na = off;
                nb = anchorB;
            } else {
                na = anchorA;
                nb = anchorB;
            }
        } else if (dragEnd) {
            if (off <= a) { // crossed the other handle: they swap roles
                dragEnd = false;
                na = Math.min(off, a - 1);
                nb = a;
            } else {
                nb = off;
            }
        } else {
            if (off >= b) {
                dragEnd = true;
                na = b;
                nb = Math.max(off, b + 1);
            } else {
                na = off;
            }
        }
        na = Math.max(0, Math.min(n, na));
        nb = Math.max(0, Math.min(n, nb));
        if (na == nb || (na == a && nb == b)) return;
        quiet();
        v.setSelection(na, nb);
        v.performHapticFeedback(android.view.HapticFeedbackConstants.TEXT_HANDLE_MOVE);
        v.invalidate();
    }

    private void endDrag() {
        mode = NONE;
        dir = 0;
        v.removeCallbacks(scroller);
        if (v.hasSelection()) showBar(false);
        v.invalidate();
    }

    void clear() {
        hideBar();
        mode = NONE;
        dir = 0;
        v.removeCallbacks(scroller);
        if (v.hasSelection()) {
            quiet();
            int e = Math.max(v.getSelectionStart(), v.getSelectionEnd());
            v.setSelection(e);
        }
        v.invalidate();
    }

    // ------------------------------------------------------------------ edge scroll

    private ScrollView page() {
        ViewParent p = v.getParent();
        while (p != null && !(p instanceof ScrollView)) p = p.getParent();
        return (ScrollView) p;
    }

    /**
     * Works out, from where the finger is right now, which way to scroll and how far into the edge zone it is.
     * 0..1 inside the zone, up to 2.2 when the finger is out past the edge of the visible area.
     */
    private void measureEdge() {
        ScrollView sv = page();
        if (sv == null) {
            dir = 0;
            return;
        }
        sv.getLocationOnScreen(loc);
        v.getWindowVisibleDisplayFrame(vis); // ends at the top of the keyboard when it is up
        float top = Math.max(loc[1], vis.top);
        float bottom = Math.min(loc[1] + sv.getHeight(), vis.bottom);
        float zone = 72 * d;
        if (rawY > bottom - zone) {
            dir = 1;
            depth = Math.min(2.2f, (rawY - (bottom - zone)) / zone);
        } else if (rawY < top + zone) {
            dir = -1;
            depth = Math.min(2.2f, ((top + zone) - rawY) / zone);
        } else {
            dir = 0;
            depth = 0;
        }
    }

    private void edge() {
        int was = dir;
        measureEdge();
        if (dir != was) {
            since = SystemClock.uptimeMillis();
            lastFrame = since;
            carry = 0;
            v.removeCallbacks(scroller);
            if (dir != 0) v.postOnAnimation(scroller);
        }
    }

    /**
     * px per second. Two things multiply: how far the finger is into the edge zone (a hair in = a crawl, at the
     * edge = quick, past the edge = faster still) and how long it has been there (1x, easing to 2.4x over 2.5 s).
     */
    private float speed() {
        float t = (SystemClock.uptimeMillis() - since) / 1000f;
        float k = Math.min(1f, t / 2.5f);
        k = k * k * (3 - 2 * k);
        float base = Math.min(1100 * d, 40 * d + 480 * d * depth * depth);
        return base * (1f + 1.2f * k);
    }

    private final Runnable scroller = new Runnable() {
        @Override
        public void run() {
            if ((mode == NONE) || dir == 0) return;
            ScrollView sv = page();
            if (sv == null) return;
            measureEdge(); // the finger may be resting: re-read where it is every frame
            if (dir == 0) return;
            long now = SystemClock.uptimeMillis();
            float dt = Math.min(0.034f, (now - lastFrame) / 1000f);
            lastFrame = now;
            carry += speed() * dt * dir;
            int step = (int) carry;
            carry -= step;
            if (step != 0) {
                int before = sv.getScrollY();
                sv.scrollBy(0, step);
                if (sv.getScrollY() != before) follow();
            }
            v.postOnAnimation(this);
        }
    };

    // ------------------------------------------------------------------ the bar

    private FrameLayout host() {
        View root = v.getRootView();
        View c = root == null ? null : root.findViewById(android.R.id.content);
        return c instanceof FrameLayout ? (FrameLayout) c : null;
    }

    private void showBar(boolean pasteOnly) {
        FrameLayout host = host();
        if (host == null) return;
        if (bar == null) {
            bar = new LinearLayout(v.getContext()) {
                float dx0, dy0, tx0, ty0;
                boolean moving;

                @Override
                public boolean onInterceptTouchEvent(MotionEvent e) {
                    if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                        dx0 = e.getRawX();
                        dy0 = e.getRawY();
                        tx0 = getTranslationX();
                        ty0 = getTranslationY();
                        moving = false;
                    } else if (e.getActionMasked() == MotionEvent.ACTION_MOVE && !moving
                            && Math.hypot(e.getRawX() - dx0, e.getRawY() - dy0) > slop) {
                        moving = true;
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    return moving;
                }

                @Override
                public boolean onTouchEvent(MotionEvent e) {
                    if (e.getActionMasked() == MotionEvent.ACTION_MOVE) {
                        FrameLayout h = host();
                        float nx = tx0 + e.getRawX() - dx0;
                        float ny = ty0 + e.getRawY() - dy0;
                        if (h != null) {
                            nx = Math.max(0, Math.min(h.getWidth() - getWidth(), nx));
                            ny = Math.max(0, Math.min(h.getHeight() - getHeight(), ny));
                        }
                        setTranslationX(nx);
                        setTranslationY(ny);
                        barMoved = true;
                    }
                    return true;
                }
            };
            bar.setOrientation(LinearLayout.HORIZONTAL);
            bar.setGravity(Gravity.CENTER_VERTICAL);
            int p = Math.round(4 * d);
            bar.setPadding(p, p, p, p);
            GradientDrawable g = new GradientDrawable();
            g.setColor(Pal.raised | 0xFF000000);
            g.setCornerRadius(22 * d);
            g.setStroke(Math.max(1, Math.round(d)), Pal.chip);
            bar.setBackground(g);
            bar.setElevation(8 * d);
        }
        if (bar.getParent() != host) {
            if (bar.getParent() != null) ((ViewGroup) bar.getParent()).removeView(bar);
            host.addView(bar, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START));
        }
        bar.removeAllViews();
        grip();
        boolean sel = v.hasSelection();
        if (sel) {
            button("Cut", () -> {
                copy();
                int a = Math.min(v.getSelectionStart(), v.getSelectionEnd());
                int b = Math.max(v.getSelectionStart(), v.getSelectionEnd());
                quiet();
                v.getText().delete(a, b);
                hideBar();
            });
            button("Copy", () -> {
                copy();
                clear();
            });
        }
        if (canPaste()) button("Paste", this::paste);
        if (v.length() > 0 && !(sel && allSelected())) button("Select all", () -> {
            if (!v.isFocused()) v.requestFocus();
            quiet();
            v.setSelection(0, v.length());
            v.invalidate();
            showBar(false);
        });
        if (bar.getChildCount() == 0) {
            hideBar();
            return;
        }
        bar.setVisibility(View.VISIBLE);
        Fonts.apply(bar);
        bar.post(this::placeBar);
    }

    /** Six small dots at the left end: the bar can be dragged from anywhere, this just says so. */
    private void grip() {
        TextView g = new TextView(v.getContext());
        g.setText("⋮⋮");
        g.setTextSize(13);
        g.setTextColor(Pal.muted);
        g.setGravity(Gravity.CENTER);
        g.setPadding(Math.round(8 * d), 0, Math.round(2 * d), 0);
        bar.addView(g, new LinearLayout.LayoutParams(-2, Math.round(40 * d)));
    }

    private boolean allSelected() {
        return Math.min(v.getSelectionStart(), v.getSelectionEnd()) == 0
                && Math.max(v.getSelectionStart(), v.getSelectionEnd()) == v.length();
    }

    private void button(String label, final Runnable act) {
        TextView t = new TextView(v.getContext());
        t.setText(label);
        t.setTextSize(14);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(Pal.ink);
        t.setGravity(Gravity.CENTER);
        int ph = Math.round(14 * d);
        t.setPadding(ph, 0, ph, 0);
        t.setMinHeight(Math.round(40 * d));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(18 * d);
        g.setColor(0);
        t.setBackground(g);
        t.setOnClickListener(x -> {
            x.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
            act.run();
        });
        bar.addView(t, new LinearLayout.LayoutParams(-2, Math.round(40 * d)));
    }

    /** Above the selection, or under it when there is no room above; always inside the screen. */
    private void placeBar() {
        FrameLayout host = host();
        if (bar == null || host == null || v.getLayout() == null || barMoved) return;
        int a = v.hasSelection() ? Math.min(v.getSelectionStart(), v.getSelectionEnd()) : v.getSelectionStart();
        int b = v.hasSelection() ? Math.max(v.getSelectionStart(), v.getSelectionEnd()) : a;
        if (a < 0) return;
        v.getLocationOnScreen(loc);
        int vx = loc[0];
        int vy = loc[1];
        host.getLocationOnScreen(loc);
        float[] pa = point(a);
        float[] pb = point(b);
        if (pa == null || pb == null) return;
        float topY = vy + lineTopY(a) - loc[1];
        float botY = vy + pb[1] + 2 * r() - loc[1];
        int bw = bar.getWidth();
        int bh = bar.getHeight();
        float y = topY - bh - 10 * d;
        v.getWindowVisibleDisplayFrame(vis);
        float minY = Math.max(0, vis.top - loc[1]) + 4 * d;
        float maxY = vis.bottom - loc[1] - bh - 4 * d;
        if (y < minY) y = botY + 8 * d;
        y = Math.max(minY, Math.min(maxY, y));
        float cx = vx - loc[0] + (pa[0] + (pa[1] == pb[1] ? pb[0] : pa[0])) / 2f;
        float x = cx - bw / 2f;
        x = Math.max(8 * d, Math.min(host.getWidth() - bw - 8 * d, x));
        bar.setTranslationX(x);
        bar.setTranslationY(y);
    }

    void hideBar() {
        barMoved = false;
        if (bar != null) bar.setVisibility(View.GONE);
    }

    /** Text changed (typing, undo): the bar no longer fits what it was shown for. */
    void onTextChanged() {
        if (mode == NONE) hideBar();
    }

    void onSelectionChanged(int s, int e) {
        if (s == e && mode == NONE && bar != null && bar.getVisibility() == View.VISIBLE
                && SystemClock.uptimeMillis() > quietUntil) {
            hideBar();
        }
        v.invalidate();
    }

    private ClipboardManager clip() {
        return (ClipboardManager) v.getContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
    }

    private void copy() {
        int a = Math.min(v.getSelectionStart(), v.getSelectionEnd());
        int b = Math.max(v.getSelectionStart(), v.getSelectionEnd());
        if (a < 0 || a == b) return;
        String s = v.getText().subSequence(a, b).toString().replace('￼', ' ');
        ClipboardManager cm = clip();
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("note", s));
    }

    private boolean canPaste() {
        ClipboardManager cm = clip();
        return cm != null && cm.hasPrimaryClip();
    }

    private void paste() {
        ClipboardManager cm = clip();
        if (cm == null || !cm.hasPrimaryClip() || cm.getPrimaryClip().getItemCount() == 0) return;
        CharSequence s = cm.getPrimaryClip().getItemAt(0).coerceToText(v.getContext());
        if (s == null) return;
        if (!v.isFocused()) v.requestFocus();
        int a = Math.max(0, Math.min(v.getSelectionStart(), v.getSelectionEnd()));
        int b = Math.max(0, Math.max(v.getSelectionStart(), v.getSelectionEnd()));
        Editable e = v.getText();
        quiet();
        e.replace(a, b, s.toString());
        v.setSelection(Math.min(e.length(), a + s.length()));
        hideBar();
    }
}
