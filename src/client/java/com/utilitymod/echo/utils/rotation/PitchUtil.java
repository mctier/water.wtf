package com.utilitymod.echo.utils.rotation;

import net.minecraft.util.math.MathHelper;

public final class PitchUtil {
    private PitchUtil() {
    }

    public static float clampPitch(float pitch) {
        return MathHelper.clamp(pitch, -90.0F, 90.0F);
    }
}
