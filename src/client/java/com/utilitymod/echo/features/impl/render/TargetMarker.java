package com.utilitymod.echo.features.impl.render;

import com.utilitymod.echo.EchoEvents;
import com.utilitymod.echo.Render3D;
import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventOnAttackEntity;
import com.utilitymod.echo.event.impl.EventRender2DGui;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.settings.impl.BoolSetting;
import com.utilitymod.echo.features.settings.impl.FloatSetting;
import com.utilitymod.echo.features.settings.impl.ModeSetting;
import com.utilitymod.echo.render.SoupAssets;
import dev.koifih.client.ui.Theme;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector4f;

/**
 * Marks your target with one of the SoupAPI target-render textures: a spinning, pulsing sprite on
 * their body, or lying flat under their feet. Fades in/out and glides between targets.
 */
public class TargetMarker extends Feature {
    private final ModeSetting style = new ModeSetting(LT.L1, LT.L2,
            LT.L2, LT.L3, LT.L4, LT.L5, LT.L6, LT.L7, LT.L8, LT.L9, LT.L10);
    private final ModeSetting placement = new ModeSetting(LT.L11, LT.L12, LT.L12, LT.L13);
    private final FloatSetting size = new FloatSetting(LT.L14, 1f, 0.4f, 2.5f, 0.05f);
    private final FloatSetting spinSpeed = new FloatSetting(LT.L15, 1f, 0f, 4f, 0.1f);
    private final BoolSetting accentTint = new BoolSetting(LT.L16, true);
    private final BoolSetting onlyAfterHit = new BoolSetting(LT.L17, false);

    private LivingEntity target, lastHit;
    private long lastHitAt;
    private float shown;
    private Vec3d pos;

    public TargetMarker() {
        super(new FeatureInfo(new com.utilitymod.echo.utils.EncryptedString(new char[]{0x226a,0x9631,0x11fb,0xeb84,0x82e0,0x0b7c,0xa8aa,0xbc0e,0x1c93,0xd12e,0x37db,0x6544,0xb17b}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536}).toString(), new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3b36,0xf881,0x93e4,0xc5de,0x4a1e,0x3fbd,0x11f1,0x76a6,0x962e,0x5f70,0xd92d,0x82ca,0x335d,0x2602,0xa429,0xd2a7,0x1581,0x50db,0x1812,0x2271,0xb98d,0x9e4e,0xd129,0xcdec,0xf005,0x8aa0,0x47cf,0xbc07,0x37d0,0xef57,0xb166,0x9bef,0x655d,0xc145,0xa8bb,0xac04,0x1cd2,0x0cdd,0x8662,0x7eca,0xeb93,0x0022,0xe736,0x04a5,0x66e9,0x722c,0x6d65,0xb501,0x2a8d,0x58e2,0xa1a4,0x0b32,0xfce2}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87,0x76d4,0xf025,0x4a6a,0xc5bb,0x5f08,0xd959,0x50ae,0x2aff,0xa44c,0x3f9d,0xb9e2,0x3333,0x8a80,0x04d1,0x9e26,0x1877,0x93c4,0x6d15,0xe75a,0x7eab,0xf8f8,0x7249,0xcd9e,0x47ef,0xc13c,0x588d,0xd2d2,0xac23,0x2670,0xa1c1,0x3b16,0xb567,0x0cb4,0x8605,0x004a,0x9b9b,0x15e8,0xef39,0x668e}).toString(), Category.RENDER));
    }

    @EventSubscribe
    private void onAttack(EventOnAttackEntity.Post event) {
        if (event.getTarget() instanceof LivingEntity l) {
            lastHit = l;
            lastHitAt = System.currentTimeMillis();
        }
    }

    private LivingEntity pick() {
        LivingEntity t = pickRaw();
        return com.utilitymod.echo.utils.combat.TargetUtils.isFriend(t) ? null : t;
    }

    private LivingEntity pickRaw() {
        if (!onlyAfterHit.getValue() && mc.crosshairTarget instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity l
                && l.isAlive() && mc.player.squaredDistanceTo(l) < 36) return l;
        if (lastHit != null && lastHit.isAlive() && !lastHit.isRemoved() && System.currentTimeMillis() - lastHitAt < 3000) return lastHit;
        return null;
    }

    private String texturePath() {
        return LT.L18 + switch (style.getValue().toString()) {
            case "Sci-Fi" -> LT.L19;
            default -> style.getValue().toString().toLowerCase();
        };
    }

    @EventSubscribe
    private void onRender(EventRender2DGui event) {
        Render3D frame = EchoEvents.lastFrame();
        if (isNull() || frame == null || mc.options.hudHidden) return;
        LivingEntity t = pick();
        if (t != null) target = t;
        shown += ((t != null ? 1f : 0f) - shown) * 0.15f;
        if (shown < 0.01f || target == null) {
            if (shown < 0.01f) target = null;
            return;
        }
        Vec3d at = target.getLerpedPos(frame.tickDelta);
        boolean feet = placement.is(LT.L13);
        Vec3d goal = feet ? at.add(0, 0.03, 0) : at.add(0, target.getHeight() * 0.55, 0);
        pos = pos == null || pos.squaredDistanceTo(goal) > 25 ? goal : pos.lerp(goal, 0.35);

        SoupAssets.Tex tex = SoupAssets.texture(texturePath(), true);
        if (tex == null) return;
        long now = System.currentTimeMillis();
        float spin = (now / 1000f) * 90f * spinSpeed.getValue();
        float pulse = 1f + 0.06f * (float) Math.sin(now / 180.0);
        int rgb = accentTint.getValue() ? (Theme.accentRgb() & 0xFFFFFF) : 0xFFFFFF;
        int argb = (Math.round(255 * shown) << 24) | rgb;

        if (feet) {
            double r = target.getWidth() * 1.1 * size.getValue() * pulse;
            double a = Math.toRadians(spin);
            Vec3d u = new Vec3d(Math.cos(a) * r, 0, Math.sin(a) * r), v = new Vec3d(-Math.sin(a) * r, 0, Math.cos(a) * r);
            SoupAssets.drawWorldQuad(event.getContext(), frame, tex, pos, u, v, argb);
        } else {
            Vector4f clip = frame.toClip(pos.x, pos.y, pos.z);
            if (clip.w() <= 0.05f) return;
            float[] g = Render3D.toGui(clip);
            int sh = event.getContext().getScaledWindowHeight();
            float px = target.getHeight() * 0.9f * size.getValue() * (sh / 2f) * Math.abs(frame.projection.m11()) / clip.w();
            SoupAssets.drawBillboard(event.getContext(), tex, g[0], g[1], px * pulse * (0.85f + 0.15f * shown), spin, argb);
        }
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7ee2,0x246c,0xef58,0xa4e0,0xe718}, new char[]{0x7eb1,0xef2c,0x2415,0xa48c,0xe77d}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa82c,0xdc97,0xccaf,0x76c1,0x6f98,0x35ea}, new char[]{0xcce2,0x6ff9,0xa85e,0x76aa,0xdcf2,0x3598}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x09e4,0x2d8c,0x21d2,0x7289,0x8151,0xbb78}, new char[]{0x09b7,0x8138,0x2de1,0x72f9,0x21be,0xbb1d}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x311b,0xef1d,0x5510,0x8671,0x696d,0x5e86}, new char[]{0xef51,0x317e,0x5577,0x8610,0x690e,0x5eff}).plainString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x76cd,0xeacf,0xcb26,0xf0a7,0x66f5,0x1635}, new char[]{0x769e,0xcb45,0x669c,0x1618,0xf0e1,0xeaa6}).plainString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb328,0x11a2,0x1476,0xd2eb,0x7c3c}, new char[]{0x7c6a,0x11c7,0x1411,0xb349,0xd298}).plainString();
        static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8de2,0xf85b,0xe00c,0xd41c,0x19e9}, new char[]{0x8db1,0xf830,0x199c,0xe060,0xd470}).plainString();
        static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x435e,0xae02}, new char[]{0xae40,0x4331,0x74e4,0x6b19}).plainString();
        static final String L9 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8b31,0x4230,0xcf38,0x2310}, new char[]{0x427a,0xcf5d,0x237b,0x8b50}).plainString();
        static final String L10 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc959,0x9d86,0x1f4a,0xcaa8,0x50f4,0x08e8,0xfe5a}, new char[]{0xfe1b,0x0885,0xc936,0x1f24,0x9de1,0x5081,0xcadb}).plainString();
        static final String L11 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb70e,0x4f47,0x3132,0x66d7,0x08f5,0x48ed,0xa88a,0x7354,0x8817}, new char[]{0x8847,0xb762,0x0894,0xa8e9,0x66b2,0x315f,0x7331,0x4f29,0x4899}).plainString();
        static final String L12 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1c3b,0x30f9,0x15cd,0xba32}, new char[]{0xba70,0x3096,0x1c5f,0x15b4}).plainString();
        static final String L13 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1346,0xfde8,0xb535,0x6bab,0x3846,0x0a29,0x37f0,0x2d20,0x392d,0xa04d}, new char[]{0x37a5,0xfd86,0xb551,0xa028,0x6bd9,0x390d,0x0a6f,0x2d45,0x1323,0x3832}).plainString();
        static final String L14 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x68dc,0x1df8,0x0073,0x87fa}, new char[]{0x688f,0x001a,0x8780,0x1d9d}).plainString();
        static final String L15 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xfd1f,0xd8cf,0x470e,0x5a3b,0x62cf,0x46af,0xcf39,0x3789,0x2f7a,0x8e70}, new char[]{0x5a68,0xfd6f,0x8e19,0xcf57,0x62ef,0xd89c,0x46df,0x2f1f,0x37ec,0x476a}).toString();
        static final String L16 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6f24,0x4db7,0x591d,0xf2b2,0x1d39,0x25ae,0x0624,0x581a,0x3cfb,0xc29a,0xeaf4}, new char[]{0x0665,0x25cd,0x4dd4,0xea91,0x5973,0x586e,0xc2ba,0x3caf,0x1d50,0x6f4a,0xf2c6}).toString();
        static final String L17 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8366,0x8992,0x1b0e,0xa5fa,0x6f8f,0x0929,0x35da,0x8525,0x5d20,0xe3db,0x8975,0x21e2,0x0936,0xf78a}, new char[]{0x0966,0x0958,0xa596,0x890c,0x8346,0x6fce,0x35bc,0xf7fe,0x8540,0x89e0,0x21c2,0x1b46,0xe3b2,0x5d54}).toString();
        static final String L18 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc373,0xf631,0x6283,0xbcd6,0xb4ac,0xb206,0x8ff4,0xd1b8,0x5016,0x5c8a,0x5227,0xd71a,0x2e51,0xcac4}, new char[]{0xbca2,0xcaa5,0xf643,0x5240,0x2e34,0x5cfe,0xb259,0xd768,0xd1dd,0xc31d,0x5072,0x62e6,0xb4de,0x8fdb}).plainString();
        static final String L19 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xfd74,0x852c,0x4d98,0xb8a6,0x48b8}, new char[]{0x48cb,0x4dfb,0xb8cf,0xfd12,0x8545}).plainString();
    }
}
