package dev.koifih.client.render;
import net.minecraft.client.gui.DrawContext;

/**
 * Scissor stack on {@link DrawContext#enableScissor}. Same nesting semantics
 * as adin's version (intersection of nested clips), but bounds travel with
 * the call because vanilla scissor state lives on the context, not in a
 * detached render state.
 */
public final class Scissor {
    private static final int[] EMPTY = {0, 0, 0, 0};
    private static int[] current;

    public static int[] current() {
        return current;
    }

    public static void clip(DrawContext graphics, int x, int y, int width, int height, Runnable draw) {
        int[] previous = current;
        int[] area = {x, y, Math.max(0, width), Math.max(0, height)};
        int[] intersection = previous == null ? area : intersect(previous, area);
        if (intersection == null) intersection = EMPTY;
        current = intersection;
        enable(graphics, intersection);
        try {
            draw.run();
        } finally {
            current = previous;
            // DrawContext owns a real scissor stack. Popping once restores
            // the parent's clip; pushing `previous` here left clips active
            // after the outer scope and hid the sidebar/panel chrome.
            graphics.disableScissor();
        }
    }

    private static void enable(DrawContext graphics, int[] area) {
        graphics.enableScissor(area[0], area[1], area[0] + area[2], area[1] + area[3]);
    }

    private static int[] intersect(int[] a, int[] b) {
        int x = Math.max(a[0], b[0]);
        int y = Math.max(a[1], b[1]);
        int w = Math.min(a[0] + a[2], b[0] + b[2]) - x;
        int h = Math.min(a[1] + a[3], b[1] + b[3]) - y;
        if (w <= 0 || h <= 0) return null;
        return new int[]{x, y, w, h};
    }

    private Scissor() {}
}
