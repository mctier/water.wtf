package com.utilitymod.anubis.util.netherite;

import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.world.World;
import net.minecraft.block.Blocks;
import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.block.BlockState;
import net.minecraft.util.Identifier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.RegistryKey;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.dimension.DimensionTypes;
import net.minecraft.registry.RegistryKeys;

public final class NetheriteChunkTracker {
   static final int MAXIMUM_CANDIDATES = 512;
   private static final int ANCIENT_STATE_ID = Block.getRawIdFromState(Blocks.ANCIENT_DEBRIS.getDefaultState());
   private static final RegistryKey<World> DONUT_NETHER = RegistryKey.of(RegistryKeys.WORLD, Identifier.of(LT.L1, LT.L2));
   private static final Set<NetheriteChunkTracker.Candidate> CANDIDATES = new LinkedHashSet<>();
   private static WeakReference<ClientWorld> lastLevel = new WeakReference<>(null);
   private static RegistryKey<World> lastDimension;
   private static NetheriteChunkTracker.CandidateListener listener;
   private static NetheriteChunkTracker.RevealListener revealListener;

   private NetheriteChunkTracker() {
   }

   public static void onPacketApplied(Packet<?> packet) {
      ClientWorld level = MinecraftClient.getInstance().world;
      if (level != null && isNether(level)) {
         if (packet instanceof ChunkDataS2CPacket chunk) {
            trackLevel(level);

            for (int sectionY : HiddenDebrisScanner.findHiddenSections(chunk.getChunkData(), level.countVerticalSections(), level.getBottomSectionCoord(), ANCIENT_STATE_ID)) {
               add(new NetheriteChunkTracker.Candidate(chunk.getChunkX(), chunk.getChunkZ(), sectionY));
            }
         } else if (packet instanceof BlockUpdateS2CPacket update) {
            onBlockUpdate(update.getPos(), update.getState());
         } else if (packet instanceof ChunkDeltaUpdateS2CPacket sectionUpdate) {
            sectionUpdate.visitUpdates(NetheriteChunkTracker::onBlockUpdate);
         }
      }
   }

   private static void onBlockUpdate(BlockPos pos, BlockState state) {
      if (Block.getRawIdFromState(state) == ANCIENT_STATE_ID) {
         if (revealListener != null) {
            revealListener.onRevealed(pos.toImmutable());
         }

         CANDIDATES.remove(new NetheriteChunkTracker.Candidate(pos.getX() >> 4, pos.getZ() >> 4, pos.getY() >> 4));
      }
   }

   private static void add(NetheriteChunkTracker.Candidate candidate) {
      if (!CANDIDATES.contains(candidate)) {
         if (CANDIDATES.size() >= 512) {
            Iterator<NetheriteChunkTracker.Candidate> oldest = CANDIDATES.iterator();
            oldest.next();
            oldest.remove();
         }

         CANDIDATES.add(candidate);
         if (listener != null) {
            listener.onCandidate(candidate);
         }
      }
   }

   private static void trackLevel(ClientWorld level) {
      RegistryKey<World> dimension = level.getRegistryKey();
      if (level != lastLevel.get() || !dimension.equals(lastDimension)) {
         CANDIDATES.clear();
         lastLevel = new WeakReference<>(level);
         lastDimension = dimension;
      }
   }

   public static boolean isNether(ClientWorld level) {
      RegistryKey<World> dimension = level.getRegistryKey();
      return dimension == World.NETHER || DONUT_NETHER.equals(dimension) || level.getDimensionEntry().matchesKey(DimensionTypes.THE_NETHER);
   }

   public static List<NetheriteChunkTracker.Candidate> snapshot() {
      return MinecraftClient.getInstance().world != lastLevel.get() ? List.of() : List.copyOf(CANDIDATES);
   }

   public static void setListener(NetheriteChunkTracker.CandidateListener candidateListener) {
      listener = candidateListener;
   }

   public static void setRevealListener(NetheriteChunkTracker.RevealListener debrisListener) {
      revealListener = debrisListener;
   }

   public record Candidate(int chunkX, int chunkZ, int sectionY) {
   }

   @FunctionalInterface
   public interface CandidateListener {
      void onCandidate(NetheriteChunkTracker.Candidate var1);
   }

   @FunctionalInterface
   public interface RevealListener {
      void onRevealed(BlockPos var1);
   }


    // cloak literals - runtime PUA carriers (cloak) or functional plain (LT)
    private static final class LT {
            static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x79ec,0xd4e2,0xf43f,0x01c6,0x81da,0xb52e}, new char[]{0xf448,0x01a9,0xd490,0xb542,0x7988,0x81a9}).plainString();
            static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3d48,0xe24c,0x97ce,0xb4bd,0xf6aa,0x650e,0x3ba5,0x75c2,0x5251,0xcd42}, new char[]{0xe23f,0xb4d0,0x657e,0xf6f5,0x75ac,0x97ab,0x3bd1,0x5239,0xcd27,0x3d3a}).plainString();
    }
}
