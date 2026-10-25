package com.utilitymod.echo.features.impl.movement.combohelpers;

import com.utilitymod.echo.event.impl.EventMovementInput;
import com.utilitymod.echo.features.settings.impl.IntSetting;
import com.utilitymod.echo.utils.strings.Concat;

/** Jump reset: hops on the hit (if on the ground) to cut incoming knockback. */
public class Jump extends ComboMode {

    public static final IntSetting durationSetting = new IntSetting(Concat.of(LT.L1), 1, 1, 10, Concat.of(LT.L2));
    public static final IntSetting reactionSetting = new IntSetting(Concat.of(LT.L3), 0, 0, 20, Concat.of(LT.L2));
    // kept so the mode exposes the same trio; "Ground Only" is implicit (can't jump mid-air anyway)
    public static final com.utilitymod.echo.features.settings.impl.BoolSetting allowInAir =
            new com.utilitymod.echo.features.settings.impl.BoolSetting(Concat.of(LT.L4), false);

    @Override
    protected void applyMovement(EventMovementInput e) {
        int t = mc.player.age - attackTick;
        int start = reactionSetting.getValue();
        boolean active = t >= start && t < start + durationSetting.getValue();
        if (active && mc.player.isOnGround() && (!allowInAir.getValue() || mc.player.isSprinting())) {
            e.jump = true;
        } else {
            e.jump = isJumpKeyDown();
        }
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3cfc,0x2bc6,0x081a,0xa4b4,0x762e,0x8eab,0x40dd,0xc5b6}, new char[]{0x3cb8,0x40a8,0x2bb4,0xc5d7,0xa4c0,0x0873,0x8ec4,0x7640}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x82b3,0x4f3b,0xa080,0x08ce,0x18f7,0xe60a}, new char[]{0x8293,0xe65e,0xa0e9,0x1894,0x4f50,0x08bd}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb5da,0xa17c,0x2d20,0x469f,0xadaf,0x2665,0xbd21,0x7cd7,0x7318,0x295d,0x5cc5,0xdedb,0x449a}, new char[]{0xbd73,0x737d,0xdeba,0xb5b9,0xaddb,0x2d49,0x46f0,0x260b,0xa15c,0x44ce,0x2934,0x5ca8,0x7cb2}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2e47,0xe1aa,0xe5ac,0x6d65,0xc240,0xf1b7,0x148c,0xd4cf,0x4c4c,0xa3a3,0xda11,0x5d70,0xab0b,0x171b,0x898d,0xeb3a,0x3e09,0xc2b9,0x7bf6}, new char[]{0xa3ec,0xc22e,0xeb56,0xda68,0xab2b,0x2e10,0x3e61,0x7b93,0xe1c4,0x89ad,0x5d23,0xd4bf,0xc2cb,0xf1de,0xe5c2,0x14f8,0x1772,0x6d0b,0x4c2b}).plainString();
    }
}
