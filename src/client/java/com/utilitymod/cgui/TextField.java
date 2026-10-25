package com.utilitymod.cgui;

import dev.koifih.client.render.Draw;
import dev.koifih.client.render.Scissor;
import dev.koifih.client.render.Text;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

import static com.utilitymod.cgui.CuiTheme.*;

/** Single-line text field. Enter commits, ESC reverts, outside click commits. */
public class TextField extends Widget {
    private final String label;
    private final Consumer<String> onCommit;
    private final StringBuilder buffer = new StringBuilder();
    private String committed;

    public TextField(String label, String initial, Consumer<String> onCommit) {
        this.label = label;
        this.committed = initial == null ? "" : initial;
        this.onCommit = onCommit;
        h = 20f;
    }

    public String value() {
        return committed;
    }

    private void openForEdit() {
        buffer.setLength(0);
        buffer.append(committed);
        requestFocus();
    }

    private void commit() {
        committed = buffer.toString();
        onCommit.accept(committed);
        blur();
    }

    private void cancel() {
        buffer.setLength(0);
        blur();
    }

    @Override
    public void render(DrawContext ctx, double mx, double my, float delta) {
        animHover(mx, my);
        if (label != null) {
            Text.draw(ctx, label, x, y + 8, TEXT_SIZE_SMALL, TEXT_MUTED);
            float labelW = Text.width(label, TEXT_SIZE_SMALL) + 8;
            drawBox(ctx, mx, x + labelW, w - labelW);
        } else {
            drawBox(ctx, mx, x, w);
        }
    }

    private void drawBox(DrawContext ctx, double mx, float bx, float width) {
        boolean focus = isFocused();
        int bg = CuiTheme.mix(BG_CONTROL, ACCENT, focus ? 0.3f : hover * 0.2f);
        Draw.rect(ctx, Math.round(bx), Math.round(y + 2), Math.round(width), Math.round(h - 4), 3, bg);
        if (focus) {
            Draw.rect(ctx, Math.round(bx), Math.round(y + 2), 1, Math.round(h - 4), 0, ACCENT);
        }
        String text = focus ? buffer.toString() : committed;
        if (!focus && text.isEmpty()) text = LT.L1;
        Text.draw(ctx, text, bx + 5, y + h * 0.5f + 2f, TEXT_SIZE_SMALL, text.isEmpty() ? TEXT_MUTED : TEXT);
        if (focus && (System.currentTimeMillis() / 500) % 2 == 0) {
            float caretX = bx + 5 + Text.width(text, TEXT_SIZE_SMALL) + 1;
            Draw.rect(ctx, Math.round(caretX), Math.round(y + 5), 1, Math.round(h - 10), 0, ACCENT);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && inside(mx, my)) {
            if (!isFocused()) openForEdit();
            return true;
        }
        if (isFocused()) {
            commit();
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int key) {
        if (!isFocused()) return false;
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            if (buffer.length() > 0) buffer.setLength(buffer.length() - 1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            commit();
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            cancel();
            return true;
        }
        return true;
    }

    @Override
    public boolean charTyped(String ch) {
        if (!isFocused()) return false;
        if (ch != null && !ch.isEmpty() && buffer.length() < 64) buffer.append(ch);
        return true;
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7a7f,0xf745,0xe3d6,0xab82}, new char[]{0xe3f6,0x7a51,0xf76b,0xabac}).plainString();
    }
}
