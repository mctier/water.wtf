package com.utilitymod.echo.features.impl.render;

import java.util.ArrayList;
import java.util.List;

/** Pure search geometry and evidence thresholds, shared with standalone tests. */
final class FortressScanPolicy {
    record Offset(int x, int z) {}

    private FortressScanPolicy() {}

    static boolean possible(int bricks, int fences, int stairs) {
        return bricks >= 64 || (bricks >= 24 && fences + stairs >= 8);
    }

    static boolean inRange(int x, int z, int centerX, int centerZ, int radius) {
        return Math.abs((long)x - centerX) <= radius && Math.abs((long)z - centerZ) <= radius;
    }

    static List<Offset> offsets(int radius) {
        List<Offset> result = new ArrayList<>();
        for (int ring = 0; ring <= radius; ring++) {
            for (int x = -ring; x <= ring; x++) {
                for (int z = -ring; z <= ring; z++) {
                    if (Math.max(Math.abs(x), Math.abs(z)) == ring) result.add(new Offset(x, z));
                }
            }
        }
        return List.copyOf(result);
    }
}
