package dev.koifih.client.input;

import net.minecraft.client.gui.Click;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * Late-injection keybind shim. Fabric's keybind registry rejects
 * registrations after {@code GameOptions} init, so the GUI open key lives
 * here: polling in {@code com.utilitymod.Keys} reads the raw code, the
 * SettingsMenu rebind control writes it. Keyboard-only (no mouse toggle).
 */
public final class Keybinds {
    private static int openKey = GLFW.GLFW_KEY_RIGHT_SHIFT;

    private Keybinds() {}

    /** Raw GLFW key code that toggles the wing (polled, not registered). */
    public static int openKeyCode() {
        return openKey;
    }

    public static void setOpenKeyCode(int code) {
        if (code > 0) openKey = code;
    }

    public static boolean matches(KeyInput event) {
        return event.key() == openKey;
    }

    public static boolean matchesMouse(Click event) {
        return false;
    }

    public static InputUtil.Key panelKey() {
        return InputUtil.Type.KEYSYM.createFromCode(openKey);
    }

    public static void setPanelKey(InputUtil.Key key) {
        if (key.getCategory() == InputUtil.Type.KEYSYM && key.getCode() != GLFW.GLFW_KEY_UNKNOWN) {
            openKey = key.getCode();
        }
    }
}
