package com.utilitymod.cgui;

import com.utilitymod.gui.UiSounds;
import dev.koifih.client.render.Draw;
import dev.koifih.client.render.Text;
import net.minecraft.client.gui.DrawContext;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static com.utilitymod.cgui.CuiTheme.*;

/** Square check with an accent fill when on. */
public class Checkbox extends Widget {
    private static final float BOX = 10f;

    private final String label;
    private final BooleanSupplier getter;
    private final Consumer<Boolean> setter;
    private float state;

    public Checkbox(String label, BooleanSupplier getter, Consumer<Boolean> setter) {
        this.label = label;
        this.getter = getter;
        this.setter = setter;
        h = CuiTheme.ROW_H;
    }

    @Override
    public void render(DrawContext ctx, double mx, double my, float delta) {
        animHover(mx, my);
        boolean on = getter.getAsBoolean();
        state += (on ? 1f - state : -state) * 0.3f;

        Draw.rect(ctx, Math.round(x), Math.round(y + (h - BOX) * 0.5f), Math.round(BOX), Math.round(BOX), 3,
                CuiTheme.mix(BG_CONTROL, ACCENT, state));
        Text.draw(ctx, label, x + BOX + 5, y + h * 0.5f + TEXT_SIZE * 0.35f, TEXT_SIZE,
                CuiTheme.mix(TEXT_MUTED, TEXT, state));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && inside(mx, my)) {
            boolean next = !getter.getAsBoolean();
            UiSounds.play(next ? UiSounds.Sound.TOGGLE_ON : UiSounds.Sound.TOGGLE_OFF);
            setter.accept(next);
            return true;
        }
        return false;
    }
}
