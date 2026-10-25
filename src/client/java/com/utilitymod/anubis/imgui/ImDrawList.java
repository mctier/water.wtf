package com.utilitymod.anubis.imgui;

import net.minecraft.client.gui.DrawContext;

/** Stand-in for imgui's ImDrawList: Anubis overlays draw through the HUD DrawContext instead. */
public final class ImDrawList {
    public final DrawContext ctx;

    public ImDrawList(DrawContext ctx) { this.ctx = ctx; }
}
