package com.utilitymod.echo.setting;

import java.util.function.Predicate;

public class IntSetting extends Setting {
    private int value;
    private final int minValue;
    private final int maxValue;

    public IntSetting(String name, int defaultValue, int minValue, int maxValue) {
        this(name, defaultValue, minValue, maxValue, null);
    }

    public IntSetting(String name, int defaultValue, int minValue, int maxValue, Predicate<Object> dependency) {
        super(name, dependency);
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.value = clamp(defaultValue);
    }

    public int getValue() { return value; }
    public int getMinValue() { return minValue; }
    public int getMaxValue() { return maxValue; }

    public void setValue(int v) {
        int next = clamp(v);
        if (next == value) return;
        value = next;
        notifyChange();
    }

    private int clamp(int v) { return Math.max(minValue, Math.min(maxValue, v)); }

    @Override public String save() { return Integer.toString(value); }

    @Override public void load(String v) { value = clamp(Integer.parseInt(v)); }
}
