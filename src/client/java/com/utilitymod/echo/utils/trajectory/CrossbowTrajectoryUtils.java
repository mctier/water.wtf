package com.utilitymod.echo.utils.trajectory;

import com.utilitymod.echo.api.ChargedProjectilesCompat;
import com.utilitymod.echo.utils.Imports;
import com.utilitymod.echo.utils.simulation.CrossbowFireworkSimulation;
import com.utilitymod.echo.utils.simulation.CrossbowSimulation;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.component.type.ChargedProjectilesComponent;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CrossbowTrajectoryUtils implements Imports {

    private CrossbowTrajectoryUtils() {
    }

    public static List<List<Vec3d>> sample(PlayerEntity player, ItemStack crossbowStack, float partialTick) {
        if (player == null || mc.world == null || crossbowStack == null || crossbowStack.isEmpty()) {
            return Collections.emptyList();
        }

        ChargedProjectilesComponent chargedProjectiles = crossbowStack.get(DataComponentTypes.CHARGED_PROJECTILES);

        List<ItemStack> loadedProjectiles = ChargedProjectilesCompat.itemCopies(chargedProjectiles);
        int projectileCount = loadedProjectiles.isEmpty() ? 1 : loadedProjectiles.size();

        float spread = 0.0F;
        var multishot = mc.world.getRegistryManager().getOptionalEntry(Enchantments.MULTISHOT);
        if (multishot.isPresent()) {
            int level = EnchantmentHelper.getLevel(multishot.get(), crossbowStack);
            if (level > 0) {
                spread += level * 10.0F;
            }
        }

        float step = projectileCount == 1 ? 0.0F : 2.0F * spread / (projectileCount - 1);
        float startOffset = (projectileCount - 1) % 2 * step / 2.0F;
        float sign = 1.0F;

        List<List<Vec3d>> trajectories = new ArrayList<>(projectileCount);
        for (int shotIndex = 0; shotIndex < projectileCount; shotIndex++) {
            float shotSpread = startOffset + sign * ((shotIndex + 1) / 2) * step;
            sign = -sign;

            ItemStack projectile = loadedProjectiles.isEmpty() ? ItemStack.EMPTY : loadedProjectiles.get(shotIndex);
            if (!projectile.isEmpty() && projectile.isOf(Items.FIREWORK_ROCKET)) {
                CrossbowFireworkSimulation simulation = new CrossbowFireworkSimulation(player, shotSpread, projectile, shotIndex, partialTick);
                trajectories.add(TrajectoryPathCollector.collect(
                    TrajectoryConstants.MAX_SIM_TICKS,
                    simulation::getPosition,
                    simulation::hasHit,
                    simulation::simulateTick
                ));
            } else {
                CrossbowSimulation simulation = new CrossbowSimulation(player, shotSpread, partialTick);
                trajectories.add(TrajectoryPathCollector.collect(
                    TrajectoryConstants.MAX_SIM_TICKS,
                    simulation::getPosition,
                    simulation::hasHit,
                    simulation::simulateTick
                ));
            }
        }

        return trajectories;
    }

    public static List<Vec3d> sampleFirework(FireworkRocketEntity firework) {
        if (firework == null || mc.world == null) return Collections.emptyList();

        CrossbowFireworkSimulation simulation = new CrossbowFireworkSimulation(firework);
        return TrajectoryPathCollector.collect(
            TrajectoryConstants.MAX_SIM_TICKS,
            simulation::getPosition,
            simulation::hasHit,
            simulation::simulateTick
        );
    }
}
