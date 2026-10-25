package com.utilitymod.echo.features.impl.combat;

import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventHandleInput;
import com.utilitymod.echo.event.impl.EventStartAttack;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.settings.impl.BoolSetting;
import com.utilitymod.echo.features.settings.impl.ColorSetting;
import com.utilitymod.echo.features.settings.impl.ModeSetting;
import com.utilitymod.echo.features.settings.impl.FloatSetting;
import com.utilitymod.echo.features.settings.impl.IntSetting;
import com.utilitymod.echo.features.settings.impl.MultiModeSetting;
import com.utilitymod.echo.features.impl.movement.Freecam;
import com.utilitymod.echo.handlers.RotationHandler;
import com.utilitymod.echo.handlers.ServerRotations;
import com.utilitymod.echo.utils.EncryptedString;
import com.utilitymod.echo.utils.combat.TargetUtils;
import com.utilitymod.echo.utils.rotation.RotationUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * LegacyPort Shrike port: on a manual attack whose crosshair did not land on an
 * entity (MISS, or a block hit), scan for the closest allowed target inside a
 * yaw/pitch window around the camera, aim the wire look at the selected body part of
 * its vanilla hitbox (silent), and release the attack once that look actually
 * intersects the box - so every packet the anticheat sees is geometrically legal.
 *
 * &#64;see HitboxMod (same wire-look convergence/release pipeline; the pick law is what ported)
 */
public class Shrike extends Feature {

	private static final EncryptedString SA_NAME = new EncryptedString(
			new char[]{0xa8a2, 0x962e, 0x82ec, 0x37d0, 0x0b7c, 0x1c9b, 0x2274, 0xb17b, 0xd129, 0xbc0c},
			new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643});
	private static final EncryptedString SA_DESC = new EncryptedString(new char[]{0x1807, 0xa420, 0xb515, 0xcdf6, 0xd979, 0xb164, 0xc5cf, 0xa8bf, 0x828d, 0xf05c, 0x723e, 0x9e06, 0x0039, 0xeb84, 0xfcf3, 0x50dd, 0xe72e, 0xc960, 0x37dd, 0x7ed8, 0x93a5, 0x0b2f, 0x4305, 0x5f67, 0x11fc, 0xa1e1, 0x3b75, 0x9bf3, 0x3347, 0x04b5, 0x8ae5, 0xd2a6, 0x962a, 0x76b8, 0xdac2, 0x0cdb, 0x66fc, 0x6d67, 0x478a, 0xf414, 0xef50, 0xd13b, 0x4fb1, 0xbc08, 0x1c81, 0xac4b, 0x8676, 0xf8d8, 0xb981, 0x2615, 0x58ad, 0xc152, 0x226c, 0x7a41, 0xe0ff, 0x1589, 0x2a9a, 0x6558, 0x3ff8, 0x4a4a},
			new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643, 0x1190, 0xebe1, 0x6536, 0xfc87, 0x76d4, 0xf025, 0x4a6a, 0xc5bb, 0x5f08, 0xd959, 0x50ae, 0x2aff, 0xa44c, 0x3f9d, 0xb9e2, 0x3333, 0x8a80, 0x04d1, 0x9e26, 0x1877, 0x93c4, 0x6d15, 0xe75a, 0x7eab, 0xf8f8, 0x7249, 0xcd9e, 0x47ef, 0xc13c, 0x588d, 0xd2d2, 0xac23, 0x2670, 0xa1c1, 0x3b16, 0xb567, 0x0cb4, 0x8605, 0x004a, 0x9b9b, 0x15e8, 0xef39, 0x668e, 0xe0df, 0x7a2c, 0xf47d, 0x4fc2, 0xc913, 0x4360, 0xdab1});
	private static final EncryptedString SA_PARTS = new EncryptedString(
			new char[]{0x226a, 0xd12a, 0x37cd, 0xa8aa, 0xbc1d},
			new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be});
	private static final EncryptedString SA_HEAD = new EncryptedString(
			new char[]{0x2279, 0xa8ae, 0xd132, 0xbc0d},
			new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69});
	private static final EncryptedString SA_NECK = new EncryptedString(
			new char[]{0x227b, 0xa8ae, 0xd134, 0xbc02},
			new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69});
	private static final EncryptedString SA_BODY = new EncryptedString(
			new char[]{0x227c, 0xa8a4, 0xd138, 0xbc10},
			new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69});
	private static final EncryptedString SA_ARMS = new EncryptedString(
			new char[]{0x2275, 0xa8b9, 0xd13b, 0xbc1a},
			new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69});
	private static final EncryptedString SA_THIGHS = new EncryptedString(
			new char[]{0xa8a3, 0xb17c, 0xd12e, 0x2271, 0x37d6, 0xbc0e},
			new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f});
	private static final EncryptedString SA_LEGS = new EncryptedString(
			new char[]{0x227f, 0xa8ae, 0xd136, 0xbc1a},
			new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69});
	private static final EncryptedString SA_FEET = new EncryptedString(
			new char[]{0x227d, 0xa8ae, 0xd13c, 0xbc1d},
			new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69});
	private static final EncryptedString SA_YAWFOV = new EncryptedString(
			new char[]{0x0b0a, 0xd123, 0x37f8, 0xb140, 0xbc49, 0x226f, 0xa8aa},
			new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c});
	private static final EncryptedString SA_PITCHFOV = new EncryptedString(
			new char[]{0x0b1a, 0x1ca4, 0x82e2, 0x37d6, 0xbc0a, 0xa8a2, 0xb12f, 0x226c, 0xd12a},
			new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2});
	private static final EncryptedString SA_RANGE = new EncryptedString(
			new char[]{0x2276, 0xd128, 0x37db, 0xa8aa, 0xbc0e},
			new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be});
	private static final EncryptedString SA_LOOKSYNC = new EncryptedString(
			new char[]{0x0b25, 0x1c91, 0x82c3, 0x379e, 0xbc02, 0xa8a4, 0xb15c, 0x2277, 0xd136},
			new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2});
	private static final EncryptedString SA_RANDOM = new EncryptedString(
			new char[]{0xa8aa, 0x962e, 0x82ec, 0x37d1, 0x0b7c, 0x1c9b, 0x2276, 0xb162, 0xd128, 0xbc0d},
			new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643});
	private static final EncryptedString SA_PLAYERSONLY = new EncryptedString(
			new char[]{0xeb98, 0x0b2f, 0x1cbd, 0x962d, 0x2279, 0xd12a, 0x11fc, 0x828d, 0xa8a7, 0x37db, 0xb17d, 0xbc10},
			new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643, 0x1190, 0xebe1});
	private static final EncryptedString SA_TRACK = new EncryptedString(
			new char[]{0xeb8a, 0x0b7c, 0x1c80, 0x9622, 0x226f, 0xd13b, 0x11f3, 0x82d9, 0xa8a7, 0x37c7, 0xb17c, 0xbc08},
			new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643, 0x1190, 0xebe1});
	private static final EncryptedString SA_TRACKTIME = new EncryptedString(
			new char[]{0xa8b9, 0x9626, 0x82c4, 0x37d5, 0x0b28, 0x1c9f, 0x2279, 0xb12f, 0xd12e, 0xbc0a},
			new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643});
	private static final EncryptedString SA_DRIFT = new EncryptedString(
			new char[]{0x2271, 0xd13e, 0x37ca, 0xa8b9, 0xbc0f},
			new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be});
	private static final EncryptedString SA_LEAD = new EncryptedString(
			new char[]{0x2279, 0xa8ae, 0xd136, 0xbc0d},
			new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69});
	private static final EncryptedString SA_MOBS = new EncryptedString(
			new char[]{0x227a, 0xa8a4, 0xd137, 0xbc1a},
			new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69});
	private static final EncryptedString SA_WEAPONONLY = new EncryptedString(
			new char[]{0x11e9, 0xa8ae, 0xbc19, 0x2279, 0x1c9c, 0x82e2, 0x0b7c, 0xd12d, 0xb161, 0x962f, 0x37d1},
			new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643, 0x1190});
	private static final EncryptedString SA_SORT = new EncryptedString(
			new char[]{0x226a, 0xa8a4, 0xd129, 0xbc1d},
			new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69});
	private static final EncryptedString SA_HEALTH = new EncryptedString(
			new char[]{0xa8ae, 0xb167, 0xd132, 0x2279, 0x37ca, 0xbc05},
			new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f});
	private static final EncryptedString SA_RENDER = new EncryptedString(
			new char[]{0xa8ae, 0xb17d, 0xd128, 0x2276, 0x37db, 0xbc0d},
			new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f});
	private static final EncryptedString SA_COLOR = new EncryptedString(
			new char[]{0x2274, 0xd139, 0x37cc, 0xa8a4, 0xbc06},
			new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be});
	private static final EncryptedString SA_CLOSEST = new EncryptedString(
			new char[]{0x0b28, 0xd139, 0x37db, 0xb17c, 0xbc1a, 0x2277, 0xa8a7},
			new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c});
	private static final EncryptedString SA_FOV = new EncryptedString(
			new char[]{0x224e, 0xa884, 0xd13c},
			new char[]{0xd17a, 0xa8cb, 0x2218});

	/** LegacyPort part table: label -> fraction of hitbox height. */
	private static final CharSequence[] PART_LABELS = {
			SA_HEAD, SA_NECK, SA_BODY, SA_ARMS, SA_THIGHS, SA_LEGS, SA_FEET};
	private static final double[] PART_FRACTIONS = {0.95, 0.85, 0.70, 0.65, 0.35, 0.20, 0.05};

	public final MultiModeSetting parts = new MultiModeSetting(SA_PARTS, PART_LABELS);
	public final FloatSetting yawFov = new FloatSetting(SA_YAWFOV, 35.0f, 1.0f, 180.0f, 1.0f);
	public final FloatSetting pitchFov = new FloatSetting(SA_PITCHFOV, 25.0f, 1.0f, 90.0f, 1.0f);
	public final FloatSetting range = new FloatSetting(SA_RANGE, 3.0f, 0.5f, 6.0f, 0.1f);
	public final IntSetting lookSyncSpeed = new IntSetting(SA_LOOKSYNC, 45, 10, 200);
	public final BoolSetting randomAim = new BoolSetting(SA_RANDOM, true);
	public final BoolSetting playersOnly = new BoolSetting(SA_PLAYERSONLY, false);

	private static Shrike instance;

	/** mochi humanized-rotation motor (server look only; camera never moves). */
	private final com.utilitymod.echo.utils.rotation.HumanizedRotator rotator =
			new com.utilitymod.echo.utils.rotation.HumanizedRotator();
	private long lastStepMs = -1;

	/** Cross-feature claim: while Shrike is enabled it owns the attack law
	 *  and other features (HitboxMod's box expansion) stand down. */
	public static boolean owns() {
		return instance != null && instance.isEnabled();
	}

	public Shrike() {
		super(new FeatureInfo(SA_NAME, SA_DESC, Category.COMBAT, false));
		instance = this;
		parts.setOption(SA_HEAD, true);
		parts.setOption(SA_BODY, true);
	}

	/** Track the target continuously, not only while the attack key is held / right after a click. */
	public final BoolSetting alwaysTrack = new BoolSetting(SA_TRACK, false);
	/** How long after the last click the server look keeps tracking (ms). */
	public final IntSetting holdMs = new IntSetting(SA_TRACKTIME, 700, 100, 3000);
	/** mochi octave-noise drift amplitude on the server look (deg). */
	public final FloatSetting drift = new FloatSetting(SA_DRIFT, 0.0f, 0.0f, 0.5f, 0.01f);
	/** mochi target-motion lead fraction applied to the tracked point. */
	public final FloatSetting lead = new FloatSetting(SA_LEAD, 0.0f, 0.0f, 0.3f, 0.01f);
	/** Groov Shrike: also target mobs (players are always allowed unless Players Only is off-limited). */
	public final BoolSetting mobs = new BoolSetting(SA_MOBS, false);
	/** Groov Shrike: only aim while holding a weapon (sword, axe, mace, spear). */
	public final BoolSetting weaponOnly = new BoolSetting(SA_WEAPONONLY, true);
	/** Groov Shrike target priority. */
	public final ModeSetting sort = new ModeSetting(SA_SORT, SA_CLOSEST, SA_CLOSEST, SA_HEALTH, SA_FOV);
	/** Groov Shrike: ring/cylinder around the locked target. */
	public final BoolSetting render = new BoolSetting(SA_RENDER, false);
	public final ColorSetting renderColor = new ColorSetting(SA_COLOR, 255, 50, 50, 200);

	/** Groov target lock: keep a fresh target for LOCK_MS; drop it after LOST_MS of being invalid. */
	private static final long LOCK_MS = 2500L, LOST_MS = 500L;
	private long lockedUntil, lostSince = -1L;

	/**
	 * Server-side-only silent aim on the shared {@link ServerRotations} manager (LegacyPort model): while
	 * you fight, the look sent to the server tracks the chosen body part of the best target in the FOV
	 * window; the camera, crosshair and mouse never move. A click lands at once when the look the server
	 * already received is inside the target's box; otherwise it is held for at most {@link #MAX_PENDING_MS}
	 * and released on the input pass after the packet that put the look on target.
	 */
	private static final long MAX_PENDING_MS = 250L;
	private static final int PRIORITY = 20;

	private LivingEntity tracked;
	private Vec3d aimPoint, jitter;
	private long lastClick, jitterAt, pendingSince;
	private boolean pendingAttack;

	@Override
	public void onDisable() {
		super.onDisable();
		tracked = null; aimPoint = null; jitter = null; pendingAttack = false;
		rotator.reset();
		lastStepMs = -1;
	}

	private boolean holdingWeapon() {
		var st = mc.player.getMainHandStack();
		var it = st.getItem();
		return st.isIn(net.minecraft.registry.tag.ItemTags.SWORDS) || st.isIn(net.minecraft.registry.tag.ItemTags.AXES)
				|| it instanceof net.minecraft.item.MaceItem
				|| com.utilitymod.echo.utils.inventory.InventoryUtils.isItemSpear(it);
	}

	private boolean engaged() {
		if (weaponOnly.getValue() && !holdingWeapon()) return false;
		if (alwaysTrack.getValue()) return true;
		if (mc.options != null && mc.options.attackKey.isPressed()) return true;
		return System.currentTimeMillis() - lastClick < holdMs.getValue();
	}

	private boolean valid(LivingEntity e) {
		if (e == null || !e.isAlive() || e.isRemoved() || e == mc.player) return false;
		if (!(e instanceof PlayerEntity) && (playersOnly.getValue() || !mobs.getValue())) return false;
		if (!TargetUtils.isTargetAllowed(e)) return false;
		double r = range.getValue();
		if (mc.player.squaredDistanceTo(e) > r * r * 4.0) return false;
		return mc.player.canSee(e);
	}

	/** The look the server already has points into {@code e}'s box. */
	private boolean sentOn(LivingEntity e) {
		return e != null && ServerRotations.ownedBy(this) && ServerRotations.sentHits(e.getBoundingBox());
	}

	@EventSubscribe
	@SuppressWarnings("unused")
	public void onStartAttack(EventStartAttack event) {
		if (isNull() || com.utilitymod.echo.api.MinecraftCompat.getScreen() != null) return;
		if (Freecam.isActive()) return;
		lastClick = System.currentTimeMillis();

		LivingEntity target = valid(tracked) ? tracked : (pickTarget() instanceof LivingEntity l ? l : null);
		if (target == null) return;
		if (event.getTarget() == target && !ServerRotations.ownedBy(this)
				&& rayHitsBox(mc.player.getEyePos(), mc.player.getRotationVector(), target.getBoundingBox())) return;

		event.cancel();
		tracked = target;
		if (sentOn(target)) { directAttack(target); return; }
		pendingAttack = true;
		pendingSince = System.currentTimeMillis();
	}

	/** Once per client tick, before the movement packet: release queued hits, keep the target, request the look. */
	@EventSubscribe(priority = EventSubscribe.Priority.HIGH)
	@SuppressWarnings("unused")
	private void onHandleInputEarly(EventHandleInput.Early event) {
		if (isNull() || Freecam.isActive()) { tracked = null; return; }

		if (pendingAttack && sentOn(tracked)) {
			pendingAttack = false;
			if (valid(tracked)) directAttack(tracked);
		}
		if (pendingAttack && System.currentTimeMillis() - pendingSince > MAX_PENDING_MS) pendingAttack = false;

		if (engaged()) {
			long now = System.currentTimeMillis();
			boolean ok = valid(tracked) && withinFov(partPoint(tracked));
			if (tracked != null && !ok) {
				if (lostSince < 0) lostSince = now;
				if (now - lostSince >= LOST_MS) { tracked = null; lostSince = -1; }
			} else lostSince = -1;
			// a better candidate may only take over once the current lock expired
			if (tracked == null || (now >= lockedUntil && !ok)) {
				LivingEntity pick = pickTarget() instanceof LivingEntity l ? l : null;
				if (pick != null && pick != tracked) { tracked = pick; lockedUntil = now + LOCK_MS; lostSince = -1; }
			}
		} else { tracked = null; lostSince = -1; }
		if (tracked == null) { aimPoint = null; rotator.reset(); lastStepMs = -1; return; }

		Box box = tracked.getBoundingBox();
		Vec3d point = partPoint(tracked);
		if (randomAim.getValue()) {
			long now = System.currentTimeMillis();
			if (jitter == null || now - jitterAt > 250L) {
				jitter = RotationUtils.randomPointInBox(box).subtract(box.getCenter()).multiply(0.25);
				jitterAt = now;
			}
			point = point.add(jitter);
			point = new Vec3d(MathHelper.clamp(point.x, box.minX, box.maxX),
					MathHelper.clamp(point.y, box.minY, box.maxY), MathHelper.clamp(point.z, box.minZ, box.maxZ));
		}
		aimPoint = point;

		// mochi humanized motor owns the per-tick walk; ServerRotations applies the exact
		// look the motor picked (speed 1.0 = no extra lerp, its own easing already ran).
		long step = System.currentTimeMillis();
		float dtSec = lastStepMs < 0 ? 0.016f : (step - lastStepMs) / 1000.0f;
		if (dtSec < 0.016f) dtSec = 0.016f;
		if (dtSec > 0.25f) dtSec = 0.25f;
		lastStepMs = step;
		float maxSpeed = MathHelper.clamp(lookSyncSpeed.getValue() * 8f, 80f, 1600f);
		rotator.configure(maxSpeed, drift.getValue(), lead.getValue());
		float[] sp = rotator.step(mc.player.getYaw(), mc.player.getPitch(), mc.player.getEyePos(), point, step, dtSec, false);
		ServerRotations.request(this, sp[0], sp[1], 1.0f, PRIORITY);
	}

	/** Closest allowed target with a selected part inside the FOV window around the camera. */
	private Entity pickTarget() {
		Vec3d eye = mc.player.getEyePos();
		float pickRange = range.getValue();
		double rangeSq = pickRange * pickRange * 4.0;
		Entity best = null;
		double bestCost = Double.MAX_VALUE;
		float fovYaw = yawFov.getValue();
		float fovPitch = pitchFov.getValue();

		for (Entity entity : mc.world.getEntities()) {
			if (!(entity instanceof LivingEntity living)) continue;
			if (entity == mc.player) continue;
			if (!living.isAlive() || living.isRemoved()) continue;
			if (playersOnly.getValue() && !(living instanceof PlayerEntity)) continue;
			if (!TargetUtils.isTargetAllowed(living)) continue;
			if (mc.player.squaredDistanceTo(living) > rangeSq) continue;
			if (!mc.player.canSee(living)) continue;

			Vec3d point = partPoint(living);
			if (!withinFov(point)) continue;

			String sortMode = String.valueOf(sort.getValue());
			double cost;
			if (sortMode.contentEquals(SA_HEALTH)) {
				cost = living.getHealth() + living.getAbsorptionAmount();
			} else if (sortMode.contentEquals(SA_FOV)) {
				cost = fovOffset(point);
			} else {
				cost = eye.squaredDistanceTo(point);
			}
			if (cost < bestCost) {
				bestCost = cost;
				best = living;
			}
		}
		return best;
	}

	/** LegacyPort part-point law: clamp the eye column into the box, pick the nearest
	 *  selected part plane, return the closest point of that plane to the eye. */
	private Vec3d partPoint(Entity entity) {
		Vec3d eye = mc.player.getEyePos();
		Box box = entity.getBoundingBox();
		double height = box.maxY - box.minY;
		if (height <= 0.0) return entity.getEyePos();

		double clampedX = MathHelper.clamp(eye.x, box.minX, box.maxX);
		double clampedZ = MathHelper.clamp(eye.z, box.minZ, box.maxZ);
		double columnSq = (eye.x - clampedX) * (eye.x - clampedX) + (eye.z - clampedZ) * (eye.z - clampedZ);

		double bestSq = Double.POSITIVE_INFINITY;
		Vec3d bestPoint = null;
		for (int i = 0; i < PART_LABELS.length; i++) {
			if (!parts.isEnabled(PART_LABELS[i])) continue;
			double partY = box.minY + PART_FRACTIONS[i] * height;
			double dy = eye.y - partY;
			double cost = columnSq + dy * dy;
			if (cost < bestSq) {
				bestSq = cost;
				bestPoint = new Vec3d(clampedX, partY, clampedZ);
			}
		}
		return bestPoint != null ? bestPoint : entity.getEyePos();
	}

	/** Angular distance (yaw + pitch degrees) between the camera look and a point. */
	private double fovOffset(Vec3d point) {
		float[] r = RotationUtils.calculate(mc.player.getEyePos(), point);
		return Math.abs(MathHelper.wrapDegrees(r[0] - mc.player.getYaw())) + Math.abs(r[1] - mc.player.getPitch());
	}

	/** Groov-style cylinder around the locked target: two rings plus vertical struts. */
	@EventSubscribe
	@SuppressWarnings("unused")
	private void onRender(com.utilitymod.echo.event.impl.EventRender3D event) {
		if (!render.getValue() || tracked == null || isNull()) return;
		var out = event.getDraw3D().getMinecraftTarget();
		float pt = event.getTickDelta();
		double x = MathHelper.lerp(pt, tracked.lastRenderX, tracked.getX());
		double y = MathHelper.lerp(pt, tracked.lastRenderY, tracked.getY());
		double z = MathHelper.lerp(pt, tracked.lastRenderZ, tracked.getZ());
		double r = tracked.getWidth() * 0.5 + 0.05, h = tracked.getHeight();
		int col = renderColor.getARGB();
		int seg = 32;
		double spin = (System.currentTimeMillis() % 4000L) / 4000.0 * Math.PI * 2;
		for (int i = 0; i < seg; i++) {
			double a0 = spin + i * Math.PI * 2 / seg, a1 = spin + (i + 1) * Math.PI * 2 / seg;
			double x0 = x + Math.cos(a0) * r, z0 = z + Math.sin(a0) * r, x1 = x + Math.cos(a1) * r, z1 = z + Math.sin(a1) * r;
			out.line(x0, y, z0, x1, y, z1, col);
			out.line(x0, y + h, z0, x1, y + h, z1, col);
			if (i % 4 == 0) out.line(x0, y, z0, x0, y + h, z0, (col & 0x00FFFFFF) | 0x50000000);
		}
	}

	/** FOV window: the aim point must be within yawFov/pitchFov of the camera look. */
	private boolean withinFov(Vec3d point) {
		Vec3d eye = mc.player.getEyePos();
		double dx = point.x - eye.x;
		double dy = point.y - eye.y;
		double dz = point.z - eye.z;
		float yawTo = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float pitchTo = (float) Math.toDegrees(Math.atan2(-dy, Math.sqrt(dx * dx + dz * dz)));
		float dYaw = Math.abs(MathHelper.wrapDegrees(yawTo - mc.player.getYaw()));
		float dPitch = Math.abs(pitchTo - mc.player.getPitch());
		return dYaw <= yawFov.getValue() && dPitch <= pitchFov.getValue();
	}

	private static boolean rayHitsBox(Vec3d eye, Vec3d look, Box box) {
		if (eye == null || look.lengthSquared() < 1.0e-8) return false;
		double len = Math.max(mc.player.getEntityInteractionRange(), 3.0f) + 4.0;
		java.util.Optional<Vec3d> hit = box.expand(1.0e-3).raycast(eye, eye.add(look.multiply(len)));
		if (hit.isEmpty()) return false;
		double maxDist = mc.player.getEntityInteractionRange() + 1.0e-3;
		return hit.get().squaredDistanceTo(eye) <= maxDist * maxDist;
	}

	/** Vanilla entity-click branch replay (AttackSetting             pipeline; crosshair set first). */
	private static void directAttack(Entity target) {
		try {
			mc.crosshairTarget = new EntityHitResult(target);
			mc.targetedEntity = target;
			com.utilitymod.echo.api.Accessors.invokeStartAttack();
		} catch (Throwable t) {
			if (mc.interactionManager != null) {
				mc.interactionManager.attackEntity(mc.player, target);
				mc.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
			}
		}
	}
}
