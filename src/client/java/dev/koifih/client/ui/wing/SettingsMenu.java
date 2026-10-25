package dev.koifih.client.ui.wing;

import dev.koifih.client.input.Keybinds;
import dev.koifih.client.render.AdinIcon;
import dev.koifih.client.render.Draw;
import dev.koifih.client.render.Text;
import dev.koifih.client.render.Transform;
import dev.koifih.client.ui.Theme;
import dev.koifih.client.ui.Tooltips;
import dev.koifih.client.ui.Transition;
import dev.koifih.client.ui.UiScale;
import dev.koifih.client.ui.Units;
import dev.koifih.client.ui.component.popup.AccentPicker;
import dev.koifih.client.ui.component.Bool;
import dev.koifih.client.ui.component.Button;
import dev.koifih.client.ui.component.Control;
import dev.koifih.client.ui.component.popup.Dropdown;
import dev.koifih.client.ui.component.Keybind;
import dev.koifih.client.ui.component.popup.Popup;
import dev.koifih.client.ui.component.Segmented;
import dev.koifih.client.util.Lang;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Click;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
public final class SettingsMenu {
    private static final int REVEAL_MILLIS = 180;
    private static final int WIDTH = 164;
    private static final int PADDING = 10;
    private static final int TITLE_HEIGHT = 18;
    private static final int ROW_STRIDE = 22;
    private static final int ROW_COUNT = 11;
    private static final int TOGGLE_WIDTH = 24;
    private static final int TOGGLE_HEIGHT = 12;
    private static final int GEAR_GAP = 6;
    private static final int GEAR_SIZE = 16;
    private static final int RADIUS = 6;
    private static final int CLOSE_ICON = 0xe5cd;
    private static final int BIND_WIDTH = 56;
    private static final int FIELD_LABEL_INSET = 23;
    private static final int LEFT_INSET = 2;
    private static final int BIND_HEIGHT = 16;
    private static final Theme.Mode[] MODES = {Theme.Mode.LIGHT, Theme.Mode.DARK};

    private final Wing gui;
    private final Transition reveal = new Transition(0f, REVEAL_MILLIS, 110, Transition.Easing.EASE_OUT_SETTLE, Transition.Easing.EASE_IN_CUBIC);
    private final List<Control> controls = new ArrayList<>();
    private PanelLayout layout;
    private boolean open;
    private Keybind bind;

    public void init(PanelLayout layout) {
        this.layout = layout;
        controls.clear();
        float scale = layout.scale();
        int rowHeight = layout.atLeastOne(ROW_STRIDE);
        int inner = layout.scaled(PADDING);

        int closeSize = layout.atLeastOne(12);
        Button close = gui.add(Button.icon(right() - inner - closeSize,
                y() + inner + (layout.scaled(TITLE_HEIGHT) - closeSize) / 2, closeSize, scale, CLOSE_ICON,
                net.minecraft.text.Text.literal(LT.L1), this::close));

        int switchHeight = layout.atLeastOne(16);
        Segmented theme = gui.add(new Segmented(x() + inner, rowY(0) + (rowHeight - switchHeight) / 2,
                width() - 2 * inner, switchHeight, scale, net.minecraft.text.Text.literal(LT.L2),
                new Segmented.Segment[] {
                        Segmented.Segment.of(AdinIcon.LIGHT, Lang.get(LT.L3)),
                        Segmented.Segment.of(AdinIcon.DARK, Lang.get(LT.L4))},
                () -> Theme.mode() == Theme.Mode.LIGHT ? 0 : 1, index -> Theme.setMode(MODES[index])));

        Dropdown language = choice(1, LT.L5, AdinIcon.LANGUAGE, names(Lang.Language.ALL, Lang.Language::displayName),
                () -> Lang.current().ordinal(), index -> {
                    Lang.set(Lang.Language.ALL[index]);
                    gui.requestRebuild();
                });
        Dropdown size = choice(2, LT.L6, AdinIcon.SIZE, names(UiScale.ALL, UiScale::displayName),
                () -> UiScale.current().ordinal(), index -> UiScale.set(UiScale.ALL[index]));
        Dropdown units = choice(3, LT.L7, AdinIcon.UNITS, names(Units.ALL, Units::displayName),
                () -> Units.current().ordinal(), index -> Units.set(Units.ALL[index]));

        int accentHeight = layout.atLeastOne(14);
        AccentPicker accent = new AccentPicker(0, rowY(5) + (rowHeight - accentHeight) / 2, accentHeight, scale,
                Theme::accentRgb, Theme::setAccent);
        accent.setX(right() - inner - accent.getWidth());
        gui.add(accent);
        accent.setBottomLimit(layout.bottom());

        Dropdown tooltips = choice(4, LT.L8, AdinIcon.TOOLTIPS, names(Tooltips.ALL, Tooltips::displayName),
                () -> Tooltips.current().ordinal(), index -> {
                    Tooltips.set(Tooltips.ALL[index]);
                    gui.requestRebuild();
                });

        int bindWidth = layout.atLeastOne(BIND_WIDTH);
        int bindHeight = layout.atLeastOne(BIND_HEIGHT);
        bind = gui.add(new Keybind(right() - inner - bindWidth, rowY(6) + (rowHeight - bindHeight) / 2,
                bindWidth, bindHeight, scale, net.minecraft.text.Text.literal(Lang.get(LT.L9)),
                Keybinds::panelKey, Keybinds::setPanelKey));
        bind.setClearable(false);

        int toggleWidth = layout.atLeastOne(TOGGLE_WIDTH);
        int toggleHeight = layout.atLeastOne(TOGGLE_HEIGHT);
        Bool clicks = gui.add(new Bool(right() - inner - toggleWidth, rowY(7) + (rowHeight - toggleHeight) / 2,
                toggleWidth, toggleHeight, scale, net.minecraft.text.Text.literal(LT.L10),
                () -> com.utilitymod.gui.UiSounds.enabled, value -> com.utilitymod.gui.UiSounds.enabled = value));

        Bool glass = gui.add(new Bool(right() - inner - toggleWidth, rowY(8) + (rowHeight - toggleHeight) / 2,
                toggleWidth, toggleHeight, scale, net.minecraft.text.Text.literal(LT.L11),
                () -> com.utilitymod.echo.hud.HudManager.liquidGlass, value -> {
                    com.utilitymod.echo.hud.HudManager.liquidGlass = value;
                    com.utilitymod.gui.GuiConfig.save();
                }));

        Bool dropdown = gui.add(new Bool(right() - inner - toggleWidth, rowY(9) + (rowHeight - toggleHeight) / 2,
                toggleWidth, toggleHeight, scale, net.minecraft.text.Text.literal(LT.L12),
                () -> com.utilitymod.gui.FrameManager.style == com.utilitymod.gui.FrameManager.Style.DROPDOWN, value -> {
                    com.utilitymod.gui.FrameManager.style = value ? com.utilitymod.gui.FrameManager.Style.DROPDOWN
                            : com.utilitymod.gui.FrameManager.Style.WATER;
                    com.utilitymod.gui.GuiConfig.save();
                    if (value) net.minecraft.client.MinecraftClient.getInstance().execute(() ->
                            net.minecraft.client.MinecraftClient.getInstance().setScreen(new com.utilitymod.gui.DropdownGui()));
                }));

        Bool cards = gui.add(new Bool(right() - inner - toggleWidth, rowY(10) + (rowHeight - toggleHeight) / 2,
                toggleWidth, toggleHeight, scale, net.minecraft.text.Text.literal(LT.L13),
                () -> com.utilitymod.gui.FrameManager.style == com.utilitymod.gui.FrameManager.Style.WATER, value -> {
                    com.utilitymod.gui.FrameManager.style = value ? com.utilitymod.gui.FrameManager.Style.WATER
                            : com.utilitymod.gui.FrameManager.Style.WATER;
                    com.utilitymod.gui.GuiConfig.save();
                    if (value) net.minecraft.client.MinecraftClient.getInstance().execute(() ->
                            net.minecraft.client.MinecraftClient.getInstance().setScreen(new com.utilitymod.gui.WaterGui()));
                }));

        controls.addAll(List.of(close, theme, language, size, units, tooltips, accent, bind, clicks, glass, dropdown, cards));
    }

    private Dropdown choice(int row, String key, AdinIcon icon, String[] names, IntSupplier get, IntConsumer set) {
        int inner = layout.scaled(PADDING);
        int height = layout.atLeastOne(18);
        Dropdown dropdown = gui.add(Dropdown.single(x() + inner, rowY(row) + (layout.atLeastOne(ROW_STRIDE) - height) / 2,
                width() - 2 * inner, height, layout.scale(), net.minecraft.text.Text.literal(Lang.get(key)), names, get, set));
        dropdown.setIcon(icon);
        dropdown.setBottomLimit(layout.bottom());
        return dropdown;
    }

    private static <T> String[] names(T[] values, Function<T, String> name) {
        String[] names = new String[values.length];
        for (int i = 0; i < names.length; i++) names[i] = name.apply(values[i]);
        return names;
    }

    public void relayout(PanelLayout layout) {
        this.layout = layout;
    }

    public int width() {
        return Math.min(layout.width() - 2 * layout.padding(), layout.scaled(WIDTH));
    }

    public int height() {
        return 2 * layout.scaled(PADDING) + layout.scaled(TITLE_HEIGHT) + ROW_COUNT * layout.atLeastOne(ROW_STRIDE);
    }

    public int x() {
        return layout.x() + layout.atLeastOne(LEFT_INSET);
    }

    private float labelX() {
        return x() + layout.scaled(PADDING) + FIELD_LABEL_INSET * layout.scale();
    }

    public int right() {
        return x() + width();
    }

    public int y() {
        return layout.bottom() - layout.sidebarInset() - layout.atLeastOne(GEAR_SIZE) - layout.scaled(GEAR_GAP) - height();
    }

    private int rowY(int row) {
        return y() + layout.scaled(PADDING) + layout.scaled(TITLE_HEIGHT) + row * layout.atLeastOne(ROW_STRIDE);
    }

    public boolean contains(double pointX, double pointY) {
        return pointX >= x() && pointX < right() && pointY >= y() && pointY < y() + height();
    }

    public boolean isOpen() {
        return open;
    }

    public boolean isHidden() {
        return !open && reveal.value() == 0f;
    }

    public List<Popup> popups() {
        List<Popup> popups = new ArrayList<>();
        for (Control control : controls) if (control instanceof Popup popup) popups.add(popup);
        return popups;
    }

    public boolean captureMouse(Click event) {
        return bind.captureMouse(event);
    }

    public void open() {
        com.utilitymod.gui.UiSounds.play(com.utilitymod.gui.UiSounds.Sound.POPUP);
        open = true;
        reveal.set(1f);
        gui.dropFocus();
    }

    public void close() {
        for (Control control : controls) control.dismiss();
        open = false;
        reveal.set(0f);
        gui.dropFocus();
    }

    public void updateStates() {
        boolean ready = open && reveal.value() == 1f;
        for (Control control : controls) {
            control.active = ready;
            control.visible = reveal.value() > 0f;
        }
    }

    public void draw(DrawContext graphics, int mouseX, int mouseY, float delta) {
        float shown = reveal.value();
        if (shown <= 0f) return;
        graphics.createNewRootLayer();
        float scale = layout.scale();
        float anchorX = x() + layout.scaled(8);
        float anchorY = y() + height();
        Transform.popIn(graphics, anchorX, anchorY, shown, 0.94f, () -> Transform.translated(graphics, 0f, 4 * scale * (1f - shown),
                () -> drawBody(graphics, mouseX, mouseY, delta)));
    }

    private void drawBody(DrawContext graphics, int mouseX, int mouseY, float delta) {
        float scale = layout.scale();
        int radius = layout.atLeastOne(RADIUS);
        Draw.bordered(graphics, x(), y(), width(), height(), radius, Theme.OVERLAY, Theme.POPUP_BORDER);
        float titleX = x() + PADDING * scale;
        float textX = labelX();
        Text.drawCentered(graphics, Lang.get(LT.L14), titleX,
                y() + (PADDING + TITLE_HEIGHT * 0.5f) * scale, 8 * scale, Theme.TEXT);
        Text.drawCentered(graphics, Lang.get(LT.L15), textX, rowY(5) + layout.atLeastOne(ROW_STRIDE) * 0.5f, 7 * scale, Theme.TEXT);
        Text.drawCentered(graphics, Lang.get(LT.L9), textX, rowY(6) + layout.atLeastOne(ROW_STRIDE) * 0.5f, 7 * scale, Theme.TEXT);
        Text.drawCentered(graphics, LT.L10, textX, rowY(7) + layout.atLeastOne(ROW_STRIDE) * 0.5f, 7 * scale, Theme.TEXT);
        Text.drawCentered(graphics, LT.L12, textX, rowY(9) + layout.atLeastOne(ROW_STRIDE) * 0.5f, 7 * scale, Theme.TEXT);
        Text.drawCentered(graphics, LT.L11, textX, rowY(8) + layout.atLeastOne(ROW_STRIDE) * 0.5f, 7 * scale, Theme.TEXT);
        for (Control control : controls) control.draw(graphics, mouseX, mouseY, delta);
        for (Control control : controls) if (control instanceof Popup popup) popup.renderPopup(graphics, mouseX, mouseY);
    }

    public SettingsMenu(Wing gui) {
        this.gui = gui;
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5ad7,0xc8d6,0x4e99,0x52a6,0x81a9,0x843b,0xf4c7,0xdb22,0x403e,0xd772,0xceff,0x3dc2,0x130a,0xc98c,0xcf3a,0xe971,0x0027,0xd26e,0xb6f4,0xcf44,0x136b}, new char[]{0xd731,0xc8ba,0xcf55,0xf4b4,0xc9e9,0x841b,0xdb41,0x52ca,0x81c0,0x5ab2,0xe91f,0xcf30,0xcedf,0x1379,0x4efc,0xb680,0x404a,0x3dab,0x0049,0xd209,0x1318}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1da9,0xd913,0x5466,0xd384,0x30bd}, new char[]{0xd3d0,0x1dc1,0xd976,0x30d0,0x5403}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x25ac,0x2599,0xef42,0x9dc6,0x3714,0xdca4,0x996f,0x63d8,0x3ff1,0x96c3,0xb84a}, new char[]{0x25d8,0x25f1,0x990a,0x3779,0xdcc1,0x63f6,0x3f9d,0xef2b,0xb82d,0x9dae,0x96b7}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd690,0xcc7a,0x5494,0x1e7c,0x07bd,0xe77f,0x9230,0x54be,0x8b28,0x9fb1}, new char[]{0x1e08,0x54fc,0x9fd4,0x54d3,0xd6f5,0x0793,0xe71b,0x8b49,0x9242,0xcc11}).plainString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd0fd,0xca6c,0x86d6,0xcfe1,0x0cba,0x13d7,0xab16,0xe1b7}, new char[]{0x86ba,0xcf80,0xd093,0x13b0,0xab63,0x0cdb,0xca0b,0xe1d2}).plainString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x912c,0xa733,0x3a6d,0xc233}, new char[]{0xa740,0x3a04,0xc249,0x9149}).plainString();
        static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x85fa,0xcb34,0x7364,0xac79,0x4de2}, new char[]{0xcb41,0x8594,0x4d8b,0x7310,0xac0a}).plainString();
        static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc5c5,0xf11f,0x7283,0xe7a7,0x9687,0x9bae,0x1bfe,0x7592}, new char[]{0x75e6,0xe7c8,0x72ec,0x9bc2,0x96f3,0xf176,0x1b8e,0xc5b6}).plainString();
        static final String L9 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa8ae, 0x9627, 0x82c4, 0x37d1, 0x0b3e, 0x1c9c, 0x226a, 0xb150, 0xd10c, 0xbc1a}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643}).plainString();
        static final String L10 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6de7,0x7d83,0xdd10,0x7b8a,0x034c,0x2423,0x6a26,0xbc2d,0xfcbd}, new char[]{0xfce8,0xbc64,0x6dc7,0x033f,0x244c,0x7bff,0x6a48,0x7de7,0xdd63}).plainString();
        static final String L11 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf494,0x925d,0x9101,0x7114,0x378b,0x6ab5,0xaea1,0x30f2,0x6096,0x0219,0x2d93,0x2326}, new char[]{0x60da,0x9234,0x2de2,0xf4e1,0x309b,0x9165,0x2306,0x6af2,0xaecd,0x0278,0x7167,0x37f8}).plainString();
        static final String L12 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x56bb,0x00b1,0xbaf3,0xb80a,0x99b1,0x19c4,0x8b3c,0xded5,0xa2fb,0x12be,0x5292,0xeb13}, new char[]{0xb84e,0xeb61,0x8b53,0x56cb,0x19a0,0x99de,0xba84,0xdebb,0x52b2,0x12f9,0x00e4,0xa2b2}).plainString();
        static final String L13 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x01e6,0x87e1,0xa774,0x0bf5,0x331c,0x2355,0x13aa,0x263d}, new char[]{0x267e,0x8780,0x0b87,0x2331,0x333c,0xa733,0x13ff,0x01af}).plainString();
        static final String L14 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe71e,0xa2b6,0xa16d,0x6215,0x4311,0x0215,0x84ac,0x0940}, new char[]{0x0266,0x4374,0x0934,0xe76a,0x84c5,0xa2d8,0xa10a,0x6266}).plainString();
        static final String L15 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x38cc,0x8f94,0x7303,0xda3d,0x4d52,0x19c6}, new char[]{0x8ff5,0x38af,0xda5e,0x4d37,0x736d,0x19b2}).plainString();
    }
}
