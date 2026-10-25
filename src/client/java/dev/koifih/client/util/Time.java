package dev.koifih.client.util;
public final class Time {
    private static final float NANOS_PER_SECOND = 1_000_000_000f;
    private static final long NANOS_PER_MILLI = 1_000_000L;
    private static final long ORIGIN = System.nanoTime();
    public static final long TICK_MILLIS = 50L;
    private static long ticks;

    public static float seconds() {
        return (System.nanoTime() - ORIGIN) / NANOS_PER_SECOND;
    }

    public static void tick() {
        ticks++;
    }

    public static long tickMillis() {
        return ticks * TICK_MILLIS;
    }

    public static boolean blink(long millis) {
        return ((System.nanoTime() - ORIGIN) / (millis * NANOS_PER_MILLI)) % 2 == 0;
    }

    public static final class Ticker {
        private long last = Long.MIN_VALUE / 2;
        private long due = Long.MIN_VALUE / 2;

        public void mark() {
            last = tickMillis();
            due = last;
        }

        public void pace(long delayMillis) {
            long now = tickMillis();
            last = now;
            due = due + delayMillis > now ? due + delayMillis : now + delayMillis;
        }

        public boolean ready() {
            return tickMillis() >= due;
        }

        public boolean elapsed(long millis) {
            return tickMillis() - last >= millis;
        }
    }

    public static final class Stopwatch {
        private long startedAt = System.nanoTime();

        public void reset() {
            startedAt = System.nanoTime();
        }

        public long elapsedMillis() {
            return (System.nanoTime() - startedAt) / NANOS_PER_MILLI;
        }

        public float seconds() {
            return (System.nanoTime() - startedAt) / NANOS_PER_SECOND;
        }

        public boolean elapsed(long millis) {
            return elapsedMillis() >= millis;
        }
    }

    private Time() {}
}
