package com.utilitymod.echo.features.impl.render;

import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventTick;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;

/** Lights the world up by giving you a client-side night-vision effect. */
public class Fullbright extends Feature {

    public Fullbright() {
        super(new FeatureInfo(new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa8be,0x9637,0x82ca,0x37dc,0x0b35,0x1c9a,0x2274,0xb17d,0xd13c,0xbc05}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643}).toString(), LT.L1, Category.RENDER));
    }

    @EventSubscribe
    private void onTick(EventTick event) {
        if (isNull()) return;
        // refresh a short, hidden night-vision effect every tick (ambient so there's no screen pulse)
        mc.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 400, 0, true, false, false));
    }

    @Override
    public void onDisable() {
        super.onDisable();
        if (mc.player != null) mc.player.removeStatusEffect(StatusEffects.NIGHT_VISION);
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3fd8,0x3501,0xa23a,0x7125,0x7806,0x65d0,0xedbe,0xfad7,0x5306,0xaa40,0x109e,0xe465,0xf0f9,0x34fe,0x290c,0x294d,0x764f,0xa802,0x7049,0x6c22,0x6493,0xf037,0xe6c4,0x448b,0x9d83,0x2605,0xfa34,0x2888,0xbff2,0x3d37,0xa9a8,0x3107,0xabde,0x4392,0x0c7a,0x4af5,0xc517,0x0351,0x5ccc,0x1449,0x3751,0xbb6f}, new char[]{0xfa67,0x3fbd,0x4a90,0x0c5a,0x9dea,0xfab9,0x3771,0x703d,0x536e,0x3564,0xe445,0x142d,0x43f3,0x44f9,0x10f5,0x65f0,0x2965,0xeddd,0xa86e,0x6c4b,0x762a,0xaa2e,0x2978,0x34d3,0x7875,0xbf9b,0x3163,0xc572,0xf017,0xabb0,0xbb06,0xe6a3,0x714d,0x5cb8,0x2625,0x0327,0x28e1,0xf08a,0x3d5e,0x64fc,0xa254,0xa981}).toString();
    }
}
