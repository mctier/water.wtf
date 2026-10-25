package dev.koifih.client.ui.component;

import dev.koifih.client.render.Switch;
import dev.koifih.client.ui.Transition;
import net.minecraft.client.gui.DrawContext;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class Bool extends Control {
    private final net.minecraft.text.Text label;
    private final BooleanSupplier get;
    private final Transition thumb;

    public Bool(int x, int y, int width, int height, float scale, net.minecraft.text.Text label,
                BooleanSupplier get, Consumer<Boolean> set) {
        super(x, y, width, height, scale, label, () -> set.accept(!get.getAsBoolean()));
        this.label = label;
        this.get = get;
        this.thumb = new Transition(get.getAsBoolean() ? 1f : 0f, 170, 170,
                Transition.Easing.EASE_OUT_SETTLE, Transition.Easing.EASE_OUT_CUBIC);
    }

    /** Card GUI look: rounded-rect track, square thumb (light off, dark on). */
    public boolean cardStyle;

    @Override
    public void draw(DrawContext graphics, int mouseX, int mouseY, float delta) {
        thumb.set(get.getAsBoolean() ? 1f : 0f);
        if (cardStyle) {
            float t = Math.clamp(thumb.value(), 0f, 1f);
            int accent = com.utilitymod.gui.CardGuiStyle.accent;
            rect(graphics, getX(), getY(), getWidth(), getHeight(), px(4), dev.koifih.client.util.Colors.lerp(0x55200A1C, accent, t));
            float inset = px(3), kn = getHeight() - 2 * inset;
            rect(graphics, getX() + inset + (getWidth() - kn - 2 * inset) * t, getY() + inset, kn, kn, px(2),
                    dev.koifih.client.util.Colors.lerp(0xFFDCD6E2, 0xFF1B0A17, t));
            return;
        }
        Switch.draw(graphics, getX(), getY(), getWidth(), getHeight(), Math.clamp(thumb.value(), 0f, 1f), thumb.flight());
    }

}
