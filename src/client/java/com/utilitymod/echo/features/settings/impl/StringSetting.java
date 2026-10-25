package com.utilitymod.echo.features.settings.impl;

import lombok.Getter;
import lombok.Setter;
import com.utilitymod.echo.features.settings.Setting;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

@Getter
@Setter
public class StringSetting extends Setting {
    private String value;
    private String placeholder;
    private final List<String> entries = new ArrayList<>();
    private Consumer<String> onAdd;
    private Consumer<String> onRemove;
    private boolean allowMultiple;
    private int maxLength = 32;

    public StringSetting(CharSequence name, String placeholder) {
        super(name);
        this.value = "";
        this.placeholder = placeholder;
        this.allowMultiple = false;
    }

    public StringSetting(CharSequence name, String placeholder, boolean allowMultiple) {
        super(name);
        this.value = "";
        this.placeholder = placeholder;
        this.allowMultiple = allowMultiple;
    }

    public StringSetting(CharSequence name, String placeholder, Predicate<Object> dependency) {
        super(name);
        this.value = "";
        this.placeholder = placeholder;
        this.allowMultiple = false;
        this.setDependency(dependency);
    }

    public StringSetting(CharSequence name, String placeholder, boolean allowMultiple, Predicate<Object> dependency) {
        super(name);
        this.value = "";
        this.placeholder = placeholder;
        this.allowMultiple = allowMultiple;
        this.setDependency(dependency);
    }
    
    public StringSetting(String name, String placeholder) {
        this((CharSequence) name, placeholder);
    }

    public StringSetting(String name, String placeholder, boolean allowMultiple) {
        this((CharSequence) name, placeholder, allowMultiple);
    }

    public StringSetting(String name, String placeholder, Predicate<Object> dependency) {
        this((CharSequence) name, placeholder, dependency);
    }

    public StringSetting(String name, String placeholder, boolean allowMultiple, Predicate<Object> dependency) {
        this((CharSequence) name, placeholder, allowMultiple, dependency);
    }

    public void addEntry(String entry) {
        String sanitized = sanitize(entry);
        if (!sanitized.isEmpty() && !entries.contains(sanitized)) {
            entries.add(sanitized);
            if (onAdd != null) {
                onAdd.accept(sanitized);
            }
        }
    }

    public void removeEntry(String entry) {
        String sanitized = sanitize(entry);
        if (entries.remove(sanitized)) {
            if (onRemove != null) {
                onRemove.accept(sanitized);
            }
        }
    }

    public void clearEntries() {
        entries.clear();
    }

    public boolean hasEntry(String entry) {
        return entries.contains(sanitize(entry));
    }

    public String getValue() {
        return value;
    }

    public String getPlaceholder() {
        return placeholder;
    }

    public void setValue(String value) {
        this.value = sanitize(value);
    }

    public void setMaxLength(int maxLength) {
        this.maxLength = Math.max(1, maxLength);
        this.value = sanitize(this.value);
        List<String> sanitizedEntries = new ArrayList<>();
        for (String entry : entries) {
            String sanitized = sanitize(entry);
            if (!sanitized.isEmpty() && !sanitizedEntries.contains(sanitized)) {
                sanitizedEntries.add(sanitized);
            }
        }
        entries.clear();
        entries.addAll(sanitizedEntries);
    }

    @Override
    public String getTypeId() { return LT.L1; }

    private String sanitize(String input) {
        if (input == null) return "";
        String trimmed = input.trim();
        if (trimmed.length() > maxLength) {
            trimmed = trimmed.substring(0, maxLength);
        }
        return trimmed;
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x131c,0x4f0b,0xf18e}, new char[]{0xf1fd,0x4f7f,0x136e,0x71a9}).plainString();
    }
}
