package com.utilitymod.anubis.module.impl.donutmacros;

import java.util.List;
import com.utilitymod.anubis.chat.ModuleChat;

final class OrderAhBot {
   private static final int MAX_LISTINGS_PER_PASS = 9;
   private static final long RECOVERY_MS = 3000L;
   final AuctionHouse ah;
   final MacroWorker worker;
   final Gui gui;
   final Product product;
   final AuctionHouse.Config config;
   private final AuctionHouse.Stock stock;
   private final List<AuctionHouse.Stock> stocks;
   private final OrderRestock restock;
   private final int[] inAh = new int[]{0};
   private boolean warnedFull;
   volatile long orderedAt;

   OrderAhBot(OrderAhSellerModule module, MacroWorker worker, Product product, AuctionHouse.Config config, String ign) {
      this.ah = new AuctionHouse(module, worker, config, ign);
      this.worker = worker;
      this.gui = this.ah.gui;
      this.product = product;
      this.config = config;
      this.stock = new AuctionHouse.Stock(product, false);
      this.stocks = List.of(this.stock);
      this.restock = new OrderRestock(this);
   }

   void say(ModuleChat.Body body) {
      this.ah.say(body);
   }

   boolean inventoryCapReached() {
      return this.ah.inventoryCapReached(this.product);
   }

   void run() {
      this.say(ModuleChat.body().text(LT.L1).name(this.product.describe()).text(this.product.restock() ? LT.L2 : LT2.L1));

      try {
         this.gui.closeAll();
         if (this.ah.refreshListings(this.stocks)) {
            this.inAh[0] = this.stock.inAh;
         }
      } catch (MacroWorker.Cancelled cancelled) {
         throw cancelled;
      } catch (RuntimeException failure) {
         this.gui.closeAll();
      }

      while (true) {
         this.worker.check();

         try {
            this.cycle();
         } catch (MacroWorker.Cancelled cancelled) {
            throw cancelled;
         } catch (RuntimeException failure) {
            String reason = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
            this.say(ModuleChat.body().warn(reason).text(LT.L3));
            this.worker.check();
            this.gui.closeAll();
            this.worker.sleep(3000L);
         }
      }
   }

   private void cycle() {
      if (!this.ah.inWorld()) {
         this.worker.sleep(2000L);
      } else {
         this.ah.ensureAlive();
         this.ah.ensurePrice(this.stock);
         this.inAh[0] = this.stock.inAh;
         if (this.stock.price <= 0L) {
            this.worker.sleep(Math.min(5000L, this.config.priceCheckMs()));
         } else {
            int count = this.ah.countInventory(this.product);
            if (count < this.config.perListing() && this.product.restock() && this.inAh[0] < this.config.maxListings() && this.restock.run()) {
               count = this.ah.countInventory(this.product);
            }

            if (count >= this.config.perListing()) {
               int room = this.config.maxListings() - this.inAh[0];
               if (room <= 0) {
                  if (!this.warnedFull) {
                     this.say(ModuleChat.body().text(LT.L4).value(this.inAh[0] + LT2.L2 + this.config.maxListings()).text(LT.L5));
                  }

                  this.warnedFull = true;
               } else {
                  this.warnedFull = false;
                  int listedNow = this.ah.listSome(this.stock, Math.min(Math.min(count / this.config.perListing(), room), 9), this.inAh);
                  if (listedNow > 0 && this.ah.countInventory(this.product) >= this.config.perListing() && this.inAh[0] < this.config.maxListings()) {
                     this.worker.sleep(500L);
                     return;
                  }
               }
            }

            this.ah.waitSalesCheck();
            if (this.ah.refreshListings(this.stocks)) {
               this.inAh[0] = this.stock.inAh;
            }

            int more = this.ah.countInventory(this.product);
            if (more < this.config.perListing() || this.inAh[0] >= this.config.maxListings()) {
               if (more < this.config.perListing() && this.product.restock() && this.inAh[0] < this.config.maxListings()) {
                  this.restock.run();
               }
            }
         }
      }
   }

   void onChat(String raw) {
      String line = ServerText.normalize(raw);
      if (!line.isEmpty()) {
         if (line.startsWith(LT.L6)) {
            this.orderedAt = System.currentTimeMillis();
         }

         this.ah.onChat(line, this.stocks);
      }
   }

   String summary() {
      return AuctionHouse.summary(this.stocks);
   }


    // cloak literals - runtime PUA carriers (cloak) or functional plain (LT)
    private static final class LT {
            static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4f3a,0x8b4e,0x3ada,0xf9ae,0x4a5c,0x4035,0xdd6e,0x0774}, new char[]{0x4a0f,0x4f5f,0x0718,0xf9c2,0x8b27,0xdd00,0x4052,0x3afa}).plainString();
            static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xdfca,0xce39,0xa6fa,0xd614,0x085c,0xa316,0xe6f8,0xcc5c,0x7251,0xc4cd,0xa0b7,0x90a6,0x451c,0xd1cc,0xc302,0xd9e0,0x6c72,0xebd8,0x7606,0x56be,0xd8fe,0xb0bc,0xa85b,0x7b9e,0xcbac}, new char[]{0xcc70,0xa6da,0xd1be,0xa0d2,0x6c01,0x7672,0x56d1,0xb0df,0x0837,0x7bf7,0xe696,0xc365,0xd634,0x90d1,0xce50,0xa82f,0xdfa2,0xebf8,0xd98f,0xa364,0x7235,0xc4a8,0x456e,0xcbdf,0xd8d0}).plainString();
            static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6c92,0x3546,0x8cc9,0xe4de,0x82ab,0xff86,0xb1a5,0x0f7e,0xbca3,0xf684,0x118c,0x4763,0xbaf1,0xb4d7,0xa252}, new char[]{0x8ce5,0x6cb2,0xa226,0xe4ac,0xf6fd,0x352f,0x82c5,0xb4b0,0xbc83,0x4702,0xffe1,0xba90,0xb1cc,0x0f10,0x11a2}).plainString();
            static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa764,0x6b24,0x13bd,0x36da,0x9c26,0x9a2f,0x8158,0x0505,0x276a}, new char[]{0xa725,0x2722,0x9a0f,0x6b42,0x9c53,0x36b6,0x8134,0x139d,0x052d}).plainString();
            static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xbed0,0x4062,0x917e,0xa279,0x687c,0xa1e1,0x502c,0x80dc,0x4850,0x6714,0x4317,0x0c73,0x5c70,0x8e92,0xf581,0x9525,0xb335,0x9bce,0xc717,0x391d,0x0c1c}, new char[]{0x3934,0x6738,0x685c,0xa196,0x9544,0x5045,0x4016,0x0c75,0x5c1e,0x4837,0x4337,0xa21f,0xc778,0x8ee0,0xbef0,0xb346,0x80bd,0x9112,0x9bab,0x0c00,0xf5af}).plainString();
            static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb6ad,0x57ef,0xf934,0xf5f5,0x468b,0xaf28,0xfdc4,0x42f7,0x7742,0x2d0c,0x1585}, new char[]{0x428e,0xf59a,0x15f0,0xfde4,0xf95b,0xaf5a,0xb6c9,0x2d69,0x46f9,0x7727,0x578b}).plainString();
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT2 {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xfb77}, new char[]{0xfb59,0x5a65,0x1733,0x560e}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8c96}, new char[]{0x8cb9,0x66fd,0x73b4,0x1a66}).plainString();
    }
}
