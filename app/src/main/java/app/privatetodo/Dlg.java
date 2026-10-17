package app.privatetodo;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

/** Makes every dialog match the design: bottom-anchored rounded 28dp card, big bold title, accent text buttons. */
final class Dlg {
    private Dlg() { }

    /** AlertDialog.Builder whose show() applies the app styling. */
    static final class Builder extends AlertDialog.Builder {
        Builder(Context c) {
            super(c);
        }

        @Override
        public AlertDialog show() {
            AlertDialog d = create();
            d.show();
            style(d);
            return d;
        }
    }

    /** Call after show(). */
    static void style(AlertDialog d) {
        Context c = d.getContext();
        float dn = c.getResources().getDisplayMetrics().density;
        Window w = d.getWindow();
        if (w != null) {
            GradientDrawable g = new GradientDrawable();
            g.setColor(Pal.raised);
            g.setCornerRadius(28 * dn);
            w.setBackgroundDrawable(new InsetDrawable(g, 0));
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setDimAmount(0.55f);
            DisplayMetrics m = c.getResources().getDisplayMetrics();
            w.setLayout(m.widthPixels - Math.round(24 * dn), WindowManager.LayoutParams.WRAP_CONTENT);
            // Sits at the bottom of the screen for one-handed use, and rides up with the keyboard.
            w.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.y = Math.round(12 * dn);
            lp.softInputMode = (lp.softInputMode & ~WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST)
                    | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
            w.setAttributes(lp);
        }
        int titleId = c.getResources().getIdentifier("alertTitle", "id", "android");
        TextView title = titleId != 0 ? (TextView) d.findViewById(titleId) : null;
        if (title != null) {
            title.setTextSize(24);
            title.setTypeface(Typeface.DEFAULT_BOLD);
            title.setTextColor(Pal.ink);
        }
        TextView msg = (TextView) d.findViewById(android.R.id.message);
        if (msg != null) {
            msg.setTextSize(16);
            msg.setTextColor(Pal.muted);
        }
        int[] which = {AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL};
        for (int k : which) {
            Button b = d.getButton(k);
            if (b == null) continue;
            b.setAllCaps(false);
            b.setTextSize(16);
            b.setTypeface(Typeface.DEFAULT_BOLD);
            b.setTextColor(Pal.accent);
            b.setMinHeight(Math.round(48 * dn));
        }
        if (w != null) Fonts.apply(w.getDecorView());
    }
}
