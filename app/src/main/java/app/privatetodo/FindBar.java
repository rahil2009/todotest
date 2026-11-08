package app.privatetodo;

import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.Layout;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

/**
 * Find and replace for a note: a small panel over the top of the page. Every match is highlighted in the text,
 * the current one stronger; the arrows step through them, Replace swaps the current one, All swaps every one.
 */
final class FindBar extends LinearLayout {
    interface Guard {
        /** Runs a text edit that must not make the page jump. */
        void run(Runnable edit);
    }

    private final RichEditText body;
    private final ScrollView sv;
    private final Guard guard;
    private final float d;
    private final EditText find;
    private final EditText rep;
    private LinearLayout replaceRow;
    private final TextView count;
    private final TextView aa;
    private boolean matchCase;
    private int[] hits = new int[0];
    private int cur = -1;
    private String q = "";

    FindBar(Context c, RichEditText body, ScrollView sv, Guard guard) {
        super(c);
        this.body = body;
        this.sv = sv;
        this.guard = guard;
        this.d = c.getResources().getDisplayMetrics().density;
        setOrientation(VERTICAL);
        int p = px(8);
        setPadding(p, p, p, p);
        GradientDrawable g = new GradientDrawable();
        g.setColor(Pal.raised | 0xFF000000);
        g.setCornerRadius(px(20));
        g.setStroke(Math.max(1, px(1)), Pal.chip);
        setBackground(g);
        setElevation(px(8));
        setClickable(true); // touches on the panel must not fall through to the note

        LinearLayout r1 = row();
        find = field("Find");
        r1.addView(find, new LayoutParams(0, px(42), 1f));
        count = label("", 12, Pal.muted);
        count.setMinWidth(px(44));
        r1.addView(count, new LayoutParams(-2, px(42)));
        aa = label("Aa", 13, Pal.muted);
        aa.setOnClickListener(v -> {
            matchCase = !matchCase;
            style(aa, matchCase);
            search(true);
        });
        r1.addView(aa, new LayoutParams(px(36), px(42)));
        r1.addView(button("▲", v -> step(-1)), new LayoutParams(px(36), px(42)));
        r1.addView(button("▼", v -> step(1)), new LayoutParams(px(36), px(42)));
        r1.addView(button("✕", v -> close()), new LayoutParams(px(36), px(42)));
        addView(r1);

        LinearLayout r2 = row();
        rep = field("Replace with");
        r2.addView(rep, new LayoutParams(0, px(42), 1f));
        TextView one = pill("Replace");
        one.setOnClickListener(v -> replaceOne());
        r2.addView(one, new LayoutParams(-2, px(38)));
        TextView all = pill("All");
        all.setOnClickListener(v -> replaceAll());
        LayoutParams al = new LayoutParams(-2, px(38));
        al.leftMargin = px(6);
        r2.addView(all, al);
        LayoutParams r2lp = new LayoutParams(-1, -2);
        r2lp.topMargin = px(6);
        addView(r2, r2lp);
        replaceRow = r2;

        find.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c2) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c2) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                q = s.toString();
                search(true);
            }
        });
        find.setOnEditorActionListener((v, id, e) -> {
            step(1);
            return true;
        });
        Fonts.apply(this);
    }

    /** Find only: hides the Replace row so the panel is just the search field. */
    void setReplaceVisible(boolean show) {
        replaceRow.setVisibility(show ? VISIBLE : GONE);
    }

    private int px(int dp) {
        return Math.round(dp * d);
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(getContext());
        r.setOrientation(HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        return r;
    }

    private EditText field(String hint) {
        EditText e = new EditText(getContext());
        e.setHint(hint);
        e.setSingleLine(true);
        e.setTextSize(15);
        e.setTextColor(Pal.ink);
        e.setHintTextColor(Pal.muted);
        e.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        e.setPadding(px(12), 0, px(12), 0);
        GradientDrawable g = new GradientDrawable();
        g.setColor(Pal.chip);
        g.setCornerRadius(px(14));
        e.setBackground(g);
        return e;
    }

    private TextView label(String t, int sp, int color) {
        TextView v = new TextView(getContext());
        v.setText(t);
        v.setTextSize(sp);
        v.setTextColor(color);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setGravity(Gravity.CENTER);
        return v;
    }

    private TextView button(String glyph, View.OnClickListener l) {
        TextView v = label(glyph, 14, Pal.ink);
        v.setOnClickListener(x -> {
            x.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
            l.onClick(x);
        });
        return v;
    }

    private TextView pill(String t) {
        TextView v = label(t, 14, Pal.onAccent);
        v.setPadding(px(14), 0, px(14), 0);
        GradientDrawable g = new GradientDrawable();
        g.setColor(Pal.accent);
        g.setCornerRadius(px(19));
        v.setBackground(g);
        return v;
    }

    private void style(TextView v, boolean on) {
        v.setTextColor(on ? Pal.accent : Pal.muted);
    }

    /** Shows the panel and puts the typing cursor in the Find box. */
    void open(FrameLayout area, int topInset) {
        // at the bottom of the page area, which is the line just above the note's tools
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        lp.setMargins(px(10), 0, px(10), px(8));
        area.addView(this, lp);
        // the selected text of the note, if any, is what people usually want to search for
        int a = Math.min(body.getSelectionStart(), body.getSelectionEnd());
        int b = Math.max(body.getSelectionStart(), body.getSelectionEnd());
        if (b > a && b - a < 80) find.setText(body.getText().subSequence(a, b).toString().replace('\n', ' '));
        find.requestFocus();
        find.setSelection(find.length());
        post(() -> {
            InputMethodManager im = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (im != null) im.showSoftInput(find, 0);
        });
    }

    void close() {
        InputMethodManager im = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (im != null) im.hideSoftInputFromWindow(find.getWindowToken(), 0);
        body.setFind(null, 0, -1);
        if (cur >= 0 && cur < hits.length) body.setSelection(Math.min(body.length(), hits[cur]));
        if (getParent() != null) ((android.view.ViewGroup) getParent()).removeView(this);
    }

    boolean isOpen() {
        return getParent() != null;
    }

    // ------------------------------------------------------------------ searching

    private void search(boolean fromCursor) {
        ArrayList<Integer> found = new ArrayList<>();
        String t = body.getText().toString();
        int n = q.length();
        if (n > 0) {
            for (int i = 0; i + n <= t.length(); i++) {
                if (t.regionMatches(!matchCase, i, q, 0, n)) {
                    found.add(i);
                    i += n - 1; // matches never overlap
                }
            }
        }
        hits = new int[found.size()];
        for (int i = 0; i < hits.length; i++) hits[i] = found.get(i);
        cur = -1;
        if (hits.length > 0) {
            int from = fromCursor ? Math.max(0, Math.min(body.getSelectionStart(), body.getSelectionEnd())) : 0;
            cur = 0;
            for (int i = 0; i < hits.length; i++) {
                if (hits[i] >= from) {
                    cur = i;
                    break;
                }
            }
        }
        show(true);
    }

    private void show(boolean reveal) {
        body.setFind(hits, q.length(), cur);
        count.setText(q.isEmpty() ? "" : hits.length == 0 ? "0" : (cur + 1) + "/" + hits.length);
        if (reveal && cur >= 0) reveal(hits[cur]);
    }

    private void step(int dir) {
        if (hits.length == 0) return;
        cur = (cur + dir + hits.length) % hits.length;
        show(true);
    }

    /** Scrolls so the match is in view, clear of this panel and the keyboard. */
    private void reveal(int off) {
        Layout l = body.getLayout();
        if (l == null) return;
        int line = l.getLineForOffset(Math.min(off, body.length()));
        int y = body.getExtendedPaddingTop() + l.getLineTop(line);
        View child = sv.getChildAt(0);
        int top = 0;
        for (View p = body; p != null && p != child; p = (View) p.getParent()) top += p.getTop();
        int at = top + y;
        int clearTop = sv.getScrollY() + px(24);
        int clearBottom = sv.getScrollY() + sv.getHeight() - getHeight() - px(48);
        if (at < clearTop || at > clearBottom) sv.smoothScrollTo(0, Math.max(0, at - px(120)));
    }

    // ------------------------------------------------------------------ replacing

    private void replaceOne() {
        if (cur < 0 || cur >= hits.length) return;
        final int at = hits[cur];
        final String with = rep.getText().toString();
        guard.run(() -> body.getText().replace(at, at + q.length(), with));
        // search again, then carry on from just after the replacement
        search(false);
        int next = at + with.length();
        cur = -1;
        for (int i = 0; i < hits.length; i++) {
            if (hits[i] >= next) {
                cur = i;
                break;
            }
        }
        if (cur < 0 && hits.length > 0) cur = 0;
        show(true);
    }

    private void replaceAll() {
        if (hits.length == 0) return;
        final int[] h = hits.clone();
        final int n = q.length();
        final String with = rep.getText().toString();
        guard.run(() -> {
            Editable e = body.getText();
            for (int i = h.length - 1; i >= 0; i--) e.replace(h[i], h[i] + n, with); // last first: earlier spots stay put
        });
        Toast.makeText(getContext(), "Replaced " + h.length, Toast.LENGTH_SHORT).show();
        search(false);
    }
}
