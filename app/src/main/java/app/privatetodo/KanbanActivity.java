package app.privatetodo;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/** Kanban: your boards. Tap one to open it. */
public class KanbanActivity extends Activity {
    private int builtTheme;
    private KanbanDb db;

    @Override
    protected void onCreate(Bundle b) {
        Pal.init(this);
        builtTheme = Pal.themeRes();
        setTheme(Pal.themeRes());
        super.onCreate(b);
        db = new KanbanDb(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Lock.guard(this, Lock.ALL);
        Pal.init(this);
        if (Pal.themeRes() != builtTheme) {
            recreate();
            return;
        }
        render();
    }

    @Override
    protected void onDestroy() {
        db.close();
        super.onDestroy();
    }

    private int dp(float v) {
        return LentUi.dp(this, v);
    }

    private void render() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Pal.bg);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(18), dp(16), dp(18), dp(110));
        scroll.addView(col, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        IconView back = new IconView(this, IconView.BACK);
        back.setContentDescription("Back");
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        header.addView(LentUi.text(this, "Boards", 30, Pal.ink, true), new LinearLayout.LayoutParams(0, -2, 1f));
        col.addView(header);

        List<KanbanDb.Board> boards = db.boards();
        if (boards.isEmpty()) {
            TextView hint = LentUi.text(this, "No boards yet.\nMake one for a project: To do, Doing, Done.", 16, Pal.muted, false);
            hint.setPadding(dp(4), dp(18), 0, 0);
            col.addView(hint);
        }
        for (final KanbanDb.Board b : boards) {
            LinearLayout c = LentUi.card(this);
            LinearLayout top = new LinearLayout(this);
            top.setGravity(Gravity.CENTER_VERTICAL);
            TextView name = LentUi.text(this, (b.emoji.isEmpty() ? "" : b.emoji + "  ") + b.name, 20, Pal.ink, true);
            name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            top.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
            top.addView(LentUi.text(this, b.cards + (b.cards == 1 ? " card" : " cards"), 14, Pal.muted, true));
            c.addView(top);
            LentUi.Bar bar = new LentUi.Bar(this, b.cards == 0 ? 0f : b.done / (float) b.cards, Pal.accent);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, dp(6));
            blp.topMargin = dp(12);
            c.addView(bar, blp);
            if (b.cards > 0) {
                TextView sub = LentUi.text(this, b.done + " of " + b.cards + " done", 13, Pal.muted, false);
                sub.setPadding(0, dp(6), 0, 0);
                c.addView(sub);
            }
            c.setOnClickListener(v -> {
                Hap.tick(v);
                startActivity(new Intent(this, KanbanBoardActivity.class).putExtra("board", b.id));
            });
            col.addView(c);
        }

        TextView fab = LentUi.pill(this, "+  New board", Pal.accent, Pal.onAccent, 16);
        fab.setPadding(dp(22), dp(14), dp(22), dp(14));
        fab.setOnClickListener(v -> {
            Hap.tick(v);
            newBoard();
        });
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END);
        flp.setMargins(0, 0, dp(20), dp(24));
        root.addView(fab, flp);

        setContentView(root);
        getWindow().setStatusBarColor(Pal.bg);
        getWindow().setNavigationBarColor(Pal.bg);
        Fonts.apply(root);
    }

    private EditText field(String hint, String value, int type) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setInputType(type);
        e.setSingleLine(true);
        e.setTextSize(17);
        e.setTextColor(Pal.ink);
        e.setHintTextColor(Pal.muted);
        e.setText(value);
        e.setBackground(LentUi.round(this, Pal.card, 16));
        e.setPadding(dp(14), dp(12), dp(14), dp(12));
        return e;
    }

    private void newBoard() {
        LinearLayout box = new LinearLayout(this);
        box.setPadding(dp(20), dp(4), dp(20), dp(4));
        final EditText emoji = field("📌", "", InputType.TYPE_CLASS_TEXT);
        emoji.setGravity(Gravity.CENTER);
        emoji.setTextSize(22);
        box.addView(emoji, new LinearLayout.LayoutParams(dp(64), -2));
        final EditText name = field("Board name, e.g. Website", "", InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(0, -2, 1f);
        nl.leftMargin = dp(8);
        box.addView(name, nl);
        Fonts.apply(box);
        new Dlg.Builder(this).setTitle("New board").setView(box)
                .setPositiveButton("Create", (d, w) -> {
                    String n = name.getText().toString().trim();
                    if (n.isEmpty()) {
                        Toast.makeText(this, "Give it a name", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    long id = db.addBoard(n, emoji.getText().toString().trim());
                    startActivity(new Intent(this, KanbanBoardActivity.class).putExtra("board", id));
                }).setNegativeButton("Cancel", null).show();
    }
}
