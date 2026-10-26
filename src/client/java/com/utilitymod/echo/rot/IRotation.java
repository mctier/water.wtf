package com.utilitymod.echo.rot;

import net.minecraft.util.math.Vec3d;

/** Clarity port: silent-rotation capability, implemented by the engine helper. */
public interface IRotation extends Capability {
    boolean isActive();

    boolean hasSilentRotation();

    boolean isActivelyTracking();

    float getSilentServerYaw();

    float getSilentServerPitch();

    void setSilentRotation(float yaw, float pitch);

    void clearSilentRotation();

    void commitSnappedRotation(float yaw, float pitch);

    void commitUseItemRotation(float yaw, float pitch);

    Vec3d getRotationVector(float pitch, float yaw);
}
