package app.privatetodo;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Random;

/**
 * Which look / animation the app uses for the four things that can be restyled in the Design lab:
 * the time dials, the slide-left (delete) animation, the slide-right (done) animation and the main-screen title.
 * Each one is stored as DEFAULT (how the app always looked), RANDOM (a different one every time) or the
 * number of one of the lab's styles.
 */
final class AnimPrefs {
    private AnimPrefs() { }

    static final int DIAL = 0;
    static final int SWIPE = 1;
    static final int TICK = 2;
    static final int TITLE = 3;

    static final int DEFAULT = -1;
    static final int RANDOM = -2;

    private static final String[] KEYS = {"use_dial", "use_swipe", "use_tick", "use_title"};
    private static final int[] COUNT = {30, 30, 30, 20};
    private static final int[] rolled = {-1, -1, -1, -1};
    private static final Random rng = new Random();

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("lab", Context.MODE_PRIVATE);
    }

    static int count(int kind) {
        return COUNT[kind];
    }

    /** DEFAULT, RANDOM or a style number, exactly as saved. */
    static int mode(Context c, int kind) {
        int v = sp(c).getInt(KEYS[kind], DEFAULT);
        if (v >= COUNT[kind]) return DEFAULT;
        return v < RANDOM ? DEFAULT : v;
    }

    static void setMode(Context c, int kind, int v) {
        sp(c).edit().putInt(KEYS[kind], v).apply();
        rolled[kind] = -1;
    }

    /** The style to use right now: DEFAULT (-1) or 0..count-1; RANDOM is turned into one of the styles. */
    static int pick(Context c, int kind) {
        int m = mode(c, kind);
        if (m == RANDOM) return rng.nextInt(COUNT[kind]);
        return m;
    }

    /** Like {@link #pick} but the same answer until {@link #reroll} is called (one look for all columns of a dial). */
    static int pickHeld(Context c, int kind) {
        int m = mode(c, kind);
        if (m != RANDOM) return m;
        if (rolled[kind] < 0) rolled[kind] = rng.nextInt(COUNT[kind]);
        return rolled[kind];
    }

    static void reroll(int kind) {
        rolled[kind] = -1;
    }
}
