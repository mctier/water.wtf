package com.utilitymod.echo.utils.rotation;

import com.utilitymod.echo.handlers.RotationHandler;
import com.utilitymod.echo.utils.Imports;
import net.minecraft.util.math.MathHelper;

/**
 * Tracks the per-tick yaw/pitch delta from the previous tick for use in Grim
 * DuplicateRotPlace bypass: the check gates on {@code deltaX > 2}, so as long as
 * we only fire interactions on a tick where last tick's delta was small, we
 * never trip the check regardless of what {@code lastPlacedDeltaX} held.
 *
 * <p>{@link #update()} must be called at HEAD of every {@code onTick} (before
 * {@code MouseHandler.turnPlayer} applies this tick's rotation). It records both
 * the visible player rotation and the server-side silent rotation so
 * {@link #isSettled} can pick whichever is actually being sent this tick.
 */
public final class RotationConvergenceTracker implements Imports {

    private float prevPlayerYaw;
    private float prevPlayerPitch;
    private float prevServerYaw;
    private float prevServerPitch;

    private float lastDeltaPlayerYaw;
    private float lastDeltaPlayerPitch;
    private float lastDeltaServerYaw;
    private float lastDeltaServerPitch;

    private float lastInteractionPitchDelta;
    private boolean primed;
    private boolean hasInteractionPitchDelta;

    public void reset() {
        primed = false;
        hasInteractionPitchDelta = false;
    }

    public void update() {
        if (mc.player == null) {
            primed = false;
            return;
        }

        float curPlayerYaw = mc.player.getYaw();
        float curPlayerPitch = mc.player.getPitch();
        float curServerYaw = RotationHandler.getServerYaw();
        float curServerPitch = RotationHandler.getServerPitch();

        if (primed) {
            lastDeltaPlayerYaw = Math.abs(MathHelper.wrapDegrees(curPlayerYaw - prevPlayerYaw));
            lastDeltaPlayerPitch = Math.abs(curPlayerPitch - prevPlayerPitch);
            lastDeltaServerYaw = Math.abs(MathHelper.wrapDegrees(curServerYaw - prevServerYaw));
            lastDeltaServerPitch = Math.abs(curServerPitch - prevServerPitch);
        } else {
            // First sample of the session: no delta yet, force "not settled".
            lastDeltaPlayerYaw = lastDeltaPlayerPitch = Float.MAX_VALUE;
            lastDeltaServerYaw = lastDeltaServerPitch = Float.MAX_VALUE;
        }

        prevPlayerYaw = curPlayerYaw;
        prevPlayerPitch = curPlayerPitch;
        prevServerYaw = curServerYaw;
        prevServerPitch = curServerPitch;
        primed = true;
    }

    /** True if last tick's per-tick yaw AND pitch delta were both under {@code threshold} degrees. */
    public boolean isSettled(float threshold) {
        if (!primed) return false;
        boolean silent = RotationHandler.isHasSilentRotation();
        float deltaYaw = silent ? lastDeltaServerYaw : lastDeltaPlayerYaw;
        float deltaPitch = silent ? lastDeltaServerPitch : lastDeltaPlayerPitch;
        return deltaYaw < threshold && deltaPitch < threshold;
    }

    /**
     * Grim's DuplicateRotPlace check keys off XRot/pitch delta only. A stable
     * low pitch delta is always fine, but while falling the required pitch can
     * keep changing; allow that as long as it is not duplicating the previous
     * interaction's pitch delta.
     */
    public boolean isDuplicateRotPlaceSafe(float threshold) {
        if (!primed) return false;
        float deltaPitch = currentPitchDelta();
        if (deltaPitch < threshold) return true;
        return !hasInteractionPitchDelta || Math.abs(deltaPitch - lastInteractionPitchDelta) >= 0.001f;
    }

    public void markInteraction() {
        if (!primed) return;
        lastInteractionPitchDelta = currentPitchDelta();
        hasInteractionPitchDelta = true;
    }

    private float currentPitchDelta() {
        return RotationHandler.isHasSilentRotation() ? lastDeltaServerPitch : lastDeltaPlayerPitch;
    }
}
