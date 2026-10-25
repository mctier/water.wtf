package com.utilitymod.anubis.event;


@FunctionalInterface
public interface Listener<E extends Event> {
   void invoke(E var1);
}
