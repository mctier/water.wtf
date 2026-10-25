package dev.koifih.client.setting;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import java.util.function.IntSupplier;
public final class RangeSetting extends Setting<int[]> {
    private final int min;
    private IntSupplier max;
    private final Measure measure;

    public RangeSetting(String id, int defaultLow, int defaultHigh, int min, int max) {
        this(id, defaultLow, defaultHigh, min, max, Measure.NONE);
    }

    public RangeSetting(String id, int defaultLow, int defaultHigh, int min, int max, Measure measure) {
        super(id, new int[] {defaultLow, defaultHigh});
        this.min = min;
        this.max = () -> max;
        this.measure = measure;
    }

    public int max() {
        return max.getAsInt();
    }

    public void maxWhen(IntSupplier supplier) {
        max = supplier;
    }

    public String format(int value) {
        return measure.format(value);
    }

    public int low() {
        return Math.clamp(value[0], min, max());
    }

    public int high() {
        return Math.clamp(value[1], min, max());
    }

    public void setLow(int low) {
        value = new int[] {Math.clamp(low, min, high()), value[1]};
    }

    public void setHigh(int high) {
        value = new int[] {value[0], Math.clamp(high, low(), max())};
    }

    @Override
    public JsonElement save() {
        JsonArray array = new JsonArray();
        array.add(value[0]);
        array.add(value[1]);
        return array;
    }

    @Override
    public void load(JsonElement json) {
        if (json == null || !json.isJsonArray() || json.getAsJsonArray().size() != 2) return;
        JsonArray array = json.getAsJsonArray();
        value = new int[] {Math.clamp(array.get(0).getAsInt(), min, max()), Math.clamp(array.get(1).getAsInt(), min, max())};
        if (value[0] > value[1]) value = new int[] {value[1], value[0]};
    }

    public final int min() { return min; }
}
