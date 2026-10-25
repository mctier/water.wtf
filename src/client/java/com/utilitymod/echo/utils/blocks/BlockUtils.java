package com.utilitymod.echo.utils.blocks;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.block.BlockState;
import net.minecraft.world.RaycastContext;
import net.minecraft.util.ActionResult;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import static com.utilitymod.echo.utils.Imports.mc;

public class BlockUtils {
    private static final double PLACE_RAY_EPSILON = 1.0E-4;
    private static final double PLACE_FACE_CORNER_OFFSET = 0.49;
    private static final double[][] PLACE_FACE_SAMPLE_OFFSETS = {
        {0.0, 0.0},
        {PLACE_FACE_CORNER_OFFSET, PLACE_FACE_CORNER_OFFSET},
        {PLACE_FACE_CORNER_OFFSET, -PLACE_FACE_CORNER_OFFSET},
        {-PLACE_FACE_CORNER_OFFSET, PLACE_FACE_CORNER_OFFSET},
        {-PLACE_FACE_CORNER_OFFSET, -PLACE_FACE_CORNER_OFFSET}
    };

    public static boolean isBlock(BlockPos pos, Block block) {
        return mc.world.getBlockState(pos).getBlock() == block;
    }

    public static boolean isBlockAtPosition(BlockPos blockPos, Block block) {
        return mc.world.getBlockState(blockPos).getBlock() == block;
    }

    public static boolean isRespawnAnchorCharged(BlockPos blockPos) {
        return isBlockAtPosition(blockPos, Blocks.RESPAWN_ANCHOR) && mc.world.getBlockState(blockPos).get(RespawnAnchorBlock.CHARGES) != 0;
    }

    public static boolean isRespawnAnchorUncharged(BlockPos blockPos) {
        return isBlockAtPosition(blockPos, Blocks.RESPAWN_ANCHOR) && mc.world.getBlockState(blockPos).get(RespawnAnchorBlock.CHARGES) == 0;
    }

    public static ActionResult interactWithBlock(BlockHitResult blockHitResult, boolean shouldSwingHand) {
        if (mc.player == null || mc.interactionManager == null) return ActionResult.PASS;

        ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, blockHitResult);
        if (result.isAccepted() && shouldSwingHand && result instanceof ActionResult.Success success) {
            ActionResult.SwingSource swingSource = success.swingSource();
            if (swingSource == ActionResult.SwingSource.CLIENT || swingSource == ActionResult.SwingSource.SERVER) {
                mc.player.swingHand(Hand.MAIN_HAND);
            }
        }
        return result;
    }

    public static boolean willPlacedBlockPlacementCollideWithEntity(BlockPos blockPos, BlockState blockState) {
        if (mc.world == null) return false;
        if (blockPos == null) return false;
        if (blockState == null) return false;

        VoxelShape collisionShape = blockState.getCollisionShape(mc.world, blockPos);

        Box searchBox = new Box(blockPos);
        var entities = mc.world.getEntitiesByClass(
            LivingEntity.class,
            searchBox,
            e -> e != mc.player
        );

        for (LivingEntity entity : entities) {
            if (entity.getBoundingBox().intersects(searchBox)) {
                return true;
            }
        }
        return false;
    }


    public static boolean isBlockPlacingCollideWithEntity(BlockPos blockPos, Block block) {
        return willPlacedBlockPlacementCollideWithEntity(blockPos, block.getDefaultState());
    }

    /** True if the player's hitbox occupies the block Box at {@code pos}. */
    public static boolean collidesWithPlayer(BlockPos pos) {
        return mc.player != null && pos != null && mc.player.getBoundingBox().intersects(new Box(pos));
    }

    /** True if an entity that blocks building overlaps the block Box at {@code pos}. */
    public static boolean collidesWithBlockingEntity(BlockPos pos) {
        if (pos == null || mc.world == null) return false;
        return hasBlockingEntity(new Box(pos));
    }

    public static boolean hasBlockingEntity(Box box) {
        if (box == null || mc.world == null) return false;
        for (Entity entity : mc.world.getOtherEntities(null, box)) {
            if (!isBlockingEntity(entity)) continue;
            if (entity.getBoundingBox().intersects(box)) return true;
        }
        return false;
    }

    private static boolean isBlockingEntity(Entity entity) {
        if (entity == null || entity == mc.player) return false;
        return !entity.isRemoved() && entity.intersectionChecked;
    }

    public static BlockPos getTargetPlacePos() {
        HitResult hitResult = mc.crosshairTarget;
        if (hitResult == null || hitResult.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        
        BlockHitResult blockHitResult = (BlockHitResult) hitResult;
        return blockHitResult.getBlockPos().offset(blockHitResult.getSide());
    }

    public static boolean isValidPlacement(BlockPos pos) {
        if (pos == null || mc.world == null) return false;
        
        BlockState state = mc.world.getBlockState(pos);
        if (!state.isAir() && !state.isReplaceable()) return false;

        return true;
    }

    public static boolean hasSupportingFace(BlockPos pos) {
        if (pos == null || mc.world == null) return false;

        for (Direction direction : Direction.values()) {
            BlockPos adjacent = pos.offset(direction);
            if (mc.world.getBlockState(adjacent).isSideSolidFullSquare(mc.world, adjacent, direction.getOpposite())) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasCrystalOnBlock(BlockPos pos) {
        if (mc.world == null) return false;

        BlockPos crystalPos = pos.up();
        if (!mc.world.isAir(crystalPos)) return false;

        double x = crystalPos.getX();
        double y = crystalPos.getY();
        double z = crystalPos.getZ();

        Box crystalAABB = new Box(x, y, z, x + 1.0, y + 2.0, z + 1.0);
        return !mc.world.getOtherEntities(null, crystalAABB).isEmpty();
    }

    public static boolean canPlaceLegit(BlockPos pos) {
        return findPlacementHit(pos, true) != null;
    }

    public static PlacementHit findPlacementHit(BlockPos pos, boolean requireRaycast) {
        if (pos == null || mc.world == null || mc.player == null) return null;
        float pt = mc.getRenderTickCounter().getTickProgress(false);
        Vec3d eyePos = mc.player.getCameraPosVec(pt);
        PlacementHit bestHit = null;
        double bestDistanceSq = Double.MAX_VALUE;

        for (Direction direction : Direction.values()) {
            BlockPos adjacent = pos.offset(direction);
            Direction face = direction.getOpposite();

            if (!mc.world.getBlockState(adjacent).isSideSolidFullSquare(mc.world, adjacent, face)) {
                continue;
            }

            PlacementHit hit = findPlacementHitOnFace(eyePos, pos, adjacent, direction, face, requireRaycast);
            if (hit == null) continue;

            double distanceSq = eyePos.squaredDistanceTo(hit.hitResult().getPos());
            if (distanceSq < bestDistanceSq) {
                bestDistanceSq = distanceSq;
                bestHit = hit;
            }
        }

        return bestHit;
    }

    private static PlacementHit findPlacementHitOnFace(Vec3d eyePos, BlockPos placementPos, BlockPos adjacent, Direction direction, Direction face, boolean requireRaycast) {
        for (double[] sampleOffset : PLACE_FACE_SAMPLE_OFFSETS) {
            Vec3d facePoint = getFaceSamplePoint(adjacent, face, sampleOffset[0], sampleOffset[1]);
            BlockHitResult hitResult = new BlockHitResult(facePoint, face, adjacent, false);

            if (requireRaycast && !canRaycastPlacementFace(eyePos, hitResult, direction)) {
                continue;
            }

            return new PlacementHit(placementPos, hitResult);
        }

        return null;
    }

    private static boolean canRaycastPlacementFace(Vec3d eyePos, BlockHitResult placementHit, Direction direction) {
        Vec3d facePoint = placementHit.getPos();
        Vec3d rayEnd = facePoint.add(
            direction.getOffsetX() * PLACE_RAY_EPSILON,
            direction.getOffsetY() * PLACE_RAY_EPSILON,
            direction.getOffsetZ() * PLACE_RAY_EPSILON
        );

        BlockHitResult hitResult = mc.world.raycast(new RaycastContext(
            eyePos,
            rayEnd,
            RaycastContext.ShapeType.OUTLINE,
            RaycastContext.FluidHandling.NONE,
            mc.player
        ));

        if (hitResult.getType() != HitResult.Type.BLOCK) return false;
        if (!hitResult.getBlockPos().equals(placementHit.getBlockPos())) return false;
        return hitResult.getSide() == placementHit.getSide();
    }

    private static Vec3d getFaceSamplePoint(BlockPos adjacent, Direction face, double offsetA, double offsetB) {
        Vec3d faceCenter = Vec3d.ofCenter(adjacent).add(
            face.getOffsetX() * 0.5,
            face.getOffsetY() * 0.5,
            face.getOffsetZ() * 0.5
        );

        return switch (face.getAxis()) {
            case X -> faceCenter.add(0.0, offsetA, offsetB);
            case Y -> faceCenter.add(offsetA, 0.0, offsetB);
            case Z -> faceCenter.add(offsetA, offsetB, 0.0);
        };
    }

    public record PlacementHit(BlockPos placementPos, BlockHitResult hitResult) {}

}
