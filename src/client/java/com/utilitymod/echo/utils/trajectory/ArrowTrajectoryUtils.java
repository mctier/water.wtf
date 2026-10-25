package com.utilitymod.echo.utils.trajectory;

import com.utilitymod.echo.utils.Imports;
import com.utilitymod.echo.utils.simulation.ArrowSimulation;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

import java.util.Collections;
import java.util.List;

public final class ArrowTrajectoryUtils implements Imports {

    private ArrowTrajectoryUtils() {
    }

    public static List<Vec3d> sample(Vec3d position, Vec3d velocity) {
        if (position == null || velocity == null || mc.world == null) return Collections.emptyList();

        ArrowSimulation simulation = new ArrowSimulation(position, velocity, null);
        return TrajectoryPathCollector.collect(
            TrajectoryConstants.MAX_SIM_TICKS,
            simulation::getPosition,
            simulation::isInGround,
            simulation::simulateTick
        );
    }

    public static List<Vec3d> sample(Entity arrow) {
        if (arrow == null || mc.world == null) return Collections.emptyList();

        ArrowSimulation simulation = new ArrowSimulation(arrow.getEntityPos(), arrow.getVelocity(), arrow);
        return TrajectoryPathCollector.collect(
            TrajectoryConstants.MAX_SIM_TICKS,
            simulation::getPosition,
            simulation::isInGround,
            simulation::simulateTick
        );
    }
}
