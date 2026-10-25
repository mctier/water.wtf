package com.utilitymod.echo.features.impl.render;

import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventOnAttackEntity;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.settings.impl.IntSetting;
import com.utilitymod.echo.features.settings.impl.ModeSetting;
import dev.koifih.client.ui.Theme;
import net.minecraft.entity.Entity;
import net.minecraft.particle.ParticleTypes;

/** Extra client-side particles on every hit you land. */
public class HitParticles extends Feature {
    private final ModeSetting type = new ModeSetting(LT.L1, LT.L2, LT.L2, LT.L3, LT.L4, LT.L5, LT.L6, LT.L7, LT.L8);
    private final IntSetting amount = new IntSetting(LT.L9, 12, 1, 60);

    public HitParticles() {
        super(new FeatureInfo(new com.utilitymod.echo.utils.EncryptedString(new char[]{0x226c,0x9620,0x11fc,0xeb84,0x82d9,0x0b2e,0xa8a2,0xbc49,0x1c9b,0xd132,0x37ee,0x6545,0xb16e}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536}).toString(), LT.L10, Category.RENDER));
    }

    @EventSubscribe
    private void onAttack(EventOnAttackEntity.Post event) {
        Entity t = event.getTarget();
        if (t == null || mc.world == null || event.getPlayer() != mc.player) return;
        var r = mc.world.random;
        double cx = t.getX(), cy = t.getY() + t.getHeight() * 0.6, cz = t.getZ();
        String mode = type.getValue().toString();
        if (mode.equals(LT.L8)) {
            for (int i = 0; i < amount.getValue(); i++) {
                mc.world.addParticleClient(ParticleTypes.CRIT, cx + (r.nextDouble() - 0.5) * t.getWidth(), cy + (r.nextDouble() - 0.5) * 0.6,
                        cz + (r.nextDouble() - 0.5) * t.getWidth(), (r.nextDouble() - 0.5) * 0.4, r.nextDouble() * 0.3, (r.nextDouble() - 0.5) * 0.4);
            }
            return;
        }
        com.utilitymod.echo.render.CustomParticles.Shape shape = switch (mode) {
            case "Glow" -> com.utilitymod.echo.render.CustomParticles.Shape.GLOW;
            case "Sparkle" -> com.utilitymod.echo.render.CustomParticles.Shape.SPARKLE;
            case "Heart" -> com.utilitymod.echo.render.CustomParticles.Shape.HEART;
            case "Snow" -> com.utilitymod.echo.render.CustomParticles.Shape.SNOW;
            case "Ring" -> com.utilitymod.echo.render.CustomParticles.Shape.RING;
            default -> com.utilitymod.echo.render.CustomParticles.Shape.STAR;
        };
        int color = mode.equals(LT.L5) ? 0xFFF472B6 : dev.koifih.client.util.Colors.lerp(Theme.ACCENT, 0xFFFFFFFF, 0.15f);
        com.utilitymod.echo.render.CustomParticles.burst(shape, cx, cy, cz, amount.getValue(), 2.2, 0.16f, 0.9f, color, 1.4f);
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7bca,0xa907,0x151e,0x366d,0xdd07,0x5b65,0xd73b,0xd7b9}, new char[]{0xd76b,0x5b04,0x361f,0xdd73,0x1577,0xa964,0xd7d5,0x7baf}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7b42,0x239d,0x0125,0xf262}, new char[]{0x23ce,0xf216,0x7b23,0x0157}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xda2e,0xda51,0xd9e6,0xe336}, new char[]{0xd9a1,0xda42,0xda3e,0xe341}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xaee7,0x9e1c,0xd8f7,0x0691,0xfa11,0x9a98,0xfc5f}, new char[]{0xfa42,0x06e1,0xd896,0x9e6e,0x9af3,0xae8b,0xfc3a}).plainString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7de4,0xcf3c,0xc65e,0xf788,0xec81}, new char[]{0xc616,0xece4,0xcf5d,0x7d96,0xf7fc}).plainString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x17a2,0x461e,0x4beb,0x9f50}, new char[]{0x9f03,0x4670,0x4b84,0x17d5}).plainString();
        static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xfc17,0x2608,0x3577,0xf598}, new char[]{0xfc45,0xf5f1,0x2666,0x3510}).plainString();
        static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1638,0x9fc8,0x888b,0x4a0a,0xff1a,0xea78,0xc1e6,0xc482,0x4bd9,0x1559,0xcea2,0xa4b3}, new char[]{0x9f9e,0xc4e3,0xff74,0xc18f,0x4bb5,0x1654,0x4a6b,0xce82,0xa4f0,0x88f9,0xea11,0x152d}).plainString();
        static final String L9 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8a1b,0x9793,0x34ec,0xc0b4,0x353e,0x3056}, new char[]{0x8a5a,0x3481,0x97fc,0x3023,0x3550,0xc0c0}).plainString();
        static final String L10 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xda61,0x3494,0x5fa3,0x9f89,0x50eb,0x7e21,0x3e12,0xe8fe,0xe062,0xf448,0x821a,0x30a6,0x4bb7,0x949a,0x0b8e,0xde59,0x2b9b,0xc736,0xb111,0x3b00,0x5079,0xc8b9,0x4d6d,0x4a2a,0x1f73,0x5fbf,0x4ed9,0xca55,0x92d8,0xeb1e,0xbe74,0xa871,0x1ed0,0xc956,0xc96c,0x3d06,0xcd8e,0x2a60,0x26ef,0x3135,0x5036,0x268d,0xf3bf,0xdc5a,0xa8ae,0xfc4e,0x71b2,0xa509}, new char[]{0x7e64,0x71ca,0x5fcb,0x26ff,0x94fb,0x26cf,0x509b,0x4d0c,0x8268,0xe016,0xa560,0x3156,0xc900,0x2a05,0x9ffa,0x5059,0xa8c1,0x0be0,0x3086,0x5fda,0x34fb,0x2bee,0xf3cd,0xfc6e,0x505e,0xf421,0xe88a,0x4eaa,0xde79,0xbe5c,0x3b6f,0x92b6,0xcde2,0xeb67,0x3d26,0x4a53,0xa81e,0xb164,0x3e32,0x1f00,0xca30,0xc933,0xda41,0xdc2e,0xc8d1,0x4bd2,0x1ebd,0xc71f}).toString();
    }
}
