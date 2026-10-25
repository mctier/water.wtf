package com.utilitymod.anubis.module.impl.donutmacros;

import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.util.Identifier;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.Registries;
import net.minecraft.component.type.ItemEnchantmentsComponent;

record Product(
   Item item,
   String id,
   String search,
   String displayName,
   long minPrice,
   List<Product.Enchant> enchants,
   boolean restock,
   int orderAmount,
   boolean beatTopOrder,
   long fixedPrice,
   long bidIncrement,
   long maxPrice,
   Predicate<ItemStack> filter
) {
   Product(
      Item item,
      String id,
      String search,
      String displayName,
      long minPrice,
      List<Product.Enchant> enchants,
      boolean restock,
      int orderAmount,
      boolean beatTopOrder,
      long fixedPrice,
      long bidIncrement,
      long maxPrice
   ) {
      this(item, id, search, displayName, minPrice, enchants, restock, orderAmount, beatTopOrder, fixedPrice, bidIncrement, maxPrice, null);
   }

   static Item resolveItem(String raw) {
      String text = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
      if (text.isEmpty()) {
         return null;
      }

      if (!text.contains(LT2.L1)) {
         text = LT.L1 + text;
      }

      Identifier id = Identifier.tryParse(text);
      if (id != null && Registries.ITEM.containsId(id)) {
         Item item = (Item)Registries.ITEM.get(id);
         return item == Items.AIR ? null : item;
      } else {
         return null;
      }
   }

   static String searchText(Item item) {
      return Registries.ITEM.getId(item).getPath().replace('_', ' ');
   }

   static List<Product.Enchant> parseEnchants(String raw) {
      List<Product.Enchant> out = new ArrayList<>();
      if (raw == null) {
         return out;
      }

      for (String part : raw.split(LT2.L2)) {
         String text = part.trim();
         if (!text.isEmpty()) {
            int level = 1;
            int space = text.lastIndexOf(32);
            if (space > 0) {
               String last = text.substring(space + 1).trim();
               int parsed = parseLevel(last);
               if (parsed > 0) {
                  level = parsed;
                  text = text.substring(0, space).trim();
               }
            }

            String key = ServerText.key(text);
            if (!key.isEmpty()) {
               out.add(new Product.Enchant(key, text, level));
            }
         }
      }

      return out;
   }

   private static int parseLevel(String raw) {
      try {
         int value = Integer.parseInt(raw);
         return value >= 1 && value <= 10 ? value : 0;
      } catch (NumberFormatException e) {
         return ServerText.roman(raw);
      }
   }

   boolean matches(ItemStack stack) {
      if (!stack.isEmpty() && stack.isOf(this.item)) {
         if (this.filter != null) {
            return this.filter.test(stack);
         }

         if (this.enchants.isEmpty()) {
            return true;
         }

         ItemEnchantmentsComponent present = EnchantmentHelper.getEnchantments(stack);

         for (Product.Enchant wanted : this.enchants) {
            if (levelOf(present, wanted.key()) < wanted.level()) {
               return false;
            }
         }

         return true;
      } else {
         return false;
      }
   }

   static boolean enchantsMatch(ItemStack stack, List<Product.Enchant> wanted, boolean exact) {
      ItemEnchantmentsComponent present = EnchantmentHelper.getEnchantments(stack);
      if (exact && present.getSize() != wanted.size()) {
         return false;
      }

      for (Product.Enchant enchant : wanted) {
         int level = levelOf(present, enchant.key());
         if (exact ? level != enchant.level() : level < enchant.level()) {
            return false;
         }
      }

      return true;
   }

   private static int levelOf(ItemEnchantmentsComponent present, String key) {
      for (Entry<RegistryEntry<Enchantment>> entry : present.getEnchantmentEntries()) {
         String path = ((RegistryEntry<?>)entry.getKey()).getKey().map(k -> ((net.minecraft.registry.RegistryKey<?>) k).getValue().getPath()).orElse("");
         if (ServerText.key(path).equals(key)) {
            return entry.getIntValue();
         }
      }

      return 0;
   }

   boolean nameMatches(String label, boolean loose) {
      String key = ServerText.key(label);
      String byId = ServerText.key(Registries.ITEM.getId(this.item).getPath());
      String byName = ServerText.key(this.displayName);
      return !key.equals(byId) && !key.equals(byName) ? loose && (key.endsWith(byId) || !byName.isEmpty() && key.endsWith(byName)) : true;
   }

   String describe() {
      StringBuilder text = new StringBuilder(this.displayName);
      if (!this.enchants.isEmpty()) {
         List<String> names = new ArrayList<>();

         for (Product.Enchant enchant : this.enchants) {
            names.add(enchant.label());
         }

         text.append(LT2.L3).append(String.join(LT2.L4, names)).append(')');
      }

      return text.toString();
   }

   record Enchant(String key, String name, int level) {
      String label() {
         return this.name + (this.level > 1 ? " " + this.level : "");
      }
   }


    // cloak literals - runtime PUA carriers (cloak) or functional plain (LT)
    private static final class LT {
            static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb15b,0x182b,0xce2b,0x065a,0xa9c1,0x9880,0x14e4,0xc75b,0x73db,0xd734}, new char[]{0x98ed,0xce42,0x1845,0xc73e,0xb138,0x1496,0x063b,0x73bd,0xd740,0xa9fb}).plainString();
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT2 {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x42eb}, new char[]{0x42d1,0x22c0,0x6352,0x219b}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6a47,0x852a,0x5dc8,0xf832}, new char[]{0x8571,0x5de4,0x6a7c,0xf86f}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5015,0x4013}, new char[]{0x5035,0x403b,0x39cd,0x7173}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7f4e,0x860f}, new char[]{0x8623,0x7f6e,0xd11f,0xe777}).plainString();
    }
}
