package com.utilitymod.echo.utils.player;

import com.utilitymod.echo.Echo;
import com.utilitymod.echo.utils.Imports;
import com.utilitymod.echo.handlers.RotationHandler;
import com.utilitymod.echo.utils.world.WorldUtils;
import net.minecraft.util.Hand;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.Items;
import net.minecraft.util.math.Box;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;

public class PlayerUtils implements Imports {

    public static Entity getClosestEnemy(float range) {
        return mc.world.getPlayers()
                .stream()
                .filter(player -> player != mc.player && mc.player.distanceTo(player) <= range && !player.isDead() && player.isAlive())
                .min(Comparator.comparingDouble(mc.player::distanceTo))
                .orElse(null);
    }

    public static boolean isPlayerShieldingInOffHand() {
        if (mc.player == null) return false;
        return mc.player.isUsingItem()
                && mc.player.getOffHandStack().getItem() == Items.SHIELD
                && mc.player.getActiveHand() == Hand.OFF_HAND;
    }

    public static boolean isPlayerShieldingInMainHand() {
        if (mc.player == null) return false;
        return mc.player.isUsingItem()
                && mc.player.getMainHandStack().getItem() == Items.SHIELD
                && mc.player.getActiveHand() == Hand.MAIN_HAND;
    }

    public static boolean isMoving() {
        if (mc.player == null) return false;
        return mc.player.input.getMovementInput().length() != 0f;
    }

    // Returns what the server will see as fallDistance for this player this tick.
    // The server caps fallDistance at 1.0 when vertical speed > -0.5 (slow fall) and resets to 0 on upward motion.
    public static double getServerSyncedFallDistance(PlayerEntity player) {
        if (player == null) return 0.0;
        double motionY = player.getVelocity().getY();
        double fd = player.fallDistance;
        if (motionY > 0.0) return 0.0;
        if (motionY > -0.5 && fd > 1.0) return 1.0;
        return fd;
    }

    public static double predictNextTickFallDistanceIfStopGliding() {
        if (mc.player == null) return 0.0;
        return predictNextTickFallDistanceIfStopGliding(mc.player);
    }

    public static void setMotionX(double motionX) {
        mc.player.setVelocity(motionX, mc.player.getVelocity().y, mc.player.getVelocity().z);
    }

    public static void setMotionY(double motionY) {
        mc.player.setVelocity(mc.player.getVelocity().x, motionY, mc.player.getVelocity().z);
    }

    public static void setMotionZ(double motionZ) {
        mc.player.setVelocity(mc.player.getVelocity().x, mc.player.getVelocity().y, motionZ);
    }

    public static double predictNextTickFallDistanceIfStopGliding(PlayerEntity player) {
        if (player == null) return 0.0;

        double lastY = player.lastY;
        double currentY = player.getY();
        double velocityY = player.getVelocity().y;
        double currentFall = player.fallDistance;

        final double GRAVITY = 0.08;
        final double TERMINAL_VELOCITY = -3.92;
        final double STANDING_HITBOX_HEIGHT = 1.8;
        final double GLIDING_HITBOX_HEIGHT = 0.6;
        final double HITBOX_HEIGHT_DELTA = STANDING_HITBOX_HEIGHT - GLIDING_HITBOX_HEIGHT;

        double nextVelocityY = velocityY + GRAVITY;
        if (nextVelocityY < TERMINAL_VELOCITY) nextVelocityY = TERMINAL_VELOCITY;

        double predictedNextY = currentY + nextVelocityY;
        if (player.isGliding()) {
            predictedNextY += HITBOX_HEIGHT_DELTA;
        }
        double predictedIncrement = lastY - predictedNextY;

        double nextFall = currentFall + predictedIncrement;
        if (nextFall < 0) nextFall = 0.0;
        return nextFall;
    }

    public static LivingEntity getManualSelectTarget() {
        if (mc.player == null || mc.world == null) return null;

        float partialTick = mc.getRenderTickCounter().getTickProgress(false);
        float yaw = mc.player.getYaw(partialTick);
        float pitch = mc.player.getPitch(partialTick);
        Vec3d eyePos = mc.player.getCameraPosVec(partialTick);
        Vec3d lookDir = RotationHandler.getRotationVector(pitch, yaw);
        double range = mc.player.getEntityInteractionRange();
        Vec3d endPos = eyePos.add(lookDir.multiply(range));

        Box searchBox = mc.player.getBoundingBox().stretch(lookDir.multiply(range)).expand(1.0);
        EntityHitResult entityHit = ProjectileUtil.raycast(
                mc.player, eyePos, endPos, searchBox,
                entity -> !entity.isSpectator() && entity.canHit(),
                range * range);

        if (entityHit == null) return null;
        if (!(entityHit.getEntity() instanceof LivingEntity living)) return null;
        return living;
    }

    public static Boolean isCollidingWithHitbox(LivingEntity player, LivingEntity entity) {
        if (player == null || entity == null) return false;
        return player.getBoundingBox().intersects(entity.getBoundingBox());
    }

    public static Boolean isCollidingWithHitbox(LivingEntity player) {
        if (player == null) return false;
        if (mc.world == null) return false;
        
        return mc.world.getOtherEntities(player, player.getBoundingBox())
                .stream()
                .anyMatch(entity -> entity instanceof LivingEntity && entity != player);
    }

    public static boolean isFriend(String name) {
        if (Echo.friendManager == null) {
            return false;
        }

        return Echo.friendManager.isFriend(name);
    }
}
