package com.utilitymod.anubis.util;

import java.util.function.LongConsumer;
import net.minecraft.world.LightType;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.block.BlockState;
import net.minecraft.world.chunk.ChunkNibbleArray;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.light.ChunkLightingView;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos.Mutable;

public final class GeodeGlowScan {
   public static final int MIN_Y = -58;
   public static final int MAX_Y = 30;
   private static final int EDGE_LIGHT = 4;
   private static final int LOW_SECTION = -4;
   private static final int HIGH_SECTION = 1;
   private static final int FIRST_CACHED = -5;
   private static final int CACHED = 8;
   private static final Direction[] SIDES = Direction.values();
   private final ChunkNibbleArray[] layers = new ChunkNibbleArray[72];
   private final Mutable cursor = new Mutable();
   private int chunkX;
   private int chunkZ;

   public int scan(ClientWorld level, WorldChunk chunk, LongConsumer hit) {
      this.chunkX = chunk.getPos().x;
      this.chunkZ = chunk.getPos().z;
      if (!this.cacheLight(level)) {
         return 0;
      }

      ChunkSection[] sections = chunk.getSectionArray();
      int minX = this.chunkX << 4;
      int minZ = this.chunkZ << 4;
      int count = 0;

      for (int sy = -4; sy <= 1; sy++) {
         int index = chunk.sectionCoordToIndex(sy);
         if (index >= 0 && index < sections.length && sections[index] != null && this.nearLight(sy)) {
            ChunkSection section = sections[index];
            int from = Math.max(-58, sy << 4);
            int to = Math.min(30, (sy << 4) + 15);

            for (int y = from; y <= to; y++) {
               for (int z = 0; z < 16; z++) {
                  for (int x = 0; x < 16; x++) {
                     if (open(section.getBlockState(x, y & 15, z))) {
                        int wx = minX + x;
                        int wz = minZ + z;
                        if (this.lightAt(wx, y, wz) == 0 && this.edge(level, wx, y, wz)) {
                           count++;
                           if (hit != null) {
                              hit.accept(BlockPos.asLong(wx, y, wz));
                           }
                        }
                     }
                  }
               }
            }
         }
      }

      return count;
   }

   private boolean cacheLight(ClientWorld level) {
      ChunkLightingView light = level.getLightingProvider().get(LightType.BLOCK);
      boolean any = false;

      for (int dx = -1; dx <= 1; dx++) {
         for (int dz = -1; dz <= 1; dz++) {
            for (int s = 0; s < 8; s++) {
               ChunkNibbleArray layer = light.getLightSection(ChunkSectionPos.from(this.chunkX + dx, -5 + s, this.chunkZ + dz));
               if (layer != null && layer.isUninitialized()) {
                  layer = null;
               }

               this.layers[slot(dx, dz, s)] = layer;
               any |= layer != null;
            }
         }
      }

      return any;
   }

   private boolean nearLight(int sy) {
      for (int dx = -1; dx <= 1; dx++) {
         for (int dz = -1; dz <= 1; dz++) {
            for (int s = sy - 1; s <= sy + 1; s++) {
               if (this.layers[slot(dx, dz, s - -5)] != null) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   private boolean edge(ClientWorld level, int x, int y, int z) {
      boolean litOpening = false;
      int brightest = 0;

      for (Direction side : SIDES) {
         int nx = x + side.getOffsetX();
         int ny = y + side.getOffsetY();
         int nz = z + side.getOffsetZ();
         int value = this.lightAt(nx, ny, nz);
         if (value > 4) {
            return false;
         }

         brightest = Math.max(brightest, value);
         if (value == 4 && !litOpening && open(level.getBlockState(this.cursor.set(nx, ny, nz)))) {
            litOpening = true;
         }
      }

      return brightest == 4 && litOpening;
   }

   private int lightAt(int x, int y, int z) {
      int s = (y >> 4) - -5;
      if (s >= 0 && s < 8) {
         ChunkNibbleArray layer = this.layers[slot((x >> 4) - this.chunkX, (z >> 4) - this.chunkZ, s)];
         return layer == null ? 0 : layer.get(x & 15, y & 15, z & 15);
      } else {
         return 0;
      }
   }

   private static int slot(int dx, int dz, int s) {
      return ((dx + 1) * 3 + dz + 1) * 8 + s;
   }

   private static boolean open(BlockState state) {
      return state.isAir() || state.isOf(Blocks.AMETHYST_CLUSTER);
   }
}
