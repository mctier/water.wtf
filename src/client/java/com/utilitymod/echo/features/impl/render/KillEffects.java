package com.utilitymod.echo.features.impl.render;

import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventRender3D;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.settings.impl.BoolSetting;
import com.utilitymod.echo.features.settings.impl.FloatSetting;
import com.utilitymod.echo.features.settings.impl.ModeSetting;
import com.utilitymod.echo.render.CustomParticles;
import com.utilitymod.echo.utils.combat.KillTracker;
import dev.koifih.client.ui.Theme;
import dev.koifih.client.util.Colors;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundEvents;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-side effect when something you hit dies. Default "Soul Wireframe": the victim's body
 * (head, torso, arms, legs - player model proportions) rises as a glowing accent wireframe,
 * slowly turning, swelling and fading, trailing sparkles.
 */
public class KillEffects extends Feature {
    private final ModeSetting effect = new ModeSetting(LT.L1, LT.L2,
            LT.L2, LT.L3, LT.L4, LT.L5, LT.L6, LT.L7);
    private final FloatSetting duration = new FloatSetting(LT.L8, 2.4f, 0.8f, 5f, 0.1f, o -> effect.is(LT.L2));
    private final FloatSetting rise = new FloatSetting(LT.L9, 1.6f, 0.3f, 4f, 0.1f, o -> effect.is(LT.L2));
    private final ModeSetting ghostStyle = new ModeSetting(LT.L10, LT.L11, o -> effect.is(LT.L2), LT.L11, LT.L12);
    private final BoolSetting sound = new BoolSetting(LT.L13, true);
    private final BoolSetting playersOnly = new BoolSetting(LT.L14, true);


    public KillEffects() {
        super(new FeatureInfo(new com.utilitymod.echo.utils.EncryptedString(new char[]{0xeb92,0x0b3a,0x1c97,0x9620,0x2274,0xd131,0x11e4,0x82cb,0xa8a2,0x379e,0xb14a,0xbc05}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1}).toString(), new com.utilitymod.echo.utils.EncryptedString(new char[]{0xdad8,0xa422,0xd93e,0x9bf3,0x226d,0x6d67,0x9e06,0x2a8b,0x4a4a,0xb508,0x58fa,0x655b,0x1c97,0x239c,0x7a55,0x261e,0xeb80,0xa8a4,0xc5d7,0xe0ff,0x8660,0xef57,0xbc05,0x7225,0xb178,0x0cd9,0x003e,0x2e77,0x3b65,0x66e9,0x93a5,0xb98c,0xf89b,0x9625,0xd129,0x3354,0x34f8,0x5472,0x04fe,0x379e,0xc933,0xf00a,0xf412,0xd2ba,0x8aa0,0xcdfb,0xe72e,0x1581,0xbd5f,0xc11c,0x7ec2,0x82df,0xa1e1,0x5f61,0x50c6,0x479c,0x11e2,0x3ff4,0x4fb7,0x1807,0xac46,0x4308,0x0b35,0x76f4,0xfce2,0xa9c0}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87,0x76d4,0xf025,0x4a6a,0xc5bb,0x5f08,0xd959,0x50ae,0x2aff,0xa44c,0x3f9d,0xb9e2,0x3333,0x8a80,0x04d1,0x9e26,0x1877,0x93c4,0x6d15,0xe75a,0x7eab,0xf8f8,0x7249,0xcd9e,0x47ef,0xc13c,0x588d,0xd2d2,0xac23,0x2670,0xa1c1,0x3b16,0xb567,0x0cb4,0x8605,0x004a,0x9b9b,0x15e8,0xef39,0x668e,0xe0df,0x7a2c,0xf47d,0x4fc2,0xc913,0x4360,0xdab1,0x5406,0x2e57,0xa9a4,0x23f5,0xbd3a,0x348b}).toString(), Category.RENDER));
        KillTracker.listen(this::onKill);
    }

    private int accent() {
        return Colors.lerp(Theme.ACCENT, 0xFFFFFFFF, 0.15f);
    }

    private void onKill(LivingEntity e) {
        if (!isEnabled() || mc.world == null) return;
        if (playersOnly.getValue() && !(e instanceof PlayerEntity)) return;
        double x = e.getX(), y = e.getY(), z = e.getZ();
        float cy = (float) (y + e.getHeight() / 2);
        switch (effect.getValue().toString()) {
            case "Soul Wireframe" -> {
                com.utilitymod.echo.render.Ghosts.spawn(x, y, z, e.getBodyYaw(), e.getHeight(), e.getWidth(), e instanceof PlayerEntity,
                        duration.getValue(), rise.getValue(), accent(),
                        ghostStyle.is(LT.L12) ? com.utilitymod.echo.render.Ghosts.Style.CHAMS : com.utilitymod.echo.render.Ghosts.Style.WIREFRAME);
                if (ghostStyle.is(LT.L12)) CustomParticles.burst(CustomParticles.Shape.GLOW, x, cy, z, 14, 1.0, 0.3f, 1.0f, accent(), -0.3f);
                if (sound.getValue()) mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 0.9f, 0.8f));
            }
            case "Lightning" -> {
                LightningEntity bolt = new LightningEntity(EntityType.LIGHTNING_BOLT, mc.world);
                bolt.refreshPositionAfterTeleport(x, y, z);
                bolt.setCosmetic(true);
                mc.world.addEntity(bolt);
                if (sound.getValue()) mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, 1f, 0.6f));
            }
            case "Hearts" -> CustomParticles.burst(CustomParticles.Shape.HEART, x, cy, z, 16, 1.6, 0.3f, 1.4f, 0xFFF472B6, -0.6f);
            case "Stars" -> CustomParticles.burst(CustomParticles.Shape.STAR, x, cy, z, 26, 2.4, 0.28f, 1.2f, accent(), 0.4f);
            case "Snow" -> CustomParticles.burst(CustomParticles.Shape.SNOW, x, cy, z, 24, 1.4, 0.3f, 2.0f, 0xFFE0F2FE, 0.6f);
            default -> CustomParticles.burst(CustomParticles.Shape.GLOW, x, cy, z, 40, 2.6, 0.32f, 1.0f, accent(), 0.2f);
        }
    }



    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x19cd,0xdbc0,0x891b,0x28d9,0x52f3,0xccda}, new char[]{0x895e,0xccbc,0x5295,0x28bc,0x19ae,0xdbb4}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x023c,0xfbf5,0x5eec,0xa19d,0x12da,0xb365,0xbc4d,0x88ee,0xceb2,0xd2b5,0x4443,0x232a,0x6667,0x5b9d}, new char[]{0x88bd,0xbc22,0x12af,0xfb99,0x6647,0xcee5,0xd2dc,0x5bef,0xb300,0x234c,0xa1ef,0x4422,0x5e81,0x0259}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8fb0,0x184d,0x70c5,0xbeee,0x334b,0x5bec,0x8ed1,0x41f1,0xdd69}, new char[]{0x1801,0x4198,0xbe89,0x8eb9,0xdd1d,0x70ab,0x3322,0x8fde,0x5b8b}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9fd9,0x930b,0x8393,0x5bbc,0x617f,0xb87d}, new char[]{0xb835,0x611a,0x5bdd,0x83e1,0x937f,0x9faa}).plainString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe534,0xd09b,0xb9c3,0x846f,0xc06b}, new char[]{0x843c,0xc01f,0xd0fa,0xe546,0xb9b0}).plainString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb57e,0xe849,0x7cd4,0xddc0}, new char[]{0xdd93,0xe827,0x7cbb,0xb509}).plainString();
        static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5001,0xa2e3,0x6d71,0xb176,0x56b9,0x9dd7,0xd585,0xc83e,0x5b37,0x226a}, new char[]{0x222d,0xb11a,0xd5ea,0xc849,0x9df7,0x56fb,0xa296,0x5073,0x5b44,0x6d05}).plainString();
        static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6947,0x975a,0x090f,0xf73f,0xe985,0x9121,0x05cc,0x8517,0x355c,0x4fb2,0x04c7,0xffe7}, new char[]{0x4ff6,0x8562,0x352e,0x6926,0x972e,0xe9ec,0x914e,0x0961,0x04e7,0xf717,0xff94,0x05e5}).toString();
        static final String L9 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x823b,0x0751,0x98a1,0x176c,0x4c9b,0x50de,0xbf67,0x5aeb,0x3f13,0xe861,0x6c0d}, new char[]{0xbf35,0x98c8,0x6c7e,0x1709,0xe841,0x5aa3,0x4cfe,0x0738,0x50b9,0x8253,0x3f67}).toString();
        static final String L10 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc0ce,0x205a,0x2823,0x3d48,0x07f5,0x0493,0xa196,0x5f1c,0x2284,0x2da2,0x3e64}, new char[]{0x2de5,0x3d20,0x2035,0x5f6f,0xa1e2,0x2803,0x3e37,0xc0ba,0x078c,0x04ff,0x22e1}).plainString();
        static final String L11 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6ee7,0x3703,0x905d,0x6285,0x0bef,0xa9d8,0x9754,0xef1d,0xf0c6}, new char[]{0x9703,0x0b86,0xef6f,0x6e82,0x3765,0xf0b4,0xa9b9,0x62e8,0x9038}).plainString();
        static final String L12 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xced7,0x2e67,0x9646,0x78f2,0x7bff}, new char[]{0x78b1,0x7b97,0x9627,0xceba,0x2e14}).plainString();
        static final String L13 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa3a4,0x4479,0xb2ae,0x53d7,0x3600}, new char[]{0xb2fd,0x53b8,0x440c,0xa3ca,0x3664}).plainString();
        static final String L14 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x77e5,0x8887,0xe9e3,0xaa6b,0xa0eb,0xe83a,0x3f60,0x52ef,0xebb4,0x3c0a,0x7161,0x2f2b}, new char[]{0xe86a,0x5283,0x2f4a,0xebcd,0x7104,0x7797,0xaa18,0x3f40,0x3c45,0x88e9,0xe98f,0xa092}).toString();
    }
}
