package com.utilitymod.cgui;

import net.minecraft.client.gui.DrawContext;

/** Vertical flow. Children stack top to bottom at full content width. */
public class VStack extends Pane {
    private float pad = CuiTheme.PAD;
    private float gap = CuiTheme.GAP;

    public VStack pad(float value) {
        pad = value;
        return this;
    }

    public VStack gap(float value) {
        gap = value;
        return this;
    }

    public float pad() {
        return pad;
    }

    public float gap() {
        return gap;
    }

    public float contentHeight() {
        float total = pad * 2;
        int count = 0;
        for (Widget c : children) {
            if (!c.visible) continue;
            total += c.h + gap;
            count++;
        }
        return count == 0 ? total : total - gap;
    }

    @Override
    public void layout() {
        float cy = pad;
        for (Widget c : children) {
            if (!c.visible) continue;
            c.x = pad;
            c.y = cy;
            c.w = Math.max(0, w - pad * 2);
            cy += c.h + gap;
        }
    }

    @Override
    public void render(DrawContext ctx, double mx, double my, float delta) {
        layout();
        super.render(ctx, mx, my, delta);
    }
}
