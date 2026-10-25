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
        if (key == -1) return LT.L1;
        String name = ModuleBinds.name(key);
        return name == null || name.isEmpty() ? LT.L2 + (key & 0x7FFFFFFF) : name;
    }

    @Override
    public void render(DrawContext ctx, double mx, double my, float delta) {
        animHover(mx, my);
        Text.draw(ctx, label, x, y + h * 0.5f + TEXT_SIZE * 0.35f, TEXT_SIZE, TEXT);

        String text = listening ? LT.L3 : valueText();
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


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xeb5a,0xa342,0x713c,0xf3f4}, new char[]{0x7152,0xeb35,0xf39a,0xa327}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf53d,0x653c,0xd640,0xa058}, new char[]{0x6557,0xf558,0xd639,0xa078}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9df1,0xb7a4,0x90b6,0xbfb7,0x53a9,0xa746,0x5405,0xe2f4,0x75cc,0x7cc7,0x30cc,0x2e71,0x62b0,0xa78c,0x0a2a,0xdea7,0xf2bf}, new char[]{0xa71d,0x2e51,0x90c6,0x75be,0xbfd2,0x30bf,0xa7ff,0x5425,0x9d9a,0xe291,0x62c9,0x0a0a,0xde89,0x7ce9,0xf291,0x5389,0xb7f9}).plainString();
    }
}
