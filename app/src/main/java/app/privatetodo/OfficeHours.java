package app.privatetodo;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Office hours: on chosen days and times, reminders and alarms use their own volume (0 = silent).
 * Outside every schedule the normal volumes from Settings apply, and so does a volume set on a single task.
 * Stored only in this app's private settings.
 */
final class OfficeHours {
    static final class Rule {
        String name = "Work";
        int days = 31;        // bit 0 = Monday ... bit 6 = Sunday
        int start = 9 * 60;   // minutes after midnight
        int end = 17 * 60;    // an end at or before the start means "until the next morning"
        int notif = 20;       // notification volume 0..100 while active
        int alarm = 40;       // alarm volume 0..100 while active
    }

    private OfficeHours() {
    }

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    static boolean on(Context c) {
        return sp(c).getBoolean("office_on", false);
    }

    static void setOn(Context c, boolean v) {
        sp(c).edit().putBoolean("office_on", v).apply();
    }

    static List<Rule> rules(Context c) {
        List<Rule> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(sp(c).getString("office_rules", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Rule r = new Rule();
                r.name = o.optString("n", "Work");
                r.days = o.optInt("d", 31) & 127;
                r.start = clampMin(o.optInt("s", 540));
                r.end = clampMin(o.optInt("e", 1020));
                r.notif = clampVol(o.optInt("nv", 20));
                r.alarm = clampVol(o.optInt("av", 40));
                out.add(r);
            }
        } catch (JSONException ignored) {
            // unreadable: no schedules
        }
        return out;
    }

    static void save(Context c, List<Rule> rules) {
        JSONArray a = new JSONArray();
        try {
            for (Rule r : rules) {
                a.put(new JSONObject().put("n", r.name).put("d", r.days).put("s", r.start).put("e", r.end)
                        .put("nv", r.notif).put("av", r.alarm));
            }
        } catch (JSONException ignored) {
            // cannot happen with plain values
        }
        sp(c).edit().putString("office_rules", a.toString()).apply();
    }

    /** The schedule that is active at this moment, or null. The first one in the list wins. */
    static Rule activeNow(Context c) {
        if (!on(c)) return null;
        Calendar now = Calendar.getInstance();
        int day = (now.get(Calendar.DAY_OF_WEEK) + 5) % 7; // Monday = 0
        int prev = (day + 6) % 7;
        int m = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        for (Rule r : rules(c)) {
            if (r.start < r.end) {
                if ((r.days & (1 << day)) != 0 && m >= r.start && m < r.end) return r;
            } else { // runs past midnight (or is all day when start == end)
                if ((r.days & (1 << day)) != 0 && m >= r.start) return r;
                if ((r.days & (1 << prev)) != 0 && m < r.end) return r;
            }
        }
        return null;
    }

    /** The volume to use for this kind of sound right now: office hours if active, otherwise Settings. */
    static int volume(Context c, int kind) {
        Rule r = activeNow(c);
        if (r == null || kind == Prefs.POMO) return Prefs.volume(c, kind);
        return kind == Prefs.ALARM ? r.alarm : r.notif;
    }

    /** "Mon–Fri · 9:00 AM– 5:00 PM" style summary. */
    static String when(Context c, Rule r) {
        String days = r.days == 127 ? "Every day" : Reminders.repeatLabel(r.days, 5);
        return days + "  ·  " + clock(c, r.start) + " – " + clock(c, r.end);
    }

    static String clock(Context c, int min) {
        int h = min / 60;
        int m = min % 60;
        if (android.text.format.DateFormat.is24HourFormat(c)) return String.format(java.util.Locale.US, "%02d:%02d", h, m);
        return (h % 12 == 0 ? 12 : h % 12) + ":" + (m < 10 ? "0" : "") + m + (h >= 12 ? " PM" : " AM");
    }

    private static int clampMin(int v) {
        return Math.max(0, Math.min(1439, v));
    }

    private static int clampVol(int v) {
        return Math.max(0, Math.min(100, v));
    }
}
