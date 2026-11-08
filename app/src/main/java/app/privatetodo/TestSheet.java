package app.privatetodo;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Test mode for the full-screen alert and the peek: a small dot at the bottom of the screen. Tap it and a panel
 * rises from the bottom with the options, and every change shows on the screen behind it straight away.
 */
final class TestSheet {
    interface IntCb {
        void on(int v);
    }

    interface BoolCb {
        void on(boolean v);
    }

    private final Context c;
    private final float d;
    private final FrameLayout host;
    private final LinearLayout body;
    private final ScrollView scroll;
    private final View dot;
    private boolean open;

    private TestSheet(FrameLayout host) {
        this.host = host;
        this.c = host.getContext();
        this.d = c.getResources().getDisplayMetrics().density;

        dot = new View(c);
        GradientDrawable dg = new GradientDrawable();
        dg.setShape(GradientDrawable.OVAL);
        dg.setColor(0xCCFFFFFF);
        dot.setBackground(dg);
        dot.setContentDescription("Test options");
        FrameLayout hit = new FrameLayout(c); // a bigger touch area than the dot itself
        hit.addView(dot, new FrameLayout.LayoutParams(px(10), px(10), Gravity.CENTER));
        hit.setOnClickListener(v -> toggle());
        FrameLayout.LayoutParams hl = new FrameLayout.LayoutParams(px(64), px(44), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        hl.bottomMargin = px(14);
        host.addView(hit, hl);
        dot.setTag(hit);

        body = new LinearLayout(c);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(px(18), px(8), px(18), px(24));
        scroll = new ScrollView(c);
        scroll.addView(body, new ViewGroup.LayoutParams(-1, -2));
        GradientDrawable sg = new GradientDrawable();
        sg.setColor(0xF2181A1F);
        sg.setCornerRadii(new float[]{px(26), px(26), px(26), px(26), 0, 0, 0, 0});
        scroll.setBackground(sg);
        scroll.setVisibility(View.INVISIBLE);
        scroll.setClickable(true);
        int h = Math.round(c.getResources().getDisplayMetrics().heightPixels * 0.5f);
        host.addView(scroll, new FrameLayout.LayoutParams(-1, h, Gravity.BOTTOM));
    }

    /** Adds the dot and the (closed) panel on top of everything in host. */
    static TestSheet attach(FrameLayout host) {
        return new TestSheet(host);
    }

    LinearLayout content() {
        return body;
    }

    private int px(int dp) {
        return Math.round(dp * d);
    }

    boolean isOpen() {
        return open;
    }

    void toggle() {
        if (open) close(); else open();
    }

    void open() {
        if (open) return;
        open = true;
        scroll.setVisibility(View.VISIBLE);
        scroll.setTranslationY(scroll.getLayoutParams().height);
        scroll.animate().translationY(0).setDuration(260).start();
        ((View) dot.getTag()).animate().alpha(0f).setDuration(160).start();
    }

    void close() {
        if (!open) return;
        open = false;
        scroll.animate().translationY(scroll.getHeight()).setDuration(220).withEndAction(() -> scroll.setVisibility(View.INVISIBLE)).start();
        ((View) dot.getTag()).animate().alpha(1f).setDuration(200).start();
    }

    // ---- building blocks, in the dark look of the alert screens

    TextView heading(String s) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextColor(0xFFFFFFFF);
        t.setTextSize(15);
        t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        t.setPadding(0, px(16), 0, px(6));
        body.addView(t);
        return t;
    }

    private void paint(TextView t, boolean on) {
        t.setTextColor(on ? 0xFF101114 : 0xFFC8CCD4);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(px(18));
        g.setColor(on ? 0xFF8FD6C8 : 0xFF2A2D34);
        t.setBackground(g);
    }

    /** A wrapping row of single-choice chips. Returns a function-like repaint via the callback. */
    void chips(String[] names, int cur, IntCb cb) {
        final TextView[] cs = new TextView[names.length];
        FlowLayout row = new FlowLayout(c, px(6), px(6));
        for (int i = 0; i < names.length; i++) {
            final int k = i;
            TextView t = new TextView(c);
            t.setText(names[i]);
            t.setTextSize(13);
            t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            t.setGravity(Gravity.CENTER);
            t.setPadding(px(13), 0, px(13), 0);
            cs[i] = t;
            paint(t, i == cur);
            t.setOnClickListener(v -> {
                for (int j = 0; j < cs.length; j++) paint(cs[j], j == k);
                cb.on(k);
            });
            row.addView(t, new ViewGroup.LayoutParams(-2, px(36)));
        }
        body.addView(row);
    }

    /** A 60..140 percent slider. */
    void slider(String label, int pct, IntCb cb) {
        LinearLayout head = new LinearLayout(c);
        TextView l = new TextView(c);
        l.setText(label);
        l.setTextColor(0xFFFFFFFF);
        l.setTextSize(14);
        l.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        head.addView(l, new LinearLayout.LayoutParams(0, -2, 1f));
        final TextView v = new TextView(c);
        v.setText(pct + "%");
        v.setTextColor(0xFFB7C2BF);
        v.setTextSize(13);
        head.addView(v);
        head.setPadding(0, px(14), 0, 0);
        body.addView(head);
        PillSlider bar = new PillSlider(c);
        bar.setIcon(PillSlider.ICON_SIZE);
        bar.setValue(Math.round((pct - 60) / 0.8f));
        bar.setListener(new PillSlider.Listener() {
            @Override
            public void onChange(int value) {
                int p = 60 + Math.round(value * 0.8f);
                v.setText(p + "%");
                cb.on(p);
            }

            @Override
            public void onStart() {
            }

            @Override
            public void onStop() {
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, px(44));
        lp.topMargin = px(4);
        body.addView(bar, lp);
    }

    void toggle(String label, boolean on, BoolCb cb) {
        final boolean[] state = {on};
        final TextView t = new TextView(c);
        t.setTextSize(14);
        t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(px(2), 0, px(2), 0);
        final Runnable draw = () -> {
            t.setText(label + "   " + (state[0] ? "On" : "Off"));
            t.setTextColor(state[0] ? 0xFF8FD6C8 : 0xFF8A8F99);
        };
        draw.run();
        t.setOnClickListener(v -> {
            state[0] = !state[0];
            draw.run();
            cb.on(state[0]);
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, px(44));
        body.addView(t, lp);
    }

    /** Chips whose font is drawn in that font: slot 0 = the screen's own, then every note font. */
    void fontChips(int curIndex, IntCb cb) {
        int n = NoteFonts.count();
        final java.util.List<TextView> cs = new java.util.ArrayList<>();
        final java.util.List<Integer> ids = new java.util.ArrayList<>();
        FlowLayout row = new FlowLayout(c, px(6), px(6));
        for (int i = -1; i < n; i++) {
            String nm = i < 0 ? "Default" : NoteFonts.name(i);
            if (nm == null || nm.startsWith("(")) continue;
            final int slot = i;
            TextView t = new TextView(c);
            t.setText(nm);
            t.setTextSize(13);
            if (i >= 0) t.setTypeface(NoteFonts.face(i));
            t.setGravity(Gravity.CENTER);
            t.setPadding(px(13), 0, px(13), 0);
            paint(t, slot == curIndex);
            cs.add(t);
            ids.add(slot);
            t.setOnClickListener(v -> {
                for (int j = 0; j < cs.size(); j++) paint(cs.get(j), ids.get(j) == slot);
                cb.on(slot);
            });
            row.addView(t, new ViewGroup.LayoutParams(-2, px(36)));
        }
        body.addView(row);
    }
}
