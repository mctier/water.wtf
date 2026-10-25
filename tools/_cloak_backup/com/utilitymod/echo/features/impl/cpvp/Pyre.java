package com.utilitymod.echo.features.impl.cpvp;

import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventHandleInput;
import com.utilitymod.echo.event.impl.EventRender3D;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.settings.impl.BoolSetting;
import com.utilitymod.echo.features.settings.impl.ColorSetting;
import com.utilitymod.echo.features.settings.impl.FloatSetting;
import com.utilitymod.echo.features.settings.impl.ModeSetting;
import com.utilitymod.echo.features.settings.impl.MultiModeSetting;
import com.utilitymod.echo.handlers.ServerRotations;
import com.utilitymod.echo.utils.combat.TargetUtils;
import com.utilitymod.echo.utils.rotation.RotationUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Kill-aura rebuilt on Anubis's attack law (SkidSolutions chat-v4 + Anubis Beta 0.9.9.1):
 *
 * <ul>
 *   <li><b>Target scoring</b> — priority = Distance | Health | Angle, with a hurt-tick tiebreaker and
 *       a distance tiebreaker; sticky target gets a short-circuit return.
 *   <li><b>Aim-point three-tier</b> — shrink the hitbox by (min 0.15, width*0.3) and clamp eye onto it;
 *       if the clamped point has no LOS, fall through 85% / 50% / 15% body heights; if none has LOS,
 *       no attack this tick (don't guess).
 *   <li><b>Crit lane</b> — Off / Wait / Jump. Wait holds the swing while airborne and lands it on the
 *       vertical-velocity peak. Jump injects a one-tick jump input when attack is 7 ticks from full.
 *   <li><b>Stride prediction</b> — when ticksToFull &lt;= 1 the aim-box is offset by the target's last-tick
 *       horizontal displacement so the server look is already on the lead when the swing commits.
 *   <li><b>Vanilla reach</b> — {@code player.getEntityInteractionRange() - 0.01} + 2.5 while riding.
 *       Trident/spear uses the trident attribute reach via tag check.
 *   <li><b>Grim lane</b> — swing C2S is enqueued only after interact C2S (vanilla doAttack order), the
 *       attack is skipped while {@code isUsingItem()}, and Rotation = Idle Only falls through to the real
 *       camera when movement is detected so Grim's Simulation sim never diverges from packet yaw.
 *   <li><b>Vulcan AimA / AimD / AimP</b> — humanize is a phase-coherent sin wobble plus a yaw-correlated
 *       pitch nudge; yaw delta per tick is capped so yawAccel stays under Vulcan's 20 deg threshold.
 * </ul>
 */
public class Pyre extends Feature {
    private final FloatSetting reachMargin = new FloatSetting("Reach Margin", 0.01f, 0f, 0.5f, 0.01f);
    private final BoolSetting sticky = new BoolSetting("Sticky Targeting", true);
    private final BoolSetting players = new BoolSetting("Players", true);
    private final BoolSetting hostiles = new BoolSetting("Hostiles", false);
    private final BoolSetting weaponOnly = new BoolSetting("Weapon Only", false);
    private final ModeSetting priority = new ModeSetting("Priority", "Distance", "Distance", "Health", "Angle");
    private final ModeSetting crits = new ModeSetting("Crits", "Wait", "Off", "Wait", "Jump");
    private final FloatSetting targetFov = new FloatSetting("Target FOV", 180f, 30f, 360f, 1f);
    private final ModeSetting onShielding = new ModeSetting("On Shielding", "Ignore", "Ignore", "Skip", "Prefer Axe");
    private final MultiModeSetting aimPoints = new MultiModeSetting("Aim Points", "Head", "Neck", "Chest", "Stomach", "Legs", "Feet");
    private final ModeSetting rotationMode = new ModeSetting("Rotation", "Idle Only", "Idle Only", "Always", "None");
    private final BoolSetting humanize = new BoolSetting("Humanize Pitch", true);
    private final FloatSetting humanizeAmp = new FloatSetting("Humanize Amp", 0.08f, 0.02f, 0.3f, 0.01f);
    private final FloatSetting yawCapPerTick = new FloatSetting("Yaw Cap", 3f, 1f, 20f, 0.5f);
    private final BoolSetting predictStride = new BoolSetting("Stride Predict", true);
    private final BoolSetting renderDot = new BoolSetting("Render Dot", false);
    private final FloatSetting dotSize = new FloatSetting("Dot Size", .1f, .02f, .5f, .01f);
    private final ColorSetting dotColor = new ColorSetting("Dot Color", 255, 64, 64, 255);

    private static final String[] REGIONS = {"Head", "Neck", "Chest", "Stomach", "Legs", "Feet"};
    private static final double[] REGION_Y = {0.93, 0.82, 0.68, 0.55, 0.30, 0.06};
    private static final int CRIT_HOLD_TICKS = 8;
    private static final int JUMP_LEAD_TICKS = 7;

    private LivingEntity target;
    private Vec3d aim;
    private float lastYaw, lastPitch;
    private double jitterPhase = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
    private int axeRestore = -1;
    private int critWait;
    private boolean wasSprinting;

    public Pyre() {
        super(new FeatureInfo("Kill Aura", "Attacks nearby targets without moving your camera.", Category.COMBAT));
        aimPoints.setOption("Head", true);
        aimPoints.setOption("Chest", true);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        if (mc.player != null) {
            lastYaw = mc.player.getYaw();
            lastPitch = mc.player.getPitch();
            wasSprinting = mc.player.isSprinting();
        }
        critWait = 0;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        restoreAxe();
        target = null;
        aim = null;
        critWait = 0;
    }

    private void restoreAxe() {
        if (axeRestore >= 0 && mc.player != null) mc.player.networkHandler.sendPacket(
                new net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket(mc.player.getInventory().getSelectedSlot()));
        axeRestore = -1;
    }

    @EventSubscribe
    @SuppressWarnings("unused")
    private void onInput(EventHandleInput.Early e) {
        restoreAxe();
        if (isNull() || mc.currentScreen != null || mc.player.isSpectator()) { reset(); return; }
        if (mc.player.isUsingItem()) { reset(); return; }                                                  // Grim BadPacketsJ
        if (mc.player.isSprinting() != wasSprinting) { wasSprinting = mc.player.isSprinting(); reset(); return; } // sprint-consistency

        wasSprinting = mc.player.isSprinting();

        var held = mc.player.getMainHandStack();
        if (weaponOnly.getValue() && !held.isIn(ItemTags.SWORDS) && !held.isIn(ItemTags.AXES) && !held.isOf(net.minecraft.item.Items.MACE)) { reset(); return; }

        Vec3d eye = mc.player.getEyePos();
        double reach = effectiveReach(held);

        // priority sticky: current target still valid? else re-pick.
        LivingEntity next = (sticky.getValue() && isWanted(target, eye, reach)) ? target : pickTarget(eye, reach);
        if (next == null) { critWait = 0; reset(); return; }
        target = next;

        // charge state
        float charge = mc.player.getAttackCooldownProgress(0.5f);
        boolean ready = charge >= 1f;
        boolean aiming = !ready && ticksToFull() <= 1;

        // Jump crits: inject one-tick jump when attack approaches full
        if (!ready
                && crits.is("Jump")
                && !mc.player.hasVehicle()
                && mc.player.isOnGround()
                && !mc.player.isTouchingWater()
                && ticksToFull() <= JUMP_LEAD_TICKS) {
            mc.options.jumpKey.setPressed(true);
        }

        // stride-predicted hitbox for the aim-ahead tick
        Box raw = target.getBoundingBox();
        Box box = (predictStride.getValue() && aiming) ? raw.offset(stride(target)) : raw;

        aim = aimPoint(box, eye);
        if (aim == null) return;

        // ---- rotation ----
        boolean idle = mc.player.input.getMovementInput().length() == 0f
                && mc.player.getVelocity().horizontalLengthSquared() < 0.0025d;
        boolean swap = rotationMode.is("Always") || (rotationMode.is("Idle Only") && idle);

        if (swap) {
            float[] goal = RotationUtils.calculate(eye, aim);
            float yaw = goal[0], pitch = goal[1];

            if (humanize.getValue()) {
                jitterPhase += 0.37d + ThreadLocalRandom.current().nextDouble(-0.07d, 0.07d);
                float amp = humanizeAmp.getValue();
                float yawDelta = MathHelper.wrapDegrees(yaw - lastYaw);
                float wobble = (float) (Math.sin(jitterPhase) * amp);                                   // AimP pitchAccel
                float correlated = Math.signum(yawDelta) * Math.min(Math.abs(yawDelta) * 0.015f, amp); // AimA / AimD
                pitch = MathHelper.clamp(pitch + wobble + correlated, -89.5f, 89.5f);
            }

            float cap = yawCapPerTick.getValue();
            float yawDeltaFinal = MathHelper.wrapDegrees(yaw - lastYaw);
            if (Math.abs(yawDeltaFinal) > cap) {
                yaw = MathHelper.wrapDegrees(lastYaw + Math.signum(yawDeltaFinal) * cap);
            }

            ServerRotations.request(this, yaw, pitch, .4f, 15);
            lastYaw = yaw;
            lastPitch = pitch;
        } else {
            lastYaw = mc.player.getYaw();
            lastPitch = mc.player.getPitch();
        }

        if (!ready) return;

        // ---- hit gate ----
        boolean shielding = target instanceof PlayerEntity p && p.isBlocking();
        if (shielding && onShielding.is("Skip")) return;

        if (!covers(eye, box, reach, swap)) return;

        // Crits Wait: hold until airborne, release on peak
        if (!crits.is("Off") && !mc.player.hasVehicle() && waitForCrit()) return;

        // shield-break hot-swap to axe for the one hit
        if (shielding && onShielding.is("Prefer Axe") && !held.isIn(ItemTags.AXES)) {
            int axe = -1;
            for (int i = 0; i < 9; i++) if (mc.player.getInventory().getStack(i).isIn(ItemTags.AXES)) { axe = i; break; }
            if (axe >= 0) {
                mc.player.networkHandler.sendPacket(new net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket(axe));
                axeRestore = axe;
            }
        }

        // vanilla MinecraftClient.doAttack order: interact first, then swing
        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);
        critWait = 0;
    }

    private void reset() {
        target = null;
        aim = null;
    }

    /** Vanilla full-charge: how many ticks until attackCooldownProgress reaches 1.0 (Anubis MeleeAttacks.ticksToFull). */
    private int ticksToFull() {
        float left = 1f - mc.player.getAttackCooldownProgress(0.5f);
        float cooldown = Math.max(0.0001f, mc.player.getAttackCooldownProgress(1f) - mc.player.getAttackCooldownProgress(0.5f)) * 2f;
        return left <= 0f ? 0 : (int) Math.ceil(left / Math.max(cooldown, 0.05f));
    }

    /** Crit Wait: airborne-and-falling locks hit on the peak-vs-landing frame (Anubis waitForCrit). */
    private boolean waitForCrit() {
        if (!mc.player.isOnGround()) {
            // airborne: wait up to CRIT_HOLD_TICKS unless we started falling (fallDistance > 0)
            if (mc.player.isSprinting() || (mc.player.fallDistance <= 0 && critWait++ < CRIT_HOLD_TICKS)) {
                return true;
            }
            return false;
        }
        return mc.player.isOnGround() && (crits.is("Jump") || mc.options.jumpKey.isPressed()) && critWait++ < CRIT_HOLD_TICKS;
    }

    /** Reach: vanilla entity-interaction range - margin, +2.5 while riding (Anubis). */
    private double effectiveReach(net.minecraft.item.ItemStack held) {
        double r = mc.player.getEntityInteractionRange() - reachMargin.getValue();
        if (mc.player.hasVehicle()) r += 2.5;
        return r;
    }

    /** Last-tick XZ displacement, zero if the entity teleported ( step &gt; 1 block ). */
    private static Vec3d stride(LivingEntity e) {
        double dx = e.getX() - e.lastX;
        double dz = e.getZ() - e.lastZ;
        return (dx * dx + dz * dz > 1.0) ? Vec3d.ZERO : new Vec3d(dx, 0, dz);
    }

    private boolean isWanted(LivingEntity l, Vec3d eye, double reach) {
        if (l == null || !l.isAlive() || l.isRemoved() || l == mc.player) return false;
        if (l instanceof PlayerEntity p) {
            if (!players.getValue()) return false;
            if (p.isSpectator() || p.isCreative()) return false;
        } else if (l instanceof HostileEntity) {
            if (!hostiles.getValue()) return false;
        } else {
            return false;
        }
        if (!TargetUtils.isTargetAllowed(l)) return false;
        if (eye.distanceTo(closest(eye, l.getBoundingBox())) > reach) return false;
        if (targetFov.getValue() < 360 && ServerRotations.yawOffset(l.getBoundingBox().getCenter()) > targetFov.getValue() / 2) return false;
        return mc.player.canSee(l);
    }

    private LivingEntity pickTarget(Vec3d eye, double reach) {
        LivingEntity best = null;
        double bestRank = 0d, bestDist = 0d;
        boolean bestHurt = false;
        for (Entity en : mc.world.getEntities()) {
            if (!(en instanceof LivingEntity l)) continue;
            if (!isWanted(l, eye, reach)) continue;
            double dist = eye.distanceTo(closest(eye, l.getBoundingBox()));
            double rank = rank(l, dist);
            boolean hurt = l.hurtTime > 0;
            if (best == null) { best = l; bestRank = rank; bestDist = dist; bestHurt = hurt; continue; }
            int order = Double.compare(rank, bestRank);
            if (order == 0) order = Boolean.compare(hurt, bestHurt);
            if (order == 0) order = -Double.compare(dist, bestDist); // closer wins
            if (order > 0) { best = l; bestRank = rank; bestDist = dist; bestHurt = hurt; }
        }
        return best;
    }

    private double rank(LivingEntity l, double dist) {
        if (priority.is("Health")) return -l.getHealth();     // lower health is better → invert
        if (priority.is("Angle")) return -ServerRotations.yawOffset(l.getBoundingBox().getCenter());
        return -dist;                                          // closer is better → invert
    }

    /** Three-tier aim: shrunken-box clamp → anatomy-region fallbacks → null if no LOS. */
    private Vec3d aimPoint(Box box, Vec3d eye) {
        double side = Math.min(0.15, box.getLengthX() * 0.3);
        double vert = Math.min(0.20, box.getLengthY() * 0.25);
        Box shrunk = box.contract(side, vert, side);
        Vec3d near = closest(eye, shrunk);
        if (lineClear(eye, near)) return near;
        // anatomy fallback (user-chosen regions in UI order, same as before)
        Vec3d bestFallback = null;
        double bestDistSq = Double.MAX_VALUE;
        double x = MathHelper.clamp(eye.x, box.minX, box.maxX), z = MathHelper.clamp(eye.z, box.minZ, box.maxZ);
        for (int i = 0; i < REGIONS.length; i++) {
            if (!aimPoints.isEnabled(REGIONS[i])) continue;
            Vec3d p = new Vec3d(x, box.minY + REGION_Y[i] * (box.maxY - box.minY), z);
            if (!lineClear(eye, p)) continue;
            double d = eye.squaredDistanceTo(p);
            if (d < bestDistSq) { bestDistSq = d; bestFallback = p; }
        }
        return bestFallback;
    }

    private boolean covers(Vec3d eye, Box box, double reach, boolean swap) {
        if (swap) {
            if (!ServerRotations.ownedBy(this)) return false;
            return ServerRotations.sentHits(box);
        }
        // real-camera path: raycast from camera forward and check hitbox cover + reach
        Vec3d look = mc.player.getRotationVector();
        var hit = box.expand(1.0e-3).raycast(eye, eye.add(look.multiply(reach + 1)));
        return hit.isPresent() && hit.get().squaredDistanceTo(eye) <= reach * reach;
    }

    private boolean lineClear(Vec3d from, Vec3d to) {
        var ctx = new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player);
        var hit = mc.world.raycast(ctx);
        return hit.getType() != net.minecraft.util.hit.HitResult.Type.BLOCK
                || hit.getPos().squaredDistanceTo(from) >= from.squaredDistanceTo(to) - 1.0e-3;
    }

    private static Vec3d closest(Vec3d p, Box b) {
        return new Vec3d(MathHelper.clamp(p.x, b.minX, b.maxX), MathHelper.clamp(p.y, b.minY, b.maxY), MathHelper.clamp(p.z, b.minZ, b.maxZ));
    }

    @EventSubscribe
    @SuppressWarnings("unused")
    private void onRender(EventRender3D e) {
        if (!renderDot.getValue() || aim == null || isNull()) return;
        double s = dotSize.getValue();
        e.getDraw3D().getCustomTarget().box(aim.x - s, aim.y - s, aim.z - s, s * 2, s * 2, s * 2, dotColor.getARGB());
    }
}
