package com.utilitymod.echo.handlers;

import com.utilitymod.echo.features.impl.combat.SilentRotations;
import com.utilitymod.echo.utils.rotation.RotationUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * Server-side rotation front-end at the clarity engine (the commit-on-tick
 * exchange in {@link RotationHandler} + the local-player swap in the silent
 * rotation mixin). The module surface every crystal-pvp caller compiles with
 * is unchanged, but the movement-swap engine of the old LegacyPort model no
 * longer runs: the silent rotation engine's tick-commit is the only authority.
 *
 * <p>High-priority per-tick request resolution stays here though (the request
 * is written into {@link RotationHandler} so the mixin is what commits it). Modules
 * still call {@link #request} (or {@link #lookAt}) every tick they want to aim.
 * Interactions must ride the look the server already received: act only when
 * {@link #sentHits}/{@link #sentHitsBlock} say the last SENT rotation covers
 * the target.
 */
public final class ServerRotations {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    /** Show the server-side yaw on your own player model (third person). */
    public static volatile boolean showOnModel = true;

    private static Object owner;
    private static float goalYaw, goalPitch, speed;
    private static int priority = Integer.MIN_VALUE;
    private static long requestTick = -1, tick;

    private static boolean active;
    private static float sentYaw, sentPitch;
    private static Object sentOwner;

    private ServerRotations() {}

    /** Engine turn-up: nothing to wire anymore — kept for all legacy init() callers. */
    public static void init() {
    }

    /** Ask for a server look this tick. Higher priority wins; speed is the 0..1 share of the gap closed per tick. */
    public static void request(Object who, float yaw, float pitch, float spd, int prio) {
        if (requestTick == tick && prio < priority) return;
        owner = who;
        goalYaw = yaw;
        goalPitch = MathHelper.clamp(pitch, -90f, 90f);
        speed = MathHelper.clamp(spd, 0.05f, 1f);
        priority = prio;
        requestTick = tick;
        // commit-on-tick: the winner of this tick's request drives the engine's silent state
        if (mc.player != null) {
            active = true;
            sentOwner = owner;
            RotationHandler.setServerRotationsWithDelta(goalYaw, goalPitch);
            RotationHandler.setHasSilentRotation(true);
        }
    }

    public static void lookAt(Object who, Vec3d point, float spd, int prio) {
        if (mc.player == null || point == null) return;
        float[] r = RotationUtils.calculate(mc.player.getEyePos(), point);
        request(who, r[0], r[1], spd, prio);
    }

    /** Yaw offset between the camera and a point, for FOV caps. */
    public static float yawOffset(Vec3d point) {
        if (mc.player == null) return 0f;
        float[] r = RotationUtils.calculate(mc.player.getEyePos(), point);
        return Math.abs(MathHelper.wrapDegrees(r[0] - mc.player.getYaw()));
    }

    public static boolean isActive() { return active; }

    /** Interpolated server yaw/pitch for drawing your own model, or null when not overriding. */
    public static float[] renderRotation(float tickDelta) {
        if (!active || !showOnModel) return null;
        float yaw = RotationHandler.getServerYaw();
        float pitch = RotationHandler.getServerPitch();
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) return null;
        return new float[]{yaw, pitch};
    }
    public static boolean ownedBy(Object who) { return active && sentOwner == who; }

    /** Direction of the rotation the server last received (the camera look when we did not override). */
    public static Vec3d sentLook() {
        float p = sentPitch * MathHelper.RADIANS_PER_DEGREE, y = -sentYaw * MathHelper.RADIANS_PER_DEGREE;
        float cp = MathHelper.cos(p);
        return new Vec3d(MathHelper.sin(y) * cp, -MathHelper.sin(p), MathHelper.cos(y) * cp);
    }

    /** True when the last sent look ray hits {@code box} within interaction reach. */
    public static boolean sentHits(Box box) {
        if (mc.player == null || box == null) return false;
        Vec3d eye = mc.player.getEyePos();
        double reach = mc.player.getEntityInteractionRange();
        var hit = box.expand(1.0e-3).raycast(eye, eye.add(sentLook().multiply(reach + 1)));
        return hit.isPresent() && hit.get().squaredDistanceTo(eye) <= reach * reach;
    }

    /** The block the last sent look actually points at (block reach), or null. */
    public static BlockHitResult sentBlockHit() {
        if (mc.player == null || mc.world == null) return null;
        Vec3d eye = mc.player.getEyePos();
        double reach = mc.player.getBlockInteractionRange();
        HitResult r = mc.world.raycast(new RaycastContext(eye, eye.add(sentLook().multiply(reach)),
                RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player));
        return r instanceof BlockHitResult b && r.getType() == HitResult.Type.BLOCK ? b : null;
    }

    /** True when the last sent look points at {@code pos} (so an interact on it is geometrically legal). */
    public static boolean sentHitsBlock(BlockPos pos) {
        BlockHitResult b = sentBlockHit();
        return b != null && b.getBlockPos().equals(pos);
    }

    /** Called once per client tick (from {@code EchoEvents.tick}) to retire the tick's request window. */
    public static void tickWindow() {
        boolean requested = requestTick == tick;
        if (!requested) {
            active = false;
            sentOwner = null;
            if (RotationHandler.isHasSilentRotation()) {
                RotationHandler.reset();
            }
        }
        if (mc.player != null) {
            // the actually-SENT rotation this tick is the engine state consumed by the movement packets
            sentYaw = RotationHandler.getServerYaw();
            sentPitch = RotationHandler.getServerPitch();
        }
        tick++;
        priority = Integer.MIN_VALUE;
    }
}
