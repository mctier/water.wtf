package com.utilitymod.cgui;

import net.minecraft.client.gui.DrawContext;

/** Any floating surface that renders above windows through the host screen
 *  popup layer (dropdown lists, color pickers). */
public interface Popup {
    /** Hit area including the expanded body. */
    boolean openHit(double mx, double my);

    boolean contains(double mx, double my);

    boolean mouseClicked(double mx, double my, int button);

    default boolean mouseDragged(double mx, double my, int button) {
        return false;
    }

    default void mouseReleased(int button) {}

    void bodyRender(DrawContext ctx, double mx, double my);

    default boolean keyPressed(int key) {
        return false;
    }

    void close();

    Window window();
}
