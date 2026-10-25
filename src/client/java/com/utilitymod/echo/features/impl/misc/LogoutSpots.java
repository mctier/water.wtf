package com.utilitymod.echo.features.impl.misc;

import com.utilitymod.echo.EchoEvents;
import com.utilitymod.echo.Render3D;
import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventRender2DGui;
import com.utilitymod.echo.event.impl.EventTick;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.impl.render.Notifications;
import com.utilitymod.echo.features.settings.impl.BoolSetting;
import com.utilitymod.echo.features.settings.impl.IntSetting;
import dev.koifih.client.render.Draw;
import dev.koifih.client.render.Text;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Marks where nearby players logged out (they vanished from the tab list while in range). */
public class LogoutSpots extends Feature {
    private final BoolSetting notify = new BoolSetting(LT.L1, true);
    private final IntSetting keep = new IntSetting(LT.L2, 10, 1, 60);

    private record Spot(String name, Vec3d pos, float health, long at) {}

    private final Map<UUID, Spot> lastSeen = new HashMap<>();
    private final List<Spot> spots = new ArrayList<>();

    public LogoutSpots() {
        super(new FeatureInfo(new com.utilitymod.echo.utils.EncryptedString(new char[]{0xeb92,0x0b7c,0x1c82,0x962c,0x227f,0xd136,0x11e4,0x82fe,0xa8a4,0x37cb,0xb17b,0xbc06}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1}).toString(), new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc5c2,0x1810,0x3340,0x11f5,0x1c97,0x6558,0x3ff8,0x0b2b,0xd929,0xfce2,0xa435,0x76b5,0x4a08,0x723c,0xb990,0x2a9e,0xf897,0x50c2,0x9631,0x37cd,0x5f28,0xbc02,0xf057,0xcdea,0x93a3,0x226a,0x8aa0,0xe73e,0x04bd,0x9e49,0x7e8b,0xebc1,0x6d70,0xa8aa,0x82c5,0xd137,0xb12f}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87,0x76d4,0xf025,0x4a6a,0xc5bb,0x5f08,0xd959,0x50ae,0x2aff,0xa44c,0x3f9d,0xb9e2,0x3333,0x8a80,0x04d1,0x9e26,0x1877,0x93c4,0x6d15,0xe75a,0x7eab,0xf8f8,0x7249,0xcd9e}).toString(), Category.UTILITY));
    }

    @Override
    public void onDisable() {
        super.onDisable();
        lastSeen.clear();
        spots.clear();
    }

    @EventSubscribe
    private void onTick(EventTick event) {
        if (isNull() || mc.getNetworkHandler() == null) {
            lastSeen.clear();
            return;
        }
        long t = System.currentTimeMillis();
        Map<UUID, Spot> now = new HashMap<>();
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p != mc.player) now.put(p.getUuid(), new Spot(p.getName().getString(), p.getEntityPos(), p.getHealth(), t));
        }
        for (var e : lastSeen.entrySet()) {
            // out of render only counts as a logout if they're also gone from the tab list
            if (now.containsKey(e.getKey()) || mc.getNetworkHandler().getPlayerListEntry(e.getKey()) != null) continue;
            Spot s = e.getValue();
            spots.removeIf(o -> o.name().equals(s.name()));
            spots.add(new Spot(s.name(), s.pos(), s.health(), t));
            if (notify.getValue()) Notifications.push(new com.utilitymod.echo.utils.EncryptedString(new char[]{0xeb92,0x0b7c,0x1c82,0x962c,0x227f,0xd136,0x11e4,0x82fe,0xa8a4,0x37cb,0xb17b,0xbc06}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1}).toString(), s.name() + LT.L3
                    + (int) s.pos().x + LT.L4 + (int) s.pos().y + LT.L4 + (int) s.pos().z, Notifications.Type.INFO);
        }
        // back online, or expired
        spots.removeIf(o -> now.values().stream().anyMatch(n -> n.name().equals(o.name())) || t - o.at() > keep.getValue() * 60_000L);
        lastSeen.clear();
        lastSeen.putAll(now);
    }

    @EventSubscribe
    private void onRender(EventRender2DGui event) {
        Render3D frame = EchoEvents.lastFrame();
        if (frame == null || mc.player == null || mc.options.hudHidden || spots.isEmpty()) return;
        var ctx = event.getContext();
        for (Spot s : spots) {
            var clip = frame.toClip(s.pos().x, s.pos().y + 2.1, s.pos().z);
            if (clip.w() <= 0.05f) continue;
            float[] p = Render3D.toGui(clip);
            String label = s.name() + "  " + Math.round(s.health()) + LT.L5 + Math.round(mc.player.getEntityPos().distanceTo(s.pos())) + LT.L6;
            float tw = Text.width(label, 7f) + 10;
            Draw.rect(ctx, Math.round(p[0] - tw / 2), Math.round(p[1] - 6), Math.round(tw), 12, 4, 0xC0301018);
            Text.drawCenteredX(ctx, label, p[0], p[1] - 0.5f, 7f, 0xFFFF7A7A);
        }
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x49a1,0x85b5,0xb947,0x498d,0xb79f,0x2408}, new char[]{0x2446,0x49ce,0xb933,0x85dc,0x49eb,0xb7e6}).toString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe969,0xe513,0xae6e,0x15e2,0xbceb,0xf586,0x0a59,0x1ea5,0x1edf,0x9367,0x95e5,0x7ce2,0xc297,0x008e}, new char[]{0x7ca9,0xe576,0x0a3c,0x1592,0xe949,0xbcc3,0xae03,0x1eb6,0x1ecb,0xc2e2,0x9591,0xf5e3,0x9314,0x00a7}).toString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4d6b,0x208b,0xdf4c,0xc2f1,0xe556,0x31c9,0x3fd5,0xf167,0x5168,0x8750,0x99e8,0x8b67,0xafb7,0xc9c8,0x8d97}, new char[]{0x5148,0xc29d,0x31a6,0x4d0c,0x8b00,0x8735,0x998c,0x3ff5,0xafd8,0x20fe,0xe522,0x8db7,0xc9a9,0xdf38,0xf147}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x02b9,0x55f4}, new char[]{0x0295,0x55d4,0x9dfe,0x8da5}).plainString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa560,0x9afc,0xb23a,0x6691}, new char[]{0xa508,0x9a8c,0x66b1,0xb21a}).plainString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa48a}, new char[]{0xa4e7,0x23f5,0x05de,0x3878}).plainString();
    }
}
