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
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;
import java.util.List;

/** The notebook: a searchable list of rich-text notes, stored only on this device. */
public class NotesActivity extends Activity {
    private TaskDb db;
    private float density;
    private int builtDark;
    private final List<Note> notes = new ArrayList<>();
    private final List<Object> rows = new ArrayList<>(); // Notes, plus String section headers
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
        header.addView(text("Notebook", 30, Pal.ink, true), new LinearLayout.LayoutParams(0, -2, 1f));
        IconView sort = new IconView(this, IconView.SORT);
        sort.setContentDescription("Sort notes");
        sort.setOnClickListener(v -> {
            Hap.tick(v);
            sortMenu(v);
        });
        header.addView(sort, new LinearLayout.LayoutParams(dp(48), dp(48)));
        IconView looks = new IconView(this, IconView.LOOKS);
        looks.setContentDescription("Change how notes are previewed");
        looks.setOnClickListener(v -> {
            Hap.tick(v);
            lookDialog();
        });
        header.addView(looks, new LinearLayout.LayoutParams(dp(48), dp(48)));
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
        notes.addAll(db.notes(query, Prefs.noteSort(this)));
        buildRows();
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

    // ------------------------------------------------------------ looks

    private static final int[] LOOK_IDS = {1, 2, 3, 4, 5, 6, 7, 8, 10};
    private static final String[] LOOK_NAMES = {"Current style", "Expanded", "Grid, 2 columns", "Big title cards",
            "Compact rows", "Titles only", "Grouped by date", "Checklist previews", "Pinned strip + list"};
    private static final String[] LOOK_DESC = {
            "Title, two lines of text and the edit time.",
            "Longer previews, up to six lines.",
            "Two notes side by side.",
            "Coloured cards with a large title.",
            "One line per note, with its time.",
            "Just the title. Fits the most notes.",
            "Today, Yesterday, This week, Earlier.",
            "To-do items shown as a checklist.",
            "Pinned notes as tiles on top, a compact list below."};

    /** Pinned tiles shown across the top in look 10. */
    private static final class PinStrip {
        final List<Note> notes;

        PinStrip(List<Note> notes) {
            this.notes = notes;
        }
    }

    private void lookDialog() {
        final int cur = Prefs.noteLook(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(4), dp(12), 0);
        final AlertDialog[] dlg = new AlertDialog[1];
        for (int i = 0; i < LOOK_IDS.length; i++) {
            final int id = LOOK_IDS[i];
            boolean on = cur == id;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(10), dp(10), dp(10), dp(10));
            GradientDrawable rg = new GradientDrawable();
            rg.setCornerRadius(dp(16));
            rg.setColor(on ? Pal.chip : android.graphics.Color.TRANSPARENT);
            if (on) rg.setStroke(dp(2), Pal.accent);
            row.setBackground(rg);
            TextView dot = text(on ? "●" : "○", 18, on ? Pal.accent : Pal.muted, true);
            dot.setGravity(Gravity.CENTER);
            row.addView(dot, new LinearLayout.LayoutParams(dp(30), -2));
            LinearLayout colx = new LinearLayout(this);
            colx.setOrientation(LinearLayout.VERTICAL);
            colx.addView(text(id + " · " + LOOK_NAMES[i], 16, Pal.ink, true));
            TextView d = text(LOOK_DESC[i], 13, Pal.muted, false);
            d.setPadding(0, dp(1), 0, 0);
            colx.addView(d);
            row.addView(colx, new LinearLayout.LayoutParams(0, -2, 1f));
            row.setOnClickListener(v -> {
                Hap.tick(v);
                Prefs.setNoteLook(this, id);
                dlg[0].dismiss();
                refresh();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = dp(2);
            box.addView(row, lp);
        }
        ScrollView sv = new ScrollView(this);
        sv.addView(box);
        dlg[0] = new Dlg.Builder(this)
                .setTitle("Note previews")
                .setView(sv)
                .setNegativeButton("Close", null)
                .create();
        dlg[0].show();
        Dlg.style(dlg[0]);
    }

    private static final String[] SORT_NAMES = {"Date edited (newest)", "Date edited (oldest)", "Date created",
            "Title A to Z", "Title Z to A"};

    /** The sort menu: a small card under the button, as on the design board. */
    private void sortMenu(View anchor) {
        final int cur = Prefs.noteSort(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(8), dp(8), dp(8), dp(4));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Pal.raised);
        bg.setCornerRadius(dp(20));
        box.setBackground(bg);
        final android.widget.PopupWindow pop = new android.widget.PopupWindow(box, dp(260),
                ViewGroup.LayoutParams.WRAP_CONTENT, true);
        pop.setBackgroundDrawable(new ColorDrawable(0));
        pop.setElevation(dp(12));
        TextView head = text("SORT BY", 12, Pal.muted, true);
        head.setLetterSpacing(0.08f);
        head.setPadding(dp(12), dp(10), dp(12), dp(6));
        box.addView(head);
        for (int i = 0; i < SORT_NAMES.length; i++) {
            final int id = i;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), 0, dp(12), 0);
            GradientDrawable rg = new GradientDrawable();
            rg.setCornerRadius(dp(14));
            rg.setColor(cur == i ? HabitUi.blend(Pal.accent, Pal.raised, 0.14f) : android.graphics.Color.TRANSPARENT);
            row.setBackground(rg);
            TextView name = text(SORT_NAMES[i], 16, Pal.ink, true);
            row.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
            if (cur == i) row.addView(text("\u2713", 18, Pal.accent, true));
            row.setOnClickListener(v -> {
                Hap.tick(v);
                Prefs.setNoteSort(this, id);
                pop.dismiss();
                refresh();
            });
            box.addView(row, new LinearLayout.LayoutParams(-1, dp(46)));
        }
        View line = new View(this);
        line.setBackgroundColor(Pal.chip);
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(-1, dp(1));
        ll.setMargins(dp(12), dp(6), dp(12), 0);
        box.addView(line, ll);
        TextView foot = text("Pinned notes always stay on top", 13, Pal.muted, false);
        foot.setPadding(dp(12), dp(10), dp(12), dp(8));
        box.addView(foot);
        Fonts.apply(box);
        pop.showAsDropDown(anchor, 0, 0, Gravity.END);
    }

    private void buildRows() {
        rows.clear();
        int look = Prefs.noteLook(this);
        List<Note> pinned = new ArrayList<>();
        List<Note> others = new ArrayList<>();
        for (Note n : notes) (n.pinned > 0 ? pinned : others).add(n);
        String pinHead = "PINNED  " + pinned.size() + " of " + Prefs.maxPins(this);
        if (look == 10) { // pinned strip + compact list
            if (!pinned.isEmpty()) {
                rows.add(pinHead);
                rows.add(new PinStrip(pinned));
                if (!others.isEmpty()) rows.add("ALL NOTES");
            }
            rows.addAll(others);
            return;
        }
        if (!pinned.isEmpty()) {
            rows.add(pinHead);
            addItems(pinned, look);
            if (!others.isEmpty() && (look != 7 || Prefs.noteSort(this) > 1)) rows.add("NOTES");
        }
        if (look == 7 && Prefs.noteSort(this) <= 1) { // grouped by date (only while sorted by edit date)
            String last = null;
            for (Note n : others) {
                String g = group(n.updated);
                if (!g.equals(last)) {
                    rows.add(g);
                    last = g;
                }
                rows.add(n);
            }
        } else {
            addItems(others, look);
        }
    }

    private void addItems(List<Note> list, int look) {
        if (look == 3) { // two per row
            for (int i = 0; i < list.size(); i += 2) {
                rows.add(new Note[]{list.get(i), i + 1 < list.size() ? list.get(i + 1) : null});
            }
        } else {
            rows.addAll(list);
        }
    }

    private static long dayStart(long t) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private String group(long t) {
        long today = dayStart(System.currentTimeMillis());
        long day = dayStart(t);
        if (day >= today) return "TODAY";
        if (day >= today - 86400000L) return "YESTERDAY";
        if (day >= today - 6 * 86400000L) return "THIS WEEK";
        return "EARLIER";
    }

    /** "14:20" today, "Yest.", "Mon", or "Oct 9". */
    private String shortWhen(long t) {
        long today = dayStart(System.currentTimeMillis());
        long day = dayStart(t);
        if (day >= today) return android.text.format.DateFormat.getTimeFormat(this).format(new java.util.Date(t));
        if (day >= today - 86400000L) return "Yest.";
        if (day >= today - 6 * 86400000L) {
            return new java.text.SimpleDateFormat("EEE", Locale.getDefault()).format(new java.util.Date(t));
        }
        return new java.text.SimpleDateFormat("MMM d", Locale.getDefault()).format(new java.util.Date(t));
    }

    private void togglePin(Note n) {
        if (n.pinned > 0) {
            db.setPinned(n.id, false);
        } else if (db.pinnedCount() >= Prefs.maxPins(this)) {
            android.widget.Toast.makeText(this, "You can pin up to " + Prefs.maxPins(this)
                    + " notes. Change this in Settings > Notebook.", android.widget.Toast.LENGTH_LONG).show();
            return;
        } else {
            db.setPinned(n.id, true);
        }
        refresh();
    }

    /** Choose one pinned note's own colour: any of the styles, or follow Settings. */
    private void pinColorDialog(final Note n) {
        final LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(8), dp(16), 0);
        final android.app.AlertDialog[] dlg = new android.app.AlertDialog[1];
        int current = n.pinColor;
        for (int start = 0; start < PinTint.COUNT; start += 5) {
            LinearLayout line = new LinearLayout(this);
            for (int i = start; i < start + 5 && i < PinTint.COUNT; i++) {
                final int idx = i;
                LinearLayout cell = new LinearLayout(this);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER_HORIZONTAL);
                cell.setPadding(dp(3), dp(4), dp(3), dp(4));
                View sw = new View(this);
                GradientDrawable g = new GradientDrawable();
                g.setCornerRadius(dp(12));
                PinTint.paint(g, i, 0);
                g.setStroke(dp(i == current ? 3 : 1), i == current ? Pal.ink : Pal.chip);
                sw.setBackground(g);
                cell.addView(sw, new LinearLayout.LayoutParams(-1, dp(44)));
                TextView lab = text(PinTint.NAMES[i], 11, i == current ? Pal.ink : Pal.muted, i == current);
                lab.setGravity(Gravity.CENTER);
                lab.setPadding(0, dp(4), 0, 0);
                cell.addView(lab);
                cell.setOnClickListener(v -> {
                    Hap.tick(v);
                    db.setPinColor(n.id, idx);
                    if (dlg[0] != null) dlg[0].dismiss();
                    refresh();
                });
                line.addView(cell, new LinearLayout.LayoutParams(0, -2, 1f));
            }
            box.addView(line);
        }
        TextView follow = text(current < 0 ? "\u2713  Follow Settings" : "Follow Settings", 14,
                current < 0 ? Pal.onSel : Pal.ink, true);
        follow.setGravity(Gravity.CENTER);
        follow.setMinHeight(dp(44));
        GradientDrawable fg = new GradientDrawable();
        fg.setCornerRadius(dp(22));
        fg.setColor(current < 0 ? Pal.sel : Pal.chip);
        follow.setBackground(fg);
        follow.setOnClickListener(v -> {
            Hap.tick(v);
            db.setPinColor(n.id, -1);
            if (dlg[0] != null) dlg[0].dismiss();
            refresh();
        });
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(-1, -2);
        flp.topMargin = dp(10);
        box.addView(follow, flp);
        dlg[0] = new Dlg.Builder(this)
                .setTitle("Colour for this note")
                .setView(scrollOf(box))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private View scrollOf(View v) {
        ScrollView sv = new ScrollView(this);
        sv.addView(v);
        return sv;
    }

    private void noteMenu(final Note n) {
        new Dlg.Builder(this)
                .setTitle(n.title.isEmpty() ? "Untitled" : n.title)
                .setItems(n.pinned > 0 ? new String[]{"Unpin", "Pinned colour\u2026", "Delete"} : new String[]{"Pin to top", "Delete"}, (d, which) -> {
                    if (which == 0) togglePin(n);
                    else if (n.pinned > 0 && which == 1) pinColorDialog(n);
                    else confirmDelete(n);
                })
                .show();
    }

    // ----------------------------------------------------------- adapter

    private final class NoteAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return rows.size();
        }

        @Override
        public Object getItem(int i) {
            return rows.get(i);
        }

        @Override
        public long getItemId(int i) {
            Object o = rows.get(i);
            return o instanceof Note ? ((Note) o).id : -1 - i;
        }

        @Override
        public View getView(int i, View convert, ViewGroup parent) {
            Object o = getItem(i);
            View v;
            if (o instanceof String) {
                TextView h = text((String) o, 12, Pal.muted, true);
                h.setLetterSpacing(0.08f);
                h.setPadding(dp(28), dp(12), dp(16), dp(4));
                v = h;
            } else if (o instanceof PinStrip) {
                v = pinStrip(((PinStrip) o).notes);
            } else if (o instanceof Note[]) {
                Note[] pair = (Note[]) o;
                LinearLayout row = new LinearLayout(NotesActivity.this);
                row.setPadding(dp(16), 0, dp(16), 0);
                for (int k = 0; k < 2; k++) {
                    LinearLayout cell = new LinearLayout(NotesActivity.this);
                    cell.setOrientation(LinearLayout.VERTICAL);
                    if (pair[k] != null) cell.addView(card(pair[k], 5, 12, 14, false, false, dp(4), true));
                    LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -2, 1f);
                    row.addView(cell, cp);
                }
                v = row;
            } else {
                v = noteView((Note) o, i);
            }
            Fonts.apply(v);
            return v;
        }
    }

    private View noteView(Note n, int position) {
        switch (Prefs.noteLook(this)) {
            case 2:
                return card(n, 6, 14, 17, false, false, dp(16), false);
            case 4:
                return bigCard(n, position);
            case 5:
                return compactRow(n, position);
            case 6:
                return card(n, 0, 14, 16, false, false, dp(16), false);
            case 7:
                return card(n, 1, 12, 15, false, false, dp(16), false);
            case 8:
                return card(n, 2, 13, 17, true, true, dp(16), false);
            case 10:
                return card(n, 1, 12, 15, false, false, dp(16), false);
            default:
                return card(n, 2, 14, 17, true, false, dp(16), false);
        }
    }

    /** The preview: to-do lines when asked and present, otherwise the text on one flowing line. */
    private String previewOf(Note n, boolean checklist) {
        if (checklist) {
            StringBuilder sb = new StringBuilder();
            int c = 0;
            for (String line : n.plain.split("\n")) {
                String t = line.trim();
                if (t.startsWith("☐") || t.startsWith("☑")) {
                    if (c > 0) sb.append('\n');
                    sb.append(t);
                    if (++c == 3) break;
                }
            }
            if (c > 0) return sb.toString();
        }
        return n.plain.replace('\n', ' ').trim();
    }

    /** A white note card with a pin in the corner; the arguments pick how much of the note it shows. */
    private View card(final Note n, int lines, int previewSp, int titleSp, boolean edited, boolean checklist,
                      int sidePad, boolean cell) {
        Context c = this;
        FrameLayout wrap = new FrameLayout(c);
        wrap.setPadding(cell ? dp(4) : dp(16), dp(4), cell ? dp(4) : dp(16), dp(4));
        if (cell) wrap.setPadding(dp(4), dp(4), dp(4), dp(4));

        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(cell ? 12 : 16), dp(cell ? 12 : 14), dp(cell ? 12 : 16), dp(cell ? 12 : 14));
        final int ps = PinTint.of(c, n);
        final boolean tinted = n.pinned > 0 && PinTint.solid(ps);
        final int fg = tinted ? PinTint.fg(ps, n.id) : 0;
        GradientDrawable g = new GradientDrawable();
        if (n.pinned > 0 && PinTint.isGradient(ps)) PinTint.paint(g, ps, n.id);
        else g.setColor(n.pinned > 0 ? PinTint.bg(ps, n.id) : Pal.card);
        g.setCornerRadius(dp(cell ? 14 : 16));
        card.setBackground(g);

        TextView title = text(n.title.isEmpty() ? "Untitled" : n.title, titleSp,
                tinted ? fg : n.title.isEmpty() ? Pal.muted : Pal.ink, true);
        title.setSingleLine(!cell);
        if (cell) title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setPadding(0, 0, dp(32), 0); // room for the pin
        card.addView(title);

        String preview = lines > 0 ? previewOf(n, checklist) : "";
        if (!preview.isEmpty()) {
            TextView p = text(preview, previewSp, tinted ? fg : Pal.muted, false);
            if (tinted) p.setAlpha(0.85f);
            boolean list = checklist && preview.indexOf('\n') >= 0;
            p.setMaxLines(list ? 3 : lines);
            p.setEllipsize(TextUtils.TruncateAt.END);
            p.setPadding(0, dp(4), 0, 0);
            if (list) p.setLineSpacing(dp(4), 1f);
            card.addView(p);
        }
        if (edited) {
            TextView when = text("Edited " + Reminders.when(c, n.updated), 12, tinted ? fg : Pal.muted, false);
            if (tinted) when.setAlpha(0.8f);
            when.setPadding(0, dp(8), 0, 0);
            card.addView(when);
        }
        card.setOnClickListener(v -> openNote(n.id));
        card.setOnLongClickListener(v -> {
            noteMenu(n);
            return true;
        });
        wrap.addView(card, new FrameLayout.LayoutParams(-1, -2));
        wrap.addView(pinButton(n, tinted ? fg : 0, cell ? 40 : 44), pinLp(cell ? 40 : 44));
        return wrap;
    }

    private IconView pinButton(final Note n, int tint, int size) {
        IconView pin = new IconView(this, IconView.PIN);
        pin.setOn(n.pinned > 0);
        if (tint != 0) pin.setTint(tint);
        pin.setContentDescription(n.pinned > 0 ? "Unpin note" : "Pin note");
        pin.setOnClickListener(v -> {
            Hap.tick(v);
            togglePin(n);
        });
        pin.setOnLongClickListener(v -> { // hold the pin to choose this note's colour
            Hap.tick(v);
            if (n.pinned == 0) {
                if (db.pinnedCount() >= Prefs.maxPins(this)) {
                    togglePin(n); // shows the limit message
                    return true;
                }
                db.setPinned(n.id, true);
                refresh();
            }
            pinColorDialog(n);
            return true;
        });
        return pin;
    }

    private FrameLayout.LayoutParams pinLp(int size) {
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(dp(size), dp(size));
        plp.gravity = Gravity.TOP | Gravity.END;
        plp.topMargin = dp(2);
        plp.rightMargin = dp(2);
        return plp;
    }

    /** Look 4: a coloured card with a large title. */
    private View bigCard(final Note n, int position) {
        int idx = position % 3;
        int bg = idx == 0 ? Pal.accent : idx == 1 ? 0xFFF2A93B : (Pal.dark ? Pal.sel : 0xFF0E1513);
        int fg = idx == 0 ? Pal.onAccent : idx == 1 ? 0xFF0E1513 : (Pal.dark ? Pal.onSel : 0xFFFFFFFF);
        int ps = PinTint.of(this, n);
        if (n.pinned > 0 && (ps != 0 || n.pinColor >= 0)) { // pinned notes follow the chosen pinned colour
            bg = PinTint.bg(ps, n.id);
            fg = PinTint.solid(ps) ? PinTint.fg(ps, n.id) : Pal.ink;
        }
        FrameLayout wrap = new FrameLayout(this);
        wrap.setPadding(dp(16), dp(4), dp(16), dp(4));
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(14));
        card.setMinimumHeight(dp(96));
        GradientDrawable g = new GradientDrawable();
        g.setColor(bg);
        if (n.pinned > 0 && PinTint.isGradient(ps)) PinTint.paint(g, ps, n.id);
        g.setCornerRadius(dp(16));
        card.setBackground(g);
        TextView title = text(n.title.isEmpty() ? "Untitled" : n.title, 22, fg, true);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setPadding(0, 0, dp(32), 0);
        card.addView(title);
        TextView when = text(shortWhen(n.updated), 11, fg, false);
        when.setAlpha(0.8f);
        when.setPadding(0, dp(6), 0, 0);
        card.addView(when);
        card.setOnClickListener(v -> openNote(n.id));
        card.setOnLongClickListener(v -> {
            noteMenu(n);
            return true;
        });
        wrap.addView(card, new FrameLayout.LayoutParams(-1, -2));
        wrap.addView(pinButton(n, fg, 44), pinLp(44));
        return wrap;
    }

    /** Look 5: one line per note, rows share one rounded block. */
    private View compactRow(final Note n, int position) {
        boolean first = position == 0 || !(rows.get(position - 1) instanceof Note);
        boolean last = position + 1 >= rows.size() || !(rows.get(position + 1) instanceof Note);
        FrameLayout wrap = new FrameLayout(this);
        wrap.setPadding(dp(16), 0, dp(16), 0);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        final int ps = PinTint.of(this, n);
        final boolean tinted = n.pinned > 0 && PinTint.solid(ps);
        final int fg = tinted ? PinTint.fg(ps, n.id) : 0;
        GradientDrawable g = new GradientDrawable();
        if (n.pinned > 0 && PinTint.isGradient(ps)) PinTint.paint(g, ps, n.id);
        else g.setColor(n.pinned > 0 ? PinTint.bg(ps, n.id) : Pal.card);
        float r = dp(14);
        g.setCornerRadii(new float[]{first ? r : 0, first ? r : 0, first ? r : 0, first ? r : 0,
                last ? r : 0, last ? r : 0, last ? r : 0, last ? r : 0});
        row.setBackground(g);
        if (n.pinned > 0) {
            IconView mark = new IconView(this, IconView.PIN);
            mark.setClickable(false);
            mark.setOn(true);
            if (tinted) mark.setTint(fg);
            LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(dp(22), dp(22));
            mp.rightMargin = dp(6);
            row.addView(mark, mp);
        }
        TextView title = text(n.title.isEmpty() ? "Untitled" : n.title, 14,
                tinted ? fg : n.title.isEmpty() ? Pal.muted : Pal.ink, true);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView when = text(shortWhen(n.updated), 12, tinted ? fg : Pal.muted, false);
        if (tinted) when.setAlpha(0.8f);
        when.setPadding(dp(8), 0, 0, 0);
        row.addView(when);
        row.setOnClickListener(v -> openNote(n.id));
        row.setOnLongClickListener(v -> {
            noteMenu(n);
            return true;
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        wrap.addView(row, new FrameLayout.LayoutParams(-1, -2));
        if (!last) { // a hairline between rows
            View line = new View(this);
            line.setBackgroundColor(Pal.bg);
            FrameLayout.LayoutParams ll = new FrameLayout.LayoutParams(-1, dp(1));
            ll.gravity = Gravity.BOTTOM;
            wrap.addView(line, ll);
        }
        return wrap;
    }

    /** Look 10: pinned notes as tiles across the top. */
    private View pinStrip(List<Note> pinned) {
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout row = new LinearLayout(this);
        row.setPadding(dp(16), dp(2), dp(8), dp(8));
        int tileW = (getResources().getDisplayMetrics().widthPixels - dp(16 + 16 + 8)) / 2;
        for (final Note n : pinned) {
            FrameLayout tile = new FrameLayout(this);
            GradientDrawable g = new GradientDrawable();
            int ps = PinTint.of(this, n);
            int tileBg = ps == 0 ? Pal.accent : PinTint.bg(ps, n.id);
            int tileFg = ps == 0 ? Pal.onAccent : PinTint.solid(ps) ? PinTint.fg(ps, n.id) : Pal.ink;
            g.setColor(tileBg);
            if (PinTint.isGradient(ps)) PinTint.paint(g, ps, n.id);
            g.setCornerRadius(dp(14));
            tile.setBackground(g);
            TextView title = text(n.title.isEmpty() ? "Untitled" : n.title, 13, tileFg, true);
            title.setMaxLines(3);
            title.setEllipsize(TextUtils.TruncateAt.END);
            title.setPadding(dp(10), dp(10), dp(30), dp(10));
            tile.addView(title, new FrameLayout.LayoutParams(-1, -1));
            tile.addView(pinButton(n, tileFg, 32), pinLp(32));
            tile.setOnClickListener(v -> openNote(n.id));
            tile.setOnLongClickListener(v -> {
                noteMenu(n);
                return true;
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(tileW, dp(70));
            lp.rightMargin = dp(8);
            row.addView(tile, lp);
        }
        hs.addView(row);
        return hs;
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
