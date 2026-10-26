package com.utilitymod.echo.rot;

/** Clarity port: per-tick silent-rotation state shared with the mixin bodies. */
public final class LocalPlayerRotationState {
    public static float tickServerYaw;
    public static float tickServerPitch;
    public static float realYaw;
    public static boolean silentActive;
    /** our own tick counter, bumped by the local-player tick mixin */
    public static int tickServerTick;

    private LocalPlayerRotationState() {
    }
}
