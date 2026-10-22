package app.privatetodo;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.widget.Toast;

import java.util.regex.Matcher;

/** Target of Android's Share menu. Saves the shared text/link straight into Shared notes and returns to the source app. */
public class ShareReceiverActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent in = getIntent();
        String text = in == null ? null : in.getStringExtra(Intent.EXTRA_TEXT);
        String subject = in == null ? null : in.getStringExtra(Intent.EXTRA_SUBJECT);
        if (subject == null && in != null) subject = in.getStringExtra(Intent.EXTRA_TITLE);
        if (text == null || text.trim().isEmpty()) {
            Toast.makeText(this, "Nothing to save", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        text = text.trim();
        String url = "";
        String rest = text;
        Matcher m = Patterns.WEB_URL.matcher(text);
        if (m.find()) {
            url = m.group();
            rest = (text.substring(0, m.start()) + " " + text.substring(m.end())).trim();
        }
        String title = subject == null ? "" : subject.trim();
        String desc = "";
        if (url.isEmpty()) {          // plain text, no link: keep it all as the description
            url = "";
            desc = text;
        } else if (title.isEmpty()) {
            title = rest;
        } else {
            desc = rest;
        }
        TaskDb db = new TaskDb(this);
        try {
            db.addShared(url, title, desc);
            Toast.makeText(this, "Saved to Shared notes", Toast.LENGTH_SHORT).show();
        } finally {
            db.close();
        }
        finish();
    }
}
