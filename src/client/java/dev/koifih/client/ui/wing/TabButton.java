package dev.koifih.client.ui.wing;

import dev.koifih.client.render.Draw;
import dev.koifih.client.render.Text;
import dev.koifih.client.ui.Theme;
import dev.koifih.client.ui.Transition;
import dev.koifih.client.ui.wing.Sidebar;
import dev.koifih.client.ui.component.Control;
import dev.koifih.client.util.Colors;
import net.minecraft.client.gui.DrawContext;

import java.util.function.BooleanSupplier;
public final class TabButton extends Control {
    private static final float ICON_INSET = 5f;
    private static final float ICON_SIZE = 11f;
    private static final float TEXT_INSET = 22f;
    private static final float TEXT_SIZE = 8.5f;
    private static final float TEXT_RIGHT_PADDING = 5f;
    private static final float ICON_BOX = 13f;
    private final Sidebar.Tab tab;
    private final BooleanSupplier selected;
    private final Transition onPill = new Transition(0f, 150);

    public TabButton(int x, int y, int width, int height, float scale, Sidebar.Tab tab,
                     BooleanSupplier selected, Runnable onSelect) {
        super(x, y, width, height, scale, net.minecraft.text.Text.literal(tab.label().get()), onSelect);
        this.tab = tab;
        this.selected = selected;
    }

    public static int iconInset(float scale) {
        return Math.round(ICON_INSET * scale);
    }

    @Override
    public net.minecraft.text.Text getMessage() {
        return net.minecraft.text.Text.literal(tab.label().get());
    }

    public void setOnPill(boolean covered) {
        onPill.set(covered ? 1f : 0f);
    }

    @Override
    public void draw(DrawContext graphics, int mouseX, int mouseY, float delta) {
        float covered = onPill.value();
        int box = Math.round(ICON_BOX * scale);
        int iconX = Math.round(getX() + (ICON_INSET + ICON_SIZE / 2f) * scale - box / 2f);
        int iconY = getY() + (getHeight() - box) / 2;
        Draw.icon(graphics, tab.icon(), iconX, iconY, box, Colors.lerp(Theme.DIM, 0xFF000000, covered),
                Colors.lerp(Theme.ACCENT, 0xFF000000, covered), Colors.lerp(Theme.TEXT, 0xFF000000, covered),
                Colors.lerp(Theme.SIDEBAR, Theme.ACCENT, covered));
        String label = getMessage().getString();
        float textX = getX() + TEXT_INSET * scale;
        float textSize = TEXT_SIZE * scale;
        float available = Math.max(1, getRight() - TEXT_RIGHT_PADDING * scale - textX);
        textSize *= Math.min(1f, available / Math.max(1f, Text.width(label, textSize)));
        Text.drawCentered(graphics, label, textX, getY() + getHeight() * 0.5f, textSize,
                Colors.lerp(Theme.DIM, Theme.ON_ACCENT, covered));
    }


    public final Sidebar.Tab tab() { return tab; }
}
