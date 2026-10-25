package com.utilitymod.echo.utils.trajectory;

import com.utilitymod.echo.utils.Imports;
import com.utilitymod.echo.utils.simulation.PotionSimulation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.Collections;
import java.util.List;

public final class PotionTrajectoryUtils implements Imports {

    private static final float POTION_PITCH_OFFSET = -20.0F;
    private static final double POTION_SPEED = 0.5;

    private PotionTrajectoryUtils() {
    }

    public static List<Vec3d> sample(PlayerEntity player, float partialTick) {
        if (player == null || mc.world == null) return Collections.emptyList();

        Vec3d spawnPos = TrajectoryLaunchUtils.getProjectileSpawnPos(player, partialTick);
        Vec3d velocity = TrajectoryLaunchUtils.getLaunchDirection(player, POTION_PITCH_OFFSET, partialTick)
            .multiply(POTION_SPEED)
            .add(TrajectoryLaunchUtils.getInheritedVelocity(player));

        PotionSimulation simulation = new PotionSimulation(spawnPos, velocity, player);
        return TrajectoryPathCollector.collect(
            TrajectoryConstants.MAX_SIM_TICKS,
            simulation::getPosition,
            simulation::hasHit,
            simulation::simulateTick
        );
    }

    public static List<Vec3d> sample(Vec3d position, Vec3d velocity) {
        if (position == null || velocity == null || mc.world == null) return Collections.emptyList();

        PotionSimulation simulation = new PotionSimulation(position, velocity, null);
        return TrajectoryPathCollector.collect(
            TrajectoryConstants.MAX_SIM_TICKS,
            simulation::getPosition,
            simulation::hasHit,
            simulation::simulateTick
        );
    }

    public static List<Vec3d> sample(Entity potion) {
        if (potion == null || mc.world == null) return Collections.emptyList();

        PotionSimulation simulation = new PotionSimulation(potion.getEntityPos(), potion.getVelocity(), potion);
        return TrajectoryPathCollector.collect(
            TrajectoryConstants.MAX_SIM_TICKS,
            simulation::getPosition,
            simulation::hasHit,
            simulation::simulateTick
        );
    }
}
