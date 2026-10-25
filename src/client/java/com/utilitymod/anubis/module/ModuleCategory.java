package com.utilitymod.anubis.module;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ModuleCategory {
   private static final List<ModuleCategory> REGISTERED = new ArrayList<>();
   private static volatile List<ModuleCategory> values = List.of();
   public static final ModuleCategory COMBAT = register(LT.L1, LT.L2, ModuleCategory.Icon.CROSSHAIR);
   public static final ModuleCategory MISC = register(LT.L3, LT.L4, ModuleCategory.Icon.GRID);
   public static final ModuleCategory DONUT = register(LT.L5, LT.L6, ModuleCategory.Icon.RING);
   public static final ModuleCategory RENDER = register(LT.L7, LT.L8, ModuleCategory.Icon.EYE);
   public static final ModuleCategory CLIENT = register(LT.L9, LT.L10, ModuleCategory.Icon.ANKH);
   public static final ModuleCategory MOVEMENT = register(LT.L11, LT.L12, ModuleCategory.Icon.MOVE);
   public static final ModuleCategory BASE_RAID = register(LT.L13, LT.L14, ModuleCategory.Icon.HOUSE);
   public static final ModuleCategory VISUALS = register(LT.L15, LT.L16, ModuleCategory.Icon.MONITOR);
   public static final ModuleCategory DONUT_MACROS = register(LT.L17, LT.L18, ModuleCategory.Icon.PAPYRUS);
   private final String id;
   private final String displayName;
   private final ModuleCategory.Icon icon;

   private ModuleCategory(String id, String displayName, ModuleCategory.Icon icon) {
      this.id = id;
      this.displayName = displayName;
      this.icon = icon;
   }

   public static synchronized ModuleCategory register(String id, String displayName, ModuleCategory.Icon icon) {
      if (!isValidId(id)) {
         throw new IllegalArgumentException(LT.L19 + id);
      }

      for (ModuleCategory category : REGISTERED) {
         if (category.id.equals(id)) {
            throw new IllegalArgumentException(LT.L20 + id);
         }
      }

      ModuleCategory category = new ModuleCategory(id, Objects.requireNonNull(displayName, LT.L21), Objects.requireNonNull(icon, LT.L22));
      REGISTERED.add(category);
      values = List.copyOf(REGISTERED);
      return category;
   }

   private static boolean isValidId(String id) {
      if (id != null && !id.isEmpty() && id.charAt(0) >= 'A' && id.charAt(0) <= 'Z') {
         for (int i = 1; i < id.length(); i++) {
            char c = id.charAt(i);
            if ((c < 'A' || c > 'Z') && (c < '0' || c > '9') && c != '_') {
               return false;
            }
         }

         return true;
      } else {
         return false;
      }
   }

   public static ModuleCategory register(String id, String displayName) {
      return register(id, displayName, ModuleCategory.Icon.ANKH);
   }

   public static List<ModuleCategory> values() {
      return values;
   }

   public String name() {
      return this.id;
   }

   public String displayName() {
      return this.displayName;
   }

   public ModuleCategory.Icon icon() {
      return this.icon;
   }

   @Override
   public String toString() {
      return this.id;
   }

   public enum Icon {
      CROSSHAIR,
      CHEVRON,
      GRID,
      RING,
      EYE,
      MONITOR,
      HOUSE,
      PAPYRUS,
      MOVE,
      ANKH;
   }


    // cloak literals - runtime PUA carriers (cloak) or functional plain (LT)
    private static final class LT {
            static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1ed7,0x77c5,0x2e15,0x283a,0xa04f,0x1097}, new char[]{0x2e56,0xa000,0x1e9a,0x7787,0x287b,0x10c3}).plainString();
            static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xacf5,0x917d,0x77a9,0xae45,0x4c98,0xf610}, new char[]{0x913e,0x4cf7,0xf67d,0xac97,0xae24,0x77dd}).toString();
            static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5dc7,0x5d89,0x1c42,0x7d55}, new char[]{0x7d18,0x5dc0,0x5d94,0x1c01}).plainString();
            static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x989d,0xc6fa,0x64f1,0xc64b}, new char[]{0xc606,0x6498,0xc689,0x98fe}).toString();
            static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1efa,0x8d4b,0x5f23,0xbc27,0x6530}, new char[]{0xbc63,0x657f,0x8d05,0x1eaf,0x5f77}).plainString();
            static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x62dd,0x810a,0x0474,0xe291,0x3f17}, new char[]{0x0430,0xe2fe,0x3f79,0x62a8,0x817e}).toString();
            static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9a8a,0x3f4a,0x0bda,0x804b,0x95ad,0x2c29}, new char[]{0x2c7b,0x3f0f,0x95e3,0x0b9e,0x800e,0x9ad8}).plainString();
            static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xdf52,0xa4b4,0x6b86,0x2e8e,0x4ff6,0x643d}, new char[]{0xdf00,0x4f93,0x6be8,0xa4d0,0x6458,0x2efc}).toString();
            static final String L9 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0537,0x4fa7,0x43fc,0xadc8,0x195e,0xab05}, new char[]{0x4fe4,0x057b,0xab4c,0x191b,0xad86,0x43a8}).plainString();
            static final String L10 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7ac9,0x6afe,0x6520,0xb8a1,0x23eb,0x2bd7,0x4174,0xf045,0xb7ba,0x1f49,0x5145,0x9835,0x481e}, new char[]{0x9874,0x2385,0x5130,0x6542,0x411d,0xb7c9,0x1f69,0xf006,0x6a92,0xb8c8,0x7aac,0x2bb9,0x486a}).toString();
            static final String L11 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0de0,0xe912,0x8933,0x7ab2,0x7fd8,0x070d,0xbdc8,0x70da}, new char[]{0x7097,0x0daf,0x075b,0x7af7,0x897e,0xbd8d,0xe95c,0x7f8c}).plainString();
            static final String L12 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa102,0x63e6,0xe855,0xd4d3,0x8818,0x810d,0xad83,0x3076}, new char[]{0xadce,0xe83a,0x6390,0x8168,0x8875,0x3013,0xd4bd,0xa176}).toString();
            static final String L13 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc6bc,0x380b,0xd3ba,0x98a2,0xa7d9,0xd4a8,0xe1b0,0xe7ec,0x0509}, new char[]{0xe1f2,0xc6fd,0xe7bf,0xd4ed,0xd3e5,0xa78b,0x384a,0x0540,0x98e6}).plainString();
            static final String L14 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x06f3,0xc477,0x458c,0x889e,0xc5a7,0xa853,0x0671,0xfb54,0xbeaf,0x48ed,0xaa82,0xef1b}, new char[]{0xc435,0x45ed,0x489e,0x0696,0xa873,0xfb12,0xaaeb,0x061f,0xef7f,0x88f7,0xbec1,0xc5c0}).toString();
            static final String L15 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7ae0,0x4bc4,0x62d7,0x371f,0x4ac7,0x5dc0,0xe498}, new char[]{0x6281,0x4a8e,0xe4cb,0x7ab5,0x5d81,0x4b88,0x374c}).plainString();
            static final String L16 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x889b,0x1e07,0xe7c7,0x135b,0x8f07,0x2d61,0xf01c}, new char[]{0xf04a,0x1332,0xe7b4,0x2d14,0x8f66,0x88f7,0x1e74}).toString();
            static final String L17 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4ffb,0xb19e,0x21ba,0x378f,0x7f7d,0x3878,0x2139,0x380d,0x1c6e,0xd03a,0x3361,0x35ac}, new char[]{0x3849,0x21f5,0x35e2,0x1c3b,0x4faf,0xd065,0xb1d3,0x3839,0x3322,0x37dd,0x7f32,0x216a}).plainString();
            static final String L18 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xdde9,0x47bd,0xf957,0x165f,0x44d0}, new char[]{0x1612,0xdd88,0x47de,0xf925,0x44bf}).toString();
            static final String L19 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xcf48,0xb1cd,0xab3a,0x75d6,0x5ec9,0x095d,0x7c24,0x11fa,0x8c64,0x0532,0xcf2d,0x77fa,0x5a51,0x9a89,0xf926,0xe54a,0xd873,0x8be2,0xea1a,0x776e,0x775e,0x1cef,0x7970,0xe620,0x5d3f,0xf379,0xc4b3,0x114e,0xfb6b,0x5428,0xa6d3,0xafae,0xbfc7,0x8605,0x34fc,0xca98,0xe23e,0x0284,0x49d0,0xd475,0x098a,0x4511,0x4c5a,0xf2dd,0x2e12,0xdc4c,0xe72c,0x302b,0x7bc4,0x6ecf,0x22a1,0x7510,0x492a,0x23eb,0x2c54,0x4260}, new char[]{0xe663,0xe74d,0xf2a9,0x238e,0x4207,0x7795,0x7c56,0xfb12,0xb1ed,0x055b,0xe52e,0x2c74,0xd81e,0x1c9a,0x4562,0xcf59,0x49f0,0x5a33,0x304e,0x6eef,0xcf3d,0x02f4,0x2e62,0xab5f,0xf30b,0x09a7,0x5d5c,0x770f,0x4959,0x22c4,0x34dc,0x5ea5,0x8c01,0xcaec,0xc4c7,0x9aec,0xa6a1,0xafdd,0x5404,0x097d,0x8b86,0xea73,0xbfa0,0xd41c,0x4c2e,0x7563,0xdc6c,0x7ba5,0x1120,0xf942,0x11da,0x7779,0x865a,0x75f1,0x794a,0xe21e}).toString();
            static final String L20 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2a6b,0x5898,0x09dc,0xfecc,0x515c,0x2716,0xec69,0x9b0f,0x11f8,0x0a73,0x6393,0xb967,0x597a,0x2f36,0x6f9f,0x4135,0x7335,0x79a3,0x01f9,0xc161,0xfc84,0x252b,0xce2f}, new char[]{0x2752,0xb912,0x590a,0x1194,0xfced,0x2f55,0x513d,0xec1d,0x254e,0x01d9,0x58fb,0xce4e,0x9b7b,0x79c6,0x63f4,0x09b3,0x0a01,0xfeb5,0x7315,0x2a02,0xc105,0x410f,0x6fbf}).toString();
            static final String L21 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5bd1,0xdb51,0x52bc,0xd678,0x57b2,0x93cf,0x5154,0x5f9a,0x1921,0x538f,0x9dd0}, new char[]{0x1945,0xdb38,0x5ba2,0x5124,0xd614,0x53ee,0x9da9,0x57fc,0x52dd,0x93a2,0x5fff}).toString();
            static final String L22 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xdfef,0x9a66,0xba84,0xff8b}, new char[]{0x9a0f,0xffe8,0xbaeb,0xdf81}).plainString();
    }
}
