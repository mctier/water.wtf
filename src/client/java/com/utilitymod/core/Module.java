package com.utilitymod.core;

import com.utilitymod.gui.Category;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

public abstract class Module {
    protected final String name;
    protected boolean enabled = false;
    protected final String description;
    protected final Category category;

    protected Module(String name, String description) {
        this(name, description, Category.MISC);
    }

    protected Module(String name, String description, Category category) {
        // heap-cloak every module name/description: see com.utilitymod.echo.utils.CloakUtil
        this.name = com.utilitymod.echo.utils.CloakUtil.heapSafe(name);
        this.description = com.utilitymod.echo.utils.CloakUtil.heapSafe(description);
        this.category = category;
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) return;
        this.enabled = enabled;
        if (enabled) onEnable(); else onDisable();
        try {
            com.utilitymod.echo.features.impl.render.Notifications.onToggle(name, enabled);
            if (!com.utilitymod.echo.features.impl.render.Notifications.muted) {
                com.utilitymod.gui.Welcome.moduleToggled();
                com.utilitymod.gui.ToggleSounds.play(category, enabled);
            }
        } catch (Throwable ignored) {
        }
    }

    public boolean isEnabled() { return enabled; }
    public final String getName() { return name; }
    public final String getDescription() { return description; }
    public final Category getCategory() { return category; }

    protected void onEnable() {}
    protected void onDisable() {}
    // Called ONLY at START_CLIENT_TICK via TickDispatcher
    public void onTickStart(MinecraftClient client) {}

    /** Client HUD hook for display-only modules. */
    public void onHudRender(DrawContext context, float tickDelta) {}

    // For JNI/JVMTI bridge - consistent logic path
    public void onNativeSignal(int code, long payload) {}
}
