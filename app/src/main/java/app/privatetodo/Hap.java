package app.privatetodo;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.HapticFeedbackConstants;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/** Small, consistent haptic taps. Respects the phone's own touch-feedback setting. */
final class Hap {
    private Hap() {
    }

    private static void go(View v, int type) {
        if (v != null) v.performHapticFeedback(type, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
    }

    /** Light tick: chips, dial steps, switches, swipe threshold. */
    static void tick(View v) {
        go(v, Build.VERSION.SDK_INT >= 21 ? HapticFeedbackConstants.CLOCK_TICK
                : HapticFeedbackConstants.VIRTUAL_KEY);
    }

    /** Firm tap: a task completed, added, deleted or restored. */
    static void confirm(View v) {
        go(v, Build.VERSION.SDK_INT >= 30 ? HapticFeedbackConstants.CONFIRM
                : HapticFeedbackConstants.VIRTUAL_KEY);
    }

    // ---- completion haptics: Pixel-style compositions of the phone's own motor primitives

    // primitive codes used in the tables below
    private static final int CLICK = 0, THUD = 1, SPIN = 2, Q_RISE = 3, S_RISE = 4, Q_FALL = 5, TICK = 6, L_TICK = 7;

    /** Names shown in Settings. 0 = off, 1 = the standard system confirm tap, the rest are compositions. */
    static final String[] NAMES = {"Off", "Standard", "Soft click", "Crisp tick", "Double tap", "Triple tick",
            "Rising", "Swell & thump", "Deep thud", "Spin", "Staircase", "Heartbeat", "Drumroll", "Pop",
            "Fanfare", "Ripple", "Gentle wave"};

    static final String[] DESCS = {"No vibration when a task is completed.",
            "The phone's standard confirm tap.",
            "One light, rounded click.",
            "One sharp, tiny tick.",
            "Two quick clicks, the second firmer.",
            "Three ticks, getting stronger.",
            "A fast rising buzz that ends in a click.",
            "A slow swell that lands with a thump.",
            "One heavy, deep thump.",
            "A quick rolling spin.",
            "Five steps climbing up.",
            "Lub-dub, like a heartbeat.",
            "A fast roll of ticks, then a thump.",
            "A sharp tick that drops away, like a bubble.",
            "Three clicks and a big finish.",
            "A click that fades out in ripples.",
            "A soft rise and a gentle fall."};

    // each pattern is {primitive, strength 0-100, delay-before-ms} triples
    private static final int[][] PATTERNS = {
            null, null,
            {CLICK, 55, 0},
            {TICK, 100, 0},
            {CLICK, 70, 0, CLICK, 100, 75},
            {TICK, 55, 0, TICK, 80, 55, TICK, 100, 55},
            {Q_RISE, 100, 0, CLICK, 100, 20},
            {S_RISE, 100, 0, THUD, 85, 30},
            {THUD, 100, 0},
            {SPIN, 100, 0},
            {L_TICK, 25, 0, TICK, 40, 50, TICK, 60, 50, CLICK, 80, 50, CLICK, 100, 50},
            {THUD, 90, 0, THUD, 55, 110},
            {TICK, 30, 0, TICK, 40, 35, TICK, 50, 35, TICK, 60, 35, TICK, 75, 35, TICK, 90, 35, THUD, 100, 60},
            {TICK, 100, 0, Q_FALL, 65, 15},
            {CLICK, 60, 0, CLICK, 80, 80, CLICK, 100, 80, THUD, 100, 120},
            {CLICK, 100, 0, TICK, 65, 95, TICK, 45, 95, L_TICK, 30, 95},
            {S_RISE, 55, 0, Q_FALL, 80, 40}};

    /** Settings > Haptics: which pattern plays when a task is completed. */
    static int pick(Context c) {
        return Math.max(0, Math.min(NAMES.length - 1,
                c.getApplicationContext().getSharedPreferences("settings", Context.MODE_PRIVATE).getInt("done_haptic", 1)));
    }

    static void setPick(Context c, int v) {
        c.getApplicationContext().getSharedPreferences("settings", Context.MODE_PRIVATE)
                .edit().putInt("done_haptic", v).apply();
    }

    /** A task was completed: plays the pattern chosen in Settings. */
    static void complete(View v) {
        play(v, pick(v.getContext()));
    }

    /** Plays one pattern (also used by the Settings test list). */
    static void play(View v, int id) {
        if (id <= 0 || id >= NAMES.length) return;
        if (id == 1) {
            confirm(v);
            return;
        }
        try {
            Vibrator vib = (Vibrator) v.getContext().getSystemService(Context.VIBRATOR_SERVICE);
            if (vib == null || !vib.hasVibrator()) {
                confirm(v);
                return;
            }
            int[] pat = PATTERNS[id];
            if (Build.VERSION.SDK_INT >= 30 && playComposition(vib, pat)) return;
            playWaveform(vib, pat);
        } catch (RuntimeException e) {
            confirm(v);
        }
    }

    private static int prim(int code) {
        switch (code) {
            case THUD: return VibrationEffect.Composition.PRIMITIVE_THUD;
            case SPIN: return VibrationEffect.Composition.PRIMITIVE_SPIN;
            case Q_RISE: return VibrationEffect.Composition.PRIMITIVE_QUICK_RISE;
            case S_RISE: return VibrationEffect.Composition.PRIMITIVE_SLOW_RISE;
            case Q_FALL: return VibrationEffect.Composition.PRIMITIVE_QUICK_FALL;
            case TICK: return VibrationEffect.Composition.PRIMITIVE_TICK;
            case L_TICK: return Build.VERSION.SDK_INT >= 31 ? VibrationEffect.Composition.PRIMITIVE_LOW_TICK
                    : VibrationEffect.Composition.PRIMITIVE_TICK;
            default: return VibrationEffect.Composition.PRIMITIVE_CLICK;
        }
    }

    private static boolean playComposition(Vibrator vib, int[] pat) {
        int n = pat.length / 3;
        int[] ids = new int[n];
        for (int i = 0; i < n; i++) ids[i] = prim(pat[i * 3]);
        boolean[] ok = vib.arePrimitivesSupported(ids);
        for (boolean b : ok) if (!b) return false;
        VibrationEffect.Composition comp = VibrationEffect.startComposition();
        for (int i = 0; i < n; i++) {
            comp.addPrimitive(ids[i], pat[i * 3 + 1] / 100f, pat[i * 3 + 2]);
        }
        vib.vibrate(comp.compose());
        return true;
    }

    private static int pulseMs(int code) {
        switch (code) {
            case THUD: return 32;
            case SPIN: return 60;
            case Q_RISE: return 45;
            case S_RISE: return 120;
            case Q_FALL: return 45;
            case TICK: return 8;
            case L_TICK: return 10;
            default: return 18;
        }
    }

    /** Older phones: the same pattern as timed pulses (with strength when the motor allows it). */
    private static void playWaveform(Vibrator vib, int[] pat) {
        List<Long> t = new ArrayList<>();
        List<Integer> a = new ArrayList<>();
        for (int i = 0; i + 2 < pat.length; i += 3) {
            long on = pulseMs(pat[i]);
            long gap = Math.max(0, pat[i + 2] - (i == 0 ? 0 : pulseMs(pat[i - 3])));
            t.add(i == 0 ? 0L : gap);
            a.add(0);
            t.add(on);
            a.add(Math.max(20, Math.min(255, pat[i + 1] * 255 / 100)));
        }
        long[] tt = new long[t.size()];
        int[] aa = new int[a.size()];
        for (int i = 0; i < tt.length; i++) {
            tt[i] = t.get(i);
            aa[i] = a.get(i);
        }
        if (Build.VERSION.SDK_INT >= 26) {
            vib.vibrate(vib.hasAmplitudeControl() ? VibrationEffect.createWaveform(tt, aa, -1)
                    : VibrationEffect.createWaveform(tt, -1));
        }
    }
}
