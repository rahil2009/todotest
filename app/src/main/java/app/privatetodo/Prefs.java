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
