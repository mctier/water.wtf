package com.utilitymod.cgui;

import net.minecraft.client.MinecraftClient;

/** Fluent builder: GuiBuilder.screen("t").window("w", 40, 40, 200).label("hi").open(); */
public final class GuiBuilder {
    private final CuiScreen screen;
    private Window current;

    private GuiBuilder(String screenTitle) {
        screen = new CuiScreen(screenTitle);
    }

    public static GuiBuilder screen(String title) {
        return new GuiBuilder(title);
    }

    public GuiBuilder window(String title, float x, float y, float width) {
        current = screen.window(title, x, y, width);
        return this;
    }

    public GuiBuilder widget(Widget w) {
        require().add(w);
        return this;
    }

    public GuiBuilder label(String text) {
        return widget(new Label(text));
    }

    public GuiBuilder button(String label, Runnable onClick) {
        return widget(new Button(label, onClick));
    }

    public GuiBuilder toggle(String label, java.util.function.BooleanSupplier get,
                             java.util.function.Consumer<Boolean> set) {
        return widget(new Toggle(label, get, set));
    }

    public GuiBuilder checkbox(String label, java.util.function.BooleanSupplier get,
                               java.util.function.Consumer<Boolean> set) {
        return widget(new Checkbox(label, get, set));
    }

    public GuiBuilder slider(String label, double min, double max, double step,
                             java.util.function.DoubleSupplier get, java.util.function.DoubleConsumer set,
                             String suffix) {
        return widget(new Slider(label, min, max, step, get, set, suffix));
    }

    public GuiBuilder dropdown(String label, java.util.function.Supplier<java.util.List<String>> options,
                               java.util.function.Supplier<String> current, java.util.function.Consumer<String> pick) {
        Window w = require();
        return widget(w.add(new Dropdown(label, options, current, pick).window(w)));
    }

    public GuiBuilder field(String label, String initial, java.util.function.Consumer<String> onCommit) {
        return widget(new TextField(label, initial, onCommit));
    }

    public GuiBuilder progress(String label, java.util.function.DoubleSupplier value) {
        return widget(new ProgressBar(label, value));
    }

    public GuiBuilder tabs(Tabs tabs) {
        return widget(tabs);
    }

    public GuiBuilder scroll(ScrollPane pane) {
        return widget(pane);
    }

    public CuiScreen open() {
        MinecraftClient.getInstance().setScreen(screen);
        return screen;
    }

    private Window require() {
        if (current == null) throw new IllegalStateException(LT.L1);
        return current;
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xbe2b,0xc6e1,0x6a0e,0xd125,0xeefa,0x6975,0x5392,0xccbf,0x6a15,0x39a1,0x294f,0xd00f,0x88ec,0xbd4d,0xb274,0xf82d,0xd20a,0x9c89,0x5040,0x5aa6,0x1dcb,0x8536,0xbd2a,0x0141,0x0c5a,0xedd3,0x6777,0x8646,0x7f1c,0xb920,0xe757,0x5d91,0x994e,0xbc45,0x8bc1,0xe104,0xaf7d,0x6ab0,0x3b3f,0x3c5b,0x48ac,0x201d,0x4057,0x3334,0x7117,0x4052,0x632b}, new char[]{0x8571,0x5de4,0x6a7c,0xf86f,0x5035,0x403b,0x39cd,0x7173,0x8623,0x7f6e,0xd11f,0xe777,0x9cea,0x0120,0xc68d,0x6919,0xbe0b,0x8bb6,0x6342,0xe16a,0x1daf,0xedbc,0x9939,0xd027,0x407e,0x3b1f,0xaf1f,0xb945,0xbc23,0x48c3,0x3c29,0x6a6b,0x203d,0xd26b,0xbd4e,0x53f6,0x2926,0x335a,0xbd2a,0x88cc,0xee8d,0xccd6,0xb210,0x6710,0x0c3f,0x6ac4,0x5ad5}).toString();
    }
}
