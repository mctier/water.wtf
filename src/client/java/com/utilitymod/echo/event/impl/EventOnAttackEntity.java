package com.utilitymod.echo.event.impl;

import com.utilitymod.echo.event.Event;
import lombok.Getter;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

@Getter
public class EventOnAttackEntity extends Event {
    private final PlayerEntity player;
    private final Entity target;

    public EventOnAttackEntity(Stage stage, PlayerEntity player, Entity target) {
        super(stage);
        this.player = player;
        this.target = target;
    }

    public static class Pre extends EventOnAttackEntity {
        public Pre(PlayerEntity player, Entity target) {
            super(Stage.PRE, player, target);
        }
    }

    public static class Post extends EventOnAttackEntity {
        public Post(PlayerEntity player, Entity target) {
            super(Stage.POST, player, target);
        }
    }
}
