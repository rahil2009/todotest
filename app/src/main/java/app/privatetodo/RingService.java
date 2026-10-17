package app.privatetodo;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

/**
 * Keeps an alarm-style reminder ringing (looping your chosen sound at your chosen volume) until you
 * press Done / Snooze / Dismiss, or after 10 minutes. It is a foreground service so Android does not
 * silence it while the screen is off. Local only, no network.
 */
public class RingService extends Service {
    private static final long MAX_MS = 10 * 60 * 1000L;
    private static volatile long ringingId = -1;

    static void start(Context c, long id) {
        start(c, id, false);
    }

    /** {@code quiet}: the alarm screen is already open, so the notification stays silent and we vibrate ourselves. */
    static void start(Context c, long id, boolean quiet) {
        c.startForegroundService(new Intent(c, RingService.class)
                .putExtra(Reminders.EXTRA_ID, id).putExtra("quiet", quiet));
    }

    static boolean isRinging() {
        return ringingId >= 0;
    }

    /** Stops the ringing if it belongs to this task. */
    static void stopFor(Context c, long id) {
        if (ringingId == id) c.stopService(new Intent(c, RingService.class));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        long id = intent == null ? -1 : intent.getLongExtra(Reminders.EXTRA_ID, -1);
        TaskDb db = new TaskDb(this);
        Task t;
        java.util.List<Sub> subs = null;
        try {
            t = db.get(id);
            if (t != null) subs = db.subs(id);
        } finally {
            db.close();
        }
        if (t == null || t.done) {
            // startForegroundService() requires a notification before we may quit.
            startForeground(1, new Notification.Builder(this, Reminders.CH_REMIND)
                    .setSmallIcon(R.drawable.ic_notify).build());
            stopSelf();
            return START_NOT_STICKY;
        }
        boolean quiet = intent != null && intent.getBooleanExtra("quiet", false);
        Notification n = Reminders.build(this, t, subs, quiet);
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground((int) id, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground((int) id, n);
        }
        ringingId = id;
        if (quiet) {
            android.os.Vibrator vb = getSystemService(android.os.Vibrator.class);
            if (vb != null && vb.hasVibrator()) {
                vb.vibrate(android.os.VibrationEffect.createWaveform(
                                new long[]{0, 600, 400, 600, 400, 600}, 0),
                        new android.media.AudioAttributes.Builder()
                                .setUsage(android.media.AudioAttributes.USAGE_ALARM).build());
            }
        }
        final long mine = id;
        // If a second alarm takes over, the first one's "done" must not stop the service.
        Sound.play(this, Prefs.ALARM, true, MAX_MS, () -> {
            if (ringingId == mine) stopSelf();
        }, t.sound, t.volume);
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        ringingId = -1;
        android.os.Vibrator vb = getSystemService(android.os.Vibrator.class);
        if (vb != null) vb.cancel();
        Sound.stop();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
