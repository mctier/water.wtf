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
    private final RangeSetting delay = new RangeSetting("Delay", 50f, 50f, 0f, 1000f, 1f);
    private final BoolSetting close = new BoolSetting("Close", true);
    private final KeybindSetting key = new KeybindSetting("Activate Key", -1);

    private long readyAt;

    public CartStealer() {
        super(new FeatureInfo("Cart Stealer", "Takes TNT minecarts out of an open shulker box.", Category.UTILITY));
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
}
