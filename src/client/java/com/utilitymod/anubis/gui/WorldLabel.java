package com.utilitymod.anubis.gui;

import com.utilitymod.anubis.imgui.ImDrawList;
import com.utilitymod.echo.EchoEvents;
import com.utilitymod.echo.Render3D;
import dev.koifih.client.render.Text;
import dev.koifih.client.util.Colors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector4f;

/** Shim: world point -> GUI px via the captured camera, and a two-line label drawn with the client font. */
public final class WorldLabel {
    private WorldLabel() {}

    /** {x, y} in GUI px, or null when behind the camera / no frame yet. */
    public static float[] project(MinecraftClient mc, Vec3d world, float screenW, float screenH) {
        Render3D frame = EchoEvents.lastFrame();
        if (frame == null) return null;
        Vector4f clip = frame.toClip(world.x, world.y, world.z);
        if (clip.w() <= 0.05f) return null;
        return Render3D.toGui(clip);
    }

    public static void drawText(ImDrawList dl, float cx, float bottom, String primary, String secondary, float alpha) {
        if (dl == null || dl.ctx == null) return;
        float y = bottom;
        if (secondary != null && !secondary.isEmpty()) {
            Text.drawCenteredX(dl.ctx, secondary, cx, y - 5, 6.5f, Colors.withAlpha(0xFFB8BCC6, alpha));
            y -= 10;
        }
        if (primary != null && !primary.isEmpty()) {
            Text.drawCenteredX(dl.ctx, primary, cx, y - 5, 7.5f, Colors.withAlpha(0xFFFFFFFF, alpha));
        }
    }
}
