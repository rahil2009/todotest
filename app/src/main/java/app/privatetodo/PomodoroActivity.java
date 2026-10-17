package app.privatetodo;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/** Pomodoro focus timer: ring with 60 ticks, session dots, a task to focus on, and daily stats. */
public class PomodoroActivity extends Activity {
    private static final int BLUE = 0xFF3B82F6;
    private static final int VIOLET = 0xFF8B5CF6;

    private float density;
    private int builtDark;
    private LinearLayout content;
    private Ring ring;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            update();
            handler.postDelayed(this, Pomo.running(PomodoroActivity.this) ? 40 : 500);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Pal.init(this);
        builtDark = Pal.themeRes();
        setTheme(Pal.themeRes());
        super.onCreate(savedInstanceState);
        density = getResources().getDisplayMetrics().density;
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Pal.init(this);
        if (Pal.themeRes() != builtDark) {
            recreate();
            return;
        }
        Pomo.clearDone(this);
        render();
        handler.post(tick);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(tick);
        super.onPause();
    }

    private int phaseColor() {
        int p = Pomo.phase(this);
        return p == Pomo.FOCUS ? Pal.accent : p == Pomo.SHORT ? BLUE : VIOLET;
    }

    /** Called often: finishes the period when the clock hits zero and redraws the ring. */
    private void update() {
        if (Pomo.running(this) && Pomo.left(this) <= 0) {
            boolean wasFocus = Pomo.phase(this) == Pomo.FOCUS;
            Pomo.finishPhase(this);
            Hap.confirm(ring != null ? ring : getWindow().getDecorView());
            render();
            if (wasFocus && ring != null) ring.burst();
            return;
        }
        if (ring != null) ring.set(Pomo.left(this), Pomo.total(this), phaseColor(),
                Pomo.phaseName(Pomo.phase(this)), Pomo.running(this));
    }

    // ------------------------------------------------------------------ build

    private void render() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Pal.bg);
        scroll.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(16), dp(20), dp(32));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        setContentView(scroll);

        final boolean running = Pomo.running(this);
        if (running) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        IconView back = new IconView(this, IconView.BACK);
        back.setContentDescription("Back");
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        header.addView(text("Pomodoro", 30, Pal.ink, true));
        content.addView(header);

        // phase chips
        LinearLayout chips = new LinearLayout(this);
        String[] names = {"Focus", "Short break", "Long break"};
        for (int i = 0; i < 3; i++) {
            final int p = i;
            boolean on = i == Pomo.phase(this);
            TextView chip = text(names[i], 14, on ? Pal.onSel : Pal.muted, true);
            chip.setGravity(Gravity.CENTER);
            chip.setMinHeight(dp(42));
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(dp(21));
            g.setColor(on ? Pal.sel : Pal.card);
            chip.setBackground(g);
            chip.setOnClickListener(v -> {
                if (Pomo.running(this)) {
                    new Dlg.Builder(this)
                            .setTitle("Switch and stop the timer?")
                            .setMessage("The running countdown will be cleared.")
                            .setPositiveButton("Switch", (d, w) -> {
                                Pomo.select(this, p);
                                render();
                            })
                            .setNegativeButton("Keep going", null)
                            .show();
                } else {
                    Pomo.select(this, p);
                    render();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
            lp.rightMargin = dp(i < 2 ? 8 : 0);
            chips.addView(chip, lp);
        }
        LinearLayout.LayoutParams chipsLp = new LinearLayout.LayoutParams(-1, -2);
        chipsLp.topMargin = dp(8);
        content.addView(chips, chipsLp);

        // ring
        ring = new Ring();
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(dp(290), dp(290));
        rlp.gravity = Gravity.CENTER_HORIZONTAL;
        rlp.topMargin = dp(18);
        content.addView(ring, rlp);

        TextView line = text(motto(), 14, Pal.muted, false);
        line.setGravity(Gravity.CENTER);
        content.addView(line, new LinearLayout.LayoutParams(-1, -2));

        // controls
        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        Glyph reset = new Glyph(Glyph.RESET, false);
        reset.setContentDescription("Reset");
        reset.setOnClickListener(v -> {
            Hap.tick(v);
            Pomo.reset(this);
            render();
        });
        controls.addView(reset, new LinearLayout.LayoutParams(dp(56), dp(56)));
        Glyph play = new Glyph(running ? Glyph.PAUSE : Glyph.PLAY, true);
        play.setContentDescription(running ? "Pause" : "Start");
        play.setOnClickListener(v -> {
            Hap.confirm(v);
            if (Pomo.running(this)) Pomo.pause(this);
            else Pomo.start(this);
            render();
            handler.removeCallbacks(tick);
            handler.post(tick);
        });
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(dp(80), dp(80));
        plp.leftMargin = dp(28);
        plp.rightMargin = dp(28);
        controls.addView(play, plp);
        Glyph skip = new Glyph(Glyph.SKIP, false);
        skip.setContentDescription("Skip to the next period");
        skip.setOnClickListener(v -> {
            Hap.tick(v);
            Pomo.skip(this);
            render();
        });
        controls.addView(skip, new LinearLayout.LayoutParams(dp(56), dp(56)));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, -2);
        clp.topMargin = dp(14);
        content.addView(controls, clp);

        // session dots
        content.addView(dots());

        // focusing on
        content.addView(sectionLabel("Focusing on"));
        content.addView(taskCard());

        // today
        content.addView(sectionLabel("Your focus"));
        content.addView(statsCard());

        // durations
        content.addView(sectionLabel("Lengths"));
        LinearLayout lens = card();
        String[] lab = {"Focus", "Short break", "Long break"};
        for (int i = 0; i < 3; i++) {
            final int p = i;
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(dp(52));
            row.addView(text(lab[i], 16, Pal.ink, true), new LinearLayout.LayoutParams(0, -2, 1f));
            row.addView(text(Pomo.minutes(this, i) + " min  ›", 16, Pal.accent, true));
            row.setOnClickListener(v -> pickLength(p));
            lens.addView(row);
        }
        content.addView(lens);

        update();
        Fonts.apply(getWindow().getDecorView());
    }

    private String motto() {
        int p = Pomo.phase(this);
        if (p == Pomo.FOCUS) return Pomo.running(this) ? "One thing. Phone down." : "25 quiet minutes. Ready when you are.".replace("25", String.valueOf(Pomo.minutes(this, 0)));
        return Pomo.running(this) ? "Look far away. Stretch. Water." : "A proper rest makes the next round better.";
    }

    private View dots() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        int done = Pomo.round(this);
        boolean inFocus = Pomo.phase(this) == Pomo.FOCUS;
        int color = Pal.accent;
        for (int i = 0; i < 4; i++) {
            View dot = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            if (i < done) {
                g.setColor(color);
            } else if (i == done && inFocus) {
                g.setColor(0);
                g.setStroke(dp(2), color);
            } else {
                g.setColor((Pal.muted & 0x00FFFFFF) | 0x33000000);
            }
            dot.setBackground(g);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(14), dp(14));
            lp.leftMargin = dp(6);
            lp.rightMargin = dp(6);
            row.addView(dot, lp);
        }
        box.addView(row);
        TextView cap = text(inFocus ? "Session " + (done + 1) + " of 4" : done + " of 4 done before the long break",
                13, Pal.muted, false);
        cap.setGravity(Gravity.CENTER);
        cap.setPadding(0, dp(8), 0, 0);
        box.addView(cap);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(20);
        box.setLayoutParams(lp);
        return box;
    }

    private View taskCard() {
        LinearLayout card = card();
        Task t = null;
        long id = Pomo.taskId(this);
        if (id >= 0) {
            TaskDb db = new TaskDb(this);
            try {
                t = db.get(id);
            } finally {
                db.close();
            }
            if (t != null && t.done) t = null;
        }
        if (t == null) {
            TextView hint = text("Pick a task to focus on", 16, Pal.accent, true);
            hint.setMinHeight(dp(48));
            hint.setGravity(Gravity.CENTER_VERTICAL);
            hint.setOnClickListener(v -> pickTask());
            card.addView(hint);
            TextView sub = text("Optional. Mark it done when the session is over.", 13, Pal.muted, false);
            card.addView(sub);
            return card;
        }
        final Task task = t;
        card.addView(text((task.emoji == null || task.emoji.isEmpty() ? "" : task.emoji + "  ") + task.title,
                18, Pal.ink, true));
        LinearLayout row = new LinearLayout(this);
        row.setPadding(0, dp(8), 0, 0);
        TextView change = chipButton("Change", false);
        change.setOnClickListener(v -> pickTask());
        row.addView(change);
        TextView done = chipButton("Mark done", true);
        done.setOnClickListener(v -> {
            Hap.confirm(v);
            Reminders.complete(this, task.id);
            Pomo.setTask(this, -1);
            render();
        });
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-2, -2);
        dlp.leftMargin = dp(8);
        row.addView(done, dlp);
        card.addView(row);
        return card;
    }

    private View statsCard() {
        LinearLayout card = card();
        int[] today = Pomo.stat(this, 0);
        card.addView(text(today[0] == 0 ? "No sessions yet today"
                        : today[0] + (today[0] == 1 ? " session" : " sessions") + "  ·  " + fmt(today[1]),
                20, Pal.ink, true));
        int streak = 0;
        for (int i = 0; i < 365; i++) {
            if (Pomo.stat(this, i)[0] > 0) streak++;
            else if (i > 0) break;
            else if (i == 0) continue;
        }
        card.addView(text(streak > 1 ? streak + " days in a row" : "Last 7 days", 13, Pal.muted, false));
        Bars bars = new Bars();
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(110));
        lp.topMargin = dp(10);
        card.addView(bars, lp);
        return card;
    }

    private static String fmt(int mins) {
        return mins >= 60 ? (mins / 60) + " h " + (mins % 60 == 0 ? "" : (mins % 60) + " min") : mins + " min";
    }

    private void pickLength(final int phase) {
        final Wheel w = new Wheel(this);
        w.setMinValue(1);
        w.setMaxValue(120);
        w.setWrapSelectorWheel(false);
        w.setValue(Pomo.minutes(this, phase));
        LinearLayout box = new LinearLayout(this);
        box.setGravity(Gravity.CENTER);
        box.addView(w, new LinearLayout.LayoutParams(dp(120), dp(150)));
        box.addView(text("min", 18, Pal.muted, true));
        new Dlg.Builder(this)
                .setTitle(Pomo.phaseName(phase) + " length")
                .setView(box)
                .setPositiveButton("Set", (d, x) -> {
                    Pomo.setMinutes(this, phase, w.getValue());
                    render();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void pickTask() {
        TaskDb db = new TaskDb(this);
        final List<Task> tasks;
        try {
            tasks = db.list(TaskDb.ACTIVE);
        } finally {
            db.close();
        }
        if (tasks.isEmpty()) {
            new Dlg.Builder(this).setTitle("No open tasks")
                    .setMessage("Add a task on the home screen first.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        String[] items = new String[tasks.size()];
        for (int i = 0; i < items.length; i++) items[i] = tasks.get(i).title;
        new Dlg.Builder(this)
                .setTitle("Focus on")
                .setItems(items, (d, which) -> {
                    Pomo.setTask(this, tasks.get(which).id);
                    render();
                })
                .setNeutralButton("None", (d, w) -> {
                    Pomo.setTask(this, -1);
                    render();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ------------------------------------------------------------------ small helpers

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private TextView sectionLabel(String s) {
        TextView t = text(s.toUpperCase(Locale.ROOT), 12, Pal.muted, true);
        t.setLetterSpacing(0.12f);
        t.setPadding(dp(4), dp(24), 0, dp(8));
        return t;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(18), dp(12), dp(18), dp(14));
        GradientDrawable g = new GradientDrawable();
        g.setColor(Pal.card);
        g.setCornerRadius(dp(20));
        c.setBackground(g);
        return c;
    }

    private TextView chipButton(String s, boolean primary) {
        TextView t = text(s, 14, primary ? Pal.onAccent : Pal.ink, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(16), 0, dp(16), 0);
        t.setMinHeight(dp(40));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(20));
        g.setColor(primary ? Pal.accent : Pal.chip);
        t.setBackground(g);
        return t;
    }

    private int dp(int v) {
        return Math.round(v * density);
    }

    // ------------------------------------------------------------------ ring

    /** Sixty ticks that go out one by one, a depleting arc with a breathing head, and a confetti burst. */
    private final class Ring extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private long left;
        private long total = 1;
        private int color = Pal.accent;
        private String label = "";
        private boolean running;
        private long burstAt;
        private final float[] bx = new float[40];
        private final float[] by = new float[40];
        private final float[] bv = new float[40];
        private final float[] ba = new float[40];
        private final int[] bc = new int[40];

        Ring() {
            super(PomodoroActivity.this);
        }

        void set(long left, long total, int color, String label, boolean running) {
            this.left = left;
            this.total = Math.max(1, total);
            this.color = color;
            this.label = label;
            this.running = running;
            invalidate();
        }

        void burst() {
            Random rnd = new Random();
            int[] pal = {Pal.accent, BLUE, VIOLET, 0xFFFF6BB5, 0xFFFFB86B};
            for (int i = 0; i < bx.length; i++) {
                ba[i] = (float) (rnd.nextFloat() * Math.PI * 2);
                bv[i] = (60 + rnd.nextFloat() * 150) * density;
                bx[i] = rnd.nextFloat() * 6f;
                bc[i] = pal[rnd.nextInt(pal.length)];
            }
            burstAt = System.currentTimeMillis();
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth();
            float cx = w / 2f;
            float cy = getHeight() / 2f;
            float outer = w / 2f - 6 * density;
            float rem = Math.max(0f, Math.min(1f, left / (float) total));
            double nowS = System.currentTimeMillis() / 1000.0;
            float breath = running ? (float) (0.5 + 0.5 * Math.sin(nowS * 2.6)) : 0.35f;

            // 60 ticks: lit while time remains
            p.setStrokeCap(Paint.Cap.ROUND);
            int lit = (int) Math.ceil(rem * 60);
            for (int i = 0; i < 60; i++) {
                double a = -Math.PI / 2 + i * Math.PI * 2 / 60;
                boolean on = i < lit;
                boolean head = on && i == lit - 1;
                float len = (i % 5 == 0 ? 12 : 8) * density + (head ? 4 * density * (0.5f + breath) : 0);
                p.setStrokeWidth((i % 5 == 0 ? 3f : 2f) * density);
                p.setColor(on ? color : ((Pal.muted & 0x00FFFFFF) | 0x40000000));
                c.drawLine(cx + (float) Math.cos(a) * outer, cy + (float) Math.sin(a) * outer,
                        cx + (float) Math.cos(a) * (outer - len), cy + (float) Math.sin(a) * (outer - len), p);
            }

            // inner depleting arc
            float arcR = outer - 30 * density;
            r.set(cx - arcR, cy - arcR, cx + arcR, cy + arcR);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(9 * density);
            p.setColor((color & 0x00FFFFFF) | 0x26000000);
            c.drawCircle(cx, cy, arcR, p);
            p.setColor(color);
            float sweep = 360f * rem;
            c.drawArc(r, -90, sweep, false, p);
            if (rem > 0f && rem < 1f) {
                double a = Math.toRadians(-90 + sweep);
                float hx = cx + (float) Math.cos(a) * arcR;
                float hy = cy + (float) Math.sin(a) * arcR;
                p.setStyle(Paint.Style.FILL);
                p.setColor((color & 0x00FFFFFF) | 0x33000000);
                c.drawCircle(hx, hy, (11 + 6 * breath) * density, p);
                p.setColor(color);
                c.drawCircle(hx, hy, 6 * density, p);
                p.setColor(Pal.card);
                c.drawCircle(hx, hy, 2.5f * density, p);
            }

            // time + label
            long secs = (left + 999) / 1000;
            String time = String.format(Locale.US, "%02d:%02d", secs / 60, secs % 60);
            p.setStyle(Paint.Style.FILL);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Fonts.face(true));
            p.setColor(Pal.ink);
            p.setTextSize(58 * density);
            c.drawText(time, cx, cy + 14 * density, p);
            p.setTextSize(14 * density);
            p.setColor(color);
            p.setLetterSpacing(0.12f);
            c.drawText(label.toUpperCase(Locale.ROOT), cx, cy + 46 * density, p);
            p.setLetterSpacing(0f);

            // confetti
            if (burstAt > 0) {
                float t = (System.currentTimeMillis() - burstAt) / 1600f;
                if (t >= 1f) {
                    burstAt = 0;
                } else {
                    for (int i = 0; i < bx.length; i++) {
                        float dist = bv[i] * t;
                        float x = cx + (float) Math.cos(ba[i]) * dist;
                        float y = cy + (float) Math.sin(ba[i]) * dist + 120 * density * t * t;
                        p.setColor((bc[i] & 0x00FFFFFF) | (Math.round(255 * (1f - t)) << 24));
                        c.save();
                        c.rotate(t * 360f * (1 + bx[i]), x, y);
                        c.drawRect(x - 4 * density, y - 2 * density, x + 4 * density, y + 2 * density, p);
                        c.restore();
                    }
                    postInvalidateOnAnimation();
                }
            }
            p.setTextAlign(Paint.Align.LEFT);
        }
    }

    // ------------------------------------------------------------------ buttons

    /** Round control with a drawn glyph: reset, play, pause or skip. */
    private final class Glyph extends View {
        static final int RESET = 0;
        static final int PLAY = 1;
        static final int PAUSE = 2;
        static final int SKIP = 3;
        private final int kind;
        private final boolean primary;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final RectF r = new RectF();

        Glyph(int kind, boolean primary) {
            super(PomodoroActivity.this);
            this.kind = kind;
            this.primary = primary;
            setClickable(true);
        }

        @Override
        protected void onDraw(Canvas c) {
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float rad = Math.min(cx, cy) - 1 * density;
            p.setStyle(Paint.Style.FILL);
            p.setColor(primary ? phaseColor() : Pal.card);
            c.drawCircle(cx, cy, rad, p);
            int ink = primary ? Pal.onAccent : Pal.ink;
            p.setColor(ink);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            float u = rad / 28f; // glyphs drawn on a 28-unit radius
            switch (kind) {
                case PLAY:
                    path.reset();
                    path.moveTo(cx - 8 * u, cy - 12 * u);
                    path.lineTo(cx + 13 * u, cy);
                    path.lineTo(cx - 8 * u, cy + 12 * u);
                    path.close();
                    c.drawPath(path, p);
                    break;
                case PAUSE:
                    r.set(cx - 10 * u, cy - 11 * u, cx - 3 * u, cy + 11 * u);
                    c.drawRoundRect(r, 2 * u, 2 * u, p);
                    r.set(cx + 3 * u, cy - 11 * u, cx + 10 * u, cy + 11 * u);
                    c.drawRoundRect(r, 2 * u, 2 * u, p);
                    break;
                case SKIP:
                    path.reset();
                    path.moveTo(cx - 10 * u, cy - 10 * u);
                    path.lineTo(cx + 6 * u, cy);
                    path.lineTo(cx - 10 * u, cy + 10 * u);
                    path.close();
                    c.drawPath(path, p);
                    r.set(cx + 8 * u, cy - 10 * u, cx + 12 * u, cy + 10 * u);
                    c.drawRoundRect(r, 2 * u, 2 * u, p);
                    break;
                default: // reset: open circle arrow
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(2.6f * u);
                    r.set(cx - 10 * u, cy - 10 * u, cx + 10 * u, cy + 10 * u);
                    c.drawArc(r, -60, 300, false, p);
                    p.setStyle(Paint.Style.FILL);
                    path.reset();
                    path.moveTo(cx + 5 * u, cy - 15 * u);
                    path.lineTo(cx + 5 * u, cy - 4 * u);
                    path.lineTo(cx + 15 * u, cy - 9 * u);
                    path.close();
                    c.drawPath(path, p);
                    break;
            }
        }
    }

    // ------------------------------------------------------------------ stats bars

    private final class Bars extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();

        Bars() {
            super(PomodoroActivity.this);
        }

        @Override
        protected void onDraw(Canvas c) {
            int[] mins = new int[7];
            int max = 1;
            for (int i = 0; i < 7; i++) {
                mins[i] = Pomo.stat(PomodoroActivity.this, 6 - i)[1];
                max = Math.max(max, mins[i]);
            }
            float w = getWidth();
            float h = getHeight();
            float slot = w / 7f;
            float barW = Math.min(slot * 0.55f, 28 * density);
            float label = 18 * density;
            Calendar cal = Calendar.getInstance();
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Fonts.face(true));
            for (int i = 0; i < 7; i++) {
                cal.setTimeInMillis(System.currentTimeMillis());
                cal.add(Calendar.DAY_OF_YEAR, -(6 - i));
                float cx = slot * i + slot / 2f;
                float bh = Math.max(4 * density, (h - label - 14 * density) * mins[i] / max);
                boolean today = i == 6;
                p.setStyle(Paint.Style.FILL);
                p.setColor(mins[i] == 0 ? ((Pal.muted & 0x00FFFFFF) | 0x26000000)
                        : today ? Pal.accent : ((Pal.accent & 0x00FFFFFF) | 0x80000000));
                r.set(cx - barW / 2, h - label - bh, cx + barW / 2, h - label);
                c.drawRoundRect(r, 6 * density, 6 * density, p);
                p.setTextSize(11 * density);
                p.setColor(today ? Pal.ink : Pal.muted);
                String d = cal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.getDefault());
                c.drawText(d.substring(0, 1).toUpperCase(Locale.ROOT), cx, h - 3 * density, p);
                if (mins[i] > 0) {
                    p.setColor(Pal.muted);
                    p.setTextSize(10 * density);
                    c.drawText(String.valueOf(mins[i]), cx, h - label - bh - 4 * density, p);
                }
            }
        }
    }
}
