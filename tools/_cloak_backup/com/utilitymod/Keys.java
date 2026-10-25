package com.utilitymod;

import com.utilitymod.gui.FrameManager;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

/**
 * RSHIFT handling for late injection.
 *
 * <p>Fabric's {@code KeyBindingHelper.registerKeyBinding} throws
 * {@code IllegalStateException: GameOptions has already been initialised}
 * when called after game start - which is always the case for an injected
 * mod. So instead of registering a keybind, we poll the physical key state
 * via GLFW at tick START (already on the client thread via
 * {@code TickDispatcher}) with edge detection. No GameOptions touched.
 */
public final class Keys {
    private static long window = 0;
    private static boolean wasDown = false;

    public static void init() {
        // Intentionally nothing to register (see class javadoc).
    }

    public static void tick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        try {
            com.utilitymod.gui.ModuleBinds.tick();
        } catch (Throwable ignored) {
        }
        if (window == 0) {
            try {
                window = client.getWindow().getHandle();
            } catch (Throwable t) {
                return;
            }
            if (window == 0) return;
        }
        boolean down;
        try {
            down = GLFW.glfwGetKey(window, dev.koifih.client.input.Keybinds.openKeyCode()) == GLFW.GLFW_PRESS;
        } catch (Throwable t) {
            return;
        }
        boolean pressed = down && !wasDown;
        wasDown = down;
        // Only open from gameplay (no screen up): the wing screen itself
        // handles RSHIFT/ESC to close, so we must not toggle while it is open.
        if (pressed) {
            UtilityMod.LOGGER.info("Open key pressed: screen=" + (client.currentScreen == null ? "none" : client.currentScreen.getClass().getName())
                    + " player=" + (client.player != null));
        }
        // open from gameplay; also close one of our own GUIs if it is somehow still up (stuck/invisible)
        if (pressed && client.player != null && (client.currentScreen == null || FrameManager.isOpen())) {
            FrameManager.toggle();
        }
    }
}
