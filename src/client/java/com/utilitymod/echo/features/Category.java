package com.utilitymod.echo.features;

/** echo features.Category, mapped onto this mod's wing tabs. */
public enum Category {
    COMBAT(LT.L1, com.utilitymod.gui.Category.COMBAT),
    MOVEMENT(LT.L2, com.utilitymod.gui.Category.MOVEMENT),
    RENDER(LT.L3, com.utilitymod.gui.Category.VISUAL),
    FINDER(LT.L4, com.utilitymod.gui.Category.FINDER),
    MACROS(LT.L5, com.utilitymod.gui.Category.MISC),
    UTILITY(LT.L6, com.utilitymod.gui.Category.MISC),
    INTERNALS(LT.L7, com.utilitymod.gui.Category.MISC),
    DONUT(LT.L8, com.utilitymod.gui.Category.DONUT);

    public final CharSequence name;
    public final com.utilitymod.gui.Category tab;

    Category(String name, com.utilitymod.gui.Category tab) {
        this.name = name;
        this.tab = tab;
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8148,0x39c2,0xfb54,0x6988,0x6e98,0x5efb}, new char[]{0x810b,0xfb3b,0x39af,0x69ea,0x6ef9,0x5e8f}).toString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x43bd,0x9270,0x308d,0xe3eb,0x953a,0x5b65,0xfb27,0x1f47}, new char[]{0x43f0,0xe384,0x9206,0xfb42,0x1f2a,0x955f,0x30e3,0x5b11}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8679,0x5529,0x7fff,0xe4e1,0xb751,0xe38a,0xd67d}, new char[]{0x557f,0xe488,0x860a,0x7f8a,0xd61c,0xb73d,0xe3f9}).toString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3b19,0x2537,0xe8f2,0xc778,0x6932,0xeba1,0x01b4,0x4bf0,0xbdd9,0x1f88,0x1a00,0x4d24}, new char[]{0x01f6,0xe893,0x2544,0x4d41,0x1fa8,0xebe7,0x695b,0x4b9e,0xbdbd,0x1a69,0xc716,0x3b7e}).toString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9604,0x3d8a,0x7765,0xab00,0x1884,0x0822}, new char[]{0xab4d,0x18e5,0x3de9,0x0850,0x770a,0x9677}).toString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4064,0x026a,0x5271,0x21a4,0x422c,0x4ae0,0xb657}, new char[]{0x5224,0x21d0,0x0203,0x4240,0xb63e,0x4a94,0x401d}).toString();
        static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x050b,0x520e,0xca4b,0x78d5,0xb0e7,0xae76,0x9e38,0x8c1e,0xd564}, new char[]{0x789c,0x0565,0x527a,0xd501,0xae04,0xca25,0x9e59,0xb08b,0x8c6d}).toString();
        static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc187,0xe18a,0x998b,0x9ada,0x44d1}, new char[]{0x4495,0xc1e8,0x9ab4,0x99fe,0xe1fe}).toString();
    }
}
