package com.utilitymod.echo.features.impl.render;

import com.utilitymod.echo.EchoEvents;
import com.utilitymod.echo.Render3D;
import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventRender2DGui;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.settings.impl.BoolSetting;
import com.utilitymod.echo.features.settings.impl.FloatSetting;
import com.utilitymod.echo.hud.HudManager;
import com.utilitymod.echo.render.GlassRenderer;
import com.utilitymod.echo.utils.player.PlayerUtils;
import dev.koifih.client.render.Draw;
import dev.koifih.client.render.Text;
import dev.koifih.client.ui.Theme;
import dev.koifih.client.util.Colors;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

/** Glass cards above players: name, health bar, ping, held item and armour. */
public class GlassNametags extends Feature {
    private final FloatSetting range = new FloatSetting(LT.L1, 48f, 8f, 128f, 1f);
    private final FloatSetting scale = new FloatSetting(LT.L2, 1f, 0.5f, 2f, 0.05f);
    private final BoolSetting showGear = new BoolSetting(LT.L3, true);
    private final BoolSetting showPing = new BoolSetting(LT.L4, true);
    private final BoolSetting glass = new BoolSetting(LT.L5, true);

    private record Tag(PlayerEntity p, float x, float y, float s, float w, float h) {}

    public GlassNametags() {
        super(new FeatureInfo(new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd13d,0x0b12,0xb12f,0x37cd,0x9626,0x2279,0xeb80,0xfcf4,0x82cc,0xbc1a,0xa8a7,0x1c9f,0x11e4,0x6551}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87}).toString(), new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6551,0xd979,0x66eb,0x7eca,0x181e,0xac03,0xeb80,0xe0ad,0x722d,0xc5cf,0xc159,0x2279,0x11e4,0x6d72,0xa8a7,0x0cd1,0xd13d,0xef40,0x50c6,0x5f60,0x335b,0x93aa,0x9626,0x3b79,0xcdbe,0x003a,0xb996,0x58ec,0xfcf4,0x37cd,0x1c9f,0x76f4,0xf896,0x3ff1,0x4788,0x82cc,0x4a03,0xbc1a,0x8625,0xb511,0xe77a,0x0b32,0x9e56,0xb12f,0x7a5f,0x2a9a,0xa42d,0x04f1,0xd2a0,0xa1a3,0x8aac,0x2611,0x1589,0x9bf7,0xf052}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87,0x76d4,0xf025,0x4a6a,0xc5bb,0x5f08,0xd959,0x50ae,0x2aff,0xa44c,0x3f9d,0xb9e2,0x3333,0x8a80,0x04d1,0x9e26,0x1877,0x93c4,0x6d15,0xe75a,0x7eab,0xf8f8,0x7249,0xcd9e,0x47ef,0xc13c,0x588d,0xd2d2,0xac23,0x2670,0xa1c1,0x3b16,0xb567,0x0cb4,0x8605,0x004a,0x9b9b,0x15e8,0xef39,0x668e,0xe0df,0x7a2c}).toString(), Category.RENDER));
    }

    @EventSubscribe
    private void onRender(EventRender2DGui event) {
        Render3D frame = EchoEvents.lastFrame();
        if (frame == null || isNull() || mc.options.hudHidden) return;
        DrawContext ctx = event.getContext();
        List<Tag> tags = new ArrayList<>();
        double max = range.getValue() * range.getValue();
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player || p.isInvisible() || mc.player.squaredDistanceTo(p) > max) continue;
            var pos = p.getLerpedPos(frame.tickDelta);
            Vector4f clip = frame.toClip(pos.x, pos.y + p.getHeight() + 0.55, pos.z);
            if (clip.w() <= 0.05f) continue;
            float[] g = Render3D.toGui(clip);
            double dist = Math.sqrt(mc.player.squaredDistanceTo(p));
            float s = (float) Math.max(0.55, Math.min(1.0, 8.0 / Math.max(1.0, dist))) * scale.getValue();
            float w = Math.max(70, Text.width(p.getName().getString(), 8f) + 40), h = showGear.getValue() ? 34 : 22;
            tags.add(new Tag(p, g[0], g[1], s, w, h));
        }
        if (tags.isEmpty()) return;
        // far ones first so near cards draw on top
        tags.sort((a, b) -> Double.compare(mc.player.squaredDistanceTo(b.p()), mc.player.squaredDistanceTo(a.p())));

        boolean useGlass = glass.getValue() && GlassRenderer.available();
        if (useGlass) {
            for (Tag t : tags) {
                float w = t.w() * t.s(), h = t.h() * t.s();
                GlassRenderer.queue(new GlassRenderer.Panel(t.x() - w / 2, t.y() - h, w, h, 6 * t.s(), 1f));
            }
            GlassRenderer.flush(ctx);
        }
        var m = ctx.getMatrices();
        for (Tag t : tags) {
            m.pushMatrix();
            m.translate(t.x() - t.w() * t.s() / 2, t.y() - t.h() * t.s());
            m.scale(t.s(), t.s());
            draw(ctx, t, useGlass);
            m.popMatrix();
        }
    }

    private void draw(DrawContext ctx, Tag t, boolean useGlass) {
        PlayerEntity p = t.p();
        float w = t.w(), h = t.h();
        Draw.rect(ctx, 0, 0, Math.round(w), Math.round(h), 6, Colors.withAlpha(useGlass ? 0xFF101216 : Theme.SIDEBAR, useGlass ? 0.4f : 0.85f));
        boolean friend = PlayerUtils.isFriend(p.getName().getString());
        Text.drawCentered(ctx, Text.fit(p.getName().getString(), w - 34, 8f), 6, 8, 8f, friend ? 0xFF4ADE80 : 0xFFFFFFFF);
        if (showPing.getValue() && mc.getNetworkHandler() != null) {
            PlayerListEntry e = mc.getNetworkHandler().getPlayerListEntry(p.getUuid());
            if (e != null) {
                String ping = e.getLatency() + LT.L6;
                Text.drawCentered(ctx, ping, w - 6 - Text.width(ping, 6.3f), 8, 6.3f, Theme.MUTED);
            }
        }
        float max = Math.max(1f, p.getMaxHealth() + p.getAbsorptionAmount());
        float hp = Math.min(1f, (p.getHealth() + p.getAbsorptionAmount()) / max);
        int col = hp > 0.6f ? 0xFF4ADE80 : hp > 0.3f ? 0xFFFBBF24 : 0xFFF87171;
        com.utilitymod.echo.render.Icons.draw(ctx, LT.L7, 5, 13, 7, col);
        Draw.rect(ctx, 14, 15, Math.round(w - 20), 3, 1, Colors.withAlpha(Theme.TRACK, 0.9f));
        Draw.rect(ctx, 14, 15, Math.max(2, Math.round((w - 20) * hp)), 3, 1, col);
        if (showGear.getValue()) {
            EquipmentSlot[] slots = {EquipmentSlot.MAINHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
                    EquipmentSlot.FEET, EquipmentSlot.OFFHAND};
            com.utilitymod.echo.render.Icons.draw(ctx, LT.L8, 5, 22.5f, 7, 0xFFB8BCC6);
            float gx = 14;
            var m = ctx.getMatrices();
            for (EquipmentSlot slot : slots) {
                ItemStack st = p.getEquippedStack(slot);
                if (st.isEmpty()) continue;
                m.pushMatrix();
                m.translate(gx, 21);
                m.scale(0.65f, 0.65f);
                ctx.drawItem(st, 0, 0);
                m.popMatrix();
                gx += 11.5f;
            }
        }
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc2e4,0x219f,0xbb3b,0xd6d5,0x3520}, new char[]{0x3572,0xd6b4,0xc28a,0x21f8,0xbb5e}).toString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb8a6,0xda5a,0xdc28,0x9246,0x9556}, new char[]{0x9215,0x9535,0xb8c7,0xda36,0xdc4d}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa99c,0x75f4,0x5171,0xb5f8,0x94a4,0xf124,0xedf8,0x6f2d,0x33cc}, new char[]{0xa9cf,0xb590,0x759b,0xf153,0xedd8,0x6f6a,0x5114,0x33ad,0x94d6}).toString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8121,0xa0a5,0x2468,0xa720,0xe8fb,0x331d,0x91ae,0x9ab7,0x79f4}, new char[]{0xe8a8,0x3375,0x91c1,0x241f,0x79d4,0x9ae7,0xa0cc,0xa74e,0x8146}).toString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x191c,0x72fd,0x042a,0xe9ac,0x27c1,0x6ec4,0x80e9,0x8a3c,0xf7b7,0x0712,0x5d54,0xa498}, new char[]{0x8a70,0x0443,0xf7c6,0xe9d9,0x1975,0x0776,0x6ee4,0x80ae,0x5d38,0xa4f9,0x27b2,0x728e}).plainString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7079,0x5a6c}, new char[]{0x7014,0x5a1f,0xdc52,0x6479}).plainString();
        static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8c77,0xc7c2,0x3b8f,0x91ad,0x9bbd}, new char[]{0x9bd5,0x3bea,0x8c16,0xc7b0,0x91d9}).plainString();
        static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7ec4,0xb418,0x0077,0xc060,0xc6cd,0xfa82}, new char[]{0xc013,0xc6a5,0x001e,0xb47d,0xfaee,0x7ea0}).plainString();
    }
}
