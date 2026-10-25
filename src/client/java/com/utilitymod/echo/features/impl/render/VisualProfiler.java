package com.utilitymod.echo.features.impl.render;

import com.utilitymod.echo.event.Profiler;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.settings.impl.IntSetting;
import com.utilitymod.echo.hud.HudElement;
import com.utilitymod.echo.hud.HudManager;
import dev.koifih.client.render.Draw;
import dev.koifih.client.render.Text;
import dev.koifih.client.ui.Theme;
import dev.koifih.client.util.Colors;
import net.minecraft.client.gui.DrawContext;

import java.util.List;
import java.util.Locale;

/** Shows which modules cost the most CPU per frame, so you know what to turn off. */
public class VisualProfiler extends Feature {
    private final IntSetting rows = new IntSetting(LT.L1, 8, 3, 20);

    public VisualProfiler() {
        super(new FeatureInfo(new com.utilitymod.echo.utils.EncryptedString(new char[]{0x82fd,0x76a6,0x0b7c,0xeb88,0x962c,0xa8a2,0x37df,0x1c80,0xfce2,0xbc1c,0xb163,0xd12c,0x655a,0x226b,0x11f6}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87,0x76d4}).toString(), LT.L2, Category.RENDER));
        HudManager.register(new Element());
    }

    @Override
    public void onEnable() {
        super.onEnable();
        Profiler.enabled = true;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        Profiler.enabled = false;
    }

    private final class Element extends HudElement {
        Element() { super(LT.L3, LT.L4, 0.995f, 0.6f); }

        private List<Profiler.Entry> list() {
            List<Profiler.Entry> all = Profiler.snapshot();
            return all.subList(0, Math.min(rows.getValue(), all.size()));
        }

        @Override public boolean active() { return isEnabled(); }
        @Override public float width() { return 140; }
        @Override public float height() { return 18 + Math.max(1, list().size()) * 11; }
        @Override public float radius() { return 6; }

        @Override
        public void render(DrawContext ctx, boolean preview) {
            int fps = Math.max(1, mc.getCurrentFps());
            Text.drawCentered(ctx, LT.L5, 6, 8, 7.5f, Theme.TEXT);
            String hint = LT.L6;
            Text.drawCentered(ctx, hint, width() - 6 - Text.width(hint, 6f), 8, 6f, Theme.MUTED);
            List<Profiler.Entry> list = list();
            if (list.isEmpty()) {
                Text.drawCentered(ctx, LT.L7, 6, 22, 6.8f, Theme.MUTED);
                return;
            }
            double top = Math.max(0.001, list.get(0).msPerSecond() / fps);
            float y = 20;
            for (Profiler.Entry e : list) {
                double perFrame = e.msPerSecond() / fps;
                float frac = (float) Math.min(1, perFrame / top);
                int col = perFrame > 1.0 ? 0xFFF87171 : perFrame > 0.3 ? 0xFFFBBF24 : 0xFF4ADE80;
                Draw.rect(ctx, 6, Math.round(y + 2), Math.max(1, Math.round(50 * frac)), 3, 1, Colors.withAlpha(col, 0.8f));
                Text.drawCentered(ctx, Text.fit(e.name(), 52, 6.8f), 60, y + 3.5f, 6.8f, Theme.TEXT);
                String v = String.format(Locale.ROOT, LT.L8, perFrame);
                Text.drawCentered(ctx, v, width() - 6 - Text.width(v, 6.8f), y + 3.5f, 6.8f, col);
                y += 11;
            }
        }
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x09a6,0xf454,0xcbbe,0xb683}, new char[]{0xb6d1,0xf43b,0xcbc9,0x09d5}).toString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb29f,0x3660,0x9f95,0x5228,0xd2df,0x654b,0xe339,0xd55f,0x4e63,0x0d97,0xf5a3,0x8b48,0x5a8d,0xf0d2,0xf1f1,0x1913,0x0844,0x7afd,0x0020,0xb216,0x85b7,0xeee8,0xd6a9,0x0901,0xf8cb,0xc6eb,0x2908,0xcd1e,0xdf9e,0xc4a9,0xbcd3,0x6c0f,0xb882,0x9b68,0x63ba,0x10a8,0xe11e,0xf747,0xcca7,0xa599,0xa76f,0x9c35,0xad41,0x4d25,0x5b6c,0xa5ee,0x0517,0x8b7e,0xfc6a,0x4753,0x9a25,0x94a9,0x8ced,0x69b7,0x6f6e}, new char[]{0x4704,0xa707,0x8b21,0x94ca,0x5ae5,0x1933,0x6f03,0xad2e,0x4d41,0x3615,0x9b04,0x10cd,0x63c9,0xf767,0x524b,0x5b03,0x297b,0xc69f,0x0921,0x9c4c,0xdff1,0x85c2,0x9a05,0x0802,0xa5be,0x4e30,0xfc4a,0xb2b7,0xf8bb,0x8b12,0xd2be,0x0043,0x9ff0,0xf0f2,0x0dfe,0xf5d7,0xb236,0x6522,0xa5f7,0xd689,0xee9c,0x69df,0x6c6a,0xb8a2,0xf1b9,0xe36c,0xcd5a,0xbcf3,0xc4cc,0x0573,0xccce,0xe16a,0x7a92,0xd52d,0x8cc4}).toString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4977,0xe692,0x42dd,0x52f0,0xcf12,0x43a7,0x1e67,0xadaa}, new char[]{0xcf62,0xe6e0,0x529f,0x1e01,0xadc3,0x42b1,0x43c2,0x4905}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x48dd,0x4086,0xab68,0xc03f,0x435a,0x58b5,0x4a9c,0xb562,0x7d36,0xa4f0,0x693e,0x7d1e,0xa34a,0x334f,0x0876}, new char[]{0xa31c,0xab01,0x40f5,0x0803,0x695f,0x4af0,0x48fd,0x430a,0x58c7,0xb50d,0x3329,0x7d5f,0xc053,0xa495,0x7d6c}).toString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9fcc,0x591c,0x78fb,0xa99e,0x3b93,0xb347,0x147b,0x6748}, new char[]{0x78ab,0x1409,0xb328,0x672e,0x9fa5,0x3bff,0x5979,0xa9ec}).toString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8c8d,0xd02b,0xd2b3,0xd14e,0x92b4,0x5e45,0x2fbc,0xbd64,0x9433,0xdb09}, new char[]{0xd046,0xd13d,0x2f9c,0x8ca2,0x9413,0x5e23,0x92c6,0xd2d2,0xdb64,0xbd01}).plainString();
        static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa808,0x2128,0x05db,0x66c0,0x909b,0x4081,0xc322,0xcaa1,0x0d7f,0xc7c3,0xcb0a,0x2a45}, new char[]{0xcb67,0xa86d,0x0d1e,0x90e8,0x66b5,0x215a,0xcac8,0x05b5,0x40e6,0xc7ed,0xc30c,0x2a6b}).plainString();
        static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6d75,0x922f,0xb842,0x64e3}, new char[]{0x64c6,0x6d5b,0x921d,0xb824}).plainString();
    }
}
