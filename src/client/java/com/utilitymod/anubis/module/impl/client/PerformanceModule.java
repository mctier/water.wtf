package com.utilitymod.anubis.module.impl.client;

/** Shim: Anubis' Performance module. Budgeted (optimized) scanning is always on here. */
public final class PerformanceModule {
    private PerformanceModule() {}

    public static boolean optimizationsEnabled() { return true; }

    public static boolean mobileEffectsEnabled() { return false; }

    /** Per-tick scan budget shared by the chunk/hole scanners. */
    public static long scanBudgetNanos() { return 3_000_000L; }
}
