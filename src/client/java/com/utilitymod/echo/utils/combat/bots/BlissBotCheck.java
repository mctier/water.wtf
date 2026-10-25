package com.utilitymod.echo.utils.combat.bots;

import com.utilitymod.echo.features.impl.misc.TargetControlModule;
import com.utilitymod.echo.utils.strings.Concat;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class BlissBotCheck implements BotCheck {

    private static final int BLISS_AIR_TICKS = 2;

    private final Map<UUID, Integer> airTicks = new HashMap<>();
    private final Set<UUID> seenFalling = new HashSet<>();
    private final Set<UUID> seenGliding = new HashSet<>();

    @Override
    public CharSequence settingName() {
        return Concat.of(LT.L1);
    }

    @Override
    public boolean isBot(PlayerEntity player, TargetControlModule targets) {
        if (!targets.botCheckList.isEnabled(settingName())) {
            clearState(player);
            return false;
        }

        if (player.isGliding()) {
            seenGliding.add(player.getUuid());
        }

        if (seenGliding.contains(player.getUuid())) {
            clearAirState(player);
            return false;
        }

        if (!player.isAlive() || player.isRemoved()) {
            clearState(player);
            return false;
        }

        if (!hasArmor(player)) {
            clearState(player);
            return false;
        }

        if (player.isOnGround()) {
            clearState(player);
            return false;
        }

        UUID uuid = player.getUuid();
        if (player.fallDistance > 0.0) {
            seenFalling.add(uuid);
        }

        int currentAirTicks = airTicks.getOrDefault(uuid, 0) + 1;
        airTicks.put(uuid, currentAirTicks);

        if (currentAirTicks < BLISS_AIR_TICKS) {
            return false;
        }

        return !seenFalling.contains(uuid);
    }

    private boolean hasArmor(PlayerEntity player) {
        if (!player.getEquippedStack(EquipmentSlot.HEAD).isEmpty()) {
            return true;
        }

        if (!player.getEquippedStack(EquipmentSlot.CHEST).isEmpty()) {
            return true;
        }

        if (!player.getEquippedStack(EquipmentSlot.LEGS).isEmpty()) {
            return true;
        }

        if (!player.getEquippedStack(EquipmentSlot.FEET).isEmpty()) {
            return true;
        }

        return false;
    }

    private void clearState(PlayerEntity player) {
        UUID uuid = player.getUuid();
        clearAirState(player);
        seenGliding.remove(uuid);
    }

    private void clearAirState(PlayerEntity player) {
        UUID uuid = player.getUuid();
        airTicks.remove(uuid);
        seenFalling.remove(uuid);
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa2b6,0x973e,0x0a40,0xb94f,0x7bcd}, new char[]{0x7b8f,0xa2da,0x9757,0x0a33,0xb93c}).plainString();
    }
}
