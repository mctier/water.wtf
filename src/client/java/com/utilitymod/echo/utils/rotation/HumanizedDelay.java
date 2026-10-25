package com.utilitymod.echo.utils.rotation;

/**
 * Port of the mochi client's HumanizedDelay: a slow wandering amplitude over
 * wall time (fractal octaves + a slow sine + decaying spikes) used to timeline
 * jitter envelopes. Feed absolute millis; sample the returned value as 0..1.
 */
public final class HumanizedDelay {

    private final double sineFrequency;
    private final double amplitude;
    private final double noiseFrequency;
    private final double phase;
    private final long timestampMillis;
    private final HumanizedRandom humanizedRandom = new HumanizedRandom();
    private final HumanizedNoise fractalNoise = new HumanizedNoise();
    private long spikeResetAt;
    private double spike;

    public HumanizedDelay(int largenessPercent) {
        this(largenessPercent, 1.0);
    }

    public HumanizedDelay(int largenessPercent, double scale) {
        this.amplitude = Math.max(0.0, scale) * HumanizedNoise.percentUnit(largenessPercent);
        this.noiseFrequency = 0.15 + this.humanizedRandom.computeDouble5(0.0, 0.25);
        this.sineFrequency = 1.5 + this.humanizedRandom.computeDouble5(0.0, 3.0);
        this.phase = this.humanizedRandom.computeDouble5(0.0, Math.PI * 2);
        this.timestampMillis = System.currentTimeMillis();
        this.spikeResetAt = this.timestampMillis + this.humanizedRandom.computeNextLong(180L, 60L, 500L);
    }

    public double computeDouble(long timeMillis) {
        if (this.amplitude <= 0.0) {
            return 0.0;
        }
        double elapsedSeconds = (double) (timeMillis - this.timestampMillis) / 1000.0;
        double octaves = this.fractalNoise.sampleOctaves(elapsedSeconds * this.noiseFrequency, 3);
        double sine = Math.sin((double) timeMillis * this.sineFrequency * (Math.PI * 2) / 1000.0 + this.phase) * 0.18;
        if (timeMillis >= this.spikeResetAt) {
            this.spike = this.humanizedRandom.computeDouble8(0.55);
            this.spikeResetAt = timeMillis + this.humanizedRandom.computeNextLong(180L, 60L, 500L);
        }
        this.spike *= 0.88;
        double value = (octaves * 0.7 + sine + this.spike) * this.amplitude;
        return HumanizedNoise.zeroGapJiggle(value, 0.01);
    }
}
