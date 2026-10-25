package com.utilitymod.echo.features.impl.render;

import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventPacketReceive;
import com.utilitymod.echo.event.impl.EventRender2DGui;
import com.utilitymod.echo.event.impl.EventTick;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.settings.impl.BoolSetting;
import com.utilitymod.echo.features.settings.impl.IntSetting;
import com.utilitymod.echo.features.settings.impl.ModeSetting;
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket;

/** Client-side time of day and weather (only your view changes). */
public class WorldTime extends Feature {
    private final IntSetting time = new IntSetting(LT.L1, 13000, 0, 24000);
    private final BoolSetting cycle = new BoolSetting(LT.L2, false);
    private final ModeSetting weather = new ModeSetting(LT.L3, LT.L4, LT.L5, LT.L4, LT.L6, LT.L7);

    private long animTime;

    public WorldTime() {
        super(new FeatureInfo(new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa8a4,0x9626,0x82c4,0x37da,0x0b08,0x1c9f,0x226a,0xb12f,0xd12d,0xbc05}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643}).toString(), new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6d61,0x82de,0x2271,0x11f5,0x9627,0x8ae4,0x37d0,0xb983,0x50ca,0xbc0c,0x1c9b,0x0b71,0x76b9,0x4a4a,0xebc1,0x04f1,0x1812,0x9e51,0xc5d4,0x93a5,0x7ece,0x5f6e,0xe732,0x6542,0xa435,0xfcee,0x3fbd,0x335d,0xf88a,0xd139,0xb17b,0x2a9e,0xd979,0xf040,0xa8a7}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87,0x76d4,0xf025,0x4a6a,0xc5bb,0x5f08,0xd959,0x50ae,0x2aff,0xa44c,0x3f9d,0xb9e2,0x3333,0x8a80,0x04d1,0x9e26,0x1877,0x93c4,0x6d15,0xe75a,0x7eab,0xf8f8}).toString(), Category.RENDER));
    }

    @EventSubscribe
    private void onPacket(EventPacketReceive event) {
        // the server re-sends time every second; keep ours
        if (event.getPacket() instanceof WorldTimeUpdateS2CPacket) event.cancel();
    }

    private void apply() {
        if (mc.world == null) return;
        long t = cycle.getValue() ? animTime : time.getValue();
        mc.world.getLevelProperties().setTimeOfDay(t);
        switch (weather.getValue().toString()) {
            case "Clear" -> { mc.world.setRainGradient(0f); mc.world.setThunderGradient(0f); }
            case "Rain" -> { mc.world.setRainGradient(1f); mc.world.setThunderGradient(0f); }
            case "Thunder" -> { mc.world.setRainGradient(1f); mc.world.setThunderGradient(1f); }
            default -> {}
        }
    }

    @EventSubscribe
    private void onTick(EventTick event) {
        animTime = cycle.getValue() ? (animTime + 20) % 24000 : time.getValue();
        apply();
    }

    @EventSubscribe
    private void onRender(EventRender2DGui event) {
        apply();
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x49f6,0x43cf,0xef91,0xba48}, new char[]{0xba1c,0x43a6,0xeffc,0x4993}).toString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6a38,0xdc41,0x10ec,0x5a54,0x721b,0x13c7,0x68b1,0x4092,0x8558,0xe857,0x1066,0x3b51,0xe61b,0x9f71,0xcd9b,0x1d1d,0xdeaa}, new char[]{0x8519,0x3b3f,0x5a3d,0x1081,0xcdfa,0x1012,0x68d4,0xdc61,0x725f,0xe67a,0xded3,0x1d3d,0x40d1,0x9f08,0x13a4,0x6a54,0xe832}).toString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4cf9,0x10fe,0xabcc,0xe6f3,0x950e,0x16a1,0x00be}, new char[]{0xab9b,0x16c4,0x4c98,0x00ca,0xe69b,0x956b,0x108c}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x655b,0x6e67,0x5f6f,0xdc79,0x30ba}, new char[]{0x6e24,0xdc15,0x5f0a,0x30db,0x6529}).plainString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7484,0x96da,0x0160,0x460d,0x900a,0x9189}, new char[]{0x9059,0x0105,0x467f,0x96ac,0x74e1,0x91fb}).plainString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6983,0xa5d7,0x750c,0xa2e0}, new char[]{0xa2b2,0x69e2,0xa5be,0x7562}).plainString();
        static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x26f5,0x61ba,0x59ff,0x7fa1,0x58c0,0x3e79,0xbe75}, new char[]{0x59ab,0x7fc9,0x58b5,0x3e17,0xbe11,0x2690,0x61c8}).plainString();
    }
}
