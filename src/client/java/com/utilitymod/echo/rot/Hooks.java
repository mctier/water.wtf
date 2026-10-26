package com.utilitymod.echo.rot;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Clarity port: string-keyed lambda maps, the glue between engine and mixin bodies. */
public final class Hooks {
    static final Map<String, Bool0> BOOL0 = new ConcurrentHashMap<>();
    static final Map<String, Bool1> BOOL1 = new ConcurrentHashMap<>();
    static final Map<String, Dbl0> DBL0 = new ConcurrentHashMap<>();
    static final Map<String, Dbl1> DBL1 = new ConcurrentHashMap<>();
    static final Map<String, Obj0> OBJ0 = new ConcurrentHashMap<>();
    static final Map<String, Obj1> OBJ1 = new ConcurrentHashMap<>();
    static final Map<String, Obj2> OBJ2 = new ConcurrentHashMap<>();
    static final Map<String, Dbl2> DBL2 = new ConcurrentHashMap<>();
    static final Map<String, Act0> ACT0 = new ConcurrentHashMap<>();
    static final Map<String, Act1> ACT1 = new ConcurrentHashMap<>();

    private Hooks() {
    }

    public interface Bool0 { boolean get(); }
    public interface Bool1 { boolean get(Object input); }
    public interface Dbl0 { double get(); }
    public interface Dbl1 { double get(Object input); }
    public interface Dbl2 { double get(Object input, Object input2); }
    public interface Obj0 { Object get(); }
    public interface Obj1 { Object get(Object input); }
    public interface Obj2 { Object get(Object input, Object input2); }
    public interface Act0 { void run(); }
    public interface Act1 { void run(Object input); }

    public static void bool0(String key, Bool0 hook) {
        if (key != null && hook != null) {
            BOOL0.put(key, hook);
        }
    }

    public static void bool1(String key, Bool1 hook) {
        if (key != null && hook != null) {
            BOOL1.put(key, hook);
        }
    }

    public static void dbl0(String key, Dbl0 hook) {
        if (key != null && hook != null) {
            DBL0.put(key, hook);
        }
    }

    public static void dbl1(String key, Dbl1 hook) {
        if (key != null && hook != null) {
            DBL1.put(key, hook);
        }
    }

    public static void dbl2(String key, Dbl2 hook) {
        if (key != null && hook != null) {
            DBL2.put(key, hook);
        }
    }

    public static void obj0(String key, Obj0 hook) {
        if (key != null && hook != null) {
            OBJ0.put(key, hook);
        }
    }

    public static void obj1(String key, Obj1 hook) {
        if (key != null && hook != null) {
            OBJ1.put(key, hook);
        }
    }

    public static void obj2(String key, Obj2 hook) {
        if (key != null && hook != null) {
            OBJ2.put(key, hook);
        }
    }

    public static void act0(String key, Act0 hook) {
        if (key != null && hook != null) {
            ACT0.put(key, hook);
        }
    }

    public static void act1(String key, Act1 hook) {
        if (key != null && hook != null) {
            ACT1.put(key, hook);
        }
    }

    public static boolean bool(String key, boolean fallback) {
        Bool0 hook = BOOL0.get(key);
        return hook != null ? hook.get() : fallback;
    }

    public static boolean bool1(String key, Object input, boolean fallback) {
        Bool1 hook = BOOL1.get(key);
        return hook != null ? hook.get(input) : fallback;
    }

    public static double dbl(String key, double fallback) {
        Dbl0 hook = DBL0.get(key);
        return hook != null ? hook.get() : fallback;
    }

    public static double dbl1(String key, Object input, double fallback) {
        Dbl1 hook = DBL1.get(key);
        return hook != null ? hook.get(input) : fallback;
    }

    public static double dbl2(String key, Object input, Object input2, double fallback) {
        Dbl2 hook = DBL2.get(key);
        return hook != null ? hook.get(input, input2) : fallback;
    }

    public static Object obj(String key) {
        Obj0 hook = OBJ0.get(key);
        return hook != null ? hook.get() : null;
    }

    public static Object obj1(String key, Object input) {
        Obj1 hook = OBJ1.get(key);
        return hook != null ? hook.get(input) : null;
    }

    public static Object obj2(String key, Object input, Object input2) {
        Obj2 hook = OBJ2.get(key);
        return hook != null ? hook.get(input, input2) : null;
    }

    public static void run(String key) {
        Act0 hook = ACT0.get(key);
        if (hook != null) {
            hook.run();
        }
    }

    public static void run1(String key, Object input) {
        Act1 hook = ACT1.get(key);
        if (hook != null) {
            hook.run(input);
        }
    }
}
