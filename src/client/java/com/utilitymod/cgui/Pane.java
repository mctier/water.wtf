package com.utilitymod.cgui;

import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

/** Container. Children render in add order; input hit-tests run back to front. */
public class Pane extends Widget {
    protected final List<Widget> children = new ArrayList<>();

    public <T extends Widget> T add(T widget) {
        // a parent-child cycle renders until the stack dies - refuse the trade instead
        if (widget == this || containsInSubtree(widget, this)) {
            return widget;
        }
        children.add(widget);
        widget.parent = this;
        return widget;
    }

    private boolean containsInSubtree(Widget root, Widget target) {
        if (root == target) return true;
        if (root instanceof Pane pane) {
            for (Widget c : pane.children()) {
                if (containsInSubtree(c, target)) return true;
            }
        }
        return false;
    }

    public void clear() {
        children.clear();
    }

    /** Position pass over children; containers override. Runs before each render. */
    public void layout() {}

    public List<Widget> children() {
        return children;
    }

    @Override
    public void render(DrawContext ctx, double mx, double my, float delta) {
        animHover(mx, my);
        for (Widget c : children) {
            if (c.visible) c.render(ctx, mx, my, delta);
        }
    }

    protected boolean dispatch(java.util.function.Predicate<Widget> call) {
        for (int i = children.size() - 1; i >= 0; i--) {
            Widget c = children.get(i);
            if (c.visible && call.test(c)) return true;
        }
        return false;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        return dispatch(c -> c.mouseClicked(mx, my, button));
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button) {
        return dispatch(c -> c.mouseDragged(mx, my, button));
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        return dispatch(c -> c.mouseReleased(mx, my, button));
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double v) {
        return dispatch(c -> c.mouseScrolled(mx, my, v));
    }

    @Override
    public boolean keyPressed(int key) {
        return dispatch(c -> c.keyPressed(key));
    }

    @Override
    public boolean charTyped(String ch) {
        return dispatch(c -> c.charTyped(ch));
    }

    /** First visible hovered child carrying a tooltip; panes searched depth-first. */
    public Widget tooltipAt(double mx, double my) {
        for (Widget c : children) {
            if (!c.visible) continue;
            if (c instanceof Pane pane) {
                Widget found = pane.tooltipAt(mx, my);
                if (found != null) return found;
            }
            if (c.tooltip != null && c.hover > 0.4f && c.contains(mx, my)) return c;
        }
        return null;
    }
}
