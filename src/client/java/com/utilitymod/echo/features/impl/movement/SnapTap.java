package com.utilitymod.echo.features.impl.movement;

import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventMovementInput;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.utils.strings.Concat;

/**
 * @See com.utilitymod.echo.mixin.input.MixinKeyboardInput
 */
public class SnapTap extends Feature {

    private boolean wasLeftPressed = false;
    private boolean wasRightPressed = false;
    private boolean wasForwardPressed = false;
    private boolean wasBackPressed = false;

    private boolean lastHorizontal = false;
    private boolean lastVertical = false;

    public SnapTap() {
        super(new FeatureInfo(
            Concat.of(new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0b33,0x1c97,0x82db,0x379e,0xbc05,0xa8be,0xb142,0x2274,0xd134}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2}).toString()),
            Concat.of(LT.L1),
            Category.MOVEMENT,
            false
        ));
    }

    @EventSubscribe
    public void onMove(EventMovementInput e) {
		if (isNull()) return;
		if (com.utilitymod.echo.api.MinecraftCompat.getScreen() != null) return;
        boolean leftJustPressed = e.left && !wasLeftPressed;
        boolean rightJustPressed = e.right && !wasRightPressed;

        if (leftJustPressed && !rightJustPressed) {
            lastHorizontal = false;
        } else if (rightJustPressed && !leftJustPressed) {
            lastHorizontal = true;
        }

        if (e.left && e.right) {
            if (lastHorizontal) {
                e.left = false;
            } else {
                e.right = false;
            }
        }

        boolean forwardJustPressed = e.forward && !wasForwardPressed;
        boolean backJustPressed = e.back && !wasBackPressed;

        if (forwardJustPressed && !backJustPressed) {
            lastVertical = true;
        } else if (backJustPressed && !forwardJustPressed) {
            lastVertical = false;
        }

        if (e.forward && e.back) {
            if (lastVertical) {
                e.back = false;
            } else {
                e.forward = false;
            }
        }

        wasLeftPressed = e.originalLeft;
        wasRightPressed = e.originalRight;
        wasForwardPressed = e.originalForward;
        wasBackPressed = e.originalBack;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        wasLeftPressed = false;
        wasRightPressed = false;
        wasForwardPressed = false;
        wasBackPressed = false;
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc1d4,0xe306,0x9504,0x8543,0xbec3,0x2c27,0xde00,0x2bab,0xbb9a,0x3c62,0x70d2,0x1a26,0x8e3d,0x964c,0xfd4e,0xf4fb,0x5116,0x5c03,0x6e4b,0xc4d7,0xacf1,0x0efe,0x36f2,0xa005,0x23bc}, new char[]{0x850a,0xa06b,0xe375,0x6e3f,0xbbfb,0x5178,0xfd3a,0x8e1d,0x1a42,0x70bb,0x2c55,0x0e9b,0xbea0,0xac85,0x5c6a,0xc1bb,0x23d2,0x36d2,0x3c01,0x956c,0xc4b6,0x2bc5,0xf49c,0x9629,0xde73}).plainString();
    }
}
