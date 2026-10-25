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
        if (com.utilitymod.inject.Hooks.PARALYZED) return;   // unloaded: no menu key, no binds
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
            UtilityMod.LOGGER.info(LT.L1 + (client.currentScreen == null ? LT.L2 : client.currentScreen.getClass().getName())
                    + LT.L3 + (client.player != null));
        }
        // open from gameplay; also close one of our own GUIs if it is somehow still up (stuck/invisible)
        if (pressed && client.player != null && (client.currentScreen == null || FrameManager.isOpen())) {
            FrameManager.toggle();
        }
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5b7c,0xab96,0x3cd7,0x7dcd,0x7a1b,0x0035,0x13a8,0x7d37,0xb5e0,0x66de,0xb2b3,0xb0dd,0x8eda,0xf5b8,0x86ad,0xba59,0xaad6,0x86dd,0x6204,0xbd74,0x4cdc,0xa486,0xaa81,0x96fb,0x13e8}, new char[]{0xabd9,0x7d47,0xaab3,0x86c3,0x6224,0xb0b6,0xba3c,0x004c,0x3cf7,0x13d8,0xaaf3,0xb585,0x66ad,0x4caf,0x8ebf,0xb2d7,0x7a21,0xf598,0x86ae,0x9698,0x139a,0xa4e3,0x5b19,0x7da3,0xbd49}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x99e0,0x70fa,0xc07e,0x495b}, new char[]{0xc010,0x998f,0x7094,0x493e}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf020,0x88ea,0x553a,0xd889,0x6db2,0x1bdc,0x786d,0x08dd}, new char[]{0x784d,0x889a,0x5556,0xf041,0xd8f0,0x6dd7,0x1bae,0x08e0}).plainString();
    }
}
