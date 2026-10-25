package com.utilitymod.echo.features.impl.combat;

import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventStartAttack;
import com.utilitymod.echo.event.impl.EventSwingHand;
import com.utilitymod.echo.event.impl.EventHandleInput;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.settings.impl.BoolSetting;
import com.utilitymod.echo.features.settings.impl.IntSetting;
import com.utilitymod.echo.features.settings.impl.KeybindSetting;
import com.utilitymod.echo.features.settings.impl.ModeSetting;
import com.utilitymod.echo.features.settings.impl.InputSimulationSetting;
import com.utilitymod.echo.handlers.InputHandler;
import com.utilitymod.echo.utils.combat.SpearUtils;
import com.utilitymod.echo.utils.strings.Concat;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import com.utilitymod.echo.features.settings.impl.ItemPickerSetting;
import com.utilitymod.echo.utils.inventory.InventoryUtils;
import com.utilitymod.echo.utils.math.TimerUtils;
import com.utilitymod.echo.utils.EncryptedString;
public class SpearLungeModule extends Feature {

	public SpearLungeModule() {
		super(new FeatureInfo(
			new com.utilitymod.echo.utils.EncryptedString(new char[]{0x11f5,0xa8bb,0xbc08,0x227d,0x1c9c,0x82d8,0x0b10,0xd129,0xb12f,0x9624,0x37cc}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190}),
			new com.utilitymod.echo.utils.EncryptedString(new char[]{0x04b8,0x6d61,0x1cd2,0x0b34,0x2a9a,0x335a,0x7226,0x3ffc,0xe735,0xeb84,0x8aec,0xd937,0xc5d7,0xa1aa,0x227d,0xd2a7,0x7e8b,0xac4a,0x93e4,0xcde8,0x76f3,0x379e,0xb17b,0xd12f,0x2613,0xa8b8,0x4a4a,0x11e0,0xb980,0x478a,0xc11c,0x82c8,0xa46c,0x58fc,0x9e52,0x3b7a,0xbc1a,0x5f7d,0x6557,0xfcf5,0x9630,0x180e,0x50c9,0xf895,0xf056,0xb51e}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87,0x76d4,0xf025,0x4a6a,0xc5bb,0x5f08,0xd959,0x50ae,0x2aff,0xa44c,0x3f9d,0xb9e2,0x3333,0x8a80,0x04d1,0x9e26,0x1877,0x93c4,0x6d15,0xe75a,0x7eab,0xf8f8,0x7249,0xcd9e,0x47ef,0xc13c,0x588d,0xd2d2,0xac23,0x2670,0xa1c1,0x3b16,0xb567}),
			Category.COMBAT
		));
	}

    private final ModeSetting mode = new ModeSetting(new com.utilitymod.echo.utils.EncryptedString(new char[]{0x227c,0xa8a4,0xd137,0xbc0c}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69}), new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa8bf,0xb164,0xd13b,0x226c,0x37dd,0xbc08}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f}), new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa8bf,0xb164,0xd13b,0x226c,0x37dd,0xbc08}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f}), new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2261,0xd131,0xb16a,0x0b2f,0xbc19,0x37cc,0xa8ae,0x82de}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad}));
    private final BoolSetting silent = new BoolSetting(new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa8a2,0xb17b,0xd129,0x2274,0x37d0,0xbc0c}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f}), false);
    private final ModeSetting cooldown = new ModeSetting(new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2277,0xd139,0xb160,0x0b2b,0xbc05,0x37da,0xa8a4,0x82c3}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad}), new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0b3d,0xd12c,0x37d2,0xb163,0xbc00,0x2276,0xa8aa}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c}), new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0b3d,0xd12c,0x37d2,0xb163,0xbc00,0x2276,0xa8aa}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c}), new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2268,0xd12a,0x37cc,0xa8aa,0xbc0c}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be}), new com.utilitymod.echo.utils.EncryptedString(new char[]{0xbc0c,0x2276,0xa8a4,0xd134}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643}));
    private final IntSetting cooldownThreshold = new IntSetting(new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa8a4,0x9666,0x82c3,0x37da,0x0b2b,0x1cd2,0x2277,0xb160,0xd139,0xbc05}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643}), 85, 0, 100, sexy -> cooldown.is(new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0b3d,0xd12c,0x37d2,0xb163,0xbc00,0x2276,0xa8aa}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c})));
    /**
     * Stab count. Damage-cost multiplier was removed: lunge now always sends a single STAB
     * (1 = vanilla server cost) so the free lunge reads as a normal thrust on the wire.
     */
    private static final int multiplier = 1;
    private final ItemPickerSetting itemWhitelist = new ItemPickerSetting(new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd133,0x0b34,0xb158,0x379e,0x9626,0x227d,0xeb88,0xfcf3,0x82c4,0xbc04,0xa8bf,0x1c86,0x11fc,0x6545}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87}), sexy -> mode.is(new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa8bf,0xb164,0xd13b,0x226c,0x37dd,0xbc08}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f})));
    private final KeybindSetting activateKey = new KeybindSetting(new com.utilitymod.echo.utils.EncryptedString(new char[]{0xeb98,0x0b28,0x1cd2,0x9608,0x226c,0xd13b,0x11f5,0x82c8,0xa8a8,0x37c8,0xb16e,0xbc00}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1}), -1, sexy -> mode.is(new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2261,0xd131,0xb16a,0x0b2f,0xbc19,0x37cc,0xa8ae,0x82de}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad})));
    private final InputSimulationSetting inputSimulation = new InputSimulationSetting(new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1c9f,0x9636,0xbc1c,0x11fc,0x76bb,0x0b0f,0xeb80,0x37ca,0xf04b,0xd133,0x82c4,0x2268,0xfcee,0xa8a5,0xb12f,0x6542}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87,0x76d4,0xf025}), false, sexy -> !silent.getValue());

    private static final int delaySwitch = 1;
    private final TimerUtils timer = new TimerUtils();
    private int originalSlot = -1;
    private boolean shouldSwitchBack = false;
    private boolean keyWasDown = false;

    private int getSpearSlotWithLunge() {
        return InventoryUtils.findItemWithEnchantmentInHotbar(
            stack -> InventoryUtils.isItemSpear(stack.getItem()),
            Enchantments.LUNGE,
            false
        );
    }

    private boolean isHoldingSpearWithLunge() {
        if (mc.player == null) return false;
        ItemStack mainHand = mc.player.getMainHandStack();
        if (mainHand.isEmpty() || !InventoryUtils.isItemSpear(mainHand.getItem())) {
            return false;
        }
        if (mc.world == null) return false;
        var opt = mc.world.getRegistryManager().getOptionalEntry(Enchantments.LUNGE);
        return opt.isPresent() && EnchantmentHelper.getLevel(opt.get(), mainHand) > 0;
    }

    @EventSubscribe
    public final void onDoAttackPre(EventStartAttack.Pre event) {
        if (!mode.is(new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa8bf,0xb164,0xd13b,0x226c,0x37dd,0xbc08}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f}))) return;
        if (isNull()) return;
        if (!isEnabled()) return;
        if (com.utilitymod.echo.api.MinecraftCompat.getScreen() != null) return;
        if (mc.isPaused()) return;
        if (isHoldingSpearWithLunge()) return;
        if (!isHoldingAllowed()) return;

        performSwap();
    }

    @EventSubscribe
    public void onStartAttack(EventStartAttack event) {
        if (!silent.getValue()) return;
        if (isNull()) return;
        if (!isEnabled()) return;

        boolean holdingLungeSpear = isHoldingSpearWithLunge();
        boolean swapWasSilent = silent.getValue() && shouldSwitchBack;

        if (!holdingLungeSpear && !swapWasSilent) return;

        event.cancel();
        sendStabPackets();

        if (swapWasSilent) {
            InventoryUtils.swapSilentBack();
            shouldSwitchBack = false;
        }
    }

    @EventSubscribe
    public void onHandleInputKeypress(EventHandleInput.Early event) {
        if (!mode.is(new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2261,0xd131,0xb16a,0x0b2f,0xbc19,0x37cc,0xa8ae,0x82de}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad}))) return;
        if (isNull()) return;
        if (!isEnabled()) return;
        if (com.utilitymod.echo.api.MinecraftCompat.getScreen() != null) return;
        if (mc.isPaused()) return;
        if (shouldSwitchBack) return;

        boolean keyDown = InputHandler.isBindDown(activateKey.getKey());
        if (keyDown && !keyWasDown) {
            if (isHoldingSpearWithLunge()) {
                keyWasDown = true;
                return;
            }
            if (performSwap()) {
                if (inputSimulation.getValue()) {
                    InputHandler.simulateClick(mc.options.attackKey, inputSimulation);
                } else {
                    com.utilitymod.echo.api.Accessors.invokeStartAttack();
                }
            }
        }
        keyWasDown = keyDown;
    }

    private boolean performSwap() {
        int spearSlot = getSpearSlotWithLunge();
        if (spearSlot == -1) return false;

        // "None" cooldown mode: lunge is fireable every click - neither the Vanilla-style
        // charge gate nor the Paper thrust gate applies, and the single STAB packet rides the
        // regular swing stream, so the server sees a thrust it accepts.
        if (cooldown.is(new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0b3d,0xd12c,0x37d2,0xb163,0xbc00,0x2276,0xa8aa}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c}))) {
            if (mc.player.getAttackCooldownProgress(0.0F) < cooldownThreshold.getValue() / 100.0F) return false;
        } else if (cooldown.is(new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2268,0xd12a,0x37cc,0xa8aa,0xbc0c}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be}))) {
            ItemStack spearStack = mc.player.getInventory().getStack(spearSlot);
            if (!SpearUtils.isSpearThrustFullyCooled(mc.player, spearStack, 0.0F)) return false;
        }

        int currentSlot = mc.player.getInventory().getSelectedSlot();
        if (currentSlot == spearSlot) return false;

        originalSlot = currentSlot;

        if (silent.getValue()) {
            InventoryUtils.swapSilentTo(spearSlot);
        } else {
            InventoryUtils.setInvSlot(spearSlot, inputSimulation.getValue());
        }

        timer.reset();
        shouldSwitchBack = true;
        return true;
    }

    private void sendStabPackets() {
        for (int i = 0; i < multiplier; i++) {
            mc.player.networkHandler.sendPacket(
                new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STAB, BlockPos.ORIGIN, Direction.DOWN)
            );
        }
    }

    @EventSubscribe
    public void onSwingHand(EventSwingHand event) {
        if (event.getPlayer() == mc.player) {
            SpearUtils.onPlayerSwing(mc.player);
        }
    }

    @EventSubscribe
    public void onHandleInputPost(EventHandleInput.Post event) {
        if (isNull()) return;

        if (shouldSwitchBack) {
            if (silent.getValue()) {
                InventoryUtils.swapSilentBack();
                shouldSwitchBack = false;
            } else if (timer.hasReachedTicks(1)) {
                if (originalSlot != -1) {
                    mc.player.getInventory().setSelectedSlot(originalSlot);
                    originalSlot = -1;
                }
                shouldSwitchBack = false;
            }
        }
    }

    private boolean isHoldingAllowed() {
		if (mc.player == null) return false;
		if (itemWhitelist.getSelectedCount() == 0) return true;

		ItemStack mainHand = mc.player.getMainHandStack();
		if (mainHand.isEmpty()) {
			return itemWhitelist.isSelected(Items.AIR);
		}

		return itemWhitelist.isSelected(mainHand.getItem());
	}

    public ItemStack getPaperCooldownSpearStack() {
        if (!isEnabled() || !cooldown.is(new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2268,0xd12a,0x37cc,0xa8aa,0xbc0c}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be})) || isNull()) {
            return ItemStack.EMPTY;
        }

        int spearSlot = getSpearSlotWithLunge();
        return spearSlot == -1 ? ItemStack.EMPTY : mc.player.getInventory().getStack(spearSlot);
    }

    @Override
    public void onDisable() {
        if (originalSlot != -1 && !silent.getValue()) {
            mc.player.getInventory().setSelectedSlot(originalSlot);
        }
        originalSlot = -1;
        shouldSwitchBack = false;
        keyWasDown = false;
    }
}
