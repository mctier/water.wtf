package com.utilitymod.echo.event.impl;

import com.utilitymod.echo.event.Event;
import com.utilitymod.echo.render2.api.Draw2D;
import com.utilitymod.echo.render2.impl.opengl.utils.RenderUtil;
import net.minecraft.client.gui.DrawContext;

/** echo EventRender2DGui (HUD pass). */
public class EventRender2DGui extends Event {
    private final DrawContext context;
    private final float tickDelta;
    private final Draw2D draw2D;

    public EventRender2DGui(DrawContext context, float tickDelta, Draw2D draw2D) {
        this.context = context;
        this.tickDelta = tickDelta;
        this.draw2D = draw2D;
    }

    public DrawContext getContext() { return context; }
    public float getTickDelta() { return tickDelta; }
    public Draw2D getDraw2D() { return draw2D; }
    public int getScaledWidth() { return RenderUtil.getScaledWidth(); }
    public int getScaledHeight() { return RenderUtil.getScaledHeight(); }
}
