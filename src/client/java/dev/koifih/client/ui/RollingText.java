package dev.koifih.client.ui;

import dev.koifih.client.render.Opacity;
import dev.koifih.client.render.Text;
import dev.koifih.client.ui.Transition.Easing;
import dev.koifih.client.util.Time;
import net.minecraft.client.gui.DrawContext;

public final class RollingText {
    private static final float ROLL_SECONDS = 0.28f;
    private static final float TRAVEL = 0.9f;

    private final Time.Stopwatch changed = new Time.Stopwatch();
    private String value = "";
    private String previous = "";

    public String value() {
        return value;
    }

    public float secondsSinceChange() {
        return changed.seconds();
    }

    public void set(String next) {
        if (next.equals(value)) return;
        previous = value;
        value = next;
        changed.reset();
    }

    public float draw(DrawContext graphics, float x, float centerY, float size, int color) {
        float rolled = Easing.EASE_OUT_CUBIC.at(changed.seconds() / ROLL_SECONDS);
        String old = previous;
        String current = value;
        if (rolled < 1f && !old.isEmpty()) Opacity.with(1f - rolled,
                () -> Text.drawCentered(graphics, old, x, centerY - size * TRAVEL * rolled, size, color));
        Opacity.with(rolled, () -> Text.drawCentered(graphics, current, x, centerY + size * TRAVEL * (1f - rolled), size, color));
        return x + Text.width(current, size);
    }
}
