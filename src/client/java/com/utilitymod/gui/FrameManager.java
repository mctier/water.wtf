package com.utilitymod.gui;

import net.minecraft.client.MinecraftClient;

/**
 * Opens/closes the wing. Called from the keybind handler at tick START
 * (see {@code Keys}), so screen state changes stay on the sanctioned path -
 * rendering itself stays on the render thread inside the screen.
 */
public final class FrameManager {
    /** PANEL = adin window, DROPDOWN = category columns, CARD = sigma card grid. */
    public enum Style { DROPDOWN, WATER }

    public static volatile Style style = Style.WATER;
    /** Slowly drifting accent-coloured blobs behind the dropdown GUI. */
    public static volatile boolean animatedBg = true;

    private FrameManager() {}

    public static void init() {
        // Nothing to register: RSHIFT is polled, screen opens on demand.
    }

    public static void toggle() {
        if (com.utilitymod.inject.Hooks.PARALYZED) return;   // unloaded
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        com.utilitymod.UtilityMod.LOGGER.info(LT.L1 + style + LT.L2 + (client.currentScreen == null ? LT.L3 : client.currentScreen.getClass().getSimpleName()));
        if (isOpen()) {
            client.setScreen(null);
        } else {
            UiSounds.play(UiSounds.Sound.OPEN);
            if (client.player == null) return;
            // Fresh instance: panelReveal animates 0 -> 1 on open by construction.
            try {
                client.setScreen(switch (style) {
                    case DROPDOWN -> new DropdownGui();
                    case WATER -> new WaterGui();
                });
            } catch (Throwable t) {
                // a broken style must never lock the menu: log it and fall back to the dropdown GUI
                com.utilitymod.UtilityMod.LOGGER.error(LT.L4 + style + LT.L5, t);
                style = Style.DROPDOWN;
                client.setScreen(new DropdownGui());
            }
        }
    }

    public static boolean isOpen() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client != null && (client.currentScreen instanceof dev.koifih.client.ui.wing.Wing
                || client.currentScreen instanceof DropdownGui
                || client.currentScreen instanceof CardGui
                || client.currentScreen instanceof WaterGui);
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x36fe,0x347a,0x1e9c,0x5039,0x47c6,0x1777,0xfb9c,0x3b66,0x32b7,0x4ac3,0x2bcf,0x68ff,0xc73a,0xd58c,0x36c6,0xd1a4,0x32cc,0xb9bd}, new char[]{0xd5cb,0x1ec9,0x32fe,0x36e6,0xfbe8,0x5056,0x3699,0x1710,0x4aaf,0x2baa,0x68c5,0xd184,0x47b5,0xb9c9,0x3403,0xc756,0x32a9,0x3b5b}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8142,0x0bc2,0xe68e,0x16e2,0xbf9a,0x9248,0xa2bd,0x1469}, new char[]{0x0be2,0xa2ce,0xbff9,0x923a,0x140c,0x1687,0xe6e0,0x817f}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x00d2,0x8e0d,0x6ebf,0x593d}, new char[]{0x8e63,0x6ed0,0x5953,0x00b7}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb95b,0xa7f2,0x8fb5,0x4e1a,0x25d7,0x217e,0xdde2,0xbaa3,0x4a1e,0x0efa}, new char[]{0x2590,0xa7a7,0x0eb3,0x8f95,0xbad0,0xb92f,0xdd9b,0x2112,0x4a7b,0x4e3a}).plainString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd7ce,0x65ae,0x09ff,0xef15,0xfd28,0x17f4,0x88a5,0x0099,0x9aeb,0xe03d,0xdddf,0xe345,0xf2a1,0xa610,0x5921}, new char[]{0xa630,0x1792,0xfd49,0xddb6,0xef79,0xe058,0xf2c5,0x9acb,0x88d1,0xe32a,0x658e,0x594e,0xd7be,0x099a,0x00f7}).plainString();
    }
}
