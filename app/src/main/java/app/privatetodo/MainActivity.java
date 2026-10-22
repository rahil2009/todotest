package app.privatetodo;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.NotificationManager;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQ_NOTIF = 11;

    private TaskDb db;
    private final List<Task> tasks = new ArrayList<>();
    // what the list actually shows: tasks, plus section headers and hints for the layout under test
    private final List<Object> items = new ArrayList<>();
    private LinearLayout extrasBox; // switch / routine strip, above the list
    private LinearLayout dockView;  // routine dock, above the add bar
    private final boolean[] collapsed = new boolean[2];

    private static final class Header {
        final String text;
        final int count;
        final int key; // 0 one-off, 1 repeating, 2 plain label
        Header(String text, int count, int key) {
            this.text = text;
            this.count = count;
            this.key = key;
        }
    }

    private static final class Hint {
        final CharSequence text;
        final Runnable click;
        Hint(CharSequence text, Runnable click) {
            this.text = text;
            this.click = click;
        }
    }
    private int filter = TaskDb.ACTIVE;
    private float density;

    private TextView subtitle;
    private View clearDone;
    private IconView bell;
    private final TextView[] chips = new TextView[8];
    // undo pill: "Task deleted  [Undo]" with a bar that drains over 4 seconds
    private UndoPill undoPill;
    private Runnable undoAction;
    private final Runnable undoHide = () -> hideUndo(true);
    private LinearLayout emptyView;
    private TextView emptyTitle;
    private TextView emptyBody;
    private TaskAdapter adapter;
    private ListView listView;
    private AlertDialog setupDialog;
    private int builtDark;
    private java.util.function.Consumer<String> soundCb;
    private EditText pillInput;
    private IconView pillMore;
    private IconView pillSend;
    private IconView searchBtn;
    private boolean searching;
    private String query = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Pal.init(this);
        builtDark = Pal.themeRes();
        setTheme(Pal.themeRes());
        super.onCreate(savedInstanceState);
        density = getResources().getDisplayMetrics().density;
        db = new TaskDb(this);
        Reminders.ensureChannels(this);
        setContentView(buildUi());
        Fonts.apply(getWindow().getDecorView());
        refresh();
        handleWidgetIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleWidgetIntent(intent);
    }

    /** The widgets' "+" opens the app with the keyboard up, ready to type a task. */
    private void handleWidgetIntent(Intent intent) {
        if (intent == null || !intent.getBooleanExtra(TaskWidget.EXTRA_ADD, false)) return;
        intent.removeExtra(TaskWidget.EXTRA_ADD);
        pillInput.postDelayed(() -> {
            pillInput.requestFocus();
            getSystemService(InputMethodManager.class).showSoftInput(pillInput, 0);
        }, 250);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Pal.init(this);
        if (Pal.themeRes() != builtDark) { // theme changed in Settings
            recreate();
            return;
        }
        Reminders.normalise(this); // every series sits on its current round
        // Picks up Done / Snooze taken from a notification or the alarm screen.
        refresh();
    }

    @Override
    protected void onPause() {
        Sound.stopPreview(); // ends any sound preview
        super.onPause();
    }

    @Override
    protected void onActivityResult(int req, int result, Intent data) {
        super.onActivityResult(req, result, data);
        String picked = SoundPicker.result(this, req, result, data);
        if (picked != null && soundCb != null) soundCb.accept(picked);
    }

    @Override
    protected void onDestroy() {
        db.close();
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        updateBell();
    }

    // ---------------------------------------------------------------- UI

    private View buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Pal.bg);

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        root.addView(column, new FrameLayout.LayoutParams(-1, -1));

        // Header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.TOP);
        header.setPadding(dp(24), dp(16), dp(12), 0);

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(text("Tasks", 34, Pal.ink, true));
        subtitle = text("", 15, Pal.muted, false);
        subtitle.setPadding(0, dp(2), 0, 0);
        titles.addView(subtitle);
        header.addView(titles, new LinearLayout.LayoutParams(0, -2, 1f));

        IconView trash = new IconView(this, IconView.TRASH);
        trash.setTint(Pal.ink);
        trash.setContentDescription("Clear completed tasks");
        trash.setOnClickListener(v -> confirmClearDone());
        clearDone = trash;
        header.addView(trash, new LinearLayout.LayoutParams(dp(41), dp(48)));

        IconView habit = new IconView(this, IconView.HABIT);
        habit.setContentDescription("Habits");
        habit.setOnClickListener(v -> {
            Hap.tick(v);
            startActivity(new Intent(this, HabitActivity.class));
        });
        header.addView(habit, new LinearLayout.LayoutParams(dp(41), dp(48)));

        IconView pomodoro = new IconView(this, IconView.POMODORO);
        pomodoro.setContentDescription("Pomodoro focus timer");
        pomodoro.setOnClickListener(v -> {
            Hap.tick(v);
            startActivity(new Intent(this, PomodoroActivity.class));
        });
        header.addView(pomodoro, new LinearLayout.LayoutParams(dp(41), dp(48)));

        IconView plan = new IconView(this, IconView.PLAN);
        plan.setContentDescription("Plan");
        plan.setOnClickListener(v -> {
            Hap.tick(v);
            startActivity(new Intent(this, PlanActivity.class));
        });
        header.addView(plan, new LinearLayout.LayoutParams(dp(41), dp(48)));

        IconView notebook = new IconView(this, IconView.NOTE);
        notebook.setContentDescription("Notebook");
        notebook.setOnClickListener(v -> startActivity(new Intent(this, NotesActivity.class)));
        header.addView(notebook, new LinearLayout.LayoutParams(dp(41), dp(48)));

        bell = new IconView(this, IconView.BELL);
        bell.setContentDescription("Reminder setup");
        bell.setOnClickListener(v -> showSetup());
        header.addView(bell, new LinearLayout.LayoutParams(dp(48), dp(48)));

        column.addView(header, new LinearLayout.LayoutParams(-1, -2));

        // Filter chips: status first, then date and priority views (scrolls sideways when it must)
        LinearLayout chipRow = new LinearLayout(this);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        chipRow.setPadding(dp(24), dp(16), dp(24), dp(12));
        // shown order; chips[] is indexed by the filter constant
        final int[] order = {TaskDb.ACTIVE, TaskDb.REPEAT, TaskDb.DONE, TaskDb.ALL, TaskDb.TODAY, TaskDb.TOMORROW,
                TaskDb.UPCOMING, TaskDb.PRIORITY};
        final String[] names = {"Active", "Repeat", "Done", "All", "Today", "Tomorrow", "Upcoming", "Priority"};
        for (int i = 0; i < names.length; i++) {
            final int f = order[i];
            TextView chip = text(names[i], 15, Pal.muted, true);
            chip.setGravity(Gravity.CENTER);
            chip.setMinHeight(dp(40));
            chip.setPadding(dp(18), 0, dp(18), 0);
            chip.setOnClickListener(v -> {
                Hap.tick(v);
                filter = f;
                refresh();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = dp(8);
            chipRow.addView(chip, lp);
            chips[f] = chip;
        }
        HorizontalScrollView chipScroll = new HorizontalScrollView(this);
        chipScroll.setHorizontalScrollBarEnabled(false);
        chipScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        chipScroll.addView(chipRow);
        column.addView(chipScroll, new LinearLayout.LayoutParams(-1, -2));

        extrasBox = new LinearLayout(this);
        extrasBox.setOrientation(LinearLayout.VERTICAL);
        extrasBox.setVisibility(View.GONE);
        column.addView(extrasBox, new LinearLayout.LayoutParams(-1, -2));

        // List + empty state
        FrameLayout body = new FrameLayout(this);
        listView = new ListView(this);
        listView.setDivider(null);
        listView.setDividerHeight(0);
        listView.setSelector(new ColorDrawable(Color.TRANSPARENT));
        listView.setClipToPadding(false);
        listView.setPadding(0, 0, 0, dp(96));
        listView.setVerticalScrollBarEnabled(false);

        LinearLayout footer = lockLine("Offline \u00B7 stored only on this device");
        footer.setPadding(0, dp(16), 0, dp(8));
        listView.addFooterView(footer, null, false);

        adapter = new TaskAdapter();
        listView.setAdapter(adapter);
        body.addView(listView, new FrameLayout.LayoutParams(-1, -1));

        emptyView = new LinearLayout(this);
        emptyView.setOrientation(LinearLayout.VERTICAL);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setPadding(dp(40), 0, dp(40), dp(96));
        emptyView.addView(new IconView(this, IconView.BADGE),
                new LinearLayout.LayoutParams(dp(72), dp(72)));
        emptyTitle = text("", 24, Pal.ink, true);
        emptyTitle.setGravity(Gravity.CENTER);
        emptyTitle.setPadding(0, dp(20), 0, 0);
        emptyView.addView(emptyTitle);
        emptyBody = text("", 16, Pal.muted, false);
        emptyBody.setGravity(Gravity.CENTER);
        emptyBody.setPadding(0, dp(8), 0, 0);
        emptyView.addView(emptyBody);
        LinearLayout privacy = lockLine("No account \u00B7 no tracking \u00B7 no internet permission");
        privacy.setPadding(0, dp(24), 0, 0);
        emptyView.addView(privacy);
        body.addView(emptyView, new FrameLayout.LayoutParams(-1, -1));

        column.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));

        // Bottom bar: settings (left) - type-a-task pill (centre) - search (right)
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(16), dp(24), dp(16), dp(16));
        // fade the list out behind the bar in the page colour, so cards never show between the buttons
        bar.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Pal.bg & 0x00FFFFFF, Pal.bg, Pal.bg}));

        IconView gear = new IconView(this, IconView.GEAR);
        gear.setContentDescription("Settings");
        floatingCircle(gear);
        gear.setOnClickListener(v -> {
            Hap.tick(v);
            startActivity(new Intent(this, SettingsActivity.class));
        });
        bar.addView(gear, new LinearLayout.LayoutParams(dp(52), dp(52)));

        LinearLayout pill = new LinearLayout(this);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(dp(20), 0, dp(4), 0);
        GradientDrawable pillBg = new GradientDrawable();
        pillBg.setColor(Pal.raised);
        pillBg.setCornerRadius(dp(26));
        pill.setBackground(pillBg);
        pill.setElevation(0);

        pillInput = new EditText(this);
        pillInput.setBackground(null);
        pillInput.setHint("Add a task\u2026");
        pillInput.setHintTextColor(Pal.muted);
        pillInput.setTextColor(Pal.ink);
        pillInput.setTextSize(16);
        pillInput.setPadding(0, 0, 0, 0);
        pillInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        pillInput.setMaxLines(1);
        pillInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        pillInput.setOnEditorActionListener((tv, action, ev) -> {
            if (searching) {
                getSystemService(InputMethodManager.class)
                        .hideSoftInputFromWindow(tv.getWindowToken(), 0);
            } else {
                addFromPill();
            }
            return true;
        });
        pillInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence c, int a, int b, int n) {
            }

            @Override
            public void onTextChanged(CharSequence c, int a, int b, int n) {
            }

            @Override
            public void afterTextChanged(Editable e) {
                if (searching) {
                    query = e.toString().trim().toLowerCase(Locale.ROOT);
                    refresh();
                }
                updatePill();
            }
        });
        pill.addView(pillInput, new LinearLayout.LayoutParams(0, dp(52), 1f));

        // Opens the full editor (reminder, subtasks, repeat) with what was typed.
        pillMore = new IconView(this, IconView.CLOCK);
        pillMore.setContentDescription("Add with reminder, subtasks or repeat");
        pillMore.setOnClickListener(v -> {
            String typed = pillInput.getText().toString().trim();
            pillInput.setText("");
            editDialog(null, typed);
        });
        pill.addView(pillMore, new LinearLayout.LayoutParams(dp(44), dp(52)));

        pillSend = new IconView(this, IconView.SEND);
        pillSend.setContentDescription("Add task");
        pillSend.setOnClickListener(v -> addFromPill());
        pillSend.setVisibility(View.GONE);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(dp(44), dp(52));
        slp.rightMargin = dp(2);
        pill.addView(pillSend, slp);

        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(0, dp(52), 1f);
        plp.leftMargin = dp(10);
        plp.rightMargin = dp(10);
        bar.addView(pill, plp);

        searchBtn = new IconView(this, IconView.SEARCH);
        searchBtn.setContentDescription("Search tasks");
        floatingCircle(searchBtn);
        searchBtn.setOnClickListener(v -> {
            Hap.tick(v);
            setSearching(!searching);
        });
        bar.addView(searchBtn, new LinearLayout.LayoutParams(dp(52), dp(52)));

        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(-1, -2);
        blp.gravity = Gravity.BOTTOM;
        root.addView(bar, blp);

        dockView = new LinearLayout(this);
        dockView.setOrientation(LinearLayout.HORIZONTAL);
        dockView.setGravity(Gravity.CENTER_VERTICAL);
        dockView.setPadding(dp(16), 0, dp(16), 0);
        GradientDrawable dockBg = new GradientDrawable();
        dockBg.setColor(Pal.sel);
        dockBg.setCornerRadius(dp(22));
        dockView.setBackground(dockBg);
        dockView.setVisibility(View.GONE);
        FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(-1, dp(64));
        dlp.gravity = Gravity.BOTTOM;
        dlp.leftMargin = dp(16);
        dlp.rightMargin = dp(16);
        dlp.bottomMargin = dp(84);
        root.addView(dockView, dlp);

        // Undo pill, floating just above the bar; the whole pill drains like the volume slider
        undoPill = new UndoPill(this);
        undoPill.setVisibility(View.GONE);
        undoPill.setOnClickListener(v -> {
            Runnable r = undoAction;
            hideUndo(false);
            if (r != null) {
                Hap.confirm(v);
                r.run();
            }
        });
        FrameLayout.LayoutParams ulp = new FrameLayout.LayoutParams(-2, -2);
        ulp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        ulp.bottomMargin = dp(92);
        root.addView(undoPill, ulp);
        return root;
    }

    /** Small padlock + muted text, centred. */
    private LinearLayout lockLine(String s) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER);
        IconView lock = new IconView(this, IconView.LOCK);
        lock.setClickable(false);
        l.addView(lock, new LinearLayout.LayoutParams(dp(20), dp(20)));
        TextView t = text(s, 13, Pal.muted, false);
        t.setPadding(dp(4), 0, 0, 0);
        l.addView(t);
        return l;
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

    private void floatingCircle(View v) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(Pal.raised);
        v.setBackground(g);
        v.setElevation(0);
        v.setStateListAnimator(null);
    }

    /** Adds a plain task straight from the bottom pill (no reminder; use the clock for that). */
    private void addFromPill() {
        String s = pillInput.getText().toString().trim();
        if (s.isEmpty()) return;
        SmartDate.Result sd = SmartDate.parse(this, s, System.currentTimeMillis());
        if (sd.found) { // "buy milk @tomorrow 7pm": the date is applied and removed from the title
            boolean ring = Prefs.smartAlarm(this);
            long id = db.add(sd.title, sd.due, sd.repeat, sd.unit, ring);
            db.setAlarmMode(id, ring, false);
            Reminders.schedule(this, db.get(id));
            ensureReminderPermissions(ring);
            Toast.makeText(this, "Set for " + Reminders.when(this, sd.due), Toast.LENGTH_SHORT).show();
        } else {
            db.add(s, 0, 0, 2, false);
        }
        Hap.confirm(pillInput);
        if (filter == TaskDb.DONE || filter >= TaskDb.TODAY) filter = TaskDb.ACTIVE;
        pillInput.setText("");
        refresh();
    }

    private void updatePill() {
        boolean has = pillInput.length() > 0;
        pillSend.setVisibility(!searching && has ? View.VISIBLE : View.GONE);
        pillMore.setVisibility(searching ? View.GONE : View.VISIBLE);
    }

    /** The search button turns the pill into a live search box; tap it again (X) to go back. */
    private void setSearching(boolean on) {
        searching = on;
        query = "";
        pillInput.setText("");
        pillInput.setHint(on ? "Search tasks\u2026" : "Add a task\u2026");
        searchBtn.setOn(on);
        searchBtn.setContentDescription(on ? "Close search" : "Search tasks");
        updatePill();
        InputMethodManager imm = getSystemService(InputMethodManager.class);
        if (on) {
            pillInput.requestFocus();
            imm.showSoftInput(pillInput, 0);
        } else {
            imm.hideSoftInputFromWindow(pillInput.getWindowToken(), 0);
        }
        refresh();
    }

    private boolean matches(Task t) {
        if (query.isEmpty() || t.title.toLowerCase(Locale.ROOT).contains(query)) return true;
        for (Sub x : db.subs(t.id)) {
            if (x.title.toLowerCase(Locale.ROOT).contains(query)) return true;
        }
        return false;
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    // -------------------------------------------------------------- data

    private void refresh() {
        tasks.clear();
        for (Task t : db.list(filter)) {
            if (!searching || matches(t)) tasks.add(t);
        }
        buildItems();
        adapter.notifyDataSetChanged();

        int left = db.list(TaskDb.ACTIVE).size();
        int done = db.count(true);
        subtitle.setText(left + " left · " + done + " done");
        clearDone.setVisibility(done > 0 ? View.VISIBLE : View.INVISIBLE);

        for (int i = 0; i < chips.length; i++) {
            styleChip(chips[i], i == filter, Pal.card);
        }

        boolean empty = tasks.isEmpty();
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        listView.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (searching && !query.isEmpty()) {
            emptyTitle.setText("No matches");
            emptyBody.setText("Nothing found for \u201C" + query + "\u201D.");
        } else if (filter == TaskDb.ACTIVE) {
            emptyTitle.setText("All clear");
            emptyBody.setText("Nothing left to do. Type in the bar below to add a task.");
        } else if (filter == TaskDb.REPEAT) {
            emptyTitle.setText("Nothing waiting");
            emptyBody.setText("Repeating tasks you tick off today wait here, and return to Active at midnight.");
        } else if (filter == TaskDb.DONE) {
            emptyTitle.setText("Nothing done yet");
            emptyBody.setText("Finished tasks show up here.");
        } else if (filter == TaskDb.TODAY) {
            emptyTitle.setText("Nothing for today");
            emptyBody.setText("Tasks due today, and anything overdue, show up here.");
        } else if (filter == TaskDb.TOMORROW) {
            emptyTitle.setText("Nothing for tomorrow");
            emptyBody.setText("Tasks with a reminder tomorrow show up here.");
        } else if (filter == TaskDb.UPCOMING) {
            emptyTitle.setText("Nothing coming up");
            emptyBody.setText("Tasks with a reminder after tomorrow show up here.");
        } else if (filter == TaskDb.PRIORITY) {
            emptyTitle.setText("No priority tasks");
            emptyBody.setText("Tasks you mark Low, Medium or High priority show up here.");
        } else {
            emptyTitle.setText("No tasks yet");
            emptyBody.setText("Type in the bar below to add your first one.");
        }
        updateBell();
    }

    private void styleChip(TextView t, boolean on, int offColor) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(20));
        g.setColor(on ? Pal.sel : offColor);
        t.setBackground(g);
        t.setTextColor(on ? Pal.onSel : Pal.muted);
    }

    /** Low = blue, medium = amber, high = red. */
    static int priorityColor(int level) {
        return level == 1 ? 0xFF3F8FE0 : level == 2 ? 0xFFF2A93B : 0xFFE5534B;
    }

    /** Small uppercase section heading, e.g. WHEN / REPEAT / PRIORITY. */
    private TextView sectionLabel(String s) {
        TextView t = text(s.toUpperCase(java.util.Locale.ROOT), 12, Pal.muted, true);
        t.setLetterSpacing(0.08f);
        return t;
    }

    /** Rounded track holding a row of equal segments; the selected one is drawn as a pill. */
    private LinearLayout segTrack(TextView[] segs) {
        return segTrack(segs, Pal.chip);
    }

    private LinearLayout segTrack(TextView[] segs, int trackColor) {
        LinearLayout track = new LinearLayout(this);
        track.setOrientation(LinearLayout.HORIZONTAL);
        track.setPadding(dp(4), dp(4), dp(4), dp(4));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(26));
        g.setColor(trackColor);
        track.setBackground(g);
        for (TextView sg : segs) {
            sg.setGravity(Gravity.CENTER);
            sg.setSingleLine(true);
            sg.setPadding(dp(2), 0, dp(2), 0);
            track.addView(sg, new LinearLayout.LayoutParams(0, dp(40), 1f));
        }
        return track;
    }

    private void segState(TextView sg, boolean on, int onBg, int onFg) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(20));
        g.setColor(on ? onBg : Color.TRANSPARENT);
        sg.setBackground(g);
        sg.setTextColor(on ? onFg : Pal.muted);
    }

    private TextView formChip(String s, boolean on) {
        TextView t = text(s, 14, Pal.muted, true);
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(dp(40));
        t.setPadding(dp(14), 0, dp(14), 0);
        styleChip(t, on, Pal.chip);
        return t;
    }

    private static boolean sameDay(long a, long b) {
        java.util.Calendar x = java.util.Calendar.getInstance();
        java.util.Calendar y = java.util.Calendar.getInstance();
        x.setTimeInMillis(a);
        y.setTimeInMillis(b);
        return x.get(java.util.Calendar.YEAR) == y.get(java.util.Calendar.YEAR)
                && x.get(java.util.Calendar.DAY_OF_YEAR) == y.get(java.util.Calendar.DAY_OF_YEAR);
    }

    private static final String[] EMOJIS = {
            "\uD83D\uDCDE", "\uD83D\uDCE7", "\uD83D\uDCB0", "\uD83D\uDED2", "\uD83C\uDF4E", "\uD83C\uDFCB\uFE0F",
            "\uD83D\uDCDA", "\uD83D\uDCBB", "\uD83D\uDCA1", "\uD83D\uDCC5", "\u23F0", "\uD83C\uDFE0",
            "\uD83D\uDE97", "\u2708\uFE0F", "\uD83C\uDF82", "\uD83C\uDF81", "\uD83D\uDC8A", "\uD83E\uDDD1\u200D\u2695\uFE0F",
            "\uD83D\uDC36", "\uD83C\uDF31", "\uD83E\uDDF9", "\uD83D\uDD27", "\uD83D\uDCDD", "\uD83D\uDCE6",
            "\uD83D\uDCB3", "\uD83C\uDF7D\uFE0F", "\u2615", "\uD83C\uDFAC", "\uD83C\uDFB5", "\uD83D\uDECF\uFE0F",
            "\u2764\uFE0F", "\u2B50", "\uD83D\uDD25", "\u26A0\uFE0F", "\uD83C\uDFAF", "\uD83D\uDCAA"};

    /** Emoji picker: a grid of common ones plus a box for any emoji from the keyboard. */
    private void pickEmoji(String current, final java.util.function.Consumer<String> done) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        final android.app.AlertDialog[] dlg = new android.app.AlertDialog[1];
        FlowLayout grid = new FlowLayout(this, dp(4), dp(4));
        for (final String e : EMOJIS) {
            TextView t = text(e, 26, Pal.ink, false);
            t.setGravity(Gravity.CENTER);
            t.setOnClickListener(v -> {
                Hap.tick(v);
                done.accept(e);
                dlg[0].dismiss();
            });
            grid.addView(t, new ViewGroup.LayoutParams(dp(48), dp(48)));
        }
        box.addView(grid);
        final EditText own = new EditText(this);
        own.setHint("Or type any emoji");
        own.setSingleLine(true);
        own.setTextColor(Pal.ink);
        own.setHintTextColor(Pal.muted);
        own.setText(current);
        LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(-1, -2);
        olp.topMargin = dp(12);
        box.addView(own, olp);
        android.app.AlertDialog.Builder b = new Dlg.Builder(this)
                .setTitle("Task emoji")
                .setView(box)
                .setPositiveButton("Use", (d, w) -> {
                    String s = own.getText().toString().trim();
                    if (s.length() > 8) s = s.substring(0, 8);
                    done.accept(s);
                })
                .setNeutralButton("Remove", (d, w) -> done.accept(""))
                .setNegativeButton("Cancel", null);
        dlg[0] = b.show();
    }

    private String subLine(Task t) {
        long when = t.snooze > 0 ? t.snooze : t.due;
        if (when <= 0) return null;
        StringBuilder sb = new StringBuilder();
        if (t.snooze > 0) sb.append("Snoozed to ");
        sb.append(Reminders.when(this, when));
        if (t.repeat > 0) sb.append(" \u00B7 ").append(Reminders.repeatLabel(t.repeat, t.repeatUnit));
        if (t.alarm) sb.append(" · Alarm");
        return sb.toString();
    }

    // ------------------------------------------------------- permissions

    private boolean notifOk() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        return getSystemService(NotificationManager.class).areNotificationsEnabled();
    }

    private boolean exactOk() {
        return Build.VERSION.SDK_INT < 31
                || getSystemService(AlarmManager.class).canScheduleExactAlarms();
    }

    private boolean fullScreenOk() {
        return Build.VERSION.SDK_INT < 34
                || getSystemService(NotificationManager.class).canUseFullScreenIntent();
    }

    private boolean batteryOk() {
        return getSystemService(PowerManager.class).isIgnoringBatteryOptimizations(getPackageName());
    }

    private void updateBell() {
        if (bell != null) bell.setOn(!notifOk() || !exactOk() || !fullScreenOk());
    }

    private void askNotifications() {
        SharedPreferences sp = getSharedPreferences("prefs", MODE_PRIVATE);
        boolean asked = sp.getBoolean("asked_notif", false);
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
                && !asked) {
            sp.edit().putBoolean("asked_notif", true).apply();
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
        } else {
            startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
        }
    }

    private void openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= 31) {
            startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:" + getPackageName())));
        }
    }

    private void openFullScreenSettings() {
        if (Build.VERSION.SDK_INT >= 34) {
            startActivity(new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                    Uri.parse("package:" + getPackageName())));
        }
    }

    private void openAppDetails() {
        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName())));
    }

    private void showSetup() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(4), dp(12), 0);
        permRow(box, "Notifications",
                "Show reminders in the status bar and on the lock screen.",
                notifOk(), this::askNotifications);
        permRow(box, "Alarms & reminders",
                "Ring exactly on time, even in battery saver.",
                exactOk(), this::openExactAlarmSettings);
        permRow(box, "Full-screen alerts",
                "Pop up over the lock screen like an alarm.",
                fullScreenOk(), this::openFullScreenSettings);
        permRow(box, "Battery: unrestricted",
                "On Samsung, set Battery to Unrestricted so the phone never puts this app to sleep.",
                batteryOk(), this::openAppDetails);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(box);
        setupDialog = new Dlg.Builder(this)
                .setTitle("Reminder setup")
                .setView(scroll)
                .setPositiveButton("Close", null)
                .show();
    }

    private void permRow(LinearLayout parent, String name, String desc, boolean ok, Runnable fix) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(64));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, dp(8), dp(8), dp(8));
        col.addView(text(name, 16, Pal.ink, true));
        col.addView(text(desc, 13, Pal.muted, false));
        row.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView action = text(ok ? "On" : "Allow", 15, ok ? Pal.muted : Pal.accent, true);
        action.setGravity(Gravity.CENTER);
        action.setMinWidth(dp(64));
        action.setMinHeight(dp(48));
        if (!ok) {
            action.setOnClickListener(v -> {
                if (setupDialog != null) setupDialog.dismiss();
                fix.run();
            });
        } else {
            action.setOnClickListener(v -> {
                if (name.startsWith("Battery")) { // still lets the user review it
                    if (setupDialog != null) setupDialog.dismiss();
                    fix.run();
                }
            });
        }
        row.addView(action, new LinearLayout.LayoutParams(-2, -2));
        parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }

    private void askOverlayPermission() {
        new Dlg.Builder(this)
                .setTitle("Allow display over other apps")
                .setMessage("Android needs this so an alarm can open full screen while the phone is unlocked. "
                        + "It is only used for your alarms.")
                .setPositiveButton("Open settings", (d, w) -> startActivity(new Intent(
                        android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:" + getPackageName()))))
                .setNegativeButton("Not now", null)
                .show();
    }

    private void ensureReminderPermissions(boolean needFullScreen) {
        if (!notifOk() || !exactOk() || (needFullScreen && !fullScreenOk())) {
            showSetup();
        }
    }

    // ----------------------------------------------------------- dialogs

    private void editDialog(final Task existing, String prefill) {
        final long[] due = {existing == null ? 0 : existing.due};
        final int[] rep = {existing == null ? 0 : existing.repeat,
                existing == null ? 2 : existing.repeatUnit};
        // true when the repeat is anything other than the Daily / Weekly shortcuts
        final boolean[] custom = {existing != null && existing.repeat > 0 && existing.repeatUnit != 5
                && !(existing.repeat == 1 && (existing.repeatUnit == 2 || existing.repeatUnit == 3))};
        final boolean[] alarm = {existing != null && existing.alarm};
        final boolean[] always = {existing != null && existing.always};
        final AlertDialog[] dlgRef = new AlertDialog[1];
        final String[] selSpec = {""}; // the quick button that set the time, "" for a hand-picked one
        final Runnable[] upd = new Runnable[1];
        final Runnable[] updSnd = new Runnable[1];
        final DialView dial = new DialView(this, true);
        dial.setVisibility(View.GONE);
        // Opens the dials for ANY time: "In" 1 min .. 99 weeks, or "At" any day within a year.
        final Runnable openDial = () -> {
            selSpec[0] = "";
            if (due[0] > 0) {
                dial.setTime(due[0]);
            } else {
                dial.setRelative(1, 1); // starts at 1 hour from now
                due[0] = dial.selectedTime();
            }
            dial.setVisibility(View.VISIBLE);
            upd[0].run();
        };

        final EditText input = new EditText(this);
        input.setHint("What needs doing?");
        input.setHintTextColor(Pal.muted);
        input.setTextColor(Pal.ink);
        input.setTextSize(18);
        input.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setMaxLines(4);
        if (existing != null) {
            input.setText(existing.title);
            input.setSelection(input.length());
        } else if (prefill != null && !prefill.isEmpty()) {
            input.setText(prefill);
            input.setSelection(input.length());
        }

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(24), dp(8), dp(24), dp(8));

        // title and emoji share one line; the emoji badge sits at the far right
        final String[] emoji = {existing == null ? "" : existing.emoji};
        final TextView emojiBtn = text("", 22, Pal.ink, false);
        emojiBtn.setGravity(Gravity.CENTER);
        emojiBtn.setIncludeFontPadding(false);
        final Runnable updEmoji = () -> {
            boolean none = emoji[0].isEmpty();
            emojiBtn.setText(none ? "\u263A" : emoji[0]);
            emojiBtn.setTextSize(none ? 20 : 22);
            emojiBtn.setTextColor(none ? Pal.muted : Pal.ink);
            GradientDrawable eg = new GradientDrawable();
            eg.setShape(GradientDrawable.OVAL);
            if (none) {
                eg.setColor(Pal.chip);
            } else {
                eg.setColor((Pal.accent & 0x00FFFFFF) | 0x24000000);
                eg.setStroke(dp(1), (Pal.accent & 0x00FFFFFF) | 0x59000000);
            }
            emojiBtn.setBackground(eg);
        };
        updEmoji.run();
        emojiBtn.setContentDescription("Task emoji");
        emojiBtn.setOnClickListener(v -> pickEmoji(emoji[0], picked -> {
            emoji[0] = picked;
            updEmoji.run();
        }));
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.addView(input, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams etl = new LinearLayout.LayoutParams(dp(44), dp(44));
        etl.leftMargin = dp(8);
        titleRow.addView(emojiBtn, etl);
        // Hero banner: the task title on a deep accent gradient with a live progress bar.
        // light mode: black banner; dark: light banner; AMOLED: black banner with a thin outline
        final int heroA = Pal.amoled ? 0xFF000000 : Pal.dark ? 0xFFE9EFEC : 0xFF0E1513;
        final int heroB = Pal.amoled ? 0xFF000000 : Pal.dark ? 0xFFCDD7D3 : 0xFF2A3431;
        final int heroInk = Pal.dark && !Pal.amoled ? 0xFF0E1513 : Color.WHITE;
        input.setTextColor(heroInk);
        input.setHintTextColor(HabitUi.alpha(heroInk, 150));
        Links.pillWatch(input, heroInk);
        input.setBackground(null);
        input.setPadding(0, 0, 0, 0);
        input.setTypeface(input.getTypeface(), android.graphics.Typeface.BOLD);
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(18), dp(14), dp(14), dp(14));
        GradientDrawable hg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{heroA, heroB});
        hg.setCornerRadius(dp(20));
        if (Pal.amoled) hg.setStroke(dp(1), 0xFF3A4643);
        hero.setBackground(hg);
        hero.addView(titleRow, new LinearLayout.LayoutParams(-1, -2));
        final View progTrack = new View(this);
        GradientDrawable pt = new GradientDrawable();
        pt.setCornerRadius(dp(3));
        pt.setColor(HabitUi.alpha(heroInk, 70));
        progTrack.setBackground(pt);
        final View progFill = new View(this);
        GradientDrawable pf = new GradientDrawable();
        pf.setCornerRadius(dp(3));
        pf.setColor(heroInk);
        progFill.setBackground(pf);
        android.widget.FrameLayout progBox = new android.widget.FrameLayout(this);
        progBox.addView(progTrack, new android.widget.FrameLayout.LayoutParams(-1, dp(6)));
        progBox.addView(progFill, new android.widget.FrameLayout.LayoutParams(0, dp(6)));
        LinearLayout.LayoutParams pbl = new LinearLayout.LayoutParams(-1, dp(6));
        pbl.topMargin = dp(10);
        hero.addView(progBox, pbl);
        final TextView progText = text("", 12, heroInk, true);
        progText.setPadding(0, dp(6), 0, 0);
        hero.addView(progText);
        form.addView(hero, new LinearLayout.LayoutParams(-1, -2));

        // Subtasks: press Enter on the keyboard to start the next one automatically.
        TextView subLabel = sectionLabel("Subtasks");
        subLabel.setPadding(0, dp(18), 0, dp(2));
        form.addView(subLabel);
        final LinearLayout subBox = new LinearLayout(this);
        subBox.setOrientation(LinearLayout.VERTICAL);
        final List<SubRow> subRows = new ArrayList<>();
        subChanged = () -> {
            final int n = subRows.size();
            int cnt = 0;
            for (SubRow r : subRows) if (r.done) cnt++;
            final int d = cnt;
            progBox.post(() -> {
                int w = progBox.getWidth();
                android.widget.FrameLayout.LayoutParams lp =
                        (android.widget.FrameLayout.LayoutParams) progFill.getLayoutParams();
                lp.width = n == 0 ? 0 : Math.round(w * d / (float) n);
                progFill.setLayoutParams(lp);
            });
            progText.setText(n == 0 ? "No subtasks yet" : d + " of " + n + " done");
        };
        if (existing != null) {
            for (Sub old : db.subs(existing.id)) {
                addSubRow(subBox, subRows, subRows.size(), old.title, old.done, false);
            }
        }
        subChanged.run();
        form.addView(subBox, new LinearLayout.LayoutParams(-1, -2));
        TextView addSub = text("+ Add subtask", 15, Pal.accent, true);
        addSub.setGravity(Gravity.CENTER);
        addSub.setMinHeight(dp(48));
        GradientDrawable adg = new GradientDrawable();
        adg.setCornerRadius(dp(16));
        adg.setStroke(dp(2), HabitUi.alpha(Pal.muted, 110), dp(6), dp(4));
        addSub.setBackground(adg);
        addSub.setOnClickListener(v -> {
            if (subRows.size() < 40) addSubRow(subBox, subRows, subRows.size(), "", false, true);
        });
        LinearLayout.LayoutParams asl = new LinearLayout.LayoutParams(-1, -2);
        asl.topMargin = dp(10);
        form.addView(addSub, asl);

        // WHEN: heading with Clear, then a row of quick buttons (the chosen one is a black pill with a tick)
        LinearLayout whenHead = new LinearLayout(this);
        whenHead.setOrientation(LinearLayout.HORIZONTAL);
        whenHead.setGravity(Gravity.CENTER_VERTICAL);
        whenHead.setPadding(0, dp(18), 0, dp(4));
        whenHead.addView(sectionLabel("When"), new LinearLayout.LayoutParams(0, -2, 1f));
        final TextView clear = text("Clear", 14, Pal.accent, true);
        clear.setGravity(Gravity.CENTER);
        clear.setMinHeight(dp(36));
        clear.setPadding(dp(12), 0, 0, 0);
        clear.setOnClickListener(v -> {
            due[0] = 0;
            selSpec[0] = "";
            dial.setVisibility(View.GONE);
            upd[0].run();
        });
        whenHead.addView(clear);
        form.addView(whenHead, new LinearLayout.LayoutParams(-1, -2));
        final TextView pick = text("", 17, Pal.accent, true); // label only; the buttons below do the picking
        final Runnable togglePick = () -> {
            if (dial.getVisibility() == View.VISIBLE) {
                dial.setVisibility(View.GONE);
            } else {
                openDial.run();
            }
        };

        // Quick presets: yours to add (+ Add) and remove (long-press a button)
        final HorizontalScrollView quickScroll = new HorizontalScrollView(this);
        quickScroll.setHorizontalScrollBarEnabled(false);
        quickScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        final LinearLayout quick = new LinearLayout(this);
        quick.setOrientation(LinearLayout.HORIZONTAL);
        quickScroll.addView(quick);
        final Runnable[] buildQuick = new Runnable[1];
        buildQuick[0] = () -> {
            quick.removeAllViews();
            if (due[0] > 0 && selSpec[0].isEmpty()) { // a time picked by hand
                TextView mine = formChip("✓ " + Reminders.when(this, due[0]), true);
                mine.setMinHeight(dp(44));
                mine.setOnClickListener(v -> {
                    Hap.tick(v);
                    togglePick.run();
                });
                LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(-2, -2);
                ml.rightMargin = dp(8);
                quick.addView(mine, ml);
            }
            for (final String spec : Presets.load(this)) {
                boolean on = due[0] > 0 && spec.equals(selSpec[0]);
                TextView q = formChip((on ? "✓ " : "") + Presets.label(spec), on);
                q.setMinHeight(dp(44));
                q.setOnClickListener(v -> {
                    Hap.tick(v);
                    selSpec[0] = spec;
                    due[0] = Presets.time(spec);
                    dial.setTime(due[0]);
                    upd[0].run();
                });
                q.setOnLongClickListener(v -> {
                    Hap.confirm(v);
                    new Dlg.Builder(this)
                            .setTitle("Remove “" + Presets.label(spec) + "”?")
                            .setMessage("It disappears from your quick buttons. You can add it again any time.")
                            .setPositiveButton("Remove", (d2, w2) -> {
                                Presets.remove(this, spec);
                                buildQuick[0].run();
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                    return true;
                });
                LinearLayout.LayoutParams ql = new LinearLayout.LayoutParams(-2, -2);
                ql.rightMargin = dp(8);
                quick.addView(q, ql);
            }
            TextView add = formChip("+ Add", false);
            add.setMinHeight(dp(44));
            add.setTextColor(Pal.accent);
            add.setOnClickListener(v -> {
                Hap.tick(v);
                newPreset(buildQuick[0]);
            });
            quick.addView(add, new LinearLayout.LayoutParams(-2, -2));
        };
        buildQuick[0].run();
        form.addView(quickScroll, new LinearLayout.LayoutParams(-1, -2));

        // Same swipe dials as the lock-screen snooze: "In" (amount + unit) or "At" (day, hour, minute).
        LinearLayout.LayoutParams dialLp = new LinearLayout.LayoutParams(-1, -2);
        dialLp.topMargin = dp(14);   // air between the WHEN buttons and the In / At tabs
        dialLp.bottomMargin = dp(6);
        form.addView(dial, dialLp);
        dial.setOnChange(() -> {
            selSpec[0] = "";
            due[0] = dial.selectedTime();
            upd[0].run();
        });

        // Repeat + alarm (only when a time is set)
        final LinearLayout repBox = new LinearLayout(this); // REPEAT: only when a time is set
        repBox.setOrientation(LinearLayout.VERTICAL);
        final LinearLayout extras = new LinearLayout(this); // alarm + sound: only when a time is set
        extras.setOrientation(LinearLayout.VERTICAL);
        TextView repLabel = sectionLabel("Repeat");
        repLabel.setPadding(0, dp(18), 0, dp(8));
        repBox.addView(repLabel);
        final TextView[] repChips = new TextView[5];
        String[] repNames = {"Once", "Daily", "Weekly", "Days", "Custom"};
        // "Every N min / hr / day / week / month": same swipe dials as the time pickers.
        final DialView everyDial = new DialView(this, false, true);
        everyDial.setRelative(Math.max(1, rep[1] == 5 ? 1 : rep[0]), rep[1] == 5 ? 2 : rep[1]);
        everyDial.setOnChange(() -> {
            rep[0] = everyDial.everyAmount();
            rep[1] = everyDial.everyUnit();
            upd[0].run();
        });
        for (int i = 0; i < 5; i++) {
            final int r = i;
            repChips[i] = text(repNames[i], 14, Pal.muted, true);
            repChips[i].setOnClickListener(v -> {
                if (r == 0) {
                    rep[0] = 0;
                    custom[0] = false;
                } else if (r == 1) {
                    rep[0] = 1;
                    rep[1] = 2;
                    custom[0] = false;
                } else if (r == 2) {
                    rep[0] = 1;
                    rep[1] = 3;
                    custom[0] = false;
                } else if (r == 3) { // specific weekdays: Monday to Friday ticked, weekend left off
                    if (rep[1] != 5 || rep[0] == 0) {
                        rep[0] = 31;
                        rep[1] = 5;
                    }
                    custom[0] = false;
                } else {
                    custom[0] = true;
                    if (rep[0] == 0 || rep[1] == 5) {
                        rep[0] = 1;
                        rep[1] = 2;
                    }
                    everyDial.setRelative(rep[0], rep[1]);
                }
                upd[0].run();
            });
        }
        repBox.addView(segTrack(repChips), new LinearLayout.LayoutParams(-1, -2));

        // Mon..Sun round toggles, shown for "Choose days"
        final TextView[] dayViews = new TextView[7];
        final LinearLayout daysRow = new LinearLayout(this);
        daysRow.setOrientation(LinearLayout.HORIZONTAL);
        String[] dayNames = {"M", "T", "W", "T", "F", "S", "S"};
        String[] dayFull = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
        for (int i = 0; i < 7; i++) {
            final int di = i;
            TextView dv = text(dayNames[i], 15, Pal.muted, true);
            dv.setGravity(Gravity.CENTER);
            dv.setContentDescription(dayFull[i]);
            dv.setOnClickListener(v -> {
                int m = rep[0] ^ (1 << di);
                if (m == 0) return; // keep at least one day
                rep[0] = m;
                upd[0].run();
            });
            dayViews[i] = dv;
            // equal-width cell with a fixed 40dp circle inside, so the shape stays round
            FrameLayout cell = new FrameLayout(this);
            cell.addView(dv, new FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER));
            daysRow.addView(cell, new LinearLayout.LayoutParams(0, dp(44), 1f));
        }
        LinearLayout.LayoutParams dayslp = new LinearLayout.LayoutParams(-1, -2);
        dayslp.topMargin = dp(10);
        repBox.addView(daysRow, dayslp);
        LinearLayout.LayoutParams edlp = new LinearLayout.LayoutParams(-1, -2);
        edlp.topMargin = dp(8);
        repBox.addView(everyDial, edlp);
        form.addView(repBox, new LinearLayout.LayoutParams(-1, -2));

        // PRIORITY: one segmented bar, the chosen level fills its pill with the priority colour
        TextView prioLabel = sectionLabel("Priority");
        prioLabel.setPadding(0, dp(18), 0, dp(8));
        form.addView(prioLabel);
        final int[] prio = {existing == null ? 0 : existing.priority};
        final TextView[] prioChips = new TextView[4];
        final String[] prioNames = {"None", "Low", "Medium", "High"};
        final int prioPill = Pal.dark ? 0xFF3A4643 : Color.WHITE;
        final Runnable[] updPrio = new Runnable[1];
        updPrio[0] = () -> {
            for (int k = 0; k < 4; k++) {
                if (k == 0) segState(prioChips[k], prio[0] == 0, prioPill, Pal.ink);
                else segState(prioChips[k], prio[0] == k, priorityColor(k), k == 2 ? 0xFF0E1513 : Color.WHITE);
            }
        };
        for (int k = 0; k < 4; k++) {
            final int level = k;
            prioChips[k] = text(prioNames[k], 14, Pal.muted, true);
            prioChips[k].setOnClickListener(v -> {
                Hap.tick(v);
                prio[0] = level;
                updPrio[0].run();
            });
        }
        form.addView(segTrack(prioChips), new LinearLayout.LayoutParams(-1, -2));
        updPrio[0].run();


        // Per-task sound and volume. Off by default: the task simply follows Settings.
        final String[] tSound = {existing == null ? "" : existing.sound};
        final int[] tVol = {existing == null ? -1 : existing.volume};
        final Runnable[] updType = new Runnable[1];
        final LinearLayout sndExtras = new LinearLayout(this); // sound: only when a time is set
        sndExtras.setOrientation(LinearLayout.VERTICAL);
        TextView sndLabel = sectionLabel("Sound & volume");
        sndLabel.setPadding(0, dp(16), 0, dp(8));
        sndExtras.addView(sndLabel);
        LinearLayout sndChips = new LinearLayout(this);
        sndChips.setOrientation(LinearLayout.HORIZONTAL);
        final TextView chipSame = formChip("Same as Settings", false);
        final TextView chipOwn = formChip("Custom for this task", false);
        LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(-2, -2);
        chipLp.rightMargin = dp(8);
        sndChips.addView(chipSame, chipLp);
        sndChips.addView(chipOwn, new LinearLayout.LayoutParams(-2, -2));
        sndExtras.addView(sndChips);

        final LinearLayout sndBox = new LinearLayout(this);
        sndBox.setOrientation(LinearLayout.VERTICAL);
        LinearLayout sndRow = new LinearLayout(this);
        sndRow.setOrientation(LinearLayout.HORIZONTAL);
        sndRow.setGravity(Gravity.CENTER_VERTICAL);
        sndRow.setMinimumHeight(dp(56));
        LinearLayout sndText = new LinearLayout(this);
        sndText.setOrientation(LinearLayout.VERTICAL);
        sndText.addView(text("Sound", 16, Pal.ink, true));
        final TextView sndName = text("", 14, Pal.accent, false);
        sndText.addView(sndName);
        sndRow.addView(sndText, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView sndChange = text("Change", 15, Pal.accent, true);
        sndChange.setGravity(Gravity.CENTER);
        sndChange.setMinHeight(dp(48));
        sndChange.setMinWidth(dp(72));
        sndRow.addView(sndChange);
        sndBox.addView(sndRow);
        LinearLayout volHead = new LinearLayout(this);
        volHead.setOrientation(LinearLayout.HORIZONTAL);
        volHead.addView(text("Volume", 16, Pal.ink, true), new LinearLayout.LayoutParams(0, -2, 1f));
        final TextView volPct = text("", 15, Pal.muted, true);
        volHead.addView(volPct);
        sndBox.addView(volHead);
        final PillSlider volBar = new PillSlider(this);
        volBar.setContentDescription("Volume for this task");
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(-1, dp(48));
        vlp.topMargin = dp(6);
        sndBox.addView(volBar, vlp);
        sndExtras.addView(sndBox);

        updSnd[0] = () -> {
            boolean own = tVol[0] >= 0 || !tSound[0].isEmpty();
            styleChip(chipSame, !own, Pal.chip);
            styleChip(chipOwn, own, Pal.chip);
            sndBox.setVisibility(own ? View.VISIBLE : View.GONE);
            sndName.setText(Sound.describeValue(this, tSound[0]));
            int kind = alarm[0] ? Prefs.ALARM : Prefs.NOTIF;
            int v = tVol[0] >= 0 ? tVol[0] : Prefs.volume(this, kind);
            volBar.setValue(v);
            volPct.setText(v + "%");
            if (updType[0] != null) updType[0].run();
        };
        chipSame.setOnClickListener(v -> {
            tSound[0] = "";
            tVol[0] = -1;
            updSnd[0].run();
        });
        chipOwn.setOnClickListener(v -> {
            if (tVol[0] < 0) tVol[0] = Prefs.volume(this, alarm[0] ? Prefs.ALARM : Prefs.NOTIF);
            updSnd[0].run();
        });
        volBar.setListener(new PillSlider.Listener() {
            @Override
            public void onChange(int value) {
                tVol[0] = value;
                volPct.setText(value + "%");
                Sound.setLiveVolume(value);
            }

            @Override
            public void onStart() {
                Sound.play(MainActivity.this, alarm[0] ? Prefs.ALARM : Prefs.NOTIF, true, 6_000L,
                        null, tSound[0], tVol[0]);
            }

            @Override
            public void onStop() {
                Sound.stopPreview();
            }
        });
        sndRow.setOnClickListener(v -> {
            final int kind = alarm[0] ? Prefs.ALARM : Prefs.NOTIF;
            soundCb = picked -> {
                tSound[0] = picked;
                if (tVol[0] < 0) tVol[0] = Prefs.volume(this, kind);
                updSnd[0].run();
                Sound.play(this, kind, false, 6_000L, null, tSound[0], tVol[0]);
            };
            new Dlg.Builder(this)
                    .setTitle("Sound for this task")
                    .setItems(new String[]{"Sounds on this phone\u2026", "Audio file\u2026",
                            "Same as Settings", "Silent"}, (dd, which) -> {
                        if (which == 0) SoundPicker.phone(this, kind, tSound[0]);
                        else if (which == 1) SoundPicker.file(this);
                        else soundCb.accept(which == 2 ? "" : Prefs.SILENT);
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
        // ALERT TYPE: one control instead of two toggles. Full alert = rings like an alarm and takes over
        // the screen even when unlocked; Normal alert = an ordinary notification; Silence = no sound.
        TextView typeLabel = sectionLabel("Alert type");
        typeLabel.setPadding(0, dp(18), 0, dp(8));
        extras.addView(typeLabel);
        final TextView[] typeChips = new TextView[3];
        String[] typeNames = {"Full alert", "Normal alert", "Silence"};
        final String[] typeHelp = {
                "Full alert rings like an alarm and takes over the screen, even when unlocked",
                "Normal alert shows a notification and plays the sound you chose",
                "Silence shows a notification with no sound"};
        final TextView typeNote = text("", 13, Pal.muted, false);
        typeNote.setPadding(0, dp(8), 0, 0);
        for (int k = 0; k < 3; k++) {
            final int mode = k;
            typeChips[k] = text(typeNames[k], 14, Pal.muted, true);
            typeChips[k].setOnClickListener(v -> {
                Hap.tick(v);
                if (Prefs.SILENT.equals(tSound[0])) tSound[0] = "";
                alarm[0] = mode == 0;
                always[0] = mode == 0;
                if (mode == 2) tSound[0] = Prefs.SILENT;
                if (mode == 0 && !android.provider.Settings.canDrawOverlays(this)) askOverlayPermission();
                updSnd[0].run();
            });
        }
        extras.addView(segTrack(typeChips), new LinearLayout.LayoutParams(-1, -2));
        extras.addView(typeNote);
        updType[0] = () -> {
            int mode = alarm[0] ? 0 : Prefs.SILENT.equals(tSound[0]) ? 2 : 1;
            for (int k = 0; k < 3; k++) segState(typeChips[k], mode == k, Pal.sel, Pal.onSel);
            typeNote.setText(typeHelp[mode]);
        };
        updSnd[0].run();

        // Pick time: its own full-width button, just above Sound & volume (always visible, so a time can be set)
        final TextView pickBtn = formChip("Pick time\u2026", false);
        pickBtn.setMinHeight(dp(44));
        pickBtn.setTextColor(Pal.accent);
        pickBtn.setOnClickListener(v -> {
            Hap.tick(v);
            togglePick.run();
            if (dial.getVisibility() == View.VISIBLE) {
                dial.post(() -> dial.requestRectangleOnScreen(
                        new android.graphics.Rect(0, 0, dial.getWidth(), dial.getHeight())));
            }
        });
        LinearLayout.LayoutParams pickLp = new LinearLayout.LayoutParams(-1, -2);
        pickLp.topMargin = dp(12);
        form.addView(extras, new LinearLayout.LayoutParams(-1, -2));
        form.addView(pickBtn, pickLp);
        form.addView(sndExtras, new LinearLayout.LayoutParams(-1, -2));
        final TextView[] dupRef = new TextView[1];
        if (existing != null) { // sits bottom-left in the Cancel / Save row (see below)
            TextView dup = text("Duplicate", 14, Pal.accent, true);
            dup.setGravity(Gravity.CENTER);
            dup.setSingleLine(true);
            dup.setPadding(dp(6), 0, dp(6), 0);
            GradientDrawable dg = new GradientDrawable();
            dg.setCornerRadius(dp(24));
            dg.setColor(Pal.chip);
            dup.setBackground(dg);
            dup.setContentDescription("Duplicate task");
            dupRef[0] = dup;
            dup.setOnClickListener(v -> {
                long copy = db.duplicate(existing.id);
                if (copy > 0) {
                    Reminders.schedule(this, db.get(copy));
                    Hap.confirm(listView);
                    toast("Task duplicated");
                    refresh();
                }
                dlgRef[0].dismiss();
            });
        }

        upd[0] = () -> {
            buildQuick[0].run();
            boolean has = due[0] > 0;
            pick.setText(has ? Reminders.when(this, due[0]) : "Pick any date & time\u2026");
            pick.setTextColor(has ? Pal.accent : Pal.muted);
            clear.setVisibility(has ? View.VISIBLE : View.GONE);
            extras.setVisibility(has ? View.VISIBLE : View.GONE);
            sndExtras.setVisibility(has ? View.VISIBLE : View.GONE);
            repBox.setVisibility(has ? View.VISIBLE : View.GONE);
            boolean daysMode = rep[1] == 5 && rep[0] > 0;
            boolean daily = rep[0] == 1 && rep[1] == 2;
            boolean weekly = rep[0] == 1 && rep[1] == 3;
            boolean isCustom = custom[0] && rep[0] > 0 && !daysMode;
            segState(repChips[0], rep[0] == 0, Pal.sel, Pal.onSel);
            segState(repChips[1], !custom[0] && !daysMode && daily, Pal.sel, Pal.onSel);
            segState(repChips[2], !custom[0] && !daysMode && weekly, Pal.sel, Pal.onSel);
            segState(repChips[3], daysMode, Pal.sel, Pal.onSel);
            segState(repChips[4], isCustom, Pal.sel, Pal.onSel);
            everyDial.setVisibility(isCustom ? View.VISIBLE : View.GONE);
            daysRow.setVisibility(daysMode ? View.VISIBLE : View.GONE);
            for (int i = 0; i < 7; i++) {
                boolean on = daysMode && (rep[0] & (1 << i)) != 0;
                GradientDrawable dg = new GradientDrawable();
                dg.setShape(GradientDrawable.OVAL);
                dg.setColor(on ? Pal.accent : Pal.chip);
                dayViews[i].setBackground(dg);
                dayViews[i].setTextColor(on ? Color.WHITE : Pal.muted);
            }
        };
        upd[0].run();

        ScrollView scroll = new ScrollView(this);
        scroll.addView(form);

        final AlertDialog dialog = new Dlg.Builder(this)
                .setTitle(existing == null ? "New task" : "Edit task")
                .setView(scroll)
                .setPositiveButton(existing == null ? "Add task" : "Save task", null)
                .setNegativeButton("Cancel", null)
                .create();
        dlgRef[0] = dialog;
        dialog.setOnDismissListener(dd -> {
            Sound.stopPreview();
            soundCb = null;
        });
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        dialog.show();
        Dlg.style(dialog);
        // Cancel + Save task fill the width (1 : 2), lined up with the form's edges
        android.widget.Button negB = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        android.widget.Button posB = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (posB.getParent() instanceof LinearLayout && ((LinearLayout) posB.getParent()).getParent() instanceof LinearLayout) {
            // Our own button row (Duplicate | Cancel | Save task) replaces the stock bar, so the
            // three always sit on one line and never stack.
            LinearLayout bar = (LinearLayout) posB.getParent();
            LinearLayout panel = (LinearLayout) bar.getParent();
            bar.removeView(negB);
            bar.removeView(posB);
            bar.setVisibility(View.GONE);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(dp(24), dp(10), dp(24), dp(16));
            boolean hasDup = dupRef[0] != null;
            if (hasDup) {
                LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(0, dp(48), 1.3f);
                dl.rightMargin = dp(8);
                row.addView(dupRef[0], dl);
            }
            LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(0, dp(48), 1f);
            nl.rightMargin = dp(hasDup ? 8 : 10);
            row.addView(negB, nl);
            row.addView(posB, new LinearLayout.LayoutParams(0, dp(48), hasDup ? 1.5f : 2f));
            panel.addView(row, panel.indexOfChild(bar) + 1);
            Fonts.apply(row);
        }
        if (dupRef[0] != null && dupRef[0].getParent() == null) { // fallback: end of the form
            form.addView(dupRef[0], new LinearLayout.LayoutParams(-1, dp(48)));
        }
        input.requestFocus();

        // Overridden so a validation error keeps the dialog (and the typed text) open.
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String s = input.getText().toString().trim();
            if (s.isEmpty()) return;
            // "In 1 hour" is measured from the moment you press Add, not from when you opened the dial.
            long d = dial.getVisibility() == View.VISIBLE ? dial.selectedTime() : due[0];
            due[0] = d;
            boolean timeChanged = existing == null || existing.due != d;
            if (d > 0 && timeChanged && d <= System.currentTimeMillis()) {
                toast("Pick a time in the future");
                return;
            }
            if (d > 0 && rep[1] == 5 && rep[0] > 0) d = Reminders.nextAllowedDay(d, rep[0]);
            due[0] = d;
            long id;
            if (existing == null) {
                id = db.add(s, d, rep[0], rep[1], alarm[0]);
                if (filter == TaskDb.DONE || filter >= TaskDb.TODAY) filter = TaskDb.ACTIVE;
            } else {
                id = existing.id;
                db.update(id, s, d, rep[0], rep[1], alarm[0]);
            }
            List<Sub> subsToSave = new ArrayList<>();
            for (SubRow r : subRows) {
                String st = r.et.getText().toString().trim();
                if (!st.isEmpty()) subsToSave.add(new Sub(0, id, st, r.done));
            }
            db.setSound(id, tSound[0], tVol[0]);
            db.setPriority(id, prio[0]);
            db.setEmoji(id, emoji[0]);
            db.setAlarmMode(id, alarm[0], alarm[0] && always[0]);
            db.replaceSubs(id, subsToSave);
            Reminders.schedule(this, db.get(id));
            if (d > 0) ensureReminderPermissions(alarm[0]);
            dialog.dismiss();
            refresh();
        });
    }

    // ------------------------------------------------ subtask rows (editor)

    private Runnable subChanged;

    private final class SubRow {
        boolean done;
        final LinearLayout view;
        final IconView check;
        final EditText et;
        final IconView remove;

        SubRow(String text, boolean done) {
            this.done = done;
            view = new LinearLayout(MainActivity.this);
            view.setOrientation(LinearLayout.HORIZONTAL);
            view.setGravity(Gravity.CENTER_VERTICAL);
            // filled card; an accent ring shows on the row being edited
            final GradientDrawable card = new GradientDrawable();
            card.setCornerRadius(dp(16));
            card.setColor(Pal.chip);
            view.setBackground(card);
            view.setPadding(dp(4), 0, dp(4), 0);
            LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(-1, -2);
            vlp.topMargin = dp(8);
            view.setLayoutParams(vlp);

            check = new IconView(MainActivity.this, IconView.CHECK);
            check.setOn(done);
            check.setContentDescription("Mark subtask done");
            view.addView(check, new LinearLayout.LayoutParams(dp(40), dp(48)));

            et = new EditText(MainActivity.this);
            et.setHint("Subtask");
            et.setTextSize(16);
            // No MULTI_LINE flag: the keyboard's Enter key becomes a "Next" action key.
            et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            et.setImeOptions(EditorInfo.IME_ACTION_NEXT);
            et.setText(text);
            Links.pillWatch(et, Pal.accent);
            // One tap must open the keyboard: the first touch only moves focus, and the IME can ignore
            // an implicit show, so ask for it explicitly on focus, on touch-up and on click.
            final Runnable showKb = () -> {
                et.requestFocus();
                MainActivity.this.getSystemService(InputMethodManager.class).showSoftInput(et, 0);
            };
            et.setOnClickListener(v -> et.post(showKb));
            final boolean[] touched = {false}; // only a finger opens the keyboard, never auto-focus
            et.setOnTouchListener((v, ev) -> {
                if (ev.getAction() == android.view.MotionEvent.ACTION_DOWN) touched[0] = true;
                if (ev.getAction() == android.view.MotionEvent.ACTION_UP) v.postDelayed(showKb, 60);
                return false;
            });
            et.setBackground(null);
            et.setTextColor(Pal.ink);
            et.setHintTextColor(Pal.muted);
            et.setPadding(0, dp(10), 0, dp(10));
            et.setOnFocusChangeListener((v, has) -> {
                if (has) {
                    card.setStroke(dp(2), Pal.accent);
                    if (touched[0]) v.postDelayed(showKb, 60);
                } else {
                    card.setStroke(0, 0);
                    touched[0] = false;
                }
            });
            view.addView(et, new LinearLayout.LayoutParams(0, -2, 1f));

            remove = new IconView(MainActivity.this, IconView.CROSS);
            remove.setContentDescription("Remove subtask");
            view.addView(remove, new LinearLayout.LayoutParams(dp(40), dp(48)));
        }
    }

    private SubRow addSubRow(final LinearLayout box, final List<SubRow> rows, int index,
                             String text, boolean done, boolean focus) {
        final SubRow r = new SubRow(text, done);
        rows.add(index, r);
        box.addView(r.view, index);
        Fonts.apply(r.view);

        r.check.setOnClickListener(v -> {
            r.done = !r.done;
            r.check.setOn(r.done);
            if (subChanged != null) subChanged.run();
        });
        r.remove.setOnClickListener(v -> removeSubRow(box, rows, r));

        // Enter: a filled line starts the next subtask; an empty line ends the list.
        r.et.setOnEditorActionListener((tv, action, ev) -> {
            if (ev != null && ev.getAction() != KeyEvent.ACTION_DOWN) return true;
            String s = tv.getText().toString().trim();
            if (s.isEmpty()) {
                removeSubRow(box, rows, r);
                getSystemService(InputMethodManager.class)
                        .hideSoftInputFromWindow(tv.getWindowToken(), 0);
            } else if (rows.size() < 40) {
                addSubRow(box, rows, rows.indexOf(r) + 1, "", false, true);
            }
            return true;
        });
        // Backspace on an empty line removes it and goes back to the one above.
        r.et.setOnKeyListener((v, keyCode, ev) -> {
            if (keyCode == KeyEvent.KEYCODE_DEL && ev.getAction() == KeyEvent.ACTION_DOWN
                    && r.et.length() == 0) {
                removeSubRow(box, rows, r);
                return true;
            }
            return false;
        });
        if (focus) { // "+ Add subtask" / Enter: focus the new line AND raise the keyboard
            r.et.requestFocus();
            r.et.postDelayed(() -> getSystemService(InputMethodManager.class)
                    .showSoftInput(r.et, 0), 80);
        }
        if (subChanged != null) subChanged.run();
        return r;
    }

    private void removeSubRow(LinearLayout box, List<SubRow> rows, SubRow r) {
        int i = rows.indexOf(r);
        if (i < 0) return;
        rows.remove(i);
        box.removeView(r.view);
        if (i > 0) rows.get(i - 1).et.requestFocus();
        if (subChanged != null) subChanged.run();
    }

    private void showUndo(String msg, Runnable undo) {
        undoAction = undo;
        undoPill.setContentDescription("Undo: " + msg);
        undoPill.removeCallbacks(undoHide);
        undoPill.animate().cancel();
        undoPill.setVisibility(View.VISIBLE);
        undoPill.setAlpha(0f);
        undoPill.animate().alpha(1f).setDuration(150).start();
        undoPill.start(4000); // linear, 4 seconds from full to empty
        undoPill.postDelayed(undoHide, 4000);
    }

    private void hideUndo(boolean fade) {
        undoPill.removeCallbacks(undoHide);
        undoPill.stop();
        undoAction = null;
        if (fade) {
            undoPill.animate().alpha(0f).setDuration(180)
                    .withEndAction(() -> undoPill.setVisibility(View.GONE)).start();
        } else {
            undoPill.animate().cancel();
            undoPill.setVisibility(View.GONE);
        }
    }

    /** Dialog flow for making a new quick button. */
    private void newPreset(final Runnable done) {
        String[] items = {"In\u2026 (for example 45 min or 2 days)", "At a time of day\u2026",
                "Tomorrow at\u2026", "Back to the standard three"};
        new Dlg.Builder(this)
                .setTitle("New quick button")
                .setItems(items, (d, which) -> {
                    if (which == 0) {
                        final DialView every = new DialView(this, false, true);
                        every.setRelative(30, 0);
                        new Dlg.Builder(this)
                                .setTitle("Remind me in")
                                .setView(every)
                                .setPositiveButton("Add", (d2, w2) -> {
                                    Presets.add(this, Presets.specIn(every.everyAmount(), every.everyUnit()));
                                    done.run();
                                })
                                .setNegativeButton("Cancel", null)
                                .show();
                    } else if (which == 3) {
                        Presets.reset(this);
                        done.run();
                    } else {
                        final boolean tomorrow = which == 2;
                        int theme = Pal.dark ? android.R.style.Theme_DeviceDefault_Dialog_Alert
                                : android.R.style.Theme_DeviceDefault_Light_Dialog_Alert;
                        new android.app.TimePickerDialog(this, theme, (tp, h, m) -> {
                            Presets.add(this, Presets.specAt(tomorrow, h, m));
                            done.run();
                        }, tomorrow ? 9 : 20, 0, true).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmDelete(final Task t) {
        new Dlg.Builder(this)
                .setTitle("Delete task?")
                .setMessage(t.title)
                .setPositiveButton("Delete", (d, w) -> {
                    final TaskDb.Snap snap = db.snapshot("id = ?", new String[]{String.valueOf(t.id)});
                    Reminders.cancel(this, t.id);
                    db.delete(t.id);
                    Hap.confirm(listView);
                    refresh();
                    showUndo("Task deleted", () -> {
                        db.restore(snap);
                        Reminders.schedule(this, db.get(t.id));
                        refresh();
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmClearDone() {
        new Dlg.Builder(this)
                .setTitle("Clear completed tasks?")
                .setMessage("They are removed from this device. You can undo for a few seconds.")
                .setPositiveButton("Clear", (d, w) -> {
                    final TaskDb.Snap snap = db.snapshot("done = 1", null);
                    for (Task t : db.list(TaskDb.DONE)) Reminders.cancel(this, t.id);
                    db.clearDone();
                    Hap.confirm(listView);
                    refresh();
                    showUndo("Completed tasks cleared", () -> {
                        db.restore(snap);
                        refresh();
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void toggleDone(final Task t) {
        if (t.repeat > 0) { // a repeating task: tick this round only, the series carries on
            final long today = startOfDayOf(System.currentTimeMillis());
            if (t.done) { // Repeat-tab row: bring today's round back to Active
                Reminders.toggleDay(this, db, t, today);
                Hap.confirm(listView);
                refresh();
                return;
            }
            final long day = startOfDayOf(t.due);
            if (day > today) { // a future round cannot be ticked early
                toast(Reminders.earlyMessage(this, t));
                return;
            }
            boolean nowDone = Reminders.toggleDay(this, db, t, day);
            Hap.confirm(listView);
            refresh();
            if (nowDone) {
                showUndo("Done \u00B7 moved to Repeat", () -> {
                    Reminders.toggleDay(this, db, db.get(t.id), day);
                    refresh();
                });
            }
            return;
        }
        setDoneAndSchedule(t.id, !t.done);
        Hap.confirm(listView);
        refresh();
        if (!t.done) {
            showUndo("Task completed", () -> {
                setDoneAndSchedule(t.id, false);
                refresh();
            });
        }
    }

    private static long startOfDayOf(long t) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTimeInMillis(t);
        c.set(java.util.Calendar.HOUR_OF_DAY, 0);
        c.set(java.util.Calendar.MINUTE, 0);
        c.set(java.util.Calendar.SECOND, 0);
        c.set(java.util.Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private void setDoneAndSchedule(long id, boolean done) {
        db.setDone(id, done);
        if (done) {
            Reminders.cancel(this, id);
        } else {
            Reminders.schedule(this, db.get(id)); // re-opened: re-arm if still pending
        }
    }

    // ----------------------------------------------------------- adapter

    // ----------------------------------------------- main screen layouts

    /** 0 current, 1 two sections, 2 switch, 4 routine strip, 5 routine dock. Only the Active tab uses them. */
    private int layoutMode() {
        return filter == TaskDb.ACTIVE && !searching ? Prefs.mainLayout(this) : 0;
    }

    /** All repeating tasks: the open ones, then the ones ticked for today (waiting in the Repeat tab). */
    private List<Task> routinesNow() {
        List<Task> r = new ArrayList<>();
        for (Task t : db.list(TaskDb.ACTIVE)) if (t.repeat > 0) r.add(t);
        r.addAll(db.list(TaskDb.REPEAT));
        return r;
    }

    private void buildItems() {
        items.clear();
        extrasBox.removeAllViews();
        extrasBox.setVisibility(View.GONE);
        dockView.removeAllViews();
        dockView.setVisibility(View.GONE);
        int mode = layoutMode();
        listView.setPadding(0, 0, 0, dp(96));
        if (mode == 0) {
            items.addAll(tasks);
            return;
        }
        List<Task> one = new ArrayList<>();
        List<Task> rep = new ArrayList<>();
        for (Task t : tasks) (t.repeat > 0 ? rep : one).add(t);

        if (mode == 1) { // two sections
            if (!one.isEmpty()) {
                items.add(new Header("One-off", one.size(), 0));
                if (!collapsed[0]) items.addAll(one);
            }
            if (!rep.isEmpty()) {
                items.add(new Header("Repeating", rep.size(), 1));
                if (!collapsed[1]) items.addAll(rep);
            }
        } else if (mode == 2) { // switch
            int kind = Prefs.mainKind(this);
            if (tasks.isEmpty()) return;
            final TextView[] segs = new TextView[3];
            String[] names = {"All " + tasks.size(), "One-off " + one.size(), "Repeating " + rep.size()};
            final int pillOn = Pal.dark ? 0xFF3A4643 : Color.WHITE;
            for (int i = 0; i < 3; i++) {
                final int k = i;
                segs[i] = text(names[i], 14, Pal.muted, true);
                segState(segs[i], kind == i, pillOn, Pal.ink);
                segs[i].setOnClickListener(v -> {
                    Hap.tick(v);
                    Prefs.setMainKind(this, k);
                    refresh();
                });
            }
            LinearLayout tr = segTrack(segs, HabitUi.blend(Pal.ink, Pal.bg, 0.08f));
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, -2);
            tlp.setMargins(dp(24), 0, dp(24), dp(8));
            extrasBox.addView(tr, tlp);
            extrasBox.setVisibility(View.VISIBLE);
            if (kind == 0) {
                items.addAll(tasks);
            } else if (kind == 1) {
                items.addAll(one);
                if (!rep.isEmpty()) {
                    android.text.SpannableString hs = new android.text.SpannableString(
                            rep.size() + " repeating task" + (rep.size() == 1 ? "" : "s") + " hidden · show");
                    int at = hs.length() - 4;
                    hs.setSpan(new android.text.style.StyleSpan(Typeface.BOLD), at, hs.length(), 0);
                    hs.setSpan(new android.text.style.ForegroundColorSpan(Pal.accent), at, hs.length(), 0);
                    items.add(new Hint(hs, () -> {
                        Prefs.setMainKind(this, 0);
                        refresh();
                    }));
                }
            } else {
                items.addAll(rep);
            }
        } else {
            final List<Task> routines = routinesNow();
            if (mode == 4) { // routine strip
                if (!routines.isEmpty()) {
                    extrasBox.addView(routineStrip(routines));
                    extrasBox.setVisibility(View.VISIBLE);
                }
                if (!one.isEmpty() || !routines.isEmpty()) items.add(new Header("One-off tasks", one.size(), 2));
            } else { // 5: routine dock
                if (!routines.isEmpty()) {
                    fillDock(routines);
                    dockView.setVisibility(View.VISIBLE);
                    listView.setPadding(0, 0, 0, dp(96 + 72));
                }
            }
            items.addAll(one);
            if (one.isEmpty() && !routines.isEmpty()) {
                items.add(new Hint("No one-off tasks · type below to add one", null));
            }
        }
    }

    private View routineStrip(List<Task> routines) {
        int doneCount = 0;
        for (Task t : routines) if (t.done) doneCount++;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        TextView head = sectionLabel("Routines · " + doneCount + " of " + routines.size() + " done");
        head.setTextColor(Pal.accent);
        head.setPadding(dp(26), dp(4), dp(20), dp(8));
        box.addView(head);
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(20), 0, dp(12), dp(4));
        for (final Task t : routines) {
            int bg = t.done ? Pal.chip : Pal.accent;
            int fg = t.done ? Pal.muted : Pal.onAccent;
            LinearLayout tile = new LinearLayout(this);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setPadding(dp(12), dp(10), dp(12), dp(10));
            GradientDrawable tg = new GradientDrawable();
            tg.setCornerRadius(dp(20));
            tg.setColor(bg);
            tile.setBackground(tg);
            LinearLayout top = new LinearLayout(this);
            top.setGravity(Gravity.CENTER_VERTICAL);
            TextView em = text(t.emoji.isEmpty() ? "↻" : t.emoji, 22, fg, false);
            top.addView(em, new LinearLayout.LayoutParams(0, -2, 1f));
            TextView ck = text(t.done ? "✓" : "", 14, t.done ? Pal.onAccent : fg, true);
            ck.setGravity(Gravity.CENTER);
            GradientDrawable cg = new GradientDrawable();
            cg.setCornerRadius(dp(7));
            if (t.done) cg.setColor(Pal.accent); else cg.setStroke(dp(2), fg);
            ck.setBackground(cg);
            top.addView(ck, new LinearLayout.LayoutParams(dp(24), dp(24)));
            tile.addView(top);
            TextView nm = text(t.title, 14, fg, true);
            nm.setMaxLines(2);
            nm.setEllipsize(android.text.TextUtils.TruncateAt.END);
            nm.setPadding(0, dp(8), 0, 0);
            if (t.done) nm.setPaintFlags(nm.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            tile.addView(nm);
            TextView tm = text(t.due > 0 ? Reminders.when(this, t.due) : "", 11, fg, false);
            tm.setSingleLine(true);
            tm.setEllipsize(android.text.TextUtils.TruncateAt.END);
            tm.setAlpha(0.8f);
            tile.addView(tm);
            tile.setOnClickListener(v -> toggleDone(t));
            tile.setOnLongClickListener(v -> {
                Hap.confirm(v);
                Task full = db.get(t.id);
                if (full != null) editDialog(full, null);
                return true;
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(116), -2);
            lp.rightMargin = dp(8);
            row.addView(tile, lp);
        }
        hs.addView(row);
        box.addView(hs);
        return box;
    }

    private void fillDock(final List<Task> routines) {
        int n = routines.size();
        Task next = null;
        for (Task t : routines) {
            if (!t.done && (next == null || t.due < next.due)) next = t;
        }
        FrameLayout badge = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(HabitUi.alpha(Pal.onSel, 36));
        badge.setBackground(bg);
        View glyph = new View(this);
        glyph.setBackground(new RepeatDrawable(Pal.onSel, dp(20)));
        badge.addView(glyph, new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER));
        dockView.addView(badge, new LinearLayout.LayoutParams(dp(38), dp(38)));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(12), 0, dp(8), 0);
        col.addView(text(n + " routine" + (n == 1 ? "" : "s"), 16, Pal.onSel, true));
        TextView nx = text(next == null ? "All done for today"
                : "Next: " + next.title + " · " + Reminders.when(this, next.due), 12, Pal.onSel, false);
        nx.setSingleLine(true);
        nx.setEllipsize(android.text.TextUtils.TruncateAt.END);
        nx.setAlpha(0.75f);
        col.addView(nx);
        dockView.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout dots = new LinearLayout(this);
        for (int i = 0; i < Math.min(n, 6); i++) {
            View d = new View(this);
            GradientDrawable dg = new GradientDrawable();
            dg.setShape(GradientDrawable.OVAL);
            dg.setColor(routines.get(i).done ? Pal.onSel : HabitUi.alpha(Pal.onSel, 80));
            d.setBackground(dg);
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(dp(9), dp(9));
            dl.leftMargin = dp(4);
            dots.addView(d, dl);
        }
        dockView.addView(dots);
        TextView chev = text("⌃", 22, Pal.onSel, true);
        chev.setPadding(dp(12), 0, 0, 0);
        dockView.addView(chev);
        dockView.setOnClickListener(v -> {
            Hap.tick(v);
            routinesDialog();
        });
    }

    /** The routines in one list: tick one to move it to the Repeat tab, tick again to bring it back. */
    private void routinesDialog() {
        final LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(4), dp(16), 0);
        final Runnable[] fill = new Runnable[1];
        fill[0] = () -> {
            box.removeAllViews();
            List<Task> list = routinesNow();
            if (list.isEmpty()) {
                TextView none = text("No repeating tasks yet.", 15, Pal.muted, false);
                none.setPadding(dp(8), dp(12), 0, dp(12));
                box.addView(none);
            }
            for (final Task t : list) {
                LinearLayout r = new LinearLayout(this);
                r.setGravity(Gravity.CENTER_VERTICAL);
                IconView ck = new IconView(this, IconView.CHECK);
                ck.setOn(t.done);
                ck.setContentDescription(t.done ? "Mark as not done" : "Mark as done");
                ck.setOnClickListener(v -> {
                    toggleDone(t);
                    fill[0].run();
                });
                r.addView(ck, new LinearLayout.LayoutParams(dp(48), dp(48)));
                LinearLayout c2 = new LinearLayout(this);
                c2.setOrientation(LinearLayout.VERTICAL);
                TextView nm = text((t.emoji.isEmpty() ? "" : t.emoji + "  ") + t.title, 16,
                        t.done ? Pal.muted : Pal.ink, false);
                if (t.done) nm.setPaintFlags(nm.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                c2.addView(nm);
                if (t.due > 0) {
                    c2.addView(text((t.done ? "Next: " : "") + Reminders.when(this, t.due), 12, Pal.muted, false));
                }
                r.addView(c2, new LinearLayout.LayoutParams(0, -2, 1f));
                box.addView(r, new LinearLayout.LayoutParams(-1, dp(56)));
            }
        };
        fill[0].run();
        ScrollView sv = new ScrollView(this);
        sv.addView(box);
        AlertDialog d = new Dlg.Builder(this)
                .setTitle("Routines")
                .setView(sv)
                .setPositiveButton("Close", null)
                .create();
        d.show();
        Dlg.style(d);
    }

    // ----------------------------------------------------------- adapter

    private final class TaskAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public Object getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            Object o = items.get(position);
            return o instanceof Task ? ((Task) o).id : -1 - position;
        }

        @Override
        public int getViewTypeCount() {
            return 3;
        }

        @Override
        public int getItemViewType(int position) {
            Object o = items.get(position);
            return o instanceof Task ? 0 : o instanceof Header ? 1 : 2;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Object o = getItem(position);
            if (o instanceof Header) return headerView((Header) o);
            if (o instanceof Hint) return hintView((Hint) o);
            Row row = convertView instanceof Row ? (Row) convertView : new Row(MainActivity.this);
            row.bind((Task) o);
            return row;
        }
    }

    private View headerView(final Header h) {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.HORIZONTAL);
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setPadding(dp(26), dp(12), dp(20), dp(6));
        boolean collapsible = h.key < 2;
        if (collapsible) {
            TextView chev = text(collapsed[h.key] ? "▸" : "▾", 16, h.key == 1 ? Pal.accent : Pal.ink, true);
            chev.setPadding(0, 0, dp(8), 0);
            v.addView(chev);
        }
        TextView label = sectionLabel(h.text);
        label.setTextColor(h.key == 1 ? Pal.accent : h.key == 0 ? Pal.ink : Pal.muted);
        v.addView(label);
        if (collapsible) {
            TextView n = text(String.valueOf(h.count), 12, Pal.ink, true);
            n.setGravity(Gravity.CENTER);
            n.setPadding(dp(8), dp(1), dp(8), dp(1));
            GradientDrawable ng = new GradientDrawable();
            ng.setCornerRadius(dp(9));
            ng.setColor(Pal.chip);
            n.setBackground(ng);
            LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(-2, -2);
            nl.leftMargin = dp(8);
            v.addView(n, nl);
            v.setOnClickListener(x -> {
                Hap.tick(x);
                collapsed[h.key] = !collapsed[h.key];
                refresh();
            });
        }
        return v;
    }

    private View hintView(final Hint h) {
        TextView t = text("", 13, Pal.muted, false);
        t.setText(h.text);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(24), dp(16), dp(24), dp(8));
        if (h.click != null) t.setOnClickListener(v -> h.click.run());
        return t;
    }

    private final class Row extends FrameLayout {
        private final IconView check;
        private final TextView title;
        private final TextView emojiView;
        private final TextView sub;
        private final LinearLayout textCol;
        private final LinearLayout subsBox;
        private final IconView delete;
        private final LinearLayout card;
        private final FrameLayout reveal;
        private final GradientDrawable revealBg = new GradientDrawable();
        private final IconView revealTick;
        private final IconView revealTrash;
        private final View prioBar;
        private final int slop;
        private Task bound;
        private GradientDrawable cardBg;
        private float downX;
        private float downY;
        private boolean swiping;
        private boolean armed;

        Row(Context c) {
            super(c);
            slop = ViewConfiguration.get(c).getScaledTouchSlop();
            setPadding(dp(20), dp(4), dp(20), dp(4));

            // Shown behind the card while swiping: green tick (right) / red bin (left).
            reveal = new FrameLayout(c);
            revealBg.setCornerRadius(dp(16));
            reveal.setBackground(revealBg);
            reveal.setVisibility(View.INVISIBLE);
            revealTick = new IconView(c, IconView.SWIPE_DONE);
            revealTick.setTint(Pal.onAccent);
            revealTick.setClickable(false);
            reveal.addView(revealTick, new FrameLayout.LayoutParams(dp(56), dp(56), Gravity.START | Gravity.CENTER_VERTICAL));
            revealTrash = new IconView(c, IconView.TRASH);
            revealTrash.setTint(Color.WHITE);
            revealTrash.setClickable(false);
            reveal.addView(revealTrash, new FrameLayout.LayoutParams(dp(56), dp(56), Gravity.END | Gravity.CENTER_VERTICAL));
            addView(reveal, new FrameLayout.LayoutParams(-1, -1));

            card = new LinearLayout(c);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setMinimumHeight(dp(64));
            card.setPadding(dp(4), 0, dp(4), 0);
            cardBg = new GradientDrawable();
            cardBg.setColor(Pal.card);
            cardBg.setCornerRadius(dp(16));
            card.setBackground(cardBg);

            prioBar = new View(c);
            prioBar.setVisibility(View.GONE);

            check = new IconView(c, IconView.CHECK);
            card.addView(check, new LinearLayout.LayoutParams(dp(48), dp(48)));

            LinearLayout col = new LinearLayout(c);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setPadding(0, dp(8), 0, dp(8));
            title = text("", 17, Pal.ink, false);
            col.addView(title);
            sub = text("", 12, Pal.muted, false);
            col.addView(sub);
            subsBox = new LinearLayout(c);
            subsBox.setOrientation(LinearLayout.VERTICAL);
            col.addView(subsBox);
            textCol = col;
            card.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));

            delete = new IconView(c, IconView.CROSS);
            delete.setContentDescription("Delete task");
            card.addView(delete, new LinearLayout.LayoutParams(dp(48), dp(48)));

            // A task with an emoji shows it as a soft badge in the delete spot; tapping asks before deleting.
            emojiView = text("", 22, Pal.ink, false);
            emojiView.setGravity(Gravity.CENTER);
            emojiView.setIncludeFontPadding(false);
            GradientDrawable badge = new GradientDrawable();
            badge.setShape(GradientDrawable.OVAL);
            badge.setColor((Pal.accent & 0x00FFFFFF) | 0x24000000);
            badge.setStroke(dp(1), (Pal.accent & 0x00FFFFFF) | 0x59000000);
            emojiView.setBackground(badge);
            emojiView.setVisibility(View.GONE);
            LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(dp(42), dp(42));
            elp.setMargins(dp(3), 0, dp(3), 0);
            card.addView(emojiView, elp);

            // priority stripe sits at the far right edge, after the emoji / delete button
            LinearLayout.LayoutParams pbl = new LinearLayout.LayoutParams(dp(4), dp(32));
            pbl.leftMargin = dp(4);
            pbl.rightMargin = dp(6);
            card.addView(prioBar, pbl);

            addView(card, new FrameLayout.LayoutParams(-1, -2));
        }

        // ---- swipe: right = done / not done, left = delete (asks first)

        @Override
        public boolean onInterceptTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getX();
                    downY = e.getY();
                    swiping = false;
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (!swiping) {
                        float dx = e.getX() - downX;
                        float dy = e.getY() - downY;
                        if (Math.abs(dx) > slop * 1.5f && Math.abs(dx) > Math.abs(dy) * 1.5f) {
                            swiping = true;
                            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                        }
                    }
                    break;
                default:
                    break;
            }
            return swiping;
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN) {
                downX = e.getX();
                downY = e.getY();
                return true; // so a swipe that starts on the row padding still follows the finger
            }
            if (!swiping) {
                if (a == MotionEvent.ACTION_MOVE) {
                    float dx = e.getX() - downX;
                    float dy = e.getY() - downY;
                    if (Math.abs(dx) > slop * 1.5f && Math.abs(dx) > Math.abs(dy) * 1.5f) {
                        swiping = true;
                        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                    }
                }
                return true;
            }
            float dx = e.getX() - downX;
            if (a == MotionEvent.ACTION_MOVE) {
                boolean past = Math.abs(dx) >= Math.max(dp(96), getWidth() * 0.3f);
                if (past && !armed) Hap.tick(this);
                armed = past;
                showSwipe(dx);
            } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                swiping = false;
                float limit = Math.max(dp(96), getWidth() * 0.3f);
                Task t = bound;
                card.animate().translationX(0).setDuration(160).withEndAction(
                        () -> reveal.setVisibility(View.INVISIBLE)).start();
                if (a == MotionEvent.ACTION_UP && t != null) {
                    if (dx <= -limit) confirmDelete(t);
                    else if (dx >= limit) toggleDone(t);
                }
            }
            return true;
        }

        private void showSwipe(float dx) {
            card.setTranslationX(dx);
            reveal.setVisibility(View.VISIBLE);
            boolean left = dx < 0;
            revealBg.setColor(left ? 0xFFD64545 : Pal.accent);
            revealTrash.setVisibility(left ? View.VISIBLE : View.INVISIBLE);
            revealTick.setVisibility(left ? View.INVISIBLE : View.VISIBLE);
        }

        void bind(final Task t) {
            bound = t;
            boolean tinted = layoutMode() == 1 && t.repeat > 0; // "Two sections": repeating rows are tinted
            cardBg.setColor(tinted ? HabitUi.blend(Pal.accent, Pal.card, 0.14f) : Pal.card);
            card.setMinimumHeight(dp(tinted ? 56 : 64));
            swiping = false;
            armed = false;
            card.animate().cancel();
            card.setTranslationX(0);
            reveal.setVisibility(View.INVISIBLE);

            emojiView.setText(t.emoji);
            emojiView.setAlpha(t.done ? 0.45f : 1f);
            emojiView.setVisibility(t.emoji.isEmpty() ? View.GONE : View.VISIBLE);
            delete.setVisibility(t.emoji.isEmpty() ? View.VISIBLE : View.GONE);
            emojiView.setContentDescription("Delete task");
            emojiView.setOnClickListener(v -> confirmDelete(t));
            title.setText(t.title);
            title.setPaintFlags(t.done
                    ? title.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG
                    : title.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            title.setTextColor(t.done ? Pal.muted : Pal.ink);
            Links.linkify(title); // links show as pills; taps elsewhere still open the editor

            if (t.priority > 0 && !t.done) {
                GradientDrawable pg = new GradientDrawable();
                pg.setCornerRadius(dp(2));
                pg.setColor(priorityColor(t.priority));
                prioBar.setBackground(pg);
                prioBar.setVisibility(View.VISIBLE);
                prioBar.setContentDescription(t.priority == 1 ? "Low priority"
                        : t.priority == 2 ? "Medium priority" : "High priority");
            } else {
                prioBar.setVisibility(View.GONE);
            }

            String line = subLine(t);
            if (line == null) {
                sub.setVisibility(View.GONE);
            } else {
                // A square-ish "when" tile, so it never reads as one of the round link pills:
                // overdue = red, today = solid accent, later = neutral, done = muted.
                long when = t.snooze > 0 ? t.snooze : t.due;
                long now = System.currentTimeMillis();
                boolean overdue = !t.done && when < now;
                boolean today = !t.done && !overdue && sameDay(when, now);
                GradientDrawable wb = new GradientDrawable();
                wb.setCornerRadius(dp(8));
                int fg;
                if (t.done) {
                    wb.setColor(Pal.chip);
                    fg = Pal.muted;
                } else if (overdue) {
                    wb.setColor((Pal.overdue & 0x00FFFFFF) | 0x26000000);
                    wb.setStroke(dp(1), Pal.overdue);
                    fg = Pal.overdue;
                } else if (today) {
                    wb.setColor(Pal.accent);
                    fg = Pal.onAccent;
                } else {
                    wb.setColor(Pal.chip);
                    fg = Pal.ink;
                }
                sub.setVisibility(View.VISIBLE);
                if (t.alarm && line.endsWith("Alarm")) { // a bell before the word "Alarm"
                    android.text.SpannableStringBuilder sbb = new android.text.SpannableStringBuilder(line);
                    int at = line.length() - 5;
                    sbb.insert(at, "\uFFFC ");
                    BellDrawable alarmBell = new BellDrawable(fg, dp(14));
                    sbb.setSpan(new android.text.style.ImageSpan(alarmBell, android.text.style.ImageSpan.ALIGN_BOTTOM),
                            at, at + 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    sub.setText(sbb);
                } else {
                    sub.setText(line);
                }
                sub.setTextColor(fg);
                sub.setTypeface(Typeface.DEFAULT_BOLD);
                sub.setBackground(wb);
                sub.setPadding(dp(8), dp(3), dp(8), dp(3));
                LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-2, -2);
                slp.topMargin = dp(4);
                sub.setLayoutParams(slp);
                if (t.repeat > 0) { // recurring: a small repeat glyph at the start of the tile
                    sub.setCompoundDrawablesWithIntrinsicBounds(new RepeatDrawable(fg, dp(14)), null, null, null);
                    sub.setCompoundDrawablePadding(dp(5));
                } else {
                    sub.setCompoundDrawables(null, null, null, null);
                }
            }

            check.setOn(t.done);
            check.setContentDescription(t.done ? "Mark as not done" : "Mark as done");
            check.setOnClickListener(v -> toggleDone(t));
            // The whole text column opens the editor; only a tap exactly on a link pill opens the link.
            textCol.setOnClickListener(v -> editDialog(t, null));
            delete.setOnClickListener(v -> confirmDelete(t));

            subsBox.removeAllViews();
            for (final Sub s : db.subs(t.id)) {
                LinearLayout subRow = new LinearLayout(MainActivity.this);
                subRow.setOrientation(LinearLayout.HORIZONTAL);
                subRow.setGravity(Gravity.CENTER_VERTICAL);

                IconView box = new IconView(MainActivity.this, IconView.CHECK);
                box.setOn(s.done);
                box.setContentDescription(s.done ? "Mark subtask not done" : "Mark subtask done");
                box.setOnClickListener(v -> {
                    boolean nowDone = !s.done;
                    db.setSubDone(s.id, nowDone);
                    Hap.tick(v);
                    boolean allDone = true;
                    java.util.List<Sub> all = db.subs(t.id);
                    for (Sub x : all) allDone &= x.done;
                    if (allDone && !all.isEmpty() && !t.done && t.repeat > 0) { // repeating: this round is done
                        if (startOfDayOf(t.due) > startOfDayOf(System.currentTimeMillis())) {
                            toast(Reminders.earlyMessage(MainActivity.this, t));
                        } else {
                            Reminders.toggleDay(MainActivity.this, db, t, startOfDayOf(t.due));
                            Hap.confirm(v);
                            toast("All subtasks done \u00B7 moved to Repeat");
                        }
                    } else if (allDone && !all.isEmpty() && !t.done) { // last subtask ticked: the task is done too
                        setDoneAndSchedule(t.id, true); // moves to Done, subtasks stay ticked
                        Hap.confirm(v);
                        toast("All subtasks done \u00B7 task completed");
                    } else if (!nowDone && t.done) { // un-ticking one re-opens the task
                        setDoneAndSchedule(t.id, false);
                    }
                    refresh();
                });
                subRow.addView(box, new LinearLayout.LayoutParams(dp(40), dp(40)));

                TextView st = text(s.title, 15, s.done ? Pal.muted : Pal.ink, false);
                st.setPaintFlags(s.done
                        ? st.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG
                        : st.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
                Links.linkify(st);
                // tapping a subtask's text also opens the editor
                subRow.setOnClickListener(v -> editDialog(t, null));
                subRow.addView(st, new LinearLayout.LayoutParams(0, -2, 1f));
                subsBox.addView(subRow);
            }
            Fonts.apply(this);
        }
    }
}
