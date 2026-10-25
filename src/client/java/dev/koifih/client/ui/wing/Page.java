package dev.koifih.client.ui.wing;

import dev.koifih.client.ui.component.popup.Popup;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.gui.Click;
import java.util.List;

public interface Page {
    void init(PanelLayout layout);

    void draw(DrawContext graphics, int mouseX, int mouseY, float delta);

    void setState(boolean shown, boolean interactive);

    default void relayout(PanelLayout layout) {}

    default List<Popup> popups() {
        return List.of();
    }

    default boolean mouseClicked(Click event, boolean doubleClick) {
        return false;
    }

    default boolean keyPressed(KeyInput event) {
        return false;
    }

    default boolean mouseScrolled(double x, double y, double dx, double dy) {
        return false;
    }

    default void reset() {}
}
