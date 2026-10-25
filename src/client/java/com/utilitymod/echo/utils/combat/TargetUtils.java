package com.utilitymod.echo.utils.combat;

import com.utilitymod.echo.Echo;
import com.utilitymod.echo.features.impl.misc.TargetControlModule;
import com.utilitymod.echo.features.impl.misc.Teams;
import com.utilitymod.echo.utils.Imports;
import com.utilitymod.echo.utils.combat.bots.BotChecks;
import com.utilitymod.echo.utils.player.PlayerUtils;
import com.utilitymod.echo.utils.strings.Concat;
import com.utilitymod.echo.utils.world.WorldUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

public class TargetUtils implements Imports {

    private static LivingEntity keptTarget;
    private static LivingEntity lastAttackedTarget;
    private static int lastAttackedTick;

    private TargetUtils() {
    }

    public static void onAttack(Entity entity) {
        if (mc.player == null) {
            return;
        }

        if (!(entity instanceof LivingEntity living)) {
            return;
        }

        lastAttackedTarget = living;
        lastAttackedTick = mc.player.age;

        if (!isTargetAllowed(living)) {
            return;
        }

        keptTarget = living;
    }

    public static boolean isFriend(net.minecraft.entity.Entity entity) {
        return entity instanceof net.minecraft.entity.player.PlayerEntity p && p != mc.player
                && PlayerUtils.isFriend(p.getName().getString());
    }

    /** A friend within {@code radius} blocks of {@code pos} (explosion guard for crystals / carts / anchors). */
    public static boolean friendNear(net.minecraft.util.math.Vec3d pos, double radius) {
        if (mc.world == null || pos == null) return false;
        double r2 = radius * radius;
        for (net.minecraft.entity.player.PlayerEntity p : mc.world.getPlayers()) {
            if (isFriend(p) && p.squaredDistanceTo(pos) <= r2) return true;
        }
        return false;
    }

    public static boolean isTargetAllowed(LivingEntity entity) {
        if (entity == null) {
            return false;
        }

        if (!entity.isAlive() || entity.isRemoved() || entity.isDead()) {
            return false;
        }

        if (mc.player == null) {
            return false;
        }

        // friends are never targets, whatever the Targets module is set to
        if (isFriend(entity)) {
            return false;
        }

        TargetControlModule targets = getTargets();
        if (targets == null) {
            return true;
        }

        var entityTypes = targets.entityTypes;
        boolean anyEnabled = false;
        for (Boolean enabled : entityTypes.getOptionsSequenceView().values()) {
            if (!enabled) {
                continue;
            }

            anyEnabled = true;
            break;
        }

        if (!anyEnabled) {
            return true;
        }

        if (WorldUtils.isNaked(entity) && !entityTypes.isEnabled(Concat.of(LT.L1))) {
            return false;
        }

        if (WorldUtils.isPlayer(entity)) {
            return isPlayerTargetAllowed(entity, targets);
        }

        if (entity.isInvisible() && !entityTypes.isEnabled(Concat.of(LT.L2))) {
            return false;
        }

        if (WorldUtils.isHostile(entity)) {
            return entityTypes.isEnabled(Concat.of(LT.L3));
        }

        if (WorldUtils.isPassive(entity)) {
            return entityTypes.isEnabled(Concat.of(LT.L4));
        }

        if (WorldUtils.isNeutral(entity)) {
            return entityTypes.isEnabled(Concat.of(LT.L5));
        }

        return false;
    }

    public static LivingEntity resolveTarget(LivingEntity candidate) {
        if (!isKeepTargetEnabled()) {
            return candidate;
        }

        if (isLastAttackedModeEnabled()) {
            return resolveLastAttackedTarget();
        }

        if (isKeptTargetValid(keptTarget)) {
            return keptTarget;
        }

        if (!isKeptTargetValid(candidate)) {
            keptTarget = null;
            return null;
        }

        keptTarget = candidate;
        return keptTarget;
    }

    public static LivingEntity findClosestResolvedTarget(Vec3d origin, float partialTick) {
        if (origin == null || mc.world == null || mc.player == null) {
            return null;
        }

        LivingEntity bestTarget = null;
        double bestDistSq = Double.MAX_VALUE;

        for (var entity : mc.world.getEntities()) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (entity == mc.player) continue;
            if (!isTargetAllowed(living)) continue;

            living = resolveTarget(living);
            if (living == null) continue;

            double distSq = origin.squaredDistanceTo(living.getLerpedPos(partialTick));
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                bestTarget = living;
            }
        }

        return bestTarget;
    }

    public static void setKeptTarget(LivingEntity target) {
        if (!isKeptTargetValid(target)) {
            return;
        }

        keptTarget = target;
    }

    public static void clearKeptTarget() {
        keptTarget = null;
    }

    public static void clearTrackedTargets() {
        keptTarget = null;
        lastAttackedTarget = null;
        lastAttackedTick = 0;
    }

    public static LivingEntity getLastAttackedTarget(int ticks) {
        if (mc.player == null) {
            return null;
        }

        if (!isLastAttackedStateValid()) {
            return null;
        }

        if (mc.player.age - lastAttackedTick > ticks) {
            return null;
        }

        return lastAttackedTarget;
    }

    public static boolean isBot(LivingEntity entity) {
        TargetControlModule targets = getTargets();
        if (targets == null) {
            return false;
        }

        return isBot(entity, targets);
    }

    private static LivingEntity resolveLastAttackedTarget() {
        LivingEntity attackedTarget = getStoredLastAttackedTarget();
        if (attackedTarget != null) {
            keptTarget = attackedTarget;
        }

        if (isKeptTargetValid(keptTarget)) {
            return keptTarget;
        }

        keptTarget = null;
        return null;
    }

    private static LivingEntity getStoredLastAttackedTarget() {
        if (!isLastAttackedStateValid()) {
            return null;
        }

        return lastAttackedTarget;
    }

    private static boolean isLastAttackedStateValid() {
        if (lastAttackedTarget == null) {
            return false;
        }

        if (mc.player == null) {
            return false;
        }

        if (lastAttackedTarget.getEntityWorld() != mc.world) {
            return false;
        }

        if (!lastAttackedTarget.isAlive() || lastAttackedTarget.isRemoved()) {
            return false;
        }

        return isTargetAllowed(lastAttackedTarget);
    }

    private static boolean isKeptTargetValid(LivingEntity target) {
        if (target == null || mc.player == null) {
            return false;
        }

        if (!target.isAlive() || target.isRemoved()) {
            return false;
        }

        if (target.getEntityWorld() != mc.world) {
            return false;
        }

        if (mc.player.distanceTo(target) > getKeepTargetRange()) {
            return false;
        }

        return isTargetAllowed(target);
    }

    private static double getKeepTargetRange() {
        TargetControlModule targets = getTargets();
        if (targets == null) {
            return 16.0;
        }

        return targets.keepTargetRange.getValue();
    }

    private static boolean isKeepTargetEnabled() {
        TargetControlModule targets = getTargets();
        if (targets == null) {
            return false;
        }

        return targets.keepTarget.getValue();
    }

    private static boolean isLastAttackedModeEnabled() {
        TargetControlModule targets = getTargets();
        if (targets == null) {
            return false;
        }

        if (!targets.keepTarget.getValue()) {
            return false;
        }

        return targets.lastAttacked.getValue();
    }

    private static boolean isPlayerTargetAllowed(LivingEntity entity, TargetControlModule targets) {
        if (!targets.entityTypes.isEnabled(Concat.of(LT.L6))) {
            return false;
        }

        if (targets.botCheck.getValue() && isBot(entity, targets)) {
            return false;
        }

        if (targets.ignoreTeams.getValue()) {
            Teams teams = Echo.featureManager.getFeatureByClass(Teams.class);
            if (teams != null && teams.isOnSameTeam(entity)) {
                return false;
            }
        }

        if (targets.ignoreFriends.getValue() && PlayerUtils.isFriend(entity.getName().getString())) {
            return false;
        }

        return true;
    }

    private static boolean isBot(LivingEntity entity, TargetControlModule targets) {
        if (!(entity instanceof PlayerEntity player)) {
            return false;
        }

        return BotChecks.isBot(player, targets);
    }

    private static TargetControlModule getTargets() {
        if (Echo.featureManager == null) {
            return null;
        }

        return Echo.featureManager.getFeatureByClass(TargetControlModule.class);
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb8c0,0x7c39,0x0aca,0xd420,0xd2da}, new char[]{0xd294,0xb8a1,0x0aa1,0xd445,0x7c5d}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5f17,0xc971,0x04c3,0xbc12,0x08ce,0x0c01,0xe593,0xcd48,0xacaf}, new char[]{0x0887,0x04ad,0xe5e5,0xbc7b,0x5f64,0x0c68,0xaccd,0xcd24,0xc914}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8469,0x9043,0x1a6a,0xcfe3,0x1849,0x4d43,0xe75f}, new char[]{0x4d0b,0x902c,0x183a,0x1a1e,0xcf8a,0xe733,0x840c}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x215d,0x5238,0xc63b,0xbcc8,0xcce2,0xe314,0x72b9}, new char[]{0x210d,0xe375,0x72ca,0xc648,0xbca1,0x524e,0xcc87}).plainString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x850f,0x3a78,0x2593,0x792e,0xa9f4,0x7f59,0xf230}, new char[]{0x7960,0x25f6,0x857a,0xa980,0x7f2b,0xf251,0x3a14}).plainString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2394,0x9c2b,0xb655,0x8cf8,0x3578,0x316b,0x6ea1}, new char[]{0x3528,0xb639,0x9c4a,0x6ed8,0x310e,0x8c8a,0x23e7}).plainString();
    }
}
