package com.utilitymod.echo.features.impl.misc;

import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventTick;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.settings.impl.BoolSetting;
import com.utilitymod.echo.features.settings.impl.IntSetting;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/** Eats when you get hungry: picks food from your hotbar, holds use, then switches back. */
public class AutoEat extends Feature {
    private final IntSetting hunger = new IntSetting(LT.L1, 14, 1, 19);
    private final BoolSetting allowGapples = new BoolSetting(LT.L2, false);
    private final BoolSetting pauseInCombat = new BoolSetting(LT.L3, true);
    private int prevSlot = -1;
    private boolean eating;

    public AutoEat() {
        super(new FeatureInfo(dev.potato.spud.api.ProtectedText.fromInts(new int[]{0x5F,0x5C,0x04,0xD4,0xE2,0x48,0x35,0xEB}), LT.L4, Category.UTILITY));
    }

    @Override
    public void onDisable() {
        super.onDisable();
        stop();
    }

    private boolean edible(ItemStack s) {
        if (s.isEmpty() || s.get(DataComponentTypes.FOOD) == null) return false;
        if (s.isOf(Items.ROTTEN_FLESH) || s.isOf(Items.SPIDER_EYE) || s.isOf(Items.POISONOUS_POTATO)
                || s.isOf(Items.PUFFERFISH) || s.isOf(Items.CHORUS_FRUIT) || s.isOf(Items.SUSPICIOUS_STEW)) return false;
        return allowGapples.getValue() || !(s.isOf(Items.GOLDEN_APPLE) || s.isOf(Items.ENCHANTED_GOLDEN_APPLE));
    }

    private void stop() {
        if (eating && mc.options != null) mc.options.useKey.setPressed(false);
        if (prevSlot >= 0 && mc.player != null) mc.player.getInventory().setSelectedSlot(prevSlot);
        eating = false;
        prevSlot = -1;
    }

    @EventSubscribe
    private void onTick(EventTick event) {
        if (isNull() || mc.currentScreen != null) {
            if (eating) stop();
            return;
        }
        boolean hungry = mc.player.getHungerManager().getFoodLevel() < hunger.getValue() && !mc.player.isCreative();
        if (pauseInCombat.getValue() && mc.options.attackKey.isPressed()) hungry = false;
        if (!hungry) {
            if (eating) stop();
            return;
        }
        if (!eating) {
            var inv = mc.player.getInventory();
            int best = -1;
            if (edible(mc.player.getOffHandStack())) best = -2;
            else for (int i = 0; i < 9; i++) if (edible(inv.getStack(i))) { best = i; break; }
            if (best == -1) return;
            if (best >= 0) {
                prevSlot = inv.getSelectedSlot();
                inv.setSelectedSlot(best);
            }
            eating = true;
        }
        mc.options.useKey.setPressed(true);
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3c96,0x08b8,0x2e8a,0xe7d4,0xbac5,0xeeca,0xb7a9,0x2be6,0xb9d6}, new char[]{0x2ba3,0xb7c8,0xbab1,0xb9f6,0x2ec8,0xe7b1,0x08d4,0x3cf9,0xeebd}).toString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb73f,0xa4e0,0x4e83,0x93c0,0x7c30,0x9d3a,0xc838,0xa65c,0xd07d,0x4bee,0xa6bf,0x995e,0x747b}, new char[]{0x4ba9,0x93af,0x9932,0xd019,0xa6da,0xb751,0xc818,0x9d7b,0xa62c,0x4ef3,0x7c5c,0xa485,0x7408}).toString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9009,0x2c1a,0xfd22,0x1a72,0x3b7e,0xc61b,0xef18,0x0e34,0x0b93,0xe502,0x55da,0xe827,0xec1c,0x5c4c,0x5c24,0x72fe,0x9ba0,0x76fc,0xa798,0x44b4}, new char[]{0x76ac,0xe846,0x1a07,0x44c7,0x2c7f,0x55fa,0x5c73,0x0e5c,0x3b1b,0x7290,0xe522,0x9be1,0x5c38,0x0be7,0xec7d,0xef7b,0xc670,0xfd4b,0x9067,0xa7ff}).toString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x09f4,0xd386,0xfa54,0x4e98,0x5f93,0x35db,0xb261,0x88a9,0x495a,0xa718,0x6cc5,0x23eb,0x1931,0x8336,0x63cd,0x4e68,0x172d,0x09e9,0xfd35,0x5870,0xf587,0xcf74,0x824f,0x3e7b,0xc71d,0x66f3,0xb23b,0x2c12,0x28d3,0xc50a,0xbc22,0x9040,0xb4eb,0x0c1a,0x23bd,0xe5fc,0x9b58,0x2606,0x48c9}, new char[]{0xa75d,0xfd54,0x4e1c,0xb248,0x2c32,0x48af,0x2399,0x902f,0x0c77,0xb241,0x834f,0x28bc,0xcf01,0x5802,0x66d3,0x9b30,0x88c6,0x5fe7,0xbc40,0xf5e6,0x099b,0x826f,0xe58b,0x35b3,0x23d8,0xd3e8,0x1911,0x63a5,0xfa21,0x1743,0x3e1c,0x493f,0x4eea,0xc52a,0x6ca1,0xc76f,0xb484,0x2676,0x0987}).toString();
    }
}
