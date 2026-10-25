package com.utilitymod.echo.features.impl.movement;

import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.SprintEvent;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.utils.strings.Concat;
import com.utilitymod.echo.features.settings.impl.BoolSetting;
import com.utilitymod.echo.handlers.impl.SprintController;
import com.utilitymod.echo.utils.player.PlayerUtils;

public class Sprint extends Feature {
    private static final CharSequence EMPTY = Concat.of();
    private static final CharSequence OMNI = Concat.of(LT.L1);

	public Sprint() {
		super(new FeatureInfo(
			Concat.of(new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa8bb,0xb17b,0xd129,0x226a,0x37d0,0xbc00}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f}).toString()),
			dev.potato.spud.api.ProtectedText.fromInts(new int[]{0x5F,0x5C,0x04,0xD4,0xE2,0x7E,0x24,0xED,0xCF,0x9F,0x4C}),
			Category.MOVEMENT
		));
	}

    private final BoolSetting omniSprint = new BoolSetting(Concat.of(LT.L1), false);

    public boolean isOmniSprintEnabled() {
        return isEnabled() && omniSprint.getValue();
    }

    @Override
    public CharSequence concat() {
        if (!isOmniSprintEnabled()) {
            return EMPTY;
        }

        return OMNI;
    }

    @Override
    public void onDisable() {
        if (mc.player != null) {
            SprintController.reset();
            mc.player.setSprinting(false);
        }
        super.onDisable();
    }

    @EventSubscribe
    public void onSprint(SprintEvent event) {
        if (!isEnabled()) {
            return;
        }
        
        if (SprintController.isForceStopSprint()) {
            event.setShouldSprint(false);
            return;
        }
        
        if (PlayerUtils.isMoving()) {
            event.setShouldSprint(true);
        }
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x77d7,0x060b,0x565f,0x6551}, new char[]{0x0644,0x653c,0x77b9,0x5636}).plainString();
    }
}
