package com.utilitymod.echo.event.impl;

import com.utilitymod.echo.event.Event;
import com.utilitymod.echo.render3.api.Draw3D;
import net.minecraft.client.render.Camera;
import org.joml.Matrix4f;

/**
 * echo EventRender3D. Posted from the WorldRenderer.render HEAD patch with
 * the frame's view (positionMatrix) and projection matrices; draw targets
 * project to the HUD (see FramebufferTarget).
 */
public class EventRender3D extends Event {
    private final float tickDelta;
    private final Camera camera;
    private final Matrix4f projectionMatrix;
    private final Matrix4f modelViewMatrix;
    private final Draw3D draw3D;

    public EventRender3D(float tickDelta, Camera camera, Matrix4f projectionMatrix, Matrix4f modelViewMatrix, Draw3D draw3D) {
        this.tickDelta = tickDelta;
        this.camera = camera;
        this.projectionMatrix = projectionMatrix;
        this.modelViewMatrix = modelViewMatrix;
        this.draw3D = draw3D;
    }

    public float getTickDelta() { return tickDelta; }
    public float getDeltaTicks() { return tickDelta; }
    public Camera getCamera() { return camera; }
    public Matrix4f getProjectionMatrix() { return projectionMatrix; }
    public Matrix4f getModelViewMatrix() { return modelViewMatrix; }
    public Draw3D getDraw3D() { return draw3D; }
}
