package app.privatetodo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** Tiny canvas-drawn icons so the app needs no image assets or libraries. */
final class IconView extends View {
    static final int CHECK = 0;
    static final int CROSS = 1;
    static final int BADGE = 3;
    static final int BELL = 4;
    static final int GEAR = 5;
    static final int SEARCH = 6;
    static final int CLOCK = 7;
    static final int SEND = 8;
    static final int BACK = 9;
    static final int NOTE = 10;
    static final int TRASH = 11;
    static final int PLUS = 12;
    static final int LOCK = 13;
    static final int SWIPE_DONE = 14;
    static final int PLAN = 15;
    static final int POMODORO = 16;
    static final int HABIT = 17;
    static final int CALENDAR = 18;
    static final int UNDO = 19;
    static final int REDO = 20;
    static final int PIN = 21;
    static final int LOOKS = 22;
    static final int SORT = 23;

    private final int kind;
    private boolean on;
    private int tint;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final android.graphics.RectF rect = new android.graphics.RectF();
    private final float d;

    IconView(Context c, int kind) {
        super(c);
        this.kind = kind;
        this.d = c.getResources().getDisplayMetrics().density;
        if (kind != BADGE) setClickable(true);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
    }

    /** Overrides the drawing colour of simple line icons (trash). */
    void setTint(int color) {
        tint = color;
        invalidate();
    }

    /** CHECK: ticked. BELL: show the "needs attention" dot. SEARCH: show the close (X) state. */
    void setOn(boolean on) {
        this.on = on;
        invalidate();
    }

    private void stroke(float width, int color) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(width * d);
        paint.setColor(color);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        switch (kind) {
            case CHECK:
                if (on) {
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(Pal.accent);
                    canvas.drawCircle(cx, cy, 12 * d, paint);
                    drawTick(canvas, cx, cy, 1f, Pal.onAccent, 2.2f);
                } else {
                    stroke(2, Pal.muted);
                    canvas.drawCircle(cx, cy, 11 * d, paint);
                }
                break;
            case CROSS:
                stroke(2, Pal.muted);
                canvas.drawLine(cx - 5 * d, cy - 5 * d, cx + 5 * d, cy + 5 * d, paint);
                canvas.drawLine(cx + 5 * d, cy - 5 * d, cx - 5 * d, cy + 5 * d, paint);
                break;
            case BELL:
                stroke(1.8f, Pal.ink);
                path.reset();
                path.moveTo(cx - 6 * d, cy + 4 * d);
                path.lineTo(cx - 6 * d, cy - 1 * d);
                path.cubicTo(cx - 6 * d, cy - 9 * d, cx + 6 * d, cy - 9 * d, cx + 6 * d, cy - 1 * d);
                path.lineTo(cx + 6 * d, cy + 4 * d);
                path.close();
                canvas.drawPath(path, paint);
                canvas.drawLine(cx - 8 * d, cy + 4 * d, cx + 8 * d, cy + 4 * d, paint);
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(cx, cy + 7.5f * d, 1.8f * d, paint);
                if (on) {
                    paint.setColor(Pal.overdue);
                    canvas.drawCircle(cx + 7 * d, cy - 8 * d, 4 * d, paint);
                }
                break;
            case GEAR:
                stroke(1.9f, Pal.ink);
                canvas.drawCircle(cx, cy, 3.2f * d, paint);
                canvas.drawCircle(cx, cy, 7 * d, paint);
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI / 4 * i;
                    float cs = (float) Math.cos(a);
                    float sn = (float) Math.sin(a);
                    canvas.drawLine(cx + cs * 7 * d, cy + sn * 7 * d,
                            cx + cs * 10 * d, cy + sn * 10 * d, paint);
                }
                break;
            case SEARCH:
                stroke(2, Pal.ink);
                if (on) { // close search
                    canvas.drawLine(cx - 6 * d, cy - 6 * d, cx + 6 * d, cy + 6 * d, paint);
                    canvas.drawLine(cx + 6 * d, cy - 6 * d, cx - 6 * d, cy + 6 * d, paint);
                } else {
                    canvas.drawCircle(cx - 2 * d, cy - 2 * d, 6.5f * d, paint);
                    canvas.drawLine(cx + 2.8f * d, cy + 2.8f * d, cx + 8 * d, cy + 8 * d, paint);
                }
                break;
            case CLOCK:
                stroke(1.8f, Pal.muted);
                canvas.drawCircle(cx, cy, 9 * d, paint);
                canvas.drawLine(cx, cy, cx, cy - 5 * d, paint);
                canvas.drawLine(cx, cy, cx + 4 * d, cy + 2 * d, paint);
                break;
            case SEND:
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Pal.accent);
                canvas.drawCircle(cx, cy, 17 * d, paint);
                stroke(2.2f, Pal.onAccent);
                canvas.drawLine(cx, cy + 6 * d, cx, cy - 6 * d, paint);
                canvas.drawLine(cx - 5 * d, cy - 1 * d, cx, cy - 6 * d, paint);
                canvas.drawLine(cx + 5 * d, cy - 1 * d, cx, cy - 6 * d, paint);
                break;
            case BACK:
                stroke(2.4f, Pal.ink);
                canvas.drawLine(cx + 3 * d, cy - 7 * d, cx - 4 * d, cy, paint);
                canvas.drawLine(cx - 4 * d, cy, cx + 3 * d, cy + 7 * d, paint);
                break;
            case LOOKS: // a block and two lines: change how notes are previewed
                stroke(2.1f, Pal.ink);
                rect.set(cx - 8 * d, cy - 8 * d, cx + 8 * d, cy - 2 * d);
                canvas.drawRoundRect(rect, 2 * d, 2 * d, paint);
                canvas.drawLine(cx - 8 * d, cy + 3 * d, cx + 8 * d, cy + 3 * d, paint);
                canvas.drawLine(cx - 8 * d, cy + 7 * d, cx + 2 * d, cy + 7 * d, paint);
                break;
            case SORT: // two arrows, down and up
                stroke(2.1f, Pal.ink);
                canvas.drawLine(cx - 5 * d, cy - 7 * d, cx - 5 * d, cy + 7 * d, paint);
                canvas.drawLine(cx - 8.5f * d, cy + 3.5f * d, cx - 5 * d, cy + 7 * d, paint);
                canvas.drawLine(cx - 5 * d, cy + 7 * d, cx - 1.5f * d, cy + 3.5f * d, paint);
                canvas.drawLine(cx + 5 * d, cy + 7 * d, cx + 5 * d, cy - 7 * d, paint);
                canvas.drawLine(cx + 1.5f * d, cy - 3.5f * d, cx + 5 * d, cy - 7 * d, paint);
                canvas.drawLine(cx + 5 * d, cy - 7 * d, cx + 8.5f * d, cy - 3.5f * d, paint);
                break;
            case PIN: { // thumbtack; filled when the note is pinned
                path.reset();
                path.moveTo(cx - 3 * d, cy - 8 * d);
                path.lineTo(cx - 3 * d, cy - 1 * d);
                path.lineTo(cx - 6 * d, cy + 2 * d);
                path.lineTo(cx + 6 * d, cy + 2 * d);
                path.lineTo(cx + 3 * d, cy - 1 * d);
                path.lineTo(cx + 3 * d, cy - 8 * d);
                path.close();
                int pc = tint != 0 ? tint : (on ? Pal.accent : Pal.muted);
                if (on) {
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(pc);
                    canvas.drawPath(path, paint);
                }
                stroke(1.9f, pc);
                canvas.drawPath(path, paint);
                canvas.drawLine(cx - 5 * d, cy - 8 * d, cx + 5 * d, cy - 8 * d, paint);
                canvas.drawLine(cx, cy + 2 * d, cx, cy + 9 * d, paint);
                break;
            }
            case UNDO:
            case REDO: { // curved arrow, pointing back (undo) or forward (redo)
                stroke(2.1f, Pal.ink);
                float sg = kind == UNDO ? 1f : -1f;
                path.reset();
                path.moveTo(cx - sg * 6 * d, cy - 4 * d);
                path.lineTo(cx + sg * 5 * d, cy - 4 * d);
                path.cubicTo(cx + sg * 10 * d, cy - 4 * d, cx + sg * 10 * d, cy + 6 * d, cx + sg * 5 * d, cy + 6 * d);
                path.lineTo(cx - sg * 1 * d, cy + 6 * d);
                canvas.drawPath(path, paint);
                canvas.drawLine(cx - sg * 6 * d, cy - 4 * d, cx - sg * 2 * d, cy - 8 * d, paint);
                canvas.drawLine(cx - sg * 6 * d, cy - 4 * d, cx - sg * 2 * d, cy, paint);
                break;
            }
            case NOTE: // notebook with a spine and ruled lines
                stroke(1.8f, Pal.ink);
                rect.set(cx - 7 * d, cy - 9 * d, cx + 7 * d, cy + 9 * d);
                canvas.drawRoundRect(rect, 2.5f * d, 2.5f * d, paint);
                canvas.drawLine(cx - 3 * d, cy - 9 * d, cx - 3 * d, cy + 9 * d, paint);
                canvas.drawLine(cx, cy - 3 * d, cx + 4 * d, cy - 3 * d, paint);
                canvas.drawLine(cx, cy + 1 * d, cx + 4 * d, cy + 1 * d, paint);
                break;
            case TRASH:
                stroke(1.9f, tint != 0 ? tint : Pal.muted);
                canvas.drawLine(cx - 7 * d, cy - 5 * d, cx + 7 * d, cy - 5 * d, paint);
                canvas.drawLine(cx - 2.5f * d, cy - 5 * d, cx - 2.5f * d, cy - 8 * d, paint);
                canvas.drawLine(cx + 2.5f * d, cy - 5 * d, cx + 2.5f * d, cy - 8 * d, paint);
                canvas.drawLine(cx - 2.5f * d, cy - 8 * d, cx + 2.5f * d, cy - 8 * d, paint);
                path.reset();
                path.moveTo(cx - 5.5f * d, cy - 5 * d);
                path.lineTo(cx - 4.5f * d, cy + 8 * d);
                path.lineTo(cx + 4.5f * d, cy + 8 * d);
                path.lineTo(cx + 5.5f * d, cy - 5 * d);
                canvas.drawPath(path, paint);
                break;
            case PLAN: // calendar with two rings and a grid dot
                stroke(1.8f, Pal.ink);
                rect.set(cx - 8 * d, cy - 7 * d, cx + 8 * d, cy + 9 * d);
                canvas.drawRoundRect(rect, 3 * d, 3 * d, paint);
                canvas.drawLine(cx - 8 * d, cy - 2 * d, cx + 8 * d, cy - 2 * d, paint);
                canvas.drawLine(cx - 3.5f * d, cy - 10 * d, cx - 3.5f * d, cy - 5 * d, paint);
                canvas.drawLine(cx + 3.5f * d, cy - 10 * d, cx + 3.5f * d, cy - 5 * d, paint);
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(cx - 3.5f * d, cy + 3 * d, 1.2f * d, paint);
                canvas.drawCircle(cx + 3.5f * d, cy + 3 * d, 1.2f * d, paint);
                canvas.drawCircle(cx - 3.5f * d, cy + 6.3f * d, 1.2f * d, paint);
                break;
            case POMODORO: // tomato with a leaf crown and a tiny clock hand
                stroke(1.8f, Pal.ink);
                canvas.drawCircle(cx, cy + 2 * d, 8 * d, paint);
                path.reset();
                path.moveTo(cx, cy - 6 * d);
                path.lineTo(cx - 4 * d, cy - 8.5f * d);
                path.moveTo(cx, cy - 6 * d);
                path.lineTo(cx + 4 * d, cy - 8.5f * d);
                path.moveTo(cx, cy - 6 * d);
                path.lineTo(cx, cy - 10.5f * d);
                canvas.drawPath(path, paint);
                canvas.drawLine(cx, cy + 2 * d, cx, cy - 2 * d, paint);
                canvas.drawLine(cx, cy + 2 * d, cx + 3 * d, cy + 3.5f * d, paint);
                break;
            case HABIT: // ring with a tick, a small spark at the top right
                stroke(1.8f, Pal.ink);
                canvas.drawCircle(cx, cy + 1 * d, 8.5f * d, paint);
                path.reset();
                path.moveTo(cx - 4 * d, cy + 1 * d);
                path.lineTo(cx - 1 * d, cy + 4 * d);
                path.lineTo(cx + 4.5f * d, cy - 2.5f * d);
                canvas.drawPath(path, paint);
                canvas.drawLine(cx + 7 * d, cy - 9 * d, cx + 7 * d, cy - 6 * d, paint);
                canvas.drawLine(cx + 5.5f * d, cy - 7.5f * d, cx + 8.5f * d, cy - 7.5f * d, paint);
                break;
            case CALENDAR: // month grid with one day filled in
                stroke(1.8f, Pal.ink);
                rect.set(cx - 9 * d, cy - 9 * d, cx + 9 * d, cy + 9 * d);
                canvas.drawRoundRect(rect, 3.5f * d, 3.5f * d, paint);
                canvas.drawLine(cx - 9 * d, cy - 4 * d, cx + 9 * d, cy - 4 * d, paint);
                paint.setStyle(Paint.Style.FILL);
                for (int gy = 0; gy < 2; gy++) {
                    for (int gx = 0; gx < 3; gx++) {
                        if (gy == 1 && gx == 1) continue;
                        canvas.drawCircle(cx + (gx - 1) * 5 * d, cy + 1 * d + gy * 5 * d, 1.1f * d, paint);
                    }
                }
                paint.setColor(Pal.accent);
                canvas.drawCircle(cx, cy + 6 * d, 2.6f * d, paint);
                break;
            case LOCK: // padlock, used in the "stored only on this device" line
                stroke(1.7f, Pal.muted);
                rect.set(cx - 5.5f * d, cy - 1 * d, cx + 5.5f * d, cy + 6.5f * d);
                canvas.drawRoundRect(rect, 2 * d, 2 * d, paint);
                path.reset();
                path.moveTo(cx - 3.2f * d, cy - 1 * d);
                path.lineTo(cx - 3.2f * d, cy - 3.5f * d);
                path.cubicTo(cx - 3.2f * d, cy - 8.5f * d, cx + 3.2f * d, cy - 8.5f * d, cx + 3.2f * d, cy - 3.5f * d);
                path.lineTo(cx + 3.2f * d, cy - 1 * d);
                canvas.drawPath(path, paint);
                break;
            case SWIPE_DONE: // plain white tick, shown behind a row while swiping it right
                drawTick(canvas, cx, cy, 1.6f, tint != 0 ? tint : Color.WHITE, 2.2f);
                break;
            case PLUS:
                stroke(2.4f, tint != 0 ? tint : Pal.onAccent);
                canvas.drawLine(cx - 7 * d, cy, cx + 7 * d, cy, paint);
                canvas.drawLine(cx, cy - 7 * d, cx, cy + 7 * d, paint);
                break;
            default: // BADGE
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Pal.accent);
                canvas.drawCircle(cx, cy, Math.min(cx, cy), paint);
                drawTick(canvas, cx, cy, 2.4f, Pal.onAccent, 1.5f);
                break;
        }
    }

    private void drawTick(Canvas canvas, float cx, float cy, float scale, int color, float stroke) {
        path.reset();
        path.moveTo(cx - 4.5f * d * scale, cy + 0.5f * d * scale);
        path.lineTo(cx - 1.5f * d * scale, cy + 3.5f * d * scale);
        path.lineTo(cx + 5f * d * scale, cy - 3.5f * d * scale);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(stroke * d * (scale > 1.5f ? 1.6f : 1f));
        paint.setColor(color);
        canvas.drawPath(path, paint);
    }
}
