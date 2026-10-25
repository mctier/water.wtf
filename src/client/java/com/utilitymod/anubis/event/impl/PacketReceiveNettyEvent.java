package com.utilitymod.anubis.event.impl;

import com.utilitymod.anubis.event.CancellableEvent;
import com.utilitymod.anubis.event.PooledCancellableEventType;
import net.minecraft.network.packet.Packet;

public final class PacketReceiveNettyEvent extends CancellableEvent {
   public static final PooledCancellableEventType<PacketReceiveNettyEvent> TYPE = new PooledCancellableEventType<>(
      LT.L1, PacketReceiveNettyEvent::new, 1024
   );
   public Packet<?> packet;

   private PacketReceiveNettyEvent() {
   }

   public static boolean dispatchSync(Packet<?> packet) {
      if (!TYPE.hasPreListeners()) {
         return false;
      }

      PacketReceiveNettyEvent event = TYPE.pool.acquire();
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
            static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb201,0xd580,0x0bee,0x6124,0x742c,0x24df,0xee27,0x2060,0xddc9,0x8abe,0xc431,0xdd0f,0xa8d5,0x1a71,0x0ca5,0xdad6,0x73c9,0xa1c8,0x5ad5,0xc8e0,0x5d72,0x52c3,0xb7ba}, new char[]{0xda86,0xdda8,0x6147,0xa8be,0xee42,0xc445,0xb253,0xd5e5,0xa1ab,0x8adb,0x73a0,0x52b5,0x2005,0xb7f4,0x5d17,0x1a05,0x0b9a,0xc899,0x249a,0xdd79,0x7449,0x0ccb,0x5aa1}).toString();
    }
}
