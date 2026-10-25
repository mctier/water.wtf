package com.utilitymod.echo.handlers;

import com.utilitymod.echo.utils.Imports;
import net.minecraft.client.MinecraftClient;

/** echo handlers.Handler: always-on bus subscriber (registered by EchoEvents). */
public class Handler implements Imports {
    protected static final MinecraftClient mc = MinecraftClient.getInstance();
}
