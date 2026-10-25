package com.utilitymod.echo.features.impl.movement.combohelpers;

import com.utilitymod.echo.Echo;
import com.utilitymod.echo.event.impl.EventMovementInput;
import com.utilitymod.echo.event.impl.EventStartAttack;
import com.utilitymod.echo.features.impl.movement.ComboHelper;
import com.utilitymod.echo.handlers.InputHandler;
import com.utilitymod.echo.utils.Imports;
import net.minecraft.util.PlayerInput;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.hit.EntityHitResult;

public abstract class ComboMode implements Imports {

    protected int attackTick = -1;
    /** The enemy we last hit; the Distance helper walks us to/holds the configured gap from it. */
    protected LivingEntity target;
    /** Set true inside applyMovement when the mode is driving forward/back for a reset this tick,
     *  so the Distance helper doesn't fight it. Left false => Distance owns forward/back. */
    protected boolean ownFB;

    public void onEnable() {
        attackTick = -1;
        target = null;
    }

    public void onDisable() {
        attackTick = -1;
        target = null;
    }

    public void onAttack(EventStartAttack e) {
        if (mc.player == null || mc.world == null) return;
        if (com.utilitymod.echo.api.MinecraftCompat.getScreen() != null) return;

        if (!validateAttack(e)) return;

        if (!(mc.crosshairTarget instanceof EntityHitResult hit)) return;
        if (!(hit.getEntity() instanceof LivingEntity target)) return;

        ComboHelper comboHelper = Echo.featureManager.getFeatureByClass(ComboHelper.class);
        if (comboHelper != null && !comboHelper.isEntityAllowed(target)) return;

        if (!validateTarget(target, hit)) return;

        this.target = target;
        attackTick = mc.player.age;
        applyMovementInstantly();
    }

    public void onMovement(EventMovementInput e) {
        if (mc.player == null || mc.world == null) return;
        if (com.utilitymod.echo.api.MinecraftCompat.getScreen() != null) return;
        if (mc.player.isDead()) return;
        if (attackTick == -1 || mc.player.age < attackTick) return;

        ownFB = false;
        applyMovement(e);
        applyDistance(e);
    }

    /** Active Distance helper: steer forward/back to reach and hold the configured gap (1..3 blocks)
     *  from {@link #target}, unless the current mode is driving forward/back for a reset this tick. */
    protected void applyDistance(EventMovementInput e) {
        if (ownFB || target == null || !target.isAlive() || target.isRemoved()) return;
        double want = ComboHelper.distanceSetting.getValue();
        double d = mc.player.distanceTo(target);
        if (d > 8.0) return; // disengaged — let the player move freely
        double margin = 0.4;
        if (d > want + margin) {        // too far -> close in
            e.forward = true;
            e.back = false;
        } else if (d < want - margin) { // too close -> back off
            e.back = true;
            e.forward = false;
        } else {                        // at the gap -> hold position
            e.forward = false;
            e.back = false;
        }
    }

    protected boolean validateAttack(EventStartAttack e) {
        return true;
    }

    protected boolean validateTarget(LivingEntity target, EntityHitResult hit) {
        return true;
    }

    protected abstract void applyMovement(EventMovementInput e);

    private void applyMovementInstantly() {
        if (mc.player.input == null) return;

        net.minecraft.client.input.Input input = mc.player.input;
        PlayerInput keys = input.playerInput;

        EventMovementInput movement = new EventMovementInput(
            keys.forward(),
            keys.backward(),
            keys.left(),
            keys.right(),
            keys.jump(),
            keys.sneak(),
            keys.sprint()
        );

        applyMovement(movement);
        movement.syncFloatsFromBooleans();

        input.playerInput = (new PlayerInput(
            movement.forward,
            movement.back,
            movement.left,
            movement.right,
            movement.jump,
            movement.sneak,
            movement.sprint
        ));
        com.utilitymod.echo.api.Accessors.setMovementVector(input, new Vec2f(movement.sidewaysAmount, movement.forwardAmount));
    }

    protected boolean isForwardKeyDown() {
        return InputHandler.isBindDown(com.utilitymod.echo.api.Accessors.getKey(mc.options.forwardKey).getCode());
    }

    protected boolean isBackKeyDown() {
        return InputHandler.isBindDown(com.utilitymod.echo.api.Accessors.getKey(mc.options.backKey).getCode());
    }

    protected boolean isJumpKeyDown() {
        return InputHandler.isBindDown(com.utilitymod.echo.api.Accessors.getKey(mc.options.jumpKey).getCode());
    }

    protected boolean isLeftKeyDown() {
        return InputHandler.isBindDown(com.utilitymod.echo.api.Accessors.getKey(mc.options.leftKey).getCode());
    }

    protected boolean isRightKeyDown() {
        return InputHandler.isBindDown(com.utilitymod.echo.api.Accessors.getKey(mc.options.rightKey).getCode());
    }

    /** Within the active tap window (reaction .. reaction+duration), honouring Ground Only. */
    protected boolean inWindow(int start, int duration, boolean groundOnly) {
        int t = mc.player.age - attackTick;
        if (t < start || t >= start + duration) return false;
        return !(groundOnly && !mc.player.isOnGround());
    }

    /** Restore the four horizontal movement fields to the player's real key state. */
    protected void passthrough(EventMovementInput e) {
        e.forward = isForwardKeyDown();
        e.back = isBackKeyDown();
        e.left = isLeftKeyDown();
        e.right = isRightKeyDown();
    }
}
