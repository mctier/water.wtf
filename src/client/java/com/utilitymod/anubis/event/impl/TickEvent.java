package com.utilitymod.anubis.event.impl;

import com.utilitymod.anubis.event.Event;
import com.utilitymod.anubis.event.EventType;

public final class TickEvent extends Event {
   public static final EventType<TickEvent> TYPE = new EventType<>(LT.L1);
   private static final TickEvent INSTANCE = new TickEvent();

   private TickEvent() {
   }

   public static void dispatch() {
      TYPE.dispatch(INSTANCE);
   }

   public static void dispatchPost() {
      TYPE.dispatchPost(INSTANCE);
   }


    // cloak literals - runtime PUA carriers (cloak) or functional plain (LT)
    private static final class LT {
            static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x04c7,0x240d,0x9af3,0x1861,0xa65a,0x5e73,0x2cc7,0x5552,0x1d6d}, new char[]{0x1d39,0x2cae,0x5e10,0xa631,0x0482,0x247b,0x9a96,0x180f,0x5526}).toString();
    }
}
