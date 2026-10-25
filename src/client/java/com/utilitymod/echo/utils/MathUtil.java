package com.utilitymod.echo.utils;

import java.util.Random;

public final class MathUtil {
    public static Random random = new Random(System.currentTimeMillis());

    public static double roundToDecimal(double n, double point) {
        return point * (double)Math.round(n / point);
    }

    public static int randomInt(int start, int bound) {
        return random.nextInt(start, bound);
    }

    public static double randomDouble(double start, double end) {
        return start + (end - start) * random.nextDouble();
    }

    public static double randomFloat(float start, float end) {
        return start + (end - start) * random.nextFloat();
    }

    public static double smoothStepLerp(double delta, double start, double end) {
        delta = Math.max(0.0, Math.min(1.0, delta));
        double t = delta * delta * (3.0 - 2.0 * delta);
        double value = start + (end - start) * t;
        return value;
    }

    public static double goodLerp(float delta, double start, double end) {
        int step = (int)Math.ceil(Math.abs(end - start) * (double)delta);
        if (start < end) {
            return Math.min(start + (double)step, end);
        }
        return Math.max(start - (double)step, end);
    }

    public static int getRandomGaussian(int mean, int deviation) {
        return (int) (random.nextGaussian() * deviation + mean);
    }

    public static double interpolate(double start, double end, double progress) {
        return start + (end - start) * progress;
    }

    public static float interpolate(float start, float end, float progress) {
        return start + (end - start) * progress;
    }

    public static float getVariedSpeed(float baseSpeed, float variationPercent) {
            float variation = baseSpeed * variationPercent;
            float min = baseSpeed - variation;
            float max = baseSpeed + variation;
            return (float) randomFloat(min, max);
    }

    /**
     * Computes seconds elapsed since lastNanoTime. Returns 1/60s on first call (when lastNanoTime is 0).
     * Clamped to 0.1s to avoid jumps after pauses.
     * Caller is responsible for storing the returned nanoTime for the next call.
     *
     * @return float[2]: [0] = delta in seconds, [1] = current nanoTime (store this for next call)
     */
    public static float getDeltaSeconds(long lastNanoTime) {
        long now = System.nanoTime();
        float delta;
        if (lastNanoTime == 0) {
            delta = 1.0f / 60.0f;
        } else {
            delta = (now - lastNanoTime) / 1_000_000_000.0f;
        }
        return Math.min(delta, 0.1f);
    }

    public static long nanoTime() {
        return System.nanoTime();
    }

}
