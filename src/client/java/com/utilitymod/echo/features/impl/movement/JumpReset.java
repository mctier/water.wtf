package com.utilitymod.echo.features.impl.movement;

import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventPacketReceive;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.settings.impl.BoolSetting;
import com.utilitymod.echo.features.settings.impl.FloatSetting;
import com.utilitymod.echo.features.settings.impl.InputSimulationSetting;
import com.utilitymod.echo.api.EntityMotionPacketCompat;
import com.utilitymod.echo.handlers.InputHandler;
import com.utilitymod.echo.utils.strings.Concat;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;

// Flags on high%
public class JumpReset extends Feature {

    private final FloatSetting chance = new FloatSetting(LT.L1, 100, 0, 100, 1);
    private final BoolSetting liquidCheck = new BoolSetting(LT.L2, true);
    private final BoolSetting webCheck = new BoolSetting(LT.L3, true);
    private final InputSimulationSetting inputSimulation = new InputSimulationSetting(Concat.of(LT.L4), false);

    public JumpReset() {
        super(new FeatureInfo(
                Concat.of(new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa8be,0x9637,0x82de,0x379e,0x0b39,0x1c97,0x2275,0xb15d,0xd130,0xbc19}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643}).toString()),
                Concat.of(LT.L5),
                Category.MOVEMENT));
    }

    @EventSubscribe
    public void onPacketReceive(EventPacketReceive event) {
        if (isNull())
            return;

        if (!(event.getPacket() instanceof EntityVelocityUpdateS2CPacket velo))
            return;

        if (mc.player == null || EntityMotionPacketCompat.id(velo) != mc.player.getId())
            return;
        if (mc.player.hurtTime <= 0)
            return;
        if (!mc.player.isOnGround())
            return;
        if (!shouldJump())
            return;

        if (inputSimulation.getValue()) {
            InputHandler.simulateClick(mc.options.jumpKey, inputSimulation);
        } else {
            mc.player.jump();
        }
    }

    private boolean shouldJump() {
        if (isNull())
            return false;

        if (Math.random() * 100 > chance.getValue())
            return false;
        if (liquidCheck.getValue() && (mc.player.isTouchingWater() || mc.player.isInLava()))
            return false;
        return !webCheck.getValue() || !mc.world.getBlockState(mc.player.getBlockPos())
                .getBlock().getTranslationKey().contains(LT.L6);
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0c8a,0xa84d,0x6259,0x6559,0xdbb1,0xc1c3}, new char[]{0xa80e,0x6231,0xc1a2,0xdbdf,0x653a,0x0cef}).toString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4b0c,0x0c11,0x7405,0xf11c,0x032b,0x5a30,0x5aeb,0xa7b6,0x9f89,0x4a2c,0x3074,0xad29}, new char[]{0xa7fa,0x0c78,0xad58,0x035e,0x746c,0x5a54,0x4a0c,0xf15f,0x4b64,0x3011,0x5a88,0x9fe2}).toString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4b89,0xe0f0,0x742b,0xd9e2,0xc17c,0xb19c,0xe61c,0x9a4d,0x301a}, new char[]{0xe0a7,0xd987,0xe67e,0x9a6d,0x4bca,0xc114,0xb1f9,0x3079,0x7440}).toString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xeee0,0xfcbb,0x129b,0x9b6b,0xf56a,0x4998,0xfbfb,0xf1e6,0x628c,0x7411,0x2eaa,0x5dea,0x0190,0xb2bf,0xf707,0x56a3}, new char[]{0xb2f6,0x49f6,0x01e0,0xf51f,0x12ef,0xfbdb,0xf1b5,0x56ca,0x747c,0x9b1e,0x62e0,0x2ecb,0xee94,0xf76e,0xfcd4,0x5d84}).plainString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x10ba,0x1bd0,0x7549,0x65cb,0x0f12,0x1b08,0x84fd,0xbae6,0xae89,0xd782,0x8acb,0x7bc9,0x12e5,0xc39a,0xd118,0x2f71,0xa0ac,0x4bf8,0x8ae7,0x49ab,0x859f,0x00d6,0xaf10,0x52aa,0x61b6,0x6da0,0x79a2,0xfea3,0x4b12,0x13ac,0x7bbc,0xd78d,0x9f5f,0xeb5f,0xca9e,0x4e13,0x3810,0x2fd7,0x3145,0x5128,0x9d62,0x094f,0x5a93,0xa443,0xd643,0x7d13,0xb39c,0x87dd,0x17de,0x470b,0x44fe,0xa1fc,0x4850,0x0e01,0x35a6,0xeacd,0x6c9b,0x4e55,0x0130,0xf4cd}, new char[]{0xa0ed,0x5ae6,0x52de,0xd62c,0x13c1,0x00b7,0x4824,0x4762,0x17bd,0x3871,0x79ce,0xf4a1,0xaef0,0x7d33,0xd7e8,0x10cf,0x1bbd,0x6ceb,0x3136,0x4b32,0x8492,0x4e3b,0x1b28,0x87b6,0x9d0c,0x128a,0x4e70,0x8aa0,0x449c,0x092e,0xb3ff,0x015b,0x8ac7,0xaf64,0xa42c,0xc3ba,0xeb2d,0xd7e8,0xd17c,0x0f67,0x2f12,0x0e64,0x7569,0xfeca,0xcaf0,0x9f3c,0x6dcf,0x35cb,0xeaa4,0x4b96,0xa19b,0x6196,0x7bbf,0x2fb2,0x7bd0,0x5147,0x49c8,0xba8f,0x85eb,0x65b2}).plainString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xae00,0xde17,0xd6f4}, new char[]{0xd683,0xde72,0xae62,0x055f}).plainString();
    }
}
