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
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(base);
        Pal.override(this, base); // the app's Light / Dark choice, not the phone's
    }

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
        if (url.isEmpty()) {          // plain text, no link: the first line is the title, the rest the description
            if (title.isEmpty()) {
                int nl = text.indexOf('\n');
                String first = (nl < 0 ? text : text.substring(0, nl)).trim();
                if (first.length() > 70) {
                    int sp = first.lastIndexOf(' ', 70);
                    first = first.substring(0, sp > 30 ? sp : 70).trim() + "\u2026";
                    desc = text;
                } else {
                    desc = nl < 0 ? "" : text.substring(nl + 1).trim();
                }
                title = first;
            } else {
                desc = text;
            }
        } else if (title.isEmpty()) {
            title = rest.isEmpty() ? host(url) : rest;
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

    /** The site name of a link, for a title when the share came with none. */
    private static String host(String url) {
        try {
            String h = android.net.Uri.parse(url.contains("://") ? url : "https://" + url).getHost();
            if (h == null) return url;
            return h.startsWith("www.") ? h.substring(4) : h;
        } catch (RuntimeException e) {
            return url;
        }
    }
}
