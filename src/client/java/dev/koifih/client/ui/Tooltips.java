package dev.koifih.client.ui;

import dev.koifih.client.util.Lang;
public enum Tooltips {
    DESCRIPTION(LT.L1),
    OFF(LT.L2);

    public static final Tooltips[] ALL = values();
    private static Tooltips current = DESCRIPTION;
    private final String key;

    public String displayName() {
        if (this == OFF) return LT.L3;
        return Lang.get(key);
    }

    public static Tooltips current() {
        return current;
    }

    public static void set(Tooltips mode) {
        current = mode;
    }

    Tooltips(String key) {
        this.key = key;
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1875,0xe75e,0x779e,0x5738,0xbf3e,0xa0e3,0x2630,0xf665,0x183f,0x25fa,0x577f,0x3d23,0xc533,0x5048,0x60c8,0xd783,0xdc0d,0x610b,0x8291,0x2afc}, new char[]{0x60bc,0x2595,0x1850,0x2a90,0xe72a,0xd7ea,0xbf4e,0x82e2,0xdc23,0x1811,0x575d,0xc540,0x2653,0xa091,0x6162,0x77ee,0x570b,0x3d4a,0x5027,0xf60b}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4a6c,0x68c2,0x3e73,0xcc58,0x27a9,0xcd21,0x0dde,0xa87b,0x3d0a,0x14e3,0xbac2,0x485a}, new char[]{0xbab6,0x3d65,0x27c6,0xa817,0x482e,0x148a,0x4a1c,0xcc2b,0xcd0f,0x0db1,0x3e15,0x68a4}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf92a,0xf1de,0x9266}, new char[]{0xf965,0x9200,0xf1b8,0xb736}).toString();
    }
}
