package com.utilitymod.echo.features.settings.impl;

import lombok.Getter;
import lombok.Setter;
import com.utilitymod.echo.features.settings.Setting;

import java.util.function.Predicate;

@Getter
@Setter
public class KeybindSetting extends Setting {
    private int key;
    private boolean listening;

    public KeybindSetting(CharSequence name, int defaultKey) {
        super(name);
        this.key = defaultKey;
        this.listening = false;
    }

    public KeybindSetting(CharSequence name, int defaultKey, Predicate<Object> dependency) {
        super(name);
        this.key = defaultKey;
        this.listening = false;
        this.setDependency(dependency);
    }
    
    public KeybindSetting(String name, int defaultKey) {
        this((CharSequence) name, defaultKey);
    }

    public KeybindSetting(String name, int defaultKey, Predicate<Object> dependency) {
        this((CharSequence) name, defaultKey, dependency);
    }

    @Override
    public String getTypeId() { return LT.L1; }



    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x18e0,0xc6c2,0x8085}, new char[]{0xc6a9,0x1885,0x80fc,0xcc41}).plainString();
    }
}


