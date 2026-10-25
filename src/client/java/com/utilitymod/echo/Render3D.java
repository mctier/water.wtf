package com.utilitymod.echo;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * One world frame: camera position + view/projection matrices captured at
 * WorldRenderer.render HEAD. Echo draws through its own GL pipeline; here
 * modules project world points to GUI coordinates and draw in the HUD pass.
 */
public final class Render3D {
    public final Vec3d camera;
    public final Matrix4f view;
    public final Matrix4f projection;
    public final float tickDelta;

    Render3D(Vec3d camera, Matrix4f view, Matrix4f projection, float tickDelta) {
        this.camera = camera;
        this.view = view;
        this.projection = projection;
        this.tickDelta = tickDelta;
    }

    /** World point -> clip space (w > 0 means in front of the camera). */
    public Vector4f toClip(double x, double y, double z) {
        Vector4f v = new Vector4f((float) (x - camera.x), (float) (y - camera.y), (float) (z - camera.z), 1f);
        v.mul(view);
        v.mul(projection);
        return v;
    }

    /** Clip -> scaled GUI coordinates ({x, y}); caller ensures w > 0. */
    public static float[] toGui(Vector4f clip) {
        var window = MinecraftClient.getInstance().getWindow();
        float sw = window.getScaledWidth();
        float sh = window.getScaledHeight();
        return new float[] {
                (clip.x() / clip.w() * 0.5f + 0.5f) * sw,
                (0.5f - clip.y() / clip.w() * 0.5f) * sh
        };
    }
}
