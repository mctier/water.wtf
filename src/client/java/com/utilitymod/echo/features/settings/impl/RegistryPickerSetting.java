package com.utilitymod.echo.features.settings.impl;

import com.utilitymod.echo.features.settings.Setting;
import lombok.Getter;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

@Getter
public class RegistryPickerSetting<T> extends Setting {

    public enum GroupState {
        NONE,
        PARTIAL,
        ALL
    }

    private final Supplier<Registry<T>> registrySupplier;
    private final Set<Identifier> selectedIds = new HashSet<>();

    public RegistryPickerSetting(CharSequence name, Registry<T> registry) {
        this(name, () -> registry);
    }

    public RegistryPickerSetting(CharSequence name, Supplier<Registry<T>> registrySupplier) {
        super(name);
        this.registrySupplier = registrySupplier;
    }

    public RegistryPickerSetting(CharSequence name, Registry<T> registry, Predicate<Object> dependency) {
        this(name, () -> registry, dependency);
    }

    public RegistryPickerSetting(CharSequence name, Supplier<Registry<T>> registrySupplier, Predicate<Object> dependency) {
        super(name);
        this.registrySupplier = registrySupplier;
        setDependency(dependency);
    }

    public Registry<T> getRegistry() {
        return registrySupplier.get();
    }

    public boolean isSelected(T entry) {
        if (entry == null) return false;
        RegistryKey<T> key = getRegistry().getKey(entry).orElse(null);
        if (key == null) return false;
        return selectedIds.contains(key.getValue());
    }

    public boolean isSelected(Identifier id) {
        return id != null && selectedIds.contains(id);
    }

    public void setSelected(T entry, boolean selected) {
        if (entry == null) return;
        RegistryKey<T> key = getRegistry().getKey(entry).orElse(null);
        if (key == null) return;
        setSelected(key.getValue(), selected);
    }

    public void setSelected(Identifier id, boolean selected) {
        if (id == null) return;
        if (selected) {
            if (selectedIds.add(id)) notifyChange();
        } else {
            if (selectedIds.remove(id)) notifyChange();
        }
    }

    public Set<Identifier> getSelectedIds() {
        return Collections.unmodifiableSet(selectedIds);
    }

    public void setSelectedIds(Collection<Identifier> ids) {
        selectedIds.clear();
        if (ids != null) selectedIds.addAll(ids);
        notifyChange();
    }

    public void applyGroup(EntryGroup<T> group, boolean enabled) {
        if (group == null) return;

        Registry<T> registry = getRegistry();
        boolean changed = false;
        for (T entry : registry) {
            if (group.matches(entry)) {
                RegistryKey<T> key = registry.getKey(entry).orElse(null);
                if (key != null) {
                    Identifier id = key.getValue();
                    if (enabled) {
                        if (selectedIds.add(id)) changed = true;
                    } else {
                        if (selectedIds.remove(id)) changed = true;
                    }
                }
            }
        }
        if (changed) notifyChange();
    }

    public GroupState getGroupState(EntryGroup<T> group) {
        if (group == null) return GroupState.NONE;

        Registry<T> registry = getRegistry();
        List<T> groupEntries = new ArrayList<>();
        for (T entry : registry) {
            if (group.matches(entry)) groupEntries.add(entry);
        }

        if (groupEntries.isEmpty()) return GroupState.NONE;

        int selectedCount = 0;
        for (T entry : groupEntries) {
            RegistryKey<T> key = registry.getKey(entry).orElse(null);
            if (key != null && selectedIds.contains(key.getValue())) selectedCount++;
        }

        if (selectedCount == 0) return GroupState.NONE;
        if (selectedCount == groupEntries.size()) return GroupState.ALL;
        return GroupState.PARTIAL;
    }

    public int getSelectedCount() {
        return selectedIds.size();
    }

    public List<EntryGroup<T>> getGroups() {
        return List.of();
    }

    public Predicate<T> getEntryFilter() {
        return e -> true;
    }

    public Function<T, String> getNameProvider() {
        return Object::toString;
    }

    @Override
    public String getTypeId() { return LT.L1; }

    public record EntryGroup<T>(CharSequence name, Predicate<T> matcher) {
        public boolean matches(T entry) {
            return matcher.test(entry);
        }
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2208,0x8f43,0x424a}, new char[]{0x227a,0x8f26,0x422d,0x9040}).plainString();
    }
}
