package com.utilitymod.echo.features.impl.movement;

import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.settings.impl.BoolSetting;
import com.utilitymod.echo.features.settings.impl.FloatSetting;
import com.utilitymod.echo.features.settings.impl.ModeSetting;
import com.utilitymod.echo.utils.ChatUtils;
import com.utilitymod.echo.utils.strings.Concat;

// @see MixinPlayer.java
public class KeepSprintModule extends Feature {

    public KeepSprintModule() {
        super(new FeatureInfo(
                Concat.of(new com.utilitymod.echo.utils.EncryptedString(new char[]{0x11e4,0xa8ae,0xbc19,0x227d,0x1c9b,0x82df,0x0b2c,0xd131,0xb15c,0x962d,0x379e}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190}).toString()),
                Concat.of(LT.L1),
                Category.MOVEMENT
        ));
    }

    public final FloatSetting speedKeptVelocity = new FloatSetting(Concat.of(LT.L2), 0.6f, 0.6f, 1.0f, 0.1f);
    private final BoolSetting shouldSprint = new BoolSetting(Concat.of(LT.L3), true);
    // In what state sprint should be modified
    private final ModeSetting sprintCondition = new ModeSetting(Concat.of(LT.L4), Concat.of(LT.L5), Concat.of(LT.L6), Concat.of(LT.L7));

    public boolean dontModifySprint() {
        if (!shouldSprint.getValue()) return false;
        final boolean onGround = mc.player.isOnGround();
        if (sprintCondition.is(Concat.of(LT.L6))) return !onGround;
        else if (sprintCondition.is(Concat.of(LT.L7))) return onGround;
        else return sprintCondition.is(Concat.of(LT.L5));
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4df5,0x027c,0x72db,0xa90a,0x6131,0x9ec2,0x3908,0xc5fe,0x0a56,0xfe24,0xdff7,0x9488,0xddcb,0xea25,0xa3c1,0xbc3d,0x2f89,0x1da4,0x88fa}, new char[]{0x0237,0xa3a4,0x72be,0x94f8,0x4d86,0x2fa9,0xc587,0x1dcb,0x6144,0x0a76,0xea56,0xddbb,0xfe56,0xbc54,0x3966,0xa97e,0x9eab,0x8894,0xdf90}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9a29,0xad6d,0x4a43,0xe7f2,0x8228,0x84c3,0x0f43,0x8973,0xcefb,0xfaf7,0xbaec,0x151e,0xc12d,0x8fb0,0xe2ae,0xbffc,0x3fba,0x4e81,0x8f57}, new char[]{0x4a10,0x9a59,0x8f32,0x8fd5,0x8917,0x4ea1,0x3ff1,0xe2cb,0xc15d,0x0f37,0xcedb,0xfaa1,0xba89,0xbf90,0xad02,0x824b,0xe79b,0x84b7,0x1567}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa429,0x7e3f,0x2473,0x2ae7,0xd4d9,0x7b2f,0xbf34,0x2cee,0x4c57,0x245c,0x6163,0x6061,0x6893}, new char[]{0xd48a,0x6009,0x241c,0xa45c,0x4c3b,0xbf50,0x6143,0x68c0,0x7b5f,0x2c9c,0x2a8e,0x2432,0x7e4b}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xae90,0x7663,0x1901,0xd213,0x5c60,0xf3df,0x23aa,0x462c,0x316a,0x9e28,0x8dd0,0x6705,0x57e3,0xbe37,0xf59f,0x1bbe}, new char[]{0x57b0,0x1bce,0x1973,0x8db9,0x9e46,0xf3ab,0x6725,0x7620,0x4643,0xbe59,0x310e,0xaef9,0xf5eb,0xd27a,0x23c5,0x5c0e}).plainString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf237,0x3f8c,0x4cea,0x9d83}, new char[]{0x3fce,0x9dec,0x4c9e,0xf25f}).plainString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd177,0x9536,0xc13d}, new char[]{0x9577,0xd11e,0xc14f,0xc150}).plainString();
        static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x00a6,0x9eb4,0xa01b,0x5264,0x3323,0xb673}, new char[]{0x3364,0x00d4,0x520b,0xb606,0xa075,0x9ed0}).plainString();
    }
}
