package com.utilitymod.echo.setting;

/**
 * echo's click-simulation toggle. On: clicks go through the mouse button
 * path (CPS counters see them). Off: the key binding's press counter is
 * bumped directly. Echo's extra "companion key" slots are not ported.
 */
public class InputSimulationSetting extends BoolSetting {
    public InputSimulationSetting(String name, boolean defaultValue) { super(name, defaultValue); }
}
