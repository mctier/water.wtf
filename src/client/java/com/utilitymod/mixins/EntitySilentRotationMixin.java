package com.utilitymod.mixins;

import com.utilitymod.echo.handlers.RotationHandler;
import com.utilitymod.echo.rot.LocalPlayerRotationState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Entity look vector replacement (EntityMixin port onto Yarn 1.21.11):
 * while silent rotation is active, the local player's eye-look vectors and
 * movement velocity yaw come from the server-side rotation, not the camera —
 * the ray and velocity the server expects are what the engine committed this tick.
 */
@Mixin(Entity.class)
public class EntitySilentRotationMixin {
    private static Vec3d cl$serverLookVector(Entity entity) {
        if (entity != MinecraftClient.getInstance().player) {
            return null;
        }

        if (!RotationHandler.isHasSilentRotation()) {
            return null;
        }

        float yaw = LocalPlayerRotationState.tickServerYaw;
        float pitch = LocalPlayerRotationState.tickServerPitch;
        return RotationHandler.getRotationVector(pitch, yaw);
    }

    @Inject(method = "getRotationVector()Lnet/minecraft/util/math/Vec3d;", at = @At("HEAD"), cancellable = true)
    private void cl$silentLookVector(CallbackInfoReturnable<Vec3d> callbackInfoReturnable) {
        Vec3d vec3d = cl$serverLookVector((Entity) (Object) this);
        if (vec3d != null) {
            callbackInfoReturnable.setReturnValue(vec3d);
        }
    }

    @Redirect(method = "updateVelocity", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getYaw()F"))
    private float cl$redirectMovementYaw(Entity entity) {
        return entity == MinecraftClient.getInstance().player && RotationHandler.isHasSilentRotation()
                ? RotationHandler.getServerYaw()
                : entity.getYaw();
    }
}
