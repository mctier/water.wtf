package com.utilitymod.echo.utils.combat.bots;

import com.utilitymod.echo.features.impl.misc.TargetControlModule;
import com.utilitymod.echo.utils.Imports;
import com.utilitymod.echo.utils.strings.Concat;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.PlayerEntity;

public final class TabListBotCheck implements BotCheck, Imports {

    @Override
    public CharSequence settingName() {
        return Concat.of(LT.L1);
    }

    @Override
    public boolean isBot(PlayerEntity player, TargetControlModule targets) {
        if (!targets.botCheckList.isEnabled(settingName())) {
            return false;
        }

        if (mc.getNetworkHandler() == null) {
            return false;
        }

        PlayerListEntry info = mc.getNetworkHandler().getPlayerListEntry(player.getUuid());
        return info == null;
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x58b1,0xa945,0xf99e,0xfe53,0xf04d,0x878b,0xe95e,0x555a}, new char[]{0xf019,0xa924,0xe93c,0xf9be,0x5516,0x58d8,0xfe20,0x87ff}).plainString();
    }
}
