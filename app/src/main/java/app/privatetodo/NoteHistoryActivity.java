package app.privatetodo;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/**
 * Note history: earlier versions of every note, kept on the phone so nothing written is ever lost to a slip,
 * a bug or a delete. Opened from Settings (all notes) or from inside a note (just that note).
 */
public class NoteHistoryActivity extends Activity {
    /** Show only this note's versions (the editor passes it). */
    static final String EXTRA_NOTE = "note_id";

    private TaskDb db;
    private float density;
    private int builtDark;
    private LinearLayout col;
    private TextView titleView;
    private TextView infoView;
    private TextView clearBtn;
    /** The note whose versions are showing; 0 = the list of notes. */
    private long openNote;
    private boolean fromEditor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Pal.init(this);
        builtDark = Pal.themeRes();
        setTheme(Pal.themeRes());
        super.onCreate(savedInstanceState);
        density = getResources().getDisplayMetrics().density;
        db = new TaskDb(this);
        openNote = getIntent().getLongExtra(EXTRA_NOTE, 0);
        fromEditor = openNote > 0;
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

    @Override
    public void onBackPressed() {
        if (openNote > 0 && !fromEditor) {
            openNote = 0;
            refresh();
            return;
        }
        super.onBackPressed();
    }

    private View build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Pal.bg);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(12), dp(12), dp(16), 0);
        IconView back = new IconView(this, IconView.BACK);
        back.setContentDescription("Back");
        back.setOnClickListener(v -> onBackPressed());
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        titleView = text("Note history", 26, Pal.ink, true);
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        header.addView(titleView, new LinearLayout.LayoutParams(0, -2, 1f));
        clearBtn = pill("Clear", Pal.overdue, Pal.chip);
        clearBtn.setOnClickListener(v -> {
            Hap.tick(v);
            if (openNote <= 0) return;
            new Dlg.Builder(this)
                    .setTitle("Clear this note's history?")
                    .setMessage("The saved earlier versions of this note are deleted. The note itself stays.")
                    .setPositiveButton("Clear", (d, w) -> {
                        db.clearHistory(openNote);
                        if (fromEditor) finish();
                        else {
                            openNote = 0;
                            refresh();
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
        header.addView(clearBtn, new LinearLayout.LayoutParams(-2, dp(40)));
        root.addView(header);

        infoView = text("", 13, Pal.muted, false);
        infoView.setPadding(dp(24), dp(4), dp(24), dp(8));
        root.addView(infoView);

        ScrollView sv = new ScrollView(this);
        col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), 0, dp(16), dp(24));
        sv.addView(col);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        return root;
    }

    private void refresh() {
        col.removeAllViews();
        if (openNote > 0) showVersions();
        else showNotes();
        Fonts.apply(col);
    }

    // ------------------------------------------------------------ list of notes

    private void showNotes() {
        titleView.setText("Note history");
        clearBtn.setVisibility(View.GONE);
        infoView.setText("Every note keeps its earlier versions here, even after you delete it. "
                + "Open one to bring an older version back.");
        List<TaskDb.HistoryNote> all = db.historyNotes();
        if (all.isEmpty()) {
            col.addView(empty("No history yet. Versions appear here as you edit your notes."));
            return;
        }
        for (final TaskDb.HistoryNote h : all) {
            LinearLayout c = card();
            TextView t = text(h.title, 17, Pal.ink, true);
            t.setSingleLine(true);
            t.setEllipsize(TextUtils.TruncateAt.END);
            c.addView(t);
            String state = h.inBin ? " · in the Bin" : (h.exists ? "" : " · note deleted");
            TextView s = text(h.count + (h.count == 1 ? " version" : " versions") + " · last "
                    + Reminders.when(this, h.last) + state, 13, h.exists && !h.inBin ? Pal.muted : Pal.overdue, false);
            s.setPadding(0, dp(4), 0, 0);
            c.addView(s);
            c.setOnClickListener(v -> {
                Hap.tick(v);
                openNote = h.noteId;
                refresh();
            });
            col.addView(c);
        }
    }

    // ------------------------------------------------------------ versions of one note

    private void showVersions() {
        final List<TaskDb.Version> vs = db.versions(openNote);
        String name = vs.isEmpty() ? "Note history" : (vs.get(0).title.trim().isEmpty() ? "Untitled note" : vs.get(0).title);
        titleView.setText(name);
        clearBtn.setVisibility(vs.isEmpty() ? View.GONE : View.VISIBLE);
        infoView.setText("Newest first. Restoring keeps what the note holds now as another version, so you can always go back.");
        if (vs.isEmpty()) {
            col.addView(empty("No earlier versions of this note yet."));
            return;
        }
        for (final TaskDb.Version v : vs) col.addView(versionCard(v));
    }

    private View versionCard(final TaskDb.Version v) {
        LinearLayout c = card();
        String full = RichText.tidy(RichText.plain(v.body)).replace('￼', ' ').trim();
        TextView when = text(Reminders.when(this, v.saved) + " · " + full.length() + " characters", 13, Pal.muted, false);
        c.addView(when);
        if (!v.title.trim().isEmpty()) {
            TextView t = text(v.title, 17, Pal.ink, true);
            t.setPadding(0, dp(4), 0, 0);
            t.setSingleLine(true);
            t.setEllipsize(TextUtils.TruncateAt.END);
            c.addView(t);
        }
        final TextView p = text(full.isEmpty() ? "(empty)" : full, 14, Pal.ink, false);
        p.setMaxLines(4);
        p.setEllipsize(TextUtils.TruncateAt.END);
        p.setPadding(0, dp(4), 0, dp(10));
        c.addView(p);
        c.setOnClickListener(x -> {
            Hap.tick(x);
            boolean open = p.getMaxLines() > 4;
            p.setMaxLines(open ? 4 : 400);
            p.setEllipsize(open ? TextUtils.TruncateAt.END : null);
        });
        TextView restore = pill("Restore this version", Pal.onAccent, Pal.accent);
        restore.setOnClickListener(x -> new Dlg.Builder(this)
                .setTitle("Restore this version?")
                .setMessage("The note goes back to how it was " + Reminders.when(this, v.saved)
                        + ". What it holds now is kept in the history.")
                .setPositiveButton("Restore", (d, w) -> {
                    long id = db.restoreVersion(v.id);
                    if (id <= 0) {
                        Toast.makeText(this, "That version is gone", Toast.LENGTH_SHORT).show();
                        refresh();
                        return;
                    }
                    Toast.makeText(this, "Note restored", Toast.LENGTH_SHORT).show();
                    if (fromEditor) {
                        setResult(RESULT_OK, new Intent().putExtra(EXTRA_NOTE, id));
                        finish();
                    } else {
                        openNote = id;
                        refresh();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show());
        c.addView(restore, new LinearLayout.LayoutParams(-1, dp(44)));
        return c;
    }

    // ------------------------------------------------------------ helpers

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(18));
        g.setColor(Pal.card);
        c.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(10);
        c.setLayoutParams(lp);
        return c;
    }

    private TextView empty(String s) {
        TextView e = text(s, 16, Pal.muted, false);
        e.setGravity(Gravity.CENTER);
        e.setPadding(dp(24), dp(80), dp(24), 0);
        return e;
    }

    private TextView pill(String s, int fg, int bg) {
        TextView t = text(s, 14, fg, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(16), 0, dp(16), 0);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(22));
        g.setColor(bg);
        t.setBackground(g);
        t.setClickable(true);
        return t;
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
