package com.utilitymod.echo.utils.trajectory;

import com.utilitymod.echo.utils.Imports;
import com.utilitymod.echo.utils.simulation.BowSimulation;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.Collections;
import java.util.List;

public final class BowTrajectoryUtils implements Imports {

    private BowTrajectoryUtils() {
    }

    public static List<Vec3d> sample(PlayerEntity player, int chargeTicks, float partialTick) {
        if (player == null || mc.world == null) return Collections.emptyList();

        BowSimulation simulation = new BowSimulation(player, Math.max(0, chargeTicks), partialTick);
        return TrajectoryPathCollector.collect(
            TrajectoryConstants.MAX_SIM_TICKS,
            simulation::getPosition,
            simulation::hasHit,
            simulation::simulateTick
        );
    }
}
