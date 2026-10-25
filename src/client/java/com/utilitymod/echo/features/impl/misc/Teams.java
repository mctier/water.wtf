package com.utilitymod.echo.features.impl.misc;

import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.settings.impl.BoolSetting;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.utils.strings.Concat;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.scoreboard.Team;

public class Teams extends Feature {

    public Teams() {
        super(new FeatureInfo(
            Concat.of(new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2279,0xd12e,0x37cd,0xa8ae,0xbc04}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be}).toString()),
            Concat.of(LT.L1),
            Category.INTERNALS
        ));
    }
    public BoolSetting colorCheck = new BoolSetting(Concat.of(LT.L2), true);
    public BoolSetting nameCheck = new BoolSetting(Concat.of(LT.L3), false);
    public BoolSetting scoreboardCheck = new BoolSetting(Concat.of(LT.L4), true);

    public boolean isOnSameTeam(Entity entity) {
        if (!(entity instanceof PlayerEntity player)) {
            return false;
        }

        if (colorCheck.getValue()) {
            String playerName = player.getDisplayName().getString();
            String selfName = mc.player.getDisplayName().getString();
            if (playerName.startsWith(LT.L5) && selfName.startsWith(LT.L5)) {
                return playerName.charAt(1) == selfName.charAt(1);
            }
        }

        if (nameCheck.getValue()) {
            String playerName = player.getDisplayName().getString();
            String selfName = mc.player.getDisplayName().getString();
            int bracketIndex = playerName.lastIndexOf('[');
            if (bracketIndex != -1) {
                String playerTeam = playerName.substring(bracketIndex);
                return selfName.endsWith(playerTeam);
            }
        }

        if (scoreboardCheck.getValue()) {
            Team playerTeam = mc.world.getScoreboard().getScoreHolderTeam(player.getName().getString());
            Team selfTeam = mc.world.getScoreboard().getScoreHolderTeam(mc.player.getName().getString());
            return playerTeam != null && playerTeam.equals(selfTeam);
        }

        return false;
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5a6c,0xf30d,0x4821,0x9d7c,0x6d22,0xcc58,0x20fe,0x8f03,0x054e,0xd58a,0x36c3,0xac63,0x1880,0x97cd,0x36c1}, new char[]{0xd5de,0x18e5,0x5a0d,0x36ac,0x9d5c,0x2093,0x6d43,0x484f,0x8f62,0x36a4,0xcc3d,0x97a0,0x052b,0xf363,0xac17}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x69b3,0xc728,0x0029,0x571a,0x4d2e,0x0a08,0x47ed,0xcd97,0x4da7,0x6777,0x9451}, new char[]{0xcdd4,0x943e,0xc744,0x6718,0x5768,0x0009,0x0a4b,0x4dcf,0x69d6,0x4d4d,0x4786}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xefbb,0x5e09,0x0b1f,0x597e,0x7785,0xe763,0xfe98,0xa0bd,0xe2ea,0xf7b6}, new char[]{0x0b51,0xefda,0xa0d0,0xfefd,0xe743,0x5e4a,0xe282,0x591b,0x77e6,0xf7dd}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x19b6,0x4dd0,0x8bc0,0xdde5,0x088b,0x10d8,0x6639,0xda5b,0x714f,0x1940,0x2be0,0x1262,0xf095,0x03a1,0x9f5c,0x6f88}, new char[]{0xddb6,0x10bb,0xf0fa,0x08f9,0x19d3,0x9f3e,0x120d,0x1921,0x713d,0x2b84,0x8be0,0x6fcb,0x03c9,0xda3e,0x665a,0x4dbb}).plainString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0be3}, new char[]{0x0b44,0x0b67,0x0417,0x58a3}).plainString();
    }
}
