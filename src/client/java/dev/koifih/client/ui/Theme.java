package dev.koifih.client.ui;

import dev.koifih.client.util.Colors;
public final class Theme {
    public enum Mode { DARK, LIGHT }

    private enum Palette {
        ON_ACCENT(0xFF101010, 0xFF101010),
        TEXT(0xFFEAEAEA, 0xFF141414),
        MUTED(0xFF858585, 0xFF666666),
        DIM(0xFF999999, 0xFF4E4E4E),
        SIDEBAR(0xFF161616, 0xFFD6D6D6),
        MAIN(0xFF0F0F0F, 0xFFE3E3E3),
        ROW(0xFF1C1C1C, 0xFFF1F1F1),
        OVERLAY(0xFF171717, 0xFFFFFFFF),
        FIELD(0xFF141414, 0xFFE6E6E6),
        CONTROL(0xFF262626, 0xFFDADADA),
        CONTROL_ACTIVE(0xFF3A3A3A, 0xFFC2C2C2),
        TRACK(0xFF343434, 0xFFCACACA),
        POPUP(0xFF202020, 0xFFFFFFFF),
        POPUP_BORDER(0x00000000, 0xFFC8C8C8),
        TOGGLE_OFF(0xFF171717, 0xFFD0D0D0),
        TOGGLE_OFF_BORDER(0xFF2B2B2B, 0xFFB4B4B4),
        THUMB_OFF(0xFF484848, 0xFF7A7A7A);

        private final int dark;
        private final int light;

        Palette(int dark, int light) {
            this.dark = dark;
            this.light = light;
        }

        int in(Mode mode) {
            return mode == Mode.LIGHT ? light : dark;
        }
    }

    public static final int DEFAULT_ACCENT = 0xB8DDB0;

    public static int ACCENT;
    public static int ON_ACCENT;
    public static int TEXT;
    public static int MUTED;
    public static int DIM;
    public static int SIDEBAR;
    public static int MAIN;
    public static int ROW;
    public static int OVERLAY;
    public static int FIELD;
    public static int CONTROL;
    public static int CONTROL_ACTIVE;
    public static int TRACK;
    public static int POPUP;
    public static int POPUP_BORDER;
    public static int TOGGLE_OFF;
    public static int TOGGLE_OFF_BORDER;
    public static int THUMB_OFF;

    private static final Palette[] COLORS = Palette.values();
    private static final int[] from = new int[COLORS.length];
    private static final int[] to = new int[COLORS.length];
    private static final Transition blend = new Transition(1f, 220);
    private static final Transition accentBlend = new Transition(1f, 120);
    private static int accentFrom;
    private static int accentTo;
    private static int accentRgb = DEFAULT_ACCENT;
    private static Mode mode = Mode.DARK;

    static {
        for (Palette color : COLORS) {
            from[color.ordinal()] = color.in(mode);
            to[color.ordinal()] = color.in(mode);
        }
        accentFrom = Colors.opaque(DEFAULT_ACCENT);
        accentTo = accentFrom;
        update();
    }

    public static Mode mode() {
        return mode;
    }

    public static void setMode(Mode newMode) {
        if (newMode == mode) return;
        float t = blend.value();
        for (Palette color : COLORS) {
            from[color.ordinal()] = Colors.lerp(from[color.ordinal()], to[color.ordinal()], t);
            to[color.ordinal()] = color.in(newMode);
        }
        mode = newMode;
        blend.snap(0f);
        blend.set(1f);
        setAccent(accentRgb);
    }

    public static int accentRgb() {
        return accentRgb;
    }

    public static void setAccent(int rgb) {
        accentRgb = Colors.rgb(rgb);
        accentFrom = ACCENT;
        accentTo = Colors.opaque(mode == Mode.LIGHT ? Colors.darken(accentRgb, 0.28f) : accentRgb);
        accentBlend.snap(0f);
        accentBlend.set(1f);
    }

    public static void update() {
        float t = blend.value();
        ACCENT = Colors.lerp(accentFrom, accentTo, accentBlend.value());
        ON_ACCENT = blended(Palette.ON_ACCENT, t);
        TEXT = blended(Palette.TEXT, t);
        MUTED = blended(Palette.MUTED, t);
        DIM = blended(Palette.DIM, t);
        SIDEBAR = blended(Palette.SIDEBAR, t);
        MAIN = blended(Palette.MAIN, t);
        ROW = blended(Palette.ROW, t);
        OVERLAY = blended(Palette.OVERLAY, t);
        FIELD = blended(Palette.FIELD, t);
        CONTROL = blended(Palette.CONTROL, t);
        CONTROL_ACTIVE = blended(Palette.CONTROL_ACTIVE, t);
        TRACK = blended(Palette.TRACK, t);
        POPUP = blended(Palette.POPUP, t);
        POPUP_BORDER = blended(Palette.POPUP_BORDER, t);
        TOGGLE_OFF = blended(Palette.TOGGLE_OFF, t);
        TOGGLE_OFF_BORDER = blended(Palette.TOGGLE_OFF_BORDER, t);
        THUMB_OFF = blended(Palette.THUMB_OFF, t);
    }

    private static int blended(Palette color, float t) {
        return Colors.lerp(from[color.ordinal()], to[color.ordinal()], t);
    }

    private Theme() {}
}
