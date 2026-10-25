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
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Kill Aura on the shared server-side {@link ServerRotations} (RotationManager model): the look the
 * server receives tracks the chosen anatomy point of the target; the hit fires only when the already-sent
 * look lands inside the hitbox at full (vanilla) charge. Humanized Aim and the per-tick yaw cap are
 * enabled on-model so Vulcan Aim(Rotation) and Grim BadPacketsJ / Simulation stay quiet.
 *
 * <p><b>Grim lane.</b>
 * <ul>
 *   <li><i>BadPacketsJ</i> (Grim): interact-entity sent while {@code isUsingItem()} (shield up / eat /
 *       draw bow / trident charge). Guarded: no attack is dispatched while the client is using an item.
 *   <li><i>BadPacketsJ</i> (Grim): swing arm must precede interact in the same tick. Order below mirrors
 *       vanilla {@code ClientPlayerEntity.attack}: swing C2S first, interact C2S second.
 *   <li><i>Simulation</i> (Grim): rotation must be already carried by a prior movement packet before
 *       the interact lands. Guarded via {@link ServerRotations#ownedBy}/{@link ServerRotations#sentHits}.
 * </ul>
 *
 * <p><b>Vulcan lane (reference: OpenVulcan combat/aim/rotation).</b>
 * <ul>
 *   <li><i>AimA</i>: pitchDelta ≈ 0 while yawDelta &gt; 0.5 within 3 attack ticks. Mitigated by phase-coherent
 *       sin-wave pitch wobble so pitchDelta is non-zero every tick the look updates.
 *   <li><i>AimD</i>: yawDelta &gt; 2.75 with pitchDelta == 0. Mitigated by yaw-correlated pitch nudge:
 *       {@code pitch += sign(yawDelta) * min(|yawDelta|*0.015, amp)}, so yaw motion always drags pitch.
 *   <li><i>AimP</i>: yawAccel &gt; 20 with pitchAccel &lt; 0.05. Mitigated by {@link #yawCapPerTick} clamping
 *       the yaw delta before it reaches {@link ServerRotations}, keeping accel under the window threshold.
 * </ul>
 */
public class KillAura extends Feature {
    private final FloatSetting reach = new FloatSetting("Reach", 2.95f, 1f, 3f, .05f);
    private final BoolSetting sticky = new BoolSetting("Sticky Targeting", true);
    private final BoolSetting playerOnly = new BoolSetting("Player Only", true);
    private final BoolSetting weaponOnly = new BoolSetting("Weapon Only", false);
    private final FloatSetting maxYaw = new FloatSetting("Max Yaw", 180f, 0f, 180f, 1f);
    private final FloatSetting targetFov = new FloatSetting("Target FOV", 180f, 30f, 360f, 1f);
    private final ModeSetting onShielding = new ModeSetting("On Shielding", "Ignore", "Ignore", "Skip", "Prefer Axe");
    private final MultiModeSetting aimPoints = new MultiModeSetting("Aim Points", "Head", "Neck", "Chest", "Stomach", "Legs", "Feet");
    private final BoolSetting humanize = new BoolSetting("Humanize Pitch", true);
    private final FloatSetting humanizeAmp = new FloatSetting("Humanize Amp", 0.08f, 0.02f, 0.3f, 0.01f);
    private final FloatSetting yawCapPerTick = new FloatSetting("Yaw Cap", 18f, 2f, 60f, 1f);
    private final BoolSetting renderDot = new BoolSetting("Render Dot", false);
    private final FloatSetting dotSize = new FloatSetting("Dot Size", .1f, .02f, .5f, .01f);
    private final ColorSetting dotColor = new ColorSetting("Dot Color", 255, 64, 64, 255);

    private static final String[] REGIONS = {"Head", "Neck", "Chest", "Stomach", "Legs", "Feet"};
    private static final double[] REGION_Y = {0.93, 0.82, 0.68, 0.55, 0.30, 0.06};

    private LivingEntity target;
    private Vec3d aim;
    private float lastYaw, lastPitch;
    private double jitterPhase = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
    private int axeRestore = -1;

    public KillAura() {
        super(new FeatureInfo("Kill Aura", "Attacks targets in reach with server-side rotations.", Category.COMBAT));
        aimPoints.setOption("Head", true);
        aimPoints.setOption("Chest", true);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        if (mc.player != null) {
            lastYaw = mc.player.getYaw();
            lastPitch = mc.player.getPitch();
        }
    }

    @Override
    public void onDisable() {
        super.onDisable();
        restoreAxe();
        target = null;
        aim = null;
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
        if (isNull() || mc.currentScreen != null || mc.player.isSpectator()) { target = null; aim = null; return; }

        // Grim BadPacketsJ: never dispatch interact while the client is using an item.
        if (mc.player.isUsingItem()) { target = null; aim = null; return; }

        var held = mc.player.getMainHandStack();
        if (weaponOnly.getValue() && !held.isIn(ItemTags.SWORDS) && !held.isIn(ItemTags.AXES)) { target = null; aim = null; return; }

        boolean keep = sticky.getValue() && inReach(target) && yawOk(target);
        if (!keep) target = acquire();
        if (target == null) { aim = null; return; }

        aim = aimPoint(target);

        // Vulcan AimA / AimD / AimP mitigation lives in the rotation computed before the request.
        float[] goal = RotationUtils.calculate(mc.player.getEyePos(), aim);
        float yaw = goal[0], pitch = goal[1];

        if (humanize.getValue()) {
            jitterPhase += 0.37d + ThreadLocalRandom.current().nextDouble(-0.07d, 0.07d);
            float amp = humanizeAmp.getValue();
            float yawDelta = MathHelper.wrapDegrees(yaw - lastYaw);
            // sin-wave wobble keeps pitchAccel > 0.05 (AimP)
            float wobble = (float) (Math.sin(jitterPhase) * amp);
            // yaw-correlated nudge keeps pitchDelta != 0 whenever yawDelta != 0 (AimA / AimD)
            float correlated = Math.signum(yawDelta) * Math.min(Math.abs(yawDelta) * 0.015f, amp);
            pitch = MathHelper.clamp(pitch + wobble + correlated, -89.5f, 89.5f);
        }

        // Yaw per-tick cap, so AimP's yawAccel > 20 window never trips.
        float cap = yawCapPerTick.getValue();
        float yawDeltaFinal = MathHelper.wrapDegrees(yaw - lastYaw);
        if (Math.abs(yawDeltaFinal) > cap) {
            yaw = MathHelper.wrapDegrees(lastYaw + Math.signum(yawDeltaFinal) * cap);
        }

        ServerRotations.request(this, yaw, pitch, .65f, 15);
        lastYaw = yaw;
        lastPitch = pitch;

        boolean shielding = target instanceof PlayerEntity p && p.isBlocking();
        if (shielding && onShielding.is("Skip")) return;
        if (!ServerRotations.ownedBy(this) || !ServerRotations.sentHits(target.getBoundingBox())) return;
        if (mc.player.getAttackCooldownProgress(0.5f) < 1f) return;   // vanilla full charge

        if (shielding && onShielding.is("Prefer Axe") && !held.isIn(ItemTags.AXES)) {
            int axe = -1;
            for (int i = 0; i < 9; i++) if (mc.player.getInventory().getStack(i).isIn(ItemTags.AXES)) { axe = i; break; }
            if (axe >= 0) {
                // silent swap for the shield-disabling hit; restored on the next input pass
                mc.player.networkHandler.sendPacket(new net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket(axe));
                axeRestore = axe;
            }
        }

        // Vanilla ClientPlayerEntity.attack order: SwingC2S first, then PlayerInteractEntity(Attack).
        // Grim BadPacketsJ reads them in a window; interact-before-swing fails the check.
        mc.player.swingHand(Hand.MAIN_HAND);
        mc.interactionManager.attackEntity(mc.player, target);
    }

    /** Eye-to-hitbox distance within Reach. */
    private boolean inReach(LivingEntity e) {
        if (e == null || !e.isAlive() || e.isRemoved()) return false;
        Vec3d eye = mc.player.getEyePos();
        Box b = e.getBoundingBox();
        Vec3d c = new Vec3d(MathHelper.clamp(eye.x, b.minX, b.maxX), MathHelper.clamp(eye.y, b.minY, b.maxY), MathHelper.clamp(eye.z, b.minZ, b.maxZ));
        return eye.distanceTo(c) <= reach.getValue();
    }

    private boolean yawOk(LivingEntity e) {
        return e != null && ServerRotations.yawOffset(e.getBoundingBox().getCenter()) <= maxYaw.getValue();
    }

    private LivingEntity acquire() {
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        for (Entity en : mc.world.getEntities()) {
            if (!(en instanceof LivingEntity l) || l == mc.player || !l.isAlive()) continue;
            if (playerOnly.getValue() && !(l instanceof PlayerEntity)) continue;
            if (l instanceof PlayerEntity p && p.isSpectator()) continue;
            if (!TargetUtils.isTargetAllowed(l) || !inReach(l) || !yawOk(l)) continue;
            if (targetFov.getValue() < 360 && ServerRotations.yawOffset(l.getBoundingBox().getCenter()) > targetFov.getValue() / 2) continue;
            if (!mc.player.canSee(l)) continue;
            double d = mc.player.squaredDistanceTo(l);
            if (d < bd) { bd = d; best = l; }
        }
        return best;
    }

    /** Enabled anatomy region whose clamped point is nearest your eye (X/Z clamped to the box, Y in that band). */
    private Vec3d aimPoint(LivingEntity e) {
        Vec3d eye = mc.player.getEyePos();
        Box b = e.getBoundingBox();
        double x = MathHelper.clamp(eye.x, b.minX, b.maxX), z = MathHelper.clamp(eye.z, b.minZ, b.maxZ);
        Vec3d best = null;
        double bd = Double.MAX_VALUE;
        for (int i = 0; i < REGIONS.length; i++) {
            if (!aimPoints.isEnabled(REGIONS[i])) continue;
            Vec3d p = new Vec3d(x, b.minY + REGION_Y[i] * (b.maxY - b.minY), z);
            double d = eye.squaredDistanceTo(p);
            if (d < bd) { bd = d; best = p; }
        }
        return best != null ? best : b.getCenter();
    }

    @EventSubscribe
    @SuppressWarnings("unused")
    private void onRender(EventRender3D e) {
        if (!renderDot.getValue() || aim == null || isNull()) return;
        double s = dotSize.getValue();
        e.getDraw3D().getCustomTarget().box(aim.x - s, aim.y - s, aim.z - s, s * 2, s * 2, s * 2, dotColor.getARGB());
    }
}
