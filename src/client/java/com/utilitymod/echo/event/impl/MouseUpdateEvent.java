package com.utilitymod.echo.event.impl;

import com.utilitymod.echo.event.Event;

public class MouseUpdateEvent extends Event {

    public double deltaX;
    public double deltaY;

    public MouseUpdateEvent(double deltaX, double deltaY) {
        this.deltaX = deltaX;
        this.deltaY = deltaY;
    }
}