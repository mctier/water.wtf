package com.utilitymod.echo.features.impl.render;

import com.utilitymod.core.Module;
import com.utilitymod.core.ModuleManager;
import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventTick;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.settings.impl.BoolSetting;
import com.utilitymod.echo.features.settings.impl.ColorSetting;
import com.utilitymod.echo.features.settings.impl.FloatSetting;
import com.utilitymod.echo.features.settings.impl.IntSetting;
import com.utilitymod.echo.features.settings.impl.KeybindSetting;
import com.utilitymod.echo.features.settings.impl.ModeSetting;
import com.utilitymod.echo.handlers.InputHandler;
import com.utilitymod.echo.hud.HudEditorScreen;
import com.utilitymod.echo.hud.HudElement;
import com.utilitymod.echo.hud.HudManager;
import com.utilitymod.echo.render.GlassRenderer;
import dev.koifih.client.render.Draw;
import dev.koifih.client.render.Text;
import dev.koifih.client.ui.Theme;
import dev.koifih.client.util.Colors;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Client HUD: watermark, module list, coordinates and FPS/ping as movable
 * elements (see the HUD editor), with optional liquid-glass backgrounds.
 */
public class HudOverlay extends Feature {
    private final BoolSetting openEditor = new BoolSetting(LT.L1, false);
    private final KeybindSetting editorKey = new KeybindSetting(LT.L2, GLFW.GLFW_KEY_RIGHT_CONTROL);
    private final FloatSetting opacity = new FloatSetting(LT.L3, 0.9f, 0f, 1f, 0.05f);
    private final ModeSetting listColor = new ModeSetting(LT.L4, LT.L5, LT.L5, LT.L6, LT.L7, LT.L8);
    private final BoolSetting suffixes = new BoolSetting(LT.L9, true);

    // Liquid glass (Clear/Tinted: Jacquesqwq/LiquidGlassShader, MIT; Lens: own edge-lens)
    private final ModeSetting glassMode = new ModeSetting(LT.L10, LT.L11, o -> HudManager.liquidGlass, LT.L11, LT.L12, LT.L13, LT.L14, LT.L15);
    private final FloatSetting shapePower = new FloatSetting(LT.L16, 4f, 2f, 12f, 0.5f, o -> HudManager.liquidGlass && (glassMode.is(LT.L11) || glassMode.is(LT.L12)));
    private final FloatSetting motion = new FloatSetting(LT.L17, 1f, 0f, 3f, 0.05f, o -> HudManager.liquidGlass && (glassMode.is(LT.L11) || glassMode.is(LT.L12)));
    private final FloatSetting flowSpeed = new FloatSetting(LT.L18, 1f, 0f, 4f, 0.05f, o -> HudManager.liquidGlass && (glassMode.is(LT.L11) || glassMode.is(LT.L12)));
    private final FloatSetting liquidPower = new FloatSetting(LT.L19, 3f, 0f, 9f, 0.25f, o -> HudManager.liquidGlass && (glassMode.is(LT.L11) || glassMode.is(LT.L12)));
    private final IntSetting blurIterations = new IntSetting(LT.L20, 1, 0, 5, o -> HudManager.liquidGlass && !glassMode.is(LT.L15));
    private final FloatSetting blurRadius = new FloatSetting(LT.L21, 2f, 0f, 12f, 0.5f, o -> HudManager.liquidGlass && !glassMode.is(LT.L15));
    private final FloatSetting blurDownscale = new FloatSetting(LT.L22, 0.5f, 0.1f, 1f, 0.05f, o -> HudManager.liquidGlass);
    private final FloatSetting glassNoise = new FloatSetting(LT.L23, 0.03f, 0f, 0.3f, 0.01f, o -> HudManager.liquidGlass && !glassMode.is(LT.L15));
    private final FloatSetting refraction = new FloatSetting(LT.L24, 0.75f, -1f, 10f, 0.05f, o -> HudManager.liquidGlass && !glassMode.is(LT.L15));
    private final FloatSetting glowWeight = new FloatSetting(LT.L25, 0.3f, -1f, 1f, 0.05f, o -> HudManager.liquidGlass && (glassMode.is(LT.L14) || (glassMode.is(LT.L11) || glassMode.is(LT.L12))));
    private final FloatSetting glowBias = new FloatSetting(LT.L26, 0f, -1f, 1f, 0.05f, o -> HudManager.liquidGlass && (glassMode.is(LT.L14) || (glassMode.is(LT.L11) || glassMode.is(LT.L12))));
    private final FloatSetting glowEdge0 = new FloatSetting(LT.L27, 0.06f, -1f, 1f, 0.01f, o -> HudManager.liquidGlass && (glassMode.is(LT.L14) || (glassMode.is(LT.L11) || glassMode.is(LT.L12))));
    private final FloatSetting glowEdge1 = new FloatSetting(LT.L28, 0f, -1f, 1f, 0.01f, o -> HudManager.liquidGlass && (glassMode.is(LT.L14) || (glassMode.is(LT.L11) || glassMode.is(LT.L12))));
    private final BoolSetting tintFromTheme = new BoolSetting(LT.L29, true, o -> HudManager.liquidGlass && !glassMode.is(LT.L14));
    private final ColorSetting glassTint = new ColorSetting(LT.L30, 0xFF9FD3FF,
            o -> HudManager.liquidGlass && !glassMode.is(LT.L14) && !tintFromTheme.getValue());
    private final FloatSetting glassTintStrength = new FloatSetting(LT.L31, 0.25f, 0f, 1f, 0.01f, o -> HudManager.liquidGlass && !glassMode.is(LT.L14));
    private final FloatSetting glassChroma = new FloatSetting(LT.L32, 0.001f, 0f, 0.01f, 0.0005f, o -> HudManager.liquidGlass && glassMode.is(LT.L13));
    private final FloatSetting glassDarkness = new FloatSetting(LT.L33, 0f, 0f, 1f, 0.05f, o -> HudManager.liquidGlass && glassMode.is(LT.L13));
    private final FloatSetting lensRefract = new FloatSetting(LT.L34, 10f, 0f, 30f, 0.5f, o -> HudManager.liquidGlass && glassMode.is(LT.L15));
    private final FloatSetting lensChroma = new FloatSetting(LT.L35, 1.5f, 0f, 6f, 0.1f, o -> HudManager.liquidGlass && glassMode.is(LT.L15));
    private final FloatSetting lensRipple = new FloatSetting(LT.L36, 0.75f, 0f, 2f, 0.05f, o -> HudManager.liquidGlass && glassMode.is(LT.L15));
    private final FloatSetting lensHighlight = new FloatSetting(LT.L37, 1f, 0f, 2f, 0.05f, o -> HudManager.liquidGlass && (glassMode.is(LT.L15) || (glassMode.is(LT.L11) || glassMode.is(LT.L12))));

    private final Map<Module, Float> slide = new HashMap<>();
    private boolean editorKeyWasDown;

    /** For the dropdown GUI's Theme tab (glass blur sliders). */
    public static HudOverlay instance;

    public FloatSetting glassBlurRadius() { return blurRadius; }

    public ModeSetting glassModeSetting() { return glassMode; }

    public FloatSetting tintStrengthSetting() { return glassTintStrength; }

    public FloatSetting motionSetting() { return motion; }

    /** One-click look: glass mode + tint strength + blur + liquid motion (tint follows the accent). */
    public void applyPreset(String mode, float tint, float blur, int passes, float liquidMotion) {
        HudManager.liquidGlass = true;
        glassMode.setValue(mode);
        tintFromTheme.setValue(true);
        glassTintStrength.setValue(tint);
        blurRadius.setValue(blur);
        blurIterations.setValue(passes);
        motion.setValue(liquidMotion);
    }

    public IntSetting glassBlurIterations() { return blurIterations; }

    public HudOverlay() {
        super(new FeatureInfo(new com.utilitymod.echo.utils.EncryptedString(new char[]{0x225c,0xa89e,0xd132}, new char[]{0xd17a,0xa8cb,0x2218}).toString(), new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6559,0xd930,0x66fa,0x7ece,0x181e,0xac50,0xeb8c,0xe0b0,0x7265,0xc59b,0xc148,0x226c,0x11b0,0x6d74,0xa8aa,0x0ce1,0xd12d,0xef50,0x50dd,0x5f64,0x335c,0x93aa,0x966f,0x3b36,0xcdbe,0x006a,0xb981,0x58ec,0xfce3,0x37cc,0x1c99,0x76a1,0xf88b,0x3fbd,0x479c,0x82df,0x4a0f,0xbc0c,0x8641,0xb52f,0xe72e,0x0b3d,0x9e42,0xb162,0x7a5e,0x2a8b,0xa460,0x04a3,0xd2a6,0xa1ea,0x8aef,0x2650,0x158c,0x9bfe,0xf049}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87,0x76d4,0xf025,0x4a6a,0xc5bb,0x5f08,0xd959,0x50ae,0x2aff,0xa44c,0x3f9d,0xb9e2,0x3333,0x8a80,0x04d1,0x9e26,0x1877,0x93c4,0x6d15,0xe75a,0x7eab,0xf8f8,0x7249,0xcd9e,0x47ef,0xc13c,0x588d,0xd2d2,0xac23,0x2670,0xa1c1,0x3b16,0xb567,0x0cb4,0x8605,0x004a,0x9b9b,0x15e8,0xef39,0x668e,0xe0df,0x7a2c}).toString(), Category.RENDER));
        instance = this;
        HudManager.register(new Watermark());
        HudManager.register(new ModuleList());
        HudManager.register(new Coords());
        HudManager.register(new Stats());
        HudManager.register(new Keystrokes());
        HudManager.register(new ArmorHud());
        HudManager.register(new ServerInfo());
        HudManager.register(new Vitals());
        GlassRenderer.settings = this::pushGlass;
    }

    /** Copies the glass settings into the renderer (runs on every flush, module on or off). */
    private void pushGlass() {
        GlassRenderer.mode = glassMode.is(LT.L14) ? GlassRenderer.Mode.CLEAR
                : glassMode.is(LT.L15) ? GlassRenderer.Mode.LENS
                : glassMode.is(LT.L13) ? GlassRenderer.Mode.TINTED
                : glassMode.is(LT.L12) ? GlassRenderer.Mode.BUBBLE : GlassRenderer.Mode.LIQUID;
        GlassRenderer.powerFactor = shapePower.getValue();
        GlassRenderer.liquidPower = liquidPower.getValue();
        GlassRenderer.motion = motion.getValue();
        GlassRenderer.flowSpeed = flowSpeed.getValue();
        GlassRenderer.blurIterations = blurIterations.getValue();
        GlassRenderer.blurRadius = blurRadius.getValue();
        GlassRenderer.blurDownscale = blurDownscale.getValue();
        GlassRenderer.noise = glassNoise.getValue();
        GlassRenderer.refractionPower = refraction.getValue();
        GlassRenderer.glowWeight = glowWeight.getValue();
        GlassRenderer.glowBias = glowBias.getValue();
        GlassRenderer.glowEdge0 = glowEdge0.getValue();
        GlassRenderer.glowEdge1 = glowEdge1.getValue();
        // follow the GUI accent (lightened like the dropdown pills), or the manual tint
        int tint = tintFromTheme.getValue()
                ? Colors.lerp(dev.koifih.client.ui.Theme.ACCENT, 0xFFFFFFFF, 0.35f)
                : glassTint.getARGB();
        GlassRenderer.tintR = ((tint >> 16) & 255) / 255f;
        GlassRenderer.tintG = ((tint >> 8) & 255) / 255f;
        GlassRenderer.tintB = (tint & 255) / 255f;
        GlassRenderer.tintStrength = glassTintStrength.getValue();
        GlassRenderer.chroma = glassChroma.getValue();
        GlassRenderer.darkness = glassDarkness.getValue();
        GlassRenderer.lensRefractPx = lensRefract.getValue();
        GlassRenderer.lensChromaPx = lensChroma.getValue();
        GlassRenderer.lensRipple = lensRipple.getValue();
        GlassRenderer.lensHighlight = lensHighlight.getValue();
    }

    /** Pushes settings into the renderer every tick; handles the editor button/key. */
    @EventSubscribe
    private void onTick(EventTick event) {
        HudManager.enabled = isEnabled();
        HudManager.panelOpacity = opacity.getValue();

        if (openEditor.getValue()) {
            openEditor.setValue(false);
            openEditor();
        }
        boolean down = mc.currentScreen == null && editorKey.getKey() > 0 && InputHandler.isBindDown(editorKey.getKey());
        if (down && !editorKeyWasDown) openEditor();
        editorKeyWasDown = down;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        HudManager.enabled = false;
    }

    private void openEditor() {
        mc.execute(() -> mc.setScreen(new HudEditorScreen(mc.currentScreen)));
    }

    // ---- elements ------------------------------------------------------------------------

    private final class Watermark extends HudElement {
        Watermark() { super(LT.L38, LT.L39, 0.005f, 0.005f); }

        @Override public boolean active() { return isEnabled(); }

        private String fps() { return "  " + mc.getCurrentFps() + LT.L40; }

        @Override public float width() { return Text.width(LT.L41, 9f) + Text.width(fps(), 7f) + 16; }
        @Override public float height() { return 18; }

        @Override
        public void render(DrawContext ctx, boolean preview) {
            Draw.rect(ctx, 0, 0, 3, 18, 2, Theme.ACCENT);
            Text.drawCentered(ctx, LT.L42, 9, 9, 9f, Theme.TEXT);
            Text.drawCentered(ctx, LT.L43, 9 + Text.width(LT.L42, 9f), 9, 9f, Theme.ACCENT);
            Text.drawCentered(ctx, fps(), 9 + Text.width(LT.L41, 9f), 9, 7f, Theme.MUTED);
        }
    }

    private String label(Module m) {
        String s = m.getName();
        if (suffixes.getValue() && m instanceof Feature f) {
            String suffix = f.concat().toString();
            if (!suffix.isEmpty()) s += " " + suffix;
        }
        return s;
    }

    private List<Module> listed(boolean preview) {
        List<Module> mods = new ArrayList<>();
        for (Module m : ModuleManager.all()) {
            float target = (m.isEnabled() || (preview && mods.size() < 4)) && m != HudOverlay.this ? 1f : 0f;
            float v = slide.getOrDefault(m, 0f);
            v += (target - v) * 0.2f;
            if (Math.abs(target - v) < 0.01f) v = target;
            slide.put(m, v);
            if (v > 0.01f) mods.add(m);
        }
        mods.sort((a, b) -> Float.compare(Text.width(label(b), 7.5f), Text.width(label(a), 7.5f)));
        return mods;
    }

    private final class ModuleList extends HudElement {
        private List<Module> cache = List.of();

        ModuleList() { super(LT.L44, LT.L45, 0.995f, 0.005f); }

        @Override public boolean wantsBackground() { return false; } // per-row backgrounds
        @Override public float width() {
            float w = 60;
            for (Module m : cache) w = Math.max(w, Text.width(label(m), 7.5f) + 12);
            return w;
        }
        @Override public float height() {
            float h = 0;
            for (Module m : cache) h += 12 * slide.getOrDefault(m, 0f) + 1;
            return Math.max(12, h);
        }
        @Override public boolean active() {
            cache = listed(false);
            return isEnabled() && !cache.isEmpty();
        }

        @Override
        public void render(DrawContext ctx, boolean preview) {
            if (preview) cache = listed(true);
            // align to the side of the screen the element sits on
            boolean right = x > 0.5f;
            float w = width();
            long now = System.currentTimeMillis();
            float y = 0;
            int i = 0;
            for (Module m : cache) {
                float v = slide.getOrDefault(m, 0f);
                String text = label(m);
                float rw = Text.width(text, 7.5f) + 10;
                float rx = right ? w - rw * v : -rw * (1 - v);
                int color = switch (listColor.getValue().toString()) {
                    case "Rainbow" -> java.awt.Color.HSBtoRGB(((now / 20 + i * 18) % 360) / 360f, 0.55f, 1f) | 0xFF000000;
                    case "White" -> 0xFFFFFFFF;
                    // accent rolling into a lighter tint down the list
                    case "Wave" -> Colors.lerp(Theme.ACCENT, Colors.lerp(Theme.ACCENT, 0xFFFFFFFF, 0.55f),
                            (float) (Math.sin(now / 420.0 - i * 0.55) * 0.5 + 0.5));
                    default -> Theme.ACCENT;
                };
                int h = Math.round(12 * v);
                Draw.rect(ctx, Math.round(rx), Math.round(y), Math.round(rw), h, 3,
                        Colors.withAlpha(Theme.SIDEBAR, HudManager.panelOpacity * 0.9f * v));
                Draw.rect(ctx, right ? Math.round(w - 2) : Math.round(rx), Math.round(y), 2, h, 1, Colors.withAlpha(color, v));
                Text.drawCentered(ctx, m.getName(), rx + 5, y + 6, 7.5f, Colors.withAlpha(color, v));
                if (!text.equals(m.getName())) {
                    Text.drawCentered(ctx, text.substring(m.getName().length()), rx + 5 + Text.width(m.getName(), 7.5f),
                            y + 6, 7.5f, Colors.withAlpha(Theme.MUTED, v));
                }
                y += 12 * v + 1;
                i++;
            }
        }
    }

    private final class Coords extends HudElement {
        Coords() { super(LT.L46, LT.L47, 0.005f, 0.995f); }

        @Override public boolean active() { return isEnabled(); }

        private String text() {
            if (mc.player == null) return LT.L48;
            return String.format(Locale.ROOT, LT.L49, mc.player.getX(), mc.player.getY(), mc.player.getZ());
        }

        @Override public float width() { return Text.width(text(), 7f) + 20; }
        @Override public float height() { return 13; }
        @Override public float radius() { return 4; }
        @Override public void render(DrawContext ctx, boolean preview) {
            com.utilitymod.echo.render.Icons.drawItem(ctx, new net.minecraft.item.ItemStack(net.minecraft.item.Items.COMPASS), 4, 2.5f, 8);
            Text.drawCentered(ctx, text(), 15, 6.5f, 7f, Theme.TEXT);
        }
    }

    private final class Stats extends HudElement {
        Stats() { super(LT.L50, LT.L51, 0.005f, 0.94f); }

        @Override public boolean active() { return isEnabled(); }

        private String text() {
            int ping = 0;
            if (mc.getNetworkHandler() != null && mc.player != null) {
                PlayerListEntry e = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
                if (e != null) ping = e.getLatency();
            }
            return mc.getCurrentFps() + LT.L52 + ping + LT.L53;
        }

        @Override public float width() { return Text.width(text(), 7f) + 30; }
        @Override public float height() { return 13; }
        @Override public float radius() { return 4; }
        @Override public void render(DrawContext ctx, boolean preview) {
            String[] parts = text().split("  ");
            com.utilitymod.echo.render.Icons.drawItem(ctx, new net.minecraft.item.ItemStack(net.minecraft.item.Items.REDSTONE), 4, 2.5f, 8);
            Text.drawCentered(ctx, parts[0], 15, 6.5f, 7f, Theme.TEXT);
            float x2 = 15 + Text.width(parts[0], 7f) + 7;
            com.utilitymod.echo.render.Icons.drawItem(ctx, new net.minecraft.item.ItemStack(net.minecraft.item.Items.ENDER_PEARL), x2, 2.5f, 8);
            Text.drawCentered(ctx, parts[1], x2 + 11, 6.5f, 7f, Theme.TEXT);
        }
    }

    // ---- Keystrokes / CPS ------------------------------------------------------------------------

    private final java.util.ArrayDeque<Long> leftClicks = new java.util.ArrayDeque<>(), rightClicks = new java.util.ArrayDeque<>();
    private boolean leftWas, rightWas;
    private final Map<String, Float> keyGlow = new HashMap<>();

    private int cps(java.util.ArrayDeque<Long> q, boolean down, boolean was) {
        long now = System.currentTimeMillis();
        if (down && !was) q.addLast(now);
        while (!q.isEmpty() && now - q.peekFirst() > 1000) q.pollFirst();
        return q.size();
    }

    private final class Keystrokes extends HudElement {
        Keystrokes() { super(LT.L54, LT.L55, 0.005f, 0.75f); }

        @Override public boolean active() { return isEnabled(); }
        @Override public boolean wantsBackground() { return false; }
        @Override public float width() { return 62; }
        @Override public float height() { return 80; }

        private void key(DrawContext ctx, String id, String label, float x, float y, float w, float h, boolean down) {
            float g = keyGlow.getOrDefault(id, 0f);
            g += ((down ? 1f : 0f) - g) * 0.35f;
            keyGlow.put(id, g);
            Draw.rect(ctx, Math.round(x), Math.round(y), Math.round(w), Math.round(h), 4,
                    Colors.lerp(Colors.withAlpha(Theme.SIDEBAR, HudManager.panelOpacity * 0.85f), Colors.withAlpha(Theme.ACCENT, 0.9f), g));
            Text.drawCenteredX(ctx, label, x + w / 2f, y + h / 2f, 7f, Colors.lerp(Theme.TEXT, 0xFF15161A, g * 0.8f));
        }

        @Override
        public void render(DrawContext ctx, boolean preview) {
            long win = mc.getWindow().getHandle();
            // physical buttons or the binding being held (module clicks hold it briefly)
            boolean lmb = mc.currentScreen == null && (GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS
                    || mc.options.attackKey.isPressed());
            boolean rmb = mc.currentScreen == null && (GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS
                    || mc.options.useKey.isPressed());
            // Count every queued click, even on a binding-state transition. Only suppress
            // the extra polled edge when this frame already contains a click for that button.
            boolean simulatedLeft = false, simulatedRight = false;
            for (int[] c; (c = com.utilitymod.echo.handlers.InputHandler.SIMULATED_CLICKS.poll()) != null; ) {
                long now = System.currentTimeMillis();
                if (c[0] == 0) { leftClicks.addLast(now); simulatedLeft = true; }
                if (c[0] == 1) { rightClicks.addLast(now); simulatedRight = true; }
            }
            int l = cps(leftClicks, lmb, leftWas || simulatedLeft);
            int r = cps(rightClicks, rmb, rightWas || simulatedRight);
            leftWas = lmb;
            rightWas = rmb;
            key(ctx, LT.L56, LT.L57, 21, 0, 20, 18, mc.options.forwardKey.isPressed());
            key(ctx, LT.L58, LT.L59, 0, 21, 20, 18, mc.options.leftKey.isPressed());
            key(ctx, LT.L60, LT.L61, 21, 21, 20, 18, mc.options.backKey.isPressed());
            key(ctx, LT.L62, LT.L63, 42, 21, 20, 18, mc.options.rightKey.isPressed());
            key(ctx, LT.L64, l + LT.L65, 0, 42, 30.5f, 18, lmb);
            key(ctx, LT.L66, r + LT.L65, 31.5f, 42, 30.5f, 18, rmb);
            key(ctx, LT.L67, "\u2014\u2014", 0, 63, 62, 14, mc.options.jumpKey.isPressed());
        }
    }

    // ---- Armor ---------------------------------------------------------------------------------

    private final class ArmorHud extends HudElement {
        private final net.minecraft.entity.EquipmentSlot[] slots = {net.minecraft.entity.EquipmentSlot.HEAD,
                net.minecraft.entity.EquipmentSlot.CHEST, net.minecraft.entity.EquipmentSlot.LEGS,
                net.minecraft.entity.EquipmentSlot.FEET, net.minecraft.entity.EquipmentSlot.MAINHAND};

        ArmorHud() { super(LT.L68, LT.L69, 0.5f, 0.86f); }

        @Override public boolean active() { return isEnabled() && mc.player != null; }
        @Override public float width() { return slots.length * 22 + 6; }
        @Override public float height() { return 30; }
        @Override public float radius() { return 6; }

        @Override
        public void render(DrawContext ctx, boolean preview) {
            if (mc.player == null) return;
            float x = 4;
            for (var slot : slots) {
                net.minecraft.item.ItemStack st = mc.player.getEquippedStack(slot);
                if (!st.isEmpty()) {
                    ctx.drawItem(st, Math.round(x + 1), 3);
                    if (st.isDamageable()) {
                        float left = 1f - st.getDamage() / (float) Math.max(1, st.getMaxDamage());
                        int col = left > 0.5f ? 0xFF4ADE80 : left > 0.25f ? 0xFFFBBF24 : 0xFFF87171;
                        Draw.rect(ctx, Math.round(x + 1), 22, 16, 2, 1, 0x80000000);
                        Draw.rect(ctx, Math.round(x + 1), 22, Math.max(1, Math.round(16 * left)), 2, 1, col);
                    } else if (st.getCount() > 1) {
                        Text.drawCentered(ctx, Integer.toString(st.getCount()), x + 10, 24, 6f, Theme.TEXT);
                    }
                }
                x += 22;
            }
        }
    }

    // ---- Server info -----------------------------------------------------------------------------

    private long lastTimePacket, joinedServerAt;
    private final float[] tpsSamples = new float[6];
    private int tpsIndex, tpsCount;

    @EventSubscribe
    private void onPacket(com.utilitymod.echo.event.impl.EventPacketReceive event) {
        if (!(event.getPacket() instanceof net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket)) return;
        long now = System.currentTimeMillis();
        if (lastTimePacket != 0) {
            long gap = now - lastTimePacket; // server sends this every 20 ticks
            if (gap > 0) {
                tpsSamples[tpsIndex] = Math.min(20f, 20f * 1000f / gap);
                tpsIndex = (tpsIndex + 1) % tpsSamples.length;
                tpsCount = Math.min(tpsCount + 1, tpsSamples.length);
            }
        }
        lastTimePacket = now;
    }

    private float tps() {
        if (tpsCount == 0) return 20f;
        float sum = 0;
        for (int i = 0; i < tpsCount; i++) sum += tpsSamples[i];
        return sum / tpsCount;
    }

    private final class ServerInfo extends HudElement {
        ServerInfo() { super(LT.L70, LT.L71, 0.005f, 0.88f); }

        @Override public boolean active() {
            boolean on = isEnabled() && mc.getCurrentServerEntry() != null && mc.player != null;
            if (!on) {
                joinedServerAt = 0;
                tpsCount = 0;
                lastTimePacket = 0;
            } else if (joinedServerAt == 0) {
                joinedServerAt = System.currentTimeMillis();
            }
            return on;
        }

        private String text() {
            int ping = 0;
            if (mc.getNetworkHandler() != null && mc.player != null) {
                PlayerListEntry e = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
                if (e != null) ping = e.getLatency();
            }
            long secs = joinedServerAt == 0 ? 0 : (System.currentTimeMillis() - joinedServerAt) / 1000;
            String time = secs >= 3600 ? String.format(Locale.ROOT, LT.L72, secs / 3600, secs / 60 % 60)
                    : String.format(Locale.ROOT, LT.L73, secs / 60, secs % 60);
            return String.format(Locale.ROOT, LT.L74, ping, tps(), time);
        }

        private final net.minecraft.item.ItemStack[] icons = {new net.minecraft.item.ItemStack(net.minecraft.item.Items.ENDER_PEARL),
                new net.minecraft.item.ItemStack(net.minecraft.item.Items.REPEATER), new net.minecraft.item.ItemStack(net.minecraft.item.Items.CLOCK)};

        @Override public float width() { return Text.width(text(), 7f) + 40; }
        @Override public float height() { return 13; }
        @Override public float radius() { return 4; }
        @Override public void render(DrawContext ctx, boolean preview) {
            String[] parts = text().split("  ");
            float x = 4;
            for (int i = 0; i < parts.length && i < icons.length; i++) {
                com.utilitymod.echo.render.Icons.drawItem(ctx, icons[i], x, 2.5f, 8);
                Text.drawCentered(ctx, parts[i], x + 11, 6.5f, 7f, Theme.TEXT);
                x += 11 + Text.width(parts[i], 7f) + 7;
            }
        }
    }

    // ---- Vitals: health / armour / hunger / air bars with icons ---------------------------------

    private final Map<String, Float> vitalShown = new HashMap<>();

    private final class Vitals extends HudElement {
        Vitals() { super(LT.L75, LT.L76, 0.5f, 0.78f); }

        @Override public boolean active() { return isEnabled() && mc.player != null && !mc.player.isCreative() && !mc.player.isSpectator(); }
        @Override public float width() { return 110; }
        @Override public float height() { return airShown() ? 52 : 40; }
        @Override public float radius() { return 6; }

        private boolean airShown() {
            return mc.player != null && mc.player.getAir() < mc.player.getMaxAir();
        }

        private float y;

        private void bar(DrawContext ctx, String icon, String key, float value, int color, String label) {
            float v = vitalShown.getOrDefault(key, value);
            v += (value - v) * 0.2f;
            vitalShown.put(key, v);
            com.utilitymod.echo.render.Icons.draw(ctx, icon, 5, y, 8, color);
            Draw.rect(ctx, 16, Math.round(y + 3), 72, 3, 1, Colors.withAlpha(Theme.TRACK, 0.9f));
            Draw.rect(ctx, 16, Math.round(y + 3), Math.max(2, Math.round(72 * Math.max(0f, Math.min(1f, v)))), 3, 1, color);
            Text.drawCentered(ctx, label, 92, y + 4.5f, 6.3f, Theme.MUTED);
            y += 12;
        }

        @Override
        public void render(DrawContext ctx, boolean preview) {
            if (mc.player == null) return;
            y = 4;
            float hp = mc.player.getHealth() + mc.player.getAbsorptionAmount();
            float maxHp = Math.max(1f, mc.player.getMaxHealth());
            bar(ctx, LT.L77, LT.L78, Math.min(1f, hp / maxHp), mc.player.getAbsorptionAmount() > 0 ? 0xFFFBBF24 : 0xFFF87171,
                    Math.round(hp) + "");
            bar(ctx, LT.L79, LT.L68, mc.player.getArmor() / 20f, 0xFF93C5FD, mc.player.getArmor() + "");
            int food = mc.player.getHungerManager().getFoodLevel();
            bar(ctx, LT.L80, LT.L81, food / 20f, 0xFFD6A15E, food + "");
            if (airShown()) {
                bar(ctx, LT.L82, LT.L83, Math.max(0, mc.player.getAir()) / (float) mc.player.getMaxAir(), 0xFF7DD3FC,
                        Math.max(0, mc.player.getAir() / 30) + "");
            }
        }
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8ba7,0x784e,0xd6e1,0xed95,0x9aa8,0xad4e,0x401b,0x1a2e,0xe640,0x286d,0xcdf8,0x97df,0x8297,0x4071,0x62ff}, new char[]{0x82d8,0x783e,0x1a4b,0xad20,0xe660,0x9ae0,0xd6b4,0x62bb,0x4051,0x8be2,0xcd9c,0x97b6,0xede1,0x2802,0x4069}).toString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x08f8,0x7d83,0x106a,0xa79d,0x7fb8,0xa367,0xd126,0xca74,0xa8bf,0x8cab}, new char[]{0xd163,0xca10,0xa8d6,0x7df7,0x7fd7,0x8cd9,0x08d8,0xa32c,0x100f,0xa7e4}).toString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xbb91,0x9fa1,0xe670,0xe885,0xe225,0x0b7e,0x93bd,0xf40a,0x9144,0x8696,0xc829,0x9e90,0x85a1}, new char[]{0x9ff1,0x85c0,0xe24b,0xe615,0xbbfd,0x0b5e,0x86d9,0x93cd,0xe8e4,0x9127,0x9ef9,0xf47e,0xc850}).toString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2e32,0x54c5,0xec0b,0x4e43,0x650d,0x2f22,0xa56d,0xd13a,0x8082,0x1ab3}, new char[]{0x4e0f,0xa504,0x54b6,0x80f6,0xec2b,0x1af0,0x2e5d,0xd156,0x6562,0x2f50}).plainString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd7d8,0xb269,0x981b,0xbc23}, new char[]{0xbc74,0xb208,0x986d,0xd7bd}).plainString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8192,0x4e1a,0x8efc,0xad1e,0x40e4,0x31c5}, new char[]{0x8ebd,0x31a6,0x81f1,0x4081,0x4e74,0xad6a}).plainString();
        static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x48ec,0xd036,0x85f3,0x0ad7,0x25c9,0xa0e0,0x7f0c}, new char[]{0x7f5e,0x8592,0x25a0,0xd058,0x0ab5,0xa08f,0x489b}).plainString();
        static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf9e7,0x5c9c,0xe1e4,0x6906,0x83bf}, new char[]{0x6951,0xf98f,0x5cf5,0xe190,0x83da}).plainString();
        static final String L9 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2f0c,0xadd3,0x3411,0x0864,0xa594,0x69a8,0x168f,0x487f}, new char[]{0x482c,0x16fa,0xa5f2,0x0802,0x2f65,0x3469,0x69cd,0xada0}).toString();
        static final String L10 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xdf7d,0xeff2,0x363d,0xe5ff,0x2609,0x0f87,0xe3d3,0x25dd,0x9ff6,0x78cd}, new char[]{0xe5b8,0xe3bf,0x78ac,0x364e,0x9f85,0x0fa7,0x2590,0xdf12,0xef96,0x266c}).plainString();
        static final String L11 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x08c7,0x9502,0x1825,0x4d13,0xbb64,0x2baa}, new char[]{0x4d5f,0xbb0d,0x2bdb,0x08b2,0x956b,0x1841}).plainString();
        static final String L12 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9b3a,0x1bbf,0xcd7a,0xab5f,0x2de4,0x7caf}, new char[]{0xab1d,0x2d91,0x9b58,0x1bdd,0xcd16,0x7cd6}).plainString();
        static final String L13 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x53e7,0x0bff,0xbf61,0x280d,0xf3d7,0x07b2}, new char[]{0x53b3,0xf3be,0xbf0f,0x2879,0x07d7,0x0b9b}).plainString();
        static final String L14 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4aad,0x3e95,0x4749,0xa20f,0x80ab}, new char[]{0x4aee,0xa263,0x3ef0,0x4728,0x80d9}).plainString();
        static final String L15 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8035,0xff3c,0x4c6b,0x5e0c}, new char[]{0x4c27,0xff59,0x805b,0x5e7f}).plainString();
        static final String L16 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd667,0xe001,0x9b7d,0x51d4,0x3b0a,0x576f,0xb206,0xe702,0x61c6,0x799e,0x53e2}, new char[]{0x573c,0x61ae,0xb267,0xe071,0x3b6f,0x79be,0xe752,0x9b12,0x51a3,0xd602,0x5390}).toString();
        static final String L17 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2f84,0x123c,0x131e,0xc871,0x5b99,0x6257,0x909b,0x1651,0xac91,0xdfe2,0x4652,0xedf7,0x934c}, new char[]{0x1270,0xc818,0xace0,0x1624,0x90f2,0x2fe0,0x4672,0xedba,0x1371,0x6223,0x5bf0,0x9323,0xdf8c}).toString();
        static final String L18 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9334,0x9433,0x0368,0xbd46,0x20db,0xfa16,0xfe3b,0x72aa,0xaac7,0x59fc}, new char[]{0xfa50,0x20b7,0xaaa8,0xbd31,0x0348,0x72f9,0x9344,0x5999,0x9456,0xfe5f}).toString();
        static final String L19 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5303,0xecb9,0xe89e,0x3e24,0x84e6,0x014b,0xa3a1,0xa28a,0xee8a,0x324c,0x94b1,0x2292,0xa531,0x9895,0x5bfe,0xb1e1,0x2c3c}, new char[]{0xeec6,0xecd0,0xa540,0x8493,0x0122,0x3228,0x2c1c,0x22c0,0x5366,0x5b98,0xe8ec,0xa2eb,0x98f6,0xa3d5,0x3e4d,0x94de,0xb18f}).toString();
        static final String L20 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4ce9,0x2eff,0xab8c,0xbbf4,0x7226,0x08c2,0x3a19,0x740d,0x2563,0x0ab1,0x0564,0x4239,0xabd4,0x38f9,0x3dfc}, new char[]{0x427b,0x0add,0xaba1,0x388b,0x3a39,0xabc5,0x08b6,0x7468,0x2511,0x0505,0x2e8b,0x4c80,0xbb9b,0x3d92,0x7255}).toString();
        static final String L21 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x33cf,0xacce,0xede7,0xd6da,0xe896,0x5f3b,0x342d,0xd14b,0xa24e,0xed9e,0xa68b}, new char[]{0x338d,0xa222,0xe8e3,0xa6f9,0x5f1b,0xd688,0xacaf,0xedfa,0x3444,0xd13e,0xed94}).toString();
        static final String L22 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xea58,0x7480,0x7b6d,0x652f,0xd118,0x0138,0x9728,0x370a,0x1d3f,0xf858,0xe82d,0x5c7b,0x78bf,0x7e8e}, new char[]{0xd15a,0x74ec,0xe858,0x7b1f,0xea78,0x656b,0x9747,0x377d,0x7ee0,0x5c08,0x78dc,0xf839,0x0154,0x1d5a}).toString();
        static final String L23 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf56e,0x054d,0x1db1,0xa241,0x0337}, new char[]{0xa20f,0x0358,0x1dd8,0xf51d,0x0528}).toString();
        static final String L24 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1a85,0xb1e6,0xb379,0xf194,0xd0a2,0x35bc,0x2891,0x0795,0x9148,0x77b4,0x94b5,0xbf22,0x1764,0x18d2,0xd6bf,0x1f3d}, new char[]{0x1880,0x28f4,0xd0c4,0x1f4f,0x77d5,0x94d6,0x1710,0x9121,0x1aea,0x07fb,0xbf02,0xf1c4,0xb316,0xb191,0xd6da,0x35ce}).toString();
        static final String L25 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8e20,0x0d8d,0x88ee,0x9b10,0x19df,0xdf6e,0x7dc3,0xee6e,0x17f5,0xea31,0x25d1}, new char[]{0x2596,0x8e4c,0x9b7f,0xee19,0x0dad,0xea66,0x1790,0x19b6,0x8889,0xdf06,0x7db7}).toString();
        static final String L26 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc292,0x42c0,0x0d9f,0xb5a3,0x35d5,0x1fbf,0x4fe9,0x0263,0x4d63}, new char[]{0x3592,0x1fd3,0x4d0c,0x0214,0x0dbf,0x4282,0xb5ca,0xc2f3,0x4f9a}).toString();
        static final String L27 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7cbe,0x5546,0xf4a6,0x8e4a,0xf5c6,0xc706,0x4ba7,0xf11a,0x66f1,0x1e60}, new char[]{0x5501,0xc76a,0x4bc8,0xf5b1,0xf13a,0x7cfb,0xf4c2,0x1e07,0x8e2f,0x66c1}).toString();
        static final String L28 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6b99,0xbae6,0xb494,0xad2d,0xe560,0xdc0c,0xa6a6,0x5b4e,0x666a,0x57c5}, new char[]{0xdc4b,0x6bf5,0xa6c9,0xe517,0x664a,0xb4d1,0xad49,0x57a2,0xba83,0x5b7f}).toString();
        static final String L29 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa8cf,0x4644,0x1735,0x7911,0xb3c9,0xf33e,0x5f17,0xd7f5,0xb089,0x82e3,0x6e38,0xcc7d,0xd4ae,0xd8ab,0xb8ea}, new char[]{0x1778,0xd794,0x4630,0xb0ea,0x6e50,0x7931,0x82a4,0xcc28,0x5f5e,0xd48e,0xb38a,0xb885,0xd8c7,0xf351,0xa8bd}).toString();
        static final String L30 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3dec,0x40b4,0xce36,0x7416,0x6747,0xedef,0x13be,0x2032,0x6a41,0x08bc}, new char[]{0x08fb,0xce5a,0x6726,0x13cd,0x40c7,0x6a61,0x3db8,0x205b,0xed81,0x7462}).toString();
        static final String L31 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xbf8c,0xb74a,0x852c,0xf664,0xa648,0x4787,0xc731,0xecc8,0x5a26,0xa2b5,0x86cd,0xad3b,0x7be5}, new char[]{0xa2e1,0xc758,0xa626,0xbff8,0x47a7,0x869e,0xb73e,0x5a54,0xf601,0xad55,0x854b,0xecbc,0x7b8d}).toString();
        static final String L32 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2a64,0x506b,0xfe74,0x4af9,0x7afe,0x86de,0x9d63,0xaf8f,0xcb45,0x886f,0x0179,0xf453,0x6229,0x0dc7,0x2d93}, new char[]{0x5028,0x9d0b,0x625b,0x8800,0xf43e,0x0118,0xafaf,0x0d94,0x7a8a,0x86ac,0xcb20,0xfe1a,0x2a03,0x4a8d,0x2dfb}).toString();
        static final String L33 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x51aa,0x587d,0x8a84,0x98ba,0x48c7,0x3d42,0xdfc7,0x0d72}, new char[]{0x98fe,0x581c,0x48b5,0x0d19,0xdfa9,0x51cf,0x8af7,0x3d31}).toString();
        static final String L34 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe2c3,0x7051,0x46dc,0x6f5c,0xd20a,0xb8f6,0xc7a5,0xcf77,0xcf73,0x6d62,0xf7e0,0x28bd,0x3d11,0x4503,0x1fac}, new char[]{0xf7ac,0x7034,0xe2ad,0x1fdf,0xcf53,0x6d30,0xb893,0xc7c3,0xd278,0xcf16,0x28de,0x6f28,0x46b5,0x3d7e,0x456d}).toString();
        static final String L35 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2f5e,0xd7c0,0xbdda,0x47de,0x7c7d,0x0483,0xab73,0x686c,0xc4a8,0x0bf8,0xa631}, new char[]{0x04cf,0x7c18,0xc4c6,0xd7b3,0x684c,0x2f1d,0x47b6,0xa643,0x0b97,0xbdb7,0xab12}).toString();
        static final String L36 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9017,0xa149,0x0fa4,0x98bc,0xeda4,0x7c13,0x01b4,0x8305,0x7a5b,0xa692,0x788e}, new char[]{0x905b,0x98d9,0x78e0,0x01c7,0xa169,0x7a09,0xa6fb,0x0fd4,0x8375,0xedc8,0x7c76}).toString();
        static final String L37 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3b71,0xea9b,0x26f3,0xa378,0x235d,0x2cbd,0x57a5,0x5d57,0x6e00}, new char[]{0x3b39,0x6e69,0x57c2,0x2335,0xa314,0x5d3e,0xeafc,0x269b,0x2cc9}).toString();
        static final String L38 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x73f5,0x3f02,0x9d2c,0xd6cf,0xd15e,0x041e,0x6e51,0x50bd,0xb9ff}, new char[]{0x6e26,0xd13f,0x7381,0x3f67,0x9d5e,0xd6a2,0xb99e,0x50cf,0x0475}).plainString();
        static final String L39 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8f61,0x5aea,0x0482,0xb7cc,0xac24,0x7f39,0xf5e1,0x5560,0x2fc0}, new char[]{0x8f36,0xac45,0x2fb4,0x5505,0x7f4b,0x5a87,0xf580,0xb7be,0x04e9}).toString();
        static final String L40 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7a22,0x8b04,0x5090,0xf6e3}, new char[]{0x7a02,0x50f6,0xf693,0x8b77}).plainString();
        static final String L41 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0aff,0x17c8,0xcd4f,0x9612,0xebf1,0xd8ba,0xb7bb,0x9fda,0x3cc6,0x6bcd}, new char[]{0xeb84,0x9666,0x0a96,0xd8d6,0xb7d2,0x3cb2,0x6bb4,0xcd22,0x17a7,0x9fbe}).plainString();
        static final String L42 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa366,0xba0b,0xbddd,0xcfc7,0x4ff0,0xe56f,0x7229}, new char[]{0xa313,0x4f84,0xe506,0xcfab,0x7240,0xbda9,0xba72}).plainString();
        static final String L43 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x052b,0xb05f,0x5e54}, new char[]{0x0546,0xb030,0x5e30,0x435e}).plainString();
        static final String L44 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9a03,0xde61,0x8526,0x3ba7,0x01a4,0x3d82,0xb2e4,0x129a,0xd3a8,0xeed6}, new char[]{0xd3c5,0x8549,0x01c0,0xde14,0x9a6f,0x12ff,0x3bcb,0xeebf,0xb297,0x3df6}).plainString();
        static final String L45 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0c4c,0xfea6,0x187f,0xe032,0x65ac,0x47d1,0xa635,0xcc35,0x7f74,0xc2e5,0x4790}, new char[]{0x0c01,0xfec9,0xcc51,0x47a4,0x1813,0xc280,0x47b0,0xe07e,0x7f1d,0x65df,0xa641}).toString();
        static final String L46 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x53ea,0x13ac,0x0abc,0x1248,0x0cd9,0xc15a}, new char[]{0x13cf,0x0cb6,0xc135,0x0ace,0x538e,0x123b}).plainString();
        static final String L47 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x068f,0x03ad,0x34bc,0x9b65,0xb0d0,0x47d6,0xce99,0xe3c9,0xe56f,0x8f18,0xa592}, new char[]{0x06cc,0x47b9,0xe500,0x03df,0x34d8,0x8f71,0xa5fc,0xcef8,0x9b11,0xb0b5,0xe3ba}).toString();
        static final String L48 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x674e,0xeccb,0x4b96,0x24cd,0x7074,0x4154,0x47e7,0xc6ee,0x554d}, new char[]{0x5515,0x6717,0xc6b4,0xeceb,0x4ba6,0x7054,0x4164,0x47c7,0x24fd}).toString();
        static final String L49 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe28d,0x8f95,0xb29a,0x086a,0xc163,0x7853,0x57ec,0x6988,0xabed,0xb5e3,0x4765,0xee4f,0x4951,0xca6c,0x9b5d,0x887f,0x2ef0,0x855a}, new char[]{0x4909,0xb2c3,0x2eaa,0x4745,0xee6a,0x0844,0xca5d,0x7835,0x885f,0xabc8,0x57c2,0x8fa4,0x69ee,0x857a,0x9b78,0xe2a3,0xc152,0xb585}).plainString();
        static final String L50 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x306b,0x8fb9,0x22da,0xc767,0xd1ae}, new char[]{0x22a9,0x301f,0xc706,0xd1da,0x8fca}).plainString();
        static final String L51 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb586,0x9766,0x34ac,0xa638,0x9aae,0xbe7d,0x76a4,0x67a4,0x4136,0xb258}, new char[]{0x9720,0x76f4,0xb20b,0xb5a6,0xbe52,0x6784,0x34fc,0xa651,0x9ac0,0x4151}).toString();
        static final String L52 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xfbd6,0xc8e2,0x3866,0xea89,0xa249,0x117e}, new char[]{0xc8c2,0x3820,0xfb86,0xeada,0x115e,0xa269}).toString();
        static final String L53 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6c6e,0x422e,0x93d2}, new char[]{0x6c4e,0x4243,0x93a1,0x24b1}).plainString();
        static final String L54 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3483,0xa83d,0x8554,0x662c,0x2100,0x5bb9,0xb1d9,0x17a4,0x99ad,0x60e6}, new char[]{0xb1b2,0x5bdc,0x34fa,0x2173,0x99d9,0x6094,0xa852,0x853f,0x6649,0x17d7}).plainString();
        static final String L55 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xfcac,0x6fe7,0xf598,0xe495,0xc3b0,0x3dd3,0x7553,0xce2d,0x0417,0x1cf2}, new char[]{0xce66,0xe4f0,0xc3c9,0x3da0,0x6f93,0x0465,0xf5f7,0x7538,0x1c97,0xfcdf}).toString();
        static final String L56 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2ae3}, new char[]{0x2a94,0xc428,0xe427,0x58ff}).plainString();
        static final String L57 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa4dc}, new char[]{0xa48b,0x9c75,0x5ec7,0x0469}).plainString();
        static final String L58 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x112e}, new char[]{0x114f,0x24ae,0xb33e,0xf80d}).plainString();
        static final String L59 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x844e}, new char[]{0x840f,0xb0a5,0xef55,0x1cb1}).plainString();
        static final String L60 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1d32}, new char[]{0x1d41,0xdad7,0x0bf0,0x568e}).plainString();
        static final String L61 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0dfd}, new char[]{0x0dae,0xa7e3,0xba55,0xf65a}).plainString();
        static final String L62 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe09d}, new char[]{0xe0f9,0xf6de,0x66a8,0x4ae7}).plainString();
        static final String L63 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc9a2}, new char[]{0xc9e6,0xe6ff,0x4fad,0x77fc}).plainString();
        static final String L64 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9b22}, new char[]{0x9b4e,0x3891,0x50fa,0xc47e}).plainString();
        static final String L65 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd4df,0x3ab0,0xe778,0x9f2b}, new char[]{0x3a90,0xe73b,0xd48f,0x9f78}).plainString();
        static final String L66 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd6b0}, new char[]{0xd6c2,0x4042,0xa7ce,0x6142}).plainString();
        static final String L67 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf5f1,0x4a7a}, new char[]{0xf582,0x4a0a,0xbca5,0xb536}).plainString();
        static final String L68 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd4e8,0xb6fe,0x6715,0xfbbc,0xc6d1}, new char[]{0xfbdd,0x6767,0xc6bc,0xb691,0xd49a}).plainString();
        static final String L69 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xacf0,0x7cfc,0xc0a0,0xcdef,0x4144}, new char[]{0xacb1,0xc0d2,0xcd82,0x412b,0x7c8e}).toString();
        static final String L70 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe958,0x9bb8,0x0333,0x2d2c,0x7f25,0x693b,0x12cf,0x2136,0x422c,0x4c64}, new char[]{0x6948,0xe93d,0x9bca,0x2d5a,0x4249,0x7f57,0x035a,0x12a1,0x4c02,0x2159}).plainString();
        static final String L71 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x71fc,0xe889,0x746e,0x3598,0x222b,0x1b0d,0x3197,0x47c9,0xedba,0x3fdd,0xf5c7}, new char[]{0x35cb,0x224e,0xe8fb,0x47bf,0xeddf,0xf5b5,0x3ffd,0x1b44,0x7400,0x719a,0x31f8}).toString();
        static final String L72 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb8ce,0x0351,0xa17b,0x2245,0x2712,0x2dfc,0x4aa6,0x6c48,0x1959}, new char[]{0x2737,0xa11f,0x1931,0x2ddc,0x4a83,0x2275,0x6c7a,0x0335,0xb8a3}).plainString();
        static final String L73 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5dd2,0xb207,0x10ef,0x9bcd,0x8901,0xcb62,0x9a1e,0x2d1c,0x16a4}, new char[]{0x2d39,0x5db6,0xcb0f,0x9a3e,0x9be8,0x8931,0x10dd,0x16c0,0xb274}).plainString();
        static final String L74 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2c43,0xa764,0x29bb,0x820f,0x3669,0x709a,0x1da8,0xafe7,0x32e8,0x7d87,0xa8e6,0x09f1,0xcac4,0x1a26,0x7edb,0x91bf,0x2d32,0x2868,0x6c38}, new char[]{0x7efe,0x280c,0x6c18,0x3604,0x7df4,0xcae4,0x2c63,0x919a,0x32c6,0x2d03,0x1dce,0x299b,0xa730,0x825f,0x1a75,0xa8c6,0xafc7,0x70bf,0x0982}).plainString();
        static final String L75 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x85ee,0x5eed,0xe721,0xfd9a,0x40fc,0xfe49}, new char[]{0xe757,0x8587,0x4088,0x5e8c,0xfdf6,0xfe3a}).plainString();
        static final String L76 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xbf30,0x13aa,0x82e8,0x80dc,0xa26c,0xd22b}, new char[]{0xd27d,0x13c3,0x80a8,0x8289,0xa200,0xbf43}).toString();
        static final String L77 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xeedb,0x58c5,0xdd1c,0x4249,0xd06a}, new char[]{0xdd74,0xeebe,0x58a4,0x423b,0xd01e}).plainString();
        static final String L78 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc835,0x1a6c}, new char[]{0xc85d,0x1a1c,0x4a46,0x86ec}).plainString();
        static final String L79 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x09be,0xf884,0xe7d3,0x2551,0xef6b,0x49a0}, new char[]{0xf8f7,0x49c8,0x2538,0x09db,0xe7bf,0xef0f}).plainString();
        static final String L80 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3ea5,0x3870,0xaee0}, new char[]{0x3ecd,0xae81,0x381d,0x4906}).plainString();
        static final String L81 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xbede,0xd2b1,0x9002,0x5275}, new char[]{0x9064,0xbeb1,0x521a,0xd2d5}).plainString();
        static final String L82 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1568,0x3183,0xfc6b,0xdebc,0x3279,0xcf56}, new char[]{0x321b,0x31f6,0xfc09,0xcf34,0x1504,0xded9}).plainString();
        static final String L83 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf7f2,0xcb81,0x0e90}, new char[]{0x0ef1,0xcbe8,0xf780,0x2021}).plainString();
    }
}
