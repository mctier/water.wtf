package com.utilitymod.echo.setting;

import java.util.function.Predicate;

/**
 * echo's {@code features.settings.Setting}: name, optional visibility
 * dependency and change listener. Echo's encrypted/i18n names become plain
 * strings; the persistence hook is {@link #notifyChange()}.
 */
public abstract class Setting {
    private final String name;
    private Predicate<Object> dependency;
    private Runnable changeListener;

    protected Setting(String name, Predicate<Object> dependency) {
        this.name = name;
        this.dependency = dependency;
    }

    public String getName() { return name; }

    public boolean isVisible() { return dependency == null || dependency.test(null); }

    public void setDependency(Predicate<Object> dependency) { this.dependency = dependency; }

    @SuppressWarnings("unchecked")
    public <T extends Setting> T onChanged(Runnable listener) {
        this.changeListener = listener;
        return (T) this;
    }

    public void notifyChange() {
        if (changeListener != null) changeListener.run();
        com.utilitymod.gui.GuiConfig.markDirty();
    }

    /** Persistence: value as a string ({@link #load} is the inverse). */
    public abstract String save();

    public abstract void load(String value);
}
