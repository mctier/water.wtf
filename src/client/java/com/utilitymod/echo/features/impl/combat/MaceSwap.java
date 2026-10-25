package com.utilitymod.echo.features.impl.combat;

import net.minecraft.client.util.InputUtil;
import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventHandleInput;
import com.utilitymod.echo.event.impl.EventPacketReceive;
import com.utilitymod.echo.event.impl.EventStartAttack;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.impl.misc.TargetControlModule;
import com.utilitymod.echo.features.settings.impl.*;
import com.utilitymod.echo.handlers.InputHandler;
import com.utilitymod.echo.handlers.impl.SwapStateManager;
import com.utilitymod.echo.utils.combat.TargetUtils;
import com.utilitymod.echo.utils.inventory.InventoryUtils;
import com.utilitymod.echo.utils.inventory.ItemGroups;
import com.utilitymod.echo.utils.strings.Concat;
import com.utilitymod.echo.utils.world.WorldUtils;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.ShieldItem;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.util.hit.EntityHitResult;
import com.utilitymod.echo.utils.EncryptedString;

import static net.minecraft.enchantment.Enchantments.BREACH;
import static net.minecraft.enchantment.Enchantments.DENSITY;

public class MaceSwap extends Feature {

	public MaceSwap() {
		super(new FeatureInfo(
			dev.potato.spud.api.ProtectedText.fromInts(new int[]{0x53,0x48,0x13,0xDE,0xE2,0x5E,0x23,0xFE,0xD6}),
			new com.utilitymod.echo.utils.EncryptedString(new char[]{0x50da,0xd92d,0x3ff6,0xd129,0xeb82,0xa42f,0x2279,0x11f1,0xb991,0x2a9e,0x37cd,0x4a18,0x76b2,0xbc19,0x5f69,0xa8bc,0xc59b,0x962e,0x0b28,0x82c2,0xfca7,0x6553,0xf04a,0x1cd2,0xb12f}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87,0x76d4,0xf025,0x4a6a,0xc5bb,0x5f08,0xd959,0x50ae,0x2aff,0xa44c,0x3f9d,0xb9e2}),
			Category.COMBAT
		));

        inputSimulation.onChanged(this::syncDelayBounds);
        syncDelayBounds();
	}

    private static final RangeSetting delay = new RangeSetting(new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2274,0xd13e,0x37c7,0xa8ae,0xbc08}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be}), 1, 1, 0, 20, 1, new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa89f,0xb17c,0xd15a,0x2271,0x37d5,0xbc0a}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f}));
    public static final ItemPickerSetting itemWhitelist = new ItemPickerSetting(new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd133,0x0b34,0xb158,0x379e,0x9626,0x227d,0xeb88,0xfcf3,0x82c4,0xbc04,0xa8bf,0x1c86,0x11fc,0x6545}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87}));
    private static final FloatSetting useDensityAboveFallDistance = new FloatSetting(new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2276,0x9621,0x11ff,0xeb97,0x828d,0x0b25,0xa8ae,0xbc1a,0x1c93,0xd13e,0x37d7,0x6553,0xb17b}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536}), 1.5f, 0.0f, 8.0f, 0.1f, new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0b2f,0xd15a,0x37dd,0xb164,0xbc06,0x2274,0xa889}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c}));
    private static final BoolSetting stunSlamBool = new BoolSetting(new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0b30,0x1c9f,0x82cc,0x379e,0xbc07,0xa8bf,0xb15c,0x226d,0xd129}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2}), true);
    private final com.utilitymod.echo.features.settings.impl.InputSimulationSetting inputSimulation = new com.utilitymod.echo.features.settings.impl.InputSimulationSetting(new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1c9f,0x9636,0xbc1c,0x11fc,0x76bb,0x0b0f,0xeb80,0x37ca,0xf04b,0xd133,0x82c4,0x2268,0xfcee,0xa8a5,0xb12f,0x6542}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87,0x76d4,0xf025}), false, obj -> stunSlamBool.getValue());

    private static boolean shouldUseAfterAttack = false;

    @Override
    public void onEnable() {
        SwapStateManager.cancel(this);
        shouldUseAfterAttack = false;
        TargetControlModule.pendingShieldBreakTarget = null;
        super.onEnable();
    }

    @EventSubscribe
    public void onAttack(EventStartAttack.Pre event) {
        if (isNull()) return;
        if (com.utilitymod.echo.api.MinecraftCompat.getScreen() != null) return;
        if (!isAllowedWeapon()) return;
        if (SwapStateManager.hasActiveSwaps() && !SwapStateManager.isOwnerActive(this)) return;
        syncDelayBounds();

        boolean preferDensity = mc.player.fallDistance > useDensityAboveFallDistance.getValue();
        RegistryKey<Enchantment> maceEnchantment = preferDensity ? DENSITY : BREACH;

        if (mc.crosshairTarget instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof LivingEntity target) {
            if (!isEntityAllowed(target)) return;

            if (target instanceof PlayerEntity playerTarget) {
                boolean isShielding = playerTarget.isUsingItem() && playerTarget.getActiveItem().getItem() instanceof ShieldItem;
                if ((isShielding && !WorldUtils.isShieldFacingAway(playerTarget))) return;
            }
            int maceSlot = InventoryUtils.findMaceWithEnchantmentInHotbar(maceEnchantment, preferDensity);

            // Spear compatibility
            if (mc.options.useKey.isPressed()) {
                mc.options.useKey.setPressed(false);
                mc.player.clearActiveItem();
                shouldUseAfterAttack = true;
            }

            if (maceSlot != -1 && !SwapStateManager.isOwnerActive(this)) {
                int ticks = Math.max(0, (int) delay.getRandom());
                if (!SwapStateManager.swapTo(this, maceSlot, inputSimulation.getValue(), ticks, this::restoreUseKeyIfNeeded)) {
                    return;
                }
            }
        }
    }

    @EventSubscribe(priority = EventSubscribe.Priority.HIGH)
    private void onShieldBreakPredicted(EventHandleInput.Early event) {
        if (isNull()) return;
        if (com.utilitymod.echo.api.MinecraftCompat.getScreen() != null) return;
        if (!stunSlamBool.getValue()) return;
        if (!isAllowedWeapon()) return;

        // Shield break predictions should only live for the next input pass.
        LivingEntity target = TargetControlModule.pendingShieldBreakTarget;
        TargetControlModule.pendingShieldBreakTarget = null;

        if (target == null) return;
        if (SwapStateManager.hasActiveSwaps() && !SwapStateManager.isOwnerActive(this) && !SwapStateManager.isOwnerActive(AegisSplit.class)) return;
        syncDelayBounds();
        if (!isEntityAllowed(target)) return;

        boolean preferDensity = mc.player.fallDistance > useDensityAboveFallDistance.getValue();
        RegistryKey<Enchantment> maceEnchantment = preferDensity ? DENSITY : BREACH;
        int maceSlot = InventoryUtils.findMaceWithEnchantmentInHotbar(maceEnchantment, preferDensity);
        if (maceSlot == -1) return;
        if (!SwapStateManager.swapTo(this, maceSlot, inputSimulation.getValue(), Math.max(0, (int) delay.getRandom()), this::restoreUseKeyIfNeeded)) {
            return;
        }
        com.utilitymod.echo.api.Accessors.invokeStartAttack();
    }

    private boolean isAllowedWeapon() {
        if (itemWhitelist.getSelectedCount() == 0) return true;
        
        ItemStack mainHand = InventoryUtils.getMainHandItem();
        if (mainHand.isEmpty()) {
            return itemWhitelist.isSelected(Items.AIR);
        }
        
        return itemWhitelist.isSelected(mainHand.getItem());
    }

	private boolean isEntityAllowed(LivingEntity entity) {
        if (!TargetUtils.isTargetAllowed(entity)) {
            return false;
        }
        return true;
    }
    
    public static boolean allowsAxes() {
        if (itemWhitelist.getSelectedCount() == 0) return true;
        for (Item item : Registries.ITEM) {
            if (ItemGroups.axes().matches(item) && itemWhitelist.isSelected(item)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isSwapActive() {
        return SwapStateManager.isOwnerActive(MaceSwap.class);
    }

    @SuppressWarnings("unused")
    @EventSubscribe
    private void onPacketReceive(EventPacketReceive e) {
        if (isNull()) return;
        Packet<?> packet = e.getPacket();
        if (packet instanceof PlayerRespawnS2CPacket
                || packet instanceof PlayerPositionLookS2CPacket) {
            resetSwapState();
            TargetControlModule.pendingShieldBreakTarget = null;
        }
    }

    @Override
    public void onDisable() {
        SwapStateManager.cancel(this);
        resetSwapState();
        TargetControlModule.pendingShieldBreakTarget = null;
        super.onDisable();
    }

    private static void resetSwapState() {
        shouldUseAfterAttack = false;
    }

    private void restoreUseKeyIfNeeded() {
        if (!shouldUseAfterAttack) return;
        shouldUseAfterAttack = false;
        if (mc.options.useKey != null) {
            InputUtil.Key key = com.utilitymod.echo.api.Accessors.getKey(mc.options.useKey);
            if (key != null) {
                int bindCode = key.getCategory() == InputUtil.Type.MOUSE
                        ? (0x80000000 | key.getCode())
                        : key.getCode();
                if (InputHandler.isBindDown(bindCode)) {
                    mc.options.useKey.setPressed(true);
                }
            }
        }
    }

    private void syncDelayBounds() {
        float minBound = inputSimulation.getValue() ? 1f : 0f;
        delay.setLowerBound(minBound);

        if (delay.getMinValue() < minBound) {
            delay.setMinValue(minBound);
        }

        if (delay.getMaxValue() < minBound) {
            delay.setMaxValue(minBound);
        }
    }

}
