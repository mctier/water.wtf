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
        o.addProperty(LT.L1, accent); o.addProperty(LT.L2, background); o.addProperty(LT.L3, back);
        o.addProperty(LT.L4, blur); o.addProperty(LT.L5, radius); o.addProperty(LT.L6, cardRadius);
        o.addProperty(LT.L7, size); o.addProperty(LT.L8, gravity); o.addProperty(LT.L9, glass); o.addProperty(LT.L10, motion);
        o.addProperty(LT.L11, tilt); o.addProperty(LT.L12, tiltStrength);
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
        accent = 0xFF000000 | number(o,LT.L1,accent,Integer.MIN_VALUE,Integer.MAX_VALUE);
        background = 0xFF000000 | number(o,LT.L2,background,Integer.MIN_VALUE,Integer.MAX_VALUE);
        back = 0xFF000000 | number(o,LT.L3,back,Integer.MIN_VALUE,Integer.MAX_VALUE);
        blur=number(o,LT.L4,blur,0,30); radius=number(o,LT.L5,radius,4,60);
        cardRadius=number(o,LT.L6,cardRadius,2,40); size=number(o,LT.L7,size,70,120);
        gravity=number(o,LT.L8,gravity,0,100); glass=flag(o,LT.L9,glass); motion=flag(o,LT.L10,motion);
        tilt=flag(o,LT.L11,tilt); tiltStrength=number(o,LT.L12,tiltStrength,0,100);
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6707,0xfbc8,0x7242,0xd6a6,0x1bea,0x7ef8}, new char[]{0xd6c7,0xfbab,0x1b89,0x6762,0x722c,0x7e8c}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x096c,0xb8d6,0x0b03,0x2e8b,0xbd52,0x1650,0x8413,0x0728,0x22f6,0x5c51}, new char[]{0xbd30,0x2eea,0x0b60,0x163b,0xb8b1,0x091e,0x847c,0x075d,0x5c3f,0x2292}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x687e,0xc302,0xaf02,0xaa9e}, new char[]{0xc360,0xaf63,0xaafd,0x6815}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x621d,0x4442,0x3c2a,0x0864}, new char[]{0x627f,0x0808,0x3c5f,0x4430}).plainString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0ee6,0x836a,0x5832,0x3777,0x512d,0x39be}, new char[]{0x3705,0x5853,0x0e82,0x5144,0x831f,0x39cd}).plainString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1bb2,0x438b,0x42e4,0xecf7,0xc016,0xa3c4,0x024a,0x17dc,0xab59,0x90b7}, new char[]{0xc075,0x90d6,0x17ae,0xab3d,0xeca5,0x43ea,0xa3a0,0x0223,0x1bc7,0x4297}).plainString();
        static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa3f7,0x910c,0x7c98,0x6544}, new char[]{0x917f,0x652d,0xa38d,0x7cfd}).plainString();
        static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1f0f,0x6a2e,0xb77b,0xe86e,0xda61,0xd44c,0x92df}, new char[]{0xda06,0x1f7d,0x6a4f,0xd43a,0x92b6,0xb70f,0xe817}).plainString();
        static final String L9 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0038,0x04ff,0x7f9e,0x6cf5,0xa7b1}, new char[]{0x0498,0x6c99,0xa7d0,0x004b,0x7fed}).plainString();
        static final String L10 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8357,0x827a,0x4c98,0xef9e,0x807a,0xff18}, new char[]{0xff75,0xeff1,0x820e,0x8013,0x8338,0x4cf6}).plainString();
        static final String L11 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc1bc,0xd28e,0x0b58,0xfb1d}, new char[]{0xd2fa,0xfb74,0x0b34,0xc1c8}).plainString();
        static final String L12 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0344,0xa8d9,0x2548,0xae4c,0x612e,0x545a,0x6f86,0xff2a,0x1f05,0xf9df,0xfeaf,0xc481}, new char[]{0x615a,0xae25,0xfec3,0x253c,0xf98c,0x0330,0x1f77,0x543f,0xff44,0xa8be,0x6ff2,0xc4e9}).plainString();
    }
}
