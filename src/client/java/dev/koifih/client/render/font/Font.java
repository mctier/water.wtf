package dev.koifih.client.render.font;

import com.google.gson.Gson;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.utilitymod.UtilityMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * adin's MSDF atlases (same json + png) without the custom text pipeline.
 *
 * <p>A late-injected jar is invisible to the ResourceManager and the MSDF
 * shader cannot bind in the HUD, so the atlas is read through the
 * classloader and resolved once on the CPU: median(R,G,B) is turned into a
 * white coverage mask, registered as a {@link NativeImageBackedTexture} with
 * a linear sampler. Glyphs are then plain tinted quads at float positions.
 */
public final class Font {
    private static final String ASSET_ROOT = LT.L1;

    public record Bounds(float left, float top, float right, float bottom) {}
    public record Glyph(int unicode, float advance, Bounds planeBounds, Bounds atlasBounds) {}
    private record Atlas(String type, float distanceRange, int width, int height, String yOrigin) {}
    private record KerningPair(int unicode1, int unicode2, float advance) {}
    private record Metrics(Atlas atlas, Glyph[] glyphs, KerningPair[] kerning) {}

    private final String name;
    private final int fallbackCodepoint;
    private final Identifier texture;
    private final Map<Integer, Glyph> glyphs = new HashMap<>();
    private final Map<Long, Float> kerning = new HashMap<>();
    private Atlas atlas;
    private Glyph fallback;
    private boolean uploaded;
    private boolean failed;

    private Font(String name, int fallbackCodepoint) {
        this.name = name;
        this.fallbackCodepoint = fallbackCodepoint;
        this.texture = Identifier.of(UtilityMod.MOD_ID, LT.L2 + name.replace('/', '_'));
    }

    public static Font load(String name, int fallbackCodepoint) {
        return new Font(name, fallbackCodepoint);
    }

    private boolean metrics() {
        if (atlas != null) return true;
        if (failed) return false;
        try (InputStream in = Font.class.getResourceAsStream(ASSET_ROOT + name + LT.L3)) {
            if (in == null) throw new IllegalStateException(LT.L4 + name + LT.L3);
            Metrics m = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), Metrics.class);
            for (Glyph g : m.glyphs) glyphs.put(g.unicode, g);
            if (m.kerning != null) {
                for (KerningPair p : m.kerning) kerning.put(key(p.unicode1, p.unicode2), p.advance);
            }
            fallback = glyphs.get(fallbackCodepoint);
            atlas = m.atlas;
            return true;
        } catch (Throwable t) {
            failed = true;
            UtilityMod.LOGGER.error(LT.L5, name, t);
            return false;
        }
    }

    /** Uploads the resolved coverage mask; must run on the render thread. */
    private boolean upload() {
        if (uploaded) return true;
        if (!metrics()) return false;
        try (InputStream in = Font.class.getResourceAsStream(ASSET_ROOT + name + LT.L6)) {
            if (in == null) throw new IllegalStateException(LT.L4 + name + LT.L6);
            NativeImage img = NativeImage.read(in);
            float range = atlas.distanceRange;
            for (int y = 0; y < img.getHeight(); y++) {
                for (int x = 0; x < img.getWidth(); x++) {
                    int argb = img.getColorArgb(x, y);
                    int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
                    float median = Math.max(Math.min(r, g), Math.min(Math.max(r, g), b)) / 255f;
                    // Signed distance in atlas pixels; slightly soft edge so
                    // linear minification at GUI sizes stays smooth.
                    float alpha = Math.clamp((median - 0.5f) * range * 0.5f + 0.5f, 0f, 1f);
                    img.setColorArgb(x, y, (Math.round(alpha * 255) << 24) | 0xFFFFFF);
                }
            }
            NativeImageBackedTexture tex = new NativeImageBackedTexture(texture::toString, img);
            MinecraftClient.getInstance().getTextureManager().registerTexture(texture, tex);
            try {
                GpuSampler linear = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);
                Field f = AbstractTexture.class.getDeclaredField(LT.L7);
                f.setAccessible(true);
                f.set(tex, linear);
            } catch (Throwable ignored) {
                // Nearest sampling still renders, just crunchier.
            }
            uploaded = true;
            return true;
        } catch (Throwable t) {
            failed = true;
            UtilityMod.LOGGER.error(LT.L8, name, t);
            return false;
        }
    }

    public Glyph glyph(int codepoint) {
        if (!metrics()) return null;
        Glyph g = glyphs.get(codepoint);
        return g != null ? g : fallback;
    }

    public float kerning(int previous, int current) {
        if (kerning.isEmpty()) return 0f;
        return kerning.getOrDefault(key(previous, current), 0f);
    }

    /** Draws the glyph's atlas cell stretched over the given screen rect. */
    public void quad(DrawContext ctx, Glyph glyph, float x0, float y0, float x1, float y1, int color) {
        if (glyph == null || glyph.planeBounds == null || glyph.atlasBounds == null) return;
        if (!upload() || x1 <= x0 || y1 <= y0) return;
        Bounds a = glyph.atlasBounds;
        int rw = Math.max(1, Math.round(a.right - a.left));
        int rh = Math.max(1, Math.round(a.bottom - a.top));
        var m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(x0, y0);
        m.scale((x1 - x0) / rw, (y1 - y0) / rh);
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, texture, 0, 0, a.left, a.top, rw, rh, rw, rh,
                atlas.width, atlas.height, color);
        m.popMatrix();
    }

    private static long key(int a, int b) {
        return ((long) a << 32) | (b & 0xffffffffL);
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1872,0x32b9,0xe849,0x4d30,0xd0b5,0xd010,0x3790,0xdaf4,0xef2e,0xacde,0x207d,0x2379,0x839d}, new char[]{0x3296,0x83fc,0x1801,0xef5d,0x2018,0xacaa,0xd063,0xe866,0xda95,0xd0d1,0x4d59,0x2317,0x37bf}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x97d7,0x7367,0x9d02,0x9db3,0x8a74,0x9b68,0x627b,0x0185,0x616a,0xb45e}, new char[]{0x97b6,0x9dd7,0x730e,0x9b06,0x6135,0xb433,0x9d71,0x8a10,0x621d,0x01aa}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xda68,0xbc6f,0xf51f,0xfbe3,0x841c}, new char[]{0xda46,0x8476,0xfb90,0xbc00,0xf571}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4e93,0x4679,0xf080,0x734b,0xf76b,0x11b7,0x9d7a,0x2aa4}, new char[]{0x2ac9,0x9d13,0xf718,0x4ee0,0x11de,0xf0ee,0x732c,0x4659}).plainString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6e53,0x9e47,0x367c,0xcee6,0xbb62,0x0abf,0x22e8,0x1272,0x7cf7,0xc957,0xb1c8,0xf897,0x4cfd,0xf672,0xe0f5,0x0892,0xa4d3,0xd4c1,0xc1c1,0x7528,0xa724,0xfbbc,0x4308,0x20f7,0x292f,0xd838,0x8dcd,0x00ce,0xfcaa,0x99a0,0x9222,0x6c2d,0xf017,0x3164}, new char[]{0xc19a,0xfccb,0x4c99,0x7c9e,0x2099,0xb195,0x365c,0x00ad,0x4369,0x8da3,0xf079,0xf61d,0xa750,0xfb9c,0x121e,0x9e28,0xbb03,0x6c49,0x6e73,0x3102,0xe09a,0xa4bd,0x9256,0xd4e1,0xce8b,0x294a,0x99d4,0x0acd,0xc93e,0x08f1,0x229b,0x7508,0xf8ec,0xd845}).plainString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x94fc,0x2adf,0x92c2,0x98a6}, new char[]{0x92ec,0x948c,0x2ab1,0x98c1}).plainString();
        static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb1d0,0x16ae,0x2dd1,0xc99e,0xca95,0xc09c,0x1702}, new char[]{0xcae6,0xc0fd,0x176f,0xc9ee,0xb1bc,0x2db4,0x16dc}).plainString();
        static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xedb2,0x34c0,0x5289,0xb428,0x14a7,0xf074,0x051b,0x22cf,0xea14,0xfaf8,0xbf56,0xd99b,0xa3f8,0x5953,0x61a8,0x2552,0x50b2,0x5d0a,0x0071,0x269c,0x0b41,0xddc0,0xbe91,0xa298,0xe1f2,0xd976,0x2f25,0x793c,0xcc4c,0x876a,0x9d92,0x7eb3,0x6a4e,0xb137}, new char[]{0x2294,0xdda1,0xa2fc,0x5d63,0x2f4b,0x002c,0xb117,0x5930,0x61c9,0x253c,0xcc22,0xa397,0xb45c,0x26bc,0xbee4,0xfa88,0xe19e,0x0574,0xd9fa,0x52ed,0x34e0,0xd910,0xea7b,0xbf38,0x0b35,0xf054,0x14c6,0x50c6,0x8706,0x7ed2,0x9de1,0xed92,0x7947,0x6a33}).plainString();
    }
}
