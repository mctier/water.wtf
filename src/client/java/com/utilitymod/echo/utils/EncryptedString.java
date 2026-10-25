package com.utilitymod.echo.utils;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Encrypted literal carrier. Runtime output is NEVER plain ASCII: {@code toString()}
 * and {@code charAt} return Private-Use-Area chars (U+E100..U+E1FF), bit-identical to
 * the HeapCloak / ProtectedText mapping, so Aho-Corasick scanners over javaw process
 * memory (UTF-8 / UTF-16 word lists) cannot match decoded labels. The payload itself
 * is permuted by a stream cipher seeded from the key material.
 */
public class EncryptedString implements CharSequence {
	char[] key, value;
	int[] perm;

	private static final long MIX1 = 0x9E3779B97F4A7C15L;
	private static final long MIX2 = 0x27D4EB2F165667C5L;
	private static final long SEED_CONST = 0x2545F4914F6CDD1DL;

	/** Bit-identical to HeapCloak / ProtectedText PUA mapping (see spudfuscator-api). */
	private static final int PUA_BASE = 0xE100;

	public EncryptedString(String s) {
		int len = Math.min(s.length(), 64);
		char[] real = new char[len];
		ThreadLocalRandom current = ThreadLocalRandom.current();
		for (int i = 0; i < len; i++) {
			real[i] = (char) current.nextInt(0xFFFF);
		}
		char[] ca = s.substring(0, len).toCharArray();
		setup(real, ca);
	}

	public static EncryptedString of(String s) {
		return new EncryptedString(s);
	}

	public static EncryptedString of(CharSequence... parts) {
		StringBuilder value = new StringBuilder();
		for (CharSequence part : parts) {
			if (part != null) value.append(part);
		}
		return new EncryptedString(value.toString());
	}

	public static EncryptedString ofFloat(float value) {
		return of(Float.toString(value));
	}

	public static EncryptedString ofFixed(float value, int decimals) {
		return of(String.format(Locale.ROOT, LT.L1 + Math.max(0, decimals) + LT.L2, value));
	}

	/** Streams must match the external re-encoder byte for byte: xorshift64, unsigned Fisher-Yates. */
	private static long mixInto(long state, int c) {
		return Long.rotateLeft((state ^ c) * MIX2, 13);
	}

	private static long deriveSeed(char[] key, int length) {
		long seed = SEED_CONST ^ (MIX1 * (length * 31L + key.length));
		for (char c : key) seed = mixInto(seed, c);
		return seed;
	}

	private static int[] permutation(long seed, int n) {
		int[] perm = new int[n];
		for (int i = 0; i < n; i++) perm[i] = i;
		long st = seed;
		for (int i = n - 1; i > 0; i--) {
			st ^= st << 13;
			st ^= st >>> 7;
			st ^= st << 17;
			int j = Integer.remainderUnsigned((int) st, i + 1);
			int t = perm[i];
			perm[i] = perm[j];
			perm[j] = t;
		}
		return perm;
	}

	private void setup(char[] realKey, char[] plain) {
		this.key = realKey;
		this.perm = permutation(deriveSeed(realKey, plain.length), plain.length);
		char[] val = new char[plain.length];
		for (int i = 0; i < plain.length; i++) {
			val[perm[i]] = (char) (plain[i] ^ realKey[i % realKey.length]);
		}
		this.value = val;
	}

	/**
	 * Pre-encoded payload: value is a permuted XOR stream and key a plain key char array, both
	 * encoded by the build-time re-encoder with the identical permutation pipeline.
	 */
	public EncryptedString(char[] value, char[] key) {
		this.key = key;
		this.value = value;
		this.perm = permutation(deriveSeed(key, value.length), value.length);
	}

	@Override
	public int length() {
		return value.length;
	}

	@Override
	public char charAt(int index) {
		return (char) (PUA_BASE | ((value[perm[index]] ^ key[index % key.length]) & 0xFF));
	}

	@Override
	public @NotNull String toString() {
		char[] out = new char[value.length];
		for (int i = 0; i < out.length; i++) {
			out[i] = (char) (PUA_BASE | ((value[perm[i]] ^ key[i % key.length]) & 0xFF));
		}
		return new String(out);
	}

	/** Functional decode: plain ASCII for regex, wire, registry and format paths only. */
	public String plainString() {
		char[] out = new char[value.length];
		for (int i = 0; i < out.length; i++) {
			out[i] = (char) ((value[perm[i]] ^ key[i % key.length]) & 0xFF);
		}
		return new String(out);
	}

	@Override
	public @NotNull CharSequence subSequence(int start, int end) {
		char[] sub = new char[end - start];
		for (int i = start; i < end; i++) {
			sub[i - start] = (char) ((value[perm[i]] ^ key[i % key.length]) & 0xFF);
		}
		EncryptedString e = new EncryptedString(new char[0], key.clone());
		e.setup(key.clone(), sub);
		return e;
	}


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x832a,0x2743}, new char[]{0x830f,0x276d,0x4010,0xa7f2}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x57e6}, new char[]{0x5780,0xfd28,0x7b6a,0xcabf}).plainString();
    }
}
