package com.utilitymod.echo.handlers;

import com.utilitymod.echo.utils.rotation.PitchUtil;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
/**
 * Yes this is important.
 * Likely not finished.
 *
 * Entity rotation vectors and related hooks.
 *
 * @see MixinEntity#getRotationVec(float) Entity raycasting and targeting
 * @see MixinEntity#getRotationVector() Item interactions
 * @see MixinEntity#getHeadRotationVector() Head-based interactions
 *
 * @see MixinPlayer#causeExtraKnockback() Attack knockback direction
 * @see MixinPlayer#doSweepAttack() Sweep attack direction
 *
 *
 *
 * @see ClientPlayerInteractionManager#interactItem() Sends PlayerInteractItemC2SPacket with silent yaw/pitch
 * @see MixinItem#raycast() Block interaction range and item raycasting (buckets, pearls, etc.)
 * 
 */
public class RotationHandler {
    @Getter
    @Setter
    private static float serverYaw = 0f;

    @Getter
    @Setter
    private static float serverPitch = 0f;

    @Getter
    private static float prevServerYaw = 0f;

    @Getter
    private static float prevServerPitch = 0f;

    @Getter
    @Setter
    private static float serverDeltaYaw = 0f;

    @Getter
    @Setter
    private static boolean hasSilentRotation = false;

    // Credit to Nami Client for helping me figure this out (the weird AimModulo360
    // flag)

    /**
     * Update server rotations (called every tick to keep them in sync).
     * This prevents the AimModulo360 flag by ensuring continuity.
     */
    public static void updateServerRotations(float yaw, float pitch) {
        // Store previous values for interpolation
        prevServerYaw = serverYaw;
        prevServerPitch = serverPitch;
        // Normalize yaw to maintain continuity relative to previous serverYaw
        serverYaw = serverYaw + MathHelper.wrapDegrees(yaw - serverYaw);
        float enforcedPitch = PitchUtil.clampPitch(pitch);
        serverPitch = MathHelper.clamp(enforcedPitch, -90f, 90f);
        serverDeltaYaw = 0f;
    }

    /**
     * Set server rotations with delta tracking for silent aim.
     * Calculates the yaw delta from the last server yaw to prevent flags.
     */
    public static void setServerRotationsWithDelta(float yaw, float pitch) {
        // Store previous values for interpolation
        prevServerYaw = serverYaw;
        prevServerPitch = serverPitch;
        // Normalize yaw to maintain continuity relative to previous serverYaw
        float wrappedDelta = MathHelper.wrapDegrees(yaw - serverYaw);
        serverDeltaYaw = wrappedDelta;
        serverYaw = yaw;
        float enforcedPitch = PitchUtil.clampPitch(pitch);
        serverPitch = MathHelper.clamp(enforcedPitch, -90f, 90f);
    }

    public static void reset() {
        serverDeltaYaw = 0f;
        hasSilentRotation = false;
    }

    public static void syncToPlayer(float playerYaw, float playerPitch) {
        // Store previous values for interpolation
        prevServerYaw = serverYaw;
        prevServerPitch = serverPitch;
        // Normalize yaw to maintain continuity relative to previous serverYaw
        serverYaw = serverYaw + MathHelper.wrapDegrees(playerYaw - serverYaw);
        float enforcedPitch = PitchUtil.clampPitch(playerPitch);
        serverPitch = MathHelper.clamp(enforcedPitch, -90f, 90f);
        serverDeltaYaw = 0f;
        hasSilentRotation = false;
    }

    public static Vec3d getRotationVector(float pitch, float yaw) {
        float pitchRad = pitch * MathHelper.RADIANS_PER_DEGREE;
        float yawRad = -yaw * MathHelper.RADIANS_PER_DEGREE;
        float cosPitch = MathHelper.cos(pitchRad);
        float sinPitch = MathHelper.sin(pitchRad);
        float cosYaw = MathHelper.cos(yawRad);
        float sinYaw = MathHelper.sin(yawRad);
        return new Vec3d(sinYaw * cosPitch, -sinPitch, cosYaw * cosPitch);
    }
}
