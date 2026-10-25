package com.utilitymod.echo.features.settings;

import com.utilitymod.echo.features.settings.impl.BoolSetting;
import com.utilitymod.echo.features.settings.impl.ColorSetting;
import com.utilitymod.echo.features.settings.impl.FloatSetting;
import com.utilitymod.echo.features.settings.impl.HotbarSelectionSetting;
import com.utilitymod.echo.features.settings.impl.IntSetting;
import com.utilitymod.echo.features.settings.impl.KeybindSetting;
import com.utilitymod.echo.features.settings.impl.ModeSetting;
import com.utilitymod.echo.features.settings.impl.MultiModeSetting;
import com.utilitymod.echo.features.settings.impl.RangeSetting;
import com.utilitymod.echo.features.settings.impl.RegistryPickerSetting;
import com.utilitymod.echo.features.settings.impl.StringSetting;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * String persistence for echo settings (echo's FeatureConfig is not ported).
 * Values round-trip through {@code config/utilitymod.json}; unknown or
 * malformed values are ignored by the caller.
 */
public final class SettingIO {
    private SettingIO() {}

    public static String save(Setting s) {
        if (s instanceof com.utilitymod.echo.features.settings.impl.InputSimulationSetting sim) {
            com.google.gson.JsonObject value = new com.google.gson.JsonObject();
            value.addProperty(LT.L1, sim.getValue());
            value.addProperty(LT.L2, sim.getInputDelay().getValue());
            com.google.gson.JsonArray keys = new com.google.gson.JsonArray();
            for (var key : sim.getInputSlots()) keys.add(key.getKey());
            value.add(LT.L3, keys);
            return value.toString();
        }
        if (s instanceof StringSetting st && st.isAllowMultiple()) {
            com.google.gson.JsonObject value = new com.google.gson.JsonObject();
            value.addProperty(LT.L4, st.getValue());
            com.google.gson.JsonArray entries = new com.google.gson.JsonArray();
            st.getEntries().forEach(entries::add); value.add(LT.L5, entries);
            return value.toString();
        }
        if (s instanceof BoolSetting b) return Boolean.toString(b.getValue());
        if (s instanceof IntSetting i) return Integer.toString(i.getValue());
        if (s instanceof FloatSetting f) return Float.toString(f.getValue());
        if (s instanceof RangeSetting r) return r.getMinValue() + LT.L6 + r.getMaxValue();
        if (s instanceof ModeSetting m) return m.getValue().toString();
        if (s instanceof MultiModeSetting mm) {
            List<String> on = new ArrayList<>();
            for (Map.Entry<CharSequence, Boolean> e : mm.getOptionsSequenceView().entrySet()) {
                if (e.getValue()) on.add(e.getKey().toString());
            }
            return String.join(LT.L7, on);
        }
        if (s instanceof ColorSetting c) return Integer.toHexString(c.getARGB());
        if (s instanceof KeybindSetting k) return Integer.toString(k.getKey());
        if (s instanceof StringSetting st) return st.getValue() == null ? "" : st.getValue();
        if (s instanceof RegistryPickerSetting<?> rp) {
            List<String> ids = new ArrayList<>();
            for (Identifier id : rp.getSelectedIds()) ids.add(id.toString());
            return String.join(LT.L7, ids);
        }
        if (s instanceof HotbarSelectionSetting h) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 9; i++) sb.append(h.isSelected(i) ? '1' : '0');
            return sb.toString();
        }
        return null;
    }

    public static void load(Setting s, String v) {
        if (v == null) return;
        if (s instanceof com.utilitymod.echo.features.settings.impl.InputSimulationSetting sim && v.startsWith(LT.L8)) {
            var value = com.google.gson.JsonParser.parseString(v).getAsJsonObject();
            boolean enabled = value.get(LT.L1).getAsBoolean();
            int delay = value.get(LT.L2).getAsInt();
            var keys = value.getAsJsonArray(LT.L3);
            if (keys.size() != sim.getInputSlots().length) throw new IllegalArgumentException(LT.L9);
            int[] codes = new int[keys.size()];
            for (int i=0;i<codes.length;i++) codes[i]=keys.get(i).getAsInt();
            sim.setValue(enabled); sim.getInputDelay().setValue(delay);
            for (int i=0;i<codes.length;i++) sim.getInputSlots()[i].setKey(codes[i]);
            return;
        }
        if (s instanceof StringSetting st && st.isAllowMultiple() && v.startsWith(LT.L8)) {
            var value = com.google.gson.JsonParser.parseString(v).getAsJsonObject();
            List<String> entries = new ArrayList<>();
            for (var entry : value.getAsJsonArray(LT.L5)) entries.add(entry.getAsString());
            for (String old : List.copyOf(st.getEntries())) st.removeEntry(old);
            st.setValue(value.get(LT.L4).getAsString());
            entries.forEach(st::addEntry);
            return;
        }
        if (s instanceof BoolSetting b) b.setValue(Boolean.parseBoolean(v));
        else if (s instanceof IntSetting i) i.setValue(Integer.parseInt(v));
        else if (s instanceof FloatSetting f) f.setValue(Float.parseFloat(v));
        else if (s instanceof RangeSetting r) {
            String[] p = v.split(LT.L6);
            r.setMinValue(Float.parseFloat(p[0]));
            r.setMaxValue(Float.parseFloat(p[1]));
        } else if (s instanceof ModeSetting m) {
            for (CharSequence mode : m.getModes()) if (mode.toString().equals(v)) m.setValue(mode);
        } else if (s instanceof MultiModeSetting mm) {
            List<String> on = List.of(v.split(LT.L7));
            for (CharSequence k : List.copyOf(mm.getOptionsSequenceView().keySet())) mm.setOption(k, on.contains(k.toString()));
        } else if (s instanceof ColorSetting c) c.setFromARGB((int) Long.parseLong(v, 16));
        else if (s instanceof KeybindSetting k) k.setKey(Integer.parseInt(v));
        else if (s instanceof StringSetting st) st.setValue(v);
        else if (s instanceof RegistryPickerSetting<?> rp) {
            List<Identifier> ids = new ArrayList<>();
            for (String id : v.split(LT.L7)) {
                Identifier parsed = id.isBlank() ? null : Identifier.tryParse(id);
                if (parsed != null) ids.add(parsed);
            }
            rp.setSelectedIds(ids);
        } else if (s instanceof HotbarSelectionSetting h) {
            for (int i = 0; i < Math.min(9, v.length()); i++) h.setSelected(i, v.charAt(i) == '1');
        }
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x39ac,0x0f40,0x303b,0x152a,0xb3b5,0x3637,0x21ae}, new char[]{0x3652,0x0f2e,0x305a,0xb3d7,0x39c0,0x21cb,0x154e}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x57c3,0x66cb,0xf7d0,0x6d1d,0xd8ec}, new char[]{0xd888,0x6d78,0x66a7,0x57a2,0xf7a9}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb4ef,0xd83e,0xc721,0x6bd3}, new char[]{0xc74a,0x6bb6,0xb496,0xd84d}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4c32,0x678a,0x71dd,0xfe18,0x7cc9}, new char[]{0xfe6e,0x4c53,0x7ca5,0x71a8,0x67ef}).plainString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x32df,0x3980,0x7231,0xc58f,0x7b33,0xb2ca,0x8490}, new char[]{0x84f5,0xc5e1,0xb2be,0x7243,0x39e9,0x7b56,0x32ac}).plainString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x159f}, new char[]{0x15a5,0x4b0a,0x09ef,0x67d1}).plainString();
        static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd70e}, new char[]{0xd722,0x731d,0xe083,0x902f}).plainString();
        static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf559}, new char[]{0xf522,0x9181,0x6a93,0x5251}).plainString();
        static final String L9 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x58dd,0x7de4,0x9749,0x45b8,0xa0dc,0xbc48,0x9f75,0xa43a,0xbce4,0x3d8c,0xe791,0x23a7,0x4b14,0x9024,0x6730,0x3a36}, new char[]{0xbc01,0x58b3,0x3a46,0xbc91,0x45cc,0x9004,0xe7e2,0x9725,0x23c8,0x7d90,0x6710,0xa0bf,0xa455,0x3df9,0x9f1b,0x4b60}).toString();
    }
}
