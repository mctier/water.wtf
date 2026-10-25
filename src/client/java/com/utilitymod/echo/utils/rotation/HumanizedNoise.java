package com.utilitymod.echo.utils.rotation;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Port of the mochi client's FractalNoise plus the tiny numeric jiggler its
 * consumers slide through (MathUtils.computeDouble4/6/7, clamped gaussian).
 */
public final class HumanizedNoise {

    private final int seed;

    public HumanizedNoise() {
        this(ThreadLocalRandom.current().nextInt());
    }

    public HumanizedNoise(int seed) {
        this.seed = seed;
    }

    private double hashLatticePoint(int value) {
        int mixed = value * 374761393 + this.seed * 668265263;
        mixed = (mixed ^ mixed >>> 13) * 1274126177;
        mixed ^= mixed >>> 16;
        return (double) (mixed & Integer.MAX_VALUE) / 2.147483647e9;
    }

    public double sample(double value) {
        int floor = (int) Math.floor(value);
        double smooth = smoothstep(value - (double) floor);
        double left = this.hashLatticePoint(floor);
        double right = this.hashLatticePoint(floor + 1);
        return (left + (right - left) * smooth) * 2.0 - 1.0;
    }

    public double sampleOctaves(double value, int octaves) {
        return this.sampleFractal(value, octaves, 2.0, 0.5);
    }

    public double sampleFractal(double value, int octaves, double freqFactor, double persistFactor) {
        double amplitude = 1.0;
        double frequency = 1.0;
        double sum = 0.0;
        double norm = 0.0;
        int count = Math.max(1, octaves);
        for (int i = 0; i < count; i++) {
            sum += this.sample(value * frequency) * amplitude;
            norm += amplitude;
            frequency *= freqFactor;
            amplitude *= persistFactor;
        }
        return norm <= 0.0 ? 0.0 : sum / norm;
    }

    public static double smoothstep(double value) {
        double clamped = Math.max(0.0, Math.min(1.0, value));
        return clamped * clamped * (3.0 - 2.0 * clamped);
    }

    /** Keeps values clear of a thin gap around every multiple of {@code quantum}. */
    public static double zeroGapJiggle(double value, double quantum) {
        if (!(quantum > 0.0) || value == 0.0) {
            return value;
        }
        double remainder = Math.abs(value % quantum);
        if (!(remainder < quantum * 0.001) && !(quantum - remainder < quantum * 0.001)) {
            return value;
        }
        return value + (value >= 0.0 ? 1.0 : -1.0) * quantum * 0.37;
    }

    /** Gaussian noise clamped to +-clamp (sigma = clamp/3). */
    public static double clampedGauss(double clamp) {
        if (clamp <= 0.0) {
            return 0.0;
        }
        return Math.max(-clamp, Math.min(clamp, ThreadLocalRandom.current().nextGaussian() * (clamp / 3.0)));
    }

    /** mochi MathUtils.computeDouble6: percent clamped to 1..100, scaled to 0..1. */
    public static double percentUnit(int percent) {
        return Math.max(1, Math.min(100, percent)) / 100.0;
    }
}
