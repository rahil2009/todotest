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
    private static final int REQ_FONT = 4103;
    private int pickKind;
    private final TextView[] soundName = new TextView[3];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Pal.init(this);
        setTheme(Pal.themeRes());
        super.onCreate(savedInstanceState);
        density = getResources().getDisplayMetrics().density;
        page = getIntent().getIntExtra("page", 0);
        setContentView(build());
        Fonts.apply(getWindow().getDecorView());
    }

    private boolean resumedOnce;

    @Override
    protected void onResume() {
        super.onResume();
        if (resumedOnce && page == 0) { // tiles show live status, so refresh after a sub-page
            blocks.clear();
            blockWords.clear();
            setContentView(build());
            Fonts.apply(getWindow().getDecorView());
        }
        resumedOnce = true;
    }

    @Override
    protected void onPause() {
        Sound.stopPreview(); // ends any test sound
        super.onPause();
    }

    // ------------------------------------------------------------------ UI

    private int page; // 0 = main screen, 1 smart dates, 2 habit units, 3 full-screen alert, 4 backup, 5 layout, 6 notebook, 7 animations, 8 haptics
    private final java.util.List<View[]> blocks = new java.util.ArrayList<>();
    private final java.util.List<String> blockWords = new java.util.ArrayList<>();

    private void block(String words, View... views) {
        blocks.add(views);
        blockWords.add(words.toLowerCase(Locale.ROOT));
    }

    private View build() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Pal.bg);
        scroll.setFillViewport(true);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(12), dp(16), dp(32));
        scroll.addView(col, new ScrollView.LayoutParams(-1, -2));

        String[] titles = {"Settings", "Smart dates", "Habit units", "Full-screen alert", "Backup", "Main screen", "Notebook", "Tick animation", "Haptics", "Water reminders"};
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        IconView back = new IconView(this, IconView.BACK);
        back.setContentDescription("Back");
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        header.addView(text(titles[page], 30, Pal.ink, true));
        col.addView(header);

        if (page == 1) {
            col.addView(smartCard());
        } else if (page == 2) {
            col.addView(unitsCard());
        } else if (page == 5) {
            col.addView(layoutCard());
        } else if (page == 6) {
            col.addView(notebookCard());
        } else if (page == 7) {
            col.addView(tickCard());
        } else if (page == 8) {
            col.addView(hapticCard());
        } else if (page == 9) {
            col.addView(waterCard());
        } else if (page == 3) {
            col.addView(alertCard());
        } else if (page == 4) {
            col.addView(backupCard());
            TextView bnote = text("Backups are plain files you choose where to save. Nothing is sent anywhere.",
                    13, Pal.muted, false);
            bnote.setPadding(dp(8), dp(16), dp(8), 0);
            col.addView(bnote);
        } else {
            buildMain(col);
        }
        return scroll;
    }

    /** The main screen: search, the everyday cards inline, and tiles that open the bigger sections. */
    private void buildMain(LinearLayout col) {
        final android.widget.EditText search = new android.widget.EditText(this);
        search.setHint("Search settings");
        search.setSingleLine(true);
        search.setTextSize(16);
        search.setTextColor(Pal.ink);
        search.setHintTextColor(Pal.muted);
        GradientDrawable sg = new GradientDrawable();
        sg.setColor(Pal.card);
        sg.setCornerRadius(dp(25));
        search.setBackground(sg);
        search.setPadding(dp(20), 0, dp(20), 0);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, dp(50));
        slp.topMargin = dp(10);
        col.addView(search, slp);

        // ---- appearance
        View appLabel = sectionLabel("Appearance");
        col.addView(appLabel);
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
        block("appearance theme light dark amoled auto", appLabel, themeCard);

        // ---- sounds, inline
        View l1 = sectionLabel("Reminder notifications");
        View c1 = soundCard(Prefs.NOTIF);
        col.addView(l1);
        col.addView(c1);
        block("reminder notifications sound volume", l1, c1);
        View l2 = sectionLabel("Alarms");
        View c2 = soundCard(Prefs.ALARM);
        col.addView(l2);
        col.addView(c2);
        block("alarms alarm sound volume", l2, c2);
        View l3 = sectionLabel("Pomodoro");
        View c3 = soundCard(Prefs.POMO);
        col.addView(l3);
        col.addView(c3);
        block("pomodoro focus timer sound volume", l3, c3);

        // ---- tiles
        View moreLabel = sectionLabel("More");
        col.addView(moreLabel);
        final int units = HabitEditor.units(this).size();
        final String[] layoutNames = {"Current", "Two sections", "Switch", "", "Routine strip", "Routine dock"};
        Object[][] tiles = {
                {IconView.SCREEN, "Main screen", layoutNames[Math.min(5, Prefs.mainLayout(this))], 5,
                        "main screen layout test sections switch routine strip dock repeating one-off"},
                {IconView.LOOKS, "Tick animation", tickSummary(), 7,
                        "animations animation tick complete done confetti ring fireworks sweep rain celebrate"},
                {IconView.SWIPE_DONE, "Done haptics", Hap.NAMES[Hap.pick(this)], 8,
                        "haptics haptic vibration vibrate feedback completed done complete pixel"},
                {IconView.HABIT, "Water reminders", WaterAlert.enabled(this)
                        ? WaterAlert.times(this).size() + " reminders a day" : "Off", 9,
                        "water drink habit reminder alert full screen alarm hydrate times per day how many"},
                {IconView.NOTE, "Notebook", "Pin up to " + Prefs.maxPins(this), 6,
                        "notebook notes pin pinned limit top history versions recover restore"},
                {IconView.CLOCK, "Smart dates", Prefs.smartOn(this) ? "On \u00b7 " + Prefs.smartTrigger(this) : "Off", 1,
                        "smart dates natural language at symbol trigger shortcuts"},
                {IconView.HABIT, "Habit units", units + " units", 2, "habit units ml min quick amounts"},
                {IconView.BELL, "Full-screen alert", AlertFx.NAMES[Math.min(AlertFx.NAMES.length - 1, Prefs.alertAnim(this))], 3,
                        "full screen alert animation glow aurora test lock screen"},
                {IconView.LOCK, "Backup", "Export \u00b7 Import", 4, "backup export import restore"},
        };
        final java.util.List<View> tileViews = new java.util.ArrayList<>();
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        for (int r = 0; r < tiles.length; r++) {
            final Object[] t = tiles[r];
            LinearLayout tile = new LinearLayout(this);
            tile.setOrientation(LinearLayout.HORIZONTAL);
            tile.setGravity(Gravity.CENTER_VERTICAL);
            tile.setPadding(dp(14), dp(12), dp(16), dp(12));
            GradientDrawable tg = new GradientDrawable();
            tg.setColor(Pal.card);
            tg.setCornerRadius(dp(22));
            tile.setBackground(tg);
            LinearLayout badge = new LinearLayout(this);
            badge.setGravity(Gravity.CENTER);
            GradientDrawable bg2 = new GradientDrawable();
            bg2.setShape(GradientDrawable.OVAL);
            bg2.setColor(Pal.chip);
            badge.setBackground(bg2);
            badge.addView(new IconView(this, (Integer) t[0]), new LinearLayout.LayoutParams(dp(26), dp(26)));
            tile.addView(badge, new LinearLayout.LayoutParams(dp(44), dp(44)));
            TextView name = text((String) t[1], 16, Pal.ink, true);
            name.setPadding(dp(14), 0, dp(8), 0);
            tile.addView(name, new LinearLayout.LayoutParams(0, -2, 1f)); // the detail sits at the far right
            TextView detail = text((String) t[2], 14, Pal.muted, false);
            detail.setSingleLine(true);
            detail.setEllipsize(android.text.TextUtils.TruncateAt.END);
            detail.setGravity(Gravity.END);
            tile.addView(detail, new LinearLayout.LayoutParams(-2, -2));
            TextView chev = text("\u203A", 22, Pal.muted, true);
            chev.setPadding(dp(8), 0, 0, 0);
            tile.addView(chev);
            final int target = (Integer) t[3];
            tile.setOnClickListener(v -> {
                Hap.tick(v);
                startActivity(new Intent(this, SettingsActivity.class).putExtra("page", target));
            });
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, -2);
            tlp.setMargins(0, dp(5), 0, dp(5));
            grid.addView(tile, tlp);
            tileViews.add(tile);
            block(((String) t[1]) + " " + t[4], tile);
        }
        col.addView(grid);
        block("more", moreLabel);
        TextView note = text("Volume is relative to your phone's own notification and alarm volume, "
                + "so it can only turn a sound down. Sounds are read from your phone and never leave it.",
                13, Pal.muted, false);
        note.setPadding(dp(8), dp(16), dp(8), 0);
        col.addView(note);

        // ---- search: hides what does not match; tiles hide inside their row, so a row hides when both do
        search.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence a, int b, int c, int d) { }
            @Override public void onTextChanged(CharSequence a, int b, int c, int d) { }
            @Override
            public void afterTextChanged(android.text.Editable e) {
                String q = e.toString().trim().toLowerCase(Locale.ROOT);
                for (int i = 0; i < blocks.size(); i++) {
                    boolean show = q.isEmpty() || blockWords.get(i).contains(q);
                    for (View v : blocks.get(i)) v.setVisibility(show ? View.VISIBLE : View.GONE);
                }
                boolean anyTile = false;
                for (View t : tileViews) anyTile |= t.getVisibility() == View.VISIBLE;
                moreLabel.setVisibility(q.isEmpty() || anyTile ? View.VISIBLE : View.GONE);
            }
        });
    }

    /** Picks how repeating and one-off tasks are laid out on the main screen (for trying them out). */
    private LinearLayout layoutCard() {
        LinearLayout card = card();
        TextView intro = text("Try a layout for a few days, then keep the one you like. It applies to the Active tab; "
                + "the other tabs stay as they are.", 14, Pal.muted, false);
        intro.setPadding(0, dp(8), 0, dp(8));
        card.addView(intro);
        final int[] ids = {0, 1, 2, 4, 5};
        String[] names = {"Current", "Two sections", "Switch", "Routine strip", "Routine dock"};
        String[] desc = {
                "Everything in one list, as before.",
                "One-off tasks first, repeating tasks below in tinted rows. Each section collapses.",
                "A switch above the list: All, One-off or Repeating. A line tells you what is hidden.",
                "Repeating tasks become tiles across the top. The list below is one-off tasks only.",
                "Repeating tasks wait in a pull-up dock above the add bar. The list is one-off tasks only."};
        int cur = Prefs.mainLayout(this);
        for (int i = 0; i < ids.length; i++) {
            final int id = ids[i];
            boolean on = cur == id;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(12), dp(12), dp(12));
            GradientDrawable rg = new GradientDrawable();
            rg.setCornerRadius(dp(16));
            rg.setColor(on ? Pal.chip : android.graphics.Color.TRANSPARENT);
            if (on) rg.setStroke(dp(2), Pal.accent);
            row.setBackground(rg);
            TextView dot = text(on ? "\u25CF" : "\u25CB", 20, on ? Pal.accent : Pal.muted, true);
            dot.setGravity(Gravity.CENTER);
            row.addView(dot, new LinearLayout.LayoutParams(dp(32), -2));
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.addView(text(names[i], 16, Pal.ink, true));
            TextView d = text(desc[i], 13, Pal.muted, false);
            d.setPadding(0, dp(2), 0, 0);
            col.addView(d);
            row.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
            row.setOnClickListener(v -> {
                Hap.tick(v);
                Prefs.setMainLayout(this, id);
                recreate();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = dp(4);
            card.addView(row, lp);
        }
        return card;
    }

    private String tickSummary() {
        return TickFx.NAMES[TickFx.style(this)];
    }

    /** Tick animation: tap a style to use it (and see it); Test plays one without choosing it. */
    private LinearLayout tickCard() {
        final LinearLayout card = card();
        TextView intro = text("What plays when you tick a task off on the main screen. Tap a style to use it; "
                + "Test shows it without changing your choice. Nothing runs between ticks.", 14, Pal.muted, false);
        intro.setPadding(0, dp(8), 0, dp(8));
        card.addView(intro);
        final int n = TickFx.NAMES.length;
        final View[] rows = new View[n];
        final View[] icons = new View[n];
        final TextView[] marks = new TextView[n];
        final GradientDrawable[] bgs = new GradientDrawable[n];
        final Runnable sync = () -> {
            int cur = TickFx.style(this);
            for (int i = 0; i < n; i++) {
                boolean on = i == cur;
                marks[i].setText(on ? "\u2713 In use" : "");
                bgs[i].setStroke(on ? dp(2) : 0, Pal.accent);
            }
        };
        for (int i = 0; i < n; i++) {
            final int style = i;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(dp(68));
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(dp(16));
            g.setColor(Pal.bg);
            row.setBackground(g);
            bgs[i] = g;
            IconView ic = new IconView(this, IconView.CHECK);
            ic.setOn(i != 0);
            ic.setClickable(false);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(44), dp(48));
            ilp.leftMargin = dp(6);
            row.addView(ic, ilp);
            icons[i] = ic;
            LinearLayout tx = new LinearLayout(this);
            tx.setOrientation(LinearLayout.VERTICAL);
            tx.setPadding(dp(8), dp(10), dp(8), dp(10));
            tx.addView(text((i == 0 ? "" : i + ". ") + TickFx.NAMES[i], 16, Pal.ink, true));
            TextView d = text(TickFx.DESCS[i], 12, Pal.muted, false);
            d.setPadding(0, dp(2), 0, 0);
            tx.addView(d);
            TextView mark = text("", 12, Pal.accent, true);
            mark.setPadding(0, dp(2), 0, 0);
            tx.addView(mark);
            marks[i] = mark;
            row.addView(tx, new LinearLayout.LayoutParams(0, -2, 1f));
            if (i > 0) {
                TextView test = text("Test", 13, Pal.accent, true);
                test.setGravity(Gravity.CENTER);
                test.setPadding(dp(14), 0, dp(14), 0);
                GradientDrawable tg = new GradientDrawable();
                tg.setCornerRadius(dp(18));
                tg.setColor(Pal.chip);
                test.setBackground(tg);
                test.setOnClickListener(v -> {
                    Hap.tick(v);
                    TickFx.play(this, icons[style], rows[style], style);
                });
                LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-2, dp(36));
                tlp.rightMargin = dp(10);
                row.addView(test, tlp);
            }
            rows[i] = row;
            row.setOnClickListener(v -> {
                Hap.tick(v);
                TickFx.setStyle(this, style);
                sync.run();
                if (style > 0) TickFx.play(this, icons[style], rows[style], style);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = dp(8);
            card.addView(row, lp);
        }
        sync.run();
        Fonts.apply(card);
        return card;
    }

    /** Done haptics: tap a pattern to feel it and use it. */
    private LinearLayout hapticCard() {
        final LinearLayout card = card();
        final LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        TextView intro = text("What your phone does when you complete a task. Tap a pattern to feel it and use it. "
                + "These use the phone's own vibration motor, in the style of the Pixel; phones without those effects "
                + "play a simpler version.", 14, Pal.muted, false);
        intro.setPadding(0, dp(8), 0, dp(8));
        card.addView(intro);
        card.addView(list);
        final Runnable[] fill = new Runnable[1];
        fill[0] = () -> {
            list.removeAllViews();
            int cur = Hap.pick(this);
            for (int i = 0; i < Hap.NAMES.length; i++) {
                final int id = i;
                boolean on = cur == id;
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(12), dp(12), dp(12), dp(12));
                GradientDrawable rg = new GradientDrawable();
                rg.setCornerRadius(dp(16));
                rg.setColor(on ? Pal.chip : android.graphics.Color.TRANSPARENT);
                if (on) rg.setStroke(dp(2), Pal.accent);
                row.setBackground(rg);
                TextView dot = text(on ? "\u25CF" : "\u25CB", 20, on ? Pal.accent : Pal.muted, true);
                dot.setGravity(Gravity.CENTER);
                row.addView(dot, new LinearLayout.LayoutParams(dp(32), -2));
                LinearLayout tx = new LinearLayout(this);
                tx.setOrientation(LinearLayout.VERTICAL);
                tx.addView(text(Hap.NAMES[i], 16, Pal.ink, true));
                TextView d = text(Hap.DESCS[i], 13, Pal.muted, false);
                d.setPadding(0, dp(2), 0, 0);
                tx.addView(d);
                row.addView(tx, new LinearLayout.LayoutParams(0, -2, 1f));
                row.setOnClickListener(v -> {
                    Hap.setPick(this, id);
                    fill[0].run();
                    Hap.play(list, id);
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                lp.bottomMargin = dp(4);
                list.addView(row, lp);
            }
            Fonts.apply(list);
        };
        fill[0].run();
        return card;
    }

    /** Notebook settings: how many notes may be pinned to the top (1 to 20). */
    private LinearLayout notebookCard() {
        LinearLayout card = card();
        card.addView(text("Pinned notes", 16, Pal.ink, true));
        TextView sub = text("Pinned notes stay at the top of the notebook. Choose how many you can pin at once, "
                + "from 1 to 20. Lowering it never unpins notes you already pinned.", 13, Pal.muted, false);
        sub.setPadding(0, dp(2), 0, dp(8));
        card.addView(sub);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        final TextView num = text(String.valueOf(Prefs.maxPins(this)), 28, Pal.ink, true);
        num.setGravity(Gravity.CENTER);
        final TextView minus = pinStep("\u2212");
        final TextView plus = pinStep("+");
        View.OnClickListener step = v -> {
            Hap.tick(v);
            int n = Prefs.maxPins(this) + (v == plus ? 1 : -1);
            n = Math.max(1, Math.min(Prefs.PINS_MAX, n));
            Prefs.setMaxPins(this, n);
            num.setText(String.valueOf(n));
        };
        minus.setOnClickListener(step);
        plus.setOnClickListener(step);
        row.addView(minus, new LinearLayout.LayoutParams(dp(52), dp(52)));
        row.addView(num, new LinearLayout.LayoutParams(0, -2, 1f));
        row.addView(plus, new LinearLayout.LayoutParams(dp(52), dp(52)));
        card.addView(row);

        // ---- note history
        TextView htitle = text("Note history", 16, Pal.ink, true);
        htitle.setPadding(0, dp(20), 0, 0);
        card.addView(htitle);
        TextView hsub = text("Every note keeps its earlier versions on this phone, even after you delete it. "
                + "Open the history to bring an older version back. It is also inside each note (clock icon).",
                13, Pal.muted, false);
        hsub.setPadding(0, dp(2), 0, dp(10));
        card.addView(hsub);
        TextView hopen = text("Open note history", 15, Pal.onAccent, true);
        hopen.setGravity(Gravity.CENTER);
        GradientDrawable hg = new GradientDrawable();
        hg.setCornerRadius(dp(24));
        hg.setColor(Pal.accent);
        hopen.setBackground(hg);
        hopen.setOnClickListener(v -> {
            Hap.tick(v);
            startActivity(new Intent(this, NoteHistoryActivity.class));
        });
        card.addView(hopen, new LinearLayout.LayoutParams(-1, dp(48)));

        // ---- colour of pinned notes' previews
        TextView ctitle = text("Pinned note colour", 16, Pal.ink, true);
        ctitle.setPadding(0, dp(20), 0, 0);
        card.addView(ctitle);
        TextView csub = text("Fills the whole preview of every pinned note. Tap one to try it; open the "
                + "notebook to see it.", 13, Pal.muted, false);
        csub.setPadding(0, dp(2), 0, dp(10));
        card.addView(csub);
        final View[] swatches = new View[PinTint.COUNT];
        final TextView[] labels = new TextView[PinTint.COUNT];
        final Runnable paint = () -> {
            int cur = PinTint.style(this);
            for (int i = 0; i < PinTint.COUNT; i++) {
                GradientDrawable g = new GradientDrawable();
                g.setCornerRadius(dp(12));
                PinTint.paint(g, i, 0);
                g.setStroke(dp(i == cur ? 3 : 1), i == cur ? Pal.ink : Pal.chip);
                swatches[i].setBackground(g);
                labels[i].setTextColor(i == cur ? Pal.ink : Pal.muted);
                labels[i].setTypeface(i == cur ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            }
        };
        for (int start = 0; start < PinTint.COUNT; start += 5) {
            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            for (int i = start; i < start + 5 && i < PinTint.COUNT; i++) {
                final int idx = i;
                LinearLayout cell = new LinearLayout(this);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER_HORIZONTAL);
                cell.setPadding(dp(3), dp(4), dp(3), dp(4));
                View sw = new View(this);
                swatches[i] = sw;
                cell.addView(sw, new LinearLayout.LayoutParams(-1, dp(46)));
                TextView lab = text(PinTint.NAMES[i], 11, Pal.muted, false);
                lab.setGravity(Gravity.CENTER);
                lab.setPadding(0, dp(4), 0, 0);
                labels[i] = lab;
                cell.addView(lab);
                cell.setOnClickListener(v -> {
                    Hap.tick(v);
                    Prefs.setPinColor(this, idx);
                    paint.run();
                });
                line.addView(cell, new LinearLayout.LayoutParams(0, -2, 1f));
            }
            card.addView(line);
        }
        paint.run();

        // ---- the font new notes are written in
        NoteFonts.init(this);
        TextView ftitle = text("Note font", 16, Pal.ink, true);
        ftitle.setPadding(0, dp(20), 0, 0);
        card.addView(ftitle);
        TextView fsub = text("The font every note is shown in. Inside a note, the Font tab can change the "
                + "font of any part of the text.", 13, Pal.muted, false);
        fsub.setPadding(0, dp(2), 0, dp(10));
        card.addView(fsub);
        final int nf = NoteFonts.count();
        final TextView[] chips = new TextView[nf];
        final TextView sample = text("The quick brown fox jumps over the lazy dog", 17, Pal.ink, false);
        sample.setTag("keepfont");
        final Runnable fpaint = () -> {
            int cur = NoteFonts.noteDefault(this);
            for (int i = 0; i < nf; i++) {
                if (chips[i] == null) continue;
                GradientDrawable g = new GradientDrawable();
                g.setCornerRadius(dp(20));
                g.setColor(i == cur ? Pal.sel : Pal.chip);
                chips[i].setBackground(g);
                chips[i].setTextColor(i == cur ? Pal.onSel : Pal.ink);
            }
            sample.setTypeface(NoteFonts.face(cur));
        };
        FlowLayout fl = new FlowLayout(this, dp(8), dp(8));
        for (int i = 0; i < nf; i++) {
            if (!NoteFonts.shown(i)) continue;
            final int idx = i;
            TextView c = text(NoteFonts.name(i), 15, Pal.ink, false);
            c.setTypeface(NoteFonts.face(i));
            c.setTag("keepfont");
            c.setGravity(Gravity.CENTER);
            c.setPadding(dp(14), 0, dp(14), 0);
            c.setOnClickListener(v -> {
                Hap.tick(v);
                NoteFonts.setNoteDefault(this, idx);
                fpaint.run();
            });
            if (i >= NoteFonts.BUILTIN) {
                c.setOnLongClickListener(v -> {
                    new Dlg.Builder(this)
                            .setTitle("Remove " + NoteFonts.name(idx) + "?")
                            .setMessage("Text written in it will show in the app font.")
                            .setPositiveButton("Remove", (d, w) -> {
                                NoteFonts.removeFont(this, idx);
                                recreate();
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                    return true;
                });
            }
            chips[i] = c;
            fl.addView(c, new android.view.ViewGroup.LayoutParams(-2, dp(42)));
        }
        card.addView(fl);
        TextView imp = text("+ Import a font (.ttf or .otf)", 15, Pal.accent, true);
        imp.setGravity(Gravity.CENTER);
        GradientDrawable ig = new GradientDrawable();
        ig.setCornerRadius(dp(24));
        ig.setColor(Pal.chip);
        imp.setBackground(ig);
        imp.setOnClickListener(v -> {
            Hap.tick(v);
            startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("*/*").putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"font/ttf", "font/otf",
                            "font/sfnt", "application/x-font-ttf", "application/x-font-otf", "application/font-sfnt",
                            "application/octet-stream"}), REQ_FONT);
        });
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(-1, dp(48));
        ilp.topMargin = dp(12);
        card.addView(imp, ilp);
        TextView ihelp = text("Pick a font file from your phone. It is copied into the app and never leaves the phone. "
                + "Press and hold an imported font to remove it. Imported fonts are not part of backups.",
                12, Pal.muted, false);
        ihelp.setPadding(0, dp(6), 0, 0);
        card.addView(ihelp);
        sample.setPadding(0, dp(12), 0, dp(4));
        card.addView(sample);
        fpaint.run();
        return card;
    }

    /** Water reminders: full-screen alarm-style alerts for the Drink water habit. */
    private LinearLayout waterCard() {
        final LinearLayout card = card();
        card.addView(toggleRow("Water reminders",
                "A full-screen alert like an alarm at the times below, with one-tap buttons to log a drink. "
                        + "It stays quiet once today's water goal is reached.",
                WaterAlert.enabled(this), on -> {
                    WaterAlert.setEnabled(this, on);
                    if (on) Toast.makeText(this, "Water reminders on", Toast.LENGTH_SHORT).show();
                }));

        final Runnable[] fill = new Runnable[1];
        final TextView[] numRef = new TextView[1];

        TextView rtitle = text("Reminders per day", 16, Pal.ink, true);
        rtitle.setPadding(0, dp(18), 0, 0);
        card.addView(rtitle);
        TextView rsub = text("How many times a day it reminds you (1 to 8), spread over the day. "
                + "Changing this re-spreads the times below; you can still edit any time by hand. "
                + "The sound plays a few times and the screen stays until you log a drink, snooze or dismiss.",
                13, Pal.muted, false);
        rsub.setPadding(0, dp(2), 0, dp(8));
        card.addView(rsub);
        LinearLayout rrow = new LinearLayout(this);
        rrow.setOrientation(LinearLayout.HORIZONTAL);
        rrow.setGravity(Gravity.CENTER_VERTICAL);
        final TextView num = text(String.valueOf(WaterAlert.perDay(this)), 28, Pal.ink, true);
        num.setGravity(Gravity.CENTER);
        numRef[0] = num;
        final TextView minus = pinStep("\u2212");
        final TextView plus = pinStep("+");
        View.OnClickListener step = v -> {
            Hap.tick(v);
            WaterAlert.setPerDay(this, WaterAlert.perDay(this) + (v == plus ? 1 : -1));
            fill[0].run();
        };
        minus.setOnClickListener(step);
        plus.setOnClickListener(step);
        rrow.addView(minus, new LinearLayout.LayoutParams(dp(52), dp(52)));
        rrow.addView(num, new LinearLayout.LayoutParams(0, -2, 1f));
        rrow.addView(plus, new LinearLayout.LayoutParams(dp(52), dp(52)));
        card.addView(rrow);

        card.addView(toggleRow("Water animation",
                "The live water filling up behind the drink buttons on the alert screen, like in your Drink water habit. "
                        + "Turn it off for a plain screen.",
                WaterAlert.animation(this), on -> WaterAlert.setAnimation(this, on)));

        TextView test = text("Test it now", 16, Pal.onAccent, true);
        test.setGravity(Gravity.CENTER);
        GradientDrawable tg = new GradientDrawable();
        tg.setCornerRadius(dp(26));
        tg.setColor(Pal.accent);
        test.setBackground(tg);
        test.setOnClickListener(v -> {
            Hap.confirm(v);
            WaterAlert.fire(this, true);
        });
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, dp(52));
        tlp.topMargin = dp(18);
        card.addView(test, tlp);

        TextView note = text("Uses your Drink water habit (any habit named water or counted in ml, l, oz, glasses or cups). "
                + "The sound, its volume and the alert animation are the ones chosen for alarms in Settings. "
                + "If nothing rings, allow notifications and full-screen alerts for this app in your phone's settings.",
                12, Pal.muted, false);
        note.setPadding(0, dp(12), 0, dp(6));
        card.addView(note);
        TextView ttitle = text("Times", 16, Pal.ink, true);
        ttitle.setPadding(0, dp(20), 0, dp(4));
        card.addView(ttitle);
        final LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        card.addView(box);
        fill[0] = () -> {
            box.removeAllViews();
            final java.util.List<Integer> times = WaterAlert.times(this);
            if (numRef[0] != null) numRef[0].setText(String.valueOf(Math.max(1, times.size())));
            for (int i = 0; i < times.size(); i++) {
                final int minutes = times.get(i);
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                GradientDrawable rg = new GradientDrawable();
                rg.setCornerRadius(dp(16));
                rg.setColor(Pal.bg);
                row.setBackground(rg);
                row.setMinimumHeight(dp(52));
                TextView when = text(WaterAlert.clock(this, minutes), 18, Pal.ink, true);
                when.setPadding(dp(16), 0, 0, 0);
                row.addView(when, new LinearLayout.LayoutParams(0, -2, 1f));
                TextView change = text("Change", 13, Pal.accent, true);
                change.setGravity(Gravity.CENTER);
                change.setPadding(dp(12), 0, dp(12), 0);
                row.addView(change, new LinearLayout.LayoutParams(-2, dp(44)));
                TextView del = text("\u2715", 16, Pal.muted, true);
                del.setGravity(Gravity.CENTER);
                del.setContentDescription("Remove this time");
                row.addView(del, new LinearLayout.LayoutParams(dp(48), dp(44)));
                View.OnClickListener edit = v -> {
                    Hap.tick(v);
                    new android.app.TimePickerDialog(this, (tp, h, m) -> {
                        java.util.List<Integer> now = WaterAlert.times(this);
                        now.remove(Integer.valueOf(minutes));
                        now.add(h * 60 + m);
                        WaterAlert.setTimes(this, now);
                        fill[0].run();
                    }, minutes / 60, minutes % 60, android.text.format.DateFormat.is24HourFormat(this)).show();
                };
                when.setOnClickListener(edit);
                change.setOnClickListener(edit);
                del.setOnClickListener(v -> {
                    Hap.tick(v);
                    java.util.List<Integer> now = WaterAlert.times(this);
                    now.remove(Integer.valueOf(minutes));
                    WaterAlert.setTimes(this, now);
                    fill[0].run();
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                lp.bottomMargin = dp(6);
                box.addView(row, lp);
            }
            if (times.size() < WaterAlert.MAX_TIMES) {
                TextView add = text("+ Add a time", 15, Pal.accent, true);
                add.setGravity(Gravity.CENTER);
                GradientDrawable ag = new GradientDrawable();
                ag.setCornerRadius(dp(26));
                ag.setColor(Pal.chip);
                add.setBackground(ag);
                add.setOnClickListener(v -> {
                    Hap.tick(v);
                    new android.app.TimePickerDialog(this, (tp, h, m) -> {
                        java.util.List<Integer> now = WaterAlert.times(this);
                        now.add(h * 60 + m);
                        WaterAlert.setTimes(this, now);
                        fill[0].run();
                    }, 12, 0, android.text.format.DateFormat.is24HourFormat(this)).show();
                });
                box.addView(add, new LinearLayout.LayoutParams(-1, dp(48)));
            }
            Fonts.apply(box);
        };
        fill[0].run();

        return card;
    }

    private TextView pinStep(String label) {
        TextView t = text(label, 26, Pal.ink, true);
        t.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(Pal.chip);
        t.setBackground(g);
        return t;
    }

    private LinearLayout alertCard() {
        LinearLayout alertCard = card();
        alertCard.addView(text("Animation", 16, Pal.ink, true));
        alertCard.addView(text("Plays behind the alarm screen. Tap one to preview it.", 13, Pal.muted, false));
        final android.widget.FrameLayout preview = new android.widget.FrameLayout(this);
        GradientDrawable pbg = new GradientDrawable();
        pbg.setColor(0xFF111513);
        pbg.setCornerRadius(dp(18));
        preview.setBackground(pbg);
        preview.setClipToOutline(true);
        final TextView[] fxChips = new TextView[AlertFx.NAMES.length];
        final Runnable showFx = () -> {
            preview.removeAllViews();
            int cur = Prefs.alertAnim(this);
            View fx = AlertFx.create(this, cur);
            if (fx != null) preview.addView(fx, new android.widget.FrameLayout.LayoutParams(-1, -1));
            TextView clock = text("9:41", 40, 0xFFFFFFFF, true);
            clock.setGravity(Gravity.CENTER);
            preview.addView(clock, new android.widget.FrameLayout.LayoutParams(-1, -1));
            for (int i = 0; i < fxChips.length; i++) {
                boolean on = i == cur;
                fxChips[i].setTextColor(on ? Pal.onSel : Pal.muted);
                GradientDrawable cg = new GradientDrawable();
                cg.setCornerRadius(dp(18));
                cg.setColor(on ? Pal.sel : Pal.chip);
                fxChips[i].setBackground(cg);
            }
        };
        FlowLayout fxRow = new FlowLayout(this, dp(6), dp(6));
        for (int i = 0; i < AlertFx.NAMES.length; i++) {
            final int mode = i;
            TextView chip = text(AlertFx.NAMES[i], 14, Pal.muted, true);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(14), 0, dp(14), 0);
            chip.setMinHeight(dp(40));
            chip.setOnClickListener(v -> {
                Hap.tick(v);
                Prefs.setAlertAnim(this, mode);
                showFx.run();
            });
            fxChips[i] = chip;
            fxRow.addView(chip, new android.view.ViewGroup.LayoutParams(-2, dp(40)));
        }
        LinearLayout.LayoutParams frlp = new LinearLayout.LayoutParams(-1, -2);
        frlp.topMargin = dp(10);
        alertCard.addView(fxRow, frlp);
        LinearLayout.LayoutParams plp2 = new LinearLayout.LayoutParams(-1, dp(150));
        plp2.topMargin = dp(10);
        plp2.bottomMargin = dp(6);
        alertCard.addView(preview, plp2);
        showFx.run();
        alertCard.addView(backupRow("Test full-screen alert",
                "Shows the alarm screen right now with sample data", v -> testAlert(0)));
        alertCard.addView(backupRow("Test on the lock screen",
                "Rings in 10 seconds: press the power button to lock the phone first", v -> testAlert(10)));
        return alertCard;
    }

    private LinearLayout backupCard() {
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
        return backupCard;
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
        if (req == REQ_FONT) {
            if (result == RESULT_OK && data != null && data.getData() != null) {
                String nm = NoteFonts.importFont(this, data.getData());
                if (nm == null) {
                    Toast.makeText(this, "That file is not a usable font (use .ttf or .otf)", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, nm + " added", Toast.LENGTH_SHORT).show();
                    recreate();
                }
            }
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
        card.addView(toggleRow("Smart dates in new tasks",
                "Write the task, then " + trig + " and when: \u201CCall mum " + trig + "sat 2pm\u201D. The date becomes the "
                        + "reminder and is removed from the title.",
                Prefs.smartOn(this), on -> Prefs.setSmartOn(this, on)));
        TextView guide = text("Read the guide", 16, Pal.onAccent, true);
        guide.setGravity(Gravity.CENTER);
        GradientDrawable guideBg = new GradientDrawable();
        guideBg.setColor(Pal.accent);
        guideBg.setCornerRadius(dp(26));
        guide.setBackground(guideBg);
        guide.setOnClickListener(v -> {
            Hap.tick(v);
            startActivity(new Intent(this, SmartGuideActivity.class));
        });
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(-1, dp(50));
        glp.topMargin = dp(4);
        glp.bottomMargin = dp(6);
        card.addView(guide, glp);
        card.addView(text("Days, exact dates like " + trig + "12 may 2027, times, \u201Cin 3 hours\u201D and repeats like "
                + trig + "every mon wed fri 6pm, each with what it means right now.", 13, Pal.muted, false));
        card.addView(backupRow("Trigger symbol: " + trig,
                "Dates are only read after this symbol. Tap to change it", v -> {
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
        card.addView(backupRow("Default time: " + when,
                "Used when you give a day but no time, like " + trig + "sat or " + trig + "12 may 2027", v ->
                        new android.app.TimePickerDialog(this, (tp, hh, mm) -> {
                            Prefs.setSmartTime(this, hh, mm);
                            recreate();
                        }, Prefs.smartHour(this), Prefs.smartMinute(this),
                                android.text.format.DateFormat.is24HourFormat(this)).show()));
        card.addView(toggleRow("Ring as a full-screen alarm",
                "Off: a normal notification. On: tasks made with " + trig + "dates ring the full-screen alarm",
                Prefs.smartAlarm(this), on -> Prefs.setSmartAlarm(this, on)));

        TextView sc = text("MY SHORTCUTS", 12, Pal.muted, true);
        sc.setLetterSpacing(0.12f);
        sc.setPadding(0, dp(14), 0, dp(2));
        card.addView(sc);
        card.addView(text("A shortcut is one word that stands for a whole date phrase. Add gym = every mon wed fri 6pm, then "
                + "type \u201CWorkout " + trig + "gym\u201D. Tap a shortcut to change it.", 13, Pal.muted, false));
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

    /** The units offered when you set up a habit, each with its one-tap amounts. */
    private LinearLayout unitsCard() {
        LinearLayout card = card();
        card.addView(text("Units and quick amounts for habits, e.g. ml with +100 +250 +500. "
                + "Tap one to change it.", 13, Pal.muted, false));
        final Runnable[] redraw = new Runnable[1];
        final java.util.List<String[]> list = HabitEditor.units(this);
        for (int i = 0; i < list.size(); i++) {
            final int idx = i;
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(dp(48));
            row.addView(text(list.get(i)[0], 15, Pal.accent, true), new LinearLayout.LayoutParams(dp(90), -2));
            String am = list.get(i)[1].isEmpty() ? "no quick amounts" : "+" + list.get(i)[1].replaceAll("[,;\\s]+", "  +");
            row.addView(text(am, 14, Pal.ink, false), new LinearLayout.LayoutParams(0, -2, 1f));
            row.setOnClickListener(v -> HabitEditor.editUnit(this, idx, () -> recreate()));
            card.addView(row);
        }
        TextView add = text("+ Add unit", 15, Pal.accent, true);
        add.setMinHeight(dp(48));
        add.setGravity(Gravity.CENTER_VERTICAL);
        add.setOnClickListener(v -> HabitEditor.editUnit(this, -1, () -> recreate()));
        card.addView(add);
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
                    : "Imported " + tasks + " tasks, notes, habits and settings", Toast.LENGTH_LONG).show();
            if (tasks >= 0) recreate(); // pick up the restored theme and settings
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
