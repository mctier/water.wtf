package com.utilitymod.echo.features.impl.render;

import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.settings.impl.BoolSetting;
import com.utilitymod.echo.features.settings.impl.IntSetting;
import com.utilitymod.echo.features.settings.impl.ModeSetting;
import com.utilitymod.echo.render.SoupAssets;
import com.utilitymod.echo.utils.player.PlayerUtils;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.AssetInfo;

import java.util.List;

/**
 * Wear any of the 116 SoupAPI capes (client-side). Also hosts the skin override hook shared with
 * {@link AnonymousSkin}: AbstractClientPlayerEntity.getSkin() RETURN -> {@link #override}.
 * Your own cape shows only when "Cape" is on in Skin Customization.
 */
public class CustomCape extends Feature {
    private static final String[] CATEGORIES = {LT.L1, LT.L2, LT.L3, LT.L4, LT.L5, LT.L6};

    private final ModeSetting category = new ModeSetting(LT.L7, LT.L1, CATEGORIES);
    private final IntSetting index = new IntSetting(LT.L8, 1, 1, 47);
    private final BoolSetting friends = new BoolSetting(LT.L9, false);

    private static CustomCape instance;

    public CustomCape() {
        super(new FeatureInfo(new com.utilitymod.echo.utils.EncryptedString(new char[]{0x11f5,0xa8be,0xbc1d,0x226b,0x1c93,0x82ee,0x0b7c,0xd139,0xb162,0x9633,0x37d1}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190}).toString(), LT.L10, Category.RENDER));
        instance = this;
        com.utilitymod.inject.Hooks.skinOverride = CustomCape::override;
    }

    private String capePath() {
        List<String> list = SoupAssets.capes(category.getValue().toString().toLowerCase());
        if (list.isEmpty()) return null;
        int i = Math.max(1, Math.min(list.size(), index.getValue())) - 1;
        return list.get(i);
    }

    @Override
    public CharSequence concat() {
        String p = capePath();
        return p == null ? "" : p.substring(p.indexOf('/') + 1).replace('_', ' ');
    }

    /** Skin hook: swap cape (this module) and body (Anonymous Skin) on the vanilla textures. */
    public static Object override(Object vanilla, Object self) {
        if (!(vanilla instanceof SkinTextures skin) || !(self instanceof AbstractClientPlayerEntity player)) return vanilla;
        var mc = net.minecraft.client.MinecraftClient.getInstance();
        boolean me = player == mc.player;
        AssetInfo.TextureAsset body = skin.body(), cape = skin.cape(), elytra = skin.elytra();
        var model = skin.model();
        boolean changed = false;

        CustomCape cc = instance;
        if (cc != null && cc.isEnabled() && (me || (cc.friends.getValue() && PlayerUtils.isFriend(player.getName().getString())))) {
            String path = cc.capePath();
            SoupAssets.Tex t = path == null ? null : SoupAssets.texture(LT.L11 + path, false);
            if (t != null) {
                cape = new AssetInfo.TextureAssetInfo(t.id(), t.id());
                elytra = cape;
                changed = true;
            }
        }
        AnonymousSkin anon = AnonymousSkin.instance;
        if (anon != null && anon.isEnabled() && anon.applies(me)) {
            SoupAssets.Tex t = SoupAssets.texture(LT.L12, false);
            if (t != null) {
                body = new AssetInfo.TextureAssetInfo(t.id(), t.id());
                model = net.minecraft.entity.player.PlayerSkinType.WIDE;
                changed = true;
            }
        }
        return changed ? new SkinTextures(body, cape, elytra, model, skin.secure()) : vanilla;
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x90e5,0x44c2,0xd13a,0xd707,0xa9a5,0xac1f,0x6583}, new char[]{0x4486,0xa9c0,0xac79,0x9084,0xd772,0xd156,0x65f7}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe3c5,0xb0be,0x7503,0x410c,0xbf9e,0x735b}, new char[]{0xbfdd,0xb0cb,0x7570,0x732f,0xe3aa,0x4161}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5f35,0x876c,0xe800}, new char[]{0x5f78,0x872f,0xe844,0xd46f}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xec8c,0x5eb2,0x88a1,0x7759,0xd1ff}, new char[]{0xeccd,0x5ec2,0xd18d,0x7730,0x88cd}).toString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd5b2,0xab78,0x749b,0xe1b9,0x00ca,0xe36f,0xda77,0x174e,0x1b19}, new char[]{0x1b54,0x74f2,0x00a9,0xd5c0,0xab17,0xe31c,0xda18,0x1728,0xe1cd}).toString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x09b8,0x0878,0x16aa,0x06b3}, new char[]{0x16fe,0x06d6,0x0819,0x09d5}).toString();
        static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5337,0xb4d7,0xfa72,0xb469,0x23c2,0xc59f,0x02e4,0x0cbf,0x3ba4,0xc9cc}, new char[]{0x0cfc,0xc9a3,0x535b,0xb405,0x0281,0xc5fc,0xfa06,0x23ab,0xb4b8,0x3bca}).plainString();
        static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4dac,0xb1ed,0x1a5f,0x1f98,0x8437,0x8c37}, new char[]{0x8c74,0xb18c,0x8447,0x1a3a,0x1fb8,0x4d8f}).toString();
        static final String L9 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x37f6,0xaecd,0xde51,0x01f4,0x7dd6,0xb35f,0xd12c,0x7ccb,0xc8dc,0x7463,0xc15d}, new char[]{0x7d90,0x7cb9,0xd145,0xb33a,0x740d,0xc8b8,0xc12e,0x01d4,0x37a2,0xaea2,0xde3e}).toString();
        static final String L10 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x87d0,0x6859,0xc3e8,0x0108,0xfb70,0x098b,0xa699,0xa5f0,0xb9a8,0xd816,0x66b8,0xbafd,0x9c29,0x06ac,0x3e81,0x87b8,0x5d82,0x7f16,0x195c,0x0521,0x3d7b,0xbb4b,0x5527,0x0737,0x6141,0xab6c,0x3396,0x8476,0xbaaf,0x45ca,0x55f1,0xa279,0xccc5,0x2bb4,0x9109,0x487f,0x5b88,0xf1f2,0xcfb5}, new char[]{0xb9ff,0x45af,0xd877,0xfb02,0xab4c,0x0169,0x5dec,0x555e,0x7f36,0xba92,0xcfd3,0x8456,0x069d,0x3d4a,0x6177,0x09ab,0xf191,0x0756,0x2bc4,0x9c4c,0xbadc,0xa5d0,0xcced,0x87bf,0x66d6,0x054d,0xbb32,0x3ea1,0x6820,0xa216,0x5584,0xa6b9,0x917a,0x1939,0x87dd,0x33b6,0x5be1,0x480b,0xc3c1}).toString();
        static final String L11 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa9a3,0x61b0,0x99b0,0xa591,0x0cdb,0x5d84}, new char[]{0x0cb8,0x5de5,0xa9d3,0x99d5,0xa5e2,0x619f}).plainString();
        static final String L12 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc5ec,0x718b,0xa506,0x9f3e,0x3ba2,0x9811,0x68a1,0x3837,0xe750,0x55b0,0x6342,0x37d1,0xf42a,0x3302}, new char[]{0xa575,0xe73b,0x3bcb,0x987f,0xc5c3,0x55d1,0x9f50,0x37be,0x3859,0x631d,0x68d2,0x71e0,0x336b,0xf444}).plainString();
    }
}
