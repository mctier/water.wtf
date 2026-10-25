package com.utilitymod.echo.features.impl.render;

import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventRender2DGui;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.settings.impl.BoolSetting;
import com.utilitymod.echo.hud.HudManager;
import com.utilitymod.echo.render.GlassRenderer;
import dev.koifih.client.render.Draw;
import dev.koifih.client.ui.Theme;
import dev.koifih.client.util.Colors;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;

/** Rounded glass hotbar with a smoothly sliding selection, replacing the vanilla one. */
public class GlassHotbar extends Feature {
    private final BoolSetting glass = new BoolSetting(LT.L1, true);
    private final BoolSetting offhand = new BoolSetting(LT.L2, true);
    private float slotX = -1;
    private long lastFrame = System.nanoTime();

    public GlassHotbar() {
        super(new FeatureInfo(new com.utilitymod.echo.utils.EncryptedString(new char[]{0xeb93,0x0b14,0x1c86,0x9621,0x2279,0xd13d,0x11f1,0x82c2,0xa8a7,0x37cd,0xb12f,0xbc1a}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1}).toString(), new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3fea,0x11f7,0xe733,0xd13b,0x93b7,0x58e8,0xa1b5,0x0b38,0x7ecf,0x1c96,0x04f1,0xbc06,0x6d79,0x4a02,0xa8eb,0x3b7f,0xa46c,0x8ae8,0xb508,0x9663,0xf891,0xb98b,0x76a7,0x0cda,0xac46,0x5f7c,0x37cb,0xf005,0x82c8,0x2a8d,0x50cf,0x226a,0x7227,0xcdf9,0x6557,0xc14f,0x2613,0x47cf,0xb161,0xd2be,0xfcf4,0x1857,0xd93b,0xc5d4,0x3347,0xeb8d,0x9e47}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87,0x76d4,0xf025,0x4a6a,0xc5bb,0x5f08,0xd959,0x50ae,0x2aff,0xa44c,0x3f9d,0xb9e2,0x3333,0x8a80,0x04d1,0x9e26,0x1877,0x93c4,0x6d15,0xe75a,0x7eab,0xf8f8,0x7249,0xcd9e,0x47ef,0xc13c,0x588d,0xd2d2,0xac23,0x2670,0xa1c1,0x3b16,0xb567,0x0cb4}).toString(), Category.RENDER));
        com.utilitymod.inject.Hooks.hideVanillaHotbar = () -> isEnabled() && mc.player != null && !mc.player.isSpectator();
    }

    @EventSubscribe
    private void onRender(EventRender2DGui event) {
        if (isNull() || mc.player.isSpectator() || mc.options.hudHidden) return;
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;

        DrawContext ctx = event.getContext();
        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
        int slot = 20, gap = 2, pad = 3;
        int w = 9 * slot + 8 * gap + 2 * pad, h = slot + 2 * pad;
        int x = (sw - w) / 2, y = sh - h - 2;
        int selected = mc.player.getInventory().getSelectedSlot();
        float target = x + pad + selected * (slot + gap);
        slotX = slotX < 0 ? target : target + (slotX - target) * (float) Math.exp(-18f * dt);

        ItemStack off = mc.player.getOffHandStack();
        boolean showOff = offhand.getValue() && !off.isEmpty();
        int ox = x - h - 4;
        boolean useGlass = glass.getValue() && (HudManager.liquidGlass || glass.getValue()) && GlassRenderer.available();
        if (useGlass) {
            GlassRenderer.queue(new GlassRenderer.Panel(x, y, w, h, 8, 1f));
            if (showOff) GlassRenderer.queue(new GlassRenderer.Panel(ox, y, h, h, 8, 1f));
            GlassRenderer.flush(ctx);
        }
        int bg = Colors.withAlpha(useGlass ? 0xFF101216 : Theme.SIDEBAR, useGlass ? 0.35f : 0.85f);
        Draw.rect(ctx, x, y, w, h, 8, bg);
        if (showOff) Draw.rect(ctx, ox, y, h, h, 8, bg);
        // sliding selection
        Draw.rect(ctx, Math.round(slotX) - 1, y + pad - 1, slot + 2, slot + 2, 6, Colors.withAlpha(Theme.ACCENT, 0.35f));
        Draw.rect(ctx, Math.round(slotX) - 1, y + h - 3, slot + 2, 2, 1, Theme.ACCENT);

        for (int i = 0; i < 9; i++) {
            ItemStack st = mc.player.getInventory().getStack(i);
            int sx = x + pad + i * (slot + gap) + 2, sy = y + pad + 2;
            if (st.isEmpty()) continue;
            ctx.drawItem(st, sx, sy);
            ctx.drawStackOverlay(mc.textRenderer, st, sx, sy);
        }
        if (showOff) {
            ctx.drawItem(off, ox + (h - 16) / 2, y + (h - 16) / 2);
            ctx.drawStackOverlay(mc.textRenderer, off, ox + (h - 16) / 2, y + (h - 16) / 2);
        }
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x120b,0x549b,0xff27,0xa53f,0x35f3,0x7787,0x6899,0xde7f,0xb305,0xfd85,0x5bd6,0x2f52}, new char[]{0x5b9a,0xa556,0xff56,0x54ee,0x359a,0xde1b,0xfda5,0x77c0,0x2f3e,0x68f8,0xb376,0x1278}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb8eb,0x4c14,0x10f0,0xd708,0x7950,0x8571,0x279f,0xe50c,0x3e64,0xebeb,0x8b4b,0xb98c}, new char[]{0x10a3,0x3e0c,0xeb84,0x4c63,0x8551,0xe543,0x8b2d,0xb88d,0x7938,0xd769,0xb9e2,0x27fb}).toString();
    }
}
