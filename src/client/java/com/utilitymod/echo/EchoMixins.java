package com.utilitymod.echo;

import com.utilitymod.echo.features.impl.combat.HitboxMod;
import com.utilitymod.echo.features.impl.movement.KeepSprintModule;
import com.utilitymod.echo.features.impl.movement.Sprint;
import com.utilitymod.echo.features.impl.render.SwingPace;
import com.utilitymod.echo.handlers.RotationHandler;
import com.utilitymod.echo.utils.combat.TargetUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Bodies of echo's entity/input mixins that drive ported modules (Hitboxes,
 * Swing Speed, Sprint omni-sprint, KeepSprint). Each method names the echo mixin it reproduces; Hooks call these
 * from the patched bytecode. Vanilla behaviour is returned whenever the
 * feature is off, exactly like the mixins.
 */
public final class EchoMixins {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final double PLAYER_STANDING_HITBOX_HEIGHT = 1.8;

    private EchoMixins() {}

    private static <T> T feature(Class<T> type) {
        return Echo.featureManager.getFeatureByClass(type);
    }

    private static Vec3d serverLook() {
        return RotationHandler.getRotationVector(RotationHandler.getServerPitch(), RotationHandler.getServerYaw());
    }

    // ---- MixinProjectileUtil: Entity.getBoundingBox redirect in getEntityHitResult ----

    /** Returns the box echo would use, or null for vanilla. */
    public static Box hitbox(Entity entity) {
        HitboxMod hitboxMod = feature(HitboxMod.class);
        if (hitboxMod == null || !hitboxMod.isEnabled()) return null;
        Box vanilla = entity.getBoundingBox().expand(entity.getTargetingMargin());
        if (entity instanceof PlayerEntity player && !TargetUtils.isTargetAllowed(player)) return vanilla;
        if (hitboxMod.playersOnly.getValue() && !(entity instanceof PlayerEntity)) return vanilla;
        if (mc.player != null && mc.player.squaredDistanceTo(entity) > Math.pow(hitboxMod.distanceSetting.getValue(), 2)) {
            return vanilla;
        }
        Box base = entity.getBoundingBox();
        if (hitboxMod.staticHitbox.getValue() && entity instanceof PlayerEntity p && p.isGliding()) {
            base = new Box(base.minX, base.minY, base.minZ, base.maxX, base.minY + PLAYER_STANDING_HITBOX_HEIGHT, base.maxZ);
        }
        Box original = base.expand(entity.getTargetingMargin());
        if (hitboxMod.uniformExpand.getValue()) {
            float s = hitboxMod.uniformExpandSize.getValue();
            return original.expand(s, s, s);
        }
        return original.expand(hitboxMod.expandX.getValue(), hitboxMod.expandY.getValue(), hitboxMod.ExpandZ.getValue());
    }

    // ---- MixinLivingEntity: getCurrentSwingDuration ----

    public static int swingDuration(int vanilla) {
        SwingPace module = feature(SwingPace.class);
        return module != null && module.isEnabled() ? SwingPace.swingTicks.getValue() : vanilla;
    }

    // ---- MixinClientInput: hasForwardImpulse HEAD (omni sprint) ----

    /** Non-null = override. */
    public static Boolean forwardImpulse(Object input) {
        Sprint sprint = feature(Sprint.class);
        if (sprint == null || !sprint.isOmniSprintEnabled()) return null;
        return ((Input) input).getMovementInput().lengthSquared() > 1.0E-5F;
    }

    // ---- MixinEntity ----

    /** getViewVector / getHeadLookAngle HEAD: silent rotation look for the local player; null = vanilla. */
    public static Vec3d silentLook(Object entity) {
        if (entity != mc.player || !RotationHandler.isHasSilentRotation()) return null;
        return serverLook();
    }

    /** moveRelative: Entity.getYRot redirect. */
    public static float moveYaw(Object entity) {
        Entity e = (Entity) entity;
        return e.getYaw();
    }

    // ---- MixinPlayer ----

    /** travel: Player.getLookAngle redirect (swimming). */
    public static Vec3d swimLook(Object player) {
        Entity e = (Entity) player;
        return e.getRotationVector();
    }

    /** causeExtraKnockback: Player.getYRot redirect. */
    public static float attackYaw(Object player) {
        Entity e = (Entity) player;
        if (e == mc.player && RotationHandler.isHasSilentRotation()) return RotationHandler.getServerYaw();
        return e.getYaw();
    }

    /** causeExtraKnockback: Player.setSprinting redirect (KeepSprint). */
    public static void attackSetSprinting(Object player, boolean sprinting) {
        KeepSprintModule keepSprint = feature(KeepSprintModule.class);
        if (keepSprint != null && keepSprint.isEnabled() && keepSprint.dontModifySprint()) return;
        ((Entity) player).setSprinting(sprinting);
    }

    // ---- MixinFireworkRocketEntity / MixinLivingEntity elytra ----

    /** LivingEntity.getLookAngle redirect (firework boost, elytra flight). */
    public static Vec3d livingLook(Object living) {
        Entity e = (Entity) living;
        return e.getRotationVector();
    }

    /** updateFallFlyingMovement: getXRot redirect. */
    public static float elytraPitch(Object living) {
        Entity e = (Entity) living;
        return e.getPitch();
    }

    /** jumpFromGround: addDeltaMovement arg (sprint-jump boost along the server yaw). */
    public static void jumpBoost(Object living, Object vec) {
        LivingEntity self = (LivingEntity) living;
        Vec3d original = (Vec3d) vec;
        self.addVelocityInternal(original);
    }
}
