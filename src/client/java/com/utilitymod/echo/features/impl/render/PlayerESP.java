package com.utilitymod.echo.features.impl.render;

import com.utilitymod.echo.features.settings.impl.IntSetting;

import com.utilitymod.echo.features.settings.impl.FloatSetting;

import com.utilitymod.echo.Echo;
import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventRender2DGui;
import com.utilitymod.echo.event.impl.EventRender3D;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.settings.impl.BoolSetting;
import com.utilitymod.echo.features.settings.impl.ColorSetting;
import com.utilitymod.echo.features.settings.impl.ModeSetting;
import com.utilitymod.echo.features.settings.impl.MultiModeSetting;
import com.utilitymod.echo.render2.api.Draw2D;
import com.utilitymod.echo.render2.impl.opengl.utils.RenderUtil;
import com.utilitymod.echo.utils.strings.Concat;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Frustum;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.awt.Color;
import java.util.ArrayDeque;
import java.util.Queue;

public class PlayerESP extends Feature {

    private final MultiModeSetting enabledModes = new MultiModeSetting(Concat.of(LT.L1), Concat.of(LT.L2), Concat.of(LT.L3), Concat.of(LT.L4));
    private final ModeSetting shaderStyle = new ModeSetting(Concat.of(LT.L5), Concat.of(LT.L6), o -> enabledModes.isEnabled(Concat.of(LT.L4)),
            Concat.of(LT.L6), Concat.of(LT.L7), Concat.of(LT.L8), Concat.of(LT.L9), Concat.of(LT.L10), Concat.of(LT.L11), Concat.of(LT.L12), Concat.of(LT.L13), Concat.of(LT.L14), Concat.of(LT.L15));
    private final MultiModeSetting shaderTargets = new MultiModeSetting(Concat.of(LT.L16), o -> enabledModes.isEnabled(Concat.of(LT.L4)),
            Concat.of(LT.L17), Concat.of(LT.L18), Concat.of(LT.L19));
    private final FloatSetting shaderRadius = new FloatSetting(Concat.of(LT.L20), 10f, 1f, 30f, 0.5f, o -> enabledModes.isEnabled(Concat.of(LT.L4)));
    private final FloatSetting shaderFill = new FloatSetting(Concat.of(LT.L21), 0.15f, 0f, 1f, 0.05f, o -> enabledModes.isEnabled(Concat.of(LT.L4)));
    private final IntSetting shaderQuality = new IntSetting(Concat.of(LT.L22), 4, 1, 12, o -> enabledModes.isEnabled(Concat.of(LT.L4)));
    private final BoolSetting fill = new BoolSetting(Concat.of(LT.L23), false);
    private final ModeSetting healthBar = new ModeSetting(Concat.of(LT.L24), Concat.of(LT.L25), Concat.of(LT.L25), Concat.of(LT.L3), Concat.of(LT.L26));
    private final BoolSetting tracers = new BoolSetting(Concat.of(LT.L27), true);
    private final ColorSetting colorSetting = new ColorSetting(Concat.of(LT.L28), 0x7F8C0000);
    private final ColorSetting friendColor = new ColorSetting(Concat.of(LT.L29), 0x7F22C55E);

    private final Queue<Runnable> queue = new ArrayDeque<>();

    public PlayerESP() {
        super(new FeatureInfo(
                dev.potato.spud.api.ProtectedText.fromInts(new int[]{0x4E,0x45,0x11,0xC2,0xA7,0x7F,0x74,0xDA,0xF5,0xA1}),
                Concat.of(LT.L30),
                Category.RENDER
        ));
        // Shader mode (off by default): force the vanilla entity-outline silhouette (white) for
        // other players and run the selected Icons Pack shader over it (ShaderEsp).
        enabledModes.setOption(Concat.of(LT.L4), false);
        java.util.function.BooleanSupplier shaderOn = () -> isEnabled() && enabledModes.isEnabled(Concat.of(LT.L4));
        com.utilitymod.echo.render.ShaderEsp.activeWhen = shaderOn;
        shaderTargets.setOption(Concat.of(LT.L18), false);
        shaderTargets.setOption(Concat.of(LT.L19), false);
        java.util.function.Predicate<Object> shaded = e -> shaderOn.getAsBoolean() && (
                (e instanceof PlayerEntity p && p != mc.player && shaderTargets.isEnabled(Concat.of(LT.L17)))
                || (e instanceof net.minecraft.entity.mob.MobEntity && shaderTargets.isEnabled(Concat.of(LT.L18)))
                || (e instanceof net.minecraft.entity.ItemEntity && shaderTargets.isEnabled(Concat.of(LT.L19))));
        com.utilitymod.inject.Hooks.outlineOverride = shaded;
        com.utilitymod.inject.Hooks.outlineColor = e -> shaded.test(e) ? 0xFFFFFF : -1;
        com.utilitymod.echo.render.ShaderEsp.settings = () -> {
            com.utilitymod.echo.render.ShaderEsp.style = com.utilitymod.echo.render.ShaderEsp.Style.valueOf(
                    shaderStyle.getValue().toString().toUpperCase(java.util.Locale.ROOT).replace(' ', '_'));
            com.utilitymod.echo.render.ShaderEsp.color = colorSetting.getARGB() | 0xFF000000;
            com.utilitymod.echo.render.ShaderEsp.radius = shaderRadius.getValue();
            com.utilitymod.echo.render.ShaderEsp.fill = shaderFill.getValue();
            com.utilitymod.echo.render.ShaderEsp.quality = shaderQuality.getValue();
        };
    }

    @EventSubscribe
    public void event(EventRender3D event) {
        if (mc.world == null) return;

        var draw3 = event.getDraw3D().getCustomTarget();
        var draw2 = Echo.draw2D;
        Matrix4f mat = new Matrix4f();
        float tickDelta = event.getTickDelta();
        Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();
        Frustum frustum = new Frustum(event.getModelViewMatrix(), event.getProjectionMatrix());
        frustum.setPosition(cam.x, cam.y, cam.z);
        boolean box = enabledModes.isEnabled(Concat.of(LT.L2));
        boolean twoD = enabledModes.isEnabled(Concat.of(LT.L3));
        boolean bar = healthBar.is(Concat.of(LT.L3));
        boolean slim = healthBar.is(Concat.of(LT.L26));
        int baseFill = colorSetting.getARGB();
        int outlineBg = 0xFF000000;

        for (var entity : mc.world.getEntities()) {
            if (!(entity instanceof PlayerEntity player)) continue;
            if (player == mc.player) continue;
            boolean friend = com.utilitymod.echo.utils.player.PlayerUtils.isFriend(player.getName().getString());
            int fillColor = friend ? friendColor.getARGB() : baseFill;
            int outlineColor = fillColor | 0xFF000000;

            Vec3d pos = player.getLerpedPos(tickDelta);
            Box bb = player.getBoundingBox();
            double sx = bb.getLengthX();
            double sy = bb.getLengthY();
            double sz = bb.getLengthZ();
            double minX = pos.x - sx / 2.0;
            double minY = pos.y;
            double minZ = pos.z - sz / 2.0;

            if (box) {
                draw3.box(minX, minY, minZ, sx, sy, sz, fillColor);
                double maxX = minX + sx;
                double maxY = minY + sy;
                double maxZ = minZ + sz;
                draw3.line(minX, minY, minZ, maxX, minY, minZ, outlineColor);
                draw3.line(maxX, minY, minZ, maxX, minY, maxZ, outlineColor);
                draw3.line(maxX, minY, maxZ, minX, minY, maxZ, outlineColor);
                draw3.line(minX, minY, maxZ, minX, minY, minZ, outlineColor);
                draw3.line(minX, maxY, minZ, maxX, maxY, minZ, outlineColor);
                draw3.line(maxX, maxY, minZ, maxX, maxY, maxZ, outlineColor);
                draw3.line(maxX, maxY, maxZ, minX, maxY, maxZ, outlineColor);
                draw3.line(minX, maxY, maxZ, minX, maxY, minZ, outlineColor);
                draw3.line(minX, minY, minZ, minX, maxY, minZ, outlineColor);
                draw3.line(maxX, minY, minZ, maxX, maxY, minZ, outlineColor);
                draw3.line(maxX, minY, maxZ, maxX, maxY, maxZ, outlineColor);
                draw3.line(minX, minY, maxZ, minX, maxY, maxZ, outlineColor);
            }
            if (tracers.getValue()) {
                draw3.tracer(pos.x, pos.y + player.getStandingEyeHeight(), pos.z, outlineColor);
            }

            if (twoD || bar || slim) {
                Box worldBB = new Box(minX, minY, minZ, minX + sx, minY + sy, minZ + sz);
                if (!frustum.isVisible(worldBB)) continue;

                Rectangle rect = projectAABB(event, worldBB);
                if (!rect.visible) continue;

                float x = (float) rect.x;
                float y = (float) rect.y;
                float w = (float) (rect.z - rect.x);
                float h = (float) (rect.w - rect.y);

                if (twoD) {
                    if (fill.getValue()) {
                        queue.add(() -> draw2.rect(mat, x, y, w, h, 0, fillColor));
                    }
                    queue.add(() -> drawOutlineRect(draw2, mat, x - 1, y - 1, w + 2, h + 2, 1, outlineBg));
                    queue.add(() -> drawOutlineRect(draw2, mat, x, y, w, h, 1, outlineColor));
                }

                if (bar) {
                    float percent = healthPercent(player);
                    int healthColor = healthColor(percent);
                    float barW = Math.max(2f, w * 0.08f);
                    float gap = 2f;
                    float bx = x - barW - gap;
                    float fillH = h * percent;
                    queue.add(() -> draw2.rect(mat, bx - 1, y - 1, barW + 2, h + 2, 0, outlineBg));
                    queue.add(() -> draw2.rect(mat, bx, y, barW, h, 0, 0xFF202020));
                    queue.add(() -> draw2.rect(mat, bx, y + h - fillH, barW, fillH, 0, healthColor));
                } else if (slim) {
                    float percent = healthPercent(player);
                    int healthColor = healthColor(percent);
                    float width = 2f;
                    float gap = 4f;
                    float fillH = h * percent;
                    queue.add(() -> draw2.rect(mat, x - width - gap , y + h - fillH, width, fillH, 0, healthColor));
                }
            }
        }
    }

    @EventSubscribe
    public void event(EventRender2DGui event) {
        while (!queue.isEmpty()) {
            queue.poll().run();
        }
    }

    private static void drawOutlineRect(Draw2D draw, Matrix4f mat, float x, float y, float w, float h, float thickness, int color) {
        draw.rect(mat, x, y, w, thickness, 0, color);
        draw.rect(mat, x, y + h - thickness, w, thickness, 0, color);
        draw.rect(mat, x, y, thickness, h, 0, color);
        draw.rect(mat, x + w - thickness, y, thickness, h, 0, color);
    }

    private static float healthPercent(PlayerEntity player) {
        float heal = player.getHealth() + player.getAbsorptionAmount();
        return Math.min(Math.max(heal / player.getMaxHealth(), 0f), 1f);
    }

    private static int healthColor(float percent) {
        float hue = percent / 3f;
        return 0xFF000000 | (Color.HSBtoRGB(hue, 1f, 1f) & 0x00FFFFFF);
    }

    private static Rectangle projectAABB(EventRender3D event, Box bb) {
        MinecraftClient minecraft = MinecraftClient.getInstance();
        Vec3d camPos = minecraft.gameRenderer.getCamera().getCameraPos();
        Matrix4f mv = event.getModelViewMatrix();
        Matrix4f proj = event.getProjectionMatrix();
        int sw = RenderUtil.getScaledWidth();
        int sh = RenderUtil.getScaledHeight();

        Vec3d[] corners = {
                new Vec3d(bb.minX, bb.minY, bb.minZ),
                new Vec3d(bb.maxX, bb.minY, bb.minZ),
                new Vec3d(bb.maxX, bb.minY, bb.maxZ),
                new Vec3d(bb.minX, bb.minY, bb.maxZ),
                new Vec3d(bb.minX, bb.maxY, bb.minZ),
                new Vec3d(bb.maxX, bb.maxY, bb.minZ),
                new Vec3d(bb.maxX, bb.maxY, bb.maxZ),
                new Vec3d(bb.minX, bb.maxY, bb.maxZ)
        };

        Vector4f[] clip = new Vector4f[8];
        for (int i = 0; i < 8; i++) {
            Vec3d c = corners[i];
            Vector4f v = new Vector4f(
                    (float) (c.x - camPos.x),
                    (float) (c.y - camPos.y),
                    (float) (c.z - camPos.z),
                    1f);
            v.mul(mv);
            v.mul(proj);
            clip[i] = v;
        }

        int[][] edges = {
                {0,1},{1,2},{2,3},{3,0},
                {4,5},{5,6},{6,7},{7,4},
                {0,4},{1,5},{2,6},{3,7}
        };

        float epsilon = 0.05f;
        Rectangle rect = null;
        boolean visible = false;
        for (int[] e : edges) {
            Vector4f a = clip[e[0]];
            Vector4f b = clip[e[1]];
            boolean aIn = a.w() > epsilon;
            boolean bIn = b.w() > epsilon;
            if (!aIn && !bIn) continue;
            Vector4f ca = a;
            Vector4f cb = b;
            if (!aIn) {
                float t = (epsilon - a.w()) / (b.w() - a.w());
                ca = lerp4(a, b, t);
            } else if (!bIn) {
                float t = (epsilon - a.w()) / (b.w() - a.w());
                cb = lerp4(a, b, t);
            }
            visible = true;
            double ax = (ca.x() / ca.w() * 0.5 + 0.5) * sw;
            double ay = (0.5 - ca.y() / ca.w() * 0.5) * sh;
            double bx = (cb.x() / cb.w() * 0.5 + 0.5) * sw;
            double by = (0.5 - cb.y() / cb.w() * 0.5) * sh;
            if (rect == null) {
                rect = new Rectangle(ax, ay, ax, ay);
            }
            if (ax < rect.x) rect.x = ax;
            if (ay < rect.y) rect.y = ay;
            if (ax > rect.z) rect.z = ax;
            if (ay > rect.w) rect.w = ay;
            if (bx < rect.x) rect.x = bx;
            if (by < rect.y) rect.y = by;
            if (bx > rect.z) rect.z = bx;
            if (by > rect.w) rect.w = by;
        }
        if (rect == null) rect = new Rectangle(0, 0, 0, 0);
        rect.visible = visible;
        return rect;
    }

    private static Vector4f lerp4(Vector4f a, Vector4f b, float t) {
        return new Vector4f(
                a.x() + (b.x() - a.x()) * t,
                a.y() + (b.y() - a.y()) * t,
                a.z() + (b.z() - a.z()) * t,
                a.w() + (b.w() - a.w()) * t
        );
    }

    public static class Rectangle {
        public double x, y, z, w;
        public boolean visible;

        public Rectangle(double x, double y, double z, double w) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.w = w;
        }
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xcaf9,0x678c,0x906a,0x8520,0x17ec}, new char[]{0x67c1,0x1783,0xca9d,0x8545,0x9019}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1dcb,0xc20d,0x0c1e}, new char[]{0x0c5c,0xc262,0x1db3,0x5538}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x34ea,0xda5e}, new char[]{0xda6c,0x34ae,0x6cd6,0x057d}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1107,0x691e,0xc750,0x1804,0x538a,0x49c7}, new char[]{0x53d9,0xc738,0x697f,0x1860,0x1162,0x49b5}).plainString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb88b,0xef96,0x3bcd,0x0bda,0x0b14,0x0c41,0xae08,0xa3d3,0x83f6,0x0fbb,0xadf2,0x83e7}, new char[]{0x3b9e,0x0c29,0xb8ea,0x0bbe,0x8393,0xa3a1,0x0f9b,0x83b4,0xae7c,0xefef,0x0b78,0xad97}).plainString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x99c6,0x222c,0x20b6,0x1020,0xdbb4,0x84ae,0x465a}, new char[]{0x84e1,0x20c3,0x1054,0x2240,0x99af,0x4634,0xdbd1}).plainString();
        static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xaf9a,0xdb78,0x15d1,0x7e8b,0xc251,0x42df,0x4322}, new char[]{0x428d,0xaffb,0xc238,0x434c,0xdb1a,0x7ee4,0x15a6}).plainString();
        static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x104e,0xb8c9,0x8204,0x9f22}, new char[]{0xb888,0x9f57,0x103c,0x8265}).plainString();
        static final String L9 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7d34,0xa71d,0x7a40,0xbbea,0x8634}, new char[]{0x7a03,0x7d5c,0xa77c,0x8659,0xbb99}).plainString();
        static final String L10 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd78e,0xd414,0x0ba1,0xf220}, new char[]{0xd7c9,0x0bcd,0xd47b,0xf257}).plainString();
        static final String L11 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa079,0x0d28,0x482a,0xe5fa,0xdf16,0xf932,0x26e8,0x73de,0x6966,0x5195,0x3c0b,0x1671}, new char[]{0xf97e,0xe593,0x2699,0xdf63,0x0d41,0x73ba,0xa059,0x51d2,0x161d,0x3c6a,0x4859,0x6915}).plainString();
        static final String L12 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xaf20,0xc1fb,0x582d,0x8d6e,0x2f39,0x6368}, new char[]{0x8d2d,0x5845,0xaf52,0x2f56,0x6305,0xc19a}).plainString();
        static final String L13 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe87d,0x3e02,0xf334,0xda97,0x3fac}, new char[]{0x3e52,0xe808,0xdafb,0xf347,0x3fc9}).plainString();
        static final String L14 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x416e,0xd86c,0x916b,0x9a8c,0xcbdd,0xd109,0xe5b1,0x67f0}, new char[]{0x9123,0x9ae3,0xd165,0x4101,0x6797,0xd81e,0xe5d0,0xcbb0}).plainString();
        static final String L15 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4be5,0xb3da,0x4d93,0x2f80,0x4a6f,0xcb8b,0x41b1,0x6d94,0x3559}, new char[]{0x41e2,0x2ff4,0x4df2,0x4b97,0x6df2,0xb3b3,0xcbee,0x4a03,0x353d}).plainString();
        static final String L16 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb965,0xc8a9,0x01df,0x1c4d,0x67ee,0xf09d,0xd3c6,0x04dc,0x9eba,0xa795,0xc3b1,0xe121,0x326d,0x5a5c}, new char[]{0x323e,0xc3d9,0x1c2c,0xe145,0xd3a3,0xf0ef,0x5a7c,0x9eee,0x04bd,0x01ad,0xb902,0xa7f0,0x679a,0xc8da}).plainString();
        static final String L17 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x07ec,0x61b8,0x4b7b,0xf590,0x18ff,0x0803,0xec7f}, new char[]{0x4b2b,0xf5fc,0x189e,0x087a,0xec1a,0x079e,0x61cb}).plainString();
        static final String L18 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x607a,0x0d20,0x380d,0x5b0b}, new char[]{0x0d6d,0x5b64,0x6018,0x387e}).plainString();
        static final String L19 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x455f,0xe50e,0x269c,0xa457,0xd6fb}, new char[]{0x4516,0xd68f,0xa432,0xe563,0x26ef}).plainString();
        static final String L20 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6c6b,0xce4d,0xfa8a,0x8f70,0x6c59,0x449f,0xc2e0,0xb5fa,0xd963,0x9880,0x765e,0xde93,0x8ede}, new char[]{0xfad9,0xd90b,0x8f11,0xb59e,0x44fa,0x6c2b,0x98a0,0x6c39,0xce2c,0xdef7,0xc289,0x762b,0x8ead}).plainString();
        static final String L21 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x52e7,0x3655,0xba11,0x56d1,0xc044,0x83dc,0x79e8,0x5382,0xc855,0x838b,0x265a}, new char[]{0x5682,0xc83d,0x5286,0x263e,0x83b9,0x83f9,0xc064,0x79ae,0x363c,0x53ee,0xba7d}).plainString();
        static final String L22 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3ec6,0x2322,0x0dbc,0x7921,0xd160,0x8aed,0x3315,0xefc1,0x9e6d,0x7870,0x9c14,0x838c,0xc705,0x4049}, new char[]{0xc756,0x8a85,0x9c75,0x3ea2,0xefa4,0x403b,0x7850,0x83dd,0x3360,0x7940,0x9e01,0x0dd5,0x2356,0xd119}).plainString();
        static final String L23 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa502,0xf914,0x7a13,0xc93f}, new char[]{0x7a55,0xa56b,0xc953,0xf978}).plainString();
        static final String L24 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1067,0xb222,0x587a,0x9e33,0x61b3,0x9338,0xc18c,0x728d,0xbe54,0x3d8b}, new char[]{0xb26a,0x9e56,0x61d2,0x5816,0x1013,0xbe3c,0x72ad,0xc1ce,0x9359,0x3df9}).plainString();
        static final String L25 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4a66,0x6f33,0x49cd,0x3e78}, new char[]{0x4a28,0x3e17,0x6f5d,0x49a8}).plainString();
        static final String L26 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x56cd,0xd650,0x709f,0xf1c0}, new char[]{0xd603,0x70f3,0x56a4,0xf1ad}).plainString();
        static final String L27 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xec6e,0x402f,0x6255,0x3b69,0xe931,0xd8c7,0xd856}, new char[]{0x3b3d,0x6227,0x404e,0xd8a4,0xe954,0xd824,0xec1d}).plainString();
        static final String L28 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6f56,0xaf0f,0xc668,0xcba6,0x683e}, new char[]{0x687d,0xcbc9,0xc604,0xaf60,0x6f24}).plainString();
        static final String L29 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x21b6,0xed59,0x4084,0x6fa6,0x3d68,0x7043,0x6594,0xa66d,0xfb9b,0x976e,0xfee1,0xc070}, new char[]{0x7005,0xfbe9,0xfe88,0x970b,0x65fa,0x21d2,0x3d48,0x40c7,0xc01f,0xed35,0x6fc9,0xa61f}).plainString();
        static final String L30 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4c66,0x0eed,0xf786,0x6aea,0xd14e,0x3aef,0x34d2,0x3c2b,0x20ba,0xc8a0,0x88da,0x1c4e,0xf582,0x78b5,0x66d8,0xf6ad,0xe2fe,0xeabb}, new char[]{0x8892,0xc8c9,0x6a8d,0x3a87,0x3c47,0xe297,0xf6ca,0x20d2,0xd13a,0xf7f5,0x34f2,0x0e9d,0xead7,0x4c07,0x1c37,0xf5e7,0x78c7,0x66ab}).plainString();
    }
}
