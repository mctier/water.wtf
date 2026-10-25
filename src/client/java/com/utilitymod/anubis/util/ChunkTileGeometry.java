package com.utilitymod.anubis.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;
import net.minecraft.util.math.Vec3d;

final class ChunkTileGeometry {
   final Vec3d[] outline;
   final Vec3d[] curves;
   final Vec3d[] edges;
   private final double x0;
   private final double z0;
   private final double x1;
   private final double z1;
   private final double radius;
   private final int mask;
   private long usedFrame;

   private ChunkTileGeometry(double x0, double z0, double x1, double z1, double radius, int mask) {
      this.x0 = x0;
      this.z0 = z0;
      this.x1 = x1;
      this.z1 = z1;
      this.radius = radius;
      this.mask = mask;
      boolean north = (mask & 1) != 0;
      boolean south = (mask & 2) != 0;
      boolean west = (mask & 4) != 0;
      boolean east = (mask & 8) != 0;
      double nw = north && west ? radius : 0.0;
      double ne = north && east ? radius : 0.0;
      double se = south && east ? radius : 0.0;
      double sw = south && west ? radius : 0.0;
      List<Vec3d> outline = new ArrayList<>(20);
      List<Vec3d> curves = new ArrayList<>(32);
      List<Vec3d> edges = new ArrayList<>(8);
      corner(outline, curves, x0 + nw, z0 + nw, nw, 180.0);
      corner(outline, curves, x1 - ne, z0 + ne, ne, 270.0);
      corner(outline, curves, x1 - se, z1 - se, se, 0.0);
      corner(outline, curves, x0 + sw, z1 - sw, sw, 90.0);
      if (north) {
         edge(edges, x0 + nw, z0, x1 - ne, z0);
      }

      if (south) {
         edge(edges, x0 + sw, z1, x1 - se, z1);
      }

      if (west) {
         edge(edges, x0, z0 + nw, x0, z1 - sw);
      }

      if (east) {
         edge(edges, x1, z0 + ne, x1, z1 - se);
      }

      this.outline = outline.toArray(new Vec3d[0]);
      this.curves = curves.toArray(new Vec3d[0]);
      this.edges = edges.toArray(new Vec3d[0]);
   }

   private boolean matches(double x0, double z0, double x1, double z1, double radius, int mask) {
      return this.mask == mask
         && Double.compare(this.x0, x0) == 0
         && Double.compare(this.z0, z0) == 0
         && Double.compare(this.x1, x1) == 0
         && Double.compare(this.z1, z1) == 0
         && Double.compare(this.radius, radius) == 0;
   }

   private static void corner(List<Vec3d> outline, List<Vec3d> curves, double cx, double cz, double radius, double startDegrees) {
      if (radius <= 0.0) {
         outline.add(new Vec3d(cx, 63.0, cz));
      } else {
         Vec3d previous = null;

         for (int i = 0; i <= 4; i++) {
            double angle = Math.toRadians(startDegrees + 90.0 * i / 4.0);
            Vec3d point = new Vec3d(cx + Math.cos(angle) * radius, 63.0, cz + Math.sin(angle) * radius);
            outline.add(point);
            if (previous != null) {
               curves.add(previous);
               curves.add(point);
            }

            previous = point;
         }
      }
   }

   private static void edge(List<Vec3d> edges, double ax, double az, double bx, double bz) {
      edges.add(new Vec3d(ax, 63.0, az));
      edges.add(new Vec3d(bx, 63.0, bz));
   }

   static final class Cache {
      private static final int CAPACITY = 512;
      private final LinkedHashMap<Long, ChunkTileGeometry> tiles = new LinkedHashMap<>(64, 0.75F, true);
      private long frame;

      void beginFrame() {
         this.frame++;
      }

      void clear() {
         this.tiles.clear();
      }

      int size() {
         return this.tiles.size();
      }

      ChunkTileGeometry get(long key, double x0, double z0, double x1, double z1, double radius, int mask) {
         ChunkTileGeometry tile = this.tiles.get(key);
         if (tile != null && tile.matches(x0, z0, x1, z1, radius, mask)) {
            tile.usedFrame = this.frame;
            return tile;
         }

         ChunkTileGeometry next = new ChunkTileGeometry(x0, z0, x1, z1, radius, mask);
         next.usedFrame = this.frame;
         if (tile == null && this.tiles.size() >= 512) {
            Iterator<Entry<Long, ChunkTileGeometry>> oldest = this.tiles.entrySet().iterator();
            Entry<Long, ChunkTileGeometry> entry = oldest.next();
            if (entry.getValue().usedFrame < this.frame - 1L) {
               oldest.remove();
               this.tiles.put(key, next);
            }
         } else {
            this.tiles.put(key, next);
         }

         return next;
      }
   }
}
