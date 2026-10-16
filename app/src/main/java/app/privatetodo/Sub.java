package app.privatetodo;

/** A checklist item that belongs to one task. */
final class Sub {
    final long id;
    final long taskId;
    final String title;
    final boolean done;

    Sub(long id, long taskId, String title, boolean done) {
        this.id = id;
        this.taskId = taskId;
        this.title = title;
        this.done = done;
    }
}
