package com.utilitymod.echo.utils.simulation;

import com.utilitymod.echo.utils.Imports;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.world.RaycastContext;
import net.minecraft.util.math.Box;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;

public class CrossbowSimulation implements Imports {

    private static final double ARROW_POWER = 3.15;
    private static final double GRAVITY = 0.05;
    private static final float INERTIA = 0.99F;
    private static final float WATER_INERTIA = 0.6F;
    private static final double ENTITY_CHECK_MARGIN = 1.0D;

    private Vec3d position;
    private Vec3d velocity;
    private final Entity sourceEntity;
    private final Entity ownerEntity;
    private boolean leftOwner;
    private boolean hit;

    public CrossbowSimulation(PlayerEntity player, float spreadDegrees, float partialTick) {
        Vec3d launchDirection = Vec3d.fromPolar(player.getPitch(partialTick), player.getYaw(partialTick)).normalize();
        Vec3d up = player.getOppositeRotationVector(partialTick).normalize();
        Vec3d rotatedDirection = rotateAroundAxis(launchDirection, up, spreadDegrees);

        double x = MathHelper.lerp(partialTick, player.lastX, player.getX());
        double y = MathHelper.lerp(partialTick, player.lastY, player.getY()) + player.getStandingEyeHeight();
        double z = MathHelper.lerp(partialTick, player.lastZ, player.getZ());
        this.position = new Vec3d(x, y, z);
        this.velocity = rotatedDirection.multiply(ARROW_POWER);

        Vec3d playerVel = player.getVelocity();
        this.velocity = velocity.add(playerVel.x, player.isOnGround() ? 0.0 : playerVel.y, playerVel.z);

        this.sourceEntity = player;
        this.ownerEntity = player;
        this.leftOwner = false;
        this.hit = false;
    }

    public void simulateTick() {
        if (hit || mc.world == null) return;

        boolean inWater = !mc.world.getFluidState(BlockPos.ofFloored(position)).isEmpty();
        Vec3d nextPos = position.add(velocity);
        updateLeftOwner(nextPos);

        BlockHitResult blockHit = mc.world.raycast(new RaycastContext(
            position, nextPos,
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
        velocity = velocity.multiply(inWater ? WATER_INERTIA : INERTIA);
        velocity = velocity.subtract(0.0, GRAVITY, 0.0);
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
        if (entity instanceof PlayerEntity target && ownerEntity instanceof PlayerEntity owner && !owner.shouldDamagePlayer(target)) return false;
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
