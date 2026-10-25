package dev.koifih.client.ui.component.popup;

import dev.koifih.client.ui.Transition;
import dev.koifih.client.ui.component.Control;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.gui.Click;

import org.lwjgl.glfw.GLFW;

public abstract class Popup extends Control {
    private boolean open;
    private final Transition reveal = new Transition(0f, 175, 110, Transition.Easing.EASE_OUT_SETTLE, Transition.Easing.EASE_IN_CUBIC);
    private int bottomLimit = Integer.MAX_VALUE;

    protected Popup(int x, int y, int width, int height, float scale, net.minecraft.text.Text label) {
        super(x, y, width, height, scale, label);
    }

    public boolean isOpen() {
        return open;
    }

    protected int bottomLimit() {
        return bottomLimit;
    }

    @Override
    public boolean capturesInput() {
        return active && visible && open;
    }

    @Override
    public void dismiss() {
        open = false;
        reveal.set(0f);
        releasePopup();
    }

    protected void open() {
        com.utilitymod.gui.UiSounds.play(com.utilitymod.gui.UiSounds.Sound.DROPDOWN);
        open = true;
        reveal.set(1f);
        onOpen();
    }

    protected float revealed() {
        return reveal.value();
    }

    protected void onOpen() {
    }

    protected float popupX() {
        return getX();
    }

    protected float popupWidth() {
        return getWidth();
    }

    protected abstract float popupHeight();

    protected float popupY() {
        float below = getBottom() + px(3);
        return below + popupHeight() <= bottomLimit ? below : getY() - popupHeight() - px(3);
    }

    protected boolean insidePopup(double x, double y) {
        return x >= popupX() && x < popupX() + popupWidth() && y >= popupY() && y < popupY() + popupHeight();
    }

    protected abstract void drawValue(DrawContext graphics);

    protected abstract void drawPopup(DrawContext graphics, float x, float y, float width, float height,
                                      float shown, int mouseX, int mouseY);

    protected abstract void clickPopup(double x, double y);

    protected void dragPopup(double x, double y) {
    }

    protected void releasePopup() {
    }

    /** Wheel hook for scrollable popups; return true when the wheel was consumed. */
    protected boolean popupScrolled(double x, double y, double dy) {
        return false;
    }

    /** Routes the wheel into this popup while it is open under the pointer. */
    public boolean scrolledWidget(double x, double y, double dy) {
        return active && visible && open && insidePopup(x, y) && popupScrolled(x, y, dy);
    }

    protected abstract void popupKeyPressed(KeyInput event);

    @Override
    public void draw(DrawContext graphics, int mouseX, int mouseY, float delta) {
        drawValue(graphics);
    }

    public void renderPopup(DrawContext graphics, int mouseX, int mouseY) {
        float shown = reveal.value();
        if (!open && shown <= 0f) return;
        graphics.createNewRootLayer();
        drawPopup(graphics, popupX(), popupY(), popupWidth(), popupHeight(), shown, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(Click event, boolean doubleClick) {
        if (!active || !visible) return false;
        if (open) {
            if (insidePopup(event.x(), event.y())) {
                clickPopup(event.x(), event.y());
                return true;
            }
            dismiss();
            return isMouseOver(event.x(), event.y());
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void onClick(Click event, boolean doubleClick) {
        open();
    }

    @Override
    public void onDrag(Click event, double dx, double dy) {
        if (open) dragPopup(event.x(), event.y());
    }

    @Override
    public void onRelease(Click event) {
        releasePopup();
    }

    @Override
    public boolean keyPressed(KeyInput event) {
        if (!active || !isFocused()) return false;
        if (open) {
            if (event.key() == GLFW.GLFW_KEY_ESCAPE || event.key() == GLFW.GLFW_KEY_TAB) dismiss();
            else popupKeyPressed(event);
            return true;
        }
        if (isSelection(event)) {
            open();
            return true;
        }
        return super.keyPressed(event);
    }

    public void setBottomLimit(int bottomLimit) { this.bottomLimit = bottomLimit; }
}
