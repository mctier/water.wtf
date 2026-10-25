package com.utilitymod.anubis.event.impl;

import com.utilitymod.anubis.event.Event;
import com.utilitymod.anubis.event.EventType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.BlockState;

public final class BlockUpdateEvent extends Event {
   public static final EventType<BlockUpdateEvent> TYPE = new EventType<>(LT.L1);
   public BlockPos pos;
   public BlockState oldState;
   public BlockState newState;
   private static final BlockUpdateEvent INSTANCE = new BlockUpdateEvent();

   private BlockUpdateEvent() {
   }

   public static void dispatch(BlockPos pos, BlockState oldState, BlockState newState) {
      if (TYPE.hasPreListeners()) {
         INSTANCE.pos = pos;
         INSTANCE.oldState = oldState;
         INSTANCE.newState = newState;
         TYPE.dispatch(INSTANCE);
         INSTANCE.pos = null;
         INSTANCE.oldState = null;
         INSTANCE.newState = null;
      }
   }


    // cloak literals - runtime PUA carriers (cloak) or functional plain (LT)
    private static final class LT {
            static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9fe8,0x0d1e,0xe494,0x3f7f,0xe3f4,0xee26,0xbfa0,0xcbc4,0x0d96,0x88f4,0xcddd,0x5be4,0x7af4,0xb36a,0x4848,0x2cb4}, new char[]{0x0dd4,0x4824,0x5b8b,0xe4f7,0x889f,0x7aa1,0x9f98,0x3f1b,0x0d7f,0xbfd4,0x2cd1,0xe3b1,0xb31c,0xcdb8,0xee48,0xcbb0}).toString();
    }
}
