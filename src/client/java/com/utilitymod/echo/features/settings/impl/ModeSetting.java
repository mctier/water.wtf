package com.utilitymod.echo.features.settings.impl;

import com.utilitymod.echo.features.settings.Setting;
import com.utilitymod.echo.utils.strings.I18nText;
import com.utilitymod.echo.utils.strings.Concat;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

@Getter
@Setter
public class ModeSetting extends Setting {
    private CharSequence valueSequence;
    private List<CharSequence> modes;
    
    public ModeSetting(CharSequence name, CharSequence defaultValue, CharSequence... modes) {
        super(name);
        this.modes = translate(modes);
        this.valueSequence = I18nText.of(defaultValue);
    }
    
    public ModeSetting(CharSequence name, CharSequence defaultValue, Predicate<Object> dependency, CharSequence... modes) {
        super(name);
        this.modes = translate(modes);
        this.valueSequence = I18nText.of(defaultValue);
        this.setDependency(dependency);
    }
    
    public ModeSetting(CharSequence name, CharSequence defaultValue, List<CharSequence> modesList) {
        super(name);
        this.modes = translate(modesList);
        this.valueSequence = I18nText.of(defaultValue);
    }
    
    public void cycle() {
        int index = -1;
        for (int i = 0; i < modes.size(); i++) {
            if (equalsIgnoreCase(modes.get(i), valueSequence)) {
                index = i;
                break;
            }
        }
        if (index == -1) index = 0;
        index = (index + 1) % modes.size();
        CharSequence next = modes.get(index);
        this.valueSequence = next;
        notifyChange();
    }
    
    public boolean is(CharSequence mode) {
        return equalsIgnoreCase(this.valueSequence, mode);
    }
    public CharSequence getValueSequence() {
        return this.valueSequence;
    }
    public void setValue(CharSequence cs) {
        this.valueSequence = I18nText.of(cs);
        notifyChange();
    }


    public CharSequence getValue() {
        return this.valueSequence;
    }

    @Override
    public String getTypeId() { return LT.L1; }

    private static boolean equalsIgnoreCase(CharSequence a, CharSequence b) {
        if (a instanceof Concat && b instanceof Concat) {
            return ((Concat)a).fragmentedEquals((Concat)b);
        }
        if (a == b) return true;
        if (a == null || b == null) return false;
        int len = a.length();
        if (len != b.length()) return false;
        for (int i = 0; i < len; i++) {
            // mask PUA cloak chars back to ASCII before comparing - no String materializes
            char ca = Character.toLowerCase(com.utilitymod.echo.utils.CloakUtil.plainChar(a.charAt(i)));
            char cb = Character.toLowerCase(com.utilitymod.echo.utils.CloakUtil.plainChar(b.charAt(i)));
            if (ca != cb) return false;
        }
        return true;
    }

    private static List<CharSequence> translate(CharSequence... values) {
        return translate(Arrays.asList(values));
    }

    private static List<CharSequence> translate(List<CharSequence> values) {
        List<CharSequence> translated = new ArrayList<>(values.size());
        for (CharSequence value : values) {
            translated.add(I18nText.of(value));
        }
        return translated;
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf538,0xdb2d,0x5403}, new char[]{0x546e,0xdb42,0xf55c,0x19eb}).plainString();
    }
} 
