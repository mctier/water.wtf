package com.utilitymod.echo.utils;

import net.minecraft.client.MinecraftClient;

/** echo utils.Imports: shared {@code mc} and the game-speed timer (patched tick counter). */
public interface Imports {
    MinecraftClient mc = MinecraftClient.getInstance();

    static void setTimer(float timer) {
        com.utilitymod.inject.Hooks.timerSpeed = timer;
    }

    static float timerSpeed() {
        return com.utilitymod.inject.Hooks.timerSpeed;
    }
}
