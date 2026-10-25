package com.utilitymod.gui;

import com.utilitymod.UtilityMod;
import com.utilitymod.core.Module;
import com.utilitymod.core.ModuleManager;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.settings.Setting;
import com.utilitymod.echo.features.settings.impl.KeybindSetting;
import com.utilitymod.echo.render.GlassRenderer;
import dev.koifih.client.render.Draw;
import dev.koifih.client.render.Scissor;
import dev.koifih.client.render.Text;
import dev.koifih.client.ui.component.Bool;
import dev.koifih.client.ui.component.Control;
import dev.koifih.client.ui.component.Keybind;
import dev.koifih.client.ui.component.Slider;
import dev.koifih.client.ui.component.popup.Popup;
import dev.koifih.client.util.Colors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.function.BiConsumer;

/**
 * Fourth GUI style ("Orbit"): categories float as liquid-glass orbs in a slowly spinning ring around a
 * central hub. Picking one swings the ring left and fans its modules out on a curved, scrollable wheel.
 * Module settings and client settings (theme, binds, configs, GUI) open in a glass panel in place of the wheel.
 */
public final class OrbitGui extends Screen {
    private static final int INK = 0xFFF6EEF8, MUTED = 0xA8E6D3E8;
    private static final float WHEEL_STEP = 0.165f, WHEEL_SPAN = 1.25f;
    private static final String[] TABS = {"Theme", "Binds", "Config", "GUI"};
    private static final int[] PRESETS = {0xFFCE52B3, 0xFFEC4899, 0xFFD946EF, 0xFF8B5CF6, 0xFF6366F1, 0xFF3B82F6,
            0xFF22D3EE, 0xFF10B981, 0xFF84CC16, 0xFFFBBF24, 0xFFF97316, 0xFFEF4444, 0xFFF0F1F6};

    private final List<CardGui.Cat> cats = CardGui.CATS;
    private final float[] hover;
    private final Map<Module, Float> pulse = new HashMap<>();
    private final StringBuilder query = new StringBuilder();
    private final float[][] sparks = new float[42][4];   // angle, radius factor, speed, size
    private final List<Hit> hits = new ArrayList<>();
    private final List<Act> acts = new ArrayList<>();

    private int sel = -1;
    private float selT, intro, spin, wheel, wheelGoal, caret, panelT;
    private boolean closing, failed;
    private long last = System.nanoTime();
    private List<Module> items = List.of();

    // settings panel state
    private Feature sheet;
    private final List<Row> rows = new ArrayList<>();
    private final List<Control> controls = new ArrayList<>();
    private Control focus;
    private boolean client;
    private int tab, colorSlot;
    private float scroll, scrollGoal, scrollMax;
    private float[] panel = new float[4];
    private Act dragging;
    private String bindingModule;
    private boolean bindingMenu, nameFocus, swallowChar;
    private final StringBuilder profileName = new StringBuilder();
    private List<String> profiles = List.of();
    private String notice = "", pendingDelete;

    private record Hit(float x, float y, float w, float h, int orb, Module module) {
        boolean in(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }
    private record Act(float x, float y, float w, float h, boolean drag, BiConsumer<Double, Double> fn) {
        boolean in(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }
    private record Row(Setting setting, Control control) {}

    public OrbitGui() {
        super(net.minecraft.text.Text.literal("Orbit"));
        hover = new float[cats.size()];
        Random r = new Random(7);
        for (float[] s : sparks) {
            s[0] = r.nextFloat() * 6.2831855f;
            s[1] = .45f + r.nextFloat() * 1.25f;
            s[2] = (.04f + r.nextFloat() * .12f) * (r.nextBoolean() ? 1 : -1);
            s[3] = 1f + r.nextFloat() * 1.4f;
        }
    }

    @Override protected void init() { GuiTextures.ensureLoaded(); }
    @Override public boolean shouldPause() { return false; }
    @Override public void renderBackground(DrawContext g, int mouseX, int mouseY, float delta) {}
    @Override public void removed() { closeSheet(); GuiConfig.save(); super.removed(); }

    /** Never leave the player locked out: on any failure fall back to the dropdown GUI. */
    private void fail(Throwable t) {
        if (failed) return;
        failed = true;
        UtilityMod.LOGGER.error("Orbit GUI failed, falling back to the dropdown GUI", t);
        FrameManager.style = FrameManager.Style.DROPDOWN;
        try { GuiConfig.save(); } catch (Throwable ignored) {}
        MinecraftClient.getInstance().setScreen(null);
    }

    // ---- model -------------------------------------------------------------------

    private List<Module> collect() {
        String q = query.toString().trim().toLowerCase(Locale.ROOT);
        List<Module> out = new ArrayList<>();
        if (!q.isEmpty()) {
            for (Module m : allModules()) if (plain(m.getName()).contains(q) && !out.contains(m)) out.add(m);
            return out;
        }
        if (sel < 0) return out;
        CardGui.Cat c = cats.get(sel);
        for (Module m : c.source()) if (c.matches(m)) out.add(m);
        return out;
    }

    private static List<Module> allModules() {
        List<Module> all = new ArrayList<>(ModuleManager.all());
        for (Module m : com.utilitymod.anubis.AnubisBridge.features()) all.add(m);
        return all;
    }

    private static String plain(String raw) {
        StringBuilder b = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) b.append(com.utilitymod.echo.utils.CloakUtil.plainChar(raw.charAt(i)));
        return b.toString().toLowerCase(Locale.ROOT);
    }

    private boolean panelOpen() { return sheet != null || client; }
    private boolean wheelOpen() { return sel >= 0 || query.length() > 0 || panelOpen(); }

    // ---- render ------------------------------------------------------------------

    @Override
    public void render(DrawContext g, int mouseX, int mouseY, float delta) {
        if (failed) return;
        try { draw(g, mouseX, mouseY, delta); } catch (Throwable t) { fail(t); }
    }

    private void draw(DrawContext g, int mouseX, int mouseY, float delta) {
        if (width < 32 || height < 32) return;   // minimised / alt-tabbed: nothing sensible to lay out
        long now = System.nanoTime();
        float dt = Math.min(.05f, (now - last) / 1e9f);
        last = now;
        hits.clear();
        acts.clear();
        items = collect();
        if (wheelGoal > items.size() - 1) wheelGoal = Math.max(0, items.size() - 1);

        intro = approach(intro, closing ? 0f : 1f, dt * (closing ? 9f : 4.5f));
        if (closing && intro <= .02f) { MinecraftClient.getInstance().setScreen(null); return; }
        selT = approach(selT, wheelOpen() ? 1f : 0f, dt * 7f);
        panelT = approach(panelT, panelOpen() ? 1f : 0f, dt * 8f);
        spin += dt * (wheelOpen() ? .012f : .035f);
        wheel += (wheelGoal - wheel) * Math.min(1f, dt * 10f);
        scroll += (scrollGoal - scroll) * Math.min(1f, dt * 14f);
        caret += dt;

        float e = smooth(selT), in = easeOut(intro);
        float cy = height / 2f;
        float R = Math.min(width, height) * .30f;
        float hx = lerp(width / 2f, width * .27f, e);
        float ringR = R * lerp(1f, .78f, e) * in;
        float px = (mouseX - width / 2f) / width, py = (mouseY - height / 2f) / height;
        int accent = CardGuiStyle.accent;

        g.createNewRootLayer();
        g.fillGradient(0, 0, width, height, Colors.withAlpha(0xFF07020A, .35f * intro), Colors.withAlpha(0xFF12030F, .55f * intro));
        Draw.rect(g, hx - R * 1.4f, cy - R * 1.4f, R * 2.8f, Math.round(R * 2.8f), Math.round(R * 1.4f), Colors.withAlpha(accent, .06f * intro));

        for (float[] s : sparks) {
            s[0] += s[2] * dt;
            float sr = R * s[1] * in, sx = hx + (float) Math.cos(s[0]) * sr - px * 18 * s[1], sy = cy + (float) Math.sin(s[0]) * sr * .92f - py * 18 * s[1];
            float tw = .25f + .2f * (float) Math.sin(s[0] * 3 + caret * .8f);
            Draw.rect(g, sx, sy, s[3], Math.max(1, Math.round(s[3])), 1, Colors.withAlpha(Colors.lerp(accent, 0xFFFFFFFF, .4f), tw * intro));
        }

        int n = cats.size();
        float[] ox = new float[n], oy = new float[n], or = new float[n];
        for (int i = 0; i < n; i++) {
            float stagger = clamp01(intro * 1.35f - i * .03f);
            float a = spin + i * 6.2831855f / n - 1.5707964f;
            float rr = ringR * easeOut(stagger);
            ox[i] = hx + (float) Math.cos(a) * rr - px * 10;
            oy[i] = cy + (float) Math.sin(a) * rr - py * 10;
            boolean h = dist(mouseX, mouseY, ox[i], oy[i]) < 20;
            hover[i] = approach(hover[i], h ? 1f : 0f, dt * 12f);
            or[i] = 15f * (1 + .12f * hover[i]) * (i == sel ? 1.18f : 1f) * stagger * lerp(1f, .9f, e);
        }

        for (int i = 0; i < n; i++) {
            int c = i == sel ? Colors.withAlpha(accent, .75f) : Colors.withAlpha(0xFFFFFFFF, .10f + .15f * hover[i]);
            dotted(g, hx, cy, ox[i], oy[i], 30, or[i] + 3, c, i == sel ? caret * 12 : 0);
        }

        // settings button under the hub
        float gx = hx, gy = cy + 48 * in, gr = 10 * in;
        boolean gearHov = dist(mouseX, mouseY, gx, gy) < gr + 3;

        float hubR = 30 * in;
        boolean glass = CardGuiStyle.glass && GlassRenderer.available();
        if (glass) {
            GlassRenderer.queue(new GlassRenderer.Panel(hx - hubR, cy - hubR, hubR * 2, hubR * 2, hubR, .95f * intro));
            if (gr > 1) GlassRenderer.queue(new GlassRenderer.Panel(gx - gr, gy - gr, gr * 2, gr * 2, gr, .95f * intro));
            for (int i = 0; i < n; i++) if (or[i] > 1)
                GlassRenderer.queue(new GlassRenderer.Panel(ox[i] - or[i], oy[i] - or[i], or[i] * 2, or[i] * 2, or[i], .95f * intro));
            flushGlass(g);
        }

        circle(g, hx, cy, hubR, Colors.withAlpha(Colors.lerp(CardGuiStyle.back, accent, .25f), glass ? .45f : .85f));
        ring(g, hx, cy, hubR + 6, 28, -spin * 1.5f, Colors.withAlpha(accent, .55f * intro));
        long on = 0; for (Module m : ModuleManager.all()) if (m.isEnabled()) on++;
        if (query.length() > 0) {
            String q = query + ((caret % 1f) < .5f ? "_" : " ");
            Text.drawCenteredX(g, "search", hx, cy - 8, 5.5f, MUTED);
            Text.drawCenteredX(g, Text.fit(q, hubR * 2 - 6, 7.5f), hx, cy + 4, 7.5f, INK);
        } else {
            Text.drawCenteredX(g, Long.toString(on), hx, cy - 3, 14f, INK);
            Text.drawCenteredX(g, "ACTIVE", hx, cy + 11, 5f, MUTED);
        }
        hits.add(new Hit(hx - hubR, cy - hubR, hubR * 2, hubR * 2, -2, null));

        // gear
        if (gr > 1) {
            circle(g, gx, gy, gr, Colors.withAlpha(Colors.lerp(CardGuiStyle.back, 0xFFFFFFFF, gearHov ? .12f : 0f), glass ? .32f : .8f));
            if (client) ringLine(g, gx, gy, gr, Colors.withAlpha(accent, .9f));
            CardGuiIcons.draw(g, "sliders", gx - gr * .55f, gy - gr * .55f, gr * 1.1f, client ? Colors.lerp(accent, 0xFFFFFFFF, .35f) : INK);
            if (gearHov) Text.drawCenteredX(g, "Settings", gx, gy + gr + 7, 6f, INK);
            acts.add(new Act(gx - gr, gy - gr, gr * 2, gr * 2, false, (x, y) -> {
                boolean open = !client;
                closeSheet();
                client = open; scroll = scrollGoal = 0;
                if (open) profiles = GuiConfig.profiles();
                UiSounds.play(UiSounds.Sound.TAB);
            }));
        }

        // orbs: clear glass; the selected one gets an accent rim + halo, not a solid fill
        for (int i = 0; i < n; i++) {
            if (or[i] <= 1) continue;
            CardGui.Cat c = cats.get(i);
            boolean s = i == sel;
            int fill = Colors.withAlpha(Colors.lerp(CardGuiStyle.back, 0xFFFFFFFF, .06f * hover[i] + (s ? .06f : 0f)), glass ? .28f : .80f);
            if (s) for (int k = 3; k >= 1; k--) circle(g, ox[i], oy[i], or[i] + k * 2.5f, Colors.withAlpha(accent, .045f));
            circle(g, ox[i], oy[i], or[i], fill);
            if (s) ringLine(g, ox[i], oy[i], or[i], Colors.withAlpha(accent, .95f));
            float is = or[i] * .95f;
            int ic = s ? Colors.lerp(accent, 0xFFFFFFFF, .45f) : Colors.lerp(Colors.withAlpha(INK, .85f), 0xFFFFFFFF, hover[i]);
            String tex = CardGui.prestigeFor(c.label());
            if (!PrestigeIcons.draw(g, tex, ox[i] - is / 2, oy[i] - is / 2, is, ic)) CardGuiIcons.draw(g, c.icon(), ox[i] - is / 2, oy[i] - is / 2, is, ic);
            boolean any = false;
            for (Module m : c.source()) if (c.matches(m) && m.isEnabled()) { any = true; break; }
            if (any) circle(g, ox[i] + or[i] * .72f, oy[i] - or[i] * .72f, 2.5f, accent);
            float la = clamp01(.55f + hover[i] + (s ? 1 : 0)) * intro * lerp(1f, s ? 1f : .55f, e);
            Text.drawCenteredX(g, c.label(), ox[i], oy[i] + or[i] + 8, 6.5f, Colors.withAlpha(INK, la));
            hits.add(new Hit(ox[i] - or[i], oy[i] - or[i], or[i] * 2, or[i] * 2, i, null));
        }

        float wheelR = ringR + 95 + 20 * e;
        float wheelA = selT * (1 - panelT);
        if (wheelA > .01f) drawWheel(g, hx, cy, wheelR, wheelA, mouseX, mouseY, dt);
        if (panelT > .01f) drawPanel(g, hx + ringR + 70, cy, mouseX, mouseY, delta, glass);

        String hint = panelOpen() ? "esc close panel  ·  scroll"
                : wheelOpen() ? "click toggle  ·  right-click settings  ·  scroll  ·  esc back"
                : "pick an orb  ·  type to search  ·  gear for settings  ·  tab switch style";
        Text.drawCenteredX(g, hint, width / 2f, height - 14, 6f, Colors.withAlpha(MUTED, .8f * intro));
    }

    private void flushGlass(DrawContext g) {
        var ms = g.getMatrices(); ms.pushMatrix(); ms.identity();
        GlassRenderer.flush(g);
        ms.popMatrix();
    }

    private void drawWheel(DrawContext g, float hx, float cy, float rw, float e, int mx, int my, float dt) {
        int accent = CardGuiStyle.accent;
        if (items.isEmpty()) {
            Text.draw(g, query.length() > 0 ? "no modules match" : "nothing here yet", hx + rw, cy, 8f, Colors.withAlpha(MUTED, e));
            return;
        }
        int focusI = Math.round(wheel);
        String title = query.length() > 0 ? "Results" : sel >= 0 ? cats.get(sel).label() : "";
        Text.drawCentered(g, title, hx + rw - 4, cy - 132 - (1 - e) * 20, 11f, Colors.withAlpha(INK, e));
        Text.drawCentered(g, items.size() + " modules", hx + rw - 4 + Text.width(title, 11f) + 8, cy - 132 - (1 - e) * 20, 6f, Colors.withAlpha(MUTED, e));
        for (int i = 0; i < items.size(); i++) {
            float th = (i - wheel) * WHEEL_STEP;
            if (Math.abs(th) > WHEEL_SPAN) continue;
            Module m = items.get(i);
            float fade = (float) Math.pow(clamp01(1 - Math.abs(th) / WHEEL_SPAN), .8f) * e;
            float sc = 1 - Math.abs(th) * .2f;
            float x = hx + (float) Math.cos(th) * rw + (1 - e) * 30, y = cy + (float) Math.sin(th) * rw * 1.05f;
            float w = 170 * sc, h = 22 * sc;
            boolean hov = mx >= x && mx < x + w && my >= y - h / 2 && my < y + h / 2;
            float p = approach(pulse.getOrDefault(m, 0f), 0f, dt * 5f);
            pulse.put(m, p);
            boolean on = m.isEnabled(), f = i == focusI;
            int base = on ? Colors.withAlpha(accent, (.42f + .18f * (hov ? 1 : 0)) * fade)
                    : Colors.withAlpha(0xFFFFFFFF, (.07f + .07f * (hov ? 1 : 0) + (f ? .05f : 0)) * fade);
            float grow = p * 3;
            Draw.rect(g, x - grow / 2, y - h / 2 - grow / 2, w + grow, Math.round(h + grow), Math.round(h / 2), base);
            Rounded.outline(g, Math.round(x), Math.round(y - h / 2), Math.round(w), Math.round(h), h / 2, 1f,
                    Colors.withAlpha(f ? accent : 0xFFFFFFFF, (f ? .7f : .14f) * fade));
            circle(g, x + 11 * sc, y, 3.2f * sc, on ? Colors.withAlpha(0xFFFFFFFF, fade) : Colors.withAlpha(0xFFFFFFFF, .25f * fade));
            Text.drawCentered(g, Text.fit(m.getName(), w - 30 * sc, 7.5f * sc), x + 21 * sc, y, 7.5f * sc, Colors.withAlpha(INK, fade));
            if (f && m.getDescription() != null) {
                List<String> lines = Text.wrap(m.getDescription(), 170, 6f, 2);
                float ly = y + h / 2 + 9;
                for (String l : lines) { Text.draw(g, l, x + 8, ly, 6f, Colors.withAlpha(MUTED, fade)); ly += 8; }
            }
            if (e > .5f) hits.add(new Hit(x, y - h / 2, w, h, -1, m));
        }
    }

    // ---- glass panel (module settings / client settings) ---------------------------

    private void drawPanel(DrawContext g, float x0, float cy, int mx, int my, float delta, boolean glass) {
        float t = smooth(panelT);
        float w = Math.min(280, width - x0 - 16), h = Math.min(330, height - 50);
        float x = x0 + (1 - t) * 18, y = cy - h / 2;
        panel = new float[]{x, y, w, h};
        int r = 14, accent = CardGuiStyle.accent;
        if (glass) {
            GlassRenderer.queue(new GlassRenderer.Panel(x, y, w, h, r, .95f * t));
            flushGlass(g);
        }
        Draw.rect(g, x, y, w, Math.round(h), r, Colors.withAlpha(Colors.lerp(CardGuiStyle.back, 0xFF12040F, .35f), (glass ? .55f : .94f) * t));
        Rounded.outline(g, Math.round(x), Math.round(y), Math.round(w), Math.round(h), r, 1f, Colors.withAlpha(0xFFFFFFFF, .14f * t));

        // header
        float hy = y + 18;
        String title = client ? "Settings" : sheet != null ? sheet.getName() : "";
        Text.drawCentered(g, title, x + 14, hy, 10f, Colors.withAlpha(INK, t));
        float xc = x + w - 16;
        boolean xh = Math.abs(mx - xc) < 8 && Math.abs(my - hy) < 8;
        CardGuiIcons.draw(g, "close", xc - 4.5f, hy - 4.5f, 9, xh ? INK : MUTED);
        acts.add(new Act(xc - 8, hy - 8, 16, 16, false, (a, b) -> { closeSheet(); client = false; UiSounds.play(UiSounds.Sound.TAB); }));
        if (sheet != null) {
            // enable switch next to the title
            boolean on = sheet.isEnabled();
            float tx = xc - 46, ty = hy - 7;
            Draw.rect(g, tx, ty, 26, 14, 4, on ? accent : 0x33FFFFFF);
            Draw.rect(g, tx + (on ? 14 : 2), ty + 2, 10, 10, 3, 0xFFFFFFFF);
            acts.add(new Act(tx, ty, 26, 14, false, (a, b) -> {
                sheet.setEnabled(!sheet.isEnabled());
                UiSounds.play(sheet.isEnabled() ? UiSounds.Sound.TOGGLE_ON : UiSounds.Sound.TOGGLE_OFF);
            }));
        }
        float top = y + 34;
        if (client) {
            // tabs
            float tw = (w - 24) / TABS.length;
            for (int i = 0; i < TABS.length; i++) {
                float bx = x + 12 + i * tw;
                boolean s = i == tab;
                if (s) Draw.rect(g, bx + 2, top, tw - 4, 18, 6, Colors.withAlpha(accent, .55f));
                Text.drawCenteredX(g, TABS[i], bx + tw / 2, top + 9, 7f, s ? 0xFFFFFFFF : MUTED);
                final int idx = i;
                acts.add(new Act(bx, top, tw, 18, false, (a, b) -> {
                    tab = idx; scroll = scrollGoal = 0; nameFocus = false; bindingModule = null; bindingMenu = false;
                    if (idx == 2) profiles = GuiConfig.profiles();
                    UiSounds.play(UiSounds.Sound.CLICK);
                }));
            }
            top += 26;
        }
        g.fill(Math.round(x + 12), Math.round(top - 4), Math.round(x + w - 12), Math.round(top - 3), Colors.withAlpha(0xFFFFFFFF, .10f));
        float bottom = y + h - 10, cx = x + 16, cw = w - 32;
        final float fTop = top;
        List<Popup> popups = new ArrayList<>();
        Scissor.clip(g, Math.round(x), Math.round(top), Math.round(w), Math.round(bottom - top), () -> {
            float yy = fTop + 4 - scroll;
            if (sheet != null) yy = sheetRows(g, cx, cw, yy, fTop, bottom, mx, my, delta, popups);
            else if (client) yy = clientPage(g, cx, cw, yy, fTop, bottom, mx, my);
            scrollMax = Math.max(0, yy + scroll - bottom + 8);
        });
        scrollGoal = Math.max(0, Math.min(scrollMax, scrollGoal));
        if (scrollMax > 0) {
            float track = bottom - top, thumb = Math.max(16, track * track / (track + scrollMax));
            Draw.rect(g, x + w - 5, top + (track - thumb) * scroll / scrollMax, 2.5f, Math.round(thumb), 1, Colors.withAlpha(accent, .85f));
        }
        for (int i = popups.size() - 1; i >= 0; i--) popups.get(i).renderPopup(g, mx, my);
    }

    private float sheetRows(DrawContext g, float lx, float cw, float y, float top, float bottom, int mx, int my, float delta, List<Popup> popups) {
        float rx = lx + cw;
        String section = null;
        for (Row row : rows) {
            Control c = row.control();
            String want = c instanceof Keybind ? "Keybind" : "Settings";
            if (!want.equals(section)) { y = sectionLine(g, want, lx, cw, y); section = want; }
            float h = c instanceof Slider ? 36 : c instanceof Bool ? 24 : 26;
            boolean inView = y + h > top && y < bottom;
            c.visible = inView; c.active = inView;
            if (inView) {
                if (c instanceof Slider sl) {
                    Text.drawCentered(g, row.setting().getName(), lx, y + 8, 7.5f, INK);
                    String val = sl.shownValue();
                    Text.drawCentered(g, val, rx - Text.width(val, 7.5f), y + 8, 7.5f, INK);
                    c.setX(Math.round(lx)); c.setWidth(Math.round(cw)); c.setHeight(12); c.setY(Math.round(y + 18));
                } else if (c instanceof Popup) {
                    c.setX(Math.round(lx - 6)); c.setY(Math.round(y + 2)); c.setWidth(Math.round(cw + 12)); c.setHeight(Math.round(h - 4));
                } else {
                    Text.drawCentered(g, row.setting().getName(), lx, y + h / 2, 7.5f, INK);
                    int ww = c instanceof Bool ? 30 : 64, hh = c instanceof Bool ? 16 : 18;
                    c.setWidth(ww); c.setHeight(hh); c.setX(Math.round(rx - ww)); c.setY(Math.round(y + (h - hh) / 2));
                }
                c.draw(g, mx, my, delta);
                if (c instanceof Popup p) popups.add(p);
            }
            y += h;
        }
        return y;
    }

    private float clientPage(DrawContext g, float x, float w, float y, float top, float bottom, int mx, int my) {
        int accent = CardGuiStyle.accent;
        switch (tab) {
            case 0 -> {
                y = sectionLine(g, "Colours", x, w, y);
                String[] names = {"Main", "Background", "Back"};
                float cw = (w - 8) / 3;
                for (int i = 0; i < 3; i++) {
                    float bx = x + i * (cw + 4);
                    boolean s = i == colorSlot;
                    Draw.rect(g, bx, y, cw, 22, 6, s ? 0x26FFFFFF : 0x10FFFFFF);
                    if (s) Rounded.outline(g, Math.round(bx), Math.round(y), Math.round(cw), 22, 6, 1f, Colors.withAlpha(accent, .8f));
                    Draw.rect(g, bx + 6, y + 6, 10, 10, 3, slotColor(i));
                    Text.drawCentered(g, names[i], bx + 20, y + 11, 6.5f, s ? INK : MUTED);
                    final int idx = i;
                    acts.add(new Act(bx, y, cw, 22, false, (a, b) -> { colorSlot = idx; UiSounds.play(UiSounds.Sound.CLICK); }));
                }
                y += 30;
                // presets
                float sw = (w - (PRESETS.length - 1) * 3) / PRESETS.length;
                for (int i = 0; i < PRESETS.length; i++) {
                    float bx = x + i * (sw + 3);
                    int col = colorSlot == 0 ? PRESETS[i] : colorSlot == 1 ? Colors.lerp(PRESETS[i], 0xFF000000, .35f) : Colors.lerp(PRESETS[i], 0xFF000000, .78f);
                    Draw.rect(g, bx, y, sw, Math.round(sw), 4, col);
                    if ((slotColor(colorSlot) & 0xFFFFFF) == (col & 0xFFFFFF)) Rounded.outline(g, Math.round(bx), Math.round(y), Math.round(sw), Math.round(sw), 4, 1f, 0xFFFFFFFF);
                    acts.add(new Act(bx, y, sw, sw, false, (a, b) -> { setSlotColor(colorSlot, col); UiSounds.play(UiSounds.Sound.CLICK); }));
                }
                y += sw + 10;
                // hue + brightness
                float[] hsv = java.awt.Color.RGBtoHSB((slotColor(colorSlot) >> 16) & 255, (slotColor(colorSlot) >> 8) & 255, slotColor(colorSlot) & 255, null);
                Text.drawCentered(g, "Hue", x, y + 4, 7f, MUTED); y += 10;
                Draw.hueBar(g, Math.round(x), Math.round(y), Math.round(w), 8, 4);
                Draw.rect(g, x + hsv[0] * w - 3, y - 2, 6, 12, 2, 0xFFFFFFFF);
                final float hy = y;
                acts.add(new Act(x - 3, hy - 4, w + 6, 16, true, (a, b) -> setSlotColor(colorSlot,
                        0xFF000000 | java.awt.Color.HSBtoRGB(clamp01((float) (a - x) / w), Math.max(.05f, hsv[1]), Math.max(.05f, hsv[2])))));
                y += 18;
                y = slider(g, "Brightness", x, w, y, 0, 100, Math.round(hsv[2] * 100), v -> setSlotColor(colorSlot,
                        0xFF000000 | java.awt.Color.HSBtoRGB(hsv[0], hsv[1], Math.max(.02f, v / 100f))));
                y = slider(g, "Saturation", x, w, y, 0, 100, Math.round(hsv[1] * 100), v -> setSlotColor(colorSlot,
                        0xFF000000 | java.awt.Color.HSBtoRGB(hsv[0], v / 100f, hsv[2])));
                y = sectionLine(g, "Glass", x, w, y + 4);
                y = toggle(g, "Liquid glass", x, w, y, CardGuiStyle.glass, () -> CardGuiStyle.glass = !CardGuiStyle.glass);
                y = slider(g, "Blur", x, w, y, 0, 30, CardGuiStyle.blur, v -> CardGuiStyle.blur = v);
                y = toggle(g, "Menu sounds", x, w, y, UiSounds.enabled, () -> UiSounds.enabled = !UiSounds.enabled);
            }
            case 1 -> {
                y = sectionLine(g, "Open menu", x, w, y);
                String key = bindingMenu ? "press a key..." : dev.koifih.client.input.Keybinds.panelKey().getLocalizedText().getString();
                y = keyRow(g, "Menu key", key, x, w, y, () -> { bindingMenu = true; bindingModule = null; });
                y = sectionLine(g, "Modules", x, w, y + 2);
                Text.drawCentered(g, "click a key to rebind  ·  esc cancels  ·  backspace clears", x, y + 3, 5.5f, MUTED);
                y += 12;
                for (Module m : allModules()) {
                    if (query.length() > 0 && !plain(m.getName()).contains(query.toString())) continue;
                    if (y + 22 < top - 30 || y > bottom + 30) { y += 22; continue; }
                    String k = m.getName().equals(bindingModule) ? "press a key..." : ModuleBinds.name(ModuleBinds.get(m.getName()));
                    y = keyRow(g, m.getName(), k.isEmpty() ? "none" : k, x, w, y, () -> { bindingModule = m.getName(); bindingMenu = false; });
                }
            }
            case 2 -> {
                y = sectionLine(g, "Save profile", x, w, y);
                float fw = w - 62;
                Draw.rect(g, x, y, fw, 20, 6, nameFocus ? 0x24FFFFFF : 0x14FFFFFF);
                if (nameFocus) Rounded.outline(g, Math.round(x), Math.round(y), Math.round(fw), 20, 6, 1f, Colors.withAlpha(accent, .8f));
                String shown = profileName.length() == 0 && !nameFocus ? "profile name..." : profileName + (nameFocus && (caret % 1f) < .5f ? "_" : "");
                Text.drawCentered(g, Text.fit(shown, fw - 12, 7f), x + 7, y + 10, 7f, profileName.length() == 0 && !nameFocus ? MUTED : INK);
                acts.add(new Act(x, y, fw, 20, false, (a, b) -> nameFocus = true));
                button(g, "Save", x + fw + 6, y, 56, false, this::saveProfile);
                y += 28;
                y = sectionLine(g, "Profiles", x, w, y);
                if (profiles.isEmpty()) { Text.drawCentered(g, "no saved profiles yet", x, y + 6, 6.5f, MUTED); y += 16; }
                for (String name : profiles) {
                    Draw.rect(g, x, y, w, 22, 6, 0x12FFFFFF);
                    Text.drawCentered(g, Text.fit(name, w - 120, 7.5f), x + 8, y + 11, 7.5f, INK);
                    button(g, "Load", x + w - 98, y + 3, 50, false, () -> {
                        boolean ok = GuiConfig.loadProfile(name);
                        notice = ok ? "loaded " + name : "load failed";
                        UiSounds.play(ok ? UiSounds.Sound.SUCCESS : UiSounds.Sound.CLICK);
                    });
                    button(g, name.equals(pendingDelete) ? "Sure?" : "Delete", x + w - 44, y + 3, 42, true, () -> {
                        if (name.equals(pendingDelete)) { GuiConfig.deleteProfile(name); profiles = GuiConfig.profiles(); pendingDelete = null; notice = "deleted " + name; }
                        else pendingDelete = name;
                    });
                    y += 26;
                }
                y += 4;
                button(g, "Open profiles folder", x, y, w, false, GuiConfig::openProfileFolder);
                y += 26;
                if (!notice.isEmpty()) { Text.drawCentered(g, notice, x, y + 4, 6.5f, MUTED); y += 14; }
            }
            default -> {
                y = sectionLine(g, "GUI style", x, w, y);
                FrameManager.Style[] styles = {FrameManager.Style.WATER, FrameManager.Style.ORBIT, FrameManager.Style.CARD, FrameManager.Style.DROPDOWN};
                String[] labels = {"water", "Orbit", "Cards", "Dropdown"};
                float bw = (w - 9) / 4;
                for (int i = 0; i < 4; i++) {
                    FrameManager.Style st = styles[i];
                    boolean s = FrameManager.style == st;
                    float bx = x + i * (bw + 3);
                    Draw.rect(g, bx, y, bw, 22, 6, s ? Colors.withAlpha(accent, .6f) : 0x14FFFFFF);
                    Text.drawCenteredX(g, labels[i], bx + bw / 2, y + 11, 7f, s ? 0xFFFFFFFF : INK);
                    acts.add(new Act(bx, y, bw, 22, false, (a, b) -> switchStyle(st)));
                }
                y += 30;
                y = sectionLine(g, "Motion", x, w, y);
                y = toggle(g, "Animations", x, w, y, CardGuiStyle.motion, () -> CardGuiStyle.motion = !CardGuiStyle.motion);
                y = slider(g, "GUI scale (%)", x, w, y, 70, 120, CardGuiStyle.size, v -> CardGuiStyle.size = v);
            }
        }
        return y;
    }

    private int slotColor(int i) { return i == 0 ? CardGuiStyle.accent : i == 1 ? CardGuiStyle.background : CardGuiStyle.back; }
    private void setSlotColor(int i, int c) {
        if (i == 0) CardGuiStyle.accent = c; else if (i == 1) CardGuiStyle.background = c; else CardGuiStyle.back = c;
        GuiConfig.markDirty();
    }

    private void saveProfile() {
        boolean ok = GuiConfig.saveProfile(profileName.toString());
        notice = ok ? "saved " + profileName : "enter a valid profile name";
        if (ok) { profiles = GuiConfig.profiles(); nameFocus = false; }
        UiSounds.play(ok ? UiSounds.Sound.SUCCESS : UiSounds.Sound.CLICK);
    }

    private void switchStyle(FrameManager.Style st) {
        FrameManager.style = st;
        GuiConfig.save();
        UiSounds.play(UiSounds.Sound.CLICK);
        MinecraftClient mc = MinecraftClient.getInstance();
        switch (st) {
            case CARD -> mc.setScreen(new CardGui());
            case DROPDOWN -> mc.setScreen(new DropdownGui());
            case PANEL -> mc.setScreen(new dev.koifih.client.ui.wing.Wing());
            case ORBIT -> {}
            case WATER -> mc.setScreen(new WaterGui());
        }
    }

    // ---- small immediate-mode widgets ---------------------------------------------------

    private float sectionLine(DrawContext g, String label, float x, float w, float y) {
        float cx = x + w / 2, lw = Text.width(label, 6.5f);
        int line = Colors.withAlpha(0xFFFFFFFF, .12f), ly = Math.round(y + 6);
        g.fill(Math.round(x), ly, Math.round(cx - lw / 2 - 7), ly + 1, line);
        g.fill(Math.round(cx + lw / 2 + 7), ly, Math.round(x + w), ly + 1, line);
        Text.drawCenteredX(g, label, cx, y + 6, 6.5f, MUTED);
        return y + 16;
    }

    private float slider(DrawContext g, String label, float x, float w, float y, int min, int max, int value, java.util.function.IntConsumer set) {
        Text.drawCentered(g, label, x, y + 6, 7.5f, INK);
        String v = Integer.toString(value);
        Text.drawCentered(g, v, x + w - Text.width(v, 7.5f), y + 6, 7.5f, INK);
        float ty = y + 17, t = clamp01((value - min) / (float) (max - min));
        Draw.rect(g, x, ty, w, 3, 1, 0x2EFFFFFF);
        if (t > 0) Draw.rect(g, x, ty, Math.max(3, w * t), 3, 1, CardGuiStyle.accent);
        float kx = Math.max(x, Math.min(x + w - 8, x + w * t - 4));
        Draw.rect(g, kx, ty - 2.5f + .6f, 8, 8, 2, 0x40000000);
        Draw.rect(g, kx, ty - 2.5f, 8, 8, 2, 0xFFFFFFFF);
        acts.add(new Act(x - 4, ty - 6, w + 8, 15, true, (a, b) -> {
            set.accept(min + Math.round(clamp01((float) (a - x) / w) * (max - min)));
            GuiConfig.markDirty();
        }));
        return y + 30;
    }

    private float toggle(DrawContext g, String label, float x, float w, float y, boolean value, Runnable change) {
        Text.drawCentered(g, label, x, y + 9, 7.5f, INK);
        float tx = x + w - 28;
        Draw.rect(g, tx, y + 2, 28, 15, 4, value ? CardGuiStyle.accent : 0x2EFFFFFF);
        Draw.rect(g, tx + (value ? 15 : 2.5f), y + 4.5f, 10, 10, 3, value ? 0xFFFFFFFF : 0xFFDCD6E2);
        acts.add(new Act(x, y, w, 20, false, (a, b) -> {
            change.run(); GuiConfig.markDirty();
            UiSounds.play(UiSounds.Sound.CLICK);
        }));
        return y + 24;
    }

    private float keyRow(DrawContext g, String label, String key, float x, float w, float y, Runnable start) {
        Text.drawCentered(g, Text.fit(label, w - 90, 7.5f), x, y + 10, 7.5f, INK);
        float bw = 76, bx = x + w - bw;
        boolean waiting = key.startsWith("press");
        Draw.rect(g, bx, y + 2, bw, 17, 5, waiting ? Colors.withAlpha(CardGuiStyle.accent, .55f) : 0x80140612);
        Rounded.outline(g, Math.round(bx), Math.round(y + 2), Math.round(bw), 17, 5, 1f, 0x18FFFFFF);
        Text.drawCenteredX(g, Text.fit(key, bw - 8, 6.5f), bx + bw / 2, y + 10.5f, 6.5f, waiting ? 0xFFFFFFFF : INK);
        acts.add(new Act(bx, y + 2, bw, 17, false, (a, b) -> { start.run(); UiSounds.play(UiSounds.Sound.CLICK); }));
        return y + 22;
    }

    private void button(DrawContext g, String label, float x, float y, float w, boolean danger, Runnable run) {
        Draw.rect(g, x, y, w, 18, 6, danger ? 0x804B2330 : Colors.withAlpha(CardGuiStyle.accent, .6f));
        Text.drawCenteredX(g, label, x + w / 2, y + 9, 7f, 0xFFFFFFFF);
        acts.add(new Act(x, y, w, 18, false, (a, b) -> run.run()));
    }

    // ---- module settings sheet ------------------------------------------------------------

    private void openSheet(Module m) {
        closeSheet();
        client = false;
        if (!(m instanceof Feature f)) return;
        sheet = f;
        scroll = scrollGoal = 0;
        int bottomLimit = height - 10;
        String mod = m.getName();
        Keybind bind = new Keybind(0, 0, 64, 18, 1f, net.minecraft.text.Text.literal("Bind"),
                () -> {
                    int code = ModuleBinds.get(mod);
                    if (code == -1 || code == 0) return InputUtil.UNKNOWN_KEY;
                    if ((code & 0x80000000) != 0) return InputUtil.Type.MOUSE.createFromCode(code & 0xFF);
                    return InputUtil.Type.KEYSYM.createFromCode(code);
                },
                key -> {
                    if (key == null || key.equals(InputUtil.UNKNOWN_KEY)) ModuleBinds.set(mod, -1);
                    else if (key.getCategory() == InputUtil.Type.MOUSE) ModuleBinds.set(mod, 0x80000000 | key.getCode());
                    else ModuleBinds.set(mod, key.getCode());
                });
        bind.setClearable(true);
        controls.add(bind);
        rows.add(new Row(new KeybindSetting("Bind", -1), bind));
        for (Setting st : f.getSettings()) {
            Control c = CardGui.create(st, 1f, bottomLimit);
            if (c == null) continue;
            if (c instanceof Bool b) b.cardStyle = true;
            if (c instanceof Slider sl) sl.cardStyle = true;
            if (c instanceof dev.koifih.client.ui.component.popup.RowPopup rp) rp.cardStyle = true;
            controls.add(c);
            rows.add(new Row(st, c));
        }
        UiSounds.play(UiSounds.Sound.POPUP);
    }

    private void closeSheet() {
        focus = null;
        for (Control c : controls) { c.dismiss(); c.visible = false; }
        controls.clear();
        rows.clear();
        sheet = null;
    }

    private boolean inPanel(double mx, double my) {
        return panelOpen() && mx >= panel[0] && mx < panel[0] + panel[2] && my >= panel[1] && my < panel[1] + panel[3];
    }

    // ---- input -------------------------------------------------------------------

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (failed) return false;
        try { return click(click, doubled); } catch (Throwable t) { fail(t); return true; }
    }

    private boolean click(Click click, boolean doubled) {
        double mx = click.x(), my = click.y();
        int button = click.button();
        if (bindingModule != null) {
            if (button != 0 || !inPanel(mx, my)) { ModuleBinds.set(bindingModule, 0x80000000 | button); bindingModule = null; return true; }
            bindingModule = null;
        }
        if (sheet != null) {
            for (Control c : controls) if (c instanceof Keybind k && k.captureMouse(click)) return true;
            for (Control c : controls) if (c instanceof Popup p && p.isOpen() && p.mouseClicked(click, doubled)) return true;
            if (inPanel(mx, my)) for (Control c : controls) {
                if (c.active && c.visible && c.isMouseOver(mx, my)) { focus = c; c.onClick(click, doubled); return true; }
            }
        }
        nameFocus = false;
        for (int i = acts.size() - 1; i >= 0; i--) {
            Act a = acts.get(i);
            if (a.in(mx, my) && button == 0) { a.fn().accept(mx, my); if (a.drag()) dragging = a; return true; }
        }
        if (inPanel(mx, my)) return true;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (!h.in(mx, my)) continue;
            if (h.module() != null) {
                Module m = h.module();
                if (button == 1) openSheet(m);
                else {
                    m.setEnabled(!m.isEnabled());
                    pulse.put(m, 1f);
                    wheelGoal = items.indexOf(m);
                    UiSounds.play(m.isEnabled() ? UiSounds.Sound.TOGGLE_ON : UiSounds.Sound.TOGGLE_OFF);
                }
                return true;
            }
            if (h.orb() == -2) { sel = -1; query.setLength(0); closeSheet(); client = false; UiSounds.play(UiSounds.Sound.CLICK); return true; }
            if (h.orb() >= 0) {
                closeSheet(); client = false;
                sel = sel == h.orb() ? -1 : h.orb();
                query.setLength(0);
                wheel = wheelGoal = 0;
                UiSounds.play(UiSounds.Sound.TAB);
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        try {
            for (Control c : controls) if (c.active && c.visible) c.onDrag(click, dx, dy);
            if (dragging != null) dragging.fn().accept(click.x(), click.y());
        } catch (Throwable t) { fail(t); }
        return true;
    }

    @Override
    public boolean mouseReleased(Click click) {
        try { for (Control c : controls) if (c.active && c.visible) c.onRelease(click); } catch (Throwable t) { fail(t); }
        dragging = null;
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double dx, double dy) {
        for (Control c : controls) if (c instanceof Popup p && p.isOpen() && p.scrolledWidget(x, y, dy)) return true;
        if (inPanel(x, y)) { scrollGoal -= (float) dy * 22; return true; }
        if (!wheelOpen() || items.isEmpty() || panelOpen()) return false;
        wheelGoal = Math.max(0, Math.min(items.size() - 1, wheelGoal - (float) Math.signum(dy)));
        UiSounds.play(UiSounds.Sound.HOVER);
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput event) {
        int k = event.key();
        if (bindingMenu) {
            if (k != GLFW.GLFW_KEY_ESCAPE) dev.koifih.client.input.Keybinds.setOpenKeyCode(k);
            bindingMenu = false; GuiConfig.markDirty(); swallowChar = true;
            UiSounds.play(UiSounds.Sound.SUCCESS);
            return true;
        }
        if (bindingModule != null) {
            if (k == GLFW.GLFW_KEY_BACKSPACE || k == GLFW.GLFW_KEY_DELETE) ModuleBinds.set(bindingModule, -1);
            else if (k != GLFW.GLFW_KEY_ESCAPE) ModuleBinds.set(bindingModule, k);
            bindingModule = null; swallowChar = true;
            UiSounds.play(UiSounds.Sound.SUCCESS);
            return true;
        }
        if (focus != null && focus.capturesInput() && focus.keyPressed(event)) { swallowChar = true; return true; }
        for (Control c : controls) if (c.capturesInput() && c.keyPressed(event)) { swallowChar = true; return true; }
        swallowChar = false;
        if (nameFocus) {
            if (k == GLFW.GLFW_KEY_BACKSPACE && profileName.length() > 0) profileName.setLength(profileName.length() - 1);
            else if (k == GLFW.GLFW_KEY_ENTER || k == GLFW.GLFW_KEY_KP_ENTER) saveProfile();
            else if (k == GLFW.GLFW_KEY_ESCAPE) nameFocus = false;
            return true;
        }
        if (k == GLFW.GLFW_KEY_ESCAPE) {
            if (panelOpen()) { closeSheet(); client = false; }
            else if (query.length() > 0) query.setLength(0);
            else if (sel >= 0) sel = -1;
            else shut();
            return true;
        }
        // the menu key itself is handled by Keys (it closes our GUIs); reacting here too would reopen it
        if (dev.koifih.client.input.Keybinds.matches(event)) return true;
        if (k == GLFW.GLFW_KEY_BACKSPACE && query.length() > 0) { query.setLength(query.length() - 1); wheelGoal = 0; return true; }
        if (panelOpen()) return super.keyPressed(event);
        if (k == GLFW.GLFW_KEY_DOWN) { wheelGoal = Math.min(Math.max(0, items.size() - 1), wheelGoal + 1); return true; }
        if (k == GLFW.GLFW_KEY_UP) { wheelGoal = Math.max(0, wheelGoal - 1); return true; }
        if ((k == GLFW.GLFW_KEY_ENTER || k == GLFW.GLFW_KEY_KP_ENTER) && !items.isEmpty()) {
            Module m = items.get(Math.max(0, Math.min(items.size() - 1, Math.round(wheel))));
            m.setEnabled(!m.isEnabled()); pulse.put(m, 1f);
            UiSounds.play(m.isEnabled() ? UiSounds.Sound.TOGGLE_ON : UiSounds.Sound.TOGGLE_OFF);
            return true;
        }
        if (k == GLFW.GLFW_KEY_LEFT || k == GLFW.GLFW_KEY_RIGHT) {
            int n = cats.size();
            sel = sel < 0 ? 0 : (sel + (k == GLFW.GLFW_KEY_RIGHT ? 1 : n - 1)) % n;
            query.setLength(0); wheel = wheelGoal = 0;
            UiSounds.play(UiSounds.Sound.TAB);
            return true;
        }
        if (k == GLFW.GLFW_KEY_TAB) { switchStyle(FrameManager.Style.DROPDOWN); return true; }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharInput event) {
        if (swallowChar) { swallowChar = false; return true; }   // that key was just used as a bind
        if (focus != null && focus.capturesInput()) return focus.charTyped(event);
        for (Control c : controls) if (c.capturesInput() && c.charTyped(event)) return true;
        String s = event.asString();
        if (s.isEmpty()) return false;
        char c = s.charAt(0);
        if (nameFocus) {
            if (profileName.length() < 32 && (Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == ' ')) profileName.append(c);
            return true;
        }
        if (bindingMenu || bindingModule != null) return true;
        if (sheet != null) return false;
        if (query.length() >= 24 || (!Character.isLetterOrDigit(c) && c != ' ')) return false;
        if (query.length() == 0 && c == ' ') return false;
        query.append(Character.toLowerCase(c));
        wheel = wheelGoal = 0; scroll = scrollGoal = 0;
        return true;
    }

    /** Close right away (no fade): the screen must be gone before the next tick so the open key works again. */
    private void shut() {
        UiSounds.play(UiSounds.Sound.CLOSE);
        UtilityMod.LOGGER.info("Orbit GUI closed");
        MinecraftClient.getInstance().setScreen(null);
    }

    @Override
    public void close() { shut(); }

    // ---- drawing helpers ----------------------------------------------------------

    private static void circle(DrawContext g, float cx, float cy, float r, int col) {
        if (r <= .3f) return;
        int d = Math.max(1, Math.round(r * 2));
        Draw.rect(g, cx - r, cy - r, r * 2, d, Math.max(1, Math.round(r)), col);
    }

    private static void ringLine(DrawContext g, float cx, float cy, float r, int col) {
        Rounded.outline(g, Math.round(cx - r), Math.round(cy - r), Math.round(r * 2), Math.round(r * 2), r, 1.3f, col);
    }

    private static void ring(DrawContext g, float cx, float cy, float r, int dots, float phase, int col) {
        for (int i = 0; i < dots; i++) {
            if (i % 4 == 3) continue;
            double a = phase + i * Math.PI * 2 / dots;
            circle(g, cx + (float) Math.cos(a) * r, cy + (float) Math.sin(a) * r, 1f, col);
        }
    }

    private static void dotted(DrawContext g, float x0, float y0, float x1, float y1, float skipA, float skipB, int col, float flow) {
        float len = dist(x0, y0, x1, y1);
        if (len <= skipA + skipB) return;
        float dx = (x1 - x0) / len, dy = (y1 - y0) / len;
        for (float d = skipA + (flow % 6f); d < len - skipB; d += 6f)
            Draw.rect(g, x0 + dx * d - .6f, y0 + dy * d - .6f, 1.2f, 1, 0, col);
    }

    private static float dist(float ax, float ay, float bx, float by) { return (float) Math.hypot(ax - bx, ay - by); }
    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
    private static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }
    private static float smooth(float t) { t = clamp01(t); return t * t * (3 - 2 * t); }
    private static float approach(float v, float goal, float k) { return v + (goal - v) * Math.min(1f, k); }
    private static float easeOut(float t) { t = clamp01(t); float u = 1 - t; return 1 - u * u * u * u; }
    private static float easeOutBack(float t) {
        t = clamp01(t);
        float c1 = 1.70158f, c3 = c1 + 1;
        return 1 + c3 * (float) Math.pow(t - 1, 3) + c1 * (float) Math.pow(t - 1, 2);
    }
}
