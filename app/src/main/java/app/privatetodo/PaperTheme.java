package app.privatetodo;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * "Paper" for the notes section only: warm paper with grain, ink text, a red pencil accent, serif titles and
 * pinned notes as tilted sticky notes. It is a colour and type layer over the notebook as it is: same screens,
 * same layout. Light only: with the app in Dark or AMOLED the notes keep the dark look.
 */
final class PaperTheme {
    static final int STANDARD = 0, PAPER = 1;
    static final int PAPER_BG = 0xFFF5EFE3;
    static final int INK = 0xFF1C1915;
    static final int MUTED = 0xFF6A6054;
    static final int RED = 0xFFB83A17;
    static final int HIGHLIGHT = 0xFFFFD95E;
    private static final int[] STICKY = {0xFFFBE7A1, 0xFFF6CABB, 0xFFDCE8C4, 0xFFD3E3EE};

    /** True while the palette below is in force (set by apply). */
    static boolean on;

    private PaperTheme() {
    }

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    static int chosen(Context c) {
        return sp(c).getInt("notes_theme", STANDARD);
    }

    static void setChosen(Context c, int v) {
        sp(c).edit().putInt("notes_theme", v).apply();
    }

    /** Call right after Pal.init in a notes screen: lays the paper colours over the light palette. */
    static void apply(Context c) {
        on = chosen(c) == PAPER && !Pal.dark;
        if (!on) return;
        Pal.bg = PAPER_BG;
        Pal.card = 0xFFFBF7EE;
        Pal.raised = 0xFFFBF7EE;
        Pal.chip = 0xFFEBE3D3;
        Pal.ink = INK;
        Pal.muted = MUTED;
        Pal.accent = RED;
        Pal.onAccent = PAPER_BG;
        Pal.sel = INK;
        Pal.onSel = PAPER_BG;
        Pal.overdue = RED;
    }

    /** A pinned note's sticky-note colour when its pinned colour is the default one. */
    static int sticky(long id) {
        return STICKY[(int) (Math.abs(id) % STICKY.length)];
    }

    /** Paper only: a strip of translucent tape holding a pinned note, like the design. */
    static View tape(Context c) {
        final float d = c.getResources().getDisplayMetrics().density;
        View v = new View(c);
        v.setRotation(-3f);
        v.setElevation(12 * d); // above the card (its elevation is 4dp) so the tape sits on the note
        v.setBackground(new Drawable() {
            private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint dash = new Paint(Paint.ANTI_ALIAS_FLAG);

            @Override
            public void draw(Canvas cv) {
                android.graphics.Rect b = getBounds();
                fill.setColor(0x8CFFFFFF);
                cv.drawRect(b, fill);
                dash.setColor(0x1F000000);
                dash.setStyle(Paint.Style.STROKE);
                dash.setStrokeWidth(d);
                dash.setPathEffect(new android.graphics.DashPathEffect(new float[]{3 * d, 2 * d}, 0));
                cv.drawLine(b.left + d / 2, b.top, b.left + d / 2, b.bottom, dash);
                cv.drawLine(b.right - d / 2, b.top, b.right - d / 2, b.bottom, dash);
            }

            @Override
            public void setAlpha(int a) {
            }

            @Override
            public void setColorFilter(android.graphics.ColorFilter f) {
            }

            @Override
            public int getOpacity() {
                return PixelFormat.TRANSLUCENT;
            }
        });
        return v;
    }

    static android.widget.FrameLayout.LayoutParams tapeLp(Context c) {
        float d = c.getResources().getDisplayMetrics().density;
        android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams((int) (58 * d), (int) (18 * d));
        lp.gravity = android.view.Gravity.TOP | android.view.Gravity.CENTER_HORIZONTAL;
        lp.topMargin = (int) (-4 * d);
        return lp;
    }

    /** A small tilt for a pinned note, steady per note. */
    static float tilt(long id) {
        return (id % 2 == 0 ? -1f : 1f) * (0.9f + (Math.abs(id) % 3) * 0.4f);
    }

    /** The paper itself: the colour, a fine grain and a soft warm edge. */
    static Drawable background(Context c) {
        return new Drawable() {
            private final Paint fill = new Paint();
            private final Paint grain = new Paint();
            private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
            private Bitmap tile;

            @Override
            public void draw(Canvas canvas) {
                android.graphics.Rect b = getBounds();
                fill.setColor(PAPER_BG);
                canvas.drawRect(b, fill);
                if (tile == null) {
                    int n = 96;
                    tile = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888);
                    java.util.Random r = new java.util.Random(7);
                    int[] px = new int[n * n];
                    for (int i = 0; i < px.length; i++) {
                        int a = r.nextInt(22); // faint warm specks
                        px[i] = (a << 24) | 0x66512F;
                    }
                    tile.setPixels(px, 0, n, 0, 0, n, n);
                    grain.setShader(new BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
                }
                canvas.drawRect(b, grain);
                float r = Math.max(b.width(), b.height()) * 0.75f;
                edge.setShader(new RadialGradient(b.exactCenterX(), b.height() * 0.4f, r,
                        new int[]{0x00000000, 0x00000000, 0x29785A28}, new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP));
                canvas.drawRect(b, edge);
            }

            @Override
            public void setAlpha(int alpha) {
            }

            @Override
            public void setColorFilter(ColorFilter cf) {
            }

            @Override
            public int getOpacity() {
                return PixelFormat.OPAQUE;
            }
        };
    }

    /** Bold titles become serif (the note list's body text stays as it is). Run after Fonts.apply. */
    static void serif(View v) {
        if (!on || v == null) return;
        float d = v.getResources().getDisplayMetrics().scaledDensity;
        walk(v, d);
    }

    private static void walk(View v, float sd) {
        if (v instanceof TextView) {
            TextView t = (TextView) v;
            if ("keepfont".equals(v.getTag())) return;
            Typeface cur = t.getTypeface();
            if (cur != null && cur.isBold() && t.getTextSize() >= 15 * sd && !(t instanceof android.widget.EditText)) {
                t.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
            }
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) walk(g.getChildAt(i), sd);
        }
    }

    /** The big page title with a yellow highlighter stroke under it. */
    static CharSequence highlighted(String s) {
        if (!on) return s;
        SpannableString ss = new SpannableString(s);
        ss.setSpan(new android.text.style.LineBackgroundSpan() {
            private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

            @Override
            public void drawBackground(Canvas c, Paint paint, int left, int right, int top, int baseline, int bottom,
                                       CharSequence text, int start, int end, int lnum) {
                float w = paint.measureText(text, start, end);
                p.setColor(HIGHLIGHT);
                float h = (baseline - top) * 0.38f;
                c.save();
                c.skew(-0.1f, 0);
                c.drawRect(left - 4 + (baseline) * 0.1f, baseline - h, left + w + 6 + baseline * 0.1f, baseline + 2, p);
                c.restore();
            }
        }, 0, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return ss;
    }
}
