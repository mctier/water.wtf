package com.utilitymod.echo.features.impl.movement;

import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventMovementInput;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.settings.impl.BoolSetting;
import com.utilitymod.echo.features.settings.impl.FloatSetting;
import com.utilitymod.echo.features.settings.impl.ModeSetting;
import com.utilitymod.echo.utils.Imports;
import com.utilitymod.echo.utils.player.MoveUtils;
import com.utilitymod.echo.utils.player.PlayerUtils;
import com.utilitymod.echo.utils.strings.Concat;

public class Speed extends Feature {
    private final ModeSetting mode = new ModeSetting(
            Concat.of(LT.L1),
            Concat.of(LT.L2),
            Concat.of(LT.L2),
            Concat.of(LT.L3),
            Concat.of(LT.L4),
            Concat.of(LT.L5),
            Concat.of(LT.L6)
    );
    private final BoolSetting timerAbuse = new BoolSetting(LT.L7, false);

    // grim
    private final FloatSetting speed = new FloatSetting
            (LT.L8, 1.0f, 0.01f, 0.1f, 0.01f,
                    obj -> mode.is(LT.L4));

    // motion
    private final FloatSetting speedMotion = new FloatSetting(
            Concat.of(LT.L5), 1.0f, 0.01f, 10.00f, 0.01f,
                        obj -> mode.is(Concat.of(LT.L5)));

    // strafe
    private final FloatSetting speedStrafe = new FloatSetting(
            Concat.of(LT.L6), 1.0f, 0.01f, 10.00f, 0.01f,
            obj -> mode.is(Concat.of(LT.L6)));
    public Speed() {
        super(new FeatureInfo(
                Concat.of(new com.utilitymod.echo.utils.EncryptedString(new char[]{0x227d,0xd129,0x37da,0xa8bb,0xbc0c}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be}).toString()),
                Concat.of(LT.L9),
                Category.MOVEMENT
        ));
    }

    @EventSubscribe
    public void onEventMove(EventMovementInput e) {
        if (isNull() || !this.isEnabled()) return;
        if (timerAbuse.getValue()) {
            Imports.setTimer(1.004F);
        }
        if (!PlayerUtils.isMoving()) {
            return;
        }
        if (mode.is(Concat.of(LT.L2))) {
            if (mc.player.isOnGround()) {
                MoveUtils.strafe(0.26F);
                e.jump = true;
            }
        } else if (mode.is(Concat.of(LT.L3))) {
            if (mc.player.isOnGround()) {
                e.jump = true;
            }
        } else if (mode.is(Concat.of(LT.L4))) {
            if (PlayerUtils.isCollidingWithHitbox(mc.player)) {
                double boost = speed.getValue();
                double yaw = MoveUtils.getDirection();
                MoveUtils.motion((float) boost);
            }
        } else if (mode.is(Concat.of(LT.L5))) {
            MoveUtils.motion(speedMotion.getValue());
        } else if (mode.is(Concat.of(LT.L6))) {
            MoveUtils.strafe(speedStrafe.getValue());
        }
    }

    @Override
    public void onDisable() {
        Imports.setTimer(1.0F);
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x833b,0x3ffb,0x2847,0x6c6e}, new char[]{0x8376,0x6c01,0x3f9f,0x2822}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xfe44,0xe1ef,0x23a6,0x75f2,0x3886,0x6f24,0xf9d6,0xd01d,0xf23e,0x1070,0x5019}, new char[]{0xe1a2,0x759b,0xfe2a,0xf9b3,0x101d,0x23c3,0x6f4a,0xd05e,0x38ea,0xf24b,0x507b}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa0ce,0x33e5,0xf377,0xa83a,0xbe8f}, new char[]{0xa082,0x3380,0xa85d,0xf31e,0xbefb}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa093,0x4873,0x6ea5,0x43f8,0x7214,0x457e,0xaf74,0x2664,0x734e,0xfc2f,0x22be}, new char[]{0x7253,0x4801,0x7327,0x22d3,0x6e85,0xfc6a,0x4396,0xaf00,0x260d,0xa0e7,0x4507}).plainString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x30d6,0x057a,0x3396,0x8ceb,0xf7a1,0x9f29}, new char[]{0x9f64,0x8c84,0xf7d5,0x0513,0x30b9,0x33f8}).plainString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xda15,0x39e5,0x4224,0x3276,0x9d47,0xe723}, new char[]{0x4277,0x3202,0x9d35,0x3984,0xe745,0xda70}).plainString();
        static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x471a,0x02d0,0x7ef9,0xe0c3,0x516e,0xdeb3,0x7fc5,0x9e45,0xb93a,0x5398,0x190a}, new char[]{0x7ff4,0x5140,0x7ec9,0xde83,0xe0f7,0x9e65,0x53cc,0x02b9,0xb957,0x196f,0x4768}).toString();
        static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc022,0xb3c9,0x71da,0x22cf,0x200f}, new char[]{0xb38b,0x2060,0x71b5,0x22bc,0xc056}).toString();
        static final String L9 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3be1,0x767a,0x5a98,0x28b9,0x1e24,0x21d3,0x6d39,0x8cc7,0xaea3,0xb164,0x0dd5,0xad95,0xdf4d,0x5956,0x8190,0xc5dd,0x2344,0x4104,0x4834,0x530d,0x8c69,0x9216,0x017b,0x1001}, new char[]{0x7633,0x232a,0x28da,0x5924,0xb101,0x1e45,0xdf3e,0x3b84,0xade6,0x5ab8,0x8caa,0xaecc,0x4842,0x21b6,0x927b,0x81f5,0x8c07,0x5379,0x0df5,0x6d4a,0xc5ad,0x4161,0x1064,0x011f}).plainString();
    }
}
