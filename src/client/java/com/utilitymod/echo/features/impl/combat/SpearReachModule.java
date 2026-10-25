package com.utilitymod.echo.features.impl.combat;

import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventStartAttack;
import com.utilitymod.echo.event.impl.EventSwingHand;
import com.utilitymod.echo.event.impl.EventTick;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.utils.combat.CombatUtils;
import com.utilitymod.echo.utils.combat.SpearUtils;
import com.utilitymod.echo.utils.strings.Concat;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import com.utilitymod.echo.features.settings.impl.BoolSetting;
import com.utilitymod.echo.features.settings.impl.ItemPickerSetting;
import com.utilitymod.echo.handlers.impl.SwapStateManager;
import com.utilitymod.echo.utils.inventory.InventoryUtils;
import com.utilitymod.echo.utils.math.TimerUtils;

public class SpearReachModule extends Feature {

	public SpearReachModule() {
		super(new FeatureInfo(
			Concat.of(new com.utilitymod.echo.utils.EncryptedString(new char[]{0x11f8,0xa8bb,0xbc08,0x227d,0x1c93,0x82c8,0x0b0e,0xd129,0xb12f,0x9620,0x37cc}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190}).toString()),
			Concat.of(LT.L1),
			Category.COMBAT
		));
	}

    private final BoolSetting paperSupport = new BoolSetting(Concat.of(LT.L2), true);
    private final ItemPickerSetting itemWhitelist = new ItemPickerSetting(Concat.of(LT.L3));

    private static final int delaySwitch = 1;
    private final TimerUtils timer = new TimerUtils();
    private boolean shouldSwitchBack = false;

    private int getSpearSlotWithoutLunge() {
        if (mc.player == null || mc.world == null) return -1;
        
        var inventory = mc.player.getInventory();
        var opt = mc.world.getRegistryManager().getOptionalEntry(Enchantments.LUNGE);
        
        for (int i = 0; i <= 8; i++) {
            ItemStack stack = inventory.getStack(i);
            if (stack != null && !stack.isEmpty() && InventoryUtils.isItemSpear(stack.getItem())) {
                if (opt.isEmpty() || EnchantmentHelper.getLevel(opt.get(), stack) == 0) {
                    return i;
                }
            }
        }
        
        return -1;
    }

    private boolean isHoldingSpearWithoutLunge() {
        if (mc.player == null) return false;
        ItemStack mainHand = mc.player.getMainHandStack();
        if (mainHand.isEmpty() || !InventoryUtils.isItemSpear(mainHand.getItem())) {
            return false;
        }
        if (mc.world == null) return false;
        var opt = mc.world.getRegistryManager().getOptionalEntry(Enchantments.LUNGE);
        return opt.isEmpty() || EnchantmentHelper.getLevel(opt.get(), mainHand) == 0;
    }

    @EventSubscribe
    public final void onDoAttackPre(EventStartAttack event) {
        if (getSwapReadyCrosshairTarget() == null) return;

        int spearSlot = getSpearSlotWithoutLunge();
        if (spearSlot == -1) return;

        int currentSlot = mc.player.getInventory().getSelectedSlot();

        if (currentSlot == spearSlot) return;

        if (!SwapStateManager.swapTo(this, spearSlot, false, delaySwitch, true, () -> shouldSwitchBack = false)) {
            return;
        }
        timer.reset();
        shouldSwitchBack = true;
    }

    @EventSubscribe
    public void onSwingHand(EventSwingHand event) {
        if (paperSupport.getValue() && event.getPlayer() == mc.player) {
            SpearUtils.onPlayerSwing(mc.player);
        }
    }

    @EventSubscribe
    public void onHandleInputPost(EventTick event) {
        if (isNull()) return;

        if (shouldSwitchBack && timer.hasReachedTicks(delaySwitch)) {
            SwapStateManager.cancel(this);
            shouldSwitchBack = false;
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
        if (!isEnabled() || !paperSupport.getValue() || isNull()) {
            return ItemStack.EMPTY;
        }

        int spearSlot = getSpearSlotWithoutLunge();
        return spearSlot == -1 ? ItemStack.EMPTY : mc.player.getInventory().getStack(spearSlot);
    }

    @Override
    public void onDisable() {
        SwapStateManager.cancel(this);
        shouldSwitchBack = false;
    }

    public LivingEntity getSwapReadyCrosshairTarget() {
        float partialTick = mc.getRenderTickCounter().getTickProgress(false);
        return getSwapReadyCrosshairTarget(partialTick);
    }

    public LivingEntity getSwapReadyCrosshairTarget(float partialTick) {
        if (!isEnabled()) return null;
        if (isNull()) return null;
        if (com.utilitymod.echo.api.MinecraftCompat.getScreen() != null) return null;
        if (mc.isPaused()) return null;
        if (isHoldingSpearWithoutLunge()) return null;
        if (!isHoldingAllowed()) return null;
        if (CombatUtils.hasCurrentItemCrosshairTarget(mc.player, partialTick)) return null;

        LivingEntity target = CombatUtils.getSpearCrosshairTarget(mc.player, partialTick);
        if (target == null) return null;

        int spearSlot = getSpearSlotWithoutLunge();
        if (spearSlot == -1) return null;

        if (paperSupport.getValue()) {
            ItemStack spearStack = mc.player.getInventory().getStack(spearSlot);
            if (!SpearUtils.isSpearThrustFullyCooled(mc.player, spearStack, partialTick)) return null;
        } else if (mc.player.getAttackCooldownProgress(partialTick) < 1.0F) {
            return null;
        }

        return target;
    }

    public static boolean isSwapActive() {
        return SwapStateManager.isOwnerActive(SpearReachModule.class);
    }



    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1775,0xe4a7,0x07fa,0x1167,0x99b0,0x3253,0x1b66,0x1057,0xd867,0x057a,0x081c,0x4cfc,0x1a50,0x7c64,0x487c,0xeab0,0x15f1,0xec65,0x9d41,0xb90a,0x01d5,0x073d,0x0b34,0xd932,0x83dd,0x62f4,0xc7a3,0x4cda,0xe57f,0x4e87,0x94b8,0xeb90,0x429c,0x90dd,0x21b9}, new char[]{0xd961,0xeac7,0xe51e,0x1581,0x0509,0x1b46,0x1a24,0x42f3,0xebb0,0x1024,0x4ef7,0x4c99,0x075c,0x086e,0xb979,0x9990,0x94de,0x629b,0x0788,0x01f5,0x110e,0x90b3,0x83be,0x7c16,0xc7c6,0xe4c6,0x480f,0x1710,0xd803,0x3273,0xec17,0x0b51,0x9d20,0x4cb9,0x21d1}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x368d,0x2726,0x3693,0x1b2e,0x3969,0x5ce2,0x8f5f,0xc9e0,0x8b43,0x51c0,0x45e3,0xa091,0x8ab7}, new char[]{0x8ae7,0x4582,0x51b0,0x390c,0x36e1,0x2706,0x8f2c,0x5c97,0xc990,0x8b33,0x36e2,0x1b5c,0xa0e5}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xbfca,0xa706,0x4ab6,0x53e5,0x925c,0x1348,0x4e4c,0xa6e9,0x91b5,0x152a,0x0d00,0x1881,0xfdb1,0xcfaf}, new char[]{0x4aff,0x91c1,0xbfaf,0x9231,0x1368,0xa751,0x4e24,0x0d69,0xfdc5,0x5380,0xa685,0xcfc6,0x1559,0x18f5}).plainString();
    }
}
