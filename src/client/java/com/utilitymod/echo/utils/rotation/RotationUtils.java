package com.utilitymod.echo.utils.rotation;

import com.utilitymod.echo.utils.rotation.PitchUtil;
import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.handlers.RotationHandler;
import com.utilitymod.echo.utils.Imports;
import com.utilitymod.echo.utils.MathUtil;
import com.utilitymod.echo.utils.player.CustomGetVelocity;
import lombok.Getter;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.world.RaycastContext;
import net.minecraft.util.math.Box;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/*
 * Credit for the closest-point math: https://github.com/sootysplash/optimal-aim
 */
public final class RotationUtils implements Imports {

    private RotationUtils() {}

    // -- Enums --

    public enum EntityPoints {
        STRAIGHT,
        CLOSEST,
        RANDOM,
        CENTER,
        FEET
    }

    public enum AimType {
        REGULAR,
        BLATANT,
        WINDMOUSE,
        GROOV,
        STRAIGHT
    }

    // -- State --

    private static float[] lastRotations = new float[]{0f, 0f};
    private static float[] lastSentRotations = new float[]{0f, 0f};

    private static double windYawVel = 0;
    private static double windPitchVel = 0;
    private static double windYaw = 0;
    private static double windPitch = 0;
    private static float gcdYawRemainder = 0f;
    private static float gcdPitchRemainder = 0f;

    // -- Groov smooth-aim state (ported from the groovClient rotation driver) --

    private static float groovWheelYaw;
    private static float groovWheelPitch;
    private static float groovDriftYaw;
    private static float groovDriftPitch;
    private static float groovDriftEnergy;
    private static boolean groovWasFar;
    private static boolean groovActive;
    private static boolean groovHasTime;
    private static long groovAcquiredAt;
    private static float groovPhaseA;
    private static float groovPhaseB;
    private static final float[] GROOV_ACC_YAW = new float[4];
    private static final float[] GROOV_ACC_PITCH = new float[4];
    private static final ArrayDeque<Float> GROOV_STEP_HISTORY = new ArrayDeque<>();

    private static long lastNanoTime = 0;

    @Getter
    private static boolean isTracking = false;
    @Getter
    private static LivingEntity currentTarget = null;

    private static Feature currentController = null;
    private static EventSubscribe.Priority currentPriority = EventSubscribe.Priority.LOWEST;

    // Sticky alt-point: prevents jitter when the primary candidate is blocked.
    // Stored as offset from box.minCorner so it tracks the entity as it moves.
    private static LivingEntity stickyAltTarget = null;
    private static Vec3d stickyAltOffset = null;

    // -- Delta time --

    private static float getDeltaSeconds() {
        float delta = MathUtil.getDeltaSeconds(lastNanoTime);
        lastNanoTime = MathUtil.nanoTime();
        return delta;
    }

    // -- State management --

    public static void resetState() {
        if (mc.player != null) {
            lastRotations[0] = mc.player.getYaw();
            lastRotations[1] = mc.player.getPitch();
            lastSentRotations[0] = mc.player.getYaw();
            lastSentRotations[1] = mc.player.getPitch();
            RotationHandler.syncToPlayer(mc.player.getYaw(), mc.player.getPitch());
        } else {
            lastRotations = new float[]{0f, 0f};
            lastSentRotations = new float[]{0f, 0f};
            RotationHandler.reset();
        }
        windYawVel = 0;
        windPitchVel = 0;
        windYaw = 0;
        windPitch = 0;
        gcdYawRemainder = 0f;
        gcdPitchRemainder = 0f;
        lastNanoTime = 0;
        groovReset();
        isTracking = false;
        currentTarget = null;
        currentController = null;
        currentPriority = EventSubscribe.Priority.LOWEST;
        clearStickyAlt();
    }

    public static void initFromPlayer() {
        if (mc.player == null) return;
        lastRotations[0] = mc.player.getYaw();
        lastRotations[1] = mc.player.getPitch();
        lastSentRotations[0] = mc.player.getYaw();
        lastSentRotations[1] = mc.player.getPitch();
        gcdYawRemainder = 0f;
        gcdPitchRemainder = 0f;
        RotationHandler.updateServerRotations(mc.player.getYaw(), mc.player.getPitch());
    }

    public static void stopTracking() {
        if (mc.player != null) {
            RotationHandler.syncToPlayer(mc.player.getYaw(), mc.player.getPitch());
            lastSentRotations[0] = mc.player.getYaw();
            lastSentRotations[1] = mc.player.getPitch();
        } else {
            RotationHandler.reset();
        }
        lastNanoTime = 0;
        groovReset();
        isTracking = false;
        currentTarget = null;
        currentController = null;
        currentPriority = EventSubscribe.Priority.LOWEST;
        clearStickyAlt();
    }

    public static float[] getLastRotations() {
        return lastRotations.clone();
    }

    // -- Core rotation: entity target --

    public static boolean rotateTo(LivingEntity target, float yawSpeed, float pitchSpeed,
                                   boolean silent, AimType aimType, EntityPoints pointsMode, float multipoint) {
        return rotateTo(target, yawSpeed, pitchSpeed, silent, aimType, pointsMode, multipoint, false, false, null, EventSubscribe.Priority.NORMAL);
    }

    public static boolean rotateTo(LivingEntity target, float yawSpeed, float pitchSpeed,
                                   boolean silent, AimType aimType, EntityPoints pointsMode, float multipoint,
                                   boolean useRandom, Feature requester, EventSubscribe.Priority priority) {
        return rotateTo(target, yawSpeed, pitchSpeed, silent, aimType, pointsMode, multipoint, useRandom, false, requester, priority);
    }

    public static boolean rotateTo(LivingEntity target, float yawSpeed, float pitchSpeed,
                                   boolean silent, AimType aimType, EntityPoints pointsMode, float multipoint,
                                   boolean useRandom, boolean throughWalls, Feature requester, EventSubscribe.Priority priority) {
        if (target == null || mc.player == null || mc.world == null) return false;
        if (requester != null && !tryTakeControl(requester, priority)) return false;

        float pt = mc.getRenderTickCounter().getTickProgress(false);
        float shrink = multipoint * 0.01f;

        Optional<Vec3d> opt = getPossiblePoints(target, shrink, pointsMode, useRandom, throughWalls, pt);
        if (opt.isEmpty()) return false;

        if (currentTarget != target) {
            currentTarget = target;
            windYawVel = 0;
            windPitchVel = 0;
            windYaw = 0;
            windPitch = 0;
            groovOnNewTarget();
            if (!isTracking) initFromPlayer();
        } else if (!isTracking) {
            initFromPlayer();
        }
        isTracking = true;

        float[] targetRot = calculate(mc.player.getCameraPosVec(pt), opt.get());
        float[] baseRotation = silent ? lastRotations : new float[]{mc.player.getYaw(), mc.player.getPitch()};
        targetRot = ensureContinuity(baseRotation, targetRot);

        lastRotations = getFixedRotations(lastRotations,
                getTargetRotations(lastRotations, targetRot, yawSpeed, pitchSpeed, silent, aimType));

        applyRotations(lastRotations, silent);
        return true;
    }

    // -- Core rotation: position target --

    public static boolean rotateTo(Vec3d position, float yawSpeed, float pitchSpeed,
                                   boolean silent, AimType aimType) {
        return rotateTo(position, yawSpeed, pitchSpeed, silent, aimType, null, EventSubscribe.Priority.NORMAL);
    }

    public static boolean rotateTo(Vec3d position, float yawSpeed, float pitchSpeed,
                                   boolean silent, AimType aimType, Feature requester, EventSubscribe.Priority priority) {
        if (position == null || mc.player == null) return false;
        if (requester != null && !tryTakeControl(requester, priority)) return false;

        currentTarget = null;
        windYawVel = 0;
        windPitchVel = 0;
        windYaw = 0;
        windPitch = 0;

        if (!isTracking) initFromPlayer();
        isTracking = true;

        float pt = mc.getRenderTickCounter().getTickProgress(false);
        float[] targetRot = calculate(mc.player.getCameraPosVec(pt), position);
        float[] baseRotation = silent ? lastRotations : new float[]{mc.player.getYaw(), mc.player.getPitch()};
        targetRot = ensureContinuity(baseRotation, targetRot);

        lastRotations = getFixedRotations(lastRotations,
                getTargetRotations(lastRotations, targetRot, yawSpeed, pitchSpeed, silent, aimType));

        applyRotations(lastRotations, silent);
        return true;
    }

    // -- Rotate back to client view --

    public static boolean rotateBack(boolean silent) {
        return rotateBack(silent, 150f, 150f, AimType.REGULAR);
    }

    public static boolean rotateBack(boolean silent, float yawSpeed, float pitchSpeed, AimType aimType) {
        return rotateBack(silent, yawSpeed, pitchSpeed, aimType, true);
    }

    public static boolean rotateBack(boolean silent, float yawSpeed, float pitchSpeed, AimType aimType, boolean scaleSpeed) {
        if (mc.player == null) {
            isTracking = false;
            currentTarget = null;
            currentController = null;
            currentPriority = EventSubscribe.Priority.LOWEST;
            return false;
        }

        if (!isTracking) {
            RotationHandler.syncToPlayer(mc.player.getYaw(), mc.player.getPitch());
            lastSentRotations[0] = mc.player.getYaw();
            lastSentRotations[1] = mc.player.getPitch();
            currentTarget = null;
            currentController = null;
            currentPriority = EventSubscribe.Priority.LOWEST;
            return false;
        }

        float[] clientRot = new float[]{mc.player.getYaw(), mc.player.getPitch()};

        if (aimType == AimType.BLATANT) {
            RotationHandler.syncToPlayer(mc.player.getYaw(), mc.player.getPitch());
            lastSentRotations[0] = mc.player.getYaw();
            lastSentRotations[1] = mc.player.getPitch();
            lastRotations[0] = mc.player.getYaw();
            lastRotations[1] = mc.player.getPitch();
            isTracking = false;
            currentTarget = null;
            currentController = null;
            currentPriority = EventSubscribe.Priority.LOWEST;
            return false;
        }

        float delta = getDeltaSeconds();
        float speedMultiplier = scaleSpeed ? (silent ? 2.5f : 0.5f) : 1.0f;

        lastRotations[0] = lerpAngle(lastRotations[0], clientRot[0], delta, yawSpeed * speedMultiplier);
        lastRotations[1] = lerpAngle(lastRotations[1], clientRot[1], delta, pitchSpeed * speedMultiplier);

        float yawDiff = Math.abs(MathHelper.wrapDegrees(lastRotations[0] - clientRot[0]));
        float pitchDiff = Math.abs(lastRotations[1] - clientRot[1]);

        if (yawDiff < 1.0f && pitchDiff < 1.0f) {
            RotationHandler.syncToPlayer(mc.player.getYaw(), mc.player.getPitch());
            lastSentRotations[0] = mc.player.getYaw();
            lastSentRotations[1] = mc.player.getPitch();
            isTracking = false;
            currentTarget = null;
            currentController = null;
            currentPriority = EventSubscribe.Priority.LOWEST;
            return false;
        }

        if (silent) applySilentRotations(lastRotations);
        return true;
    }

    // -- Sync silent rotation to client view, then run action --

    /**
     * @param requester The feature requesting rotation control
     * @param priority Priority level for rotation control
     * @param aimType BLATANT, REGULAR
     * @param yawSpeed Non blatant yaw speed
     * @param pitchSpeed Non blatant pitch speed
     * @param action The action to execute (e.g., item use) - will be run after rotation sync
     */
    public static void syncToClientAndRun(Feature requester, EventSubscribe.Priority priority,
                                               AimType aimType, float yawSpeed, float pitchSpeed,
                                               Runnable action) {
        if (mc.player == null || mc.world == null) {
            action.run();
            return;
        }

        if (!RotationHandler.isHasSilentRotation()) {
            action.run();
            return;
        }

        boolean tookControl = (requester == null || tryTakeControl(requester, priority));
        if (!tookControl) {
            action.run();
            return;
        }

        if (!isTracking) initFromPlayer();
        isTracking = true;
        currentTarget = null;

        float[] clientRot = new float[]{mc.player.getYaw(), mc.player.getPitch()};

        if (aimType == AimType.BLATANT) {
            lastRotations[0] = clientRot[0];
            lastRotations[1] = clientRot[1];
            applySilentRotations(lastRotations);
            action.run();
        } else {
            // Intentionally uses FPS-based delta for rapid convergence loop
            float delta = 1.0f / Math.max(1f, mc.getCurrentFps());

            while (true) {
                lastRotations[0] = lerpAngle(lastRotations[0], clientRot[0], delta, yawSpeed);
                lastRotations[1] = lerpAngle(lastRotations[1], clientRot[1], delta, pitchSpeed);
                applySilentRotations(lastRotations);

                float yawDiff = Math.abs(MathHelper.wrapDegrees(lastRotations[0] - clientRot[0]));
                float pitchDiff = Math.abs(lastRotations[1] - clientRot[1]);

                if (yawDiff < 1.0f && pitchDiff < 1.0f) {
                    action.run();
                    break;
                }
            }
        }
        releaseControl(requester);

        if (currentController == null && mc.player != null) {
            RotationHandler.syncToPlayer(mc.player.getYaw(), mc.player.getPitch());
            lastSentRotations[0] = mc.player.getYaw();
            lastSentRotations[1] = mc.player.getPitch();
        }
    }

    // -- Rotation application --

    public static void applySilentRotations(float[] rotations) {
        float yawDiff = Math.abs(MathHelper.wrapDegrees(rotations[0] - lastSentRotations[0]));
        float enforcedPitch = PitchUtil.clampPitch(rotations[1]);
        float pitchDiff = Math.abs(enforcedPitch - lastSentRotations[1]);

        if (yawDiff <= 0.01f && pitchDiff <= 0.01f) return;

        float normalizedYaw = lastSentRotations[0] + MathHelper.wrapDegrees(rotations[0] - lastSentRotations[0]);
        float normalizedPitch = MathHelper.clamp(enforcedPitch, -90f, 90f);

        RotationHandler.setServerRotationsWithDelta(normalizedYaw, normalizedPitch);
        RotationHandler.setHasSilentRotation(true);
        lastSentRotations[0] = normalizedYaw;
        lastSentRotations[1] = normalizedPitch;
    }

    // Only used for KB displacement. Would remove othewise
    public static void forceSilentRotations(float yaw, float pitch) {
        if (mc.player == null) return;

        float baseYaw = RotationHandler.isHasSilentRotation() ? RotationHandler.getServerYaw() : mc.player.getYaw();
        float basePitch = RotationHandler.isHasSilentRotation() ? RotationHandler.getServerPitch() : mc.player.getPitch();

        lastRotations[0] = baseYaw;
        lastRotations[1] = basePitch;
        lastSentRotations[0] = baseYaw;
        lastSentRotations[1] = basePitch;

        float[] targetRotations = ensureContinuity(
            new float[]{baseYaw, basePitch},
            new float[]{yaw, pitch}
        );

        applySilentRotations(targetRotations);
        lastRotations[0] = targetRotations[0];
        lastRotations[1] = targetRotations[1];
    }

    public static void applyClientRotations(float[] rotations) {
        if (mc.player == null) return;
        float enforcedPitch = PitchUtil.clampPitch(rotations[1]);
        mc.player.setYaw(rotations[0]);
        mc.player.setPitch(enforcedPitch);
        RotationHandler.reset();
    }

    private static void applyRotations(float[] rotations, boolean silent) {
        if (silent) {
            applySilentRotations(rotations);
        } else {
            applyClientRotations(rotations);
        }
    }

    // -- Angle calculation --

    public static float[] calculate(Vec3d from, Vec3d to) {
        Vec3d diff = to.subtract(from);
        double distance = Math.hypot(diff.x, diff.z);
        float yaw = MathHelper.wrapDegrees((float) (Math.toDegrees(Math.atan2(diff.z, diff.x))) - 90.0F);
        float pitch = MathHelper.wrapDegrees((float) (-Math.toDegrees(Math.atan2(diff.y, distance))));
        return new float[]{yaw, pitch};
    }

    public static Vec3d predictAimPoint(LivingEntity entity, int ticks, EntityPoints mode) {
        if (entity == null || ticks <= 0) return getEntityPoint(entity, mode, 1.0f);
        Vec3d offset = getEntityPoint(entity, mode, 1.0f).subtract(entity.getEntityPos());
        return entity.getEntityPos().add(CustomGetVelocity.get(entity).multiply(ticks)).add(offset);
    }

    // -- Interpolation helpers --

    public static Box getInterpolatedAABB(Entity entity, float partialTick) {
        Vec3d pos = entity.getLerpedPos(partialTick);
        return entity.getDimensions(entity.getPose()).getBoxAt(pos);
    }

    // -- Point / box utilities --

    public static Vec3d closestPointToBox(Box box, float partialTick) {
        if (mc.player == null) return box.getCenter();
        Vec3d eye = mc.player.getCameraPosVec(partialTick);
        return clampToBox(eye, box);
    }

    private static Vec3d clampToBox(Vec3d point, Box box) {
        double cx = Math.min(Math.max(point.x, box.minX), box.maxX);
        double cy = Math.min(Math.max(point.y, box.minY), box.maxY);
        double cz = Math.min(Math.max(point.z, box.minZ), box.maxZ);
        return new Vec3d(cx, cy, cz);
    }

    public static Vec3d closestPointToBoxFromRay(Box box, Vec3d rayOrigin, Vec3d rayDirection) {
        if (box == null || rayOrigin == null || rayDirection == null) return box.getCenter();

        Vec3d toBox = box.getCenter().subtract(rayOrigin);
        double t = Math.max(0, toBox.dotProduct(rayDirection));
        Vec3d rayPoint = rayOrigin.add(rayDirection.multiply(t));

        double cx = Math.min(Math.max(rayPoint.x, box.minX), box.maxX);
        double cy = Math.min(Math.max(rayPoint.y, box.minY), box.maxY);
        double cz = Math.min(Math.max(rayPoint.z, box.minZ), box.maxZ);

        double minY = box.minY + 0.51;
        if (cy < minY) cy = minY;

        return new Vec3d(cx, cy, cz);
    }

    public static Vec3d randomPointInBox(Box box) {
        double rx = box.minX + ThreadLocalRandom.current().nextDouble() * (box.maxX - box.minX);
        double ry = box.minY + ThreadLocalRandom.current().nextDouble() * (box.maxY - box.minY);
        double rz = box.minZ + ThreadLocalRandom.current().nextDouble() * (box.maxZ - box.minZ);
        return new Vec3d(rx, ry, rz);
    }

    public static Vec3d getEntityPoint(LivingEntity entity, EntityPoints mode, float partialTick) {
        if (entity == null) return Vec3d.ZERO;
        Box box = getInterpolatedAABB(entity, partialTick);
        Vec3d center = box.getCenter();

        return switch (mode) {
            case STRAIGHT -> new Vec3d(center.x, box.maxY - 0.1f, center.z);
            case CLOSEST -> closestPointToBox(box, partialTick);
            case RANDOM -> randomPointInBox(box);
            case CENTER -> center;
            case FEET -> new Vec3d(center.x, box.minY + 0.1, center.z);
        };
    }

    // -- Aim point resolution --

    public static Optional<Vec3d> getPossiblePoints(LivingEntity entity, float shrink, EntityPoints entityPoints,
                                                   boolean useRandom, boolean throughWalls, float partialTick) {
        if (entity == null || mc.player == null || mc.world == null) return Optional.empty();

        Box bBox = getInterpolatedAABB(entity, partialTick);
        Vec3d center = bBox.getCenter();
        Vec3d candidate = getEntityPoint(entity, entityPoints, partialTick);

        if (useRandom) {
            candidate = blendWithRandom(candidate, bBox, entityPoints);
        }

        Vec3d finalCandidate = center.lerp(candidate, shrink);

        if (isPointVisible(finalCandidate, partialTick)) {
            clearStickyAlt();
            return Optional.of(finalCandidate);
        }

        // Reuse last frame's visible alt if it is still visible — prevents per-frame
        // flipping between adjacent grid samples as the player moves.
        if (stickyAltTarget == entity && stickyAltOffset != null) {
            Vec3d sticky = new Vec3d(
                    bBox.minX + stickyAltOffset.x,
                    bBox.minY + stickyAltOffset.y,
                    bBox.minZ + stickyAltOffset.z
            );
            if (isInsideBox(sticky, bBox) && isPointVisible(sticky, partialTick)) {
                return Optional.of(sticky);
            }
        }

        Vec3d visibleAlt = findVisiblePointInBox(bBox, finalCandidate, partialTick);
        if (visibleAlt != null) {
            stickyAltTarget = entity;
            stickyAltOffset = new Vec3d(
                    visibleAlt.x - bBox.minX,
                    visibleAlt.y - bBox.minY,
                    visibleAlt.z - bBox.minZ
            );
            return Optional.of(visibleAlt);
        }

        clearStickyAlt();
        return throughWalls ? Optional.of(finalCandidate) : Optional.empty();
    }

    private static void clearStickyAlt() {
        stickyAltTarget = null;
        stickyAltOffset = null;
    }

    private static boolean isInsideBox(Vec3d p, Box box) {
        return p.x >= box.minX && p.x <= box.maxX
                && p.y >= box.minY && p.y <= box.maxY
                && p.z >= box.minZ && p.z <= box.maxZ;
    }

    private static boolean isPointVisible(Vec3d point, float partialTick) {
        HitResult r = rayTraceTo(point, partialTick);
        return r == null
                || r.getType() == HitResult.Type.MISS
                || r.getPos().squaredDistanceTo(point) < 1.0e-6d;
    }

    // Grid-samples the entity Box and returns the visible sample closest to {@code preferred}.
    // Density is tuned to reliably resolve slab-sized (0.5b) gaps in line of sight.
    private static Vec3d findVisiblePointInBox(Box box, Vec3d preferred, float partialTick) {
        if (mc.player == null || mc.world == null) return null;

        final int xSteps = 3;
        final int ySteps = 5;
        final int zSteps = 3;
        final double width = box.maxX - box.minX;
        final double height = box.maxY - box.minY;
        final double depth = box.maxZ - box.minZ;

        Vec3d best = null;
        double bestDistSq = Double.MAX_VALUE;

        for (int xi = 0; xi < xSteps; xi++) {
            double x = box.minX + (xi / (double) (xSteps - 1)) * width;
            for (int yi = 0; yi < ySteps; yi++) {
                double y = box.minY + (yi / (double) (ySteps - 1)) * height;
                for (int zi = 0; zi < zSteps; zi++) {
                    double z = box.minZ + (zi / (double) (zSteps - 1)) * depth;
                    Vec3d sample = new Vec3d(x, y, z);

                    if (!isPointVisible(sample, partialTick)) continue;

                    double distSq = sample.squaredDistanceTo(preferred);
                    if (distSq < bestDistSq) {
                        bestDistSq = distSq;
                        best = sample;
                    }
                }
            }
        }

        return best;
    }

    private static Vec3d blendWithRandom(Vec3d candidate, Box bBox, EntityPoints entityPoints) {
        Vec3d randomPoint;

        if (entityPoints == EntityPoints.CLOSEST) {
            double regionWidth = (bBox.maxX - bBox.minX) * 0.9;
            double regionHeight = (bBox.maxY - bBox.minY) * 0.9;
            double regionDepth = (bBox.maxZ - bBox.minZ) * 0.9;

            double rx = candidate.x + (ThreadLocalRandom.current().nextDouble() - 0.5) * regionWidth;
            double ry = candidate.y + (ThreadLocalRandom.current().nextDouble() - 0.5) * regionHeight;
            double rz = candidate.z + (ThreadLocalRandom.current().nextDouble() - 0.5) * regionDepth;

            rx = Math.min(Math.max(rx, bBox.minX), bBox.maxX);
            ry = Math.min(Math.max(ry, bBox.minY), bBox.maxY);
            rz = Math.min(Math.max(rz, bBox.minZ), bBox.maxZ);

            randomPoint = new Vec3d(rx, ry, rz);
        } else {
            randomPoint = randomPointInBox(bBox);
        }

        return candidate.lerp(randomPoint, 0.50);
    }

    // -- Ray tracing --

    public static HitResult rayTraceTo(Vec3d end, float partialTick) {
        if (mc.player == null || mc.world == null) return null;
        Vec3d start = mc.player.getCameraPosVec(partialTick);
        return mc.world.raycast(new RaycastContext(start, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player));
    }

    // -- Lerp helpers --

    public static float lerpAngle(float current, float target, float deltaSeconds, float speed) {
        float diff = MathHelper.wrapDegrees(target - current);
        float step = speed * deltaSeconds;
        if (Math.abs(diff) <= step) return current + diff;
        return current + Math.signum(diff) * step;
    }

    public static float smoothLerpAngle(float current, float target, float deltaSeconds, float speed) {
        float diff = MathHelper.wrapDegrees(target - current);
        float factor = 1.0f - (float) Math.exp(-Math.max(0.0f, speed) * deltaSeconds * 0.01f);
        return current + diff * factor;
    }

    // -- Aim math selection --

    public static float[] getTargetRotations(float[] lastRot, float[] targetRot,
                                              float yawSpeed, float pitchSpeed,
                                              boolean silent, AimType aimType) {
        if (mc.player == null || mc.world == null) return lastRot;

        float yaw = silent ? lastRot[0] : mc.player.getYaw();
        float pitch = silent ? lastRot[1] : mc.player.getPitch();
        float delta = getDeltaSeconds();
        float speedMultiplier = silent ? 2.5f : 0.5f;

        return switch (aimType) {
            case REGULAR -> new float[]{
                    lerpAngle(yaw, targetRot[0], delta, yawSpeed * speedMultiplier),
                    lerpAngle(pitch, targetRot[1], delta, pitchSpeed * speedMultiplier)
            };
            case BLATANT -> new float[]{targetRot[0], targetRot[1]};
            case WINDMOUSE -> getWindMouseRotations(
                    new float[]{yaw, pitch}, targetRot, yawSpeed, pitchSpeed, delta, silent);
            // Groov math carries its own time/displacement scaling (speed is
            // an error multiplier, not deg/sec), so the silent speed boost
            // would double-apply — speed is passed through unmolested.
            case GROOV -> getGroovRotations(lastRot, targetRot, yawSpeed, silent, delta);
            // STRAIGHT (Prestige law): ignorable convergence cost - the wire look snaps
            // to the target in one frame; only sensible behind a silent packet window.
            case STRAIGHT -> new float[]{targetRot[0], targetRot[1]};
        };
    }

    // -- WindMouse --

    private static float[] getWindMouseRotations(float[] currentRot, float[] targetRot,
                                                  float yawSpeed, float pitchSpeed,
                                                  float delta, boolean silent) {
        double currentYaw = currentRot[0];
        double currentPitch = currentRot[1];
        double targetYaw = targetRot[0];
        double targetPitch = targetRot[1];

        boolean yawEnabled = yawSpeed > 0.0f;
        boolean pitchEnabled = pitchSpeed > 0.0f;

        double yawScale = yawSpeed * (silent ? 2.5 : 0.5);
        double pitchScale = pitchSpeed * (silent ? 2.5 : 0.5);

        double M0Yaw = yawScale * delta;
        double M0Pitch = pitchScale * delta;
        double baseM0 = Math.max(M0Yaw, M0Pitch);

        double G_0 = baseM0 * 0.6;
        double W_0 = baseM0 * 0.2;
        double D_0 = 8.0;

        double diffYaw = MathHelper.wrapDegrees(targetYaw - currentYaw);
        double diffPitch = targetPitch - currentPitch;
        double dist = Math.hypot(diffYaw, diffPitch);

        double W_mag = Math.min(W_0, dist);

        // Update wind forces
        if (dist >= D_0) {
            windYaw = yawEnabled ? windYaw / Math.sqrt(3) + (ThreadLocalRandom.current().nextDouble() * 2 - 1) * W_mag / Math.sqrt(5) : 0;
            windPitch = pitchEnabled ? windPitch / Math.sqrt(3) + (ThreadLocalRandom.current().nextDouble() * 2 - 1) * W_mag / Math.sqrt(5) : 0;
        } else {
            windYaw = yawEnabled ? windYaw / Math.sqrt(3) : 0;
            windPitch = pitchEnabled ? windPitch / Math.sqrt(3) : 0;
        }

        double currM0Yaw = M0Yaw;
        double currM0Pitch = M0Pitch;
        if (dist < D_0) {
            currM0Yaw /= Math.sqrt(5);
            currM0Pitch /= Math.sqrt(5);
        }

        double gravity = Math.min(G_0, dist);

        // Update velocity
        if (yawEnabled) {
            windYawVel += windYaw + (dist == 0 ? 0 : gravity * diffYaw / dist);
        } else {
            windYawVel = 0;
        }
        if (pitchEnabled) {
            windPitchVel += windPitch + (dist == 0 ? 0 : gravity * diffPitch / dist);
        } else {
            windPitchVel = 0;
        }

        // Dampen when close
        if (dist < 0.5) {
            windYawVel *= 0.7;
            windPitchVel *= 0.7;
        }
        if (dist < 0.1) {
            windYawVel = 0;
            windPitchVel = 0;
        }

        // Clamp velocity
        if (yawEnabled && currM0Yaw > 0) {
            double yawLimit = currM0Yaw * (0.5 + ThreadLocalRandom.current().nextDouble() * 0.5);
            windYawVel = MathHelper.clamp(windYawVel, -yawLimit, yawLimit);
        }
        if (pitchEnabled && currM0Pitch > 0) {
            double pitchLimit = currM0Pitch * (0.5 + ThreadLocalRandom.current().nextDouble() * 0.5);
            windPitchVel = MathHelper.clamp(windPitchVel, -pitchLimit, pitchLimit);
        }

        return new float[]{
                (float) (currentYaw + windYawVel),
                (float) (currentPitch + windPitchVel)
        };
    }

    // -- Groov smooth-aim driver --

    /** Per-hand tremor octaves: freq rad/s, amplitude deg. */
    private static final float[] GROOV_TREMOR_FREQ = {2.7f, 5.9f, 12.3f, 25.1f};
    private static final float[] GROOV_TREMOR_AMP = {0.022f, 0.011f, 0.0055f, 0.0028f};
    private static final float[] GROOV_HARMONIC = {1.0f, 1.3f, 2.1f, 3.7f};

    private static void groovReset() {
        groovActive = false;
        groovHasTime = false;
        groovAcquiredAt = 0L;
        groovResetWheel();
    }

    private static void groovResetWheel() {
        groovWheelYaw = 0f;
        groovWheelPitch = 0f;
        groovDriftYaw = 0f;
        groovDriftPitch = 0f;
        groovDriftEnergy = 0f;
        groovWasFar = false;
    }

    /** New target: the hand doesn't change — only the wheel and ramp restart. */
    private static void groovOnNewTarget() {
        groovResetWheel();
        groovActive = false;
        groovHasTime = false;
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        groovPhaseA = (float) (rng.nextDouble() * 100.0);
        groovPhaseB = (float) (rng.nextDouble() * 100.0);
    }

    private static float groovSmoothstep(float t) {
        return t * t * (3.0f - 2.0f * t);
    }

    private static float groovTremor(boolean yawAxis, float deltaSeconds) {
        float[] acc = yawAxis ? GROOV_ACC_YAW : GROOV_ACC_PITCH;
        float base = yawAxis ? groovPhaseA : groovPhaseB;
        float total = 0f;
        for (int i = 0; i < 4; i++) {
            acc[i] += deltaSeconds * GROOV_TREMOR_FREQ[i];
            total += (float) Math.sin(acc[i] + base * GROOV_HARMONIC[i]) * GROOV_TREMOR_AMP[i];
        }
        return yawAxis ? total : total * 0.75f;
    }

    private static void groovRecordStep(float[] prev, float[] next) {
        float mag = (float) Math.hypot(MathHelper.wrapDegrees(next[0] - prev[0]), next[1] - prev[1]);
        GROOV_STEP_HISTORY.addLast(mag);
        while (GROOV_STEP_HISTORY.size() > 30) GROOV_STEP_HISTORY.removeFirst();
    }

    /** Consistency of the last sent steps: 1.0 = metronomic, floored at 0.3. */
    private static float groovConsistency() {
        int size = GROOV_STEP_HISTORY.size();
        if (size < 10) return 1.0f;
        float weighted = 0f;
        float weights = 0f;
        int i = 0;
        for (float v : GROOV_STEP_HISTORY) {
            float w = (float) (i + 1) / size;
            weighted += v * w;
            weights += w;
            i++;
        }
        float mean = weights > 0f ? weighted / weights : 5.0f;
        float variance = 0f;
        for (float v : GROOV_STEP_HISTORY) variance += (v - mean) * (v - mean);
        float std = (float) Math.sqrt(variance / size);
        return Math.max(0.3f, 1.0f - std / mean);
    }

    private static float groovFovScale() {
        Integer fov = mc.options.getFov().getValue();
        return (fov == null ? 90 : fov) / 90.0f;
    }

    /**
     * Spring-damper wheel toward the wire, split at 6 degrees: far turns use a
     * power-eased envelope with an inertial lowpass, near convergence hands the
     * momentum off to a decaying drift so the settle never stops dead, and a
     * four-octave tremor rides both axes. The wheel output never overshoots the
     * remaining error, and a 180 ms ramp gates fresh acquisitions.
     */
    private static float[] getGroovRotations(float[] lastRot, float[] targetRot,
                                              float speed, boolean silent, float deltaSeconds) {
        long now = System.currentTimeMillis();
        // getDeltaSeconds is stateful and getTargetRotations already consumed
        // this tick's sample; a second read returns ~0 and stalls the wheel.
        float delta = MathHelper.clamp(deltaSeconds, 0.0f, 0.1f);
        if (!groovActive) {
            groovActive = true;
            groovAcquiredAt = now;
            groovHasTime = false;
        }
        float dt = groovHasTime ? delta : 0.016f;
        groovHasTime = true;

        float errYaw = MathHelper.wrapDegrees(targetRot[0] - lastRot[0]);
        float errPitch = targetRot[1] - lastRot[1];
        float angle = (float) Math.hypot(errYaw, errPitch);

        float consistency = groovConsistency();
        float inputScale = consistency * groovFovScale() * (silent ? 1.0f : 0.4f);

        float stepYaw;
        float stepPitch;
        if (angle > 6.0f) {
            float eased = (float) (1.0 - Math.pow(1.0 - Math.min(1.0f, angle / 90.0f), 2.5));
            float envelope = 0.15f + eased * 0.85f;
            float yawEase = groovSmoothstep(Math.min(1.0f, Math.abs(errYaw) / 45.0f));
            float pitchEase = groovSmoothstep(Math.min(1.0f, Math.abs(errPitch) / 30.0f));
            float targetWheelYaw = errYaw * speed * inputScale * (0.2f + yawEase * 0.8f) * envelope;
            float targetWheelPitch = errPitch * speed * inputScale * (0.2f + pitchEase * 0.8f) * envelope;
            float lowpass = 1.0f - (float) Math.exp(-(16.0f + ThreadLocalRandom.current().nextFloat() * 6.0f) * delta);
            groovWheelYaw += (targetWheelYaw - groovWheelYaw) * lowpass;
            groovWheelPitch += (targetWheelPitch - groovWheelPitch) * lowpass;
            float scale = dt * 60.0f;
            stepYaw = groovWheelYaw * scale * 0.15f;
            stepPitch = groovWheelPitch * scale * 0.15f;
            float wander = 0.015f * (1.0f - eased);
            stepYaw += groovTremor(true, dt) * wander;
            stepPitch += groovTremor(false, dt) * wander;
            groovWasFar = angle > 15.0f;
        } else {
            if (groovWasFar) {
                // crossing threshold: bank part of the wheel as decaying drift
                groovWasFar = false;
                float keep = 0.015f + (float) Math.random() * 0.15f;
                groovDriftYaw = groovWheelYaw * keep * 0.3f;
                groovDriftPitch = groovWheelPitch * keep * 0.3f;
                groovDriftEnergy = 1.0f;
            }
            if (groovDriftEnergy > 0.01f) {
                groovDriftEnergy *= (float) Math.exp(-8.0f * dt);
                stepYaw = groovDriftYaw * groovDriftEnergy;
                stepPitch = groovDriftPitch * groovDriftEnergy;
            } else {
                stepYaw = 0f;
                stepPitch = 0f;
            }
            float nearGain = 0.35f + consistency * 0.25f;
            float targetWheelYaw = errYaw * nearGain * inputScale;
            float targetWheelPitch = errPitch * nearGain * inputScale;
            float lowpass = 1.0f - (float) Math.exp(-6.0f * (0.5f + consistency * 0.5f) * dt);
            groovWheelYaw += (targetWheelYaw - groovWheelYaw) * lowpass;
            groovWheelPitch += (targetWheelPitch - groovWheelPitch) * lowpass;
            float scale = dt * 60.0f;
            stepYaw += groovWheelYaw * scale * 0.08f;
            stepPitch += groovWheelPitch * scale * 0.08f;
            float tremorScale = 0.6f + consistency * 0.4f;
            stepYaw += groovTremor(true, dt) * tremorScale;
            stepPitch += groovTremor(false, dt) * tremorScale;
        }

        float ramp = groovSmoothstep(Math.min(1.0f, (now - groovAcquiredAt) / 180.0f));
        stepYaw *= ramp;
        stepPitch *= ramp;

        if (angle > 6.0f) {
            if (Math.abs(stepYaw) > Math.abs(errYaw)) stepYaw = errYaw;
            if (Math.abs(stepPitch) > Math.abs(errPitch)) stepPitch = errPitch;
        }

        float[] out = new float[]{
                lastRot[0] + stepYaw * 0.2f,
                MathHelper.clamp(lastRot[1] + stepPitch * 0.2f, -90.0f, 90.0f)
        };
        groovRecordStep(lastRot, out);
        return out;
    }

    // -- GCD fix --

    public static float[] getFixedRotations(float[] prev, float[] current) {
        float yawDiff = MathHelper.wrapDegrees(current[0] - prev[0]);
        float pitchDiff = MathHelper.wrapDegrees(current[1] - prev[1]);
        float[] capped = new float[]{prev[0] + yawDiff, prev[1] + pitchDiff};
        return applyGCD(capped, prev);
    }

    
    // -- GCD but works with hyperspeed --
    public static float[] applyGCD(float[] rotations, float[] lastRotations) {
        float f = (float) (mc.options.getMouseSensitivity().getValue() * 0.6F + 0.2F);
        float gcd = f * f * f * 1.2F;

        float accumulatedYaw = gcdYawRemainder + MathHelper.wrapDegrees(rotations[0] - lastRotations[0]);
        float accumulatedPitch = gcdPitchRemainder + MathHelper.wrapDegrees(rotations[1] - lastRotations[1]);
        float snappedYaw = gcd <= 0f ? accumulatedYaw : (int) (accumulatedYaw / gcd) * gcd;
        float snappedPitch = gcd <= 0f ? accumulatedPitch : (int) (accumulatedPitch / gcd) * gcd;

        gcdYawRemainder = MathHelper.wrapDegrees(accumulatedYaw - snappedYaw);
        gcdPitchRemainder = accumulatedPitch - snappedPitch;

        return new float[]{
                lastRotations[0] + snappedYaw,
                lastRotations[1] + snappedPitch
        };
    }

    public static float[] ensureContinuity(float[] lastRot, float[] targetRot) {
        float lastYaw = MathHelper.wrapDegrees(lastRot[0]);
        float diff = MathHelper.wrapDegrees(targetRot[0] - lastYaw);
        return new float[]{lastYaw + diff, MathHelper.clamp(targetRot[1], -90f, 90f)};
    }

    // -- Silent rotation accessors --

    public static float getSilentYaw() {
        return lastSentRotations[0];
    }

    public static float getSilentPitch() {
        return lastSentRotations[1];
    }

    public static boolean hasSilentRotation() {
        return RotationHandler.isHasSilentRotation();
    }

    // -- Humanized steering (mochi HumanizedAimController port) --

    private static final HumanizedAimController HUMANIZED =
            new HumanizedAimController(new java.util.Random(0x6767L));

    public static float getGcd() {
        float sensitivityFactor = (float) (mc.options.getMouseSensitivity().getValue() * 0.6F + 0.2F);
        return sensitivityFactor * sensitivityFactor * sensitivityFactor * 1.2F;
    }

    /** Drops the humanized steering state; callers call on target switches. */
    public static void resetHumanizedSteering() {
        HUMANIZED.reset(true);
    }

    /**
     * Steers the silent server look toward the aim point with the humanized
     * controller and publishes it through the silent wire. Control is taken
     * for {@code requester}; when the wire is denied the call is a no-op.
     *
     * @return remaining yaw delta to the aim point, or Float.NaN without control
     */
    public static float steerSilent(Feature requester, EventSubscribe.Priority priority,
                                    Vec3d aimPoint, float maxSpeed,
                                    float wobbleUnit, float momentumUnit, float predictLeadUnit,
                                    boolean pitchEnabled) {
        if (mc.player == null || aimPoint == null) {
            return Float.NaN;
        }
        if (!tryTakeControl(requester, priority)) {
            return Float.NaN;
        }
        if (!isTracking) {
            initFromPlayer();
        }
        isTracking = true;
        HUMANIZED.configure(maxSpeed, wobbleUnit, momentumUnit, predictLeadUnit, !pitchEnabled);
        Vec3d from = mc.player.getEyePos();
        float curYaw = lastRotations[0];
        float curPitch = lastRotations[1];
        float[] steps = HUMANIZED.process(
                curYaw, curPitch, from.x, from.y, from.z, aimPoint,
                System.currentTimeMillis(), getGcd(), 1.0);
        float[] next = new float[]{
                curYaw + steps[0],
                MathHelper.clamp(curPitch + steps[1], -90f, 90f)
        };
        lastRotations = next;
        applySilentRotations(next);
        return remainingYaw(from, aimPoint, next[0]);
    }

    /**
     * Steers the real client camera toward the aim point with the humanized
     * controller. No silent wire state is touched.
     */
    public static float steerClient(Feature requester, EventSubscribe.Priority priority,
                                    Vec3d aimPoint, float maxSpeed,
                                    float wobbleUnit, float momentumUnit, float predictLeadUnit,
                                    boolean pitchEnabled) {
        if (mc.player == null || aimPoint == null) {
            return Float.NaN;
        }
        tryTakeControl(requester, priority);
        HUMANIZED.configure(maxSpeed, wobbleUnit, momentumUnit, predictLeadUnit, !pitchEnabled);
        Vec3d from = mc.player.getEyePos();
        float curYaw = mc.player.getYaw();
        float curPitch = mc.player.getPitch();
        float[] steps = HUMANIZED.process(
                curYaw, curPitch, from.x, from.y, from.z, aimPoint,
                System.currentTimeMillis(), getGcd(), 1.0);
        float nextYaw = curYaw + steps[0];
        float nextPitch = MathHelper.clamp(curPitch + steps[1], -90f, 90f);
        mc.player.setYaw(nextYaw);
        mc.player.setPitch(nextPitch);
        return remainingYaw(from, aimPoint, nextYaw);
    }

    private static float remainingYaw(Vec3d from, Vec3d aimPoint, float currentYaw) {
        float[] angles = HumanizedAimController.aimAngles(
                from.x, from.y, from.z, aimPoint.x, aimPoint.y, aimPoint.z, currentYaw);
        return MathHelper.wrapDegrees(angles[0] - currentYaw);
    }

    // -- Priority system --

    public static boolean tryTakeControl(Feature requester, EventSubscribe.Priority priority) {
        if (currentController == null || currentController == requester) {
            currentController = requester;
            currentPriority = priority;
            return true;
        }
        if (getPriorityValue(priority) > getPriorityValue(currentPriority)) {
            currentController = requester;
            currentPriority = priority;
            return true;
        }
        return false;
    }

    private static int getPriorityValue(EventSubscribe.Priority priority) {
        return switch (priority) {
            case HIGHEST -> 5;
            case HIGH -> 4;
            case NORMAL -> 3;
            case LOW -> 2;
            case LOWEST -> 1;
        };
    }

    public static boolean isControlledBy(Feature feature) {
        return currentController == feature;
    }

    public static boolean canTakeControl(Feature requester, EventSubscribe.Priority priority) {
        if (currentController == null || currentController == requester) return true;
        return getPriorityValue(priority) > getPriorityValue(currentPriority);
    }

    public static void releaseControl(Feature requester) {
        if (currentController == requester) {
            currentController = null;
            currentPriority = EventSubscribe.Priority.LOWEST;
        }
    }

    /**
     * Cleanup for rotation tracking. Downgrades priority, rotates back, and releases control.
     * @return true if still rotating back (call again next tick), false when cleanup is complete
     */
    public static boolean cleanup(Feature requester, boolean silent) {
        return cleanup(requester, silent, 150f, 150f, AimType.REGULAR, true);
    }

    public static boolean cleanup(Feature requester, boolean silent, float yawSpeed, float pitchSpeed, AimType aimType, boolean scaleSpeed) {
        if (!isControlledBy(requester)) return false;
        tryTakeControl(requester, EventSubscribe.Priority.LOWEST);
        if (rotateBack(silent, yawSpeed, pitchSpeed, aimType, scaleSpeed)) return true;
        releaseControl(requester);
        return false;
    }

    // -- Builder --

    public static RotationBuilder aim(Feature requester) {
        return new RotationBuilder(requester);
    }

    public static class RotationBuilder {
        private final Feature requester;
        private EventSubscribe.Priority priority = EventSubscribe.Priority.NORMAL;
        private boolean silent = false;
        private AimType aimType = AimType.REGULAR;
        private float yawSpeed = 100f;
        private float pitchSpeed = 100f;
        private EntityPoints points = EntityPoints.STRAIGHT;
        private float multipoint = 50f;
        private boolean random = false;
        private boolean throughWalls = false;
        private int predictionTicks = 0;

        RotationBuilder(Feature requester) { this.requester = requester; }

        public RotationBuilder priority(EventSubscribe.Priority p) { this.priority = p; return this; }
        public RotationBuilder silent() { this.silent = true; return this; }
        public RotationBuilder silent(boolean s) { this.silent = s; return this; }
        public RotationBuilder windMouse() { this.aimType = AimType.WINDMOUSE; return this; }
        public RotationBuilder aimType(AimType t) { this.aimType = t; return this; }
        public RotationBuilder speed(float s) { this.yawSpeed = s; this.pitchSpeed = s; return this; }
        public RotationBuilder speed(float yaw, float pitch) { this.yawSpeed = yaw; this.pitchSpeed = pitch; return this; }
        public RotationBuilder points(EntityPoints p) { this.points = p; return this; }
        public RotationBuilder multipoint(float m) { this.multipoint = m; return this; }
        public RotationBuilder random(boolean r) { this.random = r; return this; }
        public RotationBuilder throughWalls(boolean t) { this.throughWalls = t; return this; }
        public RotationBuilder predict(int ticks) { this.predictionTicks = ticks; return this; }

        public boolean to(Vec3d pos) {
            return rotateTo(pos, yawSpeed, pitchSpeed, silent, aimType, requester, priority);
        }

        public boolean to(LivingEntity target) {
            if (predictionTicks > 0) {
                Vec3d predicted = predictAimPoint(target, predictionTicks, points);
                return rotateTo(predicted, yawSpeed, pitchSpeed, silent, aimType, requester, priority);
            }
            return rotateTo(target, yawSpeed, pitchSpeed, silent, aimType, points, multipoint, random, throughWalls, requester, priority);
        }
    }

    // -- Raycast helpers (used by interact-style modules to test what the player is currently aimed at) --

    /** Casts an OUTLINE ray from the player's eye in the given direction out to vanilla block-interaction range. */
    public static BlockHitResult raycastFromRotation(float yaw, float pitch, float partialTick) {
        if (mc.player == null || mc.world == null) return null;
        Vec3d eye = mc.player.getCameraPosVec(partialTick);
        Vec3d look = RotationHandler.getRotationVector(pitch, yaw);
        Vec3d end = eye.add(look.multiply(mc.player.getBlockInteractionRange()));
        return mc.world.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player));
    }

    /** True if a ray from the eye at the given rotation passes through (a slightly inflated) {@code clickedPos}. */
    public static boolean rayIntersectsBlock(float yaw, float pitch, BlockPos clickedPos, float partialTick) {
        if (mc.player == null || clickedPos == null) return false;
        Vec3d eye = mc.player.getCameraPosVec(partialTick);
        Vec3d look = RotationHandler.getRotationVector(pitch, yaw);
        Vec3d end = eye.add(look.multiply(mc.player.getBlockInteractionRange()));
        return new Box(clickedPos).expand(1.0E-4).raycast(eye, end).isPresent();
    }
}
