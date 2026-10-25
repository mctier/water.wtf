package com.utilitymod.echo.api;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

public final class MinecraftCompat {
    private MinecraftCompat() {}

    public static Screen getScreen() { return MinecraftClient.getInstance().currentScreen; }
}
