package app.privatetodo;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.widget.Toast;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Settings: appearance (system / light / dark), and for reminder notifications and alarms a sound
 * (any sound on the phone, or an audio file you pick) plus a volume slider. All stored on-device.
 */
public class SettingsActivity extends Activity {
    private float density;
    private static final int REQ_EXPORT = 4101;
    private static final int REQ_IMPORT = 4102;
    private int pickKind;
    private final TextView[] soundName = new TextView[3];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Pal.init(this);
        setTheme(Pal.themeRes());
        super.onCreate(savedInstanceState);
        density = getResources().getDisplayMetrics().density;
        setContentView(build());
        Fonts.apply(getWindow().getDecorView());
    }

    @Override
    protected void onPause() {
        Sound.stopPreview(); // ends any test sound
        super.onPause();
    }

    // ------------------------------------------------------------------ UI

    private View build() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Pal.bg);
        scroll.setFillViewport(true);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(12), dp(16), dp(32));
        scroll.addView(col, new ScrollView.LayoutParams(-1, -2));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        IconView back = new IconView(this, IconView.BACK);
        back.setContentDescription("Back");
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        header.addView(text("Settings", 30, Pal.ink, true));
        col.addView(header);

        // ---- appearance
        col.addView(sectionLabel("Appearance"));
        LinearLayout themeCard = card();
        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        String[] names = {"Auto", "Light", "Dark", "AMOLED"};
        int current = Prefs.theme(this);
        for (int i = 0; i < 4; i++) {
            final int mode = i;
            TextView chip = text(names[i], 14, i == current ? Pal.onSel : Pal.muted, true);
            chip.setGravity(Gravity.CENTER);
            chip.setMinHeight(dp(44));
            chip.setPadding(dp(6), 0, dp(6), 0);
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(dp(22));
            g.setColor(i == current ? Pal.sel : Pal.chip);
            chip.setBackground(g);
            chip.setOnClickListener(v -> {
                Prefs.setTheme(this, mode);
                recreate(); // onCreate re-reads the palette
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
            lp.rightMargin = dp(i < 3 ? 6 : 0);
            chips.addView(chip, lp);
        }
        themeCard.addView(chips);
        col.addView(themeCard);

        // ---- sounds
        col.addView(sectionLabel("Reminder notifications"));
        col.addView(soundCard(Prefs.NOTIF));
        col.addView(sectionLabel("Alarms"));
        col.addView(soundCard(Prefs.ALARM));
        col.addView(sectionLabel("Pomodoro"));
        col.addView(soundCard(Prefs.POMO));

        // ---- smart dates
        col.addView(sectionLabel("Smart dates"));
        col.addView(smartCard());

        // ---- full-screen alert: glow + test
        col.addView(sectionLabel("Full-screen alert"));
        LinearLayout alertCard = card();
        LinearLayout glowRow = new LinearLayout(this);
        glowRow.setOrientation(LinearLayout.HORIZONTAL);
        glowRow.setGravity(Gravity.CENTER_VERTICAL);
        glowRow.setMinimumHeight(dp(64));
        LinearLayout glowText = new LinearLayout(this);
        glowText.setOrientation(LinearLayout.VERTICAL);
        glowText.addView(text("Edge glow", 16, Pal.ink, true));
        glowText.addView(text("A soft colour light that travels round the screen edge while an alarm rings", 13, Pal.muted, false));
        glowRow.addView(glowText, new LinearLayout.LayoutParams(0, -2, 1f));
        ToggleView glowSwitch = new ToggleView(this);
        glowSwitch.set(Prefs.glow(this), false);
        glowSwitch.setListener(on -> Prefs.setGlow(this, on));
        glowRow.addView(glowSwitch, new LinearLayout.LayoutParams(dp(52), dp(32)));
        alertCard.addView(glowRow);
        alertCard.addView(backupRow("Test full-screen alert",
                "Shows the alarm screen right now with sample data", v -> testAlert(0)));
        alertCard.addView(backupRow("Test on the lock screen",
                "Rings in 10 seconds: press the power button to lock the phone first", v -> testAlert(10)));
        col.addView(alertCard);

        // ---- backup
        col.addView(sectionLabel("Backup"));
        LinearLayout backupCard = card();
        backupCard.addView(backupRow("Export backup",
                "Save all tasks, subtasks and notes to a file you choose", v -> {
                    String name = "private-todo-backup-"
                            + new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()) + ".json";
                    startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT)
                            .addCategory(Intent.CATEGORY_OPENABLE)
                            .setType("application/json")
                            .putExtra(Intent.EXTRA_TITLE, name), REQ_EXPORT);
                }));
        backupCard.addView(backupRow("Import backup",
                "Replace everything in the app with a backup file", v ->
                        new Dlg.Builder(this)
                                .setTitle("Import backup?")
                                .setMessage("This replaces all tasks and notes currently in the app.")
                                .setPositiveButton("Choose file", (d, w) ->
                                        startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT)
                                                .addCategory(Intent.CATEGORY_OPENABLE)
                                                .setType("*/*"), REQ_IMPORT))
                                .setNegativeButton("Cancel", null)
                                .show()));
        col.addView(backupCard);

        TextView note = text("Volume is relative to your phone's own notification and alarm volume, "
                + "so it can only turn a sound down. Sounds are read from your phone and never leave it.",
                13, Pal.muted, false);
        note.setPadding(dp(8), dp(16), dp(8), 0);
        col.addView(note);
        return scroll;
    }

    private LinearLayout soundCard(final int kind) {
        LinearLayout card = card();

        // Sound row
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(60));
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(text("Sound", 16, Pal.ink, true));
        soundName[kind] = text(Sound.describe(this, kind), 14, Pal.accent, false);
        labels.addView(soundName[kind]);
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView change = text("Change", 15, Pal.accent, true);
        change.setGravity(Gravity.CENTER);
        change.setMinHeight(dp(48));
        change.setMinWidth(dp(72));
        row.addView(change, new LinearLayout.LayoutParams(-2, -2));
        row.setOnClickListener(v -> chooseSound(kind));
        card.addView(row, new LinearLayout.LayoutParams(-1, -2));

        // Volume slider
        LinearLayout volHead = new LinearLayout(this);
        volHead.setOrientation(LinearLayout.HORIZONTAL);
        volHead.setPadding(0, dp(8), 0, 0);
        volHead.addView(text("Volume", 16, Pal.ink, true), new LinearLayout.LayoutParams(0, -2, 1f));
        final TextView pct = text(Prefs.volume(this, kind) + "%", 15, Pal.muted, true);
        volHead.addView(pct);
        card.addView(volHead);

        PillSlider bar = new PillSlider(this);
        bar.setValue(Prefs.volume(this, kind));
        bar.setContentDescription(kind == Prefs.ALARM ? "Alarm volume" : kind == Prefs.POMO ? "Pomodoro volume" : "Notification volume");
        bar.setListener(new PillSlider.Listener() {
            @Override
            public void onChange(int value) {
                pct.setText(value + "%");
                Prefs.setVolume(SettingsActivity.this, kind, value);
                Sound.setLiveVolume(value);
            }

            @Override
            public void onStart() {
                // Plays the sound while you slide so you hear the level.
                Sound.play(SettingsActivity.this, kind, true, 6_000L, null);
            }

            @Override
            public void onStop() {
                Sound.stopPreview();
            }
        });
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, dp(48));
        blp.topMargin = dp(6);
        blp.bottomMargin = dp(6);
        card.addView(bar, blp);

        TextView test = text("Play test sound", 15, Pal.accent, true);
        test.setGravity(Gravity.CENTER_VERTICAL);
        test.setMinHeight(dp(48));
        test.setOnClickListener(v -> Sound.play(this, kind, false, 6_000L, null));
        card.addView(test);
        return card;
    }

    private void chooseSound(final int kind) {
        pickKind = kind;
        String[] items = {"Sounds on this phone…", "Audio file…", "Phone default", "Silent"};
        new Dlg.Builder(this)
                .setTitle(kind == Prefs.ALARM ? "Alarm sound" : kind == Prefs.POMO ? "Pomodoro tune" : "Notification sound")
                .setItems(items, (d, which) -> {
                    switch (which) {
                        case 0:
                            SoundPicker.phone(this, kind, Prefs.sound(this, kind));
                            break;
                        case 1:
                            SoundPicker.file(this);
                            break;
                        case 2:
                            applySound(kind, "");
                            break;
                        default:
                            applySound(kind, Prefs.SILENT);
                            break;
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    protected void onActivityResult(int req, int result, Intent data) {
        super.onActivityResult(req, result, data);
        if ((req == REQ_EXPORT || req == REQ_IMPORT) && result == RESULT_OK && data != null
                && data.getData() != null) {
            if (req == REQ_EXPORT) doExport(data.getData());
            else doImport(data.getData());
            return;
        }
        String picked = SoundPicker.result(this, req, result, data);
        if (picked != null) applySound(pickKind, picked);
    }

    private LinearLayout toggleRow(String title, String sub, boolean on, ToggleView.Listener l) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(64));
        LinearLayout tx = new LinearLayout(this);
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.addView(text(title, 16, Pal.ink, true));
        tx.addView(text(sub, 13, Pal.muted, false));
        row.addView(tx, new LinearLayout.LayoutParams(0, -2, 1f));
        ToggleView sw = new ToggleView(this);
        sw.set(on, false);
        sw.setListener(l);
        row.addView(sw, new LinearLayout.LayoutParams(dp(52), dp(32)));
        return row;
    }

    /** "@sat 2pm" in the quick-add pill: on/off, trigger symbol, default time, ring style, shortcuts, live try-it. */
    private LinearLayout smartCard() {
        LinearLayout card = card();
        String trig = Prefs.smartTrigger(this);
        card.addView(toggleRow("Type a date in the task",
                "Write " + trig + "sat 2pm, " + trig + "tomorrow 7pm, " + trig + "next sunday 5am, "
                        + trig + "every monday 2pm or " + trig + "today 3:40pm after your task",
                Prefs.smartOn(this), on -> Prefs.setSmartOn(this, on)));
        card.addView(backupRow("Trigger symbol: " + trig,
                "Only text after this symbol is read as a date", v -> {
                    final android.widget.EditText et = new android.widget.EditText(this);
                    et.setText(Prefs.smartTrigger(this));
                    et.setSingleLine(true);
                    et.setSelectAllOnFocus(true);
                    et.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(2)});
                    LinearLayout box = new LinearLayout(this);
                    box.setPadding(dp(24), dp(8), dp(24), 0);
                    box.addView(et, new LinearLayout.LayoutParams(-1, -2));
                    new Dlg.Builder(this)
                            .setTitle("Trigger symbol")
                            .setMessage("One or two characters, for example @  #  !  ~  //")
                            .setView(box)
                            .setPositiveButton("Save", (d, w) -> {
                                Prefs.setSmartTrigger(this, et.getText().toString());
                                recreate();
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                }));
        int h = Prefs.smartHour(this);
        int mi = Prefs.smartMinute(this);
        String when = new SimpleDateFormat(android.text.format.DateFormat.is24HourFormat(this) ? "HH:mm" : "h:mm a",
                Locale.getDefault()).format(new Date(0, 0, 1, h, mi));
        card.addView(backupRow("Time when only a day is given: " + when,
                "Used for things like " + trig + "sat or " + trig + "tomorrow", v ->
                        new android.app.TimePickerDialog(this, (tp, hh, mm) -> {
                            Prefs.setSmartTime(this, hh, mm);
                            recreate();
                        }, Prefs.smartHour(this), Prefs.smartMinute(this),
                                android.text.format.DateFormat.is24HourFormat(this)).show()));
        card.addView(toggleRow("Ring as alarm",
                "Tasks made this way use the full-screen alarm instead of a normal notification",
                Prefs.smartAlarm(this), on -> Prefs.setSmartAlarm(this, on)));

        TextView sc = text("MY SHORTCUTS", 12, Pal.muted, true);
        sc.setLetterSpacing(0.12f);
        sc.setPadding(0, dp(14), 0, dp(2));
        card.addView(sc);
        card.addView(text("Short words that expand into any date phrase. Example: gym = every mon wed fri 6pm, "
                + "then type " + trig + "gym", 13, Pal.muted, false));
        final java.util.List<String[]> rules = new java.util.ArrayList<>();
        for (String line : Prefs.smartAliases(this).split("\n")) {
            int eq = line.indexOf('=');
            if (eq > 0) rules.add(new String[]{line.substring(0, eq).trim(), line.substring(eq + 1).trim()});
        }
        for (int i = 0; i < rules.size(); i++) {
            final int idx = i;
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(dp(48));
            row.addView(text(trig + rules.get(i)[0], 15, Pal.accent, true), new LinearLayout.LayoutParams(dp(110), -2));
            row.addView(text(rules.get(i)[1], 15, Pal.ink, false), new LinearLayout.LayoutParams(0, -2, 1f));
            row.setOnClickListener(v -> editRule(rules, idx));
            card.addView(row);
        }
        TextView add = text("+ Add shortcut", 15, Pal.accent, true);
        add.setMinHeight(dp(48));
        add.setGravity(Gravity.CENTER_VERTICAL);
        add.setOnClickListener(v -> editRule(rules, -1));
        card.addView(add);

        TextView tryHead = text("TRY IT", 12, Pal.muted, true);
        tryHead.setLetterSpacing(0.12f);
        tryHead.setPadding(0, dp(10), 0, dp(4));
        card.addView(tryHead);
        final android.widget.EditText tryIt = new android.widget.EditText(this);
        tryIt.setHint("buy milk " + trig + "tomorrow 7pm");
        tryIt.setSingleLine(true);
        tryIt.setTextSize(15);
        tryIt.setTextColor(Pal.ink);
        tryIt.setHintTextColor(Pal.muted);
        GradientDrawable tg = new GradientDrawable();
        tg.setColor(Pal.chip);
        tg.setCornerRadius(dp(14));
        tryIt.setBackground(tg);
        tryIt.setPadding(dp(14), 0, dp(14), 0);
        card.addView(tryIt, new LinearLayout.LayoutParams(-1, dp(48)));
        final TextView result = text("", 14, Pal.accent, true);
        result.setPadding(dp(4), dp(8), 0, dp(6));
        card.addView(result);
        tryIt.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence a, int b, int c, int d) { }
            @Override public void onTextChanged(CharSequence a, int b, int c, int d) { }
            @Override
            public void afterTextChanged(android.text.Editable e) {
                String in = e.toString();
                result.setText(in.trim().isEmpty() ? "" : SmartDate.describe(SettingsActivity.this,
                        SmartDate.parse(SettingsActivity.this, in, System.currentTimeMillis())));
            }
        });
        return card;
    }

    private void editRule(final java.util.List<String[]> rules, final int idx) {
        final android.widget.EditText name = new android.widget.EditText(this);
        name.setHint("shortcut, e.g. gym");
        name.setSingleLine(true);
        final android.widget.EditText means = new android.widget.EditText(this);
        means.setHint("meaning, e.g. every mon wed fri 6pm");
        means.setSingleLine(true);
        if (idx >= 0) {
            name.setText(rules.get(idx)[0]);
            means.setText(rules.get(idx)[1]);
        }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(8), dp(24), 0);
        box.addView(name, new LinearLayout.LayoutParams(-1, -2));
        box.addView(means, new LinearLayout.LayoutParams(-1, -2));
        Dlg.Builder b = new Dlg.Builder(this);
        b.setTitle(idx >= 0 ? "Edit shortcut" : "New shortcut")
                .setView(box)
                .setPositiveButton("Save", (d, w) -> {
                    String k = name.getText().toString().trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
                    String v = means.getText().toString().trim();
                    if (k.isEmpty() || v.isEmpty()) return;
                    if (idx >= 0) rules.set(idx, new String[]{k, v});
                    else rules.add(new String[]{k, v});
                    saveRules(rules);
                })
                .setNegativeButton("Cancel", null);
        if (idx >= 0) {
            b.setNeutralButton("Delete", (d, w) -> {
                rules.remove(idx);
                saveRules(rules);
            });
        }
        b.show();
    }

    private void saveRules(java.util.List<String[]> rules) {
        StringBuilder sb = new StringBuilder();
        for (String[] r : rules) sb.append(sb.length() > 0 ? "\n" : "").append(r[0]).append('=').append(r[1]);
        Prefs.setSmartAliases(this, sb.toString());
        recreate();
    }

    /** Makes a throw-away task with sample subtasks and rings it; AlarmActivity deletes it afterwards. */
    private void testAlert(int delaySeconds) {
        TaskDb db = new TaskDb(this);
        long id;
        try {
            long old = Prefs.testId(this);
            if (old >= 0) {
                Reminders.cancel(this, old);
                Reminders.cancelNotification(this, old);
                db.delete(old);
            }
            long due = System.currentTimeMillis() + delaySeconds * 1000L;
            id = db.add("Test reminder \u2014 check the glow", due, 0, 0, true);
            db.setAlarmMode(id, true, true);
            java.util.List<Sub> subs = new java.util.ArrayList<>();
            subs.add(new Sub(0, id, "Try snooze for 5 min", false));
            subs.add(new Sub(0, id, "Open a link: example.com", false));
            subs.add(new Sub(0, id, "Tap Done when finished", false));
            db.replaceSubs(id, subs);
            Prefs.setTestId(this, id);
            if (delaySeconds > 0) Reminders.schedule(this, db.get(id));
        } finally {
            db.close();
        }
        if (delaySeconds > 0) {
            Toast.makeText(this, "Lock the phone now \u2014 it rings in " + delaySeconds + " seconds",
                    Toast.LENGTH_LONG).show();
            return;
        }
        RingService.start(this, id, true);
        startActivity(new Intent(this, AlarmActivity.class).putExtra(Reminders.EXTRA_ID, id));
    }

    private LinearLayout backupRow(String title, String sub, View.OnClickListener l) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setMinimumHeight(dp(64));
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.addView(text(title, 16, Pal.ink, true));
        r.addView(text(sub, 13, Pal.muted, false));
        r.setOnClickListener(l);
        return r;
    }

    private void doExport(Uri uri) {
        try (OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
            out.write(Backup.export(this).getBytes(StandardCharsets.UTF_8));
            Toast.makeText(this, "Backup saved", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Could not save the backup", Toast.LENGTH_LONG).show();
        }
    }

    private void doImport(Uri uri) {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) > 0) buf.write(b, 0, n);
            int tasks = Backup.restore(this, new String(buf.toByteArray(), StandardCharsets.UTF_8));
            Toast.makeText(this, tasks < 0 ? "That is not a Private Todo backup"
                    : "Imported " + tasks + " tasks and your notes", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Could not read that file", Toast.LENGTH_LONG).show();
        }
    }

    private void applySound(int kind, String value) {
        Prefs.setSound(this, kind, value);
        soundName[kind].setText(Sound.describe(this, kind));
        Sound.play(this, kind, false, 6_000L, null); // preview what was just chosen
    }

    // ------------------------------------------------------------- helpers

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(8), dp(12), dp(8));
        GradientDrawable g = new GradientDrawable();
        g.setColor(Pal.card);
        g.setCornerRadius(dp(20));
        c.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        c.setLayoutParams(lp);
        return c;
    }

    private TextView sectionLabel(String s) {
        TextView t = text(s, 13, Pal.muted, true);
        t.setPadding(dp(8), dp(20), 0, dp(8));
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
