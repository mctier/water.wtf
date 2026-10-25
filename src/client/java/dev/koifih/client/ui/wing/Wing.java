package dev.koifih.client.ui.wing;

import dev.koifih.client.input.Keybinds;
import dev.koifih.client.render.Draw;
import dev.koifih.client.render.Opacity;
import dev.koifih.client.render.Scissor;
import dev.koifih.client.render.Transform;
import dev.koifih.client.ui.Theme;
import dev.koifih.client.ui.Transition;
import dev.koifih.client.ui.UiScale;
import dev.koifih.client.ui.wing.page.ModulesPage;
import dev.koifih.client.ui.component.Control;
import dev.koifih.client.ui.component.popup.Popup;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Adin wing shell adapted to 1.21.11 Yarn + late injection: single
 * modules page (+ settings menu), manual input routing over plain
 * {@link Control}s (no vanilla widget registry), RSHIFT/ESC close with
 * animated reveal. Cape/friends/config/video/preview subsystems are cut
 * (their backends don't exist in this build).
 */
public final class Wing extends Screen {
    private static final int PANEL_REVEAL_MILLIS = 200;
    private static final int PAGE_REVEAL_MILLIS = 150;

    private static Sidebar.Tab selectedTab = Sidebar.TABS.get(0);

    private final Transition panelReveal = new Transition(0f, PANEL_REVEAL_MILLIS, 130, Transition.Easing.EASE_OUT_SETTLE, Transition.Easing.EASE_IN_CUBIC);
    private final Transition pageReveal = new Transition(1f, PAGE_REVEAL_MILLIS, Transition.Easing.EASE_OUT_EXPO);
    private final Sidebar sidebar = new Sidebar(this, this::selectTab, this::openMenu);
    private final SettingsMenu menu = new SettingsMenu(this);
    private final ModulesPage modulesPage = new ModulesPage(this, () -> selectedTab.category());
    private final List<Page> pages = List.of(modulesPage);
    private final List<Control> widgets = new ArrayList<>();
    private UiScale appliedScale = UiScale.current();
    private PanelLayout layout;
    private Control focused;
    private Control dragTarget;
    private boolean rebuildPending;
    private boolean closing;
    private int dragOffsetX;
    private int dragOffsetY;
    private boolean draggingPanel;
    private double grabX;
    private double grabY;

    public Wing() {
        super(Text.literal(LT.L1));
        panelReveal.set(1f);
    }

    public <T extends Control> T add(T widget) {
        widgets.add(widget);
        return widget;
    }

    public void remove(Control widget) {
        widgets.remove(widget);
        if (focused == widget) focused = null;
        if (dragTarget == widget) dragTarget = null;
    }

    public void requestRebuild() {
        rebuildPending = true;
    }

    public void dropFocus() {
        if (focused != null) {
            focused.setFocused(false);
            focused.dismiss();
            focused = null;
        }
    }

    public void focusWidget(Control widget) {
        if (focused == widget) {
            widget.setFocused(true);
            return;
        }
        dropFocus();
        focused = widget;
        if (widget != null) widget.setFocused(true);
    }

    public Control focusedControl() {
        return focused;
    }

    @Override
    protected void init() {
        // Late agent injection bypasses the launch-time resource scan.
        // Register bundled Adin textures on the client render thread first.
        com.utilitymod.gui.GuiTextures.ensureLoaded();
        relayoutAll();
    }

    private void relayoutAll() {
        layout = PanelLayout.of(width, height, dragOffsetX, dragOffsetY, UiScale.current().factor());
        widgets.clear();
        focused = null;
        dragTarget = null;
        sidebar.init(layout, () -> selectedTab);
        for (Page page : pages) page.init(layout);
        menu.init(layout);
        updateStates();
    }

    private Page currentPage() {
        return modulesPage;
    }

    private void selectTab(Sidebar.Tab tab) {
        for (Page page : pages) page.reset();
        Page before = currentPage();
        if (tab != selectedTab) com.utilitymod.gui.UiSounds.play(com.utilitymod.gui.UiSounds.Sound.TAB);
        selectedTab = tab;
        if (currentPage() != before) {
            pageReveal.snap(0f);
            pageReveal.set(1f);
        }
        modulesPage.reloadRows();
        updateStates();
    }

    private void openMenu() {
        for (Page page : pages) page.reset();
        menu.open();
        updateStates();
    }

    private void closeMenu() {
        menu.close();
        updateStates();
    }

    private void updateStates() {
        boolean interactive = menu.isHidden();
        sidebar.setActive(interactive);
        Page current = currentPage();
        for (Page page : pages) page.setState(page == current, interactive);
        menu.updateStates();
    }

    private List<Popup> popups() {
        List<Popup> popups = new ArrayList<>(menu.popups());
        for (Page page : pages) popups.addAll(page.popups());
        return popups;
    }

    public void requestClose() {
        if (closing) return;
        closing = true;
        com.utilitymod.gui.UiSounds.play(com.utilitymod.gui.UiSounds.Sound.CLOSE);
        com.utilitymod.gui.GuiConfig.save();
        panelReveal.set(0f);
    }

    @Override
    public void tick() {
        if (closing && panelReveal.value() <= 0f) {
            MinecraftClient.getInstance().setScreen(null);
        }
    }

    @Override
    public void removed() {
        com.utilitymod.gui.GuiConfig.save();
        super.removed();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext graphics, int mouseX, int mouseY, float delta) {
        Theme.update();
        if (appliedScale != UiScale.current()) {
            appliedScale = UiScale.current();
            rebuildPending = true;
        }
        if (rebuildPending) {
            rebuildPending = false;
            relayoutAll();
        }
        float reveal = panelReveal.value();
        if (reveal <= 0f) return;
        float centerX = layout.x() + layout.width() * 0.5f;
        float centerY = layout.y() + layout.height() * 0.5f;
        boolean glass = com.utilitymod.echo.hud.HudManager.liquidGlass
                && com.utilitymod.echo.render.GlassRenderer.available();
        if (glass) {
            // glass backdrop (follows the pop-in scale), translucent theme panels on top
            float sc = 0.96f + 0.04f * reveal;
            float gw = layout.width() * sc, gh = layout.height() * sc;
            com.utilitymod.echo.render.GlassRenderer.queue(new com.utilitymod.echo.render.GlassRenderer.Panel(
                    centerX - gw / 2f, centerY - gh / 2f, gw, gh, layout.atLeastOne(PanelLayout.RADIUS) * sc, reveal));
            com.utilitymod.echo.render.GlassRenderer.flush(graphics);
        }
        graphics.createNewRootLayer();
        Transform.popIn(graphics, centerX, centerY, reveal, 0.96f, () -> {
            drawPanel(graphics, glass);
            drawContent(graphics, mouseX, mouseY, delta);
            sidebar.draw(graphics, layout, selectedTab, mouseX, mouseY, delta);
            menu.draw(graphics, mouseX, mouseY, delta);
        });
    }

    private void drawPanel(DrawContext graphics, boolean glass) {
        int radius = layout.atLeastOne(PanelLayout.RADIUS);
        int sidebar = glass ? dev.koifih.client.util.Colors.withAlpha(Theme.SIDEBAR, 0.3f) : Theme.SIDEBAR;
        int main = glass ? dev.koifih.client.util.Colors.withAlpha(Theme.MAIN, 0.45f) : Theme.MAIN;
        // soft drop shadow: stacked, widening translucent shells
        for (int i = 6; i >= 1; i--) {
            int s = i * 2;
            Draw.rect(graphics, layout.x() - s, layout.y() - s + 3, layout.width() + 2 * s, layout.height() + 2 * s,
                    radius + s, dev.koifih.client.util.Colors.withAlpha(0xFF000000, 0.035f));
        }
        Draw.rect(graphics, layout.x(), layout.y(), layout.width(), layout.height(), radius, sidebar);
        // thin lit top edge
        Draw.rect(graphics, layout.x() + radius, layout.y(), layout.width() - 2 * radius, 1, 0,
                dev.koifih.client.util.Colors.withAlpha(0xFFFFFFFF, 0.08f));
        Draw.rect(graphics, layout.contentX(), layout.contentY(), layout.contentWidth(), layout.contentHeight(),
                radius, Draw.TILED | Draw.TOP_LEFT | Draw.BOTTOM_RIGHT, main);
    }

    private void drawContent(DrawContext graphics, int mouseX, int mouseY, float delta) {
        updateStates();
        Page page = currentPage();
        float shown = pageReveal.value();
        Scissor.clip(graphics, layout.contentX(), layout.contentY(), layout.contentWidth(), layout.contentHeight(), () ->
                Opacity.with(shown, () -> Transform.translated(graphics, 0f, 6 * layout.scale() * (1f - shown),
                        () -> page.draw(graphics, mouseX, mouseY, delta))));
    }

    private boolean routeToPopups(Click event, boolean doubleClick) {
        for (Popup popup : popups()) {
            if (!popup.isOpen()) continue;
            // Popup.mouseClicked handles clicks on the open option list (which
            // lies outside the button) and dismisses on outside clicks.
            if (popup.mouseClicked(event, doubleClick)) {
                focusWidget(popup);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseClicked(Click event, boolean doubleClick) {
        if (closing) return true;
        if (routeToPopups(event, doubleClick)) return true;
        Control focusedControl = focused;
        if (focusedControl instanceof dev.koifih.client.ui.component.Keybind bind && bind.capturesInput()) {
            if (bind.captureMouse(event)) return true;
        }
        if (menu.isOpen()) {
            if (menu.contains(event.x(), event.y())) return routeWidgets(event, doubleClick);
            closeMenu();
            return true;
        }
        if (currentPage().mouseClicked(event, doubleClick)) return true;
        if (routeWidgets(event, doubleClick)) return true;
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && layout.inTopBar(event.x(), event.y())) {
            draggingPanel = true;
            grabX = event.x() - layout.x();
            grabY = event.y() - layout.y();
            dropFocus();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    private boolean routeWidgets(Click event, boolean doubleClick) {
        for (int i = widgets.size() - 1; i >= 0; i--) {
            Control widget = widgets.get(i);
            if (!widget.active || !widget.visible) continue;
            if (widget.isMouseOver(event.x(), event.y())) {
                focusWidget(widget);
                if (!(widget instanceof Popup)) com.utilitymod.gui.UiSounds.play(com.utilitymod.gui.UiSounds.Sound.CLICK);
                widget.onClick(event, doubleClick);
                dragTarget = widget;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(Click event, double dx, double dy) {
        if (dragTarget != null) {
            dragTarget.onDrag(event, dx, dy);
            return true;
        }
        if (!draggingPanel || event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return super.mouseDragged(event, dx, dy);
        }
        int x = Math.clamp((int) Math.round(event.x() - grabX), 0, Math.max(0, width - layout.width()));
        int y = Math.clamp((int) Math.round(event.y() - grabY), 0, Math.max(0, height - layout.height()));
        dragOffsetX = x - (width - layout.width()) / 2;
        dragOffsetY = y - (height - layout.height()) / 2;
        layout = PanelLayout.of(width, height, dragOffsetX, dragOffsetY, UiScale.current().factor());
        for (Popup popup : popups()) popup.setBottomLimit(layout.bottom());
        for (Page page : pages) page.relayout(layout);
        menu.relayout(layout);
        sidebar.resetPill();
        return true;
    }

    @Override
    public boolean mouseReleased(Click event) {
        if (dragTarget != null) {
            dragTarget.onRelease(event);
            dragTarget = null;
            return true;
        }
        if (draggingPanel && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            draggingPanel = false;
            // Sidebar/menu controls are positioned at init; re-run it so
            // their hitboxes follow the moved panel.
            relayoutAll();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double dx, double dy) {
        for (Popup popup : popups()) {
            if (popup.isOpen() && popup.mouseScrolled(x, y, dx, dy)) return true;
        }
        if (currentPage().mouseScrolled(x, y, dx, dy)) return true;
        return super.mouseScrolled(x, y, dx, dy);
    }

    @Override
    public boolean keyPressed(KeyInput event) {
        if (closing) return true;
        Control focusedControl = focused;
        if (focusedControl != null && focusedControl.capturesInput() && focusedControl.keyPressed(event)) return true;
        boolean typing = focusedControl instanceof dev.koifih.client.ui.component.TextInput input && input.capturesInput();
        if (!typing && Keybinds.matches(event)) {
            requestClose();
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_ESCAPE && menu.isOpen()) {
            closeMenu();
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            requestClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharInput event) {
        if (closing) return true;
        Control focusedControl = focused;
        if (focusedControl != null && focusedControl.capturesInput() && focusedControl.charTyped(event)) return true;
        if (modulesPage.charTyped(event)) return true;
        return super.charTyped(event);
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x226a, 0xd12c, 0x37d1, 0xa8ae, 0xbc1a}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be}).plainString();
    }
}
