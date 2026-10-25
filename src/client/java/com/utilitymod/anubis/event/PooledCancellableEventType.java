package com.utilitymod.anubis.event;

import java.util.function.Supplier;

public class PooledCancellableEventType<E extends CancellableEvent> extends PooledEventType<E> {
   public PooledCancellableEventType(Supplier<E> factory, int capacity) {
      super(factory, capacity);
   }

   public PooledCancellableEventType(String name, Supplier<E> factory, int capacity) {
      super(name, factory, capacity);
   }

   public void dispatchPre(E event) {
      CancellableEventType.dispatchPreCancellable(this.list, event);
   }

   public void dispatchPost(E event) {
      CancellableEventType.dispatchPostGuarded(this.list, event);
   }
}
