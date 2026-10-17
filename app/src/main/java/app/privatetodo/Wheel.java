package app.privatetodo;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.text.InputType;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.util.function.IntConsumer;

/**
 * Swipe-up/down dial. Replaces the platform NumberPicker so the rule is simple and the same for every
 * dial: whichever value sits in the centre band is drawn bold and full-colour, the rest are lighter.
 */
final class Wheel extends View {
    interface Listener {
        void onChange(Wheel w, int oldValue, int newValue);
    }

    private static final int ROWS = 5;

    private final float density;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint band = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final int slop;
    private final float rowH;

    private int min;
    private int max = 1;
    private String[] labels;
    private boolean wrap = true;
    private float pos; // continuous index into 0..count-1
    private int lastIdx;
    private Listener listener;
    private ValueAnimator anim;
    private VelocityTracker vt;
    private float downY;
    private float lastY;
    private boolean dragging;
    private final Typeface regular;
    private final Typeface bold;
    private Runnable flingRun;
    private long flingTime;
    private float flingV; // rows per second
    private long lastTapAt;
    private long lastHaptic;
    private IntConsumer typed; // when set, a typed number goes here instead of being matched to a row
    private static final float FRICTION = 1.9f; // per second; lower = longer glide

    Wheel(Context c) {
        super(c);
        density = c.getResources().getDisplayMetrics().density;
        rowH = 36 * density;
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
        Fonts.init(c);
        regular = Fonts.face(false);
        bold = Fonts.face(true);
        band.setColor(Pal.accent);
        setFocusable(false);
    }

    // ------------------------------------------------------------ API like NumberPicker

    void setMinValue(int v) {
        min = v;
    }

    void setMaxValue(int v) {
        max = v;
    }

    void setDisplayedValues(String[] l) {
        labels = l;
    }

    void setWrapSelectorWheel(boolean w) {
        wrap = w;
    }

    void setOnValueChangedListener(Listener l) {
        listener = l;
    }

    /** Receives a number typed by double-tapping the centre value (for dials whose rows are a custom list). */
    void setTypedHandler(IntConsumer h) {
        typed = h;
    }

    int getValue() {
        return min + lastIdx;
    }

    /** Sets the value without calling the listener. */
    void setValue(int v) {
        stop();
        int idx = Math.max(0, Math.min(count() - 1, v - min));
        pos = idx;
        lastIdx = idx;
        invalidate();
    }

    private int count() {
        return Math.max(1, max - min + 1);
    }

    private String label(int idx) {
        int v = min + idx;
        if (labels != null) {
            int k = idx;
            return k >= 0 && k < labels.length ? labels[k] : "";
        }
        return String.valueOf(v);
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void onMeasure(int w, int h) {
        setMeasuredDimension(resolveSize(Math.round(80 * density), w), Math.round(rowH * ROWS));
    }

    @Override
    protected void onDraw(Canvas c) {
        float cy = getHeight() / 2f;
        float cx = getWidth() / 2f;
        // two accent lines frame the centre value, as in the design
        float lx0 = 16 * density;
        float lx1 = getWidth() - 16 * density;
        float lh = 2 * density;
        c.drawRect(lx0, cy - rowH / 2f - lh / 2f, lx1, cy - rowH / 2f + lh / 2f, band);
        c.drawRect(lx0, cy + rowH / 2f - lh / 2f, lx1, cy + rowH / 2f + lh / 2f, band);

        int n = count();
        int base = Math.round(pos);
        paint.setTextAlign(Paint.Align.CENTER);
        for (int k = -ROWS / 2 - 1; k <= ROWS / 2 + 1; k++) {
            int idx = base + k;
            if (!wrap && (idx < 0 || idx >= n)) continue;
            int real = ((idx % n) + n) % n;
            float d = idx - pos; // distance from the centre, in rows
            float ad = Math.abs(d);
            boolean centre = ad < 0.5f;
            paint.setTypeface(centre ? bold : regular);
            paint.setTextSize((centre ? 22 : 18) * density);
            paint.setColor(centre ? Pal.ink : Pal.muted);
            paint.setAlpha(centre ? 255 : (int) Math.max(40, 200 - ad * 60));
            float y = cy + d * rowH - (paint.ascent() + paint.descent()) / 2f;
            c.drawText(label(real), cx, y, paint);
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                stop();
                getParent().requestDisallowInterceptTouchEvent(true);
                downY = lastY = e.getY();
                dragging = false;
                vt = VelocityTracker.obtain();
                vt.addMovement(e);
                return true;
            case MotionEvent.ACTION_MOVE:
                vt.addMovement(e);
                if (!dragging && Math.abs(e.getY() - downY) > slop) dragging = true;
                if (dragging) {
                    setPos(pos - (e.getY() - lastY) / rowH);
                    lastY = e.getY();
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                vt.addMovement(e);
                vt.computeCurrentVelocity(1000);
                float v = -vt.getYVelocity() / rowH; // rows per second
                v = Math.max(-110f, Math.min(110f, v));
                vt.recycle();
                vt = null;
                boolean up = e.getActionMasked() == MotionEvent.ACTION_UP;
                if (!dragging && up) {
                    float off = e.getY() - getHeight() / 2f;
                    if (Math.abs(off) < rowH / 2f) { // centre value: a quick second tap types a number
                        long now = SystemClock.uptimeMillis();
                        if (numeric() && now - lastTapAt < ViewConfiguration.getDoubleTapTimeout() + 100) {
                            lastTapAt = 0;
                            settle(Math.round(pos));
                            edit();
                            return true;
                        }
                        lastTapAt = now;
                        settle(Math.round(pos));
                    } else {
                        lastTapAt = 0;
                        settle(Math.round(pos) + Math.round(off / rowH));
                    }
                } else if (Math.abs(v) > 3f) {
                    fling(v);
                } else {
                    settle(Math.round(pos));
                }
                return true;
            default:
                return true;
        }
    }

    /** iOS-style glide: the speed you let go at decays smoothly to a stop, then the nearest row eases in. */
    private void fling(float v) {
        stop();
        flingV = v;
        flingTime = System.nanoTime();
        flingRun = new Runnable() {
            @Override
            public void run() {
                long now = System.nanoTime();
                float dt = Math.min(0.04f, (now - flingTime) / 1e9f);
                flingTime = now;
                setPos(pos + flingV * dt);
                flingV *= (float) Math.exp(-FRICTION * dt);
                boolean edge = !wrap && (pos <= 0f || pos >= count() - 1f);
                if (Math.abs(flingV) < 1.6f || edge) {
                    flingRun = null;
                    settle(Math.round(pos));
                } else {
                    postOnAnimation(this);
                }
            }
        };
        postOnAnimation(flingRun);
    }

    private boolean numeric() {
        if (typed != null || labels == null) return true;
        try {
            Integer.parseInt(labels[0].trim());
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private void edit() {
        final android.content.Context c = getContext();
        if (!(c instanceof android.app.Activity)) return;
        final EditText et = new EditText(c);
        et.setInputType(InputType.TYPE_CLASS_NUMBER);
        et.setSingleLine(true);
        et.setTextSize(26);
        et.setGravity(android.view.Gravity.CENTER);
        et.setTextColor(Pal.ink);
        et.setTypeface(Fonts.face(true));
        String cur = label(lastIdx).replaceAll("[^0-9]", "");
        et.setText(cur);
        et.setSelectAllOnFocus(true);
        LinearLayout box = new LinearLayout(c);
        box.setPadding(Math.round(32 * density), Math.round(8 * density), Math.round(32 * density), 0);
        box.addView(et, new LinearLayout.LayoutParams(-1, -2));
        String range = typed != null ? "" : labels == null ? min + " to " + max : "";
        final android.app.AlertDialog d = new Dlg.Builder(c)
                .setTitle("Type a number")
                .setMessage(range)
                .setView(box)
                .setPositiveButton("Set", (dd, ww) -> apply(et.getText().toString()))
                .setNegativeButton("Cancel", null)
                .show();
        et.requestFocus();
        if (d.getWindow() != null) {
            d.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        et.setOnEditorActionListener((tv, id, ev) -> {
            apply(et.getText().toString());
            d.dismiss();
            return true;
        });
    }

    private void apply(String s) {
        s = s.trim();
        if (s.isEmpty()) return;
        int val;
        try {
            val = Integer.parseInt(s);
        } catch (NumberFormatException ex) {
            return;
        }
        if (typed != null) {
            typed.accept(val);
            return;
        }
        int idx = -1;
        if (labels == null) {
            idx = val - min;
        } else {
            for (int i = 0; i < labels.length; i++) {
                try {
                    if (Integer.parseInt(labels[i].trim()) == val) {
                        idx = i;
                        break;
                    }
                } catch (NumberFormatException ignored) {
                    // non-numeric label
                }
            }
        }
        if (idx < 0 || idx >= count()) {
            Toast.makeText(getContext(), labels == null ? "Choose " + min + " to " + max
                    : "That value is not on this dial", Toast.LENGTH_SHORT).show();
            return;
        }
        stop();
        setPos(idx); // jump straight there (the dial may wrap)
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }

    private void setPos(float p) {
        if (!wrap) p = Math.max(0, Math.min(count() - 1, p));
        pos = p;
        int n = count();
        int idx = ((Math.round(pos) % n) + n) % n;
        if (idx != lastIdx) {
            int old = lastIdx;
            lastIdx = idx;
            long t = SystemClock.uptimeMillis();
            if (t - lastHaptic > 45) { // a soft tick per row, but never a buzz at fling speed
                lastHaptic = t;
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            }
            if (listener != null) listener.onChange(this, min + old, min + idx);
        }
        invalidate();
    }

    private void settle(int target) {
        if (!wrap) target = Math.max(0, Math.min(count() - 1, target));
        final float from = pos;
        final float to = target;
        stop();
        anim = ValueAnimator.ofFloat(from, to);
        anim.setDuration(Math.min(520, 170 + (long) (Math.abs(to - from) * 260)));
        anim.setInterpolator(new DecelerateInterpolator());
        anim.addUpdateListener(a -> setPos((Float) a.getAnimatedValue()));
        anim.start();
    }

    private void stop() {
        if (flingRun != null) {
            removeCallbacks(flingRun);
            flingRun = null;
        }
        if (anim != null) {
            anim.cancel();
            anim = null;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }
}
