package app.privatetodo;

import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** The "New folder" dialog (name + colour), the same one the notebook uses; Shared notes uses it too. */
final class FolderDlg extends ContextWrapper {
    interface Save {
        void save(String name, int color);
    }

    private final float density;

    FolderDlg(Context activity) {
        super(activity);
        density = activity.getResources().getDisplayMetrics().density;
    }

    private int dp(int v) {
        return Math.round(v * density);
    }

    /** A small hue + shade picker for a folder's own colour. */
    private void customColor(int initial, final java.util.function.IntConsumer done) {
        final float[] hsv = new float[3];
        android.graphics.Color.colorToHSV(initial, hsv);
        if (hsv[1] < 0.3f) hsv[1] = 0.75f; // greys and presets start from a vivid base
        hsv[2] = Math.max(0.3f, hsv[2]);
        final View preview = new View(this);
        final Runnable show = () -> {
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(android.graphics.Color.HSVToColor(hsv));
            preview.setBackground(g);
        };
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(8), dp(24), 0);
        box.addView(preview, centeredLp(dp(64), dp(64), dp(4)));
        int[] rainbow = new int[7];
        for (int i = 0; i < 7; i++) rainbow[i] = android.graphics.Color.HSVToColor(new float[]{i * 60f, 0.75f, 1f});
        box.addView(trackLabel("HUE"));
        box.addView(slider(360, (int) hsv[0], rainbow, v -> { hsv[0] = v; show.run(); }),
                new LinearLayout.LayoutParams(-1, dp(36)));
        box.addView(trackLabel("SHADE"));
        box.addView(slider(100, Math.round((hsv[2] - 0.3f) / 0.7f * 100), new int[]{
                        android.graphics.Color.HSVToColor(new float[]{hsv[0], 0.75f, 0.3f}),
                        android.graphics.Color.HSVToColor(new float[]{hsv[0], 0.75f, 1f})},
                v -> { hsv[2] = 0.3f + 0.7f * v / 100f; show.run(); }),
                new LinearLayout.LayoutParams(-1, dp(36)));
        show.run();
        new Dlg.Builder(this)
                .setTitle("Custom colour")
                .setView(box)
                .setPositiveButton("Use colour", (d, w) -> done.accept(android.graphics.Color.HSVToColor(hsv)))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private LinearLayout.LayoutParams centeredLp(int w, int h, int bottom) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        lp.bottomMargin = bottom;
        return lp;
    }

    private TextView trackLabel(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(12);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setLetterSpacing(0.08f);
        t.setTextColor(Pal.muted);
        t.setPadding(0, dp(12), 0, dp(2));
        return t;
    }

    /** A rounded gradient track with a round white thumb. */
    private android.widget.SeekBar slider(int max, int value, int[] colors,
                                          final java.util.function.IntConsumer onChange) {
        android.widget.SeekBar sb = new android.widget.SeekBar(this);
        sb.setMax(max);
        sb.setPadding(dp(14), 0, dp(14), 0);
        GradientDrawable track = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors);
        track.setCornerRadius(dp(8));
        track.setSize(0, dp(14));
        sb.setProgressDrawable(track);
        GradientDrawable thumb = new GradientDrawable();
        thumb.setShape(GradientDrawable.OVAL);
        thumb.setColor(0xFFFFFFFF);
        thumb.setStroke(dp(3), Pal.accent);
        thumb.setSize(dp(28), dp(28));
        sb.setThumb(thumb);
        sb.setProgress(value);
        sb.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(android.widget.SeekBar s, int p, boolean user) {
                onChange.accept(p);
            }
            @Override public void onStartTrackingTouch(android.widget.SeekBar s) { }
            @Override public void onStopTrackingTouch(android.widget.SeekBar s) { }
        });
        return sb;
    }

    /** New folder (existing == null) or rename / recolour. {@code done} gets the folder id once it is saved. */
    void show(final Folder existing, final Save save) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(8), dp(24), 0);
        final EditText name = new EditText(this);
        name.setHint("Folder name");
        name.setHintTextColor(Pal.muted);
        name.setTextColor(Pal.ink);
        name.setTextSize(18);
        name.setSingleLine(true);
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        if (existing != null) name.setText(existing.name);
        box.addView(name, new LinearLayout.LayoutParams(-1, dp(52)));
        final int[] sel = {existing != null ? existing.color : 0};
        final int PRESETS = 7; // seven ready colours + one custom colour button, all on one line
        final View[] dots = new View[PRESETS + 1];
        final Runnable paint = () -> {
            for (int i = 0; i < PRESETS; i++) {
                GradientDrawable g = new GradientDrawable();
                g.setShape(GradientDrawable.OVAL);
                g.setColor(Folder.COLORS[i]);
                g.setStroke(dp(i == sel[0] ? 3 : 1), i == sel[0] ? Pal.ink : Pal.chip);
                dots[i].setBackground(g);
            }
            // the last dot: a rainbow ring until a custom colour is chosen, then that colour
            boolean custom = sel[0] < 0 || sel[0] >= PRESETS;
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            if (custom) {
                g.setColor(Folder.rgbOf(sel[0]));
            } else {
                g.setGradientType(GradientDrawable.SWEEP_GRADIENT);
                g.setColors(new int[]{0xFFFF4D4D, 0xFFFFC94D, 0xFF5EDB6B, 0xFF4DC3FF, 0xFF8E5CFF, 0xFFFF4DC3, 0xFFFF4D4D});
            }
            g.setStroke(dp(custom ? 3 : 1), custom ? Pal.ink : Pal.chip);
            dots[PRESETS].setBackground(g);
        };
        TextView colLabel = new TextView(this);
        colLabel.setText("COLOUR");
        colLabel.setTextSize(12);
        colLabel.setTypeface(Typeface.DEFAULT_BOLD);
        colLabel.setLetterSpacing(0.08f);
        colLabel.setTextColor(Pal.muted);
        colLabel.setPadding(0, dp(16), 0, dp(6));
        box.addView(colLabel);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        for (int i = 0; i <= PRESETS; i++) {
            final int idx = i;
            dots[i] = new View(this) { // always a true circle: height follows width
                @Override
                protected void onMeasure(int w, int h) {
                    super.onMeasure(w, w);
                }
            };
            dots[i].setContentDescription(i < PRESETS ? Folder.COLOR_NAMES[i] : "Custom colour");
            dots[i].setOnClickListener(v -> {
                Hap.tick(v);
                if (idx < PRESETS) {
                    sel[0] = idx;
                    paint.run();
                } else {
                    customColor(Folder.rgbOf(sel[0]), c -> {
                        sel[0] = c;
                        paint.run();
                    });
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
            lp.setMargins(dp(3), 0, dp(3), 0);
            row.addView(dots[i], lp);
        }
        box.addView(row, new LinearLayout.LayoutParams(-1, -2));
        paint.run();
        new Dlg.Builder(this)
                .setTitle(existing == null ? "New folder" : "Edit folder")
                .setView(box)
                .setPositiveButton("Save", (d, w) -> {
                    String nm = name.getText().toString().trim();
                    if (nm.isEmpty()) return;
                    save.save(nm, sel[0]);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
