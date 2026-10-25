package dev.koifih.client.render;
import net.minecraft.client.gui.DrawContext;
import org.joml.Matrix3x2fStack;

/**
 * Real matrix transforms via {@link DrawContext#getMatrices()}. Same API
 * shapes as adin's version; rotation degrades to a plain translate (unused
 * by the wing path).
 */
public final class Transform {
    public static void translated(DrawContext graphics, float dx, float dy, Runnable draw) {
        Matrix3x2fStack pose = graphics.getMatrices();
        pose.pushMatrix();
        try {
            pose.translate(dx, dy);
            draw.run();
        } finally {
            pose.popMatrix();
        }
    }

    public static void scaledAbout(DrawContext graphics, float centerX, float centerY, float zoom, Runnable draw) {
        Matrix3x2fStack pose = graphics.getMatrices();
        pose.pushMatrix();
        try {
            pose.translate(centerX, centerY);
            pose.scale(zoom, zoom);
            pose.translate(-centerX, -centerY);
            draw.run();
        } finally {
            pose.popMatrix();
        }
    }

    public static void rotatedAbout(DrawContext graphics, float radians, float centerX, float centerY, Runnable draw) {
        translated(graphics, 0f, 0f, draw);
    }

    public static void popIn(DrawContext graphics, float centerX, float centerY, float shown, float minZoom, Runnable draw) {
        popIn(graphics, centerX, centerY, shown, minZoom, 0f, 0f, draw);
    }

    public static void popIn(DrawContext graphics, float centerX, float centerY, float shown, float minZoom,
                             float slideX, float slideY, Runnable draw) {
        float zoom = minZoom + (1f - minZoom) * shown;
        float fade = Math.clamp(shown, 0f, 1f);
        float away = 1f - fade;
        translated(graphics, slideX * away, slideY * away,
                () -> scaledAbout(graphics, centerX, centerY, zoom, () -> Opacity.with(fade, draw)));
    }

    private Transform() {}
}
