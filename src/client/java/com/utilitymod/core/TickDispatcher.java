package com.utilitymod.core;

import com.utilitymod.network.PacketQueue;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/**
 * Enforces: all actions must be sent at the START of a gametick.
 * Never between ticks or at END.
 *
 * We ONLY use START_CLIENT_TICK. Modules register via TickDispatcher.onTickStart(Runnable)
 * and packet sending is flushed here.
 */
public final class TickDispatcher {
    private static final java.util.List<Runnable> START_TASKS = new java.util.concurrent.CopyOnWriteArrayList<>();
    private static boolean initialized = false;
    private static int tickCounter = 0;

    private TickDispatcher() {}

    public static void init() {
        if (initialized) return;
        initialized = true;

        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            tickCounter++;
            // 1. Execute module logic scheduled for tick start
            for (Runnable r : START_TASKS) {
                try { r.run(); } catch (Throwable t) { t.printStackTrace(); }
            }
            // 2. Flush vanilla packets - still within START phase
            PacketQueue.flush();

            // 3. Tick modules (they must enqueue packets, not send directly)
            ModuleManager.onTickStart(client);
        });

        // Explicitly do NOT register END_CLIENT_TICK for actions.
        // If you need END for rendering/state only, use separate callback.
    }

    public static void onTickStart(Runnable r) {
        START_TASKS.add(r);
    }

    public static void removeTickStart(Runnable r) {
        START_TASKS.remove(r);
    }

    public static int getTick() { return tickCounter; }
}
