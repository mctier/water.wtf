package com.utilitymod.echo.features.settings.impl;

import lombok.Getter;
import lombok.Setter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;
import com.utilitymod.echo.features.settings.Setting;
import com.utilitymod.echo.utils.strings.I18nText;

@Getter
@Setter
public class MultiModeSetting extends Setting {
    private final LinkedHashMap<CharSequence, Boolean> options = new LinkedHashMap<>();

    public MultiModeSetting(CharSequence name, CharSequence... modes) {
        super(name);
        for (CharSequence m : modes) options.put(I18nText.of(m), false);
    }

    public MultiModeSetting(CharSequence name, Predicate<Object> dependency, CharSequence... modes) {
        super(name);
        for (CharSequence m : modes) options.put(I18nText.of(m), false);
        setDependency(dependency);
    }

    private static boolean csEquals(CharSequence a, CharSequence b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        int la = a.length();
        int lb = b.length();
        if (la != lb) return false;
        for (int i = 0; i < la; i++) {
            if (a.charAt(i) != b.charAt(i)) return false;
        }
        return true;
    }

    public void toggle(CharSequence mode) {
        for (CharSequence key : options.keySet()) {
            if (csEquals(key, mode)) {
                options.put(key, !options.get(key));
                notifyChange();
                return;
            }
        }
    }

    public boolean isEnabled(CharSequence mode) {
        for (CharSequence key : options.keySet()) {
            if (csEquals(key, mode)) {
                Boolean v = options.get(key);
                return v != null && v;
            }
        }
        return false;
    }
    /**
     * Replace the internal options map with a mutable copy of the provided map.
     * Null entries are ignored; boolean values default to false when null.
     */
    public void setOptionsSequenceView(Map<CharSequence, Boolean> newOptions) {
        options.clear();
        if (newOptions == null) return;
        for (Map.Entry<CharSequence, Boolean> e : newOptions.entrySet()) {
            CharSequence k = e.getKey();
            Boolean v = e.getValue();
            options.put(I18nText.of(k), v != null && v);
        }
        notifyChange();
    }

    public void setOption(CharSequence key, boolean value) {
        for (CharSequence existing : options.keySet()) {
            if (csEquals(existing, key)) {
                options.put(existing, value);
                notifyChange();
                return;
            }
        }
        options.put(I18nText.of(key), value);
        notifyChange();
    }


    /**
     * Returns a CharSequence-keyed view of options to avoid forcing String materialization.
     */
    public Map<CharSequence, Boolean> getOptionsSequenceView() {
        return java.util.Collections.unmodifiableMap(options);
    }

    /**
     * Backwards compatible String-keyed view (may materialize keys via toString()).
     */
    @Override
    public String getTypeId() { return LT.L1; }

    public Map<String, Boolean> getOptionsView() {
        java.util.LinkedHashMap<String, Boolean> map = new java.util.LinkedHashMap<>();
        for (Map.Entry<CharSequence, Boolean> e : options.entrySet()) {
            map.put(e.getKey().toString(), e.getValue());
        }
        return map;
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2636,0x430c,0xf8f8}, new char[]{0xf895,0x4361,0x2652,0x18c8}).plainString();
    }
}

