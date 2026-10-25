package com.utilitymod.echo.utils.network;

import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.Packet;

public final class NetworkUtil {
    private NetworkUtil() {}

    public static void send(Packet<?> packet) {
        sendPacket(packet);
    }

    public static void sendPacket(Packet<?> packet) {
        var handler = MinecraftClient.getInstance().getNetworkHandler();
        if (handler != null) handler.sendPacket(packet);
    }
}
