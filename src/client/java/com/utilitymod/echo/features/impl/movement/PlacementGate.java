package com.utilitymod.echo.features.impl.movement;

/** Tick-based rate limit, independent of rendering and wall-clock time. */
final class PlacementGate {
    private long lastAttempt = Long.MIN_VALUE;

    boolean tryAcquire(long tick, int delay) {
        int interval = Math.max(1, Math.min(6, delay));
        if (lastAttempt != Long.MIN_VALUE && tick >= lastAttempt && tick - lastAttempt < interval) return false;
        lastAttempt = tick;
        return true;
    }

    void reset() {
        lastAttempt = Long.MIN_VALUE;
    }
}
