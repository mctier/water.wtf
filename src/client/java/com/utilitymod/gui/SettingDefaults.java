package com.utilitymod.gui;

import com.utilitymod.core.Module;
import com.utilitymod.core.ModuleManager;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.settings.Setting;
import com.utilitymod.echo.features.settings.impl.BoolSetting;
import com.utilitymod.echo.features.settings.impl.ColorSetting;
import com.utilitymod.echo.features.settings.impl.FloatSetting;
import com.utilitymod.echo.features.settings.impl.IntSetting;
import com.utilitymod.echo.features.settings.impl.KeybindSetting;
import com.utilitymod.echo.features.settings.impl.ModeSetting;
import com.utilitymod.echo.features.settings.impl.MultiModeSetting;
import com.utilitymod.echo.features.settings.impl.RangeSetting;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Default values of every module setting, captured right after the modules are created and BEFORE the
 * saved config loads, so "Reset Module" can put a module back to factory settings.
 */
public final class SettingDefaults {
    private static final Map<Setting, Runnable> RESTORE = new IdentityHashMap<>();

    private SettingDefaults() {}

    public static void capture() {
        RESTORE.clear();
        for (Module m : ModuleManager.all()) {
            if (!(m instanceof Feature f)) continue;
            for (Setting st : f.getSettings()) {
                Runnable r = restorer(st);
                if (r != null) RESTORE.put(st, r);
            }
        }
    }

    private static Runnable restorer(Setting st) {
        if (st instanceof BoolSetting b) { boolean v = b.getValue(); return () -> b.setValue(v); }
        if (st instanceof FloatSetting f) { float v = f.getValue(); return () -> f.setValue(v); }
        if (st instanceof IntSetting i) { int v = i.getValue(); return () -> i.setValue(v); }
        if (st instanceof ModeSetting m) { CharSequence v = m.getValue(); return () -> m.setValue(v); }
        if (st instanceof MultiModeSetting mm) {
            Map<CharSequence, Boolean> v = new HashMap<>(mm.getOptionsSequenceView());
            return () -> v.forEach(mm::setOption);
        }
        if (st instanceof RangeSetting r) {
            float lo = r.getMinValue(), hi = r.getMaxValue();
            return () -> { r.setMinValue(lo); r.setMaxValue(hi); };
        }
        if (st instanceof ColorSetting c) { int v = c.getARGB(); return () -> c.setFromARGB(v); }
        if (st instanceof KeybindSetting k) { int v = k.getKey(); return () -> k.setKey(v); }
        return null;
    }

    /** Put every setting of {@code f} back to its default. */
    public static void reset(Feature f) {
        for (Setting st : f.getSettings()) {
            Runnable r = RESTORE.get(st);
            if (r != null) {
                try { r.run(); } catch (Throwable ignored) {}
            }
        }
        GuiConfig.markDirty();
    }
}
