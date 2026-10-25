package com.utilitymod.echo.setting;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** echo's multi-select; constructor options start enabled (as in echo). */
public class MultiModeSetting extends Setting {
    private final Map<String, Boolean> options = new LinkedHashMap<>();

    public MultiModeSetting(String name, String... enabledByDefault) {
        this(name, null, enabledByDefault);
    }

    /** All {@code options}, with only {@code enabledByDefault} switched on. */
    public static MultiModeSetting of(String name, List<String> options, String... enabledByDefault) {
        MultiModeSetting s = new MultiModeSetting(name, null, options.toArray(String[]::new));
        List<String> on = List.of(enabledByDefault);
        for (String o : options) s.options.put(o, on.contains(o));
        return s;
    }

    public MultiModeSetting(String name, Predicate<Object> dependency, String... enabledByDefault) {
        super(name, dependency);
        for (String o : enabledByDefault) options.put(o, true);
    }

    public boolean isEnabled(String option) { return options.getOrDefault(option, false); }

    public List<String> getOptions() { return List.copyOf(options.keySet()); }

    public void set(String option, boolean enabled) {
        if (!options.containsKey(option) || options.get(option) == enabled) return;
        options.put(option, enabled);
        notifyChange();
    }

    public void toggle(String option) { set(option, !isEnabled(option)); }

    @Override
    public String save() {
        StringBuilder sb = new StringBuilder();
        for (var e : options.entrySet()) if (e.getValue()) sb.append(sb.isEmpty() ? "" : LT.L1).append(e.getKey());
        return sb.toString();
    }

    @Override
    public void load(String v) {
        List<String> on = List.of(v.split(LT.L1));
        for (String k : options.keySet()) options.put(k, on.contains(k));
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe16f}, new char[]{0xe143,0x32d4,0xbeea,0x0224}).plainString();
    }
}
