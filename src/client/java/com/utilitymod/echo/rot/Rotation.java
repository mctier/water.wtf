package com.utilitymod.echo.rot;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/** Clarity port: static facade over the Bus-registered IRotation. */
public final class Rotation {
    private Rotation() {
    }

    private static IRotation getIRotation() {
        return Bus.get(IRotation.class);
    }

    public static boolean isActive() {
        IRotation rotation = getIRotation();
        return rotation != null && rotation.isActive();
    }

    public static boolean hasSilentRotation() {
        IRotation rotation = getIRotation();
        return rotation != null && rotation.hasSilentRotation();
    }

    public static boolean isActivelyTracking() {
        IRotation rotation = getIRotation();
        return rotation != null && rotation.isActivelyTracking();
    }

    public static float getSilentServerYaw() {
        IRotation rotation = getIRotation();
        return rotation != null ? rotation.getSilentServerYaw() : 0.0F;
    }

    public static float getSilentServerPitch() {
        IRotation rotation = getIRotation();
        return rotation != null ? rotation.getSilentServerPitch() : 0.0F;
    }

    public static void setSilentRotation(float yaw, float pitch) {
        IRotation rotation = getIRotation();
        if (rotation != null) {
            rotation.setSilentRotation(yaw, pitch);
        }
    }

    public static void clearSilentRotation() {
        IRotation rotation = getIRotation();
        if (rotation != null) {
            rotation.clearSilentRotation();
        }
    }

    public static void commitSnappedRotation(float yaw, float pitch) {
        IRotation rotation = getIRotation();
        if (rotation != null) {
            rotation.commitSnappedRotation(yaw, pitch);
        }
    }

    public static void commitUseItemRotation(float yaw, float pitch) {
        IRotation rotation = getIRotation();
        if (rotation != null) {
            rotation.commitUseItemRotation(yaw, pitch);
        }
    }

    public static Vec3d getRotationVector(float pitch, float yaw) {
        IRotation rotation = getIRotation();
        if (rotation != null) {
            return rotation.getRotationVector(pitch, yaw);
        }

        // vanilla Entity.getRotationVector(float, float) math, string for string off the 1.21.11 source
        float f = pitch * (float) (Math.PI / 180.0);
        float g = -yaw * (float) (Math.PI / 180.0);
        float h = MathHelper.cos(g);
        float i = MathHelper.sin(g);
        float j = MathHelper.cos(f);
        float k = MathHelper.sin(f);
        return new Vec3d(i * j, -k, h * j);
    }
}
