package com.utilitymod.echo.utils.world;


import com.utilitymod.echo.api.SlimeCompat;
import com.utilitymod.echo.utils.ChatUtils;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.ZombifiedPiglinEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.item.consume.UseAction;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import static com.utilitymod.echo.utils.Imports.mc;

public class WorldUtils {

    /**
     * Checks if the target is using an item with BLOCK action (attempting to block).
     * This is faster than isBlocking() as it doesn't wait for the 5-tick delay.
     */
    public static boolean isAttemptingToBlock(PlayerEntity target) {
        if (target == null || !target.isUsingItem()) {
            return false;
        }
        ItemStack activeStack = target.getActiveItem();
        if (activeStack.isEmpty()) {
            return false;
        }
        Item item = activeStack.getItem();
        return item.getUseAction(activeStack) == UseAction.BLOCK;
    }

    public static boolean isShieldFacingAway(PlayerEntity target) {
        if (mc.player == null || target == null) return false;

        // Use the same facing check as LivingEntity#getDamageBlockedAmount.
        // product is negative the player is behind the target (shield facing away).
        Vec3d playerPos = mc.player.getEyePos();
        Vec3d targetPos = target.getEntityPos();

        Vec3d toPlayer = playerPos.subtract(targetPos);
        Vec3d toPlayerHorizontal = new Vec3d(toPlayer.x, 0.0, toPlayer.z);
        if (toPlayerHorizontal.lengthSquared() == 0.0) return false;
        toPlayerHorizontal = toPlayerHorizontal.normalize();

        Vec3d facing = target.getRotationVector(0.0F, target.getHeadYaw());

        double dot = facing.dotProduct(toPlayerHorizontal);
        return dot < 0.0;
    }

    /*
     * Get nearest enemy player
     */
    public static PlayerEntity getNearestEnemyPlayer() {
        if (mc.player == null || mc.world == null) {
            return null;
        }
        PlayerEntity nearestPlayer = null;
        double nearestDistance = Double.MAX_VALUE;

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || player.isDead() || player.isSpectator()) {
                continue;
            }
            double distance = mc.player.squaredDistanceTo(player);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearestPlayer = player;
            }
        }
        return nearestPlayer;
    }

    public static LivingEntity getNearestLivingEntity(int range) {
        if (mc.player == null || mc.world == null) {
            return null;
        }
        LivingEntity nearestEntity = null;
        double nearestDistance = Double.MAX_VALUE;
        Box searchBox = mc.player.getBoundingBox().expand(range);

        java.util.List<LivingEntity> entities = mc.world.getEntitiesByClass(
                LivingEntity.class,
                searchBox,
                e -> e != mc.player && !e.isDead()
        );

        for (LivingEntity entity : entities) {
            double distance = mc.player.squaredDistanceTo(entity);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearestEntity = entity;
            }
        }
        return nearestEntity;
    }

    /*
     * Outputs the vertical distance between the player (user) and the other player
     */
    public static double getVerticalDistance(PlayerEntity target) {
        if (mc.player == null || target == null) {
            return 0;
        }
        return mc.player.getY() - target.getY();
    }

    /*
     * Outputs the horizontal distance between the player (user) and the other player
     */
    public static double getHorizontalDistance(PlayerEntity target) {
        if (mc.player == null || target == null) {
            return 0;
        }
        double dx = mc.player.getX() - target.getX();
        double dz = mc.player.getZ() - target.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /*
     * Outputs distance between the player (user) and the other player
     */
    public static double getDistance(PlayerEntity target) {
        if (mc.player == null || target == null) {
            return 0;
        }
        double dx = mc.player.getX() - target.getX();
        double dy = mc.player.getY() - target.getY();
        double dz = mc.player.getZ() - target.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /*
     * Checks distance to ground
     */
    public static double distanceToGround() {
        if (mc.player == null || mc.world == null) {
            return 0;
        }
        double y = mc.player.getY();
        int blockY = (int) Math.floor(y);
        for (int i = blockY; i >= 0; i--) {
            if (!mc.world.getBlockState(mc.player.getBlockPos().withY(i)).isAir()) {
                return y - i - 1;
            }
        }
        return y;
    }

    /**
     * Checks if an entity is a player entity
     */
    public static boolean isPlayer(Entity entity) {
        return entity instanceof PlayerEntity;
    }

    /**
     * Checks if an entity is a hostile mob
     */
    public static boolean isHostile(Entity entity) {
        if (entity instanceof HostileEntity) return !(entity instanceof ZombifiedPiglinEntity zombiePiglinEntity)
                || zombiePiglinEntity.isAttacking();
        if (SlimeCompat.isTinySlime(entity)) return false;
        // Might add more conditions in the future
        return entity instanceof Monster
                // Panda, Polar bear, Wolf etc
                || entity instanceof MobEntity mobEntity && mobEntity.isAttacking();
    }

    /**
     * Checks if an entity is a passive mob
     */
    public static boolean isPassive(Entity entity) {
        return entity instanceof PassiveEntity;
    }

    /**
     * Checks if an entity is a neutral mob
     */
    public static boolean isNeutral(Entity entity) {
        return entity instanceof MobEntity
                && !isHostile(entity)
                && !isPassive(entity);
    }

    /**
     * Checks if a living entity is naked (has no armor equipped)
     * Ignores elytra
     */
    public static boolean isNaked(LivingEntity entity) {
        if (entity == null) return false;
        ItemStack chest = entity.getEquippedStack(EquipmentSlot.CHEST);
        return entity.getEquippedStack(EquipmentSlot.HEAD).isEmpty()
                && (chest.isEmpty() || chest.getItem() == Items.ELYTRA)
                && entity.getEquippedStack(EquipmentSlot.LEGS).isEmpty()
                && entity.getEquippedStack(EquipmentSlot.FEET).isEmpty();
    }
}
