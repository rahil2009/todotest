package app.privatetodo;

import android.animation.ValueAnimator;
import android.content.Context;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;

/**
 * The folder deck. Drag it down and it slides open under your finger, drag back up and it slides shut, all in one
 * touch, as far as you like in either direction. Let go and it finishes the way it was heading (past halfway, or a
 * quick flick). A tap on the handle slides it the same way. Sideways drags still go to the children (the chip row).
 */
final class SwipeBox extends LinearLayout {
    private final int slop;
    private final float d;
    private float downX, downY0, lastY, vy;
    private long lastT;
    private boolean dragging, prepared, wasOpen;
    private int hC, hE, closedH;
    private ValueAnimator anim;
    private Runnable expand, collapse;
    private java.util.function.BooleanSupplier isOpen;

    SwipeBox(Context c) {
        super(c);
        setOrientation(VERTICAL);
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
        d = c.getResources().getDisplayMetrics().density;
        setClipChildren(true);
    }

    void onSwipe(java.util.function.BooleanSupplier isOpen, Runnable expand, Runnable collapse) {
        this.isOpen = isOpen;
        this.expand = expand;
        this.collapse = collapse;
    }

    private boolean openNow() {
        return isOpen != null && isOpen.getAsBoolean();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        // remember how tall the closed deck is, so it can be slid back to exactly that
        if (!openNow() && !prepared && (anim == null || !anim.isRunning()) && b - t > 0) closedH = b - t;
    }

    /** The open deck's natural height at the current width. */
    private int measureOpen() {
        int w = getWidth() > 0 ? getWidth() : getResources().getDisplayMetrics().widthPixels;
        ViewGroup.LayoutParams lp = getLayoutParams();
        int old = lp.height;
        lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        lp.height = old;
        return getMeasuredHeight();
    }

    private void setH(int h) {
        ViewGroup.LayoutParams lp = getLayoutParams();
        if (lp.height == h) return;
        lp.height = h;
        setLayoutParams(lp);
    }

    /** Gets both heights ready and shows the open content, clipped to how far the deck is pulled. */
    private void prepare() {
        if (prepared) return;
        prepared = true;
        if (anim != null) anim.cancel();
        wasOpen = openNow();
        if (!wasOpen) {
            hC = getHeight() > 0 ? getHeight() : closedH;
            setH(hC);
            if (expand != null) expand.run(); // the open layout, still clipped to the closed height
            hE = measureOpen();
        } else {
            hE = getHeight();
            hC = closedH > 0 ? closedH : Math.round(64 * d);
            setH(hE);
        }
        if (hE <= hC) hE = hC + 1;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getRawX();
                downY0 = e.getRawY();
                lastY = downY0;
                lastT = e.getEventTime();
                vy = 0;
                dragging = false;
                break;
            case MotionEvent.ACTION_MOVE:
                float mx = Math.abs(e.getRawX() - downX), my = Math.abs(e.getRawY() - downY0);
                if (my > slop && my > mx * 1.2f) {
                    dragging = true;
                    downY0 = e.getRawY();
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                }
                break;
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int a = e.getActionMasked();
        float y = e.getRawY();
        if (a == MotionEvent.ACTION_MOVE) {
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
            prepare();
            long dt = Math.max(1, e.getEventTime() - lastT);
            vy = 0.7f * vy + 0.3f * ((y - lastY) / dt); // px per ms, smoothed
            lastY = y;
            lastT = e.getEventTime();
            float dy = y - downY0;
            int h = Math.round((wasOpen ? hE : hC) + dy);
            setH(Math.max(hC, Math.min(hE, h)));
        } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
            if (prepared) {
                int h = getLayoutParams().height;
                float frac = (h - hC) / (float) (hE - hC);
                boolean open;
                if (Math.abs(vy) > 0.6f) open = vy > 0; // a flick decides
                else open = frac > 0.5f;
                settle(open);
            }
            dragging = false;
        }
        return true;
    }

    /** Slides the deck the rest of the way, then lets it size itself again. */
    private void settle(final boolean open) {
        final int from = getLayoutParams().height;
        final int to = open ? hE : hC;
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofInt(from, to);
        anim.setDuration(Math.max(90, Math.min(240, Math.abs(to - from) * 2L / Math.max(1, Math.round(d)))));
        anim.setInterpolator(new DecelerateInterpolator(1.6f));
        anim.addUpdateListener(v -> setH((Integer) v.getAnimatedValue()));
        anim.addListener(new android.animation.AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationCancel(android.animation.Animator an) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(android.animation.Animator an) {
                prepared = false;
                if (cancelled) return;
                if (!open && collapse != null) collapse.run(); // back to the single row of chips
                setH(ViewGroup.LayoutParams.WRAP_CONTENT);
            }
        });
        anim.start();
        Hap.tick(this);
    }

    /** The handle was tapped: slide open or shut. */
    void toggle() {
        prepare();
        settle(!wasOpen);
    }
}
