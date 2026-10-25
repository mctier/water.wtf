package dev.koifih.client.ui;

public final class Transition {
    @FunctionalInterface
    public interface Easing {
        Easing EASE_OUT_CUBIC = t -> 1f - (1f - t) * (1f - t) * (1f - t);
        Easing SMOOTHSTEP = t -> t * t * (3f - 2f * t);
        Easing EASE_OUT_BACK = t -> back(t, 1.70158f);
        Easing EASE_OUT_SETTLE = t -> back(t, 1.3f);
        Easing EASE_OUT_EXPO = t -> t >= 1f ? 1f : 1f - (float) Math.pow(2, -10f * t);
        Easing EASE_IN_CUBIC = t -> t * t * t;

        float apply(float t);

        default float at(float t) {
            return apply(Math.clamp(t, 0f, 1f));
        }

        default float over(float time, float delay, float duration) {
            return at((time - delay) / duration);
        }

        private static float back(float t, float amount) {
            float u = t - 1f;
            return 1f + (amount + 1f) * u * u * u + amount * u * u;
        }
    }

    private final long enterNanos;
    private final long exitNanos;
    private final Easing enterEasing;
    private final Easing exitEasing;
    private long durationNanos;
    private long delayNanos;
    private Easing easing;
    private float from;
    private float target;
    private long startedAt;

    public Transition(float initial, int durationMillis) {
        this(initial, durationMillis, Easing.EASE_OUT_CUBIC);
    }

    public Transition(float initial, int durationMillis, Easing easing) {
        this(initial, durationMillis, durationMillis, easing, easing);
    }

    public Transition(float initial, int enterMillis, int exitMillis, Easing enterEasing, Easing exitEasing) {
        this.enterNanos = enterMillis * 1_000_000L;
        this.exitNanos = exitMillis * 1_000_000L;
        this.enterEasing = enterEasing;
        this.exitEasing = exitEasing;
        this.durationNanos = this.enterNanos;
        this.easing = enterEasing;
        snap(initial);
    }

    public void snap(float value) {
        from = value;
        target = value;
        delayNanos = 0L;
        startedAt = System.nanoTime();
    }

    public void set(float newTarget) {
        set(newTarget, 0);
    }

    public void set(float newTarget, int delayMillis) {
        if (newTarget == target) return;
        from = value();
        target = newTarget;
        boolean rising = newTarget > from;
        durationNanos = rising ? enterNanos : exitNanos;
        easing = rising ? enterEasing : exitEasing;
        delayNanos = delayMillis * 1_000_000L;
        startedAt = System.nanoTime();
    }

    public float flight() {
        float span = Math.abs(target - from);
        return span <= 0f ? 0f : Math.clamp(Math.abs(target - value()) / span, 0f, 1f);
    }

    public float target() {
        return target;
    }

    public float value() {
        long elapsed = System.nanoTime() - startedAt - delayNanos;
        if (elapsed <= 0L) return from;
        float progress = Math.clamp(elapsed / (float) durationNanos, 0f, 1f);
        return progress >= 1f ? target : from + (target - from) * easing.apply(progress);
    }
}
