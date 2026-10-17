package app.privatetodo;

import android.os.Build;
import android.view.HapticFeedbackConstants;
import android.view.View;

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
}
