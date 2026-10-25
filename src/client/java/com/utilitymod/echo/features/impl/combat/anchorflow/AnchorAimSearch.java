package com.utilitymod.echo.features.impl.combat.anchorflow;

import com.utilitymod.echo.utils.Imports;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.RaycastContext;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;

import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Picks an aim point on a respawn anchor that the player can actually click.
 *
 * <p>The naive approach -- always aim at the eye-side corner of the eye-facing
 * face -- fails when an adjacent block (the safety block we just placed,
 * obsidian, etc.) occludes that corner: the raycast lands on the neighbor and
 * the interaction is rejected. So we walk every visible face in
 * most-facing-first order, and for each face try a series of sample points
 * (random near-corner -> inward toward face center -> opposite quadrants),
 * returning the first one whose ray from the eye actually hits the anchor.
 */
public final class AnchorAimSearch implements Imports {

    private AnchorAimSearch() {}

    /** Find an aim point on {@code anchorPos} that raycasts cleanly from the player's eye. */
    public static Vec3d findClearAimPoint(BlockPos anchorPos, float partialTick) {
        if (anchorPos == null) return null;
        if (mc.player == null || mc.world == null) return Vec3d.ofCenter(anchorPos);

        Vec3d eye = mc.player.getCameraPosVec(partialTick);
        Vec3d anchorCenter = Vec3d.ofCenter(anchorPos);
        Vec3d toEye = eye.subtract(anchorCenter);

        Direction[] sorted = Direction.values().clone();
        Arrays.sort(sorted, (a, b) -> Double.compare(dotEye(b, toEye), dotEye(a, toEye)));

        for (Direction faceDir : sorted) {
            if (dotEye(faceDir, toEye) <= 0.0) continue; // back face -- can't hit it from here
            Vec3d candidate = searchFace(eye, anchorPos, anchorCenter, faceDir);
            if (candidate != null) return candidate;
        }
        return anchorCenter;
    }

    /** True if {@code aimPoint} raycasts from {@code eye} and lands on {@code anchorPos} (not an adjacent block). */
    public static boolean canHitAnchor(Vec3d eye, Vec3d aimPoint, BlockPos anchorPos) {
        if (mc.world == null || mc.player == null) return false;
        Vec3d dir = aimPoint.subtract(eye);
        if (dir.lengthSquared() < 1.0E-8) return true;
        Vec3d end = eye.add(dir.normalize().multiply(mc.player.getBlockInteractionRange()));
        BlockHitResult hit = mc.world.raycast(new RaycastContext(
                eye, end,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                mc.player
        ));
        return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(anchorPos);
    }

    private static double dotEye(Direction dir, Vec3d toEye) {
        return dir.getOffsetX() * toEye.x + dir.getOffsetY() * toEye.y + dir.getOffsetZ() * toEye.z;
    }

    private static Vec3d searchFace(Vec3d eye, BlockPos anchorPos, Vec3d anchorCenter, Direction faceDir) {
        double fcx = anchorCenter.x + faceDir.getOffsetX() * 0.5;
        double fcy = anchorCenter.y + faceDir.getOffsetY() * 0.5;
        double fcz = anchorCenter.z + faceDir.getOffsetZ() * 0.5;
        Direction.Axis axis = faceDir.getAxis();

        double signA, signB;
        switch (axis) {
            case X -> {
                signA = eye.y >= anchorCenter.y ? 1.0 : -1.0;
                signB = eye.z >= anchorCenter.z ? 1.0 : -1.0;
            }
            case Y -> {
                signA = eye.x >= anchorCenter.x ? 1.0 : -1.0;
                signB = eye.z >= anchorCenter.z ? 1.0 : -1.0;
            }
            default -> {
                signA = eye.x >= anchorCenter.x ? 1.0 : -1.0;
                signB = eye.y >= anchorCenter.y ? 1.0 : -1.0;
            }
        }

        // Random near-corner so consecutive sequences don't pick identical yaws
        // (helps avoid Grim DuplicateRotPlace lining up across cycles).
        double startOff = 0.30 + ThreadLocalRandom.current().nextDouble() * 0.10; // [0.30, 0.40]

        double[][] sampleOffsets = {
                { signA * startOff, signB * startOff },
                { signA * 0.25,     signB * 0.25 },
                { signA * 0.20,     signB * 0.20 },
                { signA * 0.15,     signB * 0.15 },
                { signA * 0.10,     signB * 0.10 },
                { signA * 0.30,     0.0 },
                { 0.0,              signB * 0.30 },
                { 0.0,              0.0 },
                { -signA * 0.30,    signB * 0.30 },
                { signA * 0.30,    -signB * 0.30 },
                { -signA * 0.30,   -signB * 0.30 },
                { -signA * 0.30,    0.0 },
                { 0.0,             -signB * 0.30 },
        };

        for (double[] off : sampleOffsets) {
            Vec3d candidate = faceSamplePoint(axis, fcx, fcy, fcz, off[0], off[1]);
            if (canHitAnchor(eye, candidate, anchorPos)) return candidate;
        }
        return null;
    }

    private static Vec3d faceSamplePoint(Direction.Axis axis, double fcx, double fcy, double fcz,
                                        double offA, double offB) {
        return switch (axis) {
            case X -> new Vec3d(fcx, fcy + offA, fcz + offB);
            case Y -> new Vec3d(fcx + offA, fcy, fcz + offB);
            case Z -> new Vec3d(fcx + offA, fcy + offB, fcz);
        };
    }
}
