package com.utilitymod.cgui;

import com.utilitymod.gui.UiSounds;
import dev.koifih.client.render.Draw;
import dev.koifih.client.render.Text;
import net.minecraft.client.gui.DrawContext;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static com.utilitymod.cgui.CuiTheme.*;

/** Animated pill switch bound to a boolean getter/setter pair. */
public class Toggle extends Widget {
    private static final float PILL_W = 24f;
    private static final float PILL_H = 11f;

    private final String label;
    private final BooleanSupplier getter;
    private final Consumer<Boolean> setter;
    private float state;
    private int lastSeen;

    public Toggle(String label, BooleanSupplier getter, Consumer<Boolean> setter) {
        this.label = label;
        this.getter = getter;
        this.setter = setter;
        h = CuiTheme.ROW_H;
    }

    public boolean state() {
        return getter.getAsBoolean();
    }

    @Override
    public void render(DrawContext ctx, double mx, double my, float delta) {
        animHover(mx, my);
        boolean on = getter.getAsBoolean();
        state += (on ? 1f - state : -state) * 0.3f;

        Text.draw(ctx, label, x, y + h * 0.5f + TEXT_SIZE * 0.35f, TEXT_SIZE,
                CuiTheme.mix(TEXT, ACCENT, hover * 0.3f));

        float px = x + w - PILL_W;
        float py = y + (h - PILL_H) * 0.5f;
        int track = CuiTheme.mix(BG_CONTROL, ACCENT, state);
        Draw.rect(ctx, Math.round(px), Math.round(py), Math.round(PILL_W), Math.round(PILL_H), (int) (PILL_H * 0.5f), track);
        float knobTravel = PILL_W - PILL_H;
        float knobX = px + state * knobTravel;
        int knob = CuiTheme.mix(0xFF9BA0AA, 0xFFFFFFFF, state);
        Draw.rect(ctx, Math.round(knobX + 2), Math.round(py + 2), Math.round(PILL_H - 4), Math.round(PILL_H - 4), (int) ((PILL_H - 4) * 0.5f), knob);
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
