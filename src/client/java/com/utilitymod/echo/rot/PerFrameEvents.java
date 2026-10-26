package com.utilitymod.echo.rot;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Clarity port: frame-callback runner. Diag instrumentation stripped. */
public final class PerFrameEvents {
    private static final List<Runnable> CALLBACKS = new CopyOnWriteArrayList<>();

    private PerFrameEvents() {
    }

    public static void register(Runnable callback) {
        if (!CALLBACKS.contains(callback)) {
            CALLBACKS.add(callback);
        }
    }

    public static void unregister(Runnable callback) {
        CALLBACKS.remove(callback);
    }

    public static void fire() {
        Iterator<Runnable> iterator = CALLBACKS.iterator();
        while (iterator.hasNext()) {
            Runnable callback = iterator.next();
            try {
                callback.run();
            } catch (Throwable ignored) {
                // callback isolation mirrors clarity: one bad frame never kills the loop
            }
        }
    }
}
