package com.utilitymod.echo.event.impl;

import com.utilitymod.echo.event.Event;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

public class EventStartAttack extends Event {
    private final PlayerEntity player;
    private final Entity target;

    public EventStartAttack(PlayerEntity player, Entity target) {
        this.player = player;
        this.target = target;
    }

    public PlayerEntity getPlayer() {
        return player;
    }

    public Entity getTarget() {
        return target;
    }

    public static class Pre extends EventStartAttack {
        public Pre(PlayerEntity player, Entity target) {
            super(player, target);
        }
    }
}
