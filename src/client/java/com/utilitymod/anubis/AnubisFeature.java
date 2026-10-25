package com.utilitymod.anubis;

import com.utilitymod.anubis.module.ClientModule;
import com.utilitymod.anubis.module.setting.BooleanSetting;
import com.utilitymod.anubis.module.setting.NumberSetting;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.settings.impl.BoolSetting;
import com.utilitymod.echo.features.settings.impl.ColorSetting;
import com.utilitymod.echo.features.settings.impl.FloatSetting;
import com.utilitymod.echo.features.settings.impl.ModeSetting;

/**
 * Shows an Anubis {@link ClientModule} in the panel GUI's Donut tab. Toggling and every mirrored
 * setting read and write the real module live, so the panel GUI, dropdown GUI and config agree.
 * Text / list settings are edited in the dropdown GUI's Donut tab.
 */
public final class AnubisFeature extends Feature {
    private final ClientModule module;

    public AnubisFeature(ClientModule module) {
        super(new FeatureInfo(module.name(), module.description() == null ? "" : module.description(), Category.DONUT));
        this.module = module;
        for (var st : module.settings()) {
            var mirror = mirror(st);
            if (mirror != null) settings.add(mirror);
        }
    }

    public ClientModule module() { return module; }

    @Override public boolean isEnabled() { return module.isEnabled(); }

    @Override public void setEnabled(boolean enabled) { module.setEnabled(enabled); }

    @Override public void toggle() { module.toggle(); }

    private static com.utilitymod.echo.features.settings.Setting mirror(com.utilitymod.anubis.module.setting.Setting st) {
        java.util.function.Predicate<Object> visible = o -> st.isVisible();
        if (st instanceof BooleanSetting b) {
            return new BoolSetting(st.name(), b.get(), visible) {
                @Override public boolean getValue() { return b.get(); }
                @Override public void setValue(boolean value) { b.set(value); }
            };
        }
        if (st instanceof NumberSetting n) {
            float step = n.step() > 0 ? n.step() : 0.01f;
            return new FloatSetting(st.name(), n.get(), n.min(), n.max(), step, visible) {
                @Override public float getValue() { return n.get(); }
                @Override public void setValue(float value) { n.set(n.snap(value)); }
            };
        }
        if (st instanceof com.utilitymod.anubis.module.setting.ModeSetting mo) {
            CharSequence[] modes = mo.modes().toArray(new CharSequence[0]);
            return new ModeSetting(st.name(), mo.get(), visible, modes) {
                @Override public CharSequence getValue() { return mo.get(); }
                @Override public void setValue(CharSequence cs) { mo.set(cs.toString()); }
                @Override public boolean is(CharSequence mode) { return mo.is(mode.toString()); }
            };
        }
        if (st instanceof com.utilitymod.anubis.module.setting.ColorSetting c) {
            return new ColorSetting(st.name(), c.argb(), visible) {
                @Override public int getARGB() { return c.argb(); }
                @Override public void setFromARGB(int argb) { c.setArgb(argb); }
            };
        }
        return null; // StringSetting / StringListSetting: dropdown GUI only
    }
}
