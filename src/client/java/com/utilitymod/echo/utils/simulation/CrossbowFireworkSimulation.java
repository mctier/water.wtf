package com.utilitymod.echo.utils.simulation;

import com.utilitymod.echo.utils.Imports;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.component.type.FireworksComponent;
import net.minecraft.world.RaycastContext;
import net.minecraft.util.math.Box;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;

public class CrossbowFireworkSimulation implements Imports {

    private static final float FIREWORK_POWER = 1.6F;
    private static final float INACCURACY = 1.0F;
    private static final double ENTITY_CHECK_MARGIN = 1.0D;

    private Vec3d position;
    private Vec3d velocity;
    private final Entity sourceEntity;
    private final Entity ownerEntity;
    private final boolean shotAtAngle;
    private final int lifetime;
    private int life;
    private boolean leftOwner;
    private boolean hit;

    public CrossbowFireworkSimulation(PlayerEntity player, float spreadDegrees, ItemStack fireworkItem, int shotIndex, float partialTick) {
        Vec3d launchDirection = Vec3d.fromPolar(player.getPitch(partialTick), player.getYaw(partialTick)).normalize();
        Vec3d up = player.getOppositeRotationVector(partialTick).normalize();
        Vec3d rotatedDirection = rotateAroundAxis(launchDirection, up, spreadDegrees);

        double x = MathHelper.lerp(partialTick, player.lastX, player.getX());
        double y = MathHelper.lerp(partialTick, player.lastY, player.getY()) + player.getStandingEyeHeight() - 0.15F;
        double z = MathHelper.lerp(partialTick, player.lastZ, player.getZ());
        this.position = new Vec3d(x, y, z);
        this.velocity = applyInaccuracy(rotatedDirection, shotSeed(player, fireworkItem, shotIndex)).multiply(FIREWORK_POWER);

        Vec3d playerVel = player.getVelocity();
        this.velocity = velocity.add(playerVel.x, player.isOnGround() ? 0.0 : playerVel.y, playerVel.z);

        this.sourceEntity = player;
        this.ownerEntity = player;
        this.shotAtAngle = true;
        this.leftOwner = false;
        this.life = 0;
        this.lifetime = resolveLifetime(fireworkItem, shotSeed(player, fireworkItem, shotIndex) ^ 0x6A09E667F3BCC909L);
        this.hit = false;
    }

    public CrossbowFireworkSimulation(FireworkRocketEntity firework) {
        this.position = firework.getEntityPos();
        this.velocity = firework.getVelocity();
        this.sourceEntity = firework;
        this.ownerEntity = firework instanceof ProjectileEntity projectile ? projectile.getOwner() : firework;
        this.shotAtAngle = firework.wasShotAtAngle();

        int syncedLife = com.utilitymod.echo.api.Accessors.fireworkLife(firework);
        int syncedLifetime = com.utilitymod.echo.api.Accessors.fireworkLifetime(firework);
        boolean hasSyncedLifetime = syncedLifetime > 0;

        this.life = Math.max(0, hasSyncedLifetime ? syncedLife : firework.age);
        this.lifetime = hasSyncedLifetime
            ? syncedLifetime
            : resolveLifetime(firework.getStack(), entitySeed(firework) ^ 0x6A09E667F3BCC909L);
        this.leftOwner = this.ownerEntity == null;
        this.hit = false;
    }

    public void simulateTick() {
        if (hit || mc.world == null) return;

        if (!shotAtAngle) {
            velocity = velocity.multiply(1.15, 1.0, 1.15).add(0.0, 0.04, 0.0);
        }

        Vec3d nextPos = position.add(velocity);
        updateLeftOwner(nextPos);

        BlockHitResult blockHit = mc.world.raycast(new RaycastContext(
            position,
            nextPos,
            RaycastContext.ShapeType.COLLIDER,
            RaycastContext.FluidHandling.NONE,
            sourceEntity != null ? sourceEntity : mc.player
        ));

        EntityHitResult entityHit = raycastEntity(nextPos);

        if (entityHit != null && (blockHit.getType() == HitResult.Type.MISS
            || position.squaredDistanceTo(entityHit.getPos()) <= position.squaredDistanceTo(blockHit.getPos()))) {
            position = entityHit.getPos();
            hit = true;
            return;
        }

        if (blockHit.getType() != HitResult.Type.MISS) {
            position = blockHit.getPos();
            hit = true;
            return;
        }

        position = nextPos;
        life++;
        if (life > lifetime) {
            hit = true;
        }
    }

    public Vec3d getPosition() {
        return position;
    }

    public boolean hasHit() {
        return hit;
    }

    private EntityHitResult raycastEntity(Vec3d nextPos) {
        Box searchBox = new Box(position, nextPos).expand(ENTITY_CHECK_MARGIN);

        return ProjectileUtil.raycast(
            sourceEntity != null ? sourceEntity : mc.player,
            position,
            nextPos,
            searchBox,
            this::canHitEntity,
            position.squaredDistanceTo(nextPos)
        );
    }

    private boolean canHitEntity(Entity entity) {
        if (!entity.canBeHitByProjectile()) return false;
        return ownerEntity == null || leftOwner || !ownerEntity.isConnectedThroughVehicle(entity);
    }

    private void updateLeftOwner(Vec3d nextPos) {
        if (leftOwner || ownerEntity == null) return;

        Box ownerCheckBox = new Box(position, nextPos).expand(ENTITY_CHECK_MARGIN);
        leftOwner = ownerEntity
            .getRootVehicle()
            .streamSelfAndPassengers()
            .noneMatch(passenger -> ownerCheckBox.intersects(passenger.getBoundingBox()));
    }

    private static Vec3d applyInaccuracy(Vec3d direction, long seed) {
        if (MathHelper.approximatelyEquals(INACCURACY, 0.0F)) return direction.normalize();

        Random random = Random.create(seed);
        double spread = 0.0172275 * INACCURACY;
        Vec3d adjusted = direction.normalize().add(
            random.nextTriangular(0.0, spread),
            random.nextTriangular(0.0, spread),
            random.nextTriangular(0.0, spread)
        );
        return adjusted.normalize();
    }

    private static int resolveLifetime(ItemStack fireworkItem, long seed) {
        int flight = 1;
        FireworksComponent fireworks = fireworkItem.get(DataComponentTypes.FIREWORKS);
        if (fireworks != null) {
            flight += fireworks.flightDuration();
        }

        Random random = Random.create(seed);
        return 10 * flight + random.nextInt(6) + random.nextInt(7);
    }

    // https://prng.di.unimi.it/splitmix64.c
    private static long shotSeed(PlayerEntity player, ItemStack fireworkItem, int shotIndex) {
        long seed = 0x9E3779B97F4A7C15L;
        seed ^= (long) player.getId() * 0xBF58476D1CE4E5B9L;
        seed ^= (long) shotIndex * 0x94D049BB133111EBL;
        seed ^= (long) ItemStack.hashCode(fireworkItem) * 0xD6E8FEB86659FD93L;
        return seed;
    }

    private static long entitySeed(FireworkRocketEntity firework) {
        long seed = 0x9E3779B97F4A7C15L;
        seed ^= (long) firework.getId() * 0xBF58476D1CE4E5B9L;
        seed ^= (long) ItemStack.hashCode(firework.getStack()) * 0xD6E8FEB86659FD93L;
        return seed;
    }

    private static Vec3d rotateAroundAxis(Vec3d vector, Vec3d axis, float angleDegrees) {
        if (MathHelper.approximatelyEquals(angleDegrees, 0.0F)) return vector;

        Vec3d k = axis.normalize();
        double angleRadians = angleDegrees * MathHelper.RADIANS_PER_DEGREE;
        double cos = Math.cos(angleRadians);
        double sin = Math.sin(angleRadians);

        return vector.multiply(cos)
            .add(k.crossProduct(vector).multiply(sin))
            .add(k.multiply(k.dotProduct(vector) * (1.0 - cos)));
    }
}
