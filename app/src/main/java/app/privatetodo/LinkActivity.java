package app.privatetodo;

import android.app.Activity;
import android.os.Bundle;

/** Invisible hop used by the notification's "Open link" button: silences the reminder, then opens the link. */
public class LinkActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        long id = getIntent().getLongExtra(Reminders.EXTRA_ID, -1);
        String url = getIntent().getStringExtra("url");
        if (id >= 0) Reminders.cancelNotification(this, id); // stops ring + vibration
        if (url != null) Links.open(this, url);
        finish();
    }
}
