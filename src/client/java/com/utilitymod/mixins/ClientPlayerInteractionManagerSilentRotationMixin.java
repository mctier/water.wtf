package com.utilitymod.mixins;

import com.utilitymod.echo.handlers.RotationHandler;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Item-interaction rotation swap (MultiPlayerGameModeMixin port onto Yarn
 * 1.21.11): {@code interactItem} bakes {@code player.getYaw()/getPitch()}
 * into the {@link net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket},
 * so the fields are swapped to the committed server-side rotation for the
 * duration of the call and put right back — the packet carries the look the
 * server expects.
 */
@Mixin(ClientPlayerInteractionManager.class)
public abstract class ClientPlayerInteractionManagerSilentRotationMixin {
    @Unique
    private boolean mpgm$swapActive = false;
    @Unique
    private float mpgm$savedYaw = 0.0F;
    @Unique
    private float mpgm$savedPitch = 0.0F;

    @Inject(method = "interactItem", at = @At("HEAD"))
    private void mpgm$useItemHead(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> callbackInfoReturnable) {
        if (!RotationHandler.isHasSilentRotation()) {
            this.mpgm$swapActive = false;
        } else {
            float serverYaw = RotationHandler.getServerYaw();
            float serverPitch = MathHelper.clamp(RotationHandler.getServerPitch(), -90.0F, 90.0F);
            float yaw = player.getYaw();
            float pitch = player.getPitch();
            float dx = MathHelper.wrapDegrees(serverYaw - yaw);
            float dy = serverPitch - pitch;
            if (Math.abs(dx) < 0.05F && Math.abs(dy) < 0.05F) {
                this.mpgm$swapActive = false;
            } else {
                this.mpgm$savedYaw = yaw;
                this.mpgm$savedPitch = pitch;
                this.mpgm$swapActive = true;
                player.setYaw(serverYaw);
                player.setPitch(serverPitch);
            }
        }
    }

    @Inject(method = "interactItem", at = @At("RETURN"))
    private void mpgm$useItemReturn(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> callbackInfoReturnable) {
        if (this.mpgm$swapActive) {
            this.mpgm$swapActive = false;
            player.setYaw(this.mpgm$savedYaw);
            player.setPitch(this.mpgm$savedPitch);
        }
    }
}
