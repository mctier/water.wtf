package com.utilitymod.anubis.module.impl.render;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongListIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2LongMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.utilitymod.anubis.event.EventHandler;
import com.utilitymod.anubis.event.Stage;
import com.utilitymod.anubis.event.impl.BlockUpdateEvent;
import com.utilitymod.anubis.event.impl.PacketReceiveNettyEvent;
import com.utilitymod.anubis.event.impl.Render3DEvent;
import com.utilitymod.anubis.event.impl.TickEvent;
import com.utilitymod.anubis.gui.ChallengeToast;
import com.utilitymod.anubis.module.ClientModule;
import com.utilitymod.anubis.module.ModuleCategory;
import com.utilitymod.anubis.module.setting.BooleanSetting;
import com.utilitymod.anubis.module.setting.ColorSetting;
import com.utilitymod.anubis.util.ChunkAreas;
import com.utilitymod.anubis.util.ChunkOverlay;
import com.utilitymod.anubis.util.LevelUtil;
import net.minecraft.util.Util;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.LightType;
import net.minecraft.block.AbstractPressurePlateBlock;
import net.minecraft.block.Blocks;
import net.minecraft.block.Block;
import net.minecraft.block.ButtonBlock;
import net.minecraft.block.DaylightDetectorBlock;
import net.minecraft.block.AbstractRedstoneGateBlock;
import net.minecraft.block.DispenserBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.block.HopperBlock;
import net.minecraft.block.LeverBlock;
import net.minecraft.block.ObserverBlock;
import net.minecraft.block.NoteBlock;
import net.minecraft.block.RedstoneBlock;
import net.minecraft.block.RedstoneLampBlock;
import net.minecraft.block.RedstoneWireBlock;
import net.minecraft.block.RedstoneTorchBlock;
import net.minecraft.block.TripwireHookBlock;
import net.minecraft.text.Text;
import net.minecraft.block.PistonBlock;
import net.minecraft.block.PistonExtensionBlock;
import net.minecraft.block.PistonHeadBlock;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.packet.s2c.play.LightUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.block.BlockState;
import net.minecraft.world.chunk.ChunkNibbleArray;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.client.MinecraftClient;
import net.minecraft.world.chunk.light.ChunkLightingView;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.block.TargetBlock;
import net.minecraft.block.CaveVines;
import net.minecraft.client.world.ClientChunkManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.packet.s2c.play.LightData;
import net.minecraft.block.CrafterBlock;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.BlockPos.Mutable;

public final class PrimeChunkScan extends ClientModule {
   private static final int SECTION_BYTES = 2048;
   private static final int MAX_FLAGGED_CHUNKS = 256;
   private static final int AMETHYST_MAX_LIGHT = 5;
   private static final int AMETHYST_REACH = 5;
   private static final long REDSTONE_NOTIFY_COOLDOWN_MS = 15000L;
   private static final int ORE_LIGHT = 9;
   private static final long[] NO_ORES = new long[0];
   private static final int LAMP_LIGHT = 14;
   private static final int[] NO_LAMPS = new int[0];
   private static final long GONE_LAMP_MS = 5000L;
   private static final int MAX_TRACKED_CHUNKS = 5000;
   private static final int SNAPSHOT_RADIUS = 32;
   private static final byte[] ZERO_SECTION = new byte[2048];
   private static final double MAX_Y = 0.0;
   private final ColorSetting chunkColor = this.addSetting(LT.L1, new ColorSetting(LT.L2, 1342242650));
   private final BooleanSetting tracers = this.addSetting(LT.L3, new BooleanSetting(LT.L4, true));
   private final BooleanSetting sound = this.addSetting(LT.L5, new BooleanSetting(LT.L6, true));
   private final LinkedHashMap<Long, Map<Integer, byte[]>> chunkLight = new LinkedHashMap<>(256, 0.75F, true);
   private final LongOpenHashSet passedChunks = new LongOpenHashSet();
   private final LongLinkedOpenHashSet flaggedChunks = new LongLinkedOpenHashSet();
   private final LongOpenHashSet shownChunks = new LongOpenHashSet();
   private final ChunkAreas chunkAreas = new ChunkAreas();
   private final ChunkOverlay chunkOverlay = new ChunkOverlay(LT.L7);
   private final LongOpenHashSet checkedSections = new LongOpenHashSet();
   private final LongOpenHashSet amethystSections = new LongOpenHashSet();
   private final Long2ObjectOpenHashMap<long[]> oreSections = new Long2ObjectOpenHashMap();
   private final Long2ObjectOpenHashMap<int[]> lampSections = new Long2ObjectOpenHashMap();
   private final Long2LongOpenHashMap goneLamps = new Long2LongOpenHashMap();
   private final Mutable cursor = new Mutable();
   private final Long2LongOpenHashMap redstoneNotified = new Long2LongOpenHashMap();

   public PrimeChunkScan() {
      super(LT.L8, LT.L9, LT.L10, ModuleCategory.BASE_RAID);
      BooleanSetting notifications = this.usesNotifications();
      this.sound.visibleWhen(notifications::get);
      this.redstoneNotified.defaultReturnValue(-4611686018427387904L);
   }

   @Override
   public boolean persistEnabled() {
      return false;
   }

   @Override
   protected void onEnable() {
      this.reset();
      this.snapshotLoadedChunks();
      this.on(PacketReceiveNettyEvent.TYPE, this::onPacket);
   }

   @Override
   protected void onDisable() {
      this.reset();
   }

   private void onPacket(PacketReceiveNettyEvent event) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (event.packet instanceof GameJoinS2CPacket) {
         mc.execute(() -> {
            if (this.isEnabled()) {
               this.reset();
            }
         });
      } else if (event.packet instanceof ChunkDataS2CPacket packet) {
         int chunkX = packet.getChunkX();
         int chunkZ = packet.getChunkZ();
         LightData data = packet.getLightData();
         mc.execute(() -> {
            if (this.isEnabled() && mc.world != null) {
               this.baselineChunk(chunkX, chunkZ, data);
            }
         });
      } else if (event.packet instanceof LightUpdateS2CPacket packet) {
         int chunkX = packet.getChunkX();
         int chunkZ = packet.getChunkZ();
         LightData data = packet.getData();
         mc.execute(() -> {
            if (this.isEnabled() && mc.world != null) {
               this.detectChanges(chunkX, chunkZ, data);
            }
         });
      }
   }

   @EventHandler
   public void onBlockUpdate(BlockUpdateEvent event) {
      if (event.oldState != event.newState && MinecraftClient.getInstance().world != null) {
         this.rememberGoneLamp(event.pos, event.oldState, event.newState);
         this.detectRedstone(event.pos, event.oldState, event.newState);
      }
   }

   @EventHandler(stage = Stage.POST)
   public void onTick(TickEvent event) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.world == null) {
         this.reset();
      } else {
         if (mc.player != null && mc.player.getY() < 0.0) {
            this.passedChunks.add(mc.player.getChunkPos().toLong());
         }

         this.forgetOldLamps();
         this.refreshShown(mc.world);
      }
   }

   private void refreshShown(ClientWorld level) {
      this.shownChunks.clear();
      LongListIterator var2 = this.flaggedChunks.iterator();

      while (var2.hasNext()) {
         long key = (Long)var2.next();
         if (LevelUtil.isLoaded(level, ChunkPos.getPackedX(key), ChunkPos.getPackedZ(key))) {
            this.shownChunks.add(key);
         }
      }
   }

   @EventHandler
   public void onRender3D(Render3DEvent event) {
      if (!this.shownChunks.isEmpty() && MinecraftClient.getInstance().world != null) {
         int fill = this.chunkColor.argb();
         this.chunkOverlay.draw(this.shownChunks, fill, event.partialTick);
         if (this.tracers.get()) {
            this.chunkAreas.drawTracers(this.shownChunks, 63.0, ColorHelper.fullAlpha(fill));
         }
      }
   }

   private void flagChunk(long key) {
      this.flaggedChunks.addAndMoveToLast(key);
      this.shownChunks.add(key);

      while (this.flaggedChunks.size() > 256) {
         this.flaggedChunks.removeFirstLong();
      }
   }

   private void snapshotLoadedChunks() {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.world != null && mc.player != null) {
         int centerX = mc.player.getBlockX() >> 4;
         int centerZ = mc.player.getBlockZ() >> 4;

         for (int chunkX = centerX - 32; chunkX <= centerX + 32; chunkX++) {
            for (int chunkZ = centerZ - 32; chunkZ <= centerZ + 32; chunkZ++) {
               Map<Integer, byte[]> sections = this.snapshotChunk(chunkX, chunkZ);
               if (sections != null) {
                  this.chunkLight.put(ChunkPos.toLong(chunkX, chunkZ), sections);
               }
            }
         }

         this.trimChunkCache();
      }
   }

   private Map<Integer, byte[]> snapshotChunk(int chunkX, int chunkZ) {
      ClientWorld level = MinecraftClient.getInstance().world;
      if (level == null) {
         return null;
      }

      ClientChunkManager chunkSource = level.getChunkManager();
      if (!chunkSource.isChunkLoaded(chunkX, chunkZ)) {
         return null;
      }

      ChunkLightingView blockLight = chunkSource.getLightingProvider().get(LightType.BLOCK);
      Map<Integer, byte[]> sections = this.createZeroedChunk();

      for (int sectionY = this.bottomSection(); sectionY <= this.topSection(); sectionY++) {
         ChunkNibbleArray layer = blockLight.getLightSection(ChunkSectionPos.from(chunkX, sectionY, chunkZ));
         if (layer != null && !layer.isUninitialized(0)) {
            sections.put(sectionY, Arrays.copyOf(layer.asByteArray(), 2048));
         }
      }

      return sections;
   }

   private void baselineChunk(int chunkX, int chunkZ, LightData data) {
      Map<Integer, byte[]> sections = this.createZeroedChunk();
      this.applyInitializedSections(data, sections, chunkX, chunkZ, false, null);
      this.applyUninitializedSections(data, sections, chunkX, chunkZ, false, null);
      this.chunkLight.put(ChunkPos.toLong(chunkX, chunkZ), sections);
      this.trimChunkCache();
   }

   private void detectChanges(int chunkX, int chunkZ, LightData data) {
      long key = ChunkPos.toLong(chunkX, chunkZ);
      this.checkedSections.clear();
      this.amethystSections.clear();
      this.oreSections.clear();
      this.lampSections.clear();
      Map<Integer, byte[]> sections = this.chunkLight.get(key);
      if (sections == null) {
         sections = this.snapshotChunk(chunkX, chunkZ);
      }

      boolean compare = sections != null && !this.isOwnChunk(key);
      if (sections == null) {
         sections = this.createZeroedChunk();
      }

      PrimeChunkScan.ChangeSummary summary = new PrimeChunkScan.ChangeSummary();
      this.applyInitializedSections(data, sections, chunkX, chunkZ, compare, summary);
      this.applyUninitializedSections(data, sections, chunkX, chunkZ, compare, summary);
      this.chunkLight.put(key, sections);
      this.trimChunkCache();
      if (summary.count != 0) {
         this.flagChunk(key);
         if (this.notificationsOn()) {
            ChallengeToast.addOrUpdate(
               new ItemStack(Items.EMERALD_BLOCK), this.name(), Text.literal(summary.x + LT2.L1 + summary.y + LT2.L1 + summary.z), this.sound.get()
            );
         }
      }
   }

   private void rememberGoneLamp(BlockPos pos, BlockState previous, BlockState state) {
      if (isLamp(previous)) {
         int light = previous.getLuminance();
         if (!isLamp(state) || state.getLuminance() < light) {
            this.goneLamps.put(pos.asLong(), Util.getMeasuringTimeMs() + 5000L << 4 | light);
         }
      }
   }

   private void forgetOldLamps() {
      if (!this.goneLamps.isEmpty()) {
         long now = Util.getMeasuringTimeMs();
         LongIterator it = this.goneLamps.values().iterator();

         while (it.hasNext()) {
            if (it.nextLong() >>> 4 < now) {
               it.remove();
            }
         }
      }
   }

   private void detectRedstone(BlockPos pos, BlockState previous, BlockState state) {
      if (!(pos.getY() >= 0.0) && (isRedstone(state) || isRedstone(previous))) {
         long key = ChunkPos.toLong(pos.getX() >> 4, pos.getZ() >> 4);
         if (!this.isOwnChunk(key)) {
            this.flagChunk(key);
            long now = Util.getMeasuringTimeMs();
            if (this.notificationsOn() && now - this.redstoneNotified.get(key) >= 15000L) {
               this.redstoneNotified.put(key, now);
               ChallengeToast.addOrUpdate(
                  new ItemStack(Items.REDSTONE),
                  this.name(),
                  Text.literal(LT.L11 + pos.getX() + LT2.L1 + pos.getY() + LT2.L1 + pos.getZ()),
                  this.sound.get()
               );
            }
         }
      }
   }

   private static boolean isRedstone(BlockState state) {
      Block block = state.getBlock();
      return block instanceof RedstoneWireBlock
         || block instanceof AbstractRedstoneGateBlock
         || block instanceof RedstoneTorchBlock
         || block instanceof RedstoneLampBlock
         || block instanceof ObserverBlock
         || block instanceof PistonBlock
         || block instanceof PistonHeadBlock
         || block instanceof PistonExtensionBlock
         || block instanceof DispenserBlock
         || block instanceof HopperBlock
         || block instanceof NoteBlock
         || block instanceof DaylightDetectorBlock
         || block instanceof LeverBlock
         || block instanceof ButtonBlock
         || block instanceof AbstractPressurePlateBlock
         || block instanceof TripwireHookBlock
         || block instanceof TargetBlock
         || block instanceof CrafterBlock
         || block instanceof RedstoneBlock;
   }

   private boolean isOwnChunk(long key) {
      if (this.passedChunks.contains(key)) {
         return true;
      }

      MinecraftClient mc = MinecraftClient.getInstance();
      return mc.player != null && mc.player.getChunkPos().toLong() == key;
   }

   private void applyInitializedSections(
      LightData data, Map<Integer, byte[]> sections, int chunkX, int chunkZ, boolean compare, PrimeChunkScan.ChangeSummary summary
   ) {
      BitSet mask = data.getInitedBlock();
      List<byte[]> updates = data.getBlockNibbles();
      int next = 0;

      for (int bit = mask.nextSetBit(0); bit >= 0 && next < updates.size(); bit = mask.nextSetBit(bit + 1)) {
         byte[] update = updates.get(next++);
         int sectionY = this.lightSectionY(bit);
         if (this.isWorldSection(sectionY) && update.length == 2048) {
            this.updateSection(sections, chunkX, chunkZ, sectionY, Arrays.copyOf(update, 2048), compare, summary);
         }
      }
   }

   private void applyUninitializedSections(
      LightData data, Map<Integer, byte[]> sections, int chunkX, int chunkZ, boolean compare, PrimeChunkScan.ChangeSummary summary
   ) {
      BitSet empty = data.getUninitedBlock();

      for (int bit = empty.nextSetBit(0); bit >= 0; bit = empty.nextSetBit(bit + 1)) {
         int sectionY = this.lightSectionY(bit);
         if (this.isWorldSection(sectionY)) {
            this.updateSection(sections, chunkX, chunkZ, sectionY, ZERO_SECTION, compare, summary);
         }
      }
   }

   private void updateSection(
      Map<Integer, byte[]> sections, int chunkX, int chunkZ, int sectionY, byte[] light, boolean compare, PrimeChunkScan.ChangeSummary summary
   ) {
      byte[] previous = sections.get(sectionY);
      if (compare && previous != null && summary != null) {
         this.compareSection(previous, light, chunkX, chunkZ, sectionY, summary);
      }

      sections.put(sectionY, light);
   }

   private void compareSection(byte[] previous, byte[] light, int chunkX, int chunkZ, int sectionY, PrimeChunkScan.ChangeSummary summary) {
      for (int i = 0; i < 2048; i++) {
         int was = previous[i] & 255;
         int now = light[i] & 255;
         int changed = was ^ now;
         if ((changed & 15) != 0) {
            this.recordChange(chunkX, chunkZ, sectionY, i << 1, was & 15, now & 15, summary);
         }

         if ((changed & 240) != 0) {
            this.recordChange(chunkX, chunkZ, sectionY, (i << 1) + 1, was >>> 4 & 15, now >>> 4 & 15, summary);
         }
      }
   }

   private void recordChange(int chunkX, int chunkZ, int sectionY, int index, int oldValue, int newValue, PrimeChunkScan.ChangeSummary summary) {
      int x = (chunkX << 4) + (index & 15);
      int y = (sectionY << 4) + (index >>> 8 & 15);
      int z = (chunkZ << 4) + (index >>> 4 & 15);
      int value = Math.max(oldValue, newValue);
      if (!this.fromAmethyst(x, y, z, oldValue, newValue) && !this.fromRedstoneOre(x, y, z, value) && !this.fromLamp(x, y, z, value)) {
         summary.add(x, y, z, oldValue, newValue);
      }
   }

   private boolean fromLamp(int x, int y, int z, int value) {
      if (value > 14) {
         return false;
      }

      ClientWorld level = MinecraftClient.getInstance().world;
      if (level == null) {
         return false;
      }

      int reach = 14 - value;

      for (int sx = x - reach >> 4; sx <= x + reach >> 4; sx++) {
         for (int sy = y - reach >> 4; sy <= y + reach >> 4; sy++) {
            for (int sz = z - reach >> 4; sz <= z + reach >> 4; sz++) {
               for (int lamp : this.lamps(level, sx, sy, sz)) {
                  int index = lamp >>> 4;
                  int lx = (sx << 4) + (index & 15);
                  int ly = (sy << 4) + (index >>> 8);
                  int lz = (sz << 4) + (index >>> 4 & 15);
                  if (Math.abs(lx - x) + Math.abs(ly - y) + Math.abs(lz - z) <= (lamp & 15) - value) {
                     return true;
                  }
               }
            }
         }
      }

      long now = Util.getMeasuringTimeMs();
      ObjectIterator var19 = this.goneLamps.long2LongEntrySet().iterator();

      while (var19.hasNext()) {
         Entry gone = (Entry)var19.next();
         long packed = gone.getLongValue();
         if (packed >>> 4 >= now) {
            long pos = gone.getLongKey();
            int distance = Math.abs(BlockPos.unpackLongX(pos) - x) + Math.abs(BlockPos.unpackLongY(pos) - y) + Math.abs(BlockPos.unpackLongZ(pos) - z);
            if (distance <= (int)(packed & 15L) - value) {
               return true;
            }
         }
      }

      return false;
   }

   private int[] lamps(ClientWorld level, int sectionX, int sectionY, int sectionZ) {
      long key = ChunkSectionPos.asLong(sectionX, sectionY, sectionZ);
      int[] cached = (int[])this.lampSections.get(key);
      if (cached != null) {
         return cached;
      }

      int[] found = scanLamps(level, sectionX, sectionY, sectionZ);
      this.lampSections.put(key, found);
      return found;
   }

   private static int[] scanLamps(ClientWorld level, int sectionX, int sectionY, int sectionZ) {
      if (sectionY >= level.getBottomSectionCoord() && sectionY <= level.getTopSectionCoord()) {
         WorldChunk chunk = level.getChunkManager().getChunk(sectionX, sectionZ, ChunkStatus.FULL, false);
         if (chunk == null) {
            return NO_LAMPS;
         }

         ChunkSection section = chunk.getSection(chunk.sectionCoordToIndex(sectionY));
         if (!section.isEmpty() && section.getBlockStateContainer().hasAny(PrimeChunkScan::isLamp)) {
            IntArrayList lamps = new IntArrayList();

            for (int index = 0; index < 4096; index++) {
               BlockState state = section.getBlockState(index & 15, index >>> 8, index >>> 4 & 15);
               if (isLamp(state)) {
                  lamps.add(index << 4 | state.getLuminance());
               }
            }

            return lamps.isEmpty() ? NO_LAMPS : lamps.toIntArray();
         } else {
            return NO_LAMPS;
         }
      } else {
         return NO_LAMPS;
      }
   }

   private static boolean isLamp(BlockState state) {
      return state.isOf(Blocks.TORCH)
         || state.isOf(Blocks.WALL_TORCH)
         || state.isOf(Blocks.SOUL_TORCH)
         || state.isOf(Blocks.SOUL_WALL_TORCH)
         || state.isOf(Blocks.COPPER_TORCH)
         || state.isOf(Blocks.COPPER_WALL_TORCH)
         || CaveVines.hasBerries(state);
   }

   private boolean fromAmethyst(int x, int y, int z, int oldValue, int newValue) {
      if (Math.max(oldValue, newValue) > 5) {
         return false;
      }

      ClientWorld level = MinecraftClient.getInstance().world;
      if (level != null && this.amethystSectionNear(level, x, y, z)) {
         for (int dx = -5; dx <= 5; dx++) {
            int restX = 5 - Math.abs(dx);

            for (int dy = -restX; dy <= restX; dy++) {
               int restY = restX - Math.abs(dy);

               for (int dz = -restY; dz <= restY; dz++) {
                  if (isAmethyst(level.getBlockState(this.cursor.set(x + dx, y + dy, z + dz)))) {
                     return true;
                  }
               }
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private boolean amethystSectionNear(ClientWorld level, int x, int y, int z) {
      for (int sx = x - 5 >> 4; sx <= x + 5 >> 4; sx++) {
         for (int sy = y - 5 >> 4; sy <= y + 5 >> 4; sy++) {
            for (int sz = z - 5 >> 4; sz <= z + 5 >> 4; sz++) {
               long key = ChunkSectionPos.asLong(sx, sy, sz);
               if (this.checkedSections.add(key) && sectionHasAmethyst(level, sx, sy, sz)) {
                  this.amethystSections.add(key);
               }

               if (this.amethystSections.contains(key)) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   private static boolean sectionHasAmethyst(ClientWorld level, int sectionX, int sectionY, int sectionZ) {
      if (sectionY >= level.getBottomSectionCoord() && sectionY <= level.getTopSectionCoord()) {
         WorldChunk chunk = level.getChunkManager().getChunk(sectionX, sectionZ, ChunkStatus.FULL, false);
         if (chunk == null) {
            return false;
         }

         ChunkSection section = chunk.getSection(chunk.sectionCoordToIndex(sectionY));
         return !section.isEmpty() && section.getBlockStateContainer().hasAny(PrimeChunkScan::isAmethyst);
      } else {
         return false;
      }
   }

   private boolean fromRedstoneOre(int x, int y, int z, int value) {
      if (value > 9) {
         return false;
      }

      ClientWorld level = MinecraftClient.getInstance().world;
      if (level == null) {
         return false;
      }

      int reach = 9 - value;

      for (int sx = x - reach >> 4; sx <= x + reach >> 4; sx++) {
         for (int sy = y - reach >> 4; sy <= y + reach >> 4; sy++) {
            for (int sz = z - reach >> 4; sz <= z + reach >> 4; sz++) {
               for (long ore : this.exposedOres(level, sx, sy, sz)) {
                  int distance = Math.abs(BlockPos.unpackLongX(ore) - x)
                     + Math.abs(BlockPos.unpackLongY(ore) - y)
                     + Math.abs(BlockPos.unpackLongZ(ore) - z);
                  if (distance <= reach) {
                     return true;
                  }
               }
            }
         }
      }

      return false;
   }

   private long[] exposedOres(ClientWorld level, int sectionX, int sectionY, int sectionZ) {
      long key = ChunkSectionPos.asLong(sectionX, sectionY, sectionZ);
      long[] cached = (long[])this.oreSections.get(key);
      if (cached != null) {
         return cached;
      }

      long[] found = this.scanExposedOres(level, sectionX, sectionY, sectionZ);
      this.oreSections.put(key, found);
      return found;
   }

   private long[] scanExposedOres(ClientWorld level, int sectionX, int sectionY, int sectionZ) {
      if (sectionY >= level.getBottomSectionCoord() && sectionY <= level.getTopSectionCoord()) {
         WorldChunk chunk = level.getChunkManager().getChunk(sectionX, sectionZ, ChunkStatus.FULL, false);
         if (chunk == null) {
            return NO_ORES;
         }

         ChunkSection section = chunk.getSection(chunk.sectionCoordToIndex(sectionY));
         if (!section.isEmpty() && section.getBlockStateContainer().hasAny(PrimeChunkScan::isRedstoneOre)) {
            LongArrayList ores = new LongArrayList();

            for (int ly = 0; ly < 16; ly++) {
               for (int lz = 0; lz < 16; lz++) {
                  for (int lx = 0; lx < 16; lx++) {
                     if (isRedstoneOre(section.getBlockState(lx, ly, lz))) {
                        int wx = (sectionX << 4) + lx;
                        int wy = (sectionY << 4) + ly;
                        int wz = (sectionZ << 4) + lz;
                        if (this.exposed(level, wx, wy, wz)) {
                           ores.add(BlockPos.asLong(wx, wy, wz));
                        }
                     }
                  }
               }
            }

            return ores.isEmpty() ? NO_ORES : ores.toLongArray();
         } else {
            return NO_ORES;
         }
      } else {
         return NO_ORES;
      }
   }

   private boolean exposed(ClientWorld level, int x, int y, int z) {
      for (Direction direction : Direction.values()) {
         BlockState neighbour = level.getBlockState(
            this.cursor.set(x + direction.getOffsetX(), y + direction.getOffsetY(), z + direction.getOffsetZ())
         );
         if (neighbour.isAir() || !neighbour.isOpaque()) {
            return true;
         }
      }

      return false;
   }

   private static boolean isRedstoneOre(BlockState state) {
      return state.isOf(Blocks.REDSTONE_ORE) || state.isOf(Blocks.DEEPSLATE_REDSTONE_ORE);
   }

   private static boolean isAmethyst(BlockState state) {
      return state.isOf(Blocks.AMETHYST_CLUSTER)
         || state.isOf(Blocks.LARGE_AMETHYST_BUD)
         || state.isOf(Blocks.MEDIUM_AMETHYST_BUD)
         || state.isOf(Blocks.SMALL_AMETHYST_BUD)
         || state.isOf(Blocks.BUDDING_AMETHYST)
         || state.isOf(Blocks.AMETHYST_BLOCK);
   }

   private Map<Integer, byte[]> createZeroedChunk() {
      Map<Integer, byte[]> sections = new HashMap<>();

      for (int sectionY = this.bottomSection(); sectionY <= this.topSection(); sectionY++) {
         sections.put(sectionY, ZERO_SECTION);
      }

      return sections;
   }

   private int lightSectionY(int index) {
      ClientWorld level = MinecraftClient.getInstance().world;
      return level == null ? Integer.MIN_VALUE : level.getChunkManager().getLightingProvider().getBottomY() + index;
   }

   private int bottomSection() {
      return MinecraftClient.getInstance().world.getBottomSectionCoord();
   }

   private int topSection() {
      return MinecraftClient.getInstance().world.getTopSectionCoord();
   }

   private boolean isWorldSection(int sectionY) {
      return sectionY >= this.bottomSection() && sectionY <= this.topSection();
   }

   private void trimChunkCache() {
      while (this.chunkLight.size() > 5000) {
         Iterator<Long> keys = this.chunkLight.keySet().iterator();
         keys.next();
         keys.remove();
      }
   }

   private void reset() {
      this.chunkLight.clear();
      this.passedChunks.clear();
      this.flaggedChunks.clear();
      this.shownChunks.clear();
      this.redstoneNotified.clear();
      this.lampSections.clear();
      this.goneLamps.clear();
   }

   private static final class ChangeSummary {
      int count;
      int x;
      int y;
      int z;
      int oldValue;
      int newValue;

      void add(int x, int y, int z, int oldValue, int newValue) {
         if (this.count == 0 || Math.abs(newValue - oldValue) > Math.abs(this.newValue - this.oldValue)) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.oldValue = oldValue;
            this.newValue = newValue;
         }

         this.count++;
      }
   }


    // cloak literals - runtime PUA carriers (cloak) or functional plain (LT)
    private static final class LT {
            static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x605f,0xdb6e,0xa327,0xdbee,0x641f,0xed41,0x6b5a,0x41a0,0xae86,0xad4b,0x9feb}, new char[]{0x647c,0x6b32,0xaef3,0xa349,0x9f80,0xdbc3,0x41c3,0xed2e,0x6033,0xad24,0xdb1c}).plainString();
            static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xeb30,0x2d35,0x8b2d,0xee77,0xfb08,0x1247,0x110f,0xf59d,0x9fe6,0xe0dd,0x21c4}, new char[]{0xfb4b,0xeb58,0x9f93,0xf5f3,0x21af,0x112f,0xe09e,0x1228,0x8b41,0xee18,0x2d47}).toString();
            static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7eb1,0x5005,0x203f,0xe034,0x7636,0x6263,0xf21d}, new char[]{0x7ec5,0x5077,0x205e,0xe057,0xf278,0x7644,0x6210}).plainString();
            static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2177,0x1222,0xf2e4,0xb2ad,0x516a,0xc437,0x3c1d}, new char[]{0x1276,0x5118,0x2116,0xc454,0x3c78,0xf296,0xb2de}).toString();
            static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xbebd,0x65cf,0x7248,0x105b,0x8bac}, new char[]{0x1028,0x8bc3,0x65ba,0x7226,0xbed9}).plainString();
            static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6bc5,0x2461,0x7db2,0x47ee,0x0bd0}, new char[]{0x47bd,0x6baa,0x2414,0x0bbe,0x7dd6}).toString();
            static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb73c,0x4600,0xe532,0x4527,0xbe11,0x60a4,0xfde5,0xfb6d,0x02d1,0xa51c,0x63d5}, new char[]{0xbe41,0xfb1f,0x63bc,0x60c9,0xe557,0xfdc5,0xa57f,0xb754,0x02a4,0x4549,0x466b}).toString();
            static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6663,0xe037,0xdbaa,0x3d55,0xf696,0x6cb6,0x099b,0x96fd,0x13c6,0xd734,0x3bd0,0x737b,0x8c7a,0x63f7,0x730e,0x7abd,0x593d,0x5b3d}, new char[]{0xdbda,0x7309,0x3d3c,0x6cdb,0x8c1f,0xe01a,0x13a5,0xd75c,0x6382,0x7360,0xf6fd,0x09b6,0x6605,0x5b54,0x5953,0x3bb4,0x7ad8,0x968f}).plainString();
            static final String L9 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xdeee,0xab88,0x910b,0xa9d9,0xb842,0xe57f,0x5d52,0xeb64,0xb84a,0x720f,0x884f,0xa675,0x5cc7,0xaaea,0x97bd,0x6b38,0xb388,0xe26a}, new char[]{0x6b68,0xb3fa,0xeb0d,0x9166,0xe20f,0xaba8,0x5c84,0xaa82,0x727a,0xa61b,0xe514,0x886f,0x97fb,0xb823,0x5d3c,0xb826,0xde8b,0xa9ab}).toString();
            static final String L10 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb801,0x8e55,0x4d48,0xdb0f,0x5167,0xd426,0xe992,0x5fce,0x15b0,0xcd7a,0xd1fb,0x57df,0xf704,0xce32,0x9e97,0xa272,0x276c,0xbcc5,0x9d47,0x73fe,0xe022,0xf69d,0x2ddf,0x9087,0x5f52,0x83c3,0xcb4e,0xb274,0xb256,0xa17a,0x9590,0xc85c,0xb0c1,0xe83c,0x48dc,0xb262,0xd9a8,0x3ab2,0xcda2,0xbe96,0xa56b,0xe1ed,0x7941,0x5899,0x799c,0x3c5a,0x7a0d,0xde3d,0xeb0f,0xbb82,0x6285,0x1831,0x906e,0x2327,0x5b68}, new char[]{0x4d0e,0xb86d,0xe85d,0xf763,0x83b0,0xb276,0xd198,0x7a65,0x2719,0xeb61,0x90ec,0x2dac,0xf6bd,0x15c7,0xd44f,0x9d33,0x8e3d,0xa252,0x48b0,0xb20b,0x57b8,0xbefe,0xdb7b,0x1811,0xde5c,0xcdcc,0x9ef3,0x73de,0x3c28,0x3ad7,0x7925,0xb207,0xe056,0xb0ae,0xe9fc,0x5102,0x58b9,0x2344,0x62ed,0xc83d,0xa114,0xbbe5,0xcb2b,0x901d,0x5fee,0x79fe,0xe188,0x5f3e,0xcd15,0xce45,0xd988,0xbc9c,0xa54b,0x95a0,0x5b46}).toString();
            static final String L11 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x483e,0x7ee2,0x5f8d,0x4a62,0x1c37,0x2841,0x3560,0x6d40,0xf1fa}, new char[]{0x5fdf,0x7e87,0x2825,0x4a11,0xf18e,0x6d2f,0x350e,0x1c52,0x481e}).plainString();
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT2 {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc8dd,0x2c37}, new char[]{0xc8f1,0x2c17,0x5245,0xc6ee}).plainString();
    }
}
