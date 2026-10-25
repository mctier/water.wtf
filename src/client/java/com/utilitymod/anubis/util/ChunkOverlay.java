package com.utilitymod.anubis.util;

import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.Long2FloatMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.debug.gizmo.Gizmo;
import net.minecraft.world.debug.gizmo.GizmoDrawer;
import net.minecraft.world.debug.gizmo.GizmoDrawing;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.ColorHelper;

public final class ChunkOverlay {
   public static final double MARKER_Y = 63.0;
   private static final float EDGE_SHADE = 0.77F;
   private static final float LINE_WIDTH = 1.5F;
   private static final double CORNER_RADIUS = 2.0;
   private static final int ARC_SEGMENTS = 4;
   private static final float GROW_SECONDS = 0.3F;
   private static final float SHRINK_SECONDS = 0.25F;
   private static final float SETTLE_SECONDS = 0.25F;
   private static final float MAX_WAIT_SECONDS = 1.0F;
   private static final float RESET_GAP_SECONDS = 0.5F;
   private final Long2FloatOpenHashMap shown = new Long2FloatOpenHashMap();
   private final LongOpenHashSet pending = new LongOpenHashSet();
   private final LongOpenHashSet done = new LongOpenHashSet();
   private final LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
   private final LongArrayList group = new LongArrayList();
   private final ChunkTileGeometry.Cache mobileTiles = new ChunkTileGeometry.Cache();
   private long lastNanos;
   private int lastRecorded = -1;
   private long pendingSince;
   private long lastAdded;

   private final String source;

   public ChunkOverlay() {
      this(LT.L1);
   }

   public ChunkOverlay(String source) {
      this.source = source;
   }

   public void draw(LongSet chunks, int fill, float partialTick) {
      if (chunks.size() != this.lastRecorded) {
         this.lastRecorded = chunks.size();
         for (LongIterator it = chunks.iterator(); it.hasNext(); ) {
            com.utilitymod.gui.Waypoints.found(this.source, it.nextLong());
         }
      }
      boolean mobile = MobilePerformance.active();
      if (mobile) {
         this.mobileTiles.beginFrame();
      } else {
         this.mobileTiles.clear();
      }

      long now = System.nanoTime();
      float dt = this.lastNanos == 0L ? 0.0F : (float)(now - this.lastNanos) / 1.0E9F;
      this.lastNanos = now;
      if (dt > 0.5F) {
         this.shown.clear();
         this.pending.clear();
         this.mobileTiles.clear();
      }

      this.collect(chunks, now);
      this.step(chunks, dt);
      if (this.shown.isEmpty()) {
         this.mobileTiles.clear();
      } else {
         int edge = ColorHelper.withAlpha(Math.min(255, ColorHelper.getAlpha(fill) * 3 / 2), ColorHelper.scaleRgb(fill, 0.77F));
         FoglessLines lines = FoglessLines.forFrame(partialTick);
         this.done.clear();
         LongIterator keys = this.shown.keySet().iterator();

         while (keys.hasNext()) {
            long start = keys.nextLong();
            if (this.done.add(start)) {
               this.drawGroup(lines, chunks, start, fill, edge);
            }
         }
      }
   }

   private void collect(LongSet chunks, long now) {
      LongIterator keys = chunks.iterator();

      while (keys.hasNext()) {
         long key = keys.nextLong();
         if (!this.shown.containsKey(key) && !this.pending.contains(key)) {
            if (this.pending.isEmpty()) {
               this.pendingSince = now;
            }

            this.pending.add(key);
            this.lastAdded = now;
         }
      }

      keys = this.pending.iterator();

      while (keys.hasNext()) {
         if (!chunks.contains(keys.nextLong())) {
            keys.remove();
         }
      }

      if (!this.pending.isEmpty()) {
         if (!((float)(now - this.lastAdded) / 1.0E9F < 0.25F) || !((float)(now - this.pendingSince) / 1.0E9F < 1.0F)) {
            keys = this.pending.iterator();

            while (keys.hasNext()) {
               this.shown.put(keys.nextLong(), 0.0F);
            }

            this.pending.clear();
         }
      }
   }

   private void step(LongSet chunks, float dt) {
      ObjectIterator<Entry> entries = this.shown.long2FloatEntrySet().fastIterator();

      while (entries.hasNext()) {
         Entry entry = (Entry)entries.next();
         if (chunks.contains(entry.getLongKey())) {
            entry.setValue(Math.min(1.0F, entry.getFloatValue() + dt / 0.3F));
         } else {
            float left = entry.getFloatValue() - dt / 0.25F;
            if (left <= 0.0F) {
               entries.remove();
            } else {
               entry.setValue(left);
            }
         }
      }
   }

   private void drawGroup(FoglessLines lines, LongSet chunks, long start, int fill, int edge) {
      float progress = this.shown.get(start);
      boolean present = chunks.contains(start);
      this.group.clear();
      this.queue.enqueue(start);
      double sumX = 0.0;
      double sumZ = 0.0;

      while (!this.queue.isEmpty()) {
         long key = this.queue.dequeueLong();
         this.group.add(key);
         int cx = ChunkPos.getPackedX(key);
         int cz = ChunkPos.getPackedZ(key);
         sumX += (cx << 4) + 8.0;
         sumZ += (cz << 4) + 8.0;
         this.visit(chunks, ChunkPos.toLong(cx, cz - 1), progress, present);
         this.visit(chunks, ChunkPos.toLong(cx, cz + 1), progress, present);
         this.visit(chunks, ChunkPos.toLong(cx - 1, cz), progress, present);
         this.visit(chunks, ChunkPos.toLong(cx + 1, cz), progress, present);
      }

      float size = easeOut(progress);
      if (!(size <= 0.001F)) {
         double mx = sumX / this.group.size();
         double mz = sumZ / this.group.size();
         int groupFill = size >= 1.0F ? fill : fade(fill, size);
         int groupEdge = size >= 1.0F ? edge : fade(edge, size);

         for (int i = 0; i < this.group.size(); i++) {
            long key = this.group.getLong(i);
            int cx = ChunkPos.getPackedX(key);
            int cz = ChunkPos.getPackedZ(key);
            double x0 = mx + ((cx << 4) - mx) * size;
            double z0 = mz + ((cz << 4) - mz) * size;
            double x1 = x0 + 16.0 * size;
            double z1 = z0 + 16.0 * size;
            boolean north = !this.joined(chunks, ChunkPos.toLong(cx, cz - 1), progress, present);
            boolean south = !this.joined(chunks, ChunkPos.toLong(cx, cz + 1), progress, present);
            boolean west = !this.joined(chunks, ChunkPos.toLong(cx - 1, cz), progress, present);
            boolean east = !this.joined(chunks, ChunkPos.toLong(cx + 1, cz), progress, present);
            if (MobilePerformance.active() && size >= 1.0F) {
               int mask = (north ? 1 : 0) | (south ? 2 : 0) | (west ? 4 : 0) | (east ? 8 : 0);
               ChunkTileGeometry geometry = this.mobileTiles.get(key, x0, z0, x1, z1, 2.0 * size, mask);

               for (int edgeIndex = 0; edgeIndex < geometry.curves.length; edgeIndex += 2) {
                  lines.line(geometry.curves[edgeIndex], geometry.curves[edgeIndex + 1], groupEdge, 1.5F);
               }

               GizmoDrawing.collect(new ChunkOverlay.Plane(geometry.outline, groupFill)).ignoreOcclusion();

               for (int edgeIndex = 0; edgeIndex < geometry.edges.length; edgeIndex += 2) {
                  lines.line(geometry.edges[edgeIndex], geometry.edges[edgeIndex + 1], groupEdge, 1.5F);
               }
            } else {
               tile(lines, x0, z0, x1, z1, 2.0 * size, north, south, west, east, groupFill, groupEdge);
            }
         }
      }
   }

   private void visit(LongSet chunks, long key, float progress, boolean present) {
      if (this.joined(chunks, key, progress, present) && this.done.add(key)) {
         this.queue.enqueue(key);
      }
   }

   private boolean joined(LongSet chunks, long key, float progress, boolean present) {
      return this.shown.containsKey(key) && this.shown.get(key) == progress && chunks.contains(key) == present;
   }

   private static void tile(
      FoglessLines lines,
      double x0,
      double z0,
      double x1,
      double z1,
      double radius,
      boolean north,
      boolean south,
      boolean west,
      boolean east,
      int fill,
      int edge
   ) {
      double nw = north && west ? radius : 0.0;
      double ne = north && east ? radius : 0.0;
      double se = south && east ? radius : 0.0;
      double sw = south && west ? radius : 0.0;
      List<Vec3d> outline = new ArrayList<>(20);
      corner(outline, lines, x0 + nw, z0 + nw, nw, 180.0, edge);
      corner(outline, lines, x1 - ne, z0 + ne, ne, 270.0, edge);
      corner(outline, lines, x1 - se, z1 - se, se, 0.0, edge);
      corner(outline, lines, x0 + sw, z1 - sw, sw, 90.0, edge);
      GizmoDrawing.collect(new ChunkOverlay.Plane(outline.toArray(new Vec3d[0]), fill)).ignoreOcclusion();
      if (north) {
         edge(lines, x0 + nw, z0, x1 - ne, z0, edge);
      }

      if (south) {
         edge(lines, x0 + sw, z1, x1 - se, z1, edge);
      }

      if (west) {
         edge(lines, x0, z0 + nw, x0, z1 - sw, edge);
      }

      if (east) {
         edge(lines, x1, z0 + ne, x1, z1 - se, edge);
      }
   }

   private static void corner(List<Vec3d> outline, FoglessLines lines, double cx, double cz, double radius, double startDegrees, int edge) {
      if (radius <= 0.0) {
         outline.add(new Vec3d(cx, 63.0, cz));
      } else {
         Vec3d previous = null;

         for (int i = 0; i <= 4; i++) {
            double angle = Math.toRadians(startDegrees + 90.0 * i / 4.0);
            Vec3d point = new Vec3d(cx + Math.cos(angle) * radius, 63.0, cz + Math.sin(angle) * radius);
            outline.add(point);
            if (previous != null) {
               lines.line(previous, point, edge, 1.5F);
            }

            previous = point;
         }
      }
   }

   private static float easeOut(float t) {
      float inv = 1.0F - t;
      return 1.0F - inv * inv * inv;
   }

   private static int fade(int color, float amount) {
      return ColorHelper.withAlpha(Math.round(ColorHelper.getAlpha(color) * amount), color);
   }

   private static void edge(FoglessLines lines, double ax, double az, double bx, double bz, int color) {
      lines.line(new Vec3d(ax, 63.0, az), new Vec3d(bx, 63.0, bz), color, 1.5F);
   }

   private record Plane(Vec3d[] outline, int color) implements Gizmo {
      public void draw(GizmoDrawer primitives, float alpha) {
         primitives.addPolygon(this.outline, ChunkOverlay.fade(this.color, alpha));
      }
   }


    // cloak literals - runtime PUA carriers (cloak) or functional plain (LT)
    private static final class LT {
            static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7ab1,0x7a73,0x21bf,0x7a0a,0x082d,0x9c0c}, new char[]{0x7a35,0x7ad8,0x9c62,0x21db,0x7a6f,0x085f}).toString();
    }
}
