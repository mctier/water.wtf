package dev.koifih.client.ui.component.popup;

import dev.koifih.client.render.Draw;
import dev.koifih.client.render.Transform;
import dev.koifih.client.ui.Theme;
import dev.koifih.client.ui.Transition;
import dev.koifih.client.ui.component.Control;
import dev.koifih.client.util.Colors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.KeyInput;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

final class HsvWindow {
    static final float WIDTH = 104f;
    private static final float MARGIN = 6f;
    private static final float SQUARE_HEIGHT = 56f;
    private static final float HUE_HEIGHT = 6f;
    private static final float BUTTON_WIDTH = 18f;
    private static final float BUTTON_HEIGHT = 12f;
    private static final float BUTTON_GAP = 4f;
    private static final int COPY_ICON = 0xe14d;
    private static final int PASTE_ICON = 0xe14f;
    private static final float SWATCH_GAP = 3f;
    private static final float HEX_SIZE = 6.5f;
    private static final int SWATCH_MILLIS = 150;
    static final float HEIGHT = MARGIN + SQUARE_HEIGHT + MARGIN + HUE_HEIGHT + MARGIN + BUTTON_HEIGHT + MARGIN;

    private enum Drag { NONE, SQUARE, HUE }

    private final Control owner;
    private final IntSupplier get;
    private final IntConsumer set;
    private IntSupplier secondaryGet = () -> 0;
    private IntConsumer secondarySet = rgb -> {};
    private BooleanSupplier gradient = () -> false;
    private boolean editingSecondary;
    private final Transition selection = new Transition(0f, SWATCH_MILLIS);
    private float hue;
    private float saturation;
    private float value;
    private Drag drag = Drag.NONE;

    HsvWindow(Control owner, IntSupplier get, IntConsumer set) {
        this.owner = owner;
        this.get = get;
        this.set = set;
        readColor();
    }

    static int hsvToRgb(float h, float s, float v) {
        float c = v * s;
        float hh = (h % 1f + 1f) % 1f * 6f;
        float x = c * (1f - Math.abs(hh % 2f - 1f));
        float r, g, b;
        switch ((int) hh) {
            case 0 -> { r = c; g = x; b = 0; }
            case 1 -> { r = x; g = c; b = 0; }
            case 2 -> { r = 0; g = c; b = x; }
            case 3 -> { r = 0; g = x; b = c; }
            case 4 -> { r = x; g = 0; b = c; }
            default -> { r = c; g = 0; b = x; }
        }
        float m = v - c;
        return (Math.round((r + m) * 255) << 16) | (Math.round((g + m) * 255) << 8) | Math.round((b + m) * 255);
    }

    void gradient(IntSupplier get, IntConsumer set, BooleanSupplier enabled) {
        secondaryGet = get;
        secondarySet = set;
        gradient = enabled;
    }

    private boolean isGradient() {
        return gradient.getAsBoolean();
    }

    private int current() {
        return editingSecondary && isGradient() ? secondaryGet.getAsInt() : get.getAsInt();
    }

    private void setCurrent(int rgb) {
        if (editingSecondary && isGradient()) secondarySet.accept(rgb);
        else set.accept(rgb);
    }

    private float px(float units) {
        return owner.px(units);
    }

    void readColor() {
        if (!isGradient()) editingSecondary = false;
        selection.set(editingSecondary ? 1f : 0f);
        int rgb = current();
        float r = Colors.red(rgb) / 255f;
        float g = ((rgb >> 8) & 255) / 255f;
        float b = (rgb & 255) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float delta = max - min;
        value = max;
        saturation = max == 0 ? 0 : delta / max;
        if (delta > 0) {
            if (max == r) hue = ((g - b) / delta) / 6f;
            else if (max == g) hue = (2f + (b - r) / delta) / 6f;
            else hue = (4f + (r - g) / delta) / 6f;
            hue = (hue + 1f) % 1f;
        }
    }

    private void writeColor() {
        setCurrent(hsvToRgb(hue, saturation, value));
    }

    void copy() {
        MinecraftClient.getInstance().keyboard.setClipboard(Colors.hex(current()));
    }

    void paste() {
        String text = MinecraftClient.getInstance().keyboard.getClipboard().trim();
        if (text.startsWith(LT.L1)) text = text.substring(1);
        else if (text.startsWith(LT.L2) || text.startsWith(LT.L3)) text = text.substring(2);
        if (text.length() != 6) return;
        try {
            setCurrent(Integer.parseInt(text, 16));
            readColor();
        } catch (NumberFormatException ignored) {
        }
    }

    void draw(DrawContext graphics, float x, float y, float shown, boolean anchoredAbove) {
        float width = px(WIDTH);
        float height = px(HEIGHT);
        float anchorX = x + width / 2f;
        float anchorY = anchoredAbove ? y + height : y;
        Transform.popIn(graphics, anchorX, anchorY, shown, 0.9f, () -> drawWindow(graphics, x, y, width, height));
    }

    private void drawWindow(DrawContext graphics, float x, float y, float width, float height) {
        Draw.bordered(graphics, x, y, width, height, px(5), Theme.POPUP, Theme.POPUP_BORDER);
        int squareX = Math.round(x + px(MARGIN));
        int squareY = Math.round(y + px(MARGIN));
        int squareWidth = Math.round(width - 2 * px(MARGIN));
        int squareHeight = Math.round(px(SQUARE_HEIGHT));
        Draw.saturationValue(graphics, squareX, squareY, squareWidth, squareHeight, Math.round(px(3)),
                hsvToRgb(hue, 1f, 1f));
        marker(graphics, squareX + saturation * squareWidth, squareY + (1f - value) * squareHeight);

        int hueY = Math.round(y + px(MARGIN + SQUARE_HEIGHT + MARGIN));
        int hueHeight = Math.round(px(HUE_HEIGHT));
        Draw.hueBar(graphics, squareX, hueY, squareWidth, hueHeight, hueHeight / 2);
        marker(graphics, squareX + hue * squareWidth, hueY + hueHeight / 2f);

        float buttonY = buttonY(y);
        button(graphics, copyX(x), buttonY, COPY_ICON);
        button(graphics, pasteX(x), buttonY, PASTE_ICON);
        if (!isGradient()) {
            owner.text(graphics, Colors.hex(current()), x + px(MARGIN), buttonY + px(BUTTON_HEIGHT) / 2f,
                    px(HEX_SIZE), Theme.DIM);
            return;
        }
        float size = px(BUTTON_HEIGHT);
        float ringX = swatchX(x, selection.value());
        owner.rect(graphics, ringX - px(1), buttonY - px(1), size + px(2), size + px(2), px(4), Theme.ACCENT);
        owner.rect(graphics, swatchX(x, 0), buttonY, size, size, px(3), Colors.opaque(get.getAsInt()));
        owner.rect(graphics, swatchX(x, 1), buttonY, size, size, px(3), Colors.opaque(secondaryGet.getAsInt()));
    }

    private float buttonY(float y) {
        return y + px(MARGIN + SQUARE_HEIGHT + MARGIN + HUE_HEIGHT + MARGIN);
    }

    private float pasteX(float x) {
        return x + px(WIDTH) - px(MARGIN + BUTTON_WIDTH);
    }

    private float copyX(float x) {
        return pasteX(x) - px(BUTTON_GAP + BUTTON_WIDTH);
    }

    private float swatchX(float x, float index) {
        return x + px(MARGIN) + index * px(BUTTON_HEIGHT + SWATCH_GAP);
    }

    private void button(DrawContext graphics, float x, float y, int icon) {
        owner.rect(graphics, x, y, px(BUTTON_WIDTH), px(BUTTON_HEIGHT), px(3), Theme.CONTROL);
        float iconSize = px(8);
        Draw.icon(graphics, icon, x + (px(BUTTON_WIDTH) - iconSize) / 2, y + (px(BUTTON_HEIGHT) - iconSize) / 2,
                iconSize, Theme.DIM);
    }

    private void marker(DrawContext graphics, float centerX, float centerY) {
        float outer = px(6);
        float inner = px(4);
        owner.rect(graphics, centerX - outer / 2, centerY - outer / 2, outer, outer, outer / 2, 0xFF101010);
        owner.rect(graphics, centerX - inner / 2, centerY - inner / 2, inner, inner, inner / 2, 0xFFFFFFFF);
    }

    void click(double mouseX, double mouseY, float x, float y) {
        float buttonY = buttonY(y);
        float squareY = y + px(MARGIN);
        float hueY = y + px(MARGIN + SQUARE_HEIGHT + MARGIN);
        if (mouseY >= buttonY && mouseY < buttonY + px(BUTTON_HEIGHT)) {
            float pasteX = pasteX(x);
            float copyX = copyX(x);
            if (mouseX >= copyX && mouseX < copyX + px(BUTTON_WIDTH)) copy();
            else if (mouseX >= pasteX && mouseX < pasteX + px(BUTTON_WIDTH)) paste();
            else if (isGradient()) selectSwatch(mouseX, x);
            drag = Drag.NONE;
            return;
        }
        if (mouseY >= squareY && mouseY < squareY + px(SQUARE_HEIGHT)) drag = Drag.SQUARE;
        else if (mouseY >= hueY - px(3) && mouseY < hueY + px(HUE_HEIGHT + 3)) drag = Drag.HUE;
        else drag = Drag.NONE;
        drag(mouseX, mouseY, x, y);
    }

    private void selectSwatch(double mouseX, float x) {
        for (int index = 0; index < 2; index++) {
            float swatchX = swatchX(x, index);
            if (mouseX >= swatchX && mouseX < swatchX + px(BUTTON_HEIGHT)) {
                editingSecondary = index == 1;
                readColor();
            }
        }
    }

    void drag(double mouseX, double mouseY, float x, float y) {
        float squareX = x + px(MARGIN);
        float squareWidth = px(WIDTH) - 2 * px(MARGIN);
        if (drag == Drag.SQUARE) {
            saturation = Math.clamp((float) ((mouseX - squareX) / squareWidth), 0f, 1f);
            value = 1f - Math.clamp((float) ((mouseY - y - px(MARGIN)) / px(SQUARE_HEIGHT)), 0f, 1f);
        } else if (drag == Drag.HUE) {
            hue = Math.clamp((float) ((mouseX - squareX) / squareWidth), 0f, 1f);
        } else {
            return;
        }
        writeColor();
    }

    void release() {
        drag = Drag.NONE;
    }

    void key(KeyInput event) {
        float step = ((event.modifiers() & org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT) != 0) ? 0.1f : 0.02f;
        if ((event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT)) hue = (hue - step + 1f) % 1f;
        else if ((event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT)) hue = (hue + step) % 1f;
        else if ((event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_UP)) value = Math.min(1f, value + step);
        else if ((event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN)) value = Math.max(0f, value - step);
        else if (dev.koifih.client.ui.component.Control.isCopy(event)) { copy(); return; }
        else if (dev.koifih.client.ui.component.Control.isPaste(event)) { paste(); return; }
        else return;
        writeColor();
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x151c}, new char[]{0x153f,0xdacc,0x25bd,0xd600}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xca1e,0x44a7}, new char[]{0xca2e,0x44df,0x5889,0x2b52}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9368,0xce5c}, new char[]{0xce6c,0x9330,0xb02d,0xf5fc}).plainString();
    }
}
