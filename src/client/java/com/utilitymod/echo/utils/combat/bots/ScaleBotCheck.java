package com.utilitymod.echo.utils.combat.bots;

import com.utilitymod.echo.features.impl.misc.TargetControlModule;
import com.utilitymod.echo.utils.strings.Concat;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;

public final class ScaleBotCheck implements BotCheck {

    private static final double MIN_VALID_PLAYER_SCALE = 0.75;
    private static final double MAX_VALID_PLAYER_SCALE = 1.25;

    @Override
    public CharSequence settingName() {
        return Concat.of(LT.L1);
    }

    @Override
    public boolean isBot(PlayerEntity player, TargetControlModule targets) {
        if (!targets.botCheckList.isEnabled(settingName())) {
            return false;
        }

        double scale = player.getAttributeValue(EntityAttributes.SCALE);
        return scale < MIN_VALID_PLAYER_SCALE || scale > MAX_VALID_PLAYER_SCALE;
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x02b6,0xf086,0x3e7b,0xa4d8,0x714e}, new char[]{0x02e5,0xf0e5,0x3e1a,0xa4b4,0x712b}).plainString();
    }
}
