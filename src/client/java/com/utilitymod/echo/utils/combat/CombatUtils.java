package com.utilitymod.echo.utils.combat;

import com.utilitymod.echo.utils.Imports;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.predicate.entity.EntityPredicates;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.component.type.AttackRangeComponent;
import net.minecraft.util.math.Box;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.MathHelper;

import java.util.List;

/**
 * Shared combat helpers.
 */
public final class CombatUtils implements Imports {
	private static final AttackRangeComponent SPEAR_ATTACK_RANGE = resolveSpearAttackRange();

	private CombatUtils() {
	}

	private static AttackRangeComponent resolveSpearAttackRange() {
		AttackRangeComponent range = Items.WOODEN_SPEAR.getDefaultStack().get(DataComponentTypes.ATTACK_RANGE);
		return range != null ? range : new AttackRangeComponent(2.0F, 4.5F, 2.0F, 6.5F, 0.125F, 0.5F);
	}

	/**
	 * Checks whether a target should continue being tracked for {@code keep target} logic.
	 */
	public static boolean isTargetValid(LivingEntity target, PlayerEntity player, double maxDistance) {
		if (target == null || player == null) {
			return false;
		}
		if (!target.isAlive() || target.isRemoved()) {
			return false;
		}
		if (!player.isAlive()) {
			return false;
		}
		return player.distanceTo(target) <= maxDistance;
	}

	/**
	 * Resolves which target should be used when {@code keep target} is enabled.
	 */
	public static LivingEntity resolveKeptTarget(LivingEntity currentTarget, LivingEntity candidate, PlayerEntity player,
                                                 boolean keepTargetEnabled, boolean manualOverride, double maxDistance) {
		if (!keepTargetEnabled) {
			return candidate;
		}

		if (manualOverride) {
			if (isTargetValid(candidate, player, maxDistance)) {
				return candidate;
			}
			return isTargetValid(currentTarget, player, maxDistance) ? currentTarget : null;
		}

		if (isTargetValid(currentTarget, player, maxDistance)) {
			return currentTarget;
		}

		return isTargetValid(candidate, player, maxDistance) ? candidate : null;
	}

	/**
	 * This rayscasting may be quite innaccurate. needs further investigation and testing.
	 * Maybe we can update teh getRotationVec to be onDelta so we can get faster vector updates?
	 */

	public static boolean isCrosshairWithinReach(PlayerEntity player, LivingEntity target, double reach) {
		if (player == null || target == null) return false;
		Vec3d cam = new Vec3d(player.getX(), player.getEyeY(), player.getZ());
		Vec3d dir = player.getRotationVec(1.0F);
		if (dir.lengthSquared() == 0) return false;
		Box box = target.getBoundingBox();

		Double t = intersectRayAABB(cam, dir, box);
		return t != null && t >= 0.0 && t <= reach;
	}

	private static Double intersectRayAABB(Vec3d origin, Vec3d dir, Box box) {
		double invX = (dir.x == 0.0 ? 1e-12 : dir.x);
		double invY = (dir.y == 0.0 ? 1e-12 : dir.y);
		double invZ = (dir.z == 0.0 ? 1e-12 : dir.z);

		double tmin = (box.minX - origin.x) / invX;
		double tmax = (box.maxX - origin.x) / invX;
		if (tmin > tmax) { double tmp = tmin; tmin = tmax; tmax = tmp; }

		double tymin = (box.minY - origin.y) / invY;
		double tymax = (box.maxY - origin.y) / invY;
		if (tymin > tymax) { double tmp = tymin; tymin = tymax; tymax = tmp; }

		if ((tmin > tymax) || (tymin > tmax)) return null;
		if (tymin > tmin) tmin = tymin;
		if (tymax < tmax) tmax = tymax;

		double tzmin = (box.minZ - origin.z) / invZ;
		double tzmax = (box.maxZ - origin.z) / invZ;
		if (tzmin > tzmax) { double tmp = tzmin; tzmin = tzmax; tzmax = tmp; }

		if ((tmin > tzmax) || (tzmin > tmax)) return null;
		if (tzmin > tmin) tmin = tzmin;
		if (tzmax < tmax) tmax = tzmax;

		if (tmin < 0.0) {
			return tmax >= 0.0 ? tmax : null;
		}
		return tmin;
	}

	public static boolean isSpearCrosshairTarget(PlayerEntity player, LivingEntity target) {
		return isSpearCrosshairTarget(player, target, getPartialTick());
	}

	public static boolean isSpearCrosshairTarget(PlayerEntity player, LivingEntity target, float tickDelta) {
		return getSpearCrosshairTarget(player, tickDelta) == target;
	}

	public static boolean hasSpearCrosshairTarget(PlayerEntity player) {
		return hasSpearCrosshairTarget(player, getPartialTick());
	}

	public static boolean hasSpearCrosshairTarget(PlayerEntity player, float tickDelta) {
		return getSpearCrosshairTarget(player, tickDelta) != null;
	}

	public static boolean hasCurrentItemCrosshairTarget(PlayerEntity player) {
		return hasCurrentItemCrosshairTarget(player, getPartialTick());
	}

	public static boolean hasCurrentItemCrosshairTarget(PlayerEntity player, float tickDelta) {
		return getCurrentItemCrosshairTarget(player, tickDelta) != null;
	}

	public static LivingEntity getSpearCrosshairTarget(PlayerEntity player) {
		return getSpearCrosshairTarget(player, getPartialTick());
	}

	public static LivingEntity getSpearCrosshairTarget(PlayerEntity player, float tickDelta) {
		return getCrosshairTarget(player, SPEAR_ATTACK_RANGE, tickDelta);
	}

	public static LivingEntity getCurrentItemCrosshairTarget(PlayerEntity player) {
		return getCurrentItemCrosshairTarget(player, getPartialTick());
	}

	private static float getPartialTick() {
		return mc.getRenderTickCounter().getTickProgress(false);
	}

	public static LivingEntity getCurrentItemCrosshairTarget(PlayerEntity player, float tickDelta) {
		if (player == null || !player.isAlive()) {
			return null;
		}

		ItemStack held = player.getMainHandStack();
		AttackRangeComponent range = held.isEmpty() ? null : held.get(DataComponentTypes.ATTACK_RANGE);
		if (range == null) {
			range = AttackRangeComponent.defaultForEntity(player);
		}

		return getCrosshairTarget(player, range, tickDelta);
	}

	private static LivingEntity getCrosshairTarget(PlayerEntity player, AttackRangeComponent range, float tickDelta) {
		if (player == null || range == null) {
			return null;
		}
		if (!player.isAlive()) {
			return null;
		}

		HitResult hitResult = range.getHitResult(player, tickDelta, EntityPredicates.CAN_HIT);
		if (!(hitResult instanceof EntityHitResult entityHitResult)) {
			return null;
		}
		if (!range.isWithinRange(player, entityHitResult.getPos())) {
			return null;
		}
		if (!(entityHitResult.getEntity() instanceof LivingEntity livingEntity)) {
			return null;
		}
		if (!livingEntity.isAlive() || livingEntity.isRemoved()) {
			return null;
		}
		if (TargetUtils.isFriend(livingEntity)) {
			return null;
		}

		return livingEntity;
	}

	/**
	 * Checks if an entity is within the player's FOV.
	 */
	public static boolean isInFOV(PlayerEntity player, LivingEntity target, float fov) {
		if (player == null || target == null) return false;
		Vec3d diff = target.getEyePos().subtract(player.getEyePos());
		double yawTo = Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90.0;
		double yawDiff = Math.abs(MathHelper.wrapDegrees((float) yawTo - player.getYaw()));
		return yawDiff <= fov / 2.0f;
	}

	/**
	 * Checks if an entity is within separate yaw and pitch FOV values.
	 */
	public static boolean isInFOV(PlayerEntity player, LivingEntity target, float yawFov, float pitchFov) {
		if (player == null || target == null) return false;
		Vec3d diff = target.getEyePos().subtract(player.getEyePos());
		double yawTo = Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90.0;
		double horizontalDistance = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
		double pitchTo = -Math.toDegrees(Math.atan2(diff.y, horizontalDistance));
		double yawDiff = Math.abs(MathHelper.wrapDegrees((float) yawTo - player.getYaw()));
		double pitchDiff = Math.abs((float) pitchTo - player.getPitch());
		return yawDiff <= yawFov / 2.0f && pitchDiff <= pitchFov / 2.0f;
	}

	/**
	 * Selects the nearest valid target from a list of entities.
	 * May be extra thinking of WorldUtils
	 */
	public static LivingEntity selectNearestTarget(PlayerEntity player, List<LivingEntity> entities) {
		if (player == null || entities == null || entities.isEmpty()) return null;
		return entities.stream()
			.min(java.util.Comparator.comparingDouble(player::distanceTo))
			.orElse(null);
	}

	public static boolean canCrit() {
		if (mc.player == null) return false;

		return !mc.player.isOnGround()
				&& !mc.player.hasVehicle()
				&& !mc.player.isClimbing()
				&& !mc.player.isTouchingWater()
				&& !mc.player.hasStatusEffect(StatusEffects.BLINDNESS) // or isMobilityRestricted()
				&& !mc.player.isGliding()
				&& mc.player.fallDistance > 0.0F;
	}
}
