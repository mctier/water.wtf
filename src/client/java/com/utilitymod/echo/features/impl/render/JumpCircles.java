package com.utilitymod.echo.features.impl.render;

import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventRender3D;
import com.utilitymod.echo.event.impl.EventTick;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.settings.impl.BoolSetting;
import com.utilitymod.echo.features.settings.impl.ColorSetting;
import com.utilitymod.echo.features.settings.impl.FloatSetting;
import com.utilitymod.echo.render3.api.FramebufferTarget;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/** Expanding, fading rings where you jump (and land). */
public class JumpCircles extends Feature {
    private final com.utilitymod.echo.features.settings.impl.ModeSetting style = new com.utilitymod.echo.features.settings.impl.ModeSetting(
            LT.L1, LT.L2, LT.L3, LT.L2, LT.L4, LT.L5, LT.L6, LT.L7);
    private final FloatSetting size = new FloatSetting(LT.L8, 1.2f, 0.4f, 3f, 0.1f);
    private final FloatSetting duration = new FloatSetting(LT.L9, 0.9f, 0.2f, 3f, 0.1f);
    private final BoolSetting onLand = new BoolSetting(LT.L10, true);
    private final BoolSetting rainbow = new BoolSetting(LT.L11, false);
    private final ColorSetting color = new ColorSetting(LT.L12, 0xFFB8DDB0, o -> !rainbow.getValue());

    private record Circle(Vec3d pos, long start) {}

    private final List<Circle> circles = new ArrayList<>();
    private boolean wasOnGround = true;

    public JumpCircles() {
        super(new FeatureInfo(new com.utilitymod.echo.utils.EncryptedString(new char[]{0xeb92,0x0b35,0x1c91,0x962f,0x2275,0xd130,0x11f5,0x82df,0xa8be,0x379e,0xb14c,0xbc19}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1}).toString(), new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3ff9,0xbc0e,0xd128,0xb9c2,0x04bf,0xb12f,0x0b2b,0x8ae1,0xd929,0x2a9e,0xfce8,0x9e42,0x4a00,0xebc1,0x9631,0x654f,0x2276,0xa422,0xc5ce,0x335f,0x1c97,0x508e,0x37cd,0x5f65,0xf005,0x11f5,0x82c5,0xa8a2,0x76a1}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87,0x76d4,0xf025,0x4a6a,0xc5bb,0x5f08,0xd959,0x50ae,0x2aff,0xa44c,0x3f9d,0xb9e2,0x3333,0x8a80,0x04d1,0x9e26}).toString(), Category.RENDER));
    }

    @EventSubscribe
    private void onTick(EventTick event) {
        if (isNull()) return;
        boolean ground = mc.player.isOnGround();
        if (wasOnGround && !ground && mc.player.getVelocity().y > 0.1) {
            circles.add(new Circle(mc.player.getEntityPos().add(0, 0.02, 0), System.currentTimeMillis()));
        } else if (!wasOnGround && ground && onLand.getValue()) {
            circles.add(new Circle(mc.player.getEntityPos().add(0, 0.02, 0), System.currentTimeMillis()));
        }
        wasOnGround = ground;
    }

    private String texture() {
        return switch (style.getValue().toString()) {
            case "Bold Circle" -> LT.L13;
            case "Hexagon" -> LT.L14;
            case "Portal" -> LT.L15;
            case "Sci-Fi" -> LT.L16;
            default -> LT.L17;
        };
    }

    /** Texture styles: the SoupAPI ring art lying on the ground, expanding, turning and fading. */
    @EventSubscribe
    private void onRender2D(com.utilitymod.echo.event.impl.EventRender2DGui event) {
        if (style.is(LT.L3) || circles.isEmpty()) return;
        var frame = com.utilitymod.echo.EchoEvents.lastFrame();
        boolean glow = style.is(LT.L7);
        var tex = com.utilitymod.echo.render.SoupAssets.texture(texture(), glow);
        long now = System.currentTimeMillis();
        float life = duration.getValue() * 1000f;
        for (Circle c : circles) {
            float t = Math.min(1f, (now - c.start()) / life);
            double r = size.getValue() * (0.25 + 0.75 * (1 - Math.pow(1 - t, 3)));
            double ang = t * 1.2;
            net.minecraft.util.math.Vec3d u = new net.minecraft.util.math.Vec3d(Math.cos(ang) * r, 0, Math.sin(ang) * r);
            net.minecraft.util.math.Vec3d v = new net.minecraft.util.math.Vec3d(-Math.sin(ang) * r, 0, Math.cos(ang) * r);
            int rgb = rainbow.getValue() ? java.awt.Color.HSBtoRGB(((now / 10) % 360) / 360f, 0.6f, 1f) & 0xFFFFFF
                    : color.getARGB() & 0xFFFFFF;
            int a = Math.round(255 * (float) Math.pow(1 - t, 1.3));
            com.utilitymod.echo.render.SoupAssets.drawWorldQuad(event.getContext(), frame, tex, c.pos(), u, v, (a << 24) | rgb);
        }
    }

    @EventSubscribe
    private void onRender(EventRender3D event) {
        if (!style.is(LT.L3)) return;
        FramebufferTarget draw = event.getDraw3D().getMinecraftTarget();
        long now = System.currentTimeMillis();
        float life = duration.getValue() * 1000f;
        circles.removeIf(c -> now - c.start() > life);
        for (Circle c : circles) {
            float t = (now - c.start()) / life;
            double r = size.getValue() * (0.2 + 0.8 * (1 - Math.pow(1 - t, 3)));
            int a = Math.round(255 * (1 - t));
            int rgb = rainbow.getValue() ? java.awt.Color.HSBtoRGB(((now / 10) % 360) / 360f, 0.6f, 1f) & 0xFFFFFF
                    : color.getARGB() & 0xFFFFFF;
            int segs = 32;
            for (int i = 0; i < segs; i++) {
                double a0 = i * 2 * Math.PI / segs, a1 = (i + 1) * 2 * Math.PI / segs;
                draw.line(c.pos().x + Math.cos(a0) * r, c.pos().y, c.pos().z + Math.sin(a0) * r,
                        c.pos().x + Math.cos(a1) * r, c.pos().y, c.pos().z + Math.sin(a1) * r, (a << 24) | rgb);
            }
        }
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1256,0xb8ba,0x2665,0xc080,0xf065}, new char[]{0xc0d3,0xb8ce,0xf01c,0x123a,0x2600}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb6a6,0x7197,0x00fe,0xa1f8,0x7eeb,0x27c1}, new char[]{0x7ea8,0xa191,0x008c,0x27a2,0x71fb,0xb6c3}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x16f8,0x76fb,0x2c72,0x4f6a,0x9565}, new char[]{0x2c3e,0x950c,0x1696,0x4f0f,0x7688}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5e81,0xa87d,0x0c6a,0xa725,0xfe4d,0x8e7b,0x8dae,0xa971,0xcb8b,0xcd92,0x2c2b}, new char[]{0x0c28,0xcdfd,0x8dc2,0xcbef,0x2c0b,0xa766,0x5ee8,0xa80f,0x8e18,0xfe21,0xa914}).plainString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb4ca,0x89fa,0x11d5,0xc4b2,0xff4a,0xabaf,0xe428}, new char[]{0xabe7,0xb4af,0x11ad,0xff2b,0xe44f,0xc4dd,0x8994}).plainString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1984,0xf91a,0xf9d9,0x95ec,0xdf6b,0xc8cc}, new char[]{0xc89c,0xdf04,0xf9ab,0x19f0,0xf97b,0x9580}).plainString();
        static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6c9a,0x14c4,0x69ba,0x27e3,0x3cc2,0xbb0e}, new char[]{0x27b0,0x3ca1,0xbb67,0x6cb7,0x69fc,0x14ad}).plainString();
        static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x80ac,0xdc3c,0xb176,0xa5d8}, new char[]{0xa58b,0x80c5,0xb10c,0xdc59}).plainString();
        static final String L9 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xcf92,0xd60a,0xf0ee,0x5e95,0x9845,0xc561,0x0b6b,0xe5cb}, new char[]{0xd64e,0xc514,0x0b19,0x5ef4,0xf09a,0x982c,0xe5a4,0xcffc}).plainString();
        static final String L10 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf992,0x16bf,0x4ea6,0x2d82,0xd7dd,0xd0f2,0xeba4}, new char[]{0xd0bd,0xd7b3,0xeb84,0x4eea,0x16de,0xf9fc,0x2de6}).toString();
        static final String L11 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1345,0x71d2,0xa0fd,0x08ed,0x53bc,0xab9a,0xbf81}, new char[]{0x53ee,0xabfb,0x0884,0x71bc,0xa09f,0x132a,0xbff6}).plainString();
        static final String L12 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xfdc6,0x0a1f,0x10fa,0x5e79,0x0d3a}, new char[]{0x0a5c,0x1095,0x5e15,0xfda9,0x0d48}).plainString();
        static final String L13 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xae1f,0x55fb,0xdce3,0x2be7,0xcd62,0x1d9b,0x98ac,0xfad0,0x26ea,0x686f,0xc2d9,0xadbe,0xa64f,0xc9bc,0xbff2,0x6f39,0x0c95,0x2c37,0x411c,0xef01,0x83dd,0x65d3,0xd363,0x01ea,0x4737,0x17da,0x1b70,0xa38d,0x10e8,0xa9e2,0x8b05,0xcb05}, new char[]{0xcd08,0xdc96,0xfabd,0x1b00,0x0cca,0xaddd,0xcb6c,0x681d,0x8b66,0x2686,0x108d,0x1de8,0xc2f6,0x0189,0x4175,0x65a1,0xae7c,0x5597,0xbf97,0xd33c,0xa3ef,0x2b88,0x6f55,0x98c8,0x2c68,0x83a8,0x4759,0xc9de,0xef6d,0xa983,0x17b9,0xa624}).plainString();
        static final String L14 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x43f3,0xa1fc,0x38d8,0xc9fd,0xf137,0x2203,0x9ca1,0xf144,0xd557,0xca01,0xc7fa,0xd5c2,0xcf20,0x02fc,0xbbfd,0xcd15,0x2d51,0xa783,0x162a,0xac5d,0x9020,0x83cf,0xace7,0x7961,0x5a2c,0x5d75,0x91df,0xc122}, new char[]{0xcd7f,0x7914,0xd5af,0x9050,0x3887,0xc99e,0x1643,0xcf52,0x91bc,0xf128,0xac82,0x4380,0x83e0,0x226b,0xac38,0x2d29,0x029d,0x5d12,0x9cce,0xa192,0xa7dc,0xbb88,0xc14c,0xc798,0x5a40,0xf156,0xca62,0xd53c}).plainString();
        static final String L15 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8977,0x9ddc,0x47ae,0x90f3,0x1246,0x3040,0x381b,0x6706,0xd7a0,0xa405,0x6c89,0x7976,0x1ae5,0x69c5,0x3620,0x5903,0x8c01,0xed11,0x85f2,0x3b69,0x918f,0x5a89,0xcfdf,0x99f0,0x0122,0x72a6,0x7928}, new char[]{0x676c,0x8902,0x364d,0x9980,0x301f,0x6cea,0x859b,0xd7d2,0xed72,0x909f,0x3b0c,0xcfac,0x3834,0x7906,0x69aa,0x795a,0x0156,0x8c60,0x596f,0x47f1,0xa470,0x5ae7,0x9dbe,0x1a89,0x1227,0x91ec,0x72cd}).plainString();
        static final String L16 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3356,0x40b6,0xf2e5,0x1a79,0xbce7,0x1acc,0x9b03,0xb2cd,0xe1e6,0x6962,0x73ff,0xa1cf,0xd51e,0x7116,0x9319,0x57e3,0x7b71,0xdaf6}, new char[]{0x9373,0x6917,0xa1a2,0xb2bd,0x57bc,0x7175,0x333f,0xe194,0x1aaf,0x7393,0xf280,0xd56d,0xdad9,0x40c5,0x7b12,0xbc8e,0x1a1f,0x9b6a}).plainString();
        static final String L17 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x12e0,0xab92,0xc8d8,0x0564,0x0a39,0x92f7,0xec4d,0xcf2a,0x912d,0xca42,0x8fdd,0x816d,0x7c49,0x88e1,0x7b6a,0x27c8,0xeb89,0x3445,0x2b61,0x3aed,0xbbab,0x68ff,0xae76,0xaf1d,0x5c08,0xb9f0,0xeb2e}, new char[]{0xb99a,0x7c3c,0x128d,0x0514,0x7b35,0xec2e,0x8fb4,0x3437,0xbbc8,0x27a4,0xaf78,0x2b12,0xeb01,0x3a8e,0x9144,0xabe0,0xcf49,0x6893,0xca27,0xae29,0x8118,0x0a57,0xebeb,0x888d,0x5c69,0x9294,0xc8b3}).plainString();
    }
}
