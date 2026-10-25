package com.utilitymod.echo.handlers.impl;

import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventOnAttackEntity;
import com.utilitymod.echo.event.impl.EventPacketReceive;
import com.utilitymod.echo.handlers.Handler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import net.minecraft.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class HurtTickHandler extends Handler {
    private static final int HURT_TICKS = 10;
    private static final Map<UUID, Long> attackedEntityTicks = new HashMap<>();

    public static long getAttackedEntityTick(LivingEntity entity) {
        if (entity == null) {
            return 0L;
        }
        return attackedEntityTicks.getOrDefault(entity.getUuid(), 0L);
    }

    public static boolean isCurrentlyHurt(LivingEntity entity) {
        if (entity == null || mc.player == null) {
            return false;
        }

        long customRemaining = 0;
        Long lastAttackTick = attackedEntityTicks.get(entity.getUuid());
        if (lastAttackTick != null) {
            long currentTick = mc.player.age;
            long elapsed = currentTick - lastAttackTick;
            if (elapsed >= HURT_TICKS) {
                attackedEntityTicks.remove(entity.getUuid());
            } else {
                customRemaining = HURT_TICKS - elapsed;
            }
        }

        // Vanilla hurtTime adjusted by ping
        int pingTicks = getPingTicks();
        int vanillaRemaining = Math.max(entity.hurtTime - Math.max(pingTicks - 1, 0), 0);

        // If we have custom tracking, use the higher of both so external hits are still caught
        // If no custom tracking, fall back to vanilla hurtTime (external hits only)
        long remaining = customRemaining > 0 ? Math.max(customRemaining, vanillaRemaining) : vanillaRemaining;
        return remaining > 0;
    }

    private static int getPingTicks() {
        if (mc.player == null || mc.getNetworkHandler() == null) return 0;
        PlayerListEntry info = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
        if (info == null) return 0;
        return info.getLatency() / 50;
    }

    @EventSubscribe
    private void onAttackPost(EventOnAttackEntity.Post event) {
        if (mc.player == null) {
            return;
        }
        if (event.getTarget() instanceof LivingEntity target) {
            attackedEntityTicks.put(target.getUuid(), (long) mc.player.age);
        }
    }

    @EventSubscribe
    private void onPacketReceive(EventPacketReceive event) {
        if (event.getPacket() instanceof PlayerPositionLookS2CPacket
                || event.getPacket() instanceof PlayerRespawnS2CPacket) {
            attackedEntityTicks.clear();
        }
    }
}
