package dev.koifih.client.ui.component;

import dev.koifih.client.render.Draw;
import dev.koifih.client.render.Text;
import dev.koifih.client.ui.Theme;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import org.lwjgl.glfw.GLFW;

/**
 * Plain (non-widget) base for adin UI controls on 1.21.11 Yarn.
 *
 * <p>The original extends 26.x {@code AbstractWidget}; here the owning screen
 * routes input manually (focus tracked by the screen), so this is a simple
 * bounds + state holder. Same public surface otherwise.
 */
public abstract class Control {
    protected int x;
    protected int y;
    protected int width;
    protected int height;
    protected net.minecraft.text.Text message;
    protected final float scale;
    public boolean active = true;
    public boolean visible = true;
    private boolean focused;
    private final Runnable action;

    protected Control(int x, int y, int width, int height, float scale, net.minecraft.text.Text label) {
        this(x, y, width, height, scale, label, null);
    }

    protected Control(int x, int y, int width, int height, float scale, net.minecraft.text.Text label, Runnable action) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.scale = scale;
        this.message = label;
        this.action = action;
    }

    public boolean capturesInput() {
        return false;
    }

    public void dismiss() {
    }

    public void draw(DrawContext graphics, int mouseX, int mouseY, float delta) {
    }

    public boolean charTyped(CharInput event) {
        return false;
    }

    public boolean mouseScrolled(double x, double y, double hAmount, double vAmount) {
        return false;
    }

    public boolean mouseClicked(Click event, boolean doubleClick) {
        if (!active || !visible) return false;
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && isMouseOver(event.x(), event.y())) {
            onClick(event, doubleClick);
            return true;
        }
        return false;
    }

    protected void activate() {
        if (action != null) action.run();
    }

    public void onClick(Click event, boolean doubleClick) {
        activate();
    }

    public void onRelease(Click event) {
    }

    public void onDrag(Click event, double dx, double dy) {
    }

    public boolean keyPressed(KeyInput event) {
        if (action != null && active && focused && isSelection(event)) {
            activate();
            return true;
        }
        return false;
    }

    public static boolean isSelection(KeyInput event) {
        return event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER
                || event.key() == GLFW.GLFW_KEY_SPACE;
    }

    public static boolean isLeft(KeyInput event) {
        return event.key() == GLFW.GLFW_KEY_LEFT;
    }

    public static boolean isRight(KeyInput event) {
        return event.key() == GLFW.GLFW_KEY_RIGHT;
    }

    public static boolean isUp(KeyInput event) {
        return event.key() == GLFW.GLFW_KEY_UP;
    }

    public static boolean isDown(KeyInput event) {
        return event.key() == GLFW.GLFW_KEY_DOWN;
    }

    public static boolean hasShiftDown(KeyInput event) {
        return (event.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
    }

    public static boolean isSelectAll(KeyInput event) {
        return event.key() == GLFW.GLFW_KEY_A && (event.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0;
    }

    public static boolean isCopy(KeyInput event) {
        return event.key() == GLFW.GLFW_KEY_C && (event.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0;
    }

    public static boolean isPaste(KeyInput event) {
        return event.key() == GLFW.GLFW_KEY_V && (event.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0;
    }

    public float px(float units) {
        return units * scale;
    }

    protected float centerY() {
        return getY() + getHeight() * 0.5f;
    }

    public void rect(DrawContext graphics, float x, float y, float width, float height, float radius, int color) {
        Draw.rect(graphics, x, y, width, Math.max(1, Math.round(height)), Math.max(0, Math.round(radius)), color);
    }

    public void text(DrawContext graphics, String text, float x, float centerY, float size, int color) {
        Text.drawCentered(graphics, text, x, centerY, size, color);
    }

    protected void field(DrawContext graphics) {
        rect(graphics, getX(), getY(), getWidth(), getHeight(), px(4), Theme.FIELD);
    }

    protected static String fit(String text, float width, float size) {
        return Text.fit(text, width, size);
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getRight() { return x + width; }
    public int getBottom() { return y + height; }
    public void setX(int x) { this.x = x; }
    public void setY(int y) { this.y = y; }
    public void setWidth(int width) { this.width = width; }
    public void setHeight(int height) { this.height = height; }
    public net.minecraft.text.Text getMessage() { return message; }
    public void setMessage(net.minecraft.text.Text message) { this.message = message; }
    public boolean isFocused() { return focused; }
    public void setFocused(boolean focused) { this.focused = focused; }
    public boolean isHovered(double mouseX, double mouseY) {
        return isMouseOver(mouseX, mouseY);
    }

    public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
