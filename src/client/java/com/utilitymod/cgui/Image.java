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
        this.texture = texture == null ? Identifier.of(LT.L1, LT.L2) : texture;
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


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x72e9,0x12e3,0xd72b,0x2b21,0x1708,0x6630,0xc4a9,0x19fb,0xafb2}, new char[]{0x7284,0xafdb,0x2b4f,0x6655,0xc4ca,0x1989,0x1282,0x176e,0xd75f}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0d51,0x61a5,0x8a82,0xc9d7,0x39da,0x1a49,0x5091,0x5243,0x95af,0x8a15,0x7fb0,0xc571,0x5a9b,0x233d,0xa857,0x2733,0x9753,0x3574,0xfdb2,0xb9fb,0x1ef6,0xb846,0xe35d,0x7f5c,0x4cae,0xf2d5,0x5b09}, new char[]{0x8a61,0x8ae7,0x50e9,0x1e82,0x7f29,0x1a3b,0xc514,0xfdc1,0xc9f8,0xe33f,0xf2b9,0x61ca,0x9730,0xb990,0x5ab4,0x7fdf,0x4ccc,0x95dc,0x0d38,0x2757,0x2354,0x5222,0x39b4,0xb868,0x5b79,0xa839,0x3513}).plainString();
    }
}
