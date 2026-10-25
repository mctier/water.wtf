package com.utilitymod.echo.screens.frame.glass;

import java.awt.Color;

/** echo's GUI accent, taken from this mod's wing theme. */
public final class GlassUIConstants {
    private GlassUIConstants() {}

    public static Color ACCENT_FOR_RENDERING = new Color(0xB8DDB0);

    public static void syncTheme() {
        ACCENT_FOR_RENDERING = new Color(dev.koifih.client.ui.Theme.accentRgb());
    }
}
