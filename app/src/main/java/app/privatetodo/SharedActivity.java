package app.privatetodo;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** Links shared into the app from the Android Share menu. One permanent card per share, newest first. */
public class SharedActivity extends Activity {
    static final String EXTRA_NEW = "new_id";

    private TaskDb db;
    private float density;
    private int builtDark;
    private LinearLayout col;
    private long highlight;
    private final List<Runnable> savers = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Pal.init(this);
        builtDark = Pal.themeRes();
        setTheme(Pal.themeRes());
        super.onCreate(savedInstanceState);
        density = getResources().getDisplayMetrics().density;
        db = new TaskDb(this);
        highlight = getIntent().getLongExtra(EXTRA_NEW, 0);
        setContentView(build());
    }

    @Override
    protected void onResume() {
        super.onResume();
        Lock.guard(this, Lock.NOTES);
        Pal.init(this);
        if (Pal.themeRes() != builtDark) {
            recreate();
            return;
        }
        refresh();
    }

    @Override
    protected void onPause() {
        saveAll();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        db.close();
        super.onDestroy();
    }

    private void saveAll() {
        for (Runnable r : savers) r.run();
    }

    private View build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Pal.bg);
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(12), dp(12), dp(16), 0);
        IconView back = new IconView(this, IconView.BACK);
        back.setContentDescription("Back");
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        header.addView(text("Shared notes", 28, Pal.ink, true), new LinearLayout.LayoutParams(0, -2, 1f));
        root.addView(header);
        TextView info = text("Pick Private Todo in any app's Share menu and the link lands here.",
                13, Pal.muted, false);
        info.setPadding(dp(24), dp(4), dp(24), dp(8));
        root.addView(info);
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), 0, dp(16), dp(32));
        sv.addView(col);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        return root;
    }

    private void refresh() {
        col.removeAllViews();
        savers.clear();
        List<TaskDb.Shared> all = db.shared();
        if (all.isEmpty()) {
            TextView e = text("Nothing shared yet", 16, Pal.muted, false);
            e.setGravity(Gravity.CENTER);
            e.setPadding(0, dp(80), 0, 0);
            col.addView(e, new LinearLayout.LayoutParams(-1, -2));
        }
        for (TaskDb.Shared s : all) col.addView(card(s));
        Fonts.apply(col);
    }

    private View card(final TaskDb.Shared s) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(18));
        g.setColor(Pal.card);
        if (s.id == highlight) g.setStroke(dp(2), Pal.accent);
        c.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(10);
        c.setLayoutParams(lp);

        c.addView(text(Reminders.when(this, s.created), 12, Pal.muted, true));

        TextView link = text(s.url, 14, Pal.accent, false);
        link.setPadding(0, dp(6), 0, dp(6));
        link.setMaxLines(3);
        link.setEllipsize(android.text.TextUtils.TruncateAt.END);
        link.setOnClickListener(v -> open(s.url));
        if (!s.url.isEmpty()) c.addView(link);

        final EditText title = field("Title", s.title, false);
        c.addView(title);
        final EditText desc = field("Description", s.description, true);
        c.addView(desc);
        savers.add(() -> {
            String t = title.getText().toString().trim();
            String d = desc.getText().toString().trim();
            if (!t.equals(s.title) || !d.equals(s.description)) db.updateShared(s.id, t, d);
        });

        LinearLayout row = new LinearLayout(this);
        row.setPadding(0, dp(10), 0, 0);
        TextView remind = pill("Remind me", Pal.onAccent, Pal.accent);
        remind.setOnClickListener(v -> {
            Hap.tick(v);
            saveAll();
            String t = title.getText().toString().trim();
            String text = t.isEmpty() ? s.url : t + ": " + s.url;
            startActivity(new Intent(this, MainActivity.class)
                    .putExtra(MainActivity.EXTRA_PREFILL, text)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        });
        LinearLayout.LayoutParams a = new LinearLayout.LayoutParams(0, dp(44), 1.4f);
        a.rightMargin = dp(8);
        row.addView(remind, a);
        TextView copy = pill("Copy", Pal.ink, Pal.chip);
        copy.setOnClickListener(v -> {
            ((ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE))
                    .setPrimaryClip(ClipData.newPlainText("link", s.url));
            Toast.makeText(this, "Link copied", Toast.LENGTH_SHORT).show();
        });
        LinearLayout.LayoutParams b = new LinearLayout.LayoutParams(0, dp(44), 1f);
        b.rightMargin = dp(8);
        row.addView(copy, b);
        TextView del = pill("Delete", Pal.overdue, Pal.chip);
        del.setOnClickListener(v -> {
            saveAll();
            new Dlg.Builder(this)
                    .setTitle("Delete this shared link?")
                    .setPositiveButton("Delete", (d2, w) -> {
                        db.deleteShared(s.id);
                        refresh();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
        row.addView(del, new LinearLayout.LayoutParams(0, dp(44), 1f));
        c.addView(row);
        return c;
    }

    private void open(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(this, "No app can open this link", Toast.LENGTH_SHORT).show();
        }
    }

    private EditText field(String hint, String value, boolean multi) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(value);
        e.setTextSize(multi ? 14 : 16);
        e.setTextColor(Pal.ink);
        e.setHintTextColor(Pal.muted);
        if (!multi) e.setTypeface(Typeface.DEFAULT_BOLD);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | (multi ? InputType.TYPE_TEXT_FLAG_MULTI_LINE : 0));
        e.setBackground(null);
        e.setPadding(0, dp(6), 0, dp(6));
        return e;
    }

    private TextView pill(String s, int fg, int bg) {
        TextView t = text(s, 14, fg, true);
        t.setGravity(Gravity.CENTER);
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
