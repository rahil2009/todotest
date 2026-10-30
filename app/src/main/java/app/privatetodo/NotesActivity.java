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
import android.widget.Toast;

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
        Lock.guard(this, Lock.NOTES);
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

    private long openFolder = 0; // 0 = the main notebook, otherwise the folder that is open
    private TextView titleView;
    private TextView crumb;
    private TextView newLabel;
    private IconView folderBtn;
    private IconView moreBtn;
    private IconView searchBtn;
    private EditText searchField;
    private HorizontalScrollView chipsScroll;
    private LinearLayout chips;

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
        back.setOnClickListener(v -> goBack());
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        titleView = text("Notebook", 30, Pal.ink, true);
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        header.addView(titleView, new LinearLayout.LayoutParams(0, -2, 1f));
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
        folderBtn = new IconView(this, IconView.FOLDER);
        folderBtn.setTint(Pal.accent);
        folderBtn.setContentDescription("New folder");
        folderBtn.setOnClickListener(v -> {
            Hap.tick(v);
            folderDialog(null, null);
        });
        header.addView(folderBtn, new LinearLayout.LayoutParams(dp(48), dp(48)));
        moreBtn = new IconView(this, IconView.MORE);
        moreBtn.setContentDescription("Folder options");
        moreBtn.setOnClickListener(v -> {
            Hap.tick(v);
            Folder f = db.folder(openFolder);
            if (f != null) folderMenu(f);
        });
        header.addView(moreBtn, new LinearLayout.LayoutParams(dp(48), dp(48)));
        col.addView(header);

        // "Notebook > Health . 3 notes", only inside a folder
        crumb = text("", 13, Pal.muted, false);
        crumb.setPadding(dp(28), dp(2), dp(16), dp(6));
        col.addView(crumb);

        // Folder chips: pills with the folder's colour, a count, and "+ Folder"
        chipsScroll = new HorizontalScrollView(this);
        chipsScroll.setHorizontalScrollBarEnabled(false);
        chipsScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setPadding(dp(16), 0, dp(8), 0);
        chipsScroll.addView(chips);
        LinearLayout.LayoutParams chp = new LinearLayout.LayoutParams(-1, -2);
        chp.topMargin = dp(16);
        chp.bottomMargin = dp(8);
        col.addView(chipsScroll, chp);

        // Search: opened with the button next to "New note"
        searchField = new EditText(this);
        searchField.setHint("Search notes…");
        searchField.setHintTextColor(Pal.muted);
        searchField.setTextColor(Pal.ink);
        searchField.setTextSize(16);
        searchField.setSingleLine(true);
        searchField.setInputType(InputType.TYPE_CLASS_TEXT);
        searchField.setPadding(dp(20), 0, dp(20), 0);
        GradientDrawable sbg = new GradientDrawable();
        sbg.setColor(Pal.card);
        sbg.setCornerRadius(dp(24));
        searchField.setBackground(sbg);
        searchField.setVisibility(View.GONE);
        searchField.addTextChangedListener(new TextWatcher() {
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
        slp.setMargins(dp(16), dp(4), dp(16), dp(8));
        col.addView(searchField, slp);

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

        // Bottom bar: "New note" on one line, and a search button right next to it
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
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
        newLabel = text("New note", 16, Pal.onAccent, true);
        newLabel.setSingleLine(true);
        newLabel.setEllipsize(null);
        pill.addView(newLabel, new LinearLayout.LayoutParams(-2, -2));
        pill.setOnClickListener(v -> openNote(0));
        bar.addView(pill, new LinearLayout.LayoutParams(-2, dp(56)));
        searchBtn = new IconView(this, IconView.SEARCH);
        searchBtn.setContentDescription("Search notes");
        GradientDrawable sg = new GradientDrawable();
        sg.setShape(GradientDrawable.OVAL);
        sg.setColor(Pal.card);
        sg.setStroke(dp(2), Pal.accent); // a clear ring so it never blends into the notes behind it
        searchBtn.setBackground(sg);
        searchBtn.setTint(Pal.accent);
        searchBtn.setOnClickListener(v -> {
            Hap.tick(v);
            toggleSearch();
        });
        LinearLayout.LayoutParams sblp = new LinearLayout.LayoutParams(dp(56), dp(56));
        sblp.leftMargin = dp(8);
        bar.addView(searchBtn, sblp);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(-2, dp(56));
        plp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        plp.bottomMargin = dp(24);
        root.addView(bar, plp);
        return root;
    }

    private void toggleSearch() {
        android.view.inputmethod.InputMethodManager imm =
                (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (searchField.getVisibility() == View.VISIBLE) {
            searchField.setText("");
            searchField.setVisibility(View.GONE);
            searchBtn.setOn(false);
            if (imm != null) imm.hideSoftInputFromWindow(searchField.getWindowToken(), 0);
        } else {
            searchField.setVisibility(View.VISIBLE);
            searchBtn.setOn(true);
            searchField.requestFocus();
            if (imm != null) imm.showSoftInput(searchField, 0);
        }
        refresh();
    }

    private void goBack() {
        if (searchField.getVisibility() == View.VISIBLE) {
            toggleSearch();
        } else if (openFolder > 0) {
            openFolder = 0;
            refresh();
        } else {
            finish();
        }
    }

    @Override
    public void onBackPressed() {
        goBack();
    }

    // ------------------------------------------------------------ folders

    /** The folder chips under the title: one per folder, then "+ Folder". */
    private void buildChips(List<Folder> folders) {
        chips.removeAllViews();
        // Shared notes: links sent here from other apps, always the first chip
        LinearLayout shared = chip();
        TextView link = text("\uD83D\uDD17", 15, Pal.ink, false);
        link.setPadding(0, 0, dp(2), 0);
        shared.addView(link);
        TextView sname = text("Shared notes", 14, Pal.ink, true);
        sname.setPadding(dp(4), 0, 0, 0);
        shared.addView(sname);
        TextView scnt = text(String.valueOf(db.sharedCount()), 12, Pal.muted, false);
        scnt.setPadding(dp(10), 0, 0, 0);
        shared.addView(scnt);
        shared.setOnClickListener(v -> {
            Hap.tick(v);
            startActivity(new Intent(this, SharedActivity.class));
        });
        chips.addView(shared, chipLp());
        for (final Folder f : folders) {
            LinearLayout chip = chip();
            IconView ic = new IconView(this, IconView.FOLDER);
            ic.setTint(f.rgb());
            ic.setOn(true);
            ic.setClickable(false);
            chip.addView(ic, new LinearLayout.LayoutParams(dp(26), dp(26)));
            TextView name = text(f.name, 14, Pal.ink, true);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            name.setMaxWidth(dp(120));
            name.setPadding(dp(4), 0, 0, 0);
            chip.addView(name);
            TextView cnt = text(String.valueOf(f.count), 12, Pal.muted, false);
            cnt.setPadding(dp(10), 0, 0, 0);
            chip.addView(cnt);
            chip.setOnClickListener(v -> {
                Hap.tick(v);
                openFolder = f.id;
                refresh();
            });
            chip.setOnLongClickListener(v -> {
                Hap.confirm(v);
                folderMenu(f);
                return true;
            });
            chips.addView(chip, chipLp());
        }
        LinearLayout add = chip();
        IconView plus = new IconView(this, IconView.PLUS);
        plus.setClickable(false);
        add.addView(plus, new LinearLayout.LayoutParams(dp(26), dp(26)));
        TextView lab = text("Folder", 14, Pal.ink, true);
        lab.setPadding(dp(4), 0, 0, 0);
        add.addView(lab);
        add.setOnClickListener(v -> {
            Hap.tick(v);
            folderDialog(null, null);
        });
        chips.addView(add, chipLp());
        // The Bin: deleted notes wait here for 30 days
        LinearLayout bin = chip();
        IconView bi = new IconView(this, IconView.TRASH);
        bi.setClickable(false);
        bin.addView(bi, new LinearLayout.LayoutParams(dp(26), dp(26)));
        TextView bname = text("Bin", 14, Pal.ink, true);
        bname.setPadding(dp(4), 0, 0, 0);
        bin.addView(bname);
        int bc = db.binCount();
        if (bc > 0) {
            TextView bcnt = text(String.valueOf(bc), 12, Pal.muted, false);
            bcnt.setPadding(dp(10), 0, 0, 0);
            bin.addView(bcnt);
        }
        bin.setOnClickListener(v -> {
            Hap.tick(v);
            startActivity(new Intent(this, BinActivity.class));
        });
        chips.addView(bin, chipLp());
    }

    private LinearLayout chip() {
        LinearLayout chip = new LinearLayout(this);
        chip.setOrientation(LinearLayout.HORIZONTAL);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setPadding(dp(12), 0, dp(14), 0);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(22));
        g.setColor(Pal.card);
        chip.setBackground(g);
        chip.setClickable(true);
        return chip;
    }

    private LinearLayout.LayoutParams chipLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(44));
        lp.rightMargin = dp(8);
        return lp;
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
    private void folderDialog(final Folder existing, final java.util.function.LongConsumer done) {
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
                    long id;
                    if (existing == null) {
                        id = db.addFolder(nm, sel[0]);
                    } else {
                        id = existing.id;
                        db.updateFolder(id, nm, sel[0]);
                    }
                    if (done != null) done.accept(id);
                    refresh();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void folderMenu(final Folder f) {
        new Dlg.Builder(this)
                .setTitle(f.name)
                .setItems(new String[]{"Rename or change colour", "Delete folder"}, (d, which) -> {
                    if (which == 0) {
                        folderDialog(f, null);
                    } else {
                        new Dlg.Builder(this)
                                .setTitle("Delete folder?")
                                .setMessage(f.count == 0 ? f.name
                                        : f.name + "\nIts " + f.count + " note" + (f.count == 1 ? "" : "s")
                                        + " go back to the notebook. No note is deleted.")
                                .setPositiveButton("Delete", (d2, w2) -> {
                                    db.deleteFolder(f.id);
                                    if (openFolder == f.id) openFolder = 0;
                                    refresh();
                                })
                                .setNegativeButton("Keep", null)
                                .show();
                    }
                })
                .show();
    }

    /** Pick the folder a note lives in (or take it out of its folder). */
    private void moveDialog(final Note n) {
        final List<Folder> fs = db.folders();
        final List<String> names = new ArrayList<>();
        final List<Long> ids = new ArrayList<>();
        names.add(n.folder == 0 ? "✓  Notebook (no folder)" : "Notebook (no folder)");
        ids.add(0L);
        for (Folder f : fs) {
            names.add((n.folder == f.id ? "✓  " : "") + f.name);
            ids.add(f.id);
        }
        names.add("+ New folder…");
        new Dlg.Builder(this)
                .setTitle("Move to folder")
                .setItems(names.toArray(new String[0]), (d, which) -> {
                    if (which == names.size() - 1) {
                        folderDialog(null, id -> {
                            db.setNoteFolder(n.id, id);
                            refresh();
                        });
                    } else {
                        db.setNoteFolder(n.id, ids.get(which));
                        refresh();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** A card pinned to the bottom of the screen (easy to reach with one hand) over a light dim. */
    private android.app.Dialog bottomSheet(View card) {
        FrameLayout wrapper = new FrameLayout(this);
        wrapper.setPadding(dp(16), 0, dp(16), dp(16));
        wrapper.addView(card, new FrameLayout.LayoutParams(-1, -2));
        final android.app.Dialog dlg = new android.app.Dialog(this);
        dlg.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dlg.setContentView(wrapper);
        android.view.Window w = dlg.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0));
            w.setGravity(Gravity.BOTTOM);
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            w.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setDimAmount(0.35f);
        }
        dlg.setCanceledOnTouchOutside(true);
        return dlg;
    }

    private void refresh() {
        List<Folder> folders = db.folders();
        Folder open = null;
        for (Folder f : folders) if (f.id == openFolder) open = f;
        if (open == null) openFolder = 0;
        boolean searching = !query.isEmpty();
        notes.clear();
        // The main notebook lists notes that are in no folder; a search looks through every note.
        notes.addAll(db.notes(query, Prefs.noteSort(this), searching ? -1 : openFolder));
        buildRows();
        adapter.notifyDataSetChanged();
        boolean empty = notes.isEmpty();
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        list.setVisibility(empty ? View.GONE : View.VISIBLE);
        emptyTitle.setText(searching ? "No matches" : open != null ? "No notes in this folder" : "No notes yet");

        titleView.setText(open != null ? open.name : "Notebook");
        crumb.setVisibility(open != null ? View.VISIBLE : View.GONE);
        if (open != null) {
            crumb.setText("Notebook › " + open.name + " · " + open.count + " note"
                    + (open.count == 1 ? "" : "s"));
        }
        folderBtn.setVisibility(open != null ? View.GONE : View.VISIBLE);
        moreBtn.setVisibility(open != null ? View.VISIBLE : View.GONE);
        chipsScroll.setVisibility(open == null && !searching ? View.VISIBLE : View.GONE);
        if (open == null) buildChips(folders);
        newLabel.setText(open != null ? "New note in " + open.name : "New note");
        Fonts.apply(chips);
    }

    private void openNote(long id) {
        startActivity(new Intent(this, NoteEditorActivity.class)
                .putExtra(NoteEditorActivity.EXTRA_ID, id)
                .putExtra(NoteEditorActivity.EXTRA_FOLDER, openFolder));
    }

    private void confirmDelete(final Note n) {
        new Dlg.Builder(this)
                .setTitle("Move note to the Bin?")
                .setMessage((n.title.isEmpty() ? "Untitled" : n.title)
                        + "\n\nIt stays in the Bin for " + TaskDb.BIN_DAYS + " days, so you can bring it back.")
                .setPositiveButton("Move to Bin", (d, w) -> {
                    db.trashNote(n.id);
                    Toast.makeText(this, "Moved to Bin", Toast.LENGTH_SHORT).show();
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

    /** The sort menu: a card at the bottom of the screen, as on the design board. */
    private void sortMenu(View anchor) {
        final int cur = Prefs.noteSort(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(8), dp(8), dp(8), dp(4));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Pal.raised);
        bg.setCornerRadius(dp(20));
        box.setBackground(bg);
        final android.app.Dialog pop = bottomSheet(box);
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
            if (cur == i) row.addView(text("✓", 18, Pal.accent, true));
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
        pop.show();
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

    /** Choose one pinned note's own colour: any of the styles, or follow Settings. A sheet at the bottom. */
    private void pinColorDialog(final Note n) {
        final LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(22), dp(18), dp(14));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Pal.raised);
        bg.setCornerRadius(dp(28));
        box.setBackground(bg);
        final android.app.Dialog dlg = bottomSheet(box);
        TextView title = text("Colour for this note", 20, Pal.ink, true);
        title.setPadding(dp(4), 0, 0, dp(8));
        box.addView(title);
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
                    dlg.dismiss();
                    refresh();
                });
                line.addView(cell, new LinearLayout.LayoutParams(0, -2, 1f));
            }
            box.addView(line);
        }
        // "Follow Settings" and "Cancel" share one line
        LinearLayout foot = new LinearLayout(this);
        foot.setOrientation(LinearLayout.HORIZONTAL);
        foot.setGravity(Gravity.CENTER_VERTICAL);
        TextView follow = text(current < 0 ? "✓  Follow Settings" : "Follow Settings", 14,
                current < 0 ? Pal.onSel : Pal.ink, true);
        follow.setGravity(Gravity.CENTER);
        GradientDrawable fg = new GradientDrawable();
        fg.setCornerRadius(dp(22));
        fg.setColor(current < 0 ? Pal.sel : Pal.chip);
        follow.setBackground(fg);
        follow.setOnClickListener(v -> {
            Hap.tick(v);
            db.setPinColor(n.id, -1);
            dlg.dismiss();
            refresh();
        });
        foot.addView(follow, new LinearLayout.LayoutParams(0, dp(44), 1f));
        TextView cancel = text("Cancel", 14, Pal.accent, true);
        cancel.setGravity(Gravity.CENTER);
        cancel.setPadding(dp(18), 0, dp(18), 0);
        GradientDrawable cg = new GradientDrawable();
        cg.setCornerRadius(dp(22));
        cg.setColor(Pal.chip);
        cancel.setBackground(cg);
        cancel.setOnClickListener(v -> dlg.dismiss());
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-2, dp(44));
        clp.leftMargin = dp(8);
        foot.addView(cancel, clp);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(-1, -2);
        flp.topMargin = dp(10);
        box.addView(foot, flp);
        Fonts.apply(box);
        dlg.show();
    }

    private void noteMenu(final Note n) {
        final List<String> names = new ArrayList<>();
        final List<Runnable> acts = new ArrayList<>();
        names.add(n.pinned > 0 ? "Unpin" : "Pin to top");
        acts.add(() -> togglePin(n));
        if (n.pinned > 0) {
            names.add("Pinned colour…");
            acts.add(() -> pinColorDialog(n));
        }
        names.add("Move to folder…");
        acts.add(() -> moveDialog(n));
        names.add("Move to Bin");
        acts.add(() -> confirmDelete(n));
        new Dlg.Builder(this)
                .setTitle(n.title.isEmpty() ? "Untitled" : n.title)
                .setItems(names.toArray(new String[0]), (d, which) -> acts.get(which).run())
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
        return n.plain.replace('\n', ' ').replace('\u2610', '\u25CB').replace('\u2611', '\u25CF').trim();
    }

    /** To-do lines as round ticks (an empty ring, or a filled circle with a tick and the text struck through). */
    private View tickList(String preview, int sp, int textColor, int doneColor, boolean onColour) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(4), 0, 0);
        for (String line : preview.split("\n")) {
            boolean done = line.startsWith("\u2611");
            String label = line.length() > 1 ? line.substring(1).trim() : "";
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView dot = new TextView(this);
            dot.setGravity(Gravity.CENTER);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            if (done) {
                g.setColor(doneColor);
                dot.setText("\u2713");
                dot.setTextSize(10);
                dot.setTypeface(Typeface.DEFAULT_BOLD);
                dot.setTextColor(onColour ? Pal.accent : Pal.onAccent);
                dot.setIncludeFontPadding(false);
            } else {
                g.setColor(0);
                g.setStroke(dp(2), textColor);
            }
            dot.setBackground(g);
            row.addView(dot, new LinearLayout.LayoutParams(dp(16), dp(16)));
            TextView t = text(label, sp, textColor, false);
            t.setSingleLine(true);
            t.setEllipsize(TextUtils.TruncateAt.END);
            t.setPadding(dp(8), 0, 0, 0);
            if (done) t.setPaintFlags(t.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
            row.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, dp(24));
            box.addView(row, rlp);
        }
        return box;
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
            if (checklist && (preview.startsWith("\u2610") || preview.startsWith("\u2611"))) {
                card.addView(tickList(preview, previewSp, tinted ? fg : Pal.muted, tinted ? fg : Pal.accent,
                        tinted));
            } else {
                TextView p = text(preview, previewSp, tinted ? fg : Pal.muted, false);
                if (tinted) p.setAlpha(0.85f);
                p.setMaxLines(lines);
                p.setEllipsize(TextUtils.TruncateAt.END);
                p.setPadding(0, dp(4), 0, 0);
                card.addView(p);
            }
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
