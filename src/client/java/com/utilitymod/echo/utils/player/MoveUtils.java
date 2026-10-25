package com.utilitymod.echo.utils.player;

import com.utilitymod.echo.utils.Imports;
import lombok.experimental.UtilityClass;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.MathHelper;

import static com.utilitymod.echo.utils.player.PlayerUtils.isMoving;

@UtilityClass
public class MoveUtils implements Imports {

    public static double getDirection() {
        float rotationYaw = mc.player.getYaw();

        if (PlayerInput.DEFAULT.forward()) {
            rotationYaw += 0F;
        } else if (PlayerInput.DEFAULT.backward()) {
            rotationYaw += 180F;
        }

        float forward = 1F;

        if (PlayerInput.DEFAULT.backward()) {
            forward = -0.5F;
        } else if (PlayerInput.DEFAULT.forward()) {
            forward = 0.5F;
        }

        if (PlayerInput.DEFAULT.left()) {
            rotationYaw += 90F * forward;
        }
        if (PlayerInput.DEFAULT.right()) {
            rotationYaw -= 90F * forward;
        }

        return Math.toRadians(rotationYaw);
    }

    public static void strafe(final float speed) {
        if (!isMoving()) {
            return;
        }

        final double yaw = getDirection();
        mc.player.setVelocity(-MathHelper.sin((float) yaw) * speed, mc.player.getVelocity().y, MathHelper.cos((float) yaw) * speed);
    }

    public static void motion(final float speed) {
        if (!isMoving()) {
            return;
        }

        final double yaw = getDirection();
        mc.player.setVelocity(
                mc.player.getVelocity().x - Math.sin(yaw) * speed,
                mc.player.getVelocity().y,
                mc.player.getVelocity().z + Math.cos(yaw) * speed
        );
    }
}
