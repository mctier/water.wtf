package com.utilitymod.echo.rot;

/** Clarity port: static facade over the Bus-registered IMovement. */
public final class Movement {
    private Movement() {
    }

    public static boolean isMovementFrozen() {
        IMovement movement = Bus.get(IMovement.class);
        return movement != null && movement.isMovementFrozen();
    }

    public static boolean shouldApplyRotationFix() {
        IMovement movement = Bus.get(IMovement.class);
        return movement != null && movement.shouldApplyRotationFix();
    }

    public static boolean shouldCorrectMovementInput() {
        IMovement movement = Bus.get(IMovement.class);
        return movement != null && movement.shouldCorrectMovementInput();
    }
}
