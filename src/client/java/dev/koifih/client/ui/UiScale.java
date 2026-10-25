package dev.koifih.client.ui;
public enum UiScale {
    PERCENT_100(1f),
    PERCENT_125(1.25f),
    PERCENT_150(1.5f),
    PERCENT_200(2f);

    public static final UiScale[] ALL = values();
    private static UiScale current = PERCENT_100;
    private final float factor;

    public String displayName() {
        return Math.round(factor * 100) + LT.L1;
    }

    public static UiScale current() {
        return current;
    }

    public static void set(UiScale scale) {
        current = scale;
    }

    UiScale(float factor) {
        this.factor = factor;
    }

    public final float factor() { return factor; }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x22a4}, new char[]{0x2281,0x9ccb,0xc94e,0x2f32}).plainString();
    }
}
