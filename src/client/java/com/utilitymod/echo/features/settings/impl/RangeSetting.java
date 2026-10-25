package com.utilitymod.echo.features.settings.impl;

import lombok.Getter;
import lombok.Setter;

import java.util.function.Predicate;

import com.utilitymod.echo.features.settings.Setting;

@Getter
@Setter
public class RangeSetting extends Setting {
    private float minValue;
    private float maxValue;
    private float lowerBound;
    private float upperBound;
    private float increment;
    private CharSequence suffixSequence = "";
    private CharSequence suffix = "";

    public RangeSetting(CharSequence name, float defaultMin, float defaultMax, float lowerBound, float upperBound, float increment) {
        super(name);
        this.minValue = defaultMin;
        this.maxValue = defaultMax;
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
        this.increment = increment;
    }

    public RangeSetting(CharSequence name, float defaultMin, float defaultMax, float lowerBound, float upperBound, float increment, CharSequence suffix) {
        super(name);
        this.minValue = defaultMin;
        this.maxValue = defaultMax;
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
        this.increment = increment;
        this.suffix = suffix;
        this.suffixSequence = suffix;
    }

    public RangeSetting(CharSequence name, float defaultMin, float defaultMax, float lowerBound, float upperBound, float increment, CharSequence suffix, Predicate<Object> dependency) {
        super(name);
        this.minValue = defaultMin;
        this.maxValue = defaultMax;
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
        this.increment = increment;
        this.suffix = suffix;
        this.suffixSequence = suffix;
        this.setDependency(dependency);
    }
    
    public RangeSetting(CharSequence name, float defaultMin, float defaultMax, float lowerBound, float upperBound, float increment, Predicate<Object> dependency) {
        super(name);
        this.minValue = defaultMin;
        this.maxValue = defaultMax;
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
        this.increment = increment;
        this.setDependency(dependency);
    }
    
    public RangeSetting(String name, float defaultMin, float defaultMax, float lowerBound, float upperBound, float increment) {
        this((CharSequence) name, defaultMin, defaultMax, lowerBound, upperBound, increment);
    }

    public RangeSetting(String name, float defaultMin, float defaultMax, float lowerBound, float upperBound, float increment, String suffix) {
        this((CharSequence) name, defaultMin, defaultMax, lowerBound, upperBound, increment, suffix);
    }

    public CharSequence getSuffixSequence() {
        return this.suffixSequence;
    }

    public RangeSetting(String name, float defaultMin, float defaultMax, float lowerBound, float upperBound, float increment, String suffix, Predicate<Object> dependency) {
        this((CharSequence) name, defaultMin, defaultMax, lowerBound, upperBound, increment, suffix, dependency);
    }
    
    public RangeSetting(String name, float defaultMin, float defaultMax, float lowerBound, float upperBound, float increment, Predicate<Object> dependency) {
        this((CharSequence) name, defaultMin, defaultMax, lowerBound, upperBound, increment, dependency);
    }
    
    public void incrementMin() {
        setMinValue(Math.min(maxValue, Math.min(upperBound, minValue + increment)));
    }
    
    public void decrementMin() {
        setMinValue(Math.max(lowerBound, minValue - increment));
    }
    
    public void incrementMax() {
        setMaxValue(Math.min(upperBound, maxValue + increment));
    }
    
    public void decrementMax() {
        setMaxValue(Math.max(minValue, Math.max(lowerBound, maxValue - increment)));
    }
    
    public float getMinPercentage() {
        return (minValue - lowerBound) / (upperBound - lowerBound);
    }
    
    public float getMaxPercentage() {
        return (maxValue - lowerBound) / (upperBound - lowerBound);
    }
    
    public float getRange() {
        return maxValue - minValue;
    }

    public float getRandom() {
        return (float) (Math.random() * (maxValue - minValue)) + minValue;
    }

    @Override
    public String getTypeId() { return LT.L1; }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5ec7,0x8add,0xca3d}, new char[]{0xca4f,0x8ab3,0x5ea0,0xefca}).plainString();
    }
}
