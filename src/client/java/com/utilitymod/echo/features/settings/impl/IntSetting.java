package com.utilitymod.echo.features.settings.impl;

import lombok.Getter;
import lombok.Setter;

import java.util.function.Predicate;

import com.utilitymod.echo.features.settings.Setting;

@Getter
@Setter
public class IntSetting extends Setting {
    private int value;
    private int minValue;
    private int maxValue;
    private CharSequence suffixSequence = "";
    private String suffix = "";

    public IntSetting(CharSequence name, int defaultValue, int minValue, int maxValue) {
        super(name);
        this.value = defaultValue;
        this.minValue = minValue;
        this.maxValue = maxValue;
    }

    public IntSetting(CharSequence name, int defaultValue, int minValue, int maxValue, String suffix) {
        super(name);
        this.value = defaultValue;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.suffixSequence = suffix;
        this.suffix = suffix;
    }

    public IntSetting(CharSequence name, int defaultValue, int minValue, int maxValue, String suffix,
            Predicate<Object> dependency) {
        super(name);
        this.value = defaultValue;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.suffixSequence = suffix;
        this.suffix = suffix;
        this.setDependency(dependency);
    }

    public IntSetting(CharSequence name, int defaultValue, int minValue, int maxValue, CharSequence suffix) {
        super(name);
        this.value = defaultValue;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.suffixSequence = suffix;
        this.suffix = null;
    }

    public IntSetting(CharSequence name, int defaultValue, int minValue, int maxValue, CharSequence suffix,
            Predicate<Object> dependency) {
        super(name);
        this.value = defaultValue;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.suffixSequence = suffix;
        this.suffix = null;
        this.setDependency(dependency);
    }

    public IntSetting(CharSequence name, int defaultValue, int minValue, int maxValue, Predicate<Object> dependency) {
        super(name);
        this.value = defaultValue;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.setDependency(dependency);
    }

    public IntSetting(String name, int defaultValue, int minValue, int maxValue) {
        this((CharSequence) name, defaultValue, minValue, maxValue);
    }

    public IntSetting(String name, int defaultValue, int minValue, int maxValue, String suffix) {
        this((CharSequence) name, defaultValue, minValue, maxValue, suffix);
    }

    public IntSetting(String name, int defaultValue, int minValue, int maxValue, String suffix,
            Predicate<Object> dependency) {
        this((CharSequence) name, defaultValue, minValue, maxValue, suffix, dependency);
    }

    public IntSetting(String name, int defaultValue, int minValue, int maxValue, Predicate<Object> dependency) {
        this((CharSequence) name, defaultValue, minValue, maxValue, dependency);
    }

    public void setValue(int value) {
        this.value = Math.max(minValue, Math.min(maxValue, value));
        notifyChange();
    }

    public void setValueNoNotify(int value) {
        this.value = Math.max(minValue, Math.min(maxValue, value));
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

    @Override
    public String getTypeId() { return LT.L1; }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x95f3,0xc3c5,0x4de1}, new char[]{0x4d88,0xc3ab,0x9587,0x2341}).plainString();
    }
}