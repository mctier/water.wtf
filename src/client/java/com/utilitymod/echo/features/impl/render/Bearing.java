package com.utilitymod.echo.features.impl.render;

import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.settings.impl.BoolSetting;
import com.utilitymod.echo.hud.HudElement;
import com.utilitymod.echo.hud.HudManager;
import com.utilitymod.gui.Waypoints;
import dev.koifih.client.render.Draw;
import dev.koifih.client.render.Text;
import dev.koifih.client.ui.Theme;
import dev.koifih.client.util.Colors;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;

/** A heading strip (N / E / S / W, degree ticks) with your waypoints as markers. */
public class Bearing extends Feature {
    private final BoolSetting showWaypoints = new BoolSetting(LT.L1, true);
    private static final float FOV = 180f; // degrees visible across the strip

    public Bearing() {
        super(new FeatureInfo(new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0b2f,0xd139,0x37df,0xb17c,0xbc19,0x2275,0xa8a4}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c}).toString(), LT.L2, Category.RENDER));
        HudManager.register(new Element());
    }

    private final class Element extends HudElement {
        Element() { super(LT.L3, LT.L4, 0.5f, 0.01f); }

        @Override public boolean active() { return isEnabled() && mc.player != null; }
        @Override public float width() { return 200; }
        @Override public float height() { return 22; }
        @Override public float radius() { return 8; }

        private float xFor(float bearing, float yaw) {
            float d = MathHelper.wrapDegrees(bearing - yaw);
            return width() / 2f + d / (FOV / 2f) * (width() / 2f - 8);
        }

        @Override
        public void render(DrawContext ctx, boolean preview) {
            if (mc.player == null) return;
            float yaw = MathHelper.wrapDegrees(mc.player.getYaw());
            ctx.enableScissor(0, 0, Math.round(width()), Math.round(height()));
            for (int deg = 0; deg < 360; deg += 15) {
                float x = xFor(deg, yaw);
                if (x < 2 || x > width() - 2) continue;
                // distance from centre fades the strip edges
                float edge = 1f - Math.abs(x - width() / 2f) / (width() / 2f);
                String label = switch (deg) {
                    case 180 -> LT.L5;
                    case 270 -> LT.L6;
                    case 0 -> LT.L7;
                    case 90 -> LT.L8;
                    default -> null;
                };
                if (label != null) {
                    Text.drawCenteredX(ctx, label, x, 8, 8f, Colors.withAlpha(label.equals(LT.L5) ? Theme.ACCENT : Theme.TEXT, edge));
                } else {
                    int th = deg % 45 == 0 ? 5 : 3;
                    Draw.rect(ctx, Math.round(x), 6 - th / 2, 1, th, 0, Colors.withAlpha(Theme.MUTED, edge));
                }
            }
            if (showWaypoints.getValue()) {
                String dim = Waypoints.dimension();
                for (Waypoints.Waypoint w : Waypoints.LIST) {
                    if (!w.dimension().equals(dim)) continue;
                    double dx = w.x() + 0.5 - mc.player.getX(), dz = w.z() + 0.5 - mc.player.getZ();
                    float bearing = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
                    float x = xFor(bearing, yaw);
                    if (x < 4 || x > width() - 4) continue;
                    Draw.rect(ctx, Math.round(x - 2), 15, 4, 4, 2, w.color());
                }
            }
            // centre marker + heading
            Draw.rect(ctx, Math.round(width() / 2f) - 1, 0, 2, 3, 1, Theme.ACCENT);
            String heading = Math.round((yaw + 180 + 360) % 360) + LT.L9;
            Text.drawCenteredX(ctx, heading, width() / 2f, 17.5f, 6f, Colors.withAlpha(Theme.MUTED, 0.9f));
            ctx.disableScissor();
        }
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xaed6,0x95b3,0x2b68,0x27f6,0x5c18,0x106d,0x5ee8,0xb4cf,0x6c51,0x80e9,0xbb90,0xe296,0x3e80,0x7e49}, new char[]{0x3ed3,0x95db,0xaeb9,0xe2e1,0x6c71,0x27a1,0xb4ae,0x2b11,0xbbe0,0x7e26,0x8080,0x1003,0x5e9c,0x5c6b}).toString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf768,0x5fc6,0x273c,0x162f,0xfcd9,0x6d1a,0x7930,0x6905,0x8cd5,0x0ed7,0x2021,0x8647,0xbda2,0xd83a,0x49d4,0x19ea,0x63ff,0x6ebf,0xd077,0x8eff,0x5a20,0x5d84,0x6059,0x67cb,0x0e70,0x1ac1,0x6c3e,0x35ce,0x9c27,0x0f19,0xda4f,0x5109,0xb525,0x333c,0xc3cb,0xde8a,0x3a8a,0xbe64,0x06fe,0x8527,0xd518,0x6115,0xfbec,0xf944,0xd16f,0xb539,0x02f4,0xd3fc,0xad8e,0x141f,0x6391,0x9936,0xe6ee,0x12b1,0x8ed3,0x0577,0x5d84,0x7de8,0x8c03,0x797d,0xd17e,0xfdab,0xfb10,0x8cb3}, new char[]{0xe6a6,0xd399,0x6c5f,0xfb74,0x2755,0x8629,0xfdcc,0x6135,0x5a53,0xd56c,0xdef8,0x6ed6,0x8e8f,0x8ef3,0x8cc4,0x63f8,0x8553,0xd01f,0x6d3a,0x12c6,0xbdc3,0x0eae,0xfca9,0x696a,0x9c4e,0xf706,0x8ca1,0x2001,0x5fab,0x639e,0x35bc,0x7d83,0x7955,0xc3b9,0xda3c,0x795d,0x5dac,0x67bb,0x1643,0x6038,0x3ae9,0xd85f,0xbe44,0xb54c,0x49a0,0x5da4,0xade7,0x0690,0xd15e,0xb54d,0x1477,0x0512,0x02d4,0x1a89,0x9963,0x514d,0xfbcc,0x3359,0xd10b,0x8c6a,0x0e04,0x0f76,0x1998,0xf96d}).toString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb29b,0x39ed,0x65aa,0xe618,0x26af,0xde65,0x449f}, new char[]{0x44fc,0x26c0,0xe675,0xb2eb,0x65cb,0x399e,0xde16}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc63e,0xd0f8,0x377a,0x3f58,0x7bee,0xfbe5,0xab1e}, new char[]{0xc67d,0xfb8a,0x3717,0xd088,0x7b8f,0xab6d,0x3f2b}).toString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x612c}, new char[]{0x6162,0x158b,0x2edf,0x90bc}).plainString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe735}, new char[]{0xe770,0xbc20,0x90a4,0x161e}).plainString();
        static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa67b}, new char[]{0xa628,0x8d4f,0x1347,0xe94d}).plainString();
        static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe680}, new char[]{0xe6d7,0x24e4,0xeee9,0x05e5}).plainString();
        static final String L9 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4d20}, new char[]{0x4d90,0x649d,0x03d3,0x5312}).plainString();
    }
}
