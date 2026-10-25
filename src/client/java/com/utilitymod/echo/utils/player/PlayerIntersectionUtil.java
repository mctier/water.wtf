package com.utilitymod.echo.utils.player;

import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.List;

import static com.utilitymod.echo.utils.Imports.mc;

public class PlayerIntersectionUtil {
    private static boolean isPlayerInBlock() {
        Box playerBox = mc.player.getBoundingBox();
        BlockPos playerPos = BlockPos.ofFloored(mc.player.getEntityPos());
        for (int x = playerPos.getX() - 1; x <= playerPos.getX() + 1; x++) {
            for (int y = playerPos.getY(); y <= playerPos.getY() + 1; y++) {
                for (int z = playerPos.getZ() - 1; z <= playerPos.getZ() + 1; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!mc.world.getBlockState(pos).isAir() && playerBox.intersects(new Box(pos))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static boolean isPlayerInWeb() {
        Box playerBox = mc.player.getBoundingBox();
        BlockPos playerPosition = BlockPos.ofFloored(mc.player.getEntityPos());

        return getNearbyBlockPositions(playerPosition).stream()
                .anyMatch(pos -> isBlockCobweb(playerBox, pos));
    }

    public static boolean isPlayerInSweetBerryBush(){
        Box playerBox = mc.player.getBoundingBox();
        BlockPos playerPosition = BlockPos.ofFloored(mc.player.getEntityPos());

        return getNearbyBlockPositions(playerPosition).stream()
                .anyMatch(pos -> isBlockSweetBerryBush(playerBox, pos));
    }


    private static List<BlockPos> getNearbyBlockPositions(BlockPos center) {
        List<BlockPos> positions = new ArrayList<>();
        for (int x = center.getX() - 2; x <= center.getX() + 2; x++) {
            for (int y = center.getY() - 1; y <= center.getY() + 4; y++) {
                for (int z = center.getZ() - 2; z <= center.getZ() + 2; z++) {
                    positions.add(new BlockPos(x, y, z));
                }
            }
        }
        return positions;
    }

    private static boolean isBlockCobweb(Box playerBox, BlockPos blockPos) {
        return playerBox.intersects(new Box(blockPos)) && mc.world.getBlockState(blockPos).getBlock() == Blocks.COBWEB;
    }
    private static boolean isBlockSweetBerryBush(Box playerBox, BlockPos blockPos) {
        return playerBox.intersects(new Box(blockPos)) && mc.world.getBlockState(blockPos).getBlock() == Blocks.SWEET_BERRY_BUSH;
    }
}
