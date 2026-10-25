package com.utilitymod.echo.utils.rotation;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Port of the mochi client's HumanizedRandom: seeded PRNG with the triangular
 * sqrt-interp, exponential duration window, clamped gaussian, log-normal ms
 * window and weighted pick the humanized routines sample.
 */
public final class HumanizedRandom {

    private final Random random;
    private final HumanizedNoise fractalNoise;
    private double slewState;

    public HumanizedRandom() {
        this(ThreadLocalRandom.current().nextLong());
    }

    public HumanizedRandom(long seed) {
        this.random = new Random(seed);
        this.fractalNoise = new HumanizedNoise((int) (seed ^ seed >>> 32));
    }

    /** Triangular distribution crowded toward the {@code ideal} knot. */
    public double computeDouble(double min, double ideal, double max) {
        if (min > max) {
            double swap = min;
            min = max;
            max = swap;
        }
        if (max == min) {
            return min;
        }
        double knot = Math.max(min, Math.min(max, ideal));
        double fraction = (knot - min) / (max - min);
        double roll = this.random.nextDouble();
        return roll < fraction
                ? min + Math.sqrt(roll * (max - min) * (knot - min))
                : max - Math.sqrt((1.0 - roll) * (max - min) * (max - knot));
    }

    /** Exponential duration around {@code medianMs}, clamped to a window. */
    public long computeNextLong(long medianMs, long minMs, long maxMs) {
        long lo = Math.min(minMs, maxMs);
        long hi = Math.max(minMs, maxMs);
        if (medianMs <= 0L) {
            return Math.max(0L, lo);
        }
        double value = -Math.log(1.0 - this.random.nextDouble()) * (double) medianMs;
        return Math.max(lo, Math.min(hi, Math.round(value)));
    }

    public double computeDouble5(double a, double b) {
        if (a > b) {
            double swap = a;
            a = b;
            b = swap;
        }
        return a == b ? a : a + this.random.nextDouble() * (b - a);
    }

    public float computeFloat(float a, float b) {
        return (float) this.computeDouble5((double) a, (double) b);
    }

    public int computeInt2(int min, int max) {
        return min >= max ? min : min + this.random.nextInt(max - min);
    }

    public boolean checkCondition(double odds) {
        if (odds <= 0.0) {
            return false;
        }
        return odds >= 1.0 || this.random.nextDouble() < odds;
    }

    public double computeDouble6(double value, double sigma) {
        return sigma == 0.0 ? value : value + this.random.nextGaussian() * sigma;
    }

    public double computeDouble7(double value) {
        if (value <= 0.0) {
            return 0.0;
        }
        return this.random.nextBoolean() ? value : -value;
    }

    /** Log-normal millisecond window centered at 35% of the span. */
    public long computeLong3(double a, double b) {
        double lo = Math.min(a, b);
        double hi = Math.max(a, b);
        if (hi <= lo) {
            return Math.round(lo);
        }
        double mid = lo + (hi - lo) * 0.35;
        double value = mid * Math.exp(0.45 * this.random.nextGaussian());
        return Math.round(Math.max(lo, Math.min(hi, value)));
    }

    public double computeDouble8(double clamp) {
        if (clamp <= 0.0) {
            return 0.0;
        }
        double value = this.random.nextGaussian() * (clamp / 3.0);
        return Math.max(-clamp, Math.min(clamp, value));
    }

    public double sample(double value) {
        return this.fractalNoise.sample(value);
    }

    public double sampleOctaves(double value, int octaves) {
        return this.fractalNoise.sampleOctaves(value, octaves);
    }

    /** mochi computeDouble2: tracks a slew state and niggles away from quantum edges. */
    public double computeDouble2(double value, double quantum) {
        if (!(quantum > 0.0) || value == 0.0) {
            this.slewState = value;
            return value;
        }
        double candidate = this.slewState + this.random.nextGaussian() * quantum;
        if (candidate > value) {
            candidate = value - (candidate - value);
        } else if (candidate < 0.0) {
            candidate = -candidate;
        }
        this.slewState = Math.max(0.0, Math.min(value, candidate));
        return this.slewState;
    }
}
