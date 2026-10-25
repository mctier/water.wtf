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
            CuiScreen screen = new CuiScreen(LT.L1);
            Window win = screen.window(LT.L2, 60, 50, 210);
            win.add(new Toggle(LT.L3, crystal::isEnabled, crystal::setEnabled));
            for (var s : crystal.getSettings()) win.add(SettingAdapters.of(s));
            return screen;
        }
        CuiScreen empty = new CuiScreen(LT.L1);
        empty.window(LT.L1, 60, 50, 210).add(new Label(LT.L4, CuiTheme.TEXT_SIZE_SMALL, CuiTheme.TEXT_MUTED, false));
        return empty;
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9db7,0xc795,0xff6a,0x7ec9}, new char[]{0x9dd4,0xc7f2,0xff1f,0x7ea0}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xdbee,0x35c5,0x2ae0,0xc708,0x0b04,0x70c0,0xd876,0x5526,0x8277,0x2a3e,0x73c3,0xc7a5}, new char[]{0xdbad,0xc7d7,0x820e,0x35b6,0xc77c,0x73a2,0x2a8c,0x0b46,0x5553,0x70b2,0x2a4d,0xd802}).toString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6a22,0x57cd,0xd2ab,0x805a,0x8ec1,0x46a0,0xdc7f}, new char[]{0x8e84,0xd2c5,0x803b,0x46c2,0xdc13,0x57a8,0x6a46}).toString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x06ba,0xd0bb,0xec74,0x8316,0x6671,0x23dd,0x3938,0xb5f5,0x0545,0xde91}, new char[]{0x3956,0xec1b,0x0565,0xb598,0x23b2,0x6615,0xdee4,0xd0d7,0x8373,0x06c9}).plainString();
    }
}
