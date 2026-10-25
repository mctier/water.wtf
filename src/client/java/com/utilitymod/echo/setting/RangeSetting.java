package com.utilitymod.echo.setting;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

/** echo's min/max range with {@link #getRandom()} (uniform pick per call). */
public class RangeSetting extends Setting {
    private float minValue;
    private float maxValue;
    private final float lowerBound;
    private float upperBound;
    private final float increment;

    public RangeSetting(String name, float defaultMin, float defaultMax, float lowerBound, float upperBound,
                        float increment) {
        this(name, defaultMin, defaultMax, lowerBound, upperBound, increment, null);
    }

    public RangeSetting(String name, float defaultMin, float defaultMax, float lowerBound, float upperBound,
                        float increment, Predicate<Object> dependency) {
        super(name, dependency);
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
        this.increment = increment;
        this.minValue = snap(defaultMin);
        this.maxValue = Math.max(minValue, snap(defaultMax));
    }

    public float getMinValue() { return minValue; }
    public float getMaxValue() { return maxValue; }
    public float getLowerBound() { return lowerBound; }
    public float getUpperBound() { return upperBound; }
    public float getIncrement() { return increment; }

    public void setUpperBound(float upperBound) { this.upperBound = upperBound; }

    public void setMinValue(float v) {
        minValue = Math.min(snap(v), maxValue);
        notifyChange();
    }

    public void setMaxValue(float v) {
        maxValue = Math.max(snap(v), minValue);
        notifyChange();
    }

    public float getRange() { return maxValue - minValue; }

    public float getRandom() {
        if (maxValue <= minValue) return minValue;
        return (float) ThreadLocalRandom.current().nextDouble(minValue, maxValue);
    }

    private float snap(float v) {
        if (increment > 0) v = Math.round(v / increment) * increment;
        return Math.max(lowerBound, Math.min(upperBound, v));
    }

    @Override public String save() { return minValue + LT.L1 + maxValue; }

    @Override
    public void load(String v) {
        String[] p = v.split(LT.L1);
        minValue = snap(Float.parseFloat(p[0]));
        maxValue = Math.max(minValue, snap(Float.parseFloat(p[1])));
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf570}, new char[]{0xf54a,0x4ce1,0x4ea7,0x1432}).plainString();
    }
}
