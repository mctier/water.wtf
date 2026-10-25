package com.utilitymod.echo.features.impl.render;

import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventTick;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.settings.impl.BoolSetting;
import com.utilitymod.echo.features.settings.impl.FloatSetting;
import com.utilitymod.echo.render.CustomParticles;
import com.utilitymod.echo.render.CustomParticles.Shape;
import com.utilitymod.echo.render.Ghosts;
import dev.koifih.client.ui.Theme;
import dev.koifih.client.util.Colors;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;

/** When a player jumps several blocks in one tick (pearl, /tp, chorus...), leave a ghost where they stood. */
public class TeleportEffects extends Feature {
    private final FloatSetting minDistance = new FloatSetting(LT.L1, 5f, 2f, 30f, 0.5f);
    private final BoolSetting self = new BoolSetting(LT.L2, true);
    private final BoolSetting arrival = new BoolSetting(LT.L3, true);

    private record Last(Vec3d pos, float yaw) {}
    private final Map<Integer, Last> last = new HashMap<>();

    public TeleportEffects() {
        super(new FeatureInfo(new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1cd2,0x9606,0xbc0c,0x11f6,0x76a0,0x0b2e,0xeb87,0x37ce,0xf056,0xd12e,0x82d9,0x2274,0xfce4,0xa8ae,0xb160,0x6553}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87,0x76d4,0xf025}).toString(), new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3fef,0x11e0,0xe729,0xd13d,0x93a1,0x58e2,0xa1a7,0x0b3d,0x7e8b,0x1c96,0x04bd,0xbc1a,0x6d67,0x4a06,0xa8a3,0x3b64,0xa429,0x8af0,0xb508,0x9663,0xf88c,0xb987,0x76bd,0x0cd9,0xac57,0x5f7b,0x37ca,0xf046,0x82c3,0x2a97,0x50d9,0x2277,0x722c,0xcdf2,0x6544,0xc14c,0x2650,0x478a,0xb12f,0xd2a0,0xfcf3,0x180e,0xd979,0xc5de,0x3313,0xeb80,0x9e47}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87,0x76d4,0xf025,0x4a6a,0xc5bb,0x5f08,0xd959,0x50ae,0x2aff,0xa44c,0x3f9d,0xb9e2,0x3333,0x8a80,0x04d1,0x9e26,0x1877,0x93c4,0x6d15,0xe75a,0x7eab,0xf8f8,0x7249,0xcd9e,0x47ef,0xc13c,0x588d,0xd2d2,0xac23,0x2670,0xa1c1,0x3b16,0xb567,0x0cb4}).toString(), Category.RENDER));
    }

    @Override
    public void onDisable() {
        super.onDisable();
        last.clear();
    }

    @EventSubscribe
    private void onTick(EventTick event) {
        if (isNull()) {
            last.clear();
            return;
        }
        int color = Colors.lerp(Theme.ACCENT, 0xFFFFFFFF, 0.15f);
        double min = minDistance.getValue() * minDistance.getValue();
        Map<Integer, Last> now = new HashMap<>();
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player && !self.getValue()) continue;
            Vec3d pos = p.getEntityPos();
            now.put(p.getId(), new Last(pos, p.getBodyYaw()));
            Last prev = last.get(p.getId());
            if (prev == null || prev.pos().squaredDistanceTo(pos) < min) continue;
            Vec3d o = prev.pos();
            Ghosts.spawn(o.x, o.y, o.z, prev.yaw(), p.getHeight(), p.getWidth(), true, 1.6f, 0.6f, color, Ghosts.Style.CHAMS);
            CustomParticles.burst(Shape.GLOW, o.x, o.y + 1, o.z, 16, 1.4, 0.25f, 0.8f, color, -0.4f);
            if (arrival.getValue()) {
                CustomParticles.burst(Shape.RING, pos.x, pos.y + 0.1, pos.z, 3, 0.4, 0.6f, 0.5f, color, 0f);
                CustomParticles.burst(Shape.SPARKLE, pos.x, pos.y + 1, pos.z, 12, 1.8, 0.12f, 0.6f, 0xFFFFFFFF, 1.2f);
            }
        }
        last.clear();
        last.putAll(now);
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x67cc,0x2f8b,0xdc04,0x5b71,0xb157,0xaf9e,0x6099,0x3359,0xfba8,0xe485,0x1646,0x85aa}, new char[]{0x6781,0x60f0,0x3337,0xe4a5,0xdc40,0x2fe2,0x85d9,0xafea,0x1627,0x5b1f,0xfbcb,0xb132}).toString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf33e,0x65cf,0xc491,0x6671,0x3db9,0xea10,0x4009,0x60ad,0x1418,0xc234}, new char[]{0x3df0,0x60c3,0x6612,0x65a3,0x146d,0xc4f5,0xf35b,0xc214,0x4044,0xea75}).toString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4b38,0xb727,0xc52f,0x1a72,0x9ada,0x25fd,0xdba9,0x75c2,0x65e9,0x8d70,0x2092,0x8013,0x563c}, new char[]{0x8d31,0x659b,0x564e,0x1a1b,0xc559,0x75a3,0x2591,0xdb89,0x9a98,0xb752,0x8061,0x4b4b,0x20e6}).toString();
    }
}
