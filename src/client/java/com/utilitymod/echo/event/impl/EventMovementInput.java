package com.utilitymod.echo.event.impl;

import com.utilitymod.echo.event.Event;

/**
 * Mutable movement input event. Listeners can change the booleans to
 * alter movement key states for the tick.
 */
public class EventMovementInput extends Event {
	public boolean forward;
	public boolean back;
	public boolean left;
	public boolean right;
	public boolean jump;
	public boolean sneak;
	public boolean sprint;
	public boolean originalForward;
	public boolean originalBack;
	public boolean originalLeft;
	public boolean originalRight;
	public boolean originalJump;
	public boolean originalSneak;
	public boolean originalSprint;

	public float forwardAmount;
	public float sidewaysAmount;

	public boolean forceMovement = false;

	public EventMovementInput(boolean forward, boolean back, boolean left, boolean right, boolean jump, boolean sneak, boolean sprint) {
		this.forward = forward;
		this.back = back;
		this.left = left;
		this.right = right;
		this.jump = jump;
		this.sneak = sneak;
		this.sprint = sprint;
		this.originalForward = forward;
		this.originalBack = back;
		this.originalLeft = left;
		this.originalRight = right;
		this.originalJump = jump;
		this.originalSneak = sneak;
		this.originalSprint = sprint;

		this.forwardAmount = getMovementMultiplier(forward, back);
		this.sidewaysAmount = getMovementMultiplier(left, right);
	}

	public static float getMovementMultiplier(boolean positive, boolean negative) {
		if (positive == negative) {
			return 0.0f;
		}
		return positive ? 1.0f : -1.0f;
	}

	public void syncBooleansFromFloats() {
		this.forward = this.forwardAmount > 0;
		this.back = this.forwardAmount < 0;
		this.left = this.sidewaysAmount > 0;
		this.right = this.sidewaysAmount < 0;
	}

	public void syncFloatsFromBooleans() {
		this.forwardAmount = getMovementMultiplier(this.forward, this.back);
		this.sidewaysAmount = getMovementMultiplier(this.left, this.right);
	}
}
