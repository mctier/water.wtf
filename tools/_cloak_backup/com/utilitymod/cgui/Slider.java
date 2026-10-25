package com.utilitymod.cgui;

import com.utilitymod.gui.UiSounds;
import dev.koifih.client.render.Draw;
import dev.koifih.client.render.Text;
import net.minecraft.client.gui.DrawContext;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

import static com.utilitymod.cgui.CuiTheme.*;

/** Drag slider with snap-to-step and click-to-jump. Label left, value right. */
public class Slider extends Widget {
    private static final float TRACK_H = 3f;

    private final String label;
    private final double min, max, step;
    private final DoubleSupplier getter;
    private final DoubleConsumer setter;
    private final String suffix;
    private boolean dragging;

    public Slider(String label, double min, double max, double step,
                  DoubleSupplier getter, DoubleConsumer setter, String suffix) {
        this.label = label;
        this.min = min;
        this.max = max;
        this.step = step;
        this.getter = getter;
        this.setter = setter;
        this.suffix = suffix == null ? "" : suffix;
        h = 22f;
    }

    private double fraction() {
        double v = getter.getAsDouble();
        double range = max - min;
        if (range <= 0) return 0;
        return Math.max(0, Math.min(1, (v - min) / range));
    }

    private double valueAt(double mx) {
        double t = Math.max(0, Math.min(1, (mx - x) / w));
        double raw = min + t * (max - min);
        double snapped = Math.round(raw / step) * step;
        return Math.max(min, Math.min(max, snapped));
    }

    private String valueText() {
        double v = getter.getAsDouble();
        String body = step >= 1 ? String.valueOf((long) Math.round(v)) : String.format("%.2f", v);
        return body + suffix;
    }

    @Override
    public void render(DrawContext ctx, double mx, double my, float delta) {
        animHover(mx, my);
        Text.draw(ctx, label, x, y + 8, TEXT_SIZE, CuiTheme.mix(TEXT, ACCENT, hover * 0.3f));

        String v = valueText();
        float vw = Text.width(v, TEXT_SIZE_SMALL);
        Text.draw(ctx, v, x + w - vw, y + 8, TEXT_SIZE_SMALL, ACCENT);

        float trackY = y + h - TRACK_H - 4;
        int track = dragging || inside(mx, my) ? CuiTheme.mix(BG_CONTROL, ACCENT, 0.25f) : BG_CONTROL;
        Draw.rect(ctx, Math.round(x), Math.round(trackY), Math.round(w), Math.round(TRACK_H), 1, track);
        float fill = (float) (fraction() * w);
        Draw.rect(ctx, Math.round(x), Math.round(trackY), Math.round(fill), Math.round(TRACK_H), 1, ACCENT);
        float knobX = (float) (x + fraction() * w);
        Draw.rect(ctx, Math.round(knobX - 3), Math.round(trackY - 2), 6, Math.round(TRACK_H + 4), 2, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && inside(mx, my)) {
            dragging = true;
            setter.accept(valueAt(mx));
            UiSounds.play(UiSounds.Sound.HOVER);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button) {
        if (!dragging) return false;
        double next = valueAt(mx);
        if (Math.abs(next - getter.getAsDouble()) >= step / 2) setter.accept(next);
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = false;
        return false;
    }
}
