package com.utilitymod.echo.api;

import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.MouseInput;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

/**
 * echo api.MixinAccessorCompat. Echo already falls back to reflection when
 * its accessor mixins are absent; this keeps only that path (runtime names
 * first, Yarn names second).
 */
public final class MixinAccessorCompat {
    private MixinAccessorCompat() {}

    public static InputUtil.Key getKey(KeyBinding km) {
        return Accessors.getKey(km);
    }

    public static int getClickCount(KeyBinding km) {
        return Accessors.getClickCount(km);
    }

    public static Mouse getMouseHandler(MinecraftClient mc) {
        return mc.mouse;
    }

    public static Keyboard getKeyboardHandler(MinecraftClient mc) {
        return mc.keyboard;
    }

    /** Mouse.onMouseButton(long window, MouseInput input, int action). */
    public static void invokeOnButton(Mouse handler, long window, MouseInput input, int action) {
        if (handler == null) return;
        try {
            Accessors.method(Mouse.class, 3, LT.L1, LT.L2).invoke(handler, window, input, action);
        } catch (Throwable ignored) {
            // simulation unavailable: caller falls back to the key binding counter
        }
    }

    /** Keyboard.onKey(long window, int action, KeyInput input). */
    public static void invokeKeyPress(Keyboard handler, long window, int action, KeyInput keyEvent) {
        if (handler == null) return;
        try {
            Accessors.method(Keyboard.class, 3, LT.L3, LT.L4).invoke(handler, window, action, keyEvent);
        } catch (Throwable ignored) {
            // simulation unavailable
        }
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9a0e,0xdf19,0xd360,0x27db,0xb618,0xbd3a,0xcae9,0xa31e,0x14a6,0x70e6,0x217c}, new char[]{0x27b6,0x9a6b,0xca9d,0xdf71,0x2113,0xbd5e,0xd33f,0x70d7,0xa328,0xb628,0x1497}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x529d,0x21f0,0xf732,0x238d,0x47d3,0x3e27,0xa38b,0x2f17,0xe132,0xed30,0x6324,0x43ad,0xb705}, new char[]{0x634b,0x47bd,0x3e6a,0xf75d,0xed45,0xe141,0x52f8,0x2f55,0x23f8,0xb771,0x43d9,0xa3e4,0x219e}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x95f0,0x5fb8,0x56b2,0xd403,0x752c,0x6e12,0xd100,0xe2c7,0xf3b4,0x43d9,0xc59e}, new char[]{0xd16d,0x6e77,0xc5ea,0xf3dc,0xd46c,0x7548,0x4386,0xe2f6,0x95c4,0x5684,0x5f8e}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xda47,0x9398,0x2a5b,0x6cbc,0x0084}, new char[]{0x2a34,0xda29,0x93d3,0x6cd9,0x00fd}).plainString();
    }
}
