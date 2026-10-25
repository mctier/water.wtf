package dev.koifih.client.ui.wing;

import dev.koifih.client.render.AdinIcon;
import dev.koifih.client.render.Draw;
import dev.koifih.client.render.Text;
import dev.koifih.client.ui.Theme;
import dev.koifih.client.ui.Transition;
import dev.koifih.client.ui.component.Button;
import dev.koifih.client.ui.wing.TabButton;
import net.minecraft.client.gui.DrawContext;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
public final class Sidebar {
    public record Tab(String id, AdinIcon icon, Supplier<String> label, com.utilitymod.gui.Category category) {}

    public static final List<Tab> TABS = tabs();
    private static final int BUTTON_HEIGHT = 22;
    private static final int BUTTON_STRIDE = 26;
    private static final int PILL_RADIUS = 6;
    private static final int PILL_MILLIS = 210;
    private static final float PILL_STRETCH = 0.16f;
    private static final int GEAR_SIZE = 16;
    private static final String BRAND_TEXT = LT.L1;

    private final Wing gui;
    private final Consumer<Tab> onSelect;
    private final Runnable onSettings;
    private final List<TabButton> buttons = new ArrayList<>();
    private final Transition pillY = new Transition(0f, PILL_MILLIS, Transition.Easing.EASE_OUT_EXPO);
    private final Transition pillWidth = new Transition(0f, PILL_MILLIS, Transition.Easing.EASE_OUT_EXPO);
    private boolean pillPlaced;
    private Button settingsButton;

    private static List<Tab> tabs() {
        List<Tab> tabs = new ArrayList<>();
        for (com.utilitymod.gui.Category category : com.utilitymod.gui.Category.values()) {
            String key = LT.L2 + (category == com.utilitymod.gui.Category.VISUAL ? LT.L3 : category.getLabel().toLowerCase());
            tabs.add(new Tab(category.getLabel().toLowerCase(), mapIcon(category),
                    () -> dev.koifih.client.util.Lang.getOrDefault(key, category.getLabel()), category));
        }
        return List.copyOf(tabs);
    }

    private static AdinIcon mapIcon(com.utilitymod.gui.Category category) {
        return switch (category) {
            case COMBAT -> AdinIcon.COMBAT;
            case MOVEMENT -> AdinIcon.MOVEMENT;
            case VISUAL -> AdinIcon.RENDER;
            case MISC, DONUT, FINDER -> AdinIcon.MISC;
        };
    }

    public void init(PanelLayout layout, Supplier<Tab> selected) {
        buttons.clear();
        pillPlaced = false;
        float scale = layout.scale();
        int margin = layout.sidebarInset() - TabButton.iconInset(scale);
        int x = layout.x() + margin;
        int width = Math.max(1, layout.sidebarWidth() - 2 * margin);
        int height = layout.atLeastOne(BUTTON_HEIGHT);
        int index = 0;
        for (Tab tab : TABS) {
            int y = layout.contentY() + layout.padding() + index++ * layout.atLeastOne(BUTTON_STRIDE);
            buttons.add(gui.add(new TabButton(x, y, width, height, scale, tab,
                    () -> selected.get() == tab, () -> onSelect.accept(tab))));
        }
        int gearSize = layout.atLeastOne(GEAR_SIZE);
        settingsButton = gui.add(Button.icon(layout.x() + layout.sidebarInset(),
                layout.bottom() - layout.sidebarInset() - gearSize, gearSize, scale, AdinIcon.SETTINGS, () -> Theme.SIDEBAR,
                net.minecraft.text.Text.literal(LT.L4), onSettings));
    }

    public void setActive(boolean active) {
        for (TabButton button : buttons) button.active = active;
        settingsButton.active = active;
    }

    public void resetPill() {
        pillPlaced = false;
    }

    public void draw(DrawContext graphics, PanelLayout layout, Tab selected, int mouseX, int mouseY, float delta) {
        drawBrand(graphics, layout);
        drawPill(graphics, layout, selected);
        for (TabButton button : buttons) button.draw(graphics, mouseX, mouseY, delta);
        settingsButton.draw(graphics, mouseX, mouseY, delta);
    }

    private void drawBrand(DrawContext graphics, PanelLayout layout) {
        float size = 10 * layout.scale();
        float x = layout.x() + PanelLayout.SIDEBAR_INSET * layout.scale();
        float baseline = Text.centeredBaseline(BRAND_TEXT, size, layout.y() + layout.topBarHeight() * 0.5f);
        Text.draw(graphics, List.of(new Text.Span(LT.L5, Theme.TEXT),
                new Text.Span(LT.L6, Theme.ACCENT)), x, baseline, size);
    }

    private void drawPill(DrawContext graphics, PanelLayout layout, Tab selected) {
        TabButton target = null;
        for (TabButton button : buttons) {
            if (button.tab() == selected) target = button;
        }
        if (target == null) return;
        if (!pillPlaced) {
            pillY.snap(target.getY());
            pillWidth.snap(target.getWidth());
            pillPlaced = true;
        }
        pillY.set(target.getY());
        pillWidth.set(target.getWidth());
        float y = pillY.value();
        float height = target.getHeight() * (1f + PILL_STRETCH * pillY.flight());
        Draw.rect(graphics, target.getX(), y - (height - target.getHeight()) * 0.5f, pillWidth.value(), Math.round(height),
                Math.max(1, Math.round(PILL_RADIUS * layout.scale())), Theme.ACCENT);
        for (TabButton button : buttons) {
            float center = button.getY() + button.getHeight() * 0.5f;
            button.setOnPill(center >= y && center < y + target.getHeight());
        }
    }

    public Sidebar(Wing gui, Consumer<Tab> onSelect, Runnable onSettings) {
        this.gui = gui;
        this.onSelect = onSelect;
        this.onSettings = onSettings;
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd36c,0x578c,0x4ed8,0x25e6,0x40cf,0x5d2e,0x9a48,0x60e1,0xdbfa,0xf604}, new char[]{0x5d5b,0x4eac,0x6088,0x57e0,0xf66d,0x40bb,0x9a31,0xdb97,0xd303,0x2582}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xaf97,0xac74,0x25f5,0x2d4d,0xa28d,0xf4fb,0x9bcc,0x91ec,0x2c92}, new char[]{0x2596,0x9bad,0xf48f,0xac11,0xa2ea,0xaff8,0x919e,0x2d34,0x2cbc}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xef9d,0x6fda,0x0acc,0x51dd,0x4b7d,0x116d}, new char[]{0xefef,0x0aa9,0x4b13,0x51b9,0x6fbf,0x111f}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xdd64,0x178a,0xc4f1,0xd478,0xc8ad,0xd19e,0xb915,0xa46b,0xc264,0x9331,0xdeef,0x6b11,0x9560,0x7a31,0x0100}, new char[]{0xc227,0x6b7d,0x7a58,0xd1fb,0x17e4,0xc8d9,0x9540,0x0173,0xc494,0xb961,0xde9b,0xdd0d,0xd416,0xa40c,0x9342}).plainString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xaa14,0xde84,0xec3e,0x59d8,0x7072,0xf286,0x6688}, new char[]{0xf2f3,0x66fc,0xaa7d,0x59b4,0xec57,0x7006,0xdefd}).plainString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc71c,0x7ff6,0xc631}, new char[]{0x7f9b,0xc773,0xc655,0x1d4b}).plainString();
    }
}
