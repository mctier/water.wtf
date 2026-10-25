package com.utilitymod.echo.features.settings.impl;

import com.utilitymod.echo.features.settings.Setting;

public class HotbarSelectionSetting extends Setting {

    private final boolean[] selected = new boolean[9];
    public HotbarSelectionSetting(CharSequence nameSequence) {
        super(nameSequence);
    }

    public boolean isSelected(int slot) {
        return selected[slot];
    }

    public void setSelected(int slot) {
        selected[slot] = true;
    }

    public void setSelected(int slot, boolean value) {
        selected[slot] = value;
    }

    public void toggleSelected(int slot) {
        selected[slot] = !selected[slot];
    }
}
