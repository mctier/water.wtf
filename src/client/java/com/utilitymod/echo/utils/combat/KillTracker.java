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

    public static void onPacket(Packet<?> packet) {
        if (!(packet instanceof EntityStatusS2CPacket p) || p.getStatus() != DEATH) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.execute(() -> {
            if (mc.world == null) return;
            Entity e = p.getEntity(mc.world);
            if (!(e instanceof LivingEntity living) || e == mc.player) return;
            Long at = recentHits.remove(e.getId());
            if (at == null || System.currentTimeMillis() - at > WINDOW_MS) return;
            for (Consumer<LivingEntity> l : listeners) {
                try {
                    l.accept(living);
                } catch (Throwable ignored) {
                }
            }
        });
        long now = System.currentTimeMillis();
        recentHits.values().removeIf(t -> now - t > WINDOW_MS);
    }
}
