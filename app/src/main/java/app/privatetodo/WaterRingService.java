package app.privatetodo;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import java.util.List;

/**
 * Rings the alarm sound a set number of times (Settings > Water reminders) and shows the full-screen water alert.
 * A foreground service so Android keeps the sound going while the screen is off. Local only.
 */
public class WaterRingService extends Service {
    private static volatile boolean ringing;
    private final Handler main = new Handler(Looper.getMainLooper());
    private int token;

    static boolean isRinging() {
        return ringing;
    }

    static void stopRinging(Context c) {
        c.stopService(new Intent(c, WaterRingService.class));
        c.getSystemService(NotificationManager.class).cancel(WaterAlert.NOTIF_ID);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification n = notification();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(WaterAlert.NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(WaterAlert.NOTIF_ID, n);
        }
        ringing = true;
        try { // with the "show over other apps" permission the screen can open even while the phone is in use
            if (android.provider.Settings.canDrawOverlays(this)) {
                startActivity(new Intent(this, WaterAlarmActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_USER_ACTION));
            }
        } catch (RuntimeException ignored) {
            // the notification's full-screen intent still covers the locked case
        }
        android.os.Vibrator vb = getSystemService(android.os.Vibrator.class);
        if (vb != null && vb.hasVibrator()) {
            vb.vibrate(android.os.VibrationEffect.createWaveform(new long[]{0, 500, 500}, 0),
                    new android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_ALARM).build());
        }
        token++;
        ring(WaterAlert.PLAYS, token);
        return START_NOT_STICKY;
    }

    private void ring(final int left, final int my) {
        if (my != token) return;
        if (left <= 0) {
            finishRinging();
            return;
        }
        Sound.play(this, Prefs.ALARM, false, 30_000L, () -> main.postDelayed(() -> ring(left - 1, my), 900));
    }

    /** All rings are done: the sound and vibration stop, the notification and the screen stay until you answer. */
    private void finishRinging() {
        android.os.Vibrator vb = getSystemService(android.os.Vibrator.class);
        if (vb != null) vb.cancel();
        stopForeground(Service.STOP_FOREGROUND_DETACH);
        stopSelf();
    }

    private Notification notification() {
        Reminders.ensureChannels(this);
        Habit h = WaterAlert.habit(this);
        int amt = h == null ? 0 : WaterAlert.todayAmount(this, h);
        String text = h == null ? "Time to drink some water"
                : "Time to drink water · " + h.progress(amt);
        PendingIntent open = PendingIntent.getActivity(this, 7201,
                new Intent(this, WaterAlarmActivity.class).setData(Uri.parse("todo://water-screen"))
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_USER_ACTION),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b = new Notification.Builder(this, Reminders.CH_ALARM)
                .setSmallIcon(R.drawable.ic_notify)
                .setContentTitle("Drink water")
                .setContentText(text)
                .setContentIntent(open)
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setTimeoutAfter(30 * 60 * 1000L)
                .setFullScreenIntent(open, true);
        Icon icon = Icon.createWithResource(this, R.drawable.ic_notify);
        if (h != null) {
            List<Integer> q = h.quickAmounts();
            int step = q.isEmpty() ? h.step : q.get(0);
            Intent add = new Intent(this, WaterReceiver.class).setAction(WaterAlert.ACTION_ADD)
                    .setData(Uri.parse("todo://water/add/" + step)).putExtra("amount", step);
            b.addAction(new Notification.Action.Builder(icon, "+" + step + (h.unit.isEmpty() ? "" : " " + h.unit),
                    PendingIntent.getBroadcast(this, 7202, add,
                            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT)).build());
        }
        Intent stop = new Intent(this, WaterReceiver.class).setAction(WaterAlert.ACTION_STOP)
                .setData(Uri.parse("todo://water/stop"));
        b.addAction(new Notification.Action.Builder(icon, "Dismiss",
                PendingIntent.getBroadcast(this, 7203, stop,
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT)).build());
        return b.build();
    }

    @Override
    public void onDestroy() {
        token++; // any ring still queued must not start
        ringing = false;
        android.os.Vibrator vb = getSystemService(android.os.Vibrator.class);
        if (vb != null) vb.cancel();
        Sound.stop();
        main.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
