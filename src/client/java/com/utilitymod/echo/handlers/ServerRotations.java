package com.utilitymod.echo.handlers;

import com.utilitymod.echo.event.EventManager;
import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventMove;
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
 * Server-side-only rotations (LegacyPort RotationManager model), shared by the crystal-pvp modules.
 *
 * <p>Modules call {@link #request} (or {@link #lookAt}) every tick they want to aim, from their input-phase
 * handler. Right before the vanilla movement packet the highest-priority request of the tick wins: the
 * server look eases toward it, is swapped into the player AND the outgoing packet values, and the real
 * camera rotation is put back right after the packet. The camera, crosshair and mouse never move.
 *
 * <p>Interactions must ride the look the server already received: act only when {@link #sentHits}/
 * {@link #sentHitsBlock} say the last SENT rotation covers the target, which is always on a later input
 * pass than the packet that carried it (so the order is look → interact, never the reverse).
 */
public final class ServerRotations {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final ServerRotations INSTANCE = new ServerRotations();
    private static boolean registered;
    /** Show the server-side yaw on your own player model (third person). */
    public static volatile boolean showOnModel = true;

    private static Object owner;
    private static float goalYaw, goalPitch, speed;
    private static int priority = Integer.MIN_VALUE;
    private static long requestTick = -1, tick;

    private static boolean active, swapped;
    private static float sYaw, sPitch, prevYaw, prevPitch;
    private static float sentYaw, sentPitch;
    private static Object sentOwner;

    private static float rYaw, rPitch, rBody, rHead, rLastYaw, rLastPitch, rLastBody, rLastHead;

    private ServerRotations() {}

    public static void init() {
        if (registered) return;
        registered = true;
        EventManager.register(INSTANCE);
    }

    /** Ask for a server look this tick. Higher priority wins; speed is the 0..1 share of the gap closed per tick. */
    public static void request(Object who, float yaw, float pitch, float spd, int prio) {
        init();
        if (requestTick == tick && prio < priority) return;
        owner = who;
        goalYaw = yaw;
        goalPitch = MathHelper.clamp(pitch, -90f, 90f);
        speed = MathHelper.clamp(spd, 0.05f, 1f);
        priority = prio;
        requestTick = tick;
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
        float yaw = prevYaw + MathHelper.wrapDegrees(sYaw - prevYaw) * tickDelta;
        float pitch = prevPitch + (sPitch - prevPitch) * tickDelta;
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

    @EventSubscribe(priority = EventSubscribe.Priority.LOWEST)
    @SuppressWarnings("unused")
    private void onMovePre(EventMove.Pre event) {
        var p = mc.player;
        if (p == null) return;
        boolean requested = requestTick == tick;
        if (!active) { sYaw = p.getYaw(); sPitch = p.getPitch(); }
        prevYaw = sYaw;
        prevPitch = sPitch;
        float gy = requested ? goalYaw : p.getYaw(), gp = requested ? goalPitch : p.getPitch();
        float k = requested ? speed : 0.5f;
        float ny = sYaw + MathHelper.wrapDegrees(gy - sYaw) * k;
        float np = MathHelper.clamp(sPitch + (gp - sPitch) * k, -90f, 90f);
        float[] snapped = RotationUtils.applyGCD(new float[]{ny, np}, new float[]{sYaw, sPitch});
        sYaw = snapped[0];
        sPitch = MathHelper.clamp(snapped[1], -90f, 90f);
        float off = Math.abs(MathHelper.wrapDegrees(sYaw - p.getYaw())) + Math.abs(sPitch - p.getPitch());
        active = requested || off > 0.5f;
        if (!active) {
            sentOwner = null;
            return;
        }
        rYaw = p.getYaw(); rPitch = p.getPitch(); rBody = p.bodyYaw; rHead = p.headYaw;
        rLastYaw = p.lastYaw; rLastPitch = p.lastPitch; rLastBody = p.lastBodyYaw; rLastHead = p.lastHeadYaw;
        p.setYaw(sYaw);
        p.setPitch(sPitch);
        p.bodyYaw = sYaw;
        p.headYaw = sYaw;
        p.lastYaw = prevYaw; p.lastPitch = prevPitch; p.lastBodyYaw = prevYaw; p.lastHeadYaw = prevYaw;
        // the packet's yaw/pitch are taken from this event after dispatch, not from the player fields
        event.setYaw(sYaw);
        event.setPitch(sPitch);
        swapped = true;
        sentOwner = requested ? owner : null;
    }

    @EventSubscribe(priority = EventSubscribe.Priority.HIGHEST)
    @SuppressWarnings("unused")
    private void onMovePost(EventMove.Post event) {
        var p = mc.player;
        if (swapped && p != null) {
            sentYaw = sYaw;
            sentPitch = sPitch;
            p.setYaw(rYaw); p.setPitch(rPitch); p.bodyYaw = rBody; p.headYaw = rHead;
            p.lastYaw = rLastYaw; p.lastPitch = rLastPitch; p.lastBodyYaw = rLastBody; p.lastHeadYaw = rLastHead;
            if (showOnModel) {
                // visual only: turn your own model's head/body to the server yaw for third person.
                // The camera follows yaw/pitch, not these, so your view is untouched.
                p.headYaw = sYaw; p.bodyYaw = sYaw;
                p.lastHeadYaw = prevYaw; p.lastBodyYaw = prevYaw;
            }
        } else if (p != null) {
            sentYaw = p.getYaw();
            sentPitch = p.getPitch();
        }
        swapped = false;
        tick++;
        priority = Integer.MIN_VALUE;
    }
}
