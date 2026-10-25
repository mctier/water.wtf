package com.utilitymod.echo.utils;

import com.utilitymod.echo.render3.api.FramebufferTarget;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public class DrawUtils implements Imports {

    private static final double TAU = Math.PI * 2.0;

    public static void drawOutlinedCircle(
        FramebufferTarget draw,
        Vec3d center,
        Vec3d normal,
        double radius,
        int segments,
        int color
    ) {
        if (draw == null || center == null || normal == null || segments < 3 || radius <= 0.0) return;

        Vec3d n = normal.normalize();
        if (n.lengthSquared() < 1.0E-6) {
            n = new Vec3d(0.0, 1.0, 0.0);
        }

        Vec3d reference = Math.abs(n.y) < 0.99 ? new Vec3d(0.0, 1.0, 0.0) : new Vec3d(1.0, 0.0, 0.0);
        Vec3d tangent = n.crossProduct(reference).normalize();
        if (tangent.lengthSquared() < 1.0E-6) {
            tangent = new Vec3d(1.0, 0.0, 0.0);
        }
        Vec3d bitangent = n.crossProduct(tangent).normalize();

        for (int i = 0; i < segments; i++) {
            double angleA = TAU * i / segments;
            double angleB = TAU * (i + 1) / segments;

            Vec3d from = center
                .add(tangent.multiply(Math.cos(angleA) * radius))
                .add(bitangent.multiply(Math.sin(angleA) * radius));
            Vec3d to = center
                .add(tangent.multiply(Math.cos(angleB) * radius))
                .add(bitangent.multiply(Math.sin(angleB) * radius));

            draw.line(from.x, from.y, from.z, to.x, to.y, to.z, color);
        }
    }

    public static void drawBoxOutline(FramebufferTarget draw, Box box, int color) {
        double minX = box.minX;
        double minY = box.minY;
        double minZ = box.minZ;
        double maxX = box.maxX;
        double maxY = box.maxY;
        double maxZ = box.maxZ;

        draw.line(minX, minY, minZ, maxX, minY, minZ, color);
        draw.line(maxX, minY, minZ, maxX, minY, maxZ, color);
        draw.line(maxX, minY, maxZ, minX, minY, maxZ, color);
        draw.line(minX, minY, maxZ, minX, minY, minZ, color);

        draw.line(minX, maxY, minZ, maxX, maxY, minZ, color);
        draw.line(maxX, maxY, minZ, maxX, maxY, maxZ, color);
        draw.line(maxX, maxY, maxZ, minX, maxY, maxZ, color);
        draw.line(minX, maxY, maxZ, minX, maxY, minZ, color);

        draw.line(minX, minY, minZ, minX, maxY, minZ, color);
        draw.line(maxX, minY, minZ, maxX, maxY, minZ, color);
        draw.line(maxX, minY, maxZ, maxX, maxY, maxZ, color);
        draw.line(minX, minY, maxZ, minX, maxY, maxZ, color);
    }
}
