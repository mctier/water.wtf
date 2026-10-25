package com.utilitymod.cgui;

import com.utilitymod.gui.UiSounds;
import dev.koifih.client.render.Draw;
import dev.koifih.client.render.Text;
import net.minecraft.client.gui.DrawContext;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.utilitymod.cgui.CuiTheme.*;

/** Typed picker. Collapsed it is a label + value box; open, the option list
 *  renders through the host screen popup layer so it layers above siblings. */
public class Dropdown extends Widget implements Popup {
    private final String label;
    private final Supplier<List<String>> options;
    private final Supplier<String> current;
    private final Consumer<String> pick;
    private boolean open;
    private Window host;

    public Dropdown(String label, Supplier<List<String>> options, Supplier<String> current, Consumer<String> pick) {
        this.label = label;
        this.options = options;
        this.current = current;
        this.pick = pick;
        h = 22f;
    }

    public Dropdown window(Window host) {
        this.host = host;
        return this;
    }

    public boolean openHit(double mx, double my) {
        return open && mx >= x && mx <= x + w && my >= y && my <= y + h + listH();
    }

    public boolean open() {
        return open;
    }

    public Window window() {
        if (host != null) return host;
        for (Widget p = parent; p != null; p = p.parent) {
            if (p instanceof Window win) return win;
        }
        return null;
    }

    public void close() {
        open = false;
        if (screen != null) screen.removePopup(this);
    }

    public void toggleOpen() {
        if (open) {
            close();
            return;
        }
        open = true;
        if (screen != null) screen.addPopup(this);
        UiSounds.play(UiSounds.Sound.DROPDOWN);
    }

    public void bodyRender(DrawContext ctx, double mx, double my) {
        if (!open) return;
        List<String> opts = options.get();
        float ly = y + h;
        Draw.rect(ctx, Math.round(x), Math.round(ly), Math.round(w), Math.round(listH()), (int) RADIUS_CONTROL, 0xF3101317);
        for (int i = 0; i < opts.size(); i++) {
            String opt = opts.get(i);
            boolean rowHover = mx >= x && mx <= x + w && my >= ly && my <= ly + 14f;
            if (rowHover) {
                Draw.rect(ctx, Math.round(x), Math.round(ly), Math.round(w), 15, 0, CuiTheme.mix(0xF3101317, ACCENT, 0.22f));
            }
            Text.draw(ctx, opt, x + 6, ly + 8, TEXT_SIZE_SMALL, rowHover ? ACCENT : TEXT);
            ly += 15;
            if (i > 15) break;
        }
    }

    private float listH() {
        List<String> opts = options.get();
        return opts == null ? 0 : Math.min(opts.size(), 16) * 15f + 4;
    }

    private String currentText() {
        String cur = current.get();
        return cur == null ? "" : cur;
    }

    @Override
    public void render(DrawContext ctx, double mx, double my, float delta) {
        animHover(mx, my);
        Text.draw(ctx, label, x, y + 8, TEXT_SIZE, CuiTheme.mix(TEXT, ACCENT, hover * 0.3f));

        float boxY = y + 10;
        float boxH = 12f;
        int boxBg = open ? CuiTheme.mix(BG_CONTROL, ACCENT, 0.25f)
                : CuiTheme.mix(BG_CONTROL, ACCENT, hover * 0.3f);
        Draw.rect(ctx, Math.round(x), Math.round(boxY), Math.round(w), Math.round(boxH), 3, boxBg);
        Text.draw(ctx, currentText(), x + 5, boxY + boxH * 0.5f + 2f, TEXT_SIZE_SMALL, TEXT);
        String glyph = open ? "^" : "v";
        Text.draw(ctx, glyph, x + w - Text.width(glyph, TEXT_SIZE_SMALL) - 5, boxY + boxH * 0.5f + 2f, TEXT_SIZE_SMALL, open ? ACCENT : TEXT_MUTED);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return false;
        if (open) {
            List<String> opts = options.get();
            float ly = y + h;
            for (int i = 0; i < opts.size() && ly <= y + h + listH(); i++) {
                String opt = opts.get(i);
                if (mx >= x && mx <= x + w && my >= ly && my <= ly + 15f) {
                    pick.accept(opt);
                    UiSounds.play(UiSounds.Sound.CLICK);
                    close();
                    return true;
                }
                ly += 15;
            }
            close();
            return true;
        }
        if (inside(mx, my)) {
            toggleOpen();
            return true;
        }
        return false;
    }
}
