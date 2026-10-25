package com.utilitymod.gui;

import com.utilitymod.UtilityMod;
import net.minecraft.util.Identifier;

/**
 * NightX visual tokens for the wing (single source of truth, no literals
 * at call sites). Derived from the bundled NightX reference screenshots and
 * the skill guides (gui-visuals.md, nightx-minecraft-style.md):
 * near-black shell, dark cards, light-cyan accent, white/muted text.
 *
 * <p>Backdrop is dim-only (no blur pass - user decision). Rounded corners and
 * glow come from {@link Rounded} (CPU SDF port of the Icons Pack shaders).
 * All motion is delta-time, interruptible, easeOutCubic.
 */
public final class GuiTheme {
    private GuiTheme() {}

    // ---- layout (scaled GUI coordinates) ----
    public static final int WINDOW_W = 360;
    public static final int WINDOW_H = 300;
    public static final int WINDOW_PAD = 8;
    public static final int HEADER_H = 32;
    public static final int CONTENT_TITLE_H = 18;
    public static final int FOOTER_H = 20;
    public static final int RAIL_W = 96;
    public static final int RAIL_ITEM_H = 24;
    public static final int RAIL_GAP = 4;
    public static final int RAIL_ICON = 14;
    public static final int CARD_H = 44;
    public static final int CARD_GAP = 8;
    public static final int SEARCH_W = 72;
    public static final int SEARCH_H = 16;
    public static final int CLOSE_S = 14;
    public static final int TOGGLE_W = 26;
    public static final int TOGGLE_H = 14;
    public static final int SCROLLBAR_W = 3;

    // ---- roundness (mirrors Icons Pack roundness uniform) ----
    public static final float RADIUS_WINDOW = 12f;
    public static final float RADIUS_CARD = 10f;
    public static final float RADIUS_PILL = 12f;
    public static final float RADIUS_TOGGLE = 7f;

    // ---- glow (mirrors glow.frag uniforms) ----
    public static final int GLOW = 0x3033CCFF;
    public static final float GLOW_SIZE = 10f;
    public static final float GLOW_STRENGTH = 0.35f;

    // ---- NightX palette (sampled from reference) ----
    public static final int DIM = 0x8C000000;
    public static final int WINDOW_BG = 0xF50B1016;
    public static final int HEADER_TEXT = 0xFFFFFFFF;
    public static final int HEADER_SUB = 0xFF8E8E96;
    public static final int RAIL_TEXT = 0xFFC9CAD0;
    public static final int RAIL_TEXT_DIM = 0xFF8294A6;
    public static final int CARD_BG = 0xFF171E27;
    public static final int CARD_BG_HOVER = 0xFF202A36;
    public static final int ACCENT = 0xFFA9E4FF;
    public static final int ACCENT_DIM = 0x59A9E4FF;
    public static final int TEXT_PRIMARY = 0xFFFFFFFF;
    public static final int TEXT_SECONDARY = 0xFFA9BACB;
    public static final int TEXT_ON_ACCENT = 0xFF102027;
    public static final int TOGGLE_ON_TRACK = 0xFFA9E4FF;
    public static final int TOGGLE_OFF_TRACK = 0xFF343538;
    public static final int TOGGLE_ON_THUMB = 0xFF0B0B0D;
    public static final int TOGGLE_OFF_THUMB = 0xFF8E8E93;
    public static final int SEARCH_BG = 0xFF101720;
    public static final int SEARCH_TEXT = 0xFFD8D9DE;
    public static final int SEARCH_HINT = 0xFF8A9BAD;
    public static final int POPOVER_BG = 0xFA111923;
    public static final int BORDER_SUBTLE = 0x1FFFFFFF;

    // ---- liquid-glass dressing (ported from adin bordered shell +
    // LiquidGlassShader fresnel/rim/tint, faked with layered fills) ----
    public static final int BORDER = 0xFF293746;
    public static final int SPEC_TOP_A = 0x1CFFFFFF; // white 28/255
    public static final int SPEC_TOP_B = 0x00FFFFFF; // transparent
    public static final int GLASS_TINT = 0x0800C8E8; // cyan wash 20/255
    public static final int RIM = 0x1EA9E4FF; // cyan rim 30/255
    public static final int CARD_SPEC = 0x1AFFFFFF; // white 10/255
    public static final int PILL_SPEC_A = 0x40FFFFFF; // white 25/255

    // ---- toasts ----
    public static final int TOAST_W = 170;
    public static final int TOAST_H = 28;
    public static final float TOAST_LIFE = 2.4f;

    // ---- motion (seconds) ----
    public static final float T_OPEN = 0.20f;
    public static final float T_PILL = 0.20f;
    public static final float T_HOVER = 0.15f;
    public static final float T_TOGGLE = 0.18f;
    public static final float T_POPOVER = 0.20f;
    public static final float T_SEARCH = 0.15f;
    public static final float T_SCROLL = 0.12f;

    // ---- shared textures (Icons Pack, white monochrome) ----
    public static final Identifier LOGO = Identifier.of(UtilityMod.MOD_ID, LT.L1);
    public static final int LOGO_W = 32;
    public static final int LOGO_H = 32;

    // ---- helpers ----

    public static int withAlpha(int argb, float mult) {
        int a = Math.round(((argb >>> 24) & 0xFF) * mult);
        if (a < 0) a = 0;
        if (a > 255) a = 255;
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    /** Frame-rate independent exponential approach (interruptible by retarget). */
    public static float approach(float current, float target, float dt, float duration) {
        if (duration <= 0f) return target;
        float k = 1f - (float) Math.exp(-dt / duration * 3f);
        float v = current + (target - current) * k;
        if (Math.abs(target - v) < 0.0015f) return target;
        return v;
    }

    public static float easeOutCubic(float t) {
        if (t <= 0f) return 0f;
        if (t >= 1f) return 1f;
        float u = 1f - t;
        return 1f - u * u * u;
    }

    public static int lerpColor(int from, int to, float t) {
        if (t <= 0f) return from;
        if (t >= 1f) return to;
        int a = Math.round(lerp((from >>> 24) & 0xFF, (to >>> 24) & 0xFF, t));
        int r = Math.round(lerp((from >>> 16) & 0xFF, (to >>> 16) & 0xFF, t));
        int g = Math.round(lerp((from >>> 8) & 0xFF, (to >>> 8) & 0xFF, t));
        int b = Math.round(lerp((from) & 0xFF, (to) & 0xFF, t));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7743,0x0fed,0x90a7,0x565f,0xc9e9,0xd11f,0xd972,0xca16,0xe8cb,0xa8e8,0x7228,0x2b03,0x2370,0x907f,0x108c,0x2be7,0xd05d,0xc1b5,0x389a,0x6f8c,0xcd9b,0x45af,0x52c3,0x730d,0x14dc,0x16df,0x5b76,0x3930,0x5b9f,0x3f44}, new char[]{0xd906,0xd17a,0x3f3c,0x52b7,0x45da,0x2b71,0x5bfa,0xc99a,0x2bc8,0x7724,0xc1c0,0xa881,0x5670,0x14b5,0x6fef,0x16b0,0x395e,0xd02e,0x235f,0x10e0,0xcdf4,0x736a,0x5b19,0xca49,0x721b,0x904d,0x38b4,0xe8bb,0x0f83,0x90c0}).plainString();
    }
}
