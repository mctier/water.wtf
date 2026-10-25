package com.utilitymod.echo.render3.api;

import com.utilitymod.echo.Echo;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/** echo render3 Draw3D: custom target = filled boxes, minecraft target = outlines (both projected). */
public class Draw3D {
    public Vec3d origin = Vec3d.ZERO;
    public Matrix4f mvp;
    public Matrix4f view;
    public Matrix4f proj;
    private final FramebufferTarget custom = new FramebufferTarget(true);
    private final FramebufferTarget minecraft = new FramebufferTarget(false);

    public static Draw3D getInstance() {
        return Echo.draw3d;
    }

    public void updateCamera(Matrix4f view, Matrix4f proj) {
        this.view = view;
        this.proj = proj;
    }

    public FramebufferTarget getMinecraftTarget() {
        return minecraft;
    }

    public FramebufferTarget getCustomTarget() {
        return custom;
    }

    public void beginFrame() {
        custom.beginFrame();
        minecraft.beginFrame();
    }

    public void endFrame() {
        custom.endFrame();
        minecraft.endFrame();
    }

    public void cleanup() {
        custom.cleanup();
        minecraft.cleanup();
    }
}
