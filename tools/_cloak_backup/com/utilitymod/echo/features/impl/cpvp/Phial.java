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
    private final ModeSetting mode = new ModeSetting("Mode", "Always", "Always", "Keybind");
    private final KeybindSetting key = new KeybindSetting("Refill Key", -1);
    private final BoolSetting healingOnly = new BoolSetting("Healing Only", true);
    private final RangeSetting delay = new RangeSetting("Delay", 60f, 110f, 0f, 1000f, 1f);

    private long readyAt;

    public Phial() {
        super(new FeatureInfo("Pot Refill", "Fills empty hotbar slots with splash potions.", Category.UTILITY));
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
        boolean go = mode.is("Always") ? mc.currentScreen instanceof InventoryScreen
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
}
