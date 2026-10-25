package com.utilitymod.gui;

/** Standalone: compile alongside CardGuiMotion.java; no Minecraft or test framework required. */
public final class CardGuiMotionTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void near(float actual, float expected, float epsilon, String message) {
        check(Math.abs(actual - expected) <= epsilon, message + ": " + actual + " != " + expected);
    }

    private static void frame(CardGuiMotion motion, float x, float y, float scale, float dt) {
        motion.update(x, y, 100 * scale, 80 * scale, 584 * scale, 390 * scale, scale, dt, true, false);
    }

    private static CardGuiMotion hold(int fps) {
        CardGuiMotion m = new CardGuiMotion();
        for (int i = 0; i < fps; i++) frame(m, 684, 275, 1f, 1f / fps);
        return m;
    }

    public static void main(String[] args) {
        CardGuiMotion m = new CardGuiMotion();
        for (int i = 0; i < 120; i++) frame(m, 392, 275, 1f, 1f / 60);
        near(m.x(), 0, 0.0001f, "center has no horizontal pressure");
        near(m.y(), 0, 0.0001f, "center has no vertical pressure");
        m = hold(60);
        check(m.x() < -4.99f && m.x() >= -5f, "right edge pushes panel left");
        near(m.y(), 0, 0.0001f, "right-center does not move vertically");
        near(hold(30).x(), m.x(), 0.0001f, "30/60 FPS agreement");
        near(hold(144).x(), m.x(), 0.0001f, "144/60 FPS agreement");

        float frozenX = m.x();
        m.update(100, 80, 100, 80, 584, 390, 1, 0.05f, true, true);
        near(m.x(), frozenX, 0f, "captured input freezes actual origin");
        for (int i = 0; i < 120; i++) frame(m, -1000, -1000, 1f, 1f / 60);
        near(m.x(), 0, 0.0001f, "release settles to base");
        near(m.y(), 0, 0.0001f, "release settles vertically");

        for (float scale : new float[]{0.25f, 0.5f, 1f, 2f, 3f}) {
            m.reset();
            for (int i = 0; i < 500; i++) {
                float x = (i % 100 < 50 ? 100 : 684) * scale;
                float y = (i % 100 < 50 ? 80 : 470) * scale;
                frame(m, x, y, scale, i % 7 == 0 ? 5f : 1f / 60);
                check(Float.isFinite(m.x()) && Float.isFinite(m.y()), "finite across frame stalls");
                check(Math.abs(m.x()) <= 5f * scale && Math.abs(m.y()) <= 5f * scale, "bounded at every scale");
            }
            m.reset();
            for (int i = 0; i < 60; i++) frame(m, 100 * scale, 80 * scale, scale, 1f / 60);
            check(m.x() > 0 && m.y() > 0, "top-left corner pushes down/right");
        }
        m.update(0, 0, 100, 80, 584, 390, 1, 0.016f, false, false);
        near(m.x(), 0, 0, "reduced motion resets immediately");
        near(m.y(), 0, 0, "reduced motion resets both axes");
        m.update(Float.NaN, Float.NaN, 100, 80, 584, 390, 1, Float.NaN, true, false);
        check(Float.isFinite(m.x()) && Float.isFinite(m.y()), "invalid mouse/delta do not poison state");
        m.update(100, 80, 100, 80, 0, 390, 1, 0.016f, true, false);
        near(m.x(), 0, 0, "invalid layout resets");
        System.out.println("PASS: edge direction, corners, center, 30/60/144 FPS, bounds, stalls, settle, freeze, reduced motion and invalid inputs");
    }
}
