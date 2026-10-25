package dev.koifih.client.setting;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
public final class SliderSetting extends Setting<Integer> {
    private final int min;
    private final int max;
    private final Measure measure;

    public SliderSetting(String id, int defaultValue, int min, int max) {
        this(id, defaultValue, min, max, Measure.NONE);
    }

    public SliderSetting(String id, int defaultValue, int min, int max, Measure measure) {
        super(id, defaultValue);
        this.min = min;
        this.max = max;
        this.measure = measure;
    }

    public String format(int value) {
        return measure.format(value);
    }

    @Override
    public void set(Integer value) {
        super.set(Math.clamp(value, min, max));
    }

    @Override
    public JsonElement save() {
        return new JsonPrimitive(value);
    }

    @Override
    public void load(JsonElement json) {
        if (json != null && json.isJsonPrimitive()) set(json.getAsInt());
    }

    public final int min() { return min; }

    public final int max() { return max; }
}
