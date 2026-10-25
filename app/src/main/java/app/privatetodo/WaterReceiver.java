package app.privatetodo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Fired by AlarmManager at a water reminder time, and by the buttons of the water notification. */
public class WaterReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        String action = intent.getAction();
        if (WaterAlert.ACTION_ADD.equals(action)) {
            WaterAlert.add(ctx, intent.getIntExtra("amount", 0));
            WaterRingService.stopRinging(ctx);
            return;
        }
        if (WaterAlert.ACTION_STOP.equals(action)) {
            WaterRingService.stopRinging(ctx);
            return;
        }
        int slot = intent.getIntExtra("slot", 0);
        if (slot != WaterAlert.SNOOZE_SLOT) WaterAlert.scheduleAll(ctx); // arm tomorrow's time
        WaterAlert.fire(ctx, false);
    }
}
