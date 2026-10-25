package com.utilitymod.echo.render;

import com.utilitymod.echo.render3.api.FramebufferTarget;
import dev.koifih.client.util.Colors;

import java.util.ArrayList;
import java.util.List;

/**
 * Floating ghost bodies (player-model parts) used by Kill Effects and Teleport Effects. Rendered
 * from the world-render hook into the projected 3D target: levitate with ease-in-out, fade in,
 * hold, dissolve. Styles: wireframe (outline + faint fill) or chams (solid translucent fill).
 */
public final class Ghosts {
    public enum Style { WIREFRAME, CHAMS }

    private record Ghost(double x, double y, double z, float yaw, float scale, boolean humanoid, float w, float h,
                         long born, float lifeMs, float rise, int color, Style style) {}

    private static final List<Ghost> LIVE = new ArrayList<>();

    private static final float[][] PARTS = {
            {-4, 24, -4, 4, 32, 4},    // head
            {-4, 12, -2, 4, 24, 2},    // torso
            {-8, 12, -2, -4, 24, 2},   // right arm
            {4, 12, -2, 8, 24, 2},     // left arm
            {-4, 0, -2, 0, 12, 2},     // right leg
            {0, 0, -2, 4, 12, 2},      // left leg
    };

    private Ghosts() {}

    public static synchronized void spawn(double x, double y, double z, float yaw, float height, float width,
                                          boolean humanoid, float seconds, float rise, int color, Style style) {
        if (LIVE.size() > 24) LIVE.remove(0);
        LIVE.add(new Ghost(x, y, z, yaw, height / 1.8f, humanoid, width, height, System.currentTimeMillis(),
                seconds * 1000f, rise, color, style));
    }

    private static float easeInOutCubic(float t) {
        return t < 0.5f ? 4 * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 3) / 2;
    }

    private static float smooth(float e0, float e1, float x) {
        float t = Math.max(0f, Math.min(1f, (x - e0) / (e1 - e0)));
        return t * t * (3 - 2 * t);
    }

    public static synchronized void render(FramebufferTarget d) {
        if (LIVE.isEmpty()) return;
        long now = System.currentTimeMillis();
        LIVE.removeIf(g -> now - g.born() > g.lifeMs());
        for (Ghost g : LIVE) {
            float t = Math.min(1f, (now - g.born()) / g.lifeMs());
            float lift = easeInOutCubic(t) * g.rise() + (float) Math.sin(t * Math.PI * 2) * 0.04f;
            float alpha = smooth(0f, 0.12f, t) * (1f - smooth(0.4f, 1f, t));
            if (alpha < 0.01f) continue;
            boolean chams = g.style() == Style.CHAMS;
            int line = Colors.withAlpha(g.color(), alpha * (chams ? 0.55f : 1f));
            int fill = Colors.withAlpha(g.color(), (chams ? 0.32f : 0.07f) * alpha);
            float rot = (float) Math.toRadians(-g.yaw());
            float unit = g.scale() / 16f;
            double by = g.y() + lift;
            if (g.humanoid()) {
                for (float[] p : PARTS) part(d, g.x(), by, g.z(), rot, unit, p, line, fill);
            } else {
                float hw = g.w() / 2f / unit, hh = g.h() / unit;
                part(d, g.x(), by, g.z(), rot, unit, new float[] {-hw, 0, -hw, hw, hh, hw}, line, fill);
            }
        }
    }

    private static void part(FramebufferTarget d, double ox, double oy, double oz, float rot, float unit, float[] p, int line, int fill) {
        double cx = (p[0] + p[3]) / 2f * unit, cz = (p[2] + p[5]) / 2f * unit;
        double cos = Math.cos(rot), sin = Math.sin(rot);
        double rx = ox + cx * cos - cz * sin, rz = oz + cx * sin + cz * cos;
        double hw = Math.min(p[3] - p[0], p[5] - p[2]) / 2f * unit;
        d.box(rx - hw, oy + p[1] * unit, rz - hw, hw * 2, (p[4] - p[1]) * unit, hw * 2, fill);
        double[][] c = new double[8][3];
        for (int i = 0; i < 8; i++) {
            float lx = ((i & 1) == 0 ? p[0] : p[3]) * unit;
            float ly = ((i & 2) == 0 ? p[1] : p[4]) * unit;
            float lz = ((i & 4) == 0 ? p[2] : p[5]) * unit;
            c[i][0] = ox + lx * cos - lz * sin;
            c[i][1] = oy + ly;
            c[i][2] = oz + lx * sin + lz * cos;
        }
        int[][] edges = {{0, 1}, {2, 3}, {4, 5}, {6, 7}, {0, 2}, {1, 3}, {4, 6}, {5, 7}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
        for (int[] e : edges) d.line(c[e[0]][0], c[e[0]][1], c[e[0]][2], c[e[1]][0], c[e[1]][1], c[e[1]][2], line);
    }
}
