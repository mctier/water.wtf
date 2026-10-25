package com.utilitymod.echo.features.settings.impl;

import com.utilitymod.echo.utils.inventory.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

public class ItemPickerSetting extends RegistryPickerSetting<Item> {

    public ItemPickerSetting(CharSequence name) {
        super(name, () -> Registries.ITEM);
    }

    public ItemPickerSetting(CharSequence name, Predicate<Object> dependency) {
        super(name, () -> Registries.ITEM, dependency);
    }

    @Override
    public String getTypeId() { return LT.L1; }

    @Override
    public List<EntryGroup<Item>> getGroups() {
        return ItemGroups.getAllGroups();
    }

    @Override
    public Function<Item, String> getNameProvider() {
        return item -> new ItemStack(item).getName().getString();
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xaf24,0xe7ca,0x4772}, new char[]{0xaf4d,0xe7be,0x471f,0x9505}).plainString();
    }
}
