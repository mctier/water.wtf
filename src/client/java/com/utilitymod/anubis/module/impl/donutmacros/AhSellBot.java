package com.utilitymod.anubis.module.impl.donutmacros;

import java.util.ArrayList;
import java.util.List;
import com.utilitymod.anubis.chat.ModuleChat;

final class AhSellBot {
   private static final int MAX_LISTINGS_PER_PASS = 9;
   private static final long RECOVERY_MS = 3000L;
   private final AuctionHouse ah;
   private final MacroWorker worker;
   private final AuctionHouse.Config config;
   private final List<AuctionHouse.Stock> stocks;
   private final int[] inAh = new int[]{0};
   private boolean warnedFull;
   private boolean warnedEmpty;

   AhSellBot(AhSellModule module, MacroWorker worker, List<Product> products, boolean fixed, AuctionHouse.Config config, String ign) {
      this.ah = new AuctionHouse(module, worker, config, ign);
      this.worker = worker;
      this.config = config;
      List<AuctionHouse.Stock> list = new ArrayList<>();

      for (Product product : products) {
         list.add(new AuctionHouse.Stock(product, fixed));
      }

      this.stocks = List.copyOf(list);
   }

   void run() {
      List<String> names = new ArrayList<>();

      for (AuctionHouse.Stock stock : this.stocks) {
         names.add(stock.product.displayName() + (stock.fixedPrice ? LT2.L1 + ServerText.shortMoney(stock.price) + LT2.L2 : ""));
      }

      this.ah.say(ModuleChat.body().text(LT.L1).name(String.join(LT2.L3, names)).text(LT2.L4));

      try {
         this.ah.gui.closeAll();
         if (this.ah.refreshListings(this.stocks)) {
            this.inAh[0] = this.total();
         }
      } catch (MacroWorker.Cancelled cancelled) {
         throw cancelled;
      } catch (RuntimeException failure) {
         this.ah.gui.closeAll();
      }

      while (true) {
         this.worker.check();

         try {
            this.cycle();
         } catch (MacroWorker.Cancelled cancelled) {
            throw cancelled;
         } catch (RuntimeException failure) {
            String reason = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
            this.ah.say(ModuleChat.body().warn(reason).text(LT.L2));
            this.worker.check();
            this.ah.gui.closeAll();
            this.worker.sleep(3000L);
         }
      }
   }

   private void cycle() {
      if (!this.ah.inWorld()) {
         this.worker.sleep(2000L);
      } else {
         this.ah.ensureAlive();
         boolean listedAny = false;
         boolean anyInInventory = false;

         for (AuctionHouse.Stock stock : this.stocks) {
            this.worker.check();
            int count = this.ah.countInventory(stock.product);
            if (count >= this.config.perListing() || stock.inAh != 0) {
               this.ah.ensurePrice(stock);
               if (stock.price > 0L && count >= this.config.perListing()) {
                  anyInInventory = true;
                  this.inAh[0] = this.total();
                  int room = this.config.maxListings() - this.inAh[0];
                  if (room <= 0) {
                     if (!this.warnedFull) {
                        this.ah.say(ModuleChat.body().text(LT.L3).value(this.inAh[0] + LT2.L5 + this.config.maxListings()).text(LT.L4));
                     }

                     this.warnedFull = true;
                     break;
                  }

                  this.warnedFull = false;
                  int wanted = Math.min(Math.min(count / this.config.perListing(), room), 9);
                  if (this.ah.listSome(stock, wanted, this.inAh) > 0) {
                     listedAny = true;
                  }
               }
            }
         }

         if (!anyInInventory && this.total() == 0) {
            if (!this.warnedEmpty) {
               this.ah.say(ModuleChat.body().text(LT.L5));
            }

            this.warnedEmpty = true;
         } else {
            this.warnedEmpty = false;
         }

         if (listedAny && this.total() < this.config.maxListings() && this.moreToList()) {
            this.worker.sleep(500L);
         } else {
            this.ah.waitSalesCheck();
            if (this.ah.refreshListings(this.stocks)) {
               this.inAh[0] = this.total();
            }
         }
      }
   }

   private boolean moreToList() {
      for (AuctionHouse.Stock stock : this.stocks) {
         if (stock.price > 0L && this.ah.countInventory(stock.product) >= this.config.perListing()) {
            return true;
         }
      }

      return false;
   }

   private int total() {
      int sum = 0;

      for (AuctionHouse.Stock stock : this.stocks) {
         sum += stock.inAh;
      }

      return sum;
   }

   void onChat(String raw) {
      String line = ServerText.normalize(raw);
      if (!line.isEmpty()) {
         this.ah.onChat(line, this.stocks);
      }
   }

   String summary() {
      return AuctionHouse.summary(this.stocks);
   }


    // cloak literals - runtime PUA carriers (cloak) or functional plain (LT)
    private static final class LT {
            static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe92a,0x6518,0xf18d,0x72ca,0x28c6,0x9eff,0x76e0,0x7478}, new char[]{0x9eac,0x741d,0x6574,0xe946,0x7689,0x28a8,0x72ad,0xf1ad}).plainString();
            static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x60dd,0x772b,0x8105,0xb68e,0x9821,0x9bed,0x740a,0x29b5,0xb155,0x9b6b,0x4e89,0xe80d,0x209d,0xdbf8,0xb50b}, new char[]{0x20b1,0xb175,0x60a9,0x9b9f,0x29cc,0xdb91,0xb6e0,0x9846,0xb52b,0xe86c,0x4eee,0x8164,0x7463,0x9b05,0x7705}).plainString();
            static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x87ff,0xab33,0x897e,0x09fb,0xed0e,0xde85,0x9e66,0x4828,0xe45c}, new char[]{0xab72,0xe414,0x87df,0x099d,0x9e13,0xdee9,0x8912,0xed2e,0x4800}).plainString();
            static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7359,0x7ba7,0xbbd3,0x10e3,0x7849,0x65ee,0x8c82,0x818b,0x977e,0x2e3b,0xd71d,0x6a34,0xd8f4,0x10e0,0x8019,0x0366,0x4a8c,0x612f,0x8cbe,0x868b,0x5cef}, new char[]{0x5cc6,0xd731,0x975e,0x81fc,0x86ea,0x6a5d,0x0312,0x6587,0x7827,0x7bc0,0x7379,0x807f,0x2e54,0x4afe,0x610f,0x8ccd,0x8ce3,0x108f,0xd891,0xbba0,0x10ce}).plainString();
            static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x46db,0x23c5,0x9162,0xabae,0xe634,0xad49,0xac2e,0x6fdb,0xf6ee,0xbb38,0x7c72,0xe0ea,0xf546,0x7212,0xfa75,0xdf50,0xb016,0xb0bf,0xd5a3,0x21a1,0xbfbc,0x0765,0x0ec0,0xd9ee,0x0df5,0x2d86,0x7f0a,0xab9d,0x7c01,0xc593,0x80b6,0x5dff,0x5ef1,0xad17,0xfc8d,0x0bee,0xbaa7,0x161f,0x05ad,0x5b40,0xf5ae,0xeb9f,0xac5d,0x0a5c,0x3dfb}, new char[]{0xdf1e,0xfce2,0xac5a,0x2dee,0x7c68,0x80d8,0xf689,0xf58e,0xad3d,0xad78,0x5ed1,0xabdd,0xf523,0x5d93,0x0709,0x5b60,0xe646,0x05c4,0xac3a,0x1677,0x7266,0x6ffb,0xfa1b,0xabf2,0x3d8c,0x0dd9,0x0bce,0xb061,0x21c0,0x7f63,0xe09e,0x0ea9,0xd980,0x0a3b,0xb09f,0xd5c5,0xbac8,0x23b7,0xbb18,0x910b,0x46af,0xc5f6,0xebf2,0xbfcf,0x7c5c}).plainString();
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT2 {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4efe,0xa862,0x891e}, new char[]{0x4ede,0xa84a,0x893a,0xb591}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x118b}, new char[]{0x11a2,0xff30,0x3d83,0xd5eb}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5319,0x0ca4}, new char[]{0x5335,0x0c84,0xc07e,0x0de0}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1472}, new char[]{0x145c,0x8951,0x003b,0xcae5}).plainString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe350}, new char[]{0xe37f,0xdb50,0x898f,0x015d}).plainString();
    }
}
