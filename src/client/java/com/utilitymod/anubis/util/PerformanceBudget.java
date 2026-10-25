package com.utilitymod.anubis.util;

import com.utilitymod.anubis.module.impl.client.PerformanceModule;

/**
 * Shim: one shared per-tick time budget for background scans, so several finders running
 * together never cost more than {@link PerformanceModule#scanBudgetNanos()} per tick.
 */
public final class PerformanceBudget {
    private static long tickStart, used, lastTick;

    private PerformanceBudget() {}

    public static void beginTick() {
        lastTick = used;
        used = 0;
        tickStart = System.nanoTime();
    }

    /** Deadline (nanoTime) for a scan that wants {@code requestedNanos}; shrinks as the tick budget is spent. */
    public static long acquire(long requestedNanos) {
        long left = Math.max(200_000L, PerformanceModule.scanBudgetNanos() - used);
        return System.nanoTime() + Math.max(0L, Math.min(requestedNanos, left));
    }

    public static void record(long startedAt) {
        used += Math.max(0L, System.nanoTime() - startedAt);
    }

    public static long lastTickNanos() {
        return lastTick;
    }
}
