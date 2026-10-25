package com.utilitymod.echo.utils.combat.bots;

import com.utilitymod.echo.features.impl.misc.TargetControlModule;
import net.minecraft.entity.player.PlayerEntity;

public interface BotCheck {

    CharSequence settingName();

    boolean isBot(PlayerEntity player, TargetControlModule targets);
}
