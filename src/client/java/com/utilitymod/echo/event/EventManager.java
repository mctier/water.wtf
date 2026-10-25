package com.utilitymod.echo.event;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * echo event.EventManager (unchanged semantics): handlers are scanned for
 * single-argument {@link EventSubscribe} methods; post() dispatches by exact
 * event class in priority order and returns whether the event was cancelled.
 * Listener failures are logged, never thrown into game code.
 */
public final class EventManager {
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
    private static final Map<Class<?>, SortedMap<EventSubscribe.Priority, ListenerData>> listeners = new ConcurrentHashMap<>();
    private static final Map<Object, Boolean> subscribers = new ConcurrentHashMap<>();

    private EventManager() {}

    public static void register(Object handler) {
        if (handler == null || subscribers.containsKey(handler)) return;
        subscribers.put(handler, true);
        for (Class<?> clazz = handler.getClass(); clazz != null && clazz != Object.class; clazz = clazz.getSuperclass()) {
            for (Method method : clazz.getDeclaredMethods()) {
                Class<?>[] parameterTypes = method.getParameterTypes();
                if (parameterTypes.length != 1 || !Event.class.isAssignableFrom(parameterTypes[0])
                        || !method.isAnnotationPresent(EventSubscribe.class)) {
                    continue;
                }
                EventSubscribe.Priority priority = method.getAnnotation(EventSubscribe.class).priority();
                SortedMap<EventSubscribe.Priority, ListenerData> byPriority =
                        listeners.computeIfAbsent(parameterTypes[0], key -> new TreeMap<>());
                synchronized (byPriority) {
                    byPriority.computeIfAbsent(priority, key -> new ListenerData()).put(handler, method);
                }
            }
        }
    }

    public static void unregister(Object handler) {
        if (subscribers.remove(handler) == null) return;
        for (Map<EventSubscribe.Priority, ListenerData> datas : listeners.values()) {
            synchronized (datas) {
                for (ListenerData data : datas.values()) data.remove(handler);
            }
        }
    }

    public static boolean post(Event event) {
        SortedMap<EventSubscribe.Priority, ListenerData> datas = listeners.get(event.getClass());
        if (datas == null) return false;
        ListenerData[] snapshot;
        synchronized (datas) {
            snapshot = datas.values().toArray(new ListenerData[0]);
        }
        for (ListenerData data : snapshot) data.post(event);
        return event.cancelled;
    }

    private static final class ListenerData {
        private final Map<Object, MethodHandle[]> handlers = new ConcurrentHashMap<>();

        synchronized void put(Object handler, Method method) {
            MethodHandle[] old = handlers.get(handler);
            MethodHandle[] handles = old == null ? new MethodHandle[1] : java.util.Arrays.copyOf(old, old.length + 1);
            try {
                method.setAccessible(true);
                handles[handles.length - 1] = LOOKUP.unreflect(method);
            } catch (IllegalAccessException error) {
                error.printStackTrace();
                return;
            }
            handlers.put(handler, handles);
        }

        synchronized void remove(Object handler) {
            handlers.remove(handler);
        }

        void post(Event event) {
            for (Map.Entry<Object, MethodHandle[]> entry : handlers.entrySet()) {
                for (MethodHandle handle : entry.getValue()) {
                    long start = Profiler.enabled ? System.nanoTime() : 0L;
                    try {
                        handle.invoke(entry.getKey(), event);
                    } catch (Throwable error) {
                        com.utilitymod.UtilityMod.LOGGER.warn(LT.L1,
                                entry.getKey().getClass().getSimpleName(), event.getClass().getSimpleName(), error.toString());
                    }
                    if (start != 0L) Profiler.record(entry.getKey(), System.nanoTime() - start);
                }
            }
        }
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3958,0xe4df,0x04c5,0xa666,0xdc07,0x8da4,0x11cc,0x950b,0xf09e,0x0e0f,0x4ad5,0x5bc1,0x1d2e,0x6254,0xe27c,0xbb77,0xa81d,0xd822,0x2b8a,0xd549,0x5d49,0x71c3,0xdfa8,0x35b3,0x3483,0xf3df}, new char[]{0xd512,0x393d,0x2be9,0x11a4,0x8dcb,0x719e,0x3593,0xa866,0x4aa8,0xdf88,0xe4b9,0x5ba0,0x0e66,0x5d25,0xf0fb,0x1d4a,0x6274,0xbb18,0x9565,0xe25c,0xd859,0xf3a2,0xa65c,0x34a3,0xdc7c,0x04b8}).plainString();
    }
}
