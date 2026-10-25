package com.utilitymod.anubis.event.impl;

import com.utilitymod.anubis.event.CancellableEvent;
import com.utilitymod.anubis.event.PooledCancellableEventType;
import net.minecraft.network.packet.Packet;

public final class PacketSendEvent extends CancellableEvent {
   public static final PooledCancellableEventType<PacketSendEvent> TYPE = new PooledCancellableEventType<>(LT.L1, PacketSendEvent::new, 1024);
   public Packet<?> packet;

   private PacketSendEvent() {
   }

   public static boolean dispatchSync(Packet<?> packet) {
      if (!TYPE.hasPreListeners()) {
         return false;
      }

      PacketSendEvent event = TYPE.pool.acquire();
      event.packet = packet;
      event.resetCancellation();

      try {
         TYPE.dispatch(event);
         return event.isCancelled();
      } finally {
         event.packet = null;
         TYPE.pool.release(event);
      }
   }


    // cloak literals - runtime PUA carriers (cloak) or functional plain (LT)
    private static final class LT {
            static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xcb8a,0x4f52,0x8bdf,0xd1e5,0x136a,0xc2ac,0x354d,0xcc65,0xa836,0xc2ca,0xeaf3,0x9d67,0x8db1,0x6314,0xa07a}, new char[]{0x8b8f,0xcbeb,0x9d04,0xea98,0x6371,0xcc11,0xa029,0x130f,0xc2c2,0x8dd5,0xa873,0xd193,0x3528,0x4f3c,0xc2be}).toString();
    }
}
