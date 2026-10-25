package com.utilitymod.echo.render2.impl.opengl.utils;

import net.minecraft.client.MinecraftClient;

public final class RenderUtil {
    private RenderUtil() {}

    public static int getScaledWidth() { return MinecraftClient.getInstance().getWindow().getScaledWidth(); }

    public static int getScaledHeight() { return MinecraftClient.getInstance().getWindow().getScaledHeight(); }
}
