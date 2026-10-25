package com.utilitymod.echo.event.impl;

import com.utilitymod.echo.event.Event;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EventSlowdown extends Event {
    private float slowdownMultiplier;
    private boolean isSlowDueToItem;
    private boolean isMovingSlowly;
    private boolean spoofPassenger;

    public EventSlowdown(float slowdownMultiplier, boolean isSlowDueToItem, boolean isMovingSlowly, boolean spoofPassenger) {
        super();
        this.slowdownMultiplier = slowdownMultiplier;
        this.isSlowDueToItem = isSlowDueToItem;
        this.isMovingSlowly = isMovingSlowly;
        this.spoofPassenger = spoofPassenger;
    }
}
