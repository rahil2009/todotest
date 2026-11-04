package app.privatetodo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;

/**
 * Page backgrounds for notes: grid, dot, lined and tinted papers, drawn behind the text of every note
 * (Settings > Notebook > Page background). Lined papers follow the note's own line height.
 */
final class Paper {
    static final String[] NAMES = {"Plain",
            "Grid", "Fine grid", "Major grid", "Dots", "Dense dots", "Lined", "Lined + margin", "Wide lined",
            "Cornell", "Isometric", "Hex", "Diagonal", "Crosshatch", "Music staff", "Engineering", "Plus marks",
            "Dashed lines", "Legal pad", "Kraft", "Blueprint"};

    private static final Paint P = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Path PATH = new Path();

    private Paper() {
    }

    /** Paper colours: index 0 is none (the page colour), then ten soft tints. Light and dark versions. */
    static final String[] COLOR_NAMES = {"None", "Cream", "Sand", "Lemon", "Peach", "Rose", "Lavender", "Sky", "Mint",
            "Sage", "Slate"};
    private static final int[] LIGHT = {0, 0xFFFBF3DF, 0xFFF3E7D3, 0xFFFFF8D1, 0xFFFDE8DC, 0xFFFBE7EA, 0xFFEEE9FB,
            0xFFE5F0FB, 0xFFE6F5EC, 0xFFE9EFE2, 0xFFE9ECEF};
    private static final int[] DARK = {0, 0xFF2A2619, 0xFF2C2519, 0xFF2E2B17, 0xFF2F221B, 0xFF2E1E22, 0xFF241F33,
            0xFF1A2430, 0xFF1A2A22, 0xFF20271C, 0xFF22262A};

    static int tint(int index) {
        if (index <= 0 || index >= LIGHT.length) return 0;
        return Pal.dark ? DARK[index] : LIGHT[index];
    }

    private static android.content.SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    /** The style for all notes. */
    static int chosen(Context c) {
        return sp(c).getInt("note_paper", 0);
    }

    static void setChosen(Context c, int v) {
        sp(c).edit().putInt("note_paper", v).apply();
    }

    /** The colour for all notes (index into COLOR_NAMES). */
    static int chosenColor(Context c) {
        return sp(c).getInt("note_paper_col", 0);
    }

    static void setChosenColor(Context c, int v) {
        sp(c).edit().putInt("note_paper_col", v).apply();
    }

    /** One note's own style / colour, or -1 to follow the setting for all notes. */
    static int ownStyle(Context c, long id) {
        return id <= 0 ? -1 : sp(c).getInt("paper_s" + id, -1);
    }

    static int ownColor(Context c, long id) {
        return id <= 0 ? -1 : sp(c).getInt("paper_c" + id, -1);
    }

    static void setOwn(Context c, long id, int style, int color) {
        if (id <= 0) return;
        android.content.SharedPreferences.Editor e = sp(c).edit();
        if (style < 0) e.remove("paper_s" + id); else e.putInt("paper_s" + id, style);
        if (color < 0) e.remove("paper_c" + id); else e.putInt("paper_c" + id, color);
        e.apply();
    }

    static int styleFor(Context c, long id) {
        int s = ownStyle(c, id);
        return s >= 0 ? s : chosen(c);
    }

    static int colorFor(Context c, long id) {
        int s = ownColor(c, id);
        return s >= 0 ? s : chosenColor(c);
    }

    private static int ink(int alpha) {
        return (Pal.ink & 0x00FFFFFF) | (alpha << 24);
    }

    /**
     * Draws style {@code s} over {@code clip} (view coordinates). {@code lineH} is the text's line height,
     * {@code top} where the first line starts, {@code left}/{@code right} the text column, d the density.
     */
    static void draw(Canvas c, int s, Rect clip, float lineH, float top, float left, float right, float d) {
        if (s <= 0 || s >= NAMES.length) return;
        P.setStyle(Paint.Style.STROKE);
        P.setStrokeWidth(Math.max(1f, d * 0.8f));
        P.setPathEffect(null);
        float x0 = clip.left;
        float x1 = clip.right;
        float y0 = clip.top;
        float y1 = clip.bottom;
        float g = 20 * d; // grid step
        boolean darkPage = Pal.dark;
        switch (s) {
            case 1:
                grid(c, clip, g, ink(darkPage ? 30 : 26));
                break;
            case 2:
                grid(c, clip, 12 * d, ink(darkPage ? 22 : 18));
                break;
            case 3:
                grid(c, clip, 10 * d, ink(darkPage ? 16 : 14));
                P.setStrokeWidth(Math.max(1f, d * 1.2f));
                grid(c, clip, 50 * d, ink(darkPage ? 40 : 34));
                break;
            case 4:
                dots(c, clip, g, 1.3f * d, ink(darkPage ? 70 : 60));
                break;
            case 5:
                dots(c, clip, 12 * d, 1f * d, ink(darkPage ? 60 : 50));
                break;
            case 6:
            case 7:
            case 8:
            case 9:
            case 17:
            case 18: {
                float step = s == 8 ? lineH * 2 : lineH;
                if (s == 18) { // legal pad: yellow paper
                    fill(c, clip, darkPage ? 0x1AFFE680 : 0x40FFEB8A);
                }
                P.setColor(s == 18 ? (darkPage ? 0x4D7FB4FF : 0x5C4A86E0) : ink(darkPage ? 34 : 30));
                if (s == 17) P.setPathEffect(new android.graphics.DashPathEffect(new float[]{6 * d, 5 * d}, 0));
                float first = top + step;
                float start = first + (float) Math.floor(Math.max(0, y0 - first) / step) * step;
                for (float y = start; y < y1; y += step) c.drawLine(x0, y, x1, y, P);
                P.setPathEffect(null);
                if (s == 7 || s == 18) { // the red margin
                    P.setColor(darkPage ? 0x80FF6B6B : 0x99E05050);
                    float mx = left - 8 * d;
                    c.drawLine(mx, y0, mx, y1, P);
                }
                if (s == 9) { // Cornell: a cue column on the left
                    P.setColor(ink(darkPage ? 50 : 44));
                    float cx = left + (right - left) * 0.28f;
                    c.drawLine(cx, Math.max(y0, top), cx, y1, P);
                }
                break;
            }
            case 10: { // isometric dots: every other row shifted half a step
                float step = 18 * d;
                float rowH = step * 0.866f;
                P.setStyle(Paint.Style.FILL);
                P.setColor(ink(darkPage ? 70 : 60));
                int r0 = (int) Math.floor(y0 / rowH);
                for (int r = r0; r * rowH < y1; r++) {
                    float off = (r & 1) == 0 ? 0 : step / 2f;
                    float sx = (float) Math.floor((x0 - off) / step) * step + off;
                    for (float x = sx; x < x1; x += step) c.drawCircle(x, r * rowH, 1.2f * d, P);
                }
                break;
            }
            case 11: { // hexagons
                float rr = 12 * d;
                float w = rr * 1.732f;
                float h = rr * 1.5f;
                P.setColor(ink(darkPage ? 28 : 24));
                int r0 = (int) Math.floor(y0 / h) - 1;
                for (int r = r0; r * h < y1 + h; r++) {
                    float off = (r & 1) == 0 ? 0 : w / 2f;
                    float sx = (float) Math.floor((x0 - off) / w) * w + off - w;
                    for (float x = sx; x < x1 + w; x += w) hex(c, x, r * h, rr);
                }
                break;
            }
            case 12:
            case 13: { // diagonal, and crosshatch
                float step = 16 * d;
                P.setColor(ink(darkPage ? 22 : 18));
                float span = (y1 - y0) + (x1 - x0);
                float base = (float) Math.floor((x0 + y0) / step) * step;
                for (float k = base; k < base + span + step; k += step) c.drawLine(k - y0, y0, k - y1, y1, P);
                if (s == 13) {
                    float base2 = (float) Math.floor((x0 - y1) / step) * step;
                    for (float k = base2; k < base2 + span + step; k += step) c.drawLine(k + y0, y0, k + y1, y1, P);
                }
                break;
            }
            case 14: { // music staves: groups of five thin lines
                float gap = 7 * d;
                float block = gap * 4 + 34 * d;
                P.setColor(ink(darkPage ? 40 : 34));
                float first = top + 10 * d;
                float start = first + (float) Math.floor(Math.max(0, y0 - first) / block) * block;
                for (float y = start; y < y1; y += block) {
                    for (int i = 0; i < 5; i++) c.drawLine(x0, y + i * gap, x1, y + i * gap, P);
                }
                break;
            }
            case 15: // engineering: green paper, minor and major grid
                fill(c, clip, darkPage ? 0x1446C28A : 0x2AB8E6C8);
                grid(c, clip, 10 * d, darkPage ? 0x2446C28A : 0x3348A070);
                P.setStrokeWidth(Math.max(1f, d * 1.1f));
                grid(c, clip, 50 * d, darkPage ? 0x5546C28A : 0x6648A070);
                break;
            case 16: { // a small plus at every grid point
                P.setColor(ink(darkPage ? 50 : 44));
                float arm = 3 * d;
                float sx = (float) Math.floor(x0 / g) * g;
                float sy = (float) Math.floor(y0 / g) * g;
                for (float y = sy; y < y1; y += g) {
                    for (float x = sx; x < x1; x += g) {
                        c.drawLine(x - arm, y, x + arm, y, P);
                        c.drawLine(x, y - arm, x, y + arm, P);
                    }
                }
                break;
            }
            case 19: // kraft: warm brown paper with faint fibres
                fill(c, clip, darkPage ? 0x1FC8A070 : 0x40D9B98A);
                dots(c, clip, 9 * d, 0.6f * d, darkPage ? 0x22FFE0B0 : 0x228A6A3A);
                break;
            case 20: // blueprint: blue paper and a light grid
                fill(c, clip, darkPage ? 0x262F6FD6 : 0x332F6FD6);
                grid(c, clip, 12 * d, darkPage ? 0x26AFCBFF : 0x332F6FD6);
                P.setStrokeWidth(Math.max(1f, d * 1.1f));
                grid(c, clip, 60 * d, darkPage ? 0x4DAFCBFF : 0x552F6FD6);
                break;
            default:
                break;
        }
        P.setStyle(Paint.Style.STROKE);
        P.setPathEffect(null);
    }

    private static void fill(Canvas c, Rect r, int color) {
        P.setStyle(Paint.Style.FILL);
        P.setColor(color);
        c.drawRect(r, P);
        P.setStyle(Paint.Style.STROKE);
    }

    private static void grid(Canvas c, Rect r, float step, int color) {
        P.setStyle(Paint.Style.STROKE);
        P.setColor(color);
        float sx = (float) Math.floor(r.left / step) * step;
        float sy = (float) Math.floor(r.top / step) * step;
        for (float x = sx; x < r.right; x += step) c.drawLine(x, r.top, x, r.bottom, P);
        for (float y = sy; y < r.bottom; y += step) c.drawLine(r.left, y, r.right, y, P);
    }

    private static void dots(Canvas c, Rect r, float step, float rad, int color) {
        P.setStyle(Paint.Style.FILL);
        P.setColor(color);
        float sx = (float) Math.floor(r.left / step) * step;
        float sy = (float) Math.floor(r.top / step) * step;
        for (float y = sy; y < r.bottom; y += step) {
            for (float x = sx; x < r.right; x += step) c.drawCircle(x, y, rad, P);
        }
        P.setStyle(Paint.Style.STROKE);
    }

    private static void hex(Canvas c, float cx, float cy, float r) {
        PATH.reset();
        for (int i = 0; i < 6; i++) {
            double a = Math.PI / 3 * i + Math.PI / 6;
            float x = cx + (float) Math.cos(a) * r;
            float y = cy + (float) Math.sin(a) * r;
            if (i == 0) PATH.moveTo(x, y);
            else PATH.lineTo(x, y);
        }
        PATH.close();
        c.drawPath(PATH, P);
    }
}
