package com.utilitymod.echo.features.impl.movement;

/** Standalone assertions; no game runtime or external test dependency required. */
public final class FastBridgePlacementGateTest {
    private static int checks;
    private static void check(boolean result) {
        checks++;
        if (!result) throw new AssertionError("check " + checks);
    }

    public static void main(String[] args) {
        FastBridgePlacementGate gate = new FastBridgePlacementGate();
        check(gate.tryAcquire(100, 2));
        check(!gate.tryAcquire(100, 2));
        check(!gate.tryAcquire(101, 2));
        check(gate.tryAcquire(102, 2));
        gate.reset();
        check(gate.tryAcquire(102, 2));
        check(gate.tryAcquire(0, 2)); // New world clock.
        check(!gate.tryAcquire(0, 0));
        check(gate.tryAcquire(1, 0)); // Clamp minimum to one tick.
        check(!gate.tryAcquire(6, 999));
        check(gate.tryAcquire(7, 999)); // Clamp maximum to six ticks.
        gate.reset();
        for (int tick = 0; tick < 12; tick++) check(gate.tryAcquire(tick, 3) == (tick % 3 == 0));
        System.out.println("PASS: " + checks + " placement timing assertions");
    }
}
