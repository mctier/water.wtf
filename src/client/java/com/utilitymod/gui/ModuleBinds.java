package com.utilitymod.gui;

import com.google.gson.JsonObject;
import com.utilitymod.anubis.AnubisBridge;
import com.utilitymod.anubis.module.ClientModule;
import com.utilitymod.core.Module;
import com.utilitymod.core.ModuleManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Toggle keybinds for every module (regular and Donut tab), set by middle-clicking a row in the
 * dropdown GUI. Polled with GLFW at tick start (no KeyBinding registration after game init).
 * Codes: GLFW key, or 0x80000000 | mouse button.
 */
public final class ModuleBinds {
    private static final Map<String, Integer> BINDS = new HashMap<>();
    private static final Map<String, Boolean> WAS_DOWN = new HashMap<>();

    private ModuleBinds() {}

    private static String key(String moduleName) {
        return moduleName.toLowerCase(Locale.ROOT);
    }

    public static int get(String moduleName) {
        return BINDS.getOrDefault(key(moduleName), -1);
    }

    public static void set(String moduleName, int code) {
        if (code == -1 || code == GLFW.GLFW_KEY_UNKNOWN) BINDS.remove(key(moduleName));
        else BINDS.put(key(moduleName), code);
        WAS_DOWN.put(key(moduleName), true); // the press that set it must not toggle
        if (code != -1) Welcome.keyBound();
        GuiConfig.save();
    }

    public static String name(int code) {
        if (code == -1) return "";
        if ((code & 0x80000000) != 0) return LT.L1 + ((code & 0xFF) + 1);
        String n = InputUtil.Type.KEYSYM.createFromCode(code).getLocalizedText().getString();
        return n.length() > 6 ? n.substring(0, 6) : n.toUpperCase(Locale.ROOT);
    }

    /** Edge-detected toggles; only while playing (no screen open). */
    public static void tick() {
        if (com.utilitymod.inject.Hooks.PARALYZED) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (BINDS.isEmpty() || mc.player == null) return;
        long window = mc.getWindow().getHandle();
        boolean playing = mc.currentScreen == null;
        for (Map.Entry<String, Integer> e : BINDS.entrySet()) {
            int code = e.getValue();
            boolean down = (code & 0x80000000) != 0
                    ? GLFW.glfwGetMouseButton(window, code & 0xFF) == GLFW.GLFW_PRESS
                    : GLFW.glfwGetKey(window, code) == GLFW.GLFW_PRESS;
            boolean was = WAS_DOWN.getOrDefault(e.getKey(), false);
            WAS_DOWN.put(e.getKey(), down);
            if (down && !was && playing) toggle(e.getKey());
        }
    }

    /** Bind name of the panic key (turns every module off). */
    public static final String PANIC = LT2.L1;

    public static void panic() {
        for (Module m : ModuleManager.all()) if (m.isEnabled()) m.setEnabled(false);
        for (ClientModule am : AnubisBridge.all()) if (am.isEnabled()) am.toggle();
    }

    private static void toggle(String name) {
        if (name.equals(key(PANIC))) { panic(); return; }
        Module m = ModuleManager.get(name);
        if (m != null) {
            m.setEnabled(!m.isEnabled());
            return;
        }
        for (ClientModule am : AnubisBridge.all()) {
            if (com.utilitymod.anubis.util.TextMask.key(am.name()).equals(name)) {
                am.toggle();
                return;
            }
        }
    }

    public static JsonObject save() {
        JsonObject o = new JsonObject();
        BINDS.forEach(o::addProperty);
        return o;
    }

    public static void load(JsonObject o) {
        BINDS.clear();
        if (o == null) return;
        for (var e : o.entrySet()) {
            try {
                BINDS.put(e.getKey(), e.getValue().getAsInt());
                WAS_DOWN.put(e.getKey(), true);
            } catch (Exception ignored) {
            }
        }
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6024,0x1aae}, new char[]{0x1ae3,0x6066,0x298a,0xd709}).plainString();
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT2 {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x26e2,0x829d,0xff0c,0xc1e7,0x077b,0xe13c,0x843c,0x81a7,0x879b}, new char[]{0x26bd,0xff53,0xc197,0x845d,0x81c9,0xe155,0x82fe,0x0724,0x87c4}).plainString();
    }
}
