package com.utilitymod.cgui;

import com.utilitymod.gui.UiSounds;
import dev.koifih.client.render.Draw;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Host screen: owns the window list, click-to-front z-order and the input bus.
 * Follows the house law - fixed draw scale regardless of game GUI scale, all
 * widget math in virtual units, no per-frame allocation.
 */
public class CuiScreen extends net.minecraft.client.gui.screen.Screen {
    protected final MinecraftClient client = MinecraftClient.getInstance();
    private final List<Window> windows = new ArrayList<>();
    private final List<Popup> popups = new ArrayList<>();
    public Widget focused;

    public CuiScreen(String title) {
        super(Text.literal(title));
        screenKey = title;
    }

    /** Stable key the layout store is keyed by. */
    public final String screenKey;

    public Window window(String title, float x, float y, float width) {
        Window win = new Window(title, x, y, width);
        win.screen = this;
        windows.add(win);
        return win;
    }

    public void toFront(Window win) {
        if (windows.remove(win)) windows.add(win);
    }

    public void remove(Window win) {
        windows.remove(win);
        win.screen = null;
    }

    public void focus(Widget widget) {
        focused = widget;
    }

    public void addPopup(Popup popup) {
        popups.remove(popup);
        popups.add(popup);
    }

    public void removePopup(Popup popup) {
        popups.remove(popup);
    }

    public List<Window> windows() {
        return windows;
    }

    /** Draw scale: looks like GUI scale 2 regardless of the game's GUI scale. */
    public float s() {
        double f = client != null ? client.getWindow().getScaleFactor() : 2;
        return (float) Math.max(0.4, Math.min(1.0, 2.0 / f));
    }

    public float vw() {
        return width / s();
    }

    public float vh() {
        return height / s();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        Draw.rect(ctx, 0, 0, this.width, this.height, 0, 0x66050609);
        double mx = mouseX / s(), my = mouseY / s();
        for (Window win : windows) {
            win.screen = this;
            attachScreen(win);
            win.render(ctx, mx, my, delta);
        }
        for (Popup popup : popups) {
            popup.bodyRender(ctx, mx, my);
        }
        Window top = frontmost(mx, my);
        if (top != null && focused == null) {
            Widget tip = top.tooltipAt(mx, my);
            if (tip != null) drawTooltip(ctx, mx, my, tip.tooltip);
        }
        super.render(ctx, mouseX, mouseY, delta);
    }

    private void drawTooltip(DrawContext ctx, double mx, double my, String text) {
        dev.koifih.client.render.Text tip = null;
        java.util.List<String> lines = dev.koifih.client.render.Text.wrap(text, 150, CuiTheme.TEXT_SIZE_SMALL, 4);
        float w = 0;
        for (String line : lines) w = Math.max(w, dev.koifih.client.render.Text.width(line, CuiTheme.TEXT_SIZE_SMALL) + 10);
        float h = lines.size() * 10 + 4;
        float tx = (float) mx + 8, ty = (float) my + 8;
        if (tx + w > vw() - 4) tx = (float) mx - w - 8;
        if (ty + h > vh() - 4) ty = (float) my - h - 8;
        dev.koifih.client.render.Draw.rect(ctx, (int) tx, (int) ty, (int) w, (int) h, 5, 0xF0101216);
        dev.koifih.client.render.Draw.rect(ctx, (int) tx, (int) ty, 2, (int) h, 1, CuiTheme.ACCENT);
        float ly = ty + 6;
        for (String line : lines) {
            dev.koifih.client.render.Text.draw(ctx, line, tx + 6, ly, CuiTheme.TEXT_SIZE_SMALL, 0xFFD8DAE0);
            ly += 10;
        }
    }

    private Window frontmost(double mx, double my) {
        for (int i = windows.size() - 1; i >= 0; i--) {
            if (windows.get(i).contains(mx, my)) return windows.get(i);
        }
        return null;
    }

    /** Seed the screen back-ref through the whole tree (popups, focus, tooltips need it). */
    private void attachScreen(Widget widget) {
        if (!(widget instanceof Pane pane)) return;
        for (Widget child : pane.children()) {
            child.screen = this;
            attachScreen(child);
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubleClick) {
        double mx = click.x() / s(), my = click.y() / s();
        for (int i = popups.size() - 1; i >= 0; i--) {
            Popup popup = popups.get(i);
            if (popup.openHit(mx, my) || popup.contains(mx, my)) {
                toFront(popup.window());
                popup.mouseClicked(mx, my, click.button());
                return true;
            }
            popup.close();
        }
        if (focused != null && !focused.contains(mx, my)) focused = null;
        Window hit = frontmost(mx, my);
        if (hit != null) {
            toFront(hit);
            if (hit.mouseClicked(mx, my, click.button())) return true;
            if (my <= hit.y + Window.HEADER) return true;
        }
        return super.mouseClicked(click, doubleClick);
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        double mx = click.x() / s(), my = click.y() / s();
        for (int i = popups.size() - 1; i >= 0; i--) {
            if (popups.get(i).mouseDragged(mx, my, click.button())) return true;
        }
        if (!windows.isEmpty() && windows.get(windows.size() - 1).mouseDragged(mx, my, click.button())) return true;
        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean mouseReleased(Click click) {
        double mx = click.x() / s(), my = click.y() / s();
        for (Window win : windows) win.mouseReleased(mx, my, click.button());
        for (Popup popup : popups) popup.mouseReleased(click.button());
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        Window hit = frontmost(mx / s(), my / s());
        if (hit != null && hit.mouseScrolled(mx / s(), my / s(), v)) return true;
        return super.mouseScrolled(mx, my, h, v);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        if (focused != null && focused.keyPressed(key)) return true;
        for (int i = popups.size() - 1; i >= 0; i--) {
            popups.get(i).keyPressed(key);
        }
        for (int i = windows.size() - 1; i >= 0; i--) {
            if (windows.get(i).keyPressed(key)) return true;
        }
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            client.setScreen(null);
            UiSounds.play(UiSounds.Sound.CLOSE);
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        String str = input.asString();
        if (focused != null && focused.charTyped(str)) return true;
        for (int i = windows.size() - 1; i >= 0; i--) {
            if (windows.get(i).charTyped(str)) return true;
        }
        return super.charTyped(input);
    }

    @Override
    public void removed() {
        for (Window win : windows) CuiLayout.remember(screenKey, win);
        CuiLayout.save();
        super.removed();
    }
}
