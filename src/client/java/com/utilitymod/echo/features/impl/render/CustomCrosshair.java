package com.utilitymod.echo.features.impl.render;

import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventRender2DGui;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.settings.impl.BoolSetting;
import com.utilitymod.echo.features.settings.impl.ColorSetting;
import com.utilitymod.echo.features.settings.impl.FloatSetting;
import com.utilitymod.echo.render3.api.FramebufferTarget;
import com.utilitymod.inject.Hooks;
import net.minecraft.client.gui.DrawContext;

/** Configurable crosshair (replaces vanilla's): gap, length, thickness, dot, outline, dynamic spread. */
public class CustomCrosshair extends Feature {
    private final FloatSetting gap = new FloatSetting(LT.L1, 2f, 0f, 10f, 0.5f);
    private final FloatSetting length = new FloatSetting(LT.L2, 4f, 1f, 15f, 0.5f);
    private final FloatSetting thickness = new FloatSetting(LT.L3, 1f, 0.5f, 4f, 0.5f);
    private final BoolSetting dot = new BoolSetting(LT.L4, false);
    private final BoolSetting outline = new BoolSetting(LT.L5, true);
    private final BoolSetting dynamic = new BoolSetting(LT.L6, true);
    private final BoolSetting attackIndicator = new BoolSetting(LT.L7, true);
    private final ColorSetting color = new ColorSetting(LT.L8, 0xFFFFFFFF);

    private float spread;

    public CustomCrosshair() {
        super(new FeatureInfo(new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0b3d,0x1c80,0x82c4,0x37cd,0xbc1a,0xa8b9,0xb167,0x2277,0xd139}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2}).toString(), new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1c80,0x962c,0xbc1d,0x11e3,0x76bd,0x0b7c,0xeb92,0x37d1,0xf057,0xd139,0x82ce,0x226b,0xfce6,0xa8be,0xb162,0x655e}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87,0x76d4,0xf025}).toString(), Category.RENDER));
        Hooks.hideVanillaCrosshair = this::isEnabled;
    }

    @EventSubscribe
    private void onRender(EventRender2DGui event) {
        if (isNull() || !mc.options.getPerspective().isFirstPerson() || mc.options.hudHidden) return;
        DrawContext ctx = event.getContext();
        float cx = ctx.getScaledWindowWidth() / 2f, cy = ctx.getScaledWindowHeight() / 2f;
        double speed = mc.player.getVelocity().horizontalLength();
        float target = dynamic.getValue() ? (float) Math.min(6, speed * 18 + (mc.player.isOnGround() ? 0 : 2)) : 0f;
        spread += (target - spread) * 0.25f;
        float g = gap.getValue() + spread, l = length.getValue(), t = thickness.getValue();
        int c = color.getARGB();
        if (outline.getValue()) {
            arms(ctx, cx, cy, g - 0.5f, l + 1f, t + 1f, 0xC0000000);
        }
        arms(ctx, cx, cy, g, l, t, c);
        if (dot.getValue()) {
            if (outline.getValue()) FramebufferTarget.rect(ctx, cx - t / 2 - 0.5f, cy - t / 2 - 0.5f, t + 1, t + 1, 0xC0000000);
            FramebufferTarget.rect(ctx, cx - t / 2, cy - t / 2, t, t, c);
        }
        if (attackIndicator.getValue()) {
            float progress = mc.player.getAttackCooldownProgress(0f);
            if (progress < 1f) {
                int segs = 24;
                float r = g + l + 3;
                for (int i = 0; i < Math.round(segs * progress); i++) {
                    double a0 = -Math.PI / 2 + i * 2 * Math.PI / segs, a1 = -Math.PI / 2 + (i + 1) * 2 * Math.PI / segs;
                    FramebufferTarget.line(ctx, cx + (float) Math.cos(a0) * r, cy + (float) Math.sin(a0) * r,
                            cx + (float) Math.cos(a1) * r, cy + (float) Math.sin(a1) * r, (c & 0xFFFFFF) | 0xA0000000);
                }
            }
        }
    }

    private static void arms(DrawContext ctx, float cx, float cy, float g, float l, float t, int c) {
        FramebufferTarget.rect(ctx, cx - t / 2, cy - g - l, t, l, c);
        FramebufferTarget.rect(ctx, cx - t / 2, cy + g, t, l, c);
        FramebufferTarget.rect(ctx, cx - g - l, cy - t / 2, l, t, c);
        FramebufferTarget.rect(ctx, cx + g, cy - t / 2, l, t, c);
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe71e,0xda71,0x2214}, new char[]{0xda36,0xe77f,0x2264,0xa690}).toString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa26d,0x6dba,0x6c5c,0x2a22,0x928e,0x7e02}, new char[]{0xa221,0x92eb,0x2a4c,0x6ddd,0x7e76,0x6c34}).toString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x06a9,0x0921,0x5f2a,0xf442,0xcd21,0x3c22,0x5150,0x7b39,0xc414}, new char[]{0xf416,0x7b51,0x3c4b,0xcd42,0x094a,0x06c7,0x5f4f,0xc467,0x5123}).toString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0f20,0x826c,0x11f8}, new char[]{0x8228,0x1197,0x0f54,0x94db}).toString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2c60,0x00b8,0xb40f,0x0660,0xea0e,0xbefe,0x53ba}, new char[]{0xb440,0xea7b,0x00cc,0x53d6,0x0609,0xbe90,0x2c05}).plainString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x025f,0x6eae,0x1400,0xc53f,0x46cb,0x518d,0x584b}, new char[]{0x6eea,0x0226,0x46a5,0x1461,0xc552,0x5822,0x51ee}).toString();
        static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa8cf,0x717e,0x7eca,0x3012,0xd0bf,0x4d49,0xf03c,0x8b98,0x5940,0xb90b,0x68bc,0xb8d3,0x1660}, new char[]{0x8bdb,0x307d,0x7ea5,0xf050,0xa8ab,0x7111,0xd0c8,0x592e,0xb92b,0x68ee,0x4d20,0xb8bd,0x1607}).toString();
        static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4352,0x507f,0xf7ff,0x0fac,0x092e}, new char[]{0x0fef,0xf790,0x5013,0x0941,0x4320}).plainString();
    }
}
