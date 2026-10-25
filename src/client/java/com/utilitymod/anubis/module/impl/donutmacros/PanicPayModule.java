package com.utilitymod.anubis.module.impl.donutmacros;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.utilitymod.anubis.chat.ModuleChat;
import com.utilitymod.anubis.event.EventHandler;
import com.utilitymod.anubis.event.impl.PacketReceiveNettyEvent;
import com.utilitymod.anubis.module.ClientModule;
import com.utilitymod.anubis.module.ModuleCategory;
import com.utilitymod.anubis.module.setting.BooleanSetting;
import com.utilitymod.anubis.module.setting.StringSetting;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemConvertible;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.client.network.ClientPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PanicPayModule extends ClientModule {
   private static final Logger LOGGER = LoggerFactory.getLogger(LT.L1);
   private static final Pattern VALID_NAME = Pattern.compile(LT.L2);
   private static final Pattern RATE_LIMIT = Pattern.compile(LT.L3);
   private static final int INVENTORY_SLOTS = 36;
   private static final int CONFIRM_SLOT = 53;
   private static final int MAX_STRIKES = 2;
   private static final int MAX_SELL_ROUNDS = 6;
   private static final int[] ARMOR_MENU_SLOTS = new int[]{5, 6, 7, 8};
   private static final int OFFHAND_MENU_SLOT = 45;
   private static final long COMMAND_GAP_MS = 1000L;
   private static final long OPEN_TIMEOUT_MS = 8000L;
   private static final long REPLY_TIMEOUT_MS = 6000L;
   private final StringSetting payTo = this.addSetting(LT.L4, new StringSetting(LT.L5, "").hint(LT.L6));
   private final BooleanSetting sellArmor = this.addSetting(LT.L7, new BooleanSetting(LT.L8, true));
   private final BooleanSetting sellOffhand = this.addSetting(LT.L9, new BooleanSetting(LT.L10, true));
   private final MacroWorker worker = new MacroWorker(LT.L11);
   private volatile boolean running;
   private volatile long paidAt;
   private volatile long poorAt;
   private volatile long errorAt;
   private volatile String errorText = "";
   private volatile long holdUntil;
   private volatile long balanceAt;
   private volatile double balance;
   private volatile String balanceText = "";
   private long lastCommandAt;

   public PanicPayModule() {
      super(LT.L12, LT.L13, LT.L14, ModuleCategory.DONUT_MACROS);
      this.usesNotifications();
      this.payTo.sessionOnly();
   }

   @Override
   public boolean persistEnabled() {
      return false;
   }

   @Override
   public boolean inConfigs() {
      return false;
   }

   @Override
   protected void onEnable() {
      MinecraftClient mc = MinecraftClient.getInstance();
      String target = this.payTo.get().trim();
      if (mc.player == null || mc.getNetworkHandler() == null) {
         this.say(ModuleChat.body().warn(LT.L15));
         this.requestDisable();
      } else if (!VALID_NAME.matcher(target).matches()) {
         this.say(ModuleChat.body().warn(LT.L16));
         this.requestDisable();
      } else if (target.equalsIgnoreCase(mc.getSession().getUsername())) {
         this.say(ModuleChat.body().warn(LT.L17));
         this.requestDisable();
      } else {
         boolean armor = this.sellArmor.get();
         boolean offhand = this.sellOffhand.get();
         this.running = true;
         this.lastCommandAt = 0L;
         this.say(
            ModuleChat.body().danger(LT.L18).text(LT.L19).name(target).text(LT.L20)
         );
         this.worker.start(() -> {
            try {
               this.run(new Gui(this.worker), target, armor, offhand);
            } catch (AuctionHouse.Problem problem) {
               this.say(ModuleChat.body().warn(problem.getMessage()).text(LT.L21));
            } finally {
               this.finish(mc);
            }
         }, failure -> {
            LOGGER.error(LT.L22, failure);
            this.say(ModuleChat.body().danger(LT.L23).text(LT2.L1 + failure));
            this.finish(mc);
         });
      }
   }

   private void finish(MinecraftClient mc) {
      mc.execute(() -> {
         if (this.running && this.isEnabled()) {
            this.setEnabled(false);
         }
      });
   }

   @Override
   protected void onDisable() {
      boolean wasRunning = this.running;
      this.running = false;
      this.worker.stop();
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      if (wasRunning && player != null && player.currentScreenHandler != player.playerScreenHandler) {
         player.closeHandledScreen();
      }
   }

   @EventHandler
   public void onPacket(PacketReceiveNettyEvent event) {
      if (this.running && event.packet instanceof GameMessageS2CPacket chat && chat.content() != null) {
         String raw = chat.content().getString();
         String line = ServerText.normalize(raw);
         long now = System.currentTimeMillis();
         Matcher wait = RATE_LIMIT.matcher(line);
         if (wait.find()) {
            try {
               this.holdUntil = now + (long)Math.ceil(Double.parseDouble(wait.group(1)) * 1000.0) + 100L;
            } catch (NumberFormatException var11) {
            }
         } else {
            if (line.startsWith(LT.L24)) {
               this.paidAt = now;
            } else if (line.contains(LT.L25)) {
               this.poorAt = now;
            } else if (!line.startsWith(LT.L26) && (!line.startsWith(LT.L27) || !line.contains(LT2.L2))) {
               if (line.contains(LT.L28)
                  || line.contains(LT.L29)
                  || line.contains(LT.L30)
                  || line.contains(LT.L31)
                  || line.contains(LT.L32)
                  || line.contains(LT.L33)
                  || line.contains(LT.L34)
                  || line.contains(LT.L35)) {
                  this.errorText = raw;
                  this.errorAt = now;
               }
            } else {
               String shown = line.substring(line.indexOf(36));
               List<Double> values = ServerText.moneyAll(shown);
               String token = ServerText.moneyToken(shown);
               if (!values.isEmpty() && token != null) {
                  this.balance = values.get(0);
                  this.balanceText = token;
                  this.balanceAt = now;
               }
            }
         }
      }
   }

   private void run(Gui gui, String target, boolean armor, boolean offhand) {
      gui.closeAll();
      Map<Item, Integer> strikes = new HashMap<>();
      int sold = this.sellRounds(gui, strikes);
      if ((armor || offhand) && this.takeOff(gui, armor, offhand)) {
         sold += this.sellRounds(gui, strikes);
      }

      this.say(ModuleChat.body().text(LT.L36).value(sold).text(sold == 1 ? LT.L37 : LT.L38).text(LT.L39).name(target).text(LT2.L3));
      this.payBalance(gui, target);
   }

   private int sellRounds(Gui gui, Map<Item, Integer> strikes) {
      int total = 0;

      for (int round = 0; round < 6; round++) {
         Map<Item, Integer> before = this.worker.call(() -> inventoryCounts(strikes));
         if (before.isEmpty()) {
            break;
         }

         this.openSell(gui);
         int moved = this.worker
            .call(
               () -> {
                  ClientPlayerEntity player = Gui.player();
                  List<Integer> slots = new ArrayList<>();

                  for (Slot slot : player.currentScreenHandler.slots) {
                     if (slot.inventory instanceof PlayerInventory
                        && slot.hasStack()
                        && slot.getIndex() < 36
                        && strikes.getOrDefault(slot.getStack().getItem(), 0) < 2) {
                        slots.add(slot.id);
                     }
                  }

                  for (int index : slots) {
                     Gui.mc().interactionManager.clickSlot(player.currentScreenHandler.syncId, index, 0, SlotActionType.QUICK_MOVE, player);
                  }

                  return slots.size();
               }
            );
         this.worker.ticks(2);
         if (moved > 0 && this.worker.call(Gui::containerOpenNow)) {
            gui.click(53, 0, SlotActionType.PICKUP);
         }

         this.worker.sleep(700L);
         gui.closeAll();
         this.worker.sleep(400L);
         Map<Item, Integer> after = this.worker.call(() -> inventoryCounts(strikes));

         for (Entry<Item, Integer> entry : before.entrySet()) {
            int left = after.getOrDefault(entry.getKey(), 0);
            if (left < entry.getValue()) {
               total += entry.getValue() - left;
            } else {
               int strike = strikes.merge(entry.getKey(), 1, Integer::sum);
               if (strike == 2) {
                  this.say(
                     ModuleChat.body()
                        .warn(LT.L40)
                        .name(new ItemStack((ItemConvertible)entry.getKey()).getName().getString())
                        .text(LT.L41)
                  );
               }
            }
         }
      }

      return total;
   }

   private static Map<Item, Integer> inventoryCounts(Map<Item, Integer> strikes) {
      Map<Item, Integer> counts = new HashMap<>();
      PlayerInventory inventory = Gui.player().getInventory();

      for (int index = 0; index < 36; index++) {
         ItemStack stack = inventory.getStack(index);
         if (!stack.isEmpty() && strikes.getOrDefault(stack.getItem(), 0) < 2) {
            counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
         }
      }

      return counts;
   }

   private void openSell(Gui gui) {
      for (int attempt = 0; attempt < 3; attempt++) {
         this.command(gui, LT.L42);
         if (this.worker.waitFor(PanicPayModule::sellMenuOpen, 8000L)) {
            this.worker.sleep(150L);
            return;
         }

         gui.closeAll();
      }

      throw new AuctionHouse.Problem(LT.L43);
   }

   private static boolean sellMenuOpen() {
      ClientPlayerEntity player = Gui.mc().player;
      if (player != null && player.currentScreenHandler != player.playerScreenHandler && Gui.mc().currentScreen instanceof HandledScreen) {
         int container = 0;

         for (Slot slot : player.currentScreenHandler.slots) {
            if (!(slot.inventory instanceof PlayerInventory)) {
               container++;
            }
         }

         return container > 53;
      } else {
         return false;
      }
   }

   private boolean takeOff(Gui gui, boolean armor, boolean offhand) {
      List<Integer> slots = this.worker.call(() -> {
         ClientPlayerEntity player = Gui.player();
         List<Integer> out = new ArrayList<>();
         if (armor) {
            for (int slotx : ARMOR_MENU_SLOTS) {
               if (player.playerScreenHandler.getSlot(slotx).hasStack()) {
                  out.add(slotx);
               }
            }
         }

         if (offhand && player.playerScreenHandler.getSlot(45).hasStack()) {
            out.add(45);
         }

         return out;
      });
      if (slots.isEmpty()) {
         return false;
      }

      gui.closeAll();

      for (int slot : slots) {
         gui.clickInventory(slot, 0, SlotActionType.QUICK_MOVE);
         this.worker.ticks(1);
      }

      gui.closeInventory();
      this.worker.ticks(4);
      return true;
   }

   private void payBalance(Gui gui, String target) {
      String amount = this.readBalance(gui);
      if (this.balance < 1.0) {
         this.say(ModuleChat.body().warn(LT.L44));
      } else {
         for (int attempt = 0; attempt < 3; attempt++) {
            PanicPayModule.Payment result = this.pay(gui, target, amount);
            if (result == PanicPayModule.Payment.PAID) {
               this.say(ModuleChat.body().good(LT.L45 + amount).text(LT.L46).name(target).text(LT2.L3));
               return;
            }

            if (result == PanicPayModule.Payment.POOR) {
               throw new AuctionHouse.Problem(LT.L47 + target + " " + amount + LT.L48);
            }

            if (result == PanicPayModule.Payment.FAILED) {
               throw new AuctionHouse.Problem(LT.L49 + this.errorText);
            }
         }

         throw new AuctionHouse.Problem(LT.L50 + target + " " + amount);
      }
   }

   private String readBalance(Gui gui) {
      for (int attempt = 0; attempt < 3; attempt++) {
         long mark = System.currentTimeMillis();
         this.command(gui, LT.L51);
         if (this.worker.waitFor(() -> this.balanceAt >= mark, 6000L)) {
            return this.balanceText;
         }
      }

      throw new AuctionHouse.Problem(LT.L52);
   }

   private PanicPayModule.Payment pay(Gui gui, String target, String amount) {
      long mark = System.currentTimeMillis();
      this.command(gui, LT.L53 + target + " " + amount);
      Object[] pressed = new Object[]{null};
      long end = mark + 6000L;

      while (System.currentTimeMillis() < end) {
         if (this.paidAt >= mark) {
            return PanicPayModule.Payment.PAID;
         }

         if (this.poorAt >= mark) {
            return PanicPayModule.Payment.POOR;
         }

         if (this.errorAt >= mark) {
            return PanicPayModule.Payment.FAILED;
         }

         if (this.holdUntil > mark) {
            return PanicPayModule.Payment.RETRY;
         }

         this.worker.run(() -> {
            Dialogs.View view = Dialogs.current(Gui.mc());
            if (view != null && view.dialog() != pressed[0]) {
               Dialogs.Button confirm = view.labelled(LT.L54, LT.L55, LT.L56);
               if (confirm != null && Dialogs.press(Gui.mc(), view, confirm, Map.of())) {
                  pressed[0] = view.dialog();
               }
            }
         });
         this.worker.sleep(50L);
      }

      return PanicPayModule.Payment.RETRY;
   }

   private void command(Gui gui, String command) {
      long wait = Math.max(this.holdUntil, this.lastCommandAt + 1000L) - System.currentTimeMillis();
      if (wait > 0L) {
         this.worker.sleep(wait);
      }

      gui.command(command);
      this.lastCommandAt = System.currentTimeMillis();
   }

   private void say(ModuleChat.Body body) {
      ModuleChat.send(this, body);
   }

   private enum Payment {
      PAID,
      POOR,
      RETRY,
      FAILED;
   }


    // cloak literals - runtime PUA carriers (cloak) or functional plain (LT)
    private static final class LT {
            static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6107,0xcfaf,0xf5f9,0x4349,0x8b30,0xd8c0}, new char[]{0x6146,0x4327,0xd8b5,0xcfcd,0xf590,0x8b43}).plainString();
            static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x449f,0xf845,0xc3b4,0x1de3,0xcf9c,0xc44f,0x600d,0x2b72,0xec18,0x717c,0x50ed,0x9485,0x2e9b,0x499b,0x2be6,0x7fed,0x9213,0x4cd9,0x79af,0x1341,0xb5d2}, new char[]{0x7120,0x136f,0x94ba,0x44c4,0x7fac,0x50c0,0x49c1,0xc3d5,0x923e,0x6077,0xcfac,0xb5ff,0x2ea2,0x2bb9,0xf818,0x79d4,0x4ce8,0x1dcf,0xec29,0x2b44,0xc432}).toString();
            static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9579,0x8f5a,0x50ec,0xedb1,0x8e4d,0xbe75,0x3cd1,0xc4e7,0x91a3,0xf75e,0xceda,0xbb31,0xe2f3,0xf141,0x0668,0x4dcd,0xc364,0x6a52,0x5f33,0x46ca,0x7b98,0x3240,0x255a,0xe17d,0x37b8,0xa9dc,0x5a67,0xfe8e,0x9be2,0x2418,0x8a6e,0xdd95,0x96aa,0x807d,0x2704,0xab0f,0x32ce,0xc5a4,0x3978,0x6456,0x3924,0x4490}, new char[]{0x3901,0xceb5,0x8008,0x2724,0xfee0,0xe118,0xc5c1,0xa9b8,0xc344,0x3950,0xbe1a,0x4ded,0x5a10,0xf120,0x7bf1,0x6a26,0x257a,0xddf4,0x322e,0x91cc,0xbb45,0x44f8,0xedd4,0xe281,0x5f13,0x9551,0x4691,0xc4bb,0x6432,0x3cff,0xf703,0x0643,0x9683,0x32ee,0x8a1d,0xab6a,0x508f,0x2477,0x8f34,0x9b86,0x8e3e,0x3787}).plainString();
            static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0132,0x9569,0xd992,0x2a94,0x02a3,0xb818}, new char[]{0x2ae4,0xb879,0x014b,0xd9bf,0x951d,0x02cc}).plainString();
            static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4c8f,0xfd66,0xa332,0xfd88,0x6cf9,0xb05f}, new char[]{0xb00f,0x6c98,0xfdf1,0x4caf,0xfd32,0xa35d}).toString();
            static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x33f3,0x321a,0xe5c3,0x5384,0x9e53,0x4e33,0xa33c,0x51fd,0x5599,0x15f0,0xb88f}, new char[]{0x15a0,0x4e5f,0xe5a2,0x9e2a,0x327f,0xa34e,0xb8af,0x53ea,0x519c,0x55f4,0x3396}).toString();
            static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x08a5,0x20a4,0x74b2,0x6faa,0xfeb4,0x486f,0xdc3d,0xaec6,0x537f,0xfd72}, new char[]{0x6fd9,0x20c1,0x4803,0x5313,0x749f,0x08c4,0xdc4f,0xfd1f,0xfedb,0xaeb4}).plainString();
            static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb297,0x8294,0xdb14,0x0432,0x4b32,0xca74,0x3f23,0x1ea8,0xa488,0x10c3}, new char[]{0xa4db,0x1ecd,0xb2fb,0x045e,0xdb34,0x1082,0x3f51,0x82f9,0x4b5d,0xca06}).toString();
            static final String L9 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8b23,0x82ee,0x8547,0x8555,0xc4d9,0xf366,0xc027,0x1f97,0x8d25,0x4740,0x6265,0x7d9d}, new char[]{0x4733,0x8b46,0x852b,0x6209,0xc4f4,0x8d4a,0xc041,0xf300,0x853d,0x7dfc,0x1ff9,0x828a}).plainString();
            static final String L10 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x00fa,0x13d8,0x94d2,0xce0c,0xbe23,0x9d2b,0x7fa0,0x1395,0xa09d,0x69bc,0x8dc8,0x2c7c}, new char[]{0xa0ce,0x009f,0x9d47,0x7fcc,0x699c,0xce43,0x13f3,0x2c1a,0x94ba,0xbe42,0x8da6,0x13bc}).toString();
            static final String L11 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xce3b,0xb222,0x507f,0x4612,0x9020,0x40ab,0x31aa,0x43d0,0xaaae,0xb777,0x6f69,0xb915,0x4e5a,0x806d,0xae4c,0x21b8}, new char[]{0xae0d,0xb719,0x40de,0xb240,0xb97c,0x500c,0xaa8e,0x21e8,0x4673,0x43be,0x6f00,0x31c9,0x804d,0x4e0a,0x9041,0xce42}).toString();
            static final String L12 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x992f,0x2f69,0x720d,0x3e7e,0x748e,0x05e4,0x7b08,0x2fc4,0x361a}, new char[]{0x3e0e,0x367b,0x2f07,0x058d,0x726e,0x2fe9,0x995f,0x74ef,0x7b71}).plainString();
            static final String L13 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1f0f,0x903a,0x228a,0xa945,0x153b,0xac83,0x3cac,0x1ddb,0x6b2c}, new char[]{0x156b,0x1f6e,0xa92b,0x1db2,0x6b4f,0xaca3,0x906a,0x3ccd,0x22f3}).toString();
            static final String L14 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7a44,0xe2a2,0x15e3,0xc3db,0x452f,0xd20d,0x74be,0x7c56,0x361c,0xe223,0xdcde,0x61ba,0xea63,0x2519,0xcebe,0x2b73,0xde1a,0x26c7,0x0460,0x0050,0xc43f,0xcddb,0x53b4,0xade5,0xb89e,0xd37d,0x0283,0x6358,0x69a1,0xd109,0x429f,0x6d4b,0x3812,0x8107,0x7fe2,0x6d57,0xe8ea,0x9e2d,0x813e,0x9707,0x9b1f,0x202c,0xd7f1,0x8ff6,0x77f5,0xf7f4,0x34f1,0xe992,0xcaa3,0xe57b,0xa440,0xad3a,0x97e0,0xb00b,0xcd89,0xaca0,0x7950,0x6d89,0x1870,0x2a2e,0x6e2a,0xbf75,0x379f,0x477c,0x69c4,0xb38d,0xf9a8,0x7750,0x208f,0x056c,0xfa82,0x88eb,0x7cca,0x82b7,0x2f49,0x1c5f,0x5fca,0xdbb7,0x445f,0x7b21,0x4556,0xd56d,0xdf5f,0x1b00,0x76fc,0x7bfc,0xa737,0x2548}, new char[]{0x8fa5,0x4533,0x7690,0x69a8,0x69d2,0x26e7,0xcada,0xbf1a,0x889e,0x253a,0x37bf,0x632f,0xde72,0x61d5,0xd261,0x0405,0xe9b2,0xcde0,0x9b71,0x7926,0xd508,0x1b6e,0xad4e,0xc450,0x7b8e,0x1c26,0x2539,0xdbd6,0xb065,0x7734,0xe282,0x2f28,0xf786,0xfaef,0x6d38,0x20fd,0x5394,0xd786,0xcdb2,0x1804,0xe882,0xdcfe,0x2003,0x3482,0x7b44,0x02ef,0x4543,0x812b,0x749e,0x5fbe,0xad8d,0xe51e,0x6d25,0x42bf,0xc3ab,0x367d,0x9e54,0x0023,0x447f,0xa756,0x4710,0x978c,0x77d5,0x82ce,0x387d,0xf9dd,0x2b01,0xa460,0x7ca7,0xb8f1,0xced0,0x815b,0x6df0,0x9727,0xacd4,0x7c39,0xea43,0x7f8d,0xb3e3,0x7a21,0xd35d,0x6e5a,0x2a42,0x050d,0xd170,0xe246,0xdf2d,0x15cd}).toString();
            static final String L15 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xac6f,0xa0aa,0x0fd4,0xd492,0x4506,0xddf6,0x00b6,0x5dec,0x45da,0xaca0,0xad78,0x2840,0x1b3b,0xfce4,0xe050,0x704a,0x28b8,0xd5c4,0x9bc7,0xfeb8}, new char[]{0x454c,0x7025,0x2829,0xdd98,0x9be7,0xfed9,0x0096,0xad0b,0xfc81,0xac1d,0x45ac,0x1b5e,0xd4e0,0x2898,0xa0cc,0x5d85,0xacd2,0xd5b7,0x0fa0,0xe07e}).plainString();
            static final String L16 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd165,0x6bc8,0xb898,0x8bf7,0x95ba,0x8105,0x4fa3,0xdd04,0xaac3,0x17e8,0x8aff,0x730e,0xdc85,0xb8a8,0xb832,0x7591,0x5ea1,0xe98c,0x8d4d,0xe46b,0xab7d,0x518b,0x8b30,0xfa0c,0x8bd2,0x2939,0xbefd,0x5791,0x5114,0x927d,0x8f23,0x58db,0x810f,0xe000}, new char[]{0xe438,0x8b55,0x9209,0x5e81,0xb8f8,0x8b96,0x7377,0x8f03,0xd131,0x58b4,0x4f83,0x75e5,0x51e4,0x57b1,0x8bb3,0x17c8,0xfa7c,0x6ba4,0xab1c,0x2940,0x8160,0x8a8d,0x5134,0xe06e,0x8d2c,0xb8f5,0xbe98,0xaae3,0xe9ea,0xb85b,0x817d,0xdd77,0x95ce,0xdcab}).plainString();
            static final String L17 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x68eb,0x56d7,0x7c8a,0x8f8d,0xa529,0xde3d,0x5f8e,0x798c,0x5cf7,0xec7d,0x9164,0x23a7,0xbb2d,0x8776,0xed32,0xf63f,0x0b20,0xf7be,0xe797,0xcc97,0xe867,0xf4d0,0x86dd,0xe765}, new char[]{0x868d,0xed53,0x911d,0x0b00,0x5fda,0xe808,0x8fad,0xbb44,0xf7cd,0x7caa,0x56ae,0xe7f8,0xec08,0xa55b,0xccb7,0xde52,0xf648,0x6885,0x2387,0xe70b,0x8717,0xf4bd,0x5c92,0x79a2}).plainString();
            static final String L18 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x847a,0x29f3,0x8b39,0x384f,0xd444,0x466f,0xf877,0xab7c,0x6279,0x8b40,0x22aa,0x9ec4,0xfde6,0xc222,0xc6b4,0x73e2,0xcc3b}, new char[]{0x9e94,0x22cb,0x4601,0xf81e,0x2990,0xcc1b,0xab2c,0x382e,0xc25b,0x8b19,0x7391,0xfd92,0x8b21,0x8408,0xd430,0xc6d1,0x621d}).toString();
            static final String L19 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf2a1,0xf152,0x190a,0x7cbe,0x80ae,0xcc03,0x626f,0x95b1,0x235a,0xcd6b,0x0d52,0xadf1,0x6b60,0x807f,0xc2d8,0x4134,0xd951,0x79af,0x1962,0xe6c6,0xa0d7,0x907f,0x2456,0xbdd2,0xbc0b,0x8014,0xc6dd,0x7f17,0xca48,0x4c3d,0x6b7c,0x8ea9,0x7c08,0xf420}, new char[]{0xbde8,0x7c28,0x1911,0x79ca,0x9013,0xe6aa,0x4c54,0x6b0e,0xf447,0x0d72,0xca2d,0xbc7d,0x8ecc,0xc6af,0xa0ae,0xcd1f,0x415c,0x7f7e,0xcc6d,0x6208,0xf28d,0xd971,0x232e,0x807c,0x6b19,0x8011,0x808e,0xad81,0xf133,0xc2a1,0x7cd7,0x2438,0x196d,0x9591}).plainString();
            static final String L20 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x53ab,0xace0,0x3a94,0xbe0a,0x0cad,0xf756,0x0e31,0x964f,0x0ef3,0xba73,0xe5e5,0x5cd5,0x1d02,0xda41,0x71cc,0x614d,0xa3cd,0x10cc,0x04b2,0x867b,0x77fa,0x1ab0,0x36a3,0x2a90,0x0900,0x9b62,0x5175,0x9926,0x06d8,0xcde1,0x17c6,0xb520}, new char[]{0x71e2,0x3683,0x611d,0xb552,0x0e54,0x5106,0x5ca6,0x17e6,0x06ac,0xac88,0x861e,0x0920,0x0cc6,0x1ad5,0x53d2,0xcdc1,0x9947,0xe582,0xda20,0xa3a4,0xba1d,0xf776,0x10b8,0x1d6d,0x0ed3,0xbe69,0x04d3,0x2afe,0x9b01,0x962a,0x7796,0x3aba}).plainString();
            static final String L21 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x70a0,0x9cdc,0xa49b,0x5cdb,0x8fc5,0x821f,0x1ced,0x7c4a,0x6ab7,0x5ebc}, new char[]{0x1cc1,0x7080,0x7c39,0x6ac3,0x5cb4,0x8fb5,0x9cac,0x5ed9,0xa4ff,0x8231}).plainString();
            static final String L22 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x82c2,0x0b2f,0xa8a5,0xd11f,0x0b28,0x9633,0xbc0a,0x82f0,0x37dc,0xd11b,0xa8af,0xa88a,0xb166,0xd121,0x2271,0x1cd2,0x379e,0xbc1c,0x2276,0xb17c,0x1c82,0x9633}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643}).plainString();
            static final String L23 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x98b2,0x7761,0x7a6d,0x8b34,0xb017,0xfdce,0x0d7b}, new char[]{0x7a3e,0xb063,0x0d14,0x98c2,0xfdbe,0x7704,0x8b50}).toString();
            static final String L24 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa71e,0x302c,0x073d,0x3522,0x9705,0x8228,0xf7b8,0xc3f7,0xe560}, new char[]{0xc38e,0xe50f,0x0748,0xa73e,0xf7c8,0x9764,0x3045,0x824c,0x3502}).plainString();
            static final String L25 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd4b4,0xfff0,0xd923,0xa2c6,0x60e0,0x158a,0x0ae0,0xe084,0x77e1,0xb0eb,0xb534,0x3648}, new char[]{0xff95,0xd4da,0xd94c,0x7794,0xa2a1,0x6088,0xb0cb,0xb559,0xe0eb,0x15e4,0x362d,0x0a99}).plainString();
            static final String L26 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf5b2,0xba4f,0xc701,0xb0f9,0x113e,0x83e6,0xe46b,0x9f00,0x0f21,0xa883}, new char[]{0xba36,0x0f4e,0xa8f6,0xc721,0x838e,0x115f,0x9f76,0xe40e,0xf592,0xb0dd}).plainString();
            static final String L27 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7c69,0xc9b3,0xf5bf,0x18ef,0x775e,0xcc00,0x0ca3}, new char[]{0x188d,0xcc61,0x7c05,0x0cc2,0xc9dd,0x773d,0xf5da}).plainString();
            static final String L28 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8aa2,0xbcc9,0xb216,0x9953,0xba23,0xa712,0x5165,0xb4a4,0xc0e7,0xb8f8}, new char[]{0xba4d,0xa77d,0xc093,0x8a82,0xb897,0x510b,0x993f,0xbca0,0xb278,0xb4c1}).plainString();
            static final String L29 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x886d,0xe2f0,0x9405,0x38e8,0xaa24,0xedc0,0xfcf7,0xf7bf,0x8e55}, new char[]{0x3886,0x946a,0xaa50,0x884d,0xe296,0xf7d0,0x8e20,0xedae,0xfc93}).plainString();
            static final String L30 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe4d6,0xded0,0xa558,0x0fde,0xba6d,0x4729,0x9c3f,0x1bbe,0xfc09,0x082b}, new char[]{0x1bdd,0xba0c,0xe4b8,0x0fb0,0xfc66,0xdea4,0x080b,0x4759,0x9c5e,0xa521}).plainString();
            static final String L31 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x50c0,0xae5e,0x9ae7,0x6263,0x13cc,0x9419,0x23f2,0x2af5,0x4312}, new char[]{0x2a96,0x9478,0x437c,0x9ac0,0x6217,0x23d2,0x13bc,0x50a1,0xae27}).plainString();
            static final String L32 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa0c4,0x6b84,0x9e08,0x7102,0x5bc9,0x09af,0xd3cc,0x70a1,0x25c6,0x1929,0x3b3d}, new char[]{0xd3a2,0x09c0,0xa0b0,0x1909,0x9e69,0x7081,0x25b0,0x6be5,0x716e,0x5ba0,0x3b59}).plainString();
            static final String L33 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb06e,0xf235,0xe529,0xfe27,0xce19,0xe50c,0x8a44}, new char[]{0xce70,0xe547,0xb018,0x8a25,0xf259,0xe565,0xfe43}).plainString();
            static final String L34 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xdacf,0x965c,0xa71a,0x961f,0x32f7,0x396b,0x8a20,0xa66e,0xa19c,0x6a59,0xef31,0x5328,0x421d}, new char[]{0x534c,0xef5e,0x3292,0x6a2a,0x4273,0x967b,0x966b,0xa1bc,0xa60b,0x8a58,0xa773,0x3918,0xdabb}).plainString();
            static final String L35 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb4b9,0xcfa4,0xd658,0x1d32,0xb972,0xc6f5,0xe357,0xb061,0x0ba9,0x6c53,0x7635,0xfaf7,0xe14e,0xffcc}, new char[]{0xb4dd,0xffa3,0xc690,0xcfd7,0xb952,0xb00f,0x0bc6,0x7641,0x1d12,0xd63d,0xe32f,0xfa9e,0x6c20,0xe13a}).plainString();
            static final String L36 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5d19,0xde38,0xce74,0x8890,0x0945}, new char[]{0xde6b,0x88ff,0xce18,0x5d7d,0x0965}).plainString();
            static final String L37 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x57ad,0xde7e,0x7523,0xb62e,0x8aab}, new char[]{0xb60e,0x754a,0x8adf,0x57c8,0xde13}).plainString();
            static final String L38 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6567,0xc85d,0x6908,0x3363,0x739b,0x0f47}, new char[]{0x6928,0x330a,0xc829,0x0f22,0x650a,0x73e8}).plainString();
            static final String L39 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x690b,0x4f4a,0x2aef,0xc58b,0x7df5,0xe053,0xd1c6,0xa9f4,0x80d6}, new char[]{0xa9d8,0xd1e6,0x7d85,0x696a,0x80af,0x4f23,0xc5e5,0xe034,0x2acf}).plainString();
            static final String L40 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe752,0xe88b,0x4cb3,0x4961,0x1678,0x7feb,0xca45,0xe321,0x62cb,0x55e0,0x59b5,0xa840,0x9d77,0xbabd,0x5722}, new char[]{0x9d34,0xe73d,0x7f9e,0x1614,0x4905,0xa860,0xe34f,0xe8e4,0x5594,0x5702,0x62b8,0x59d0,0xbad1,0x4cdf,0xca65}).plainString();
            static final String L41 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7c71,0xa416,0x0507,0x50d6,0xc404,0xe5b9,0x42c7,0xb14a,0x41f8,0x7f91,0x85f4,0x79df,0x1360,0x760b}, new char[]{0x50fa,0x7fb1,0x7678,0x4193,0x42ae,0x7c01,0x79af,0xb123,0xc46a,0xe5de,0x0527,0x859d,0xa462,0x134e}).plainString();
            static final String L42 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5290,0xde4d,0x250f,0x9494}, new char[]{0x52e3,0x256a,0x94f8,0xde21}).plainString();
            static final String L43 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1880,0x7fa5,0x1438,0x3032,0xe4c8,0xdf67,0x1f3f,0xd3b4,0xf67c,0x5cf6,0xaa01,0x2dba,0x1557,0x637d,0x2d70,0xe7ea,0xb80d,0x483c,0x1e09,0xb849,0xc2fe,0x5dfc,0x3b07,0xf344,0x439d,0xb95b}, new char[]{0x1e5d,0xb933,0x6318,0x3012,0x5c85,0x4859,0x43f1,0xb825,0xf364,0x5d91,0xc29b,0x1f51,0x7fd0,0x2d50,0xe78e,0xd3dd,0x18e4,0xe4e8,0x1456,0x3b68,0x2dce,0xb82d,0xaa6e,0xf60c,0xdf02,0x1539}).toString();
            static final String L44 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x99e6,0xb7eb,0xea51,0x53cd,0x421f,0xf763,0xb44f,0x5239,0x90c4,0x5294,0x5fc9,0xed0e,0x80ae,0xc286,0x568f,0x582a,0x7937,0x4e7b,0xa67c,0x1255,0x704d,0x9ef3,0x0859,0xee5d,0x3822}, new char[]{0x9eaa,0x4e14,0xea24,0x56af,0x7025,0x9987,0x5fbf,0x53a8,0x3802,0xf70d,0xb784,0xb46f,0x0834,0x4270,0xc2e8,0xed6b,0x90bd,0x1275,0x585e,0xee32,0x52b4,0xa60c,0x5258,0x80d7,0x7919}).plainString();
            static final String L45 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2ef8,0xa032,0xb5a5,0x8ea5,0x4cdd,0xcb29}, new char[]{0x2ea8,0xb5c4,0xa05b,0x4cb9,0xcb09,0x8e81}).plainString();
            static final String L46 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1b70,0xd9d9,0xdaf0,0xeda1}, new char[]{0xdad0,0xd9ad,0xedce,0x1b50}).plainString();
            static final String L47 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xacfe,0xf583,0x9603,0x5a22,0xd765}, new char[]{0xacd1,0x9673,0xf5e2,0x5a5b,0xd745}).plainString();
            static final String L48 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb8fe,0x8cf1,0x4b34,0x9587,0x883c,0x0858,0x4490,0xf0d3,0x813c,0xe7e2,0xddd8,0xfab6,0x197a,0x6eba,0x237b,0x991f,0xaf06,0xf908,0xb629,0x6a0a,0xc13f,0xce6b,0x9513,0x1427,0x4f0f,0x175d,0x3d0c,0xce8f,0x7600,0x6847}, new char[]{0xf928,0xb65e,0x4f6e,0x8c82,0x3d2c,0x1908,0x231e,0x4b52,0x8149,0x6ec9,0x083d,0x9577,0x44aa,0x7620,0x9971,0xb891,0x6a7e,0xc11f,0xce0e,0x8852,0xcee0,0xe797,0x95e0,0xddb0,0x6867,0xf0be,0x1448,0x1733,0xfad3,0xaf7f}).plainString();
            static final String L49 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xccbf,0x64cb,0x3c10,0xdb5d,0x5660,0xd8dc,0xe67c,0xde2e,0x37e9,0x16b8,0xd704,0x2448,0xa2d6}, new char[]{0xde01,0x64bb,0x5601,0x3c69,0xcc9f,0xd8ba,0xdb3c,0xd76d,0x2424,0xe619,0x16dc,0x37d3,0xa2f6}).plainString();
            static final String L50 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x343a,0xdcdf,0x2501,0x14e0,0xa969,0x9789,0x01b9,0x9803,0x5e1a,0x1f9f,0xd718,0x14d8,0x57a8,0xbb19,0x7efb,0xd012,0x9e92,0x6b23}, new char[]{0x1496,0x6b4c,0xd032,0xd779,0x9efc,0x01ca,0x2576,0x97ec,0x5e68,0xa949,0x7e8f,0x986c,0x341a,0xbb36,0xdcaf,0x1ffe,0x57d1,0x14c0}).toString();
            static final String L51 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4d32,0xd2fd,0xa21f}, new char[]{0xa27d,0xd29c,0x4d5e,0xad23}).plainString();
            static final String L52 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9810,0x2745,0x9f5e,0xe9f6,0x8405,0x4b73,0x9d68,0xf0b7,0xe4bf,0x5931,0x7607,0x808e,0xdb11,0x7ab6,0x6f84,0xf92b,0x264c,0x6bd0,0x8128,0xd491,0xb9ef,0xccf3,0xead4,0x2cc1,0x308d,0xed6d,0x48e2,0x2b1f,0xcf91,0x0de4,0x1b8c,0xb7dd,0xad24,0x6a48,0xbd3e,0xa42a,0xc087}, new char[]{0x9f1d,0xa445,0x5944,0x488e,0x2721,0xd4b1,0x6fea,0x2cae,0xc0f3,0x30ad,0xad56,0x2b7a,0xdb70,0x8461,0x80ae,0xcfe8,0x9d07,0x7672,0x0d96,0xb9cf,0xb7bf,0x9871,0xe4d3,0xed0c,0x4b1d,0x1bef,0x7ad3,0x6bf0,0x262a,0x815a,0x6a27,0xeab9,0xf097,0xccdc,0xbd5c,0xe997,0xf947}).toString();
            static final String L53 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0119,0x9af0,0x6df9,0x658b}, new char[]{0x9a80,0x0178,0x65f2,0x6dd9}).plainString();
            static final String L54 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5c48,0x6ba7,0x77f3,0xb716,0xa634,0x96b8,0xcbbc}, new char[]{0x96db,0x779c,0xcbd2,0x6bc1,0x5c21,0xa646,0xb77b}).plainString();
            static final String L55 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x642d,0x6769,0x682b}, new char[]{0x6852,0x6448,0x671a,0xe0b6}).plainString();
            static final String L56 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5e56,0x896d,0xb9a8}, new char[]{0x5e26,0x890c,0xb9d1,0x8785}).plainString();
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT2 {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7e73,0x1de4}, new char[]{0x1dde,0x7e53,0xdeed,0x1908}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa909}, new char[]{0xa92d,0x0408,0x4da4,0x307e}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x44d1}, new char[]{0x44ff,0x4581,0xd1ee,0x6381}).plainString();
    }
}
