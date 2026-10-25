package com.utilitymod.anubis.event;

import java.util.function.Consumer;

public class EventType<E extends Event> {
   protected final ListenerList<E> list = new ListenerList<>();
   private final String name;

   public EventType() {
      this(LT.L1);
   }

   public EventType(String name) {
      this.name = name;
   }

   public final String name() {
      return this.name;
   }

   public void register(ListenerHandle<E> handle) {
      this.list.register(handle);
   }

   public void unregister(ListenerHandle<E> handle) {
      this.list.unregister(handle);
   }

   public ListenerHandle<E> listen(Object owner, Consumer<E> callback) {
      return this.listen(owner, Stage.PRE, EventPriority.NORMAL, callback);
   }

   public ListenerHandle<E> listen(Object owner, EventPriority priority, Consumer<E> callback) {
      return this.listen(owner, Stage.PRE, priority, callback);
   }

   public ListenerHandle<E> listen(Object owner, Stage stage, EventPriority priority, Consumer<E> callback) {
      ListenerHandle<E> handle = new ListenerHandle<>(this, stage, priority, owner, callback);
      this.register(handle);
      return handle;
   }

   public boolean hasPreListeners() {
      return this.list.preCallbacks().length != 0;
   }

   public boolean hasPostListeners() {
      return this.list.postCallbacks().length != 0;
   }

   public boolean hasListeners() {
      return this.hasPreListeners() || this.hasPostListeners();
   }

   public final void dispatch(E event) {
      this.dispatchPre(event);
   }

   public void dispatchPre(E event) {
      this.list.dispatchPre(event);
   }

   public void dispatchPost(E event) {
      this.list.dispatchPost(event);
   }


    // cloak literals - runtime PUA carriers (cloak) or functional plain (LT)
    private static final class LT {
            static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9cd3,0xad8e,0x0982,0x5f9f,0xfd48,0x53cc,0x816d}, new char[]{0x8118,0x53a2,0xade0,0xfd29,0x09ef,0x9cb6,0x5ffb}).plainString();
    }
}
