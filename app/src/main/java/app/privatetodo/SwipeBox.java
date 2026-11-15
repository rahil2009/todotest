package app.privatetodo;

import android.content.Context;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.LinearLayout;

/**
 * A column that follows a vertical drag live: drag down and it opens as soon as you have moved far enough (keep your
 * finger down and it stays open), drag back up and it closes, without lifting. Sideways drags go through to the children.
 */
final class SwipeBox extends LinearLayout {
    private final int slop;
    private final float step;
    private float downX, refY;
    private boolean open;
    private Runnable expand, collapse;
    private java.util.function.BooleanSupplier isOpen;

    SwipeBox(Context c) {
        super(c);
        setOrientation(VERTICAL);
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
        step = 28 * c.getResources().getDisplayMetrics().density;
    }

    void onSwipe(java.util.function.BooleanSupplier isOpen, Runnable expand, Runnable collapse) {
        this.isOpen = isOpen;
        this.expand = expand;
        this.collapse = collapse;
    }

    private float downY0;
    private boolean dragging;

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getRawX();
                downY0 = e.getRawY();
                refY = downY0;
                dragging = false;
                open = isOpen != null && isOpen.getAsBoolean();
                break;
            case MotionEvent.ACTION_MOVE:
                float mx = Math.abs(e.getRawX() - downX), my = Math.abs(e.getRawY() - downY0);
                if (my > slop && my > mx * 1.2f) {
                    dragging = true;
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
        if (a == MotionEvent.ACTION_MOVE) {
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
            float y = e.getRawY();
            if (!open) {
                if (y < refY) refY = y; // follows the finger up while closed
                if (y - refY > step) {
                    open = true;
                    refY = y;
                    if (expand != null) expand.run();
                }
            } else {
                if (y > refY) refY = y; // follows the finger down while open
                if (refY - y > step) {
                    open = false;
                    refY = y;
                    if (collapse != null) collapse.run();
                }
            }
        }
        return true;
    }
}
