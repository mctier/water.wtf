package com.utilitymod.gui;

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
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/** White PNG glyphs (from LegacyPort) for the Cards GUI, loaded via the classloader for late injection and tinted on draw. */
public final class PrestigeIcons {
    private static final Map<String, Identifier> IDS = new HashMap<>();
    private static final Map<String, Integer> SIZE = new HashMap<>();
    private PrestigeIcons() {}

    private static Identifier load(String name) {
        if (IDS.containsKey(name)) return IDS.get(name);
        Identifier id = null;
        try (InputStream in = PrestigeIcons.class.getResourceAsStream(LT.L1 + name + LT.L2)) {
            if (in != null) {
                NativeImage img = NativeImage.read(in);
                id = Identifier.of(UtilityMod.MOD_ID, LT.L3 + name);
                NativeImageBackedTexture tex = new NativeImageBackedTexture(id::toString, img);
                MinecraftClient.getInstance().getTextureManager().registerTexture(id, tex);
                SIZE.put(name, img.getWidth());
                try {   // smooth minification instead of the default nearest sampler
                    GpuSampler linear = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);
                    for (Field f : AbstractTexture.class.getDeclaredFields()) {
                        if (f.getType() == GpuSampler.class) { f.setAccessible(true); f.set(tex, linear); break; }
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {
            id = null;
        }
        IDS.put(name, id);
        return id;
    }

    /** Draws the named glyph as a size x size square tinted argb; false if it isn't available. */
    public static boolean draw(DrawContext g, String name, float x, float y, float size, int argb) {
        if (name == null) return false;
        Identifier id = load(name);
        if (id == null) return false;
        int tex = SIZE.getOrDefault(name, 48);
        var m = g.getMatrices();
        m.pushMatrix();
        m.translate(x, y);
        m.scale(size / tex, size / tex);
        g.drawTexture(RenderPipelines.GUI_TEXTURED, id, 0, 0, 0f, 0f, tex, tex, tex, tex, argb);
        m.popMatrix();
        return true;
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9d30,0x677d,0xefbf,0xc736,0x4d62,0xd292,0xfe89,0x1aa7,0x6d37,0xac6f,0xcda0,0x5e2d,0xd5f1,0x1890,0x571b,0x5934,0xcc59,0xe2e7,0x0b23,0x978f,0x907e,0x047e,0x8f0f,0x730e,0x055f,0x0026,0x55f9,0x0a4f,0xf306,0x5a79,0x4836,0x36cb,0x4dba,0x0a2c,0xe72a,0x944b,0xa0a3,0x47f6,0x69bf,0x36c5,0x20fa}, new char[]{0x9051,0x1ac6,0x040d,0x0a3c,0x736b,0x558d,0xd2e1,0xd5de,0x18e5,0x5a0d,0x36ac,0x9d5c,0x2093,0x6d43,0x484f,0x8f62,0x36a4,0xcc3d,0x97a0,0x052b,0xf363,0xac17,0xcdd4,0x943e,0xc744,0x6718,0x5768,0x0009,0x0a4b,0x4dcf,0x69d6,0x4d4d,0x4786,0x0b51,0xefda,0xa0d0,0xfefd,0xe743,0x5e4a,0xe282,0x591b}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xddd8,0xf7ad,0x10dc,0x77c8}, new char[]{0x77e6,0xf7dd,0xddb6,0x10bb}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9f11,0x03ae,0x127d,0xf09d,0x1953,0x6675,0x7158,0x6fa2,0xda5b,0x8b94,0x088c,0x19ba,0x2bf7}, new char[]{0xf0fa,0x08f9,0x19d3,0x9f3e,0x120d,0x1921,0x713d,0x2b84,0x8be0,0x6fcb,0x03c9,0xda3e,0x665a}).plainString();
    }
}
