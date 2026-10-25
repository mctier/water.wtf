package com.utilitymod.echo.setting;

import java.util.function.Predicate;

public class FloatSetting extends Setting {
    private float value;
    private final float minValue;
    private final float maxValue;
    private final float increment;

    public FloatSetting(String name, float defaultValue, float minValue, float maxValue, float increment) {
        this(name, defaultValue, minValue, maxValue, increment, null);
    }

    public FloatSetting(String name, float defaultValue, float minValue, float maxValue, float increment,
                        Predicate<Object> dependency) {
        super(name, dependency);
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.increment = increment;
        this.value = clampSnap(defaultValue);
    }

    public float getValue() { return value; }
    public float getMinValue() { return minValue; }
    public float getMaxValue() { return maxValue; }
    public float getIncrement() { return increment; }

    public void setValue(float v) {
        float next = clampSnap(v);
        if (next == value) return;
        value = next;
        notifyChange();
    }

    public void setValueNoNotify(float v) { value = clampSnap(v); }

    public void increment() { setValue(value + increment); }

    public void decrement() { setValue(value - increment); }

    private float clampSnap(float v) {
        if (increment > 0) v = Math.round(v / increment) * increment;
        return Math.max(minValue, Math.min(maxValue, v));
    }

    @Override public String save() { return Float.toString(value); }

    @Override public void load(String v) { value = clampSnap(Float.parseFloat(v)); }
}
