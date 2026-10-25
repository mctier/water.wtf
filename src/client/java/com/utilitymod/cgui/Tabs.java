package com.utilitymod.cgui;

import com.utilitymod.gui.UiSounds;
import dev.koifih.client.render.Draw;
import dev.koifih.client.render.Text;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

import static com.utilitymod.cgui.CuiTheme.*;

/** Row of tab cells. Active cell gets the accent underline; click selects and fires. */
public class Tabs extends Widget {
    private final List<Tab> tabs = new ArrayList<>();
    private int selected;

    public Tabs(String... names) {
        for (String name : names) tabs.add(new Tab(name));
        h = 18f;
        tooltip = null;
    }

    public Tabs on(String name, Runnable onSelect) {
        for (Tab tab : tabs) {
            if (tab.name.equals(name)) tab.onSelect = onSelect;
        }
        return this;
    }

    public String selected() {
        return tabs.isEmpty() ? "" : tabs.get(selected).name;
    }

    public int selectedIndex() {
        return selected;
    }

    @Override
    public void render(DrawContext ctx, double mx, double my, float delta) {
        int count = tabs.size();
        if (count == 0) return;
        float cell = w / count;
        for (int i = 0; i < count; i++) {
            Tab tab = tabs.get(i);
            float cx = x + cell * i;
            boolean hoverCell = mx >= cx && mx <= cx + cell && my >= y && my <= y + h;
            tab.hover += (hoverCell ? 1f - tab.hover : -tab.hover) * 0.3f;
            boolean active = i == selected;
            if (hoverCell) tab.hover = Math.max(tab.hover, active ? 1f : tab.hover + 0.4f);
            String label = tab.name;
            float tw = Text.width(label, TEXT_SIZE);
            Text.draw(ctx, label, cx + (cell - tw) * 0.5f, y + h * 0.5f + 2f, TEXT_SIZE,
                    CuiTheme.mix(TEXT_MUTED, ACCENT, active ? 1f : tab.hover * 0.7f));
            if (active) {
                Draw.rect(ctx, Math.round(cx + 6), Math.round(y + h - 2), Math.round(cell - 12), 2, 1, ACCENT);
            } else if (hoverCell) {
                Draw.rect(ctx, Math.round(cx + 6), Math.round(y + h - 2), Math.round(cell - 12), 2, 1,
                        CuiTheme.mix(0, ACCENT, 0.4f));
            }
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0 || tabs.isEmpty()) return false;
        float cell = w / tabs.size();
        for (int i = 0; i < tabs.size(); i++) {
            if (mx >= x + cell * i && mx <= x + cell * (i + 1) && my >= y && my <= y + h) {
                if (selected != i) {
                    selected = i;
                    UiSounds.play(UiSounds.Sound.TAB);
                    Tab tab = tabs.get(i);
                    if (tab.onSelect != null) tab.onSelect.run();
                }
                return true;
            }
        }
        return false;
    }

    private static final class Tab {
        final String name;
        Runnable onSelect;
        float hover;

        Tab(String name) {
            this.name = name;
        }
    }
}
