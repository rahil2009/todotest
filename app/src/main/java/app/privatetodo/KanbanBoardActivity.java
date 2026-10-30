package app.privatetodo;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * One Kanban board: columns side by side (swipe sideways), cards inside. Long-press a card and drag it to another
 * column or to a new place in its own; tap a card to edit it, link a task or a note, or delete it.
 */
public class KanbanBoardActivity extends Activity {
    private int builtTheme;
    private KanbanDb db;
    private long boardId;
    private HorizontalScrollView hs;
    private int scrollX;

    @Override
    protected void onCreate(Bundle b) {
        Pal.init(this);
        builtTheme = Pal.themeRes();
        setTheme(Pal.themeRes());
        super.onCreate(b);
        db = new KanbanDb(this);
        boardId = getIntent().getLongExtra("board", 0);
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
        if (hs != null) scrollX = hs.getScrollX();
        KanbanDb.Board board = db.board(boardId);
        if (board == null) {
            finish();
            return;
        }
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Pal.bg);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(18), dp(16), dp(18), dp(8));
        IconView back = new IconView(this, IconView.BACK);
        back.setContentDescription("Back");
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView title = LentUi.text(this, (board.emoji.isEmpty() ? "" : board.emoji + "  ") + board.name, 26, Pal.ink, true);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView more = LentUi.pill(this, "⋯", Pal.chip, Pal.ink, 20);
        more.setContentDescription("Board options");
        more.setOnClickListener(v -> {
            Hap.tick(v);
            boardMenu(board);
        });
        header.addView(more, new LinearLayout.LayoutParams(dp(48), dp(40)));
        page.addView(header);

        hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout cols = new LinearLayout(this);
        cols.setPadding(dp(14), dp(4), dp(14), dp(16));
        int colW = Math.round(getResources().getDisplayMetrics().widthPixels * 0.80f);
        List<KanbanDb.Col> list = db.columns(boardId);
        for (KanbanDb.Col c : list) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(colW, -1);
            lp.rightMargin = dp(10);
            cols.addView(column(c, list), lp);
        }
        TextView addCol = LentUi.pill(this, "+  Add column", Pal.chip, Pal.ink, 15);
        addCol.setOnClickListener(v -> {
            Hap.tick(v);
            nameDialog("New column", "", "Column name", name -> {
                db.addColumn(boardId, name);
                render();
            });
        });
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(-2, dp(44));
        alp.rightMargin = dp(14);
        cols.addView(addCol, alp);
        hs.addView(cols, new HorizontalScrollView.LayoutParams(-2, -1));
        page.addView(hs, new LinearLayout.LayoutParams(-1, 0, 1f));

        setContentView(root);
        getWindow().setStatusBarColor(Pal.bg);
        getWindow().setNavigationBarColor(Pal.bg);
        Fonts.apply(root);
        final int sx = scrollX;
        hs.post(() -> hs.scrollTo(sx, 0));
    }

    // ------------------------------------------------------------------ columns and cards

    private View column(final KanbanDb.Col col, final List<KanbanDb.Col> all) {
        final LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(LentUi.round(this, Pal.chip, 22));
        box.setPadding(dp(10), dp(10), dp(10), dp(10));

        List<KanbanDb.Card> cards = db.cards(col.id);
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(6), 0, dp(6), dp(8));
        TextView name = LentUi.text(this, col.name, 17, Pal.ink, true);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        head.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView n = LentUi.text(this, String.valueOf(cards.size()), 14, Pal.muted, true);
        n.setPadding(dp(10), dp(2), dp(10), dp(2));
        n.setBackground(LentUi.round(this, Pal.card, 100));
        head.addView(n);
        head.setOnClickListener(v -> {
            Hap.tick(v);
            columnMenu(col, all);
        });
        box.addView(head);

        final ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setVerticalScrollBarEnabled(false);
        final LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        sv.addView(list, new ScrollView.LayoutParams(-1, -2));
        for (KanbanDb.Card k : cards) list.addView(cardView(k));
        box.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));

        TextView add = LentUi.text(this, "+  Add card", 15, Pal.muted, true);
        add.setPadding(dp(8), dp(12), dp(8), dp(4));
        add.setOnClickListener(v -> {
            Hap.tick(v);
            KanbanDb.Card k = new KanbanDb.Card();
            k.col = col.id;
            editCard(k);
        });
        box.addView(add);

        list.setOnDragListener((v, e) -> {
            Object state = e.getLocalState();
            if (!(state instanceof Long)) return false;
            switch (e.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                    return true;
                case DragEvent.ACTION_DRAG_ENTERED:
                    box.setBackground(LentUi.round(this, (Pal.accent & 0x00FFFFFF) | 0x33000000, 22));
                    return true;
                case DragEvent.ACTION_DRAG_EXITED:
                case DragEvent.ACTION_DRAG_ENDED:
                    box.setBackground(LentUi.round(this, Pal.chip, 22));
                    return true;
                case DragEvent.ACTION_DRAG_LOCATION:
                    autoScroll(v, sv, e);
                    return true;
                case DragEvent.ACTION_DROP: {
                    int pos = list.getChildCount();
                    for (int i = 0; i < list.getChildCount(); i++) {
                        View c = list.getChildAt(i);
                        if (e.getY() < c.getTop() + c.getHeight() / 2f) {
                            pos = i;
                            break;
                        }
                    }
                    db.move((Long) state, col.id, pos);
                    Hap.confirm(v);
                    render();
                    return true;
                }
                default:
                    return true;
            }
        });
        return box;
    }

    /** While a card is being dragged near an edge, scroll the board sideways or the column up/down. */
    private void autoScroll(View list, ScrollView sv, DragEvent e) {
        int[] loc = new int[2];
        list.getLocationOnScreen(loc);
        float sx = loc[0] + e.getX();
        int w = getResources().getDisplayMetrics().widthPixels;
        int edge = dp(56);
        if (sx < edge) hs.smoothScrollBy(-dp(40), 0);
        else if (sx > w - edge) hs.smoothScrollBy(dp(40), 0);
        int[] sl = new int[2];
        sv.getLocationOnScreen(sl);
        float sy = loc[1] + e.getY();
        if (sy < sl[1] + dp(48)) sv.smoothScrollBy(0, -dp(30));
        else if (sy > sl[1] + sv.getHeight() - dp(48)) sv.smoothScrollBy(0, dp(30));
    }

    private View cardView(final KanbanDb.Card k) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(14), dp(12), dp(14), dp(12));
        c.setBackground(LentUi.round(this, Pal.card, 16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(8);
        c.setLayoutParams(lp);
        c.addView(LentUi.text(this, k.title, 16, Pal.ink, true));
        if (!k.note.isEmpty()) {
            TextView nt = LentUi.text(this, k.note, 14, Pal.muted, false);
            nt.setMaxLines(2);
            nt.setEllipsize(android.text.TextUtils.TruncateAt.END);
            nt.setPadding(0, dp(3), 0, 0);
            c.addView(nt);
        }
        if (k.taskId > 0 || k.noteId > 0) {
            LinearLayout links = new LinearLayout(this);
            links.setPadding(0, dp(8), 0, 0);
            if (k.taskId > 0) {
                TaskDb tdb = new TaskDb(this);
                Task t;
                try {
                    t = tdb.get(k.taskId);
                } finally {
                    tdb.close();
                }
                String label = t == null ? "Task removed" : (t.done ? "✓ " : "○ ") + t.title;
                TextView chip = linkChip(label, t != null && t.done);
                chip.setOnClickListener(v -> {
                    Hap.tick(v);
                    startActivity(new Intent(this, MainActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                });
                links.addView(chip);
            }
            if (k.noteId > 0) {
                TaskDb ndb = new TaskDb(this);
                Note nn;
                try {
                    nn = ndb.note(k.noteId);
                } finally {
                    ndb.close();
                }
                TextView chip = linkChip(nn == null ? "Note removed" : "📝 " + (nn.title.isEmpty() ? "Untitled" : nn.title), false);
                if (nn != null) {
                    chip.setOnClickListener(v -> {
                        Hap.tick(v);
                        startActivity(new Intent(this, NoteEditorActivity.class)
                                .putExtra(NoteEditorActivity.EXTRA_ID, k.noteId));
                    });
                }
                links.addView(chip);
            }
            c.addView(links);
        }
        c.setOnClickListener(v -> {
            Hap.tick(v);
            editCard(k);
        });
        c.setOnLongClickListener(v -> {
            Hap.confirm(v);
            ClipData data = ClipData.newPlainText("card", String.valueOf(k.id));
            v.startDragAndDrop(data, new View.DragShadowBuilder(v), Long.valueOf(k.id), 0);
            return true;
        });
        return c;
    }

    private TextView linkChip(String s, boolean done) {
        TextView t = LentUi.text(this, s, 12, done ? Pal.muted : Pal.ink, true);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setMaxWidth(dp(150));
        t.setPadding(dp(10), dp(4), dp(10), dp(4));
        t.setBackground(LentUi.round(this, Pal.chip, 100));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = dp(6);
        t.setLayoutParams(lp);
        return t;
    }

    // ------------------------------------------------------------------ dialogs

    private interface Named {
        void got(String name);
    }

    private EditText field(String hint, String value, int type, boolean single) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setInputType(type);
        e.setSingleLine(single);
        if (!single) {
            e.setMinLines(2);
            e.setGravity(Gravity.TOP);
        }
        e.setTextSize(17);
        e.setTextColor(Pal.ink);
        e.setHintTextColor(Pal.muted);
        e.setText(value);
        e.setBackground(LentUi.round(this, Pal.card, 16));
        e.setPadding(dp(14), dp(12), dp(14), dp(12));
        return e;
    }

    private void nameDialog(String title, String current, String hint, final Named done) {
        LinearLayout box = new LinearLayout(this);
        box.setPadding(dp(20), dp(4), dp(20), dp(4));
        final EditText e = field(hint, current, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, true);
        box.addView(e, new LinearLayout.LayoutParams(-1, -2));
        Fonts.apply(box);
        new Dlg.Builder(this).setTitle(title).setView(box).setPositiveButton("Save", (d, w) -> {
            String s = e.getText().toString().trim();
            if (s.isEmpty()) {
                Toast.makeText(this, "Type a name", Toast.LENGTH_SHORT).show();
                return;
            }
            done.got(s);
        }).setNegativeButton("Cancel", null).show();
    }

    private void boardMenu(final KanbanDb.Board b) {
        String[] items = {"Rename board", "Add column", "Delete board"};
        new Dlg.Builder(this).setTitle(b.name).setItems(items, (d, which) -> {
            if (which == 0) {
                nameDialog("Rename board", b.name, "Board name", name -> {
                    db.renameBoard(b.id, name, b.emoji);
                    render();
                });
            } else if (which == 1) {
                nameDialog("New column", "", "Column name", name -> {
                    db.addColumn(boardId, name);
                    render();
                });
            } else {
                new Dlg.Builder(this).setTitle("Delete this board?")
                        .setMessage("Its columns and cards go with it. Linked tasks and notes stay.")
                        .setPositiveButton("Delete", (d2, w2) -> {
                            db.deleteBoard(b.id);
                            finish();
                        }).setNegativeButton("Keep", null).show();
            }
        }).show();
    }

    private void columnMenu(final KanbanDb.Col col, final List<KanbanDb.Col> all) {
        String[] items = {"Rename column", "Delete column"};
        new Dlg.Builder(this).setTitle(col.name).setItems(items, (d, which) -> {
            if (which == 0) {
                nameDialog("Rename column", col.name, "Column name", name -> {
                    db.renameColumn(col.id, name);
                    render();
                });
                return;
            }
            if (all.size() <= 1) {
                Toast.makeText(this, "A board needs at least one column", Toast.LENGTH_SHORT).show();
                return;
            }
            long into = 0;
            for (KanbanDb.Col o : all) {
                if (o.id != col.id) {
                    into = o.id;
                    break;
                }
            }
            final long target = into;
            int n = db.count(col.id);
            new Dlg.Builder(this).setTitle("Delete \"" + col.name + "\"?")
                    .setMessage(n == 0 ? "The column is empty." : n + (n == 1 ? " card moves" : " cards move") + " to the first other column.")
                    .setPositiveButton("Delete", (d2, w2) -> {
                        db.deleteColumn(col.id, target);
                        render();
                    }).setNegativeButton("Keep", null).show();
        }).show();
    }

    private void editCard(final KanbanDb.Card k) {
        final boolean isNew = k.id == 0;
        final long[] task = {k.taskId};
        final long[] note = {k.noteId};
        final long[] colId = {k.col};

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(4), dp(20), dp(4));
        final EditText title = field("Card title", k.title, InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, true);
        box.addView(title);
        final EditText detail = field("Details (optional)", k.note, InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, false);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-1, -2);
        dl.topMargin = dp(8);
        box.addView(detail, dl);

        // move to column
        TextView mh = LentUi.text(this, "COLUMN", 12, Pal.muted, true);
        mh.setLetterSpacing(0.08f);
        mh.setPadding(dp(2), dp(14), 0, dp(6));
        box.addView(mh);
        LinearLayout colRow = new LinearLayout(this);
        final List<TextView> chips = new ArrayList<>();
        final List<KanbanDb.Col> cols = db.columns(boardId);
        for (final KanbanDb.Col c : cols) {
            final TextView chip = LentUi.chip(this, c.name, c.id == colId[0]);
            chips.add(chip);
            chip.setOnClickListener(v -> {
                colId[0] = c.id;
                for (int i = 0; i < chips.size(); i++) {
                    boolean on = cols.get(i).id == colId[0];
                    chips.get(i).setTextColor(on ? Pal.onSel : Pal.muted);
                    chips.get(i).setBackground(LentUi.round(this, on ? Pal.sel : Pal.chip, 100));
                }
                Hap.tick(v);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = dp(8);
            colRow.addView(chip, lp);
        }
        HorizontalScrollView ch = new HorizontalScrollView(this);
        ch.setHorizontalScrollBarEnabled(false);
        ch.addView(colRow);
        box.addView(ch);

        // links
        TextView lh = LentUi.text(this, "LINKS", 12, Pal.muted, true);
        lh.setLetterSpacing(0.08f);
        lh.setPadding(dp(2), dp(14), 0, dp(6));
        box.addView(lh);
        final TextView taskBtn = LentUi.pill(this, "", Pal.chip, Pal.ink, 15);
        final TextView noteBtn = LentUi.pill(this, "", Pal.chip, Pal.ink, 15);
        final Runnable refresh = () -> {
            taskBtn.setText(task[0] > 0 ? "☑  " + taskTitle(task[0]) + "   ✕" : "☑  Link a task");
            noteBtn.setText(note[0] > 0 ? "📝  " + noteTitle(note[0]) + "   ✕" : "📝  Link a note");
        };
        refresh.run();
        taskBtn.setOnClickListener(v -> {
            if (task[0] > 0) {
                task[0] = 0;
                refresh.run();
            } else {
                pickTask(id -> {
                    task[0] = id;
                    refresh.run();
                });
            }
        });
        noteBtn.setOnClickListener(v -> {
            if (note[0] > 0) {
                note[0] = 0;
                refresh.run();
            } else {
                pickNote(id -> {
                    note[0] = id;
                    refresh.run();
                });
            }
        });
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, -2);
        bl.bottomMargin = dp(8);
        box.addView(taskBtn, bl);
        box.addView(noteBtn, new LinearLayout.LayoutParams(-1, -2));

        ScrollView sv = new ScrollView(this);
        sv.addView(box);
        Fonts.apply(box);
        Dlg.Builder b = new Dlg.Builder(this);
        b.setTitle(isNew ? "New card" : "Edit card").setView(sv);
        b.setPositiveButton("Save", (d, w) -> {
            String t = title.getText().toString().trim();
            if (t.isEmpty()) {
                Toast.makeText(this, "Give the card a title", Toast.LENGTH_SHORT).show();
                return;
            }
            boolean moved = !isNew && colId[0] != k.col;
            k.title = t;
            k.note = detail.getText().toString().trim();
            k.taskId = task[0];
            k.noteId = note[0];
            if (moved) {
                long old = k.col;
                k.col = old; // keep the old column for save(), then move
                db.save(k);
                db.move(k.id, colId[0], db.count(colId[0]));
            } else {
                k.col = colId[0];
                db.save(k);
            }
            Hap.confirm(sv);
            render();
        }).setNegativeButton("Cancel", null);
        if (!isNew) {
            b.setNeutralButton("Delete", (d, w) -> {
                db.delete(k.id);
                render();
            });
        }
        b.show();
    }

    private String taskTitle(long id) {
        TaskDb t = new TaskDb(this);
        try {
            Task x = t.get(id);
            return x == null ? "Task removed" : x.title;
        } finally {
            t.close();
        }
    }

    private String noteTitle(long id) {
        TaskDb t = new TaskDb(this);
        try {
            Note x = t.note(id);
            return x == null ? "Note removed" : (x.title.isEmpty() ? "Untitled" : x.title);
        } finally {
            t.close();
        }
    }

    private interface IdPick {
        void got(long id);
    }

    private void pickTask(final IdPick p) {
        TaskDb t = new TaskDb(this);
        final List<Task> tasks = new ArrayList<>();
        try {
            for (Task x : t.list(TaskDb.ACTIVE)) tasks.add(x);
        } finally {
            t.close();
        }
        if (tasks.isEmpty()) {
            Toast.makeText(this, "No open tasks to link", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] names = new String[tasks.size()];
        for (int i = 0; i < names.length; i++) names[i] = tasks.get(i).title;
        new Dlg.Builder(this).setTitle("Link a task").setItems(names, (d, which) -> p.got(tasks.get(which).id)).show();
    }

    private void pickNote(final IdPick p) {
        TaskDb t = new TaskDb(this);
        final List<Note> notes;
        try {
            notes = t.notes("");
        } finally {
            t.close();
        }
        if (notes.isEmpty()) {
            Toast.makeText(this, "No notes to link", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] names = new String[notes.size()];
        for (int i = 0; i < names.length; i++) names[i] = notes.get(i).title.isEmpty() ? "Untitled" : notes.get(i).title;
        new Dlg.Builder(this).setTitle("Link a note").setItems(names, (d, which) -> p.got(notes.get(which).id)).show();
    }
}
