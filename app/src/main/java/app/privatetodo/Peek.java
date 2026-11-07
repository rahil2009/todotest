package app.privatetodo;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;

/**
 * The lock-screen peek for ordinary (non full-screen) reminders: when one arrives while the phone is locked or
 * asleep, the screen wakes to a dim black page with the reminder for a few seconds, like an always-on display,
 * then goes dark. It rides on the notification's own full-screen intent: Android only launches that when the phone
 * is locked or idle, and shows a normal pop-in when you are using the phone, so nothing here can interrupt you.
 */
final class Peek {
    static final String EXTRA_TITLE = "title";
    static final String EXTRA_TEXT = "text";

    private Peek() {
    }

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    static boolean enabled(Context c) {
        return sp(c).getBoolean("peek_on", true);
    }

    static void setEnabled(Context c, boolean v) {
        sp(c).edit().putBoolean("peek_on", v).apply();
    }

    /** How long the reminder stays lit before fading to black. */
    static int seconds(Context c) {
        return sp(c).getInt("peek_secs", 6);
    }

    static void setSeconds(Context c, int s) {
        sp(c).edit().putInt("peek_secs", s).apply();
    }

    static PendingIntent intent(Context c, long id, String title, String text) {
        Intent i = new Intent(c, PeekActivity.class)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_TEXT, text)
                .setData(Uri.parse("todo://peek/" + id))
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_USER_ACTION
                        | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        return PendingIntent.getActivity(c, (int) id + 3_000_000, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
