package app.privatetodo;

import android.content.Context;
import android.database.Cursor;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;

/**
 * Plays the chosen reminder / alarm sound at the chosen volume. The app plays the sound itself
 * (the notification channels are silent), which is what makes a per-sound volume slider possible.
 * Everything stays on the device: sounds come from the phone's own ringtones or a file you pick.
 */
final class Sound {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static MediaPlayer player;
    private static Runnable doneCb;
    private static Runnable timeout;

    private Sound() {
    }

    // ------------------------------------------------------------ choosing

    /** The sound to use: the user's pick, else the phone's default. null = silent. */
    static Uri resolve(Context c, int kind) {
        return resolve(c, kind, "");
    }

    /** {@code override} is a task's own sound; "" means use the Settings choice. */
    static Uri resolve(Context c, int kind, String override) {
        String s = override != null && !override.isEmpty() ? override : Prefs.sound(c, kind);
        if (Prefs.SILENT.equals(s)) return null;
        if (!s.isEmpty()) return Uri.parse(s);
        return defaultUri(kind);
    }

    static Uri defaultUri(int kind) {
        Uri u = RingtoneManager.getDefaultUri(
                kind == Prefs.ALARM ? RingtoneManager.TYPE_ALARM : RingtoneManager.TYPE_NOTIFICATION);
        if (u == null) u = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        return u;
    }

    /** Human-readable name of the current choice, for the Settings screen. */
    static String describe(Context c, int kind) {
        String s = Prefs.sound(c, kind);
        return s.isEmpty() ? "Phone default" : describeValue(c, s);
    }

    /** Name for a stored sound value. "" reads as "Same as Settings" (used for per-task sounds). */
    static String describeValue(Context c, String s) {
        if (s == null || s.isEmpty()) return "Same as Settings";
        if (Prefs.SILENT.equals(s)) return "Silent";
        Uri u = Uri.parse(s);
        String name = null;
        try (Cursor cur = c.getContentResolver().query(u, new String[]{OpenableColumns.DISPLAY_NAME},
                null, null, null)) {
            if (cur != null && cur.moveToFirst()) name = cur.getString(0);
        } catch (Exception ignored) {
            // fall through to the ringtone title
        }
        if (name == null || name.isEmpty()) {
            try {
                Ringtone r = RingtoneManager.getRingtone(c, u);
                if (r != null) name = r.getTitle(c);
            } catch (Exception ignored) {
                // fall through
            }
        }
        if (name == null || name.isEmpty()) return "Custom sound";
        int dot = name.lastIndexOf('.');
        return dot > 0 && name.length() - dot <= 5 ? name.substring(0, dot) : name;
    }

    // ------------------------------------------------------------ playing

    /**
     * Plays the sound for {@code kind}. {@code loop} repeats it until {@link #stop} or {@code maxMs}.
     * {@code done} runs once when playback ends for any reason (finished, timed out, replaced or
     * stopped), including immediately if there is nothing to play.
     */
    static void play(Context ctx, int kind, boolean loop, long maxMs, Runnable done) {
        play(ctx, kind, loop, maxMs, done, "", -1);
    }

    /** Same, with a task's own sound ("" = Settings) and volume 0..100 (-1 = Settings). */
    static synchronized void play(Context ctx, int kind, boolean loop, long maxMs, Runnable done,
                                  String soundOverride, int volumeOverride) {
        end(); // replaces anything already playing
        Context app = ctx.getApplicationContext();
        Uri uri = resolve(app, kind, soundOverride);
        if (uri == null) {
            if (done != null) done.run();
            return;
        }
        float vol = (volumeOverride >= 0 ? volumeOverride : Prefs.volume(app, kind)) / 100f;
        MediaPlayer mp = open(app, uri, kind, loop, vol);
        if (mp == null && !uri.equals(defaultUri(kind))) { // chosen file unreadable: use the default
            mp = open(app, defaultUri(kind), kind, loop, vol);
        }
        if (mp == null) {
            if (done != null) done.run();
            return;
        }
        player = mp;
        doneCb = done;
        if (!loop) mp.setOnCompletionListener(m -> end());
        timeout = Sound::end;
        MAIN.postDelayed(timeout, maxMs);
        try {
            mp.start();
        } catch (Exception e) {
            end();
        }
    }

    static synchronized void stop() {
        end();
    }

    /** Ends a Settings / editor preview, but never a real alarm that is ringing. */
    static synchronized void stopPreview() {
        if (!RingService.isRinging()) end();
    }

    /** Moves the volume of whatever is playing right now (used by the Settings slider). */
    static synchronized void setLiveVolume(int percent) {
        if (player == null) return;
        try {
            float v = Math.max(0, Math.min(100, percent)) / 100f;
            player.setVolume(v, v);
        } catch (Exception ignored) {
            // player was released
        }
    }

    private static MediaPlayer open(Context c, Uri uri, int kind, boolean loop, float vol) {
        MediaPlayer mp = new MediaPlayer();
        try {
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(kind == Prefs.ALARM ? AudioAttributes.USAGE_ALARM
                            : AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            mp.setDataSource(c, uri);
            mp.setLooping(loop);
            mp.setVolume(vol, vol);
            mp.prepare();
            return mp;
        } catch (Exception e) {
            mp.release();
            return null;
        }
    }

    private static synchronized void end() {
        if (timeout != null) {
            MAIN.removeCallbacks(timeout);
            timeout = null;
        }
        MediaPlayer p = player;
        player = null;
        Runnable d = doneCb;
        doneCb = null;
        if (p != null) {
            try {
                p.stop();
            } catch (Exception ignored) {
                // not started
            }
            p.release();
        }
        if (d != null) d.run();
    }
}
