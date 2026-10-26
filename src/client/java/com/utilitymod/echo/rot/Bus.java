package com.utilitymod.echo.rot;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class Bus {
    private static final Map<Class<?>, Object> REGISTRY = new ConcurrentHashMap<>();

    private Bus() {
    }

    public static <T> void register(Class<T> type, T instance) {
        if (type != null && instance != null) {
            REGISTRY.put(type, instance);
        }
    }

    public static void unregister(Class<?> type) {
        if (type != null) {
            REGISTRY.remove(type);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T get(Class<T> type) {
        return (T) REGISTRY.get(type);
    }

    public static boolean has(Class<?> type) {
        return type != null && REGISTRY.containsKey(type);
    }
}
