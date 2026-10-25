package com.utilitymod.anubis.module.impl.render;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import com.utilitymod.anubis.event.EventHandler;
import com.utilitymod.anubis.event.impl.BlockUpdateEvent;
import com.utilitymod.anubis.event.impl.PacketAppliedEvent;
import com.utilitymod.anubis.event.impl.Render3DEvent;
import com.utilitymod.anubis.event.impl.TickEvent;
import com.utilitymod.anubis.module.ClientModule;
import com.utilitymod.anubis.module.ModuleCategory;
import com.utilitymod.anubis.module.impl.client.PerformanceModule;
import com.utilitymod.anubis.module.setting.BooleanSetting;
import com.utilitymod.anubis.module.setting.ColorSetting;
import com.utilitymod.anubis.module.setting.ModeSetting;
import com.utilitymod.anubis.module.setting.NumberSetting;
import com.utilitymod.anubis.util.MobilePerformance;
import com.utilitymod.anubis.util.PerformanceBudget;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.world.debug.gizmo.GizmoDrawing;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.block.BlockState;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos.Mutable;

public class HoleEspModule extends ClientModule {
   private static final String MODE_ALL = LT.L1;
   private static final String MODE_HOLES_TUNNELS = LT.L2;
   private static final String MODE_HOLES_STAIRS = LT.L3;
   private static final String MODE_TUNNELS_STAIRS = LT.L4;
   private static final String MODE_HOLES = LT.L5;
   private static final String MODE_TUNNELS = LT.L6;
   private static final String MODE_STAIRS = LT.L7;
   private static final int TYPE_HOLE = 0;
   private static final int TYPE_HOLE_3X1 = 1;
   private static final int TYPE_TUNNEL = 2;
   private static final int TYPE_STAIR = 3;
   private static final int MAX_HITS_PER_CHUNK = 1024;
   private static final int MAX_RENDER_BOXES = 8192;
   private static final int HOLE_GRID_W = 20;
   private static final int ELYTRA_REFRESH_TICKS = 5;
   private static final int NORMAL_REFRESH_TICKS = 20;
   private static final float ELYTRA_SCAN_RADIUS = 12.0F;
   private static final float ELYTRA_SCAN_BUDGET_MS = 8.0F;
   private static final int[][] HORIZONTAL_DIRS = new int[][]{{1, 0}, {-1, 0}, {0, -1}, {0, 1}};
   private static final ThreadLocal<Mutable> PASSABLE_POS = ThreadLocal.withInitial(Mutable::new);
   private final ConcurrentHashMap<Long, List<HoleEspModule.Hit>> chunkCache = new ConcurrentHashMap<>();
   private final Queue<Long> scanQueue = new ConcurrentLinkedQueue<>();
   private final Set<Long> queued = ConcurrentHashMap.newKeySet();
   private long lastPlayerChunk = Long.MIN_VALUE;
   private int refreshTicks = 0;
   private ClientWorld sessionLevel;
   private boolean lastOptimized;
   private HoleEspModule.FastHoleScan activeScan;
   private boolean[] passableBuffer = new boolean[0];
   private final DrawStyle[] mobileStyles = new DrawStyle[4];
   private final ModeSetting sMode = this.addSetting(
      LT.L8, new ModeSetting(LT.L9, LT.L5, LT.L5, LT.L1, LT.L2, LT.L3, LT.L4, LT.L6, LT.L7)
   );
   private final NumberSetting sScanRadius = this.addSetting(LT.L10, new NumberSetting(LT.L11, 8.0F, 1.0F, 24.0F).step(1.0F));
   private final NumberSetting sChunksPerTick = this.addSetting(LT.L12, new NumberSetting(LT.L13, 64.0F, 1.0F, 100.0F).step(1.0F));
   private final NumberSetting sScanBudgetMs = this.addSetting(LT.L14, new NumberSetting(LT.L15, 3.0F, 1.0F, 10.0F).step(1.0F));
   private final BooleanSetting sAirOnly = this.addSetting(LT.L16, new BooleanSetting(LT.L17, true));
   private final NumberSetting sMinYOffset = this.addSetting(LT.L18, new NumberSetting(LT.L19, 0.0F, 0.0F, 319.0F).step(1.0F));
   private final NumberSetting sMaxYOffset = this.addSetting(LT.L20, new NumberSetting(LT.L21, 0.0F, 0.0F, 319.0F).step(1.0F));
   private final NumberSetting sMinHoleDepth = this.addSetting(LT.L22, new NumberSetting(LT.L23, 4.0F, 1.0F, 20.0F).step(1.0F));
   private final NumberSetting sMinTunnelLength = this.addSetting(LT.L24, new NumberSetting(LT.L25, 3.0F, 1.0F, 20.0F).step(1.0F));
   private final NumberSetting sMinTunnelHeight = this.addSetting(LT.L26, new NumberSetting(LT.L27, 2.0F, 1.0F, 10.0F).step(1.0F));
   private final NumberSetting sMaxTunnelHeight = this.addSetting(LT.L28, new NumberSetting(LT.L29, 3.0F, 2.0F, 10.0F).step(1.0F));
   private final NumberSetting sMinStairLength = this.addSetting(LT.L30, new NumberSetting(LT.L31, 3.0F, 1.0F, 20.0F).step(1.0F));
   private final NumberSetting sMinStairHeight = this.addSetting(LT.L32, new NumberSetting(LT.L33, 3.0F, 2.0F, 10.0F).step(1.0F));
   private final NumberSetting sMaxStairHeight = this.addSetting(LT.L34, new NumberSetting(LT.L35, 5.0F, 2.0F, 10.0F).step(1.0F));
   private final BooleanSetting sFill = this.addSetting(LT.L36, new BooleanSetting(LT.L37, true));
   private final BooleanSetting sOutline = this.addSetting(LT.L38, new BooleanSetting(LT.L39, true));
   private final NumberSetting sLineWidth = this.addSetting(LT.L40, new NumberSetting(LT.L41, 1.5F, 0.5F, 5.0F).step(0.5F));
   private final ColorSetting sHoleLine = this.addSetting(LT.L42, new ColorSetting(LT.L43, 1291845631));
   private final ColorSetting sHoleFill = this.addSetting(LT.L44, new ColorSetting(LT.L45, 1342177279));
   private final ColorSetting sHole3Line = this.addSetting(LT.L46, new ColorSetting(LT.L47, 644245094));
   private final ColorSetting sHole3Fill = this.addSetting(LT.L48, new ColorSetting(LT.L49, 644245094));
   private final ColorSetting sTunnelLine = this.addSetting(LT.L50, new ColorSetting(LT.L51, 1593835775));
   private final ColorSetting sTunnelFill = this.addSetting(LT.L52, new ColorSetting(LT.L53, 503316735));
   private final ColorSetting sStairLine = this.addSetting(LT.L54, new ColorSetting(LT.L55, 1610547455));
   private final ColorSetting sStairFill = this.addSetting(LT.L56, new ColorSetting(LT.L57, 520028415));

   public HoleEspModule() {
      super(LT.L58,
         dev.potato.spud.api.ProtectedText.fromInts(new int[]{0x56,0x46,0x1C,0xDE,0xE2,0x48,0x07,0xCF}).rawValue(),
         LT.L59, ModuleCategory.RENDER);
      List.of(
            this.sMode,
            this.sScanRadius,
            this.sAirOnly,
            this.sMinYOffset,
            this.sMaxYOffset,
            this.sMinHoleDepth,
            this.sMinTunnelLength,
            this.sMinTunnelHeight,
            this.sMaxTunnelHeight,
            this.sMinStairLength,
            this.sMinStairHeight,
            this.sMaxStairHeight
         )
         .forEach(setting -> setting.onChange(() -> {
            if (PerformanceModule.optimizationsEnabled()) {
               this.resetScan();
            }
         }));
   }

   @Override
   protected void onEnable() {
      this.resetScan();
   }

   private void resetScan() {
      this.lastPlayerChunk = Long.MIN_VALUE;
      this.refreshTicks = 0;
      this.chunkCache.clear();
      this.scanQueue.clear();
      this.queued.clear();
      this.activeScan = null;
   }

   @Override
   protected void onDisable() {
      this.resetScan();
      this.sessionLevel = null;
      this.passableBuffer = new boolean[0];
   }

   @EventHandler
   public void onTick(TickEvent event) {
      MinecraftClient mc = MinecraftClient.getInstance();
      boolean optimized = PerformanceModule.optimizationsEnabled();
      if (this.lastOptimized != optimized || optimized && this.sessionLevel != mc.world) {
         this.resetScan();
      }

      this.lastOptimized = optimized;
      this.sessionLevel = mc.world;
      if (mc.world != null && mc.player != null) {
         int cx = mc.player.getBlockX() >> 4;
         int cz = mc.player.getBlockZ() >> 4;
         long curChunk = ChunkPos.toLong(cx, cz);
         int radius = this.scanRadius(mc);
         boolean fastTravel = isFastTravel(mc);
         boolean periodicRefresh = ++this.refreshTicks >= (fastTravel ? 5 : 20);
         if (curChunk != this.lastPlayerChunk || periodicRefresh) {
            this.refreshTicks = 0;
            this.lastPlayerChunk = curChunk;
            this.chunkCache.keySet().removeIf(keyx -> {
               int kx = chunkX(keyx);
               int kz = chunkZ(keyx);
               return Math.abs(kx - cx) > radius || Math.abs(kz - cz) > radius;
            });
            List<long[]> toQueue = new ArrayList<>();
            Vec3d velocity = mc.player.getVelocity();
            double vx = velocity.x;
            double vz = velocity.z;
            double horizontalSpeed = Math.sqrt(vx * vx + vz * vz);
            if (horizontalSpeed > 1.0E-4) {
               vx /= horizontalSpeed;
               vz /= horizontalSpeed;
            }

            for (int x = cx - radius; x <= cx + radius; x++) {
               for (int z = cz - radius; z <= cz + radius; z++) {
                  long key = ChunkPos.toLong(x, z);
                  if (!this.chunkCache.containsKey(key)
                     && !this.queued.contains(key)
                     && (this.activeScan == null || this.activeScan.key != key)
                     && mc.world.getChunkManager().isChunkLoaded(x, z)) {
                     int dx = x - cx;
                     int dz = z - cz;
                     long distScore = (dx * dx + dz * dz) * 1024L;
                     long aheadScore = fastTravel ? Math.round((dx * vx + dz * vz) * 1536.0) : 0L;
                     toQueue.add(new long[]{key, distScore - aheadScore});
                  }
               }
            }

            toQueue.sort(Comparator.comparingLong(a -> a[1]));

            for (long[] entry : toQueue) {
               this.scanQueue.add(entry[0]);
               this.queued.add(entry[0]);
            }
         }

         int chunksPerTick = Math.max(1, Math.round(value(this.sChunksPerTick, 4.0F)));
         float budgetMs = fastTravel && !optimized ? Math.max(value(this.sScanBudgetMs, 3.0F), 8.0F) : value(this.sScanBudgetMs, 3.0F);
         long requested = (long)(Math.max(1.0F, budgetMs) * 1000000.0F);
         long deadline = optimized ? PerformanceBudget.acquire(requested) : System.nanoTime() + requested;
         long started = System.nanoTime();
         if (started < deadline) {
            try {
               int i = 0;

               while (i < chunksPerTick && System.nanoTime() < deadline) {
                  if (this.activeScan != null) {
                     if (!this.activeScan.validFor(mc.world, cx, cz, radius)) {
                        int sx = this.activeScan.chunk.getPos().x;
                        int sz = this.activeScan.chunk.getPos().z;
                        long key = this.activeScan.key;
                        this.activeScan = null;
                        if (Math.abs((long)sx - cx) <= radius
                           && Math.abs((long)sz - cz) <= radius
                           && mc.world.getChunkManager().isChunkLoaded(sx, sz)
                           && this.queued.add(key)) {
                           this.scanQueue.add(key);
                        }
                     } else if (this.activeScan.step()) {
                        this.storeHits(this.activeScan.key, this.activeScan.hits);
                        this.activeScan = null;
                        i++;
                     }
                  } else {
                     Long key = this.scanQueue.poll();
                     if (key == null) {
                        break;
                     }

                     this.queued.remove(key);
                     int sx = chunkX(key);
                     int sz = chunkZ(key);
                     if (mc.world.getChunkManager().isChunkLoaded(sx, sz)) {
                        this.scanChunk(mc.world, sx, sz, key);
                     }

                     if (this.activeScan == null) {
                        i++;
                     }
                  }
               }
            } finally {
               if (optimized) {
                  PerformanceBudget.record(started);
               }
            }
         }
      }
   }

   @EventHandler
   public void onPacketApplied(PacketAppliedEvent event) {
      if (PerformanceModule.optimizationsEnabled()) {
         if (event.packet instanceof ChunkDataS2CPacket chunk) {
            this.invalidateAround(chunk.getChunkX(), chunk.getChunkZ());
         }
      }
   }

   private void invalidateAround(int cx, int cz) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player != null) {
         int radius = this.scanRadius(mc) + 1;
         if (Math.abs((long)cx - (mc.player.getBlockX() >> 4)) <= radius && Math.abs((long)cz - (mc.player.getBlockZ() >> 4)) <= radius) {
            for (int x = cx - 1; x <= cx + 1; x++) {
               for (int z = cz - 1; z <= cz + 1; z++) {
                  long key = ChunkPos.toLong(x, z);
                  boolean known = this.chunkCache.remove(key) != null;
                  if (this.activeScan != null && this.activeScan.key == key) {
                     this.activeScan = null;
                     known = true;
                  }

                  if (known && this.queued.add(key)) {
                     this.scanQueue.add(key);
                  }
               }
            }

            this.lastPlayerChunk = Long.MIN_VALUE;
         }
      }
   }

   @EventHandler
   public void onBlockUpdate(BlockUpdateEvent event) {
      if (event.pos != null) {
         if (PerformanceModule.optimizationsEnabled()) {
            this.invalidateAround(event.pos.getX() >> 4, event.pos.getZ() >> 4);
         } else {
            long key = ChunkPos.toLong(event.pos.getX() >> 4, event.pos.getZ() >> 4);
            this.chunkCache.remove(key);
            if (this.queued.add(key)) {
               this.scanQueue.add(key);
            }
         }
      }
   }

   @EventHandler
   public void onRender3D(Render3DEvent event) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player != null) {
         boolean fill = this.sFill == null || this.sFill.value().get();
         boolean outline = this.sOutline == null || this.sOutline.value().get();
         if (fill || outline) {
            int px = mc.player.getBlockX() >> 4;
            int pz = mc.player.getBlockZ() >> 4;
            int radius = this.scanRadius(mc);
            float lineWidth = value(this.sLineWidth, 1.5F);
            boolean mobile = MobilePerformance.active();
            if (mobile) {
               Arrays.fill(this.mobileStyles, null);
            }

            int rendered = 0;

            for (Entry<Long, List<HoleEspModule.Hit>> entry : this.chunkCache.entrySet()) {
               long key = entry.getKey();
               if (Math.abs(chunkX(key) - px) <= radius && Math.abs(chunkZ(key) - pz) <= radius) {
                  for (HoleEspModule.Hit hit : entry.getValue()) {
                     if (++rendered > 8192) {
                        return;
                     }

                     DrawStyle style = mobile ? this.mobileStyles[hit.type()] : null;
                     if (style == null) {
                        style = !fill
                           ? DrawStyle.stroked(this.lineColor(hit.type()), lineWidth)
                           : (
                              !outline
                                 ? DrawStyle.filled(this.fillColor(hit.type()))
                                 : DrawStyle.filledAndStroked(this.lineColor(hit.type()), lineWidth, this.fillColor(hit.type()))
                           );
                        if (mobile) {
                           this.mobileStyles[hit.type()] = style;
                        }
                     }

                     GizmoDrawing.box(hit.box(), style).ignoreOcclusion();
                  }
               }
            }
         }
      }
   }

   private void scanChunk(ClientWorld level, int chunkX, int chunkZ, long chunkKey) {
      WorldChunk chunk = level.getChunkManager().getWorldChunk(chunkX, chunkZ);
      if (chunk != null) {
         int baseX = chunkX << 4;
         int baseZ = chunkZ << 4;
         int minBuildY = level.getDimension().minY();
         int maxBuildY = minBuildY + chunk.getSectionArray().length * 16;
         int minY = Math.min(maxBuildY - 1, minBuildY + Math.round(value(this.sMinYOffset, 0.0F)));
         int maxY = Math.max(minY + 1, maxBuildY - Math.round(value(this.sMaxYOffset, 0.0F)));
         HoleEspModule.Mode mode = this.currentMode();
         boolean airOnly = this.sAirOnly != null && this.sAirOnly.value().get();
         if (mode.holes() && !mode.tunnels() && !mode.stairs()) {
            if (PerformanceModule.optimizationsEnabled()) {
               this.activeScan = new HoleEspModule.FastHoleScan(chunk, chunkKey, baseX, baseZ, minY, maxY, airOnly);
            } else {
               this.scanHolesFast(level, chunkKey, baseX, baseZ, minY, maxY, airOnly);
            }
         } else {
            List<HoleEspModule.Hit> hits = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            Mutable pos = new Mutable();
            ChunkSection[] sections = chunk.getSectionArray();

            for (int si = 0; si < sections.length; si++) {
               ChunkSection section = sections[si];
               if (section != null && !section.isEmpty()) {
                  int sectionBaseY = minBuildY + si * 16;

                  for (int ly = 0; ly < 16; ly++) {
                     int y = sectionBaseY + ly;
                     if (y > minY && y < maxY) {
                        for (int z = 0; z < 16; z++) {
                           for (int x = 0; x < 16; x++) {
                              int wx = baseX + x;
                              int wz = baseZ + z;
                              pos.set(wx, y, wz);
                              if (isPassable(level, pos, airOnly)) {
                                 if (mode.holes() && !isPassable(level, wx, y - 1, wz, airOnly)) {
                                    this.checkHole(level, pos, airOnly, hits, seen);
                                    this.check3x1Hole(level, pos, airOnly, hits, seen);
                                 }

                                 if (mode.tunnels()) {
                                    this.checkTunnelStarts(level, pos, airOnly, hits, seen);
                                 }

                                 if (mode.stairs()) {
                                    this.checkStaircaseStarts(level, pos, airOnly, hits, seen);
                                 }

                                 if (hits.size() >= 1024) {
                                    this.chunkCache.put(chunkKey, List.copyOf(hits));
                                    return;
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }

            this.storeHits(chunkKey, hits);
         }
      }
   }

   private void storeHits(long key, List<HoleEspModule.Hit> hits) {
      HoleEspModule.Mode mode = this.currentMode();
      boolean cacheEmpty = PerformanceModule.optimizationsEnabled() && mode.holes() && !mode.tunnels() && !mode.stairs();
      if (hits.isEmpty() && !cacheEmpty) {
         this.chunkCache.remove(key);
      } else {
         this.chunkCache.put(key, List.copyOf(hits));
      }
   }

   private void scanHolesFast(ClientWorld level, long chunkKey, int baseX, int baseZ, int minY, int maxY, boolean airOnly) {
      int gridMinY = minY - 1;
      int gridMaxY = maxY;
      int yCount = gridMaxY - gridMinY + 1;
      if (yCount <= 1) {
         this.chunkCache.remove(chunkKey);
      } else {
         boolean[] passable = new boolean[yCount * 20 * 20];
         Mutable pos = new Mutable();

         for (int yi = 0; yi < yCount; yi++) {
            int y = gridMinY + yi;
            int yBase = yi * 20 * 20;

            for (int lz = -1; lz <= 18; lz++) {
               int zBase = yBase + (lz + 1) * 20;

               for (int lx = -1; lx <= 18; lx++) {
                  pos.set(baseX + lx, y, baseZ + lz);
                  passable[zBase + lx + 1] = isPassable(level, pos, airOnly);
               }
            }
         }

         int minDepth = Math.round(value(this.sMinHoleDepth, 4.0F));
         List<HoleEspModule.Hit> hits = new ArrayList<>();
         Set<String> seen = new HashSet<>();

         for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
               int y = minY;

               while (y < maxY) {
                  if (grid(passable, y, lx, lz, gridMinY) && !grid(passable, y - 1, lx, lz, gridMinY)) {
                     int depth = count1x1Depth(passable, y, lx, lz, gridMinY, maxY);
                     if (depth >= minDepth) {
                        addHit(hits, seen, new Box(baseX + lx, y, baseZ + lz, baseX + lx + 1, y + depth, baseZ + lz + 1), 0);
                        if (hits.size() >= 1024) {
                           this.chunkCache.put(chunkKey, List.copyOf(hits));
                           return;
                        }
                     }

                     int depthX = count3x1DepthX(passable, y, lx, lz, gridMinY, maxY);
                     if (depthX >= minDepth) {
                        addHit(hits, seen, new Box(baseX + lx, y, baseZ + lz, baseX + lx + 3, y + depthX, baseZ + lz + 1), 1);
                        if (hits.size() >= 1024) {
                           this.chunkCache.put(chunkKey, List.copyOf(hits));
                           return;
                        }
                     }

                     int depthZ = count3x1DepthZ(passable, y, lx, lz, gridMinY, maxY);
                     if (depthZ >= minDepth) {
                        addHit(hits, seen, new Box(baseX + lx, y, baseZ + lz, baseX + lx + 1, y + depthZ, baseZ + lz + 3), 1);
                        if (hits.size() >= 1024) {
                           this.chunkCache.put(chunkKey, List.copyOf(hits));
                           return;
                        }
                     }

                     y += Math.max(1, Math.max(depth, Math.max(depthX, depthZ)));
                  } else {
                     y++;
                  }
               }
            }
         }

         if (hits.isEmpty()) {
            this.chunkCache.remove(chunkKey);
         } else {
            this.chunkCache.put(chunkKey, List.copyOf(hits));
         }
      }
   }

   private static int count1x1Depth(boolean[] grid, int startY, int lx, int lz, int gridMinY, int maxY) {
      int y = startY;

      while (
         y < maxY
            && grid(grid, y, lx, lz, gridMinY)
            && !grid(grid, y, lx - 1, lz, gridMinY)
            && !grid(grid, y, lx + 1, lz, gridMinY)
            && !grid(grid, y, lx, lz - 1, gridMinY)
            && !grid(grid, y, lx, lz + 1, gridMinY)
      ) {
         y++;
      }

      return y - startY;
   }

   private static int count3x1DepthX(boolean[] grid, int startY, int lx, int lz, int gridMinY, int maxY) {
      int y = startY;

      while (
         y < maxY
            && grid(grid, y, lx, lz, gridMinY)
            && grid(grid, y, lx + 1, lz, gridMinY)
            && grid(grid, y, lx + 2, lz, gridMinY)
            && !grid(grid, y, lx - 1, lz, gridMinY)
            && !grid(grid, y, lx + 3, lz, gridMinY)
            && !grid(grid, y, lx, lz - 1, gridMinY)
            && !grid(grid, y, lx, lz + 1, gridMinY)
            && !grid(grid, y, lx + 1, lz - 1, gridMinY)
            && !grid(grid, y, lx + 1, lz + 1, gridMinY)
            && !grid(grid, y, lx + 2, lz - 1, gridMinY)
            && !grid(grid, y, lx + 2, lz + 1, gridMinY)
      ) {
         y++;
      }

      return y - startY;
   }

   private static int count3x1DepthZ(boolean[] grid, int startY, int lx, int lz, int gridMinY, int maxY) {
      int y = startY;

      while (
         y < maxY
            && grid(grid, y, lx, lz, gridMinY)
            && grid(grid, y, lx, lz + 1, gridMinY)
            && grid(grid, y, lx, lz + 2, gridMinY)
            && !grid(grid, y, lx, lz - 1, gridMinY)
            && !grid(grid, y, lx, lz + 3, gridMinY)
            && !grid(grid, y, lx - 1, lz, gridMinY)
            && !grid(grid, y, lx + 1, lz, gridMinY)
            && !grid(grid, y, lx - 1, lz + 1, gridMinY)
            && !grid(grid, y, lx + 1, lz + 1, gridMinY)
            && !grid(grid, y, lx - 1, lz + 2, gridMinY)
            && !grid(grid, y, lx + 1, lz + 2, gridMinY)
      ) {
         y++;
      }

      return y - startY;
   }

   private static boolean grid(boolean[] grid, int y, int lx, int lz, int gridMinY) {
      int yi = y - gridMinY;
      return grid[yi * 20 * 20 + (lz + 1) * 20 + lx + 1];
   }

   private void checkHole(ClientWorld level, BlockPos pos, boolean airOnly, List<HoleEspModule.Hit> hits, Set<String> seen) {
      if (this.isValidHoleSection(level, pos, airOnly)) {
         Mutable current = new Mutable(pos.getX(), pos.getY(), pos.getZ());

         while (this.isValidHoleSection(level, current, airOnly)) {
            current.move(0, 1, 0);
         }

         int depth = current.getY() - pos.getY();
         if (depth >= Math.round(value(this.sMinHoleDepth, 4.0F))) {
            addHit(
               hits,
               seen,
               new Box(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, current.getY(), pos.getZ() + 1),
               0
            );
         }
      }
   }

   private void check3x1Hole(ClientWorld level, BlockPos pos, boolean airOnly, List<HoleEspModule.Hit> hits, Set<String> seen) {
      if (this.isValid3x1HoleSectionX(level, pos, airOnly)) {
         Mutable current = new Mutable(pos.getX(), pos.getY(), pos.getZ());

         while (this.isValid3x1HoleSectionX(level, current, airOnly)) {
            current.move(0, 1, 0);
         }

         int depth = current.getY() - pos.getY();
         if (depth >= Math.round(value(this.sMinHoleDepth, 4.0F))) {
            addHit(
               hits,
               seen,
               new Box(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 3, current.getY(), pos.getZ() + 1),
               1
            );
         }
      }

      if (this.isValid3x1HoleSectionZ(level, pos, airOnly)) {
         Mutable current = new Mutable(pos.getX(), pos.getY(), pos.getZ());

         while (this.isValid3x1HoleSectionZ(level, current, airOnly)) {
            current.move(0, 1, 0);
         }

         int depth = current.getY() - pos.getY();
         if (depth >= Math.round(value(this.sMinHoleDepth, 4.0F))) {
            addHit(
               hits,
               seen,
               new Box(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, current.getY(), pos.getZ() + 3),
               1
            );
         }
      }
   }

   private void checkTunnelStarts(ClientWorld level, BlockPos pos, boolean airOnly, List<HoleEspModule.Hit> hits, Set<String> seen) {
      int minLength = Math.round(value(this.sMinTunnelLength, 3.0F));

      for (int[] dir : HORIZONTAL_DIRS) {
         Mutable previous = new Mutable(pos.getX() - dir[0], pos.getY(), pos.getZ() - dir[1]);
         if (!this.isTunnelSection(level, previous, dir, airOnly)) {
            Mutable current = new Mutable(pos.getX(), pos.getY(), pos.getZ());
            BlockPos start = null;
            BlockPos end = null;
            int steps = 0;
            int maxHeight = 0;
            if (this.isTunnelSection(level, current, dir, airOnly)) {
               start = current.toImmutable();
            }

            while (this.isTunnelSection(level, current, dir, airOnly)) {
               maxHeight = Math.max(maxHeight, this.getTunnelHeight(level, current, airOnly));
               end = current.toImmutable();
               current.move(dir[0], 0, dir[1]);
               steps++;
            }

            if (start != null && end != null && steps >= minLength) {
               addHit(
                  hits,
                  seen,
                  new Box(
                     Math.min(start.getX(), end.getX()),
                     start.getY(),
                     Math.min(start.getZ(), end.getZ()),
                     Math.max(start.getX(), end.getX()) + 1,
                     start.getY() + maxHeight,
                     Math.max(start.getZ(), end.getZ()) + 1
                  ),
                  2
               );
            }
         }
      }
   }

   private void checkStaircaseStarts(ClientWorld level, BlockPos pos, boolean airOnly, List<HoleEspModule.Hit> hits, Set<String> seen) {
      int minLength = Math.round(value(this.sMinStairLength, 3.0F));

      for (int[] dir : HORIZONTAL_DIRS) {
         Mutable previous = new Mutable(pos.getX() - dir[0], pos.getY() - 1, pos.getZ() - dir[1]);
         if (!this.isStaircaseSection(level, previous, dir, airOnly)) {
            Mutable current = new Mutable(pos.getX(), pos.getY(), pos.getZ());
            int steps = 0;
            List<Box> boxes = new ArrayList<>();

            while (this.isStaircaseSection(level, current, dir, airOnly)) {
               int height = this.getStaircaseHeight(level, current, airOnly);
               boxes.add(
                  new Box(
                     current.getX(),
                     current.getY(),
                     current.getZ(),
                     current.getX() + 1,
                     current.getY() + height,
                     current.getZ() + 1
                  )
               );
               current.move(dir[0], 1, dir[1]);
               steps++;
            }

            if (steps >= minLength) {
               for (Box box : boxes) {
                  addHit(hits, seen, box, 3);
               }
            }
         }
      }
   }

   private boolean isValidHoleSection(ClientWorld level, BlockPos pos, boolean airOnly) {
      return isPassable(level, pos, airOnly)
         && !isPassable(level, pos.getX(), pos.getY(), pos.getZ() - 1, airOnly)
         && !isPassable(level, pos.getX(), pos.getY(), pos.getZ() + 1, airOnly)
         && !isPassable(level, pos.getX() + 1, pos.getY(), pos.getZ(), airOnly)
         && !isPassable(level, pos.getX() - 1, pos.getY(), pos.getZ(), airOnly);
   }

   private boolean isValid3x1HoleSectionX(ClientWorld level, BlockPos pos, boolean airOnly) {
      int x = pos.getX();
      int y = pos.getY();
      int z = pos.getZ();
      return isPassable(level, x, y, z, airOnly)
         && isPassable(level, x + 1, y, z, airOnly)
         && isPassable(level, x + 2, y, z, airOnly)
         && !isPassable(level, x, y, z - 1, airOnly)
         && !isPassable(level, x, y, z + 1, airOnly)
         && !isPassable(level, x + 1, y, z - 1, airOnly)
         && !isPassable(level, x + 1, y, z + 1, airOnly)
         && !isPassable(level, x + 2, y, z - 1, airOnly)
         && !isPassable(level, x + 2, y, z + 1, airOnly)
         && !isPassable(level, x - 1, y, z, airOnly)
         && !isPassable(level, x + 3, y, z, airOnly);
   }

   private boolean isValid3x1HoleSectionZ(ClientWorld level, BlockPos pos, boolean airOnly) {
      int x = pos.getX();
      int y = pos.getY();
      int z = pos.getZ();
      return isPassable(level, x, y, z, airOnly)
         && isPassable(level, x, y, z + 1, airOnly)
         && isPassable(level, x, y, z + 2, airOnly)
         && !isPassable(level, x - 1, y, z, airOnly)
         && !isPassable(level, x + 1, y, z, airOnly)
         && !isPassable(level, x - 1, y, z + 1, airOnly)
         && !isPassable(level, x + 1, y, z + 1, airOnly)
         && !isPassable(level, x - 1, y, z + 2, airOnly)
         && !isPassable(level, x + 1, y, z + 2, airOnly)
         && !isPassable(level, x, y, z - 1, airOnly)
         && !isPassable(level, x, y, z + 3, airOnly);
   }

   private boolean isTunnelSection(ClientWorld level, BlockPos pos, int[] dir, boolean airOnly) {
      int height = this.getTunnelHeight(level, pos, airOnly);
      if (height >= Math.round(value(this.sMinTunnelHeight, 2.0F)) && height <= Math.round(value(this.sMaxTunnelHeight, 3.0F))) {
         if (!isPassable(level, pos.getX(), pos.getY() - 1, pos.getZ(), airOnly)
            && !isPassable(level, pos.getX(), pos.getY() + height, pos.getZ(), airOnly)) {
            boolean alongX = dir[0] != 0;

            for (int i = 0; i < height; i++) {
               int y = pos.getY() + i;
               if (alongX) {
                  if (isPassable(level, pos.getX(), y, pos.getZ() - 1, airOnly)
                     || isPassable(level, pos.getX(), y, pos.getZ() + 1, airOnly)) {
                     return false;
                  }
               } else if (isPassable(level, pos.getX() - 1, y, pos.getZ(), airOnly)
                  || isPassable(level, pos.getX() + 1, y, pos.getZ(), airOnly)) {
                  return false;
               }
            }

            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private boolean isStaircaseSection(ClientWorld level, BlockPos pos, int[] dir, boolean airOnly) {
      int height = this.getStaircaseHeight(level, pos, airOnly);
      if (height >= Math.round(value(this.sMinStairHeight, 3.0F)) && height <= Math.round(value(this.sMaxStairHeight, 5.0F))) {
         if (!isPassable(level, pos.getX(), pos.getY() - 1, pos.getZ(), airOnly)
            && !isPassable(level, pos.getX(), pos.getY() + height, pos.getZ(), airOnly)) {
            boolean alongX = dir[0] != 0;

            for (int i = 0; i < height; i++) {
               int y = pos.getY() + i;
               if (alongX) {
                  if (isPassable(level, pos.getX(), y, pos.getZ() - 1, airOnly)
                     || isPassable(level, pos.getX(), y, pos.getZ() + 1, airOnly)) {
                     return false;
                  }
               } else if (isPassable(level, pos.getX() - 1, y, pos.getZ(), airOnly)
                  || isPassable(level, pos.getX() + 1, y, pos.getZ(), airOnly)) {
                  return false;
               }
            }

            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private int getTunnelHeight(ClientWorld level, BlockPos pos, boolean airOnly) {
      int height = 0;
      int max = Math.round(value(this.sMaxTunnelHeight, 3.0F));

      while (height < max && isPassable(level, pos.getX(), pos.getY() + height, pos.getZ(), airOnly)) {
         height++;
      }

      return height;
   }

   private int getStaircaseHeight(ClientWorld level, BlockPos pos, boolean airOnly) {
      int height = 0;
      int max = Math.round(value(this.sMaxStairHeight, 5.0F));

      while (height < max && isPassable(level, pos.getX(), pos.getY() + height, pos.getZ(), airOnly)) {
         height++;
      }

      return height;
   }

   private static boolean isPassable(ClientWorld level, int x, int y, int z, boolean airOnly) {
      return isPassable(level, PASSABLE_POS.get().set(x, y, z), airOnly);
   }

   private static boolean isPassable(ClientWorld level, BlockPos pos, boolean airOnly) {
      BlockState state = level.getBlockState(pos);
      if (state.isAir()) {
         return true;
      }

      if (airOnly) {
         return false;
      }

      VoxelShape shape = state.getCollisionShape(level, pos);
      return shape.isEmpty() || !VoxelShapes.fullCube().equals(shape);
   }

   private static void addHit(List<HoleEspModule.Hit> hits, Set<String> seen, Box box, int type) {
      String key = type
         + LT2.L1
         + box.minX
         + LT2.L2
         + box.minY
         + LT2.L2
         + box.minZ
         + LT2.L2
         + box.maxX
         + LT2.L2
         + box.maxY
         + LT2.L2
         + box.maxZ;
      if (seen.add(key)) {
         for (HoleEspModule.Hit hit : hits) {
            if (hit.type() == type && hit.box().intersects(box)) {
               return;
            }
         }

         hits.add(new HoleEspModule.Hit(box, type));
      }
   }

   private HoleEspModule.Mode currentMode() {
      String mode = this.sMode == null ? LT.L1 : this.sMode.value();
      // Mask-aware mode switch: selected values are PUA carriers, plain case labels would never match.
      if (com.utilitymod.anubis.util.TextMask.eq(mode, LT.L2)) return new HoleEspModule.Mode(true, true, false);
      if (com.utilitymod.anubis.util.TextMask.eq(mode, LT.L3)) return new HoleEspModule.Mode(true, false, true);
      if (com.utilitymod.anubis.util.TextMask.eq(mode, LT.L4)) return new HoleEspModule.Mode(false, true, true);
      if (com.utilitymod.anubis.util.TextMask.eq(mode, LT.L5)) return new HoleEspModule.Mode(true, false, false);
      if (com.utilitymod.anubis.util.TextMask.eq(mode, LT.L6)) return new HoleEspModule.Mode(false, true, false);
      if (com.utilitymod.anubis.util.TextMask.eq(mode, LT.L7)) return new HoleEspModule.Mode(false, false, true);
      return new HoleEspModule.Mode(true, true, true);
   }

   private int lineColor(int type) {
      return switch (type) {
         case 1 -> color(this.sHole3Line, 1610589440);
         case 2 -> color(this.sTunnelLine, 1593835775);
         case 3 -> color(this.sStairLine, 1610547455);
         default -> color(this.sHoleLine, 1610547200);
      };
   }

   private int fillColor(int type) {
      return switch (type) {
         case 1 -> color(this.sHole3Fill, 520070400);
         case 2 -> color(this.sTunnelFill, 503316735);
         case 3 -> color(this.sStairFill, 520028415);
         default -> color(this.sHoleFill, 520028160);
      };
   }

   private static int chunkX(long key) {
      return (int)key;
   }

   private static int chunkZ(long key) {
      return (int)(key >> 32);
   }

   private int scanRadius(MinecraftClient mc) {
      int renderDistance = (Integer)mc.options.getViewDistance().getValue();
      float configuredValue = value(this.sScanRadius, 4.0F);
      if (isFastTravel(mc)) {
         configuredValue = Math.max(configuredValue, 12.0F);
      }

      int configured = Math.max(1, Math.round(configuredValue));
      return Math.min(renderDistance, configured);
   }

   private static boolean isFastTravel(MinecraftClient mc) {
      if (mc.player == null) {
         return false;
      }

      Vec3d velocity = mc.player.getVelocity();
      double horizontalSpeedSq = velocity.x * velocity.x + velocity.z * velocity.z;
      return mc.player.isGliding() || horizontalSpeedSq > 0.18;
   }

   private static float value(NumberSetting setting, float fallback) {
      return setting == null ? fallback : setting.value()[0];
   }

   private static int color(ColorSetting setting, int fallback) {
      return setting == null ? fallback : setting.argb();
   }

   private final class FastHoleScan {
      final WorldChunk chunk;
      final ClientWorld level;
      final WorldChunk[] neighbors = new WorldChunk[9];
      final long key;
      final int baseX;
      final int baseZ;
      final int minY;
      final int maxY;
      final int gridMinY;
      final int yCount;
      final int minDepth;
      final boolean airOnly;
      final boolean[] passable;
      final List<HoleEspModule.Hit> hits = new ArrayList<>();
      final Set<String> seen = new HashSet<>();
      final Mutable pos = new Mutable();
      int row;
      int column;

      FastHoleScan(WorldChunk chunk, long key, int baseX, int baseZ, int minY, int maxY, boolean airOnly) {
         this.chunk = chunk;
         this.level = HoleEspModule.this.sessionLevel;
         this.key = key;
         this.baseX = baseX;
         this.baseZ = baseZ;
         this.minY = minY;
         this.maxY = maxY;
         this.gridMinY = minY - 1;
         this.yCount = maxY - this.gridMinY + 1;
         this.minDepth = Math.round(HoleEspModule.value(HoleEspModule.this.sMinHoleDepth, 4.0F));
         this.airOnly = airOnly;
         int size = Math.max(0, this.yCount) * 20 * 20;
         if (HoleEspModule.this.passableBuffer.length < size) {
            HoleEspModule.this.passableBuffer = new boolean[size];
         }

         this.passable = HoleEspModule.this.passableBuffer;

         for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
               this.neighbors[(dx + 1) * 3 + dz + 1] = this.level.getChunkManager().getWorldChunk((baseX >> 4) + dx, (baseZ >> 4) + dz);
            }
         }
      }

      boolean validFor(ClientWorld currentLevel, int centerX, int centerZ, int radius) {
         if (currentLevel == this.level
            && Math.abs((long)this.chunk.getPos().x - centerX) <= radius
            && Math.abs((long)this.chunk.getPos().z - centerZ) <= radius) {
            for (int dx = -1; dx <= 1; dx++) {
               for (int dz = -1; dz <= 1; dz++) {
                  if (this.neighbors[(dx + 1) * 3 + dz + 1] != this.level.getChunkManager().getWorldChunk((this.baseX >> 4) + dx, (this.baseZ >> 4) + dz)) {
                     return false;
                  }
               }
            }

            return true;
         } else {
            return false;
         }
      }

      boolean step() {
         if (this.yCount <= 1) {
            return true;
         }

         if (this.row < this.yCount) {
            int y = this.gridMinY + this.row;
            int yBase = this.row++ * 20 * 20;

            for (int lz = -1; lz <= 18; lz++) {
               int zBase = yBase + (lz + 1) * 20;

               for (int lx = -1; lx <= 18; lx++) {
                  this.pos.set(this.baseX + lx, y, this.baseZ + lz);
                  this.passable[zBase + lx + 1] = HoleEspModule.isPassable(this.level, this.pos, this.airOnly);
               }
            }

            return false;
         } else {
            if (this.column >= 256) {
               return true;
            }

            int lx = this.column & 15;
            int lz = this.column++ >> 4;
            int y = this.minY;

            while (y < this.maxY) {
               if (HoleEspModule.grid(this.passable, y, lx, lz, this.gridMinY) && !HoleEspModule.grid(this.passable, y - 1, lx, lz, this.gridMinY)) {
                  int depth = HoleEspModule.count1x1Depth(this.passable, y, lx, lz, this.gridMinY, this.maxY);
                  if (depth >= this.minDepth) {
                     HoleEspModule.addHit(
                        this.hits, this.seen, new Box(this.baseX + lx, y, this.baseZ + lz, this.baseX + lx + 1, y + depth, this.baseZ + lz + 1), 0
                     );
                  }

                  if (this.hits.size() >= 1024) {
                     return true;
                  }

                  int depthX = HoleEspModule.count3x1DepthX(this.passable, y, lx, lz, this.gridMinY, this.maxY);
                  if (depthX >= this.minDepth) {
                     HoleEspModule.addHit(
                        this.hits, this.seen, new Box(this.baseX + lx, y, this.baseZ + lz, this.baseX + lx + 3, y + depthX, this.baseZ + lz + 1), 1
                     );
                  }

                  if (this.hits.size() >= 1024) {
                     return true;
                  }

                  int depthZ = HoleEspModule.count3x1DepthZ(this.passable, y, lx, lz, this.gridMinY, this.maxY);
                  if (depthZ >= this.minDepth) {
                     HoleEspModule.addHit(
                        this.hits, this.seen, new Box(this.baseX + lx, y, this.baseZ + lz, this.baseX + lx + 1, y + depthZ, this.baseZ + lz + 3), 1
                     );
                  }

                  if (this.hits.size() >= 1024) {
                     return true;
                  }

                  y += Math.max(1, Math.max(depth, Math.max(depthX, depthZ)));
               } else {
                  y++;
               }
            }

            return this.column >= 256;
         }
      }
   }

   private record Hit(Box box, int type) {
   }

   private record Mode(boolean holes, boolean tunnels, boolean stairs) {
   }


    // cloak literals - runtime PUA carriers (cloak) or functional plain (LT)
    private static final class LT {
            static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xfe47,0x3cbc,0xff2f}, new char[]{0xff6e,0xfe2b,0x3cd0,0x1307}).toString();
            static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xbbf5,0x159b,0xf7f0,0x38eb,0x0503,0xd929,0xa784,0x6971,0x8d6f,0xbe32,0xe15b,0xfc0d,0x3036,0x0ecc,0x3b22}, new char[]{0xd961,0xfc62,0xbe5e,0x3053,0x15e8,0x3b02,0x695a,0x38cb,0xbba1,0xa7f1,0xe135,0xf79e,0x0ea9,0x056f,0x8d1c}).toString();
            static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x255d,0x46b9,0x6533,0xf367,0x6079,0x36d0,0xcbbf,0xfe65,0xaed4,0xfc85,0xb238,0xb272,0x9679,0xe644}, new char[]{0x3698,0xe62b,0x6015,0xfe00,0xb24b,0xcb9f,0xfcae,0x4699,0xb221,0xaea0,0x253c,0x655a,0xf315,0x960a}).toString();
            static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x00ca,0xc75b,0x7f30,0x2b54,0x6782,0x9ec9,0x194f,0x7bfe,0x751d,0xe91d,0xb4b9,0xc5e6,0x1250,0x35f3,0xd7d5,0xac35}, new char[]{0xc70f,0xd7a0,0xb4d7,0x00a4,0x192a,0x123c,0x3580,0x9ee9,0xc5cd,0x753d,0x7bad,0xe969,0x67e3,0x7f59,0xac47,0x2b27}).toString();
            static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xfacb,0x2a76,0xdf13,0x09fd,0x649a}, new char[]{0x64d2,0x2a19,0xdf7f,0xfaae,0x098e}).toString();
            static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0f80,0x9bf8,0x92c9,0x5dd3,0x8225,0x06c3,0xe0e0}, new char[]{0x0697,0x0ff5,0x9b96,0x824b,0xe085,0x92a5,0x5da0}).toString();
            static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x071c,0x129b,0x3263,0xd833,0x703a,0xae23}, new char[]{0x3230,0x0768,0x12fa,0xd85a,0x7048,0xae50}).toString();
            static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x32fe,0x6c53,0x3691,0xa7a9,0x3e44,0x0140,0x3184,0x7845,0x45c6}, new char[]{0xa7cd,0x36f4,0x7831,0x3e21,0x45a5,0x0134,0x3297,0x6c3c,0x31ea}).plainString();
            static final String L9 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4f14,0x56b4,0x2b62,0xfce0,0xbd2d,0xff15,0xa30b,0xee1d,0x48cc}, new char[]{0x2b26,0xa36e,0xfc94,0xff70,0xbd4e,0x4f60,0x56dd,0xee72,0x48a2}).toString();
            static final String L10 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8815,0x1f79,0x3417,0x108a,0xa1fa,0x61ec,0x8f7c,0x1a9d,0xbe07,0xb793,0x5715}, new char[]{0xb7e0,0xbe64,0x1f18,0xa194,0x10a7,0x619e,0x8874,0x8f18,0x577c,0x3462,0x1aee}).plainString();
            static final String L11 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x92f9,0x3d4e,0x8c55,0xae1a,0x9432,0x7999,0xd8de,0xd3c0,0xb385,0x665b,0x0207}, new char[]{0x92aa,0x79fa,0x663a,0x3d20,0xd3e0,0x9460,0xb3e4,0x8c31,0xae73,0x0272,0xd8ad}).toString();
            static final String L12 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x59d3,0x7b4b,0x4041,0x59b0,0x023b,0x5581,0xdbaa,0x39d4,0x474e,0xf6e8,0xa08b}, new char[]{0xf68b,0xdbc2,0x7b3e,0x59de,0xa0e0,0x59a0,0x4763,0x39a0,0x55e8,0x4022,0x0250}).plainString();
            static final String L13 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2de7,0xd663,0xee75,0x11b5,0x8b9e,0x1647,0x812c,0xdb7e,0xdc30,0x8fa7,0xdb89}, new char[]{0xdbca,0x162f,0xee00,0x11db,0x2d8c,0xdb0d,0x8f88,0xd637,0x8145,0xdc53,0x8bf5}).toString();
            static final String L14 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x87cb,0x2c74,0xaafd,0xdca8,0x5a40,0xb5c0,0x4c94,0xdc5d,0xa2e8,0x9ee8,0x4e96,0xf8b5,0x4029,0xc7af}, new char[]{0x87b8,0x5a23,0xf8d4,0xa286,0x2c59,0xb5a2,0xdc28,0x4cf0,0xdccf,0xaa98,0x4ee2,0xc782,0x9e85,0x405a}).plainString();
            static final String L15 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x57da,0x72a4,0xbd4f,0x3914,0x578b,0x7fe7,0xba33,0x2e7c,0xa62f,0x01c6,0x56ab,0xfd28,0x8cdd,0xeab9}, new char[]{0x5789,0x2e1f,0x57ea,0x01a8,0xea99,0x3956,0x56de,0xba57,0x7f80,0x8cb8,0xbd3b,0xa60f,0xfd45,0x72d7}).toString();
            static final String L16 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc342,0xc953,0xb4cc,0x46fa,0xf4b3,0x0859,0x27f8,0x7766,0xab96,0xcfbe,0xb456,0x1a76,0x7978,0x1ed7,0xbc2b}, new char[]{0xc93c,0x1eb9,0x770a,0x4683,0xb4e1,0xb437,0x2791,0xbc59,0x7955,0xabf4,0xcfd2,0x1a19,0xc321,0x0832,0xf4c0}).plainString();
            static final String L17 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x521d,0xa3ba,0x4cd2,0x27f6,0xe85f,0x27b4,0xd3ec,0xd9a3,0xe46e,0x0159,0x63e4,0x4a4d,0xb151,0x1db7,0x0e19}, new char[]{0x27fb,0x0e77,0xd380,0x5264,0x4a6d,0x63a5,0x0130,0xe82d,0x27d6,0x1df5,0x4cbe,0xb13e,0xa3d9,0xe405,0xd9d0}).toString();
            static final String L18 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3284,0xd775,0x5bc3,0x353b,0x95c9,0x16d6,0x592b,0x90cf,0x5649,0x920f,0xa558,0x0af0}, new char[]{0x32e9,0x16bf,0x95a7,0x5bee,0x3542,0xa575,0x9260,0x562f,0x90a9,0x0a83,0xd710,0x595f}).plainString();
            static final String L19 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3020,0xd9a3,0xc78d,0xea69,0x6d33,0xceb2,0xe5c8,0x17d1,0x0527,0xe63f,0x1381,0x4aaa}, new char[]{0xe585,0x17b8,0x0549,0xe61f,0xd9fa,0x3000,0x6d7c,0xc7eb,0x13e7,0xcec1,0xea0c,0x4ade}).toString();
            static final String L20 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xef46,0xe957,0xdb0a,0xdf2a,0xef61,0xdd2b,0x44c5,0x7f64,0x889b,0x98f6,0x421f,0x34b9}, new char[]{0xe93a,0x9897,0xdf52,0x7f49,0x34c0,0x44e8,0xdb65,0xdd4d,0xef07,0x88e8,0x427a,0xef32}).plainString();
            static final String L21 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc5ee,0x91fa,0xfb34,0xba4e,0xbc82,0x917a,0xe061,0x02a3,0x0f3c,0x937b,0x0c29,0xf055}, new char[]{0xba03,0x919b,0x0f44,0x915a,0xfb6d,0x0c09,0xe02e,0xbce4,0x931d,0x02d0,0xf030,0xc59a}).toString();
            static final String L22 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x403e,0x51a2,0x9d93,0x802f,0x3079,0x6383,0x4eea,0xccfd,0x60c9,0xbdc3,0xb4d0,0x4b94,0xa1fe,0x83fd}, new char[]{0x8390,0x4e83,0xb4be,0x60e4,0x4bfc,0xbdac,0x51ce,0x63e6,0xa1d3,0x9df7,0xcc98,0x3009,0x805b,0x4056}).plainString();
            static final String L23 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xeb77,0x483b,0xb293,0x2bc4,0x093d,0x0952,0x6a4c,0x4336,0xa5a7,0x5de1,0x2a69,0xe383,0xb6ab,0xbef9}, new char[]{0x6a01,0x093b,0x0953,0xeb57,0xbeb1,0x2bab,0x435a,0x2a0c,0xe3a3,0xa5e3,0x5d84,0xb6db,0x484f,0xb2fb}).toString();
            static final String L24 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf2bd,0xf4f4,0xd873,0x79ae,0x808d,0xb04e,0x19db,0x4aae,0x520a,0x14cd,0xec2c,0xcf25,0x971b,0x4e00,0x2039,0x0fcc,0xe5ae}, new char[]{0x4e6d,0x79c7,0x2057,0xb063,0x527e,0x14b8,0xd81d,0xe5c0,0xf491,0x19b7,0x0fe1,0xf2d1,0xcf40,0xec42,0x4ac9,0x976f,0x80e5}).plainString();
            static final String L25 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xaa2a,0x5bc5,0x2c45,0xdef4,0x6127,0x20c9,0x6d6c,0xd646,0xe8a4,0x2a7f,0xd4c2,0xfc5a,0x7898,0xc875,0xba17,0x3306,0x3f7b}, new char[]{0xaa67,0x20a0,0xd628,0x2a5f,0xd496,0x6d19,0xe8ca,0x3368,0x6142,0x78f4,0xfc7a,0xc839,0xba72,0x2c2b,0x5ba2,0x3f0f,0xde9c}).toString();
            static final String L26 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe269,0xcfd6,0x4594,0x7520,0xa9eb,0x7f61,0xde4a,0xa7b4,0x3c1d,0x0d1a,0xda6d,0xfcc5,0x3603,0xcf6b,0x14fc,0x680d,0x06c6}, new char[]{0xa986,0x06af,0x0d74,0x362e,0x1488,0xa7c1,0xde24,0xe207,0x7f04,0xda01,0xcf46,0x45fc,0x3c78,0x7549,0xfca2,0x6865,0xcfa2}).plainString();
            static final String L27 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe62d,0xa8c0,0x8da0,0x150d,0xabe5,0xc5ce,0xd453,0x0c92,0xad1a,0xaeee,0x5040,0x2e56,0xa070,0x8a20,0x2d93,0x68bd,0x14bb}, new char[]{0xe660,0x1564,0x8dce,0xd473,0x0cc6,0xab90,0x68d3,0x2dfd,0x5025,0xc5a2,0x2e76,0xad52,0x8a45,0xae87,0xa017,0xa8a8,0x14cf}).toString();
            static final String L28 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x96a9,0x0269,0x47b0,0xad6b,0x3fd0,0x5ee5,0x14ec,0x72b2,0x4c47,0xc7f2,0xd815,0xe30a,0x8093,0x63b8,0x7a6e,0xd350,0xb261}, new char[]{0x5e88,0x148d,0x47c8,0x0244,0x96dd,0xad1e,0x3fbe,0x80fd,0x7a0b,0x4c2b,0xc7df,0xb209,0x72d7,0x63d1,0xe36d,0xd87d,0xd324}).plainString();
            static final String L29 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb0bc,0xab06,0xd8ac,0xfbc3,0x3b04,0xf512,0x532a,0x0d91,0x7c71,0xbfa9,0xdff2,0x10db,0xd4c4,0xabe9,0xb5a8,0xe0ec,0x2693}, new char[]{0xb0f1,0xdf93,0xe094,0xabc9,0xb5fc,0x0de4,0x10b5,0xfbad,0x3b61,0x5346,0x7c51,0x26db,0xab63,0xd4ad,0xf575,0xbfc1,0xd8d8}).toString();
            static final String L30 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x02a8,0xfe21,0xe244,0x438f,0x65b8,0x81d5,0x3b45,0x2406,0x61aa,0x3694,0x3d9e,0x39c4,0x8416,0x0530,0x8f30,0x1bf9}, new char[]{0x43e2,0xfe48,0x2468,0x843b,0x02db,0x8f44,0x3b24,0x1b90,0x65ca,0xe269,0x81b9,0x3dfb,0x055e,0x39a3,0x61de,0x36fc}).plainString();
            static final String L31 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1199,0xce62,0x778a,0xf79e,0xdfb1,0x6275,0x0c76,0x183d,0xac25,0xaecd,0x22d8,0xfd1c,0xcffb,0xbcfc,0xb7f6,0x1d84}, new char[]{0x77c7,0x1854,0xb798,0x11b9,0x0c25,0xf7ea,0xfd7d,0x22b1,0x6207,0xac05,0x1dc8,0xaea8,0xce0c,0xdfd6,0xcf8f,0xbc94}).toString();
            static final String L32 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2226,0x106f,0xeedc,0x5513,0x78a2,0x89b1,0x8d39,0xedfd,0xb88e,0xee1f,0xfc76,0x077a,0x181f,0x8b96,0x7019,0xed4b}, new char[]{0x7074,0x1876,0xb8e0,0xed66,0x8be5,0x78d6,0x100e,0xed94,0x8d4b,0xfc5b,0x557b,0x2243,0xee76,0x89d6,0xeeb4,0x070e}).plainString();
            static final String L33 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb751,0x932a,0x49c5,0x582a,0x459c,0xe35c,0xb222,0x9c01,0x1c59,0x0a94,0x2a18,0x6994,0x59c5,0x8c05,0x3734,0x3009}, new char[]{0x9367,0xb24b,0x5844,0x45bc,0x4996,0x8c71,0x3068,0x2a71,0x0ae6,0x9c21,0x69dc,0x1c3c,0x59ac,0xe33b,0x375c,0xb725}).toString();
            static final String L34 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x71c2,0x4644,0xa995,0xc860,0xe3a2,0xb7fe,0xebe3,0xce0d,0x4091,0x7050,0xddad,0x779d,0xfb10,0x9f99,0xf6ff,0x842f}, new char[]{0xeb8e,0xe3c3,0x7028,0xa9b8,0xddde,0xb78a,0xc801,0xce64,0xfb62,0x4669,0x40f9,0xf69a,0x9ff0,0x8448,0x71aa,0x77e9}).plainString();
            static final String L35 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xfb57,0x97ee,0x43a9,0x7e8c,0x59ff,0xcb1e,0xd56b,0xa359,0xd29e,0x6767,0xa705,0x6df6,0x5691,0xca07,0xf72f,0x126f}, new char[]{0xa314,0x7eed,0x43d1,0x6dd6,0xf77c,0xcb6a,0x120e,0x56f8,0x598d,0xfb77,0xd523,0xd2fb,0x670e,0x9789,0xa76d,0xca73}).toString();
            static final String L36 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6005,0xb714,0xb9f3,0x5936}, new char[]{0x5950,0xb77d,0x6069,0xb99f}).plainString();
            static final String L37 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6775,0x7a76,0xda4d,0xb704}, new char[]{0xda0b,0x671c,0x7a1a,0xb768}).toString();
            static final String L38 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2222,0xd929,0xe07d,0x373e,0xa074,0x1915,0xb222}, new char[]{0xd946,0x2257,0xa000,0x1979,0xe014,0xb24c,0x375b}).plainString();
            static final String L39 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa34e,0x513a,0xb05d,0xbc9d,0x17cc,0x9ba3,0xbfd2}, new char[]{0xa301,0xb028,0x514e,0xbcf1,0xbfbb,0x17a2,0x9bc6}).toString();
            static final String L40 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb992,0xf41c,0xb60a,0x3e8a,0xd456,0x440a,0x7f4c,0x97c2,0xee7e,0x6f43}, new char[]{0xd43a,0xf475,0x4464,0x7f29,0x3ea7,0x97b5,0xb9fb,0xee1a,0xb67e,0x6f2b}).plainString();
            static final String L41 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x59c9,0x226d,0xd4e9,0xa6d4,0x02b2,0x6b34,0xcb25,0x2c3c,0x90fb,0x9f45}, new char[]{0x9f09,0xa6bd,0x6b5a,0x59ac,0x2c1c,0x223a,0x02db,0x909f,0xd49d,0xcb4d}).toString();
            static final String L42 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb1b9,0x1c95,0x31c6,0xfda3,0x0481,0xf257,0x1f0f,0x49ef}, new char[]{0x49de,0x04f9,0xf266,0x1cb8,0x1f63,0xfdca,0x31a8,0xb1dc}).plainString();
            static final String L43 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xbeb8,0x6df7,0xc155,0xe7f7,0xaa61,0x512b,0x4557,0x3d5f}, new char[]{0xc164,0xaa19,0x3d6e,0x4577,0xbef4,0xe79e,0x5145,0x6d92}).toString();
            static final String L44 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xbf61,0xa113,0x5e91,0x1ff9,0x425e,0xd31f,0xa739,0x12e8}, new char[]{0x12d9,0xbf19,0x426f,0x5ebc,0xd379,0xa750,0xa17f,0x1f95}).plainString();
            static final String L45 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x17a0,0xbce4,0xa203,0x61ab,0x29b9,0x9381,0x9d4b,0x3baa}, new char[]{0xa232,0xbc9c,0x93b0,0x2999,0x17e6,0x61c2,0x3bc6,0x9d27}).toString();
            static final String L46 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xdc5b,0xeefe,0x6157,0xd1d0,0xa537,0x17da,0x5965,0xa92c}, new char[]{0xa504,0x591d,0x6166,0xeed3,0xd1bc,0x17b3,0xa942,0xdc3e}).plainString();
            static final String L47 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb174,0xf087,0xcaa4,0xa2cf,0x02b7,0x0f27,0x33ed,0xd688}, new char[]{0x0f14,0xd6f0,0x33dc,0x0297,0xb138,0xf0ee,0xcaca,0xa2aa}).toString();
            static final String L48 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc880,0xadd1,0x8f21,0x2598,0xd024,0xe1da,0xb2f6,0x98b4}, new char[]{0xd017,0x25e0,0xade0,0x9899,0xb290,0x8f48,0xc8ec,0xe1b6}).plainString();
            static final String L49 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8e89,0x70bd,0xdf80,0x40fe,0x9b54,0xffb2,0xd0db,0x1e3d}, new char[]{0xd0e8,0xdff8,0x40cf,0x1e1d,0x9b12,0x70d4,0x8ee5,0xffde}).toString();
            static final String L50 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x186e,0x97b2,0xc6a5,0x69c4,0x2bec,0x7a30,0x183f,0xe456,0x5cd9,0x5cfd,0x340a}, new char[]{0x97c6,0x7a45,0x5c93,0x2b82,0x5cbc,0x69a8,0x3427,0xe43a,0xc6cc,0x1800,0x185a}).plainString();
            static final String L51 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4420,0xc57f,0x0d27,0x1cbd,0x8350,0x7701,0xd37c,0x377a,0x5be3,0x04e9,0x9428}, new char[]{0xc52b,0x5b96,0x3714,0x776f,0x8335,0x1cd1,0x0d07,0x9464,0x0480,0x444e,0xd319}).toString();
            static final String L52 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf422,0x1547,0x0213,0xb199,0x51d7,0xd367,0x81de,0x8192,0x9129,0x7bb0,0xdca8}, new char[]{0x915d,0x1532,0x81b0,0x51b9,0x7bd5,0x81fe,0xf40f,0xd301,0xb1f0,0x027f,0xdcc4}).plainString();
            static final String L53 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x75cc,0x2052,0xb1c8,0xf5ae,0xdf02,0xc5df,0x8c30,0x74fe,0x290a,0xa5ed,0x5a70}, new char[]{0xf5fa,0x5a05,0x2964,0x8c5e,0x2037,0xc5b3,0xdf22,0x758a,0xa584,0x7492,0xb1a4}).toString();
            static final String L54 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5b8c,0xcc1c,0x4067,0xf141,0x8464,0xb559,0x2272,0x3afc,0x4048,0x5687}, new char[]{0x8417,0x56f3,0x4029,0x5be5,0xb52b,0x404a,0xcc70,0x221b,0xf12f,0x3a99}).plainString();
            static final String L55 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3c3c,0x9509,0xee0d,0xd2aa,0x2680,0x2880,0xb053,0x13e2,0xb080,0xf3e4}, new char[]{0xee5e,0x1396,0x28e1,0xb03a,0x26f2,0x9529,0xd2e6,0x3c55,0xf38a,0xb0e5}).toString();
            static final String L56 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa762,0xf3c0,0xa5ed,0xdd3d,0x759f,0x6623,0x3898,0xdaaa,0x3ef7,0x25bc}, new char[]{0xdd4e,0xa599,0xdacb,0x664a,0x25ce,0xa74f,0x75f9,0x3e9e,0x38f4,0xf3ac}).plainString();
            static final String L57 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5a8f,0xefab,0xb813,0x0e23,0x171b,0xca89,0x56b3,0x9a83,0x8edc,0xe8e4}, new char[]{0xb840,0x9af7,0x8ebd,0xefc2,0xe896,0x5693,0xcacf,0x0e4a,0x5ae3,0x1777}).toString();
            static final String L58 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9b49,0xf189,0x5fe2,0xdde7,0xf102,0x9f9e,0x20fc,0xc815}, new char[]{0x2094,0xc87a,0x9ff2,0x9b2c,0xf1a4,0x5f87,0xdd94,0xf172}).plainString();
            static final String L59 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x24ba,0xed34,0x43da,0x1027,0x05e6,0x7a49,0x263d,0xcb96,0x9e56,0x150e,0x377c,0xeac0,0x4a70,0x1f1a,0x97ae,0x8db3,0xa85e,0xa79d,0xf8fa,0x165e,0x4636,0xedba,0x9a8c,0x6042,0xa366,0xbb9d,0xded2,0x3b6d,0x0822,0xe9b0,0x566a,0xbdfd,0xc3b4,0xb360,0x6c72,0xa976,0xd1ec,0x44d7,0xf4d4,0x78a4,0x3bf7,0x7b17}, new char[]{0x9ac4,0x24d3,0x0845,0xa30e,0xa91a,0x7a20,0x44b0,0xbbf5,0x97da,0x1f69,0x6062,0xedd2,0xed5b,0x43b6,0xd189,0x5619,0x7b3b,0xe990,0xf4a0,0x8dc6,0x1630,0xeaae,0x3719,0xb30c,0x6c01,0x4a5c,0x4616,0x0587,0x3b03,0x156a,0xbddd,0xa7ee,0x1053,0xdeb3,0x78cd,0xc3c6,0x3b94,0xa83f,0xcbe5,0xf89f,0x264e,0x9e78}).toString();
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT2 {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2ee6}, new char[]{0x2edc,0x9e36,0x57fc,0xf417}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6db4}, new char[]{0x6d98,0xc498,0xcd20,0xd204}).plainString();
    }
}
