package com.utilitymod.cgui;

import net.minecraft.client.gui.DrawContext;

/**
 * Base of everything drawable and clickable. Coordinates are virtual screen
 * units (raw / draw scale, same law as DropdownGui). Hover uses the house
 * delta approach - no per-frame allocation, no timers.
 */
public abstract class Widget {
    public float x, y, w, h;
    public Pane parent;
    /** Back-ref to the host screen for viewport clamping; null when detached. */
    public CuiScreen screen;
    public boolean visible = true;
    public float hover;
    /** Optional hover tooltip; the host screen draws it above all windows. */
    public String tooltip;

    protected void requestFocus() {
        if (screen != null) screen.focus(this);
    }

    protected boolean isFocused() {
        return screen != null && screen.focused == this;
    }

    protected void blur() {
        if (screen != null && screen.focused == this) screen.focused = null;
    }

    public boolean contains(double mx, double my) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    public void render(DrawContext ctx, double mx, double my, float delta) {}

    public boolean mouseClicked(double mx, double my, int button) { return false; }

    public boolean mouseDragged(double mx, double my, int button) { return false; }

    public boolean mouseReleased(double mx, double my, int button) { return false; }

    public boolean mouseScrolled(double mx, double my, double v) { return false; }

    public boolean keyPressed(int key) { return false; }

    public boolean charTyped(String ch) { return false; }

    protected boolean inside(double mx, double my) {
        return visible && contains(mx, my);
    }

    protected void animHover(double mx, double my) {
        hover += (inside(mx, my) ? 1f - hover : -hover) * 0.3f;
    }
}
