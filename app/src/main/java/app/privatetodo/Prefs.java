package app.privatetodo;

import android.content.Context;
import android.content.SharedPreferences;

/** User settings, stored only in this app's private storage. */
final class Prefs {
    static final int NOTIF = 0;
    static final int ALARM = 1;
    static final int POMO = 2;

    static final int THEME_SYSTEM = 0;
    static final int THEME_LIGHT = 1;
    static final int THEME_DARK = 2;
    static final int THEME_AMOLED = 3;

    /** Sound value meaning "no sound". An empty string means "the phone's default". */
    static final String SILENT = "silent";

    private Prefs() {
    }

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    static int theme(Context c) {
        return sp(c).getInt("theme", THEME_SYSTEM);
    }

    static void setTheme(Context c, int v) {
        sp(c).edit().putInt("theme", v).apply();
    }

    /** "" = phone default, SILENT, or a content:// URI string chosen by the user. */
    static String sound(Context c, int kind) {
        return sp(c).getString(key("sound", kind), "");
    }

    static void setSound(Context c, int kind, String v) {
        sp(c).edit().putString(key("sound", kind), v == null ? "" : v).apply();
    }

    /** 0 to 100, relative to the phone's own notification / alarm volume. */
    static int volume(Context c, int kind) {
        return sp(c).getInt(key("volume", kind), 100);
    }

    static void setVolume(Context c, int kind, int v) {
        sp(c).edit().putInt(key("volume", kind), Math.max(0, Math.min(100, v))).apply();
    }

    /** The quick "Remind me" buttons, as a ';'-separated list (see Presets). */
    static String presets(Context c, String fallback) {
        return sp(c).getString("presets", fallback);
    }

    static void setPresets(Context c, String v) {
        sp(c).edit().putString("presets", v).apply();
    }

    /** Text size multiplier for the notebook (pinch to zoom, or the Aa button). */
    static float noteScale(Context c) {
        return sp(c).getFloat("note_scale", 1f);
    }

    static void setNoteScale(Context c, float v) {
        sp(c).edit().putFloat("note_scale", v).apply();
    }

    // ---- smart "@" dates in the quick-add pill

    static boolean smartOn(Context c) {
        return sp(c).getBoolean("smart_on", true);
    }

    static void setSmartOn(Context c, boolean v) {
        sp(c).edit().putBoolean("smart_on", v).apply();
    }

    static String smartTrigger(Context c) {
        String s = sp(c).getString("smart_trigger", "@");
        return s.isEmpty() ? "@" : s;
    }

    static void setSmartTrigger(Context c, String v) {
        sp(c).edit().putString("smart_trigger", v == null || v.trim().isEmpty() ? "@" : v.trim()).apply();
    }

    /** Clock time used when only a day is given ("@sat"). */
    static int smartHour(Context c) {
        return sp(c).getInt("smart_hour", 9);
    }

    static int smartMinute(Context c) {
        return sp(c).getInt("smart_min", 0);
    }

    static void setSmartTime(Context c, int h, int m) {
        sp(c).edit().putInt("smart_hour", h).putInt("smart_min", m).apply();
    }

    static boolean smartAlarm(Context c) {
        return sp(c).getBoolean("smart_alarm", false);
    }

    static void setSmartAlarm(Context c, boolean v) {
        sp(c).edit().putBoolean("smart_alarm", v).apply();
    }

    /** One "shortcut=what it means" per line. */
    static String smartAliases(Context c) {
        return sp(c).getString("smart_aliases", SmartDate.DEFAULT_ALIASES);
    }

    static void setSmartAliases(Context c, String v) {
        sp(c).edit().putString("smart_aliases", v).apply();
    }

    /** Animated edge glow on the full-screen alert (on by default). */
    static boolean glow(Context c) {
        return sp(c).getBoolean("glow", true);
    }

    // ---- habit units ("name=quick amounts" per line); edited in Settings > Habit units

    static final String DEFAULT_UNITS = "ml=100,250,500\nL=1,2\nmin=5,10,15\nhr=1\ncount=1,2,5\ncups=1,2\n"
            + "glasses=1,2\npages=5,10,20\nsteps=500,1000,2000\nkm=1,2,5\nreps=5,10,20\nkcal=50,100,200\ng=10,50,100";

    static String habitUnits(Context c) {
        return sp(c).getString("habit_units", DEFAULT_UNITS);
    }

    static void setHabitUnits(Context c, String v) {
        sp(c).edit().putString("habit_units", v).apply();
    }

    /** Which AlertFx style plays behind the full-screen alert (0 = off). Starts from the old glow switch. */
    static int alertAnim(Context c) {
        SharedPreferences s = sp(c);
        if (s.contains("alert_fx")) return s.getInt("alert_fx", AlertFx.EDGE);
        if (s.contains("alert_anim")) { // older numbering: 1 edge, 2 pulse, 3 aurora, 4 sparkles, 5 waves
            int old = s.getInt("alert_anim", 1);
            return old == 3 ? AlertFx.AURORA : old == 5 ? AlertFx.WAVES : old == 4 ? AlertFx.MESH
                    : old == 0 ? AlertFx.OFF : AlertFx.EDGE;
        }
        return s.getBoolean("glow", true) ? AlertFx.EDGE : AlertFx.OFF;
    }

    static void setAlertAnim(Context c, int v) {
        sp(c).edit().putInt("alert_fx", v).apply();
    }

    /** Main screen layout under test: 0 current, 1 two sections, 2 switch, 4 routine strip, 5 routine dock. */
    static int mainLayout(Context c) {
        return sp(c).getInt("main_layout", 0);
    }

    static void setMainLayout(Context c, int v) {
        sp(c).edit().putInt("main_layout", v).apply();
    }

    /** The "Switch" layout: 0 all, 1 one-off, 2 repeating. */
    static int mainKind(Context c) {
        return sp(c).getInt("main_kind", 1);
    }

    static void setMainKind(Context c, int v) {
        sp(c).edit().putInt("main_kind", v).apply();
    }

    /** Notebook list style, numbered as in the design board: 1 current, 2 expanded, 3 grid, 4 big titles,
     *  5 compact rows, 6 titles only, 7 grouped by date, 8 checklist previews, 10 pinned strip + list. */
    static int noteLook(Context c) {
        return sp(c).getInt("note_look", 1);
    }

    static void setNoteLook(Context c, int v) {
        sp(c).edit().putInt("note_look", v).apply();
    }

    /** Notebook sort: 0 edited newest, 1 edited oldest, 2 created (newest first), 3 title A-Z, 4 title Z-A. */
    static int noteSort(Context c) {
        return sp(c).getInt("note_sort", 0);
    }

    static void setNoteSort(Context c, int v) {
        sp(c).edit().putInt("note_sort", v).apply();
    }

    /** How many notes can be pinned to the top of the notebook (1 to 20, default 5). */
    static final int PINS_DEFAULT = 5;
    static final int PINS_MAX = 20;

    static int maxPins(Context c) {
        return Math.max(1, Math.min(PINS_MAX, sp(c).getInt("max_pins", PINS_DEFAULT)));
    }

    static void setMaxPins(Context c, int v) {
        sp(c).edit().putInt("max_pins", Math.max(1, Math.min(PINS_MAX, v))).apply();
    }

    /** Fill style of pinned notes' previews, see PinTint (0 = faint tint, the default). */
    static int pinColor(Context c) {
        return sp(c).getInt("pin_color", 0);
    }

    static void setPinColor(Context c, int v) {
        sp(c).edit().putInt("pin_color", v).apply();
    }

    static void setGlow(Context c, boolean v) {
        sp(c).edit().putBoolean("glow", v).apply();
    }

    /** Id of the throw-away task used by the Settings test button, or -1. */
    static long testId(Context c) {
        return sp(c).getLong("test_id", -1);
    }

    static void setTestId(Context c, long v) {
        sp(c).edit().putLong("test_id", v).apply();
    }

    private static String key(String what, int kind) {
        return what + (kind == ALARM ? "_alarm" : kind == POMO ? "_pomo" : "_notif");
    }
}
