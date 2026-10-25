package com.utilitymod.echo.features.impl.cpvp;

import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventTick;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.settings.impl.IntSetting;
import com.utilitymod.echo.features.settings.impl.KeybindSetting;
import com.utilitymod.echo.features.settings.impl.ModeSetting;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.Items;

/**
 * OpenPrestige Cart Refill: keeps Target Slot stocked with a TNT minecart, pulling one from your main
 * inventory while the inventory is open (Always) or when you press Refill Key (Keybind).
 */
public class CartRefill extends Feature {
    private final ModeSetting mode = new ModeSetting(LT.L1, LT.L2, LT.L2, LT.L3);
    private final KeybindSetting key = new KeybindSetting(LT.L4, -1);
    private final IntSetting targetSlot = new IntSetting(LT.L5, 9, 1, 9);
    private final IntSetting delay = new IntSetting(LT.L6, 100, 0, 1000);

    private long readyAt;

    public CartRefill() {
        super(new FeatureInfo(LT.L7, LT.L8, Category.UTILITY));
    }

    @EventSubscribe
    @SuppressWarnings("unused")
    private void onTick(EventTick e) {
        if (isNull() || System.currentTimeMillis() < readyAt) return;
        boolean go = mode.is(LT.L2) ? mc.currentScreen instanceof InventoryScreen
                : com.utilitymod.echo.handlers.InputHandler.isBindDown(key.getKey());
        if (!go) return;
        int slot = targetSlot.getValue() - 1;
        var stack = mc.player.getInventory().getStack(slot);
        if (stack.isOf(Items.TNT_MINECART) && stack.getCount() >= stack.getMaxCount()) return;
        int src = Cpvp.findInventory(Items.TNT_MINECART, false);
        if (src < 0) return;
        if (stack.isOf(Items.TNT_MINECART)) {
            // top up: quick-move the stack back so the hotbar copy merges (vanilla shift-click behaviour)
            mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, src, 0,
                    net.minecraft.screen.slot.SlotActionType.QUICK_MOVE, mc.player);
        } else {
            Cpvp.swapInto(src, slot);
        }
        readyAt = System.currentTimeMillis() + delay.getValue();
    }



    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x35a3,0x0aaa,0xe400,0x7e7c}, new char[]{0x7e31,0x0ac5,0xe464,0x35c6}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8d18,0xa1a2,0xd238,0xd10b,0x9d14,0x43f8}, new char[]{0x8d59,0xa1ce,0x438f,0xd259,0xd172,0x9d67}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x899c,0xf399,0x7da5,0x4b47,0x2330,0xc53f,0x4876}, new char[]{0x237b,0xf3fc,0x480f,0x7dc7,0x89f5,0xc551,0x4b23}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8294,0x01c2,0x9cea,0xb517,0x3f22,0xc909,0x3946,0x7e38,0x73b5,0xbadf}, new char[]{0x73e7,0x3923,0xb571,0x9c83,0x7e54,0x82f8,0xc929,0xba94,0x3f47,0x01bb}).toString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7d67,0x7fb5,0x2ee5,0x0d2c,0x11f6,0x20f0,0xf814,0x7bb7,0x43c2,0xb63a,0xb065}, new char[]{0x7fe1,0x2e84,0x7d15,0x2097,0xf871,0x43b6,0x0d0c,0x11a5,0x7bdb,0xb655,0xb011}).toString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc58d,0xdc13,0xaea7,0x7154,0x32fd}, new char[]{0xaee3,0xdc76,0x3291,0xc5ec,0x712d}).toString();
        static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x839e,0x6074,0x7608,0x3fc9,0x4bac,0xafd0,0xa372,0x0dfe,0x2551,0x5920,0xc866}, new char[]{0x6037,0xc807,0x4bde,0x83ea,0xaff0,0x765a,0x5945,0x0d98,0x3fa0,0x253d,0xa31e}).toString();
        static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb2c9,0x78e0,0xb0da,0x7ee1,0xa115,0x8a92,0x0127,0x06d8,0x92f2,0xd24a,0x73f1,0xad3b,0x5876,0x678c,0x828c,0x1c8f,0xd232,0x7e7a,0xc1d0,0xcfb8,0x88e9,0xfe10,0x735c,0xb2f1,0x3bc0,0x39a9,0x300e,0xe8db,0x65bd,0x27e2,0x9c3a,0x246d,0xf100,0xd9ab,0xf6ef,0xa946,0xcdb8,0xa67d,0x456a,0xecb5,0xc844}, new char[]{0xece7,0x306b,0xa61b,0x7889,0x8afe,0x7e8d,0xd9d8,0xfe30,0x5817,0xf6cf,0x4502,0xd225,0x82f8,0x9290,0x2783,0x67fe,0x1caf,0xc837,0xb0b6,0xb2a6,0xcfcc,0xb2d1,0xe8ac,0x8880,0xcdcc,0x9c52,0x7e5a,0xa912,0x73bf,0x0173,0xd212,0x2400,0x65d4,0x7332,0x06bd,0xf163,0xad5a,0xa167,0x3bb4,0xc1a3,0x3987}).toString();
    }
}
