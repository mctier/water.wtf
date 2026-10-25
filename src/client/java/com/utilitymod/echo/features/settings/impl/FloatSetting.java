package com.utilitymod.echo.features.settings.impl;

import lombok.Getter;
import lombok.Setter;

import java.util.function.Predicate;

import com.utilitymod.echo.features.settings.Setting;

@Getter
@Setter
public class FloatSetting extends Setting {
    private float value;
    private float minValue;
    private float maxValue;
    private float increment;
    private CharSequence suffixSequence = "";
    private String suffix = "";

    public FloatSetting(CharSequence name, float defaultValue, float minValue, float maxValue, float increment) {
        super(name);
        this.value = defaultValue;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.increment = increment;
    }

    public FloatSetting(CharSequence name, float defaultValue, float minValue, float maxValue, float increment,
            CharSequence suffix) {
        super(name);
        this.value = defaultValue;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.increment = increment;
        this.suffixSequence = suffix;
        this.suffix = null;
    }

    public FloatSetting(CharSequence name, float defaultValue, float minValue, float maxValue, float increment,
            Predicate<Object> dependency) {
        super(name);
        this.value = defaultValue;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.increment = increment;
        this.setDependency(dependency);
    }

    public FloatSetting(CharSequence name, float defaultValue, float minValue, float maxValue, float increment,
            CharSequence suffix, Predicate<Object> dependency) {
        super(name);
        this.value = defaultValue;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.increment = increment;
        this.suffixSequence = suffix;
        this.suffix = null;
        this.setDependency(dependency);
    }

    public FloatSetting(CharSequence name, float defaultValue, float minValue, float maxValue, float increment,
            String suffix) {
        super(name);
        this.value = defaultValue;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.increment = increment;
        this.suffixSequence = suffix;
        this.suffix = suffix;
    }

    public FloatSetting(CharSequence name, float defaultValue, float minValue, float maxValue, float increment,
            String suffix, Predicate<Object> dependency) {
        super(name);
        this.value = defaultValue;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.increment = increment;
        this.suffixSequence = suffix;
        this.suffix = suffix;
        this.setDependency(dependency);
    }

    public FloatSetting(String name, float defaultValue, float minValue, float maxValue, float increment,
            Predicate<Object> dependency) {
        this((CharSequence) name, defaultValue, minValue, maxValue, increment, dependency);
    }

    public FloatSetting(String name, float defaultValue, float minValue, float maxValue, float increment) {
        this((CharSequence) name, defaultValue, minValue, maxValue, increment);
    }

    public FloatSetting(String name, float defaultValue, float minValue, float maxValue, float increment,
            String suffix) {
        this((CharSequence) name, defaultValue, minValue, maxValue, increment, suffix);
    }

    public FloatSetting(String name, float defaultValue, float minValue, float maxValue, float increment, String suffix,
            Predicate<Object> dependency) {
        this((CharSequence) name, defaultValue, minValue, maxValue, increment, suffix, dependency);
    }

    public String getSuffix() {
        if (this.suffix != null)
            return this.suffix;
        if (this.suffixSequence == null)
            return null;
        this.suffix = this.suffixSequence.toString();
        return this.suffix;
    }

    public CharSequence getSuffixSequence() {
        return this.suffixSequence;
    }

    public void increment() {
        setValue(Math.min(value + increment, maxValue));
    }

    public void decrement() {
        setValue(Math.max(value - increment, minValue));
    }

    public void setValue(float value) {
        this.value = Math.max(minValue, Math.min(maxValue, value));
        notifyChange();
    }

    public void setValueNoNotify(float value) {
        this.value = Math.max(minValue, Math.min(maxValue, value));
    }

    @Override
    public String getTypeId() { return LT.L1; }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1f40,0x3ee9,0x3f75}, new char[]{0x1f26,0x3e85,0x3f01,0x9103}).plainString();
    }
}
