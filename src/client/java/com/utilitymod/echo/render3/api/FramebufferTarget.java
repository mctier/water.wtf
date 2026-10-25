package com.utilitymod.echo.render3.api;

import com.utilitymod.echo.Render3D;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.Box;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

/**
 * echo render3 target, implemented by projection: primitives submitted in
 * the world pass are stored in world space, then projected with that frame's
 * camera matrices and drawn in the HUD pass ({@link #drawProjected}).
 * Filled boxes draw as a translucent screen-space silhouette; outline targets
 * draw the 12 near-plane-clipped edges; tracers start at the crosshair.
 */
public class FramebufferTarget {
    private static final float EPSILON = 0.05f;
    /** Edges are cut this far (view depth, blocks) in front of the eye so a box hugging the camera can't
     *  project into screen-wide streaks; zoom (narrow FOV) magnifies those streaks a lot. */
    private static final float LINE_NEAR = 0.3f;
    private static final int[][] EDGES = {
            {0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};

    private record Prim(int kind, double[] v, int color) {}

    private final List<Prim> prims = new ArrayList<>();
    private volatile List<Prim> frame = List.of();
    private final boolean fillBoxes;

    public FramebufferTarget(boolean fillBoxes) {
        this.fillBoxes = fillBoxes;
    }

    public void beginFrame() {
        prims.clear();
    }

    public void endFrame() {
        frame = List.copyOf(prims);
    }

    public void cleanup() {
        prims.clear();
        frame = List.of();
    }

    public void box(double x, double y, double z, double sx, double sy, double sz, int color) {
        prims.add(new Prim(0, new double[] {x, y, z, x + sx, y + sy, z + sz}, color));
    }

    public void tracer(double x, double y, double z, int color) {
        prims.add(new Prim(1, new double[] {x, y, z}, color));
    }

    public void line(double fromX, double fromY, double fromZ, double toX, double toY, double toZ, int color) {
        prims.add(new Prim(2, new double[] {fromX, fromY, fromZ, toX, toY, toZ}, color));
    }

    /** Particle sprites (target-ESP effects) are not ported. */
    public void particle(double x, double y, double z, float size, int color, Object uv, float rotation) {
    }

    public void drawProjected(DrawContext ctx, Render3D cam) {
        if (cam == null) return;
        for (Prim p : frame) {
            double[] v = p.v();
            switch (p.kind()) {
                case 0 -> {
                    Vector4f[] c = corners(cam, new Box(v[0], v[1], v[2], v[3], v[4], v[5]));
                    if (fillBoxes) {
                        // a silhouette is only meaningful when the whole box is in front of the camera;
                        // otherwise its screen bounds explode into a sheet over the view
                        boolean front = true;
                        for (Vector4f k : c) front &= k.w() > LINE_NEAR;
                        float[] r = front ? bounds(c) : null;
                        if (r != null) rect(ctx, r[0], r[1], r[2] - r[0], r[3] - r[1], p.color());
                    } else {
                        for (int[] e : EDGES) {
                            float[] s = clip(c[e[0]], c[e[1]]);
                            if (s != null) line(ctx, s[0], s[1], s[2], s[3], p.color());
                        }
                    }
                }
                case 1 -> {
                    Vector4f t = cam.toClip(v[0], v[1], v[2]);
                    if (t.w() > EPSILON) {
                        float[] g = Render3D.toGui(t);
                        line(ctx, ctx.getScaledWindowWidth() / 2f, ctx.getScaledWindowHeight() / 2f, g[0], g[1], p.color());
                    }
                }
                default -> {
                    float[] s = clip(cam.toClip(v[0], v[1], v[2]), cam.toClip(v[3], v[4], v[5]));
                    if (s != null) line(ctx, s[0], s[1], s[2], s[3], p.color());
                }
            }
        }
    }

    private static Vector4f[] corners(Render3D cam, Box b) {
        return new Vector4f[] {
                cam.toClip(b.minX, b.minY, b.minZ), cam.toClip(b.maxX, b.minY, b.minZ),
                cam.toClip(b.maxX, b.minY, b.maxZ), cam.toClip(b.minX, b.minY, b.maxZ),
                cam.toClip(b.minX, b.maxY, b.minZ), cam.toClip(b.maxX, b.maxY, b.minZ),
                cam.toClip(b.maxX, b.maxY, b.maxZ), cam.toClip(b.minX, b.maxY, b.maxZ)};
    }

    private static float[] clip(Vector4f a, Vector4f b) {
        float near = Math.max(EPSILON, LINE_NEAR);
        boolean aIn = a.w() > near, bIn = b.w() > near;
        if (!aIn && !bIn) return null;
        Vector4f ca = a, cb = b;
        if (!aIn) ca = lerp(a, b, (near - a.w()) / (b.w() - a.w()));
        else if (!bIn) cb = lerp(a, b, (near - a.w()) / (b.w() - a.w()));
        float[] pa = Render3D.toGui(ca), pb = Render3D.toGui(cb);
        return new float[] {pa[0], pa[1], pb[0], pb[1]};
    }

    private static float[] bounds(Vector4f[] c) {
        float[] r = null;
        for (int[] e : EDGES) {
            float[] s = clip(c[e[0]], c[e[1]]);
            if (s == null) continue;
            if (r == null) r = new float[] {s[0], s[1], s[0], s[1]};
            for (int i = 0; i < 4; i += 2) {
                r[0] = Math.min(r[0], s[i]);
                r[1] = Math.min(r[1], s[i + 1]);
                r[2] = Math.max(r[2], s[i]);
                r[3] = Math.max(r[3], s[i + 1]);
            }
        }
        return r;
    }

    private static Vector4f lerp(Vector4f a, Vector4f b, float t) {
        return new Vector4f(a.x() + (b.x() - a.x()) * t, a.y() + (b.y() - a.y()) * t,
                a.z() + (b.z() - a.z()) * t, a.w() + (b.w() - a.w()) * t);
    }

    public static void rect(DrawContext ctx, float x, float y, float w, float h, int color) {
        if (!(w > 0) || !(h > 0) || w > 100000 || h > 100000) return;
        var m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(x, y);
        m.scale(w, h);
        ctx.fill(0, 0, 1, 1, color);
        m.popMatrix();
    }

    public static void line(DrawContext ctx, float x1, float y1, float x2, float y2, int color) {
        float dx = x2 - x1, dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.01f || !Float.isFinite(len) || len > 10000f) return;
        var m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(x1, y1);
        m.rotate((float) Math.atan2(dy, dx));
        m.translate(0, -0.5f);
        m.scale(len, 1f);
        ctx.fill(0, 0, 1, 1, color);
        m.popMatrix();
    }
}
