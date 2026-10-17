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

    Habit(long id, String name, String emoji, int color, int goal, String unit, int step, long created) {
        this.id = id;
        this.name = name;
        this.emoji = emoji;
        this.color = color;
        this.goal = goal;
        this.unit = unit;
        this.step = Math.max(1, step);
        this.created = created;
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
