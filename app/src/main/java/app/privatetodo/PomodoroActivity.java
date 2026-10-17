package app.privatetodo;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.SweepGradient;
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
            handler.postDelayed(this, Pomo.running(PomodoroActivity.this) ? 100 : 500);
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
        if (ring != null) ring.set(Pomo.left(this), Pomo.endAt(this), Pomo.total(this), phaseColor(),
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
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(dp(310), dp(310));
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

    /**
     * The clock face. A soft disc with 120 hair-line ticks, a gradient arc that glows, a comet of bright ticks
     * and a halo head travelling with the time, ripples and drifting sparks while it runs, and digits that
     * roll over like a flip clock. Everything is time-based, so it stays smooth at any frame rate.
     */
    private final class Ring extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private final Matrix mx = new Matrix();
        private final long t0 = System.nanoTime();
        private long leftFixed;
        private long endAt;
        private long total = 1;
        private int color = Pal.accent;
        private String label = "";
        private boolean running;
        private long burstAt;
        private String shown = "";
        private final long[] changedAt = new long[5];
        private final char[] prev = new char[5];
        private final float[] bx = new float[40];
        private final float[] ba = new float[40];
        private final float[] bv = new float[40];
        private final float[] bs = new float[40];
        private final int[] bc = new int[40];
        private final float[] ox = new float[11];
        private final float[] oy = new float[11];
        private final float[] os = new float[11];

        Ring() {
            super(PomodoroActivity.this);
            Random rnd = new Random(11);
            for (int i = 0; i < ox.length; i++) {
                ox[i] = rnd.nextFloat() * 6.28f;
                oy[i] = 0.25f + rnd.nextFloat() * 0.6f;
                os[i] = (0.08f + rnd.nextFloat() * 0.22f) * (rnd.nextBoolean() ? 1 : -1);
            }
        }

        void set(long leftNow, long end, long total, int color, String label, boolean running) {
            this.leftFixed = leftNow;
            this.endAt = end;
            this.total = Math.max(1, total);
            this.color = color;
            this.label = label;
            this.running = running;
            invalidate();
        }

        void burst() {
            Random rnd = new Random();
            int[] pal = {Pal.accent, BLUE, VIOLET, 0xFFFF6BB5, 0xFFFFB86B, 0xFFFFFFFF};
            for (int i = 0; i < bx.length; i++) {
                ba[i] = (float) (rnd.nextFloat() * Math.PI * 2);
                bv[i] = (70 + rnd.nextFloat() * 190) * density;
                bx[i] = rnd.nextFloat() * 6f;
                bs[i] = 0.6f + rnd.nextFloat() * 0.9f;
                bc[i] = pal[rnd.nextInt(pal.length)];
            }
            burstAt = System.currentTimeMillis();
        }

        private long leftNow() {
            return running ? Math.max(0, endAt - System.currentTimeMillis()) : leftFixed;
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth();
            float cx = w / 2f;
            float cy = getHeight() / 2f;
            float d = density;
            float t = (System.nanoTime() - t0) / 1e9f;
            long left = leftNow();
            float rem = Math.max(0f, Math.min(1f, left / (float) total));
            float breath = running ? 0.5f + 0.5f * (float) Math.sin(t * 2.2f) : 0.3f;
            float outer = w / 2f - 10 * d;
            int light = HabitUi.blend(color, 0xFFFFFFFF, 0.55f);

            // ambient glow behind everything
            p.setStyle(Paint.Style.FILL);
            p.setShader(new RadialGradient(cx, cy, outer * 1.18f,
                    new int[]{HabitUi.alpha(color, Math.round((running ? 70 : 38) + 30 * breath)), 0x00000000},
                    new float[]{0.55f, 1f}, Shader.TileMode.CLAMP));
            c.drawCircle(cx, cy, outer * 1.18f, p);
            // the disc the clock sits on
            p.setShader(new RadialGradient(cx - outer * 0.25f, cy - outer * 0.3f, outer * 1.4f,
                    new int[]{HabitUi.blend(Pal.card, 0xFFFFFFFF, Pal.dark ? 0.90f : 1f),
                            HabitUi.blend(Pal.card, Pal.bg, 0.55f)}, null, Shader.TileMode.CLAMP));
            c.drawCircle(cx, cy, outer - 2 * d, p);
            p.setShader(null);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1f * d);
            p.setColor(HabitUi.alpha(Pal.ink, Pal.dark ? 0x22 : 0x14));
            c.drawCircle(cx, cy, outer - 2 * d, p);

            // 120 hair-line ticks; lit ones follow the time, with a bright comet at the head
            p.setStrokeCap(Paint.Cap.ROUND);
            float lit = rem * 120f;
            for (int i = 0; i < 120; i++) {
                double a = -Math.PI / 2 + i * Math.PI * 2 / 120;
                boolean major = i % 10 == 0;
                boolean on = i < lit;
                float gap = lit - i; // ticks behind the head
                float comet = running && on ? Math.max(0f, 1f - gap / 16f) : 0f;
                float len = (major ? 9f : 5f) * d + comet * 6f * d;
                p.setStrokeWidth((major ? 2.2f : 1.4f) * d + comet * 1.2f * d);
                if (on) p.setColor(HabitUi.alpha(HabitUi.blend(light, color, comet), Math.round(150 + 105 * comet)));
                else p.setColor(HabitUi.alpha(Pal.muted, major ? 0x55 : 0x2E));
                float r0 = outer - 10 * d;
                c.drawLine(cx + (float) Math.cos(a) * r0, cy + (float) Math.sin(a) * r0,
                        cx + (float) Math.cos(a) * (r0 - len), cy + (float) Math.sin(a) * (r0 - len), p);
            }

            // gradient arc with a layered glow
            float arcR = outer - 38 * d;
            r.set(cx - arcR, cy - arcR, cx + arcR, cy + arcR);
            p.setStrokeWidth(11 * d);
            p.setColor(HabitUi.alpha(color, 0x22));
            c.drawCircle(cx, cy, arcR, p);
            float sweep = 360f * rem;
            if (sweep > 0.5f) {
                SweepGradient sg = new SweepGradient(cx, cy, new int[]{light, color, HabitUi.blend(color, light, 0.5f), light},
                        new float[]{0f, 0.45f, 0.8f, 1f});
                mx.setRotate(-90, cx, cy);
                sg.setLocalMatrix(mx);
                p.setShader(sg);
                for (int g = 3; g >= 1; g--) { // glow: wider, fainter copies of the arc
                    p.setStrokeWidth((11 + g * 7) * d);
                    p.setAlpha(Math.round((12 + 10 * breath) * (4 - g)));
                    c.drawArc(r, -90, sweep, false, p);
                }
                p.setStrokeWidth(11 * d);
                p.setAlpha(255);
                c.drawArc(r, -90, sweep, false, p);
                p.setShader(null);
            }
            if (rem > 0f && rem < 1f) { // halo head
                double a = Math.toRadians(-90 + sweep);
                float hx = cx + (float) Math.cos(a) * arcR;
                float hy = cy + (float) Math.sin(a) * arcR;
                p.setStyle(Paint.Style.FILL);
                p.setShader(new RadialGradient(hx, hy, (20 + 8 * breath) * d,
                        new int[]{HabitUi.alpha(light, 0xB0), 0x00000000}, null, Shader.TileMode.CLAMP));
                c.drawCircle(hx, hy, (20 + 8 * breath) * d, p);
                p.setShader(null);
                p.setColor(0xFFFFFFFF);
                c.drawCircle(hx, hy, 5.5f * d, p);
                p.setColor(color);
                c.drawCircle(hx, hy, 3f * d, p);
            }

            float inner = arcR - 14 * d;
            if (running) {
                // ripples leaving the centre
                p.setStyle(Paint.Style.STROKE);
                for (int i = 0; i < 2; i++) {
                    float ph = ((t / 3.6f) + i * 0.5f) % 1f;
                    p.setStrokeWidth((1.5f + ph * 2f) * d);
                    p.setColor(HabitUi.alpha(color, Math.round(70 * (1f - ph) * (1f - ph))));
                    c.drawCircle(cx, cy, inner * (0.35f + 0.65f * ph), p);
                }
                // drifting sparks
                p.setStyle(Paint.Style.FILL);
                for (int i = 0; i < ox.length; i++) {
                    float ang = ox[i] + t * os[i];
                    float rr = inner * oy[i];
                    float tw = (float) Math.sin(t * (1.2f + i * 0.17f) + ox[i]);
                    float a2 = 0.2f + 0.8f * tw * tw;
                    float x = cx + (float) Math.cos(ang) * rr;
                    float y = cy + (float) Math.sin(ang) * rr;
                    p.setColor(HabitUi.alpha(light, Math.round(28 * a2)));
                    c.drawCircle(x, y, 5f * d, p);
                    p.setColor(HabitUi.alpha(0xFFFFFFFF, Math.round(180 * a2)));
                    c.drawCircle(x, y, 1.4f * d, p);
                }
            }

            drawDigits(c, cx, cy, left, t, d);

            // caption under the time
            p.setStyle(Paint.Style.FILL);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Fonts.face(true));
            p.setLetterSpacing(0.16f);
            p.setTextSize(12.5f * d);
            p.setColor(color);
            c.drawText(label.toUpperCase(Locale.ROOT), cx, cy + 48 * d, p);
            p.setLetterSpacing(0f);
            p.setTypeface(Fonts.face(false));
            p.setTextSize(12f * d);
            p.setColor(Pal.muted);
            String sub = running
                    ? "ends " + android.text.format.DateFormat.getTimeFormat(PomodoroActivity.this)
                            .format(new java.util.Date(endAt))
                    : "paused";
            c.drawText(sub, cx, cy + 68 * d, p);

            // confetti
            if (burstAt > 0) {
                float tt = (System.currentTimeMillis() - burstAt) / 1800f;
                if (tt >= 1f) {
                    burstAt = 0;
                } else {
                    for (int i = 0; i < bx.length; i++) {
                        float ease = 1f - (1f - tt) * (1f - tt);
                        float dist = bv[i] * ease;
                        float x = cx + (float) Math.cos(ba[i]) * dist;
                        float y = cy + (float) Math.sin(ba[i]) * dist + 150 * d * tt * tt;
                        p.setColor(HabitUi.alpha(bc[i], Math.round(255 * (1f - tt))));
                        c.save();
                        c.rotate(tt * 420f * (1 + bx[i]), x, y);
                        c.drawRoundRect(x - 4.5f * d * bs[i], y - 2f * d * bs[i], x + 4.5f * d * bs[i],
                                y + 2f * d * bs[i], d, d, p);
                        c.restore();
                    }
                }
            }
            p.setTextAlign(Paint.Align.LEFT);
            if (running || burstAt > 0) postInvalidateOnAnimation();
        }

        /** MM:SS in fixed-width digits. A digit that changes slides up and fades while the new one slides in. */
        private void drawDigits(Canvas c, float cx, float cy, long left, float t, float d) {
            long secs = (left + 999) / 1000;
            String time = String.format(Locale.US, "%02d:%02d", secs / 60, secs % 60);
            long now = System.currentTimeMillis();
            if (!time.equals(shown)) {
                for (int i = 0; i < 5; i++) {
                    if (shown.length() == 5 && shown.charAt(i) != time.charAt(i)) {
                        prev[i] = shown.charAt(i);
                        changedAt[i] = now;
                    }
                }
                shown = time;
            }
            p.setStyle(Paint.Style.FILL);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Fonts.face(true));
            p.setTextSize(56 * d);
            p.setLetterSpacing(0f);
            float dw = p.measureText("0");
            float colon = p.measureText(":") * 0.8f;
            float total = dw * 4 + colon;
            float x0 = cx - total / 2f;
            Paint.FontMetrics fm = p.getFontMetrics();
            float base = cy - 2 * d - (fm.ascent + fm.descent) / 2f;
            float roll = 34 * d;
            float[] xs = {x0 + dw / 2f, x0 + dw * 1.5f, x0 + dw * 2f + colon / 2f, x0 + dw * 2.5f + colon,
                    x0 + dw * 3.5f + colon};
            for (int i = 0; i < 5; i++) {
                char ch = time.charAt(i);
                if (i == 2) { // colon pulses while running
                    p.setColor(HabitUi.alpha(Pal.ink, running ? Math.round(110 + 145 * (0.5f + 0.5f * (float) Math.sin(t * 3.1f))) : 255));
                    c.drawText(":", xs[i], base - 3 * d, p);
                    continue;
                }
                float q = Math.min(1f, (now - changedAt[i]) / 260f);
                if (q < 1f && prev[i] != 0) {
                    float e = 1f - (1f - q) * (1f - q) * (1f - q);
                    p.setColor(HabitUi.alpha(Pal.ink, Math.round(255 * (1f - e))));
                    c.drawText(String.valueOf(prev[i]), xs[i], base - roll * e, p);
                    p.setColor(HabitUi.alpha(Pal.ink, Math.round(255 * e)));
                    c.drawText(String.valueOf(ch), xs[i], base + roll * (1f - e), p);
                } else {
                    p.setColor(Pal.ink);
                    c.drawText(String.valueOf(ch), xs[i], base, p);
                }
            }
            if (now - changedAt[0] < 300 || now - changedAt[1] < 300 || now - changedAt[3] < 300
                    || now - changedAt[4] < 300) postInvalidateOnAnimation();
        }
    }

    // ------------------------------------------------------------------ buttons

    /** Round control with a drawn glyph: reset, play, pause or skip. The main one glows while the timer runs. */
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
        private final long t0 = System.nanoTime();

        Glyph(int kind, boolean primary) {
            super(PomodoroActivity.this);
            this.kind = kind;
            this.primary = primary;
            setClickable(true);
            setOnTouchListener((v, e) -> {
                int a = e.getActionMasked();
                if (a == android.view.MotionEvent.ACTION_DOWN) {
                    v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(90).start();
                } else if (a == android.view.MotionEvent.ACTION_UP || a == android.view.MotionEvent.ACTION_CANCEL) {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(200)
                            .setInterpolator(new android.view.animation.OvershootInterpolator(3f)).start();
                }
                return false;
            });
        }

        @Override
        protected void onDraw(Canvas c) {
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float rad = Math.min(cx, cy) - 5 * density;
            float t = (System.nanoTime() - t0) / 1e9f;
            int col = phaseColor();
            p.setStyle(Paint.Style.FILL);
            if (primary) {
                if (kind == PAUSE) { // a slow halo while counting down
                    float ph = (t / 2.2f) % 1f;
                    p.setColor(HabitUi.alpha(col, Math.round(90 * (1f - ph))));
                    c.drawCircle(cx, cy, rad + 4 * density + ph * 8 * density, p);
                }
                p.setShader(new LinearGradient(0, cy - rad, 0, cy + rad,
                        HabitUi.blend(col, 0xFFFFFFFF, 0.78f), col, Shader.TileMode.CLAMP));
                c.drawCircle(cx, cy, rad, p);
                p.setShader(null);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(1.2f * density);
                p.setColor(0x44FFFFFF);
                c.drawCircle(cx, cy, rad - 1.5f * density, p);
                p.setStyle(Paint.Style.FILL);
            } else {
                p.setColor(Pal.card);
                c.drawCircle(cx, cy, rad, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(1f * density);
                p.setColor(HabitUi.alpha(Pal.ink, 0x1A));
                c.drawCircle(cx, cy, rad - 0.5f * density, p);
                p.setStyle(Paint.Style.FILL);
            }
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
                    p.setStrokeWidth(4 * u);
                    p.setStyle(Paint.Style.FILL_AND_STROKE);
                    c.drawPath(path, p);
                    break;
                case PAUSE:
                    r.set(cx - 10 * u, cy - 11 * u, cx - 3 * u, cy + 11 * u);
                    c.drawRoundRect(r, 3 * u, 3 * u, p);
                    r.set(cx + 3 * u, cy - 11 * u, cx + 10 * u, cy + 11 * u);
                    c.drawRoundRect(r, 3 * u, 3 * u, p);
                    break;
                case SKIP:
                    path.reset();
                    path.moveTo(cx - 10 * u, cy - 10 * u);
                    path.lineTo(cx + 6 * u, cy);
                    path.lineTo(cx - 10 * u, cy + 10 * u);
                    path.close();
                    p.setStrokeWidth(3 * u);
                    p.setStyle(Paint.Style.FILL_AND_STROKE);
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
            if (primary && kind == PAUSE) postInvalidateOnAnimation();
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
