package com.utilitymod.echo.features.impl.combat;

/** Deterministic targeting/weapon/cadence rules shared by the module and headless tests. */
final class AutoMacePolicy {
    private long lastAttempt = Long.MIN_VALUE;
    private long lastAttemptMs = Long.MIN_VALUE;

    boolean ready(long tick, int delay) {
        return lastAttempt == Long.MIN_VALUE || tick < lastAttempt
                || tick - lastAttempt >= Math.max(1, delay);
    }

    /** CPS gate: true once at least intervalMs has passed since the last dispatch. */
    boolean readyMs(long nowMs, long intervalMs) {
        return lastAttemptMs == Long.MIN_VALUE || nowMs < lastAttemptMs
                || nowMs - lastAttemptMs >= Math.max(0L, intervalMs);
    }

    /** Milliseconds between attacks for a target clicks-per-second rate. */
    static long intervalMsForCps(float cps) {
        return cps <= 0f ? 0L : Math.round(1000.0 / cps);
    }

    boolean attempted(long tick) { return lastAttempt == tick; }
    void mark(long tick) { lastAttempt = tick; }
    void markMs(long nowMs) { lastAttemptMs = nowMs; }
    void reset() { lastAttempt = Long.MIN_VALUE; lastAttemptMs = Long.MIN_VALUE; }

    static boolean allowed(boolean player, boolean mob, boolean players, boolean mobs) {
        return player ? players : mob && mobs;
    }

    static boolean preferDensity(boolean shieldDrain, double fall, double threshold) {
        return shieldDrain || fall >= threshold;
    }

    static boolean chance(int percent, int roll) {
        return roll >= 0 && roll < 100 && roll < Math.max(0, Math.min(100, percent));
    }
}
