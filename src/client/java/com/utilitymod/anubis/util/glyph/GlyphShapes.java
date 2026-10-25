package com.utilitymod.anubis.util.glyph;

import java.util.Arrays;

public final class GlyphShapes {
   public static final float[] ANKH = ankh();
   public static final float[] SCARAB = scarab();
   public static final float[] SPARK = spark();
   public static final float[] GRAIN = grain();
   public static final float[] DIAMOND = diamond();
   public static final float[] DISK = new GlyphShapes.Builder().disk(0.0F, 0.0F, 1.0F, 1.0F, 12).build();
   public static final float[] OCTAGON = new GlyphShapes.Builder().disk(0.0F, 0.0F, 1.0F, 1.0F, 8).build();
   public static final float[] HALO = new GlyphShapes.Builder().arc(0.0F, 0.0F, 0.84F, 0.84F, 1.0F, 1.0F, 0.0F, (float) (Math.PI * 2), 20).build();
   public static final float[] EYE = horusEye();
   public static final float[] EYE_PUPIL = new GlyphShapes.Builder().offset(-0.14F, 0.04F).disk(-0.08F, 0.07F, 0.19F, 0.19F, 8).build();

   private GlyphShapes() {
   }

   private static float[] ankh() {
      GlyphShapes.Builder b = new GlyphShapes.Builder();
      b.quad(-0.17F, -1.0F, 0.17F, -1.0F, 0.09F, 0.06F, -0.09F, 0.06F);
      b.quad(-0.56F, -0.02F, -0.09F, 0.04F, -0.09F, 0.2F, -0.56F, 0.26F);
      b.quad(-0.09F, 0.04F, 0.09F, 0.04F, 0.09F, 0.2F, -0.09F, 0.2F);
      b.quad(0.09F, 0.04F, 0.56F, -0.02F, 0.56F, 0.26F, 0.09F, 0.2F);
      b.arc(0.0F, 0.6F, 0.19F, 0.28F, 0.3F, 0.42F, (float) (-Math.PI / 2), (float) (Math.PI * 3.0 / 2.0), 10);
      return b.build();
   }

   private static float[] scarab() {
      GlyphShapes.Builder b = new GlyphShapes.Builder();
      float gap = 0.035F;
      float cy = -0.12F;
      float rx = 0.56F;
      float ry = 0.78F;
      int steps = 5;

      for (int i = 0; i < steps; i++) {
         double a0 = (Math.PI / 2) + Math.PI * i / steps;
         double a1 = (Math.PI / 2) + Math.PI * (i + 1) / steps;
         float y0 = cy + (float)Math.sin(a0) * ry;
         float y1 = cy + (float)Math.sin(a1) * ry;
         float x0 = (float)Math.cos(a0) * rx - gap;
         float x1 = (float)Math.cos(a1) * rx - gap;
         b.quad(-gap, y0, x0, y0, x1, y1, -gap, y1);
         b.quad(gap, -y0 + 2.0F * cy, -x0, -y0 + 2.0F * cy, -x1, -y1 + 2.0F * cy, gap, -y1 + 2.0F * cy);
      }

      b.quad(-0.44F, 0.62F, 0.44F, 0.62F, 0.33F, 0.86F, -0.33F, 0.86F);
      b.quad(-0.24F, 0.86F, 0.24F, 0.86F, 0.13F, 1.0F, -0.13F, 1.0F);
      b.stroke(0.055F, -0.34F, 0.7F, -0.62F, 0.8F, -0.74F, 0.98F);
      b.stroke(0.055F, 0.34F, 0.7F, 0.62F, 0.8F, 0.74F, 0.98F);
      b.stroke(0.055F, -0.5F, 0.12F, -0.82F, 0.2F, -0.92F, 0.04F);
      b.stroke(0.055F, 0.5F, 0.12F, 0.82F, 0.2F, 0.92F, 0.04F);
      b.stroke(0.055F, -0.44F, -0.52F, -0.74F, -0.72F, -0.8F, -0.96F);
      b.stroke(0.055F, 0.44F, -0.52F, 0.74F, -0.72F, 0.8F, -0.96F);
      return b.build();
   }

   private static float[] spark() {
      GlyphShapes.Builder b = new GlyphShapes.Builder();
      b.quad(0.0F, -1.0F, 0.15F, 0.0F, 0.0F, 1.0F, -0.15F, 0.0F);
      b.quad(-0.62F, 0.0F, 0.0F, -0.13F, 0.62F, 0.0F, 0.0F, 0.13F);
      return b.build();
   }

   private static float[] grain() {
      return new GlyphShapes.Builder().quad(-0.8F, -0.62F, 0.72F, -0.88F, 0.9F, 0.46F, -0.52F, 0.82F).build();
   }

   private static float[] diamond() {
      return new GlyphShapes.Builder().quad(0.0F, -1.0F, 1.0F, 0.0F, 0.0F, 1.0F, -1.0F, 0.0F).build();
   }

   private static float[] horusEye() {
      GlyphShapes.Builder b = new GlyphShapes.Builder().offset(-0.14F, 0.04F);
      float[] upper = curve(-0.96F, 0.0F, -0.08F, 0.64F, 0.76F, 0.02F, 9);
      b.stroke(0.1F, append(upper, 1.3F, 0.02F));
      b.stroke(0.08F, curve(-0.96F, 0.0F, -0.06F, -0.46F, 0.76F, 0.02F, 8));
      b.stroke(0.12F, curve(-1.0F, 0.46F, 0.02F, 0.98F, 1.28F, 0.52F, 9));
      b.stroke(0.09F, -0.36F, -0.24F, -0.38F, -0.6F, -0.44F, -0.96F);
      b.stroke(
         0.08F,
         0.06F,
         -0.22F,
         0.3F,
         -0.44F,
         0.6F,
         -0.6F,
         0.85F,
         -0.72F,
         0.98F,
         -0.86F,
         0.95F,
         -0.99F,
         0.83F,
         -1.03F,
         0.74F,
         -0.96F,
         0.76F,
         -0.87F,
         0.85F,
         -0.85F
      );
      return b.build();
   }

   private static float[] curve(float x0, float y0, float cx, float cy, float x1, float y1, int steps) {
      float[] out = new float[(steps + 1) * 2];

      for (int i = 0; i <= steps; i++) {
         float t = (float)i / steps;
         float s = 1.0F - t;
         out[i * 2] = s * s * x0 + 2.0F * s * t * cx + t * t * x1;
         out[i * 2 + 1] = s * s * y0 + 2.0F * s * t * cy + t * t * y1;
      }

      return out;
   }

   private static float[] append(float[] points, float x, float y) {
      float[] out = Arrays.copyOf(points, points.length + 2);
      out[points.length] = x;
      out[points.length + 1] = y;
      return out;
   }

   private static final class Builder {
      private float[] data = new float[64];
      private int size;
      private float offsetX;
      private float offsetY;

      GlyphShapes.Builder offset(float x, float y) {
         this.offsetX = x;
         this.offsetY = y;
         return this;
      }

      GlyphShapes.Builder quad(float ax, float ay, float bx, float by, float cx, float cy, float dx, float dy) {
         if (this.size + 8 > this.data.length) {
            this.data = Arrays.copyOf(this.data, this.data.length * 2);
         }

         this.data[this.size++] = ax + this.offsetX;
         this.data[this.size++] = ay + this.offsetY;
         this.data[this.size++] = bx + this.offsetX;
         this.data[this.size++] = by + this.offsetY;
         this.data[this.size++] = cx + this.offsetX;
         this.data[this.size++] = cy + this.offsetY;
         this.data[this.size++] = dx + this.offsetX;
         this.data[this.size++] = dy + this.offsetY;
         return this;
      }

      GlyphShapes.Builder arc(float cx, float cy, float innerX, float innerY, float outerX, float outerY, float from, float to, int steps) {
         for (int i = 0; i < steps; i++) {
            double a0 = from + (double)((to - from) * i) / steps;
            double a1 = from + (double)((to - from) * (i + 1)) / steps;
            float c0 = (float)Math.cos(a0);
            float s0 = (float)Math.sin(a0);
            float c1 = (float)Math.cos(a1);
            float s1 = (float)Math.sin(a1);
            this.quad(
               cx + c0 * innerX, cy + s0 * innerY, cx + c0 * outerX, cy + s0 * outerY, cx + c1 * outerX, cy + s1 * outerY, cx + c1 * innerX, cy + s1 * innerY
            );
         }

         return this;
      }

      GlyphShapes.Builder disk(float cx, float cy, float rx, float ry, int sides) {
         float[] ring = new float[sides * 2];

         for (int i = 0; i < sides; i++) {
            double a = (Math.PI * 2) * i / sides + Math.PI / sides;
            ring[i * 2] = cx + (float)Math.cos(a) * rx;
            ring[i * 2 + 1] = cy + (float)Math.sin(a) * ry;
         }

         for (int i = 1; i + 1 < sides; i += 2) {
            int j = Math.min(i + 2, sides - 1);
            this.quad(ring[0], ring[1], ring[i * 2], ring[i * 2 + 1], ring[(i + 1) * 2], ring[(i + 1) * 2 + 1], ring[j * 2], ring[j * 2 + 1]);
         }

         return this;
      }

      GlyphShapes.Builder stroke(float width, float... points) {
         int n = points.length / 2;
         if (n < 2) {
            return this;
         }

         float half = width * 0.5F;
         float[] nx = new float[n];
         float[] ny = new float[n];

         for (int i = 0; i < n; i++) {
            float sx = 0.0F;
            float sy = 0.0F;
            float px = 0.0F;
            float py = 0.0F;
            if (i > 0) {
               float dx = points[i * 2] - points[i * 2 - 2];
               float dy = points[i * 2 + 1] - points[i * 2 - 1];
               float len = (float)Math.sqrt(dx * dx + dy * dy);
               if (len > 1.0E-6F) {
                  px = -dy / len;
                  py = dx / len;
                  sx += px;
                  sy += py;
               }
            }

            if (i < n - 1) {
               float dx = points[i * 2 + 2] - points[i * 2];
               float dy = points[i * 2 + 3] - points[i * 2 + 1];
               float len = (float)Math.sqrt(dx * dx + dy * dy);
               if (len > 1.0E-6F) {
                  sx += -dy / len;
                  sy += dx / len;
                  if (i == 0) {
                     px = -dy / len;
                     py = dx / len;
                  }
               }
            }

            float len = (float)Math.sqrt(sx * sx + sy * sy);
            if (len < 1.0E-6F) {
               sx = px;
               sy = py;
               len = 1.0F;
            }

            sx /= len;
            sy /= len;
            float dot = Math.max(0.5F, sx * px + sy * py);
            nx[i] = sx * half / dot;
            ny[i] = sy * half / dot;
         }

         for (int i = 0; i < n - 1; i++) {
            float ax = points[i * 2];
            float ay = points[i * 2 + 1];
            float bx = points[i * 2 + 2];
            float by = points[i * 2 + 3];
            this.quad(ax - nx[i], ay - ny[i], bx - nx[i + 1], by - ny[i + 1], bx + nx[i + 1], by + ny[i + 1], ax + nx[i], ay + ny[i]);
         }

         return this;
      }

      float[] build() {
         return Arrays.copyOf(this.data, this.size);
      }
   }
}
