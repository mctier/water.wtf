package com.utilitymod.anubis.module.impl.donutmacros;

import java.util.function.Consumer;
import com.utilitymod.anubis.chat.ModuleChat;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;

final class OrdersNav {
   static final int YOUR_ORDERS_BUTTON = 51;
   static final int ORDER_SLOTS = 45;
   private static final long OPEN_TIMEOUT_MS = 10000L;

   private OrdersNav() {
   }

   static boolean openYourOrders(MacroWorker worker, Gui gui, Consumer<ModuleChat.Body> say) {
      gui.closeAll();
      worker.sleep(150L);
      gui.command(LT.L1);
      if (!worker.waitFor(OrdersNav::isOrdersRootNow, 10000L)) {
         say.accept(ModuleChat.body().warn(LT.L2));
         gui.closeAll();
         return false;
      } else {
         String before = gui.fingerprint();
         gui.click(51, 0, SlotActionType.PICKUP);
         if (worker.waitFor(OrdersNav::isYourOrdersNow, 2500L)) {
            return true;
         } else if (gui.waitChange(before, 1000L)) {
            worker.sleep(150L);
            return true;
         } else {
            say.accept(ModuleChat.body().warn(LT.L3));
            gui.closeAll();
            return false;
         }
      }
   }

   static boolean isOrdersRootNow() {
      if (!Gui.containerOpenNow()) {
         return false;
      } else {
         String title = ServerText.normalize(Gui.mc().currentScreen.getTitle());
         if (title.startsWith(LT.L4) || title.equals(LT.L1)) {
            return true;
         } else if (title.contains(LT.L5) || title.contains(LT.L6)) {
            return false;
         } else {
            return title.contains(LT.L1) ? true : Gui.nameOf(Gui.slotNow(51)).contains(LT.L7);
         }
      }
   }

   static boolean isYourOrdersNow() {
      if (!Gui.containerOpenNow()) {
         return false;
      }

      String title = ServerText.normalize(Gui.mc().currentScreen.getTitle());
      return title.contains(LT.L5) || Gui.findNow(OrdersNav::isNewOrderPane, 45) >= 0;
   }

   static boolean isNewOrderPane(ItemStack stack) {
      return stack.isOf(Items.GRAY_STAINED_GLASS_PANE) && Gui.nameOf(stack).contains(LT.L8);
   }

   static boolean isPane(ItemStack stack) {
      return stack.isEmpty() || Registries.ITEM.getId(stack.getItem()).getPath().contains(LT.L9);
   }


    // cloak literals - runtime PUA carriers (cloak) or functional plain (LT)
    private static final class LT {
            static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0daf,0x3a75,0x30c4,0xdff8,0xbc25,0x0753}, new char[]{0x30ab,0xdf8a,0xbc41,0x0dca,0x3a07,0x0720}).plainString();
            static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xed0b,0xd6e5,0x0f62,0x495b,0xab90,0xa852,0x457f,0x6241,0x1a3c,0x5918,0x67f0,0x14c0,0xf233,0xcac8,0x9b8f,0x375a,0x7833,0x922e,0x7e26,0xd3c9,0x9f55,0xa9ba,0xae68}, new char[]{0x453c,0xabff,0xd3bc,0x4937,0xcaac,0x5938,0x9be1,0x3735,0xf247,0x6261,0x14af,0x6780,0x7e43,0xa83c,0xae48,0x0f4d,0x1a53,0xd697,0x7857,0xed6e,0x9f27,0x925d,0xa994}).plainString();
            static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3f89,0x090b,0x8c07,0xbd5e,0x4550,0x3584,0x9ad2,0x455e,0xa7cf,0x4f58,0xa833,0x103c,0x26dd,0x54d6,0xa995,0x99be,0xaaef,0xb190,0x2d0f,0x8a2e,0x94ca,0x0009,0x9a8f,0x677c,0x34ea,0x8d32,0x426d}, new char[]{0xa9d6,0x453f,0x8a5b,0x4201,0x006d,0x26fd,0x4f36,0x99d1,0x349e,0x9aaf,0x4531,0x104c,0x35e1,0xbd30,0x54f6,0x6725,0xaa80,0x097e,0x9aa0,0x8d12,0xa780,0xb1e2,0xa857,0x3fec,0x8c75,0x94b9,0x2d21}).plainString();
            static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x939a,0x5177,0xc1eb,0x0292,0xac18,0x08b7,0xb67c,0xf048,0xce20,0xd988,0xd220,0x188d}, new char[]{0x18e2,0x02e0,0xd9ec,0xac7d,0x08c5,0xc198,0xb65c,0x93b2,0xf038,0xd241,0x5110,0xce45}).plainString();
            static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x595b,0x2c70,0xaf15,0xd575,0xac25,0x40d8,0x5076,0x7954,0x924e,0x7cd2,0xbff5}, new char[]{0x2c09,0x5019,0x923b,0x7926,0xac05,0xd51a,0x40aa,0x593f,0x7cb7,0xbf87,0xaf66}).plainString();
            static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xfc23,0x5d44,0xdc0c,0x9a0e,0x78be,0x222f,0x8b7a}, new char[]{0xdc6d,0x9a7b,0x78dd,0x5d30,0x8b13,0xfc4c,0x2241}).plainString();
            static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4a46,0x57e0,0x6ace,0x450a,0xb901,0x31ac,0x88b0,0xf34b,0xdc40,0x84d5}, new char[]{0xf332,0x84ba,0x31d9,0x4a34,0x452a,0x88df,0xdc32,0x5784,0xb964,0x6abc}).plainString();
            static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xdd6f,0xc623,0x96a0,0x75ca,0x79d6,0x63e1,0x2388,0xa910,0xba06}, new char[]{0x23e6,0xc646,0x96d7,0x79f6,0xa97f,0xdd1d,0x75ae,0xba63,0x6393}).plainString();
            static final String L9 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x55ea,0x200b,0xc1e7,0xd419,0x60ab,0x2821,0xb071,0x186a,0x83b7,0x9628,0x510c,0x9e09,0x346e,0xbda8,0x4c05,0x2074,0xce31,0xba0a}, new char[]{0x1819,0xba7e,0xb010,0xc18e,0x5162,0x83d2,0xd47d,0xce6e,0x4c62,0xbdc4,0x60ca,0x2852,0x341d,0x2054,0x9658,0x9e68,0x5584,0x2011}).plainString();
    }
}
