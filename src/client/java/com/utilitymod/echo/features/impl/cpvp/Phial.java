package com.utilitymod.echo.features.impl.cpvp;

import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventTick;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.settings.impl.BoolSetting;
import com.utilitymod.echo.features.settings.impl.KeybindSetting;
import com.utilitymod.echo.features.settings.impl.ModeSetting;
import com.utilitymod.echo.features.settings.impl.RangeSetting;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/**
 * Pot Refill: fills empty hotbar slots with splash potions from your inventory (healing only by default),
 * while the inventory is open (Always) or while Refill Key is held (Keybind), one potion per Delay.
 */
public class Phial extends Feature {
    private final ModeSetting mode = new ModeSetting(LT.L1, LT.L2, LT.L2, LT.L3);
    private final KeybindSetting key = new KeybindSetting(LT.L4, -1);
    private final BoolSetting healingOnly = new BoolSetting(LT.L5, true);
    private final RangeSetting delay = new RangeSetting(LT.L6, 60f, 110f, 0f, 1000f, 1f);

    private long readyAt;

    public Phial() {
        super(new FeatureInfo(LT.L7, LT.L8, Category.UTILITY));
    }

    private boolean wanted(ItemStack s) {
        if (!s.isOf(Items.SPLASH_POTION)) return false;
        if (!healingOnly.getValue()) return true;
        var contents = s.get(DataComponentTypes.POTION_CONTENTS);
        if (contents == null) return false;
        for (var eff : contents.getEffects())
            if (eff.getEffectType().equals(StatusEffects.INSTANT_HEALTH)) return true;
        return false;
    }

    @EventSubscribe
    @SuppressWarnings("unused")
    private void onTick(EventTick e) {
        if (isNull() || System.currentTimeMillis() < readyAt) return;
        boolean go = mode.is(LT.L2) ? mc.currentScreen instanceof InventoryScreen
                : com.utilitymod.echo.handlers.InputHandler.isBindDown(key.getKey());
        if (!go) return;
        var inv = mc.player.getInventory();
        int empty = -1;
        for (int i = 0; i < 9; i++) if (inv.getStack(i).isEmpty()) { empty = i; break; }
        if (empty < 0) return;
        for (int i = 9; i < 36; i++) {
            if (!wanted(inv.getStack(i))) continue;
            Cpvp.swapInto(i, empty);
            readyAt = Cpvp.next(delay);
            return;
        }
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5b76,0xa4ae,0xbd2c,0x7dc7}, new char[]{0xa4e3,0x5b19,0x7da3,0xbd49}).toString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x495f,0xc051,0x88e9,0x99e3,0x7834,0x70e3}, new char[]{0xc010,0x998f,0x7094,0x493e,0x784d,0x889a}).toString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd889,0x6db5,0x551d,0xf024,0x9db0,0x1bc7,0x088e}, new char[]{0x5556,0xf041,0xd8f0,0x6dd7,0x1bae,0x08e0,0x9dd4}).toString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc737,0x7ec6,0xc7a0,0x2af5,0xff7a,0xdbc4,0xc7bb,0x73c7,0x3596,0x8262}, new char[]{0xc7f2,0xff1f,0x7ea0,0xdbad,0xc7d7,0x820e,0x35b6,0xc77c,0x73a2,0x2a8c}).toString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2a21,0xd2a2,0x6a3f,0xd86b,0x8eea,0x5536,0x70d3,0x468d,0xdc7d,0x57c4,0x801b,0x0b0e}, new char[]{0x0b46,0x5553,0x70b2,0x2a4d,0xd802,0x8e84,0xd2c5,0x803b,0x46c2,0xdc13,0x57a8,0x6a46}).toString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x23cb,0xb5f9,0x3912,0xec7e,0x0509}, new char[]{0x3956,0xec1b,0x0565,0xb598,0x23b2}).toString();
        static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xde8b,0x6645,0x233c,0x8eed,0xd0a3,0xb77e,0x83bb,0x069b,0x2bd3,0x8353}, new char[]{0x6615,0xdee4,0xd0d7,0x8373,0x06c9,0x2bb6,0x235a,0x83d2,0xb712,0x8e81}).toString();
        static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4126,0x6e5f,0xf2bc,0xa2d4,0xad8d,0x3350,0x1088,0xbfd9,0x4cec,0x5819,0x9fe6,0xfc49,0x9051,0x2fb1,0x35ad,0x3b20,0x7e90,0xe6d5,0xfdc9,0xbb3f,0x923d,0xb6ce,0xcff5,0xef0c,0xfa58,0x455a,0x5043,0x11fd,0xd44b,0x63c2,0x106b,0x2d9e,0x35cb,0x0959,0xb17b,0xd542,0x0334,0x5ce2,0xb380,0x01b0,0xc1c4,0x1e4d,0x494b,0xad66,0x519c}, new char[]{0x091f,0x7ef9,0x333c,0x5c8e,0x35b8,0x4ccc,0x4143,0x2fdc,0xc1b4,0x6e2b,0x9f9f,0xbb1f,0xa2bc,0x01df,0x35d9,0xfdab,0x51fd,0xad14,0x5839,0xd438,0x1191,0x2df1,0x9025,0xb108,0xfa78,0xf2cb,0xd52b,0x0340,0x1e25,0x921d,0xbfaa,0x10f8,0x502f,0x453b,0x3b53,0xcf9d,0xb3a0,0xb6be,0x1004,0xe6a1,0x4922,0xef63,0x63ac,0xadfe,0xfc67}).toString();
    }
}
