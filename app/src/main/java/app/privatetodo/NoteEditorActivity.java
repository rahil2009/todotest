package app.privatetodo;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
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
import android.widget.FrameLayout;
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
    /** A new note starts inside this folder (0 = none). */
    static final String EXTRA_FOLDER = "folder_id";

    private static final int BULLET_KEY = -1;
    private static final int SIZE_KEY = -9;
    private static final int TABLE_KEY = 8;
    private static final int NEXT_KEY = -10;
    private static final int CHECK_KEY = -2;
    private static final int CLEAR_KEY = -3;
    private static final int NUM_KEY = -4;
    private static final int IN_KEY = -5;
    private static final int OUT_KEY = -6;
    private static final int NO_BRUSH = -100;
    private static final int LINK_KEY = -19;
    private static final int ALIGN_L_KEY = -20;
    private static final int ALIGN_C_KEY = -21;
    private static final int ALIGN_R_KEY = -22;

    private TaskDb db;
    private float density;
    private long noteId;
    private boolean dirty;
    private boolean deleted;

    private EditText title;
    private RichEditText body;
    private TextView dateLabel;

    // Finger mode: pick a style, then swipe over the text to apply it.
    private boolean paintMode;
    private int brush = NO_BRUSH;
    private int brushHl = RichText.HL_COLORS[0];
    private int brushFg = RichText.FG_COLORS[0];
    private boolean strokeErase;
    private FrameLayout fab;
    private IconView fabIcon;
    private View headerView;
    private LinearLayout dateRow;
    private TextView readTitle;
    private View toolbarView;
    private FrameLayout readClose;
    private boolean reading;
    private final Runnable sleepAgain = () -> getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    private TextView paintHint;
    private LinearLayout paintBox;
    private TextView helpCard;
    private TextView helpBtn;

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
    /** Saves the note a moment after typing pauses, so a crash or a killed app loses at most a few seconds. */
    private final Runnable autosaveTask = () -> {
        if (restoring || body == null) return;
        commitNow();
        saveNow(true);
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Pal.init(this);
        setTheme(Pal.themeRes());
        super.onCreate(savedInstanceState);
        density = getResources().getDisplayMetrics().density;
        RichText.init(this);
        NoteFonts.init(this);
        db = new TaskDb(this);
        noteId = savedInstanceState != null ? savedInstanceState.getLong(EXTRA_ID, 0)
                : getIntent().getLongExtra(EXTRA_ID, 0);
        if (noteId == 0) noteId = getIntent().getLongExtra(EXTRA_ID, 0);
        setContentView(build());
        Fonts.apply(getWindow().getDecorView());
        body.setTypeface(NoteFonts.face(NoteFonts.noteDefault(this)));
        body.setBaseStroke(NoteFonts.stroke(NoteFonts.noteDefault(this)));

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
            busy = true; // loading must never run the "typed text" logic (Enter, list continuation ...)
            try {
                RichText.fromJson(n.body, body.getText());
                RichText.refold(body.getText());
            } finally {
                busy = false;
                typing = false;
                insCount = 0;
            }
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

        IconView histBtn = new IconView(this, IconView.CLOCK);
        histBtn.setContentDescription("Note history");
        histBtn.setOnClickListener(v -> { Hap.tick(v); openHistory(); });
        header.addView(histBtn, new LinearLayout.LayoutParams(dp(40), dp(48)));

        IconView trash = new IconView(this, IconView.TRASH);
        trash.setContentDescription("Delete note");
        trash.setOnClickListener(v -> confirmDelete());
        header.addView(trash, new LinearLayout.LayoutParams(dp(48), dp(48)));
        root.addView(header);
        headerView = header;

        dateLabel = new TextView(this);
        dateLabel.setTextSize(13);
        dateLabel.setTextColor(Pal.muted);
        dateLabel.setPadding(dp(64), 0, dp(8), dp(4));
        dateRow = new LinearLayout(this);
        dateRow.setOrientation(LinearLayout.HORIZONTAL);
        dateRow.setGravity(Gravity.CENTER_VERTICAL);
        dateRow.addView(dateLabel, new LinearLayout.LayoutParams(0, -2, 1f));
        IconView readBtn = new IconView(this, IconView.FULLSCREEN);
        readBtn.setContentDescription("Full screen reading");
        readBtn.setOnClickListener(v -> { Hap.tick(v); enterReading(); });
        dateRow.addView(readBtn, new LinearLayout.LayoutParams(dp(44), dp(32)));
        root.addView(dateRow);

        readTitle = new TextView(this);
        readTitle.setTextSize(24);
        readTitle.setTypeface(Typeface.DEFAULT_BOLD);
        readTitle.setTextColor(Pal.ink);
        readTitle.setPadding(dp(24), dp(40), dp(72), dp(4));
        readTitle.setVisibility(View.GONE);
        root.addView(readTitle);

        body = new RichEditText(this);
        body.setTag("keepfont");
        body.setHint("Start writing…");
        body.setHintTextColor(Pal.muted);
        body.setTextColor(Pal.ink);
        scale = Prefs.noteScale(this);
        RichText.zoom = scale;
        body.setTextSize(17 * scale);
        body.setTableTapListener((tb, r, c, onTick, l, t) -> onTableTap(tb, r, c, onTick));
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
        body.setPadding(dp(24), dp(8), dp(24), dp(24));
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
        body.setCheckTapListener(ls -> keepScroll(() -> {
            commitNow();
            busy = true;
            if (RichText.isToggle(RichText.prefixAt(body.getText(), ls))) RichText.flipToggle(body.getText(), ls);
            else RichText.flipCheck(body.getText(), ls);
            busy = false;
            changed();
        }));
        body.setLinkTapListener(this::openNoteLink);
        body.setCounterListener(this::onCounterTap);
        body.setSelectionListener((s, e) -> {
            if (!typing && !busy && !(s == e && s == expectCursor)) pending.clear();
            refreshToolbar();
        });
        ScrollView scroll = new ScrollView(this);
        noteScroll = scroll;
        scroll.setFillViewport(true);
        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));
        FrameLayout area = new FrameLayout(this);
        area.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        paintHint = new TextView(this);
        paintHint.setTextSize(13);
        paintHint.setTypeface(Typeface.DEFAULT_BOLD);
        paintHint.setTextColor(Pal.onAccent);
        paintHint.setPadding(dp(14), dp(8), dp(14), dp(8));
        GradientDrawable hg = new GradientDrawable();
        hg.setCornerRadius(dp(18));
        hg.setColor(Pal.accent);
        paintHint.setBackground(hg);
        paintHint.setVisibility(View.VISIBLE);
        helpBtn = new TextView(this);
        helpBtn.setText("i");
        helpBtn.setTextSize(15);
        helpBtn.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC));
        helpBtn.setGravity(Gravity.CENTER);
        helpBtn.setTextColor(Pal.accent);
        helpBtn.setContentDescription("What can finger mode do?");
        GradientDrawable hb = new GradientDrawable();
        hb.setShape(GradientDrawable.OVAL);
        hb.setColor(Pal.raised);
        hb.setStroke(dp(2), Pal.accent);
        helpBtn.setBackground(hb);
        helpBtn.setOnClickListener(v -> {
            Hap.tick(v);
            toggleHelp();
        });
        helpCard = new TextView(this);
        helpCard.setTextSize(13.5f);
        helpCard.setTextColor(Pal.ink);
        helpCard.setLineSpacing(0, 1.15f);
        helpCard.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable hc = new GradientDrawable();
        hc.setCornerRadius(dp(18));
        hc.setColor(Pal.raised | 0xFF000000); // fully solid: no see-through shade behind the text
        hc.setStroke(dp(1), Pal.accent);
        helpCard.setBackground(hc);
        helpCard.setText(helpText());
        helpCard.setVisibility(View.GONE);
        LinearLayout hrow = new LinearLayout(this);
        hrow.setOrientation(LinearLayout.HORIZONTAL);
        hrow.setGravity(Gravity.CENTER_VERTICAL);
        hrow.addView(paintHint, new LinearLayout.LayoutParams(0, -2, 1f)); // wraps instead of pushing the i off screen
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(36), dp(36));
        ilp.leftMargin = dp(8);
        ilp.gravity = Gravity.CENTER_VERTICAL;
        hrow.addView(helpBtn, ilp);
        paintBox = new LinearLayout(this);
        paintBox.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, -2);
        clp.bottomMargin = dp(8);
        paintBox.addView(helpCard, clp);
        paintBox.addView(hrow, new LinearLayout.LayoutParams(-1, -2));
        paintBox.setVisibility(View.GONE);
        FrameLayout.LayoutParams hlp = new FrameLayout.LayoutParams(-1, -2);
        hlp.gravity = Gravity.BOTTOM | Gravity.START;
        hlp.leftMargin = dp(16);
        hlp.rightMargin = dp(88);
        hlp.bottomMargin = dp(22);
        area.addView(paintBox, hlp);
        fab = new FrameLayout(this);
        fab.setContentDescription("Finger mode: pick a style, then swipe over text to apply it");
        fabIcon = new IconView(this, IconView.FINGER);
        fabIcon.setClickable(false);
        fab.addView(fabIcon, new FrameLayout.LayoutParams(dp(56), dp(56)));
        fab.setClickable(true);
        fab.setOnClickListener(v -> {
            Hap.tick(v);
            setPaintMode(!paintMode);
        });
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(dp(56), dp(56));
        flp.gravity = Gravity.BOTTOM | Gravity.END;
        flp.rightMargin = dp(16);
        flp.bottomMargin = dp(16);
        area.addView(fab, flp);
        styleFab();
        root.addView(area, new LinearLayout.LayoutParams(-1, 0, 1f));

        buildCellEdit(area);
        root.addView(buildTableBar());
        toolbarView = buildToolbar();
        root.addView(toolbarView);

        // the floating X of full screen reading
        readClose = new FrameLayout(this);
        readClose.setContentDescription("Close full screen reading");
        IconView xi = new IconView(this, IconView.CLOSE_X);
        xi.setClickable(false);
        readClose.addView(xi, new FrameLayout.LayoutParams(dp(48), dp(48)));
        GradientDrawable xg = new GradientDrawable();
        xg.setShape(GradientDrawable.OVAL);
        xg.setColor(Pal.raised | 0xFF000000);
        xg.setStroke(dp(1), Pal.chip);
        readClose.setBackground(xg);
        readClose.setElevation(dp(4));
        readClose.setClickable(true);
        readClose.setOnClickListener(v -> { Hap.tick(v); exitReading(); });
        readClose.setVisibility(View.GONE);
        FrameLayout.LayoutParams xlp = new FrameLayout.LayoutParams(dp(48), dp(48));
        xlp.gravity = Gravity.TOP | Gravity.END;
        xlp.topMargin = dp(16);
        xlp.rightMargin = dp(16);
        area.addView(readClose, xlp);
        return root;
    }

    private void styleFab() {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(paintMode ? Pal.accent : Pal.raised);
        g.setStroke(dp(2), Pal.accent);
        fab.setBackground(g);
        fabIcon.setTint(paintMode ? Pal.onAccent : Pal.accent);
        fabIcon.setOn(paintMode);
    }

    private float scale = 1f;

    private void setScale(float v) {
        scale = Math.max(0.7f, Math.min(2.6f, v));
        RichText.zoom = scale;
        body.setTextSize(17 * scale); // the title keeps its own fixed size
        if (cellOpen) body.post(this::placeCell);
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

    /** Aa: pick any size (8 to 72) for the selected text, or for what you type next when nothing is selected. */
    private final java.util.ArrayList<TextView> sizeChips = new java.util.ArrayList<>();
    private static final int[] SIZE_STEPS = {12, 15, 18, 21, 24, 27, 30, 35, 40};

    /** The size buttons open as a layer of their own in the toolbar (like the colour tab): one tap, no popup. */
    private void showSizeLayer() {
        showTab(0); // keeps the Text tab lit; tap it again to come back to the normal row
        toolHost.removeAllViews();
        sizeChips.clear();
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.HORIZONTAL);
        wrap.setGravity(Gravity.CENTER_VERTICAL);
        wrap.setPadding(dp(8), dp(6), dp(8), dp(2));
        TextView back = sizeChip("\u2039");
        back.setContentDescription("Back to text tools");
        back.setOnClickListener(v -> { Hap.tick(v); showTab(0); });
        wrap.addView(back, new LinearLayout.LayoutParams(dp(40), dp(46)));
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        for (final int q : SIZE_STEPS) {
            TextView c = sizeChip(String.valueOf(q));
            c.setTag(q);
            c.setOnClickListener(v -> {
                Hap.tick(v);
                int cur = currentSize();
                setTextSize(cur == q ? 0 : q, selStart(), selEnd()); // tap the lit one again for normal size
                paintSizeChips();
            });
            sizeChips.add(c);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(48), dp(46));
            lp.leftMargin = dp(6);
            row.addView(c, lp);
        }
        hs.addView(row);
        wrap.addView(hs, new LinearLayout.LayoutParams(0, -2, 1f));
        wrap.addView(customSize(), new LinearLayout.LayoutParams(dp(78), dp(46)));
        Fonts.apply(wrap);
        toolHost.addView(wrap, new FrameLayout.LayoutParams(-1, -2));
        paintSizeChips();
    }

    private TextView sizeChip(String label) {
        TextView c = new TextView(this);
        c.setText(label);
        c.setTextSize(15);
        c.setTypeface(Typeface.DEFAULT_BOLD);
        c.setGravity(Gravity.CENTER);
        c.setTextColor(Pal.ink);
        GradientDrawable cg = new GradientDrawable();
        cg.setCornerRadius(dp(22));
        cg.setColor(Pal.chip);
        c.setBackground(cg);
        return c;
    }

    /** The custom size at the far right: a tap applies the number shown, a double tap lets you type another. */
    private View customSize() {
        final FrameLayout holder = new FrameLayout(this);
        final TextView chip = sizeChip("Aa\u22EF");
        chip.setContentDescription("Custom size: double tap to type a number");
        final int[] custom = {getSharedPreferences("settings", MODE_PRIVATE).getInt("custom_size", 0)};
        if (custom[0] > 0) chip.setText(String.valueOf(custom[0]));
        chip.setTag(-1);
        sizeChips.add(chip);
        final android.view.GestureDetector gd = new android.view.GestureDetector(this,
                new android.view.GestureDetector.SimpleOnGestureListener() {
                    @Override public boolean onDown(android.view.MotionEvent e) { return true; }

                    @Override public boolean onSingleTapConfirmed(android.view.MotionEvent e) {
                        Hap.tick(chip);
                        if (custom[0] > 0) {
                            int cur = currentSize();
                            setTextSize(cur == custom[0] ? 0 : custom[0], selStart(), selEnd());
                            paintSizeChips();
                        } else {
                            Toast.makeText(NoteEditorActivity.this, "Double tap to type a size", Toast.LENGTH_SHORT).show();
                        }
                        return true;
                    }

                    @Override public boolean onDoubleTap(android.view.MotionEvent e) {
                        Hap.confirm(chip);
                        final int selS = selStart();
                        final int selE = selEnd();
                        final EditText in = new EditText(NoteEditorActivity.this);
                        in.setInputType(InputType.TYPE_CLASS_NUMBER);
                        in.setGravity(Gravity.CENTER);
                        in.setTextSize(16);
                        in.setTypeface(Typeface.DEFAULT_BOLD);
                        in.setTextColor(Pal.ink);
                        in.setHint(RichText.MIN_SIZE + "\u2013" + RichText.MAX_SIZE);
                        in.setHintTextColor(Pal.muted);
                        in.setSingleLine(true);
                        in.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
                        GradientDrawable ig = new GradientDrawable();
                        ig.setCornerRadius(dp(22));
                        ig.setColor(Pal.chip);
                        ig.setStroke(dp(2), Pal.accent);
                        in.setBackground(ig);
                        in.setPadding(dp(4), 0, dp(4), 0);
                        holder.removeAllViews();
                        holder.addView(in, new FrameLayout.LayoutParams(-1, -1));
                        in.setOnEditorActionListener((tv, action, ev) -> {
                            int n = parseSize(in, 0);
                            if (n > 0) {
                                custom[0] = n;
                                getSharedPreferences("settings", MODE_PRIVATE).edit().putInt("custom_size", n).apply();
                                chip.setText(String.valueOf(n));
                                setTextSize(n, selS, selE);
                            }
                            android.view.inputmethod.InputMethodManager im = (android.view.inputmethod.InputMethodManager)
                                    getSystemService(INPUT_METHOD_SERVICE);
                            if (im != null) im.hideSoftInputFromWindow(in.getWindowToken(), 0);
                            holder.removeAllViews();
                            holder.addView(chip, new FrameLayout.LayoutParams(-1, -1));
                            paintSizeChips();
                            return true;
                        });
                        in.requestFocus();
                        android.view.inputmethod.InputMethodManager im = (android.view.inputmethod.InputMethodManager)
                                getSystemService(INPUT_METHOD_SERVICE);
                        if (im != null) im.showSoftInput(in, 0);
                        return true;
                    }
                });
        chip.setOnTouchListener((v, ev) -> gd.onTouchEvent(ev));
        holder.addView(chip, new FrameLayout.LayoutParams(-1, -1));
        holder.setPadding(dp(6), 0, 0, 0);
        return holder;
    }

    private int currentSize() {
        int s = selStart();
        int en = selEnd();
        int cur = RichText.sizeAt(body.getText(), s, en);
        if (s == en && pending.containsKey(RichText.SIZE)) cur = pending.get(RichText.SIZE);
        return cur;
    }

    private void paintSizeChips() {
        if (sizeChips.isEmpty()) return;
        int cur = currentSize();
        for (TextView c : sizeChips) {
            int tag = (Integer) c.getTag();
            int val = tag == -1 ? getSharedPreferences("settings", MODE_PRIVATE).getInt("custom_size", 0) : tag;
            boolean on = cur > 0 && val == cur;
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(dp(22));
            g.setColor(on ? Pal.accent : Pal.chip);
            c.setBackground(g);
            c.setTextColor(on ? Pal.onAccent : Pal.ink);
        }
    }

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

    // ------------------------------------------------ bottom toolbar: tools of the open tab, tab pills below

    private static final int TOGGLE_KEY = -11;
    private static final int KANBAN_KEY = -12;
    private static final int DIVIDER_KEY = -13;
    private static final int DATE_KEY = -14;
    private static final int TIME_KEY = -15;
    private static final int DATETIME_KEY = -16;
    private static final int IMPORT_FONT_KEY = -17;
    private static final int COUNTER_KEY = -18;
    private static final int REQ_FONT = 72;
    private static final int FONT_BASE = 1000;
    private static final int CASE_BASE = 2000;
    private static final int ACT_BASE = 3000; // templates and Markdown actions
    private static final int REQ_MD = 71;

    private static final String[] TABS = {"Text", "Lists", "Insert", "Case", "Color", "Font", "Templates", "Markdown"};
    private static int lastTab = 0;

    private FrameLayout toolHost;
    private LinearLayout tabRow;
    private int curTab = -1;
    private final java.util.ArrayList<TextView> fgSwatches = new java.util.ArrayList<>();
    private final java.util.ArrayList<TextView> hlSwatches = new java.util.ArrayList<>();

    /** One button of the tool row: a glyph over a small label. */
    /** A toolbar glyph drawn in the text colour: 0 = a to-do box with a tick, 1 = a calendar with a clock. */
    private static final class GlyphSpan extends android.text.style.ReplacementSpan {
        private final int kind;

        GlyphSpan(int kind) {
            this.kind = kind;
        }

        @Override
        public int getSize(android.graphics.Paint p, CharSequence text, int start, int end,
                           android.graphics.Paint.FontMetricsInt fm) {
            return Math.round(p.getTextSize() * 1.15f);
        }

        @Override
        public void draw(android.graphics.Canvas c, CharSequence text, int start, int end, float x, int top,
                         int y, int bottom, android.graphics.Paint p) {
            int oldC = p.getColor();
            android.graphics.Paint.Style oldS = p.getStyle();
            float oldW = p.getStrokeWidth();
            float u = p.getTextSize();
            float w = u * 1.15f;
            float cx = x + w / 2f;
            float cy = y - u * 0.34f;
            float h = u * 0.5f; // half size of the box
            p.setStyle(android.graphics.Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1.6f, u * 0.1f));
            p.setStrokeCap(android.graphics.Paint.Cap.ROUND);
            p.setStrokeJoin(android.graphics.Paint.Join.ROUND);
            if (kind == 0) {
                c.drawRoundRect(cx - h, cy - h, cx + h, cy + h, u * 0.2f, u * 0.2f, p);
                android.graphics.Path tick = new android.graphics.Path();
                tick.moveTo(cx - h * 0.5f, cy + h * 0.02f);
                tick.lineTo(cx - h * 0.1f, cy + h * 0.42f);
                tick.lineTo(cx + h * 0.55f, cy - h * 0.4f);
                c.drawPath(tick, p);
            } else {
                c.drawRoundRect(cx - h, cy - h * 0.8f, cx + h, cy + h, u * 0.16f, u * 0.16f, p);
                c.drawLine(cx - h, cy - h * 0.25f, cx + h, cy - h * 0.25f, p);
                c.drawLine(cx - h * 0.5f, cy - h * 1.1f, cx - h * 0.5f, cy - h * 0.55f, p);
                c.drawLine(cx + h * 0.5f, cy - h * 1.1f, cx + h * 0.5f, cy - h * 0.55f, p);
                p.setStyle(android.graphics.Paint.Style.FILL);
                c.drawCircle(cx - h * 0.35f, cy + h * 0.35f, u * 0.05f, p);
                c.drawCircle(cx + h * 0.05f, cy + h * 0.35f, u * 0.05f, p);
                c.drawCircle(cx + h * 0.45f, cy + h * 0.35f, u * 0.05f, p);
            }
            p.setColor(oldC);
            p.setStyle(oldS);
            p.setStrokeWidth(oldW);
        }
    }

    private static final class Tool {
        LinearLayout box;
        TextView glyph;
        TextView label;
    }

    private final Map<Integer, Tool> buttons = new HashMap<>();

    private View buildToolbar() {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setBackgroundColor(Pal.raised);
        toolHost = new FrameLayout(this);
        wrap.addView(toolHost, new LinearLayout.LayoutParams(-1, -2));

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        tabRow = new LinearLayout(this);
        tabRow.setOrientation(LinearLayout.HORIZONTAL);
        tabRow.setGravity(Gravity.CENTER_VERTICAL);
        tabRow.setPadding(dp(10), dp(2), dp(10), dp(8));
        for (int i = 0; i < TABS.length; i++) {
            final int idx = i;
            TextView t = new TextView(this);
            t.setText(TABS[i]);
            t.setTextSize(13);
            t.setTypeface(Typeface.DEFAULT_BOLD);
            t.setGravity(Gravity.CENTER);
            t.setPadding(dp(14), 0, dp(14), 0);
            t.setMinHeight(dp(34));
            t.setOnClickListener(v -> {
                Hap.tick(v);
                showTab(idx);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(34));
            lp.rightMargin = dp(6);
            tabRow.addView(t, lp);
        }
        hs.addView(tabRow);
        wrap.addView(hs, new LinearLayout.LayoutParams(-1, -2));
        showTab(lastTab);
        return wrap;
    }

    private void showTab(int i) {
        curTab = i;
        lastTab = i;
        buttons.clear();
        fgSwatches.clear();
        hlSwatches.clear();
        sizeChips.clear();
        toolHost.removeAllViews();
        View v = buildTab(i);
        if (i != 5) Fonts.apply(v);
        toolHost.addView(v, new FrameLayout.LayoutParams(-1, -2));
        for (int k = 0; k < tabRow.getChildCount(); k++) {
            TextView t = (TextView) tabRow.getChildAt(k);
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(dp(17));
            g.setColor(k == i ? Pal.accent : Pal.chip);
            t.setBackground(g);
            t.setTextColor(k == i ? Pal.onAccent : Pal.ink);
        }
        refreshToolbar();
    }

    private View buildTab(int i) {
        switch (i) {
            case 0:
                return toolRow(
                        tool(SIZE_KEY, "Aa", Typeface.BOLD, "Size", "Text size"),
                        tool(RichText.BOLD, "B", Typeface.BOLD, "Bold", "Bold"),
                        tool(RichText.ITALIC, "I", Typeface.ITALIC, "Italic", "Italic"),
                        tool(RichText.UNDER, "U", Typeface.NORMAL, "Underline", "Underline"),
                        tool(RichText.STRIKE, "S", Typeface.NORMAL, "Strike", "Strikethrough"),
                        tool(RichText.CODE, "{ }", Typeface.NORMAL, "Code", "Inline code"),
                        tool(RichText.HEAD, "H", Typeface.BOLD, "Heading", "Heading"),
                        tool(CLEAR_KEY, "Tx", Typeface.BOLD, "Clear", "Back to standard text (clear styles)"));
            case 1:
                return toolRow(
                        tool(BULLET_KEY, "•", Typeface.BOLD, "Bullets", "Bullet list"),
                        tool(NUM_KEY, "1.", Typeface.BOLD, "Numbered", "Numbered list"),
                        tool(CHECK_KEY, "\uFFFC", Typeface.BOLD, "To-do", "To-do list"),
                        tool(TOGGLE_KEY, "▸", Typeface.BOLD, "Toggle", "Toggle: hide or show the lines under it"),
                        tool(OUT_KEY, "←", Typeface.BOLD, "Out", "Move item out one level"),
                        tool(IN_KEY, "→", Typeface.BOLD, "In", "Nest item one level in"),
                        alignTool(ALIGN_L_KEY, 0),
                        alignTool(ALIGN_C_KEY, 1),
                        alignTool(ALIGN_R_KEY, 2));
            case 2:
                return toolRow(
                        tool(TABLE_KEY, "▦", Typeface.BOLD, "Table", "Table"),
                        tool(KANBAN_KEY, "◫", Typeface.BOLD, "Kanban", "Kanban board (To do, Doing, Done)"),
                        tool(RichText.QUOTE, "“", Typeface.BOLD, "Quote", "Quote"),
                        tool(RichText.CALLOUT, "▤", Typeface.BOLD, "Callout", "Callout box"),
                        tool(DIVIDER_KEY, "―", Typeface.BOLD, "Divider", "Divider line"),
                        tool(COUNTER_KEY, "±", Typeface.BOLD, "Counter", "A number with minus and plus buttons"),
                        tool(RichText.BLOCK, "</>", Typeface.BOLD, "Code block", "Code block"),
                        tool(LINK_KEY, "↗", Typeface.BOLD, "Note link", "Link to another note"),
                        tool(DATE_KEY, "◷", Typeface.BOLD, "Date", "Insert today's date"),
                        tool(TIME_KEY, "⏱", Typeface.BOLD, "Time", "Insert the time"),
                        tool(DATETIME_KEY, "\uFFFC", Typeface.BOLD, "Date+time", "Insert date and time together"));
            case 3: {
                Tool[] t = new Tool[CaseTools.NAMES.length];
                for (int k = 0; k < t.length; k++) {
                    t[k] = tool(CASE_BASE + k, caseGlyph(k), Typeface.BOLD, CaseTools.NAMES[k],
                            CaseTools.NAMES[k] + ": " + CaseTools.SAMPLES[k]);
                    t[k].box.setMinimumWidth(dp(70));
                }
                return toolRow(t);
            }
            case 4:
                return colorTab();
            case 5: {
                java.util.List<Tool> fl = new java.util.ArrayList<>();
                for (int k = 0; k < NoteFonts.count(); k++) {
                    if (!NoteFonts.shown(k)) continue;
                    Tool ft = tool(FONT_BASE + k, "Ag", Typeface.NORMAL, k == 0 ? "Default" : NoteFonts.name(k),
                            NoteFonts.note(k));
                    ft.glyph.setTypeface(NoteFonts.face(k));
                    ft.glyph.setTag("keepfont");
                    ft.glyph.setTextSize(22);
                    ft.box.setMinimumWidth(dp(74));
                    fl.add(ft);
                }
                Tool imp = tool(IMPORT_FONT_KEY, "+", Typeface.BOLD, "Import font", "Import a .ttf or .otf font file");
                imp.box.setMinimumWidth(dp(74));
                fl.add(imp);
                return toolRow(fl.toArray(new Tool[0]));
            }
            case 6:
                return toolRow(
                        tool(ACT_BASE, "☰", Typeface.BOLD, "Daily log", "Daily log template"),
                        tool(ACT_BASE + 1, "☷", Typeface.BOLD, "Meeting", "Meeting notes template"),
                        tool(ACT_BASE + 2, "☕", Typeface.BOLD, "Recipe", "Recipe template"),
                        tool(ACT_BASE + 3, "▦", Typeface.BOLD, "Week plan", "Week plan table"),
                        tool(ACT_BASE + 4, "★", Typeface.BOLD, "Mine", "Insert my saved template"),
                        tool(ACT_BASE + 5, "+", Typeface.BOLD, "Save as mine", "Save this note as my template"));
            default:
                return toolRow(
                        tool(ACT_BASE + 10, "↓", Typeface.BOLD, "Paste MD", "Paste Markdown from the clipboard as formatted text"),
                        tool(ACT_BASE + 11, "MD", Typeface.BOLD, "Copy MD", "Copy this note as Markdown"),
                        tool(ACT_BASE + 12, "⇩", Typeface.BOLD, "Save .md", "Save this note as a .md file"),
                        tool(ACT_BASE + 13, "↻", Typeface.BOLD, "Convert", "Turn the selected Markdown into formatted text"),
                        tool(ACT_BASE + 14, "?", Typeface.BOLD, "Shortcuts", "Markdown typing shortcuts"));
        }
    }

    /** Align left / centre / right button: three little lines lined up the way the text will be. */
    private Tool alignTool(int key, int mode) {
        Tool t = tool(key, "\u2501\u2501\u2501\u2501\n\u2501\u2501\n\u2501\u2501\u2501\u2501", Typeface.BOLD,
                mode == 0 ? "Left" : mode == 1 ? "Centre" : "Right",
                mode == 0 ? "Align left" : mode == 1 ? "Align centre" : "Align right");
        t.glyph.setTextSize(8);
        t.glyph.setSingleLine(false);
        t.glyph.setLineSpacing(0, 0.75f);
        t.glyph.setGravity(mode == 0 ? Gravity.START : mode == 1 ? Gravity.CENTER_HORIZONTAL : Gravity.END);
        t.glyph.setPadding(0, dp(3), 0, dp(3));
        t.glyph.setMinWidth(dp(26));
        return t;
    }

    private static String caseGlyph(int k) {
        switch (k) {
            case 0: return "AA";
            case 1: return "aa";
            case 2: return "Aa";
            case 3: return "A.";
            case 4: return "aB";
            case 5: return "AB";
            case 6: return "a_b";
            case 7: return "a-b";
            case 8: return "A_B";
            case 9: return "a.b";
            case 10: return "aA";
            default: return "⇅";
        }
    }

    private View toolRow(Tool... tools) {
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(6), dp(8), dp(2));
        for (Tool t : tools) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(54));
            lp.rightMargin = dp(4);
            row.addView(t.box, lp);
        }
        hs.addView(row);
        return hs;
    }

    private Tool tool(final int key, String glyph, int style, String label, String desc) {
        final Tool t = new Tool();
        t.box = new LinearLayout(this);
        t.box.setOrientation(LinearLayout.VERTICAL);
        t.box.setGravity(Gravity.CENTER);
        t.box.setMinimumWidth(dp(58));
        t.box.setPadding(dp(8), dp(2), dp(8), dp(2));
        t.box.setContentDescription(desc);
        t.glyph = new TextView(this);
        t.glyph.setText(glyph);
        t.glyph.setTextSize(18);
        t.glyph.setTypeface(Typeface.DEFAULT, style);
        t.glyph.setGravity(Gravity.CENTER);
        t.glyph.setSingleLine(true);
        if (key == CHECK_KEY || key == DATETIME_KEY) {
            android.text.SpannableString gs = new android.text.SpannableString("\uFFFC");
            gs.setSpan(new GlyphSpan(key == CHECK_KEY ? 0 : 1), 0, 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            t.glyph.setText(gs);
        }
        if (key == RichText.UNDER) t.glyph.setPaintFlags(t.glyph.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        if (key == RichText.STRIKE) t.glyph.setPaintFlags(t.glyph.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
        if (key == RichText.CODE || key == RichText.BLOCK) t.glyph.setTypeface(Typeface.MONOSPACE, key == RichText.BLOCK ? Typeface.BOLD : Typeface.NORMAL);
        if (key == RichText.QUOTE) { // a big display-serif opening quotation mark, like the pull-quotes on the web
            t.glyph.setTypeface(NoteFonts.face(5));
            t.glyph.setTag("keepfont");
            t.glyph.setTextSize(30);
            t.glyph.setPadding(0, dp(6), 0, 0);
            t.glyph.setIncludeFontPadding(false);
            // same height as every other glyph, so the "Quote" label lines up with the rest of the row
            TextView ref = new TextView(this);
            ref.setTextSize(18);
            android.graphics.Paint.FontMetricsInt fmi = ref.getPaint().getFontMetricsInt();
            t.glyph.setHeight(fmi.bottom - fmi.top);
        }
        t.label = new TextView(this);
        t.label.setText(label);
        t.label.setTextSize(10.5f);
        t.label.setGravity(Gravity.CENTER);
        t.label.setSingleLine(true);
        t.box.addView(t.glyph, new LinearLayout.LayoutParams(-2, -2));
        t.box.addView(t.label, new LinearLayout.LayoutParams(-2, -2));
        t.box.setOnClickListener(v -> {
            Hap.tick(v);
            if (key == SIZE_KEY) showSizeLayer();
            else onTool(key);
        });
        buttons.put(key, t);
        return t;
    }

    // ----- Color tab: text and mark swatches right in the tab

    private View colorTab() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setPadding(dp(12), dp(6), dp(8), dp(2));
        v.addView(colorLine("TEXT", RichText.FG_COLORS, true));
        v.addView(colorLine("MARK", RichText.HL_COLORS, false));
        return v;
    }

    private View colorLine(String name, int[] colors, boolean text) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = new TextView(this);
        label.setText(name);
        label.setTextColor(Pal.muted);
        label.setTextSize(11);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(label, new LinearLayout.LayoutParams(dp(46), -2));
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout dots = new LinearLayout(this);
        dots.setOrientation(LinearLayout.HORIZONTAL);
        dots.setGravity(Gravity.CENTER_VERTICAL);
        for (final int color : colors) dots.addView(swatch(color, false, text), swatchLp());
        final String ckey = text ? "custom_fg" : "custom_hl";
        int saved = getSharedPreferences("settings", MODE_PRIVATE).getInt(ckey, 0);
        if (saved != 0) dots.addView(swatch(saved, false, text), swatchLp()); // the last colour picked below
        TextView more = new TextView(this);
        more.setText("+");
        more.setGravity(Gravity.CENTER);
        more.setTextColor(0xFFFFFFFF);
        more.setTextSize(18);
        more.setTypeface(Typeface.DEFAULT_BOLD);
        more.setContentDescription("Pick any colour");
        GradientDrawable rg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{
                0xFFE5484D, 0xFFF5D90A, 0xFF30A46C, 0xFF3E8BFF, 0xFF8E4EC6});
        rg.setShape(GradientDrawable.OVAL);
        more.setBackground(rg);
        more.setOnClickListener(v -> { Hap.tick(v); pickColor(text); });
        dots.addView(more, swatchLp());
        dots.addView(swatch(Pal.chip, true, text), swatchLp()); // none / default
        hs.addView(dots);
        row.addView(hs, new LinearLayout.LayoutParams(0, -2, 1f));
        row.setMinimumHeight(dp(44));
        return row;
    }

    /** Any colour: a saturation / brightness square, a hue bar and a hex box. The pick is applied and kept as a swatch. */
    private void pickColor(final boolean text) {
        final String ckey = text ? "custom_fg" : "custom_hl";
        int start = getSharedPreferences("settings", MODE_PRIVATE).getInt(ckey, text ? 0xFF1E40AF : 0xFFFFE066);
        if (start == 0) start = 0xFF1E40AF;
        final int[] pick = {start};
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(8), dp(16), 0);
        final ColorPickerView cp = new ColorPickerView(this);
        cp.setColor(start);
        box.addView(cp, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        final View preview = new View(this);
        final EditText hex = new EditText(this);
        hex.setSingleLine(true);
        hex.setTextSize(16);
        hex.setTypeface(Typeface.MONOSPACE);
        hex.setTextColor(Pal.ink);
        hex.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(7)});
        final boolean[] fromPicker = {false};
        final Runnable paint = () -> {
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(pick[0]);
            g.setStroke(dp(1), Pal.chip);
            preview.setBackground(g);
            fromPicker[0] = true;
            hex.setText(String.format("#%06X", pick[0] & 0xFFFFFF));
            fromPicker[0] = false;
        };
        cp.setListener(c -> { pick[0] = c; paint.run(); });
        hex.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence a, int b, int c, int d) { }
            @Override public void onTextChanged(CharSequence a, int b, int c, int d) { }
            @Override public void afterTextChanged(Editable ed) {
                if (fromPicker[0]) return;
                String t = ed.toString().trim();
                if (!t.startsWith("#")) t = "#" + t;
                if (t.length() != 7) return;
                try {
                    int c = 0xFF000000 | Integer.parseInt(t.substring(1), 16);
                    pick[0] = c;
                    cp.setColor(c);
                    GradientDrawable g = new GradientDrawable();
                    g.setShape(GradientDrawable.OVAL);
                    g.setColor(c);
                    g.setStroke(dp(1), Pal.chip);
                    preview.setBackground(g);
                } catch (NumberFormatException ex) {
                    // keep the last good colour
                }
            }
        });
        paint.run();
        line.addView(preview, new LinearLayout.LayoutParams(dp(40), dp(40)));
        LinearLayout.LayoutParams hlp2 = new LinearLayout.LayoutParams(0, -2, 1f);
        hlp2.leftMargin = dp(12);
        line.addView(hex, hlp2);
        box.addView(line, new LinearLayout.LayoutParams(-1, -2));
        new Dlg.Builder(this)
                .setTitle(text ? "Text colour" : "Highlight colour")
                .setView(box)
                .setPositiveButton("Apply", (d, w) -> {
                    getSharedPreferences("settings", MODE_PRIVATE).edit().putInt(ckey, pick[0]).apply();
                    if (text) textColor(pick[0]); else highlight(pick[0]);
                    showTab(curTab); // the new swatch appears in the row
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private LinearLayout.LayoutParams swatchLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(34), dp(34));
        lp.setMargins(0, dp(4), dp(10), dp(4));
        return lp;
    }

    private TextView swatch(final int color, final boolean none, final boolean text) {
        TextView t = new TextView(this);
        t.setGravity(Gravity.CENTER);
        t.setTextColor(Pal.muted);
        t.setTextSize(14);
        t.setTag(none ? 0 : color);
        if (none) {
            t.setText("✕");
            t.setContentDescription(text ? "Default text colour" : "No highlight");
        } else {
            t.setContentDescription(text ? "Text colour" : "Highlight colour");
        }
        paintSwatch(t, color, false);
        t.setOnClickListener(v -> {
            Hap.tick(v);
            int c = none ? 0 : color;
            if (text) textColor(c); else highlight(c);
        });
        (text ? fgSwatches : hlSwatches).add(t);
        return t;
    }

    private void paintSwatch(TextView t, int color, boolean sel) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        if (sel) g.setStroke(dp(3), Pal.ink);
        t.setBackground(g);
    }

    private void updateSwatches() {
        if (fgSwatches.isEmpty() && hlSwatches.isEmpty()) return;
        int s = selStart();
        int en = selEnd();
        Editable e = body.getText();
        int pos = s == en ? Math.max(0, s - 1) : s;
        int fg = pending.containsKey(RichText.FG) && s == en ? pending.get(RichText.FG) : RichText.colorAt(e, RichText.FG, pos);
        int hl = pending.containsKey(RichText.HILITE) && s == en ? pending.get(RichText.HILITE) : RichText.colorAt(e, RichText.HILITE, pos);
        if (paintMode) {
            fg = brush == RichText.FG ? brushFg : -1;
            hl = brush == RichText.HILITE ? brushHl : -1;
        }
        for (TextView t : fgSwatches) {
            int c = (Integer) t.getTag();
            paintSwatch(t, c == 0 ? Pal.chip : c, c == fg);
        }
        for (TextView t : hlSwatches) {
            int c = (Integer) t.getTag();
            paintSwatch(t, c == 0 ? Pal.chip : c, c == hl);
        }
    }

    // ----- the tools themselves

    private void dayStamp(int what) {
        String f = what == 1 ? "HH:mm" : what == 2 ? "EEE, d MMM yyyy, HH:mm" : "EEE, d MMM yyyy";
        insertPlain(new java.text.SimpleDateFormat(f, java.util.Locale.getDefault()).format(new java.util.Date()));
    }

    private void insertPlain(String text) {
        commitNow();
        Editable e = body.getText();
        int s = selStart();
        int en = selEnd();
        busy = true;
        e.replace(s, en, text);
        busy = false;
        body.setSelection(Math.min(e.length(), s + text.length()));
        changed();
        refreshToolbar();
    }

    /** A tap on a counter: minus and plus move it by its step, the number opens the editor. */
    private void onCounterTap(RichText.Counter c, int zone) {
        Editable e = body.getText();
        if (e.getSpanStart(c) < 0) return;
        if (zone == 1) {
            counterDialog(c);
            return;
        }
        long v = RichText.counterValue(e, c) + (zone == 2 ? c.step : -c.step);
        commitNow();
        busy = true;
        RichText.setCounter(e, c, v, c.step);
        busy = false;
        changed();
    }

    private EditText counterField(LinearLayout box, String label, String hint, String value, boolean signed) {
        TextView l = new TextView(this);
        l.setText(label);
        l.setTextSize(12);
        l.setTextColor(Pal.muted);
        l.setPadding(0, dp(10), 0, dp(2));
        box.addView(l);
        EditText f = new EditText(this);
        f.setSingleLine(true);
        f.setTextSize(17);
        f.setTextColor(Pal.ink);
        f.setHint(hint);
        if (signed) {
            f.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
        } else if (hint.startsWith("e.g.")) {
            f.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        }
        f.setText(value);
        f.setSelection(f.getText().length());
        box.addView(f, new LinearLayout.LayoutParams(-1, -2));
        return f;
    }

    private static long parseLong(String s, long fallback) {
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    /**
     * Makes a counter (existing == null: asks for a name, a start value and a step, and inserts "Name: 0" with
     * the buttons) or edits one (value, step, and an "add or subtract" box for typing a whole amount at once).
     */
    private void counterDialog(final RichText.Counter existing) {
        final Editable e = body.getText();
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(22), dp(4), dp(22), 0);
        final long cur = existing == null ? 0 : RichText.counterValue(e, existing);
        final EditText name = existing == null
                ? counterField(box, "Name (optional)", "e.g. Tables moved", "", false) : null;
        final EditText val = counterField(box, existing == null ? "Start at" : "Value", "0", String.valueOf(cur), true);
        final EditText step = counterField(box, "Each tap on - or + moves it by", "1",
                String.valueOf(existing == null ? 1 : existing.step), false);
        step.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        final EditText delta = existing == null ? null
                : counterField(box, "Or add / subtract an amount now", "e.g. 9 or -3", "", true);
        new Dlg.Builder(this)
                .setTitle(existing == null ? "New counter" : "Edit counter")
                .setView(box)
                .setPositiveButton(existing == null ? "Insert" : "Save", (d, w) -> {
                    int st = (int) Math.max(1, Math.min(9999, parseLong(step.getText().toString(), 1)));
                    long v = parseLong(val.getText().toString(), 0);
                    if (existing == null) {
                        commitNow();
                        busy = true;
                        int at = RichText.insertCounter(e, selStart(), selEnd(), name.getText().toString(), v, st);
                        busy = false;
                        long shown = Math.max(-9_999_999L, Math.min(9_999_999L, v));
                        body.setSelection(Math.min(e.length(), at + Long.toString(shown).length()));
                        changed();
                        refreshToolbar();
                    } else if (e.getSpanStart(existing) >= 0) {
                        v += parseLong(delta.getText().toString(), 0);
                        commitNow();
                        busy = true;
                        RichText.setCounter(e, existing, v, st);
                        busy = false;
                        changed();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Makes a table cell a counter (starting from the number already in it), edits one, or turns it back to text. */
    private void tableCounterDialog(final RichText.Tbl t, final int r, final int c) {
        final String old = t.cells[r][c];
        long[] cv = RichText.Tbl.counterOf(old);
        long start = 0;
        if (cv != null) {
            start = cv[0];
        } else {
            try {
                start = Long.parseLong(RichText.Tbl.label(old).trim());
            } catch (NumberFormatException ex) {
                start = 0;
            }
        }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(22), dp(4), dp(22), 0);
        final EditText val = counterField(box, "Value", "0", String.valueOf(start), true);
        final EditText step = counterField(box, "Each tap on - or + moves it by", "1",
                String.valueOf(cv == null ? 1 : cv[1]), false);
        step.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        final EditText delta = counterField(box, "Or add / subtract an amount now", "e.g. 9 or -3", "", true);
        Dlg.Builder b = new Dlg.Builder(this);
        b.setTitle(cv == null ? "Counter in this cell" : "Edit counter")
                .setView(box)
                .setPositiveButton("Save", (d, w) -> {
                    int st = (int) Math.max(1, Math.min(9999, parseLong(step.getText().toString(), 1)));
                    long v = parseLong(val.getText().toString(), 0) + parseLong(delta.getText().toString(), 0);
                    if (body.getText().getSpanStart(t) < 0 || r >= t.rows() || c >= t.cols()) return;
                    commitNow();
                    t.cells[r][c] = RichText.Tbl.counterCell(v, st);
                    RichText.refresh(body.getText(), t);
                    changed();
                    body.invalidate();
                })
                .setNegativeButton("Cancel", null);
        if (cv != null) {
            b.setNeutralButton("Back to text", (d, w) -> {
                if (body.getText().getSpanStart(t) < 0 || r >= t.rows() || c >= t.cols()) return;
                commitNow();
                t.cells[r][c] = String.valueOf(cv[0]);
                RichText.refresh(body.getText(), t);
                changed();
                body.invalidate();
            });
        }
        b.show();
    }

    private void insertTableBlock(RichText.Tbl t) {
        commitNow();
        Editable e = body.getText();
        busy = true;
        int at = RichText.insertTable(e, selStart(), t);
        busy = false;
        body.setSelection(Math.min(e.length(), at + 2));
        changed();
        final int pos = at;
        body.post(() -> {
            RichText.Tbl made = RichText.tableOnLine(body.getText(), pos);
            if (made != null) openCell(made, 0, 0);
        });
    }

    private void applyFont(int idx) {
        commitNow();
        Editable e = body.getText();
        int s = selStart();
        int en = selEnd();
        if (s == en) {
            pending.put(RichText.FONT, idx);
        } else {
            RichText.apply(e, RichText.FONT, s, en, idx, idx != 0);
            changed();
        }
        refreshToolbar();
    }

    private void applyCase(int mode) {
        commitNow();
        Editable e = body.getText();
        int s = selStart();
        int en = selEnd();
        busy = true;
        int ne = CaseTools.apply(e, mode, s, en);
        busy = false;
        if (s != en) body.setSelection(s, Math.min(e.length(), ne));
        changed();
        refreshToolbar();
    }

    private static final String[] TEMPLATES = {
            "# {date}\n\n## Plan\n- [ ] \n- [ ] \n- [ ] \n\n## Notes\n- \n\n## Done today\n- ",
            "# Meeting\n\n**Date:** {date}\n**With:** \n\n## Agenda\n1. \n2. \n\n## Notes\n- \n\n## Action items\n| Task | Owner | Done |\n| --- | --- | --- |\n|  |  | [ ] |\n|  |  | [ ] |",
            "# Recipe\n\n> Serves 2 · 30 min\n\n## Ingredients\n- [ ] \n- [ ] \n- [ ] \n\n## Steps\n1. \n2. \n3. \n\n## Notes\n- ",
            "# Week plan\n\n| Mon | Tue | Wed | Thu | Fri |\n| --- | --- | --- | --- | --- |\n|  |  |  |  |  |\n|  |  |  |  |  |\n|  |  |  |  |  |"};

    private void insertTemplate(int i) {
        String md;
        if (i == 4) {
            md = getSharedPreferences("settings", MODE_PRIVATE).getString("my_template", "");
            if (md.isEmpty()) {
                Toast.makeText(this, "Nothing saved yet: write a note, then tap Save as mine", Toast.LENGTH_LONG).show();
                return;
            }
        } else {
            md = TEMPLATES[i].replace("{date}",
                    new java.text.SimpleDateFormat("EEE, d MMM yyyy", java.util.Locale.getDefault()).format(new java.util.Date()));
        }
        commitNow();
        busy = true;
        int c = Markdown.insert(body.getText(), selStart(), md);
        busy = false;
        body.setSelection(Math.min(body.getText().length(), c));
        changed();
        refreshToolbar();
    }

    private void markdownAction(int n) {
        Editable e = body.getText();
        switch (n) {
            case 0: { // paste
                android.content.ClipboardManager cm = getSystemService(android.content.ClipboardManager.class);
                CharSequence clip = cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip().getItemCount() > 0
                        ? cm.getPrimaryClip().getItemAt(0).coerceToText(this) : null;
                if (clip == null || clip.length() == 0) {
                    Toast.makeText(this, "The clipboard is empty", Toast.LENGTH_SHORT).show();
                    return;
                }
                commitNow();
                busy = true;
                int c = Markdown.insert(e, selStart(), clip.toString());
                busy = false;
                body.setSelection(Math.min(e.length(), c));
                changed();
                break;
            }
            case 1: {
                android.content.ClipboardManager cm = getSystemService(android.content.ClipboardManager.class);
                cm.setPrimaryClip(android.content.ClipData.newPlainText("Markdown",
                        Markdown.export(e, title.getText().toString())));
                Toast.makeText(this, "Copied as Markdown", Toast.LENGTH_SHORT).show();
                break;
            }
            case 2: {
                flushCell();
                String name = title.getText().toString().trim();
                if (name.isEmpty()) name = "note";
                android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT);
                i.addCategory(android.content.Intent.CATEGORY_OPENABLE);
                i.setType("text/markdown");
                i.putExtra(android.content.Intent.EXTRA_TITLE, name.replaceAll("[\\\\/:*?\"<>|]", "_") + ".md");
                startActivityForResult(i, REQ_MD);
                break;
            }
            case 3: {
                int s = selStart();
                int en = selEnd();
                if (s == en) {
                    Toast.makeText(this, "Select the Markdown text first", Toast.LENGTH_SHORT).show();
                    return;
                }
                commitNow();
                busy = true;
                int c = Markdown.convertSelection(e, s, en);
                busy = false;
                body.setSelection(Math.min(e.length(), c));
                changed();
                break;
            }
            default:
                new Dlg.Builder(this)
                        .setTitle("Markdown shortcuts")
                        .setMessage("Type at the start of a line, then a space:\n\n"
                                + "#  heading\n-  or  *  bullet\n[]  to-do\n>  quote\n\n"
                                + "Type --- and press Enter for a divider line.\n"
                                + "1. starts a numbered list.\n\n"
                                + "Paste MD turns pasted Markdown (headings, **bold**, *italic*, ~~strike~~, `code`, ==mark==, lists, quotes, tables) into formatted text. Copy MD and Save .md go the other way.")
                        .setPositiveButton("OK", null)
                        .show();
        }
        refreshToolbar();
    }

    @Override
    protected void onActivityResult(int req, int res, android.content.Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_HISTORY) {
            long hid = res == RESULT_OK && data != null ? data.getLongExtra(NoteHistoryActivity.EXTRA_NOTE, 0) : 0;
            if (hid > 0) {
                // The restored text is in the database: reopen the note from there, and never save this stale screen over it.
                dirty = false;
                deleted = true;
                finish();
                startActivity(new Intent(this, NoteEditorActivity.class).putExtra(EXTRA_ID, hid));
            }
            return;
        }
        if (req == REQ_FONT) {
            if (res == RESULT_OK && data != null && data.getData() != null) {
                String nm = NoteFonts.importFont(this, data.getData());
                if (nm == null) {
                    Toast.makeText(this, "That file is not a usable font (use .ttf or .otf)", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, nm + " added. Pick it in the Font tab.", Toast.LENGTH_LONG).show();
                    showTab(5);
                }
            }
            return;
        }
        if (req != REQ_MD || res != RESULT_OK || data == null || data.getData() == null) return;
        try (java.io.OutputStream out = getContentResolver().openOutputStream(data.getData())) {
            out.write(Markdown.export(body.getText(), title.getText().toString()).getBytes("UTF-8"));
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
        } catch (Exception ex) {
            Toast.makeText(this, "Could not save the file", Toast.LENGTH_LONG).show();
        }
    }

    // ----------------------------------------- tables: edited right in the note, no dialog

    private EditText cellEdit;
    private RichText.Tbl curTbl;
    private int curR;
    private int curC;
    private boolean cellOpen;
    private boolean cellBusy;
    private FrameLayout areaView;
    private View tableBarView;

    private void buildCellEdit(FrameLayout area) {
        areaView = area;
        cellEdit = new EditText(this);
        cellEdit.setTag("keepfont");
        cellEdit.setSingleLine(true);
        cellEdit.setIncludeFontPadding(false);
        cellEdit.setGravity(Gravity.CENTER_VERTICAL);
        cellEdit.setTextColor(Pal.ink);
        cellEdit.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        cellEdit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        cellEdit.setVisibility(View.GONE);
        cellEdit.setOnEditorActionListener((tv, a, ev) -> {
            nextCell();
            return true;
        });
        cellEdit.setOnFocusChangeListener((v, has) -> {
            if (!has && cellOpen && !cellBusy) closeCell(false);
        });
        area.addView(cellEdit, new FrameLayout.LayoutParams(dp(100), dp(40)));
        noteScroll.getViewTreeObserver().addOnScrollChangedListener(this::placeCell);
        area.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (cellOpen && (b - t) != (ob - ot)) {
                placeCell();
                ensureCellVisible();
            }
        });
    }

    private static int blend(int base, int over, float a) {
        int r = Math.round(android.graphics.Color.red(base) * (1 - a) + android.graphics.Color.red(over) * a);
        int g = Math.round(android.graphics.Color.green(base) * (1 - a) + android.graphics.Color.green(over) * a);
        int b = Math.round(android.graphics.Color.blue(base) * (1 - a) + android.graphics.Color.blue(over) * a);
        return android.graphics.Color.rgb(r, g, b);
    }

    private void onTableTap(RichText.Tbl t, int r, int c, boolean onTick) {
        if (paintMode || reading) return;
        long[] cv = r == 0 ? null : RichText.Tbl.counterOf(t.cells[r][c]);
        if (cv != null) { // a counter cell: - and + step it, the number opens its editor
            if (cellOpen) flushCell();
            int zone = body.lastTableZone();
            if (zone == 0 || zone == 2) {
                commitNow();
                t.cells[r][c] = RichText.Tbl.counterCell(cv[0] + (zone == 2 ? cv[1] : -cv[1]), cv[1]);
                RichText.refresh(body.getText(), t);
                changed();
                body.invalidate();
            } else {
                tableCounterDialog(t, r, c);
            }
            return;
        }
        if (onTick) {
            flushCell();
            commitNow();
            t.flipTick(r, c);
            RichText.refresh(body.getText(), t);
            changed();
            body.invalidate();
            return;
        }
        openCell(t, r, c);
    }

    private void openCell(RichText.Tbl t, int r, int c) {
        if (paintMode || t == null || r >= t.rows() || c >= t.cols()) return;
        if (cellOpen) flushCell();
        cellBusy = true;
        curTbl = t;
        curR = r;
        curC = c;
        cellOpen = true;
        String lab = RichText.Tbl.label(t.cells[r][c]);
        cellEdit.setText(lab);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(6));
        g.setColor(r == 0 ? blend(Pal.bg, Pal.accent, Pal.dark ? 0.22f : 0.18f) : Pal.card);
        g.setStroke(dp(2), Pal.accent);
        cellEdit.setBackground(g);
        Typeface base = body.getTypeface() == null ? Typeface.DEFAULT : body.getTypeface();
        cellEdit.setTypeface(r == 0 ? Typeface.create(base, Typeface.BOLD) : base);
        cellEdit.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, body.getTextSize());
        cellEdit.setHint(r == 0 ? "Header" : "");
        cellEdit.setHintTextColor(Pal.muted);
        cellEdit.setVisibility(View.VISIBLE);
        placeCell();
        cellEdit.requestFocus();
        if (lab.matches("Column \\d+")) cellEdit.selectAll();
        else cellEdit.setSelection(lab.length());
        android.view.inputmethod.InputMethodManager imm =
                getSystemService(android.view.inputmethod.InputMethodManager.class);
        imm.showSoftInput(cellEdit, 0);
        cellBusy = false;
        tableBarView.setVisibility(View.VISIBLE);
        ensureCellVisible();
        cellEdit.postDelayed(this::ensureCellVisible, 320);
    }

    /** Pixel geometry of the open cell inside the editor area: {x, y, w, h, paddingLeft}. */
    private float[] cellGeometry() {
        android.text.Layout l = body.getLayout();
        if (l == null || curTbl == null) return null;
        int st = body.getText().getSpanStart(curTbl);
        if (st < 0) return null;
        int line = l.getLineForOffset(st);
        android.graphics.Paint p = body.getPaint();
        float top = l.getLineTop(line) + body.getTotalPaddingTop() + body.getTop();
        float left = body.getTotalPaddingLeft();
        float rh = curTbl.rowH(p);
        float cw = curTbl.colW();
        float pad = curTbl.padX(p);
        float size = p.getTextSize();
        boolean tick = RichText.Tbl.tick(curTbl.cells[curR][curC]) > 0;
        float ax = left + curC * cw;
        float ox = tick ? ax + pad + size * 1.25f : ax;
        float padL = tick ? size * 0.05f : pad;
        return new float[]{ox, top + curR * rh, cw - (ox - ax) - 1, rh - 1, padL, pad};
    }

    private void placeCell() {
        if (!cellOpen || curTbl == null) return;
        float[] g = cellGeometry();
        if (g == null) {
            dropCell();
            return;
        }
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) cellEdit.getLayoutParams();
        int w = Math.round(g[2]);
        int h = Math.round(g[3]);
        if (lp.width != w || lp.height != h) {
            lp.width = w;
            lp.height = h;
            cellEdit.setLayoutParams(lp);
        }
        cellEdit.setPadding(Math.round(g[4]), 0, Math.round(g[5]), 0);
        cellEdit.setTranslationX(g[0]);
        cellEdit.setTranslationY(g[1] - noteScroll.getScrollY());
    }

    private void ensureCellVisible() {
        if (!cellOpen) return;
        float[] g = cellGeometry();
        if (g == null) return;
        float y = g[1] - noteScroll.getScrollY();
        int ah = areaView.getHeight();
        if (y < dp(8)) noteScroll.scrollBy(0, Math.round(y - dp(8)));
        else if (ah > 0 && y + g[3] > ah - dp(8)) noteScroll.scrollBy(0, Math.round(y + g[3] - ah + dp(8)));
        placeCell();
    }

    /** Writes the open cell's text back into the table (one undo step). */
    private void flushCell() {
        if (!cellOpen || curTbl == null) return;
        if (body.getText().getSpanStart(curTbl) < 0 || curR >= curTbl.rows() || curC >= curTbl.cols()) return;
        String txt = cellEdit.getText().toString().replace('\n', ' ');
        String old = curTbl.cells[curR][curC];
        String neu = RichText.Tbl.compose(RichText.Tbl.tick(old), txt);
        if (neu.equals(old)) return;
        commitNow();
        curTbl.cells[curR][curC] = neu;
        RichText.refresh(body.getText(), curTbl);
        changed();
    }

    private void closeCell(boolean hideKeyboard) {
        if (!cellOpen) return;
        flushCell();
        dropCell();
        if (hideKeyboard) {
            getSystemService(android.view.inputmethod.InputMethodManager.class)
                    .hideSoftInputFromWindow(body.getWindowToken(), 0);
            body.requestFocus();
        }
    }

    /** Hides the cell editor without writing anything. */
    private void dropCell() {
        cellOpen = false;
        if (cellEdit != null) cellEdit.setVisibility(View.GONE);
        if (tableBarView != null) tableBarView.setVisibility(View.GONE);
    }

    /** IME "next": on to the cell on the right, then the next row; past the last cell it adds a row. */
    private void nextCell() {
        if (!cellOpen) return;
        flushCell();
        final RichText.Tbl t = curTbl;
        int r = curR;
        int c = curC + 1;
        if (c >= t.cols()) {
            c = 0;
            r++;
        }
        if (r >= t.rows()) {
            if (t.rows() >= 40) return;
            commitNow();
            t.addRow(t.rows());
            RichText.refresh(body.getText(), t);
            changed();
        }
        final int rr = r;
        final int cc = c;
        cellOpen = false;
        body.post(() -> openCell(t, rr, cc));
    }

    private View buildTableBar() {
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setBackgroundColor(Pal.raised);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(6), dp(8), dp(6));
        String[] names = {"Done", "+ Row", "+ Col", "− Row", "− Col", "Tick", "←", "→", "↑", "↓", "± Counter", "Delete table"};
        for (int i = 0; i < names.length; i++) {
            final int op = i;
            TextView chip = toolChip(names[i], () -> tableOp(op));
            chip.setMinHeight(dp(38));
            chip.setTextSize(13);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(38));
            lp.rightMargin = dp(6);
            row.addView(chip, lp);
        }
        hs.addView(row);
        hs.setVisibility(View.GONE);
        tableBarView = hs;
        return hs;
    }

    private void tableOp(int op) {
        if (!cellOpen || curTbl == null) return;
        if (op == 0) {
            closeCell(true);
            return;
        }
        flushCell();
        final RichText.Tbl t = curTbl;
        int r = curR;
        int c = curC;
        commitNow();
        Editable e = body.getText();
        switch (op) {
            case 1:
                if (t.rows() >= 40) return;
                t.addRow(r + 1);
                r = Math.max(1, r + 1);
                break;
            case 2:
                if (t.cols() >= 8) return;
                t.addCol(c + 1);
                c++;
                break;
            case 3:
                if (r == 0 || !t.delRow(r)) {
                    Toast.makeText(this, r == 0 ? "The header row stays" : "A table keeps its header and one row",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                r = Math.min(r, t.rows() - 1);
                break;
            case 4:
                if (!t.delCol(c)) {
                    Toast.makeText(this, "A table keeps at least two columns", Toast.LENGTH_SHORT).show();
                    return;
                }
                c = Math.min(c, t.cols() - 1);
                break;
            case 5:
                if (r == 0) {
                    Toast.makeText(this, "Header cells have no tick", Toast.LENGTH_SHORT).show();
                    return;
                }
                t.cycleTick(r, c);
                break;
            case 6: case 7: case 8: case 9: {
                int nr = r + (op == 8 ? -1 : op == 9 ? 1 : 0);
                int nc = c + (op == 6 ? -1 : op == 7 ? 1 : 0);
                boolean ok = nr >= 0 && nc >= 0 && nr < t.rows() && nc < t.cols() && (r == 0) == (nr == 0);
                if (!ok) {
                    Toast.makeText(this, "Nowhere to move it", Toast.LENGTH_SHORT).show();
                    return;
                }
                t.swap(r, c, nr, nc);
                r = nr;
                c = nc;
                break;
            }
            case 10:
                if (r == 0) {
                    Toast.makeText(this, "Header cells are text; put a counter in a row below", Toast.LENGTH_SHORT).show();
                    return;
                }
                dropCell();
                tableCounterDialog(t, r, c);
                return;
            default: {
                int a = e.getSpanStart(t);
                dropCell();
                if (a >= 0) {
                    e.removeSpan(t);
                    e.delete(a, Math.min(e.length(), a + 1));
                    changed();
                }
                return;
            }
        }
        RichText.refresh(e, t);
        changed();
        final int rr = r;
        final int cc = c;
        cellOpen = false;
        body.post(() -> openCell(t, rr, cc));
    }

    // ------------------------------------------------------------ full screen reading

    private static final long READ_AWAKE_MS = 2 * 60 * 1000L;

    /** Hides every bar and the keyboard so the note fills the screen; the X (or Back) brings the editor back. */
    private void enterReading() {
        if (reading) return;
        if (cellOpen) closeCell(true);
        commitNow();
        saveNow();
        reading = true;
        if (paintMode) setPaintMode(false);
        android.view.inputmethod.InputMethodManager im = (android.view.inputmethod.InputMethodManager)
                getSystemService(INPUT_METHOD_SERVICE);
        if (im != null) im.hideSoftInputFromWindow(body.getWindowToken(), 0);
        body.clearFocus();
        body.setFocusable(false);
        body.setCursorVisible(false);
        body.setShowSoftInputOnFocus(false);
        headerView.setVisibility(View.GONE);
        dateRow.setVisibility(View.GONE);
        toolbarView.setVisibility(View.GONE);
        tableBarView.setVisibility(View.GONE);
        fab.setVisibility(View.GONE);
        paintBox.setVisibility(View.GONE);
        String t = title.getText().toString().trim();
        readTitle.setText(t);
        readTitle.setVisibility(t.isEmpty() ? View.GONE : View.VISIBLE);
        readClose.setVisibility(View.VISIBLE);
        body.setPadding(dp(24), dp(t.isEmpty() ? 40 : 12), dp(24), dp(48));
        body.setLineSpacing(0, 1.3f);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        keepAwakeFor();
    }

    private void exitReading() {
        if (!reading) return;
        reading = false;
        body.removeCallbacks(sleepAgain);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(0);
        headerView.setVisibility(View.VISIBLE);
        dateRow.setVisibility(View.VISIBLE);
        toolbarView.setVisibility(View.VISIBLE);
        fab.setVisibility(View.VISIBLE);
        readTitle.setVisibility(View.GONE);
        readClose.setVisibility(View.GONE);
        body.setPadding(dp(24), dp(8), dp(24), dp(24));
        body.setLineSpacing(0, 1.15f);
        body.setFocusable(true);
        body.setFocusableInTouchMode(true);
        body.setCursorVisible(true);
        body.setShowSoftInputOnFocus(true);
        refreshToolbar();
    }

    /** Keeps the screen on while you read, and lets it sleep again after two minutes without a touch. */
    private void keepAwakeFor() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        body.removeCallbacks(sleepAgain);
        body.postDelayed(sleepAgain, READ_AWAKE_MS);
    }

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
        if (reading && ev.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) keepAwakeFor();
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public void onBackPressed() {
        if (reading) {
            exitReading();
            return;
        }
        if (cellOpen) {
            closeCell(true);
            return;
        }
        super.onBackPressed();
    }

    // --------------------------------------------------------- formatting

    private int selStart() {
        return Math.max(0, Math.min(body.getSelectionStart(), body.getSelectionEnd()));
    }

    private int selEnd() {
        return Math.max(0, Math.max(body.getSelectionStart(), body.getSelectionEnd()));
    }

    private static boolean isBrush(int key) {
        return key == RichText.BOLD || key == RichText.ITALIC || key == RichText.UNDER
                || key == RichText.STRIKE || key == RichText.CODE || key == RichText.HILITE
                || key == RichText.FG || key == CLEAR_KEY;
    }

    private ScrollView noteScroll;

    /** Runs a note edit and puts the page back where it was: the editor otherwise jumps to the cursor after every tick or toolbar change. */
    private void keepScroll(Runnable edit) {
        final ScrollView sv = noteScroll;
        if (sv == null) {
            edit.run();
            return;
        }
        final int y = sv.getScrollY();
        edit.run();
        sv.scrollTo(0, y);
        sv.post(() -> sv.scrollTo(0, y));
        sv.postDelayed(() -> sv.scrollTo(0, y), 90);
        sv.postDelayed(() -> sv.scrollTo(0, y), 220);
    }

    private void onTool(final int key) {
        keepScroll(() -> onToolImpl(key));
    }

    private void onToolImpl(int key) {
        commitNow();
        if (paintMode && isBrush(key)) { // finger mode: the button picks the brush instead of applying
            selectBrush(key == brush ? NO_BRUSH : key);
            return;
        }
        if (key >= ACT_BASE && key < ACT_BASE + 100) {
            int n = key - ACT_BASE;
            if (n < 6) {
                if (n == 5) {
                    flushCell();
                    getSharedPreferences("settings", MODE_PRIVATE).edit()
                            .putString("my_template", Markdown.export(body.getText(), "")).apply();
                    Toast.makeText(this, "Saved as your template", Toast.LENGTH_SHORT).show();
                } else {
                    insertTemplate(n);
                }
            } else {
                markdownAction(n - 10);
            }
            return;
        }
        if (key >= CASE_BASE && key < CASE_BASE + 100) {
            applyCase(key - CASE_BASE);
            return;
        }
        if (key >= FONT_BASE && key < FONT_BASE + 100) {
            applyFont(key - FONT_BASE);
            return;
        }
        if (key == COUNTER_KEY) {
            counterDialog(null);
            return;
        }
        if (key == IMPORT_FONT_KEY) {
            startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("*/*").putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"font/ttf", "font/otf",
                            "font/sfnt", "application/x-font-ttf", "application/x-font-otf", "application/font-sfnt",
                            "application/octet-stream"}), REQ_FONT);
            return;
        }
        if (key == DATE_KEY || key == TIME_KEY || key == DATETIME_KEY) {
            dayStamp(key == TIME_KEY ? 1 : key == DATETIME_KEY ? 2 : 0);
            return;
        }
        if (key == DIVIDER_KEY) {
            busy = true;
            int at = RichText.insertRule(body.getText(), selStart());
            busy = false;
            body.setSelection(Math.min(body.getText().length(), at + 2));
            changed();
            refreshToolbar();
            return;
        }
        if (key == KANBAN_KEY) {
            insertTableBlock(new RichText.Tbl(RichText.Tbl.kanban()));
            return;
        }
        Editable e = body.getText();
        int s = selStart();
        int en = selEnd();
        if (key == CLEAR_KEY) {
            if (s == en) { // just a cursor: the next typed text is plain
                for (int t : new int[]{RichText.BOLD, RichText.ITALIC, RichText.UNDER, RichText.STRIKE,
                        RichText.HILITE, RichText.CODE, RichText.FG, RichText.FONT}) pending.put(t, 0);
            } else {
                RichText.clearStyles(e, s, en);
                changed();
            }
            refreshToolbar();
            return;
        }
        if (key == NUM_KEY) {
            busy = true;
            int c = RichText.toggleNumbered(e, s, en);
            busy = false;
            body.setSelection(Math.min(c, e.length()));
            changed();
            refreshToolbar();
            return;
        }
        if (key == IN_KEY || key == OUT_KEY) {
            nest(key == IN_KEY ? 1 : -1);
            return;
        }
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
            insertTableBlock(new RichText.Tbl(RichText.Tbl.blank(3, 3)));
            return;
        }
        if (key == TOGGLE_KEY) {
            busy = true;
            int d = RichText.toggleToggle(e, s);
            busy = false;
            body.setSelection(Math.max(0, Math.min(e.length(), en + d)));
            changed();
            refreshToolbar();
            return;
        }
        if (key == ALIGN_L_KEY || key == ALIGN_C_KEY || key == ALIGN_R_KEY) {
            busy = true;
            RichText.setAlign(e, s, en, key == ALIGN_C_KEY ? 1 : key == ALIGN_R_KEY ? 2 : 0);
            busy = false;
            changed();
            refreshToolbar();
            return;
        }
        if (key == LINK_KEY) {
            pickNoteLink();
            return;
        }
        if (key == RichText.QUOTE || key == RichText.CALLOUT || key == RichText.BLOCK) {
            // whole-paragraph styles: switched on or off for the paragraphs touched, never "for the next text"
            boolean on = !RichText.paragraphsHave(e, key, s, en);
            int keepS = s;
            int keepE = en;
            busy = true;
            boolean added = RichText.setParagraph(e, key, s, en, on);
            busy = false;
            int n2 = e.length();
            if (added) body.setSelection(Math.min(n2, RichText.lineStart(e, Math.min(keepS, n2))));
            else body.setSelection(Math.min(keepS, n2), Math.min(keepE, n2));
            changed();
            refreshToolbar();
            return;
        }
        boolean lineType = key == RichText.HEAD;
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

    /** Moves the touched list lines one nesting level in (+1) or out (-1). */
    private void nest(final int delta) {
        keepScroll(() -> nestImpl(delta));
    }

    private void nestImpl(int delta) {
        Editable e = body.getText();
        int s = selStart();
        int en = selEnd();
        int cursor = body.getSelectionEnd();
        int firstLs = RichText.lineStart(e, s);
        int lastLs = RichText.lineStart(e, en);
        int distFromEnd = RichText.lineEnd(e, lastLs) - cursor;
        busy = true;
        int p = firstLs;
        while (true) {
            int lvl = RichText.indentOf(e, p);
            int want = Math.max(0, Math.min(RichText.MAX_INDENT, lvl + delta));
            if (delta > 0) { // an item nests at most one level under the line above it
                int above = p == 0 ? -1 : RichText.indentOf(e, RichText.lineStart(e, p - 1));
                want = Math.min(want, above + 1);
            }
            if (want != lvl) RichText.setIndent(e, p, want);
            if (p >= lastLs) break;
            int le = RichText.lineEnd(e, p);
            if (le >= e.length()) break;
            p = le + 1;
        }
        RichText.renumber(e, firstLs, -1);
        RichText.refold(e);
        busy = false;
        int newEnd = RichText.lineEnd(e, lastLs);
        if (s == en) {
            body.setSelection(Math.max(firstLs, Math.min(newEnd, newEnd - distFromEnd)));
        } else {
            body.setSelection(firstLs, newEnd);
        }
        changed();
        refreshToolbar();
    }

    private void textColor(final int color) {
        keepScroll(() -> textColorImpl(color));
    }

    private void textColorImpl(int color) {
        commitNow();
        if (paintMode) {
            brushFg = color;
            selectBrush(RichText.FG);
            return;
        }
        Editable e = body.getText();
        int s = selStart();
        int en = selEnd();
        if (s == en) {
            pending.put(RichText.FG, color);
        } else {
            RichText.apply(e, RichText.FG, s, en, color, color != 0);
            changed();
        }
        refreshToolbar();
    }

    // ------------------------------------------------------- finger mode

    private void setPaintMode(boolean on) {
        paintMode = on;
        brush = NO_BRUSH;
        styleFab();
        if (on) {
            closeCell(false);
            android.view.inputmethod.InputMethodManager imm =
                    getSystemService(android.view.inputmethod.InputMethodManager.class);
            imm.hideSoftInputFromWindow(body.getWindowToken(), 0);
            body.setPaintListener(new RichEditText.PaintListener() {
                @Override public void onStart(int offset) {
                    strokeErase = brush != NO_BRUSH && brushActiveAt(offset);
                }
                @Override public void onStroke(int lo, int hi) {
                    if (brush != NO_BRUSH) paintRange(lo, hi, strokeErase);
                }
                @Override public void onEnd() {
                    if (brush != NO_BRUSH) commitNow();
                }
                @Override public void onTap(int offset) {
                    paintWordAt(offset);
                }
            });
        } else {
            body.setPaintListener(null);
        }
        updatePaintHint();
        refreshToolbar();
    }

    private void selectBrush(int key) {
        brush = key;
        updatePaintHint();
        refreshToolbar();
    }

    private String brushName() {
        if (brush == RichText.BOLD) return "Bold";
        if (brush == RichText.ITALIC) return "Italic";
        if (brush == RichText.UNDER) return "Underline";
        if (brush == RichText.STRIKE) return "Strikethrough";
        if (brush == RichText.CODE) return "Code";
        if (brush == RichText.HILITE) return brushHl == 0 ? "No highlight" : "Highlight";
        if (brush == RichText.FG) return brushFg == 0 ? "Default colour" : "Text colour";
        if (brush == CLEAR_KEY) return "Standard";
        return "";
    }

    private void updatePaintHint() {
        if (!paintMode) {
            paintBox.setVisibility(View.GONE);
            helpCard.setVisibility(View.GONE);
            return;
        }
        paintHint.setText(brush == NO_BRUSH ? "Finger mode \u00b7 pick a style below, then swipe over text"
                : brushName() + " \u00b7 swipe over text (swipe styled text again to undo)");
        paintBox.setVisibility(View.VISIBLE);
    }

    /** The (i) button next to the finger-mode hint opens / closes the list of what finger mode can do. */
    private void toggleHelp() {
        if (helpCard.getVisibility() == View.VISIBLE) {
            helpCard.animate().alpha(0f).translationY(dp(8)).setDuration(140)
                    .withEndAction(() -> helpCard.setVisibility(View.GONE)).start();
            return;
        }
        helpCard.setAlpha(0f);
        helpCard.setTranslationY(dp(8));
        helpCard.setVisibility(View.VISIBLE);
        helpCard.animate().alpha(1f).translationY(0).setDuration(160).start();
    }

    private static CharSequence helpText() {
        return "Finger mode: pick a brush below, then swipe over text with your finger.\n\n"
                + "Brushes you can use:\n"
                + "\u2022 Bold, Italic, Underline, Strike\n"
                + "\u2022 Code (inline)\n"
                + "\u2022 Highlight, in any highlight colour\n"
                + "\u2022 Text colour\n"
                + "\u2022 Tx, to wipe styles back to standard\n\n"
                + "Tap a word to style just that word. Swipe over text that already has the style to take it off.\n\n"
                + "Not brushes (they still work as normal buttons): size, headings, lists, quote, callout, code block, "
                + "alignment, tables and fonts. Tap the hand button again to leave finger mode.";
    }

    /** Does the character at this offset already carry the active brush (so the stroke erases)? */
    private boolean brushActiveAt(int off) {
        Editable e = body.getText();
        if (e.length() == 0) return false;
        int pos = Math.max(0, Math.min(off, e.length() - 1));
        if (off >= e.length() || e.charAt(pos) == '\n') pos = Math.max(0, pos - 1);
        if (brush == CLEAR_KEY) return false;
        if (brush == RichText.HILITE) return brushHl == 0 || RichText.colorAt(e, RichText.HILITE, pos) == brushHl;
        if (brush == RichText.FG) return brushFg == 0 || RichText.colorAt(e, RichText.FG, pos) == brushFg;
        return RichText.active(e, brush, pos, pos + 1);
    }

    /** Applies the brush to [lo, hi), line by line, leaving list markers alone. */
    private void paintRange(int lo, int hi, boolean erase) {
        Editable e = body.getText();
        lo = Math.max(0, lo);
        hi = Math.min(e.length(), hi);
        int p = RichText.lineStart(e, lo);
        while (p <= hi && p <= e.length()) {
            int le = RichText.lineEnd(e, p);
            int a = Math.max(lo, p + RichText.markerLen(e, p));
            int b = Math.min(hi, le);
            if (a < b) {
                if (brush == CLEAR_KEY) {
                    RichText.clearStyles(e, a, b);
                } else if (brush == RichText.HILITE) {
                    RichText.apply(e, RichText.HILITE, a, b, brushHl, !erase && brushHl != 0);
                } else if (brush == RichText.FG) {
                    RichText.apply(e, RichText.FG, a, b, brushFg, !erase && brushFg != 0);
                } else {
                    RichText.apply(e, brush, a, b, 0, !erase);
                }
            }
            if (le >= e.length()) break;
            p = le + 1;
        }
        changed();
        body.invalidate();
    }

    /** A tap in finger mode paints the word under the finger. */
    private void paintWordAt(int off) {
        if (brush == NO_BRUSH) {
            Toast.makeText(this, "Pick a style first, then swipe over text", Toast.LENGTH_SHORT).show();
            return;
        }
        Editable e = body.getText();
        int n = e.length();
        int at = Math.max(0, Math.min(off, n));
        if (!(at < n && Character.isLetterOrDigit(e.charAt(at)))) {
            if (at > 0 && Character.isLetterOrDigit(e.charAt(at - 1))) at--;
            else return;
        }
        int a = at;
        int b = at;
        while (a > 0 && Character.isLetterOrDigit(e.charAt(a - 1))) a--;
        while (b < n && Character.isLetterOrDigit(e.charAt(b))) b++;
        boolean erase = brushActiveAt(at);
        paintRange(a, b, erase);
        commitNow();
    }

    private void highlight(final int color) {
        keepScroll(() -> highlightImpl(color));
    }

    private void highlightImpl(int color) {
        commitNow();
        if (paintMode) {
            brushHl = color;
            selectBrush(RichText.HILITE);
            return;
        }
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
        if (key == RichText.QUOTE || key == RichText.CALLOUT || key == RichText.BLOCK) {
            return RichText.paragraphsHave(body.getText(), key, s, en);
        }
        if (s == en && pending.containsKey(key)) return pending.get(key) != 0;
        return RichText.active(body.getText(), key, s, en);
    }

    private int fontSel() {
        int s = selStart();
        int en = selEnd();
        if (s == en && pending.containsKey(RichText.FONT)) return pending.get(RichText.FONT);
        return RichText.fontAt(body.getText(), s, en);
    }

    private void refreshToolbar() {
        if (body == null) return;
        int s = selStart();
        int en = selEnd();
        if (!buttons.isEmpty()) {
            int mk = RichText.prefixAt(body.getText(), RichText.lineStart(body.getText(), s));
            int font = fontSel();
            for (Map.Entry<Integer, Tool> b : buttons.entrySet()) {
                int key = b.getKey();
                boolean on;
                if (key == BULLET_KEY) {
                    on = mk == RichText.P_BULLET;
                } else if (key == CHECK_KEY) {
                    on = RichText.isCheck(mk);
                } else if (key == TOGGLE_KEY) {
                    on = RichText.isToggle(mk);
                } else if (key == ALIGN_L_KEY || key == ALIGN_C_KEY || key == ALIGN_R_KEY) {
                    int al = RichText.alignAt(body.getText(), s);
                    on = al == (key == ALIGN_C_KEY ? 1 : key == ALIGN_R_KEY ? 2 : 0);
                } else if (key >= FONT_BASE && key < FONT_BASE + 100) {
                    on = font == key - FONT_BASE;
                } else if (key >= CASE_BASE || key < 0 && key != SIZE_KEY && key != NUM_KEY
                        && key != BULLET_KEY && key != CHECK_KEY) {
                    on = false;
                } else {
                    on = isOn(key, s, en);
                }
                if (key == SIZE_KEY) on = RichText.sizeAt(body.getText(), s, en) != 0;
                if (key == NUM_KEY) {
                    on = RichText.numLen(body.getText(), RichText.lineStart(body.getText(), s)) > 0;
                }
                if (key == TABLE_KEY) on = false;
                style(b.getValue(), on, paintMode && key == brush);
            }
        }
        updateSwatches();
    }

    private void style(Tool t, boolean on, boolean brushed) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(12));
        g.setColor(brushed ? Pal.accent : on ? Pal.sel : 0);
        if (paintMode && !brushed) g.setStroke(dp(1), Pal.chip);
        t.box.setBackground(g);
        int col = brushed ? Pal.onAccent : on ? Pal.onSel : Pal.ink;
        t.glyph.setTextColor(col);
        t.label.setTextColor(brushed || on ? col : Pal.muted);
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
            // Markdown-style shortcuts: "# ", "- ", "[] ", "> " at the start of a line
            if (insCount == 1 && insBefore == 0 && insStart < e.length() && e.charAt(insStart) == ' ') {
                moved = mdShortcut(e);
            }
            // Enter = the last inserted character is a newline (some keyboards commit "word\n" together).
            int nl = insStart + insCount - 1;
            boolean enter = !moved && insCount >= 1 && nl < e.length() && e.charAt(nl) == '\n'
                    && e.subSequence(insStart, nl).toString().indexOf('\n') < 0;
            boolean kept = true; // false when the Enter's line break was taken back / replaced
            if (enter) {
                int ps = RichText.lineStart(e, nl); // the line that Enter just ended
                boolean restEmpty = nl + 1 >= e.length() || e.charAt(nl + 1) == '\n';
                int kind = RichText.prefixAt(e, ps);
                int numLen = kind == RichText.P_NONE ? RichText.numLen(e, ps) : 0;
                int lvl = RichText.indentOf(e, ps);
                boolean emptyItem = (kind != RichText.P_NONE && nl - ps == 2)
                        || (numLen > 0 && nl - ps == numLen);
                if (RichText.leaveStyle(e, ps, nl)) {
                    // Enter on an empty quote / callout / code-block line: the line becomes plain, the block ends
                    expectCursor = Math.min(e.length(), ps);
                    body.setSelection(expectCursor);
                    moved = true;
                    kept = false;
                } else if (kind == RichText.P_NONE && numLen == 0 && nl - ps == 3
                        && e.subSequence(ps, nl).toString().equals("---")) {
                    // --- and Enter = a divider line
                    e.replace(ps, nl, String.valueOf(RichText.RULE_CHAR));
                    e.setSpan(new RichText.Rule(), ps, ps + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    expectCursor = Math.min(e.length(), ps + 2);
                    body.setSelection(expectCursor);
                    moved = true;
                    kept = false;
                } else if (emptyItem && restEmpty && lvl > 0) {
                    // Enter on an empty nested item steps it back out one level
                    e.delete(nl, nl + 1);
                    RichText.setIndent(e, ps, lvl - 1);
                    if (numLen > 0) RichText.renumber(e, ps, -1);
                    expectCursor = Math.min(e.length(), RichText.lineEnd(e, ps));
                    body.setSelection(expectCursor);
                    moved = true;
                    kept = false;
                } else if (emptyItem && restEmpty) { // Enter on an empty item ends the list
                    e.delete(ps, nl + 1);
                    expectCursor = ps;
                    body.setSelection(Math.min(expectCursor, e.length()));
                    moved = true;
                    kept = false;
                } else if (RichText.isToggle(kind)) {
                    // Enter in a toggle opens it and starts a line inside it
                    if (kind == RichText.P_TOGGLE_CLOSED) e.replace(ps, ps + 1, "\u25BE");
                    RichText.setIndent(e, ps, lvl);
                    RichText.setIndent(e, nl + 1, Math.min(RichText.MAX_INDENT, lvl + 1));
                    expectCursor = nl + 1;
                    body.setSelection(Math.min(expectCursor, e.length()));
                    moved = true;
                } else if (kind != RichText.P_NONE) {
                    // New line continues the list; a to-do item continues as an unticked one.
                    String next = kind == RichText.P_BULLET ? RichText.BULLET : RichText.CHECK_OFF;
                    e.insert(nl + 1, next);
                    RichText.apply(e, RichText.STRIKE, nl + 1, nl + 1 + next.length(), 0, false);
                    RichText.setIndent(e, ps, lvl);
                    RichText.setIndent(e, nl + 1, lvl);
                    expectCursor = nl + 1 + next.length();
                    body.setSelection(Math.min(expectCursor, e.length()));
                    moved = true;
                } else if (numLen > 0) {
                    // 1. 2. 3. : the next line gets the next number (and later items follow)
                    String next = (RichText.numValue(e, ps) + 1) + ". ";
                    e.insert(nl + 1, next);
                    RichText.apply(e, RichText.STRIKE, nl + 1, nl + 1 + next.length(), 0, false);
                    RichText.setIndent(e, ps, lvl);
                    RichText.setIndent(e, nl + 1, lvl);
                    RichText.renumber(e, ps, nl + 1);
                    expectCursor = nl + 1 + next.length();
                    body.setSelection(Math.min(expectCursor, e.length()));
                    moved = true;
                } else if (kind == RichText.P_NONE && numLen == 0 && nl == ps && restEmpty && lvl > 0) {
                    // Enter on an empty indented line steps it back out
                    e.delete(nl, nl + 1);
                    RichText.setIndent(e, ps, lvl - 1);
                    expectCursor = Math.min(e.length(), RichText.lineEnd(e, ps));
                    body.setSelection(expectCursor);
                    moved = true;
                    kept = false;
                } else if (lvl > 0) {
                    // a plain indented line: the new line starts at the same level
                    RichText.setIndent(e, ps, lvl);
                    RichText.setIndent(e, nl + 1, lvl);
                }
                if (kept) {
                    if (restEmpty) endLineStyles(e, ps, nl);
                    // the new line keeps a quote / callout / code block / alignment of the line above
                    if (RichText.inheritStyles(e, ps, nl)) {
                        int q = Math.min(e.length(), nl + 1);
                        expectCursor = q;
                        body.setSelection(q);
                        moved = true;
                    }
                }
            }
            // Backspace over the space of "• " / "☐ " removes the whole marker.
            if (!moved && insCount == 0 && insBefore == 1) {
                int ls = RichText.lineStart(e, Math.min(insStart, e.length()));
                if (insStart == ls + 1 && ls < e.length()) {
                    char ch = e.charAt(ls);
                    if (ch == '\u2022' || ch == '\u2610' || ch == '\u2611' || ch == '\u25B8' || ch == '\u25BE') {
                        e.delete(ls, ls + 1);
                        expectCursor = ls;
                        body.setSelection(Math.min(ls, e.length()));
                        moved = true;
                    }
                }
            }
            if (!moved) expectCursor = insStart + insCount;
            // One quote / callout / code / indent span per paragraph, whatever the typing did to them.
            RichText.normalize(e);
            if (enter || insBefore > 0) RichText.refold(e); // lines may have joined or split under a toggle
        } finally {
            busy = false;
            typing = false;
            insCount = 0;
        }
        refreshToolbar();
    }

    /** "# ", "- ", "* ", "[] ", "> " typed at the start of a line become a heading, bullet, to-do or quote. */
    private boolean mdShortcut(Editable e) {
        int ls = RichText.lineStart(e, insStart);
        int upto = insStart + 1;
        String pre = e.subSequence(ls, upto).toString();
        int lvl = RichText.indentOf(e, ls);
        if (pre.equals("- ") || pre.equals("* ")) {
            e.replace(ls, upto, RichText.BULLET);
            if (lvl > 0) RichText.setIndent(e, ls, lvl);
            expectCursor = ls + 2;
        } else if (pre.equals("[] ") || pre.equals("[ ] ")) {
            e.replace(ls, upto, RichText.CHECK_OFF);
            if (lvl > 0) RichText.setIndent(e, ls, lvl);
            expectCursor = ls + 2;
        } else if (pre.matches("#{1,3} ") || pre.equals("> ")) {
            e.delete(ls, upto);
            if (pre.startsWith("#")) {
                int le = RichText.lineEnd(e, ls);
                if (le > ls) RichText.apply(e, RichText.HEAD, ls, le, 0, true);
                else pending.put(RichText.HEAD, 1);
            } else {
                RichText.setParagraph(e, RichText.QUOTE, ls, ls, true);
            }
            expectCursor = ls;
        } else {
            return false;
        }
        body.setSelection(Math.min(expectCursor, e.length()));
        return true;
    }

    /**
     * After Enter on a line, the next line should not inherit a heading (quote, callout and code block
     * are carried on, or left, by RichText.inheritStyles / leaveStyle).
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
    }

    // ------------------------------------------------------------ note links

    private static String noteLabel(Note n) {
        String t = n.title == null ? "" : n.title.trim();
        if (t.isEmpty()) {
            String body = RichText.tidy(n.plain == null ? "" : n.plain).trim();
            int nl = body.indexOf('\n');
            t = nl >= 0 ? body.substring(0, nl) : body;
        }
        if (t.isEmpty()) t = "Untitled note";
        return t.length() > 60 ? t.substring(0, 60) + "…" : t;
    }

    /** Insert > Note link: pick another note; its title (or the selected text) becomes a tappable link to it. */
    private void pickNoteLink() {
        flushCell();
        commitNow();
        final java.util.ArrayList<Note> list = new java.util.ArrayList<>();
        for (Note n : db.notes("", 0, -1)) if (n.id != noteId) list.add(n);
        if (list.isEmpty()) {
            Toast.makeText(this, "No other notes to link to yet", Toast.LENGTH_SHORT).show();
            return;
        }
        final int selS = selStart();
        final int selE = selEnd();
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(4), dp(16), dp(4));
        final EditText q = new EditText(this);
        q.setHint("Search notes");
        q.setHintTextColor(Pal.muted);
        q.setTextColor(Pal.ink);
        q.setSingleLine(true);
        box.addView(q, new LinearLayout.LayoutParams(-1, -2));
        final LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        ScrollView sv = new ScrollView(this);
        sv.addView(rows, new ScrollView.LayoutParams(-1, -2));
        box.addView(sv, new LinearLayout.LayoutParams(-1, dp(300)));
        final AlertDialog[] dlg = new AlertDialog[1];
        final Runnable fill = () -> {
            rows.removeAllViews();
            String f = q.getText().toString().trim().toLowerCase(java.util.Locale.ROOT);
            int shown = 0;
            for (final Note n : list) {
                String label = noteLabel(n);
                if (!f.isEmpty() && !label.toLowerCase(java.util.Locale.ROOT).contains(f)
                        && !(n.plain != null && n.plain.toLowerCase(java.util.Locale.ROOT).contains(f))) continue;
                TextView row = new TextView(this);
                row.setText(label);
                row.setTextSize(16);
                row.setTextColor(Pal.ink);
                row.setSingleLine(true);
                row.setEllipsize(android.text.TextUtils.TruncateAt.END);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setMinHeight(dp(48));
                row.setPadding(dp(8), 0, dp(8), 0);
                row.setOnClickListener(v -> {
                    if (dlg[0] != null) dlg[0].dismiss();
                    insertLink(n, selS, selE);
                });
                rows.addView(row, new LinearLayout.LayoutParams(-1, -2));
                if (++shown >= 80) break;
            }
            if (shown == 0) {
                TextView none = new TextView(this);
                none.setText("No note matches");
                none.setTextColor(Pal.muted);
                none.setPadding(dp(8), dp(12), dp(8), dp(12));
                rows.addView(none);
            }
        };
        q.addTextChangedListener(new SimpleWatcher() {
            @Override
            public void afterTextChanged(Editable e) {
                fill.run();
            }
        });
        fill.run();
        dlg[0] = new Dlg.Builder(this).setTitle("Link to a note").setView(box)
                .setNegativeButton("Cancel", null).show();
    }

    private void insertLink(Note n, int s, int en) {
        Editable e = body.getText();
        s = Math.max(0, Math.min(s, e.length()));
        en = Math.max(s, Math.min(en, e.length()));
        busy = true;
        int a;
        int b;
        try {
            if (en > s) { // the selected text becomes the link
                a = s;
                b = en;
            } else {
                String label = noteLabel(n);
                e.insert(s, label);
                a = s;
                b = s + label.length();
            }
            e.setSpan(new RichText.NoteLink((int) n.id), a, b, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            e.insert(b, " "); // typing carries on after the link, not inside it
            RichText.normalize(e);
        } finally {
            busy = false;
            typing = false;
            insCount = 0;
        }
        body.requestFocus();
        body.setSelection(Math.min(e.length(), b + 1));
        changed();
        refreshToolbar();
    }

    /** A tap on a note link: save this note, then open the linked one. */
    private void openNoteLink(int id) {
        flushCell();
        commitNow();
        saveNow();
        if (db.deletedAt(id) > 0) {
            Toast.makeText(this, "That note is in the Bin", Toast.LENGTH_SHORT).show();
            return;
        }
        if (db.note(id) == null) {
            Toast.makeText(this, "That note no longer exists", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent i = new Intent(this, NoteEditorActivity.class);
        i.putExtra(EXTRA_ID, (long) id);
        startActivity(i);
    }

    // ------------------------------------------------------------ saving

    /** A body change happened: mark it for saving and take an undo step once typing pauses. */
    private void changed() {
        dirty = true;
        if (restoring || body == null) return;
        body.removeCallbacks(commitTask);
        body.postDelayed(commitTask, 700);
        body.removeCallbacks(autosaveTask);
        body.postDelayed(autosaveTask, 2500);
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
        flushCell();
        commitNow();
        if (histPos > 0) restore(histPos - 1);
    }

    private void redo() {
        flushCell();
        commitNow();
        if (histPos < hist.size() - 1) restore(histPos + 1);
    }

    private void restore(int pos) {
        dropCell();
        restoring = true;
        busy = true;
        try {
            Editable e = body.getText();
            e.clear();
            RichText.fromJson(hist.get(pos), e);
            RichText.refold(e);
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
        saveNow(false);
    }

    private static final int REQ_HISTORY = 41;

    /** Opens this note's earlier versions; whatever is typed is saved first so it is in the list too. */
    private void openHistory() {
        commitNow();
        saveNow();
        if (noteId <= 0) {
            Toast.makeText(this, "Nothing saved yet", Toast.LENGTH_SHORT).show();
            return;
        }
        startActivityForResult(new Intent(this, NoteHistoryActivity.class)
                .putExtra(NoteHistoryActivity.EXTRA_NOTE, noteId), REQ_HISTORY);
    }

    private void saveNow(boolean auto) {
        flushCell();
        if (body != null) {
            body.removeCallbacks(autosaveTask);
            if (!auto) body.removeCallbacks(commitTask);
        }
        if (deleted || !dirty) return;
        String t = title.getText().toString().trim();
        Editable b = body.getText();
        boolean empty = t.isEmpty() && b.toString().trim().isEmpty();
        if (empty && auto) return; // a half-way edit (everything selected and deleted) is never auto-saved as "deleted"
        if (empty) {
            if (noteId > 0) {
                db.discardNote(noteId); // keeps what the note held in its history first
                noteId = 0;
            }
        } else {
            boolean fresh = noteId <= 0;
            noteId = db.saveNote(noteId, t, RichText.toJson(b), RichText.tablesPlain(b));
            if (fresh && noteId > 0) {
                long inFolder = getIntent().getLongExtra(EXTRA_FOLDER, 0);
                if (inFolder > 0) db.setNoteFolder(noteId, inFolder);
            }
            getIntent().putExtra(EXTRA_ID, noteId);
            dateLabel.setText("Edited " + Reminders.when(this, System.currentTimeMillis()));
        }
        dirty = false;
    }

    private void confirmDelete() {
        new Dlg.Builder(this)
                .setTitle("Move this note to the Bin?")
                .setMessage("It stays in the Bin for " + TaskDb.BIN_DAYS + " days, so you can bring it back.")
                .setPositiveButton("Move to Bin", (d, w) -> {
                    if (noteId > 0) db.trashNote(noteId);
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
