package com.utilitymod.echo.utils.simulation;

import com.utilitymod.echo.utils.Imports;
import net.minecraft.util.math.BlockPos;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.world.RaycastContext;
import net.minecraft.util.math.Box;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;

public class PearlSimulation implements Imports {

    private static final double GRAVITY = 0.03;
    private static final float INERTIA = 0.99F;
    private static final float WATER_INERTIA = 0.8F;
    private static final double ENTITY_CHECK_MARGIN = 1.0D;

    private Vec3d position;
    private Vec3d velocity;
    private final Entity sourceEntity;
    private final Entity ownerEntity;
    private boolean leftOwner;
    private boolean hit;

    public PearlSimulation(Vec3d position, Vec3d velocity) {
        this(position, velocity, mc.player);
    }

    public PearlSimulation(Vec3d position, Vec3d velocity, Entity ignoredEntity) {
        this.position = position;
        this.velocity = velocity;
        this.sourceEntity = ignoredEntity;
        this.ownerEntity = resolveOwner(ignoredEntity);
        this.leftOwner = this.ownerEntity == null;
        this.hit = false;
    }

    public void simulateTick() {
        if (hit || mc.world == null) return;

        boolean inWater = !mc.world.getFluidState(BlockPos.ofFloored(position)).isEmpty();
        velocity = velocity.subtract(0, GRAVITY, 0);
        velocity = velocity.multiply(inWater ? WATER_INERTIA : INERTIA);
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
    }

    public void simulateTicks(int ticks) {
        for (int i = 0; i < ticks && !hit; i++) {
            simulateTick();
        }
    }

    public BlockHitResult simulateUntilHit(int maxTicks) {
        for (int i = 0; i < maxTicks && !hit; i++) {
            Vec3d prevPos = position;
            simulateTick();
            if (hit) {
                return mc.world.raycast(new RaycastContext(
                    prevPos, position,
                    RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE,
                    mc.player
                ));
            }
        }
        return null;
    }

    public Vec3d getPosition() {
        return position;
    }

    public Vec3d getVelocity() {
        return velocity;
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

    private Entity resolveOwner(Entity sourceEntity) {
        if (sourceEntity instanceof ProjectileEntity projectile) {
            return projectile.getOwner();
        }

        return sourceEntity;
    }

    private void updateLeftOwner(Vec3d nextPos) {
        if (leftOwner || ownerEntity == null) return;

        Box ownerCheckBox = new Box(position, nextPos).expand(ENTITY_CHECK_MARGIN);
        leftOwner = ownerEntity
            .getRootVehicle()
            .streamSelfAndPassengers()
            .noneMatch(passenger -> ownerCheckBox.intersects(passenger.getBoundingBox()));
    }
}
