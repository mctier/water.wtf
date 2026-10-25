package com.utilitymod.echo.utils.combat.bots;

import com.utilitymod.echo.features.impl.misc.TargetControlModule;
import com.utilitymod.echo.utils.strings.Concat;
import net.minecraft.entity.player.PlayerEntity;

public final class NameBotCheck implements BotCheck {

    @Override
    public CharSequence settingName() {
        return Concat.of(LT.L1);
    }

    @Override
    public boolean isBot(PlayerEntity player, TargetControlModule targets) {
        if (!targets.botCheckList.isEnabled(settingName())) {
            return false;
        }

        String name = getBotCheckName(player);
        if (name.isEmpty()) {
            return true;
        }

        if (name.contains(LT.L2)) {
            return true;
        }

        if (name.contains(LT.L3)) {
            return true;
        }

        if (name.contains(" ")) {
            return true;
        }

        if (name.startsWith(LT.L4)) {
            return true;
        }

        return name.startsWith(LT.L5);
    }

    private String getBotCheckName(PlayerEntity player) {
        if (player.getDisplayName() == null) {
            return "";
        }

        String name = player.getDisplayName().getString();
        if (name == null) {
            return "";
        }

        return name.toLowerCase();
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb85c,0x957d,0x9d6e,0xe06f}, new char[]{0xb812,0xe00e,0x9d03,0x9518}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x349d}, new char[]{0x34a7,0x4f0d,0x3655,0xc238}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8c47}, new char[]{0x8c6c,0x2a13,0x3a61,0xb52c}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0bde,0xd46d,0xecc3}, new char[]{0xd40e,0x0bb7,0xecb7,0x4f1c}).plainString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9057,0x922c,0x0e43}, new char[]{0x9242,0x9027,0x0e20,0x07db}).plainString();
    }
}
