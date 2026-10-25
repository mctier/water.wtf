package com.utilitymod.echo.features.impl.misc;

import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventTick;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.settings.impl.IntSetting;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

/** Keeps a totem in your offhand: refills it from your inventory right after one pops. */
public class TotemFlow extends Feature {
    private final IntSetting delay = new IntSetting(LT.L1, 1, 0, 10);
    private int wait;

    public TotemFlow() {
        super(new FeatureInfo(dev.potato.spud.api.ProtectedText.fromInts(new int[]{0x5F,0x5C,0x04,0xD4,0xE2,0x59,0x3B,0xEB,0xC3,0x9C}), LT.L2, Category.UTILITY));
    }

    @EventSubscribe
    private void onTick(EventTick event) {
        if (isNull()) return;
        if (mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
            wait = delay.getValue();
            return;
        }
        // only with no screen or the player inventory open (other containers use different slot ids)
        if (mc.currentScreen != null && !(mc.currentScreen instanceof InventoryScreen)) return;
        if (wait-- > 0) return;
        var inv = mc.player.getInventory();
        for (int i = 0; i < 36; i++) {
            if (!inv.getStack(i).isOf(Items.TOTEM_OF_UNDYING)) continue;
            int slot = i < 9 ? 36 + i : i; // inventory index -> player screen slot
            // SWAP with button 40 = swap with offhand
            mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, slot, 40, SlotActionType.SWAP, mc.player);
            wait = delay.getValue();
            return;
        }
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa0e5,0x7352,0x8698,0x9334,0x554b,0x1c58,0x22c5,0xb0c1,0xfa1d,0xeaaf,0x8933,0xf30d,0xc17a}, new char[]{0xc13e,0xeaca,0x733e,0x552a,0x934d,0xfa3d,0xb0e9,0x1c2c,0x895a,0xf36e,0x22ae,0x86eb,0xa0cc}).toString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xfc91,0x9eec,0x4f49,0x58f3,0x9f32,0x3a43,0x5538,0x9a48,0x3f2a,0x71b0,0xcd88,0xec9f,0x8503,0xae01,0x598c,0x4cc5,0xfb7a,0x9988,0x69c3,0x0136,0x83b1,0xcdd2,0xd73d,0xdf86,0xdce6,0x3567,0xd667,0x7d09,0x77b9,0x7fe5,0x8581,0x13f0,0x688e,0x2da5,0xd8f9,0x6507,0x3b9c,0x9a2a,0x537c,0xb27d,0x7c7a,0xae9a,0x7a12,0xf7f9,0x7d23,0x82a1}, new char[]{0x85d3,0x7d6c,0x0150,0x3bf5,0x69af,0x5310,0xdc95,0x68ae,0xd61e,0x3508,0x3a36,0x1382,0x8391,0x589c,0x9a2e,0x4f2f,0x7c12,0x77d8,0x99e6,0xfb1e,0x9f12,0xf78d,0x4caa,0x7a66,0x2dc0,0xb210,0x3f0a,0x7d45,0xcdfa,0xfcfe,0xecf2,0x8281,0xae78,0xcdbd,0x7f90,0xd74f,0xdfa6,0x656e,0xaef4,0x9a5c,0x555d,0x59e2,0x71c4,0x856c,0x9e9e,0xd880}).toString();
    }
}
