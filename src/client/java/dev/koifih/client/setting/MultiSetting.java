package dev.koifih.client.setting;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.IntPredicate;
public final class MultiSetting extends Setting<Set<Integer>> {
    private final String[] options;
    private IntPredicate optionVisible = option -> true;

    public MultiSetting(String id, String[] options, int... defaults) {
        super(id, new LinkedHashSet<>());
        this.options = options;
        for (int index : defaults) value.add(index);
    }

    public void optionVisibleWhen(IntPredicate condition) {
        optionVisible = condition;
    }

    public boolean isOptionVisible(int option) {
        return optionVisible.test(option);
    }

    public boolean has(int option) {
        return value.contains(option) && isOptionVisible(option);
    }

    @Override
    public JsonElement save() {
        JsonArray array = new JsonArray();
        for (int index : value) array.add(options[index]);
        return array;
    }

    @Override
    public void load(JsonElement json) {
        if (json == null || !json.isJsonArray()) return;
        value.clear();
        for (JsonElement element : json.getAsJsonArray()) {
            for (int i = 0; i < options.length; i++) if (options[i].equals(element.getAsString())) value.add(i);
        }
    }

    public final String[] options() { return options; }
}
