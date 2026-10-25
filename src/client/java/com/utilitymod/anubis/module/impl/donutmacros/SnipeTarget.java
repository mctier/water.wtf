package com.utilitymod.anubis.module.impl.donutmacros;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.utilitymod.anubis.module.setting.StringSetting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;

final class SnipeTarget {
   private static final Pattern DURATION = Pattern.compile(LT.L1);
   final Item item;
   final boolean shard;
   final SnipeTarget.Preset preset;
   final String displayName;
   final String search;
   final List<Product.Enchant> enchants;
   final boolean exactEnchants;
   final long maxPrice;
   final int minMinutes;

   private SnipeTarget(
      Item item,
      boolean shard,
      SnipeTarget.Preset preset,
      String displayName,
      String search,
      List<Product.Enchant> enchants,
      boolean exactEnchants,
      long maxPrice,
      int minMinutes
   ) {
      this.item = item;
      this.shard = shard;
      this.preset = preset;
      this.displayName = displayName;
      this.search = search;
      this.enchants = enchants;
      this.exactEnchants = exactEnchants;
      this.maxPrice = maxPrice;
      this.minMinutes = minMinutes;
   }

   static SnipeTarget parse(String rawItem, String rawEnchants, boolean exact, long maxPrice, int minMinutes) {
      List<Product.Enchant> enchants = Product.parseEnchants(rawEnchants);
      SnipeTarget.Preset preset = SnipeTarget.Preset.of(rawItem);
      if (preset != null) {
         return new SnipeTarget(preset.item, true, preset, preset.label, preset.search, enchants, exact, maxPrice, minMinutes);
      }

      String text = rawItem == null ? "" : rawItem.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
      if (text.startsWith(LT.L2)) {
         text = text.substring(LT.L2.length());
      }

      boolean shard = text.startsWith(LT.L3);
      Item item = Product.resolveItem(shard ? LT.L4 + text.substring(LT.L3.length()) : text);
      if (item == null) {
         return null;
      }

      String name = shard ? LT.L5 + capitalize(text.substring(LT.L3.length())) : new ItemStack(item).getName().getString();
      return new SnipeTarget(item, shard, null, name, Product.searchText(item), enchants, exact, maxPrice, shard ? minMinutes : 0);
   }

   private static String capitalize(String path) {
      StringBuilder out = new StringBuilder();

      for (String word : path.split(LT2.L1)) {
         if (!word.isEmpty()) {
            if (out.length() > 0) {
               out.append(' ');
            }

            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
         }
      }

      return out.toString();
   }

   /** PUA mirror so a plaintext needle counterpart never enters scanned heap pages. */
   private static String masked(String in) {
      char[] out = new char[in.length()];
      for (int i = 0; i < in.length(); i++) {
         char c = in.charAt(i);
         out[i] = c < 0x100 ? (char) (0xE100 | c) : c;
      }
      return new String(out);
   }

   boolean sameKind(ItemStack stack) {
      if (!stack.isEmpty() && stack.isOf(this.item)) {
         String name = ServerText.key(stack.getName().getString());
         if (this.preset != null) {
            if (!name.contains(this.preset.keyword)) {
               return false;
            }

            if (!this.preset.exclude.isEmpty() && name.contains(this.preset.exclude)) {
               return false;
            }
         } else if (name.contains(LT.L6) != this.shard) {
            return false;
         }

         return this.enchants.isEmpty() || Product.enchantsMatch(stack, this.enchants, this.exactEnchants);
      } else {
         return false;
      }
   }

   boolean matches(ItemStack stack) {
      if (!this.sameKind(stack)) {
         return false;
      } else {
         return this.preset != null ? minutesLeft(stack) >= Math.max(0, this.minMinutes) : this.minMinutes <= 0 || minutesLeft(stack) >= this.minMinutes;
      }
   }

   static int minutesLeft(ItemStack stack) {
      for (String line : ServerText.lore(stack)) {
         String clean = ServerText.normalize(line);
         String sweep = masked(clean);
         if (sweep.contains(LT.L19) || sweep.contains(LT.L7)) {
            Matcher matcher = DURATION.matcher(clean);
            int minutes = 0;
            boolean found = false;

            while (matcher.find()) {
               found = true;
               int value = Integer.parseInt(matcher.group(1));
               switch (matcher.group(2)) {
                  case "d":
                     minutes += value * 1440;
                     break;
                  case "h":
                     minutes += value * 60;
                     break;
                  case "m":
                     minutes += value;
               }
            }

            if (found) {
               return minutes;
            }
         }
      }

      return -1;
   }

   Product product(long sellPrice) {
      return new Product(
         this.item,
         Registries.ITEM.getId(this.item).getPath(),
         this.search,
         this.displayName,
         sellPrice,
         this.enchants,
         false,
         1,
         false,
         1L,
         1L,
         0L,
         this::sameKind
      );
   }

   String describe() {
      StringBuilder text = new StringBuilder(this.displayName);
      if (!this.enchants.isEmpty()) {
         StringBuilder names = new StringBuilder();

         for (Product.Enchant enchant : this.enchants) {
            if (names.length() > 0) {
               names.append(LT2.L2);
            }

            names.append(enchant.label());
         }

         text.append(LT2.L3).append(this.exactEnchants ? LT.L8 : LT.L9).append(names).append(')');
      }

      return text.toString();
   }

   enum Preset {
      SELL_AXE(LT.L10, LT.L11, Items.NETHERITE_AXE, LT.L12, LT.L13, ""),
      DRILL(LT.L14, LT.L15, Items.NETHERITE_PICKAXE, LT.L14, LT.L14, ""),
      SHARD_AXE(LT.L16, LT.L17, Items.NETHERITE_AXE, LT.L18, LT.L6, LT.L13);

      final String value;
      final String label;
      final Item item;
      final String search;
      final String keyword;
      final String exclude;

      Preset(String value, String label, Item item, String search, String keyword, String exclude) {
         this.value = value;
         this.label = label;
         this.item = item;
         this.search = search;
         this.keyword = keyword;
         this.exclude = exclude;
      }

      static SnipeTarget.Preset of(String raw) {
         String text = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_');

         for (SnipeTarget.Preset preset : values()) {
            if (preset.value.equals(text)) {
               return preset;
            }
         }

         return null;
      }

      static List<StringSetting.Preset> choices() {
         List<StringSetting.Preset> out = new ArrayList<>();

         for (SnipeTarget.Preset preset : values()) {
            out.add(new StringSetting.Preset(preset.value, preset.label, Registries.ITEM.getId(preset.item).getPath(), true));
         }

         return out;
      }
   }


    // cloak literals - runtime PUA carriers (cloak) or functional plain (LT)
    private static final class LT {
            static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x886d,0x370a,0xe63c,0x8e3c,0x5dbd,0x908a,0x6c5d,0xcc5a,0x1cc1,0xad78,0x75ec,0x4e97,0x4a53,0xb667,0x3c01,0x5f9d}, new char[]{0x5fb5,0x5de1,0x3c65,0x4a78,0x90a3,0x6c01,0xcc29,0x8e16,0xb64f,0x4ecc,0x8809,0xe654,0xad15,0x1cb2,0x75b1,0x3723}).plainString();
            static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3ba3,0xd77e,0x6810,0x487a,0xdf13,0xb865,0x8ae5,0x6c9e,0x9aa8,0x6dee}, new char[]{0x4817,0x3bca,0x8a8b,0xb800,0x6d8d,0xdf61,0x9ac9,0x6cf8,0x6864,0xd744}).plainString();
            static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2b81,0x5352,0x0f06,0xe29e,0x2b05,0xe303}, new char[]{0x0f75,0x2b6d,0xe362,0x2bf3,0xe2fa,0x530d}).plainString();
            static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf7f4,0xc276,0xe401,0x6250,0xca67,0x8945,0x3730,0xe9f5,0xc062,0x3601}, new char[]{0xf79a,0xc213,0x8931,0x6238,0x3664,0xe473,0x3759,0xca13,0xc007,0xe9aa}).plainString();
            static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe0f1,0xd01f,0xbcb4,0xb21c,0x7c9d,0xf5c2}, new char[]{0xbce7,0x7cf5,0xd07e,0xb26e,0xf5a6,0xe0d1}).toString();
            static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd292,0x692d,0x254c,0x9b9d,0xa13a}, new char[]{0x9bee,0x2524,0x694c,0xd2e0,0xa15e}).plainString();
            static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x32f9,0x260f,0xa84c,0xc49b,0x984f,0x4d01}, new char[]{0x329c,0x4d79,0xa83c,0x2666,0x983d,0xc4fe}).toString();
            static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7ea6,0x6b42,0x1664,0xf672,0x406a,0xffc1,0xadef,0x644a}, new char[]{0x7ec3,0x6432,0xffa0,0xf611,0x401e,0x1608,0x6b3b,0xadcf}).plainString();
            static final String L9 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe1f0,0xdf09,0xb0c9,0x1d36,0xff0b,0x50e4,0x6b06,0x743d,0xb8d2}, new char[]{0xdf68,0x5090,0xb8f2,0x7451,0x6b63,0xe191,0xff78,0xb0bd,0x1d16}).plainString();
            static final String L10 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0f37,0x6544,0x2084,0x9bb8,0x615d,0xb157,0x1472,0xf20e}, new char[]{0x612e,0x9bdd,0x0f5b,0x6528,0x20db,0x1413,0xf276,0xb132}).plainString();
            static final String L11 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x336a,0x6728,0x3fc0,0xdea5,0xabc6,0x8465,0x37c7,0xf5b1}, new char[]{0x3794,0x674d,0x3306,0xabaa,0x8445,0x3f81,0xdedd,0xf5d4}).toString();
            static final String L12 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1f10,0x6044,0x8a89,0xf7cb,0x3c61,0x3d98,0xad69,0x74e6}, new char[]{0xf7b8,0x1f75,0x6028,0x3c0d,0x74c6,0xad08,0x3de0,0x8aec}).plainString();
            static final String L13 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf20a,0x52fc,0x7e4b,0x83bf}, new char[]{0xf279,0x7e2e,0x5290,0x83d3}).plainString();
            static final String L14 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf335,0xa525,0x52ed,0xd24a,0x5beb}, new char[]{0x5b8f,0xf347,0x5284,0xd226,0xa549}).plainString();
            static final String L15 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x34cc,0xa45b,0xd2e4,0x01d8,0x36c3}, new char[]{0x3488,0xd296,0xa432,0x36af,0x01b4}).toString();
            static final String L16 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb6af,0x19e6,0x99bd,0x580a,0x44b9,0x15c5,0x5adf,0x6cf2,0x8fa3}, new char[]{0x99ce,0x198e,0x15a4,0x6c80,0x8fc7,0x5855,0x5abe,0x44c1,0xb6ca}).plainString();
            static final String L17 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0615,0x8323,0xa76f,0x46f7,0xaade,0x29f8,0xd81a,0xa771,0x906e}, new char[]{0xa722,0xaab6,0xa70e,0x4685,0x900a,0x29d8,0xd85b,0x066d,0x8346}).toString();
            static final String L18 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd975,0xdddb,0x5e44,0xf63e,0xe0c2,0xfb39,0xb225,0x3ef0,0xaf3c}, new char[]{0xaf4f,0xe0aa,0xd914,0xf64c,0xb241,0xfb19,0xddba,0x5e3c,0x3e95}).plainString();
            static final String L19 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2274,0x9631,0x11e5,0xeb82,0x82de,0x0b39,0xa8ae,0xbc0f,0x1c86,0xd109,0x379e,0x6542,0xb16b}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536}).toString();
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT2 {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4c93}, new char[]{0x4ccc,0x95dc,0x0d38,0x2757}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5202,0x2378}, new char[]{0x2354,0x5222,0x39b4,0xb868}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa811,0x5b59}, new char[]{0x5b79,0xa839,0x3513,0x7152}).plainString();
    }
}
