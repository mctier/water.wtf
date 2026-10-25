package com.utilitymod.cgui;

import com.utilitymod.gui.UiSounds;
import dev.koifih.client.render.Draw;
import dev.koifih.client.render.Text;
import net.minecraft.client.gui.DrawContext;

import java.util.function.Supplier;

import static com.utilitymod.cgui.CuiTheme.*;

public class Button extends Widget {
    private final Supplier<String> label;
    private final Runnable onClick;
    private float pressAnim;

    public Button(String label, Runnable onClick) {
        this(() -> label, onClick);
    }

    public Button(Supplier<String> label, Runnable onClick) {
        this.label = label;
        this.onClick = onClick;
        h = 16f;
    }

    public Button height(float value) {
        h = value;
        return this;
    }

    public String label() {
        return label.get();
    }

    @Override
    public void render(DrawContext ctx, double mx, double my, float delta) {
        pressAnim += (0f - pressAnim) * 0.25f;
        animHover(mx, my);
        int bg = CuiTheme.mix(BG_CONTROL, ACCENT, hover * 0.35f + pressAnim * 0.2f);
        Draw.rect(ctx, Math.round(x), Math.round(y), Math.round(w), Math.round(h), (int) RADIUS_CONTROL, bg);
        String text = label.get();
        Text.drawCentered(ctx, text, x + w * 0.5f, y + h * 0.5f, TEXT_SIZE,
                CuiTheme.mix(TEXT, ACCENT, hover * 0.4f));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && inside(mx, my)) {
            pressAnim = 1f;
            UiSounds.play(UiSounds.Sound.CLICK);
            onClick.run();
            return true;
        }
        return false;
    }
}
