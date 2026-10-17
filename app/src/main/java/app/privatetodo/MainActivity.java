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
    private int filter = TaskDb.ACTIVE;
    private float density;

    private TextView subtitle;
    private View clearDone;
    private IconView bell;
    private final TextView[] chips = new TextView[3];
    private LinearLayout emptyView;
    private TextView emptyTitle;
    private TextView emptyBody;
    private TaskAdapter adapter;
    private ListView listView;
    private AlertDialog setupDialog;
    private boolean builtDark;
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
        builtDark = Pal.dark;
        setTheme(Pal.dark ? R.style.AppThemeDark : R.style.AppTheme);
        super.onCreate(savedInstanceState);
        density = getResources().getDisplayMetrics().density;
        db = new TaskDb(this);
        Reminders.ensureChannels(this);
        setContentView(buildUi());
        Fonts.apply(getWindow().getDecorView());
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Pal.init(this);
        if (Pal.dark != builtDark) { // theme changed in Settings
            recreate();
            return;
        }
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

        IconView notebook = new IconView(this, IconView.NOTE);
        notebook.setContentDescription("Notebook");
        notebook.setOnClickListener(v -> startActivity(new Intent(this, NotesActivity.class)));
        header.addView(notebook, new LinearLayout.LayoutParams(dp(48), dp(48)));

        bell = new IconView(this, IconView.BELL);
        bell.setContentDescription("Reminder setup");
        bell.setOnClickListener(v -> showSetup());
        header.addView(bell, new LinearLayout.LayoutParams(dp(48), dp(48)));

        IconView trash = new IconView(this, IconView.TRASH);
        trash.setTint(Pal.ink);
        trash.setContentDescription("Clear completed tasks");
        trash.setOnClickListener(v -> confirmClearDone());
        clearDone = trash;
        header.addView(trash, new LinearLayout.LayoutParams(dp(48), dp(48)));
        column.addView(header, new LinearLayout.LayoutParams(-1, -2));

        // Filter chips
        LinearLayout chipRow = new LinearLayout(this);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        chipRow.setPadding(dp(24), dp(16), dp(24), dp(12));
        String[] names = {"Active", "Done", "All"};
        for (int i = 0; i < 3; i++) {
            final int f = i;
            TextView chip = text(names[i], 15, Pal.muted, true);
            chip.setGravity(Gravity.CENTER);
            chip.setMinHeight(dp(40));
            chip.setPadding(dp(18), 0, dp(18), 0);
            chip.setOnClickListener(v -> {
                filter = f;
                refresh();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = dp(8);
            chipRow.addView(chip, lp);
            chips[i] = chip;
        }
        column.addView(chipRow, new LinearLayout.LayoutParams(-1, -2));

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
        bar.setPadding(dp(16), dp(8), dp(16), dp(16));

        IconView gear = new IconView(this, IconView.GEAR);
        gear.setContentDescription("Settings");
        floatingCircle(gear);
        gear.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        bar.addView(gear, new LinearLayout.LayoutParams(dp(52), dp(52)));

        LinearLayout pill = new LinearLayout(this);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(dp(20), 0, dp(4), 0);
        GradientDrawable pillBg = new GradientDrawable();
        pillBg.setColor(Pal.raised);
        pillBg.setCornerRadius(dp(26));
        pill.setBackground(pillBg);
        pill.setElevation(dp(1));

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
        searchBtn.setOnClickListener(v -> setSearching(!searching));
        bar.addView(searchBtn, new LinearLayout.LayoutParams(dp(52), dp(52)));

        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(-1, -2);
        blp.gravity = Gravity.BOTTOM;
        root.addView(bar, blp);
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
        v.setElevation(dp(1));
    }

    /** Adds a plain task straight from the bottom pill (no reminder; use the clock for that). */
    private void addFromPill() {
        String s = pillInput.getText().toString().trim();
        if (s.isEmpty()) return;
        db.add(s, 0, 0, 2, false);
        if (filter == TaskDb.DONE) filter = TaskDb.ACTIVE;
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
        adapter.notifyDataSetChanged();

        int left = db.count(false);
        int done = db.count(true);
        subtitle.setText(left + " left · " + done + " done");
        clearDone.setVisibility(done > 0 ? View.VISIBLE : View.INVISIBLE);

        for (int i = 0; i < 3; i++) {
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
        } else if (filter == TaskDb.DONE) {
            emptyTitle.setText("Nothing done yet");
            emptyBody.setText("Finished tasks show up here.");
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

    private TextView formChip(String s, boolean on) {
        TextView t = text(s, 14, Pal.muted, true);
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(dp(40));
        t.setPadding(dp(14), 0, dp(14), 0);
        styleChip(t, on, Pal.chip);
        return t;
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
        final boolean[] custom = {existing != null && existing.repeat > 0
                && !(existing.repeat == 1 && (existing.repeatUnit == 2 || existing.repeatUnit == 3))};
        final boolean[] alarm = {existing != null && existing.alarm};
        final Runnable[] upd = new Runnable[1];
        final Runnable[] updSnd = new Runnable[1];
        final DialView dial = new DialView(this, true);
        dial.setVisibility(View.GONE);
        // Opens the dials for ANY time: "In" 1 min .. 99 weeks, or "At" any day within a year.
        final Runnable openDial = () -> {
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
        form.addView(input, new LinearLayout.LayoutParams(-1, -2));

        // Subtasks: press Enter on the keyboard to start the next one automatically.
        TextView subLabel = text("Subtasks", 13, Pal.muted, true);
        subLabel.setPadding(0, dp(16), 0, dp(2));
        form.addView(subLabel);
        final LinearLayout subBox = new LinearLayout(this);
        subBox.setOrientation(LinearLayout.VERTICAL);
        final List<SubRow> subRows = new ArrayList<>();
        if (existing != null) {
            for (Sub old : db.subs(existing.id)) {
                addSubRow(subBox, subRows, subRows.size(), old.title, old.done, false);
            }
        }
        form.addView(subBox, new LinearLayout.LayoutParams(-1, -2));
        TextView addSub = text("+ Add subtask", 15, Pal.accent, true);
        addSub.setGravity(Gravity.CENTER_VERTICAL);
        addSub.setMinHeight(dp(48));
        addSub.setOnClickListener(v -> {
            if (subRows.size() < 40) addSubRow(subBox, subRows, subRows.size(), "", false, true);
        });
        form.addView(addSub, new LinearLayout.LayoutParams(-1, -2));

        TextView prioLabel = text("Priority", 13, Pal.muted, true);
        prioLabel.setPadding(0, dp(12), 0, dp(6));
        form.addView(prioLabel);
        final int[] prio = {existing == null ? 0 : existing.priority};
        final TextView[] prioChips = new TextView[4];
        LinearLayout prioRow = new LinearLayout(this);
        prioRow.setOrientation(LinearLayout.HORIZONTAL);
        String[] prioNames = {"None", "Low", "Medium", "High"};
        final Runnable[] updPrio = new Runnable[1];
        updPrio[0] = () -> {
            for (int k = 0; k < 4; k++) {
                boolean on = prio[0] == k;
                styleChip(prioChips[k], on, Pal.chip);
                if (on && k > 0) {
                    GradientDrawable g = new GradientDrawable();
                    g.setCornerRadius(dp(20));
                    g.setColor(priorityColor(k));
                    prioChips[k].setBackground(g);
                    prioChips[k].setTextColor(k == 2 ? 0xFF111513 : Color.WHITE);
                }
            }
        };
        for (int k = 0; k < 4; k++) {
            final int level = k;
            prioChips[k] = formChip(prioNames[k], false);
            prioChips[k].setPadding(dp(4), 0, dp(4), 0);
            prioChips[k].setOnClickListener(v -> {
                prio[0] = level;
                updPrio[0].run();
            });
            LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(0, dp(40), 1f);
            if (k < 3) plp.rightMargin = dp(8);
            prioRow.addView(prioChips[k], plp);
        }
        updPrio[0].run();
        form.addView(prioRow, new LinearLayout.LayoutParams(-1, -2));

        TextView remindLabel = text("Remind me", 13, Pal.muted, true);
        remindLabel.setPadding(0, dp(16), 0, dp(6));
        form.addView(remindLabel);

        // Quick presets
        LinearLayout quick = new LinearLayout(this);
        quick.setOrientation(LinearLayout.HORIZONTAL);
        String[] qNames = {"In 1 hour", "Tonight 8 pm", "Tomorrow 9 am"};
        for (int i = 0; i < qNames.length; i++) {
            final int which = i;
            TextView q = formChip(qNames[i], false);
            q.setTextSize(13);
            q.setPadding(dp(4), 0, dp(4), 0);
            q.setOnClickListener(v -> {
                Calendar c = Calendar.getInstance();
                if (which == 0) {
                    c.add(Calendar.HOUR_OF_DAY, 1);
                } else if (which == 1) {
                    c.set(Calendar.HOUR_OF_DAY, 20);
                    c.set(Calendar.MINUTE, 0);
                    if (c.getTimeInMillis() <= System.currentTimeMillis()) {
                        c.add(Calendar.DAY_OF_YEAR, 1);
                    }
                } else {
                    c.add(Calendar.DAY_OF_YEAR, 1);
                    c.set(Calendar.HOUR_OF_DAY, 9);
                    c.set(Calendar.MINUTE, 0);
                }
                c.set(Calendar.SECOND, 0);
                c.set(Calendar.MILLISECOND, 0);
                due[0] = c.getTimeInMillis();
                dial.setTime(due[0]);
                upd[0].run();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(44), 1f);
            if (i < qNames.length - 1) lp.rightMargin = dp(8);
            quick.addView(q, lp);
        }
        form.addView(quick, new LinearLayout.LayoutParams(-1, -2));

        // Picked time + clear
        LinearLayout pickRow = new LinearLayout(this);
        pickRow.setOrientation(LinearLayout.HORIZONTAL);
        pickRow.setGravity(Gravity.CENTER_VERTICAL);
        final TextView pick = text("", 17, Pal.accent, true);
        pick.setGravity(Gravity.CENTER_VERTICAL);
        pick.setMinHeight(dp(52));
        pickRow.addView(pick, new LinearLayout.LayoutParams(0, -2, 1f));
        final TextView clear = text("Clear", 15, Pal.muted, true);
        clear.setGravity(Gravity.CENTER);
        clear.setMinHeight(dp(48));
        clear.setPadding(dp(12), 0, dp(4), 0);
        clear.setOnClickListener(v -> {
            due[0] = 0;
            dial.setVisibility(View.GONE);
            upd[0].run();
        });
        pickRow.addView(clear, new LinearLayout.LayoutParams(-2, -2));
        form.addView(pickRow, new LinearLayout.LayoutParams(-1, -2));

        // Same swipe dials as the lock-screen snooze: "In" (amount + unit) or "At" (day, hour, minute).
        form.addView(dial, new LinearLayout.LayoutParams(-1, -2));
        dial.setOnChange(() -> {
            due[0] = dial.selectedTime();
            upd[0].run();
        });
        pick.setOnClickListener(v -> {
            if (dial.getVisibility() == View.VISIBLE) {
                dial.setVisibility(View.GONE);
                return;
            }
            openDial.run();
        });

        // Repeat + alarm (only when a time is set)
        final LinearLayout extras = new LinearLayout(this);
        extras.setOrientation(LinearLayout.VERTICAL);
        TextView repLabel = text("Repeat", 13, Pal.muted, true);
        repLabel.setPadding(0, dp(8), 0, dp(6));
        extras.addView(repLabel);
        LinearLayout repRow = new LinearLayout(this);
        repRow.setOrientation(LinearLayout.HORIZONTAL);
        final TextView[] repChips = new TextView[4];
        String[] repNames = {"Once", "Daily", "Weekly", "Custom\u2026"};
        // "Every N min / hr / day / week / month": same swipe dials as the time pickers.
        final DialView everyDial = new DialView(this, false, true);
        everyDial.setRelative(Math.max(1, rep[0]), rep[1]);
        everyDial.setOnChange(() -> {
            rep[0] = everyDial.everyAmount();
            rep[1] = everyDial.everyUnit();
            upd[0].run();
        });
        for (int i = 0; i < 4; i++) {
            final int r = i;
            repChips[i] = formChip(repNames[i], false);
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
                } else {
                    custom[0] = true;
                    if (rep[0] == 0) {
                        rep[0] = 1;
                        rep[1] = 2;
                    }
                    everyDial.setRelative(rep[0], rep[1]);
                }
                upd[0].run();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = dp(8);
            repRow.addView(repChips[i], lp);
        }
        HorizontalScrollView repScroll = new HorizontalScrollView(this);
        repScroll.setHorizontalScrollBarEnabled(false);
        repScroll.addView(repRow);
        extras.addView(repScroll);
        LinearLayout.LayoutParams edlp = new LinearLayout.LayoutParams(-1, -2);
        edlp.topMargin = dp(8);
        extras.addView(everyDial, edlp);

        LinearLayout alarmRow = new LinearLayout(this);
        alarmRow.setOrientation(LinearLayout.HORIZONTAL);
        alarmRow.setGravity(Gravity.CENTER_VERTICAL);
        alarmRow.setMinimumHeight(dp(64));
        alarmRow.setPadding(0, dp(12), 0, 0);
        LinearLayout alarmText = new LinearLayout(this);
        alarmText.setOrientation(LinearLayout.VERTICAL);
        alarmText.addView(text("Alarm style", 16, Pal.ink, true));
        TextView alarmDesc = text("Full screen on the lock screen. Rings until you dismiss it.", 13, Pal.muted, false);
        alarmDesc.setPadding(0, dp(2), 0, 0);
        alarmText.addView(alarmDesc);
        alarmRow.addView(alarmText, new LinearLayout.LayoutParams(0, -2, 1f));
        final ToggleView alarmSwitch = new ToggleView(this);
        alarmSwitch.set(alarm[0], false);
        alarmSwitch.setListener(on -> {
            alarm[0] = on;
            if (updSnd[0] != null) updSnd[0].run(); // default volume differs for alarms
        });
        LinearLayout.LayoutParams swLp = new LinearLayout.LayoutParams(-2, -2);
        swLp.leftMargin = dp(16);
        alarmRow.addView(alarmSwitch, swLp);
        alarmRow.setOnClickListener(v -> alarmSwitch.performClick());
        extras.addView(alarmRow, new LinearLayout.LayoutParams(-1, -2));

        // Per-task sound and volume. Off by default: the task simply follows Settings.
        final String[] tSound = {existing == null ? "" : existing.sound};
        final int[] tVol = {existing == null ? -1 : existing.volume};
        TextView sndLabel = text("Sound & volume", 13, Pal.muted, true);
        sndLabel.setPadding(0, dp(12), 0, dp(6));
        extras.addView(sndLabel);
        LinearLayout sndChips = new LinearLayout(this);
        sndChips.setOrientation(LinearLayout.HORIZONTAL);
        final TextView chipSame = formChip("Same as Settings", false);
        final TextView chipOwn = formChip("Custom for this task", false);
        LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(-2, -2);
        chipLp.rightMargin = dp(8);
        sndChips.addView(chipSame, chipLp);
        sndChips.addView(chipOwn, new LinearLayout.LayoutParams(-2, -2));
        extras.addView(sndChips);

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
        extras.addView(sndBox);

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
        updSnd[0].run();
        form.addView(extras, new LinearLayout.LayoutParams(-1, -2));

        upd[0] = () -> {
            boolean has = due[0] > 0;
            pick.setText(has ? Reminders.when(this, due[0]) : "Pick any date & time\u2026");
            pick.setTextColor(has ? Pal.accent : Pal.muted);
            clear.setVisibility(has ? View.VISIBLE : View.GONE);
            extras.setVisibility(has ? View.VISIBLE : View.GONE);
            boolean daily = rep[0] == 1 && rep[1] == 2;
            boolean weekly = rep[0] == 1 && rep[1] == 3;
            boolean isCustom = custom[0] && rep[0] > 0;
            styleChip(repChips[0], rep[0] == 0, Pal.chip);
            styleChip(repChips[1], !custom[0] && daily, Pal.chip);
            styleChip(repChips[2], !custom[0] && weekly, Pal.chip);
            styleChip(repChips[3], isCustom, Pal.chip);
            repChips[3].setText(isCustom ? Reminders.repeatLabel(rep[0], rep[1]) : "Custom\u2026");
            everyDial.setVisibility(isCustom ? View.VISIBLE : View.GONE);
        };
        upd[0].run();

        ScrollView scroll = new ScrollView(this);
        scroll.addView(form);

        final AlertDialog dialog = new Dlg.Builder(this)
                .setTitle(existing == null ? "New task" : "Edit task")
                .setView(scroll)
                .setPositiveButton(existing == null ? "Add" : "Save", null)
                .setNegativeButton("Cancel", null)
                .create();
        dialog.setOnDismissListener(dd -> {
            Sound.stopPreview();
            soundCb = null;
        });
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        dialog.show();
        Dlg.style(dialog);
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
            long id;
            if (existing == null) {
                id = db.add(s, d, rep[0], rep[1], alarm[0]);
                if (filter == TaskDb.DONE) filter = TaskDb.ACTIVE;
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
            db.replaceSubs(id, subsToSave);
            Reminders.schedule(this, db.get(id));
            if (d > 0) ensureReminderPermissions(alarm[0]);
            dialog.dismiss();
            refresh();
        });
    }

    // ------------------------------------------------ subtask rows (editor)

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
        if (focus) r.et.requestFocus();
        return r;
    }

    private void removeSubRow(LinearLayout box, List<SubRow> rows, SubRow r) {
        int i = rows.indexOf(r);
        if (i < 0) return;
        rows.remove(i);
        box.removeView(r.view);
        if (i > 0) rows.get(i - 1).et.requestFocus();
    }

    private void confirmDelete(final Task t) {
        new Dlg.Builder(this)
                .setTitle("Delete task?")
                .setMessage(t.title)
                .setPositiveButton("Delete", (d, w) -> {
                    Reminders.cancel(this, t.id);
                    db.delete(t.id);
                    refresh();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmClearDone() {
        new Dlg.Builder(this)
                .setTitle("Clear completed tasks?")
                .setMessage("This permanently deletes them from this device.")
                .setPositiveButton("Clear", (d, w) -> {
                    for (Task t : db.list(TaskDb.DONE)) Reminders.cancel(this, t.id);
                    db.clearDone();
                    refresh();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void toggleDone(Task t) {
        db.setDone(t.id, !t.done);
        if (t.done) {
            Reminders.schedule(this, db.get(t.id)); // re-opened: re-arm if still pending
        } else {
            Reminders.cancel(this, t.id);
        }
        refresh();
    }

    // ----------------------------------------------------------- adapter

    private final class TaskAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return tasks.size();
        }

        @Override
        public Task getItem(int position) {
            return tasks.get(position);
        }

        @Override
        public long getItemId(int position) {
            return tasks.get(position).id;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Row row = convertView instanceof Row ? (Row) convertView : new Row(MainActivity.this);
            row.bind(getItem(position));
            return row;
        }
    }

    private final class Row extends FrameLayout {
        private final IconView check;
        private final TextView title;
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
        private float downX;
        private float downY;
        private boolean swiping;

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
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Pal.card);
            bg.setCornerRadius(dp(16));
            card.setBackground(bg);

            prioBar = new View(c);
            prioBar.setVisibility(View.GONE);
            LinearLayout.LayoutParams pbl = new LinearLayout.LayoutParams(dp(4), dp(32));
            pbl.leftMargin = dp(6);
            card.addView(prioBar, pbl);

            check = new IconView(c, IconView.CHECK);
            card.addView(check, new LinearLayout.LayoutParams(dp(48), dp(48)));

            LinearLayout col = new LinearLayout(c);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setPadding(0, dp(8), 0, dp(8));
            title = text("", 17, Pal.ink, false);
            col.addView(title);
            sub = text("", 13, Pal.muted, false);
            sub.setPadding(0, dp(2), 0, 0);
            col.addView(sub);
            subsBox = new LinearLayout(c);
            subsBox.setOrientation(LinearLayout.VERTICAL);
            col.addView(subsBox);
            textCol = col;
            card.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));

            delete = new IconView(c, IconView.CROSS);
            delete.setContentDescription("Delete task");
            card.addView(delete, new LinearLayout.LayoutParams(dp(48), dp(48)));

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
            swiping = false;
            card.animate().cancel();
            card.setTranslationX(0);
            reveal.setVisibility(View.INVISIBLE);

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
                long when = t.snooze > 0 ? t.snooze : t.due;
                boolean overdue = !t.done && when < System.currentTimeMillis();
                sub.setVisibility(View.VISIBLE);
                sub.setText(line);
                sub.setTextColor(overdue ? Pal.overdue : (t.done ? Pal.muted : Pal.accent));
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
                    db.setSubDone(s.id, !s.done);
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
