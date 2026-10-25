// Vendored from SpudFuscator spudfuscator-api @ 1.0.0.
// Encoding MUST stay bit-identical to:
//   - HeapCloak.java in the obfuscator (bytecode XOR stream)
//   - tools/encrypt_names.py (CLI encoder)
//   - the injected RT.p([B)Ljava/lang/String; runtime
// Formula: enc[i] = plain[i] ^ ((0x1E ^ (i * 0x37)) & 0xFF)
package dev.potato.spud.api;

/**
 * Source-facing companion to the bytecode-level {@code HeapCloak} pass. Any
 * feature / setting name declared via {@link #fromInts(int[])} never
 * materializes as plaintext on the JVM heap — {@link #toString()} and
 * {@link #rawValue()} return a Private-Use-Area String ({@code U+E100..U+E1FF}),
 * which an Aho–Corasick ASCII/UTF-16 pattern DB cannot match.
 *
 * <p>Rendering paths that need glyphs call {@link #plainCharAt(int)} one char
 * at a time — the only call site allowed to observe plaintext.
 */
public final class ProtectedText implements CharSequence {

    private static final int KEY_A = 0x1E;
    private static final int KEY_STEP = 0x37;
    private static final int PUA_BASE = 0xE100;

    private final int[] enc;
    private volatile String rawCached;

    private ProtectedText(int[] enc) {
        this.enc = enc;
    }

    /** Wrap an encrypted byte-int array (0x00..0xFF) produced by {@code tools/encrypt_names.py}. */
    public static ProtectedText fromInts(int[] enc) {
        if (enc == null) {
            throw new IllegalArgumentException(LT.L1);
        }
        return new ProtectedText(enc.clone());
    }

    /**
     * Build-time helper: encrypt a plain string. <b>Never call this at runtime
     * with a flagged plaintext</b> — the argument itself is a heap-visible String.
     * Use the CLI tool ({@code tools/encrypt_names.py}) or bytecode-level
     * {@code HeapCloak} for anything the scanner would flag.
     */
    public static ProtectedText fromPlain(String plain) {
        int[] enc = new int[plain.length()];
        for (int i = 0; i < plain.length(); i++) {
            enc[i] = (plain.charAt(i) ^ key(i)) & 0xFF;
        }
        return new ProtectedText(enc);
    }

    /** Compute the encrypted int[] for a plaintext string (build-time tooling only). */
    public static int[] encrypt(String plain) {
        int[] enc = new int[plain.length()];
        for (int i = 0; i < plain.length(); i++) {
            enc[i] = (plain.charAt(i) ^ key(i)) & 0xFF;
        }
        return enc;
    }

    /** Return the PUA-encoded String view — safe to store, safe to pass around. */
    public String rawValue() {
        String r = rawCached;
        if (r != null) {
            return r;
        }
        char[] out = new char[enc.length];
        for (int i = 0; i < enc.length; i++) {
            int plain = (enc[i] ^ key(i)) & 0xFF;
            out[i] = (char) (PUA_BASE | plain);
        }
        r = new String(out);
        rawCached = r;
        return r;
    }

    /**
     * Return the decoded plain char at {@code i} — the ONE call site allowed
     * to observe plaintext, exactly one character at a time. Rendering paths
     * use this to draw glyph-by-glyph without ever building a full ASCII String.
     */
    public char plainCharAt(int i) {
        return (char) ((enc[i] ^ key(i)) & 0xFF);
    }

    /** Extract text as a fresh {@code String} — DO NOT use for anything a scanner would flag. */
    public String plainDangerous() {
        char[] out = new char[enc.length];
        for (int i = 0; i < enc.length; i++) {
            out[i] = plainCharAt(i);
        }
        return new String(out);
    }

    /** If {@code cs} is a {@code ProtectedText}, return its PUA form; else return {@code cs.toString()}. */
    public static String raw(CharSequence cs) {
        return (cs instanceof ProtectedText pt) ? pt.rawValue() : cs.toString();
    }

    /** {@code true} if any char sits in the cloak's PUA range. */
    public static boolean isCloaked(CharSequence cs) {
        if (cs instanceof ProtectedText) {
            return true;
        }
        for (int i = 0; i < cs.length(); i++) {
            char c = cs.charAt(i);
            if (c >= 0xE100 && c <= 0xE1FF) {
                return true;
            }
        }
        return false;
    }

    private static int key(int i) {
        return (KEY_A ^ (i * KEY_STEP)) & 0xFF;
    }

    @Override public int length() { return enc.length; }
    @Override public char charAt(int index) { return rawValue().charAt(index); }
    @Override public String toString() { return rawValue(); }

    @Override
    public CharSequence subSequence(int start, int end) {
        int[] sub = new int[end - start];
        System.arraycopy(enc, start, sub, 0, end - start);
        return new ProtectedText(sub);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ProtectedText pt && java.util.Arrays.equals(enc, pt.enc);
    }

    @Override
    public int hashCode() {
        return java.util.Arrays.hashCode(enc);
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf8a8,0x6174,0x8e6c,0x9fbe,0x879d,0x2f33,0x1bc2,0x0382,0xacd6,0x20c6,0x424a}, new char[]{0x9fdb,0x2f5d,0x8e0f,0x03a2,0xaceb,0x87a0,0x1be2,0x20a8,0x6101,0x4226,0xf8c4}).plainString();
    }
}
