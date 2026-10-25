package com.utilitymod.cgui;

import dev.koifih.client.render.Draw;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

import static com.utilitymod.cgui.CuiTheme.*;

/**
 * Decorative texture drawn at row scale. A bad identifier or missing asset
 * draws nothing - layout never breaks on art.
 */
public class Image extends Widget {
    private final Identifier texture;
    private final String label;

    public Image(String label, String namespace, String path, float height) {
        this.label = label;
        this.texture = Identifier.of(namespace, path);
        h = Math.max(8f, height);
    }

    public Image(Identifier texture, float height) {
        this.label = null;
        this.texture = texture == null ? Identifier.of("minecraft", "textures/block/obsidian.png") : texture;
        h = Math.max(8f, height);
    }

    @Override
    public void render(DrawContext ctx, double mx, double my, float delta) {
        int xx = Math.round(x), yy = Math.round(y);
        int ww = Math.round(w), hh = Math.round(h);
        if (ww < 1 || hh < 1) return;
        Draw.fadedTexture(ctx, texture, x, y, w, h, 0f, 1f);
        if (label != null) {
            dev.koifih.client.render.Text.draw(ctx, label, xx + 4, yy + hh - 6, TEXT_SIZE_SMALL, TEXT_MUTED);
        }
    }
}
