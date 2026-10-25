package com.utilitymod.echo.setting;

import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.LinkedHashSet;
import java.util.Set;

/** echo's item whitelist: empty selection means "any item" (echo semantics). */
public class ItemPickerSetting extends Setting {
    private final Set<Item> selected = new LinkedHashSet<>();

    public ItemPickerSetting(String name) { super(name, null); }

    public int getSelectedCount() { return selected.size(); }

    public boolean isSelected(Item item) { return selected.contains(item); }

    public Set<Item> getSelected() { return Set.copyOf(selected); }

    public void toggle(Item item) {
        if (!selected.remove(item)) selected.add(item);
        notifyChange();
    }

    @Override
    public String save() {
        StringBuilder sb = new StringBuilder();
        for (Item i : selected) sb.append(sb.isEmpty() ? "" : LT.L1).append(Registries.ITEM.getId(i));
        return sb.toString();
    }

    @Override
    public void load(String v) {
        selected.clear();
        for (String id : v.split(LT.L1)) {
            Identifier parsed = id.isBlank() ? null : Identifier.tryParse(id);
            if (parsed != null && Registries.ITEM.containsId(parsed)) selected.add(Registries.ITEM.get(parsed));
        }
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xde7a}, new char[]{0xde56,0xd2fc,0x6d4f,0xccb6}).plainString();
    }
}
