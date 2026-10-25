package com.utilitymod.echo.features.settings.impl;

import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.utils.strings.Concat;
import lombok.Getter;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

@Getter
public final class InputSimulationSetting extends BoolSetting {
    private static final Set<InputSimulationSetting> INSTANCES = ConcurrentHashMap.newKeySet();

    private final KeybindSetting[] inputSlots = {
            new KeybindSetting(Concat.of(LT.L1), -1),
            new KeybindSetting(Concat.of(LT.L2), -1),
            new KeybindSetting(Concat.of(LT.L3), -1),
            new KeybindSetting(Concat.of(LT.L4), -1),
            new KeybindSetting(Concat.of(LT.L5), -1)
    };
    private final IntSetting inputDelay = new IntSetting(Concat.of(LT.L6), 100, 50, 10_000, LT.L7);
    private Feature owner;
    private long nextInputNanos;

    public InputSimulationSetting(CharSequence name, boolean defaultValue) {
        super(name, defaultValue);
        INSTANCES.add(this);
    }

    public InputSimulationSetting(CharSequence name, boolean defaultValue, Predicate<Object> dependency) {
        super(name, defaultValue, dependency);
        INSTANCES.add(this);
    }

    public void setOwner(Feature owner) {
        this.owner = owner;
    }

    public boolean isReady(long now) {
        if (now < nextInputNanos) {
            return false;
        }

        nextInputNanos = now + inputDelay.getValue() * 1_000_000L;
        return true;
    }

    public static Set<InputSimulationSetting> instances() {
        return Collections.unmodifiableSet(INSTANCES);
    }

    @Override
    public String getTypeId() {
        return LT.L8;
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf918,0x35df,0x72aa,0x969a,0x42fe,0x6371,0x6a41}, new char[]{0x6a08,0x96f4,0x428e,0x72df,0x6305,0x35ff,0xf929}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2cd8,0x19eb,0x060e,0x684f,0x8ce0,0x6fe7,0x02be}, new char[]{0x19a2,0x02d0,0x683f,0x2cad,0x067a,0x6fc7,0x8cd2}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9270,0xe3ce,0x6a63,0x1ba5,0xaffc,0x9a21,0x643e}, new char[]{0x1bec,0xaf92,0x644e,0x9205,0x6a17,0xe3ee,0x9a12}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa992,0x5725,0xab0f,0x32be,0x1f86,0xbfba,0xafdd}, new char[]{0x32f7,0xafb3,0xa9e2,0x5750,0x1ff2,0xbf9a,0xab3b}).plainString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4f7b,0x4d91,0x0fb9,0x77f6,0x1cff,0x2875,0xda27}, new char[]{0xda6e,0x1c91,0x7786,0x4de4,0x4f0f,0x0f99,0x2840}).plainString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4e2b,0xa839,0xac28,0xc57a,0x3d2b,0xcd06,0x4d4c,0x35df,0x6b13,0xca26,0x5325}, new char[]{0xa870,0x534b,0xc50a,0xac5d,0xcd72,0x35ff,0xca62,0x6b76,0x4e47,0x3d4a,0x4d35}).plainString();
        static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x49a4,0x4dbc}, new char[]{0x49c9,0x4dcf,0xf287,0xeedc}).plainString();
        static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf22a,0xf3c1,0xec37}, new char[]{0xf3a8,0xec59,0xf25a,0x4286}).plainString();
    }
}
