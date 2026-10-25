package com.utilitymod.echo.handlers;

import com.sun.jna.Platform;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.DWORD;
import com.sun.jna.platform.win32.WinDef.LONG;
import com.sun.jna.platform.win32.WinDef.WORD;
import com.sun.jna.platform.win32.WinUser.INPUT;
import com.sun.jna.platform.win32.WinUser.KEYBDINPUT;
import com.sun.jna.platform.win32.WinUser.MOUSEINPUT;
import com.utilitymod.echo.api.MinecraftCompat;
import com.utilitymod.echo.api.MixinAccessorCompat;
import com.utilitymod.echo.features.settings.impl.InputSimulationSetting;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.MouseInput;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.Keyboard;
import net.minecraft.client.Mouse;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

import static com.utilitymod.echo.utils.Imports.mc;

public final class InputHandler {
    public static final HashMap<Integer, Boolean> mouseButtons = new HashMap<>();
    private static final HashMap<Integer, Long> pendingMouseReleases = new HashMap<>();
    private static final HashMap<Integer, KeyBinding> pendingMouseKeyMappings = new HashMap<>();
    private static final HashMap<Integer, Long> pendingKeyReleases = new HashMap<>();

    // Kept for source compat — Fabric-mod build still exposes accessors on these handlers. Under
    // the injector the mixins never applied, so returning the raw handler as an accessor cast
    // ClassCastExceptions and murders every caller. New code should call MixinAccessorCompat
    // directly with the raw handler; these getters try the accessor cast first and return null on
    // failure so guarded callers do the right thing.
    public static Mouse getRawMouseHandler() {
        return MixinAccessorCompat.getMouseHandler(mc);
    }

    public static Keyboard getRawKeyboardHandler() {
        return MixinAccessorCompat.getKeyboardHandler(mc);
    }

    public static void simulateMouseClick(int keyCode) {
        simulateMouseClick(keyCode, 20);
    }

    public static void simulateMouseClick(int keyCode, int millis) {
        simulateMouseClick(keyCode, millis, null);
    }

    private static void simulateMouseClick(int keyCode, int millis, KeyBinding keyMapping) {
        if (!isWindowFocused()) return;

        // A click must remain pressed through an input frame. Dispatching both
        // edges on the same call stack makes polling CPS/keystroke HUDs miss it.
        if (mouseButtons.getOrDefault(keyCode, false)) {
            releaseMouseButton(keyCode);
        }

        // Mirror the keybinding state as well as the mouse callback. This is
        // how clients such as Vape expose synthetic presses to their HUDs.
        if (keyMapping != null) {
            keyMapping.setPressed(true);
            pendingMouseKeyMappings.put(keyCode, keyMapping);
        }
        simulateMousePress(keyCode);
        long holdNanos = Math.max(1L, millis) * 1_000_000L;
        pendingMouseReleases.put(keyCode, System.nanoTime() + holdNanos);
    }

    public static void releaseExpiredMouseButtons() {
        long now = System.nanoTime();
        Iterator<Map.Entry<Integer, Long>> iterator = pendingMouseReleases.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, Long> entry = iterator.next();
            if (now < entry.getValue()) continue;

            int button = entry.getKey();
            iterator.remove();
            releaseMouseButton(button);
        }

        Iterator<Map.Entry<Integer, Long>> keyIterator = pendingKeyReleases.entrySet().iterator();
        while (keyIterator.hasNext()) {
            Map.Entry<Integer, Long> entry = keyIterator.next();
            if (now < entry.getValue()) continue;

            keyIterator.remove();
            simulateKeyRelease(entry.getKey());
        }
    }

    public static void processConfiguredInputs() {
        if (!isWindowFocused() || MinecraftCompat.getScreen() != null) return;

        long now = System.nanoTime();
        for (InputSimulationSetting setting : InputSimulationSetting.instances()) {
            if (!setting.getValue() || setting.getOwner() == null || !setting.getOwner().isEnabled() || !setting.isReady(now)) {
                continue;
            }

            Set<Integer> keys = new HashSet<>(5);
            for (var slot : setting.getInputSlots()) {
                int key = slot.getKey();
                if (key != -1 && key != GLFW.GLFW_KEY_UNKNOWN && (key & 0x80000000) == 0 && !slot.isListening()) {
                    keys.add(key);
                }
            }

            for (int key : keys) {
                simulateKeyClick(key, 25);
            }
        }
    }

    public static void simulateClick(KeyBinding keyMapping, InputSimulationSetting inputSimulation) {
        if (inputSimulation == null) return;
        if (directAttack(keyMapping)) {
            sendCompanionInputs(inputSimulation);
            return;
        }
        simulateClick(keyMapping, inputSimulation.getValue());
        sendCompanionInputs(inputSimulation);
    }

    /**
     * Attack-key "click" on an entity under the crosshair: run what vanilla's doAttack does for
     * an entity hit (attack packet + cooldown reset + swing). Under late injection the synthetic
     * click-count path does not reliably reach doAttack, so modules that click to attack
     * (AutoMace, Crystalline, AegisSplit, SpearLunge) never hit. Returns false when the
     * crosshair is not on an entity, so the regular click path still handles blocks/air.
     */
    private static boolean directAttack(KeyBinding keyMapping) {
        net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
        if (keyMapping != mc.options.attackKey || mc.player == null || mc.interactionManager == null) return false;
        if (!(mc.crosshairTarget instanceof net.minecraft.util.hit.EntityHitResult hit)) return false;
        net.minecraft.entity.Entity target = hit.getEntity();
        if (target == null || target == mc.player || !target.isAlive() || mc.player.isSpectator()) return false;
        // Same pipeline as a real click, minus the click-count step that doesn't register after injection:
        // 1) "attack starting" -> Mace Swap / AegisSplit swap to mace / axe (and chain stun slams)
        new com.utilitymod.echo.event.impl.EventStartAttack.Pre(mc.player, target).call();
        // 2) vanilla doAttack: hits with the (possibly swapped) item, fires the normal attack events,
        //    resets the cooldown and swings
        mc.crosshairTarget = hit.getEntity() == target ? mc.crosshairTarget : new net.minecraft.util.hit.EntityHitResult(target);
        try {
            com.utilitymod.echo.api.Accessors.invokeStartAttack();
        } catch (Throwable t) {
            mc.interactionManager.attackEntity(mc.player, target);
            mc.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
        }
        showClick(keyMapping, 0);
        return true;
    }

    /** Simulated clicks per mouse button (0 left, 1 right), for CPS / keystroke displays. */
    public static final java.util.concurrent.ConcurrentLinkedQueue<int[]> SIMULATED_CLICKS = new java.util.concurrent.ConcurrentLinkedQueue<>();
    private static final java.util.concurrent.ScheduledExecutorService RELEASER =
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, LT.L1);
                t.setDaemon(true);
                return t;
            });

    /**
     * Makes a direct (no-mouse) action visible to input displays: the binding reads as held for
     * ~45 ms (keystroke mods polling isPressed light up; vanilla only acts on new presses, so
     * this cannot attack twice) and our Keystrokes HUD counts it toward CPS.
     */
    public static void showClick(KeyBinding keyMapping, int button) {
        SIMULATED_CLICKS.add(new int[] {button});
        while (SIMULATED_CLICKS.size() > 64) SIMULATED_CLICKS.poll();
        if (keyMapping == null || keyMapping.isPressed()) return;
        keyMapping.setPressed(true);
        net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
        RELEASER.schedule(() -> mc.execute(() -> {
            InputUtil.Key key = MixinAccessorCompat.getKey(keyMapping);
            boolean physical = key != null && isWindowFocused() && (key.getCategory() == InputUtil.Type.MOUSE
                    ? org.lwjgl.glfw.GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), key.getCode()) == org.lwjgl.glfw.GLFW.GLFW_PRESS
                    : org.lwjgl.glfw.GLFW.glfwGetKey(mc.getWindow().getHandle(), key.getCode()) == org.lwjgl.glfw.GLFW.GLFW_PRESS);
            if (!physical) keyMapping.setPressed(false);
        }), 45, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    private static void sendCompanionInputs(InputSimulationSetting inputSimulation) {
        if (!inputSimulation.getValue() || !inputSimulation.isReady(System.nanoTime())) return;

        Set<Integer> keys = new HashSet<>(5);
        for (var slot : inputSimulation.getInputSlots()) {
            int key = slot.getKey();
            if (key != -1 && key != GLFW.GLFW_KEY_UNKNOWN && (key & 0x80000000) == 0 && !slot.isListening()) {
                keys.add(key);
            }
        }

        for (int key : keys) {
            simulateKeyClick(key, 25);
        }
    }

    private static void releaseMouseButton(int button) {
        KeyBinding keyMapping = pendingMouseKeyMappings.remove(button);
        if (keyMapping != null) {
            keyMapping.setPressed(false);
        }
        simulateMouseRelease(button);
    }

    public static void simulateClick(KeyBinding keyMapping, boolean useSimulation) {
        simulateClick(keyMapping, useSimulation, 20);
    }

    public static void simulateClick(KeyBinding keyMapping, boolean useSimulation, int millis) {
        if (keyMapping == null || !isWindowFocused()) return;

        InputUtil.Key key = MixinAccessorCompat.getKey(keyMapping);

        if (key == null) return;

        if (key.getCategory() == InputUtil.Type.MOUSE) {
            int button = key.getCode();

            if (useSimulation) {
                int clicksBefore = MixinAccessorCompat.getClickCount(keyMapping);
                // Feed both edges through Minecraft's Mouse. This is
                // the same path used by the real GLFW callback and is visible
                // to CPS/keystrokes HUD mixins.
                simulateMouseClick(button, millis, keyMapping);
                GLFW.glfwPollEvents();
                if (MixinAccessorCompat.getClickCount(keyMapping) == clicksBefore) {
                    incrementClickCountForKeyMapping(keyMapping);
                }
            } else {
                incrementClickCountForKeyMapping(keyMapping);
            }
        } else {
            if (useSimulation) {
                simulateKeyClick(keyMapping, millis);
                GLFW.glfwPollEvents();
            } else {
                keyMapping.setPressed(true);
                incrementClickCountForKeyMapping(keyMapping);
                keyMapping.setPressed(false);
            }
        }
    }

    public static void simulateKeyClick(KeyBinding keyMapping, int millis) {
        if (keyMapping == null || !isWindowFocused()) return;
        InputUtil.Key key = MixinAccessorCompat.getKey(keyMapping);
        if (key == null || key.getCategory() != InputUtil.Type.KEYSYM) return;

        simulateKeyClick(key.getCode(), millis);
    }

    public static void simulateKeyClick(int keyCode, int millis) {
        if (!isWindowFocused() || keyCode == GLFW.GLFW_KEY_UNKNOWN) return;

        if (pendingKeyReleases.remove(keyCode) != null) {
            simulateKeyRelease(keyCode);
        }

        simulateKeyPress(keyCode);
        long holdNanos = Math.max(1L, millis) * 1_000_000L;
        pendingKeyReleases.put(keyCode, System.nanoTime() + holdNanos);
    }

    public static void simulateKeyPress(KeyBinding keyMapping) {
        if (keyMapping == null) return;
        InputUtil.Key key = MixinAccessorCompat.getKey(keyMapping);
        if (key == null || key.getCategory() != InputUtil.Type.KEYSYM) {
            keyMapping.setPressed(true);
            return;
        }
        simulateKeyPress(key.getCode());
    }

    public static void simulateKeyRelease(KeyBinding keyMapping) {
        if (keyMapping == null) return;
        InputUtil.Key key = MixinAccessorCompat.getKey(keyMapping);
        if (key == null || key.getCategory() != InputUtil.Type.KEYSYM) {
            keyMapping.setPressed(false);
            return;
        }
        simulateKeyRelease(key.getCode());
    }

    public static void simulateKeyPress(int keyCode) {
        if (sendWindowsKeyInput(keyCode, false)) {
            GLFW.glfwPollEvents();
            return;
        }
        simulateKeyEvent(keyCode, GLFW.GLFW_PRESS);
    }

    public static void simulateKeyRelease(int keyCode) {
        if (sendWindowsKeyInput(keyCode, true)) {
            GLFW.glfwPollEvents();
            return;
        }
        simulateKeyEvent(keyCode, GLFW.GLFW_RELEASE);
    }

    private static void simulateKeyEvent(int keyCode, int action) {
        if (mc == null || mc.options == null) return;

        for (KeyBinding keyMapping : mc.options.allKeys) {
            InputUtil.Key key = MixinAccessorCompat.getKey(keyMapping);
            if (key != null && key.getCode() == keyCode) {
                if (action == GLFW.GLFW_PRESS) {
                    keyMapping.setPressed(true);
                } else {
                    keyMapping.setPressed(false);
                }
                break;
            }
        }

        if (mc.getWindow() == null) return;
        Keyboard keyboardHandler = MixinAccessorCompat.getKeyboardHandler(mc);
        if (keyboardHandler != null) {
            MixinAccessorCompat.invokeKeyPress(keyboardHandler, mc.getWindow().getHandle(), action,
                    new KeyInput(keyCode, 0, 1));
        }
    }

    private static boolean isWindowFocused() {
        if (mc == null || mc.getWindow() == null) return false;
        return GLFW.glfwGetWindowAttrib(mc.getWindow().getHandle(), GLFW.GLFW_FOCUSED) == GLFW.GLFW_TRUE;
    }

    public static void simulateMousePress(int keyCode) {
        dispatchMouseButton(keyCode, GLFW.GLFW_PRESS);
    }

    public static void simulateMouseRelease(int keyCode) {
        dispatchMouseButton(keyCode, GLFW.GLFW_RELEASE);
    }

    private static void dispatchMouseButton(int keyCode, int action) {
        if (mc == null) return;

        // Update our synthetic state before dispatching so listeners observe the
        // same state as they do for a real GLFW callback.
        if (action == GLFW.GLFW_PRESS) {
            mouseButtons.put(keyCode, true);
        } else if (action == GLFW.GLFW_RELEASE) {
            mouseButtons.put(keyCode, false);
        }

        if (sendWindowsMouseInput(keyCode, action)) {
            GLFW.glfwPollEvents();
            return;
        }

        Mouse mouseHandler = MixinAccessorCompat.getMouseHandler(mc);
        if (mouseHandler != null && mc.getWindow() != null) {
            MixinAccessorCompat.invokeOnButton(mouseHandler, mc.getWindow().getHandle(),
                    new MouseInput(keyCode, 0), action);
        }
    }

    private static boolean sendWindowsKeyInput(int keyCode, boolean release) {
        if (!Platform.isWindows()) return false;

        int scanCode = GLFW.glfwGetKeyScancode(keyCode);
        if (scanCode <= 0) return false;

        try {
            INPUT input = new INPUT();
            input.type = new DWORD(INPUT.INPUT_KEYBOARD);
            input.input.setType(KEYBDINPUT.class);
            input.input.ki = new KEYBDINPUT();
            input.input.ki.wVk = new WORD(0);
            input.input.ki.wScan = new WORD(scanCode);
            input.input.ki.dwFlags = new DWORD(0x0008 | (release ? 0x0002 : 0));
            input.input.ki.time = new DWORD(0);
            input.write();
            return User32.INSTANCE.SendInput(new DWORD(1), new INPUT[]{input}, input.size()).intValue() == 1;
        } catch (LinkageError | RuntimeException ignored) {
            return false;
        }
    }

    private static boolean sendWindowsMouseInput(int button, int action) {
        if (!Platform.isWindows()) return false;

        int flags = switch (button) {
            case GLFW.GLFW_MOUSE_BUTTON_LEFT -> action == GLFW.GLFW_PRESS ? 0x0002 : 0x0004;
            case GLFW.GLFW_MOUSE_BUTTON_RIGHT -> action == GLFW.GLFW_PRESS ? 0x0008 : 0x0010;
            case GLFW.GLFW_MOUSE_BUTTON_MIDDLE -> action == GLFW.GLFW_PRESS ? 0x0020 : 0x0040;
            default -> 0;
        };
        if (flags == 0) return false;

        try {
            INPUT input = new INPUT();
            input.type = new DWORD(INPUT.INPUT_MOUSE);
            input.input.setType(MOUSEINPUT.class);
            input.input.mi = new MOUSEINPUT();
            input.input.mi.dx = new LONG(0);
            input.input.mi.dy = new LONG(0);
            input.input.mi.mouseData = new DWORD(0);
            input.input.mi.dwFlags = new DWORD(flags);
            input.input.mi.time = new DWORD(0);
            input.write();
            return User32.INSTANCE.SendInput(new DWORD(1), new INPUT[]{input}, input.size()).intValue() == 1;
        } catch (LinkageError | RuntimeException ignored) {
            return false;
        }
    }

    public static boolean isMouseButtonPressed(int keyCode) {
        return mouseButtons.getOrDefault(keyCode, false);
    }

    public static boolean isMouseDown(int button) {
        if (mc == null) return false;
        return GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), button) == GLFW.GLFW_PRESS
                || mouseButtons.getOrDefault(button, false);
    }

    public static boolean isKeyDown(int key) {
        if (mc == null) return false;
        return GLFW.glfwGetKey(mc.getWindow().getHandle(), key) == GLFW.GLFW_PRESS;
    }

    public static boolean isBindDown(int bind) {
        if (bind == -1 || bind == 0) return false;
        if ((bind & 0x80000000) != 0) {
            int mb = bind & 0xFF;
            // For mouse binds we need to use GLFW directly
            if (mc == null || mc.getWindow() == null) return false;
            return GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), mb) == GLFW.GLFW_PRESS;
        }
        return isKeyDown(bind);
    }

    public static boolean shouldSimulateClick(boolean useSimulation, int activateKey, int simButton) {
        if (!useSimulation) return false;
        if ((activateKey & 0x80000000) == 0) return true;
        int actButton = activateKey & 0xFF;
        return actButton != simButton || !isBindDown(activateKey);
    }

    public static void incrementClickCountForKeyMapping(KeyBinding keyMapping) {
        if (keyMapping == null) return;
        InputUtil.Key key = MixinAccessorCompat.getKey(keyMapping);
        if (key != null) {
            KeyBinding.onKeyPressed(key);
        }
    }

    private static int keyToBind(InputUtil.Key key) {
        if (key == null) return -1;
        if (key.getCategory() == InputUtil.Type.MOUSE) {
            return 0x80000000 | key.getCode();
        }
        return key.getCode();
    }

    public static boolean isBindDown(KeyBinding keyMapping) {
        if (keyMapping == null) return false;
        InputUtil.Key key = MixinAccessorCompat.getKey(keyMapping);
        return isBindDown(keyToBind(key));
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe101,0xfa9f,0x7b1e,0x9e17,0xf134,0x34b1,0xa8b1,0xa147,0x6adb,0x3e62,0xd734,0x42c0,0x9048,0x94db,0x9d46,0x81f3,0x7b19,0x6922,0x3a7a,0xc655,0x1e4a,0xb575,0x57be,0x3893}, new char[]{0x903d,0x6aaf,0x9e7e,0x7b72,0xb51c,0x1e3e,0x3e1b,0x94b6,0x38fc,0xc631,0xa89c,0xf15d,0xa129,0x3a0a,0x57cb,0x7b6d,0x42ed,0xfaed,0x8196,0x9d2a,0xe164,0xd755,0x6951,0x34d4}).plainString();
    }
}
