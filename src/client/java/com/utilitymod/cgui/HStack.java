package com.utilitymod.cgui;

/** Horizontal flow. Visible children split the row evenly, keeping their own heights. */
public class HStack extends Pane {
    private float pad = 0f;
    private float gap = CuiTheme.GAP;

    public HStack pad(float value) {
        pad = value;
        return this;
    }

    public HStack gap(float value) {
        gap = value;
        return this;
    }

    @Override
    public void layout() {
        int count = 0;
        for (Widget c : children) if (c.visible) count++;
        if (count == 0) return;
        float cell = (w - pad * 2 - gap * (count - 1)) / count;
        float cx = x + pad;
        for (Widget c : children) {
            if (!c.visible) continue;
            c.x = cx;
            c.y = y;
            c.w = cell;
            cx += cell + gap;
        }
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button) {
        layout();
        return super.mouseDragged(mx, my, button);
    }
}
