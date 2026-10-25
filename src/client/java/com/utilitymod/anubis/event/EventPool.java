package com.utilitymod.anubis.event;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.function.Supplier;

public final class EventPool<E extends Event> {
   private final ArrayBlockingQueue<E> pool;
   private final Supplier<E> factory;

   public EventPool(Supplier<E> factory, int capacity) {
      this.factory = factory;
      this.pool = new ArrayBlockingQueue<>(capacity);
   }

   public E acquire() {
      E event = this.pool.poll();
      if (event == null) {
         event = this.factory.get();
      }

      return event;
   }

   public void release(E event) {
      this.pool.offer(event);
   }
}
