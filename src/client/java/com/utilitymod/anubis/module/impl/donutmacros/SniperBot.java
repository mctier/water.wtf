package com.utilitymod.anubis.module.impl.donutmacros;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.utilitymod.anubis.chat.ModuleChat;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.text.Style;
import net.minecraft.text.TextColor;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.DataComponentTypes;

final class SniperBot {
   private static final Pattern RATE_LIMIT = Pattern.compile(LT.L1);
   private static final int LISTING_SLOTS = 45;
   private static final int DEFAULT_FILTER = 47;
   private static final int DEFAULT_REFRESH = 49;
   private static final long OPEN_TIMEOUT_MS = 10000L;
   private static final long BUY_TIMEOUT_MS = 4000L;
   private static final long FAILED_SKIP_MS = 60000L;
   private static final long BOUGHT_SKIP_MS = 15000L;
   private static final long RECOVERY_MS = 2000L;
   private final AhSniperModule module;
   private final MacroWorker worker;
   private final Gui gui;
   private final SnipeTarget target;
   private final long refreshMs;
   private final int maxBuys;
   private final AuctionHouse ah;
   private final AuctionHouse.Stock stock;
   private final List<AuctionHouse.Stock> stocks;
   private final Map<String, Long> skip = new HashMap<>();
   private volatile long boughtAt;
   private volatile long goneAt;
   private volatile long poorAt;
   private volatile long ownAt;
   private volatile long holdUntil;
   private int bought;
   private int unsold;
   private long spent;

   SniperBot(AhSniperModule module, MacroWorker worker, SnipeTarget target, long refreshMs, int maxBuys, long sellPrice, String ign) {
      this.module = module;
      this.worker = worker;
      this.gui = new Gui(worker);
      this.target = target;
      this.refreshMs = refreshMs;
      this.maxBuys = maxBuys;
      if (sellPrice > 0L) {
         this.ah = new AuctionHouse(module, worker, new AuctionHouse.Config(0L, 60000L, 20000L, 1, 1000, 0L, 3.0, 20.0, 36, 15000L), ign);
         this.stock = new AuctionHouse.Stock(target.product(sellPrice), true);
         this.stocks = List.of(this.stock);
      } else {
         this.ah = null;
         this.stock = null;
         this.stocks = List.of();
      }
   }

   private void say(ModuleChat.Body body) {
      ModuleChat.send(this.module, body);
   }

   void run() {
      this.say(
         ModuleChat.body()
            .text(LT.L2)
            .name(this.target.describe())
            .text(LT.L3)
            .value(ServerText.shortMoney(this.target.maxPrice))
            .text(this.target.minMinutes > 0 ? LT.L4 + formatMinutes(this.target.minMinutes) + LT.L5 : "")
            .text(this.stock != null ? LT.L6 + ServerText.shortMoney(this.stock.price) : "")
            .text(LT2.L1)
      );

      while (true) {
         this.worker.check();

         try {
            if (!this.worker.call(() -> Gui.mc().player != null && Gui.mc().getNetworkHandler() != null)) {
               this.worker.sleep(2000L);
            } else {
               if (this.stock != null && this.unsold > 0) {
                  this.sellStock();
               }

               this.snipe();
            }
         } catch (MacroWorker.Cancelled cancelled) {
            throw cancelled;
         } catch (RuntimeException failure) {
            String reason = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
            this.say(ModuleChat.body().warn(reason).text(LT.L7));
            this.worker.check();
            this.gui.closeAll();
            this.worker.sleep(2000L);
         }
      }
   }

   private void snipe() {
      this.openAuction();
      this.ensureRecent();
      long nextRefresh = System.currentTimeMillis() + this.refreshMs;

      while (true) {
         this.worker.check();
         if (!this.worker.call(SniperBot::auctionOpenNow)) {
            return;
         }

         long mark = System.currentTimeMillis();
         SniperBot.Hit hit = this.worker.call(this::scanAndClick);
         if (hit != null) {
            if (this.handleBuy(hit, mark)) {
               return;
            }

            nextRefresh = System.currentTimeMillis() + this.refreshMs;
         } else {
            long now = System.currentTimeMillis();
            if (now >= nextRefresh) {
               this.worker.run(this::clickRefresh);
               nextRefresh = now + this.refreshMs;
            }

            this.worker.sleep(50L);
         }
      }
   }

   private void openAuction() {
      this.gui.closeAll();
      long wait = this.holdUntil - System.currentTimeMillis();
      if (wait > 0L) {
         this.worker.sleep(wait);
      }

      this.gui.command(LT.L8 + this.target.search);
      if (!this.worker.waitFor(SniperBot::auctionOpenNow, 10000L)) {
         throw new AuctionHouse.Problem(LT.L9);
      }

      this.worker.sleep(300L);
   }

   private static boolean auctionOpenNow() {
      if (!Gui.containerOpenNow()) {
         return false;
      }

      String title = ServerText.normalize(Gui.mc().currentScreen.getTitle());
      return title.contains(LT.L10) ? false : title.contains(LT.L11) || refreshSlot(Gui.containerNow()) >= 0;
   }

   private void ensureRecent() {
      for (int attempt = 0; attempt < 4; attempt++) {
         int[] state = this.worker.call(() -> {
            List<ItemStack> stacks = Gui.containerNow();
            int slot = filterSlot(stacks);
            return new int[]{slot, slot < 0 ? SniperBot.Sort.UNKNOWN.ordinal() : selectedSort(stacks.get(slot)).ordinal()};
         });
         if (state[0] < 0) {
            this.say(ModuleChat.body().warn(LT.L12).text(LT.L13));
            return;
         }

         SniperBot.Sort sort = SniperBot.Sort.values()[state[1]];
         if (sort == SniperBot.Sort.RECENT) {
            return;
         }

         if (sort == SniperBot.Sort.UNKNOWN && attempt > 0) {
            this.say(ModuleChat.body().warn(LT.L14).text(LT.L15));
            return;
         }

         String before = this.gui.fingerprint();
         this.gui.click(state[0], 0, SlotActionType.PICKUP);
         this.gui.waitChange(before, 2000L);
         this.worker.sleep(250L);
      }
   }

   private static int filterSlot(List<ItemStack> stacks) {
      for (int i = 45; i < stacks.size(); i++) {
         ItemStack stack = stacks.get(i);
         if (!stack.isEmpty() && (stack.isOf(Items.HOPPER) || Gui.nameOf(stack).contains(LT.L16))) {
            return i;
         }
      }

      return stacks.size() > 47 && !stacks.get(47).isEmpty() ? 47 : -1;
   }

   private static int refreshSlot(List<ItemStack> stacks) {
      for (int i = 45; i < stacks.size(); i++) {
         ItemStack stack = stacks.get(i);
         if (!stack.isEmpty() && stack.isOf(Items.ANVIL)) {
            return i;
         }
      }

      return stacks.size() > 49 && stacks.get(49).isOf(Items.ANVIL) ? 49 : -1;
   }

   static SniperBot.Sort selectedSort(ItemStack filter) {
      List<SniperBot.Sort> options = new ArrayList<>();
      List<Integer> colors = new ArrayList<>();

      for (Text line : ((LoreComponent)filter.getOrDefault(DataComponentTypes.LORE, LoreComponent.DEFAULT)).lines()) {
         String text = ServerText.normalize(Dialogs.visibleText(line));
         SniperBot.Sort option = text.contains(LT.L17)
            ? SniperBot.Sort.RECENT
            : (text.contains(LT.L18) ? SniperBot.Sort.LOWEST : (text.contains(LT.L19) ? SniperBot.Sort.HIGHEST : SniperBot.Sort.UNKNOWN));
         if (option != SniperBot.Sort.UNKNOWN) {
            options.add(option);
            colors.add(color(line));
         }
      }

      if (options.size() < 2) {
         return SniperBot.Sort.UNKNOWN;
      }

      for (int i = 0; i < options.size(); i++) {
         boolean unique = true;
         boolean othersAgree = true;
         int other = -2;

         for (int j = 0; j < options.size(); j++) {
            if (j != i) {
               if (colors.get(j).equals(colors.get(i))) {
                  unique = false;
               }

               if (other == -2) {
                  other = colors.get(j);
               } else if (other != colors.get(j)) {
                  othersAgree = false;
               }
            }
         }

         if (unique && othersAgree) {
            return options.get(i);
         }
      }

      int best = -1;
      SniperBot.Sort pick = SniperBot.Sort.UNKNOWN;

      for (int i = 0; i < options.size(); i++) {
         int rgb = colors.get(i);
         int brightness = (rgb >> 16 & 0xFF) + (rgb >> 8 & 0xFF) + (rgb & 0xFF);
         if (brightness > best) {
            best = brightness;
            pick = options.get(i);
         } else if (brightness == best) {
            pick = SniperBot.Sort.UNKNOWN;
         }
      }

      return pick;
   }

   private static int color(Text line) {
      int[] value = new int[]{-1};
      line.visit((style, text) -> {
         if (value[0] < 0 && !text.isBlank() && !text.trim().equals("•") && !text.trim().equals("▪")) {
            TextColor color = style.getColor();
            value[0] = color == null ? 11141290 : color.getRgb();
            return Optional.empty();
         } else {
            return Optional.empty();
         }
      }, Style.EMPTY);
      return value[0];
   }

   private void clickRefresh() {
      ClientPlayerEntity player = Gui.mc().player;
      if (player != null && auctionOpenNow()) {
         int slot = refreshSlot(Gui.containerNow());
         if (slot >= 0) {
            Gui.mc().interactionManager.clickSlot(player.currentScreenHandler.syncId, slot, 0, SlotActionType.PICKUP, player);
         }
      }
   }

   private SniperBot.Hit scanAndClick() {
      ClientPlayerEntity player = Gui.mc().player;
      if (player != null && auctionOpenNow()) {
         String title = ServerText.normalize(Gui.mc().currentScreen.getTitle());
         if (title.contains(LT.L20) && !title.contains(LT.L21)) {
            return null;
         }

         long now = System.currentTimeMillis();
         this.skip.values().removeIf(until -> until < now);
         List<ItemStack> stacks = Gui.containerNow();

         for (int i = 0; i < 45 && i < stacks.size(); i++) {
            ItemStack stack = stacks.get(i);
            if (!stack.isEmpty() && this.target.matches(stack)) {
               double total = ServerText.listingPrice(stack);
               if (!(total <= 0.0)) {
                  long unit = Math.round(total / Math.max(1, stack.getCount()));
                  if (unit <= this.target.maxPrice) {
                     String fingerprint = fingerprint(stack, total);
                     if (!this.skip.containsKey(fingerprint)) {
                        Gui.mc().interactionManager.clickSlot(player.currentScreenHandler.syncId, i, 0, SlotActionType.PICKUP, player);
                        return new SniperBot.Hit(i, unit, fingerprint, stack.getName().getString());
                     }
                  }
               }
            }
         }

         return null;
      } else {
         return null;
      }
   }

   private static String fingerprint(ItemStack stack, double total) {
      return Registries.ITEM.getId(stack.getItem())
         + LT2.L2
         + stack.getName().getString()
         + LT2.L2
         + Math.round(total)
         + LT2.L2
         + stack.getDamage()
         + LT2.L2
         + String.join(LT2.L3, ServerText.lore(stack));
   }

   private boolean handleBuy(SniperBot.Hit hit, long mark) {
      Object[] pressed = new Object[]{null};
      int[] pressedChest = new int[]{-1};
      long end = System.currentTimeMillis() + 4000L;

      while (System.currentTimeMillis() < end) {
         if (this.boughtAt >= mark) {
            return this.bought(hit);
         }

         if (this.poorAt >= mark) {
            this.say(
               ModuleChat.body()
                  .danger(LT.L22)
                  .text(LT.L23)
                  .name(hit.name())
                  .text(LT.L24)
                  .value(ServerText.shortMoney(hit.price()))
                  .text(LT.L25)
            );
            this.module.stopFromBot(this);
            throw new MacroWorker.Cancelled();
         }

         if (this.goneAt >= mark || this.ownAt >= mark) {
            this.skip.put(hit.fingerprint(), System.currentTimeMillis() + 60000L);
            return false;
         }

         this.worker.run(() -> {
            Dialogs.View view = Dialogs.current(Gui.mc());
            if (view == null) {
               ClientPlayerEntity player = Gui.mc().player;
               if (player != null && Gui.containerOpenNow() && !auctionOpenNow()) {
                  int sync = player.currentScreenHandler.syncId;
                  if (sync != pressedChest[0]) {
                     List<ItemStack> stacks = Gui.containerNow();

                     for (int i = 0; i < stacks.size(); i++) {
                        ItemStack stack = stacks.get(i);
                        if (!stack.isEmpty() && Registries.ITEM.getId(stack.getItem()).getPath().contains(LT.L26)) {
                           Gui.mc().interactionManager.clickSlot(sync, i, 0, SlotActionType.PICKUP, player);
                           pressedChest[0] = sync;
                           return;
                        }
                     }
                  }
               }
            } else {
               if (view.dialog() != pressed[0] && (view.titled(LT.L27) || view.titled(LT.L28) || view.titled(LT.L29))) {
                  Dialogs.Button yes = view.labelled(LT.L30, LT.L31, LT.L27);
                  if (yes != null && Dialogs.press(Gui.mc(), view, yes, Map.of())) {
                     pressed[0] = view.dialog();
                  }
               }
            }
         });
         this.worker.sleep(50L);
      }

      this.skip.put(hit.fingerprint(), System.currentTimeMillis() + 60000L);
      return !this.worker.call(SniperBot::auctionOpenNow);
   }

   private boolean bought(SniperBot.Hit hit) {
      this.skip.put(hit.fingerprint(), System.currentTimeMillis() + 15000L);
      this.bought++;
      this.unsold++;
      this.spent = this.spent + hit.price();
      this.say(
         ModuleChat.body()
            .good(LT.L32)
            .text(" ")
            .name(hit.name())
            .text(LT.L33)
            .value(ServerText.shortMoney(hit.price()))
            .text(LT2.L4)
            .value(this.bought)
            .text(this.bought == 1 ? LT.L34 : LT.L35)
            .text(LT2.L5)
            .value(ServerText.shortMoney(this.spent))
            .text(LT.L36)
      );
      if (this.maxBuys > 0 && this.bought >= this.maxBuys) {
         if (this.stock != null) {
            this.worker.sleep(300L);
            this.sellStock();
         }

         this.say(ModuleChat.body().text(LT.L37).value(this.maxBuys).text(this.maxBuys == 1 ? LT.L34 : LT.L35).text(LT.L25));
         this.module.stopFromBot(this);
         throw new MacroWorker.Cancelled();
      } else {
         if (this.stock == null) {
            return !this.worker.call(SniperBot::auctionOpenNow);
         }

         this.worker.sleep(300L);
         this.sellStock();
         return true;
      }
   }

   private void sellStock() {
      int count = Math.min(this.unsold, this.ah.countInventory(this.stock.product));
      if (count > 0) {
         this.unsold = this.unsold - this.ah.listSome(this.stock, Math.min(count, 9), new int[]{0});
      }
   }

   void onChat(String raw) {
      String line = ServerText.normalize(raw);
      if (!line.isEmpty()) {
         long now = System.currentTimeMillis();
         Matcher wait = RATE_LIMIT.matcher(line);
         if (wait.find()) {
            try {
               this.holdUntil = now + (long)Math.ceil(Double.parseDouble(wait.group(1)) * 1000.0) + 100L;
            } catch (NumberFormatException var7) {
            }
         }

         if (line.startsWith(LT.L38)) {
            this.boughtAt = now;
         }

         if (line.contains(LT.L39) || line.contains(LT.L40) || line.contains(LT.L41)) {
            this.goneAt = now;
         }

         if (line.contains(LT.L42)) {
            this.poorAt = now;
         }

         if (line.contains(LT.L43)) {
            this.ownAt = now;
         }

         if (this.ah != null) {
            this.ah.onChat(line, this.stocks);
         }
      }
   }

   String summary() {
      return LT.L44
         + this.bought
         + (this.bought == 1 ? LT.L45 : LT.L46)
         + LT.L33
         + ServerText.shortMoney(this.spent)
         + (this.stock != null ? LT2.L6 + AuctionHouse.summary(this.stocks) : LT2.L1);
   }

   private static String formatMinutes(int minutes) {
      return minutes % 60 == 0 ? minutes / 60 + LT2.L7 : minutes / 60 + LT2.L8 + minutes % 60 + LT2.L9;
   }

   private record Hit(int slot, long price, String fingerprint, String name) {
   }

   enum Sort {
      UNKNOWN,
      LOWEST,
      HIGHEST,
      RECENT;
   }


    // cloak literals - runtime PUA carriers (cloak) or functional plain (LT)
    private static final class LT {
            static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x37c4,0x3336,0x7c6b,0xaeef,0x3cf9,0x6efe,0x4289,0x99d4,0xdf39,0xa1f7,0x12df,0x1c55,0x506e,0x8bab,0x9cea,0x996b,0x3705,0xa89f,0x6e70,0x4351,0xe4a2,0xe856,0x15a5,0xa529,0x62ca,0x6cb0,0xfb28,0xec3c,0xef54,0x9fee,0xd8ec,0x0c62,0xe9a3,0x2236,0x9517,0x7138,0x3037,0xe84b,0x1c48,0xf538,0x474f,0x6bf1}, new char[]{0xa18e,0x3359,0xe4d7,0x1c75,0x99ba,0x715d,0xe9c6,0x4335,0x6e50,0xe83f,0xf557,0x6bd1,0x9560,0xe837,0x376c,0x3c8d,0xa8bf,0x6cd1,0xdf57,0x9c85,0xec48,0x8bc3,0xd889,0xfb5a,0x1c68,0xa501,0x9fb5,0x4713,0xef30,0x9945,0x1282,0xaec4,0x221f,0x1585,0x7c18,0x3052,0x6e9d,0x62a5,0x5000,0x42ed,0x0c11,0x37fb}).plainString();
            static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1b3f,0x7583,0x3d7b,0x29c2,0xc531,0x912f,0x3610,0xab47}, new char[]{0x2991,0x1b51,0x9146,0xc541,0x3d12,0x367e,0xab20,0x75a3}).plainString();
            static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc24e,0xc7a2,0x20a5,0xc2ea,0x7689,0x2532,0x2d8b,0x5ac2}, new char[]{0x2085,0xc7d7,0x2de5,0xc22a,0x5aa7,0x76fb,0xc2ca,0x2516}).plainString();
            static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9339,0x54ce,0x5f4d,0x2cee,0x9796,0x7c68}, new char[]{0x7c48,0x97e1,0x2c87,0x54ba,0x9351,0x5f6d}).plainString();
            static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2822,0xbac9,0xcf87,0x7e55,0x235b,0x7c3f}, new char[]{0xcfac,0x237b,0x7c53,0xbaac,0x7e33,0x2856}).plainString();
            static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x46e6,0x9d98,0xf9ab,0xc607,0xb384,0xaee5,0xdc85,0x362e,0xa48a,0xdb1e,0xe880,0xda0a,0x6faf,0x2b9a}, new char[]{0x6f83,0xe8a0,0xf9d8,0xc662,0x3642,0xa4e6,0xdcec,0xb3ea,0xda6d,0xdb3e,0x9df9,0xae91,0x2bba,0x46c2}).plainString();
            static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5225,0x5a1c,0xa146,0x9adb,0x8283,0x4fd0,0x1e74,0x9b1f,0x7d09,0x4c04,0xaf5d,0x8197,0x00b1,0xd5bc,0xf754}, new char[]{0xd590,0x81b7,0x5251,0x9b6d,0x5a65,0x7d60,0xa128,0x00d6,0x1e54,0x4fb1,0x9abc,0xf735,0x82ea,0x4c6a,0xaf73}).plainString();
            static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8518,0xea07,0x9936}, new char[]{0xea66,0x8570,0x9916,0x8522}).plainString();
            static final String L9 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xda17,0x18f0,0x3825,0x66df,0xce9d,0xe536,0x13aa,0x94fc,0xb213,0xafb0,0x0611,0x2045,0x02f5,0xae3d,0x4451,0x859e,0x6563,0xa39b,0xb0d2}, new char[]{0x0645,0x029d,0xe553,0xb233,0x2004,0xb09a,0x18d0,0x6507,0x66b6,0x13ce,0x94dc,0xda79,0x384a,0xcee9,0xae1d,0xa3f4,0x85ee,0xafd5,0x443f}).toString();
            static final String L10 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd664,0x0d00,0xe7bf,0x00e5,0x4e71}, new char[]{0x0d6f,0xd616,0x4e15,0xe7da,0x0097}).plainString();
            static final String L11 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa745,0xd805,0x7fb8,0x29c8,0xa12f,0xaec7,0xd963}, new char[]{0xa724,0x29bd,0xaea4,0xd871,0x7fd1,0xa140,0xd90d}).plainString();
            static final String L12 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x629c,0x2135,0x223b,0xc7d6,0x5ab1,0x63f3,0xb94e,0x20a4,0xd665,0x62fe,0xec50,0xaca1,0x9606,0x15f6,0xf9f1,0x2f20,0x3126,0x471c,0xa8ef,0x98e7,0x3424,0x2e87}, new char[]{0x98a9,0xb921,0xf9d1,0xd623,0x4775,0x314a,0xec24,0x2f45,0x3456,0x2ea7,0x20c6,0x628b,0xc7a2,0x6387,0x5ade,0x62f2,0x9626,0x1590,0xacce,0x2140,0xa881,0x225f}).plainString();
            static final String L13 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xfc13,0x8ba6,0x3b1a,0x5dcc,0xe5e5,0x03f1,0x10b4,0x53a7,0x6306,0xbee6,0xc4cd,0xf882,0x8292,0x2f3c,0x7014,0xb6d7,0x9136,0xd4ca,0x40a7,0xf1ea,0x40fd,0x1b53,0x6ea0,0x63f3,0x0ef6,0xebe8,0x386a,0xa049,0x916a,0x2531,0xb03b,0xc091,0xb831,0x5742,0x037d}, new char[]{0xf1c6,0xa069,0x9119,0xbe88,0xe58c,0x3b6a,0xb052,0x639d,0x10d3,0x2f1c,0x9141,0x707d,0xeb9c,0x636e,0x6e80,0xfc67,0x1b3b,0x380f,0x82b2,0xf8e1,0x8bd3,0x030f,0x0383,0x0e93,0xb6b9,0xd4be,0x5dec,0x5731,0x4092,0x2543,0x53d3,0xc0f8,0xc4a3,0x40c0,0xb81f}).plainString();
            static final String L14 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7ecc,0x8b30,0x6183,0xe1f0,0xfbb7,0x4208,0x675b,0xcba4,0x6ea9,0x3d9b,0x92d1,0x93e7,0x6bbd,0x1a91,0x5853,0x9111,0x3fd2,0x68ba,0x88d7,0x8bfa,0x53c0,0x4af8,0x63e5,0x6146,0x8466}, new char[]{0x8894,0x61ec,0x3dee,0x3fbe,0xfbd3,0x1ab1,0x6ec7,0x92be,0x6391,0x8bda,0x6729,0x53a5,0x68db,0x9383,0x8446,0xe184,0x9179,0x7ea9,0x5873,0x4abe,0x612f,0x8b5c,0x6bc9,0xcbc1,0x427a}).plainString();
            static final String L15 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x23c1,0xe219,0xb9da,0xb6b7,0x4621,0xa5bb,0x51a1,0x93ae,0xb54d,0xcd06,0x9bcc,0x1bbc,0x58c1,0x7232,0x5541,0x0c43,0xb7b3,0x2d1d,0x5cb6,0x3ead,0xdf71,0x76cc,0xb5ab,0x3693,0x1a1b,0x14de,0x12e7,0x550b,0x44db,0x6d3b,0xa710,0x4655,0xb27f,0xcfc0,0x67f4,0xd95a,0xeb0d}, new char[]{0xb561,0xb25f,0xb5c6,0x6795,0x1a70,0x5cd3,0x4601,0x9bbf,0x44ae,0x76be,0x556e,0xe239,0x5528,0xcd72,0x12c7,0xa5d2,0x6d48,0x938e,0xcfaf,0x725c,0x58e1,0x2393,0xd93f,0x0c20,0x14bb,0xb6d9,0x2d69,0xb7df,0x51d8,0xb9fa,0x4619,0xdf18,0x3ede,0xeb79,0x36f6,0x1bd8,0xa73e}).plainString();
            static final String L16 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x40ef,0xf7a2,0x3c1c,0x0d69,0xe547,0xf861}, new char[]{0x4089,0xf808,0x3c70,0xe533,0xf7c7,0x0d1b}).plainString();
            static final String L17 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc54a,0xeef1,0x59f9,0x43fa,0xdb9b,0x029d}, new char[]{0x4388,0xdbfe,0xc529,0xee94,0x02f3,0x598d}).plainString();
            static final String L18 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xec58,0xa49d,0x30c0,0x35c6,0xef70,0xb4b0}, new char[]{0xec34,0x30af,0xb4c7,0xef15,0x35b5,0xa4e9}).plainString();
            static final String L19 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xda06,0x1ae7,0x9671,0x3469,0xbe53,0x1b83,0x97e4}, new char[]{0x3401,0xda6f,0x1be4,0xbe3b,0x1a82,0x9602,0x9790}).plainString();
            static final String L20 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe899,0x4c02,0xc52a,0x9b29,0xc90c}, new char[]{0xe8b1,0xc97c,0x4c63,0xc54d,0x9b4c}).plainString();
            static final String L21 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb878,0xdf14,0xa1fd,0x89f9,0x55c9,0xc46f,0x542b,0x850b}, new char[]{0x5403,0x857b,0x55a8,0xdf73,0xc40a,0xa1dd,0xb849,0x89d0}).plainString();
            static final String L22 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1ffb,0xb139,0x6370,0x1838,0x1947,0x96e8,0x957e,0x845f,0x912f,0x8f59,0x9a93,0x3f1d,0x13d9,0x5858,0x5e20,0x79b1}, new char[]{0x633e,0x9afc,0x1933,0x910f,0x185d,0x9510,0x5e4f,0x8f2c,0x968f,0x13b1,0x7991,0x5835,0x1f94,0xb157,0x3f78,0x8426}).toString();
            static final String L23 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9ebd,0x7a84,0xa078,0xa9f0,0x87c3}, new char[]{0x9e9d,0xa996,0x7aeb,0xa00a,0x87e3}).plainString();
            static final String L24 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1305,0x9545,0x68af,0xe961,0x5074}, new char[]{0x5054,0x1364,0x68db,0xe941,0x9561}).plainString();
            static final String L25 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0ed0,0x6a9e,0xa95c,0xa2e9,0x493a,0x2cc9,0x8529,0x3c56,0x2f0d,0xc0fe,0xce11}, new char[]{0xce3d,0x8509,0x0ea3,0x494e,0xc091,0x3c26,0x6aee,0xa935,0x2ca7,0xa28e,0x2f23}).plainString();
            static final String L26 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xfd43,0x82fe,0xc2f9,0x478e}, new char[]{0x47e2,0xc290,0x8293,0xfd26}).plainString();
            static final String L27 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6f42,0x867c,0xc776}, new char[]{0x6f20,0x8609,0xc70f,0xb050}).plainString();
            static final String L28 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0a1b,0x6d68,0xa8dc,0xcd4b}, new char[]{0x0a68,0x6d1d,0xa8ae,0xcd2e}).plainString();
            static final String L29 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5611,0xc0aa,0xbadc,0x001b,0x4b42,0x829a,0x9d98,0xa9d3}, new char[]{0x4b32,0xa9a6,0xc0d8,0x0078,0x5679,0x9df9,0x82e9,0xbab9}).plainString();
            static final String L30 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x28e0,0xda61,0x7b38}, new char[]{0x2899,0x7b5d,0xda12,0x4dcd}).plainString();
            static final String L31 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9c2f,0x1273,0x3065,0xbd86,0x8c1a,0x507d,0x2393}, new char[]{0xbde5,0x5012,0x8c74,0x23f5,0x121a,0x9c5d,0x3008}).plainString();
            static final String L32 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xcaa1,0xfbee,0x9dca,0x369e,0xea91,0xc3d8}, new char[]{0x36cd,0xfb80,0xeaf8,0xc3a8,0xcac4,0x9dae}).plainString();
            static final String L33 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7244,0x6349,0x0bcd,0xfa27,0x325e,0xe80d}, new char[]{0xe82d,0x632f,0x722b,0xfa55,0x0bed,0x327a}).plainString();
            static final String L34 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x37d6,0x8a54,0xfa07,0x97f9}, new char[]{0x97d9,0xfa65,0x8a21,0x37af}).plainString();
            static final String L35 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe177,0xf698,0xdfe0,0xff80,0x8202}, new char[]{0xe157,0xffe2,0xdf95,0xf6e1,0x8271}).plainString();
            static final String L36 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5cf6,0xf52a,0xfc8a,0xc63b,0x2b3b,0xc8c3,0x7a17,0x6ab5}, new char[]{0xc8e3,0x7a64,0xc64b,0xf54f,0x6adb,0xfcfe,0x2b12,0x5cd8}).plainString();
            static final String L37 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6c9a,0x95d5,0xc985,0x57af,0xfc23,0x8613,0x6e64,0x3c90}, new char[]{0x3cc2,0xc9e0,0x6e05,0x6cf9,0xfc4b,0x95b0,0x8677,0x578f}).plainString();
            static final String L38 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf09c,0xcb39,0x3fd2,0x6d63,0xb01e,0xc1bc,0x3596,0x2f3a,0xf148,0x3bad}, new char[]{0xf131,0x35f9,0xcb4c,0xc19c,0x2f58,0xb071,0xf0e9,0x6d04,0x3bc5,0x3fa6}).plainString();
            static final String L39 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0c8e,0x86c0,0x205a,0xe799,0x768b,0x4de6,0x9eb2,0xe6e8,0x1e84,0x031b,0x2d4b,0x65b6,0xbf11,0x58cb}, new char[]{0x76ea,0x86ac,0x65c4,0x9ed7,0x1ee5,0xbf75,0x2d32,0xe7b9,0x2038,0xe687,0x036e,0x0ce9,0x4d8e,0x58bf}).plainString();
            static final String L40 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4b40,0x54e4,0xfbee,0x97f2,0x9f60,0x3e3e,0x411f,0x7f56,0x15b7,0xa127,0x315d,0xa8b8,0x4da3,0x1985,0x9c02,0x4ccb,0x582e,0xb393,0x1d52}, new char[]{0x548a,0xfb81,0x4b60,0x7f3a,0x5841,0x1d3c,0x15d0,0x4dc6,0x9f12,0x19a5,0x9c63,0xa151,0x9793,0xb3fa,0x4173,0x3e5f,0xa8da,0x3131,0x4cae}).plainString();
            static final String L41 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7f0e,0x08ce,0x3e9c,0x548d,0x3f7d,0xb493,0x1a04,0xf86f,0xf783,0xf788,0x2983,0xbe1d,0x1a9d}, new char[]{0xb4fd,0x3f12,0xbe69,0x3ebc,0x1afc,0xf7f5,0x08af,0x7f67,0x29ef,0xf80e,0x54ef,0x1a68,0xf7ed}).plainString();
            static final String L42 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa64a,0x1d81,0x645c,0xda31,0x6610,0x496a,0xea5d,0xd869,0xdeef,0xf1f6,0xa2b2,0x8782}, new char[]{0xda54,0x87ec,0xd806,0xa2c7,0x6677,0xa622,0x1da1,0xf19b,0xea32,0x6432,0x490f,0xde96}).plainString();
            static final String L43 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x305b,0xb903,0x88cb,0x1d41,0x9a6e,0xb908,0x1ff2,0x0c5d}, new char[]{0x3022,0x1f9d,0x9a1b,0x1d33,0xb928,0x88a4,0x0c2a,0xb96d}).plainString();
            static final String L44 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xbfcd,0x5d86,0xebd9,0x79f6,0xac7e,0xe935,0x6794}, new char[]{0x5dd5,0x7998,0xac17,0x67e4,0xebbc,0xe951,0xbfed}).toString();
            static final String L45 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9ae9,0x319e,0x059f,0x7d2c,0x24e7}, new char[]{0x31be,0x9a80,0x2493,0x05fa,0x7d41}).plainString();
            static final String L46 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc36a,0xe819,0xcb71,0xbc86,0x7b08,0x6aab}, new char[]{0xcb51,0x7b61,0xc31e,0xe87c,0x6ac6,0xbcf5}).plainString();
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT2 {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xeb1b}, new char[]{0xeb35,0xf39a,0xa327,0x6557}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf524}, new char[]{0xf558,0xd639,0xa078,0xa71d}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2e7e}, new char[]{0x2e51,0x90c6,0x75be,0xbfd2}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x309f,0xa7d7}, new char[]{0x30bf,0xa7ff,0x5425,0x9d9a}).plainString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe2bd,0x0a2e,0x62e9}, new char[]{0xe291,0x62c9,0x0a0a,0xde89}).plainString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf2b1,0x7cc7}, new char[]{0x7ce9,0xf291,0x5389,0xb7f9}).plainString();
        static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1053}, new char[]{0x103b,0xb51b,0xf923,0x6247}).plainString();
        static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3b24,0xadc8}, new char[]{0xada0,0x3b04,0x67da,0xae4d}).plainString();
        static final String L9 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe39b}, new char[]{0xe3f6,0x7a51,0xf76b,0xabac}).plainString();
    }
}
