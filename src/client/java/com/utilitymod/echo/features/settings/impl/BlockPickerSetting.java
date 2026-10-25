package com.utilitymod.echo.features.settings.impl;

import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.function.Function;
import java.util.function.Predicate;

/** Pick any set of blocks (saved as block ids). Edited in the dropdown GUI's block picker screen. */
public class BlockPickerSetting extends RegistryPickerSetting<Block> {
    public BlockPickerSetting(CharSequence name, String... defaults) {
        super(name, () -> Registries.BLOCK);
        for (String id : defaults) setSelected(Identifier.of(id), true);
    }

    public BlockPickerSetting(CharSequence name, Predicate<Object> dependency) {
        super(name, () -> Registries.BLOCK, dependency);
    }

    public BlockPickerSetting(CharSequence name, Predicate<Object> dependency, String... defaults) {
        super(name, () -> Registries.BLOCK, dependency);
        for (String id : defaults) setSelected(Identifier.of(id), true);
    }

    @Override
    public String getTypeId() { return LT.L1; }

    @Override
    public Function<Block, String> getNameProvider() {
        return b -> b.getName().getString();
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x96de,0x9a8c,0xf46b}, new char[]{0xf409,0x96b2,0x9ae7,0x1807}).plainString();
    }
}
