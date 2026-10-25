package com.utilitymod.echo.event.impl;

import com.utilitymod.echo.event.Event;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EventMove extends Event {
    private double x, y, z;
    private boolean onGround;
    private double yaw, pitch;

    public EventMove(Event.Stage stage, double x, double y, double z, boolean onGround, double yaw, double pitch) {
        super(stage);
        this.x = x;
        this.y = y;
        this.z = z;
        this.onGround = onGround;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public static class Pre extends EventMove {
        public Pre(double x, double y, double z, boolean onGround, double yaw, double pitch) {
            super(Event.Stage.PRE, x, y, z, onGround, yaw, pitch);
        }
    }

    public static class Post extends EventMove {
        public Post(double x, double y, double z, boolean onGround, double yaw, double pitch) {
            super(Event.Stage.POST, x, y, z, onGround, yaw, pitch);
        }
    }

}
