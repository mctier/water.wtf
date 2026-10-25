package com.utilitymod.echo.features.impl.combat;

public final class AutoMacePolicyTest {
    private static int checks;
    private static void check(boolean value) {
        if (!value) throw new AssertionError("check " + (checks + 1));
        checks++;
    }

    public static void main(String[] args) {
        AutoMacePolicy policy = new AutoMacePolicy();
        check(policy.ready(10, 0));
        policy.mark(10);
        check(policy.attempted(10));
        check(!policy.ready(10, 0));
        check(policy.ready(11, 0));
        check(!policy.ready(11, 2));
        check(policy.ready(12, 2));
        check(policy.ready(0, 2));
        policy.reset();
        check(policy.ready(10, 20));
        check(!policy.attempted(10));
        for (int p = 0; p < 2; p++) for (int m = 0; m < 2; m++) {
            check(AutoMacePolicy.allowed(true, false, p == 1, m == 1) == (p == 1));
            check(AutoMacePolicy.allowed(false, true, p == 1, m == 1) == (m == 1));
            check(!AutoMacePolicy.allowed(false, false, p == 1, m == 1));
        }
        check(AutoMacePolicy.preferDensity(true, 0, 7));
        check(AutoMacePolicy.preferDensity(false, 7, 7));
        check(!AutoMacePolicy.preferDensity(false, 6.9, 7));
        for (int roll = 0; roll < 100; roll++) {
            check(!AutoMacePolicy.chance(0, roll));
            check(AutoMacePolicy.chance(100, roll));
            check(AutoMacePolicy.chance(35, roll) == (roll < 35));
        }
        check(!AutoMacePolicy.chance(100, -1));
        check(!AutoMacePolicy.chance(100, 100));
        System.out.println("PASS: " + checks + " AutoMace policy assertions");
    }
}
