package com.utilitymod.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * Antialiased rounded primitives at screen resolution.
 *
 * <p>Straight parts are plain fills; each corner is a quarter of a baked
 * disc / ring texture, tinted and stretched over the corner at float
 * precision. Textures are baked per power-of-two size closest to the corner's
 * real on-screen pixel size, so the antialiased edge is ~1 screen pixel wide
 * at every GUI scale (no GUI-pixel staircase).
 */
public final class Rounded {
    private static final Map<Long, Identifier> CACHE = new HashMap<>();
    public static final int TOP_LEFT = 1, TOP_RIGHT = 2, BOTTOM_RIGHT = 4, BOTTOM_LEFT = 8, ALL = 15;

    private Rounded() {}

    public static float sdRoundBox(float px, float py, float hx, float hy, float r) {
        float qx = Math.abs(px) - (hx - r), qy = Math.abs(py) - (hy - r);
        return (float) (Math.min(Math.max(qx, qy), 0) + Math.hypot(Math.max(qx, 0), Math.max(qy, 0)) - r);
    }

    public static boolean inside(float mx, float my, int x, int y, int w, int h, float r) {
        if (w <= 0 || h <= 0) return false;
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2f));
        return sdRoundBox(mx - x - w / 2f, my - y - h / 2f, w / 2f, h / 2f, r) <= 0;
    }

    public static void fill(DrawContext ctx, int x, int y, int w, int h, float radius, int color) {
        fill(ctx, x, y, w, h, radius, ALL, color);
    }

    public static void fill(DrawContext ctx, int x, int y, int w, int h, float radius, int corners, int color) {
        if (w <= 0 || h <= 0 || (color >>> 24) == 0) return;
        int r = Math.round(Math.max(0f, Math.min(radius, Math.min(w, h) / 2f)));
        if (r == 0) {
            ctx.fill(x, y, x + w, y + h, color);
            return;
        }
        ctx.fill(x + r, y, x + w - r, y + h, color);
        ctx.fill(x, y + r, x + r, y + h - r, color);
        ctx.fill(x + w - r, y + r, x + w, y + h - r, color);
        corner(ctx, x, y, r, 0, 0, corners & TOP_LEFT, color);
        corner(ctx, x + w - r, y, r, 1, 0, corners & TOP_RIGHT, color);
        corner(ctx, x, y + h - r, r, 0, 1, corners & BOTTOM_LEFT, color);
        corner(ctx, x + w - r, y + h - r, r, 1, 1, corners & BOTTOM_RIGHT, color);
    }

    public static void outline(DrawContext ctx, int x, int y, int w, int h, float radius, float thickness, int color) {
        if (w <= 0 || h <= 0 || thickness <= 0 || (color >>> 24) == 0) return;
        int t = Math.max(1, Math.round(thickness));
        int r = Math.round(Math.max(0f, Math.min(radius, Math.min(w, h) / 2f)));
        if (2 * t >= w || 2 * t >= h) {
            fill(ctx, x, y, w, h, r, color);
            return;
        }
        int e = Math.max(r, t);
        ctx.fill(x + e, y, x + w - e, y + t, color);
        ctx.fill(x + e, y + h - t, x + w - e, y + h, color);
        ctx.fill(x, y + e, x + t, y + h - e, color);
        ctx.fill(x + w - t, y + e, x + w, y + h - e, color);
        if (r <= t) {
            ctx.fill(x, y, x + e, y + t, color);
            ctx.fill(x, y + t, x + t, y + e, color);
            ctx.fill(x + w - e, y, x + w, y + t, color);
            ctx.fill(x + w - t, y + t, x + w, y + e, color);
            ctx.fill(x, y + h - t, x + e, y + h, color);
            ctx.fill(x, y + h - e, x + t, y + h - t, color);
            ctx.fill(x + w - e, y + h - t, x + w, y + h, color);
            ctx.fill(x + w - t, y + h - e, x + w, y + h - t, color);
            return;
        }
        float ring = t / (float) r;
        quarter(ctx, x, y, r, 0, 0, ring, color);
        quarter(ctx, x + w - r, y, r, 1, 0, ring, color);
        quarter(ctx, x, y + h - r, r, 0, 1, ring, color);
        quarter(ctx, x + w - r, y + h - r, r, 1, 1, ring, color);
    }

    public static void glow(DrawContext ctx, int x, int y, int w, int h, float radius, float glowSize, float strength, int color) {
        if (glowSize <= 0 || strength <= 0) return;
        int base = color >>> 24;
        for (int i = 3; i >= 1; i--) {
            float distance = glowSize * i / 4f;
            int a = Math.round(base * (float) Math.pow(1 - distance / glowSize, 1.5) * strength / 4f);
            if (a <= 1) continue;
            int pad = Math.round(distance);
            fill(ctx, x - pad, y - pad, w + 2 * pad, h + 2 * pad, radius + pad, (a << 24) | (color & 0xffffff));
        }
    }

    private static void corner(DrawContext ctx, int x, int y, int r, int qx, int qy, int rounded, int color) {
        if (rounded == 0) ctx.fill(x, y, x + r, y + r, color);
        else quarter(ctx, x, y, r, qx, qy, 0f, color);
    }

    /** Draws quadrant (qx,qy) of a disc (ring == 0) or ring of relative thickness {@code ring}. */
    private static void quarter(DrawContext ctx, int x, int y, int r, int qx, int qy, float ring, int color) {
        int n = textureSize(r);
        int ringTexels = ring <= 0f ? 0 : Math.max(1, Math.round(ring * n));
        Identifier tex = texture(n, ringTexels);
        if (tex == null) {
            ctx.fill(x, y, x + r, y + r, color);
            return;
        }
        var m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(x, y);
        m.scale(r / (float) n, r / (float) n);
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, tex, 0, 0, qx * n, qy * n, n, n, n, n, 2 * n, 2 * n, color);
        m.popMatrix();
    }

    private static int textureSize(int r) {
        double scale;
        try {
            scale = MinecraftClient.getInstance().getWindow().getScaleFactor();
        } catch (Throwable t) {
            scale = 2;
        }
        int px = (int) Math.ceil(r * scale);
        int n = 4;
        while (n < px && n < 128) n <<= 1;
        return n;
    }

    private static Identifier texture(int n, int ring) {
        long key = ((long) n << 32) | ring;
        Identifier cached = CACHE.get(key);
        if (cached != null) return cached;
        try {
            int size = 2 * n;
            NativeImage img = new NativeImage(size, size, false);
            for (int py = 0; py < size; py++) {
                for (int px = 0; px < size; px++) {
                    float dx = px + 0.5f - n, dy = py + 0.5f - n;
                    float d = (float) Math.sqrt(dx * dx + dy * dy);
                    float a = clamp(n - d + 0.5f);
                    if (ring > 0) a -= clamp(n - ring - d + 0.5f);
                    img.setColorArgb(px, py, (Math.round(Math.max(0f, a) * 255) << 24) | 0xFFFFFF);
                }
            }
            Identifier id = Identifier.of(LT.L1, LT.L2 + n + LT.L3 + ring);
            NativeImageBackedTexture tex = new NativeImageBackedTexture(id::toString, img);
            MinecraftClient.getInstance().getTextureManager().registerTexture(id, tex);
            try {
                GpuSampler linear = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);
                Field f = AbstractTexture.class.getDeclaredField(LT.L4);
                f.setAccessible(true);
                f.set(tex, linear);
            } catch (Throwable ignored) {
                // Texel-exact sizing still keeps nearest sampling smooth.
            }
            CACHE.put(key, id);
            return id;
        } catch (Throwable t) {
            CACHE.put(key, null);
            return null;
        }
    }

    private static float clamp(float v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1e5b,0x7c5c,0x2ad4,0x9532,0x448f,0xe23d,0x8a4b,0xb362,0x2d2e,0x357c}, new char[]{0xb317,0x9546,0x1e32,0x3510,0x2d47,0x8a3f,0xe244,0x44e2,0x7c33,0x2ab0}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xcfa7,0x6075,0x79ca,0x8f27,0xf1d5,0x87d0,0x0fa4,0x3386}, new char[]{0xcfd5,0x601a,0x8f52,0x79a4,0x87b4,0x0fc1,0x33e2,0xf1fa}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9ca3}, new char[]{0x9cfc,0x25df,0x4e76,0xdb19}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x70ed,0xad79,0x0de6,0xa446,0x0b72,0xd0ac,0xe7f5}, new char[]{0x709e,0xa427,0x0d8b,0xd0dc,0xad15,0x0b17,0xe787}).plainString();
    }
}
