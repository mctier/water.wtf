package com.utilitymod.echo.features.impl.combat;

import com.utilitymod.echo.Echo;
import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventHandleInput;
import com.utilitymod.echo.event.impl.EventStartAttack;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.impl.misc.TargetControlModule;
import com.utilitymod.echo.features.settings.impl.BoolSetting;
import com.utilitymod.echo.features.settings.impl.IntSetting;
import com.utilitymod.echo.features.settings.impl.ModeSetting;
import com.utilitymod.echo.handlers.InputHandler;
import com.utilitymod.echo.handlers.impl.SwapStateManager;
import com.utilitymod.echo.utils.combat.TargetUtils;
import com.utilitymod.echo.utils.inventory.InventoryUtils;
import com.utilitymod.echo.utils.math.TimerUtils;
import com.utilitymod.echo.utils.player.PlayerUtils;
import com.utilitymod.echo.utils.strings.Concat;
import com.utilitymod.echo.utils.world.WorldUtils;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ShieldItem;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import org.lwjgl.glfw.GLFW;
import com.utilitymod.echo.utils.EncryptedString;
public class AegisSplit extends Feature {

    private static final CharSequence CLICK_MODE = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2271,0xd139,0x37d5,0xa8a7,0xbc0a}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be});
    private static final CharSequence NORMAL_MODE = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa8a4,0xb163,0xd134,0x226a,0x37df,0xbc04}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f});
    private static final CharSequence DRAIN_MODE = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa8b9,0x37d0,0xd13e,0x2279,0xbc00}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643});

	public AegisSplit() {
		super(new FeatureInfo(
			new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa8b9, 0x9637, 0x82e6, 0x37dd, 0x0b7c, 0x1c9b, 0x227d, 0xb167, 0xd138, 0xbc08}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643}),
			new com.utilitymod.echo.utils.EncryptedString(new char[]{0xbc08,0x0b7c,0xd92a,0x227d,0x4a0f,0x1c9c,0xfcf4,0x5f6c,0x11fd,0xd138,0xb17c,0x37d5,0x82c8,0x6516,0xc5d7,0x76bc,0xa8b9,0xf04c,0x9626,0xeb98}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87,0x76d4,0xf025,0x4a6a,0xc5bb,0x5f08,0xd959}),
			Category.COMBAT
		));

        inputSimulation.onChanged(this::syncSwapBackBounds);
        syncSwapBackBounds();        reactionDelay.setDependency(p -> isAutoMode());
        delayAttack.setDependency(p -> isAutoMode());
        autoSwapToAxe.setDependency(p -> isAutoMode());
        initialDelay.setDependency(s -> isAutoMode() && autoSwapToAxe.getValue());
        unblockShield.setDependency(p -> isAutoMode());

        returnBack.setDependency(p -> modeSetting.is(CLICK_MODE) || (isAutoMode() && autoSwapToAxe.getValue()));
        swapBackDelay.setDependency(g -> returnBack.getValue()
            && (modeSetting.is(CLICK_MODE) || (isAutoMode() && autoSwapToAxe.getValue())));
        slamOnBreak.setDependency(p -> isAutoMode());
    }

    /** Normal + Drain share the auto attack pipeline. */
    private boolean isAutoMode() {
        return modeSetting.is(NORMAL_MODE) || modeSetting.is(DRAIN_MODE);
    }

    private final ModeSetting modeSetting = new ModeSetting(
        new com.utilitymod.echo.utils.EncryptedString(new char[]{0x227c,0xa8a4,0xd137,0xbc0c}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69}),
        CLICK_MODE,
        CLICK_MODE,
        NORMAL_MODE,
        DRAIN_MODE
    );

    private final IntSetting reactionDelay = new IntSetting(
        new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd128,0x0b33,0xb166,0x37ca,0x9607,0x2279,0xeb8d,0xfcfe,0x82c3,0xbc0a,0xa8ae,0x1cd2,0x11f5,0x6557}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87}), 0, 0, 500);
    private final IntSetting delayAttack = new IntSetting(
        dev.potato.spud.api.ProtectedText.fromInts(new int[]{0x5F,0x5D,0x04,0xDA,0xA1,0x66,0x74,0xDB,0xC3,0x9D,0x59,0x3A}), 0, 0, 500);

    private final BoolSetting autoSwapToAxe = new BoolSetting(
        new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0b2b,0x1c82,0x82cc,0x379e,0xbc06,0xa8be,0xb15c,0x226c,0xd13b}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2}), true);
    private final IntSetting initialDelay = new IntSetting(
        new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2274,0xd13e,0x37c7,0xa8ae,0xbc08}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be}), 1, 0, 20);

    private final BoolSetting returnBack = new BoolSetting(
        new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0b3d,0x1c99,0x82ce,0x379e,0xbc19,0xa8bc,0xb14d,0x2279,0xd129}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2}), false);
    private final IntSetting swapBackDelay = new IntSetting(
        new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2274,0xd13e,0x37c7,0xa8ae,0xbc08}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be}), 1, 0, 20);

    private final TimerUtils reactionTimer = new TimerUtils();
    private final TimerUtils firstSwapTimer = new TimerUtils();
    private final TimerUtils attackTimer = new TimerUtils();

    private final BoolSetting unblockShield = new BoolSetting(new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6565,0xa8a5,0xfcef,0x76bd,0xf040,0xebc1,0x9634,0x37d1,0x828d,0xb16c,0xbc05,0x4a06,0x1cbd,0xc5df,0x227a,0xd12f,0x11fe,0x0b37}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87,0x76d4,0xf025,0x4a6a,0xc5bb}), true);
    private final com.utilitymod.echo.features.settings.impl.InputSimulationSetting inputSimulation = new com.utilitymod.echo.features.settings.impl.InputSimulationSetting(new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd129,0x0b28,0xb16e,0x37d2,0x960a,0x2275,0xeb91,0xfcf3,0x82c8,0xbc1c,0xa8a2,0x1cd2,0x11fe,0x6543}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87}), false);
    private final BoolSetting stun = new BoolSetting(new com.utilitymod.echo.utils.EncryptedString(new char[]{0x226d,0xa8bf,0xd129,0xbc07}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69}), false);

    /**
     * Drain: instead of stopping after the axe disables the shield for 5s, the module keeps
     * swinging through the whole disable window (and every re-block), so every absorbed hit
     * burns shield durability until the shield is gone for good. The moment the shield is
     * spent, the kill is handed to MaceSwap for the one-tap slam.
     */
    public final BoolSetting slamOnBreak = new BoolSetting(
        new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa8e6,0x224c,0x1cd2,0x2279,0xbc02,0xd108,0x82c3,0xbc08,0xb12f,0xa8ae,0x0b13,0x37ce,0x9601,0xd14b}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643}), true);

    private boolean hasPlayerUnblocked = false;
    private boolean awaitingSwapBack = false;

    private PlayerEntity pendingTarget = null;

    // Drain state: tracks the shield of the target currently being worn down.
    private PlayerEntity drainTarget = null;
    private int shieldDamageSeen = -1;
    private long drainShieldSeenAt = 0L;
    private long drainLastHitAt = 0L;

    private void resetDrainState() {
        drainTarget = null;
        shieldDamageSeen = -1;
        drainShieldSeenAt = 0L;
        drainLastHitAt = 0L;
    }

    private void noteDrainHit(PlayerEntity target) {
        drainTarget = target;
        drainLastHitAt = System.currentTimeMillis();
        ItemStack shield = shieldStack(target);
        if (shield != null && shield.getDamage() > shieldDamageSeen) {
            shieldDamageSeen = shield.getDamage();
        }
    }

    /** The target's shield stack (offhand), or null when they are not carrying one. */
    private static ItemStack shieldStack(PlayerEntity target) {
        ItemStack off = target.getOffHandStack();
        return off.getItem() instanceof ShieldItem ? off : null;
    }

    @Override
    public void onDisable() {
        SwapStateManager.cancel(this);
        super.onDisable();
        hasPlayerUnblocked = false;
        awaitingSwapBack = false;
        pendingTarget = null;
        resetDrainState();
        TargetControlModule.pendingShieldBreakTarget = null;
        attackTimer.reset();
        reactionTimer.reset();
        firstSwapTimer.reset();
    }

    @SuppressWarnings("unused")
    @EventSubscribe
    private void onMousePress(EventStartAttack.Pre event) {
        if (!modeSetting.is(CLICK_MODE)) return;
        if (mc.player == null || mc.world == null || com.utilitymod.echo.api.MinecraftCompat.getScreen() != null) return;
        if (InventoryUtils.isHoldingAxeItem()) return;
        if (SwapStateManager.hasActiveSwaps() && !SwapStateManager.isOwnerActive(this)) return;
        syncSwapBackBounds();

        if (mc.crosshairTarget instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof PlayerEntity target) {
            if (!TargetUtils.isTargetAllowed(target)) return;
            boolean canBreakShield = !WorldUtils.isShieldFacingAway(target);
            boolean isBlocking = isShieldBlocking(target);
            ItemStack mainHand = InventoryUtils.getMainHandItem();

            MaceSwap maceLink = Echo.featureManager.getFeatureByClass(MaceSwap.class);
            boolean maceLinkAllowsAxes = maceLink != null && maceLink.isEnabled() && MaceSwap.allowsAxes();

            if (isBlocking && canBreakShield) {
                if (mainHand.getItem() instanceof AxeItem) {
                    // The click event will perform the attack with the axe already equipped.
                    pendingTarget = target;
                    if (stun.getValue()) {
                        InputHandler.simulateClick(mc.options.attackKey, inputSimulation);
                    }
                    return;
                }

                if (maceLinkAllowsAxes) return;
                int itemSlot = InventoryUtils.findItemWithPredicateInHotbar(
                    ItemStack -> ItemStack.getItem() instanceof AxeItem
                );
                if (itemSlot != -1 && !SwapStateManager.isOwnerActive(this)) {
                    if (!SwapStateManager.swapTo(this, itemSlot, inputSimulation.getValue(), swapBackDelay.getValue(), returnBack.getValue())) {
                        return;
                    }
                    
                    /*
                     Schedule shield break prediction for MaceSwap.
                     Changing a field causes a natural tick delay.
                     At least for the next event. I could like maybe explain it more but basically it takes
                     until the next tick for the events to recognize a field has changed values.
                     However if you want something to happen on the same event. Just make a fake event.
                    */
                    pendingTarget = target;
                    if (stun.getValue()) {
                        InputHandler.simulateClick(mc.options.attackKey, inputSimulation);
                    }
                }
            }
        }
    }

    @SuppressWarnings("unused")
    @EventSubscribe(priority = EventSubscribe.Priority.HIGHEST)
    private void onTick(EventHandleInput.Early event) {
        if (mc.player == null || mc.world == null || com.utilitymod.echo.api.MinecraftCompat.getScreen() != null) return;

        if (awaitingSwapBack && !SwapStateManager.hasActiveSwaps() && !(mc.player.getMainHandStack().getItem() instanceof AxeItem)) {
            clearSwapBackState();
        }

        if (isAutoMode()) {
            handleNormalMode();
        }

        if (pendingTarget != null) {
            TargetControlModule.pendingShieldBreakTarget = pendingTarget;
            pendingTarget = null;
        }
    }

    private void handleNormalMode() {
        if (SwapStateManager.hasActiveSwaps() && !SwapStateManager.isOwnerActive(this)) return;
        syncSwapBackBounds();
        boolean isPlayerOffhandShielding = PlayerUtils.isPlayerShieldingInOffHand();
        boolean holdingAxe = mc.player.getMainHandStack().getItem() instanceof AxeItem;

        if (awaitingSwapBack) {
            if (!SwapStateManager.hasActiveSwaps() && !holdingAxe) {
                clearSwapBackState();
                return;
            }

            if (!holdingAxe) {
                return;
            }
        }

        if (mc.crosshairTarget instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof PlayerEntity target) {
            boolean canBreakShield = !WorldUtils.isShieldFacingAway(target);
            boolean isBlocking = isShieldBlocking(target);
            ItemStack mainHand = InventoryUtils.getMainHandItem();

            if (isBlocking && canBreakShield){
                if (isPlayerOffhandShielding){
                    if (unblockShield.getValue()){
                        mc.options.useKey.setPressed(false);
                        hasPlayerUnblocked = true;
                    }
                }
                if (mainHand.getItem() instanceof AxeItem){
                    if (attackTimer.hasReached(delayAttack.getValue())){
                        clickAttack();
                        
                        pendingTarget = target;

                        // Causing duplicate inputs with the other attack
                        attackTimer.reset();
                        noteDrainHit(target);
                    }
                } else {
                    if (!autoSwapToAxe.getValue()) return;
                    if (reactionTimer.hasReached(reactionDelay.getValue())){
                        if (firstSwapTimer.hasReachedTicks(initialDelay.getValue())){
                            int itemSlot = InventoryUtils.findItemWithPredicateInHotbar
                                    (ItemStack -> ItemStack.getItem() instanceof AxeItem);
                            if (itemSlot != -1 && !SwapStateManager.isOwnerActive(this)){
                                clickAttack();
                                firstSwapTimer.reset();
                                attackTimer.reset();
                                if (!SwapStateManager.swapTo(this, itemSlot, inputSimulation.getValue(), swapBackDelay.getValue(), returnBack.getValue(), this::onSwapEnded)) {
                                    return;
                                }

                                awaitingSwapBack = returnBack.getValue();
                            }
                        }
                    }
                }
            } else if (modeSetting.is(DRAIN_MODE)) {
                handleDrainContinue(target);
            } else {
                reactionTimer.reset();
        
            }
        } else {
            firstSwapTimer.reset();
            reactionTimer.reset();
            attackTimer.reset();
            if (drainTarget != null) resetDrainState();
    
        }
    }

    /**
     * Drain phase: the axe hit landed but the target is not actively blocking right now
     * (shield disabled by our SSD hits, mid re-block gap, or already broken). Keep swinging
     * so the next absorbed hit takes the durability it needs, and the moment the shield item
     * is spent hand the kill to MaceSwap for the one-tap slam.
     */
    private void handleDrainContinue(PlayerEntity target) {
        long now = System.currentTimeMillis();
        ItemStack shield = shieldStack(target);
        if (shield != null) {
            drainTarget = target;
            drainShieldSeenAt = now;
            if (shield.getDamage() > shieldDamageSeen) shieldDamageSeen = shield.getDamage();
        }

        boolean facingAway = WorldUtils.isShieldFacingAway(target);
        boolean recentlySeen = drainTarget == target && now - drainShieldSeenAt < 2000L;

        if (!recentlySeen || facingAway) {
            reactionTimer.reset();
            if (drainTarget == target && now - drainShieldSeenAt >= 2000L) resetDrainState();
            return;
        }

        boolean shieldGone = shield == null;

        if (!shieldGone) {
            // Shield still held (disabled or re-block window): keep swinging through it.
            if (attackTimer.hasReached(delayAttack.getValue())) {
                clickAttack();
                pendingTarget = target;
                attackTimer.reset();
                noteDrainHit(target);
            }
            reactionTimer.reset();
            return;
        }

        // Shield item is gone: drain complete - feed the mace one-tap.
        if (slamOnBreak.getValue() && now - drainLastHitAt < 1500L) {
            pendingTarget = target;
        }
        resetDrainState();
    }

    public void reShield(){
        if (mc.player == null || mc.world == null) return;
        if (GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS){
            if (mc.player.getOffHandStack().getItem() instanceof ShieldItem){
                mc.options.useKey.setPressed(true);
                hasPlayerUnblocked = false;
            }
        } else {
            hasPlayerUnblocked = false;
        }
    }

    private void onSwapEnded() {
        if (!returnBack.getValue()) {
            hasPlayerUnblocked = false;
            awaitingSwapBack = false;
            return;
        }

        if (modeSetting.is(NORMAL_MODE) && hasPlayerUnblocked) {
            reShield();
        }

        awaitingSwapBack = false;
        clearSwapBackState();
    }

    private void syncSwapBackBounds() {
        int minBound = inputSimulation.getValue() ? 1 : 0;

        if (swapBackDelay.getMinValue() < minBound) {
            swapBackDelay.setMinValue(minBound);
        }

        if (swapBackDelay.getMaxValue() < minBound) {
            swapBackDelay.setMaxValue(minBound);
        }

        if (swapBackDelay.getValue() < minBound) {
            swapBackDelay.setValue(minBound);
        }
    }

    private void clearSwapBackState() {
        awaitingSwapBack = false;
        reactionTimer.reset();
        firstSwapTimer.reset();
        attackTimer.reset();
    }

    private void clickAttack() {
        InputHandler.simulateClick(mc.options.attackKey, inputSimulation);
        if (stun.getValue()) {
            InputHandler.simulateClick(mc.options.attackKey, inputSimulation);
        }
    }

    private static boolean isShieldBlocking(PlayerEntity target) {
        return target.isBlocking()
            && target.isUsingItem()
            && target.getActiveItem().getItem() instanceof ShieldItem;
    }
}
