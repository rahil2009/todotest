package app.privatetodo;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.net.Uri;
import android.view.View;
import android.widget.RemoteViews;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * The home-screen widgets, all drawn from the local databases only. The lists (Tasks, Notes, Plan, Habits,
 * Lent) are real scrolling lists (see {@link WidgetService}); Today and Water draw a progress ring;
 * Shortcuts is a row of one-tap buttons. Taps on list rows go through {@link WidgetActionActivity}.
 */
public final class Widgets {
    private Widgets() { }

    static final int K_TASKS = 0;
    static final int K_NOTES = 1;
    static final int K_PLAN = 2;
    static final int K_HABITS = 3;
    static final int K_LENT = 4;
    static final int K_COUNT = 5;
    static final int K_NOW = 6;

    // ------------------------------------------------------------------ shared helpers

    static PendingIntent act(Context ctx, int code, String act, long id, int amt) {
        Intent i = new Intent(ctx, WidgetActionActivity.class)
                .putExtra("act", act).putExtra("id", id).putExtra("amt", amt)
                .setData(Uri.parse("widget://act/" + act + "/" + id + "/" + amt + "/" + code));
        return PendingIntent.getActivity(ctx, code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** {title, sub, empty text} of a list widget. */
    static String[] head(Context ctx, int wid, int kind) {
        switch (kind) {
            case K_NOTES: {
                TaskDb db = new TaskDb(ctx);
                int n;
                try {
                    n = db.notes("").size();
                } finally {
                    db.close();
                }
                return new String[]{"Notebook", n + (n == 1 ? " note" : " notes"), "No notes yet"};
            }
            case K_PLAN: {
                int[] c = planCounts(ctx);
                return new String[]{"Plan", new SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(new Date())
                        + " · " + c[0] + "/" + c[1] + " done", "A clear day"};
            }
            case K_HABITS: {
                HabitDb db = new HabitDb(ctx);
                int done = 0;
                int n = 0;
                try {
                    long day = HabitDb.today();
                    for (Habit h : db.list()) {
                        n++;
                        if (h.doneAt(db.amount(h.id, day))) done++;
                    }
                } finally {
                    db.close();
                }
                return new String[]{"Habits", n == 0 ? "" : done + " of " + n + " done today", "No habits yet"};
            }
            case K_LENT: {
                long owed = 0;
                LentDb db = new LentDb(ctx);
                try {
                    for (LentDb.Loan l : db.all()) if (!l.closed && l.dir == 0 && l.money()) owed += l.left();
                } finally {
                    db.close();
                }
                return new String[]{"Lent", owed > 0 ? "You are owed " + LentUi.money(ctx, owed) : "Nobody owes you",
                        "Nothing lent out"};
            }
            case K_COUNT: {
                CountdownDb db = new CountdownDb(ctx);
                String sub = "Nothing counting down";
                try {
                    for (CountdownDb.Item i : db.all()) {
                        if (i.daysLeft() >= 0) {
                            sub = CountdownRemind.headline(i);
                            break;
                        }
                    }
                } finally {
                    db.close();
                }
                return new String[]{"Countdowns", sub, "No countdowns yet"};
            }
            default: {
                int fi = TaskWidget.filterIndex(ctx, wid);
                TaskDb db = new TaskDb(ctx);
                int total = 0;
                try {
                    for (Task t : db.list(TaskWidget.FILTERS[fi])) if (!t.done) total++;
                } finally {
                    db.close();
                }
                return new String[]{TaskWidget.NAMES[fi] + " ▾", total == 0 ? "All clear"
                        : total + (total == 1 ? " task" : " tasks"), "Nothing in " + TaskWidget.NAMES[fi]};
            }
        }
    }

    /** {done, total} of the tasks due today. */
    static int[] planCounts(Context ctx) {
        long start = dayStart();
        long end = start + 86_400_000L;
        TaskDb db = new TaskDb(ctx);
        int done = 0;
        int total = 0;
        try {
            for (Task t : db.list(TaskDb.RAW)) {
                if (t.due < start || t.due >= end) continue;
                total++;
                if (t.done) done++;
            }
        } finally {
            db.close();
        }
        return new int[]{done, total};
    }

    static long dayStart() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    /** A scrolling list widget: header (title, count, add button) over a real ListView. */
    static RemoteViews list(Context ctx, int wid, int kind, PendingIntent headTap, PendingIntent addTap) {
        String[] h = head(ctx, wid, kind);
        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.w_list);
        v.setTextViewText(R.id.w_title, h[0]);
        v.setTextViewText(R.id.w_sub, h[1]);
        v.setTextViewText(R.id.w_empty, h[2]);
        v.setOnClickPendingIntent(R.id.w_head, headTap);
        v.setOnClickPendingIntent(R.id.w_add, addTap);
        Intent svc = new Intent(ctx, WidgetService.class)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, wid).putExtra("kind", kind);
        svc.setData(Uri.parse(svc.toUri(Intent.URI_INTENT_SCHEME)));
        v.setRemoteAdapter(R.id.w_list, svc);
        v.setEmptyView(R.id.w_list, R.id.w_empty);
        Intent tpl = new Intent(ctx, WidgetActionActivity.class).setData(Uri.parse("widget://tpl/" + kind + "/" + wid));
        v.setPendingIntentTemplate(R.id.w_list, PendingIntent.getActivity(ctx, wid * 10 + kind, tpl,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE));
        return v;
    }

    static Intent fill(String act, long id) {
        return new Intent().putExtra("act", act).putExtra("id", id);
    }

    /** A progress ring, drawn in the widget colours. */
    static Bitmap ring(Context ctx, int dp, float frac, int color) {
        float d = ctx.getResources().getDisplayMetrics().density;
        int px = Math.max(8, Math.round(dp * d));
        Bitmap b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float w = px * 0.13f;
        RectF r = new RectF(w / 2f, w / 2f, px - w / 2f, px - w / 2f);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(w);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(ctx.getColor(R.color.w_chip));
        c.drawArc(r, 0, 360, false, p);
        if (frac > 0f) {
            p.setColor(color);
            c.drawArc(r, -90, 360f * Math.min(1f, frac), false, p);
        }
        return b;
    }

    /** A round tile with a letter or emoji, coloured from the name (the same colour as in the Lent screens). */
    static Bitmap avatar(Context ctx, String name, String emoji, int dp) {
        float d = ctx.getResources().getDisplayMetrics().density;
        int px = Math.max(8, Math.round(dp * d));
        Bitmap b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        int col = LentUi.tone(name);
        p.setColor((col & 0x00FFFFFF) | 0x55000000);
        c.drawCircle(px / 2f, px / 2f, px / 2f, p);
        boolean em = emoji != null && !emoji.isEmpty();
        String label = em ? emoji : (name.trim().isEmpty() ? "?" : name.trim().substring(0, 1).toUpperCase(Locale.getDefault()));
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextSize(px * 0.46f);
        p.setColor(em ? ctx.getColor(R.color.w_ink) : ctx.getColor(R.color.w_ink));
        Paint.FontMetrics fm = p.getFontMetrics();
        c.drawText(label, px / 2f, px / 2f - (fm.ascent + fm.descent) / 2f, p);
        return b;
    }

    // ------------------------------------------------------------------ refresh

    private interface B {
        RemoteViews build(int id);
    }

    private static void each(AppWidgetManager m, Context ctx, Class<?> cls, B b, boolean list, int kind) {
        int[] ids = m.getAppWidgetIds(new ComponentName(ctx, cls));
        if (ids.length == 0) return;
        if (kind == K_NOW) {
            m.notifyAppWidgetViewDataChanged(ids, R.id.n_stack);
            return;
        }
        if (list) { // keep the scroll position: refresh the numbers in the header and tell the list its data changed
            for (int id : ids) {
                String[] h = head(ctx, id, kind);
                RemoteViews part = new RemoteViews(ctx.getPackageName(), R.layout.w_list);
                part.setTextViewText(R.id.w_title, h[0]);
                part.setTextViewText(R.id.w_sub, h[1]);
                part.setTextViewText(R.id.w_empty, h[2]);
                m.partiallyUpdateAppWidget(id, part);
            }
            m.notifyAppWidgetViewDataChanged(ids, R.id.w_list);
        } else {
            for (int id : ids) m.updateAppWidget(id, b.build(id));
        }
    }

    /** Redraws every widget. Cheap and safe to call often. */
    static void refreshAll(Context ctx) {
        try {
            AppWidgetManager m = AppWidgetManager.getInstance(ctx);
            each(m, ctx, TaskWidget.class, id -> TaskWidget.build(ctx, id), true, K_TASKS);
            each(m, ctx, NotesWidget.class, id -> NotesWidget.build(ctx, id), true, K_NOTES);
            each(m, ctx, PlanWidget.class, id -> PlanWidget.build(ctx, id), true, K_PLAN);
            each(m, ctx, Habits.class, id -> Habits.build(ctx, id), true, K_HABITS);
            each(m, ctx, Lent.class, id -> Lent.build(ctx, id), true, K_LENT);
            each(m, ctx, Count.class, id -> Count.build(ctx, id), true, K_COUNT);
            each(m, ctx, Now.class, id -> Now.build(ctx, id), true, K_NOW);
            each(m, ctx, TaskWidget.QuickWidget.class, id -> TaskWidget.QuickWidget.build(ctx), false, 0);
            each(m, ctx, Today.class, id -> Today.build(ctx), false, 0);
            each(m, ctx, Water.class, id -> Water.build(ctx), false, 0);
        } catch (RuntimeException ignored) {
            // a widget must never crash the app
        }
    }

    // ------------------------------------------------------------------ Today: a ring and what is next

    public static class Today extends AppWidgetProvider {
        @Override
        public void onUpdate(Context ctx, AppWidgetManager m, int[] ids) {
            for (int id : ids) m.updateAppWidget(id, build(ctx));
        }

        static RemoteViews build(Context ctx) {
            RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.w_today);
            int[] c = planCounts(ctx);
            int left = c[1] - c[0];
            v.setImageViewBitmap(R.id.t_ring, ring(ctx, 62, c[1] == 0 ? 0f : c[0] / (float) c[1], ctx.getColor(R.color.w_accent)));
            v.setTextViewText(R.id.t_num, c[1] == 0 ? "–" : left == 0 ? "✓" : String.valueOf(left));
            v.setTextViewText(R.id.t_label, c[1] == 0 ? "Clear day" : left == 0 ? "All done" : left + (left == 1 ? " task left" : " tasks left"));
            // what is next: the first open task from now on, else the earliest open one today
            long start = dayStart();
            long end = start + 86_400_000L;
            long now = System.currentTimeMillis();
            Task next = null;
            TaskDb db = new TaskDb(ctx);
            try {
                for (Task t : db.list(TaskDb.RAW)) {
                    if (t.done || t.due < start || t.due >= end) continue;
                    boolean later = t.due >= now;
                    boolean nl = next != null && next.due >= now;
                    if (next == null || (later && (!nl || t.due < next.due)) || (!later && !nl && t.due < next.due)) next = t;
                }
            } finally {
                db.close();
            }
            String nx = "";
            if (next != null) nx = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(next.due)) + "  " + next.title;
            v.setTextViewText(R.id.t_next, nx);
            v.setOnClickPendingIntent(R.id.t_root, TaskWidget.open(ctx, false, 5));
            return v;
        }
    }

    // ------------------------------------------------------------------ Water: ring and one-tap buttons

    public static class Water extends AppWidgetProvider {
        @Override
        public void onUpdate(Context ctx, AppWidgetManager m, int[] ids) {
            for (int id : ids) m.updateAppWidget(id, build(ctx));
        }

        static RemoteViews build(Context ctx) {
            RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.w_water);
            HabitDb db = new HabitDb(ctx);
            Habit water = null;
            int amt = 0;
            try {
                for (Habit h : db.list()) {
                    if (HabitUi.isWater(h)) {
                        water = h;
                        break;
                    }
                }
                if (water != null) amt = Math.max(0, db.amount(water.id, HabitDb.today()));
            } finally {
                db.close();
            }
            int blue = ctx.getColor(R.color.w_blue);
            PendingIntent open = TaskWidget.openActivity(ctx, HabitActivity.class, 40, 0);
            v.setOnClickPendingIntent(R.id.h_root, open);
            if (water == null) {
                v.setImageViewBitmap(R.id.h_ring, ring(ctx, 60, 0f, blue));
                v.setTextViewText(R.id.h_pct, "💧");
                v.setTextViewText(R.id.h_title, "Water");
                v.setTextViewText(R.id.h_amount, "No water habit");
                v.setTextViewText(R.id.h_goal, "Tap to add one in Habits");
                v.setViewVisibility(R.id.h_buttons, View.GONE);
                return v;
            }
            float frac = water.goal > 0 ? amt / (float) water.goal : 0f;
            v.setImageViewBitmap(R.id.h_ring, ring(ctx, 60, frac, blue));
            v.setTextViewText(R.id.h_pct, Math.round(Math.min(1f, frac) * 100) + "%");
            v.setTextViewText(R.id.h_title, "💧 " + water.name);
            String unit = water.unit.isEmpty() ? "" : " " + water.unit;
            v.setTextViewText(R.id.h_amount, amt + unit);
            v.setTextViewText(R.id.h_goal, frac >= 1f ? "Goal reached" : "of " + water.goal + unit);
            v.setViewVisibility(R.id.h_buttons, View.VISIBLE);
            List<Integer> q = water.quickAmounts();
            int[] ids = {R.id.h_b1, R.id.h_b2, R.id.h_b3};
            for (int i = 0; i < ids.length; i++) {
                if (i < q.size()) {
                    v.setViewVisibility(ids[i], View.VISIBLE);
                    v.setTextViewText(ids[i], "+" + q.get(i));
                    v.setOnClickPendingIntent(ids[i], act(ctx, 50 + i, "habit", water.id, q.get(i)));
                } else {
                    v.setViewVisibility(ids[i], View.GONE);
                }
            }
            return v;
        }
    }

    // ------------------------------------------------------------------ Habits and Lent lists

    public static class Habits extends AppWidgetProvider {
        @Override
        public void onUpdate(Context ctx, AppWidgetManager m, int[] ids) {
            for (int id : ids) m.updateAppWidget(id, build(ctx, id));
        }

        static RemoteViews build(Context ctx, int wid) {
            return list(ctx, wid, K_HABITS, TaskWidget.openActivity(ctx, HabitActivity.class, 41, 0),
                    TaskWidget.openActivity(ctx, HabitActivity.class, 42, 0));
        }
    }

    public static class Lent extends AppWidgetProvider {
        @Override
        public void onUpdate(Context ctx, AppWidgetManager m, int[] ids) {
            for (int id : ids) m.updateAppWidget(id, build(ctx, id));
        }

        static RemoteViews build(Context ctx, int wid) {
            return list(ctx, wid, K_LENT, TaskWidget.openActivity(ctx, LentActivity.class, 43, 0),
                    TaskWidget.openActivity(ctx, LentEditActivity.class, 44, 0));
        }
    }

    /** One card at a time: the next task, with a live countdown and Done / +1 hour. Swipe the card to see the one after. */
    public static class Now extends AppWidgetProvider {
        @Override
        public void onUpdate(Context ctx, AppWidgetManager m, int[] ids) {
            for (int id : ids) m.updateAppWidget(id, build(ctx, id));
        }

        static RemoteViews build(Context ctx, int wid) {
            RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.w_now);
            Intent svc = new Intent(ctx, WidgetService.class)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, wid).putExtra("kind", K_NOW);
            svc.setData(Uri.parse(svc.toUri(Intent.URI_INTENT_SCHEME)));
            v.setRemoteAdapter(R.id.n_stack, svc);
            v.setEmptyView(R.id.n_stack, R.id.n_empty);
            Intent tpl = new Intent(ctx, WidgetActionActivity.class).setData(Uri.parse("widget://tpl/now/" + wid));
            v.setPendingIntentTemplate(R.id.n_stack, PendingIntent.getActivity(ctx, wid * 10 + K_NOW, tpl,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE));
            return v;
        }
    }

    public static class Count extends AppWidgetProvider {
        @Override
        public void onUpdate(Context ctx, AppWidgetManager m, int[] ids) {
            for (int id : ids) m.updateAppWidget(id, build(ctx, id));
        }

        static RemoteViews build(Context ctx, int wid) {
            return list(ctx, wid, K_COUNT, TaskWidget.openActivity(ctx, CountdownActivity.class, 60, 0),
                    TaskWidget.openActivity(ctx, CountdownActivity.class, 61, 0));
        }
    }

    static List<Task> sortedToday(List<Task> all) {
        long start = dayStart();
        long end = start + 86_400_000L;
        ArrayList<Task> today = new ArrayList<>();
        for (Task t : all) if (!t.done && t.due >= start && t.due < end) today.add(t);
        Collections.sort(today, (a, b) -> Long.compare(a.due, b.due));
        return today;
    }
}
