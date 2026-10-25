package com.utilitymod.echo.utils.strings;

/**
 * echo i18n text. Echo translates feature/setting names at runtime; this mod
 * keeps English names, so {@link #of} returns the input unchanged. The type
 * exists because echo's Concat checks {@code instanceof I18nText}.
 */
public final class I18nText implements CharSequence {
    private final String text;

    private I18nText(String text) {
        this.text = text;
    }

    public static CharSequence of(CharSequence text) {
        return text == null ? "" : text;
    }

    public CharSequence stableHashSource() {
        return text;
    }

    @Override public int length() { return text.length(); }
    @Override public char charAt(int index) { return text.charAt(index); }
    @Override public CharSequence subSequence(int start, int end) { return text.subSequence(start, end); }
    @Override public String toString() { return text; }
}
