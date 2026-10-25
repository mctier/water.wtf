package com.utilitymod.echo.features.impl.render;

import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventRender2DGui;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.settings.impl.FloatSetting;
import dev.koifih.client.render.Draw;
import dev.koifih.client.ui.Theme;
import dev.koifih.client.util.Colors;
import net.minecraft.client.gui.DrawContext;

/**
 * A clean crescent slash across the screen when you swing: a tapered arc that sweeps with the
 * swing and fades out (first person only).
 */
public class WeaponTrails extends Feature {
    private final FloatSetting size = new FloatSetting(LT.L1, 1f, 0.5f, 2f, 0.05f);
    private final FloatSetting opacity = new FloatSetting(LT.L2, 0.8f, 0.2f, 1f, 0.05f);

    private float lastProgress;
    private long swingStart;

    public WeaponTrails() {
        super(new FeatureInfo(new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2279,0x9622,0x11f9,0xeb8d,0x82f9,0x0b7c,0xa8ae,0xbc19,0x1c80,0xd12d,0x37d1,0x6545,0xb161}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536}).toString(), new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6d62,0x82c4,0x226b,0x11b0,0x9624,0x8af9,0x37db,0xb98c,0x508e,0xbc1e,0x1c9c,0x0b2c,0x76a7,0x4a4a,0xeb92,0x04be,0x1857,0x9e53,0xc5da,0x93b7,0x7ec5,0x5f7a,0xe733,0x655a,0xa424,0xfce6,0x3ff8,0x3313,0xf89f,0xd13b,0xb16a,0x2a88,0xd93a,0xf04d,0xa8eb}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87,0x76d4,0xf025,0x4a6a,0xc5bb,0x5f08,0xd959,0x50ae,0x2aff,0xa44c,0x3f9d,0xb9e2,0x3333,0x8a80,0x04d1,0x9e26,0x1877,0x93c4,0x6d15,0xe75a,0x7eab,0xf8f8}).toString(), Category.RENDER));
    }

    @EventSubscribe
    private void onRender(EventRender2DGui event) {
        if (isNull() || !mc.options.getPerspective().isFirstPerson() || mc.options.hudHidden) return;
        float p = mc.player.getHandSwingProgress(event.getTickDelta());
        if (p > 0 && (lastProgress == 0 || p < lastProgress)) swingStart = System.currentTimeMillis();
        lastProgress = p;
        float t = (System.currentTimeMillis() - swingStart) / 260f;
        if (t > 1f) return;

        DrawContext ctx = event.getContext();
        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
        float cx = sw * 0.62f, cy = sh * 0.72f;
        float r = Math.min(sw, sh) * 0.32f * size.getValue();
        // sweep from upper right to lower left; head leads, tail fades
        float head = Math.min(1f, t * 1.6f);
        float tail = Math.max(0f, t * 1.6f - 0.55f);
        int steps = 48;
        for (int i = 0; i <= steps; i++) {
            float u = i / (float) steps;
            if (u < tail || u > head) continue;
            double a = Math.toRadians(-70 + u * 150);
            float x = cx + (float) Math.cos(a) * r * 0.9f, y = cy - (float) Math.sin(a) * r * 0.45f;
            float along = (u - tail) / Math.max(0.001f, head - tail);       // 0 at the tail, 1 at the head
            float width = 1.5f + 5f * along * (1f - t);
            float a2 = opacity.getValue() * along * (1f - t);
            Draw.rect(ctx, Math.round(x - width / 2), Math.round(y - width / 2), Math.round(width), Math.round(width),
                    Math.round(width / 2), Colors.withAlpha(Colors.lerp(Theme.ACCENT, 0xFFFFFFFF, along * 0.5f), a2));
        }
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0fd5,0x62aa,0xa736,0xa938}, new char[]{0xa96b,0x0fbc,0x62d0,0xa753}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc7a9,0xfcb3,0xe6ec,0xf0a9,0xa42b,0xb87c,0x7d24}, new char[]{0xf0e6,0xb80c,0xc7c8,0x7d47,0xfcda,0xe698,0xa452}).toString();
    }
}
