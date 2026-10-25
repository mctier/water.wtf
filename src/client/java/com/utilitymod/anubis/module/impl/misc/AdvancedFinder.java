package com.utilitymod.anubis.module.impl.misc;

import com.utilitymod.anubis.util.Access;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.utilitymod.anubis.chat.ModuleChat;
import com.utilitymod.anubis.util.netherite.NetheriteChunkTracker;
import net.minecraft.item.ItemStack;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.BlockState;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.network.PendingUpdateManager;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.util.math.BlockPos.Mutable;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action;

final class AdvancedFinder {
   private static final double REACH = 5.0;
   private static final double PROBES_PER_TICK = 18.0;
   private static final double BURST = 600.0;
   private static final int RECOUNT_INTERVAL_TICKS = 10;
   private static final int MAX_REVEALED = 256;
   private final NetheriteFinderModule module;
   private final Map<NetheriteChunkTracker.Candidate, AdvancedFinder.Scan> scans = new HashMap<>();
   private final Set<NetheriteChunkTracker.Candidate> done = new HashSet<>();
   private final Set<BlockPos> revealed = new LinkedHashSet<>();
   private boolean armed;
   private int ticks;
   private double budget;
   private int recountCooldown;
   private static final int MISSING_REFRESH_TICKS = 10;
   private int handWarning;
   private String lastReason = "";
   private ClientWorld level;

   AdvancedFinder(NetheriteFinderModule module) {
      this.module = module;
   }

   boolean armed() {
      return this.armed;
   }

   Collection<BlockPos> revealed() {
      return this.revealed;
   }

   boolean isDone(NetheriteChunkTracker.Candidate section) {
      return this.done.contains(section);
   }

   float progress(NetheriteChunkTracker.Candidate section) {
      AdvancedFinder.Scan scan = this.armed ? this.scans.get(section) : null;
      return scan == null ? -1.0F : fraction(scan);
   }

   private static float fraction(AdvancedFinder.Scan scan) {
      return scan.total == 0 ? 1.0F : Math.min(1.0F, (float)scan.sent / scan.total);
   }

   void forEachProbe(Vec3d eye, double radius, int limit, float missingFrom, AdvancedFinder.ProbeVisitor visitor) {
      if (this.armed) {
         double radiusSq = radius * radius;
         int emitted = 0;

         for (AdvancedFinder.Scan scan : this.scans.values()) {
            if (!(fraction(scan) >= missingFrom)) {
               int baseX = scan.section.chunkX() << 4;
               int baseY = scan.section.sectionY() << 4;
               int baseZ = scan.section.chunkZ() << 4;

               for (int index = 0; index < 4096; index++) {
                  int at = scan.stamp[index];
                  if (at != 0) {
                     int x = baseX + (index & 15);
                     int y = baseY + (index >> 8);
                     int z = baseZ + (index >> 4 & 15);
                     double dx = x + 0.5 - eye.x;
                     double dy = y + 0.5 - eye.y;
                     double dz = z + 0.5 - eye.z;
                     if (!(dx * dx + dy * dy + dz * dz > radiusSq)) {
                        visitor.visit(x, y, z, this.ticks - at + 1);
                        if (++emitted >= limit) {
                           return;
                        }
                     }
                  }
               }
            }
         }
      }
   }

   void forEachMissing(Vec3d eye, double radius, int limit, float missingFrom, AdvancedFinder.MissingVisitor visitor) {
      if (this.armed) {
         double radiusSq = radius * radius;
         int emitted = 0;

         for (AdvancedFinder.Scan scan : this.scans.values()) {
            if (!(fraction(scan) < missingFrom)) {
               this.refreshMissing(scan);
               int baseX = scan.section.chunkX() << 4;
               int baseY = scan.section.sectionY() << 4;
               int baseZ = scan.section.chunkZ() << 4;

               for (int i = 0; i < scan.missingCount; i++) {
                  int index = scan.missing[i];
                  if (!scan.probed.get(index)) {
                     int x = baseX + (index & 15);
                     int y = baseY + (index >> 8);
                     int z = baseZ + (index >> 4 & 15);
                     double dx = x + 0.5 - eye.x;
                     double dy = y + 0.5 - eye.y;
                     double dz = z + 0.5 - eye.z;
                     if (!(dx * dx + dy * dy + dz * dz > radiusSq)) {
                        visitor.visit(x, y, z);
                        if (++emitted >= limit) {
                           return;
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private void refreshMissing(AdvancedFinder.Scan scan) {
      if (this.level != null && this.ticks - scan.missingAt >= 10) {
         scan.missingAt = this.ticks;
         int baseX = scan.section.chunkX() << 4;
         int baseY = scan.section.sectionY() << 4;
         int baseZ = scan.section.chunkZ() << 4;
         Mutable cursor = new Mutable();
         int count = 0;

         for (int index = scan.probed.nextClearBit(0); index < 4096; index = scan.probed.nextClearBit(index + 1)) {
            cursor.set(baseX + (index & 15), baseY + (index >> 8), baseZ + (index >> 4 & 15));
            if (worthProbing(this.level, cursor)) {
               scan.missing[count++] = index;
            }
         }

         scan.missingCount = count;
      }
   }

   void arm() {
      this.armed = true;
      this.lastReason = "";
   }

   void disarm(MinecraftClient mc) {
      this.armed = false;
      this.lastReason = "";
   }

   void reset(MinecraftClient mc) {
      this.disarm(mc);
      this.scans.clear();
      this.done.clear();
      this.revealed.clear();
      this.budget = 0.0;
      this.level = null;
   }

   boolean onRevealed(BlockPos pos) {
      NetheriteChunkTracker.Candidate section = new NetheriteChunkTracker.Candidate(pos.getX() >> 4, pos.getZ() >> 4, pos.getY() >> 4);
      if (!this.revealed.add(pos)) {
         return false;
      }

      if (this.revealed.size() > 256) {
         Iterator<BlockPos> oldest = this.revealed.iterator();
         oldest.next();
         oldest.remove();
      }

      if (this.armed && this.scans.remove(section) != null) {
         this.done.add(section);
         ModuleChat.send(
            this.module,
            ModuleChat.body().text(LT.L1).good(LT.L2).text(LT.L3).coords(pos.getX(), pos.getY(), pos.getZ())
         );
      }

      return true;
   }

   void maintain(MinecraftClient mc) {
      ClientWorld world = mc.world;
      if (world == this.level) {
         if (world != null) {
            this.revealed.removeIf(pos -> !world.getBlockState(pos).isOf(Blocks.ANCIENT_DEBRIS));
         }
      } else {
         this.scans.clear();
         this.done.clear();
         this.revealed.clear();
         this.level = world;
      }
   }

   void tick(MinecraftClient mc) {
      if (this.armed) {
         ClientPlayerEntity player = mc.player;
         ClientWorld world = mc.world;
         if (player != null && world != null && player.networkHandler != null && mc.interactionManager != null) {
            this.ticks++;
            this.budget = Math.min(600.0, this.budget + 18.0);
            if (this.handWarning > 0) {
               this.handWarning--;
            }

            if (!NetheriteChunkTracker.isNether(world)) {
               this.hold(LT.L4);
            } else {
               this.lastReason = "";
               if (mc.currentScreen == null && !player.isUsingItem() && !mc.interactionManager.isBreakingBlock()) {
                  Vec3d eye = player.getEyePos();
                  this.refreshScans(world, eye);
                  if (!this.scans.isEmpty() && !(this.budget < 1.0)) {
                     List<AdvancedFinder.Probe> probes = this.collect(world, eye);
                     if (!probes.isEmpty()) {
                        int selected = player.getInventory().getSelectedSlot();
                        int slot = probeSlot(player, selected);
                        if (slot < 0) {
                           if (this.handWarning <= 0) {
                              this.handWarning = 200;
                              ModuleChat.send(this.module, ModuleChat.body().text(LT.L5));
                           }
                        } else {
                           if (slot != selected) {
                              player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
                           }

                           BlockPos last = null;

                           for (AdvancedFinder.Probe probe : probes) {
                              if (this.budget < 1.0) {
                                 break;
                              }

                              sendAction(player, world, Action.START_DESTROY_BLOCK, probe.pos());
                              probe.scan().probed.set(probe.index());
                              probe.scan().sent++;
                              probe.scan().stamp[probe.index()] = this.ticks;
                              probe.scan().dirty = true;
                              this.budget--;
                              last = probe.pos();
                           }

                           if (last != null) {
                              sendAction(player, world, Action.ABORT_DESTROY_BLOCK, last);
                           }

                           if (slot != selected) {
                              player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(selected));
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private void refreshScans(ClientWorld world, Vec3d eye) {
      Set<NetheriteChunkTracker.Candidate> live = new HashSet<>(NetheriteChunkTracker.snapshot());
      this.scans.keySet().retainAll(live);

      for (NetheriteChunkTracker.Candidate candidate : live) {
         if (!this.done.contains(candidate) && !this.scans.containsKey(candidate) && distanceToSectionSq(eye, candidate) <= 25.0) {
            this.scans.put(candidate, new AdvancedFinder.Scan(candidate, countProbeable(world, candidate)));
         }
      }

      if (this.recountCooldown-- <= 0) {
         this.recountCooldown = 10;
         Iterator<AdvancedFinder.Scan> iterator = this.scans.values().iterator();

         while (iterator.hasNext()) {
            AdvancedFinder.Scan scan = iterator.next();
            if (scan.dirty) {
               scan.dirty = false;
               if (!hasUnprobed(world, scan)) {
                  iterator.remove();
                  this.done.add(scan.section);
                  NetheriteChunkTracker.Candidate s = scan.section;
                  ModuleChat.send(
                     this.module,
                     ModuleChat.body().text(LT.L6).good(LT.L7).text(LT.L3).coords(s.chunkX() * 16 + 8, s.sectionY() * 16 + 8, s.chunkZ() * 16 + 8)
                  );
               }
            }
         }
      }
   }

   private List<AdvancedFinder.Probe> collect(ClientWorld world, Vec3d eye) {
      List<AdvancedFinder.Probe> coarse = new ArrayList<>();
      List<AdvancedFinder.Probe> fine = new ArrayList<>();
      int span = (int)Math.ceil(5.0);
      int cx = (int)Math.floor(eye.x);
      int cy = (int)Math.floor(eye.y);
      int cz = (int)Math.floor(eye.z);
      Mutable cursor = new Mutable();

      for (AdvancedFinder.Scan scan : this.scans.values()) {
         int baseX = scan.section.chunkX() << 4;
         int baseY = scan.section.sectionY() << 4;
         int baseZ = scan.section.chunkZ() << 4;
         int minX = Math.max(baseX, cx - span);
         int maxX = Math.min(baseX + 15, cx + span);
         int minY = Math.max(baseY, cy - span);
         int maxY = Math.min(baseY + 15, cy + span);
         int minZ = Math.max(baseZ, cz - span);
         int maxZ = Math.min(baseZ + 15, cz + span);

         for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
               for (int z = minZ; z <= maxZ; z++) {
                  int lx = x & 15;
                  int ly = y & 15;
                  int lz = z & 15;
                  int index = ly << 8 | lz << 4 | lx;
                  if (!scan.probed.get(index)) {
                     cursor.set(x, y, z);
                     double distanceSq = distanceToBlockSq(eye, cursor);
                     if (!(distanceSq > 25.0)) {
                        if (!worthProbing(world, cursor)) {
                           scan.probed.set(index);
                        } else {
                           AdvancedFinder.Probe probe = new AdvancedFinder.Probe(scan, index, cursor.toImmutable(), distanceSq);
                           if (lx % 3 == 1 && ly % 3 == 1 && lz % 3 == 1) {
                              coarse.add(probe);
                           } else {
                              fine.add(probe);
                           }
                        }
                     }
                  }
               }
            }
         }
      }

      coarse.sort(Comparator.comparingDouble(AdvancedFinder.Probe::distanceSq));
      fine.sort(Comparator.comparingDouble(AdvancedFinder.Probe::distanceSq));
      coarse.addAll(fine);
      return coarse;
   }

   private static int countProbeable(ClientWorld world, NetheriteChunkTracker.Candidate section) {
      int baseX = section.chunkX() << 4;
      int baseY = section.sectionY() << 4;
      int baseZ = section.chunkZ() << 4;
      Mutable cursor = new Mutable();
      int count = 0;

      for (int index = 0; index < 4096; index++) {
         cursor.set(baseX + (index & 15), baseY + (index >> 8), baseZ + (index >> 4 & 15));
         if (worthProbing(world, cursor)) {
            count++;
         }
      }

      return count;
   }

   private static boolean hasUnprobed(ClientWorld world, AdvancedFinder.Scan scan) {
      int baseX = scan.section.chunkX() << 4;
      int baseY = scan.section.sectionY() << 4;
      int baseZ = scan.section.chunkZ() << 4;
      Mutable cursor = new Mutable();

      for (int index = scan.probed.nextClearBit(0); index < 4096; index = scan.probed.nextClearBit(index + 1)) {
         cursor.set(baseX + (index & 15), baseY + (index >> 8), baseZ + (index >> 4 & 15));
         if (worthProbing(world, cursor)) {
            return true;
         }
      }

      return false;
   }

   private static boolean worthProbing(ClientWorld world, BlockPos pos) {
      BlockState state = world.getBlockState(pos);
      return !state.isAir() && state.getFluidState().isEmpty() && !state.isOf(Blocks.ANCIENT_DEBRIS)
         ? state.isOpaqueFullCube() && state.getHardness(world, pos) > 0.0F
         : false;
   }

   private void hold(String reason) {
      if (!reason.equals(this.lastReason)) {
         this.lastReason = reason;
         ModuleChat.send(this.module, ModuleChat.body().text(reason));
      }
   }

   private static void sendAction(ClientPlayerEntity player, ClientWorld world, Action action, BlockPos pos) {
      PendingUpdateManager handler = Access.pendingUpdates(world);
      PendingUpdateManager sequence = handler.incrementSequence();

      try {
         player.networkHandler.sendPacket(new PlayerActionC2SPacket(action, pos, Direction.UP, sequence.getSequence()));
      } catch (Throwable var9) {
         if (sequence != null) {
            try {
               sequence.close();
            } catch (Throwable var8) {
               var9.addSuppressed(var8);
            }
         }

         throw var9;
      }

      if (sequence != null) {
         sequence.close();
      }
   }

   private static double distanceToSectionSq(Vec3d from, NetheriteChunkTracker.Candidate section) {
      double minX = section.chunkX() * 16.0;
      double minY = section.sectionY() * 16.0;
      double minZ = section.chunkZ() * 16.0;
      double dx = Math.max(0.0, Math.max(minX - from.x, from.x - (minX + 16.0)));
      double dy = Math.max(0.0, Math.max(minY - from.y, from.y - (minY + 16.0)));
      double dz = Math.max(0.0, Math.max(minZ - from.z, from.z - (minZ + 16.0)));
      return dx * dx + dy * dy + dz * dz;
   }

   private static double distanceToBlockSq(Vec3d from, BlockPos pos) {
      double dx = Math.max(0.0, Math.max(pos.getX() - from.x, from.x - (pos.getX() + 1.0)));
      double dy = Math.max(0.0, Math.max(pos.getY() - from.y, from.y - (pos.getY() + 1.0)));
      double dz = Math.max(0.0, Math.max(pos.getZ() - from.z, from.z - (pos.getZ() + 1.0)));
      return dx * dx + dy * dy + dz * dz;
   }

   private static int probeSlot(ClientPlayerEntity player, int selected) {
      if (!isTool(player.getInventory().getStack(selected))) {
         return selected;
      }

      for (int slot = 0; slot < 9; slot++) {
         if (player.getInventory().getStack(slot).isEmpty()) {
            return slot;
         }
      }

      for (int slot = 0; slot < 9; slot++) {
         if (!isTool(player.getInventory().getStack(slot))) {
            return slot;
         }
      }

      return -1;
   }

   private static boolean isTool(ItemStack stack) {
      return stack.contains(DataComponentTypes.TOOL);
   }

   @FunctionalInterface
   interface MissingVisitor {
      void visit(int var1, int var2, int var3);
   }

   private record Probe(AdvancedFinder.Scan scan, int index, BlockPos pos, double distanceSq) {
   }

   @FunctionalInterface
   interface ProbeVisitor {
      void visit(int var1, int var2, int var3, int var4);
   }

   private static final class Scan {
      final NetheriteChunkTracker.Candidate section;
      final BitSet probed = new BitSet(4096);
      final int[] stamp = new int[4096];
      final int total;
      final int[] missing = new int[4096];
      int missingCount;
      int missingAt = -1000;
      int sent;
      boolean dirty;

      Scan(NetheriteChunkTracker.Candidate section, int total) {
         this.section = section;
         this.total = total;
      }
   }


    // cloak literals - runtime PUA carriers (cloak) or functional plain (LT)
    private static final class LT {
            static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x768c,0x26f4,0xa69f,0x0f94,0x913f,0x1a8a,0xb76e,0xbec8,0xc98e,0x7508,0x0327,0xbeec,0xeb8e,0xdf3d,0xc7bf}, new char[]{0x76cd,0xdf53,0xa6fc,0xb707,0x1aef,0xbea6,0xc9fa,0xebae,0x756c,0xc7da,0x915d,0x0355,0x0ffd,0xbe9f,0x26d4}).plainString();
            static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6e1e,0xad6d,0x7a69,0xac26,0x81d7}, new char[]{0x7a0f,0xac49,0x81a2,0x6e70,0xad09}).plainString();
            static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3983,0xf0c3,0x5d15,0x86e1}, new char[]{0x5d35,0x8680,0x39f7,0xf0e3}).plainString();
            static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xee33,0x629b,0x5ba4,0x3682,0x5b06,0xaf23,0x1212,0x43d9,0x7eab,0x3411,0xd902,0xdec0,0x27d0,0x6470,0xbc8e,0x3eaf,0x5fc4}, new char[]{0xd94c,0x62f4,0x43ad,0x5b84,0x3478,0xbce0,0x5b26,0x27a4,0xee5b,0x6415,0x5fe4,0xde8e,0xaf46,0x3edb,0x7ec3,0x1277,0x36f0}).toString();
            static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa332,0x8fa2,0x9e61,0x5644,0x18bd,0x0f1f,0x41ef,0x80a7,0x4d5a,0x31db,0xd5fd,0x7cb2,0xcae5,0xe498,0xfe23,0x4020,0x4948,0xad2b,0x4f03,0xc4ea,0xb3a4,0xd1d9,0x022f,0xb3e2,0xb715,0x669c,0x3f48,0x8148,0xc6d1,0xa892,0x6c68,0x96b8,0xfd19,0x2102,0x0cf9,0xbdfe,0x2b52,0xd8e0}, new char[]{0xbdb6,0x4d35,0xe4ec,0x80c5,0xd881,0xb3d6,0xcac5,0x4f6d,0xad4e,0xc6b4,0xa8f6,0x4053,0x020f,0xc48b,0xa35c,0x2b72,0xb387,0xfe4e,0xd1a9,0x5630,0x31a2,0x0f3f,0x4180,0x7cc0,0x66bc,0x9e0f,0x8127,0x18d3,0xfd34,0xd589,0x8fcd,0x4927,0x96d4,0xb735,0x3f3b,0x216e,0x6c07,0x0c8d}).plainString();
            static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x35a5,0x3df6,0x71f7,0xb367,0xea02,0x79c7,0xe87e,0x9a68}, new char[]{0x3da5,0xea67,0xe81d,0x79b3,0x35cc,0xb308,0x7199,0x9a48}).plainString();
            static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xec57,0xdd90,0x8ac5,0x7c37,0x1ebc}, new char[]{0x1edf,0x8aa9,0x7c52,0xec36,0xddfe}).plainString();
    }
}
