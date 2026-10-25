package com.utilitymod.echo.features.impl.render;

import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventTick;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.settings.impl.BoolSetting;
import com.utilitymod.echo.features.settings.impl.ModeSetting;
import com.utilitymod.echo.render.CustomParticles;
import com.utilitymod.echo.render.CustomParticles.Shape;
import dev.koifih.client.ui.Theme;
import dev.koifih.client.util.Colors;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;

/** Trails, orbiting sparks and impact bursts on your projectiles (arrows, tridents, pearls, potions...). */
public class ProjectileEffects extends Feature {
    private final ModeSetting trail = new ModeSetting(LT.L1, LT.L2, LT.L2, LT.L3, LT.L4, LT.L5, LT.L6, LT.L7);
    private final BoolSetting orbit = new BoolSetting(LT.L8, true);
    private final BoolSetting impact = new BoolSetting(LT.L9, true);
    private final BoolSetting others = new BoolSetting(LT.L10, false);

    private final Map<Integer, Vec3d> tracked = new HashMap<>();
    private int tick;

    public ProjectileEffects() {
        super(new FeatureInfo(new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6550,0xa8b9,0xfce1,0x76b1,0xf046,0xeba4,0x9626,0x37db,0x82c4,0xb16c,0xbc03,0x4a1e,0x1c9e,0xc5c8,0x2277,0xd12a,0x11b0,0x0b28}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87,0x76d4,0xf025,0x4a6a,0xc5bb}).toString(), new com.utilitymod.echo.utils.EncryptedString(new char[]{0x04f1,0x6d74,0x7a49,0x6542,0x181a,0xd938,0xe0b5,0x2279,0xe739,0xb17c,0x479d,0x37d2,0x0cdb,0xc5c8,0xd12e,0x93b4,0xb983,0xef4b,0x3b36,0x5f78,0x335d,0xac03,0x1598,0xcdeb,0xa1af,0xbc00,0x66e1,0x0b70,0xf042,0xeb88,0x828d,0x3fbd,0xfcee,0xdad4,0x4a4a,0x7edf,0x2a94,0xa43f,0xf41e,0x76ba,0x8ae4,0x722b,0x11f2,0xc97a,0xc14f,0xa8b9,0x1c9d,0xb51e,0x261f,0x4fb6,0x8670,0x9bbb,0xf8d8,0x0038,0x9631,0x5475,0x50dc,0x430c,0x9e4f,0xd2a1,0x58f9}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87,0x76d4,0xf025,0x4a6a,0xc5bb,0x5f08,0xd959,0x50ae,0x2aff,0xa44c,0x3f9d,0xb9e2,0x3333,0x8a80,0x04d1,0x9e26,0x1877,0x93c4,0x6d15,0xe75a,0x7eab,0xf8f8,0x7249,0xcd9e,0x47ef,0xc13c,0x588d,0xd2d2,0xac23,0x2670,0xa1c1,0x3b16,0xb567,0x0cb4,0x8605,0x004a,0x9b9b,0x15e8,0xef39,0x668e,0xe0df,0x7a2c,0xf47d,0x4fc2,0xc913,0x4360,0xdab1,0x5406}).toString(), Category.RENDER));
    }

    private int color() {
        return Colors.lerp(Theme.ACCENT, 0xFFFFFFFF, 0.15f);
    }

    private Shape shape() {
        return switch (trail.getValue().toString()) {
            case "Star" -> Shape.STAR;
            case "Sparkle" -> Shape.SPARKLE;
            case "Heart" -> Shape.HEART;
            case "Snow" -> Shape.SNOW;
            default -> Shape.GLOW;
        };
    }

    @EventSubscribe
    private void onTick(EventTick event) {
        if (isNull()) return;
        tick++;
        Map<Integer, Vec3d> seen = new HashMap<>();
        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof ProjectileEntity p)) continue;
            if (!others.getValue() && p.getOwner() != mc.player) continue;
            Vec3d pos = e.getEntityPos();
            Vec3d vel = e.getVelocity();
            boolean flying = vel.lengthSquared() > 0.01;
            seen.put(e.getId(), pos);
            if (!flying) continue;
            if (!trail.is(LT.L7)) {
                // a few samples between last and current position keep the trail continuous
                Vec3d last = tracked.getOrDefault(e.getId(), pos);
                for (int i = 0; i < 3; i++) {
                    Vec3d q = last.lerp(pos, i / 3.0);
                    CustomParticles.spawn(shape(), q.x, q.y, q.z, 0, 0.02, 0, 0.13f, 0.7f, color(), 0f);
                }
            }
            if (orbit.getValue()) {
                // two sparks circling the flight direction
                Vec3d dir = vel.normalize();
                Vec3d side = dir.crossProduct(new Vec3d(0, 1, 0));
                if (side.lengthSquared() < 1e-4) side = new Vec3d(1, 0, 0);
                side = side.normalize();
                Vec3d up = side.crossProduct(dir).normalize();
                for (int k = 0; k < 2; k++) {
                    double a = tick * 0.9 + k * Math.PI;
                    Vec3d o = side.multiply(Math.cos(a) * 0.35).add(up.multiply(Math.sin(a) * 0.35));
                    CustomParticles.spawn(Shape.SPARKLE, pos.x + o.x, pos.y + o.y, pos.z + o.z, 0, 0, 0, 0.09f, 0.45f, 0xFFFFFFFF, 0f);
                }
            }
        }
        // gone or stopped since last tick -> impact at its last position
        if (impact.getValue()) {
            for (var e : tracked.entrySet()) {
                Entity ent = mc.world.getEntityById(e.getKey());
                boolean stopped = ent != null && ent.getVelocity().lengthSquared() <= 0.01;
                if (!seen.containsKey(e.getKey()) || (stopped && seen.containsKey(e.getKey()) && !e.getValue().equals(seen.get(e.getKey())))) {
                    Vec3d p = e.getValue();
                    CustomParticles.burst(Shape.RING, p.x, p.y, p.z, 4, 0.6, 0.45f, 0.5f, color(), 0f);
                    CustomParticles.burst(shape() == Shape.GLOW ? Shape.STAR : shape(), p.x, p.y, p.z, 14, 2.4, 0.16f, 0.8f, color(), 1.2f);
                }
            }
        }
        tracked.clear();
        for (var e : seen.entrySet()) {
            Entity ent = mc.world.getEntityById(e.getKey());
            if (ent != null && ent.getVelocity().lengthSquared() > 0.01) tracked.put(e.getKey(), e.getValue());
        }
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6f6b,0xe1ef,0xdb60,0x89ce,0x8bff}, new char[]{0x6f3f,0x8b8d,0x89af,0xe186,0xdb0c}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe910,0xe4ed,0xec8c,0x66a2}, new char[]{0xe957,0xe481,0xece3,0x66d5}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xdee5,0xf5aa,0x179c,0x7d2b}, new char[]{0x17cf,0xde91,0xf5cb,0x7d59}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x422b,0x67f4,0xb0f6,0x0f43,0xce87,0xd410,0x9438}, new char[]{0x4278,0x0f33,0xd471,0xb084,0x9453,0xceeb,0x6791}).plainString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xcdb3,0x4abc,0x6984,0x0787,0xe5c6}, new char[]{0xcdfb,0x4ad9,0x69e5,0x07f5,0xe5b2}).plainString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5e7a,0x80e2,0xc3bc,0xc843}, new char[]{0xc3ef,0x808c,0x5e15,0xc834}).plainString();
        static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2f80,0xc113,0xee1c,0xf224}, new char[]{0xee52,0xc17c,0x2fee,0xf241}).plainString();
        static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x46f1,0x6f0d,0x0ba4,0xf906,0xced5,0xfa28,0x162a,0xfb84,0x4d1b,0x1781,0xc06a,0xd262,0x33c5,0xaa45}, new char[]{0x46be,0x6f7f,0x33a7,0xcebc,0xc01e,0x4d72,0xf968,0xfa4f,0xaa65,0x0bf0,0xfbf6,0xd203,0x17e8,0x1646}).toString();
        static final String L9 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x88cd,0x9bc8,0x953e,0xa1b2,0xcb67,0x7b5a,0xafd1,0x4d6d,0x2125,0x71f9,0xd9d0,0x8df7,0xb3bf}, new char[]{0x8884,0x8d9a,0xd9a0,0xcb06,0x7b39,0x2151,0x951e,0xaf94,0xa1d4,0x719f,0x4d08,0x9bab,0xb3cb}).toString();
        static final String L10 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1ae8,0xd0f5,0xee4f,0xc9e5,0xe915,0x1313,0x31e8,0xe466,0x971f,0xf18a,0xa9ed,0x26e6,0x5710,0x46a6,0x7127,0xdcc6,0x7a66,0xb6d8}, new char[]{0xe429,0x1367,0x268e,0xf1ef,0x1a9a,0x4686,0xb688,0x7a0a,0x3189,0x5769,0xa988,0x976d,0x7154,0xdce1,0xee6f,0xe941,0xc98a,0xd09a}).toString();
    }
}
