package dev.koifih.client.ui.component;
import net.minecraft.client.util.InputUtil;
import dev.koifih.client.render.Text;
import dev.koifih.client.ui.Theme;
import dev.koifih.client.ui.Transition;
import dev.koifih.client.util.Colors;
import dev.koifih.client.util.Lang;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.gui.Click;

import org.lwjgl.glfw.GLFW;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class Keybind extends Control {
    private final Supplier<InputUtil.Key> get;
    private final Consumer<InputUtil.Key> set;
    private boolean capturing;
    private boolean clearable = true;
    private final Transition listening = new Transition(0f, 120);

    public Keybind(int x, int y, int width, int height, float scale, net.minecraft.text.Text label,
                   Supplier<InputUtil.Key> get, Consumer<InputUtil.Key> set) {
        super(x, y, width, height, scale, label);
        this.get = get;
        this.set = set;
    }

    @Override
    public boolean capturesInput() {
        return active && visible && capturing;
    }

    @Override
    public void dismiss() {
        capturing = false;
    }

    public boolean captureMouse(Click event) {
        if (!capturesInput()) return false;
        set.accept(InputUtil.Type.MOUSE.createFromCode(event.button()));
        capturing = false;
        return true;
    }

    private String display() {
        if (capturing) {
            int dots = 1 + (int) ((System.nanoTime() / 350_000_000L) % 3);
            return LT.L1.repeat(dots);
        }
        InputUtil.Key key = get.get();
        return key == InputUtil.UNKNOWN_KEY ? Lang.get(LT.L2) : key.getLocalizedText().getString();
    }

    @Override
    public void draw(DrawContext graphics, int mouseX, int mouseY, float delta) {
        listening.set(capturing ? 1f : 0f);
        float amount = listening.value();
        rect(graphics, getX(), getY(), getWidth(), getHeight(), px(3),
                Colors.lerp(Theme.CONTROL, Theme.CONTROL_ACTIVE, amount));
        float size = px(7);
        String label = display();
        float labelWidth = Text.width(label, size);
        float available = getWidth() - px(8);
        int color = Colors.lerp(Theme.TEXT, Theme.ACCENT, amount);
        if (labelWidth <= available) {
            text(graphics, label, getX() + (getWidth() - labelWidth) / 2, centerY(), size, color);
        } else {
            float edge = getRight() - px(4);
            Text.drawFaded(graphics, label, getX() + px(4), Text.centeredBaseline(label, size, centerY()),
                    size, color, edge, edge - px(12));
        }
    }

    @Override
    public void onClick(Click event, boolean doubleClick) {
        capturing = true;
    }

    @Override
    public boolean keyPressed(KeyInput event) {
        if (!active || !isFocused()) return false;
        if (capturing) {
            int key = event.key();
            boolean clear = key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE;
            if (!clear) set.accept(InputUtil.Type.KEYSYM.createFromCode(key));
            else if (clearable) set.accept(InputUtil.UNKNOWN_KEY);
            capturing = false;
            return true;
        }
        if (isSelection(event)) {
            capturing = true;
            return true;
        }
        return super.keyPressed(event);
    }


    public void setClearable(boolean clearable) { this.clearable = clearable; }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xcaa6}, new char[]{0xca88,0x4baa,0xd5ec,0x68ac}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xca33,0xf5c9,0x461e,0xefa2}, new char[]{0x4670,0xf5a6,0xca5d,0xefc7}).plainString();
    }
}
