package com.utilitymod.echo.render;

import com.utilitymod.UtilityMod;
import com.utilitymod.echo.EchoEvents;
import com.utilitymod.echo.Render3D;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Clean custom particles: textures are generated procedurally at first use (soft glow, star,
 * heart, sparkle, snowflake, ring - white with alpha, tinted per particle), simulated in world
 * space and drawn as camera-facing sprites in the HUD pass via the captured camera projection.
 */
public final class CustomParticles {
    public enum Shape { GLOW, STAR, HEART, SPARKLE, SNOW, RING }

    private static final int TEX = 32;
    private static final Map<Shape, Identifier> IDS = new EnumMap<>(Shape.class);
    private static final List<P> LIVE = new ArrayList<>();
    private static final int MAX = 600;
    private static long lastNanos = System.nanoTime();

    private static final class P {
        double x, y, z, vx, vy, vz;
        float size, life, age, spin, rot, gravity;
        int color;
        Shape shape;
    }

    private CustomParticles() {}

    // ---- spawning ----------------------------------------------------------------------------------

    public static void spawn(Shape shape, double x, double y, double z, double vx, double vy, double vz,
                             float size, float lifeSeconds, int argb, float gravity) {
        if (LIVE.size() >= MAX) return;
        P p = new P();
        p.x = x; p.y = y; p.z = z; p.vx = vx; p.vy = vy; p.vz = vz;
        p.size = size; p.life = lifeSeconds; p.color = argb; p.shape = shape; p.gravity = gravity;
        p.rot = ThreadLocalRandom.current().nextFloat() * 360f;
        p.spin = (ThreadLocalRandom.current().nextFloat() - 0.5f) * 240f;
        LIVE.add(p);
    }

    /** A spherical-ish burst around a point. */
    public static void burst(Shape shape, double x, double y, double z, int count, double speed, float size,
                             float life, int argb, float gravity) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < count; i++) {
            double th = r.nextDouble() * Math.PI * 2, ph = Math.acos(2 * r.nextDouble() - 1);
            double s = speed * (0.4 + 0.6 * r.nextDouble());
            spawn(shape, x, y, z, Math.sin(ph) * Math.cos(th) * s, Math.abs(Math.cos(ph)) * s * 0.8 + speed * 0.2,
                    Math.sin(ph) * Math.sin(th) * s, size * (0.7f + 0.6f * r.nextFloat()), life * (0.7f + 0.6f * r.nextFloat()),
                    argb, gravity);
        }
    }

    // ---- simulate + draw (HUD pass) ---------------------------------------------------------------

    public static void render(DrawContext ctx) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastNanos) / 1e9f);
        lastNanos = now;
        if (LIVE.isEmpty()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        Render3D frame = EchoEvents.lastFrame();
        if (mc.world == null || frame == null) {
            LIVE.clear();
            return;
        }
        ensureTextures();
        int sh = ctx.getScaledWindowHeight();
        var m = ctx.getMatrices();
        for (Iterator<P> it = LIVE.iterator(); it.hasNext(); ) {
            P p = it.next();
            p.age += dt;
            if (p.age >= p.life) {
                it.remove();
                continue;
            }
            // integrate: drag + gravity
            double drag = Math.pow(0.18, dt);
            p.vx *= drag; p.vz *= drag;
            p.vy = p.vy * drag - p.gravity * dt;
            p.x += p.vx * dt; p.y += p.vy * dt; p.z += p.vz * dt;
            p.rot += p.spin * dt;

            Vector4f clip = frame.toClip(p.x, p.y, p.z);
            if (clip.w() <= 0.05f) continue;
            float[] g = Render3D.toGui(clip);
            // world size -> screen px (projection y scale * screen half height / depth)
            float px = p.size * (sh / 2f) * Math.abs(frame.projection.m11()) / clip.w();
            if (px < 0.5f || px > 400f) continue;
            float t = p.age / p.life;
            float alpha = t < 0.15f ? t / 0.15f : 1f - (t - 0.15f) / 0.85f;
            float scale = 1f - 0.35f * t;
            int a = Math.round(((p.color >>> 24) & 255) * alpha);
            int col = (a << 24) | (p.color & 0xFFFFFF);
            float s = px * scale;
            m.pushMatrix();
            m.translate(g[0], g[1]);
            m.rotate((float) Math.toRadians(p.rot));
            m.scale(s / TEX, s / TEX);
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, IDS.get(p.shape), -TEX / 2, -TEX / 2, 0f, 0f, TEX, TEX, TEX, TEX, col);
            m.popMatrix();
        }
    }

    // ---- procedural textures ----------------------------------------------------------------------

    private static boolean texturesReady;

    private static void ensureTextures() {
        if (texturesReady) return;
        texturesReady = true;
        var tm = MinecraftClient.getInstance().getTextureManager();
        for (Shape s : Shape.values()) {
            NativeImage img = new NativeImage(TEX, TEX, true);
            for (int y = 0; y < TEX; y++) {
                for (int x = 0; x < TEX; x++) {
                    float u = (x + 0.5f) / TEX * 2 - 1, v = (y + 0.5f) / TEX * 2 - 1;
                    float a = Math.max(0f, Math.min(1f, alpha(s, u, v)));
                    img.setColorArgb(x, y, (Math.round(a * 255) << 24) | 0xFFFFFF);
                }
            }
            Identifier id = Identifier.of(UtilityMod.MOD_ID, LT.L1 + s.name().toLowerCase());
            tm.registerTexture(id, new NativeImageBackedTexture(id::toString, img));
            IDS.put(s, id);
        }
    }

    private static float smooth(float e0, float e1, float x) {
        float t = Math.max(0f, Math.min(1f, (x - e0) / (e1 - e0)));
        return t * t * (3 - 2 * t);
    }

    /** Alpha of each shape at (u, v) in [-1, 1]. */
    private static float alpha(Shape s, float u, float v) {
        float r = (float) Math.sqrt(u * u + v * v);
        float glow = (float) Math.pow(Math.max(0f, 1f - r), 2.2);
        switch (s) {
            case GLOW:
                return glow;
            case STAR: {
                // 4-point star: thin diamond arms + core
                float arms = Math.max(1f - (Math.abs(u) * 6 + Math.abs(v) * 1.1f), 1f - (Math.abs(v) * 6 + Math.abs(u) * 1.1f));
                return Math.max(smooth(0f, 0.25f, arms), glow * 0.6f);
            }
            case HEART: {
                float x = u * 1.25f, y = -v * 1.25f + 0.25f;
                float f = (float) Math.pow(x * x + y * y - 1, 3) - x * x * y * y * y;
                return smooth(0.06f, -0.06f, f);
            }
            case SPARKLE: {
                float cross = Math.max(smooth(0.1f, 0f, Math.abs(u)) * smooth(1f, 0.2f, Math.abs(v)),
                        smooth(0.1f, 0f, Math.abs(v)) * smooth(1f, 0.2f, Math.abs(u)));
                return Math.max(cross, glow * 0.8f);
            }
            case SNOW: {
                float ang = (float) Math.atan2(v, u);
                float arm = (float) Math.abs(Math.cos(ang * 3));      // 6 arms
                float line = smooth(0.12f, 0.02f, (1 - arm) * r * 3) * smooth(0.95f, 0.7f, r);
                return Math.max(line, smooth(0.3f, 0.1f, r));
            }
            case RING:
            default:
                return smooth(0.18f, 0.05f, Math.abs(r - 0.7f)) + glow * 0.25f;
        }
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6d6b,0x3919,0xa083,0xa29a,0x1b8e,0x7a8a,0x633a,0x85f1,0xa78e,0x940b}, new char[]{0xa7fe,0x3978,0x1bfc,0x8585,0x6353,0xa2f9,0x6d07,0x946e,0xa0f0,0x7aa5}).plainString();
    }
}
