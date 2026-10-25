package com.utilitymod.echo.features.impl.render;

import com.utilitymod.echo.EchoEvents;
import com.utilitymod.echo.Render3D;
import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventRender2DGui;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.settings.impl.BoolSetting;
import com.utilitymod.echo.features.settings.impl.IntSetting;
import dev.koifih.client.render.Draw;
import dev.koifih.client.render.Text;
import dev.koifih.client.util.Colors;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector4f;

/** Floating health above living entities: a colour-coded number and a thin bar over each head. */
public class HealthIndicators extends Feature {
    private final BoolSetting players = new BoolSetting(LT.L1, true);
    private final BoolSetting mobs = new BoolSetting(LT.L2, false);
    private final BoolSetting self = new BoolSetting(LT.L3, false);
    private final IntSetting range = new IntSetting(LT.L4, 48, 4, 96, LT.L5);
    private final BoolSetting showBar = new BoolSetting(LT.L6, true);
    private final BoolSetting showNumber = new BoolSetting(LT.L7, true);

    public HealthIndicators() {
        super(new FeatureInfo(new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4a19,0x76bb,0xf057,0xb167,0x2279,0xd132,0x0b7c,0xeb82,0x82e4,0x1c9c,0x9627,0x11f9,0xa8ae,0x37ca,0xfcf3,0x6557,0xbc05}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87,0x76d4,0xf025,0x4a6a}).toString(), new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3b36,0xf88e,0x93e4,0xc5ce,0x4a04,0x3ffc,0x11f5,0x76bc,0x962b,0x5f65,0xd93b,0x82ca,0x3357,0x2602,0xa46c,0xd2ab,0x1587,0x50cb,0x1805,0x2277,0xb98c,0x9e47,0xd13c,0xcdbe,0xf005,0x8aa0,0x479f,0xbc08,0x37ca,0xef5b,0xb166,0x9bf6,0x655a,0xc150,0xa8a7,0xac46,0x1cd2,0x0cda,0x8661,0x7ec4,0xeb80,0x006a,0xe738,0x04b3,0x66fd,0x722c,0x6d74,0xb506,0x2a8d,0x58ec,0xa1b2,0x0b32,0xfcf3}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87,0x76d4,0xf025,0x4a6a,0xc5bb,0x5f08,0xd959,0x50ae,0x2aff,0xa44c,0x3f9d,0xb9e2,0x3333,0x8a80,0x04d1,0x9e26,0x1877,0x93c4,0x6d15,0xe75a,0x7eab,0xf8f8,0x7249,0xcd9e,0x47ef,0xc13c,0x588d,0xd2d2,0xac23,0x2670,0xa1c1,0x3b16,0xb567,0x0cb4,0x8605,0x004a,0x9b9b,0x15e8,0xef39,0x668e}).toString(), Category.RENDER));
    }

    @EventSubscribe
    private void onRender(EventRender2DGui event) {
        Render3D frame = EchoEvents.lastFrame();
        if (isNull() || frame == null || mc.options.hudHidden) return;
        DrawContext ctx = event.getContext();
        float pt = mc.getRenderTickCounter().getTickProgress(false);
        double r2 = (double) range.getValue() * range.getValue();

        for (var e : mc.world.getEntities()) {
            if (!(e instanceof LivingEntity le) || !le.isAlive()) continue;
            boolean isPlayer = le instanceof PlayerEntity;
            boolean isMob = le instanceof MobEntity;
            if (le == mc.player) { if (!self.getValue()) continue; }
            else if (isPlayer) { if (!players.getValue()) continue; }
            else if (isMob) { if (!mobs.getValue()) continue; }
            else continue;
            if (mc.player.squaredDistanceTo(le) > r2) continue;

            Vec3d p = le.getLerpedPos(pt);
            Vector4f clip = frame.toClip(p.x, p.y + le.getHeight() + 0.45, p.z);
            if (clip.w() <= 0.05f) continue;
            float[] g = Render3D.toGui(clip);

            float hp = le.getHealth() + le.getAbsorptionAmount();
            float max = Math.max(1f, le.getMaxHealth() + le.getAbsorptionAmount());
            float pct = Math.max(0f, Math.min(1f, hp / max));
            int col = healthColor(pct);

            float cx = g[0], cy = g[1];
            if (showBar.getValue()) {
                int w = 26, h = 3;
                int x = Math.round(cx - w / 2f), y = Math.round(cy);
                Draw.rect(ctx, x, y, w, h, 1, 0xB0101216);
                Draw.rect(ctx, x, y, Math.max(1, Math.round(w * pct)), h, 1, col);
            }
            if (showNumber.getValue()) {
                String s = String.valueOf(Math.round(hp));
                Text.drawCenteredX(ctx, s, cx, cy - (showBar.getValue() ? 10f : 6f), 7f, col);
            }
        }
    }

    private int healthColor(float pct) {
        if (pct > 0.5f) {
            float t = (pct - 0.5f) / 0.5f;          // yellow -> green
            int rr = (int) (0xFF * (1 - t)) + (int) (0x7F * t);
            return 0xFF000000 | (rr << 16) | (0xD9 << 8) | 0x60;
        }
        float t = pct / 0.5f;                        // red -> yellow
        int gg = (int) (0xC5 * t);
        return 0xFF000000 | (0xF0 << 16) | (gg << 8) | 0x50;
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x65bb,0x834a,0x21f6,0xf4c0,0x0119,0xb26f,0x56a7}, new char[]{0x831a,0x219a,0xf4a1,0x65c2,0xb20a,0x016b,0x56d4}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x12c9,0x0b02,0xd900,0xe49a}, new char[]{0x0b4f,0xd96f,0x12ab,0xe4e9}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa971,0x3e7d,0x3f0b,0x533b}, new char[]{0x5368,0x3e18,0x3f67,0xa917}).toString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2698,0xe6a9,0x3245,0x84a0,0xd8ab}, new char[]{0xd8f9,0x3224,0x26f6,0x84c7,0xe6cc}).toString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6b71,0x7095,0xb56c,0x9326,0x73a7,0x00d0,0x7ab2}, new char[]{0x9306,0x7ad0,0x70f9,0xb503,0x00b3,0x73cc,0x6b02}).plainString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4ac2,0x539a,0xcaf0,0x3515,0x765e,0x41ed,0x3d0c,0x882b}, new char[]{0x3d5f,0x357d,0xca9f,0x7629,0x880b,0x53d8,0x418c,0x4ab0}).toString();
        static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5423,0x1173,0x5841,0xcc82,0xd036,0x1736,0xd56c,0xc145,0xc7d6,0xc6d8,0x4f98}, new char[]{0xccd1,0x111b,0x4ff7,0xc6af,0x1716,0x580f,0xd043,0xd501,0xc7b4,0x5446,0xc137}).toString();
    }
}
