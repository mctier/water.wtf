package com.utilitymod.echo.features.impl.combat;

import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventStartAttack;
import com.utilitymod.echo.event.impl.EventStartUseItem;
import com.utilitymod.echo.event.impl.EventTick;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.settings.impl.BoolSetting;
import com.utilitymod.echo.features.settings.impl.FloatSetting;
import com.utilitymod.echo.features.settings.impl.IntSetting;
import com.utilitymod.echo.features.settings.impl.ItemPickerSetting;
import com.utilitymod.echo.features.settings.impl.KeybindSetting;
import com.utilitymod.echo.handlers.InputHandler;
import com.utilitymod.echo.handlers.impl.SwapStateManager;
import com.utilitymod.echo.utils.blocks.BlockUtils;
import com.utilitymod.echo.utils.combat.TargetUtils;
import com.utilitymod.echo.utils.inventory.InventoryUtils;
import com.utilitymod.echo.utils.math.TimerUtils;
import com.utilitymod.echo.utils.strings.Concat;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.Hand;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import com.utilitymod.echo.utils.EncryptedString;
import dev.potato.spud.api.ProtectedText;

public class Crystalline extends Feature {

	public Crystalline() {
		super(new FeatureInfo(
			ProtectedText.fromInts(new int[]{0x5F,0x5C,0x04,0xD4,0xE2,0x4E,0x26,0xE6,0xD5,0x85,0x59,0x2F}),
			new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd2b3,0xb16e,0x11fc,0x82c4,0x3357,0xb9c2,0x654f,0xc5d8,0x2603,0x9e49,0xfca7,0xf8d8,0x9622,0xcdec,0x722a,0xd13b,0x4a0b,0xeb8d,0x3ff9,0xa8be,0x226c,0x04a5,0x76a4,0x1c91,0xe73f,0xf049,0x7ed8,0x508e,0x4796,0xd92a,0xa422,0x6d61,0x5f6d,0x93a5,0x8ae5,0xbc06,0x58f9,0x2a9e,0xc14f,0x0b28,0x37d3,0x1819,0xac4f}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87,0x76d4,0xf025,0x4a6a,0xc5bb,0x5f08,0xd959,0x50ae,0x2aff,0xa44c,0x3f9d,0xb9e2,0x3333,0x8a80,0x04d1,0x9e26,0x1877,0x93c4,0x6d15,0xe75a,0x7eab,0xf8f8,0x7249,0xcd9e,0x47ef,0xc13c,0x588d,0xd2d2,0xac23,0x2670}),
			Category.COMBAT
		));
		itemWhitelist.setSelected(Items.END_CRYSTAL, true);
	}


    // clone: Bypassed sheet pacing in ms (Place 50 / Break 100) — carriers harness rt-verified
    private static final EncryptedString K_PLACE = new EncryptedString(new char[]{0x11e9, 0xa8a7, 0xbc0a, 0x2279, 0x1c9e, 0x82c8, 0x0b18, 0xd12a, 0xb12f, 0x9622, 0x37db}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643, 0x1190});
    private static final EncryptedString K_MS = new EncryptedString(new char[]{0xa8b8, 0xd117}, new char[]{0xd17a, 0xa8cb});
    private static final EncryptedString K_ACC = new EncryptedString(new char[]{0x1c9e, 0x9626, 0xbc49, 0x11e2, 0x76bb, 0x0b3f, 0xeb80, 0x37ff, 0xf04b, 0xd139, 0x82c8, 0x224b, 0xfcee, 0xa89b, 0xb16c, 0x6542}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643, 0x1190, 0xebe1, 0x6536, 0xfc87, 0x76d4, 0xf025});
    private static final EncryptedString K_SOLO = new EncryptedString(new char[]{0x2274, 0xa8a4, 0xd129, 0xbc06}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69});
    private static final EncryptedString K_PING = new EncryptedString(new char[]{0x82c0, 0x76b1, 0x0b33, 0xeb92, 0x9626, 0xa8a2, 0x379e, 0x1c82, 0xfcf3, 0xbc0e, 0xb14c, 0xd12a, 0x6557, 0x2276, 0x11fe}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643, 0x1190, 0xebe1, 0x6536, 0xfc87, 0x76d4});
    private static final EncryptedString K_MINY = new EncryptedString(new char[]{0x2276, 0xd137, 0x37e7, 0xa8a2, 0xbc49}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be});

    private final IntSetting delayPlace = new IntSetting(K_PLACE, 50, 0, 500, K_MS);
    private final IntSetting crackCooldown = new IntSetting(
        new com.utilitymod.echo.utils.EncryptedString(new char[]{0x11e9,0xa8b9,0xbc08,0x227d,0x1c9e,0x82c8,0x0b18,0xd138,0xb12f,0x9622,0x37d5}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190}),
        100, 0, 500, K_MS
    );
    private final IntSetting failChance = new IntSetting(new com.utilitymod.echo.utils.EncryptedString(new char[]{0x11f5,0xa8aa,0xbc05,0x2271,0x1c9c,0x82cc,0x0b34,0xd13c,0xb14c,0x9620,0x379e}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190}), 0, 0, 100);
    private final com.utilitymod.echo.features.settings.impl.InputSimulationSetting inputSimulation = new com.utilitymod.echo.features.settings.impl.InputSimulationSetting(new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1c9f,0x9636,0xbc1c,0x11fc,0x76bb,0x0b0f,0xeb80,0x37ca,0xf04b,0xd133,0x82c4,0x2268,0xfcee,0xa8a5,0xb12f,0x6542}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87,0x76d4,0xf025}), false);
    /** Input simulation pacing per button: right click (place) and left click (break) each get their own CPS. */
    private final IntSetting rightCps = new IntSetting("Right CPS", 20, 1, 30, "cps", p -> inputSimulation.getValue());
    private final IntSetting leftCps = new IntSetting("Left CPS", 10, 1, 30, "cps", p -> inputSimulation.getValue());
    private long lastRightMs, lastLeftMs;
    private final BoolSetting stopOnKill = new BoolSetting(new com.utilitymod.echo.utils.EncryptedString(new char[]{0xeb8d,0x0b32,0x1c99,0x962a,0x2277,0xd129,0x11fc,0x828d,0xa8bf,0x379e,0xb160,0xbc19}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1}), false);

    // Hit Crystal settings
    private final BoolSetting hitCrystal = new BoolSetting(new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd12a,0x0b13,0xb12f,0x37db,0x962a,0x2279,0xeb88,0xfce9,0x82cf,0xbc0a,0xa8a7,0x1c81,0x11f4,0x6557}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87}), true);
    private final IntSetting switchTickDelay = new IntSetting(
        ProtectedText.fromInts(new int[]{0x4D,0x5E,0x19,0xCF,0xA1,0x65,0x74,0xDB,0xC3,0x9D,0x59,0x3A}),
        1, 0, 10,
        new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa8bf,0xb17c,0xd15a,0x2271,0x37d5,0xbc0a}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f}),
        p -> hitCrystal.getValue()
    );
    private final IntSetting placeObsidianTickDelay = new IntSetting(
        new com.utilitymod.echo.utils.EncryptedString(new char[]{0xbc0a,0x0b13,0xd920,0x2279,0x4a0f,0x1c81,0xfce9,0x5f69,0x11f4,0xd12a,0xb12f,0x37db,0x82cf,0x6557,0xc5d7,0x76f4,0xa8a7,0xf061,0x962a,0xeb88}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87,0x76d4,0xf025,0x4a6a,0xc5bb,0x5f08,0xd959}),
        1, 0, 10,
        new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa8bf,0xb17c,0xd15a,0x2271,0x37d5,0xbc0a}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f}),
        p -> hitCrystal.getValue()
    );
    private final BoolSetting backSwitch = new BoolSetting(ProtectedText.fromInts(new int[]{0x4D,0x5E,0x19,0xCF,0xA1,0x65,0x74,0xDD,0xC7,0x92,0x53}), true, p -> hitCrystal.getValue());
    private final ItemPickerSetting itemWhitelist = new ItemPickerSetting(new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd133,0x0b34,0xb158,0x379e,0x9626,0x227d,0xeb88,0xfcf3,0x82c4,0xbc04,0xa8bf,0x1c86,0x11fc,0x6545}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87}));
    private final IntSetting clickCap = new IntSetting(new com.utilitymod.echo.utils.EncryptedString(new char[]{0x224b,0xa89b,0xd139}, new char[]{0xd17a,0xa8cb,0x2218}), 18, 0, 100);
    private final BoolSetting antiSuicide = new BoolSetting(new com.utilitymod.echo.utils.EncryptedString(new char[]{0xeb84,0x0b29,0x1c91,0x962a,0x226c,0xd13b,0x11f4,0x82c4,0xa8a5,0x379e,0xb15c,0xbc00}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1}), true);
    private final BoolSetting cpsAcceleration = new BoolSetting(K_ACC, true);
    private final BoolSetting doubleTap = new BoolSetting(new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa8a4, 0x9633, 0x82f9, 0x37d2, 0x0b7c, 0x1c93, 0x226d, 0xb16a, 0xd13e, 0xbc0b}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643}), true);
    private final BoolSetting solo = new BoolSetting(K_SOLO, true);
    private final BoolSetting pingCompensate = new BoolSetting(K_PING, true);
    private final FloatSetting minY = new FloatSetting(K_MINY, 0.5f, 0.0f, 3.0f, 0.5f);
//    private final ModeSetting amountMode = new ModeSetting
//            (new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa8a6,0xb17b,0xd13b,0x2277,0x37d0,0xbc1c}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f}), new com.utilitymod.echo.utils.EncryptedString(new char[]{0x227e,0xd133,0xb166,0x0b28,0xbc00,0x37d0,0xa8a5,0x82c8}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad}), p -> hitCrystal.getValue(), new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa8a2,0xb16a,0xd129,0x2276,0x37d2,0xbc0e}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f}), new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa8a4,0xb16a,0xd13e,0x226d,0x37d2,0xbc0b}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f}), new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa8b9,0xb16a,0xd12e,0x2271,0x37d2,0xbc19}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f}), new com.utilitymod.echo.utils.EncryptedString(new char[]{0x227e,0xd133,0xb166,0x0b28,0xbc00,0x37d0,0xa8a5,0x82c8}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad}));
    private final KeybindSetting activateKey = new KeybindSetting(new com.utilitymod.echo.utils.EncryptedString(new char[]{0xeb98,0x0b28,0x1cd2,0x9608,0x226c,0xd13b,0x11f5,0x82c8,0xa8a8,0x37c8,0xb16e,0xbc00}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1}), -1);


    private final TimerUtils placeTimer = new TimerUtils();
    private final TimerUtils breakTimer = new TimerUtils();

    private enum HitCrystalStage {None, SwitchObsidian, PlaceObsidian, SwitchCrystal, Done}
    private HitCrystalStage currentStage = HitCrystalStage.None;
    private int switchCooldown, placeCooldown;
    private boolean placedObsidian, lastRmbPressed;
    private boolean simulateAttackNext = true;
    private long lastSimClickMs;
    private long accelStampMs = System.currentTimeMillis();
    private int accelBonusCps;
    @Nullable
    private BlockPos expectedPlacementPos;

    @Override
    public void onEnable() {
        super.onEnable();
        placeTimer.reset();
        breakTimer.reset();
        accelStampMs = System.currentTimeMillis();
        accelBonusCps = 0;
        resetHitCrystal();
    }

    @Override
    public void onDisable() {
        resetHitCrystal();
        super.onDisable();
    }

    private void resetHitCrystal() {
        currentStage = HitCrystalStage.None;
        switchCooldown = 0;
        placeCooldown = 0;
        SwapStateManager.cancel(this, false);
        placedObsidian = false;
        lastRmbPressed = false;
        simulateAttackNext = true;
        expectedPlacementPos = null;
    }

    @EventSubscribe
    private void onTick(EventTick event) {
        if (mc.crosshairTarget != null && com.utilitymod.echo.utils.combat.TargetUtils.friendNear(mc.crosshairTarget.getPos(), 6.5)) return;
        if(isNull()) return;
        if (com.utilitymod.echo.api.MinecraftCompat.getScreen() != null) return;
        handleCrystalLogic();
    }

    @EventSubscribe
    private void onDoAttack(EventStartAttack event) {
        if (mc.crosshairTarget != null && com.utilitymod.echo.utils.combat.TargetUtils.friendNear(mc.crosshairTarget.getPos(), 6.5)) return;
        if (inputSimulation.getValue()) return;

        if (isEnabled() && mc.player != null && mc.world != null && mc.crosshairTarget instanceof BlockHitResult blockHit) {
            BlockPos pos = blockHit.getBlockPos();
            Block block = mc.world.getBlockState(pos).getBlock();
            
            if ((block == Blocks.OBSIDIAN || block == Blocks.BEDROCK) && activationDown()) {
                event.cancel();
            }
        }
    }

    @EventSubscribe
    private void onItemUse(EventStartUseItem.Pre event) {
        if (mc.crosshairTarget != null && com.utilitymod.echo.utils.combat.TargetUtils.friendNear(mc.crosshairTarget.getPos(), 6.5)) return;
        if (isNull()) return;
        
        int key = activateKey.getKey();
        boolean isRMB = key == -1
                || ((key & 0x80000000) != 0 && (key & 0xFF) == GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        if (!isRMB) return;
        
        if (event.isTargetingBlock()) {
            BlockHitResult blockHit = event.getBlockHitResult();
            if (blockHit != null) {
                BlockPos pos = blockHit.getBlockPos();
                Block block = mc.world.getBlockState(pos).getBlock();
                Item heldItem = event.getStack().getItem();
                if ((block == Blocks.OBSIDIAN || block == Blocks.BEDROCK) &&
                    (heldItem == Items.OBSIDIAN || heldItem == Items.BEDROCK) &&
                    currentStage != HitCrystalStage.PlaceObsidian &&
                    !canPlaceHeldBlockAt(blockHit) &&
                    activationDown()) {
                    if (!InventoryUtils.hasItemInHotbar(item -> item == Items.END_CRYSTAL)) return;
                    if (InventoryUtils.isFood(event.getStack())) return;
                    event.cancel();
                }
            }
        }
    }

    private void handleCrystalLogic() {
        if (isNull()) return;
        if (mc.player.isUsingItem()) return;
        if (corpseNearby() && stopOnKill.getValue()) return;

        boolean rmbPressed = activationDown();

        Item heldItem = mc.player.getMainHandStack().getItem();
        boolean validActivationItem = isValidActivationItem(heldItem);

        if (hitCrystal.getValue() && rmbPressed && validActivationItem) {
            if (currentStage == HitCrystalStage.None && canActivateHitCrystal()) {
                activateHitCrystal();
            }
        }
        lastRmbPressed = rmbPressed;

        if (!rmbPressed && currentStage != HitCrystalStage.None) {
            SwapStateManager.cancel(this, backSwitch.getValue());
            currentStage = HitCrystalStage.None;
        }


        if (currentStage != HitCrystalStage.None) {
            handleHitCrystalLogic();
            return;
        }

        if (!rmbPressed || !validActivationItem) return;
        handleNormalCrystalLogic();
    }

    private boolean isValidActivationItem(Item item) {
        return itemWhitelist.getSelectedCount() > 0 && itemWhitelist.isSelected(item);
    }

    /** Bind owns activation; unset bind (-1) falls back to plain right-click. */
    private boolean activationDown() {
        int key = activateKey.getKey();
        if (key == -1) return InputHandler.isBindDown(mc.options.useKey);
        return InputHandler.isBindDown(key);
    }

    private boolean canActivateHitCrystal() {
        if (mc.crosshairTarget instanceof BlockHitResult blockHit) {
            BlockPos pos = blockHit.getBlockPos();
            BlockState state = mc.world.getBlockState(pos);
            Block block = state.getBlock();
            // If we're looking at obsidian/bedrock already, only need crystals
            if (block == Blocks.OBSIDIAN || block == Blocks.BEDROCK) {
                return InventoryUtils.hasItemInHotbar(item -> item == Items.END_CRYSTAL);
            }
            // Otherwise we need obsidian (or bedrock) to place and crystals
            return InventoryUtils.hasItemInHotbar(item -> item == Items.OBSIDIAN || item == Items.BEDROCK) && InventoryUtils.hasItemInHotbar(item -> item == Items.END_CRYSTAL);
        }
        return false;
    }

    private void activateHitCrystal() {
        // If we're already looking at obsidian/bedrock, go straight to switching to crystal
        if (mc.crosshairTarget instanceof BlockHitResult blockHit) {
            BlockPos pos = blockHit.getBlockPos();
            BlockState hitState = mc.world.getBlockState(pos);
            Block block = hitState.getBlock();
            if (block == Blocks.OBSIDIAN || block == Blocks.BEDROCK) {
                currentStage = HitCrystalStage.SwitchCrystal;
                switchCooldown = switchTickDelay.getValue();
                placedObsidian = false;
                return;
            }
            
            if (hitState.isAir()) {
                return;
            }
            
            Direction hitFace = blockHit.getSide();
            BlockPos placementPos = hitState.isReplaceable() ? pos : pos.offset(hitFace);
            BlockState placementState = mc.world.getBlockState(placementPos);
            
            if (!placementState.isAir() && !placementState.isReplaceable()) {
                return;
            }
        } else {
            return;
        }
        
        currentStage = HitCrystalStage.SwitchObsidian;
        switchCooldown = switchTickDelay.getValue();
        placeCooldown = placeObsidianTickDelay.getValue();
        placedObsidian = false;
    }

    private void handleHitCrystalLogic() {
        HitCrystalStage prevStage = null;
        while (currentStage != HitCrystalStage.None && currentStage != prevStage) {
            prevStage = currentStage;

            if (currentStage == HitCrystalStage.PlaceObsidian) {
                if (placeCooldown > 0) {
                    placeCooldown--;
                    return;
                }
            } else if (currentStage == HitCrystalStage.SwitchObsidian || currentStage == HitCrystalStage.SwitchCrystal) {
                if (switchCooldown > 0) {
                    switchCooldown--;
                    return;
                }
            }

            switch (currentStage) {
                case SwitchObsidian -> {
                    int obsidianSlot = InventoryUtils.findItemWithPredicateInHotbar(itemStack -> itemStack.getItem() == Items.OBSIDIAN);
                    if (obsidianSlot == -1) obsidianSlot = InventoryUtils.findItemWithPredicateInHotbar(itemStack -> itemStack.getItem() == Items.BEDROCK);
                    if (obsidianSlot != -1) {
                        if (!SwapStateManager.swapToIfNeeded(this, obsidianSlot, inputSimulation.getValue(), -1, false)) {
                            currentStage = HitCrystalStage.None;
                            break;
                        }
                        currentStage = HitCrystalStage.PlaceObsidian;
                    } else {
                        currentStage = HitCrystalStage.None;
                    }
                }
                case PlaceObsidian -> {
                    // If we previously attempted to place an obsidian, check if it actually placed
                    if (placedObsidian && expectedPlacementPos != null) {
                        BlockState placedState = mc.world.getBlockState(expectedPlacementPos);
                        Block placedBlock = placedState.getBlock();
                        if (placedBlock == Blocks.OBSIDIAN || placedBlock == Blocks.BEDROCK) {
                            currentStage = HitCrystalStage.SwitchCrystal;
                            switchCooldown = switchTickDelay.getValue();
                            placedObsidian = false;
                            expectedPlacementPos = null;
                            continue;
                        }
                        // still not placed; wait until cooldown expiry then try again
                        placeCooldown = placeObsidianTickDelay.getValue();
                        return;
                    }

                    // Try to place obsidian
                    if (mc.crosshairTarget instanceof BlockHitResult blockHit) {
                        BlockPos placementPos = resolvePlacementPos(blockHit);
                        BlockState placementState = mc.world.getBlockState(placementPos);
                        Block placementBlock = placementState.getBlock();

                        // Only place if the target position doesn't already have obsidian/bedrock
                        if (placementBlock != Blocks.OBSIDIAN && placementBlock != Blocks.BEDROCK) {
                            InputHandler.simulateClick(mc.options.useKey, inputSimulation);
                            placedObsidian = true;
                            expectedPlacementPos = placementPos;
                        } else {
                            // Already has obsidian/bedrock, switch to crystal
                            currentStage = HitCrystalStage.SwitchCrystal;
                            switchCooldown = switchTickDelay.getValue();
                            placedObsidian = false;
                            expectedPlacementPos = null;
                        }
                    }
                }
                case SwitchCrystal -> {
                    int crystalSlot = InventoryUtils.findItemWithPredicateInHotbar(itemStack -> itemStack.getItem() == Items.END_CRYSTAL);
                    if (crystalSlot != -1) {
                        if (!SwapStateManager.swapToIfNeeded(this, crystalSlot, inputSimulation.getValue(), -1, false)) {
                            currentStage = HitCrystalStage.None;
                            break;
                        }
                        currentStage = HitCrystalStage.Done;
                        placeTimer.reset();
                    } else {
                        currentStage = HitCrystalStage.None;
                    }
                }
                case Done -> {
                    handleNormalCrystalLogic();
                }
                case None -> {}
            }
        }
    }

    private BlockPos resolvePlacementPos(BlockHitResult blockHit) {
        ItemPlacementContext placeContext = new ItemPlacementContext(
            mc.player,
            Hand.MAIN_HAND,
            mc.player.getMainHandStack(),
            blockHit
        );
        return placeContext.getBlockPos();
    }

    private boolean canPlaceHeldBlockAt(BlockHitResult blockHit) {
        if (mc.player == null || mc.world == null) return false;

        BlockPos placementPos = resolvePlacementPos(blockHit);
        BlockState placementState = mc.world.getBlockState(placementPos);
        return placementState.isAir() || placementState.isReplaceable();
    }

    private void handleClickSimulation(boolean rmbPressed) {
        if (!rmbPressed) return;
        if (!isLookingAtValidTarget()) return;

        // Attack clicks may only land on a crystal — the raw click path digs whatever
        // block is under the crosshair, which is how the sim lane ended up mining.
        boolean crystalVisit = mc.crosshairTarget instanceof EntityHitResult eHit
                && eHit.getEntity() instanceof EndCrystalEntity c && c.isAlive() && !c.isRemoved()
                && c.getEntityPos().y - mc.player.getEntityPos().y >= minY.getValue() - 1.0f;
        boolean blockVisit = mc.crosshairTarget instanceof BlockHitResult bHit
                && mc.world.getBlockState(bHit.getBlockPos()).isOf(Blocks.OBSIDIAN)
                        || mc.crosshairTarget instanceof BlockHitResult bHit2
                && mc.world.getBlockState(bHit2.getBlockPos()).isOf(Blocks.BEDROCK);
        boolean breakReady = breakTimer.hasReached(crackCooldown.getValue() + pingBonusMs());

        boolean placeReady = placeTimer.hasReached(delayPlace.getValue() + pingBonusMs());
        if (!breakReady && !placeReady) return;
        if (crystalVisit && suicideRisk(((EntityHitResult) mc.crosshairTarget).getEntity().getEntityPos())) return;
        if (blockVisit && suicideRisk(Vec3d.ofCenter(((BlockHitResult) mc.crosshairTarget).getBlockPos()))) return;
        if (!inputSimulation.getValue() && !cpsGate()) return;

        if (simulateAttackNext) {
            if (breakReady) {
                if (inputSimulation.getValue() && !cpsGate(false)) return;
                if (crystalVisit && Math.random() * 100 >= failChance.getValue()) {
                    InputHandler.simulateClick(mc.options.attackKey, inputSimulation);
                }
                breakTimer.reset();
                simulateAttackNext = false;
                return;
            }

            if (placeReady && blockVisit) {
                if (inputSimulation.getValue() && !cpsGate(true)) return;
                if (Math.random() * 100 >= failChance.getValue()) {
                    InputHandler.simulateClick(mc.options.useKey, inputSimulation);
                }
                if (doubleTap.getValue()) breakTimer.reset();
                placeTimer.reset();
                simulateAttackNext = true;
            }
            return;
        }

        if (placeReady && blockVisit) {
            if (inputSimulation.getValue() && !cpsGate(true)) return;
            if (Math.random() * 100 >= failChance.getValue()) {
                InputHandler.simulateClick(mc.options.useKey, inputSimulation);
            }
            if (doubleTap.getValue()) breakTimer.reset();
            placeTimer.reset();
            simulateAttackNext = true;
            return;
        }

        if (breakReady && crystalVisit) {
            if (inputSimulation.getValue() && !cpsGate(false)) return;
            if (Math.random() * 100 >= failChance.getValue()) {
                InputHandler.simulateClick(mc.options.attackKey, inputSimulation);
            }
            breakTimer.reset();
            simulateAttackNext = false;
        }
    }

    private boolean isLookingAtValidTarget() {
        if (mc.crosshairTarget instanceof EntityHitResult entityHit) {
            if (entityHit.getEntity() instanceof EndCrystalEntity crystal) {
                return !crystal.isRemoved() && crystal.isAlive();
            }
            return false;
        }
        if (mc.crosshairTarget instanceof BlockHitResult blockHit) {
            BlockState state = mc.world.getBlockState(blockHit.getBlockPos());
            return state.isOf(Blocks.OBSIDIAN) || state.isOf(Blocks.BEDROCK);
        }
        return false;
    }

    private void handleNormalCrystalLogic() {
        if (TargetUtils.friendNear(mc.player.getEntityPos(), 6.5) && !solo.getValue()) return;
        if (inputSimulation.getValue()) {
            boolean hasCrystalInHand = mc.player.getMainHandStack().isOf(Items.END_CRYSTAL);
            boolean hasCrystalInHotbar = InventoryUtils.hasItemInHotbar(item -> item == Items.END_CRYSTAL);
            boolean hasCrystalInOffhand = mc.player.getOffHandStack().isOf(Items.END_CRYSTAL);
            if (!hasCrystalInHand && !hasCrystalInHotbar && !hasCrystalInOffhand) return;

            if (!hasCrystalInHand && hasCrystalInHotbar) {
                int crystalSlot = InventoryUtils.findItemWithPredicateInHotbar(itemStack -> itemStack.getItem() == Items.END_CRYSTAL);
                InventoryUtils.setInvSlot(crystalSlot, true);
                return;
            }

            handleClickSimulation(true);
            return;
        }
        handlePlaceCrystal();
        handleBreakCrystal();
    }

    private void handlePlaceCrystal() {
        if (!(mc.crosshairTarget instanceof BlockHitResult blockHit)) return;
        
        BlockPos pos = blockHit.getBlockPos();
        BlockState state = mc.world.getBlockState(pos);
        boolean validBlock = state.isOf(Blocks.OBSIDIAN) || state.isOf(Blocks.BEDROCK);
        
        if (!validBlock) return;
        if (BlockUtils.hasCrystalOnBlock(pos)) return;
        if (mc.player.getMainHandStack().getItem() != Items.END_CRYSTAL) {
            int crystalSlot = InventoryUtils.findItemWithPredicateInHotbar(itemStack -> itemStack.getItem() == Items.END_CRYSTAL);
            if (crystalSlot != -1) {
                InventoryUtils.setInvSlot(crystalSlot, inputSimulation.getValue());
                return; 
            }
            return;
        }

        if (placeTimer.hasReached(delayPlace.getValue() + pingBonusMs())) {
            if (Math.random() * 100 < failChance.getValue()) return;
            if (!cpsGate(true) || suicideRisk(Vec3d.ofCenter(blockHit.getBlockPos()))) return;
            InputHandler.simulateClick(mc.options.useKey, inputSimulation);
            if (doubleTap.getValue()) breakTimer.reset();
            placeTimer.reset();
        }
    }

    private void handleBreakCrystal() {
        if (!(mc.crosshairTarget instanceof EntityHitResult entityHit)) return;
        if (!(entityHit.getEntity() instanceof EndCrystalEntity crystal)) return;
        
        if (crystal.isRemoved() || !crystal.isAlive()) return;

        if (breakTimer.hasReached(crackCooldown.getValue() + pingBonusMs())) {
            if (Math.random() * 100 < failChance.getValue()) return;
            if (!cpsGate(false) || belowMinY(crystal.getEntityPos()) || suicideRisk(crystal.getEntityPos())) return;
            InputHandler.simulateClick(mc.options.attackKey, inputSimulation);
            breakTimer.reset();
        }
    }

    private boolean corpseNearby() {
        for (var player : mc.world.getPlayers()) {
            if (mc.player == player) continue;

            double distanceSquared = mc.player.squaredDistanceTo(player);
            if (distanceSquared < 36 && player.isDead()) {
                return true;
            }
        }
        return false;
    }

    /** Absolute click ceiling in both lanes; 0 = uncapped. CPS Acceleration creeps the ceiling
     *  up +2 per half second active until it doubles itself. */
    /** Lane gate: with Input Simulation on, right/left clicks each run at their own CPS; otherwise the shared cap. */
    private boolean cpsGate(boolean right) {
        if (!inputSimulation.getValue()) return cpsGate();
        long now = System.currentTimeMillis();
        int cps = Math.max(1, right ? rightCps.getValue() : leftCps.getValue());
        long last = right ? lastRightMs : lastLeftMs;
        if (now - last < 1000L / cps) return false;
        if (right) lastRightMs = now; else lastLeftMs = now;
        return true;
    }

    private boolean cpsGate() {
        int cap = clickCap.getValue();
        if (cap <= 0) return true;
        if (cpsAcceleration.getValue()) {
            long stamp = System.currentTimeMillis();
            if (stamp - accelStampMs >= 500) {
                accelStampMs = stamp;
                accelBonusCps = Math.min(accelBonusCps + 2, cap);
            }
            cap += accelBonusCps;
        }
        long now = System.currentTimeMillis();
        if (now - lastSimClickMs < 1000L / cap) return false;
        lastSimClickMs = now;
        return true;
    }

    /** Clone (Ping Compensate) — stream extends the pacing windows by our measured latency
     *  (HurtTickHandler law: tab-list entry latency, full ms). */
    private int pingBonusMs() {
        if (!pingCompensate.getValue() || mc.getNetworkHandler() == null) return 0;
        PlayerListEntry info = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
        return info == null ? 0 : info.getLatency();
    }

    /** Clone (Min Y) — crystals standing a window below our feet don't pay for the trade.
     *  0 disables the height filter. */
    private boolean belowMinY(Vec3d blastCenter) {
        float minYFloor = minY.getValue();
        if (minYFloor <= 0.0f) return false;
        return blastCenter.y - mc.player.getEntityPos().y < minYFloor - 1.0f;
    }

    /** Blast would floor us: never trade below half a heart — vanilla crystal power is radius 6. */
    private boolean suicideRisk(Vec3d blastCenter) {
        if (!antiSuicide.getValue()) return false;
        float self = com.utilitymod.echo.utils.combat.ExplosionUtils.getExplosionDamageTo(mc.player, blastCenter, 6.0f);
        return self >= mc.player.getHealth() - 1.0f;
    }

    public int getActivateKey() {
        return activateKey.getKey();
    }

    public boolean isHitCrystalEnabled() {
        return hitCrystal.getValue();
    }
}
