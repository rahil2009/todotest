package app.privatetodo;

import android.text.util.Linkify;
import android.util.Patterns;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;

/** Finds web links in task text and makes them tappable. Opening one hands off to your browser. */
final class Links {
    private static int linkColor() {
        return Pal.accent;
    }

    private Links() {
    }

    /** All distinct web links in the given texts, in order of appearance. */
    static List<String> find(String... texts) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String text : texts) {
            if (text == null) continue;
            Matcher m = Patterns.WEB_URL.matcher(text);
            while (m.find()) out.add(m.group());
        }
        return new ArrayList<>(out);
    }

    /** "example.com/x" becomes "https://example.com/x". */
    static String normalize(String url) {
        return url.matches("(?i)^[a-z][a-z0-9+.-]*://.*") ? url : "https://" + url;
    }

    static void linkify(TextView t) {
        linkify(t, linkColor());
    }

    /**
     * Underlines links and makes them tappable. The TextView itself is left non-clickable so a tap
     * on plain text falls through to the parent (which opens the editor) while a tap on a link opens it.
     */
    static void linkify(TextView t, int color) {
        Linkify.addLinks(t, Linkify.WEB_URLS | Linkify.EMAIL_ADDRESSES);
        t.setLinkTextColor(color);
        t.setClickable(false);
        t.setFocusable(false);
        t.setLongClickable(false);
    }
}
