package com.utilitymod.anubis.util;

/** Shim: desktop only, so the Android reductions are never active. */
public final class MobilePerformance {
    private MobilePerformance() {}

    public static boolean isAndroid() { return false; }
    public static boolean active() { return false; }
    public static int segments(int original, int minimum) { return original; }
    public static int particles(int original, int minimum) { return original; }
    public static int trailSamples(int original, int minimum) { return original; }
    public static int uiSegments(int original, float radiusPixels) { return original; }
}
