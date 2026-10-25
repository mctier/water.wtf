package com.utilitymod.echo.utils.combat.bots;

import com.utilitymod.echo.features.impl.misc.TargetControlModule;
import com.utilitymod.echo.utils.Imports;
import com.utilitymod.echo.utils.strings.Concat;
import net.minecraft.entity.player.PlayerEntity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class MovementBotCheck implements BotCheck, Imports {

    private final Set<UUID> movedEntities = new HashSet<>();

    @Override
    public CharSequence settingName() {
        return Concat.of(LT.L1);
    }

    @Override
    public boolean isBot(PlayerEntity player, TargetControlModule targets) {
        if (!targets.botCheckList.isEnabled(settingName())) {
            return false;
        }

        return !hasEntityMoved(player);
    }

    private boolean hasEntityMoved(PlayerEntity player) {
        if (mc.getNetworkHandler() == null) {
            return false;
        }

        if (player.lastX != player.getX() || player.lastZ != player.getZ()) {
            movedEntities.add(player.getUuid());
        }

        return movedEntities.contains(player.getUuid());
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3405,0x11ff,0x4fd4,0x6d6f,0x24f6,0xf032,0xea8f,0xeca6}, new char[]{0x24bb,0x1190,0x6d19,0xeaea,0x3468,0x4fb1,0xf05c,0xecd2}).plainString();
    }
}
