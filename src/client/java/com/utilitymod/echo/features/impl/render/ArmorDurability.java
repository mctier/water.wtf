package com.utilitymod.echo.features.impl.render;

import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventRender2DGui;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.settings.impl.BoolSetting;
import com.utilitymod.echo.features.settings.impl.IntSetting;
import dev.koifih.client.render.Draw;
import dev.koifih.client.render.Text;
import dev.koifih.client.util.Colors;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.EntityHitResult;

/** HUD of the armour (and held item) plus durability of the opponent you are looking at or nearest to. */
public class ArmorDurability extends Feature {
    private final IntSetting range = new IntSetting(LT.L1, 24, 4, 64, LT.L2);
    private final BoolSetting onlyLookedAt = new BoolSetting(LT.L3, false);
    private final BoolSetting showHeld = new BoolSetting(LT.L4, true);
    private final BoolSetting showName = new BoolSetting(LT.L5, true);
    private final IntSetting posY = new IntSetting(LT.L6, 34, 2, 200, LT.L7);

    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    public ArmorDurability() {
        super(new FeatureInfo(new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1c80,0x9622,0xbc06,0x11f2,0x76a0,0x0b18,0xeb88,0x37cc,0xf05c,0xd13b,0x82d8,0x2275,0xfcee,0xa8b9,0xb12f,0x655a}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87,0x76d4,0xf025}).toString(), new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf057,0x0033,0xd2a2,0x76a1,0xef19,0x8ae9,0xa1af,0x7ecd,0x58e2,0xac53,0xf8d8,0xc11c,0x9e4f,0xe0be,0x3351,0x8625,0xe735,0xd93d,0xc5da,0x655b,0x1803,0x0cc0,0x478a,0xa8a3,0xeb93,0xfce8,0x7a4f,0x9bf4,0x4a4a,0x261f,0xcdf6,0x9663,0x3b73,0x5f66,0xbc1e,0xb12f,0x6d35,0xb509,0x11f1,0x37cd,0x66e8,0xd129,0x93bd,0xb983,0xa439,0x2277,0x2a9b,0x04bd,0x3fef,0x82c5,0x1c97,0x0b28,0x723d,0x508e,0x159d,0xf418}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87,0x76d4,0xf025,0x4a6a,0xc5bb,0x5f08,0xd959,0x50ae,0x2aff,0xa44c,0x3f9d,0xb9e2,0x3333,0x8a80,0x04d1,0x9e26,0x1877,0x93c4,0x6d15,0xe75a,0x7eab,0xf8f8,0x7249,0xcd9e,0x47ef,0xc13c,0x588d,0xd2d2,0xac23,0x2670,0xa1c1,0x3b16,0xb567,0x0cb4,0x8605,0x004a,0x9b9b,0x15e8,0xef39,0x668e,0xe0df,0x7a2c,0xf47d}).toString(), Category.RENDER));
    }

    @EventSubscribe
    private void onRender(EventRender2DGui event) {
        if (isNull() || mc.options.hudHidden) return;
        LivingEntity t = pickTarget();
        if (t == null) return;
        DrawContext ctx = event.getContext();

        java.util.List<ItemStack> items = new java.util.ArrayList<>();
        if (showHeld.getValue()) { ItemStack main = t.getMainHandStack(); if (!main.isEmpty()) items.add(main); }
        for (EquipmentSlot s : ARMOR) { ItemStack p = t.getEquippedStack(s); if (!p.isEmpty()) items.add(p); }
        if (items.isEmpty()) return;

        int cell = 20, n = items.size();
        int totalW = n * cell;
        int sw = ctx.getScaledWindowWidth();
        int x0 = (sw - totalW) / 2;
        int y = posY.getValue();

        Draw.rect(ctx, x0 - 6, y - 4, totalW + 12, 30, 7, 0xB0101216);
        if (showName.getValue()) {
            String name = t.getName().getString();
            Text.drawCenteredX(ctx, name, sw / 2f, y - 13f, 7f, 0xFFE9EEF5);
        }
        for (int i = 0; i < n; i++) {
            ItemStack stack = items.get(i);
            int ix = x0 + i * cell + 2;
            ctx.drawItem(stack, ix, y);
            ctx.drawStackOverlay(mc.textRenderer, stack, ix, y);
            if (stack.isDamageable()) {
                int left = stack.getMaxDamage() - stack.getDamage();
                float frac = left / (float) Math.max(1, stack.getMaxDamage());
                int col = frac > 0.5f ? 0xFF7FD9A8 : frac > 0.25f ? 0xFFFFC55C : 0xFFF08A8A;
                Text.drawCenteredX(ctx, String.valueOf(left), ix + 8f, y + 18f, 6f, col);
            }
        }
    }

    private LivingEntity pickTarget() {
        double r2 = (double) range.getValue() * range.getValue();
        if (mc.crosshairTarget instanceof EntityHitResult hit && hit.getEntity() instanceof PlayerEntity p
                && p != mc.player && p.isAlive() && mc.player.squaredDistanceTo(p) <= r2) return p;
        if (onlyLookedAt.getValue()) return null;
        PlayerEntity best = null; double bestD = r2;
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player || !p.isAlive()) continue;
            double d = mc.player.squaredDistanceTo(p);
            if (d <= bestD) { bestD = d; best = p; }
        }
        return best;
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4677,0xafe6,0x4e5e,0x041f,0x9c9b}, new char[]{0x4e0c,0x4616,0xaf88,0x9cfc,0x047a}).toString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7b27,0x4dce,0x13bb,0x7077,0x688f,0xb6f1,0x0f81}, new char[]{0x4dee,0x7b45,0xb69d,0x13d4,0x68ec,0x701c,0x0ff2}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc24c,0x83b5,0x6820,0x5bba,0xf65f,0x818e,0x6dad,0x856e,0xe887,0x9660,0x5d30,0x1b82,0x9502,0x9756,0xd043}, new char[]{0xd00c,0x5d5e,0x1bee,0xc235,0xe8a7,0x8539,0x81e6,0x9733,0x6dc3,0x9640,0xf61e,0x83dc,0x684d,0x5bdf,0x9566}).toString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe337,0x8db1,0x9505,0x9fea,0x5a1a,0xc025,0xc7c9,0x53dd,0x649a,0x26b8,0x0969,0xc30b,0xd903,0xd230}, new char[]{0xc076,0x64f2,0x5a75,0x9572,0x2698,0x0921,0x8dd4,0xc367,0xc7ad,0x53fd,0xe37e,0xd244,0xd966,0x9f87}).toString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3639,0xe349,0x5cf3,0x16e9,0x7dc8,0x35ee,0x0db6,0x41b9,0x5e36}, new char[]{0x0de5,0x5c9b,0x7da7,0x5e41,0xe369,0x16a7,0x358f,0x3654,0x41dc}).toString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5819,0x0a35,0x57fc,0xbe5b,0xd95a,0x7d3d,0xf802,0x7ef5}, new char[]{0xf85b,0x7d1d,0x5856,0x579a,0x7e93,0xd929,0xbe3e,0x0a41}).toString();
        static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x73eb,0x0d70,0x8467}, new char[]{0x8447,0x0d00,0x7393,0xbfe8}).plainString();
    }
}
