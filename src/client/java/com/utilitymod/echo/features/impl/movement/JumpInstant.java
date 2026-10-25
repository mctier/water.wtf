package com.utilitymod.echo.features.impl.movement;

import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventHandleInput;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.utils.strings.Concat;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.utils.strings.Concat;

public class JumpInstant extends Feature {

	public JumpInstant() {
		super(new FeatureInfo(
			dev.potato.spud.api.ProtectedText.fromInts(new int[]{0x50,0x46,0x50,0xF1,0xB7,0x60,0x24,0xBF,0xE2,0x94,0x54,0x22,0xF3}),
			Concat.of(LT.L1),
			Category.MOVEMENT
		));
	}

    @EventSubscribe
    public void onTick(EventHandleInput.Early event) {
        if (mc.player == null || mc.world == null) return;

        com.utilitymod.echo.api.Accessors.setJumpInstant(mc.player, 0);
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1d2c,0x0724,0xdd65,0x43e6,0xb96f,0x2120,0x700f,0x6f59,0xd763,0xe023,0xa261,0xd292,0x52c9,0x4510,0x6a8e,0xf5c9,0x9615,0x8829,0xd208,0x219f,0xd822}, new char[]{0xd25a,0x4575,0xb902,0x52a6,0x0752,0xd706,0xdd16,0x8809,0x6ae4,0x707a,0x214d,0x9665,0x21bf,0x4385,0xd2fd,0x1d43,0xf5a5,0xa205,0x6f36,0xe054,0xd84c}).plainString();
    }
}
