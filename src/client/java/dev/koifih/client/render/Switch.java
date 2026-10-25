package dev.koifih.client.render;

import dev.koifih.client.ui.Theme;
import dev.koifih.client.util.Colors;
import net.minecraft.client.gui.DrawContext;
public final class Switch {
    private static final float STRETCH = 0.28f;

    public static void draw(DrawContext graphics, float x, float y, float width, int height, float on, float squash) {
        int radius = height / 2;
        Draw.rect(graphics, x, y, width, height, radius, Colors.lerp(Theme.TOGGLE_OFF_BORDER, Theme.ACCENT, on));
        Draw.rect(graphics, x + 1f, y + 1f, width - 2f, height - 2, Math.max(0, radius - 1),
                Colors.lerp(Theme.TOGGLE_OFF, Theme.ACCENT, on));
        int inset = Math.max(1, Math.round(height / 6f));
        int size = Math.max(1, height - 2 * inset);
        float stretch = 1f + STRETCH * squash;
        int wide = Math.max(1, Math.round(size * stretch));
        int tall = Math.max(1, Math.round(size / stretch));
        float travel = width - 2 * inset - size;
        float thumbX = x + inset + travel * on - (wide - size) * 0.5f;
        float limit = x + width - inset - wide;
        Draw.rect(graphics, Math.clamp(thumbX, x + inset, limit), y + inset + (size - tall) * 0.5f,
                wide, tall, tall / 2, Colors.lerp(Theme.THUMB_OFF, Theme.ON_ACCENT, on));
    }

    private Switch() {}
}
