package com.utilitymod.echo.features.impl.movement.combohelpers;

import com.utilitymod.echo.event.impl.EventMovementInput;
import com.utilitymod.echo.features.settings.impl.BoolSetting;
import com.utilitymod.echo.features.settings.impl.IntSetting;
import com.utilitymod.echo.utils.strings.Concat;

/**
 * Smart reset: picks the best reset for the situation at the moment of each hit.
 *  - in the air            -> strafe reset (can't W-tap or jump mid-air)
 *  - sprinting forward      -> W-tap (cleanest sprint reset while chasing)
 *  - otherwise, on ground   -> jump reset
 * Forward/back for the non-W-tap cases are left to the Distance helper, so you keep the gap.
 */
public class Smart extends ComboMode {

    public static final IntSetting durationSetting = new IntSetting(Concat.of(LT.L1), 3, 1, 20, Concat.of(LT.L2));
    public static final IntSetting reactionSetting = new IntSetting(Concat.of(LT.L3), 0, 0, 20, Concat.of(LT.L2));
    public static final BoolSetting allowInAir = new BoolSetting(Concat.of(LT.L4), false);

    private int handled = -1;
    private boolean dir;
    private int choice; // 0 w-tap, 1 jump, 2 strafe

    @Override
    protected void applyMovement(EventMovementInput e) {
        if (!inWindow(reactionSetting.getValue(), durationSetting.getValue(), allowInAir.getValue())) return;
        if (attackTick != handled) {
            handled = attackTick;
            dir = !dir;
            boolean onGround = mc.player.isOnGround();
            boolean sprintingForward = mc.player.isSprinting() && isForwardKeyDown();
            choice = !onGround ? 2 : (sprintingForward ? 0 : 1);
        }
        switch (choice) {
            case 0 -> {                                   // W-tap
                e.forward = false;
                ownFB = true;
            }
            case 1 -> {                                   // jump reset
                if (mc.player.isOnGround()) e.jump = true;
            }
            default -> {                                  // strafe reset
                e.left = dir;
                e.right = !dir;
            }
        }
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf7ab,0x1d94,0x9db6,0xcc92,0xa368,0xc9f6,0xaa1f,0x905f}, new char[]{0x901b,0x9dc3,0xf7d9,0x1df5,0xa31c,0xaa76,0xc999,0xccfc}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x08a7,0xefe8,0x3e2c,0xf39f,0x0bb5,0x249e}, new char[]{0x0887,0x3e78,0x0bdc,0xef8b,0xf3f4,0x24ed}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5f6c,0xcc55,0x3cc2,0xd1e9,0x022d,0xbd59,0xca3a,0x5dd9,0x3995,0xdceb,0x2e9f,0x7a7a,0xde46}, new char[]{0xdcb9,0xca5f,0xd188,0x39f6,0x3cb6,0xcc3c,0xde29,0x5db7,0x5f4c,0xbd0d,0x7a13,0x2ef2,0x0248}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2349,0xb8f5,0xcd07,0x499d,0x7fdb,0xef64,0x38ef,0x3a0b,0xa8f0,0x7573,0x2edc}, new char[]{0x49da,0x2eae,0xb89a,0xef11,0x2327,0x388b,0xa8d0,0x3a44,0xcd69,0x7fb7,0x750a}).plainString();
    }
}
