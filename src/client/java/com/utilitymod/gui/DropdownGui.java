package com.utilitymod.gui;

import com.utilitymod.core.Module;
import com.utilitymod.core.ModuleManager;
import com.utilitymod.anubis.AnubisBridge;
import com.utilitymod.anubis.module.ClientModule;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.settings.Setting;
import com.utilitymod.echo.features.settings.impl.BoolSetting;
import com.utilitymod.echo.features.settings.impl.ColorSetting;
import com.utilitymod.echo.features.settings.impl.FloatSetting;
import com.utilitymod.echo.features.settings.impl.IntSetting;
import com.utilitymod.echo.features.settings.impl.KeybindSetting;
import com.utilitymod.echo.features.settings.impl.ModeSetting;
import com.utilitymod.echo.hud.HudManager;
import com.utilitymod.echo.render.GlassRenderer;
import dev.koifih.client.render.Draw;
import dev.koifih.client.render.Text;
import dev.koifih.client.ui.Theme;
import dev.koifih.client.util.Colors;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Classic dropdown wing: one draggable liquid-glass column per category, enabled
 * modules as accent pills, right-click a module to expand its settings inline.
 * Left-click toggles / edits, right-click a header collapses the column.
 */
public final class DropdownGui extends Screen {
    private static final int W = 122, HEADER = 20, ROW = 20, SLIDER_ROW = 26, PAD = 3, RADIUS = 9;
    private static final int[] COLORS = {0xFFFFFF, 0xFF5555, 0xFFAA00, 0xFFFF55, 0x55FF55, 0x55FFFF, 0x9FD3FF,
            0x5555FF, 0xAA55FF, 0xFF55FF, 0xA8B4E8};

    /** Column positions/collapse survive reopening the GUI. */
    private static final Map<String, float[]> POS = new HashMap<>();
    private static final Set<String> COLLAPSED = new HashSet<>();
    private static final Set<Module> EXPANDED = new HashSet<>();

    private record Column(String id, String title, List<Module> modules) {}
    private record Hit(float x, float y, float w, float h, Runnable left, Runnable right, Consumer<Float> drag) {}

    private final List<Column> columns = new ArrayList<>();
    private final Map<String, Float> heights = new HashMap<>();
    private final Map<Object, Float> anim = new HashMap<>();
    private final List<Hit> hits = new ArrayList<>();
    private String draggingColumn;
    private float grabX, grabY;
    private Hit draggingSlider;
    private KeybindSetting listening;
    private final long opened = System.currentTimeMillis();

    private enum Tab {
        MODULES(LT.L1), HUD(LT.L2), FRIENDS(LT.L3), THEME(LT.L4), CONFIG(LT.L5), DONUT(LT.L6),
        WAYPOINTS(LT.L7);
        final String label;
        Tab(String label) { this.label = label; }
    }
    private static Tab tab = Tab.MODULES;
    private static final StringBuilder search = new StringBuilder();
    private boolean searchFocused;
    private static final int TAB_W = 62, TAB_H = 24, BAR_Y = 6, SEARCH_W = 200, SEARCH_H = 18, SEARCH_Y = 36, TOP = 62;
    private static final int TAB_BLUE = 0xFF3B82F6;
    private static final int[] ACCENTS = {0xA8B4E8, 0x3B82F6, 0x8B5CF6, 0xEC4899, 0xF43F5E, 0xF59E0B, 0x22C55E, 0x14B8A6, 0xFFFFFF};

    private record Item(String label, boolean on, Runnable action, int swatch,
                        float min, float max, float step, java.util.function.DoubleSupplier get,
                        java.util.function.DoubleConsumer set, Runnable right) {
        Item(String label, boolean on, Runnable action) { this(label, on, action, 0); }
        Item(String label, boolean on, Runnable action, int swatch) { this(label, on, action, swatch, 0, 0, 0, null, null, null); }

        static Item slider(String label, float min, float max, float step,
                           java.util.function.DoubleSupplier get, java.util.function.DoubleConsumer set) {
            return new Item(label, false, null, -2, min, max, step, get, set, null);
        }

        /** Small uppercase section title with a divider (not clickable). */
        static Item header(String title) {
            return new Item(title, false, null, -5, 0, 0, 0, null, null, null);
        }

        /** Row: left-click load, right-click delete, plus an inline "Copy" pill (stored in set). */
        static Item withCopy(String label, Runnable load, Runnable delete, Runnable copy) {
            return new Item(label, false, load, -7, 0, 0, 0, null, v -> copy.run(), delete);
        }

        /** Two half-width buttons in one row. */
        static Item pair(String left, Runnable leftAction, String right, Runnable rightAction) {
            return new Item(left + LT.L8 + right, false, leftAction, -6, 0, 0, 0, null, null, rightAction);
        }

        /** Row with separate left / right click actions. */
        static Item two(String label, boolean on, Runnable left, Runnable right) {
            return new Item(label, on, left, 0, 0, 0, 0, null, null, right);
        }
    }

    private float barX() { return (vw() - barW()) / 2f; }
    private float barW() { return TAB_W * Tab.values().length + 8; }

    private final Map<String, List<Module>> filteredCache = new HashMap<>();
    private final Map<AnubisBridge.Group, List<ClientModule>> groupFilterCache = new HashMap<>();
    private final Map<ClientModule, List<com.utilitymod.anubis.module.setting.Setting>> groupSettingsCache = new HashMap<>();

    private List<Module> filtered(Column c) {
        String q = search.toString().trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return c.modules();
        return filteredCache.computeIfAbsent(c.id(), k -> filter(c, q));
    }

    private static List<Module> filter(Column c, String q) {
        List<Module> out = new ArrayList<>();
        for (Module m : c.modules()) if (m.getName().toLowerCase(Locale.ROOT).contains(q)) out.add(m);
        return out;
    }

    private boolean columnShown(Column c) {
        if (c.id().equals(LT.L9)) return search.toString().isBlank();
        return !filtered(c).isEmpty();
    }

    public static DropdownGui openSection(String section) {
        tab = Tab.valueOf(section);
        search.setLength(0);
        return new DropdownGui();
    }

    public DropdownGui() {
        super(net.minecraft.text.Text.literal(LT.L10));
        for (Category c : Category.values()) {
            if (c == Category.DONUT) continue; // has its own tab
            List<Module> mods = new ArrayList<>();
            for (Module m : ModuleManager.all()) if (m.getCategory() == c) mods.add(m);
            mods.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            columns.add(new Column(c.name(), c == Category.VISUAL ? LT.L11 : c.getLabel(), mods));
        }
        columns.add(new Column(LT.L9, LT.L12, List.of()));
    }

    /** Draw scale: looks like GUI scale 2 regardless of the game's GUI scale. */
    private float s() {
        double f = client != null ? client.getWindow().getScaleFactor() : 2;
        return (float) Math.max(0.4, Math.min(1.0, 2.0 / f));
    }

    private float vw() { return width / s(); }
    private float vh() { return height / s(); }

    @Override
    protected void init() {
        // lay out columns left to right, wrapping, and keep every column on screen
        float x = 12, y = TOP;
        for (Column c : columns) {
            float[] p = POS.get(c.id());
            if (p == null) {
                if (x + W > vw() - 4) {
                    x = 12;
                    y += 260;
                }
                POS.put(c.id(), new float[] {x, y});
                x += W + 8;
            } else {
                p[0] = Math.max(0, Math.min(vw() - W, p[0]));
                p[1] = Math.max(0, Math.min(vh() - HEADER, p[1]));
            }
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    /** Reusable composite key holders: the animation map keys are built once per object/channel,
     *  not once per frame (per-frame string churn showed up as steady GC pressure while the GUI
     *  is open). Channels: 0=x,1=on,2=hv,3=ax,4=aon,5=ahv,6=h(column),7=ah(group). */
    private static final class K { final String[] vals = new String[8]; }
    private final java.util.IdentityHashMap<Object, K> keyMemo = new java.util.IdentityHashMap<>();

    private String kObj(Object scope, int ch, String prefix, String suffix) {
        K a = keyMemo.computeIfAbsent(scope, x -> new K());
        String s = a.vals[ch];
        if (s == null) { s = suffix == null ? prefix : prefix + suffix; a.vals[ch] = s; }
        return s;
    }

    /** Label-keyed memo for page items whose records are rebuilt each frame. */
    private static final java.util.Map<String, String> LABEL_KEYS = new java.util.concurrent.ConcurrentHashMap<>();
    private String kLabel(String prefix, String label) {
        return LABEL_KEYS.computeIfAbsent(label, l -> prefix + l);
    }

    private float approach(Object key, float target, float def) {
        float v = anim.getOrDefault(key, def);
        v += (target - v) * 0.3f;
        if (Math.abs(target - v) < 0.002f) v = target;
        anim.put(key, v);
        return v;
    }

    // ---- content height --------------------------------------------------------------------

    private final Map<Module, List<Setting>> settingsCache = new HashMap<>();

    private List<Setting> visibleSettings(Module m) {
        return settingsCache.computeIfAbsent(m, this::computeVisibleSettings);
    }

    private List<Setting> computeVisibleSettings(Module m) {
        List<Setting> out = new ArrayList<>();
        if (m instanceof Feature f) {
            for (Setting s : f.getSettings()) {
                if (!s.isVisible()) continue;
                if (s instanceof BoolSetting || s instanceof FloatSetting || s instanceof IntSetting
                        || s instanceof ModeSetting || s instanceof KeybindSetting || s instanceof ColorSetting
                        || s instanceof com.utilitymod.echo.features.settings.impl.MultiModeSetting
                        || s instanceof com.utilitymod.echo.features.settings.impl.RegistryPickerSetting) out.add(s);
            }
        }
        return out;
    }

    private static int settingHeight(Setting s) {
        return s instanceof FloatSetting || s instanceof IntSetting ? SLIDER_ROW : ROW;
    }

    private float contentHeight(Column c) {
        if (COLLAPSED.contains(c.id())) return 0;
        if (c.id().equals(LT.L9)) return 4 * (ROW + 2) + PAD;
        float h = 0;
        for (Module m : filtered(c)) {
            h += ROW + 2;
            float open = anim.getOrDefault(kObj(m, 0, LT.L13, m.getName()), 0f);
            if (open > 0.001f) {
                float sh = 0;
                for (Setting s : visibleSettings(m)) sh += settingHeight(s) + 2;
                h += sh * open;
            }
        }
        return h + PAD;
    }

    // ---- render ----------------------------------------------------------------------------

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        Theme.update(); // advance accent / dark-light blends (only the panel GUI did this before)
        float reveal = Math.min(1f, (System.currentTimeMillis() - opened) / 180f);
        reveal = 1f - (1f - reveal) * (1f - reveal);
        hits.clear();
        firstRow = null;
        accentRow = null;
        hitModule.clear();
        tips.clear();
        pageCache = null;
        filteredCache.clear();
        settingsCache.clear();
        groupFilterCache.clear();
        groupSettingsCache.clear();
        for (Column c : columns) {
            for (Module m : c.modules()) approach(kObj(m, 0, LT.L13, m.getName()), EXPANDED.contains(m) ? 1f : 0f, 0f);
            heights.put(c.id(), approach(kObj(c, 6, LT.L14, c.id()), contentHeight(c), 0f));
        }
        if (tab == Tab.DONUT) {
            for (var g : AnubisBridge.GROUPS) {
                for (ClientModule am : g.modules()) approach(kObj(am, 3, LT.L15, am.id()), A_EXPANDED.contains(am) ? 1f : 0f, 0f);
                String gk = kObj(g, 7, LT.L16, g.title());
            heights.put(gk, approach(kObj(g, 7, LT.L17, g.title()), groupHeight(g), 0f));
            }
        }

        float sc = s();
        if (FrameManager.animatedBg) drawBlobs(ctx, reveal);
        boolean glass = HudManager.liquidGlass && GlassRenderer.available();
        if (glass) {
            GlassRenderer.queue(new GlassRenderer.Panel(barX() * sc, BAR_Y * sc, barW() * sc, TAB_H * sc, TAB_H / 2f * sc, reveal));
            if (tab == Tab.MODULES || tab == Tab.FRIENDS || tab == Tab.DONUT) {
                GlassRenderer.queue(new GlassRenderer.Panel((vw() - SEARCH_W) / 2f * sc, SEARCH_Y * sc, SEARCH_W * sc,
                        SEARCH_H * sc, SEARCH_H / 2f * sc, reveal));
            }
            if (tab != Tab.MODULES && tab != Tab.DONUT) {
                float[] r = pageRect();
                GlassRenderer.queue(new GlassRenderer.Panel(r[0] * sc, r[1] * sc, r[2] * sc, r[3] * sc, RADIUS * sc, reveal));
            }
            for (Column c : columns) {
                if (tab != Tab.MODULES || !columnShown(c)) continue;
                float[] p = POS.get(c.id());
                GlassRenderer.queue(new GlassRenderer.Panel(p[0] * sc, p[1] * sc, W * sc,
                        (HEADER + 4 + heights.get(c.id())) * sc, RADIUS * sc, reveal));
            }
            if (tab == Tab.DONUT) {
                for (var g : AnubisBridge.GROUPS) {
                    if (!groupShown(g)) continue;
                    float[] p = groupPos(g);
                    GlassRenderer.queue(new GlassRenderer.Panel(p[0] * sc, p[1] * sc, W * sc,
                            (HEADER + 4 + heights.getOrDefault(kObj(g, 7, LT.L16, g.title()), 0f)) * sc, RADIUS * sc, reveal));
                }
            }
            GlassRenderer.flush(ctx);
        }
        var m = ctx.getMatrices();
        m.pushMatrix();
        m.scale(sc, sc);
        int vmx = Math.round(mouseX / sc), vmy = Math.round(mouseY / sc);
        drawTabs(ctx, glass, reveal, vmx, vmy);
        int hoverMx = vmx, hoverMy = vmy;
        if (tab == Tab.MODULES || tab == Tab.FRIENDS || tab == Tab.DONUT) drawSearch(ctx, glass, reveal);
        if (tab == Tab.MODULES) {
            for (Column c : columns) if (columnShown(c)) drawColumn(ctx, c, glass, reveal, vmx, vmy);
        } else if (tab == Tab.DONUT) {
            for (var g : AnubisBridge.GROUPS) if (groupShown(g)) drawGroup(ctx, g, glass, reveal, vmx, vmy);
        } else {
            drawPage(ctx, glass, reveal, vmx, vmy);
        }
        m.popMatrix();
        hoverSound(hoverMx, hoverMy);
        m.pushMatrix();
        m.scale(sc, sc);
        drawTooltip(ctx, hoverMx, hoverMy);
        drawCoachMark(ctx);
        m.popMatrix();
    }

    // ---- animated background ----------------------------------------------------------------------

    /** Three soft accent-hued blobs drifting on slow orbits (stacked translucent circles). */
    private void drawBlobs(DrawContext ctx, float alpha) {
        double t = System.currentTimeMillis() / 1000.0;
        float[] hsb = java.awt.Color.RGBtoHSB((Theme.accentRgb() >> 16) & 255, (Theme.accentRgb() >> 8) & 255, Theme.accentRgb() & 255, null);
        float[] hueShift = {0f, 0.08f, -0.08f};
        for (int b = 0; b < 3; b++) {
            double ph = t * (0.09 + b * 0.03) + b * 2.1;
            float cx = width * (0.5f + 0.38f * (float) Math.cos(ph)), cy = height * (0.5f + 0.32f * (float) Math.sin(ph * 1.3 + b));
            float r = Math.min(width, height) * (0.34f + 0.06f * b);
            int col = java.awt.Color.HSBtoRGB(hsb[0] + hueShift[b], Math.min(1f, hsb[1] * 0.9f + 0.1f), Math.min(1f, hsb[2] * 0.9f + 0.1f));
            for (int k = 0; k < 7; k++) {
                float rr = r * (1f - k * 0.13f);
                Draw.rect(ctx, Math.round(cx - rr), Math.round(cy - rr), Math.round(rr * 2), Math.round(rr * 2), Math.round(rr),
                        Colors.withAlpha(0xFF000000 | col, 0.035f * alpha));
            }
        }
    }

    // ---- first-time guide: spotlight + bubble ------------------------------------------------------

    private float[] firstRow, accentRow;
    private float[] coachRect;      // smoothed spotlight rect
    private float coachAlpha;

    private float[] tabRect(Tab t) {
        return new float[] {barX() + 4 + t.ordinal() * TAB_W, BAR_Y + 3, TAB_W, TAB_H - 6};
    }

    /** What to point at for the current guide step (null = nothing to show here). */
    private float[] coachTarget(Welcome.Step step) {
        return switch (step) {
            case OPEN -> null;
            case TOGGLE, BIND -> tab == Tab.MODULES ? firstRow : tabRect(Tab.MODULES);
            case HUD -> tabRect(Tab.HUD);
            case THEME -> tab == Tab.THEME ? accentRow : tabRect(Tab.THEME);
        };
    }

    private String coachText(Welcome.Step step) {
        return switch (step) {
            case TOGGLE -> tab == Tab.MODULES ? LT.L18 : LT.L19;
            case BIND -> tab == Tab.MODULES ? LT.L20 : LT.L19;
            case HUD -> LT.L21;
            case THEME -> tab == Tab.THEME ? LT.L22 : LT.L23;
            default -> "";
        };
    }

    private void drawCoachMark(DrawContext ctx) {
        Welcome.poll();
        Welcome.Step step = Welcome.current();
        float[] target = step == null ? null : coachTarget(step);
        coachAlpha += ((target != null ? 1f : 0f) - coachAlpha) * 0.2f;
        if (target == null) {
            if (coachAlpha < 0.01f) coachRect = null;
            if (coachRect == null) return;
            target = coachRect;
        }
        // glide the spotlight between targets
        if (coachRect == null) coachRect = target.clone();
        for (int i = 0; i < 4; i++) coachRect[i] += (target[i] - coachRect[i]) * 0.25f;
        float a = coachAlpha;
        if (a < 0.01f) return;
        float pad = 3;
        float x = coachRect[0] - pad, y = coachRect[1] - pad, w = coachRect[2] + 2 * pad, h = coachRect[3] + 2 * pad;
        float vw = vw(), vh = vh();
        int dim = Colors.withAlpha(0xFF000000, 0.45f * a);

        // dim everything around the target (four rects)
        com.utilitymod.echo.render3.api.FramebufferTarget.rect(ctx, 0, 0, vw, y, dim);
        com.utilitymod.echo.render3.api.FramebufferTarget.rect(ctx, 0, y + h, vw, vh - y - h, dim);
        com.utilitymod.echo.render3.api.FramebufferTarget.rect(ctx, 0, y, x, h, dim);
        com.utilitymod.echo.render3.api.FramebufferTarget.rect(ctx, x + w, y, vw - x - w, h, dim);

        // pulsing accent outline
        float pulse = 0.55f + 0.45f * (float) Math.sin(System.currentTimeMillis() / 260.0);
        int line = Colors.withAlpha(accent(), (0.6f + 0.4f * pulse) * a);
        float o = 1 + pulse;
        com.utilitymod.echo.render3.api.FramebufferTarget.rect(ctx, x - o, y - o, w + 2 * o, 1, line);
        com.utilitymod.echo.render3.api.FramebufferTarget.rect(ctx, x - o, y + h + o - 1, w + 2 * o, 1, line);
        com.utilitymod.echo.render3.api.FramebufferTarget.rect(ctx, x - o, y - o, 1, h + 2 * o, line);
        com.utilitymod.echo.render3.api.FramebufferTarget.rect(ctx, x + w + o - 1, y - o, 1, h + 2 * o, line);

        if (step == null) return;
        // speech bubble below (or above when near the bottom)
        String title = LT.L24 + (Welcome.completed() + 1) + LT.L25 + Welcome.total();
        String text = coachText(step);
        float bw = Math.max(Text.width(text, 7.5f), Text.width(title, 6.5f) + 34) + 16, bh = 30;
        float bx = Math.max(4, Math.min(vw - bw - 4, x + w / 2 - bw / 2));
        boolean above = y + h + bh + 12 > vh;
        float by = above ? y - bh - 8 : y + h + 8;
        float tipX = Math.max(bx + 8, Math.min(bx + bw - 8, x + w / 2));
        int bg = Colors.withAlpha(0xFF15171C, 0.96f * a);
        // arrow
        var m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(tipX, above ? by + bh : by);
        m.rotate((float) Math.toRadians(45));
        Draw.rect(ctx, -3, -3, 6, 6, 1, bg);
        m.popMatrix();
        Draw.rect(ctx, Math.round(bx), Math.round(by), Math.round(bw), Math.round(bh), 7, bg);
        Draw.rect(ctx, Math.round(bx), Math.round(by), 2, Math.round(bh), 1, Colors.withAlpha(accent(), a));
        Text.drawCentered(ctx, title, bx + 9, by + 9, 6.5f, Colors.withAlpha(accent(), a));
        Text.drawCentered(ctx, text, bx + 9, by + 20.5f, 7.5f, Colors.withAlpha(0xFFFFFFFF, a));
        // skip link
        String skip = LT.L26;
        float sx = bx + bw - 9 - Text.width(skip, 6.5f);
        Text.drawCentered(ctx, skip, sx, by + 9, 6.5f, Colors.withAlpha(0xFF8A8E98, a));
        hits.add(new Hit(sx - 3, by + 2, Text.width(skip, 6.5f) + 6, 13, () -> {
            Welcome.skip();
            UiSounds.play(UiSounds.Sound.TOGGLE_OFF);
        }, null, null));
    }

    // ---- binds + tooltips -----------------------------------------------------------------------

    /** Row hit -> module name (middle-click binds) and row hit -> description (tooltips), per frame. */
    private final Map<Hit, String> hitModule = new java.util.IdentityHashMap<>();
    private final Map<Hit, String> tips = new java.util.IdentityHashMap<>();
    /** Module waiting for a key after a middle-click. */
    private String bindingModule;
    private long hoverSince;

    private void drawBind(DrawContext ctx, String module, float x, float y, float alpha) {
        String label = module.equals(bindingModule) ? LT.L27 : ModuleBinds.name(ModuleBinds.get(module));
        if (label.isEmpty()) return;
        float tw = Text.width(label, 6.5f) + 6;
        float bx = x + W - 24 - tw;
        Draw.rect(ctx, Math.round(bx), Math.round(y + 5), Math.round(tw), ROW - 10, 3, Colors.withAlpha(0xFF000000, 0.3f * alpha));
        Text.drawCentered(ctx, label, bx + 3, y + ROW / 2f, 6.5f, Colors.withAlpha(0xFFE2E4EA, alpha));
    }

    private void drawTooltip(DrawContext ctx, int mx, int my) {
        if (draggingColumn != null || draggingSlider != null || bindingModule != null) return;
        Hit h = hitAt(mx, my);
        String tip = h == null ? null : tips.get(h);
        if (tip == null || System.currentTimeMillis() - hoverSince < 450) return;
        List<String> lines = Text.wrap(tip, 150, 7f, 4);
        float w = 0;
        for (String l : lines) w = Math.max(w, Text.width(l, 7f));
        float bw = w + 12, bh = lines.size() * 9 + 8;
        float tx = Math.min(mx + 10, vw() - bw - 4), ty = Math.min(my + 12, vh() - bh - 4);
        Draw.rect(ctx, Math.round(tx), Math.round(ty), Math.round(bw), Math.round(bh), 5, 0xF0101216);
        Draw.rect(ctx, Math.round(tx), Math.round(ty), 2, Math.round(bh), 1, accent());
        for (int i = 0; i < lines.size(); i++) {
            Text.drawCentered(ctx, lines.get(i), tx + 7, ty + 8.5f + i * 9, 7f, 0xFFE2E4EA);
        }
    }

    /** Last hovered control (by its rect); a tick plays when the cursor moves onto a new one. */
    private String lastHover;

    private void hoverSound(int mx, int my) {
        if (draggingColumn != null || draggingSlider != null) return;
        Hit h = hitAt(mx, my);
        String key = h == null ? null : Math.round(h.x()) + LT.L28 + Math.round(h.y()) + LT.L28 + Math.round(h.w());
        if (key != null && !key.equals(lastHover)) UiSounds.play(UiSounds.Sound.HOVER);
        if (key == null || !key.equals(lastHover)) hoverSince = System.currentTimeMillis();
        lastHover = key;
    }

    private void drawColumn(DrawContext ctx, Column c, boolean glass, float alpha, int mx, int my) {
        float[] p = POS.get(c.id());
        float x = p[0], y = p[1], h = HEADER + 4 + heights.get(c.id());
        int bg = glass ? Colors.withAlpha(0xFF101216, 0.35f * alpha) : Colors.withAlpha(Theme.SIDEBAR, 0.95f * alpha);
        Draw.rect(ctx, Math.round(x), Math.round(y), W, Math.round(h), RADIUS, bg);
        Text.drawCentered(ctx, c.title(), x + 8, y + HEADER / 2f + 1, 8.5f, Colors.withAlpha(0xFFFFFFFF, alpha));
        final float hx = x, hy = y;
        hits.add(new Hit(x, y, W, HEADER, () -> {
            draggingColumn = c.id();
            grabX = 0;
            grabY = 0;
        }, () -> {
            if (!COLLAPSED.remove(c.id())) COLLAPSED.add(c.id());
            UiSounds.play(UiSounds.Sound.CLICK);
        }, null));

        float visibleH = heights.get(c.id());
        if (visibleH < 1) return;
        ctx.enableScissor(Math.round(x), Math.round(y + HEADER), Math.round(x + W), Math.round(y + HEADER + 4 + visibleH));
        float ry = y + HEADER + 2;
        if (c.id().equals(LT.L9)) {
            ry = clientRows(ctx, x, ry, alpha, mx, my);
        } else {
            for (Module m : filtered(c)) {
                ry = moduleRow(ctx, m, x, ry, alpha, mx, my);
            }
        }
        ctx.disableScissor();
    }

    private float moduleRow(DrawContext ctx, Module m, float x, float y, float alpha, int mx, int my) {
        boolean hov = mx >= x && mx < x + W && my >= y && my < y + ROW;
        float on = approach(kObj(m, 1, LT.L29, m.getName()), m.isEnabled() ? 1f : 0f, m.isEnabled() ? 1f : 0f);
        float hv = approach(kObj(m, 2, LT.L30, m.getName()), hov ? 1f : 0f, 0f);
        if (on > 0.01f) {
            Draw.rect(ctx, Math.round(x + 3), Math.round(y), W - 6, ROW, 4, Colors.withAlpha(accent(), on * alpha));
        }
        if (hv > 0.01f) {
            Draw.rect(ctx, Math.round(x + 3), Math.round(y), W - 6, ROW, 4, Colors.withAlpha(0xFFFFFFFF, 0.08f * hv * alpha));
        }
        int textColor = Colors.lerp(0xFFDADCE2, 0xFFFFFFFF, on);
        Text.drawCentered(ctx, m.getName(), x + 9 + hv * 1.5f, y + ROW / 2f, 8f, Colors.withAlpha(textColor, alpha));
        List<Setting> settings = visibleSettings(m);
        if (!settings.isEmpty()) {
            float open = anim.getOrDefault(kObj(m, 0, LT.L13, m.getName()), 0f);
            String chev = open > 0.5f ? LT.L31 : LT.L32;
            Text.drawCentered(ctx, chev, x + W - 14, y + ROW / 2f, 8f, Colors.withAlpha(0xFFB8BCC6, alpha));
        }
        drawBind(ctx, m.getName(), x, y, alpha);
        Hit row = new Hit(x, y, W, ROW, () -> {
            m.setEnabled(!m.isEnabled());
            UiSounds.play(m.isEnabled() ? UiSounds.Sound.TOGGLE_ON : UiSounds.Sound.TOGGLE_OFF);
        }, () -> {
            if (settings.isEmpty()) return;
            if (!EXPANDED.remove(m)) EXPANDED.add(m);
            UiSounds.play(UiSounds.Sound.CLICK);
        }, null);
        hits.add(row);
        if (firstRow == null) firstRow = new float[] {x, y, W, ROW};
        hitModule.put(row, m.getName());
        if (m.getDescription() != null && !m.getDescription().isEmpty()) tips.put(row, m.getDescription());
        y += ROW + 2;

        float open = anim.getOrDefault(kObj(m, 0, LT.L13, m.getName()), 0f);
        if (open <= 0.001f) return y;
        float start = y;
        float total = 0;
        for (Setting s : settings) total += settingHeight(s) + 2;
        float limit = start + total * open;
        for (Setting s : settings) {
            if (y >= limit) break;
            y = settingRow(ctx, s, x, y, alpha * Math.min(1f, open * 1.5f), mx, my);
        }
        // accent guide on the left of the expanded block
        Draw.rect(ctx, Math.round(x + 6), Math.round(start), 2, Math.max(1, Math.round(limit - start - 2)), 1,
                Colors.withAlpha(accent(), 0.8f * alpha));
        return limit;
    }

    private float settingRow(DrawContext ctx, Setting s, float x, float y, float alpha, int mx, int my) {
        int h = settingHeight(s);
        float lx = x + 14, rw = W - 22;
        Draw.rect(ctx, Math.round(x + 10), Math.round(y), W - 14, h, 4, Colors.withAlpha(0xFF000000, 0.18f * alpha));
        float cy = s instanceof FloatSetting || s instanceof IntSetting ? y + 8 : y + h / 2f;
        Text.drawCentered(ctx, s.getName(), lx, cy, 7f, Colors.withAlpha(0xFFD0D3DA, alpha));
        float right = x + W - 8;

        if (s instanceof BoolSetting b) {
            float on = approach(b, b.getValue() ? 1f : 0f, b.getValue() ? 1f : 0f);
            int tw = 20, th = 10;
            float tx = right - tw, ty = y + (h - th) / 2f;
            Draw.rect(ctx, Math.round(tx), Math.round(ty), tw, th, 5,
                    Colors.withAlpha(Colors.lerp(0xFF3A3D45, accent(), on), alpha));
            Draw.rect(ctx, Math.round(tx + 1 + on * (tw - th)), Math.round(ty + 1), th - 2, th - 2, 4, Colors.withAlpha(0xFFFFFFFF, alpha));
            hits.add(new Hit(x, y, W, h, () -> {
                b.setValue(!b.getValue());
                UiSounds.play(b.getValue() ? UiSounds.Sound.TOGGLE_ON : UiSounds.Sound.TOGGLE_OFF);
            }, null, null));
        } else if (s instanceof FloatSetting || s instanceof IntSetting) {
            float min, max, val;
            String text;
            if (s instanceof FloatSetting f) {
                min = f.getMinValue();
                max = f.getMaxValue();
                val = f.getValue();
                float inc = f.getIncrement() > 0 ? f.getIncrement() : 0.01f;
                text = inc >= 1 ? Integer.toString(Math.round(val)) : String.format(Locale.ROOT, inc >= 0.1f ? LT.L33 : LT.L34, val);
            } else {
                IntSetting i = (IntSetting) s;
                min = i.getMinValue();
                max = i.getMaxValue();
                val = i.getValue();
                text = Integer.toString(i.getValue());
            }
            Text.drawCentered(ctx, text, right - Text.width(text, 7f), cy, 7f, Colors.withAlpha(0xFFFFFFFF, alpha));
            float frac = max > min ? (val - min) / (max - min) : 0;
            float shown = approach(s, frac, frac);
            float bx = lx, bw = right - lx, by = y + h - 7;
            Draw.rect(ctx, Math.round(bx), Math.round(by), Math.round(bw), 3, 1, Colors.withAlpha(0xFF3A3D45, alpha));
            Draw.rect(ctx, Math.round(bx), Math.round(by), Math.max(2, Math.round(bw * shown)), 3, 1, Colors.withAlpha(accent(), alpha));
            final float fmin = min, fmax = max;
            Consumer<Float> drag = mouse -> {
                float t = Math.max(0, Math.min(1, (mouse - bx) / bw));
                float v = fmin + (fmax - fmin) * t;
                if (s instanceof FloatSetting f) {
                    float inc = f.getIncrement() > 0 ? f.getIncrement() : 0.01f;
                    f.setValue(Math.round(v / inc) * inc);
                } else {
                    ((IntSetting) s).setValue(Math.round(v));
                }
            };
            hits.add(new Hit(x, y, W, h, null, null, drag));
        } else if (s instanceof ModeSetting mo) {
            String v = mo.getValue().toString();
            float pw = Text.width(v, 7f) + 12;
            Draw.rect(ctx, Math.round(right - pw), Math.round(y + 3), Math.round(pw), h - 6, 6, Colors.withAlpha(0xFF2C2F36, 0.85f * alpha));
            Text.drawCentered(ctx, v, right - pw + 6, y + h / 2f, 7f, Colors.withAlpha(0xFFFFFFFF, alpha));
            List<CharSequence> modes = mo.getModes();
            hits.add(new Hit(x, y, W, h, () -> {
                mo.cycle();
                UiSounds.play(UiSounds.Sound.CLICK);
            }, () -> {
                int i = 0;
                for (int k = 0; k < modes.size(); k++) if (modes.get(k).toString().equals(mo.getValue().toString())) i = k;
                mo.setValue(modes.get((i - 1 + modes.size()) % modes.size()));
                UiSounds.play(UiSounds.Sound.CLICK);
            }, null));
        } else if (s instanceof KeybindSetting k) {
            String v = listening == k ? LT.L27 : keyName(k.getKey());
            float pw = Text.width(v, 7f) + 12;
            Draw.rect(ctx, Math.round(right - pw), Math.round(y + 3), Math.round(pw), h - 6, 6, Colors.withAlpha(0xFF2C2F36, 0.85f * alpha));
            Text.drawCentered(ctx, v, right - pw + 6, y + h / 2f, 7f, Colors.withAlpha(0xFFFFFFFF, alpha));
            hits.add(new Hit(x, y, W, h, () -> {
                listening = k;
                UiSounds.play(UiSounds.Sound.CLICK);
            }, () -> k.setKey(-1), null));
        } else if (s instanceof com.utilitymod.echo.features.settings.impl.RegistryPickerSetting<?> rp) {
            // block / item picker: "N selected" pill -> searchable icon grid
            String v = rp.getSelectedCount() + " selected  ›";
            float pw = Text.width(v, 7f) + 12;
            boolean ph = mx >= right - pw && mx < right && my >= y + 3 && my < y + h - 3;
            Draw.rect(ctx, Math.round(right - pw), Math.round(y + 3), Math.round(pw), h - 6, 6,
                    Colors.withAlpha(ph ? accent() : 0xFF2C2F36, (ph ? 1f : 0.85f) * alpha));
            Text.drawCentered(ctx, v, right - pw + 6, y + h / 2f, 7f, Colors.withAlpha(ph ? 0xFF15161A : 0xFFFFFFFF, alpha));
            hits.add(new Hit(x, y, W, h, () -> {
                UiSounds.play(UiSounds.Sound.CLICK);
                client.setScreen(new RegistryPickerScreen(this, rp));
            }, null, null));
        } else if (s instanceof com.utilitymod.echo.features.settings.impl.MultiModeSetting mm) {
            // one chip per option, right-aligned; click a chip to toggle it
            List<CharSequence> opts = new ArrayList<>(mm.getOptionsSequenceView().keySet());
            float cx = right;
            for (int i = opts.size() - 1; i >= 0; i--) {
                CharSequence opt = opts.get(i);
                boolean on = mm.isEnabled(opt);
                float cw = Text.width(opt.toString(), 6.5f) + 8;
                cx -= cw;
                Draw.rect(ctx, Math.round(cx), Math.round(y + 4), Math.round(cw), h - 8, 5,
                        Colors.withAlpha(on ? accent() : 0xFF2C2F36, (on ? 1f : 0.85f) * alpha));
                Text.drawCentered(ctx, opt.toString(), cx + 4, y + h / 2f, 6.5f,
                        Colors.withAlpha(on ? 0xFF15161A : 0xFFD0D3DA, alpha));
                hits.add(new Hit(cx, y, cw, h, () -> {
                    mm.toggle(opt);
                    UiSounds.play(mm.isEnabled(opt) ? UiSounds.Sound.TOGGLE_ON : UiSounds.Sound.TOGGLE_OFF);
                }, null, null));
                cx -= 3;
            }
        } else if (s instanceof ColorSetting c) {
            Draw.rect(ctx, Math.round(right - 14), Math.round(y + 4), 14, h - 8, 4, Colors.withAlpha(c.getARGB() | 0xFF000000, alpha));
            hits.add(new Hit(x, y, W, h, () -> cycleColor(c, 1), () -> cycleColor(c, -1), null));
        }
        return y + h + 2;
    }

    private float clientRows(DrawContext ctx, float x, float y, float alpha, int mx, int my) {
        y = clientToggle(ctx, LT.L35, x, y, alpha, HudManager.liquidGlass, v -> HudManager.liquidGlass = v);
        y = clientToggle(ctx, LT.L36, x, y, alpha, UiSounds.enabled, v -> UiSounds.enabled = v);
        y = clientToggle(ctx, LT.L37, x, y, alpha, ToggleSounds.enabled, v -> ToggleSounds.enabled = v);
        // style switch
        String v = switch (FrameManager.style) {
            case WATER -> LT2.L2;
            case DROPDOWN -> LT.L40;
        };
        float right = x + W - 8;
        Text.drawCentered(ctx, LT.L41, x + 9, y + ROW / 2f, 8f, Colors.withAlpha(0xFFDADCE2, alpha));
        float pw = Text.width(v, 7f) + 12;
        Draw.rect(ctx, Math.round(right - pw), Math.round(y + 3), Math.round(pw), ROW - 6, 6, Colors.withAlpha(0xFF2C2F36, 0.85f * alpha));
        Text.drawCentered(ctx, v, right - pw + 6, y + ROW / 2f, 7f, Colors.withAlpha(0xFFFFFFFF, alpha));
        // cycles Panel -> Cards -> Dropdown
        hits.add(new Hit(x, y, W, ROW, () -> {
            FrameManager.Style next = FrameManager.style == FrameManager.Style.DROPDOWN
                    ? FrameManager.Style.WATER : FrameManager.Style.DROPDOWN;
            FrameManager.style = next;
            GuiConfig.save();
            UiSounds.play(UiSounds.Sound.CLICK);
            if (next == FrameManager.Style.WATER) client.setScreen(new WaterGui());
            else client.setScreen(new DropdownGui());
        }, null, null));
        return y + ROW + 2;
    }

    private float clientToggle(DrawContext ctx, String name, float x, float y, float alpha, boolean value, Consumer<Boolean> set) {
        float on = approach(kLabel(LT.L42, name), value ? 1f : 0f, value ? 1f : 0f);
        if (on > 0.01f) Draw.rect(ctx, Math.round(x + 3), Math.round(y), W - 6, ROW, 4, Colors.withAlpha(accent(), on * alpha));
        Text.drawCentered(ctx, name, x + 9, y + ROW / 2f, 8f, Colors.withAlpha(0xFFFFFFFF, alpha));
        hits.add(new Hit(x, y, W, ROW, () -> {
            set.accept(!value);
            GuiConfig.save();
            UiSounds.play(!value ? UiSounds.Sound.TOGGLE_ON : UiSounds.Sound.TOGGLE_OFF);
        }, null, null));
        return y + ROW + 2;
    }

    // ---- top bar / search / pages --------------------------------------------------------------

    private void drawTabs(DrawContext ctx, boolean glass, float alpha, int mx, int my) {
        float bx = barX(), bw = barW();
        Draw.rect(ctx, Math.round(bx), BAR_Y, Math.round(bw), TAB_H, TAB_H / 2,
                Colors.withAlpha(0xFF101216, (glass ? 0.45f : 0.92f) * alpha));
        float target = bx + 4 + tab.ordinal() * TAB_W;
        float hx = approach(LT.L43, target, target);
        Draw.rect(ctx, Math.round(hx), BAR_Y + 3, TAB_W, TAB_H - 6, (TAB_H - 6) / 2, Colors.withAlpha(accent(), alpha));
        drawDonutCat(ctx, bx - 30, BAR_Y - 1, alpha);
        for (Tab t : Tab.values()) {
            float tx = bx + 4 + t.ordinal() * TAB_W;
            boolean hov = mx >= tx && mx < tx + TAB_W && my >= BAR_Y && my < BAR_Y + TAB_H;
            int col = t == tab ? 0xFF15161A : hov ? 0xFFE2E4EA : 0xFF9A9EA8;
            Text.drawCenteredX(ctx, t.label, tx + TAB_W / 2f, BAR_Y + TAB_H / 2f, 7.5f, Colors.withAlpha(col, alpha));
            hits.add(new Hit(tx, BAR_Y, TAB_W, TAB_H, () -> {
                UiSounds.play(UiSounds.Sound.CLICK);
                if (t == Tab.HUD) {
                    client.setScreen(new com.utilitymod.echo.hud.HudEditorScreen(this));
                    return;
                }
                if (tab != t) pageScroll = 0;
                tab = t;
                searchFocused = false;
            }, null, null));
        }
    }

    private void drawSearch(DrawContext ctx, boolean glass, float alpha) {
        float sx = (vw() - SEARCH_W) / 2f;
        Draw.rect(ctx, Math.round(sx), SEARCH_Y, SEARCH_W, SEARCH_H, SEARCH_H / 2,
                Colors.withAlpha(searchFocused ? 0xFF1A1D24 : 0xFF101216, (glass ? 0.5f : 0.92f) * alpha));
        String q = search.toString();
        boolean empty = q.isEmpty();
        String text = empty ? (tab == Tab.FRIENDS ? LT.L44 : LT.L45) : q;
        if (editing != null) text = LT.L46 + editingName + LT.L47;
        boolean caret = searchFocused && (System.currentTimeMillis() / 500) % 2 == 0;
        // magnifier: ring + handle
        float ix = sx + 10, iy = SEARCH_Y + SEARCH_H / 2f;
        int ic = Colors.withAlpha(0xFF9A9EA8, alpha);
        Draw.rect(ctx, Math.round(ix - 4), Math.round(iy - 4), 7, 7, 4, ic);
        Draw.rect(ctx, Math.round(ix - 3), Math.round(iy - 3), 5, 5, 3, Colors.withAlpha(0xFF101216, alpha));
        Draw.rect(ctx, Math.round(ix + 2), Math.round(iy + 2), 2, 2, 1, ic);
        Text.drawCentered(ctx, Text.fit(text, SEARCH_W - 40, 7.5f) + (caret ? LT.L48 : ""), sx + 20,
                iy, 7.5f, Colors.withAlpha(empty ? 0xFF7A7E88 : 0xFFFFFFFF, alpha));
        if (!empty) {
            Text.drawCentered(ctx, LT.L49, sx + SEARCH_W - 12, iy, 7.5f, Colors.withAlpha(0xFF9A9EA8, alpha));
            hits.add(new Hit(sx + SEARCH_W - 20, SEARCH_Y, 20, SEARCH_H, () -> search.setLength(0), null, null));
        }
        hits.add(new Hit(sx, SEARCH_Y, SEARCH_W - (empty ? 0 : 20), SEARCH_H, () -> searchFocused = true, null, null));
    }

    private float[] pageRect() {
        float w = tab == Tab.WAYPOINTS ? 270 : 230, h = HEADER + 6 + pageContentHeight() + PAD;
        float y = tab == Tab.FRIENDS ? TOP : SEARCH_Y;
        return new float[] {(vw() - w) / 2f, y, w, Math.min(h, vh() - y - 8)};
    }

    private List<Item> pageCache;
    private float pageScroll;

    private List<Item> pageItems() {
        if (pageCache != null) return pageCache;
        List<Item> items = buildPageItems();
        pageCache = items;
        return items;
    }

    private List<Item> buildPageItems() {
        List<Item> items = new ArrayList<>();
        switch (tab) {
            case FRIENDS -> {
                var fm = com.utilitymod.echo.Echo.friendManager;
                java.util.TreeSet<String> names = new java.util.TreeSet<>(fm.all());
                if (client != null && client.getNetworkHandler() != null) {
                    String self = client.player == null ? "" : client.player.getName().getString();
                    for (var e : client.getNetworkHandler().getPlayerList()) {
                        String n = e.getProfile().name();
                        if (!n.equals(self)) names.add(n.toLowerCase(Locale.ROOT));
                    }
                }
                String q = search.toString().trim().toLowerCase(Locale.ROOT);
                for (String n : names) {
                    if (!q.isEmpty() && !n.contains(q)) continue;
                    items.add(new Item(n, fm.isFriend(n), () -> {
                        if (fm.isFriend(n)) fm.remove(n); else fm.add(n);
                        GuiConfig.save();
                    }));
                }
                if (items.isEmpty()) items.add(new Item(q.isEmpty() ? LT.L50 : LT.L51 + q, false, null));
            }
            case THEME -> {
                var hud = com.utilitymod.echo.features.impl.render.HudOverlay.instance;
                items.add(Item.header(LT.L52));
                items.add(new Item(LT.L53, Theme.mode() == Theme.Mode.DARK, () -> {
                    Theme.setMode(Theme.mode() == Theme.Mode.DARK ? Theme.Mode.LIGHT : Theme.Mode.DARK);
                    GuiConfig.save();
                }));
                items.add(new Item(LT.L54, false, null, -1));
                items.add(Item.slider(LT.L55, 0, 360, 1, () -> accentHsb()[0] * 360, v -> setAccentHsb(0, (float) v / 360f)));
                items.add(Item.slider(LT.L56, 0, 100, 1, () -> accentHsb()[1] * 100, v -> setAccentHsb(1, (float) v / 100f)));
                items.add(Item.slider(LT.L57, 0, 100, 1, () -> accentHsb()[2] * 100, v -> setAccentHsb(2, (float) v / 100f)));
                items.add(new Item(editing == ACCENT_HEX
                        ? LT.L58 + editBuf + ((System.currentTimeMillis() / 500) % 2 == 0 ? LT.L48 : "")
                        : String.format(Locale.ROOT, LT.L59, Theme.accentRgb() & 0xFFFFFF),
                        editing == ACCENT_HEX, () -> startEdit(ACCENT_HEX, LT.L54, String.format(Locale.ROOT, LT.L60, Theme.accentRgb() & 0xFFFFFF))));
                items.add(new Item(LT.L61, FrameManager.animatedBg, () -> {
                    FrameManager.animatedBg = !FrameManager.animatedBg;
                    GuiConfig.save();
                }));
                items.add(Item.slider(LT.L62, 0, 10, 1,
                        () -> client.options.getMenuBackgroundBlurriness().getValue(),
                        v -> client.options.getMenuBackgroundBlurriness().setValue((int) Math.round(v))));

                items.add(Item.header(LT.L35));
                items.add(new Item(LT.L63, HudManager.liquidGlass, () -> {
                    HudManager.liquidGlass = !HudManager.liquidGlass;
                    GuiConfig.save();
                }));
                if (hud != null) {
                    var mode = hud.glassModeSetting();
                    items.add(Item.two("Mode  \u2039 " + mode.getValue() + " \u203a", false, () -> {
                        HudManager.liquidGlass = true;
                        mode.cycle();
                        GuiConfig.save();
                    }, () -> {
                        var modes = mode.getModes();
                        int i = 0;
                        for (int k = 0; k < modes.size(); k++) if (modes.get(k).toString().equals(mode.getValue().toString())) i = k;
                        mode.setValue(modes.get((i - 1 + modes.size()) % modes.size()));
                        GuiConfig.save();
                    }));
                    items.add(Item.slider(LT.L64, 0, 1, 0.05f,
                            () -> hud.tintStrengthSetting().getValue(), v -> hud.tintStrengthSetting().setValue((float) v)));
                    items.add(Item.slider(LT.L65, 0, 12, 0.5f,
                            () -> hud.glassBlurRadius().getValue(), v -> hud.glassBlurRadius().setValue((float) v)));
                    items.add(Item.slider(LT.L66, 0, 5, 1,
                            () -> hud.glassBlurIterations().getValue(), v -> hud.glassBlurIterations().setValue((int) Math.round(v))));
                    items.add(Item.slider(LT.L67, 0, 3, 0.1f,
                            () -> hud.motionSetting().getValue(), v -> hud.motionSetting().setValue((float) v)));
                }

                items.add(Item.header(LT.L68));
                items.add(new Item(editing == THEME_NAME
                        ? LT.L69 + editBuf + ((System.currentTimeMillis() / 500) % 2 == 0 ? LT.L48 : "")
                        : LT.L70, editing == THEME_NAME, () -> startEdit(THEME_NAME, LT.L4, "")));
                for (CustomThemes.Data d : new ArrayList<>(CustomThemes.LIST)) {
                    items.add(Item.two(d.name() + LT.L71 + d.mode(), false, () -> CustomThemes.apply(d), () -> CustomThemes.remove(d)));
                }

                if (hud != null) {
                    items.add(Item.header(LT.L72));
                    preset(items, hud, LT.L73, 0x60A5FA, LT.L74, 0.25f, 2f, 1, 1.2f);
                    preset(items, hud, LT.L75, 0xEC4899, LT.L76, 0.25f, 2f, 1, 1f);
                    preset(items, hud, LT.L77, 0x3B82F6, LT.L76, 0.3f, 3f, 2, 1.4f);
                    preset(items, hud, LT.L78, 0x8B5CF6, LT.L79, 0.35f, 4f, 2, 0.6f);
                    preset(items, hud, LT.L80, 0x14B8A6, LT.L81, 0.15f, 1.5f, 1, 1f);
                    preset(items, hud, LT.L82, 0xF59E0B, LT.L83, 0.2f, 0f, 0, 1f);
                    preset(items, hud, LT.L84, 0xA8B4E8, LT.L79, 0.1f, 6f, 3, 0.5f);
                }
            }
            case CONFIG -> {
                items.add(Item.header(LT.L85));
                items.add(new Item(editing == PROFILE_NAME
                        ? LT.L69 + editBuf + ((System.currentTimeMillis() / 500) % 2 == 0 ? LT.L48 : "")
                        : LT.L86, editing == PROFILE_NAME, () -> startEdit(PROFILE_NAME, LT.L87, "")));
                for (String p : GuiConfig.profiles()) {
                    items.add(Item.withCopy(p, () -> {
                        boolean ok = GuiConfig.loadProfile(p);
                        com.utilitymod.echo.features.impl.render.Notifications.push(LT.L5,
                                ok ? LT.L88 + p : LT.L89 + p, com.utilitymod.echo.features.impl.render.Notifications.Type.INFO);
                    }, () -> GuiConfig.deleteProfile(p), () -> {
                        int n = ShareCodes.copyProfile(p);
                        com.utilitymod.echo.features.impl.render.Notifications.push(LT.L90,
                                n > 0 ? LT.L91 + p + LT.L92 + n + LT.L93 : LT.L94,
                                com.utilitymod.echo.features.impl.render.Notifications.Type.INFO);
                    }));
                }
                items.add(Item.pair(LT.L95, GuiConfig::openProfileFolder, LT.L96, GuiConfig::load));

                items.add(Item.header(LT.L97));
                items.add(Item.pair(LT.L98, () -> {
                    int n = ShareCodes.copyToClipboard();
                    com.utilitymod.echo.features.impl.render.Notifications.push(LT.L90,
                            n > 0 ? LT.L99 + n + LT.L100 : LT.L94,
                            com.utilitymod.echo.features.impl.render.Notifications.Type.INFO);
                }, LT.L101, () -> {
                    String err = ShareCodes.importFromClipboard();
                    com.utilitymod.echo.features.impl.render.Notifications.push(LT.L90,
                            err == null ? LT.L102 : err,
                            err == null ? com.utilitymod.echo.features.impl.render.Notifications.Type.ENABLED
                                    : com.utilitymod.echo.features.impl.render.Notifications.Type.DISABLED);
                }));

                items.add(Item.header(LT.L103));
                items.add(new Item(LT.L36, UiSounds.enabled, () -> {
                    UiSounds.enabled = !UiSounds.enabled;
                    GuiConfig.save();
                }));
                items.add(new Item(LT.L37, ToggleSounds.enabled, () -> {
                    ToggleSounds.enabled = !ToggleSounds.enabled;
                    GuiConfig.save();
                }));
                items.add(new Item(LT.L104, ToggleSounds.soupPack, () -> {
                    ToggleSounds.soupPack = !ToggleSounds.soupPack;
                    GuiConfig.save();
                }));
                items.add(Item.pair(LT.L105, GuiConfig::save, LT.L106, () -> {
                    FrameManager.style = FrameManager.Style.WATER;
                    GuiConfig.save();
                    client.setScreen(new WaterGui());
                }));

                items.add(Item.header(LT.L107));
                items.add(Item.pair(LT.L108, SelfTest::report,
                        Welcome.guideDone ? LT.L109 : LT.L110, Welcome.guideDone ? Welcome::restart : Welcome::skip));
            }
            case WAYPOINTS -> {
                items.add(new Item(LT.L111, false, Waypoints::addHere));
                String dim = Waypoints.dimension();
                for (Waypoints.Waypoint wp : new ArrayList<>(Waypoints.LIST)) {
                    if (!wp.dimension().equals(dim)) continue;
                    String dist = client.player == null ? "" : "  " + Math.round(Math.sqrt(client.player.squaredDistanceTo(
                            wp.x() + 0.5, wp.y(), wp.z() + 0.5))) + LT.L112;
                    items.add(Item.two(wp.name() + "  " + wp.x() + " " + wp.y() + " " + wp.z() + dist, false,
                            () -> client.keyboard.setClipboard(wp.x() + " " + wp.y() + " " + wp.z()),
                            () -> Waypoints.remove(wp)));
                }
                var history = Waypoints.history();
                items.add(new Item(LT.L113 + history.size() + LT.L114, false, null));
                if (!history.isEmpty()) items.add(new Item(LT.L115, false, Waypoints::clearHistory));
                int shown = 0;
                for (Waypoints.Finding f : history) {
                    if (++shown > 80) break;
                    int bx = f.chunkX() * 16 + 8, bz = f.chunkZ() * 16 + 8;
                    String dist = client.player == null ? "" : "  " + Math.round(Math.sqrt(
                            Math.pow(client.player.getX() - bx, 2) + Math.pow(client.player.getZ() - bz, 2))) + LT.L112;
                    items.add(Item.two(f.source() + "  " + bx + LT.L116 + bz + dist, false,
                            () -> Waypoints.add(f.source(), bx, client.player == null ? 64 : client.player.getBlockY(), bz),
                            () -> client.keyboard.setClipboard(bx + LT.L117 + bz)));
                }
            }
            default -> {}
        }
        return items;
    }

    private static float[] accentHsb() {
        int c = Theme.accentRgb();
        return java.awt.Color.RGBtoHSB((c >> 16) & 255, (c >> 8) & 255, c & 255, null);
    }

    /** Sets one HSB component of the accent (0 hue, 1 saturation, 2 brightness), keeping the others. */
    private static void setAccentHsb(int index, float value) {
        float[] hsb = accentHsb();
        hsb[index] = Math.max(0f, Math.min(1f, value));
        Theme.setAccent(java.awt.Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]) & 0xFFFFFF);
    }

    private static float itemHeight(Item it) {
        return it.swatch() == -5 ? 17 : ROW + 2;
    }

    private float pageContentHeight() {
        float h = 0;
        for (Item it : pageItems()) h += itemHeight(it);
        return h;
    }

    private static final Object PROFILE_NAME = new Object();
    private static final Object THEME_NAME = new Object();
    private static final Object ACCENT_HEX = new Object();

    private static void preset(List<Item> items, com.utilitymod.echo.features.impl.render.HudOverlay hud, String name,
                               int accent, String mode, float tint, float blur, int passes, float motion) {
        items.add(new Item(name + LT.L71 + mode, false, () -> {
            Theme.setAccent(accent);
            hud.applyPreset(mode, tint, blur, passes, motion);
            GuiConfig.save();
        }));
    }

    private void drawPage(DrawContext ctx, boolean glass, float alpha, int mx, int my) {
        float[] r = pageRect();
        float x = r[0], y = r[1], w = r[2];
        Draw.rect(ctx, Math.round(x), Math.round(y), Math.round(w), Math.round(r[3]), RADIUS,
                glass ? Colors.withAlpha(0xFF101216, 0.35f * alpha) : Colors.withAlpha(Theme.SIDEBAR, 0.95f * alpha));
        String title = tab == Tab.FRIENDS ? LT.L118 + com.utilitymod.echo.Echo.friendManager.all().size() + LT.L119 : tab.label;
        Text.drawCentered(ctx, title, x + 8, y + HEADER / 2f + 1, 8.5f, Colors.withAlpha(0xFFFFFFFF, alpha));
        ctx.enableScissor(Math.round(x), Math.round(y + HEADER), Math.round(x + w), Math.round(y + r[3]));
        float ry = y + HEADER + 4 - pageScroll;
        for (Item it : pageItems()) {
            if (ry + ROW < y + HEADER || ry > y + r[3]) {
                ry += itemHeight(it);
                continue;
            }
            if (it.swatch() == -5) {
                // section header: small muted caps + hairline
                float hy = ry + 9;
                Text.drawCentered(ctx, it.label().toUpperCase(Locale.ROOT), x + 9, hy, 6.2f, Colors.withAlpha(accent(), 0.9f * alpha));
                float tw = Text.width(it.label().toUpperCase(Locale.ROOT), 6.2f);
                Draw.rect(ctx, Math.round(x + 14 + tw), Math.round(hy), Math.round(w - 23 - tw), 1, 0, Colors.withAlpha(0xFFFFFFFF, 0.1f * alpha));
                ry += itemHeight(it);
                continue;
            }
            if (it.swatch() == -7) {
                String copy = LT.L120;
                float pw = Text.width(copy, 6.5f) + 10;
                float px = x + w - 8 - pw;
                boolean ph = mx >= px && mx < px + pw && my >= ry + 4 && my < ry + ROW - 4;
                Draw.rect(ctx, Math.round(px), Math.round(ry + 4), Math.round(pw), ROW - 8, 5,
                        Colors.withAlpha(ph ? accent() : 0xFF2C2F36, (ph ? 1f : 0.85f) * alpha));
                Text.drawCentered(ctx, copy, px + 5, ry + ROW / 2f, 6.5f, Colors.withAlpha(ph ? 0xFF15161A : 0xFFE2E4EA, alpha));
                Item row = it;
                hits.add(new Hit(px, ry + 4, pw, ROW - 8, () -> row.set().accept(0), null, null));
            }
            if (it.swatch() == -6) {
                String[] labels = it.label().split(LT.L8, 2);
                float bw = (w - 12) / 2f;
                for (int b = 0; b < 2; b++) {
                    float bx = x + 4 + b * (bw + 4);
                    boolean bh = mx >= bx && mx < bx + bw && my >= ry && my < ry + ROW;
                    Draw.rect(ctx, Math.round(bx), Math.round(ry + 1), Math.round(bw), ROW - 2, 6,
                            Colors.withAlpha(bh ? 0xFF3A3D45 : 0xFF2A2C33, (bh ? 0.95f : 0.8f) * alpha));
                    Text.drawCenteredX(ctx, labels[b], bx + bw / 2f, ry + ROW / 2f, 7.5f, Colors.withAlpha(0xFFFFFFFF, alpha));
                    Runnable act = b == 0 ? it.action() : it.right();
                    hits.add(new Hit(bx, ry, bw, ROW, () -> {
                        act.run();
                        UiSounds.play(UiSounds.Sound.CLICK);
                    }, null, null));
                }
                ry += itemHeight(it);
                continue;
            }
            boolean hov = (it.action() != null || it.right() != null) && mx >= x && mx < x + w && my >= ry && my < ry + ROW;
            float on = approach(kLabel(LT.L121, it.label()), it.on() ? 1f : 0f, it.on() ? 1f : 0f);
            if (on > 0.01f) Draw.rect(ctx, Math.round(x + 3), Math.round(ry), Math.round(w - 6), ROW, 4, Colors.withAlpha(accent(), on * alpha));
            if (hov) Draw.rect(ctx, Math.round(x + 3), Math.round(ry), Math.round(w - 6), ROW, 4, Colors.withAlpha(0xFFFFFFFF, 0.08f * alpha));
            boolean dim = it.action() == null && it.right() == null && it.swatch() == 0;
            float labelY = it.swatch() == -2 ? ry + ROW / 2f - 2 : ry + ROW / 2f;
            Text.drawCentered(ctx, it.label(), x + 9, labelY, 8f, Colors.withAlpha(dim && it.swatch() != -2 ? 0xFF9A9EA8 : 0xFFFFFFFF, alpha));
            if (it.swatch() == -2) {
                // slider: value on the right, bar along the bottom of the row
                double v = it.get().getAsDouble();
                String text = it.step() >= 1 ? Long.toString(Math.round(v)) : String.format(Locale.ROOT, LT.L33, v);
                Text.drawCentered(ctx, text, x + w - 9 - Text.width(text, 7.5f), ry + ROW / 2f - 2, 7.5f, Colors.withAlpha(0xFFFFFFFF, alpha));
                float frac = it.max() > it.min() ? (float) ((v - it.min()) / (it.max() - it.min())) : 0;
                float shown = approach(kLabel(LT.L122, it.label()), frac, frac);
                float bx = x + 9, bw = w - 18, by = ry + ROW - 4;
                Draw.rect(ctx, Math.round(bx), Math.round(by), Math.round(bw), 3, 1, Colors.withAlpha(0xFF3A3D45, alpha));
                Draw.rect(ctx, Math.round(bx), Math.round(by), Math.max(2, Math.round(bw * shown)), 3, 1, Colors.withAlpha(accent(), alpha));
                Item slider = it;
                hits.add(new Hit(x, ry, w, ROW, null, null, mouse -> {
                    float t = Math.max(0, Math.min(1, (mouse - bx) / bw));
                    double raw = slider.min() + (slider.max() - slider.min()) * t;
                    slider.set().accept(Math.round(raw / slider.step()) * slider.step());
                }));
            } else if (it.swatch() == -1) {
                accentRow = new float[] {x + 3, ry, w - 6, ROW};
                float sx = x + w - 8 - ACCENTS.length * 13;
                for (int i = 0; i < ACCENTS.length; i++) {
                    int rgb = ACCENTS[i];
                    boolean sel = (Theme.accentRgb() & 0xFFFFFF) == rgb;
                    float cx = sx + i * 13;
                    if (sel) Draw.rect(ctx, Math.round(cx - 1), Math.round(ry + 4), 12, 12, 6, Colors.withAlpha(0xFFFFFFFF, alpha));
                    Draw.rect(ctx, Math.round(cx), Math.round(ry + 5), 10, 10, 5, Colors.withAlpha(0xFF000000 | rgb, alpha));
                    hits.add(new Hit(cx - 1, ry, 13, ROW, () -> {
                        Theme.setAccent(rgb);
                        GuiConfig.save();
                        UiSounds.play(UiSounds.Sound.CLICK);
                    }, null, null));
                }
            } else if (it.action() != null || it.right() != null) {
                Runnable a = it.action();
                boolean was = it.on();
                hits.add(new Hit(x, ry, w, ROW, a == null ? null : () -> {
                    a.run();
                    UiSounds.play(was ? UiSounds.Sound.TOGGLE_OFF : UiSounds.Sound.TOGGLE_ON);
                }, it.right() == null ? null : () -> {
                    it.right().run();
                    UiSounds.play(UiSounds.Sound.TOGGLE_OFF);
                }, null));
            }
            ry += ROW + 2;
        }
        ctx.disableScissor();
    }

    // ---- Donut tab: modules ported from Anubis (GPL-3) ---------------------------------------------

    private static final Set<ClientModule> A_EXPANDED = new HashSet<>();
    /** StringSetting / StringListSetting being typed into. */
    private Object editing;
    private String editingName = "";
    private final StringBuilder editBuf = new StringBuilder();

    private float[] groupPos(AnubisBridge.Group g) {
        String key = LT.L16 + g.title();
        float[] p = POS.get(key);
        if (p == null) {
            int i = AnubisBridge.GROUPS.indexOf(g);
            p = new float[] {Math.max(0, (vw() - AnubisBridge.GROUPS.size() * (W + 8)) / 2f) + i * (W + 8), TOP};
            POS.put(key, p);
        }
        return p;
    }

    private List<ClientModule> filteredGroup(AnubisBridge.Group g) {
        return groupFilterCache.computeIfAbsent(g, this::computeFilteredGroup);
    }

    private List<ClientModule> computeFilteredGroup(AnubisBridge.Group g) {
        String q = search.toString().trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return g.modules();
        List<ClientModule> out = new ArrayList<>();
        for (ClientModule m : g.modules()) if (m.name().toLowerCase(Locale.ROOT).contains(q)) out.add(m);
        return out;
    }

    private boolean groupShown(AnubisBridge.Group g) {
        return !filteredGroup(g).isEmpty();
    }

    private List<com.utilitymod.anubis.module.setting.Setting> aSettings(ClientModule m) {
        return groupSettingsCache.computeIfAbsent(m, this::computeGroupSettings);
    }

    private List<com.utilitymod.anubis.module.setting.Setting> computeGroupSettings(ClientModule m) {
        List<com.utilitymod.anubis.module.setting.Setting> out = new ArrayList<>();
        for (var st : m.settings()) if (st.isVisible()) out.add(st);
        return out;
    }

    private static int aSettingHeight(com.utilitymod.anubis.module.setting.Setting st) {
        return st instanceof com.utilitymod.anubis.module.setting.NumberSetting ? SLIDER_ROW : ROW;
    }

    private float groupHeight(AnubisBridge.Group g) {
        if (COLLAPSED.contains(LT.L16 + g.title())) return 0;
        float h = 0;
        for (ClientModule m : filteredGroup(g)) {
            h += ROW + 2;
            float open = anim.getOrDefault(kObj(m, 3, LT.L15, m.id()), 0f);
            if (open > 0.001f) {
                float sh = 0;
                for (var st : aSettings(m)) sh += aSettingHeight(st) + 2;
                h += sh * open;
            }
        }
        return h + PAD;
    }

    private void drawGroup(DrawContext ctx, AnubisBridge.Group g, boolean glass, float alpha, int mx, int my) {
        String key = LT.L16 + g.title();
        float[] p = groupPos(g);
        float x = p[0], y = p[1];
        float visibleH = heights.getOrDefault(key, 0f);
        int bg = glass ? Colors.withAlpha(0xFF101216, 0.35f * alpha) : Colors.withAlpha(Theme.SIDEBAR, 0.95f * alpha);
        Draw.rect(ctx, Math.round(x), Math.round(y), W, Math.round(HEADER + 4 + visibleH), RADIUS, bg);
        Text.drawCentered(ctx, g.title(), x + 8, y + HEADER / 2f + 1, 8.5f, Colors.withAlpha(0xFFFFFFFF, alpha));
        hits.add(new Hit(x, y, W, HEADER, () -> {
            draggingColumn = key;
        }, () -> {
            if (!COLLAPSED.remove(key)) COLLAPSED.add(key);
            UiSounds.play(UiSounds.Sound.CLICK);
        }, null));
        if (visibleH < 1) return;
        ctx.enableScissor(Math.round(x), Math.round(y + HEADER), Math.round(x + W), Math.round(y + HEADER + 4 + visibleH));
        float ry = y + HEADER + 2;
        for (ClientModule m : filteredGroup(g)) ry = aModuleRow(ctx, m, x, ry, alpha, mx, my);
        ctx.disableScissor();
    }

    private float aModuleRow(DrawContext ctx, ClientModule m, float x, float y, float alpha, int mx, int my) {
        boolean hov = mx >= x && mx < x + W && my >= y && my < y + ROW;
        float on = approach(kObj(m, 4, LT.L123, m.id()), m.isEnabled() ? 1f : 0f, m.isEnabled() ? 1f : 0f);
        float hv = approach(kObj(m, 5, LT.L124, m.id()), hov ? 1f : 0f, 0f);
        if (on > 0.01f) Draw.rect(ctx, Math.round(x + 3), Math.round(y), W - 6, ROW, 4, Colors.withAlpha(accent(), on * alpha));
        if (hv > 0.01f) Draw.rect(ctx, Math.round(x + 3), Math.round(y), W - 6, ROW, 4, Colors.withAlpha(0xFFFFFFFF, 0.08f * hv * alpha));
        Text.drawCentered(ctx, Text.fit(m.name(), W - 30, 8f), x + 9 + hv * 1.5f, y + ROW / 2f, 8f,
                Colors.withAlpha(Colors.lerp(0xFFDADCE2, 0xFFFFFFFF, on), alpha));
        List<com.utilitymod.anubis.module.setting.Setting> settings = aSettings(m);
        float open = anim.getOrDefault(kObj(m, 3, LT.L15, m.id()), 0f);
        if (!settings.isEmpty()) {
            Text.drawCentered(ctx, open > 0.5f ? LT.L31 : LT.L32, x + W - 14, y + ROW / 2f, 8f, Colors.withAlpha(0xFFB8BCC6, alpha));
        }
        drawBind(ctx, m.name(), x, y, alpha);
        Hit row = new Hit(x, y, W, ROW, () -> {
            if (m.isAction()) m.onClick();
            else m.toggle();
            GuiConfig.save();
            UiSounds.play(m.isEnabled() ? UiSounds.Sound.TOGGLE_ON : UiSounds.Sound.TOGGLE_OFF);
        }, () -> {
            if (settings.isEmpty()) return;
            if (!A_EXPANDED.remove(m)) A_EXPANDED.add(m);
            UiSounds.play(UiSounds.Sound.CLICK);
        }, null);
        hits.add(row);
        hitModule.put(row, m.name());
        if (m.description() != null && !m.description().isEmpty()) tips.put(row, m.description());
        y += ROW + 2;
        if (open <= 0.001f) return y;
        float start = y, total = 0;
        for (var st : settings) total += aSettingHeight(st) + 2;
        float limit = start + total * open;
        for (var st : settings) {
            if (y >= limit) break;
            y = aSettingRow(ctx, st, x, y, alpha * Math.min(1f, open * 1.5f));
        }
        Draw.rect(ctx, Math.round(x + 6), Math.round(start), 2, Math.max(1, Math.round(limit - start - 2)), 1,
                Colors.withAlpha(accent(), 0.8f * alpha));
        return limit;
    }

    private void pill(DrawContext ctx, String v, float right, float y, int h, float alpha) {
        float pw = Math.min(W - 60, Text.width(v, 7f) + 12);
        Draw.rect(ctx, Math.round(right - pw), Math.round(y + 3), Math.round(pw), h - 6, 6, Colors.withAlpha(0xFF2C2F36, 0.85f * alpha));
        Text.drawCentered(ctx, Text.fit(v, pw - 10, 7f), right - pw + 6, y + h / 2f, 7f, Colors.withAlpha(0xFFFFFFFF, alpha));
    }

    private float aSettingRow(DrawContext ctx, com.utilitymod.anubis.module.setting.Setting st, float x, float y, float alpha) {
        int h = aSettingHeight(st);
        float lx = x + 14, right = x + W - 8;
        Draw.rect(ctx, Math.round(x + 10), Math.round(y), W - 14, h, 4, Colors.withAlpha(0xFF000000, 0.18f * alpha));
        boolean slider = st instanceof com.utilitymod.anubis.module.setting.NumberSetting;
        float cy = slider ? y + 8 : y + h / 2f;
        Text.drawCentered(ctx, Text.fit(st.name(), W - 70, 7f), lx, cy, 7f, Colors.withAlpha(0xFFD0D3DA, alpha));

        if (st instanceof com.utilitymod.anubis.module.setting.BooleanSetting b) {
            float on = approach(b, b.get() ? 1f : 0f, b.get() ? 1f : 0f);
            int tw = 20, th = 10;
            float tx = right - tw, ty = y + (h - th) / 2f;
            Draw.rect(ctx, Math.round(tx), Math.round(ty), tw, th, 5, Colors.withAlpha(Colors.lerp(0xFF3A3D45, accent(), on), alpha));
            Draw.rect(ctx, Math.round(tx + 1 + on * (tw - th)), Math.round(ty + 1), th - 2, th - 2, 4, Colors.withAlpha(0xFFFFFFFF, alpha));
            hits.add(new Hit(x, y, W, h, () -> {
                b.set(!b.get());
                UiSounds.play(b.get() ? UiSounds.Sound.TOGGLE_ON : UiSounds.Sound.TOGGLE_OFF);
            }, null, null));
        } else if (st instanceof com.utilitymod.anubis.module.setting.NumberSetting n) {
            String text = n.formatted();
            Text.drawCentered(ctx, text, right - Text.width(text, 7f), cy, 7f, Colors.withAlpha(0xFFFFFFFF, alpha));
            float min = n.min(), max = n.max();
            float frac = max > min ? (n.get() - min) / (max - min) : 0;
            float shown = approach(n, frac, frac);
            float bx = lx, bw = right - lx, by = y + h - 7;
            Draw.rect(ctx, Math.round(bx), Math.round(by), Math.round(bw), 3, 1, Colors.withAlpha(0xFF3A3D45, alpha));
            Draw.rect(ctx, Math.round(bx), Math.round(by), Math.max(2, Math.round(bw * shown)), 3, 1, Colors.withAlpha(accent(), alpha));
            hits.add(new Hit(x, y, W, h, null, null, mouse -> {
                float t = Math.max(0, Math.min(1, (mouse - bx) / bw));
                n.set(n.snap(min + (max - min) * t));
            }));
        } else if (st instanceof com.utilitymod.anubis.module.setting.ModeSetting mo) {
            pill(ctx, mo.get(), right, y, h, alpha);
            hits.add(new Hit(x, y, W, h, () -> {
                mo.next();
                UiSounds.play(UiSounds.Sound.CLICK);
            }, () -> {
                List<String> modes = mo.modes();
                int i = Math.max(0, modes.indexOf(mo.get()));
                mo.set(modes.get((i - 1 + modes.size()) % modes.size()));
                UiSounds.play(UiSounds.Sound.CLICK);
            }, null));
        } else if (st instanceof com.utilitymod.anubis.module.setting.ColorSetting c) {
            Draw.rect(ctx, Math.round(right - 14), Math.round(y + 4), 14, h - 8, 4, Colors.withAlpha(c.argb() | 0xFF000000, alpha));
            hits.add(new Hit(x, y, W, h, () -> cycleAColor(c, 1), () -> cycleAColor(c, -1), null));
        } else if (st instanceof com.utilitymod.anubis.module.setting.StringSetting str) {
            String v = editing == str ? editBuf + ((System.currentTimeMillis() / 500) % 2 == 0 ? LT.L48 : "") : str.get();
            pill(ctx, v.isEmpty() ? LT.L31 : v, right, y, h, alpha);
            hits.add(new Hit(x, y, W, h, () -> startEdit(str, st.name(), str.get()), () -> str.set(""), null));
        } else if (st instanceof com.utilitymod.anubis.module.setting.StringListSetting list) {
            String v = editing == list ? LT.L125 + editBuf + ((System.currentTimeMillis() / 500) % 2 == 0 ? LT.L48 : "")
                    : list.size() + (list.size() == 1 ? LT.L126 : LT.L127);
            pill(ctx, v, right, y, h, alpha);
            hits.add(new Hit(x, y, W, h, () -> startEdit(list, st.name(), ""), () -> {
                list.clear();
                UiSounds.play(UiSounds.Sound.TOGGLE_OFF);
            }, null));
        }
        return y + h + 2;
    }

    private void startEdit(Object target, String name, String initial) {
        editing = target;
        editingName = name;
        editBuf.setLength(0);
        editBuf.append(initial);
        searchFocused = false;
        UiSounds.play(UiSounds.Sound.CLICK);
    }

    private void finishEdit(boolean save) {
        if (save) {
            if (editing == THEME_NAME) {
                String n = editBuf.toString().trim();
                if (!n.isEmpty()) {
                    CustomThemes.saveCurrent(n);
                    com.utilitymod.echo.features.impl.render.Notifications.push(LT.L4, LT.L128 + n,
                            com.utilitymod.echo.features.impl.render.Notifications.Type.INFO);
                }
            } else if (editing == ACCENT_HEX) {
                String hex = editBuf.toString().trim().replace(LT.L129, "");
                try {
                    if (hex.length() == 3) hex = "" + hex.charAt(0) + hex.charAt(0) + hex.charAt(1) + hex.charAt(1) + hex.charAt(2) + hex.charAt(2);
                    if (hex.length() == 6) {
                        Theme.setAccent(Integer.parseInt(hex, 16));
                        GuiConfig.save();
                    }
                } catch (NumberFormatException ignored) {
                }
            } else if (editing == PROFILE_NAME) {
                String n = editBuf.toString().trim();
                if (!n.isEmpty() && GuiConfig.saveProfile(n)) {
                    com.utilitymod.echo.features.impl.render.Notifications.push(LT.L5, LT.L130 + n,
                            com.utilitymod.echo.features.impl.render.Notifications.Type.INFO);
                }
            } else if (editing instanceof com.utilitymod.anubis.module.setting.StringSetting str) str.set(editBuf.toString());
            else if (editing instanceof com.utilitymod.anubis.module.setting.StringListSetting list) list.addValue(editBuf.toString().trim());
            GuiConfig.save();
        }
        editing = null;
        editBuf.setLength(0);
    }

    private static void cycleAColor(com.utilitymod.anubis.module.setting.ColorSetting c, int dir) {
        int cur = c.argb() & 0xFFFFFF, idx = -1;
        for (int i = 0; i < COLORS.length; i++) if (COLORS[i] == cur) idx = i;
        idx = (idx + dir + COLORS.length) % COLORS.length;
        c.setArgb((c.argb() & 0xFF000000) | COLORS[idx]);
        UiSounds.play(UiSounds.Sound.CLICK);
    }

    /** Pixel cat loafing on a blue-frosted donut (k outline, o fur, w muzzle/hole, e eyes, p cheeks, b/l/n frosting, d dough). */
    private static final String[] CAT = {
            LT.L131,
            LT.L132,
            LT.L133,
            LT.L134,
            LT.L135,
            LT.L136,
            LT.L137,
            LT.L138,
            LT.L139,
            LT.L140,
            LT.L141,
            LT.L142,
            LT.L143,
            LT.L144,
            LT.L145,
    };

    private void drawDonutCat(DrawContext ctx, float x, float y, float alpha) {
        long now = System.currentTimeMillis();
        float bob = (float) Math.sin(now / 420.0) * 0.8f;
        boolean blink = now % 3600 < 140;
        float px = 1.6f;
        for (int r = 0; r < CAT.length; r++) {
            String row = CAT[r];
            float ry = y + r * px + (r < 8 ? bob : 0);
            for (int c = 0; c < row.length(); c++) {
                int col = switch (row.charAt(c)) {
                    case 'k' -> 0xFF0E1830;
                    case 'o' -> 0xFFF4A259;
                    case 'w' -> 0xFFFFF4E8;
                    case 'e' -> blink ? 0xFFF4A259 : 0xFF111111;
                    case 'p' -> 0xFFF7A1C4;
                    case 'b' -> 0xFF1E6FF0;   // frosting
                    case 'l' -> 0xFF5AA0FF;   // frosting highlight
                    case 'n' -> 0xFF1450B8;   // drip shadow
                    case 'd' -> 0xFFD9A066;   // dough
                    default -> 0;
                };
                if (col == 0) continue;
                com.utilitymod.echo.render3.api.FramebufferTarget.rect(ctx, x + c * px, ry, px + 0.05f, px + 0.05f,
                        Colors.withAlpha(col, alpha));
            }
        }
    }

    /** Lavender-ish pill colour derived from the theme accent (lighter, like the reference). */
    private static int accent() {
        return Colors.lerp(Theme.ACCENT, 0xFFFFFFFF, 0.35f);
    }

    private static void cycleColor(ColorSetting c, int dir) {
        int cur = c.getARGB() & 0xFFFFFF, idx = -1;
        for (int i = 0; i < COLORS.length; i++) if (COLORS[i] == cur) idx = i;
        idx = (idx + dir + COLORS.length) % COLORS.length;
        c.setFromARGB((c.getARGB() & 0xFF000000) | COLORS[idx]);
        UiSounds.play(UiSounds.Sound.CLICK);
    }

    private static String keyName(int code) {
        if (code == -1 || code == 0) return LT.L146;
        if ((code & 0x80000000) != 0) return LT.L147 + ((code & 0xFF) + 1);
        String n = InputUtil.Type.KEYSYM.createFromCode(code).getLocalizedText().getString();
        return n.toUpperCase(Locale.ROOT);
    }

    // ---- input -------------------------------------------------------------------------------

    private Hit hitAt(double mx, double my) {
        // smallest target under the cursor wins (inline pills / chips sit inside their row)
        Hit best = null;
        float bestArea = Float.MAX_VALUE;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (mx >= h.x() && mx < h.x() + h.w() && my >= h.y() && my < h.y() + h.h()) {
                float area = h.w() * h.h();
                if (area < bestArea) {
                    bestArea = area;
                    best = h;
                }
            }
        }
        return best;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubleClick) {
        double mx = click.x() / s(), my = click.y() / s();
        if (listening != null) {
            listening.setKey(0x80000000 | click.button());
            listening = null;
            return true;
        }
        if (bindingModule != null) {
            // mouse buttons 3+ can be binds; left/right cancel
            if (click.button() >= 2) ModuleBinds.set(bindingModule, 0x80000000 | click.button());
            bindingModule = null;
            UiSounds.play(UiSounds.Sound.CLICK);
            return true;
        }
        Hit h = hitAt(mx, my);
        if (click.button() == 2 && h != null && hitModule.containsKey(h)) {
            bindingModule = hitModule.get(h);
            UiSounds.play(UiSounds.Sound.CLICK);
            return true;
        }
        searchFocused = false;
        if (editing != null) finishEdit(true);
        if (h == null) return super.mouseClicked(click, doubleClick);
        if (click.button() == 0) {
            if (h.drag() != null) {
                draggingSlider = h;
                h.drag().accept((float) mx);
            } else if (h.left() != null) {
                h.left().run();
                if (draggingColumn != null) {
                    float[] p = POS.get(draggingColumn);
                    grabX = (float) mx - p[0];
                    grabY = (float) my - p[1];
                    // bring to front
                    for (int i = 0; i < columns.size(); i++) {
                        if (columns.get(i).id().equals(draggingColumn)) {
                            columns.add(columns.remove(i));
                            break;
                        }
                    }
                }
            }
            return true;
        }
        if (click.button() == 1 && h.right() != null) {
            h.right().run();
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        if (draggingSlider != null) {
            draggingSlider.drag().accept((float) (click.x() / s()));
            return true;
        }
        if (draggingColumn != null) {
            float[] p = POS.get(draggingColumn);
            p[0] = Math.max(0, Math.min(vw() - W, (float) (click.x() / s()) - grabX));
            p[1] = Math.max(0, Math.min(vh() - HEADER, (float) (click.y() / s()) - grabY));
            return true;
        }
        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean mouseReleased(Click click) {
        draggingColumn = null;
        draggingSlider = null;
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        if (tab != Tab.MODULES && tab != Tab.DONUT) {
            float[] r = pageRect();
            float total = pageContentHeight() + HEADER + 6 + PAD;
            pageScroll = Math.max(0, Math.min(Math.max(0, total - r[3]), pageScroll - (float) v * 22f));
            return true;
        }
        for (float[] p : POS.values()) p[1] += (float) v * 18f;
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (bindingModule != null) {
            int key = input.key();
            boolean clear = key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE;
            ModuleBinds.set(bindingModule, clear ? -1 : key);
            bindingModule = null;
            UiSounds.play(UiSounds.Sound.CLICK);
            return true;
        }
        if (listening != null) {
            int key = input.key();
            listening.setKey(key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_DELETE || key == GLFW.GLFW_KEY_BACKSPACE ? -1 : key);
            listening = null;
            return true;
        }
        if (editing != null) {
            int key = input.key();
            if (key == GLFW.GLFW_KEY_BACKSPACE) {
                if (!editBuf.isEmpty()) editBuf.setLength(editBuf.length() - 1);
            } else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                finishEdit(true);
            } else if (key == GLFW.GLFW_KEY_ESCAPE) {
                finishEdit(false);
            }
            return true;
        }
        if (searchFocused || ((tab == Tab.MODULES || tab == Tab.DONUT) && !search.isEmpty())) {
            int key = input.key();
            if (key == GLFW.GLFW_KEY_BACKSPACE && !search.isEmpty()) {
                search.setLength(search.length() - 1);
                return true;
            }
            if (key == GLFW.GLFW_KEY_ENTER && tab == Tab.FRIENDS && !search.toString().isBlank()) {
                com.utilitymod.echo.Echo.friendManager.add(search.toString().trim());
                search.setLength(0);
                GuiConfig.save();
                UiSounds.play(UiSounds.Sound.TOGGLE_ON);
                return true;
            }
            if (key == GLFW.GLFW_KEY_ESCAPE && searchFocused) {
                searchFocused = false;
                search.setLength(0);
                return true;
            }
        }
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharInput input) {
        if (listening != null) return true;
        if (editing != null) {
            if (editBuf.length() < 256) editBuf.append(input.asString());
            return true;
        }
        // typing anywhere on Modules/Friends/Donut goes to the search bar
        if (tab != Tab.MODULES && tab != Tab.FRIENDS && tab != Tab.DONUT) return false;
        String str = input.asString();
        if (str.isEmpty() || search.length() > 32) return true;
        searchFocused = true;
        search.append(str);
        return true;
    }

    @Override
    public void close() {
        GuiConfig.save();
        client.options.write();
        super.close();
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xba08,0xe2ad,0x2ce1,0x2a52,0xc5bf,0xf3c9,0xcff6}, new char[]{0x2a1f,0xba67,0x2c85,0xc5ca,0xcf9a,0xf3ac,0xe2de}).toString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0276,0x7188,0xeaaf}, new char[]{0x023e,0x71dd,0xeaeb,0x0c1d}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3375,0x374b,0x7180,0xe3fe,0x1a61,0x2bad,0x56cf}, new char[]{0x5689,0x3739,0x71e9,0xe39b,0x331b,0x2bc9,0x1a12}).toString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0aae,0x1864,0xcb32,0x13a0,0x2804}, new char[]{0x13f4,0x286c,0xcb57,0x1809,0x0acb}).plainString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x87ba,0xaf99,0x28ff,0x6066,0x612f,0xb5eb}, new char[]{0x616c,0xaff6,0x87d4,0x2899,0xb582,0x6001}).toString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa70b,0x1826,0xdcfa,0x45bd,0x9023}, new char[]{0x45f9,0xdc95,0xa765,0x1853,0x9057}).toString();
        static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe448,0x7fb3,0x0de4,0x0b53,0x78ec,0xa0ba,0x1a1b,0xffec,0x29f1}, new char[]{0x1a4c,0xe429,0x0d9d,0x789c,0x0b3c,0x2998,0xff82,0x7fc7,0xa0c9}).toString();
        static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4e67}, new char[]{0x4e67,0x4525,0x0114,0x13c4}).plainString();
        static final String L9 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xce3a,0x318d,0x39fa,0x095e,0x3b42,0xb267}, new char[]{0xce79,0x0912,0x31c4,0xb222,0x3b0c,0x39ae}).plainString();
        static final String L10 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x226a, 0xd12c, 0x37d1, 0xa8ae, 0xbc1a}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be}).plainString();
        static final String L11 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0d42,0x4e0d,0x3195,0x073b,0x2cbc,0xed2f}, new char[]{0x2cee,0xed4a,0x4e63,0x075f,0x0d27,0x31e7}).plainString();
        static final String L12 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2d5a,0xf66a,0xd254,0x73f4,0xd854,0x07f4}, new char[]{0xf629,0x7398,0xd83d,0x2d3f,0xd23a,0x0780}).toString();
        static final String L13 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xdb89,0xfe9b}, new char[]{0xfee3,0xdbb3,0x1cb8,0xe28e}).plainString();
        static final String L14 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x796f,0x21e6}, new char[]{0x7907,0x21dc,0x67c2,0xae05}).plainString();
        static final String L15 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x99eb,0x8e34,0xadf8}, new char[]{0x8e55,0xad80,0x99d1,0x201e}).plainString();
        static final String L16 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x689d,0x5059}, new char[]{0x5018,0x68a7,0x9795,0xdaf1}).plainString();
        static final String L17 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8119,0xa9f0,0x8179}, new char[]{0xa991,0x8171,0x8143,0xd082}).plainString();
        static final String L18 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9ed9,0x7bb3,0xe85a,0xa05c,0x3888,0x994d,0x7347,0xeaab,0xab48,0x170e,0xb744,0x4016,0x03da,0x9aa3,0x248c,0xc189,0x82c1,0x4a49,0xe2d9,0x73bf,0xbf4b,0x3ffa,0x1023,0x37a7,0x0577,0xfd7d,0x156b,0xc330,0x2f67,0x40b0,0x73bd,0xe56a,0x214f}, new char[]{0x37eb,0x9ac6,0xa03a,0xfd09,0x38a5,0x7bd0,0xe2b5,0x82a8,0x73de,0x3f91,0x9ef9,0x73de,0x154b,0xab25,0xc35f,0x2f03,0x40c5,0xeac7,0x1046,0xc1a9,0x03ae,0x0518,0xbf6b,0x7333,0x4a3c,0xe518,0xe834,0x216f,0x1767,0x24f8,0xb764,0x9922,0x4078}).toString();
        static final String L19 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7167,0x8922,0x71cb,0xa7d8,0xcda1,0x7216,0x8998,0xc137,0x3bb8,0x8281,0xedec,0xadd6,0xa5fd,0x8e48,0xf512,0xf56a,0x4193,0xa3eb,0xa4f0,0xf3f9}, new char[]{0xf525,0xcdd1,0xf39c,0x71a5,0x82a1,0xf566,0x894a,0x7102,0xa5dd,0xa795,0xa384,0xc153,0x7263,0x89f4,0xadb3,0xed9f,0x41b3,0x8e3c,0x3bd9,0xa492}).toString();
        static final String L20 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6c77,0x7efa,0x6507,0x0e86,0xdde7,0x2601,0xc399,0xf9e6,0xd681,0xa20d,0xfda4,0x35bc,0x4df8,0xe6f1,0x0f5e,0x0274,0x5970,0xf117,0xd068,0x564d,0xf215,0x57ca,0xadf6,0x7d0b,0x0145,0x1f04,0x8a15,0x7731,0xadfd,0xb219,0x4ef6,0x12f5,0x5e14,0x5130,0x206a,0x6c8d,0xa6f2,0x9316,0xad26}, new char[]{0x6c3a,0x4e9f,0xf271,0x1f60,0x0eea,0xd6e4,0x7ed7,0x6564,0x4d94,0x5919,0x7d68,0xad4d,0xe6d1,0x2660,0x57ea,0xa69f,0xd007,0x0f3a,0xad88,0x515c,0x200f,0x5661,0xf137,0xc3ed,0xa265,0x7754,0xdd89,0x0165,0x5e64,0xb26b,0xad93,0x6cfe,0x8a66,0xfd84,0x0215,0xf9c6,0x35d7,0x9373,0x128c}).toString();
        static final String L21 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x404b,0x586b,0x5746,0x2345,0xa0ae,0xc314,0x914b,0x5c10,0xbd2e,0xe9fc,0x7232,0xfb84,0x8e17,0xe185,0x0672,0x5443,0x6815,0x527b,0x9c7a,0x30c4,0x39ce,0x134e,0x4733,0xfc3e,0x11fd,0x0790,0x1eb7,0x6477,0x9866}, new char[]{0x5705,0xa0c2,0x07f9,0x6414,0x4758,0x584b,0x3986,0x7267,0x523f,0xfba4,0x403f,0x1321,0xe9dc,0x5c71,0x1ec5,0x9c08,0x8e76,0x061c,0xe1e2,0xfc5b,0xbd0e,0x543a,0x30ab,0x9813,0x118f,0x6835,0x230d,0x911e,0xc350}).toString();
        static final String L22 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa657,0x8340,0xffcd,0x7409,0x73e0,0x67d7,0x1753,0xc834,0x16e0,0x39b3,0x16b2,0xfe8a,0xf3cb,0x06f8,0x7a83,0xa505,0xb32f,0x34a6,0x9f33,0x8612,0xacf3}, new char[]{0x16e2,0xfee3,0x069b,0x34cd,0xa677,0xc855,0xb341,0x9f13,0xa564,0x1683,0xf3a8,0x39d6,0x832e,0x1727,0x8632,0x67b4,0x7aec,0xac9f,0xffa2,0x7395,0x747b}).toString();
        static final String L23 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9dbd,0x0707,0x7505,0xd2f5,0x48db,0x8d9c,0xa836,0xe64c,0x0977,0x25fa,0x7aa7,0xb512,0x7db4,0x9e61,0xf343,0xdafc,0xacbd,0xde3f}, new char[]{0xa879,0xda8c,0xe629,0x9dd3,0x0957,0x8de8,0xf32b,0xb577,0x7a87,0x7de0,0x076f,0x48be,0x7568,0xde5a,0x9e41,0x258e,0xacdc,0xd297}).toString();
        static final String L24 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x890a,0xe59b,0x2ae2,0x2596,0x00d2}, new char[]{0x0081,0x2a96,0x25f3,0x897a,0xe5bb}).toString();
        static final String L25 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb9d0,0xb4cf,0x30a6,0xadfd}, new char[]{0x3086,0xb4a0,0xad9b,0xb9f0}).plainString();
        static final String L26 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf248,0x0844,0x57f1,0x3a10}, new char[]{0xf21b,0x579a,0x3a79,0x0834}).toString();
        static final String L27 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe2a0,0x1669,0xd981}, new char[]{0xe28e,0x1647,0xd9af,0x1e79}).plainString();
        static final String L28 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3151}, new char[]{0x316b,0xa35d,0x6d8a,0xd71e}).plainString();
        static final String L29 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7966,0xae68,0xe522}, new char[]{0xe54d,0xae06,0x795c,0xd40b}).plainString();
        static final String L30 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0325,0x9e2a,0x34da}, new char[]{0x34b2,0x9e5c,0x031f,0x3ec3}).plainString();
        static final String L31 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xfca8}, new char[]{0xfc85,0x6ea0,0x979a,0x02ed}).plainString();
        static final String L32 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xad63}, new char[]{0xad48,0x48cc,0x2e29,0xb3ef}).plainString();
        static final String L33 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x296a,0xfc4c,0xc10d,0xa499}, new char[]{0x294f,0xfc62,0xa4a8,0xc16b}).plainString();
        static final String L34 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xfe86,0xc9d1,0x4f84,0xc2f6}, new char[]{0xc9f4,0x4faa,0xc2c4,0xfee0}).plainString();
        static final String L35 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x49a4,0xd9fe,0x3e35,0xc5de,0xa43b,0x4b6b,0x8dbc,0x47c0,0x4021,0x5831,0x9fc2,0x066b}, new char[]{0x0627,0x8dd5,0x5840,0x49d1,0x4b02,0xc5ba,0x3e15,0x4066,0xa457,0xd99f,0x9fb1,0x47b3}).plainString();
        static final String L36 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xde57,0x53d0,0xf4a7,0xc4a2,0x905d,0x2cfc,0xd1de,0x69be,0xa9e1}, new char[]{0x2ca9,0x69f7,0x907d,0x5383,0xde38,0xd1ab,0xf4c9,0xa985,0xc4d1}).toString();
        static final String L37 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x14ed,0x96ac,0x124f,0x4339,0x52e7,0xfe96,0xac51,0xfd7e,0xeecd,0x60b1,0x88a1,0x704a,0x6871}, new char[]{0xfd2a,0xfef9,0x435e,0x6816,0x1481,0xac34,0x6091,0xee9e,0x1220,0x703f,0x88cf,0x5283,0x96df}).toString();
        static final String L38 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2541,0x821c,0xc193,0x32f0,0x073b}, new char[]{0x32a0,0x827d,0x252f,0xc1f6,0x0757}).toString();
        static final String L39 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc0dd,0xfe44,0xe35a,0x433c,0xc077}, new char[]{0x437f,0xe33b,0xc005,0xfe20,0xc0ae}).toString();
        static final String L40 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf645,0x3d79,0x6191,0xa880,0x4b91,0x2ad8,0x392b,0x1f77}, new char[]{0xa8c4,0x4be3,0x1f18,0x61e1,0xf621,0x2ab7,0x395c,0x3d17}).toString();
        static final String L41 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xec67,0xe386,0xba62,0x2978,0x0016,0x437b,0x7c22,0x7764,0xa84e}, new char[]{0xe3c1,0xba37,0xec2e,0x2958,0x7c71,0x0062,0x4302,0x7708,0xa82b}).toString();
        static final String L42 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa833,0x9cbd}, new char[]{0xa850,0x9c87,0x82d4,0x2351}).plainString();
        static final String L43 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xcd07,0xf8ad,0xc625,0xf0c2}, new char[]{0xcd73,0xf8cc,0xc647,0xf0ba}).plainString();
        static final String L44 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0dc8,0x2015,0xb554,0x7d7b,0x87ba,0xb419,0x44bf,0xc27d,0xd7bf,0x6e46,0xcbff,0xe4f8,0xa422,0xe277,0xbe15,0x3783,0xf9ba,0x565a,0xcf6a,0x9345,0x5560,0x250d,0x6027,0xd485,0xc2e3,0x0def,0x3c21,0x535f}, new char[]{0x6073,0xf9c3,0xb524,0xbe70,0x449f,0xe216,0x879a,0x7d15,0xb478,0x37ee,0x5505,0x5676,0xc2c3,0xcbba,0xcf04,0xd7cb,0x3c44,0x2067,0x0dcf,0x532b,0xc212,0x6e66,0x9324,0x2569,0xd4e1,0xe4d6,0x0de6,0xa40c}).toString();
        static final String L45 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc28c,0xd183,0x6a85,0x878d,0xf66e,0x45cf,0x472d,0x1594,0xc522,0x195c,0xf509,0xf99c,0xdd44,0x6f8b,0xb6d2,0x1448,0xd857}, new char[]{0x6fd8,0xc2e9,0xf60f,0xdd36,0x6ae6,0x1420,0xf9bc,0xd83a,0x4742,0xd1e7,0x15e1,0xb6be,0x87e8,0x192f,0x45e1,0xc50c,0xf527}).toString();
        static final String L46 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5c03,0x38c5,0x9214,0x669d,0xd180,0x6199,0x65a3,0xdc53}, new char[]{0x5c46,0x65c7,0x38ac,0x61ed,0x66f4,0xdc3d,0xd1e7,0x9234}).toString();
        static final String L47 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x385f,0x5896,0x023c,0xec67,0xcaa4,0xb0c4,0x06af,0xce22,0xf7eb,0xed34,0xf8ea,0x600d,0x23f2,0x2d11,0x1f05,0xe139,0x0a09,0xf538,0xbe17,0x03e9,0x7178,0x01dc,0x6290,0x53f0,0xd89c,0x6584,0x2184,0x31d2,0xf76a,0x98f9,0x4b4e,0xabd9}, new char[]{0x068f,0x62b0,0x58be,0x3197,0xec09,0x65f0,0xf55d,0x4b3c,0x0a29,0x5384,0x9896,0xe119,0xd8ef,0x025d,0xb0b2,0xed51,0xce0e,0x21a4,0x2d54,0x039a,0xf889,0xabf9,0x2386,0xcacb,0xf74a,0x606e,0x1f64,0x01b2,0x383c,0xf78e,0x7114,0xbe3e}).toString();
        static final String L48 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x73a2}, new char[]{0x73de,0x8908,0x46f4,0xac75}).plainString();
        static final String L49 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7021}, new char[]{0x7059,0x35ca,0xec90,0xcd5f}).plainString();
        static final String L50 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x89fb,0x79b5,0xc3ff,0x38e1,0x70a3,0xe474,0x3611,0x60a0,0xed8d,0x9470}, new char[]{0x60ee,0xe41b,0x7995,0x9400,0xc393,0x3880,0x8982,0xede8,0x70d1,0x3662}).toString();
        static final String L51 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8ef7,0x216c,0x0647,0x75f0,0xf406,0xf358,0x18b7,0x3ec0,0x1587,0x0f9e,0x47a0,0x5d98,0xdbd1,0xfbf2,0xff29,0x2e9e,0x50d9,0x255f,0xe149}, new char[]{0x5089,0xff5b,0x0ffb,0x0634,0x252c,0x2ebe,0xe10c,0x18d9,0xfb86,0x15e2,0xf474,0x8ed7,0x5dec,0x759f,0x214c,0xf339,0xdbb5,0x3ea4,0x4780}).toString();
        static final String L52 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x59f1,0xa2d9,0x3315,0xb064,0x4d2b,0x36f4,0x125c,0x667a,0x1dae,0x395d}, new char[]{0x121d,0x5981,0x660a,0xa2bc,0x1dcf,0x3686,0x393c,0x4d45,0xb007,0x3370}).toString();
        static final String L53 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3979,0x088c,0x2f4b,0xe5df,0x45d4,0xe4f3,0x21c5,0x9523,0xa37b}, new char[]{0x08c8,0xe5be,0x390b,0xe498,0x21e5,0x956e,0xa314,0x45b0,0x2f2e}).toString();
        static final String L54 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa8ed,0xcf38,0x1b87,0x3f60,0x1fca,0xb44b}, new char[]{0xa8ac,0x1fa9,0x3f03,0xb42e,0xcf56,0x1bf3}).plainString();
        static final String L55 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe82a,0xbc6b,0x968c}, new char[]{0xe862,0xbc1e,0x96e9,0x9c38}).toString();
        static final String L56 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xeb9a,0x5eff,0x8cf9,0x3bb3,0xd000,0x0d7a,0x1eb4,0x5110,0xa1b7,0x6c38}, new char[]{0x8caa,0x6c59,0x0d0e,0xebef,0x3bc1,0xd061,0x5e8b,0xa1de,0x517f,0x1eda}).toString();
        static final String L57 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x438a,0x2ae4,0x2494,0x058d,0x594b,0x7876,0x54a3,0x5ebb,0x75f4,0xab6f}, new char[]{0xab2d,0x24e6,0x54ca,0x2a83,0x781e,0x43fe,0x5925,0x7591,0x05fe,0x5ec8}).toString();
        static final String L58 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xbc03,0xe772,0xf88c,0x8282,0x948b,0x68ec}, new char[]{0x94c3,0x6889,0x82fa,0xbc39,0xf8ac,0xe751}).toString();
        static final String L59 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0f4f,0xc6e3,0x908b,0x57d6,0x98a8,0x5c24,0x0931,0x6009,0xd82b,0x5d3d,0x1a67,0x0f26,0x51ac,0xea2b,0x562d,0xccef,0x6213}, new char[]{0x625b,0x51c9,0xea53,0xd80b,0x5d5e,0x98c7,0x5641,0x57b9,0xc696,0x1a15,0x0911,0x5c04,0x0f05,0x90ae,0xccdf,0x0f79,0x6051}).plainString();
        static final String L60 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x888b,0xdc27,0x6963,0x6170}, new char[]{0x88ae,0xdc17,0x6146,0x693b}).plainString();
        static final String L61 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xba43,0x46e4,0x02cd,0x5bb2,0xe43f,0xd087,0x3c57,0x7757,0x620f,0xd518,0xbee8,0x21a4,0x4751,0x772c,0x8fa0,0x206c,0xf93d,0x40b6,0x13ff}, new char[]{0xd0c6,0xf953,0x6266,0x40db,0xbe89,0x4725,0xd57d,0xba27,0xe41f,0x202e,0x02ac,0x3c34,0x8fcb,0x21c3,0x775e,0x468b,0x7722,0x5bdc,0x139b}).toString();
        static final String L62 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2219,0xa654,0xc08a,0xb753,0x1740,0xf5dc,0x2066,0xa641,0xb57b,0x48b5,0x456d,0x827f,0x70d9,0xab62,0xd246}, new char[]{0x48f7,0xa635,0x450e,0xb738,0x8218,0x1732,0x2276,0x2013,0x70b7,0xa625,0xb55b,0xf59e,0xd22a,0xab17,0xc0f8}).toString();
        static final String L63 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9a98,0xb6fe,0x40ab,0x701b,0x281e,0x675f,0xac4b}, new char[]{0x705e,0x9af6,0x673e,0x40c9,0xac27,0xb69b,0x287a}).toString();
        static final String L64 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3848,0xeb0b,0x9d16,0xd3ac,0x4cae,0xa269,0x9e43,0x6a47,0x9074,0x7759,0x4811,0x87e2,0x4c1e}, new char[]{0x381c,0xd3c5,0x9e2d,0xa21d,0x6a67,0x4cfd,0x9000,0x8790,0x4c7b,0x487f,0x773e,0xeb7f,0x9d7e}).toString();
        static final String L65 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4342,0xf484,0xb853,0xdae6}, new char[]{0xf4c6,0xda8a,0xb826,0x4330}).toString();
        static final String L66 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1434,0x32af,0x8e77,0x359f,0x13cb,0xbb5b,0xf39e,0xa359,0x3e52,0xd612,0x089f}, new char[]{0xa31b,0x3e3e,0x13be,0xbb29,0x08bf,0x35cf,0x8e16,0xf3ed,0x1447,0xd677,0x32dc}).toString();
        static final String L67 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2cf7,0xebc3,0xe9cc,0x590d,0xa0a0,0x6898,0xa55f,0x7009,0x8425,0x6227,0x8add,0x8cc9,0x476d}, new char[]{0x8a91,0x624e,0x68e9,0x2c82,0x4704,0xa0c4,0x8ce9,0x7044,0x844a,0x5979,0xa536,0xebac,0xe9a2}).toString();
        static final String L68 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x11b0,0xe4d6,0x7d3e,0x6958,0xfd47,0xf1d4,0x0ff0,0x5b34,0xc705,0x2239,0xcada,0x9d9d,0x7849,0x5ef6,0xc778,0x087f,0x515c,0xcb8d,0xb099,0x5653,0xe4fc,0x84b8,0x1e05,0x4636,0xb178,0x0862,0xb3ef,0x67ac,0xf32e,0x7d1f,0x32d7,0xc6d5,0x7f04,0x182d,0x478a}, new char[]{0x2274,0x7d47,0x0842,0x47de,0xb387,0x1848,0xfd2a,0x7d7a,0xb10b,0xc725,0x4616,0x5e41,0xe4f6,0x6978,0xc70a,0x5b5d,0x84df,0xe494,0x783d,0x6781,0xb0fa,0x11dc,0x5135,0xf34d,0x7f6f,0x1e25,0x5627,0xcab5,0xc6f5,0x081b,0xf1b1,0xcbe1,0x9df8,0x0f84,0x32b2}).toString();
        static final String L69 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe0c3,0x7919,0x8d2f,0x092c,0x89b2,0x499d}, new char[]{0x8d61,0x7978,0xe0ae,0x0949,0x8988,0x49bd}).toString();
        static final String L70 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd9e8,0xeced,0xedbf,0x1ed3,0x010e,0x99d5,0xd9d8,0x6811,0xea64,0x0c6d,0x8e2f,0xbdfe,0x257b,0x4a9c,0x32b4,0x4110,0x94c3,0x109b,0xb5c5,0xcb02}, new char[]{0x413b,0x94e3,0x10bb,0x4acf,0xd9b9,0xb5b3,0x1eb6,0x99f5,0xd98b,0x6864,0xcb70,0x0c1f,0xedda,0xea0a,0xec99,0x8e0f,0xbd92,0x0161,0x32db,0x2510}).toString();
        static final String L71 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xdfd1,0xc9f1,0xcf17,0xc54e,0x7051}, new char[]{0xcf37,0x7071,0xc56e,0xc946,0xdff1}).plainString();
        static final String L72 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x87de,0x6107,0x7474,0x25b8,0xbade,0xa318,0xea97}, new char[]{0xba8e,0xeae5,0x7411,0x87ad,0x6162,0xa36c,0x25cb}).toString();
        static final String L73 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x35aa,0xc0a8,0x4058,0xb166,0xaf75,0x3cdd,0x3bad}, new char[]{0xc0ea,0xaf00,0x35c8,0xb104,0x3bc1,0x403d,0x3cae}).toString();
        static final String L74 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x90c1,0x5216,0xed31,0xaf15,0x02d5,0x0d5e}, new char[]{0x0297,0x90b4,0xaf77,0x0d3c,0xed5d,0x526f}).plainString();
        static final String L75 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf28b,0xdf54,0xf34c,0xe4ea,0x6269,0x87ee}, new char[]{0xf31f,0xe48b,0x6202,0xf2fe,0x879c,0xdf35}).toString();
        static final String L76 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb739,0x5adc,0xfc46,0x922e,0x95ab,0x934e}, new char[]{0x9302,0xfc2f,0x95da,0xb74c,0x9247,0x5ab8}).plainString();
        static final String L77 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7088,0x4ff2,0xa957,0xa75d,0x1fcc}, new char[]{0x70c7,0xa934,0xa738,0x4f93,0x1fa2}).toString();
        static final String L78 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x06ad,0x8e13,0x8ff6,0x07c1,0x090b,0xf036,0x4363,0x6c96}, new char[]{0x06e0,0x8e7a,0x8f92,0x07af,0xf05f,0x6cf1,0x430b,0x097f}).toString();
        static final String L79 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8349,0x78b4,0xf193,0xc21f,0xc242,0xe297}, new char[]{0xc216,0xe2fe,0x78da,0xc26b,0x832c,0xf1f7}).plainString();
        static final String L80 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc793,0x0b88,0xb620,0x3ba0}, new char[]{0x0bc5,0xc7fa,0x3bce,0xb654}).toString();
        static final String L81 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x408a,0x47ad,0xe3a6,0x4d8d,0x0f30}, new char[]{0x47ee,0x4de1,0xe3c3,0x40eb,0x0f42}).plainString();
        static final String L82 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa8e9,0x38f5,0x7702,0x46f7,0xd487,0x6117}, new char[]{0xa8ba,0x7777,0x389b,0xd4f4,0x6172,0x4683}).toString();
        static final String L83 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb3d0,0xcca2,0xa88f,0xc741}, new char[]{0xb39c,0xa8ea,0xcccc,0xc732}).plainString();
        static final String L84 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb4bc,0x2131,0x7f6f,0xaebd,0x97ad}, new char[]{0xaefb,0x2143,0x7f00,0x97de,0xb4c8}).toString();
        static final String L85 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4d7e,0xbfb0,0xcb8e,0xa5bd,0xe182,0x6e83,0xb4b8,0xc080,0xc33a,0xe939,0xda33,0x338d,0x553e,0xa10e,0x1ced,0xdea2,0x4448,0xec23,0xe15b,0x1036,0xa19f,0x43a4,0x169f,0xfba5,0x1fd7,0x7f3b,0xc8c8,0xfa62,0x4d6c,0x60cf,0x3b44,0x9264,0x37dc,0x1804}, new char[]{0x4d3c,0xbfc2,0xc355,0x43c2,0xdecb,0xe137,0xb4dd,0x1877,0xa1bf,0xda13,0x7f8c,0x6ea3,0x9244,0x33ff,0x105f,0x3b23,0xfa0a,0x554a,0xe1af,0xc0e3,0x37b0,0xa167,0x1c8e,0x60a4,0x4468,0x16eb,0x1fb8,0xcbae,0xa5d9,0xe95c,0xfbc9,0x4d1b,0xc8bc,0xec46}).toString();
        static final String L86 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xbaa4,0x790c,0xf835,0x3fa3,0x66cc,0x713a,0x00ee,0x0045,0xb10b,0x12a2,0x9755,0xb1ab,0x8c4a,0x77ed,0x3d10,0x2827,0x1319,0x4a3f,0x862e,0xe6c9,0x2d0e,0x9719,0x04c6,0xdbc5,0xb57f,0xd525,0x04ed,0xb6ae,0x16c7,0xb9aa,0x5cc8,0x18ed,0x49bf}, new char[]{0x77c6,0x860e,0xb18b,0x00bd,0xb51e,0x49c9,0xb6cb,0xb98a,0xb168,0x4a4a,0x16b5,0x2855,0x715f,0xdbab,0xd551,0xe6e9,0x8c29,0x002a,0x04a8,0x12c4,0x7965,0x3d77,0xf815,0x2d6f,0x049e,0x9775,0x9769,0x136b,0x1882,0x3fc5,0xbacd,0x5ca4,0x66a9}).toString();
        static final String L87 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb5ea,0xf0aa,0x2251,0x6480,0x7a09,0xf58f,0x25d0}, new char[]{0x7a59,0x2223,0x64ef,0x25b6,0xb583,0xf5e3,0xf0cf}).toString();
        static final String L88 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd4cd,0xa321,0x28e3,0xb6e6,0xf78f,0x5d7c,0x746e}, new char[]{0x7422,0xf7e0,0xb687,0xd4a9,0xa344,0x5d18,0x28c3}).toString();
        static final String L89 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x74d7,0x252e,0xd4d1,0xd739,0x35e9,0x1086,0x0d42,0x0ba3,0xf37c,0x75f3,0x3337,0xafc2,0xe58c,0x50db,0x0db8}, new char[]{0x0be0,0x0dd7,0xf309,0xe5e0,0xafa6,0xd719,0x74b9,0x3586,0x3343,0x10a6,0x759f,0x50b4,0xd4b0,0x0d26,0x250e}).toString();
        static final String L90 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1e11,0x44f8,0x8177,0xaf82,0xd049,0xb866,0x9404,0xd68f,0x8484,0x49b2}, new char[]{0x49e1,0xb80e,0x4499,0x1e63,0xafe7,0x9424,0x84c7,0xd026,0xd6eb,0x8112}).toString();
        static final String L91 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9e7a,0x0e1f,0x1695,0x974d,0x532b,0x70ba,0xc89f}, new char[]{0x70f9,0x9e15,0x0e6f,0x9724,0x16f0,0x534f,0xc8bf}).toString();
        static final String L92 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9366,0x51eb}, new char[]{0x9346,0x51c3,0xf735,0xa03a}).plainString();
        static final String L93 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x218a,0xc205,0x97f9,0x65b1,0xb1ec,0xa311,0x27cc}, new char[]{0x97d9,0xa372,0x21e2,0xb18d,0x27be,0xc276,0x6598}).plainString();
        static final String L94 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc7e9,0xf30b,0x38a5,0x027c,0x374c,0xe582,0x96e6,0x9a60,0x647e,0xa122,0x2968,0xf6a4,0x745f,0xafb7,0x9f43,0x838d,0x9196,0x142c,0x8307,0xdcc4,0xc62b}, new char[]{0xf6e7,0xdcab,0xa157,0x7433,0x91f2,0x83ad,0x8369,0x38ca,0x9f37,0x2948,0x021f,0x9694,0x3729,0xafd6,0xe5f6,0x641b,0x9a40,0xc648,0xc786,0xf36f,0x1449}).toString();
        static final String L95 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9693,0x84a7,0x02d0,0xdab7,0x8f85,0xb9e1,0xd9ff,0x53f9,0x5c23,0x45e0,0xa2b5}, new char[]{0xdaf8,0x8ff5,0x5c46,0x96fd,0x53d9,0xa2f3,0xb98e,0x458c,0x84c3,0x02b5,0xd98d}).toString();
        static final String L96 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9d65,0x6484,0xe1db,0xbe43,0x979a,0x9f73,0x8476,0x9821,0x3d3f,0x0745,0xd40a,0x9546,0xb391}, new char[]{0x9873,0x64e1,0xd466,0x9529,0x9f12,0x9d01,0x3d1f,0x97d9,0xe1b4,0xbe2d,0xb3f7,0x072c,0x8411}).toString();
        static final String L97 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1bdf,0x78e1,0x010d,0x13f5,0x9940,0x01a8,0x8b58,0x7589,0x0d72,0x676f,0x4087,0x0798,0xb927,0x0691,0x6243,0x33cd,0xcd5c,0x26c2,0x6025,0x40d8,0x9728,0xda9f,0xb675,0x7e07,0x531d,0x9c9c,0xd3bb,0x57c0,0xda72,0x812b,0xdfb1,0xfe55,0x467f,0x75f2}, new char[]{0x0d21,0x759a,0x40b9,0x9cee,0x461a,0x06b1,0x7e27,0x53aa,0xb907,0x40a7,0xdfde,0xd3c9,0x012d,0x815e,0x8b2b,0xfe30,0xda52,0x78a2,0x604a,0x33bd,0x1ba6,0xdabf,0xb61a,0x139b,0x6263,0x01c9,0x07b8,0x9930,0x57b2,0x9747,0xcd3a,0x6706,0x26ae,0x75ec}).toString();
        static final String L98 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc2c2,0x6992,0x374d,0x9d98,0xc37f,0xe42d,0x6a28,0x6dde,0x3aa5,0xd25e,0x014d,0xfdfa}, new char[]{0x010e,0x3aca,0xd22e,0xe454,0x6a08,0xc281,0x3738,0xfd88,0x9dea,0xc31a,0x69fc,0x6daa}).toString();
        static final String L99 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9b27,0xa297,0xa24a,0xf13b,0xaf47,0x01ef,0x3b49,0xbe5a}, new char[]{0xbe19,0xa225,0x019f,0xf152,0x9b42,0x3b2d,0xa2b7,0xaf6f}).toString();
        static final String L100 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0fa6,0x0f93,0x6363,0xfd87,0x814e,0x20dc,0x0225,0xe9dd,0x0882,0xe56f,0x3d00,0x3ab9,0x8e0f,0xd6da,0x6996,0x2ebe,0x760a,0xce48,0xca93,0xa9d6,0x83c0,0x8d6b,0x998a,0x736d,0xc4e7,0xf86c,0x2a0b}, new char[]{0x6343,0xe50c,0xa9be,0x2edf,0x3acb,0x08f1,0xcaba,0x83e0,0xce65,0xf84c,0x8d1b,0x766b,0x3d73,0x99fe,0x0ff6,0x0205,0xe9b4,0x0fd2,0xfda7,0x2a6a,0x8120,0xd6a3,0x8e78,0x69fe,0x7308,0x20ae,0xc482}).plainString();
        static final String L101 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7ddd,0x253e,0x5406,0x199d,0x59c0,0x5908,0xe46b,0x236e,0xe98b,0x808b,0xe21d,0x6a2b,0x6e85,0x4bf6,0xcb93,0xffe7}, new char[]{0x2327,0xe9e6,0x5476,0xe404,0xff95,0x4b82,0x5928,0x19de,0x80e7,0x59a9,0xe26d,0x6a49,0x7db2,0x255f,0x6ef7,0xcbf7}).toString();
        static final String L102 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xbdaf,0xecfa,0x22ab,0x16d1,0xa283,0x1c88,0x02fa,0x5f2e,0xb823,0x4107,0x2554,0x8f4f,0x6e89,0xc686,0xc7ba,0xf39a,0x09f3,0x829e,0x9dfa,0x04d2,0x539a,0xfa90,0x8607,0x4631,0xb524,0x17ec,0x1c07,0x0e09,0xe216,0x85f7,0x67f4,0x9263,0xb966,0x5626,0xe681,0x809c,0x496b,0x5778,0xa94a,0xa5ea,0xae7b,0xbe36,0x8255,0xb1af,0x7b39,0x1611,0x5552,0x4173,0x635c,0x0ad2}, new char[]{0xb92f,0x16bc,0x5522,0x0e66,0x1cfa,0x570c,0xa92f,0x1c63,0xe6a0,0x5f0e,0x82d1,0xb548,0x8f2b,0x4153,0x0ab1,0x4904,0x6332,0xb1c9,0xec93,0xa2e4,0x8627,0x0289,0x9202,0xc6f0,0x5643,0x8593,0x09d3,0xbdce,0x1662,0x7b19,0xc7d8,0xb842,0x178f,0x416c,0x6efc,0x22db,0xbe1b,0x6796,0x4654,0xfaf6,0xa585,0xae09,0xe273,0xf3b7,0x823c,0x04bf,0x2524,0x53f5,0x9d88,0x80e8}).toString();
        static final String L103 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4591,0xc287,0x12a3,0xdec7,0x4d5f,0x0211,0x79d5}, new char[]{0x7992,0x12c6,0x45ff,0xc2e2,0x0263,0xdea6,0x4d33}).toString();
        static final String L104 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xbba1,0xd021,0xb6ad,0x0077,0x763b,0xd372,0xf307,0xc823,0xed0c,0xc2ea,0x4c7a,0x487b,0x32e9,0x5f2a,0xfdbe}, new char[]{0x7668,0x4814,0xbbd4,0xf377,0xfd9e,0xd072,0xb6c2,0x5f5f,0xed62,0xd316,0x4c5a,0xc2ba,0x0016,0xc840,0x3282}).toString();
        static final String L105 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xca15,0x00b2,0xdc77,0xb303,0x915d,0x7c77,0xd27f,0x3531,0x9b10,0x1762,0x8c82}, new char[]{0xb350,0x3550,0xca63,0x9b75,0x917d,0x00f1,0x170d,0x7c19,0xd219,0xdc1e,0x8ce5}).toString();
        static final String L106 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6043,0xd79a,0xa7cf,0x4f74,0xf373,0xc18e,0xa6f2,0xa9d5}, new char[]{0xf330,0xa693,0xa7bd,0x4f10,0xa9f5,0x6004,0xd7cf,0xc1c7}).plainString();
        static final String L107 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8537,0x3f6b,0x5696,0xdc2b}, new char[]{0x3f23,0x8552,0xdc47,0x56e6}).plainString();
        static final String L108 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe1fb,0xfbba,0xc728,0x58fc,0x9ebb,0x5ebc,0x549b,0x9c01,0xd292}, new char[]{0x9ee8,0x5ed9,0xfbd6,0xe19d,0xc705,0x9c55,0x54fe,0xd2e1,0x5888}).toString();
        static final String L109 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xede2,0x9174,0x09df,0x5f53,0x369f,0x3be6,0x41da,0xfcb6,0x79f8,0xfd3f,0xf5a5,0x9730,0x3d41}, new char[]{0x4188,0xfd5a,0x9107,0x36eb,0x3b87,0x9742,0xed96,0x5f73,0x0998,0xfcc3,0xf5cc,0x799c,0x3d24}).toString();
        static final String L110 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x80ba,0x95f0,0x9e4f,0x50bd,0x6154,0x7fa1,0x9dfc,0xa596,0x1994,0x331e}, new char[]{0x19c7,0x3375,0x50d4,0x9e3f,0x7f81,0x9dbb,0x9585,0x80d3,0x6130,0xa5f3}).toString();
        static final String L111 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe40b,0x14b2,0xffcb,0xc622,0xf935,0x5b2c,0x4fad,0x5e6a,0xb915,0xd18d,0x446c,0x6915,0x6a5b,0x0fc1,0x661a,0x0a1d,0x2561}, new char[]{0x2520,0x6971,0x5b48,0x6a7b,0xc675,0xd1ec,0x0a64,0xffbb,0x14dd,0x4405,0xf95b,0x5e1e,0x4f8d,0x0f89,0xe46e,0xb967,0x667f}).toString();
        static final String L112 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb282}, new char[]{0xb2ef,0x91d2,0xed42,0xcc5b}).plainString();
        static final String L113 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xfe9b,0x0870,0xb61a,0x0214,0x7576,0x7e96,0x85b2,0x9ec5,0xb36f,0x4530,0xad25,0xcbca,0x4690,0x5cf7,0x42cf,0x9893}, new char[]{0xcb8c,0x0819,0x98fd,0xb30b,0x0271,0x7504,0xb63a,0xfed3,0x9eac,0xad56,0x4544,0x46ff,0x42bd,0x5c8e,0x8592,0x7ebe}).toString();
        static final String L114 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xbd34,0x1778,0xda87,0xbe6c,0xc65c,0x6f82,0xdaea,0x906c,0x240f,0xae22,0x9227,0x0d6b,0xdf2a,0xacbb,0x8daa,0x9b68,0x0696,0x9268,0x14c7,0x894a,0x4557,0x88ca,0xed05,0x2a2e,0x6d64,0x2226,0xa758,0x0ea8,0x3748,0x9639,0xc371,0xc84b,0xd970,0xd126,0x0313,0xe4e8,0x7456,0x444e,0x73b4,0x3193}, new char[]{0x739d,0xed25,0x3768,0x033e,0xe4c8,0x31b3,0x88a9,0xa734,0x743f,0x14a4,0xc820,0xac81,0x4577,0x8ddd,0x892b,0xc625,0x2256,0xdf45,0x9b01,0x1716,0xd904,0xbd18,0xc351,0x0d19,0x9650,0xbe0b,0x9200,0x9253,0x6d49,0xdae4,0x2a42,0x0ec1,0xd145,0x9007,0x6fb8,0x446e,0xda89,0xae4d,0x247f,0x06ef}).plainString();
        static final String L115 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0683,0xe7a9,0xf94e,0x9cb2,0x3f7d,0xf847,0x7f62,0x0d00,0x5265,0x7ace,0x7671,0x2a53,0x5f4f}, new char[]{0xf804,0x5f23,0x9cd7,0x7f03,0x7abc,0xf96e,0x0d48,0x3f14,0x06f0,0x7605,0x2a3c,0xe7db,0x521c}).toString();
        static final String L116 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xcc5f,0xed16}, new char[]{0xed3a,0xcc7f,0x8ec3,0x7914}).plainString();
        static final String L117 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x008e,0x3181,0x960a}, new char[]{0x962a,0x31ff,0x00ae,0x781c}).plainString();
        static final String L118 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb38d,0x599a,0x302e,0x209a,0x7cbc,0x73ed,0xe17f,0x38ae,0xf032,0x295d}, new char[]{0x20dc,0x38dc,0x7cd5,0xf057,0x7383,0x2939,0x305d,0xb3ad,0x59ba,0xe157}).toString();
        static final String L119 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8df5}, new char[]{0x8ddc,0xa407,0x1014,0x8020}).plainString();
        static final String L120 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb6d4,0x3091,0x8ad2,0x8c43}, new char[]{0x8c00,0x30fe,0x8aa2,0xb6ad}).toString();
        static final String L121 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa6e9,0x886a}, new char[]{0xa699,0x8850,0x017b,0xa956}).plainString();
        static final String L122 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3e09,0x72ca,0x414c}, new char[]{0x413c,0x72b9,0x3e33,0xe262}).plainString();
        static final String L123 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb764,0x854c,0x7fc4,0x4241}, new char[]{0xb705,0x422e,0x7faa,0x8576}).plainString();
        static final String L124 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe619,0x4bca,0xf617,0x6e9a}, new char[]{0xf676,0x6ef2,0xe66f,0x4bf0}).plainString();
        static final String L125 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1f2e,0x792e}, new char[]{0x7905,0x1f0e,0xe913,0x2cec}).plainString();
        static final String L126 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9dc9,0xd9b0,0x41f4,0xeacb,0xce75,0x10b6}, new char[]{0x41d4,0x10d3,0x9da7,0xce01,0xd9c2,0xeab2}).plainString();
        static final String L127 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3576,0x0e5e,0x2af2,0x9911,0xdecb,0x29f6,0xf378,0x968b}, new char[]{0x29d6,0x9974,0x2a9c,0x0e2a,0x96f9,0x351f,0xf31d,0xdeb8}).plainString();
        static final String L128 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc3ff,0xe856,0xdd7a,0x5414,0x46ee,0x489f}, new char[]{0x46bd,0x5475,0x48e9,0xdd1f,0xc39b,0xe876}).toString();
        static final String L129 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9174}, new char[]{0x9157,0x5444,0x5e38,0x9717}).plainString();
        static final String L130 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x928c,0x92a7,0xaf2d,0x64c0,0x8455,0x3a95,0xf31c,0x023f,0xf779,0x68e2,0x1614,0x2b59,0xbe58,0xdbaa}, new char[]{0x92f4,0x92ed,0xf36a,0xf71c,0x025b,0xdb8a,0x64b0,0x8427,0x3afa,0xaf4b,0x2b30,0xbe34,0x1671,0x68c2}).toString();
        static final String L131 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc4c7,0x7dfd,0x2416,0x92e5,0xba6a,0x63a9,0xbf27,0x5641,0x0c5c,0xad76,0xdc9d,0x513c,0x0197,0x722e,0x43d9,0xeffe}, new char[]{0x2438,0xbf09,0x92cb,0xc4ac,0x01b9,0xefd0,0x0c72,0x5112,0x566f,0x43f7,0x7d96,0x7200,0xba44,0xdcb3,0xad58,0x6387}).plainString();
        static final String L132 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1490,0x2394,0x6ddd,0xd49e,0x4d97,0xfc98,0x9096,0xb3da,0xbc3c,0x9acd,0x2c40,0x29df,0x8ff9,0x4b31,0x860b,0x14fa}, new char[]{0x9ae3,0x29f1,0x6df3,0x8f92,0x4b5a,0xbc12,0xb3f4,0x2c6e,0x23ba,0x14fb,0x4dfc,0x90b8,0x8625,0xd4b0,0x14d4,0xfcb6}).plainString();
        static final String L133 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2d3c,0xfec4,0x06ea,0xcda3,0x8f7f,0x3b8a,0x1f84,0xfd9d,0x64f8,0xd0b4,0x6785,0xe056,0xba4f,0x8885,0x41a2,0x5bec}, new char[]{0x8f51,0xcd8d,0x67ab,0xfeaf,0x0685,0x5b87,0x88ee,0x3be1,0xfdf6,0xba20,0x2d57,0x418c,0xe078,0xd09a,0x64d6,0x1faa}).plainString();
        static final String L134 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xba98,0x0670,0x2094,0xa317,0xfda4,0x060a,0x84c8,0x245c,0x36c7,0xf247,0xe722,0x3f77,0x7346,0x0890,0x7bf6,0xad93}, new char[]{0xf269,0xe70c,0xbaf3,0x08ff,0xadfc,0x061f,0x7b99,0xa378,0xfdcb,0x0665,0x2433,0x84a3,0x36e9,0x7368,0x3f59,0x20ba}).plainString();
        static final String L135 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3f1a,0xeabf,0xb6ec,0x77f2,0xff55,0x2efa,0x8b9e,0x6009,0xfebd,0xa51b,0x76cb,0xa883,0xd232,0xa631,0x1266,0xaafd}, new char[]{0xea91,0xd21c,0xff3e,0xaa92,0xa654,0xb683,0x8bf1,0x779d,0x3f75,0x1203,0xa8ec,0xfed6,0x6027,0x2ed4,0xa535,0x76e5}).plainString();
        static final String L136 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd244,0xeb9e,0x3f0c,0xf663,0x28fb,0x4026,0x106f,0x501a,0xff2f,0xdef8,0x516c,0x76d5,0x1366,0x8e9d,0xdd9b,0x153c}, new char[]{0xd26a,0x3f22,0xff44,0x1309,0xddf4,0x4049,0x288c,0xde8f,0xf60c,0x1553,0x76ba,0x8ef6,0x1041,0x5142,0x5034,0xebb0}).plainString();
        static final String L137 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6ae8,0xf5ea,0x4440,0x69f8,0x3f7e,0xda00,0xcc89,0x1122,0x8b6e,0x8afc,0x1df9,0x05b3,0x6154,0x1483,0x31e6,0x5839}, new char[]{0x446e,0x6ac6,0x3f15,0x613b,0x14ec,0xf59a,0xda77,0x8a8b,0x5849,0x05dc,0x8b01,0xcce2,0x31c8,0x110c,0x1dd7,0x69d6}).plainString();
        static final String L138 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8997,0xf89a,0x9db3,0x7883,0xfc41,0x96aa,0x6f2c,0x0e93,0x3214,0x0e65,0x535c,0x1700,0xb5c6,0xcbf8,0xa58b,0x5a33}, new char[]{0x5a1d,0x0e4b,0xb5ad,0x5337,0x0efc,0x3263,0x96c1,0xcb93,0x9dc4,0xa5e4,0xf8f1,0x176b,0x89b9,0x6f02,0x78ad,0xfc6f}).plainString();
        static final String L139 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x95d2,0xa2ca,0xbba9,0x6aee,0xd5ba,0x6bb4,0xedbf,0x4a33,0xd601,0xf2d0,0xcf96,0x29db,0x6af7,0xf473,0x52e8,0xd89e}, new char[]{0x95fc,0xf2fe,0xd66a,0xbbc2,0xd5d1,0x4a58,0x29b0,0x6bdf,0xcffd,0xa2a1,0xedd4,0x6a85,0x6a9c,0x5283,0xd8b0,0xf45d}).plainString();
        static final String L140 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x06c8,0x785c,0x8c90,0x0f08,0xa821,0xe761,0x7647,0x8f10,0x74db,0x27a5,0x85de,0xba93,0x43e6,0x2685,0xb2ea,0x9fe4}, new char[]{0xe74f,0xa84a,0xb288,0x0f6a,0x7830,0x27c9,0x8f72,0x4384,0x85bc,0x26e7,0x74b7,0x06a4,0x7625,0xbaf8,0x8cbe,0x9fca}).plainString();
        static final String L141 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xcbea,0xba5d,0x34ac,0x1a10,0xe7ae,0xf47b,0x3444,0x7815,0xd3ab,0xfc81,0x2a2f,0x610e,0x01b8,0x73ab,0x2cd7,0xc22f}, new char[]{0xe7c5,0xf419,0xd3c9,0x73c7,0x7877,0xc24d,0x2a44,0x1a7b,0xba36,0x342f,0x616c,0x2cb5,0x34c0,0x01da,0xcb81,0xfcaf}).plainString();
        static final String L142 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xce77,0xa475,0xe59a,0x6f8d,0x1807,0x64e5,0xd5f1,0x67fe,0xd4a2,0x173e,0xbedc,0x946e,0xf008,0x9f21,0xe4b0,0xb798}, new char[]{0xf063,0xb7fa,0xd593,0x175c,0x1865,0xe4db,0xce00,0x6789,0x6ffa,0x9419,0xbeb7,0xa417,0xe5f8,0xd4c0,0x648e,0x9f0f}).plainString();
        static final String L143 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0565,0xf5b7,0xb563,0x908a,0x3ab2,0x448b,0x6a41,0xf4b8,0x5423,0xc009,0x311a,0x4b81,0x8281,0xbd1b,0x131e,0xd417}, new char[]{0xf5dc,0xc06d,0xbd79,0x6a2f,0x3ad0,0xb501,0x4bea,0x82ea,0x44e0,0x5448,0x137c,0xd479,0x3178,0x90ee,0x050e,0xf496}).plainString();
        static final String L144 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xffbb,0xf39b,0x44ae,0x6037,0x5f58,0xbe72,0x0f26,0xdbab,0xbef6,0xaeb9,0x5ba2,0x7733,0x5665,0x8e44,0xd93c,0x2a0b}, new char[]{0x8e6a,0x0f4d,0xbe92,0xffdf,0xbe1c,0x5f3c,0x5601,0xd958,0x7757,0xaed7,0xdbcf,0x2a6f,0x605c,0x4480,0xf3b5,0x5b8c}).plainString();
        static final String L145 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa358,0x6cdd,0x468c,0x8959,0x90b0,0xbda6,0x07c1,0x4f32,0xb592,0x4a1f,0xcaa6,0x89b5,0x9dc9,0x0178,0x8859,0x7c3e}, new char[]{0x9de7,0x909e,0x6cb6,0x46e7,0x4f59,0x8832,0x8932,0x4a74,0xbdcd,0xb5f9,0xa333,0x7c55,0x0113,0x899b,0xca88,0x07ef}).plainString();
        static final String L146 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa09a,0x7940,0xf909}, new char[]{0x792e,0xf926,0xa0fb,0x9d2f}).plainString();
        static final String L147 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x76ec,0x0219}, new char[]{0x0254,0x76ae,0x41f7,0x22d4}).plainString();
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT2 {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe18c,0xd843,0xcfbc,0xa6d3,0x419c}, new char[]{0xa69c,0x41ee,0xd821,0xcfd5,0xe1f8}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x94f5,0x556d,0x00f9,0x428a,0x5fc1,0x1c6e,0x65d0,0x8d82,0x9436}, new char[]{0x9441,0x0098,0x5519,0x5fa4,0x9487,0x42a4,0x1c02,0x8ded,0x65bc}).plainString();
    }
}
