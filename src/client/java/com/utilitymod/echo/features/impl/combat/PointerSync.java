package com.utilitymod.echo.features.impl.combat;

import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventMove;
import com.utilitymod.echo.event.impl.MouseUpdateEvent;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.settings.impl.BoolSetting;
import com.utilitymod.echo.features.settings.impl.FloatSetting;
import com.utilitymod.echo.features.settings.impl.ItemPickerSetting;
import com.utilitymod.echo.features.settings.impl.ModeSetting;
import com.utilitymod.echo.features.settings.impl.RangeSetting;
import com.utilitymod.echo.handlers.InputHandler;
import com.utilitymod.echo.handlers.impl.HurtTickHandler;
import com.utilitymod.echo.utils.EncryptedString;
import com.utilitymod.echo.utils.combat.CombatUtils;
import com.utilitymod.echo.utils.combat.TargetUtils;
import com.utilitymod.echo.utils.rotation.RotationUtils;
import com.utilitymod.echo.utils.strings.Concat;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * Port of the targeting shell (internal reference build.PointerSync),
 * taken from echo-main 1.21.11, 1:1.
 */
public final class PointerSync extends Feature {

    // -- Name carriers (generated, canonical codec) --
    private static final EncryptedString K_AURA_GUIDE = new EncryptedString(new char[]{0xa8be, 0x9626, 0x82c4, 0x379e, 0x0b29, 0x1c96, 0x226a, 0xb148, 0xd13b, 0xbc08}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643});
    private static final EncryptedString K_DESCRIPTION = new EncryptedString(new char[]{0x3ffc, 0xbc06, 0xd13b, 0xb990, 0x04a5, 0xb16e, 0x0b28, 0x8ae5, 0xd938, 0x2adf, 0xfca7, 0x9e55, 0x4a07, 0xeb8d, 0x9622, 0x654f, 0x226c, 0xa438, 0xc5c8, 0x3354, 0x1c91, 0x50da, 0x37d3, 0x5f28, 0xf04c, 0x11fc, 0x82c4, 0xa8be, 0x76b5}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643, 0x1190, 0xebe1, 0x6536, 0xfc87, 0x76d4, 0xf025, 0x4a6a, 0xc5bb, 0x5f08, 0xd959, 0x50ae, 0x2aff, 0xa44c, 0x3f9d, 0xb9e2, 0x3333, 0x8a80, 0x04d1, 0x9e26});
    private static final EncryptedString K_AIM_MATH = new EncryptedString(new char[]{0x2275, 0xd13b, 0xb16e, 0x0b28, 0xbc49, 0x37f3, 0xa8a2, 0x82c5}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad});
    private static final EncryptedString K_AIM_VECTOR = new EncryptedString(new char[]{0xa8a2, 0x9631, 0x82d9, 0x37e8, 0x0b3f, 0x1c9d, 0x2275, 0xb16a, 0xd13b, 0xbc49}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643});
    private static final EncryptedString K_TARGET_PRIORITY = new EncryptedString(new char[]{0x82fd, 0x76ad, 0x0b7c, 0xeb93, 0x962a, 0xa8aa, 0x37db, 0x1c80, 0xfcf3, 0xbc0e, 0xb17b, 0xd12e, 0x655f, 0x226a, 0x11ff}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643, 0x1190, 0xebe1, 0x6536, 0xfc87, 0x76d4});
    private static final EncryptedString K_RANDOM = new EncryptedString(new char[]{0xa8aa, 0xb162, 0xd128, 0x2276, 0x37d1, 0xbc0d}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f});
    private static final EncryptedString K_SILENT_ROTATIONS = new EncryptedString(new char[]{0x1c9d, 0x9637, 0xbc0c, 0x11f1, 0x76ba, 0x0b7c, 0xeb95, 0x37d0, 0xf056, 0xd129, 0x82ff, 0x2274, 0xfce8, 0xa8a2, 0xb17b, 0x655f}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643, 0x1190, 0xebe1, 0x6536, 0xfc87, 0x76d4, 0xf025});
    private static final EncryptedString K_THROUGH_WALLS = new EncryptedString(new char[]{0x226a, 0x9622, 0x11fc, 0xeb8d, 0x828d, 0x0b34, 0xa8a3, 0xbc06, 0x1ca5, 0xd12e, 0x37cb, 0x6545, 0xb168}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643, 0x1190, 0xebe1, 0x6536});
    private static final EncryptedString K_HORIZONTAL = new EncryptedString(new char[]{0xa8a4, 0x962f, 0x82d9, 0x37c4, 0x0b32, 0x1c93, 0x226a, 0xb160, 0xd132, 0xbc00}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643});
    private static final EncryptedString K_VERTICAL = new EncryptedString(new char[]{0x226a, 0xd12c, 0xb16c, 0x0b3d, 0xbc1d, 0x37d7, 0xa8ae, 0x82c1}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad});
    private static final EncryptedString K_ON_CLICK = new EncryptedString(new char[]{0x2238, 0xd135, 0xb166, 0x0b3f, 0xbc2a, 0x37d2, 0xa8a5, 0x82c6}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad});
    private static final EncryptedString K_RANGE = new EncryptedString(new char[]{0x2276, 0xd128, 0x37db, 0xa8aa, 0xbc0e}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be});
    private static final EncryptedString K_FOV_MODE = new EncryptedString(new char[]{0x224e, 0xd13c, 0xb160, 0x0b38, 0xbc49, 0x37f3, 0xa884, 0x82c8}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad});
    private static final EncryptedString K_FOV = new EncryptedString(new char[]{0x224e, 0xa884, 0xd13c}, new char[]{0xd17a, 0xa8cb, 0x2218});
    private static final EncryptedString K_YAW_FOV = new EncryptedString(new char[]{0x0b0a, 0xd123, 0x37f8, 0xb140, 0xbc49, 0x226f, 0xa8aa}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c});
    private static final EncryptedString K_PITCH_FOV = new EncryptedString(new char[]{0x0b1a, 0x1ca4, 0x82e2, 0x37d6, 0xbc0a, 0xa8a2, 0xb12f, 0x226c, 0xd12a}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2});
    private static final EncryptedString K_MULTIPOINT = new EncryptedString(new char[]{0xa8be, 0x9637, 0x82c4, 0x37d7, 0x0b33, 0x1c9c, 0x2274, 0xb17f, 0xd137, 0xbc1d}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643});
    private static final EncryptedString K_ITEM_WHITELIST = new EncryptedString(new char[]{0xd133, 0x0b34, 0xb158, 0x379e, 0x9626, 0x227d, 0xeb88, 0xfcf3, 0x82c4, 0xbc04, 0xa8bf, 0x1c86, 0x11fc, 0x6545}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643, 0x1190, 0xebe1, 0x6536, 0xfc87});

    public PointerSync() {
        super(new FeatureInfo(
                K_AURA_GUIDE,
                K_DESCRIPTION,
                Category.COMBAT
        ));
    }

    // -- Settings --

    private final ModeSetting aimMath = new ModeSetting(K_AIM_MATH,
            LT.L1,  // Regular
            LT.L1,  // Regular
            LT.L2,  // Blatant
            LT.L3); // WindMouse
    private final ModeSetting aimVector = new ModeSetting(K_AIM_VECTOR,
            LT.L4,  // Straight
            LT.L4,  // Straight
            LT.L5); // Closest
    private final ModeSetting targetPriority = new ModeSetting(K_TARGET_PRIORITY,
            LT.L6,  // Nearest
            LT.L6,  // Nearest
            LT.L7,  // FOV
            LT.L8); // HurtTick
    private final BoolSetting random = new BoolSetting(K_RANDOM, false);
    private final BoolSetting silent = new BoolSetting(K_SILENT_ROTATIONS, false);
    private final BoolSetting throughWalls = new BoolSetting(K_THROUGH_WALLS, false);
    private final RangeSetting horizontalSpeed = new RangeSetting(K_HORIZONTAL, 30f, 50f, 0f, 300f, 1f);
    private final RangeSetting verticalSpeed = new RangeSetting(K_VERTICAL, 30f, 50f, 0f, 300f, 1f);
    {
        silent.onChanged(() -> {
            float newUpperBound = silent.getValue() ? 600f : 300f;
            horizontalSpeed.setUpperBound(newUpperBound);
            verticalSpeed.setUpperBound(newUpperBound);
            if (horizontalSpeed.getMaxValue() > newUpperBound) {
                horizontalSpeed.setMaxValue(newUpperBound);
            }
            if (horizontalSpeed.getMinValue() > newUpperBound) {
                horizontalSpeed.setMinValue(newUpperBound);
            }
            if (verticalSpeed.getMaxValue() > newUpperBound) {
                verticalSpeed.setMaxValue(newUpperBound);
            }
            if (verticalSpeed.getMinValue() > newUpperBound) {
                verticalSpeed.setMinValue(newUpperBound);
            }
        });
    }
    private final BoolSetting onClickBoolSetting = new BoolSetting(K_ON_CLICK, false);
    private final FloatSetting range = new FloatSetting(K_RANGE, 6.0f, 1.0f, 50.0f, 0.1f);
    private final ModeSetting fovMode = new ModeSetting(K_FOV_MODE,
            LT.L9,  // Single
            LT.L9,  // Single
            LT.L10); // Split
    private final FloatSetting fov = new FloatSetting(K_FOV, 90.0f, 0.0f, 360.0f, 1f, o -> fovMode.is(LT.L9));
    private final FloatSetting yawFov = new FloatSetting(K_YAW_FOV, 90.0f, 0.0f, 360.0f, 1f, o -> fovMode.is(LT.L10));
    private final FloatSetting pitchFov = new FloatSetting(K_PITCH_FOV, 90.0f, 0.0f, 180.0f, 1f, o -> fovMode.is(LT.L10));
    private final FloatSetting multipoint = new FloatSetting(K_MULTIPOINT, 50.0f, 0.0f, 100.0f, 1f);
    private final ItemPickerSetting itemWhitelist = new ItemPickerSetting(K_ITEM_WHITELIST);

    @Override
    public CharSequence concat() {
        return aimMath.getValue();
    }

    @Override
    public void onDisable() {
        super.onDisable();
        RotationUtils.resetState();
    }

    @EventSubscribe
    private void onRender(MouseUpdateEvent event) {
        if (isNull() || com.utilitymod.echo.api.MinecraftCompat.getScreen() != null) {
            RotationUtils.rotateBack(true, horizontalSpeed.getRandom(), verticalSpeed.getRandom(), getAimType(), false);
            return;
        }

        if (!isHoldingAllowed()) {
            // Dont disengage rots if maceswap is active...
            if (silent.getValue() &&
            RotationUtils.isTracking() &&
            RotationUtils.isControlledBy(this) &&
            (MaceSwap.isSwapActive() || SpearReachModule.isSwapActive())) {
                return;
            }
            disengageRotations();
            return;
        }

        // On-click check
        boolean attackPressed = InputHandler.isBindDown(mc.options.attackKey);
        if (onClickBoolSetting.getValue() && !attackPressed) {
            disengageRotations();
            return;
        }

        // Find valid targets - filter by distance first (cheaper) before expensive FOV calculation
        double rangeValue = range.getValue();
        double rangeSquared = rangeValue * rangeValue;
        List<LivingEntity> validTargets = mc.world.getEntitiesByClass(
            LivingEntity.class,
            mc.player.getBoundingBox().expand(rangeValue),
            e -> e != mc.player && !e.isDead() && e.isAlive()
        ).stream()
            .filter(TargetUtils::isTargetAllowed)
            .filter(e -> mc.player.squaredDistanceTo(e) <= rangeSquared)
            .filter(this::isTargetInFov)
            .toList();

        LivingEntity candidateTarget = selectPriorityTarget(validTargets);
        LivingEntity target = TargetUtils.resolveTarget(candidateTarget);

        if (target == null || mc.player.squaredDistanceTo(target) > rangeSquared) {
            disengageRotations();
            return;
        }

        if (!isTargetInFov(target)) {
            disengageRotations();
            return;
        }

        boolean rotationSuccess = RotationUtils.aim(this)
            .speed(horizontalSpeed.getRandom(), verticalSpeed.getRandom())
            .silent(silent.getValue())
            .aimType(getAimType())
            .points(getPointsMode())
            .multipoint(multipoint.getValue())
            .random(random.getValue())
            .throughWalls(throughWalls.getValue())
            .predict(0)
            .to(target);

        if (!rotationSuccess) {
            disengageRotations();
        }
    }

    private void disengageRotations() {
        if (silent.getValue() && RotationUtils.isTracking() && RotationUtils.isControlledBy(this)) {
            RotationUtils.rotateBack(true, horizontalSpeed.getRandom(), verticalSpeed.getRandom(), getAimType(), false);
        } else if (RotationUtils.isControlledBy(this)) {
            RotationUtils.stopTracking();
        }
    }

    private RotationUtils.AimType getAimType() {
        if (aimMath.is(LT.L3)) return RotationUtils.AimType.WINDMOUSE;
        return aimMath.is(LT.L2) ? RotationUtils.AimType.BLATANT : RotationUtils.AimType.REGULAR;
    }

    private RotationUtils.EntityPoints getPointsMode() {
        if (aimVector.is(LT.L5)) return RotationUtils.EntityPoints.CLOSEST;
        return RotationUtils.EntityPoints.STRAIGHT;
    }

    private boolean isTargetInFov(LivingEntity entity) {
        if (fovMode.is(LT.L10)) {
            return CombatUtils.isInFOV(mc.player, entity, yawFov.getValue(), pitchFov.getValue());
        }
        return CombatUtils.isInFOV(mc.player, entity, fov.getValue());
    }

    private LivingEntity selectPriorityTarget(List<LivingEntity> targets) {
        if (targets == null || targets.isEmpty()) {
            return null;
        }

        if (targetPriority.is(LT.L8)) {
            return targets.stream()
                .min(java.util.Comparator
                    .comparingLong(HurtTickHandler::getAttackedEntityTick)
                    .thenComparingDouble(e -> mc.player.squaredDistanceTo(e))
                    .thenComparingDouble(this::getFovDelta)
                )
                .orElse(null);
        }

        if (targetPriority.is(LT.L7)) {
            return targets.stream()
                .min(java.util.Comparator
                    .comparingDouble(this::getFovDelta)
                    .thenComparingDouble(e -> mc.player.squaredDistanceTo(e))
                )
                .orElse(null);
        }

        return CombatUtils.selectNearestTarget(mc.player, targets);
    }

    private double getFovDelta(LivingEntity entity) {
        float pt = mc.getRenderTickCounter().getTickProgress(false);
        Vec3d diff = entity.getCameraPosVec(pt).subtract(mc.player.getCameraPosVec(pt));
        double horizontalDistance = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
        float targetYaw = (float) (Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90.0);
        float targetPitch = (float) (-Math.toDegrees(Math.atan2(diff.y, horizontalDistance)));
        float yawDiff = Math.abs(MathHelper.wrapDegrees(targetYaw - mc.player.getYaw()));
        float pitchDiff = Math.abs(targetPitch - mc.player.getPitch());
        return Math.sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff);
    }

    private boolean isHoldingAllowed() {
        if (isNull()) return false;
        if (itemWhitelist.getSelectedCount() == 0) return true;

        ItemStack mainHand = mc.player.getMainHandStack();
        if (mainHand.isEmpty()) {
            return itemWhitelist.isSelected(Items.AIR);
        }

        return itemWhitelist.isSelected(mainHand.getItem());
    }

    @EventSubscribe
    private void onMove(EventMove.Pre event) {
        if (!silent.getValue() || isNull()) return;
        if (!RotationUtils.hasSilentRotation()) return;

        event.setYaw(RotationUtils.getSilentYaw());
        event.setPitch(RotationUtils.getSilentPitch());
    }

    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final EncryptedString L1 = new EncryptedString(new char[]{0x0b2e, 0xd128, 0x37d2, 0xb16e, 0xbc1c, 0x227f, 0xa8ae}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c}); // Regular
        static final EncryptedString L2 = new EncryptedString(new char[]{0x0b28, 0xd138, 0x37df, 0xb161, 0xbc1d, 0x2279, 0xa8a7}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c}); // Blatant
        static final EncryptedString L3 = new EncryptedString(new char[]{0x0b29, 0x1c97, 0x82de, 0x37f3, 0xbc0d, 0xa8a2, 0xb160, 0x2276, 0xd12d}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2}); // WindMouse
        static final EncryptedString L4 = new EncryptedString(new char[]{0x226a, 0xd129, 0xb168, 0x0b34, 0xbc08, 0x37d7, 0xa8bf, 0x82d9}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad}); // Straight
        static final EncryptedString L5 = new EncryptedString(new char[]{0x0b28, 0xd139, 0x37db, 0xb17c, 0xbc1a, 0x2277, 0xa8a7}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c}); // Closest
        static final EncryptedString L6 = new EncryptedString(new char[]{0x0b28, 0xd134, 0x37db, 0xb17c, 0xbc1b, 0x2279, 0xa8ae}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c}); // Nearest
        static final EncryptedString L7 = new EncryptedString(new char[]{0x224e, 0xa884, 0xd13c}, new char[]{0xd17a, 0xa8cb, 0x2218}); // FOV
        static final EncryptedString L8 = new EncryptedString(new char[]{0x226a, 0xd132, 0xb166, 0x0b3f, 0xbc1d, 0x37ea, 0xa8be, 0x82c6}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad}); // HurtTick
        static final EncryptedString L9 = new EncryptedString(new char[]{0xa8a2, 0xb16a, 0xd129, 0x2276, 0x37d2, 0xbc0e}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f}); // Single
        static final EncryptedString L10 = new EncryptedString(new char[]{0x2274, 0xd129, 0x37ca, 0xa8bb, 0xbc00}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be}); // Split
    }
}
