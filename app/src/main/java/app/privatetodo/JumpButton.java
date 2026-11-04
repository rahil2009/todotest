package app.privatetodo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.ScrollView;

/**
 * A small round button that floats over a long note while you scroll: an arrow down jumps to the end, an arrow up
 * jumps to the start. It shows only while your finger moves the page (not when the editor scrolls by itself),
 * points the way you are heading, flips to "up" near the end, and fades out 1.6 s after the page stops.
 */
final class JumpButton extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final float d;
    private boolean up;
    private long lastTouch;
    private ScrollView sv;
    private final Runnable hide = () -> fade(false);

    JumpButton(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeWidth(2.4f * d);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(Pal.raised | 0xFF000000);
        g.setStroke(Math.round(d), Pal.chip);
        setBackground(g);
        setElevation(4 * d);
        setAlpha(0f);
        setVisibility(GONE);
        setClickable(true);
        setOnClickListener(v -> {
            Hap.tick(v);
            if (sv == null) return;
            if (up) sv.smoothScrollTo(0, 0);
            else sv.smoothScrollTo(0, Math.max(0, sv.getChildAt(0).getHeight() - sv.getHeight()));
            removeCallbacks(hide);
            postDelayed(hide, 900);
        });
    }

    /** Watches this scroll view. Call once after it is built. */
    void attach(ScrollView s) {
        sv = s;
        s.setOnScrollChangeListener((v, x, y, ox, oy) -> onScrolled(y, oy));
    }

    /** Fed every touch of the screen by the activity, so a finger on the text counts too. */
    void touched() {
        lastTouch = System.currentTimeMillis();
    }

    private void onScrolled(int y, int oy) {
        if (sv == null || sv.getChildAt(0) == null) return;
        if (!NoteOpen.jumpButton(getContext())) return;
        if (System.currentTimeMillis() - lastTouch > 3000) return; // the editor moved itself: stay hidden
        int content = sv.getChildAt(0).getHeight();
        int view = sv.getHeight();
        if (content < view * 1.5f) return; // short note: nothing to jump over
        int max = content - view;
        boolean nearEnd = y > max - view / 3;
        boolean nearStart = y < view / 3;
        boolean wantUp = nearEnd || (!nearStart && y < oy);
        if (wantUp != up) {
            up = wantUp;
            setContentDescription(up ? "Jump to the top" : "Jump to the end");
            invalidate();
        }
        fade(true);
        removeCallbacks(hide);
        postDelayed(hide, 1800);
    }

    private void fade(boolean show) {
        if (show) { // at once, on the first pixel of scroll: a late arrow is no use
            animate().cancel();
            setVisibility(VISIBLE);
            setAlpha(1f);
        } else {
            animate().cancel();
            animate().alpha(0f).setDuration(260).withEndAction(() -> setVisibility(GONE)).start();
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float s = 5.5f * d;
        paint.setColor(Pal.accent);
        path.reset();
        if (up) {
            path.moveTo(cx - s, cy + s * 0.45f);
            path.lineTo(cx, cy - s * 0.55f);
            path.lineTo(cx + s, cy + s * 0.45f);
        } else {
            path.moveTo(cx - s, cy - s * 0.45f);
            path.lineTo(cx, cy + s * 0.55f);
            path.lineTo(cx + s, cy - s * 0.45f);
        }
        c.drawPath(path, paint);
        // a short bar on the far side: "to the very top / end"
        float by = up ? cy - s * 1.25f : cy + s * 1.25f;
        c.drawLine(cx - s * 0.8f, by, cx + s * 0.8f, by, paint);
    }
}
