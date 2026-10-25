package com.utilitymod.cgui;

import com.utilitymod.echo.features.settings.Setting;
import com.utilitymod.echo.features.settings.impl.BoolSetting;
import com.utilitymod.echo.features.settings.impl.ColorSetting;
import com.utilitymod.echo.features.settings.impl.FloatSetting;
import com.utilitymod.echo.features.settings.impl.IntSetting;
import com.utilitymod.echo.features.settings.impl.KeybindSetting;
import com.utilitymod.echo.features.settings.impl.ModeSetting;
import com.utilitymod.echo.features.settings.impl.MultiModeSetting;
import com.utilitymod.echo.utils.CloakUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.utilitymod.cgui.CuiTheme.*;

/** Bindings from the existing Setting objects into widgets. Reads and writes
 *  go straight through the settings API, so profile saves and the web UI
 *  stay in sync with anything touched through this toolkit. */
public final class SettingAdapters {
    private SettingAdapters() {}

    public static Widget of(Setting s) {
        Widget w;
        if (s instanceof BoolSetting b) {
            w = new Toggle(b.getName(), b::getValue, b::setValue);
        } else if (s instanceof IntSetting i) {
            w = new Slider(i.getName(), i.getMinValue(), i.getMaxValue(), 1,
                    i::getValue, v -> i.setValue((int) Math.round(v)), i.getSuffix());
        } else if (s instanceof FloatSetting f) {
            float step = Math.max(0.01f, f.getIncrement());
            w = new Slider(f.getName(), f.getMinValue(), f.getMaxValue(), step,
                    f::getValue, (double v) -> f.setValue((float) v), f.getSuffix());
        } else if (s instanceof KeybindSetting k) {
            w = new KeybindButton(k.getName(), k::getKey, k::setKey);
        } else if (s instanceof ModeSetting m) {
            w = modeDropdown(m);
        } else if (s instanceof ColorSetting c) {
            ColorPicker picker = new ColorPicker(stringOf(c.getName()), c);
            w = picker;
        } else if (s instanceof MultiModeSetting mm) {
            w = multiPane(mm);
        } else {
            w = new Label(s.getName(), TEXT_SIZE_SMALL, TEXT_MUTED, false);
        }
        w.visible = s.isVisible();
        return w;
    }

    /** Real dropdown over the mode list; picks map back to the live CharSequence. */
    private static Widget modeDropdown(ModeSetting m) {
        Dropdown drop = new Dropdown(String.valueOf(m.getName()), () -> {
            List<String> opts = new ArrayList<>(m.getModes().size());
            for (CharSequence mode : m.getModes()) opts.add(display(mode));
            return opts;
        }, () -> display(m.getValueSequence()), picked -> {
            for (CharSequence mode : m.getModes()) {
                if (same(mode, picked)) {
                    m.setValue(mode);
                    return;
                }
            }
        });
        drop.h = 22f;
        return drop;
    }

    /** One toggle per option bound through setOption; pane autosizes to the list. */
    private static Widget multiPane(MultiModeSetting mm) {
        VStack pane = new VStack().pad(0).gap(4f);
        Map<CharSequence, Boolean> opts = mm.getOptionsSequenceView();
        for (CharSequence key : opts.keySet()) {
            String shown = display(key);
            pane.add(new Toggle(shown, () -> mm.isEnabled(key), value -> mm.setOption(key, value)));
        }
        pane.h = pane.contentHeight() + 2f;
        return pane;
    }

    /** Harness law: CharSequence labels display through the cloak form, never plain. */
    private static String display(CharSequence cs) {
        return CloakUtil.heapSafe(cs);
    }

    private static String stringOf(CharSequence cs) {
        return CloakUtil.heapSafe(cs);
    }

    private static boolean same(CharSequence cs, String picked) {
        return display(cs).contentEquals(picked);
    }
}
