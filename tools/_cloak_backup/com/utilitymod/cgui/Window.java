package com.utilitymod.cgui;

import com.utilitymod.gui.UiSounds;
import dev.koifih.client.render.Draw;
import dev.koifih.client.render.Text;
import net.minecraft.client.gui.DrawContext;

import static com.utilitymod.cgui.CuiTheme.*;

/**
 * Movable, width-resizable, collapsible window. Height is content-driven - the
 * VStack inside lays out every frame, so widgets never fight for space.
 */
public class Window extends Pane {
    public static final float HEADER = 24f;
    private static final float MIN_W = 140f;
    public static final float GRIP = 12f;

    private final String title;
    public final VStack content = new VStack().pad(PAD).gap(GAP);
    public Runnable onClose;
    private boolean collapsed;
    private float collapseAnim;
    private boolean draggingTitle;
    private float grabX, grabY;
    private boolean resizing;
    private float resizeStartW;

    public Window(String title, float x, float y, float width) {
        this.title = title;
        this.x = x;
        this.y = y;
        this.w = width;
        this.h = HEADER + 40;
        // add() routes widgets into content - the constructor must place content in the
        // window's own child space (tooltip traversal) without re-feeding it back into itself.
        super.add(content);
    }

    public <T extends Widget> T add(T widget) {
        content.add(widget);
        return widget;
    }

    public String getTitle() {
        return title;
    }

    public boolean isCollapsed() {
        return collapsed;
    }

    public void toggleCollapsed() {
        collapsed = !collapsed;
        UiSounds.play(collapsed ? UiSounds.Sound.TOGGLE_OFF : UiSounds.Sound.TOGGLE_ON);
    }

    @Override
    public void render(DrawContext ctx, double mx, double my, float delta) {
        collapseAnim += (collapsed ? 1f - collapseAnim : -collapseAnim) * 0.3f;
        float shownW = Math.max(MIN_W, w);
        float bodyH = contentHeight();
        h = HEADER + bodyH;
        float shownH = HEADER + bodyH * (1f - collapseAnim);

        Draw.rect(ctx, Math.round(x), Math.round(y), Math.round(shownW), Math.round(shownH), (int) RADIUS_WINDOW, BG_WINDOW);

        boolean headHover = my >= y && my <= y + HEADER && mx >= x && mx <= x + shownW;
        int headBg = CuiTheme.mix(BG_CARD, ACCENT, headHover ? hover * 0.15f : 0f);
        Draw.rect(ctx, Math.round(x), Math.round(y), Math.round(shownW), Math.round(HEADER), (int) RADIUS_WINDOW, headBg);

        float base = y + HEADER * 0.5f + 2f;
        Text.draw(ctx, title, x + 8, base, TEXT_SIZE, TEXT);

        float cx = x + shownW - 8;
        float cw = Text.width("x", TEXT_SIZE);
        boolean closeHover = mx >= cx - cw - 2 && my >= y && my <= y + HEADER;
        Text.draw(ctx, "x", cx - cw, base, TEXT_SIZE, closeHover ? DANGER : TEXT_MUTED);

        float mxg = cx - cw - 10;
        String minGlyph = collapsed ? "+" : "-";
        float mw = Text.width(minGlyph, TEXT_SIZE);
        boolean minHover = mx >= mxg - mw - 2 && mx <= mxg + 2 && my >= y && my <= y + HEADER;
        Text.draw(ctx, minGlyph, mxg - mw, base, TEXT_SIZE, minHover ? ACCENT : TEXT_MUTED);

        if (!collapsed) {
            content.x = x;
            content.y = y + HEADER;
            content.w = shownW;
            content.render(ctx, mx, my, delta);
            boolean gripHover = mx >= x + shownW - GRIP && my >= y + shownH - GRIP;
            int grip = CuiTheme.mix(BG_WINDOW, ACCENT, gripHover ? 0.6f : 0.15f);
            Draw.rect(ctx, Math.round(x + shownW - 6), Math.round(y + shownH - 6), 4, 4, 1, grip);
        }
        animHover(mx, my);
    }

    private float contentHeight() {
        float total = content.pad() * 2;
        boolean any = false;
        for (Widget c : content.children()) {
            if (!c.visible) continue;
            total += c.h + content.gap();
            any = true;
        }
        return any ? total - content.gap() + PAD : content.pad() * 2;
    }

    @Override
    public boolean contains(double mx, double my) {
        if (collapsed) {
            return mx >= x && mx <= x + Math.max(MIN_W, w) && my >= y && my <= y + HEADER;
        }
        return mx >= x && mx <= x + Math.max(MIN_W, w) && my >= y && my <= y + Math.max(HEADER, h);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return false;
        if (closeHit(mx, my)) {
            if (onClose != null) onClose.run();
            return true;
        }
        if (minHit(mx, my)) {
            toggleCollapsed();
            return true;
        }
        float shownW = Math.max(MIN_W, w);
        float shownH = HEADER + contentHeight() * (1f - collapseAnim);
        boolean grip = !collapsed && mx >= x + shownW - GRIP && my >= y + shownH - GRIP;
        if (grip) {
            resizing = true;
            grabX = (float) mx;
            resizeStartW = w;
            return true;
        }
        if (my <= y + HEADER) {
            draggingTitle = true;
            grabX = (float) (mx - x);
            grabY = (float) (my - y);
            return true;
        }
        return false;
    }

    private boolean closeHit(double mx, double my) {
        float cx = x + Math.max(MIN_W, w) - 8;
        float cw = Text.width("x", TEXT_SIZE);
        return mx >= cx - cw - 2 && my >= y && my <= y + HEADER;
    }

    private boolean minHit(double mx, double my) {
        float cx = x + Math.max(MIN_W, w) - 8;
        float cw = Text.width("x", TEXT_SIZE);
        float mxg = cx - cw - 10;
        float mw = Text.width(collapsed ? "+" : "-", TEXT_SIZE);
        return mx >= mxg - mw - 2 && mx <= mxg + 2 && my >= y && my <= y + HEADER;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button) {
        if (draggingTitle) {
            if (screen != null) {
                x = (float) Math.max(0, Math.min(screen.vw() - Math.max(MIN_W, w), mx - grabX));
                y = (float) Math.max(0, Math.min(screen.vh() - HEADER, my - grabY));
            } else {
                x = (float) Math.max(0, mx - grabX);
                y = (float) Math.max(0, my - grabY);
            }
            return true;
        }
        if (resizing) {
            w = (float) Math.max(MIN_W, Math.min(640f, resizeStartW + (mx - grabX)));
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        draggingTitle = false;
        resizing = false;
        return false;
    }
}
