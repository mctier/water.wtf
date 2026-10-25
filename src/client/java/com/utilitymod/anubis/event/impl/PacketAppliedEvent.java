package com.utilitymod.anubis.event.impl;

import com.utilitymod.anubis.event.Event;
import com.utilitymod.anubis.event.EventType;
import net.minecraft.network.packet.Packet;

public final class PacketAppliedEvent extends Event {
   public static final EventType<PacketAppliedEvent> TYPE = new EventType<>(LT.L1);
   public Packet<?> packet;
   private static final PacketAppliedEvent INSTANCE = new PacketAppliedEvent();

   private PacketAppliedEvent() {
   }

   public static void dispatch(Packet<?> packet) {
      if (TYPE.hasPreListeners()) {
         INSTANCE.packet = packet;
         TYPE.dispatch(INSTANCE);
         INSTANCE.packet = null;
      }
   }


    // cloak literals - runtime PUA carriers (cloak) or functional plain (LT)
    private static final class LT {
            static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb220,0x5058,0xb7e2,0xf3d8,0x4e73,0x2184,0x11bb,0xf3cf,0xe622,0x326a,0x4422,0x75b5,0xcae5,0xe9fd,0x6163,0xe4ba,0x960d,0x93c1}, new char[]{0xcab5,0x5039,0xb243,0x93aa,0x4447,0xe989,0x4e32,0xb792,0x321a,0xe64e,0x21ed,0xe4df,0x75d1,0x11fe,0xf3b9,0x6106,0xf3b6,0x9679}).toString();
    }
}
