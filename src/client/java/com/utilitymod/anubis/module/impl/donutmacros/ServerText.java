package com.utilitymod.anubis.module.impl.donutmacros;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.DataComponentTypes;

final class ServerText {
   private static final Pattern FORMATTING = Pattern.compile(LT2.L1);
   private static final Pattern MONEY_EXACT = Pattern.compile(LT.L1);
   private static final Pattern MONEY_ANY = Pattern.compile(LT.L2);
   private static final String SMALL_CAPS = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘʀѕꜱᴛᴜᴠᴡʏᴢ";
   private static final String SMALL_CAPS_PLAIN = LT.L3;
   private static final String[] ROMAN = new String[]{LT2.L2, LT.L4, LT.L5, LT.L6, LT2.L3, LT.L7, LT.L8, LT.L9, LT.L10, LT2.L4};

   private ServerText() {
   }

   static String normalize(String raw) {
      if (raw == null) {
         return "";
      }

      String text = FORMATTING.matcher(raw).replaceAll("");
      StringBuilder out = new StringBuilder(text.length());

      for (int i = 0; i < text.length(); i++) {
         char c = text.charAt(i);
         int small = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘʀѕꜱᴛᴜᴠᴡʏᴢ".indexOf(c);
         if (small >= 0) {
            out.append(LT.L3.charAt(small));
         } else if (c != 10140 && c != 8594) {
            out.append(c);
         } else {
            out.append('-');
         }
      }

      return out.toString().toLowerCase(Locale.ROOT).replaceAll(LT2.L5, " ").trim();
   }

   static String normalize(Text component) {
      return component == null ? "" : normalize(component.getString());
   }

   static String key(String raw) {
      String text = normalize(raw);
      StringBuilder out = new StringBuilder(text.length());

      for (int i = 0; i < text.length(); i++) {
         char c = text.charAt(i);
         if (c >= 'a' && c <= 'z' || c >= '0' && c <= '9') {
            out.append(c);
         }
      }

      return out.toString();
   }

   static long parseMoney(String raw) {
      if (raw == null) {
         return -1L;
      }

      Matcher matcher = MONEY_EXACT.matcher(raw.trim().toLowerCase(Locale.ROOT));
      return !matcher.matches() ? -1L : Math.round(scale(matcher.group(1), matcher.group(2)));
   }

   static List<Double> moneyAll(String raw) {
      List<Double> out = new ArrayList<>();
      if (raw == null) {
         return out;
      }

      Matcher matcher = MONEY_ANY.matcher(normalize(raw));

      while (matcher.find()) {
         double value = scale(matcher.group(1), matcher.group(2));
         if (value > 0.0) {
            out.add(value);
         }
      }

      return out;
   }

   static String moneyToken(String raw) {
      Matcher matcher = MONEY_ANY.matcher(normalize(raw));
      if (!matcher.find()) {
         return null;
      }

      String suffix = matcher.group(2);
      return matcher.group(1).replace(LT2.L6, "") + (suffix == null ? "" : suffix);
   }

   static double displayStep(String raw) {
      Matcher matcher = MONEY_ANY.matcher(normalize(raw));
      if (!matcher.find()) {
         return 1.0;
      }

      String number = matcher.group(1);
      int dot = number.indexOf(46);
      int decimals = dot < 0 ? 0 : number.length() - dot - 1;
      return Math.max(1.0, scale(LT2.L7, matcher.group(2)) / Math.pow(10.0, decimals));
   }

   private static double scale(String number, String suffix) {
      double value;
      try {
         value = Double.parseDouble(number.replace(LT2.L6, ""));
      } catch (NumberFormatException e) {
         return -1.0;
      }

      if (suffix == null) {
         return value;
      }

      return switch (suffix) {
         case "k" -> value * 1000.0;
         case "m" -> value * 1000000.0;
         case "b" -> value * 1.0E9;
         default -> value;
      };
   }

   static String exactMoney(long value) {
      if (value >= 1000000000L && value % 1000000000L == 0L) {
         return value / 1000000000L + LT2.L8;
      } else if (value >= 1000000L && value % 1000000L == 0L) {
         return value / 1000000L + LT2.L9;
      } else if (value >= 1000000L && value % 100000L == 0L) {
         return value / 1000000L + LT2.L10 + value % 1000000L / 100000L + LT2.L9;
      } else {
         return value >= 1000L && value % 1000L == 0L ? value / 1000L + LT2.L11 : Long.toString(value);
      }
   }

   static String shortMoney(double value) {
      if (value >= 1.0E9) {
         return trim(value / 1.0E9) + LT2.L8;
      } else if (value >= 1000000.0) {
         return trim(value / 1000000.0) + LT2.L9;
      } else {
         return value >= 1000.0 ? trim(value / 1000.0) + LT2.L11 : Long.toString(Math.round(value));
      }
   }

   private static String trim(double value) {
      String text = String.format(Locale.ROOT, LT2.L12, value);
      return text.contains(LT2.L10) ? text.replaceAll(LT2.L13, "").replaceAll(LT2.L14, "") : text;
   }

   static int roman(String raw) {
      String text = normalize(raw);

      for (int i = 0; i < ROMAN.length; i++) {
         if (ROMAN[i].equals(text)) {
            return i + 1;
         }
      }

      return 0;
   }

   static String withoutRoman(String raw) {
      String text = normalize(raw);
      int space = text.lastIndexOf(32);
      return space > 0 && roman(text.substring(space + 1)) > 0 ? text.substring(0, space) : text;
   }

   static List<String> lore(ItemStack stack) {
      List<String> out = new ArrayList<>();

      for (Text line : ((LoreComponent)stack.getOrDefault(DataComponentTypes.LORE, LoreComponent.DEFAULT)).lines()) {
         out.add(line.getString());
      }

      return out;
   }

   static double listingPrice(ItemStack stack) {
      List<String> lore = lore(stack);
      double best = -1.0;

      for (String line : lore) {
         String clean = normalize(line);
         if (clean.contains(LT.L11) || clean.contains(LT.L12) || clean.contains(LT.L13)) {
            for (double value : moneyAll(line)) {
               if (value >= 100.0 && (best < 0.0 || value < best)) {
                  best = value;
               }
            }
         }
      }

      if (best > 0.0) {
         return best;
      }

      for (String line : lore) {
         if (line.contains(LT2.L15)) {
            for (double value : moneyAll(line)) {
               if (value >= 100.0 && (best < 0.0 || value < best)) {
                  best = value;
               }
            }
         }
      }

      return best;
   }

   static double orderPrice(ItemStack stack) {
      List<String> lore = lore(stack);
      double best = -1.0;

      for (String line : lore) {
         String clean = normalize(line);
         if (line.contains(LT2.L15) && (clean.contains(LT.L11) || clean.contains(LT.L14) || clean.contains(LT.L15) || clean.contains(LT.L16))) {
            for (double value : moneyAll(line)) {
               if (value > 0.0 && (best < 0.0 || value < best)) {
                  best = value;
               }
            }
         }
      }

      if (best > 0.0) {
         return best;
      }

      for (String line : lore) {
         if (line.contains(LT2.L15)) {
            List<Double> values = moneyAll(line);
            if (!values.isEmpty()) {
               return values.get(0);
            }
         }
      }

      return -1.0;
   }


    // cloak literals - runtime PUA carriers (cloak) or functional plain (LT)
    private static final class LT {
            static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2caf,0x63ff,0xe6cf,0x0670,0x097b,0xdac2,0xfacc,0x289a,0x68a0,0xb447,0x06b7,0x8ab7,0x8f54,0xd5b6,0xdf84,0x4790,0xac94,0xfcd2,0x812b,0x5c9a,0x915a,0x5ea7,0x141f,0x59fa,0xa4c1,0x53d8,0xe542,0x0e97,0x97c2,0x2940,0x9a22,0x9305,0x2789,0x09df,0x09f0,0xf842,0x06ec,0x5698,0xd339,0x9bc1,0x75bf,0xe3e8,0x05a5,0xd0fb,0x9771,0x6f2c}, new char[]{0x8ae9,0xda9e,0x56bc,0x8f6b,0x75e3,0xd5c5,0x5e8d,0x27a1,0x9b9a,0xd0cb,0x8106,0x09c9,0x9107,0x6f77,0x142f,0x09f2,0x63c6,0x47bc,0x291d,0x065a,0x058d,0x9a1d,0x06d6,0xe693,0xe3c6,0xa49a,0x9335,0x5cb7,0x068e,0x0926,0x975a,0x6889,0xdfbb,0xe56b,0xfc8e,0x2cdc,0xd313,0xfae4,0x0ecc,0x28f1,0x53b5,0x5998,0xacc9,0x97eb,0xb478,0xf866}).plainString();
            static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x617a,0x62b6,0x2d61,0x52e0,0xe9d3,0x1ec8,0x459c,0xed42,0x277e,0xf386,0xb3d6,0xded5,0x05e4,0xab3d,0x44b7,0xc396,0x86fb,0x3f8b,0x8132,0x3fd2,0x2b9c,0x71ee,0xa383,0x104c,0xc6ae,0xa426,0x4b41,0xd173,0x942a,0xb922,0x074e,0xc3de,0x87f0,0x68c1,0xde1c,0x78a0,0x3c07,0x15a8,0x007c,0x100a,0xe9e9,0x39e8,0x9547,0x414c,0x58c7,0x5292,0xa6f2,0x1d64,0xbe15,0x7847,0x30d9,0x46bd,0x68e3,0x8a46,0xe3b5,0xcd1d,0x3d0c,0x8bb7,0x0895}, new char[]{0xbe49,0x68c7,0x30e6,0xe9b2,0x3c27,0xde40,0x0590,0xe3e8,0x4166,0x52c8,0xa3d8,0xb3e6,0xcd30,0x2ba5,0x8a1b,0x86a0,0x39d8,0xd15e,0xa41f,0x2d4d,0x781a,0x788a,0x3d24,0x3fb4,0x1d5e,0xc382,0xc3b8,0x15f3,0x468d,0x1ee5,0xab04,0xa6af,0x3ff9,0x2757,0x4b7e,0x8b9e,0x689a,0x005c,0xc6f2,0x719a,0xe98e,0x58ed,0x1022,0x44ec,0x8159,0x0723,0x6118,0x951a,0x52bb,0x1073,0x87d8,0x08aa,0xb903,0x62ed,0xdeb4,0x9407,0xed38,0xf3db,0x45b5}).plainString();
            static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa63f,0x2c2b,0xa869,0x1052,0x6efe,0x66d1,0xbcca,0x6c4e,0x78d7,0xd75c,0x93b1,0x653d,0x365f,0x9764,0x9cef,0xdf61,0x56d2,0x433f,0x3205,0xc4fb,0x282e,0x79e2,0x5294,0x79c5,0xa211}, new char[]{0xa270,0x9706,0x655e,0x9c8b,0x7987,0xbcac,0x56b5,0x6e96,0x3636,0xd736,0xa802,0x93dd,0xdf0c,0x79ab,0x103d,0xa64f,0x78a5,0x52e7,0x3276,0x6c3a,0x285b,0x4349,0xc48c,0x66a8,0x2c51}).plainString();
            static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x08b5,0x2852}, new char[]{0x08dc,0x283b,0x5fad,0xabbd}).plainString();
            static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2bfc,0x733b,0x955a}, new char[]{0x9533,0x7352,0x2b95,0xbe5c}).plainString();
            static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8b40,0x87c1}, new char[]{0x87a8,0x8b36,0xb7e0,0x9d5e}).plainString();
            static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xaad0,0x7f9b}, new char[]{0x7fed,0xaab9,0xae7f,0x005b}).plainString();
            static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xfc5d,0x981c,0xa322}, new char[]{0xa354,0xfc34,0x9875,0x2e2e}).plainString();
            static final String L9 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc0ec,0xf2de,0xda92,0x4527}, new char[]{0xdae4,0xf2b7,0x454e,0xc085}).plainString();
            static final String L10 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe652,0x4330}, new char[]{0xe63b,0x4348,0x6e59,0x6559}).plainString();
            static final String L11 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xcd13,0x5688,0x239d,0x0c54,0xc1fb}, new char[]{0x23ed,0x0c26,0x56e1,0xcd70,0xc19e}).plainString();
            static final String L12 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1a61,0x9246,0x1dbc,0xa0ef}, new char[]{0x9225,0x1a0e,0x1dcf,0xa09b}).plainString();
            static final String L13 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x86fc,0xdd12,0xcf6f}, new char[]{0xcf0d,0xdd67,0x8685,0x5e60}).plainString();
            static final String L14 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf316,0xa6d2,0xafc6,0x7440}, new char[]{0xafa3,0x7421,0xf375,0xa6ba}).plainString();
            static final String L15 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x29cc,0x3d55,0xc765,0x9877,0xf6b5,0xa8f2,0x8ab1,0xe7d5}, new char[]{0x29bc,0x9812,0x8ac3,0xa8d2,0xf6dc,0xc711,0x3d30,0xe7b8}).plainString();
            static final String L16 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xcee2,0x5741,0xfff8}, new char[]{0x5731,0xff99,0xce9b,0x8ad4}).plainString();
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT2 {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9c4d,0x010e}, new char[]{0x9cea,0x0120,0xc68d,0x6919}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xbe62}, new char[]{0xbe0b,0x8bb6,0x6342,0xe16a}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1dd9}, new char[]{0x1daf,0xedbc,0x9939,0xd027}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4006}, new char[]{0x407e,0x3b1f,0xaf1f,0xb945}).plainString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x48b0,0xbc7f,0x3c02}, new char[]{0xbc23,0x48c3,0x3c29,0x6a6b}).plainString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2011}, new char[]{0x203d,0xd26b,0xbd4e,0x53f6}).plainString();
        static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2917}, new char[]{0x2926,0x335a,0xbd2a,0x88cc}).plainString();
        static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xeeef}, new char[]{0xee8d,0xccd6,0xb210,0x6710}).plainString();
        static final String L9 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0c52}, new char[]{0x0c3f,0x6ac4,0x5ad5,0x7284}).plainString();
        static final String L10 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xaff5}, new char[]{0xafdb,0x2b4f,0x6655,0xc4ca}).plainString();
        static final String L11 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x19e2}, new char[]{0x1989,0x1282,0x176e,0xd75f}).plainString();
        static final String L12 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8ac9,0x50db,0x1ee4,0x8a44}, new char[]{0x8a61,0x8ae7,0x50e9,0x1e82}).plainString();
        static final String L13 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1a10,0xc530,0x7f19}, new char[]{0x7f29,0x1a3b,0xc514,0xfdc1}).plainString();
        static final String L14 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc9a4,0xf29d,0xe311}, new char[]{0xc9f8,0xe33f,0xf2b9,0x61ca}).plainString();
        static final String L15 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9714}, new char[]{0x9730,0xb990,0x5ab4,0x7fdf}).plainString();
    }
}
