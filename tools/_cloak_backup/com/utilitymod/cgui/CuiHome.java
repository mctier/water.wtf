package com.utilitymod.cgui;

import com.utilitymod.core.Module;
import com.utilitymod.core.ModuleManager;

import static com.utilitymod.cgui.CuiTheme.*;
import com.utilitymod.echo.features.impl.combat.CrystalBurst;
import net.minecraft.client.MinecraftClient;

/** Example window: one module, its real settings, zero duplicated state. */
public final class CuiHome {
    private CuiHome() {}

    public static void open() {
        MinecraftClient.getInstance().setScreen(open0());
    }

    /** Builds the example screen for the hot-open path. */
    public static CuiScreen open0() {
        for (Module m : ModuleManager.all()) {
            if (!(m instanceof CrystalBurst crystal)) continue;
            CuiScreen screen = new CuiScreen("cgui");
            Window win = screen.window("CrystalBurst", 60, 50, 210);
            win.add(new Toggle("Enabled", crystal::isEnabled, crystal::setEnabled));
            for (var s : crystal.getSettings()) win.add(SettingAdapters.of(s));
            return screen;
        }
        CuiScreen empty = new CuiScreen("cgui");
        empty.window("cgui", 60, 50, 210).add(new Label("no modules", CuiTheme.TEXT_SIZE_SMALL, CuiTheme.TEXT_MUTED, false));
        return empty;
    }
}
