package app.privatetodo;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashMap;
import java.util.Map;

/**
 * Rich-text note editor. Toolbar: heading, bold, italic, underline, strikethrough, highlight colours,
 * inline code, code block, bullets. With text selected a style applies to the selection; with just a
 * cursor it applies to what you type next. Saves by itself when you leave.
 */
public class NoteEditorActivity extends Activity {
    static final String EXTRA_ID = "note_id";

    private static final int BULLET_KEY = -1;
    private static final int SIZE_KEY = -9;
    private static final int TABLE_KEY = 8;
    private static final int NEXT_KEY = -10;
    private static final int CHECK_KEY = -2;

    private TaskDb db;
    private float density;
    private long noteId;
    private boolean dirty;
    private boolean deleted;

    private EditText title;
    private RichEditText body;
    private TextView dateLabel;
    private LinearLayout colorRow;
    private final Map<Integer, TextView> buttons = new HashMap<>();

    // Styles chosen with only a cursor (apply to the next typed text). 0 = off, 1 = on, colour for highlight.
    private final Map<Integer, Integer> pending = new HashMap<>();
    private boolean busy;
    private boolean typing;
    private int insStart;
    private int insCount;
    private int insBefore;
    private int expectCursor = -1;

    // Undo / redo: snapshots of the whole body (text, styles, lists, tables), taken after a short pause in editing.
    private final java.util.ArrayList<String> hist = new java.util.ArrayList<>();
    private final java.util.ArrayList<int[]> histSel = new java.util.ArrayList<>();
    private int histPos = -1;
    private boolean restoring;
    private IconView undoBtn;
    private IconView redoBtn;
    private final Runnable commitTask = this::commitNow;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Pal.init(this);
        setTheme(Pal.themeRes());
        super.onCreate(savedInstanceState);
        density = getResources().getDisplayMetrics().density;
        RichText.init(this);
        db = new TaskDb(this);
        noteId = savedInstanceState != null ? savedInstanceState.getLong(EXTRA_ID, 0)
                : getIntent().getLongExtra(EXTRA_ID, 0);
        if (noteId == 0) noteId = getIntent().getLongExtra(EXTRA_ID, 0);
        setContentView(build());
        Fonts.apply(getWindow().getDecorView());

        Note n = noteId > 0 ? db.note(noteId) : null;
        if (n == null) {
            noteId = 0;
            dateLabel.setText("New note");
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                    | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            body.requestFocus();
        } else {
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
                    | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            View content = findViewById(android.R.id.content);
            content.setFocusableInTouchMode(true);
            content.requestFocus(); // so the title does not grab focus and pop the keyboard up
            title.setText(n.title);
            RichText.fromJson(n.body, body.getText());
            dateLabel.setText("Edited " + Reminders.when(this, n.updated));
        }
        dirty = false;
        refreshToolbar();
        hist.clear();
        histSel.clear();
        histPos = -1;
        commitNow(); // the opening state is the first step of the history
    }

    @Override
    protected void onPause() {
        saveNow();
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        saveNow();
        out.putLong(EXTRA_ID, noteId);
        super.onSaveInstanceState(out);
    }

    @Override
    protected void onDestroy() {
        db.close();
        super.onDestroy();
    }

    // ------------------------------------------------------------------ UI

    private View build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Pal.bg);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(12), dp(12), dp(12), 0);
        IconView back = new IconView(this, IconView.BACK);
        back.setContentDescription("Back");
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));

        title = new EditText(this);
        title.setHint("Title");
        title.setHintTextColor(Pal.muted);
        title.setTextColor(Pal.ink);
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setBackground(null);
        title.setSingleLine(true);
        title.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        title.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        title.setOnEditorActionListener((tv, a, ev) -> {
            body.requestFocus();
            return true;
        });
        title.addTextChangedListener(new SimpleWatcher() {
            @Override
            public void afterTextChanged(Editable e) {
                dirty = true;
            }
        });
        header.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1f));

        undoBtn = new IconView(this, IconView.UNDO);
        undoBtn.setContentDescription("Undo");
        undoBtn.setOnClickListener(v -> { Hap.tick(v); undo(); });
        header.addView(undoBtn, new LinearLayout.LayoutParams(dp(40), dp(48)));
        redoBtn = new IconView(this, IconView.REDO);
        redoBtn.setContentDescription("Redo");
        redoBtn.setOnClickListener(v -> { Hap.tick(v); redo(); });
        header.addView(redoBtn, new LinearLayout.LayoutParams(dp(40), dp(48)));

        IconView trash = new IconView(this, IconView.TRASH);
        trash.setContentDescription("Delete note");
        trash.setOnClickListener(v -> confirmDelete());
        header.addView(trash, new LinearLayout.LayoutParams(dp(48), dp(48)));
        root.addView(header);

        dateLabel = new TextView(this);
        dateLabel.setTextSize(13);
        dateLabel.setTextColor(Pal.muted);
        dateLabel.setPadding(dp(64), 0, dp(16), dp(4));
        root.addView(dateLabel);

        body = new RichEditText(this);
        body.setHint("Start writing…");
        body.setHintTextColor(Pal.muted);
        body.setTextColor(Pal.ink);
        scale = Prefs.noteScale(this);
        RichText.zoom = scale;
        body.setTextSize(17 * scale);
        body.setTableTapListener(tb -> editTable(tb));
        body.setTableChangedListener(() -> changed());
        body.setZoomListener((f, done) -> {
            if (done) {
                Prefs.setNoteScale(this, scale);
                return;
            }
            setScale(scale * f);
        });
        body.setLineSpacing(0, 1.15f);
        body.setBackground(null);
        body.setGravity(Gravity.TOP | Gravity.START);
        body.setMinHeight(dp(420));
        body.setPadding(dp(20), dp(8), dp(20), dp(24));
        body.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        body.addTextChangedListener(new SimpleWatcher() {
            @Override
            public void beforeTextChanged(CharSequence c, int a, int b, int n) {
                typing = true;
            }

            @Override
            public void onTextChanged(CharSequence c, int start, int before, int count) {
                insStart = start;
                insCount = count;
                insBefore = before;
            }

            @Override
            public void afterTextChanged(Editable e) {
                afterEdit(e);
            }
        });
        body.setCheckTapListener(ls -> {
            commitNow();
            busy = true;
            RichText.flipCheck(body.getText(), ls);
            busy = false;
            changed();
        });
        body.setSelectionListener((s, e) -> {
            if (!typing && !busy && !(s == e && s == expectCursor)) pending.clear();
            refreshToolbar();
        });
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        root.addView(buildColorRow());
        root.addView(buildToolbar());
        return root;
    }

    private View buildColorRow() {
        colorRow = new LinearLayout(this);
        colorRow.setOrientation(LinearLayout.HORIZONTAL);
        colorRow.setGravity(Gravity.CENTER_VERTICAL);
        colorRow.setPadding(dp(16), dp(8), dp(16), dp(8));
        colorRow.setBackgroundColor(Pal.raised);
        colorRow.setVisibility(View.GONE);
        TextView label = new TextView(this);
        label.setText("Highlight");
        label.setTextColor(Pal.muted);
        label.setTextSize(13);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setPadding(0, 0, dp(12), 0);
        colorRow.addView(label);
        for (final int color : RichText.HL_COLORS) {
            colorRow.addView(swatch(color, null), swatchLp());
        }
        colorRow.addView(swatch(Pal.chip, "✕"), swatchLp()); // no highlight
        return colorRow;
    }

    private LinearLayout.LayoutParams swatchLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(36), dp(36));
        lp.rightMargin = dp(10);
        return lp;
    }

    private TextView swatch(final int color, String mark) {
        TextView t = new TextView(this);
        t.setGravity(Gravity.CENTER);
        t.setTextColor(Pal.muted);
        t.setTextSize(14);
        if (mark != null) {
            t.setText(mark);
            t.setContentDescription("No highlight");
        } else {
            t.setContentDescription("Highlight colour");
        }
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        t.setBackground(g);
        t.setOnClickListener(v -> {
            highlight(mark == null ? color : 0);
            colorRow.setVisibility(View.GONE);
        });
        return t;
    }

    private float scale = 1f;

    private void setScale(float v) {
        scale = Math.max(0.7f, Math.min(2.6f, v));
        RichText.zoom = scale;
        body.setTextSize(17 * scale); // the title keeps its own fixed size
    }

    private void tableMenu() {
        Editable e = body.getText();
        RichText.Tbl t = RichText.tableOnLine(e, selStart());
        if (t != null) editTable(t);
        else newTableDialog();
    }

    private void newTableDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setPadding(dp(24), dp(8), dp(24), 0);
        final Wheel cols = new Wheel(this);
        cols.setMinValue(2);
        cols.setMaxValue(6);
        cols.setWrapSelectorWheel(false);
        cols.setValue(3);
        final Wheel rows = new Wheel(this);
        rows.setMinValue(2);
        rows.setMaxValue(15);
        rows.setWrapSelectorWheel(false);
        rows.setValue(3);
        box.addView(labelled("Columns", cols), new LinearLayout.LayoutParams(0, -2, 1f));
        box.addView(labelled("Rows", rows), new LinearLayout.LayoutParams(0, -2, 1f));
        new Dlg.Builder(this)
                .setTitle("Insert table")
                .setView(box)
                .setPositiveButton("Insert", (d, w) -> {
                    int at = RichText.insertTable(body.getText(), selStart(), cols.getValue(), rows.getValue());
                    body.setSelection(Math.min(body.getText().length(), at + 2));
                    changed();
                    RichText.Tbl t = RichText.tableOnLine(body.getText(), at);
                    if (t != null) editTable(t);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ----- table editor: a grid of real text fields (first row = column headers)

    private String[][] tmpCells;
    private LinearLayout tmpGrid;

    private void editTable(final RichText.Tbl t) {
        tmpCells = new String[t.rows()][];
        for (int r = 0; r < t.rows(); r++) tmpCells[r] = t.cells[r].clone();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(8), dp(16), 0);

        tmpGrid = new LinearLayout(this);
        tmpGrid.setOrientation(LinearLayout.VERTICAL);
        buildGrid();
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(tmpGrid);
        // Cap the grid's height so the + Row / + Column / - Row / - Column chips below
        // always stay on screen, however many rows the table has (it scrolls instead).
        final int maxGridH = (int) (getResources().getDisplayMetrics().heightPixels * 0.5f);
        android.widget.ScrollView vs = new android.widget.ScrollView(this) {
            @Override
            protected void onMeasure(int wSpec, int hSpec) {
                super.onMeasure(wSpec, View.MeasureSpec.makeMeasureSpec(maxGridH, View.MeasureSpec.AT_MOST));
            }
        };
        vs.addView(hs);
        root.addView(vs, new LinearLayout.LayoutParams(-1, -2));

        FlowLayout tools = new FlowLayout(this, dp(8), dp(8));
        tools.setPadding(0, dp(8), 0, dp(4));
        tools.addView(toolChip("+ Row", () -> {
            if (tmpCells.length >= 40) return;
            String[][] n2 = new String[tmpCells.length + 1][];
            System.arraycopy(tmpCells, 0, n2, 0, tmpCells.length);
            n2[tmpCells.length] = new String[tmpCells[0].length];
            java.util.Arrays.fill(n2[tmpCells.length], "");
            tmpCells = n2;
            buildGrid();
        }));
        tools.addView(toolChip("+ Column", () -> {
            if (tmpCells[0].length >= 8) return;
            for (int r = 0; r < tmpCells.length; r++) {
                String[] row = java.util.Arrays.copyOf(tmpCells[r], tmpCells[r].length + 1);
                row[row.length - 1] = r == 0 ? "Column " + row.length : "";
                tmpCells[r] = row;
            }
            buildGrid();
        }));
        tools.addView(toolChip("\u2212 Row", () -> {
            if (tmpCells.length <= 2) {
                Toast.makeText(this, "A table keeps its header and one row", Toast.LENGTH_SHORT).show();
                return;
            }
            // choose which body row to delete (the header row stays)
            final String[] items = new String[tmpCells.length - 1];
            for (int r = 1; r < tmpCells.length; r++) {
                items[r - 1] = "Row " + r + (preview(tmpCells[r]).isEmpty() ? " (empty)" : ":  " + preview(tmpCells[r]));
            }
            new Dlg.Builder(this)
                    .setTitle("Delete which row?")
                    .setItems(items, (d, which) -> {
                        final int row = which + 1;
                        new Dlg.Builder(this)
                                .setTitle("Delete row " + row + "?")
                                .setMessage(preview(tmpCells[row]).isEmpty() ? "It is empty." : preview(tmpCells[row]))
                                .setPositiveButton("Delete", (d2, w2) -> {
                                    String[][] next = new String[tmpCells.length - 1][];
                                    for (int r = 0, o = 0; r < tmpCells.length; r++) {
                                        if (r != row) next[o++] = tmpCells[r];
                                    }
                                    tmpCells = next;
                                    buildGrid();
                                })
                                .setNegativeButton("Keep", null)
                                .show();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        }));
        tools.addView(toolChip("\u2212 Column", () -> {
            if (tmpCells[0].length <= 2) {
                Toast.makeText(this, "A table keeps at least two columns", Toast.LENGTH_SHORT).show();
                return;
            }
            final int cols = tmpCells[0].length;
            final String[] items = new String[cols];
            for (int k = 0; k < cols; k++) {
                String head = RichText.Tbl.label(tmpCells[0][k]).trim();
                items[k] = "Column " + (k + 1) + (head.isEmpty() ? "" : ":  " + head);
            }
            new Dlg.Builder(this)
                    .setTitle("Delete which column?")
                    .setItems(items, (d, which) -> {
                        final int col = which;
                        StringBuilder what = new StringBuilder();
                        for (String[] row : tmpCells) {
                            String shown = RichText.Tbl.label(row[col]).trim();
                            if (!shown.isEmpty()) what.append(what.length() > 0 ? " \u00B7 " : "").append(shown);
                        }
                        new Dlg.Builder(this)
                                .setTitle("Delete column " + (col + 1) + "?")
                                .setMessage(what.length() == 0 ? "It is empty." : what.toString())
                                .setPositiveButton("Delete", (d2, w2) -> {
                                    for (int r = 0; r < tmpCells.length; r++) {
                                        String[] row = new String[cols - 1];
                                        for (int k = 0, o = 0; k < cols; k++) {
                                            if (k != col) row[o++] = tmpCells[r][k];
                                        }
                                        tmpCells[r] = row;
                                    }
                                    buildGrid();
                                })
                                .setNegativeButton("Keep", null)
                                .show();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        }));
        root.addView(tools);

        new Dlg.Builder(this)
                .setTitle("Table")
                .setView(root)
                .setPositiveButton("Done", (d, w) -> {
                    t.cells = tmpCells;
                    RichText.refresh(body.getText(), t);
                    changed();
                    body.invalidate();
                })
                .setNeutralButton("Remove table", (d, w) -> {
                    Editable e = body.getText();
                    int a = e.getSpanStart(t);
                    if (a >= 0) {
                        e.removeSpan(t);
                        e.delete(a, Math.min(e.length(), a + 1));
                        changed();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static String preview(String[] row) {
        StringBuilder sb = new StringBuilder();
        for (String cell : row) {
            String shown = RichText.Tbl.label(cell).trim();
            if (!shown.isEmpty()) sb.append(sb.length() > 0 ? " \u00B7 " : "").append(shown);
        }
        return sb.length() > 40 ? sb.substring(0, 40) + "\u2026" : sb.toString();
    }

    private TextView toolChip(String label, final Runnable action) {
        TextView c = new TextView(this);
        c.setText(label);
        c.setTextSize(14);
        c.setTypeface(Typeface.DEFAULT_BOLD);
        c.setTextColor(Pal.accent);
        c.setGravity(Gravity.CENTER);
        c.setMinHeight(dp(44));
        c.setPadding(dp(14), 0, dp(14), 0);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(22));
        g.setColor(Pal.chip);
        c.setBackground(g);
        c.setOnClickListener(v -> {
            Hap.tick(v);
            action.run();
        });
        return c;
    }

    private void buildGrid() {
        tmpGrid.removeAllViews();
        for (int r = 0; r < tmpCells.length; r++) {
            LinearLayout row = new LinearLayout(this);
            for (int k = 0; k < tmpCells[r].length; k++) {
                final int rr = r;
                final int kk = k;
                LinearLayout cell = new LinearLayout(this);
                cell.setGravity(Gravity.CENTER_VERTICAL);
                GradientDrawable g = new GradientDrawable();
                g.setCornerRadius(dp(8));
                if (r == 0) {
                    g.setColor((Pal.accent & 0x00FFFFFF) | (Pal.dark ? 0x38000000 : 0x2E000000));
                } else {
                    g.setColor(Pal.chip);
                }
                cell.setBackground(g);

                final EditText et = new EditText(this);
                et.setText(RichText.Tbl.label(tmpCells[r][k]));
                et.setSingleLine(true);
                et.setTextSize(15);
                et.setTextColor(Pal.ink);
                et.setHint(r == 0 ? "Header" : "");
                et.setHintTextColor(Pal.muted);
                et.setMaxLines(1);
                et.setBackground(null);
                et.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_NEXT);
                et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
                et.setTypeface(r == 0 ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
                et.setPadding(dp(8), 0, dp(8), 0);
                final TextView tickBtn = r == 0 ? null : new TextView(this);
                if (tickBtn != null) {
                    tickBtn.setGravity(Gravity.CENTER);
                    tickBtn.setContentDescription("Tick box in this cell");
                    paintTick(tickBtn, RichText.Tbl.tick(tmpCells[r][k]));
                    tickBtn.setOnClickListener(v -> {
                        Hap.tick(v);
                        int st = (RichText.Tbl.tick(tmpCells[rr][kk]) + 1) % 3; // none -> box -> ticked -> none
                        tmpCells[rr][kk] = RichText.Tbl.compose(st, et.getText().toString());
                        paintTick(tickBtn, st);
                    });
                    cell.addView(tickBtn, new LinearLayout.LayoutParams(dp(36), dp(46)));
                }
                et.addTextChangedListener(new SimpleWatcher() {
                    @Override
                    public void afterTextChanged(Editable s) {
                        tmpCells[rr][kk] = RichText.Tbl.compose(RichText.Tbl.tick(tmpCells[rr][kk]), s.toString());
                    }
                });
                cell.addView(et, new LinearLayout.LayoutParams(0, dp(46), 1f));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(160), dp(46));
                lp.setMargins(0, 0, dp(6), dp(6));
                row.addView(cell, lp);
            }
            tmpGrid.addView(row);
        }
        Fonts.apply(tmpGrid);
    }

    /** The little tick control in a table cell: faint = none, ring = box, filled = ticked. */
    private void paintTick(TextView t, int state) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        int size = dp(22);
        g.setSize(size, size);
        if (state == 2) {
            g.setColor(Pal.accent);
            t.setText("\u2713");
            t.setTextColor(Pal.onAccent);
        } else if (state == 1) {
            g.setColor(0);
            g.setStroke(dp(2), Pal.muted);
            t.setText("");
        } else {
            g.setColor(0);
            g.setStroke(dp(1), (Pal.muted & 0x00FFFFFF) | 0x55000000);
            t.setText("");
        }
        t.setTextSize(13);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setBackground(new android.graphics.drawable.InsetDrawable(g, dp(7), dp(12), dp(7), dp(12)));
    }

    private View labelled(String label, View v) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(13);
        t.setTextColor(Pal.muted);
        t.setGravity(Gravity.CENTER);
        l.addView(t);
        l.addView(v, new LinearLayout.LayoutParams(-1, -2));
        return l;
    }

    /** Aa: pick any size (8 to 72) for the selected text, or for what you type next when nothing is selected. */
    private void pickSize() {
        final int s = selStart();
        final int en = selEnd();
        int cur = RichText.sizeAt(body.getText(), s, en);
        if (s == en && pending.containsKey(RichText.SIZE)) cur = pending.get(RichText.SIZE);
        final int[] val = {cur > 0 ? cur : RichText.BASE_SIZE};

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(8), dp(24), 0);

        TextView hint = new TextView(this);
        hint.setText(s == en ? "For the text you type next" : "For the selected text");
        hint.setTextColor(Pal.muted);
        hint.setTextSize(13);
        box.addView(hint);

        LinearLayout stepper = new LinearLayout(this);
        stepper.setOrientation(LinearLayout.HORIZONTAL);
        stepper.setGravity(Gravity.CENTER_VERTICAL);
        final EditText num = new EditText(this);
        num.setInputType(InputType.TYPE_CLASS_NUMBER);
        num.setGravity(Gravity.CENTER);
        num.setTextSize(28);
        num.setTypeface(Typeface.DEFAULT_BOLD);
        num.setTextColor(Pal.ink);
        num.setSelectAllOnFocus(true);
        num.setMinWidth(dp(96));
        num.setText(String.valueOf(val[0]));
        final TextView minus = sizeStep("\u2212");
        final TextView plus = sizeStep("+");
        View.OnClickListener step = v -> {
            Hap.tick(v);
            int n = parseSize(num, val[0]) + (v == plus ? 1 : -1);
            n = Math.max(RichText.MIN_SIZE, Math.min(RichText.MAX_SIZE, n));
            val[0] = n;
            num.setText(String.valueOf(n));
            num.setSelection(num.getText().length());
        };
        minus.setOnClickListener(step);
        plus.setOnClickListener(step);
        stepper.addView(minus, new LinearLayout.LayoutParams(dp(52), dp(52)));
        stepper.addView(num, new LinearLayout.LayoutParams(0, -2, 1f));
        stepper.addView(plus, new LinearLayout.LayoutParams(dp(52), dp(52)));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, -2);
        slp.topMargin = dp(8);
        box.addView(stepper, slp);

        HorizontalScrollView quick = new HorizontalScrollView(this);
        quick.setHorizontalScrollBarEnabled(false);
        LinearLayout qrow = new LinearLayout(this);
        for (final int q : new int[]{12, 14, 17, 20, 25, 32, 40, 56}) {
            TextView c = new TextView(this);
            c.setText(String.valueOf(q));
            c.setTextSize(15);
            c.setTypeface(Typeface.DEFAULT_BOLD);
            c.setTextColor(Pal.ink);
            c.setGravity(Gravity.CENTER);
            GradientDrawable cg = new GradientDrawable();
            cg.setCornerRadius(dp(22));
            cg.setColor(Pal.chip);
            c.setBackground(cg);
            c.setOnClickListener(v -> {
                Hap.tick(v);
                val[0] = q;
                num.setText(String.valueOf(q));
                num.setSelection(num.getText().length());
            });
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(dp(52), dp(44));
            cl.rightMargin = dp(8);
            qrow.addView(c, cl);
        }
        quick.addView(qrow);
        LinearLayout.LayoutParams qlp = new LinearLayout.LayoutParams(-1, -2);
        qlp.topMargin = dp(14);
        box.addView(quick, qlp);

        TextView note = new TextView(this);
        note.setText("Any size from 8 to 72. Pinch still zooms the whole note.");
        note.setTextColor(Pal.muted);
        note.setTextSize(12);
        note.setPadding(0, dp(6), 0, 0);
        box.addView(note);

        new Dlg.Builder(this)
                .setTitle("Text size")
                .setView(box)
                .setPositiveButton("Apply", (d, w) -> setTextSize(parseSize(num, val[0]), s, en))
                .setNeutralButton("Normal", (d, w) -> setTextSize(0, s, en))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private TextView sizeStep(String label) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(26);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(Pal.ink);
        t.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(Pal.chip);
        t.setBackground(g);
        return t;
    }

    private int parseSize(EditText num, int fallback) {
        try {
            int n = Integer.parseInt(num.getText().toString().trim());
            return Math.max(RichText.MIN_SIZE, Math.min(RichText.MAX_SIZE, n));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    /** size 0 = back to normal. */
    private void setTextSize(int size, int s, int en) {
        commitNow();
        Editable e = body.getText();
        if (s == en) {
            pending.put(RichText.SIZE, size);
        } else {
            RichText.apply(e, RichText.SIZE, s, en, size, size > 0);
            changed();
        }
        refreshToolbar();
    }

    private View buildToolbar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), dp(6), dp(8), dp(6));

        TextView aa = addButton(bar, SIZE_KEY, "Aa", Typeface.BOLD, "Text size");
        aa.setOnClickListener(v -> pickSize());
        addButton(bar, RichText.BOLD, "B", Typeface.BOLD, "Bold");
        addButton(bar, RichText.ITALIC, "I", Typeface.ITALIC, "Italic");
        addButton(bar, RichText.UNDER, "U", Typeface.NORMAL, "Underline");
        addButton(bar, RichText.STRIKE, "S", Typeface.NORMAL, "Strikethrough");
        TextView hl = addButton(bar, RichText.HILITE, "A", Typeface.BOLD, "Highlight colour");
        SpannableString marked = new SpannableString("A");
        marked.setSpan(new RichText.Hl(RichText.HL_COLORS[0]), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        hl.setText(marked);
        addButton(bar, RichText.HEAD, "H", Typeface.BOLD, "Heading");
        addButton(bar, BULLET_KEY, "\u2022", Typeface.BOLD, "Bullet list");
        addButton(bar, CHECK_KEY, "(\u2713)", Typeface.BOLD, "To-do list");
        addButton(bar, TABLE_KEY, "\u25A6", Typeface.BOLD, "Table");
        addButton(bar, RichText.CODE, "`", Typeface.NORMAL, "Inline code").setTypeface(Typeface.MONOSPACE);
        addButton(bar, RichText.BLOCK, "{ }", Typeface.NORMAL, "Code block").setTypeface(Typeface.MONOSPACE);

        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setBackgroundColor(Pal.raised);
        scroll.addView(bar);
        return scroll;
    }

    private TextView addButton(LinearLayout bar, final int key, String label, int style, String desc) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(17);
        t.setTypeface(Typeface.DEFAULT, style);
        t.setGravity(Gravity.CENTER);
        t.setMinWidth(dp(48));
        t.setMinHeight(dp(44));
        t.setContentDescription(desc);
        if (key == RichText.UNDER) t.setPaintFlags(t.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        if (key == RichText.STRIKE) t.setPaintFlags(t.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
        t.setOnClickListener(v -> onTool(key));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = dp(4);
        bar.addView(t, lp);
        buttons.put(key, t);
        return t;
    }

    // --------------------------------------------------------- formatting

    private int selStart() {
        return Math.max(0, Math.min(body.getSelectionStart(), body.getSelectionEnd()));
    }

    private int selEnd() {
        return Math.max(0, Math.max(body.getSelectionStart(), body.getSelectionEnd()));
    }

    private void onTool(int key) {
        commitNow();
        if (key == RichText.HILITE) {
            colorRow.setVisibility(colorRow.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
            return;
        }
        Editable e = body.getText();
        int s = selStart();
        int en = selEnd();
        if (key == BULLET_KEY || key == CHECK_KEY) {
            busy = true;
            int c = RichText.toggleList(e, s, en, key == CHECK_KEY);
            busy = false;
            body.setSelection(Math.min(c, e.length()));
            changed();
            refreshToolbar();
            return;
        }
        if (key == TABLE_KEY) {
            tableMenu();
            return;
        }
        boolean lineType = key == RichText.HEAD || key == RichText.BLOCK;
        if (s == en) {
            int a = RichText.lineStart(e, s);
            int b = RichText.lineEnd(e, s);
            if (lineType && b > a) { // a line with text: format the whole line
                RichText.apply(e, key, a, b, 0, !RichText.active(e, key, a, b));
            } else {
                pending.put(key, isOn(key, s, en) ? 0 : 1);
            }
        } else {
            if (lineType) {
                s = RichText.lineStart(e, s);
                en = RichText.lineEnd(e, en);
            }
            RichText.apply(e, key, s, en, 0, !RichText.active(e, key, s, en));
        }
        changed();
        refreshToolbar();
    }

    private void highlight(int color) {
        commitNow();
        Editable e = body.getText();
        int s = selStart();
        int en = selEnd();
        if (s == en) {
            pending.put(RichText.HILITE, color);
        } else {
            RichText.apply(e, RichText.HILITE, s, en, color, color != 0);
            changed();
        }
        refreshToolbar();
    }

    private boolean isOn(int key, int s, int en) {
        if (s == en && pending.containsKey(key)) return pending.get(key) != 0;
        return RichText.active(body.getText(), key, s, en);
    }

    private void refreshToolbar() {
        if (body == null || buttons.isEmpty()) return;
        int s = selStart();
        int en = selEnd();
        for (Map.Entry<Integer, TextView> b : buttons.entrySet()) {
            int key = b.getKey();
            boolean on;
            if (key == BULLET_KEY) {
                on = RichText.prefixAt(body.getText(), RichText.lineStart(body.getText(), s))
                        == RichText.P_BULLET;
            } else if (key == CHECK_KEY) {
                on = RichText.prefixAt(body.getText(), RichText.lineStart(body.getText(), s))
                        >= RichText.P_CHECK_OFF;
            } else if (key == RichText.HILITE) {
                on = isOn(key, s, en);
            } else {
                on = isOn(key, s, en);
            }
            if (key == SIZE_KEY) on = RichText.sizeAt(body.getText(), s, en) != 0;
            style(b.getValue(), on);
        }
    }

    private void style(TextView t, boolean on) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(12));
        g.setColor(on ? Pal.sel : 0);
        t.setBackground(g);
        // The highlight button keeps its own dark-on-yellow look.
        if (!(t.getText() instanceof Spanned)) t.setTextColor(on ? Pal.onSel : Pal.ink);
    }

    /** Runs after every text change: applies "next typed text" styles and continues bullet lists. */
    private void afterEdit(Editable e) {
        if (busy) {
            typing = false;
            return;
        }
        busy = true;
        try {
            changed();
            if (insCount > 0) {
                for (Map.Entry<Integer, Integer> p : pending.entrySet()) {
                    int v = p.getValue();
                    RichText.apply(e, p.getKey(), insStart, insStart + insCount, v, v != 0);
                }
            }
            boolean moved = false;
            // Enter = the last inserted character is a newline (some keyboards commit "word\n" together).
            int nl = insStart + insCount - 1;
            boolean enter = insCount >= 1 && nl < e.length() && e.charAt(nl) == '\n'
                    && e.subSequence(insStart, nl).toString().indexOf('\n') < 0;
            if (enter) {
                int ps = RichText.lineStart(e, nl); // the line that Enter just ended
                boolean restEmpty = nl + 1 >= e.length() || e.charAt(nl + 1) == '\n';
                int kind = RichText.prefixAt(e, ps);
                if (kind != RichText.P_NONE && nl - ps == 2 && restEmpty) { // Enter on an empty item ends the list
                    e.delete(ps, nl + 1);
                    expectCursor = ps;
                    body.setSelection(Math.min(expectCursor, e.length()));
                    moved = true;
                } else if (kind != RichText.P_NONE) {
                    // New line continues the list; a to-do item continues as an unticked one.
                    String next = kind == RichText.P_BULLET ? RichText.BULLET : RichText.CHECK_OFF;
                    e.insert(nl + 1, next);
                    RichText.apply(e, RichText.STRIKE, nl + 1, nl + 1 + next.length(), 0, false);
                    expectCursor = nl + 1 + next.length();
                    body.setSelection(Math.min(expectCursor, e.length()));
                    moved = true;
                }
                if (restEmpty) endLineStyles(e, ps, nl);
            }
            // Backspace over the space of "• " / "☐ " removes the whole marker.
            if (!moved && insCount == 0 && insBefore == 1) {
                int ls = RichText.lineStart(e, Math.min(insStart, e.length()));
                if (insStart == ls + 1 && ls < e.length()) {
                    char ch = e.charAt(ls);
                    if (ch == '\u2022' || ch == '\u2610' || ch == '\u2611') {
                        e.delete(ls, ls + 1);
                        expectCursor = ls;
                        body.setSelection(Math.min(ls, e.length()));
                        moved = true;
                    }
                }
            }
            if (!moved) expectCursor = insStart + insCount;
        } finally {
            busy = false;
            typing = false;
            insCount = 0;
        }
        refreshToolbar();
    }

    /**
     * After Enter on a line, the next line should not inherit a heading, and Enter on an empty line
     * inside a code block leaves the block.
     */
    private void endLineStyles(Editable e, int ps, int nl) {
        for (Object o : e.getSpans(ps, nl + 1, Object.class)) {
            int type = RichText.typeOf(o);
            if (type == RichText.HEAD && e.getSpanEnd(o) == nl + 1) {
                int a = e.getSpanStart(o);
                e.removeSpan(o);
                if (a < nl) e.setSpan(RichText.make(RichText.HEAD, 0), a, nl, Spanned.SPAN_EXCLUSIVE_INCLUSIVE);
            }
        }
        if (nl == ps && RichText.active(e, RichText.BLOCK, ps, nl + 1)) {
            RichText.apply(e, RichText.BLOCK, ps, nl + 1, 0, false); // second Enter ends the code block
        }
    }

    // ------------------------------------------------------------ saving

    /** A body change happened: mark it for saving and take an undo step once typing pauses. */
    private void changed() {
        dirty = true;
        if (restoring || body == null) return;
        body.removeCallbacks(commitTask);
        body.postDelayed(commitTask, 700);
    }

    private void commitNow() {
        if (body == null || restoring) return;
        body.removeCallbacks(commitTask);
        String snap = RichText.toJson(body.getText());
        if (histPos >= 0 && snap.equals(hist.get(histPos))) {
            histSel.set(histPos, new int[]{selStart(), selEnd()});
            updateUndoButtons();
            return;
        }
        while (hist.size() > histPos + 1) { // a new edit drops the redo steps
            hist.remove(hist.size() - 1);
            histSel.remove(histSel.size() - 1);
        }
        hist.add(snap);
        histSel.add(new int[]{selStart(), selEnd()});
        if (hist.size() > 100) {
            hist.remove(0);
            histSel.remove(0);
        }
        histPos = hist.size() - 1;
        updateUndoButtons();
    }

    private void undo() {
        commitNow();
        if (histPos > 0) restore(histPos - 1);
    }

    private void redo() {
        commitNow();
        if (histPos < hist.size() - 1) restore(histPos + 1);
    }

    private void restore(int pos) {
        restoring = true;
        busy = true;
        try {
            Editable e = body.getText();
            e.clear();
            RichText.fromJson(hist.get(pos), e);
            int[] sel = histSel.get(pos);
            body.setSelection(Math.min(sel[0], e.length()), Math.min(sel[1], e.length()));
        } finally {
            busy = false;
            restoring = false;
        }
        histPos = pos;
        pending.clear();
        dirty = true;
        body.invalidate();
        refreshToolbar();
        updateUndoButtons();
    }

    private void updateUndoButtons() {
        if (undoBtn == null || redoBtn == null) return;
        boolean canUndo = histPos > 0;
        boolean canRedo = histPos >= 0 && histPos < hist.size() - 1;
        undoBtn.setAlpha(canUndo ? 1f : 0.3f);
        redoBtn.setAlpha(canRedo ? 1f : 0.3f);
        undoBtn.setEnabled(canUndo);
        redoBtn.setEnabled(canRedo);
    }

    private void saveNow() {
        if (body != null) body.removeCallbacks(commitTask);
        if (deleted || !dirty) return;
        String t = title.getText().toString().trim();
        Editable b = body.getText();
        boolean empty = t.isEmpty() && b.toString().trim().isEmpty();
        if (empty) {
            if (noteId > 0) {
                db.deleteNote(noteId);
                noteId = 0;
            }
        } else {
            noteId = db.saveNote(noteId, t, RichText.toJson(b), RichText.tablesPlain(b));
            getIntent().putExtra(EXTRA_ID, noteId);
            dateLabel.setText("Edited " + Reminders.when(this, System.currentTimeMillis()));
        }
        dirty = false;
    }

    private void confirmDelete() {
        new Dlg.Builder(this)
                .setTitle("Delete this note?")
                .setMessage("This permanently deletes it from this device.")
                .setPositiveButton("Delete", (d, w) -> {
                    if (noteId > 0) db.deleteNote(noteId);
                    deleted = true;
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private int dp(int v) {
        return Math.round(v * density);
    }

    /** TextWatcher with empty defaults. */
    private abstract static class SimpleWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence c, int a, int b, int n) {
        }

        @Override
        public void onTextChanged(CharSequence c, int a, int b, int n) {
        }

        @Override
        public void afterTextChanged(Editable e) {
        }
    }
}
