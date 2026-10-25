package com.utilitymod.anubis.util;

/**
 * PUA-breath mask utilities for the anubis side: all module/setting/label strings circulate as
 * PUA carriers (U+E100..U+E1FF); this is the only chokepoint that unmasks them, for functional
 * boundaries (chat text, wire payloads, config keys) so the plain form never sits in a heap
 * field of its own.
 */
public final class TextMask {
    private TextMask() {}

    public static String plain(CharSequence cs) {
        if (cs == null) return "";
        StringBuilder out = new StringBuilder(cs.length());
        for (int i = 0; i < cs.length(); i++) {
            char c = cs.charAt(i);
            out.append(c >= 0xE100 && c <= 0xE1FF ? (char) (c & 0xFF) : c);
        }
        return out.toString();
    }

    /** Case-insensitive compare after unmasking both sides; works across PUA/plain carriers. */
    public static boolean eq(CharSequence a, CharSequence b) {
        if (a == null || b == null) return a == b;
        String p = plain(a);
        String q = plain(b);
        if (p.length() != q.length()) return false;
        for (int i = 0; i < p.length(); i++) {
            if (fold(p.charAt(i)) != fold(q.charAt(i))) return false;
        }
        return true;
    }

    /** Plain ASCII -> PUA carrier (inverse of plain). Non-Latin1 chars pass through untouched. */
    public static String mask(CharSequence cs) {
        if (cs == null) return "";
        StringBuilder out = new StringBuilder(cs.length());
        for (int i = 0; i < cs.length(); i++) {
            char c = cs.charAt(i);
            out.append(c < 0x100 ? (char) (0xE100 | c) : c);
        }
        return out.toString();
    }

    /** Lowercased plain token, for lookup keys that may arrive as either carrier or user text. */
    public static String key(CharSequence cs) {
        return plain(cs).toLowerCase(java.util.Locale.ROOT);
    }

    private static char fold(char c) {
        char up = Character.toUpperCase(c);
        return Character.toLowerCase(up);
    }
}
