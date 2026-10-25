package dev.koifih.client.ui.component.popup;

import dev.koifih.client.render.Text;
import dev.koifih.client.ui.Theme;
import dev.koifih.client.util.Colors;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.KeyInput;

import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public final class ColorPicker extends RowPopup {
    private static final float WINDOW_GAP = 4f;

    private final IntSupplier get;
    private final HsvWindow window;
    private IntSupplier secondary = () -> 0;
    private BooleanSupplier gradient = () -> false;

    public ColorPicker(int x, int y, int width, int height, float scale, net.minecraft.text.Text label,
                       IntSupplier get, IntConsumer set) {
        super(x, y, width, height, scale, label);
        this.get = get;
        this.window = new HsvWindow(this, get, set);
    }

    public void gradient(IntSupplier get, IntConsumer set, BooleanSupplier enabled) {
        secondary = get;
        gradient = enabled;
        window.gradient(get, set, enabled);
    }

    @Override
    protected void onOpen() {
        window.readColor();
    }

    @Override
    protected float popupWidth() {
        return Math.min(getWidth(), px(HsvWindow.WIDTH));
    }

    @Override
    protected float popupX() {
        return getRight() - popupWidth();
    }

    @Override
    protected float popupHeight() {
        return px(HsvWindow.HEIGHT);
    }

    @Override
    protected boolean opensBelow() {
        return getBottom() + px(WINDOW_GAP) + popupHeight() <= bottomLimit();
    }

    @Override
    protected float popupY() {
        return opensBelow() ? getBottom() + px(WINDOW_GAP) : getY() - px(WINDOW_GAP) - popupHeight();
    }

    @Override
    protected void drawRowValue(DrawContext graphics, float rightLimit, float available) {
        float swatch = px(9);
        if (gradient.getAsBoolean()) {
            rect(graphics, rightLimit - swatch, centerY() - swatch / 2, swatch, swatch, px(2), Colors.opaque(secondary.getAsInt()));
            rect(graphics, rightLimit - 2 * swatch - px(3), centerY() - swatch / 2, swatch, swatch, px(2), Colors.opaque(get.getAsInt()));
            return;
        }
        float size = px(7);
        String label = Colors.hex(get.getAsInt());
        float labelWidth = Text.width(label, size);
        text(graphics, label, rightLimit - labelWidth, centerY(), size, Theme.TEXT);
        rect(graphics, rightLimit - labelWidth - px(5) - swatch, centerY() - swatch / 2, swatch, swatch, px(2),
                Colors.opaque(get.getAsInt()));
    }

    @Override
    protected void drawPopup(DrawContext graphics, float x, float y, float width, float height,
                             float shown, int mouseX, int mouseY) {
        window.draw(graphics, x, y, shown, !opensBelow());
    }

    @Override
    protected void clickPopup(double x, double y) {
        window.click(x, y, popupX(), popupY());
    }

    @Override
    protected void dragPopup(double x, double y) {
        window.drag(x, y, popupX(), popupY());
    }

    @Override
    protected void releasePopup() {
        window.release();
    }

    @Override
    protected void popupKeyPressed(KeyInput event) {
        window.key(event);
    }

}
