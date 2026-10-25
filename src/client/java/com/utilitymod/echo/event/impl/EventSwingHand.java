package com.utilitymod.echo.event.impl;

import com.utilitymod.echo.event.Event;
import lombok.Getter;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;

@Getter
public class EventSwingHand extends Event {
    private final PlayerEntity player;
    private final Hand hand;

    public EventSwingHand(PlayerEntity player, Hand hand) {
        this.player = player;
        this.hand = hand;
    }

}
