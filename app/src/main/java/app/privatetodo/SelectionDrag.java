package app.privatetodo;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.os.SystemClock;
import android.text.Layout;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import android.widget.ScrollView;

/**
 * The note's own selection handles (Android 10 and newer).
 *
 * Android draws its selection handles in separate little windows, so the note never sees the finger that drags
 * them, and they only scroll the text field itself, never the page around it. That is why a selection could not
 * run past the screen. Here the system handles are made invisible and these are drawn in their place: the note
 * owns the drag, moves the selection under the finger, and when the finger rests near the top or bottom edge of
 * what is visible (above the keyboard when it is up) the page scrolls: slowly at first, faster the longer you hold.
 */
final class SelectionDrag {
    private final RichEditText v;
    private final float d;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Rect vis = new Rect();
    private final int[] loc = new int[2];

    private boolean dragging;
    private boolean dragEnd;      // true: the end handle, false: the start handle
    private float rawX;
    private float rawY;
    private float grabDy;         // finger to text point, kept from where the handle was grabbed
    private int dir;              // -1 up, 0 still, 1 down
    private float depth;          // 0..1 into the edge zone
    private long since;
    private long lastFrame;
    private float carry;
    private Object magnifier;

    SelectionDrag(RichEditText v) {
        this.v = v;
        this.d = v.getResources().getDisplayMetrics().density;
        if (enabled()) {
            android.graphics.drawable.GradientDrawable none = new android.graphics.drawable.GradientDrawable();
            none.setColor(0);
            none.setSize(1, 1);
            v.setTextSelectHandleLeft(none);
            v.setTextSelectHandleRight(none.getConstantState().newDrawable().mutate());
        }
    }

    static boolean enabled() {
        return Build.VERSION.SDK_INT >= 29;
    }

    boolean dragging() {
        return dragging;
    }

    private float handleR() {
        return 10 * d;
    }

    /** Where a handle sits: x of the selection edge, and the bottom of its line (view coordinates). */
    private float[] point(int offset) {
        Layout l = v.getLayout();
        if (l == null) return null;
        int line = l.getLineForOffset(offset);
        float x = v.getTotalPaddingLeft() + l.getPrimaryHorizontal(offset);
        float y = v.getTotalPaddingTop() + l.getLineBaseline(line) + v.getPaint().descent();
        return new float[]{x, y};
    }

    // ------------------------------------------------------------------ drawing

    void draw(Canvas c) {
        if (!enabled() || !v.hasSelection() || !v.isFocused()) return;
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

    /** A teardrop: a circle hanging under the text, its pointed corner on the selection edge. */
    private void drop(Canvas c, float x, float y, boolean left) {
        float r = handleR();
        float cx = left ? x - r : x + r;
        float cy = y + r;
        path.reset();
        path.addCircle(cx, cy, r, Path.Direction.CW);
        if (left) path.addRect(cx, y, x, cy, Path.Direction.CW);
        else path.addRect(x, y, cx, cy, Path.Direction.CW);
        c.drawPath(path, paint);
    }

    // ------------------------------------------------------------------ touch

    /** Called first from the text view's onTouchEvent. Returns true while it owns the gesture. */
    boolean onTouch(MotionEvent e) {
        if (!enabled()) return false;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                dragging = false;
                if (!v.hasSelection() || v.getLayout() == null) return false;
                int a = Math.min(v.getSelectionStart(), v.getSelectionEnd());
                int b = Math.max(v.getSelectionStart(), v.getSelectionEnd());
                float[] pa = point(a);
                float[] pb = point(b);
                if (pa == null || pb == null) return false;
                float r = handleR();
                float x = e.getX();
                float y = e.getY();
                // the grab area is generous: the whole circle and a thumb's width around it
                float da = dist(x, y, pa[0] - r, pa[1] + r);
                float db = dist(x, y, pb[0] + r, pb[1] + r);
                float reach = 30 * d;
                if (Math.min(da, db) > reach) return false;
                dragging = true;
                dragEnd = db <= da;
                grabDy = y - (dragEnd ? pb[1] : pa[1]);
                rawX = e.getRawX();
                rawY = e.getRawY();
                dir = 0;
                ViewParent p = v.getParent();
                if (p != null) p.requestDisallowInterceptTouchEvent(true);
                v.performHapticFeedback(android.view.HapticFeedbackConstants.TEXT_HANDLE_MOVE);
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (!dragging) return false;
                rawX = e.getRawX();
                rawY = e.getRawY();
                follow();
                edge();
                showMagnifier();
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (!dragging) return false;
                stop();
                return true;
            }
            default:
                return dragging;
        }
    }

    private static float dist(float x, float y, float cx, float cy) {
        float dx = x - cx;
        float dy = y - cy;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    void stop() {
        dragging = false;
        dir = 0;
        v.removeCallbacks(scroller);
        hideMagnifier();
        v.invalidate();
    }

    /** Puts the dragged edge of the selection at the text under the finger (taking the grab offset off). */
    private void follow() {
        if (v.getLayout() == null) return;
        v.getLocationOnScreen(loc);
        float x = rawX - loc[0];
        float y = rawY - loc[1] - grabDy;
        int off = v.getOffsetForPosition(x, Math.max(0, y));
        if (off < 0) return;
        int a = Math.min(v.getSelectionStart(), v.getSelectionEnd());
        int b = Math.max(v.getSelectionStart(), v.getSelectionEnd());
        if (dragEnd) {
            if (off <= a) { // dragged past the other handle: they swap
                dragEnd = false;
                setSel(Math.min(off, a - 1), a);
            } else if (off != b) {
                setSel(a, off);
            }
        } else {
            if (off >= b) {
                dragEnd = true;
                setSel(b, Math.max(off, b + 1));
            } else if (off != a) {
                setSel(off, b);
            }
        }
    }

    private void setSel(int a, int b) {
        int n = v.length();
        a = Math.max(0, Math.min(n, a));
        b = Math.max(0, Math.min(n, b));
        if (a == b) return;
        v.setSelection(a, b);
        v.invalidate();
    }

    /** Is the finger near the top or bottom of what can be seen? Starts or stops the scrolling. */
    private void edge() {
        ScrollView sv = scroller();
        if (sv == null) return;
        sv.getLocationOnScreen(loc);
        v.getWindowVisibleDisplayFrame(vis); // shrinks to the top of the keyboard when it is up
        float top = Math.max(loc[1], vis.top);
        float bottom = Math.min(loc[1] + sv.getHeight(), vis.bottom);
        float zone = 60 * d;
        int nd = 0;
        float dep = 0;
        if (rawY > bottom - zone) {
            nd = 1;
            dep = Math.min(1f, (rawY - (bottom - zone)) / zone);
        } else if (rawY < top + zone) {
            nd = -1;
            dep = Math.min(1f, ((top + zone) - rawY) / zone);
        }
        depth = dep;
        if (nd != dir) { // entering, leaving or switching edges: start slow again
            dir = nd;
            since = SystemClock.uptimeMillis();
            lastFrame = since;
            carry = 0;
            v.removeCallbacks(scroller);
            if (dir != 0) v.postOnAnimation(scroller);
        }
    }

    private ScrollView scroller() {
        ViewParent p = v.getParent();
        while (p != null && !(p instanceof ScrollView)) p = p.getParent();
        return (ScrollView) p;
    }

    /**
     * Pixels per second: about two lines a second at first, building smoothly (ease-in) over three seconds
     * to about twenty-five lines a second; deeper into the edge zone is up to a third faster.
     */
    private float speed() {
        float t = (SystemClock.uptimeMillis() - since) / 1000f;
        float slow = 45 * d;
        float fast = 600 * d;
        float k = Math.min(1f, t / 3f);
        k = k * k * (3 - 2 * k); // smoothstep: no sudden kick at either end
        return (slow + (fast - slow) * k) * (0.75f + 0.25f * depth);
    }

    private final Runnable scroller = new Runnable() {
        @Override
        public void run() {
            if (!dragging || dir == 0) return;
            ScrollView sv = scroller();
            if (sv == null) return;
            long now = SystemClock.uptimeMillis();
            float dt = Math.min(0.034f, (now - lastFrame) / 1000f);
            lastFrame = now;
            carry += speed() * dt * dir;
            int step = (int) carry;
            carry -= step;
            if (step != 0) {
                int before = sv.getScrollY();
                sv.scrollBy(0, step);
                if (sv.getScrollY() != before) follow(); // the text moved under a still finger: extend
            }
            v.postOnAnimation(this);
        }
    };

    private void showMagnifier() {
        if (Build.VERSION.SDK_INT < 29) return;
        try {
            if (magnifier == null) magnifier = new android.widget.Magnifier.Builder(v).build();
            v.getLocationOnScreen(loc);
            ((android.widget.Magnifier) magnifier).show(rawX - loc[0], rawY - loc[1] - grabDy);
        } catch (RuntimeException ignored) {
            // no magnifier on this phone: the drag works without it
        }
    }

    private void hideMagnifier() {
        if (magnifier != null && Build.VERSION.SDK_INT >= 28) {
            try {
                ((android.widget.Magnifier) magnifier).dismiss();
            } catch (RuntimeException ignored) {
                // already gone
            }
        }
    }

    /** Unused: kept so callers can ask whether a view is the drag's (for future overlays). */
    boolean owns(View other) {
        return other == v;
    }
}
