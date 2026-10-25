package com.utilitymod.echo.utils.trajectory;

import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public final class TrajectoryPathCollector {

    private TrajectoryPathCollector() {
    }

    public static List<Vec3d> collect(int maxTicks, Supplier<Vec3d> getPosition, BooleanSupplier isDone, Runnable simulateTick) {
        List<Vec3d> points = new ArrayList<>(maxTicks + 1);
        points.add(getPosition.get());

        for (int i = 0; i < maxTicks && !isDone.getAsBoolean(); i++) {
            simulateTick.run();
            points.add(getPosition.get());
        }

        return points;
    }
}
