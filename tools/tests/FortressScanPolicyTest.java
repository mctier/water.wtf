package com.utilitymod.echo.features.impl.render;

import java.util.HashSet;

public final class FortressScanPolicyTest {
    private static int checks;
    private static void check(boolean ok) { checks++; if (!ok) throw new AssertionError("check " + checks); }

    public static void main(String[] args) {
        check(!FortressScanPolicy.possible(0, 0, 0));
        check(!FortressScanPolicy.possible(63, 0, 0));
        check(FortressScanPolicy.possible(64, 0, 0));
        check(!FortressScanPolicy.possible(23, 8, 0));
        check(!FortressScanPolicy.possible(24, 7, 0));
        check(FortressScanPolicy.possible(24, 8, 0));
        check(FortressScanPolicy.possible(24, 0, 8));
        check(FortressScanPolicy.possible(24, 4, 4));
        check(!FortressScanPolicy.possible(0, 100, 100));
        for (int radius : new int[]{2, 10, 24}) {
            var offsets = FortressScanPolicy.offsets(radius);
            check(offsets.size() == (radius * 2 + 1) * (radius * 2 + 1));
            check(new HashSet<>(offsets).size() == offsets.size());
            check(offsets.getFirst().x() == 0 && offsets.getFirst().z() == 0);
            int lastRing = -1;
            for (var offset : offsets) {
                int ring = Math.max(Math.abs(offset.x()), Math.abs(offset.z()));
                check(ring >= lastRing && ring <= radius);
                lastRing = ring;
                check(FortressScanPolicy.inRange(offset.x()-40,offset.z()-80,-40,-80,radius));
            }
            // Persistent cursor covers every position, including negative/no-hit positions.
            var visited = new HashSet<FortressScanPolicy.Offset>();
            int cursor = 0;
            for (int tick = 0; tick < offsets.size(); tick++) {
                visited.add(offsets.get(cursor)); cursor = (cursor + 1) % offsets.size();
            }
            check(visited.size() == offsets.size() && cursor == 0);
        }
        check(!FortressScanPolicy.inRange(3,0,0,0,2));
        check(!FortressScanPolicy.inRange(0,-3,0,0,2));
        check(FortressScanPolicy.inRange(-2,2,0,0,2));
        System.out.println("PASS fortress policy checks=" + checks);
    }
}
