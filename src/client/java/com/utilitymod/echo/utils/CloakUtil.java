package com.utilitymod.echo.utils;

import dev.potato.spud.api.ProtectedText;

/**
 * Heap-safety boundary. Everything that lands in {@code Module.name} /
 * {@code Module.description} goes through {@link #heapSafe}: the resulting
 * String contains only Private-Use-Area chars (U+E100..U+E1FF), bit-identical
 * to the HeapCloak / ProtectedText mapping. Aho-Corasick scanners over javaw
 * process memory (UTF-8 / UTF-16 word lists) cannot match these.
 */
public final class CloakUtil {
    public static final int PUA_BASE = 0xE100;
    public static final int PUA_END = 0xE1FF;

    private CloakUtil() {}

    public static boolean isCloaked(char c) {
        return c >= PUA_BASE && c <= PUA_END;
    }

    /** Mask one heap char back to its plain form - no String materialization. */
    public static char plainChar(char c) {
        return isCloaked(c) ? (char) (c & 0xFF) : c;
    }

    /**
     * Return a heap-safe (PUA-only) String for any CharSequence. ProtectedText and
     * EncryptedString already produce PUA output; plain segments are masked char by
     * char, so mixed compositions (Concat pieces, literals) cloak as a whole.
     */
    public static String heapSafe(CharSequence cs) {
        if (cs == null) return "";
        if (cs instanceof EncryptedString) return cs.toString();
        if (cs instanceof ProtectedText pt) return pt.rawValue();
        StringBuilder sb = new StringBuilder(cs.length());
        for (int i = 0; i < cs.length(); i++) {
            char c = cs.charAt(i);
            sb.append(isCloaked(c) ? c : (char) (PUA_BASE | (c & 0xFF)));
        }
        return sb.toString();
    }
}
