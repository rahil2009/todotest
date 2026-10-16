package app.privatetodo;

/** One notebook page. {@code body} is RichText JSON; {@code plain} is the same text without formatting. */
final class Note {
    final long id;
    final String title;
    final String body;
    final String plain;
    final long updated;

    Note(long id, String title, String body, String plain, long updated) {
        this.id = id;
        this.title = title;
        this.body = body;
        this.plain = plain;
        this.updated = updated;
    }
}
