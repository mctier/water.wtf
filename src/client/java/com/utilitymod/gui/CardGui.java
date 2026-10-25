package com.utilitymod.gui;

import com.utilitymod.core.Module;
import com.utilitymod.core.ModuleManager;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.settings.Setting;
import com.utilitymod.echo.features.settings.impl.BoolSetting;
import com.utilitymod.echo.features.settings.impl.ColorSetting;
import com.utilitymod.echo.features.settings.impl.FloatSetting;
import com.utilitymod.echo.features.settings.impl.IntSetting;
import com.utilitymod.echo.features.settings.impl.KeybindSetting;
import com.utilitymod.echo.features.settings.impl.ModeSetting;
import com.utilitymod.echo.features.settings.impl.MultiModeSetting;
import com.utilitymod.echo.features.settings.impl.RangeSetting;
import dev.koifih.client.render.Draw;
import dev.koifih.client.render.Scissor;
import dev.koifih.client.render.Switch;
import dev.koifih.client.render.Text;
import dev.koifih.client.ui.Theme;
import dev.koifih.client.ui.Transition;
import dev.koifih.client.ui.component.Bool;
import dev.koifih.client.ui.component.Control;
import dev.koifih.client.ui.component.Keybind;
import dev.koifih.client.ui.component.Slider;
import dev.koifih.client.ui.component.TextInput;
import dev.koifih.client.ui.component.popup.Dropdown;
import dev.koifih.client.ui.component.popup.Popup;
import dev.koifih.client.util.Colors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import org.lwjgl.glfw.GLFW;

import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;

/**
 * Third GUI style ("Cards"): a wide panel with a brand/category sidebar and a
 * two-column grid of module cards (name, description, live toggle) - the
 * sigma/internalsonly look. Left-click toggles, gear / right-click opens the
 * module's settings sheet. Same module model and sounds as the other GUIs.
 */
public final class CardGui extends Screen {
    private static final int BASE_W = 610;
    private static final int BASE_H = 400;
    private static final int SIDEBAR_UNITS = 155;
    private static final int CARD_HEIGHT_UNITS = 63;
    private static final int CARD_GAP_UNITS = 10;
    private static final int CARD_RADIUS_UNITS = 11;
    private static final int COLS = 2;
    private static final int PINK = 0xFFCE52B3, INK = 0xFFF7EAF5, MUTED = 0xFFD4B8D1;
    private static final int[] NAV_ICONS = {0xe145, 0xe566, 0xe8f4, 0xe55b, 0xe8b8, 0xe8cb};
    private static final String[] CLIENT_LINKS = {LT2.L1, LT2.L2, LT2.L3, LT2.L4};
    private static final int SEARCH_H_UNITS = 28;

    /** A sidebar category: either a real module Category, or a playstyle kit that gathers modules by name keywords. */
    record Cat(String label, com.utilitymod.gui.Category category, String icon, String... keys) {
        boolean kit() { return keys.length > 0; }
        boolean matches(Module m) {
            if (!kit()) return m.getCategory() == category;
            // each module lives in exactly one kit: the first kit (in sidebar order) whose keywords hit its name
            String n = plainName(m);
            for (Cat c : CATS) if (c.kit() && c.hits(n)) return c == this;
            // combat has no tab of its own: anything combat that no kit claims lands in Sword
            return label.equals(LT2.L5) && m.getCategory() == com.utilitymod.gui.Category.COMBAT;
        }
        private boolean hits(String n) {
            for (String k : keys) if (n.contains(k)) return true;
            return false;
        }
        private static String plainName(Module m) {
            String raw = m.getName();
            StringBuilder plain = new StringBuilder(raw.length());
            for (int i = 0; i < raw.length(); i++) plain.append(com.utilitymod.echo.utils.CloakUtil.plainChar(raw.charAt(i)));
            return plain.toString().toLowerCase(Locale.ROOT);
        }
        Iterable<? extends Module> source() {
            if (category == com.utilitymod.gui.Category.DONUT && !kit()) return com.utilitymod.anubis.AnubisBridge.features();
            if (!kit()) return ModuleManager.all();
            List<Module> all = new ArrayList<>(ModuleManager.all());
            for (Module m : com.utilitymod.anubis.AnubisBridge.features()) all.add(m);
            return all;
        }
    }
    /** Index of the first playstyle kit in {@link #CATS}. */
    static final int FIRST_KIT = 5;
    static final List<Cat> CATS = List.of(
            new Cat(LT.L6, com.utilitymod.gui.Category.MOVEMENT, LT2.L6),
            new Cat(LT.L7, com.utilitymod.gui.Category.VISUAL, LT2.L7),
            new Cat(LT.L8, com.utilitymod.gui.Category.FINDER, LT2.L8),
            new Cat(LT.L9, com.utilitymod.gui.Category.MISC, LT2.L9),
            new Cat(LT.L10, com.utilitymod.gui.Category.DONUT, LT2.L10),
            // playstyle kits (a module can sit in several)
            new Cat(LT2.L11, com.utilitymod.gui.Category.COMBAT, LT2.L12, LT2.L13, LT2.L14, LT2.L15, LT2.L16, LT2.L17),
            new Cat(LT2.L18, com.utilitymod.gui.Category.COMBAT, LT2.L12, LT2.L19, LT2.L20),
            new Cat(LT2.L21, com.utilitymod.gui.Category.COMBAT, LT2.L12, LT2.L22, LT2.L23, LT2.L24, LT2.L25),
            new Cat(LT2.L5, com.utilitymod.gui.Category.COMBAT, LT2.L12, LT2.L26, LT2.L27, LT2.L28, LT2.L29, LT2.L30,
                    LT2.L31, LT2.L32, LT2.L33, LT2.L34, LT2.L35, LT2.L36, LT2.L37),
            new Cat(LT2.L38, com.utilitymod.gui.Category.COMBAT, LT2.L10, LT2.L10, LT2.L39, LT2.L40, LT2.L41, LT2.L42,
                    LT2.L43, LT2.L44, LT2.L45, LT2.L46, LT2.L47, LT2.L48, LT2.L49, LT2.L50, LT2.L51));

    private final Transition reveal = new Transition(0f, 190, 90, Transition.Easing.EASE_OUT_SETTLE, Transition.Easing.EASE_IN_CUBIC);
    private static final MinecraftClient MCA = MinecraftClient.getInstance();
    /** Screen-level focus (Screen has none of Wing's widget routing). */
    private Control focus;
    private final Map<Module, Float> toggleShown = new HashMap<>();
    private final Map<Module, Float> cardHover = new HashMap<>();
    private TextInput search;
    private final StringBuilder query = new StringBuilder();
    private int panelX, panelY, panelW, panelH, sidebarW;
    private float scale;
    private int selectedCat = 0;
    private float scroll;
    private float scrollGoal;
    private boolean closing;
    private final CardGuiMotion motion = new CardGuiMotion();
    private long frameTime;
    private float frameEase = .2f;
    private boolean pointerHeld;
    private int page; // 0 modules, 1 configs, 2 themes, 3 keybinds, 4 options
    private TextInput profileInput;
    private final StringBuilder profileName = new StringBuilder();
    private List<String> profiles = List.of();
    private float pageScroll, pageMax;
    private String notice = "", pendingDelete, bindingModule;
    private boolean bindingMenu;
    private int colorEditor = -1;
    private final List<Hotspot> hotspots = new ArrayList<>();
    private float tiltX, tiltY;
    private org.joml.Matrix3x2f tiltInv = new org.joml.Matrix3x2f();
    private final float[] catRowY = new float[16], linkRowY = new float[8];
    private int navRowH = 1, navLeft, navRight;
    private Hotspot dragging;
    private record Hotspot(float x, float y, float w, float h, java.util.function.BiConsumer<Double, Double> action, boolean drag) {
        boolean contains(double mx, double my) { return mx >= x && mx < x+w && my >= y && my < y+h; }
    }


    // Settings sheet state.
    private record Row(Setting setting, Control control, boolean wide) {}
    private final List<Row> sheetRows = new ArrayList<>();
    private final List<Control> sheetControls = new ArrayList<>();
    private Feature sheetModule;
    private float sheetScroll, sheetScrollGoal;

    public CardGui() {
        super(net.minecraft.text.Text.literal(LT.L11));
    }

    // ---- panel geometry ---------------------------------------------------------

    private int pad() { return Math.round(10 * scale); }
    private int contentX() { return panelX + sidebarW; }
    private int contentY() { return panelY + pad(); }
    private int contentW() { return panelW - sidebarW; }
    private int contentH() { return panelH - 2 * pad(); }

    /** Set by OrbitGui: open this module's settings sheet as soon as the screen initialises. */
    static Module pendingSheet;

    @Override
    protected void init() {
        com.utilitymod.gui.GuiTextures.ensureLoaded();
        scale = fitScale();
        motion.reset();
        frameTime = System.nanoTime();
        panelW = Math.max(1, Math.round(BASE_W * scale));
        panelH = Math.max(1, Math.round(BASE_H * scale));
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        sidebarW = Math.round(SIDEBAR_UNITS * scale);
        reveal.set(1f);
        search = new TextInput(0, 0, 10, 10, scale, net.minecraft.text.Text.literal(LT.L12), 24,
                query::toString, s -> { query.setLength(0); query.append(s); });
        search.setFocused(false);
        search.setPlaceholder(LT2.L52);
        search.setSurface(0x14FFFFFF, INK, MUTED, 28, 36);
        profileInput = new TextInput(0, 0, 10, 10, scale, net.minecraft.text.Text.literal(LT2.L53), 48,
                profileName::toString, v -> { profileName.setLength(0); profileName.append(v); });
        profileInput.setPlaceholder(LT2.L53);
        profileInput.setSurface(0x209F6695, INK, MUTED, 10, 10);
        profiles = GuiConfig.profiles();
        closeSheet();
        if (pendingSheet != null) { Module m = pendingSheet; pendingSheet = null; openSheet(m); }
    }

    private float fitScale() {
        float desired = Math.min(width * .64f / BASE_W, height * .74f / BASE_H) * CardGuiStyle.size / 100f;
        return Math.max(.1f, Math.min(desired, Math.min((width-16f)/BASE_W, (height-16f)/BASE_H)));
    }

    private void layoutMotion(int mx, int my) {
        long now = System.nanoTime();
        float dt = frameTime == 0 ? 1f/60f : (now-frameTime)/1_000_000_000f;
        frameTime = now;
        frameEase = 1f-(float)Math.exp(-14f*Math.min(.05f, Math.max(0,dt)));
        int bx=(width-panelW)/2, by=(height-panelH)/2;
        motion.update(mx,my,bx,by,panelW,panelH,scale,dt,CardGuiStyle.motion && CardGuiStyle.gravity>0,
                pointerHeld || sheetModule!=null || (focus!=null && focus.capturesInput()));
        panelX=bx+Math.round(motion.x()*CardGuiStyle.gravity/100f);
        panelY=by+Math.round(motion.y()*CardGuiStyle.gravity/100f);
    }

    @Override
    public void removed() {
        closeSheet();
        com.utilitymod.gui.GuiConfig.save();
        super.removed();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // ---- module list ------------------------------------------------------------

    private List<Module> visible() {
        String q = query.toString().trim().toLowerCase(Locale.ROOT);
        List<Module> out = new ArrayList<>();
        Cat cur = CATS.get(selectedCat);
        for (Module m : cur.source()) {
            if (!cur.matches(m)) continue;
            if (!q.isEmpty() && !(m.getName().toLowerCase(Locale.ROOT).contains(q)
                    || (m.getDescription() != null && m.getDescription().toLowerCase(Locale.ROOT).contains(q)))) continue;
            out.add(m);
        }
        return out;
    }

    // ---- draw -------------------------------------------------------------------

    @Override
    public void render(DrawContext graphics, int mouseX, int mouseY, float delta) {
        Theme.update();
        layoutMotion(mouseX, mouseY);
        hotspots.clear();
        if (closing && reveal.value() <= 0f) {
            MinecraftClient.getInstance().setScreen(null);
            return;
        }
        float shown = reveal.value();
        graphics.createNewRootLayer();
        if (shown < 1f) graphics.fill(0, 0, width, height, Colors.withAlpha(0xFF08080C, 0.55f * (1f - shown)));

        var tiltStack = graphics.getMatrices();
        tiltStack.pushMatrix();
        tiltStack.mul(updateTilt(mouseX, mouseY));
        org.joml.Vector2f lm = tiltInv.transformPosition(new org.joml.Vector2f(mouseX, mouseY));
        mouseX = Math.round(lm.x); mouseY = Math.round(lm.y);
        int radius = Math.max(1, Math.round(CardGuiStyle.radius * scale));
        for (int i = 6; i >= 1; i--) {
            int s = i * 2;
            Draw.rect(graphics, panelX - s, panelY - s + 3, panelW + 2 * s, panelH + 2 * s,
                    radius + s, Colors.withAlpha(0xFF000000, 0.04f));
        }
        if (CardGuiStyle.glass && com.utilitymod.echo.render.GlassRenderer.available()) {
            com.utilitymod.echo.render.GlassRenderer.queue(new com.utilitymod.echo.render.GlassRenderer.Panel(
                    panelX, panelY, panelW, panelH, radius, 0.92f));
            glassFlush(graphics);
        }
        boolean glassReady = CardGuiStyle.glass && com.utilitymod.echo.render.GlassRenderer.available();
        if (!glassReady) com.utilitymod.echo.render.GlassRenderer.reportCardGate(CardGuiStyle.glass);
        if (glassReady) {
            // keep the tint off the rim so the shader's refracted glass edge stays visible
            int in = Math.round(16 * scale);
            com.utilitymod.gui.Rounded.outline(graphics, panelX, panelY, panelW, panelH, radius, 1f, 0x40FFFFFF);
        } else {
            Draw.rect(graphics, panelX, panelY, panelW, panelH, radius, Colors.withAlpha(CardGuiStyle.back, .82f));
            Draw.rect(graphics, panelX, panelY, panelW, Math.round(panelH * .55f), radius, Colors.withAlpha(CardGuiStyle.background, .16f));
            com.utilitymod.gui.Rounded.outline(graphics, panelX, panelY, panelW, panelH, radius, 1f, 0x18F4C4EF);
        }
        if (!glassReady) Draw.rect(graphics, panelX, panelY, sidebarW, panelH, radius, Colors.withAlpha(0xFF000000, .10f));
        graphics.fill(contentX()-1, panelY, contentX(), panelY+panelH, 0x22F0B6E5);
        graphics.fill(contentX(), panelY+Math.round(48*scale), panelX+panelW, panelY+Math.round(48*scale)+1, 0x22FFFFFF);

        drawSidebar(graphics, mouseX, mouseY);
        drawTopbar(graphics);
        drawSearch(graphics, mouseX, mouseY, delta);
        if (page == 0) {
            drawCategoryHeader(graphics);
            drawCards(graphics, mouseX, mouseY, delta);
        } else drawClientPage(graphics, mouseX, mouseY, delta);
        if (sheetModule != null) drawSheet(graphics, mouseX, mouseY, delta);
        tiltStack.popMatrix();
    }

    /** Reference-style magenta body: bright pink-violet on the left fading to deep plum on the right, inset so the liquid-glass rim shows around it. */
    private void pigment(DrawContext g, int x, int y, int w, int h, int r) {
        // saturated magenta body (reference), not a theme-washed lilac
        Draw.rect(g, x, y, w, h, r, Colors.withAlpha(0xFF8A0E78, .74f));
        int steps = 16;
        for (int i = 0; i < steps; i++) {               // deep plum toward the right and bottom-right
            float t = i / (float) steps;
            int sx = x + Math.round(w * (.30f + .70f * t));
            Draw.rect(g, sx, y, x + w - sx, h, r, Colors.withAlpha(0xFF14020F, .055f));
        }
        for (int i = 0; i < steps; i++) {               // hot pink bloom top-left
            float t = i / (float) steps;
            Draw.rect(g, x, y, Math.round(w * (.50f - .40f * t)), Math.round(h * (.70f - .50f * t)), r,
                    Colors.withAlpha(0xFFD62ABF, .03f));
        }
    }

    /** Flushes queued Cards glass panels with the dropdown GUI's liquid-glass shader, outside the tilt transform. */
    private void glassFlush(DrawContext g) {
        var ms = g.getMatrices();
        ms.pushMatrix();
        ms.identity();
        com.utilitymod.echo.render.GlassRenderer.flushCard(g, CardGuiStyle.background, CardGuiStyle.blur / 5f, !CardGuiStyle.motion);
        ms.popMatrix();
    }

    /** Hover tilt: shears the panel toward the pointer (a 2D stand-in for a 3D lean), spring-smoothed. */
    private org.joml.Matrix3x2f updateTilt(int mx, int my) {
        float cx = panelX + panelW / 2f, cy = panelY + panelH / 2f;
        boolean on = CardGuiStyle.tilt && CardGuiStyle.tiltStrength > 0 && !closing;
        float tx = 0f, ty = 0f;
        if (on && (pointerHeld || dragging != null)) { tx = tiltX; ty = tiltY; }   // hold still while dragging
        else if (on) {
            float halo = 14 * scale;
            if (mx >= panelX - halo && mx <= panelX + panelW + halo && my >= panelY - halo && my <= panelY + panelH + halo) {
                tx = Math.max(-1f, Math.min(1f, (mx - cx) / (panelW / 2f)));
                ty = Math.max(-1f, Math.min(1f, (my - cy) / (panelH / 2f)));
            }
        }
        float e = Math.min(1f, frameEase * .55f);
        tiltX += (tx - tiltX) * e;
        tiltY += (ty - tiltY) * e;
        float str = CardGuiStyle.tiltStrength / 100f;
        float k = .03f * str;
        float sc = 1f - .012f * str * (Math.abs(tiltX) + Math.abs(tiltY));
        org.joml.Matrix3x2f m = new org.joml.Matrix3x2f().translate(cx, cy)
                .mul(new org.joml.Matrix3x2f(1f, k * tiltX, k * tiltY, 1f, 0f, 0f))
                .scale(sc).translate(-cx, -cy);
        tiltInv = new org.joml.Matrix3x2f(m).invert();
        return m;
    }

    private Click local(Click e) {
        org.joml.Vector2f v = tiltInv.transformPosition(new org.joml.Vector2f((float) e.x(), (float) e.y()));
        return new Click(v.x, v.y, e.buttonInfo());
    }

    private void drawCategoryHeader(DrawContext g) {
        String label=CATS.get(selectedCat).label();
        List<Module> entries=visible();
        long enabled=entries.stream().filter(Module::isEnabled).count();
        glyph(g,label,CATS.get(selectedCat).icon(),contentX()+15*scale,panelY+64*scale,12*scale,CardGuiStyle.accent);
        Text.draw(g,label,contentX()+34*scale,panelY+71*scale,10*scale,INK);
        Text.draw(g,entries.size()+LT2.L54,contentX()+39*scale+Text.width(label,10*scale),panelY+71*scale,6.5f*scale,MUTED);
        String on=enabled+LT2.L55;
        float bx=panelX+panelW-47*scale;
        Draw.rect(g,bx,panelY+60*scale,35*scale,Math.round(17*scale),Math.round(3*scale),0x22FFFFFF);
        Text.draw(g,on,bx+6*scale,panelY+68.5f*scale,6.5f*scale,INK);
        g.fill(contentX()+Math.round(12*scale),panelY+Math.round(84*scale),panelX+panelW-Math.round(12*scale),panelY+Math.round(84*scale)+1,0x18FFFFFF);
    }

    private void setPage(int value) {
        page=value; dragging=null; pointerHeld=false; pageScroll=0; colorEditor=-1; pendingDelete=null; bindingModule=null; bindingMenu=false;
        closeSheet(); focus=null; search.setFocused(false); profileInput.setFocused(false);
        profiles=GuiConfig.profiles(); notice="";
        UiSounds.play(UiSounds.Sound.TAB);
    }
    private float pageX() { return contentX()+16*scale; }
    private float pageWidth() { return contentW()-32*scale; }
    private void hot(float x,float y,float w,float h,boolean drag,java.util.function.BiConsumer<Double,Double> action) {
        float top=panelY+56*scale, bottom=panelY+panelH-12*scale;
        if(y>=top && y+h<=bottom) hotspots.add(new Hotspot(x,y,w,h,action,drag));
    }
    private void button(DrawContext g,String text,float x,float y,float w,Runnable action,boolean danger) {
        Draw.rect(g,x,y,w,Math.max(1,Math.round(20*scale)),Math.round(3*scale),danger?0x804B2330:Colors.withAlpha(CardGuiStyle.accent,.64f));
        Text.drawCenteredX(g,text,x+w/2,y+10*scale,7*scale,INK);
        hot(x,y,w,20*scale,false,(mx,my)->action.run());
    }
    private void change(java.util.function.IntConsumer set,int value) { set.accept(value); GuiConfig.markDirty(); }
    private float slider(DrawContext g,String label,float y,int min,int max,int value,java.util.function.IntConsumer set) {
        float x=pageX()+14*scale,w=pageWidth()-28*scale;
        Text.draw(g,label,x,y+8*scale,8*scale,INK);
        String val=Integer.toString(value); float vx=x+w-Text.width(val,8*scale);
        Text.draw(g,val,vx,y+8*scale,8*scale,INK); Text.draw(g,val,vx+.3f*scale,y+8*scale,8*scale,INK);
        float ty=y+21*scale,t=(value-min)/(float)(max-min);
        int th=Math.max(2,Math.round(3*scale)),tr=Math.max(1,th/2);
        Draw.rect(g,x,ty,w,th,tr,0x2EFFFFFF);
        if(t>0)Draw.rect(g,x,ty,Math.max(th,w*t),th,tr,CardGuiStyle.accent);
        float k=8*scale,kx=Math.max(x,Math.min(x+w-k,x+w*t-k/2)),ky=ty+th/2f-k/2;
        Draw.rect(g,kx,ky+scale*.6f,k,Math.round(k),Math.round(2*scale),0x40000000);
        Draw.rect(g,kx,ky,k,Math.round(k),Math.round(2*scale),0xFFFFFFFF);
        hot(x-4*scale,ty-6*scale,w+8*scale,15*scale,true,(mx,my)->change(set,min+Math.round(Math.max(0,Math.min(1,(float)(mx-x)/w))*(max-min))));
        return y+33*scale;
    }
    private float toggle(DrawContext g,String label,float y,boolean value,Runnable change) {
        float x=pageX()+14*scale,w=pageWidth()-28*scale;
        Text.draw(g,label,x,y+10*scale,8*scale,INK);
        float tw=28*scale,th=16*scale,tx=x+w-tw,ty=y+2*scale;
        Draw.rect(g,tx,ty,tw,Math.round(th),Math.round(4*scale),value?CardGuiStyle.accent:0x2EFFFFFF);
        float k=10*scale;
        Draw.rect(g,tx+(value?tw-k-3*scale:3*scale),ty+3*scale,k,Math.round(k),Math.round(2.5f*scale),value?0xFFFFFFFF:0xFFDCD6E2);
        hot(x,y,w,22*scale,false,(mx,my)->{change.run();GuiConfig.markDirty();});
        return y+26*scale;
    }
    /** Centered section label between two hairlines (reference "Colours" divider). */
    private float section(DrawContext g,String label,float y) {
        float x=pageX()+14*scale,w=pageWidth()-28*scale,cx=x+w/2,lw=Text.width(label,7*scale);
        int line=Colors.withAlpha(0xFFFFFFFF,.12f),ly=Math.round(y+6*scale);
        g.fill(Math.round(x),ly,Math.round(cx-lw/2-8*scale),ly+1,line);
        g.fill(Math.round(cx+lw/2+8*scale),ly,Math.round(x+w),ly+1,line);
        Text.drawCenteredX(g,label,cx,y+6*scale,7*scale,MUTED);
        return y+18*scale;
    }
    private int color(int i) { return i==0?CardGuiStyle.accent:i==1?CardGuiStyle.background:CardGuiStyle.back; }
    private void color(int i,int c) {
        if(i==0)CardGuiStyle.accent=c;else if(i==1)CardGuiStyle.background=c;else CardGuiStyle.back=c;
        GuiConfig.markDirty();
    }
    private float colorRow(DrawContext g,String label,int index,float y) {
        float x=pageX()+14*scale,w=pageWidth()-28*scale;
        Text.draw(g,label,x,y+10*scale,8*scale,INK);
        float sw=32*scale,sh=16*scale,sx=x+w-sw-14*scale,sy=y+2*scale;
        Draw.rect(g,sx-scale,sy-scale,sw+2*scale,Math.round(sh+2*scale),Math.round(4*scale),0x55FFFFFF);
        Draw.rect(g,sx,sy,sw,Math.round(sh),Math.round(3*scale),color(index));
        Draw.rect(g,sx+2*scale,sy+1.5f*scale,sw-4*scale,Math.max(1,Math.round(scale)),0,0x33FFFFFF);
        float cx=x+w-5*scale,chy=y+10*scale,cs=3*scale;
        int cc=colorEditor==index?INK:MUTED;
        if(colorEditor==index){ chevLine(g,cx-cs,chy-cs/2,cx,chy+cs/2,cc); chevLine(g,cx,chy+cs/2,cx+cs,chy-cs/2,cc); }
        else { chevLine(g,cx-cs/2,chy-cs,cx+cs/2,chy,cc); chevLine(g,cx+cs/2,chy,cx-cs/2,chy+cs,cc); }
        hot(x,y,w,22*scale,false,(mx,my)->colorEditor=colorEditor==index?-1:index);
        y+=25*scale;
        if(colorEditor!=index)return y;
        float[] hsv=java.awt.Color.RGBtoHSB((color(index)>>16)&255,(color(index)>>8)&255,color(index)&255,null);
        float cy=y, ch=58*scale,hy=y+64*scale;
        int hue=0xFF000000|java.awt.Color.HSBtoRGB(hsv[0],1,1);
        // Horizontal saturation and vertical value gradients, in 32 bounded strips.
        for(int j=0;j<32;j++) {
            int x0=Math.round(x+w*j/32),x1=Math.round(x+w*(j+1)/32);
            g.fillGradient(x0,Math.round(cy),x1,Math.round(cy+ch),Colors.lerp(0xFFFFFFFF,hue,j/31f),0xFF000000);
        }
        Draw.rect(g,x+hsv[1]*w-2*scale,cy+(1-hsv[2])*ch-2*scale,4*scale,Math.max(1,Math.round(4*scale)),Math.round(scale),INK);
        Draw.hueBar(g,Math.round(x),Math.round(hy),Math.round(w),Math.max(1,Math.round(8*scale)),0);
        Draw.rect(g,x+hsv[0]*w-1*scale,hy-1*scale,2*scale,Math.round(10*scale),0,INK);
        hot(x,cy,w,ch,true,(mx,my)->color(index,java.awt.Color.HSBtoRGB(hsv[0],Math.max(0,Math.min(1,(float)(mx-x)/w)),1-Math.max(0,Math.min(1,(float)(my-cy)/ch)))));
        hot(x,hy,w,8*scale,true,(mx,my)->color(index,java.awt.Color.HSBtoRGB(Math.max(0,Math.min(1,(float)(mx-x)/w)),hsv[1],hsv[2])));
        return y+82*scale;
    }
    private void chevLine(DrawContext g,float x0,float y0,float x1,float y1,int col) {
        int n=(int)Math.max(4,Math.round(Math.hypot(x1-x0,y1-y0)*2));
        float d=Math.max(1,scale*.9f);
        for(int i=0;i<=n;i++){float t=i/(float)n;Draw.rect(g,x0+(x1-x0)*t-d/2,y0+(y1-y0)*t-d/2,d,Math.max(1,Math.round(d)),0,col);}
    }
    private void drawClientPage(DrawContext g,int mx,int my,float delta) {
        float x=pageX(),w=pageWidth(),top=panelY+60*scale,bottom=panelY+panelH-14*scale;
        if(page==1) {
            profileInput.visible=true; profileInput.setX(Math.round(x));profileInput.setY(Math.round(top));
            profileInput.setWidth(Math.round(w-78*scale));profileInput.setHeight(Math.round(25*scale));
            profileInput.draw(g,mx,my,delta);
            button(g,LT2.L56,x+w-70*scale,top+2*scale,70*scale,()->{
                notice=GuiConfig.saveProfile(profileName.toString())?LT2.L57:LT2.L58;
                profiles=GuiConfig.profiles();
            },false);
            int listTop=Math.round(top+36*scale),listBottom=Math.round(bottom-13*scale);
            pageMax=Math.max(0,profiles.size()*32*scale-(listBottom-listTop));pageScroll=Math.min(pageMax,pageScroll);
            Scissor.clip(g,Math.round(x),listTop,Math.round(w),listBottom-listTop,()->{
                for(int i=0;i<profiles.size();i++) {
                    String name=profiles.get(i);float y=listTop+i*32*scale-pageScroll;
                    if(y<listTop || y+27*scale>listBottom)continue;
                    Draw.rect(g,x,y,w,Math.round(27*scale),Math.round(3*scale),0x25FFFFFF);
                    Text.draw(g,Text.fit(name,w-130*scale,8*scale),x+13*scale,y+13*scale,8*scale,INK);
                    button(g,LT2.L59,x+w-90*scale,y+3*scale,50*scale,()->{boolean loaded=GuiConfig.loadProfile(name); notice=loaded?LT2.L60:LT2.L61; if(loaded)init();},false);
                    button(g,name.equals(pendingDelete)?LT2.L62:LT2.L63,x+w-32*scale,y+3*scale,28*scale,()->{
                        if(name.equals(pendingDelete)){ GuiConfig.deleteProfile(name); profiles=GuiConfig.profiles();pendingDelete=null; }
                        else {pendingDelete=name;notice=LT2.L64+name;}
                    },true);
                }
                if(profiles.isEmpty())Text.draw(g,LT2.L65,x+12*scale,listTop+15*scale,8*scale,MUTED);
            });
            Text.draw(g,Text.fit(notice.isEmpty()?LT2.L66:notice,w,6.5f*scale),x+3*scale,bottom-3*scale,6.5f*scale,MUTED);
            return;
        }
        int clipTop=Math.round(top),clipBottom=Math.round(bottom);
        Scissor.clip(g,Math.round(x),clipTop,Math.round(w),clipBottom-clipTop,()->{
            Draw.rect(g,x,top,w,Math.round(bottom-top),Math.round(6*scale),0x14FFFFFF);
            com.utilitymod.gui.Rounded.outline(g,Math.round(x),Math.round(top),Math.round(w),Math.round(bottom-top),6*scale,1f,0x1AFFFFFF);
            float y=top+13*scale-pageScroll;
            if(page!=2){Text.drawCenteredX(g,CLIENT_LINKS[page-1],x+w/2,y,7*scale,MUTED); y+=15*scale;}
            else y-=7*scale;
            if(page==2) {
                y=section(g,LT2.L67,y);
                y=colorRow(g,LT2.L68,0,y);y=colorRow(g,LT2.L69,1,y);y=colorRow(g,LT2.L70,2,y);
                y=section(g,LT2.L71,y+4*scale);
                y=slider(g,LT2.L72,y,0,30,CardGuiStyle.blur,v->CardGuiStyle.blur=v);
                y=slider(g,LT2.L73,y,4,60,CardGuiStyle.radius,v->CardGuiStyle.radius=v);
                y=slider(g,LT2.L74,y,2,40,CardGuiStyle.cardRadius,v->CardGuiStyle.cardRadius=v);
                y=toggle(g,LT2.L75,y,CardGuiStyle.glass,()->CardGuiStyle.glass=!CardGuiStyle.glass);
                y=section(g,LT2.L76,y+4*scale);
                y=toggle(g,LT2.L77,y,CardGuiStyle.motion,()->CardGuiStyle.motion=!CardGuiStyle.motion);
                y=slider(g,LT2.L78,y,0,100,CardGuiStyle.gravity,v->CardGuiStyle.gravity=v);
                y=toggle(g,LT2.L79,y,CardGuiStyle.tilt,()->CardGuiStyle.tilt=!CardGuiStyle.tilt);
                y=slider(g,LT2.L80,y,0,100,CardGuiStyle.tiltStrength,v->CardGuiStyle.tiltStrength=v);
            } else if(page==4) {
                Text.draw(g,LT2.L81,x+14*scale,y+10*scale,8*scale,INK);
                button(g,bindingMenu?LT2.L82:dev.koifih.client.input.Keybinds.panelKey().getLocalizedText().getString(),x+w-100*scale,y,86*scale,()->bindingMenu=true,false);y+=32*scale;
                y=toggle(g,LT2.L83,y,UiSounds.enabled,()->UiSounds.enabled=!UiSounds.enabled);
                y=slider(g,LT2.L84,y,70,120,CardGuiStyle.size,v->CardGuiStyle.size=v);
                y=toggle(g,LT2.L85,y,!CardGuiStyle.motion,()->CardGuiStyle.motion=!CardGuiStyle.motion);
                y=toggle(g,LT2.L86,y,CardGuiStyle.glass,()->CardGuiStyle.glass=!CardGuiStyle.glass);
                button(g,LT2.L87,x+14*scale,y,w-28*scale,GuiConfig::openProfileFolder,false); y+=32*scale;
                Text.draw(g,LT2.L88,x+14*scale,y,6.5f*scale,MUTED); y+=22*scale;
            } else {
                Text.draw(g,LT2.L89,x+14*scale,y,6.5f*scale,MUTED);y+=18*scale;
                List<Module> modules=new ArrayList<>(ModuleManager.all());modules.addAll(com.utilitymod.anubis.AnubisBridge.features());
                for(Module m:modules) {
                    if(query.length()>0 && !m.getName().toLowerCase(Locale.ROOT).contains(query.toString().toLowerCase(Locale.ROOT)))continue;
                    if(y>=top && y+23*scale<=bottom) {
                        Text.draw(g,Text.fit(m.getName(),w-120*scale,8*scale),x+14*scale,y+10*scale,8*scale,INK);
                        String key=m.getName().equals(bindingModule)?LT2.L82:ModuleBinds.name(ModuleBinds.get(m.getName()));
                        button(g,key.isEmpty()?LT2.L90:key,x+w-96*scale,y,82*scale,()->bindingModule=m.getName(),false);
                    }
                    y+=27*scale;
                }
            }
            pageMax=Math.max(0,y+pageScroll-bottom+10*scale);
        });
        pageScroll=Math.max(0,Math.min(pageScroll,pageMax));
        if(pageMax>0) {
            float track=bottom-top,thumb=Math.max(12*scale,track*track/(track+pageMax));
            Draw.rect(g,x+w-3*scale,top+(track-thumb)*pageScroll/pageMax,2*scale,Math.round(thumb),0,CardGuiStyle.accent);
        }
    }

    private void drawTopbar(DrawContext graphics) {
        String user = MCA.player != null ? MCA.player.getName().getString() : LT.L13;
        String date = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern(LT2.L91, Locale.ENGLISH));
        int av = Math.round(30 * scale);
        int ax = panelX + panelW - av - Math.round(10 * scale), ay = panelY + Math.round(6 * scale);
        float right = ax - 10 * scale;
        Text.draw(graphics, user, right - Text.width(user, 8.5f * scale), panelY + 15 * scale, 8.5f * scale, INK);
        Text.draw(graphics, user, right - Text.width(user, 8.5f * scale) + .25f * scale, panelY + 15 * scale, 8.5f * scale, INK);
        Text.draw(graphics, date, right - Text.width(date, 6f * scale), panelY + 28 * scale, 6f * scale, MUTED);
        Draw.rect(graphics, ax, ay, av, av, Math.round(7 * scale), Colors.withAlpha(0xFFFFFFFF, .14f));
        if (MCA.player != null) {
            int face = av - Math.round(4 * scale);
            net.minecraft.client.gui.PlayerSkinDrawer.draw(graphics, MCA.player.getSkin(), ax + Math.round(2 * scale), ay + Math.round(2 * scale), face);
        } else Draw.icon(graphics, dev.koifih.client.render.AdinIcon.USER, ax + Math.round(6 * scale), ay + Math.round(6 * scale), Math.round(18 * scale), INK);
        float wUser = Math.max(Text.width(user, 8.5f * scale), Text.width(date, 6f * scale));
        int bs = Math.round(28 * scale);
        int bx = Math.round(right - wUser - 14 * scale - bs), by = panelY + Math.round(7 * scale);
        Draw.rect(graphics, bx, by, bs, bs, Math.round(7 * scale), Colors.withAlpha(0xFFFFFFFF, .07f));
        CardGuiIcons.draw(graphics, LT2.L92, bx + Math.round(7 * scale), by + Math.round(7 * scale), Math.round(14 * scale), INK);
    }

    private void drawSidebar(DrawContext graphics, int mouseX, int mouseY) {
        float size = scale;
        Text.draw(graphics, List.of(new Text.Span(LT.L15, INK), new Text.Span(LT.L16, INK)),
                panelX + 16 * size, panelY + 24 * size, 10 * size);
        Text.draw(graphics, LT.L17, panelX + 16 * size, panelY + 36 * size, 6.5f * size, MUTED);

        int margin = Math.round(8 * size);
        // 12 categories + 4 links + 2 headers + kits label: shrink rows so everything fits, hide the note if still tight
        float avail = panelH - 58 * size - 2 * 31 * size - 10 * size - 9 * size - 18 * size;
        int rows = CATS.size() + CLIENT_LINKS.length;
        boolean showNote = avail - 50 * size >= rows * 17 * size;
        if (showNote) avail -= 50 * size;
        int rowH = Math.max(Math.round(12 * size), Math.min(Math.round(19 * size), Math.round(avail / rows - 2 * size)));
        int gap = Math.max(1, Math.round(Math.min(3 * size, (avail - rows * rowH) / rows)));
        int navX = panelX + margin;
        int navW = sidebarW - 2 * margin;
        int lineX = navX + Math.round(10 * size);
        int subX = navX + Math.round(16 * size);
        int subW = navX + navW - subX;
        navRowH = rowH; navLeft = navX; navRight = navX + navW;
        int y = panelY + Math.round(58 * size);

        y = sectionHeader(graphics, LT2.L93, LT.L18, navX, navW, rowH, y);
        int listTop = y;
        for (int c = 0; c < CATS.size(); c++) {
            Cat cat = CATS.get(c);
            catRowY[c] = y;
            boolean sel = page == 0 && c == selectedCat;
            boolean hov = mouseX >= navX && mouseX < navX + navW && mouseY >= y && mouseY < y + rowH;
            int count = 0; boolean any = false;
            for (Module m : cat.source())
                if (cat.matches(m)) { count++; any |= m.isEnabled(); }
            if (c == FIRST_KIT) {   // kits sub-header
                Text.draw(graphics, LT2.L94, subX + 4 * size, y + 4 * size, 5.5f * size, Colors.withAlpha(MUTED, .7f));
                y += Math.round(9 * size);
                catRowY[c] = y;
                hov = mouseX >= navX && mouseX < navX + navW && mouseY >= y && mouseY < y + rowH;
            }
            navRow(graphics, cat.icon(), cat.label, subX, subW, rowH, y, sel, hov);
            String cs = Integer.toString(count);
            float ty = y + rowH / 2f;
            float cx = subX + subW - 7 * size - Text.width(cs, 6.5f * size);
            Text.draw(graphics, cs, cx, ty, 6.5f * size, Colors.lerp(MUTED, INK, .4f));
            if (any) Draw.rect(graphics, cx - 8 * size, ty - 2 * size, 4 * size, Math.round(4 * size), Math.round(2 * size), CardGuiStyle.accent);
            y += rowH + gap;
        }
        graphics.fill(lineX, listTop, lineX + 1, y - gap, Colors.withAlpha(0xFFFFFFFF, .12f));

        y = sectionHeader(graphics, LT2.L95, LT.L19, navX, navW, rowH, y + Math.round(10 * size));
        listTop = y;
        for (int i = 0; i < CLIENT_LINKS.length; i++) {
            linkRowY[i] = y;
            boolean hov = mouseX >= navX && mouseX < navX + navW && mouseY >= y && mouseY < y + rowH;
            navRow(graphics, new String[]{LT2.L96,LT2.L97,LT2.L98,LT2.L9}[i], CLIENT_LINKS[i], subX, subW, rowH, y, page == i + 1, hov);
            y += rowH + gap;
        }
        graphics.fill(lineX, listTop, lineX + 1, y - Math.round(3 * size), Colors.withAlpha(0xFFFFFFFF, .12f));

        // footer warning (matches the source screenshot's warning block)
        if (!showNote) return;
        float footY = panelY + panelH - 44 * size;
        CardGuiIcons.draw(graphics, LT2.L99, (int) (panelX + 16 * size), (int) footY, (int) (12 * size), 0xFFFFC34B);
        Text.draw(graphics, LT2.L100, panelX + 34 * size, footY + 6 * size, 7.5f * size, 0xFFFFC34B);
        List<String> lines = Text.wrap(LT2.L101,
                sidebarW - 30 * size, 6f * size, 3);
        float ly = footY + 16 * size;
        for (String line : lines) {
            Text.draw(graphics, line, panelX + 16 * size, ly, 6f * size, MUTED);
            ly += 8 * size;
        }
    }

    private int sectionHeader(DrawContext g, String icon, String label, int x, int w, int rowH, int y) {
        int h = rowH + Math.round(3 * scale);
        Draw.rect(g, x, y, w, h, Math.round(7 * scale), Colors.withAlpha(0xFFFFFFFF, .07f));
        CardGuiIcons.draw(g, icon, x + Math.round(6 * scale), y + Math.round(4.5f * scale), Math.round(12 * scale), INK);
        Text.draw(g, label, x + 25 * scale, y + h / 2f, 8.5f * scale, INK);
        Text.draw(g, label, x + 25.25f * scale, y + h / 2f, 8.5f * scale, INK);
        return y + rowH + Math.round(9 * scale);
    }

    static String prestigeFor(String label) {
        return switch (label) {
            case "Combat" -> LT2.L102; case "Movement" -> LT2.L103; case "Visuals" -> LT2.L104;
            case "Misc" -> LT2.L105; case "Configs" -> LT2.L106; case "Themes" -> LT2.L107;
            case "Keybinds", "Macros" -> LT2.L98;
            case "Mace" -> LT2.L108; case "Spear" -> LT2.L109; case "Crystal" -> LT2.L110; case "Sword" -> LT2.L102;
            default -> null;
        };
    }

    private void glyph(DrawContext g, String label, String vector, float x, float y, float size, int color) {
        if (!PrestigeIcons.draw(g, prestigeFor(label), x, y, size, color)) CardGuiIcons.draw(g, vector, x, y, size, color);
    }

    private void navRow(DrawContext g, String icon, String label, int x, int w, int rowH, int y, boolean sel, boolean hov) {
        if (sel) {
            Draw.rect(g, x, y, w, rowH, Math.round(5 * scale), Colors.withAlpha(0xFFFFFFFF, .10f));
            Draw.rect(g, panelX + Math.round(3 * scale), y + Math.round(4 * scale), Math.max(1, Math.round(2.5f * scale)),
                    rowH - Math.round(8 * scale), Math.round(scale), CardGuiStyle.accent);
        } else if (hov) Draw.rect(g, x, y, w, rowH, Math.round(5 * scale), Colors.withAlpha(0xFFFFFFFF, .05f));
        glyph(g, label, icon, x + Math.round(7 * scale), y + Math.round(4.5f * scale), Math.round(10 * scale), sel ? CardGuiStyle.accent : INK);
        Text.draw(g, label, x + 25 * scale, y + rowH / 2f, 8f * scale, sel || hov ? INK : Colors.lerp(MUTED, INK, .5f));
    }

    private void drawSearch(DrawContext graphics, int mouseX, int mouseY, float delta) {
        int h = Math.round(SEARCH_H_UNITS * scale);
        search.setWidth(Math.max(1, contentW()-Math.round(162*scale)));
        search.setHeight(h);
        search.setX(Math.round(contentX() + 12 * scale));
        search.setY(Math.round(panelY + 6 * scale));
        search.visible = page != 1;
        if (search.visible) {
            search.draw(graphics, mouseX, mouseY, delta);
            CardGuiIcons.draw(graphics,LT2.L111,search.getX()+10*scale,search.getY()+8*scale,11*scale,MUTED);
            float kx=search.getX()+search.getWidth()-32*scale;
            Draw.rect(graphics,kx,search.getY()+7*scale,25*scale,Math.round(14*scale),Math.round(2*scale),0x18FFFFFF);
            Text.drawCenteredX(graphics,LT2.L112,kx+12.5f*scale,search.getY()+14*scale,5.5f*scale,MUTED);
        }
    }

    private int cardsTop() {
        return panelY + Math.round(92 * scale);
    }

    private void drawCards(DrawContext graphics, int mouseX, int mouseY, float delta) {
        int top = panelY + Math.round(92 * scale);
        int bottom = contentY() + contentH() - Math.round(14 * scale);
        int gap = Math.round(CARD_GAP_UNITS * scale);
        int cardH = Math.round(CARD_HEIGHT_UNITS * scale);
        int gridW = contentW() - Math.round(24 * scale);
        int cw = Math.max(1, (gridW - (COLS - 1) * gap) / COLS);
        List<Module> mods = visible();
        float max = Math.max(0f, ((mods.size() + COLS - 1) / COLS) * (float) (cardH + gap) - gap - (bottom - top));
        scrollGoal = Math.max(0f, Math.min(max, scrollGoal));
        scroll += (scrollGoal - scroll) * frameEase;
        if (Math.abs(scrollGoal - scroll) < 0.5f) scroll = scrollGoal;

        Scissor.clip(graphics, contentX(), top, contentW(), Math.max(0, bottom - top), () -> {
            for (int i = 0; i < mods.size(); i++) {
                int col = i % COLS, row = i / COLS;
                int cx = contentX() + Math.round(12 * scale) + col * (cw + gap);
                int cy = top - Math.round(scroll) + row * (cardH + gap);
                if (cy + cardH < top || cy > bottom) continue;
                drawCard(graphics, mods.get(i), cx, cy, cw, cardH, mouseX, mouseY);
            }
        });

        if (max > 1f) {
            int ch = bottom - top;
            int bw = Math.max(2, Math.round(3.5f * scale));
            int bx = panelX + panelW - Math.round(7 * scale);
            int barH = Math.max(Math.round(24 * scale), Math.round(ch * ch / (float) (ch + max)));
            int barY = top + Math.round(scroll / max * (ch - barH));
            Draw.rect(graphics, bx, top, bw, ch, bw / 2, Colors.withAlpha(0xFFFFFFFF, .05f));
            Draw.rect(graphics, bx - Math.round(scale), barY - Math.round(scale), bw + Math.round(2 * scale), barH + Math.round(2 * scale),
                    bw, Colors.withAlpha(CardGuiStyle.accent, .22f));
            Draw.rect(graphics, bx, barY, bw, barH, bw / 2, CardGuiStyle.accent);
            Draw.rect(graphics, bx + Math.max(1, Math.round(.7f * scale)), barY + Math.round(3 * scale), Math.max(1, Math.round(.9f * scale)),
                    Math.max(1, barH - Math.round(6 * scale)), 0, Colors.withAlpha(0xFFFFFFFF, .45f));
        }
    }

    private void drawCard(DrawContext g, Module m, int x, int y, int w, int h, int mouseX, int mouseY) {
        boolean hov = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        float ht = approach(cardHover.getOrDefault(m, 0f), hov ? 1f : 0f);
        cardHover.put(m, ht);
        boolean on = m.isEnabled();
        float ts = approach(toggleShown.getOrDefault(m, on ? 1f : 0f), on ? 1f : 0f);
        toggleShown.put(m, ts);

        int r = Math.round(CardGuiStyle.cardRadius * scale);
        // filled frosted card, no outline; brighter pink when enabled
        // reference: off cards are a darker translucent plum, on cards lift toward pink
        int fill = Colors.withAlpha(0xFF1A0418, 0.20f - 0.06f * ht);
        fill = Colors.lerp(fill, Colors.withAlpha(0xFFC23AAE, 0.22f + 0.05f * ht), ts);
        Draw.rect(g, x, y, w, h, r, fill);
        Draw.rect(g, x + r, y, Math.max(1, w - 2 * r), 1, 0, Colors.withAlpha(0xFFFFFFFF, 0.07f + 0.05f * ht));

        String name = Text.fit(m.getName(), w - 90 * scale, 9.5f * scale);
        Text.draw(g, name, x + 14 * scale, y + 21 * scale, 9.5f * scale, INK);
        Text.draw(g, name, x + 14.25f * scale, y + 21 * scale, 9.5f * scale, INK);
        String desc = m.getDescription() == null ? "" : m.getDescription();
        if (!desc.isEmpty()) {
            List<String> lines = Text.wrap(desc.replace('\uE120', ' '), w - 100 * scale, 6.6f * scale, 2);
            float ly = y + 37 * scale;
            for (int i = 0; i < Math.min(2, lines.size()); i++) {
                Text.draw(g, Text.fit(lines.get(i), w - 100 * scale, 6.6f * scale), x + 14 * scale, ly, 6.6f * scale, MUTED);
                ly += 10 * scale;
            }
        }

        int toggleW = Math.round(32 * scale), th = Math.max(1, Math.round(18 * scale));
        int tx = x + w - toggleW - Math.round(12 * scale);
        int ty = y + Math.round(12 * scale);
        if (m instanceof Feature f && !f.getSettings().isEmpty())
            CardGuiIcons.draw(g, LT2.L9, tx - Math.round(21 * scale), ty + Math.round(3.5f * scale),
                    Math.round(11 * scale), hov ? INK : Colors.lerp(MUTED, INK, .35f));
        // rounded-rect track, square thumb: light when off, dark when on
        Draw.rect(g, tx, ty, toggleW, th, Math.round(4 * scale), Colors.lerp(0x55200A1C, CardGuiStyle.accent, ts));
        float inset = 3 * scale, kn = th - 2 * inset;
        Draw.rect(g, tx + inset + (toggleW - kn - 2 * inset) * ts, ty + inset, kn, Math.round(kn), Math.round(2 * scale),
                Colors.lerp(0xFFDCD6E2, 0xFF1B0A17, ts));
    }

    private float approach(float current, float target) {
        float next = current + (target - current) * frameEase;
        return Math.abs(target - next) < 0.002f ? target : next;
    }

    // ---- settings sheet ---------------------------------------------------------

    private void openSheet(Module m) {
        if(search!=null) { search.setFocused(false); search.dismiss(); }
        focus=null;
        closeSheet();
        if (!(m instanceof Feature f) || f.getSettings().isEmpty()) return;
        sheetModule = f;
        sheetScroll = sheetScrollGoal = 0f;
        {   // the module's own toggle key, like the reference's Keybind > Bind row
            String mod = m.getName();
            Keybind bind = new Keybind(0, 0, Math.round(56 * scale), Math.max(1, Math.round(16 * scale)), scale,
                    net.minecraft.text.Text.literal(LT2.L113),
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
            sheetControls.add(bind);
            sheetRows.add(new Row(new KeybindSetting(LT2.L113, -1), bind, true));
        }
        for (Setting st : f.getSettings()) {
            Control c = create(st);
            if (c == null) continue;
            if (c instanceof Bool b) b.cardStyle = true;
            if (c instanceof Slider sl) sl.cardStyle = true;
            if (c instanceof dev.koifih.client.ui.component.popup.RowPopup rp) rp.cardStyle = true;
            sheetControls.add(c);
            sheetRows.add(new Row(st, c, !(c instanceof Bool)));
        }
    }

    private void closeSheet() {
        focus = null;
        for (Control c : sheetControls) {
            c.dismiss();
            c.visible = false;
        }
        sheetControls.clear();
        sheetRows.clear();
        sheetModule = null;
    }

    private Control create(Setting st) { return create(st, scale, panelY + panelH - pad()); }

    /** Builds the control for a setting at {@code scale}; dropdown lists open no lower than {@code bottomLimit}. */
    static Control create(Setting st, float scale, int bottomLimit) {
        var label = net.minecraft.text.Text.literal(st.getName());
        int fieldH = Math.max(1, Math.round(16 * scale));
        if (st instanceof BoolSetting b) {
            return new Bool(0, 0, Math.round(24 * scale), Math.max(1, Math.round(12 * scale)), scale, label, b::getValue, b::setValue);
        }
        if (st instanceof FloatSetting f) {
            float inc = f.getIncrement() > 0 ? f.getIncrement() : 0.01f;
            Slider sl = new Slider(0, 0, Math.round(120 * scale), fieldH, scale, label,
                    Math.round(f.getMinValue() / inc), Math.round(f.getMaxValue() / inc),
                    () -> Math.round(f.getValue() / inc), v -> f.setValue(v * inc));
            sl.setFormat(v -> fmt(v * inc, inc));
            return sl;
        }
        if (st instanceof IntSetting i) {
            return new Slider(0, 0, Math.round(120 * scale), fieldH, scale, label, i.getMinValue(), i.getMaxValue(),
                    i::getValue, i::setValue);
        }
        if (st instanceof RangeSetting r) {
            float inc = r.getIncrement() > 0 ? r.getIncrement() : 0.01f;
            Slider sl = Slider.range(0, 0, Math.round(120 * scale), fieldH, scale, label,
                    Math.round(r.getLowerBound() / inc), () -> Math.round(r.getUpperBound() / inc),
                    () -> Math.round(r.getMinValue() / inc), v -> r.setMinValue(v * inc),
                    () -> Math.round(r.getMaxValue() / inc), v -> r.setMaxValue(v * inc));
            sl.setFormat(v -> fmt(v * inc, inc));
            return sl;
        }
        if (st instanceof ModeSetting mo) {
            List<CharSequence> modes = mo.getModes();
            String[] names = modes.stream().map(CharSequence::toString).toArray(String[]::new);
            Dropdown d = Dropdown.single(0, 0, Math.round(120 * scale), fieldH, scale, label, names,
                    () -> java.util.Arrays.asList(names).indexOf(mo.getValue().toString()),
                    i -> mo.setValue(modes.get(i)));
            d.setBottomLimit(bottomLimit);
            return d;
        }
        if (st instanceof MultiModeSetting mm) {
            List<CharSequence> opts = List.copyOf(mm.getOptionsSequenceView().keySet());
            Dropdown d = Dropdown.multi(0, 0, Math.round(120 * scale), fieldH, scale, label,
                    opts.stream().map(CharSequence::toString).toArray(String[]::new),
                    () -> new LiveSelection(mm, opts));
            d.setBottomLimit(bottomLimit);
            return d;
        }
        if (st instanceof ColorSetting c) {
            String[] names = {LT.L22, LT.L23, LT.L24, LT.L25, LT.L26, LT.L27, LT.L28, LT.L29, LT.L30};
            int[] rgb = {0xE53935, 0xFB8C00, 0xFDD835, 0x43A047, 0x00ACC1, 0x1E88E5, 0x8E24AA, 0xEC407A, 0xFFFFFF};
            Dropdown d = Dropdown.single(0, 0, Math.round(120 * scale), fieldH, scale, label, names,
                    () -> nearestColor(c.getARGB(), rgb),
                    i -> c.setFromARGB((c.getARGB() & 0xFF000000) | rgb[i]));
            d.setBottomLimit(bottomLimit);
            return d;
        }
        if (st instanceof KeybindSetting k) {
            Keybind kb = new Keybind(0, 0, Math.round(56 * scale), fieldH, scale, label,
                    () -> {
                        int code = k.getKey();
                        if (code == -1 || code == 0) return net.minecraft.client.util.InputUtil.UNKNOWN_KEY;
                        if ((code & 0x80000000) != 0) return net.minecraft.client.util.InputUtil.Type.MOUSE.createFromCode(code & 0xFF);
                        return net.minecraft.client.util.InputUtil.Type.KEYSYM.createFromCode(code);
                    },
                    key -> {
                        if (key == null || key.equals(net.minecraft.client.util.InputUtil.UNKNOWN_KEY)) k.setKey(-1);
                        else if (key.getCategory() == net.minecraft.client.util.InputUtil.Type.MOUSE) k.setKey(0x80000000 | key.getCode());
                        else k.setKey(key.getCode());
                    });
            kb.setClearable(true);
            return kb;
        }
        return null;
    }

    static String fmt(float v, float inc) {
        if (inc >= 1f) return Integer.toString(Math.round(v));
        int decimals = inc >= 0.1f ? 1 : 2;
        return String.format(Locale.ROOT, LT.L31 + decimals + LT.L32, v);
    }

    private static int nearestColor(int argb, int[] rgb) {
        int best = 0;
        long bestD = Long.MAX_VALUE;
        int a = argb & 0xFFFFFF;
        for (int i = 0; i < rgb.length; i++) {
            int b = rgb[i];
            long dr = ((a >> 16) & 255) - ((b >> 16) & 255), dg = ((a >> 8) & 255) - ((b >> 8) & 255), db = (a & 255) - (b & 255);
            long d = dr * dr + dg * dg + db * db;
            if (d < bestD) { bestD = d; best = i; }
        }
        return best;
    }

    /** Write-through Set view of a MultiModeSetting for Dropdown.multi (same as the panel GUI). */
    private static final class LiveSelection extends AbstractSet<Integer> {
        private final MultiModeSetting setting;
        private final List<CharSequence> options;

        LiveSelection(MultiModeSetting setting, List<CharSequence> options) {
            this.setting = setting;
            this.options = options;
        }

        @Override
        public Iterator<Integer> iterator() {
            List<Integer> on = new ArrayList<>();
            for (int i = 0; i < options.size(); i++) if (setting.isEnabled(options.get(i))) on.add(i);
            return on.iterator();
        }

        @Override public int size() { int n = 0; for (CharSequence o : options) if (setting.isEnabled(o)) n++; return n; }
        @Override public boolean contains(Object o) { return o instanceof Integer i && i >= 0 && i < options.size() && setting.isEnabled(options.get(i)); }
        @Override public boolean add(Integer i) { boolean was = contains(i); setting.setOption(options.get(i), true); return !was; }
        @Override public boolean remove(Object o) { boolean was = contains(o); if (was) setting.setOption(options.get((Integer) o), false); return was; }
    }

    private static final int SHEET_ROW_H_UNITS = 22;

    /** Sheet rectangle: a tall floating glass card over the module grid, as in the reference. */
    private int[] sheetRect() {
        int sw = Math.min(contentW() - 2 * pad(), Math.round(300 * scale));
        int sy = panelY + Math.round(54 * scale);
        int sh = panelY + panelH - Math.round(10 * scale) - sy;
        int sx = contentX() + (contentW() - sw) / 2 + Math.round(28 * scale);
        sx = Math.min(sx, panelX + panelW - pad() - sw);
        return new int[]{sx, sy, sw, sh};
    }
    private int sheetHeaderH() { return Math.round(32 * scale); }
    private int rowHeight(Row r) {
        if (r.control() instanceof Slider) return Math.round(38 * scale);
        return Math.round((r.control() instanceof Bool ? 26 : 28) * scale);
    }
    private int sectionH() { return Math.round(22 * scale); }

    private void sectionLine(DrawContext g, String label, int x, int w, int y) {
        float cy = y + sectionH() / 2f;
        float tw = Text.width(label, 6.8f * scale);
        float mid = x + w / 2f;
        int line = Colors.withAlpha(0xFFFFFFFF, .14f);
        g.fill(x, Math.round(cy), Math.round(mid - tw / 2 - 10 * scale), Math.round(cy) + 1, line);
        g.fill(Math.round(mid + tw / 2 + 10 * scale), Math.round(cy), x + w, Math.round(cy) + 1, line);
        Text.drawCenteredX(g, label, mid, cy, 6.8f * scale, MUTED);
    }

    private void drawSheet(DrawContext g, int mouseX, int mouseY, float delta) {
        int[] rc = sheetRect();
        int sx = rc[0], sy = rc[1], sheetW = rc[2], sheetH = rc[3];
        List<Popup> openPopups = new ArrayList<>();
        int padX = Math.round(16 * scale);
        int headerH = sheetHeaderH();
        int top = sy + headerH + Math.round(4 * scale);
        int bottom = sy + sheetH - Math.round(8 * scale);
        int rowsH = bottom - top;

        List<Row> keyRows = new ArrayList<>(), setRows = new ArrayList<>();
        for (Row r : sheetRows) (r.control() instanceof Keybind ? keyRows : setRows).add(r);
        int content = 0;
        if (!keyRows.isEmpty()) { content += sectionH(); for (Row r : keyRows) content += rowHeight(r); }
        if (!setRows.isEmpty()) { content += sectionH(); for (Row r : setRows) content += rowHeight(r); }
        sheetScrollGoal = Math.max(0f, Math.min(Math.max(0f, content - rowsH), sheetScrollGoal));
        sheetScroll += (sheetScrollGoal - sheetScroll) * frameEase;
        if (Math.abs(sheetScrollGoal - sheetScroll) < 0.5f) sheetScroll = sheetScrollGoal;

        g.createNewRootLayer();
        int r = Math.round(12 * scale);
        for (int i = 4; i >= 1; i--) {
            int s = Math.round(i * 2 * scale);
            Draw.rect(g, sx - s, sy - s + Math.round(3 * scale), sheetW + 2 * s, sheetH + 2 * s, r + s, Colors.withAlpha(0xFF000000, .05f));
        }
        boolean sheetGlass = CardGuiStyle.glass && com.utilitymod.echo.render.GlassRenderer.available();
        if (sheetGlass) {
            var t = new org.joml.Vector2f[]{ new org.joml.Vector2f(), new org.joml.Vector2f() };
            g.getMatrices().transformPosition(sx, sy, t[0]);
            g.getMatrices().transformPosition(sx + sheetW, sy + sheetH, t[1]);
            com.utilitymod.echo.render.GlassRenderer.queue(new com.utilitymod.echo.render.GlassRenderer.Panel(
                    t[0].x, t[0].y, t[1].x - t[0].x, t[1].y - t[0].y, r, .9f));
            glassFlush(g);
        }
        if (sheetGlass) { }
        else Draw.rect(g, sx, sy, sheetW, sheetH, r, Colors.withAlpha(Colors.lerp(CardGuiStyle.back, 0xFF12040F, .35f), .94f));
        Draw.rect(g, sx, sy, sheetW, Math.round(sheetH * .4f), r, Colors.withAlpha(CardGuiStyle.background, .10f));
        Rounded.outline(g, sx, sy, sheetW, sheetH, r, Math.max(.5f, scale * .6f), Colors.withAlpha(0xFFFFFFFF, .10f));

        for (Row row : sheetRows) row.control().visible = false;
        Scissor.clip(g, sx, top, sheetW, Math.max(0, rowsH), () -> {
            int y = top - Math.round(sheetScroll);
            for (int sec = 0; sec < 2; sec++) {
                List<Row> rows = sec == 0 ? keyRows : setRows;
                if (rows.isEmpty()) continue;
                sectionLine(g, sec == 0 ? LT2.L114 : LT2.L115, sx + padX, sheetW - 2 * padX, y);
                y += sectionH();
                for (Row row : rows) {
                    int h = rowHeight(row);
                    Control c = row.control();
                    boolean inView = y + h > top && y < bottom;
                    c.visible = inView;
                    c.active = inView;
                    if (inView) {
                        int lx = sx + padX, rx = sx + sheetW - padX;
                        if (c instanceof Slider sl) {
                            float ly = y + 10 * scale;
                            Text.draw(g, row.setting().getName(), lx, ly, 8 * scale, INK);
                            String val = sl.shownValue();
                            Text.draw(g, val, rx - Text.width(val, 8 * scale), ly, 8 * scale, INK);
                            Text.draw(g, val, rx - Text.width(val, 8 * scale) + .25f * scale, ly, 8 * scale, INK);
                            c.setX(lx); c.setWidth(rx - lx);
                            c.setHeight(Math.round(12 * scale));
                            c.setY(y + Math.round(20 * scale));
                        } else if (c instanceof Popup) {
                            c.setX(lx - Math.round(6 * scale)); c.setY(y + Math.round(2 * scale));
                            c.setWidth(rx - lx + Math.round(12 * scale)); c.setHeight(h - Math.round(4 * scale));
                        } else {
                            Text.draw(g, row.setting().getName(), lx, y + h / 2f, 8 * scale, INK);
                            int cw = c instanceof Bool ? Math.round(32 * scale) : Math.round(64 * scale);
                            int ch = c instanceof Bool ? Math.round(18 * scale) : Math.round(20 * scale);
                            c.setWidth(cw); c.setHeight(ch);
                            c.setX(rx - cw); c.setY(y + (h - ch) / 2);
                        }
                        c.draw(g, mouseX, mouseY, delta);
                        if (c instanceof Popup pp) openPopups.add(pp);
                    }
                    y += h;
                }
            }
        });

        // scroll indicator
        if (content > rowsH) {
            int barH = Math.max(Math.round(18 * scale), Math.round(rowsH * rowsH / (float) content));
            int barY = top + Math.round(sheetScroll / Math.max(1f, content - rowsH) * (rowsH - barH));
            Draw.rect(g, sx + sheetW - Math.round(5 * scale), barY, Math.max(1, Math.round(2.5f * scale)), barH, Math.round(1.5f * scale),
                    Colors.withAlpha(CardGuiStyle.accent, .85f));
        }

        // header: title + close cross, divider
        Text.draw(g, sheetModule.getName(), sx + padX, sy + headerH / 2f + scale, 10 * scale, INK);
        Text.draw(g, sheetModule.getName(), sx + padX + .3f * scale, sy + headerH / 2f + scale, 10 * scale, INK);
        float xs = 9 * scale, xc = sx + sheetW - padX - xs / 2, yc = sy + headerH / 2f + scale;
        boolean xh = mouseX >= xc - 10 * scale && mouseX < xc + 10 * scale && mouseY >= sy && mouseY < sy + headerH;
        CardGuiIcons.draw(g, LT2.L116, xc - xs / 2, yc - xs / 2, xs, xh ? INK : Colors.lerp(MUTED, INK, .6f));
        g.fill(sx + padX - Math.round(4 * scale), sy + headerH, sx + sheetW - padX + Math.round(4 * scale), sy + headerH + 1, Colors.withAlpha(0xFFFFFFFF, .10f));
        for (int i = openPopups.size() - 1; i >= 0; i--) openPopups.get(i).renderPopup(g, mouseX, mouseY);
    }

    private boolean sheetContentHit(double mx, double my) {
        int[] rc = sheetRect();
        return mx >= rc[0] && mx < rc[0] + rc[2] && my >= rc[1] + sheetHeaderH() && my < rc[1] + rc[3];
    }

    private boolean sheetInside(double mx, double my) {
        int[] rc = sheetRect();
        return mx >= rc[0] && mx < rc[0] + rc[2] && my >= rc[1] && my < rc[1] + rc[3];
    }

    /** The close cross in the sheet header. */
    private boolean sheetHeaderHit(double mx, double my) {
        if (sheetModule == null) return false;
        int[] rc = sheetRect();
        float xc = rc[0] + rc[2] - 16 * scale - 4.5f * scale;
        return mx >= xc - 12 * scale && mx < xc + 12 * scale && my >= rc[1] && my < rc[1] + sheetHeaderH();
    }

    // ---- input ------------------------------------------------------------------

    @Override
    public boolean mouseClicked(Click event, boolean doubleClick) {
        event = local(event);
        if (closing) return true;
        pointerHeld = true;
        if (sheetModule == null && page != 0) {
            if (page == 4 && event.button() == 1) {
                // reference law: right click on the options page resets the open key
                dev.koifih.client.input.Keybinds.setOpenKeyCode(GLFW.GLFW_KEY_RIGHT_SHIFT);
                GuiConfig.markDirty();
                return true;
            }
            if (page == 1 && profileInput.isMouseOver(event.x(),event.y())) {
                focus=profileInput; search.setFocused(false); profileInput.setFocused(true); profileInput.onClick(event,doubleClick); return true;
            }
            for (Hotspot h : hotspots) if (h.contains(event.x(),event.y())) {
                if (event.button()==0) { h.action.accept(event.x(),event.y()); if(h.drag) dragging=h; }
                return true;
            }
        }
        if (sheetModule != null) {
            for(Control c:sheetControls) if(c instanceof Keybind k && k.captureMouse(event)) return true;
            // open dropdown lists first, then rows/controls, then outside-click close
            for (Control c : sheetControls) {
                if (c instanceof Popup p && p.isOpen() && p.mouseClicked(event, doubleClick)) return true;
            }
            for (Control c : sheetControls) {
                if (sheetContentHit(event.x(),event.y()) && c.active && c.visible && c.isMouseOver(event.x(), event.y())) {
                    focus = c;
                    c.onClick(event, doubleClick);
                    return true;
                }
            }
            if (event.button() == 0 && sheetHeaderHit(event.x(), event.y())) {
                closeSheet();
                com.utilitymod.gui.UiSounds.play(com.utilitymod.gui.UiSounds.Sound.TAB);
                return true;
            }
            if (event.button() == 0 && !sheetInside(event.x(), event.y())) closeSheet();
            return true;
        }
        // search field
        if (search.isMouseOver(event.x(), event.y()) && search.visible) {
            focus = search;
            profileInput.setFocused(false); search.setFocused(true);
            search.onClick(event, doubleClick);
            return true;
        }
        // sidebar navigation
        if (event.x() >= panelX && event.x() < contentX()) {
            boolean inX = event.x() >= navLeft && event.x() < navRight;
            if (inX && event.button() == 0) for (int i = 0; i < CLIENT_LINKS.length; i++) {
                if (event.y() >= linkRowY[i] && event.y() < linkRowY[i] + navRowH) { setPage(i + 1); return true; }
            }
            if (inX) for (int c = 0; c < CATS.size(); c++) {
                if (event.y() >= catRowY[c] && event.y() < catRowY[c] + navRowH) {
                    Cat cat = CATS.get(c);
                    setPage(0);
                    selectedCat = c;
                    query.setLength(0);
                    focus = null;
                    com.utilitymod.gui.UiSounds.play(com.utilitymod.gui.UiSounds.Sound.TAB);
                    scrollGoal = 0f;
                    return true;
                }
            }
            return true;
        }
        if (page != 0) return true;
        // cards
        if (event.button() == 0 || event.button() == 1) {
            Module hit = cardAt(event.x(), event.y());
            if (hit != null) {
                if (event.button() == 1) {
                    openSheet(hit);
                    com.utilitymod.gui.UiSounds.play(com.utilitymod.gui.UiSounds.Sound.POPUP);
                    return true;
                }
                // gear zone opens settings instead of toggling
                int gap = Math.round(CARD_GAP_UNITS * scale);
                int gridW = contentW() - Math.round(24 * scale);
                int cw = Math.max(1, (gridW - 1 * gap) / COLS);
                int cardH = Math.round(CARD_HEIGHT_UNITS * scale);
                int toggleW = Math.round(32 * scale);
                int x = cardX(hit, cw);
                int y = cardY(hit, cardH, gap);
                int tx = x + cw - toggleW - Math.round(12 * scale);
                int gx = tx - Math.round(24 * scale);
                boolean onGear = hit instanceof Feature f && !f.getSettings().isEmpty()
                        && event.x() >= gx && event.x() < tx - Math.round(4 * scale)
                        && event.y() >= y + 6*scale && event.y() < y + 32*scale;
                if (onGear) {
                    openSheet(hit);
                    com.utilitymod.gui.UiSounds.play(com.utilitymod.gui.UiSounds.Sound.POPUP);
                    return true;
                }
                hit.setEnabled(!hit.isEnabled());
                com.utilitymod.gui.UiSounds.play(hit.isEnabled() ? com.utilitymod.gui.UiSounds.Sound.TOGGLE_ON : com.utilitymod.gui.UiSounds.Sound.TOGGLE_OFF);
                Toasts.push(hit.isEnabled() ? Toasts.ICON_ON : Toasts.ICON_OFF,
                        hit.getName() + (hit.isEnabled() ? LT.L36 : LT.L37));
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private int cardX(Module target, int cardW) {
        List<Module> mods = visible();
        int gap = Math.round(CARD_GAP_UNITS * scale);
        for (int i = 0; i < mods.size(); i++) {
            if (mods.get(i) == target) {
                return contentX() + Math.round(12 * scale) + (i % COLS) * (cardW + gap);
            }
        }
        return -1;
    }

    private int cardY(Module target, int cardH, int gap) {
        int top = panelY + Math.round(92 * scale);
        List<Module> mods = visible();
        for (int i = 0; i < mods.size(); i++) {
            if (mods.get(i) == target) {
                return top - Math.round(scroll) + (i / COLS) * (cardH + gap);
            }
        }
        return -1;
    }

    private Module cardAt(double mx, double my) {
        if(mx<contentX() || mx>=panelX+panelW || my<cardsTop() || my>=contentY()+contentH()-Math.round(14*scale)) return null;
        int top = panelY + Math.round(92 * scale);
        int gap = Math.round(CARD_GAP_UNITS * scale);
        int cardH = Math.round(CARD_HEIGHT_UNITS * scale);
        int gridW = contentW() - Math.round(24 * scale);
        int cw = Math.max(1, (gridW - (COLS - 1) * gap) / COLS);
        List<Module> mods = visible();
        for (int i = 0; i < mods.size(); i++) {
            int x = contentX() + Math.round(12 * scale) + (i % COLS) * (cw + gap);
            int y = top - Math.round(scroll) + (i / COLS) * (cardH + gap);
            if (mx >= x && mx < x + cw && my >= y && my < y + cardH) return mods.get(i);
        }
        return null;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double dx, double dy) {
        { org.joml.Vector2f v = tiltInv.transformPosition(new org.joml.Vector2f((float) x, (float) y)); x = v.x; y = v.y; }
        if (x < contentX() || x >= panelX+panelW || y < panelY+48*scale || y >= panelY+panelH) return false;
        // Open dropdown first: the wheel scrolls its list, never the sheet behind it.
        if (sheetModule != null) {
            for (Control c : sheetControls) {
                if (c instanceof Popup p && p.isOpen() && p.scrolledWidget(x, y, dy)) return true;
            }
            boolean popupOpen = false;
            for (Control c : sheetControls) popupOpen |= c instanceof Popup p && p.isOpen();
            if (!popupOpen) sheetScrollGoal += dy < 0 ? 24 * scale : -24 * scale;
            return true;
        }
        if (page != 0) { pageScroll=Math.max(0,Math.min(pageMax,pageScroll-(float)dy*25*scale)); return true; }
        if (x >= contentX() && x < panelX + panelW) {
            scrollGoal += dy < 0 ? 60 * scale : -60 * scale;
            return true;
        }
        return super.mouseScrolled(x, y, dx, dy);
    }

    @Override
    public boolean mouseDragged(Click event, double dx, double dy) {
        event = local(event);
        if (dragging!=null) { dragging.action.accept(event.x(),event.y()); return true; }
        if (sheetModule != null) {
            for (Control c : sheetControls) if (c.active && c.visible) c.onDrag(event, dx, dy);
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(Click event) {
        event = local(event);
        pointerHeld=false;
        if(dragging!=null) { dragging=null; GuiConfig.markDirty(); if(Math.abs(fitScale()-scale)>.001f)init(); return true; }
        if (sheetModule != null) {
            for (Control c : sheetControls) if (c.active && c.visible) c.onRelease(event);
            return true;
        }
        return super.mouseReleased(event);
    }

    private Control focusedControl() {
        return focus;
    }

    public boolean keyPressed(KeyInput event) {
        if (closing) return true;
        if(bindingMenu || bindingModule!=null) {
            if(event.key()!=GLFW.GLFW_KEY_ESCAPE) {
                if(bindingMenu) dev.koifih.client.input.Keybinds.setOpenKeyCode(event.key());
                else ModuleBinds.set(bindingModule,event.key()==GLFW.GLFW_KEY_BACKSPACE || event.key()==GLFW.GLFW_KEY_DELETE ? -1 : event.key());
            }
            bindingMenu=false; bindingModule=null; GuiConfig.markDirty(); return true;
        }
        if(event.key()==GLFW.GLFW_KEY_F && (event.modifiers() & GLFW.GLFW_MOD_CONTROL)!=0 && search.visible) { focus=search; search.setFocused(true); return true; }
        Control focused = focusedControl();
        if (focused != null && focused.capturesInput() && focused.keyPressed(event)) return true;
        if (sheetModule != null) {
            for (Control c : sheetControls) if (c.capturesInput() && c.keyPressed(event)) return true;
        }
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (sheetModule != null) {
                closeSheet();
                return true;
            }
            requestClose();
            return true;
        }
        if (dev.koifih.client.input.Keybinds.matches(event)) {
            requestClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharInput event) {
        Control focused = focusedControl();
        if (focused != null && focused.capturesInput()) return focused.charTyped(event);
        if (sheetModule != null) {
            for (Control c : sheetControls) if (c.capturesInput() && c.charTyped(event)) return true;
        }
        return super.charTyped(event);
    }

    private void requestClose() {
        if (closing) return;
        closing = true;
        com.utilitymod.gui.UiSounds.play(com.utilitymod.gui.UiSounds.Sound.CLOSE);
        com.utilitymod.gui.GuiConfig.save();
        reveal.set(0f);
    }

    @Override
    public void tick() {
        if (closing && reveal.value() <= 0f) {
            MinecraftClient.getInstance().setScreen(null);
        }
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xbaf4,0x59b0,0x56cc,0x1603,0x7c40,0xf406,0xfad2}, new char[]{0x568f,0x59df,0xf468,0x7c26,0x166a,0xfab5,0xba87}).toString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xdb35,0xaef8,0x16c9,0xe054,0x4a2d,0xf0d4}, new char[]{0xaeac,0xdb5d,0xf0b1,0xe039,0x4a48,0x16ba}).toString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x56ac,0x1006,0x646a,0x67b3,0x5fe1,0x3b39}, new char[]{0x104b,0x5f80,0x3b5a,0x56de,0x6405,0x67c0}).toString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x11a3,0x962b,0xa708}, new char[]{0x9663,0x11f6,0xa74c,0x8072}).plainString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x63f7,0xf9c7,0x656c,0xb795,0x0480,0xd737}, new char[]{0x04c3,0xd758,0xb7f8,0x650e,0x6396,0xf9b3}).toString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa4e1,0xb378,0xefec,0x5042,0x3997,0xba26,0x2d16,0x23fc}, new char[]{0xa4ac,0x2393,0x2d60,0xba43,0xef81,0x39f2,0xb316,0x5036}).plainString();
        static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x328a,0x6c96,0x2352,0x0f41,0xcf30,0x86e5,0xf4d6}, new char[]{0x32dc,0x233b,0xf4a5,0x6ce3,0x8684,0x0f2d,0xcf43}).toString();
        static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x24df,0xf093,0xc55e,0x696b,0xc59b,0x27f4}, new char[]{0xf0d5,0x279d,0x24b1,0xc5ff,0xc53b,0x6919}).toString();
        static final String L9 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0361,0xc1fc,0x4c79,0x634d}, new char[]{0x032c,0x6324,0xc18f,0x4c1a}).toString();
        static final String L10 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf7b8,0x9b4f,0x4289,0x1554,0x8236}, new char[]{0x9b0b,0x42e6,0x8258,0x1521,0xf7cc}).toString();
        static final String L11 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xaa5b,0x1fb4,0x3716,0x9f4f,0xf5e6,0x3162,0x80dc,0x5260,0x7a38,0x1999,0x3d54,0xc904,0x1212,0xcc5e,0x0e50,0xdc53}, new char[]{0x8089,0xc970,0x19f0,0x127e,0x310b,0x5214,0x1fcd,0x9f02,0x7a57,0xdc37,0xf5c6,0x3d17,0xaa3a,0x0e22,0xcc3a,0x3765}).plainString();
        static final String L12 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x43a5,0xc8d0,0x01d4,0x22b7,0xa346,0x53dd,0xcfbd,0x47ec,0x9ad0}, new char[]{0x43f6,0x22d2,0x9ab1,0xa334,0xc8b3,0x4784,0xcf93,0x53f3,0x01fa}).plainString();
        static final String L13 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4977,0x6be8,0xca19,0x693f,0x2f73,0x1c70}, new char[]{0xca69,0x491b,0x1c11,0x6b91,0x2f16,0x694d}).plainString();
        static final String L14 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf56c,0xc968,0x14a6,0x7a18,0x49eb,0xbca8,0xe42f,0xabf8,0xf2e5,0x2f51}, new char[]{0xc90c,0xe40f,0xabb5,0x14eb,0x2f1c,0xbc88,0xf290,0x499e,0xf519,0x7a6d}).toString();
        static final String L15 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x53b4,0xd948,0x0e11,0xd079,0x6c32,0x3b5e,0xf78e}, new char[]{0xd02c,0x6c46,0xd921,0xf7e2,0x3b37,0x0e65,0x53cd}).toString();
        static final String L16 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3f4b,0x3e63,0x1c80}, new char[]{0x3e2e,0x3f24,0x1ce4,0xe9e1}).toString();
        static final String L17 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0471,0x7666,0x26cb,0xa29c,0xe333,0xaf1b,0x3443,0x9b10,0x9505}, new char[]{0x9b53,0xa2dd,0xe361,0x9541,0x7646,0xaf4d,0x0438,0x3406,0x269c}).toString();
        static final String L18 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x61f0,0xa667,0xc2ea,0x5926,0x73fb,0x724c,0xacf9}, new char[]{0xacb4,0x7223,0x5942,0x6185,0xc286,0x739e,0xa614}).toString();
        static final String L19 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2f63,0x2ef6,0x835f,0xdc82,0x1220,0xa0d2}, new char[]{0x1263,0x8333,0xdceb,0x2e93,0x2f0d,0xa0a6}).toString();
        static final String L20 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1de2,0xdf0b,0x3468,0x3e9a,0x65d0,0xab9d,0x8714}, new char[]{0x6587,0xdf4a,0x1db0,0x3426,0x875d,0xabd3,0x3edd}).plainString();
        static final String L21 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3f8a,0x35ec,0x7c32,0x7849,0x43e3,0x055d,0xcce2,0x91c6,0x06cd,0xfebc,0xf06e,0x53c2,0xafa8,0x57b5,0xffcd,0x4bda,0xe645,0x0aa1,0xa8ae,0xc616,0x6dcf,0x3205,0x9b45,0xf771,0xa05e,0x3c86,0x7025,0xc02a,0x8019,0xd2dd,0xbfee,0x82d5,0x6087,0x9faa,0x9bb3,0xa86b,0x72f5,0xfdb1,0x8a08,0x4d65,0x5efd,0xf114,0xfae0,0x75a2,0xf691,0x64b5,0x035b,0x5eb9,0xdbb7,0x3f06,0x060c,0x7bf6,0x8506,0x4285,0xc7be,0x3a63,0x7cf2,0xf27d,0xda24,0x574d,0xbe5e,0xd8fa}, new char[]{0x5381,0x57dd,0xf175,0xfa8e,0x9bd4,0x7c57,0x4d16,0xda04,0x91a7,0xc05a,0x067c,0x7049,0x5ec0,0xf6b1,0xa032,0x326c,0x4395,0x3f63,0x4bf4,0x5edd,0xf722,0xc677,0xafde,0xfed9,0x35cc,0x728c,0x0334,0xffb8,0x9fd8,0xf04e,0xd899,0xd2b2,0x7827,0xc7d8,0x0534,0x3ce1,0xbe7e,0x64d7,0x9b20,0x7c94,0x8a67,0xf20f,0x6daa,0x8526,0x3ff9,0x75d5,0xdbde,0x60f3,0x807a,0x0ac9,0x06a4,0xa8c0,0xa80c,0xe665,0x7b95,0xfddd,0x5724,0xbf8b,0xcc8c,0x42f1,0x82a6,0x3a4d}).toString();
        static final String L22 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc6e5,0x9fdb,0x957b}, new char[]{0x9529,0x9fbe,0xc681,0x1ddb}).toString();
        static final String L23 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x05d7,0x7a0a,0x8378,0x451c,0xfef3,0x3c89}, new char[]{0xfebc,0x3cfb,0x8319,0x7a64,0x457b,0x05b2}).toString();
        static final String L24 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xfe33,0x85d0,0x7f6d,0xba3a,0xe5de,0x59c3}, new char[]{0xe587,0x59a6,0xba56,0x7f01,0xfe5c,0x85a7}).toString();
        static final String L25 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xbeee,0x15a7,0x4639,0x3b68,0xfebe}, new char[]{0xbea9,0x15d5,0x465c,0xfedb,0x3b06}).toString();
        static final String L26 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x17fc,0x6f1d,0x95e6,0x9ab7}, new char[]{0x6f5e,0x959f,0x9ad6,0x1792}).toString();
        static final String L27 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xed4f,0x601b,0xbdff,0xebb3}, new char[]{0xebf1,0x6077,0xbd8a,0xed2a}).toString();
        static final String L28 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1342,0x1bb8,0xeb91,0xb90b,0xeb26,0x30c8}, new char[]{0x1312,0xeb53,0xebe3,0xb97b,0x1bd4,0x30ad}).toString();
        static final String L29 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x452b,0xae72,0xe12e,0xbab3}, new char[]{0xe17e,0xbada,0xae1c,0x4540}).toString();
        static final String L30 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3d73,0x07c4,0xa0e1,0x52e9,0x2eb8}, new char[]{0x0793,0xa089,0x2ed1,0x3d07,0x528c}).plainString();
        static final String L31 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa407,0x83e0}, new char[]{0x83c5,0xa429,0x8bba,0x605e}).plainString();
        static final String L32 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe5af}, new char[]{0xe5c9,0x6c0b,0x1f39,0xdeec}).plainString();
        static final String L33 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0b49,0xd2f6,0xd3be,0x2836,0xd1e1,0x0491}, new char[]{0xd3fd,0x04de,0xd2b8,0x2870,0xd1a8,0x0b0e}).plainString();
        static final String L34 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa9e9,0x8ad7,0xa889,0x53a9,0x1654}, new char[]{0x8a83,0xa9a1,0xa8cc,0x53e4,0x1611}).plainString();
        static final String L35 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1d91,0x578e,0x1a80,0x50f7,0xc6bd}, new char[]{0x1ac4,0xc6f2,0x50b9,0x57db,0x1dc5}).plainString();
        static final String L36 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc9dc,0x6c28,0x0150,0x3abe,0x172f,0xafec,0xcd17,0xa393}, new char[]{0xafcc,0x6c4d,0xa3fd,0x3adf,0x174d,0xc9b0,0xcd72,0x0134}).plainString();
        static final String L37 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9da8,0xee44,0x91f8,0x22e1,0xdd3c,0x898b,0xef82,0xcbbd,0xe61e}, new char[]{0xdd1c,0xe67a,0x89e2,0xeff1,0xee25,0x9dca,0x9194,0x2284,0xcbd9}).plainString();
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT2 {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9506,0x761e,0x168b,0x0e26,0x19cd,0xfa0a,0x2806}, new char[]{0x9545,0xfa65,0x7670,0x2860,0x16e2,0x0e41,0x19be}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8265,0xe42f,0xcb65,0x6eae,0xfafa,0x74b6}, new char[]{0xfaae,0x820d,0xcb00,0x6ec3,0x74d3,0xe45c}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9f1b,0xf2a6,0x3e95,0xffa2,0x46b0,0xa3ac,0x7765,0x6b28}, new char[]{0xffe9,0x3ef0,0x771c,0xf2c4,0xa3c5,0x46de,0x6b4c,0x9f68}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x742f,0xdf6c,0x35db,0x4b2d,0xfb1b,0x842b,0x0fc8}, new char[]{0x8464,0x745f,0xfb6f,0x0fa1,0x4b42,0xdf02,0x35a8}).toString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0b69,0x7378,0x66bd,0x7614,0xfefa}, new char[]{0xfea9,0x66ca,0x767b,0x0b1b,0x731c}).plainString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xbc04,0xd605,0x4e1a,0xaa2f,0x5e2c,0x202b,0xb6d9,0x1123}, new char[]{0xb6b4,0x2044,0xbc72,0xaa4a,0xd668,0x5e49,0x4e74,0x1157}).plainString();
        static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2a45,0x5cdf,0x5141}, new char[]{0x2a20,0x5ca6,0x5124,0x63c5}).plainString();
        static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1fcd,0xcafc,0x3532,0x1f25,0x650d,0x1118,0x274c}, new char[]{0x656e,0x1fa2,0x355f,0x273c,0xca9d,0x1f56,0x116b}).plainString();
        static final String L9 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3df1,0x758b,0xaeb5,0x37c6,0xf33b,0x865c,0x8044}, new char[]{0x8037,0x75e7,0x3d98,0xf35f,0x37a3,0xaec7,0x862f}).plainString();
        static final String L10 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd8ee,0xb3be,0x1754,0x53e5,0x0ca2,0x5ea0}, new char[]{0x1727,0xb3d6,0x0ccb,0xd88b,0x5389,0x5ec4}).plainString();
        static final String L11 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xbde8,0xb0f9,0x719c,0x651e,0xace4,0x7df7,0x419c}, new char[]{0x655d,0x41ee,0x7d8e,0xb08a,0xbd9c,0x71fd,0xac88}).plainString();
        static final String L12 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf380,0x5134,0xa7b6,0x5676,0x8600,0x31d3}, new char[]{0x5157,0xf3ef,0x866d,0xa7d4,0x31b2,0x5602}).plainString();
        static final String L13 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa076,0x2c11,0x2c68,0x42a0,0x20d5,0x6a2b,0x489d}, new char[]{0x2c0b,0x20a7,0x6a52,0x48ee,0x2c65,0x42c1,0xa01a}).plainString();
        static final String L14 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x056b,0xf7ab,0xfb90,0x8113,0x17e5,0xdf1d}, new char[]{0x1784,0x0505,0xfbf3,0xf7c3,0x817c,0xdf6f}).plainString();
        static final String L15 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3add,0xc563,0x7416,0x7c57}, new char[]{0xc500,0x3ab4,0x7c23,0x746f}).plainString();
        static final String L16 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6b61,0x3efb,0x27f4,0x00f5,0x9a22}, new char[]{0x9a43,0x6b04,0x2793,0x3e92,0x0086}).plainString();
        static final String L17 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xddb9,0x36ef,0xf525,0xb861,0xe2eb}, new char[]{0xddcd,0xf54a,0xb815,0x368a,0xe286}).plainString();
        static final String L18 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7128,0x28f5,0x01d7,0xac40,0x4f03}, new char[]{0x28a6,0xac30,0x714d,0x01b6,0x4f71}).plainString();
        static final String L19 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb2ba,0x3c58,0x2423,0xb941,0x49af}, new char[]{0x3c2b,0xb2ca,0x2446,0x49ce,0xb933}).plainString();
        static final String L20 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe513,0x7cce,0x499e,0x85b0,0xb788}, new char[]{0x85dc,0x49eb,0xb7e6,0x7ca9,0xe576}).plainString();
        static final String L21 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xbca6,0x0a71,0xe92a,0x15f3}, new char[]{0x0a3c,0x1592,0xe949,0xbcc3}).plainString();
        static final String L22 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1ed7,0xc287,0x1ea8,0xae6e}, new char[]{0xae03,0x1eb6,0x1ecb,0xc2e2}).plainString();
        static final String L23 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x95e2,0x9361,0x00c9,0xf597}, new char[]{0x9591,0xf5e3,0x9314,0x00a7}).plainString();
        static final String L24 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x31c3,0x8b63,0x512a,0xc2ef,0x4d6d,0x875d}, new char[]{0x5148,0xc29d,0x31a6,0x4d0c,0x8b00,0x8735}).plainString();
        static final String L25 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xafba,0x99ef,0x3f9a}, new char[]{0x998c,0x3ff5,0xafd8,0x20fe}).plainString();
        static final String L26 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xdf5f,0x55a6,0x02f0,0xe556,0x8dc5,0xf120,0xc9c0}, new char[]{0xe522,0x8db7,0xc9a9,0xdf38,0xf147,0x0295,0x55d4}).plainString();
        static final String L27 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8dcc,0x9d9f,0xa565}, new char[]{0x9dfe,0x8da5,0xa508,0x9a8c}).plainString();
        static final String L28 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x66d9,0xb273,0x05b1,0xa493,0x2397,0x3800}, new char[]{0x66b1,0xb21a,0xa4e7,0x23f5,0x05de,0x3878}).plainString();
        static final String L29 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9a3d,0xc570,0x4b24,0x5419,0x749a,0x0a43,0xd059,0xbcb2,0xa34e}, new char[]{0xbcd0,0x0a22,0x547a,0x9a56,0xc504,0x74e8,0xd038,0x4b47,0xa325}).plainString();
        static final String L30 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3468,0xd002,0x7043,0xdd27,0xd3cf,0x19bd,0xc850,0xcb91,0x9e63,0x739c}, new char[]{0xc83a,0xd077,0xcbfc,0x9e13,0xdd07,0x341a,0x19d8,0x73ef,0xd3aa,0x7037}).plainString();
        static final String L31 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x452c,0x3504,0x7c0d,0x4d16,0x6d72,0x1e10,0x1cc6,0x6ac3,0xeaba,0x702f,0xfb5f}, new char[]{0x356f,0x7c68,0x6d17,0x6ab3,0x700f,0x455f,0xeaca,0x4d64,0xfb36,0x1ca8,0x1e64}).plainString();
        static final String L32 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf5bd,0x2f61,0x4c29,0xb6a5,0xf5a7,0x080f,0xed21,0xa0a1}, new char[]{0xf5d1,0xed44,0x0863,0xf5d2,0xb6c6,0xa0c8,0x2f15,0x4c50}).plainString();
        static final String L33 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3d2f,0x41dd,0x331c,0x4549,0x76d0}, new char[]{0x41be,0x3d40,0x4524,0x76b2,0x3373}).plainString();
        static final String L34 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7d3c,0x88ff,0x58c5,0x684a,0xaa3f,0x48bb,0x2f18}, new char[]{0x888f,0x2f77,0x6823,0x58ab,0x7d48,0xaa5a,0x48c9}).plainString();
        static final String L35 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc65c,0x291c,0x5ded,0xaa8a,0x644d,0xedec,0xb963,0x1365}, new char[]{0x1316,0x2972,0x5d8c,0xaafa,0xc67c,0xb917,0x642c,0xed9c}).plainString();
        static final String L36 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6ee3,0x9d39,0x84aa,0x3893,0xebec,0x502f}, new char[]{0x38e0,0x9d49,0x84d8,0x6e8a,0x5041,0xeb98}).plainString();
        static final String L37 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x73e2,0xd274,0xe92c,0x58d7,0x421c,0xb86f}, new char[]{0x4268,0xd215,0x7390,0xe94b,0xb80a,0x58a3}).plainString();
        static final String L38 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8321,0x233b,0x290e}, new char[]{0x295d,0x836c,0x236b,0x1c64}).plainString();
        static final String L39 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xdc8d,0xffe4,0x0f68,0xac9e,0xf7b4}, new char[]{0xff80,0xdcff,0xf7d5,0xacf7,0x0f06}).plainString();
        static final String L40 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xbbea,0xf1c3,0xe6f0,0x913d}, new char[]{0xe693,0x915c,0xbb98,0xf1b7}).plainString();
        static final String L41 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xaae7,0xadf7,0xd2f2,0x8223,0x177b,0x4dfa,0x038b}, new char[]{0x4d98,0xd293,0x03e8,0xad9c,0xaa88,0x171d,0x8245}).plainString();
        static final String L42 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x174e,0x6a88,0x26bd}, new char[]{0x172b,0x6ae9,0x26c9,0x2512}).plainString();
        static final String L43 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc0ff,0x2238,0x12d4,0xf42f,0xf200}, new char[]{0xc09e,0xf45d,0x12b9,0x2257,0xf272}).plainString();
        static final String L44 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8daf,0xb970,0x310e,0x9ff3,0x271d,0x8cfc}, new char[]{0xb91c,0x8dc0,0x277a,0x3161,0x9f86,0x8c88}).plainString();
        static final String L45 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xde5d,0x92fb,0x1f0d,0x0b30,0xee1d,0x5899,0x4909,0x595c,0x1a48,0x415a,0xd6b1,0x3c3d}, new char[]{0x1a3e,0xee74,0x592f,0x0b45,0x3c5c,0xde31,0x1f2d,0x497b,0x413b,0x9295,0x58fe,0xd6d4}).plainString();
        static final String L46 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe842,0x19b2,0x1b31,0xa34b,0x6058,0x1f84,0x9dff,0x3920,0xbfa3}, new char[]{0xe830,0x1fe1,0xa328,0x9d90,0x6036,0x19dc,0xbfc6,0x3943,0x1b45}).plainString();
        static final String L47 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2019,0xecd2,0x1e1c}, new char[]{0x1e75,0x2077,0xeca4,0x7d91}).plainString();
        static final String L48 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x74e5,0x0bda,0xe143,0xb32d,0xae43,0x80c8,0x1372,0x10f2,0x55aa}, new char[]{0x1093,0x0baf,0x1306,0x55c5,0xae63,0xe137,0x748a,0x80a7,0xb341}).toString();
        static final String L49 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x641e,0x43af,0xc74f,0x66db,0x3100,0xaf89,0x8e98,0x0992}, new char[]{0x66ba,0xc73a,0x43db,0x8ef7,0x3174,0xafe6,0x6471,0x09fe}).plainString();
        static final String L50 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa866,0xa4f0,0x8d14,0xa187,0xd602,0x10ad}, new char[]{0x8d72,0xa1ee,0xa49e,0x10c9,0xd667,0xa814}).plainString();
        static final String L51 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5de1,0x9b42,0x5bb9,0xe5d8,0x407a}, new char[]{0x9b2f,0x5bd0,0x5d8f,0xe5bd,0x4008}).plainString();
        static final String L52 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x248e,0x7f76,0x4c71,0x191d,0x386e,0x8bf0,0xc4a6,0x07de,0x89d6,0x84e6,0xf704,0x5c9f,0x329b,0x5a59,0xed39,0xadfa,0x37e2}, new char[]{0x5ccc,0x8b95,0xf765,0x7f04,0xed5a,0x32f3,0xc486,0x378f,0x4c1e,0x24ea,0x89a3,0xad96,0x380b,0x8495,0x5a77,0x1933,0x07f0}).toString();
        static final String L53 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf564,0xed11,0x3fdd,0x06af,0x95a5,0xb3a1,0xab63,0x215e,0x65bc,0xd520,0xa7ab,0x30c5,0xe985,0x9273,0xc8d6,0x451c,0x77b9,0x79bb,0x728a}, new char[]{0x77f7,0xd545,0xa7dc,0x3ffd,0x456c,0x212c,0xf50b,0x79dd,0x30ac,0xed7d,0x06ca,0x9585,0xc8b8,0x9212,0xb3cc,0xab06,0x6592,0xe9ab,0x72a4}).plainString();
        static final String L54 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc176,0x4ad3,0x1ab4,0x63d8,0xbc28,0x14b9,0x0678,0x2648}, new char[]{0x4af3,0xbc45,0x14d6,0x262c,0x060d,0xc11a,0x1ad1,0x63ab}).plainString();
        static final String L55 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x69c2,0xc22f,0x5660}, new char[]{0x69e2,0x560f,0xc241,0x9711}).plainString();
        static final String L56 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3add,0xd8af,0x7ffa,0xde8f}, new char[]{0x3a8e,0x7f9b,0xdef9,0xd8ca}).toString();
        static final String L57 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5238,0x21a3,0x8561,0xe183,0x38c6,0x26fa,0x49c7,0x8c96,0x47d3,0xaf8f,0xf479,0x1bb2,0x852e}, new char[]{0x4783,0x1bc0,0x8541,0xafe9,0xe1ea,0x2696,0x49a2,0x8541,0x21d0,0x8cf7,0x524e,0xf41c,0x38a2}).toString();
        static final String L58 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb4ce,0x8e17,0x8a79,0xc787,0xfbb4,0xef17,0x78dd,0x9095,0xcca0,0x565d,0xdb31,0xa2e5,0x0321,0x1233,0xd6cb,0x8104,0x8eee,0x7fab,0x83a4,0x6314,0xafc7,0x465e,0x731d,0x1338,0xf1fd,0x8660}, new char[]{0x5618,0xb4a0,0xf189,0x8a1c,0x1241,0xef37,0x463f,0x8124,0xfbc2,0x737c,0x8e7b,0xa28c,0x0345,0xd6eb,0x1348,0x83d6,0x90fa,0xdb57,0x7fc2,0xcccc,0x8e8b,0x8640,0x637a,0xc7e6,0xafaa,0x78b8}).toString();
        static final String L59 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x51b9,0xfc31,0x7a8d,0x586b}, new char[]{0x5827,0x7ae2,0x51d8,0xfc55}).toString();
        static final String L60 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6d6d,0xbb8e,0x66d0,0x7068,0x946e,0x0edf,0x446a,0x712f,0xca02,0xa83f,0x4617,0x8e06,0x3bbe,0xb767}, new char[]{0x8e56,0xbbfc,0x7007,0x0eb9,0x4403,0x7143,0xca67,0x66f0,0x9402,0xa850,0xb706,0x6d09,0x4672,0x3bda}).toString();
        static final String L61 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x84db,0x20ba,0x94bd,0x71f9,0xdc25,0xdf80,0x30dc,0x8b64,0x899f,0xf925,0x92ac}, new char[]{0xf969,0xdfef,0x84ba,0x719d,0xdc05,0x20dc,0x89fe,0x94d4,0x30b0,0x92c9,0x8b00}).toString();
        static final String L62 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x45c4,0x6dbe,0xf43f,0xac25}, new char[]{0x459d,0xf45a,0x6dcd,0xac1a}).toString();
        static final String L63 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0d0b}, new char[]{0x0d53,0x5e13,0x1d07,0x7195}).plainString();
        static final String L64 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa59d,0xb567,0xcf69,0xabd1,0x9889,0x52cd,0xb5b8,0x963e,0xfc1a,0xec51,0x05c2,0x7cdf,0x8dd2,0x7b56,0x7550,0x1b53,0xc245,0x9b49,0x1af9,0xbf3e,0x10e7}, new char[]{0x7c9c,0xb50b,0x9b20,0xcf0a,0x52a6,0x7570,0x9667,0x98ec,0xbf4d,0x10d8,0x1ad9,0xfc6e,0x7b39,0xabf1,0x05a6,0xb5dd,0xc229,0xa5f8,0x8da6,0xec34,0x1b73}).toString();
        static final String L65 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc0ff,0xb340,0xa57d,0xd468,0x0bed,0x5725,0x5a80,0x00f3,0xc44a,0xba64,0xc928,0x0f1d,0x645a,0x4d20,0xcbc8,0x8be3,0x2c13,0xb432,0xbd77,0x32d6,0x3499,0xc92b}, new char[]{0x576b,0xa512,0x4d00,0x2c60,0x643b,0xcbbe,0xc09a,0xb324,0x0bcd,0x0f6d,0xc959,0x32b9,0x5ae6,0xd401,0xbd1b,0x0096,0xc439,0xc908,0x8b9a,0xba01,0xb446,0x34b7}).toString();
        static final String L66 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa91f,0x61a3,0x57cf,0x221d,0xc0b8,0xb88c,0x0b51,0x5065,0x8ffd,0xa5eb,0x17bc,0x2ec8,0x84b6,0x149b,0x1696,0x31dd,0xd566,0x3f2c,0xae8a,0xf1d7,0x2d43,0x7b99,0xb838,0x09fd,0x745f,0x0bef,0x21aa,0xa1cb,0xda92,0xabcd,0xd71e,0xa365,0x069d,0x55a2,0xe4a7,0x2b28,0xbad4,0xb1c3,0xd299,0x951e,0x33e6,0x992a,0xfdb8,0xe981,0x8e7e,0x1405,0x9e5f,0x704f,0x4462,0x3e0a,0x9ee2,0xcacd,0xb2e5,0x17ee,0x0518,0x382b,0x7cf7}, new char[]{0x386a,0x7bec,0xb291,0x2d2c,0xabbe,0xbab5,0x3e7c,0xe4c2,0x706f,0x9e34,0x0b34,0x2b4d,0x17cc,0x61d0,0x31fd,0x5011,0xdafa,0x14fe,0x218a,0xaee9,0x55d7,0xe9f3,0x056a,0xd2fc,0xd508,0x0989,0xa1eb,0xcabe,0x8e0a,0x3f4d,0x742b,0xfddd,0x143e,0x7cd7,0x3396,0xd76c,0x0b80,0xb8ea,0x440b,0x8f91,0x16f3,0xf1a4,0xb1e3,0xa304,0xa96d,0xb85d,0x06bd,0xa598,0x2273,0x57ae,0x9e92,0x9959,0x1786,0x9571,0xc0cc,0x84c5,0x2ee6}).toString();
        static final String L67 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x66ee,0xc58a,0x8cf3,0x2ff6,0x4681,0x742d,0xae5f}, new char[]{0x66ad,0x7442,0x2f9a,0x8c9c,0xc5ff,0xae2d,0x46f2}).toString();
        static final String L68 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x237f,0x9ffa,0x2a3e,0x474f,0xd585,0x5d9f,0x1c77,0xc1a6,0x50e5,0xb724,0x03b4}, new char[]{0x50a8,0xd5e4,0x9f93,0xb74a,0x2a1e,0x1c34,0x2310,0x03d8,0xc1c9,0x5dea,0x473d}).toString();
        static final String L69 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xae59,0xb09d,0x8015,0x4dd2,0xdc8d,0x075b,0x746f,0x37e6,0xe879,0xaeb1}, new char[]{0xaef3,0x4db3,0x0738,0x807e,0xae3e,0x741d,0xdce2,0xb0e8,0x3788,0xe81d}).toString();
        static final String L70 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0346,0x1360,0x22bb,0xf931}, new char[]{0x1322,0x22da,0x0325,0xf95a}).toString();
        static final String L71 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1e05,0x5167,0xac22,0xc04b,0x4cd5,0xb950,0x5322,0x70c8,0x33dc,0xf803}, new char[]{0xac63,0xc03b,0x5117,0xf866,0x1e64,0x70ba,0x33bd,0xb93e,0x5341,0x4cb0}).toString();
        static final String L72 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xeb52,0x9db7,0xac0d,0x4e4f}, new char[]{0x4e0d,0xac61,0x9dc2,0xeb20}).toString();
        static final String L73 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x91c7,0x7bf8,0x0f2c,0xc17b,0xa95b,0xb8fb,0x6294,0x593b,0x3004,0xd01f}, new char[]{0x7bbf,0x9192,0x5972,0xc15b,0xd04d,0x0f4d,0xb89f,0x62fd,0x3071,0xa928}).toString();
        static final String L74 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd9da,0xb6e0,0xc247,0x7707,0x1609,0xca92,0x37af,0x2ddc,0x198d,0xcca6,0xde69,0xb213,0x5c00}, new char[]{0xde24,0xc228,0x166d,0x2da9,0xcafe,0xccc3,0x7727,0xb241,0xb681,0x37cb,0x5c69,0xd9af,0x19fe}).toString();
        static final String L75 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x59bc,0xb927,0xe32f,0x61c7,0x87e6,0xb3a0,0x3e46,0x950e,0x5387,0x2246,0x8aa9,0x11c9}, new char[]{0x8ae5,0x222f,0xe35e,0x957b,0x3e2f,0x8782,0x599c,0x61a0,0xb94b,0x53e6,0xb3d3,0x11ba}).toString();
        static final String L76 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x778e,0x3945,0xa99a,0x5894,0x6433,0x8485}, new char[]{0x3908,0x645c,0x77fa,0xa9f3,0x84ea,0x58fa}).toString();
        static final String L77 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x720c,0x3213,0xe978,0xa836,0xf856,0x74c2,0x792a,0x70b4,0xd354,0xaee6}, new char[]{0x748a,0xae89,0x727a,0xe91d,0xf824,0xa816,0x3263,0xd321,0x70c7,0x7942}).toString();
        static final String L78 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7853,0x36db,0xa04e,0xa451,0xe384,0xeb95,0xdc34,0xbd63,0xa6ab,0xe2cb,0xfece,0x055e,0x453b}, new char[]{0x456b,0xa424,0xebe6,0xe2a3,0xfeee,0xa6d8,0xdc40,0x052c,0xa02b,0xe3ea,0x7834,0x36af,0xbd0b}).toString();
        static final String L79 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x01fa,0x2268,0x0dde,0xf6a7,0x0d0b,0x4a24,0xa105,0x445f,0xc425,0x396e}, new char[]{0xa14d,0x2207,0xc453,0x443a,0x391c,0x0dfe,0x0d7f,0x0193,0xf6cb,0x4a50}).toString();
        static final String L80 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb439,0xa1f7,0x0b1c,0x705b,0x41ab,0x2bd8,0x9b65,0xa8fa,0x1ddc,0x1547,0x5256,0x6a55,0x8647}, new char[]{0xa1a3,0x152e,0x1db0,0x702f,0x0b3c,0x5225,0x9b11,0x8635,0x41ce,0x2bb6,0xa89d,0xb44d,0x6a3d}).toString();
        static final String L81 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb200,0x7cdb,0xb5ae,0x1188,0xfe06,0xae07,0x8371,0xa8d0,0xb904}, new char[]{0x11c7,0xae77,0xb5cb,0xfe68,0xb924,0x831c,0x7cbe,0xb26e,0xa8a5}).toString();
        static final String L82 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc02e,0x8ab7,0x6cc8,0x6bf2,0xf7ca,0xf39b,0xbed9,0xebfc,0x4122,0x12c0,0x7827,0x68a0}, new char[]{0xbe89,0x12b2,0x8ad2,0x6cbb,0xf3e8,0x4102,0xeb97,0xc04b,0xf7b3,0x7809,0x688e,0x6bdc}).toString();
        static final String L83 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x41e7,0x938c,0x0978,0xd51a,0xc7f6,0x7b74,0xb76d,0xf92a,0xac67,0xbd54,0x5f8e}, new char[]{0x93c1,0x5feb,0xac09,0x4192,0xf90a,0xc785,0x7b1b,0x090d,0xbd3a,0xd57e,0xb71e}).toString();
        static final String L84 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf82e,0xce72,0xec04,0x4fe5,0x2d53,0x7fb8,0xb152,0xca3f,0xfba6,0xa899,0x4a4f,0x7c09,0xc720}, new char[]{0xca78,0x7fed,0x4a06,0xec24,0xc753,0xf84d,0x7c68,0xce1e,0xfbc3,0xb172,0xa8b1,0x4fc0,0x2d7a}).toString();
        static final String L85 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xcdc9,0x20c5,0x620b,0xe2f9,0x4961,0x1696,0x9c8f,0xebad,0xf9ee,0xdd3f,0xca40,0x5841,0x1e23,0x18d5}, new char[]{0x9cdd,0x4904,0xebc9,0xf99b,0x18b6,0xe29c,0xcdad,0x20e5,0x582c,0xdd50,0x627f,0x1e4a,0x16f9,0xca2e}).toString();
        static final String L86 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2313,0x3c66,0x4bc9,0x9318,0x0186,0x3b54,0x8162,0xaf95,0x0a8e,0xd268,0x1d17,0xea88,0x969e}, new char[]{0x01c1,0xeae4,0x9379,0xafe6,0x0afd,0x3c46,0x4bac,0x3b32,0x96f8,0x1d72,0x2370,0x8116,0xd21b}).toString();
        static final String L87 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9a68,0x68be,0xb00f,0xec91,0x725a,0x8b45,0x5cfa,0xf4ec,0x30ad,0x237a,0xaa5c,0x1f17,0xc62e,0x54b2,0xe8f6}, new char[]{0x5caa,0xe884,0x8b2a,0x68d8,0xaa35,0x9a04,0x723f,0x2309,0x1f37,0xc648,0xecfe,0xf480,0xb06b,0x30c8,0x54c0}).toString();
        static final String L88 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0a9b,0x4006,0xeed8,0xd3eb,0x7bef,0xb8a7,0x7f20,0xcd5e,0x8e2c,0x1fed,0xde25,0xf366,0xc990,0x5cab,0xb22c,0x0cc2,0xcad1,0xa0bd,0x62be,0xc5fa,0xebe2,0x0e36,0x4fc2,0x649a,0xa15d,0xf529,0xe735,0x4db8,0xe7e1,0x3372,0x5338,0xe3c6,0x8983,0x9ea9,0x8c2e,0x89ad,0xaf20,0x2eb3,0x3a92,0xdfa9,0xf8b5,0xaff9,0x49cc,0x8d4c,0x361e,0x0506,0x8071,0x4d2c,0x667e,0x6d5c,0x42bf,0x1c76,0xdcfa,0xdbf7,0xd9c1,0x8dd7,0xb77f,0xea99,0xae6c,0x0c86,0x91f7,0x85eb,0x60bc,0x6ccb,0xc1ff,0x0198,0x5e7f,0x16de,0x0572}, new char[]{0x1fa8,0xc589,0x49af,0x3313,0x0aeb,0x4fa7,0x5c8b,0xdb94,0x64fb,0x60d2,0xb71c,0xb249,0x7b83,0x6cb8,0x89a3,0x7f52,0xdfcc,0x5e1d,0xe75c,0xeaf7,0x8c4a,0x4d02,0x0e16,0xae3e,0xd9a8,0xa0da,0xde4d,0xcaa5,0x363e,0x0511,0x6d30,0x8e45,0x0ce5,0x8dbc,0x3ab2,0x4dd9,0x0cac,0x057f,0x8006,0x5350,0xf303,0x01ea,0xeb87,0xdcda,0xb8cf,0x62db,0xd399,0x16bb,0xc1df,0xf8c7,0xf54c,0x42cc,0xa138,0x859f,0xaf8a,0xaf00,0x8d38,0xc9f8,0xcd3b,0x665e,0x1c19,0x89dd,0xe3a3,0x4068,0x2e93,0xeeb3,0xe784,0x9ed0,0x91d9}).toString();
        static final String L89 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x597d,0x5973,0x380e,0x8fe9,0xd86c,0x446a,0xde7f,0xc36c,0x8014,0x3c25,0x53a0,0x4be3,0xf3c5,0x0b15,0x4d82,0x93b6,0xd850,0xa382,0x6d32,0xae9f,0x0237,0xe2f9,0x54cc,0xc24f,0x7b85,0x7403,0x430c,0x3694,0x39e6,0x4cd0,0x102f,0xfa69,0x2b9b,0x4c0d,0x98e4,0x0d07,0x8bc0,0x07de,0xc13c,0x0422,0xb673,0x5855,0x3df0,0xe923,0xac9b,0xe364,0xad58,0x2927,0x8716,0x6aea}, new char[]{0x296a,0x1040,0xd834,0xe311,0xe295,0x4cb5,0x6aca,0x5821,0xacf4,0x3869,0x7464,0x36f8,0x4c68,0x54ec,0x6d59,0x93d3,0x2be2,0x53d3,0xaebf,0x595e,0x0217,0xe940,0x044e,0x3d99,0x8ba3,0x8f82,0x595d,0xf3b1,0x8779,0xde5f,0x0b77,0x4403,0x3c4b,0x4de6,0x4bd8,0xc26f,0xad1c,0xa3e7,0xfa05,0x3983,0xd818,0x0d62,0x07fe,0x7be6,0xb61f,0xc309,0x9885,0xc14e,0x8067,0x4322}).toString();
        static final String L90 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe89f,0xf282,0xd2dc,0x3b31,0x7824,0x46be,0xb547}, new char[]{0xb512,0xd2b2,0xe8fd,0x784b,0x3b44,0xf2ec,0x46da}).toString();
        static final String L91 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa2b7,0xdcfd,0xb15d,0x1259,0xa417,0x105e,0x5647,0x9c6a,0xc3e6,0x764d,0x8aa3,0x3b9a,0xfd08,0xecca,0x20f0,0x03d3,0xec2b}, new char[]{0xc382,0xa297,0xb110,0x1214,0x039e,0x766d,0x102b,0xa462,0xecbf,0x9c1f,0x3bb6,0xdcdd,0x8aeb,0xec63,0x567d,0xfd65,0x209d}).toString();
        static final String L92 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2ab4,0x3cfd,0x0506,0x0156}, new char[]{0x0134,0x0563,0x3c91,0x2ad8}).plainString();
        static final String L93 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7c36,0x973b,0x5aeb,0x366e,0xd1f6,0x8fbf}, new char[]{0x5a87,0x8fde,0x9742,0x7c53,0xd184,0x361d}).plainString();
        static final String L94 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8eac,0x1f81,0x45ef,0x53ce}, new char[]{0x5385,0x1fc8,0x8ef8,0x45bc}).plainString();
        static final String L95 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2ca4,0x913a,0x0168,0x4ca0,0x7da0,0x7cac,0xbbb5}, new char[]{0x0105,0x4ccf,0x2cca,0x9153,0xbbc1,0x7dcf,0x7cde}).plainString();
        static final String L96 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd0be,0x1854,0x0144,0x506d,0x26d5,0x05e6}, new char[]{0xd0d8,0x5002,0x058a,0x1830,0x26b0,0x0136}).plainString();
        static final String L97 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8572,0x7c32,0x0780,0x4da8,0x2453,0xd25d,0x13c6}, new char[]{0x4dd8,0x07e1,0x13aa,0x2436,0x8506,0xd229,0x7c57}).plainString();
        static final String L98 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6b78,0x3052,0xc80b,0x01a8,0x0ebe,0x8be4,0x8dfd,0x2baf}, new char[]{0x8d96,0x01cd,0x302b,0xc869,0x0ed1,0x8b85,0x2bdd,0x6b1c}).plainString();
        static final String L99 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf6d5,0xd8e5,0x77cb,0xbbe6}, new char[]{0xd88c,0xbb88,0x77ad,0xf6ba}).plainString();
        static final String L100 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb611,0xc2ad,0x0138,0xbdf3}, new char[]{0xc2e3,0xbdbc,0x016c,0xb654}).plainString();
        static final String L101 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf622,0x7961,0x6166,0xcd2d,0x7787,0xdb44,0xbfd6,0xdefc,0xe0d6,0x517c,0xfec8,0xa51d,0xba9b,0x6f44,0xdb2a,0xde65,0x1d11,0x9533,0xa929,0x908f,0x0d05,0x2b16,0x7c72,0x9bc3,0xf56c,0xe871,0xebcb,0x36e9,0x9007,0xdec8,0xbaa8,0xf8d6,0x3f66,0xdbd4,0xf845,0x41f9,0xfdfd,0x4107,0xe42b,0xce4e,0xedaf,0xd45c,0x99be,0x02dc,0x364f,0xe445,0xbe90,0xc9dd,0x1fc5,0x20d0,0x09e8,0x8037,0x38bd,0x1d8b,0x7190,0xfb3c,0xf4e2,0xbd65,0x71b2,0x466f,0xa208,0x16f3,0x09aa,0x6bdb,0xccfb,0x16e9,0xe8bc,0xf5f0,0x2c87,0xf0f2,0x15b7,0xf161,0xcff4,0x672f}, new char[]{0xe8f0,0x169c,0x460c,0x9066,0xcf98,0x9be3,0xdb34,0xcc89,0x02b3,0xfd9b,0xcd44,0x77eb,0xbafe,0xa56e,0x515c,0x71e3,0x20b1,0xf8a0,0xdead,0x16c9,0xf49b,0x90e0,0x675a,0x8045,0xce6e,0xd42f,0x6bbe,0x1fb1,0x369c,0x38cd,0xfb12,0xf5d0,0xe417,0xf82c,0x3628,0x7c1a,0x3f12,0xfee5,0xbacb,0xde09,0x99d7,0xde9f,0xeba0,0xed8f,0x0d64,0xa228,0x4194,0xbeff,0x2ce3,0xf657,0x790d,0x1d74,0xdb0a,0x6112,0x09c5,0xf54c,0xe0b3,0xe44f,0x71db,0xa95d,0xe851,0xc9b4,0xdba0,0xf081,0x09c8,0xf112,0xbd00,0x2b62,0x6f30,0x955a,0xbfb8,0x4160,0x15c4,0x1da5}).toString();
        static final String L102 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3ff6,0xd385,0x6502,0x18a4,0x66fe,0x917b,0xb70c,0xd337,0xa81e,0x2e51}, new char[]{0x9118,0x18c5,0x6576,0xb753,0xd354,0x2e3e,0xd3e8,0x3f94,0x669f,0xa86a}).plainString();
        static final String L103 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5adc,0xa662,0x8d2f,0x2116,0x4cbb,0x05e3,0x6f59,0x34b9,0x8138,0xe574,0x40e2,0xec57}, new char[]{0xec34,0x8159,0xe500,0x34e6,0x408f,0x6f36,0x5aaa,0x8d4a,0x217b,0x4cde,0x058d,0xa616}).plainString();
        static final String L104 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8a2f,0x8079,0x3979,0x14d5,0xa306,0x58fe,0x75b1,0xd318,0x319e,0x4cad}, new char[]{0x801a,0x75d0,0x31ea,0xd347,0x8a59,0x5897,0x14a6,0xa373,0x3918,0x4cc1}).plainString();
        static final String L105 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x149a,0x4645,0x6178,0x7888,0x6470,0x336e,0x61b6,0x3d53}, new char[]{0x4626,0x61d7,0x3d27,0x642f,0x3303,0x6111,0x14e9,0x78eb}).plainString();
        static final String L106 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8319,0xf192,0x298a,0xadc7,0xa18d,0x3e9e,0x21a2}, new char[]{0xf1f1,0x21cd,0x3ef0,0xada1,0xa1e4,0x29ed,0x836a}).plainString();
        static final String L107 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7150,0x8190,0x1ccc,0x3103,0x3709,0x51f3}, new char[]{0x81e4,0x7138,0x376c,0x316e,0x1ca9,0x5180}).plainString();
        static final String L108 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5a44,0xaaac,0x25c5,0xf02f,0x0b69,0x20c4,0x5257,0x5a32}, new char[]{0x5a51,0x5236,0x0b1d,0x5a1b,0xf042,0xaacd,0x20a7,0x25a0}).plainString();
        static final String L109 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf470,0xc06f,0x9eb3,0x3c48,0x80ba,0x542c,0x78bb,0x3dec,0xd95d}, new char[]{0xd93e,0x80db,0x3d98,0x78e4,0xc01c,0xf400,0x3c2d,0x544d,0x9ec1}).plainString();
        static final String L110 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2957,0xa61e,0xa888,0x55fa,0x5f4a,0x566c,0x3455,0xb826,0xa29c,0xdc41,0x8fc9}, new char[]{0xb845,0xa2fd,0x2923,0x8f96,0xa67d,0x5f38,0x342c,0x5589,0xa8fc,0xdc20,0x5600}).plainString();
        static final String L111 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc8c8,0xa4e6,0xa47f,0x46ae,0xb155,0xf28a}, new char[]{0xa40c,0x46cb,0xa487,0xc8ba,0xf2e9,0xb13d}).plainString();
        static final String L112 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7bae,0x5530,0xbc0f,0xbb8d,0xb5df,0x16a1}, new char[]{0x16e2,0xbbf9,0xbc7d,0x555c,0x7b8e,0xb599}).toString();
        static final String L113 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xbdd0,0xcff3,0xc552,0x136e}, new char[]{0xcfb1,0x1307,0xc53c,0xbdb4}).plainString();
        static final String L114 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x43fe,0x7f71,0xcbff,0x1a5b,0xf909,0x9baa,0x3afe}, new char[]{0x3ab5,0x7f14,0x1a22,0x9bc8,0xf960,0xcb91,0x439a}).plainString();
        static final String L115 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6c06,0x2e0e,0x7bac,0xd684,0x88ef,0x5fc7,0xf975,0x1085}, new char[]{0x5f94,0x10e0,0x889b,0x2e7a,0xf91c,0xd6ea,0x6c61,0x7bdf}).toString();
        static final String L116 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xee46,0x098f,0xc97a,0x9309,0xb018}, new char[]{0xee25,0xb074,0x09e0,0x937a,0xc91f}).plainString();
    }
}
