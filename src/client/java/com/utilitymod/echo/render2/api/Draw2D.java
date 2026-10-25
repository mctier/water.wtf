package com.utilitymod.echo.render2.api;

import com.utilitymod.echo.render3.api.FramebufferTarget;
import net.minecraft.client.gui.DrawContext;
import org.joml.Matrix4f;

/**
 * echo render2 Draw2D subset: rects drawn straight into the HUD DrawContext
 * that EchoEvents installs around the EventRender2DGui pass.
 */
public class Draw2D {
    private DrawContext current;

    public void begin(DrawContext context) {
        current = context;
    }

    public void end() {
        current = null;
    }

    public void rect(Matrix4f model, float x, float y, float w, float h, float radius, int color) {
        if (current != null) FramebufferTarget.rect(current, x, y, w, h, color);
    }

    public void flush() {
    }
}
