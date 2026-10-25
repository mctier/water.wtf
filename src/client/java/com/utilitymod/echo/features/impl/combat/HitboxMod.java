package com.utilitymod.echo.features.impl.combat;

import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventHandleInput;
import com.utilitymod.echo.event.impl.EventStartAttack;
import com.utilitymod.echo.features.impl.movement.Freecam;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.settings.impl.BoolSetting;
import com.utilitymod.echo.features.settings.impl.FloatSetting;
import com.utilitymod.echo.handlers.InputHandler;
import com.utilitymod.echo.handlers.RotationHandler;
import com.utilitymod.echo.utils.combat.TargetUtils;
import com.utilitymod.echo.utils.rotation.RotationUtils;
import com.utilitymod.echo.utils.strings.Concat;
import com.utilitymod.echo.utils.EncryptedString;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class HitboxMod extends Feature {

	public HitboxMod() {
		super(new FeatureInfo(
			new com.utilitymod.echo.utils.EncryptedString(new char[]{0x226c,0xd132,0xb177,0x0b39,0xbc0b,0x37d1,0xa8a2,0x82de}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad}),
			new com.utilitymod.echo.utils.EncryptedString(new char[]{0x37d8,0xbc00,0xfce8,0xd93c,0xc5d2,0x962b,0x82de,0x76ac,0x11f9,0x1cd2,0x6554,0xd137,0xeb95,0x0b39,0x50dd,0x4a19,0xf005,0xb166,0x227c,0xa8a4,0x5f72}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87,0x76d4,0xf025,0x4a6a,0xc5bb,0x5f08,0xd959,0x50ae}),
			Category.COMBAT,
			false
		));
	}
    public BoolSetting uniformExpand = new BoolSetting(new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0b31,0xd12f,0x37d1,0xb17d,0xbc0f,0x2271,0xa8a5}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c}), true);

    public final FloatSetting expandX = new FloatSetting(new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2268,0xd13f,0xb16b,0x0b7c,0xbc08,0x37d0,0xa8b3,0x82f5}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad}), 0.0f, 0.0f, 2.0f, 0.1f, p -> !uniformExpand.getValue());
    public final FloatSetting expandY = new FloatSetting(new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2268,0xd13f,0xb16b,0x0b7c,0xbc08,0x37d0,0xa8b3,0x82f4}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad}), 0.0f, 0.0f, 2.0f, 0.1f, p -> !uniformExpand.getValue());
    public final FloatSetting ExpandZ = new FloatSetting(new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2268,0xd13f,0xb16b,0x0b7c,0xbc08,0x37d0,0xa8b3,0x82f7}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad}), 0.0f, 0.0f, 2.0f,0.1f, p -> !uniformExpand.getValue());

    public final FloatSetting uniformExpandSize = new FloatSetting(new com.utilitymod.echo.utils.EncryptedString(new char[]{0x11f5,0xa8b3,0xbc08,0x2268,0x1c9b,0x82fe,0x0b7c,0xd13f,0xb16b,0x9639,0x37d0}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190}), 0.0f, 0.0f, 2.0f, 0.1f, p -> uniformExpand.getValue());
    public BoolSetting staticHitbox = new BoolSetting(new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2279,0x9637,0x11f2,0xeb8e,0x82e5,0x0b7c,0xa8bf,0xbc1d,0x1c9b,0xd129,0x37d7,0x654e,0xb16c}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536}), false);
    public BoolSetting playersOnly = new BoolSetting(new com.utilitymod.echo.utils.EncryptedString(new char[]{0xeb98,0x0b2f,0x1cbd,0x962d,0x2279,0xd12a,0x11fc,0x828d,0xa8a7,0x37db,0xb17d,0xbc10}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1}), true);

    public final FloatSetting distanceSetting = new FloatSetting(new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2276,0xd128,0x37db,0xa8aa,0xbc0e}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be}), 6.0f, 1, 20, 0.2f);

    /**
     * Grim bypass: an attack whose transmitted look ray misses the server's (vanilla) hitbox is
     * exactly what Grim's Reach/Hitboxes check flags. With this on, a click picked through an
     * expanded box is held back, the wire look silently converges onto the vanilla box, and the
     * attack is released the moment its look genuinely intersects the real hitbox - so every
     * packet Grim sees is geometrically legal no matter how large the expansion is.
     */
    public final BoolSetting grimBypass = new BoolSetting(
        new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd138,0x82ea,0x37cd,0xa8b2,0x2268,0xb17c,0xbc08,0x0b7c,0x1c80,0x962a,0xd117}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643}), true);
    /** Silent look sync speed while a held click converges onto the vanilla box. */
    final com.utilitymod.echo.features.settings.impl.IntSetting lookSyncSpeed = new com.utilitymod.echo.features.settings.impl.IntSetting(
        new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1c91,0xa8a4,0x82c3,0x0b25,0x379e,0xbc02,0xd136,0xb15c,0x2277}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643}), 45, 10, 200);

    /**
     * RandomCenter (Tatako / Grizzly / MX): statistical and ML analyzers flag identical,
     * perfectly-repeated aim solutions. With this on, each Grim-converged attack aims at a
     * slightly randomized point inside the vanilla hitbox instead of the same closest point
     * every time, which reads as human inaccuracy while staying inside Grim's reach limit.
     */
    public final BoolSetting randomAim = new BoolSetting(
        new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa8aa,0x962e,0x82ec,0x37d1,0x0b7c,0x1c9b,0x2276,0xb162,0xd128,0xbc0d}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643}), true);

    /** Tiny slop so grazing rays count as hits; Grim itself expands by 0.0005. */
    private static final double GRIM_BOX_EPS = 1.0e-3;

    private Entity pendingTarget;
    private Vec3d pendingAimPoint;
    private long pendingSince;
    private boolean cleaningUp;
    /** Set once the wire look converged; the attack is released the NEXT input pass, so the
     *  position packet carrying the converged rotation is already sent before the attack. */
    private boolean readyToRelease;

    @Override
    public void onDisable() {
        super.onDisable();
        dropPending();
    }

    private void dropPending() {
        pendingTarget = null;
        pendingAimPoint = null;
        pendingSince = 0L;
        cleaningUp = true;
        readyToRelease = false;
    }

    /** The look Grim will actually evaluate: the silent rotation while one is active, else the camera look. */
    private static Vec3d wireLook() {
        if (RotationHandler.isHasSilentRotation()) {
            return RotationHandler.getRotationVector(RotationHandler.getServerPitch(), RotationHandler.getServerYaw());
        }
        if (mc.player == null) return Vec3d.ZERO;
        return mc.player.getRotationVector();
    }

    private static boolean rayHitsBox(Vec3d eye, Vec3d look, Box box) {
        if (eye == null || look.lengthSquared() < 1.0e-8) return false;
        double len = Math.max(mc.player.getEntityInteractionRange(), 3.0f) + 4.0;
        java.util.Optional<Vec3d> hit = box.expand(GRIM_BOX_EPS).raycast(eye, eye.add(look.multiply(len)));
        if (hit.isEmpty()) return false;
        // Grim's Reach also measures eye -> intersection distance against the interaction range
        // attribute (treated as 3.0 by most configs). A grazing ray at 3.4 blocks out is a REACH
        // flag even though it "touches" the box - require the intersection inside Grim's reach.
        double maxDist = mc.player.getEntityInteractionRange() + GRIM_BOX_EPS;
        return hit.get().squaredDistanceTo(eye) <= maxDist * maxDist;
    }

    @SuppressWarnings("unused")
    @EventSubscribe
    private void onStartAttack(EventStartAttack event) {
        if (Shrike.owns()) return;
        if (!grimBypass.getValue() || Freecam.isActive()) return;
        if (isNull() || com.utilitymod.echo.api.MinecraftCompat.getScreen() != null) return;
        Entity target = event.getTarget();
        if (!(target instanceof LivingEntity living)) return;
        if (living instanceof net.minecraft.entity.player.PlayerEntity p && !TargetUtils.isTargetAllowed(p)) return;
        if (RangedCheck.distanceExpired(living, distanceSetting.getValue() + 2.0f)) return;

        Vec3d eye = mc.player.getEyePos();
        Box vanilla = target.getBoundingBox();
        if (rayHitsBox(eye, wireLook(), vanilla)) {
            // Wire look already intersects the real hitbox - this attack is Grim-legal as is.
            if (pendingTarget == target) dropPending();
            return;
        }

        // This pick came purely from the expanded box: hold the attack and pre-aim the wire look.
        pendingTarget = target;
        pendingSince = System.currentTimeMillis();
        pendingAimPoint = null;
        cleaningUp = false;
        readyToRelease = false;
        event.cancel();
    }

    @SuppressWarnings("unused")
    @EventSubscribe(priority = EventSubscribe.Priority.HIGHEST)
    private void onHandleInput(EventHandleInput.Early event) {
        if (Shrike.owns()) return;
        if (!grimBypass.getValue() || Freecam.isActive()) {
            if (pendingTarget != null) dropPending();
        }
        if (cleaningUp) {
            cleaningUp = RotationUtils.cleanup(this, true) || false;
            if (!cleaningUp) return;
        }
        if (pendingTarget == null) return;

        // Release only on a LATER input pass than the one that converged the rotation: this
        // pass's handleInputEvents runs before sendPosition, so an attack fired the same pass
        // would ride the PREVIOUS look packet and Grim would evaluate the stale ray.
        if (readyToRelease) {
            readyToRelease = false;
            LivingEntity releaseTarget = pendingTarget instanceof LivingEntity rt ? rt : null;
            dropPending();
            cleaningUp = true;
            if (releaseTarget != null
                    && releaseTarget.isAlive()
                    && !releaseTarget.isRemoved()
                    && mc.crosshairTarget instanceof net.minecraft.util.hit.EntityHitResult ehr
                    && ehr.getEntity() == releaseTarget) {
                directAttack(releaseTarget);
            }
            return;
        }

        LivingEntity target = pendingTarget instanceof LivingEntity living ? living : null;
        if (target == null || target.isDead() || !target.isAlive()
                || System.currentTimeMillis() - pendingSince > 1500L) {
            dropPending();
            cleaningUp = true;
            return;
        }

        Vec3d eye = mc.player.getEyePos();
        Box vanilla = target.getBoundingBox();

        // Converge the wire look onto the closest vanilla-box point to the camera ray (GCD-safe,
        // speed-limited in RotationUtils). Silent rotations ride the next position packet.
        Vec3d camLook = mc.player.getRotationVector();
        pendingAimPoint = RotationUtils.closestPointToBoxFromRay(vanilla, eye, camLook);
        if (randomAim.getValue()) {
            // RandomCenter: vary the convergence point across attack attempts so ML/statistical
            // analyzers do not see an identical boxed pass-through every time. The blend keeps
            // the eye-to-intersection distance close to the box minimum for Grim's reach check.
            Vec3d randomPoint = RotationUtils.randomPointInBox(vanilla);
            pendingAimPoint = pendingAimPoint.lerp(randomPoint, 0.25);
            pendingAimPoint = new Vec3d(
                MathHelper.clamp(pendingAimPoint.x, vanilla.minX, vanilla.maxX),
                MathHelper.clamp(pendingAimPoint.y, vanilla.minY, vanilla.maxY),
                MathHelper.clamp(pendingAimPoint.z, vanilla.minZ, vanilla.maxZ));
        }
        boolean aimed = RotationUtils.aim(this)
            .priority(EventSubscribe.Priority.HIGH)
            .silent()
            .speed(lookSyncSpeed.getValue())
            .aimType(RotationUtils.AimType.REGULAR)
            .to(pendingAimPoint);

        if (aimed && rayHitsBox(eye, wireLook(), vanilla)) {
            // Wire look intersects the vanilla box and is inside reach -> release the held
            // attack on the NEXT input pass (see release comment above).
            readyToRelease = true;
        }
    }

    /**
     * Reliable attack path for post-injection clients: click-count attacks do not always reach
     * doAttack (see InputHandler.directAttack), so replay vanilla's entity-click branch directly.
     */
    private static void directAttack(Entity target) {
        try {
            mc.crosshairTarget = new net.minecraft.util.hit.EntityHitResult(target);
            mc.targetedEntity = target;
            com.utilitymod.echo.api.Accessors.invokeStartAttack();
        } catch (Throwable t) {
            if (mc.interactionManager != null) {
                mc.interactionManager.attackEntity(mc.player, target);
                mc.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
            }
        }
    }

    /**
     * Cheap radius screen used by the gate: keeps the pending-slot from pre-aiming at entities
     * that are nowhere near the fight.
     */
    static final class RangedCheck {
        static boolean distanceExpired(Entity entity, float max) {
            return mc.player == null || mc.player.squaredDistanceTo(entity) > max * max;
        }
    }
}
