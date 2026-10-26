package com.utilitymod.gui;

import com.utilitymod.core.Module;
import com.utilitymod.core.ModuleManager;
import dev.koifih.client.render.Draw;
import dev.koifih.client.render.Scissor;
import dev.koifih.client.render.Text;
import dev.koifih.client.util.Colors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;

/**
 * Fifth GUI style ("water.lol"): dark slate panel, icon sidebar of categories with Editor / Configs / Themes
 * at the bottom, a search bar, playstyle tabs (All + the kits), and a two-column grid of module cards
 * (dimmed when off). Click toggles, right-click opens the settings sheet, two buttons below the panel.
 */
public final class WaterGui extends Screen {
    private static final int BG = 0xFF161A22, SIDEBAR_LINE = 0xFF262B36, CARD = 0xFF262B36, CARD_HOVER = 0xFF2D3340,
            FIELD = 0xFF1C212B, FIELD_LINE = 0xFF353C4A, INK = 0xFFE8ECF3, MUTED = 0xFF8C93A3, FAINT = 0xFF5D6473,
            TAB_ON = 0xFF2C3442, TAB_TEXT_ON = 0xFF5FA8FF;
    private static final float BASE_W = 580, BASE_H = 360;

    private record Cat(String label, String icon, com.utilitymod.gui.Category category) {}
    private static final List<Cat> CATS = List.of(
            new Cat(LT.L1, LT.L2, com.utilitymod.gui.Category.COMBAT),
            new Cat(LT.L3, LT.L4, com.utilitymod.gui.Category.MOVEMENT),
            new Cat(LT.L5, LT.L6, com.utilitymod.gui.Category.VISUAL),
            new Cat(LT.L7, LT.L8, com.utilitymod.gui.Category.FINDER),
            new Cat(LT.L9, LT.L10, com.utilitymod.gui.Category.MISC),
            new Cat(LT.L11, LT.L12, com.utilitymod.gui.Category.DONUT));
    private static final String[] BOTTOM = {LT.L13, LT.L14, LT.L15};
    private static final String[] BOTTOM_ICONS = {LT.L16, LT.L17, LT.L18};

    private record Hit(float x, float y, float w, float h, BiConsumer<Integer, double[]> fn) {
        boolean in(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }

    private final List<Hit> hits = new ArrayList<>();
    private final StringBuilder query = new StringBuilder();
    private int cat, tab, page;          // page 0 = modules, 1 = configs, 2 = themes
    private float scroll, scrollGoal, scrollMax, s = 1f, open;
    private boolean searchFocus, confirmUnload, typedViaKey;
    /** Animation state: content reveal (restarts on every view change), sliding sidebar highlight, per-card hover/on fades. */
    private float reveal, selY = -1, frameDt, tabX = -1;
    private final float[] sideHover = new float[16];
    private final java.util.Map<String, Float> btnHover = new java.util.HashMap<>();
    private String viewKey = "";
    private final java.util.Map<Module, float[]> cardAnim = new java.util.HashMap<>();   // {hover, on}

    private double[] mouse() {
        var mc = MinecraftClient.getInstance();
        double sx = mc.mouse.getX() * mc.getWindow().getScaledWidth() / Math.max(1, mc.getWindow().getWidth());
        double sy = mc.mouse.getY() * mc.getWindow().getScaledHeight() / Math.max(1, mc.getWindow().getHeight());
        return new double[]{sx, sy};
    }

    private static float ease(float t) { t = Math.max(0, Math.min(1, t)); float u = 1 - t; return 1 - u * u * u; }
    private static float toward(float v, float goal, float k) { return v + (goal - v) * Math.min(1, k); }
    private long last = System.nanoTime();
    private List<String> profiles = List.of();
    private final StringBuilder profileName = new StringBuilder();
    private boolean nameFocus;
    private String notice = "";

    public WaterGui() { super(net.minecraft.text.Text.literal(LT.L19)); }

    @Override protected void init() { GuiTextures.ensureLoaded(); }
    @Override public boolean shouldPause() { return false; }
    @Override public void removed() { closeSheet(); GuiConfig.save(); super.removed(); }

    // ---- data ----------------------------------------------------------------------------

    private static final String[] TAB_NAMES = {LT.L20, LT.L21, LT.L22, LT.L23, LT2.L1, LT.L24, LT.L25, LT.L26};
    /** Checked in this order; the first match wins, so every module sits in exactly one tab. */
    private static final String[][] TAB_RULES = {
            {LT.L23, LT.L27},
            {LT.L22, LT.L28, LT.L29, LT.L30, LT.L31, LT.L32, LT.L33, LT.L34},
            {LT2.L1, LT2.L2, LT2.L3, LT2.L4, LT2.L5, LT2.L6, LT2.L7, LT2.L8},
            {LT.L24, LT.L35, LT.L36, LT.L37, LT.L38},
            {LT.L25, LT.L39, LT.L40},
            {LT.L21, LT.L41, LT.L42, LT.L43, LT.L44, LT.L45, LT.L46, LT.L47, LT.L12, LT.L48, LT.L49, LT.L50, LT.L51,
                    LT.L52, LT.L53, LT.L54, LT.L55, LT.L56, LT.L57, LT.L58, LT.L59, LT.L60, LT.L61, LT.L62, LT.L63}};

    private List<String> tabs() { return List.of(TAB_NAMES); }

    /** The playstyle tab a module belongs to ("Other" when nothing matches). */
    private static String tabOf(Module m) {
        String n = plain(m.getName());
        for (String[] rule : TAB_RULES)
            for (int i = 1; i < rule.length; i++) if (n.contains(plain(rule[i]))) return rule[0];   // keywords may be cloaked: decode
        return LT.L26;
    }

    private List<Module> modules() {
        Cat c = CATS.get(cat);
        List<Module> src = c.category() == com.utilitymod.gui.Category.DONUT
                ? new ArrayList<>(com.utilitymod.anubis.AnubisBridge.features()) : new ArrayList<>(ModuleManager.all());
        String q = query.toString().trim().toLowerCase(Locale.ROOT);
        String t = tabs().get(Math.min(tab, tabs().size() - 1));
        List<Module> out = new ArrayList<>();
        for (Module m : q.isEmpty() && t.equals(TAB_NAMES[0]) ? src : all()) {
            if (q.isEmpty() && t.equals(TAB_NAMES[0]) && waterCat(m) != c.category()) continue;
            if (!q.isEmpty() && !plain(m.getName()).contains(q)) continue;
            if (!t.equals(LT.L20) && !inTab(m, t)) continue;
            out.add(m);
        }
        out.sort((a, b) -> plain(a.getName()).compareTo(plain(b.getName())));
        return out;
    }

    private static List<Module> all() {
        List<Module> a = new ArrayList<>(ModuleManager.all());
        for (Module m : com.utilitymod.anubis.AnubisBridge.features()) a.add(m);
        return a;
    }

    private static final String[] FINDER = {LT.L64, LT.L65, LT.L66, LT.L67, LT.L68, LT.L69, LT.L70};
    private static final String[] COMBAT = {LT.L43, LT.L44, LT.L45, LT.L46, LT.L28, LT.L29, LT.L30, LT.L35, LT.L39,
            LT.L12, LT.L42, LT.L36, LT.L47, LT.L49, LT.L50, LT.L51, LT.L62, LT.L31, LT.L56,
            LT.L57, LT.L32, LT.L48, LT.L71, LT.L27, LT.L72, LT.L52, LT.L53, LT.L54, LT.L55, LT.L61, LT.L58};
    private static final String[] COMBAT_NOT = {LT.L73, LT.L74, LT.L75, LT.L76, LT.L77, LT.L78, LT.L79};
    private static final String[] MOVEMENT = {LT.L59, LT.L80, LT.L81, LT.L82, LT.L83, LT.L84, LT.L85, LT.L86,
            LT.L87, LT.L60, LT.L88, LT.L89, LT.L90, LT.L91, LT.L92, LT.L93, LT.L94, LT.L95, LT.L96, LT.L97};
    private static final String[] RENDER = {LT.L98, LT.L75, LT.L99, LT.L100, LT.L101, LT.L102, LT.L103, LT.L104, LT.L77,
            LT.L105, LT.L106, LT.L107, LT.L108, LT.L109, LT.L110, LT.L111, LT.L112, LT.L113, LT.L78, LT.L114, LT.L115,
            LT.L116, LT.L117, LT.L118, LT.L76, LT.L119, LT.L120, LT.L121, LT.L122, LT.L123, LT.L124, LT.L125,
            LT.L126, LT.L127, LT.L128, LT.L129, LT.L130};

    private static boolean has(String n, String[] keys) {
        for (String k : keys) if (n.contains(plain(k))) return true;
        return false;
    }

    /** Which water.lol sidebar category a module belongs in (Donut modules always stay in Donut). */
    private static com.utilitymod.gui.Category waterCat(Module m) {
        if (m.getCategory() == com.utilitymod.gui.Category.DONUT) return com.utilitymod.gui.Category.DONUT;
        String n = plain(m.getName());
        if (has(n, FINDER)) return com.utilitymod.gui.Category.FINDER;
        if (has(n, COMBAT) && !has(n, COMBAT_NOT)) return com.utilitymod.gui.Category.COMBAT;
        if (has(n, MOVEMENT)) return com.utilitymod.gui.Category.MOVEMENT;
        if (has(n, RENDER) || m.getCategory() == com.utilitymod.gui.Category.VISUAL) return com.utilitymod.gui.Category.VISUAL;
        return com.utilitymod.gui.Category.MISC;
    }

    private static boolean inTab(Module m, String tab) {
        return tabOf(m).equals(tab);
    }

    private static String plain(String raw) {
        StringBuilder b = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) b.append(com.utilitymod.echo.utils.CloakUtil.plainChar(raw.charAt(i)));
        return b.toString().toLowerCase(Locale.ROOT);
    }

    // ---- render ----------------------------------------------------------------------------

    @Override
    public void renderBackground(DrawContext g, int mouseX, int mouseY, float delta) {}

    @Override
    public void render(DrawContext g, int mx, int my, float delta) {
        long now = System.nanoTime();
        float dt = Math.min(.05f, (now - last) / 1e9f);
        last = now;
        open += (1 - open) * Math.min(1, dt * 10);
        frameDt = dt;
        String key = page + LT.L131 + cat + LT.L131 + tab + LT.L131 + themeTab + LT.L131 + query;
        if (!key.equals(viewKey)) { viewKey = key; reveal = 0; }
        reveal = Math.min(1, reveal + dt * 2.6f);
        scroll += (scrollGoal - scroll) * Math.min(1, dt * 16);
        hits.clear();
        s = Math.min(width * .78f / BASE_W, height * .80f / BASE_H) * CardGuiStyle.size / 100f;
        float w = BASE_W * s, h = BASE_H * s;
        float x = (width - w) / 2f, y = (height - h) / 2f - 10 * s + (1 - open) * 12 * s;
        int a = Math.round(open * 255);

        g.createNewRootLayer();
        g.fill(0, 0, width, height, Colors.withAlpha(0xFF05070A, .35f * open));
        float sc = 0.96f + 0.04f * ease(open);
        var ms = g.getMatrices();
        ms.pushMatrix();
        ms.translate(width / 2f, height / 2f);
        ms.scale(sc, sc);
        ms.translate(-width / 2f, -height / 2f);
        Draw.rect(g, x, y, w, Math.round(h), Math.round(14 * s), Colors.withAlpha(BG, ((BG >>> 24) / 255f) * open));
        Rounded.outline(g, Math.round(x), Math.round(y), Math.round(w), Math.round(h), 14 * s, 1f, Colors.withAlpha(0xFFFFFFFF, .06f * open));

        float sideW = 104 * s;
        drawSidebar(g, x, y, sideW, h, mx, my);
        g.fill(Math.round(x + sideW), Math.round(y + 8 * s), Math.round(x + sideW) + 1, Math.round(y + h - 8 * s), SIDEBAR_LINE);

        float cx = x + sideW + 14 * s, cw = w - sideW - 28 * s;
        if (page == 0) drawModules(g, cx, y, cw, h, mx, my);
        else if (page == 1) drawConfigs(g, cx, y + 14 * s, cw, mx, my);
        else if (page == 4) drawControlPanel(g, cx, y + 12 * s, cw, mx, my);
        else drawThemes(g, cx, y + 14 * s, cw, mx, my);

        drawButtons(g, x, y + h + 10 * s, w, mx, my);
        ms.popMatrix();
    }

    private void drawSidebar(DrawContext g, float x, float y, float w, float h, int mx, int my) {
        int accent = CardGuiStyle.accent;
        // logo
        float lx = x + 14 * s, ly = y + 26 * s;
        Text.drawCentered(g, LT.L132, lx, ly, 13 * s, accent);
        Text.drawCentered(g, LT.L132, lx + .4f * s, ly, 13 * s, accent);
        Text.drawCentered(g, LT.L133, lx + Text.width(LT.L132, 13 * s) + 2 * s, ly + 2 * s, 7 * s, MUTED);

        float ry = y + 46 * s, rh = 24 * s;
        for (int i = 0; i < CATS.size(); i++) {
            boolean sel = page == 0 && i == cat;
            if (sel) {
                if (selY < 0) selY = ry;
                selY = toward(selY, ry, frameDt * 14);
                Draw.rect(g, x + 8 * s, selY, w - 16 * s, Math.round(rh), Math.round(6 * s), accent);
            }
            boolean hov = mx >= x + 8 * s && mx < x + w - 8 * s && my >= ry && my < ry + rh;
            sideHover[i] = toward(sideHover[i], hov && !sel ? 1 : 0, frameDt * 12);
            if (sideHover[i] > .01f) Rounded.fill(g, Math.round(x + 8 * s), Math.round(ry), Math.round(w - 16 * s), Math.round(rh), 6 * s,
                    Colors.withAlpha(0xFFFFFFFF, .08f * sideHover[i]));
            float nudge = 3 * s * sideHover[i];
            int txt = sel ? 0xFFFFFFFF : Colors.lerp(Colors.withAlpha(INK, .8f), 0xFFFFFFFF, sideHover[i]);
            icon(g, CATS.get(i).icon(), x + 18 * s + nudge, ry + rh / 2, 10 * s, txt);
            Text.drawCentered(g, CATS.get(i).label(), x + 34 * s + nudge, ry + rh / 2, 8 * s, txt);
            final int idx = i;
            hits.add(new Hit(x + 8 * s, ry, w - 16 * s, rh, (b, p) -> { cat = idx; page = 0; scrollGoal = 0; UiSounds.play(UiSounds.Sound.TAB); }));
            ry += rh + 3 * s;
        }
        float by = y + h - (BOTTOM.length * 28 + 6) * s;
        for (int i = 0; i < BOTTOM.length; i++) {
            boolean sel = page == i && i > 0;
            boolean hov = mx >= x + 8 * s && mx < x + w - 8 * s && my >= by && my < by + rh;
            if (sel) Draw.rect(g, x + 8 * s, by, w - 16 * s, Math.round(rh), Math.round(6 * s), 0x22FFFFFF);
            int hi = 8 + i;
            sideHover[hi] = toward(sideHover[hi], hov && !sel ? 1 : 0, frameDt * 12);
            if (sideHover[hi] > .01f) Rounded.fill(g, Math.round(x + 8 * s), Math.round(by), Math.round(w - 16 * s), Math.round(rh), 6 * s,
                    Colors.withAlpha(0xFFFFFFFF, .08f * sideHover[hi]));
            float bn = 3 * s * sideHover[hi];
            int bt = Colors.lerp(Colors.withAlpha(INK, .8f), 0xFFFFFFFF, Math.max(sideHover[hi], sel ? 1 : 0));
            icon(g, BOTTOM_ICONS[i], x + 18 * s + bn, by + rh / 2, 10 * s, bt);
            Text.drawCentered(g, BOTTOM[i], x + 34 * s + bn, by + rh / 2, 8 * s, bt);
            final int idx = i;
            hits.add(new Hit(x + 8 * s, by, w - 16 * s, rh, (b, p) -> {
                UiSounds.play(UiSounds.Sound.CLICK);
                if (idx == 0) MinecraftClient.getInstance().setScreen(new com.utilitymod.echo.hud.HudEditorScreen(this));
                else { page = idx; if (idx == 1) profiles = GuiConfig.profiles(); }
            }));
            by += rh + 4 * s;
        }
    }

    /**
     * Smooth icons: OpenPrestige PNGs (linear-filtered) where we have one, otherwise the Material MSDF
     * icon font, which stays crisp at any size. The old vector icons were built from blocky rects.
     */
    /** Clean pill switch: track + round knob, radii kept under half the size so the corners never overlap. */
    private void toggleSwitch(DrawContext g, float x, float y, float w, float h, boolean on) {
        int tw = Math.round(w), th = Math.max(4, Math.round(h));
        int ix = Math.round(x), iy = Math.round(y);
        // animated: knob slides, track fades grey -> accent, knob stretches a little mid-travel
        String key = LT3.L1 + ix + LT3.L2 + iy;
        float t = btnHover.merge(key, on ? 1f : 0f, (o, n) -> toward(o, on ? 1 : 0, frameDt * 14));
        Rounded.fill(g, ix, iy, tw, th, th / 2f, Colors.lerp(0xFF2A303C, CardGuiStyle.accent, t));
        int k = th - 4;
        float stretch = (float) Math.sin(t * Math.PI) * k * .35f;
        float kx = ix + 2 + (tw - k - 4) * t - stretch / 2;
        Rounded.fill(g, Math.round(kx), iy + 2, Math.round(k + stretch), k, k / 2f, 0xFFFFFFFF);
    }

    private void icon(DrawContext g, String name, float cx, float cy, float size, int col) {
        String tex = switch (name) {
            case "combat" -> LT.L134;
            case "movement" -> LT.L135;
            case "eye" -> LT.L136;
            case "sliders" -> LT.L137;
            case "keyboard" -> LT.L16;
            case "folder" -> LT.L138;
            case "palette" -> LT.L139;
            default -> null;
        };
        float big = size * 1.25f;   // the PNGs have padding
        if (tex != null && PrestigeIcons.draw(g, tex, cx - big / 2, cy - big / 2, big, col)) return;
        int cp = switch (name) {
            case "back" -> 0xe5c4;      // arrow_back
            case "compass" -> 0xe1b3;   // gps_fixed
            case "shield" -> 0xe7ef;    // group
            case "search" -> 0xe8b6;    // search
            case "combat" -> 0xe518;
            case "movement" -> 0xe566;  // directions_run
            case "eye" -> 0xe8f4;       // visibility
            case "sliders" -> 0xe8b8;   // settings
            case "keyboard" -> 0xe312;  // keyboard
            case "folder" -> 0xe2c7;    // folder_open
            case "palette" -> 0xe3c9;
            default -> 0xe88e;          // info
        };
        dev.koifih.client.render.Draw.icon(g, cp, cx - size * .6f, cy - size * .6f, size * 1.2f, col);
    }

    private void drawModules(DrawContext g, float x, float y, float w, float h, int mx, int my) {
        // search
        float sy = y + 12 * s, sh = 20 * s;
        Draw.rect(g, x, sy, w, Math.round(sh), Math.round(6 * s), FIELD);
        Rounded.outline(g, Math.round(x), Math.round(sy), Math.round(w), Math.round(sh), 6 * s, 1f, searchFocus ? Colors.withAlpha(CardGuiStyle.accent, .7f) : FIELD_LINE);
        icon(g, LT.L140, x + 12 * s, sy + sh / 2, 9 * s, MUTED);
        boolean empty = query.length() == 0;
        String shown = empty && !searchFocus ? LT.L141 : query + (searchFocus && (System.currentTimeMillis() / 500) % 2 == 0 ? LT.L142 : "");
        Text.drawCentered(g, shown, x + 22 * s, sy + sh / 2, 8 * s, empty && !searchFocus ? MUTED : INK);
        hits.add(new Hit(x, sy, w, sh, (b, p) -> searchFocus = true));

        // tabs
        List<String> t = tabs();
        float ty = sy + sh + 8 * s, th = 20 * s;
        Draw.rect(g, x, ty, w, Math.round(th), Math.round(6 * s), FIELD);
        Rounded.outline(g, Math.round(x), Math.round(ty), Math.round(w), Math.round(th), 6 * s, 1f, FIELD_LINE);
        float tw = (w - 4 * s) / t.size();
        for (int i = 0; i < t.size(); i++) {
            float bx = x + 2 * s + i * tw;
            boolean on = i == tab;
            if (on) {
                if (tabX < 0) tabX = bx;
                tabX = toward(tabX, bx, frameDt * 14);
                Rounded.fill(g, Math.round(tabX), Math.round(ty + 2 * s), Math.round(tw), Math.round(th - 4 * s), 5 * s, TAB_ON);
                Rounded.outline(g, Math.round(tabX), Math.round(ty + 2 * s), Math.round(tw), Math.round(th - 4 * s), 5 * s, 1f, 0x22FFFFFF);
            }
            boolean tHov = mx >= bx && mx < bx + tw && my >= ty && my < ty + th;
            float th2 = btnHover.merge(LT2.L9 + i, 0f, (o, n) -> toward(o, tHov ? 1 : 0, frameDt * 12));
            Text.drawCenteredX(g, t.get(i), bx + tw / 2, ty + th / 2, 8 * s, on ? TAB_TEXT_ON : Colors.lerp(MUTED, INK, th2));
            final int idx = i;
            hits.add(new Hit(bx, ty, tw, th, (b, p) -> { tab = idx; scrollGoal = 0; UiSounds.play(UiSounds.Sound.CLICK); }));
        }

        // grid
        float gy = ty + th + 10 * s, gh = y + h - 12 * s - gy;
        float gap = 7 * s, cardH = 32 * s;
        int cols = sheet != null ? 1 : 2;
        float colW = (w - gap) / 2;
        List<Module> mods = modules();
        int rows = (mods.size() + cols - 1) / cols;
        scrollMax = Math.max(0, rows * (cardH + gap) - gap - gh);
        scrollGoal = Math.max(0, Math.min(scrollMax, scrollGoal));
        final float top = gy;
        Scissor.clip(g, Math.round(x), Math.round(gy), Math.round(w), Math.round(gh), () -> {
            for (int i = 0; i < mods.size(); i++) {
                Module m = mods.get(i);
                float appear = ease(reveal * 2.2f - i * 0.06f);
                float cx = x + (i % cols) * (colW + gap), cy = top + (i / cols) * (cardH + gap) - scroll + (1 - appear) * 8 * s;
                if (cy + cardH < top || cy > top + gh) continue;
                boolean on = m.isEnabled();
                boolean hov = mx >= cx && mx < cx + colW && my >= cy && my < cy + cardH && my >= top && my < top + gh;
                float[] an = cardAnim.computeIfAbsent(m, k -> new float[]{0, on ? 1 : 0});
                an[0] = toward(an[0], hov ? 1 : 0, frameDt * 14);
                an[1] = toward(an[1], on ? 1 : 0, frameDt * 10);
                float alpha = (.55f + .45f * an[1]) * appear;
                int bg = Colors.lerp(CARD, CARD_HOVER, sheet == m ? 1f : an[0]);
                Draw.rect(g, cx, cy - an[0] * 1.2f * s, colW, Math.round(cardH), Math.round(6 * s), Colors.withAlpha(bg, (.7f + .3f * an[1]) * appear));
                if (an[1] > .02f) Draw.rect(g, cx, cy + 6 * s + (1 - an[1]) * (cardH / 2 - 6 * s), Math.max(1, 2 * s),
                        Math.max(1, Math.round((cardH - 12 * s) * an[1])), Math.round(s), Colors.withAlpha(CardGuiStyle.accent, appear));
                Text.drawCentered(g, Text.fit(m.getName(), colW - 20 * s, 8.5f * s), cx + 10 * s, cy + 11 * s, 8.5f * s, Colors.withAlpha(INK, alpha));
                String d = m.getDescription() == null ? "" : m.getDescription();
                Text.drawCentered(g, Text.fit(d, colW - 20 * s, 5.8f * s), cx + 10 * s, cy + 22.5f * s, 5.8f * s, Colors.withAlpha(MUTED, alpha));
                hits.add(new Hit(cx, Math.max(cy, top), colW, Math.min(cy + cardH, top + gh) - Math.max(cy, top), (b, p) -> {
                    if (b == 1) {
                        openSheet(m);
                        return;
                    }
                    m.setEnabled(!m.isEnabled());
                    UiSounds.play(m.isEnabled() ? UiSounds.Sound.TOGGLE_ON : UiSounds.Sound.TOGGLE_OFF);
                }));
            }
            if (mods.isEmpty()) Text.drawCenteredX(g, LT.L143, x + w / 2, top + 20 * s, 8 * s, MUTED);
        });
        if (sheet != null) {
            // settings panel: slides in from the right and fades in
            sheetT = toward(sheetT, 1, frameDt * 9);
            float e = ease(sheetT);
            var ms = g.getMatrices();
            ms.pushMatrix();
            ms.translate((1 - e) * 26 * s, 0);
            final float fx = x + colW + gap, fy = gy, fw = colW, fh = gh;
            dev.koifih.client.render.Opacity.with(e, () -> drawSheet(g, fx, fy, fw, fh, mx, my, frameDt * 20f));
            ms.popMatrix();
        }
    }

    private String selectedProfile = LT.L144;

    /** Configs page: name field + Create / Save / Upload / Save Config, then the saved profiles. */
    private void drawConfigs(DrawContext g, float x, float y, float w, int mx, int my) {
        float fh = 22 * s, fw = w * .38f;
        Draw.rect(g, x, y, fw, Math.round(fh), Math.round(6 * s), FIELD);
        Rounded.outline(g, Math.round(x), Math.round(y), Math.round(fw), Math.round(fh), 6 * s, 1f,
                nameFocus ? Colors.withAlpha(CardGuiStyle.accent, .7f) : FIELD_LINE);
        String shown = profileName.length() == 0 && !nameFocus ? selectedProfile : profileName + (nameFocus ? LT.L142 : "");
        Text.drawCenteredX(g, Text.fit(shown, fw - 12 * s, 8.5f * s), x + fw / 2, y + fh / 2, 8.5f * s, INK);
        hits.add(new Hit(x, y, fw, fh, (b, p) -> nameFocus = true));

        float bx = x + fw + 8 * s;
        bx = outlineButton(g, LT.L145, bx, y, fh, () -> {
            String n = profileName.toString().trim();
            if (n.isEmpty()) { notice = LT.L146; return; }
            notice = GuiConfig.saveProfile(n) ? LT.L147 + n : LT.L148 + n;
            selectedProfile = n; profileName.setLength(0); profiles = GuiConfig.profiles();
        });
        bx = outlineButton(g, LT.L149, bx, y, fh, () -> {
            notice = GuiConfig.saveProfile(selectedProfile) ? LT.L150 + selectedProfile : LT.L151;
            profiles = GuiConfig.profiles();
        });
        bx = outlineButton(g, LT.L152, bx, y, fh, () -> { GuiConfig.openProfileFolder(); notice = LT.L153; });
        outlineButton(g, LT.L154, bx, y, fh, () -> { GuiConfig.save(); notice = LT.L155; });

        float ly = y + fh + 12 * s, viewH = BASE_H * s - 40 * s - fh;
        if (profiles.isEmpty()) {
            Text.drawCenteredX(g, LT.L156, x + w / 2, y + viewH / 2, 8.5f * s, MUTED);
        }
        for (String p : profiles) {
            if (ly > y + viewH) break;
            boolean sel = p.equals(selectedProfile);
            Draw.rect(g, x, ly, w, Math.round(24 * s), Math.round(6 * s), sel ? CARD_HOVER : CARD);
            if (sel) Draw.rect(g, x, ly + 5 * s, Math.max(1, 2 * s), Math.round(14 * s), Math.round(s), CardGuiStyle.accent);
            Text.drawCentered(g, Text.fit(p, w - 120 * s, 8 * s), x + 10 * s, ly + 12 * s, 8 * s, INK);
            hits.add(new Hit(x, ly, w - 104 * s, 24 * s, (b, q) -> selectedProfile = p));
            button(g, LT.L157, x + w - 98 * s, ly + 4 * s, 44 * s, 16 * s, true, () -> {
                notice = GuiConfig.loadProfile(p) ? LT.L158 + p : LT.L159; selectedProfile = p;
            });
            button(g, LT.L160, x + w - 50 * s, ly + 4 * s, 44 * s, 16 * s, false, () -> {
                GuiConfig.deleteProfile(p); profiles = GuiConfig.profiles(); notice = LT.L161 + p;
            });
            ly += 28 * s;
        }
        if (!notice.isEmpty()) Text.drawCenteredX(g, notice, x + w / 2, y + viewH + 6 * s, 7 * s, MUTED);
    }

    /** Outlined pill button sized to its label; returns the x after it. */
    private float outlineButton(DrawContext g, String label, float x, float y, float h, Runnable r) {
        float w = Text.width(label, 8.5f * s) + 20 * s;
        Draw.rect(g, x, y, w, Math.round(h), Math.round(6 * s), FIELD);
        Rounded.outline(g, Math.round(x), Math.round(y), Math.round(w), Math.round(h), 6 * s, 1f, FIELD_LINE);
        Text.drawCenteredX(g, label, x + w / 2, y + h / 2, 8.5f * s, INK);
        hits.add(new Hit(x, y, w, h, (b, p) -> { UiSounds.play(UiSounds.Sound.CLICK); r.run(); }));
        return x + w + 6 * s;
    }

    /** Gradient themes: name, left colour, right colour, filter group. */
    private record Theme(String name, int a, int b, String group) {}
    private static final List<Theme> THEMES = List.of(
            new Theme(LT.L162, 0xFFFF1F4F, 0xFFFF5A3C, LT.L163),
            new Theme(LT.L164, 0xFFFF7A1A, 0xFFFFB12E, LT.L165),
            new Theme(LT.L166, 0xFFFFE03A, 0xFFC9F046, LT.L165),
            new Theme(LT.L167, 0xFF6CF02E, 0xFF2EE07A, LT.L168),
            new Theme(LT.L169, 0xFF22E39B, 0xFF2EC4C8, LT.L168),
            new Theme(LT.L170, 0xFF2BE6FF, 0xFF3F8CFF, LT.L171),
            new Theme(LT.L172, 0xFF3B82FF, 0xFF5CC8FF, LT.L171),
            new Theme(LT.L173, 0xFFA855F7, 0xFFF857C7, LT.L174),
            new Theme(LT.L175, 0xFFFF4FA3, 0xFFFF5F6D, LT.L163),
            new Theme(LT.L176, 0xFFA78BFA, 0xFF818CF8, LT.L174),
            new Theme(LT.L177, 0xFF7C3AED, 0xFF4F6BFF, LT.L174),
            new Theme(LT.L178, 0xFF9B5CFF, 0xFFE45CF2, LT.L174),
            new Theme(LT.L179, 0xFF22E1FF, 0xFFF72FD2, LT.L174),
            new Theme(LT.L180, 0xFFFF6A4D, 0xFFFF9F1A, LT.L165),
            new Theme(LT.L181, 0xFF06B6D4, 0xFF3B6CFF, LT.L171),
            new Theme(LT.L182, 0xFF6EE7B7, 0xFFA06CFF, LT.L168),
            new Theme(LT.L183, 0xFFF78FD2, 0xFF93D8F7, LT.L174),
            new Theme(LT.L184, 0xFF88C0D0, 0xFFB48EAD, LT.L171),
            new Theme(LT.L185, 0xFFEB6F92, 0xFF9CCFD8, LT.L163),
            new Theme(LT.L186, 0xFFFF3B5C, 0xFFFF7A6B, LT.L163),
            new Theme(LT.L187, 0xFFFF6A4D, 0xFFFF9F4D, LT.L163),
            new Theme(LT.L188, 0xFFFFB02E, 0xFFFFE066, LT.L165),
            new Theme(LT.L189, 0xFF5CF5A8, 0xFF4DD8F0, LT.L168),
            new Theme(LT.L190, 0xFF1FD1C1, 0xFF3B8CFF, LT.L171),
            new Theme(LT.L191, 0xFF8FD3FF, 0xFFB28CFF, LT.L171),
            new Theme(LT.L192, 0xFF3B6CFF, 0xFF45D4FF, LT.L171),
            new Theme(LT.L193, 0xFFFF5CC8, 0xFFFF7A9A, LT.L163),
            new Theme(LT.L194, 0xFF94A3B8, 0xFF64748B, LT.L195),
            new Theme(LT.L195, 0xFFE5E7EB, 0xFF9CA3AF, LT.L195));
    private static final String[] THEME_TABS = {LT.L20, LT.L163, LT.L165, LT.L168, LT.L171, LT.L174, LT.L195};
    private int themeTab;

    /** Left-to-right gradient as thin vertical strips. */
    private static void hGradient(DrawContext g, float x, float y, float w, float h, int a, int b) {
        int strips = Math.max(8, Math.round(w / 3));
        for (int i = 0; i < strips; i++) {
            int x0 = Math.round(x + w * i / strips), x1 = Math.round(x + w * (i + 1) / strips);
            g.fill(x0, Math.round(y), x1, Math.round(y + h), Colors.lerp(a, b, i / (float) (strips - 1)));
        }
    }

    private void drawThemes(DrawContext g, float x, float y, float w, int mx, int my) {
        float th = 20 * s, tw = (w - 4 * s) / THEME_TABS.length;
        Draw.rect(g, x, y, w, Math.round(th), Math.round(6 * s), FIELD);
        Rounded.outline(g, Math.round(x), Math.round(y), Math.round(w), Math.round(th), 6 * s, 1f, FIELD_LINE);
        for (int i = 0; i < THEME_TABS.length; i++) {
            float bx = x + 2 * s + i * tw;
            boolean on = i == themeTab;
            if (on) Draw.rect(g, bx, y + 2 * s, tw, Math.round(th - 4 * s), Math.round(5 * s), TAB_ON);
            Text.drawCenteredX(g, THEME_TABS[i], bx + tw / 2, y + th / 2, 8 * s, on ? 0xFFFFFFFF : MUTED);
            final int idx = i;
            hits.add(new Hit(bx, y, tw, th, (b, p) -> { themeTab = idx; scrollGoal = 0; UiSounds.play(UiSounds.Sound.CLICK); }));
        }
        List<Theme> list = new ArrayList<>();
        for (Theme t : THEMES) if (themeTab == 0 || t.group().equals(THEME_TABS[themeTab])) list.add(t);
        float top = y + th + 10 * s, gap = 7 * s, cw = (w - gap * 3) / 4, ch = cw * .72f;
        float viewH = BASE_H * s - th - 46 * s;
        int rows = (list.size() + 3) / 4;
        scrollMax = Math.max(0, rows * (ch + gap) - gap - viewH);
        scrollGoal = Math.max(0, Math.min(scrollMax, scrollGoal));
        Scissor.clip(g, Math.round(x), Math.round(top), Math.round(w), Math.round(viewH), () -> {
            for (int i = 0; i < list.size(); i++) {
                Theme t = list.get(i);
                float cx = x + (i % 4) * (cw + gap), cy = top + (i / 4) * (ch + gap) - scroll;
                if (cy + ch < top || cy > top + viewH) continue;
                boolean sel = (CardGuiStyle.accent & 0xFFFFFF) == (t.a() & 0xFFFFFF);
                Draw.rect(g, cx, cy, cw, Math.round(ch), Math.round(6 * s), CARD);
                Rounded.outline(g, Math.round(cx), Math.round(cy), Math.round(cw), Math.round(ch), 6 * s, sel ? 1.5f : 1f, sel ? t.a() : 0x18FFFFFF);
                float pad = 2 * s, gh = ch * .66f;
                hGradient(g, cx + pad, cy + pad, cw - pad * 2, gh, t.a(), t.b());
                Text.drawCentered(g, t.name(), cx + 7 * s, cy + pad + gh + (ch - gh - pad) / 2, 8 * s, sel ? t.a() : INK);
                hits.add(new Hit(cx, Math.max(cy, top), cw, Math.min(cy + ch, top + viewH) - Math.max(cy, top), (b, p) -> {
                    CardGuiStyle.accent = t.a();
                    CardGuiStyle.background = t.b();
                    GuiConfig.markDirty();
                    UiSounds.play(UiSounds.Sound.CLICK);
                }));
            }
        });
    }

    private void button(DrawContext g, String label, float x, float y, float w, float h, boolean primary, Runnable r) {
        double[] m = mouse();
        boolean hov = m[0] >= x && m[0] < x + w && m[1] >= y && m[1] < y + h;
        float hv = btnHover.merge(LT2.L10 + label + Math.round(x) + Math.round(y), 0f, (o, n) -> toward(o, hov ? 1 : 0, frameDt * 12));
        int base = primary ? CardGuiStyle.accent : 0xFF2A303C;
        Rounded.fill(g, Math.round(x), Math.round(y), Math.round(w), Math.round(h), 6 * s, Colors.lerp(base, 0xFFFFFFFF, .12f * hv));
        Text.drawCenteredX(g, label, x + w / 2, y + h / 2, 7.5f * s, 0xFFFFFFFF);
        hits.add(new Hit(x, y, w, h, (b, p) -> { UiSounds.play(UiSounds.Sound.CLICK); r.run(); }));
    }

    // ---- module settings page (in place, no Cards GUI) ----------------------------------------------

    private record Row(com.utilitymod.echo.features.settings.Setting setting, dev.koifih.client.ui.component.Control control) {}
    private com.utilitymod.echo.features.Feature sheet;
    private final List<Row> rows = new ArrayList<>();
    private final List<dev.koifih.client.ui.component.Control> controls = new ArrayList<>();
    private dev.koifih.client.ui.component.Control focus;
    private float sheetScroll, sheetMax, sheetT;
    private float[] sheetRect = new float[4];
    private int returnPage;

    private void openSheet(Module m) {
        boolean same = sheet == m;
        closeSheet();
        if (same) { UiSounds.play(UiSounds.Sound.TAB); return; }   // right-click the open one again: close
        if (!(m instanceof com.utilitymod.echo.features.Feature f)) { notice = LT.L196; return; }
        sheet = f;
        returnPage = page;
        sheetT = 0;
        reveal = Math.min(reveal, .35f);   // let the cards re-cascade as the grid narrows
        sheetScroll = 0;
        String mod = m.getName();
        var bind = new dev.koifih.client.ui.component.Keybind(0, 0, 64, 18, 1f, net.minecraft.text.Text.literal(LT.L197),
                () -> {
                    int code = ModuleBinds.get(mod);
                    if (code == -1 || code == 0) return net.minecraft.client.util.InputUtil.UNKNOWN_KEY;
                    if ((code & 0x80000000) != 0) return net.minecraft.client.util.InputUtil.Type.MOUSE.createFromCode(code & 0xFF);
                    return net.minecraft.client.util.InputUtil.Type.KEYSYM.createFromCode(code);
                },
                key -> {
                    if (key == null || key.equals(net.minecraft.client.util.InputUtil.UNKNOWN_KEY)) ModuleBinds.set(mod, -1);
                    else if (key.getCategory() == net.minecraft.client.util.InputUtil.Type.MOUSE) ModuleBinds.set(mod, 0x80000000 | key.getCode());
                    else ModuleBinds.set(mod, key.getCode());
                });
        bind.setClearable(true);
        controls.add(bind);
        rows.add(new Row(new com.utilitymod.echo.features.settings.impl.KeybindSetting(LT.L197, -1), bind));
        for (var st : f.getSettings()) {
            var c = CardGui.create(st, s, height - 10);
            if (c == null) continue;
            if (c instanceof dev.koifih.client.ui.component.Bool b) b.cardStyle = true;
            if (c instanceof dev.koifih.client.ui.component.Slider sl) sl.cardStyle = true;
            if (c instanceof dev.koifih.client.ui.component.popup.RowPopup rp) rp.cardStyle = true;
            controls.add(c);
            rows.add(new Row(st, c));
        }
        UiSounds.play(UiSounds.Sound.POPUP);
    }

    private void closeSheet() {
        focus = null;
        for (var c : controls) { c.dismiss(); c.visible = false; }
        controls.clear();
        rows.clear();
        sheet = null;
    }

    private void backFromSheet() {
        closeSheet();
        page = 0;
        UiSounds.play(UiSounds.Sound.TAB);
    }

    private void drawSheet(DrawContext g, float x, float y, float w, float h, int mx, int my, float delta) {
        if (sheet == null) return;
        final float sheetBottom = y + h - 4 * s;
        sheetRect = new float[]{x, y, w, h};
        // selected module card on top
        float hh = 32 * s;
        Draw.rect(g, x, y, w, Math.round(hh), Math.round(6 * s), CARD_HOVER);
        Text.drawCentered(g, Text.fit(sheet.getName(), w - 50 * s, 8.5f * s), x + 10 * s, y + 11 * s, 8.5f * s, INK);
        String d = sheet.getDescription() == null ? "" : sheet.getDescription();
        Text.drawCentered(g, Text.fit(d, w - 20 * s, 5.8f * s), x + 10 * s, y + 22.5f * s, 5.8f * s, MUTED);
        boolean on = sheet.isEnabled();
        float tw = 24 * s, th = 13 * s, tx = x + w - tw - 8 * s, ty = y + 5 * s;
        toggleSwitch(g, tx, ty, tw, th, on);
        hits.add(new Hit(tx, ty, tw, th, (b, p) -> {
            sheet.setEnabled(!sheet.isEnabled());
            UiSounds.play(sheet.isEnabled() ? UiSounds.Sound.TOGGLE_ON : UiSounds.Sound.TOGGLE_OFF);
        }));
        // settings box below it
        float by = y + hh + 4 * s;
        Draw.rect(g, x, by, w, Math.round(y + h - by), Math.round(6 * s), 0xFF1E232D);
        Rounded.outline(g, Math.round(x), Math.round(by), Math.round(w), Math.round(y + h - by), 6 * s, 1f, 0x14FFFFFF);
        // Reset Module row
        float ry = by + 6 * s;
        Text.drawCentered(g, LT.L198, x + 10 * s, ry + 8 * s, 8 * s, INK);
        float rbw = w * .48f;
        Draw.rect(g, x + w - rbw - 8 * s, ry, rbw, Math.round(16 * s), Math.round(5 * s), FIELD);
        Rounded.outline(g, Math.round(x + w - rbw - 8 * s), Math.round(ry), Math.round(rbw), Math.round(16 * s), 5 * s, 1f, FIELD_LINE);
        Text.drawCenteredX(g, LT.L198, x + w - rbw / 2 - 8 * s, ry + 8 * s, 7.5f * s, INK);
        final var target = sheet;
        hits.add(new Hit(x + w - rbw - 8 * s, ry, rbw, 16 * s, (b, p) -> {
            SettingDefaults.reset(target);
            notice = LT.L199 + target.getName();
            UiSounds.play(UiSounds.Sound.SUCCESS);
        }));
        y = ry + 20 * s - 10 * s;   // rows start right below (the header math below adds hh + 10)
        hh = 0;
        x = x + 4 * s;
        w = w - 8 * s;
        float top = y + hh + 10 * s, bottom = sheetBottom;
        List<dev.koifih.client.ui.component.popup.Popup> popups = new ArrayList<>();
        final float lx = x + 4 * s, cw = w - 8 * s, rx = lx + cw;
        Scissor.clip(g, Math.round(x), Math.round(top), Math.round(w), Math.round(bottom - top), () -> {
            float yy = top - sheetScroll;
            String section = null;
            for (Row row : rows) {
                var c = row.control();
                String want = c instanceof dev.koifih.client.ui.component.Keybind ? LT.L200 : LT.L201;
                if (!want.equals(section)) {
                    float lw = Text.width(want, 6.5f * s), mid = lx + cw / 2;
                    g.fill(Math.round(lx), Math.round(yy + 6 * s), Math.round(mid - lw / 2 - 6 * s), Math.round(yy + 6 * s) + 1, 0x18FFFFFF);
                    g.fill(Math.round(mid + lw / 2 + 6 * s), Math.round(yy + 6 * s), Math.round(rx), Math.round(yy + 6 * s) + 1, 0x18FFFFFF);
                    Text.drawCenteredX(g, want, mid, yy + 6 * s, 6.5f * s, MUTED);
                    yy += 16 * s;
                    section = want;
                }
                float rh = (c instanceof dev.koifih.client.ui.component.TextInput ? 36 : c instanceof dev.koifih.client.ui.component.Slider ? 36 : c instanceof dev.koifih.client.ui.component.Bool ? 24 : 26) * s;
                boolean inView = yy + rh > top && yy < bottom;
                c.visible = inView;
                c.active = inView;
                if (inView) {
                    if (c instanceof dev.koifih.client.ui.component.Slider sl) {
                        Text.drawCentered(g, row.setting().getName(), lx, yy + 8 * s, 8 * s, INK);
                        String val = sl.shownValue();
                        Text.drawCentered(g, val, rx - Text.width(val, 8 * s), yy + 8 * s, 8 * s, INK);
                        c.setX(Math.round(lx)); c.setWidth(Math.round(cw)); c.setHeight(Math.round(12 * s)); c.setY(Math.round(yy + 18 * s));
                    } else if (c instanceof dev.koifih.client.ui.component.TextInput) {
                        // text setting: label on top, full-width box below
                        Text.drawCentered(g, row.setting().getName(), lx, yy + 8 * s, 8 * s, INK);
                        c.setX(Math.round(lx)); c.setWidth(Math.round(cw)); c.setHeight(Math.round(16 * s)); c.setY(Math.round(yy + 16 * s));
                    } else if (c instanceof dev.koifih.client.ui.component.popup.Popup) {
                        c.setX(Math.round(lx - 6 * s)); c.setY(Math.round(yy + 2 * s)); c.setWidth(Math.round(cw + 12 * s)); c.setHeight(Math.round(rh - 4 * s));
                    } else {
                        Text.drawCentered(g, row.setting().getName(), lx, yy + rh / 2, 8 * s, INK);
                        int ww = Math.round((c instanceof dev.koifih.client.ui.component.Bool ? 30 : 64) * s);
                        int hh2 = Math.round((c instanceof dev.koifih.client.ui.component.Bool ? 16 : 18) * s);
                        c.setWidth(ww); c.setHeight(hh2); c.setX(Math.round(rx - ww)); c.setY(Math.round(yy + (rh - hh2) / 2));
                    }
                    c.draw(g, mx, my, delta);
                    if (c instanceof dev.koifih.client.ui.component.popup.Popup pp) popups.add(pp);
                }
                yy += rh;
            }
            sheetMax = Math.max(0, yy + sheetScroll - bottom + 8 * s);
        });
        sheetScroll = Math.max(0, Math.min(sheetMax, sheetScroll));
        for (int i = popups.size() - 1; i >= 0; i--) popups.get(i).renderPopup(g, mx, my);
    }

    // ---- Control Panel ------------------------------------------------------------------------------

    private String binding;   // "menu" / "panic" while waiting for a key

    private float cpCard(DrawContext g, float x, float y, float w, String title, String desc, Boolean toggle, Runnable flip) {
        float h = 30 * s;
        Draw.rect(g, x, y, w, Math.round(h), Math.round(6 * s), CARD);
        Text.drawCentered(g, title, x + 9 * s, y + 10 * s, 9 * s, INK);
        Text.drawCentered(g, Text.fit(desc, w - 50 * s, 5.8f * s), x + 9 * s, y + 21 * s, 5.8f * s, MUTED);
        if (toggle != null) {
            float tw = 24 * s, th = 13 * s, tx = x + w - tw - 8 * s, ty = y + 8 * s;
            toggleSwitch(g, tx, ty, tw, th, toggle);
            hits.add(new Hit(x, y, w, h, (b, p) -> { flip.run(); GuiConfig.markDirty(); UiSounds.play(UiSounds.Sound.CLICK); }));
        }
        return y + h;
    }

    private float cpRow(DrawContext g, float x, float y, float w, String label, String value, boolean waiting, Runnable click) {
        Text.drawCentered(g, label, x + 9 * s, y + 9 * s, 8 * s, INK);
        float bw = w * .52f, bx = x + w - bw - 8 * s;
        Draw.rect(g, bx, y + 1 * s, bw, Math.round(16 * s), Math.round(5 * s), waiting ? Colors.withAlpha(CardGuiStyle.accent, .5f) : FIELD);
        Rounded.outline(g, Math.round(bx), Math.round(y + s), Math.round(bw), Math.round(16 * s), 5 * s, 1f, FIELD_LINE);
        Text.drawCenteredX(g, value, bx + bw / 2, y + 9 * s, 7.5f * s, INK);
        hits.add(new Hit(bx, y, bw, 18 * s, (b, p) -> { click.run(); UiSounds.play(UiSounds.Sound.CLICK); }));
        return y + 22 * s;
    }

    private void drawControlPanel(DrawContext g, float x, float y, float w, int mx, int my) {
        Text.drawCenteredX(g, LT.L202, x + w / 2, y + 4 * s, 10 * s, INK);
        Text.drawCenteredX(g, LT.L203, x + w / 2, y + 16 * s, 7 * s, MUTED);
        float gap = 7 * s, cw = (w - gap) / 2, top = y + 28 * s;
        // left column
        float ly = top;
        ly = cpCard(g, x, ly, cw, LT.L204, LT.L205,
                com.utilitymod.echo.handlers.ServerRotations.showOnModel,
                () -> com.utilitymod.echo.handlers.ServerRotations.showOnModel = !com.utilitymod.echo.handlers.ServerRotations.showOnModel) + gap;
        ly = cpCard(g, x, ly, cw, LT.L206, LT.L207, UiSounds.enabled,
                () -> UiSounds.enabled = !UiSounds.enabled) + gap;
        com.utilitymod.web.WebUiModule web = null;
        for (Module m : ModuleManager.all()) if (m instanceof com.utilitymod.web.WebUiModule wm) web = wm;
        final var webMod = web;
        if (webMod != null) ly = cpCard(g, x, ly, cw, LT.L208, LT.L209,
                webMod.isEnabled(), () -> webMod.setEnabled(!webMod.isEnabled())) + gap;
        cpCard(g, x, ly, cw, LT.L210, LT.L211, CardGuiStyle.glass,
                () -> CardGuiStyle.glass = !CardGuiStyle.glass);

        // right column
        float rx = x + cw + gap, ry = top;
        Draw.rect(g, rx, ry, cw, Math.round(76 * s), Math.round(6 * s), CARD);
        Text.drawCentered(g, LT.L212, rx + 9 * s, ry + 10 * s, 9 * s, INK);
        Text.drawCentered(g, LT.L213, rx + 9 * s, ry + 21 * s, 5.8f * s, MUTED);
        float iy = ry + 30 * s;
        Text.drawCentered(g, LT.L214, rx + 9 * s, iy + 9 * s, 8 * s, INK);
        boolean cs = com.utilitymod.echo.hud.WaterHud.colorShift;
        float tw = 24 * s, th = 13 * s, tx = rx + cw - tw - 8 * s, ty = iy + 2.5f * s;
        toggleSwitch(g, tx, ty, tw, th, cs);
        hits.add(new Hit(rx, iy, cw, 18 * s, (b, p) -> { com.utilitymod.echo.hud.WaterHud.colorShift = !com.utilitymod.echo.hud.WaterHud.colorShift; UiSounds.play(UiSounds.Sound.CLICK); }));
        iy += 22 * s;
        String menuKey = LT.L215.equals(binding) ? LT.L216 : dev.koifih.client.input.Keybinds.panelKey().getLocalizedText().getString().toUpperCase(Locale.ROOT);
        cpRow(g, rx, iy, cw, LT.L217, menuKey, LT.L215.equals(binding), () -> binding = LT.L215);

        ry += 76 * s + gap;
        Draw.rect(g, rx, ry, cw, Math.round(56 * s), Math.round(6 * s), CARD);
        Text.drawCentered(g, LT.L218, rx + 9 * s, ry + 10 * s, 9 * s, INK);
        Text.drawCentered(g, LT.L219, rx + 9 * s, ry + 21 * s, 5.8f * s, MUTED);
        float pbw = 46 * s, pbx = rx + cw - pbw - 8 * s;
        Draw.rect(g, pbx, ry + 5 * s, pbw, Math.round(14 * s), Math.round(5 * s), 0xFFB4233A);
        Text.drawCenteredX(g, LT.L218, pbx + pbw / 2, ry + 12 * s, 7.5f * s, 0xFFFFFFFF);
        hits.add(new Hit(pbx, ry + 5 * s, pbw, 14 * s, (b, p) -> { ModuleBinds.panic(); notice = LT.L220; UiSounds.play(UiSounds.Sound.TOGGLE_OFF); }));
        String pk = ModuleBinds.name(ModuleBinds.get(ModuleBinds.PANIC));
        cpRow(g, rx, ry + 32 * s, cw, LT.L197, LT.L221.equals(binding) ? LT.L216 : pk.isEmpty() ? LT.L222 : pk, LT.L221.equals(binding), () -> binding = LT.L221);
    }

    /** Unload: every module off, settings saved, GUI closed, then every hook goes inert so nothing can reopen. */
    private void unload() {
        try { ModuleBinds.panic(); } catch (Throwable ignored) {}
        try { GuiConfig.save(); } catch (Throwable ignored) {}
        MinecraftClient.getInstance().setScreen(null);
        com.utilitymod.UtilityMod.LOGGER.info(LT3.L3);
        com.utilitymod.inject.Hooks.detonate();
    }

    private boolean sheetInput() { return page == 0 && sheet != null; }

    /** The two pills under the panel: Control Panel (web UI) and Unload (Exit Gate). */
    private void drawButtons(DrawContext g, float px, float y, float pw, int mx, int my) {
        float bw = 96 * s, bh = 18 * s, gap = 6 * s;
        float x = px + pw / 2 - bw - gap / 2;
        Draw.rect(g, x, y, bw, Math.round(bh), Math.round(6 * s), 0xF0161A22);
        Rounded.outline(g, Math.round(x), Math.round(y), Math.round(bw), Math.round(bh), 6 * s, 1f, 0x22FFFFFF);
        Text.drawCenteredX(g, LT.L202, x + bw / 2, y + bh / 2, 7.5f * s, INK);
        hits.add(new Hit(x, y, bw, bh, (b, p) -> { closeSheet(); page = page == 4 ? 0 : 4; UiSounds.play(UiSounds.Sound.TAB); }));
        float x2 = x + bw + gap;
        Draw.rect(g, x2, y, bw, Math.round(bh), Math.round(6 * s), CardGuiStyle.accent);
        Text.drawCenteredX(g, confirmUnload ? LT.L223 : LT.L224, x2 + bw / 2, y + bh / 2, 7.5f * s, 0xFFFFFFFF);
        hits.add(new Hit(x2, y, bw, bh, (b, p) -> {
            if (!confirmUnload) { confirmUnload = true; return; }
            unload();
        }));
    }

    // ---- input ------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (sheetInput()) {
            for (var c : controls) if (c instanceof dev.koifih.client.ui.component.Keybind k && k.captureMouse(click)) return true;
            for (var c : controls) if (c instanceof dev.koifih.client.ui.component.popup.Popup p && p.isOpen() && p.mouseClicked(click, doubled)) return true;
            for (var c : controls) {
                if (c.active && c.visible && c.isMouseOver(click.x(), click.y())) {
                    for (var o : controls) if (o != c && o instanceof dev.koifih.client.ui.component.TextInput ti) ti.setFocused(false);
                    if (c instanceof dev.koifih.client.ui.component.TextInput ti) ti.setFocused(true);
                    focus = c; c.onClick(click, doubled); return true;
                }
            }
        }
        boolean wasSearch = searchFocus, wasName = nameFocus;
        searchFocus = false;
        nameFocus = false;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (h.in(click.x(), click.y())) {
                if (h.fn() != null) h.fn().accept(click.button(), new double[]{click.x(), click.y()});
                return true;
            }
        }
        confirmUnload = false;
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double dx, double dy) {
        if (sheetInput()) {
            for (var c : controls) if (c instanceof dev.koifih.client.ui.component.popup.Popup p && p.isOpen() && p.scrolledWidget(x, y, dy)) return true;
            boolean overSheet = x >= sheetRect[0] && x < sheetRect[0] + sheetRect[2] && y >= sheetRect[1] && y < sheetRect[1] + sheetRect[3];
            if (overSheet) {
                sheetScroll = Math.max(0, Math.min(sheetMax, sheetScroll - (float) dy * 24 * s));
                return true;
            }
        }
        scrollGoal -= (float) dy * 30 * s;
        return true;
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        if (sheetInput()) for (var c : controls) if (c.active && c.visible) c.onDrag(click, dx, dy);
        return true;
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (sheetInput()) for (var c : controls) if (c.active && c.visible) c.onRelease(click);
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput e) {
        int k = e.key();
        if (binding != null) {
            if (k != GLFW.GLFW_KEY_ESCAPE) {
                boolean clear = k == GLFW.GLFW_KEY_BACKSPACE || k == GLFW.GLFW_KEY_DELETE;
                if (binding.equals(LT.L215) && !clear) dev.koifih.client.input.Keybinds.setOpenKeyCode(k);
                else if (binding.equals(LT.L221)) ModuleBinds.set(ModuleBinds.PANIC, clear ? -1 : k);
                GuiConfig.markDirty();
            }
            binding = null;
            typedViaKey = true;
            return true;
        }
        if (sheetInput()) {
            if (focus instanceof dev.koifih.client.ui.component.TextInput ti && ti.capturesInput()
                    && (e.modifiers() & GLFW.GLFW_MOD_CONTROL) == 0) {
                // type from key presses (char events don't always reach this screen)
                String kn = GLFW.glfwGetKeyName(k, e.scancode());
                char ch = 0;
                if (kn != null && kn.length() == 1) ch = kn.charAt(0);
                else if (k == GLFW.GLFW_KEY_SPACE) ch = ' ';
                if (ch != 0) {
                    if ((e.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0) ch = Character.toUpperCase(ch);
                    ti.charTyped(new net.minecraft.client.input.CharInput(ch, e.modifiers()));
                    typedViaKey = true;
                    return true;
                }
            }
            if (focus != null && focus.capturesInput() && focus.keyPressed(e)) { typedViaKey = true; return true; }
            for (var c : controls) if (c.capturesInput() && c.keyPressed(e)) { typedViaKey = true; return true; }
            if (k == GLFW.GLFW_KEY_ESCAPE) { backFromSheet(); return true; }
            if (dev.koifih.client.input.Keybinds.matches(e)) return true;
            return super.keyPressed(e);
        }
        StringBuilder field = nameFocus ? profileName : searchFocus ? query : null;
        if (k == GLFW.GLFW_KEY_ESCAPE) {
            if (field != null) { searchFocus = nameFocus = false; return true; }
            close();
            return true;
        }
        if (field != null && k == GLFW.GLFW_KEY_BACKSPACE && field.length() > 0) { field.setLength(field.length() - 1); scrollGoal = 0; return true; }
        if (nameFocus && (k == GLFW.GLFW_KEY_ENTER || k == GLFW.GLFW_KEY_KP_ENTER)) {
            String n = profileName.toString().trim();
            notice = !n.isEmpty() && GuiConfig.saveProfile(n) ? LT.L147 + n : LT.L225;
            if (!n.isEmpty()) { selectedProfile = n; profileName.setLength(0); }
            profiles = GuiConfig.profiles();
            return true;
        }
        boolean ctrl = (e.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0;
        if (page == 0 && ((ctrl && k == GLFW.GLFW_KEY_F) || (k == GLFW.GLFW_KEY_SLASH && field == null))) {
            searchFocus = true; nameFocus = false; typedViaKey = true; return true;
        }
        if (ctrl && k == GLFW.GLFW_KEY_BACKSPACE && field != null) { field.setLength(0); scrollGoal = 0; return true; }
        if (dev.koifih.client.input.Keybinds.matches(e) && field == null) return true;   // menu key is handled by Keys
        // Type straight from key presses: works even when char events never reach this screen.
        if (!ctrl) {
            String name = GLFW.glfwGetKeyName(k, e.scancode());
            char c = 0;
            if (name != null && name.length() == 1) c = name.charAt(0);
            else if (k == GLFW.GLFW_KEY_SPACE) c = ' ';
            if (c != 0 && (Character.isLetterOrDigit(c) || c == ' ' || c == '-' || c == '_')) {
                if (field == null && page == 0 && c != ' ') { searchFocus = true; field = query; }
                if (field != null) {
                    if (field.length() < (field == query ? 24 : 32)) field.append(c);
                    if (field == query) scrollGoal = 0;
                    typedViaKey = true;
                    return true;
                }
            }
        }
        return super.keyPressed(e);
    }

    @Override
    public boolean charTyped(CharInput e) {
        if (typedViaKey) { typedViaKey = false; return true; }   // already inserted from the key press
        if (sheetInput()) {
            if (focus != null && focus.capturesInput()) return focus.charTyped(e);
            for (var c : controls) if (c.capturesInput() && c.charTyped(e)) return true;
            return false;
        }
        String str = e.asString();
        if (str.isEmpty()) return false;
        char c = str.charAt(0);
        if (nameFocus) { if (profileName.length() < 32 && (Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == ' ')) profileName.append(c); return true; }
        if (!searchFocus && page == 0 && Character.isLetterOrDigit(c)) searchFocus = true;   // type anywhere to search
        if (searchFocus && query.length() < 24 && (Character.isLetterOrDigit(c) || c == ' ')) { query.append(c); scrollGoal = 0; return true; }
        return false;
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x58c2,0xc14a,0x4df8,0x0b2b,0x0475,0x0b0a}, new char[]{0x4dbb,0x0b44,0x0b67,0x0417,0x58a3,0xc13e}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7351,0x932f,0xfa5c,0xb09d,0x5547,0xeaa9}, new char[]{0xeaca,0x733e,0x552a,0x934d,0xfa3d,0xb0e9}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8686,0x1c61,0x8935,0x85bd,0xf318,0x7d18,0x22cb,0xa0a9}, new char[]{0x1c2c,0x895a,0xf36e,0x22ae,0x86eb,0xa0cc,0x85d3,0x7d6c}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x68cb,0x013d,0x69d9,0x357c,0xd670,0x5375,0x3b9a,0xdcf8}, new char[]{0x0150,0x3bf5,0x69af,0x5310,0xdc95,0x68ae,0xd61e,0x3508}).plainString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3a64,0x9a4b,0x83ff,0x4f5d,0x13e7,0x58f8}, new char[]{0x3a36,0x1382,0x8391,0x589c,0x9a2e,0x4f2f}).toString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7c77,0x9983,0x77a1}, new char[]{0x7c12,0x77d8,0x99e6,0xfb1e}).plainString();
        static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4cc4,0xb262,0xf7e4,0x7a02,0x2da5,0x9f54}, new char[]{0x9f12,0xf78d,0x4caa,0x7a66,0x2dc0,0xb210}).toString();
        static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xae0b,0x7d2a,0xfc8e,0x3f69,0xec93,0x82f2,0xcd97}, new char[]{0x3f0a,0x7d45,0xcdfa,0xfcfe,0xecf2,0x8281,0xae78}).plainString();
        static final String L9 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xcdf0,0x7ff9,0xd73c,0xdfc5}, new char[]{0xcdbd,0x7f90,0xd74f,0xdfa6}).plainString();
        static final String L10 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x71b6,0x651d,0x5987,0x5539,0xae98,0x9a35,0x851f}, new char[]{0x656e,0xaef4,0x9a5c,0x555d,0x59e2,0x71c4,0x856c}).plainString();
        static final String L11 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa1e7,0x9eda,0xd8ef,0x610f,0x7087}, new char[]{0x9e9e,0xd880,0x6161,0xa192,0x70f3}).toString();
        static final String L12 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf16e,0xa3cd,0xec9b,0x17cf,0x5a3d,0x6061}, new char[]{0xa3be,0xf106,0x5a54,0x17aa,0xecf7,0x6005}).plainString();
        static final String L13 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc606,0x13cd,0x6cb1,0xd4af,0xa06b,0x21c8}, new char[]{0x218d,0xa00f,0xc66f,0x13b9,0xd4c0,0x6cc3}).toString();
        static final String L14 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xade2,0x7565,0xa6b2,0xe561,0x78a2,0x23d5,0x38f0}, new char[]{0xa6f1,0x750a,0x23bb,0x3896,0xe508,0xad85,0x78d1}).plainString();
        static final String L15 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0d0f,0x0c2d,0xb633,0x7213,0x73f7,0xa4a1}, new char[]{0x0c79,0x739f,0xb656,0x0d62,0xa4c4,0x7260}).plainString();
        static final String L16 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7036,0x630c,0x9c75,0x153d,0x8d96,0xef3d,0x3a62,0x02e9}, new char[]{0xef56,0x028c,0x8def,0x7054,0x3a0d,0x636d,0x154f,0x9c11}).plainString();
        static final String L17 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8c79,0x996c,0x53e4,0x9c4f,0x254d,0xd828}, new char[]{0x8c1f,0x538b,0x2521,0x9c2b,0xd84d,0x991e}).plainString();
        static final String L18 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8240,0xf07f,0xe8e8,0xe509,0x536d,0x2f00,0xee26}, new char[]{0xe898,0x2f61,0xf013,0xee43,0x8234,0xe57d,0x5308}).plainString();
        static final String L19 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd5f0,0x7e78,0xfaa4,0xefa6,0xf83b,0x3589,0x097d,0x76c8,0x2652}, new char[]{0xf84c,0xfac5,0x2626,0xefc3,0x35fb,0xd5de,0x0911,0x7e17,0x76a4}).plainString();
        static final String L20 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x059a,0x34c7,0xdc29}, new char[]{0x3486,0x05f6,0xdc45,0xa1d6}).plainString();
        static final String L21 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8d6b,0x38ca,0xae83,0xc9b1,0xc3ba}, new char[]{0xaed0,0x8d1c,0xc3d5,0x38b8,0xc9d5}).plainString();
        static final String L22 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xdb8d,0xc6c9,0x2088,0x3456,0x0e8c,0xf67c,0x0c85}, new char[]{0x0cc6,0x20fa,0x342f,0xf60f,0xdbf9,0xc6a8,0x0ee0}).plainString();
        static final String L23 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd5b2,0x36aa,0x0fc5,0x7e27}, new char[]{0x36e9,0xd5d3,0x0fb7,0x7e53}).toString();
        static final String L24 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3bab,0x257a,0x723b,0x398b}, new char[]{0x39c6,0x251b,0x7258,0x3bce}).plainString();
        static final String L25 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x140a,0x37c7,0x01b9,0xb34a,0x0573}, new char[]{0x0520,0x01c9,0xb32f,0x37a6,0x1478}).plainString();
        static final String L26 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xded1,0x46b2,0x25c2,0x4b69,0xb0d9}, new char[]{0x46fd,0xb0ad,0xdeb9,0x25a7,0x4b1b}).toString();
        static final String L27 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc863,0x3ec3,0xf830,0x4fc0}, new char[]{0x3ea0,0xf851,0xc811,0x4fb4}).plainString();
        static final String L28 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf245,0xf6d7,0xe3c2,0xe2e6,0x624d,0x8a49,0x5630}, new char[]{0xe285,0xf237,0x5649,0xf6a4,0x6239,0x8a28,0xe3ae}).plainString();
        static final String L29 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6f09,0x4be0,0x337d,0xf5d1,0x0739,0xf68d}, new char[]{0x4b81,0x3313,0xf6ee,0x6f61,0x0756,0xf5a3}).plainString();
        static final String L30 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xcde8,0xda1f,0xdbda,0x3414,0xfcb9}, new char[]{0xdbae,0xda70,0xcd9c,0xfcdc,0x3479}).plainString();
        static final String L31 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3dc6,0x1f21,0x2cae,0xc091,0x2a56,0x8de7,0x2b0e,0x20dc,0x3035,0x1e32,0x58e1}, new char[]{0x5885,0x1f4e,0x3040,0x20be,0x2a3a,0x2ccb,0x1e12,0xc0f9,0x8d86,0x3da8,0x2b6a}).plainString();
        static final String L32 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa9e4,0x90b5,0xb093,0xd538}, new char[]{0x90d6,0xa98d,0xd54c,0xb0ea}).plainString();
        static final String L33 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x83a7,0x8f2e,0x04b1,0xbe59,0x4399,0x003d,0x7302,0xa9d4}, new char[]{0x736d,0x43fb,0xa9a7,0x83ce,0x0059,0xbe30,0x04d0,0x8f40}).plainString();
        static final String L34 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4ffa,0x1537,0x774b,0x95dd}, new char[]{0x95b2,0x7729,0x4f98,0x154e}).plainString();
        static final String L35 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4e36,0x28bc,0xa291,0x2741}, new char[]{0x272c,0x4e57,0x28df,0xa2f4}).plainString();
        static final String L36 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb76a,0x1dba,0x3a14,0xfb22,0x56be,0x1d89}, new char[]{0xb708,0x1dfb,0x3a71,0xfb43,0x1dd9,0x56d6}).plainString();
        static final String L37 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa14b,0x5be0,0x3247,0xc036,0x79a2,0x7257,0x8253}, new char[]{0x79c6,0x5b85,0x823d,0x3234,0x723e,0xc042,0xa132}).plainString();
        static final String L38 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5f9c,0x9d04,0xb8c8,0x480a,0x4481,0xf48a,0xdcce,0x5d0f,0x0ffb,0xbd63,0x23e6}, new char[]{0x9d73,0xdca7,0x2388,0xbd07,0x482a,0x5d6c,0xf4e2,0xb8a9,0x44f3,0x0f9c,0x5ff9}).plainString();
        static final String L39 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x95ac,0x0a93,0x97e5,0x71fa,0x3dcf}, new char[]{0x95df,0x9795,0x0af6,0x3dae,0x7188}).plainString();
        static final String L40 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf307,0x8808,0x7556,0x152a,0x722a}, new char[]{0xf36b,0x155f,0x7244,0x7531,0x886d}).plainString();
        static final String L41 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xbd50,0x065e,0xf224,0x367e}, new char[]{0x360d,0x062a,0xf251,0xbd3e}).plainString();
        static final String L42 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x362b,0x346d,0x31cb}, new char[]{0x3648,0x3402,0x31a9,0x4c91}).plainString();
        static final String L43 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9613,0x418a,0xecfc}, new char[]{0x41eb,0x967a,0xec91,0x726a}).plainString();
        static final String L44 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xea16,0xc2a9,0x4259,0x3382}, new char[]{0x33e3,0xea63,0xc2db,0x4238}).plainString();
        static final String L45 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc3c8,0xe4c3,0xa2bb,0x0195,0xceb1,0x4bd4,0xab0d}, new char[]{0xa2cf,0xc3ba,0x01fc,0xab6a,0xe4a4,0xced4,0x4ba6}).plainString();
        static final String L46 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2358,0x7846,0x05e3,0xf76b,0xd4e8,0x47af}, new char[]{0x058b,0xd481,0x232c,0x47cd,0xf704,0x783e}).plainString();
        static final String L47 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0743,0x7335,0x248e,0xe4e1,0xb51f,0xf1b7,0xdf6b,0x0780,0x3a63}, new char[]{0xf1d5,0x07e1,0x24ed,0x735e,0xb56b,0xe493,0xdf0a,0x3a00,0x0728}).plainString();
        static final String L48 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xae45,0xc2f6,0xdf40,0x8c47,0x0874}, new char[]{0xae24,0x8c22,0xc291,0x081d,0xdf33}).plainString();
        static final String L49 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x81bf,0xce1d,0x7e95,0xaff2,0x4896,0xb86e,0xb054,0x84d0}, new char[]{0x81c9,0xaf97,0x7ef9,0x48f9,0xb037,0xce74,0x84a4,0xb817}).plainString();
        static final String L50 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa1bd,0x727b,0x3bf8,0x14f2,0xc4ed}, new char[]{0x7218,0xa1d2,0x3b95,0xc48f,0x149d}).plainString();
        static final String L51 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3f5c,0xc0a9,0x07fd,0x89aa,0xb5bb,0x205a,0x937e,0xf361,0xc041,0x2b43}, new char[]{0xf30b,0xc0dc,0x2037,0x3f2c,0x2b63,0x930c,0xc024,0x078e,0x89cf,0xb5cf}).plainString();
        static final String L52 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5877,0x3fe7,0x33df,0x70d2}, new char[]{0x3f84,0x70a0,0x581e,0x33ab}).plainString();
        static final String L53 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x29d3,0x407e,0xc10c,0x261b,0x03e4}, new char[]{0x400c,0xc169,0x267a,0x29b0,0x038c}).plainString();
        static final String L54 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3180,0x13c2,0x1bdc,0x182b,0x577c}, new char[]{0x31f7,0x13e2,0x1ba8,0x571d,0x185b}).plainString();
        static final String L55 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x637b,0xf1e6,0xa5f7,0x713e}, new char[]{0xf191,0x714a,0xa596,0x630b}).plainString();
        static final String L56 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xee3a,0xd127,0xfdae,0xcd68,0x6e34,0xa777}, new char[]{0xcd1b,0xd14e,0xa71b,0xee5f,0x6e5a,0xfdda}).plainString();
        static final String L57 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xeebf,0x07a7,0xe702,0x3a64,0xc39d,0xbeb9,0x5b30}, new char[]{0xc3ed,0x07c8,0xbed0,0x3a0a,0xe776,0x5b55,0xeecd}).plainString();
        static final String L58 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa5b0,0x98aa,0xe308,0x510a}, new char[]{0x5161,0x98c3,0xe364,0xa5dc}).plainString();
        static final String L59 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xcf43,0xf46f,0xc4ec,0x5819,0x6223,0x0a23}, new char[]{0xf41c,0x0a53,0xc49e,0x5870,0xcf2d,0x6257}).plainString();
        static final String L60 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0d34,0x19dc,0x604b,0x50f6,0x58a3,0x21dc,0x29cb,0xca56}, new char[]{0x21af,0x58cd,0x5097,0x0d44,0x29eb,0xca22,0x19bd,0x603b}).plainString();
        static final String L61 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa4cf,0xf3f3,0xfcd8,0xb1a2,0xb218,0x5403,0xb626,0x6adc,0x305d,0xaf64,0xbf4c,0x7f64}, new char[]{0xaf05,0xb26d,0xb652,0xbf23,0x7f44,0xfcbb,0xb1ce,0x546a,0x6abf,0xa4a4,0xf396,0x302f}).toString();
        static final String L62 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x51a9,0xcb52,0x4930,0xa297,0x329c,0x73a6,0xe28b}, new char[]{0xe2e9,0x51c8,0x4953,0x73cd,0xa2f8,0x32fa,0xcb34}).plainString();
        static final String L63 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x464d,0x3593,0x5b19}, new char[]{0x5b71,0x4624,0x35e7,0x2e48}).plainString();
        static final String L64 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa09e,0x5eaf,0xf056,0xdc3d,0x5cf1,0x1053}, new char[]{0xdc5b,0x5ec6,0x103d,0xa0fa,0x5c94,0xf024}).plainString();
        static final String L65 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0c10,0xbd99,0x885f,0x8e80,0x77ea,0x1f1d,0x00dd,0xcd6d,0x3e41,0x5029}, new char[]{0x3e32,0xbded,0x7798,0x00b2,0xcd03,0x8ee7,0x0c78,0x5046,0x1f71,0x883b}).plainString();
        static final String L66 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1ff7,0x80cd,0x3f28,0x982f,0x699a,0x5c24}, new char[]{0x5c54,0x80a2,0x69e8,0x985b,0x3f49,0x1f9b}).plainString();
        static final String L67 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9b4f,0xba67,0xfece,0xafd0,0x79cf,0x41b3,0x3846}, new char[]{0xfeac,0x79ae,0x3835,0xba13,0x9b26,0x41dc,0xafbe}).plainString();
        static final String L68 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc2a1,0x1eae,0x08e3,0xaccf,0x4527,0x9cfc,0x5d27,0x35a2}, new char[]{0x4541,0x088c,0xc2d3,0x35d6,0x1edc,0x9c99,0x5d54,0xacbc}).plainString();
        static final String L69 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xfc7c,0x01a9,0x28fc,0x5c0e,0x3119,0x1107,0x39ad}, new char[]{0x39db,0xfc15,0x116b,0x2890,0x5c6f,0x317e,0x01cc}).plainString();
        static final String L70 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5a44,0x379b,0x52d2,0x53fe,0xd771,0x072b,0xd27a,0x2d24,0xb96b,0x4b00,0x4516,0x227d}, new char[]{0xd710,0x2213,0x5a27,0x37f2,0x2d41,0x4b6e,0x538a,0xd25a,0x4575,0xb902,0x52a6,0x0752}).plainString();
        static final String L71 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6a93,0xdd74,0xd77e,0x8866}, new char[]{0xd706,0xdd16,0x8809,0x6ae4}).plainString();
        static final String L72 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x21d6,0x701e,0x43eb,0x9604,0x213f}, new char[]{0x707a,0x214d,0x9665,0x21bf,0x4385}).plainString();
        static final String L73 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd28f,0xa26c,0x1d26,0xf5c3,0xe038,0x6f5a}, new char[]{0xd2fd,0x1d43,0xf5a5,0xa205,0x6f36,0xe054}).plainString();
        static final String L74 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6254,0xd83f,0x655f,0xc1c3,0x0c9d,0xa87a,0xdbb3}, new char[]{0xd84c,0xa80e,0x6231,0xc1a2,0xdbdf,0x653a,0x0cef}).plainString();
        static final String L75 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0c0d,0xad3c,0xa792}, new char[]{0xa7fa,0x0c78,0xad58,0x035e}).plainString();
        static final String L76 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe0d5,0x5afc,0x3070,0x5a3a,0x4a68,0xf136,0x9f8d,0x4b07,0x7405}, new char[]{0x746c,0x5a54,0x4a0c,0xf15f,0x4b64,0x3011,0x5a88,0x9fe2,0xe0a7}).plainString();
        static final String L77 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4ba1,0xe61f,0xc171,0x9a1f,0xb18b,0xd9ea}, new char[]{0xd987,0xe67e,0x9a6d,0x4bca,0xc114,0xb1f9}).plainString();
        static final String L78 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0185,0x300d,0xf1c7,0x499c,0x7432,0xf57c,0xb297,0x129b,0xfbb4}, new char[]{0x3079,0x7440,0xb2f6,0x49f6,0x01e0,0xf51f,0x12ef,0xfbdb,0xf1b5}).plainString();
        static final String L79 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2eaa,0x6295,0x7415,0x56bc,0xf707,0xeef8,0x5de1,0xfcae,0xa09f,0x9b6d}, new char[]{0x56ca,0x747c,0x9b1e,0x62e0,0x2ecb,0xee94,0xf76e,0xfcd4,0x5d84,0xa0ed}).plainString();
        static final String L80 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5a95,0xd649,0x00d3,0x13a4,0x52ae}, new char[]{0x5ae6,0x52de,0xd62c,0x13c1,0x00b7}).plainString();
        static final String L81 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4857,0x3801,0x17d8,0x4716}, new char[]{0x4824,0x4762,0x17bd,0x3871}).plainString();
        static final String L82 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x10a0,0x7d40,0x1bca,0xf4ce,0x79a0,0xd784,0xaed0}, new char[]{0x79ce,0xf4a1,0xaef0,0x7d33,0xd7e8,0x10cf,0x1bbd}).plainString();
        static final String L83 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x84fe,0x4b41,0x6c85,0x1b5f,0x3159,0x4e54}, new char[]{0x6ceb,0x3136,0x4b32,0x8492,0x4e3b,0x1b28}).plainString();
        static final String L84 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x87d8,0x4e07,0x44fe,0x8ac5,0x12aa,0x9d63}, new char[]{0x87b6,0x9d0c,0x128a,0x4e70,0x8aa0,0x449c}).plainString();
        static final String L85 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8aa2,0x012c,0xaf06,0xb390,0x0940}, new char[]{0x092e,0xb3ff,0x015b,0x8ac7,0xaf64}).plainString();
        static final String L86 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa446,0xc3cf,0xd798,0xeb40}, new char[]{0xa42c,0xc3ba,0xeb2d,0xd7e8}).plainString();
        static final String L87 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0e03,0x35a7,0x750c,0x9f53,0x4bf9,0xa1ec,0x0f12,0xca93,0xeac0,0xfeea,0x2f7c,0x6da0,0x61f8,0xd110}, new char[]{0xd17c,0x0f67,0x2f12,0x0e64,0x7569,0xfeca,0xcaf0,0x9f3c,0x6dcf,0x35cb,0xeaa4,0x4b96,0xa19b,0x6196}).plainString();
        static final String L88 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xbafc,0x512e,0x7bb1,0x2fc6,0x49ba,0x7bcc}, new char[]{0x7bbf,0x2fb2,0x7bd0,0x5147,0x49c8,0xba8f}).plainString();
        static final String L89 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xde19,0x859c,0x65d3,0xd6ef}, new char[]{0x85eb,0x65b2,0xd683,0xde72}).plainString();
        static final String L90 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa3c1,0x72cc,0x0536,0xae16,0x025a}, new char[]{0xae62,0x055f,0x0237,0xa3a4,0x72be}).plainString();
        static final String L91 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2fcc,0x6125,0x949e,0x1da8,0x0a1b,0x4df4,0xc5e2}, new char[]{0x94f8,0x4d86,0x2fa9,0xc587,0x1dcb,0x6144,0x0a76}).plainString();
        static final String L92 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3907,0x9ec7,0xa912,0xea38,0xddd4,0xbc32,0xfe76}, new char[]{0xea56,0xddbb,0xfe56,0xbc54,0x3966,0xa97e,0x9eab}).plainString();
        static final String L93 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9a38,0x8f5e,0x8fb9,0x4a76,0xdfff,0x88fa}, new char[]{0x8894,0xdf90,0x4a10,0x9a59,0x8f32,0x8fd5}).plainString();
        static final String L94 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8972,0x4ec5,0x3f96,0xe2ae}, new char[]{0x8917,0x4ea1,0x3ff1,0xe2cb}).plainString();
        static final String L95 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xceb2,0xbae1,0xc13b,0x0f5b,0xfac6,0xbfe4}, new char[]{0xc15d,0x0f37,0xcedb,0xfaa1,0xba89,0xbf90}).plainString();
        static final String L96 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8227,0xe7e2,0xad64}, new char[]{0xad02,0x824b,0xe79b,0x84b7}).plainString();
        static final String L97 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6070,0x4c5a,0xd4e6,0xa42e,0x2468,0x1502}, new char[]{0x1567,0xd48a,0x6009,0x241c,0xa45c,0x4c3b}).plainString();
        static final String L98 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6130,0xbf35,0x68b0}, new char[]{0xbf50,0x6143,0x68c0,0x7b5f}).plainString();
        static final String L99 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7e2f,0x1bbc,0x245c,0x2cee,0x2aeb,0x57d5}, new char[]{0x2c9c,0x2a8e,0x2432,0x7e4b,0x57b0,0x1bce}).plainString();
        static final String L100 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4624,0x191d,0xf3ce,0x6751,0x8dd8,0x7641,0x9e2b}, new char[]{0x1973,0x8db9,0x9e46,0xf3ab,0x6725,0x7620,0x4643}).plainString();
        static final String L101 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xbe3e,0xd209,0x3162,0xae98,0xf598}, new char[]{0xbe59,0x310e,0xaef9,0xf5eb,0xd27a}).plainString();
        static final String L102 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf22b,0x9d89,0x23a0,0x3fa8,0x5c68,0x4cfd}, new char[]{0x23c5,0x5c0e,0x3fce,0x9dec,0x4c9e,0xf25f}).plainString();
        static final String L103 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc12e,0xc139,0x9503,0xd16c,0x3308}, new char[]{0x9577,0xd11e,0xc14f,0xc150,0x3364}).plainString();
        static final String L104 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x526a,0xa001,0xb674,0x9eb9,0xa2d0,0x2c85,0xdee6,0x00a4}, new char[]{0x00d4,0x520b,0xb606,0xa075,0x9ed0,0xa2b3,0x2ce9,0xde83}).plainString();
        static final String L105 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xdd5f,0x0558,0x3ffb,0x82fc,0x61b2,0x347c,0xf6b3,0x959b,0x1e7a}, new char[]{0xf6d0,0x3f89,0x1e15,0x828f,0xdd2c,0x3414,0x61d3,0x0531,0x95e9}).plainString();
        static final String L106 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x14ce,0x39bb,0xb6b0,0x59d9}, new char[]{0x59a3,0x14a1,0xb6df,0x39d6}).plainString();
        static final String L107 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xea05,0x8b43,0xcc12,0x10a5,0x261e,0x8c97}, new char[]{0x8b21,0x8ce5,0xea6c,0xcc75,0x2676,0x10d1}).plainString();
        static final String L108 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x393a,0x237a,0xaae0}, new char[]{0x2309,0x3951,0xaa99,0x7819}).plainString();
        static final String L109 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7dbd,0x05d0,0x3993,0x6914,0xc01c,0xd127,0xb786}, new char[]{0xd148,0x7dc8,0x05a4,0x6978,0xb7ef,0x39fd,0xc079}).plainString();
        static final String L110 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8cc2,0x131c,0xfd05,0xe5b8}, new char[]{0xfd63,0x1375,0xe5d4,0x8cae}).plainString();
        static final String L111 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xcc3c,0x6f39,0xd4d0,0xdefe}, new char[]{0xde9d,0x6f58,0xd4a0,0xcc59}).plainString();
        static final String L112 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0f23,0x669a,0x108e,0xf0db}, new char[]{0xf0a8,0x66f1,0x10e7,0x0f4d}).plainString();
        static final String L113 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x83f8,0xeed7,0x0bce,0xc705}, new char[]{0x0bad,0x8390,0xeeb6,0xc768}).plainString();
        static final String L114 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x26b8,0xca58,0xfc11,0x3565,0x629c,0xcfb3,0xa202,0xb894}, new char[]{0x62ec,0xa270,0xcfdc,0x26de,0xb8fd,0x3509,0xca3d,0xfc63}).plainString();
        static final String L115 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8fd7,0xe9aa,0x4088,0x6278}, new char[]{0x8fa3,0x6211,0x40e5,0xe9cf}).plainString();
        static final String L116 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x23d9,0xded6,0xedb1,0x1e6a}, new char[]{0x23bb,0xdeba,0xedc4,0x1e18}).plainString();
        static final String L117 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5894,0x567d,0x72a7,0x0c5e,0x7f4a}, new char[]{0x72d4,0x7f25,0x58e1,0x0c30,0x5619}).plainString();
        static final String L118 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xacbe,0xff8b,0xa00c,0x4cee,0xfed7}, new char[]{0xa07f,0xacc9,0xfebe,0xffe5,0x4c89}).plainString();
        static final String L119 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x670b,0x40a1,0x1427,0x7e46,0xd2c2,0x5370,0x213c,0xa0cf,0xe614,0x1438}, new char[]{0x676f,0x40d4,0xa0bd,0x5311,0x1445,0x1451,0x7e2a,0x2155,0xe660,0xd2bb}).plainString();
        static final String L120 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2b78,0x2b65,0x2d01,0x550a,0xfd72,0x4201,0xfdd3}, new char[]{0x2d63,0x2b00,0x4260,0x5578,0xfd1b,0xfdbd,0x2b1f}).plainString();
        static final String L121 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4aba,0x5be7,0xfab0,0x07e3,0xb22b,0x3a6a,0xcc32,0xb4c8}, new char[]{0xb4bf,0x4adb,0x3a13,0x0793,0x5b88,0xb242,0xfade,0xcc46}).plainString();
        static final String L122 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5cbd,0xd388,0x5a87,0xd5fc,0xad25}, new char[]{0xd3fb,0xad51,0x5ae6,0xd588,0x5cce}).plainString();
        static final String L123 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3f0c,0x4418,0xd30f,0x70ed,0xe38b,0x633c}, new char[]{0xd36c,0x4471,0x3f7e,0xe3e8,0x6350,0x7088}).plainString();
        static final String L124 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1d2c,0x8181,0xb5b9,0xdf7c,0x334e,0x4c08}, new char[]{0x4c7e,0x81e8,0x333d,0xb5cc,0x1d4d,0xdf10}).plainString();
        static final String L125 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x605a,0x5773,0xad93,0xb073,0xa510,0xd284,0x13d8,0xd924,0xa43e,0x6c4f,0xe8e3,0xbe21}, new char[]{0xa57e,0x571c,0x6c3b,0xbe48,0xd942,0xb01a,0xa45d,0xd2e5,0xe897,0x6033,0xadfc,0x13b6}).plainString();
        static final String L126 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe87a,0xa471,0x9638,0x04cc,0xc452,0x69d4}, new char[]{0x69bc,0x9657,0xa405,0x04ae,0xc433,0xe808}).plainString();
        static final String L127 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe534,0x1e2d,0x37d8,0xb1e1,0x4913}, new char[]{0x37a8,0x4972,0xb18f,0xe551,0x1e41}).plainString();
        static final String L128 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9643,0xd710,0x41d9,0x3d75,0xd04c,0x3b1a,0x58a0,0xd9b0}, new char[]{0x41b8,0x962e,0x3d17,0xd779,0xd9d5,0x58ce,0x3b79,0xd029}).plainString();
        static final String L129 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd3e2,0x3017,0x29bb,0x0a7b,0x2e95,0x355c}, new char[]{0x2ef6,0x3076,0x0a16,0xd387,0x352e,0x29da}).plainString();
        static final String L130 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0704,0x70e4,0x7de1}, new char[]{0x0762,0x7d8e,0x7092,0x59dc}).plainString();
        static final String L131 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x23d9}, new char[]{0x23e3,0x91be,0x2e52,0xb78b}).plainString();
        static final String L132 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x80d0,0xb2dc,0x8331,0xb280,0x5199}, new char[]{0x51ee,0xb2e1,0xb2a8,0x8354,0x80a2}).plainString();
        static final String L133 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9f67,0x02af,0xead5,0x3354}, new char[]{0x0281,0xeab9,0x9f08,0x3338}).plainString();
        static final String L134 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe550,0x9187,0x857e,0x6d34,0xbf99,0xf94f,0x812c,0xf3af,0x7c11,0x3fb6}, new char[]{0xf92c,0x7c70,0x6d40,0x91d8,0x814f,0xbff6,0x3fdb,0xf3cd,0xe531,0x850a}).plainString();
        static final String L135 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1a27,0xfd55,0x8e6b,0x70d6,0x0ef5,0xe314,0x2c30,0xa008,0xbba4,0x6e4b,0xbed4,0x5115}, new char[]{0xa06b,0xe375,0x6e3f,0xbbfb,0x5178,0xfd3a,0x8e1d,0x1a42,0x70bb,0x2c55,0x0e9b,0xbea0}).plainString();
        static final String L136 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc1cf,0x3c68,0xace6,0x238d,0x951f,0xc4c3,0x36a4,0x2ba4,0xf4f0,0x5c0b}, new char[]{0xac85,0x5c6a,0xc1bb,0x23d2,0x36d2,0x3c01,0x956c,0xc4b6,0x2bc5,0xf49c}).plainString();
        static final String L137 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6c5e,0x964a,0xe1d1,0x284b,0x3ff2,0x8302,0xde12,0x75f8}, new char[]{0x9629,0xde73,0x8376,0x6c01,0x3f9f,0x2822,0xe1a2,0x759b}).plainString();
        static final String L138 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6f23,0xfe49,0xd039,0x1073,0x23a5,0xf9dc,0x3899}, new char[]{0xfe2a,0xf9b3,0x101d,0x23c3,0x6f4a,0xd05e,0x38ea}).plainString();
        static final String L139 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5013,0xa0e7,0x33ed,0xa838,0xf23f,0xf36d}, new char[]{0xf24b,0x507b,0xa082,0x3380,0xa85d,0xf31e}).plainString();
        static final String L140 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7355,0x22b0,0x6eed,0x7236,0xbe88,0x4860}, new char[]{0xbefb,0x7253,0x4801,0x7327,0x22d3,0x6e85}).plainString();
        static final String L141 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x267f,0x456f,0xaf61,0xfc39,0xa084,0x43f3}, new char[]{0xfc6a,0x4396,0xaf00,0x260d,0xa0e7,0x4507}).toString();
        static final String L142 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9f3b}, new char[]{0x9f64,0x8c84,0xf7d5,0x0513}).plainString();
        static final String L143 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x326f,0x4257,0xdeeb,0xe730,0xda1c,0x53a9,0x7ee9,0x9d5a,0x30f7,0x7f91,0x39e0,0x3397,0x5133,0xe092,0x9e17}, new char[]{0x30b9,0x33f8,0x4277,0x3202,0x9d35,0x3984,0xe745,0xda70,0x7ff4,0x5140,0x7ec9,0xde83,0xe0f7,0x9e65,0x53cc}).toString();
        static final String L144 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x200c,0x71c1,0xb932,0x4709,0x02dd,0x1909,0xb3fe}, new char[]{0x02b9,0xb957,0x196f,0x4768,0xb38b,0x2060,0x71b5}).plainString();
        static final String L145 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc024,0x22ff,0x28ae,0x7656,0x234b,0x5941}, new char[]{0x22bc,0xc056,0x7633,0x232a,0x28da,0x5924}).toString();
        static final String L146 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x21db,0x6d39,0x4823,0xdf4e,0x0d87,0xaea2,0xadc6,0x5ad9,0x8c8a,0x3be1,0xc5d9,0x8c61,0x1e3c,0xb155,0x81d5,0x921e,0x5310}, new char[]{0xb101,0x1e45,0xdf3e,0x3b84,0xade6,0x5ab8,0x8caa,0xaecc,0x4842,0x21b6,0x927b,0x81f5,0x8c07,0x5379,0x0df5,0x6d4a,0xc5ad}).toString();
        static final String L147 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5652,0x77dc,0x6548,0xa2d7,0x4122,0x017a,0x1016,0x0625}, new char[]{0x4161,0x1064,0x011f,0x0644,0x653c,0x77b9,0x5636,0xa2f7}).toString();
        static final String L148 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc54c,0x9991,0xf355,0x1dcf,0x94e8,0x725e,0xe728,0xa8c0,0xa226,0xa693,0xaf4e,0xb51c,0xac88,0xbbc3,0x2d36,0x4271}, new char[]{0x1d8c,0xa249,0x949d,0x2d5a,0xe74c,0xace6,0x7279,0x4205,0xc56c,0x99f2,0xf327,0xb579,0xbba2,0xaf3a,0xa6f6,0xa8e0}).toString();
        static final String L149 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8de0,0x31f9,0x6cea,0xd88d}, new char[]{0x8db3,0x3198,0xd8fb,0x6c8f}).toString();
        static final String L150 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf1ba,0xfc88,0x1fbd,0xbeed,0x0115,0xe831}, new char[]{0xbebe,0x0174,0xf1cc,0xfced,0xe855,0x1f9d}).toString();
        static final String L151 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe8ff,0xfeee,0x69b9,0x4eb8,0x8e25,0x66f0,0x123c,0x9cd2,0x34aa,0x05e9,0xd54e,0x121c,0x5939}, new char[]{0x34e9,0xd521,0xfe9b,0x0585,0xe89b,0x1272,0x121b,0x9ca6,0x5919,0x4ecb,0x69d8,0x8e53,0x6695}).toString();
        static final String L152 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x830c,0x77cb,0xb97f,0x127a,0x0733,0xde5f}, new char[]{0x779e,0x120a,0x075f,0x8363,0xb91e,0xde3b}).toString();
        static final String L153 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x533d,0xda83,0x0bb2,0x24c6,0xc06b,0x8c3f,0xe5f7,0x31fe,0x9b79,0x5222,0xaa27,0x48b0,0x1bf3,0x0750,0x0189,0xb8e1,0xe655,0x193b,0x221a,0x99ac,0x705b,0x0125,0x1a44,0x680d,0x6390,0x633f,0x27c8,0xba04,0x1b15,0x6990,0x9ea9,0x5066,0x321b,0xe18c,0x661e,0xf204,0x08ca,0xb9aa,0xb21c,0x1b07,0x8577,0xe243,0x1379,0x1506,0xa79c,0xbb58,0xf1be,0x83bb,0xbb13,0x6484,0x4b64,0x7a03,0x6032,0x874c,0x3558,0xc42d}, new char[]{0xbb57,0x27ba,0x5009,0xdaf3,0x191b,0x7075,0x6867,0x130a,0xa7f3,0xb88f,0x31de,0x64f4,0x2268,0xbb37,0x99ca,0x01e0,0x69fc,0x327e,0x1b66,0xba24,0x6356,0xe1e2,0x1a30,0xc004,0xe5d7,0xc459,0x83d3,0x8512,0x663e,0xb9cc,0xb273,0xe22f,0x9ecd,0x353d,0x0157,0x489c,0x4b44,0x7a77,0x8c57,0x0735,0x524c,0x1bd3,0x6040,0x1563,0xaa48,0x63e0,0x0bd7,0x9b17,0xe675,0x537e,0xf26b,0x24a8,0x08ac,0x1b6e,0x872b,0xf1cd}).toString();
        static final String L154 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb696,0xce1e,0x0ff3,0x4ec9,0xb0c3,0x2a96,0x0143,0x8708,0x0b82,0xb7b7,0x9d4d}, new char[]{0x875b,0x9d2c,0x0bf4,0xb6f3,0xce3e,0x4e8a,0x012c,0x2af8,0xb0a5,0xb7de,0x0f94}).toString();
        static final String L155 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4435,0x6650,0xd356,0x440d,0x31b5,0x40f9,0x726d,0xfdd9,0x6265,0xe2a0,0xa8d8,0x594c,0xdb9f,0xedcf,0xaf2c,0x7522,0x0124,0x34df,0xaec6}, new char[]{0x722e,0xdbea,0x0156,0x593e,0xa8bd,0x34b1,0x4479,0x3195,0xaf5f,0xaea3,0xedbb,0x6210,0xe2d0,0xd376,0xfdaa,0x6631,0x7554,0x409c,0x4451}).toString();
        static final String L156 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4d4c,0x5926,0xe901,0xae3a,0x79c4,0x5694,0xd74b,0xefdc,0x739e,0xc14b,0xd868,0x9119,0x308c,0xde34,0x0b49,0xf9c1,0x29f1,0xdffe,0xba9e,0xb9f5,0x42b8}, new char[]{0x42f6,0xb99a,0x73be,0x4d3f,0xdf9f,0x916f,0xd80d,0xefb8,0xe921,0xae4a,0x2983,0xbaf1,0x56f2,0xc122,0x30e0,0x5943,0x79b7,0xd76b,0xde4d,0xf9a4,0x0b3d}).toString();
        static final String L157 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x098d,0x2904,0x3390,0xbc08}, new char[]{0x09c1,0x33ff,0xbc69,0x2960}).toString();
        static final String L158 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x04ac,0xb6c7,0xf343,0x3405,0xf130,0xbe74,0xf394}, new char[]{0xbe38,0xf15f,0xf3f5,0xf327,0xb6a2,0x3461,0x048c}).toString();
        static final String L159 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9856,0xc318,0x06cf,0x8662,0x72d3,0x2312,0x826c,0xa3f9,0xee16,0x2fec,0x7484}, new char[]{0x74c8,0xee79,0x9837,0xc37c,0x06ef,0x2f8a,0x2373,0xa390,0x8200,0x8607,0x72b7}).toString();
        static final String L160 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0612,0x4d47,0x2c2e,0xf5e9,0x4654,0xb246}, new char[]{0xb202,0x4631,0x4d2b,0xf58c,0x2c5a,0x0677}).toString();
        static final String L161 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x657b,0x1466,0x1d76,0xd898,0x9a69,0xf143,0x6dfa,0xf8c4}, new char[]{0xf107,0x651e,0x9a05,0x1403,0xd8ec,0x1d13,0x6d9e,0xf8e4}).toString();
        static final String L162 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0029,0xea7a,0x7083,0x8c11}, new char[]{0x70d1,0x005c,0xea18,0x8c68}).toString();
        static final String L163 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x68ad,0x4b14,0x4ebd}, new char[]{0x4eef,0x68c8,0x4b70,0x05b1}).toString();
        static final String L164 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x67fc,0x2bba,0xceac,0x250c,0x6976,0x2f98,0x9bdf,0x22a1,0x2b09}, new char[]{0xcef8,0x9bbe,0x2ff6,0x22c6,0x6913,0x257e,0x2b60,0x2bd4,0x6799}).toString();
        static final String L165 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x88dd,0xf5ea,0x2b2b,0x969a,0x0f00,0xf78e}, new char[]{0x0f59,0x96ff,0xf7e2,0x88b1,0xf585,0x2b5c}).toString();
        static final String L166 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x61df,0xf10a,0xbf11}, new char[]{0xf159,0x61aa,0xbf7f,0x0fc8}).toString();
        static final String L167 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0be5,0x7253,0xbc5c,0x2074}, new char[]{0x721f,0x201d,0xbc31,0x0b80}).toString();
        static final String L168 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6e99,0xd46d,0x1bcb,0xf89e,0x71e1}, new char[]{0x71a6,0x1bb9,0xf8fb,0xd408,0x6ef7}).toString();
        static final String L169 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5779,0x9e92,0x883a,0xcb45,0xa68e,0xa35e,0xfd3c}, new char[]{0x887f,0x5714,0xa6eb,0xfd4e,0xcb24,0xa332,0x9ef6}).toString();
        static final String L170 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb843,0x8f43,0x5aca,0x8a38}, new char[]{0x8f00,0x8a41,0x5aab,0xb82d}).toString();
        static final String L171 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xaa35,0x0f8e,0xd097,0x552c}, new char[]{0xaa77,0x0fe2,0x5559,0xd0f2}).toString();
        static final String L172 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb99e,0x97c3,0xd51f,0x3451,0x5def}, new char[]{0x3410,0x97b9,0xb9eb,0xd56d,0x5d8a}).toString();
        static final String L173 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xbd88,0xbe9c,0x1728,0x9318,0x8167,0x0a88}, new char[]{0x0ade,0xbef5,0x1747,0xbde4,0x8102,0x936c}).toString();
        static final String L174 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe8d3,0x9abd,0x8dcb,0x5655,0x4f29,0x5ac0}, new char[]{0x5a90,0x9ac8,0x8db9,0x5625,0x4f45,0xe8b6}).toString();
        static final String L175 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x58dc,0x98ce,0xd17a,0x2809,0xad90,0x5bde,0x9145}, new char[]{0x5891,0xd11b,0x5bb9,0x9120,0x2867,0x98ba,0xadf1}).toString();
        static final String L176 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb646,0x57c5,0xae9a,0x20e5,0x1f0c,0x2273}, new char[]{0xb609,0xaee8,0x2086,0x57ad,0x221a,0x1f68}).toString();
        static final String L177 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x25df,0x2e9e,0x431a,0xf295,0x9810,0xbd50}, new char[]{0x2598,0xf2e0,0x9862,0x436a,0x2ef2,0xbd35}).toString();
        static final String L178 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2bc4,0x40c9,0x3cca,0xc5b2,0xc50c}, new char[]{0xc54b,0x3cb8,0x40a8,0x2bb4,0xc5d7}).toString();
        static final String L179 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8eab,0x0816,0x762e,0xa48e}, new char[]{0xa4c0,0x0873,0x8ec4,0x7640}).toString();
        static final String L180 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x82c0,0x4f35,0xa087,0x08c9,0x18e7,0xe62b}, new char[]{0x8293,0xe65e,0xa0e9,0x1894,0x4f50,0x08bd}).toString();
        static final String L181 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb5d8,0xadb5,0xdedf,0x731e,0xbd3c}, new char[]{0xbd73,0x737d,0xdeba,0xb5b9,0xaddb}).toString();
        static final String L182 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x44bc,0xa133,0x2d08,0x2955,0x2679,0x4685}, new char[]{0x2d49,0x46f0,0x260b,0xa15c,0x44ce,0x2934}).plainString();
        static final String L183 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xeb23,0xab4a,0xc24d,0x7cc0,0xda04,0x5cec,0xa38d}, new char[]{0x5ca8,0x7cb2,0xa3ec,0xc22e,0xeb56,0xda68,0xab2b}).toString();
        static final String L184 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7be1,0x3e0e,0x2e5e,0xe1a0}, new char[]{0x2e10,0x3e61,0x7b93,0xe1c4}).toString();
        static final String L185 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5d4c,0x171c,0xc2ae,0x6d6e,0x89ff,0xd4cc,0xe592,0xf1fe,0x1491}, new char[]{0x89ad,0x5d23,0xd4bf,0xc2cb,0xf1de,0xe5c2,0x14f8,0x1772,0x6d0b}).toString();
        static final String L186 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x04c8,0xc4a5,0x4c68,0x4ed0,0xcc48,0x6319}, new char[]{0x4c2b,0xc4cd,0x04ad,0xcc3a,0x636b,0x4ea9}).toString();
        static final String L187 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x76d2,0x1f8c,0xbe7d,0xb635,0x4cf8}, new char[]{0x1fcf,0xb65a,0xbe0f,0x76b3,0x4c94}).toString();
        static final String L188 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1d8a,0x584b,0xa533,0x6362,0x9d22}, new char[]{0x1dcb,0x9d4f,0xa551,0x582e,0x6310}).toString();
        static final String L189 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xacd6,0xfcca,0x8646,0x5010}, new char[]{0x505d,0xfca3,0xacb8,0x8632}).toString();
        static final String L190 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x55c4,0xc144,0x6c2a,0xd07f,0xb999,0x80b3}, new char[]{0x5588,0x80d2,0xb9fe,0xc12b,0xd010,0x6c44}).toString();
        static final String L191 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xad3b,0x1f50,0x1ac1,0x6603,0x390a}, new char[]{0xad7d,0x6671,0x1aae,0x1f23,0x397e}).toString();
        static final String L192 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe0af,0x8295,0x2c0b,0xfc92,0x58ef,0xc572,0x1ddd,0xfdd6}, new char[]{0xfd85,0x588e,0xfce2,0x2c7b,0xc51a,0x1db4,0xe0dd,0x82f0}).toString();
        static final String L193 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x65bf,0x1fcb,0xf911,0xe66a,0x0d87}, new char[]{0x65fc,0x1faa,0x0de9,0xf975,0xe613}).toString();
        static final String L194 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x367b,0xe0fa,0xe86f,0x9f2e,0xe506}, new char[]{0x9f7d,0xe803,0x361a,0xe08e,0xe563}).toString();
        static final String L195 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5499,0x9501,0x2149,0x2cdc}, new char[]{0x2c91,0x956e,0x54f7,0x2126}).toString();
        static final String L196 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6b68,0xe359,0xb8cf,0x4725,0xf7aa,0x1f9b,0x1ee9,0xdf17,0xf926,0x9da4,0x987a,0x316f,0x49a8,0xbb11,0x34a7,0x5795,0x88e3,0xd481,0x0191,0xfe41,0x516e,0x2cb8,0xb812,0xa634,0x402c,0x891d,0x9075}, new char[]{0x6b3c,0x57fd,0xd4e8,0x2ccb,0x3487,0xb87f,0xb8a0,0xf942,0x4750,0xdf7b,0x310a,0x400c,0xfe29,0xa655,0xe32a,0xbb31,0x1e87,0x8972,0x1fbb,0x01e2,0x49cd,0x980e,0x8897,0x5107,0x901b,0x9dc3,0xf7d9}).toString();
        static final String L197 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa375,0xaa18,0x1db7,0xc9fd}, new char[]{0x1df5,0xa31c,0xaa76,0xc999}).plainString();
        static final String L198 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3e0b,0xefff,0xd1fd,0x24a0,0xccae,0x3cd3,0x08e2,0xca3b,0xf3d4,0x399a,0xdcd6,0x0bb9}, new char[]{0xccfc,0x0887,0x3e78,0x0bdc,0xef8b,0xf3f4,0x24ed,0xdcb9,0xca5f,0xd188,0x39f6,0x3cb6}).toString();
        static final String L199 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5f29,0xde4c,0xbd79,0xcc6e,0x7a33,0x5dc4}, new char[]{0xcc3c,0xde29,0x5db7,0x5f4c,0xbd0d,0x7a13}).toString();
        static final String L200 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb8f3,0xef7f,0x2ecc,0x2343,0x2eb9,0x49a3,0x022d}, new char[]{0x2ef2,0x0248,0x49da,0x2eae,0xb89a,0xef11,0x2327}).plainString();
        static final String L201 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf3fe,0x3a30,0x38d8,0x7564,0x5d6d,0xa8b5,0x7fde,0xcd1d}, new char[]{0x388b,0xa8d0,0x3a44,0xcd69,0x7fb7,0x750a,0x5d0a,0xf38d}).toString();
        static final String L202 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb80a,0x17c0,0x1463,0xa8dc,0x985d,0x2da8,0x3a12,0x92ef,0xf7c6,0x4a11,0x444c,0xa186,0x6b4b}, new char[]{0x2deb,0xf7a9,0x4422,0x929b,0x982f,0xa8b3,0x6b27,0x17e0,0x3a42,0xa1e7,0x140d,0xb86f,0x4a7d}).toString();
        static final String L203 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4b53,0xe6fb,0xba8d,0x54cc,0x6869,0xb95d,0xa7e1,0x0b5d,0x4124,0x1eea,0xaafc,0xc5d3,0x155a,0xcddf,0xe87b,0xe5c0,0x6095,0xc74d,0x91ca,0x2c8b,0x0036,0x2125,0xc38d,0x9c73,0x9132,0xa056,0xb83a,0x67b9,0xbe4a,0xc70b,0x1153,0x3ba3,0x0c79,0xe19c,0x5d27,0x3e76,0x1718,0xb015,0x2cd3,0x4946,0xa974,0xb995,0xe675,0x1403,0x1b16,0x5609,0x8bfd,0x5740,0xc7da,0xa70f}, new char[]{0xe829,0x8b92,0x9146,0x5d46,0x0042,0x113a,0xbe25,0xc765,0x2cf8,0x6795,0x1423,0x54a5,0x214b,0xcdab,0xc5b6,0x4b21,0xb85c,0x3e17,0xe698,0x4141,0x6845,0x1b36,0xe1f5,0xc723,0xa791,0x567c,0xb061,0x5760,0xb9f4,0xa038,0x153e,0xbaad,0x2cb6,0xa762,0x0b38,0xc7a8,0xe5a7,0x1e8f,0xa91a,0x3bc0,0x0c00,0xc3ad,0x9c10,0xe61a,0x60fb,0x176c,0xb92f,0x4929,0xaa90,0x91b9}).toString();
        static final String L204 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x269a,0x85b7,0x1f1e,0x039f,0x14df,0x9662,0xec03,0x0fd9,0x1f03}, new char[]{0x0f8b,0x03f0,0x26ee,0x85d6,0x1f77,0xec6a,0x14b0,0x1f70,0x9611}).toString();
        static final String L205 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3f58,0x9d5f,0xfafa,0x9734,0x6654,0xa178,0x7aa0,0x3c98,0xdb0c,0x2990,0x22a9,0x6463,0x831d,0x26d1,0x5b79,0xc3ae,0x25d2,0xe983,0x18cc,0x7deb,0xf803,0x149b,0x25b7,0x54ce,0xc937,0x06ee,0x9106,0x2f1d,0xf3fd,0x547c,0xc90c,0xa154,0x5f82,0x6876,0x9716,0x0894,0xad6e,0xf948,0x7f6b,0xce5e,0x88be,0x2c43}, new char[]{0x2581,0xad06,0x5fed,0x7d9c,0x25c4,0x29b0,0x22da,0xce3b,0xf38f,0x2f6b,0x3cfd,0xc945,0xf82e,0xa10b,0x7f02,0xa130,0x08f1,0x9736,0xdb6d,0x3f31,0xf925,0x54ee,0x8372,0x9168,0xc92c,0x0697,0xe9ec,0xfa8f,0x2c31,0x26f1,0x640e,0x14f4,0x6812,0x5419,0x6638,0x9714,0x88d7,0x5b17,0x7a80,0xc3e8,0x18f9,0x9d71}).toString();
        static final String L206 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x04cb,0xab65,0x037d,0x5432,0x4a4c,0x4e80,0x3e54,0x85ae,0xcab9,0x8312,0x4ab6}, new char[]{0x835f,0x3e31,0xcad7,0xab10,0x4a6c,0x85fd,0x545d,0x0308,0x4ad8,0x04af,0x4ef3}).toString();
        static final String L207 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1423,0x6eac,0x8334,0x31eb,0xcae2,0x4f4e,0x9469,0xaa19,0x40ac,0xb600,0x5936,0x959d,0xbc39,0xa6b8,0x3294,0x085e,0x3eaa,0x5daa,0x90b7,0xe7ad,0xed24,0x7d34,0xac84,0xf062,0x544b,0xc6c8,0x3c65,0xfff5,0x622a,0xc26f,0x1d4b,0xad70,0x5142,0xd7e2}, new char[]{0xffb6,0xf00e,0xca8b,0x90d4,0x6241,0x3198,0xb620,0x3c04,0x95f3,0x1d2f,0x3e8a,0xacf7,0x543c,0x5959,0x9406,0x5131,0xc6a0,0x32f1,0x082d,0xed04,0xe7c4,0x7d5a,0x8314,0x5dcf,0xc219,0x1446,0x6ede,0xa6c1,0xd7c2,0xbc54,0x40c9,0x4f20,0xad05,0xaa37}).toString();
        static final String L208 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2eec,0x850b,0x2e23,0xac28,0x5b6a,0x1882,0xcbc4,0xf85f,0x1360,0xcbc5,0xe97b,0xd608,0x3d61,0xaacf,0x1ee4,0x9ba1,0xb559}, new char[]{0xcb93,0x1305,0xf83d,0xe95b,0xaa8c,0xcbaa,0x2e82,0x1e90,0x2e51,0xb536,0xd664,0x3d41,0x9bf1,0xac49,0x8565,0x18e7,0x5b06}).toString();
        static final String L209 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd3d0,0x06a8,0x8a92,0x8c0f,0x9591,0xc4e9,0x1c01,0x0670,0x7254,0x7988,0x435e,0x60d9,0x85f2,0x7045,0x73e3,0x74d4,0x0345,0x66d9,0xc84c,0xc9ef,0x4dc2,0xf1d3,0x210d,0x00db,0x964b,0xb1ad,0x9dd2,0xc1a2,0x82bd,0x2cf7,0x8ddc,0xf2a7,0xca43,0xa55d,0x5072,0x685e,0xd090,0x9151,0x56ac,0x0eea,0xcb8a,0x7f01,0x4db2,0x0572,0x93d0,0xd406,0x1c57,0x6aaa,0x1458}, new char[]{0x749b,0xd476,0x6acf,0xd3be,0xf2d4,0xcbaa,0x95e5,0x859a,0xca2a,0x9122,0xc86c,0x0e89,0x93bc,0x79e1,0xd0f5,0x1c6f,0x66ad,0x9df5,0x7036,0x00fb,0x5011,0xf1bc,0x8c61,0x06dc,0x0337,0xc1cd,0x061c,0x687e,0x432e,0xc488,0xb1ca,0x8af7,0x212d,0x051b,0x82d3,0x4d92,0x4dbb,0x7f6e,0x142d,0x60ab,0x2cd7,0x7381,0x8dae,0x9624,0xa52a,0x7227,0x56c9,0x1c25,0xc9c1}).toString();
        static final String L210 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x941d,0x8a45,0x4d16,0xb3a6,0x33d1,0x13fa,0x5bdf,0x01c5,0xa11b,0x8b44,0x0fe3,0x4f3a}, new char[]{0x0faf,0x1393,0x4f4b,0xb3d3,0x9474,0x01a1,0x8a65,0x4d51,0x5bb3,0x8b25,0x33a2,0xa168}).toString();
        static final String L211 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7570,0x541f,0x1431,0xad50,0x8d94,0xa445,0x4cd8,0x798d,0x1b59,0x98f1,0xbe26,0xe7ac,0x03fd,0x11e3,0xc388,0x1877,0xff43,0x4d50,0x2656,0xd8b9,0xbab2,0x61fe,0x3543,0x324c,0xa97e,0x8429,0x9e26,0x68a3,0xab52,0xce8e,0xae37,0x1ce2,0xc6b6,0xcf81}, new char[]{0x1476,0x6192,0xad31,0xe7df,0x8de7,0x7550,0xff30,0x263e,0xcfe0,0x1c86,0x79e8,0xbe54,0x3563,0xceec,0xa91b,0x0395,0x181e,0xc6d8,0x844d,0x326c,0x118e,0xa420,0xae59,0x4d25,0xc3fb,0x9e06,0x68c2,0x4cb6,0x9895,0x1b79,0x5457,0xd8ec,0xab16,0xba9c}).toString();
        static final String L212 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xabc7,0x260e,0x25e8,0x1168,0xd461,0xbae3,0x8ee5,0x7b58,0xb74b}, new char[]{0x2647,0x2586,0xabb3,0x7b3d,0x8e97,0xd407,0x1109,0xb728,0xba86}).toString();
        static final String L213 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x87c5,0x384b,0xd79b,0x713c,0x9bf7,0xbe4b,0xfcf7,0xac8b,0xf506,0xdba2,0xc97a,0x5cc0,0x9a96,0x5887,0xff7b,0x5729,0x10e2,0x3da4,0x1b81,0x2b89,0xcba2,0xdc94,0x576a,0x6387,0x5ab9,0x2878,0xb9aa,0xcb38,0x7e1f,0xb936,0xfd6f}, new char[]{0xcb70,0x5705,0xdbd5,0xc95a,0x87b1,0x1be9,0x5ca5,0xfd4f,0xbe28,0xcbce,0xff12,0xb9cf,0x7e71,0x58f3,0x386b,0xf56a,0x9af9,0xfc98,0x9b9c,0xacf8,0xd7bb,0x3dc5,0x63e9,0x574d,0xb916,0x7153,0xdce4,0x1087,0x5ad7,0x2bfa,0x2856}).toString();
        static final String L214 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xcd26,0xd3ef,0x1eb8,0xc24b,0xa9e1,0x7e04,0xc69e,0x0cc6,0x7b3b,0x830f,0xc4b0,0x4c86,0x7e1e,0x4b51,0x504f,0x8c48}, new char[]{0x8c1c,0xa984,0x4b29,0x7e6a,0xcd06,0x500c,0x0ca9,0xc6f2,0xc4df,0x4cf4,0x7b1b,0xd3bc,0x8367,0xc222,0x1ede,0x7e70}).toString();
        static final String L215 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1912,0xc32b,0x3223,0x9dd6}, new char[]{0x9dbb,0xc34e,0x197c,0x3256}).plainString();
        static final String L216 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x058e,0x0584,0x8555,0x089d,0xcecd,0xa13c,0x8bc0,0x6279,0x8373,0xcf7c,0x374f,0xb0b4,0x50fb,0x42f9}, new char[]{0xce9d,0x620b,0x8530,0x05f7,0x373c,0x50db,0x05ef,0x8353,0xa157,0xb0d1,0x08e4,0x42d7,0x8bee,0xcf52}).toString();
        static final String L217 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5ee5,0xe08b,0xb65d,0xbe0b,0x3d38,0x0d59,0x353d,0x43c4,0xf977}, new char[]{0xf93a,0x5e80,0xe0e5,0xb628,0xbe2b,0x4386,0x3d51,0x0d37,0x3559}).toString();
        static final String L218 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe5d2,0x93e2,0x8657,0xd7c9,0x9aa9}, new char[]{0x93b2,0x8636,0xe5bc,0x9ac0,0xd7aa}).toString();
        static final String L219 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc223,0xa3d2,0x3a98,0x3d9d,0x8336,0xc0a1,0x95bb,0x9b03,0xd122,0xcda4,0x2ac9,0x0cec,0x5da9,0x7f93,0x6288,0xc122,0x5d63,0xa803,0x2c4d,0x6bc6,0xa828,0x6976,0x2f41,0x9c73,0xb640,0x2abb,0x8a03,0x64e7,0xb11c,0x3086,0xf9f4,0xfac1,0x3ed7}, new char[]{0x2f15,0xcdd1,0xc0d3,0xb62e,0x6bb5,0x8a23,0x9c1c,0xf992,0x0c8a,0x8316,0x5d06,0xfab7,0xc147,0x62fa,0x3de4,0x30a6,0x5dc4,0x3eb8,0x95df,0x2ace,0x3af4,0xd147,0x2c6d,0x2aa0,0x7ffd,0x6956,0xc242,0xb13c,0xa85b,0xa86d,0x9b62,0xa3a2,0x64c9}).toString();
        static final String L220 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3221,0xd377,0x220b,0x3ee4,0x81ca,0x6cc6,0x337d,0x50dc,0x5363,0x9945,0x17f7,0x0dff,0xcb86,0x13de,0xfef3,0xfa7c,0xeee2,0xe5c7,0x674e,0x6bbc,0x781e}, new char[]{0x17b2,0x6bca,0xd312,0x6cb4,0x3e9d,0xee96,0xcbee,0x530a,0x324f,0x81ad,0x13fe,0x50a8,0x786b,0x673c,0xfa12,0x0d9a,0x226f,0x335d,0x992a,0xfe95,0xe5a1}).toString();
        static final String L221 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2917,0xad0e,0xd8d2,0x5f03,0xd138}, new char[]{0xad7e,0xd8b3,0xd156,0x5f6a,0x2974}).plainString();
        static final String L222 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1560,0x73d3,0x1dee,0x5d51}, new char[]{0x5d1f,0x150f,0x73bd,0x1d8b}).plainString();
        static final String L223 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe268,0x1c30,0x54af,0xd707,0xb084,0xd4e3,0xe519,0xfa5d,0x1db7,0x2892,0xe8f1,0xa5b8,0x6ba3,0x6844,0x3bb0,0x927c}, new char[]{0x6be0,0x54c3,0xd76e,0x921f,0xe572,0xe248,0x1c44,0xe89e,0xd4c3,0xb0e7,0x3bdf,0xfa33,0xa5de,0x1dde,0x6836,0x28ff}).toString();
        static final String L224 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x46f6,0x3d60,0x129b,0x7e13,0x0ccb,0x9a63,0x8929,0x0509,0x019a,0x5e45,0x05fb,0xe8a7,0x8ac7,0x20ea,0x8929,0xbd84}, new char[]{0x12ce,0xe8c9,0x0ca7,0x4699,0x3d01,0xbde0,0x8ae7,0x058c,0x5e24,0x209e,0x056c,0x895b,0x7e3d,0x8945,0x9a0c,0x01f6}).toString();
        static final String L225 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3ddc,0x8785,0x56bc,0xeba5,0xfcd2,0x1d8f,0x8503,0xb956,0x54c2,0xa3f1,0xfdbc,0x6ba7,0x4413,0x5b3f,0x5227,0x817a,0x9285,0x71fd}, new char[]{0xfdf9,0x447d,0x56c8,0xb933,0x92f7,0x71dd,0xa390,0x5b1f,0x8575,0x1dee,0xfcbe,0x3db5,0x54a6,0x6b87,0x8114,0x87e4,0x524a,0xebc0}).toString();
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT2 {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd55f,0xfa28,0xf2a4}, new char[]{0xfa78,0xf2cb,0xd52b,0x0340}).toString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xbfde,0x9272,0x1e55}, new char[]{0x1e25,0x921d,0xbfaa,0x10f8}).toString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb6d0,0x505f,0xb3cf,0x3b27,0x4554,0xcff4}, new char[]{0x502f,0x453b,0x3b53,0xcf9d,0xb3a0,0xb6be}).toString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x63c0,0x4944,0xe6c4,0xef0a,0x1076,0xad92}, new char[]{0x1004,0xe6a1,0x4922,0xef63,0x63ac,0xadfe}).toString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xbf7d,0xfc0a,0x7c8a,0xe004,0x40f8,0x975a,0xc451,0x090a,0xf24b,0xb1f5,0x641e}, new char[]{0xfc67,0xb19a,0x972f,0xbf0e,0x409d,0x643e,0xc425,0xe073,0xf22e,0x096b,0x7ce1}).toString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe82a,0x0146,0xb299,0xd12b,0x66df,0x1e54,0xa87b,0xc067,0xd025,0xc4c6}, new char[]{0xb2f8,0x66b6,0x1e39,0xe80a,0xc4a7,0xa808,0xd158,0xc00e,0x0135,0xd051}).toString();
        static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xfa73,0xe0f5,0x432c,0x27a4}, new char[]{0x27cc,0xe090,0xfa12,0x4340}).toString();
        static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa1f7,0xe7a0,0x1679,0xb56b}, new char[]{0xe7d3,0xa198,0x160c,0xb51b}).toString();
        static final String L9 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x45b7,0xda6e,0x20ab}, new char[]{0xda1a,0x20ca,0x45d5,0xc122}).toString();
        static final String L10 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4e06,0xdf7a,0x223b}, new char[]{0x2259,0x4e72,0xdf14,0x5473}).toString();
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT3 {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1e4a,0xe87d}, new char[]{0x1e39,0xe80a,0xc4a7,0xa808}).toString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd162}, new char[]{0xd158,0xc00e,0x0135,0xd051}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb574,0xdf75,0x1660,0xa1b6,0x5417,0xe0f1,0xda76,0x4e1d,0x27bb,0x8d6e,0x00bb,0x2235,0x20ea,0xe7a1,0x45a0,0xfa66,0xc14c,0x4325}, new char[]{0x27cc,0xe090,0xfa12,0x4340,0xe7d3,0xa198,0x160c,0xb51b,0xda1a,0x20ca,0x45d5,0xc122,0x2259,0x4e72,0xdf14,0x5473,0x8d0b,0x00df}).toString();
    }
}
