package dev.koifih.client.setting;

import dev.koifih.client.ui.Units;
import java.util.Locale;

public enum Measure {
    NONE,
    DISTANCE,
    PERCENT,
    DEGREES,
    FRACTION,
    MILLIS,
    TICKS;

    public String format(int value) {
        return switch (this) {
            case DISTANCE -> Units.current().distance(value);
            case PERCENT -> value + LT.L1;
            case DEGREES -> value + LT.L2;
            case FRACTION -> String.format(Locale.ROOT, LT.L3, value / 100.0);
            case MILLIS -> value + LT.L4;
            case TICKS -> value + LT.L5;
            case NONE -> Integer.toString(value);
        };
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x49f1}, new char[]{0x49d4,0xd9f3,0xe83d,0xbfdb}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x16ad}, new char[]{0x161d,0xef76,0x5432,0x5e81}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa97c,0x4c84,0xbed5,0x9f1f}, new char[]{0x4ca1,0x9f31,0xa94e,0xbeb3}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7390,0x1c5c}, new char[]{0x1c31,0x73e3,0xfbdf,0x4af5}).plainString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2137}, new char[]{0x2143,0x78fc,0xaa95,0x7250}).plainString();
    }
}
