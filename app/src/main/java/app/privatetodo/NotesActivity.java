package app.privatetodo;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** The notebook: a searchable list of rich-text notes, stored only on this device. */
public class NotesActivity extends Activity {
    private TaskDb db;
    private float density;
    private int builtDark;
    private final List<Note> notes = new ArrayList<>();
    private String query = "";
    private ListView list;
    private LinearLayout emptyView;
    private TextView emptyTitle;
    private NoteAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Pal.init(this);
        builtDark = Pal.themeRes();
        setTheme(Pal.themeRes());
        super.onCreate(savedInstanceState);
        density = getResources().getDisplayMetrics().density;
        db = new TaskDb(this);
        setContentView(build());
        Fonts.apply(getWindow().getDecorView());
    }

    @Override
    protected void onResume() {
        super.onResume();
        Pal.init(this);
        if (Pal.themeRes() != builtDark) {
            recreate();
            return;
        }
        refresh();
    }

    @Override
    protected void onDestroy() {
        db.close();
        super.onDestroy();
    }

    private View build() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Pal.bg);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(12), dp(12), dp(16), 0);
        IconView back = new IconView(this, IconView.BACK);
        back.setContentDescription("Back");
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        header.addView(text("Notebook", 30, Pal.ink, true));
        col.addView(header);

        // Search
        EditText search = new EditText(this);
        search.setHint("Search notes…");
        search.setHintTextColor(Pal.muted);
        search.setTextColor(Pal.ink);
        search.setTextSize(16);
        search.setSingleLine(true);
        search.setInputType(InputType.TYPE_CLASS_TEXT);
        search.setBackground(null);
        search.setPadding(dp(20), 0, dp(20), 0);
        GradientDrawable sbg = new GradientDrawable();
        sbg.setColor(Pal.card);
        sbg.setCornerRadius(dp(24));
        search.setBackground(sbg);
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence c, int a, int b, int n) {
            }

            @Override
            public void onTextChanged(CharSequence c, int a, int b, int n) {
            }

            @Override
            public void afterTextChanged(Editable e) {
                query = e.toString().trim();
                refresh();
            }
        });
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, dp(48));
        slp.setMargins(dp(16), dp(12), dp(16), dp(8));
        col.addView(search, slp);

        FrameLayout body = new FrameLayout(this);
        list = new ListView(this);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setSelector(new ColorDrawable(0));
        list.setClipToPadding(false);
        list.setPadding(0, 0, 0, dp(96));
        list.setVerticalScrollBarEnabled(false);
        adapter = new NoteAdapter();
        list.setAdapter(adapter);
        body.addView(list, new FrameLayout.LayoutParams(-1, -1));

        emptyView = new LinearLayout(this);
        emptyView.setOrientation(LinearLayout.VERTICAL);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setPadding(dp(40), 0, dp(40), dp(96));
        emptyTitle = text("", 22, Pal.ink, true);
        emptyTitle.setGravity(Gravity.CENTER);
        emptyView.addView(emptyTitle);
        TextView hint = text("Format with bold, italic, highlights and code. Stays on this device.",
                15, Pal.muted, false);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(8), 0, 0);
        emptyView.addView(hint);
        body.addView(emptyView, new FrameLayout.LayoutParams(-1, -1));
        col.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));

        // New note pill
        LinearLayout pill = new LinearLayout(this);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER);
        GradientDrawable pbg = new GradientDrawable();
        pbg.setColor(Pal.accent);
        pbg.setCornerRadius(dp(28));
        pill.setBackground(pbg);
        pill.setElevation(0);
        pill.setClickable(true);
        pill.setContentDescription("New note");
        pill.setPadding(dp(18), 0, dp(24), 0);
        IconView plus = new IconView(this, IconView.PLUS);
        plus.setClickable(false);
        pill.addView(plus, new LinearLayout.LayoutParams(dp(32), dp(32)));
        pill.addView(text("New note", 16, Pal.onAccent, true));
        pill.setOnClickListener(v -> openNote(0));
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(-2, dp(56));
        plp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        plp.bottomMargin = dp(24);
        root.addView(pill, plp);
        return root;
    }

    private void refresh() {
        notes.clear();
        notes.addAll(db.notes(query));
        adapter.notifyDataSetChanged();
        boolean empty = notes.isEmpty();
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        list.setVisibility(empty ? View.GONE : View.VISIBLE);
        emptyTitle.setText(query.isEmpty() ? "No notes yet" : "No matches");
    }

    private void openNote(long id) {
        startActivity(new Intent(this, NoteEditorActivity.class)
                .putExtra(NoteEditorActivity.EXTRA_ID, id));
    }

    private void confirmDelete(final Note n) {
        new Dlg.Builder(this)
                .setTitle("Delete note?")
                .setMessage(n.title.isEmpty() ? "Untitled" : n.title)
                .setPositiveButton("Delete", (d, w) -> {
                    db.deleteNote(n.id);
                    refresh();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private final class NoteAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return notes.size();
        }

        @Override
        public Note getItem(int i) {
            return notes.get(i);
        }

        @Override
        public long getItemId(int i) {
            return notes.get(i).id;
        }

        @Override
        public View getView(int i, View convert, ViewGroup parent) {
            final Note n = getItem(i);
            Context c = NotesActivity.this;
            FrameLayout wrap = new FrameLayout(c);
            wrap.setPadding(dp(16), dp(4), dp(16), dp(4));

            LinearLayout card = new LinearLayout(c);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(16), dp(14), dp(16), dp(14));
            GradientDrawable g = new GradientDrawable();
            g.setColor(Pal.card);
            g.setCornerRadius(dp(16));
            card.setBackground(g);

            TextView title = text(n.title.isEmpty() ? "Untitled" : n.title, 17,
                    n.title.isEmpty() ? Pal.muted : Pal.ink, true);
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.END);
            card.addView(title);

            String preview = n.plain.replace('\n', ' ').trim();
            if (!preview.isEmpty()) {
                TextView p = text(preview, 14, Pal.muted, false);
                p.setMaxLines(2);
                p.setEllipsize(TextUtils.TruncateAt.END);
                p.setPadding(0, dp(4), 0, 0);
                card.addView(p);
            }
            TextView when = text("Edited " + Reminders.when(c, n.updated), 12, Pal.muted, false);
            when.setPadding(0, dp(8), 0, 0);
            card.addView(when);

            card.setOnClickListener(v -> openNote(n.id));
            card.setOnLongClickListener(v -> {
                confirmDelete(n);
                return true;
            });
            wrap.addView(card, new FrameLayout.LayoutParams(-1, -2));
            Fonts.apply(wrap);
            return wrap;
        }
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private int dp(int v) {
        return Math.round(v * density);
    }
}
