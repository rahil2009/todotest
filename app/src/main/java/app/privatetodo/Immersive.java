package app.privatetodo;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.view.DisplayCutout;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

/**
 * True full screen for the alarm and water screens: the status bar and the navigation bar are hidden (a swipe
 * from the edge shows them for a moment), and the picture runs under the camera cut-out instead of leaving
 * black bands at the top and bottom. Content is only kept clear of the cut-out itself.
 */
final class Immersive {
    private Immersive() { }

    /** Call in onCreate, before or after setContentView. {@code bg} paints any strip the content does not cover. */
    static void apply(Activity a, int bg) {
        Window w = a.getWindow();
        w.setBackgroundDrawable(new ColorDrawable(bg));
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.layoutInDisplayCutoutMode = Build.VERSION.SDK_INT >= 30
                    ? WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                    : WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            w.setAttributes(lp);
        }
        if (Build.VERSION.SDK_INT >= 30) w.setDecorFitsSystemWindows(false);
        View content = w.getDecorView().findViewById(android.R.id.content);
        if (content != null && Build.VERSION.SDK_INT >= 28) {
            content.setOnApplyWindowInsetsListener((v, ins) -> {
                DisplayCutout dc = ins.getDisplayCutout();
                if (dc != null) {
                    v.setPadding(dc.getSafeInsetLeft(), dc.getSafeInsetTop(), dc.getSafeInsetRight(), dc.getSafeInsetBottom());
                } else {
                    v.setPadding(0, 0, 0, 0);
                }
                return ins;
            });
            content.requestApplyInsets();
        }
        hideBars(a);
    }

    /** Call from onWindowFocusChanged(true): the bars come back after a dialog or a swipe from the edge. */
    static void hideBars(Activity a) {
        Window w = a.getWindow();
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = w.getInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.systemBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        }
    }
}
