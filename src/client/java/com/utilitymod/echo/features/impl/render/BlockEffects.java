package com.utilitymod.echo.features.impl.render;

import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventTick;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.settings.impl.BoolSetting;
import com.utilitymod.echo.render.CustomParticles;
import com.utilitymod.echo.render.CustomParticles.Shape;
import dev.koifih.client.ui.Theme;
import dev.koifih.client.util.Colors;
import net.minecraft.block.BlockState;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/** Sparks while you mine, a ring when you start hitting a block, and a burst in the block's colour when it breaks. */
public class BlockEffects extends Feature {
    private final BoolSetting mining = new BoolSetting(LT.L1, true);
    private final BoolSetting hit = new BoolSetting(LT.L2, true);
    private final BoolSetting breakBurst = new BoolSetting(LT.L3, true);

    private BlockPos mined;
    private BlockState minedState;
    private boolean wasBreaking;

    public BlockEffects() {
        super(new FeatureInfo(new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2277,0x9626,0x11f3,0xeb95,0x82cb,0x0b19,0xa8a7,0xbc0a,0x1c94,0xd138,0x37d5,0x6545,0xb12f}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536}).toString(), new com.utilitymod.echo.utils.EncryptedString(new char[]{0x478e,0x37dd,0xcdfb,0xf89a,0x11f9,0x6d7b,0x0cd7,0xd93e,0x1857,0x0b2f,0xf04c,0x1c85,0x5082,0xe73e,0x6553,0xd13f,0x7e8b,0x93a5,0x227e,0xeb8d,0xa1a3,0x828d,0x4a04,0xd2bc,0xbc0c,0x76b9,0xa8ad,0x2adf,0x0039,0xb508,0x9e41,0xc5d2,0x3347,0xb17b,0xb996,0xc157,0xac44,0x3ff4,0x3b7a,0x866e,0x8ae9,0x962b,0x5f66,0xa424,0x723b,0xfca7,0x58e4,0x2650,0x04bf}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87,0x76d4,0xf025,0x4a6a,0xc5bb,0x5f08,0xd959,0x50ae,0x2aff,0xa44c,0x3f9d,0xb9e2,0x3333,0x8a80,0x04d1,0x9e26,0x1877,0x93c4,0x6d15,0xe75a,0x7eab,0xf8f8,0x7249,0xcd9e,0x47ef,0xc13c,0x588d,0xd2d2,0xac23,0x2670,0xa1c1,0x3b16,0xb567,0x0cb4,0x8605,0x004a}).toString(), Category.RENDER));
    }

    @EventSubscribe
    private void onTick(EventTick event) {
        if (isNull() || mc.interactionManager == null) return;
        // the block we were mining turned to air -> break burst in its map colour
        if (mined != null && mc.world.getBlockState(mined).isAir()) {
            if (breakBurst.getValue() && minedState != null) {
                int rgb = minedState.getMapColor(mc.world, mined).color;
                int col = 0xFF000000 | (rgb == 0 ? (Theme.accentRgb() & 0xFFFFFF) : rgb);
                Vec3d c = Vec3d.ofCenter(mined);
                CustomParticles.burst(Shape.GLOW, c.x, c.y, c.z, 18, 2.2, 0.18f, 0.8f, col, 2.5f);
                CustomParticles.burst(Shape.SPARKLE, c.x, c.y, c.z, 8, 1.6, 0.12f, 0.6f, 0xFFFFFFFF, 1.5f);
            }
            mined = null;
        }
        boolean breaking = mc.interactionManager.isBreakingBlock();
        if (breaking && mc.crosshairTarget instanceof BlockHitResult bh && bh.getType() == HitResult.Type.BLOCK) {
            if (!wasBreaking && hit.getValue()) {
                Vec3d p = bh.getPos();
                CustomParticles.spawn(Shape.RING, p.x, p.y, p.z, 0, 0, 0, 0.5f, 0.35f, Colors.lerp(Theme.ACCENT, 0xFFFFFFFF, 0.2f), 0f);
            }
            mined = bh.getBlockPos();
            minedState = mc.world.getBlockState(mined);
            if (mining.getValue()) {
                Vec3d p = bh.getPos();
                var n = bh.getSide().getVector();
                var r = mc.world.random;
                CustomParticles.spawn(Shape.SPARKLE, p.x, p.y, p.z, n.getX() * 1.2 + (r.nextDouble() - 0.5),
                        n.getY() * 1.2 + r.nextDouble() * 0.6, n.getZ() * 1.2 + (r.nextDouble() - 0.5),
                        0.09f, 0.45f, Colors.lerp(Theme.ACCENT, 0xFFFFFFFF, 0.3f), 3f);
            }
        }
        wasBreaking = breaking;
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3401,0xe464,0x1fa7,0x1c7e,0xffe5,0xc1b9,0x9bf2,0xb480,0xe67c,0x903a,0x7681,0xbfb0,0xf664}, new char[]{0xffa8,0x9053,0xbfde,0x9b9b,0x76ef,0xe61b,0x1c5e,0xe437,0x3471,0xc1d8,0xf616,0x1fcc,0xb4f3}).toString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6c56,0x6af5,0x8b52,0x76d2,0x30f6,0x89fe,0xe770,0x8609}, new char[]{0x769a,0x8b3b,0x898a,0x6c76,0xe722,0x8660,0x3098,0x6a92}).toString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4fe5,0xa7a9,0x1588,0x95d6,0xdf8f,0x782d,0x8698,0x0c28,0xdb3b,0x669d,0x0135}, new char[]{0x15ca,0xdb49,0x7848,0x0154,0x66f6,0x4fc5,0x0c6a,0xdffa,0x95a4,0xa7da,0x86ec}).toString();
    }
}
