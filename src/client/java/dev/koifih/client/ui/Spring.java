package dev.koifih.client.ui;

public final class Spring {
    private static final float MAX_STEP = 0.05f;
    private static final int SUBSTEPS = 4;

    private final float stiffness;
    private final float damping;
    private float value;
    private float velocity;
    private float target;
    private long updatedAt = System.nanoTime();

    public Spring(float initial, float stiffness, float damping) {
        this.stiffness = stiffness;
        this.damping = damping;
        snap(initial);
    }

    public void snap(float value) {
        this.value = value;
        target = value;
        velocity = 0f;
        updatedAt = System.nanoTime();
    }

    public void set(float target) {
        this.target = target;
    }

    public float update() {
        long now = System.nanoTime();
        float step = Math.min((now - updatedAt) / 1e9f, MAX_STEP) / SUBSTEPS;
        updatedAt = now;
        for (int i = 0; i < SUBSTEPS; i++) {
            velocity += (-stiffness * (value - target) - damping * velocity) * step;
            value += velocity * step;
        }
        return value;
    }
}
