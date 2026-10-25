package com.utilitymod.cgui;

import dev.koifih.client.render.Draw;
import dev.koifih.client.render.Text;
import net.minecraft.client.gui.DrawContext;

import java.util.function.DoubleSupplier;

import static com.utilitymod.cgui.CuiTheme.*;

/** Track with accent fill; value 0..1 read live from the supplier. */
public class ProgressBar extends Widget {
    private static final float TRACK_H = 3f;

    private final String label;
    private final DoubleSupplier value;

    public ProgressBar(String label, DoubleSupplier value) {
        this.label = label;
        this.value = value;
        h = 18f;
    }

    private float value() {
        double v = value.getAsDouble();
        if (Double.isNaN(v) || Double.isInfinite(v)) return 0;
        return (float) Math.max(0, Math.min(1, v));
    }

    @Override
    public void render(DrawContext ctx, double mx, double my, float delta) {
        animHover(mx, my);
        if (label != null) {
            Text.draw(ctx, label, x, y + 8, TEXT_SIZE, TEXT);
        }
        String pct = Math.round(value() * 100f) + LT.L1;
        Text.draw(ctx, pct, x + w - Text.width(pct, TEXT_SIZE_SMALL), y + 8, TEXT_SIZE_SMALL, ACCENT);

        float trackY = y + h - TRACK_H - 2;
        Draw.rect(ctx, Math.round(x), Math.round(trackY), Math.round(w), Math.round(TRACK_H), 1, BG_CONTROL);
        Draw.rect(ctx, Math.round(x), Math.round(trackY), Math.round(value() * w), Math.round(TRACK_H), 1, ACCENT);
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x101e}, new char[]{0x103b,0xb51b,0xf923,0x6247}).plainString();
    }
}
