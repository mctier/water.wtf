package com.utilitymod.echo.features.impl.render;

import com.utilitymod.echo.EchoEvents;
import com.utilitymod.echo.Render3D;
import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventOnAttackEntity;
import com.utilitymod.echo.event.impl.EventRender2DGui;
import com.utilitymod.echo.event.impl.EventTick;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.settings.impl.BoolSetting;
import com.utilitymod.echo.features.settings.impl.ColorSetting;
import com.utilitymod.echo.render3.api.FramebufferTarget;
import dev.koifih.client.render.Text;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Crosshair hit marker on attack, plus floating damage numbers above hit entities. */
public class HitMarkers extends Feature {
    private final BoolSetting marker = new BoolSetting(LT.L1, true);
    private final BoolSetting damage = new BoolSetting(LT.L2, true);
    private final ColorSetting color = new ColorSetting(LT.L3, 0xFFFF5555);

    private long markerAt;
    private record Number(Vec3d pos, String text, long start, int color) {}
    private final List<Number> numbers = new ArrayList<>();
    private final Map<Integer, Float> lastHealth = new HashMap<>();

    public HitMarkers() {
        super(new FeatureInfo(new com.utilitymod.echo.utils.EncryptedString(new char[]{0x11e3,0xa8a2,0xbc49,0x226c,0x1c97,0x82c6,0x0b2e,0xd132,0xb16e,0x9631,0x37f3}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190}).toString(), new com.utilitymod.echo.utils.EncryptedString(new char[]{0x226c,0x6558,0x11b0,0xa42b,0x9e41,0xb986,0x04b0,0x5f69,0xa8a2,0x9631,0x82c6,0x93e4,0xfce3,0x8aed,0xf043,0x50c7,0xc5d4,0x1812,0x37d3,0x76f4,0xeb80,0xd92d,0xb16e,0x3352,0xf89a,0x722c,0x7ec6,0x2a91,0x1c97,0x479c,0x0b2e,0xd132,0xbc49,0x4a06,0x3fbd,0x6d7b,0xe72f,0xcdec}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87,0x76d4,0xf025,0x4a6a,0xc5bb,0x5f08,0xd959,0x50ae,0x2aff,0xa44c,0x3f9d,0xb9e2,0x3333,0x8a80,0x04d1,0x9e26,0x1877,0x93c4,0x6d15,0xe75a,0x7eab,0xf8f8,0x7249,0xcd9e,0x47ef}).toString(), Category.RENDER));
    }

    @EventSubscribe
    private void onAttack(EventOnAttackEntity.Post event) {
        markerAt = System.currentTimeMillis();
    }

    /** Damage = observed health drop of nearby living entities (works for any damage source). */
    @EventSubscribe
    private void onTick(EventTick event) {
        if (isNull() || !damage.getValue()) return;
        for (var e : mc.world.getEntities()) {
            if (!(e instanceof LivingEntity l) || e == mc.player || mc.player.squaredDistanceTo(e) > 32 * 32) continue;
            float hp = l.getHealth() + l.getAbsorptionAmount();
            Float prev = lastHealth.put(e.getId(), hp);
            if (prev != null && prev - hp > 0.05f) {
                Vec3d at = e.getEntityPos().add((Math.random() - 0.5) * 0.6, e.getHeight() + 0.3, (Math.random() - 0.5) * 0.6);
                int c = prev - hp >= 6 ? 0xFFFF4040 : prev - hp >= 3 ? 0xFFFFAA33 : 0xFFFFFF66;
                numbers.add(new Number(at, String.format(Locale.ROOT, LT.L4, prev - hp), System.currentTimeMillis(), c));
            }
        }
        lastHealth.keySet().removeIf(id -> mc.world.getEntityById(id) == null);
    }

    @EventSubscribe
    private void onRender(EventRender2DGui event) {
        DrawContext ctx = event.getContext();
        long now = System.currentTimeMillis();
        if (marker.getValue() && now - markerAt < 350) {
            float t = (now - markerAt) / 350f;
            int a = Math.round(255 * (1 - t));
            int c = (a << 24) | (color.getARGB() & 0xFFFFFF);
            float cx = ctx.getScaledWindowWidth() / 2f, cy = ctx.getScaledWindowHeight() / 2f;
            float in = 3 + 2 * t, out = 8 + 2 * t;
            FramebufferTarget.line(ctx, cx - in, cy - in, cx - out, cy - out, c);
            FramebufferTarget.line(ctx, cx + in, cy - in, cx + out, cy - out, c);
            FramebufferTarget.line(ctx, cx - in, cy + in, cx - out, cy + out, c);
            FramebufferTarget.line(ctx, cx + in, cy + in, cx + out, cy + out, c);
        }
        Render3D frame = EchoEvents.lastFrame();
        numbers.removeIf(n -> now - n.start() > 1200);
        if (frame == null) return;
        for (Number n : numbers) {
            float t = (now - n.start()) / 1200f;
            Vector4f clip = frame.toClip(n.pos().x, n.pos().y + t * 0.6, n.pos().z);
            if (clip.w() <= 0.05f) continue;
            float[] g = Render3D.toGui(clip);
            int a = Math.round(255 * Math.min(1f, 2f * (1 - t)));
            Text.drawCenteredX(ctx, n.text(), g[0], g[1], 9f, (a << 24) | (n.color() & 0xFFFFFF));
        }
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x95d0,0xefea,0x520f,0x9041,0x54ba,0x6400,0x004e,0xb191,0x3198,0x216a,0x286f,0xa098,0xbdcd,0x667c,0x6d9e,0x6b47}, new char[]{0x6443,0x31ea,0x2105,0x281c,0x003d,0x5267,0x54db,0x9028,0xa0ea,0x95f0,0xb1dc,0xef8b,0x6dec,0x6617,0x6b22,0xbdbf}).toString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb0a8,0xf30b,0xac25,0xa9ec,0x3ee2,0x6550,0x7c9e,0x11f9,0x82f3,0x1c80,0x7c22,0xf93a,0x1852,0x9bc2}, new char[]{0xf34f,0x6531,0x9baf,0x1833,0xac42,0xf95f,0x11d9,0x82bd,0x7ceb,0x7c4f,0x3e80,0xa989,0x1cf2,0xb0db}).toString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x13aa,0xc8c3,0xb99a,0xf702,0x043f}, new char[]{0xf741,0x0450,0xb9f6,0xc8ac,0x13d8}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf759,0x6d2e,0x12f1,0xd60f}, new char[]{0xd62a,0xf777,0x12c0,0x6d48}).plainString();
    }
}
