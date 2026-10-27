package com.utilitymod.echo.utils.combat;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * "A living thing you hit in the last 4 seconds just died" -> kill listeners (kill effects,
 * session stats). Death = entity status 3 for that entity id. Fed from EchoEvents' attack and
 * packet hooks, so it works whether or not any module is on.
 */
public final class KillTracker {
    private static final byte DEATH = 3;
    private static final long WINDOW_MS = 4000;
    private static final Map<Integer, Long> recentHits = new ConcurrentHashMap<>();
    private static final List<Consumer<LivingEntity>> listeners = new CopyOnWriteArrayList<>();

    private KillTracker() {}

    public static void listen(Consumer<LivingEntity> onKill) {
        listeners.add(onKill);
    }

    public static void onAttack(Entity target) {
        if (target instanceof LivingEntity) recentHits.put(target.getId(), System.currentTimeMillis());
    }

    /** Fallback for servers that don't send the death status: a recently hit entity that is now dead / at 0 HP. */
    public static void poll() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;
        long now = System.currentTimeMillis();
        for (Map.Entry<Integer, Long> en : recentHits.entrySet()) {
            if (now - en.getValue() > WINDOW_MS) { recentHits.remove(en.getKey()); continue; }
            if (!(mc.world.getEntityById(en.getKey()) instanceof LivingEntity living) || living == mc.player) continue;
            if (!living.isDead() && living.getHealth() > 0) continue;
            recentHits.remove(en.getKey());
            fire(living);
        }
    }

    private static void fire(LivingEntity living) {
        for (Consumer<LivingEntity> l : listeners) {
            try {
                l.accept(living);
            } catch (Throwable ignored) {
            }
        }
    }

    public static void onPacket(Packet<?> packet) {
        if (!(packet instanceof EntityStatusS2CPacket p) || p.getStatus() != DEATH) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.execute(() -> {
            if (mc.world == null) return;
            Entity e = p.getEntity(mc.world);
            if (!(e instanceof LivingEntity living) || e == mc.player) return;
            Long at = recentHits.remove(e.getId());
            if (at == null || System.currentTimeMillis() - at > WINDOW_MS) return;
            fire(living);
        });
        long now = System.currentTimeMillis();
        recentHits.values().removeIf(t -> now - t > WINDOW_MS);
    }
}
