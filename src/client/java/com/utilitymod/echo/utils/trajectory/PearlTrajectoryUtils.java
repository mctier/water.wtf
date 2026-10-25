package com.utilitymod.echo.utils.trajectory;

import com.utilitymod.echo.utils.Imports;
import com.utilitymod.echo.utils.simulation.PearlSimulation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.Collections;
import java.util.List;

public final class PearlTrajectoryUtils implements Imports {

    private static final double PEARL_SPEED = 1.5;

    private PearlTrajectoryUtils() {
    }

    public static List<Vec3d> sample(PlayerEntity player, float partialTick) {
        if (player == null || mc.world == null) return Collections.emptyList();

        Vec3d spawnPos = TrajectoryLaunchUtils.getProjectileSpawnPos(player, partialTick);
        Vec3d velocity = TrajectoryLaunchUtils.getLaunchDirection(player, partialTick)
            .multiply(PEARL_SPEED)
            .add(TrajectoryLaunchUtils.getInheritedVelocity(player));

        PearlSimulation simulation = new PearlSimulation(spawnPos, velocity, player);
        return TrajectoryPathCollector.collect(
            TrajectoryConstants.MAX_SIM_TICKS,
            simulation::getPosition,
            simulation::hasHit,
            simulation::simulateTick
        );
    }

    public static List<Vec3d> sample(Vec3d position, Vec3d velocity) {
        if (position == null || velocity == null || mc.world == null) return Collections.emptyList();

        PearlSimulation simulation = new PearlSimulation(position, velocity, null);
        return TrajectoryPathCollector.collect(
            TrajectoryConstants.MAX_SIM_TICKS,
            simulation::getPosition,
            simulation::hasHit,
            simulation::simulateTick
        );
    }

    public static List<Vec3d> sample(Entity pearl) {
        if (pearl == null || mc.world == null) return Collections.emptyList();

        PearlSimulation simulation = new PearlSimulation(pearl.getEntityPos(), pearl.getVelocity(), pearl);
        return TrajectoryPathCollector.collect(
            TrajectoryConstants.MAX_SIM_TICKS,
            simulation::getPosition,
            simulation::hasHit,
            simulation::simulateTick
        );
    }
}
