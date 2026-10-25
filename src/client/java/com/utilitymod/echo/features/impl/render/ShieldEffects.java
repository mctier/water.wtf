package com.utilitymod.echo.features.impl.render;

import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventPacketReceive;
import com.utilitymod.echo.event.impl.EventTick;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.settings.impl.BoolSetting;
import com.utilitymod.echo.render.CustomParticles;
import com.utilitymod.echo.render.CustomParticles.Shape;
import dev.koifih.client.ui.Theme;
import dev.koifih.client.util.Colors;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

/** A soft rotating ward in front of players while they block, and a shatter burst when a shield breaks. */
public class ShieldEffects extends Feature {
    private final BoolSetting others = new BoolSetting(LT.L1, true);
    private final BoolSetting shatter = new BoolSetting(LT.L2, true);
    private int tick;

    public ShieldEffects() {
        super(new FeatureInfo(new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd129,0x0b7c,0xb16b,0x37d2,0x9625,0x2271,0xeb82,0xfcf4,0x82e8,0xbc0c,0xa8a3,0x1c94,0x11f5,0x6542}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87}).toString(), new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4a0e,0xa1e1,0x58e4,0x655e,0x2614,0x8af4,0x379e,0x2adf,0x479c,0x82c4,0xb983,0x1857,0x9be8,0xf8d8,0x7228,0xac4f,0x0b34,0x1c9e,0x5082,0x76b1,0x9626,0x5f66,0xeb92,0x04b4,0xa8aa,0xa43f,0x93b3,0x3347,0xfcee,0x6d7d,0xb178,0xd12d,0xbc0d,0xf049,0xe73f,0x9e54,0xd93e,0x3ff5,0xd2b7,0xb515,0x8664,0x3b74,0x0cd1,0x0021,0xc154,0x7ec5,0xcdbe,0xc5d2,0x11b0,0x226a}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87,0x76d4,0xf025,0x4a6a,0xc5bb,0x5f08,0xd959,0x50ae,0x2aff,0xa44c,0x3f9d,0xb9e2,0x3333,0x8a80,0x04d1,0x9e26,0x1877,0x93c4,0x6d15,0xe75a,0x7eab,0xf8f8,0x7249,0xcd9e,0x47ef,0xc13c,0x588d,0xd2d2,0xac23,0x2670,0xa1c1,0x3b16,0xb567,0x0cb4,0x8605,0x004a,0x9b9b}).toString(), Category.RENDER));
    }

    private int color() {
        return Colors.lerp(Theme.ACCENT, 0xFFFFFFFF, 0.25f);
    }

    @EventSubscribe
    private void onTick(EventTick event) {
        if (isNull()) return;
        tick++;
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (!p.isBlocking() || (p != mc.player && !others.getValue())) continue;
            if (p == mc.player && mc.options.getPerspective().isFirstPerson()) continue;
            // ring of sparks on a disc in front of them, turning slowly
            Vec3d look = Vec3d.fromPolar(0, p.getBodyYaw()).normalize();
            Vec3d centre = p.getEntityPos().add(0, p.getHeight() * 0.55, 0).add(look.multiply(0.55));
            Vec3d side = look.crossProduct(new Vec3d(0, 1, 0)).normalize();
            for (int k = 0; k < 3; k++) {
                double a = tick * 0.35 + k * (Math.PI * 2 / 3);
                Vec3d o = side.multiply(Math.cos(a) * 0.55).add(0, Math.sin(a) * 0.55, 0);
                CustomParticles.spawn(Shape.GLOW, centre.x + o.x, centre.y + o.y, centre.z + o.z, 0, 0, 0, 0.12f, 0.35f, color(), 0f);
            }
        }
    }

    @EventSubscribe
    private void onPacket(EventPacketReceive event) {
        if (!shatter.getValue() || !(event.getPacket() instanceof PlaySoundS2CPacket p)
                || !p.getSound().matches(e -> e.equals(SoundEvents.ITEM_SHIELD_BREAK.value()))) return;
        double x = p.getX(), y = p.getY() + 1.1, z = p.getZ();
        mc.execute(() -> {
            CustomParticles.burst(Shape.RING, x, y, z, 3, 0.8, 0.7f, 0.45f, color(), 0f);
            CustomParticles.burst(Shape.STAR, x, y, z, 22, 3.2, 0.16f, 0.9f, color(), 3f);
            CustomParticles.burst(Shape.SPARKLE, x, y, z, 10, 2.0, 0.12f, 0.6f, 0xFFFFFFFF, 2f);
        });
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xfd27,0xe29c,0x0983,0x19db,0x6f56,0xa074,0x86df,0x5c0c,0x1ca3,0x7435,0xfdb2,0xf485,0x055a}, new char[]{0xa03b,0x7441,0xf4ed,0x053f,0xe2ee,0xfd92,0x868f,0x6f3a,0xfd46,0x09fa,0x19be,0x5c7e,0x1cd0}).toString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0496,0x1f1f,0x8ffc,0xef2f,0xc68f,0xec3d,0x79de,0x9dd8,0x080c,0x5ddc,0xa73d,0xcc45,0xc56b,0x21f4}, new char[]{0x8faf,0x219c,0xec5c,0x79aa,0xef5b,0x9dbd,0xcc37,0x04b6,0x0849,0xc50d,0xc6e9,0x5db9,0x1f7c,0xa749}).toString();
    }
}
