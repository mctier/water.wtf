package com.utilitymod.gui;

import net.minecraft.client.MinecraftClient;

/**
 * Opens/closes the wing. Called from the keybind handler at tick START
 * (see {@code Keys}), so screen state changes stay on the sanctioned path -
 * rendering itself stays on the render thread inside the screen.
 */
public final class FrameManager {
    /** PANEL = adin window, DROPDOWN = category columns, CARD = sigma card grid. */
    public enum Style { PANEL, DROPDOWN, CARD, ORBIT, WATER }

    public static volatile Style style = Style.WATER;
    /** Slowly drifting accent-coloured blobs behind the dropdown GUI. */
    public static volatile boolean animatedBg = true;

    private FrameManager() {}

    public static void init() {
        // Nothing to register: RSHIFT is polled, screen opens on demand.
    }

    public static void toggle() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        com.utilitymod.UtilityMod.LOGGER.info("GUI toggle: style=" + style + " screen=" + (client.currentScreen == null ? "none" : client.currentScreen.getClass().getSimpleName()));
        if (isOpen()) {
            client.setScreen(null);
        } else {
            UiSounds.play(UiSounds.Sound.OPEN);
            if (client.player == null) return;
            // Fresh instance: panelReveal animates 0 -> 1 on open by construction.
            try {
                client.setScreen(switch (style) {
                    case DROPDOWN -> new DropdownGui();
                    case CARD -> new CardGui();
                    case ORBIT -> new OrbitGui();
                    case WATER -> new WaterGui();
                    case PANEL -> new dev.koifih.client.ui.wing.Wing();
                });
            } catch (Throwable t) {
                // a broken style must never lock the menu: log it and fall back to the dropdown GUI
                com.utilitymod.UtilityMod.LOGGER.error("GUI style " + style + " failed to open", t);
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
                || client.currentScreen instanceof OrbitGui
                || client.currentScreen instanceof WaterGui);
    }
}
