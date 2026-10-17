package app.privatetodo;

import android.content.Context;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns "call mom @sat 2pm" into a task "call mom" due on Saturday at 14:00.
 * Only text after the trigger symbol (default @) is read, so ordinary titles are never touched.
 * Understands today, tomorrow, weekdays, "next sunday", "in 2 hours", "dec 25", clock times,
 * and repeats such as "every monday 2pm", "every 3 days", "every weekday 9am", "daily".
 * Your own shortcuts from Settings (e.g. "gym" = "every mon wed fri 6pm") are expanded first.
 */
final class SmartDate {
    private SmartDate() { }

    static final String DEFAULT_ALIASES = "morning=8am\nnoon=12pm\nafternoon=3pm\nevening=6pm\n"
            + "night=9pm\neod=5pm\nweekend=sat 10am\nnextweek=next mon 9am";

    static final class Result {
        boolean found;
        String title;
        long due;
        int repeat;
        int unit;
    }

    private static final String[] DAYS = {"mon", "tue", "wed", "thu", "fri", "sat", "sun"};
    private static final String[] MONTHS = {"jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep",
            "oct", "nov", "dec"};
    private static final Pattern TIME = Pattern.compile("^(\\d{1,2})(?::(\\d{2}))?(am|pm|a|p)?$");
    private static final Pattern NUM = Pattern.compile("^(\\d{1,2})(?:st|nd|rd|th)?$");

    static Map<String, String> aliases(Context c) {
        Map<String, String> m = new HashMap<>();
        for (String line : Prefs.smartAliases(c).split("\n")) {
            int eq = line.indexOf('=');
            if (eq <= 0) continue;
            String k = line.substring(0, eq).trim().toLowerCase(Locale.ROOT);
            String v = line.substring(eq + 1).trim();
            if (!k.isEmpty() && !v.isEmpty()) m.put(k, v);
        }
        return m;
    }

    static Result parse(Context ctx, String input, long now) {
        Result r = new Result();
        r.title = input;
        if (!Prefs.smartOn(ctx)) return r;
        String trig = Prefs.smartTrigger(ctx);
        Matcher mt = Pattern.compile("(^|\\s)" + Pattern.quote(trig) + "(?=\\S)").matcher(input);
        if (!mt.find()) return r;
        int trigStart = mt.start() + mt.group(1).length();
        int phraseStart = trigStart + trig.length();
        String phrase = input.substring(phraseStart);

        Map<String, String> al = aliases(ctx);
        List<String> toks = new ArrayList<>();
        List<Integer> ownerEnd = new ArrayList<>(); // end offset (in phrase) of the typed word each token came from
        Matcher tm = Pattern.compile("\\S+").matcher(phrase);
        while (tm.find()) {
            String w = tm.group().toLowerCase(Locale.ROOT).replaceAll("[.;!?]+$", "");
            String use = al.containsKey(w) ? al.get(w) : w;
            for (String part : use.toLowerCase(Locale.ROOT).split("[\\s,]+")) {
                if (part.isEmpty()) continue;
                toks.add(part);
                ownerEnd.add(tm.end());
            }
        }
        P p = new P(toks);
        int p0 = 0;
        int used = 0;
        while (p0 < toks.size()) {
            int n = p.step(p0);
            if (n == 0) break;
            p0 += n;
            used = p0;
        }
        if (used == 0 || !p.any) return r;

        int end = phraseStart + ownerEnd.get(used - 1);
        String title = (input.substring(0, trigStart) + " " + input.substring(Math.min(end, input.length())))
                .replaceAll("\\s+", " ").trim();
        r.title = title.isEmpty() ? "Reminder" : title;
        r.found = true;

        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(now);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);

        if (p.relAmount > 0) {
            Calendar d = (Calendar) c.clone();
            d.add(p.relField, p.relAmount);
            r.due = d.getTimeInMillis();
        } else if (p.hour < 0 && p.dayOffset < 0 && p.weekday < 0 && p.mask == 0 && p.month < 0
                && p.everyUnit >= 0 && p.everyUnit <= 1) {
            long step = p.everyN * (p.everyUnit == 0 ? 60_000L : 3_600_000L);
            r.due = now + step; // "every 30 min" starts 30 min from now
        } else {
            boolean timeGiven = p.hour >= 0;
            int h = timeGiven ? p.hour : Prefs.smartHour(ctx);
            int mi = timeGiven ? p.minute : Prefs.smartMinute(ctx);
            Calendar d = (Calendar) c.clone();
            d.set(Calendar.HOUR_OF_DAY, h);
            d.set(Calendar.MINUTE, mi);
            if (p.month >= 0) {
                d.set(Calendar.MONTH, p.month);
                d.set(Calendar.DAY_OF_MONTH, p.dom);
                if (d.before(c)) d.add(Calendar.YEAR, 1);
            } else if (p.weekday >= 0 || p.mask != 0) {
                int today = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7;
                for (int k = p.next ? 1 : 0; k <= 14; k++) {
                    int idx = (today + k) % 7;
                    boolean ok = p.weekday >= 0 ? idx == p.weekday : (p.mask & (1 << idx)) != 0;
                    if (!ok) continue;
                    Calendar d2 = (Calendar) d.clone();
                    d2.add(Calendar.DAY_OF_YEAR, k);
                    if (d2.after(c)) {
                        d = d2;
                        break;
                    }
                }
            } else if (p.dayOffset >= 0) {
                d.add(Calendar.DAY_OF_YEAR, p.dayOffset);
                if (p.monthOffset > 0) d.add(Calendar.MONTH, p.monthOffset);
            } else if (p.monthOffset > 0) {
                d.add(Calendar.MONTH, p.monthOffset);
            } else if (!d.after(c)) {
                d.add(Calendar.DAY_OF_YEAR, 1); // a bare time that already passed means tomorrow
            }
            r.due = d.getTimeInMillis();
        }
        if (p.mask != 0) {
            r.repeat = p.mask;
            r.unit = 5;
        } else if (p.everyUnit >= 0) {
            r.repeat = p.everyN;
            r.unit = p.everyUnit;
        } else if (p.weekday >= 0 && p.every) {
            r.repeat = 1 << p.weekday;
            r.unit = 5;
        } else {
            r.repeat = 0;
            r.unit = 2;
        }
        return r;
    }

    /** What the parser found, in words, for the Settings preview. */
    static String describe(Context c, Result r) {
        if (!r.found) return "No date found. Type e.g. buy milk @tomorrow 7pm";
        String s = "“" + r.title + "”  →  " + Reminders.when(c, r.due);
        if (r.repeat > 0) s += "  ·  " + Reminders.repeatLabel(r.repeat, r.unit).toLowerCase(Locale.ROOT);
        return s;
    }

    // ------------------------------------------------------------------ token grammar

    private static final class P {
        final List<String> t;
        boolean any;
        boolean atFlag;
        boolean next;
        boolean every;
        int hour = -1;
        int minute;
        int dayOffset = -1;
        int monthOffset;
        int weekday = -1;
        int mask;
        int month = -1;
        int dom;
        int everyN;
        int everyUnit = -1;
        int relAmount;
        int relField = Calendar.MINUTE;

        P(List<String> t) {
            this.t = t;
        }

        private String at(int i) {
            return i < t.size() ? t.get(i) : "";
        }

        private int day(String s) {
            for (int i = 0; i < 7; i++) {
                if (s.startsWith(DAYS[i]) && s.length() <= 9) {
                    String full = new String[]{"monday", "tuesday", "wednesday", "thursday", "friday",
                            "saturday", "sunday"}[i];
                    if (full.startsWith(s) || s.equals("tues") || s.equals("thur") || s.equals("thurs")) return i;
                }
            }
            return -1;
        }

        private int month(String s) {
            for (int i = 0; i < 12; i++) {
                if (s.startsWith(MONTHS[i]) && s.length() >= 3 && s.length() <= 9) {
                    String[] full = {"january", "february", "march", "april", "may", "june", "july", "august",
                            "september", "october", "november", "december"};
                    if (full[i].startsWith(s) || s.equals("sept")) return i;
                }
            }
            return -1;
        }

        /** 0 min, 1 hour, 2 day, 3 week, 4 month; -1 if the word is not a unit. */
        private int unit(String s) {
            switch (s) {
                case "min": case "mins": case "minute": case "minutes": return 0;
                case "hr": case "hrs": case "hour": case "hours": return 1;
                case "day": case "days": return 2;
                case "week": case "weeks": case "wk": case "wks": return 3;
                case "month": case "months": return 4;
                default: return -1;
            }
        }

        private int number(String s) {
            if (s.equals("a") || s.equals("an")) return 1;
            return s.matches("\\d{1,3}") ? Integer.parseInt(s) : -1;
        }

        /** Tokens consumed starting at i, or 0 if nothing there is a date word. */
        int step(int i) {
            if (i >= t.size()) return 0;
            String w = t.get(i);
            switch (w) {
                case "at": case "on": case "by": case "this": case "from": case "the": {
                    boolean old = atFlag;
                    if (w.equals("at")) atFlag = true;
                    int n = step(i + 1);
                    atFlag = old;
                    return n > 0 ? n + 1 : 0;
                }
                case "next": {
                    String u = at(i + 1);
                    if (u.equals("week")) {
                        dayOffset = 7;
                        any = true;
                        return 2;
                    }
                    if (u.equals("month")) {
                        monthOffset = 1;
                        any = true;
                        return 2;
                    }
                    boolean old = next;
                    next = true;
                    int n = step(i + 1);
                    if (n == 0) next = old;
                    return n > 0 ? n + 1 : 0;
                }
                case "today":
                    dayOffset = 0;
                    any = true;
                    return 1;
                case "tonight":
                    dayOffset = 0;
                    if (hour < 0) {
                        hour = 21;
                        minute = 0;
                    }
                    any = true;
                    return 1;
                case "tomorrow": case "tmrw": case "tmr":
                    dayOffset = 1;
                    any = true;
                    return 1;
                case "noon":
                    hour = 12;
                    minute = 0;
                    any = true;
                    return 1;
                case "midnight":
                    hour = 0;
                    minute = 0;
                    any = true;
                    return 1;
                case "daily": case "everyday":
                    return rep(1, 2, 1);
                case "weekly":
                    return rep(1, 3, 1);
                case "monthly":
                    return rep(1, 4, 1);
                case "hourly":
                    return rep(1, 1, 1);
                case "every": case "each":
                    return everyClause(i);
                case "in":
                    return inClause(i);
                default:
                    break;
            }
            int d = day(w);
            if (d >= 0) {
                weekday = d;
                any = true;
                return 1;
            }
            // dates: "dec 25", "25 dec", "25th december"
            int mo = month(w);
            if (mo >= 0) {
                Matcher m = NUM.matcher(at(i + 1));
                if (m.matches() && validDom(m.group(1))) {
                    month = mo;
                    dom = Integer.parseInt(m.group(1));
                    any = true;
                    return 2;
                }
            }
            Matcher nm = NUM.matcher(w);
            if (nm.matches() && month(at(i + 1)) >= 0 && validDom(nm.group(1))
                    && !w.matches("\\d{1,2}(am|pm)")) {
                month = month(at(i + 1));
                dom = Integer.parseInt(nm.group(1));
                any = true;
                return 2;
            }
            Matcher tmt = TIME.matcher(w);
            if (tmt.matches()) {
                int h = Integer.parseInt(tmt.group(1));
                int mi = tmt.group(2) == null ? 0 : Integer.parseInt(tmt.group(2));
                String ap = tmt.group(3);
                int used = 1;
                if (ap == null && tmt.group(2) == null) {
                    String nx = at(i + 1);
                    if (nx.equals("am") || nx.equals("pm") || nx.equals("a") || nx.equals("p")) {
                        ap = nx;
                        used = 2;
                    } else if (!atFlag) {
                        return 0; // a lone number such as "3" is not a time unless it follows "at"
                    }
                }
                if (mi > 59) return 0;
                if (ap != null) {
                    if (h < 1 || h > 12) return 0;
                    boolean pm = ap.startsWith("p");
                    h = h % 12 + (pm ? 12 : 0);
                } else if (h > 23) {
                    return 0;
                }
                hour = h;
                minute = mi;
                any = true;
                return used;
            }
            return 0;
        }

        private boolean validDom(String s) {
            int v = Integer.parseInt(s);
            return v >= 1 && v <= 31;
        }

        private int rep(int n, int unit, int used) {
            everyN = n;
            everyUnit = unit;
            every = true;
            any = true;
            return used;
        }

        private int everyClause(int i) {
            int q = i + 1;
            String u = at(q);
            if (u.isEmpty()) return 0;
            if (u.equals("day")) return rep(1, 2, 2);
            if (u.equals("weekday") || u.equals("weekdays")) {
                mask = 31;
                every = true;
                any = true;
                return 2;
            }
            if (u.equals("weekend") || u.equals("weekends")) {
                mask = 96;
                every = true;
                any = true;
                return 2;
            }
            int un = unit(u);
            if (un >= 0) return rep(1, un, 2);
            int n = number(u);
            if (n > 0 && unit(at(q + 1)) >= 0) return rep(n, unit(at(q + 1)), 3);
            int count = 0;
            int m = 0;
            while (day(at(q + count)) >= 0) {
                m |= 1 << day(at(q + count));
                count++;
                if (at(q + count).equals("and")) count++; // "every mon and fri"
            }
            if (m != 0) {
                mask = m;
                every = true;
                any = true;
                return 1 + count;
            }
            return 0;
        }

        private int inClause(int i) {
            int n = number(at(i + 1));
            int un = unit(at(i + 2));
            if (n <= 0 || un < 0) return 0;
            relAmount = n;
            relField = un == 0 ? Calendar.MINUTE : un == 1 ? Calendar.HOUR_OF_DAY : un == 2
                    ? Calendar.DAY_OF_YEAR : un == 3 ? Calendar.WEEK_OF_YEAR : Calendar.MONTH;
            any = true;
            return 3;
        }
    }
}
