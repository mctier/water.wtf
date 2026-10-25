package com.utilitymod.echo.features;

import com.utilitymod.core.Module;
import com.utilitymod.echo.event.EventManager;
import com.utilitymod.echo.features.settings.Setting;
import com.utilitymod.echo.utils.CloakUtil;
import com.utilitymod.echo.utils.Imports;
import net.minecraft.client.MinecraftClient;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * echo features.Feature on top of this mod's {@link Module}: enabling
 * registers the module's {@code @EventSubscribe} methods on the echo event
 * bus, disabling unregisters them (same as echo). Settings are every
 * {@link Setting} field, collected after construction like echo's
 * initSettings().
 */
public class Feature extends Module implements Imports {
    protected static final MinecraftClient mc = MinecraftClient.getInstance();
    public ArrayList<Setting> settings = new ArrayList<>();
    private final FeatureInfo info;
    private int key;
    private boolean visible;
    private boolean settingsCollected;

    public Feature(FeatureInfo info) {
        super(CloakUtil.heapSafe(info.name()), CloakUtil.heapSafe(info.description()), info.category().tab);
        this.info = info;
        this.key = info.key();
        this.visible = info.visible();
    }

    public void initSettings() {
        if (settingsCollected) return;
        settingsCollected = true;
        List<Class<?>> chain = new ArrayList<>();
        for (Class<?> c = getClass(); c != null && c != Feature.class; c = c.getSuperclass()) chain.add(0, c);
        for (Class<?> c : chain) {
            for (Field f : c.getDeclaredFields()) {
                if (!Setting.class.isAssignableFrom(f.getType())) continue;
                try {
                    f.setAccessible(true);
                    Object v = f.get(Modifier.isStatic(f.getModifiers()) ? null : this);
                    if (v != null && !settings.contains(v)) settings.add((Setting) v);
                    if (v instanceof com.utilitymod.echo.features.settings.impl.InputSimulationSetting sim) sim.setOwner(this);
                } catch (ReflectiveOperationException ignored) {
                    // skip inaccessible field
                }
            }
        }
    }

    public List<Setting> getSettings() {
        initSettings();
        return settings;
    }

    public Category getEchoCategory() { return info.category(); }

    public CharSequence getDescriptionSequence() { return info.description(); }

    @Override
    public void onEnable() {
        EventManager.register(this);
    }

    @Override
    public void onDisable() {
        EventManager.unregister(this);
    }

    public void toggle() { setEnabled(!isEnabled()); }

    public Boolean hasSettings() { return !getSettings().isEmpty(); }

    public void setKey(int key) { this.key = key; }

    public int getKey() { return key; }

    public boolean isVisible() { return visible; }

    public void setVisible(boolean visible) { this.visible = visible; }

    public String getInfo() { return ""; }

    /** Suffix shown next to the name. */
    public CharSequence concat() { return ""; }

    public boolean isNull() { return mc.player == null || mc.world == null; }
}
