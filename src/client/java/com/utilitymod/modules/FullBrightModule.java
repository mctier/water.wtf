package com.utilitymod.modules;

import com.utilitymod.core.Module;
import com.utilitymod.gui.Category;
import net.minecraft.client.MinecraftClient;

public class FullBrightModule extends Module {
    private double prevGamma;
    public FullBrightModule() { super(LT.L1, LT.L2, Category.VISUAL); }
    @Override protected void onEnable() {
        var c = MinecraftClient.getInstance();
        prevGamma = c.options.getGamma().getValue();
        c.options.getGamma().setValue(12.0);
    }
    @Override protected void onDisable() {
        MinecraftClient.getInstance().options.getGamma().setValue(prevGamma);
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5a66,0xc4b5,0xa3cc,0xfe23,0x325e,0x5503,0xdf7b,0x9438,0x10aa,0x919a}, new char[]{0x5545,0xc4c0,0x10c6,0xa3a0,0xfe61,0x5a14,0x91f3,0x3239,0x9450,0xdf0f}).toString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3826,0x36b1,0xf15b,0x0023,0x6535,0xa82f,0xfe27,0x6b25,0x426f,0x727e,0x7016,0x7eaf,0x0802,0x16bd,0xfe30,0x460e,0x45e7,0xb24c,0x8c59,0x16dd,0x1771,0x5a9a}, new char[]{0x5add,0x466f,0x8c34,0x171c,0x420e,0xfe10,0xb27d,0x6b17,0x3806,0x16f0,0xfe07,0xa84c,0x086e,0x004a,0x16d8,0x4589,0xf12f,0x7036,0x655a,0x7210,0x36dd,0x7ed6}).toString();
    }
}
