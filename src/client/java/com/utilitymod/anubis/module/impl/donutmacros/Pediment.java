package com.utilitymod.anubis.module.impl.donutmacros;

import com.utilitymod.anubis.chat.ModuleChat;
import com.utilitymod.anubis.event.EventHandler;
import com.utilitymod.anubis.event.impl.TickEvent;
import com.utilitymod.anubis.module.ClientModule;
import com.utilitymod.anubis.module.ModuleCategory;
import com.utilitymod.anubis.module.setting.StringListSetting;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerEntity;

public final class Pediment extends ClientModule {
   private static final int INVENTORY_SLOTS = 36;
   private static final int CONFIRM_SLOT = 53;
   private static final long POLL_MS = 350L;
   private static final long COMMAND_COOLDOWN_MS = 4000L;
   private static final long OPEN_TIMEOUT_MS = 10000L;
   private static final long CONFIRM_COOLDOWN_MS = 900L;
   private static final long POST_CONFIRM_MS = 250L;
   private static final int STUCK_CONFIRMS = 3;
   private final StringListSetting neverSell = this.addSetting(
      LT.L1, new StringListSetting(LT.L2, LT.L3, LT.L4, LT.L5).itemPicker()
   );
   private int menuId = -1;
   private long openingUntil;
   private long lastCommandAt;
   private long lastConfirmAt;
   private long nextPollAt;
   private boolean confirmPending;
   private String lastFingerprint = "";
   private int sameAfterConfirm;
   private int sold;
   private int confirmedCount;
   private boolean warnedNoOpen;

   public Pediment() {
      super(
         LT.L6,
         dev.potato.spud.api.ProtectedText.fromInts(new int[]{0x5F,0x5C,0x04,0xD4,0xE2,0x5E,0x31,0xF3,0xCA}).rawValue(),
         LT.L7,
         ModuleCategory.DONUT_MACROS
      );
      this.usesNotifications();
   }

   @Override
   public boolean persistEnabled() {
      return false;
   }

   @Override
   protected void onEnable() {
      this.menuId = -1;
      this.openingUntil = 0L;
      this.lastCommandAt = 0L;
      this.lastConfirmAt = 0L;
      this.nextPollAt = 0L;
      this.confirmPending = false;
      this.lastFingerprint = "";
      this.sameAfterConfirm = 0;
      this.sold = 0;
      this.confirmedCount = 0;
      this.warnedNoOpen = false;
   }

   @Override
   protected void onDisable() {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      if (player != null && this.ours(player)) {
         player.closeHandledScreen();
      }

      this.menuId = -1;
      if (this.sold > 0) {
         ModuleChat.send(this, ModuleChat.body().text(LT.L8).value(this.sold).text(this.sold == 1 ? LT.L9 : LT.L10));
      }
   }

   @EventHandler
   public void onTick(TickEvent event) {
      MinecraftClient mc = MinecraftClient.getInstance();
      ClientPlayerEntity player = mc.player;
      if (player != null && mc.getNetworkHandler() != null && mc.interactionManager != null && !player.isDead()) {
         long now = System.currentTimeMillis();
         if (this.menuId < 0 && this.openingUntil > 0L) {
            if (player.currentScreenHandler != player.playerScreenHandler && mc.currentScreen instanceof HandledScreen && containerSlots(player.currentScreenHandler) > 53) {
               this.menuId = player.currentScreenHandler.syncId;
               this.openingUntil = 0L;
               this.nextPollAt = now + 150L;
               this.warnedNoOpen = false;
            } else if (now > this.openingUntil) {
               this.openingUntil = 0L;
               if (!this.warnedNoOpen) {
                  ModuleChat.send(this, ModuleChat.body().warn(LT.L11).text(LT.L12));
               }

               this.warnedNoOpen = true;
            }
         } else if (!this.ours(player)) {
            this.menuId = -1;
            if (mc.currentScreen == null && now - this.lastCommandAt >= 4000L) {
               player.networkHandler.sendChatCommand(LT.L13);
               this.lastCommandAt = now;
               this.openingUntil = now + 10000L;
            }
         } else if (now >= this.nextPollAt) {
            this.nextPollAt = now + 350L;
            ScreenHandler menu = player.currentScreenHandler;
            if (this.confirmedCount > 0) {
               int gone = this.confirmedCount - countWaiting(menu);
               this.confirmedCount = 0;
               if (gone > 0) {
                  this.sold += gone;
                  ModuleChat.actionBar(this, ModuleChat.body().text(LT.L8).value(this.sold).text(LT.L14));
               }
            }

            if (this.confirmPending) {
               this.confirmPending = false;
               if (waiting(menu) && now - this.lastConfirmAt >= 900L && this.sameAfterConfirm < 3) {
                  int before = countWaiting(menu);
                  mc.interactionManager.clickSlot(menu.syncId, 53, 0, SlotActionType.PICKUP, player);
                  this.lastConfirmAt = now;
                  this.lastFingerprint = fingerprint(menu);
                  this.confirmedCount = before;
                  this.nextPollAt = now + 250L;
               }
            } else {
               int moved = 0;

               for (Slot slot : menu.slots) {
                  if (slot.inventory instanceof PlayerInventory
                     && slot.hasStack()
                     && slot.getIndex() < 36
                     && !SellRoutine.neverSell(slot.getStack(), this.neverSell.get())) {
                     mc.interactionManager.clickSlot(menu.syncId, slot.id, 0, SlotActionType.QUICK_MOVE, player);
                     moved++;
                  }
               }

               String fingerprint = fingerprint(menu);
               if (moved > 0 || !fingerprint.equals(this.lastFingerprint)) {
                  this.sameAfterConfirm = 0;
               } else if (waiting(menu)) {
                  this.sameAfterConfirm++;
               }

               if (moved > 0 || waiting(menu)) {
                  this.confirmPending = true;
                  this.nextPollAt = now + 50L;
               }
            }
         }
      } else {
         this.menuId = -1;
      }
   }

   private boolean ours(ClientPlayerEntity player) {
      return this.menuId >= 0 && player.currentScreenHandler != player.playerScreenHandler && player.currentScreenHandler.syncId == this.menuId;
   }

   private static boolean waiting(ScreenHandler menu) {
      return countWaiting(menu) > 0;
   }

   private static int countWaiting(ScreenHandler menu) {
      int count = 0;

      for (Slot slot : menu.slots) {
         if (!(slot.inventory instanceof PlayerInventory) && slot.id != 53 && slot.hasStack()) {
            count += slot.getStack().getCount();
         }
      }

      return count;
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

   private static String fingerprint(ScreenHandler menu) {
      StringBuilder text = new StringBuilder();

      for (Slot slot : menu.slots) {
         if (slot.inventory instanceof PlayerInventory) {
            break;
         }

         ItemStack stack = slot.getStack();
         if (!stack.isEmpty()) {
            text.append(slot.id).append(':').append(stack.getItem()).append('x').append(stack.getCount()).append(';');
         }
      }

      return text.toString();
   }


    // cloak literals - runtime PUA carriers (cloak) or functional plain (LT)
    private static final class LT {
            static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3c79,0x7e70,0x2ad2,0x3aa4,0xd527,0x6497,0xc14d,0x95e2,0x5fb5,0x64d2}, new char[]{0x958c,0x7e15,0x3c0f,0xc128,0x3ad6,0x64ba,0x2aa1,0x64b7,0xd54b,0x5fd9}).plainString();
            static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6b29,0x788e,0x66fb,0x875c,0x24f4,0xa970,0x8349,0xbb60,0x9ea1,0x7284}, new char[]{0x6b67,0x669e,0x78f8,0x2491,0xbb12,0xa950,0x870f,0x832c,0x72e8,0x9ecd}).toString();
            static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xcfe6,0xbde3,0xcae3,0x5a90,0xedad,0x780e,0xa55e,0x80ab,0xa916,0xf82e,0x7ebc,0x7d9b,0x5c4a,0xd6ef,0x7a41,0x07ca,0x5378,0x700b,0xeec2,0x332d,0x04a2,0x3bf1,0xf081,0xd314,0xffa1}, new char[]{0x3340,0x5af9,0x7df5,0x531d,0xedce,0x787c,0xbd82,0xd372,0x3b85,0xf0bb,0x7065,0xf84b,0x7a35,0xcf8e,0xa53b,0xa964,0xca8a,0x5c3e,0x07af,0xee9d,0x80d8,0xd698,0x04cd,0xffd3,0x7ed8}).plainString();
            static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1e82,0x82a2,0xb893,0x5a95,0xef6f,0x1d4b,0x6425,0x723b,0x8d93,0xfba9,0xced8,0x9549}, new char[]{0x82c5,0x1eed,0x8dff,0xb8f7,0x1d2e,0xfbc7,0xce87,0xef0e,0x724b,0x9539,0x6449,0x5af0}).plainString();
            static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7ac3,0xeef0,0x84f6,0x5776,0x7bac,0x8e3a,0xdba7,0x1cd0,0x8e73,0x1818,0x186a,0x9fbe,0x4c10,0x1bd2,0xc9e9,0xcee6,0x8163,0x8632,0x686d,0xfc56,0xabf9,0x2e15,0x9c15,0x1ac1,0x628e,0x934a}, new char[]{0x1807,0x1aa8,0xdbc9,0xab9c,0x7bcf,0xc99b,0x4c71,0x1bb4,0x8117,0x1822,0x62fa,0x8499,0x6819,0x8657,0x8e1e,0x8e65,0x1cbf,0x7aa5,0x5729,0x9c60,0xfc38,0xee94,0x9333,0x9fd7,0xce88,0x2e72}).plainString();
            static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xbef1,0x5f5d,0x67d5,0xff37,0xcfa2,0xfd8a,0x6e38,0x995f,0x292c}, new char[]{0x5f3c,0xbe84,0x2958,0x9930,0xcf8f,0x67a6,0xfdef,0xff5b,0x6e54}).plainString();
            static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x93d7,0x55e1,0x1d45,0x7fd6,0x0e15,0xbc89,0x4431,0xda9f,0x1811,0x91fb,0x9568,0x33a8,0x0682,0xc5b3,0x2fd0,0x1ba4,0x6aee,0xb799,0x163a,0xd0a5,0xb4ef,0x0860,0x621d,0xf631,0x2c12,0x65ae,0x0afd,0x5f33,0xd8b9,0x7d50,0xb70f,0xaf69,0x5b15,0x93ed,0xbf9a,0x3216,0xb46a,0xffe2,0xadb1,0xfdfa,0x5d78,0xf909,0x072c,0xc764,0xaf2c,0xe893,0x4e5d,0xa0f4,0xa454,0xd1e0,0x40a6,0xc14a,0x46ea,0x5666,0x3f1b,0x84e8,0x42c6,0x1287,0xbfbc,0xf8d7,0x3641,0x6317,0x1154,0xeca2,0xbeb1,0xa958,0x87db,0x69ee,0x15ab,0xacf7,0xef4f,0x5675,0xa123,0xf6e5,0xbfb1,0xb4a4,0xafc3,0x8ae6,0xc9db,0xc94c,0x09b8,0x3cf3,0xc4f8,0x54ab,0xe30b,0x64f3,0x7f71,0x9be8,0xdca4,0xea95,0xbdd6}, new char[]{0x0767,0x5b70,0x0a98,0xbfc1,0xac84,0x0998,0xa080,0x3629,0xdafa,0xeab5,0x42e9,0x7d23,0x15ce,0x1656,0xaddd,0x8ac6,0x1bc9,0xb7fc,0x4e33,0xc13f,0xaf49,0x9507,0xaf5c,0x0e70,0x06ec,0xb44a,0xc499,0xe365,0x33cc,0xffc2,0x40cd,0x5584,0xd8dc,0x1124,0xb77c,0x7f51,0xef3c,0xa93d,0xf65d,0xc5df,0x6274,0x93b9,0xbffd,0x7ff6,0xc701,0x1867,0x5603,0xbcfb,0x6997,0x918f,0x5d10,0xd189,0x3278,0x4456,0xbe91,0xbda2,0xd0cd,0x2c73,0xecd6,0x0840,0xe8fa,0xfd89,0x87fb,0xf8b9,0xc9b4,0xf97d,0xa474,0x9382,0x54c5,0x64d3,0x8491,0xc923,0x12f2,0x65dc,0xafe3,0x2f9e,0x3f7e,0xb4d2,0xbfd9,0xb49d,0x5f13,0xdcf7,0x5610,0x1d29,0x6a82,0x3cd3,0x4686,0xa14a,0x9b9b,0xf691,0x6339}).toString();
            static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc5cf,0xacf9,0x3dde,0x6b29,0x7f0d}, new char[]{0x6b7a,0xc5a0,0xac95,0x7f69,0x3dfe}).plainString();
            static final String L9 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x27a9,0xe728,0xf589,0x29f8,0xa9e1,0x1ba9}, new char[]{0xa9c1,0x2991,0xf5fd,0x1bcc,0x27c4,0xe706}).plainString();
            static final String L10 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x73ac,0x8a46,0x54ec,0x7c0f,0xfe5e,0xd84f,0xecab}, new char[]{0x8a66,0xfe37,0xd83b,0x5489,0xecc6,0x73df,0x7c21}).plainString();
            static final String L11 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7b93,0x38ad,0x3eac,0xf169,0x575f,0xec21,0xef90,0x8c0c,0xeef0,0x41db,0xf452,0x4d07,0x3e30,0xecd7,0x738c,0xc85c,0x23a9,0xad45,0x1919,0x2e91,0x9f4c,0x3aa9,0x9910,0x9f52,0xd939,0x800a}, new char[]{0x38f9,0x41b3,0xf10c,0x802a,0x7be0,0xf437,0xeffc,0x9f20,0xec01,0x3e5d,0x9975,0x3ec2,0x2ee4,0xecf7,0xc838,0x4d6e,0xad21,0x3a89,0xd957,0x8c63,0xee84,0x9f72,0x23c6,0x1969,0x73e9,0x5731}).plainString();
            static final String L12 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd9b7,0xfbfd,0x253a,0x65b1,0xafb4,0x6ae7,0xa185,0xf9a8,0x2c1c,0x86d8,0x1919}, new char[]{0xfbd1,0x6ac7,0x2c6e,0x86bd,0x254e,0xa1f7,0xafcd,0xf9c1,0xd9d9,0x197e,0x659f}).plainString();
            static final String L13 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x812b,0xdfdd,0x7ba3,0xeed2}, new char[]{0x8158,0xeeb7,0xdfb1,0x7bcf}).plainString();
            static final String L14 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe50b,0x68ed,0xac16,0xad1d,0x9b40,0x5af0,0xe292,0xd90a,0x3eab,0x5b24,0x514b,0x88a7,0xc469,0x851d,0x0057,0x456f,0xcd21,0xe1b7,0xff51}, new char[]{0xc449,0xac7f,0x8569,0xe56e,0xd967,0x689e,0x3e8b,0x9b34,0xad75,0xff38,0x88d4,0x516b,0xe2e1,0xcd44,0x0024,0x5a83,0xe1de,0x4500,0x5b4a}).plainString();
    }
}
