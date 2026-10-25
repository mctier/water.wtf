package com.utilitymod.echo.features.impl.movement.combohelpers;

import com.utilitymod.echo.event.impl.EventMovementInput;
import com.utilitymod.echo.features.settings.impl.BoolSetting;
import com.utilitymod.echo.features.settings.impl.IntSetting;
import com.utilitymod.echo.utils.strings.Concat;

/** Strafe reset: steps sideways during the window, flipping direction each hit.
 *  Forward/back are left to the Distance helper, so you strafe while holding the gap. */
public class Strafe extends ComboMode {

    public static final IntSetting durationSetting = new IntSetting(Concat.of(LT.L1), 3, 1, 20, Concat.of(LT.L2));
    public static final IntSetting reactionSetting = new IntSetting(Concat.of(LT.L3), 0, 0, 20, Concat.of(LT.L2));
    public static final BoolSetting allowInAir = new BoolSetting(Concat.of(LT.L4), false);

    private int handled = -1;
    private boolean dir;

    @Override
    protected void applyMovement(EventMovementInput e) {
        if (inWindow(reactionSetting.getValue(), durationSetting.getValue(), allowInAir.getValue())) {
            if (attackTick != handled) {
                handled = attackTick;
                dir = !dir;
            }
            e.left = dir;
            e.right = !dir;
        }
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf3f8,0x5d4e,0xa8dd,0x2d99,0x92f2,0xf7c8,0x4456,0x9840}, new char[]{0x5d0a,0xf38d,0x2deb,0xf7a9,0x4422,0x929b,0x982f,0xa8b3}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x17b4,0x1466,0xb81c,0x3a2b,0xa184,0x6b07}, new char[]{0x6b27,0x17e0,0x3a42,0xa1e7,0x140d,0xb86f}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2cac,0x67fc,0xc745,0x002b,0xe84c,0x5d32,0xbe4b,0x54c0,0x9125,0x4a2f,0x144e,0x1155,0x8bf3}, new char[]{0x4a7d,0xe829,0x8b92,0x9146,0x5d46,0x0042,0x113a,0xbe25,0xc765,0x2cf8,0x6795,0x1423,0x54a5}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe18c,0x210c,0xb832,0x1b5a,0xc5d9,0x410e,0xcdd9,0x682b,0x4b54,0xe6b8,0x3e73}, new char[]{0x214b,0xcdab,0xc5b6,0x4b21,0xb85c,0x3e17,0xe698,0x4141,0x6845,0x1b36,0xe1f5}).plainString();
    }
}
