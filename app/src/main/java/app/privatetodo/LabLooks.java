package app.privatetodo;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

/** Design lab only: ten-plus-ten extra looks (styles 10 to 29) for {@link LabDial}. */
final class LabLooks {
    private static final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final RectF rf = new RectF();
    private static final Path path = new Path();

    private LabLooks() {
    }

    private static int centreColor(int s) {
        switch (s) {
            case 10: return 0xFFFFFFFF;
            case 11: return Pal.accent;
            case 12: return 0xFF7DF9FF;
            case 13: return 0xFF22301A;
            case 14: return 0xFFFFFFFF;
            case 15: return Pal.onAccent;
            case 16: return 0xFFFFFFFF;
            case 17: return 0xFFFFFFFF;
            case 18: return Pal.ink;
            case 19: return Pal.onAccent;
            case 20: return Pal.accent;
            case 21: return 0xFFFFFFFF;
            case 22: return 0xFFFF4DA6;
            case 23: return 0xFFFFFFFF;
            case 24: return 0xFF39FF14;
            case 25: return 0xFFFFFFFF;
            case 26: return 0xFFFFFFFF;
            case 27: return Pal.accent;
            case 28: return 0xFF9FFFE9;
            default: return 0xFFFFFFFF;
        }
    }

    private static int sideColor(int s) {
        switch (s) {
            case 10: return 0xFFB7C4E8;
            case 12: return 0xFF2C8C96;
            case 13: return 0xFF5F7050;
            case 14: return 0xFFBBBBBB;
            case 16: return 0xFF4A4A57;
            case 21: return 0xFF8A8A8A;
            case 22: return 0xFF2EE6FF;
            case 24: return 0xFF1C8A0A;
            case 25: return 0xFFA9C4EE;
            case 26: return 0xFFFFE3D1;
            case 28: return 0xFF4C8F86;
            case 17: return 0xFFE3DAFF;
            default: return Pal.muted;
        }
    }

    private static void txt(Canvas c, String s, float x, float y, float sizePx, int color, int alpha,
                            boolean bold, boolean mono) {
        Typeface tf = mono ? Typeface.create(Typeface.MONOSPACE, bold ? Typeface.BOLD : Typeface.NORMAL)
                : Fonts.face(bold);
        p.setTypeface(tf);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(sizePx);
        p.setColor(color);
        p.setAlpha(Math.max(0, Math.min(255, alpha)));
        c.drawText(s, x, y - (p.ascent() + p.descent()) / 2f, p);
        p.setAlpha(255);
    }

    private static int mix(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int r = Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    // ------------------------------------------------------------------ entry

    static void draw(LabDial v, Canvas c, int s) {
        float w = v.vw();
        float h = v.vh();
        float cy = h / 2f;
        float d = v.dens();
        float rowH = v.rowH();
        panel(c, s, w, h, d);
        for (int i = 0; i < v.ncols(); i++) col(v, c, s, i, cy, d, rowH, w, h);
        if (v.ncols() < 3) {
            if (s == 12 || s == 24) scanlines(c, w, h, d, s == 12 ? 0x147DF9FF : 0x1439FF14);
            return;
        }
        // colon between hour and minute
        float colonX = (v.cx1(0) + v.cx0(1)) / 2f;
        boolean mono = s == 13 || s == 24 || s == 25;
        float size = 26 * d;
        int cc = centreColor(s);
        if (s == 22) {
            p.setShadowLayer(10 * d, 0, 0, cc);
        } else if (s == 10 || s == 28 || s == 24) {
            p.setShadowLayer(8 * d, 0, 0, cc);
        }
        txt(c, ":", colonX, cy - 2 * d, size, cc, 255, true, mono);
        p.clearShadowLayer();
        if (s == 12 || s == 24) scanlines(c, w, h, d, s == 12 ? 0x147DF9FF : 0x1439FF14);
    }

    private static void scanlines(Canvas c, float w, float h, float d, int color) {
        p.setColor(color);
        p.setStyle(Paint.Style.FILL);
        for (float y = 0; y < h; y += 3 * d) c.drawRect(0, y, w, y + d * 0.8f, p);
    }

    // ------------------------------------------------------------------ panels

    private static void clipPanel(Canvas c, float w, float h, float d) {
        path.reset();
        rf.set(0, 0, w, h);
        path.addRoundRect(rf, 22 * d, 22 * d, Path.Direction.CW);
        c.clipPath(path);
    }

    private static void panel(Canvas c, int s, float w, float h, float d) {
        p.setStyle(Paint.Style.FILL);
        int base;
        switch (s) {
            case 10: base = 0xFF0A0F1C; break;
            case 12: base = 0xFF04141A; break;
            case 13: base = 0xFFB7C79A; break;
            case 14: base = 0xFF181B1A; break;
            case 16: base = 0xFF0B0B0F; break;
            case 19: base = Pal.bg; break;
            case 21: base = 0xFF111111; break;
            case 22: base = 0xFF160A24; break;
            case 24: base = 0xFF020A04; break;
            case 25: base = 0xFF0F3A78; break;
            case 28: base = 0xFF061A18; break;
            default: base = Pal.chip; break;
        }
        c.save();
        clipPanel(c, w, h, d);
        if (s == 17) {
            p.setShader(new LinearGradient(0, 0, w, h, 0xFF0B6B5D, 0xFF7C4DFF, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, p);
            p.setShader(null);
        } else if (s == 26) {
            p.setShader(new LinearGradient(0, 0, 0, h, new int[]{0xFFFFA64D, 0xFFFF4D8D, 0xFF5B2A9D}, null,
                    Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, p);
            p.setShader(null);
            p.setColor(0xCCFFE08A);
            c.drawCircle(w * 0.5f, h * 0.98f, h * 0.34f, p);
        } else {
            p.setColor(base);
            c.drawRect(0, 0, w, h, p);
        }
        switch (s) {
            case 10: {
                int[] cols = {0x6600E5C3, 0x667C4DFF, 0x44FF4DA6};
                float[][] at = {{0.2f, 0.25f, 0.65f}, {0.85f, 0.75f, 0.6f}, {0.55f, 0.05f, 0.5f}};
                for (int i = 0; i < 3; i++) {
                    p.setShader(new RadialGradient(w * at[i][0], h * at[i][1], w * at[i][2], cols[i],
                            cols[i] & 0x00FFFFFF, Shader.TileMode.CLAMP));
                    c.drawRect(0, 0, w, h, p);
                }
                p.setShader(null);
                break;
            }
            case 13: // ghost segments are drawn per column; here only an inner bezel
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(3 * d);
                p.setColor(0x33000000);
                rf.set(6 * d, 6 * d, w - 6 * d, h - 6 * d);
                c.drawRoundRect(rf, 16 * d, 16 * d, p);
                p.setStyle(Paint.Style.FILL);
                break;
            case 16: { // a cone of light from the top onto the chosen row
                float cy = h / 2f;
                path.reset();
                path.moveTo(w * 0.42f, 0);
                path.lineTo(w * 0.58f, 0);
                path.lineTo(w * 0.95f, cy + 34 * d);
                path.lineTo(w * 0.05f, cy + 34 * d);
                path.close();
                p.setShader(new LinearGradient(0, 0, 0, cy + 34 * d, 0x00FFFFFF, 0x44FFFFFF, Shader.TileMode.CLAMP));
                c.drawPath(path, p);
                p.setShader(new RadialGradient(w / 2f, cy, w * 0.5f, 0x55FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
                c.drawRect(0, 0, w, h, p);
                p.setShader(null);
                break;
            }
            case 25: {
                p.setColor(0x24FFFFFF);
                p.setStrokeWidth(d);
                for (float x = 0; x < w; x += 14 * d) c.drawLine(x, 0, x, h, p);
                for (float y = 0; y < h; y += 14 * d) c.drawLine(0, y, w, y, p);
                break;
            }
            default:
                break;
        }
        c.restore();
    }

    // ------------------------------------------------------------------ columns

    private static void col(LabDial v, Canvas c, int s, int i, float cy, float d, float rowH, float w, float h) {
        float x0 = v.cx0(i);
        float x1 = v.cx1(i);
        float cx = (x0 + x1) / 2f;
        float cw = x1 - x0;
        float pos = v.cpos(i);
        int n = v.cn(i);
        boolean wrap = v.cwrap(i);
        int base = Math.round(pos);
        int cc = centreColor(s);
        int sc = sideColor(s);
        boolean mono = s == 13 || s == 24 || s == 25;

        // ---- column level things drawn behind the rows
        switch (s) {
            case 13: { // ghost "88" under the chosen row
                String ghost = i == 2 ? "88" : "88";
                txt(c, ghost, cx, cy, 30 * d, 0xFF22301A, 0x1C, true, true);
                break;
            }
            case 14: { // mechanical tile
                p.setColor(0xFF242928);
                rf.set(x0 + 2 * d, 4 * d, x1 - 2 * d, v.vh() - 4 * d);
                c.drawRoundRect(rf, 10 * d, 10 * d, p);
                p.setColor(0xFF313836);
                rf.set(x0 + 2 * d, cy - rowH / 2f, x1 - 2 * d, cy + rowH / 2f);
                c.drawRoundRect(rf, 6 * d, 6 * d, p);
                break;
            }
            case 18: { // a ring that fills with the value
                float frac = (((pos % n) + n) % n) / n;
                float rad = rowH * 0.8f;
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeCap(Paint.Cap.ROUND);
                p.setStrokeWidth(4 * d);
                p.setColor((Pal.accent & 0x00FFFFFF) | 0x30000000);
                c.drawCircle(cx, cy, rad, p);
                p.setColor(Pal.accent);
                rf.set(cx - rad, cy - rad, cx + rad, cy + rad);
                c.drawArc(rf, -90, 360 * frac, false, p);
                p.setStyle(Paint.Style.FILL);
                break;
            }
            case 20: { // wavy guide line
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(2 * d);
                p.setColor((Pal.accent & 0x00FFFFFF) | 0x55000000);
                path.reset();
                for (float y = 0; y <= v.vh(); y += 4 * d) {
                    float xx = cx + (float) Math.sin((y - cy) / rowH * 1.1f - pos * 0.0f) * 9 * d;
                    if (y == 0) path.moveTo(xx, y);
                    else path.lineTo(xx, y);
                }
                c.drawPath(path, p);
                p.setStyle(Paint.Style.FILL);
                break;
            }
            case 21: { // sprocket holes that roll with the dial
                p.setColor(0x66FFFFFF);
                float step = 14 * d;
                float off = ((pos * rowH) % step + step) % step;
                for (float y = -step + off; y < v.vh() + step; y += step) {
                    rf.set(x0 + 3 * d, y, x0 + 9 * d, y + 7 * d);
                    c.drawRoundRect(rf, 1.5f * d, 1.5f * d, p);
                    rf.set(x1 - 9 * d, y, x1 - 3 * d, y + 7 * d);
                    c.drawRoundRect(rf, 1.5f * d, 1.5f * d, p);
                }
                break;
            }
            case 22: { // neon frame around the chosen row
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(2 * d);
                p.setColor(0xFFFF4DA6);
                p.setShadowLayer(10 * d, 0, 0, 0xFFFF4DA6);
                rf.set(x0 + 4 * d, cy - rowH / 2f + 2 * d, x1 - 4 * d, cy + rowH / 2f - 2 * d);
                c.drawRoundRect(rf, 12 * d, 12 * d, p);
                p.clearShadowLayer();
                p.setStyle(Paint.Style.FILL);
                break;
            }
            case 25: { // dimension brackets
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(1.6f * d);
                p.setColor(0xFFFFFFFF);
                float a = x0 + 6 * d;
                float b = x1 - 6 * d;
                float t = cy - rowH / 2f + 2 * d;
                float bt = cy + rowH / 2f - 2 * d;
                float L = 9 * d;
                c.drawLine(a, t, a + L, t, p);
                c.drawLine(a, t, a, t + L, p);
                c.drawLine(b, t, b - L, t, p);
                c.drawLine(b, t, b, t + L, p);
                c.drawLine(a, bt, a + L, bt, p);
                c.drawLine(a, bt, a, bt - L, p);
                c.drawLine(b, bt, b - L, bt, p);
                c.drawLine(b, bt, b, bt - L, p);
                p.setStyle(Paint.Style.FILL);
                break;
            }
            case 28: { // sonar rings
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(1.6f * d);
                float ph = (((pos * 0.35f) % 1f) + 1f) % 1f;
                for (int j = 0; j < 4; j++) {
                    float f = (j * 0.25f + ph) % 1f;
                    float rad = f * rowH * 2.1f;
                    p.setColor(0xFF3DDCC2);
                    p.setAlpha((int) (150 * (1f - f)));
                    c.drawCircle(cx, cy, rad, p);
                }
                p.setAlpha(255);
                p.setStyle(Paint.Style.FILL);
                break;
            }
            case 29: { // the liquid tube
                float frac = (((pos % n) + n) % n) / Math.max(1f, n - 1f);
                float top = cy - rowH / 2f + 2 * d;
                float bot = cy + rowH / 2f - 2 * d;
                float level = bot - (bot - top) * Math.min(1f, frac);
                path.reset();
                RectF tube = new RectF(x0 + 4 * d, top, x1 - 4 * d, bot);
                c.save();
                Path clip = new Path();
                clip.addRoundRect(tube, 13 * d, 13 * d, Path.Direction.CW);
                c.clipPath(clip);
                p.setColor((Pal.accent & 0x00FFFFFF) | 0x26000000);
                c.drawRect(tube, p);
                p.setColor(Pal.accent);
                path.moveTo(tube.left, bot);
                for (float x = tube.left; x <= tube.right; x += 3 * d) {
                    path.lineTo(x, level + (float) Math.sin(x / (9 * d) + pos * 3f) * 2.6f * d);
                }
                path.lineTo(tube.right, bot);
                path.close();
                c.drawPath(path, p);
                c.restore();
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(1.6f * d);
                p.setColor(Pal.accent);
                c.drawRoundRect(tube, 13 * d, 13 * d, p);
                p.setStyle(Paint.Style.FILL);
                break;
            }
            default:
                break;
        }

        if (s == 27) { // fan: handled as one block, farthest card first
            fan(v, c, i, cy, d, rowH, cx, cw, pos, n, wrap, base);
            return;
        }

        for (int pass = 0; pass < 1; pass++) {
            for (int r = -3; r <= 3; r++) {
                int idx = base + r;
                if (!wrap && (idx < 0 || idx >= n)) continue;
                int real = ((idx % n) + n) % n;
                float dist = idx - pos;
                float ad = Math.abs(dist);
                if (ad > 2.7f) continue;
                String lab = v.clabel(i, real);
                float near = Math.max(0f, 1f - ad);
                boolean centre = ad < 0.5f;
                float y = cy + dist * rowH;
                switch (s) {
                    case 10: {
                        if (centre) p.setShadowLayer(16 * d, 0, 0, 0xFF9FFFE9);
                        txt(c, lab, cx, y, (17 + 14 * (float) Math.pow(near, 1.3)) * d, centre ? cc : sc,
                                (int) (90 + 165 * near), near > 0.5f, false);
                        p.clearShadowLayer();
                        break;
                    }
                    case 11:
                        txt(c, lab, cx, y, (centre ? 26 : 16 + 4 * near) * d, centre ? cc : sc,
                                (int) Math.max(50, 230 - ad * 80), centre, false);
                        break;
                    case 12: {
                        float jit = (float) Math.sin(pos * 18f) * 0.8f * d;
                        float sz = (17 + 11 * near) * d;
                        if (centre) {
                            txt(c, lab, cx - 1.6f * d + jit, y, sz, 0xFFFF3B6B, 150, true, false);
                            txt(c, lab, cx + 1.6f * d + jit, y, sz, 0xFF00E5FF, 150, true, false);
                        }
                        txt(c, lab, cx + jit, y, sz, centre ? cc : sc, (int) (80 + 175 * near), near > 0.5f, false);
                        break;
                    }
                    case 13:
                        txt(c, lab, cx, y, (centre ? 30 : 20) * d, centre ? cc : sc, centre ? 255 : (int) Math.max(40, 160 - ad * 60),
                                true, true);
                        break;
                    case 14:
                        odometerRow(c, v, i, lab, cx, cw, y, dist, rowH, d, pos, centre);
                        break;
                    case 15: {
                        float rad = rowH * (0.2f + 0.34f * (float) Math.pow(near, 0.8));
                        if (centre) {
                            p.setColor(Pal.accent);
                            c.drawCircle(cx, y, rad, p);
                            p.setColor(0x55FFFFFF);
                            rf.set(cx - rad * 0.6f, y - rad * 0.75f, cx - rad * 0.05f, y - rad * 0.35f);
                            c.drawOval(rf, p);
                        } else {
                            p.setStyle(Paint.Style.STROKE);
                            p.setStrokeWidth(1.6f * d);
                            p.setColor(Pal.accent);
                            p.setAlpha((int) Math.max(50, 200 - ad * 80));
                            c.drawCircle(cx, y, rad, p);
                            p.setStyle(Paint.Style.FILL);
                            p.setAlpha(255);
                        }
                        txt(c, lab, cx, y, (12 + 13 * near) * d, centre ? cc : sc, (int) (110 + 145 * near), centre, false);
                        break;
                    }
                    case 16:
                        if (centre) p.setShadowLayer(12 * d, 0, 0, 0xFFFFFFFF);
                        txt(c, lab, cx, y, (17 + 11 * near) * d, mix(sc, cc, (float) Math.pow(near, 1.5)),
                                255, near > 0.5f, false);
                        p.clearShadowLayer();
                        break;
                    case 17:
                        if (centre) {
                            p.setColor(0x38FFFFFF);
                            rf.set(x0 + 3 * d, y - rowH / 2f + 3 * d, x1 - 3 * d, y + rowH / 2f - 3 * d);
                            c.drawRoundRect(rf, 14 * d, 14 * d, p);
                            p.setStyle(Paint.Style.STROKE);
                            p.setStrokeWidth(d);
                            p.setColor(0x99FFFFFF);
                            c.drawRoundRect(rf, 14 * d, 14 * d, p);
                            p.setStyle(Paint.Style.FILL);
                        }
                        txt(c, lab, cx, y, (centre ? 25 : 18) * d, centre ? cc : sc, centre ? 255 : (int) Math.max(50, 190 - ad * 70),
                                centre, false);
                        break;
                    case 18:
                        txt(c, lab, cx, y, (centre ? 25 : 16) * d, centre ? cc : sc,
                                centre ? 255 : (int) Math.max(40, 170 - ad * 60), centre, false);
                        break;
                    case 19: {
                        float sc2 = 0.88f + 0.12f * near;
                        float kw = (cw - 14 * d) * sc2 / 2f;
                        float kh = (rowH - 8 * d) * sc2 / 2f;
                        float press = centre ? 2.5f * d : 0f;
                        p.setColor(centre ? 0xFF084A41 : 0xFFB5BEBA);
                        rf.set(cx - kw, y - kh + 3.5f * d, cx + kw, y + kh + 3.5f * d);
                        c.drawRoundRect(rf, 9 * d, 9 * d, p);
                        p.setColor(centre ? Pal.accent : Pal.card);
                        rf.set(cx - kw, y - kh + press, cx + kw, y + kh + press);
                        c.drawRoundRect(rf, 9 * d, 9 * d, p);
                        txt(c, lab, cx, y + press, (centre ? 22 : 17) * d, centre ? cc : Pal.muted,
                                (int) Math.max(70, 255 - ad * 70), true, false);
                        break;
                    }
                    case 20: {
                        float xo = (float) Math.sin(dist * 1.1f) * 9 * d;
                        c.save();
                        c.translate(cx + xo, y);
                        c.rotate((float) Math.sin(dist) * 5f);
                        txt(c, lab, 0, 0, (centre ? 28 : 18) * d, centre ? cc : sc, (int) Math.max(50, 240 - ad * 70), centre, false);
                        c.restore();
                        break;
                    }
                    case 21:
                        p.setColor(centre ? 0x22FFFFFF : 0x10FFFFFF);
                        rf.set(x0 + 14 * d, y - rowH / 2f + 3 * d, x1 - 14 * d, y + rowH / 2f - 3 * d);
                        c.drawRoundRect(rf, 3 * d, 3 * d, p);
                        if (centre) {
                            p.setStyle(Paint.Style.STROKE);
                            p.setStrokeWidth(1.4f * d);
                            p.setColor(0xCCFFFFFF);
                            c.drawRoundRect(rf, 3 * d, 3 * d, p);
                            p.setStyle(Paint.Style.FILL);
                        }
                        txt(c, lab, cx, y, (centre ? 22 : 17) * d, centre ? cc : sc, (int) Math.max(60, 255 - ad * 80),
                                centre, false);
                        break;
                    case 22: {
                        p.setStyle(Paint.Style.STROKE);
                        p.setStrokeWidth((centre ? 1.7f : 1.2f) * d);
                        p.setTypeface(Fonts.face(true));
                        p.setTextAlign(Paint.Align.CENTER);
                        p.setTextSize((centre ? 30 : 19) * d);
                        p.setColor(centre ? cc : sc);
                        p.setAlpha(centre ? 255 : (int) Math.max(50, 190 - ad * 70));
                        if (centre) p.setShadowLayer(12 * d, 0, 0, cc);
                        c.drawText(lab, cx, y - (p.ascent() + p.descent()) / 2f, p);
                        p.clearShadowLayer();
                        p.setStyle(Paint.Style.FILL);
                        p.setAlpha(255);
                        break;
                    }
                    case 23: {
                        float hue = (real * 37 + i * 90) % 360;
                        int col = Color.HSVToColor(new float[]{hue, 0.55f, 0.98f});
                        float sc2 = 0.78f + 0.27f * near;
                        float pw = (cw - 10 * d) * sc2 / 2f;
                        float ph2 = (rowH - 6 * d) * sc2 / 2f;
                        p.setColor(col);
                        p.setAlpha((int) (centre ? 255 : 140));
                        rf.set(cx - pw, y - ph2, cx + pw, y + ph2);
                        c.drawRoundRect(rf, ph2, ph2, p);
                        if (centre) {
                            p.setColor(0x55FFFFFF);
                            rf.set(cx - pw + 8 * d, y - ph2 + 3 * d, cx - pw * 0.1f, y - ph2 * 0.35f);
                            c.drawRoundRect(rf, 4 * d, 4 * d, p);
                        }
                        p.setAlpha(255);
                        txt(c, lab, cx, y, (centre ? 23 : 17) * d, 0xFFFFFFFF, (int) (centre ? 255 : 210), true, false);
                        break;
                    }
                    case 24: {
                        String line = (centre ? "> " : "  ") + lab;
                        if (centre) p.setShadowLayer(8 * d, 0, 0, cc);
                        txt(c, line, cx, y, (centre ? 24 : 18) * d, centre ? cc : sc, centre ? 255 : (int) Math.max(40, 160 - ad * 60),
                                true, true);
                        p.clearShadowLayer();
                        if (centre) {
                            p.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
                            p.setTextSize(24 * d);
                            float tw = p.measureText(line);
                            p.setColor(cc);
                            p.setAlpha(190);
                            rf.set(cx + tw / 2f + 2 * d, y - 11 * d, cx + tw / 2f + 12 * d, y + 11 * d);
                            c.drawRect(rf, p);
                            p.setAlpha(255);
                        }
                        break;
                    }
                    case 25:
                        txt(c, lab, cx, y, (centre ? 23 : 17) * d, centre ? cc : sc, (int) Math.max(50, 255 - ad * 80),
                                centre, true);
                        break;
                    case 26:
                        if (centre) p.setShadowLayer(8 * d, 0, 3 * d, 0x66000000);
                        txt(c, lab, cx, y, (centre ? 31 : 18) * d, centre ? cc : sc, centre ? 255 : (int) Math.max(60, 220 - ad * 70),
                                centre, false);
                        p.clearShadowLayer();
                        if (centre) {
                            p.setColor(0xCCFFFFFF);
                            rf.set(cx - 16 * d, y + 20 * d, cx + 16 * d, y + 23 * d);
                            c.drawRoundRect(rf, 2 * d, 2 * d, p);
                        }
                        break;
                    case 28:
                        if (centre) p.setShadowLayer(10 * d, 0, 0, cc);
                        txt(c, lab, cx, y, (centre ? 27 : 17) * d, centre ? cc : sc, centre ? 255 : (int) Math.max(40, 190 - ad * 70),
                                centre, false);
                        p.clearShadowLayer();
                        break;
                    default: { // 29 liquid
                        float frac = (((pos % n) + n) % n) / Math.max(1f, n - 1f);
                        int tc = centre ? (frac > 0.55f ? 0xFFFFFFFF : Pal.ink) : sc;
                        txt(c, lab, cx, y, (centre ? 24 : 17) * d, tc, centre ? 255 : (int) Math.max(40, 180 - ad * 70),
                                centre, false);
                        break;
                    }
                }
            }
        }

        // ---- things drawn over the rows
        if (s == 11) { // rings and a satellite
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.6f * d);
            float[] rr = {0.72f, 0.98f, 1.24f};
            int[] aa = {0xB0, 0x58, 0x28};
            for (int j = 0; j < 3; j++) {
                p.setColor(Pal.accent);
                p.setAlpha(aa[j]);
                c.drawCircle(cx, cy, Math.min(cw / 2f - 2 * d, rowH * rr[j]), p);
            }
            p.setStyle(Paint.Style.FILL);
            p.setAlpha(255);
            double ang = pos * 0.9;
            float orr = Math.min(cw / 2f - 2 * d, rowH * 0.98f);
            p.setColor(Pal.accent);
            c.drawCircle(cx + (float) Math.cos(ang) * orr, cy + (float) Math.sin(ang) * orr, 3.4f * d, p);
        }
    }

    // ------------------------------------------------------------------ odometer

    private static void odometerRow(Canvas c, LabDial v, int colIdx, String lab, float cx, float cw, float y,
                                    float dist, float rowH, float d, float pos, boolean centre) {
        if (lab.length() > 2 || !Character.isDigit(lab.charAt(0))) { // AM / PM
            txt(c, lab, cx, y, (centre ? 22 : 16) * d, centre ? 0xFFFFFFFF : 0xFF9A9A9A, centre ? 255 : 150, true, true);
            return;
        }
        String l2 = lab.length() == 1 ? " " + lab : lab;
        float f = pos - Math.round(pos);
        float tp = Math.round(pos) + 0.5f * Math.signum(f) * (float) Math.pow(Math.min(1f, 2f * Math.abs(f)), 3);
        // tens slide slower, then catch up
        float cyy = v.vh() / 2f;
        float yTens = cyy + (pos + dist - tp) * rowH;
        float dx = cw * 0.2f;
        txt(c, String.valueOf(l2.charAt(1)), cx + dx, y, (centre ? 26 : 19) * d, centre ? 0xFFFFFFFF : 0xFFAAAAAA,
                (int) Math.max(60, 255 - Math.abs(dist) * 80), true, true);
        txt(c, String.valueOf(l2.charAt(0)), cx - dx, yTens, (centre ? 26 : 19) * d, centre ? 0xFFFFFFFF : 0xFFAAAAAA,
                (int) Math.max(60, 255 - Math.abs(yTens - cyy) / rowH * 80), true, true);
    }

    // ------------------------------------------------------------------ card fan

    private static void fan(LabDial v, Canvas c, int i, float cy, float d, float rowH, float cx, float cw,
                            float pos, int n, boolean wrap, int base) {
        float R = rowH * 2.6f;
        float pivotY = cy + R;
        for (int k = 3; k >= 0; k--) { // farthest first, the chosen card last
            for (int sgn = -1; sgn <= 1; sgn += 2) {
                if (k == 0 && sgn == 1) continue;
                int idx = base + (k == 0 ? 0 : sgn * k);
                if (!wrap && (idx < 0 || idx >= n)) continue;
                int real = ((idx % n) + n) % n;
                float dist = idx - pos;
                float ad = Math.abs(dist);
                if (ad > 2.8f) continue;
                float a = dist * 0.4f;
                c.save();
                c.translate(cx, pivotY);
                c.rotate((float) Math.toDegrees(a));
                c.translate(0, -R);
                float cwid = (cw - 14 * d) / 2f;
                float chei = (rowH - 6 * d) / 2f;
                p.setColor(0x22000000);
                rf.set(-cwid + 1 * d, -chei + 3 * d, cwid + 1 * d, chei + 3 * d);
                c.drawRoundRect(rf, 10 * d, 10 * d, p);
                p.setColor(Pal.card);
                rf.set(-cwid, -chei, cwid, chei);
                c.drawRoundRect(rf, 10 * d, 10 * d, p);
                boolean centre = ad < 0.5f;
                if (centre) {
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(2 * d);
                    p.setColor(Pal.accent);
                    c.drawRoundRect(rf, 10 * d, 10 * d, p);
                    p.setStyle(Paint.Style.FILL);
                }
                txt(c, v.clabel(i, real), 0, 0, (centre ? 23 : 17) * d, centre ? Pal.accent : Pal.muted,
                        (int) Math.max(70, 255 - ad * 70), centre, false);
                c.restore();
            }
        }
    }
}
