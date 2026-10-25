package com.utilitymod.anubis.module.impl.misc;

import com.utilitymod.anubis.event.EventHandler;
import com.utilitymod.anubis.event.impl.PacketSendEvent;
import com.utilitymod.anubis.event.impl.TickEvent;
import com.utilitymod.anubis.module.ClientModule;
import com.utilitymod.anubis.module.ModuleCategory;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.Full;

public final class DonutNoFallModule extends ClientModule {
   private static final double TRIGGER_FALL_DISTANCE = 3.0;
   private static final int GROUND_PACKETS_TO_RESET = 4;
   private boolean noFall;
   private double lastFallDistance;
   private int groundTicks;
   private boolean sendingReplacement;

   public DonutNoFallModule() {
      super(LT.L1, LT.L2, LT.L3, ModuleCategory.DONUT);
   }

   @Override
   protected void onEnable() {
      this.noFall = false;
      this.lastFallDistance = 0.0;
      this.groundTicks = 0;
   }

   @EventHandler
   public void onTick(TickEvent event) {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      if (player != null) {
         if (this.noFall) {
            player.setOnGround(false);
         }

         if (this.lastFallDistance > 3.0) {
            this.noFall = true;
         }

         this.lastFallDistance = player.fallDistance;
      }
   }

   @EventHandler
   public void onPacketSend(PacketSendEvent event) {
      if (!this.sendingReplacement && event.packet instanceof PlayerMoveC2SPacket move) {
         MinecraftClient mc = MinecraftClient.getInstance();
         ClientPlayerEntity player = mc.player;
         ClientPlayNetworkHandler connection = mc.getNetworkHandler();
         if (player != null && connection != null) {
            event.setCancelled(true);
            if (this.noFall && move.isOnGround()) {
               this.groundTicks++;
            }

            if (this.groundTicks % 2 == 1) {
               player.setPos(player.getX(), player.getY() + 1.0E-8, player.getZ());
            }

            boolean onGround = !this.noFall && player.isOnGround();
            this.sendingReplacement = true;

            try {
               connection.sendPacket(
                  new Full(
                     player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch(), onGround, false
                  )
               );
            } finally {
               this.sendingReplacement = false;
            }

            if (this.groundTicks == 4) {
               this.noFall = false;
               this.groundTicks = 0;
            }
         }
      }
   }


    // cloak literals - runtime PUA carriers (cloak) or functional plain (LT)
    private static final class LT {
            static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe76a,0x0cf8,0x5d14,0xf102,0x49d0,0xbd52,0x38bf,0xe42f,0xc7a4,0x8883,0x092d,0x460b}, new char[]{0x0c9c,0x0942,0x5d7a,0xe45a,0x467f,0xf12f,0xe704,0xbd3d,0xc7c2,0x38de,0x49bc,0x88ef}).plainString();
            static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6e11,0x9f4f,0xa776,0x430d,0xf833,0x422f,0x82bc,0xfeda,0x4a93,0x926b,0x3664,0xde98}, new char[]{0xf877,0x9204,0x4363,0xfeaf,0x9f3b,0x4ab3,0x6e5f,0x82d3,0xa730,0xdef9,0x4243,0x3608}).toString();
            static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa3af,0x60d0,0x318e,0x8906,0x052f,0x7579,0x7de4,0xd228,0x2a6e,0xac25,0x8f0b,0x2882,0x4f99,0x6117,0x1084,0xab19,0xa678,0x6f04,0x20ab,0x9eae,0x52e9,0xe440,0x7452,0x2e0f,0xb531,0x41be,0x24e8,0x318e,0x4704,0xe1e0,0xbe88,0x8917}, new char[]{0x6f50,0x5288,0x7439,0x751c,0x8f2b,0xd246,0x2e60,0xe460,0x9ec8,0x054e,0x7d88,0xab75,0x10a4,0xbeec,0x60b1,0x2485,0xa619,0x4763,0xe185,0x8937,0x4ff6,0xa3c1,0x2a4e,0x31ca,0x20c4,0x31e0,0xac50,0x6163,0x28d1,0x894b,0x41ee,0xb51f}).toString();
    }
}
