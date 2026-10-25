package com.utilitymod.anubis.util.glyph;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.ColorHelper;

public final class GlyphTint {
   public static final String THEME = LT.L1;
   public static final String MANUAL = LT.L2;
   private static final float WARM = 0.16666667F;
   private static final float COOL = 0.6666667F;
   private static final float COMPANION_TURN = -0.09F;
   private int primary;
   private int secondary;
   private int highlight;
   private int companion;
   private int dust;
   private int flash;
   private float hue;
   private float saturation;
   private float value;

   public GlyphTint resolve(String mode, int accent, int manualMain, int manualSecond) {
      boolean manual = LT.L2.equals(mode);
      this.primary = ColorHelper.fullAlpha(manual ? manualMain : accent);
      this.split(this.primary);
      if (manual) {
         this.secondary = ColorHelper.fullAlpha(manualSecond);
         this.companion = mix(this.primary, this.secondary, 0.5F);
      } else {
         this.secondary = hsv(toward(this.hue, 0.6666667F, 0.025F), this.saturation * 1.05F, this.value * 0.66F);
         this.companion = hsv(this.hue + -0.09F, this.saturation * 0.9F, this.value * 0.94F);
      }

      this.highlight = hsv(toward(this.hue, 0.16666667F, 0.03F), this.saturation * 0.5F, this.value + (1.0F - this.value) * 0.85F);
      this.dust = hsv(toward(this.hue, 0.16666667F, 0.02F), this.saturation * 0.34F, 0.5F + this.value * 0.4F);
      this.flash = mix(this.highlight, -1, 0.6F);
      return this;
   }

   private void split(int rgb) {
      int r = ColorHelper.getRed(rgb);
      int g = ColorHelper.getGreen(rgb);
      int b = ColorHelper.getBlue(rgb);
      int max = Math.max(r, Math.max(g, b));
      int min = Math.min(r, Math.min(g, b));
      float range = max - min;
      this.value = max / 255.0F;
      this.saturation = max == 0 ? 0.0F : range / max;
      if (range == 0.0F) {
         this.hue = 0.0F;
      } else if (max == r) {
         this.hue = MathHelper.fractionalPart((g - b) / range / 6.0F);
      } else if (max == g) {
         this.hue = (2.0F + (b - r) / range) / 6.0F;
      } else {
         this.hue = (4.0F + (r - g) / range) / 6.0F;
      }
   }

   private static float toward(float from, float target, float step) {
      float delta = target - from;
      delta -= MathHelper.floor(delta + 0.5F);
      return Math.abs(delta) <= step ? target : from + Math.copySign(step, delta);
   }

   private static int hsv(float h, float s, float v) {
      float wrapped = MathHelper.fractionalPart(h);
      if (wrapped >= 1.0F) {
         wrapped = 0.0F;
      }

      return MathHelper.hsvToArgb(wrapped, MathHelper.clamp(s, 0.0F, 1.0F), MathHelper.clamp(v, 0.0F, 1.0F), 255);
   }

   public int primary() {
      return this.primary;
   }

   public int secondary() {
      return this.secondary;
   }

   public int highlight() {
      return this.highlight;
   }

   public int companion() {
      return this.companion;
   }

   public int dust() {
      return this.dust;
   }

   public int flash() {
      return this.flash;
   }

   public static int alpha(int rgb, float alpha) {
      float clamped = alpha <= 0.0F ? 0.0F : Math.min(1.0F, alpha);
      return (int)(clamped * 255.0F + 0.5F) << 24 | rgb & 16777215;
   }

   public static int mix(int from, int to, float k) {
      return ColorHelper.lerp(Math.max(0.0F, Math.min(1.0F, k)), from, to);
   }

   public static int lift(int rgb, float amount) {
      return mix(ColorHelper.fullAlpha(rgb), -1, amount);
   }


    // cloak literals - runtime PUA carriers (cloak) or functional plain (LT)
    private static final class LT {
            static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1116,0xa47d,0x2d01,0xd714,0x6278}, new char[]{0xd740,0x117e,0xa418,0x2d6c,0x621d}).toString();
            static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd99b,0x0e4a,0xbc22,0x32ec,0xe898,0x72d1}, new char[]{0x32a1,0xd9fa,0x72bf,0x0e3f,0xbc43,0xe8f4}).toString();
    }
}
