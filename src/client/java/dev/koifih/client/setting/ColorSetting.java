package dev.koifih.client.setting;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import dev.koifih.client.util.Colors;
import java.util.function.BooleanSupplier;
public final class ColorSetting extends Setting<Integer> {
    private final int defaultSecondary;
    private int secondary;
    private BooleanSupplier gradient = () -> false;

    public ColorSetting(String id, int defaultRgb) {
        this(id, defaultRgb, defaultRgb);
    }

    public ColorSetting(String id, int defaultRgb, int defaultSecondaryRgb) {
        super(id, Colors.rgb(defaultRgb));
        this.defaultSecondary = Colors.rgb(defaultSecondaryRgb);
        this.secondary = defaultSecondary;
    }

    public void gradientWhen(BooleanSupplier condition) {
        gradient = condition;
    }

    public boolean isGradient() {
        return gradient.getAsBoolean();
    }

    public void setSecondary(int rgb) {
        secondary = Colors.rgb(rgb);
    }

    @Override
    public void reset() {
        super.reset();
        secondary = defaultSecondary;
    }

    @Override
    public JsonElement save() {
        if (secondary == value) return new JsonPrimitive(Colors.hex(value));
        JsonArray array = new JsonArray();
        array.add(Colors.hex(value));
        array.add(Colors.hex(secondary));
        return array;
    }

    @Override
    public void load(JsonElement json) {
        if (json == null) return;
        if (json.isJsonArray() && json.getAsJsonArray().size() == 2) {
            JsonArray array = json.getAsJsonArray();
            value = parse(array.get(0), value);
            secondary = parse(array.get(1), secondary);
        } else if (json.isJsonPrimitive()) {
            value = parse(json, value);
            secondary = value;
        }
    }

    private static int parse(JsonElement json, int fallback) {
        try {
            return Colors.rgb(Integer.parseInt(json.getAsString().replace(LT.L1, ""), 16));
        } catch (NumberFormatException | IllegalStateException exception) {
            return fallback;
        }
    }

    public int secondary() { return secondary; }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6636}, new char[]{0x6615,0x043d,0x5dfe,0x906b}).plainString();
    }
}
