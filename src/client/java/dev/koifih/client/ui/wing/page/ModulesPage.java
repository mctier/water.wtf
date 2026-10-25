package dev.koifih.client.ui.wing.page;

import com.utilitymod.core.Module;
import com.utilitymod.core.ModuleManager;
import com.utilitymod.gui.Toasts;
import dev.koifih.client.render.Draw;
import dev.koifih.client.render.Scissor;
import dev.koifih.client.render.Switch;
import dev.koifih.client.render.Text;
import dev.koifih.client.render.Transform;
import dev.koifih.client.ui.Theme;
import dev.koifih.client.ui.Transition;
import dev.koifih.client.ui.wing.Wing;
import dev.koifih.client.ui.wing.Page;
import dev.koifih.client.ui.wing.PanelLayout;
import dev.koifih.client.ui.component.Control;
import dev.koifih.client.ui.component.TextInput;
import dev.koifih.client.ui.component.popup.Popup;
import dev.koifih.client.util.Colors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Module list page for our own module model, drawn in adin style: staggered
 * rows with hover tint, live toggle switches, header search, animated scroll
 * under scissor. Setting rows render when a module exposes adin
 * {@code Setting}s (none do yet - the overlay path stays dormant).
 */
public final class ModulesPage implements Page {
    private static final int ROW_HEIGHT = 40;
    private static final int ROW_STRIDE = 46;
    private static final float DESCRIPTION_SIZE = 6.8f;
    private static final int ROW_RADIUS = 6;
    private static final int TOGGLE_WIDTH = 24;
    private static final int TOGGLE_HEIGHT = 12;
    private static final int ROW_STAGGER_MILLIS = 22;
    private static final int SEARCH_HEIGHT = 20;
    private static final int TITLE_HEIGHT = 20;

    private final Wing gui;
    private final Supplier<com.utilitymod.gui.Category> category;
    private final StringBuilder search = new StringBuilder();
    private final Map<Module, Float> toggleShown = new HashMap<>();
    private final Map<Module, Float> hoverShown = new HashMap<>();
    private final List<Transition> rowReveals = new ArrayList<>();
    private TextInput searchBox;
    private PanelLayout layout;
    private boolean shown;
    private float scroll;
    private float scrollGoal;
    private Module hovered;
    private SettingsPanel settings;
    private static final int GEAR_SIZE = 12;
    private Module hoveredNow;

    public ModulesPage(Wing gui, Supplier<com.utilitymod.gui.Category> category) {
        this.gui = gui;
        this.category = category;
    }

    @Override
    public void init(PanelLayout layout) {
        this.layout = layout;
        searchBox = new TextInput(0, 0, 10, 10, layout.scale(),
                net.minecraft.text.Text.literal(LT.L1),
                24, search::toString, s -> { search.setLength(0); search.append(s); });
        searchBox.setFocused(false);
        gui.add(searchBox);
        // Widgets were rebuilt (relayout): reopen the settings view if one was open.
        com.utilitymod.echo.features.Feature reopen = settings != null && settings.isOpen() ? settings.module() : null;
        settings = new SettingsPanel(gui);
        if (reopen != null) settings.open(reopen, layout);
        reloadRows();
    }

    @Override
    public void relayout(PanelLayout layout) {
        this.layout = layout;
        if (settings != null) settings.relayout(layout);
    }

    public void reloadRows() {
        rowReveals.clear();
        List<Module> mods = visible();
        for (int i = 0; i < mods.size(); i++) {
            Transition reveal = new Transition(0f, 180, Transition.Easing.EASE_OUT_SETTLE);
            reveal.set(1f, i * ROW_STAGGER_MILLIS);
            rowReveals.add(reveal);
        }
        scrollGoal = 0f;
    }

    @Override
    public void reset() {
        if (settings != null) settings.close();
        reloadRows();
    }

    @Override
    public void setState(boolean shown, boolean interactive) {
        this.shown = shown;
    }

    private List<Module> visible() {
        String q = search.toString().trim().toLowerCase();
        List<Module> out = new ArrayList<>();
        Iterable<? extends Module> source = category.get() == com.utilitymod.gui.Category.DONUT
                ? com.utilitymod.anubis.AnubisBridge.features() : ModuleManager.all();
        for (Module m : source) {
            if (m.getCategory() != category.get()) continue;
            if (!q.isEmpty() && !(m.getName().toLowerCase().contains(q)
                    || (m.getDescription() != null && m.getDescription().toLowerCase().contains(q)))) continue;
            out.add(m);
        }
        return out;
    }

    private int searchY() {
        return layout.contentY() + layout.padding();
    }

    /** Search field sits under the category title. */
    private int searchFieldY() {
        return searchY() + TITLE_HEIGHT;
    }

    private int rowsY() {
        return searchFieldY() + SEARCH_HEIGHT + 8;
    }

    private float maxScroll() {
        List<Module> mods = visible();
        int contentH = layout.bottom() - rowsY() - layout.padding();
        return Math.max(0f, mods.size() * ROW_STRIDE - ROW_STRIDE + ROW_HEIGHT - contentH);
    }

    @Override
    public void draw(DrawContext graphics, int mouseX, int mouseY, float delta) {
        if (layout == null) return;
        scroll += (clampGoal() - scroll) * 0.35f;
        if (Math.abs(clampGoal() - scroll) < 0.5f) scroll = clampGoal();

        int cx = layout.rowX();
        int cw = layout.rowWidth();
        if (settings != null && settings.isOpen()) {
            searchBox.visible = false;
            settings.draw(graphics, cx, searchY(), cx, rowsY(), cw, layout.bottom() - layout.padding(),
                    mouseX, mouseY, delta);
            return;
        }
        searchBox.visible = true;
        // Search field.
        int sw = cw;
        searchBox.setX(cx);
        searchBox.setY(searchFieldY());
        searchBox.setWidth(sw);
        searchBox.setHeight(SEARCH_HEIGHT);
        searchBox.draw(graphics, mouseX, mouseY, delta);

        // Title.
        com.utilitymod.gui.Category cat = category.get();
        String title = dev.koifih.client.util.Lang.getOrDefault(LT.L2 + (cat == com.utilitymod.gui.Category.VISUAL ? LT.L3 : cat.getLabel().toLowerCase()), cat.getLabel());
        float titleY = searchY() + TITLE_HEIGHT * 0.5f - 2;
        Draw.rect(graphics, cx + 2, Math.round(titleY - 2), 4, 4, 2, Theme.ACCENT);
        Text.drawCentered(graphics, title, cx + 11, titleY, 10f, Theme.TEXT);

        List<Module> mods = visible();
        // "3 / 18 on" counter, right-aligned in the title row
        int enabledCount = 0;
        for (Module mod : mods) if (mod.isEnabled()) enabledCount++;
        String count = enabledCount + LT.L4 + mods.size() + LT.L5;
        Text.drawCentered(graphics, count, cx + cw - Text.width(count, 7f) - 2, titleY, 7f, Theme.MUTED);
        int top = rowsY();
        int bottom = layout.bottom() - layout.padding();
        hoveredNow = null;
        Scissor.clip(graphics, cx, top, cw, Math.max(0, bottom - top), () -> {
            for (int i = 0; i < mods.size(); i++) {
                int ry = top - Math.round(scroll) + i * ROW_STRIDE;
                if (ry + ROW_HEIGHT < top || ry > bottom) continue;
                float shown = i < rowReveals.size() ? rowReveals.get(i).value() : 1f;
                if (shown <= 0f) continue;
                drawRow(graphics, mods.get(i), cx, ry, cw, shown, mouseX, mouseY);
            }
        });

        hovered = hoveredNow;
        float max = maxScroll();
        if (max > 0f) {
            int ch = bottom - top;
            int barH = Math.max(12, Math.round(ch * ch / (float) (ch + max)));
            int barY = top + (ch - barH <= 0 ? 0 : Math.round(scroll / max * (ch - barH)));
            graphics.fill(cx + cw - 3, barY, cx + cw, barY + barH, Colors.withAlpha(Theme.DIM, 0.7f));
        }
    }

    static boolean glass() {
        return com.utilitymod.echo.hud.HudManager.liquidGlass && com.utilitymod.echo.render.GlassRenderer.available();
    }

    private float clampGoal() {
        float max = maxScroll();
        scrollGoal = Math.max(0f, Math.min(max, scrollGoal));
        return scrollGoal;
    }

    private void drawRow(DrawContext graphics, Module m, int x, int y, int w, float shown, int mouseX, int mouseY) {
        boolean hov = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + ROW_HEIGHT;
        float ht = approach(hoverShown.getOrDefault(m, 0f), hov ? 1f : 0f);
        hoverShown.put(m, ht);
        if (hov && hovered != m) com.utilitymod.gui.UiSounds.play(com.utilitymod.gui.UiSounds.Sound.HOVER);
        if (hov) hoveredNow = m;
        float on = approach(toggleShown.getOrDefault(m, m.isEnabled() ? 1f : 0f), m.isEnabled() ? 1f : 0f);
        toggleShown.put(m, on);

        // card: hover lifts it a touch to the right; enabled modules carry a faint accent tint
        int lift = Math.round(2 * ht);
        x += lift;
        w -= lift;
        int rowColor = Colors.lerp(Theme.ROW, Theme.CONTROL, ht * 0.5f);
        rowColor = Colors.lerp(rowColor, Theme.ACCENT, 0.10f * on);
        Draw.rect(graphics, x, y, w, ROW_HEIGHT, ROW_RADIUS, rowColor);
        // top highlight line (lit edge), brighter on hover
        Draw.rect(graphics, x + ROW_RADIUS, y, w - 2 * ROW_RADIUS, 1, 0, Colors.withAlpha(0xFFFFFFFF, 0.04f + 0.06f * ht));
        // accent bar grows in from the middle as the module turns on
        if (on > 0.01f) {
            int barH = Math.max(2, Math.round((ROW_HEIGHT - 16) * on));
            Draw.rect(graphics, x + 5, y + (ROW_HEIGHT - barH) / 2, 3, barH, 1, Colors.withAlpha(Theme.ACCENT, on));
        }

        float textX = x + 14 + 4 * on;
        float textWidth = w - TOGGLE_WIDTH - 38 - (hasSettings(m) ? GEAR_SIZE + 10 : 0);
        String name = Text.fit(m.getName(), textWidth, 8.5f);
        String desc = m.getDescription() == null ? "" : m.getDescription();
        List<String> lines = desc.isEmpty() || dev.koifih.client.ui.Tooltips.current() == dev.koifih.client.ui.Tooltips.OFF ? List.of() : Text.wrap(desc, textWidth, DESCRIPTION_SIZE, 2);
        int nameColor = Colors.lerp(Theme.TEXT, 0xFFFFFFFF, on * (Theme.mode() == Theme.Mode.DARK ? 1f : 0f));
        if (lines.isEmpty()) {
            Text.drawCentered(graphics, name, textX, y + ROW_HEIGHT * 0.5f, 8.5f, nameColor);
        } else if (lines.size() == 1) {
            Text.drawCentered(graphics, name, textX, y + 13, 8.5f, nameColor);
            Text.drawCentered(graphics, lines.getFirst(), textX, y + 27, DESCRIPTION_SIZE, Theme.MUTED);
        } else {
            Text.drawCentered(graphics, name, textX, y + 10, 8.5f, nameColor);
            for (int i = 0; i < lines.size(); i++) {
                Text.drawCentered(graphics, lines.get(i), textX, y + 21.5f + i * 9f, DESCRIPTION_SIZE, Theme.MUTED);
            }
        }

        if (hasSettings(m)) {
            int gx = gearX(x, w);
            boolean gearHover = mouseX >= gx && mouseX < gx + GEAR_SIZE && mouseY >= y && mouseY < y + ROW_HEIGHT;
            Draw.icon(graphics, dev.koifih.client.render.AdinIcon.SETTINGS, gx, y + (ROW_HEIGHT - GEAR_SIZE) / 2, GEAR_SIZE,
                    gearHover ? Theme.TEXT : Theme.MUTED);
        }
        int tx = x + w - TOGGLE_WIDTH - 14;
        int ty = y + (ROW_HEIGHT - TOGGLE_HEIGHT) / 2;
        Switch.draw(graphics, tx, ty, TOGGLE_WIDTH, TOGGLE_HEIGHT, on, Math.abs(on - (m.isEnabled() ? 1f : 0f)));
    }

    private float approach(float current, float target) {
        float next = current + (target - current) * 0.3f;
        return Math.abs(target - next) < 0.002f ? target : next;
    }

    @Override
    public boolean mouseClicked(Click event, boolean doubleClick) {
        if (settings != null && settings.isOpen()) {
            if (event.button() == 0 && settings.headerBackHit(event.x(), event.y(), layout.rowX(), searchY())) {
                settings.close();
                com.utilitymod.gui.UiSounds.play(com.utilitymod.gui.UiSounds.Sound.TAB);
                return true;
            }
            return false; // controls are Wing widgets: let its routing handle them
        }
        if (event.button() != 0 && event.button() != 1) return false;
        double mx = event.x();
        double my = event.y();
        // Search box.
        if (searchBox.isMouseOver(mx, my)) {
            gui.focusWidget(searchBox);
            searchBox.onClick(event, doubleClick);
            return true;
        }
        int cx = layout.rowX();
        int cw = layout.rowWidth();
        int top = rowsY();
        int bottom = layout.bottom() - layout.padding();
        if (mx < cx || mx >= cx + cw || my < top || my >= bottom) return false;
        List<Module> mods = visible();
        for (int i = 0; i < mods.size(); i++) {
            int ry = top - Math.round(scroll) + i * ROW_STRIDE;
            if (my >= ry && my < ry + ROW_HEIGHT) {
                Module m = mods.get(i);
                boolean onGear = mx >= gearX(cx, cw) && mx < gearX(cx, cw) + GEAR_SIZE;
                if (hasSettings(m) && (event.button() == 1 || onGear)) {
                    settings.open((com.utilitymod.echo.features.Feature) m, layout);
                    com.utilitymod.gui.UiSounds.play(com.utilitymod.gui.UiSounds.Sound.POPUP);
                    return true;
                }
                if (event.button() != 0) return true;
                m.setEnabled(!m.isEnabled());
                com.utilitymod.gui.UiSounds.play(m.isEnabled() ? com.utilitymod.gui.UiSounds.Sound.TOGGLE_ON : com.utilitymod.gui.UiSounds.Sound.TOGGLE_OFF);
                Toasts.push(m.isEnabled() ? Toasts.ICON_ON : Toasts.ICON_OFF,
                        m.getName() + (m.isEnabled() ? LT.L6 : LT.L7));
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double dx, double dy) {
        if (layout == null) return false;
        int cx = layout.rowX();
        int cw = layout.rowWidth();
        int top = rowsY();
        int bottom = layout.bottom() - layout.padding();
        if (x < cx || x >= cx + cw || y < top || y >= bottom) return false;
        if (settings != null && settings.isOpen()) {
            settings.scrollBy(dy, top, bottom);
            return true;
        }
        scrollGoal = clampGoal() + (dy < 0 ? 46f : -46f);
        clampGoal();
        return true;
    }

    @Override
    public List<dev.koifih.client.ui.component.popup.Popup> popups() {
        return settings == null ? List.of() : settings.popups();
    }

    private static boolean hasSettings(Module m) {
        return m instanceof com.utilitymod.echo.features.Feature em && !em.getSettings().isEmpty();
    }

    private int gearX(int x, int w) {
        return x + w - TOGGLE_WIDTH - 14 - GEAR_SIZE - 10;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyInput event) {
        return false;
    }

    public boolean charTyped(net.minecraft.client.input.CharInput event) {
        Control focused = gui.focusedControl();
        if (focused == searchBox && searchBox.capturesInput()) {
            return searchBox.charTyped(event);
        }
        return false;
    }

    private void clickSound(float pitch) {
        try {
            MinecraftClient.getInstance().getSoundManager()
                    .play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, pitch));
        } catch (Throwable t) {
            // Audio must never break input handling.
        }
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9c88,0x9dff,0x4144,0xfc33,0xc948,0x394d}, new char[]{0x9dac,0x4121,0x9ce9,0xfc41,0xc92b,0x3925}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8c3e,0xdb1a,0xb3fd,0x075e,0xc330,0xb557,0x045f,0x070e,0x487b}, new char[]{0x8c5d,0xdb7b,0x072a,0xc355,0x481c,0x0430,0x077c,0xb52e,0xb3d3}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xcff9,0xaf9b,0xe078,0xc309,0xe4a1,0x0ccc}, new char[]{0xe00a,0xe4c4,0xcf97,0xc36d,0x0ca9,0xafe9}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2bfb,0x73f0,0xcc11}, new char[]{0x2bdb,0xcc3e,0x73d0,0xf78a}).plainString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2b03,0xc77a,0x342c}, new char[]{0x2b23,0xc715,0x3442,0x1ac0}).plainString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0fb4,0x69c8,0xf892,0x3e65,0x16b8,0xfaed,0x8dff,0x7e46}, new char[]{0x3e45,0x0fd1,0xf8fc,0x8d9e,0x7e24,0x69a4,0x16dd,0xfa89}).plainString();
        static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3c1b,0x63ff,0x4ab4,0xd18c,0xa3d5,0xf160,0x484f,0x3a03,0xc616}, new char[]{0x4a94,0xa3b1,0xc67f,0x3a70,0x3c7a,0xf102,0x6393,0x482a,0xd1e8}).plainString();
    }
}
