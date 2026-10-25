package com.utilitymod.cgui;

/**
 * The one theme class the whole toolkit reads. Widgets never hard-code a color -
 * swap a token here and every window, row and control restyles on the next frame.
 */
public final class CuiTheme {
    public static int ACCENT = 0xFF6FCBDC;
    public static int BG_WINDOW = 0xF0111317;
    public static int BG_CARD = 0xFF171A1F;
    public static int BG_CONTROL = 0xFF1D2127;
    public static int TEXT = 0xFFE2E4EA;
    public static int TEXT_MUTED = 0xFF8A8E98;
    public static int BORDER = 0x1FFFFFFF;
    public static int DANGER = 0xFFE05561;

    public static float RADIUS_WINDOW = 10f;
    public static float RADIUS_CONTROL = 4f;
    public static float TEXT_SIZE = 6.5f;
    public static float TEXT_SIZE_SMALL = 6f;
    public static float ROW_H = 14f;
    public static float PAD = 8f;
    public static float GAP = 5f;

    private CuiTheme() {}

    /** Per-channel mix, no allocation; t in 0..1. */
    public static int mix(int a, int b, float t) {
        if (t <= 0f) return a;
        if (t >= 1f) return b;
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF, aa = (a >>> 24) & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF, ba = (b >>> 24) & 0xFF;
        int r = Math.round(ar + (br - ar) * t);
        int g = Math.round(ag + (bg - ag) * t);
        int bl = Math.round(ab + (bb - ab) * t);
        int al = Math.round(aa + (ba - aa) * t);
        return (al << 24) | (r << 16) | (g << 8) | bl;
    }
}
