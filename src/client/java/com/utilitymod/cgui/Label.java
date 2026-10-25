package com.utilitymod.cgui;

import dev.koifih.client.render.Text;
import net.minecraft.client.gui.DrawContext;

import static com.utilitymod.cgui.CuiTheme.*;

public class Label extends Widget {
    private String text;
    private float size;
    private int color;
    private boolean centered;

    public Label(String text) {
        this(text, TEXT_SIZE, TEXT, false);
    }

    public Label(String text, float size, int color, boolean centered) {
        this.text = text;
        this.size = size;
        this.color = color;
        this.centered = centered;
        h = size + 6;
    }

    public Label text(String value) {
        text = value;
        return this;
    }

    public Label color(int value) {
        color = value;
        return this;
    }

    public String text() {
        return text;
    }

    @Override
    public void render(DrawContext ctx, double mx, double my, float delta) {
        if (centered) {
            Text.drawCentered(ctx, text, x + w * 0.5f, y + h * 0.5f, size, color);
        } else {
            Text.draw(ctx, text, x, y + h * 0.5f + size * 0.35f, size, color);
        }
    }
}
