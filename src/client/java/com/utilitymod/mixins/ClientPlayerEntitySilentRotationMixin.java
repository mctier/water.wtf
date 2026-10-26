package com.utilitymod.mixins;

import com.utilitymod.echo.features.impl.combat.SilentRotations;
import com.utilitymod.echo.handlers.RotationHandler;
import com.utilitymod.echo.rot.LocalPlayerRotationState;
import com.utilitymod.echo.rot.util.RotationGcd;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Clarity silent-rotation commit (LocalPlayerMixin port onto Yarn 1.21.11):
 * the clean static silent state drives the tick HEAD commit. Vanilla
 * {@code sendMovementPackets} runs later inside the same tick, so the swapped
 * fields are what the engine already aimed; the tick RETURN restores the real
 * camera. Rotation packets only carry the silent look, your view never moves.
 */
@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntitySilentRotationMixin {
    @Unique
    private boolean cl$silentApplied;
    @Unique
    private float cl$tickServerYaw;
    @Unique
    private float cl$tickServerPitch;
    @Unique
    private float cl$sYaw;
    @Unique
    private float cl$sPitch;
    @Unique
    private float cl$sHeadYaw;
    @Unique
    private float cl$sBodyYaw;
    @Unique
    private boolean cl$haveLast;
    @Unique
    private float cl$lastYaw;
    @Unique
    private float cl$lastPitch;
    @Unique
    private static final float cl$BODY_FOLLOW = 0.22F;
    @Unique
    private static final float cl$MAX_HEAD_YAW = 50.0F;

    @Inject(method = "tick", at = @At("HEAD"))
    private void cl$silentRotationHead(CallbackInfo callbackInfo) {
        this.cl$silentApplied = false;
        LocalPlayerRotationState.silentActive = false;
        ClientPlayerEntity localPlayer = (ClientPlayerEntity) (Object) this;
        if (!RotationHandler.isHasSilentRotation()) {
            this.cl$haveLast = true;
            this.cl$lastYaw = localPlayer.getYaw();
            this.cl$lastPitch = localPlayer.getPitch();
        } else {
            if (!this.cl$haveLast) {
                this.cl$lastYaw = localPlayer.getYaw();
                this.cl$lastPitch = localPlayer.getPitch();
                this.cl$haveLast = true;
            }

            float silentServerYaw = RotationHandler.getServerYaw();
            float silentServerPitch = MathHelper.clamp(RotationHandler.getServerPitch(), -90.0F, 90.0F);
            if (Float.isFinite(silentServerYaw) && Float.isFinite(silentServerPitch)) {
                if (RotationGcd.enabled()) {
                    this.cl$tickServerYaw = RotationGcd.snapYaw(this.cl$lastYaw, silentServerYaw);
                    this.cl$tickServerPitch = RotationGcd.snapPitch(this.cl$lastPitch, silentServerPitch);
                } else {
                    this.cl$tickServerYaw = silentServerYaw;
                    this.cl$tickServerPitch = silentServerPitch;
                }

                this.cl$lastYaw = this.cl$tickServerYaw;
                this.cl$lastPitch = this.cl$tickServerPitch;
                this.cl$sYaw = localPlayer.getYaw();
                this.cl$sPitch = localPlayer.getPitch();
                this.cl$sHeadYaw = localPlayer.headYaw;
                this.cl$sBodyYaw = localPlayer.bodyYaw;
                LocalPlayerRotationState.tickServerYaw = this.cl$tickServerYaw;
                LocalPlayerRotationState.tickServerPitch = this.cl$tickServerPitch;
                LocalPlayerRotationState.realYaw = this.cl$sYaw;
                LocalPlayerRotationState.silentActive = true;
                // 1.21.11 has no tickCount here — the mixin keeps the counter itself
                LocalPlayerRotationState.tickServerTick++;
                localPlayer.setYaw(this.cl$tickServerYaw);
                localPlayer.setPitch(this.cl$tickServerPitch);
                this.cl$silentApplied = true;
            } else {
                this.cl$lastYaw = localPlayer.getYaw();
                this.cl$lastPitch = localPlayer.getPitch();
            }
        }
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void cl$silentRotationReturn(CallbackInfo callbackInfo) {
        LocalPlayerRotationState.silentActive = false;
        if (this.cl$silentApplied) {
            this.cl$silentApplied = false;
            ClientPlayerEntity localPlayer = (ClientPlayerEntity) (Object) this;
            localPlayer.setYaw(this.cl$sYaw);
            localPlayer.setPitch(this.cl$sPitch);
            localPlayer.lastYaw = this.cl$sYaw;
            localPlayer.lastPitch = this.cl$sPitch;
            float factor = SilentRotations.showModel ? this.cl$tickServerYaw : this.cl$sYaw;
            localPlayer.headYaw = factor;
            float factor2 = this.cl$sBodyYaw + MathHelper.wrapDegrees(factor - this.cl$sBodyYaw) * cl$BODY_FOLLOW;
            float wrapDegrees2 = MathHelper.wrapDegrees(factor - factor2);
            if (wrapDegrees2 > cl$MAX_HEAD_YAW) {
                factor2 = factor - cl$MAX_HEAD_YAW;
            } else if (wrapDegrees2 < -cl$MAX_HEAD_YAW) {
                factor2 = factor + cl$MAX_HEAD_YAW;
            }
            localPlayer.bodyYaw = factor2;
            localPlayer.lastHeadYaw = factor;
            localPlayer.lastBodyYaw = factor2;
        }
    }
}
