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
        if (current == null) throw new IllegalStateException("GuiBuilder: call window() before adding widgets");
        return current;
    }
}
