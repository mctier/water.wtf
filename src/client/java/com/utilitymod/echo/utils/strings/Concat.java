package com.utilitymod.echo.utils.strings;

import org.jetbrains.annotations.NotNull;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

// I really hate myself
public abstract class Concat implements CharSequence {

    public static Concat of(String... parts) { return new Fragmented(parts); }
    public static Concat of(CharSequence... parts) { return new Joined(parts); }
    public static Concat ofChars(char[] chars) { return new CharArray(chars); }
    public static Concat ofInt(int value) { return new CharArray(Integer.toString(value).toCharArray()); }
    public static Concat ofFloat(float value) { return new CharArray(Float.toString(value).toCharArray()); }
    public static Concat ofFixed(float value, int decimals) {
        return new CharArray(String.format(Locale.ROOT, LT.L1 + decimals + LT.L2, value).toCharArray());
    }
    public static Concat ofEncoded(byte[] encoded, int seed) { return new Encoded(encoded, seed); }

    public abstract boolean fragmentedEquals(Concat other);
    public CharSequence toLowerCase() { return new LowerCase(this); }

    protected void checkBounds(int index, int length) {
        if (index < 0 || index >= length) throw new IndexOutOfBoundsException(index);
    }

    protected void checkRange(int start, int end, int length) {
        if (start < 0 || end > length || start > end) throw new IndexOutOfBoundsException();
    }

    protected boolean contentEquals(Concat other) {
        if (length() != other.length()) return false;
        for (int i = 0; i < length(); i++) {
            // mask PUA cloak chars so mixed plain/PUA compositions still compare equal
            if (com.utilitymod.echo.utils.CloakUtil.plainChar(charAt(i))
                    != com.utilitymod.echo.utils.CloakUtil.plainChar(other.charAt(i))) return false;
        }
        return true;
    }

    public static String hash(CharSequence seq) {
        if (seq == null) return null;
        if (seq instanceof I18nText text) {
            seq = text.stableHashSource();
        }
        try {
            MessageDigest md = MessageDigest.getInstance(LT.L3);
            for (int i = 0, len = seq.length(); i < len; i++) {
                char c = seq.charAt(i);
                if (c < 0x80) {
                    md.update((byte) c);
                } else if (c < 0x800) {
                    md.update((byte) (0xC0 | (c >> 6)));
                    md.update((byte) (0x80 | (c & 0x3F)));
                } else if (Character.isHighSurrogate(c) && i + 1 < len && Character.isLowSurrogate(seq.charAt(i + 1))) {
                    int cp = Character.toCodePoint(c, seq.charAt(++i));
                    md.update((byte) (0xF0 | (cp >> 18)));
                    md.update((byte) (0x80 | ((cp >> 12) & 0x3F)));
                    md.update((byte) (0x80 | ((cp >> 6) & 0x3F)));
                    md.update((byte) (0x80 | (cp & 0x3F)));
                } else {
                    md.update((byte) (0xE0 | (c >> 12)));
                    md.update((byte) (0x80 | ((c >> 6) & 0x3F)));
                    md.update((byte) (0x80 | (c & 0x3F)));
                }
            }
            byte[] hash = md.digest();
            StringBuilder sb = new StringBuilder(16);
            for (int i = 0; i < 8; i++) sb.append(String.format(LT.L4, hash[i] & 0xff));
            return sb.toString();
        } catch (Exception e) { return null; }
    }

    private record LowerCase(CharSequence src) implements CharSequence {
        public int length() { return src.length(); }
        public char charAt(int i) { return Character.toLowerCase(src.charAt(i)); }
        public CharSequence subSequence(int s, int e) { return new LowerCase(src.subSequence(s, e)); }
        @Override public @NotNull String toString() {
            StringBuilder sb = new StringBuilder(length());
            for (int i = 0; i < length(); i++) sb.append(charAt(i));
            return sb.toString();
        }
    }

    private static class Fragmented extends Concat {
        private final String[] frags;
        private final int[] offsets;
        private final int len;

        Fragmented(String[] parts) {
            frags = parts.clone();
            offsets = new int[parts.length];
            int off = 0;
            for (int i = 0; i < parts.length; i++) { offsets[i] = off; off += parts[i].length(); }
            len = off;
        }

        @Override public int length() { return len; }

        @Override public char charAt(int index) {
            checkBounds(index, len);
            for (int i = 0; i < frags.length; i++) {
                if (index < offsets[i] + frags[i].length()) return frags[i].charAt(index - offsets[i]);
            }
            throw new IndexOutOfBoundsException(index);
        }

        @Override public @NotNull String toString() { return String.join("", frags); }

        @Override public @NotNull CharSequence subSequence(int start, int end) {
            checkRange(start, end, len);
            ArrayList<String> parts = new ArrayList<>();
            for (int i = 0; i < frags.length; i++) {
                int fs = offsets[i], fe = fs + frags[i].length();
                if (fe > start && fs < end)
                    parts.add(frags[i].substring(Math.max(0, start - fs), Math.min(frags[i].length(), end - fs)));
            }
            return new Fragmented(parts.toArray(String[]::new));
        }

        @Override public boolean fragmentedEquals(Concat other) {
            return other instanceof Fragmented f ? Arrays.equals(frags, f.frags) : false;
        }
    }

    private static class CharArray extends Concat {
        private final char[] chars;

        CharArray(char[] c) { chars = c.clone(); }

        @Override public int length() { return chars.length; }
        @Override public char charAt(int i) { checkBounds(i, chars.length); return chars[i]; }
        @Override public @NotNull String toString() { return new String(chars); }

        @Override public @NotNull CharSequence subSequence(int s, int e) {
            checkRange(s, e, chars.length);
            return new CharArray(Arrays.copyOfRange(chars, s, e));
        }

        @Override public boolean fragmentedEquals(Concat other) {
            return other instanceof CharArray ca ? Arrays.equals(chars, ca.chars) : contentEquals(other);
        }
    }

    private static class Joined extends Concat {
        private final CharSequence[] parts;
        private final int[] offsets;
        private final int len;

        Joined(CharSequence[] parts) {
            this.parts = parts.clone();
            offsets = new int[parts.length];

            int off = 0;
            for (int i = 0; i < parts.length; i++) {
                offsets[i] = off;
                off += parts[i].length();
            }

            len = off;
        }

        @Override public int length() { return len; }

        @Override public char charAt(int index) {
            checkBounds(index, len);
            for (int i = 0; i < parts.length; i++) {
                int start = offsets[i];
                int end = start + parts[i].length();
                if (index < end) {
                    return parts[i].charAt(index - start);
                }
            }
            throw new IndexOutOfBoundsException(index);
        }

        @Override public @NotNull String toString() {
            StringBuilder sb = new StringBuilder(len);
            for (CharSequence part : parts) {
                sb.append(part);
            }
            return sb.toString();
        }

        @Override public @NotNull CharSequence subSequence(int start, int end) {
            checkRange(start, end, len);
            ArrayList<CharSequence> result = new ArrayList<>();

            for (int i = 0; i < parts.length; i++) {
                int partStart = offsets[i];
                int partEnd = partStart + parts[i].length();
                if (partEnd <= start || partStart >= end) {
                    continue;
                }

                int localStart = Math.max(0, start - partStart);
                int localEnd = Math.min(parts[i].length(), end - partStart);
                result.add(parts[i].subSequence(localStart, localEnd));
            }

            return new Joined(result.toArray(CharSequence[]::new));
        }

        @Override public boolean fragmentedEquals(Concat other) {
            return contentEquals(other);
        }
    }

    private static class Encoded extends Concat {
        private final byte[] data;
        private final int seed;

        Encoded(byte[] enc, int s) { data = enc.clone(); seed = s; }

        private int keyAt(int index) {
            int k = seed;
            for (int i = 0; i < index; i++) k = k * 1103515245 + 12345;
            return k;
        }

        @Override public int length() { return data.length; }
        @Override public char charAt(int i) { checkBounds(i, data.length); return (char) (data[i] & 0xFF ^ (keyAt(i) & 0xFF)); }
        @Override public @NotNull String toString() {
            StringBuilder sb = new StringBuilder(data.length);
            for (int i = 0; i < data.length; i++) sb.append(charAt(i));
            return sb.toString();
        }

        @Override public @NotNull CharSequence subSequence(int s, int e) {
            checkRange(s, e, data.length);
            return new Encoded(Arrays.copyOfRange(data, s, e), keyAt(s));
        }

        @Override public boolean fragmentedEquals(Concat other) { return contentEquals(other); }
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8096,0x4939}, new char[]{0x491c,0x80b8,0x36bb,0x3a3a}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb40b}, new char[]{0xb46d,0xf9f7,0x55ee,0xdf66}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4de7,0x581e,0x9d0a,0xcd01,0x6b7a,0x3c30,0x558a}, new char[]{0x6b29,0xcd49,0x9d4b,0x3c1d,0x55b8,0x4dd2,0x5828}).toString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7d29,0x9083,0x7f6d,0xc1f7}, new char[]{0x7f48,0x90b3,0xc1c5,0x7d51}).plainString();
    }
}
