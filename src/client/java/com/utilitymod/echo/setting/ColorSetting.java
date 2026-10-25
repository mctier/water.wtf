package com.utilitymod.echo.setting;

import java.util.function.Predicate;

/** ARGB color (echo's ColorSetting minus its HSV/rainbow editor state). */
public class ColorSetting extends Setting {
    private int argb;

    public ColorSetting(String name, int defaultArgb) { this(name, defaultArgb, null); }

    public ColorSetting(String name, int defaultArgb, Predicate<Object> dependency) {
        super(name, dependency);
        this.argb = defaultArgb;
    }

    public int getARGB() { return argb; }

    public void setARGB(int argb) {
        if (this.argb == argb) return;
        this.argb = argb;
        notifyChange();
    }

    @Override public String save() { return Integer.toHexString(argb); }

    @Override public void load(String v) { argb = (int) Long.parseLong(v, 16); }
}
