package com.utilitymod.anubis.module.impl.render;

import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2IntMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongConsumer;
import com.utilitymod.anubis.event.EventHandler;
import com.utilitymod.anubis.event.impl.PacketAppliedEvent;
import com.utilitymod.anubis.event.impl.Render3DEvent;
import com.utilitymod.anubis.event.impl.TickEvent;
import com.utilitymod.anubis.module.ClientModule;
import com.utilitymod.anubis.module.ModuleCategory;
import com.utilitymod.anubis.module.setting.BooleanSetting;
import com.utilitymod.anubis.module.setting.ColorSetting;
import com.utilitymod.anubis.module.setting.ModeSetting;
import com.utilitymod.anubis.module.setting.NumberSetting;
import com.utilitymod.anubis.util.ChunkAreas;
import com.utilitymod.anubis.util.ChunkOverlay;
import com.utilitymod.anubis.util.GeodeGlowScan;
import com.utilitymod.anubis.util.LevelUtil;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.LightType;
import net.minecraft.block.Blocks;
import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.packet.s2c.play.LightUpdateS2CPacket;
import net.minecraft.block.BlockState;
import net.minecraft.state.property.Properties;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.client.MinecraftClient;
import net.minecraft.block.BuddingAmethystBlock;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.BlockPos.Mutable;

public final class SusChunkScan extends ClientModule {
   private static final long SCAN_BUDGET_NANOS = 3000000L;
   private static final int MIN_SCANNED_NEIGHBOURS = 3;
   private static final int NO_PACKET_SENSITIVITY = 1;
   private static final int VISIBLE_REFRESH_TICKS = 20;
   private static final int PACKET_MIN_Y = -16;
   private static final int PACKET_MAX_Y = 30;
   private static final String METHOD_CLASSIC = LT.L1;
   private static final String METHOD_ANBS = LT.L2;
   private static final int GLOW_CELLS_PER_POINT = 12;
   private static final int GEODE_TOP_Y = 47;
   private static final int MIN_EXPOSED_BUDDING = 10;
   private static final int MIN_EXPOSED_LINING = 160;
   private static final int GEODE_SETTLE_TICKS = 40;
   private static final int GEODE_CELL_SHIFT = 2;
   private static final int TORCH_LIGHT = 14;
   private static final Direction[] SIDES = Direction.values();
   private final ModeSetting method = this.addSetting(LT.L3, new ModeSetting(LT.L4, LT.L1, LT.L1, LT.L2));
   private final NumberSetting spreadRadius = this.addSetting(LT.L5, new NumberSetting(LT.L6, 5.0F, 2.0F, 16.0F).step(1.0F));
   private final NumberSetting sensitivity = this.addSetting(LT.L7, new NumberSetting(LT.L8, 6.0F, 1.0F, 20.0F).step(1.0F));
   private final ColorSetting chunkColor = this.addSetting(LT.L9, new ColorSetting(LT.L10, 1627325444));
   private final BooleanSetting tracers = this.addSetting(LT.L11, new BooleanSetting(LT.L12, true));
   private final BooleanSetting detectMined = this.addSetting(LT.L13, new BooleanSetting(LT.L14, false));
   private final ColorSetting minedColor = this.addSetting(LT.L15, new ColorSetting(LT.L16, 1621511679));
   private final ChunkAreas chunkAreas = new ChunkAreas();
   private final ChunkOverlay chunkOverlay = new ChunkOverlay(LT.L17);
   private final ChunkAreas minedAreas = new ChunkAreas();
   private final ChunkOverlay minedOverlay = new ChunkOverlay(LT.L18);
   private final GeodeGlowScan glow = new GeodeGlowScan();
   private final Long2IntOpenHashMap heat = new Long2IntOpenHashMap();
   private final Long2IntOpenHashMap vetoes = new Long2IntOpenHashMap();
   private final Long2IntOpenHashMap chunkScore = new Long2IntOpenHashMap();
   private final Long2BooleanOpenHashMap chunkUngrown = new Long2BooleanOpenHashMap();
   private final Long2BooleanOpenHashMap chunkHasPacket = new Long2BooleanOpenHashMap();
   private final LongOpenHashSet scannedChunks = new LongOpenHashSet();
   private final LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
   private final LongOpenHashSet queued = new LongOpenHashSet();
   private Long2IntOpenHashMap visible = new Long2IntOpenHashMap();
   private boolean visibleDirty = true;
   private int visibleAge;
   private ClientWorld lastLevel;
   private int appliedRadius;
   private final Long2ObjectOpenHashMap<SusChunkScan.GeodeSlice> geodeSlices = new Long2ObjectOpenHashMap();
   private final Long2IntOpenHashMap geodeWaiting = new Long2IntOpenHashMap();
   private final Long2IntOpenHashMap geodeScanTicks = new Long2IntOpenHashMap();
   private List<SusChunkScan.GeodeCluster> geodeClusters = List.of();
   private boolean geodeClustersDirty;
   private LongOpenHashSet minedChunks = new LongOpenHashSet();
   private int minedAge;
   private int ticks;

   public SusChunkScan() {
      super(LT.L19, LT.L20, LT.L21, ModuleCategory.BASE_RAID);
      this.method.onChange(this::rescan);
      this.sensitivity.onChange(() -> this.visibleDirty = true);
      this.detectMined.onChange(this::restartGeodes);
      this.minedColor.visibleWhen(this.detectMined::get);
   }

   private void rescan() {
      this.clearAll();
      this.queueLoadedChunks();
   }

   private void restartGeodes() {
      this.clearGeodes();
      if (this.detectMined.get()) {
         this.queueLoadedChunks();
      }
   }

   @Override
   protected void onEnable() {
      this.clearAll();
      this.lastLevel = MinecraftClient.getInstance().world;
      this.queueLoadedChunks();
   }

   @Override
   protected void onDisable() {
      this.clearAll();
      this.lastLevel = null;
   }

   private void clearAll() {
      this.heat.clear();
      this.vetoes.clear();
      this.chunkScore.clear();
      this.chunkUngrown.clear();
      this.chunkHasPacket.clear();
      this.scannedChunks.clear();
      this.queue.clear();
      this.queued.clear();
      this.visible = new Long2IntOpenHashMap();
      this.visibleDirty = true;
      this.appliedRadius = this.spreadRadius.getInt();
      this.clearGeodes();
   }

   private void clearGeodes() {
      this.geodeSlices.clear();
      this.geodeWaiting.clear();
      this.geodeScanTicks.clear();
      this.geodeClusters = List.of();
      this.geodeClustersDirty = false;
      this.minedChunks = new LongOpenHashSet();
      this.minedAge = 0;
   }

   private void queueLoadedChunks() {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.world != null && mc.player != null) {
         int radius = (Integer)mc.options.getViewDistance().getValue() + 1;
         ChunkPos center = mc.player.getChunkPos();

         for (int x = center.x - radius; x <= center.x + radius; x++) {
            for (int z = center.z - radius; z <= center.z + radius; z++) {
               if (LevelUtil.isLoaded(mc.world, x, z)) {
                  this.enqueue(ChunkPos.toLong(x, z));
               }
            }
         }
      }
   }

   private void enqueue(long key) {
      if (this.queued.add(key)) {
         this.queue.enqueue(key);
      }
   }

   @EventHandler
   public void onPacketApplied(PacketAppliedEvent event) {
      if (event.packet instanceof ChunkDataS2CPacket chunk) {
         this.enqueue(ChunkPos.toLong(chunk.getChunkX(), chunk.getChunkZ()));
      } else if (event.packet instanceof LightUpdateS2CPacket light && this.method.is(LT.L2)) {
         this.enqueue(ChunkPos.toLong(light.getChunkX(), light.getChunkZ()));
      }
   }

   @EventHandler
   public void onTick(TickEvent event) {
      MinecraftClient mc = MinecraftClient.getInstance();
      ClientWorld level = mc.world;
      if (level != this.lastLevel) {
         this.clearAll();
         this.lastLevel = level;
         this.queueLoadedChunks();
      }

      if (level != null) {
         this.ticks++;
         if (this.spreadRadius.getInt() != this.appliedRadius) {
            this.clearAll();
            this.queueLoadedChunks();
         }

         long deadline = System.nanoTime() + 3000000L;

         while (!this.queue.isEmpty() && System.nanoTime() < deadline) {
            long key = this.queue.dequeueLong();
            this.queued.remove(key);
            int cx = ChunkPos.getPackedX(key);
            int cz = ChunkPos.getPackedZ(key);
            if (LevelUtil.isLoaded(level, cx, cz)) {
               this.updateChunk(key, level.getChunk(cx, cz), level);
               this.scannedChunks.add(key);
               this.visibleDirty = true;
            }
         }

         if (this.visibleDirty || ++this.visibleAge >= 20) {
            this.visible = this.computeVisible(level);
            this.visibleDirty = false;
            this.visibleAge = 0;
         }

         if (this.detectMined.get() && ++this.minedAge >= 20) {
            this.minedChunks = this.computeMined(level);
            this.minedAge = 0;
         }
      }
   }

   private void updateChunk(long key, WorldChunk chunk, ClientWorld level) {
      int r = this.spreadRadius.getInt();
      int cx = ChunkPos.getPackedX(key);
      int cz = ChunkPos.getPackedZ(key);
      int oldHeat = this.chunkScore.getOrDefault(key, 0);
      boolean oldUngrown = this.chunkUngrown.getOrDefault(key, false);
      if (oldHeat > 0) {
         forArea(cx, cz, r, area -> subtract(this.heat, area, oldHeat));
      }

      if (oldUngrown) {
         forArea(cx, cz, r, area -> subtract(this.vetoes, area, 1));
      }

      this.chunkHasPacket.put(key, hasBlocksInRange(chunk, -16, 30));
      SusChunkScan.ChunkScore score = this.scoreChunk(chunk, level);
      this.chunkScore.put(key, score.selfHeat());
      this.chunkUngrown.put(key, score.hasUngrown());
      if (score.hasUngrown()) {
         forArea(cx, cz, r, area -> this.vetoes.addTo(area, 1));
      }

      if (score.selfHeat() > 0) {
         forArea(cx, cz, r, area -> this.heat.addTo(area, score.selfHeat()));
      }

      if (this.detectMined.get()) {
         this.collectGeode(key, chunk, level);
      }
   }

   private static void forArea(int cx, int cz, int r, LongConsumer action) {
      for (int dx = -r; dx <= r; dx++) {
         for (int dz = -r; dz <= r; dz++) {
            action.accept(ChunkPos.toLong(cx + dx, cz + dz));
         }
      }
   }

   private static void subtract(Long2IntOpenHashMap map, long key, int amount) {
      int left = map.get(key) - amount;
      if (left > 0) {
         map.put(key, left);
      } else {
         map.remove(key);
      }
   }

   private static boolean hasBlocksInRange(WorldChunk chunk, int minY, int maxY) {
      ChunkSection[] sections = chunk.getSectionArray();
      int minSectionY = chunk.getBottomSectionCoord();

      for (int i = 0; i < sections.length; i++) {
         ChunkSection section = sections[i];
         if (section != null && !section.isEmpty()) {
            int baseY = minSectionY + i << 4;
            if (baseY + 15 >= minY && baseY <= maxY) {
               for (int y = 0; y < 16; y++) {
                  int worldY = baseY + y;
                  if (worldY >= minY && worldY <= maxY) {
                     for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                           if (!section.getBlockState(x, y, z).isAir()) {
                              return true;
                           }
                        }
                     }
                  }
               }
            }
         }
      }

      return false;
   }

   private SusChunkScan.ChunkScore scoreChunk(WorldChunk chunk, ClientWorld level) {
      if (this.method.is(LT.L2)) {
         return new SusChunkScan.ChunkScore(this.glow.scan(level, chunk, null) / 12, false);
      }

      ChunkSection[] sections = chunk.getSectionArray();
      int minSectionY = chunk.getBottomSectionCoord();
      ChunkPos pos = chunk.getPos();
      boolean paletteOnly = isDonutFolia();
      int grown = 0;
      int ungrown = 0;
      boolean sawBud = false;
      boolean sawCluster = false;
      Mutable cursor = new Mutable();

      for (int i = 0; i < sections.length; i++) {
         ChunkSection section = sections[i];
         if (section != null && !section.isEmpty()) {
            if (paletteOnly) {
               if (!sawBud && section.hasAny(SusChunkScan::isBud)) {
                  sawBud = true;
               }

               if (!sawCluster && section.hasAny(statex -> statex.isOf(Blocks.AMETHYST_CLUSTER))) {
                  sawCluster = true;
               }
            } else if (section.hasAny(SusChunkScan::isAmethystGrowth)) {
               int baseY = minSectionY + i << 4;

               for (int x = 0; x < 16; x++) {
                  for (int y = 0; y < 16; y++) {
                     for (int z = 0; z < 16; z++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (state.isOf(Blocks.AMETHYST_CLUSTER)) {
                           grown++;
                        } else if (isBud(state) && state.contains(Properties.FACING)) {
                           Direction facing = (Direction)state.get(Properties.FACING);
                           cursor.set(pos.getStartX() + x, baseY + y, pos.getStartZ() + z).move(facing.getOpposite());
                           if (level.getBlockState(cursor).isOf(Blocks.BUDDING_AMETHYST)) {
                              ungrown++;
                           } else {
                              grown++;
                           }
                        }
                     }
                  }
               }
            }
         }
      }

      if (paletteOnly) {
         if (sawBud) {
            ungrown++;
         } else if (sawCluster) {
            grown++;
         }
      }

      return new SusChunkScan.ChunkScore(grown, ungrown > 0);
   }

   private void collectGeode(long key, WorldChunk chunk, ClientWorld level) {
      this.geodeScanTicks.put(key, this.ticks);
      if (this.geodeSlices.remove(key) != null) {
         this.geodeClustersDirty = true;
      }

      this.geodeWaiting.remove(key);
      int cx = ChunkPos.getPackedX(key);
      int cz = ChunkPos.getPackedZ(key);

      for (int dx = -1; dx <= 1; dx++) {
         for (int dz = -1; dz <= 1; dz++) {
            long near = ChunkPos.toLong(cx + dx, cz + dz);
            if ((dx != 0 || dz != 0) && (this.geodeWaiting.get(near) & waitBit(-dx, -dz)) != 0) {
               this.enqueue(near);
            }
         }
      }

      if (LevelUtil.isOverworld(level)) {
         ChunkSection[] sections = chunk.getSectionArray();
         int minSectionY = chunk.getBottomSectionCoord();
         int minX = cx << 4;
         int minZ = cz << 4;
         LongArrayList lining = new LongArrayList();
         LongArrayList budding = new LongArrayList();
         Mutable cursor = new Mutable();
         int waiting = 0;

         for (int i = 0; i < sections.length; i++) {
            int baseY = minSectionY + i << 4;
            if (baseY > 47) {
               break;
            }

            ChunkSection section = sections[i];
            if (section != null && !section.isEmpty() && section.hasAny(SusChunkScan::isLining)) {
               for (int y = 0; y < 16; y++) {
                  for (int z = 0; z < 16; z++) {
                     for (int x = 0; x < 16; x++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (isLining(state)) {
                           int wx = minX + x;
                           int wy = baseY + y;
                           int wz = minZ + z;
                           boolean exposed = false;
                           int missing = 0;

                           for (Direction side : SIDES) {
                              int nx = wx + side.getOffsetX();
                              int ny = wy + side.getOffsetY();
                              int nz = wz + side.getOffsetZ();
                              if (!level.isOutOfHeightLimit(ny)) {
                                 int ncx = nx >> 4;
                                 int ncz = nz >> 4;
                                 boolean inside = ncx == cx && ncz == cz;
                                 if (!inside && !LevelUtil.isLoaded(level, ncx, ncz)) {
                                    missing |= waitBit(ncx - cx, ncz - cz);
                                 } else {
                                    cursor.set(nx, ny, nz);
                                    if (isOpen(inside ? chunk.getBlockState(cursor) : level.getBlockState(cursor))) {
                                       exposed = true;
                                       break;
                                    }
                                 }
                              }
                           }

                           if (!exposed) {
                              waiting |= missing;
                           } else {
                              long packed = BlockPos.asLong(wx, wy, wz);
                              lining.add(packed);
                              if (state.isOf(Blocks.BUDDING_AMETHYST)) {
                                 budding.add(packed);
                              }
                           }
                        }
                     }
                  }
               }
            }
         }

         if (waiting != 0) {
            this.geodeWaiting.put(key, waiting);
         }

         if (!lining.isEmpty()) {
            this.geodeSlices.put(key, new SusChunkScan.GeodeSlice(lining.toLongArray(), budding.toLongArray()));
            this.geodeClustersDirty = true;
         }
      }
   }

   private static int waitBit(int dx, int dz) {
      return 1 << (dx + 1) * 3 + dz + 1;
   }

   private LongOpenHashSet computeMined(ClientWorld level) {
      if (this.geodeSlices.keySet().removeIf(key -> !LevelUtil.isLoaded(level, ChunkPos.getPackedX(key), ChunkPos.getPackedZ(key)))) {
         this.geodeClustersDirty = true;
      }

      this.geodeWaiting.keySet().removeIf(key -> !LevelUtil.isLoaded(level, ChunkPos.getPackedX(key), ChunkPos.getPackedZ(key)));
      this.geodeScanTicks.keySet().removeIf(key -> !LevelUtil.isLoaded(level, ChunkPos.getPackedX(key), ChunkPos.getPackedZ(key)));
      if (this.geodeClustersDirty) {
         this.geodeClusters = this.clusterGeodes();
         this.geodeClustersDirty = false;
      }

      LongOpenHashSet mined = new LongOpenHashSet();

      for (SusChunkScan.GeodeCluster cluster : this.geodeClusters) {
         boolean large = cluster.budding() > 0 ? cluster.budding() >= 10 : cluster.lining().length >= 160;
         if (large && this.settled(level, cluster) && unlit(level, cluster.lining())) {
            mined.add(cluster.chunk());
         }
      }

      return mined;
   }

   private List<SusChunkScan.GeodeCluster> clusterGeodes() {
      Long2ObjectOpenHashMap<LongArrayList> cells = new Long2ObjectOpenHashMap();
      LongOpenHashSet budding = new LongOpenHashSet();
      ObjectIterator clusters = this.geodeSlices.values().iterator();

      while (clusters.hasNext()) {
         SusChunkScan.GeodeSlice slice = (SusChunkScan.GeodeSlice)clusters.next();

         for (long packed : slice.lining()) {
            long cell = BlockPos.asLong(
               BlockPos.unpackLongX(packed) >> 2, BlockPos.unpackLongY(packed) >> 2, BlockPos.unpackLongZ(packed) >> 2
            );
            LongArrayList members = (LongArrayList)cells.get(cell);
            if (members == null) {
               cells.put(cell, members = new LongArrayList());
            }

            members.add(packed);
         }

         for (long packed : slice.budding()) {
            budding.add(packed);
         }
      }

      List<SusChunkScan.GeodeCluster> clustersx = new ArrayList<>();
      LongOpenHashSet seen = new LongOpenHashSet();
      LongArrayFIFOQueue frontier = new LongArrayFIFOQueue();
      LongIterator var25 = cells.keySet().iterator();

      while (var25.hasNext()) {
         long start = (Long)var25.next();
         if (seen.add(start)) {
            LongArrayList lining = new LongArrayList();
            frontier.enqueue(start);

            while (!frontier.isEmpty()) {
               long cell = frontier.dequeueLong();
               lining.addAll((LongList)cells.get(cell));
               int x = BlockPos.unpackLongX(cell);
               int y = BlockPos.unpackLongY(cell);
               int z = BlockPos.unpackLongZ(cell);

               for (int dx = -1; dx <= 1; dx++) {
                  for (int dy = -1; dy <= 1; dy++) {
                     for (int dz = -1; dz <= 1; dz++) {
                        long next = BlockPos.asLong(x + dx, y + dy, z + dz);
                        if (cells.containsKey(next) && seen.add(next)) {
                           frontier.enqueue(next);
                        }
                     }
                  }
               }
            }

            clustersx.add(toCluster(lining.toLongArray(), budding));
         }
      }

      return clustersx;
   }

   private static SusChunkScan.GeodeCluster toCluster(long[] lining, LongOpenHashSet budding) {
      int count = 0;
      int minX = Integer.MAX_VALUE;
      int minZ = Integer.MAX_VALUE;
      int maxX = Integer.MIN_VALUE;
      int maxZ = Integer.MIN_VALUE;
      long sumX = 0L;
      long sumZ = 0L;

      for (long packed : lining) {
         if (budding.contains(packed)) {
            count++;
         }

         int x = BlockPos.unpackLongX(packed);
         int z = BlockPos.unpackLongZ(packed);
         minX = Math.min(minX, x);
         minZ = Math.min(minZ, z);
         maxX = Math.max(maxX, x);
         maxZ = Math.max(maxZ, z);
         sumX += x;
         sumZ += z;
      }

      int centerX = (int)Math.floorDiv(sumX, lining.length);
      int centerZ = (int)Math.floorDiv(sumZ, lining.length);
      return new SusChunkScan.GeodeCluster(lining, count, minX, minZ, maxX, maxZ, ChunkPos.toLong(centerX >> 4, centerZ >> 4));
   }

   private boolean settled(ClientWorld level, SusChunkScan.GeodeCluster cluster) {
      for (int cx = cluster.minX() - 1 >> 4; cx <= cluster.maxX() + 1 >> 4; cx++) {
         for (int cz = cluster.minZ() - 1 >> 4; cz <= cluster.maxZ() + 1 >> 4; cz++) {
            long key = ChunkPos.toLong(cx, cz);
            if (!LevelUtil.isLoaded(level, cx, cz) || !this.geodeScanTicks.containsKey(key)) {
               return false;
            }

            if (this.ticks - this.geodeScanTicks.get(key) < 40) {
               return false;
            }
         }
      }

      return true;
   }

   private static boolean unlit(ClientWorld level, long[] lining) {
      Mutable cursor = new Mutable();
      Long2IntOpenHashMap peaks = new Long2IntOpenHashMap();

      for (long packed : lining) {
         int x = BlockPos.unpackLongX(packed);
         int y = BlockPos.unpackLongY(packed);
         int z = BlockPos.unpackLongZ(packed);

         for (Direction side : SIDES) {
            cursor.set(x + side.getOffsetX(), y + side.getOffsetY(), z + side.getOffsetZ());
            if (isAmethystGrowth(level.getBlockState(cursor))) {
               return false;
            }

            int light = level.getLightLevel(LightType.BLOCK, cursor);
            if (light > 0 && sourceLight(level, cursor, light, peaks) != 14) {
               return false;
            }
         }
      }

      return true;
   }

   private static int sourceLight(ClientWorld level, BlockPos start, int light, Long2IntOpenHashMap peaks) {
      LongArrayList path = new LongArrayList();
      Mutable at = start.mutableCopy();
      Mutable probe = new Mutable();

      int peak;
      while (true) {
         long key = at.asLong();
         if (peaks.containsKey(key)) {
            peak = peaks.get(key);
            break;
         }

         path.add(key);
         Direction uphill = null;
         int best = light;

         for (Direction side : SIDES) {
            int value = level.getLightLevel(LightType.BLOCK, probe.set(at, side));
            if (value > best) {
               best = value;
               uphill = side;
            }
         }

         if (uphill == null) {
            peak = light;
            break;
         }

         at.move(uphill);
         light = best;
      }

      for (int i = 0; i < path.size(); i++) {
         peaks.put(path.getLong(i), peak);
      }

      return peak;
   }

   private static boolean isLining(BlockState state) {
      return state.isOf(Blocks.AMETHYST_BLOCK) || state.isOf(Blocks.BUDDING_AMETHYST);
   }

   private static boolean isOpen(BlockState state) {
      return BuddingAmethystBlock.canGrowIn(state) || isAmethystGrowth(state);
   }

   private static boolean isBud(BlockState state) {
      Block block = state.getBlock();
      return block == Blocks.SMALL_AMETHYST_BUD || block == Blocks.MEDIUM_AMETHYST_BUD || block == Blocks.LARGE_AMETHYST_BUD;
   }

   private static boolean isAmethystGrowth(BlockState state) {
      return state.isOf(Blocks.AMETHYST_CLUSTER) || isBud(state);
   }

   private static boolean isDonutFolia() {
      ClientPlayNetworkHandler connection = MinecraftClient.getInstance().getNetworkHandler();
      if (connection == null) {
         return false;
      }

      String brand = connection.getBrand();
      return brand != null && brand.contains(LT.L22);
   }

   private Long2IntOpenHashMap computeVisible(ClientWorld level) {
      Long2IntOpenHashMap eligible = new Long2IntOpenHashMap();
      ObjectIterator withPacket = this.heat.long2IntEntrySet().iterator();

      while (withPacket.hasNext()) {
         Entry entry = (Entry)withPacket.next();
         long key = entry.getLongKey();
         if (!this.vetoes.containsKey(key)) {
            int cx = ChunkPos.getPackedX(key);
            int cz = ChunkPos.getPackedZ(key);
            if (LevelUtil.isLoaded(level, cx, cz) && this.scannedNeighbours(cx, cz) >= 3) {
               eligible.put(key, entry.getIntValue());
            }
         }
      }

      int withPacketx = this.sensitivity.getInt();
      int reach = this.appliedRadius;
      Long2IntOpenHashMap kept = new Long2IntOpenHashMap();
      ObjectIterator var6 = eligible.long2IntEntrySet().iterator();

      while (var6.hasNext()) {
         Entry entry = (Entry)var6.next();
         long key = entry.getLongKey();
         int value = entry.getIntValue();
         int threshold = this.chunkHasPacket.getOrDefault(key, true) ? withPacketx : 1;
         if (value >= threshold && isPeak(eligible, key, value, reach)) {
            kept.put(key, value);
         }
      }

      return kept;
   }

   private static boolean isPeak(Long2IntOpenHashMap field, long key, int value, int reach) {
      int cx = ChunkPos.getPackedX(key);
      int cz = ChunkPos.getPackedZ(key);

      for (int dx = -reach; dx <= reach; dx++) {
         for (int dz = -reach; dz <= reach; dz++) {
            if ((dx != 0 || dz != 0) && field.get(ChunkPos.toLong(cx + dx, cz + dz)) > value) {
               return false;
            }
         }
      }

      return true;
   }

   private int scannedNeighbours(int cx, int cz) {
      int count = 0;

      for (int dx = -1; dx <= 1; dx++) {
         for (int dz = -1; dz <= 1; dz++) {
            if ((dx != 0 || dz != 0) && this.scannedChunks.contains(ChunkPos.toLong(cx + dx, cz + dz))) {
               count++;
            }
         }
      }

      return count;
   }

   @EventHandler
   public void onRender3D(Render3DEvent event) {
      if (MinecraftClient.getInstance().world != null) {
         this.chunkOverlay.draw(this.visible.keySet(), this.chunkColor.argb(), event.partialTick);
         if (this.tracers.get()) {
            this.chunkAreas.drawTracers(this.visible.keySet(), 63.0, ColorHelper.fullAlpha(this.chunkColor.argb()));
         }

         this.minedOverlay.draw(this.minedChunks, this.minedColor.argb(), event.partialTick);
         if (this.tracers.get()) {
            this.minedAreas.drawTracers(this.minedChunks, 63.0, ColorHelper.fullAlpha(this.minedColor.argb()));
         }
      }
   }

   private record ChunkScore(int selfHeat, boolean hasUngrown) {
   }

   private record GeodeCluster(long[] lining, int budding, int minX, int minZ, int maxX, int maxZ, long chunk) {
   }

   private record GeodeSlice(long[] lining, long[] budding) {
   }


    // cloak literals - runtime PUA carriers (cloak) or functional plain (LT)
    private static final class LT {
            static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7cd5,0xb280,0x7f80,0x5bd6,0xe4a2,0xefba,0xf6e0}, new char[]{0xb2c3,0xefd6,0x7cb4,0xe4d1,0x7ff3,0xf689,0x5bb5}).toString();
            static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5f8f,0xa94b,0xbb97,0x5f36,0x8f46,0xafaf,0x0d62,0x5901,0x721b,0x48ec}, new char[]{0x5940,0x5fc1,0x0d20,0xaffc,0xa960,0x48cc,0xbbc4,0x7278,0x8f27,0x5f58}).toString();
            static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xfe14,0xa708,0xf07d,0xd1bf,0x7cbf,0xc267}, new char[]{0x7cd2,0xd1da,0xfe60,0xf015,0xc208,0xa76c}).plainString();
            static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xbd8a,0x2dd1,0xee20,0x9f9e,0x2130,0x41ad}, new char[]{0xee6d,0x2155,0xbdfe,0x41c5,0x9ff1,0x2db5}).toString();
            static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x37e8,0x0f5d,0xb6bc,0x503b,0xd674,0x275e,0x1abb,0x0d1f,0x2e6d,0x97f0,0xc541,0xd269,0x26ce}, new char[]{0x26bd,0x0d6f,0x0f2f,0x505e,0x2e0c,0x9794,0x1a96,0xc533,0xb6dd,0xd610,0x3781,0x272b,0xd21a}).plainString();
            static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7c3e,0x5b15,0x8ea7,0x86f9,0x3efd,0x15d2,0xd70d,0xb302,0x57b7,0x1a0d,0x3e88,0x8fd7,0xddf3,0x4b98,0x630a,0x871e,0x8df6,0xfd67,0x1c55}, new char[]{0x8da5,0x8fbe,0xd760,0x868c,0xfd0b,0x5b74,0x7c4a,0x4bf1,0x3e92,0x15bc,0x3ea8,0x1c11,0xdd9a,0x876d,0x57c3,0xb363,0x8ec9,0x6369,0x1a68}).toString();
            static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8ea3,0xcee0,0x98b8,0x594e,0xb05e,0x88c7,0x8b5f,0x0d06,0xec40,0x1423,0x5828,0x2165,0x1c33,0x3858,0xbd4f,0xdcfc,0x95c4,0x739c}, new char[]{0xbd3f,0x592f,0x95a7,0x210e,0xce85,0x585c,0xec6d,0x98cb,0x383d,0x73f2,0x1450,0x8b36,0x1c47,0x8eca,0xb028,0x88ae,0x0d72,0xdc85}).plainString();
            static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb23c,0x0914,0x7c7d,0x41d6,0xf34e,0x3311,0xae18,0x3781,0x9b5e,0x4f28,0x19eb}, new char[]{0x9b0d,0xae7d,0x1985,0xf33d,0x097d,0x41a2,0x4f41,0x7c0b,0x37e8,0x3365,0xb245}).toString();
            static final String L9 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb9a7,0x49e8,0x6b54,0x3555,0xf97b,0x0d34,0xe1dc,0xd6c1,0x8bae,0xce4c,0x6460}, new char[]{0x6b37,0x0d5c,0xd6b4,0xb9c9,0xf910,0x49c5,0xce2f,0x640f,0x8bc2,0x353a,0xe1ae}).plainString();
            static final String L10 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1992,0x3b53,0xe2a4,0x80c7,0x68d1,0xfe14,0xe29d,0xe403,0x6b3e,0xe245,0x3cdc}, new char[]{0xfe57,0x3b3b,0x68a4,0xe46d,0xe2cf,0x80e7,0x3c9f,0x19fd,0xe229,0xe2f2,0x6b4c}).toString();
            static final String L11 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x57d6,0xe413,0x986b,0x54f3,0xb425,0x163b,0x798e}, new char[]{0x981f,0x1649,0x5492,0xb446,0x57b3,0x79fc,0xe460}).plainString();
            static final String L12 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2966,0x4106,0xab5d,0xd1f9,0x6c56,0x34c6,0x144e}, new char[]{0x6c02,0xab2f,0x2907,0xd19a,0x4163,0x143c,0x34b5}).toString();
            static final String L13 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8bda,0x8398,0x1ebb,0xd3e0,0xae31,0x9aef,0x7dc6,0x76d4,0x06c9,0xd855,0xa284,0x2b8e,0x9e2e,0xdbec,0x6004,0x3666,0x810d,0xa775,0xe659,0xb66b,0xf6f8}, new char[]{0x83fc,0x8168,0xd821,0x3603,0xf69b,0xdb98,0x7deb,0xe634,0x9a86,0x8bb4,0x06ac,0x76b0,0xa2a9,0xae52,0x1ed7,0x9e5b,0xa706,0x6070,0xb60e,0xd392,0x2bfd}).plainString();
            static final String L14 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x106c,0xb72c,0xd63e,0x0ae8,0x1be2,0xef39,0x01b8,0x5a54,0x9ccf,0x2e94,0x0af2,0x6d36,0xf6b0,0xb72d,0x28fb,0x79f2,0x4be6,0x5347,0x5274,0x72db,0x93b4}, new char[]{0x0aac,0x01dd,0xef4d,0x6d53,0x93d7,0xd64a,0xb70c,0xf6fd,0x1005,0x5329,0x5a31,0x4b82,0xb70d,0x2ed7,0x799e,0x5201,0x9cbc,0x0a86,0x289e,0x1b90,0x72a8}).toString();
            static final String L15 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4a3d,0xd283,0x112d,0x1366,0x601c,0x917f,0x0f6f,0x2f27,0xc8f3,0x02aa,0x88ed}, new char[]{0x4a50,0xc89a,0x1143,0x2f42,0xd2e7,0x6031,0x02c9,0x8882,0x9113,0x0f00,0x1314}).plainString();
            static final String L16 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb88f,0xc635,0x079b,0x68be,0xcacc,0x6d34,0xf60b,0x8734,0x9ce4,0xa184,0x719c}, new char[]{0x6d79,0x9c8d,0xcaa2,0x07fe,0x68da,0x8714,0xf648,0xb8e0,0x71f0,0xc65a,0xa1f6}).toString();
            static final String L17 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe3c3,0x1196,0x953f,0x3177,0xfc3a,0x79bd,0x3f68,0x2562,0xe799}, new char[]{0x11c5,0x954a,0xe7ea,0x3f48,0xfc59,0x250a,0x79c8,0x3119,0xe3a8}).toString();
            static final String L18 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0a9b,0xfcd2,0x32a4,0xd866,0x3848,0x45b5,0xfa54,0xccb8,0xdf3e,0x9033,0x7832}, new char[]{0xd82b,0xccd1,0x32ca,0x9056,0x45d1,0xfcf2,0xdf5d,0x0af3,0x7847,0xfa3a,0x3823}).toString();
            static final String L19 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1fe2,0x3bc7,0x0186,0xa87f,0xeafd,0x4b09,0xaea3,0x78da,0x4b65,0x6071,0xb094,0x3140,0xff13,0x44fc,0x1ded,0x5e36}, new char[]{0xb0e7,0x1f97,0x448f,0x78f7,0x1d8e,0x4b0d,0xff66,0xa811,0x01ed,0x605c,0x3ba1,0xaeca,0xea93,0x4b6d,0x3125,0x5e44}).plainString();
            static final String L20 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x07d2,0xc11f,0x9d09,0x0ebd,0x2c06,0xcd35,0x938c,0x65bd,0xe000,0x054e,0x000d,0x984a,0x1aaa}, new char[]{0x0efe,0x9822,0x053b,0x07bc,0x0066,0x9d29,0xe041,0xc171,0x93e3,0x2c6b,0x65dc,0xcd59,0x1ad3}).toString();
            static final String L21 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1a75,0x0eb0,0xd28e,0x7f32,0xa59f,0xb76f,0xce8a,0x813e,0x55d2,0x4aba,0xcf7f,0xf906,0xc73b,0xbbbe,0xa328,0xa74f,0xe7a3,0x504d,0x2867,0x2100,0xdd9f,0x9bec,0xb6d3,0xc1f0,0x41b6,0xbc32,0xc3cb,0x71ef,0x0db1,0x584e,0xf650,0x62cc,0x9d91,0x209d,0x3754,0x58f6,0xec66,0xe005,0x7a22,0xba0f,0xe668,0x038e,0x6785,0x7728,0x90e1,0xeaf8,0x7a34,0x5d32,0x97d5,0xe573,0x93c2,0xdb7d,0x92d7,0xffdb,0x0b8c,0x17c9,0x162b,0x6f48,0xb022,0x748a,0xa630,0x1c69,0x4be9,0x392b,0x4a38,0x9972,0x082f,0x85fb,0x7ad0}, new char[]{0x4afc,0xcf16,0x0841,0x5d56,0xa643,0x9952,0x3948,0xc753,0xffae,0x97bb,0x7a49,0x03fd,0xa5bf,0xdde8,0x0be5,0xa35c,0xf96e,0x74aa,0x8588,0xe51a,0x774f,0x373a,0x1658,0x6f68,0xe607,0x2801,0x586e,0xea88,0x20f1,0x5897,0xc3b2,0xec03,0x9093,0xba2f,0x7a55,0xb041,0x7f46,0xbc5b,0x4a4e,0x41df,0xf624,0xb6aa,0xdb5d,0x9b8a,0x0dc3,0xcee5,0x0edd,0xe783,0x92b1,0xe070,0x1c05,0x62a0,0x7196,0xa76f,0x9df6,0x2172,0x1a1a,0xbbc9,0x4b87,0x7af0,0x93a3,0xb702,0x815b,0x55a6,0xd2e6,0x5034,0xc183,0x67f1,0x17e7}).toString();
            static final String L22 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x52c7,0xbf59,0x07bd,0x5e8a,0x2a1c,0xd0f5,0x6a2b,0x5fb5,0x1e59,0x293d}, new char[]{0x5283,0x2a73,0x5ee4,0x5fc0,0xd081,0x1e1f,0x07d2,0xbf35,0x6a42,0x295c}).plainString();
    }
}
