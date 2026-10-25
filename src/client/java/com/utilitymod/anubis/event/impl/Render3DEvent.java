package com.utilitymod.anubis.event.impl;

import com.utilitymod.anubis.event.Event;
import com.utilitymod.anubis.event.EventType;
import net.minecraft.client.render.Frustum;

public final class Render3DEvent extends Event {
   public static final EventType<Render3DEvent> TYPE = new EventType<>(LT.L1);
   public float partialTick;
   public Frustum frustum;
   private static final Render3DEvent INSTANCE = new Render3DEvent();
   private static boolean rendering;
   private static long frame;
   private static long lastDurationNanos;

   private Render3DEvent() {
   }

   public static void dispatch(float partialTick) {
      dispatch(partialTick, null);
   }

   public static void dispatch(float partialTick, Frustum frustum) {
      if (!TYPE.hasListeners()) {
         lastDurationNanos = 0L;
      } else {
         if (rendering) {
            throw new IllegalStateException(LT.L2);
         }

         rendering = true;
         long start = System.nanoTime();
         INSTANCE.partialTick = partialTick;
         INSTANCE.frustum = frustum;

         try {
            TYPE.dispatchPre(INSTANCE);
            TYPE.dispatchPost(INSTANCE);
         } finally {
            INSTANCE.frustum = null;
            lastDurationNanos = System.nanoTime() - start;
            frame++;
            rendering = false;
         }
      }
   }

   public static long frame() {
      return frame;
   }

   public static long lastDurationNanos() {
      return lastDurationNanos;
   }


    // cloak literals - runtime PUA carriers (cloak) or functional plain (LT)
    private static final class LT {
            static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x118d,0xc133,0xfa79,0x22cc,0x586e,0x2ca5,0x41d1,0x8db0,0xa96b,0xb18e,0x5edb,0xa848,0x79c2}, new char[]{0x583c,0x2cc0,0xa826,0x22a8,0x5ebe,0xfa0b,0xb1bd,0x8df4,0x11c8,0xa91d,0x41b4,0x79ac,0xc147}).toString();
            static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1774,0x524c,0x6c66,0x8b10,0xc0e1,0x7700,0xca48,0x7c00,0x5e1b,0x65a0,0xffec,0x89cf,0x61b4,0xd8c5,0x363c,0x82d9,0xd303,0xfb3b,0xb6ba,0x264f,0x1095,0x9b6f,0x8453,0xa756,0xd04b,0xb779,0xb846,0x5e28,0xc3f2,0xd28b}, new char[]{0x8401,0xca2d,0x6c08,0x89ab,0x10f0,0x8b62,0x6593,0x61f0,0xc3b7,0xb830,0x7765,0xa738,0xd8b1,0x266f,0x5e78,0xd2ea,0x3652,0xb717,0xff83,0xc095,0xd323,0x82bb,0xd02e,0x7c20,0xfb55,0x5229,0xb6c9,0x1700,0x5e4d,0x9b0b}).toString();
    }
}
