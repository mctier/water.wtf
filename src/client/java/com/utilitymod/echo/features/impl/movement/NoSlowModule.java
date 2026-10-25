package com.utilitymod.echo.features.impl.movement;

import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventSlowdown;
import com.utilitymod.echo.event.impl.EventTick;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.settings.impl.BoolSetting;
import com.utilitymod.echo.features.settings.impl.IntSetting;
import com.utilitymod.echo.features.settings.impl.ModeSetting;
import com.utilitymod.echo.utils.strings.Concat;

import static com.utilitymod.echo.utils.Imports.mc;

public class NoSlowModule extends Feature {
    public NoSlowModule() {
        super(new FeatureInfo(
            Concat.of(new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0b2b,0xd134,0x37d2,0xb160,0xbc3a,0x2238,0xa8a4}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c}).toString()),
            Concat.of(LT.L1),
            Category.MOVEMENT));
    }

    private final ModeSetting mode = new ModeSetting(Concat.of(LT.L2), Concat.of(LT.L3), Concat.of(LT.L3)
            ,Concat.of(LT.L4)
    );
    private final BoolSetting enableSneak = new BoolSetting(Concat.of(LT.L5), false);
    private final IntSetting grimTicks = new IntSetting(Concat.of(LT.L6), 2, 0, 10, obj -> mode.is(LT.L4));

    private int grimTickCount = 0;
    private boolean grimActiveThisTick = false;

    @Override
    public void onDisable() {
        super.onDisable();
        grimTickCount = 0;
        grimActiveThisTick = false;
    }

    @Override
    public void onEnable() {
        super.onEnable();
        grimTickCount = 0;
        grimActiveThisTick = false;
    }

    @EventSubscribe
    public void onTick(EventTick event) {
        if (isNull()) return;
        if (mc.player == null) return;

        grimActiveThisTick = false;

        if (!mc.player.isUsingItem()) {
            grimTickCount = 0;
        } else {
            grimTickCount++;
        }
    }

    @EventSubscribe
    public void onSlowdown(EventSlowdown event) {
        if (isNull()) return;

        if (mode.is(Concat.of(LT.L4))) {
            if (grimActiveThisTick) {
                event.setSpoofPassenger(true);
                return;
            }
            if (grimTickCount >= grimTicks.getValue()) {
                grimActiveThisTick = true;
                grimTickCount = 0;
                event.setSpoofPassenger(true);
            }
            return;
        }

        event.setSlowdownMultiplier(1.0f);
        event.setSlowDueToItem(false);
        if (enableSneak.getValue()) {
            event.setMovingSlowly(false);
        }
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb077,0xa50a,0xdf73,0x573c,0xd92d,0xad3c,0xd31f,0x633f,0x335b,0xb5aa,0x13c5,0xbe3a,0x3f0d,0xe8e3,0xad91,0x4c5e,0x5cab,0x70ff,0xd39e,0x1d28,0xe384,0xcc14,0x6056,0xd28c,0x6c5d,0xd5fe,0x5a89,0xa47d,0x4451,0x818d}, new char[]{0xcc46,0xd3fb,0xad51,0x5ae6,0xd588,0x5cce,0xd36c,0x4471,0x3f7e,0xe3e8,0x6350,0x7088,0x4c7e,0x81e8,0x333d,0xb5cc,0x1d4d,0xdf10,0xa57e,0x571c,0x6c3b,0xbe48,0xd942,0xb01a,0xa45d,0xd2e5,0xe897,0x6033,0xadfc,0x13b6}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x69f1,0x04cb,0xa461,0x9638}, new char[]{0x69bc,0x9657,0xa405,0x04ae}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe53d,0x37c6,0x491b,0xe869,0xc445,0x1e20,0xb1e3}, new char[]{0xc433,0xe808,0x37a8,0x4972,0xb18f,0xe551,0x1e41}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd714,0x41ff,0x965c,0x3d7e}, new char[]{0x41b8,0x962e,0x3d17,0xd779}).plainString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd3d4,0x3013,0x3540,0x58a0,0x2e9a,0x0703,0x7de5,0x0a36,0xd04b,0x3b18,0x29bf,0xd990}, new char[]{0xd9d5,0x58ce,0x3b79,0xd029,0x2ef6,0x3076,0x0a16,0xd387,0x352e,0x29da,0x0762,0x7d8e}).plainString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x70d6,0x238f,0xb7ab,0xb288,0x80d1,0x59b9,0x2e2b,0x91df,0xb2cb,0x51ba,0x833f}, new char[]{0x7092,0x59dc,0x23e3,0x91be,0x2e52,0xb78b,0x51ee,0xb2e1,0xb2a8,0x8354,0x80a2}).plainString();
    }
}
