package com.utilitymod.echo.utils.combat.bots;

import com.utilitymod.echo.features.impl.misc.TargetControlModule;
import net.minecraft.entity.player.PlayerEntity;

import java.util.List;

public final class BotChecks {

    private static final List<BotCheck> CHECKS = List.of(
            new NameBotCheck(),
            new BlissBotCheck(),
            new MovementBotCheck(),
            new ScaleBotCheck(),
            new TabListBotCheck()
    );

    private BotChecks() {
    }

    public static boolean isBot(PlayerEntity player, TargetControlModule targets) {
        for (BotCheck check : CHECKS) {
            if (!check.isBot(player, targets)) {
                continue;
            }

            return true;
        }

        return false;
    }
}
