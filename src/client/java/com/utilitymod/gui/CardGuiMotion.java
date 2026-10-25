package com.utilitymod.gui;

/** Bounded, frame-rate independent edge pressure for the card GUI's actual layout origin. */
public final class CardGuiMotion {
    private static final float MAX_PUSH = 5f;
    private static final float EDGE_BAND = 28f;
    private static final float OUTSIDE_HALO = 12f;
    private static final double SPRING_FREQUENCY = 16.0;
    private static final double MAX_STEP = 1.0 / 120.0;
    private double offsetX, offsetY, velocityX, velocityY;

    public float x() { return (float) offsetX; }
    public float y() { return (float) offsetY; }

    public void reset() {
        offsetX = offsetY = velocityX = velocityY = 0.0;
    }

    /** Coordinates are scaled GUI pixels; targets always use the stationary base rectangle. */
    public void update(float mouseX, float mouseY, float baseX, float baseY,
                       float width, float height, float scale, float dt,
                       boolean enabled, boolean frozen) {
        if (!enabled || !Float.isFinite(scale) || scale <= 0f
                || !Float.isFinite(width) || !Float.isFinite(height) || width <= 0f || height <= 0f
                || !Float.isFinite(baseX) || !Float.isFinite(baseY)) {
            reset();
            return;
        }
        if (frozen) {
            // Preserve hitboxes throughout a captured drag, without storing a release impulse.
            velocityX = velocityY = 0.0;
            return;
        }
        if (!Float.isFinite(dt) || dt <= 0f) return;
        double targetX = 0.0, targetY = 0.0;
        float halo = OUTSIDE_HALO * scale;
        if (Float.isFinite(mouseX) && Float.isFinite(mouseY)
                && mouseX >= baseX - halo && mouseX <= baseX + width + halo
                && mouseY >= baseY - halo && mouseY <= baseY + height + halo) {
            targetX = pressure(mouseX - baseX, width, scale) * MAX_PUSH * scale;
            targetY = pressure(mouseY - baseY, height, scale) * MAX_PUSH * scale;
        }
        double remaining = Math.min(0.05, dt);
        while (remaining > 1.0E-8) {
            double step = Math.min(MAX_STEP, remaining);
            double decay = Math.exp(-SPRING_FREQUENCY * step);
            // Exact critically damped spring integration; no unstable explicit Euler step.
            double dx = offsetX - targetX;
            double dy = offsetY - targetY;
            double ax = velocityX + SPRING_FREQUENCY * dx;
            double ay = velocityY + SPRING_FREQUENCY * dy;
            offsetX = targetX + (dx + ax * step) * decay;
            offsetY = targetY + (dy + ay * step) * decay;
            velocityX = (velocityX - SPRING_FREQUENCY * ax * step) * decay;
            velocityY = (velocityY - SPRING_FREQUENCY * ay * step) * decay;
            remaining -= step;
        }
        double limit = MAX_PUSH * scale;
        if (Math.abs(offsetX) > limit) { offsetX = Math.copySign(limit, offsetX); velocityX = 0.0; }
        if (Math.abs(offsetY) > limit) { offsetY = Math.copySign(limit, offsetY); velocityY = 0.0; }
    }

    private static float pressure(float coordinate, float length, float scale) {
        float left = edgeWeight(coordinate, scale);
        float right = edgeWeight(length - coordinate, scale);
        return left - right;
    }

    private static float edgeWeight(float inwardDistance, float scale) {
        float reach = (inwardDistance < 0f ? OUTSIDE_HALO : EDGE_BAND) * scale;
        float weight = Math.max(0f, 1f - Math.abs(inwardDistance) / reach);
        return weight * weight * (3f - 2f * weight);
    }
}
