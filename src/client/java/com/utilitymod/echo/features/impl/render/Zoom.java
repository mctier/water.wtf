package com.utilitymod.echo.features.impl.render;

import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.settings.impl.BoolSetting;
import com.utilitymod.echo.features.settings.impl.FloatSetting;
import com.utilitymod.echo.features.settings.impl.KeybindSetting;
import com.utilitymod.echo.handlers.InputHandler;
import com.utilitymod.inject.Hooks;
import org.lwjgl.glfw.GLFW;

/** Smooth zoom while a key is held (default C); multiplies the game FOV. */
public class Zoom extends Feature {
    private final KeybindSetting key = new KeybindSetting(LT.L1, GLFW.GLFW_KEY_C);
    private final FloatSetting factor = new FloatSetting(LT.L2, 4f, 1.5f, 10f, 0.5f);
    private final BoolSetting smooth = new BoolSetting(LT.L3, true);

    private double current = 1.0;
    private long last = System.nanoTime();

    public Zoom() {
        super(new FeatureInfo(new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2277,0xa8a4,0xd120,0xbc04}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69}).toString(), new com.utilitymod.echo.utils.EncryptedString(new char[]{0x379e,0xbc0d,0xfca7,0xd930,0xc5d6,0x963a,0x82c6,0x76ae,0x11b0,0x1c97,0x6559,0xd132,0xeb95,0x0b7c,0x50c0,0x4a05,0xf04a,0xb16e,0x2274,0xa8a4,0x5f28}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87,0x76d4,0xf025,0x4a6a,0xc5bb,0x5f08,0xd959,0x50ae}).toString(), Category.RENDER));
        Hooks.fovMultiplier = this::multiplier;
    }

    private double multiplier() {
        long now = System.nanoTime();
        double dt = Math.min(0.1, (now - last) / 1e9);
        last = now;
        boolean held = isEnabled() && mc.currentScreen == null && key.getKey() > 0 && InputHandler.isBindDown(key.getKey());
        double target = held ? 1.0 / factor.getValue() : 1.0;
        if (!smooth.getValue()) current = target;
        else current += (target - current) * Math.min(1.0, dt * 14);
        return current;
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xdd18,0x61c3,0xe5ff,0xf99b,0x6f7d,0xed30,0x2b15,0x1ec8}, new char[]{0x6f27,0xdd77,0x1ea7,0x61ae,0xf9bb,0x2b5e,0xed55,0xe586}).toString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4156,0x8d5a,0x356c,0x3323,0x83ee,0xce4f}, new char[]{0x352a,0x838f,0x8d39,0x4122,0xce20,0x3351}).toString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x35bd,0x14f8,0x3cd4,0x0853,0x6b7e,0xf60f}, new char[]{0x0800,0xf662,0x3cbb,0x35d2,0x6b0a,0x1490}).toString();
    }
}
