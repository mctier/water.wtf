package com.utilitymod.anubis.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import java.math.BigDecimal;
import java.util.Locale;

public final class NumberSetting extends Setting {
   private static final int MAX_DECIMALS = 4;
   private static final double[] POW10 = new double[]{1.0, 10.0, 100.0, 1000.0, 10000.0};
   private static final String[] FORMATS = new String[]{LT2.L1, LT2.L2, LT2.L3, LT2.L4, LT2.L5};
   private final float[] value;
   private final float min;
   private final float max;
   private float step;
   private int stepDecimals;
   private double gridMin;
   private double gridStep;

   public NumberSetting(String name, float value, float min, float max) {
      super(name);
      this.value = new float[]{value};
      this.min = min;
      this.max = max;
   }

   public NumberSetting step(float step) {
      if (step >= 0.0F && !Float.isInfinite(step)) {
         this.step = step;
         this.stepDecimals = step > 0.0F ? Math.max(decimalsOf(step), decimalsOf(this.min)) : 0;
         this.gridMin = this.toDecimals(this.min);
         double written = this.toDecimals(step);
         this.gridStep = written > 0.0 ? written : step;
         this.set(this.value[0]);
         return this;
      } else {
         throw new IllegalArgumentException(LT.L1 + step + LT.L2 + this.name() + LT2.L6);
      }
   }

   public float step() {
      return this.step;
   }

   public float get() {
      return this.value[0];
   }

   public int getInt() {
      return Math.round(this.value[0]);
   }

   public void set(float newValue) {
      float snapped = this.snap(newValue);
      if (this.value[0] != snapped) {
         this.value[0] = snapped;
         this.notifyChanged();
      }
   }

   public float snap(float v) {
      float clamped = this.clamp(v);
      if (this.step <= 0.0F) {
         return clamped;
      }

      long k = Math.round((clamped - this.gridMin) / this.gridStep + 1.0E-9);
      float snapped = this.onGrid(k);
      if (snapped > this.max) {
         snapped = this.onGrid(k - 1L);
      }

      return Math.max(this.min, Math.min(this.max, snapped));
   }

   public int decimals() {
      if (this.step > 0.0F) {
         return this.stepDecimals;
      } else {
         float range = this.max - this.min;
         if (this.min == Math.rint(this.min) && this.max == Math.rint(this.max) && range >= 20.0F) {
            return 0;
         } else {
            return range <= 2.0F ? 2 : 1;
         }
      }
   }

   public String formatted() {
      int decimals = this.decimals();
      return decimals == 0 ? Integer.toString(Math.round(this.value[0])) : String.format(Locale.ROOT, FORMATS[decimals], this.value[0]);
   }

   public float[] value() {
      return this.value;
   }

   public float min() {
      return this.min;
   }

   public float max() {
      return this.max;
   }

   private float clamp(float v) {
      if (Float.isNaN(v)) {
         return this.value[0];
      } else if (v < this.min) {
         return this.min;
      } else {
         return v > this.max ? this.max : v;
      }
   }

   private float onGrid(long k) {
      return (float)this.toDecimals(this.gridMin + k * this.gridStep);
   }

   private double toDecimals(double v) {
      double scale = POW10[this.stepDecimals];
      return Math.round(v * scale) / scale;
   }

   private static int decimalsOf(float v) {
      if (!Float.isFinite(v)) {
         return 0;
      }

      int scale = new BigDecimal(Float.toString(v)).stripTrailingZeros().scale();
      return Math.max(0, Math.min(4, scale));
   }

   @Override
   public JsonElement serialize() {
      return new JsonPrimitive(this.value[0]);
   }

   @Override
   protected void deserializeValue(JsonElement element) {
      if (element != null && element.isJsonPrimitive()) {
         try {
            this.value[0] = this.snap(element.getAsFloat());
         } catch (RuntimeException var3) {
         }
      }
   }


    // cloak literals - runtime PUA carriers (cloak) or functional plain (LT)
    private static final class LT {
            static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2142,0xcce4,0x32bc,0x1dd1,0x088d,0x5ea9,0x01bc,0x7cb3,0xe6db,0x59b0,0xcb31,0x18b0,0xbac4}, new char[]{0x01f5,0x18de,0x2134,0x1db0,0x32d0,0x08e4,0xe6bf,0x5990,0xcc97,0xbab0,0xcb54,0x5ed9,0x7c93}).toString();
            static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6608,0x4dd7,0xaf11,0x53dd,0x1f0d,0xad96,0x7d21,0xc81a,0x799a,0xf890,0xbc58,0xcfbd,0xf762,0xa642}, new char[]{0x79ba,0xf704,0xbc37,0x53af,0xc83a,0x667b,0x4db2,0xaf65,0xa636,0xcfd4,0x1f63,0xadf1,0xf8b0,0x7d06}).plainString();
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT2 {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2a20,0xaf31,0x30e3,0xc84b}, new char[]{0xc86e,0x2a0e,0x30d3,0xaf57}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2b0b,0xba56,0x45be,0xeb04}, new char[]{0x459b,0x2b25,0xeb35,0xba30}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1374,0xc5e9,0x057e,0x2761}, new char[]{0x1351,0x274f,0xc5db,0x0518}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf2bc,0x8786,0x0c9c,0x9655}, new char[]{0x9670,0xf292,0x0caf,0x87e0}).plainString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x70cf,0xe0de,0x533f,0x36f7}, new char[]{0x36d2,0x5311,0x70fb,0xe0b8}).plainString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd3dc}, new char[]{0xd3fb,0x8c15,0x5444,0xacf9}).plainString();
    }
}
