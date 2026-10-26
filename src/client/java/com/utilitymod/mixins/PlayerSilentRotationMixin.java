package com.utilitymod.mixins;

import com.utilitymod.echo.handlers.RotationHandler;
import com.utilitymod.echo.rot.LocalPlayerRotationState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Attack yaw redirect (PlayerMixin port onto Yarn 1.21.11): base attack /
 * sweep / sprint-knockback inside {@code PlayerEntity.attack} all read the
 * entity yaw — while silent rotation is active they must read the committed
 * server-side look instead, or the swing direction diverges server side.
 */
@Mixin(PlayerEntity.class)
public class PlayerSilentRotationMixin {
    @Redirect(method = "attack", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;getYaw()F"))
    private float cl$redirectAttackYaw(PlayerEntity playerEntity) {
        return playerEntity == MinecraftClient.getInstance().player
                && LocalPlayerRotationState.silentActive
                ? LocalPlayerRotationState.tickServerYaw
                : playerEntity.getYaw();
    }
}
