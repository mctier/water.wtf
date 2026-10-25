package com.utilitymod.echo.utils.math;


import net.minecraft.client.MinecraftClient;

public class TimerUtils {
    private long lastMS;
    private long lastUpdateTick;

    /**
     * Gets the current time in milliseconds.
     *
     * @return The current time in milliseconds.
     */
    public long currentTimeMillis() {
        return System.nanoTime() / 1_000_000L;
    }

    /**
     * Gets the current game time in ticks.
     *
     * @return The current game time in ticks, or 0 if player is null.
     */
    private long currentTick() {
        var player = MinecraftClient.getInstance().player;
        return (player != null) ? player.age : 0;
    }

    public void reset() {
        lastMS = currentTimeMillis();
        lastUpdateTick = currentTick();
    }

    public boolean hasElapsedTime(long time) {
        return currentTimeMillis() - lastMS >= time;
    }

    public long getElapsedTime() {
        return currentTimeMillis() - lastMS;
    }

    public boolean hasReached(final double milliseconds) {
        if (milliseconds == 0) return true;
        return currentTimeMillis() - lastMS >= milliseconds;
    }

    /**
     * Check if the specified number of game ticks have elapsed since last reset.
     */
    public boolean hasReachedTicks(final int ticks) {
        if (ticks == 0) return true;
        long current = currentTick();
        if (current < lastUpdateTick) {
            lastUpdateTick = current;
            return false;
        }
        return current - lastUpdateTick >= ticks;
    }

    public int getElapsedTicks() {
        return (int) (currentTick() - lastUpdateTick);
    }
}
