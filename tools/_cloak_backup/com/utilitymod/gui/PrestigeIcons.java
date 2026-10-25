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
        try (InputStream in = PrestigeIcons.class.getResourceAsStream("/assets/utilitymod/textures/gui/prestige/" + name + ".png")) {
            if (in != null) {
                NativeImage img = NativeImage.read(in);
                id = Identifier.of(UtilityMod.MOD_ID, "gui/prestige/" + name);
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
}
