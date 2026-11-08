package app.privatetodo;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Path;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

/**
 * The light/dark switch without the flash: the old screen is photographed just before the change; the rebuilt
 * screen opens under that photo, and a circle grows from the middle, uncovering the new look as it spreads.
 */
final class ThemeFx {
    private static Bitmap shot;
    private static long shotAt;

    private ThemeFx() {
    }

    /** Call just before the theme changes (and before the activity is recreated). */
    static void capture(Activity a) {
        try {
            View decor = a.getWindow().getDecorView();
            int w = decor.getWidth();
            int h = decor.getHeight();
            if (w <= 0 || h <= 0) return;
            Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            decor.draw(new Canvas(b));
            shot = b;
            shotAt = SystemClock.uptimeMillis();
        } catch (RuntimeException | OutOfMemoryError e) {
            shot = null; // no animation, the screen just changes
        }
    }

    /** Call at the end of onCreate of a screen that can be recreated by a theme change. */
    static void play(Activity a) {
        // kept for a moment: Android may rebuild the screen twice for one theme change, and both should show it
        final Bitmap b = shot;
        if (b == null || SystemClock.uptimeMillis() - shotAt > 1800) {
            shot = null;
            return;
        }
        final ViewGroup decor = (ViewGroup) a.getWindow().getDecorView();
        final float[] r = {0f};
        final Path hole = new Path();
        final float maxR = (float) Math.hypot(b.getWidth(), b.getHeight()) / 2f + 8;
        final View cover = new View(a) {
            @Override
            protected void onDraw(Canvas c) {
                hole.reset();
                hole.addCircle(getWidth() / 2f, getHeight() / 2f, r[0], Path.Direction.CW);
                c.save();
                c.clipOutPath(hole);
                c.drawBitmap(b, 0, 0, null);
                c.restore();
            }
        };
        cover.setClickable(true); // swallow touches while it plays
        decor.addView(cover, new FrameLayout.LayoutParams(-1, -1));
        ValueAnimator va = ValueAnimator.ofFloat(0f, maxR);
        va.setDuration(620);
        va.setStartDelay(60);
        va.setInterpolator(new DecelerateInterpolator(1.6f));
        va.addUpdateListener(x -> {
            r[0] = (float) x.getAnimatedValue();
            cover.invalidate();
        });
        va.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator an) {
                decor.removeView(cover);
            }
        });
        va.start();
    }
}
