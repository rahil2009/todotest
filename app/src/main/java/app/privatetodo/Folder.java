package app.privatetodo;

/** A notebook folder: a name, a colour (index into {@link #COLORS}) and how many notes it holds. */
final class Folder {
    static final int[] COLORS = {0xFF0B6B5D, 0xFFF2A93B, 0xFFE9677B, 0xFF5AA9E6, 0xFF8E7CE0, 0xFF3FB8AF, 0xFFF97316, 0xFF64748B};
    static final String[] COLOR_NAMES = {"Green", "Amber", "Rose", "Sky", "Violet", "Teal", "Orange", "Slate"};

    final long id;
    final String name;
    final int color;
    final int count;

    Folder(long id, String name, int color, int count) {
        this.id = id;
        this.name = name;
        this.color = Math.max(0, Math.min(COLORS.length - 1, color));
        this.count = count;
    }

    int rgb() {
        return COLORS[color];
    }
}
