package com.utilitymod.echo.features.settings.impl;

import lombok.Setter;

import java.util.function.Predicate;

import com.utilitymod.echo.features.settings.Setting;

@Setter
public class BoolSetting extends Setting {
    private boolean value;

    public boolean getValue() {
        return value;
    }

    public void setValue(boolean value) {
        this.value = value;
        notifyChange();
    }

    public BoolSetting(CharSequence name, boolean defaultValue) {
        super(name);
        this.value = defaultValue;
    }
    
    public BoolSetting(CharSequence name, boolean defaultValue, Predicate<Object> dependency) {
        super(name);
        this.value = defaultValue;
        this.setDependency(dependency);
    }
    
    public BoolSetting(String name, boolean defaultValue) {
        this((CharSequence) name, defaultValue);
    }
    
    public BoolSetting(String name, boolean defaultValue, Predicate<Object> dependency) {
        this((CharSequence) name, defaultValue, dependency);
    }

    @Override
    public String getTypeId() { return LT.L1; }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7956,0x3359,0xf392}, new char[]{0xf3f0,0x7939,0x3336,0x6391}).plainString();
    }
}
