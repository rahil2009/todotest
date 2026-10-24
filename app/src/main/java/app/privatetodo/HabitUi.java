package app.privatetodo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Small custom views for the habit screens. */
final class HabitUi {
    private HabitUi() { }

    static int blend(int a, int b, float t) {
        return Color.argb(255,
                Math.round(Color.red(a) * t + Color.red(b) * (1 - t)),
                Math.round(Color.green(a) * t + Color.green(b) * (1 - t)),
                Math.round(Color.blue(a) * t + Color.blue(b) * (1 - t)));
    }

    /** A "drink water" habit (it gets the rising-water background): a goal in a drink unit, or "water" in the name. */
    static boolean isWater(Habit h) {
        if (h.goal <= 0) return false;
        String n = h.name.toLowerCase(java.util.Locale.ROOT);
        String u = h.unit.toLowerCase(java.util.Locale.ROOT).trim();
        if (n.contains("water") || n.contains("hydrat")) return true;
        return u.equals("ml") || u.equals("l") || u.startsWith("litre") || u.startsWith("liter")
                || u.equals("oz") || u.equals("fl oz") || u.startsWith("glass") || u.startsWith("cup");
    }

    static int alpha(int c, int a) {
        return (c & 0x00FFFFFF) | (a << 24);
    }

    /** A colour that white text reads well on (the habit colour, deepened in light mode). */
    static int deep(int c) {
        return Pal.dark ? c : blend(c, 0xFF000000, 0.62f);
    }

    static int onDeep(int c) {
        return Pal.dark ? 0xFF10201B : Color.WHITE;
    }

    // ------------------------------------------------------------------ badge with a rising "liquid" level

    /** Rounded-square emoji badge. The colour fills from the bottom as the day's goal is reached. */
    static final class Badge extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private final Path clip = new Path();
        private final int color;
        private final String emoji;
        private float level;

        Badge(Context c, int color, String emoji) {
            super(c);
            this.color = color;
            this.emoji = emoji;
            setClickable(true);
        }

        void setLevel(float v) {
            level = Math.max(0f, Math.min(1f, v));
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth();
            float h = getHeight();
            float rad = Math.min(w, h) * 0.34f;
            r.set(0, 0, w, h);
            p.setStyle(Paint.Style.FILL);
            p.setColor(alpha(color, Pal.dark ? 0x40 : 0x66));
            c.drawRoundRect(r, rad, rad, p);
            if (level > 0f) {
                clip.reset();
                clip.addRoundRect(r, rad, rad, Path.Direction.CW);
                c.save();
                c.clipPath(clip);
                p.setColor(alpha(color, level >= 1f ? 0xE0 : 0x99));
                c.drawRect(0, h * (1f - level), w, h, p);
                c.restore();
            }
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(h * 0.5f);
            p.setColor(Color.BLACK);
            Paint.FontMetrics fm = p.getFontMetrics();
            c.drawText(emoji.isEmpty() ? "✓" : emoji, w / 2f, h / 2f - (fm.ascent + fm.descent) / 2f, p);
        }
    }

    // ------------------------------------------------------------------ one day on a card

    static final int FUTURE = 0;
    static final int TODAY = 1;
    static final int DONE = 2;
    static final int MISSED = 3;
    static final int PARTIAL = 4;
    static final int SKIP = 5;

    static final class DayDot extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private final int color;
        private int state = FUTURE;
        private float frac;

        DayDot(Context c, int color) {
            super(c);
            this.color = color;
            setClickable(true);
        }

        void set(int state, float frac) {
            this.state = state;
            this.frac = frac;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float d = getResources().getDisplayMetrics().density;
            float cx = getWidth() / 2f;
            float cy = Math.min(getHeight() / 2f, 18 * d);
            float rad = 13 * d;
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            switch (state) {
                case DONE:
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(color);
                    c.drawCircle(cx, cy, rad, p);
                    p.setStyle(Paint.Style.STROKE);
                    p.setColor(Color.WHITE);
                    p.setStrokeWidth(2.4f * d);
                    c.drawLine(cx - 5 * d, cy + 0.5f * d, cx - 1.5f * d, cy + 4 * d, p);
                    c.drawLine(cx - 1.5f * d, cy + 4 * d, cx + 5.5f * d, cy - 3.5f * d, p);
                    break;
                case MISSED:
                    p.setStyle(Paint.Style.STROKE);
                    p.setColor(0xFFEF5350);
                    p.setStrokeWidth(2.4f * d);
                    c.drawLine(cx - 5 * d, cy - 5 * d, cx + 5 * d, cy + 5 * d, p);
                    c.drawLine(cx - 5 * d, cy + 5 * d, cx + 5 * d, cy - 5 * d, p);
                    break;
                case SKIP:
                    r.set(cx - rad, cy - rad, cx + rad, cy + rad);
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(alpha(Pal.muted, 0x22));
                    c.drawCircle(cx, cy, rad, p);
                    c.save();
                    android.graphics.Path clip = new android.graphics.Path();
                    clip.addCircle(cx, cy, rad, android.graphics.Path.Direction.CW);
                    c.clipPath(clip);
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(2f * d);
                    p.setColor(alpha(Pal.muted, 0x70));
                    for (float k = -rad * 2; k < rad * 2; k += 6 * d) {
                        c.drawLine(cx + k - rad, cy + rad, cx + k + rad, cy - rad, p);
                    }
                    c.restore();
                    break;
                case PARTIAL:
                case TODAY:
                    r.set(cx - rad + 1.5f * d, cy - rad + 1.5f * d, cx + rad - 1.5f * d, cy + rad - 1.5f * d);
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(3f * d);
                    p.setColor(alpha(color, 0x55));
                    c.drawOval(r, p);
                    if (frac > 0f) {
                        p.setColor(color);
                        c.drawArc(r, -90, 360f * Math.min(1f, frac), false, p);
                    }
                    if (state == TODAY) {
                        p.setStyle(Paint.Style.FILL);
                        p.setColor(color);
                        c.drawCircle(cx, cy + rad + 5 * d, 2.2f * d, p);
                    }
                    break;
                default:
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(alpha(color, 0x2A));
                    c.drawCircle(cx, cy, rad, p);
                    break;
            }
        }
    }

    // ------------------------------------------------------------------ flame

    static final class Flame extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        Flame(Context c) {
            super(c);
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth();
            float h = getHeight();
            p.setColor(Pal.ink);
            p.setStyle(Paint.Style.FILL);
            path.reset();
            path.moveTo(w * 0.5f, 0);
            path.cubicTo(w * 0.62f, h * 0.22f, w * 0.95f, h * 0.38f, w * 0.9f, h * 0.68f);
            path.cubicTo(w * 0.86f, h * 0.92f, w * 0.66f, h, w * 0.5f, h);
            path.cubicTo(w * 0.3f, h, w * 0.1f, h * 0.9f, w * 0.1f, h * 0.66f);
            path.cubicTo(w * 0.1f, h * 0.5f, w * 0.2f, h * 0.38f, w * 0.3f, h * 0.3f);
            path.cubicTo(w * 0.32f, h * 0.42f, w * 0.4f, h * 0.46f, w * 0.42f, h * 0.44f);
            path.cubicTo(w * 0.48f, h * 0.3f, w * 0.44f, h * 0.14f, w * 0.5f, 0);
            path.close();
            c.drawPath(path, p);
        }
    }

    // ------------------------------------------------------------------ month calendar

    /** Month grid (Sunday first). Runs of completed days join into one pill, like the history reference. */
    static final class Cal extends View {
        interface DayListener {
            void onDay(long day);
        }

        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private final float d;
        private final Habit habit;
        private Map<Long, Integer> data = new HashMap<>();
        private int year;
        private int month;
        private DayListener listener;

        Cal(Context c, Habit habit) {
            super(c);
            this.habit = habit;
            d = c.getResources().getDisplayMetrics().density;
            LocalDate t = LocalDate.now();
            year = t.getYear();
            month = t.getMonthValue();
        }

        void setListener(DayListener l) {
            listener = l;
        }

        void setData(Map<Long, Integer> m) {
            data = m;
            invalidate();
        }

        void shift(int by) {
            LocalDate x = LocalDate.of(year, month, 1).plusMonths(by);
            year = x.getYear();
            month = x.getMonthValue();
            requestLayout();
            invalidate();
        }

        String title() {
            LocalDate x = LocalDate.of(year, month, 1);
            return x.getMonth().getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault())
                    + " " + x.getYear();
        }

        private int lead() {
            return LocalDate.of(year, month, 1).getDayOfWeek().getValue() % 7; // Sunday = 0
        }

        private int rows() {
            return (lead() + LocalDate.of(year, month, 1).lengthOfMonth() + 6) / 7;
        }

        private float head() {
            return 30 * d;
        }

        private float cell() {
            return 46 * d;
        }

        @Override
        protected void onMeasure(int wSpec, int hSpec) {
            setMeasuredDimension(MeasureSpec.getSize(wSpec), Math.round(head() + rows() * cell()));
        }

        private int state(long day, long today) {
            int a = data.containsKey(day) ? data.get(day) : 0;
            if (a < 0) return SKIP;
            if (habit.doneAt(a)) return DONE;
            if (day == today) return TODAY;
            if (a > 0) return PARTIAL;
            return day > today ? FUTURE : MISSED;
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth() / 7f;
            long today = HabitDb.today();
            int pill = deep(habit.color);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Fonts.face(false));
            p.setTextSize(13 * d);
            p.setColor(Pal.muted);
            String[] hd = {"S", "M", "T", "W", "T", "F", "S"};
            for (int i = 0; i < 7; i++) c.drawText(hd[i], w * i + w / 2f, head() - 8 * d, p);

            int len = LocalDate.of(year, month, 1).lengthOfMonth();
            int lead = lead();
            long first = LocalDate.of(year, month, 1).toEpochDay();
            for (int row = 0; row < rows(); row++) {
                float top = head() + row * cell();
                float cy = top + cell() / 2f;
                // pills for runs of done days within this week row
                int col = 0;
                while (col < 7) {
                    int dom = row * 7 + col - lead + 1;
                    if (dom < 1 || dom > len || state(first + dom - 1, today) != DONE) {
                        col++;
                        continue;
                    }
                    int start = col;
                    while (col + 1 < 7) {
                        int nd = row * 7 + col + 1 - lead + 1;
                        if (nd < 1 || nd > len || state(first + nd - 1, today) != DONE) break;
                        col++;
                    }
                    r.set(w * start + 3 * d, cy - 17 * d, w * (col + 1) - 3 * d, cy + 17 * d);
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(pill);
                    c.drawRoundRect(r, 17 * d, 17 * d, p);
                    col++;
                }
                for (int cc = 0; cc < 7; cc++) {
                    int dom = row * 7 + cc - lead + 1;
                    if (dom < 1 || dom > len) continue;
                    long day = first + dom - 1;
                    int st = state(day, today);
                    float cx = w * cc + w / 2f;
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(2f * d);
                    if (st == TODAY) {
                        p.setColor(pill);
                        c.drawCircle(cx, cy, 16 * d, p);
                    } else if (st == PARTIAL) {
                        p.setStyle(Paint.Style.FILL);
                        p.setColor(alpha(habit.color, 0x55));
                        c.drawCircle(cx, cy, 16 * d, p);
                    } else if (st == SKIP) {
                        p.setColor(alpha(Pal.muted, 0x90));
                        p.setPathEffect(new android.graphics.DashPathEffect(new float[]{4 * d, 3 * d}, 0));
                        c.drawCircle(cx, cy, 16 * d, p);
                        p.setPathEffect(null);
                    }
                    p.setStyle(Paint.Style.FILL);
                    p.setTextSize(15 * d);
                    p.setTypeface(Fonts.face(st == DONE || st == TODAY));
                    p.setColor(st == DONE ? onDeep(habit.color) : day > today ? Pal.muted : Pal.ink);
                    c.drawText(String.valueOf(dom), cx, cy + 5.5f * d, p);
                }
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (e.getAction() == MotionEvent.ACTION_DOWN) return true;
            if (e.getAction() != MotionEvent.ACTION_UP || listener == null) return false;
            float y = e.getY() - head();
            if (y < 0) return true;
            int row = (int) (y / cell());
            int col = (int) (e.getX() / (getWidth() / 7f));
            int dom = row * 7 + col - lead() + 1;
            if (col < 0 || col > 6 || dom < 1 || dom > LocalDate.of(year, month, 1).lengthOfMonth()) return true;
            long day = LocalDate.of(year, month, dom).toEpochDay();
            if (day <= HabitDb.today()) listener.onDay(day);
            return true;
        }
    }
}
