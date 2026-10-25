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
    private final ModeSetting mode = new ModeSetting("Mode", "Always", "Always", "Keybind");
    private final KeybindSetting key = new KeybindSetting("Refill Key", -1);
    private final IntSetting targetSlot = new IntSetting("Target Slot", 9, 1, 9);
    private final IntSetting delay = new IntSetting("Delay", 100, 0, 1000);

    private long readyAt;

    public CartRefill() {
        super(new FeatureInfo("Cart Refill", "Refills a hotbar slot with TNT minecarts.", Category.UTILITY));
    }

    @EventSubscribe
    @SuppressWarnings("unused")
    private void onTick(EventTick e) {
        if (isNull() || System.currentTimeMillis() < readyAt) return;
        boolean go = mode.is("Always") ? mc.currentScreen instanceof InventoryScreen
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

}
