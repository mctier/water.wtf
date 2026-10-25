package com.utilitymod.anubis.chat;

import com.utilitymod.anubis.module.ClientModule;
import net.minecraft.text.Text;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
public final class ModuleChat {
   private static final String HEADER = LT.L1;
   private static final int DARK_GREY = 5592405;
   private static final int GREY = 11184810;
   private static final int WHITE = 16777215;
   private static final int DANGER = 16733525;
   private static final int GOOD = 5635925;
   private static final int WARN = 16755200;
   private static final int VALUE = 5636095;

   private ModuleChat() {
   }

   public static ModuleChat.Body body() {
      return new ModuleChat.Body();
   }

   public static void send(ClientModule module, Text body) {
      onClientThread(() -> {
         if (module.notificationsOn()) {
            if (com.utilitymod.echo.features.impl.render.Notifications.showsMessages()) {
               com.utilitymod.echo.features.impl.render.Notifications.push(module.name(), body.getString(),
                  com.utilitymod.echo.features.impl.render.Notifications.Type.INFO);
            } else {
               addLine(module, body);
            }
         }
      });
   }

   public static void send(ClientModule module, ModuleChat.Body body) {
      send(module, body.build());
   }

   public static void send(ClientModule module, String text) {
      send(module, Text.literal(text));
   }

   public static void actionBar(ClientModule module, ModuleChat.Body body) {
      Text built = body.build();
      onClientThread(() -> {
         MinecraftClient mc = MinecraftClient.getInstance();
         if (module.notificationsOn() && mc.inGameHud != null) {
            mc.inGameHud.setOverlayMessage(line(module, built), false);
         }
      });
   }

   public static void toggleLine(ClientModule module, ModuleChat.Body state) {
      send(module, state);
   }

   private static void addLine(ClientModule module, Text body) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.inGameHud != null) {
         mc.inGameHud.getChatHud().addMessage(line(module, body));
      }
   }

   private static MutableText line(ClientModule module, Text body) {
      return Text.empty()
         .append(Text.literal(LT2.L1).withColor(5592405))
         .append(Text.literal(LT.L2).withColor(dev.koifih.client.ui.Theme.accentRgb() & 0xFFFFFF))
         .append(Text.literal(LT2.L3).withColor(5592405))
         .append(Text.empty().withColor(11184810).append(body));
   }

   private static void onClientThread(Runnable task) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.isOnThread()) {
         task.run();
      } else {
         mc.execute(task);
      }
   }
   public static final class Body {
      private final MutableText root = Text.empty().withColor(11184810);

      private Body() {
      }

      public ModuleChat.Body text(String text) {
         this.root.append(Text.literal(text));
         return this;
      }

      public ModuleChat.Body name(String name) {
         return this.part(name, 16777215);
      }

      public ModuleChat.Body danger(String text) {
         return this.part(text, 16733525);
      }

      public ModuleChat.Body good(String text) {
         return this.part(text, 5635925);
      }

      public ModuleChat.Body warn(String text) {
         return this.part(text, 16755200);
      }

      public ModuleChat.Body value(Object value) {
         return this.part(String.valueOf(value), 5636095);
      }

      public ModuleChat.Body coords(int x, int y, int z) {
         return this.part(x + LT2.L4 + y + LT2.L4 + z, 5636095);
      }

      public ModuleChat.Body append(Text component) {
         this.root.append(component);
         return this;
      }

      public Text build() {
         return this.root.copy();
      }

      private ModuleChat.Body part(String text, int rgb) {
         this.root.append(Text.literal(text).withColor(rgb));
         return this;
      }
   }


    // cloak literals - runtime PUA carriers (cloak) or functional plain (LT)
    private static final class LT {
            static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x435b,0x5e55,0x5218,0x0fd7,0x6bea,0xb2d1,0x15aa,0x277c,0xd00c,0xd49e,0xdf0f,0x4d80,0x08f3}, new char[]{0x08b2,0x4dee,0xd4eb,0x6b88,0x0fbe,0x4328,0xb2f1,0xdf4c,0x5e39,0x5271,0xd069,0x15c4,0x2708}).toString();
            static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf724,0x8667,0xb7fb,0x08f5,0xa37a,0x0850,0xbfe7,0xa43a,0xcb69,0x7a2f}, new char[]{0x7a5a,0x0824,0xa453,0xb797,0xf74d,0xcb1d,0x861e,0xa317,0x089a,0xbf83}).plainString();
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT2 {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc378}, new char[]{0xc323,0xb23f,0xdae3,0xf838}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x82b7,0x2d75}, new char[]{0x2d28,0x8297,0xf165,0x7c65}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xcc01,0x8773,0x73e2}, new char[]{0x8753,0xcc3b,0x73c2,0x7fd3}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xddeb,0xd2fa}, new char[]{0xddc7,0xd2da,0xb305,0x8c6c}).plainString();
    }
}
