package com.utilitymod.gui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.Text;

/**
 * GUI text helpers. All strings go through {@link #text(String)} so the font
 * can be swapped in one place.
 *
 * <p>NOTE: a Comfortaa bitmap font ({@code assets/utilitymod/font/}) was tried
 * and reverted: a late-injected jar is invisible to Minecraft's
 * {@code ResourceManager} (packs are enumerated at launch), so the custom
 * font id never resolves and every glyph renders as tofu. Manual texture
 * registration (see {@link GuiTextures}) works for icons because textures can
 * be bound directly, but fonts only load through the resource reload
 * pipeline. Vanilla text until a pack-based delivery path exists.
 */
public final class Fonts {
    private Fonts() {}

    public static Text text(String s) {
        return Text.literal(s);
    }

    public static int width(TextRenderer tr, String s) {
        return tr.getWidth(text(s));
    }

    /** Truncate with ".." using real metrics. */
    public static String fit(TextRenderer tr, String s, int max) {
        if (width(tr, s) <= max) return s;
        return tr.trimToWidth(text(s), Math.max(8, max - 8)).getString() + LT.L1;
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xffda,0x82f9}, new char[]{0x82d7,0xfff4,0x03d6,0x68aa}).plainString();
    }
}
