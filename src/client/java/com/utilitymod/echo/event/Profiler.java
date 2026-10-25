package com.utilitymod.echo.event;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-module CPU time spent in event handlers (Visual Profiler). Accumulates while enabled and
 * publishes a snapshot once per second as milliseconds per second (ms/s): 16 ms/s at 60 FPS
 * is ~0.27 ms per frame.
 */
public final class Profiler {
    public static volatile boolean enabled;
    public record Entry(String name, double msPerSecond) {}

    private static final Map<Object, long[]> acc = new HashMap<>();
    private static volatile List<Entry> snapshot = List.of();
    private static long windowStart = System.nanoTime();

    private Profiler() {}

    static synchronized void record(Object owner, long nanos) {
        acc.computeIfAbsent(owner, k -> new long[1])[0] += nanos;
        long now = System.nanoTime();
        if (now - windowStart >= 1_000_000_000L) publish(now);
    }

    private static void publish(long now) {
        double secs = (now - windowStart) / 1e9;
        List<Entry> out = new ArrayList<>();
        for (var e : acc.entrySet()) {
            Object o = e.getKey();
            String name = o instanceof com.utilitymod.core.Module m ? m.getName() : o.getClass().getSimpleName();
            out.add(new Entry(name, e.getValue()[0] / 1e6 / secs));
        }
        out.sort((a, b) -> Double.compare(b.msPerSecond(), a.msPerSecond()));
        snapshot = List.copyOf(out);
        acc.clear();
        windowStart = now;
    }

    public static List<Entry> snapshot() {
        return snapshot;
    }
}
