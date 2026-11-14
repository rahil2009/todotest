package app.privatetodo;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.LinearLayout;

/** A column that turns a vertical swipe on its children into expand (down) or collapse (up), and lets sideways swipes through. */
final class SwipeBox extends LinearLayout {
    private final int slop;
    private float dx, dy;
    private boolean fired;
    private Runnable down, up;

    SwipeBox(Context c) {
        super(c);
        setOrientation(VERTICAL);
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
    }

    void onSwipe(Runnable expand, Runnable collapse) {
        down = expand;
        up = collapse;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dx = e.getX();
                dy = e.getY();
                fired = false;
                break;
            case MotionEvent.ACTION_MOVE:
                float mx = Math.abs(e.getX() - dx), my = e.getY() - dy;
                if (Math.abs(my) > slop * 1.5f && Math.abs(my) > mx * 1.4f) return true;
                break;
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_MOVE && !fired) {
            float my = e.getY() - dy;
            if (Math.abs(my) > slop * 3f) {
                fired = true;
                Runnable r = my > 0 ? down : up;
                if (r != null) r.run();
            }
        }
        return true;
    }
}
