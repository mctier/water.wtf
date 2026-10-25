package com.utilitymod.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.utilitymod.echo.features.impl.render.HudOverlay;
import dev.koifih.client.ui.Theme;

import java.util.ArrayList;
import java.util.List;

/** User-made themes: accent + dark/light + full glass look, saved by name in the config. */
public final class CustomThemes {
    public record Data(String name, int accent, boolean dark, String mode, float tint, float blur, int passes, float motion) {}

    public static final List<Data> LIST = new ArrayList<>();

    private CustomThemes() {}

    /** Snapshot of the current look. */
    public static Data capture(String name) {
        HudOverlay hud = HudOverlay.instance;
        return new Data(name, Theme.accentRgb(), Theme.mode() == Theme.Mode.DARK,
                hud == null ? LT.L1 : hud.glassModeSetting().getValue().toString(),
                hud == null ? 0.25f : hud.tintStrengthSetting().getValue(),
                hud == null ? 2f : hud.glassBlurRadius().getValue(),
                hud == null ? 1 : hud.glassBlurIterations().getValue(),
                hud == null ? 1f : hud.motionSetting().getValue());
    }

    public static void saveCurrent(String name) {
        String n = name.trim();
        if (n.isEmpty()) return;
        LIST.removeIf(d -> d.name().equalsIgnoreCase(n));
        LIST.add(capture(n));
        GuiConfig.save();
    }

    public static void apply(Data d) {
        Theme.setMode(d.dark() ? Theme.Mode.DARK : Theme.Mode.LIGHT);
        Theme.setAccent(d.accent());
        HudOverlay hud = HudOverlay.instance;
        if (hud != null) hud.applyPreset(d.mode(), d.tint(), d.blur(), d.passes(), d.motion());
        GuiConfig.save();
    }

    public static void remove(Data d) {
        LIST.remove(d);
        GuiConfig.save();
    }

    public static JsonArray save() {
        JsonArray a = new JsonArray();
        for (Data d : LIST) {
            JsonObject o = new JsonObject();
            o.addProperty(LT.L2, d.name());
            o.addProperty(LT.L3, d.accent());
            o.addProperty(LT.L4, d.dark());
            o.addProperty(LT.L5, d.mode());
            o.addProperty(LT.L6, d.tint());
            o.addProperty(LT.L7, d.blur());
            o.addProperty(LT.L8, d.passes());
            o.addProperty(LT.L9, d.motion());
            a.add(o);
        }
        return a;
    }

    public static void load(JsonArray a) {
        LIST.clear();
        if (a == null) return;
        for (var e : a) {
            try {
                JsonObject o = e.getAsJsonObject();
                LIST.add(new Data(o.get(LT.L2).getAsString(), o.get(LT.L3).getAsInt(), o.get(LT.L4).getAsBoolean(),
                        o.get(LT.L5).getAsString(), o.get(LT.L6).getAsFloat(), o.get(LT.L7).getAsFloat(),
                        o.get(LT.L8).getAsInt(), o.get(LT.L9).getAsFloat()));
            } catch (Exception ignored) {
            }
        }
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa39e,0xee3b,0xdaf9,0xcb3d,0xf882,0xbf18}, new char[]{0xa3d2,0xda90,0xbf69,0xf8f7,0xcb54,0xee5f}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x117a,0x5faa,0xc460,0xf928}, new char[]{0x5fc4,0xc401,0xf945,0x111f}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9fb5,0xe408,0x5e6a,0xf784,0x0d8b,0x34fa}, new char[]{0x0dea,0x3499,0x5e09,0x9fd0,0xf7ea,0xe47c}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa4bc,0xecb9,0xbcb2,0xb0f5}, new char[]{0xecdd,0xbcd3,0xb087,0xa4d7}).plainString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x92ef,0x63ef,0x8919,0x1a93}, new char[]{0x8974,0x6380,0x928b,0x1af6}).plainString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa704,0x40d7,0x671d,0x0581}, new char[]{0x40a3,0x6774,0xa76a,0x05f5}).plainString();
        static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb316,0xdfd6,0x1133,0x6e7f}, new char[]{0x1151,0xdfba,0x6e0a,0xb364}).plainString();
        static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xac9f,0x88d0,0xbcc5,0x3da7,0xae4d,0x5962}, new char[]{0x3dd7,0x88b1,0xacec,0x5911,0xbca0,0xae3e}).plainString();
        static final String L9 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xbb5d,0x685b,0x8188,0x433c,0x4282,0xd094}, new char[]{0xd0f9,0x6834,0x4348,0xbb34,0x81e7,0x42ec}).plainString();
    }
}
