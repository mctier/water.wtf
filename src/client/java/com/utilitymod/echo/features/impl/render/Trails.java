package com.utilitymod.echo.features.impl.render;

import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventRender3D;
import com.utilitymod.echo.event.impl.EventTick;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.settings.impl.BoolSetting;
import com.utilitymod.echo.features.settings.impl.ColorSetting;
import com.utilitymod.echo.features.settings.impl.IntSetting;
import com.utilitymod.echo.render3.api.FramebufferTarget;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** Breadcrumb trail behind you (and optionally other players), fading with age; rainbow option. */
public class Trails extends Feature {
    private final com.utilitymod.echo.features.settings.impl.ModeSetting style = new com.utilitymod.echo.features.settings.impl.ModeSetting(
            LT.L1, LT.L2, LT.L2, LT.L3, LT.L4);
    private final IntSetting length = new IntSetting(LT.L5, 40, 5, 200);
    private final BoolSetting others = new BoolSetting(LT.L6, false);
    private final BoolSetting rainbow = new BoolSetting(LT.L7, true);
    private final ColorSetting color = new ColorSetting(LT.L8, 0xFF7AD7FF, o -> !rainbow.getValue());

    private final Map<Integer, Deque<Vec3d>> points = new HashMap<>();

    public Trails() {
        super(new FeatureInfo(new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa8b9,0xb17c,0xd12e,0x2279,0x37d2,0xbc00}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f}).toString(), new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6d6c,0x828d,0x2279,0x11f4,0x9622,0x8ae4,0x37cd,0xb98b,0x508e,0xbc1e,0x1c94,0x0b3d,0x76f4,0x4a18,0xeb88,0x04f1,0x181b,0x9e56,0xc5da,0x93a5,0x7ed9,0x5f61,0xe73f,0x6558,0xa429,0xfce0,0x3ff5,0x335d,0xf88b,0xd13e,0xb12f,0x2a9d,0xd935,0xf051,0xa8b9}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87,0x76d4,0xf025,0x4a6a,0xc5bb,0x5f08,0xd959,0x50ae,0x2aff,0xa44c,0x3f9d,0xb9e2,0x3333,0x8a80,0x04d1,0x9e26,0x1877,0x93c4,0x6d15,0xe75a,0x7eab,0xf8f8}).toString(), Category.RENDER));
    }

    @Override
    public void onDisable() {
        super.onDisable();
        points.clear();
    }

    @EventSubscribe
    private void onTick(EventTick event) {
        if (isNull()) return;
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p != mc.player && !others.getValue()) continue;
            Deque<Vec3d> d = points.computeIfAbsent(p.getId(), k -> new ArrayDeque<>());
            Vec3d pos = p.getEntityPos().add(0, 0.05, 0);
            if (d.isEmpty() || d.peekLast().squaredDistanceTo(pos) > 0.0025) d.addLast(pos);
            while (d.size() > length.getValue()) d.removeFirst();
        }
        points.keySet().removeIf(id -> mc.world.getEntityById(id) == null);
    }

    @EventSubscribe
    private void onRender(EventRender3D event) {
        FramebufferTarget draw = event.getDraw3D().getMinecraftTarget();
        long now = System.currentTimeMillis();
        boolean ribbon = style.is(LT.L2), steps = style.is(LT.L3);
        for (Deque<Vec3d> d : points.values()) {
            int n = d.size(), i = 0;
            if (steps) {
                drawFootsteps(draw, d, now);
                continue;
            }
            Vec3d prev = null;
            for (Iterator<Vec3d> it = d.iterator(); it.hasNext(); i++) {
                Vec3d p = it.next();
                if (prev != null) {
                    float age = i / (float) n;
                    int a = Math.round(40 + 215 * age);
                    int rgb = rainbow.getValue()
                            ? java.awt.Color.HSBtoRGB(((now / 15 + i * 6) % 360) / 360f, 0.6f, 1f) & 0xFFFFFF
                            : color.getARGB() & 0xFFFFFF;
                    draw.line(prev.x, prev.y, prev.z, p.x, p.y, p.z, (a << 24) | rgb);
                    if (ribbon) {
                        // vertical ribbon: top edge + rungs fading toward the tail
                        double h = 0.55 * age;
                        draw.line(prev.x, prev.y + h, prev.z, p.x, p.y + h, p.z, (a << 24) | rgb);
                        draw.line(p.x, p.y, p.z, p.x, p.y + h, p.z, (Math.round(a * 0.35f) << 24) | rgb);
                    }
                }
                prev = p;
            }
        }
    }

    /** Alternating left/right footprints every ~0.7 blocks along the path, fading with age. */
    private void drawFootsteps(FramebufferTarget draw, Deque<Vec3d> d, long now) {
        int n = d.size(), i = 0, step = 0;
        Vec3d prev = null, lastStep = null;
        for (Vec3d p : d) {
            i++;
            if (prev != null && (lastStep == null || lastStep.squaredDistanceTo(p) > 0.5)) {
                Vec3d dir = p.subtract(prev);
                if (dir.lengthSquared() > 1e-6) {
                    dir = dir.normalize();
                    Vec3d side = new Vec3d(-dir.z, 0, dir.x).multiply((step % 2 == 0) ? 0.14 : -0.14);
                    Vec3d c = p.add(side);
                    float age = i / (float) n;
                    int rgb = rainbow.getValue() ? java.awt.Color.HSBtoRGB(((now / 15 + i * 6) % 360) / 360f, 0.6f, 1f) & 0xFFFFFF
                            : color.getARGB() & 0xFFFFFF;
                    int a = Math.round(200 * age);
                    double fw = 0.07, fl = 0.13;
                    draw.box(c.x - fw, c.y - 0.04, c.z - fw, fw * 2, 0.01, fw * 2, (a << 24) | rgb);
                    Vec3d toe = c.add(dir.multiply(fl));
                    draw.line(c.x, c.y - 0.03, c.z, toe.x, toe.y - 0.03, toe.z, (a << 24) | rgb);
                    lastStep = p;
                    step++;
                }
            }
            prev = p;
        }
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7ed3,0x7b6d,0xa15c,0x24a5,0x0ded}, new char[]{0x0dbe,0x24d1,0xa125,0x7b01,0x7eb6}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x33f5,0xe2d5,0x04de,0xd7e3,0xc5a1,0x9fec}, new char[]{0x048c,0x9f85,0xd781,0x3397,0xc5ce,0xe2bb}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6727,0x6916,0x6c43,0x2278,0x8cbc,0xe083,0xb317,0x38f7,0xe2ad}, new char[]{0x38b1,0x6979,0x8cd3,0x6753,0xe2de,0x6c37,0xb372,0xe0f3,0x220b}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2983,0xd3fb,0x5943,0x4109}, new char[]{0x29cf,0x4160,0xd395,0x5926}).plainString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x287c,0x0ff4,0x5fd6,0xd525,0xd516,0x14ce}, new char[]{0xd569,0x2819,0xd578,0x0f93,0x5fa2,0x14a6}).toString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4b97,0x415c,0xcde4,0x5d8f,0xc771,0x3ddd,0x4df2,0x4147,0x75d7,0xa09d,0xee24,0xcc57,0x1a64}, new char[]{0x1a2b,0x4be3,0x412f,0x4139,0x3daf,0xa0bd,0x4da2,0xee48,0x5dee,0xcc2e,0xcd81,0x75a5,0xc702}).toString();
        static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9c95,0xc376,0x3033,0x3f55,0x059c,0xfa14,0x6275}, new char[]{0x6227,0x3052,0x9cfc,0xfa7a,0xc314,0x3f3a,0x05eb}).plainString();
        static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe96b,0xea6a,0x6d51,0xff0d,0x5cba}, new char[]{0xff4e,0xe904,0x5cd6,0x6d3e,0xea18}).plainString();
    }
}
