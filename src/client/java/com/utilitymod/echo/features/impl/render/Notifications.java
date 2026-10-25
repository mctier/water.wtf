package com.utilitymod.echo.features.impl.render;

import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventRender2DGui;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.settings.impl.BoolSetting;
import com.utilitymod.echo.features.settings.impl.FloatSetting;
import com.utilitymod.echo.features.settings.impl.IntSetting;
import com.utilitymod.echo.features.settings.impl.ModeSetting;
import com.utilitymod.echo.hud.HudManager;
import com.utilitymod.echo.render.GlassRenderer;
import com.utilitymod.gui.UiSounds;
import dev.koifih.client.render.Draw;
import dev.koifih.client.render.Text;
import dev.koifih.client.ui.Theme;
import dev.koifih.client.util.Colors;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Toast notifications: one per module toggle (and anything calling {@link #push}). Toasts slide
 * in from the screen edge, stack smoothly, show a draining timer bar and use liquid glass when
 * it is on.
 */
public class Notifications extends Feature {
    public enum Type { ENABLED, DISABLED, INFO }

    private final ModeSetting position = new ModeSetting(LT.L1, LT.L2, LT.L2, LT.L3, LT.L4, LT.L5);
    private final IntSetting duration = new IntSetting(LT.L6, 1800, 500, 6000);
    private final IntSetting maxShown = new IntSetting(LT.L7, 5, 1, 10);
    private final FloatSetting scale = new FloatSetting(LT.L8, 1f, 0.6f, 1.6f, 0.05f);
    private final BoolSetting toggles = new BoolSetting(LT.L9, true);
    private final BoolSetting sound = new BoolSetting(LT.L10, true);
    private final BoolSetting messages = new BoolSetting(LT.L11, true);

    private static final int W = 130, H = 30, GAP = 4, RADIUS = 7;
    private static Notifications instance;
    /** Set while the config loads so restoring saved modules does not spam toasts. */
    public static volatile boolean muted;

    private static final class Toast {
        final String title, text;
        final Type type;
        final long born = System.currentTimeMillis();
        float slide, y = Float.NaN;
        boolean leaving;

        Toast(String title, String text, Type type) {
            this.title = title;
            this.text = text;
            this.type = type;
        }
    }

    private final List<Toast> toasts = new ArrayList<>();
    private long lastFrame = System.nanoTime();

    public Notifications() {
        super(new FeatureInfo(new com.utilitymod.echo.utils.EncryptedString(new char[]{0x226c,0x962a,0x11ff,0xeb8f,0x82cc,0x0b3f,0xa8a4,0xbc00,0x1c86,0xd134,0x37d8,0x6545,0xb166}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536}).toString(), new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2279,0x6545,0x11e5,0xa439,0x9e54,0xb987,0x04b0,0x5f28,0xa8a4,0x966e,0x82c2,0x93e4,0xfca7,0x8aa0,0xf04d,0x50c1,0xc5d5,0x1812,0x37ca,0x76a3,0xeb91,0xd934,0xb12f,0x3340,0xf89f,0x7225,0x7ecc,0x2a9b,0x1c82,0x478b,0x0b2c,0xd12e,0xbc1a,0x4a0f,0x3ff1,0x6d61,0xe735,0xcdfb}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87,0x76d4,0xf025,0x4a6a,0xc5bb,0x5f08,0xd959,0x50ae,0x2aff,0xa44c,0x3f9d,0xb9e2,0x3333,0x8a80,0x04d1,0x9e26,0x1877,0x93c4,0x6d15,0xe75a,0x7eab,0xf8f8,0x7249,0xcd9e,0x47ef}).toString(), Category.RENDER));
        instance = this;
    }

    /** Shows a toast if the Notifications module is on. Safe from any thread. */
    public static void push(String title, String text, Type type) {
        Notifications n = instance;
        if (n == null || !n.isEnabled()) return;
        mc.execute(() -> {
            n.toasts.add(new Toast(title, text, type));
            if (n.sound.getValue()) {
                UiSounds.play(type == Type.DISABLED ? UiSounds.Sound.TOGGLE_OFF : UiSounds.Sound.TOGGLE_ON);
            }
        });
    }

    /** True when module messages (finder results etc.) should be toasts instead of chat lines. */
    public static boolean showsMessages() {
        Notifications n = instance;
        return n != null && n.isEnabled() && n.messages.getValue();
    }

    /** Module toggle hook (Module / Anubis ClientModule setEnabled). */
    public static void onToggle(String name, boolean enabled) {
        Notifications n = instance;
        if (muted || n == null || !n.isEnabled() || !n.toggles.getValue() || LT.L12.equals(name)) return;
        push(name, enabled ? LT.L13 : LT.L14, enabled ? Type.ENABLED : Type.DISABLED);
    }

    private static float approach(float current, float target, float rate, float dt) {
        return target + (current - target) * (float) Math.exp(-rate * dt);
    }

    @EventSubscribe
    private void onRender(EventRender2DGui event) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        if (toasts.isEmpty() || mc.options.hudHidden) return;
        DrawContext ctx = event.getContext();
        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
        float sc = scale.getValue();
        float w = W * sc, h = H * sc;
        String pos = position.getValue().toString();
        boolean right = pos.endsWith(LT.L15), top = pos.startsWith(LT.L16);
        long life = duration.getValue();
        long nowMs = System.currentTimeMillis();

        // retire expired / overflow toasts
        int alive = 0;
        for (int i = toasts.size() - 1; i >= 0; i--) {
            Toast t = toasts.get(i);
            if (!t.leaving && (nowMs - t.born > life || ++alive > maxShown.getValue())) t.leaving = true;
        }

        // layout: newest nearest the corner
        float margin = 6;
        float slot = 0;
        List<float[]> rects = new ArrayList<>();
        for (int i = toasts.size() - 1; i >= 0; i--) {
            Toast t = toasts.get(i);
            t.slide = approach(t.slide, t.leaving ? 0f : 1f, t.leaving ? 14f : 16f, dt);
            float targetY = top ? margin + slot : sh - margin - h - slot;
            t.y = Float.isNaN(t.y) ? targetY : approach(t.y, targetY, 14f, dt);
            slot += (h + GAP * sc) * Math.max(t.slide, t.leaving ? t.slide : 1f);
            float ease = 1f - (1f - t.slide) * (1f - t.slide);
            float x = right ? sw - margin - w + (w + margin) * (1f - ease) : margin - (w + margin) * (1f - ease);
            rects.add(new float[] {x, t.y, ease, i});
        }
        toasts.removeIf(t -> t.leaving && t.slide < 0.01f);

        boolean glass = HudManager.liquidGlass && GlassRenderer.available();
        if (glass) {
            for (float[] r : rects) GlassRenderer.queue(new GlassRenderer.Panel(r[0], r[1], w, h, RADIUS * sc, r[2]));
            GlassRenderer.flush(ctx);
        }
        var m = ctx.getMatrices();
        for (float[] r : rects) {
            int idx = (int) r[3];
            if (idx >= toasts.size()) continue;
            Toast t = toasts.get(idx);
            float a = r[2];
            m.pushMatrix();
            m.translate(r[0], r[1]);
            m.scale(sc, sc);
            Draw.rect(ctx, 0, 0, W, H, RADIUS, Colors.withAlpha(glass ? 0xFF101216 : Theme.SIDEBAR, (glass ? 0.4f : 0.92f) * a));
            int color = switch (t.type) {
                case ENABLED -> Colors.lerp(Theme.ACCENT, 0xFFFFFFFF, 0.2f);
                case DISABLED -> 0xFFFF6B6B;
                case INFO -> 0xFF9FD3FF;
            };
            // module icon on a soft halo in the status colour, with a small status dot
            Draw.rect(ctx, 6, H / 2 - 8, 15, 15, 7, Colors.withAlpha(color, 0.2f * a));
            com.utilitymod.echo.render.Icons.drawItem(ctx, com.utilitymod.echo.render.Icons.moduleIcon(t.title), 8.5f, H / 2f - 5.5f, 10);
            Draw.rect(ctx, 17, H / 2 + 3, 4, 4, 2, Colors.withAlpha(color, a));
            Text.drawCentered(ctx, Text.fit(t.title, W - 32, 8f), 24, 10.5f, 8f, Colors.withAlpha(0xFFFFFFFF, a));
            Text.drawCentered(ctx, t.text, 24, 20.5f, 6.8f, Colors.withAlpha(color, a));
            // draining timer bar
            float left = 1f - Math.min(1f, (nowMs - t.born) / (float) life);
            Draw.rect(ctx, 6, H - 3, Math.max(1, Math.round((W - 12) * left)), 2, 1, Colors.withAlpha(color, 0.7f * a));
            m.popMatrix();
        }
    }

    @Override
    public void onDisable() {
        super.onDisable();
        toasts.clear();
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6259,0xa73b,0xf3c2,0x3716,0x8e40,0xd025,0xe873,0x1547}, new char[]{0x8e10,0xe81c,0x3765,0xd04c,0xf3b6,0xa752,0x1528,0x6237}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xda37,0x5acd,0xd0d4,0x7293,0x06a6,0xfbbf,0x430e,0x2d87,0xe3a1,0xe987,0xc04d,0x432f}, new char[]{0xc00f,0x06c9,0xda43,0x2df3,0xe9e8,0x5aa0,0xe381,0xfbed,0x4367,0x72f4,0xd0bc,0x435b}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2a8d,0x67bd,0x15ec,0x9b30,0x83de,0x348c,0x1650,0x14d8,0xa014}, new char[]{0x67e9,0x9b5f,0x159c,0xa034,0x2adf,0x1639,0x83b9,0x34e4,0x14ac}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xfd1f,0x0c19,0x661e,0xbaf1,0xa3cb,0x3548,0xc3e6,0xfba9,0x96c8,0x622d,0xa88a}, new char[]{0xfd5d,0x6671,0x6259,0xa8fe,0xba9e,0xfbc4,0xa3eb,0x3504,0x0c7c,0x96ae,0xc392}).plainString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x12fa,0x40f9,0x4c99,0x2f1a,0x1614,0x282a,0x29ad,0x1bde}, new char[]{0x287e,0x4cf6,0x29dd,0x40d9,0x1658,0x2f7f,0x1bb8,0x128e}).plainString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xde9c,0x5233,0xf45c,0x970c,0xe42f,0x0781,0xb1b8,0x0d0e,0x1a80,0xa597,0x97b6,0xa771,0x6edb}, new char[]{0xded8,0xb1cd,0xa5e5,0x1ae1,0xe45b,0xa718,0x97d9,0x0d60,0x07a1,0x521b,0x9761,0x6ea8,0xf475}).toString();
        static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x53df,0xfafb,0xaef2,0x3d80,0x2474,0x2815,0xbc6a,0x6bc2,0x5dfe}, new char[]{0x2858,0x2415,0x5d86,0xbc4a,0x6b91,0xae9a,0xfa94,0x53a8,0x3dee}).toString();
        static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8ff8,0xa4f0,0x5fe2,0x3033,0x6df2}, new char[]{0x8fab,0x5f81,0xa491,0x305f,0x6d97}).plainString();
        static final String L9 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x519d,0x8d0b,0x0c4f,0xcfc3,0xcf81,0xc1a4,0xc71f,0x961d,0xe684,0x4ab3,0x2547,0x6ef7,0x007b,0x93f9}, new char[]{0x51d0,0x9396,0x0c2b,0x6e82,0xc773,0x2522,0xc184,0x002f,0xcfee,0x4ad4,0xcfa4,0xe6e8,0x9678,0x8d78}).toString();
        static final String L10 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6ea7,0xf10a,0xfef6,0x46c7,0x5dcb}, new char[]{0x5d98,0x6ec8,0x46b2,0xfe98,0xf16e}).plainString();
        static final String L11 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x78f5,0x8d75,0x7de4,0xbd56,0xbb58,0x73a4,0x299e,0xf3e7,0x70bf,0xb6d7,0x7a47,0x196f,0xfd68,0x6a4c,0x625e}, new char[]{0x6213,0xbd39,0x8d11,0x73d1,0xbb34,0x7a22,0x7dc4,0xfd25,0xb6b2,0x191c,0x29ed,0x70de,0x7892,0xf382,0x6a3f}).toString();
        static final String L12 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5692,0x7604,0x3ec7,0x9536,0x610d,0x30b4,0x51ab,0xd06a,0x2345,0x022c,0x22f7,0xf34d,0x1e30}, new char[]{0x6143,0x2298,0x30c0,0xf324,0x024a,0x766d,0x2326,0xd00b,0x9542,0x1e59,0x56fd,0x3ea9,0x51d8}).toString();
        static final String L13 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7e8b,0x712a,0x3ef8,0x6578,0xbabf,0xed5a,0xc1d0}, new char[]{0x653d,0xc1be,0x714b,0xed38,0x7ee7,0xbada,0x3e9c}).toString();
        static final String L14 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe970,0xf367,0x4027,0xe769,0xb671,0xd6e5,0x70f5,0x5810}, new char[]{0xf323,0xe700,0x5863,0xe911,0x4045,0xd689,0xb614,0x7091}).toString();
        static final String L15 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2467,0xa26a,0xfafc,0x2588,0xa7f7}, new char[]{0xfaae,0x240e,0xa790,0xa202,0x25fc}).plainString();
        static final String L16 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe767,0x84d3,0x99e3}, new char[]{0xe733,0x998c,0x84a3,0xd555}).plainString();
    }
}
