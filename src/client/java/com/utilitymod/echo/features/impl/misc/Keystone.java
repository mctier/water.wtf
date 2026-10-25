package com.utilitymod.echo.features.impl.misc;

import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventTick;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.settings.impl.IntSetting;
import com.utilitymod.echo.utils.EncryptedString;
import com.utilitymod.echo.utils.math.TimerUtils;

import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

/**
 * Chest Totem — sheet law: the moment your totem pops, the module opens the
 * player inventory, takes a totem out of it and drops it into the totem slot (the
 * offhand), then closes the screen. The swap rides the vanilla offhand-swap button (40)
 * while the inventory is open, so the click is the same one your hand could send —
 * nothing packet-shaped the server hasn't seen a human do.
 */
public class Keystone extends Feature {

    // -- Label carriers (generated codec-faithful — round-trip verified in harness) --
    private static final EncryptedString K_NAME = new EncryptedString(new char[]{0x0b0e, 0x1c95, 0x82c4, 0x37d3, 0xbc0c, 0xa8a4, 0xb12f, 0x226c, 0xd12e}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2});
    private static final EncryptedString K_DESCRIPTION = new EncryptedString(new char[]{0xa1a0, 0xe728, 0xe0af, 0xf043, 0xd2bd, 0x15c8, 0x47cf, 0xa439, 0xfcf4, 0x9be2, 0x1819, 0xc5d4, 0x1c86, 0x7228, 0xc14f, 0x93b0, 0x828d, 0xbc00, 0x0cc0, 0x227e, 0x76f4, 0x866d, 0x50d7, 0xef49, 0xeb84, 0x8aee, 0x655b, 0xd128, 0x9e43, 0x002f, 0xf8d8, 0x2a90, 0x04a7, 0xb9c2, 0x5f65, 0x962c, 0x3b65, 0x58e2, 0xb163, 0x6d7a, 0x37d2, 0xa8ae, 0x4a18, 0xd979, 0xcded, 0x3fef, 0xac4d, 0x0b2f, 0x11e4, 0x7ed2, 0x335a, 0x66e1, 0xb547, 0x2650}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643, 0x1190, 0xebe1, 0x6536, 0xfc87, 0x76d4, 0xf025, 0x4a6a, 0xc5bb, 0x5f08, 0xd959, 0x50ae, 0x2aff, 0xa44c, 0x3f9d, 0xb9e2, 0x3333, 0x8a80, 0x04d1, 0x9e26, 0x1877, 0x93c4, 0x6d15, 0xe75a, 0x7eab, 0xf8f8, 0x7249, 0xcd9e, 0x47ef, 0xc13c, 0x588d, 0xd2d2, 0xac23, 0x2670, 0xa1c1, 0x3b16, 0xb567, 0x0cb4, 0x8605, 0x004a, 0x9b9b, 0x15e8, 0xef39, 0x668e, 0xe0df});
    private static final EncryptedString K_DELAY = new EncryptedString(new char[]{0x2274, 0xd13e, 0x37c7, 0xa8ae, 0xbc08}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be});
    private static final EncryptedString K_MS = new EncryptedString(new char[]{0xa8b8, 0xd117}, new char[]{0xd17a, 0xa8cb});

    private final IntSetting delay = new IntSetting(K_DELAY, 50, 0, 1000, K_MS);

    private final TimerUtils equipTimer = new TimerUtils();
    private boolean openedByUs;

    public Keystone() {
        super(new FeatureInfo(K_NAME, K_DESCRIPTION, Category.UTILITY));
    }

    @Override
    public void onDisable() {
        if (openedByUs && mc.player != null) mc.setScreen(null);
        openedByUs = false;
        super.onDisable();
    }

    @EventSubscribe
    private void onTick(EventTick event) {
        if (!isEnabled() || isNull()) return;
        if (mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
            if (openedByUs) {
                mc.setScreen(null);
                openedByUs = false;
            }
            return;
        }
        // other containers use different slot ids — only ride the player inventory
        if (mc.currentScreen != null && !(mc.currentScreen instanceof InventoryScreen)) return;
        if (!equipTimer.hasReached(delay.getValue())) return;

        if (mc.currentScreen == null) {
            mc.setScreen(new InventoryScreen(mc.player));
            openedByUs = true;
            equipTimer.reset();
            return;
        }
        var inv = mc.player.getInventory();
        for (int i = 0; i < 36; i++) {
            if (!inv.getStack(i).isOf(Items.TOTEM_OF_UNDYING)) continue;
            int slot = i < 9 ? 36 + i : i; // inventory index -> player screen slot
            // SWAP with button 40 = swap with offhand
            mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, slot, 40, SlotActionType.SWAP, mc.player);
            equipTimer.reset();
            return;
        }
        // no totem anywhere: close the screen we opened, retry after the delay window
        if (openedByUs) {
            mc.setScreen(null);
            openedByUs = false;
        }
        equipTimer.reset();
    }
}
