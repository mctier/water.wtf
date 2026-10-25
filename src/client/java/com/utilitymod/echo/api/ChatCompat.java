package com.utilitymod.echo.api;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public final class ChatCompat {
    private ChatCompat() {}

    public static void addMessage(Text message) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.inGameHud != null) mc.inGameHud.getChatHud().addMessage(message);
    }
}
