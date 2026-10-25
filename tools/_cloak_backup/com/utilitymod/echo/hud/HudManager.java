package com.utilitymod.echo.hud;

import com.google.gson.JsonObject;
import com.utilitymod.echo.render.GlassRenderer;
import dev.koifih.client.render.Draw;
import dev.koifih.client.ui.Theme;
import dev.koifih.client.util.Colors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Lays out and draws all {@link HudElement}s: background pass first (theme
 * panels, or every visible element's liquid-glass panel in one batched shader
 * pass), then each element's contents. Positions persist through
 * GuiConfig ({@link #save}/{@link #load}).
 */
public final class HudManager {
    private static final List<HudElement> ELEMENTS = new ArrayList<>();
    private static final java.util.Set<String> FAILED = java.util.concurrent.ConcurrentHashMap.newKeySet();
    /** Set by the HUD module. */
    public static volatile boolean enabled = true;
    public static volatile boolean liquidGlass = false;
    public static volatile float panelOpacity = 0.9f;

    private HudManager() {}

    public static void register(HudElement e) {
        ELEMENTS.add(e);
    }

    public static List<HudElement> elements() {
        return ELEMENTS;
    }

    /** Screen-space rect {x, y, w, h} of an element (GUI px). */
    public static float[] rect(HudElement e, int sw, int sh) {
        float w = e.width() * e.scale, h = e.height() * e.scale;
        float x = Math.max(0, Math.min(sw - w, e.x * (sw - w)));
        float y = Math.max(0, Math.min(sh - h, e.y * (sh - h)));
        return new float[] {x, y, w, h};
    }

    /** Moves an element so its top-left is at (px, py) GUI px. */
    public static void place(HudElement e, float px, float py, int sw, int sh) {
        float w = e.width() * e.scale, h = e.height() * e.scale;
        e.x = sw - w <= 0 ? 0 : Math.max(0, Math.min(1, px / (sw - w)));
        e.y = sh - h <= 0 ? 0 : Math.max(0, Math.min(1, py / (sh - h)));
    }

    /** In-game HUD pass. */
    public static void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options.hudHidden || mc.currentScreen instanceof HudEditorScreen) return;
        draw(ctx, false);
    }

    /** Draws every element (editor: all visible ones with sample data). */
    static void draw(DrawContext ctx, boolean editor) {
        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
        List<HudElement> shown = new ArrayList<>();
        for (HudElement e : ELEMENTS) {
            if (!e.visible) continue;
            if (!editor && !e.active()) continue;
            shown.add(e);
        }
        // backgrounds
        boolean anyGlass = false;
        for (HudElement e : shown) {
            if (!e.wantsBackground() || !glass(e)) continue;
            anyGlass = true;
            float[] r = rect(e, sw, sh);
            float a = editor ? 1f : e.alpha();
            GlassRenderer.queue(new GlassRenderer.Panel(r[0], r[1], r[2], r[3], e.radius() * e.scale, a));
        }
        if (anyGlass) GlassRenderer.flush(ctx);
        for (HudElement e : shown) {
            float[] r = rect(e, sw, sh);
            float a = editor ? 1f : e.alpha();
            var m = ctx.getMatrices();
            m.pushMatrix();
            m.translate(r[0], r[1]);
            m.scale(e.scale, e.scale);
            if (e.wantsBackground() && !glass(e)) {
                Draw.rect(ctx, 0, 0, Math.round(e.width()), Math.round(e.height()), Math.round(e.radius()),
                        Colors.withAlpha(Theme.SIDEBAR, panelOpacity * a));
            }
            try {
                e.render(ctx, editor);
            } catch (Throwable t) {
                // one broken element must never take the game down: skip it and log once
                if (FAILED.add(e.id)) com.utilitymod.UtilityMod.LOGGER.error("HUD element " + e.id + " failed to render", t);
            }
            m.popMatrix();
        }
    }

    /** Whether an element's background is drawn as liquid glass. */
    public static boolean glass(HudElement e) {
        return (liquidGlass || e.forceGlass()) && GlassRenderer.available();
    }

    // ---- persistence --------------------------------------------------------------------

    public static JsonObject save() {
        JsonObject all = new JsonObject();
        all.addProperty(LT.L1, liquidGlass);
        for (HudElement e : ELEMENTS) {
            JsonObject o = new JsonObject();
            o.addProperty(LT.L2, e.x);
            o.addProperty(LT.L3, e.y);
            o.addProperty(LT.L4, e.scale);
            o.addProperty(LT.L5, e.visible);
            all.add(e.id, o);
        }
        return all;
    }

    public static void load(JsonObject all) {
        if (all == null) return;
        if (all.has(LT.L1)) liquidGlass = all.get(LT.L1).getAsBoolean();
        for (HudElement e : ELEMENTS) {
            if (!all.has(e.id) || !all.get(e.id).isJsonObject()) continue;
            JsonObject o = all.getAsJsonObject(e.id);
            if (o.has(LT.L2)) e.x = o.get(LT.L2).getAsFloat();
            if (o.has(LT.L3)) e.y = o.get(LT.L3).getAsFloat();
            if (o.has(LT.L4)) e.scale = o.get(LT.L4).getAsFloat();
            if (o.has(LT.L5)) e.visible = o.get(LT.L5).getAsBoolean();
        }
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9f82,0x3b93,0x3ed8,0x5b61,0x4268,0x44c7,0xbe07,0xf026,0x33e6,0xa8c0,0xddd9}, new char[]{0xddb5,0xbe6e,0x3ea9,0x3393,0x5b08,0xf042,0x9fc5,0x3bff,0x44a6,0xa8b3,0x421b}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x650d}, new char[]{0x6575,0xec38,0x15ac,0xb3e2}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3ee4}, new char[]{0x3e9d,0x4988,0x0172,0xd882}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf529,0xbcf5,0x3918,0x30ee,0xa811}, new char[]{0xf55a,0x397b,0xa870,0xbc99,0x308b}).plainString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xee3f,0xfec4,0xb07c,0x1871,0xe0f5,0x4120,0xf21c}, new char[]{0xb00a,0xe09c,0xee4c,0xfead,0xf27e,0x414c,0x1814}).plainString();
    }
}
