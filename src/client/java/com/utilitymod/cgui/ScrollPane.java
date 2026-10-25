package com.utilitymod.cgui;

import dev.koifih.client.render.Draw;
import dev.koifih.client.render.Scissor;
import net.minecraft.client.gui.DrawContext;

import static com.utilitymod.cgui.CuiTheme.*;

/** Scissored vertical scroll area. Children lay out inside the clip; the
 *  wheel scrolls between content height and viewport height. */
public class ScrollPane extends Pane {
    private final float rowH;
    private float scroll;

    public ScrollPane(float height, float rowH) {
        this.h = height;
        this.rowH = rowH;
    }

    @Override
    public void layout() {
        float cy = y - scroll;
        int count = 0;
        for (Widget c : children) {
            if (!c.visible) continue;
            c.x = x;
            c.y = cy;
            c.w = w - (contentHeight() > h ? 5 : 0);
            cy += c.h + GAP;
            count++;
        }
    }

    private float contentHeight() {
        float total = 0;
        int count = 0;
        for (Widget c : children) {
            if (!c.visible) continue;
            total += c.h + GAP;
            count++;
        }
        return count > 0 ? total - GAP : 0;
    }

    private boolean clipped(Widget c) {
        return c.y >= y - rowH && c.y + c.h <= y + h + rowH;
    }

    @Override
    public void render(DrawContext ctx, double mx, double my, float delta) {
        layout();
        float max = Math.max(0, contentHeight() - h);
        scroll = Math.max(0, Math.min(max, scroll));
        if (contentHeight() > h) {
            Draw.rect(ctx, Math.round(x + w - 3), Math.round(y), 2, Math.round(h), 1,
                    CuiTheme.mix(BG_CONTROL, ACCENT, 0.4f));
            float barH = Math.max(20f, h * (h / contentHeight()));
            float barY = y + (scroll / max) * (h - barH);
            Draw.rect(ctx, Math.round(x + w - 3), Math.round(barY), 2, Math.round(barH), 1, ACCENT);
        }
        final double fmx = mx, fmy = my;
        Scissor.clip(ctx, (int) x, (int) y, (int) w, (int) h, () -> {
            for (Widget c : children) {
                if (c.visible && clipped(c)) c.render(ctx, fmx, fmy, delta);
            }
        });
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (int i = children.size() - 1; i >= 0; i--) {
            Widget c = children.get(i);
            if (c.visible && clipped(c) && c.mouseClicked(mx, my, button)) return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        for (int i = children.size() - 1; i >= 0; i--) children.get(i).mouseReleased(mx, my, button);
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button) {
        for (int i = children.size() - 1; i >= 0; i--) {
            Widget c = children.get(i);
            if (c.visible && c.mouseDragged(mx, my, button)) return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double v) {
        if (!contains(mx, my)) return false;
        float max = Math.max(0, contentHeight() - h);
        scroll = Math.max(0, Math.min(max, scroll - (float) v * rowH * 0.6f));
        return true;
    }

    @Override
    public boolean keyPressed(int key) {
        return dispatch(c -> c.keyPressed(key));
    }

    @Override
    public boolean charTyped(String ch) {
        return dispatch(c -> c.charTyped(ch));
    }
}
