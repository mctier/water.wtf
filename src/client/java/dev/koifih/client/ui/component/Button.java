package dev.koifih.client.ui.component;

import dev.koifih.client.render.AdinIcon;
import dev.koifih.client.render.Draw;
import dev.koifih.client.render.Text;
import dev.koifih.client.ui.Theme;
import net.minecraft.client.gui.DrawContext;

import java.util.function.IntSupplier;

public final class Button extends Control {
    private enum Style { FILLED, TILE, ICON }

    private final Style style;
    private final int icon;
    private AdinIcon adinIcon;
    private IntSupplier backdrop;

    public Button(int x, int y, int width, int height, float scale, net.minecraft.text.Text label, Runnable action) {
        this(x, y, width, height, scale, label, Style.FILLED, -1, action);
    }

    private Button(int x, int y, int width, int height, float scale, net.minecraft.text.Text label, Style style, int icon, Runnable action) {
        super(x, y, width, height, scale, label, action);
        this.style = style;
        this.icon = icon;
    }

    public static Button tile(int x, int y, int width, int height, float scale, net.minecraft.text.Text label, int icon, Runnable action) {
        return new Button(x, y, width, height, scale, label, Style.TILE, icon, action);
    }

    public static Button icon(int x, int y, int size, float scale, int icon, net.minecraft.text.Text label, Runnable action) {
        return new Button(x, y, size, size, scale, label, Style.ICON, icon, action);
    }

    public static Button icon(int x, int y, int size, float scale, AdinIcon icon, IntSupplier backdrop,
                              net.minecraft.text.Text label, Runnable action) {
        Button button = new Button(x, y, size, size, scale, label, Style.ICON, -1, action);
        button.adinIcon = icon;
        button.backdrop = backdrop;
        return button;
    }

    public static int preferredWidth(String label, float scale) {
        return (int) Math.ceil(Text.width(label, 7 * scale) + 24 * scale);
    }

    @Override
    public void draw(DrawContext graphics, int mouseX, int mouseY, float delta) {
        switch (style) {
            case ICON -> {
                float iconSize = getWidth() * 0.7f;
                float iconX = getX() + (getWidth() - iconSize) / 2;
                float iconY = getY() + (getHeight() - iconSize) / 2;
                if (adinIcon != null) {
                    Draw.icon(graphics, adinIcon, iconX, iconY, iconSize, Theme.DIM, Theme.ACCENT, Theme.TEXT, backdrop.getAsInt());
                } else {
                    Draw.icon(graphics, icon, iconX, iconY, iconSize, Theme.DIM);
                }
            }
            case TILE -> {
                rect(graphics, getX(), getY(), getWidth(), getHeight(), px(5), Theme.ROW);
                float iconSize = px(10);
                Draw.icon(graphics, icon, getX() + (getWidth() - iconSize) / 2, getY() + (getHeight() - iconSize) / 2, iconSize, Theme.DIM);
            }
            case FILLED -> {
                rect(graphics, getX(), getY(), getWidth(), getHeight(), px(5), Theme.ACCENT);
                float size = px(7);
                String label = fit(getMessage().getString(), getWidth() - px(12), size);
                text(graphics, label, getX() + (getWidth() - Text.width(label, size)) / 2, centerY(), size, Theme.ON_ACCENT);
            }
        }
    }
}
