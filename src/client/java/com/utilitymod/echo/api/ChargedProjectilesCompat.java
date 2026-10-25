package com.utilitymod.echo.api;

import net.minecraft.component.type.ChargedProjectilesComponent;
import net.minecraft.item.ItemStack;

import java.util.List;

/** echo api.ChargedProjectilesCompat (1.21.11 branch). */
public final class ChargedProjectilesCompat {
    private ChargedProjectilesCompat() {}

    public static List<ItemStack> itemCopies(ChargedProjectilesComponent chargedProjectiles) {
        return chargedProjectiles == null ? List.of() : chargedProjectiles.getProjectiles();
    }
}
