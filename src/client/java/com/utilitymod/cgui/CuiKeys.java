package com.utilitymod.cgui;

import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

/**
 * Opens the cgui demo with CTRL+SHIFT+G, polled with edge detection - no
 * GameOptions touched (same law as Keys: Fabric keybind registration is
 * barred for late injection). CTRL+SHIFT+G again closes it.
 */
public final class CuiKeys {
    private static boolean wasDown;

    private CuiKeys() {}

    /** Self-registers the poll on the start-of-tick dispatcher. */
    public static void init() {
        com.utilitymod.core.TickDispatcher.onTickStart(CuiKeys::tick);
    }

    private static void tick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        long window;
        try {
            window = client.getWindow().getHandle();
        } catch (Throwable t) {
            return;
        }
        boolean combo = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_G) == GLFW.GLFW_PRESS
                && GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                && GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS;
        boolean pressed = combo && !wasDown;
        wasDown = combo;
        if (!pressed) return;
        boolean open = client.currentScreen instanceof CuiScreen;
        if (open) {
            client.setScreen(null);
            com.utilitymod.gui.UiSounds.play(com.utilitymod.gui.UiSounds.Sound.CLOSE);
        } else if (client.player != null && client.currentScreen == null) {
            com.utilitymod.gui.UiSounds.play(com.utilitymod.gui.UiSounds.Sound.OPEN);
            // hot-reload law: every open re-reads the user json; falls back to the built-in window
            CuiScreen built = CuiLoader.load();
            if (built == null) built = CuiHome.open0();
            for (Window win : built.windows()) CuiLayout.apply(built.screenKey, win);
            client.setScreen(built);
        }
    }
}
