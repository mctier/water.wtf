package com.utilitymod.echo.features.impl.misc;

import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventTick;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.settings.impl.BoolSetting;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

/** While you mine, holds the fastest hotbar tool for that block, then switches back. */
public class AutoTool extends Feature {
    private final BoolSetting backSwitch = new BoolSetting(dev.potato.spud.api.ProtectedText.fromInts(new int[]{0x4D,0x5E,0x19,0xCF,0xA1,0x65,0x74,0xDD,0xC7,0x92,0x53}), true);
    private final BoolSetting saveTools = new BoolSetting(LT.L1, true);
    private int prevSlot = -1;

    public AutoTool() {
        super(new FeatureInfo(dev.potato.spud.api.ProtectedText.fromInts(new int[]{0x5F,0x5C,0x04,0xD4,0xE2,0x59,0x3B,0xF0,0xCA}), LT.L2, Category.UTILITY));
    }

    @EventSubscribe
    private void onTick(EventTick event) {
        if (isNull()) return;
        var inv = mc.player.getInventory();
        boolean mining = mc.currentScreen == null && mc.options.attackKey.isPressed() && mc.crosshairTarget != null
                && mc.crosshairTarget.getType() == HitResult.Type.BLOCK;
        if (!mining) {
            if (prevSlot >= 0 && backSwitch.getValue()) inv.setSelectedSlot(prevSlot);
            prevSlot = -1;
            return;
        }
        BlockState state = mc.world.getBlockState(((BlockHitResult) mc.crosshairTarget).getBlockPos());
        if (state.isAir()) return;
        int best = inv.getSelectedSlot();
        float bestSpeed = inv.getStack(best).getMiningSpeedMultiplier(state);
        for (int i = 0; i < 9; i++) {
            ItemStack s = inv.getStack(i);
            if (saveTools.getValue() && s.isDamageable() && s.getMaxDamage() - s.getDamage() < 10) continue;
            float sp = s.getMiningSpeedMultiplier(state);
            if (sp > bestSpeed + 0.01f) {
                bestSpeed = sp;
                best = i;
            }
        }
        if (best != inv.getSelectedSlot()) {
            if (prevSlot < 0) prevSlot = inv.getSelectedSlot();
            inv.setSelectedSlot(best);
        }
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x29e6,0x962a,0x516f,0x8f22,0xff8b,0x50c8,0x80c0,0x75bd,0x7e12,0x5c65,0xa5bd,0xb859,0xb231,0xa44a,0x6807,0xa5a0,0xb187,0x81fe,0x29fe}, new char[]{0xffd8,0x510e,0xa5d6,0x75d8,0x29c6,0xb1cb,0x2991,0xb246,0xa46a,0x81ba,0x965f,0x5c17,0x50a9,0x8f40,0xb830,0x686b,0x7e7b,0xa5c9,0x80b9}).toString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xfb0a,0x8410,0x28be,0x7373,0x9f48,0xfacc,0xcb73,0xe428,0x74f3,0xfeca,0x765b,0x2064,0x46b1,0x91e8,0xb6c1,0xfa0a,0x0b62,0x7437,0xd606,0x075b,0xaa23,0x0e24,0x168a,0x8268,0xff86,0x6eb7,0x199e,0x5e2c,0x6b3e,0x66a1,0xbc1f,0x42e5,0x35c7,0x7650,0xf2e4,0x2814,0x9531,0x1b1d,0x0f81,0x18de,0xc38e,0x7464,0x3e9f,0xdf6e,0xa3a3,0x7770,0x91b7,0x4b20}, new char[]{0x188d,0x91c0,0x28d7,0xc3fa,0x7407,0x428d,0x918d,0x0728,0x1b3d,0x9545,0xfa65,0x7670,0x2860,0x16e2,0x0e41,0x19be,0xfaae,0x820d,0xcb00,0x6ec3,0x74d3,0xe45c,0xffe9,0x3ef0,0x771c,0xf2c4,0xa3c5,0x46de,0x6b4c,0x9f68,0x8464,0x745f,0xfb6f,0x0fa1,0x4b42,0xdf02,0x35a8,0xfea9,0x66ca,0x767b,0x0b1b,0x731c,0xb6b4,0x2044,0xbc72,0xaa4a,0xd668,0x5e49}).toString();
    }
}
