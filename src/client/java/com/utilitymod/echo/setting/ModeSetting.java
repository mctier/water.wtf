package com.utilitymod.echo.setting;

import java.util.List;
import java.util.function.Predicate;

public class ModeSetting extends Setting {
    private final List<String> modes;
    private String value;

    public ModeSetting(String name, String defaultValue, String... modes) {
        this(name, defaultValue, null, modes);
    }

    public ModeSetting(String name, String defaultValue, Predicate<Object> dependency, String... modes) {
        super(name, dependency);
        this.modes = List.of(modes);
        this.value = this.modes.contains(defaultValue) ? defaultValue : this.modes.getFirst();
    }

    public String getValue() { return value; }
    public List<String> getModes() { return modes; }
    public int getIndex() { return modes.indexOf(value); }

    public boolean is(String mode) { return value.equalsIgnoreCase(mode); }

    public void setValue(String mode) {
        if (!modes.contains(mode) || mode.equals(value)) return;
        value = mode;
        notifyChange();
    }

    public void setIndex(int i) { setValue(modes.get(Math.floorMod(i, modes.size()))); }

    public void cycle() { setIndex(getIndex() + 1); }

    @Override public String save() { return value; }

    @Override public void load(String v) { if (modes.contains(v)) value = v; }
}
