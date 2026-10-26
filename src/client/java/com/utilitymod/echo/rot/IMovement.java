package com.utilitymod.echo.rot;

/** Clarity port: movement-gate capability answered by the combat side. */
public interface IMovement extends Capability {
    boolean isMovementFrozen();

    boolean shouldApplyRotationFix();

    boolean shouldCorrectMovementInput();
}
