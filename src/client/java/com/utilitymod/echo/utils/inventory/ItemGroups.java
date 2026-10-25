package com.utilitymod.echo.utils.inventory;

import com.utilitymod.echo.features.settings.impl.RegistryPickerSetting;
import com.utilitymod.echo.utils.strings.Concat;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.MaceItem;

import java.util.ArrayList;
import java.util.List;

public final class ItemGroups {

    private ItemGroups() {}

    private static RegistryPickerSetting.EntryGroup<Item> createTagGroup(CharSequence name, TagKey<Item> tag) {
        return new RegistryPickerSetting.EntryGroup<>(
                name,
                item -> {
                    ItemStack stack = new ItemStack(item);
                    return stack.isIn(tag);
                }
        );
    }

    private static List<RegistryPickerSetting.EntryGroup<Item>> createGroups() {
        List<RegistryPickerSetting.EntryGroup<Item>> groups = new ArrayList<>();
        groups.add(createTagGroup(Concat.of(LT.L1), ItemTags.SWORDS));
        groups.add(createTagGroup(Concat.of(LT.L2), ItemTags.AXES));
        groups.add(new RegistryPickerSetting.EntryGroup<>(
                Concat.of(LT.L3),
                item -> item instanceof MaceItem
        ));
        groups.add(new RegistryPickerSetting.EntryGroup<>(
                Concat.of(LT.L4),
                item -> item == Items.ELYTRA
        ));
        groups.add(new RegistryPickerSetting.EntryGroup<>(
                Concat.of(LT.L5),
                item -> item == Items.AIR
        ));
        groups.add(createTagGroup(Concat.of(LT.L6), ItemTags.HEAD_ARMOR));
        groups.add(createTagGroup(Concat.of(LT.L7), ItemTags.CHEST_ARMOR));
        groups.add(createTagGroup(Concat.of(LT.L8), ItemTags.LEG_ARMOR));
        groups.add(createTagGroup(Concat.of(LT.L9), ItemTags.FOOT_ARMOR));
        groups.add(createTagGroup(Concat.of(LT.L10), ItemTags.PICKAXES));
        groups.add(createTagGroup(Concat.of(LT.L11), ItemTags.SHOVELS));
        groups.add(createTagGroup(Concat.of(LT.L12), ItemTags.HOES));
        groups.add(createTagGroup(Concat.of(LT.L13), ItemTags.SPEARS));
        groups.add(createTagGroup(Concat.of(LT.L14), ItemTags.PLANKS));
        return groups;
    }

    private static final class Holder {
        private static final List<RegistryPickerSetting.EntryGroup<Item>> ALL_GROUPS = createGroups();
    }

    public static List<RegistryPickerSetting.EntryGroup<Item>> getAllGroups() {
        return new ArrayList<>(Holder.ALL_GROUPS);
    }

    public static RegistryPickerSetting.EntryGroup<Item> swords() {
        return Holder.ALL_GROUPS.get(0);
    }

    public static RegistryPickerSetting.EntryGroup<Item> axes() {
        return Holder.ALL_GROUPS.get(1);
    }

    public static RegistryPickerSetting.EntryGroup<Item> mace() {
        return Holder.ALL_GROUPS.get(2);
    }

    public static RegistryPickerSetting.EntryGroup<Item> fists() {
        return Holder.ALL_GROUPS.get(4);
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x34c0,0x0e40,0x2653,0x8b0b,0x7b36,0x5d49}, new char[]{0x5d1a,0x2624,0x7b59,0x0e32,0x34a4,0x8b78}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4c27,0x4320,0xe6e3,0x641c}, new char[]{0x4361,0x6464,0xe686,0x4c54}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb222,0x799a,0xec6f,0xc764}, new char[]{0xb26f,0xec0e,0x79f9,0xc701}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6d9f,0x9c52,0x85cb,0xfd34,0xded9,0xe143}, new char[]{0xe106,0x6df3,0x85b2,0xfd40,0xdeab,0x9c33}).plainString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5751,0x19b3,0xb0c3,0xb374,0xbfa2}, new char[]{0x19f5,0xb0aa,0xb307,0x5725,0xbfd1}).plainString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6a18,0x5b8e,0x7f40,0xda8a,0x2152,0x186f}, new char[]{0x211a,0x5beb,0x1803,0x6a75,0x7f25,0xdafe}).plainString();
        static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1397,0x11e1,0xf9ab,0x8cd7,0xd132,0x80d9,0xb1ef,0xd5f1,0x3c56,0xac0a}, new char[]{0xb1ac,0x1189,0xf9ce,0x80aa,0x8ca3,0xd142,0xac66,0x13f6,0x3c22,0xd594}).plainString();
        static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9949,0x03c3,0x746e,0x690f,0x0522,0xbedc,0x920a,0xcb66}, new char[]{0x7422,0x03a6,0x0545,0x926d,0xcb0f,0x9927,0xbebb,0x697c}).plainString();
        static final String L9 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xafb0,0x07c2,0xa99f,0xdf97,0x2516}, new char[]{0xdfd5,0xa9f0,0x07ad,0x2562,0xafc3}).plainString();
        static final String L10 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xdaa6,0x30bd,0x57c7,0xf4ac,0x4197,0x6b0c,0xeb84,0x97b7}, new char[]{0x5797,0x30d4,0x97d4,0x6b67,0xebe5,0xf4d4,0x41f2,0xdad5}).plainString();
        static final String L11 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9082,0x94b3,0x7d96,0xe665,0x9078,0xbb5c,0x4d04}, new char[]{0x90d1,0xbb34,0x94dc,0x7de0,0x4d61,0xe609,0x900b}).plainString();
        static final String L12 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0f72,0xdfad,0x7085,0xc0a1}, new char[]{0x0f3a,0xdfc2,0x70e0,0xc0d2}).plainString();
        static final String L13 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x144e,0xcfb2,0xc939,0x9adb,0x7a7e,0x3a05}, new char[]{0x9a88,0x143e,0x7a1b,0xcfd3,0x3a77,0xc94a}).plainString();
        static final String L14 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7c32,0x91b2,0x3c4b,0x2c5e,0x76b2,0xfe2c}, new char[]{0x3c1b,0x91de,0x76d3,0x7c5c,0xfe47,0x2c2d}).plainString();
    }
}
