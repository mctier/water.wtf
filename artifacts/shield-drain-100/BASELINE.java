package com.utilitymod.echo.features.impl.combat;

import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventHandleInput;
import com.utilitymod.echo.event.impl.EventMove;
import com.utilitymod.echo.event.impl.MouseUpdateEvent;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.settings.impl.BoolSetting;
import com.utilitymod.echo.features.settings.impl.FloatSetting;
import com.utilitymod.echo.features.settings.impl.ModeSetting;
import com.utilitymod.echo.features.settings.impl.RangeSetting;
import com.utilitymod.echo.handlers.InputHandler;
import com.utilitymod.echo.handlers.impl.HurtTickHandler;

import com.utilitymod.echo.handlers.impl.SwapStateManager;
import com.utilitymod.echo.api.MinecraftCompat;
import com.utilitymod.echo.utils.combat.CombatUtils;
import com.utilitymod.echo.utils.combat.TargetUtils;
import com.utilitymod.echo.utils.inventory.InventoryUtils;
import com.utilitymod.echo.utils.rotation.RotationUtils;
import com.utilitymod.echo.utils.strings.Concat;
import net.minecraft.util.math.MathHelper;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import com.utilitymod.echo.utils.EncryptedString;

import java.util.List;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ShieldItem;
import net.minecraft.item.AxeItem;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.util.Hand;
import net.minecraft.world.RaycastContext;
import com.utilitymod.echo.event.impl.EventStartAttack;
import com.utilitymod.echo.features.settings.impl.IntSetting;
import com.utilitymod.echo.features.settings.impl.KeybindSetting;
import com.utilitymod.echo.api.Accessors;
import org.lwjgl.glfw.GLFW;

public class AutoMace extends Feature {


    public AutoMace() {
        super(new FeatureInfo(
            Concat.of(ModuleText.S0),
            Concat.of(ModuleText.S1),
            Category.COMBAT
        ));
        swingDelay.describedBy(ModuleText.S2);
        cps.describedBy(ModuleText.S3);
        hitboxExpand.describedBy(ModuleText.S4);
        shieldDrain.describedBy(ModuleText.S5);
        maceKey.describedBy(ModuleText.S6);
        axeKey.describedBy(ModuleText.S7);
        preset.describedBy(ModuleText.S72);
        preset.onChanged(this::applyPreset);
    }

    private final ModeSetting preset = new ModeSetting(ModuleText.S73, ModuleText.S74, ModuleText.S74, ModuleText.S75, ModuleText.S76);
    private boolean applyingPreset;

    private void applyPreset() {
        if (applyingPreset || preset.is(ModuleText.S74)) return;
        applyingPreset = true;
        try {
            boolean lowRate = preset.is(ModuleText.S75);
            fallDistance.setValue(3f);
            swingDelay.setValue(lowRate ? 4 : 2);
            cps.setMinValue(lowRate ? 2f : 4f);
            cps.setMaxValue(lowRate ? 4f : 6f);
            hitboxExpand.setValue(0f);
            range.setValue(3f);
            horizontalSpeed.setMinValue(lowRate ? 15f : 25f);
            horizontalSpeed.setMaxValue(lowRate ? 25f : 40f);
            speedVert.setMinValue(lowRate ? 15f : 25f);
            speedVert.setMaxValue(lowRate ? 25f : 40f);
            throughWalls.setValue(false);
            shieldDrain.setValue(false);
            spearMace.setValue(false);
            preArmOnFall.setValue(false);
            autoMace.setValue(true);
            // One-shot application: saved configs retain the resulting individual settings.
            // Return to Custom so selecting the same preset again reapplies it.
            preset.setValue(ModuleText.S74);
        } finally {
            applyingPreset = false;
        }
    }

    private final BoolSetting holdToEnable = new BoolSetting(ModuleText.S8, false);
    private final FloatSetting fallDistance = new FloatSetting(ModuleText.S9, 3f, 0f, 10f, 0.1f);
    private final IntSetting swingDelay = new IntSetting(ModuleText.S10, 2, 0, 20, ModuleText.S11);
    private final RangeSetting cps = new RangeSetting(ModuleText.S12, 4f, 6f, 1f, 100f, 0.5f);
    private final FloatSetting densityThreshold = new FloatSetting(ModuleText.S13, 7f, 0f, 10f, 0.1f);
    private final BoolSetting targetPlayer = new BoolSetting(ModuleText.S14, true);
    private final BoolSetting targetMob = new BoolSetting(ModuleText.S15, false);
    private final FloatSetting hitboxExpand = new FloatSetting(ModuleText.S16, 0f, 0f, 1f, 0.01f);
    private final BoolSetting stunSlam = new BoolSetting(ModuleText.S17, true);
    private final ModeSetting stunWeapon = new ModeSetting(LT.L1, LT.L2, o -> stunSlam.getValue(), LT.L2, LT.L3);
    private final IntSetting stunChance = new IntSetting(ModuleText.S18, 100, 0, 100, o -> stunSlam.getValue());
    /** Ticks between the axe shield-break and the mace follow-up of a stun-slam combo. */
    private final IntSetting stunSlamDelay = new IntSetting(ModuleText.S77, 0, 0, 3, o -> stunSlam.getValue());
    /** Drain shield durability so it breaks in one tap. */
    private final BoolSetting oneTap = new BoolSetting(LT.L4, false);
    private final BoolSetting shieldDrain = new BoolSetting(ModuleText.S19, false);
    private final IntSetting shieldDrainCps = new IntSetting(LT.L5, 20, 10, 100, LT.L6, o -> shieldDrain.getValue());
    private final com.utilitymod.echo.features.settings.impl.InputSimulationSetting inputSimulation =
            new com.utilitymod.echo.features.settings.impl.InputSimulationSetting(ModuleText.S70, false, o -> shieldDrain.getValue());
    private final BoolSetting autoMace = new BoolSetting(ModuleText.S0, true);
    private final BoolSetting preArmOnFall = new BoolSetting(LT.L7, false);
    private final BoolSetting breach = new BoolSetting(ModuleText.S20, true);
    private final BoolSetting stayMace = new BoolSetting(ModuleText.S21, true);
    private final BoolSetting spearMace = new BoolSetting(ModuleText.S22, false);
    private final ModeSetting inputMode = new ModeSetting(ModuleText.S23, ModuleText.S24, ModuleText.S24, ModuleText.S25);
    private final BoolSetting keySim = new BoolSetting(ModuleText.S26, true);
    private final KeybindSetting maceKey = new KeybindSetting(ModuleText.S27, GLFW.GLFW_KEY_3, o -> keySim.getValue());
    private final KeybindSetting axeKey = new KeybindSetting(ModuleText.S28, GLFW.GLFW_KEY_2, o -> keySim.getValue());
    private final BoolSetting silent = new BoolSetting(ModuleText.S29, false);
    private final BoolSetting autoAim = new BoolSetting(ModuleText.S30, false);

    private final ModeSetting aimMath = new ModeSetting(EncryptedString.of(ModuleText.S31), EncryptedString.of(ModuleText.S32), o -> aimingEnabled(), EncryptedString.of(ModuleText.S32), EncryptedString.of(ModuleText.S33), EncryptedString.of(ModuleText.S34));
    private final ModeSetting aimVector = new ModeSetting(EncryptedString.of(ModuleText.S35), EncryptedString.of(ModuleText.S36), o -> aimingEnabled(), EncryptedString.of(ModuleText.S36), EncryptedString.of(ModuleText.S37));
    private final ModeSetting targetPriority = new ModeSetting(EncryptedString.of(ModuleText.S38), EncryptedString.of(ModuleText.S39), o -> aimingEnabled(), EncryptedString.of(ModuleText.S39), EncryptedString.of(ModuleText.S40), EncryptedString.of(ModuleText.S41));
    private final BoolSetting random = new BoolSetting(EncryptedString.of(ModuleText.S42), false, o -> aimingEnabled());
    private final BoolSetting throughWalls = new BoolSetting(EncryptedString.of(ModuleText.S43), false, o -> aimingEnabled());
    private final RangeSetting horizontalSpeed = new RangeSetting(EncryptedString.of(ModuleText.S44), 30f, 50f, 0f, 300f, 1f, o -> aimingEnabled());
    private final RangeSetting speedVert = new RangeSetting(EncryptedString.of(ModuleText.S45), 30f, 50f, 0f, 300f, 1f, o -> aimingEnabled());
    {
        silent.onChanged(() -> {
            if (!silent.getValue() && RotationUtils.isControlledBy(this)) RotationUtils.stopTracking();
            float newUpperBound = silent.getValue() ? 600f : 300f;
            horizontalSpeed.setUpperBound(newUpperBound);
            speedVert.setUpperBound(newUpperBound);
            if (horizontalSpeed.getMaxValue() > newUpperBound) horizontalSpeed.setMaxValue(newUpperBound);
            if (horizontalSpeed.getMinValue() > newUpperBound) horizontalSpeed.setMinValue(newUpperBound);
            if (speedVert.getMaxValue() > newUpperBound) speedVert.setMaxValue(newUpperBound);
            if (speedVert.getMinValue() > newUpperBound) speedVert.setMinValue(newUpperBound);
        });
    }
    private final FloatSetting range = new FloatSetting(EncryptedString.of(ModuleText.S46), 6.0f, 1.0f, 50.0f, 0.1f, o -> aimingEnabled());
    private final FloatSetting yawFov = new FloatSetting(ModuleText.S47, 180.0f, 0.0f, 180.0f, 1f, o -> aimingEnabled());
    private final FloatSetting pitchFov = new FloatSetting(EncryptedString.of(ModuleText.S48), 90.0f, 0.0f, 180.0f, 1f, o -> aimingEnabled());
    private final FloatSetting multipoint = new FloatSetting(EncryptedString.of(ModuleText.S49), 50.0f, 0.0f, 100.0f, 1f, o -> aimingEnabled());

    private final BoolSetting showStatus = new BoolSetting(ModuleText.S50, true);
    private String lastStatus = ModuleText.S51;
    private final BoolSetting debug = new BoolSetting(EncryptedString.of(ModuleText.S52), false);
    private long lastDebug;

    private final AutoMacePolicy cadence = new AutoMacePolicy();
    private LivingEntity spearTarget, slamTarget;
    private int spearExpiry, slamExpiry, selectionExpiry, requestedSlot = -1, originalSlot = -1;
    /** One stun-slam combo per target, then a short lockout: the axe's shield-disable takes a tick
     *  or two to replicate, so without this the next tick still sees a "shielded" target and
     *  re-rolls the combo, stacking more hits every cadence-ready tick while the aim stays on it. */
    private LivingEntity slamRecoveryTarget;
    private int slamRecoveryUntil;
    /** Armed stun-slam follow-up waiting for its configured tick delay. */
    private LivingEntity pendingSlamTarget;
    private int pendingSlamUntil, pendingSlamSlot = -1;
    private static final int SLAM_RECOVERY_TICKS = 30;
    private int requestedKey = -1;
    private boolean attacking, attackObserved;
    private LivingEntity attackingTarget;
    private int lastSentAttackTick = -1;
    private long nextIntervalMs;
    private double drainAccum;
    private int lastDrainTick = -1;
    private double recentFall;
    private int fallHold, lastFallTick = -1;
    private Object sessionPlayer, sessionWorld;

    /** Attacks to send this tick for shield drain, from the configured CPS (20 tps base; >20 CPS
     *  bursts multiple hits per tick). Consumed once per tick so it isn't double-counted. */
    private int drainBurst(int tick) {
        if (tick == lastDrainTick) return 0;
        lastDrainTick = tick;
        drainAccum = Math.min(5.0, drainAccum + Math.max(0, Math.min(100, shieldDrainCps.getValue())) / 20.0);
        int burst = Math.min(5, (int) Math.floor(drainAccum));
        drainAccum -= burst;
        return burst;
    }

    /** Fall distance for the Density/fall decision, held briefly after landing. Vanilla zeroes
     *  fallDistance on touchdown, so without this a landing smash reads as fall 0 and picks Breach. */
    private double effectiveFall() {
        int tick = mc.player.age;
        if (tick != lastFallTick) {
            lastFallTick = tick;
            double fd = mc.player.fallDistance;
            if (fd > 0.01) { recentFall = Math.max(recentFall, fd); fallHold = 6; }
            else if (fallHold > 0) fallHold--;
            else recentFall = 0.0;
        }
        return Math.max(mc.player.fallDistance, recentFall);
    }

    @Override
    public CharSequence concat() { return lastStatus; }

    private boolean aimingEnabled() { return autoAim.getValue() || silent.getValue(); }
    private boolean activationHeld() { return !holdToEnable.getValue() || InputHandler.isBindDown(mc.options.attackKey); }

    @Override
    public void onEnable() { clearSession(false); super.onEnable(); }

    @Override
    public void onDisable() {
        super.onDisable();
        clearSession(true);
        if (RotationUtils.isControlledBy(this)) RotationUtils.stopTracking();
    }

    private void clearSession(boolean restore) {
        if (requestedKey >= 0) InputHandler.simulateKeyRelease(requestedKey);
        if (requestedSlot >= 0 && mc.options != null) {
            Accessors.setClickCount(mc.options.hotbarKeys[requestedSlot], 0);
        }
        if (restore && mc.player != null && mc.player == sessionPlayer && originalSlot >= 0) {
            InventoryUtils.setInvSlot(originalSlot);
        }
        requestedKey = requestedSlot = originalSlot = -1;
        spearTarget = slamTarget = null;
        pendingSlamTarget = null;
        pendingSlamSlot = -1;
        slamRecoveryTarget = null;
        slamRecoveryUntil = 0;
        drainAccum = 0; lastDrainTick = -1;
        cadence.reset();
        nextIntervalMs = 0;
        lastSentAttackTick = -1;
        attackingTarget = null;
        attackObserved = false;
        sessionPlayer = mc.player;
        sessionWorld = mc.world;
    }

    @EventSubscribe(priority = EventSubscribe.Priority.HIGH)
    private void onManualAttack(EventStartAttack event) {
        if (!attacking && !isNull() && lastSentAttackTick == mc.player.age) event.cancel();
    }

    @EventSubscribe
    private void onSpearHit(com.utilitymod.echo.event.impl.EventOnAttackEntity.Post event) {
        if (attacking) {
            if (event.getPlayer() == mc.player && event.getTarget() == attackingTarget) attackObserved = true;
            return;
        }
        if (isNull() || event.getPlayer() != mc.player
                || MinecraftCompat.getScreen() != null || !spearMace.getValue()) return;
        if (!activationHeld() || !InputHandler.isBindDown(mc.options.useKey)
                || !InventoryUtils.isItemSpear(mc.player.getMainHandStack().getItem())) return;
        if (!(event.getTarget() instanceof LivingEntity target) || !allowedTarget(target)) return;
        // Preserve the original spear hit; schedule a mace follow-up on a later input tick.
        spearTarget = target;
        spearExpiry = mc.player.age + 10;
        cadence.mark(mc.player.age);
    }

    @EventSubscribe
    private void onMouseUpdate(MouseUpdateEvent event) {
        if (!aimingEnabled()) {
            if (RotationUtils.isControlledBy(this)) disengageRotations();
            return;
        }

        if (isNull() || MinecraftCompat.getScreen() != null || !activationHeld()) {
            disengageRotations();
            return;
        }

        if (!isFallValid()) {
            disengageRotations();
            return;
        }

        if (!isHoldingMaceOrSwap()) {
            if (silent.getValue() &&
                RotationUtils.isTracking() &&
                RotationUtils.isControlledBy(this) &&
                (MaceSwap.isSwapActive() || SpearReachModule.isSwapActive())) {
                return;
            }
            disengageRotations();
            return;
        }

        double rangeValue = range.getValue();
        double rangeSquared = rangeValue * rangeValue;
        List<LivingEntity> validTargets = mc.world.getEntitiesByClass(
            LivingEntity.class,
            mc.player.getBoundingBox().expand(rangeValue),
            e -> e != mc.player && !e.isDead() && e.isAlive()
        ).stream()
            .filter(this::allowedTarget)
            .filter(e -> mc.player.squaredDistanceTo(e) <= rangeSquared)
            .filter(this::isTargetInFov)
            .toList();

        LivingEntity candidateTarget = pendingSlamTarget != null && validTargets.contains(pendingSlamTarget)
                ? pendingSlamTarget : selectPriorityTarget(validTargets);
        LivingEntity target = TargetUtils.resolveTarget(candidateTarget);

        if (target == null || mc.player.squaredDistanceTo(target) > rangeSquared || !isTargetInFov(target)) {
            disengageRotations();
            return;
        }

        boolean rotationSuccess = RotationUtils.aim(this)
            .priority(EventSubscribe.Priority.HIGH)
            .speed(horizontalSpeed.getRandom(), speedVert.getRandom())
            .silent(silent.getValue())
            .aimType(getAimType())
            .points(getPointsMode())
            .multipoint(multipoint.getValue())
            .random(random.getValue())
            .throughWalls(throughWalls.getValue())
            .to(target);

        if (!rotationSuccess) {
            disengageRotations();
        }
    }

    @EventSubscribe
    private void onTickInputEvent(EventHandleInput.Early event) {
        updateAttackStatus();
    }

    @EventSubscribe(priority = EventSubscribe.Priority.HIGH)
    private void onInputPost(EventHandleInput.Post event) {
        // Vanilla consumes hotbar key presses inside handleInputEvents. Retry after that,
        // rather than waiting a full falling tick and losing the target below us.
        if (!isNull() && requestedSlot >= 0 && cadence.ready(mc.player.age, swingDelay.getValue())
                && mc.player.getInventory().getSelectedSlot() == requestedSlot) updateAttackStatus();
    }

    private void updateAttackStatus() {
        String why = attackOrReason();
        lastStatus = why == null ? ModuleText.S53 : why;
        if ((showStatus.getValue() || debug.getValue()) && !isNull()) {
            long now = System.currentTimeMillis();
            if (now - lastDebug > 350) {
                lastDebug = now;
                mc.inGameHud.setOverlayMessage(net.minecraft.text.Text.literal(ModuleText.S54 + lastStatus), false);
            }
        }
    }

    /** Exactly one attempt per input tick, after any simulated selection is confirmed. */
    private String attackOrReason() {
        if (sessionPlayer != mc.player || sessionWorld != mc.world) clearSession(false);
        if (isNull() || MinecraftCompat.getScreen() != null || !activationHeld()) {
            clearSession(true);
            if (RotationUtils.isControlledBy(this)) RotationUtils.stopTracking();
            return ModuleText.S55;
        }
        if (mc.player.isSpectator() || !mc.player.isAlive() || mc.interactionManager == null) return ModuleText.S55;
        if (SwapStateManager.hasActiveSwaps()) return ModuleText.S56;
        int tick = mc.player.age;
        if (lastSentAttackTick == tick) return ModuleText.S64;
        if (spearTarget != null && (tick > spearExpiry || !spearMace.getValue())) { clearSession(true); return ModuleText.S57; }
        if (slamTarget != null && (tick > slamExpiry || !allowedTarget(slamTarget))) slamTarget = null;
        if (pendingSlamTarget != null && (tick > pendingSlamUntil + 2 || !allowedTarget(pendingSlamTarget))) { pendingSlamTarget = null; pendingSlamSlot = -1; }
        LivingEntity target = pendingSlamTarget != null ? pendingSlamTarget : spearTarget != null ? spearTarget : slamTarget != null ? slamTarget : resolveAttackTarget();
        if (!allowedTarget(target)) {
            if (preArmFalling(tick)) return LT.L8;
            clearSession(true); return ModuleText.S58;
        }
        LivingEntity locked = TargetUtils.resolveTarget(target);
        if (locked != target || !inReach(target)) {
            if (preArmFalling(tick)) return LT.L8;
            clearSession(true); return ModuleText.S59;
        }
        boolean draining = (shieldDrain.getValue() || oneTap.getValue()) && shielding(target);
        if (!draining && pendingSlamTarget == null && target == slamRecoveryTarget && tick < slamRecoveryUntil) return ModuleText.S71;
        // Armed stun-slam follow-up: fire the stored mace once its delay ticks are up.
        if (pendingSlamTarget != null && target == pendingSlamTarget) {
            if (tick < pendingSlamUntil) {
                return String.format(java.util.Locale.ROOT, ModuleText.S78, pendingSlamUntil - tick);
            }
            if (pendingSlamSlot < 0 || pendingSlamSlot > 8
                    || !mc.player.getInventory().getStack(pendingSlamSlot).isOf(Items.MACE)) {
                clearSession(true); return ModuleText.S67;
            }
            if (!rotationAligned(target)) return ModuleText.S64;
            if (selectWeapon(pendingSlamSlot, maceKey.getKey(), tick, true)) {
                sendOneAttack(target, draining);
                if (!attackObserved) return ModuleText.S69;
                cadence.mark(tick);
                cadence.markMs(System.currentTimeMillis());
                nextIntervalMs = AutoMacePolicy.intervalMsForCps(cps.getRandom());
                pendingSlamTarget = null;
                pendingSlamSlot = -1;
                spearTarget = null;
                slamTarget = null;
                lastSentAttackTick = tick;
                        requestedSlot = -1;
                if (requestedKey >= 0) InputHandler.simulateKeyRelease(requestedKey);
                requestedKey = -1;
                if (!stayMace.getValue() && originalSlot >= 0) InventoryUtils.setInvSlot(originalSlot);
                originalSlot = -1;
                return null;
            }
            return ModuleText.S68;
        }
        if (spearTarget == null && InventoryUtils.isItemSpear(mc.player.getMainHandStack().getItem())) return ModuleText.S60;
        if (!autoMace.getValue() && spearTarget == null && slamTarget == null && !draining) return ModuleText.S61;
        double fall = effectiveFall();
        if (!draining && spearTarget == null && slamTarget == null && fall < fallDistance.getValue()) { clearSession(true); return String.format(java.util.Locale.ROOT, ModuleText.S62, fall, fallDistance.getValue()); }
        // Shield drain uses its own bounded per-tick CPS budget.
        int burst = 1;
        if (draining) {
            burst = drainBurst(tick);
            if (burst <= 0) return ModuleText.S64;
        } else {
            if (!cadence.ready(tick, swingDelay.getValue())) return ModuleText.S63;
            if (!cadence.readyMs(System.currentTimeMillis(), nextIntervalMs)) return ModuleText.S64;
        }
        if (slamTarget != null && shielding(target)) return ModuleText.S65;

        // Grim aim checks: an attack carried by a look that doesn't yet reach the target reads
        // as aimbot from the server, so wait a tick until the visible rotation carries the hit.
        if (!rotationAligned(target)) return ModuleText.S64;

        boolean axe = !draining && slamTarget == null && shielding(target) && stunSlam.getValue();
        if (axe) {
            // Keep the decision stable across an asynchronous hotbar selection.
            axe = requestedSlot >= 0
                    ? isStunWeapon(mc.player.getInventory().getStack(requestedSlot).getItem())
                    : AutoMacePolicy.chance(stunChance.getValue(),
                        java.util.concurrent.ThreadLocalRandom.current().nextInt(100));
        }
        // The stun-slam mace follow-up is a shield-break combo, not a fall smash, so don't force a
        // Density mace from height — use whatever mace is available by selecting with fall 0.
        double weaponFall = (slamTarget != null && !axe) ? 0.0 : fall;
        int slot = weaponSlot(axe, draining, weaponFall);
        // Stun Slam is optional: lack of an axe must not disable normal mace attacks.
        if (axe && slot < 0) {
            axe = false;
            slot = weaponSlot(false, draining, weaponFall);
        }
        if (slot < 0) { clearSession(true); return draining ? ModuleText.S66 : ModuleText.S67; }
        // Stun Slam swaps instantly (no key-sim latency): from extreme height the target is only in
        // reach for ~1 tick, too short for a simulated hotbar press to register the axe/mace in time.
        boolean instant = axe || slamTarget != null;
        if (!selectWeapon(slot, axe ? axeKey.getKey() : maceKey.getKey(), tick, instant)) return ModuleText.S68;
        if (spearTarget != null && mc.player.isUsingItem()) mc.interactionManager.stopUsingItem(mc.player);
        cadence.mark(tick);
        cadence.markMs(System.currentTimeMillis());
        nextIntervalMs = AutoMacePolicy.intervalMsForCps(cps.getRandom());
        sendOneAttack(target, draining);
        boolean landed = attackObserved;
        // Stun Slam is one tick, two inputs: the axe disables the shield, then we instantly swap to
        // the mace and smash in the SAME tick (the server processes the axe hit first). One tick in
        // reach is enough, and the axe knockback no longer pushes the target out of a later slam.
        if (axe && landed) {
            int maceSlot = weaponSlot(false, draining, fall);   // density if the dive was high
            int delay = Math.max(0, Math.min(3, stunSlamDelay.getValue()));
            if (delay == 0) {
                // Stun Slam is instant: the axe disables the shield, then we swap to the mace and
                // smash in the SAME tick — from extreme height the target is only in reach ~1 tick.
                if (maceSlot >= 0 && selectWeapon(maceSlot, maceKey.getKey(), tick, true)) sendOneAttack(target, draining);
            } else if (maceSlot >= 0) {
                // Delayed follow-up: hold the mace selection and let the axe's shield-break
                // replicate over the configured ticks, then smash. Racing hits inside one combo are the tell;
                // a gap reads as human re-aim between the two swings.
                pendingSlamTarget = target;
                pendingSlamUntil = tick + delay;
                pendingSlamSlot = maceSlot;
            }
        } else if (draining && landed) {
            // At 20 ticks/s: 50 CPS alternates 2/3 attempts; 100 CPS permits 5.
            for (int i = 1; i < burst; i++) {
                if (!allowedTarget(target) || !shielding(target) || !inReach(target) || !rotationAligned(target)) break;
                sendOneAttack(target, true);
                if (!attackObserved) break;
            }
        }
        // doAttack() returns false even for entity hits; only the attack-post hook
        // confirms that the interaction-manager attack path actually completed.
        if (!landed) return ModuleText.S69;
        lastSentAttackTick = tick;
        if (axe) {
            slamRecoveryTarget = target;
            slamRecoveryUntil = tick + SLAM_RECOVERY_TICKS;
        }
        spearTarget = null;
        slamTarget = null;
        requestedSlot = -1;
        if (requestedKey >= 0) InputHandler.simulateKeyRelease(requestedKey);
        requestedKey = -1;
        if (!stayMace.getValue() && originalSlot >= 0) InventoryUtils.setInvSlot(originalSlot);
        originalSlot = -1;
        return null;
    }

    private boolean shielding(LivingEntity target) {
        return target.isUsingItem() && target.getActiveItem().getItem() instanceof ShieldItem;
    }

    private int weaponSlot(boolean axe, boolean draining, double fall) {
        var enchant = AutoMacePolicy.preferDensity(draining, fall, densityThreshold.getValue())
                ? Enchantments.DENSITY : breach.getValue() ? Enchantments.BREACH : null;
        if (keySim.getValue()) {
            int preferredKey = axe ? axeKey.getKey() : maceKey.getKey();
            for (int slot = 0; slot < 9; slot++) {
                var bound = Accessors.getKey(mc.options.hotbarKeys[slot]);
                if (bound == null || bound.getCategory() != net.minecraft.client.util.InputUtil.Type.KEYSYM
                        || bound.getCode() != preferredKey) continue;
                ItemStack stack = mc.player.getInventory().getStack(slot);
                if (axe && isStunWeapon(stack.getItem())) return slot;
                if (!axe && stack.isOf(Items.MACE)) {
                    if (enchant == null) return slot;
                    var entry = mc.world.getRegistryManager().getOptionalEntry(enchant);
                    if (entry.isPresent() && EnchantmentHelper.getLevel(entry.get(), stack) > 0) return slot;
                }
            }
        }
        // If the preferred key's mace has the wrong enchantment, select the matching mace
        // elsewhere and simulate that slot's actual bound key rather than attacking with it.
        if (axe) return stunWeapon.is(LT.L3) ? InventoryUtils.getSwordSlot() : InventoryUtils.getAxeSlot();
        return InventoryUtils.findMaceWithEnchantmentInHotbar(enchant, !draining);
    }

    /** The configured stun weapon: an axe (vanilla shield disable) or a sword. */
    private boolean isStunWeapon(net.minecraft.item.Item item) {
        return stunWeapon.is(LT.L3) ? InventoryUtils.isSword(item) : item instanceof AxeItem;
    }

    /** One full attack dispatch at the target: fakes the crosshair, charges the weapon, sends the
     *  input (or forces a direct hit if doAttack refused), then restores state and the mining guard. */
    private void sendOneAttack(LivingEntity target, boolean draining) {
        // Make each attack (incl. every shield-drain burst hit) show on the Keystrokes/CPS counter.
        InputHandler.showClick(null, 0);
        HitResult previousHit = mc.crosshairTarget;
        var previousEntity = mc.targetedEntity;
        mc.crosshairTarget = new EntityHitResult(target, attackPoint(target));
        mc.targetedEntity = target;
        attacking = true;
        attackObserved = false;
        attackingTarget = target;
        Accessors.setAttackStrengthTicker(mc.player, 1000);
        try {
            if (draining && inputSimulation.getValue()) {
                // Drain hits through the input-sim path: EventStartAttack.Pre then vanilla doAttack
                // for the faked entity crosshair, plus the Input 1-5 companion keys.
                InputHandler.simulateClick(mc.options.attackKey, inputSimulation);
                if (!attackObserved) {
                    mc.interactionManager.attackEntity(mc.player, target);
                    mc.player.swingHand(Hand.MAIN_HAND);
                }
            } else if (inputMode.is(ModuleText.S24)) {
                Accessors.resetAttackCooldown();
                Accessors.invokeStartAttack();
                if (!attackObserved) {
                    mc.interactionManager.attackEntity(mc.player, target);
                    mc.player.swingHand(Hand.MAIN_HAND);
                }
            } else {
                mc.interactionManager.attackEntity(mc.player, target);
                mc.player.swingHand(Hand.MAIN_HAND);
            }
        } finally {
            attacking = false;
            attackingTarget = null;
            mc.crosshairTarget = previousHit;
            mc.targetedEntity = previousEntity;
            Accessors.setAttackCooldown(10);
        }
    }

    /** While falling past the Min Fall threshold, hold the correct mace ready even before the target
     *  is in reach, so a drop from height doesn't land with the wrong item and miss the smash. */
    private boolean preArmFalling(int tick) {
        if (!preArmOnFall.getValue() || !autoMace.getValue() || mc.player.isOnGround()) return false;
        double fall = effectiveFall();
        if (fall < fallDistance.getValue()) return false;
        if (InventoryUtils.isItemSpear(mc.player.getMainHandStack().getItem())) return false;
        // Only pre-arm when actually diving toward an enemy; otherwise plain falling/jumping would
        // keep swapping to the mace for no reason. Radius grows with fall so a high dive still counts.
        double radius = 6.0 + fall;
        if (mc.world.getEntitiesByClass(LivingEntity.class, mc.player.getBoundingBox().expand(radius), this::allowedTarget).isEmpty())
            return false;
        int slot = weaponSlot(false, false, fall);
        if (slot < 0) return false;
        selectWeapon(slot, maceKey.getKey(), tick);
        return true;
    }

    private boolean selectWeapon(int slot, int key, int tick) {
        return selectWeapon(slot, key, tick, false);
    }

    private boolean selectWeapon(int slot, int key, int tick, boolean instant) {
        if (mc.player.getInventory().getSelectedSlot() == slot) return true;
        if (originalSlot < 0) originalSlot = mc.player.getInventory().getSelectedSlot();
        if (instant || !keySim.getValue()) { InventoryUtils.setInvSlot(slot); return mc.player.getInventory().getSelectedSlot() == slot; }
        var binding = Accessors.getKey(mc.options.hotbarKeys[slot]);
        // The hotbar key can't be key-simulated (unbound, or bound to a mouse button). Don't stall
        // on "waiting for weapon key" forever — just switch the slot directly so the mace swings.
        if (binding == null || binding.getCategory() != net.minecraft.client.util.InputUtil.Type.KEYSYM) {
            InventoryUtils.setInvSlot(slot);
            return mc.player.getInventory().getSelectedSlot() == slot;
        }
        key = binding.getCode();
        if (requestedSlot != slot) {
            if (requestedKey >= 0) InputHandler.simulateKeyRelease(requestedKey);
            requestedSlot = slot;
            requestedKey = key;
            selectionExpiry = tick + 5;
            var hotbar = mc.options.hotbarKeys[slot];
            int clicksBefore = Accessors.getClickCount(hotbar);
            InputHandler.simulateKeyClick(key, 20);
            // OS key injection can succeed without delivering a Minecraft key callback.
            // Preserve Key Sim, but ensure vanilla has a queued hotbar press to consume.
            if (Accessors.getClickCount(hotbar) == clicksBefore) {
                InputHandler.incrementClickCountForKeyMapping(hotbar);
            }
        } else if (tick > selectionExpiry) {
            // Key simulation didn't take within the window — fall back to a direct slot switch
            // instead of aborting, so the swing isn't lost to a stuck "waiting for weapon key".
            if (requestedKey >= 0) InputHandler.simulateKeyRelease(requestedKey);
            requestedSlot = requestedKey = -1;
            InventoryUtils.setInvSlot(slot);
            return mc.player.getInventory().getSelectedSlot() == slot;
        }
        return false;
    }

    private boolean allowedTarget(LivingEntity target) {
        return target != null && target != mc.player && target.isAlive() && !target.isRemoved()
                && !target.isInvulnerable()
                && AutoMacePolicy.allowed(target instanceof PlayerEntity, target instanceof MobEntity,
                    targetPlayer.getValue(), targetMob.getValue())
                && TargetUtils.isTargetAllowed(target);
    }

    private Vec3d attackPoint(LivingEntity target) {
        var box = target.getBoundingBox();
        Vec3d eye = mc.player.getEyePos();
        return new Vec3d(MathHelper.clamp(eye.x, box.minX, box.maxX),
                MathHelper.clamp(eye.y, box.minY, box.maxY), MathHelper.clamp(eye.z, box.minZ, box.maxZ));
    }

    /** Gate: the rotation the server actually sees must point into the target hitbox before any
     *  attack packet goes out — Grim ray-traces every interact against the last sent look. */
    private boolean rotationAligned(LivingEntity target) {
        Vec3d eye = mc.player.getEyePos();
        boolean controlled = RotationUtils.isControlledBy(this) && RotationUtils.hasSilentRotation();
        float actualYaw = controlled ? RotationUtils.getSilentYaw() : mc.player.getYaw();
        float actualPitch = controlled ? RotationUtils.getSilentPitch() : mc.player.getPitch();
        Vec3d end = eye.add(Vec3d.fromPolar(actualPitch, actualYaw).multiply(mc.player.getEntityInteractionRange()));
        var realHit = target.getBoundingBox().raycast(eye, end);
        if (!target.getBoundingBox().contains(eye) && realHit.isEmpty()) return false;
        var obstruction = mc.world.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, mc.player));
        if (obstruction.getType() != HitResult.Type.MISS && realHit.isPresent()
                && eye.squaredDistanceTo(obstruction.getPos()) < eye.squaredDistanceTo(realHit.get())) return false;
        return true;
    }

    private boolean inReach(LivingEntity target) {
        double reach = mc.player.getEntityInteractionRange();
        return mc.player.getEyePos().squaredDistanceTo(attackPoint(target)) <= reach * reach && mc.player.canSee(target);
    }

    @EventSubscribe
    private void onMove(EventMove.Pre event) {
        if (!silent.getValue() || isNull() || !RotationUtils.isControlledBy(this)) return;
        if (!RotationUtils.hasSilentRotation()) return;

        event.setYaw(RotationUtils.getSilentYaw());
        event.setPitch(RotationUtils.getSilentPitch());
    }

    private boolean isFallValid() {
        if (shieldDrain.getValue() || spearTarget != null || slamTarget != null || pendingSlamTarget != null) return true;
        if (mc.player.fallDistance < fallDistance.getValue()) return false;
        return true;
    }

    private LivingEntity resolveAttackTarget() {
        if (!silent.getValue() && mc.crosshairTarget instanceof EntityHitResult hit
                && hit.getEntity() instanceof LivingEntity target && allowedTarget(target)) return target;
        Vec3d eye = mc.player.getEyePos();
        float yaw = silent.getValue() && RotationUtils.isControlledBy(this) ? RotationUtils.getSilentYaw() : mc.player.getYaw();
        float pitch = silent.getValue() && RotationUtils.isControlledBy(this) ? RotationUtils.getSilentPitch() : mc.player.getPitch();
        Vec3d end = eye.add(Vec3d.fromPolar(pitch, yaw).multiply(mc.player.getEntityInteractionRange()));
        var block = mc.world.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, mc.player));
        double maxDistance = block.getType() == HitResult.Type.MISS ? eye.squaredDistanceTo(end) : eye.squaredDistanceTo(block.getPos());
        LivingEntity nearest = null;
        double margin = hitboxExpand.getValue();
        for (LivingEntity target : mc.world.getEntitiesByClass(LivingEntity.class,
                mc.player.getBoundingBox().stretch(end.subtract(eye)).expand(margin + 1), this::allowedTarget)) {
            var point = target.getBoundingBox().expand(margin).raycast(eye, end);
            if (point.isPresent() && inReach(target) && eye.squaredDistanceTo(point.get()) < maxDistance) {
                maxDistance = eye.squaredDistanceTo(point.get());
                nearest = target;
            }
        }
        return nearest;
    }

    private boolean isHoldingMaceOrSwap() {
        return mc.player.getMainHandStack().isOf(Items.MACE)
                || InventoryUtils.findMaceWithEnchantmentInHotbar(null, true) >= 0;
    }

    private void disengageRotations() {
        if (silent.getValue() && RotationUtils.isTracking() && RotationUtils.isControlledBy(this)) {
            RotationUtils.rotateBack(true, horizontalSpeed.getRandom(), speedVert.getRandom(), getAimType());
        } else if (RotationUtils.isControlledBy(this)) {
            RotationUtils.stopTracking();
        }
    }

    private RotationUtils.AimType getAimType() {
        if (aimMath.is(Concat.of(ModuleText.S34))) return RotationUtils.AimType.WINDMOUSE;
        return aimMath.is(Concat.of(ModuleText.S33)) ? RotationUtils.AimType.BLATANT : RotationUtils.AimType.REGULAR;
    }

    private RotationUtils.EntityPoints getPointsMode() {
        if (aimVector.is(Concat.of(ModuleText.S37))) return RotationUtils.EntityPoints.CLOSEST;
        return RotationUtils.EntityPoints.STRAIGHT;
    }

    private boolean isTargetInFov(LivingEntity entity) {
        return CombatUtils.isInFOV(mc.player, entity, yawFov.getValue(), pitchFov.getValue());
    }

    private LivingEntity selectPriorityTarget(List<LivingEntity> targets) {
        if (targets == null || targets.isEmpty()) return null;

        if (targetPriority.is(Concat.of(ModuleText.S41))) {
            return targets.stream()
                .min(java.util.Comparator
                    .comparingLong(HurtTickHandler::getAttackedEntityTick)
                    .thenComparingDouble(e -> mc.player.squaredDistanceTo(e))
                    .thenComparingDouble(this::getFovDelta)
                )
                .orElse(null);
        }

        if (targetPriority.is(Concat.of(ModuleText.S40))) {
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

    // Generated literals use the existing EncryptedString decoder. Keep decoded values stable.
    private static final class ModuleText {
        private static final String S0 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd786,0xcb86,0xbe7c,0x1e90,0x4646,0x0bbe,0x814a,0x6530,0x816e}, new char[]{0x812f,0xcbf3,0x0bca,0xd7e9,0x1eb0,0x657d,0x4627,0xbe1f}).toString();
        private static final String S1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0fa3,0xc03d,0x43a1,0x4018,0x4009,0x4053,0xc02c,0xf231,0x9597,0x43bb,0x401e,0x4011,0xc034,0x0f81,0x95d8,0xc038,0x0fb4,0x762c,0x5b42,0x0fa3,0x7624,0xf27a,0x5b5b,0x43a2,0x5b4b,0x5b4f,0x9597,0x5b40,0x762d,0xc026,0xf231,0x5b4f,0x43ba,0x9597,0x43b7,0xf27e,0x7621,0xf27d,0x401a,0x7631,0x0fa9,0x0fa9,0x95d6}, new char[]{0x0fc0,0x5b2e,0x407d,0xf211,0xc055,0x95b7,0x43d6,0x7645}).toString();
        private static final String S2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf4a8,0xf4a8,0xae36,0x2624,0xf05f,0xa11d,0x60be,0xdc54,0xdc07,0xae30,0xf05d,0xa117,0x60f5,0xf047,0xf4af,0xf33e,0x263f,0xa11c,0xf32d,0xf4a1,0xf325,0x60b4,0xf33b,0x263f,0x263f,0xf4e4,0xf327,0xae27,0xdc0e,0xa102,0x60ab,0xf010,0xf052,0xa103,0x60ab,0xf4af,0x60ba,0xae27,0xf044,0x266a,0xa11d,0x60b5,0xf051,0xae36,0x60b8,0xf4a1,0xf36c,0x266b,0xdc1d,0xdc18,0xf4ad,0xf325,0x60b5,0xf321,0x60b8,0xdc11,0xf059,0x2670,0xa100,0xae36,0xdc1a,0x263f,0xae27,0xdc00,0x267e,0xae1e,0xf323,0xae73,0xa14e,0xdc00,0xae3f,0xf4b0,0xf36c,0xa11d,0xf044}, new char[]{0xae53,0xf34c,0x60db,0xf4c4,0xa16e,0x261f,0xf030,0xdc74}).toString();
        private static final String S3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x65ab,0xd3b5,0x2f59,0xcb67,0x2f17,0x65bd,0xcb6a,0xcb65,0xb8f9,0xb8ad,0x65b6,0x9351,0xcb74,0x0da1,0x2f14,0x9356,0xcb61,0x2f16,0x9343,0x65b6,0xb8e2,0x2f1c,0x2f1c,0xd3fc,0xb8e4,0x43bd,0x931e,0xd3ac,0x43b1,0xb8e9,0xd3bd,0x9310,0x0dab,0x43ab,0x2f18,0xcb65,0x9353,0x65b6,0x2f54,0xd3bf,0x65bd,0xb8ee,0x43bc,0xcb74,0x43f9,0x2f1f,0xb8ad,0x65bc,0x65b1,0xd3b0,0x9343,0x935f,0x65a8,0x0db4,0x0db4,0x0de0,0x9359,0x65ab,0xd3fc,0x43b6,0x0db7,0x0da1,0x9310,0xb8ff,0x2f18,0x2f59,0x0da9,0xcb72,0x9351,0x935c,0x43b1,0x65ab,0x0de0,0xb8e3,0x2f17,0x9354,0x65bd,0x9355,0xcb6d,0x2f09,0x9358,0x0da5,0x9357,0xb8ff,0xb8ec,0xb8e1,0x65f8,0x43b8,0x9342,0xd3fc,0xd3b8,0xb8fe,0xd3af,0x0daf,0x0da1,0x0da9,0x65b4,0xcb6e,0xcb26,0xcb63,0x43b0,0xd3b8,0x43ab,0xcb28,0x0db4,0xd3fc,0x0de0,0x9364,0x2f09,0x43b6,0x65f4,0x43b2,0x2f0d,0xd3b8,0xb8cc,0x43b8,0x2f0c,0x43b4,0xd3a8,0xcb61,0xcb75,0x43aa,0x65b6,0xb8e9,0xcb6b,0xd3fc,0xcb63,0x43ba,0x43bc,0x0da9,0xd3fc,0x2f16,0x65be,0x2f11,0xb8ad,0xb8f9,0xd3fc,0xb8ad,0xcb74,0x43ba,0xb8ad,0x0de0,0x9342,0xd3b9,0x0dae,0xcb63,0x2f10,0x65f8}, new char[]{0x9330,0x2f79,0xb88d,0xcb06,0x43d9,0x0dc0,0x65d8,0xd3dc}).toString();
        private static final String S4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x000a,0x7d58,0xc176,0xd771,0xd734,0x0008,0xc9ea,0xc179,0xe5d3,0xc9f0,0x0006,0x489f,0x488a,0x489f,0xc9e0,0x000b,0xd760,0x000c,0x9926,0xc139,0x4885,0xc178,0xc179,0xd776,0xe5c6,0xe5d4,0xc9e8,0xd766,0x7d51,0x000b,0xc171,0x488c,0x001b,0xe5ce,0xc9e4,0x993d,0x001b,0xe5ce,0xe5cf,0x4884,0x9969,0x7d5a,0xc137,0xc9e4,0xc9eb,0x996d,0x9969,0xe587,0xd734,0x7d59,0xc9f7,0xd734,0xc13a,0x001c,0x4893,0xe5ce,0xc174,0xc9c0,0x997f,0xe5d5,0xd760,0x4888,0x7d47,0x9926,0xd734,0x7d54,0xc16e,0x7d50,0x7d15,0x7d15,0x001b,0xe5d3,0x9968,0x9975,0x4899,0x488e,0xc9e0}, new char[]{0xc985,0x48eb,0x006f,0xe5a7,0xc117,0x9906,0xd714,0x7d35}).toString();
        private static final String S5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x26da,0x6a27,0x819e,0x3d65,0x1294,0x6a3a,0x81cd,0x3d72,0xf4d4,0x3d76,0xf4d2,0x2263,0x222b,0x12da,0x1296,0x3d74,0x81d7,0x269b,0x26d7,0x227f,0xbac0,0x81ca,0x3d70,0xbac8,0x6a37,0x129b,0x226e,0x26cf,0x222b,0x26de,0x3d67,0x6a63,0x26de,0x26cc,0x3d70,0xbad2,0xbac4,0xf4db,0x81ca,0xf4d7,0x227f,0x81d7,0xbac4,0xbad5,0x8193,0xf4d9,0x1297,0x6a2a,0xf492,0x129b,0x2262,0x6a27,0x129f,0xf4df,0x12be,0xbad2,0xf49c,0x81ec,0x3d66,0x6a2b}, new char[]{0x81be,0x12fa,0x3d15,0xf4bc,0xbaa1,0x220b,0x26bb,0x6a43}).toString();
        private static final String S6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x27d2,0xefe5,0x83e6,0xefe9,0x3817,0x125f,0x27c3,0x6cf9,0x83ef,0x21d7,0xbf34,0x6ce9,0x3845,0x6cfe,0x124f,0x6cef,0xbf7d,0xbf7d,0x83a3,0x1258,0x6cf2,0x27df,0x6cf3,0xefee,0xefc7,0x83ea,0x27c5,0x27c4,0x27d0,0x6cbb,0x83e7,0xbf3f,0x6cf8,0x27d6,0x27d2,0x21dc,0x21c0,0x27c5,0xefeb,0x3856,0x21c6,0x83a3,0x3859,0x1259,0x3842,0x83d3,0x385c,0x21d5,0x1203,0x3852,0x120d,0x83ec,0xefe9,0xbf2e,0xbf33,0x21d0,0x21d7,0xbf30,0x83ad,0x27d2,0x1249,0x21c0,0xbf7d,0xbf38,0xefe7,0xbf35,0xefe6,0x125f,0x83a3,0x83fa,0x385f,0x2194,0xefe9,0x3852,0x1248,0x6cfa,0xefed,0x120d,0x21d1,0x3859,0x6cef}, new char[]{0x8383,0x122d,0xbf5d,0xef88,0x21b4,0x6c9b,0x3837,0x27b7}).toString();
        private static final String S7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4051,0x710d,0xeda4,0x405f,0x4019,0x3345,0x4009,0xe227,0x7118,0x4003,0xb805,0x3359,0xedae,0x330c,0x6f90,0x6fdd,0x335e,0xb841,0xedb8,0xa826,0xede1,0xb804,0x711e,0xeda5,0xeda8,0x6fc7,0x3352,0xe265,0xedb4,0xb808,0xe266,0xb80e,0x3343,0x7110,0x6fc2,0x335f,0x4003,0xa874,0x711e,0xa867,0x3358,0x4007,0xa863,0x711a,0x710b,0x7117,0x6fc2,0xedb2,0x4014,0x6fd9,0xe275,0xa863,0xb803,0xe227,0xe261,0xe266,0xe273,0xe264,0x7110,0x6fd8,0x4017,0xb812,0xa863,0xb80f,0xb841,0x6f90,0xa863,0xb815,0x712c,0x401d,0x7107,0xe269,0xa826,0xeda5,0x4014,0xa864,0x3353,0xa826,0x6fdb,0x3352,0xe227,0x6fe3,0xb831,0xedb5}, new char[]{0xb861,0xe207,0x717f,0x4071,0xa806,0x6fb0,0x3337,0xedc1}).toString();
        private static final String S8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd652,0xd653,0x79f5,0x381b,0x7efc,0x8ea4,0x5b91,0x3816,0xe62b,0xe667,0x79f3,0x5b9c,0x81e3,0x8eb5}, new char[]{0x3853,0xd63c,0x5bfd,0x7991,0xe60b,0x8ed0,0x7e93,0x81c3}).toString();
        private static final String S9 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x98b3,0xf16f,0x8f50,0x5548,0xbd1c,0xb5ca,0x2752,0x3d23}, new char[]{0xbd51,0x98da,0x273c,0xb5ea,0x3d65,0x5529,0xf103,0x8f3c}).toString();
        private static final String S10 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4891,0xefb3,0xe0d7,0x5281,0xffa1,0xa8f2,0xefa5,0x0eba,0xad4f,0xe0c7,0xad70}, new char[]{0xad23,0xefd2,0xe0ae,0x0ed4,0xa895,0xff81,0x52c5,0x48f4}).toString();
        private static final String S11 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb172,0x4455,0x92fb,0xe825,0x4481,0x7cd5}, new char[]{0x7cf5,0x4421,0xe84c,0xb111,0x9290,0x44f2,0x46cf,0xc776}).toString();
        private static final String S12 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9e4a,0x9205,0xb8e4}, new char[]{0x9246,0x9e1a,0xb8b7,0x6a42,0x06ab,0x8fae,0x5bcc,0xa115}).toString();
        private static final String S13 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x815e,0x66e6,0x930e,0x9272,0x9318,0x8144,0x1b2b,0xac83,0xe849,0x66f0,0x3210,0xe855,0x3220,0x927f,0x1b67,0x3200,0xac9f}, new char[]{0x3244,0x9217,0xaced,0x6683,0x812d,0xe821,0x9361,0x1b0b}).toString();
        private static final String S14 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x63fd,0xb886,0xbbdf,0x63f6,0x53ea,0xb891,0x230d,0x1e9a,0x4463,0x1ea2,0xeef6,0x230d,0xbbdd}, new char[]{0x1ef6,0x236c,0x6384,0xbbba,0xb8f4,0xee82,0x53ca,0x4433}).toString();
        private static final String S15 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa556,0x1e2c,0x104f,0xa555,0x5d12,0x9403,0x05eb,0x52c7,0x52fc,0x41c8}, new char[]{0x5293,0xa537,0x1e5e,0x058c,0x9466,0x41bc,0x106f,0x5d5f}).toString();
        private static final String S16 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1b08,0x4b22,0xafa1,0x4b09,0xde31,0x6700,0xde3a,0x670c,0x8454,0x4b3b,0x964f,0x965a,0x4b39}, new char[]{0x4b71,0x4b4b,0x962e,0x6762,0xde55,0xafd9,0x1b28,0x8411}).toString();
        private static final String S17 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4970,0xd2d5,0xb060,0x72cd,0xadc1,0x5824,0x23de,0x9834,0x23e0}, new char[]{0x23b3,0x72b9,0xd2a0,0x491e,0xb040,0xad92,0x9858,0x5845}).toString();
        private static final String S18 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6d7d,0xe93b,0x7491,0x6d43,0x74c5,0xf617,0xf5e7,0xdec2,0xf647,0x2f79,0xe9be}, new char[]{0x6d10,0x74e5,0xf662,0xdeac,0xf5c7,0x2f2a,0xe9d2,0xe95a}).toString();
        private static final String S19 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe23a,0xc696,0x7bcf,0x6895,0x689e,0x712e,0x7bcf,0x5a4c,0x2623,0x7127,0xadf7,0xadd6}, new char[]{0xada4,0x7146,0x7ba6,0x68fb,0x5a20,0xc6f2,0x2603,0xe27e}).toString();
        private static final String S20 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x27ce,0xfb88,0x9023,0xf3bd,0xd077,0x054e}, new char[]{0xfbca,0x9051,0x27ab,0x052f,0xd014,0xf3d5,0x3245,0xd343}).toString();
        private static final String S21 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb98e,0x7cbd,0xe10e,0xe138,0x3f5c,0x1ed6,0xcc77,0xeadb,0x4e61}, new char[]{0xe15d,0x1ea2,0x3f3d,0xeaa2,0xb9ae,0xcc3a,0x4e00,0x7cde}).toString();
        private static final String S22 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x51aa,0x3d86,0x9d38,0x2ec1,0xddc6,0xdd5a,0x9d08,0xcc20,0x12b2,0x2ed4}, new char[]{0x9d5b,0x2ea4,0x3de3,0x51cb,0xcc52,0xdde6,0xdd17,0x12d3}).toString();
        private static final String S23 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5547,0xb0f1,0x81c3,0xfff6,0xe8f6,0x556a,0x4062,0x4069,0xf55e,0x97aa}, new char[]{0x550e,0x4007,0xb081,0x81b6,0xf52a,0x978a,0xe8bb,0xff99}).toString();
        private static final String S24 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2473,0x1dbe,0x63c1,0xc1b5,0xd3e7,0xf040,0x99a4,0x35e3,0x1daf,0xd3c1}, new char[]{0xd392,0x1ddb,0x99ca,0x3587,0xc195,0xf009,0x63af,0x2403}).toString();
        private static final String S25 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb395,0x4c21,0x7297,0xfd9e,0xd647,0x171d}, new char[]{0xb3d1,0xd62e,0x4c53,0x72f2,0xfdfd,0x1769,0x5f67,0x13d8}).toString();
        private static final String S26 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x438d,0xe6e3,0x1e51,0x4fed,0x179f,0xb4e8,0xefcd}, new char[]{0x1e1a,0x17fa,0xefb4,0x4fcd,0xe6b0,0xb481,0x43e0,0x52d8}).toString();
        private static final String S27 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8e11,0x0869,0xb1d4,0xe839,0x893b,0xf1b5,0xd38b,0x6898}, new char[]{0x68d5,0x0808,0xe85a,0xf1d0,0x8e31,0x8970,0xb1b1,0xd3f2}).toString();
        private static final String S28 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5358,0xc4ac,0x9eaa,0x84c6,0x371a,0xbf42,0xecdc}, new char[]{0x9eeb,0xc4d4,0x84a3,0xecfc,0xbf09,0x533d,0x3763,0x7327}).toString();
        private static final String S29 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x675e,0xbeb9,0xc49d,0xe12e,0xb481,0xb4bb,0x0462,0x30a6,0x6db0,0xbebd}, new char[]{0xb4e8,0xbed0,0xc4f1,0x6dd5,0x040c,0xe15a,0x3086,0x671f}).toString();
        private static final String S30 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa24b,0xdd63,0x9cfb,0x48cf,0x0701,0x9ce6,0xee6a,0x9ec3}, new char[]{0x488e,0x9eb6,0xee1e,0xa224,0x9cc6,0xdd22,0x9c92,0x076c}).toString();
        private static final String S31 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3cfb,0x1e8e,0x7c1e,0x6c31,0x1be7,0x4a6e,0x2a04,0x7b8b}, new char[]{0x3cba,0x2a6d,0x1ee3,0x6c11,0x4a23,0x1b86,0x7c6a,0x7be3}).toString();
        private static final String S32 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6aea,0x5258,0x32e8,0x67ef,0xb110,0xa00d,0x1d57}, new char[]{0xb142,0x6a8f,0x1d30,0xa078,0x5234,0x678e,0x329a,0x13fc}).toString();
        private static final String S33 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb577,0x65de,0x1e4b,0x8b24,0x0d95,0x296c,0x72a4}, new char[]{0x0dd7,0x8b48,0xb516,0x1e3f,0x72c5,0x2902,0x65aa,0x5581}).toString();
        private static final String S34 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xcc3d,0x3d83,0xcc0f,0xbc70,0xcd48,0x2d2b,0xb30c,0xb5a0,0x94e0}, new char[]{0xcc6a,0x2d42,0x3ded,0xb5c4,0xcd05,0xb363,0x9495,0xbc03}).toString();
        private static final String S35 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x02ac,0x113d,0x45fe,0xf8d2,0x16b3,0x0282,0x49ef,0x4bd6,0x9cb4,0x49f4}, new char[]{0x02ed,0x4986,0xf8bf,0x45de,0x16e5,0x4bb3,0x9cd7,0x1149}).toString();
        private static final String S36 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xfa99,0xd0f4,0xfa8d,0x3730,0x5f8e,0x1dc6,0xe03d,0x75fc}, new char[]{0x5fdd,0x7588,0x1db4,0xfaec,0xd09d,0xfafe,0xe055,0x3744}).toString();
        private static final String S37 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x225f,0x6e0e,0xe74a,0x159b,0x1b2f,0xb657,0x911a}, new char[]{0xe709,0x1b43,0x15f4,0x9169,0x6e6b,0xb624,0x222b,0x2ee6}).toString();
        private static final String S38 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2425,0x5041,0x7147,0x4083,0x4096,0xdb4e,0xd16f,0x0e05,0x714f,0x2429,0xd136,0x244b,0x0e05,0x246d,0x505c}, new char[]{0x241f,0x712e,0x502e,0x40e4,0x2440,0x0e71,0xd116,0xdb1e}).toString();
        private static final String S39 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x623a,0x1f0a,0xe3ce,0x33ba,0x3aef,0x2f6c,0x5aa1}, new char[]{0x6274,0xe3ab,0x33db,0x5ad3,0x3a8a,0x1f79,0x2f18,0x8958}).toString();
        private static final String S40 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb60f,0x0bc6,0xac3f}, new char[]{0xac79,0xb640,0x0b90,0x8c94,0xd1ce,0x27fe,0x9019,0x3968}).toString();
        private static final String S41 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0bc2,0xa29c,0x3896,0xab57,0x4fa5,0xc890,0x03a4,0x8d13}, new char[]{0x4fed,0x0bb7,0xab25,0x03d0,0x38c2,0xc8f9,0x8d70,0xa2f7}).toString();
        private static final String S42 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7a28,0xd954,0x5bc3,0xcc62,0x10cd,0xa782}, new char[]{0xa7d0,0xd935,0x7a46,0x10a9,0xcc0d,0x5bae,0x2b8c,0x6e5a}).toString();
        private static final String S43 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6767,0x5d92,0x1116,0x0f4a,0x62ae,0x6c9d,0x0253,0x025a,0x0f4c,0x62b0,0xf618,0x5d91,0x1115}, new char[]{0x5dc6,0x023b,0x62dc,0x117a,0x0f39,0xf67f,0x670f,0x6cbd}).toString();
        private static final String S44 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xab49,0x66c8,0x1481,0x26f5,0xb0d4,0xc4c9,0x2a85,0xc4e0,0xb0d7,0x2b46}, new char[]{0xc481,0xb0b8,0x14f3,0x2aec,0xab33,0x66a7,0x269b,0x2b32}).toString();
        private static final String S45 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x55b6,0x28cf,0x4820,0x04ea,0x4a2d,0x7032,0x945f,0x9a55}, new char[]{0x9409,0x4a48,0x9a27,0x55c2,0x4849,0x7051,0x048b,0x28a3}).toString();
        private static final String S46 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb366,0x5756,0x3ad9,0x7f79,0xdb5e}, new char[]{0x7f2b,0xb307,0x3ab7,0xdb39,0x5733,0xca4c,0xceec,0xdfde}).toString();
        private static final String S47 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd14f,0xe8cc,0x94d8}, new char[]{0x9481,0xe8ad,0xd138,0x3c82,0x4d4a,0xe8b0,0xd5ea,0x5348}).toString();
        private static final String S48 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb8e3,0xfbeb,0xa2a4,0x7e74,0x1823,0x2bbd,0x2bbb,0x7ff6,0xe339}, new char[]{0x2beb,0xe350,0xfb9f,0xb880,0xa2cc,0x7e54,0x7fb0,0x186c}).toString();
        private static final String S49 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x63bb,0x7f39,0x1335,0x972e,0x33b8,0xc868,0x7f38,0x8837,0x339b,0x5f77}, new char[]{0x33d6,0x7f4c,0x5f1b,0x8843,0x135c,0xc818,0x9741,0x63d2}).toString();
        private static final String S50 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa08b,0xfe66,0xc1c8,0xf065,0xb5d6,0xa096,0xaff5,0xb62e,0xb48d,0xf042,0xb5ca}, new char[]{0xf036,0xa0e3,0xb5b9,0xaf82,0xb60e,0xfe35,0xb4f9,0xc1a9}).toString();
        private static final String S51 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6d76,0xa51e,0x151c,0xff90,0x5ae7,0xfc6b,0x5bb4}, new char[]{0x156b,0xfff1,0x6d1f,0xa56a,0x5bdd,0xfc05,0x5a80,0xaf62}).toString();
        private static final String S52 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6b53,0x397e,0x546c,0xa874,0xf2dd}, new char[]{0xf299,0x391b,0xa816,0x6b26,0x540b,0xa063,0x11fe,0x7aa1}).toString();
        private static final String S53 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb5a5,0x9566,0x9561,0xe751,0x3f18,0xb5a4,0xe746,0x5611,0x29b1,0x5611,0xfeb9,0x3f50,0xfebd,0x6e9f,0x6e97,0x29bc,0x29b4}, new char[]{0x29d5,0x9512,0xfec9,0x5670,0xe732,0x6efc,0x3f70,0xb5c1}).toString();
        private static final String S54 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xbd87,0x7281,0x7469,0x3bbe,0x7426,0x4677,0x7bc8,0x3bea,0xe175,0xfdd4,0xe151}, new char[]{0xe110,0x741c,0x3bca,0xbde8,0x4657,0x7b85,0xfdb5,0x72e2}).toString();
        private static final String S55 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x90e4,0x8eb2,0xce4a,0x5e9b,0x0319,0x2bfc,0xb5e7,0xa1e9}, new char[]{0x0370,0xa187,0x8ed3,0xb584,0x5eef,0xce23,0x2b8a,0x9081}).toString();
        private static final String S56 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb2a8,0x69f8,0xd8f5,0x69ef,0x4204,0xe40f,0x745a,0xb2b2,0x02b6,0xe7ee,0xb2b6,0xe413,0x02af,0xe7e0,0xd8ba,0x4209,0xe40b,0xd8ba,0x69bd,0x740e,0xe7fc,0x02af,0x4204,0x740a,0x740f,0xe7eb,0xb2a4,0x02ae}, new char[]{0xb2c5,0x02c1,0xe78f,0x747a,0xe467,0x4261,0x699d,0xd89a}).toString();
        private static final String S57 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xcced,0x422f,0xf323,0xa2be,0x6da1,0xd871,0x484d,0xcce4,0xa2e1,0xd867,0xd873,0xf33c,0xa2be,0xf329,0x1d52,0x4859,0x1d44,0x1d5a,0x6dee,0x426a,0xccfb,0x4851,0x427a}, new char[]{0xcc88,0x4821,0xf34c,0x1d33,0xa2cc,0x420f,0xd817,0x6d81}).toString();
        private static final String S58 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4c75,0x69c3,0x70aa,0x56b4,0x69d9,0x70a2,0x70e6,0x69c8,0x7559,0x754c,0x055c,0x054e,0xd1c3,0xd1ce,0x70f8,0x56b4,0x4c7e}, new char[]{0x69ad,0x4c1a,0x5694,0x752d,0xd1a2,0x708a,0x70cd,0x0539}).toString();
        private static final String S59 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x23b9,0x4255,0xefb1,0xe216,0xe21f,0xe21d,0x9895,0x9717,0x23fc,0x1d1c,0xe21c,0x1d19,0xefb1,0x23b9,0xe21c,0x425c,0x1d4b,0xefe5,0x4255,0xefe3,0x4256,0x40b8,0x23fc,0x23ae,0x9703,0x1d03,0x9711,0x40b5,0x970d,0x40b2,0x40fd,0x9716,0x9897,0xefe3,0x40ba,0x9893,0x1d00,0x9895,0x9882}, new char[]{0x9762,0x98f6,0x1d6b,0x40dd,0xe279,0xef91,0x23dc,0x4239}).toString();
        private static final String S60 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb70d,0x58a6,0x7685,0x58b8,0x7026,0x0421,0x27e7,0x4f45,0x7024,0xc057,0x27a8,0x0475,0xb70a,0xc016,0x7680,0xb717,0x7691,0x27a6,0x4f44,0x0468,0x4f5e}, new char[]{0x76f7,0x27c7,0x4f2c,0x0401,0xb77e,0x58c8,0x7041,0xc036}).toString();
        private static final String S61 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x01b1,0x4e4e,0x01fe,0x60ab,0xca48,0x4e4c,0xe0c6,0x78ff,0x78e4,0x01e4,0x4e0f,0x60ac,0x60be,0x33d9,0x33ce,0xca51,0x78f6,0x428e,0xe0c6,0x428c}, new char[]{0x4e2f,0x0191,0x60ca,0x7890,0xca25,0xe0a7,0x33ba,0x42e7}).toString();
        private static final String S62 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd091,0x7e4f,0x5085,0x3c92,0xffac,0x5eab,0xab1c,0x3a65,0x7e05,0xab5d,0x3a2e,0x7e50,0x5080,0xab13,0xffef,0x50c1,0x3a25,0xd0c6,0x3cde,0x5eed,0x5eab,0xffa5,0x3c9b}, new char[]{0x5ecd,0xab7d,0xffc0,0x3cfe,0x50a5,0x3a00,0x7e61,0xd0f7}).toString();
        private static final String S63 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0ae5,0x0af3,0x3f2d,0x40ed,0x73da,0xc64c,0x6472,0x23cd,0x3f3d,0x961f,0x40f2}, new char[]{0x409e,0x0a84,0x3f54,0x641c,0x73bd,0x963f,0xc628,0x23a8}).toString();
        private static final String S64 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xfa9a,0x1bb8,0x3a2a,0x5b75,0xec74,0xfa8d,0xe7c6,0x5356,0x8782}, new char[]{0xfaf9,0xe7b6,0xec07,0x1b98,0x5b19,0x3a43,0x533b,0x87eb}).toString();
        private static final String S65 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5440,0x14d7,0xbfe1,0xef52,0xef5c,0x6c84,0x7cff,0xbfb3,0x2668,0x14dc,0x263c,0x6c9e,0x5458,0x14d2,0x6c98,0x2668,0x5e90,0x545b,0xbffa,0x7cba,0x5ed8,0x7cad,0x14c3,0x5e96,0xef51,0x5451}, new char[]{0x5437,0x14b3,0xbf93,0x261c,0x6cf7,0x5ef8,0xef35,0x7cdf}).toString();
        private static final String S66 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x42a6,0xc005,0xc009,0xc2c6,0xf250,0x42ba,0xf279,0xc5bf,0x29a4,0xc00d,0x5463,0xc282,0xc2cb,0x29f5,0x8916,0x42b7,0xf261,0x5475,0x890a,0xc5b9,0x5463}, new char[]{0xf214,0xc06c,0x42d4,0x5406,0xc2a2,0xc5cb,0x896f,0x2984}).toString();
        private static final String S67 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xafe9,0x8461,0xd1df,0x4f47,0x8468,0x65ac,0xc8d7,0xd1c6,0xfcaa,0x4f44,0x846c,0x65e3,0xd597,0xaffb,0xd58d,0xd581,0x65a7,0xd1c3,0xfca5,0xfcbb,0xc8ca,0x4f09,0xafef,0x846a,0xc8d6}, new char[]{0x840f,0x65c3,0x4f29,0xfccc,0xd5e4,0xaf88,0xd1b6,0xc8b8}).toString();
        private static final String S68 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7c0d,0x0952,0x8989,0x2d81,0x90c2,0x2d87,0x0f0b,0x86f1,0x4dd1,0x9096,0x095c,0x86a3,0x7c06,0x0f1a,0x9089,0x8997,0x4d81,0x86ea,0x095d,0x0f13,0x7c1a,0x8985}, new char[]{0x0f7c,0x0933,0x8683,0x90e2,0x89e0,0x7c63,0x2de6,0x4df1}).toString();
        private static final String S69 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5103,0xcd46,0x5614,0x3d8d,0xc64c,0x6761,0x5619,0x5105,0x3d8d,0xc67e,0x676a,0x5618,0xcd0b,0x565e,0x83f2,0xc67a,0x3dc2,0xc65c,0xcd0b,0x83fc,0x3dc2,0x676a,0x5102,0x511c,0x83f0,0x6761,0x83fe,0xc61b,0xcd44,0xcd55,0x511c,0x83e5,0x3d8d,0xcd4b,0x83f2,0xc678,0x5613,0xcd0e,0xc67d,0x6764,0x5613,0x3dd9,0xc679,0xcd55,0x3d8d,0x6760,0x83f9,0xc677,0x5613,0xc61b,0xc679,0x5118,0xc61b,0xc657,0x6766,0xc653,0x5113}, new char[]{0xcd27,0x3dad,0x8391,0xc616,0x6705,0x5177,0xc63b,0x5676}).toString();
        private static final String S70 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x852f,0x69f6,0xd818,0x544b,0x546f,0x6654,0x1c20,0xa47b,0xbba1}, new char[]{0x5426,0xa415,0xbbd1,0xd86d,0x855b,0x69d6,0x1c73,0x663d}).toString();
        private static final String S71 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2279,0x9635,0x11f5,0xeb93,0x82ce,0x0b39,0xa8a7,0xbc04,0x1c9d,0xd109,0x379e,0x654f,0xb17d}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536}).toString();
        private static final String S72 = new EncryptedString(new char[]{0x2391,0x39f2,0x518d,0x39a1,0x6af7,0x51d9,0x23c2,0x39be,0x39a6,0x51d7,0x5183,0x51d1,0x39a6,0x6ae3,0x23de,0x39f2,0x51c2,0x6aef,0x23d4,0x39bb,0x51c6,0x23de,0x23d6,0x2391,0x6af3,0x51ca,0x39b7,0x5183,0x23df,0x51ce,0x6ae2,0x39bb,0x51c0,0x5183,0x6aab,0x6ae2,0x6ae6,0x23de,0x6ae8,0x39f2,0x6ae8,0x39bc,0x23d9,0x23d6,0x51da,0x2391,0x6aa9,0x6aeb,0x6ae0,0x39b3,0x51c4,0x6aea,0x23c1,0x2391,0x39b7,0x6ae2,0x39a1,0x23df,0x51c8,0x23df,0x23d4,0x6ae2,0x51e2,0x39b3,0x6af3,0x51d7,0x6ae2,0x6af5,0x6af2,0x23d2,0x23d4,0x23d3,0x39bd,0x6aa7,0x51cd,0x518f,0x6ae9,0x51c0,0x39b0,0x6afe,0x23d2,0x23d4,0x23d0,0x39a6,0x51ca,0x39bc,0x51ce,0x51e7,0x51d1,0x39bb,0x39a1,0x39bc,0x39f2}, new char[]{0x51a3,0x23b1,0x6a87,0x39d2}).toString();
        private static final String S73 = new EncryptedString(new char[]{0x6af7,0x6ad7,0x39a0,0x39be,0x23c2,0x39a6,0x51da,0x51c6,0x23c1,0x2391,0x6ae2,0x51e2}, new char[]{0x51a3,0x23b1,0x6a87,0x39d2}).toString();
        private static final String S74 = new EncryptedString(new char[]{0x6af4,0x51e0,0x23dc,0x23c4,0x51cc,0x39a6}, new char[]{0x51a3,0x23b1,0x6a87,0x39d2}).toString();
        private static final String S75 = new EncryptedString(new char[]{0x51f1,0x6af0,0x39b7,0x23de,0x39f2,0x6af3,0x23d0,0x51ef}, new char[]{0x51a3,0x23b1,0x6a87,0x39d2}).toString();
        private static final String S76 = new EncryptedString(new char[]{0x51cd,0x6aeb,0x39b6,0x23d0,0x39b3,0x6ae2,0x23d2,0x51e1}, new char[]{0x51a3,0x23b1,0x6a87,0x39d2}).toString();
        private static final String S77 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x253f,0x250d,0xf4ef,0x2527,0x671d,0xa38d,0x6758,0xdd41,0xa380,0xdd6b,0x4f08,0x4f5d,0xf4f6,0x2515,0x2532}, new char[]{0x2546,0xf49b,0x4f7d,0xdd05,0x6778,0x2561,0xa3ec}).toString();
        private static final String S78 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2f72,0x2f45,0x2f5a,0x2f61,0x2f6d,0x2f71,0x2f4b,0x2f6d,0x2f46,0x2f37,0x2f39,0x2f01,0x2f3c,0x2f5c,0x2f2a,0x2f78}, new char[]{0x2f1e,0x2f19,0x2f04,0x2f0f,0x2f0a,0x2f35,0x2f30,0x2f3b,0x2f26,0x2f21,0x2f2c,0x2f57,0x2f52,0x2f5d,0x2f58,0x2f43}).toString();
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1f18,0x2fdd,0x1241,0x0229,0x9927,0xa708,0xfb22,0xbe1f,0x7c98,0x7ce0,0xe389}, new char[]{0x027a,0x9953,0xe3fc,0x2fb3,0x7cb8,0xfb75,0x1f7d,0x1220,0x7c90,0xbe70,0xa766}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x857d,0xa3a6,0xb8c1}, new char[]{0xb880,0x8505,0xa3c3,0x3a59}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xcbaa,0x8156,0x2ff4,0xab6a,0xe82b}, new char[]{0xab39,0x2f83,0x8139,0xe859,0xcbce}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf50a,0x676d,0xf759,0xb3e0,0x1fb3}, new char[]{0x675c,0xf52a,0xb3b4,0x1fd2,0xf729}).toString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x54f8,0x429c,0x9c05,0x1b87,0xd3bc,0x4799,0x52e0,0xc9fb,0xfd0b,0x8fbf,0xc327,0x941a,0x4fb6,0x8c87,0xf3f4,0x2260}, new char[]{0x2233,0x9472,0xfd62,0xd3d9,0xc997,0x42f8,0x8f9f,0x52a4,0xc355,0xf395,0x9c6c,0x5496,0x8ca7,0x1bc4,0x4fe6,0x47ca}).toString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe24f,0xd0ec,0x1b40,0xa18d}, new char[]{0xd0cc,0xe22c,0xa1fd,0x1b33}).plainString();
        static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9a62,0xaf6a,0xc85b,0x66eb,0xb26f,0x2f63,0xfa8b,0x0472,0xdf8e,0x43ff,0xb133,0xcc4e,0x83f0,0x3ccf,0xb03a}, new char[]{0x83a0,0x0400,0xb156,0xb242,0xc81a,0x6699,0x4392,0xdfae,0xaf25,0x3ca1,0x2f43,0xb07c,0xfaea,0xcc22,0x9a0e}).toString();
        static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa6b3,0xd07e,0x6510,0x974f,0x35c9,0x5fdc,0xc8f0,0xb2f7,0xb976,0xe263,0xdde5,0x5b6c,0x539a,0x5798,0xe84f,0xea0d,0x5ecf,0xc7f5,0xf474,0x49ad,0x277c}, new char[]{0x35a8,0x270e,0xd013,0xc79c,0x53f4,0x6577,0x5eef,0x49c0,0xf415,0xa6d0,0x57fd,0x976f,0x5b44,0xe829,0xdd84,0xe20f,0x5fb0,0xea64,0xb299,0xc897,0xb95f}).plainString();
    }
}
