package com.utilitymod.cgui;

import com.utilitymod.gui.ModuleBinds;
import com.utilitymod.gui.UiSounds;
import dev.koifih.client.render.Draw;
import dev.koifih.client.render.Text;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

import static com.utilitymod.cgui.CuiTheme.*;

/**
 * Key + mouse-button bind box, house code law: GLFW keys pass through,
 * mouse buttons arrive as 0x80000000 | button (ModuleBinds format).
 * ESC / DEL / BKSP clear to -1.
 */
public class KeybindButton extends Widget {
    private final String label;
    private final IntSupplier getter;
    private final IntConsumer setter;
    private boolean listening;

    public KeybindButton(String label, IntSupplier getter, IntConsumer setter) {
        this.label = label;
        this.getter = getter;
        this.setter = setter;
        h = ROW_H;
    }

    public boolean listening() {
        return listening;
    }

    private String valueText() {
        int key = getter.getAsInt();
        if (key == -1) return "none";
        String name = ModuleBinds.name(key);
        return name == null || name.isEmpty() ? "key " + (key & 0x7FFFFFFF) : name;
    }

    @Override
    public void render(DrawContext ctx, double mx, double my, float delta) {
        animHover(mx, my);
        Text.draw(ctx, label, x, y + h * 0.5f + TEXT_SIZE * 0.35f, TEXT_SIZE, TEXT);

        String text = listening ? "[ press key ... ]" : valueText();
        float tw = Text.width(text, TEXT_SIZE_SMALL);
        float boxW = tw + 10;
        float boxX = x + w - boxW;
        int bg = listening ? CuiTheme.mix(BG_CONTROL, ACCENT, 0.4f)
                : CuiTheme.mix(BG_CONTROL, ACCENT, hover * 0.3f);
        Draw.rect(ctx, Math.round(boxX), Math.round(y + 1), Math.round(boxW), Math.round(h - 2), 3, bg);
        Text.draw(ctx, text, boxX + 5, y + h * 0.5f + 2f, TEXT_SIZE_SMALL, listening ? ACCENT : TEXT);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && inside(mx, my)) {
            listening = true;
            requestFocus();
            UiSounds.play(UiSounds.Sound.CLICK);
            return true;
        }
        if (listening && button >= 1 && button <= 8) {
            setter.accept(0x80000000 | button);
            listening = false;
            blur();
            UiSounds.play(UiSounds.Sound.SUCCESS);
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int key) {
        if (!listening) return false;
        boolean clear = key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_DELETE || key == GLFW.GLFW_KEY_BACKSPACE;
        setter.accept(clear ? -1 : key);
        listening = false;
        blur();
        UiSounds.play(UiSounds.Sound.SUCCESS);
        return true;
    }
}
