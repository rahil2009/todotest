package app.privatetodo;

import android.content.Context;
import android.content.SharedPreferences;

/** User settings, stored only in this app's private storage. */
final class Prefs {
    static final int NOTIF = 0;
    static final int ALARM = 1;

    static final int THEME_SYSTEM = 0;
    static final int THEME_LIGHT = 1;
    static final int THEME_DARK = 2;

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

    private static String key(String what, int kind) {
        return what + (kind == ALARM ? "_alarm" : "_notif");
    }
}
