package app.privatetodo;

/** One notebook page. {@code body} is RichText JSON; {@code plain} is the same text without formatting. */
final class Note {
    final long id;
    final String title;
    final String body;
    final String plain;
    final long updated;
    /** When it was pinned (0 = not pinned). */
    final long pinned;
    /** This note's own pinned colour (a PinTint style), or -1 to follow Settings. */
    final int pinColor;
    /** The folder it lives in (0 = none). */
    final long folder;

    Note(long id, String title, String body, String plain, long updated, long pinned, int pinColor, long folder) {
        this.id = id;
        this.title = title;
        this.body = body;
        this.plain = plain;
        this.updated = updated;
        this.pinned = pinned;
        this.pinColor = pinColor;
        this.folder = folder;
    }
}
