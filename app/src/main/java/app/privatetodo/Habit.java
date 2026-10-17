package app.privatetodo;

/** One habit: a name, emoji, colour and an optional daily goal (0 = just tick it off). */
final class Habit {
    final long id;
    final String name;
    final String emoji;
    final int color;
    final int goal;
    final String unit;
    final int step;
    final long created;
    /** Extra one-tap amounts besides {@link #step}, e.g. "100,500" for a water habit. */
    final String extras;

    Habit(long id, String name, String emoji, int color, int goal, String unit, int step, long created, String extras) {
        this.id = id;
        this.name = name;
        this.emoji = emoji;
        this.color = color;
        this.goal = goal;
        this.unit = unit;
        this.step = Math.max(1, step);
        this.created = created;
        this.extras = extras == null ? "" : extras;
    }

    /** The quick amounts to offer: the main step first, then the extras, without repeats. */
    java.util.List<Integer> quickAmounts() {
        java.util.LinkedHashSet<Integer> set = new java.util.LinkedHashSet<>();
        set.add(step);
        for (String s : extras.split("[,;\\s]+")) {
            try {
                int v = Integer.parseInt(s.trim());
                if (v > 0) set.add(v);
            } catch (NumberFormatException ignored) {
                // skip junk
            }
        }
        return new java.util.ArrayList<>(set);
    }

    /** "200/1000 ml", "0/30 min", or "Done" / "Not done" for tick-only habits. */
    String progress(int amount) {
        if (amount < 0) return "Skipped";
        if (goal <= 0) return amount > 0 ? "Done" : "Not done";
        return amount + "/" + goal + (unit.isEmpty() ? "" : " " + unit);
    }

    boolean doneAt(int amount) {
        return amount > 0 && (goal <= 0 || amount >= goal);
    }
}
