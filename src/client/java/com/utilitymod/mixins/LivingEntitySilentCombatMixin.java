package com.utilitymod.mixins;

import com.utilitymod.echo.handlers.RotationHandler;
import com.utilitymod.echo.rot.LocalPlayerRotationState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Combat movement yaw redirects (LivingEntityMixin port onto Yarn 1.21.11):
 * the sprint-jump boost direction and sweep/knockback attack direction both
 * take yaw from the entity — while silent rotation is active they must take
 * the committed server-side look or your body moves one way while the server
 * thinks you face the target.
 */
@Mixin(LivingEntity.class)
public class LivingEntitySilentCombatMixin {
    @Redirect(method = "jump", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;getYaw()F"))
    private float cl$redirectJumpYaw(LivingEntity livingEntity) {
        return livingEntity == MinecraftClient.getInstance().player
                && LocalPlayerRotationState.silentActive
                ? LocalPlayerRotationState.tickServerYaw
                : livingEntity.getYaw();
    }

    @Redirect(method = "knockbackTarget", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;getYaw()F"))
    private float cl$redirectKnockbackYaw(LivingEntity livingEntity) {
        return livingEntity == MinecraftClient.getInstance().player
                && LocalPlayerRotationState.silentActive
                ? LocalPlayerRotationState.tickServerYaw
                : livingEntity.getYaw();
    }
}
