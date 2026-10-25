package dev.koifih.client.ui.component;

import dev.koifih.client.render.Text;
import dev.koifih.client.ui.Theme;
import dev.koifih.client.util.Time;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.gui.Click;

import org.lwjgl.glfw.GLFW;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class TextInput extends Control {
    private static final long BLINK_MILLIS = 500L;

    private final int maxLength;
    private final Supplier<String> get;
    private final Consumer<String> set;
    private int cursor;
    private boolean selectAll;
    private String placeholder = "";

    public TextInput(int x, int y, int width, int height, float scale, net.minecraft.text.Text label,
                     int maxLength, Supplier<String> get, Consumer<String> set) {
        super(x, y, width, height, scale, label);
        this.maxLength = maxLength;
        this.get = get;
        this.set = set;
    }

    @Override
    public boolean capturesInput() {
        return active && visible && isFocused();
    }

    public void highlightAll() {
        cursor = get.get().length();
        selectAll = true;
    }

    @Override
    public void dismiss() {
        selectAll = false;
    }

    @Override
    public void draw(DrawContext graphics, int mouseX, int mouseY, float delta) {
        String value = get.get();
        float size = px(7);
        float textX = getX() + px(7);
        field(graphics);
        if (value.isEmpty()) text(graphics, fit(placeholder, getWidth() - px(14), size), textX, centerY(), size, Theme.MUTED);
        else text(graphics, fit(value, getWidth() - px(14), size), textX, centerY(), size, selectAll ? Theme.ACCENT : Theme.TEXT);
        boolean blinkVisible = Time.blink(BLINK_MILLIS);
        if (isFocused() && blinkVisible) {
            String beforeCursor = value.substring(0, Math.min(cursor, value.length()));
            float cursorX = Math.min(getRight() - px(5), textX + Text.width(beforeCursor, size));
            rect(graphics, cursorX, centerY() - px(4), Math.max(0.5f, scale), px(8), 0, Theme.ACCENT);
        }
    }

    @Override
    public void onClick(Click event, boolean doubleClick) {
        cursor = get.get().length();
        selectAll = false;
    }

    @Override
    public boolean keyPressed(KeyInput event) {
        if (!active || !isFocused()) return false;
        String value = get.get();
        int key = event.key();
        cursor = Math.clamp(cursor, 0, value.length());
        if (isSelectAll(event)) {
            selectAll = true;
        } else if (isCopy(event)) {
            MinecraftClient.getInstance().keyboard.setClipboard(value);
        } else if (isPaste(event)) {
            insert(MinecraftClient.getInstance().keyboard.getClipboard());
        } else if ((event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT)) {
            cursor = Math.max(0, cursor - 1);
            selectAll = false;
        } else if ((event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT)) {
            cursor = Math.min(value.length(), cursor + 1);
            selectAll = false;
        } else if (key == GLFW.GLFW_KEY_HOME) {
            cursor = 0;
        } else if (key == GLFW.GLFW_KEY_END) {
            cursor = value.length();
        } else if (key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE) {
            delete(key == GLFW.GLFW_KEY_BACKSPACE, value);
        } else {
            return super.keyPressed(event);
        }
        return true;
    }

    private void delete(boolean backspace, String value) {
        if (selectAll) {
            set.accept("");
            cursor = 0;
            selectAll = false;
        } else if (backspace && cursor > 0) {
            set.accept(value.substring(0, cursor - 1) + value.substring(cursor));
            cursor--;
        } else if (!backspace && cursor < value.length()) {
            set.accept(value.substring(0, cursor) + value.substring(cursor + 1));
        }
    }

    private void insert(String typed) {
        StringBuilder clean = new StringBuilder();
        typed.codePoints().filter(c -> c >= 32 && c <= 255 && c != 127).forEach(clean::appendCodePoint);
        String value = get.get();
        if (selectAll) {
            value = "";
            cursor = 0;
            selectAll = false;
        }
        cursor = Math.clamp(cursor, 0, value.length());
        String added = clean.substring(0, Math.min(clean.length(), Math.max(0, maxLength - value.length())));
        set.accept(value.substring(0, cursor) + added + value.substring(cursor));
        cursor += added.length();
    }

    @Override
    public boolean charTyped(CharInput event) {
        if (!active || !isFocused()) return false;
        insert(event.asString());
        return true;
    }


    public void setPlaceholder(String placeholder) { this.placeholder = placeholder; }
}
