package dev.koifih.client.ui;

import dev.koifih.client.util.Lang;
public enum Units {
    METRIC(LT.L1) {
        @Override
        public String distance(int blocks) {
            return blocks + LT.L2;
        }
    },
    IMPERIAL(LT.L3) {
        @Override
        public String distance(int blocks) {
            return Math.round(blocks * FEET_PER_BLOCK) + LT.L4;
        }
    };

    public static final Units[] ALL = values();
    private static final float FEET_PER_BLOCK = 3.28084f;
    private static Units current = METRIC;
    private final String key;

    public abstract String distance(int blocks);

    public String displayName() {
        return Lang.get(key);
    }

    public static Units current() {
        return current;
    }

    public static void set(Units units) {
        current = units;
    }

    Units(String key) {
        this.key = key;
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6f48,0x6a6a,0x185e,0x0139,0x2d1d,0x22ea,0x5fa0,0x199a,0x18b3,0x1021,0x53f4,0x3a82}, new char[]{0x6a1f,0x104f,0x6f21,0x182a,0x18c0,0x53da,0x2d70,0x015c,0x229e,0x19e8,0x3aeb,0x5fc3}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7528}, new char[]{0x7545,0x289d,0x9a4d,0x040b}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9a6f,0x8c6d,0xafc9,0xd5c0,0x8e56,0xa38e,0x2684,0xee83,0x65ad,0xf65d,0xd273,0x7b71,0x798e,0x481b}, new char[]{0xafbc,0xf633,0x8c04,0xeef7,0x7b02,0x9a41,0x26ed,0x79e3,0x486b,0xa3eb,0xd5b2,0xd21a,0x8e37,0x65c1}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x36cc,0x6198}, new char[]{0x61fe,0x36b8,0xcc80,0xe37f}).plainString();
    }
}
