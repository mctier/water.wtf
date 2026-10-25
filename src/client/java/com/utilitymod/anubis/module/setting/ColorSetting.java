package com.utilitymod.anubis.module.setting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public final class ColorSetting extends Setting {
   private final float[] value;
   private int packed;
   private int packedR;
   private int packedG;
   private int packedB;
   private int packedA;

   public ColorSetting(String name, int argb) {
      super(name);
      this.value = new float[]{(argb >> 16 & 0xFF) / 255.0F, (argb >> 8 & 0xFF) / 255.0F, (argb & 0xFF) / 255.0F, (argb >>> 24 & 0xFF) / 255.0F};
   }

   public float[] value() {
      return this.value;
   }

   public void setArgb(int argb) {
      if (this.argb() != argb) {
         this.setFromArgb(argb);
         this.notifyChanged();
      }
   }

   public int argb() {
      int r = Float.floatToRawIntBits(this.value[0]);
      int g = Float.floatToRawIntBits(this.value[1]);
      int b = Float.floatToRawIntBits(this.value[2]);
      int a = Float.floatToRawIntBits(this.value[3]);
      if (r != this.packedR || g != this.packedG || b != this.packedB || a != this.packedA) {
         this.packed = this.pack();
         this.packedR = r;
         this.packedG = g;
         this.packedB = b;
         this.packedA = a;
      }

      return this.packed;
   }

   public int rgb() {
      return this.argb() & 16777215;
   }

   public int argbWithAlpha(int alpha) {
      return clamp255(alpha) << 24 | this.argb() & 16777215;
   }

   private int pack() {
      return pack(this.value[0], this.value[1], this.value[2], this.value[3]);
   }

   private static int pack(float red, float green, float blue, float alpha) {
      int a = Math.round(clamp01(alpha) * 255.0F);
      int r = Math.round(clamp01(red) * 255.0F);
      int g = Math.round(clamp01(green) * 255.0F);
      int b = Math.round(clamp01(blue) * 255.0F);
      return a << 24 | r << 16 | g << 8 | b;
   }

   @Override
   public JsonElement compact(JsonElement element) {
      try {
         if (element != null && element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            return new JsonPrimitive(
               hex(pack(readFloat(object, LT2.L1, 0.0F), readFloat(object, LT2.L2, 0.0F), readFloat(object, LT2.L3, 0.0F), readFloat(object, LT2.L4, 1.0F)))
            );
         }

         if (element != null && element.isJsonArray() && element.getAsJsonArray().size() >= 3) {
            JsonArray array = element.getAsJsonArray();
            float alpha = array.size() > 3 ? array.get(3).getAsFloat() : 1.0F;
            return new JsonPrimitive(hex(pack(array.get(0).getAsFloat(), array.get(1).getAsFloat(), array.get(2).getAsFloat(), alpha)));
         }
      } catch (RuntimeException var4) {
      }

      return element;
   }

   public static String hex(int argb) {
      return String.format(LT2.L5, argb);
   }

   @Override
   public JsonElement serialize() {
      JsonObject object = new JsonObject();
      object.addProperty(LT2.L1, clamp01(this.value[0]));
      object.addProperty(LT2.L2, clamp01(this.value[1]));
      object.addProperty(LT2.L3, clamp01(this.value[2]));
      object.addProperty(LT2.L4, clamp01(this.value[3]));
      return object;
   }

   @Override
   protected void deserializeValue(JsonElement element) {
      if (element != null) {
         try {
            if (element.isJsonObject()) {
               JsonObject object = element.getAsJsonObject();
               this.value[0] = readFloat(object, LT2.L1, this.value[0]);
               this.value[1] = readFloat(object, LT2.L2, this.value[1]);
               this.value[2] = readFloat(object, LT2.L3, this.value[2]);
               this.value[3] = readFloat(object, LT2.L4, this.value[3]);
               this.clampValues();
               return;
            }

            if (element.isJsonArray()) {
               JsonArray array = element.getAsJsonArray();

               for (int i = 0; i < Math.min(4, array.size()); i++) {
                  this.value[i] = clamp01(array.get(i).getAsFloat());
               }

               return;
            }

            if (element.isJsonPrimitive()) {
               JsonPrimitive primitive = element.getAsJsonPrimitive();
               if (primitive.isString()) {
                  this.setFromHex(primitive.getAsString());
               } else if (primitive.isNumber()) {
                  this.setFromArgb(primitive.getAsInt());
               }
            }
         } catch (RuntimeException var4) {
         }
      }
   }

   private void setFromHex(String raw) {
      String hex = raw.trim();
      if (hex.startsWith(LT2.L6)) {
         hex = hex.substring(1);
      }

      if (hex.length() == 6) {
         hex = LT.L1 + hex;
      }

      if (hex.length() == 8) {
         this.setFromArgb((int)Long.parseLong(hex, 16));
      }
   }

   private void setFromArgb(int argb) {
      this.value[0] = (argb >> 16 & 0xFF) / 255.0F;
      this.value[1] = (argb >> 8 & 0xFF) / 255.0F;
      this.value[2] = (argb & 0xFF) / 255.0F;
      this.value[3] = (argb >>> 24 & 0xFF) / 255.0F;
   }

   private void clampValues() {
      for (int i = 0; i < this.value.length; i++) {
         this.value[i] = clamp01(this.value[i]);
      }
   }

   private static float readFloat(JsonObject object, String name, float fallback) {
      return !object.has(name) ? fallback : clamp01(object.get(name).getAsFloat());
   }

   private static float clamp01(float v) {
      return !Float.isNaN(v) && !Float.isInfinite(v) ? Math.max(0.0F, Math.min(1.0F, v)) : 1.0F;
   }

   private static int clamp255(int value) {
      return Math.max(0, Math.min(255, value));
   }


    // cloak literals - runtime PUA carriers (cloak) or functional plain (LT)
    private static final class LT {
            static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x73ac,0xd44e}, new char[]{0x73ea,0xd408,0x0e2f,0x3921}).plainString();
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT2 {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x932e}, new char[]{0x935c,0xe66b,0x7272,0x86f0}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7ff5}, new char[]{0x7f92,0x4a23,0x5595,0x9efa}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xabb7}, new char[]{0xabd5,0x784f,0x7bd3,0x4005}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3bca}, new char[]{0x3bab,0x0168,0x5a30,0x319f}).plainString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4014,0x81ba,0x3f3c,0x20b3}, new char[]{0x819f,0x2083,0x3f04,0x404c}).plainString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8561}, new char[]{0x8542,0xe5f6,0x3e1e,0x2457}).plainString();
    }
}
