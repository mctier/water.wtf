package com.utilitymod.echo.features.impl.render;

import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventOnAttackEntity;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.settings.impl.FloatSetting;
import com.utilitymod.echo.features.settings.impl.ModeSetting;
import com.utilitymod.echo.render.SoupAssets;

/** Plays a SoupAPI sound on every hit you land (pok, bonk, bubble, magic pok, bell, get). */
public class HitSounds extends Feature {
    private final ModeSetting sound = new ModeSetting(LT.L1, LT.L2, LT.L2, LT.L3, LT.L4, LT.L5, LT.L6, LT.L7);
    private final FloatSetting volume = new FloatSetting(LT.L8, 0.7f, 0.05f, 1f, 0.05f);

    public HitSounds() {
        super(new FeatureInfo(new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa8a2,0x9630,0x82c3,0x37ed,0x0b29,0x1c96,0x226c,0xb160,0xd132,0xbc49}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643}).toString(), new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3fe8,0xbc06,0xd13b,0xb9c2,0x04bf,0xb161,0x0b38,0x8ae1,0xd92d,0x2a86,0xfce2,0x9e42,0x4a4a,0xeb84,0x962d,0x6540,0x226b,0xa423,0xc5d3,0x335f,0x1c9d,0x508e,0x37cb,0x5f61,0xf05c,0x11b0,0x828d,0xa8eb,0x76a6}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87,0x76d4,0xf025,0x4a6a,0xc5bb,0x5f08,0xd959,0x50ae,0x2aff,0xa44c,0x3f9d,0xb9e2,0x3333,0x8a80,0x04d1,0x9e26}).toString(), Category.RENDER));
    }

    @Override
    public void onEnable() {
        super.onEnable();
        SoupAssets.preload(LT.L9, LT.L10, LT.L11, LT.L12, LT.L13, LT.L14);
    }

    @EventSubscribe
    private void onAttack(EventOnAttackEntity.Post event) {
        if (event.getPlayer() != mc.player) return;
        SoupAssets.play(sound.getValue().toString().toLowerCase().replace(' ', '_'), volume.getValue());
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa214,0x2781,0x0dcc,0x1189,0x5521}, new char[]{0x11da,0x554e,0x0db9,0xa27a,0x27e5}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5221,0xf204,0x4de3}, new char[]{0x5271,0x4d8c,0xf26f,0x143e}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6a38,0xa668,0x40de,0xfb74,0x7b15,0xb8c9,0x6f7b,0xa948,0xa026}, new char[]{0xa625,0x40bf,0xa92f,0xa04f,0xb8aa,0xfb54,0x6f2b,0x7b7a,0x6a53}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x60b5,0x9840,0x049e,0xd47d}, new char[]{0x04dc,0x982f,0x60db,0xd416}).plainString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6efa,0xba72,0x491f,0xe002,0xad67,0x5b93}, new char[]{0x6eb8,0x5be6,0x497d,0xe060,0xad0b,0xba17}).plainString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9318,0x51f6,0x9177,0x1d74}, new char[]{0x1d36,0x937d,0x911b,0x519a}).plainString();
        static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0770,0xd81b,0x18a8}, new char[]{0x18ef,0x0715,0xd86f,0xac07}).plainString();
        static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x932f,0xe76a,0x47b2,0xcb1a,0x522b,0xb40a}, new char[]{0xe73c,0x5244,0x47de,0xb47f,0x9342,0xcb7f}).toString();
        static final String L9 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2ec3,0x2da0,0x3b30}, new char[]{0x3b40,0x2dcf,0x2ea8,0x9b14}).plainString();
        static final String L10 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x69fe,0xe213,0xc39a,0x1c46,0x97d9,0x9f79,0x0e68,0xc362,0xe17a}, new char[]{0x97b4,0x1c27,0xc305,0x0e01,0x699d,0xc3c5,0xe263,0xe115,0x9f12}).plainString();
        static final String L11 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1248,0x224f,0x9319,0xef65}, new char[]{0xef07,0x2220,0x9377,0x1223}).plainString();
        static final String L12 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd461,0x6f18,0x533f,0xdd0d,0x616b,0x310b}, new char[]{0x535d,0x317e,0xd403,0xdd6f,0x6f74,0x610e}).plainString();
        static final String L13 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xeccd,0x5d8d,0x1eb3,0x5b30}, new char[]{0x5b52,0x5de8,0xeca1,0x1edf}).plainString();
        static final String L14 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x93ff,0x8a7a,0xa89f}, new char[]{0x8a1d,0x939a,0xa8eb,0x38eb}).plainString();
    }
}
