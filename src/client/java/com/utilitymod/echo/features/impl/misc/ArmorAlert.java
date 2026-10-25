package com.utilitymod.echo.features.impl.misc;

import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventTick;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.impl.render.Notifications;
import com.utilitymod.echo.features.settings.impl.BoolSetting;
import com.utilitymod.echo.features.settings.impl.IntSetting;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvents;

import java.util.HashSet;
import java.util.Set;

/** Warns once when a piece of armour (or your held tool) gets low on durability. */
public class ArmorAlert extends Feature {
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND};
    private final IntSetting percent = new IntSetting(LT.L1, 15, 1, 50);
    private final BoolSetting heldItem = new BoolSetting(LT.L2, true);
    private final Set<String> warned = new HashSet<>();
    private int cooldown;

    public ArmorAlert() {
        super(new FeatureInfo(new com.utilitymod.echo.utils.EncryptedString(new char[]{0x11e4,0xa8b9,0xbc06,0x2275,0x1c97,0x82c1,0x0b1d,0xd13b,0xb12f,0x9631,0x37cc}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190}).toString(), new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5f65,0x11b0,0x58e6,0xa8aa,0x93b0,0xd936,0xeb98,0x2a8d,0x226a,0x6d35,0xf005,0xbc07,0x82c5,0xcdec,0x76a6,0x7ec4,0xf8d8,0x6559,0x478a,0xb12f,0xb991,0xc5c9,0x9e49,0x04b3,0xd12d,0x1802,0x3ff4,0xc15d,0xa46c,0xe72e,0x50db,0xfcf2,0x722b,0x1c97,0x37cd,0x4a0b,0x3313,0x8ae1,0x962d,0x0b2b}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87,0x76d4,0xf025,0x4a6a,0xc5bb,0x5f08,0xd959,0x50ae,0x2aff,0xa44c,0x3f9d,0xb9e2,0x3333,0x8a80,0x04d1,0x9e26,0x1877,0x93c4,0x6d15,0xe75a,0x7eab,0xf8f8,0x7249,0xcd9e,0x47ef,0xc13c,0x588d}).toString(), Category.UTILITY));
    }

    @EventSubscribe
    private void onTick(EventTick event) {
        if (isNull() || --cooldown > 0) return;
        cooldown = 20;
        for (EquipmentSlot slot : SLOTS) {
            if (slot == EquipmentSlot.MAINHAND && !heldItem.getValue()) continue;
            ItemStack s = mc.player.getEquippedStack(slot);
            if (s.isEmpty() || !s.isDamageable()) continue;
            String key = slot.name() + s.getItem();
            int left = s.getMaxDamage() - s.getDamage();
            if (left * 100f / s.getMaxDamage() <= percent.getValue()) {
                if (warned.add(key)) {
                    Notifications.push(LT.L3, s.getName().getString() + LT.L4 + left + LT.L5, Notifications.Type.DISABLED);
                    mc.player.playSound(SoundEvents.BLOCK_ANVIL_LAND, 0.4f, 1.6f);
                }
            } else {
                warned.remove(key);
            }
        }
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7e7a,0x9c3c,0xcdbe,0x0153,0xa953,0xe8fb,0xd237,0x88e1,0x73cb}, new char[]{0xe8ac,0x8880,0xcdcc,0x9c52,0x7e5a,0xa912,0x73bf,0x0173,0xd212}).toString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x06d9,0x2448,0xad13,0x735e,0x0b12,0xc1ce,0x65b1,0x1076,0x39a7,0xf143,0xa113,0x6741,0x3bd1}, new char[]{0x2400,0x65d4,0x7332,0x06bd,0xf163,0xad5a,0xa167,0x3bb4,0xc1a3,0x3987,0x1022,0x0b7d,0x672e}).toString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf901,0xadac,0xb126,0xc5ca,0xa76a,0x7448,0x219f,0xeb44,0x1f6a,0xff3b,0xdcaf}, new char[]{0xaded,0xa718,0x1f07,0x7427,0x21ed,0xdc8f,0xf940,0xc5a6,0xff5e,0xeb36,0xb152}).toString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb14e,0x326c,0x7997,0x1783}, new char[]{0x79b7,0x17e2,0xb13a,0x324c}).plainString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x69ad,0xa3bb,0x3d24,0x1815,0xe724,0xb3c8,0x543f,0x83b9,0xbe48,0xa403,0xe753}, new char[]{0xbe68,0x545b,0xb3bd,0xa471,0x69cc,0x1877,0xa3d2,0x3d48,0xe74d,0xe727,0x83c0}).plainString();
    }
}
