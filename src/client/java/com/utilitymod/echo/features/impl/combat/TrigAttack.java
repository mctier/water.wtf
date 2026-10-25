package com.utilitymod.echo.features.impl.combat;

import com.utilitymod.echo.Echo;
import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventHandleInput;
import com.utilitymod.echo.event.impl.EventPacketReceive;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.settings.impl.*;
import com.utilitymod.echo.handlers.InputHandler;
import com.utilitymod.echo.handlers.impl.HurtTickHandler;
import com.utilitymod.echo.utils.combat.CombatUtils;
import com.utilitymod.echo.utils.combat.TargetUtils;
import com.utilitymod.echo.utils.inventory.InventoryUtils;
import com.utilitymod.echo.utils.math.TimerUtils;
import com.utilitymod.echo.utils.player.PlayerIntersectionUtil;
import com.utilitymod.echo.utils.player.PlayerUtils;
import com.utilitymod.echo.utils.strings.Concat;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import com.utilitymod.echo.utils.EncryptedString;
public class TrigAttack extends Feature {
    private static final CharSequence EMPTY = Concat.of();

    private final ModeSetting critMode = new ModeSetting(
            Concat.of(LT.L1),
            Concat.of(LT.L2),
            Concat.of(LT.L2),
            Concat.of(LT.L3),
            Concat.of(LT.L4)
    );
    private final BoolSetting requireSpaceToCrit = new BoolSetting(new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6516,0xa8b9,0xfcf4,0x76a4,0xf044,0xeb8f,0x9663,0x379e,0x82c1,0xb160,0xbc1d,0x4a09,0x1c8b,0xc5de,0x2271,0xd139,0x11ff,0x0b32}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87,0x76d4,0xf025,0x4a6a,0xc5bb}), false);
    private final BoolSetting attackShieldedTargets = new BoolSetting(new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd13b,0x0b7c,0xb164,0x37dd,0x962a,0x226c,0xeb8d,0xfcf4,0x82fe,0xbc08,0xa8bf,0x1c9a,0x11f5,0x6552}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87}), true);
    private final BoolSetting hurtTimeCheck = new BoolSetting(new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd132,0x0b31,0xb166,0x37ea,0x9600,0x226a,0xeb84,0xfcec,0x82c8,0xbc1d,0xa8be,0x1cd2,0x11f8,0x6555}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87}), true);
    private final ItemPickerSetting itemWhitelist = new ItemPickerSetting(new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd133,0x0b34,0xb158,0x379e,0x9626,0x227d,0xeb88,0xfcf3,0x82c4,0xbc04,0xa8bf,0x1c86,0x11fc,0x6545}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87}));
    private final RangeSetting attackCooldown = new RangeSetting(new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2277,0xd139,0xb160,0x0b2b,0xbc05,0x37da,0xa8a4,0x82c3}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad}), 90, 100, 0, 100, 1);
    private final RangeSetting critCooldown = new RangeSetting(new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2271,0x9627,0x11ff,0xeb96,0x82c2,0x0b33,0xa8b9,0xbc1d,0x1c9e,0xd139,0x379e,0x6558,0xb14c}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536}), 70, 89, 0, 100, 1);
    private final RangeSetting allowedCritRange = new RangeSetting(new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa8b9,0x9626,0x82c3,0x379e,0x0b3d,0x1c95,0x2271,0xb15d,0xd139,0xbc1d}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643}), 1.0f, 2.2f, 0f, 6f, 0.1f);
    private final IntSetting reactionDelay = new IntSetting(new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd128,0x0b33,0xb166,0x37ca,0x9607,0x2279,0xeb8d,0xfcfe,0x82c3,0xbc0a,0xa8ae,0x1cd2,0x11f5,0x6557}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87}), 0, 0, 500);
    private final IntSetting delayAttack = new IntSetting(dev.potato.spud.api.ProtectedText.fromInts(new int[]{0x5F,0x5D,0x04,0xDA,0xA1,0x66,0x74,0xDB,0xC3,0x9D,0x59,0x3A}), 0, 0, 500);
    private final BoolSetting requireClick = new BoolSetting(new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6555,0xa8ae,0xfcec,0x76f4,0xf06e,0xeb80,0x9637,0x37d7,0x828d,0xb17d,0xbc1c,0x4a0f,0x1cb3,0xc5c2,0x2269,0xd128,0x11e4,0x0b39}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87,0x76d4,0xf025,0x4a6a,0xc5bb}), false);
    private final BoolSetting unshield = new BoolSetting(new com.utilitymod.echo.utils.EncryptedString(new char[]{0x226b,0xd12f,0xb16a,0x0b30,0xbc01,0x37d7,0xa8a5,0x82c9}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad}), true);
    private final com.utilitymod.echo.features.settings.impl.InputSimulationSetting clickSim = new com.utilitymod.echo.features.settings.impl.InputSimulationSetting(dev.potato.spud.api.ProtectedText.fromInts(new int[]{0x5D,0x45,0x19,0xD8,0xA9,0x2D,0x07,0xF6,0xCB,0x84,0x54,0x22,0xFE,0xBC,0x73,0x49}), false);

    private final BoolSetting debug = new BoolSetting(new com.utilitymod.echo.utils.EncryptedString(new char[]{0x227a,0xd13e,0x37d9,0xa8ae,0xbc1c}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be}), false);
    private String reason;
    private long lastDebug;

    private final TimerUtils reactionTimer = new TimerUtils();
    private final TimerUtils attackTimer = new TimerUtils();
    private final TimerUtils pSync = new TimerUtils();
    
    private boolean unblocked;
    private boolean shouldReblockShield = false;
    private boolean hasValidTarget = false;
    private boolean shouldAttack = false;
    
    private long lastAttackTick = -1;
    private LivingEntity targetToAttack = null;

    public TrigAttack() {
        super(new FeatureInfo(
                dev.potato.spud.api.ProtectedText.fromInts(new int[]{0x4A,0x5B,0x19,0xDC,0xA5,0x68,0x26,0xBF,0xE4,0x9E,0x4C}),
                new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd932,0x58ff,0x8aa0,0xa8be,0xc5da,0x1c91,0xbc06,0x479b,0xb987,0x335d,0xa1b2,0x76b5,0x04b0,0x2604,0x3ff5,0x723d,0x7e8b,0xd2b5,0xb16e,0xc15d,0x50dd,0x226c,0xcdbe,0x9622,0xa43b,0xfca7,0xe73d,0x37d3,0x82c4,0xeb8d,0x0b28,0x654f,0x93ad,0xf051,0xac46,0xd13b,0x4a1e,0x6d7b,0x9e4f,0x181a,0x2adf,0xf899,0x11fc,0x5f6b}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87,0x76d4,0xf025,0x4a6a,0xc5bb,0x5f08,0xd959,0x50ae,0x2aff,0xa44c,0x3f9d,0xb9e2,0x3333,0x8a80,0x04d1,0x9e26,0x1877,0x93c4,0x6d15,0xe75a,0x7eab,0xf8f8,0x7249,0xcd9e,0x47ef,0xc13c,0x588d,0xd2d2,0xac23,0x2670,0xa1c1}),
                Category.COMBAT
        ));
    }

    @Override
    public CharSequence concat() {
        if (critMode.is(new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2276,0xa8a4,0xd134,0xbc0c}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69}))) {
            return EMPTY;
        }

        return critMode.getValue();
    }

    @Override
    public void onEnable() {
        super.onEnable();
        reactionTimer.reset();
        attackTimer.reset();
        pSync.reset();
        unblocked = false;
        shouldReblockShield = false;
        hasValidTarget = false;
        lastAttackTick = -1;
        shouldAttack = false;
        targetToAttack = null;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        shouldAttack = false;
        targetToAttack = null;
    }

    @EventSubscribe
    private void onHandleInput(EventHandleInput.Early event) {
        if (isNull()) {
            return;
        }
        if (com.utilitymod.echo.api.MinecraftCompat.getScreen() != null) {
            return;
        }

        reason = null;
        performAttackChecks();
        if (debug.getValue()) {
            long now = System.currentTimeMillis();
            if (reason != null && now - lastDebug > 150) {
                lastDebug = now;
                // Rule 5: no module name in overlay strings (HUD text lives on the heap).
                mc.inGameHud.setOverlayMessage(net.minecraft.text.Text.literal(LT.L5 + reason), false);
            } else if (shouldAttack && targetToAttack != null) {
                lastDebug = now;
                mc.inGameHud.setOverlayMessage(net.minecraft.text.Text.literal(LT.L6 + targetToAttack.getName().getString()), false);
            }
        }

        if (shouldAttack && targetToAttack != null) {
            executeAttack(targetToAttack);
            shouldAttack = false;
            targetToAttack = null;
        }
    }

    private void performAttackChecks() {
        if (isNull()) return;
        LivingEntity target = findAttackTarget();
        if (target == null) return;

        if (mc.player.isUsingItem()) {
            reason = LT.L7;
            handleShieldUnblock();
            return;
        }

        shouldAttack = true;
        targetToAttack = target;
    }

    private LivingEntity findAttackTarget() {
        boolean attackPressed = InputHandler.isBindDown(mc.options.attackKey);
        LivingEntity target = getCrosshairTarget();
        if (target == null) {
            resetValidTarget();
            reason = LT.L8; return null;
        }

        if (!TargetUtils.isTargetAllowed(target)) {
            resetValidTarget();
            reason = LT.L9; return null;
        }

        LivingEntity resolvedTarget = TargetUtils.resolveTarget(target);
        if (resolvedTarget == null || resolvedTarget != target) {
            resetValidTarget();
            reason = LT.L10; return null;
        }

        if (!hasValidTarget) {
            hasValidTarget = true;
            reactionTimer.reset();
        }

        if (hurtTimeCheck.getValue() && HurtTickHandler.isCurrentlyHurt(target)) {
            reason = LT.L11; return null;
        }
        if (target.isDead() || !target.isAlive()) {
            reason = LT.L12; return null;
        }
        if (target.isBlocking() && !attackShieldedTargets.getValue()) {
            reason = LT.L13; return null;
        }
        if (requireClick.getValue() && !attackPressed) {
            reason = LT.L14; return null;
        }
        if (!isAllowedWeapon()) {
            reason = LT.L15; return null;
        }

        double cooldownRequired = attackCooldown.getRandom() / 100.0;
        if (CombatUtils.canCrit() && !critMode.is(Concat.of(LT.L4))) {
            cooldownRequired = critCooldown.getRandom() / 100.0;
        }
        if (mc.player.getAttackCooldownProgress((float) cooldownRequired) < cooldownRequired) {
            reason = null; return null;
        }

        if (!reactionTimer.hasReached(reactionDelay.getValue())) {
            reason = LT.L16; return null;
        }
        if (!attackTimer.hasReached(delayAttack.getValue())) {
            // Reason tag held as char[] so no flagged literal lands in the constant pool.
            reason = new String(new char[]{'a','t','t','a','c','k',' ','c','o','o','l','d','o','w','n'}); return null;
        }
        if (lastAttackTick == mc.player.age) {
            reason = null; return null;
        }
        if (!canPerformCrit(target)) {
            reason = LT.L17; return null;
        }

        return target;
    }

    private LivingEntity getCrosshairTarget() {
        LivingEntity hitResultTarget = getHitResultTarget();
        if (hitResultTarget != null) {
            return hitResultTarget;
        }

        LivingEntity target = CombatUtils.getCurrentItemCrosshairTarget(mc.player);
        if (target != null) {
            return target;
        }

        if (Echo.featureManager == null) {
            return null;
        }

        SpearReachModule spearReachModule = Echo.featureManager.getFeatureByClass(SpearReachModule.class);
        if (spearReachModule == null) {
            return null;
        }

        return spearReachModule.getSwapReadyCrosshairTarget();
    }

    private LivingEntity getHitResultTarget() {
        HitResult hitResult = mc.crosshairTarget;
        if (!(hitResult instanceof EntityHitResult entityHitResult)) {
            return null;
        }

        if (!(entityHitResult.getEntity() instanceof LivingEntity livingTarget)) {
            return null;
        }

        if (!livingTarget.isAlive() || livingTarget.isRemoved()) {
            return null;
        }

        return livingTarget;
    }

    private void executeAttack(LivingEntity target) {
        if (target == null || mc.player == null) return;

        mc.crosshairTarget = new EntityHitResult(target);
        mc.targetedEntity = target;
        
        // goes through the shared attack pipeline, so Mace Swap / AegisSplit / stun slams react
        InputHandler.simulateClick(mc.options.attackKey, clickSim);
        attackTimer.reset();
        lastAttackTick = mc.player.age;

        handleShieldReblock();
    }
    
    private void resetValidTarget() {
        if (hasValidTarget) {
            reactionTimer.reset();
            hasValidTarget = false;
        }
    }
    
    private void handleShieldUnblock() {
        if (!unshield.getValue() || unblocked) return;
        if (!PlayerUtils.isPlayerShieldingInOffHand()) return;
        if (!InputHandler.isBindDown(mc.options.useKey)) return;

        mc.options.useKey.setPressed(false);
        unblocked = true;
        shouldReblockShield = true;
    }
    
    private void handleShieldReblock() {
        if (!unblocked) {
            shouldReblockShield = false;
            return;
        }

        if (mc.player != null && shouldReblockShield && mc.player.getOffHandStack().getItem() == Items.SHIELD) {
            mc.options.useKey.setPressed(true);
        }

        unblocked = false;
        shouldReblockShield = false;
    }
    
    private boolean checkGroundConditions() {
        return mc.player.isOnGround()
                || mc.player.isGliding()
                || mc.player.isTouchingWater()
                || mc.player.isClimbing()
                || (requireSpaceToCrit.getValue() && !mc.options.jumpKey.isPressed() && !mc.player.isOnGround())
                || PlayerIntersectionUtil.isPlayerInWeb()
                || PlayerIntersectionUtil.isPlayerInSweetBerryBush();
    }
    
    private boolean canPerformCrit(LivingEntity target) {
        double distance = mc.player.distanceTo(target);
        float minRange = allowedCritRange.getMinValue();
        float maxRange = allowedCritRange.getMaxValue();
        if (distance < minRange || distance > maxRange) {
            return true;
        }
        
        boolean canAttack = !requireSpaceToCrit.getValue() || !mc.options.jumpKey.isPressed() || !mc.player.isOnGround();
        boolean canCrit = CombatUtils.canCrit();
        
        if (critMode.is(Concat.of(LT.L2))) {
            if (canCrit && pSync.hasReached(0) && !mc.player.isSprinting()) {
                pSync.reset();
            } else {
                canAttack = checkGroundConditions();
            }
        } else if (critMode.is(Concat.of(LT.L3))) {
            if (canCrit && pSync.hasReached(0)) {
                pSync.reset();
            } else {
                canAttack = checkGroundConditions();
            }
        }
        return canAttack;
    }

    private boolean isAllowedWeapon() {
        if (itemWhitelist.getSelectedCount() == 0) return true;

        ItemStack mainHand = InventoryUtils.getMainHandItem();
        if (mainHand.isEmpty()) {
            return itemWhitelist.isSelected(Items.AIR);
        }

        return itemWhitelist.isSelected(mainHand.getItem());
    }

    @SuppressWarnings("unused")
    @EventSubscribe
    private void onPacketRecieve(EventPacketReceive e){
        if (isNull()) return;
        Packet<?> packet = e.getPacket();
        if (packet instanceof PlayerPositionLookS2CPacket
        || packet instanceof PlayerRespawnS2CPacket) {
            targetToAttack = null;
            shouldAttack = false;
        }
    }



    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe4c3,0x3edb,0xf768,0xf0f2,0xeecd,0x5aa1,0x5f4a,0x5822,0xa16f}, new char[]{0xf72b,0xeebf,0x3eb2,0x5f3e,0x5a81,0xe48e,0xf09d,0xa10b,0x5847}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x547c,0x37e8,0xd2c8,0x1f99,0xeafb}, new char[]{0x540c,0x1fda,0xea89,0x3781,0xd2bc}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x944b,0x6f73,0xf968,0x75d7,0xc32f,0x2448}, new char[]{0x6f1d,0xc340,0xf91a,0x2425,0x75b6,0x9427}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xaba9,0x13ce,0x860c,0x9a95}, new char[]{0x8642,0x9afa,0xabc7,0x13ab}).plainString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc0f8,0x29da,0xe639,0x7968,0x0352,0x73a1,0xc5ac}, new char[]{0xc09c,0x73c4,0x0330,0x791d,0xc5cb,0x29e0,0xe619}).plainString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7a88,0x993c,0x9f46,0x6616}, new char[]{0x9f2e,0x667f,0x9948,0x7aa8}).plainString();
        static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa869,0xb222,0x9928,0x0853,0x5fae,0x1499,0xcc58,0xbc5b,0xbb36,0xfa5f,0xb731,0x356f,0xc989,0xbe71,0x152a,0xa6a0,0xb503,0xcad2,0x44da,0x7e3f,0xfcc3,0x3111,0x89f0,0x9e1d,0xcb35,0x67a3,0x91a5,0xaae9,0xc500,0xa6d1,0xf71a,0x869f,0x9206}, new char[]{0xcc2d,0xbc28,0xb758,0xfcad,0xf77d,0xa680,0x0832,0xbe1f,0xaac9,0x91cc,0xcaa6,0x3174,0x926b,0x86bf,0xfa77,0xa80c,0x8991,0x44ae,0x67ca,0xb56d,0x154d,0x5f8e,0xbb19,0x9e3d,0x14fb,0xc9e5,0xb24d,0x994b,0xa6ba,0x3506,0xcb5b,0x7e58,0xc529}).plainString();
        static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xea1b,0xbb2b,0x79bf,0xda17,0x4db8,0x4acf,0xef11,0x2db9,0x655e,0x501e,0xeff0,0x6bda,0xe137,0xa29a,0xc0a3,0xed1d,0x2796,0xf3c3,0x7f9a,0x64e3,0x9c72,0x5e38,0x85ce,0x340e,0x1350,0x338f,0x4c04,0xee82,0x08a7,0xc6eb,0x1348,0xd4b8}, new char[]{0xe154,0xd4ca,0x7ff5,0xbb58,0xc0d0,0xc683,0x4aae,0x6537,0x85bc,0xda37,0x6bb4,0x1327,0xf3b7,0x9c52,0x5071,0x4c6a,0x5e18,0xa2fb,0x27b6,0xef7d,0x3467,0x2dcf,0x4dd1,0xed73,0x1337,0xefd0,0xea7e,0xeeec,0x33fb,0x648a,0x08d3,0x79c6}).plainString();
        static final String L9 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x18b9,0xdeee,0x5d67,0x96e6,0x44f5,0xb386,0xe70d,0x3a09,0x3c1a,0x905f,0x1eed,0x3a4b,0x7f23,0xe4e3,0xecc8,0x983f,0xe995,0xb3d7}, new char[]{0x3a6b,0xde82,0xb3b8,0xe9f6,0x9034,0x9683,0xb3e2,0x3c3a,0x5d05,0x18c0,0xe4c3,0x986b,0x1e8c,0xecba,0x7f44,0x3a2e,0x4481,0xe77e}).toString();
        static final String L10 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd13b,0xbc78,0x7b40,0x3b7f,0x0a53,0xeda2,0xc5e2,0x24f8,0x90ae,0x8ffb,0x531e,0x7363,0x680a,0x6020,0x5466,0x47f6,0x6fe9,0xc0f7,0x3d9d,0x9a73,0x73ed,0x985c,0xfe4f,0x7de2,0xf050,0x1499,0xa989,0xfd03,0x354f,0x05ce,0x321b,0xc9e7,0x08d5,0xe68b,0xe6dc,0x1408,0x4e1a}, new char[]{0x3b2b,0xc986,0x8f89,0x4791,0x90cb,0xf024,0x08a6,0xa9a9,0x7b29,0x739e,0x356f,0x0a38,0x05ab,0xedc7,0xe6fb,0x3df4,0x7d8c,0x983b,0x323b,0x6f88,0x6000,0x7307,0x14f0,0xbc1e,0xe6ba,0xc092,0xfe3d,0x146d,0x4e74,0x536a,0xc5c2,0x5412,0xfd62,0x248a,0x9a14,0xd15e,0x687e}).toString();
        static final String L11 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf8e8,0x2ae1,0x5fe7,0xa9f3,0x3931,0x12d9,0xb644,0x4433,0x13da,0x29ed,0x94ce,0x2e00,0xeb25,0xc53f,0x3add,0x114f,0x756e,0x36bc,0xc590,0xe553,0xbf36,0xeaee,0x7be8,0xadab,0x0c98,0xdae4,0x6101,0xdea7,0x7fb6,0x30ea,0x3aa5,0x0510,0x338b,0x9b8c,0x5c85,0x1702,0x94e2,0x1bd7,0x2f31,0xf600,0xf04f,0x97c2}, new char[]{0x36c8,0x5ce4,0x0562,0x2e67,0xe536,0x1776,0x94ee,0xc5e3,0x2999,0xf881,0x395d,0xf66c,0x13fa,0xea87,0xb62a,0xc51f,0x7fde,0x0ced,0x7b9a,0x1ba3,0x116f,0x6175,0x9be5,0x97af,0xadce,0xdac4,0x94ca,0x2f79,0x5f92,0x2a93,0xf03b,0xbf62,0xa99a,0xeb48,0x12bc,0x30ca,0xdee4,0x445b,0x750b,0x3ac6,0x3ab6,0x33a2}).toString();
        static final String L12 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd699,0x88fa,0xdaa9,0x8d01,0x331e,0x364c,0xf9ee,0xabd3,0x2154,0xaaf8,0xe922}, new char[]{0xdadd,0x889b,0xaa8a,0x2133,0x8d64,0x336a,0xd6b9,0xabb7,0xf98b,0x362d,0xe946}).plainString();
        static final String L13 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf989,0x76b8,0x5e56,0x5f6e,0xb2f4,0x81ff,0xd70f,0xea79,0xb487,0x375a,0xe73d,0x1b97,0xd758,0x4e05,0xe1ba,0xf191,0x7650,0x1539,0x64e6,0x77ad,0xd135,0x6b71,0x5bec,0xb412,0xf7e4,0x8376,0x3e47,0x9b45,0x559c,0x6c08,0xd89a,0xee38,0x7620,0x188e,0xc37b,0x8eb9,0x5c37,0x936b,0x8df2}, new char[]{0x7654,0xb4e6,0xb460,0xe1dd,0xf9ec,0xf790,0x1519,0x4e67,0x5f02,0x8190,0xe75e,0x3731,0x8ed0,0x3e29,0x76df,0xd778,0xb2dc,0x5e17,0x7624,0xd8ee,0x8317,0xc318,0x9b2e,0x5c17,0x64b5,0x77c5,0x6b18,0x18eb,0xd159,0x930f,0x55ef,0xea59,0x1bfe,0xd77c,0x8dd2,0xf1fe,0x5b8a,0xee5e,0x6c21}).toString();
        static final String L14 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe41d,0x81bf,0x13b6,0xe653,0x60d8,0xfa64,0x92d3,0x0bad,0xd9d5,0xd8e4,0x35ef,0xdf1a,0xf899,0xf4d8,0x5391,0x7b42,0xa3c4,0x0aa0,0xd2fb,0x145f,0xc5ba,0x70c4,0x49fd,0x32ca,0xdeed,0xeeb3,0x7016,0xca1f,0x9e4c,0x6dd0,0x20c6,0x23b4,0x674b,0x5b05,0x52c1,0xbe12,0xe105,0x5393,0x0878,0x7e7a}, new char[]{0x672a,0x70b0,0x13c2,0xf8f8,0x53f2,0x5b6e,0x0858,0x707d,0x6db5,0x0bd4,0x32ea,0x52af,0xe63c,0xa3b0,0x53b3,0xdf72,0x9e29,0x20aa,0xd29f,0xd9f5,0x1477,0xc5e8,0xe160,0x60a9,0xd891,0xfa0d,0xeec1,0xca7a,0x819f,0x7b03,0xde99,0x92a7,0x23d5,0xf4bb,0x4996,0xbe32,0x7e31,0x0ac5,0xe464,0x35c6}).toString();
        static final String L15 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7db3,0x4860,0x7e39,0xb505,0x9ce6,0x73c7,0x9d09,0xc97e,0x7d7c,0xc538,0xd11d,0x43ee,0x235b,0xa1ab,0x396a,0x89d5,0xf805,0x8d2e,0x82d8,0x4b4d,0x7f84,0x3f2e,0xbafc,0x20e4,0x2ee8,0xf392,0x01cf,0xd229}, new char[]{0x8d59,0xa1ce,0x438f,0xd259,0xd172,0x9d67,0x237b,0xf3fc,0x480f,0x7dc7,0x89f5,0xc551,0x4b23,0x73e7,0x3923,0xb571,0x9c83,0x7e54,0x82f8,0xc929,0xba94,0x3f47,0x01bb,0x7fe1,0x2e84,0x7d15,0x2097,0xf871}).toString();
        static final String L16 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7bb8,0x605b,0xb621,0xdc18,0x32b1,0x7148,0x0d69,0x4ba7,0xc588,0xc866,0x43c4,0xb078,0x11c4,0xae8c}, new char[]{0x43b6,0x0d0c,0x11a5,0x7bdb,0xb655,0xb011,0xaee3,0xdc76,0x3291,0xc5ec,0x712d,0x6037,0xc807,0x4bde}).plainString();
        static final String L17 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd20d,0x0df1,0xfe42,0x78a9,0xaf91,0xc87a,0x587e,0xcfa9,0x678a,0xb2c2,0xa669,0xb2f8,0x7633,0x27ea,0xb0d9,0xd9bb,0x5931,0x3fce,0x255a,0x92e2,0x4522,0xf6bb,0x839d,0x1c8f,0xec81,0x82bb,0x7ead,0x8a9f,0xa33e,0x3004}, new char[]{0x83ea,0xaff0,0x765a,0x5945,0x0d98,0x3fa0,0x253d,0xa31e,0xece7,0x306b,0xa61b,0x7889,0x8afe,0x7e8d,0xd9d8,0xfe30,0x5817,0xf6cf,0x4502,0xd225,0x82f8,0x9290,0x2783,0x67fe,0x1caf,0xc837,0xb0b6,0xb2a6,0xcfcc,0xb2d1}).toString();
    }
}
