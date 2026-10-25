package com.utilitymod.anubis.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.util.Identifier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.component.DataComponentTypes;

public final class ItemEnchants {
   private static final String[] ROMAN = new String[]{LT2.L1, LT.L1, LT.L2, LT.L3, LT2.L2, LT.L4, LT.L5, LT.L6, LT.L7, LT2.L3};
   private static DynamicRegistryManager cachedFor;
   private static List<RegistryEntry<Enchantment>> cached = List.of();

   private ItemEnchants() {
   }

   public static Item resolve(String raw) {
      String text = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
      if (text.isEmpty()) {
         return null;
      }

      if (text.indexOf(58) < 0) {
         text = LT.L8 + text;
      }

      Identifier id = Identifier.tryParse(text);
      if (id != null && Registries.ITEM.containsId(id)) {
         Item item = (Item)Registries.ITEM.get(id);
         return item == Items.AIR ? null : item;
      } else {
         return null;
      }
   }

   public static String stored(Item item) {
      Identifier key = Registries.ITEM.getId(item);
      return LT.L9.equals(key.getNamespace()) ? key.getPath() : key.toString();
   }

   public static boolean supports(Item item) {
      if (item == null) {
         return false;
      }

      if (item == Items.ENCHANTED_BOOK) {
         return true;
      }

      List<RegistryEntry<Enchantment>> all = all();
      ItemStack stack = item.getDefaultStack();
      if (all.isEmpty()) {
         return stack.contains(DataComponentTypes.ENCHANTABLE);
      }

      for (RegistryEntry<Enchantment> holder : all) {
         if (((Enchantment)holder.value()).isSupportedItem(stack)) {
            return true;
         }
      }

      return false;
   }

   public static List<RegistryEntry<Enchantment>> applicable(Item item) {
      List<RegistryEntry<Enchantment>> out = new ArrayList<>();
      if (item == null) {
         return out;
      }

      ItemStack stack = item.getDefaultStack();
      boolean book = item == Items.ENCHANTED_BOOK;

      for (RegistryEntry<Enchantment> holder : all()) {
         if (book || ((Enchantment)holder.value()).isSupportedItem(stack)) {
            out.add(holder);
         }
      }

      out.sort(Comparator.comparing(holderx -> ((Enchantment)holderx.value()).description().getString(), String.CASE_INSENSITIVE_ORDER));
      return out;
   }

   public static List<RegistryEntry<Enchantment>> all() {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.world == null) {
         return List.of();
      }

      DynamicRegistryManager access = mc.world.getRegistryManager();
      if (access != cachedFor) {
         List<RegistryEntry<Enchantment>> found = new ArrayList<>();
         access.getOrThrow(RegistryKeys.ENCHANTMENT).streamEntries().forEach(found::add);
         cached = List.copyOf(found);
         cachedFor = access;
      }

      return cached;
   }

   public static String path(RegistryEntry<Enchantment> holder) {
      return holder.getKey().map(key -> key.getValue().getPath()).orElse("");
   }

   public static String key(String raw) {
      String text = raw == null ? "" : raw.toLowerCase(Locale.ROOT);
      StringBuilder out = new StringBuilder(text.length());

      for (int i = 0; i < text.length(); i++) {
         char c = text.charAt(i);
         if (c >= 'a' && c <= 'z' || c >= '0' && c <= '9') {
            out.append(c);
         }
      }

      return out.toString();
   }

   public static Map<String, Integer> parse(String raw) {
      Map<String, Integer> out = new LinkedHashMap<>();
      if (raw == null) {
         return out;
      }

      for (String part : raw.split(LT2.L4)) {
         String text = part.trim();
         if (!text.isEmpty()) {
            int level = 1;
            int space = text.lastIndexOf(32);
            if (space > 0) {
               int parsed = level(text.substring(space + 1).trim());
               if (parsed > 0) {
                  level = parsed;
                  text = text.substring(0, space).trim();
               }
            }

            String key = key(text);
            if (!key.isEmpty()) {
               out.put(key, level);
            }
         }
      }

      return out;
   }

   private static int level(String raw) {
      try {
         int value = Integer.parseInt(raw);
         return value >= 1 && value <= 10 ? value : 0;
      } catch (NumberFormatException e) {
         String text = raw.toLowerCase(Locale.ROOT);

         for (int i = 0; i < ROMAN.length; i++) {
            if (ROMAN[i].equals(text)) {
               return i + 1;
            }
         }

         return 0;
      }
   }

   public static String roman(int level) {
      return level >= 1 && level <= ROMAN.length ? ROMAN[level - 1].toUpperCase(Locale.ROOT) : Integer.toString(level);
   }


    // cloak literals - runtime PUA carriers (cloak) or functional plain (LT)
    private static final class LT {
            static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc56f,0x3029}, new char[]{0xc506,0x3040,0x9971,0xb733}).plainString();
            static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa43c,0x86a2,0xd0df}, new char[]{0xd0b6,0xa455,0x86cb,0xc640}).plainString();
            static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x372b,0xa6cb}, new char[]{0xa6a2,0x375d,0x8f2f,0x8177}).plainString();
            static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb295,0xafff}, new char[]{0xaf89,0xb2fc,0x67dd,0x2b8f}).plainString();
            static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x87f5,0x4b3d,0x26f6}, new char[]{0x8783,0x4b54,0x269f,0x8362}).plainString();
            static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd8c1,0x614a,0x2206,0x4bde}, new char[]{0x4ba8,0xd8a8,0x226f,0x6123}).plainString();
            static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x90c6,0x4d43}, new char[]{0x4d2a,0x90be,0xa401,0xb4c1}).plainString();
            static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa783,0xfb3a,0x8b2b,0x247e,0x1d56,0xf139,0x7890,0xdd08,0x992e,0x9569}, new char[]{0x2413,0x9500,0x78fe,0x8b4e,0x994d,0xdd7a,0xfb5b,0xa7e5,0xf14d,0x1d6c}).plainString();
            static final String L9 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x15df,0x7b36,0x3834,0xd82f,0xf953,0x9465,0x1056,0xa597,0xc1f8}, new char[]{0x7b5b,0xf93a,0x385a,0xa5f2,0x1035,0x9417,0x15be,0xc19e,0xd85b}).plainString();
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT2 {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb52b}, new char[]{0xb542,0x7603,0xbb31,0x8dd4}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x45e8}, new char[]{0x459e,0xb5af,0x7deb,0xa16b}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf3e3}, new char[]{0xf39b,0x2e53,0x6c08,0xe7e5}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xaaa6,0x37e5,0x1826,0x9c97}, new char[]{0x187d,0x37c9,0x9cac,0xaafb}).plainString();
    }
}
