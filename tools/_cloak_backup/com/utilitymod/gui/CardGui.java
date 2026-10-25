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
    private static final String[] CLIENT_LINKS = {"Configs", "Themes", "Keybinds", "Options"};
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
            return label.equals("Sword") && m.getCategory() == com.utilitymod.gui.Category.COMBAT;
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
            new Cat(LT.L6, com.utilitymod.gui.Category.MOVEMENT, "movement"),
            new Cat(LT.L7, com.utilitymod.gui.Category.VISUAL, "eye"),
            new Cat(LT.L8, com.utilitymod.gui.Category.FINDER, "compass"),
            new Cat(LT.L9, com.utilitymod.gui.Category.MISC, "sliders"),
            new Cat(LT.L10, com.utilitymod.gui.Category.DONUT, "shield"),
            // playstyle kits (a module can sit in several)
            new Cat("Crystal", com.utilitymod.gui.Category.COMBAT, "combat", "crystal", "anchor", "city", "aegis", "totem"),
            new Cat("Spear", com.utilitymod.gui.Category.COMBAT, "combat", "spear", "lunge"),
            new Cat("Mace", com.utilitymod.gui.Category.COMBAT, "combat", "mace", "stun", "breach", "cob"),
            new Cat("Sword", com.utilitymod.gui.Category.COMBAT, "combat", "trigger", "aim", "hitbox", "backtrack", "jump reset",
                    "keep sprint", "velocity", "combo", "pointer", "snap tap", "sprint", "target"),
            new Cat("SMP", com.utilitymod.gui.Category.COMBAT, "shield", "shield", "drain", "cart", "backoff", "eat",
                    "armor", "logout", "visual range", "reconnect", "inv", "auto tool", "autotool", "finder", "miner"));

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
        search.setPlaceholder("Search modules...");
        search.setSurface(0x14FFFFFF, INK, MUTED, 28, 36);
        profileInput = new TextInput(0, 0, 10, 10, scale, net.minecraft.text.Text.literal("New profile name..."), 48,
                profileName::toString, v -> { profileName.setLength(0); profileName.append(v); });
        profileInput.setPlaceholder("New profile name...");
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
        Text.draw(g,entries.size()+" modules",contentX()+39*scale+Text.width(label,10*scale),panelY+71*scale,6.5f*scale,MUTED);
        String on=enabled+" on";
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
            button(g,"Save",x+w-70*scale,top+2*scale,70*scale,()->{
                notice=GuiConfig.saveProfile(profileName.toString())?"Profile saved":"Enter a valid profile name";
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
                    button(g,"Load",x+w-90*scale,y+3*scale,50*scale,()->{boolean loaded=GuiConfig.loadProfile(name); notice=loaded?"Profile loaded":"Load failed"; if(loaded)init();},false);
                    button(g,name.equals(pendingDelete)?"Yes?":"X",x+w-32*scale,y+3*scale,28*scale,()->{
                        if(name.equals(pendingDelete)){ GuiConfig.deleteProfile(name); profiles=GuiConfig.profiles();pendingDelete=null; }
                        else {pendingDelete=name;notice="Click Yes? to delete "+name;}
                    },true);
                }
                if(profiles.isEmpty())Text.draw(g,"No saved profiles yet.",x+12*scale,listTop+15*scale,8*scale,MUTED);
            });
            Text.draw(g,Text.fit(notice.isEmpty()?"Autosave keeps the current state; profiles are snapshots.":notice,w,6.5f*scale),x+3*scale,bottom-3*scale,6.5f*scale,MUTED);
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
                y=section(g,"Colours",y);
                y=colorRow(g,"Main Colour",0,y);y=colorRow(g,"Background",1,y);y=colorRow(g,"Back",2,y);
                y=section(g,"Appearance",y+4*scale);
                y=slider(g,"Blur",y,0,30,CardGuiStyle.blur,v->CardGuiStyle.blur=v);
                y=slider(g,"GUI Radius",y,4,60,CardGuiStyle.radius,v->CardGuiStyle.radius=v);
                y=slider(g,"Module Radius",y,2,40,CardGuiStyle.cardRadius,v->CardGuiStyle.cardRadius=v);
                y=toggle(g,"Liquid glass",y,CardGuiStyle.glass,()->CardGuiStyle.glass=!CardGuiStyle.glass);
                y=section(g,"Motion",y+4*scale);
                y=toggle(g,"Hover push",y,CardGuiStyle.motion,()->CardGuiStyle.motion=!CardGuiStyle.motion);
                y=slider(g,"Push strength",y,0,100,CardGuiStyle.gravity,v->CardGuiStyle.gravity=v);
                y=toggle(g,"Hover tilt",y,CardGuiStyle.tilt,()->CardGuiStyle.tilt=!CardGuiStyle.tilt);
                y=slider(g,"Tilt strength",y,0,100,CardGuiStyle.tiltStrength,v->CardGuiStyle.tiltStrength=v);
            } else if(page==4) {
                Text.draw(g,"Open menu",x+14*scale,y+10*scale,8*scale,INK);
                button(g,bindingMenu?"Press key...":dev.koifih.client.input.Keybinds.panelKey().getLocalizedText().getString(),x+w-100*scale,y,86*scale,()->bindingMenu=true,false);y+=32*scale;
                y=toggle(g,"Menu sounds",y,UiSounds.enabled,()->UiSounds.enabled=!UiSounds.enabled);
                y=slider(g,"GUI scale (%)",y,70,120,CardGuiStyle.size,v->CardGuiStyle.size=v);
                y=toggle(g,"Reduced motion",y,!CardGuiStyle.motion,()->CardGuiStyle.motion=!CardGuiStyle.motion);
                y=toggle(g,"Glass effects",y,CardGuiStyle.glass,()->CardGuiStyle.glass=!CardGuiStyle.glass);
                button(g,"Profiles folder",x+14*scale,y,w-28*scale,GuiConfig::openProfileFolder,false); y+=32*scale;
                Text.draw(g,"Escape cancels rebind. Right click anywhere here resets the open key.",x+14*scale,y,6.5f*scale,MUTED); y+=22*scale;
            } else {
                Text.draw(g,"Module toggle keys - click to bind; Delete clears.",x+14*scale,y,6.5f*scale,MUTED);y+=18*scale;
                List<Module> modules=new ArrayList<>(ModuleManager.all());modules.addAll(com.utilitymod.anubis.AnubisBridge.features());
                for(Module m:modules) {
                    if(query.length()>0 && !m.getName().toLowerCase(Locale.ROOT).contains(query.toString().toLowerCase(Locale.ROOT)))continue;
                    if(y>=top && y+23*scale<=bottom) {
                        Text.draw(g,Text.fit(m.getName(),w-120*scale,8*scale),x+14*scale,y+10*scale,8*scale,INK);
                        String key=m.getName().equals(bindingModule)?"Press key...":ModuleBinds.name(ModuleBinds.get(m.getName()));
                        button(g,key.isEmpty()?"Unbound":key,x+w-96*scale,y,82*scale,()->bindingModule=m.getName(),false);
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
        String date = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("d MMM uuuu, HH:mm", Locale.ENGLISH));
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
        CardGuiIcons.draw(graphics, "bell", bx + Math.round(7 * scale), by + Math.round(7 * scale), Math.round(14 * scale), INK);
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

        y = sectionHeader(graphics, "layers", LT.L18, navX, navW, rowH, y);
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
                Text.draw(graphics, "KITS", subX + 4 * size, y + 4 * size, 5.5f * size, Colors.withAlpha(MUTED, .7f));
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

        y = sectionHeader(graphics, "monitor", LT.L19, navX, navW, rowH, y + Math.round(10 * size));
        listTop = y;
        for (int i = 0; i < CLIENT_LINKS.length; i++) {
            linkRowY[i] = y;
            boolean hov = mouseX >= navX && mouseX < navX + navW && mouseY >= y && mouseY < y + rowH;
            navRow(graphics, new String[]{"folder","palette","keyboard","sliders"}[i], CLIENT_LINKS[i], subX, subW, rowH, y, page == i + 1, hov);
            y += rowH + gap;
        }
        graphics.fill(lineX, listTop, lineX + 1, y - Math.round(3 * size), Colors.withAlpha(0xFFFFFFFF, .12f));

        // footer warning (matches the source screenshot's warning block)
        if (!showNote) return;
        float footY = panelY + panelH - 44 * size;
        CardGuiIcons.draw(graphics, "info", (int) (panelX + 16 * size), (int) footY, (int) (12 * size), 0xFFFFC34B);
        Text.draw(graphics, "NOTE", panelX + 34 * size, footY + 6 * size, 7.5f * size, 0xFFFFC34B);
        List<String> lines = Text.wrap("Local profiles save your setup. Right-click a module to edit its settings.",
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
            case "Combat" -> "cat_combat"; case "Movement" -> "cat_movement"; case "Visuals" -> "cat_visual";
            case "Misc" -> "cat_misc"; case "Configs" -> "configs"; case "Themes" -> "themes";
            case "Keybinds", "Macros" -> "keyboard";
            case "Mace" -> "cat_mace"; case "Spear" -> "cat_spear"; case "Crystal" -> "cat_crystal"; case "Sword" -> "cat_combat";
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
            CardGuiIcons.draw(graphics,"search",search.getX()+10*scale,search.getY()+8*scale,11*scale,MUTED);
            float kx=search.getX()+search.getWidth()-32*scale;
            Draw.rect(graphics,kx,search.getY()+7*scale,25*scale,Math.round(14*scale),Math.round(2*scale),0x18FFFFFF);
            Text.drawCenteredX(graphics,"Ctrl F",kx+12.5f*scale,search.getY()+14*scale,5.5f*scale,MUTED);
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
            CardGuiIcons.draw(g, "sliders", tx - Math.round(21 * scale), ty + Math.round(3.5f * scale),
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
                    net.minecraft.text.Text.literal("Bind"),
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
            sheetRows.add(new Row(new KeybindSetting("Bind", -1), bind, true));
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
                sectionLine(g, sec == 0 ? "Keybind" : "Settings", sx + padX, sheetW - 2 * padX, y);
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
        CardGuiIcons.draw(g, "close", xc - xs / 2, yc - xs / 2, xs, xh ? INK : Colors.lerp(MUTED, INK, .6f));
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
}
