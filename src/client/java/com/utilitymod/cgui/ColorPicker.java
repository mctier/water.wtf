package com.utilitymod.cgui;

import com.utilitymod.echo.features.settings.impl.ColorSetting;
import com.utilitymod.gui.UiSounds;
import dev.koifih.client.render.Draw;
import dev.koifih.client.render.Text;
import net.minecraft.client.gui.DrawContext;

import static com.utilitymod.cgui.CuiTheme.*;

/**
 * Color bound to a ColorSetting. The row is label + live swatch; opening the
 * swatch floats the editor above sibling rows through the host screen popup
 * layer. Drags write through setFromHSBNoNotify / setAlphaNoNotify; the
 * notifying public setters fire on release.
 */
public class ColorPicker extends Widget implements Popup {
    private static final float SV_W = 112f, SV_H = 56f;
    private static final float BAR_W = 112f, BAR_H = 8f;

    private final String label;
    private final ColorSetting color;
    private Window host;
    private boolean open;
    private boolean draggingSv, draggingHue, draggingAlpha;

    public ColorPicker(String label, ColorSetting color) {
        this.label = label;
        this.color = color;
        h = 16f;
    }

    public ColorPicker window(Window host) {
        this.host = host;
        return this;
    }

    @Override
    public Window window() {
        if (host != null) return host;
        for (Widget p = parent; p != null; p = p.parent) {
            if (p instanceof Window win) return win;
        }
        return null;
    }

    @Override
    public boolean openHit(double mx, double my) {
        if (!open) return false;
        float[] b = bounds();
        return mx >= b[0] && mx <= b[2] && my >= b[1] && my <= b[3];
    }

    @Override
    public boolean contains(double mx, double my) {
        return inside(mx, my);
    }

    @Override
    public void close() {
        open = false;
        draggingSv = draggingHue = draggingAlpha = false;
        if (screen != null) screen.removePopup(this);
    }

    private void toggleOpen() {
        if (open) {
            close();
            return;
        }
        open = true;
        if (screen != null) screen.addPopup(this);
        UiSounds.play(UiSounds.Sound.POPUP);
    }

    private float[] bounds() {
        float bx = x + Math.max(0, w - SV_W - 12);
        float by = y + h + 4;
        float bh = SV_H + 4 + BAR_H + 3 + BAR_H + 3 + 10 + 6;
        if (screen != null) {
            float vw = screen.vw(), vh = screen.vh();
            bx = Math.max(2, Math.min(bx, vw - SV_W - 14));
            by = Math.max(2, Math.min(by, vh - bh - 4));
        }
        return new float[]{bx, by, bx + SV_W + 12, by + bh};
    }

    @Override
    public void bodyRender(DrawContext ctx, double mx, double my) {
        if (!open) return;
        float[] b = bounds();
        float bx = b[0], by = b[1];
        float wTotal = SV_W + 12;
        Draw.rect(ctx, Math.round(bx), Math.round(by), Math.round(wTotal), Math.round(b[3] - by), (int) RADIUS_WINDOW, 0xF3111317);

        int hueColor = fullHue(color.getHue());
        float px = bx + 6, py = by + 6;
        Draw.saturationValue(ctx, Math.round(px), Math.round(py), (int) SV_W, (int) SV_H, 3, hueColor);
        float sx = px + color.getSaturation() * SV_W;
        float sy = py + (1f - color.getBrightness()) * SV_H;
        Draw.rect(ctx, (int) sx - 2, (int) sy - 2, 5, 5, 2, 0xFFFFFFFF);
        Draw.rect(ctx, (int) sx - 1, (int) sy - 1, 3, 3, 1, opaqueSwatch());

        float hy = py + SV_H + 4;
        Draw.hueBar(ctx, Math.round(px), Math.round(hy), (int) BAR_W, (int) BAR_H, 2);
        float hx = px + color.getHue() * BAR_W;
        Draw.rect(ctx, (int) hx - 1, Math.round(hy) - 1, 3, (int) BAR_H + 2, 1, 0xFFFFFFFF);

        float pyAlpha = hy + BAR_H + 3;
        if (color.isShowAlpha()) {
            // checkerboard backdrop so the alpha amount reads against any color
            Draw.rect(ctx, Math.round(px), Math.round(pyAlpha), (int) BAR_W, (int) BAR_H, 2, 0xFFE8E8E8);
            for (float cx = px; cx < px + BAR_W; cx += 4) {
                Draw.rect(ctx, Math.round(cx), Math.round(pyAlpha), 2, (int) BAR_H, 0, 0xFF9A9A9A);
                Draw.rect(ctx, Math.round(cx + 2), Math.round(pyAlpha), 2, (int) BAR_H / 2, 0, 0xFF9A9A9A);
                Draw.rect(ctx, Math.round(cx), Math.round(pyAlpha + BAR_H / 2f), 2, (int) BAR_H / 2, 0, 0xFF9A9A9A);
            }
            Draw.rect(ctx, Math.round(px), Math.round(pyAlpha), (int) BAR_W, (int) BAR_H, 0,
                    (color.getARGB() & 0xFFFFFF) | (color.getAlpha() << 24));
            float ax = px + (1f - clamp01(color.getAlpha() / 255f)) * BAR_W;
            Draw.rect(ctx, (int) ax - 1, Math.round(pyAlpha) - 1, 3, (int) BAR_H + 2, 1, 0xFFFFFFFF);
        }

        float pyPre = pyAlpha + BAR_H + 3;
        Draw.rect(ctx, Math.round(px), Math.round(pyPre), (int) BAR_W, 10, 2, opaqueSwatch());
        String aText = String.valueOf(color.getAlpha());
        Text.draw(ctx, aText, px + BAR_W - Text.width(aText, TEXT_SIZE_SMALL) - 3, pyPre + 6,
                TEXT_SIZE_SMALL, opaqueText(opaqueSwatch()));
    }

    private int opaqueSwatch() {
        return color.getARGB() | 0xFF000000;
    }

    private static int opaqueText(int argb) {
        int lum = 2126 * ((argb >> 16) & 0xFF) + 7152 * ((argb >> 8) & 0xFF) + 722 * (argb & 0xFF);
        return lum > 60000 ? 0xFF101317 : 0xFFE2E4EA;
    }

    static int fullHue(float hue) {
        float h = hue % 1.0f;
        if (h < 0) h += 1f;
        return java.awt.Color.HSBtoRGB(h, 1f, 1f) & 0xFFFFFF;
    }

    @Override
    public void render(DrawContext ctx, double mx, double my, float delta) {
        animHover(mx, my);
        Text.draw(ctx, label, x, y + 8, TEXT_SIZE, CuiTheme.mix(TEXT, ACCENT, hover * 0.3f));
        float sw = Text.width(label, TEXT_SIZE);
        float bx = x + Math.max(sw + 6, w - 28);
        int bg = open ? CuiTheme.mix(BG_CONTROL, ACCENT, 0.35f) : CuiTheme.mix(BG_CONTROL, ACCENT, hover * 0.25f);
        Draw.rect(ctx, Math.round(bx), Math.round(y + 2), 22, Math.round(h - 4), 3, opaqueSwatch());
        Draw.rect(ctx, Math.round(bx), Math.round(y + 2), 22, Math.round(h - 4), 3, bg & 0x1F000000);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return false;
        if (open) {
            float[] b = bounds();
            if (inside(mx, my)) {
                toggleOpen();
                return true;
            }
            if (mx < b[0] || mx > b[2] || my < b[1] || my > b[3]) return false;
            float bx = b[0] + 6, by = b[1] + 6;
            if (my >= by && my <= by + SV_H) {
                draggingSv = true;
                applySv(mx, my);
                return true;
            }
            float hy = by + SV_H + 4;
            if (my >= hy && my <= hy + BAR_H) {
                draggingHue = true;
                applyHue(mx);
                return true;
            }
            float ay = hy + BAR_H + 3;
            if (color.isShowAlpha() && my >= ay && my <= ay + BAR_H) {
                draggingAlpha = true;
                applyAlpha(my);
                return true;
            }
            return true;
        }
        if (inside(mx, my)) {
            toggleOpen();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button) {
        if (!open || button != 0) return false;
        if (draggingSv) {
            applySv(mx, my);
            return true;
        }
        if (draggingHue) {
            applyHue(mx);
            return true;
        }
        if (draggingAlpha) {
            applyAlpha(my);
            return true;
        }
        return false;
    }

    @Override
    public void mouseReleased(int button) {
        if (draggingSv) {
            draggingSv = false;
            color.setFromHSB(color.getHue(), color.getSaturation(), color.getBrightness());
        }
        if (draggingHue) {
            draggingHue = false;
            color.setFromHSB(color.getHue(), color.getSaturation(), color.getBrightness());
        }
        if (draggingAlpha) {
            draggingAlpha = false;
            color.setAlpha(color.getAlpha());
        }
    }

    private void applySv(double mx, double my) {
        float[] b = bounds();
        float bx = b[0] + 6, by = b[1] + 6;
        float sat = clamp01((float) (mx - bx) / Math.max(1, SV_W));
        float val = clamp01(1f - (float) (my - by) / Math.max(1, SV_H));
        color.setFromHSBNoNotify(color.getHue(), sat, val);
    }

    private void applyHue(double mx) {
        float[] b = bounds();
        float bx = b[0] + 6;
        float hue = clamp01((float) (mx - bx) / Math.max(1, BAR_W));
        // hue 1.0 equals hue 0 on the wheel - stay under one turn
        if (hue >= 0.999f) hue = 0.999f;
        color.setFromHSBNoNotify(hue, color.getSaturation(), color.getBrightness());
    }

    private void applyAlpha(double my) {
        float[] b = bounds();
        float by = b[1] + 6 + SV_H + 4 + BAR_H + 3;
        float alpha = clamp01(1f - (float) (my - by) / Math.max(1, BAR_H));
        color.setAlphaNoNotify(Math.round(alpha * 255f));
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
