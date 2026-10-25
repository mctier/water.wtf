package com.utilitymod.echo.utils.simulation;

import com.utilitymod.echo.utils.Imports;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.BowItem;
import net.minecraft.world.RaycastContext;
import net.minecraft.util.math.Box;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;

public class BowSimulation implements Imports {

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

    public BowSimulation(PlayerEntity player, int chargeTicks, float partialTick) {
        float power = BowItem.getPullProgress(chargeTicks);

        double x = MathHelper.lerp(partialTick, player.lastX, player.getX());
        double y = MathHelper.lerp(partialTick, player.lastY, player.getY()) + player.getStandingEyeHeight();
        double z = MathHelper.lerp(partialTick, player.lastZ, player.getZ());
        this.position = new Vec3d(x, y, z);
        this.velocity = getArrowVelocity(player.getPitch(partialTick), player.getYaw(partialTick), power * 3.0f);

        // Add player momentum
        Vec3d playerVel = player.getVelocity();
        this.velocity = velocity.add(playerVel.x, player.isOnGround() ? 0 : playerVel.y, playerVel.z);
        this.sourceEntity = player;
        this.ownerEntity = player;
        this.leftOwner = false;
        this.hit = false;
    }

    public BowSimulation(Vec3d position, float pitch, float yaw, float power) {
        this.position = position;
        this.velocity = getArrowVelocity(pitch, yaw, power * 3.0f);
        this.sourceEntity = mc.player;
        this.ownerEntity = resolveOwner(mc.player);
        this.leftOwner = this.ownerEntity == null;
        this.hit = false;
    }

    /** Continue a real projectile's trajectory, including its launch spread and momentum. */
    public BowSimulation(ProjectileEntity projectile) {
        this.position = projectile.getEntityPos();
        this.velocity = projectile.getVelocity();
        this.sourceEntity = projectile;
        this.ownerEntity = projectile.getOwner();
        this.leftOwner = false;
        this.hit = false;
    }

    private Vec3d getArrowVelocity(float pitch, float yaw, float power) {
        float x = -MathHelper.sin(yaw * MathHelper.RADIANS_PER_DEGREE) * MathHelper.cos(pitch * MathHelper.RADIANS_PER_DEGREE);
        float y = -MathHelper.sin(pitch * MathHelper.RADIANS_PER_DEGREE);
        float z = MathHelper.cos(yaw * MathHelper.RADIANS_PER_DEGREE) * MathHelper.cos(pitch * MathHelper.RADIANS_PER_DEGREE);
        return new Vec3d(x, y, z).normalize().multiply(power);
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
        velocity = velocity.subtract(0, GRAVITY, 0);
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
        if (entity instanceof PlayerEntity target && ownerEntity instanceof PlayerEntity owner && !owner.shouldDamagePlayer(target)) return false;
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

