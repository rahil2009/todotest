package app.privatetodo;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/** The notes Bin: deleted notes wait here for 30 days, then go for good. */
public class BinActivity extends Activity {
    private TaskDb db;
    private float density;
    private int builtDark;
    private LinearLayout col;
    private TextView emptyAll;

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
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Pal.bg);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(12), dp(12), dp(16), 0);
        IconView back = new IconView(this, IconView.BACK);
        back.setContentDescription("Back");
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView title = text("Bin", 28, Pal.ink, true);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView empty = pill("Empty Bin", Pal.overdue, Pal.chip);
        empty.setOnClickListener(v -> {
            Hap.tick(v);
            if (db.binCount() == 0) return;
            new Dlg.Builder(this)
                    .setTitle("Empty the Bin?")
                    .setMessage("Every note in the Bin is deleted for good. This cannot be undone.")
                    .setPositiveButton("Empty Bin", (d, w) -> {
                        db.emptyBin();
                        refresh();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
        header.addView(empty, new LinearLayout.LayoutParams(-2, dp(40)));
        root.addView(header);

        TextView info = text("Deleted notes stay here for " + TaskDb.BIN_DAYS
                + " days, then they are removed for good.", 13, Pal.muted, false);
        info.setPadding(dp(24), dp(4), dp(24), dp(8));
        root.addView(info);

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
        List<Note> bin = db.binNotes();
        if (bin.isEmpty()) {
            TextView e = text("The Bin is empty", 16, Pal.muted, false);
            e.setGravity(Gravity.CENTER);
            e.setPadding(0, dp(80), 0, 0);
            col.addView(e, new LinearLayout.LayoutParams(-1, -2));
            Fonts.apply(col);
            return;
        }
        for (final Note n : bin) col.addView(card(n));
        Fonts.apply(col);
    }

    private View card(final Note n) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(18));
        g.setColor(Pal.card);
        c.setBackground(g);
        TextView t = text(n.title.isEmpty() ? "Untitled" : n.title, 18, Pal.ink, true);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        c.addView(t);
        String body = RichText.tidy(RichText.plain(n.body)).replace('￼', ' ').trim();
        if (!body.isEmpty()) {
            TextView p = text(body, 14, Pal.muted, false);
            p.setMaxLines(2);
            p.setEllipsize(android.text.TextUtils.TruncateAt.END);
            p.setPadding(0, dp(4), 0, 0);
            c.addView(p);
        }
        long at = db.deletedAt(n.id);
        long left = at + TaskDb.BIN_DAYS * 24L * 3600 * 1000 - System.currentTimeMillis();
        int days = (int) Math.max(1, Math.ceil(left / 86400000.0));
        TextView when = text("Deleted " + Reminders.when(this, at) + " · " + days + (days == 1 ? " day" : " days")
                + " left", 12, Pal.muted, false);
        when.setPadding(0, dp(8), 0, dp(10));
        c.addView(when);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        TextView restore = pill("Restore", Pal.onAccent, Pal.accent);
        restore.setOnClickListener(v -> {
            Hap.confirm(v);
            db.restoreNote(n.id);
            if (n.folder > 0 && db.folder(n.folder) == null) db.setNoteFolder(n.id, 0); // its folder is gone
            Toast.makeText(this, "Note restored", Toast.LENGTH_SHORT).show();
            refresh();
        });
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(0, dp(44), 1f);
        rlp.rightMargin = dp(8);
        row.addView(restore, rlp);
        TextView gone = pill("Delete forever", Pal.overdue, Pal.chip);
        gone.setOnClickListener(v -> new Dlg.Builder(this)
                .setTitle("Delete forever?")
                .setMessage("This note will be gone for good.")
                .setPositiveButton("Delete forever", (d, w) -> {
                    db.deleteNote(n.id);
                    refresh();
                })
                .setNegativeButton("Cancel", null)
                .show());
        row.addView(gone, new LinearLayout.LayoutParams(0, dp(44), 1f));
        c.addView(row);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(10);
        c.setLayoutParams(lp);
        return c;
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
