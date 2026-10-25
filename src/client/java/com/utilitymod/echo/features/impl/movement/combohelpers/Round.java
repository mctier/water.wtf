package com.utilitymod.echo.features.impl.movement.combohelpers;

import com.utilitymod.echo.event.impl.EventMovementInput;
import com.utilitymod.echo.features.settings.impl.BoolSetting;
import com.utilitymod.echo.features.settings.impl.IntSetting;
import com.utilitymod.echo.utils.strings.Concat;

/** Round reset: rotates through every reset technique each hit — W-tap, strafe, then jump. */
public class Round extends ComboMode {

    public static final IntSetting durationSetting = new IntSetting(Concat.of(LT.L1), 4, 1, 20, Concat.of(LT.L2));
    public static final IntSetting reactionSetting = new IntSetting(Concat.of(LT.L3), 0, 0, 20, Concat.of(LT.L2));
    public static final BoolSetting allowInAir = new BoolSetting(Concat.of(LT.L4), false);

    private int handled = -1;
    private int idx;        // 0 W-tap, 1 strafe, 2 jump
    private boolean dir;

    @Override
    protected void applyMovement(EventMovementInput e) {
        if (!inWindow(reactionSetting.getValue(), durationSetting.getValue(), allowInAir.getValue())) return;
        if (attackTick != handled) {
            handled = attackTick;
            idx = (idx + 1) % 3;
            if (idx == 1) dir = !dir;
        }
        switch (idx) {
            case 0 -> {                                   // W-tap
                e.forward = false;
                ownFB = true;
            }
            case 1 -> {                                   // strafe (Distance keeps the gap)
                e.left = dir;
                e.right = !dir;
            }
            default -> {                                  // jump reset
                if (mc.player.isOnGround()) e.jump = true;
            }
        }
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4edd,0xbe61,0x1fa6,0xcc48,0x630a,0x04d8,0xb635,0xc489}, new char[]{0xc4cd,0x04ad,0xcc3a,0x636b,0x4ea9,0x1fcf,0xb65a,0xbe0f}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4cc0,0xa53a,0x9d2c,0x1da2,0x7693,0x585d}, new char[]{0x76b3,0x4c94,0x1dcb,0x9d4f,0xa551,0x582e}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd044,0x6614,0x80bd,0x5038,0x8646,0xfcc2,0xacdb,0xb990,0x6c2d,0x6342,0xad10,0xc10b,0x55e1}, new char[]{0x6310,0x505d,0xfca3,0xacb8,0x8632,0x5588,0x80d2,0xb9fe,0xc12b,0xd010,0x6c44,0xad7d,0x6671}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8289,0x58e0,0xfc86,0x1ae9,0xc555,0x1dda,0xfdf0,0x1f51,0xe0b1,0x2c5b,0x3911}, new char[]{0x1aae,0x1f23,0x397e,0xfd85,0x588e,0xfce2,0x2c7b,0xc51a,0x1db4,0xe0dd,0x82f0}).plainString();
    }
}
