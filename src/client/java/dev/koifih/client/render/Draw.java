package dev.koifih.client.render;

import com.utilitymod.gui.Rounded;
import dev.koifih.client.render.font.Font;
import dev.koifih.client.render.font.Fonts;
import dev.koifih.client.util.Colors;
import net.minecraft.client.gui.DrawContext;

/**
 * DrawContext-backed reimplementation of adin's Draw facade (same method
 * shapes, 1.21.11 Yarn types). The original routes every call through custom
 * MSDF pipelines that cannot bind in an injected HUD; here rounded rects use
 * {@link Rounded} (CPU SDF), gradients use {@code fillGradient}, and icons,
 * logo and text draw from adin's MSDF atlases via {@link Font}.
 */
public final class Draw {
    public static final int TOP_LEFT = 1;
    public static final int TOP_RIGHT = 2;
    public static final int BOTTOM_RIGHT = 4;
    public static final int BOTTOM_LEFT = 8;
    public static final int ALL_CORNERS = 15;
    public static final int TILED = 32;

    private static final int LOGO_STAR = 1;
    private static final int LOGO_BAND_BACK = 2;
    private static final int LOGO_BAND_FRONT = 3;
    private static final int LOGO_BACK_COLOR = 0xFFA3A3A3;
    private static final int LOGO_FRONT_COLOR = 0xFFEAEAEA;
    private static final int ICON_LAYERS = 3;

    public static void rect(DrawContext graphics, int x, int y, int width, int height, int radius, int color) {
        rect(graphics, x, y, width, height, radius, ALL_CORNERS, color, color);
    }

    public static void rect(DrawContext graphics, int x, int y, int width, int height, int radius,
                            int corners, int color) {
        rect(graphics, x, y, width, height, radius, corners, color, color);
    }

    public static void rect(DrawContext graphics, float x, float y, float width, int height,
                            int radius, int color) {
        if (width <= 0 || height <= 0) return;
        rect(graphics, Math.round(x), Math.round(y), Math.max(1, Math.round(width)), height, radius, color);
    }

    public static void stairs(DrawContext graphics, int x, int y, int width, int height, int steps, int color) {
        color = Opacity.apply(color);
        if (width <= 0 || height <= 0 || Colors.transparent(color)) return;
        int stepW = Math.max(1, width / Math.max(1, steps));
        for (int i = 0; i < steps; i++) {
            int sx = x + i * stepW;
            int sw = Math.min(stepW, x + width - sx);
            int sh = height - (height * i / Math.max(1, steps));
            graphics.fill(sx, y + height - sh, sx + sw, y + height, color);
        }
    }

    public static void gradient(DrawContext graphics, int x, int y, int width, int height, int radius,
                                int corners, int top, int bottom) {
        top = Opacity.apply(top);
        bottom = Opacity.apply(bottom);
        if (width <= 0 || height <= 0 || (Colors.transparent(top) && Colors.transparent(bottom))) return;
        // The rounded bottom is the clip-safe fallback. The gradient is then
        // painted once through the straight middle; no translucent top layer
        // is composited beneath it.
        rect(graphics, x, y, width, height, radius, corners, bottom);
        if (top != bottom) {
            graphics.fillGradient(x + radius, y, x + width - radius, y + height, top, bottom);
        }
    }

    public static void bordered(DrawContext graphics, float x, float y, float width, float height,
                                float radius, int fill, int border) {
        int r = Math.max(0, Math.round(radius));
        int h = Math.max(1, Math.round(height));
        int w = Math.max(1, Math.round(width));
        int xi = Math.round(x);
        int yi = Math.round(y);
        Rounded.fill(graphics, xi - 1, yi - 1, w + 2, h + 2, r + 1, Opacity.apply(border));
        Rounded.fill(graphics, xi, yi, w, h, r, Opacity.apply(fill));
    }

    public static void hueBar(DrawContext graphics, int x, int y, int width, int height, int radius) {
        if (width <= 0 || height <= 0) return;
        int strips = Math.min(24, Math.max(6, width / 4));
        for (int i = 0; i < strips; i++) {
            float t0 = i / (float) strips;
            int c0 = hsv(t0 * 360f, 1f, 1f);
            int sx = x + width * i / strips;
            int ex = x + width * (i + 1) / strips;
            graphics.fill(sx, y, ex, y + height, Opacity.apply(c0));
        }
        Rounded.outline(graphics, x, y, width, height, radius, 1f, Opacity.apply(0xFF2B2B2B));
    }

    public static void saturationValue(DrawContext graphics, int x, int y, int width, int height,
                                       int radius, int hueColor) {
        if (width <= 0 || height <= 0) return;
        int hue = hueColor & 0xFFFFFF;
        int base = 0xFF000000 | hue;
        Rounded.fill(graphics, x, y, width, height, radius, Opacity.apply(base));
        graphics.fillGradient(x, y, x + width, y + height,
                Opacity.apply(0xFFFFFFFF), Opacity.apply(0x00FFFFFF));
        graphics.fillGradient(x, y, x + width, y + height,
                Opacity.apply(0x00000000), Opacity.apply(0xFF000000));
    }

    public static void icon(DrawContext graphics, int codepoint, float x, float y, float size, int color) {
        color = Opacity.apply(color);
        if (size <= 0 || !Float.isFinite(size) || Colors.transparent(color)) return;
        Font font = Fonts.MATERIAL_ICONS;
        Font.Glyph glyph = font.glyph(codepoint);
        if (glyph == null || glyph.planeBounds() == null) return;
        Font.Bounds plane = glyph.planeBounds();
        float cx = x + size * 0.5f;
        float cy = y + size * 0.5f;
        float hw = (plane.right() - plane.left()) * size * 0.5f;
        float hh = (plane.bottom() - plane.top()) * size * 0.5f;
        font.quad(graphics, glyph, cx - hw, cy - hh, cx + hw, cy + hh, color);
    }

    public static void fadedTexture(DrawContext graphics, net.minecraft.util.Identifier texture,
                                    float x, float y, float width, float height, float u0, float fade) {
        // Fade factor needs a shader; plain blit keeps layout identical.
        int w = Math.max(1, Math.round(width));
        int h = Math.max(1, Math.round(height));
        graphics.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, texture,
                Math.round(x), Math.round(y), 0, 0, w, h, w, h);
    }

    public static void logo(DrawContext graphics, float x, float y, float size, int starColor) {
        logo(graphics, x, y, size, LOGO_BACK_COLOR, starColor, LOGO_FRONT_COLOR);
    }

    public static void logo(DrawContext graphics, float x, float y, float size, int back, int star, int front) {
        layers(graphics, Fonts.LOGO, LOGO_BAND_BACK, LOGO_STAR, LOGO_BAND_FRONT, back, star, front, x, y, size);
    }

    public static void icon(DrawContext graphics, AdinIcon icon, float x, float y, float size,
                            int back, int hero, int front, int backdrop) {
        icon(graphics, icon, x, y, size, back, hero, front);
    }

    public static void icon(DrawContext graphics, AdinIcon icon, float x, float y, float size,
                            int back, int hero, int front) {
        int first = icon.ordinal() * ICON_LAYERS + 1;
        layers(graphics, Fonts.ADIN_ICONS, first, first + 1, first + 2, back, hero, front, x, y, size);
    }

    public static void icon(DrawContext graphics, AdinIcon icon, int x, int y, int size, int color) {
        icon(graphics, icon, (float) x, (float) y, (float) size, color, color, color);
    }

    private static void layers(DrawContext graphics, Font font, int back, int middle, int front,
                               int backColor, int middleColor, int frontColor, float x, float y, float size) {
        if (size <= 0 || !Float.isFinite(size)) return;
        layer(graphics, font, back, x, y, size, backColor);
        layer(graphics, font, middle, x, y, size, middleColor);
        layer(graphics, font, front, x, y, size, frontColor);
    }

    private static void layer(DrawContext graphics, Font font, int codepoint, float x, float y,
                              float size, int color) {
        color = Opacity.apply(color);
        if (Colors.transparent(color)) return;
        Font.Glyph glyph = font.glyph(codepoint);
        if (glyph == null || glyph.planeBounds() == null) return;
        Font.Bounds p = glyph.planeBounds();
        font.quad(graphics, glyph, x + p.left() * size, y + p.top() * size,
                x + p.right() * size, y + p.bottom() * size, color);
    }

    private static void rect(DrawContext graphics, int x, int y, int width, int height, int radius,
                             int corners, int color, int bottomColor) {
        color = Opacity.apply(color);
        bottomColor = Opacity.apply(bottomColor);
        if (width <= 0 || height <= 0 || (Colors.transparent(color) && Colors.transparent(bottomColor))) return;
        float r = Math.max(0, Math.min(radius, Math.min(width, height) / 2f));
        Rounded.fill(graphics, x, y, width, height, r, corners & ALL_CORNERS, color);
    }

    private static int hsv(float h, float s, float v) {
        float c = v * s;
        float x = c * (1f - Math.abs((h / 60f) % 2f - 1f));
        float m = v - c;
        float r;
        float g;
        float b;
        if (h < 60) { r = c; g = x; b = 0; }
        else if (h < 120) { r = x; g = c; b = 0; }
        else if (h < 180) { r = 0; g = c; b = x; }
        else if (h < 240) { r = 0; g = x; b = c; }
        else if (h < 300) { r = x; g = 0; b = c; }
        else { r = c; g = 0; b = x; }
        return 0xFF000000 | (Math.round((r + m) * 255) << 16) | (Math.round((g + m) * 255) << 8)
                | Math.round((b + m) * 255);
    }

    private Draw() {}
}
