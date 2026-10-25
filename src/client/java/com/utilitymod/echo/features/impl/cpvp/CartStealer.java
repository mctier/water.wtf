package com.utilitymod.echo.features.impl.cpvp;

import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventTick;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.settings.impl.BoolSetting;
import com.utilitymod.echo.features.settings.impl.KeybindSetting;
import com.utilitymod.echo.features.settings.impl.RangeSetting;
import com.utilitymod.echo.handlers.InputHandler;
import net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

/**
 * OpenPrestige Cart Stealer: while a shulker box is open (and Activate Key is held, if bound), shift-clicks
 * every TNT minecart into your inventory with a random delay between pulls, then closes the box.
 */
public class CartStealer extends Feature {
    private final RangeSetting delay = new RangeSetting(LT.L1, 50f, 50f, 0f, 1000f, 1f);
    private final BoolSetting close = new BoolSetting(LT.L2, true);
    private final KeybindSetting key = new KeybindSetting(LT.L3, -1);

    private long readyAt;

    public CartStealer() {
        super(new FeatureInfo(LT.L4, LT.L5, Category.UTILITY));
    }

    @EventSubscribe
    @SuppressWarnings("unused")
    private void onTick(EventTick e) {
        if (isNull() || !(mc.currentScreen instanceof ShulkerBoxScreen screen)) return;
        int bind = key.getKey();
        if (bind != -1 && bind != 0 && !InputHandler.isBindDown(bind)) return;
        if (System.currentTimeMillis() < readyAt) return;
        var handler = screen.getScreenHandler();
        // container slots come first (27 for a shulker box)
        for (int i = 0; i < 27 && i < handler.slots.size(); i++) {
            if (!handler.slots.get(i).getStack().isOf(Items.TNT_MINECART)) continue;
            mc.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
            readyAt = Cpvp.next(delay);
            return;
        }
        if (close.getValue()) mc.player.closeHandledScreen();
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xad8c,0x0b18,0x1066,0x6742,0xa761}, new char[]{0x1022,0x0b7d,0x672e,0xaded,0xa718}).toString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf925,0x2182,0x744b,0x1f44,0xdcfc}, new char[]{0x1f07,0x7427,0x21ed,0xdc8f,0xf940}).toString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb3d8,0xb13b,0x5410,0x79c1,0xc5e7,0xff3d,0x1783,0xa408,0xeb42,0xbe48,0xb14e,0x3229}, new char[]{0xc5a6,0xff5e,0xeb36,0xb152,0x79b7,0x17e2,0xb13a,0x324c,0xbe68,0x545b,0xb3bd,0xa471}).toString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1816,0x7a11,0x79d8,0xe774,0x83b4,0x3f6b,0xe76d,0xa3a0,0xe07e,0x698f,0xa1ac,0x3d3c}, new char[]{0x69cc,0x1877,0xa3d2,0x3d48,0xe74d,0xe727,0x83c0,0xa1c9,0xe01f,0x79b4,0x7a74,0x3f19}).toString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xfa5e,0x80cb,0xf7b2,0x70f5,0x9f8e,0x57f4,0x8d4c,0x0f47,0x8d76,0xd0fc,0x91cb,0xc921,0x3479,0x15c5,0xcb77,0x0ce8,0x62ef,0xec01,0xf71d,0x932c,0xb0f4,0x693e,0xd37a,0x8370,0xa13b,0x5a2a,0xd24e,0x6997,0x8457,0x57cc,0xf1f6,0xba6c,0x17d2,0xb16a,0x7b5a,0xe57c,0x005f,0x0248,0x06b3,0xf81a,0x53c1,0xbc7b,0x413e,0xcac4,0x93b5,0x70af,0x6b34}, new char[]{0xf7e6,0x934d,0x8d1d,0x53a4,0xec72,0x17f2,0xb0a0,0x8d02,0x57a0,0x8350,0xfa33,0xd095,0x0031,0x0f22,0x15a6,0xcb16,0x023a,0x69e3,0x80b8,0x7b7a,0xf772,0xba19,0x70db,0x70d5,0x4151,0xa15d,0xd35a,0xbc1a,0xe512,0xc901,0xb105,0x06c3,0x93d0,0xf198,0x57ec,0x629c,0xf872,0x0c9d,0x9fe2,0x91a0,0xcaa1,0x5a58,0x6b14,0x695c,0x3416,0x842f,0xd260}).toString();
    }
}
