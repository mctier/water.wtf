package com.utilitymod.anubis.util;

import net.minecraft.world.World;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.dimension.DimensionTypes;

public final class LevelUtil {
   private LevelUtil() {
   }

   public static boolean isOverworld(ClientWorld level) {
      return level.getRegistryKey() == World.OVERWORLD || level.getDimensionEntry().matchesKey(DimensionTypes.OVERWORLD);
   }

   public static boolean isLoaded(ClientWorld level, int chunkX, int chunkZ) {
      return level.getChunkManager().isChunkLoaded(chunkX, chunkZ);
   }
}
