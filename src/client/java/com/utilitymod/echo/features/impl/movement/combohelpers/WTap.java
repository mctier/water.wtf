package com.utilitymod.echo.features.impl.movement.combohelpers;

import com.utilitymod.echo.event.impl.EventMovementInput;
import com.utilitymod.echo.event.impl.EventStartAttack;
import com.utilitymod.echo.features.settings.impl.BoolSetting;
import com.utilitymod.echo.features.settings.impl.IntSetting;
import com.utilitymod.echo.utils.strings.Concat;

public class WTap extends ComboMode {

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
            e.forward = false; // drop forward to reset sprint
            ownFB = true;
        }
        // outside the window: Distance helper (or your keys) drives forward/back
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1550,0x560e,0xa7e4,0xb99d,0x5714,0xb000,0xc767,0xa057}, new char[]{0xc723,0xa791,0x567c,0xb061,0x5760,0xb9f4,0xa038,0x153e}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2ce2,0xba8d,0x0b5b,0xe5d4,0xa70b,0xc7c3}, new char[]{0xbaad,0x2cb6,0xa762,0x0b38,0xc7a8,0xe5a7}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe675,0x6095,0xa97f,0x9c79,0x91dc,0xaafd,0x1edd,0x174c,0xc3d9,0x0c63,0xb97b,0x4940,0x3ba1}, new char[]{0x1e8f,0xa91a,0x3bc0,0x0c00,0xc3ad,0x9c10,0xe61a,0x60fb,0x176c,0xb92f,0x4929,0xaa90,0x91b9}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1490,0xec0e,0x967f,0x25ed,0x0382,0x85a3,0x2681,0x0fcc,0x1f3f,0xad7f,0x1f19}, new char[]{0x0f8b,0x03f0,0x26ee,0x85d6,0x1f77,0xec6a,0x14b0,0x1f70,0x9611,0x2581,0xad06}).plainString();
    }
}
