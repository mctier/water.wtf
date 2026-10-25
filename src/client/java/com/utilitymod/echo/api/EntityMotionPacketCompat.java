package com.utilitymod.echo.api;

import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.util.math.Vec3d;

/** echo api.EntityMotionPacketCompat (1.21.11 branch). */
public final class EntityMotionPacketCompat {
    private EntityMotionPacketCompat() {}

    public static int id(EntityVelocityUpdateS2CPacket packet) {
        return packet.getEntityId();
    }

    public static Vec3d movement(EntityVelocityUpdateS2CPacket packet) {
        return packet.getVelocity();
    }
}
