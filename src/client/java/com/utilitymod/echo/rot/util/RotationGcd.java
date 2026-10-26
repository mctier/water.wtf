package com.utilitymod.echo.rot.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;

/**
 * Clarity port: GCD snapping to the mouse sensitivity step.
 * The gate is driven by the engine module toggle (SilentRotations "GCD Fix");
 * with nothing set it falls open like the clarity default.
 */
public final class RotationGcd {
    /** Module-driven gate: true by default (clarity's RotationGcd.enabled), set by SilentRotations. */
    public static volatile boolean gateEnabled = true;

    private RotationGcd() {
    }

    public static boolean enabled() {
        return gateEnabled;
    }

    public static float step() {
        try {
            double sensitivity = MinecraftClient.getInstance().options.getMouseSensitivity().getValue();
            double product = sensitivity * 0.6;
            double sum = product + 0.2;
            double sumSq = sum * sum;
            double cube = sumSq * sum;
            return (float) (cube * 8.0 * 0.15);
        } catch (Throwable throwable) {
            return 0.15F;
        }
    }

    public static float snapYaw(float lastYaw, float target) {
        float step = step();
        if (step < 1.0E-4F) {
            return target;
        }

        float diff = MathHelper.wrapDegrees(target - lastYaw);
        return lastYaw + Math.round(diff / step) * step;
    }

    public static float snapPitch(float lastPitch, float target) {
        float step = step();
        if (step < 1.0E-4F) {
            return MathHelper.clamp(target, -90.0F, 90.0F);
        }

        float diff = target - lastPitch;
        float snapped = lastPitch + Math.round(diff / step) * step;
        while (snapped > 90.0F) {
            snapped -= step;
        }
        while (snapped < -90.0F) {
            snapped += step;
        }
        return snapped;
    }
}
