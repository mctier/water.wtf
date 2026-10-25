package com.utilitymod.echo.setting;

import java.util.function.Predicate;

public class BoolSetting extends Setting {
    private boolean value;

    public BoolSetting(String name, boolean defaultValue) { this(name, defaultValue, null); }

    public BoolSetting(String name, boolean defaultValue, Predicate<Object> dependency) {
        super(name, dependency);
        this.value = defaultValue;
    }

    public boolean getValue() { return value; }

    public void setValue(boolean value) {
        if (this.value == value) return;
        this.value = value;
        notifyChange();
    }

    public void toggle() { setValue(!value); }

    @Override public String save() { return Boolean.toString(value); }

    @Override public void load(String v) { value = Boolean.parseBoolean(v); }
}
