package app.privatetodo;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** Links shared into the app from the Android Share menu. One permanent card per share, newest first. */
public class SharedActivity extends Activity {
    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(base);
        Pal.override(this, base); // the app's Light / Dark choice, not the phone's
    }

    static final String EXTRA_NEW = "new_id";

    private TaskDb db;
    private float density;
    private int builtDark;
    private LinearLayout col;
    private long highlight;
    private long curFolder = -1; // -1 = all
    private FolderDial dial;
    private android.widget.HorizontalScrollView chipScroll;
    private LinearLayout chipRow;
    private final List<Runnable> savers = new ArrayList<>();
    private List<Folder> folders = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Pal.init(this);
        PaperTheme.apply(this);
        builtDark = Pal.themeRes();
        setTheme(Pal.themeRes());
        super.onCreate(savedInstanceState);
        density = getResources().getDisplayMetrics().density;
        db = new TaskDb(this);
        highlight = getIntent().getLongExtra(EXTRA_NEW, 0);
        setContentView(build());
    }

    @Override
    protected void onResume() {
        super.onResume();
        Lock.guard(this, Lock.NOTES);
        Pal.init(this);
        PaperTheme.apply(this);
        if (Pal.themeRes() != builtDark) {
            recreate();
            return;
        }
        refresh();
    }

    @Override
    protected void onPause() {
        saveAll();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        db.close();
        super.onDestroy();
    }

    private void saveAll() {
        for (Runnable r : savers) r.run();
    }

    private View build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Pal.bg);
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(12), dp(12), dp(16), 0);
        IconView back = new IconView(this, IconView.BACK);
        back.setContentDescription("Back");
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        header.addView(text("Shared notes", 28, Pal.ink, true), new LinearLayout.LayoutParams(0, -2, 1f));
        root.addView(header);
        TextView info = text("Pick Private Todo in any app's Share menu and the link lands here.",
                13, Pal.muted, false);
        info.setPadding(dp(24), dp(4), dp(24), dp(8));
        root.addView(info);
        chipScroll = new android.widget.HorizontalScrollView(this);
        chipScroll.setHorizontalScrollBarEnabled(false);
        chipRow = new LinearLayout(this);
        chipRow.setPadding(dp(14), 0, dp(14), dp(6));
        chipScroll.addView(chipRow);
        root.addView(chipScroll, new LinearLayout.LayoutParams(-1, -2));
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), 0, dp(16), dp(32));
        sv.addView(col);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        android.widget.FrameLayout frame = new android.widget.FrameLayout(this);
        frame.addView(root, new android.widget.FrameLayout.LayoutParams(-1, -1));
        dial = new FolderDial(this);
        dial.setVisibility(View.GONE);
        frame.addView(dial, new android.widget.FrameLayout.LayoutParams(-1, -1));
        return frame;
    }

    private void refresh() {
        col.removeAllViews();
        savers.clear();
        folders = db.sharedFolders();
        boolean known = curFolder == -1 || curFolder == 0;
        for (Folder f : folders) if (f.id == curFolder) known = true;
        if (!known) curFolder = -1;
        buildChips();
        List<TaskDb.Shared> all = new ArrayList<>();
        for (TaskDb.Shared x : db.shared()) if (curFolder == -1 || x.folder == curFolder) all.add(x);
        if (all.isEmpty()) {
            TextView e = text(curFolder == -1 ? "Nothing shared yet" : "Nothing in here. Hold a card and drag to move one in.", 16, Pal.muted, false);
            e.setGravity(Gravity.CENTER);
            e.setPadding(0, dp(80), 0, 0);
            col.addView(e, new LinearLayout.LayoutParams(-1, -2));
        }
        for (TaskDb.Shared s : all) col.addView(card(s));
        Fonts.apply(col);
    }

    private View card(final TaskDb.Shared s) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(18));
        g.setColor(Pal.card);
        if (s.id == highlight) g.setStroke(dp(2), Pal.accent);
        c.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(10);
        c.setLayoutParams(lp);

        TextView when = text(Reminders.when(this, s.created) + folderTag(s.folder), 12, Pal.muted, true);
        c.addView(when);
        final View.OnLongClickListener hold = v -> {
            startDial(s, c);
            return true;
        };
        c.setOnLongClickListener(hold);
        when.setOnLongClickListener(hold);
        c.setOnTouchListener((v, e) -> dialTouch(e));
        when.setOnTouchListener((v, e) -> {
            boolean r = dialTouch(e);
            return r;
        });

        TextView link = text(s.url, 14, Pal.accent, false);
        link.setPadding(0, dp(6), 0, dp(6));
        link.setMaxLines(3);
        link.setEllipsize(android.text.TextUtils.TruncateAt.END);
        link.setOnClickListener(v -> open(s.url));
        link.setOnLongClickListener(hold);
        link.setOnTouchListener((v, e) -> dialTouch(e));
        if (!s.url.isEmpty()) c.addView(link);

        final EditText title = field("Title", s.title, false);
        c.addView(title);
        final EditText desc = field("Description", s.description, true);
        c.addView(desc);
        savers.add(() -> {
            String t = title.getText().toString().trim();
            String d = desc.getText().toString().trim();
            if (!t.equals(s.title) || !d.equals(s.description)) db.updateShared(s.id, t, d);
        });

        LinearLayout row = new LinearLayout(this);
        row.setPadding(0, dp(10), 0, 0);
        TextView remind = pill("Remind me", Pal.onAccent, Pal.accent);
        remind.setOnClickListener(v -> {
            Hap.tick(v);
            saveAll();
            String t = title.getText().toString().trim();
            String text = t.isEmpty() ? s.url : t + ": " + s.url;
            startActivity(new Intent(this, MainActivity.class)
                    .putExtra(MainActivity.EXTRA_PREFILL, text)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        });
        LinearLayout.LayoutParams a = new LinearLayout.LayoutParams(0, dp(44), 1.4f);
        a.rightMargin = dp(8);
        row.addView(remind, a);
        TextView copy = pill("Copy", Pal.ink, Pal.chip);
        copy.setOnClickListener(v -> {
            ((ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE))
                    .setPrimaryClip(ClipData.newPlainText("link", s.url));
            Toast.makeText(this, "Link copied", Toast.LENGTH_SHORT).show();
        });
        LinearLayout.LayoutParams b = new LinearLayout.LayoutParams(0, dp(44), 1f);
        b.rightMargin = dp(8);
        row.addView(copy, b);
        TextView del = pill("Delete", Pal.overdue, Pal.chip);
        del.setOnClickListener(v -> {
            saveAll();
            new Dlg.Builder(this)
                    .setTitle("Delete this shared link?")
                    .setPositiveButton("Delete", (d2, w) -> {
                        db.deleteShared(s.id);
                        refresh();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
        row.addView(del, new LinearLayout.LayoutParams(0, dp(44), 1f));
        c.addView(row);
        return c;
    }

    private String folderTag(long id) {
        for (Folder f : folders) if (f.id == id) return "  \u00b7  " + f.name;
        return "";
    }

    private LinearLayout folderChip() {
        LinearLayout chip = new LinearLayout(this);
        chip.setOrientation(LinearLayout.HORIZONTAL);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setPadding(dp(12), 0, dp(14), 0);
        chip.setClickable(true);
        return chip;
    }

    private void styleChip(LinearLayout chip, boolean on, int rim) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(22));
        g.setColor(on ? Pal.sel : Pal.card);
        if (!on && rim != 0) g.setStroke(dp(1), (rim & 0x00FFFFFF) | 0x55000000);
        chip.setBackground(g);
    }

    private void buildChips() {
        chipRow.removeAllViews();
        chipRow.setPadding(dp(14), dp(2), dp(14), dp(8));
        // All
        LinearLayout all = folderChip();
        styleChip(all, curFolder == -1, 0);
        TextView an = text("All", 14, curFolder == -1 ? Pal.onSel : Pal.ink, true);
        all.addView(an);
        TextView ac = text(String.valueOf(db.sharedCount()), 12, curFolder == -1 ? Pal.onSel : Pal.muted, false);
        ac.setPadding(dp(10), 0, 0, 0);
        all.addView(ac);
        all.setOnClickListener(v -> {
            Hap.tick(v);
            curFolder = -1;
            refresh();
        });
        chipRow.addView(all, chipLp());
        for (final Folder f : folders) {
            boolean on = curFolder == f.id;
            LinearLayout chip = folderChip();
            styleChip(chip, on, f.rgb());
            IconView ic = new IconView(this, IconView.FOLDER);
            ic.setTint(f.rgb());
            ic.setOn(true);
            ic.setClickable(false);
            chip.addView(ic, new LinearLayout.LayoutParams(dp(26), dp(26)));
            TextView nm = text(f.name, 14, on ? Pal.onSel : Pal.ink, true);
            nm.setSingleLine(true);
            nm.setEllipsize(android.text.TextUtils.TruncateAt.END);
            nm.setMaxWidth(dp(120));
            nm.setPadding(dp(4), 0, 0, 0);
            chip.addView(nm);
            TextView cn = text(String.valueOf(f.count), 12, on ? Pal.onSel : Pal.muted, false);
            cn.setPadding(dp(10), 0, 0, 0);
            chip.addView(cn);
            chip.setOnClickListener(v -> {
                Hap.tick(v);
                curFolder = f.id;
                refresh();
            });
            chip.setOnLongClickListener(v -> {
                Hap.confirm(v);
                folderMenu(f);
                return true;
            });
            chipRow.addView(chip, chipLp());
        }
        LinearLayout add = folderChip();
        styleChip(add, false, 0);
        IconView plus = new IconView(this, IconView.PLUS);
        plus.setClickable(false);
        plus.setTint(Pal.accent);
        add.addView(plus, new LinearLayout.LayoutParams(dp(26), dp(26)));
        TextView lab = text("Folder", 14, Pal.ink, true);
        lab.setPadding(dp(4), 0, 0, 0);
        add.addView(lab);
        add.setOnClickListener(v -> {
            Hap.tick(v);
            newFolder(null);
        });
        chipRow.addView(add, chipLp());
    }

    private LinearLayout.LayoutParams chipLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(44));
        lp.rightMargin = dp(8);
        return lp;
    }

    /** The same name + colour dialog the notebook uses. */
    private void newFolder(final java.util.function.LongConsumer then) {
        new FolderDlg(this).show(null, (name, color) -> {
            long id = db.addSharedFolder(name, color);
            if (then != null) then.accept(id);
            else curFolder = id;
            refresh();
        });
    }

    private void folderMenu(final Folder f) {
        new Dlg.Builder(this).setTitle(f.name)
                .setItems(new String[]{"Rename or change colour", "Delete folder"}, (d, w) -> {
                    if (w == 0) {
                        new FolderDlg(this).show(f, (name, color) -> {
                            db.updateSharedFolder(f.id, name, color);
                            refresh();
                        });
                    } else {
                        new Dlg.Builder(this).setTitle("Delete folder?")
                                .setMessage(f.name + "\nIts shared notes go back to All. None is deleted.")
                                .setPositiveButton("Delete", (d2, w2) -> {
                                    db.deleteSharedFolder(f.id);
                                    if (curFolder == f.id) curFolder = -1;
                                    refresh();
                                })
                                .setNegativeButton("Keep", null).show();
                    }
                }).show();
    }

    // ---- hold a card, slide up or down: a wheel of folders turns under your finger, lift to drop it there
    private boolean dialOn;
    private TaskDb.Shared dialItem;
    private float dialY0;

    private void startDial(TaskDb.Shared s, View card) {
        saveAll();
        dialItem = s;
        dialOn = true;
        card.getParent().requestDisallowInterceptTouchEvent(true);
        List<String> names = new ArrayList<>();
        names.add("No folder");
        for (Folder f : folders) names.add(f.name);
        names.add("+ New folder");
        int start = 0;
        for (int i = 0; i < folders.size(); i++) if (folders.get(i).id == s.folder) start = i + 1;
        dialY0 = Float.NaN;
        dial.open(names, start);
        Hap.tick(card);
    }

    private boolean dialTouch(MotionEvent e) {
        if (!dialOn) return false;
        int act = e.getActionMasked();
        if (Float.isNaN(dialY0)) dialY0 = e.getRawY();
        if (act == MotionEvent.ACTION_MOVE) {
            dial.drag(e.getRawY() - dialY0);
        } else if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) {
            dialOn = false;
            int idx = dial.close();
            if (act == MotionEvent.ACTION_UP) dropAt(idx);
        }
        return true;
    }

    private void dropAt(int idx) {
        final TaskDb.Shared s = dialItem;
        if (s == null) return;
        if (idx == folders.size() + 1) { // + New folder
            newFolder(id -> db.moveShared(s.id, id));
            return;
        }
        long target = idx == 0 ? 0 : folders.get(idx - 1).id;
        if (target != s.folder) {
            db.moveShared(s.id, target);
            Toast.makeText(this, target == 0 ? "Moved out of the folder" : "Moved to " + folders.get(idx - 1).name,
                    Toast.LENGTH_SHORT).show();
        }
        refresh();
    }

    /** The turning wheel: items curve away above and below the middle, the one in the band is where the card lands. */
    private final class FolderDial extends View {
        private List<String> names = new ArrayList<>();
        private float pos;
        private float base;
        private int lastIdx;
        private final android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);

        FolderDial(Context c) {
            super(c);
            setClickable(true);
        }

        void open(List<String> n, int start) {
            names = n;
            base = start;
            pos = start;
            lastIdx = start;
            setVisibility(View.VISIBLE);
            setAlpha(0f);
            animate().alpha(1f).setDuration(140).start();
            invalidate();
        }

        void drag(float dy) {
            pos = Math.max(0, Math.min(names.size() - 1, base + dy / dp(54)));
            int idx = Math.round(pos);
            if (idx != lastIdx) {
                lastIdx = idx;
                performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
            }
            invalidate();
        }

        int close() {
            int idx = Math.round(pos);
            animate().alpha(0f).setDuration(120).withEndAction(() -> setVisibility(View.GONE)).start();
            return idx;
        }

        @Override
        protected void onDraw(android.graphics.Canvas c) {
            c.drawColor(0xB0000000);
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            float R = dp(150);
            p.setStyle(android.graphics.Paint.Style.FILL);
            p.setColor(0x33FFFFFF);
            c.drawRoundRect(cx - dp(150), cy - dp(26), cx + dp(150), cy + dp(26), dp(26), dp(26), p);
            p.setTextAlign(android.graphics.Paint.Align.CENTER);
            for (int i = 0; i < names.size(); i++) {
                float a = (i - pos) * 0.42f;
                if (Math.abs(a) > 1.45f) continue;
                float cos = (float) Math.cos(a);
                float y = cy + R * (float) Math.sin(a);
                p.setTextSize(dp(20) * (0.6f + 0.4f * cos));
                p.setColor(0xFFFFFFFF);
                p.setAlpha(Math.round(255 * cos * cos));
                p.setFakeBoldText(Math.abs(i - pos) < 0.5f);
                c.drawText(names.get(i), cx - (1 - cos) * dp(70), y + dp(7), p);
            }
            p.setAlpha(255);
            p.setTextSize(dp(12));
            p.setColor(0xCCFFFFFF);
            p.setFakeBoldText(false);
            c.drawText("Slide up or down, lift to drop", cx, getHeight() - dp(60), p);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            return true;
        }
    }

    private void open(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(this, "No app can open this link", Toast.LENGTH_SHORT).show();
        }
    }

    private EditText field(String hint, String value, boolean multi) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(value);
        e.setTextSize(multi ? 14 : 16);
        e.setTextColor(Pal.ink);
        e.setHintTextColor(Pal.muted);
        if (!multi) e.setTypeface(Typeface.DEFAULT_BOLD);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | (multi ? InputType.TYPE_TEXT_FLAG_MULTI_LINE : 0));
        e.setBackground(null);
        e.setPadding(0, dp(6), 0, dp(6));
        return e;
    }

    private TextView pill(String s, int fg, int bg) {
        TextView t = text(s, 14, fg, true);
        t.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(22));
        g.setColor(bg);
        t.setBackground(g);
        t.setClickable(true);
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
