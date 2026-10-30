package app.privatetodo;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Supplies the rows of the scrolling widgets (tasks, notes, plan, habits, lent). Local data only. */
public class WidgetService extends RemoteViewsService {
    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new Factory(getApplicationContext(),
                intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0), intent.getIntExtra("kind", 0));
    }

    /** One row, whatever the list. */
    private static final class Item {
        int type; // 0 normal, 1 free-time strip
        long id;
        String title = "";
        String sub = "";
        String chip = "";
        boolean late;
        int prio;
        String emoji = "";
        String person = "";
        int dir;
        int progress;
        boolean done;
        int amt;
        boolean pinned;
        long due;
    }

    private static final class Factory implements RemoteViewsFactory {
        private final Context ctx;
        private final int wid;
        private final int kind;
        private List<Item> items = new ArrayList<>();

        Factory(Context ctx, int wid, int kind) {
            this.ctx = ctx;
            this.wid = wid;
            this.kind = kind;
        }

        @Override
        public void onCreate() {
        }

        @Override
        public void onDestroy() {
        }

        @Override
        public void onDataSetChanged() {
            List<Item> out = new ArrayList<>();
            try {
                switch (kind) {
                    case Widgets.K_NOTES:
                        loadNotes(out);
                        break;
                    case Widgets.K_PLAN:
                        loadPlan(out);
                        break;
                    case Widgets.K_HABITS:
                        loadHabits(out);
                        break;
                    case Widgets.K_LENT:
                        loadLent(out);
                        break;
                    case Widgets.K_COUNT:
                        loadCount(out);
                        break;
                    case Widgets.K_NOW:
                        loadNow(out);
                        break;
                    default:
                        loadTasks(out);
                        break;
                }
            } catch (RuntimeException ignored) {
                // show what we have rather than crash the home screen
            }
            items = out;
        }

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public long getItemId(int position) {
            return position < items.size() ? items.get(position).id : position;
        }

        @Override
        public boolean hasStableIds() {
            return false;
        }

        @Override
        public int getViewTypeCount() {
            return 5;
        }

        @Override
        public RemoteViews getLoadingView() {
            return null;
        }

        // ------------------------------------------------------------------ loading

        private String chipFor(Task t, long now) {
            if (t.due <= 0) return "";
            Calendar a = Calendar.getInstance();
            a.setTimeInMillis(t.due);
            Calendar b = Calendar.getInstance();
            boolean today = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
            return new SimpleDateFormat(today ? "HH:mm" : "EEE d", Locale.getDefault()).format(new Date(t.due));
        }

        private void loadTasks(List<Item> out) {
            int fi = TaskWidget.filterIndex(ctx, wid);
            TaskDb db = new TaskDb(ctx);
            try {
                long now = System.currentTimeMillis();
                for (Task t : db.list(TaskWidget.FILTERS[fi])) {
                    if (t.done || out.size() >= 80) continue;
                    Item i = new Item();
                    i.id = t.id;
                    i.title = t.emoji.isEmpty() ? t.title : t.emoji + "  " + t.title;
                    i.chip = chipFor(t, now);
                    i.late = t.due > 0 && t.due < now;
                    i.prio = t.priority;
                    if (t.repeat > 0) i.sub = Reminders.repeatLabel(t.repeat, t.repeatUnit);
                    out.add(i);
                }
            } finally {
                db.close();
            }
        }

        private void loadNotes(List<Item> out) {
            TaskDb db = new TaskDb(ctx);
            try {
                for (Note n : db.notes("")) {
                    if (out.size() >= 60) break;
                    Item i = new Item();
                    i.id = n.id;
                    i.pinned = n.pinned > 0;
                    i.title = (i.pinned ? "📌 " : "") + (n.title.isEmpty() ? "Untitled" : n.title);
                    i.sub = n.plain.replace('\n', ' ').replace('\t', ' ').trim();
                    out.add(i);
                }
            } finally {
                db.close();
            }
        }

        private static String clock(long t) {
            return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(t));
        }

        private void loadPlan(List<Item> out) {
            TaskDb db = new TaskDb(ctx);
            List<Task> today;
            try {
                today = Widgets.sortedToday(db.list(TaskDb.RAW));
            } finally {
                db.close();
            }
            long now = System.currentTimeMillis();
            long dayEnd = Widgets.dayStart() + 22 * 3_600_000L;
            long cursor = now - now % (15 * 60_000L) + 15 * 60_000L;
            for (Task t : today) {
                if (t.due - cursor >= 3_600_000L) {
                    Item f = new Item();
                    f.type = 1;
                    f.title = "Free " + clock(cursor) + " – " + clock(t.due);
                    out.add(f);
                }
                Item i = new Item();
                i.id = t.id;
                i.title = t.emoji.isEmpty() ? t.title : t.emoji + "  " + t.title;
                i.chip = clock(t.due);
                i.late = t.due < now;
                i.prio = t.priority;
                out.add(i);
                cursor = Math.max(cursor, t.due + 30 * 60_000L);
            }
            if (dayEnd - cursor >= 3_600_000L) {
                Item f = new Item();
                f.type = 1;
                f.title = "Free " + clock(cursor) + " – " + clock(dayEnd);
                out.add(f);
            }
        }

        private void loadHabits(List<Item> out) {
            HabitDb db = new HabitDb(ctx);
            try {
                long day = HabitDb.today();
                for (Habit h : db.list()) {
                    int a = db.amount(h.id, day);
                    Item i = new Item();
                    i.id = h.id;
                    i.emoji = h.emoji.isEmpty() ? "◎" : h.emoji;
                    i.title = h.name;
                    i.done = h.doneAt(a);
                    i.progress = h.goal > 0 ? Math.max(0, Math.min(100, a * 100 / h.goal)) : (i.done ? 100 : 0);
                    Map<Long, Integer> all = db.all(h.id);
                    HabitDb.Stats st = HabitDb.stats(h, all);
                    i.sub = h.progress(a) + (st.streak > 1 ? "  ·  🔥 " + st.streak : "");
                    i.amt = h.step;
                    out.add(i);
                }
            } finally {
                db.close();
            }
        }

        /** The next few open tasks that have a time: overdue ones first, then the soonest. */
        private void loadNow(List<Item> out) {
            TaskDb db = new TaskDb(ctx);
            try {
                long now = System.currentTimeMillis();
                List<Task> dated = new ArrayList<>();
                for (Task t : db.list(TaskDb.ACTIVE)) if (!t.done && t.due > 0) dated.add(t);
                Collections.sort(dated, (a, b) -> Long.compare(a.due, b.due));
                for (Task t : dated) {
                    if (out.size() >= 8) break;
                    Item i = new Item();
                    i.id = t.id;
                    i.title = t.emoji.isEmpty() ? t.title : t.emoji + "  " + t.title;
                    i.due = t.due;
                    i.late = t.due < now;
                    i.prio = t.priority;
                    i.sub = new SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault()).format(new Date(t.due))
                            + (t.repeat > 0 ? "  \u00B7  " + Reminders.repeatLabel(t.repeat, t.repeatUnit) : "");
                    out.add(i);
                }
            } finally {
                db.close();
            }
        }

        private void loadCount(List<Item> out) {
            CountdownDb db = new CountdownDb(ctx);
            try {
                for (CountdownDb.Item c : db.all()) {
                    if (out.size() >= 40) break;
                    long n = c.daysLeft();
                    if (n < -3) continue; // keep just-passed events visible for a few days
                    Item i = new Item();
                    i.id = c.id;
                    i.person = c.title;
                    i.emoji = c.emoji;
                    i.title = c.title;
                    i.sub = LentUi.date(c.day) + (c.minutes >= 0 ? "  \u00B7  " + LentUi.clock(ctx, c.minutes) : "");
                    i.chip = n == 0 ? "Today" : n == 1 ? "1 day" : n > 1 ? n + " days" : (-n) + "d ago";
                    i.late = n < 0;
                    out.add(i);
                }
            } finally {
                db.close();
            }
        }

        private void loadLent(List<Item> out) {
            LentDb db = new LentDb(ctx);
            try {
                java.util.LinkedHashMap<String, Item> map = new java.util.LinkedHashMap<>();
                java.util.HashMap<String, long[]> sums = new java.util.HashMap<>(); // owed, things, nextDue
                long today = LentDb.today();
                for (LentDb.Loan l : db.all()) {
                    if (l.closed || l.dir != 0) continue;
                    String key = l.person.toLowerCase(Locale.ROOT);
                    Item i = map.get(key);
                    long[] s = sums.get(key);
                    if (i == null) {
                        i = new Item();
                        i.person = l.person;
                        i.title = l.person;
                        i.dir = 0;
                        map.put(key, i);
                        s = new long[3];
                        sums.put(key, s);
                    }
                    if (l.money()) s[0] += l.left();
                    else {
                        s[1]++;
                        if (i.emoji.isEmpty()) i.emoji = l.emoji;
                    }
                    if (l.due > 0 && (s[2] == 0 || l.due < s[2])) s[2] = l.due;
                }
                for (Map.Entry<String, Item> e : map.entrySet()) {
                    Item i = e.getValue();
                    long[] s = sums.get(e.getKey());
                    i.chip = s[0] > 0 ? LentUi.money(ctx, s[0]) + (s[1] > 0 ? " +" + s[1] : "")
                            : s[1] + (s[1] == 1 ? " thing" : " things");
                    i.sub = s[2] > 0 ? LentUi.dueLabel(s[2]) : "no return date";
                    i.late = s[2] > 0 && s[2] < today;
                    i.id = s[2] > 0 ? s[2] : Long.MAX_VALUE;
                    if (s[0] > 0 || s[1] > 0) out.add(i);
                }
                Collections.sort(out, (a, b) -> {
                    if (a.late != b.late) return a.late ? -1 : 1;
                    return Long.compare(a.id, b.id);
                });
            } finally {
                db.close();
            }
        }

        // ------------------------------------------------------------------ one row

        @Override
        public RemoteViews getViewAt(int position) {
            if (position < 0 || position >= items.size()) return null;
            Item i = items.get(position);
            String pkg = ctx.getPackageName();
            switch (kind) {
                case Widgets.K_NOTES: {
                    RemoteViews r = new RemoteViews(pkg, R.layout.w_item_note);
                    r.setTextViewText(R.id.wi_title, i.title);
                    r.setTextViewText(R.id.wi_sub, i.sub);
                    r.setViewVisibility(R.id.wi_sub, i.sub.isEmpty() ? android.view.View.GONE : android.view.View.VISIBLE);
                    r.setOnClickFillInIntent(R.id.wi_root, Widgets.fill("note", i.id));
                    return r;
                }
                case Widgets.K_HABITS: {
                    RemoteViews r = new RemoteViews(pkg, R.layout.w_item_habit);
                    r.setTextViewText(R.id.wi_emoji, i.emoji);
                    r.setTextViewText(R.id.wi_title, i.title);
                    r.setTextViewText(R.id.wi_sub, i.sub);
                    r.setProgressBar(R.id.wi_bar, 100, i.progress, false);
                    r.setTextViewText(R.id.wi_btn, i.done ? "✓" : "+");
                    r.setOnClickFillInIntent(R.id.wi_btn, Widgets.fill("habit", i.id).putExtra("amt", i.amt));
                    r.setOnClickFillInIntent(R.id.wi_root, Widgets.fill("open_habits", i.id));
                    return r;
                }
                case Widgets.K_NOW: {
                    RemoteViews r = new RemoteViews(pkg, R.layout.w_item_now);
                    long left = i.due - System.currentTimeMillis();
                    r.setTextViewText(R.id.wi_title, i.title);
                    r.setTextViewText(R.id.wi_sub, i.sub);
                    r.setTextViewText(R.id.wi_label, position == 0 ? (i.late ? "OVERDUE" : "NEXT UP") : (i.late ? "OVERDUE" : "THEN"));
                    if (left > 0 && left < 12 * 3_600_000L) { // live countdown, ticking on the home screen
                        r.setViewVisibility(R.id.wi_clock, android.view.View.VISIBLE);
                        r.setViewVisibility(R.id.wi_time, android.view.View.GONE);
                        r.setChronometerCountDown(R.id.wi_clock, true);
                        r.setChronometer(R.id.wi_clock, android.os.SystemClock.elapsedRealtime() + left, null, true);
                    } else {
                        r.setViewVisibility(R.id.wi_clock, android.view.View.GONE);
                        r.setViewVisibility(R.id.wi_time, android.view.View.VISIBLE);
                        r.setTextViewText(R.id.wi_time, i.late ? "late" : left < 48 * 3_600_000L ? "tomorrow" : "in " + (left / 86_400_000L) + " days");
                        r.setTextColor(R.id.wi_time, ctx.getColor(i.late ? R.color.w_red : R.color.w_accent));
                    }
                    if (i.prio > 0) {
                        r.setViewVisibility(R.id.wi_prio, android.view.View.VISIBLE);
                        r.setInt(R.id.wi_prio, "setBackgroundColor", i.prio == 1 ? 0xFF4CAF50 : i.prio == 2 ? 0xFFF2A93B : 0xFFD64545);
                    } else {
                        r.setViewVisibility(R.id.wi_prio, android.view.View.GONE);
                    }
                    r.setOnClickFillInIntent(R.id.wi_done, Widgets.fill("done", i.id));
                    r.setOnClickFillInIntent(R.id.wi_later, Widgets.fill("snooze", i.id));
                    r.setOnClickFillInIntent(R.id.wi_root, Widgets.fill("task", i.id));
                    return r;
                }
                case Widgets.K_COUNT: {
                    RemoteViews r = new RemoteViews(pkg, R.layout.w_item_lent);
                    r.setImageViewBitmap(R.id.wi_avatar, Widgets.avatar(ctx, i.person, i.emoji, 38));
                    r.setTextViewText(R.id.wi_title, i.title);
                    r.setTextViewText(R.id.wi_sub, i.sub);
                    r.setTextColor(R.id.wi_sub, ctx.getColor(R.color.w_muted));
                    r.setTextViewText(R.id.wi_time, i.chip);
                    r.setOnClickFillInIntent(R.id.wi_root, Widgets.fill("count", i.id));
                    return r;
                }
                case Widgets.K_LENT: {
                    RemoteViews r = new RemoteViews(pkg, R.layout.w_item_lent);
                    r.setImageViewBitmap(R.id.wi_avatar, Widgets.avatar(ctx, i.person, "", 38));
                    r.setTextViewText(R.id.wi_title, i.title);
                    r.setTextViewText(R.id.wi_sub, i.sub);
                    r.setTextColor(R.id.wi_sub, ctx.getColor(i.late ? R.color.w_red : R.color.w_muted));
                    r.setTextViewText(R.id.wi_time, i.chip);
                    r.setOnClickFillInIntent(R.id.wi_root, Widgets.fill("lent", 0)
                            .putExtra("person", i.person).putExtra("dir", i.dir));
                    return r;
                }
                default: {
                    if (i.type == 1) {
                        RemoteViews r = new RemoteViews(pkg, R.layout.w_item_free);
                        r.setTextViewText(R.id.wi_title, i.title);
                        r.setOnClickFillInIntent(R.id.wi_root, Widgets.fill("plan", 0));
                        return r;
                    }
                    RemoteViews r = new RemoteViews(pkg, R.layout.w_item_task);
                    r.setTextViewText(R.id.wi_title, i.title);
                    r.setTextViewText(R.id.wi_sub, i.sub);
                    r.setViewVisibility(R.id.wi_sub, i.sub.isEmpty() ? android.view.View.GONE : android.view.View.VISIBLE);
                    r.setTextViewText(R.id.wi_time, i.chip);
                    r.setViewVisibility(R.id.wi_time, i.chip.isEmpty() ? android.view.View.GONE : android.view.View.VISIBLE);
                    r.setTextColor(R.id.wi_time, ctx.getColor(i.late ? R.color.w_red : R.color.w_ink));
                    r.setInt(R.id.wi_time, "setBackgroundResource", i.late ? R.drawable.widget_red_chip : R.drawable.widget_chip);
                    if (i.prio > 0) {
                        r.setViewVisibility(R.id.wi_prio, android.view.View.VISIBLE);
                        r.setInt(R.id.wi_prio, "setBackgroundColor", i.prio == 1 ? 0xFF4CAF50 : i.prio == 2 ? 0xFFF2A93B : 0xFFD64545);
                    } else {
                        r.setViewVisibility(R.id.wi_prio, android.view.View.GONE);
                    }
                    r.setOnClickFillInIntent(R.id.wi_check, Widgets.fill("done", i.id));
                    r.setOnClickFillInIntent(R.id.wi_root, Widgets.fill(kind == Widgets.K_PLAN ? "plan" : "task", i.id));
                    return r;
                }
            }
        }
    }
}
