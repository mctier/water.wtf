package com.utilitymod.anubis.module.impl.donutmacros;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.BiPredicate;
import com.utilitymod.anubis.chat.ModuleChat;
import com.utilitymod.anubis.module.ClientModule;
import com.utilitymod.anubis.module.setting.StringListSetting;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.registry.Registries;

final class SellRoutine {
   private static final int INVENTORY_SLOTS = 36;
   private static final int CONFIRM_SLOT = 53;
   private static final int MAX_CONFIRMS = 3;
   private static final int STRIKES_TO_SKIP = 2;
   private static final int MAX_RESENDS = 3;
   private static final long CHECK_EVERY_MS = 500L;
   private static final long COMMAND_COOLDOWN_MS = 4000L;
   private static final long OPEN_TIMEOUT_MS = 10000L;
   private static final long STRAY_MENU_MS = 5000L;
   private static final long FAIL_COOLDOWN_MS = 30000L;
   private static final long CONFIRM_COOLDOWN_MS = 900L;
   private static final long POST_CONFIRM_MS = 250L;
   private static final long SKIP_MS = 600000L;
   private final ClientModule owner;
   private final StringListSetting neverSell;
   private final BiPredicate<Integer, ItemStack> keep;
   private final Runnable onCommand;
   private SellRoutine.State state = SellRoutine.State.IDLE;
   private long nextCheckAt;
   private long lastCommandAt;
   private long deadline;
   private boolean rejected;
   private long retryAt;
   private int resends;
   private int menuId = -1;
   private int confirms;
   private final Map<Item, Integer> offered = new LinkedHashMap<>();
   private final Map<Item, Integer> strikes = new HashMap<>();
   private final Map<Item, Long> skipUntil = new HashMap<>();

   SellRoutine(ClientModule owner, StringListSetting neverSell, BiPredicate<Integer, ItemStack> keep, Runnable onCommand) {
      this.owner = owner;
      this.neverSell = neverSell;
      this.keep = keep;
      this.onCommand = onCommand;
   }

   void reset() {
      this.state = SellRoutine.State.IDLE;
      this.nextCheckAt = 0L;
      this.lastCommandAt = 0L;
      this.rejected = false;
      this.menuId = -1;
      this.offered.clear();
      this.strikes.clear();
      this.skipUntil.clear();
   }

   boolean active() {
      return this.state != SellRoutine.State.IDLE;
   }

   void commandRejected(long retryAt) {
      if (this.state == SellRoutine.State.OPENING) {
         this.rejected = true;
         this.retryAt = retryAt;
      }
   }

   void abort(MinecraftClient mc) {
      if (this.state != SellRoutine.State.IDLE) {
         ClientPlayerEntity player = mc.player;
         if (player != null && player.currentScreenHandler != player.playerScreenHandler && player.currentScreenHandler.syncId == this.menuId) {
            player.closeHandledScreen();
         }

         this.state = SellRoutine.State.IDLE;
         this.menuId = -1;
         this.offered.clear();
         this.nextCheckAt = System.currentTimeMillis() + 500L;
      }
   }

   boolean step(MinecraftClient mc, ClientPlayerEntity player, ClientPlayNetworkHandler connection, long now, boolean canCommand) {
      return switch (this.state) {
         case IDLE -> this.stepIdle(mc, player, connection, now, canCommand);
         case OPENING -> this.stepOpening(mc, player, connection, now);
         case MOVING -> this.stepMoving(mc, player, now);
         case CONFIRMING -> this.stepConfirming(mc, player, now);
      };
   }

   private boolean stepIdle(MinecraftClient mc, ClientPlayerEntity player, ClientPlayNetworkHandler connection, long now, boolean canCommand) {
      if (now - this.lastCommandAt < 5000L && sellMenuOpen(mc, player)) {
         player.closeHandledScreen();
         return true;
      }

      if (now < this.nextCheckAt) {
         return false;
      }

      this.nextCheckAt = now + 500L;
      if (mc.currentScreen != null || !canCommand || now - this.lastCommandAt < 4000L) {
         return false;
      }

      if (!this.hasSellable(player.getInventory(), now)) {
         return false;
      }

      this.resends = 0;
      this.send(connection, now);
      return true;
   }

   private void send(ClientPlayNetworkHandler connection, long now) {
      connection.sendChatCommand(LT.L1);
      this.lastCommandAt = now;
      this.onCommand.run();
      this.deadline = now + 10000L;
      this.rejected = false;
      this.state = SellRoutine.State.OPENING;
   }

   private boolean stepOpening(MinecraftClient mc, ClientPlayerEntity player, ClientPlayNetworkHandler connection, long now) {
      if (sellMenuOpen(mc, player)) {
         this.menuId = player.currentScreenHandler.syncId;
         this.state = SellRoutine.State.MOVING;
         return true;
      }

      if (this.rejected && now >= this.retryAt && this.resends < 3) {
         this.resends++;
         this.send(connection, now);
         return true;
      }

      if (now < this.deadline) {
         return true;
      }

      this.state = SellRoutine.State.IDLE;
      ModuleChat.send(this.owner, ModuleChat.body().warn(LT.L2).text(LT.L3));
      this.nextCheckAt = now + 30000L;
      return false;
   }

   private boolean stepMoving(MinecraftClient mc, ClientPlayerEntity player, long now) {
      ScreenHandler menu = player.currentScreenHandler;
      if (!this.ours(player)) {
         return this.finish(null, now);
      }

      int moved = 0;

      for (Slot slot : menu.slots) {
         if (slot.inventory instanceof PlayerInventory && slot.hasStack()) {
            int index = slot.getIndex();
            if (index < 36 && this.sellable(index, slot.getStack(), now)) {
               mc.interactionManager.clickSlot(menu.syncId, slot.id, 0, SlotActionType.QUICK_MOVE, player);
               moved++;
            }
         }
      }

      if (moved == 0) {
         return this.close(player, null, now);
      }

      this.offered.clear();
      this.offered.putAll(waitingItems(menu));
      this.confirms = 0;
      this.deadline = now;
      this.state = SellRoutine.State.CONFIRMING;
      return true;
   }

   private boolean stepConfirming(MinecraftClient mc, ClientPlayerEntity player, long now) {
      if (!this.ours(player)) {
         return this.finish(null, now);
      } else if (now < this.deadline) {
         return true;
      } else {
         ScreenHandler menu = player.currentScreenHandler;
         if (this.confirms < 3 && itemsWaiting(menu)) {
            mc.interactionManager.clickSlot(menu.syncId, 53, 0, SlotActionType.PICKUP, player);
            this.confirms++;
            this.deadline = now + (this.confirms == 1 ? 250L : 900L);
            return true;
         } else {
            return this.close(player, waitingItems(menu), now);
         }
      }
   }

   private boolean close(ClientPlayerEntity player, Map<Item, Integer> left, long now) {
      if (this.ours(player)) {
         player.closeHandledScreen();
      }

      return this.finish(left, now);
   }

   private boolean finish(Map<Item, Integer> left, long now) {
      if (left != null) {
         this.settle(left, now);
      }

      this.offered.clear();
      this.menuId = -1;
      this.state = SellRoutine.State.IDLE;
      this.nextCheckAt = now + 500L;
      return false;
   }

   private void settle(Map<Item, Integer> left, long now) {
      int sold = 0;

      for (Entry<Item, Integer> entry : this.offered.entrySet()) {
         Item item = entry.getKey();
         int remaining = left.getOrDefault(item, 0);
         sold += Math.max(0, entry.getValue() - remaining);
         if (remaining <= 0) {
            this.strikes.remove(item);
         } else if (this.strikes.merge(item, 1, Integer::sum) >= 2) {
            this.strikes.remove(item);
            this.skipUntil.put(item, now + 600000L);
            ModuleChat.send(this.owner, ModuleChat.body().warn(LT.L4).name(item.getName().getString()).text(LT.L5));
         }
      }

      if (sold > 0) {
         ModuleChat.send(this.owner, ModuleChat.body().text(LT.L6).value(sold).text(sold == 1 ? LT.L7 : LT.L8));
      }
   }

   private boolean ours(ClientPlayerEntity player) {
      return player.currentScreenHandler != player.playerScreenHandler && player.currentScreenHandler.syncId == this.menuId;
   }

   private boolean hasSellable(PlayerInventory inventory, long now) {
      for (int index = 0; index < 36; index++) {
         ItemStack stack = inventory.getStack(index);
         if (!stack.isEmpty() && this.sellable(index, stack, now)) {
            return true;
         }
      }

      return false;
   }

   private boolean sellable(int index, ItemStack stack, long now) {
      return !this.skipped(stack.getItem(), now) && !this.keep.test(index, stack) && !this.blacklisted(stack);
   }

   private boolean skipped(Item item, long now) {
      Long until = this.skipUntil.get(item);
      if (until == null) {
         return false;
      }

      if (now < until) {
         return true;
      }

      this.skipUntil.remove(item);
      return false;
   }

   private boolean blacklisted(ItemStack stack) {
      return neverSell(stack, this.neverSell.get());
   }

   static boolean neverSell(ItemStack stack, List<String> entries) {
      String id = Registries.ITEM.getId(stack.getItem()).toString().toLowerCase(Locale.ROOT);
      String shortName = id.substring(id.indexOf(58) + 1);

      for (String raw : entries) {
         String entry = raw.trim().toLowerCase(Locale.ROOT);
         if (!entry.isEmpty()) {
            if (id.equals(entry) || shortName.equals(entry)) {
               return true;
            }

            if (entry.indexOf(58) < 0 && id.contains(entry)) {
               return true;
            }
         }
      }

      return false;
   }

   private static boolean itemsWaiting(ScreenHandler menu) {
      for (Slot slot : menu.slots) {
         if (!(slot.inventory instanceof PlayerInventory) && slot.id != 53 && slot.hasStack()) {
            return true;
         }
      }

      return false;
   }

   private static Map<Item, Integer> waitingItems(ScreenHandler menu) {
      Map<Item, Integer> items = new LinkedHashMap<>();

      for (Slot slot : menu.slots) {
         if (!(slot.inventory instanceof PlayerInventory) && slot.id != 53 && slot.hasStack()) {
            items.merge(slot.getStack().getItem(), slot.getStack().getCount(), Integer::sum);
         }
      }

      return items;
   }

   private static boolean sellMenuOpen(MinecraftClient mc, ClientPlayerEntity player) {
      ScreenHandler menu = player.currentScreenHandler;
      return menu != player.playerScreenHandler && mc.currentScreen instanceof HandledScreen && containerSlots(menu) > 53;
   }

   private static int containerSlots(ScreenHandler menu) {
      int count = 0;

      for (Slot slot : menu.slots) {
         if (!(slot.inventory instanceof PlayerInventory)) {
            count++;
         }
      }

      return count;
   }

   private enum State {
      IDLE,
      OPENING,
      MOVING,
      CONFIRMING;
   }


    // cloak literals - runtime PUA carriers (cloak) or functional plain (LT)
    private static final class LT {
            static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xbb13,0x7f90,0x1430,0x802d}, new char[]{0x7fe3,0x1455,0x8041,0xbb7f}).plainString();
            static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb1c0,0x587a,0x6c27,0xdc6f,0xc595,0xd71f,0x2f29,0x676f,0x98cb,0x6e49,0x7e08,0xaab8,0x33a0,0x5ec7,0x2e37,0x935e,0xbbc1,0xb8d3,0x53d3,0x0fd1,0x4960,0xa16e,0xd62a,0xa15a,0xf938,0x9137}, new char[]{0x582e,0xd777,0xb1a5,0x98eb,0xb8a0,0x7e6d,0x490c,0x6c4b,0xa14e,0xf955,0xdc0a,0x9330,0xbbb4,0x5ee7,0x670b,0x915e,0xa13e,0x2f09,0xd644,0x53bc,0xaacc,0xc5b5,0x0fbe,0x6e39,0x33c5,0x2e59}).plainString();
            static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xdf05,0x000d,0x63e0,0x67a1,0xc290,0xde3c,0xc5e6,0x01f0,0xbfeb,0x5f9b,0x3e16,0x85c6,0x4162,0x0e7a,0x4e7c,0xd1c5,0xf4a0,0x7fb8,0xe6ba,0xe40f,0x22dd,0x1bf8}, new char[]{0x414e,0x5fbb,0xd1b1,0xdf77,0xde45,0xbf82,0x0063,0x0197,0x1bd8,0x4e1d,0x22ba,0xe6db,0x6389,0x67cf,0x85e6,0xc2f9,0x3e78,0x7f98,0xf493,0xe43f,0xc595,0x0e54}).plainString();
            static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x74d3,0x03b4,0x2362,0x309c,0xffde,0xa30a,0xfd02,0x57bd,0x8f91,0x1b6a,0xe41f,0x9ba1,0xd390,0xcd2e,0x631c}, new char[]{0x57fe,0x74bc,0x6369,0xd3fc,0x2306,0x8fb1,0xfd6c,0xa365,0xe46b,0x30bc,0xcd5d,0x1b0f,0x03d8,0x9bcd,0xfffe}).plainString();
            static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb302,0x6e0b,0x0b00,0xf838,0x7cd6,0x84ac,0x6bae,0x50b5,0xb3ee,0x64df,0xc37a,0x8f4b,0x282b,0xaa2d,0x2470,0xa218,0xf218,0x7dce,0x96cc,0xe279,0xea95,0x8357,0x3843,0x1ba7,0x8353,0xa26b,0x1901,0xb72d,0x1027}, new char[]{0xeab9,0xa238,0x7ca5,0x6e60,0xc313,0x8327,0x3833,0xb744,0x96a2,0x8f2c,0xb3ce,0x7da7,0xa21f,0x848c,0x0b66,0xf277,0xf84a,0x1b87,0x1016,0x2440,0x64ff,0x50d8,0xaa44,0x2845,0x1974,0x6bda,0xe21c,0xb371,0x837d}).plainString();
            static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa19a,0x0aad,0xbbfb,0x0d0d,0x8b23}, new char[]{0xa1c9,0xbb94,0x0ac1,0x0d69,0x8b03}).plainString();
            static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe158,0xc09f,0x9ab8,0x54f5,0x89fe,0x2481}, new char[]{0x89de,0x24e8,0xe12c,0xc0fa,0x5498,0x9a96}).plainString();
            static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9e31,0x40eb,0x3a56,0x16fb,0x874f,0x76fc,0xf4a5}, new char[]{0x76dc,0x3a3f,0x9e45,0x872a,0x4086,0x1688,0xf48b}).plainString();
    }
}
