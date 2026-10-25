package com.utilitymod.anubis.module.impl.donutmacros;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import com.utilitymod.anubis.chat.ModuleChat;
import com.utilitymod.anubis.module.ClientModule;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.item.ItemStack;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.registry.Registries;

final class AuctionHouse {
   private static final int INVENTORY_SLOTS = 36;
   private static final int AH_SCAN_SLOTS = 45;
   private static final int AH_ROOT_BUTTON = 51;
   private static final int CHEST_CONFIRM_SLOT = 15;
   private static final int INVENTORY_SETTLE_TICKS = 4;
   private static final int COLLECT_LIMIT = 50;
   private static final int VERIFY_LIMIT = 15;
   private static final long OPEN_TIMEOUT_MS = 10000L;
   private static final long NAV_TIMEOUT_MS = 4000L;
   private static final long SELL_TIMEOUT_MS = 15000L;
   final ClientModule module;
   final MacroWorker worker;
   final Gui gui;
   final AuctionHouse.Config config;
   private final String ign;
   private final String ignKey;
   private volatile long tooManyAt;
   private volatile long alreadyBoughtAt;
   private volatile long listedAt;
   private volatile long errorAt;

   AuctionHouse(ClientModule module, MacroWorker worker, AuctionHouse.Config config, String ign) {
      this.module = module;
      this.worker = worker;
      this.gui = new Gui(worker);
      this.config = config;
      this.ign = ign;
      this.ignKey = ServerText.key(ign);
   }

   void say(ModuleChat.Body body) {
      ModuleChat.send(this.module, body);
   }

   boolean inWorld() {
      return this.worker.call(() -> Gui.mc().player != null && Gui.mc().getNetworkHandler() != null);
   }

   void ensurePrice(AuctionHouse.Stock stock) {
      if (stock.fixedPrice) {
         stock.price = stock.product.minPrice();
      } else {
         if (stock.price <= 0L || System.currentTimeMillis() - stock.lastPriceCheck >= this.config.priceCheckMs()) {
            this.updatePrice(stock);
         }
      }
   }

   private void updatePrice(AuctionHouse.Stock stock) {
      Product product = stock.product;
      double[] scan = this.scan(stock);
      stock.lastPriceCheck = System.currentTimeMillis();
      boolean confirmed = scan[0] > 0.0;
      boolean matched = scan[1] > 0.0;
      double cheapestUnit = scan[2];
      long undercut = this.config.undercut();
      String name = product.displayName();
      if (cheapestUnit > 0.0) {
         long cheapest = Math.round(cheapestUnit);
         long next = Math.max(cheapest - undercut, product.minPrice());
         if (stock.price > 0L && cheapest < stock.price) {
            long drop = stock.price - cheapest;
            if (drop > undercut * this.config.ignoreDrops()) {
               this.say(
                  ModuleChat.body()
                     .name(name)
                     .text(LT2.L1)
                     .warn(LT.L1)
                     .text(LT.L2)
                     .value(ServerText.shortMoney(cheapest))
                     .text(LT.L3)
                     .value(ServerText.shortMoney(drop))
                     .text(LT2.L2)
               );
            } else if (next < stock.price) {
               this.say(
                  ModuleChat.body()
                     .name(name)
                     .text(LT2.L1)
                     .warn(LT.L4)
                     .text(LT.L2)
                     .value(ServerText.shortMoney(cheapest))
                     .text(LT.L5)
                     .value(ServerText.shortMoney(next))
                     .text(LT2.L3)
               );
               this.relist(stock, next);
            }
         } else if (stock.price > 0L && next > stock.price + undercut * this.config.relistGap() && stock.inAh > 0) {
            this.say(
               ModuleChat.body()
                  .name(name)
                  .text(LT.L6)
                  .value(ServerText.shortMoney(cheapest))
                  .text(LT.L5)
                  .value(ServerText.shortMoney(next))
                  .text(LT2.L3)
            );
            this.relist(stock, next);
         } else {
            if (stock.price <= 0L) {
               this.say(
                  ModuleChat.body()
                     .name(name)
                     .text(LT.L7)
                     .value(ServerText.shortMoney(cheapest))
                     .text(LT.L8)
                     .value(ServerText.shortMoney(next))
                     .text(LT.L9)
               );
            }

            stock.price = next;
         }
      } else if (stock.price <= 0L && confirmed && !matched) {
         stock.price = product.minPrice() * 2L;
         this.say(ModuleChat.body().name(name).text(LT.L10).value(ServerText.shortMoney(stock.price)).text(LT.L9));
      } else if (stock.price <= 0L) {
         this.say(ModuleChat.body().name(name).text(LT2.L1).warn(LT.L11).text(LT.L12));
      }
   }

   private double[] scan(AuctionHouse.Stock stock) {
      Product product = stock.product;
      this.gui.closeAll();
      this.worker.sleep(200L);
      this.gui.command(LT.L13 + product.search());
      boolean open = this.worker.waitFor(() -> this.isAhResultsNow() && this.hasContent(), 10000L);
      if (!open) {
         this.gui.closeAll();
         return new double[]{0.0, 0.0, -1.0};
      } else {
         this.worker.sleep(600L);
         if (!this.worker.call(this::isAhResultsNow)) {
            this.gui.closeAll();
            return new double[]{0.0, 0.0, -1.0};
         } else {
            double[] result = this.worker.call(() -> {
               List<ItemStack> stacks = Gui.containerNow();
               boolean matched = false;
               double cheapest = -1.0;

               for (int i = 0; i < stacks.size() && i < 45; i++) {
                  ItemStack stack = stacks.get(i);
                  if (product.matches(stack)) {
                     matched = true;
                     double total = ServerText.listingPrice(stack);
                     if (!(total <= 0.0)) {
                        double unit = total / Math.max(1, stack.getCount());
                        if (!this.ownListing(stock, stack, unit) && (cheapest < 0.0 || unit < cheapest)) {
                           cheapest = unit;
                        }
                     }
                  }
               }

               return new double[]{1.0, matched ? 1.0 : 0.0, cheapest};
            });
            this.gui.closeAll();
            this.worker.sleep(200L);
            return result;
         }
      }
   }

   private boolean ownListing(AuctionHouse.Stock stock, ItemStack stack, double unit) {
      for (String line : ServerText.lore(stack)) {
         if (!this.ignKey.isEmpty() && ServerText.key(line).contains(this.ignKey)) {
            return true;
         }
      }

      return stock.inAh > 0 && stock.price > 0L && Math.abs(unit - stock.price) < 1.0;
   }

   private boolean hasContent() {
      List<ItemStack> stacks = Gui.containerNow();
      if (stacks.size() > 49 && !stacks.get(49).isEmpty()) {
         return true;
      }

      for (int i = 0; i <= 10 && i < stacks.size(); i++) {
         if (!stacks.get(i).isEmpty()) {
            return true;
         }
      }

      return false;
   }

   private boolean isAhResultsNow() {
      if (!Gui.containerOpenNow()) {
         return false;
      } else {
         String title = ServerText.normalize(Gui.mc().currentScreen.getTitle());
         if (title.contains(LT.L14)) {
            return false;
         } else {
            return !title.contains(LT.L15) && !title.contains(LT.L16) && !title.equals(LT.L17) && !title.contains(LT.L18) ? isChest(Gui.slotNow(51)) : true;
         }
      }
   }

   private boolean isAhRootNow() {
      if (!Gui.containerOpenNow()) {
         return false;
      } else {
         return ServerText.normalize(Gui.mc().currentScreen.getTitle()).contains(LT.L14) ? false : isChest(Gui.slotNow(51));
      }
   }

   private static boolean isChest(ItemStack stack) {
      if (stack.isEmpty()) {
         return false;
      }

      String path = Registries.ITEM.getId(stack.getItem()).getPath();
      return path.equals(LT.L19) || path.equals(LT.L20);
   }

   private boolean openYourItems() {
      this.gui.closeAll();
      this.worker.sleep(200L);
      this.gui.command(LT.L13 + this.ign);
      if (!this.worker.waitFor(this::isAhRootNow, 10000L)) {
         return false;
      }

      for (int attempt = 0; attempt < 2; attempt++) {
         String before = this.gui.fingerprint();
         this.gui.click(51, 0, SlotActionType.PICKUP);
         if (this.gui.waitChange(before, 4000L)) {
            this.worker.sleep(200L);
            return this.worker.call(Gui::containerOpenNow);
         }
      }

      return false;
   }

   boolean refreshListings(List<AuctionHouse.Stock> stocks) {
      if (!this.openYourItems()) {
         this.gui.closeAll();
         return false;
      }

      int[] counts = this.worker.call(() -> {
         int[] found = new int[stocks.size()];

         for (ItemStack stack : Gui.containerNow()) {
            for (int ix = 0; ix < stocks.size(); ix++) {
               if (stocks.get(ix).product.matches(stack)) {
                  found[ix]++;
                  break;
               }
            }
         }

         return found;
      });
      this.gui.closeAll();
      this.worker.sleep(200L);

      for (int i = 0; i < stocks.size(); i++) {
         AuctionHouse.Stock stock = stocks.get(i);
         int gone = stock.inAh - counts[i];
         if (gone > 0 && stock.chatSales.getAndSet(0) == 0) {
            stock.sold.addAndGet(gone);
            stock.earned.addAndGet(gone * Math.max(0L, stock.price) * this.config.perListing());
            this.say(
               ModuleChat.body().good(LT.L21 + gone + LT2.L4).text(" ").name(stock.product.displayName()).text(LT.L22).value(stock.sold.get()).text(LT2.L2)
            );
         }

         stock.chatSales.set(0);
         stock.inAh = counts[i];
      }

      return true;
   }

   private int collect(AuctionHouse.Stock stock) {
      if (!this.openYourItems()) {
         this.say(ModuleChat.body().warn(LT.L23));
         this.gui.closeAll();
         return 0;
      }

      int collected = this.collectPass(stock.product, 50);
      if (collected > 0) {
         this.gui.closeAll();
         this.worker.sleep(500L);
         if (this.openYourItems()) {
            collected += this.collectPass(stock.product, 15);
         }
      }

      this.gui.closeAll();
      this.worker.sleep(200L);
      if (collected > 0) {
         this.say(
            ModuleChat.body()
               .text(LT.L24)
               .value(collected)
               .text(collected == 1 ? LT.L25 : LT.L26)
               .name(stock.product.displayName())
               .text(LT2.L3)
         );
      }

      return collected;
   }

   private int collectPass(Product product, int limit) {
      int collected = 0;
      Set<Integer> skipped = new HashSet<>();

      while (collected < limit && this.worker.call(Gui::containerOpenNow)) {
         int slot = this.worker.call(() -> {
            List<ItemStack> stacks = Gui.containerNow();

            for (int i = 0; i < stacks.size(); i++) {
               if (!skipped.contains(i) && product.matches(stacks.get(i))) {
                  return i;
               }
            }

            return -1;
         });
         if (slot < 0) {
            break;
         }

         long mark = System.currentTimeMillis();
         this.gui.click(slot, 0, SlotActionType.PICKUP);
         this.worker.ticks(3);
         if (this.alreadyBoughtAt >= mark) {
            skipped.add(slot);
         } else {
            collected++;
         }
      }

      return collected;
   }

   private void relist(AuctionHouse.Stock stock, long next) {
      this.refreshListings(List.of(stock));
      stock.price = next;
      this.collect(stock);
      stock.inAh = 0;
   }

   int listSome(AuctionHouse.Stock stock, int wanted, int[] totalInAh) {
      if (wanted <= 0) {
         return 0;
      }

      this.gui.closeAll();
      this.gui.closeInventory();
      int previous = this.worker.call(() -> Gui.player().getInventory().getSelectedSlot());
      int done = 0;
      int failures = 0;

      try {
         for (int i = 0; i < wanted; i++) {
            this.ensureAlive();
            int hotbar = this.stage(stock.product);
            if (hotbar < 0) {
               this.say(ModuleChat.body().warn(LT.L27).text(LT.L28));
               break;
            }

            AuctionHouse.Listing result = this.listFrom(stock, hotbar);
            if (result == AuctionHouse.Listing.FULL) {
               totalInAh[0] = Math.max(totalInAh[0], this.config.maxListings());
               this.say(ModuleChat.body().warn(LT.L29));
               break;
            }

            if (result == AuctionHouse.Listing.FAILED) {
               if (++failures >= 2) {
                  break;
               }
            } else {
               done++;
               stock.inAh++;
               totalInAh[0]++;
               stock.listed.incrementAndGet();
               if (this.config.listingDelayMs() > 0L && i < wanted - 1) {
                  this.worker.sleep(this.config.listingDelayMs());
               }
            }
         }
      } finally {
         this.worker.run(() -> {
            ClientPlayerEntity player = Gui.mc().player;
            if (player != null) {
               player.getInventory().setSelectedSlot(previous);
            }
         });
      }

      if (done > 0) {
         this.say(
            ModuleChat.body()
               .text(LT.L30)
               .value(done + LT2.L4)
               .text(" ")
               .name(stock.product.displayName())
               .text(LT.L31)
               .value(ServerText.shortMoney(stock.price * this.config.perListing()))
               .text(LT.L32)
               .value(totalInAh[0])
               .text(LT2.L2)
         );
      }

      return done;
   }

   private AuctionHouse.Listing listFrom(AuctionHouse.Stock stock, int hotbar) {
      Product product = stock.product;
      this.worker.run(() -> Gui.player().getInventory().setSelectedSlot(hotbar));
      this.worker.ticks(2);
      int before = this.worker.call(() -> Gui.player().getInventory().getStack(hotbar).getCount());
      long mark = System.currentTimeMillis();
      this.gui.command(LT.L33 + ServerText.exactMoney(stock.price * this.config.perListing()));
      long end = mark + 15000L;
      boolean[] confirmed = new boolean[]{false};
      Object[] pressedDialog = new Object[]{null};
      int[] pressedChest = new int[]{-1};

      while (System.currentTimeMillis() < end) {
         if (this.tooManyAt >= mark) {
            this.gui.closeAll();
            return AuctionHouse.Listing.FULL;
         }

         if (this.listedAt >= mark) {
            this.worker.sleep(150L);
            this.gui.closeAll();
            return AuctionHouse.Listing.LISTED;
         }

         if (this.errorAt >= mark) {
            this.gui.closeAll();
            return AuctionHouse.Listing.FAILED;
         }

         long elapsed = System.currentTimeMillis() - mark;
         int state = this.worker
            .call(
               () -> {
                  Dialogs.View view = Dialogs.current(Gui.mc());
                  if (view != null) {
                     if (view.dialog() != pressedDialog[0] && (view.titled(LT.L34) || view.titled(LT.L35) || view.titled(LT.L36))) {
                        Dialogs.Button yes = view.labelled(LT.L37, LT.L35);
                        if (yes != null && Dialogs.press(Gui.mc(), view, yes, Map.of())) {
                           pressedDialog[0] = view.dialog();
                           confirmed[0] = true;
                           return 1;
                        }
                     }

                     return 0;
                  } else if (Gui.containerOpenNow()) {
                     ClientPlayerEntity player = Gui.player();
                     int sync = player.currentScreenHandler.syncId;
                     ItemStack button = Gui.slotNow(15);
                     if (sync != pressedChest[0]
                        && !button.isEmpty()
                        && Registries.ITEM.getId(button.getItem()).getPath().contains(LT.L38)) {
                        Gui.mc().interactionManager.clickSlot(sync, 15, 0, SlotActionType.PICKUP, player);
                        pressedChest[0] = sync;
                        confirmed[0] = true;
                        return 1;
                     } else {
                        return 0;
                     }
                  } else {
                     ItemStack held = Gui.player().getInventory().getStack(hotbar);
                     boolean gone = !product.matches(held) || held.getCount() < before;
                     return !gone || !confirmed[0] && elapsed <= 2000L ? 0 : 2;
                  }
               }
            );
         if (state == 2) {
            this.gui.closeAll();
            return AuctionHouse.Listing.LISTED;
         }

         this.worker.sleep(state == 1 ? 250L : 50L);
      }

      this.gui.closeAll();
      this.say(ModuleChat.body().warn(LT.L39).text(LT2.L3));
      return AuctionHouse.Listing.FAILED;
   }

   private int stage(Product product) {
      int n = this.config.perListing();
      boolean clicked = false;

      try {
         for (int attempt = 0; attempt < 12; attempt++) {
            int[] plan = this.worker.call(() -> planStage(product, n));
            if (plan[0] == 0) {
               return plan[1];
            }

            if (plan[0] < 0) {
               return -1;
            }

            for (int i = 2; i < plan.length; i += 2) {
               this.gui.clickInventory(plan[i] >> 1, plan[i] & 1, plan[i + 1] == 1 ? SlotActionType.QUICK_MOVE : SlotActionType.PICKUP);
               clicked = true;
               this.worker.ticks(1);
            }
         }

         return -1;
      } finally {
         if (clicked) {
            this.gui.closeInventory();
            this.worker.ticks(4);
         }
      }
   }

   private static int[] planStage(Product product, int n) {
      ClientPlayerEntity player = Gui.player();
      PlayerInventory inventory = player.getInventory();
      ItemStack carried = player.playerScreenHandler.getCursorStack();
      if (!carried.isEmpty()) {
         int empty = emptyMain(inventory);
         return empty < 0 ? new int[]{-1} : new int[]{1, 0, click(menuSlot(empty), 0), 0};
      }

      for (int h = 0; h < 9; h++) {
         ItemStack stack = inventory.getStack(h);
         if (product.matches(stack) && stack.getCount() == n) {
            return new int[]{0, h};
         }
      }

      int target = -1;

      for (int h = 0; h < 9; h++) {
         ItemStack stack = inventory.getStack(h);
         if (product.matches(stack) && stack.getCount() < n) {
            target = h;
            break;
         }
      }

      if (target < 0) {
         for (int h = 0; h < 9; h++) {
            if (inventory.getStack(h).isEmpty()) {
               target = h;
               break;
            }
         }
      }

      if (target < 0) {
         int victim = -1;

         for (int h = 8; h >= 0; h--) {
            if (!product.matches(inventory.getStack(h))) {
               victim = h;
               break;
            }
         }

         if (victim < 0) {
            victim = 8;
         }

         return emptyMain(inventory) < 0 ? new int[]{-1} : new int[]{1, 0, click(menuSlot(victim), 0), 1};
      } else {
         int have = product.matches(inventory.getStack(target)) ? inventory.getStack(target).getCount() : 0;
         int source = -1;
         int best = 0;

         for (int index = 0; index < 36; index++) {
            if (index != target) {
               ItemStack stack = inventory.getStack(index);
               if (product.matches(stack) && stack.getCount() > best) {
                  best = stack.getCount();
                  source = index;
               }
            }
         }

         if (source < 0) {
            return new int[]{-1};
         }

         int need = n - have;
         int[] plan = new int[2 + 2 * (need + 2)];
         int at = 2;
         plan[0] = 1;
         plan[at++] = click(menuSlot(source), 0);
         plan[at++] = 0;
         if (best <= need) {
            plan[at++] = click(menuSlot(target), 0);
            plan[at++] = 0;
         } else {
            for (int i = 0; i < need; i++) {
               plan[at++] = click(menuSlot(target), 1);
               plan[at++] = 0;
            }

            plan[at++] = click(menuSlot(source), 0);
            plan[at++] = 0;
         }

         return Arrays.copyOf(plan, at);
      }
   }

   private static int click(int slot, int button) {
      return slot << 1 | button;
   }

   private static int menuSlot(int inventoryIndex) {
      return inventoryIndex < 9 ? 36 + inventoryIndex : inventoryIndex;
   }

   private static int emptyMain(PlayerInventory inventory) {
      for (int index = 9; index < 36; index++) {
         if (inventory.getStack(index).isEmpty()) {
            return index;
         }
      }

      return -1;
   }

   int countInventory(Product product) {
      return this.worker.call(() -> {
         ClientPlayerEntity player = Gui.mc().player;
         if (player == null) {
            return 0;
         }

         int total = 0;

         for (int index = 0; index < 36; index++) {
            ItemStack stack = player.getInventory().getStack(index);
            if (product.matches(stack)) {
               total += stack.getCount();
            }
         }

         return total;
      });
   }

   boolean inventoryCapReached(Product product) {
      return this.worker.call(() -> {
         PlayerInventory inventory = Gui.player().getInventory();
         int used = 0;
         int empty = 0;

         for (int index = 0; index < 36; index++) {
            ItemStack stack = inventory.getStack(index);
            if (stack.isEmpty()) {
               empty++;
            } else if (stack.isOf(product.item())) {
               used++;
            }
         }

         return used >= this.config.collectSlots() || empty <= 0;
      });
   }

   void ensureAlive() {
      if (this.worker.call(() -> Gui.mc().player != null && Gui.mc().player.isDead())) {
         this.say(ModuleChat.body().danger(LT.L40).text(LT.L41));
         long end = System.currentTimeMillis() + 30000L;
         long next = System.currentTimeMillis() + 700L;

         while (System.currentTimeMillis() < end) {
            boolean dead = this.worker.call(() -> Gui.mc().player != null && Gui.mc().player.isDead());
            if (!dead) {
               break;
            }

            if (System.currentTimeMillis() >= next) {
               this.worker.run(() -> {
                  ClientPlayerEntity player = Gui.mc().player;
                  if (player != null) {
                     player.requestRespawn();
                  }
               });
               next = System.currentTimeMillis() + 1500L;
            }

            this.worker.sleep(100L);
         }

         this.worker.run(() -> {
            if (Gui.mc().currentScreen instanceof DeathScreen) {
               Gui.mc().setScreen(null);
            }
         });
         throw new AuctionHouse.Problem(LT.L42);
      }
   }

   void waitSalesCheck() {
      long until = System.currentTimeMillis() + this.config.salesCheckMs();

      while (System.currentTimeMillis() < until) {
         this.worker.sleep(Math.min(1000L, until - System.currentTimeMillis()));
      }
   }

   String onChat(String line, List<AuctionHouse.Stock> stocks) {
      long now = System.currentTimeMillis();
      if (line.contains(LT.L43)) {
         this.tooManyAt = now;
      }

      if (line.contains(LT.L44)) {
         this.alreadyBoughtAt = now;
      }

      if (line.startsWith(LT.L45)) {
         this.listedAt = now;
      }

      if (line.contains(LT.L46) || line.contains(LT.L47) || line.contains(LT.L48)) {
         this.errorAt = now;
      }

      int bought = line.indexOf(LT.L49);
      if (bought <= 0) {
         return null;
      }

      String rest = line.substring(bought + LT.L49.length());
      int forAt = rest.lastIndexOf(LT.L50);
      String itemPart = forAt > 0 ? rest.substring(0, forAt) : rest;
      AuctionHouse.Stock stock = null;

      for (AuctionHouse.Stock candidate : stocks) {
         if (candidate.product.nameMatches(itemPart, false)) {
            stock = candidate;
            break;
         }
      }

      if (stock == null) {
         for (AuctionHouse.Stock candidate : stocks) {
            if (candidate.product.nameMatches(itemPart, true)) {
               stock = candidate;
               break;
            }
         }
      }

      if (stock == null) {
         return null;
      }

      List<Double> money = forAt > 0 ? ServerText.moneyAll(rest.substring(forAt)) : List.of();
      long amount = money.isEmpty() ? 0L : Math.round(money.get(0));
      stock.sold.incrementAndGet();
      stock.chatSales.incrementAndGet();
      stock.earned.addAndGet(amount);
      this.say(
         ModuleChat.body()
            .good(LT.L51)
            .text(" ")
            .name(stock.product.displayName())
            .text(amount > 0L ? LT.L52 + ServerText.shortMoney(amount) : "")
            .text(LT.L22)
            .value(stock.sold.get())
            .text(LT2.L5)
            .value(ServerText.shortMoney(stock.earned.get()))
            .text(LT2.L2)
      );
      return stock.product.displayName();
   }

   static String summary(List<AuctionHouse.Stock> stocks) {
      int listed = 0;
      int sold = 0;
      long earned = 0L;

      for (AuctionHouse.Stock stock : stocks) {
         listed += stock.listed.get();
         sold += stock.sold.get();
         earned += stock.earned.get();
      }

      return LT.L53 + listed + LT.L54 + sold + LT.L55 + ServerText.shortMoney(earned) + LT2.L3;
   }

   record Config(
      long undercut,
      long priceCheckMs,
      long salesCheckMs,
      int perListing,
      int maxListings,
      long listingDelayMs,
      double relistGap,
      double ignoreDrops,
      int collectSlots,
      long orderCheckMs
   ) {
   }

   private enum Listing {
      LISTED,
      FULL,
      FAILED;
   }

   static final class Problem extends RuntimeException {
      Problem(String message) {
         super(message, null, false, false);
      }
   }

   static final class Stock {
      final Product product;
      final boolean fixedPrice;
      long price = -1L;
      long lastPriceCheck;
      int inAh;
      final AtomicInteger listed = new AtomicInteger();
      final AtomicInteger sold = new AtomicInteger();
      final AtomicInteger chatSales = new AtomicInteger();
      final AtomicLong earned = new AtomicLong();

      Stock(Product product, boolean fixedPrice) {
         this.product = product;
         this.fixedPrice = fixedPrice;
         if (fixedPrice) {
            this.price = product.minPrice();
         }
      }
   }


    // cloak literals - runtime PUA carriers (cloak) or functional plain (LT)
    private static final class LT {
            static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x49b8,0x2e6a,0x1fdf,0x9a08,0xd1af,0x2f20,0x872d,0x6c89,0x9f88,0x9c48,0x4f85,0xb29f,0x0722,0x6e87,0xa9cf,0x15cd,0xb03c,0x9adb,0x3799,0x5467,0x33ae,0x6196,0x62cd,0x5d9d}, new char[]{0x62a4,0x9c2f,0x9fe6,0x5408,0x875f,0x61ff,0x6ce7,0x0745,0x2f00,0x2e0b,0x15ed,0x37fd,0xd1ca,0x6ee2,0xa9bf,0x338e,0x49cd,0xb052,0x9a6c,0x9abe,0x5def,0x4fe6,0xb2ea,0x1fab}).plainString();
            static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc93a,0x9132,0x39de,0xa6db,0x4943}, new char[]{0x39fe,0xc94e,0x492c,0x9112,0xa6ff}).plainString();
            static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf270,0x01fd,0x0583,0x72d1,0xc7b4,0xa679,0xe443,0x327d}, new char[]{0x05a3,0xf258,0xc7d0,0xe431,0xa616,0x72a1,0x01dd,0x3259}).plainString();
            static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5888,0x96e6,0xec30,0x1163,0xaac3,0xcd2b,0xd5fe,0x77b8}, new char[]{0xec45,0x58e6,0xcd4f,0xaaa6,0x9694,0xd59d,0x77cd,0x1117}).plainString();
            static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x510e,0x2580,0x2407,0x47cd,0xfd8a,0xbaef,0x8d47,0x3cd3,0xa633,0xd806,0x7a12,0xea17,0x281f,0x3f8f,0xd62a,0x7f03}, new char[]{0x242b,0x7f23,0x3ca1,0x516b,0xfde6,0xa65a,0x286c,0xba9b,0x3fe6,0xea79,0x47aa,0x25a0,0xd867,0xd65e,0x7a32,0x8d63}).plainString();
            static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1159,0x01a8,0xe2f2,0x1647,0x8730,0x701e,0xf666,0x41ef,0x88f1,0x9c9a,0x8449,0xd369,0xffa3,0xce81,0x09ba,0x8a4a,0x1455,0x6a63,0xb087,0x945b}, new char[]{0x8473,0xe2d2,0x09ca,0x1635,0x1130,0x8a29,0xb0e2,0x41cf,0xd31e,0x8894,0x01c6,0x9cee,0x703e,0x8745,0xcef1,0x6a43,0xf612,0x143a,0x947b,0xff87}).plainString();
            static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9a11,0xebfd,0x0119,0x4f57,0xc407,0xd6a3,0x05e7,0xddd8,0xae77,0x6fb8,0x1e17,0xbecd,0x1279,0x016f,0x7242}, new char[]{0x9a2b,0x7262,0xc464,0xeb95,0x121c,0x1e76,0xdda8,0x6fdd,0xae04,0xbeb9,0x014f,0xd6ca,0x016a,0x05c7,0x4f73}).plainString();
            static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe151,0x3635,0xe527,0xb3a8,0xd202,0x4381,0xa237,0x7f5b,0x3a3f,0x9d7d,0xcbfc,0xb7c4,0x7ffa,0x76d5}, new char[]{0xd22e,0x7fda,0xb7b7,0x7f3e,0xe54b,0x43ed,0xb3c1,0x3a51,0x9d1a,0x3615,0xe130,0xa243,0x76f5,0xcbd8}).plainString();
            static final String L9 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x154e,0xb911,0x7dc5,0xbedc,0x438c,0x0272}, new char[]{0x156e,0x43e9,0x7da4,0xb972,0x021a,0xbef2}).plainString();
            static final String L10 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xcec8,0x40f1,0x77ac,0xfaa7,0xc67a,0xf366,0xa44c,0x5391,0x18af,0x0cf6,0x634d,0x2143,0xc629,0xd0f2,0xb25d,0x48ca,0x1b05,0xb5b8,0x847a,0x056c,0x66c9,0xbc52,0xb2d2,0xe92e,0x261d,0x39be,0x4d2e,0xb2b1,0xcd66,0x1ff4,0x494c,0x6e87,0xbfff}, new char[]{0x1fce,0x6ea7,0x6323,0xbc3d,0xf346,0xcea7,0xd086,0xb5d0,0x77c9,0xb2c3,0x054c,0xc616,0x2674,0x4082,0x66bd,0xcd0f,0x39d0,0xb2b5,0x0c85,0xe902,0x2163,0x53e2,0xa429,0xbf93,0x1b69,0x4925,0xfac9,0x48ad,0xc609,0x18ce,0x4d5a,0x845a,0xb279}).plainString();
            static final String L11 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3a0b,0xdae0,0xed2a,0x0c39,0xb31c,0xe4b0,0xd60d,0x9c3f,0xb209,0xc79c,0xc878,0x6176,0x8573,0x4eca,0x87fb,0x359d,0x9ef3,0x2a6c,0x464a,0xea3b,0xbc6f,0xb50a,0x7b97,0x5f7c}, new char[]{0x4ea9,0xed45,0xbc1a,0xb370,0xda84,0xc7bc,0x6118,0x9c50,0x2a18,0x3a2b,0xe4c2,0x0c5c,0x7bf6,0xc81c,0x87db,0x8532,0xb241,0x9ed3,0x463a,0xea49,0x35f4,0xb569,0xd668,0x5f0f}).plainString();
            static final String L12 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7065,0xe8d1,0x7cf6,0x84f3,0xa211,0xbf08,0xbfd1,0xa51f,0xeaa5,0x8418}, new char[]{0xa23d,0xa53f,0x8484,0xbf69,0xe8b8,0xead1,0xbfb8,0x8476,0x7002,0x7cd8}).plainString();
            static final String L13 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5c3f,0x90ec,0xd8c5}, new char[]{0x5c5e,0xd8ad,0x90cc,0xaebd}).plainString();
            static final String L14 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2db4,0x7eb8,0x4b14,0xc115,0x2b44}, new char[]{0xc17a,0x2dc6,0x2b20,0x4b71,0x7eca}).plainString();
            static final String L15 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x91b8,0xe55f,0x12f3,0x99f1,0xca00,0xfb55,0x7d68}, new char[]{0xca61,0xe52a,0x9992,0x91cc,0x7d01,0x129c,0xfb3b}).plainString();
            static final String L16 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xbe5d,0x9e57,0xb468,0x2b03,0xec9a,0x08d4}, new char[]{0x08b9,0x9e36,0xece8,0x2b68,0xbe38,0xb41c}).plainString();
            static final String L17 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xfa67,0xb4a2}, new char[]{0xb4c3,0xfa0f,0x3c1c,0xc968}).plainString();
            static final String L18 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5e1e,0xa37d,0xb6f1,0xac07}, new char[]{0xb6d1,0xac66,0xa315,0x5e3e}).plainString();
            static final String L19 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xcb40,0x0a54,0x8fd1,0xb20a,0x3524}, new char[]{0x0a37,0x354c,0xb26f,0x8fa2,0xcb34}).plainString();
            static final String L20 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf901,0xa02e,0x0b2a,0x8950,0xc37c,0xc374,0xd7a7,0xde1d,0xf51b,0xc252,0xb691,0xfe87,0xb69b}, new char[]{0xa05a,0xb6e3,0xc31d,0xf971,0xc222,0x0b4f,0xd7c3,0xf544,0xc317,0x8938,0xde78,0xfef4,0xb6ef}).plainString();
            static final String L21 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe2a3,0x3d95,0x9690,0xe50f,0x4f68}, new char[]{0xe2f0,0x3dfa,0xe563,0x96f4,0x4f48}).plainString();
            static final String L22 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd412,0x0b4c,0xdaf3,0xbd57,0x835b,0x5fc0,0x3aee,0x31b4}, new char[]{0x837b,0xbd7f,0xda87,0xd47d,0x3a9a,0x5fa1,0x0b20,0x3194}).plainString();
            static final String L23 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8e9f,0xdb3d,0x259d,0x9041,0x5108,0x9ba3,0x4eba,0x5f50,0x91e6,0x5d28,0x536e,0x85fe,0x6228,0x8dfa,0xc135,0x616b,0x523e,0x86e0,0x0c7a,0x2330,0xfd6f,0x0038,0x4fa8,0x6869,0x9ec0,0x3c84}, new char[]{0xc176,0x5251,0x517d,0x9eac,0x5d4c,0x5f70,0x0056,0x868f,0x2344,0x4f88,0x8d95,0x0c0a,0x680c,0x902f,0x6208,0x5337,0x25f2,0x611e,0x858c,0x9b83,0x3ccd,0x4ece,0x9183,0xdb50,0x8eec,0xfd41}).plainString();
            static final String L24 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe3d3,0x904a,0x4aac,0x24ac,0x8905,0x1619,0x815b,0xc421,0x6ecc,0x145a}, new char[]{0x4af8,0x896a,0x8134,0x9021,0x147a,0xc443,0xe3b2,0x6eaf,0x1672,0x248c}).plainString();
            static final String L25 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xdcd1,0x22de,0x2991,0xde98,0xaf81,0x0589,0xdddc,0xed26,0xb741,0xe2f2,0xd7a2,0x627b}, new char[]{0xd782,0xb72d,0xed4f,0xddaf,0xe286,0x6212,0x22b0,0xafe6,0xdcf1,0x29fe,0x05ef,0xdeb8}).plainString();
            static final String L26 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2aa1,0xe6cf,0x1dac,0xb69c,0xf509,0x0b42,0xd1ae,0x679f,0xa4a9,0xab9d,0x0e44,0x3a0b,0x67a6}, new char[]{0xe6ef,0x1dc0,0x3a62,0xabee,0xd1da,0xf560,0x2acf,0x0e23,0xb6ef,0xa489,0x67f0,0x67c0,0x0b62}).plainString();
            static final String L27 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x46db,0x5fe7,0x57df,0x6095,0x2590,0xcf22,0xa041,0x33f4,0x648b,0x4de3,0xff33,0x066c,0x4882,0xfbd1,0xa61d,0x5097,0x96dc,0xb6bf,0x7ece,0x1aa9,0x616c,0x3107,0x8612,0xb9b3,0xa428,0xb30b,0x7333,0xdd7b,0x4ba7,0xd69e,0x3e8c}, new char[]{0x25d3,0x6103,0xcf57,0x46b7,0xa025,0xfbf1,0x867c,0x3ee3,0x7347,0x7eee,0xb37b,0x4d91,0x60f0,0xff43,0x64ea,0x57ad,0x5f82,0x3127,0xd6ff,0x33d4,0xb6d7,0x48ed,0x4bd3,0x96be,0xb9d2,0x1adb,0xa63d,0xa45b,0xdd17,0x0603,0x50e3}).plainString();
            static final String L28 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3d18,0x5672,0x0c8c,0xb2ab,0xbcda,0x2eb5,0x7122,0xf82d,0x06e3,0x470b,0xc65e,0x8b3a,0xd5f8,0x336c,0xa469,0x7433,0x2e5a,0xf52b,0xc5ce}, new char[]{0x06c3,0xf805,0xb2c2,0xc5a0,0xf55d,0x8b5f,0xbcb4,0xd58c,0xc631,0x4779,0x744a,0x3d38,0x330a,0x0cf9,0x2ed9,0xa405,0x2e65,0x710b,0x565c}).plainString();
            static final String L29 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6e99,0x532b,0xf6c4,0x2e19,0x5353,0x8f85,0x4d53,0x6344,0xfbc2,0x1bee,0xc9a3,0x37e0,0x3eed,0xc2fd,0x8104,0x6ae9,0x96a4,0x0c0f,0x284b,0xe296,0x8783,0xd762,0xbe0e,0x9e04,0x0236,0xe814,0x605c,0x2f53,0x3847,0x83ff,0x5d5a,0x67cd,0x6074,0x5ecf,0x7e52,0x4a12,0xed1e,0x5f66,0x9043}, new char[]{0xd736,0x96cc,0x3e88,0x83df,0x87c2,0xed56,0x8124,0x3834,0x282a,0x8ffc,0xc9d0,0x6054,0x532a,0x4a7d,0x6a9c,0x9063,0x0c67,0xf6a5,0x602a,0x67a8,0x37c0,0x5d2e,0xe87b,0xfbad,0xbe2e,0x6329,0x4d32,0x5ea1,0x5f1f,0x0216,0x9e68,0xe2ff,0x5358,0xc289,0x6ef0,0x2f3d,0x1b89,0x7e21,0x2e37}).plainString();
            static final String L30 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2a11,0x9ff5,0x8dde,0x536b,0x7ede,0x679f,0xf3a3}, new char[]{0x67d3,0xf3ca,0x8dad,0x7eaa,0x2a74,0x9f91,0x534b}).plainString();
            static final String L31 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4173,0x1477,0x7782,0x9e8c,0x6376}, new char[]{0x77a2,0x1416,0x4107,0x6356,0x9ea8}).plainString();
            static final String L32 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5a32,0xb08d,0xffe4,0x7414,0x9dce,0xab18,0xd472,0x9b98,0x1c8c}, new char[]{0xd452,0x5a1a,0x9da7,0x747a,0x1cac,0xab59,0xb0c5,0x9ba2,0xffc4}).plainString();
            static final String L33 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb5ae,0x4f94,0x20b3,0x6b90,0xa2a3,0x61f1,0x7ee9,0x540d}, new char[]{0x20d2,0x6199,0x6bb0,0xb5dd,0x4ff1,0xa2cf,0x5461,0x7ec9}).plainString();
            static final String L34 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9fea,0xd969,0x8de0,0x0714}, new char[]{0x0767,0x8d95,0xd91b,0x9f8f}).plainString();
            static final String L35 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3d0c,0xfcff,0x9cfd,0xf072,0x7cc5,0xba1b,0xd7e7}, new char[]{0x9c9e,0x7caa,0xf01c,0x3d6a,0xd78e,0xfc8d,0xba76}).plainString();
            static final String L36 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb4f7,0x4521,0xc2ba,0xa6cc}, new char[]{0xa6bf,0xb492,0x454d,0xc2d6}).plainString();
            static final String L37 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x29c8,0xdacf,0x55a8}, new char[]{0x29b1,0xdaaa,0x55db,0x5390}).plainString();
            static final String L38 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6a4f,0x2de0,0x824b,0xa7d6}, new char[]{0xa7ba,0x2d89,0x6a22,0x822e}).plainString();
            static final String L39 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xaa69,0x4034,0x0ff6,0xa1bb,0x9cbc,0x20ab,0x55a5,0xb82e,0x90f5,0x57b5,0x4966,0x568f,0xcd7e,0x8a60,0x643a,0x9315,0x381b}, new char[]{0x20e7,0x490f,0x9086,0x9cc8,0xcd17,0x55cb,0x56e8,0x5795,0x4040,0x6453,0xb843,0x0f93,0x8a04,0x383b,0x937a,0xa1ce,0xaa1d}).plainString();
            static final String L40 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xdbab,0xf718,0xc409,0x2133,0x2540,0x855b,0x7ce5,0x31aa}, new char[]{0x216a,0xdbc4,0x7c90,0x2560,0x31ce,0x8532,0xc46c,0xf77c}).toString();
            static final String L41 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3304,0x5caf,0xc8e7,0x43f2,0xf4b0,0xc3bb,0x0e29,0x9ee7,0x4b9d,0xc661,0x7e12,0x80ce,0x4cf4}, new char[]{0x80e2,0x9ec7,0x5cdd,0x0e4c,0x7e61,0x4382,0x4bfc,0xc890,0xc60f,0x336d,0xf4de,0xc3dc,0x4cda}).plainString();
            static final String L42 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2888,0xdfb3,0x0609,0xcf6c,0x76e9,0x3c27,0xa897,0xb615,0x0be5}, new char[]{0xa8c5,0xcf09,0x067a,0x0b95,0xdfd2,0xb662,0x3c49,0x28ed,0x768d}).toString();
            static final String L43 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7d66,0x409f,0x87d2,0x5e0e,0x8aef,0x99fe,0x7ce2,0x83cb,0xf93c,0x0c0d,0xeca6,0x0937,0x4d9f,0xa697,0x3400,0xdfe5,0x6ad9,0xe890,0x935b,0xcbc1,0x0ab9}, new char[]{0xecd2,0x87bd,0x7c8d,0x5e2e,0x83a6,0xa6f6,0x0c63,0x40e6,0x937b,0xf950,0x3469,0x0944,0x7d12,0x4dfa,0xcba5,0x0a99,0x9997,0xdf91,0xe8f5,0x8a82,0x6aaa}).plainString();
            static final String L44 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0c17,0x3d7a,0x5b07,0x188b,0x8e3e,0x6d9e,0x86f6,0x9831,0xc8d3,0xaae4,0x67bd,0xeac3,0x2bd6,0x6e0e}, new char[]{0x6e6f,0xc8bf,0x6dec,0x3d1f,0x0c76,0xeaa7,0x9848,0x86d6,0x18e9,0xaa8b,0x2ba3,0x8e59,0x67d5,0x5b73}).plainString();
            static final String L45 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2451,0xb8f0,0x198d,0x4ae9,0x0926,0x77bc,0xf91e,0xab9a,0x4a12,0xa6f1}, new char[]{0x19f4,0x4a86,0xa684,0x779c,0x243d,0x4a7b,0x0955,0xf96a,0xb895,0xabfe}).plainString();
            static final String L46 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xcf09,0x3c92,0xbe50,0xd6d1,0x852f,0x5a63,0xea6d,0x7ebf,0xd68f,0x5af0,0x4aeb,0x4f40,0x8be0,0xef94,0x8b05,0xe97b,0x4c07,0xec98}, new char[]{0x3ceb,0x5a9f,0xd6fa,0xefb4,0xbe33,0x8b81,0x7ed1,0x8b6b,0xd6be,0xcf7d,0x5a43,0x4c63,0xea02,0x4acb,0x4f34,0xe913,0xecf1,0x855c}).plainString();
            static final String L47 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf541,0xbb2b,0x643f,0x56e0,0x441d,0xf050,0xe3da,0x2b2c,0x2293,0xbb43,0x3614,0x2c88,0x1eea,0x4cea,0xfcda,0x6168,0x1c89,0x97b0,0x151f,0x6d5e,0x151f,0x8324,0x02c5,0xfd3c}, new char[]{0x2b55,0x1e85,0x6d2b,0xfd1c,0x1571,0x1cec,0xbb26,0xbb4f,0x9790,0xf535,0x6450,0x6148,0x2cff,0x8345,0xe3b3,0x4469,0xf070,0x157e,0x22fd,0x4c85,0xfcae,0x367c,0x5685,0x02b7}).plainString();
            static final String L48 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xefb8,0x2288,0xcddd,0x2295,0xa4ee,0x6a36,0xfd7b,0x4209,0x180d,0xd47d,0xc646,0xd3e8,0xf4e8,0x207e,0xee73,0x33b7,0xfd6f}, new char[]{0xd419,0x2011,0x22fb,0xc661,0x22fc,0xcdfd,0x33df,0xf489,0xa498,0xd38d,0x182d,0xee16,0xfd15,0x6a59,0xfd1a,0xefdf,0x4261}).plainString();
            static final String L49 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8052,0x2338,0xb338,0xe509,0x08af,0xf965,0x8103,0xc081,0xb222,0xb37c,0x11a5,0x8f6a,0x9cf4}, new char[]{0x9cd4,0xb31e,0x8f05,0xc0f4,0xb245,0xf90d,0xb34c,0x088f,0x11dc,0xe566,0x8027,0x8171,0x2318}).plainString();
            static final String L50 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2c3f,0xbc49,0xf172,0xb6e6,0x5bad}, new char[]{0x2c1f,0xb680,0x5bc2,0xbc3b,0xf152}).plainString();
            static final String L51 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x754d,0xbc6d,0xd2f4,0x946a}, new char[]{0x9439,0x7522,0xd298,0xbc09}).plainString();
            static final String L52 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x541b,0xde50,0x572d,0x754d,0x0377,0x2329}, new char[]{0x756d,0xde36,0x5742,0x5469,0x2309,0x0353}).plainString();
            static final String L53 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe797,0x2f1e,0x083a,0x6ffe,0xd0a4,0x1225,0x8375}, new char[]{0xd0e8,0xe7fe,0x1256,0x2f6a,0x085f,0x8311,0x6fde}).toString();
            static final String L54 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc525,0x7f1c,0x9bd1,0x31e7,0xd340,0xb17f,0x8f18}, new char[]{0xd36c,0xb15f,0x9ba2,0x3188,0x7f70,0xc541,0x8f38}).plainString();
            static final String L55 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb9f1,0x2b7e,0x4397,0xf964,0x9eca,0x3c21,0x16a6,0xe4d0,0x79b7,0x7b0d,0x27b5}, new char[]{0x3c0d,0x1686,0xb994,0xf905,0x9eb8,0x43f9,0x27d0,0x79d3,0xe4f0,0x7b73,0x2b5a}).plainString();
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT2 {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6089,0xc9ef}, new char[]{0xc9d5,0x60a9,0xd2f6,0x4749}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8a07,0xc53f}, new char[]{0x8a2e,0xc511,0x4ee8,0xd514}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb54f}, new char[]{0xb561,0x5a0d,0x26fc,0xd646}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb1d1}, new char[]{0xb1a9,0xb1b6,0x1fc2,0x98e6}).plainString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x887a,0xe4e2,0x3ad5,0x928a}, new char[]{0x92a6,0xe4c2,0x8804,0x3af1}).plainString();
    }
}
