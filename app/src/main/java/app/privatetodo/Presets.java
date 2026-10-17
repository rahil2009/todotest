package app.privatetodo;

import android.content.Context;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * The quick "Remind me" buttons in the task editor. Each is a short spec you can add or remove:
 * "in:N:unit" (N min/hours/days/weeks/months from now), "at:H:M" (next time it is H:M) and
 * "tom:H:M" (tomorrow at H:M). Stored only on this device.
 */
final class Presets {
    private static final String[] ONE = {"min", "hour", "day", "week", "month"};
    private static final String[] MANY = {"min", "hours", "days", "weeks", "months"};
    private static final String DEFAULTS = "in:1:1;at:20:0;tom:9:0";

    private Presets() {
    }

    static List<String> load(Context c) {
        String raw = Prefs.presets(c, DEFAULTS);
        List<String> out = new ArrayList<>();
        for (String s : raw.split(";")) {
            if (!s.trim().isEmpty() && parse(s) != null) out.add(s.trim());
        }
        return out;
    }

    static void save(Context c, List<String> specs) {
        StringBuilder sb = new StringBuilder();
        for (String s : specs) sb.append(sb.length() > 0 ? ";" : "").append(s);
        Prefs.setPresets(c, sb.toString());
    }

    static void add(Context c, String spec) {
        List<String> l = load(c);
        if (!l.contains(spec)) l.add(spec);
        save(c, l);
    }

    static void remove(Context c, String spec) {
        List<String> l = load(c);
        l.remove(spec);
        save(c, l);
    }

    static void reset(Context c) {
        Prefs.setPresets(c, DEFAULTS);
    }

    private static int[] parse(String spec) {
        try {
            String[] p = spec.trim().split(":");
            int a = Integer.parseInt(p[1]);
            int b = Integer.parseInt(p[2]);
            int kind = p[0].equals("in") ? 0 : p[0].equals("at") ? 1 : p[0].equals("tom") ? 2 : -1;
            return kind < 0 ? null : new int[]{kind, a, b};
        } catch (RuntimeException e) {
            return null;
        }
    }

    static String label(String spec) {
        int[] p = parse(spec);
        if (p == null) return spec;
        if (p[0] == 0) {
            int u = Math.max(0, Math.min(4, p[2]));
            return "In " + (p[1] == 1 ? "1 " + ONE[u] : p[1] + " " + MANY[u]);
        }
        String hm = String.format(java.util.Locale.US, "%02d:%02d", p[1], p[2]);
        return p[0] == 1 ? "At " + hm : "Tomorrow " + hm;
    }

    static String specIn(int amount, int unit) {
        return "in:" + amount + ":" + unit;
    }

    static String specAt(boolean tomorrow, int hour, int minute) {
        return (tomorrow ? "tom:" : "at:") + hour + ":" + minute;
    }

    /** The moment this preset points to, measured from now. */
    static long time(String spec) {
        int[] p = parse(spec);
        Calendar c = Calendar.getInstance();
        if (p == null) return c.getTimeInMillis();
        if (p[0] == 0) {
            int n = p[1];
            switch (p[2]) {
                case 0:
                    c.add(Calendar.MINUTE, n);
                    break;
                case 1:
                    c.add(Calendar.HOUR_OF_DAY, n);
                    break;
                case 2:
                    c.add(Calendar.DAY_OF_YEAR, n);
                    break;
                case 3:
                    c.add(Calendar.DAY_OF_YEAR, 7 * n);
                    break;
                default:
                    c.add(Calendar.MONTH, n);
                    break;
            }
        } else {
            c.set(Calendar.HOUR_OF_DAY, p[1]);
            c.set(Calendar.MINUTE, p[2]);
            if (p[0] == 2) {
                c.add(Calendar.DAY_OF_YEAR, 1);
            } else if (c.getTimeInMillis() <= System.currentTimeMillis()) {
                c.add(Calendar.DAY_OF_YEAR, 1);
            }
        }
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }
}
