package com.utilitymod.anubis.util.netherite;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.ChunkData;

public final class HiddenDebrisScanner {
   private static final int BLOCK_VALUES = 4096;
   private static final int BIOME_VALUES = 64;
   private static final int BLOCK_PALETTE_BITS = 8;
   private static final int BIOME_PALETTE_BITS = 3;

   private HiddenDebrisScanner() {
   }

   public static List<Integer> findHiddenSections(ChunkData data, int sectionCount, int minSectionY, int targetStateId) {
      Objects.requireNonNull(data, LT.L1);
      if (targetStateId < 0) {
         throw new IllegalArgumentException(LT.L2);
      }

      PacketByteBuf buf = data.getSectionsDataBuf();

      try {
         List<Integer> sections = scanSections(buf, sectionCount, minSectionY, targetStateId);
         return buf.readableBytes() == 0 ? sections : List.of();
      } catch (RuntimeException malformed) {
         return List.of();
      } finally {
         buf.release();
      }
   }

   static List<Integer> scanSections(PacketByteBuf buf, int sectionCount, int minSectionY, int targetStateId) {
      Objects.requireNonNull(buf, LT.L3);
      if (sectionCount >= 0 && targetStateId >= 0) {
         List<Integer> hidden = new ArrayList<>();

         for (int i = 0; i < sectionCount; i++) {
            int nonEmptyBlocks = buf.readUnsignedShort();
            HiddenDebrisScanner.PaletteRead blocks = readPalette(buf, 4096, 8, 4, targetStateId);
            readPalette(buf, 64, 3, 1, -1);
            if (nonEmptyBlocks > 0 && blocks.hiddenTarget()) {
               hidden.add(minSectionY + i);
            }
         }

         return List.copyOf(hidden);
      } else {
         throw new IllegalArgumentException(LT.L4);
      }
   }

   private static HiddenDebrisScanner.PaletteRead readPalette(PacketByteBuf buf, int valueCount, int maxPaletteBits, int minBits, int target) {
      int bits = buf.readUnsignedByte();
      int storageBits;
      int[] palette;
      if (bits == 0) {
         storageBits = 0;
         palette = new int[]{buf.readVarInt()};
      } else if (bits <= maxPaletteBits) {
         storageBits = Math.max(minBits, bits);
         int size = buf.readVarInt();
         if (size <= 0 || size > 1 << storageBits) {
            throw new IllegalArgumentException(LT.L5);
         }

         palette = new int[size];

         for (int i = 0; i < size; i++) {
            palette[i] = buf.readVarInt();
         }
      } else {
         storageBits = bits;
         if (storageBits > 30) {
            throw new IllegalArgumentException(LT.L6);
         }

         palette = null;
      }

      int valuesPerLong = storageBits == 0 ? 0 : 64 / storageBits;
      if (storageBits > 0 && valuesPerLong == 0) {
         throw new IllegalArgumentException(LT.L7);
      }

      int longs = storageBits == 0 ? 0 : (valueCount + valuesPerLong - 1) / valuesPerLong;
      long bytes = longs * 8L;
      if (bytes > buf.readableBytes()) {
         throw new IndexOutOfBoundsException(LT.L8);
      }

      boolean paletteHasTarget = palette != null && contains(palette, target);
      if (!paletteHasTarget) {
         buf.skipBytes((int)bytes);
         return new HiddenDebrisScanner.PaletteRead(false, true);
      }

      int used = 0;
      if (storageBits == 0) {
         if (palette[0] == target) {
            used = valueCount;
         }
      } else {
         int start = buf.readerIndex();
         long mask = (1L << storageBits) - 1L;

         for (int i = 0; i < valueCount; i++) {
            int longIndex = i / valuesPerLong;
            int bitIndex = i - longIndex * valuesPerLong;
            int raw = (int)(buf.getLong(start + longIndex * 8) >>> bitIndex * storageBits & mask);
            int value = palette == null ? raw : paletteValue(palette, raw);
            if (value == target) {
               used++;
            }
         }
      }

      buf.skipBytes((int)bytes);
      return new HiddenDebrisScanner.PaletteRead(paletteHasTarget, used == 0);
   }

   private static int paletteValue(int[] palette, int index) {
      if (index >= 0 && index < palette.length) {
         return palette[index];
      } else {
         throw new IllegalArgumentException(LT.L9);
      }
   }

   private static boolean contains(int[] values, int target) {
      for (int value : values) {
         if (value == target) {
            return true;
         }
      }

      return false;
   }

   private record PaletteRead(boolean paletteHasTarget, boolean targetUnused) {
      boolean hiddenTarget() {
         return this.paletteHasTarget && this.targetUnused;
      }
   }


    // cloak literals - runtime PUA carriers (cloak) or functional plain (LT)
    private static final class LT {
            static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc3d4,0x7690,0xbe1f,0x5e4b}, new char[]{0x76f4,0xbe7e,0x5e3f,0xc3b5}).plainString();
            static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8ab5,0x020e,0x3475,0x066f,0x5b32,0x9dfa,0x9606,0x5eaa,0x46aa,0x9646,0x36aa,0x73a2,0xde5b,0xcaf9,0x4f21,0x8a0e,0xc967,0xc55b,0x9b6f,0xfaba,0x039f,0x980e,0x3d3b,0x0011,0x4d86,0x465e,0x225d,0x5e1d,0x684e,0x5548,0x248b,0xb260,0x99fd,0x3c42}, new char[]{0x9d8e,0x3d5a,0xc915,0x5e7a,0xde3e,0x9632,0xcaaa,0x9989,0x9b0e,0x9672,0x552d,0x4f68,0x36ce,0x468a,0x5ec7,0xc52e,0x462d,0x987a,0x686e,0x73c0,0x4de3,0x5b12,0xb20e,0x341a,0x0260,0x03b2,0x8adb,0x8a6b,0x223a,0x24ea,0xface,0x0078,0x0619,0x3c27}).toString();
            static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x91f5,0x7a24,0x1a77}, new char[]{0x1a15,0x7a51,0x9193,0x8451}).plainString();
            static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd3f4,0x8202,0x165d,0xe53f,0x414a,0x3c74,0xb320,0x5075,0xd76d,0x581f,0x7864,0x6c7d,0xd77c,0x2619,0x24be,0xfd05,0x60fa,0xc8f4,0x2757,0x18f6,0x3fee,0x32ef,0x23ad,0xc939,0x4c32,0xbe79,0x9f04,0x84a9,0xfaaa,0xfe9b}, new char[]{0xc89d,0x23c3,0xe549,0x24df,0xfd69,0x826b,0x3f8a,0xd75c,0x7817,0x3c11,0xbe1a,0xd380,0xfac3,0x4125,0x501b,0xfebb,0x2724,0xb343,0xc958,0x9f6a,0x8489,0x4c53,0x266b,0x609d,0x586a,0x1630,0x328a,0x6c13,0xd719,0x1885}).plainString();
            static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe8e7,0x7866,0x8a2b,0x1f92,0xdd00,0xeecd,0xc95c,0xe807,0x4fed,0x7df2,0x6806,0xf69e,0xcaa7,0x918f,0x6730,0x4da5,0x874c,0x076f,0xb4a6,0x1925,0x7e4b,0xae36,0x1b6f,0x9153,0xee88,0xae2d}, new char[]{0xae44,0xb4c8,0x8a5d,0x4dc4,0x913f,0x6759,0xeeec,0x7e6b,0xe86b,0x7809,0x872f,0x1944,0xae5a,0x6826,0x7d82,0x1b0e,0xcacb,0xf6fb,0x071b,0xdd74,0xc939,0x1fb2,0x91fc,0xe88e,0x4f97,0xeea8}).plainString();
            static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x73ef,0x6f77,0x05ab,0xd82c,0x47f5,0xd186,0x8074,0x404d,0xc1e0,0x3f1f,0x90c2,0xd360,0x5232,0xa815,0x44d5,0x6d4f,0x81dc,0x69a4,0xf6de,0x4ed3,0xfc36,0xcfe6,0xf603,0x0b78,0x99ba,0x663c}, new char[]{0xf6ba,0x44bc,0xa867,0xd1e3,0x0b1b,0x4781,0x3f3f,0x90b2,0x69c5,0x81b0,0xf666,0xd858,0x739b,0x99df,0x6f57,0x6d26,0x4ea0,0x5212,0xc194,0x4022,0x05c4,0xd340,0xcf91,0xfc5f,0x6658,0x8011}).plainString();
            static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x44b9,0x0d08,0xd331,0xabc6,0x6ed2,0x55af,0x35e6,0x32ca,0x90cb,0xbe4c,0x1565,0xa1e1,0x591f,0x4e95,0x1176,0x65cc,0x3a06,0x5b94,0x1541,0x624f,0x6186,0x8455,0xb35c,0x6014,0x8700,0xa110,0x00cf,0xe4de,0xccc5}, new char[]{0x8769,0x4efb,0xabb0,0xcca4,0x3a6a,0x607d,0xe4ba,0x35c6,0xa191,0x1504,0x32a6,0xd354,0x90bf,0x596b,0x55ca,0xb37c,0x65bf,0x44cd,0x6ebd,0x1104,0x0d69,0x1526,0xbe29,0x8475,0xa167,0x61ef,0x622b,0x5be0,0x00a7}).plainString();
            static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x171a,0x5816,0xe17a,0x12c7,0x09da,0x89ca,0x9a33,0xe17c,0x4405,0xfef1,0xb003,0xb3b2,0x1f1e,0xff28,0xf81e,0x5610,0xbb52,0x4431,0x3012,0x2e9a,0xa802,0x4f41}, new char[]{0xa876,0x4443,0x1f6b,0x567e,0xe119,0x12a6,0xb3c6,0x9a56,0x2efe,0x173a,0xe10c,0xbb33,0x587a,0x89af,0x4f35,0xff5c,0x4460,0xfed1,0x3076,0xf87f,0xb077,0x09bb}).plainString();
            static final String L9 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe9fa,0xfdd8,0x6d8a,0xf929,0x98d5,0x6930,0x71f6,0x54e8,0xf403,0x082e,0xa94b,0x48b6,0x517c,0xebec,0x3e06,0x5476,0x3801,0xc7ef,0x8fd5,0x8697,0xabbb,0xf2f7,0x74d0,0x8bcb,0x3617,0x3bdd,0x6a2f,0xd347,0x94e0,0x9374,0x33f8}, new char[]{0x6a5f,0xeb8d,0x3bbe,0x541d,0x084b,0x7192,0x33d8,0x510c,0x74b1,0x98b9,0x9485,0x3663,0x8bbf,0xa92e,0xf423,0x5481,0x931a,0x86f3,0x3864,0xe982,0x6910,0xf29e,0x6df9,0xc7cf,0x3e6f,0x48d8,0xabcd,0x8fb4,0xfdb4,0xd32e,0xf94d}).plainString();
    }
}
