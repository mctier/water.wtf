package com.utilitymod.echo.features.impl.combat;

import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventStartAttack;
import com.utilitymod.echo.event.impl.EventHandleInput;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.settings.impl.BoolSetting;
import com.utilitymod.echo.features.settings.impl.RangeSetting;
import com.utilitymod.echo.handlers.impl.SwapStateManager;
import com.utilitymod.echo.utils.combat.TargetUtils;
import com.utilitymod.echo.utils.inventory.InventoryUtils;
import com.utilitymod.echo.utils.strings.Concat;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Items;
import com.utilitymod.echo.utils.EncryptedString;

import static net.minecraft.enchantment.Enchantments.KNOCKBACK;

public class TotemHitModule extends Feature{
    public TotemHitModule() {
        super(new FeatureInfo(
            dev.potato.spud.api.ProtectedText.fromInts(new int[]{0x4A,0x46,0x04,0xDE,0xAF,0x2D,0x1C,0xF6,0xD2}),
            new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4a09,0xa1a9,0x58ad,0x6555,0x2604,0x8ae8,0x37cd,0x2a90,0x4781,0x82c2,0xb9c2,0x1857,0x9bf6,0xf89b,0x7222,0xac4a,0x0b28,0x1cd2,0x50d9,0x76b6,0x9628,0x5f28,0xeb8e,0x04b4,0xa8bc,0xa43e,0x93a5,0x3344,0xfcec,0x6d61,0xb12f,0xd129,0xbc19,0xf044,0xe72e,0x9e48,0xd92a,0x3ff9,0xd2a5,0xb513,0x8671,0x3b36,0x0cdb,0x002f,0xc15b,0x7eca,0xcdf7,0xc5d0,0x11fe,0x2279}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87,0x76d4,0xf025,0x4a6a,0xc5bb,0x5f08,0xd959,0x50ae,0x2aff,0xa44c,0x3f9d,0xb9e2,0x3333,0x8a80,0x04d1,0x9e26,0x1877,0x93c4,0x6d15,0xe75a,0x7eab,0xf8f8,0x7249,0xcd9e,0x47ef,0xc13c,0x588d,0xd2d2,0xac23,0x2670,0xa1c1,0x3b16,0xb567,0x0cb4,0x8605,0x004a,0x9b9b}),
            Category.COMBAT
        ));
    }
    private final BoolSetting swapSilent = new BoolSetting(dev.potato.spud.api.ProtectedText.fromInts(new int[]{0x4D,0x40,0x1C,0xDE,0xAC,0x79,0x74,0xCC,0xD1,0x90,0x48}), false);
    private final RangeSetting swapBackDelay = new RangeSetting
    (new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2274,0xd13e,0x37c7,0xa8ae,0xbc08}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be}), 1, 1, 1, 20, 1, (obj -> !swapSilent.getValue()));
    private final com.utilitymod.echo.features.settings.impl.InputSimulationSetting inputSimulation = new com.utilitymod.echo.features.settings.impl.InputSimulationSetting(new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1c9f,0x9636,0xbc1c,0x11fc,0x76bb,0x0b0f,0xeb80,0x37ca,0xf04b,0xd133,0x82c4,0x2268,0xfcee,0xa8a5,0xb12f,0x6542}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87,0x76d4,0xf025}), true, (obj -> !swapSilent.getValue())).describedBy(new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd93b,0x58f9,0x8aa0,0xa8a3,0xc5d4,0x1c86,0xbc1d,0x4783,0xb987,0x334a,0xa1b2,0x76b0,0x04a1,0x261e,0x3ff6,0x7224,0x7ed8,0xd2bb,0xb16a,0xc15d,0x50cf,0x227d,0xcdeb,0x962c,0xa46c,0xfce9,0xe77a,0x37d6,0x828d,0xeb92,0x0b2e,0x6553,0x93b7,0xf005,0xac4c,0xd12d,0x4a02,0x6d66,0x9e54,0x1812,0x2a8d,0xf891,0x11b0,0x5f7c}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87,0x76d4,0xf025,0x4a6a,0xc5bb,0x5f08,0xd959,0x50ae,0x2aff,0xa44c,0x3f9d,0xb9e2,0x3333,0x8a80,0x04d1,0x9e26,0x1877,0x93c4,0x6d15,0xe75a,0x7eab,0xf8f8,0x7249,0xcd9e,0x47ef,0xc13c,0x588d,0xd2d2,0xac23,0x2670,0xa1c1}));

    boolean attacked = false;

    private int findHotbarSlotWithKnockbackSword() {
        return InventoryUtils.findItemWithEnchantmentInHotbar(
            stack -> InventoryUtils.isSword(stack.getItem()),
            KNOCKBACK,
            false
        );
    }

    @Override
    public void onDisable() {
        super.onDisable();
        attacked = false;
        SwapStateManager.cancel(this);
    }

    @EventSubscribe
    private void onAttack(EventStartAttack.Pre event) {
        if (isNull()) return;
        if (mc.isPaused()) return;
        if (mc.player == null) return;
        if (!(event.getTarget() instanceof LivingEntity entity)) return;
        if (!isEntityAllowed(entity)) return;
        if (mc.player.getMainHandStack().getItem() != Items.TOTEM_OF_UNDYING) return;
        int knockbackSwordSlot = findHotbarSlotWithKnockbackSword();
        if (knockbackSwordSlot == -1) return;
        if (!SwapStateManager.swapTo(
                this,
                knockbackSwordSlot,
                inputSimulation.getValue() && !swapSilent.getValue(),
                swapSilent.getValue() ? 0 : (int) swapBackDelay.getRandom(),
                !swapSilent.getValue(),
                () -> attacked = false)) {
            return;
        }
        attacked = true;
    }

    private boolean isEntityAllowed(LivingEntity entity) {
		if (!TargetUtils.isTargetAllowed(entity)) {
            return false;
        }
        return true;
	}
}
