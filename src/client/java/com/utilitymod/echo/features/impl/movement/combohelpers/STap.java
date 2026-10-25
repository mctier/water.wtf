package com.utilitymod.echo.features.impl.movement.combohelpers;

import com.utilitymod.echo.event.impl.EventMovementInput;
import com.utilitymod.echo.event.impl.EventStartAttack;
import com.utilitymod.echo.features.settings.impl.BoolSetting;
import com.utilitymod.echo.features.settings.impl.IntSetting;
import com.utilitymod.echo.utils.strings.Concat;

public class STap extends ComboMode {

    public static final IntSetting durationSetting = new IntSetting(Concat.of(LT.L1), 3, 1, 20, Concat.of(LT.L2));
    public static final IntSetting reactionSetting = new IntSetting(Concat.of(LT.L3), 0, 0, 20, Concat.of(LT.L2));
    public static final BoolSetting allowInAir = new BoolSetting(Concat.of(LT.L4), false);

    @Override
    protected boolean validateAttack(EventStartAttack e) {
        return mc.player.forwardSpeed > 0.0;
    }

    @Override
    protected void applyMovement(EventMovementInput e) {
        if (inWindow(reactionSetting.getValue(), durationSetting.getValue(), allowInAir.getValue())) {
            e.forward = false;
            e.back = true; // tap backward to reset sprint
            ownFB = true;
        }
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9f14,0xf914,0x65b8,0xe667,0x3674,0x1fdf,0x0d9b,0xe86c}, new char[]{0x65fc,0x1faa,0x0de9,0xf975,0xe613,0x9f7d,0xe803,0x361a}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x950d,0xe537,0x549c,0xe0ae,0x2155,0x2cf8}, new char[]{0xe08e,0xe563,0x2c91,0x956e,0x54f7,0x2126}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb8cf,0x6b6e,0x34f3,0x3163,0xfe4c,0xdf2f,0xb816,0x4770,0x2ca8,0x5798,0xd489,0x4061,0xf92c}, new char[]{0x6b3c,0x57fd,0xd4e8,0x2ccb,0x3487,0xb87f,0xb8a0,0xf942,0x4750,0xdf7b,0x310a,0x400c,0xfe29}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1fdf,0xe358,0x4982,0x01c2,0x891c,0xbb5e,0x9860,0x517e,0x1ef2,0x88fb,0xa612}, new char[]{0xa655,0xe32a,0xbb31,0x1e87,0x8972,0x1fbb,0x01e2,0x49cd,0x980e,0x8897,0x5107}).plainString();
    }
}
