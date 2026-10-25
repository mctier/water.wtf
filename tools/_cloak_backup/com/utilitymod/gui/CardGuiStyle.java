package com.utilitymod.gui;

import com.google.gson.JsonObject;

/** Persisted Cards-only visual tokens; no shared HUD shader settings are changed. */
public final class CardGuiStyle {
    public static int accent = 0xFFCE52B3, background = 0xFF8C135E, back = 0xFF3C0737;
    public static int blur = 20, radius = 22, cardRadius = 13, size = 100, gravity = 0;
    public static boolean glass = true, motion = true, tilt = true;
    public static int tiltStrength = 60;
    private CardGuiStyle() {}
    public static JsonObject save() {
        JsonObject o = new JsonObject();
        o.addProperty("accent", accent); o.addProperty("background", background); o.addProperty("back", back);
        o.addProperty("blur", blur); o.addProperty("radius", radius); o.addProperty("cardRadius", cardRadius);
        o.addProperty("size", size); o.addProperty("gravity", gravity); o.addProperty("glass", glass); o.addProperty("motion", motion);
        o.addProperty("tilt", tilt); o.addProperty("tiltStrength", tiltStrength);
        return o;
    }
    private static int number(JsonObject o, String key, int fallback, int min, int max) {
        try { return o.has(key) ? Math.max(min, Math.min(max, o.get(key).getAsInt())) : fallback; }
        catch (RuntimeException ex) { return fallback; }
    }
    private static boolean flag(JsonObject o, String key, boolean fallback) {
        try { return o.has(key) ? o.get(key).getAsBoolean() : fallback; }
        catch (RuntimeException ex) { return fallback; }
    }
    public static void load(JsonObject o) {
        accent = 0xFF000000 | number(o,"accent",accent,Integer.MIN_VALUE,Integer.MAX_VALUE);
        background = 0xFF000000 | number(o,"background",background,Integer.MIN_VALUE,Integer.MAX_VALUE);
        back = 0xFF000000 | number(o,"back",back,Integer.MIN_VALUE,Integer.MAX_VALUE);
        blur=number(o,"blur",blur,0,30); radius=number(o,"radius",radius,4,60);
        cardRadius=number(o,"cardRadius",cardRadius,2,40); size=number(o,"size",size,70,120);
        gravity=number(o,"gravity",gravity,0,100); glass=flag(o,"glass",glass); motion=flag(o,"motion",motion);
        tilt=flag(o,"tilt",tilt); tiltStrength=number(o,"tiltStrength",tiltStrength,0,100);
    }
}
