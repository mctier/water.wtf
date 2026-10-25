package com.utilitymod.jni;

import com.utilitymod.UtilityMod;
import com.utilitymod.core.ModuleManager;
import com.utilitymod.core.TickDispatcher;

/**
 * JNI/JVMTI bridge - consistent logic that will not break.
 * Java side owns state. Native side only signals via this class.
 * All native-triggered actions are deferred to tick START via TickDispatcher.
 *
 * Native DLL should implement:
 *   JNIEXPORT void JNICALL Java_com_utilitymod_jni_NativeBridge_nativeSignal(JNIEnv*, jclass, jint code, jlong payload)
 * and call nativeSignal() from JVMTI callbacks.
 */
public final class NativeBridge {
    private static boolean loaded = false;

    static {
        try {
            // Optional native lib - if not present, pure Java still works
            System.loadLibrary(LT.L1);
            loaded = true;
            UtilityMod.LOGGER.info(LT.L2);
        } catch (UnsatisfiedLinkError e) {
            UtilityMod.LOGGER.info(LT.L3, e.getMessage());
        }
    }

    private NativeBridge() {}

    public static boolean isLoaded() { return loaded; }

    // Called from native (JVMTI/JNI) - must be fast, just enqueue to tick
    public static void nativeSignal(int code, long payload) {
        // Codes: 1=toggle module by index, 2=toggle menu, 10+ custom
        TickDispatcher.onTickStart(() -> handleSignalTickStart(code, payload));
        // Note: we use one-shot task; alternative is to enqueue and handle in next START
        // To avoid leak, we auto-remove after one execution
    }

    // JNI entry point - native calls this
    public static void nativeSignalJNI(int code, long payload) {
        nativeSignal(code, payload);
    }

    private static void handleSignalTickStart(int code, long payload) {
        // This runs at START_CLIENT_TICK, safe to enqueue packets
        switch (code) {
            case 2 -> com.utilitymod.gui.FrameManager.toggle();
            case 1 -> {
                // payload = module index
                var mods = new java.util.ArrayList<>(ModuleManager.all());
                int idx = (int) payload;
                if (idx >= 0 && idx < mods.size()) {
                    var m = mods.get(idx);
                    m.setEnabled(!m.isEnabled());
                }
            }
            default -> {
                // Broadcast to modules - consistent path
                for (var m : ModuleManager.all()) {
                    try { m.onNativeSignal(code, payload); } catch (Throwable t) { t.printStackTrace(); }
                }
            }
        }
    }

    // Java -> Native call example (if native loaded)
    public static native void notifyModuleToggle(String moduleName, boolean enabled);
    public static native void pollNativeEvents();

    // Fallback if native not loaded
    public static void safeNotifyToggle(String module, boolean enabled) {
        if (!loaded) return;
        try { notifyModuleToggle(module, enabled); } catch (UnsatisfiedLinkError ignored) {}
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9c62,0xe3b5,0xb109,0xd4d1,0x5316,0x3a93,0xd88a,0x0985,0xcb49,0xa4ea,0x827b,0x1905,0xe4d6,0xa25e,0x67d7,0x3ad8,0x35c0}, new char[]{0xcb3c,0xa22a,0x67be,0xa486,0x3afa,0x09f1,0xd4a8,0x8216,0xb166,0xd8ee,0x1928,0xe4b8,0xe3d4,0x35b4,0x3ab1,0x9c14,0x5373}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd4d0,0xd8bc,0xd2ff,0xd9b5,0x0b3c,0x8992,0x2156,0x67c9,0xa1ea,0x19c9,0x59cb,0x63d3,0xa7a3,0xe3f3,0x6fb8,0xc08b,0xcce3,0xa8b4,0x171a,0xbc0e,0xed39,0x07d6,0x5ec4,0xedf6,0xff2c,0x2a67,0xda19,0xfdd7,0x4194,0x1f30,0x7bcf,0xbd08,0x1720,0x6e2f,0x50e6,0x0f37,0x2cc1,0xbcca,0x7977}, new char[]{0xa1b1,0xedb8,0xd9d4,0x6e5b,0x67a0,0xcc95,0x1f55,0x0f75,0xc0f9,0xda70,0x2ca5,0x07b1,0xbd6d,0xff71,0xa783,0xd49c,0x7918,0x5087,0x89f6,0x2133,0x1744,0x5ee4,0x59be,0x2a13,0xed50,0x0b50,0xbc67,0xd28b,0xd8c5,0x41f9,0x6fd7,0x177e,0x19e4,0x7ba1,0xfdb6,0xbcbe,0xe39a,0x63a5,0xa8d1}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x26f1,0xce17,0x280b,0x39b4,0x53da,0x0fbb,0xdca0,0x8bb6,0x8205,0xc0f7,0x3f09,0x5d99,0x86e7,0x8054,0x6188,0x8337,0x0443,0xb0b4,0x9348,0x6ce0,0xd284,0xefe0,0x8f29,0x35cd,0xdf49,0xca0d,0x1d1c,0x3507,0xbc97,0x222b,0x3d05,0x8b70,0x0830,0x1cb4,0x49a0,0x16c3,0x0f24,0x8345,0xcf2d,0x0f52,0x1315,0xc9f3,0x386f,0x9951,0xb1ec,0x733d,0xe209,0x2b56,0x39d1,0xc2ad,0xbf03,0x2602,0xce24,0x7a57,0x756b,0xe574,0x8417,0xa65a,0xfeb2}, new char[]{0xc2f6,0xefae,0xdcc1,0x8271,0x8b19,0x3571,0x8320,0xb1ae,0xfec0,0x6c89,0x49c4,0xc994,0x7a32,0x9315,0x8f09,0x86a9,0x5df8,0xce50,0x2b3f,0x1363,0x1d79,0xe229,0x8bda,0x7502,0x1cd6,0xd2a4,0x803a,0x993e,0xbce3,0xbf23,0xce71,0x39db,0xdf3c,0x3d6b,0x0fdf,0x61a8,0x3f24,0xa67a,0xb0c6,0xca78,0x16ad,0x0f4a,0xe51d,0x8479,0x286c,0x0463,0x7377,0x224a,0x3819,0x53bb,0x26dc,0x35a2,0x0f3c,0xc09b,0xcf54,0x39eb,0x2622,0x084b,0x834a}).plainString();
    }
}
